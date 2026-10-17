package com.lookie.opluskey.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 弹窗正文：只认 `**加粗**` 与行首 `- ` 两种记号。

/** 加粗那一段用的样式 —— 只改字体粗细，颜色与字号跟着正文。 */
private val BOLD = SpanStyle(fontWeight = FontWeight.Bold)

/** 行首的项目符号记号。 */
private const val BULLET_MARK = "- "

/** 转义符：自己不显示，后面那一个字符照字面摆。 */
private const val BACKSLASH = '\\'

/** 加粗记号由它两两成对构成。 */
private const val ASTERISK = '*'

/** 项目符号那个字形，后跟两个空格 —— 与文字之间留出间隔。 */
private const val BULLET_GLYPH = "\u2022  "

/** 段与段之间留的间隔。 */
private val PARAGRAPH_GAP = 6.dp

/**
 * 把**用户写的东西**（命令名、应用名）里会被当成记号的字符转义掉。
 *
 *  正文整体按上面那套记号解析，所以不转义的话，一个叫 `2*3` 的命令会让后半句变成加粗，
 *  名字里带行首 `- ` 会被当成项目符号。转法就是记号前面加一个反斜杠（CommonMark 的写法），
 *  由 [emphasis] 反向吃掉 —— 两边是同一份字符集 [EMPHASIS_SPECIALS]，不会走散。
 */
internal fun escapeEmphasis(text: String): String =
    buildString(text.length) {
        for (c in text) {
            if (c in EMPHASIS_SPECIALS) append('\\')
            append(c)
        }
    }

/** 会被本渲染器当记号处理的字符 —— 要转义的就是这些。 */
private const val EMPHASIS_SPECIALS = "\\*-"

/**
 * 弹窗正文 —— 只认两种记号：`**加粗**`，以及行首的 `- `（项目符号）。
 *
 * ## 为什么不再挂 markdown 渲染器
 *
 *  这一处原来用的是 `com.mikepenz:multiplatform-markdown-renderer-android`，它只为
 *  「一句话里加粗两个词」这件事，拖进来一整套 CommonMark 解析器
 *  （`org.jetbrains:markdown`：词法 / AST / 内联语义那一套）。
 *  而**实际用到的语法就两样** —— 逐份字典核过：
 *
 *   - `**…**` 加粗：九处（免责声明、命令风险提示、免费提醒、清空配置、放弃编辑、
 *     填充前缀、删除命令 ×2、取消小窗口）；
 *   - 行首 `- `：仅「免责声明」一处，两行。
 *
 *  链接（`[文字](地址)`）、标题、表格、代码块 —— **一处都没有**。
 *  于是整套换成 [AnnotatedString]：解析是下面那份手写扫描（二十来行），
 *  渲染直接走 COUI 的 `Text`，色板与字体与弹窗其余部分同源。
 *
 * ## 记号的边界
 *
 *  只认**成对**的 `**`：落单的按字面留在文字里 —— 宁可多显示两个星号，
 *  也不让半句被误判成加粗（正文里会塞命令名、应用名，星号不稀奇）。
 *  `\` 是转义符：自己不显示，后面那一个字符照字面摆，与 [escapeEmphasis] 互为逆操作。
 *
 * @param textAlign 短正文居中、长正文左对齐，由调用方按正文自己判（见 `isShortDialogBody`）。
 */
@Composable
internal fun EmphasisText(
    text: String,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val centered = textAlign == TextAlign.Center
    // 正文比标题**降一档**：弹窗标题走 `onBackground`，这里走 `onBackgroundVariant`。
    //  同色的话正文读起来像标题的续行，层级分不出来；压到更次级那一档又会读成「可以不看」。
    //  这一档是照原来那套 markdown 映射搬过来的（旧 `DefaultMarkdownColors.text` 就是它），
    //  换手写渲染时**别顺手把 color 丢了** —— 丢了正文就会比标题还亮。
    val style =
        COUITheme.textStyles.body2.copy(
            color = COUITheme.colorScheme.onBackgroundVariant,
            textAlign = textAlign,
        )
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        // 段 = 两条（及以上）换行切开。段内一行一行画：「- xxx」那一行的圆点由 [EmphasisLine] 补。
        val paragraphs = if (centered) text.split("\n\n") else listOf(text)
        paragraphs.forEachIndexed { index, para ->
            if (index > 0) Spacer(Modifier.height(PARAGRAPH_GAP))
            para.lines().forEach { raw -> EmphasisLine(raw.trim(), style) }
        }
    }
}

/**
 * 正文里的一行 —— [text] 已去过首尾空白，空行不画。
 *
 *  行首 `- ` 走 [Bullet] 那条路：圆点占自己的宽、文字顶剩下的宽，于是**折行对齐的是文字左缘**
 *  而不是缩到圆点底下（直接把「• 」拼进同一个 `Text` 里就没这个效果）。
 */
@Composable
private fun EmphasisLine(
    text: String,
    style: TextStyle,
) {
    if (text.isEmpty()) return
    if (text.startsWith(BULLET_MARK)) {
        Row(Modifier.fillMaxWidth()) {
            Text(text = BULLET_GLYPH, style = style)
            Text(
                text = emphasis(text.removePrefix(BULLET_MARK)),
                style = style,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Text(
            text = emphasis(text),
            style = style,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 把一段正文里的 `**加粗**` 翻成 [AnnotatedString]。
 *
 *  逐字符扫一遍：遇到成对的 `**` 就在这儿切一段 —— `**` 本身不显示，只翻转
 *  「后面这段要不要加粗」；遇到 `\` 就把它后头那一个字符照字面摆（转义）。其余字符原样。
 *
 *  为什么**不**用正则：正文里可能出现真的以 `-` / `*` 开头的名字，正则替换容易把
 *  「转义过的记号」和「真记号」一起改掉；一次线性扫描里判这两件事，它们不会互相打架。
 */
private fun emphasis(text: String): AnnotatedString =
    buildAnnotatedString {
        var bold = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == BACKSLASH && i + 1 < text.length) {
                append(text[i + 1])
                i += 2
            } else if (c == ASTERISK && i + 1 < text.length && text[i + 1] == ASTERISK) {
                if (bold) pop() else pushStyle(BOLD)
                bold = !bold
                i += 2
            } else {
                append(c)
                i += 1
            }
        }
    }
