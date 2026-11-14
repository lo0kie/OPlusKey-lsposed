package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action

/**
 * 「计时器」那一格 —— **内置快捷功能**，不是「支持的命令格式」里的一条，也没有下一层菜单：
 * 点它直接挑时长，落成 `timer:<秒>`。
 *
 * ## 为什么只有倒计时，没有秒表
 *
 *  ColorOS 的秒表**没有任何外部入口**（真机逆向结论）：
 *
 *   · 时钟里唯一声明了 `stopwatch.ACTION_START3` 的是 `AlarmClockCTS`，可那个类里没有一行代码读这串
 *     action（全包的字符串表里搜不到），它实际是 `AlarmClock` 的子类 ⇒ 发过去只会**打开时钟主界面**，
 *     秒表一个数都不走；
 *   · 真正起秒表的是 `stopwatch.StopWatchService`，它 `exported=false`，且只处理「继续 / 暂停 /
 *     停止」这类**已有会话**的通知动作（`stopwatch_action_type`），冷启动传进去也不会开始计时；
 *   · 界面上没有导出活动、没有快捷方式、没有广播入口能落到秒表那一页。
 *
 *  所以这一格只做**倒计时** —— 那条走的是标准 `AlarmClock.ACTION_SET_TIMER` + `EXTRA_SKIP_UI`，
 *  真机上验过：键一按，状态栏直接开始倒数，界面不弹出来。
 *
 * ## 为什么单独一份，而不并进 [ActionCatalog]
 *
 *  那一份清单已经贴着 `LargeClass` / `TooManyFunctions` 的阈值（见 `config/detekt/detekt.yml`），
 *  再加一族当场顶破。更实际的理由是**归属**：这一族有自己的落脚点（时钟应用）、自己的取值形态
 *  （`timer:<秒>` 带时长），单独一处反而能把「模块不自己计时」那件事讲清楚。
 *
 * ## 为什么这一族不列进「支持的命令格式」卡
 *
 *  那张卡的收录规则是「只列没有图形入口的格式」（见 `ui/model/text/PageCopy`）：这一格就是它的
 *  入口。但 token 仍在 `Action.PREFIXES` 里 —— 从格子里挑出来的动作还要能当**单条命令的正文**
 *  存下来，那一步只过 `hasSupportedFormat` 这一道闸。
 */
object ClockCatalog {
    /**
     * 「计时器」那一格的标记 —— **永不写进配置**（`Action.parse("clock:")` 认不出 ⇒ `none`）。
     *
     *  **这一格自己就是那个动作**（没有下一层菜单）：点它直接弹时长弹窗，挑完落成 `timer:<秒>`。
     *  与录屏 / 录音那几格同一个形态 —— 格子名就是动作名，中间不再夹一层只写着同一句的小标题。
     */
    const val TILE = "clock:"

    /**
     * 一分钟多少秒 —— [durationLabel] 与时长弹窗共用。
     *
     *  落盘的取值是**秒**（`timer:300`，标准 `EXTRA_LENGTH` 也只认秒），界面上念的是分 / 秒 ——
     *  换算只能有一份，否则 `300` 在一处念「5 分钟」、在另一处念「300 秒」。
     */
    const val MINUTE_SECONDS = 60

    /** 网格里那一格（也是挑完之后那一条的名字来源）。 */
    val TILE_SPEC: ActionCatalog.Spec =
        ActionCatalog.Spec(name = Strings.CATALOG_CLOCK, token = TILE, glyph = ActionCatalog.Glyph.Timer)

    /**
     * 这一格是不是「还要问一个时长」的计时器；界面层据此弹时长弹窗、据此判「这一格绑了没有」。
     *
     *  与 `ActionCatalog.brightnessModeOf` 同一个分工：判据留在模型里，`ui` 不认 token 字面量。
     */
    fun isTimerTile(token: String): Boolean = token == TILE

    /**
     * 选定时长之后的那一条（`timer:300`）—— 弹窗按完「确定」时交给落盘那一侧。
     *
     *  名字与格子同一颗（「计时器」）：时长是这一条的**取值**，由 [durationLabel] 在提示与格子里
     *  那行小字里念（`计时器 - 5 分钟`），不写进名字。
     */
    fun timerSpecOf(seconds: Int): ActionCatalog.Spec =
        ActionCatalog.Spec(
            name = Strings.CATALOG_CLOCK,
            token = Action.Timer(seconds).token,
            glyph = ActionCatalog.Glyph.Timer,
        )

    /**
     * 这一条当前落在**多长的时长**上（`5 分钟`）；别的动作返回 `null`。
     *
     *  格子那行小字与句子里的状态段都读它：不带时长的 `timer:`（老配置、只打开页面那一档）
     *  返回 `null` —— 那一档确实没有时长可说。
     */
    fun durationOf(action: Action): String? = (action as? Action.Timer)?.seconds?.let(::durationLabel)

    /**
     * 秒数念成人话 —— `5 分钟` / `30 秒` / `1 分 30 秒`。
     *
     *  时长弹窗那一行实时文案与 [durationOf] 共用这一处：两处各拼一遍的话，「90 秒」在一处
     *  念成 `2 分钟`、在另一处念成 `90 秒`，同一件事两个说法。
     *
     *  整分只说分（`300` ⇒ `5 分钟`，不念 `5 分 0 秒`）；不到一分钟只说秒。三种形态各自一条
     *  字典键 —— 单位不进代码，英文那边是 `5 min` / `30 s`。
     */
    fun durationLabel(seconds: Int): String {
        val minutes = seconds / MINUTE_SECONDS
        val rest = seconds % MINUTE_SECONDS
        return when {
            rest == 0 -> I18n.format(Strings.CATALOG_CLOCK_MIN, minutes)
            minutes == 0 -> I18n.format(Strings.CATALOG_CLOCK_SEC, rest)
            else -> I18n.format(Strings.CATALOG_CLOCK_MIN_SEC, minutes, rest)
        }
    }
}
