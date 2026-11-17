package com.lookie.opluskey.ui.row

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.LocalTick
import com.lookie.opluskey.ui.feedback.NoSwitchHaptic
import com.lookie.opluskey.ui.feedback.rememberTick
import com.lookie.opluskey.ui.feedback.scrollBarTick
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.page.ROW_START_ICON_GAP
import com.lookie.opluskey.ui.model.page.SliderTapTrack
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.theme.EASING_EMPHASIZED_DECEL
import com.lookie.opluskey.ui.model.theme.VISIBILITY_ENTER_MS
import com.lookie.opluskey.ui.style.tinted
import com.lookie.opluskey.ui.write.BindingTarget
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalDisabledClick
import io.github.suqi8.coui.kmp.basic.ScrollBarAdapter
import io.github.suqi8.coui.kmp.basic.VerticalScrollBar
import io.github.suqi8.coui.kmp.basic.rememberScrollBarAdapter
import io.github.suqi8.coui.kmp.interfaces.ExperimentalScrollBarApi
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 可展开行行首那颗图标 —— 与 `CardRow` / `SwitchRow` 那颗同一档：库只给 8dp，左边距是 16dp
 * （见 [ROW_START_ICON_GAP]）。
 *
 */
@Composable
private fun ExpandableRowIcon(
    icon: ImageVector,
    tint: Color,
) {
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.padding(end = ROW_START_ICON_GAP),
    )
}

/**
 * 一行长文本的**折行展开 / 收起** —— 收起时压成一行（末尾省略号），展开后原样折行。
 *
 * ## 它解决的是「折行」不是「多段」
 *
 *  值本身是**一段**文字（指纹这种），只是太长、在界面上会折成两三行，把整张卡的行高撑得不齐。
 *  所以收起时给 `maxLines = 1` + 省略号，展开时放开 —— 展开后与不折叠时**一模一样**：
 *  排版仍是同一个 `Text`（同样的字号、颜色、左边界、行距），只是 `maxLines` 不同。
 *
 *  早先两版都错在这里：先是另画一块子行（左对齐与行距都对不上），后又按行拆成 `List<String>`
 *  （把「一段折行」当成了「多段内容」）。
 *
 * ## 行尾那颗「展开 / 收起」
 *
 *  它只是**指示**，不是按钮：点击一律交给整行（`BasicComponent` 的 `onClick`）。
 *  早先给它单独挂了 `clickable`，结果是「点箭头才灵、点同一行的文字反应不一样」——
 *  同一件事在一行里出现两种命中区，用户没法预期。
 *
 *  只有**真的折了行**才画这颗箭头：一行放得下时「展开」点了也没有变化，等于骗人。
 *  判据是收起状态下那次排版有没有溢出（`TextLayoutResult.hasVisualOverflow`），
 *  量一次就够，不必自己算宽度。
 */

@Composable
internal fun SettingsPage.ExpandableRow(
    spec: ExpandableRowSpec,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val tick = rememberTick()
    // 收起时那一次排版有没有溢出 —— 只有溢出（也就是真的折行了）才值得给「展开」
    var overflowed by remember(spec.text) { mutableStateOf(false) }
    val toggle = {
        tick()
        onToggle()
    }
    //  语义色（如「不是这包」的红）→ 随机配色 → 常规内容色，与 `CardRow` / `SwitchRow` 同序；
    //  这条色也带过渡 —— 它与同一张卡里那些行首图标一起变，不能只有这一颗硬切。
    val tintTarget = spec.iconTint ?: spec.icon.tinted() ?: COUITheme.colorScheme.onSurfaceContainer
    val iconTint by animateColorAsState(tintTarget, COLOR_TRANSITION_SPEC, label = "ExpandableRowIcon")
    BasicComponent(
        onClick = toggle,
        //  「减少界面图标」开着时行首那一格不画（与 `CardRow` 那几支同一条，见 `LocalReduceRowIcons`）
        startAction =
            if (LocalReduceRowIcons.current) {
                null
            } else {
                { ExpandableRowIcon(spec.icon, iconTint) }
            },
        endActions =
            if (expanded || overflowed) {
                {
                    // 箭头 + 旋转：收起时朝下、展开时转 180° 朝上，转的过程与高度动画同一档。
                    //  **不单独挂点击**：整行就是命中区（见类注释）。
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL),
                        label = "ExpandArrow",
                    )
                    Icon(
                        PageIcons.StepDown,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .rotate(rotation)
                                .size(ROW_END_ICON_SIZE),
                        // 与全页其他「行尾图标」同一档色（不是强调色）：它只是个指示，不是动作项
                        tint = COUITheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            } else {
                null
            },
        cardListPosition = spec.position,
    ) {
        // 高度自己补动画：`maxLines` 一变，这一块就从一行跳到三行 —— 与页面里其他「动态显隐」
        //  同一档时长与缓动（`VISIBILITY_ENTER_MS` / `EASING_EMPHASIZED_DECEL`），切换才不突兀。
        Column(
            Modifier.animateContentSize(tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL)),
        ) {
            // 标题与值自己画：库那个 `BasicComponent(title, summary)` 版本不给 `maxLines`，
            //  而「收起成一行」正是这里唯一要做的事。字号与字重走全项目那一份（[RowTitleLine]）。
            RowTitleLine(
                text = spec.title,
                // 与滑杆那一档同一套：标题取 `onBackground`（库的 `titleColor()` 是 internal，取不到）
                color = COUITheme.colorScheme.onBackground,
            )
            RowSummaryLine(
                text = spec.text,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = ROW_LINE_GAP),
                // 收起压成一行、展开原样折行 —— 这是这一档唯一的行为差别
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                onTextLayout = { result ->
                    // 只在收起态量：展开态本来就全部显示，溢出恒为 false
                    if (!expanded) overflowed = result.hasVisualOverflow
                },
            )
        }
    }
}

/**
 * 一行**会折行的长文本**长什么样 —— 把「画什么」与「展没展开」分开，后者由调用方持有。
 *
 * @param text 这一行的值（**一段**文字，界面上可能折成两三行）
 * @param iconTint 行首图标颜色 —— **`null` = 跟着「随机图标配色」走**（见 `LocalIconTint`），
 *   没开就退回常规内容色。要给语义色的（「不是这包」那种红）由调用方显式给。
 */
internal class ExpandableRowSpec(
    val title: String,
    val text: String,
    val icon: ImageVector,
    val position: CardListPosition,
    val iconTint: Color? = null,
)

/**
 * **一组列表行** —— 行与行之间给 [gap]，**第一行不加、最后一行不加**。
 *
 *  首 / 末这一处判断就在这个组件里做一次，调用点只提供行本身 —— 行不需要知道自己是第几行，
 *  也不要在行自己身上挂外边距（那正是之前「标题上间距一会儿大一会儿小」的来源：
 *  行自带的下边距会叠到紧跟其后的标题上）。
 *
 *  为什么不是 `LazyColumn(verticalArrangement = spacedBy(…))`：那样连「标题 → 第一行」也会被
 *  插一档；这里只作用在同一组行内部。
 */
internal fun <T> LazyListScope.spacedItems(
    items: List<T>,
    gap: Dp = 12.dp,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    itemsIndexed(
        items = items,
        key = if (key == null) null else { _, item -> key(item) },
    ) { index, item ->
        Column {
            //  首行不给上间距 —— 标题与第一行之间只该有标题自带的那一档
            if (index > 0) Spacer(Modifier.height(gap))
            itemContent(item)
        }
    }
}

/**
 * 「这一档的系统场景入口」那一行 —— 「按键绑定」页与「组合键编辑」页共用这一份。
 *
 *  两页这一行说的是同一件事（场景先判、命中就轮不到下面那些绑定），摆的位置也一样；
 *  各写一遍的话，「停用了还点不点得进去」「提示说的是哪一句」迟早分叉 —— 而它们本来就该同步。
 *
 *  **这一行跟着这一档的启停走**（用户口径）：这一份停用了，它与「独立设置」一起灰掉 ——
 *  停用的键 / 组合不接管按键，表进去也改不出效果；灰着点一下说的是同一句停用原因。
 *  这一条与 [BindingTarget.pageBlockedReason] 的说明同步改过（那一段原先写的是反过来的规矩）。
 *
 * @param enabled 这一份自己**还开着**吗（判据由调用点给，见 `bindingTargetStopped`）
 * @param disabledReason 灰着那一句（同上）
 * @param onOpen 点这一行进「系统场景」—— 调用方负责把档位摆到眼前这一页再导航
 */
internal fun SettingsPage.scenarioEntry(
    target: BindingTarget,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onOpen: () -> Unit,
): CardRow.Item =
    CardRow.Item(
        title =
            scenarioCountNotice(target.rulesIn(configState.config.value))
                ?: I18n.get(Strings.COMMAND_BRANCH_SCENARIO_NONE),
        icon = PageIcons.CommandBranch,
        arrow = true,
        enabled = enabled,
        disabledHint = disabledReason,
        onClick = onOpen,
    )

/** 规则表里有启用中的那几条，就报出条数；一条都没有 / 全停用 → `null`（整段不画）。 */
internal fun scenarioCountNotice(rules: List<SceneRule>): String? {
    val count = rules.count { it.enabled }
    if (count == 0) return null
    return I18n.format(Strings.COMMAND_BRANCH_SCENARIO_COUNT, count)
}

// 竖向滚动条 —— 全项目**唯一**那一份。
//
// 库的 `VerticalScrollBar` 只画条：常亮与否、振动、轨道两端留白这三件事都得调用点自己配。
// 三处（浮层菜单 / 选择应用列表 / 图标网格）原先各写一份，振动那段甚至有一处是内联复制出来的
// —— 改一处漏两处，表现就是「这条滚起来有振动、那条没有」。

/**
 * 轨道上下让出去的留白 —— 8dp（库自带的那一档）。
 *
 *  面板 / 弹窗都有圆角：轨道贴着边画时，拇指滑到顶 / 底会压在那圈弧上，看着像「滑出面板」。
 *  上下各收一档，两端都停在圆角之内。
 *
 *  **个别地方要更宽时由调用点自己传**（菜单那条取小标题上留白那一档，见 `PageMenuPopup`）——
 *  默认值不为某一个调用点让步。
 */
private val SCROLLBAR_TRACK_PADDING = 8.dp

/**
 * **通用竖向滚动条** —— 三处共用（浮层菜单 `MenuScrollBar`、选择应用列表 `PickerScrollBar`、
 * 图标选择器），几何（宽度 / 高度 / 边距）由调用点给。
 *
 *  三件事收在这一层，调用点不必再各配一遍：
 *   · **常亮**（`alwaysVisible`）：这些地方都不是「滑动时给你的反馈」，而是「这下面还有内容」的
 *     唯一提示 —— 按库默认那种「不碰就淡出」等于没有提示（用户口径）。
 *   · **按住滑块的振动**走通用通道（[scrollBarTick]）：判据是「DOWN 已被库里 consume」，
 *     按在轨道空白处不振。
 *   · **轨道两端留白**（[SCROLLBAR_TRACK_PADDING]）：拇指不压在圆角上。
 *
 * @param adapter 由 [rememberScrollBarAdapter] 给（三个便捷重载会替你建）
 * @param modifier 几何与位置 —— 宽度、高度、贴哪条边由调用点定
 * @param trackPadding 轨道两端让出去的留白；传 `PaddingValues()` 可关掉
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun AppScrollBar(
    adapter: ScrollBarAdapter,
    modifier: Modifier = Modifier,
    trackPadding: PaddingValues = PaddingValues(vertical = SCROLLBAR_TRACK_PADDING),
) {
    VerticalScrollBar(
        adapter = adapter,
        trackPadding = trackPadding,
        modifier = modifier.scrollBarTick(),
        alwaysVisible = true,
    )
}

/** 页面级滚动（`Column` + `verticalScroll`）那一档 —— 浮层菜单用它。 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun AppScrollBar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    trackPadding: PaddingValues = PaddingValues(vertical = SCROLLBAR_TRACK_PADDING),
) {
    AppScrollBar(rememberScrollBarAdapter(scrollState), modifier, trackPadding)
}

/** `LazyColumn` 那一档 —— 选择应用 / 选择界面两页用它。 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun AppScrollBar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    trackPadding: PaddingValues = PaddingValues(vertical = SCROLLBAR_TRACK_PADDING),
) {
    AppScrollBar(rememberScrollBarAdapter(listState), modifier, trackPadding)
}

/** `LazyVerticalGrid` 那一档 —— 图标选择器用它。 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun AppScrollBar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    trackPadding: PaddingValues = PaddingValues(vertical = SCROLLBAR_TRACK_PADDING),
) {
    AppScrollBar(rememberScrollBarAdapter(gridState), modifier, trackPadding)
}

/**
 * 「点轨道跳值」手势（滑杆行那条路）：值域整份取自 [SliderSpec]，几何由调用方给的 [SliderTapTrack] 决定
 *（行里传 `SLIDER_ROW_TAP_TRACK`，弹窗自己算那一份）。
 *
 *  这个重载只负责「别把 [spec] 拆开抄」：值域与步长必须一起传；只传 min / max 会让触点折算用错
 *  `step` —— 三档滑杆上 `(max-min)/step` 算成 0、夹到 1，点中间只能落到端点上。
 *
 *  **这里传的是取值刻度**（[SliderSpec.step]），折算出来的只是「点在轨道哪个位置」；
 *  真正写下去之前还要过两层吸：调用方的 `snapDrag`（拖动刻度，取整数倍）与 `sliderCommit`
 *  里的 `spec.snap`（取值刻度）。粗拖那条路不走库的 `steps` —— 它只能均分，除不尽就会
 *  落出 175 这种数（见 `SliderSpec.snapDrag`）。
 */
internal fun Modifier.tapSliderToSeek(
    spec: SliderSpec,
    track: SliderTapTrack,
    onSeek: (Int) -> Unit,
): Modifier = tapSliderToSeek(spec.min, spec.max, spec.step, spec.enabled, track, onSeek)

/**
 * 「点轨道跳值」手势：手势节点比滑杆大（整行），这里按 [track] 圈出滑杆占的那条带子，
 * 只有落在带子里的轻点才跳值。
 *
 *  手势挂在**外层节点**上，不挂滑杆自己的 `Modifier` —— 那会与它内部的拖动争事件。
 *  几何由调用方按自己的排版给（行里要算行内边距与行首图标，弹窗里那根滑杆就是节点本身）。
 *
 * ## 按下的去处
 *
 *  · **落在带子里**：这一下归滑杆 —— 拖动全归它（我们不消费按下与移动），
 *    轻点则由这里跳值；**抬手要消费掉**。不消费的话，外层那一层「点整行」的点击
 *    （`BasicComponent` 的 `onClick`，滑杆行用它开手动输入窗）会同时被触发：点轨道
 *    = 跳到那个值 **+** 弹一个输入窗，而用户只是想拖到那儿。
 *  · **不落在带子里**（标题 / 摘要 / 行尾值 / 行内边距）：这里一概不碰，事件原样往上传，
 *    由外层那一层统一处理（波纹 + 振动 + 开窗）。
 *
 * ## 调用方的义务
 *
 *  `onSeek` 必须是**稳定的**闭包：`pointerInput` 的键里没有它，闭包停在首次组合那一刻。
 *  直接捕获会变的参数（例如当前值）就一直用旧的那份 —— 轻点回初值会被调用方自己那句
 *  「值没变就不提交」判掉。写法见 `PageSlider` 与 `PercentDialog`：闭包只捕获
 *  `rememberUpdatedState` 出来的那个 State，调用时才读值。
 */
internal fun Modifier.tapSliderToSeek(
    min: Int,
    max: Int,
    step: Int,
    enabled: Boolean,
    track: SliderTapTrack,
    onSeek: (Int) -> Unit,
): Modifier =
    pointerInput(min, max, step, enabled) {
        // 禁用时这条手势也得停 —— 它是**自己挂的**、不走滑杆的 `enabled`，
        // 不停的话置灰的滑杆照样能点着跳值（多条命令下的「连续触发间隔」）。
        if (!enabled) return@pointerInput
        val slop = viewConfiguration.touchSlop
        // 轨道两端：手势节点边缘往里让出的距离（行里是行内边距 + 行首图标，弹窗里是 0）
        val trackStart = track.startInset.toPx()
        val trackEnd = track.endInset.toPx()
        val zone = track.zone.toPx()
        awaitPointerEventScope {
            while (true) {
                val down =
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                // 没落在滑杆那条带子里 ⇒ 这次按下不归这条手势管
                // （不 consume，让外层那一层照常处理它）
                //  注意：**纵向与横向都要圈** —— 只判纵向的话，横向靠后面的
                //  `coerceIn(0f, 1f)` 兜 —— 在轨道**左/右外侧**、但仍在底部那条带子里轻点会被折成 0 或 1，
                //  也就是**写 min / max**：用户只是点了行首图标那一格或行尾空白，值却被跳到端点。
                if (down.position.y < size.height - zone) continue
                if (down.position.x < trackStart || down.position.x > size.width - trackEnd) continue
                // 滑动 / 没正常抬手 ⇒ 不算轻点（拖动全归滑杆，抬手也不归我们消费）
                val at = awaitTapUp(down, slop) ?: continue
                val span = (size.width - trackStart - trackEnd).coerceAtLeast(1f)
                val frac = ((at.x - trackStart) / span).coerceIn(0f, 1f)
                onSeek(seekValue(frac, min, max, step))
            }
        }
    }

/**
 * 等这次按下的**抬手**；返回抬手时的位置。
 *
 *  确认是轻点之后**把抬手消费掉**（见 [tapSliderToSeek] 的「按下的去处」）：
 *  这一下已经由跳值那一条处理过了，外层的行点击不该再开一次输入窗。
 *
 * @return `null` = 这次不算轻点（滑走了 / 被取消 / 中途丢了这一个指针）
 */
private suspend fun AwaitPointerEventScope.awaitTapUp(
    down: PointerInputChange,
    slop: Float,
): Offset? {
    var moved = false
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return null
        if ((change.position - down.position).getDistance() > slop) moved = true
        if (change.changedToUpIgnoreConsumed()) {
            if (moved) return null
            change.consume()
            return down.position
        }
        if (!change.pressed) return null
    }
}

/**
 * 轨道上的相对位置（0..1）→ 值：折成值之后交给 [snapToStep] 取整。
 *
 *  刻度必须与 `Slider.steps` 那套同源（`(max-min)/step` 格），不能直接按 `step` 取整 ——
 *  区间不整除时两套值对不上。那份刻度只在 [snapToStep] 写一次，这里只是「位置 → 值」的折算。
 */
private fun seekValue(
    frac: Float,
    min: Int,
    max: Int,
    step: Int,
): Int = snapToStep((min + frac * (max - min)).roundToInt(), min, max, step)

/**
 * 每颗开关**上一次落定的位置** —— 页面级一份，按行标题记（见 `PageContent` 的 provide）。
 *
 * ## 为什么需要它
 *
 *  写配置会让整页内容重新挂一遍（`PageContent` 读整份 `config` ⇒ 任何一次写都重组整页，
 *  库那边的 entry 内容跟着重新录制）。库的 `Switch` 在新组合里把那次当成「首次组合」，
 *  直接跳过动画（`Switch.kt` 的 `isFirstComposition`）—— 滑块与轨道于是停在终值，看起来是硬切。
 *
 *  把「上一个位置」记在**行的组合之外**：行被重建时先摆回那个位置（新组合因此是「旧态」），
 *  下一帧再把真值交回去，库就有一条真正的「旧 → 新」可以播。
 */
internal val LocalSwitchPositions = staticCompositionLocalOf<MutableMap<String, Boolean>> { mutableMapOf() }

/** 库 `Switch` 那两条动画（滑块 383ms / 轨道 450ms）落定所需的时间 —— 之后才算「用户看到的」。 */
private const val SWITCH_SETTLE_MS = 500L

/**
 * 单行开关（库 `SwitchPreference` 的项目包装）。
 *
 * 存在的理由是**行首图标必须自己上色**：库只给**它自己画的东西**按 `enabled` 上色，
 * `startAction` 槽位原样渲染 —— 不给的话 `enabled = false` 会「文字灰了、图标还亮着」
 * （在「长按连续触发」那行复现）。
 *
 * 这条规矩不许在各调用点各抄一遍 —— 抄漏一个就不一致。收进这里之后配色要变只改这一处 ——
 * 与 [SliderRow] 同一个道理，两张行挨在同一张卡里时也不会各用一种灰。
 *
 * 库 `Switch` 自己会无条件振一次，这里统一套 [NoSwitchHaptic] 掐掉，振动只走调用方的
 * `tick()` / `tickNow()` 那条通道。
 *
 * ## 副标题（[summaryOn] / [summaryOff]）的口径
 *
 *  **真假各一句，由这里按 `checked` 取**：开关的两个状态说的是两件事，一句描述不了 ——
 *  「关闭后此模块将停用」在已经关着的时候是错的（那时它已经关着了）。
 *
 *  · **精确**：说的是「现在这个状态下会发生什么」。「开启 / 关闭」用结果表达（走哪条接口、
 *    执行一次还是重复、弹不弹提示），不写内部机制（哪个窗口、哪条判据）；
 *  · **精简**：一行放得下（中文约 14 字），多一个字就折行、把整张卡撑高。
 *
 *  两句都由调用点给（文案走字典 `Strings.*_ON` / `*_OFF`），**取哪一句这件事只在这里做** ——
 *  各调用点自己写一遍 `if (checked)`，迟早有一处忘了分、变成「一句管两个状态」。
 *  置灰时那个「为什么点不了」的原因**优先于**这两句，由调用点写成 `reason ?: …` 的两份
 *  （见 `LongRepeatCard`）。
 *
 * @param icon 行首图标。同一张卡里几行都给图标，标题才会对齐（见 `ActionIcons` 里那条账）；
 *   `null` = 这一行不画（「减少界面图标」开着时 `CardRow` 那一层就这么给）
 * @param summaryOn 开关**开着**时的副标题
 * @param summaryOff 开关**关着**时的副标题
 * @param enabled false ⇒ 整行置灰、不吃点击。标题 / 副标题 / 开关由库负责，**行首图标由这里负责**
 * @param position 卡片内的行位置（首行 / 中间 / 末行），决定圆角与分隔线
 *
 *  **没有 `SettingsPage.` 接收者**：体内只碰库件与 [NoSwitchHaptic] / [rememberRowIconColor]，
 *  都是顶层声明 —— 接收者一次都用不到。原先挂着它只是为了「与同目录那批页面组件写法一致」，
 *  而那正是「component/ 名不副实」的一部分：**通用控件不该背页面类型**（它会白白把
 *  `SettingsPage` 拉进每个使用者的依赖里）。这是这一批里第一个去掉接收者的，同目录其余
 *  真的用不到接收者的也照此办理。
 */
@Composable
internal fun SwitchRow(
    title: String,
    /** 副标题（`null` = 这一行不画副标题，只留标题与开关）。 */
    summaryOn: String?,
    summaryOff: String?,
    icon: ImageVector?,
    checked: Boolean,
    position: CardListPosition,
    enabled: Boolean = true,
    /** 拨这一下振不振 —— 默认振（与 `CardRow.Item.vibrate` 同一条口径）。 */
    vibrate: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    //  振动走页面那条通道（`LocalTick`）：这一层不带 `SettingsPage` 接收者。
    //  `NoSwitchHaptic` 已经把库自带的那一次关掉了 —— 那个不看系统静音，这里要看。
    val tick = LocalTick.current
    // 颜色带过渡，与 [SliderRow] 同一套参数：主开关一拨，这个图标与库里那些标题一起渐变。
    //  硬切会出现「图标在淡、字在跳」——长按那张卡里 SwitchRow 与 SliderRow 就是挨着的。
    val iconColor by rememberRowIconColor(enabled, icon = icon, label = "SwitchIcon")

    //  这一行画出来的那个位置由 [shown] 说了算：稳态下它就是 `checked`；只有「行刚被重建」的
    //  那一帧，它是页面级记下的**上一个位置**，随后再切回真值 —— 库的开关因此总有一条能播的
    //  「旧 → 新」（见 [LocalSwitchPositions] 与 [SWITCH_SETTLE_MS]）。
    val positions = LocalSwitchPositions.current
    //  **按 [title] 记一份**：这一格的组合槽位被别的行复用时（卡片里条件行增删），
    //  沿用上一行的位置会让开关首帧画成别人的值再回弹。
    //  同一页出现两行同名（多处「振动反馈」）时仍共用 [positions] 里那一格 —— 那要按行 key 记才根治，
    //  而 `LocalSwitchPositions` 现在只认标题。
    var shown by remember(title) { mutableStateOf(positions[title] ?: checked) }
    LaunchedEffect(checked) {
        if (shown == checked) return@LaunchedEffect
        positions[title] = shown
        shown = checked
        try {
            delay(SWITCH_SETTLE_MS)
        } finally {
            //  **被打断也要归位**：`checked` 在这 200ms 内又翻一次时这一支会被取消（`delay` 抛
            //  取消异常），不归位的话 `positions[title]` 就停在上一个值 —— 这一行下次重建时
            //  首帧会照那个旧值画一下再回弹。
            positions[title] = shown
        }
    }

    //  置灰那一档**点一下要说话**：COUI 在 `enabled = false` 时把行自己的点击摘了，但**行尾那颗
    //  开关仍然会吃掉触摸**（它自己带手势），所以挂在父层的 `clickable` 拿不到事件 ——
    //  实测「点开关没反应、点空白处才有提示」。正确做法是**盖一层**：置灰时在整行之上铺一块
    //  只吃点击的透明层，命中最上层，这一下就稳定落到提示上。
    //  `indication = null`：置灰行不该再冒涟漪（它会看着像「其实能点」）。
    val disabledClick = LocalDisabledClick.current
    Box(Modifier.fillMaxWidth()) {
        NoSwitchHaptic {
            SwitchPreference(
                title = title,
                summary = if (checked) summaryOn else summaryOff,
                checked = shown,
                onCheckedChange = { on ->
                    if (vibrate) tick()
                    onCheckedChange(on)
                },
                cardListPosition = position,
                insideMargin = BasicComponentDefaults.InsideMargin,
                startAction = {
                    //  「减少界面图标」开着时这一格整块不画（`icon` 由 `CardRow` 那一层给 `null`）
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = iconColor,
                            //  与 `CardRow` 那颗行首图标同一档：补上库少给的那 8dp（见 [ROW_START_ICON_GAP]）
                            modifier = Modifier.padding(end = ROW_START_ICON_GAP),
                        )
                    }
                },
                // `enabled = false` 由 COUI 负责：标题与副标题走 `disabledColor`、行尾开关跟着灰
                enabled = enabled,
            )
        }
        if (!enabled && disabledClick != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = disabledClick,
                    ),
            )
        }
    }
}
