package com.lookie.opluskey.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.frosted
import com.lookie.opluskey.ui.model.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.model.opticalCenter
import dev.chrisbanes.haze.HazeState
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.SnackbarDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
import kotlinx.coroutines.delay

/**
 * 页面底部的**一条**提示 —— 常驻结论与操作回执共用一个位置，谁最近变了显示谁。
 *
 * ## 为什么不用库的 `SnackbarHost`
 *
 *  那个 host 是「一个 `LazyColumn` + 每条 item 外面套一层横向 `AnchoredDraggable`」，
 *  两件事都在它内部、外面调不了（`canSwipeToDismiss = false` 也压不住）：
 *
 *   · 横滑容器的锚点第一帧之后才量出来 ⇒ 出现那一下胶囊先右移一点、再被弹簧拉回；
 *   · 收一条时条目留在队列里画退场动画，新的一条**排在它上面** ⇒ 连续两句时第二句
 *     从更高的地方落回底边。
 *
 *  所以这里自己排：**一个 [HintSlot] 就是那一句话**，同一时刻只有一条，换内容就是换字符串
 *  （两份状态谁该露脸见 [PageSnackbars] 里那段）。没有队列就没有叠放，
 *  没有 drag 容器就没有横向漂移 —— 这两个毛病从结构上不会再出现。
 *
 * ## 为什么连库的 `Snackbar` 也不用了（改自己画胶囊）
 *
 *  要加磨砂，模糊层就得**正好落在胶囊那一块**上；而库的 `Snackbar` 把外边距写死在自己身上
 *  （`padding(horizontal = 16.dp, vertical = 4.dp)`，见其源码），它只收 `modifier`、且挂在那圈
 *  外边距**外面** ⇒ 磨砂画出来是「胶囊 + 一圈 16dp 的糊边」（[CommandToolbar] 当初踩的同一个坑：
 *  真机反馈「给工具条外面再套一个磨砂圈」）。
 *  外观那几笔（圆角 / 内边距 / 字号 / 底色）仍取库的公开常量 [SnackbarDefaults]，改库就跟着改。
 *
 * ## 落点
 *
 *  **状态栏之下、页面最顶端**，离状态栏只有胶囊外边距那 4dp（[HINT_ITEM_MARGIN]）——
 *  顶边不再留阴影画布（[HINT_TOP_ROOM] = 0）。顶部**不能照搬**原先离底边那一档
 *  （`coui_snack_bar_margin_bottom` 32dp）：那一档里 28dp 是给阴影留的画布，而阴影在**下方**、
 *  还是下移 4dp 的 —— 上方真正要留的只有 22dp 不到，且那圈是最淡的尾，被离屏层切掉看不出来。
 *  顶部照留满就变成「状态栏下面空一大块」（真机反馈三次：先「离得也太远了点」，改到 32dp 后
 *  「还是太远了」，收到 12dp 后仍要「改成 0 试一下」）。
 *  原先贴底边时，弹窗偏下、两者必然重叠 —— 提示条会把弹窗底部那排按钮压住；挪到顶部之后
 *  互不打扰，「弹窗开着时的提示」照样看得见。
 *
 *  这笔账以前是「槽上留 24dp + 画布下边 4dp」—— 画布下边只有 4dp 时，**入场动画那一下**
 *  阴影会被画布下沿切平（`fadeIn` 期间节点走离屏层，层边界就是节点边界），动画播完、层撤掉
 *  才恢复（用户口径「入场动画时底部阴影被截断，播完就好了」）。现在把槽上那 24dp 并进画布，
 *  胶囊的位置一个像素没动，四边的画布都是 28dp。
 *
 * ## 挂在浮层槽、压在弹窗之上
 *
 *  这一槽是 `popupHost`（不是在 `snackbarHost`），理由见 `PageContent`：库把 `popup`
 *  放在最后 `place()`，用提示槽的话弹窗一开提示就整个被盖住。
 */
@Composable
internal fun SettingsPage.PageSnackbars() {
    // 磨砂取样源：栈顶页的内容层（与顶栏 / 浮层菜单 / 底部工具栏同一个）。本组件画在 Scaffold
    //  的浮层槽里、在页面那层**之外**，所以不会落进自己取样的那一层（落了就是 RenderThread 栈溢出）。
    val backdrop = chrome.pageHazeState
    // 底部**只有一个位置**：两份状态（常驻结论 / 操作回执）里**谁最近变了就显示谁**。
    //
    //  历史：「回执在场时常驻那条整个让位」—— 这条规则治好了「两层胶囊叠在底部」（用户反馈过三次），
    //  但带出两个新毛病（用户口径：「点振动反馈再点主开关，主开关的提示条会等振动反馈的消息条
    //  消失再显示，而且是从靠上的位置瞬回的」）：
    //   · 主开关刚变（「现在能不能用」已经变了）却要**等**回执两秒走完才敢露脸；
    //   · 露脸那一刻回执还在画退场动画、仍占着一行高，于是它**先出现在靠上的位置**，等退场
    //     结束才瞬回底边。
    //
    //  现在按 [HintState.stamp] 比「谁的话更新」（不是比谁非空 —— 主开关变的时候回执照样非空）：
    //   · 回执刚来 → 它在上面，常驻那条让位；
    //   · 主开关 / 运行状态刚变 → 常驻结论**立刻**顶掉还在计时的回执（用户要的就是「立即生效」）；
    //   · 回执到点自己走 → 落到常驻那条。
    //  三种切换都发生在**同一个 [HintSlot] 节点**里（换的只是字符串），所以不会再有任何位移：
    //  没有第二个节点占位，也就没有「从上面瞬回」。
    val action = hints.actionHint
    val status = hints.statusHint
    val text =
        when {
            action.text == null -> status.text

            status.text == null -> action.text

            //  号是全局发的，平手只可能是「两边都还没说过话」（上面两条已经拦掉），这里偏回执无害
            action.stamp >= status.stamp -> action.text

            else -> status.text
        }
    //  这里**不留额外间距**：那一笔已经并进阴影画布（见 [HINT_SHADOW_ROOM]）——
    //  离顶栏多远由画布 + 胶囊外边距一起决定，画布够大才不会在入场动画期间切掉阴影。
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        HintSlot(text, backdrop)
    }
}

/**
 * 一行提示：**淡入 + 缩放并行**地出现 / 收走；换句时在文字淡出后的窗口里平滑过渡宽度。
 *
 *  显 / 隐由 [AnimatedVisibility] 管（整行出现 / 收走那一下）；换句是文字淡出 → 胶囊过渡宽度 →
 *  文字淡入（三段时长见 [HintCapsule]），避免文字比宽度先变导致的字幅溢出。
 *  （换句**不能**套 `AnimatedContent` / `Crossfade`，那条理由见 [HintCapsule]。）
 *
 * @param text 这一行此刻要显示的话；`null` = 这一行不收（走退场动画）
 */
@Composable
private fun HintSlot(
    text: String?,
    backdrop: HazeState?,
) {
    // 退场那一两百毫秒里 `text` 已经是 null，但还要接着画上一次的内容 —— 否则会先闪空再消失。
    //  初值取当前文本：这一槽里换的只是字符串（两份状态谁露脸见 [PageSnackbars]），
    //  `AnimatedVisibility` 收起后重新开出时节点是新建的，第一帧就该是那句话，不该先空一帧。
    var last by remember { mutableStateOf(text.orEmpty()) }
    if (text != null && text != last) last = text

    AnimatedVisibility(
        visible = text != null,
        enter = HINT_ENTER,
        exit = HINT_EXIT,
    ) {
        // 三边留满阴影画布（阴影主要落在下 / 左右），**顶边收窄**：提示条在页面最顶端，
        //  上方留满 28dp 就是「状态栏下面空一大块」（见文件头「落点」那段）。
        Box(
            Modifier.padding(
                start = HINT_SHADOW_ROOM,
                end = HINT_SHADOW_ROOM,
                top = HINT_TOP_ROOM,
                bottom = HINT_SHADOW_ROOM,
            ),
        ) {
            HintCapsule(last, backdrop)
        }
    }
}

/**
 * 一枚提示胶囊 —— 底色 + 磨砂 + 圆角 + 阴影 + 文字，几何照抄库的 `Snackbar`。
 *
 *  修饰符顺序就是绘制顺序，四处顺序都不能换：
 *   · 外圈 [HINT_SIDE_MARGIN] / [HINT_ITEM_MARGIN] **必须在磨砂前面**，否则糊的范围会带上一圈外边距；
 *   · 阴影也必须在磨砂**前面** —— [frosted] 链上有个 `clip(胶囊)`，挂在它里面的阴影会被裁光
 *    （真机反馈过「工具条阴影怎么没了」）；
 *   · 底色在磨砂**后面**（盖在糊上）：底色要半透明才透得出糊，见 [frostedContainer] 那段注释；
 *   · `clip` 在底色**后面**、[animateContentSize] **前面**：它看到的是动画中的尺寸，
 *    文字被裁在正在变宽的胶囊边上。
 *
 * ## 换句：宽度过渡 + 文字在**过渡期间不可见**
 *
 *  只上 `animateContentSize` 会露馅：它动的是节点报给外层的尺寸，而文字是**立刻**按新内容
 *  排的 —— 换句变长时胶囊还在往新宽度长，文字已经占满目标宽度，那几十毫秒挂在胶囊外
 *  （用户口径「文字先变化，在动画未完成前文字会溢出」）。
 *  反过来直接去掉动画（同帧就位）又丢了过渡（用户口径「提示条的宽度过渡没了」）。
 *
 *  现在的顺序是**三段**，宽度动画在文字不可见的那一段里跑完：
 *   1. 文字淡出（[HINT_TEXT_FADE_MS]）；
 *   2. 换内容 —— 这一刻文字 alpha 已经是 0，胶囊开始把宽度过渡到新尺寸（[HINT_RESIZE_MS]）；
 *   3. 等过渡结束，文字淡入。
 *  于是「文字与宽度不同步」这件事仍然存在，但发生在一个看不见的窗口里。
 *
 * ## 为什么换句不走 `AnimatedContent`
 *
 *  试过（`AnimatedContent` + `SizeTransform(clip = true)`），真机上两个毛病都出在它身上：
 *   · 那个 `clip` 把内容裁到**容器框**里，而容器框贴的就是胶囊边 ⇒ 阴影四周被切平；
 *   · 交叉淡化是给整棵子树套一层 alpha，磨砂那层也在里面 ⇒ 换句这一百多毫秒里糊消失、
 *    半透明的底色直接透出底下的内容（真机反馈「模糊会不见透出底部区域」）。
 *  现在只给**文字**那一层套 alpha（`graphicsLayer`），磨砂 / 底色 / 描边全程不动。
 */
@Composable
private fun HintCapsule(
    message: String,
    backdrop: HazeState?,
) {
    val shape = remember { SquircleShape(HINT_CAPSULE_RADIUS) }
    val colors = SnackbarDefaults.snackbarColors()
    // 取样层没就绪（建页期那几帧）就不糊，底色退回不透明的那一档
    val container = if (backdrop == null) colors.containerColor else frostedContainer(colors.containerColor)
    // 换句三段：淡出 → 换内容（宽度在这一段过渡）→ 淡入（见上面那段注释）
    var shownText by remember { mutableStateOf(message) }
    val textAlpha = remember { Animatable(1f) }
    LaunchedEffect(message) {
        if (message == shownText) return@LaunchedEffect
        textAlpha.animateTo(0f, tween(HINT_TEXT_FADE_MS, easing = AccelerateEasing()))
        shownText = message
        //  只等到宽度过渡的**尾巴**：淡入与最后那几十毫秒重叠，否则「等都等完了文字才出来」
        //  会有一拍发条感。这时宽度已经走到九成，配上刚开始涨的 alpha，看不出不同步。
        delay((HINT_RESIZE_MS - HINT_TEXT_FADE_MS).toLong())
        textAlpha.animateTo(1f, tween(HINT_TEXT_FADE_MS, easing = DecelerateEasing()))
    }
    Box(
        modifier =
            Modifier
                .padding(horizontal = HINT_SIDE_MARGIN, vertical = HINT_ITEM_MARGIN)
                .dropShadow(shape = shape, shadow = HINT_SHADOW)
                .then(backdrop?.let { Modifier.frosted(it, shape) } ?: Modifier)
                .background(container, shape)
                //  一圈细描边：磨砂底与底下内容撞色时边界会糊掉（暗色下尤其），描一道把边收住。
                //  挂在 `background` **之后**（画在底色上）、`clip` **之前**：描边居中于节点边界，
                //  先 clip 会把外半圈裁掉、视觉只剩半宽。
                .border(HINT_BORDER_WIDTH, colors.contentColor.copy(alpha = HINT_BORDER_ALPHA), shape)
                //  `clip` 挂在 [animateContentSize] **外面**：它看到的是动画中的尺寸，文字才被裁在
                //  正在变宽的胶囊边上（放在里面看到的是目标尺寸，等于没裁）。
                .clip(shape)
                // 换句时宽度（换行时连高度）跟着过渡 —— 从中间往两边长
                .animateContentSize(
                    animationSpec = tween(HINT_RESIZE_MS, easing = DecelerateEasing()),
                    alignment = Alignment.Center,
                ).padding(SnackbarDefaults.InsideMargin)
                // 加高这一档：squircle 的半径上限是 `min(宽, 高) / 2`，提示条只有一行字时
                //  那一半把半径钳死了，角看上去发方（用户反馈「圆角变小了」）。上下各加 2dp
                //  让上限抬 2dp —— 换来的圆角，代价是胶囊胖一点。
                .padding(vertical = HINT_TALLER_BY)
                //  这一层**没有任何手势**：胶囊压在内容上，点在它身上要**穿透**到底下那一行
                //  （用户口径「snackbar 改成点击穿透，点它时它下面的东西响应」）。
                //  以前这里挂 `detectTapGestures {}` 把点击吃掉（怕点提示时误点到下面那一行），
                //  但那正好反了：提示只是回执，用户真要点的往往就是它盖住的那一行。
                .semantics { liveRegion = LiveRegionMode.Polite },
        // 文字**垂直居中**：`Box` 默认 `TopStart`，胶囊比一行字高时文字会顶在上沿
        //  （用户反馈「文字没有垂直居中」）。水平方向本来就贴着文字宽度，居中与否看不出。
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shownText,
            color = colors.contentColor,
            // 库 `couiTextAppearanceButton`：14sp Medium —— 与真机上的提示同一档字号
            style = SnackbarDefaults.textStyle(),
            // 只动文字这一层的 alpha：磨砂 / 底色 / 描边不跟着淡，换句时糊不会消失。
            modifier =
                Modifier
                    // 胶囊高度是设计值，字在里头竖居中 —— 补一档光学修正（见 `opticalCenter`）
                    .opticalCenter(SnackbarDefaults.textStyle())
                    .graphicsLayer { alpha = textAlpha.value },
        )
    }
}

/**
 * 超椭圆（squircle）形状 —— 提示胶囊那一圈圆角用它画。
 *
 * ## 为什么不用 `RoundedCornerShape`
 *
 *  COUI 的圆角全是 squircle（卡片 / 按钮 / 弹窗 / 顶栏都用它），一个正圆角的胶囊混进去，
 *  四角会显得「比周围更尖」—— 提示条正好压在卡片与工具栏上，对比最明显。
 *
 * ## 为什么自己实现 `Shape` 而不是直接用 `Modifier.squircle*`
 *
 *  库给的是三支 `Modifier`（`squircleBackground` / `squircleClip` / `squircleBorder`），
 *  而这个胶囊需要**同一个形状**贯穿五处：阴影（`dropShadow`）、磨砂离屏（[frosted]）、
 *  底色、描边、裁内容 —— 前两者只收 `Shape`。所以这里把库的 `addSquircleRect` 包一层，
 *  形状定义仍然只有库那一份，不自己写贝塞尔。
 *
 * @param cornerRadius 圆角半径（与库 `SnackbarDefaults.SingleLineCornerRadius` 同一档）
 */
private class SquircleShape(
    private val cornerRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path =
            Path().apply {
                addSquircleRect(
                    width = size.width,
                    height = size.height,
                    cornerRadius = with(density) { cornerRadius.toPx() },
                )
            }
        return Outline.Generic(path)
    }
}

/**
 * 磨砂胶囊的底色 —— **按比例缩** alpha，不能整个 `copy(alpha = …)` 换掉。
 *
 *  库的深色板里有些面是**半透白**（如 `surfaceContainer` 的 `0x1AFFFFFF`），整个换 alpha 会把
 *  半透白变成「白 67%」的亮块（真机在底部工具栏上踩过「暗色下还是亮色胶囊」）。
 */
private fun frostedContainer(base: Color): Color = base.copy(alpha = base.alpha * FROSTED_SURFACE_ALPHA)

/**
 * 提示胶囊的描边 —— 1dp 发丝线，颜色是**提示文字色**的一档淡透明度。
 *
 *  为什么需要：胶囊是半透明磨砂底，落在同色的内容上时边界会糊掉（暗色下最明显，
 *  看着像「一块模糊的空气」）。描一圈就把它从背景里拎出来。
 *
 *  取 [SnackbarDefaults] 那份 `contentColor` 而不是另找一个主题色：深浅两套主题下它都正好是
 *  「比底色明显、又不抢文字」的那一支，不必再对一套颜色。
 */
private val HINT_BORDER_WIDTH = 1.dp

/** 描边的不透明度 —— 就 12%：看得见边、不显眼（一开始用分隔线色，真机反馈「太亮了」）。 */
private const val HINT_BORDER_ALPHA = 0.12f

/**
 * 胶囊上下各多留的一档 —— 为的是把 squircle 的半径上限抬起来。
 *
 *  `addSquircleRect` 会把半径钳到 `min(宽, 高) / 2`：提示条只有一行字时，高度那一半
 *  （约 18dp）比 `SnackbarDefaults.SingleLineCornerRadius`（24dp × 1.28 = 30.8dp）小，
 *  于是半径被压到 18dp，超椭圆看上去比同半径的圆弧「更方」（用户反馈「圆角变小了」）。
 *  上下各 2dp 把这个上限抬到 20dp，角就能撑大一点；代价是胶囊略胖。
 */
private val HINT_TALLER_BY = 2.dp

/**
 * 胶囊外面那圈**给阴影留的画布**（左 / 右 / 下）—— 28dp = 阴影半径 26 + 下移 4。
 *
 *  为什么需要它：进出场用 alpha（`fadeIn` / `fadeOut`）时，Compose 会给节点开一层会话层
 *  （`saveLayer`），**会话层的边界就是节点边界** —— 而阴影画在胶囊外面，不留出这一圈就会
 *  被四周裁平。取 28dp：阴影半径 26 + 下移 4。
 *
 *  底部以前只留 4dp（「那圈阴影本来就该被窗口沿切掉」），但离屏层的边界在**动画期间**就生效，
 *  于是入场那 150ms 里底部阴影是齐边切平的、播完才恢复。现在底边留满。
 *
 *  **顶边不用这一档**，见 [HINT_TOP_ROOM]。
 */
private val HINT_SHADOW_ROOM = 28.dp

/**
 * 顶边那圈画布的宽度 —— **0dp**：贴到最上面，离状态栏只剩 [HINT_ITEM_MARGIN] 的 4dp。
 *
 *  阴影是**下移 4dp** 的：上方实际溢出只有 `26 - 4 = 22dp`，而且那一段是整圈里最淡的尾
 *  （14% 的黑再乘径向渐变到尾端），被离屏层切掉看不出来 —— 拿它换「贴近状态栏」很值。
 *
 *  顶部照留满 28dp（原先离底边那一档）浮现出来就是「状态栏下面空一大块」（真机反馈两次：
 *  先「离得也太远了点」，改到 32dp 后仍是「还是太远了」）；12dp 仍嫌远，收到 0。
 */
private val HINT_TOP_ROOM = 0.dp

/** 胶囊左右两侧的外边距（库 `Snackbar` 内联的那 16dp，COUI `grid_guide_column_card_margin_start`）。 */
private val HINT_SIDE_MARGIN = 16.dp

/** 胶囊上下两侧的外边距（库 `Snackbar` 内联的那 4dp）—— 与 [HINT_SHADOW_ROOM] 合起来才是 32dp。 */
private val HINT_ITEM_MARGIN = 4.dp

/**
 * 胶囊圆角 —— 取库的**单行**那一档（24dp，`couiRoundCornerXXL`）。
 *
 *  库会按行数在 16dp / 24dp 之间切；这里两行都当胶囊看：提示基本都是一行，
 *  真换行时保持同一颗圆角的观感也比中途变一次圆角稳。
 */
private val HINT_CAPSULE_RADIUS = SnackbarDefaults.SingleLineCornerRadius

/** 胶囊的阴影 —— 库 `Snackbar` 那笔 COUI ShadowUtils level 3（半径 26dp / 黑 14% / 下移 4dp）。 */
private val HINT_SHADOW: Shadow =
    Shadow(
        radius = 26.dp,
        color = Color.Black,
        alpha = 0.14f,
        offset = DpOffset(x = 0.dp, y = 4.dp),
    )

/** 进出场那几条规格 —— **淡入淡出与缩放并行**（COUI 那个组合：0.8 缩放 + 淡入）。 */
private const val HINT_ENTER_MS = 150

private const val HINT_EXIT_MS = 130

/** 缩放从 / 到 0.9：与淡出一起来时，幅度不必像纯缩放那样夸张。 */
private const val HINT_ENTER_SCALE = 0.9f

/** 进出场：缩放 + 淡入淡出（两条并行，同一条 `tween`；缓动取 COUI 的减速 / 加速两档）。 */
private val HINT_ENTER: EnterTransition =
    fadeIn(tween(HINT_ENTER_MS, easing = DecelerateEasing())) +
        scaleIn(tween(HINT_ENTER_MS, easing = DecelerateEasing()), initialScale = HINT_ENTER_SCALE)

private val HINT_EXIT: ExitTransition =
    fadeOut(tween(HINT_EXIT_MS, easing = AccelerateEasing())) +
        scaleOut(tween(HINT_EXIT_MS, easing = AccelerateEasing()), targetScale = HINT_ENTER_SCALE)

/**
 * 换句时胶囊宽度过渡的时长 —— 这一段**文字是不可见的**（见 [HintCapsule] 的「换句三段」），
 * 于是宽度动画不会带着半截文字一起动。
 *
 *  120ms：与进出场那两条（150 / 130）同一档。原来取 180，加上前后两段淡入淡出要 300ms 出头，
 *  真机上换句显得慢半拍（用户口径「文字动画太慢了」）。
 */
private const val HINT_RESIZE_MS = 120

/** 换句时文字淡出 / 淡入各自的时长 —— 只是「别硬切」，不需要看出渐变，所以给得很短。 */
private const val HINT_TEXT_FADE_MS = 35
