package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt

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
     *  与相邻的花结数字（`PageIcons.menuKeyNumber`）必须不同字形：清单里第一行是侧键、往下是
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
    //  这几族共用同一套角标与同一个位置，于是「增加 / 减少 / 设置为」在音量、亮度、铃声音量里
    //  长得一模一样，只有底图不同 —— 扫一眼先认出「是加还是设」，再认出「加的是什么」。

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

    // —— 铃声音量：**格子那颗铃铛** + 同一套右下角角标 ——
    //
    //  底图就是格子上那颗 `bell-ringing` 本身（钟身 + 铃舌 + 两条响铃弧线），不换形状、不缩尺寸；
    //  三行只是在这颗底图的右下角截一刀、把角标放进去 —— 于是「格子」与「三行」是同一颗图标，
    //  差别只有那个角标。
    //  画法与音量 / 亮度**同一套**：底图满格，右下角那几笔按缺口截掉，角标落进缺口里。

    /**
     * 铃声音量三行共用的底图：格子上那颗 `bell-ringing` —— 钟身 + 顶钮 + 铃舌 + 两条响铃弧线。
     *
     *  坐标一个没动（唇 4~20 / y=17，腰 x=6 与 18，穹顶 r=7、顶钮 r=2、铃舌 r=3，
     *  两条弧线 11.05 半径），所以它与格子那颗**逐点重合**，只有右下角按角标让了位。
     *
     *  让位的三笔与图标库 `bell-plus` / `bell-cog` 同一个做法：**唇、右腰、铃舌**各自在缺口圆上
     *  收笔（不是整颗缩小，也不是把角标叠上去）—— 缺口随角标大小变，齿轮那档收得最早。
     *  两条响铃弧线在穹顶两侧、离缺口很远，三档都画满。
     *
     *  **穹顶必须宽**：穹顶一窄、两侧就变成几乎竖直的斜边，整颗读成「尖头锥子」而不是铃铛；
     *  顶钮同理，要画成**圆头凸起**（半个小圆），一根带圆帽的短竖在 20dp 下就是一根尖刺。
     *
     *  @param notch 这一档角标占的缺口；`null` 画满（这一族现在三档都带角标，
     *    留着与 [volumeBase] / [sunBase] 同形，也给「以后要不要一颗不带角标的铃铛」留了口子）
     *  @param ringing 要不要画那两条响铃弧线（就是格子那颗 `bell-ringing` 与 `bell` 的差别）：
     *    铃声音量那三行要（说的就是「响铃」），响铃状态的「循环」不要 —— 那颗角标本身就是一圈箭头，
     *    再套两条响铃弧线，一格里三处弧线，角标反而更看不清
     */
    private fun bellBase(
        notch: Notch?,
        ringing: Boolean = true,
    ): List<PathNode> =
        PathData {
            //  唇（y=17）与右腰（x=18）各自在缺口圆上收笔：圆心 (19,19)，两个方向上的间距是 2 与 1
            val lipEnd = notch?.let { 19f - sqrt(it.radius * it.radius - 4f) } ?: 20f
            val sideEnd = notch?.let { 19f - sqrt(it.radius * it.radius - 1f) } ?: 14f
            //  钟身：从唇的右端（缺口上）起笔，逆着绕一圈回到右腰的收笔点
            moveTo(lipEnd, 17f)
            lineTo(4f, 17f)
            arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -3f)
            lineTo(6f, 11f)
            arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, -6f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4f, 0f)
            arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 6f)
            lineTo(18f, sideEnd)
            //  不带角标：右外扩与整条唇补齐（与图标库那颗 `bell-ringing` 一致）
            if (notch == null) {
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 3f)
                lineTo(lipEnd, 17f)
            }
            //  铃舌：唇上垂下来的一条短竖 + 半个小圆，右端同样收在缺口圆上
            val clapperEnd = notch?.let { arcCutLower(12f, 18f, 3f, it) } ?: (15f to 18f)
            moveTo(9f, 17f)
            lineTo(9f, 18f)
            arcToRelative(
                3f,
                3f,
                0f,
                isMoreThanHalf = false,
                isPositiveArc = false,
                clapperEnd.first - 9f,
                clapperEnd.second - 18f,
            )
            if (notch == null) {
                lineTo(15f, 17f)
            }
            //  两条响铃弧线：与格子那颗同一对，位置在穹顶两侧、离缺口很远，带角标的三档都画满
            if (ringing) {
                moveTo(21f, 6.727f)
                arcToRelative(11.05f, 11.05f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.794f, -3.727f)
                moveTo(3f, 6.727f)
                arcToRelative(11.05f, 11.05f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.792f, -3.727f)
            }
        }

    /** 「增加铃声音量」—— 满格铃铛（右下角让位）+ 右下角加号。 */
    val RingerVolumeAdd: ImageVector by lazy { badgedIcon("RingerVolumeAdd", Badge.Plus, ::bellBase) }

    /** 「减少铃声音量」—— 满格铃铛 + 右下角减号。 */
    val RingerVolumeSub: ImageVector by lazy { badgedIcon("RingerVolumeSub", Badge.Minus, ::bellBase) }

    /** 铃声「设置为」—— 满格铃铛 + 右下角齿轮（拨到某个数值）。 */
    val RingerVolumeSet: ImageVector by lazy { badgedIcon("RingerVolumeSet", Badge.Gear, ::bellBase) }

    /**
     * 响铃状态的「循环」那一档：**同一颗满格铃铛** + 右下角一圈箭头。
     *
     *  与上面三颗同一套：底图不缩、右下角按缺口让位，只有角标不同 —— 于是「循环」与
     *  「增加 / 减少 / 设置为」摆在同一个菜单里，第一眼都是铃铛，第二眼才看角标。
     *
     *  底图**不画那两条响铃弧线**：这一格的角标本身就是一圈箭头，再套两条弧线，7 格的角标
     *  挤在四道弧线里更看不清。
     */
    val RingerCycle: ImageVector by lazy { badgedIcon("RingerCycle", Badge.Cycle) { bellBase(it, ringing = false) } }

    /**
     * 媒体控制的「播放/暂停」（`keyevent:85`）：**播放三角** + 右下角暂停两笔。
     *
     *  三角用的是图标库那颗 `player-play` 的原坐标（7,4 / 7,20 / 20,12），一点没缩 —— 与单独那一档
     *  「播放」一样大。暂停那两笔落在三角底边的右下方，底边在那里**断成两截**让出缺口
     *  （与铃铛的唇、右腰同一个做法）：不断的话，那两笔会贴着底边，看着像画糊了。
     */
    val MediaPlayPause: ImageVector by lazy {
        badgedIcon("MediaPlayPause", Badge.Pause) { _ ->
            //  底边（7,20 → 20,12）与缺口圆的交点：解一次这条线与圆的方程。半径用 [cutRadius] 自己定 ——
            //  `notch` 那个留白（3.3）是给细弧用的，整条边按它切会把底边吃掉大半
            val r = cutRadius(Badge.Pause, 2.7f)
            val len = sqrt(233f) // 底边长：13² + 8²
            val b = -328f / len // 2·(P0−C)·d，P0 = (7,20)、C = (19,19)
            val half = sqrt(b * b - 4f * (145f - r * r)) / 2f
            val t1 = -b / 2f - half
            val t2 = -b / 2f + half
            PathData {
                moveTo(7f, 4f)
                lineTo(7f, 20f)
                lineTo(7f + 13f * t1 / len, 20f - 8f * t1 / len)
                //  缺口之后接着画：底边的剩余一段 + 斜边
                moveTo(7f + 13f * t2 / len, 20f - 8f * t2 / len)
                lineTo(20f, 12f)
                lineTo(7f, 4f)
            }
        }
    }

    /** 「增加亮度」—— 底图（右下角的光让位）+ 加号。 */
    val BrightnessAdd: ImageVector by lazy { badgedIcon("BrightnessAdd", Badge.Plus, ::sunBase) }

    /** 「减少亮度」—— 底图 + 减号。 */
    val BrightnessSub: ImageVector by lazy { badgedIcon("BrightnessSub", Badge.Minus, ::sunBase) }

    /** 「设置为」—— 底图 + 齿轮。 */
    val BrightnessSet: ImageVector by lazy { badgedIcon("BrightnessSet", Badge.Gear, ::sunBase) }

    // —— 录屏：只是那颗摄像机，**不拼角标** ——
    //
    //  为什么不给三档各拼一颗（与音量 / 亮度同一套做法）：那三档是**同一个动作的三种状态**，
    //  而状态已经由单选弹窗与小字说清了（`录屏 - 开始`）。拼角标的代价是底图必须让出右下角，
    //  而摄像机最好认的那一笔（朝右张开的镜头）正好长在那儿 —— 让位之后它就不像摄像机了
    //  （真机口径「效果太差了，缺角裁切呢，小图标也变形了」）。所以这一族只留底图
    //  （用户口径「录屏不用换图标了，改成用选择器选三态」）。
    //  底图与 [Glyph.ScreenRecord][ActionCatalog.Glyph.ScreenRecord] 那颗共用，格子与弹窗标题顺眼。

    /**
     * 录屏那一格 / 那一行：摄像机 —— 机身（圆角矩形）+ 右侧朝外张开的镜头。
     *
     *  **满格、不让位**：这一族只出格子与弹窗标题那一颗（三态不拼角标，见上面那段），
     *  所以不需要给右下角腾地方 —— 机身 x 2~15.5 / y 6~17.5、镜头顶点落在 (21,16.5)，
     *  与图标库那颗 `video` 同一个构图。**整颗在 24 格里居中**（横向 2~21、纵向 6~17.5）：
     *  第一版压在 y 4~15.5，与旁边的音量 / 应用控制摆在一列里明显偏高。
     *
     *  形状直接写在 `by lazy` 里：只有这一颗用它，单独立一个 `cameraBase()` 只会让这个
     *  object 的函数数顶破 detekt 的 `TooManyFunctions`。
     */
    val ScreenRecordTile: ImageVector by lazy {
        flatIcon(
            "ScreenRecordTile",
            PathData {
                //  机身：圆角矩形（四个角各 2 格）
                val left = 2f
                val top = 6f
                val right = 15.5f
                val bottom = 17.5f
                val corner = 2f
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

                //  镜头：机身后方向右张开的一笔（与图标库那颗 `video` 同一个形状）
                moveTo(right, 10.5f)
                lineTo(21f, 7.5f)
                lineTo(21f, 16.5f)
                lineTo(right, 13.5f)
            },
        )
    }

    // —— 小窗那两档：**竖着的窗口** ——
    //
    //  图标库 `app-window-*` 那一族只有横版（18 宽 × 14 高），整颗逆时针转 90° 得到竖版：
    //  外框从 3~21 × 5~19 变成 5~19 × 3~21，那块小窗跟着转 —— 右下角那颗转到**右上角**，
    //  正中那颗转完还在正中（它的中心就是旋转中心）。
    //  坐标在这里转好而不是运行时转：`ImageVector` 交给 `Icon` 之后没有旋转的入口，
    //  转在字形的坐标里，任何调用点拿到的都是同一颗竖版。

    /** 「把前台应用收成小窗」那一档：竖窗口 + **正中**一块小窗（`app-window-center` 转 90°）。 */
    val MiniWindowExpand: ImageVector by lazy { miniWindow("MiniWindowExpand", blockLeft = 11f, blockTop = 11f) }

    /** 「收起小窗」那一档：竖窗口 + **右上角**一块小窗（`app-window-bottom-right` 转 90°）。 */
    val MiniWindowCollapse: ImageVector by lazy { miniWindow("MiniWindowCollapse", blockLeft = 14f, blockTop = 6f) }

    /**
     * 竖窗口：圆角外框（5,3 ~ 19,21，四角 r=2）+ 一块小窗。
     *
     *  小窗那块的边长是 **2 格、笔画 2** —— 笔画正好盖满内部，落下来是 4×4 的实心方块（外沿带圆角），
     *  也就是图标库那颗 `fill + stroke` 的观感：**比 2 格宽的边框粗一圈**。
     *  只填不描边会小一圈（2×2 的点看着比框细），正中与角上就更难认了。
     *
     *  @param blockLeft 那块小窗（2×2 的路径，墨迹 4×4）的左上角
     *  @param blockTop 同上，纵向
     */
    private fun miniWindow(
        name: String,
        blockLeft: Float,
        blockTop: Float,
    ): ImageVector {
        val builder = vectorBuilder(name)
        builder.addStroked(
            PathData {
                moveTo(7f, 3f)
                lineTo(17f, 3f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(19f, 19f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(7f, 21f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(5f, 5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()
            },
        )
        builder.addStroked(
            PathData {
                moveTo(blockLeft, blockTop)
                lineTo(blockLeft + 2f, blockTop)
                lineTo(blockLeft + 2f, blockTop + 2f)
                lineTo(blockLeft, blockTop + 2f)
                close()
            },
        )
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
     * 「电源菜单」那一格：`menu-2` 的三条横线 + 右下角电源符号。
     *
     *  三条横线照图标库的原坐标（y = 6 / 12 / 18，x 从 4 起）；带角标时**右端在缺口圆上收笔** ——
     *  越靠下的线收得越早，三条自然错开，不会有一条横穿角标。
     */
    val PowerMenu: ImageVector by lazy { badgedIcon("PowerMenu", Badge.Power, ::menuBase) }

    /** `menu-2` 那三条横线的高度 —— 坐标照图标库那颗。 */
    private val MENU_LINES = listOf(6f, 12f, 18f)

    /**
     * 菜单底图：三条横线，右端按缺口收笔。
     *
     *  @param notch 带角标时传这一档的缺口；格子那颗传 `null`，三条都画到 x=20
     */
    private fun menuBase(notch: Notch?): List<PathNode> =
        PathData {
            MENU_LINES.forEach { y ->
                val end =
                    notch?.let { n ->
                        val dy = y - n.y
                        //  这条线落在缺口圆上那一点；离得太远（|dy| 超过半径）就画到 20
                        if (abs(dy) < n.radius) n.x - sqrt(n.radius * n.radius - dy * dy) else 20f
                    } ?: 20f
                moveTo(4f, y)
                lineTo(end, y)
            }
        }

    /**
     * 亮度底图：图标库那颗 `brightness-up` —— 内圈 r=3 + 八道短光（半径 7~9）。
     *
     *  @param notch 带角标的那三档传这一档的缺口：圆环在缺口圆上收笔、碰着缺口的光线整条不画；
     *    格子那颗传 `null`，整颗画满。
     */
    private fun sunBase(notch: Notch?): List<PathNode> =
        PathData {
            //  内圈：圆心 (12,12)、半径 3
            if (notch == null) {
                moveTo(15f, 12f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -6f, 0f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6f, 0f)
            } else {
                //  带角标：这一圈在缺口圆里那一段不画，从缺口上沿绕过左边接到下沿
                val (upper, lower) = ringCut(12f, 12f, 3f, notch)
                moveTo(upper.first, upper.second)
                arcToRelative(
                    3f,
                    3f,
                    0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    lower.first - upper.first,
                    lower.second - upper.second,
                )
            }
            //  八道短光：四条正方向、四条 45° 斜向，半径 7 到 9 —— 碰着缺口的那条整条不画
            //  （一格长的光被截掉半截没有意义，整条让位更干净）
            SUN_RAYS.forEach { (from, to) ->
                if (notch?.touches(from.first, from.second, to.first, to.second) != true) {
                    moveTo(from.first, from.second)
                    lineTo(to.first, to.second)
                }
            }
        }

    /**
     * `brightness-up` 那八道短光的两端（半径 7 → 9）—— 坐标照图标库那颗，一个没改。
     */
    private val SUN_RAYS =
        listOf(
            (12f to 5f) to (12f to 3f),
            (17f to 7f) to (18.4f to 5.6f),
            (19f to 12f) to (21f to 12f),
            (17f to 17f) to (18.4f to 18.4f),
            (12f to 19f) to (12f to 21f),
            (7f to 17f) to (5.6f to 18.4f),
            (6f to 12f) to (4f to 12f),
            (7f to 7f) to (5.6f to 5.6f),
        )

    /** 内弧（右数第二根线）的圆心 x 与半径 —— 起点 (14.8, 8.6)、走 (0, 6.8)。 */
    private const val INNER_WAVE_CENTER_X = 11.14f

    private const val INNER_WAVE_RADIUS = 5f

    /** 外弧的圆心 x 与半径 —— 起点 (17.9, 5)。 */
    private const val OUTER_WAVE_CENTER_X = 10.76f

    private const val OUTER_WAVE_RADIUS = 10f

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
