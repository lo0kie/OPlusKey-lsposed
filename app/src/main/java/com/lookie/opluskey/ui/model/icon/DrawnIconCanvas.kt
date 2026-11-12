package com.lookie.opluskey.ui.model.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
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

/**
 * 一格铺满的字形（不带角标）—— 各族的底图与格子都用它，笔画同一档。
 *
 *  放在这一处而不是 `DrawnIcons` 里：它是「怎么落笔」那一半，与具体画哪颗图标无关；
 *  而 `DrawnIcons` 每加一族就多一个 `xxxBase`，把这两支通用的搬出来，才留得下新族的画法。
 */
internal fun flatIcon(
    name: String,
    vararg paths: List<PathNode>,
): ImageVector {
    val builder = vectorBuilder(name)
    paths.forEach { builder.addStroked(it) }
    return builder.build()
}

/**
 * 底图 + 右下角角标。
 *
 *  **底图不缩、角标不叠**：底图仍是满格那一颗（笔画同宽、坐标不动），只是右下角那几笔在画的时候
 *  就**让开**了（见 `volumeWaves` / `sunRays` / `DrawnIcons.bellBase`）—— 角标正好落在让出来的
 *  那块空里，与图标库里 `bell-plus` / `bell-cog` 的画法一致：一个字形、两笔，不是两块拼起来。
 *
 *  @param base 底图的画法，按这一档角标占的缺口截（缺口见 [notch]）
 */
internal fun badgedIcon(
    name: String,
    badge: Badge,
    base: (Notch?) -> List<PathNode>,
): ImageVector {
    val builder = vectorBuilder(name)
    builder.addStroked(base(notch(badge)))
    builder.addStroked(badgePath(badge), width = BADGE_STROKE)
    return builder.build()
}

/**
 * 同一套拼接，但底图是**图标库现成的那颗字形**。
 *
 *  与上面那一支只差「底图怎么让位」：手绘那几族是**画的时候收笔**（笔画在缺口圆上停住），
 *  而图标库的字形改不了 path，只能**裁** —— 把底图整组套进一条「右下角切掉一块」的裁剪路径里
 *  （缺角是**方形**，理由见 [cornerClip]）。角标、笔画宽度仍走 [badgePath] / [BADGE_STROKE]，
 *  缺角大小按 [cornerNotchStart] 跟角标墨迹走 —— 与手绘那几族同一份账。
 *
 *  @param base 底图字形（图标库那一颗，24 视口）
 */
internal fun badgedIcon(
    name: String,
    badge: Badge,
    base: ImageVector,
): ImageVector {
    val builder = vectorBuilder(name)
    builder.addGroup(clipPathData = cornerClip(cornerNotchStart(badge)))
    builder.copyPaths(base.root)
    builder.clearGroup()
    builder.addStroked(badgePath(badge), width = BADGE_STROKE)
    return builder.build()
}

/**
 * 「右下角切掉一块」的裁剪路径 —— 缺角是**方形**，不是圆。
 *
 *  圆形缺口只适合手绘那几族：它们的底图是**细笔画**（声波弧、光线、铃舌），圆只削到笔画末端，
 *  看着就是「这一笔在缺口边上收笔」。而这里裁的是图标库的**整颗闭合字形**（终端框、气泡轮廓）：
 *  拿圆去咬会在轮廓上留一段月牙形的弧，读起来像图标破了。方形缺角读作「右下角那一块被切掉」，
 *  与角标那块方方正正的地也正好对上。
 *
 *  @param cut 缺角方块的左上角（x / y 各取这个值）—— 由 [cornerNotchStart] 按角标墨迹算
 */
private fun cornerClip(cut: Float): List<PathNode> =
    PathData {
        moveTo(0f, 0f)
        lineTo(24f, 0f)
        lineTo(24f, cut)
        lineTo(cut, cut)
        lineTo(cut, 24f)
        lineTo(0f, 24f)
        close()
    }

/** 把一个 [VectorGroup]（含子组）的路径原样搬进当前 builder —— 裁剪 / 拼接现成字形时用。 */
private fun ImageVector.Builder.copyPaths(group: VectorGroup) {
    group.forEach { node ->
        when (node) {
            is VectorPath -> {
                addPath(
                    pathData = node.pathData,
                    pathFillType = node.pathFillType,
                    name = node.name,
                    fill = node.fill,
                    fillAlpha = node.fillAlpha,
                    stroke = node.stroke,
                    strokeAlpha = node.strokeAlpha,
                    strokeLineWidth = node.strokeLineWidth,
                    strokeLineCap = node.strokeLineCap,
                    strokeLineJoin = node.strokeLineJoin,
                    strokeLineMiter = node.strokeLineMiter,
                    trimPathStart = node.trimPathStart,
                    trimPathEnd = node.trimPathEnd,
                    trimPathOffset = node.trimPathOffset,
                )
            }

            is VectorGroup -> {
                //  `addGroup` 没有块形式：它返回 builder，组由 `clearGroup()` 收尾。
                addGroup(
                    name = node.name,
                    rotate = node.rotation,
                    pivotX = node.pivotX,
                    pivotY = node.pivotY,
                    translationX = node.translationX,
                    translationY = node.translationY,
                    scaleX = node.scaleX,
                    scaleY = node.scaleY,
                    clipPathData = node.clipPathData,
                )
                copyPaths(node)
                clearGroup()
            }
        }
    }
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
