package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.frosted
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.COMMAND_TOOLBAR_BOTTOM
import com.lookie.opluskey.ui.model.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.TOOLBAR_EDGE_GAP
import com.lookie.opluskey.ui.model.TOOLBAR_ICON_SIZE
import com.lookie.opluskey.ui.model.TOOLBAR_SHADOW_ALPHA
import com.lookie.opluskey.ui.model.edgeSlideIn
import com.lookie.opluskey.ui.model.edgeSlideOut
import com.lookie.opluskey.ui.screen.apppicker.appLabelOf
import com.lookie.opluskey.ui.screen.binding.BindingScreen
import com.lookie.opluskey.ui.state.rememberTick
import io.github.suqi8.coui.kmp.basic.FloatingToolbar
import io.github.suqi8.coui.kmp.basic.FloatingToolbarDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.theme.COUITheme

internal fun SettingsPage.commandToolbarVisible(): Boolean {
    val payloadReady =
        when (commandDraft.draft.kind) {
            CommandKind.SINGLE -> commandDraft.draft.single.isNotBlank()
            CommandKind.SERIAL -> commandDraft.draft.steps.isNotEmpty()
        }
    return commandDraft.draft.editingName != null || commandDraft.draft.name.isNotBlank() || payloadReady
}

@Composable
internal fun SettingsPage.CommandActions(modifier: Modifier = Modifier) {
    val tick = rememberTick()
    val editing = commandDraft.draft.editingName != null
    val name = commandDraft.draft.name
    val payloadReady =
        when (commandDraft.draft.kind) {
            CommandKind.SINGLE -> commandDraft.draft.single.isNotBlank()
            CommandKind.SERIAL -> commandDraft.draft.steps.isNotEmpty()
        }

    val canReset = commandToolbarVisible()
    // 重名（且不是编辑它自己）时按钮直接禁用；`cmd:` 引用成环时同样禁用（提示落在名称那格里）。
    val canSave =
        name.isNotBlank() && payloadReady &&
            !commandNameDuplicated(name, commandDraft.draft.editingName) &&
            commandReferenceCycle() == null

    // 两个按钮都不可用就整条隐藏；`canSave` 蕴含 `canReset`，判据只看后者。
    val visible = canReset
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
        //   于是胶囊外面会多出一圈糊边（真机反馈「给工具条外部再套一个磨砂圈」就是它）。
        //   改成：我们自己 `padding(outSidePadding)` 再挂磨砂（画在留白以内 ⇒ 正好等于胶囊那一块），
        //   并把库的 `outSidePadding` 置零 —— 总占位、点击范围都不变（都是 12dp / 8dp）。
        //
        //  注意：**阴影得自己补，且补在磨砂外面**：磨砂链上有个 `clip(胶囊)`（见 [frosted]），
        //  挂在它里面的东西全被裁进胶囊那个形状；
        //   库自己的 `dropShadow` 就挂在那里，而阴影本来画在形状**外面** ⇒ 被裁得一干二净
        //  （真机反馈「工具条阴影怎么没了」就是它）。所以把库的 `shadowElevation` 置零、按库原样
        //   （`radius = 10dp / 黑 10%`）在被裁的那一层**外面**补一层同样的阴影。
        val state = pageHazeState
        if (state == null && !frostDegradeLogged) {
            frostDegradeLogged = true
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
        val frost: Modifier = state?.let { gap.frosted(it, capsule) } ?: gap
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
            // 用图标按钮：文字会把这窄条撑宽。文案进了各自的 `contentDescription`。
            // 按钮 40dp、高亮圆角一半，外留白与按钮间距都是 [TOOLBAR_EDGE_GAP]。
            // 底色 / 字形的可用 / 禁用切换走统一的 [COLOR_TRANSITION_SPEC]。
            val resetIconColor by animateColorAsState(
                targetValue =
                    if (canReset) {
                        COUITheme.colorScheme.onSurfaceContainer
                    } else {
                        COUITheme.colorScheme.disabledOnSecondaryVariant
                    },
                animationSpec = COLOR_TRANSITION_SPEC,
                label = "ToolbarResetIcon",
            )
            val saveBackgroundColor by animateColorAsState(
                targetValue =
                    if (canSave) {
                        // 底色走**主题原色**（透明度导致颜色和主题色有偏差，
                        //  指的是**这颗按钮**，不是整条工具条）：不再单独降 alpha，字形（`onPrimary`，白）
                        //  压在实色 `primary` 上对比度也最足。代价是实色底把糊盖住，这颗圆里看不到磨砂。
                        COUITheme.colorScheme.primary
                    } else {
                        COUITheme.colorScheme.surfaceContainerHigh
                    },
                animationSpec = COLOR_TRANSITION_SPEC,
                label = "ToolbarSaveBackground",
            )
            val saveIconColor by animateColorAsState(
                targetValue =
                    if (canSave) {
                        COUITheme.colorScheme.onPrimary
                    } else {
                        COUITheme.colorScheme.disabledOnSecondaryVariant
                    },
                animationSpec = COLOR_TRANSITION_SPEC,
                label = "ToolbarSaveIcon",
            )
            Row(
                modifier = Modifier.padding(TOOLBAR_EDGE_GAP),
                horizontalArrangement = Arrangement.spacedBy(TOOLBAR_EDGE_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
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
                    enabled = canReset,
                    minWidth = TOOLBAR_ICON_SIZE,
                    minHeight = TOOLBAR_ICON_SIZE,
                    cornerRadius = TOOLBAR_ICON_SIZE / 2,
                ) {
                    Icon(
                        PageIcons.MenuReset,
                        contentDescription =
                            if (editing) {
                                I18n.get(Strings.COMMON_CANCEL, "取消")
                            } else {
                                I18n.get(Strings.COMMON_RESET, "重置")
                            },
                        // 禁用时跟着置灰（库不给图标上色）
                        tint = resetIconColor,
                    )
                }
                IconButton(
                    onClick = {
                        tick()
                        addCommand()
                    },
                    enabled = canSave,
                    minWidth = TOOLBAR_ICON_SIZE,
                    minHeight = TOOLBAR_ICON_SIZE,
                    cornerRadius = TOOLBAR_ICON_SIZE / 2,
                    // `primary` + 亮字形；禁用时落回 `surfaceContainerHigh` + 禁用灰（底不消失）。
                    backgroundColor = saveBackgroundColor,
                ) {
                    Icon(
                        PageIcons.Confirm,
                        contentDescription =
                            if (editing) {
                                I18n.get(Strings.COMMON_MODIFY, "修改")
                            } else {
                                I18n.get(Strings.COMMON_SAVE, "保存")
                            },
                        tint = saveIconColor,
                    )
                }
            }
        }
    }
}
