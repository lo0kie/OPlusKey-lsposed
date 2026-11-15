package com.lookie.opluskey.ui.style

import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.DIALOG_LINE_HEIGHT_STYLE
import com.lookie.opluskey.ui.dialog.DIALOG_PLATFORM_STYLE
import com.lookie.opluskey.ui.model.page.FROSTED_BLUR_RADIUS
import com.lookie.opluskey.ui.model.theme.AppTheme
import com.lookie.opluskey.ui.model.theme.DARK_SCHEME
import com.lookie.opluskey.ui.model.theme.LIGHT_SCHEME
import com.lookie.opluskey.ui.model.theme.NIGHT_MODE_KEY
import com.lookie.opluskey.ui.model.theme.NIGHT_POLL_MS
import com.lookie.opluskey.ui.model.theme.NIGHT_RETRY_MS
import com.lookie.opluskey.ui.model.theme.VISIBILITY_ENTER
import com.lookie.opluskey.ui.model.theme.VISIBILITY_EXIT
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.CardColors
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.TextStyles
import io.github.suqi8.coui.kmp.theme.defaultTextStyles
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
        //  **分段与对齐方式无关**：只按居中档分段的话，同一段文案在左对齐时会被压成一整块
        //  （免责声明那种长正文正是左对齐的）—— 同一句话两处排版，读起来不像一份文件。
        //  空行不进排版（画出来也只是一行空白），段间那道 [PARAGRAPH_GAP] 照旧。
        //  分段只在**文案变了**才重算：这一层每次重组（弹窗按钮态、焦点、进出场动画）都会跑，
        //  不记的话每次都要把整段正文再切一遍、再逐行 trim。对齐方式不参与分段（见下），
        //  所以 `remember` 的键只给 `text`。
        val paragraphs =
            remember(text) {
                text.split("\n\n").map { para -> para.lines().map { it.trim() }.filter { it.isNotEmpty() } }
            }
        val total = remember(paragraphs) { paragraphs.sumOf { it.size } }
        var drawn = 0
        paragraphs.forEachIndexed { index, lines ->
            if (index > 0) Spacer(Modifier.height(PARAGRAPH_GAP))
            lines.forEach { line ->
                drawn++
                EmphasisLine(line, style, lineHeightStyle(drawn, total))
            }
        }
    }
}

/**
 * 正文第 [index] 行（共 [total] 行）的行盒裁剪。
 *
 *  行盒比字形高，多出来的白会算进「区与区之间」：整段正文只有首行上沿、末行下沿贴着弹窗的
 *  间距（见 `DIALOG_LINE_HEIGHT_STYLE`），中间那些行的行距由行高自己撑，一点都不能裁。
 */
private fun lineHeightStyle(
    index: Int,
    total: Int,
): LineHeightStyle =
    when {
        total <= 1 -> DIALOG_LINE_HEIGHT_STYLE
        index == 1 -> LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.FirstLineTop)
        index == total -> LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.LastLineBottom)
        else -> LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
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
    lineHeightStyle: LineHeightStyle,
) {
    if (text.isEmpty()) return
    val lineStyle = style.copy(lineHeightStyle = lineHeightStyle, platformStyle = DIALOG_PLATFORM_STYLE)
    //  加粗解析同理：只有这一行的文字变了才需要重扫
    val rendered = remember(text) { emphasis(text) }
    if (text.startsWith(BULLET_MARK)) {
        Row(Modifier.fillMaxWidth()) {
            Text(text = BULLET_GLYPH, style = lineStyle)
            Text(
                text = remember(text) { emphasis(text.removePrefix(BULLET_MARK)) },
                style = lineStyle,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Text(
            text = rendered,
            style = lineStyle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 把一段正文里的 `**加粗**` 翻成 [AnnotatedString]。
 *
 *  逐字符扫一遍：遇到**成对**的 `**` 就在这儿切一段 —— `**` 本身不显示，只翻转
 *  「后面这段要不要加粗」；遇到 `\` 就把它后头那一个字符照字面摆（转义）。其余字符原样。
 *
 *  **落单的 `**` 按字面显示**（注释里那句约定原来只有文字、没有实现）：改成「成对」判之后，
 *  先扫一遍取出所有**未被转义**的 `**` 起始下标，两两配对（1↔2、3↔4…），最后落单的那一个
 *  **不入表** ⇒ 它就按字面跟在正文里。原先的实现是无条件翻转，一个落单的 `**` 会让**后半句
 *  整段变粗**、星号还消失 —— 用户写的命令名里出现两个星号就会撞上。
 *
 *  为什么**不**用正则：正文里可能出现真的以 `-` / `*` 开头的名字，正则替换容易把
 *  「转义过的记号」和「真记号」一起改掉；一次线性扫描里判这两件事，它们不会互相打架。
 */
private fun emphasis(text: String): AnnotatedString {
    //  成对判据先算一遍（O(n)）：正文都是弹窗里那几句，长度有界
    val marks = pairedMarks(text)
    return buildAnnotatedString {
        var bold = false
        var i = 0
        var next = 0
        while (i < text.length) {
            val c = text[i]
            if (c == BACKSLASH && i + 1 < text.length) {
                append(text[i + 1])
                i += 2
            } else if (next < marks.size && i == marks[next]) {
                if (bold) pop() else pushStyle(BOLD)
                bold = !bold
                next++
                i += 2
            } else {
                append(c)
                i += 1
            }
        }
    }
}

/**
 * 一段正文里**成对**的 `**` 起始下标（升序）；落单的那一个不在表里。
 *
 *  转义跳过：`\*` 后面那个字符照字面摆，所以它不可能是记号的一半（与 [emphasis] 同一口径）。
 */
private fun pairedMarks(text: String): IntArray {
    val found = ArrayList<Int>()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == BACKSLASH && i + 1 < text.length -> {
                i += 2
            }

            c == ASTERISK && i + 1 < text.length && text[i + 1] == ASTERISK -> {
                found.add(i)
                i += 2
            }

            else -> {
                i += 1
            }
        }
    }
    //  两两成对：`1↔2`、`3↔4`…，末尾落单的那个丢掉（它按字面显示）
    val paired = found.size - found.size % 2
    return IntArray(paired) { found[it] }
}

/**
 * 「这一颗图标该染成什么色」—— 由页面在根上提供；没提供时返回 `null`（= 用原来的中性色）。
 *
 *  只有「外观 → 随机图标配色」开着时才有值（见 `GlobalConfig.iconTintSeed`）：
 *  关着时页面 provide 的是恒 `null` 的那一支，控件照旧跟着主题走。
 *
 *  按**图标身份**取色（[ImageVector.name]），不按调用点位置：同一颗图标在菜单里、在卡片里
 *  颜色一致，挪个位置也不会换色。
 *
 *  动态档（不是 `staticCompositionLocalOf`）：这一位**会变** —— 拨「随机图标配色」那一下换种子，
 *  而静态 local 一变会把 provide 处**整棵内容**重组合一遍（那一页每个控件都跟着重跑）。
 *  动态 local 只把订阅过它的图标标脏，页面其余部分不动 —— 换一批图标颜色不该拖着整页重画。
 *  代价是每个读者各登记一条，几十颗图标值这个开销。
 */
internal val LocalIconTint = compositionLocalOf<(ImageVector) -> Color?> { { null } }

/**
 * 一颗图标的**随机配色**（见 [LocalIconTint]）—— 关着时返回 `null`。
 *
 *  抽成一个扩展只为让调用点写成一行：`tint = row.iconTint ?: icon.tinted() ?: fallback`。
 */
@androidx.compose.runtime.Composable
internal fun ImageVector.tinted(): Color? = LocalIconTint.current(this)

/**
 * 卡片的三档底色 —— 全项目一处。
 *
 *  这三档（常规 / 按下 / 内容色）原先在命令卡、绑定格与 `CardList` 默认值里各写一遍，值一样：
 *  同一页里两张卡按下去的深浅不同、或者一张的内容色漏了跟上主题，都是这么来的。
 *
 *  @param contentColor 卡片内容色。默认是 `onSurfaceContainer`；绑定格那一档会传一个**动画值**
 *  进来（整格的内容色随启用 / 选中变，见 `BindingTile` 的 `contentColor`），其余调用点不用传。
 */
@Composable
internal fun appCardColors(contentColor: Color = COUITheme.colorScheme.onSurfaceContainer): CardColors =
    CardDefaults.defaultColors(
        color = COUITheme.colorScheme.surfaceContainer,
        contentColor = contentColor,
        pressedColor = COUITheme.colorScheme.surfaceContainerHigh,
    )

/**
 * 磨砂。
 *
 * @param performanceMode 模糊的采样档位（`null` = 库默认的 `Balanced`）。糊是**每帧重算**的：
 *   按屏幕坐标取样，节点每移一点就要重新捕获一遍背面、重新糊一遍。`Performance` 把采样降到
 *   半分辨率 —— Haze 会按缩放同比放大半径，所以**视觉半径不变**，变的只是糊上那点细节，
 *   24dp 这种半径下基本看不出。只有「一直在动」的糊才值得降档；静止的糊只算一次，用默认档。
 */
internal fun Modifier.frosted(
    state: HazeState,
    shape: Shape,
    performanceMode: HazePerformanceMode? = null,
): Modifier =
    this
        .clip(shape)
        .hazeBlur(
            input = HazeInput.Sources(state),
            style = HAZE_FROSTED_STYLE,
            performanceMode = performanceMode,
        )

/**
 * 磨砂的模糊规格 —— 顶栏 / 浮层菜单 / 底部工具栏 / 确认弹窗共用这一份。
 *
 * [FROSTED_BLUR_RADIUS] 直接就是 dp：Haze 的 `blurRadius` 收 dp，不必再按 density 换算成像素。
 * 噪点显式置 0 —— 库默认 0.15，会在糊上叠一层细颗粒，与这套观感对不上。
 */
private val HAZE_FROSTED_STYLE: HazeBlurStyle =
    HazeBlurStyle {
        blurRadius(FROSTED_BLUR_RADIUS)
        noiseFactor(0f)
    }

/**
 * 直接画 [Drawable] 的 [Painter] —— **不建中间位图**。
 *
 * ## 为什么不用 `Bitmap` 中转
 *
 *  早先在 `AppTile` 里是「`createBitmap` → `Canvas` → `drawable.draw` → `asImageBitmap`」：
 *  每滑过一个应用就在 Java 堆与 Native 堆各分配一张 48dp 见方的位图（还有一次整块拷贝），
 *  列表几百项时内存曲线跟着滚动来回抖，GC 也被喂饱。
 *
 *  这里在 `onDraw` 里直接拿到 `nativeCanvas` 把 Drawable 画上去 —— Compose 本来就是
 *  「一层层往 canvas 上画」，中间那张位图纯属多余的搬运。矢量图 / 自适应图标也因此按当前
 *  画布尺寸实时光栅化，不会因为取样尺寸与渲染尺寸不等而发虚。
 *
 * ## 为什么只实现「画」，不实现 alpha / 色彩滤镜
 *
 *  `applyAlpha` / `applyColorFilter` 的语义是改**这个 painter 的**绘制参数，而这里的
 *  Drawable 是从缓存里取出来的、**可能被多行共用**（`AppIconCache.put`）。改它的
 *  `alpha` 会把状态留在那个对象上，别的行下次画出来就跟着变。返回 `false` 表示「本 painter
 *  不支持这一项」，Compose 会在有 `alpha` modifier 时退回分层绘制。
 *
 * ## 边界（bounds）会被改
 *
 *  `onDraw` 里改的是 Drawable 自己的 bounds —— 这是 `Drawable` 唯一的绘制接口，绕不开。
 *  绘制都在主线程上串行发生，**每次画之前重设一遍**，所以共用同一个 Drawable 也不会串。
 */
internal class DrawablePainter(
    private val drawable: Drawable,
) : Painter() {
    /** 内容固有尺寸；`-1` 表示这一类 Drawable 没有固有尺寸（Compose 侧按「未知」处理）。 */
    override val intrinsicSize: Size
        get() {
            val w = drawable.intrinsicWidth
            val h = drawable.intrinsicHeight
            return if (w < 0 || h < 0) Size.Unspecified else Size(w.toFloat(), h.toFloat())
        }

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }

    // `true` = 由本 painter 自己处理并**不改变绘制结果**。这两项都返回 false：
    //  真去改 Drawable 会把状态留在共用对象上（见类注释）。
    override fun applyAlpha(alpha: Float): Boolean = false

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean = false

    /**
     * RTL：`Drawable` 自己认 `setLayoutDirection`，但同一个缓存对象同样可能被两种方向共用，
     * 所以这里只画它的**当前**方向，不改它 —— 应用图标本身也不随方向翻转。
     */
    override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean = false
}

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

/**
 * 空态 / 加载态在**可见区**里居中那一块的账 —— 全项目一处。
 *
 *  三处在用：已保存的命令（`CommandListPage`）、变量列表、场景块。三处原先各写一遍
 *  「视口 − 顶栏 − 页底留白」，算式一样、注释各说一遍 —— 而漏减一项的后果是**中线偏上或偏下**
 *  （真机口径「没有垂直居中」），那种偏差对着一屏空白根本看不出来，只能照着账核。
 *
 *  **为什么不能在这些位置直接用 `fillMaxHeight`**：它们都在滚动容器（或 `LazyColumn` 的项）里，
 *  子项拿到的是无限高度约束，`fillMaxHeight()` 在里面没有意义 —— 只能由外层量一次视口再传进来。
 *
 *  @param maxHeight 视口高度（外层 `BoxWithConstraints` 的 `maxHeight`）
 *  @param topInset 压在内容上的顶部浮层（顶栏、分页带、搜索带）
 *  @param bottomInset 页底那档留白：不减的话空态比可见区高出一截，居中位置会偏下、还多出一段能拖的空白
 */
internal fun visibleAreaHeight(
    maxHeight: Dp,
    topInset: Dp,
    bottomInset: Dp = 0.dp,
): Dp = (maxHeight - topInset - bottomInset).coerceAtLeast(0.dp)

/**
 * 「让出顶部浮层、在剩下的那块里居中」—— 空态与加载态**唯一的一层摆位**。
 *
 *  @param modifier 高度从哪来由调用点定：`fillMaxSize`（浮层）、`fillParentMaxHeight`（列表项）、
 *    `height(...)`（滚动内容里量好的那一块）
 */
@Composable
internal fun CenteredInInsets(
    modifier: Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.padding(top = topInset, bottom = bottomInset),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 滚动内容里的空态那一块 —— 撑满「可见区那一块」并居中。
 *
 *  与 [Feedback] 分工：那个管「空态里面长什么样」（字形 + 一句话），这个管「摆在哪」。
 *  三处滚动页原先各写一个 `Box(fillMaxWidth().height(...), Center)`，同一个意思三个写法。
 */
@Composable
internal fun VisibleAreaCentered(
    height: Dp,
    content: @Composable () -> Unit,
) {
    CenteredInInsets(Modifier.fillMaxWidth().height(height)) { content() }
}

// 文本按**格**算长度、按**码点**截断的几件小事 —— 弹窗正文与菜单标题共用同一套口径，不各写一份。
//
//  「格」是等宽视角下的半角单位：一个汉字（或全角标点）两格，一个拉丁字母 / 数字一格。
//  界面上的两处判据（正文算不算「短到能居中」、菜单标题要不要截断）都按格数说规格，
//  而不是按 `String.length` —— 后者把「中文」算两个字符宽得多、"English" 反而算七个。

/**
 * 这个**码点**占两格（全角）。
 *
 *  只取 Unicode 里中日韩与全角标点那几段，外加**补充平面**（emoji 等）：「汉字 / 全角标点
 *  vs 其余」这个粗判不追求覆盖所有宽字符（真漏了，结果也只是把那一格算窄，不影响可用性）。
 *
 *  **按码点判、不按 `Char`**：emoji 是代理对（两个 `Char`），按 `Char` 数会把一个 emoji
 *  算成一格 —— 与「全角占两格」的直觉相反，截断时也会算出比实际更宽的结果。
 */
internal fun isFullWidth(codePoint: Int): Boolean =
    when (codePoint) {
        // 谚文字母
        in 0x1100..0x115F -> true

        // 中日韩部首、假名、汉字、注音
        in 0x2E80..0xA4CF -> true

        // 谚文音节
        in 0xAC00..0xD7A3 -> true

        // 中日韩兼容汉字
        in 0xF900..0xFAFF -> true

        // 中日韩兼容形式
        in 0xFE30..0xFE6F -> true

        // 全角形式
        in 0xFF00..0xFF60 -> true

        // 全角符号
        in 0xFFE0..0xFFE6 -> true

        // 补充平面（emoji 与各类符号）—— 表宽两格
        else -> codePoint > 0xFFFF
    }

/** 把一串文本折成**格数**（全角两格、其余一格）。 */
internal fun displayWidth(text: String): Int {
    var width = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        width += if (isFullWidth(cp)) 2 else 1
        i += Character.charCount(cp)
    }
    return width
}

/**
 * 把文本砍到 [maxColumns] 格以内，末尾补一个省略号；没超就原样返回。
 *
 *  只改**画出来的那一份**：日志与配置里仍是全文（否则截断后的名字在日志里认不出是哪一条）。
 *  按**码点**前进、只在码点边界上记可截位置 —— 按 `Char` 数再 `take(chars)` 会把代理对
 *  （emoji）从中间切开，留下半个代理字符（画出来是乱码）。
 */
internal fun ellipsizeToColumns(
    text: String,
    maxColumns: Int,
): String {
    if (displayWidth(text) <= maxColumns) return text
    var width = 0
    var cut = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val step = Character.charCount(cp)
        val next = width + if (isFullWidth(cp)) 2 else 1
        if (next > maxColumns) break
        width = next
        i += step
        cut = i
    }
    return text.take(cut) + "…"
}

/**
 * 按**码点**截断到 [max] 个码点以内 —— `take()` 数的是 UTF-16 单元，会切开代理对、留下半个字符。
 *
 *  切点落在码点边界上：解析器与渲染拿到的都是完整字符。
 */
internal fun String.truncateToCodePoints(max: Int): String =
    if (max <= 0) {
        ""
    } else {
        substring(0, offsetByCodePoints(0, minOf(max, codePointCount(0, length))))
    }

// 显隐动画包一层：进场 / 退场各自给一套动画，调用点不重复写 `AnimatedVisibility`。

/**
 * **动态显隐的一段控件** —— 统一动画的唯一入口（规格见 [VISIBILITY_ENTER]）。
 *
 * 为什么要有这个包装：`AnimatedVisibility` 的 `enter` / `exit` 每个调用点各写一遍
 * 容易写岔（时长、方向、少写一半），而「所有动态显隐的控件动画一致」正是这件事的目的。
 * 用法：把 `if (条件) { 控件… }` 直接换成 `AnimatedColumn(visible = 条件) { 控件… }`
 * —— 条件里那个 `if` 没了，**里面的缩进不用动**（多出来的那层 `Column` 在本函数里，
 * 见下）。
 *
 *  注意：里面那个 `Column` **不能省**：`AnimatedVisibility` 的内容槽位不是 `Column`，
 * 多个子项（本项目里常见「一档 `Spacer` + 小标题 + 一张卡」这种组合）会**叠在同一处**。
 * 所以这里固定套一层 `Column` —— 这也正是调用点不必再动缩进的原因。
 *
 * @param visible 显不显示（也就是那个 `if` 的条件）
 */
@Composable
internal fun AnimatedColumn(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = VISIBILITY_ENTER,
        exit = VISIBILITY_EXIT,
    ) {
        Column(content = content)
    }
}

/**
 * 同 [AnimatedColumn]，但显隐状态由调用方**持有**（`MutableTransitionState`）。
 *
 *  给「除了播动画，还要读 `currentState` 判『这一行此刻还在不在场上』」的容器用 ——
 *  `CardList` 靠它决定行序（首 / 中 / 末行）什么时候换档：换早了，末行一藏上面那行就
 *  多出一档内缩，把还在收缩的行顶一下。
 */
@Composable
internal fun AnimatedColumn(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = VISIBILITY_ENTER,
        exit = VISIBILITY_EXIT,
    ) {
        Column(content = content)
    }
}

/**
 * 同 [AnimatedColumn]，但**只淡入淡出、不动高度** —— 给「位置本来就在文档流里、
 * 高度由别处定死」的东西用（格子里的那行小字、居中那圈加载提示）：
 * 给它们套展开 / 收起会把所在的行 / 卡片撑一下，反而是抖。
 */
@Composable
internal fun AnimatedFade(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        //  缓动与其余显隐同一条（COUI 的减速 / 加速两档，见 PageMotion 顶部那段）
        enter = fadeIn(tween(durationMillis = 220, easing = DecelerateEasing())),
        exit = fadeOut(tween(durationMillis = 160, easing = AccelerateEasing())),
    ) { content() }
}

// 主题：深浅色判定/取色与系统主题同步。
//
// 从 `SettingsPage.kt` 按职责拆出：成员改为 [SettingsPage]
// 的扩展/同包顶层声明，调用点零改动。

/** 页面底色 —— COUI 色板的 `surface`（深色 `#000000`、浅色 `#F0F1F2`，与系统设置页一致）。 */
internal val SettingsPage.pageBackgroundColor: Int
    get() = colorsFor(chrome.dark).surface.toArgb()

/**
 * 这一页现在用的是哪一档界面颜色（[AppTheme]）。
 *
 *  只有**自己定底色的浮层**要它：菜单 / 确认弹窗的面板不走 `COUITheme` 的 `surface`（那一档
 *  要比页面亮一档，见 `PageColors`），而 Monet 那一档又得跟着系统取色。
 *  由 `PageContent` 在页面级提供；取不到时是默认档（按传统那套中性面板画）。
 */
internal val LocalAppTheme = staticCompositionLocalOf { AppTheme.AUTO }

internal fun SettingsPage.colorsFor(night: Boolean) = if (night) DARK_SCHEME else LIGHT_SCHEME

/**
 * 系统当前是不是深色 —— 读**系统全局配置**；不能用 Activity / Application 的 `configuration`
 * （宿主声明了 `configChanges=uiMode`）。
 *
 *  **判据只有这一处**：页面（[systemNight]）与 hook 侧（`hook/HostWindow.isNight`）都调它，
 *  只是读不出来时兜底不同 —— 页面退回自己记的 `chrome.dark`，hook 侧退回 `Context` 的
 *  configuration。那两份兜底本来就不该一样，但「系统到底深不深」只能有一个答案。
 *
 *  @return 读不出来（极少数 ROM 上 `Resources.getSystem()` 会抛）给 `null`，由调用方兜底
 */
internal fun systemNightOrNull(): Boolean? =
    try {
        (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    } catch (e: Throwable) {
        XLog.d("读系统深浅失败（由调用方兜底）：${e.message}")
        null
    }

internal fun SettingsPage.systemNight(): Boolean = systemNightOrNull() ?: chrome.dark

/** 系统切深浅时同步给 hook 侧（顶栏与状态栏配色）。 */
@Composable
internal fun SettingsPage.SyncToHost() {
    LaunchedEffect(chrome.dark) { hooks.onSystemThemeChanged?.invoke() }

    // 颜色模式改成「固定浅 / 固定深」时，**系统那边什么都没变** —— 上面那两条路（设置项监听 /
    // 轮询）都不会醒。得由配置这一侧推一次，否则 `chrome.dark` 还停在旧值上，整页要等到下一次
    // 系统切换深浅才会跟着变。
    val theme = configState.config.value.global.theme
    LaunchedEffect(theme) { syncNight("颜色模式变更") }

    // 真机量下来两边轮询延迟随机落在 0.14s~0.56s（相位运气），补这条推送把最常见的「手动切」
    // 压成即时。回调里不读设置项的值、只当「有事发生」的信号，权威仍是 [systemNight]（读系统
    // 全局配置）；也不能只查一次 —— 写设置项与全局配置真的翻过去不是同一时刻。
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        var retry: kotlinx.coroutines.Job? = null
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    XLog.d("推送：$NIGHT_MODE_KEY 变了（连查 ${NIGHT_RETRY_MS.size} 次）")
                    retry?.cancel()
                    retry =
                        scope.launch {
                            for (wait in NIGHT_RETRY_MS) {
                                if (wait > 0) kotlinx.coroutines.delay(wait)
                                if (syncNight("$NIGHT_MODE_KEY 变了")) break
                            }
                        }
                }
            }
        val registered =
            try {
                context.contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(NIGHT_MODE_KEY),
                    false,
                    observer,
                )
                true
            } catch (e: Throwable) {
                XLog.w("监听系统深浅设置失败（只剩轮询）：${e.message}")
                false
            }
        onDispose {
            retry?.cancel()
            if (registered) {
                runCatching { context.contentResolver.unregisterContentObserver(observer) }
            }
        }
    }

    // 「定时 / 日落到日出」那种设置项不变的切换不会触发上面的监听，所以留着这条轮询
    // （间隔 [NIGHT_POLL_MS]，读的是系统配置上的一个字段，代价可忽略）。
    // 注意：它会常驻：露出宿主原版页面只是把 pageView 置 GONE，组合并没有被销毁，而自建的
    // owner 又停在 RESUMED（规则禁推 DESTROYED）—— 别拿「不可见就停」当前提去改它。
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(NIGHT_POLL_MS)
            syncNight("轮询")
        }
    }
}

/**
 * 对一次「系统深浅可能变了」的账 —— 推送那一路与轮询那一路共用这一个入口。
 *
 * @param from 触发来源（日志用；排查「到底哪条路先醒」时全靠它）
 * @return 这次真的翻过去了（推送那一路据此决定还要不要再查）
 */
internal fun SettingsPage.syncNight(from: String): Boolean {
    val now = effectiveNight()
    if (now == chrome.dark) return false
    XLog.w("深浅变化（$from）：night=$now")
    chrome.dark = now
    return true
}

/**
 * 当前**实际**该用哪一档深浅 —— 配置里固定浅 / 固定深时就不再看系统。
 *
 *  用户选「固定深色」就是为了「不管系统怎么切」：此时系统那两条路（设置项监听 / 轮询）算出来的
 *  结论都不该生效。跟随系统那一档才落到 [systemNight]。
 *
 *  它同时决定**推给宿主**的那一份（[SyncToHost] 看到 `chrome.dark` 变了就推）：所以固定深色时，
 *  宿主自己的顶栏与状态栏也跟着深 —— 否则页面深、周围一圈浅，比不提供这一档更难看。
 */
internal fun SettingsPage.effectiveNight(): Boolean =
    when (AppTheme.ofCode(configState.config.value.global.theme)) {
        AppTheme.AUTO -> systemNight()

        // Monet 也是「跟随系统」：深浅同样跟着系统走，只是配色换成系统那套（见 `colorsOf`）
        AppTheme.MONET -> systemNight()

        AppTheme.LIGHT -> false

        AppTheme.DARK -> true
    }
