package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.ModeMenuPopups
import com.lookie.opluskey.ui.ModeMenuTarget
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.boundAppName
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.actionRowView
import com.lookie.opluskey.ui.model.durationText
import com.lookie.opluskey.ui.model.gridColumnCount
import com.lookie.opluskey.ui.model.tileIcon
import com.lookie.opluskey.ui.tickNow
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

// 「按键绑定」页的两张网格 —— 内置动作一组组排下来，再加最下面「已保存的命令」那一组。
//
// 从 `BindingList.kt` 拆出来：那一段原本占了一个 666 行函数的后三分之一，而且缩进已经到 36 列
// （`Column → forEach → Column → forEach → Row → forEach → key → ActionTile`）。
// 现在一格一个函数（[BuiltInTile] / [CustomTile]），排布的那三层只负责**计数与让位**。

/**
 * 内置动作网格 —— 按 [ActionCatalog.groupsFor] 分组，每组一个小标题。
 *
 *  **不要自己另拼一份清单**：分组与格子必须同源（`groupsFor()` 现在不按手势分化，三档清单一致；
 *  哪天真按手势分化了，也得让分组跟着一起变）。曾经两边各算一次，组大小没同步少 ⇒ 最后一组
 *  多吃进第一条自定义命令（真机反馈「单击 / 双击的自定义命令跑到快捷功能里了」）。
 *
 * 焦点格索引仍与 `st.specs` 下标一一对应（见 `BindingList` 里「扁平焦点索引的账」）——
 * 分组只是展示层切分，组里的格子用的还是**全局**下标（每组的起点见 groupBases），
 * 不能在组内从 0 数。
 */
@Composable
internal fun SettingsPage.BuiltInGrid(
    st: BindingGridState,
    enabled: Boolean,
) {
    val groups = remember { ActionCatalog.groupsFor() }
    //  **每一组的起始下标预先算好**，不用跨组累加的可变变量。
    //  BoxWithConstraints 的 content 是另起的子组合：在那里读外层那个累加变量，拿到的是
    //  **首次组合那一刻的值**，而父作用域每帧又把它重新初始化成 0 —— 结果两组都从同一个起点取格子
    //  （真机：系统操作与快捷功能渲染的是同一批「已保存的命令」）。起点必须是纯数据。
    val groupBases =
        remember(groups) {
            val starts = ArrayList<Int>(groups.size)
            var base = 0
            groups.forEach {
                starts += base
                base += it.second.size
            }
            starts
        }
    //  每一组 = 标题 + 网格，包成一块；组与组之间的间距由小标题自带，不做首尾特判。
    Column {
        groups.forEachIndexed { groupIndex, (groupTitle, group) ->
            SectionTitle(ActionCatalog.groupTitleLabel(groupTitle))

            // 这一段用一个**内层 Column** 单独带 `PAGE_MARGIN` 横向 padding，
            // 标题在外层不受影响。
            //
            //  **列数按可用宽度算**（`gridColumnCount`）：最少一列，横屏 / 平板 / 大字模式下
            //  宽过一档就自动多一列 —— 固定两列时右边会空出一大片，格子里那点字却还是被挤着。
            BoxWithConstraints(
                Modifier.padding(horizontal = PAGE_MARGIN),
            ) {
                val columns = gridColumnCount(maxWidth)
                //  把真实列数告诉状态：方向键的分行表按它算（见 `BindingGridState.columns`）。
                //  写成 `LaunchedEffect` 而不是组合期直接赋值 —— 组合期只读不写是本项目的纪律。
                LaunchedEffect(columns) { st.columns = columns }
                val base = groupBases[groupIndex]
                Column(
                    verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
                ) {
                    for (rowIndices in (base until base + group.size).chunked(columns)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            // 上下左右间距保持一致；不再给焦点圈留补偿（见 `GRID_GAP` 的注释）
                            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                        ) {
                            rowIndices.forEach { index ->
                                BuiltInTile(st, index, enabled, Modifier.weight(1f))
                            }
                            // 最后一行不满时补空位：不补的话剩下那格会被 weight 撑到整行宽
                            repeat(columns - rowIndices.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 内置网格里的一格。
 *
 *  `Modifier.weight` 由调用点在 `Row` 里取好传进来 —— 它是 `RowScope` 的扩展，
 *  而 `key { }` 的 lambda 已经不在那个作用域里了。
 */
@Composable
private fun SettingsPage.BuiltInTile(
    st: BindingGridState,
    index: Int,
    enabled: Boolean,
    cellModifier: Modifier,
) {
    val spec = st.specs.getOrNull(index)
    if (spec == null) {
        Spacer(cellModifier)
        return
    }
    // 会弹菜单的那几格各自 remember 一份触点锚点（键是 token）——
    // `PreciseClickState` 里除触点坐标外还存着「锚点自己的布局位置」，
    // 两格共用一份就会被算成「最后布局的那一格」，浮层弹到别人身上。
    //  写在 `key(spec.token)` 里、而不是在页面这一层一格格手写：新增一个入口格
    //  不用再补一份锚点、也不用改一张 `when` 表（判据就是「这格点了弹不弹菜单」）。
    key(spec.token) {
        val tileAnchor = rememberPreciseClickState()
        //  登记给确认键那条路用（见 `BindingGridState.tileAnchors`）—— **进出各一次**，不在组合体里写：
        //  组合期写共享 map 有两个后果（跳过重组时不登记、格子被删掉后又摘不掉），而这张表
        //  正是确认键弹菜单的落点来源。
        DisposableEffect(spec.token, tileAnchor) {
            st.tileAnchors[spec.token] = tileAnchor
            onDispose {
                if (st.tileAnchors[spec.token] === tileAnchor) st.tileAnchors.remove(spec.token)
            }
        }
        val current = st.target.actionIn(configState.config.value)
        //  入口格（点了弹二级菜单的那几格）：`modes` 非空。要不要挂锚点也从这里算
        val modes = ActionCatalog.modeMenu(spec.token)
        val popsMenu = modes != null
        //  「这一行长什么样」（字形 / 小字 / 选中态）全交给 [actionRowView] ——
        //  抽屉那一行调的是同一个函数，两处不再是各拼一半的复印件。
        //  小字写「应用名 - 组件名 / 快捷方式名」（`微信 - 扫一扫`）：与「选择应用」页页头、
        //  点开那层菜单的子标题是同一段（父名「应用控制」不写 —— 格子的标题就是它）
        val view = actionRowView(spec, current, boundAppName(current, withDetail = false))
        ActionTile(
            spec = spec,
            //  入口格的行首随当前档位变（主屏幕 / 静音 / 上一首 …）；「应用控制」格再细一档：
            //  绑成什么样就画什么（结束 / 快捷方式 / 某个界面）。规则全在 `actionRowView` 里。
            icon = view.icon,
            selected = view.selected,
            focused = index == st.focusedIndex,
            focusRequester = st.focusers[index],
            //  锚点**每格一份**：取值格点了不弹浮层，所以只有会弹菜单的格子才挂（`popsMenu`），
            //  其余不白记坐标。
            modifier = if (popsMenu) cellModifier.preciseClickAnchor(tileAnchor) else cellModifier,
            //  可用性与 `pick` 同一判据（主开关 + 链路就绪）：少判一道就会
            //  「亮着、点了被拦」—— 点了只弹一句提示，不如先灰着。
            enabled = enabled && linkReady,
            // 小字 = 当前绑的是什么，与抽屉那一行同一句（`actionRowView.subtitle`）
            subtitle = view.subtitle,
            // 入口格虽然是**入口**（点了进下一层），但**不画行尾箭头** ——
            // 走 `ActionTile` 的默认值 false，别顺手补回来。
            onClick = { bindingActivate(st, spec, tileAnchor, enabled) },
        )
    }
}

/**
 * 「自定义命令」那一段 —— **始终在**：没有命令时给一行「去创建自定义命令」
 * （整段不出现的话，网格里就看不出这块地方是干什么的）。样式与网格同一套纵向节奏。
 */
@Composable
internal fun SettingsPage.CustomCommandsGrid(
    st: BindingGridState,
    enabled: Boolean,
    onCreateCommand: () -> Unit,
) {
    val commandsByToken =
        remember(configState.commands.value) {
            configState.commands.value.associateBy { it.token }
        }
    //  与内置网格同一条规则：列数按可用宽度算（`gridColumnCount`），最少一列。
    BoxWithConstraints(
        Modifier.padding(horizontal = PAGE_MARGIN),
    ) {
        val columns = gridColumnCount(maxWidth)
        //  与内置网格同一个值（同一页、同一段横向边距）；这里再写一次是为了「自定义命令多到
        //  内置网格被折叠」的那种布局下也一定被设过。
        LaunchedEffect(columns) { st.columns = columns }
        Column(
            verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
        ) {
            if (st.specs.size <= st.builtInCount) {
                CreateCommandRow(enabled, onCreateCommand)
            } else {
                // 焦点索引 `[builtInCount, totalCount)` —— 与 `specs` 下标一一对应
                for (rowIndices in (st.builtInCount until st.totalCount).chunked(columns)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                    ) {
                        rowIndices.forEach { index ->
                            CustomTile(st, index, enabled, commandsByToken, Modifier.weight(1f))
                        }
                        repeat(columns - rowIndices.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 还没保存过任何命令时那一行 —— 整行可点，进「自定义命令」页新增一条（与 FAB 同一个去处）。 */
@Composable
private fun SettingsPage.CreateCommandRow(
    enabled: Boolean,
    onCreateCommand: () -> Unit,
) {
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    title = I18n.get(Strings.BINDING_CREATE_CUSTOM_COMMAND, "自定义命令"),
                    summary = I18n.get(Strings.BINDING_CREATE_CUSTOM_COMMAND_DESC, "一条命令或多个动作"),
                    icon = PageIcons.CommandCode,
                    // 行尾箭头：「这一行后面还有一页」（它不是弹浮层的入口格，所以不画那颗选择器字形）
                    arrow = true,
                    enabled = enabled,
                    onClick = {
                        onCreateCommand()
                    },
                ),
            ),
        // 这张卡在**已经带左右边距的网格段**里，所以自己不再加一档
        margin = 0.dp,
    )
}

/** 自定义命令网格里的一格。 */
@Composable
private fun SettingsPage.CustomTile(
    st: BindingGridState,
    index: Int,
    enabled: Boolean,
    commandsByToken: Map<String, Command>,
    cellModifier: Modifier,
) {
    val spec = st.specs.getOrNull(index)
    if (spec == null) {
        Spacer(cellModifier)
        return
    }
    // 每格按 `key(spec.token)` 认身份 —— 与内置那组同一写法。
    //  这一段是按**位次**排出来的（列数由 `st.columns` 现算，见 `BindingGridState`），
    //  而自定义命令会增删、会改名重排：不按 token 认身份，格子里的状态（焦点圈、进出场）
    //  就跟着位次挪到下一条命令身上。
    key(spec.token) {
        // 这条命令本身（`cmd:<名字>` 反查）—— 小字与「进编辑」两处都要用
        val cmd = commandsByToken[spec.token]
        val current = st.target.actionIn(configState.config.value)
        val selected = ActionCatalog.isSelected(spec, current)
        // 「进这条命令的编辑页」—— **已选中时再点一次**就是唯一入口（长按不接，见 `ActionTile`）。
        //
        //  走 `draft.load` 而**不是** FAB 那条 `navigateToCommands` —— 后者会先 `draft.reset()`
        //  （那是「新增」的入口），这里要的正是「把这条命令灌进表单」，
        //  与「自定义命令」页里轻点已保存命令同一个入口。
        val openEditor = {
            if (cmd != null) {
                // 自定义命令这一格不是 `CardRow`（它是网格里的一格），振动仍自己叫一声；
                // 走页面那条通用通道（`tickNow`），判据与列表行完全同一份。
                tickNow()
                XLog.w("手势页打开自定义命令编辑：${cmd.name}")
                // 这里直接 `load`、不走 `loadDraft` 那个「先问一句」：草稿脏了必然还在
                // 「自定义命令」页 —— 退出那一页时 `goBack` 已经问过并 `draft.reset()`。
                //  走 `loadDraft` 反而更糟：它会把确认窗挂起，而那个窗画在「自定义命令」页上、
                //  此刻根本没组合 ⇒ 按下去什么都不发生。
                commandDraft.draft.load(cmd)
                navTo(Screen.COMMANDS)
            } else {
                //  静默 return 的话「点不动」在日志里没有凭据 —— 至少说清是找不到那条命令
                XLog.w("手势页打开自定义命令编辑：这条绑定不是 `cmd:`（${spec.token}），跳过")
            }
        }
        ActionTile(
            spec = spec,
            icon = tileIcon(spec),
            selected = selected,
            focused = index == st.focusedIndex,
            focusRequester = st.focusers[index],
            modifier = cellModifier,
            // 与内置网格同一判据。
            enabled = enabled && linkReady,
            // 标题下面那行小字 = **命令间隔时长**（`stepDelayMs`）。
            // 只有多条命令有这个值 —— 单条只有一步、用不到它，所以那种格子不画。
            subtitle =
                cmd
                    ?.takeIf { it.kind == CommandKind.SERIAL }
                    ?.let { durationText(it.stepDelayMs) },
            // **已经选中的再点一次 = 「我要改它」**，不是「重新绑一遍」，
            // 所以走编辑；没选中时才是「选中绑定」。这一格不再接长按 ——
            // 「选中 → 再点一次」两步本来就走得通，长按只是同一件事的第二个入口。
            onClick = { if (selected) openEditor() else bindingSelect(st, spec, enabled) },
        )
    }
}

/**
 * 入口格那一层的菜单。它**不占布局位置** —— 真正的落点由那一格记下的触点决定，
 * 挂在这里只是让它跟着这一页一起进 / 出组合。
 *
 *  档位那一层菜单、以及系统开关再往下那一问（弹窗）连同两者之间的守卫都在 [ModeMenuPopups]
 *  里 —— 这里只报「哪一格开着」与「选中了哪一档」，不拼菜单结构。
 */
@Composable
internal fun SettingsPage.BindingTileMenu(st: BindingGridState) {
    val current = st.target.actionIn(configState.config.value)
    ModeMenuPopups(
        // 锚点就是**开着的那一格**自己那一份 —— 这里不再有「哪一格用哪一支」的表：
        // 那种表漏一个入口格，菜单就弹到别人身上（小窗、通知与控制中心，两次都是这么漏的）。
        //  关掉之后 `tileMenu` 是 null，而退场动画期间真正生效的是 `MenuPopup` 内部保持住的
        // 上一次那份锚点，所以这里给个没记过触点的占位即可。
        preciseClick = st.tileMenu?.anchor ?: st.noAnchor,
        // 行与顶上那行小标题都由这一格算：不再各传一份。
        //  三档手势拿到的菜单完全相同（灵感语音是「开始 / 结束」两个平级动作）
        target = st.tileMenu?.spec?.let { ModeMenuTarget(it) },
        current = current,
        //  走 `pick` 而**不是** `bindingSelect`：菜单里的取值不在 `specs` 里，
        //  `select` 那一步的 `specs.indexOf(spec)` 会取到 −1、把焦点圈甩到第一格去。
        //  圈落回**被点开的那一格入口格**（本来就是点它才弹出的菜单），也是当场就挪 ——
        //  不靠「选中项变了再由 effect 收回来」（那条路真机上没跑起来，见 `bindingSelect`）。
        onPick = { spec ->
            st.tileMenu?.spec?.let { st.focusOn(it.token) }
            // 「应用控制」那一层：两行都**不是取值**，选完进挑应用页（打开 / 结束各一趟），
            //  真正的落盘在那张页面的确认键上（`app:` / `proc:stop:`）。
            when (spec.token) {
                ActionCatalog.APP_CONTROL_OPEN -> st.onAppControl(st.target, false)
                ActionCatalog.APP_CONTROL_STOP -> st.onAppControl(st.target, true)
                else -> pick(spec, st.target)
            }
        },
        onDismiss = { st.tileMenu = null },
    )
}
