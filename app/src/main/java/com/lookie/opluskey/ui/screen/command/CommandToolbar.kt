package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.HINT_SLOT_COMMAND
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.addCommand
import com.lookie.opluskey.ui.frosted
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.COMMAND_TOOLBAR_BOTTOM
import com.lookie.opluskey.ui.model.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.TOOLBAR_EDGE_GAP
import com.lookie.opluskey.ui.model.TOOLBAR_ICON_SIZE
import com.lookie.opluskey.ui.model.TOOLBAR_SHADOW_ALPHA
import com.lookie.opluskey.ui.model.edgeSlideIn
import com.lookie.opluskey.ui.model.edgeSlideOut
import com.lookie.opluskey.ui.rememberTick
import io.github.suqi8.coui.kmp.basic.FloatingToolbar
import io.github.suqi8.coui.kmp.basic.FloatingToolbarDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.LocalDisabledClick
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 载荷**还差什么**（`null` = 够了）—— 「保存」能不能点、灰着时说什么，判据只有这一处。
 *
 *  单条那一档三句话各自对应一种真实的半成品：没写 / 没写对格式 / 写了前缀没写正文。最后那档
 *  （`shell:` 这种只有前缀的）没有一句在说它 —— 保存按钮灰着、点一下只回一句
 *  「这一项现在不可用」，说不出是哪一种。多条那一档只差「有没有步骤」。
 *
 *  **这一支必须能在填好之后返回 `null`**：`saveBlockedReason()` 拿它当唯一判据，
 *  永远非空就等于「保存」按钮永远点不动。
 */
private fun SettingsPage.payloadBlockedReason(): String? =
    when (commandDraft.draft.kind) {
        CommandKind.SINGLE -> {
            singlePayloadBlockedReason()
        }

        CommandKind.SERIAL -> {
            if (commandDraft.draft.steps.isEmpty()) I18n.get(Strings.COMMAND_NEED_STEP, "还没添加步骤") else null
        }
    }

/** 单条那一档的载荷判据（[payloadBlockedReason] 的一半，判据仍只有那一处）。 */
private fun SettingsPage.singlePayloadBlockedReason(): String? {
    val raw = commandDraft.draft.single
    if (raw.isBlank()) return I18n.get(Strings.COMMAND_CMD_HINT, "请输入命令")
    if (!Action.hasSupportedFormat(raw)) {
        return I18n.get(Strings.COMMAND_FORMAT_REQUIRED, "开头要写 shell: / cmd: 这类前缀")
    }
    // 写法合法之后，再看这段字**解析出来到底做不做事**：`shell:` / `cmd:` 这种只有前缀的
    //  （[Action.isBarePrefix]），以及 `keyevent:` 空正文 / 认不出的键码 / `none`（解析成
    //  [Action.None]）——存下去都是一条注定什么都不做的命令。
    val action = Action.parse(raw)
    return if (action.isBarePrefix || action == Action.None) {
        I18n.get(Strings.COMMAND_PAYLOAD_INCOMPLETE, "命令内容还不完整")
    } else {
        // 这里**必须**能返回 `null`：它是「能不能存」的唯一判据，一直非空就等于保存按钮永远灰着。
        null
    }
}

/**
 * 名字那一格的红字 —— 与「保存被禁用时那句话」共用这一支（同一件事只说一处）。
 *
 *  两条判据本身在 `CommandLibrary`（重名扫一遍命令库、`cmd:` 成环走引用图），这里只负责措辞。
 */
internal fun SettingsPage.commandNameProblem(): String? =
    when {
        commandNameDuplicated(commandDraft.draft.name, commandDraft.draft.editingName) -> {
            I18n.get(Strings.COMMAND_NAME_DUPLICATE, "与已有命令重名，请换个名字")
        }

        else -> {
            commandReferenceCycle()?.let { I18n.format(Strings.COMMAND_CYCLE_ERROR, it.joinToString(" → ")) }
        }
    }

/**
 * 「保存」此刻**为什么**点不动（`null` = 能点）—— 保存按钮的 `enabled` 就是「它是不是 `null`」。
 *
 *  灰着被点一下时说的也是这一句（见下面那层 `LocalDisabledClick`）：兜底的
 *  「这一项现在不可用」对「写了 `shell:`、正文还空着」的人等于什么都没说。
 */
internal fun SettingsPage.saveBlockedReason(): String? {
    if (commandDraft.draft.name.isBlank()) return I18n.get(Strings.COMMAND_NAME_HINT, "请输入名称")
    payloadBlockedReason()?.let { return it }
    return commandNameProblem()
}

/**
 * 底部工具条此刻该不该在。
 *
 *  排序态（`stepEditing`）下**整条滑出隐藏**，而不是置灰：那时改的是步骤顺序、不是命令内容，
 *  保存与重置都没有意义；留一条灰工具条只是多占一块地方。
 */
internal fun SettingsPage.commandToolbarVisible(): Boolean =
    !commandDraft.stepEditing &&
        (commandDraft.draft.editingName != null || commandDraft.draft.name.isNotBlank() || payloadBlockedReason() == null)

/**
 * 底部那条浮动工具条要的几件事 —— 由「自定义命令」页把它那份草稿的状态译成这一份。
 *
 *  [visible] 管整条出不出现；[canReset] / [canSave] 是两个按钮各自可不可点；
 *  [labels.blocked] 是「保存还不能点」的原因（点灰按钮时就说这一句）。
 */
internal class ToolbarSpec(
    val visible: Boolean,
    val canReset: Boolean,
    val canSave: Boolean,
    val labels: ToolbarLabels,
    val actions: ToolbarActions,
)

/** 两个按钮各自的文案与「不能存」的原因。 */
internal class ToolbarLabels(
    val reset: String,
    val save: String,
    val blocked: () -> String?,
)

/** 两个按钮各自做什么。 */
internal class ToolbarActions(
    val onReset: () -> Unit,
    val onSave: () -> Unit,
)

/**
 * 底部那条浮动工具条的本体 —— 磨砂、阴影、进出场、两颗圆按钮全在这里。
 *
 *  现在只有「自定义命令」页在用它（[CommandActions]）：条件执行页的落盘入口是右下角那颗
 *  「确定」，不在这条工具条上。
 */
@Composable
internal fun SettingsPage.FloatingActions(
    spec: ToolbarSpec,
    modifier: Modifier = Modifier,
) {
    val canReset = spec.canReset
    val canSave = spec.canSave
    val visible = spec.visible
    // 滑出要滑到屏幕下沿之外：胶囊浮在底边上方 [COMMAND_TOOLBAR_BOTTOM] 处，那段距离要一起算。
    val bottomGapPx = with(LocalDensity.current) { COMMAND_TOOLBAR_BOTTOM.roundToPx() }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // 从底边滑入 / 滑出 —— 与右下角那颗 FAB 同一份规格（见 [edgeSlideIn]）
        enter = edgeSlideIn(bottomGapPx),
        exit = edgeSlideOut(bottomGapPx),
    ) {
        // 磨砂：与顶栏、浮层菜单同一个挂法 —— 从**页面内容层**取样（[frosted]）。
        //  取样方在本页那个取样源**之外**（工具栏挂在页内、`hazeSource` 那个 Box 外面，
        //  见 `SettingsPage` 的调用点），不会落进自己取样的那一层（落了就是 RenderThread 栈溢出）。
        //
        //  注意：**留白必须自己先加**（拉过 `FloatingToolbar.kt` 才看明白）：库内部那串链是
        //   `modifier → padding(outSidePadding) → divider → shadow → squircleBackground(color)`，
        //   而修饰符的绘制顺序就是链上的先后 —— 磨砂挂在 `modifier` 上，画的是**连留白一起**的整块矩形，
        //   于是胶囊外面会多出一圈糊边。
        //   改成：我们自己 `padding(outSidePadding)` 再挂磨砂（画在留白以内 ⇒ 正好等于胶囊那一块），
        //   并把库的 `outSidePadding` 置零 —— 总占位、点击范围都不变（都是 12dp / 8dp）。
        //
        //  注意：**阴影得自己补，且补在磨砂外面**：磨砂链上有个 `clip(胶囊)`（见 [frosted]），
        //  挂在它里面的东西全被裁进胶囊那个形状；
        //   库自己的 `dropShadow` 就挂在那里，而阴影本来画在形状**外面** ⇒ 被裁得一干二净
        //  所以把库的 `shadowElevation` 置零、按库原样
        //   （`radius = 10dp / 黑 10%`）在被裁的那一层**外面**补一层同样的阴影。
        val frost = toolbarFrostModifier()
        FloatingToolbar(
            modifier = frost,
            // 底色要**半透明**才透得出磨砂：`compositeOver(surface)` 会把它压成不透明实心，
            //  那正是「磨砂看不见」的直接原因。
            //  注意：alpha 只能**按比例缩**，不能 `copy(alpha = …)` 整个换掉 —— 库的深色板里
            //  `surfaceContainer` 是**半透白**（`0x1AFFFFFF`，白 10%，叠在黑页上才是那个深灰），
            //  整个换成 0.82 就成了「白 82%」的亮胶囊：真机反馈「暗色下工具条还是亮色的」正是它。
            //  浅色板里它是实色白，替换恰好看不出来，所以之前没暴露。顶栏没这毛病是因为它取
            //  `surface`（两档都是实色）。
            color =
                COUITheme.colorScheme.surfaceContainer.let {
                    it.copy(alpha = it.alpha * FROSTED_SURFACE_ALPHA)
                },
            // 留白已由上面那段 `padding` 顶掉，这里置零 —— 不置会**再**缩一圈
            outSidePadding = PaddingValues(0.dp),
            // 库那层阴影画在磨砂的裁剪层**里面**、必定被裁光（见上面那段注释），改由我们自己在外面补
            shadowElevation = 0.dp,
            // `showDivider` 那圈描边与半透明底互斥（原注释的结论，磨砂要它让位）。
            showDivider = false,
        ) {
            ToolbarButtons(spec)
        }
    }
}

/**
 * 「自定义命令」页那条工具条 —— 重置 / 保存两颗圆按钮，浮在页面底边上方。
 *
 *  实现全在 [FloatingActions] 里（磨砂、阴影、进出场），这一层只负责「把命令草稿的状态译成 spec」。
 */
@Composable
internal fun SettingsPage.CommandActions(modifier: Modifier = Modifier) {
    val tick = rememberTick()
    val editing = commandDraft.draft.editingName != null
    FloatingActions(
        spec =
            ToolbarSpec(
                visible = commandToolbarVisible(),
                canReset = commandToolbarVisible(),
                // 能不能存只看这一句（`null` = 能存）：判据与「点灰按钮时说的话」是同一支
                canSave = saveBlockedReason() == null,
                labels =
                    ToolbarLabels(
                        reset = if (editing) I18n.get(Strings.COMMON_CANCEL, "取消") else I18n.get(Strings.COMMON_RESET, "重置"),
                        save = if (editing) I18n.get(Strings.COMMON_MODIFY, "修改") else I18n.get(Strings.COMMON_SAVE, "保存"),
                        blocked = ::saveBlockedReason,
                    ),
                actions =
                    ToolbarActions(
                        onReset = {
                            tick()
                            // 多条命令带步骤时走行级退场：步骤行 shrink 完才真正 reset，否则整批行瞬时消失。
                            if (commandDraft.draft.kind == CommandKind.SERIAL && commandDraft.draft.steps.isNotEmpty()) {
                                // 记下这一刻的内容指纹：收尾时对一次账，这期间用户又输入了就放弃 reset。
                                commandDraft.resetBaseline = commandDraft.draft.contentFingerprint()
                                commandDraft.resetPending = true
                            } else {
                                commandDraft.draft.reset()
                            }
                        },
                        onSave = {
                            tick()
                            addCommand()
                        },
                    ),
            ),
        modifier = modifier,
    )
}

/**
 * 「保存」灰着被点一下时说的那一句 —— 就地换掉整页那条 `LocalDisabledClick`。
 *
 *  单独抽一支只为把 [ToolbarButtons] 的长度压回 detekt 的上限。
 *
 *  **lambda 必须 `remember` 住、原因用 `rememberUpdatedState` 跟住**：`spec.labels` 每次重组
 *  都是新实例，直接把它捕进 lambda 就等于每次换一个引用 —— 读这条通道的控件会跟着白白重组。
 *  也归整页那一个位（[HINT_SLOT_COMMAND]）：连点几下「保存」时改字不叠条。
 */
@Composable
private fun SettingsPage.SaveDisabledHint(
    labels: ToolbarLabels,
    content: @Composable () -> Unit,
) {
    val current = rememberUpdatedState(labels)
    val onBlocked: () -> Unit =
        remember {
            {
                current.value.blocked()?.let { hint(it, slot = HINT_SLOT_COMMAND) }
            }
        }
    CompositionLocalProvider(LocalDisabledClick provides onBlocked) { content() }
}

/**
 * 底部工具条那层「磨砂 + 阴影」的修饰符 —— 从 [FloatingActions] 抽出来只为把那边压回长度上限。
 *
 *  · 磨砂从**页面内容层**取样（[frosted]）。取样方在页面那个取样源**之外**（工具条挂在页内、
 *    `hazeSource` 那个 Box 外面），不会落进自己取样的那一层（落了就是 RenderThread 栈溢出）。
 *  · **留白必须自己先加**：库内部那串链是 `modifier → padding(outSidePadding) → divider →
 *    shadow → squircleBackground(color)`，而修饰符的绘制顺序就是链上的先后 —— 磨砂挂在
 *    `modifier` 上，画的是**连留白一起**的整块矩形，于是胶囊外面会多出一圈糊边。
 *    改成：我们自己 `padding(outSidePadding)` 再挂磨砂（画在留白以内 ⇒ 正好等于胶囊那一块），
 *    并把库的 `outSidePadding` 置零 —— 总占位、点击范围都不变。
 *  · **阴影得自己补，且补在磨砂外面**：磨砂链上有个 `clip(胶囊)`（见 [frosted]），挂在它里面
 *    的东西全被裁进胶囊那个形状；库自己的 `dropShadow` 就挂在那里，而阴影本来画在形状**外面**
 *    ⇒ 被裁得一干二净。所以把库的 `shadowElevation` 置零、按库原样（`radius = 10dp / 黑 10%`）
 *    在被裁的那一层**外面**补一层同样的阴影。
 */
@Composable
private fun SettingsPage.toolbarFrostModifier(): Modifier {
    val state = chrome.pageHazeState
    if (state == null && !chrome.frostDegradeLogged) {
        chrome.frostDegradeLogged = true
        XLog.w("底部工具栏磨砂跳过：页面取样层未就绪 ⇒ 这一轮仍用不透明底")
    }
    val capsule = remember { RoundedCornerShape(FloatingToolbarDefaults.CornerRadius) }
    val gap =
        Modifier
            .padding(FloatingToolbarDefaults.OutSidePadding)
            .dropShadow(
                shape = capsule,
                shadow = Shadow(radius = 10.dp, color = Color.Black, alpha = TOOLBAR_SHADOW_ALPHA),
            )
    return state?.let { gap.frosted(it, capsule) } ?: gap
}

/**
 * 工具条里那两个圆按钮 —— 从 [FloatingActions] 抽出来只为把那边压回长度上限。
 *
 *  颜色三档都走 [COLOR_TRANSITION_SPEC]；保存那颗的底色用**主题原色** `primary`
 *  （不再单独降 alpha：字形压在上面对比度最足，代价是实色底把糊盖住）。
 */
@Composable
private fun SettingsPage.ToolbarButtons(spec: ToolbarSpec) {
// 用图标按钮：文字会把这窄条撑宽。文案进了各自的 `contentDescription`。
// 按钮 40dp、高亮圆角一半，外留白与按钮间距都是 [TOOLBAR_EDGE_GAP]。
// 底色 / 字形的可用 / 禁用切换走统一的 [COLOR_TRANSITION_SPEC]。
    val on = COUITheme.colorScheme.onSurfaceContainer
    val off = COUITheme.colorScheme.disabledOnSecondaryVariant
// 保存那颗的底色走**主题原色**（不单独降 alpha：字形压在上面对比度最足，代价是实色底盖住了糊）
    val saveOn = COUITheme.colorScheme.primary
    val saveOff = COUITheme.colorScheme.surfaceContainerHigh
    val resetIconColor by animateColorAsState(
        targetValue = if (spec.canReset) on else off,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "ToolbarResetIcon",
    )
    val saveBackgroundColor by animateColorAsState(
        targetValue = if (spec.canSave) saveOn else saveOff,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "ToolbarSaveBackground",
    )
    val saveIconColor by animateColorAsState(
        targetValue = if (spec.canSave) COUITheme.colorScheme.onPrimary else off,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "ToolbarSaveIcon",
    )
    Row(
        modifier = Modifier.padding(TOOLBAR_EDGE_GAP),
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_EDGE_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = spec.actions.onReset,
            enabled = spec.canReset,
            minWidth = TOOLBAR_ICON_SIZE,
            minHeight = TOOLBAR_ICON_SIZE,
            cornerRadius = TOOLBAR_ICON_SIZE / 2,
        ) {
            Icon(
                PageIcons.MenuReset,
                contentDescription = spec.labels.reset,
                // 禁用时跟着置灰（库不给图标上色）
                tint = resetIconColor,
            )
        }
        // 灰着被点了一下时，说清是**哪一档**没齐（不是页面不可用）—— 见 [SaveDisabledHint]。
        SaveDisabledHint(spec.labels) {
            IconButton(
                onClick = spec.actions.onSave,
                enabled = spec.canSave,
                minWidth = TOOLBAR_ICON_SIZE,
                minHeight = TOOLBAR_ICON_SIZE,
                cornerRadius = TOOLBAR_ICON_SIZE / 2,
                // `primary` + 亮字形；禁用时落回 `surfaceContainerHigh` + 禁用灰（底不消失）。
                backgroundColor = saveBackgroundColor,
            ) {
                Icon(
                    PageIcons.Confirm,
                    contentDescription = spec.labels.save,
                    tint = saveIconColor,
                )
            }
        }
    }
}
