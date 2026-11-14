package com.lookie.opluskey.ui.model.text

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.present.ActionCatalog
import com.lookie.opluskey.core.present.ClockCatalog
import com.lookie.opluskey.core.present.CommonCommands
import com.lookie.opluskey.core.present.appTargetLabel
import com.lookie.opluskey.core.present.commandTitleOf
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.icon.actionIcon
import com.lookie.opluskey.ui.model.icon.commandIcon
import com.lookie.opluskey.ui.model.icon.specIcon

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
 * `cmd:` 后面那一段在界面上叫什么 —— 被引用那条命令的**名字**。
 *
 *  用户命令库里的（含手改出来的）原样显示；模块内置的那几条（`CommonCommands`：
 *  `cmd:common.wechat_scan`）存的是稳定 id，要按当前语言过一遍字典，否则界面上会冒出
 *  `common.wechat_scan` 这种没人读得懂的东西。
 *
 *  与 `core/present/CommandTitle` 的 `Action.Custom` 那一支同一个口径：那一边管提示 / 摘要句，
 *  这一边管步骤行与格子标题，两处都得认。
 */
private fun commandRefName(token: String): String = token.removePrefix(Command.PREFIX).let { raw -> CommonCommands.labelOf(raw) ?: raw }

/**
 * 一个步骤在界面上叫什么。
 *
 *  **除「打开应用」外一律拍平**：菜单里的分组标题（`系统操作` / `快捷功能` /
 *  `自定义命令`）与入口格的格子名（`系统导航` / `系统按键` / `媒体控制` …）都不再进标题 ——
 *  步骤行与卡片摘要都窄，多一段前缀只剩啰嗦，「这一步是什么」靠行首图标（[stepIcon]）加名字
 *  已经说得清。`系统导航:主屏幕` → `主屏幕`、`系统按键:音量加` → `音量加`、`快捷功能:手电筒` → `手电筒`。
 *  唯一的例外是「打开应用」那两档（`app:` 与 `window:`），它们显示成「打开应用:应用名」/
 *  「以小窗口打开:应用名」—— 那一段不是分类，是动作本身：少了它就只剩一个应用名，
 *  看不出这一步是「打开某个应用」，也看不出是小窗那一档。口径与执行时提示
 *  （`host/action/ActionNotice`）**同一份实现**（`core/model/CommandTitle`），不再各写一遍。
 *  - 内置动作取清单里的名字（[ActionCatalog.specOf]）；
 *  - 入口格取那一档的档位名（[ActionCatalog.modeName]）；
 *  - `cmd:` 只显示命令名；
 *  - 都认不出来就原样显示取值（比如手改过的文件）。
 */
internal fun stepTitle(
    token: String,
    appName: (String) -> String,
): String {
    if (isCommandRef(token)) return commandRefName(token)
    //  走 `ActionCatalog` 那份按 token 记忆的解析结果，不在这里现 `Action.parse` ——
    //  步骤行的每一次重组、每一条已保存命令卡都会走到这一句（见 `ActionCatalog.actionOf`）。
    val action = ActionCatalog.actionOf(token)
    return commandTitleOf(action).forSentence(
        action = action,
        appName = appTargetName(appName),
        // 认不出来时用**配置里那串原始写法**（宿主那边用规范化后的 token）
        fallback = token,
    )
}

/**
 * 同 [stepTitle]，但**目标那一段由调用方整段给**（`完整目标`，例如已经拼好括号的
 * `微信(小窗 扫一扫)`）。
 *
 *  为什么要这一支：[stepTitle] 的 `appName` 回调收的是「目标 → 名字」，它自己还会按动作类型
 *  补「(小窗)」注记。而系统场景那一格用 [appTargetLabelOf][com.lookie.opluskey.ui.appTargetLabelOf]
 *  已经把括号拼全了 —— 再从 [appTargetName] 走一遍就会套两层
 *  （真机口径「应用控制: 打开应用 - 应用(小窗 组件)(小窗)，多了一个」）。
 *  所以「谁拼括号」只能有一处：要么这里，要么 `appName` 回调。
 */
internal fun stepTitleWithTarget(
    token: String,
    target: (Action) -> String,
): String {
    if (isCommandRef(token)) return commandRefName(token)
    val action = ActionCatalog.actionOf(token)
    return commandTitleOf(action).forSentence(
        action = action,
        appName = target,
        fallback = token,
    )
}

/**
 * 界面侧那份「目标 → 名字」的回调，包成 [commandTitleOf] 要的形状。
 *
 *  名字从哪儿来是**界面侧**的事（宿主那边现查 PM）：这里先查这次清单、再查配置里记的名字。
 *  只有这一份 —— 各写一遍时，「哪几种动作要名字」这种判据就会各长一套（漏一档的表现是
 *  提示里多出一个空括号）。
 *
 *  **「小窗」在这一层标**（`微信(小窗)`）：动词那一侧只有「打开应用」一个词，小窗是目标的注记。
 *  这条回调只给「名字」—— 已经有完整目标的调用点走 [stepTitleWithTarget]。
 */
private fun appTargetName(appName: (String) -> String): (Action) -> String =
    { a ->
        when (a) {
            is Action.App -> appTargetLabel(appName(a.target), window = false, detail = null, stockAnim = a.stockAnim)
            is Action.AppWindow -> appTargetLabel(appName(a.target), window = true, detail = null)
            is Action.AppShortcut -> appTargetLabel(appName(a.appTarget.orEmpty()), window = a.window, detail = null)
            is Action.Process -> appName(a.pkg)
            else -> ""
        }
    }

/**
 * 「额外动作」那一行的值怎么写 —— **完整文案**，与步骤行、执行提示逐字相同：
 *
 *  · 有父动作：`父动作: 子动作 - 状态`（`系统开关: WIFI - 打开`、`快捷功能: 手电筒 - 关闭`）；
 *  · 没有父动作：`动作 - 状态`（`录屏 - 开始`）；
 *  · 顶层格只有名字：`截屏`；
 *  · 没配动作：`无操作` —— 这一行要有值，不然看不出「确实没配」。
 *
 *  这一行**不省父动作**（用户口径「额外动作的子标题要取完整文案」）：上面那行标题是
 *  「额外动作」四个字，不是某个格子的名字 —— 省掉父名之后 `WIFI - 打开` 读不出是哪一格的东西。
 *  与网格小字（`forTile`，那里格子名就在格子上、省得掉）正好相反。
 *
 *  这里**不再特判「无操作」**：`forSentence` 现在对任何动作都给完整一句（它不判「该不该显示」），
 *  所以这一行直接拿结果就行。以前那两行特判 + `ActionCatalog.label` 是工厂里那个空串逼出来的。
 */
internal fun releaseActionTitle(
    token: String,
    appName: (String) -> String,
): String {
    val action = ActionCatalog.actionOf(token)
    return commandTitleOf(action).forSentence(action, appTargetName(appName), token)
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
    //  **内置那几条常用命令不在用户命令库里**（`cmd:common.wechat_scan` 这类）：按库里查必然落空，
    //  落到通用的「命令」字形上就等于把它们的信息抹掉了。它们自带 root 位与那一步正文 ——
    //  按与库里那条**同一条判据**（`commandIcon`）画，root 角标才不会丢。
    CommonCommands.entryOf(token)?.let { return commandIcon(CommandKind.SINGLE, 1, it.root, it.step) }
    val ref = commands.firstOrNull { it.name == name } ?: return PageIcons.CommandSingle
    //  单条命令按它**自己那种写法**取底图、root 那档加井号角标，与命令列表里那张卡同一条判据
    return commandIcon(ref.kind, ref.steps.size, ref.root, ref.single)
}

/**
 * 与 [stepIcon] 一一对应的行首图标 —— 有父格时用**那一行自己的**字形。
 *
 *  「循环」写的是 `keyevent:164`，与响铃那一格**同一个 token**：按 token 取图标只能拿到格子那颗
 *  （响铃状态），档位自己的字形（重复箭头）只登记在 `Spec.glyph` 上。所以这里先问
 * [ActionCatalog.modeHitOf]，问到就交给 `specIcon`（它认 `glyph`），问不到再按 token 走老路。
 */
internal fun actionCellIcon(
    action: Action,
    commands: List<Command>,
): ImageVector {
    //  计时器**没有入口格**（那一格就是它自己，不进 `MODE_MENUS`），`modeHitOf` 查不到它；
    //  而 token 是 `timer:300`，按 token 走兜底只会拿到通用那颗 —— 它的字形登记在格子那份 [Spec] 上。
    //  这条也决定「执行时提示走流体云」时胶囊左边那颗图（见 `ActionCardArt`）。
    if (action is Action.Timer) return specIcon(ClockCatalog.TILE_SPEC)
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
