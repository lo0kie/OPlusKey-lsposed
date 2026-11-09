package com.lookie.opluskey.host.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.bridge.AppEntry
import com.lookie.opluskey.bridge.host.BuiltInMessenger
import com.lookie.opluskey.bridge.host.RecorderStateBridge
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.RomProfile

/** 拉界面 / 发广播 / 触发外部能力这一族动作的执行器（小窗那一族见 [MiniWindowLauncher]）。 */
internal class AppLauncher(
    private val context: () -> Context?,
    /** 失败通知出口。 */
    private val onFailure: (CommandFailure) -> Unit,
) {
    /** 小窗那一族动作的执行器（`window:` 起法与「把前台收成小窗」共用一套反射降级，见 [MiniWindowLauncher]）。 */
    internal val mini = MiniWindowLauncher(context, onFailure)

    /**
     * 「再点一次要关」那一档的**进程内记账**（此刻只有录屏，见 [RomProfile.BuiltIn.stopAction]）。
     *
     *  记的是「我们自己起过、还没发过停止」的能力名。为什么只能自己记：宿主没有
     *  `MANAGE_MEDIA_PROJECTION`，`MediaProjectionManager` 查不到系统那边的投影会话。
     *  进程被杀后这本账清零 —— 那时再按一下会起第二个会话，录屏应用自己会拒绝或提示。
     */
    private val toggledOn: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet()

    /**
     * `intent:ACTION`：先按界面拉起，拉不起来再当广播发。顺序不能反 ——
     * 清单里打开相机那条就是个界面 Intent，只发广播相机不会开。
     */
    fun openIntent(action: String) {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，intent:$action 跳过")
                return
            }
        try {
            ctx.startActivity(Intent(action).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            XLog.w("intent:$action → 已作为界面拉起")
            return
        } catch (e: Throwable) {
            // 回落这一档是一次隐式广播：Intent 既没 `setPackage` 也没组件，API 26+ 打不到
            //  清单里登记的接收器，**只可能命中动态注册者**；而且没人接收时 `sendBroadcast`
            //  也不抛异常 —— 所以「发出去了」绝不等于「有人收」，本文件里也**没有**一条「已发广播」
            //  的成功日志（正是为了让这一档不显得比实际更可靠）。
            //  留着它的理由：动态注册者那一档仍然有效，去掉等于把这一档一起关掉。
            XLog.w("intent:$action 当界面拉不起来（${e.message}），改发隐式广播（没带包名，多半无人接收）")
        }
        try {
            // 注意：广播上**不挂** `FLAG_ACTIVITY_NEW_TASK`：那是给
            //  `startActivity` 用的，挂在 Intent 上再 `sendBroadcast` 没有任何意义（原样照抄了
            //  上面那条 startActivity 的写法）。无害但误导，去掉。
            ctx.sendBroadcast(Intent(action))
        } catch (e: Throwable) {
            // 界面与广播两条路都没成 ⇒ 这一下确实什么都没发生。原来只记日志，用户侧一片静默。
            XLog.w("发广播失败：$action", e)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /**
     * `app:包名/Activity` 或 `app:包名` —— 拉起一个界面。
     *
     * 目标可以带多开前缀（`999@包名/Activity`，见 `AppTarget`）：那时**解析与启动都必须换成那个
     * 用户的视角** —— 主用户的 `PackageManager` 看不见分身里的包，直接 `ctx.startActivity`
     * 也会在主用户里找组件（表现为「解析不到界面」）。
     *
     * [Action.App.stockAnim] 为真时套上**原版那层启动包裹**（见 [StockLaunchWrap]）：这是**命令级**
     * 的一档（同一条绑定自己决定），与小窗那一档互斥。
     */
    fun launchApp(action: Action.App) {
        val target = action.target
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，app:$target 跳过")
                return
            }
        val userId = AppTarget.userId(target)
        val pkg = AppTarget.pkg(target)
        val pm = AppCloneCatalog.packageManagerFor(ctx, userId) ?: ctx.packageManager
        val intent = entryIntent(pm, target, pkg, userId)
        if (intent == null) {
            // 区分「包还在但没有可启动界面」与「包已不在」，前者提示「已卸载」会误导
            //  分身目标：主用户的 PM 问不出那个包在不在（`getApplicationInfo` 必抛），
            //  这一支**不报「可能已卸载」**—— 上面 `resolveAsUser` 已经按 userId 问过 binder，
            //  拿不到就是「没有可用入口」。误报「已卸载」正是「列表点得动、按下报找不到」的成因之一。
            val installed = appInstalled(pm, pkg, userId)
            XLog.w("解析不到界面：$target（这个包还在=$installed，user=$userId）")
            onFailure(entryFailure(installed))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        //  **原版动画那一档：整件事交给宿主自己那条链路**（`OriginalLaunch` 的类注释里有证据：
        //  系统里真的出现了它建的 `SpruceLauncher` RemoteTransition）。它内部会自己起界面，
        //  所以这里交出之后必须 return —— 否则会开出两个任务。
        //  只对主用户转交：它用 `UserHandle.CURRENT`，分身目标会被起进主用户（见那边的注释）。
        //
        //  **转交之前也得挂 SKIP_CHOOSER**：这条路不经过 [AppLaunchAsUser.start]，而
        //  「装了分身的应用在主用户下启动」同样会被 ColorOS 问「用哪一份」—— 选择器一盖上，
        //  原版那套转场直接被截断（用户口径「会弹分身选择器」）。
        if (action.stockAnim && userId == 0) {
            AppLaunchAsUser.markSkipChooser(intent)
            if (OriginalLaunch.launch(ctx, stockLaunchIntent(intent))) return
        }
        try {
            // 主用户与分身**都走** ATMS 的 `startActivityAsUser`：只有这条路能在下发前统一给
            //  intent 挂 `OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER`。不挂的话，**装了分身的应用在主
            //  用户下启动照样会被 ColorOS 问「用哪一份」**（同名包在 0 与 998/999 里各有一份）。
            //  `createContextAsUser` 那条为什么不行见 `AppCloneCatalog`。
            if (!AppLaunchAsUser.start(ctx, intent, userId)) {
                //  **原先这里还挂着一条 `catch (ActivityNotFoundException)`，那条分支是死的**：
                //  `AppLaunchAsUser.start` 全程 `runCatching`，ANFE 在被它自己吞成 `false` 了，
                //  永远不会抛到这里 —— 于是「包还在、但那个组件被停用」那一档提示从来没出现过，
                //  用户看到的永远是泛泛的「这次没执行成功」。
                //  现在改成在**失败分支**里按同一句判据分档：显式点名了组件、而包还在
                //  ⇒ 就是这个组件用不了（[CommandFailure.APP_NO_ENTRY]）；其余（后台启动被拦、
                //  ATMS 两个签名都不通）⇒ 泛泛的 [CommandFailure.UNKNOWN]，别把系统拒绝说成应用的问题。
                val explicit = intent.component != null
                val installed = appInstalled(pm, pkg, userId)
                XLog.w("启动被拒：$target（user=$userId，显式组件=$explicit，这个包还在=$installed）")
                onFailure(if (explicit && installed) entryFailure(true) else CommandFailure.UNKNOWN)
                return
            }
        } catch (e: Throwable) {
            // 最常见的是后台启动限制 / 安全异常，不是「已卸载」—— 报「没执行成功」别误导
            XLog.w("拉起界面失败：$target（user=$userId）", e)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /**
     * `window:包名` —— 用小窗打开（有现成任务就复用，没有才新建一份）。
     *
     *  实现整族搬到了 [MiniWindowLauncher]（`window:` 起法、`zoom:mini` 的两档、ColorOS 小窗反射、
     *  几何验收与动画路线，连同它专用的常量）。本类只留转发口。
     */
    fun launchAppInWindow(target: String) = mini.launchAppInWindow(target)

    /** 把**前台**应用收成小窗（`zoom:mini` 那两档）；[collapsed] = 收起档。实现见 [MiniWindowLauncher]。 */
    fun putForegroundInMiniWindow(collapsed: Boolean = false) = mini.putForegroundInMiniWindow(collapsed)

    // `app:` / `window:` 共用的入口解析已挪到 `AppEntry.intentOf` —— 与「包名 → 入口」的
    //  `AppEntry.resolve` 是同一件事的两级，放一处；本类与小窗那一侧都调它。

    /**
     * 执行 ColorOS 小布快捷指令（`com.coloros.shortcuts`）。
     * 早先「三套通道逐个试、哪个成了算哪个」的判据不成立（`sendBroadcast` 是 fire-and-forget，
     * 没接收者也不抛异常，第一条永远 ok、后两套根本不会被尝试）。现在先问系统哪些 action
     * 真有接收者、**只发第一个命中的那条**（避免同一条指令被执行两次），一条都查不到再全部试
     * （非导出接收器对跨 uid 查询不可见）；日志只说「已发」这一步的事实，指令有没有被执行是小布自己的事。
     */
    fun runShortcut(shortcutId: String) {
        if (shortcutId.isBlank()) {
            //  说清断在哪一环：这是配置里存着一个空指令（导入 / 手改），不是「发了但没人接」
            XLog.w("小布快捷指令：shortcutId 为空，跳过")
            return
        }
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，shortcut:$shortcutId 跳过")
                return
            }

        //  注意：**只发一条**：两条 action 都命中时会各发一遍，而同一个
        //  接收器往往同时监听这两条 action ⇒ 指令被执行两次（注释里「避免同一条指令被执行两次」
        //  正好写反了）。既然已经问出「谁有接收者」，就只发给第一个命中的那个。
        //  一条都没查到（非导出接收器对跨 uid 查询不可见）时退回「全部各试一次」——
        //  那种情况下不知道哪条才对，宁可重复也不能不发。
        val resolved = SHORTCUT_ACTIONS.filter { hasReceiver(ctx, it) }
        val channels =
            if (resolved.isNotEmpty()) {
                listOf(resolved.first())
            } else {
                XLog.w("小布快捷指令：没查到任何接收者（可能是非导出接收器），按全部通道各试一次")
                SHORTCUT_ACTIONS
            }

        var sent = 0
        for (action in channels) {
            // 这是 `sendBroadcast`，不是 `startActivity`：`FLAG_ACTIVITY_*` 对广播没有意义，
            //  带上只会让读日志的人以为这里在起界面。
            val intent =
                Intent(action)
                    .setPackage(SHORTCUT_PKG)
            // 各版本用过不同的 extra key 名，**两条都带上** —— 实现方读哪个都能拿到。
            for (key in SHORTCUT_ID_KEYS) intent.putExtra(key, shortcutId)
            try {
                ctx.sendBroadcast(intent)
                sent++
                XLog.w("已发小布快捷指令：$action（id=$shortcutId）")
            } catch (e: Throwable) {
                XLog.w("发小布快捷指令失败：$action", e)
            }
        }
        if (sent == 0) onFailure(CommandFailure.SHORTCUT_FAILED)
        // 兜底：`sendBroadcast` 在没有接收者时**不抛异常**，所以「发出去了」不等于「有人收」——
        //  查不到任何接收者（`resolved` 为空）时上面会按全部通道各发一次，而结果**无从确认**
        //  （收方不回执）。这条 w 是留给排查的：用户在「快捷指令没反应」时能看到是这一档。
        if (resolved.isEmpty()) {
            XLog.w("小布快捷指令：查不到接收者，已按全部通道各发一次（结果无法确认，id=$shortcutId）")
        }
    }

    /** 这条 action 在 `com.coloros.shortcuts` 里有没有接收者（查不到 / 抛异常都按「没有」）。 */
    private fun hasReceiver(
        ctx: Context,
        action: String,
    ): Boolean =
        try {
            ctx.packageManager
                .queryBroadcastReceivers(
                    Intent(action).setPackage(SHORTCUT_PKG),
                    0,
                ).isNotEmpty()
        } catch (e: Throwable) {
            XLog.d("查小布快捷指令接收者失败（按没有处理）：$action | ${e.message}")
            false
        }

    /**
     * 起一个 ColorOS 系统能力（`oplus:` 动作）；目标组件与 action 见 [RomProfile]。
     */
    fun startOplusFeature(feature: String) {
        val target =
            RomProfile.builtIn(feature) ?: run {
                // 本机 ROM 没验过时落点会照基准实现试（见 `RomProfile.landing`），所以走到这里只剩
                //  两种情形：功能名写错了，或者那一档里根本没登记这个名字 —— 由下面那句日志报出来
                XLog.w("oplus:$feature 没有落点 —— 内置命令档位：${RomProfile.describeCurrent()}")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，oplus:$feature 跳过")
                return
            }
        // 带 AIDL 的那几档（录音）：先问一次真实状态再决定这一下是「起」「停」还是「不动」。
        //  必须排在下面「只停」那一支前面 —— 录音的「只结束」正是 service 为空、只有 AIDL 的落点。
        if (target.aidl != null && runAidlFeature(ctx, feature, target, onFailure) { startOplusFeature(feature) }) return

        // 「只停」那一档（录屏的「结束」）：没有启动落点，只发那条停止广播。
        //  记账一起清掉 —— 之后按「切换」那一档要按「现在是关」算。
        if (target.service.isEmpty()) {
            val stop = target.stopAction
            if (stop != null) {
                ctx.sendBroadcast(Intent(stop).setPackage(target.owner))
                toggledOn.remove(feature)
                XLog.w("oplus:$feature → 停止（广播 $stop）")
            } else {
                XLog.w("oplus:$feature 没有启动落点、也没有停止广播 —— 什么都不做")
            }
            return
        }

        // 「再点一次要关」那一档（此刻只有录屏）：登记了 [RomProfile.BuiltIn.stopAction] 的能力
        //  按开关语义走 —— 起过一次、还没发过停止 ⇒ 这一下是关。
        //  记账只在进程内：宿主没有 MANAGE_MEDIA_PROJECTION，查不到系统那边的投影会话。
        if (target.stopAction != null) {
            if (toggledOn.remove(feature)) {
                ctx.sendBroadcast(Intent(target.stopAction).setPackage(target.owner))
                XLog.w("oplus:$feature → 停止（广播 ${target.stopAction}）")
                return
            }
            toggledOn.add(feature)
        }

        // Messenger 绑定型（场景表 type=service 的大部分）必须 bind 后发 Message
        if (target.message != null) {
            if (sendMessengerMessage(ctx, feature, target)) return
            //  绑定没成立 = 这一次没起来 ⇒ 撤掉「现在是开」那笔账（否则下一次按会被当成「关」，
            //  发一条没人接的停止广播，按一下等于没反应）。
            if (target.stopAction != null) toggledOn.remove(feature)
            //  **而且要有回执**：原先这条分支什么都不报 —— 手电筒 / 勿扰那几档绑定失败时，
            //  用户看到的就是「按了、屏幕没动、也没有提示」（`BuiltInMessenger` 那边已经把
            //  「断在哪一环」写进日志了，但日志不是回执）。
            XLog.w("oplus:$feature 的 Messenger 绑定/发送没成，报一次失败")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        val intent = buildFeatureIntent(target)
        intent.putExtrasOf(target.extras + lockedExtras(target, ctx), feature)

        val short = "${target.owner}/${target.service.substringAfterLast('.')}"
        // 界面型目标：非 Activity 的 Context 起界面必须带 NEW_TASK，否则 ActivityNotFoundException
        if (target.activity) {
            //  登记了「先点亮屏幕」的那两档（锁屏相机 / 计算器）：灭着就先点一下，否则界面在幕后起来
            if (target.wakeScreen) wakeForFeature(ctx, feature)
            //  后面那几位由落点表自己声明（见 `RomProfile.BuiltIn.flags`），这里不按功能名分派
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or target.flags)
            try {
                ctx.startActivity(intent)
                XLog.w("oplus:$feature → startActivity($short)")
            } catch (e: Throwable) {
                // 没起来 ⇒ 那本「现在是开」的账也要撤掉，否则下一次按会被当成「关」
                if (target.stopAction != null) toggledOn.remove(feature)
                XLog.w("起 oplus:$feature 的界面失败：${e.javaClass.simpleName}: ${e.message}")
                onFailure(CommandFailure.UNKNOWN)
            }
            return
        }

        // 目标不是界面型：起服务那一条（两条起法 + 退路都在 [launchService] 里）
        launchService(ctx, feature, target, intent)
    }

    /**
     * 非界面型目标的起法 —— **先按清单声明的顺序**试前台 / 普通两条路，都被拒才退一层显式组件。
     *
     *  抽出来只为让 [startOplusFeature] 收在 detekt 的 `LongMethod` 长度线内：上面那条界面型
     *  分支与这一支互不依赖，合在一起时这一支光起服务就占掉四十来行。
     *
     * @param intent 已经填好 extras / 目标组件的 Intent（两条起法共用同一个）
     */
    private fun launchService(
        ctx: Context,
        feature: String,
        target: RomProfile.BuiltIn,
        intent: Intent,
    ) {
        val short = "${target.owner}/${target.service.substringAfterLast('.')}"
        // 目标声明了 special-use 前台类型时先走前台 —— 那是它在清单里给自己定的起法，
        // 普通 startService 会被后台启动限制拒。没声明的反过来先试 startService：
        // 对方不调 startForeground 时，前台方式会让**它的进程**抛
        // ForegroundServiceDidNotStartInTimeException。
        var last: Throwable? = null
        for (foreground in if (target.foreground) FOREGROUND_FIRST else SERVICE_FIRST) {
            try {
                if (foreground) ctx.startForegroundService(intent) else ctx.startService(intent)
                XLog.w("oplus:$feature → ${if (foreground) "startForegroundService" else "startService"}($short)")
                return
            } catch (e: Throwable) {
                last = e
                XLog.d("oplus:$feature 的 ${if (foreground) "前台" else "普通"}起法被拒：${e.message}")
            }
        }

        // 两种起法都被拒 ⇒ 退一层显式组件（口径与边界见 [tryComponentFallback]）。
        // 退路成功（返回 null）就在这里收手；否则拿最后那个错误报失败。
        val finalError = tryComponentFallback(ctx, feature, target, last) ?: return
        //  三条起法全失败 ⇒ 与界面型那条失败分支同一笔账：撤掉「现在是开」，下次按还是「开」。
        if (target.stopAction != null) toggledOn.remove(feature)
        XLog.w("起 oplus:$feature 失败：${finalError.javaClass.simpleName}: ${finalError.message}")
        onFailure(CommandFailure.UNKNOWN)
    }

    /**
     * Messenger 绑定型服务：**bind 后发一条 `Message`**（`what=1` 单击 / `2` 长按）。
     *
     * 实现在 `bridge/host/BuiltInMessenger` —— 手电筒那条也走它，两边各写一份时先漂的
     * 一定是边界处理（超时 / bind 被拒 / 失败上报），见那边的说明。
     */
    private fun sendMessengerMessage(
        ctx: Context,
        feature: String,
        target: RomProfile.BuiltIn,
    ): Boolean = BuiltInMessenger.send(ctx, target, "oplus:$feature") { onFailure(CommandFailure.UNKNOWN) }

    /**
     * 小布识屏：配置里仍写 KSU 那串 shell 命令（老配置不能失效），执行换成
     * [startOplusFeature] 的精确调用。
     */
    fun startSidebar() = startOplusFeature(RomProfile.NAME_SCREEN_OCR)

    private companion object {
        /** 小布快捷指令的包名。 */
        const val SHORTCUT_PKG = "com.coloros.shortcuts"

        /**
         * ColorOS 快捷指令的触发 action —— 各版本用过好几套名字（旧的是 `oplus` 命名空间）。
         * 见 [runShortcut]：先按「有接收者」筛，筛不出来才全部各发一次。
         */
        val SHORTCUT_ACTIONS =
            listOf(
                "com.coloros.shortcuts.action.EXECUTE_SHORTCUT",
                "com.oplus.shortcuts.action.EXECUTE_SHORTCUT",
            )

        /** 指令 id 在广播里的 extra key —— 各版本用过这两个名字，两条都带。 */
        val SHORTCUT_ID_KEYS = listOf("shortcut_id", "shortcutId")

        /** [startOplusFeature] 的尝试顺序：true = 前台起法。 */
        val FOREGROUND_FIRST = listOf(true, false)
        val SERVICE_FIRST = listOf(false, true)

        // 「等服务连上的上限」不在本类的 companion 里：它跟着 `BuiltInMessenger.TIMEOUT_MS` 走
        // （手电筒那条也吃同一个值），别在这儿再存一份。
    }
}

/**
 * 带 [RomProfile.BuiltIn.aidl] 的那几档（此刻只有录音）—— **先问一次真实状态，再决定这一下怎么动**。
 *
 *  为什么不能只发那条意图：录音机那条意图**自己就是开关**（在录时会被它停掉），而「只开始」恰恰
 *  不允许这样；「只结束」更是只有 AIDL 一条路（录音机没有任何停止广播）。
 *
 *  写成顶层函数而不是 `AppLauncher` 的成员：那个类的函数条数贴着 detekt `TooManyFunctions`
 *  那一档（`tryComponentFallback` 同一个理由）。
 *
 * @return `true` = 这一下已经处理完（停过 / 判定成空操作），调用方**别再起界面**
 */
private fun runAidlFeature(
    ctx: Context,
    feature: String,
    target: RomProfile.BuiltIn,
    onFailure: (CommandFailure) -> Unit,
    /** 主线程上折返用：整条重来一遍（理由见开头那段）。 */
    restart: () -> Unit,
): Boolean {
    //  问状态要 bind + 等事务（跨进程，见 `bridge/host/RecorderStateBridge`）：本函数在设置页
    //  「测试命令」那条路上是**主线程**调用，那就整条挪到后台线程再进来，别在主线程上等
    //  （宿主按键那条路本来就在 worker 线程上，这一步等于空操作）。
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Thread({ restart() }, "opluskey-aidl").apply { isDaemon = true }.start()
        return true
    }
    val aidl = target.aidl ?: return false
    return aidlDecision(ctx, target, aidl, "oplus:$feature", onFailure)
}

/**
 * 三档共用的一次判定 —— **先问一次真实状态，再决定这一下是「起」「停」还是什么都不做**。
 *
 *  为什么「只结束」也要先问：对录音机发一次「没有采集的停止」它照样会把收尾那一套走一遍
 *  （`stopRecord` 内部会 bind `RecorderService`），真机上的表现是**什么都没录也弹出一张录音的
 *  流体云**；而查状态只 bind `RecordStateService`，那个 `onCreate` 只注册广播接收器，是惰性的。
 *
 * @return `true` = 已经处理完（停过 / 判定成空操作），调用方别再起界面
 */
private fun aidlDecision(
    ctx: Context,
    target: RomProfile.BuiltIn,
    aidl: RomProfile.AidlControl,
    tag: String,
    onFailure: (CommandFailure) -> Unit,
): Boolean {
    val state = RecorderStateBridge.state(ctx, target.owner, aidl, tag)
    if (state == RecorderStateBridge.State.RECORDING) {
        if (aidl.mode == RomProfile.AidlControl.Mode.START) {
            //  「只开始」撞上在录：什么都不做。直接发那条意图会被录音机当成「再按一次」而停录。
            XLog.w("$tag：已经在录，这一下什么都不做（「只开始」那一档）")
        } else {
            //  「切换」与「只结束」：在录就停 —— 不指望 ROM 那条意图的判据（它还要看录音页在不在前台）
            stopByAidl(ctx, target, aidl, tag, onFailure)
        }
        return true
    }
    if (aidl.mode == RomProfile.AidlControl.Mode.STOP) {
        //  没在录就**不发停止**：那一下会让录音机把收尾流程走一遍（真机上是凭空一张流体云）。
        //  查不到状态时同理不发 —— 这条通道本身不可信时，停一下的代价比「没反应」更大。
        if (state == RecorderStateBridge.State.UNKNOWN) {
            XLog.w("$tag：查不到录音状态，这一下不发停止（发了会在没录时走一遍收尾）")
            onFailure(CommandFailure.UNKNOWN)
        } else {
            XLog.w("$tag：没有在录音，这一下什么都不做（「只结束」那一档）")
        }
        return true
    }
    if (state == RecorderStateBridge.State.UNKNOWN) {
        if (aidl.mode == RomProfile.AidlControl.Mode.START) {
            //  查不到状态时**不发**那条意图：它在录音中会把正在录的停掉，而这一档的语义是「只开始」。
            //  宁可报一次失败，也不替用户结束一次录音（五、4 不伪造能力）。
            XLog.w("$tag：查不到录音状态，「只开始」这一档不发意图（发下去会把在录的停掉）")
            onFailure(CommandFailure.UNKNOWN)
            return true
        }
        //  「切换」的降级：这条意图本身就是开关，等于回到拆出「只开始 / 只结束」之前的行为
        XLog.w("$tag：查不到录音状态，交给 ROM 自己那套开关")
    }
    return false
}

/**
 * 走一次录音机的 AIDL 停录（`stopRecord`），失败就报一次「没执行成功」。
 *
 *  为什么失败要报：这条通道是那一档的**唯一**手段，静默失败的表现就是「按下去什么都没发生」。
 */
private fun stopByAidl(
    ctx: Context,
    target: RomProfile.BuiltIn,
    aidl: RomProfile.AidlControl,
    tag: String,
    onFailure: (CommandFailure) -> Unit,
) {
    if (RecorderStateBridge.stop(ctx, target.owner, aidl, tag)) {
        XLog.w("$tag → AIDL stopRecord 已发出")
    } else {
        XLog.w("$tag 的 AIDL stopRecord 没发出去（断在哪一环见上面那行），报一次失败")
        onFailure(CommandFailure.UNKNOWN)
    }
}

/**
 * `action` 起法失败之后试一次**显式组件**，返回这一次的错误（成功返回 `null`）；试不了就把 [last]
 * 原样退回去。
 *
 *  为什么要有这一层：两次起法走的是 `action + setPackage`（由清单 intent-filter 解析），清单对不上 /
 *  组件被停用时它必然失败，而显式组件能直接命中 —— 与「逐层降级」同一条纪律。
 *
 *  为什么不能无脑退：[RomProfile.BuiltIn] 的 `service` **不总是类名**（有的档把 action 串写在同一格
 *  里，见 `RomProfile` 的 `RECORDING`），拿它去拼 [`ComponentName`] 只会得到一个永远解析不到的组件，
 *  退路就成了「只会多打一条失败日志」的空动作。所以先判它像不像类名：含 `.` 且每一段都是合法 Java
 *  标识符；只有 `action` 那一支才需要退路，`action` 为空时第一次起法本就是显式组件。
 *
 *  写成**顶层**函数而不是 `AppLauncher` 的成员：它只吃入参、不吃对象状态，而那个对象的函数条数已经
 *  贴着 detekt `TooManyFunctions` 那一档。
 */
private fun tryComponentFallback(
    ctx: Context,
    feature: String,
    target: RomProfile.BuiltIn,
    last: Throwable?,
): Throwable? {
    val service = target.service
    val looksLikeClassName =
        target.action != null &&
            service.contains('.') &&
            service.split('.').all { part ->
                part.isNotEmpty() &&
                    Character.isJavaIdentifierStart(part[0]) &&
                    part.drop(1).all { Character.isJavaIdentifierPart(it) }
            }
    if (!looksLikeClassName) return last
    XLog.w(
        "oplus:$feature 的 action 起法失败（${last?.javaClass?.simpleName}: ${last?.message}），" +
            "改退显式组件 ${target.owner}/$service 再试",
    )
    return try {
        val explicit =
            Intent()
                .setComponent(ComponentName(target.owner, service))
                .apply {
                    //  退路只换「怎么定位这个服务」，**载荷不能丢**：少了 triggerType /
                    //  longPressEventType 这类 extra，服务照样被起、但已经不是用户要的那一下，
                    //  而日志还写着「退路成功」。
                    target.action?.let { action = it }
                    putExtrasOf(target.extras, feature)
                }
        if (target.foreground) ctx.startForegroundService(explicit) else ctx.startService(explicit)
        XLog.w("oplus:$feature → 显式组件退路成功（${target.owner}/${service.substringAfterLast('.')}）")
        null
    } catch (e: Throwable) {
        XLog.w("oplus:$feature 的显式组件退路也失败：${e.javaClass.simpleName}: ${e.message}")
        e
    }
}

/**
 * 把落点表里登记的那几样 extras **按原类型**搬进 Intent —— 起 / 停、正路（action + setPackage）
 * 与退路（显式组件）四条路共用这一处。
 *
 *  为什么必须共用且不能转字符串：原先起 / 停那两条用的是一个「`Int` 原样、其余转 `String`」的
 *  版本，而退路那份保着 `Long` / `Boolean` / `Float`。同一个能力走哪条路，服务端读到的类型就不一样
 *  —— 一条 `longPressEventType=false` 到了那边变成字符串 `"false"`，语义已经变了，日志还写着成功。
 *  少了 `triggerType` / `longPressEventType` 这类项同理：服务照样被起、但已经不是用户要的那一下。
 *
 *  抽成顶层函数只为让 [tryComponentFallback] 的圈复杂度、以及 [AppLauncher] 的函数条数别顶破
 *  detekt 那一档。
 */
private fun Intent.putExtrasOf(
    extras: Map<String, Any>,
    feature: String,
): Intent =
    apply {
        extras.forEach { (k, v) ->
            when (v) {
                is String -> putExtra(k, v)
                is Int -> putExtra(k, v)
                is Long -> putExtra(k, v)
                is Boolean -> putExtra(k, v)
                is Float -> putExtra(k, v)
                else -> XLog.w("oplus:$feature 的 extra $k 类型 ${v.javaClass.simpleName} 搬不过去")
            }
        }
    }

/**
 * 造出「第一次」要发的那个 Intent（起采集与结束两条路共用）。
 *
 *  有 action 的按系统自己的写法发 —— `action + setPackage`，由清单 intent-filter 解析；
 *  场景表里 `scene_owner` + action 本来就是系统发给该服务的原样。只有组件名（无 action）
 *  的才用显式组件。失败之后的退路不在这里，在 [AppLauncher.startOplusFeature]
 *  （试 [tryComponentFallback] 的显式组件）。
 *
 *  提成顶层函数只为让 [AppLauncher] 的函数数别顶破 detekt；本函数不吃对象状态。
 */
private fun buildFeatureIntent(target: RomProfile.BuiltIn): Intent =
    if (target.action != null) {
        Intent(target.action).setPackage(target.owner)
    } else {
        Intent().setComponent(ComponentName(target.owner, target.service))
    }

/**
 * 起界面之前先把屏幕点亮（[RomProfile.BuiltIn.wakeScreen] 那一档）。
 *
 *  侧键在**熄屏**时也照常被拦住（见 `docs/device/side-key.md` 三十九），而 `startActivity`
 *  本身不会点亮屏幕 —— 不补这一下，相机 / 计算器在幕后起来了，用户看到的仍是黑屏。
 *  已经亮着就什么都不做（不亮屏比亮屏更不该发生的是「多改了一次屏幕状态」）。
 *
 *  点不亮**不拦着**起界面：那是两件事，界面在幕后起来总比连起都不起好，失败只落一行日志。
 */
private fun wakeForFeature(
    ctx: Context,
    feature: String,
) {
    try {
        val power = ctx.getSystemService(android.os.PowerManager::class.java)
        if (power == null || power.isInteractive) return
        requestWake(power, "opluskey:$feature")
        XLog.w("oplus:$feature → 熄屏，先点亮屏幕")
    } catch (e: Throwable) {
        XLog.w("oplus:$feature 点亮屏幕失败（界面仍会起来，但可能看不见）：${e.javaClass.simpleName}: ${e.message}")
    }
}

/**
 * 这一档**只在锁屏上带**的载荷（[RomProfile.BuiltIn.keyguardExtras]）—— 不在安全锁屏上就是空的。
 *
 *  为什么要现读状态而不是一律带上：相机那两个来源标记说的是「这一屏是从锁屏进来的」，它据此收起
 *  解锁后才该有的那几样。解锁状态下按同一颗键就是一次普通启动，不能这么说。
 */
private fun lockedExtras(
    target: RomProfile.BuiltIn,
    ctx: Context,
): Map<String, Any> = if (target.keyguardExtras.isEmpty() || !onSecureKeyguard(ctx)) emptyMap() else target.keyguardExtras

/**
 * 此刻是不是停在**安全锁屏**上（与 SystemUI 发锁屏来源标记的条件同口径：显示中 + 有锁）。
 *
 *  只给 [lockedExtras] 用：读不到 / 抛异常一律按「不在锁屏上」处理 —— 少带两个来源标记只影响
 *  相机那一屏的形态，不影响起不起得来（相机的 `showWhenLocked` 写在清单里）。
 *  这里用 `try/catch (Throwable)` 而不是 `runCatching`：读不到是**预期内的失败**，但也可能是
 *  系统服务异常（`Error` 系），这一处必须自己收住，不能让异常漏到按键回调（五之二、3）。
 */
private fun onSecureKeyguard(ctx: Context): Boolean =
    try {
        val keyguard = ctx.getSystemService(android.app.KeyguardManager::class.java)
        keyguard?.isKeyguardLocked == true && keyguard.isKeyguardSecure
    } catch (e: Throwable) {
        XLog.w("读锁屏状态失败（按未锁屏处理）：${e.javaClass.simpleName}: ${e.message}")
        false
    }

/**
 * 目标 → 入口 Intent。
 *
 *  分身：主用户的 PM 看不见那个包，[AppEntry.intentOf] 必然落空 —— 这时按 userId 走 binder
 *  再问一次（[AppEntry.resolveAsUser]）。只给了包名才这么退：用户点名了某个 Activity 时
 *  不要另挑一个顶上。
 *
 *  提成顶层函数与 [putExtrasOf] 同一个原因：这个类的函数条数贴着 detekt `TooManyFunctions` 那一档。
 */
private fun entryIntent(
    pm: android.content.pm.PackageManager,
    target: String,
    pkg: String,
    userId: Int,
): Intent? =
    AppEntry.intentOf(pm, target)
        ?: if (userId != 0 && AppTarget.activity(target) == null) {
            AppEntry.resolveAsUser(pkg, userId)
        } else {
            null
        }
