package com.lookie.opluskey.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.frosted
import com.lookie.opluskey.ui.model.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.state.HintState
import dev.chrisbanes.haze.HazeState
import io.github.suqi8.coui.kmp.basic.SnackbarDefaults
import io.github.suqi8.coui.kmp.basic.Text

/**
 * 页面底部的**两行**提示 —— 上常驻、下操作。
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
 *  所以这里自己排：**一个 [HintState] 就是一行**，同一时刻只有一句话，换内容就是换字符串。
 *  没有队列就没有叠放，没有 drag 容器就没有横向漂移 —— 这两个毛病从结构上不会再出现。
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
 *  距屏幕底边 32dp —— 与 COUI 的 `coui_snack_bar_margin_bottom` 一致：
 *  这一页的 [HINT_BOTTOM_GAP]（24dp）+ 阴影画布的下边（[HINT_ROOM_BOTTOM] 4dp）+
 *  胶囊自己那圈外边距（[HINT_ITEM_MARGIN] 4dp）。
 *  页面是边到边（`contentWindowInsets` 全零），底边由 [HINT_INSETS] 自己补上导航条，
 *  所以这个数就是到**可视底边**的距离。
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = HINT_BOTTOM_GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HINT_GAP),
    ) {
        // 常驻结论在上：它说的是「整个模块能不能用」，压在操作回执上面更合读的顺序
        HintSlot(link.statusHint, backdrop)
        // 操作回执在下：贴着屏幕底边，就是用户刚动手的那一格附近
        HintSlot(link.actionHint, backdrop)
    }
}

/**
 * 一行提示：**淡入 + 缩放并行**地出现 / 收走；**在内容之间**换句时胶囊自己变宽。
 *
 *  两层动画是分开的：显 / 隐由 [AnimatedVisibility] 管（整行出现 / 收走那一下），
 *  换句由 [HintCapsule] 里的 `animateContentSize` 管 —— 后者动的是胶囊自己的尺寸，
 *  于是「已保存」→「已删除「双击」」这种长短变化是宽度滑过去，而不是一帧跳过去。
 *  （换句**不能**套 `AnimatedContent` / `Crossfade`，那条理由见 [HintCapsule]。）
 */
@Composable
private fun HintSlot(
    state: HintState,
    backdrop: HazeState?,
) {
    val text = state.text
    // 退场那一两百毫秒里 `text` 已经是 null，但还要接着画上一次的内容 —— 否则会先闪空再消失
    var last by remember { mutableStateOf("") }
    if (text != null && text != last) last = text

    AnimatedVisibility(
        visible = text != null,
        enter = HINT_ENTER,
        exit = HINT_EXIT,
    ) {
        // 这圈留白是**给阴影的画布**，不是排版留白（见 [HINT_SHADOW_ROOM]）：
        //  alpha 那一层（`saveLayer`）的边界就是节点边界，而阴影画在胶囊外面 —— 不留出来就会被裁平。
        Box(
            Modifier.padding(
                start = HINT_SHADOW_ROOM,
                end = HINT_SHADOW_ROOM,
                top = HINT_SHADOW_ROOM,
                bottom = HINT_ROOM_BOTTOM,
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
 *   · `clip` 在底色**后面**、[animateContentSize] **前面**：它看到的是动画中的尺寸，文字才被裁在
 *    正在变宽的胶囊边上（放在里面等于裁到目标尺寸，等于没裁）；
 *   · [animateContentSize] 在最后：它动的是这一颗节点报给外层的尺寸，外面那几层
 *    （阴影 / 糊 / 底色 / 裁剪）都按这个尺寸画 ⇒ **一颗**胶囊变宽，而不是两颗交叉淡化。
 *
 * ## 为什么换句不走 `AnimatedContent`
 *
 *  试过（`AnimatedContent` + `SizeTransform(clip = true)`），真机上两个毛病都出在它身上：
 *   · 那个 `clip` 把内容裁到**容器框**里，而容器框贴的就是胶囊边 ⇒ 阴影四周被切平；
 *   · 交叉淡化是给整棵子树套一层 alpha，磨砂那层也在里面 ⇒ 换句这一百多毫秒里糊消失、
 *    半透明的底色直接透出底下的内容（真机反馈「模糊会不见透出底部区域」）。
 *  现在文字换得干脆，宽度由 [animateContentSize] 从中间往两边长 —— 全程一层、一个 alpha。
 */
@Composable
private fun HintCapsule(
    message: String,
    backdrop: HazeState?,
) {
    val shape = remember { RoundedCornerShape(HINT_CAPSULE_RADIUS) }
    val colors = SnackbarDefaults.snackbarColors()
    // 取样层没就绪（建页期那几帧）就不糊，底色退回不透明的那一档
    val container = if (backdrop == null) colors.containerColor else frostedContainer(colors.containerColor)
    Box(
        Modifier
            .padding(horizontal = HINT_SIDE_MARGIN, vertical = HINT_ITEM_MARGIN)
            .dropShadow(shape = shape, shadow = HINT_SHADOW)
            .then(backdrop?.let { Modifier.frosted(it, shape) } ?: Modifier)
            .background(container, shape)
            // `clip` 必须挂在 [animateContentSize] **外面**：它裁的是「它自己那一层看到的尺寸」，
            //  放在里面的话看到的是文字的**目标**尺寸（等于内容本身）⇒ 裁了个寂寞，
            //  长出来的那几帧字会挂在胶囊外面。挂在外面看到的是**动画中**的尺寸，才是那颗胶囊。
            .clip(shape)
            // 换句时宽度（换行时连高度）跟着动画 —— 从中间往两边长
            .animateContentSize(animationSpec = tween(HINT_RESIZE_MS), alignment = Alignment.Center)
            .padding(SnackbarDefaults.InsideMargin)
            // 提示压在内容上：吃掉落在它身上的点击，免得穿透去点到底下那一行
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = message,
            color = colors.contentColor,
            // 库 `couiTextAppearanceButton`：14sp Medium —— 与真机上的提示同一档字号
            style = SnackbarDefaults.textStyle(),
        )
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
 * 提示条那一槽要让开的系统条 —— **只有导航条**（底部）。
 *
 *  为什么需要它：提示条原先挂在 `snackbarHost` 槽，那个槽是库自己按 `contentWindowInsets`
 *  让过位的；现在改挂 `popupHost` 槽（为了压在弹窗之上，见 `PageContent`），库就不再管它了 ——
 *  而这一页的 `contentWindowInsets` 是全零（边到边），于是提示条会沉进手势导航条下面那一条里。
 *  [HINT_BOTTOM_GAP] 那 24dp 是按「槽底 = 可视底边」算的（合计 32dp），少了这条 inset 就整体下移。
 *
 *  只要导航条、不要状态栏：这一槽是底部对齐的，顶部那条对落点没有意义。
 *  （`WindowInsets.navigationBars` 是 `@Composable` getter —— 只能在组合里取，不能存成顶层 val。）
 */
@Composable
internal fun hintInsets(): WindowInsets = WindowInsets.navigationBars

/**
 * 提示距屏幕底边的距离 —— 与 COUI 的 `coui_snack_bar_margin_bottom` 一致（32dp）。
 *
 *  这一笔是**剩下的**那一截：这一页的 24dp + 阴影画布 [HINT_ROOM_BOTTOM] 的 4dp +
 *  胶囊自己那圈外边距（[HINT_ITEM_MARGIN]）4dp = 32dp。
 *  三处任改一处，都要回头把这个总数对回 32dp。
 */
private val HINT_BOTTOM_GAP = 24.dp

/**
 * 胶囊外面那圈**给阴影留的画布**（左右与上）。
 *
 *  为什么需要它：进出场用 alpha（`fadeIn` / `fadeOut`）时，Compose 会给节点开一层会话层
 *  （`saveLayer`），**会话层的边界就是节点边界** —— 而阴影画在胶囊外面，不留出这一圈就会
 *  被四周裁平（真机反馈「打开过程中四周阴影还是被裁切」）。取 28dp：阴影半径 26 + 下移 4。
 *
 *  注意它是**布局留白**，会把胶囊顶上去：所以 [HINT_BOTTOM_GAP] 只剩 24dp，两行之间的
 *  [HINT_GAP] 也归零（各自那圈留白已经把它们隔开了）。
 */
private val HINT_SHADOW_ROOM = 28.dp

/**
 * 画布在**下**边只留 4dp。
 *
 *  底部那圈阴影本来就该被窗口沿切掉 —— COUI 自己也是这样（真机口径「COUI 的只有底部」）。
 *  多留 24dp 会把胶囊顶得离底边太远，得不偿失。
 */
private val HINT_ROOM_BOTTOM = 4.dp

/** 胶囊左右两侧的外边距（库 `Snackbar` 内联的那 16dp，COUI `grid_guide_column_card_margin_start`）。 */
private val HINT_SIDE_MARGIN = 16.dp

/** 胶囊上下两侧的外边距（库 `Snackbar` 内联的那 4dp）—— 与 [HINT_BOTTOM_GAP] 合起来才是 32dp。 */
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

/**
 * 两行提示之间（常驻那条与操作回执同时在场时）的**额外**间距 —— 归零。
 *
 *  两行各自带着上下各 28/4dp 的阴影画布（[HINT_SHADOW_ROOM] / [HINT_ROOM_BOTTOM]），
 *  那圈留白已经把它们隔开了（实际视觉间距 ≈ 32dp）；再加 8dp 会显得两张提示离得太远。
 */
private val HINT_GAP = 0.dp

/** 进出场那几条规格 —— **淡入淡出与缩放并行**（COUI 那个组合：0.8 缩放 + 淡入）。 */
private const val HINT_ENTER_MS = 150

private const val HINT_EXIT_MS = 130

/** 缩放从 / 到 0.9：与淡出一起来时，幅度不必像纯缩放那样夸张。 */
private const val HINT_ENTER_SCALE = 0.9f

/** 进出场：缩放 + 淡入淡出（两条并行，同一条 `tween`）。 */
private val HINT_ENTER: EnterTransition =
    fadeIn(tween(HINT_ENTER_MS)) + scaleIn(tween(HINT_ENTER_MS), initialScale = HINT_ENTER_SCALE)

private val HINT_EXIT: ExitTransition =
    fadeOut(tween(HINT_EXIT_MS)) + scaleOut(tween(HINT_EXIT_MS), targetScale = HINT_ENTER_SCALE)

/** 换句那一下的宽度动画时长。 */
private const val HINT_RESIZE_MS = 180
