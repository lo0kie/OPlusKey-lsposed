package com.lookie.opluskey.page

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.config.Action
import com.lookie.opluskey.config.Command

/**
 * 命令文本的显示层：把一条命令 / 一个步骤压成界面上要显示的那几行字。
 * 与 Compose、与页面状态无关 —— 输入是 `Command` / 字符串，输出是字符串 / 图标。
 */
/**
 * 一条命令在卡片里的摘要行：一步一条，同时带着那一步的原始取值。
 *
 * 带 `token` 是因为行首要按它决定补不补那枚命令引用小图标 —— 光看 `title` 分不出
 * 「引用另一条命令」与「恰好同名的命令文本」。
 */
internal data class StepLine(val token: String, val title: String)

/**
 * 「多条命令」的摘要行：一步一条。只管多条 —— 单条那一路显示原始取值。
 *
 * @param appLabel 透传给 [stepTitle]：步骤里可能有 `app:<包名>`，那一步要显示应用名。
 */
internal fun serialSummarySteps(
    steps: List<String>,
    appLabel: (String) -> String,
): List<StepLine> = steps.map { StepLine(it, stepTitle(it, appLabel)) }

/**
 * 这一步是不是引用另一条命令（`cmd:<名字>`）。
 * 界面据此在行首补一枚小图标，`stepIcon` 也按它选字形。
 */
internal fun isCommandRef(token: String): Boolean = token.startsWith(Command.PREFIX)

/**
 * 一个步骤在界面上叫什么：
 *  - 内置动作取清单里的名字（[ActionCatalog.specOf]）；
 *  - `cmd:` 取命令名；
 *  - `app:` 显示成「打开应用:应用名」—— 这里不碰 `Context`，包名换名字由调用方经 [appLabel] 传进来；
 *  - 入口格那一组（响铃状态 / 媒体控制）显示成「组名:档位名」——
 *    光看 [ActionCatalog.specOf] 只能拿到组名（那一格代表整组，见 [ActionCatalog.isSelected]）；
 *  - 都认不出来就原样显示取值（比如手改过的文件）。
 */
internal fun stepTitle(token: String, appLabel: (String) -> String): String {
    if (isCommandRef(token)) return token.removePrefix(Command.PREFIX)
    val action = Action.parse(token)
    if (action is Action.App) {
        return "打开应用:${appLabel(action.target.substringBefore('/'))}"
    }
    ActionCatalog.modeGroup(action)?.let { (group, mode) -> return "$group:$mode" }
    return ActionCatalog.specOf(action)?.title ?: token
}

/**
 * 一个步骤在界面上的行首图标 —— 与 [stepTitle] 一一对应。
 *
 * `cmd:` 取**被引用那条命令**自己的类型图标（单条 → 终端，多条 → 按步数换的方框）——
 * 光给一个通用「命令」字形的话，步骤行里看不出它引用的是哪一种。
 * 命令库里查不到（那条被删了 / 改了名）时退回通用字形：配置里留着一条指向空气的 `cmd:` 是可能的。
 * 其余内置动作取动作图标，认不出来时 [actionIcon] 会落到兜底字形上。
 *
 * @param commands 当前命令库 —— 与 [stepTitle] 的 `appLabel` 同一条规矩：查名字要用谁由调用方传进来。
 */
internal fun stepIcon(token: String, commands: List<Command>): ImageVector {
    if (!isCommandRef(token)) return actionIcon(Action.parse(token).token)
    val name = token.removePrefix(Command.PREFIX)
    val ref = commands.firstOrNull { it.name == name } ?: return PageIcons.CommandSingle
    return commandIcon(ref.kind, ref.steps.size)
}

/**
 * 时长（毫秒）在界面上怎么写 —— 全局唯一一处，滑杆行尾与网格小字都走它。
 *
 * 1000ms 及以上写成秒（`1500 → 1.5 秒`）：这两处宽度紧、四位数读不出量级；不足 1000ms 仍写
 * 毫秒 —— 步长是 [STEP_MS]（100ms），一秒内换成秒反而更难读。小数只在需要时留、不四舍五入
 *（`1050 → 1.05 秒`）。
 */
internal fun durationText(ms: Int): String {
    if (ms < 1000) return "$ms 毫秒"
    val seconds = ms / 1000
    val rest = ms % 1000
    if (rest == 0) return "$seconds 秒"
    // 余数按三位补零、再去掉末尾的零：1500 → "1.5"、1050 → "1.05"、1005 → "1.005"
    return "$seconds.${rest.toString().padStart(3, '0').trimEnd('0')} 秒"
}
