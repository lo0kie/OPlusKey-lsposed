package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.FAB_BOTTOM_CLEARANCE
import com.lookie.opluskey.ui.Feedback
import com.lookie.opluskey.ui.HINT_SLOT_COMMAND
import com.lookie.opluskey.ui.PageCornerFab
import com.lookie.opluskey.ui.PageFabButton
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.screen.editingKeyName
import com.lookie.opluskey.ui.state.BranchDraft
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

// 「条件执行」页 —— 从「按键绑定」页顶栏 ⋮ 菜单进来。
//
//  **分叉不是命令库的一条命令**：它是叠在「按键绑定」之上的一张**顺序表**（`KeyConfig.rules`），
//  按下的那一刻从上往下找，第一条成立的就执行它的动作；一条都不中，才执行「按键绑定」里
//  这一档原本绑的命令。所以这一页没有名字、不进「已保存的命令」，也不占用任何一档绑定。

/**
 * 「条件执行」页 —— 编辑**当前这颗键、当前这一档**的条件表。
 *
 *  ## 是哪颗键、哪一档
 *
 *  键 = 从「按键绑定」页带过来的那一颗（`KeyWatchState.editing`），档 = 进来时停在哪一档手势
 *  （`branchGesture`，由 `BindingScreen` 跟着 pager 同步）。两者一起构成 [BranchDraft.owner]：
 *  换了任何一半都重新载入，免得在长按那一档看到单击的规则。
 *
 *  ## 为什么进页要重新载入
 *
 *  这一页的入口是「看这一档现在有什么、再改」。草稿提到页面级是为了「挑应用」往返不丢，
 *  但**每次真正进页**都得按配置重灌一遍 —— 否则上一次编辑完没保存的那份会留在屏幕上。
 */
@Composable
internal fun SettingsPage.BranchScreen(scrollBehavior: ScrollBehavior) {
    val scrollState = rememberScrollState()
    val tick = rememberTick()
    val owner = BranchDraft.ownerOf(branchKeyNow(), branchGestureNow())

    // 按「当前键 + 当前档」重灌草稿。
    //
    //  **不能只看 owner**：从「选择应用」返回时本页会被重新组合（来源页的组合已注销，
    //  见 `AppPickerState` 的说明），owner 不变、但草稿里正是刚编的那一份 —— 重灌会把它
    //  连同刚挑中的那一项一起冲掉。出栈时 `discardPageStateOf` 已经把草稿 reset 成 `owner = null`，
    //  所以「重新进页」仍然会走到下面按配置重灌这一支。
    LaunchedEffect(owner) {
        if (branchDraft.draft.owner == owner) return@LaunchedEffect
        reloadBranchDraft()
    }

    //  右下角现在只剩**一颗**「确认」（常驻、不跟滚动躲、也不按状态忽隐忽现）。
    //  「添加」挪到了顶栏右上角（[BranchHeaderAdd]）—— 它是「往表里加一行」，不是这一页的主操作。
    //  原先「确认」只在有改动时出现、「添加」在条件恒成立时按不动，两处早都撤了：
    //  检查统一挪到**点确认那一刻**（见 [BranchSaveFab]）。

    //  量一次视口高度：空态要**在可见区里居中**，而它在滚动容器里拿不到「一屏多高」——
    //  滚动容器的子项拿到的是无限高度约束，`fillMaxHeight()` 在那儿没有意义。
    BoxWithConstraints(Modifier.fillMaxSize()) {
        //  减掉滚动内容底部那档留白（`FAB_BOTTOM_CLEARANCE`）：不减的话空态那块比可见区
        //  高出一截，居中的位置会偏下、还多出一段能拖的空白。
        val emptyHeight =
            (maxHeight - currentHeaderHeight(scrollBehavior) - FAB_BOTTOM_CLEARANCE)
                .coerceAtLeast(0.dp)
        Column(
            Modifier
                .fillMaxSize()
                // 大标题随滚动折叠（`nestedScroll` 必须在 `verticalScroll` **之前**）——
                //  与「自定义命令」页 / 「按键绑定」页同一套接法（见 `CommandPage` / `BindingList`）。
                //  原先这一页只读 `currentHeaderHeight(scrollBehavior)` 让位、却没把滚动量喂给顶栏
                //  ⇒ 顶栏收不到位移，标题**永远停在展开态**。
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                // 越界回弹：`overScrollVertical` 挂在滚动容器**外侧**，所以内容不满一屏时照样能拖。
                //  这一页常常只有一两条条件、根本撑不满一屏 —— 不挂它的话在那一档完全动不了。
                .overScrollVertical()
                .verticalScroll(scrollState)
                // 底下那颗「确认」浮在内容之上：留白按「一颗按钮」给死，
                //  滚到底时表尾那句注脚停在它上面，而不是被压在底下。
                //  注意这一句要落在 `verticalScroll` **之后**：那样它才是滚动内容里的内缩，
                //  而不是把视口本身缩短（见 `BindingList` 顶部那段「纵向 padding 一档都不留」）。
                .padding(bottom = FAB_BOTTOM_CLEARANCE),
        ) {
            Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

            // 正文直接从顶栏下面开始，没有「条件执行」小标题：
            //  顶栏尾随小标题已经写着「侧键-单击」说明在哪一档，正文再写一遍页名是重复的；
            //  空态/表格本来就是这一页唯一的正文，直接顶到顶栏下面反而更像一页表。
            BranchCards(tick, emptyHeight)
        }
        // 位置走全项目那一份（[PageCornerFab]），不在这里另算 —— 恒显示，切换时不动画
        PageCornerFab(visible = true) { BranchSaveFab() }
    }
    // 改了还没保存就按返回时，`goBack` 会先置这一位 —— 这个窗必须挂上，否则返回被拦住却**没有窗**
    //  命令页那边是 `CommandDialogs` 一起挂的，这一页只挂这一个。
    LeaveDiscardDialog(I18n.get(Strings.DIALOG_DISCARD_CONDITION_TITLE, "放弃未保存的条件"))
}

/**
 * 右下角那颗「确认」—— **恒显示**，点下去那一刻才检查、才落盘。
 *
 *  页内的改动（加一条 / 换条件 / 换动作 / 上移下移删除）全是在改草稿：配置一个字节都不动。
 *  按这颗才把这一档的整张表写回去，写成了才退页（写盘失败留在原地，否则用户以为存好了、
 *  其实一个字节都没写）。
 *
 *  ## 检查放在点击里，不放在按钮外观上
 *
 *  三种「按了也不该落盘」各有各的说法，点一下由提示通道逐条说出来（同一语义位，不叠条）：
 *   1. 半成品（[branchRulesBlockedReason]）：空条件 / 空动作的行写下去就是一条永不成立的规则；
 *   2. **死分支**（[branchDeadReason]）：某一档条件的取值全在表里 ⇒ 它后面那几条永远轮不到；
 *      或者前面某一条更宽、把这一条整个包含住了（组合条件那种情形）；
 *   3. 什么都没改：不写盘，直接退页（写一遍同样的内容是白写，还会多一条「已保存」回执）。
 *
 *  按钮本身**不做禁用态**（也不换配色、更不用 `Modifier.alpha` —— alpha 会单独合成一层，
 *  把阴影裁进按钮方框里）。一个恒显示的按钮忽明忽暗，比「按下去被告知差什么」更难理解。
 */
@Composable
private fun SettingsPage.BranchSaveFab() {
    val tick = rememberTick()
    PageFabButton(
        onClick = {
            tick()
            val blocked = branchRulesBlockedReason() ?: branchDeadReason()
            when {
                blocked != null -> hint(blocked, slot = HINT_SLOT_COMMAND)

                // 没有改动：这张表本来就是这个样子，不白写一遍
                !branchDraft.draft.dirty -> goBack()

                saveBranchRules() -> goBack()
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

/** 按配置把这一档重新载入草稿（进页时与工具条的「重置」共用这一支）。 */
private fun SettingsPage.reloadBranchDraft() {
    val key = branchKeyNow()
    val gesture = branchGestureNow()
    val source =
        configState.config.value
            .key(key)
            .rules
            .filter { it.gesture == gesture }
    XLog.w("条件执行：载入 ${key.label} - ${gesture.label} 的 ${source.size} 条")
    branchDraft.draft.load(BranchDraft.ownerOf(key, gesture), source)
}

/**
 * 「删掉这一条」的确认窗（触发点在行尾那颗「更多」菜单里，这里只负责画）。
 *
 *  为什么中间那颗菜单不当场删：这一页里「上移 / 下移」都能原样挪回来，只有删除是不可逆的，
 *  而那颗三点离右列（换动作）只隔几个 dp —— 点歪一下整条就没了。确认窗是这条路上唯一一道闸。
 */
@Composable
internal fun SettingsPage.BranchDeleteDialog(menus: BranchMenuState) {
    val at = menus.deleteAt
    ConfirmDialog(
        show = at >= 0,
        title = I18n.get(Strings.DIALOG_DELETE_CONDITION_TITLE, "删除条件"),
        summary = I18n.get(Strings.DIALOG_DELETE_CONDITION_MESSAGE, "这条条件和它的动作会一起删掉，下面的条件会往上顶一条。"),
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
 * 结尾那一行默认项在界面上怎么写 —— `快捷键 - 单击`（键名 - 手势）。
 *
 *  同一句也挂在顶栏的尾随小标题上：那里说的是「这一页在编哪一档」，与表尾那一行是同一件事。
 */
internal fun SettingsPage.branchDefaultLabel(): String = "${editingKeyName()} - ${branchGestureNow().label}"

/**
 * 把这一档的条件表写回配置 —— 只替换**这一颗键这一档**那几条，其余规则原样留着。
 *
 *  与命令库那套不同：分叉不占绑定，所以这里既不新增命令、也不碰别的档。
 */
internal fun SettingsPage.saveBranchRules(): Boolean {
    val key = branchKeyNow()
    val gesture = branchGestureNow()
    val rules = branchDraft.draft.rules.toList()
    val cfg = configState.config.value
    val next =
        cfg.copy(
            keys =
                cfg.keys.mapValues { (id, keyConfig) ->
                    if (id != key) {
                        keyConfig
                    } else {
                        // 顺序就是判定顺序：先把别的档原样留下，这一档整段换成本次编辑的这一批
                        keyConfig.copy(rules = keyConfig.rules.filterNot { it.gesture == gesture } + rules)
                    }
                },
        )
    return when (write(next, "条件执行：${key.label} - ${gesture.label}（${rules.size} 条）")) {
        CommandsSave.Failed -> {
            false
        }

        else -> {
            XLog.w("条件执行已保存：${key.label} - ${gesture.label} ${rules.size} 条")
            branchDraft.draft.load(BranchDraft.ownerOf(key, gesture), rules)
            true
        }
    }
}

/**
 * 「一条都还没有」那块空态 —— **在滚动内容里**，跟着内容一起拖、一起弹。
 *
 *  说的是**此刻什么都不配时会发生什么**：按键绑定里这一档原本绑的命令照常执行。表尾那句注脚
 *  讲的是同一件事，所以一条都没有时它不出现（见 `BranchCards`），这句话由空态来说。
 *
 *  它拿到的是 `emptyHeight`（可见区减顶栏、减底部留白）—— 那块高度由 `BranchCards` 给，
 *  这样它在可见区里居中，而整块仍在滚动内容里（拖动时会跟着弹）。
 */
@Composable
internal fun SettingsPage.BranchEmptyHint() {
    Feedback(
        glyph = { tint ->
            Icon(PageIcons.NoMatch, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = tint)
        },
        //  上面一句说**状态**，下面一句说**现在会发生什么** —— 只有下面那句时，
        //  用户得先读完整句才知道这块是空的。
        title = I18n.get(Strings.COMMAND_BRANCH_EMPTY_TITLE, "还没有添加条件"),
        text = branchEmptyText(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 保存前还差什么（`null` = 可以存）。
 *
 *  **空表是合法的**：把最后一条删掉再按确定，等于「这一档恢复成无条件」—— 存下去就是把这
 *  一档的规则整段清掉，行为回落到按键绑定。所以这里不再把空表当成「还没配完」，
 *  否则最后一条永远删不掉，也没有别的入口能做这件事。
 *
 *  剩下两种半成品各说一句：条件没配完 / 有动作没选。
 *
 *  「条件没配完」这一支同时管两件事：**整块还没选条件**（[StateRule.conditions] 为空）与
 *  **选了要取值的档但取值还空着**（响铃状态 / 屏幕方向）。两种情况都落不下去 ——
 *  判据是 [StateRule.isReady]，一块带几条条件都算同一个问题（少一条就是没配完）。
 */
internal fun SettingsPage.branchRulesBlockedReason(): String? {
    val rules = branchDraft.draft.rules
    return when {
        rules.any { !it.isReady } -> {
            I18n.get(Strings.COMMAND_BRANCH_NEED_CONDITION, "还有条件没选完")
        }

        rules.any { it.action == com.lookie.opluskey.core.model.Action.None } -> {
            I18n.get(Strings.COMMAND_BRANCH_NEED_COMMAND, "每条条件都要选一个动作")
        }

        else -> {
            null
        }
    }
}
