package com.lookie.opluskey.ui.model.icon

import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.PathNode
import kotlin.math.sqrt

// **角标与缺口** —— 音量 / 亮度 / 铃声音量那几族共用的一套几何：右下角那颗小符号怎么画、它占多大一块地、
// 底图该在哪儿让位。
//
//  单独一处是因为这三件事互相咬合：角标尺寸决定缺口半径，缺口半径又决定声波 / 圆环截在哪里。
//  画在图里的时候这些数字一旦分散，改一个就得回去找另外两处。

/**
 * 右下角那个角标：加一档 / 减一档 / 拨到某个数值 / 循环。
 *
 *  **只给音量、亮度、铃声音量、响铃状态那几族**。「应用控制」那三颗用图标库的 `category` /
 *  `category-plus` / `category-minus`（不拼：一叠方块少一块就读不出「应用」了），录屏那三态也不拼
 *  （它们是同一个动作的三种状态，由单选弹窗说）—— 所以这里没有播放 / 停止 / 切换。
 *
 *  [Cycle] 是唯一一个「不是动词」的角标：它标的是响铃状态里的「循环」那一档（按一下轮换三档），
 *  底图是铃铛，角标是一圈箭头。
 */
internal enum class Badge { Plus, Minus, Gear, Cycle, Pause, Power, TriangleUp, TriangleDown, Hash }

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

                Badge.Cycle -> CYCLE_INK

                Badge.Pause -> PAUSE_INK

                Badge.Power -> POWER_INK

                //  三角看底角：顶点离圆心 [TRI_HEIGHT]/2，底角还要再横着挪 [TRI_HALF]
                Badge.TriangleUp -> TRI_INK

                Badge.TriangleDown -> TRI_INK

                //  井号看四条笔的端点
                Badge.Hash -> HASH_ARM
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
 * **方缺角**的起算点：从这一点到右下角那一块要被切掉（x / y 各取这个值）。
 *
 *  只给「底图是图标库整颗字形」的那一路用（见 `DrawnIconCanvas.badgedIcon`）：那种底图是**闭合轮廓**，
 *  拿 [notch] 的圆去咬会在轮廓上留一段月牙弧，读起来像图标破了 —— 手绘那几族才是圆缺口，
 *  因为它们的底图是细笔画，圆只削到笔画末端。
 *
 *  边长 = 角标墨迹 + 一圈**很窄**的留白（[CORNER_GAP]），这是两个错处夹出来的一个值：
 *  · 留太宽（试过 2 格）：边界被推到离角标挺远的地方，底图那些被切断的笔画就在**半空里留下两截
 *    断头** —— 终端框的下边、气泡的右边各剩一小段，看着像图标坏了（真机口径「残留一点点线条」
 *    「线条从中间被切开」）；
 *  · 留太窄（试过 0.8 格）：缺口几乎贴着角标，看不出「缺了一块」。
 *
 *  所以取 1.2：边界刚好罩住角标墨迹，切出来的断头就落在角标自己的笔画边上，不再悬空。
 *  [NOTCH_GAP] 那 3.3 格是按细弧调的，套在方形上会把半个图标切掉，不能直接借。
 */
internal fun cornerNotchStart(badge: Badge): Float = BADGE_CENTER - badge.inkRadius - CORNER_GAP

/** 方缺角那一圈留白 —— 为什么是 1.2，见 [cornerNotchStart]。 */
private const val CORNER_GAP = 1.2f

/**
 * 切底图用的缺口半径 —— 角标墨迹 + 这一圈留白，由调用方自己定。
 *
 *  [notch] 里那个留白（3.3 格）是按声波、圆环那类**细弧**调的：弧在缺口圆上收笔，留多一圈看不出来。
 *  而铃铛的唇、播放三角的底边这类**整条边**要让位时，留白得自己量 —— 太大整条边被吃掉，
 *  太小缺口根本看不出来（真机口径「缺口太少了」）。
 */
internal fun cutRadius(
    badge: Badge,
    gap: Float,
): Float = badge.inkRadius + gap

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
                drawGear()
            }

            Badge.Cycle -> {
                drawCycle()
            }

            Badge.Pause -> {
                //  两道竖笔（暂停那两竖）：比加号窄一档 —— 两条并排摆宽了会读成一个方块
                moveTo(c - PAUSE_GAP, c - PAUSE_HALF)
                lineTo(c - PAUSE_GAP, c + PAUSE_HALF)
                moveTo(c + PAUSE_GAP, c - PAUSE_HALF)
                lineTo(c + PAUSE_GAP, c + PAUSE_HALF)
            }

            Badge.TriangleUp -> {
                //  正三角（音量键「+」）：与图标库 `triangle` 同形，缩到角标大小、重心落在角标中心
                moveTo(c, c - TRI_HEIGHT / 2)
                lineTo(c + TRI_HALF, c + TRI_HEIGHT / 2)
                lineTo(c - TRI_HALF, c + TRI_HEIGHT / 2)
                close()
            }

            Badge.TriangleDown -> {
                //  倒三角（音量键「-」）：同一颗翻过来
                moveTo(c, c + TRI_HEIGHT / 2)
                lineTo(c + TRI_HALF, c - TRI_HEIGHT / 2)
                lineTo(c - TRI_HALF, c - TRI_HEIGHT / 2)
                close()
            }

            Badge.Power -> {
                //  电源符号：一圈（缺口留在正上方）+ 顶上那一竖 —— 照图标库 `power` 的比例缩到角标大小
                moveTo(c + POWER_RING * 0.707f, c - POWER_RING * 0.707f)
                arcToRelative(POWER_RING, POWER_RING, 0f, isMoreThanHalf = true, isPositiveArc = true, -POWER_RING * 1.414f, 0f)
                moveTo(c, c - POWER_BAR)
                lineTo(c, c)
            }

            Badge.Hash -> {
                //  井号（root 的提示符）：两竖 + 两横，四条各 [HASH_ARM] 半长，不画斜 ——
                //  7 格见方里再斜一下就糊成一团，方方正正反而更像提示符本身
                moveTo(c - HASH_GAP, c - HASH_ARM)
                lineTo(c - HASH_GAP, c + HASH_ARM)
                moveTo(c + HASH_GAP, c - HASH_ARM)
                lineTo(c + HASH_GAP, c + HASH_ARM)
                moveTo(c - HASH_ARM, c - HASH_GAP)
                lineTo(c + HASH_ARM, c - HASH_GAP)
                moveTo(c - HASH_ARM, c + HASH_GAP)
                lineTo(c + HASH_ARM, c + HASH_GAP)
            }
        }
    }

/**
 * 齿轮角标 —— 一圈环加六颗齿。
 *
 *  单独一支只因为它比别的角标长得多（环 + 逐个算齿的坐标）。画法与数值没有变，
 *  仍然是「照图标库 `bell-cog` 那一颗」。
 */
private fun PathBuilder.drawGear() {
    val c = BADGE_CENTER
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

/**
 * 循环角标 —— 一段 3/4 的弧加两笔箭头。
 *
 *  照图标库 `calendar-repeat` 右下角那一笔（那套图标里现成的「角标版 repeat」），整体平移到角标
 *  中心 (19, 19)。坐标一个没改，只是平移了 `(0, +1)`：原图标里那一笔的包围盒中心是 (19, 18)。
 *
 *  单独一支的理由与 [drawGear] 相同（长度）。
 */
private fun PathBuilder.drawCycle() {
    moveTo(20f, 15f)
    lineTo(22f, 17f)
    lineTo(19f, 17f)
    moveTo(20f, 19f)
    lineTo(22f, 17f)
    moveTo(21f, 17f)
    arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 0f, 5.236f)
}

/**
 * 一段圆弧（圆心 y 恒为 12）与缺口圆的交点，取**靠上**那个 —— 弧就在这一点收笔。
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
): Pair<Float, Float> = circleCut(centerX, ARC_CENTER_Y, radius, notch).first

/**
 * 同一段圆弧与缺口圆的交点，取**靠下**那个 —— 铃舌是唇下垂着的小半圆，在右下角那一侧收笔。
 *
 *  与 [arcCut] 是同一个式子的两个根：声波从顶上往下走要上交点，铃舌从唇上往下绕要下交点。
 *
 * @param centerX 圆弧圆心的 x
 * @param centerY 圆弧圆心的 y（铃舌的圆心在 (12, 18)，不在这批声波那条 y=12 上）
 */
internal fun arcCutLower(
    centerX: Float,
    centerY: Float,
    radius: Float,
    notch: Notch,
): Pair<Float, Float> = circleCut(centerX, centerY, radius, notch).second

/**
 * 一圈（任意圆心、半径）与缺口圆的交点，取**上沿、下沿**两点 —— 圈在这两点之间让位。
 *
 *  与 [arcCut] 一样是两圆求交，只是这次要两个解：那一圈得从上沿绕过去接下下沿。
 */
internal fun ringCut(
    centerX: Float,
    centerY: Float,
    radius: Float,
    notch: Notch,
): Pair<Pair<Float, Float>, Pair<Float, Float>> = circleCut(centerX, centerY, radius, notch)

/**
 * 两圆求交 —— 缺口这套几何里**唯一**的算式，三处收笔点都由它算：声波（上交点）、
 * 太阳那一圈（两个交点）、铃舌（下交点）。
 *
 *  返回 `(靠上那个) to (靠下那个)`。写一处而不是各抄一份：缺口的半径一变，
 *  这几个收笔点必须**同时**跟着动，抄成三份就会漏改其中两处。
 */
private fun circleCut(
    centerX: Float,
    centerY: Float,
    radius: Float,
    notch: Notch,
): Pair<Pair<Float, Float>, Pair<Float, Float>> {
    val dx = notch.x - centerX
    val dy = notch.y - centerY
    val distance = sqrt(dx * dx + dy * dy)
    //  沿两圆心连线走 along，再垂直偏 offset 就是那两个交点
    val along = (distance * distance + radius * radius - notch.radius * notch.radius) / (2f * distance)
    val offset = sqrt(radius * radius - along * along)
    val ux = dx / distance
    val uy = dy / distance
    val midX = centerX + along * ux
    val midY = centerY + along * uy
    return ((midX + offset * uy) to (midY - offset * ux)) to ((midX - offset * uy) to (midY + offset * ux))
}

/** 这批字形的笔画 —— 与图标库那支笔同宽（都是 2.0 / 24 视口），摆在一起不会一粗一细。 */
internal const val ICON_STROKE = 2f

/**
 * 右下角那颗角标的笔画 —— 比底图**细一档**（2 → 1.7）。
 *
 *  角标只有 7 格大，用底图那支 2.0 的笔画时，加号、齿轮、那圈箭头挤在一起会糊成一坨；
 *  细一档就分得开，又不至于细到像另一套图标（真机口径「不要细太多」）。
 */
internal const val BADGE_STROKE = 1.7f

/** 角标中心（右下角那一块的中心）—— 与图标库那几颗 `bell-*` 的角标同一个位置。 */
private const val BADGE_CENTER = 19f

/** 加号 / 减号的半臂长（横竖各 6 格，与 `bell-plus` 那颗同宽）。 */
private const val BADGE_ARM = 3f

/**
 * 井号角标（「以 root 执行」）的半臂长 —— 四条笔的端点离角标中心 3.2 格。
 *
 *  比 [BADGE_ARM] 略长：井号是四条**互相平行**的短线拼出来的，端点那一截在视觉上会「缩」进去一点，
 *  与加号同长时整颗看着小一圈。
 */
private const val HASH_ARM = 3.2f

/** 井号那两横 / 两竖之间的距离（半间距）—— 间距再大中间就空了，四条笔读成四段。 */
private const val HASH_GAP = 1.5f

/** 减号的半臂长 —— 比 [BADGE_ARM] 短 0.4：光是一条横线时看上去更长，得收一点才与加号同档。 */
private const val MINUS_ARM = 2.6f

/** 齿轮那圈环的半径。 */
private const val GEAR_RING = 2f

/** 齿轮的齿尖到圆心的距离（环外 1.5 格，与 `bell-cog` 那颗一致）。 */
private const val GEAR_TOOTH = 3.5f

/** 循环角标的墨迹半径（不含描边）：那一笔最远的一点离圆心 4.1 格。 */
private const val CYCLE_INK = 4.1f

/** 暂停那两道竖笔的半高。 */
private const val PAUSE_HALF = 2.2f

/** 暂停那两道竖笔离中心的横向距离 —— 两条墨迹之间留 1.2 格。 */
private const val PAUSE_GAP = 1.6f

/** 暂停角标的墨迹半径（不含描边）：竖笔的端点到圆心 2.72 格。 */
private const val PAUSE_INK = 2.72f

/** 电源符号那圈的半径。 */
private const val POWER_RING = 3.3f

/** 电源符号顶上那一竖的长度（从圆心往上量）。 */
private const val POWER_BAR = 3.5f

/** 电源角标的墨迹半径（不含描边）：那一竖的顶端到圆心 3.5 格。 */
private const val POWER_INK = 3.5f

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

/**
 * 三角角标的半底宽（正 / 倒两档共用）。
 *
 *  2.5 / 4.4 是「与加号那颗一般大」的量：加号墨迹到 3.85 格，三角底角落在 3.33 + 描边 ≈ 4.2 格。
 *  再小一档（1.9 / 3）在 20dp 上只剩一个点，读不出是三角（真机口径「角标太小了看不清」）。
 */
private const val TRI_HALF = 3f

/** 三角角标的高 —— 顶点到底边。 */
private const val TRI_HEIGHT = 5.2f

/**
 * 三角角标**参与缺口计算**的墨迹半径 —— 先钉在放大前那一档（用户口径「缺口先不动」）。
 *
 *  三角画得比这更大时，底图（喇叭那两道声波）的让位仍按这个数走，于是三角会压到声波上；
 *  这是先把形状定下来、再回头对齐缺口的两步走。等尺寸定了，这里改成
 *  `sqrt(TRI_HALF² + TRI_HEIGHT²/4)` 就重新跟上了。
 */
private const val TRI_INK = 3.33f

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
