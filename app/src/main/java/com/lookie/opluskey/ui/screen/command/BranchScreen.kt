package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.core.model.comboLabel
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.Feedback
import com.lookie.opluskey.ui.HINT_SLOT_DISABLED
import com.lookie.opluskey.ui.HINT_SLOT_PAGE
import com.lookie.opluskey.ui.PageCornerFab
import com.lookie.opluskey.ui.PageFabButton
import com.lookie.opluskey.ui.PageScrollColumn
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.state.BranchDraft
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「系统场景」页 —— 从「按键绑定」页顶栏 ⋮ 菜单进来。
//
//  **场景不是命令库的一条命令**：它是叠在「按键绑定」之上的一张**顺序表**（`KeyConfig.scenes`），
//  按下的那一刻从上往下找，第一条「条件成立、且这一档有值」的块就执行它那一档的动作；一块都不中，
//  才执行「按键绑定」里这一档原本绑的命令。所以这一页没有名字、不进「已保存的命令」，也不占用任何绑定。
//
//  **一块 = 一组条件 + 完整的三档手势**：页面不再按手势分页（那是翻转之前的样子），
//  三档在每一块里各占一行（见 `BranchCards`）。

/**
 * 「系统场景」页 —— 编辑**当前这颗键（或这一档组合键）的整张场景表**。
 *
 *  ## 在编谁
 *
 *  键 = 从「按键绑定」页带过来的那一颗（`KeyWatchState.editing`）；从组合键编辑页进来时
 *  那几个入口会把 `branchDraft.comboId` 填上，于是读写的表换成 `combos`。两者一起构成
 *  [SettingsPage.branchOwnerOf]：换了任何一半都重新载入。
 *
 *  ## 为什么进页要重新载入
 *
 *  这一页的入口是「看这颗键现在有什么、再改」。草稿提到页面级是为了「挑应用」往返不丢，
 *  但**每次真正进页**都得按配置重灌一遍 —— 否则上一次编辑完没保存的那份会留在屏幕上。
 */
@Composable
internal fun SettingsPage.BranchScreen(scrollBehavior: ScrollBehavior) {
    val tick = rememberTick()
    val scroll = rememberScrollState()
    val draft = branchDraftNow()

    //  按配置重灌：判据是 owner 对不对（键 / 组合键）。从「选择应用」返回时本页会被重新组合
    //  （来源页的组合已注销，见 `AppPickerState` 的说明），owner 不变、而草稿里正是刚编的那一份
    //  —— 那时重灌会把它连同刚挑中的那一项一起冲掉。出栈时 `discardPageStateOf` 已经把草稿
    //  reset 成 `owner = null`，所以「重新进页」仍然会走到下面按配置重灌这一支。
    LaunchedEffect(branchOwnerOf()) {
        if (draft.owner != branchOwnerOf()) reloadBranchDraft()
    }

    //  右下角只有**一颗**「确认」，而且**只在有改动时出现**：没改过就没有要写回去的东西。
    //  「添加」在顶栏右上角（[BranchHeaderAdd]）—— 它是「往表里加一块」，不是这一页的主操作。
    //  检查统一挪到**点确认那一刻**（见 [BranchSaveFab]）。

    //  量一次视口高度：空态要**在可见区里居中**，而它在滚动容器里拿不到「一屏多高」——
    //  滚动容器的子项拿到的是无限高度约束，`fillMaxHeight()` 在那儿没有意义。
    BoxWithConstraints(Modifier.fillMaxSize()) {
        //  减掉滚动内容底部那档留白（[PAGE_BOTTOM_PADDING]，与首页同一档）：不减的话空态那块
        //  比可见区高出一截，居中的位置会偏下、还多出一段能拖的空白。
        val emptyHeight =
            (maxHeight - currentHeaderHeight(scrollBehavior) - PAGE_BOTTOM_PADDING)
                .coerceAtLeast(0.dp)
        PageScrollColumn(scrollBehavior = scrollBehavior, state = scroll) {
            Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))
            //  行的可点性（含「这颗键自己停用」那一道闸）由 `BranchRuleColumn` 就地判。
            BranchCards(draft = draft, tick = tick, emptyHeight = emptyHeight)
        }
        // 位置走全项目那一份（[PageCornerFab]），不在这里另算 —— 有改动才出现，进出带过渡
        PageCornerFab(visible = draft.dirty) { BranchSaveFab() }
    }
    // 改了还没保存就按返回时，`goBack` 会先置这一位 —— 这个窗必须挂上，否则返回被拦住却**没有窗**
    LeaveDiscardDialog(I18n.get(Strings.DIALOG_DISCARD_CONDITION_TITLE, "放弃未保存的场景"))
}

/**
 * 右下角那颗「确认」—— **有改动时才出现**，点下去那一刻才检查、才落盘。
 *
 *  页内的改动（加一块 / 换条件 / 换某一档的动作 / 上移下移删除）全是在改草稿：配置一个字节都不动。
 *  按这颗才把整张表写回去；**写成之后留在这一页**，只报一句「已保存系统场景」——
 *  落盘完草稿重载、`dirty` 归零，这颗自己滑出去。写盘失败也留在原地（否则用户以为存好了、
 *  其实一个字节都没写）。
 *
 * ## 检查放在点击里，不放在按钮外观上
 *
 *  两种「按了也不该落盘」各有各的说法，点一下由提示通道逐条说出来（同一语义位，不叠条）：
 *   1. 半成品（[branchRulesBlockedReason]）：空条件的块写下去就是一块永不成立的场景；
 *   2. **死分支**（[branchDeadReason]）：某一档条件的取值全在表里 ⇒ 它后面那几块永远轮不到。
 *
 *  按钮本身**不做禁用态**（也不换配色、更不用 `Modifier.alpha` —— alpha 会单独合成一层，
 *  把阴影裁进按钮方框里）。一个忽明忽暗的按钮，比「按下去被告知差什么」更难理解。
 */
@Composable
private fun SettingsPage.BranchSaveFab() {
    val tick = rememberTick()
    PageFabButton(
        onClick = {
            tick()
            val blocked = branchRulesBlockedReason() ?: branchDeadReason()
            if (blocked != null) {
                hint(blocked, slot = HINT_SLOT_DISABLED)
            } else if (saveScenes()) {
                hint(I18n.get(Strings.COMMAND_BRANCH_SAVED, "已保存系统场景"), slot = HINT_SLOT_PAGE)
            }
        },
    ) {
        Icon(
            PageIcons.Confirm,
            contentDescription = I18n.get(Strings.COMMON_CONFIRM, "确定"),
            tint = COUITheme.colorScheme.onPrimary,
        )
    }
}

/** 按配置把草稿重新载入（进页 / 换键时走这一支）。 */
private fun SettingsPage.reloadBranchDraft() {
    val draft = branchDraftNow()
    val comboId = branchDraft.comboId
    val cfg = configState.config.value
    if (comboId != null) {
        //  组合键那一档：同一套 `SceneRule`，只是从 `combos` 里取。
        //  找不到（在别处把这一组删了）就清空草稿 —— 留着上一趟的场景会让用户「编辑一组
        //  已经不存在的组合键」，点确认时还会把它写回去。
        val combo = cfg.combos.firstOrNull { it.id == comboId }
        if (combo == null) {
            XLog.w("系统场景：组合键 $comboId 已经不在了，草稿清空")
            draft.reset()
            return
        }
        XLog.d("系统场景：载入组合键 ${combo.id} 的 ${combo.scenes.size} 块")
        draft.load(BranchDraft.comboOwnerOf(combo.id), combo.scenes)
        return
    }
    val key = branchKeyNow()
    val source = cfg.key(key).scenes
    XLog.w("系统场景：载入 ${key.label} 的 ${source.size} 块")
    draft.load(BranchDraft.ownerOf(key), source)
}

/**
 * 「删掉这一块」的确认窗（触发点在行尾那颗「更多」菜单里，这里只负责画）。
 *
 *  为什么中间那颗菜单不当场删：这一页里「上移 / 下移」都能原样挪回来，只有删除是不可逆的，
 *  而那颗三点离右列（换动作）只隔几个 dp —— 点歪一下整块就没了。确认窗是这条路上唯一一道闸。
 */
@Composable
internal fun SettingsPage.BranchDeleteDialog(menus: BranchMenuState) {
    val at = menus.deleteAt
    ConfirmDialog(
        show = at >= 0,
        title = I18n.get(Strings.DIALOG_DELETE_CONDITION_TITLE, "删除场景"),
        summary = I18n.get(Strings.DIALOG_DELETE_CONDITION_MESSAGE, "这条场景和它的动作一起删掉。"),
        action = I18n.get(Strings.COMMON_DELETE, "删除"),
        onDismiss = { menus.deleteAt = -1 },
        onConfirm = {
            // 只**标出来**，不在这里删：行要先播完收缩（见 [BranchMenuState.deletingAt]），
            // 数据由 `BranchCards` 那个 effect 在动画结束时才移除。
            menus.deleteAt = -1
            menus.deletingAt = at
        },
    )
}

/**
 * 「停用这一块场景」的确认窗（触发点在行尾那颗「更多」菜单里）。
 *
 *  为什么也要一道确认：这一块停用之后**按下去不会再命中它**（三档一起）。与删除不是一回事
 *  （删除是它不见了），而两行在菜单里挨着 —— 一句确认把「暂时不生效」与「没了」分开。
 *
 *  **启用不走这里**（那是把它开回来，没有可失去的东西）：菜单里直接改草稿，
 *  与「按键绑定」「组合键编辑」那两处同一条规矩。
 *
 *  与删除的另一个区别：删除是**立即动数据**的（配合行的收缩动画），这里是**只改草稿**，
 *  还要再点右下角那颗「确认」才落盘 —— 这一页所有改动都是这个规矩（见 `BranchSaveFab`）。
 */
@Composable
internal fun SettingsPage.BranchToggleDialog(
    draft: BranchDraft,
    menus: BranchMenuState,
) {
    val at = menus.toggleAt
    val scene = draft.scenes.getOrNull(at)
    //  场景已经不在了（这一趟里删过）⇒ 窗不显示
    ConfirmDialog(
        show = scene != null,
        title = I18n.get(Strings.COMMAND_BRANCH_DISABLE, "停用场景"),
        summary = I18n.get(Strings.COMMAND_BRANCH_DISABLE_MESSAGE, "这条场景将不再生效。"),
        action = I18n.get(Strings.COMMON_DISABLE, "停用"),
        onDismiss = { menus.toggleAt = -1 },
        onConfirm = {
            menus.toggleAt = -1
            //  按下标去改是安全的：窗开着的这段里加不了也删不了另一块（菜单都收着），
            //  仍核一次「要改的还是不是它」—— 宁可不改，不能错改。
            draft.scenes.getOrNull(at)?.let { target ->
                draft.update(at, target.copy(enabled = false))
            }
        },
    )
}

/**
 * 「删掉一块里的**一条**条件」的确认窗（触发点在条件菜单的「删除」那一行）。
 *
 *  与 [BranchDeleteDialog] 分开：那个删的是**整块场景**（连它的三档动作一起消失、下面一行往上顶），
 *  这个只摘掉一块里的一条 —— 后果差得远，标题与正文都不能共用一套说法。
 */
@Composable
internal fun SettingsPage.BranchConditionDeleteDialog(menus: BranchMenuState) {
    val target = menus.deletingCond
    ConfirmDialog(
        show = target != null,
        title = I18n.get(Strings.DIALOG_DELETE_ONE_CONDITION_TITLE, "删除这条条件"),
        summary = I18n.get(Strings.DIALOG_DELETE_ONE_CONDITION_MESSAGE, "只删这一条。"),
        action = I18n.get(Strings.COMMON_DELETE, "删除"),
        onDismiss = { menus.deletingCond = null },
        onConfirm = {
            //  只**标出来**，不在这里删：那一行要先播完收起（与删整块同一个做法，见
            //  `BranchMenuState.exitingCond`），数据由 `BranchCards` 那个 effect 在动画结束时摘掉。
            menus.exitingCond = target
            menus.deletingCond = null
        },
    )
}

/**
 * 把场景表写回配置 —— 只替换**这一颗键（或这一档组合键）**的那一段。
 *
 *  与命令库那套不同：场景不占绑定，所以这里既不新增命令、也不碰别的键。
 *
 *  ## 写的是整张草稿
 *
 *  同一份草稿有两个入口在改：本页（整表）与「场景动作」页（从某一块点进去改那几档）。
 *  两边都按 `draft.scenes` 整表写回 —— 块与块之间没有稳定身份，无法安全地只替换其中一块
 *  （判据见 [scenesToWrite]）。别的键 / 别的组合键一个字节都不动，那一段仍按原样带过去。
 *
 *  ## 写成之后怎么收尾
 *
 *  写成之后怎么收尾，看**是谁点的**：
 *   · 本页那颗「确认」＝草稿与配置刚对齐 ⇒ [BranchDraft.load]，草稿与快照一起换成新的；
 *   · 「场景动作」页那颗 ⇒ 草稿本身已经就是刚写下去的那一份 ⇒ [BranchDraft.noteSaved]，
 *     只挪快照、不重发行身份，退回去时那几行不重播入场。
 */
internal fun SettingsPage.saveScenes(): Boolean {
    val draft = branchDraftNow()
    val scenes = scenesToWrite(draft)
    val cfg = configState.config.value
    val comboId = branchDraft.comboId
    if (comboId != null) {
        val combo = cfg.combos.firstOrNull { it.id == comboId }
        if (combo == null) {
            //  在别处把这一组删了：不能再往一个不存在的 id 上写（那等于凭空造一档组合键）
            XLog.w("系统场景：组合键 $comboId 已经不在了，本次不保存")
            return false
        }
        val next = cfg.copy(combos = cfg.combos.map { if (it.id != comboId) it else it.copy(scenes = scenes) })
        val label = comboLabel(cfg, combo)
        return when (write(next, "系统场景：$label（${scenes.size} 块）")) {
            CommandsSave.Failed -> {
                false
            }

            else -> {
                XLog.w("系统场景已保存：$label ${scenes.size} 块")
                //  「场景动作」页那颗：草稿已经等于写下去的这份 ⇒ 只挪快照、保住行身份；
                //  本页那颗：草稿与配置刚对齐 ⇒ 整份重灌（见上面那两段说明）
                if (screen == Screen.SCENE_BINDING) {
                    draft.noteSaved(scenes)
                } else {
                    draft.load(BranchDraft.comboOwnerOf(comboId), scenes)
                }
                true
            }
        }
    }
    val key = branchKeyNow()
    val next =
        cfg.copy(
            keys =
                cfg.keys.mapValues { (id, keyConfig) ->
                    if (id != key) keyConfig else keyConfig.copy(scenes = scenes)
                },
        )
    return when (write(next, "系统场景：${key.label}（${scenes.size} 块）")) {
        CommandsSave.Failed -> {
            false
        }

        else -> {
            XLog.w("系统场景已保存：${key.label} ${scenes.size} 块")
            //  与上面那一支同一条判据：在「场景动作」页只挪快照，在本页整份重灌
            if (screen == Screen.SCENE_BINDING) {
                draft.noteSaved(scenes)
            } else {
                draft.load(BranchDraft.ownerOf(key), scenes)
            }
            true
        }
    }
}

/**
 * 这一次要写下去的那张表 —— **整张草稿**。
 *
 *  ## 为什么不能按下标逐格合
 *
 *  配置里那一份（原表）与草稿之间**没有稳定的块身份**：`SceneRule` 只是一个条件集合 + 三档
 *  动作，`BranchDraft.ids` 只活在这一趟界面里、不落盘。所以草稿里删过 / 挪过块之后，
 *  「在编哪一块」那个下标在原表里指的可能已经是另一块 —— 替换它会把别的一块写没（丢块），
 *  或者把刚写的那块留在原位再补一份（复制块）。
 *
 *  整表写回不会丢块也不会复制块：写下去的就是屏幕上那张表本身，顺序与内容一一对应，
 *  根本不存在「按下标去找对应关系」这一步。代价是别的块若还有没确认的改动也会一起落盘 ——
 *  在拿不到块身份之前，那比写错一块安全。
 */
private fun SettingsPage.scenesToWrite(draft: BranchDraft): List<SceneRule> = draft.scenes.toList()

/**
 * 「一块都还没有」那块空态 —— **在滚动内容里**，跟着内容一起拖、一起弹。
 *
 *  说的是**此刻什么都不配时会发生什么**：按键绑定里各档原本绑的命令照常执行。表尾那句注脚
 *  讲的是同一件事，所以一块都没有时它不出现（见 `BranchCards`），这句话由空态来说。
 *
 *  它拿到的是 `emptyHeight`（可见区减顶栏、减底部留白）—— 那块高度由 `BranchCards` 给，
 *  这样它在可见区里居中，而整块仍在滚动内容里（拖动时会弹）。
 */
@Composable
internal fun SettingsPage.BranchEmptyHint() {
    Feedback(
        glyph = { tint ->
            Icon(PageIcons.NoMatch, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = tint)
        },
        //  上面一句说**状态**，下面一句说**现在会发生什么** —— 只有下面那句时，
        //  用户得先读完整句才知道这块是空的。
        title = I18n.get(Strings.COMMAND_BRANCH_EMPTY_TITLE, "还没有添加场景"),
        text = branchEmptyText(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 保存前还差什么（`null` = 可以存）。
 *
 *  **空表是合法的**：把最后一块删掉再按确定，等于「这颗键恢复成无条件」——
 *  存下去就是把场景表整段清掉，行为回落到按键绑定。所以这里不再把空表当成「还没配完」，
 *  否则最后一块永远删不掉，也没有别的入口能做这件事。
 *
 *  半成品只剩「条件没配完」一种：**三档动作可以留空**（= 这一档不参与，见 `SceneRule`），
 *  它不是没配完，而是「这一块不管这一档」。
 *
 *  「条件没配完」这一支同时管两件事：**整块还没选条件**（[SceneRule.conditions] 为空）与
 *  **选了要取值的档但取值还空着**（响铃状态 / 屏幕方向）。两种情况都落不下去 ——
 *  判据是 `SceneRule.isReady`，一块带几条条件都算同一个问题（少一条就是没配完）。
 */
internal fun SettingsPage.branchRulesBlockedReason(): String? {
    val scenes = branchDraftNow().scenes
    return when {
        scenes.any { !it.isReady } -> {
            I18n.get(Strings.COMMAND_BRANCH_NEED_CONDITION, "还有场景没选完")
        }

        //  时间区间两两不许重叠 —— 选的时候已经拦过一次，这里兜手改 / 导入来的配置：
        //  那种表里两条重叠的区间，后一条永远轮不到，界面上却看不出来。
        branchTimesOverlap() -> {
            I18n.get(Strings.COMMAND_COND_TIME_OVERLAP, "时间区间不允许重叠")
        }

        else -> {
            null
        }
    }
}
