package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.StateRule
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT_MS
import com.lookie.opluskey.ui.model.conditionIcon
import com.lookie.opluskey.ui.model.conditionRowTitle
import com.lookie.opluskey.ui.state.BranchDraft
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

// 条件执行行里的那几件小件 —— 条件行写什么、行尾那颗「不会执行」的注脚、以及「点开还有一层」
// 那枚选择器。从 `CommandBranches.kt` 拆出来：那边是整块卡片怎么排，这边是行内文案与行尾记号，
// 两件事各自涨，合在一处迟早顶到 detekt 的函数数上限。

/**
 * 条件那几行写什么 —— 一条条件一行，没配完的那一条 `empty = true`（压禁用色）。
 *
 *  抽成一支只为让 [BranchRow] 留在 detekt 的长度上限内；判据见那边的注释。
 */
internal fun SettingsPage.ruleConditionLines(rule: StateRule): List<ConditionLine> =
    if (rule.conditions.isEmpty()) {
        //  一条都没配时也给一行「未选择条件」，让用户知道这一格要配什么
        listOf(
            ConditionLine(
                I18n.get(Strings.COMMAND_BRANCH_PICK_CONDITION, "未选择场景"),
                PageIcons.Scene,
                empty = true,
            ),
        )
    } else {
        rule.conditions.map {
            ConditionLine(
                //  插进来的空条件 / 只选了种类没选取值：这一行写「未选择条件」并压禁用色
                text =
                    if (it.isUsable) {
                        conditionRowTitle(it, ::appNameOf)
                    } else {
                        I18n.get(Strings.COMMAND_BRANCH_PICK_CONDITION, "未选择场景")
                    },
                icon = it.kind?.let(::conditionIcon) ?: PageIcons.Scene,
                empty = !it.isUsable,
            )
        }
    }

/** 条件那**一行**写什么、配哪颗字形、是不是还没配完 —— 一条条件一个，卡片里各占一行。 */
internal class ConditionLine(
    val text: String,
    val icon: ImageVector,
    /** 这一条还没配完（刚插进来的空条件 / 只选了种类没选取值）⇒ 图标与文字压禁用色。 */
    val empty: Boolean,
)

/**
 * 条件那一行的行尾：死分支先标一句红的，再接那枚选择器。
 *
 *  为什么标在**这一行**而不是小标题：小标题是库组件 `SmallTitle`，只能给一整串字，染不了一半；
 *  而这一行本来就是「这一条什么时候执行」，写在这儿读起来最顺。
 */
@Composable
internal fun ConditionTrailing(dead: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dead) {
            Text(
                I18n.get(Strings.COMMAND_BRANCH_DEAD, "不会执行"),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.error,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        SelectorGlyph()
    }
}

/**
 * 行尾那颗「点这一行能选一个」的指示（[PageIcons.Selector]）—— 条件行、步骤行、「添加命令」行**同一颗**。
 *
 *  尺寸取 [ROW_END_ICON_SIZE]（24dp），与「捐赠打赏」那一行的行尾同一档，不套 `ICON_ACTION_SIZE`
 *  那颗按钮大的盒子；tint 走最弱那一档（`onSurfaceVariantActions`），比行首那颗图标弱。
 */
@Composable
internal fun SelectorGlyph() {
    Icon(
        PageIcons.Selector,
        contentDescription = null,
        modifier = Modifier.size(ROW_END_ICON_SIZE),
        tint = COUITheme.colorScheme.onSurfaceVariantActions,
    )
}

/**
 * 删除一条规则的收尾 —— **先播收缩、再动数据**（见 [BranchMenuState.deletingAt]）。
 *
 *  抽成一支只为让 [BranchCards] 留在 detekt 的长度上限内；判据见那边原来那段注释：
 *  收尾再核一次「要删的还是不是它」（这 260ms 里用户还能上移 / 下移 / 再删一条，下标会移位），
 *  宁可不删、不能错删；删的是最后一条时不等 —— 那一下整块要变空态，由外层
 *  （空态 ↔ 表格）那套动画接管，两段串起来就太拖了。
 */
@Composable
internal fun BranchDeleteEffect(
    draft: BranchDraft,
    menus: BranchMenuState,
) {
    LaunchedEffect(menus.deletingAt) {
        val at = menus.deletingAt
        if (at < 0) return@LaunchedEffect
        //  记下「要删的是哪一条」而不是只记下标：这 260ms 里下标会移位，按 at 去删就会删错行。
        //  按**规则本身**找回来，挪过也能删对；找不到（已经被改掉了）就什么都不删。
        val target =
            draft.rules.getOrNull(at) ?: run {
                menus.deletingAt = NO_EXITING_ROW
                return@LaunchedEffect
            }
        if (draft.rules.size > 1) delay(STEP_ROW_EXIT_MS + ROW_EXIT_SETTLE_MS)
        if (menus.deletingAt == at) {
            val now = draft.rules.indexOf(target)
            if (now >= 0) draft.remove(now)
            menus.deletingAt = NO_EXITING_ROW
        }
    }
}
