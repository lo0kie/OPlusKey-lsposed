package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp

// 手绘图标共用的**画布与笔法** —— 建字形用的 builder、描边那一笔，外加「系统配色」那两块色块。
//
//  与 `DrawnIcons` 的分工：那边画图标（一个字形一套坐标），这里只回答「怎么落笔」——
//  视口恒为 24、描边不填充、圆头圆角、宽度对图标库的 2.0 取齐。

/** 这批字形唯一的建造器：24dp 的默认尺寸、24 的视口 —— 与图标库那批同尺。 */
internal fun vectorBuilder(name: String): ImageVector.Builder =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

/**
 * 描边路径（不填充、圆头圆角）—— 手绘字形唯一的落笔方式。
 *
 *  @param width 笔画宽度；缺省取这支笔的标准档（与图标库同宽），侧键那两支各自另有取值
 */
internal fun ImageVector.Builder.addStroked(
    path: List<PathNode>,
    width: Float = ICON_STROKE,
) {
    addPath(
        pathData = path,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** 「系统配色」菜单里那两块色块 —— 形状固定，颜色由调用方逐行给。 */
internal object PaletteIcons {
    /**
     * 「系统配色」菜单里每一行行首那块**色块** —— **四个小方块拼成一大块**（2×2）。
     *
     *  形状各档共用，颜色由 `MenuEntry.Row.iconTint` 逐行给（色块本身就是这一行的身份）。
     *  四格是**同一个色的四档透明度**（100% / 55% / 30% / 12%）：读起来是「这一套色的一个色板」，
     *  而不是四块互不相干的色；`Icon` 上色走 `SrcIn`，透明度原样保留。
     *  用实心方块：描边的图标上色之后只是一圈彩线，看不出是「这一套色」。
     */
    val Swatch: ImageVector by lazy { paletteSwatch() }

    /** 单个整块（20 见方、圆角 [SWATCH_BLOCK_CORNER]）—— 强调色只有一种颜色时用它。 */
    val Block: ImageVector by lazy { paletteBlock() }

    private fun paletteBlock(): ImageVector {
        val builder = vectorBuilder("PaletteBlock")
        builder.addPath(
            //  四格那块整体也占 20 见方（2..22）：整块与它同尺寸，换档时行首不会忽大忽小
            pathData = roundedTile(2f, 2f, side = SWATCH_SPAN, corner = SWATCH_BLOCK_CORNER),
            fill = SolidColor(Color.Black),
        )
        return builder.build()
    }

    /** 色块里的四格（2×2，边长 9、间距 2，整体 20×20 居中）—— 逐格给一档透明度。 */
    private fun paletteSwatch(): ImageVector {
        val builder = vectorBuilder("PaletteSwatch")
        listOf(2f to 2f, 13f to 2f, 2f to 13f, 13f to 13f)
            .forEachIndexed { i, (x, y) ->
                builder.addPath(
                    pathData = roundedTile(x, y),
                    fill = SolidColor(Color.Black.copy(alpha = SWATCH_ALPHAS[i])),
                )
            }
        return builder.build()
    }

    /** 四格的透明度：左上最实、右下最淡（与真机上「一个色块的四档色阶」同一个读法）。 */
    private val SWATCH_ALPHAS = listOf(1f, 0.55f, 0.3f, 0.12f)

    /** 一格圆角方块（边长 [SWATCH_TILE]、圆角 [SWATCH_CORNER]）—— 四角各画一段四分之一圆。 */
    private fun roundedTile(
        x: Float,
        y: Float,
        side: Float = SWATCH_TILE,
        corner: Float = SWATCH_CORNER,
    ): List<PathNode> {
        val r = corner
        val far = side - r
        return PathData {
            moveTo(x + r, y)
            lineTo(x + far, y)
            arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, r, r)
            lineTo(x + side, y + far)
            arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, -r, r)
            lineTo(x + r, y + side)
            arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, -r, -r)
            lineTo(x, y + r)
            arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, r, -r)
            close()
        }
    }

    /** 一格边长（四格 2×2、间距 2，整体 20 见方居中）。 */
    private const val SWATCH_TILE = 9f

    /** 四格整体的边长 —— 整块那一档与它同尺寸，换档时行首不会忽大忽小。 */
    private const val SWATCH_SPAN = 20f

    /** 圆角半径 —— 与页面里卡片 / 色块的圆角同一档观感（24 视口里 2）。 */
    private const val SWATCH_CORNER = 2f

    /** 整块的圆角：色块大了要跟着放大，否则读起来像「四个小格之一」被放大了。 */
    private const val SWATCH_BLOCK_CORNER = 5f
}
