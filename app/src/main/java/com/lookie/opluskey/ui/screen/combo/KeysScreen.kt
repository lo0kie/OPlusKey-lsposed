package com.lookie.opluskey.ui.screen.combo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.present.keyName
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageCornerFab
import com.lookie.opluskey.ui.chrome.PageFabButton
import com.lookie.opluskey.ui.chrome.PageScrollColumn
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.dialog.MultiPickerDialog
import com.lookie.opluskey.ui.dialog.PickerOption
import com.lookie.opluskey.ui.dialog.SinglePickerDialog
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.TitleCounter
import com.lookie.opluskey.ui.screen.binding.CollectKeyDialog
import com.lookie.opluskey.ui.screen.binding.ComboModeAskDialog
import com.lookie.opluskey.ui.screen.binding.ComboStarterBlockedDialog
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.binding.ComboEditState
import com.lookie.opluskey.ui.state.binding.KeysEditState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「按键功能」页 —— **按键列表 + 组合按键列表**收在同一页，从首页「按键操作」卡里那一行进来。
//
// 上面是「这台机器上有哪几颗键」（点一颗进它的绑定页），下面是组合按键那一列
// （一档一行，点一行进编辑页 `Screen.COMBO_EDIT`；两张清单各自下面单起一张「添加…」卡）。
// 两个清单本来就是同一个问题（有哪些「按键」可配），拆两处要在首页摆一列清单占掉半屏。

/**
 * 「按键功能」页 —— 两张清单上下排：先按键、后组合按键。
 *
 *  每张清单与它那张「添加…」卡是**两张卡**（用户口径「所有的添加行都另起一个卡片」）：
 *  清单回答「现在有哪几个」，添加那张回答「怎么再加一个」。
 *
 *  **组合那一列一行都没有时什么都不摆**（用户口径：那句「还没有组合按键」去掉）——只剩那张
 * 「添加组合按键」卡。这一页本来就只有两件事（这一列有哪几组 / 再加一组），
 *  空的时候「加一组」这一行自己就把话说完了，再补一句说明只是多一句废话。
 */
@Composable
internal fun SettingsPage.KeysScreen(scrollBehavior: ScrollBehavior) {
    val cfg = configState.config.value
    val writable = pageWritable()
    val scroll: ScrollState = rememberScrollState()
    //  「添加按键」那一行弹的收集窗（等按键 → 起名字 → 落盘，见 `CollectKeyDialog`）。
    //  状态就住这一页：它是这一页上的一个浮层，不跨页存活。
    var collectPending by remember { mutableStateOf(false) }
    //  筛的那颗键已经不在任何组合里了（那一组被删 / 那颗键被删）⇒ 退回「全部」，
    //  否则回到这一页只会看到一张空表，还不知道是筛出来的。
    //  两段各清各的：那一列里没有它了才退回「全部」，另一列的筛选不受影响
    LaunchedEffect(ComboEditState.waitFilter, cfg.combos) {
        val key = ComboEditState.waitFilter
        if (key != null && cfg.combos.none { !it.isHold && key in it.keys }) ComboEditState.waitFilter = null
    }
    LaunchedEffect(ComboEditState.holdFilter, cfg.combos) {
        val key = ComboEditState.holdFilter
        if (key != null && cfg.combos.none { it.isHold && key in it.keys }) ComboEditState.holdFilter = null
    }

    //  编辑态：勾中的项数 + 删除确认窗开着没有（0 项时右下角那两颗不出现，见下）
    val pickedCount = KeysEditState.count
    var confirmDelete by remember { mutableStateOf(false) }
    //  勾中的键会**连带删掉**「用到它的」组合（少一颗键的组合是废配置，见 `removePicked`）：
    //  确认窗要把这个数报出来，别让人以为只少两颗键。已经勾上的组合不重复算。
    val cascadeCombos =
        cfg.combos.count { c -> c.id !in KeysEditState.pickedCombos && c.keys.any { it in KeysEditState.pickedKeys } }
    //  离开这一页时把编辑态收干净：下次进来是正常的一页，而不是顶栏写着「完成」、
    //  清单上还挂着一排勾。
    DisposableEffect(Unit) { onDispose { KeysEditState.clear() } }

    Box(Modifier.fillMaxSize()) {
        PageScrollColumn(scrollBehavior = scrollBehavior, state = scroll) {
            //  与其它页同一档：顶栏叠在内容上，顶部这一块自己让出来（动态值）。
            //  **不再另加 [PAGE_TOP_PADDING]**：这一页首行就是小标题，它自己带 16 + 8 的上间距
            //  （与首页、更多选项两页同一笔账）；再加一档就比别的页多出 8dp。
            Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))
            //  第一块：内置侧键那一颗（从首页搬来）—— 点一行进那颗键的绑定页。
            //  **与下面那块拆成两张卡**（用户口径）：内置侧键和收集来的键来路不同，
            //  额度也只限后者 —— 计数跟着「自定义按键」那块的小标题走，不再挂在「按键列表」上。
            SectionTitle(text = I18n.get(Strings.KEYS_SECTION_DEFAULT, "默认按键"))
            KeysListCard(writable)
            //  第二块：收集来的键 + 「添加按键」那一行。行尾是**已用 / 上限**（与「命令名称」那行
            //  同一个 `TitleCounter`）：上限只管收集来的这几颗，侧键是内置的、不占额度。
            SectionTitle(
                text = I18n.get(Strings.KEY_WATCH_TITLE, "自定义按键"),
                trailing = { TitleCounter(cfg.customKeys.size, KeyId.MAX_CUSTOM_KEYS) },
            )
            CustomKeysCard(writable) { collectPending = true }
            //  第二块：组合按键 —— **两列**（用户口径）：等待组合 / 按住组合，
            //  各有自己的筛选与上限（MAX_COMBOS 是每一列各算一次）。
            //  类别在添加那一刻定下来（ComboEditState.pendingKind），之后不可改。
            ComboListSection(cfg, writable, cfg.combos.count { !it.isHold } >= MAX_COMBOS, hold = false)
            ComboListSection(cfg, writable, cfg.combos.count { it.isHold } >= MAX_COMBOS, hold = true)
        }
        //  **勾了任意一项就滑进这两颗**（用户口径）：编辑态、选中非空才出现，剩 0 项时自己滑出去。
        //
        //  「删除」有一档做不到：勾的**只有内置快捷键**时它删不掉（`removePicked` 会把它滤掉）。
        //  以前那颗照样能按、先弹确认窗再回一句「不能删除」—— 两步空转，窗口里还写着「将删除 0 项」；
        //  现在**不弹窗**，点一下直接说原因（判据与 `removePicked` 里那道过滤同一条）。
        //  这一颗的外观不跟着变（用户口径「不要灰色」）：灰掉之后既不像能用也不像不能用。
        val deletablePicked =
            (KeysEditState.pickedKeys - KeyId.PLUS.id).isNotEmpty() || KeysEditState.pickedCombos.isNotEmpty()
        KeysEditFabs(
            visible = KeysEditState.editing && pickedCount > 0,
            turningOn = !pickedAnyEnabled(cfg),
            onToggle = { togglePickedEnabled() },
            onDelete = {
                if (deletablePicked) {
                    confirmDelete = true
                } else {
                    hint(I18n.get(Strings.KEYS_BUILTIN_KEEP, "内置快捷键不能删除"), slot = "keys_del")
                }
            },
        )
    }

    //  删除是不可撤销的（那几项的绑定一起没），所以先问一句 —— 与「删除组合按键」同一档规格。
    //  勾了内置侧键时要在正文里说明它会被**剔除**：它在编辑态里能勾（用户口径），但删不掉，
    //  不提前说清，用户会以为「勾了却没删干净」。
    KeysDeleteConfirm(
        show = confirmDelete,
        count = pickedCount,
        cascadeCombos = cascadeCombos,
        builtinPicked = KeyId.PLUS.id in KeysEditState.pickedKeys,
        onDismiss = { confirmDelete = false },
        onConfirm = {
            confirmDelete = false
            //  **删成了才退出编辑态**：写盘被拒时那一排勾删的还在，退出编辑态等于把它们
            //  当成「已经删掉」，再进编辑态得重新勾一遍。
            if (removePicked()) {
                //  删完退出编辑态：这一趟的活干完了，留着一排勾没有下一步
                KeysEditState.toggleEditing()
            }
        },
    )

    //  编辑态里按返回：**先问一句**再退页（判据与置位在 `SettingsPage.goBack`）——
    //  这一退会把勾选整个丢掉，而「勾了几项、本来要删什么」是刚做过的活。
    KeysLeaveEditConfirm()

    //  启用 / 停用时那一问「连带的键与组合怎么办」（状态在 `KeysAsk`）
    KeysAskDialog()

    CollectKeyDialog(
        show = collectPending,
        onDismiss = { collectPending = false },
    )

    //  按住组合那一类：选完键还要问「先按住第一颗还是第二颗」—— 顺序不同就是两档
    var holdOrder by remember { mutableStateOf<List<KeyId>?>(null) }

    ComboKeysDialog(
        show = ComboEditState.pendingKind != null,
        hold = ComboEditState.pendingKind?.let { it != ComboKey.MODE_WAIT } == true,
        onDismiss = { ComboEditState.pendingKind = null },
        onConfirm = { keys ->
            val kind = ComboEditState.pendingKind
            ComboEditState.pendingKind = null
            when (kind) {
                null -> Unit
                ComboKey.MODE_WAIT -> comboWriteGuard("添加等待组合") { addCombo(keys, starter = null) != null }
                else -> holdOrder = keys
            }
        },
    )

    //  按住组合只差最后一步：**先按住哪一颗**（两种顺序各是一档）。
    //
    //  **窗口不能跟着数据一起消失**：点中 / 取消时 `holdOrder` 立刻回 `null`，若这一窗的开关直接
    //  就是 `order != null`，它会在同一帧从组合里被摘掉 —— 那一下是硬切，退场动画根本来不及播
    //  （用户口径「触发方式的 dialog 没有出场动画」）。所以这里留一份**比数据慢一格**的状态：
    //  数据（`holdOrder`）空了只代表「关窗请求」，`modeShown` 等窗自己播完退场再翻下去。
    //  画的内容照**最后画过的那一份**（`LastHoldOrder`），否则退场那两百毫秒里行名会先变空。
    val order = holdOrder
    val orderLast = remember { LastHoldOrder() }
    if (order != null) orderLast.value = order
    var modeShown by remember { mutableStateOf(false) }
    //  **合成式而不是 `LaunchedEffect`**：`LaunchedEffect` 要等这一帧画完才跑，而点选那一帧
    //  `order` 已经空了 —— 中间会先组合出一次「没有东西可画」，整支 `if` 塌掉、窗跟着被摘，
    //  退场照样播不出来。合成式当场置位，同一个组合过程里 `shown` 就接得上。
    if (order != null && !modeShown) modeShown = true
    val shown = (order ?: orderLast.value).takeIf { modeShown }
    if (shown != null && shown.isNotEmpty()) {
        val names = shown.map { keyName(it) }
        //  **每个成员当首键就是一种选项**（用户口径）：三颗时列三行 ——
        //  「按住 A 后再按 B、C」「按住 B 后再按 A、C」「按住 C 后再按 A、B」。
        //  模板按成员数现算（`holdSentenceTemplate`），每一行的名字串都把那一颗排到最前。
        val template = holdSentenceTemplate(names.size - 1)
        //  这一组键里**已经当过首键**的那几颗 —— 用过的那一行置灰：同一种按法只能有一档
        //  （再来一档就是死配置，判定侧只会取配置里靠前的那个）。判据与 `comboRoomLeft` 同源。
        val ids = shown.map { it.id }
        val used = remember(cfg.combos, ids) { usedComboStarters(cfg, ids) }
        SinglePickerDialog(
            show = order != null,
            title = I18n.get(Strings.COMBO_MODE_TITLE, "触发方式"),
            options =
                shown.mapIndexed { at, key ->
                    //  这一行：第 `at` 颗当首键，其余按原顺序跟成后续成员
                    val ordered = listOf(names[at]) + names.filterIndexed { i, _ -> i != at }
                    PickerOption(template, enabled = key.id !in used, chips = ordered)
                },
            //  这一窗是「新建一档」，不是在改某个已经选中的值 —— 哪一行都不打勾
            selectedIndex = -1,
            onDismiss = { holdOrder = null },
            onDismissFinished = {
                modeShown = false
                orderLast.value = null
            },
            onPick = { index ->
                holdOrder = null
                val starter = shown.getOrNull(index)
                comboWriteGuard("添加按住组合") { starter != null && addCombo(shown, starter) != null }
            },
            //  置灰那一行点下去**要有话说**（用户口径「禁用的条目点击没有消息提示」）：
            //  一句「这种触发方式已经添加过了」就够 —— 用户点的就是那一行，
            //  再把它的名字念一遍（「先按住第一颗 这种…」）是把同一件事说两次。
            onDisabledPick = {
                hint(I18n.get(Strings.COMBO_MODE_ALREADY, "这种触发方式已经添加过了"), slot = HINT_SLOT_COMBO)
            },
        )
    }

    //  「起点键得先改成松手时触发」那一问 —— 加按住组合时才会弹（`addCombo` 发现起点键不合规，
    //  只把问题记在 `ComboModeAsk` 上，**那一档此时还没进配置**）。
    //  **必须挂在这一页**：添加动作发生在这里，没有这一行就没人画那张窗 ——
    //  那一档会静悄悄地不生效（或者说，用户点了添加却什么都不会发生）。
    ComboModeAskDialog()
    //  起点键「只参与组合」那一道拦截 —— 同一处添加动作，同样的挂法（见 `ComboStarterBlocked`）
    ComboStarterBlockedDialog()
}

/**
 * 「触发方式」那一窗退场期间要画的那几颗键（见 [KeysScreen]）。
 *
 *  窗的开关是「数据在不在」，而点中一行时数据**当场**就收了 —— 没有这一格的话，退场那两百
 *  毫秒里行上的键名先变空（「按住  后再按  」）。所以照**最后画过的那一份**画，与
 *  `CommonCommandsScreen` 的 `LastCommand`、`DonateDialog` 的 `Last` 同一个理由。
 */
private class LastHoldOrder {
    var value: List<KeyId>? = null
}

/**
 * 编辑态右下角那颗「删除」—— 位置与滑入滑出交给 [PageCornerFab]（与其它页的 FAB 同一份规格）。
 *
 *  显隐判据在调用点（编辑态 + 选中不为空）；它自己是颗实心圆，图标走 `onPrimary`。
 *
 *  「只勾了内置快捷键」时删除做不到（它被 `removePicked` 滤掉），但这一颗**外观一律不变**：
 *  不做置灰 —— 灰掉之后它既不像能按、也不像真的禁用，还比旁边那颗（停用 / 启用）暗一档，
 *  反倒像坏了；做不到的原因交给点下去那一句（用户口径「不要灰色」）。判据在调用点，它只负责画。
 */
@Composable
private fun BoxScope.KeysEditFabs(
    visible: Boolean,
    /** 这一下是「启用」还是「停用」（见调用点的 `pickedAnyOn`）。 */
    turningOn: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    PageCornerFab(visible = visible) {
        //  两颗竖着排：上面「停用 / 启用」、下面「删除」—— 删除是破坏性的，压在离手更远的那一格
        //  （拇指扫过时先碰到的是开关）。
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(KEYS_FAB_GAP),
        ) {
            PageFabButton(
                onClick = onToggle,
                //  **主题色**（用户口径：这一颗原来是灰底，读不出「可以按」）：`primary` 与
                //  「确认」那一族同一个色号；不再走 `secondaryContainer` —— 那一档在暗色下是
                //  半透明白，合过 `surface` 之后就是一片灰。
                containerColor = COUITheme.colorScheme.primary,
            ) {
                //  **图标跟着这一下要做什么，而不是「现在是什么状态」**（之前正好接反了：
                //  该停用时画对勾、该启用时画叉）。启用用 `ToggleRight`（开关语义），
                //  不用对勾 —— 对勾在这一版界面里表示「已选中」，摆在「启用」上会被读成勾选。
                Icon(
                    if (turningOn) PageIcons.MasterSwitch else PageIcons.Cancel,
                    contentDescription =
                        if (turningOn) {
                            I18n.get(Strings.KEYS_ENABLE, "启用")
                        } else {
                            I18n.get(Strings.KEYS_DISABLE, "停用")
                        },
                    tint = COUITheme.colorScheme.onPrimary,
                )
            }
            PageFabButton(
                onClick = onDelete,
                //  **危险色**（用户口径）：删除是不可撤销的，用 `error` 与上面那颗主题色分开 ——
                //  两颗都是实心圆时只靠字形区分，拇指扫过来先看到的颜色才是那道保险。
                containerColor = COUITheme.colorScheme.error,
            ) {
                Icon(
                    PageIcons.CommandDelete,
                    contentDescription = I18n.get(Strings.COMMON_DELETE, "删除"),
                    //  `onError` 而不是写死白色：Monet 深色档的 `error` 是一档浅红，配白字会糊
                    //  （与确认窗那颗红色按钮同一对取色）。
                    tint = COUITheme.colorScheme.onError,
                )
            }
        }
    }
}

/** 两颗 FAB 之间那一档间距 —— 12dp（与页面里卡片之间同一档，看得清是两颗独立按钮）。 */
private val KEYS_FAB_GAP = 12.dp

/**
 * 删掉选中那几项之前的确认窗 —— 那几项的绑定一起没，删了不能撤销。
 *
 *  勾了键时还要说清**连带的组合按键**（见 [removePicked]：少一颗键的组合会被一起删掉）——
 *  只在真有组合被带走时才多出那一句，没勾键 / 没有组合受影响时正文仍是一句话。
 *
 * @param count 选中几项（正文里报出来：确认窗要让人看清「这一下会删掉几条」）
 * @param cascadeCombos 顺带被删掉的组合按键数（`0` = 不写那一句）
 * @param builtinPicked 勾里含内置侧键 —— 它删不掉，要提前说明这一项会被剔除
 */
@Composable
private fun SettingsPage.KeysDeleteConfirm(
    show: Boolean,
    count: Int,
    cascadeCombos: Int,
    builtinPicked: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    //  内置侧键不计进「将删除 N 项」：它删不掉，勾上它只是被剔除（下面单独一句说明）
    val effective = if (builtinPicked) (count - 1).coerceAtLeast(0) else count
    val body = I18n.format(Strings.KEYS_DELETE_CONFIRM, effective)
    //  段与段用空行切开（`EmphasisText` 的分段规则）：连带影响、剔除项各自单独一段，读得清
    val notes =
        buildList {
            if (cascadeCombos > 0) add(I18n.format(Strings.KEYS_DELETE_COMBOS, cascadeCombos))
            if (builtinPicked) add(I18n.get(Strings.KEYS_DELETE_BUILTIN, "内置快捷键不能删，已从这一批剔除"))
        }
    ConfirmDialog(
        show = show,
        title = I18n.get(Strings.KEYS_DELETE_TITLE, "删除选中的项目"),
        summary = (listOf(body) + notes).joinToString("\n\n"),
        action = I18n.get(Strings.COMMON_DELETE, "删除"),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * 编辑态里按返回那一问 —— 「退出编辑？」。
 *
 *  置位在 [SettingsPage.goBack]（它同时**拦住了这一退**），窗画在这里：判据留在页面那一层，
 *  长什么样归这一页。确认之后**只退出编辑态**、人留在这页 —— 先把那一份收干净
 *  （[KeysEditState.clear]，勾选一起清），不再调 [goBack]：这一退本来就不该把人带走。
 *
 *  不危险动作：退出编辑只是丢掉勾选，不动配置，所以动作项**不是**红的那一档。
 */
@Composable
private fun SettingsPage.KeysLeaveEditConfirm() {
    ConfirmDialog(
        show = overlays.confirmLeaveEdit,
        title = I18n.get(Strings.KEYS_LEAVE_EDIT_TITLE, "退出编辑？"),
        //  正文报个数（`%1$d`）：这一问只在勾了东西时出现，写清丢的是哪几项，比一句「会被清空」有用
        summary = I18n.format(Strings.KEYS_LEAVE_EDIT_DESC, KeysEditState.count),
        action = I18n.get(Strings.KEYS_LEAVE_EDIT_OK, "退出编辑"),
        positiveDanger = false,
        onDismiss = { overlays.confirmLeaveEdit = false },
        onConfirm = {
            overlays.confirmLeaveEdit = false
            KeysEditState.clear()
        },
    )
}

/**
 * 「选择按键」弹窗 —— 这一档组合由哪几颗键组成。
 *
 *  成员数在 [ComboKey.MIN_KEYS]..[ComboKey.MAX_KEYS]（两颗到三颗）之间。两颗是最常见的那一档，
 *  三颗是「三键齐按」—— 判定侧那条多成员路径（`ComboDetector.multiFor`）就是为它接的。
 *  所以勾满三颗之后第四颗勾不上，顺手弹条说「一个组合最多 3 颗按键」。
 *  （让用户勾出个四颗的组合、再在「添加」那颗灰按钮上告诉他不行，等于把同一个规矩说两遍。）
 *
 *  「为什么还不能添加」只有 [blockedReason] 一处判据：已存在 / 还没选够。
 *  不能确定时按钮是灰的，点一下弹**通用消息条**（不在弹窗里另画一行）。
 */
@Composable
private fun SettingsPage.ComboKeysDialog(
    show: Boolean,
    /** true = 这一趟加的是「按住组合」（选完键再问「按住哪一颗」——每个成员都能当首键）。 */
    hold: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<KeyId>) -> Unit,
) {
    val cfg = configState.config.value
    val keys = cfg.allKeys
    //  每次露出都从空选起：上一次点了取消还留着一半勾选，读起来像「上次已经选好了」
    var picked by remember(show) { mutableStateOf(emptySet<String>()) }
    //  这一组键在**这一类里**还有没有没占的触发方式：等待组合一档、按住组合两档（两种顺序）。
    //  判据与 `addCombo` 的去重同一条（`comboAllModesTaken`）。
    //  两类都能选到 [`ComboKey.MAX_KEYS`] 颗：按住那一档不再是「只两颗」——首键一颗 +
    //  后续成员任意多颗（上限与等待组合同一个数）。
    val maxKeys = ComboKey.MAX_KEYS
    val pickedIds = picked.toList()
    val allModesTaken = picked.size >= ComboKey.MIN_KEYS && comboAllModesTaken(cfg, pickedIds, hold)
    //  「为什么还不能添加」只有一处判据：`null` = 可以加。点那颗灰按钮时由
    //  `MultiPickerDialog` 的 `onBlockedClick` 把它送进通用消息条 —— 灰着点了没反应最像「坏了」。
    //  颗数超出那一档**不在这里判**：勾满两颗之后第三颗勾不上（见 `onToggle`），走不到这儿。
    val blockedReason =
        when {
            //  按住那两种顺序都加过时换一种说法（用户口径「要改成所有触发都已添加」）：
            //  从这一列的「添加」进来，用户面对的就是「按住组合」这一类，全占满就是这一类没得加了。
            //  等待组合只有一档，它已存在时仍是「这组按键已经添加过了」——那种情况下另一类
            //  往往还能加，说「所有」不准确。
            allModesTaken && hold -> I18n.get(Strings.COMBO_ALL_MODES_TAKEN, "所有触发方式都已添加")

            allModesTaken -> I18n.get(Strings.COMBO_DUPLICATE, "这组按键已经添加过了")

            picked.size < ComboKey.MIN_KEYS -> I18n.format(Strings.COMBO_MIN_KEYS, ComboKey.MIN_KEYS)

            else -> null
        }

    //  样式走公共的多选选择器（`PagePicker`）：与「系统开关」那层三态选择器同一份行高、内缩、
    //  选中底色与对勾 —— 两处是同一类东西（弹窗里挑一列取值），不该各自一套。
    MultiPickerDialog(
        show = show,
        title = I18n.get(Strings.COMBO_PICK_KEYS, "选择按键"),
        //  标题下面那句「选 2 颗」去掉（用户口径）：标题「选择按键」已经够，颗数靠勾选时的提示说；
        //  真要选错颗数，勾满上限之后下一颗勾不上时会有话说（见 `onToggle`）。
        hint = null,
        labels = keys.map { keyName(it) },
        selected = keys.indices.filter { keys[it].id in picked }.toSet(),
        onToggle = { index ->
            val key = keys.getOrNull(index) ?: return@MultiPickerDialog
            val on = key.id in picked
            //  勾满上限之后再点下一颗：**勾不上**，顺手弹条说为什么 —— 上限这件事
            //  在勾选那一步就说清，别让用户先勾出一个不成立的组合、再去猜「添加」为什么是灰的。
            //  取消勾选永远放行（那是往回收，不受上限管）。
            if (!on && picked.size >= maxKeys) {
                //  上限按类说（见 `comboMaxKeysHint`）：等待组合 3 颗、按住组合 2 颗
                hint(comboMaxKeysHint(hold), slot = HINT_SLOT_COMBO)
            } else {
                picked = if (on) picked - key.id else picked + key.id
            }
        },
        confirmText = I18n.get(Strings.COMBO_ADD_CONFIRM, "添加"),
        confirmEnabled = blockedReason == null,
        //  点那颗灰按钮 → 弹**通用消息条**（不在弹窗里另画一行）：被拦住的说法全应用只有一种形态。
        //  没原因就是真按不动（不该有这种情况，兜住而已）。
        onBlockedClick = blockedReason?.let { reason -> { hint(reason, slot = HINT_SLOT_COMBO) } },
        onDismiss = onDismiss,
        onConfirm = { onConfirm(keys.filter { it.id in picked }) },
    )
}
