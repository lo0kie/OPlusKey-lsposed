package com.lookie.opluskey.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.HINT_SLOT_CHECKING
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.frosted
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.CHECKING_HINT
import com.lookie.opluskey.ui.model.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.model.HOST_ALIVE_TIMEOUT_MS
import com.lookie.opluskey.ui.model.QUERY_ROUNDS
import com.lookie.opluskey.ui.model.opticalCenter
import com.lookie.opluskey.ui.noticeReason
import com.lookie.opluskey.ui.retireHint
import com.lookie.opluskey.ui.state.HintItem
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.SnackbarDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 页面顶端那一叠提示条 —— **后来的在最上面**，最多三条（见 `HintQueue`）。
 *
 * ## 三条规矩
 *
 *  1. **入场**：这一条自己从上方 16dp 滑下来，同时 0.92 → 1 放大。位移是相对**它自己**的
 *     （`graphicsLayer.translationY`），与容器无关 —— 容器多高都不会改变它滑动的距离。
 *  2. **出场**：两个口，都 180ms。**到点**的原路往**上** 16dp 收回去（与入场互逆）；
 *     **被挤出队列**的（超出三条）往**下** 56dp 滑走 —— 它是被后来的那条顶下去的
 *     （见 `HintQueue.enqueue`）。两者都缩到 0.92 并淡出，播完才从队列摘掉。
 *  3. **让位**：新的一条插进来时，**旧的往下让**。这里没有 FLIP 补偿 —— 位移是**被新条目
 *     报出来的高度挤出来的**（见 [HintSlot] 里那个 `Modifier.layout`）。
 *
 * ## 为什么是 `Column` 而不是自己 `Layout`
 *
 *  这一叠不需要错落排布，`Column` 就够了；重点是它**不裁剪、也不拦触摸** —— 整叠只占内容高度，
 *  页面照常可点。（`LazyColumn` + `animateItem` 是官方的位置动画，但它必须有界高度、放进浮层
 *  就得铺满整屏，实测**整页点不动**，所以用不了。）
 *
 * @param statusBarTop 状态栏高度，由 `PageContent` 在页面级读好传进来 —— **不能在 `popupHost`
 *   这一层自己读**：那是 `SubcomposeLayout` 另起的子组合，`statusBarsPadding()` 在这里恒为 0
 *   （真机：提示条直接盖住了状态栏的时间与电量）。
 */
@Composable
internal fun SettingsPage.PageSnackbars(statusBarTop: Dp) {
    // 磨砂取样源：栈顶页的内容层（与顶栏 / 浮层菜单 / 底部工具栏同一个）。本组件画在 Scaffold
    //  的浮层槽里、在页面那层**之外**，所以不会落进自己取样的那一层（落了就是 RenderThread 栈溢出）。
    val backdrop = chrome.pageHazeState
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                // 画布要**盖住状态栏那一条**（阴影往上要溢 22dp），所以把整叠上提到
                // 「状态栏高度 − 画布」的位置：画布从状态栏里开始，胶囊正好落在状态栏下 4dp
                .padding(top = (statusBarTop - HINT_SHADOW_CAP).coerceAtLeast(0.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val items = hints.queue.items
        items.forEachIndexed { index, item ->
            // 按**条目身份**做 key：插入一条时只有新节点播入场，其余几条只做让位
            key(item.id) {
                HintSlot(
                    item = item,
                    backdrop = backdrop,
                    //  **后来的压在上面**（`Column` 的绘制顺序是「后面的盖前面的」，正好反着）。
                    //  位置在最上面不代表层级最高：入场那一下新旧会短暂重叠，新的必须盖住旧的。
                    modifier = Modifier.zIndex((items.size - index).toFloat()),
                )
            }
        }
    }
}

/**
 * 「能用，只是这次没落到新代码上」那一类**一次性提醒**（系统侧 / 宿主侧跑着旧包，见 [noticeReason]）。
 *
 *  这一类**一次性提醒**（系统侧 / 宿主侧跑着旧包，见 [noticeReason]）：页面照常能改，说一遍就走
 *  —— 走操作回执那条（`hints.hint`，几秒自己消失）。用户口径：「除了模块无法正常运行
 *  时的提示，其他的不要常驻，比如系统侧还是旧版这种，提示一下就好」。
 *
 *  **也在页面级**：它弹的是同一条提示条，不该有第二个宿主、也不该挑页显示 —— 原先挂在首页时
 *  只认 `screen == SHORTCUTS`，冷启动后马上点进子页这条提醒就被吞了，而它说的「这一版没落到
 *  新代码上」与用户在哪一页毫无关系（用户口径「不要留在首页，提示条全局单例，不要有第二个」）。
 *
 *  「整趟进程只说一遍」不靠页面，靠 `HostLinkState.lastNoticeShown`（进程级）：来回切页不会重复，
 *  提醒自己消失（重启完 / 换包后恢复）时清零，下次再出现照说。
 */
@Composable
internal fun SettingsPage.PageNoticeHint() {
    val notice = noticeReason()
    LaunchedEffect(notice) {
        if (notice == null) {
            //  只在**自检跑完**之后才清零：自检没跑完（`injectChecked` 还是 false）时这一段
            //  `null` 是「还没问出来」，不是「提醒消失了」—— 顺手清零，等于每换一次语言、
            //  每重跑一次自检都把「说过了」抹掉，同一条提醒再弹一遍。
            if (link.injectChecked) link.lastNoticeShown = null
            return@LaunchedEffect
        }
        // 同一条整趟只弹一次（`lastNoticeShown` 是进程级的那一位）。**比身份不比文案** ——
        //  比文案的话切一次语言就不认得了（见 [LinkNotice]）。
        if (link.lastNoticeShown == notice) return@LaunchedEffect
        link.lastNoticeShown = notice
        val text = notice.text()
        XLog.w("页面提醒：$text")
        //  先让「正在检查模块…」那条退场，再上结论：同一帧顶上来时两条会叠在一起，
        //  屏幕上看到的只是「一条在换成另一句」，结论那 2 秒很容易被当成「检查还在走」。
        //  错开一档退场时长，读起来才是「检查完了 → 结论」。
        retireHint(HINT_SLOT_CHECKING)
        delay(HINT_HANDOFF_MS)
        hint(text, holdMs = NOTICE_HOLD_MS)
    }
}

/**
 * 自检还没跑完时那一条**一次性**提示 —— 「正在检查模块…」。
 *
 *  与已删掉的那条「常驻结论」不是一回事：那条是**一直挂着**的状态说明（用户要求整条删除），
 *  这一条只是冷启动/刚进页面那几百毫秒的告知，走队列、两秒自己走。
 *
 *  判据是 `link.injectChecked`（自检跑完没有）。只弹一次：`LaunchedEffect(Unit)` 在页面
 *  首次组合时跑一遍，之后自检完成与否都不再打扰。
 */
@Composable
internal fun SettingsPage.PageCheckingHint() {
    LaunchedEffect(Unit) {
        //  自检已经跑完（热启动、或从子页回来）就不说这句
        if (link.injectChecked) return@LaunchedEffect
        //  **不按固定秒数走**：这条的意思是「还在问」，自检第一轮就要等满
        //  [HOST_ALIVE_TIMEOUT_MS]（3 秒）才敢下结论 —— 用默认那 2.2 秒，它会在结论出来
        //  之前先自己消失，中间空出一段「什么都没有」的空档，看着像「检查完了、什么也没说」。
        //  所以给一个足够长的兜底，真正的收场在下面那条 effect。
        hint(CHECKING_HINT, slot = HINT_SLOT_CHECKING, holdMs = CHECKING_HINT_FALLBACK_MS)
    }
    LaunchedEffect(link.injectChecked) {
        //  自检一跑完就把这条收掉（播退场），「系统侧 / 宿主侧还是旧版」那条紧接着顶上来。
        if (link.injectChecked) retireHint(HINT_SLOT_CHECKING)
    }
}

/**
 * 「正在检查模块…」那条的**兜底**停留时长 —— 只是防它赖着不走：正常路径是自检一结束就收。
 *
 *  按自检最长那一趟给（[QUERY_ROUNDS] 轮 × [HOST_ALIVE_TIMEOUT_MS]）再留 1 秒余量。
 */
private const val CHECKING_HINT_FALLBACK_MS = QUERY_ROUNDS * HOST_ALIVE_TIMEOUT_MS + 1000L

/** 「检查中 → 结论」之间那一档停顿 —— 等于提示条退场的那 220ms 再留一点余量。 */
private const val HINT_HANDOFF_MS = 260L

/**
 * 「系统侧 / 宿主侧还是旧版」那条的停留时长 —— 比默认那 2.2 秒长。
 *
 *  默认那一档是给「已保存 / 已删除」这种一眼就完的回执用的；这一条要用户**去做一件事**
 *  （重启手机 / 重启服务），而且它前面刚走完一条「正在检查」，读的时间本来就被压掉一截。
 */
private const val NOTICE_HOLD_MS = 4000L

/**
 * 队列里的一格 —— 走位与缩放都在**它自己**身上，旧消息的位移是**被它占的高度挤出来的**。
 *
 * ## 怎么「挤」
 *
 *  入场/退场只跑一个 `progress`（0 = 不在位，1 = 在位）。方向按 `exiting / exitingDown` 定，
 *  但**都是相对自己**的：
 *   · 入场：从上方 16dp 落下来、0.92 → 1；
 *   · 到点退场：原路往**上** 16dp 收回去、1 → 0.92、淡出；
 *   · 被挤出队列：往**下** 56dp 滑走，同样缩到 0.92。
 *  真正推动别的条目的是**报告给父级的高度**（见下面 `Modifier.layout`）：按 `progress` 从 0 长到满，
 *  父级（`Column`）每帧重排 ⇒ 下面几条被逐帧往下挤；走的时候反过来，它们逐帧落回来。
 *
 *  和 `expandVertically` 的区别在**内容不裁**：这里只是把「报给父级的高度」按比例缩小，
 *  内容仍按原尺寸完整画在 (0,0) —— 所以看到的是整条滑进来，而不是「从上往下一点点露出来」
 *  （用户口径「不是从零长出来，动画不要变，挤的是位移量」）。
 *
 *  于是也不需要 FLIP / 让位动画：没有任何一条自己去做位移补偿，位移全是布局挤出来的。
 *
 * @param item 这一格画什么、是不是正在走
 */
@Composable
private fun HintSlot(
    item: HintItem,
    backdrop: HazeState?,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(item.exiting) {
        progress.animateTo(
            targetValue = if (item.exiting) 0f else 1f,
            //  **对称的 `FastOutSlowInEasing`**：`DecelerateEasing` 前段极快、后段几乎不动，
            //  看着就是「一下冲到位然后停住」（真机反馈「过渡到中间停住」）。
            animationSpec = tween(HINT_FLY_MS.toInt(), easing = FastOutSlowInEasing),
        )
    }
    val enter = with(LocalDensity.current) { HINT_ENTER_DISTANCE.toPx() }
    val exit = with(LocalDensity.current) { HINT_EXIT_DOWN.toPx() }
    //  方向和长度在**组合期**就定好：只有「被挤出队列」那条往下，其余（入场、到点退场）
    //  都取负 —— 到点走的走的是入场那条路的反向，从哪儿来回哪儿去。
    val travel = if (item.exitingDown) exit else -enter
    Box(
        modifier
            //  报给父级的高度按 `progress` 长（入场）/ 缩（退场）—— 这就是「挤」：父级每帧重排，
            //  下面的条目跟着位移。
            //
            //  入场时内容**贴着这个槽的下沿**放（`shown - full`，即往上冒出一整条）：报出去的高度
            //  长多少，它的下边缘就走到哪，下面那几条被推开的距离与新消息的下边缘**始终是同一条线**。
            //  以前内容固定画在 (0,0)、只靠 `translationY` 往上挪 16dp，槽还没长起来的那半程里
            //  新胶囊其实已经整条画在旧消息身上了 —— 屏幕上就是「盖着」而不是「挤开」。
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val full = placeable.height
                val shown = (full * progress.value).roundToInt().coerceIn(0, full)
                layout(placeable.width, shown) {
                    //  退场那条不动：它本来就要从自己的位置滑走、把位置让出来
                    placeable.place(0, if (item.exiting) 0 else shown - full)
                }
            }.graphicsLayer {
                //  相对**自己**的位移与缩放（与容器无关）：
                //   · 入场：**不再自带位移**（位置由上面 `place` 贴着槽的下沿定），代价是它从
                //     上面「冒出来」而不是浮在半空滑下来 —— 这样才不会压住别人；
                //   · 到点退场：往上 16dp 收回去（`travel` 取负）；
                //   · 被挤出队列：往**下** 56dp 滑走 —— 它是被后来的顶下去的。
                //  缩放只影响画出来的大小，**占位仍按未缩放的尺寸**（槽的高度来自 `placeable.height`）。
                translationY = if (item.exiting) travel * (1f - progress.value) else 0f
                val scale = HINT_SCALE + (1f - HINT_SCALE) * progress.value
                scaleX = scale
                scaleY = scale
                //  入场**不淡入**（文字从头就实着），走的时候**淡出**
                alpha = if (item.exiting) progress.value.coerceIn(0f, 1f) else 1f
            },
    ) {
        // 这圈是**给阴影的画布**，不是排版留白（见 [HINT_SHADOW_SIDE]）：动画期间那层
        // `graphicsLayer` 的边界就是节点边界，而阴影画在胶囊外面 —— 不留出来就会被裁平。
        //  宽度**不要** `fillMaxWidth`：那会把这一格撑满整屏，胶囊就贴到左边去了
        //  （真机反馈「现在不居中了」）。宽度跟着胶囊走，居中由 [PageSnackbars] 给。
        Box(
            Modifier.padding(
                start = HINT_SHADOW_SIDE,
                end = HINT_SHADOW_SIDE,
                top = HINT_SHADOW_CAP,
                bottom = HINT_SHADOW_CAP,
            ),
        ) {
            HintCapsule(item.text, backdrop)
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
                .then(
                    backdrop?.let {
                        //  这一叠**一直在动**（进出场 + 让位都是逐帧位移），而糊是每帧重算的 ——
                        //  所以取半分辨率采样那一档：Haze 会按缩放同比放大半径，视觉半径与观感不变，
                        //  只是少掉一点糊的细节；换来的是一半的采样像素。
                        Modifier.frosted(it, shape, performanceMode = HazePerformanceMode.Performance)
                    } ?: Modifier,
                ).background(container, shape)
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
    //  上一次算出来的轮廓。同一个形状在一帧里要被问**四五次**（阴影 / 磨砂裁剪 / 底色 /
    //  描边 / 裁内容），每次都是一模一样的尺寸 —— 不缓存就是同一帧里重画四五遍同样的
    //  8 段超椭圆曲线、再建四五个 `Path`；进出场那几帧本来就没有余量，这一档省得最省事。
    private var cached: Outline? = null
    private var cachedSize: Size = Size.Unspecified
    private var cachedDensity = 0f
    private var cachedDirection: LayoutDirection? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val hit = cached
        //  尺寸、密度、书写方向三者任一变了才重算（换句时宽度会变，那是唯一会命中的场合）
        if (hit != null && sameAsCached(size, density, layoutDirection)) return hit
        val path =
            Path().apply {
                addSquircleRect(
                    width = size.width,
                    height = size.height,
                    cornerRadius = with(density) { cornerRadius.toPx() },
                )
            }
        val outline = Outline.Generic(path)
        cached = outline
        cachedSize = size
        cachedDensity = density.density
        cachedDirection = layoutDirection
        return outline
    }

    /** 上一次算的那份轮廓还能不能复用 —— 尺寸 / 密度 / 书写方向三样都对上才算命中。 */
    private fun sameAsCached(
        size: Size,
        density: Density,
        layoutDirection: LayoutDirection,
    ): Boolean = size == cachedSize && density.density == cachedDensity && layoutDirection == cachedDirection
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
 * 胶囊左右两侧的**阴影画布**（28dp = 阴影半径 26 + 余量）。
 *
 *  为什么需要它：进出场那层 `alpha`（离屏层）的边界就是节点边界，而阴影画在胶囊外面 ——
 *  不留出来就会被裁平。左右两侧不参与堆叠间距，所以照留满。
 */
private val HINT_SHADOW_SIDE = 28.dp

/**
 * 上下那两圈画布 —— 只留这么点，因为**它同时就是相邻两条的间距**：每条上下各占一份，
 * 再加上 [HINT_ITEM_MARGIN] 的上下各 4dp，两条胶囊之间实际空 **16dp**（用户口径「消息间距再小一点」）。
 *  留满 28dp 时两条之间空 56dp，比胶囊本身还高；收到 4dp 才凑出这一档。
 *
 *  代价：阴影往上是 22dp、往下 30dp，**动画那 180ms 里**上下各裁掉一截 —— 进场与退场都套着
 *  那层 `alpha`，离屏层的边界就是节点边界。稳定后没有 alpha 层，一点不裁。
 *  裁掉的是径向渐变最淡的尾巴，真机上看不出来；退场往下走时同理，走的那条会被自己的下沿裁一点。
 */
private val HINT_SHADOW_CAP = 4.dp

/** 胶囊左右两侧的外边距（库 `Snackbar` 内联的那 16dp，COUI `grid_guide_column_card_margin_start`）。 */
private val HINT_SIDE_MARGIN = 16.dp

/** 胶囊上下两侧的外边距（库 `Snackbar` 内联的那 4dp）—— 与 [HINT_SHADOW_CAP] 的上下各 4dp 相加，才是两条之间的 8dp。 */
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
 * 进出场与让位那几条规格 —— 照搬 `fret-logic` 的 `v-transition-fly-up`（它是
 * `src/assets/transitions.scss` 里给浮层消息用的那一套）：
 *
 * ```scss
 * .fly-up-enter-from { transform: translateY(-16px) scale(0.96); opacity: 0; }
 * .fly-up-leave-to   { transform: translateY(-16px) scale(0.92); opacity: 0; }
 * .fly-up-enter-active, .fly-up-leave-active {
 *   transition: opacity .1s cubic-bezier(.25,.1,.25,1), transform .1s cubic-bezier(.34,1.56,.64,1);
 * }
 * .fly-up-move { transition: transform .18s cubic-bezier(.34,1.56,.64,1); }
 * ```
 *
 *  **`transform` 一律用 `FastOutSlowInEasing`，不用带 overshoot 的 `cubic-bezier(.34,1.56,.64,1)`**：
 *  那条曲线在末尾会「冲过头再弹回来」，十六进制缩放在 100ms 里弹一下特别刺眼
 *  （用户口径「不要做回弹效果，太难看了」）。fret-logic 用它是因为 web 上那点回弹配着 180ms
 *  还算轻，挪到这个位置就过了。
 *
 *  和原版**不一样的两处**（都按真机口径调的）：
 *   · 原版进出场都往上 16px；这里**进场往上（-16dp 落下来）、到点出场原路往上收回去**，
 *     只有**被挤出队列**那条反过来往下 56dp（用户口径「超出队列的消息往下滑出配合 scale」、
 *     「到点自动消失往上」）；
 *   · 原版的 `.fly-up-move`（让位位移）**不另做**：位移靠新条目报出来的高度逐帧挤，见 [HintSlot]。
 *     上一版用 `expandVertically` 做让位，问题正是「高度逐帧变化把别的东西一起推」——
 *     这里虽然也动高度，但内容不裁、整条照原样画，看上去是「被挤」，不是「从零长出来」。
 */
private const val HINT_FLY_MS = 180L

/** 入场从上方落下的距离（`translateY(-16px)`）。 */
private val HINT_ENTER_DISTANCE = 16.dp

/**
 * 退场往**下**滑走的距离 —— 只给**被挤出队列**那一条用（到点的走 `HINT_ENTER_DISTANCE` 原路往上）。
 *
 *  比入场远：入场是「落进来」，这条是「被顶下去」，得有被推开的量感。取 56dp（≈ 一格「胶囊 +
 *  间距」）—— 走完这一段它已经让出整整一格，配合 0.92 的缩小与淡出，观感就是被后来那条挤掉。
 *  这也是用户口径「超出队列的消息往下滑出配合 scale」「到点自动消失往上」的那两句话的分界：
 *  **自己走的原路返回，被挤掉的顺着挤的方向走**。
 */
private val HINT_EXIT_DOWN = 56.dp

/** 缩放的两端 —— 入场 0.92 → 1（`fly-up` 是 0.96，这里取 0.92 与出场共用一条曲线）。 */
private const val HINT_SCALE = 0.92f

/**
 * 换句时胶囊宽度过渡的时长 —— 这一段**文字是不可见的**（见 [HintCapsule] 的「换句三段」），
 * 于是宽度动画不会带着半截文字一起动。
 *
 *  120ms：比进出场那条（[HINT_FLY_MS] = 180）短一档 —— 换句只做宽度过渡，
 *  加上前后两段文字淡入淡出也要 300ms 出头，再长就显得慢半拍。
 */
private const val HINT_RESIZE_MS = 120

/** 换句时文字淡出 / 淡入各自的时长 —— 只是「别硬切」，不需要看出渐变，所以给得很短。 */
private const val HINT_TEXT_FADE_MS = 35
