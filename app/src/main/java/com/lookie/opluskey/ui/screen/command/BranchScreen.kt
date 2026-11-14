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
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageCornerFab
import com.lookie.opluskey.ui.chrome.PageFabButton
import com.lookie.opluskey.ui.chrome.PageFabIcon
import com.lookie.opluskey.ui.chrome.PageScrollColumn
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.feedback.Feedback
import com.lookie.opluskey.ui.feedback.HINT_SLOT_DISABLED
import com.lookie.opluskey.ui.feedback.HINT_SLOT_PAGE
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.rememberTick
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.state.binding.BranchDraft
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.style.visibleAreaHeight
import com.lookie.opluskey.ui.write.branchKeyNow
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior

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
        //  账走全项目那一支（[visibleAreaHeight]），三处滚动页不各写一遍。
        val emptyHeight =
            visibleAreaHeight(
                maxHeight = maxHeight,
                topInset = currentHeaderHeight(scrollBehavior),
                bottomInset = PAGE_BOTTOM_PADDING,
            )
        PageScrollColumn(scrollBehavior = scrollBehavior, state = scroll) {
            Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))
            //  行的可点性（整页那一道闸）由 `BranchRuleColumn` 就地判。
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
 *  「按了也不该落盘」的几种说法都收在 [branchRulesBlockedReason]：条件没配完、时间区间重叠、
 *  整块重复。前两种是「配不下去」，最后一种是「配了也白配」。至于**顺序**上轮不到谁
 *  （「某档取值前面已经凑齐」那一类）**不在这里拦** —— 表怎么排就怎么执行，判定侧本来就是
 *  「从上往下第一条成立的赢」；整块重复不是顺序问题（那两块交换位置还是后一块输）。
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
            val blocked = branchRulesBlockedReason()
            if (blocked != null) {
                hint(blocked, slot = HINT_SLOT_DISABLED)
            } else if (saveScenes()) {
                hint(I18n.get(Strings.COMMAND_BRANCH_SAVED, "已保存系统场景"), slot = HINT_SLOT_PAGE)
            }
        },
    ) {
        PageFabIcon(
            PageIcons.Confirm,
            contentDescription = I18n.get(Strings.COMMON_CONFIRM, "确定"),
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
        summary = I18n.get(Strings.DIALOG_DELETE_CONDITION_MESSAGE, "这条场景与其动作将一并删除。"),
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
 *  （删除是它不见了），而两行在菜单里挨着 —— 标题（停用 / 删除）与动作项两处把它分开。
 *  **不另写正文**：「将不再生效」只是把标题又说了一遍。
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
 *  这个只摘掉一块里的一条 —— 后果差得远，标题不能共用一套说法。
 *  这一支**不另写正文**：摘掉一条之后没有第二种后果可说，「只删这一条」只是把标题重复一遍
 *  （整块那个要写，因为「动作也一起删」是标题说不出来的）。
 */
@Composable
internal fun SettingsPage.BranchConditionDeleteDialog(menus: BranchMenuState) {
    val target = menus.deletingCond
    ConfirmDialog(
        show = target != null,
        title = I18n.get(Strings.DIALOG_DELETE_ONE_CONDITION_TITLE, "删除这条场景"),
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
 *  ## 只有本页那颗「确认」走这里
 *
 *  「场景动作」页那颗不整表写回 —— 那会把本页还没确认的条件改动一起落盘，等于替用户按了
 *  这颗「确认」。它只写自己那一块（[saveSceneBlock]），两页各管自己的账。
 *
 *  ## 写成之后怎么收尾
 *
 *  写成之后草稿与配置刚对齐 ⇒ [BranchDraft.rebase]：行身份与配置槽位一起重发，
 *  屏幕上的内容一个字不变。
 */
internal fun SettingsPage.saveScenes(): Boolean {
    val draft = branchDraftNow()
    val scenes = scenesToWrite(draft)
    if (writeScenes(scenes) == CommandsSave.Failed) return false
    draft.rebase(scenes)
    return true
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
 *  本页那颗「确认」正是这个语义（整张表就是这一页在编的东西）。
 *
 *  「场景动作」页那颗**不走这里**：它只改一块、也只该写一块，靠的是草稿记着的**配置槽位**
 *  （载入时按位置对齐，见 `BranchDraft.slots` 与 [saveSceneBlock]），不是按内容去猜。
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
        title = I18n.get(Strings.COMMAND_BRANCH_EMPTY_TITLE, "尚未添加场景"),
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
 *
 *  「整块重复」是另一种配错（见 [duplicateBlockIndexes]）：同一组条件在表里出现了两次。
 *  界面上后一块的行尾已经标着「与上面重复」，这里再拦一道 —— 提示条上那句说得更直白
 *  （「请并成一块」），而保存是个不可撤销的收口动作。
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

        duplicateBlockIndexes(scenes).isNotEmpty() -> {
            I18n.get(Strings.COMMAND_BRANCH_SAME_SCENE_SAVE, "有两块的场景完全一样，请并成一块")
        }

        else -> {
            null
        }
    }
}

/**
 * 空态那句「现在会发生什么」—— 模板里的 `%s`（原来填「键名 - 手势」）**不填**，去掉它之后
 * 压掉多余空格，其余照模板拼成一整句。
 *
 *  三档都在同一页里，一句话点名其中一档反而误导；这句只说明「没有场景时会回落到按键绑定」。
 */
internal fun SettingsPage.branchEmptyText(): String {
    val template = I18n.get(Strings.COMMAND_BRANCH_EMPTY, "将执行按键绑定里的 %s")
    //  这句不拼「键名 - 手势」：三档都在同一页里，一句话点名其中一档反而误导。
    //  模板里的 `%s` 没有可填的键名：去掉它时连同旁边那一个空格一起收掉，再压掉连续空格，
    //  否则中文会拖着行尾空格、英文会留下「Will run  from …」这种双空格。
    return template.replace("%s", "").replace(Regex(" +"), " ").trim()
}
