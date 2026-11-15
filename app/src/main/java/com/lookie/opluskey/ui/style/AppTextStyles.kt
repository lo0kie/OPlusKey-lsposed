package com.lookie.opluskey.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import io.github.suqi8.coui.kmp.theme.TextStyles
import io.github.suqi8.coui.kmp.theme.defaultTextStyles

/**
 * 全项目那一份**折行规则** —— 一处声明，界面上每一处文字都吃它。
 *
 * ## 为什么不是 `LocalTextStyle`
 *
 *  `CompositionLocalProvider(LocalTextStyle provides …)` 是 Material3 的说法：M3 的 `Text`
 *  默认参数是 `style = LocalTextStyle.current`。本项目一个字都不走 M3 —— `Text` 是
 *  `io.github.suqi8.coui.kmp.basic.Text`，它的默认参数是 **`style = COUITheme.textStyles.main`**
 *  （见库的 `basic/Text.kt`），读的是 COUI 自己那条 `LocalTextStyles`，而那条是 `internal`
 *  —— 外面连 provide 都 provide 不了。所以照 Material 那套写，在这里是**完全无效**的一层。
 *
 * ## 为什么这条路反而更强
 *
 *  M3 那套有一个说不掉的分界：显式传了 `style = …` 的 `Text` **不会**再合并 `LocalTextStyle`，
 *  于是「全局」要靠调用点自觉 `.copy()` 派生。COUI 这边反过来 —— 显式传的那些样式**本身就是**
 *  `COUITheme.textStyles.xxx`，只要把这一份 `TextStyles` 换掉，显式与默认**一起**变：
 *
 *  · 不传 `style` 的 `Text` → 默认 `main`，换掉；
 *  · `style = COUITheme.textStyles.headline1` 这类 → 同一个 `TextStyles` 实例上的另一格，换掉；
 *  · 库里的 `SmallTitle` / `BasicComponent` / `Button` / `Chip` / `TabRow` / `TextField` /
 *    各类弹窗与抽屉（二十多个文件读 `COUITheme.textStyles`）→ 同一个来源，换掉；
 *  · `TextFieldDefaults.textStyle(mode)` 是 `textStyles.body1.copy(...)` → 跟着走。
 *
 *  剩下唯一不吃的，是**从零构造**的 `TextStyle(...)`（本项目一处都没有）与显式改写这一项的
 *  调用点 —— 全项目只有命令全屏编辑那一处，它必须留 `LineBreak.Simple`（见 `CommandFullscreen`：
 *  一条 `shell:settings put system …` 按短语断会在行尾提前折行）。
 *
 * ## 为什么不带 `Hyphens.Auto`
 *
 *  这一页里的拉丁字母基本是**标识符**：包名（`com.tencent.mm`）、命令行（`settings put system …`）。
 *  自动断字会在行尾往这些串里插一个连字符 —— 那不是排版，那是「值被改了」的错觉。
 *  真要用（英文界面那种成句的正文），在这一行加 `hyphens = Hyphens.Auto` 即可，
 *  机制已经在位，不必再改第二处。
 */
internal val APP_LINE_BREAK = LineBreak.Paragraph

/**
 * 全项目那一份**行高分配** —— 与 [APP_LINE_BREAK] 同一个入口、同一批十四格。
 *
 * ## 它解决的是什么
 *
 *  COUI 的默认是 `LineHeightStyle(Alignment.Top, Trim.None)`：多余行距全放在行下方、首末行
 *  的空白一律不裁。那样行盒 = `lineHeight`（1.158em 起），而**汉字墨迹只占行盒上半部**
 *  （descent 那一段是留给拉丁降部 g / p / y 的）。
 *
 *  于是任何「把文字盒竖着居中」的布局（`Alignment.Center`、`CenterVertically`、`Placeholder`
 *  居中）居中的都是**行盒**而不是墨迹 —— 汉字看着一律偏上。项目原先为此养了二十多处
 *  `Modifier.opticalCenter` 一类的 `offset` 补丁，逐处把墨迹再挪回去；那一套已随这一档整体删除，
 *  **不要再往回加**：新界面只要用 `Center` 类布局就是对的。
 *
 *  ## 换成 `Center` + `Both` 之后
 *
 *  · `Trim.Both`：裁掉**首行**上方的空白与**末行**下方的空白 —— 单行文字就是两头都裁，
 *    行盒于是贴住墨迹；多行文字只动首末行，行间行距仍由 `lineHeight` 管（不受影响）。
 *  · `Alignment.Center`：`lineHeight` 与字体自身行盒不等高时，把差值上下均分而不是全塞到下面。
 *
 *  两件事合起来，行盒的中心就近似墨迹的中心 —— 所有 `Center` 类布局直接得到视觉居中，
 *  不必再逐处 `offset`。
 *
 *  ## 代价
 *
 *  库选 `Top` + `None` 是为了让 `lineHeight` 的倍数在**单行**文字上也撑出行高（模仿 Android 的
 *  `lineSpacingMultiplier`）。换成这一档之后，单行文字的盒子会贴着字收紧 —— 这正是本项目要的
 *  （列表行、卡片、胶囊、网格格都是「一行字 + 固定内边距」），但**多行正文的行距一格没变**。
 */
internal val APP_LINE_HEIGHT_STYLE =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    )

/**
 * 交给 [io.github.suqi8.coui.kmp.theme.COUITheme] 的那一份 `TextStyles` —— **全项目唯一的主题入口**
 * （见 `ui/screen/PageContent.kt` 那一次 `COUITheme(...)`）。
 *
 *  以库的默认那一份为底（`defaultTextStyles()`），只改两件事：折行（[APP_LINE_BREAK]）
 *  与行高分配（[APP_LINE_HEIGHT_STYLE]）。字号 / 字重 / `lineHeight` 仍是库的原值。
 *
 *  `remember` 不带 key：这些样式只跟 `sp` 常量走，不随主题档与深浅变 —— 每次重组重造一份，
 *  下面那层 `COUITheme` 会把整棵页面树当成换了主题。
 */
@Composable
internal fun appTextStyles(): TextStyles = remember { defaultTextStyles().withAppTypography() }

/**
 * 十四格逐格换一遍 —— `TextStyles` 没有「对所有格套同一个函数」的入口，这里就是那份显式清单。
 *
 *  **十四格一个都不能漏**：漏掉的那一格就是「这一档文字还按平台默认折行 / 按库的默认分配行高」，
 *  而它多半是 `title` / `subtitle` 里的某一个 —— 那种不一致只有把两种摆在同一页上才看得出来。
 */
private fun TextStyles.withAppTypography(): TextStyles =
    copy(
        main = main.withAppTypography(),
        paragraph = paragraph.withAppTypography(),
        body1 = body1.withAppTypography(),
        body2 = body2.withAppTypography(),
        button = button.withAppTypography(),
        footnote1 = footnote1.withAppTypography(),
        footnote2 = footnote2.withAppTypography(),
        headline1 = headline1.withAppTypography(),
        headline2 = headline2.withAppTypography(),
        subtitle = subtitle.withAppTypography(),
        title1 = title1.withAppTypography(),
        title2 = title2.withAppTypography(),
        title3 = title3.withAppTypography(),
        title4 = title4.withAppTypography(),
    )

/** 单格那一刀：只换折行与行高分配，字号 / 字重 / `lineHeight` 原样带走。 */
private fun TextStyle.withAppTypography(): TextStyle = copy(lineBreak = APP_LINE_BREAK, lineHeightStyle = APP_LINE_HEIGHT_STYLE)
