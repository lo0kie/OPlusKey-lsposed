package com.lookie.opluskey.ui.model

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
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
    val measurer = rememberTextMeasurer()
    val dy =
        remember(style, density) {
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
    return offset { IntOffset(x = 0, y = dy) }
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
