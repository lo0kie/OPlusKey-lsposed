package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.actionTitle

// 命令文本的显示层：把一条命令 / 一个步骤压成界面上要显示的那几行字。
// 与 Compose、与页面状态无关 —— 输入是 `Command` / 字符串，输出是字符串 / 图标。

/**
 * 一条命令在卡片里的摘要行：一步一条，同时带着那一步的原始取值。
 *
 * 带 `token` 是因为行首要按它决定补不补那枚命令引用小图标 —— 光看 `title` 分不出
 * 「引用另一条命令」与「恰好同名的命令文本」。
 */
internal data class StepLine(
    val token: String,
    val title: String,
)

/**
 * 「多条命令」的摘要行：一步一条。只管多条 —— 单条那一路显示原始取值。
 *
 * @param appName 透传给 [stepTitle]：步骤里可能有 `app:` / `window:` / `appshortcut:`，
 *   那一步要显示名字。入参是**目标写法**（`999@包名/类名`），不是包名 —— 分身与具体界面都要认，
 *   名字的优先级收在 `appNameOf` 一处。
 */
internal fun serialSummarySteps(
    steps: List<String>,
    appName: (String) -> String,
): List<StepLine> = steps.map { StepLine(it, stepTitle(it, appName)) }

/**
 * 这一步是不是引用另一条命令（`cmd:<名字>`）。
 * 界面据此在行首补一枚小图标，`stepIcon` 也按它选字形。
 */
internal fun isCommandRef(token: String): Boolean = token.startsWith(Command.PREFIX)

/**
 * 一个步骤在界面上叫什么。
 *
 *  **除「打开应用」外一律拍平**：菜单里的分组标题（`系统操作` / `快捷功能` /
 *  `自定义命令`）与入口格的格子名（`系统导航` / `系统按键` / `媒体控制` …）都不再进标题 ——
 *  步骤行与卡片摘要都窄，多一段前缀只剩啰嗦，「这一步是什么」靠行首图标（[stepIcon]）加名字
 *  已经说得清。`系统导航:主屏幕` → `主屏幕`、`系统按键:音量+` → `音量+`、`快捷功能:手电筒` → `手电筒`。
 *  唯一的例外是「打开应用」那两档（`app:` 与 `window:`），它们显示成「打开应用:应用名」/
 *  「以小窗口打开:应用名」—— 那一段不是分类，是动作本身：少了它就只剩一个应用名，
 *  看不出这一步是「打开某个应用」，也看不出是小窗那一档。口径与执行时提示
 *  （`host/action/ActionNotice`）**同一份实现**（`core/model/actionTitle`），不再各写一遍。
 *  - 内置动作取清单里的名字（[ActionCatalog.specOf]）；
 *  - 入口格取那一档的档位名（[ActionCatalog.modeName]）；
 *  - `cmd:` 只显示命令名；
 *  - 都认不出来就原样显示取值（比如手改过的文件）。
 */
internal fun stepTitle(
    token: String,
    appName: (String) -> String,
): String {
    if (isCommandRef(token)) return token.removePrefix(Command.PREFIX)
    val action = Action.parse(token)
    return actionTitle(
        action = action,
        // 名字从哪儿来是**界面侧**的事（宿主那边现查 PM）：这里先查这次清单、再查配置里记的名字
        appName = { a ->
            when (a) {
                is Action.App -> appName(a.target)
                is Action.AppWindow -> appName(a.target)
                is Action.AppShortcut -> appName(a.appTarget.orEmpty())
                else -> ""
            }
        },
        // 认不出来时用**配置里那串原始写法**（宿主那边用规范化后的 token）
        fallback = token,
    )
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
internal fun stepIcon(
    token: String,
    commands: List<Command>,
): ImageVector {
    // 走 `ActionCatalog` 那份按 token 记忆的规范化结果，不在这里现 `Action.parse` ——
    //  步骤行的每一次重组都会走到这一句（见 `ActionCatalog.canonicalToken`）。
    if (!isCommandRef(token)) return actionIcon(ActionCatalog.canonicalToken(token))
    val name = token.removePrefix(Command.PREFIX)
    val ref = commands.firstOrNull { it.name == name } ?: return PageIcons.CommandSingle
    return commandIcon(ref.kind, ref.steps.size)
}

/**
 * 「条件执行」那种**一行两格**的列表里，命令那一格怎么写 —— **父级 - 子级**（「响铃状态 - 循环」）。
 *
 *  为什么这一处要带父名：那一格紧挨着条件那格（「响铃状态 - 亮屏」），两格同一套写法才读得顺；
 *  而且「循环」「静音」这种词单摆着看不出是谁在循环、谁在静音。**其余地方仍走 [stepTitle]**
 *  （「已保存的命令」那些步骤行窄，只写取值更省地方）。
 *
 *  没有父格的动作（`nav:` 那些入口格以外的一切、`cmd:` 引用、自定义命令）原样退回 [stepTitle]。
 */
internal fun actionCellTitle(
    action: Action,
    appName: (String) -> String,
): String {
    val hit = ActionCatalog.modeHitOf(action)
    return if (hit == null) stepTitle(action.token, appName) else "${hit.tile.label} - ${hit.mode.label}"
}

/**
 * 与 [actionCellTitle] 一一对应的行首图标 —— 有父格时用**那一行自己的**字形。
 *
 *  「循环」写的是 `keyevent:164`，与响铃那一格**同一个 token**：按 token 取图标只能拿到格子那颗
 *  （响铃状态），档位自己的字形（重复箭头）只登记在 `Spec.glyph` 上。所以这里先问
 * [ActionCatalog.modeHitOf]，问到就交给 `specIcon`（它认 `glyph`），问不到再按 token 走老路。
 */
internal fun actionCellIcon(
    action: Action,
    commands: List<Command>,
): ImageVector {
    val hit = ActionCatalog.modeHitOf(action)
    return if (hit == null) stepIcon(action.token, commands) else specIcon(hit.mode)
}

/**
 * 时长（毫秒）在界面上怎么写 —— 全局唯一一处，滑杆行尾与网格小字都走它。
 *
 * 1000ms 及以上写成秒（`1500 → 1.5 秒`）：这两处宽度紧、四位数读不出量级；不足 1000ms 仍写
 * 毫秒 —— 步长是 [STEP_MS]（50ms），一秒内换成秒反而更难读。小数只在需要时留、不四舍五入
 *（`1050 → 1.05 秒`）。
 */
internal fun durationText(ms: Int): String {
    if (ms < 1000) return I18n.format(Strings.COMMON_TIME_MS, ms)
    val seconds = ms / 1000
    val rest = ms % 1000
    val secStr =
        if (rest == 0) {
            seconds.toString()
        } else {
            // 余数按三位补零、再去掉末尾的零：1500 → "1.5"、1050 → "1.05"、1005 → "1.005"
            "$seconds.${rest.toString().padStart(3, '0').trimEnd('0')}"
        }
    return I18n.format(Strings.COMMON_TIME_SEC, secStr)
}
