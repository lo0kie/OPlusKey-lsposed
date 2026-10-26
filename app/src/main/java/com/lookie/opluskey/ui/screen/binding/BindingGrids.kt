package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.ModeMenuPopups
import com.lookie.opluskey.ui.ModeMenuTarget
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.COLUMNS
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.durationText
import com.lookie.opluskey.ui.model.tileIcon
import com.lookie.opluskey.ui.tickNow
import io.github.suqi8.coui.kmp.basic.SmallTitle
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
 *  **不能按 [ActionCatalog.GROUPS] 排**：清单按手势剔过条目（「灵感语音」只在长按），组大小
 *  却没少 ⇒ 最后一组会多吃进第一条自定义命令（真机反馈「单击 / 双击的自定义命令跑到快捷功能
 *  里了」）。分组与清单必须同源，见 `ActionCatalog.groupsFor`。
 *
 * 焦点格索引仍与 `st.specs` 下标一一对应（见 `BindingList` 里「扁平焦点索引的账」）——
 * 分组只是展示层切分，组里的格子用的还是**全局**下标（`builtInBase` 跨组连续累计），
 * 不能在组内从 0 数。
 */
@Composable
internal fun SettingsPage.BuiltInGrid(
    st: BindingGridState,
    enabled: Boolean,
) {
    val groups = remember { ActionCatalog.groupsFor() }
    var builtInBase = 0
    groups.forEachIndexed { groupIndex, (groupTitle, group) ->
        // 「上一段 → 这个标题」之间给一档（与网格行距同值），与下面「自定义命令」那一段同一条规矩。
        // 第一组不加 —— 它前面那一段（按键设置卡）已经自带收尾间距，双击档则是页首、不需要。
        if (groupIndex > 0) Spacer(Modifier.height(GRID_ROW_GAP))
        SmallTitle(ActionCatalog.groupTitleLabel(groupTitle))

        // 这一段用一个**内层 Column** 单独带 `PAGE_MARGIN` 横向 padding，
        // 标题在外层不受影响。
        Column(
            Modifier.padding(horizontal = PAGE_MARGIN),
            verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
        ) {
            for (rowIndices in (builtInBase until builtInBase + group.size).chunked(COLUMNS)) {
                Row(
                    Modifier.fillMaxWidth(),
                    // 上下左右间距保持一致；不再给焦点圈留补偿（见 `GRID_GAP` 的注释）
                    horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                ) {
                    rowIndices.forEach { index ->
                        BuiltInTile(st, index, enabled, Modifier.weight(1f))
                    }
                    // 最后一行不满时补空位：不补的话剩下那格会被 weight 撑到整行宽
                    repeat(COLUMNS - rowIndices.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        builtInBase += group.size
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
        // 登记给确认键那条路用（见 `BindingGridState.tileAnchors`）—— 写的是普通 map、不是 state。
        st.tileAnchors[spec.token] = tileAnchor
        val current = st.target.actionIn(configState.config.value)
        //  入口格（点了弹二级菜单的那几格）：`modes` 非空。这一格绑在哪一档也从这里算一次
        //  —— **小字与小字前面那枚图标共用它**，同源就不会各说各的。
        val modes = ActionCatalog.modeMenu(spec.token, withLongOnly = st.g == Gesture.LONG)
        val popsMenu = modes != null
        val boundMode = modes?.firstOrNull { ActionCatalog.isBound(it, current) }
        val boundAppLabel = rememberBoundAppLabel(current)
        ActionTile(
            spec = spec,
            //  入口格的行首**随当前档位变**（主屏幕 / 静音 / 上一首 …）；
            //  一档都没绑时退回这一格自己的大类字形（媒体控制 → 音符、响铃状态 → 铃铛…）。
            //  「打开应用」格再细一档：绑成什么样就画什么（小窗 / 快捷方式 / 某个界面 / 整个应用）——
            //  这一格以前靠标题右边那枚「小窗起」标记，现在标记没了，信息全在这一颗图标上。
            //  只对「打开应用」那一格生效：`appTileIcon` 看的是**当前绑定**，而每格的
            //  `current` 都是同一个动作 —— 不判格子就会让整片网格（导航 / 响铃 / 媒体…）
            //  全画成「打开应用」那一族的字形。
            icon =
                (if (spec.token == ActionCatalog.APP_TILE) appTileIcon(current) else null)
                    ?: boundMode?.let { tileIcon(it) }
                    ?: tileIcon(spec),
            selected = ActionCatalog.isSelected(spec, current),
            focused = index == st.focusedIndex,
            focusRequester = st.focusers[index],
            //  锚点**每格一份**：取值格点了不弹浮层，所以只有会弹菜单的格子才挂（`popsMenu`），
            //  其余不白记坐标。
            modifier = if (popsMenu) cellModifier.preciseClickAnchor(tileAnchor) else cellModifier,
            //  可用性与 `pick` 同一判据（主开关 + 链路就绪）：少判一道就会
            //  「亮着、点了被拦」—— 点了只弹一句提示，不如先灰着。
            enabled = enabled && linkReady,
            // 「打开应用」格与入口格都要补一行小字：前者是绑的应用名，
            // 入口格那格只写组名，不点开菜单根本看不出绑的是哪一档。
            subtitle =
                when (spec.token) {
                    ActionCatalog.APP_TILE -> {
                        boundAppLabel
                    }

                    //  系统开关那一格再细一档：「WIFI · 打开」/「NFC · 切换」—— 只写开关名时
                    //  看不出绑的是哪一档（三档里连「切换」也要写，不然它和只写名字没区别）。
                    //  **必须判格子**：`switchStateLabel` 只看「当前绑的是什么」，而 `current` 每一格
                    //  都相同 —— 不判的话绑一次「打开」，整片网格的每一格都会挂上「打开」。
                    ActionCatalog.SWITCH_TILE -> {
                        listOfNotNull(boundMode?.label, ActionCatalog.switchStateLabel(current))
                            .joinToString(" - ")
                            .ifEmpty { null }
                    }

                    else -> {
                        boundMode?.label
                    }
                },
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
    Column(
        Modifier.padding(horizontal = PAGE_MARGIN),
        verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
    ) {
        if (st.specs.size <= st.builtInCount) {
            CreateCommandRow(enabled, onCreateCommand)
        } else {
            // 焦点索引 `[builtInCount, totalCount)` —— 与 `specs` 下标一一对应
            for (rowIndices in (st.builtInCount until st.totalCount).chunked(COLUMNS)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                ) {
                    rowIndices.forEach { index ->
                        CustomTile(st, index, enabled, commandsByToken, Modifier.weight(1f))
                    }
                    repeat(COLUMNS - rowIndices.size) {
                        Spacer(Modifier.weight(1f))
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
    //  这一段是按**位次**排出来的（`chunked(COLUMNS)` 之后按下标取），而自定义命令会增删、
    //  会改名重排：不按 token 认身份，格子里的状态（焦点圈、进出场）就跟着位次挪到下一条命令身上。
    key(spec.token) {
        // 这条命令本身（`cmd:<名字>` 反查）—— 小字与「进编辑」两处都要用
        val cmd = commandsByToken[spec.token]
        val current = st.target.actionIn(configState.config.value)
        val selected = ActionCatalog.isSelected(spec, current)
        // 「进这条命令的编辑页」—— 长按、以及**已选中时再点一次**，共用这一个入口。
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
                //  静默 return 的话「长按没反应」在日志里没有凭据 —— 至少说清是找不到那条命令
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
            // 长按与「已选中时再点一次」同一个去处（上面那个 `openEditor`）。
            // 长按之后那一下抬手由库的 `combinedClickable` 吞掉（不会再触发点击）。
            onLongClick = openEditor,
            // **已经选中的再点一次 = 「我要改它」**，不是「重新绑一遍」，
            // 所以走编辑；没选中时才是「选中绑定」。
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
        //  「灵感语音」只在长按这一页出现（它起的是麦克风采集，短按一下开录没有语义）
        target = st.tileMenu?.spec?.let { ModeMenuTarget(it, withLongOnly = st.g == Gesture.LONG) },
        current = current,
        //  走 `pick` 而**不是** `bindingSelect`：菜单里的取值不在 `specs` 里，
        //  `select` 那一步的 `specs.indexOf(spec)` 会取到 −1、把焦点圈甩到第一格去。
        //  圈落回**被点开的那一格入口格**（本来就是点它才弹出的菜单），也是当场就挪 ——
        //  不靠「选中项变了再由 effect 收回来」（那条路真机上没跑起来，见 `bindingSelect`）。
        onPick = { spec ->
            st.tileMenu?.spec?.let { st.focusOn(it.token) }
            pick(spec, st.target)
        },
        onDismiss = { st.tileMenu = null },
    )
}

/**
 * 一个绑定目标在网格里显示的那行字（`微信 1` / `扫一扫`）。
 *
 *  优先级只有一处（`appNameOf`）：存下来的名字 → 老配置 token 里的 `|名字` → 现查 → 包名。
 *  这里只留一个转发，是因为调用点读起来要的是「这格该写什么字」，不是「名字怎么来的」。
 */
private fun SettingsPage.boundAppText(target: String): String = appNameOf(target)

/**
 * 「打开应用」格的行首字形 —— 绑成哪一档就画哪一颗；绑的是**整个应用**时返回 `null`，
 * 由调用点退回格子自己的默认字形。
 *
 *  **只有三档**（「小窗」不参与：它只说明「用哪种方式起」，不是「绑到了什么」——
 *  以前它单独占一档，于是 `window:包名/类名` 会被画成小窗、看不出绑的是某个界面）：
 *  · 快捷方式（`appshortcut:`，带不带 `w:` 都一样）→ 表格 + 箭头那颗；
 *  · 应用里某个具体界面（`包名/类名`，`app:` 与 `window:` 都算）→ 屏幕；
 *  · 整个应用（纯包名）→ 不覆盖，用默认那颗。
 *
 *  单独一支只为让 `BuiltInTile` 别顶破 detekt 的圈复杂度上限。
 */
private fun appTileIcon(action: Action): ImageVector? =
    when (action) {
        is Action.AppShortcut -> {
            PageIcons.ShortcutMark
        }

        is Action.App, is Action.AppWindow -> {
            if (AppTarget.activity(action.appTarget.orEmpty()) != null) PageIcons.ActivityScreen else null
        }

        else -> {
            null
        }
    }

/**
 * 「打开应用」格选中时标题下面那行小字 = **当前绑的是哪一个**。
 *
 *  · 应用 / 某个界面：应用名（`微信`）；
 *  · 快捷方式：`应用名: 快捷方式名`（`微信: 扫一扫`）—— 光写应用名的话，
 *    绑的是扫一扫还是收付款在网格里看不出来。
 *
 *  名字查一次就缓存（PM 查询，入参是动作所以只在换动作 / 名字表变了时重查）。
 *  少认一种时那一格就没有小标题（实证：开着小窗挑完应用回来，标题下面是空的）。
 */
@Composable
private fun SettingsPage.rememberBoundAppLabel(current: Action): String? =
    remember(current, configState.config.value.appNames) {
        when (current) {
            is Action.App, is Action.AppWindow -> current.appTarget?.let { boundAppText(it) }

            // 快捷方式（`appshortcut:`）也算「打开应用」那一族，小字写「应用: 快捷方式」
            is Action.AppShortcut -> shortcutTitle(current)

            else -> null
        }
    }

/**
 * 快捷方式那一档的小字：`微信: 扫一扫`。
 *
 *  快捷方式**自己的名字**只存在 `Config.appNames`（键是身份，挑的时候与绑定同一次落盘）——
 *  token 里没有它，现查也查不到（`appNameOf` 对快捷方式目标会退回**应用名**）。
 *  所以拿不到名字、或者拿到的就是应用名时**只写应用名**，别写成「微信: 微信」。
 *
 *  应用名那一边带上用户前缀：分身（`999@`）要在名字后面缀「2」，走 `appNameOf` 才认得出。
 */
private fun SettingsPage.shortcutTitle(action: Action.AppShortcut): String {
    val app = boundAppText(AppTarget.format(action.pkg, action.userId))
    val own = configState.config.value.appName(action.plainTarget)
    return if (own.isNullOrBlank() || own == app) app else "$app: $own"
}
