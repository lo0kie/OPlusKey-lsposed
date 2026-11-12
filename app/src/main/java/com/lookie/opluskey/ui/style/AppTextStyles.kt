package com.lookie.opluskey.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
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
 * 交给 [io.github.suqi8.coui.kmp.theme.COUITheme] 的那一份 `TextStyles` —— **全项目唯一的主题入口**
 * （见 `ui/screen/PageContent.kt` 那一次 `COUITheme(...)`）。
 *
 *  以库的默认那一份为底（`defaultTextStyles()`），只改折行这一项：字号 / 字重 / 行高 /
 *  `LineHeightStyle` 仍是库的原值，不与这一条混在一起。
 *
 *  `remember` 不带 key：这些样式只跟 `sp` 常量走，不随主题档与深浅变 —— 每次重组重造一份，
 *  下面那层 `COUITheme` 会把整棵页面树当成换了主题。
 */
@Composable
internal fun appTextStyles(): TextStyles = remember { defaultTextStyles().withLineBreak() }

/**
 * 十四格逐格换一遍 —— `TextStyles` 没有「对所有格套同一个函数」的入口，这里就是那份显式清单。
 *
 *  **十四格一个都不能漏**：漏掉的那一格就是「这一档文字还按平台默认折行」，而它多半是
 *  `title` / `subtitle` 里的某一个 —— 那种不一致只有把两种折行摆在同一页上才看得出来。
 */
private fun TextStyles.withLineBreak(): TextStyles =
    copy(
        main = main.withLineBreak(),
        paragraph = paragraph.withLineBreak(),
        body1 = body1.withLineBreak(),
        body2 = body2.withLineBreak(),
        button = button.withLineBreak(),
        footnote1 = footnote1.withLineBreak(),
        footnote2 = footnote2.withLineBreak(),
        headline1 = headline1.withLineBreak(),
        headline2 = headline2.withLineBreak(),
        subtitle = subtitle.withLineBreak(),
        title1 = title1.withLineBreak(),
        title2 = title2.withLineBreak(),
        title3 = title3.withLineBreak(),
        title4 = title4.withLineBreak(),
    )

/** 单格那一刀：只换折行，字号 / 字重 / 行高 / `LineHeightStyle` 原样带走。 */
private fun TextStyle.withLineBreak(): TextStyle = copy(lineBreak = APP_LINE_BREAK)
