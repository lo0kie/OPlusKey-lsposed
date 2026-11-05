package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.PathNode
import kotlin.math.sqrt

// **角标与缺口** —— 音量 / 亮度那两族共用的一套几何：右下角那颗小符号怎么画、它占多大一块地、
// 底图该在哪儿让位。
//
//  单独一处是因为这三件事互相咬合：角标尺寸决定缺口半径，缺口半径又决定声波 / 圆环截在哪里。
//  画在图里的时候这些数字一旦分散，改一个就得回去找另外两处。

/** 右下角那个角标：加一档 / 减一档 / 拨到某个数值。 */
internal enum class Badge { Plus, Minus, Gear }

/**
 * 角标在 24 格里占的那块地 —— 圆心是角标中心，半径见 [notch]。
 *
 *  底图（声波、光线、圆环）在画的时候就按这块地让位，于是**三档各截各的**：齿轮比加号大一圈，
 *  缺口也跟着大一圈 —— 不会出现「同一处切口、大角标贴着脸、小角标空一大片」。
 */
internal class Notch(
    val x: Float,
    val y: Float,
    /** 切底图用的半径：弧线就截在这个圆上。 */
    val radius: Float,
    /** 判「整条让位」用的半径（光线那类一格长的笔画）：比 [radius] 小一档，只吞真正挡路的那条。 */
    val clearance: Float,
) {
    /** 这条线段碰不碰缺口（含被整条吞掉的情况）。 */
    fun touches(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = dx * dx + dy * dy
        //  线段上离圆心最近的那一点（投影参数夹在两端之间）
        val t = if (length == 0f) 0f else (((x - x1) * dx + (y - y1) * dy) / length).coerceIn(0f, 1f)
        val nearX = x1 + dx * t - x
        val nearY = y1 + dy * t - y
        return nearX * nearX + nearY * nearY < clearance * clearance
    }
}

/**
 * 这一档角标在 24 格里占到的半径（墨迹，含描边）—— **三档各算各的**：
 * 加号看臂长、减号看它自己那根更短的臂、齿轮看齿尖。
 *
 *  三档共用一份数值的话，切口会按最大的那个走：减号明明短一截，底图却按加号的尺寸让位，
 *  于是减号那颗的留白就比加号大一截。
 */
private val Badge.inkRadius: Float
    get() =
        ICON_STROKE / 2 +
            when (this) {
                Badge.Plus -> BADGE_ARM
                Badge.Minus -> MINUS_ARM
                Badge.Gear -> GEAR_TOOTH
            }

/** 这一档角标占的缺口。 */
internal fun notch(badge: Badge): Notch =
    Notch(
        x = BADGE_CENTER,
        y = BADGE_CENTER,
        radius = badge.inkRadius + NOTCH_GAP,
        clearance = badge.inkRadius + RAY_GAP,
    )

/**
 * 角标那一笔 —— 照图标库 `bell-plus` / `bell-cog` 的那套画，中心 (19, 19)：
 *
 *  · 加号 / 减号：横竖各 6 格（16~22），减号短 0.4 格；
 *  · 齿轮：**环半径 2、六颗齿绕一圈每 60° 一颗、齿长 1.5**（整体 15.5~22.5）——
 *    齿数与齿长都按图标库那颗来：齿多了会挤成一圈毛刺、齿短了整颗看着比加减号小一圈，
 *    与相邻图标放在一起就不像同一套角标。
 */
internal fun badgePath(badge: Badge): List<PathNode> =
    PathData {
        val c = BADGE_CENTER
        when (badge) {
            Badge.Plus -> {
                moveTo(c, c - BADGE_ARM)
                lineTo(c, c + BADGE_ARM)
                moveTo(c - BADGE_ARM, c)
                lineTo(c + BADGE_ARM, c)
            }

            Badge.Minus -> {
                //  比加号短一点：一条不断的横线看上去总比同样长度的十字「长」，
                //  两边一样长时减号会显大 —— 收 0.4 格，两个摆在一起才读成同一档。
                moveTo(c - MINUS_ARM, c)
                lineTo(c + MINUS_ARM, c)
            }

            Badge.Gear -> {
                //  一圈（半径 [GEAR_RING]）
                moveTo(c + GEAR_RING, c)
                arcToRelative(GEAR_RING, GEAR_RING, 0f, isMoreThanHalf = false, isPositiveArc = true, -GEAR_RING * 2, 0f)
                arcToRelative(GEAR_RING, GEAR_RING, 0f, isMoreThanHalf = false, isPositiveArc = true, GEAR_RING * 2, 0f)
                //  六颗齿：正上起、每 60° 一颗，从环上伸到 [GEAR_TOOTH]
                GEAR_DIRECTIONS.forEach { (ux, uy) ->
                    moveTo(c + ux * GEAR_RING, c + uy * GEAR_RING)
                    lineTo(c + ux * GEAR_TOOTH, c + uy * GEAR_TOOTH)
                }
            }
        }
    }

/**
 * 一段圆弧（圆心 y 恒为 12）与缺口圆的交点，取靠上那个 —— 弧就在这一点收笔。
 *
 *  音量那两道声波是同心圆上的一段，都用这一支求交：从顶上往下走，先碰到的是上面那个交点。
 *
 *  @param centerX 圆弧圆心的 x（y 一律 12，这批声波都是同心圆）
 *  @param radius 圆弧半径
 */
internal fun arcCut(
    centerX: Float,
    radius: Float,
    notch: Notch,
): Pair<Float, Float> {
    val dx = notch.x - centerX
    val dy = notch.y - ARC_CENTER_Y
    val distance = sqrt(dx * dx + dy * dy)
    //  沿两圆心连线走 along，再朝上垂直偏 offset 就是上面那个交点
    val along = (distance * distance + radius * radius - notch.radius * notch.radius) / (2f * distance)
    val offset = sqrt(radius * radius - along * along)
    val ux = dx / distance
    val uy = dy / distance
    return (centerX + along * ux + offset * uy) to (ARC_CENTER_Y + along * uy - offset * ux)
}

/**
 * 太阳那一圈与缺口圆的交点（上沿、下沿两点）—— 圈在这两点之间让位。
 *
 *  与 [arcCut] 一样是两圆求交，只是这次要两个解：那一圈得从上沿绕过去接下沿。
 */
internal fun ringCut(notch: Notch): Pair<Pair<Float, Float>, Pair<Float, Float>> {
    val dx = notch.x - SUN_CENTER
    val dy = notch.y - SUN_CENTER
    val distance = sqrt(dx * dx + dy * dy)
    val along = (distance * distance + SUN_RADIUS * SUN_RADIUS - notch.radius * notch.radius) / (2f * distance)
    val offset = sqrt(SUN_RADIUS * SUN_RADIUS - along * along)
    val ux = dx / distance
    val uy = dy / distance
    val midX = SUN_CENTER + along * ux
    val midY = SUN_CENTER + along * uy
    return ((midX + offset * uy) to (midY - offset * ux)) to ((midX - offset * uy) to (midY + offset * ux))
}

/** 这批字形的笔画 —— 与图标库那支笔同宽（都是 2.0 / 24 视口），摆在一起不会一粗一细。 */
internal const val ICON_STROKE = 2f

/** 角标中心（右下角那一块的中心）—— 与图标库那几颗 `bell-*` 的角标同一个位置。 */
private const val BADGE_CENTER = 19f

/** 加号 / 减号的半臂长（横竖各 6 格，与 `bell-plus` 那颗同宽）。 */
private const val BADGE_ARM = 3f

/** 减号的半臂长 —— 比 [BADGE_ARM] 短 0.4：光是一条横线时看上去更长，得收一点才与加号同档。 */
private const val MINUS_ARM = 2.6f

/** 齿轮那圈环的半径。 */
private const val GEAR_RING = 2f

/** 齿轮的齿尖到圆心的距离（环外 1.5 格，与 `bell-cog` 那颗一致）。 */
private const val GEAR_TOOTH = 3.5f

/**
 * 齿轮六颗齿的方向（单位向量，从正上起每 60° 一颗）。
 *
 *  写死而不是现算三角函数：六个数是常量，算一次与查一次结果一样，但查表这份在读的时候
 *  一眼能看出「绕一圈等距」这件事。
 */
private val GEAR_DIRECTIONS =
    listOf(
        0f to -1f,
        0.866f to -0.5f,
        0.866f to 0.5f,
        0f to 1f,
        -0.866f to 0.5f,
        -0.866f to -0.5f,
    )

/** 太阳圆心（两个方向都是 12）。 */
private const val SUN_CENTER = 12f

/** 太阳那圈圆的半径。 */
internal const val SUN_RADIUS = 4f

/** 声波那两道弧的圆心 y —— 两道都是同心圆，圆心都落在这一行上。 */
internal const val ARC_CENTER_Y = 12f

/**
 * 缺口在角标外面再让出的那一圈 —— 3.3 格。
 *
 *  量的是图标库自己的间距：`bell-plus` / `bell-cog` 里，主体墨迹到角标墨迹的最近距离约 **2 格**
 *  （24 视口）。这一圈是**圆心距**，两头各占 1 格描边：缺口圆半径 = 角标本体 + 1（角标描边）
 *  + 3.3，而底图笔画的内沿又在缺口圆上往里 1 格，所以两边墨迹之间正好剩 2 格多 —— 与图标库一致。
 */
private const val NOTCH_GAP = 3.3f

/** 判「整条让位」时多留的间距（给光线那类一格长的笔画用）。 */
private const val RAY_GAP = 2f
