package com.lookie.opluskey.ui.hook

import android.view.ViewGroup
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.bridge.hookGuarded
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.pageBackgroundColor
import com.lookie.opluskey.ui.screen.createView
import com.lookie.opluskey.ui.state.TestRunnerCache
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/**
 * 把系统的「设置 → 侧键」页换成模块自己的页面 —— 换页编排与 Hook 安装。
 *
 * 它是个**枢纽**：装 Hook、建页面、把宿主的 container 上盖上模块页面，然后把三件独立的事分出去 ——
 * 窗口与状态栏的绘制（[HostChrome]）、系统返回的处理（[BackInterception]）、配置与日志的导入导出
 * （[ConfigTransfer]）；当前 Activity 与页面这两个跨簇共享的引用收在 [HostSession]。
 *
 * 反过来的边只有三条 —— 返回那一族要「切回模块界面」「关页收引用」「现在露的是谁」——
 * 所以 [restoreModuleUi] / [clearPageRefs] / [stockRevealed] 是 `internal`：
 * 它们的语义属于换页编排本身，搬不走。
 */
internal object SettingsPageHooks {
    /**
     * 宿主 Activity 基类 —— 页面骨架在这里，见类注释。
     *
     * 注意：类名本体在 `bridge/Framework.kt` 的候选表里（一、7），这里只是别名。
     */
    private val BASE_ACTIVITY = Framework.HOST_SETTINGS_BASE_ACTIVITY

    /**
     * 侧键设置页的**实际** Activity（[BASE_ACTIVITY] 的子类）。
     *
     *  「重启服务」杀掉本进程之后要靠它把页面重新拉起来（见 `ui/state/PageControl`
     * 的 `schedulePageRelaunch`）：那一枪是进程死了之后由系统投递的，拿不到当前 Activity，
     * 只能有一个写死的组件名兜底。照旧保守取 `activity?.componentName`，取到就不用它。
     *  真机 `dumpsys package com.oplus.gesture` 核对（它的 intent-filter 里带着
     * `com.oplus.gesture.StartActionButtonSettingsActivity` 这个 action）。
     *
     * 注意：类名本体在 `bridge/Framework.kt` 的候选表里（一、7），这里只是别名。
     */
    internal val SETTINGS_ACTIVITY = Framework.HOST_SETTINGS_ACTIVITY

    /**
     * `R.id.container` / `R.id.appbar_layout` 的数值、宿主包名、浅色主题名 ——
     * 全部搬到了 `ui/HostWindow.kt`（连同 [findContainer] / [pageContext] / [isNight]
     * 那几个纯读取的辅助函数）。
     *
     * 换页期间那两个「当前」引用（Activity / 页面）在 `ui/HostSession.kt` —— 它们跨
     * 换页编排 / 窗口与状态栏 / 返回拦截 / 导入导出四簇，所以单拎出去。本对象只留换页与 Hook 编排。
     */

    @Volatile
    private var installed = false

    /**
     * 安装入口带进来的 `module`。
     *
     * 留着是因为**返回拦截**要装 Hook，而 `install()` 之后才会真正换页
     * （那时已经没有 `module` 参数了）。
     */
    private var moduleRef: XposedModule? = null

    /**
     * 宿主那个基类本身（[BASE_ACTIVITY]）—— 换页回调**按它过滤实例**。
     *
     *  [Reflect.method] 是**沿父链上溯**取最靠下的那份声明：宿主若没自己重写 `onCreate`，
     *  取到的就是平台 `Activity#onCreate` —— 那个 hook 挂在**平台类**上，
     *  等于这个进程里**所有** Activity 都会进来。只判 `is Activity` 挡不住，
     *  必须判「是不是我们这个基类的实例」，否则别的页面也会被改主题 / 盖模块页。
     */
    private var baseClass: Class<*>? = null

    /**
     * 模块页面的根 View 与宿主原顶栏 —— 「打开自带的快捷键界面」靠切换这两者的可见性实现。
     *
     *  宿主 Activity 是 `launchMode=singleInstance`（真机 aapt 核对），再 start 只会走
     * `onNewIntent`、`onCreate` 不再跑，所以不能靠「起一个不换页的实例」。
     * 就地切换还有个前提：宿主那棵 View 树一直活着（[replaceContent] 只覆盖不清空）。
     *
     *  与上面那几个字段一样，**页面关掉时要一起松掉**（[clearPageRefs]）——
     * 不然它们会钉住一个已销毁的 Activity 直到下次进页。
     */
    private var pageView: android.view.View? = null
    private var hostAppbar: android.view.View? = null

    /** 换页时的内容容器（露出原版页面时要重刷状态栏配色）。 */
    private var hostContainer: ViewGroup? = null

    /**
     * 宿主原版页面的底色。
     *
     * 原版页是宿主写死的**深色**（清单主题 `ActionButtonThemeAppNoTitle` 的 parent 是
     * `Theme.COUI.Main.Dark`，见 [HostChrome.applyHostTheme]）—— 所以露出它时状态栏也得是黑的，
     * 不能留我们页面的浅色底（真机反馈「打开自带的界面后状态栏是白的、页面是黑的」）。
     */
    private const val STOCK_BACKDROP = 0xFF000000.toInt()

    //  刻意**不**引用色板里那个页面底色：这一档说的是「宿主原版页面本来就写死深色」，
    //  与模块自己的深浅主题无关 —— 将来色板换了，这里也不该跟着变。

    /** 现在露的是不是宿主原版页面（true 时系统返回 = 切回模块界面）。 */
    @Volatile
    internal var stockRevealed = false

    /**
     * 顶栏标题：宿主的原文（首页用）。
     *
     * 顶栏自绘后，标题由模块页面自己渲染；这里只负责把宿主 Activity
     * 的标题读出来交给页面（子页标题「按键绑定」也归页面管）。
     */
    private var hostTitle: String = ""

    // 按键绑定子页的标题挪进了页面（`SettingsPage.BINDING_TITLE`）——
    // 顶栏自绘后标题归页面自己渲染。

    // 旁路标记的文件名 —— 两条候选路径共用一个名字。
    private const val BYPASS_NAME = "opluskey_bypass"

    /**
     * 调试旁路：这些路径里**任意一个存在**就跳过换页，让系统原版页面显示出来。
     *
     * 存在的唯一理由是**视觉对齐**：要照着原版把新页面做得像，就得先把原版截下来看。
     * 造这个开关比反复改代码方便（`touch` 一下即可来回切换）。
     *
     * 多条路径是因为**设置页进程能读哪个路径并不确定**
     * （它是宿主的 `platform_app` 域，读不了 `/data/system`）——
     * 两个都试，哪个能创建就用哪个。第二条跟着配置文件走（[ConfigStore.filesDir]），
     * 免得配置挪了地方、这个路径还指着旧址。
     */
    private val BYPASS_PATHS =
        listOf(
            "/data/local/tmp/$BYPASS_NAME",
            ConfigStore.filesDir().resolve(BYPASS_NAME).path,
        )

    private fun bypassed(): Boolean {
        for (p in BYPASS_PATHS) {
            try {
                if (java.io.File(p).exists()) {
                    XLog.w("命中调试旁路（$p 存在）→ 本次不换页，显示系统原版页面")
                    return true
                }
            } catch (e: Throwable) {
                XLog.d("检查旁路标记失败（忽略）：$p | ${e.message}")
            }
        }
        return false
    }

    fun install(
        module: XposedModule,
        loader: ClassLoader,
    ) {
        if (bypassed()) return
        moduleRef = module

        if (installed) {
            XLog.w("设置页 Hook 已经装过，跳过重复安装")
            return
        }

        val base =
            com.lookie.opluskey.core.reflect.Reflect
                .findClass(loader, BASE_ACTIVITY)
        if (base == null) {
            XLog.w(
                "没找到 $BASE_ACTIVITY —— 本机不是 ColorOS，或侧键设置页改了名。" +
                    "模块不换页，但设置页仍是系统原版（功能正常）",
            )
            return
        }

        val onCreate =
            com.lookie.opluskey.core.reflect.Reflect.method(
                base,
                "onCreate",
                android.os.Bundle::class.java,
            )
        if (onCreate == null) {
            XLog.w("$BASE_ACTIVITY 上没找到 onCreate(Bundle)，无法换页")
            return
        }
        baseClass = base

        val handle =
            module.hookGuarded(onCreate) { chain ->
                onAfterCreate(chain)
            }

        // 「导出日志 / 导出配置 / 导入配置」都要从宿主的 onActivityResult 收结果
        // （宿主 Activity 不是我们的，SAF 的结果只走它）
        ConfigTransfer.installFileResultHook(module)

        // 生命周期转发：**从「设置 → 快捷键」重新进来**时把页面栈清回首页
        //   宿主这个 Activity 被系统复用、再进来不重建，栈会残留在子页）。
        //
        //  判据是 `Activity#onNewIntent`，**不是**「onResume / onUserLeaveHint」那一对：
        //   · 设置里点「快捷键」⇒ 宿主是 `singleInstance`，系统把这次启动当**新 Intent**
        //   · 从最近任务切回来 / 熄屏亮屏 / 通知栏来回 ⇒ 只是把任务提到前台，**不派发** ⇒ 不清栈。
        //
        //  用 [com.lookie.opluskey.core.reflect.Reflect.method] 从宿主类往上找（它取**最靠下**的那份声明）：
        //    宿主若自己重写了 onNewIntent 就挂它那份（不调 super 也能收到），
        //    没重写就落到平台的 `Activity#onNewIntent` 上。与 onCreate 同一挂法；
        //    按实例过滤，不吃宿主进程里其它 Activity。
        val lifecycleHooks = intArrayOf(0)
        val onNewIntent =
            com.lookie.opluskey.core.reflect.Reflect.method(
                base,
                "onNewIntent",
                android.content.Intent::class.java,
            )
        if (onNewIntent != null) {
            if (module.hookGuarded(onNewIntent) { chain ->
                    // 前置动作单独兜住：它抛了也不能把宿主自己的 onNewIntent 一并跳过
                    try {
                        if (chain.thisObject === HostSession.activity) {
                            // 从设置重进：若上一轮停在「露出宿主原版页面」那一档
                            // （用户从 ⋮ 菜单点过「打开自带的快捷键界面」，按 Home 走了、
                            //  再从设置进来），必须先把模块页面切回来 —— 否则重进看到的
                            // 是原版页，还会以为模块坏了。宿主是 singleInstance，重进走的是
                            // 这里、`onCreate` 不会再跑，所以这条恢复只能挂在这儿。
                            if (restoreModuleUi()) {
                                XLog.w("从设置重进：上一轮露着宿主原版页 ⇒ 先切回模块页面")
                            }
                            HostSession.page?.onHostReentered()
                        }
                    } catch (e: Throwable) {
                        XLog.w("onHostReentered 失败（onNewIntent 照常 proceed）", e)
                    }
                    chain.proceed()
                } != null
            ) {
                lifecycleHooks[0]++
            }
        } else {
            XLog.w("$BASE_ACTIVITY 上没找到 onNewIntent(Intent)，从设置重进不会清栈")
        }

        // 关页兜底：顶栏返回 / 系统回收 / 手势退出，最终都会走到 `onDestroy`。
        //  不在这儿清，[clearPageRefs] 里那几个进程级 `var` 会一直钉住**已销毁**的 Activity
        //  和它挂着的整棵宿主视图树（`HostSession.activity` / `pageView` / `hostAppbar` /
        //  `hostContainer`）—— 直到下次进页才被覆盖。
        //  挂法与上面一致；实例过滤交给 [clearPageRefs] 自己（`HostSession.activity !== activity` 直接返回），
        //  因为 `onDestroy` 必然落在 `Activity` 上、进程里每个 Activity 都会进来。
        if (installDestroyHook(base, module)) {
            lifecycleHooks[0]++
        } else {
            XLog.w("$BASE_ACTIVITY 上没找到 onDestroy，关页后页面引用不会松")
        }

        if (handle != null) {
            installed = true
            XLog.w(
                "设置页换页 Hook 安装完成：换页 1/1 + 生命周期 ${lifecycleHooks[0]}/2" +
                    "（$BASE_ACTIVITY）",
            )
        } else {
            XLog.w("设置页换页 Hook 装不上（见上面的失败原因）")
        }
    }

    /**
     * 给宿主设置页挂 `onDestroy`（关页兜底清理）。返回是否挂上。
     *
     *  挂在基类上时，进程里每个 Activity 销毁都会进来 —— 过滤交给 [clearPageRefs]
     *  自己做（它先比对 `HostSession.activity`，不是本页就直接返回）。
     */
    private fun installDestroyHook(
        base: Class<*>,
        module: XposedModule,
    ): Boolean {
        val onDestroy =
            com.lookie.opluskey.core.reflect.Reflect
                .method(base, "onDestroy") ?: return false
        return module.hookGuarded(onDestroy) { chain ->
            try {
                val activity = chain.thisObject
                if (activity is android.app.Activity) clearPageRefs(activity)
            } catch (e: Throwable) {
                XLog.w("关页清理失败（onDestroy 照常 proceed）", e)
            }
            chain.proceed()
        } != null
    }

    /**
     * `onCreate` 的 after 回调。
     *
     *  顺序是「先让原方法跑完，再换内容」—— 必须这样：
     *  - 原方法里才有 `setContentView`，不跑完就没有 `container` 可找；
     *  - 顶部栏、窗口配色也都在原方法里装好，我们只在其后动内容区。
     *
     * 在**原方法之前**做的事只有一件：把宿主主题换成浅色变体（见 [HostChrome.applyHostTheme]）——
     * 主题必须在 `setContentView` 之前定下来，晚了顶栏不会跟着变。
     *
     * 原方法自身的异常**原样抛出**（不是我们该吞的），
     * 只有「换页」这一步的异常才归我们消化。
     */
    private fun onAfterCreate(chain: XposedInterface.Chain): Any? {
        //  **先按实例过滤，再动任何东西**：`Reflect.method` 沿父链上溯，宿主没重写
        // `onCreate` 时这个 hook 落在平台 `Activity#onCreate` 上 —— 那时这个进程里
        // 每一个 Activity 都会进来。判 `is baseClass` 才算「这是侧键设置页」；
        // 判不出来的（`baseClass` 为空）按「不是」处理，宁可这次不换页，
        // 也不能去改别的页面的主题、更不能往别人的 container 上盖模块页。
        //  主题必须在 `setContentView` 之前定下来，所以这条过滤得排在 `HostChrome.applyHostTheme` 前面。
        if (baseClass?.isInstance(chain.thisObject) != true) {
            return chain.proceed()
        }
        (chain.thisObject as? android.app.Activity)?.let { HostChrome.applyHostTheme(it) }

        // 再把宿主自己的 onCreate 跑完。这一步失败是宿主的 bug，让它照常崩。
        val result = chain.proceed()

        val activity = chain.thisObject
        if (activity !is android.app.Activity) {
            XLog.w("hook 到的不是 Activity（${activity?.javaClass?.name}），跳过换页")
            return result
        }

        //  **必须在 `chain.proceed()` 之后**（= `setContentView` 之后）才设得上：
        // `Window.setWindowAnimations()` 在 `mWindowAttributes` 还没建起来时直接 return，
        // 而 `mWindowAttributes` 是 `setContentView` → `installDecor` 那一步才初始化的。
        // 窗口的 `wanim` 依旧是非 0 的宿主主题值，等于没设。
        //  但要清楚：**设上了也治不了「从设置进来黑一下」** —— 那截黑是宿主 splash，
        // 后面那 0.26s 淡入由 ShellTransitions 执行、不看 `wanim`。实测数据见
        // [HostChrome.disableWindowTransition] 的注释，别把这个调用当成那条 bug 的修复。
        HostChrome.disableWindowTransition(activity)

        try {
            replaceContent(activity)
        } catch (e: Throwable) {
            // 换页失败不能让设置页打不开 —— 退回系统原版即可。
            XLog.w("设置页换页失败（保留系统原版页面）", e)
        }
        return result
    }

    /** 找到 `container` 并盖上模块页面。 */
    private fun replaceContent(activity: android.app.Activity) {
        //  注意：盖页面 = 模块界面**在上**，这个标记必须跟着归零：
        //  它只在 [restoreModuleUi]（返回切回）与 [clearPageRefs]（「交还宿主」那一条退页）
        //  里复位 —— 「露过原版页之后经别的路径离页（Home → Activity 被回收）再重进」时
        //  `replaceContent` 不碰它 ⇒ 残留 true ⇒ 首页被误挂上返回回调、吞掉第一次返回、丢跟手动画。
        stockRevealed = false
        val container = findContainer(activity)
        if (container == null) {
            XLog.w(
                "找不到 R.id.container（0x${Integer.toHexString(ID_CONTAINER)}）" +
                    "—— 可能是 ROM 改了布局，换页放弃，页面仍是系统原版",
            )
            return
        }

        //  全量迁移：页面改成「**宿主真·COUI 控件 + 平台组件**」，
        // 不再走 Compose / MD3 —— 那条路是「MD 组件 + COUI 取色」的混搭，观感与系统页对不上。
        // 新页面用反射造宿主的 COUISwitch（类名未混淆、构造器是标准 View 构造器、
        // 平台方法名改不了），其余用框架控件 + 宿主的 COUI 资源名，
        // 依据见 docs/device-findings.md 第十节。
        //  全量迁移：页面改成 **COUI（Compose 组件库）**，
        // 不再用宿主控件反射、也不再从宿主主题取色 —— 依据见 SettingsPage 的类注释
        // 与 app/build.gradle.kts 的依赖段。
        // ⇒ ComposeView 需要 ViewTree owner，本文件在 `container.addView` **之后**
        //   回调 `page.viewTree.attachOwnersUpward(view)`（宿主那棵树上一个 owner 都没有）。
        val pageCtx = pageContext(activity)
        val view: android.view.View
        val pageBg: Int
        val page = SettingsPage(pageCtx)
        view = page.createView() ?: run {
            XLog.w("模块页面构建失败 —— 保持系统原版页面")
            return
        }
        pageBg = page.pageBackgroundColor

        // 「从设置进模块页面会瞬间黑再亮」）。
        //
        // 更正定责：这里刷的是**本 Activity 自己的 window / decorView /
        // （splash，纯黑，由宿主清单主题决定、在 SystemUI 侧画）。完整的录屏数据与
        // 结论见 [HostChrome.disableWindowTransition] 的注释。
        // 这一层刷色仍然保留：splash 撤掉之后、模块页面首帧画出来之前，露出来的是
        // 本 Activity 的 window 底色，刷成页面底色可保证那一瞬不跳色（保险性质）。
        //
        // 只改背景色，不动结构、不动 insets（与 [HostChrome.applyStatusBarArea] 同一纪律）。
        HostChrome.paintWindowBackdrop(activity, container, pageBg)

        // 不透明 + 吃触摸：盖住宿主内容，同时不让点击穿透到下面
        view.isClickable = true
        view.isFocusable = true

        container.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        //  **必须在 addView 之后**给整条祖先链挂 ViewTree owner：ComposeView 在
        // `onAttachedToWindow` 里就要用（那个时机在 try/catch 之外，缺了直接崩），
        // 而它只沿 **parent 链**找 owner —— addView 之前 parent 还是 null。
        page.viewTree.attachOwnersUpward(view)

        // 首帧诊断：记下模块页面**真的画到屏幕上**的时刻。
        // 用途：录屏逐帧分析「从设置进来黑多久」时，靠它把那段黑切成两截 ——
        // 「宿主 splash 还在」与「splash 撤了、我们的页面还没画出来」。
        // 只看录屏是分不清的（两种情况都是一片黑），这是唯一能定责的锚点。
        // 只打一次；移除监听走 `post`（在 onDraw 里直接 remove 会踩并发修改）。
        try {
            val probe = view
            probe.viewTreeObserver.addOnDrawListener(
                object : android.view.ViewTreeObserver.OnDrawListener {
                    private var done = false

                    override fun onDraw() {
                        if (done) return
                        done = true
                        XLog.w("模块页面首帧已绘制")
                        probe.post {
                            runCatching { probe.viewTreeObserver.removeOnDrawListener(this) }
                        }
                    }
                },
            )
        } catch (e: Throwable) {
            XLog.d("挂首帧诊断失败（忽略）：${e.message}")
        }

        // 标题与返回键由模块页面自己的 `TopAppBar` 渲染，宿主的 appbar 整条藏掉。
        // 只改可见性、不 remove —— 换页第一纪律（宿主可能持有引用，remove = 埋雷）。
        // 藏掉之后也就不需要避让（`reserveAppbarSpace` 那套）、不需要给宿主顶栏
        // 刷底色 / 换标题 / 染图标（那些函数一并移除）。
        try {
            // 走 [findAppbar] 而不是裸 `findViewById(ID_APPBAR)`：宿主 ROM 升级重建资源表之后
            //  写死的数值会失配，那条路会静默查不到 ⇒ 顶栏藏不掉、压在模块页面上。带名字反查的
            //  兜底早就在 [findAppbar] 里，只有经它才执行得到。
            val appbar = findAppbar(activity)
            if (appbar == null) {
                // 不伪造能力：没找到就说没找到，别打「已隐藏」（排查时会被带偏）
                XLog.w("没找到宿主顶栏（R.id.appbar_layout）—— 顶栏可能与页面重叠")
            } else {
                appbar.visibility = android.view.View.GONE
                hostAppbar = appbar
                XLog.w("宿主顶栏已隐藏 —— 标题与返回键由模块页面自绘")
            }
        } catch (e: Throwable) {
            XLog.w("隐藏宿主顶栏失败（顶栏可能与页面重叠，功能不受影响）", e)
        }

        // 见 [revealStock] —— 宿主那棵 View 树一直都在，露出来就是原版页面。
        pageView = view
        hostContainer = container
        page.hooks.onOpenStock = { revealStock(activity) }
        // 进出子页 / 浮层开合 ⇒ 重新定「要不要挂着返回回调」（见 BackInterception.syncBackCallback 的注释）
        page.hooks.onBackInterceptionChanged = { BackInterception.syncBackCallback(activity) }
        page.hooks.onExportLog = { ConfigTransfer.exportLog(activity) }
        page.hooks.onExportConfig = { ConfigTransfer.exportConfig(activity) }
        page.hooks.onImportConfig = { ConfigTransfer.importConfig(activity) }

        // 状态栏区域已归页面自己管：模块页面本身是 edge-to-edge 的（Scaffold
        // `contentWindowInsets = 0`、顶栏 107dp 含状态栏 inset、底色铺到 y=0），
        // 那一条早就是页面自己画的，之前盖在上面的 [HostChrome.coverStatusBar] 遮罩反而把它
        // 挡在模块控制之外 —— 顶栏的模糊在那儿断了、浮层的 dim 也到不了。
        // 现在只剩「露出宿主原版页面」时才盖（那一档模块页面是 GONE 的，没人画那一条）。

        // 首页标题交给页面（子页标题「按键绑定」在页面里）
        hostTitle =
            try {
                activity.title?.toString().orEmpty()
            } catch (e: Throwable) {
                XLog.d("读宿主标题失败（忽略）：${e.message}")
                ""
            }
        page.hooks.homeTitle = hostTitle

        // 返回：子页（按键绑定）→ 首页，首页 → 关页面。
        // (1) 页面顶栏自己的返回键（页面内部处理）；
        // (2) 系统返回 / 返回手势（终点是 `Activity#finish`，见 [BackInterception.installBackInterception]）。
        HostSession.activity = activity
        HostSession.page = page
        moduleRef?.let { BackInterception.installBackInterception(it, activity) }

        // 系统切深浅时：页面自己会换色板，但**状态栏是我们刷在宿主 View 上的**，
        // 不会跟着变 —— 不重刷就会出现「页面已经变深色、状态栏还是浅色」的割裂。
        page.hooks.onSystemThemeChanged = {
            XLog.w("系统深浅变了：重刷状态栏配色")
            HostChrome.paintChrome(activity, container, page.pageBackgroundColor)
        }

        // 状态栏的配色统一走 HostChrome.paintChrome（见上面那条回调）。
        //  先拆掉可能残留在窗上的状态栏遮罩（上一档「露出宿主原版页面」盖的）：
        // 这一步之后那一条就完全归模块页面自己画，见 [HostChrome.coverStatusBar] 的注释。
        HostChrome.removeStatusFiller()
        HostChrome.paintChrome(activity, container, pageBg)

        //  深浅切换只靠页面里的每秒轮询（读系统全局配置）。
        // 抓到它是「亮色进页先黑 1 秒再变白」的元凶：宿主声明了 `configChanges=uiMode`，
        // 它的配置**停在旧的 night 值**（见 [pageContext] 注释），回调把这份过期配置喂给
        // 页面 ⇒ 页面刚画完浅色就被翻成深色，一秒后轮询才纠正回来。删。

        // 状态栏图标的明暗已经由上面那句 `HostChrome.paintChrome` 里的
        // `HostChrome.setStatusBarIconAppearance` 按页面底色刷过（浅底 → 深色图标）。

        XLog.w(
            "设置页已盖上模块页面（container 原有 ${container.childCount - 1} 个子 View 保留未动）",
        )
    }

    /**
     * 露出宿主**原版**快捷键界面：藏掉模块页面、恢复宿主顶栏（菜单项「打开自带的快捷键界面」）。
     *
     * 宿主的内容一直活着（[replaceContent] 只覆盖不清空），所以只是切可见性 ——
     * 不动结构、不重造页面，返回时再切回来即可（[restoreModuleUi]）。
     */
    private fun revealStock(activity: android.app.Activity) {
        try {
            pageView?.visibility = android.view.View.GONE
            hostAppbar?.visibility = android.view.View.VISIBLE
            stockRevealed = true
            // 状态栏跟着原版页面走（原版是深色底，见 STOCK_BACKDROP）。
            //  这一档是 [HostChrome.coverStatusBar] **唯一**还在用的地方：模块页面已经 GONE，
            // 那一条没人画，而宿主原版页顶栏的配色我们碰不了 ⇒ 直接盖一块实心遮罩最稳。
            hostContainer?.let {
                HostChrome.paintStatusBarChrome(activity, it, STOCK_BACKDROP)
                HostChrome.coverStatusBar(activity, STOCK_BACKDROP)
            }
            // 原版页露着时这一退是「切回模块界面」⇒ 该由模块接，回调得挂上（见 [BackInterception.needsBackCallback]）
            BackInterception.syncBackCallback(activity)
            XLog.w("已露出宿主原版快捷键界面（状态栏切深色底；系统返回切回模块界面）")
        } catch (e: Throwable) {
            XLog.w("露出宿主原版页面失败（忽略）：${e.message}")
        }
    }

    /**
     * 切回模块界面（在宿主原版界面上按系统返回时）。
     *
     * @return true 表示这次返回被消费掉了（不要再关页面）。
     */
    internal fun restoreModuleUi(): Boolean {
        if (!stockRevealed) return false
        return try {
            hostAppbar?.visibility = android.view.View.GONE
            pageView?.visibility = android.view.View.VISIBLE
            stockRevealed = false
            // 状态栏跟着模块页面走（按当前深浅取页面底色）。
            //  必须把 [HostChrome.coverStatusBar] 盖的那层**拿掉**：它在 decorView 最上面，
            // 留着的话状态栏那一条就又脱离模块控制了（顶栏的模糊 / 浮层的 dim 都到不了）。
            HostChrome.removeStatusFiller()
            val activity = HostSession.activity
            val bg = HostSession.page?.pageBackgroundColor
            if (activity != null && bg != null) {
                hostContainer?.let { HostChrome.paintStatusBarChrome(activity, it, bg) }
            }
            // 原版页没了 ⇒ 这一退的归属重新算（首页 + 无浮层就是「不必再接」，见 [BackInterception.needsBackCallback]）
            if (activity != null) BackInterception.syncBackCallback(activity)
            XLog.w("系统返回：从宿主原版界面切回模块界面（不关设置页）")
            true
        } catch (e: Throwable) {
            XLog.w("切回模块界面失败（忽略）：${e.message}")
            false
        }
    }

    /**
     * 松掉换页期间钉住的那些引用（当前 Activity / 页面 / 页面根 View / 宿主顶栏 / 容器 /
     * 「露着原版页面」标记）。**页面关掉时调**（见 [BackInterception.handleDiscreteBack] 的「交还宿主」那支）。
     *
     *  这些字段都是进程级的 `var`（界面换页是这个进程唯一的事，没必要每实例一份），
     *  不主动清就会一直钉住**上一个已销毁的 Activity**，直到下次进页才被覆盖 ——
     *  有界泄漏，只影响这个 `:ui` 进程，但既然有关页这个明确的时点就该清掉。
     */
    internal fun clearPageRefs(activity: android.app.Activity) {
        if (HostSession.activity !== activity) return
        //  状态栏遮罩也要一起撤：它挂在 **decorView** 上（不是宿主那棵树），
        //  `pageView` 那个引用一松就再没人认得出它 —— 留在窗上会盖住下一轮进来的页面
        //  （表现为「重进设置页后状态栏那一条是死的、不吃点击也不跟着换色」）。
        HostChrome.removeStatusFiller()
        //  返回回调也一起摘：页面已经关了，钩子里那几档判定全落到「首页 / 没有浮层」上。
        //  平时是 [BackInterception.syncBackCallback] 先摘掉的，这里只是「关页」这个时点的兜底（重复调用无害）。
        BackInterception.unregisterBackCallback()
        HostSession.activity = null
        HostSession.page = null
        pageView = null
        hostAppbar = null
        hostContainer = null
        stockRevealed = false
        //  「测试命令」那份执行器也一起放掉：它持宿主 Application Context、几个系统服务，
        //  以及一条**注册在系统里的**手电筒 torch 回调（`FlashlightController` 注册后不会自摘）。
        //  页面关掉就没有人再用它了，留着只是把这几样钉在这个 `:ui` 进程里。
        TestRunnerCache.release()
    }

    // `isLightColor` / `pageContext` / `findContainer` 已搬到 `ui/HostWindow.kt`
    // （纯读取与包装，与换页编排无关）。
}
