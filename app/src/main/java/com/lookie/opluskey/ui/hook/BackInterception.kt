package com.lookie.opluskey.ui.hook

import androidx.annotation.RequiresApi
import com.lookie.opluskey.bridge.hookGuarded
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hideIme
import com.lookie.opluskey.ui.isImeVisible
import io.github.libxposed.api.XposedModule

/**
 * 系统返回的处理 —— 按需挂 / 摘 `OnBackInvokedCallback`（API 33+），以及平台 `onBackPressed` 那两条兜底。
 *
 * 这一族要回读换页侧的三样东西（[SettingsPageHooks.restoreModuleUi] /
 * [SettingsPageHooks.clearPageRefs] / [SettingsPageHooks.stockRevealed]）—— 那些是「切回模块界面」
 * 「关页收引用」，属于换页编排本身，放在 [SettingsPageHooks] 那边。
 */
internal object BackInterception {
    /** 平台类 `Activity#onBackPressed` 那个 hook 装没装（**进程内只装一次**）。 */
    private var legacyBackHooked = false

    /** 宿主**自己**声明的那层 `onBackPressed` 装没装（见 [installBackInterception] 的 (3)'）。 */
    private var hostBackHooked = false

    /**
     * 已经注册过 `OnBackInvokedCallback` 的那个 **Activity 实例**（没注册时是 null）。
     *
     *  注册是**按需**的（见 [syncBackCallback]）：只有「子页 / 浮层 / 露着原版页」这几档才挂
     *  —— 挂着就等于告诉系统「这一退我自己处理」，而首页那一退正需要系统自己的关页预测动画
     *（下面那一层是另一个窗口，我们画不出来）。要注销就得知道**挂在谁身上**、**挂的是哪一个
     *  回调**，所以这里记实例、[backCallback] 记回调。
     *
     *  注意：记实例而不是一个进程级的布尔量：宿主是 `launchMode=singleInstance`，但页面 `finish`
     *  之后重进必然是**新实例**（被关掉的正是原来那一个）——布尔量会把新实例挡在外面，
     *  表现是「从子页按返回，直接回到系统设置」。
     */
    private var backCallbackActivity: android.app.Activity? = null

    /** 此刻挂在 [backCallbackActivity] 上的那个回调（注销要原样传回去）。 */
    private var backCallback: android.window.OnBackInvokedCallback? = null

    /** 拦截「系统返回 / 返回手势」，让子页先回上一层。 */
    internal fun installBackInterception(
        module: XposedModule,
        activity: android.app.Activity,
    ) {
        HostSession.activity = activity

        // (3) 老路子：hook 平台类 `Activity#onBackPressed`
        if (!legacyBackHooked) {
            legacyBackHooked = hookOnBackPressed(module, platformOnBackPressed())
        }

        // (3)' 宿主自己那层。 不会重复消费：(3)' 一旦判定「消费」，宿主方法体就不执行
        //     ⇒ 它那句 `super.onBackPressed()` 也不会发生 ⇒ (3) 收不到。
        if (!hostBackHooked) {
            hostBackHooked = hookOnBackPressed(module, hostOnBackPressed(activity))
        }

        // (2) 官方入口：**按需挂着**（见 [syncBackCallback]）。进页这一档是首页、也还没有浮层，
        //    所以下面这次注册只为拿一个判据 ——「宿主到底 opt-in 了没有」（没 opt-in 会抛，
        //    见 [registerBackCallback]）—— 试完立刻由 syncBackCallback 按需收回。
        val optIn = if (backCallbackActivity !== activity) registerBackCallback(activity) else true
        syncBackCallback(activity)

        XLog.w(
            "返回拦截：平台 onBackPressed=$legacyBackHooked，" +
                "宿主 onBackPressed=$hostBackHooked，" +
                "宿主有返回回调=$optIn，此刻挂着=${backCallbackActivity === activity}",
        )
        if (!legacyBackHooked && !hostBackHooked && !optIn) {
            XLog.w("返回拦截一条都没装上 —— 子页返回请用顶栏那个返回箭头")
        }
    }

    /**
     * 「返回」的**第一优先级**：键盘开着就先收键盘，这次返回到此为止
     *
     * 平台约定里 IME 比页面导航更靠前，但我们在 `OnBackInvokedDispatcher` 上注册的是
     * `PRIORITY_OVERLAY`（2000000）—— 比 IME 自己那个回调优先级高，返回会被我们抢走、
     * 退回了「按键绑定」。
     *
     *  判据只认 [isImeVisible]（IME inset 可见），不认「有没有字段持有焦点」——
     * 后者在键盘已收起时照样成立，会把本该退页面的返回吃掉。
     *
     * @return true = 键盘本来是开着的（已经收起来，调用方不要再往下走）
     */
    private fun consumeBackByIme(): Boolean {
        val activity = HostSession.activity ?: return false
        if (!isImeVisible(activity)) return false
        XLog.w("系统返回：键盘开着 ⇒ 只收键盘，不退页面")
        hideIme(activity)
        return true
    }

    /**
     * 把同一套「返回先给页面用」的判定挂到一个 `onBackPressed` 上。
     *
     * @return 挂上了没有（[origin] 为 null 时直接 false）
     */
    private fun hookOnBackPressed(
        module: XposedModule,
        origin: java.lang.reflect.Method?,
    ): Boolean {
        if (origin == null) return false
        return module.hookGuarded(origin) { chain ->
            if (chain.thisObject === HostSession.activity) {
                when {
                    //  键盘优先于页面栈：与 `registerBackCallback` 那条保持同一个顺序，
                    //    两条路谁先收到都不能让「键盘开着却退了页面」。
                    consumeBackByIme() -> {
                        null
                    }

                    //  浮窗开着 ⇒ 自己消费这次返回：收掉浮窗，不退页面（同 handleDiscreteBack）
                    HostSession.page?.hasOpenOverlays() == true -> {
                        XLog.w("系统返回（onBackPressed）：浮窗开着 ⇒ 收掉浮窗，不退页面")
                        HostSession.page?.closeOverlays("系统返回收浮窗")
                        null
                    }

                    HostSession.page?.goBack() == true -> {
                        XLog.w("系统返回（onBackPressed）：子页 → 首页（不关设置页）")
                        null // 不 proceed = 这一次返回被消费
                    }

                    SettingsPageHooks.restoreModuleUi() -> {
                        null
                    }

                    // 原版界面 → 模块界面，同样消费掉
                    else -> {
                        chain.proceed()
                    }
                }
            } else {
                chain.proceed()
            }
        } != null
    }

    /**
     * 给这个 Activity 实例注册 `OnBackInvokedCallback`（API 33+；宿主没 opt-in 会抛，catch 掉）。
     *
     *  注意：**按需调用**（见 [syncBackCallback]）：挂上它就等于告诉系统「这一退我自己处理」，
     *  系统从那一刻起**不再给这个窗口做关页动画** —— 所以只有「子页 / 浮层 / 露着原版页」
     *  这几档才该挂，首页那一退要留空给系统。别改回「一直挂着」。
     *
     *  API 34 起注册 [android.window.OnBackAnimationCallback]：系统会把预测返回手势的
     * **逐帧进度**也送进来，转发给页面（[SettingsPage.navBackInput]）后，coui-nav 自己驱动
     * 「页面跟着手指走」的转场。API 33（ColorOS 13）没有这个接口，注册原来的普通回调，
     * 返回退化成「松手才弹」的一次性动作。
     *
     *  注意：优先级是 `PRIORITY_OVERLAY`：这一层本来就要盖过宿主与系统默认的返回处理，
     * 换成动画回调不改变它「抢下返回」这件事。
     *
     * @return 注册上了没有（false = 宿主没 opt-in，或平台太老）
     */
    private fun registerBackCallback(activity: android.app.Activity): Boolean =
        try {
            val callback: android.window.OnBackInvokedCallback =
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    PredictiveBackCallback(activity)
                } else {
                    android.window.OnBackInvokedCallback { handleDiscreteBack(activity) }
                }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback,
            )
            backCallback = callback
            backCallbackActivity = activity
            true
        } catch (e: Throwable) {
            // 宿主没在清单里开 `android:enableOnBackInvokedCallback` 时就是这条
            XLog.w("注册返回回调失败（宿主未 opt-in）：${e.javaClass.simpleName}: ${e.message}")
            false
        }

    /**
     * 按**此刻**的页面状态定「要不要把返回回调挂在宿主上」—— 该挂就挂、该摘就摘，别的什么都不做。
     *
     *  ## 为什么要有这一档
     *
     *  挂着回调 = 告诉系统「这一退我自己处理」，系统就不做窗口动画了。而「首页返回 ⇒ 关掉整个
     *  设置页」这一退的跟手动画（窗口跟手缩放、露出下面那一层）**只有系统做得出来** ——
     *  下面那一层是另一个窗口，我们画不了（[SettingsPage.needsBackInterception] 里有同一笔账）。
     *  所以：**要自己接的那几档才挂，其余留空给系统。**
     *
     *  调用点：进页（[installBackInterception]）、页面回报状态变化
     *（`PageHooks.onBackInterceptionChanged`）、原版页露出与切回（[SettingsPageHooks.revealStock] /
     *  [SettingsPageHooks.restoreModuleUi]）。
     */
    internal fun syncBackCallback(activity: android.app.Activity) {
        if (needsBackCallback()) {
            if (backCallbackActivity !== activity) {
                //  换实例之前**先摘旧的**：不摘的话旧 Activity 的 dispatcher 上还挂着我们那份回调，
                //  旧页只要还活着，它的每一退都会被那份回调再接一次（两个回调同时生效）。
                unregisterBackCallback()
                if (registerBackCallback(activity)) {
                    XLog.w("返回回调：已挂上（这一退是子页 / 浮层 / 原版页 —— 归模块接）")
                }
            }
        } else {
            // 不再需要自己接 ⇒ 把手上的回调摘掉。判据**不能**只看「是不是当前这个 activity」：
            //  静态引用可能指着**另一个（旧的）实例**（宿主换了 Activity、旧页还没走完），那时两个
            //  分支都不成立，旧实例上的回调与这份引用就永远留着 —— 钉住一个已销毁的 Activity。
            //  摘的时候不挑实例：`unregisterBackCallback` 用的就是当初注册的那一份。
            unregisterBackCallback()
            XLog.w("返回回调：已摘掉（首页、无浮层 ⇒ 交给系统，关页才有跟手动画）")
        }
    }

    /**
     * 此刻「这一退」要不要模块自己接 —— 三个来源，任一成立就要：
     *
     *  · **露着宿主原版页**（[SettingsPageHooks.stockRevealed]，hook 侧自己的状态）⇒ 这一退是切回模块界面；
     *  · **页面说要接**（[SettingsPage.needsBackInterception]：子页 / 浮层）；
     *  · API 34 以下**一律要**：那些版本没有预测返回（见 [registerBackCallback]），
     *    留空给系统一点好处都没有，只会让返回从「模块自己处理」退化成系统的默认行为。
     */
    private fun needsBackCallback(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 34) return true
        if (SettingsPageHooks.stockRevealed) return true
        return HostSession.page?.needsBackInterception() == true
    }

    /** 摘掉此刻挂着的返回回调（[syncBackCallback] 与 [handleDiscreteBack] 的「交还宿主」都走它）。 */
    internal fun unregisterBackCallback() {
        val activity = backCallbackActivity
        val callback = backCallback
        backCallbackActivity = null
        backCallback = null
        if (activity == null || callback == null) return
        try {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        } catch (e: Throwable) {
            XLog.d("注销返回回调失败（忽略）：${e.message}")
        }
    }

    /**
     * 「一次性返回」的判定 —— 键盘 → 页面栈 → 露回模块界面 → 交还宿主。
     *
     *  两条没有手势进度的路都走这里：API 33 上的 `OnBackInvokedCallback`，以及
     * API 34+ 上「回调没接管手势」（键盘优先、浮层、以及注册还挂着时的首页）时的提交。
     */
    private fun handleDiscreteBack(activity: android.app.Activity) {
        XLog.w("系统返回（OnBackInvoked）：收到")
        // 这一整段跑在**系统回调**里（`OnBackInvokedCallback` / 预测返回的提交），抛出去就是
        //  宿主 `:ui` 进程崩；而它又不是 hooker，拿不到 `hookGuarded` 的 PROTECTIVE 兜底。
        //  只有 `unregisterBackCallback()` 自己那一层 try 的话，其余
        // （`goBack` / `SettingsPageHooks.restoreModuleUi` / `closeOverlays` / `SettingsPageHooks.clearPageRefs` / `finish`）全裸着。
        //  catch 里**只记账、不补动作**：这几步语义各不相同（收键盘 / 收浮层 / 退子页 / 切回模块界面 /
        //  关页面），随手补一个 `finish()` 会把「本该留在页内」的那几次也一起关掉。
        try {
            when {
                // 键盘开着 ⇒ 这一次返回只用来收键盘（见 consumeBackByIme 的说明）
                consumeBackByIme() -> {
                    // 收掉键盘就是这一下的全部处理
                }

                // 浮窗开着 ⇒ 自己消费这次返回：把浮窗收掉，不退页面
                // 用户定案：不接的话，浮窗盖在上面、底下的页面却被退掉。
                HostSession.page?.hasOpenOverlays() == true -> {
                    XLog.w("系统返回：浮窗开着 ⇒ 收掉浮窗，不退页面")
                    HostSession.page?.closeOverlays("系统返回收浮窗")
                }

                HostSession.page?.goBack() == true -> {
                    XLog.w("系统返回（OnBackInvoked）：子页 → 首页（不关设置页）")
                }

                SettingsPageHooks.restoreModuleUi() -> {
                    // 原版界面 → 模块界面，消费掉这次返回
                }

                else -> {
                    XLog.w("系统返回（OnBackInvoked）：不在子页 ⇒ 交还宿主（关页面）")
                    // 先摘掉自己（[SettingsPageHooks.clearPageRefs] 里也会摘一次，重复调用无害）：挂着回调时
                    // 系统不会再自己关页面。
                    unregisterBackCallback()
                    // 页面关掉了 = 这一份换页用的引用用完了，一起松掉：
                    // 不松的话当前 Activity / SettingsPage / 两个 View / 容器会一直挂到**下次进页**
                    // 才被覆盖（表现为「钉住一个已销毁的 Activity」，只影响这个 :ui 进程）。
                    SettingsPageHooks.clearPageRefs(activity)
                    activity.finish()
                }
            }
        } catch (e: Throwable) {
            XLog.w("系统返回处理失败（已忽略，不补动作）：${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    /**
     * 预测返回的手势回调（API 34+）。
     *
     *  会话状态是**三态**而不是布尔 —— 「键盘开着」这条在 `onBackStarted` 就把返回消费掉了
     *（收键盘），但系统并不知道，提交时照样会调 [onBackInvoked]；那次提交**必须不再往下走**
     *（键盘已经收了，再走一遍就会把页面也退掉）。
     *
     *  只有接管了的会话才把进度喂给页面：喂了，coui-nav 就会驱动「页面跟着手指走」的转场。
     *
     *  注意：这个回调**只在「子页 / 浮层」那几档才挂在宿主上**（见 [syncBackCallback]）：
     *  首页那一退是「关掉整个设置页」，那一下的跟手动画只有系统做得出来，我们**不注册**、
     *  留空给它 —— 所以首页返回时这里根本收不到事件。
     *
     *  `@RequiresApi(34)`：`OnBackAnimationCallback` 是 API 34 才有的接口，这个类的**声明**
     *  就碰到它；而实例化那一处是按 `SDK_INT` 判过的（见上面注册那段）。标上这一条之后 lint
     *  才会去核那条判据 —— 用 `@SuppressLint` 会把这一层核对一起关掉。
     */
    @RequiresApi(34)
    private class PredictiveBackCallback(
        private val activity: android.app.Activity,
    ) : android.window.OnBackAnimationCallback {
        private companion object {
            /** 还没开始 / 已结束。 */
            private const val SESSION_NONE = 0

            /** 这一手势被「收键盘」消费掉了 —— 后续提交与进度都忽略。 */
            private const val SESSION_IME = 1

            /** 已交给页面（[SettingsPage.navBackInput]），进度与提交都喂给它。 */
            private const val SESSION_PAGE = 2
        }

        private var session = SESSION_NONE

        /**
         * 四个回调共用的保护壳。
         *
         *  它们跑在**系统回调**里（`OnBackAnimationCallback` / `OnBackInvokedCallback`），抛出去就是
         *  宿主 `:ui` 进程崩；而它们不是 hooker，拿不到 `hookGuarded` 的 PROTECTIVE 兜底 ——
         *  与 [handleDiscreteBack] 同一个处境。
         *  catch 里**只记账 + 把会话归零**：归零是因为停在上一轮的值上，后面的
         *  `onBackProgressed` / `onBackInvoked` 会照着那个值派发到错的地方；
         *  不补动作 —— 这几步语义各不相同（收键盘 / 喂进度 / 提交 / 取消），随手补一个
         *  `finish()` 会把「本该留在页内」的那几次也一起关掉。
         */
        private fun guarded(
            what: String,
            block: () -> Unit,
        ) {
            try {
                block()
            } catch (e: Throwable) {
                session = SESSION_NONE
                XLog.w("系统返回（$what）失败（已忽略，不补动作）：${e.javaClass.simpleName}: ${e.message}", e)
            }
        }

        override fun onBackStarted(backEvent: android.window.BackEvent) {
            guarded("手势开始") {
                val page = HostSession.page
                session =
                    when {
                        consumeBackByIme() -> {
                            SESSION_IME
                        }

                        page != null && page.wouldConsumeBack() -> {
                            page.navBackInput.started(backEvent.toNavigationEvent())
                            SESSION_PAGE
                        }

                        else -> {
                            SESSION_NONE
                        }
                    }
            }
        }

        override fun onBackProgressed(backEvent: android.window.BackEvent) {
            guarded("手势进度") {
                if (session == SESSION_PAGE) {
                    HostSession.page?.navBackInput?.progressed(backEvent.toNavigationEvent())
                }
            }
        }

        override fun onBackInvoked() {
            guarded("提交") {
                when (session) {
                    SESSION_PAGE -> HostSession.page?.navBackInput?.completed()

                    // 键盘那次已经收掉了，这次提交别再走「一次性」判定（否则会把页面也退掉）
                    SESSION_IME -> Unit

                    // 注册还挂着、但这一退不归我们接（键盘优先 / 浮层 / 状态刚好在中途变了）⇒
                    // 走一次性判定。首页那一档正常情况下**根本没挂上回调**（见 [syncBackCallback]），
                    // 这一支只是兜底。
                    else -> handleDiscreteBack(activity)
                }
            }
            session = SESSION_NONE
        }

        override fun onBackCancelled() {
            guarded("取消") {
                if (session == SESSION_PAGE) HostSession.page?.navBackInput?.cancelled()
            }
            session = SESSION_NONE
        }
    }

    /**
     * 平台手势事件 → coui-nav 的事件（两边的 `EDGE_LEFT/RIGHT` 常量取值一致）。
     *
     *  `@RequiresApi(34)`：入参 `BackEvent` 本身就是 API 34 的类型，用它的地方都在那条判据之内。
     */
    @RequiresApi(34)
    private fun android.window.BackEvent.toNavigationEvent() =
        androidx.navigationevent.NavigationEvent(
            swipeEdge = swipeEdge,
            progress = progress,
            touchX = touchX,
            touchY = touchY,
            // `frameTimeMillis` 是 API 36 才有的读取口 —— 34 / 35 上退回系统时钟：
            //  它只是给这个事件打个时间戳（消费方用它算帧间隔），换一把钟不影响手势判定，
            //  但少这一判在 34 / 35 上会 `NoSuchMethodError`。
            frameTimeMillis =
                if (android.os.Build.VERSION.SDK_INT >= 36) {
                    frameTimeMillis
                } else {
                    android.os.SystemClock.uptimeMillis()
                },
        )
}
