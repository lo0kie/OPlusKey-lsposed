package com.lookie.opluskey.host.action

import android.content.Context
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.host.action.MiniWindowFacts.MINI_VERIFY_MS
import com.lookie.opluskey.host.action.MiniWindowFacts.ZOOM_LAUNCH_FLAG_USER_POSITION

/**
 * `appshortcut:包名/快捷方式id` 的执行 —— 起一个**应用自己注册的**快捷方式（扫一扫 / 收付款）。
 *
 * ## 起法只有一处：[ShortcutStarter]
 *
 *  快捷方式那条 intent 里往往只有 action 与 `PersistableBundle`（微信就是
 *  `ShortCutDispatchAction` + `LaunchType=launch_type_scan_qrcode`），所以**不能自己拼** intent；
 *  `ShortcutInfo.getIntent()` 在真机上取回来又是 `null`（支付宝、微信那几条动态快捷方式都是）。
 *  ⇒ 只能靠 `LauncherApps`：要么 `startShortcut`（系统代发），要么 `getShortcutIntent` 拿 PI 自己发。
 *
 *  后者是本仓唯一能过 BAL 的那条：系统代发会被 `BAL_BLOCK` 静默拦掉，**调用侧看不出任何异常**
 *  （真机 PLK110 / Android 16：
 *  `Background activity launch blocked! … realCallerUidHasVisibleActivity: false`
 *  + `START … (BAL_BLOCK) result code=102`）。两条起法及其顺序的理由都写在 [ShortcutStarter] 里。
 *
 * 权限：`getShortcuts` 与 `startShortcut` 在系统侧都过 `ensureShortcutPermission`，非桌面应用
 *  要靠 `ACCESS_SHORTCUTS`（宿主声明且已 grant，真机实测过）—— 拿不到就是 `SecurityException`，
 *  这里如实报「没有入口」，不假装成功。
 *
 * ## 小窗那一档（`appshortcut:w:…`）怎么做
 *
 *  见 [launchInWindow]：已在小窗 ⇒ 不动；全屏且在最前面 ⇒ 原地收；
 *  其余 ⇒ **起一次**（PI 带上窗口模式）**再等它进表、收它**（[ShortcutThenMinimize]）。
 *  全过程只发一次启动请求 —— 多发一次就多一份任务，用户看到的是更长的等待。
 *
 * 单独一个类而不是挂进 [AppLauncher]：那个类已经贴着 detekt 的函数数上限，而这一档与它
 *  那些「拉起界面 / 发广播」的方法没有共用状态（只借 Context、小窗执行器与失败出口）。
 */
internal class AppShortcutLaunch(
    private val context: () -> Context?,
    /** 小窗那一档的验收与兜底（`appshortcut:w:…`）—— 与 `window:包名` 共用同一套判据。 */
    private val mini: MiniWindowLauncher,
    private val onFailure: (CommandFailure) -> Unit,
) {
    /**
     * 「先起、再原地收」那一支 —— 拆成单独的类只为压函数数（见 [ShortcutThenMinimize]）。
     *
     * 用 `by lazy` 而不是构造参数：本类已经挂着函数数上限；而这段只在「需要起一次」那一档
     * 才会被用到，晚一点建没有代价。
     */
    private val thenMinimize: ShortcutThenMinimize by lazy {
        ShortcutThenMinimize(mini, onFailure) { pkg, shortcutId, userId, taskId ->
            minimize(pkg, shortcutId, userId, taskId)
        }
    }

    /** @param window true = 用小窗起（`appshortcut:w:…`） */
    fun launch(
        pkg: String,
        shortcutId: String,
        userId: Int,
        window: Boolean,
    ) {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，appshortcut:$pkg/$shortcutId 跳过")
                return
            }
        if (window) {
            launchInWindow(ctx, pkg, shortcutId, userId)
            return
        }
        startPlain(ctx, pkg, shortcutId, userId)
    }

    /**
     * 小窗那一档 —— 只有三种处置，按「目标此刻是什么状态」选：
     *
     *  1. 已经在小窗 ⇒ **什么都不做**。重发会走一遍应用自己的跳板链（支付宝是
     *     `ShortcutsLauncherActivity → SchemeLauncherActivity → LauncherActivity.alias`），
     *     链尾把主界面起成**全屏**任务、把小窗顶掉 —— 真机日志：
     *     `Add Task{#1194 …} to hidden list because adding Task{#1197 …}`，紧接着
     *     `notifyTaskRemoved #1194`。用户的观感就是「再按一下，小窗没了，出来个全屏」。
     *     代价：小窗被收起（右上角那条）时按一下不会展开。两害相权取轻 —— 现在的行为是
     *     「把小窗换成全屏」，比「不展开」恶劣得多。要做对得能从 `OplusAppInfo` 里分出
     *     「展开 / 收起」两档，那个判据没在真机上验过（`appBounds` 两档同值，见 `MiniWindowFacts`）。
     *  2. 有全屏任务、**而且就在最前面** ⇒ **原地收小窗**。用户正看着它，收一下立刻见效，
     *     不必再起一份。判据必须是 [MiniWindowLauncher.foregroundTaskFor]（只看表里第 0 项）：
     *     按包名扫整张表会把**后台**那份全屏任务也捞进来，那样「按一下只把它收成小窗、
     *     屏幕一动不动」，用户看到的是没反应。
     *  3. 其余（没有任务 / 只有后台任务）⇒ **起一次，等它进表，收它**。
     *
     * ## 为什么 3 那一条直接发 PI，而不是先试 `startShortcut`
     *
     *  `startShortcut` 带窗口模式 options 在真机上经常**整发不落地**（表里连任务都不会出现），
     *  而自发 PI 通常 0.7 秒就进表；它是唯一能过 BAL 的那条。所以只发一次、
     *  由 [ShortcutStarter.send] 保证先走 PI 且带上窗口模式 ⇒ 要么直接起成小窗，
     *  要么 1.4 秒内收一下。取证见 `docs/zoom-window.md` 的「快捷方式以小窗打开」一节。
     *
     *  单拆一个函数是为了让 [launch] 的出口数量留在 detekt 的 `ReturnCount` 之内。
     */
    private fun launchInWindow(
        ctx: Context,
        pkg: String,
        shortcutId: String,
        userId: Int,
    ) {
        //  分身**不复用已有任务**：`taskOfPackage` / `foregroundTaskFor` 只按包名比，而主用户与
        //  分身同包名 —— 主用户那份在前台时，这一下收走的是**主应用**（真机反馈：
        //  「在主应用上绑分身小窗，主应用变成了小窗」）。直接起一次，由 `thenMinimize` 按
        //  「发之前 / 发之后」的差集认领新任务。
        if (userId != 0) {
            XLog.w("快捷方式 w：$pkg 是分身（user=$userId）⇒ 跳过「复用已有任务」，直接起")
            thenMinimize.start(ctx, pkg, shortcutId, userId, round = 0)
            return
        }
        val existing = mini.taskOfPackage(pkg)
        // 判据级：这一行是「这次为什么没走成小窗」的第一个分叉，没有它只能靠猜。
        XLog.w("快捷方式 w：$pkg 现有任务 #$existing（小窗=${existing > 0 && mini.zoomStateOf(existing) == true}）")
        if (existing > 0) {
            if (mini.zoomStateOf(existing) == true) {
                XLog.w("快捷方式：$pkg 已在小窗（任务 #$existing），不重起（重起会把它顶成全屏）")
                return
            }
            //  只有「它在最前面」才原地收：收一份后台任务屏幕不会有任何变化，等于按了没反应。
            val foreground = mini.foregroundTaskFor(pkg)
            if (foreground > 0) {
                XLog.w("快捷方式：$pkg 在全屏前台（任务 #$foreground）⇒ 原地收成小窗")
                minimize(pkg, shortcutId, userId, foreground)
                return
            }
            XLog.w("快捷方式：$pkg 有全屏任务 #$existing 但在后台 ⇒ 起一次再收（收后台那份屏幕不会变）")
        }
        thenMinimize.start(ctx, pkg, shortcutId, userId, round = 0)
    }

    /**
     * 普通起法（全屏）—— `appshortcut:包名/id` 不加 `w:` 时的那一档。
     *
     * 不带窗口模式启动，让系统按默认窗口模式起。起法与窗口那一档**同一个出口**
     * （[ShortcutStarter.send]）：先自发 PI（唯一能过 BAL 的那条），拿不到才系统代发。
     * 区别只在 `windowOptions` 传 `null`。
     *
     * **不退成「打开主应用」**：这条走的仍是快捷方式那条 intent，参数不会丢。
     */
    private fun startPlain(
        ctx: Context,
        pkg: String,
        shortcutId: String,
        userId: Int,
    ) {
        if (ShortcutStarter.send(ctx, ShortcutStarter.Request(pkg, shortcutId, userId, windowOptions = null))) return
        // 连发都没发出去 —— 最常见是那条快捷方式已被应用自己删掉（动态快捷方式随登录状态变）。
        // 报「没有入口」而不是「已卸载」：包明明还在。
        onFailure(CommandFailure.APP_NO_ENTRY)
    }

    /**
     * 把目标那份任务**原地**收成小窗，收完再验一轮。
     *
     * @param taskId **点名**要收哪一份。这个参数是必需的（不能在里面按包名现扫）：同一个包
     *   可能同时有多份任务，扫出来的可能是「小窗那份」（那就什么都不用做）或「跳板链正在起的那份」
     *   （收了立刻被下一跳顶掉）。调用方本来就知道自己要收哪一份 —— 要么是当时在台前的那份，
     *   要么是我们自己那一发刚开出来的那份。
     */
    internal fun minimize(
        pkg: String,
        shortcutId: String,
        userId: Int,
        taskId: Int,
    ) {
        if (taskId <= 0) {
            XLog.w("收小窗：没有点名任务（$pkg/$shortcutId）")
            return
        }
        if (mini.zoomStateOf(taskId) == true) {
            XLog.w("快捷方式 → 小窗 $pkg/$shortcutId（user=$userId，任务 #$taskId）")
            return
        }
        val code = mini.startFlexibleFromRecentsCode(taskId, ZOOM_LAUNCH_FLAG_USER_POSITION)
        if (code == null || code < 0) {
            XLog.w("收小窗：任务 #$taskId 没受理（返回 $code，$pkg/$shortcutId），保持原窗口")
            onFailure(CommandFailure.APP_NO_MINI_WINDOW)
            return
        }
        mini.postDelayedGuarded("appshortcut:$pkg/$shortcutId 的收小窗验收", MINI_VERIFY_MS) {
            when {
                mini.zoomStateOf(taskId) == true -> {
                    XLog.w("快捷方式 → 原地收成小窗 $pkg/$shortcutId（user=$userId，任务 #$taskId）")
                }

                // 任务没了 ⇒ 跳板链把它换掉了。这时候**不再自己追新的那份**：新任务会被
                // 下一跳的界面自己顶成全屏，追过去收是跟应用的导航链赛跑，输多赢少。
                !mini.hasTask(taskId) -> {
                    XLog.w("收小窗：任务 #$taskId 已被跳板链换掉（$pkg/$shortcutId），下一次按下再处理")
                }

                else -> {
                    XLog.w("收小窗：任务 #$taskId 仍是全屏（$pkg/$shortcutId），放弃")
                    onFailure(CommandFailure.APP_NO_MINI_WINDOW)
                }
            }
        }
    }
}
