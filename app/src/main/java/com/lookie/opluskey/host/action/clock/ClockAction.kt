package com.lookie.opluskey.host.action.clock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.lookie.opluskey.bridge.StatusBarBridge
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.host.action.CommandFailure

/**
 * 倒计时那条命令的执行（`timer:`）。
 *
 * ## 要的是「已经在跑」，不是「把页面打开」
 *
 *  落点带 `AlarmClock.EXTRA_SKIP_UI = true` —— 这个 extra 的语义就是「别停下来问我，直接开始」。
 *  少了它，ColorOS 会停在编辑页上等用户点开始，那就成了「打开一个界面」，而这条命令要的是
 *  **计时已经在走**（真机验过：带上它，状态栏直接开始倒数，界面不弹出来）。
 *
 * ## 标题改不了（别再加 `EXTRA_MESSAGE`）
 *
 *  那条通知 / 胶囊上写的 `Start Timer Test` 是 **ColorOS 时钟自己写死的**：
 *  `HandleApiActivity` 处理 `SET_TIMER` 时直接 `timerSchedule.s("Start Timer Test")`，
 *  **一个 extra 都不读** —— `AlarmClock.EXTRA_MESSAGE` 传什么都不生效（真机试过，标题仍是那一串）。
 *  想换成中文只有两条路，两条都不做：hook 时钟进程改那个字符串（不在模块作用域），
 *  或者自己发通知（那就没有状态栏倒数与到点响铃了，正是这条命令要借时钟的原因）。
 *
 * ## 为什么不自己计时
 *
 *  模块跑在宿主进程里、没有任何界面：倒计时要挂在状态栏 / 通知栏上、要在响的时候把用户叫醒 ——
 *  这件事只有时钟应用做得到。所以这条命令把「开始」这一步交给时钟，模块只负责按下去。
 *
 * ## 两条路，为什么不一样
 *
 *  · **带秒数**走标准的 `AlarmClock.ACTION_SET_TIMER` —— ColorOS 的 `HandleApiActivity` 接它，
 *    那个是**导出的**，任何机型都认这一条。但这一下**必须由 system_server 发起**
 *    （`StatusBarBridge.sendTimer`）：宿主是普通应用 uid，按键那一刻它不在前台，自己
 *    `startActivity` 会被后台起活动那道闸静默丢掉 —— 真机上就是「按了没反应、也没有流体云」，
 *    而同一个 intent 用 `am start` 一起就有；
 *  · **不带秒数的 `timer:`**（只要求打开倒计时页）只能落到时钟自己的 `AlarmClockCTS` 上 ——
 *    它 `exported=false`，普通应用起不动，只有持有 `START_ANY_ACTIVITY` 的宿主才进得去。
 *
 *  ## 秒表为什么不在这里
 *
 *  秒表**没有任何外部入口**：清单里那条 `stopwatch.ACTION_START3` 没有一行代码读它（发过去只是
 *  打开时钟主界面），真正起秒表的是不导出的 `StopWatchService`，而它只处理「继续 / 暂停 / 停止」
 *  这类已有会话的动作。取证与结论见 `core/present/ClockCatalog` 的类注释。
 *
 * ## 起不来怎么办
 *
 *  先退回时钟应用的主界面（总比什么都不发生好）；连主界面都起不来才报失败 —— 那说明这台
 *  机器压根没装 ColorOS 时钟。
 */
internal object ClockAction {
    /** ColorOS 时钟的包名。 */
    private const val PKG = "com.coloros.alarmclock"

    /** 倒计时那一页（`exported=false`，见类注释）。 */
    private const val CTS = "com.oplus.alarmclock.cts.AlarmClockCTS"

    /** 「打开倒计时页」—— ColorOS 自己的 action（不是 AOSP 的）。 */
    private const val ACTION_TIMER = "com.oplus.alarmclock.Timer.ACTION_START2"

    /**
     * 倒计时（`timer:<秒>` / `timer:`）。
     *
     *  带秒数：交给 **system_server** 起（`StatusBarBridge.sendTimer`）—— 宿主自己
     *  `startActivity` 会撞后台起活动那道闸，被静默丢掉（见 [setTimer] 的注释）。
     *  不带秒数的 `timer:` 只要求把倒计时页调出来，仍走本进程那一条（best effort）。
     *
     * @param seconds 非空 = **直接开始**一个这么长的倒计时（不弹确认页）；`null` = 只把倒计时页
     *   调出来，时长由用户自己拨 —— 那种情况下「开始」这件事只能等用户按
     */
    fun timer(
        context: Context?,
        seconds: Int?,
        onFailure: (CommandFailure) -> Unit,
    ) {
        if (context == null) {
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        if (seconds != null) {
            //  这一条**不靠起界面完成**：广播交给注入侧，由 system_server 自己起那个活动。
            //  广播都发不出去时不能就这么收场：按下去了、倒计时没跑、屏幕上也没有任何反馈，
            //  用户只会以为「这颗键坏了」。按类注释那条降级顺序退回倒计时页，让用户自己按开始。
            if (!setTimer(context, seconds)) {
                XLog.w("timer:$seconds 的广播没发出去 —— 退回倒计时页")
                openPage(context, ACTION_TIMER, onFailure)
            }
            return
        }
        //  不带秒数：把倒计时页调出来，让用户自己拨
        openPage(context, ACTION_TIMER, onFailure)
    }

    /**
     * 标准的「起一个倒计时」—— `AlarmClock.ACTION_SET_TIMER` + `EXTRA_LENGTH`（秒），
     * **由 system_server 代起**。
     *
     *  ## 为什么不在宿主里直接 startActivity
     *
     *  真机上按下去什么都没发生（也没有流体云），而同样的 intent 用 `am start` 一起就有。
     *  差别在**后台起活动**（BAL）：宿主是普通应用 uid，按键那一刻它不在前台，`startActivity`
     *  被系统直接丢掉 —— 而且**不抛异常**，所以原先那个「起不来就退回打开页面」的判据永远不成立，
     *  连日志都没有。走 `StatusBarBridge` 之后这一下由 system_server 发起，那道闸不适用。
     *
     *  **`EXTRA_SKIP_UI = true` 是这条命令的关键**：它的语义就是「别停下来让我确认，直接开始」。
     *  不传这一位的话 ColorOS 会停在「设一个倒计时」的编辑页上等用户点开始 —— 那成了「打开界面」，
     *  而这条命令要的是**已经在跑**。
     */
    private fun setTimer(
        context: Context,
        seconds: Int,
    ): Boolean = StatusBarBridge.sendTimer(context, seconds)

    /** 起时钟里那一页；失败退回主界面，再失败才报出去。 */
    private fun openPage(
        context: Context,
        action: String,
        onFailure: (CommandFailure) -> Unit,
    ) {
        val started =
            runCatching {
                context.startActivity(
                    Intent(action)
                        .setComponent(ComponentName(PKG, CTS))
                        //  SKIP_UI 一起带上：标准 extra，认它就直接开始、不认也无害
                        //  （少了这一位只会更慢一点，不会出错）。
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                //  这里失败是**预期内**的：非 ColorOS 机型没有这个组件，或者宿主没拿到
                //  `START_ANY_ACTIVITY`（换了个宿主）。所以只记一行，接着试主界面
                XLog.w("时钟：起 $action 失败（这台机器的时钟可能不是 ColorOS 那份）", it)
            }.isSuccess
        if (started) return
        //  退回主界面：倒计时就在那几个 tab 里，用户点一下也能到
        val fellBack =
            runCatching {
                context.packageManager.getLaunchIntentForPackage(PKG)?.let {
                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } != null
            }.getOrDefault(false)
        if (!fellBack) {
            XLog.w("时钟：连主界面都起不来（这台机器没装 $PKG？）")
            onFailure(CommandFailure.UNKNOWN)
        }
    }
}
