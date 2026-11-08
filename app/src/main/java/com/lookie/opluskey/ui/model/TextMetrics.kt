package com.lookie.opluskey.ui.model

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import com.lookie.opluskey.core.log.XLog
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * 竖居中文字的**光学中心**修正 —— 偏移量按**这个字体的实际字形边界**算，不写任何比例。
 *
 * ## 为什么需要它
 *
 *  `Alignment.Center` 居中的是**行盒**（`ascent + descent`），而人眼判的是**字形墨迹**：
 *  descent 那一段是留给拉丁降部（g / p / y）的，汉字基本不占 ⇒ 墨迹落在行盒上半，看着偏上。
 *
 * ## 走过的那两条弯路（都别再回去）
 *
 *  · 按字重写死比例（Regular 0.035em / Medium 0.1em）：那两个数量的是**特定字体文件**的 `hhea`
 *    度量，换个字体就不成立 —— 中文字体刚好、英文字体偏下（真机反馈）。
 *  · 只关 `includeFontPadding`：它去的是行盒**外层**那点留白，不动 `ascent : descent` 的比例，
 *    实测肉眼零变化。
 *
 * ## 现在的算法
 *
 *  两样东西都是**量出来的**，没有常数：
 *   · 行盒高与基线位置 —— `TextMeasurer` 按当前 `TextStyle` 量一个汉字；
 *   · 墨迹上下边界 —— `Paint.fontMetrics` 的 `top` / `bottom`（**字形极值**，比 `ascent` /
 *     `descent` 贴近真实墨迹；中文字体这两组数差得尤其多）。
 *
 *  于是「行盒中心 − 墨迹中心」就是该下沉的量。换字体、换字号、换字重都自动跟着变。
 *
 * ## 只用在「竖居中」的单行文字上
 *
 *  多行文字眼睛看的是基线，挪了反而让行距看着不匀；固定高度容器里它补的正是那一档偏差
 *  （网格格、胶囊、表格单元格）。
 *
 * 实现用 `offset {}`：只改绘制位置、不改测量（容器高度、行距、换行都不受影响）。
 */
@Composable
internal fun Modifier.opticalCenter(style: TextStyle): Modifier {
    val density = LocalDensity.current
    //  每个调用点仍各有一份 `TextMeasurer`（它是 @Composable，没法在缓存命中时跳过创建），
    //  但**真正的排版**只在 [opticalDyCache] 缺失时才发生 —— 它自带的那层缓存是按实例的，
    //  列表里每滚出一行都是新实例、缓存全空。
    //  `cacheSize = 1`：这个实例只会用来量那一个「国」字。
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val dy = remember(style, density) { opticalDy(style, density, measurer) }
    return offset { IntOffset(x = 0, y = dy) }
}

/**
 * 单行文字**上提、贴齐行盒顶**的修正 —— 上移「行盒顶 → 墨迹顶」那一档。
 *
 * 与 [opticalCenter] 是同一把尺子的两个方向：那一支把墨迹挪到行盒正中（竖居中容器用），
 * 这一支把墨迹顶对齐行盒顶（**上面紧挨着另一行字**时用）。
 *
 * ## 用在哪儿
 *
 *  顶栏副标题：它排在主标题下面一行，行盒之间只有 3.5dp（库的 `SubtitleMarginTop`），
 *  可两个行盒各有一截汉字不占的留白 —— 上面那行的 descent、这一行的 ascent —— 于是
 *  3.5dp 的间隙看着像 11.7dp（真机像素量的：41px @560dpi），副标题像是被推远了一截。
 *  上提这一侧，让墨迹从设计位置（行盒顶 + 3.5dp）开始，两行读起来才是一个整体。
 *
 * ## 这一档就这么多，别再补
 *
 *  量出来只有 1.4dp（14sp 的汉字，行盒留白本就不多）—— **就是要这么多**：真机上再补一档
 *  3dp 就贴得太紧了（试过，被否掉）。真正把两行拉开的是**主标题**行盒底下那截降部留白
 *  （32sp 的字，约 6.9dp），那一侧不归副标题管 —— 动它等于把整块标题往上推，还会连带顶栏高度。
 *
 * 同样用 `offset {}`：只改绘制、不改测量 —— 顶栏高度与折叠几何都不受影响。
 */
@Composable
internal fun Modifier.opticalTightTop(style: TextStyle): Modifier {
    val density = LocalDensity.current
    // 理由同 [opticalCenter]：实例每个调用点一份，真正的排版只在缓存缺失时发生。
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val dy = remember(style, density) { inkTop(style, density, measurer) }
    return offset { IntOffset(x = 0, y = -dy) }
}

/** 上提量缓存 —— 与 [opticalDyCache] 同一套理由；分开一张表，两个方向的量互不覆盖。 */
private val inkTopCache = ConcurrentHashMap<Pair<TextStyle, Density>, Int>()

private fun inkTop(
    style: TextStyle,
    density: Density,
    measurer: TextMeasurer,
): Int =
    inkTopCache.getOrPut(style to density) {
        val probe = measurer.measure("国", style)
        //  这里**不能用** `fontMetrics`（见 [inkRect]）：它的 `top` 是全字体的最大极值
        //  （含拉丁字母的重音空间，约 -1.15em），比汉字自己的墨迹顶（约 -0.88em）还高；
        //  拿它减基线，得到的常是个零或负数 —— 上提量变 0，甚至反过来把字推下去。
        val ink = inkRect(style, with(density) { style.fontSize.toPx() })
        val px = if (ink == null) 0 else (probe.firstBaseline + ink.top).roundToInt()
        XLog.w(
            "副标题光学修正：行盒高=${probe.size.height} 基线=${probe.firstBaseline} " +
                "墨迹=${ink?.top}..${ink?.bottom} ⇒ 上提 ${px}px",
        )
        px
    }

/**
 * 这个汉字**自己的墨迹框**（`getTextBounds`，相对基线，top 为负）。
 *
 *  与 [glyphBounds] 的区别就是整件事的关键：`Paint.fontMetrics` 给的是**字体**的极值
 *  （`top` / `bottom` 涵盖所有字形，包括拉丁字母的重音与降部），而 `getTextBounds("国")`
 *  给的是**这一颗汉字**实际画出来的范围。两者在 `top` 上能差 0.27em —— 14sp 就是 3.8dp，
 *  正好是「上提多少」这一档的全部量。
 *
 * @return `null` = 取不到（字体服务异常）；调用方退回不修正
 */
private fun inkRect(
    style: TextStyle,
    em: Float,
): Rect? =
    runCatching {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, style.fontWeight?.weight ?: 400, style.fontStyle == FontStyle.Italic)
                textSize = em
            }
        Rect().also { paint.getTextBounds("国", 0, 1, it) }
    }.getOrNull()

/**
 * 下沉量缓存 —— **进程级**，键是（样式, 密度）。
 *
 *  为什么要有它：`TextMeasurer.measure` 每量一次就是一次段落排版（建一个 `MultiParagraph`），
 *  加上为取字形边界临时建的 `Paint` 与 `Typeface`。而这些量与**调用点无关**，只跟字号 / 字重 /
 *  字体 / 密度有关 —— 「按键绑定」页那几格、应用清单那些行、命令卡的每一步，量出来的都是同一个数。
 *
 *  改前：列表里每滚出一行 = 一次排版 + 一次 `Typeface.create`；改后：同一种字号在整个进程里
 *  只量一次，之后是一行 `HashMap` 查找。
 *
 *  键的取值空间是全项目的字号档位（个位数）× 密度（通常 1 档），不必考虑淘汰。
 *  用 `ConcurrentHashMap`：组合在主线程跑，但这张表是进程级共享的，别赌只有一个写者。
 *
 *  **换字体族不换键**：键里只有样式与密度，字体族取的是系统默认族（见 [glyphBounds]）。
 *  哪天正文改用自定义字体，得把族名一起拼进键，否则会一直用着旧字体量出来的那个数。
 */
private val opticalDyCache = ConcurrentHashMap<Pair<TextStyle, Density>, Int>()

private fun opticalDy(
    style: TextStyle,
    density: Density,
    measurer: TextMeasurer,
): Int =
    opticalDyCache.getOrPut(style to density) {
        // 一个汉字足够：它同时给出这个字体的行盒高与基线位置
        val probe = measurer.measure("国", style)
        val bounds = glyphBounds(style, with(density) { style.fontSize.toPx() })
        if (bounds == null) {
            // 取不到字形边界就按行盒居中（不猜比例 —— 猜错只会比现在更偏）
            0
        } else {
            // top / bottom 是**相对基线**的字形极值（top 为负，bottom 为正）
            val glyphCenter = probe.firstBaseline + (bounds.top + bounds.bottom) / 2f
            (probe.size.height / 2f - glyphCenter).roundToInt()
        }
    }

/**
 * 当前样式在 `em` 字号下的**字形边界**（`Paint.FontMetrics`）。
 *
 *  字体族走系统默认族 + 同字重：COUI 的正文用的就是系统字体，两者对得上。
 *  哪天正文换成自定义字体，这里要改成从 `LocalFontFamilyResolver` 解析出真正的 `Typeface`
 *  —— 解析是 suspend，得另起一层，别在这里顺手 `runBlocking`。
 *
 * @return `null` = 取不到（例如字体服务异常）；调用方退回不修正
 */
private fun glyphBounds(
    style: TextStyle,
    em: Float,
): Paint.FontMetrics? =
    runCatching {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, style.fontWeight?.weight ?: 400, style.fontStyle == FontStyle.Italic)
                textSize = em
            }
        paint.fontMetrics
    }.getOrNull()
