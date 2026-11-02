package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.Feedback
import com.lookie.opluskey.ui.HINT_SLOT_DISABLED
import com.lookie.opluskey.ui.HINT_SLOT_PAGE
import com.lookie.opluskey.ui.PageCornerFab
import com.lookie.opluskey.ui.PageFabButton
import com.lookie.opluskey.ui.PageScrollColumn
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.screen.combo.comboLabel
import com.lookie.opluskey.ui.state.BranchDraft
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal

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
 *  键 = 从「按键绑定」页带过来的那一颗（`KeyWatchState.editing`），档 = 顶栏那条手势带上
 *  亮着的那一档（`branchGesture`：进页时由 `BindingScreen` 跟着 pager 同步，进页之后由带子自己改）。
 *  两者一起构成 [BranchDraft.owner]：换了任何一半都重新载入，免得在长按那一档看到单击的规则。
 *
 *  ## 为什么进页要重新载入
 *
 *  这一页的入口是「看这一档现在有什么、再改」。草稿提到页面级是为了「挑应用」往返不丢，
 *  但**每次真正进页**都得按配置重灌一遍 —— 否则上一次编辑完没保存的那份会留在屏幕上。
 */
@Composable
internal fun SettingsPage.BranchScreen(
    scrollBehavior: ScrollBehavior,
    pagerState: PagerState,
) {
    val tick = rememberTick()
    //  三页各持一份滚动位置（与「按键绑定」「自定义命令」同一套做法）：共用一份的话，
    //  相邻两页同时在场时两处 `verticalScroll` 挂同一个 state、互相打架（那一页踩过）。
    val scrolls = (0 until Gesture.entries.size).map { rememberScrollState() }
    val gestures = branchGestures()
    //  顶栏那条带子只是「点哪一页」，真相在分页器里 —— 把当前页的档位镜像回页面级，
    //  FAB / 退出判据 / 顶栏小标题都读它（`branchGestureNow()`）。
    LaunchedEffect(pagerState.currentPage) {
        branchGesture = gestures.getOrElse(pagerState.currentPage) { Gesture.SINGLE }.ordinal
    }

    //  右下角只有**一颗**「确认」，而且**只在当前这一页有改动时出现**：没改过就没有要写回去的
    //  东西，摆一颗恒亮的按钮等于让人以为「不点它就白改了」。退出这一页走返回 —— 没改动时
    //  返回不会被拦（见 `goBack` 的判据）。
    //  「添加」挪到了顶栏右上角（[BranchHeaderAdd]）—— 它是「往表里加一行」，不是这一页的主操作。
    //  检查统一挪到**点确认那一刻**（见 [BranchSaveFab]）。

    //  量一次视口高度：空态要**在可见区里居中**，而它在滚动容器里拿不到「一屏多高」——
    //  滚动容器的子项拿到的是无限高度约束，`fillMaxHeight()` 在那儿没有意义。
    //  顶栏下面那条手势带（[SettingsPage.branchesBandHeight]）也占高度，一起减掉。
    val bandBlock = branchesBandHeight.dp()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        //  减掉滚动内容底部那档留白（[PAGE_BOTTOM_PADDING]，与首页同一档）：不减的话空态那块
        //  比可见区高出一截，居中的位置会偏下、还多出一段能拖的空白。
        val emptyHeight =
            (maxHeight - currentHeaderHeight(scrollBehavior) - bandBlock - PAGE_BOTTOM_PADDING)
                .coerceAtLeast(0.dp)
        //  三档手势是**三页**，与「按键绑定」同一套：横滑换档、滑块跟手（带子那边读同一个
        //  分页器）。一页一份草稿 —— 三页同时在场，共用一个对象会把邻页没保存的东西冲掉。
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    // 横向越界回弹：与「按键绑定」页那三个手势页同一套弹簧
                    .overScrollHorizontal(),
        ) { page ->
            val gesture = gestures.getOrElse(page) { Gesture.SINGLE }
            val draft = branchDraft.of(gesture)
            // 这一页那一份按配置重灌。
            //
            //  **判据是 owner 对不对**（键 / 组合键 + 这一档）：从「选择应用」返回时本页会被
            //  重新组合（来源页的组合已注销，见 `AppPickerState` 的说明），owner 不变、而草稿里
            //  正是刚编的那一份 —— 那时重灌会把它连同刚挑中的那一项一起冲掉。出栈时
            //  `discardPageStateOf` 已经把三份都 reset 成 `owner = null`，所以「重新进页」
            //  仍然会走到下面按配置重灌这一支。
            LaunchedEffect(branchOwnerOf(gesture)) {
                if (draft.owner != branchOwnerOf(gesture)) reloadBranchDraft(draft, gesture)
            }
            PageScrollColumn(scrollBehavior = scrollBehavior, state = scrolls[page]) {
                Spacer(Modifier.height(currentHeaderHeight(scrollBehavior) + bandBlock))

                // 正文直接从手势带下面开始：那一档手势由带子写，正文再写一遍页名是重复的；
                //  空态/表格本来就是这一页唯一的正文，直接顶上去反而更像一页表。
                BranchCards(draft = draft, tick = tick, emptyHeight = emptyHeight)
            }
        }
        // 位置走全项目那一份（[PageCornerFab]），不在这里另算 —— 有改动才出现，进出带过渡
        PageCornerFab(visible = branchDraftNow().dirty) { BranchSaveFab() }
    }
    // 改了还没保存就按返回时，`goBack` 会先置这一位 —— 这个窗必须挂上，否则返回被拦住却**没有窗**
    //  命令页那边是 `CommandDialogs` 一起挂的，这一页只挂这一个。
    LeaveDiscardDialog(I18n.get(Strings.DIALOG_DISCARD_CONDITION_TITLE, "放弃未保存的场景"))
}

/**
 * 右下角那颗「确认」—— **有改动时才出现**，点下去那一刻才检查、才落盘。
 *
 *  页内的改动（加一条 / 换条件 / 换动作 / 上移下移删除）全是在改草稿：配置一个字节都不动。
 *  按这颗才把这一档的整张表写回去；**写成之后留在这一页**，只报一句「已保存系统场景」——
 *  落盘完草稿重载、`dirty` 归零，这颗自己滑出去。写盘失败也留在原地（否则用户以为存好了、
 *  其实一个字节都没写）。
 *
 * ## 检查放在点击里，不放在按钮外观上
 *
 *  两种「按了也不该落盘」各有各的说法，点一下由提示通道逐条说出来（同一语义位，不叠条）：
 *   1. 半成品（[branchRulesBlockedReason]）：空条件 / 空动作的行写下去就是一条永不成立的规则；
 *   2. **死分支**（[branchDeadReason]）：某一档条件的取值全在表里 ⇒ 它后面那几条永远轮不到；
 *      或者前面某一条更宽、把这一条整个包含住了（组合条件那种情形）。
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
            } else if (saveBranchRules()) {
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

/** 按配置把**这一页那一份**重新载入（进页 / 换键时走这一支）。 */
private fun SettingsPage.reloadBranchDraft(
    draft: BranchDraft,
    gesture: Gesture,
) {
    val comboId = branchDraft.comboId
    val cfg = configState.config.value
    if (comboId != null) {
        //  组合键这一档：同一套 `StateRule`，只是从 `combos` 里取。
        //  找不到（在别处把这一组删了）就清空草稿 —— 留着上一趟的规则会让用户「编辑一条
        //  已经不存在的组合键」，点确认时还会把它写回去。
        val combo = cfg.combos.firstOrNull { it.id == comboId }
        if (combo == null) {
            XLog.w("系统场景：组合键 $comboId 已经不在了，草稿清空")
            draft.reset()
            return
        }
        val source = combo.rules.filter { it.gesture == gesture }
        XLog.w("系统场景：载入组合键 ${combo.id} - ${gesture.label} 的 ${source.size} 条")
        draft.load(BranchDraft.comboOwnerOf(combo.id, gesture), source)
        return
    }
    val key = branchKeyNow()
    val source =
        cfg
            .key(key)
            .rules
            .filter { it.gesture == gesture }
    XLog.w("系统场景：载入 ${key.label} - ${gesture.label} 的 ${source.size} 条")
    draft.load(BranchDraft.ownerOf(key, gesture), source)
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
        title = I18n.get(Strings.DIALOG_DELETE_CONDITION_TITLE, "删除场景"),
        summary = I18n.get(Strings.DIALOG_DELETE_CONDITION_MESSAGE, "这条场景和它的动作会一起删掉，下面的场景会往上顶一条。"),
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
 * 「停用这一条场景」的确认窗（触发点在行尾那颗「更多」菜单里）。
 *
 *  为什么也要一道确认：这一条停用之后**按下去不会再命中它**。与删除不是一回事（删除是它不见了），
 *  而两行在菜单里挨着 —— 一句确认把「暂时不生效」与「没了」分开。
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
    val rule = draft.rules.getOrNull(at)
    //  规则已经不在了（这一趟里删过）⇒ 窗不显示
    ConfirmDialog(
        show = rule != null,
        title = I18n.get(Strings.COMMAND_BRANCH_DISABLE, "停用场景"),
        summary = I18n.get(Strings.COMMAND_BRANCH_DISABLE_MESSAGE, "这条场景将不再生效。"),
        action = I18n.get(Strings.COMMON_DISABLE, "停用"),
        onDismiss = { menus.toggleAt = -1 },
        onConfirm = {
            menus.toggleAt = -1
            //  按下标去改是安全的：窗开着的这段里加不了也删不了另一条（菜单都收着），
            //  仍核一次「要改的还是不是它」—— 宁可不改，不能错改。
            draft.rules.getOrNull(at)?.let { target ->
                draft.update(at, target.copy(enabled = false))
            }
        },
    )
}

/**
 * 「删掉一行里的**一条**条件」的确认窗（触发点在条件菜单的「删除」那一行）。
 *
 *  与 [BranchDeleteDialog] 分开：那个删的是**整条规则**（连它的动作一起消失、下面一行往上顶），
 *  这个只摘掉一块里的一条 —— 后果差得远，标题与正文都不能共用一套说法。
 */
@Composable
internal fun SettingsPage.BranchConditionDeleteDialog(menus: BranchMenuState) {
    val target = menus.deletingCond
    ConfirmDialog(
        show = target != null,
        title = I18n.get(Strings.DIALOG_DELETE_ONE_CONDITION_TITLE, "删除这条场景"),
        summary = I18n.get(Strings.DIALOG_DELETE_ONE_CONDITION_MESSAGE, "只删这一条场景，同一行里的其余场景不受影响。"),
        action = I18n.get(Strings.COMMON_DELETE, "删除"),
        onDismiss = { menus.deletingCond = null },
        onConfirm = {
            //  只**标出来**，不在这里删：那一行要先播完收起（与删整行同一个做法，见
            //  `BranchMenuState.exitingCond`），数据由 `BranchCards` 那个 effect 在动画结束时摘掉。
            menus.exitingCond = target
            menus.deletingCond = null
        },
    )
}

/**
 * 把这一档的条件表写回配置 —— 只替换**这一颗键这一档**那几条，其余规则原样留着。
 *
 *  与命令库那套不同：分叉不占绑定，所以这里既不新增命令、也不碰别的档。
 */
internal fun SettingsPage.saveBranchRules(): Boolean {
    val gesture = branchGestureNow()
    val rules = branchDraftNow().rules.toList()
    val cfg = configState.config.value
    //  组合键那一档：同一套规则、同一套「只替换这一档」的写法，只是写回 `combos` 而不是 `keys`。
    val comboId = branchDraft.comboId
    if (comboId != null) {
        val combo = cfg.combos.firstOrNull { it.id == comboId }
        if (combo == null) {
            //  在别处把这一组删了：不能再往一个不存在的 id 上写（那等于凭空造一档组合键）
            XLog.w("系统场景：组合键 $comboId 已经不在了，本次不保存")
            return false
        }
        val next =
            cfg.copy(
                combos =
                    cfg.combos.map {
                        if (it.id != comboId) {
                            it
                        } else {
                            // 顺序就是判定顺序：别的档原样留下，这一档整段换成本次编辑的这一批
                            it.copy(rules = it.rules.filterNot { rule -> rule.gesture == gesture } + rules)
                        }
                    },
            )
        val label = comboLabel(cfg, combo, bracketed = true)
        return when (write(next, "系统场景：$label - ${gesture.label}（${rules.size} 条）")) {
            CommandsSave.Failed -> {
                false
            }

            else -> {
                XLog.w("系统场景已保存：$label - ${gesture.label} ${rules.size} 条")
                branchDraftNow().load(BranchDraft.comboOwnerOf(comboId, gesture), rules)
                true
            }
        }
    }
    val key = branchKeyNow()
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
    return when (write(next, "系统场景：${key.label} - ${gesture.label}（${rules.size} 条）")) {
        CommandsSave.Failed -> {
            false
        }

        else -> {
            XLog.w("系统场景已保存：${key.label} - ${gesture.label} ${rules.size} 条")
            branchDraftNow().load(BranchDraft.ownerOf(key, gesture), rules)
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
        title = I18n.get(Strings.COMMAND_BRANCH_EMPTY_TITLE, "还没有添加场景"),
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
 *  半成品只剩「条件没配完」一种：**动作可以留空**（= 无操作，见 `ActionCatalog.BRANCH_GROUPS`），
 *  它不是没配完，而是「这一条命中时什么都不做」。
 *
 *  「条件没配完」这一支同时管两件事：**整块还没选条件**（[StateRule.conditions] 为空）与
 *  **选了要取值的档但取值还空着**（响铃状态 / 屏幕方向）。两种情况都落不下去 ——
 *  判据是 [StateRule.isReady]，一块带几条条件都算同一个问题（少一条就是没配完）。
 */
internal fun SettingsPage.branchRulesBlockedReason(): String? {
    val rules = branchDraftNow().rules
    return when {
        rules.any { !it.isReady } -> {
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
