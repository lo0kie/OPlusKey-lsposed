package com.lookie.opluskey.command

import android.graphics.Rect

/**
 * 命令要落地时，**接入方**必须提供的那几样能力 —— 本层唯一向外的出口。
 *
 * ## 为什么是端口，不是直接调
 *
 *  这一层能搬出来的前提是「它不认识本模块的自有通道」。而下面这几件事恰恰只能由**某一个**
 *  模块提供：状态栏 / 小窗 / 系统开关那几条都是「宿主发广播、注入侧在 system_server 里干」——
 *  广播的 action、包名、附加字段全是本模块的私有协议（`StatusBarBridge` / `FlexibleWindowBridge`
 *  / `SettingBridge`），注入侧那一半又依赖 ROM 的挂点候选表（`bridge/Framework`），
 *  而那几张表被注入侧与本模块共用、不能下移。
 *
 *  所以把它收成接口：这一层只说「要发一条系统指令 / 要注入一颗键 / 要把某个任务收成小窗」，
 *  至于怎么发、发给谁、走不走广播，由接入方决定。新模块接进来时**这一层一行都不用改**，
 *  只要给一份自己的实现；而本模块的实现就是那几个 bridge（`bridge/CommandPlatformImpl`）。
 *
 * ## 为什么方法名不照抄 bridge 的那几个
 *
 *  照抄就把「本模块的私有协议」写进了这一层的词汇表（`CMD_MINI` 这种字符串一旦出现在这里，
 *  接入方也只能跟着叫这个名字）。所以这里按**意图**命名：`collapseToMini` / `animateToMini`
 *  —— 本模块的实现把它们翻成自己那两条广播。
 */
interface CommandPlatform {
    /**
     * 一条系统指令，落点由接入方决定 —— 本模块是「静音广播 → 注入侧在 system_server 里执行」。
     *
     * @param command 语义名（`notifications` / `control_center` / `recents` / `power_menu` /
     *   `voice_assist`）。**这些名字是这一层与接入方之间的约定**，不是任何 bridge 的常量。
     */
    fun sendSystemCommand(command: String)

    /**
     * 让系统时钟**开始**数一个 [seconds] 秒的倒计时（不自己计时）。
     *
     * @return 请求发出去了没有 —— 发不出去时执行侧要退回「把倒计时页调出来」那一档，
     *   否则用户按下去既没倒计时、也没有任何反馈。
     */
    fun sendTimer(seconds: Int): Boolean

    /** 把背光推到目标值（`0..255` 那一档）；写不进去由接入方记日志。 */
    fun sendBrightness(target: Int)

    /**
     * 切一个系统开关。
     *
     * @param id 开关标识（`SettingSwitch` 那一侧定义的名字）
     * @param on `null` = 「由执行方自己读当前状态再取反」
     */
    fun sendSetting(
        id: String,
        on: Boolean?,
    )

    /**
     * 「结束应用」这一档的判据：这个包是不是**停不得**的那几个（系统自己 / 宿主 / 模块）。
     *
     *  规则本身属于接入方 —— 那几项说的是「谁在跑这套东西」，写在这一层就等于把本模块的
     *  包名搬进通用层。执行侧只是拿它先给用户一句明话。
     */
    fun isStopForbidden(pkg: String): Boolean

    /**
     * 请执行方停掉 [pkg]（`proc:stop:包名`）。
     *
     * @return 请求**发出去了**没有。它不表示真的停成了 —— 这条通道是单向的，成败由执行方记日志。
     */
    fun stopPackage(pkg: String): Boolean

    /**
     * 注入一颗按键事件。
     *
     * @return 真的注入成功了没有 —— 注入不成立时调用方要报一条失败给用户
     *   （「动作执行了、屏幕没反应」在用户侧等于没反应）。
     */
    fun injectKey(keyCode: Int): Boolean

    /**
     * 把某个任务收成小窗。
     *
     * @param bounds 应用配置矩形（**不是**屏幕矩形）；`null` = 用接入方的兜底值
     * @param scale 显示缩放；`null` = 用接入方的兜底值
     */
    fun collapseToMini(
        taskId: Int,
        bounds: Rect?,
        scale: Float?,
    )

    /** 让窗口从「全屏外观」缩到「小窗外观」—— 动画参数由调用方算好。 */
    fun animateToMini(
        taskId: Int,
        params: WindowAnimParams,
    )
}

/**
 * 小窗收起的起止状态 —— **这些就是要反复试的参数**，所以它们由调用方算、经端口下发，
 * 改它们不用重新编译接入方。
 */
class WindowAnimParams(
    val startRect: Rect,
    val finishRect: Rect,
    val startScale: Float,
    val finishScale: Float,
    val startAlpha: Float,
    val finishAlpha: Float,
    val durationMs: Long,
)

/**
 * [CommandPlatform.sendSystemCommand] 里那几个语义名 —— **这一层与接入方之间的唯一约定**。
 *
 *  不直接引用任何 bridge 的常量：那些是「本模块怎么发」的细节（广播 action、附加字段同族），
 *  下移到这一层就把接入方的私有协议变成了通用词汇。
 */
object SystemCommands {
    /** 拉下通知中心。 */
    const val NOTIFICATIONS = "notifications"

    /** 拉下控制中心。 */
    const val CONTROL_CENTER = "control_center"

    /** 打开最近任务。 */
    const val RECENTS = "recents"

    /** 弹电源菜单。 */
    const val POWER_MENU = "power_menu"

    /** 唤起语音助手（本模块是请注入侧以系统身份起语音服务）。 */
    const val VOICE_ASSIST = "voice_assist"

    /** 停一个包（结束应用）。 */
    const val STOP_PACKAGE = "stop_package"
}
