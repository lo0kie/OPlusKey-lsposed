package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * **自己落 path 的那批图标** —— 图标库里没有的形状全在这里，一支笔、一套规则。
 *
 * ## 为什么单独一处
 *
 *  [PageIcons] 是一张「名字 → 图标」的翻译表：一条一行 `TablerIcons.Outline.*`，读的人扫一眼就知道
 *  某个位置用的是哪颗现成字形。手绘那几支要建 builder、算坐标、配笔画宽度，混在表里会把
 *  「查表」和「画图」两件事搅在一起 —— 表被撑开、构图说明也没地方写。所以画与取名字分开：
 *  **这里只画**，用它的地方直接取 [DrawnIcons] 的成员。
 *
 * ## 一支笔的三条约定
 *
 *  1. **视口恒为 24、默认尺寸恒为 24dp** —— 与 Tabler 那批同尺寸，同一个列表里不会一粗一细、
 *     一大一小；
 *  2. **描边不填充**，圆头 + 圆角；宽度见各支自己的常量（1.4~2.0），都对 Tabler 的 2.0 取齐；
 *  3. **笔画颜色恒为黑**：真正的颜色由调用方 `Icon(tint = …)` 的 `SrcIn` 决定，这里的黑只是占位
 *     （色块那两支除外 —— 它们靠 `alpha` 分色阶，同样由上色方保留）。
 */
internal object DrawnIcons {
    /**
     * 首页「按键」卡里侧键那一行，也是组合键筛选菜单里那一格 —— **手机外框 + 左边凸出来的一颗侧键**。
     *
     *  与应用图标同一个 motif（那颗图标的字形就是「手机 + 凸起的侧键 + 面板」，见
     *  `res/drawable/ic_module_foreground.xml`）；24 视口上只留最能读懂的两件：外框与那颗凸起，
     *  面板与箭头在这种尺寸下会糊成一团。
     *
     *  与相邻的圈里数字（`PageIcons.menuKeyNumber`）必须不同字形：清单里第一行是侧键、往下是
     *  收集来的键，只靠行首这一颗分谁是谁。
     */
    val MenuSideKey: ImageVector by lazy {
        //  构图：手机偏右（8.4~18.4），侧键在左边（x=5.6）与它并排 —— 整个字形左右居中在 24 格里
        ImageVector
            .Builder(
                name = "MenuSideKey",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                addPath(
                    pathData =
                        PathData {
                            val left = 8.4f
                            val top = 3f
                            val right = left + 10f
                            val bottom = 21f
                            val corner = 2.6f
                            moveTo(left + corner, top)
                            lineTo(right - corner, top)
                            quadTo(right, top, right, top + corner)
                            lineTo(right, bottom - corner)
                            quadTo(right, bottom, right - corner, bottom)
                            lineTo(left + corner, bottom)
                            quadTo(left, bottom, left, bottom - corner)
                            lineTo(left, top + corner)
                            quadTo(left, top, left + corner, top)
                            close()
                        },
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = SIDE_KEY_STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    // 一条圆头短线：圆帽就是这颗键的两端。比手机外框那一竖的 1/6 略长一点就够 ——
                    //  再长就像贴在边框上的第三条边，短了又只剩一个圆点。
                    pathData =
                        PathData {
                            moveTo(5.6f, 9.2f)
                            lineTo(5.6f, 11.6f)
                        },
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = SIDE_KEY_BAR_STROKE,
                    strokeLineCap = StrokeCap.Round,
                )
            }.build()
    }

    // —— 音量 / 亮度：一颗底图 + 右下角一个角标 ——
    //
    //  格子里放底图本身，菜单三行放「同一颗底图 + 右下角角标」：加、减、齿轮。**底图不缩**，
    //  只是它右下角那一笔在画的时候就断开/让位（与图标库 `bell-plus` / `bell-cog` 一个路子），
    //  角标落进让出来的空里 —— 一个字形两笔，不是两块拼起来。
    //  两族共用同一套角标与同一个位置，于是「增加 / 减少 / 设置为」在音量与亮度里长得一模一样，
    //  只有底图不同 —— 扫一眼先认出「是加还是设」，再认出「加的是什么」。

    /**
     * 音量格：**喇叭 + 两道声波**，说的是「音量」这件事本身。
     *
     *  「音量 + / 音量 −」那两颗**按键**动作也取 [VolumeAdd] / [VolumeSub] —— 同样是「让声音大 / 小一点」，
     *  不必再按声波数另画一套；只有格子那颗（不带角标）是这一族自己的底图。
     */
    val VolumeTile: ImageVector by lazy { flatIcon("VolumeTile", volumeBase(notch = null)) }

    /** 「增加音量」—— 底图（右下角让位）+ 右下角加号。 */
    val VolumeAdd: ImageVector by lazy { badgedIcon("VolumeAdd", Badge.Plus, ::volumeBase) }

    /** 「减少音量」—— 底图 + 右下角减号。 */
    val VolumeSub: ImageVector by lazy { badgedIcon("VolumeSub", Badge.Minus, ::volumeBase) }

    /** 「设置为」—— 底图 + 右下角齿轮（拨到某个数值）。 */
    val VolumeSet: ImageVector by lazy { badgedIcon("VolumeSet", Badge.Gear, ::volumeBase) }

    /** 亮度格：**太阳**（圆 + 八道短光），坐标与图标库那颗 `sun` 一致。 */
    val BrightnessTile: ImageVector by lazy { flatIcon("BrightnessTile", sunBase(notch = null)) }

    /** 「增加亮度」—— 底图（右下角的光让位）+ 加号。 */
    val BrightnessAdd: ImageVector by lazy { badgedIcon("BrightnessAdd", Badge.Plus, ::sunBase) }

    /** 「减少亮度」—— 底图 + 减号。 */
    val BrightnessSub: ImageVector by lazy { badgedIcon("BrightnessSub", Badge.Minus, ::sunBase) }

    /** 「设置为」—— 底图 + 齿轮。 */
    val BrightnessSet: ImageVector by lazy { badgedIcon("BrightnessSet", Badge.Gear, ::sunBase) }

    /** 一格铺满的字形（不带角标）—— 两族的底图与格子都用它，笔画同一档。 */
    private fun flatIcon(
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
     *  **底图不缩、角标不叠**：底图仍是满格那一颗（笔画同宽、坐标不动），只是右下角那一笔在画的时候
     *  就**让开**了（见 [volumeWaves] / [sunRays]）—— 角标正好落在让出来的那块空里，
     *  与图标库里 `bell-plus` / `bell-cog` 的画法一致：一个字形、两笔，不是两块拼起来。
     *
     *  @param base 底图的画法，按这一档角标占的缺口截（缺口见 `DrawnIconBadge.notch`）
     */
    private fun badgedIcon(
        name: String,
        badge: Badge,
        base: (Notch?) -> List<PathNode>,
    ): ImageVector {
        val builder = vectorBuilder(name)
        builder.addStroked(base(notch(badge)))
        builder.addStroked(badgePath(badge))
        return builder.build()
    }

    /**
     * 音量底图：喇叭本体 + 两道声波。
     *
     *  @param notch 带角标的那三颗传这一档的缺口（见 [Notch]）：外弧在缺口圆上收笔；
     *    格子那颗传 `null`，画满。
     */
    private fun volumeBase(notch: Notch?): List<PathNode> = volumeBody() + volumeWaves(notch)

    /** 喇叭本体：方箱 + 朝右张开的锥面（一笔闭合，靠圆角把三处拐点收圆）。 */
    private fun volumeBody(): List<PathNode> =
        PathData {
            moveTo(11f, 5f)
            lineTo(6f, 9f)
            lineTo(2f, 9f)
            lineTo(2f, 15f)
            lineTo(6f, 15f)
            lineTo(11f, 19f)
            close()
        }

    /**
     * 喇叭右侧那两道声波 —— 两道同心圆弧（圆心都在 y=12 这一行上，半径 5 / 10）。
     *
     *  **两道都要让位**：靠里那道从右数第二根线，端头正对着角标，只裁外面那道时它就成了
     *  顶在角标边上的那根 —— 三档都得按缺口圆截断，只是内弧的截点比外弧更靠上。
     */
    private fun volumeWaves(notch: Notch?): List<PathNode> =
        PathData {
            moveTo(14.8f, 8.6f)
            if (notch == null) {
                arcToRelative(INNER_WAVE_RADIUS, INNER_WAVE_RADIUS, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 6.8f)
            } else {
                val cut = arcCut(INNER_WAVE_CENTER_X, INNER_WAVE_RADIUS, notch)
                arcToRelative(
                    INNER_WAVE_RADIUS,
                    INNER_WAVE_RADIUS,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    cut.first - 14.8f,
                    cut.second - 8.6f,
                )
            }
            moveTo(17.9f, 5f)
            if (notch == null) {
                //  格子那颗：从顶上一直弯到底
                arcToRelative(OUTER_WAVE_RADIUS, OUTER_WAVE_RADIUS, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14f)
            } else {
                //  带角标：弯到与缺口圆相交那一点为止 —— 三档缺口大小不同，收笔位置也就不同
                val cut = arcCut(OUTER_WAVE_CENTER_X, OUTER_WAVE_RADIUS, notch)
                arcToRelative(
                    OUTER_WAVE_RADIUS,
                    OUTER_WAVE_RADIUS,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    cut.first - 17.9f,
                    cut.second - 5f,
                )
            }
        }

    /**
     * 亮度底图：太阳 = 一圈 + 八道短光。
     *
     *  @param notch 带角标的那三颗传这一档的缺口：落进缺口里的光不画；格子那颗传 `null`，八道全画。
     */
    private fun sunBase(notch: Notch?): List<PathNode> = sunRing(notch) + sunRays(notch)

    /**
     * 太阳那一圈：圆心 (12, 12)、半径 4。
     *
     *  带角标时这一圈也要**缺一角**：在缺口圆里的那一段不画，从缺口的上沿绕过左边接到下沿 ——
     *  只让光线让位、圈还画满，角标就会压在这道弧上，看着像贴上去的。
     */
    private fun sunRing(notch: Notch?): List<PathNode> =
        PathData {
            if (notch == null) {
                moveTo(8f, 12f)
                arcToRelative(SUN_RADIUS, SUN_RADIUS, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 0f)
                arcToRelative(SUN_RADIUS, SUN_RADIUS, 0f, isMoreThanHalf = false, isPositiveArc = true, -8f, 0f)
            } else {
                val (upper, lower) = ringCut(notch)
                moveTo(upper.first, upper.second)
                //  从缺口上沿逆时针绕一大圈（跨过 3/4 个圆）到缺口下沿
                arcToRelative(
                    SUN_RADIUS,
                    SUN_RADIUS,
                    0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    lower.first - upper.first,
                    lower.second - upper.second,
                )
            }
        }

    /** 太阳那八道短光：四条正方向、四条 45° 斜向，长度 1。 */
    private fun sunRays(notch: Notch?): List<PathNode> =
        PathData {
            ray(3f, 12f, 4f, 12f, notch)
            ray(12f, 3f, 12f, 4f, notch)
            ray(20f, 12f, 21f, 12f, notch)
            ray(12f, 20f, 12f, 21f, notch)
            ray(5.6f, 4.6f, 6.3f, 5.3f, notch)
            ray(18.4f, 4.6f, 17.7f, 5.3f, notch)
            ray(6.3f, 16.7f, 5.6f, 17.4f, notch)
            ray(17.7f, 16.7f, 18.4f, 17.4f, notch)
        }

    /** 一道短光：碰着缺口圆就不画 —— 一格长的光被截掉半截没有意义，整条让位更干净。 */
    private fun PathBuilder.ray(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        notch: Notch?,
    ) {
        if (notch != null && notch.touches(x1, y1, x2, y2)) return
        moveTo(x1, y1)
        lineTo(x2, y2)
    }

    /** 内弧（右数第二根线）的圆心 x 与半径 —— 起点 (14.8, 8.6)、走 (0, 6.8)。 */
    private const val INNER_WAVE_CENTER_X = 11.14f

    private const val INNER_WAVE_RADIUS = 5f

    /** 外弧的圆心 x 与半径 —— 起点 (17.9, 5)。 */
    private const val OUTER_WAVE_CENTER_X = 10.76f

    private const val OUTER_WAVE_RADIUS = 10f

    /** 太阳那圈圆的半径。 */
    private const val SUN_RADIUS = 4f

    /**
     * 手电筒 —— **手绘**：宽口的灯罩 + 直筒身 + 一个开关记号。
     *
     *  图标库那颗 `flashlight` 是斜放的（灯头朝左上、光线方向靠构图暗示），在这一列 20dp 的行首里
     *  会读成「一个歪着的方块」；摆正之后头顶那道宽口就是最好认的那一笔，筒身只交代「是个手电」。
     *  身上那条短竖线是开关 —— 没有它，整颗就只剩一个漏斗。
     */
    val Flashlight: ImageVector by lazy {
        flatIcon(
            "Flashlight",
            PathData {
                //  灯罩：宽顶收口到筒身（一笔闭合）
                moveTo(6f, 3.5f)
                lineTo(18f, 3.5f)
                lineTo(14.8f, 9.5f)
                lineTo(9.2f, 9.5f)
                close()
                //  筒身：两条竖边 + 圆角收底
                moveTo(9.2f, 9.5f)
                lineTo(14.8f, 9.5f)
                lineTo(14.8f, 20f)
                arcToRelative(1.6f, 1.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.6f, 1.6f)
                lineTo(10.8f, 21.6f)
                arcToRelative(1.6f, 1.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.6f, -1.6f)
                close()
                //  开关
                moveTo(12f, 14.6f)
                lineTo(12f, 16.6f)
            },
        )
    }

    /**
     * 「作者 → 酷安」那一行的行首 —— **酷安自己的标志**（图标库里没有这一支）。
     *
     *  pathData 是从手机上那版酷安安装包（`com.coolapk.market` 的 adaptive icon 前景层，
     *  108 视口的 vector）里原样取出来的，只做等比缩放与平移摆进 24 格，形状一个点没动。
     *
     *  官方这支是**实心**的一块，所以这里 fill 不 stroke —— 与相邻那些描边品牌字形不是一路，
     *  但重画成描边就不是酷安了；上色仍由 `SrcIn` 给。
     */
    val BrandCoolapk: ImageVector by lazy {
        val builder = vectorBuilder("BrandCoolapk")
        builder.addPath(pathData = coolapkPath(), fill = SolidColor(Color.Black))
        builder.build()
    }

    /** 官方 pathData 解析成节点 —— 解析失败就当空字形，绝不在组合期抛异常（降级纪律）。 */
    private fun coolapkPath(): List<PathNode> =
        runCatching { PathParser().parsePathString(COOLAPK_PATH).toNodes() }.getOrDefault(emptyList())

    /**
     * 酷安官方标志的 pathData，已经换算到 24 格。
     *
     *  原始坐标是 108 视口里 x 31.3~76.5 / y 41.8~66.1 那一块（adaptive icon 的安全区里），
     *  换算用的等比系数是 20 / 45.2（45.2 是那块图形的宽度），再加一次平移把它摆到 24 格正中。
     *  改这个字符串之前先把 `assets` 里的原图重画一遍，别手改控制点。
     */
    private const val COOLAPK_PATH =
        "M 19.65,11.62c -1.37,-2.48,-2.57,-4.56,-2.61,-4.65l 0,0c -0.13,-0.22,-0.4,-0.35,-0.66,-0.35" +
            "h -0.09c -0.22,0,-0.44,0.13,-0.58,0.31c -0.35,0.49,-0.66,0.97,-0.97,1.5" +
            "c -2.04,3.54,-3.01,4.82,-4.25,5.84c -1.06,0.97,-2.57,1.37,-4.03,1.06" +
            "c -0.27,-0.09,-0.53,-0.18,-0.8,-0.31c -0.58,-0.31,-1.06,-0.75,-1.42,-1.33" +
            "c -0.18,-0.27,-0.27,-0.53,-0.31,-0.84c -0.22,-0.88,-0.09,-1.81,0.35,-2.61l 0,0l 0.04,-0.09" +
            "c 0.31,-0.49,0.71,-0.88,1.24,-1.15l 0.09,-0.04l 0.04,-0.04L 5.85,8.92h 0.04" +
            "c 0.04,-0.04,0.09,-0.04,0.13,-0.04l 0,0l 0.13,-0.04c 0.58,-0.18,1.19,-0.22,1.77,-0.18" +
            "c 1.28,0.13,2.26,0.66,3.85,1.99c 0.13,0.13,0.27,0.22,0.4,0.31c 0.53,-0.31,1.02,-0.66,1.5,-1.06" +
            "c 0.04,-0.09,-1.42,-1.28,-2.21,-1.81c -0.71,-0.44,-1.42,-0.84,-2.21,-1.11" +
            "c -0.44,-0.13,-0.84,-0.22,-1.28,-0.27c -0.22,-0.04,-0.4,-0.04,-0.62,-0.04" +
            "c -2.96,0,-5.35,2.39,-5.35,5.35l 0,0c 0,0.04,0,0.13,0,0.18c 0.09,1.95,1.19,3.67,2.92,4.56" +
            "l 0.04,0.04l 0.09,0.04c 0.4,0.18,0.8,0.31,1.19,0.4h 0.09h 0.09c 0.31,0.04,0.58,0.09,0.88,0.09" +
            "h 0.09c 0.75,0,1.46,-0.13,2.12,-0.35c 1.37,-0.53,2.96,-1.86,4.16,-3.54" +
            "c 0.4,-0.53,1.19,-1.81,2.21,-3.5c 0.09,-0.18,0.22,-0.35,0.35,-0.53" +
            "c 0.09,0.09,1.99,3.58,1.95,3.63s -0.49,-0.31,-1.02,-0.71c -0.31,-0.27,-0.71,-0.53,-1.11,-0.71" +
            "c -0.49,0.35,-0.97,0.71,-1.46,1.11c 0,0.04,0.8,0.62,1.73,1.33l 3.14,2.3" +
            "c 1.33,0.97,1.46,1.06,1.68,1.06c 0.49,0,0.88,-0.4,0.88,-0.88v -0.04" +
            "C 22.13,16.14,21.96,15.74,19.65,11.62z"

    /**
     * 侧键那颗外框的笔画宽度 —— 与 Tabler 那一档（2dp）对齐，收 0.1 是为了让圆头笔画的两端
     * 不顶到 24 格的边界。
     */
    private const val SIDE_KEY_STROKE = 1.9f

    /**
     * 侧键那一条的笔画宽度 —— 比外框细一档。
     *
     *  外框是**轮廓**、侧键是**贴着轮廓凸出来的那一条**；两者同宽时，那条短线读起来像外框
     *  拐过来的一段（这颗键就消失了），细一点才分得开。它不跟外框共用同一个常量。
     */
    private const val SIDE_KEY_BAR_STROKE = 1.4f
}
