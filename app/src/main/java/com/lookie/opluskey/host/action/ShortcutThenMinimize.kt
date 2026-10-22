package com.lookie.opluskey.host.action

import android.content.Context
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.host.action.MiniWindowFacts.MINI_VERIFY_MS
import com.lookie.opluskey.host.action.MiniWindowFacts.SHORTCUT_VERIFY_RETRIES

/**
 * 快捷方式「起一次、等它进表、收它」—— `appshortcut:w:包名/id` 的第三条分支
 * （目标此刻不在小窗、也不在全屏前台时走这里）。
 *
 * ## 一次只发一发，且那一发带着窗口模式
 *
 *  起法只有 [ShortcutStarter] 一处（PI 自发优先，那是唯一能过 BAL 的；拿不到 PI 才系统代发）。
 *  这里多传一份 [ShortcutStarter.zoomOptions]：**窗口模式写在同一份 options 里**，
 *  服务端认了就从小窗起，不必再收。
 *
 * ## 重试轮只等、绝不补发
 *
 *  `getAllTopAppInfos()` 对**刚发出、还在注册**的那份任务读到的是 `-1`，所以「查不到」
 *  不等于「没起来」。在这里补发一次，跳板链会把两份任务串起来、第二份顶掉第一份 ——
 *  用户看到的是「按一下，等好几秒窗口才出来」。⇒ 多等的那几轮**不发任何东西**。
 *
 *  轮数只有 [SHORTCUT_VERIFY_RETRIES]（=2）次、每次 [MINI_VERIFY_MS]（=700ms）⇒ 上限 1.4 秒；
 *  PI 那一发通常 0.7 秒就进表，正常路径只等一轮。
 *
 * ## 为什么收到 taskId 之后交给回调，而不是自己收
 *
 *  「收成小窗」那一步与 [AppShortcutLaunch.launchInWindow] 里「已有全屏任务 ⇒ 原地收」
 *  完全是同一件事（同一套 `onFailure` + 验收）。两边各写一份很快就会走成两套判据，
 *  所以这里只管「把任务弄出来」，收到 taskId 就交回去（那边是 `minimize`）。
 *
 * 单拆一个类是为压 [AppShortcutLaunch] 的函数数（detekt `TooManyFunctions`，阈值 11）。
 * `Context` 由 [start] 逐次传进来、不在这里存 —— 这支只在需要起一次时才会跑。
 */
internal class ShortcutThenMinimize(
    private val mini: MiniWindowLauncher,
    private val onFailure: (CommandFailure) -> Unit,
    /** 拿到「我们自己这一发开出来的那份任务」之后怎么收 —— 由调用方决定（与 `minimize` 同一套）。 */
    private val minimize: (pkg: String, shortcutId: String, userId: Int, taskId: Int) -> Unit,
) {
    /**
     * 起一次，等它进表，再交给 [minimize]。
     *
     * @param round 0 = 真去起；后面几轮只是「等它进表」，**绝不再发**（理由见类注释）
     */
    fun start(
        ctx: Context,
        pkg: String,
        shortcutId: String,
        userId: Int,
        round: Int,
    ) {
        //  分身：先记下「发之前表里已有哪几份任务」，之后只认新出现的那一份 ——
        //  表里没有 userId，按包名收会把主用户那份收成小窗（同包名）。
        val before = if (userId != 0) mini.tasksOfPackage(pkg) else emptySet()
        if (round == 0 && !launch(ctx, pkg, shortcutId, userId)) return
        await(pkg, shortcutId, userId, round, before)
    }

    /**
     * 发一次快捷方式 —— **这一次就带着窗口模式**，不留「先起成全屏再收」那一步。
     *
     * 为什么必须在这里带上：不带窗口模式的 PI 一定先起成全屏，用户看到「先闪一屏全屏、
     * 再缩成小窗」。带上之后两种可能：服务端认了 ⇒ 直接就是小窗（`minimize` 里会判
     * 「已是小窗」直接返回，不再收）；不认 ⇒ 1.4 秒内收一下。
     *
     * @return false = 连发都没发出去，调用方不必再等
     */
    private fun launch(
        ctx: Context,
        pkg: String,
        shortcutId: String,
        userId: Int,
    ): Boolean {
        val request = ShortcutStarter.Request(pkg, shortcutId, userId, ShortcutStarter.zoomOptions())
        if (ShortcutStarter.send(ctx, request)) return true
        onFailure(CommandFailure.APP_NO_ENTRY)
        return false
    }

    /**
     * 等我们自己那一发开出来的任务进表。
     *
     * 判据用 [MiniWindowLauncher.taskOfPackage]（扫整张表）而不是「按 taskId 点名」：
     * 点名的前提是知道 id，而**这条路上我们从来拿不到 id** —— `PendingIntent.send` 与
     * `startShortcut` 都不回执。所以这里只能按包名等，等的对象是「表里出现了这个包的任务」，
     * 等到了就交给 [minimize] 去收（那边会再确认它是不是小窗、是不是我们要的那一份）。
     */
    private fun await(
        pkg: String,
        shortcutId: String,
        userId: Int,
        round: Int,
        before: Set<Int>,
    ) {
        mini.postDelayedGuarded("appshortcut:$pkg/$shortcutId 的先起后收（第 ${round + 1} 次）", MINI_VERIFY_MS) {
            val taskId = mini.taskOfPackage(pkg, exclude = before)
            when {
                taskId > 0 -> {
                    XLog.w("先起后收：$pkg 的任务 #$taskId 已进表，收它")
                    minimize(pkg, shortcutId, userId, taskId)
                }

                round < SHORTCUT_VERIFY_RETRIES -> {
                    XLog.w("先起后收：$pkg 还没有任务，再等一轮（不再重发）")
                    await(pkg, shortcutId, userId, round + 1, before)
                }

                else -> {
                    XLog.w("先起后收：$pkg 一直没起来（放弃）：$pkg/$shortcutId")
                    onFailure(CommandFailure.APP_NO_ENTRY)
                }
            }
        }
    }
}
