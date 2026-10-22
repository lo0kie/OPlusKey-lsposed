package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.PageCornerFab
import com.lookie.opluskey.ui.PageFabButton
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_ICON_SIZE
import com.lookie.opluskey.ui.rememberFabVisibleOnScroll
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.screen.editingKeyName
import com.lookie.opluskey.ui.state.BranchDraft
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
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
        val key = branchKeyNow()
        val gesture = branchGestureNow()
        val source =
            configState.config.value
                .key(key)
                .rules
                .filter { it.gesture == gesture }
        XLog.w("条件执行：载入 ${key.label} - ${gesture.label} 的 ${source.size} 条")
        branchDraft.draft.load(owner, source)
    }

    // 那颗「确定」跟着滚动方向藏 / 露 —— 与「按键绑定」页那颗同一判据，不另写一套。
    val fabVisible = rememberFabVisibleOnScroll(scrollState)

    Box(Modifier.fillMaxSize()) {
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
                // 底下那颗「确定」浮在内容之上：留白按「它的位置 + 它的身高 + 一档间隙」给，
                //  滚到底时表尾那句注脚停在它上面，而不是被压在它底下。
                //  注意这一句要落在 `verticalScroll` **之后**：那样它才是滚动内容里的内缩，
                //  而不是把视口本身缩短（见 `BindingList` 顶部那段「纵向 padding 一档都不留」）。
                .padding(bottom = FAB_BOTTOM_CLEARANCE),
        ) {
            Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

            // 正文直接从顶栏下面开始，没有「条件执行」小标题：
            //  顶栏尾随小标题已经写着「侧键-单击」说明在哪一档，正文再写一遍页名是重复的；
            //  空态/表格本来就是这一页唯一的正文，直接顶到顶栏下面反而更像一页表。
            BranchCards(tick)
        }
        // 位置与进出场走「按键绑定」页那颗同一份（[PageCornerFab]），不在这里另算一遍
        PageCornerFab(visible = fabVisible) { BranchConfirmFab() }
    }
    // 改了还没保存就按返回时，`goBack` 会先置这一位 —— 这个窗必须挂上，否则返回被拦住却**没有窗**
    //  命令页那边是 `CommandDialogs` 一起挂的，这一页只挂这一个。
    LeaveDiscardDialog()
}

/**
 * 右下角那颗「确定」—— 这一页**唯一的落盘点**。
 *
 *  页内的改动（加一条 / 换条件 / 换动作 / 上移下移删除）全是在改草稿：配置一个字节都不动，
 *  退出时草稿作废。按这一颗才把这一档的整张表写回去。
 *
 *  半成品不许落盘（见 [branchRulesBlockedReason]）：空条件 / 空动作的行写下去就是一条永不成立的
 *  规则。灰着又拦点击比直接放着更难解释，所以按钮常驻、按下去就地给一句原因。
 *
 *  写成了才退页：写盘失败时留在原地，否则用户以为存好了、其实一个字节都没写。
 */
@Composable
private fun SettingsPage.BranchConfirmFab() {
    val tick = rememberTick()
    PageFabButton(
        onClick = {
            tick()
            val blocked = branchRulesBlockedReason()
            if (blocked != null) {
                hint(blocked)
                return@PageFabButton
            }
            if (saveBranchRules()) goBack()
        },
    ) {
        Icon(
            PageIcons.Confirm,
            contentDescription = I18n.get(Strings.COMMON_CONFIRM, "确定"),
            // 库只给容器色、不给字形色
            tint = COUITheme.colorScheme.onPrimary,
        )
    }
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
 * 「一条都还没有」那块空态 —— 与「自定义命令」页那块同一个样子（字形 + 一行字，不占卡片）。
 *
 *  说的是**此刻什么都不配时会发生什么**：按键绑定里这一档原本绑的命令照常执行。表尾那句注脚
 *  讲的是同一件事，所以一条都没有时它不出现（见 `BranchCards`），这句话由空态来说。
 */
@Composable
internal fun SettingsPage.BranchEmptyHint() {
    Column(
        //  上下**不再对称**：上边那一档是它与顶栏 / 表格之间的呼吸，下边紧跟「添加条件」那颗按钮，
        //  原来各 24dp 时字与按钮之间空出一大块、看着像两段不相干的东西（用户口径「文字和按钮
        //  距离缩小」）。下边收到 [EMPTY_STATE_TO_BUTTON_GAP]，按钮仍在同一组里跟着居中。
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = EMPTY_STATE_PADDING, bottom = EMPTY_STATE_TO_BUTTON_GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            PageIcons.CommandBranch,
            contentDescription = null,
            modifier = Modifier.size(ROW_ICON_SIZE),
            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(EMPTY_STATE_TEXT_GAP))
        Text(
            text = I18n.format(Strings.COMMAND_BRANCH_EMPTY, branchDefaultLabel()),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 空态那块**上**边留白（与「已保存的命令」空态同一档）。 */
private val EMPTY_STATE_PADDING = 24.dp

/**
 * 空态那行字与「添加条件」按钮之间的距离 —— 就是空态的**下**边留白。
 *
 *  比 [EMPTY_STATE_PADDING] 小两档：那一档是「与别的东西分开」，这一档是「紧接着的那颗按钮」，
 *  ［添加条件］本来就属于这块空态（点了它就长出一张表），贴紧一点才读成一组。
 */
private val EMPTY_STATE_TO_BUTTON_GAP = 8.dp

/** 空态字形与那行字之间的距离。 */
private val EMPTY_STATE_TEXT_GAP = 8.dp

/**
 * 内容底部留白 —— 比别页多一档，专门给右下角那颗「确定」让位。
 *
 *  它距底边 [PAGE_BOTTOM_PADDING] × 2（与别页那颗 FAB 同一个位置，见 [PageCornerFab]），
 *  按钮身高 56dp（库里 `FloatingActionButton` 的默认档），再加一档 8dp 的间隙 ——
 *  三者加起来才是「滚到最底下时最后一行停在它上面」的那个留白。
 */
private val FAB_BOTTOM_CLEARANCE = PAGE_BOTTOM_PADDING * 2 + 56.dp + 8.dp

/**
 * 保存前还差什么（`null` = 可以存）。
 *
 *  **空表是合法的**：把最后一条删掉再按确定，等于「这一档恢复成无条件」—— 存下去就是把这
 *  一档的规则整段清掉，行为回落到按键绑定。所以这里不再把空表当成「还没配完」，
 *  否则最后一条永远删不掉，也没有别的入口能做这件事。
 *
 *  剩下两种半成品各说一句：条件没配完 / 有动作没选。
 *
 *  「条件没配完」这一支同时管两件事：**整条还没选条件**（[StateRule.condition] 为空）与
 *  **选了要取值的档但取值还空着**（响铃状态 / 屏幕方向）。两种情况都落不下去。
 */
internal fun SettingsPage.branchRulesBlockedReason(): String? {
    val rules = branchDraft.draft.rules
    return when {
        rules.any { it.condition?.isUsable != true } -> {
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
