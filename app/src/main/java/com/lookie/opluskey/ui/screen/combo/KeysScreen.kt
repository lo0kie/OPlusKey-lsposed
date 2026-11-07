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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.MultiPickerDialog
import com.lookie.opluskey.ui.PageCornerFab
import com.lookie.opluskey.ui.PageFabButton
import com.lookie.opluskey.ui.PageScrollColumn
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.TitleCounter
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.binding.CollectKeyDialog
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.ComboEditState
import com.lookie.opluskey.ui.state.KeysEditState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「按键功能」页 —— **按键列表 + 组合按键列表**收在同一页，从首页「按键操作」卡里那一行进来。
//
// 上面是「这台机器上有哪几颗键」（点一颗进它的绑定页），下面是组合按键那一列
// （一档一行，点一行进编辑页 `Screen.COMBO_EDIT`；末行「添加组合按键」弹选键窗）。
// 两个清单本来就是同一个问题（有哪些「按键」可配），拆两处要在首页摆一列清单占掉半屏。

/**
 * 「按键功能」页 —— 两张清单上下排：先按键、后组合按键。
 *
 *  **组合那一列一行都没有时什么都不摆**（用户口径：那句「还没有组合按键」去掉）——只剩末行
 * 「添加组合按键」。这一页本来就只有两件事（这一列有哪几组 / 再加一组），
 * 空的时候「加一组」这一行自己就把话说完了，再补一句说明只是多一句废话。
 */
@Composable
internal fun SettingsPage.KeysScreen(scrollBehavior: ScrollBehavior) {
    val cfg = configState.config.value
    val writable = pageWritable()
    val atLimit = cfg.combos.size >= MAX_COMBOS
    val scroll: ScrollState = rememberScrollState()
    //  「添加按键」那一行弹的收集窗（等按键 → 起名字 → 落盘，见 `CollectKeyDialog`）。
    //  状态就住这一页：它是这一页上的一个浮层，不跨页存活。
    var collectPending by remember { mutableStateOf(false) }
    //  筛的那颗键已经不在任何组合里了（那一组被删 / 那颗键被删）⇒ 退回「全部」，
    //  否则回到这一页只会看到一张空表，还不知道是筛出来的。
    LaunchedEffect(ComboEditState.filterKey, cfg.combos) {
        val key = ComboEditState.filterKey
        if (key != null && cfg.combos.none { key in it.keys }) ComboEditState.filterKey = null
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
            //  第一块：按键列表（从首页搬来）—— 一颗键一行，点一行进那颗键的绑定页。
            //  行尾是**已用 / 上限**（与「命令名称」那行同一个 `TitleCounter`）：数的是收集来的
            //  那几颗（上限只管它们），侧键是内置的、不占额度。
            SectionTitle(
                text = I18n.get(Strings.HOME_KEYS_SECTION, "按键列表"),
                trailing = { TitleCounter(cfg.customKeys.size, KeyId.MAX_CUSTOM_KEYS) },
            )
            KeysListCard(writable) { collectPending = true }
            //  第二块：组合按键那一列（标题本身就是筛选入口，见 [ComboListSection]）
            ComboListSection(cfg, writable, atLimit)
        }
        //  **勾了任意一项就滑进这两颗**（用户口径）：编辑态、选中非空才出现，剩 0 项时自己滑出去。
        KeysEditFabs(
            visible = KeysEditState.editing && pickedCount > 0,
            turningOn = !pickedAnyEnabled(cfg),
            onToggle = { togglePickedEnabled() },
            onDelete = { confirmDelete = true },
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

    ComboKeysDialog(
        show = ComboEditState.pickKeysPending,
        onDismiss = { ComboEditState.pickKeysPending = false },
        onConfirm = { keys ->
            ComboEditState.pickKeysPending = false
            comboWriteGuard("添加组合键") { addCombo(keys) }
        },
    )
}

/**
 * 编辑态右下角那颗「删除」—— 位置与滑入滑出交给 [PageCornerFab]（与其它页的 FAB 同一份规格）。
 *
 *  显隐判据在调用点（编辑态 + 选中不为空）；它自己是颗实心圆，图标走 `onPrimary`。
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
                //  **必须 `compositeOver` 合一次**：COUI 暗色的层级色是半透明白（`secondaryContainer`
                //  一带），直接交给 FAB 就是真透明 —— 底下的卡片会透上来，看着像颗幽灵按钮。
                containerColor =
                    COUITheme.colorScheme.secondaryContainer
                        .compositeOver(COUITheme.colorScheme.surface),
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
                    tint = COUITheme.colorScheme.onSecondaryContainer,
                )
            }
            PageFabButton(onClick = onDelete) {
                Icon(
                    PageIcons.CommandDelete,
                    contentDescription = I18n.get(Strings.COMMON_DELETE, "删除"),
                    tint = COUITheme.colorScheme.onPrimary,
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
            if (builtinPicked) add(I18n.get(Strings.KEYS_DELETE_BUILTIN, "内置侧键不能删除，会从这一批里剔除"))
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
 *  成员数恒为 [ComboKey.MAX_KEYS]（两颗，用户口径）。判定侧其实认得更多成员（3 颗起的组合
 *  判定、执行、日志都已接线），但**界面上没有造它的入口**（用户口径未定）—— 所以勾满两颗
 *  之后第三颗勾不上，顺手弹条说「一个组合最多 2 颗按键」。
 *  （让用户勾出个三颗的组合、再在「添加」那颗灰按钮上告诉他不行，等于把同一个规矩说两遍。）
 *
 *  「为什么还不能添加」只有 [blockedReason] 一处判据：已存在 / 还没选够。
 *  不能确定时按钮是灰的，点一下弹**通用消息条**（不在弹窗里另画一行）。
 */
@Composable
private fun SettingsPage.ComboKeysDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<KeyId>) -> Unit,
) {
    val cfg = configState.config.value
    val keys = cfg.allKeys
    //  每次露出都从空选起：上一次点了取消还留着一半勾选，读起来像「上次已经选好了」
    var picked by remember(show) { mutableStateOf(emptySet<String>()) }
    //  这一组键已经有一档了 ⇒ 「添加」按不动：判定那边取第一档，第二档永远轮不到
    //  （同一个成员集合在 `Config.comboFor` 里按配置顺序取靠前的那个），加进去等于配一档死配置。
    val duplicate = picked.size >= ComboKey.MIN_KEYS && cfg.combos.any { it.keys.sorted() == picked.sorted() }
    //  「为什么还不能添加」只有一处判据：`null` = 可以加。点那颗灰按钮时由
    //  `MultiPickerDialog` 的 `onBlockedClick` 把它送进通用消息条 —— 灰着点了没反应最像「坏了」。
    //  颗数超出那一档**不在这里判**：勾满两颗之后第三颗勾不上（见 `onToggle`），走不到这儿。
    val blockedReason =
        when {
            duplicate -> I18n.get(Strings.COMBO_DUPLICATE, "这组按键已经添加过了")
            picked.size < ComboKey.MIN_KEYS -> I18n.format(Strings.COMBO_MIN_KEYS, ComboKey.MIN_KEYS)
            else -> null
        }

    //  样式走公共的多选选择器（`PagePicker`）：与「系统开关」那层三态选择器同一份行高、内缩、
    //  选中底色与对勾 —— 两处是同一类东西（弹窗里挑一列取值），不该各自一套。
    MultiPickerDialog(
        show = show,
        title = I18n.get(Strings.COMBO_PICK_KEYS, "选择按键"),
        //  标题下面那句「选 2 颗」去掉（用户口径）：一个组合就是两颗，标题「选择按键」已经够；
        //  真要选错颗数，勾满两颗之后第三颗勾不上时会有话说（见 `onToggle`）。
        hint = null,
        labels = keys.map { keyName(it) },
        selected = keys.indices.filter { keys[it].id in picked }.toSet(),
        onToggle = { index ->
            val key = keys.getOrNull(index) ?: return@MultiPickerDialog
            val on = key.id in picked
            //  勾满上限之后再点第三颗：**勾不上**，顺手弹条说为什么 —— 只允许两颗这件事
            //  在勾选那一步就说清，别让用户先勾出一个不成立的组合、再去猜「添加」为什么是灰的。
            //  取消勾选永远放行（那是往回收，不受上限管）。
            if (!on && picked.size >= ComboKey.MAX_KEYS) {
                hint(I18n.format(Strings.COMBO_MAX_KEYS, ComboKey.MAX_KEYS), slot = HINT_SLOT_COMBO)
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
