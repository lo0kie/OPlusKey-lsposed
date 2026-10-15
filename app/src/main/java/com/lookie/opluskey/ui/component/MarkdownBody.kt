package com.lookie.opluskey.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 弹窗正文的 markdown 渲染 —— 全项目**唯一**一处把 markdown 翻成 Compose 的地方。
 *
 * 用它而不是库自带的 `summary` 槽：那个槽只收纯文本，而正文要能带**强调**与**列表**
 * （风险提示那一段就是靠这两样才读得顺）。
 *
 * 主题不走库的 m3 那套（本项目用 COUI，再引一层 Material3 只会多一套色板），所以颜色与字体
 * 在这里一档档映射过去 —— 库的 `Markdown(...)` 把 `colors` / `typography` 定成**必填**，正是留给这种接法。
 *
 * @param textAlign 短正文居中、长正文左对齐，由调用方按正文自己判 —— 与库 `DialogContentLayout`
 *   自己那条口径一致（它按实测行数切，这里按「有没有换行 + 折算宽度」估，见 `isShortDialogBody`）。
 */
@Composable
internal fun MarkdownBody(
    text: String,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val colors = DefaultMarkdownColors(
        text = COUITheme.colorScheme.onBackgroundVariant,
        codeBackground = COUITheme.colorScheme.surfaceContainerHigh,
        inlineCodeBackground = COUITheme.colorScheme.surfaceContainerHigh,
        dividerColor = COUITheme.colorScheme.dividerLine,
        tableBackground = COUITheme.colorScheme.surfaceContainer,
    )
    val base = COUITheme.textStyles.body2.copy(textAlign = textAlign)
    val typography = DefaultMarkdownTypography(
        // 弹窗正文里不该出现真正的标题层级（它只有强调与列表），所以 `#` 也只给一档加粗正文 ——
        //  万一有人写了 `#`，不至于突然放出一个撑破弹窗的大字。
        h1 = base.copy(fontWeight = FontWeight.Bold),
        h2 = base,
        h3 = base,
        h4 = base,
        h5 = base,
        h6 = base,
        text = base,
        code = COUITheme.textStyles.footnote1.copy(textAlign = textAlign),
        inlineCode = base,
        quote = base,
        paragraph = base,
        ordered = base,
        bullet = base,
        list = base,
        textLink = TextLinkStyles(style = SpanStyle(color = COUITheme.colorScheme.primary)),
        table = COUITheme.textStyles.footnote2.copy(textAlign = textAlign),
    )
    Markdown(
        content = text,
        colors = colors,
        typography = typography,
        // 库的默认是 `fillMaxSize()` —— 弹窗里要按内容高，不能占满
        modifier = modifier,
        // 同步解析：默认那条是异步的（走 `rememberMarkdownState`），弹窗会先闪一帧空的
        immediate = true,
    )
}

/**
 * 把**用户写的东西**（命令名、应用名）里的 markdown 记号转义掉。
 *
 * 正文整体走 markdown，所以不转义的话，一个叫 `2*3` 的命令会让后半句变成斜体、
 * 名字里带 `[` 会让后面整段被当成链接。转法就是每个记号前面加一个反斜杠（CommonMark 的写法）。
 */
internal fun escapeMarkdown(text: String): String = buildString(text.length) {
    for (c in text) {
        if (c in MARKDOWN_SPECIALS) append('\\')
        append(c)
    }
}

/** CommonMark 里有含义的那些字符（在这个项目的正文里只会出现这些）。 */
private const val MARKDOWN_SPECIALS = "\\`*_~{}[]()#+-.!|>"
