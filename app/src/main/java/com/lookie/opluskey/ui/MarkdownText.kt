package com.lookie.opluskey.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser

// 更新说明那一段的 Markdown —— 只翻成 `AnnotatedString`，排版仍由调用方那张 `Text` 管。
//
//  为什么只翻成**一段**文字、不拆成多个块组件：
//   1. 弹窗里对这段正文有 `maxLines` 截断（6 行 + 省略号）。拆成多个 `Text` 之后，这个「整段
//      最多几行」就没法表达了 —— 每个块各自截断，摊出来的高度不确定。
//   2. 字号 / 行距 / 颜色得跟弹窗正文同源，而这些都是 `Text` 的样式参数，交给同一次绘制最省事。
//
//  于是这里只做「记号 → `SpanStyle`」：标题加粗放大、列表补圆点、代码换等宽、链接着主题色。
//  解析交给 `org.jetbrains:markdown`（GFM），不自己写扫描 —— release 正文里标题、列表、
//  行内代码、链接都会出现，手写扫描器迟早漏掉一种。

/** 一份 GFM 解析器 —— 无状态，复用同一份（构造一次的代价不值得每次付）。 */
private val MD_PARSER by lazy {
    MarkdownParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken.NonCancellable)
}

/** 无序列表的圆点 —— 与 `EmphasisText` 那一套同一个字形，两处列表看起来才是一回事。 */
private const val MD_BULLET = "\u2022  "

/** 行内代码两侧的薄空格（U+2009）—— 代替 `background` 给不出的那点内边距，见代码里的说明。 */
private const val CODE_PADDING = "\u2009"

/** 各级标题相对正文字号的倍率（`ATX_1..6` / `SETEXT_1..2` 分别对应 `h1..h6`）。 */
private val MD_HEADING_SCALES = listOf(1.35f, 1.25f, 1.15f, 1.08f, 1.02f, 1f)

/**
 * 一份渲染配方 —— 正文样式 + 两种强调色。
 *
 *  打包成一个对象是因为它要在每个 `append*` 之间传：拆成三个参数的话，光是标题那一层就
 *  六个参数（detekt 的上限），而且每加一种颜色就要再改一圈签名。
 */
private class MdStyle(
    /** 正文样式，标题的放大倍率以它的字号为基准。 */
    val text: TextStyle,
    /** 链接色。 */
    val accent: Color,
    /** 行内代码 / 代码块共用的字体与底色。 */
    val code: SpanStyle,
)

/**
 * 把一段 Markdown 翻成 [AnnotatedString]。
 *
 * @param style 正文本身用的样式 —— 标题的放大倍率以它的字号为基准（字号未指定时标题只加粗、
 *   不放大）
 * @param accent 链接色（一般给主题色）
 * @param codeBackground 行内代码 / 代码块的底色
 */
internal fun markdownToAnnotated(
    markdown: String,
    style: TextStyle,
    accent: Color,
    codeBackground: Color,
): AnnotatedString {
    //  **先按长度截断**：这段正文来自网络（更新说明），解析与渲染都在主线程的组合期、且都是
    //  按 AST 深度递归。一条几 MB 的正文足以把主线程占满（ANR），而说明是给人看的 ——
    //  超出的部分截掉就够了，比让弹窗卡死好。
    val body = if (markdown.length <= MAX_MARKDOWN_CHARS) markdown else markdown.take(MAX_MARKDOWN_CHARS)
    val root =
        runCatching { MD_PARSER.buildMarkdownTreeFromString(body as CharSequence) }.getOrNull()
            // 解析器抛异常时**退回原文**：更新说明再花哨，也不该让弹窗画不出来
            ?: return AnnotatedString(body)
    val md =
        MdStyle(
            text = style,
            accent = accent,
            //  底色要**比弹窗底深一档**：原先给的是 `surfaceContainer`，而浅色主题里它就是纯白，
            //  与弹窗底同色 ⇒ 行内代码只剩下一副等宽字形，看起来像「字体坏了」而不是「这是代码」。
            code = SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
        )
    return buildAnnotatedString { appendBlocks(root, body, md) }
}

/** 更新说明的字数上限 —— 正文来自网络，解析与渲染都在主线程。 */
private const val MAX_MARKDOWN_CHARS = 64_000

/** 顶层块：段落、标题、列表、代码块、引用。其余类型保守地按行内处理。 */
private fun AnnotatedString.Builder.appendBlocks(
    node: ASTNode,
    src: String,
    md: MdStyle,
) {
    for (child in node.children) {
        val level = headingLevel(child.type)
        when {
            level != null -> {
                appendHeading(child, src, level, md)
            }

            //  段落**也要在头部补换行** —— 漏了这一句，标题与下一段会粘成一行
            //  （真机：`### 其它` 后面直接接上正文）
            child.type == MarkdownElementTypes.PARAGRAPH -> {
                if (length > 0) append("\n\n")
                appendInline(child, src, md)
            }

            child.type == MarkdownElementTypes.UNORDERED_LIST -> {
                appendList(child, src, false, md)
            }

            child.type == MarkdownElementTypes.ORDERED_LIST -> {
                appendList(child, src, true, md)
            }

            child.type == MarkdownElementTypes.CODE_FENCE ||
                child.type == MarkdownElementTypes.CODE_BLOCK -> {
                appendCode(child, src, md)
            }

            child.type == MarkdownElementTypes.BLOCK_QUOTE -> {
                appendQuote(child, src, md)
            }

            //  空白 / EOL 这类块间记号不画；其余（HTML 块、链接定义）退回行内，至少字还在
            child.type == MarkdownTokenTypes.EOL ||
                child.type == MarkdownTokenTypes.WHITE_SPACE -> {
                //  什么都不画
            }

            else -> {
                appendInline(child, src, md)
            }
        }
    }
}

/** `ATX_1..6` / `SETEXT_1..2` 对应第几级标题；不是标题返回 `null`。 */
private fun headingLevel(type: IElementType): Int? =
    when (type) {
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 -> 1
        MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 -> 2
        MarkdownElementTypes.ATX_3 -> 3
        MarkdownElementTypes.ATX_4 -> 4
        MarkdownElementTypes.ATX_5 -> 5
        MarkdownElementTypes.ATX_6 -> 6
        else -> null
    }

/** 标题：加粗 + 按级别放大；`#` 那一串记号本身不画。 */
private fun AnnotatedString.Builder.appendHeading(
    node: ASTNode,
    src: String,
    level: Int,
    md: MdStyle,
) {
    val base =
        md.text.fontSize
            .takeIf { it.isSp }
            ?.value ?: 0f
    val size: TextUnit = if (base > 0f) (base * MD_HEADING_SCALES[level - 1]).sp else TextUnit.Unspecified
    //  块与块之间补一个换行（补在头部：末尾那个 `AnnotatedString.Builder` 删不掉）
    if (length > 0) append("\n\n")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size)) {
        appendInline(node, src, md, skipMarkers = true)
    }
}

/** 列表：每个 `LIST_ITEM` 前补圆点 / 序号；项内按行内画（嵌套列表摊平，说明文字一般用不到）。 */
private fun AnnotatedString.Builder.appendList(
    node: ASTNode,
    src: String,
    ordered: Boolean,
    md: MdStyle,
) {
    var index = 0
    //  块与块之间补一个换行（补在头部：末尾那个 `AnnotatedString.Builder` 删不掉）
    if (length > 0) append("\n\n")
    for (item in node.children) {
        if (item.type != MarkdownElementTypes.LIST_ITEM) continue
        index += 1
        //  项与项之间单换行（同一个列表是一块，不要每项都隔成段落）
        if (index > 1) append('\n')
        append(if (ordered) "$index.  " else MD_BULLET)
        appendInline(item, src, md, skipMarkers = true)
    }
}

/** 代码块：等宽 + 底色，内容取 `CODE_FENCE_CONTENT`（三引号那一行与语言名不画）。 */
private fun AnnotatedString.Builder.appendCode(
    node: ASTNode,
    src: String,
    md: MdStyle,
) {
    val body =
        node.children
            .filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT || it.type == MarkdownTokenTypes.CODE_LINE }
            .joinToString("\n") { src.substring(it.startOffset, it.endOffset).trimEnd('\n') }
    if (body.isEmpty()) return
    //  块与块之间补一个换行（补在头部：末尾那个 `AnnotatedString.Builder` 删不掉）
    if (length > 0) append("\n\n")
    withStyle(md.code) {
        append(body)
    }
}

/** 引用：整段着链接色，`>` 记号不画。 */
private fun AnnotatedString.Builder.appendQuote(
    node: ASTNode,
    src: String,
    md: MdStyle,
) {
    //  块与块之间补一个换行（补在头部：末尾那个 `AnnotatedString.Builder` 删不掉）
    if (length > 0) append("\n\n")
    withStyle(SpanStyle(color = md.accent)) {
        for (child in node.children) {
            //  `>` 不作为 `MarkdownTokenTypes` 的记号出现在这棵树上（真机实测：它跟着内容一起被
            //  画了出来），所以按**文本**认：只由 `>` 组成的叶子（后面可能跟着空白）直接跳过。
            val mark = src.substring(child.startOffset, child.endOffset).trim()
            if (child.children.isEmpty() && mark.isNotEmpty() && mark.all { it == '>' }) {
                continue
            }
            appendInline(child, src, md, skipMarkers = true)
        }
    }
}

/**
 * 一条行内节点流里「这一格画不画」的判定 —— 跨格记一个状态，所以摊不成纯函数。
 *
 *  两种情况跳过：
 *   1. [skipMarkers] 为真时的记号本身（`**`、`` ` ``、`#`、`-` …）；
 *   2. 记号**紧邻**的那一个空白 —— `### 标题` 里 `#` 与文字之间那个空格属于记号。
 *
 *  第 2 条一定要限定「紧邻」：词与词之间的空白是同一类 token（`MarkdownTokenTypes.WHITE_SPACE`），
 *  跟着记号一起无差别跳过的话，`hello world` 会被拼成 `helloworld`。
 */
private class MarkerSkipper(
    private val skipMarkers: Boolean,
) {
    /** 上一格是不是被跳过的记号 —— 只有它为真时才允许再吃一个空白。 */
    private var afterMarker = false

    fun skip(child: ASTNode): Boolean {
        if (skipMarkers && isMarker(child.type)) {
            afterMarker = true
            return true
        }
        if (afterMarker && child.type == MarkdownTokenTypes.WHITE_SPACE) {
            afterMarker = false
            return true
        }
        afterMarker = false
        return false
    }
}

/**
 * 行内：递归把记号翻成样式。
 *
 *  [skipMarkers] 为真时**跳过记号本身**（`**`、`` ` ``、`-` 这些）—— 只在「已经知道这一层
 *  的记号是什么」的场合传（标题 / 列表项 / 加粗体内部），否则会把星号原样画出来。
 */
private fun AnnotatedString.Builder.appendInline(
    node: ASTNode,
    src: String,
    md: MdStyle,
    skipMarkers: Boolean = false,
) {
    if (node.children.isEmpty()) {
        append(src.substring(node.startOffset, node.endOffset))
        return
    }
    //  记号**后面紧跟的那一个空白**属于记号本身（`### 标题`、`- 项`、`> 引用` 都带着它），
    //  要一起吃掉；但正文里词与词之间的空白是同一类 token —— 一律跳过的话
    //  `hello world` 会被拼成 `helloworld`（标题与加粗里最容易撞上）。所以只吃紧邻的那一个。
    //  这两条判断要跨格记状态，放在循环外那个小对象里（见 [MarkerSkipper]）。
    val skipper = MarkerSkipper(skipMarkers)
    for (child in node.children) {
        if (skipper.skip(child)) continue
        when (child.type) {
            MarkdownElementTypes.STRONG -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInline(child, src, md, skipMarkers = true)
                }
            }

            MarkdownElementTypes.EMPH -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInline(child, src, md, skipMarkers = true)
                }
            }

            MarkdownElementTypes.CODE_SPAN -> {
                withStyle(md.code) {
                    //  `SpanStyle.background` 只铺**字面**，没有内边距（这是 Compose 文本模型
                    //  的限制，不像 CSS 能写 padding）—— 底色块会紧紧咬住字形，看起来像被
                    //  荧光笔刷了一道。两侧各垫一个薄空格，当作最小可用的内边距。
                    append(CODE_PADDING)
                    appendInline(child, src, md, skipMarkers = true)
                    append(CODE_PADDING)
                }
            }

            GFMElementTypes.STRIKETHROUGH -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInline(child, src, md, skipMarkers = true)
                }
            }

            MarkdownElementTypes.INLINE_LINK -> {
                appendLink(child, src, md)
            }

            MarkdownTokenTypes.HARD_LINE_BREAK -> {
                append('\n')
            }

            //  段内的软换行：CommonMark 视作空格，这里也照这个语义（否则长行会被拆成一堆短行）
            MarkdownTokenTypes.EOL -> {
                append(' ')
            }

            else -> {
                appendInline(child, src, md, skipMarkers)
            }
        }
    }
}

/**
 * `[文字](地址)` —— 只画文字、着链接色加下划线，**不接点击**。
 *
 *  弹窗里的链接极少（更新说明里的仓库地址），为它挂一条手势链不值；真要打开，正文下面
 *  那颗「去下载」已经指向发布页了。
 */
private fun AnnotatedString.Builder.appendLink(
    node: ASTNode,
    src: String,
    md: MdStyle,
) {
    //  直接按**原文**取 `[` 与 `]` 之间那一段，不跟 `LINK_TEXT` 的子节点走：真机上那个节点
    //  带着方括号（`[项目仓库]`），按它画出来就是括号连同地址一起露在正文里。
    val raw = src.substring(node.startOffset, node.endOffset)
    val start = raw.indexOf('[')
    val end = raw.indexOf(']')
    val label = if (start >= 0 && end > start) raw.substring(start + 1, end) else raw
    withStyle(SpanStyle(color = md.accent, textDecoration = TextDecoration.Underline)) {
        append(label)
    }
}

/**
 * 记号本身（`**`、`*`、`` ` ``、`~~`、`#`、`>`、列表符号）—— 这些只表示「怎么排」，不显示。
 *
 *  **空白与行尾不在此列**：它们不是记号，是正文的一部分（词间距靠它们）。跟着一块跳掉的话
 *  `hello world` 会变 `helloworld`；块首那个空白由 [appendInline] 里「紧跟记号的那一个」单独处理。
 */
private fun isMarker(type: IElementType): Boolean =
    type == MarkdownTokenTypes.EMPH ||
        type == MarkdownTokenTypes.BACKTICK ||
        type == MarkdownTokenTypes.ATX_HEADER ||
        type == MarkdownTokenTypes.SETEXT_1 ||
        type == MarkdownTokenTypes.SETEXT_2 ||
        type == MarkdownTokenTypes.BLOCK_QUOTE ||
        type == MarkdownTokenTypes.LIST_BULLET ||
        type == MarkdownTokenTypes.LIST_NUMBER
