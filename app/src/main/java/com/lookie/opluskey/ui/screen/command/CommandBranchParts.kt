package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.DisabledStateText
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.appTargetLabelOf
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT_MS
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT_MS
import com.lookie.opluskey.ui.model.conditionIcon
import com.lookie.opluskey.ui.model.conditionRowTitle
import com.lookie.opluskey.ui.model.stepTitleWithTarget
import com.lookie.opluskey.ui.state.BranchDraft
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

// 条件执行行里的那几件小件 —— 条件行写什么、行尾那句「已停用」的注脚、以及「点开还有一层」
// 那枚选择器。从 `CommandBranches.kt` 拆出来：那边是整块卡片怎么排，这边是行内文案与行尾记号，
// 两件事各自涨，合在一处迟早顶到 detekt 的函数数上限。

/**
 * 条件那几行写什么 —— 一条条件一行，没配完的那一条 `empty = true`（压禁用色）。
 *
 *  抽成一支只为让 [BranchRow] 留在 detekt 的长度上限内；判据见那边的注释。
 */
internal fun SettingsPage.ruleConditionLines(rule: SceneRule): List<ConditionLine> =
    if (rule.conditions.isEmpty()) {
        //  一条都没配时也给一行「未选择条件」，让用户知道这一格要配什么
        listOf(
            ConditionLine(
                I18n.get(Strings.COMMAND_BRANCH_PICK_CONDITION, "未选择场景"),
                PageIcons.Scene,
                empty = true,
                //  占位行与「配好之后的那一条」**共用同一个身份**（见下面 `single` 那一支）：
                //  这一下不是插入，而是同一行换内容。
                rowKey = CONDITION_PLACEHOLDER_KEY,
            ),
        )
    } else {
        //  只剩一条时，这一行**就是**原来那个占位行 —— 位置、高度都没变，变的只有内容
        //  （「未选择场景」→「亮屏中」）。给它同一个身份，行状态与子组合都接着用：
        //  那一下才不会「先把高度塌掉、再把新行滑进来」，而是原地淡换内容。
        //  第二条起才是真的往表里插一行，用条件自己当身份（见 `CardRow.Item.rowKey`）。
        val single = rule.conditions.size == 1
        //  同一个取值可能出现两遍（界面里二级会置灰，但手改 / 导入来的配置不管这一道）：
        //  行身份带上「第几次出现」，否则两行的 `rowKey` 相同，`CardList` 会把它们当成同一行。
        val seen = HashMap<BranchCondition, Int>()
        rule.conditions.map { condition ->
            val nth = seen[condition] ?: 0
            seen[condition] = nth + 1
            ConditionLine(
                //  插进来的空条件 / 只选了种类没选取值：这一行写「未选择条件」并压禁用色
                text =
                    if (condition.isUsable) {
                        conditionRowTitle(condition, ::appNameOf)
                    } else {
                        I18n.get(Strings.COMMAND_BRANCH_PICK_CONDITION, "未选择场景")
                    },
                icon = conditionLineIcon(condition),
                empty = !condition.isUsable,
                //  应用那一族（前台 / 前台以外）都能挑好几个，名字按**框**排（与组合键成员同一种标签，
                //  见 [ConditionRowTitle]）—— 给 `null` 的那几档仍是一行字。
                apps =
                    if (condition.kind?.isAppFamily == true && condition.isUsable) {
                        condition.appPackages.map(::appNameOf)
                    } else {
                        null
                    },
                //  框前面那个种类名要念对档：`前台应用 - [微信]` / `前台应用以外 - [微信]`
                kind = condition.kind,
                //  这一行在列表里的身份：行进出场按它认「是不是同一行」，不按位次
                //  （见 `CardRow.Item.rowKey`）。
                rowKey = if (single) CONDITION_PLACEHOLDER_KEY else (condition to nth),
            )
        }
    }

/** 占位行「未选择场景」的身份 —— 与「这一块唯一那条条件」共用（见 [ruleConditionLines]）。 */
private val CONDITION_PLACEHOLDER_KEY = Any()

/**
 * 条件那一行的行首字形 —— 取**父图标**：这一条属于哪一类条件就给哪一类的字形
 * （[conditionIcon]：插头 / 路由器 / 耳机 / 一台手机…），不再按**取值**换（子图标已去掉）。
 *
 *  以前是「跟着取值给」：亮屏与锁屏、WiFi 开与关各一颗，一列条件扫下来全是只差一道斜杠的图，
 *  读起来比文字还费劲。现在一列里每行一眼分得出「是充电还是 WiFi」，具体哪个取值由文字说。
 *
 *  还没选种类的那一条（刚插进来的空条件）给父级那颗 [PageIcons.Scene]。
 */
private fun conditionLineIcon(condition: BranchCondition): ImageVector = condition.kind?.let(::conditionIcon) ?: PageIcons.Scene

/** 条件那**一行**写什么、配哪颗字形、是不是还没配完 —— 一条条件一个，卡片里各占一行。 */
internal class ConditionLine(
    val text: String,
    val icon: ImageVector,
    /** 这一条还没配完（刚插进来的空条件 / 只选了种类没选取值）⇒ 图标与文字压禁用色。 */
    val empty: Boolean,
    /**
     * 「前台应用」那一档的应用名 —— 有值时这一行的标题**按框画**（与组合键成员同一种标签），
     * `null` = 走普通的整行文字（[text]）。
     *
     *  为什么单独给一份而不是让界面去拆 [text]：拆字符串要认「种类 - 取值」那个分隔符，
     *  而应用名里完全可能带 ` - `（`微信 - LauncherUI` 那种写法就在别处出现过）。
     */
    val apps: List<String>? = null,
    /**
     * 这一条条件的**种类** —— 只有应用那一族读它（[ConditionRowTitle] 里框前面那个种类名：
     *  `前台应用 - [微信]` 与 `前台应用以外 - [微信]` 不能念成同一句）。
     *
     *  占位行（「未选择场景」）给 `null`：它没有种类。
     */
    val kind: ConditionKind? = null,
    /**
     * 这一行在卡片里的身份 —— 那条条件 + 它是同一取值的第几次出现（占位行 / 「未选择条件」那一行给 `null`）。
     *
     *  行进场动画要靠它认「这是新插进来的一行，还是刚才就在这里的那一行」：
     *  `CardList` 的进出场状态按位次记，新插一行会落到原来那一行的槽上（见 `CardRow.Item.rowKey`）。
     *  带上出现次序是因为同一个取值能写两遍（同一种类可以加好几条，见 `blockKindsOf`）。
     */
    val rowKey: Any? = null,
)

/**
 * 条件那一行的行尾：先标一句状态，再接那枚选择器。
 *
 *  [off]「已停用」（弱色）—— 它自己关着（行尾「更多」菜单里的停用，`SceneRule.enabled`）。
 *
 *  为什么标在**这一行**而不是小标题：小标题是库组件 `SmallTitle`，只能给一整串字，染不了一半；
 *  而这一行本来就是「这一条什么时候执行」，写在这儿读起来最顺。
 */
@Composable
internal fun ConditionTrailing(off: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (off) RuleStateHint(RuleState.OFF)
        RowEndIcon(PageIcons.Selector)
    }
}

/**
 * 行尾那半句话 —— 「已停用」（弱色）/「与上面重复」（红字）/ 什么都不画。
 *
 *  **只有这一份**：条件行与场景小标题行都显示同一种状态（用户口径「场景停用时行尾也要说」），
 *  两处各写一遍的话，字号 / 颜色 / 与右侧图标的间距迟早会各走各的。
 */
@Composable
internal fun RuleStateHint(state: RuleState) {
    when (state) {
        RuleState.NORMAL -> {
            //  正常那一档什么都不画
        }

        RuleState.OFF -> {
            //  与按键列表行尾那半句同一份（见 `DisabledStateText`）
            DisabledStateText()
        }

        RuleState.DUPLICATE -> {
            //  红色：这不是「暂时不生效」，是**配错了** —— 后一块一个字都不会执行，
            //  点「确认」时也会被拦下来（见 `branchRulesBlockedReason`）
            Text(
                I18n.get(Strings.COMMAND_BRANCH_SAME_SCENE, "与上面重复"),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.error,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

/**
 * 删除一条规则的收尾 —— **先播收缩、再动数据**（见 [BranchMenuState.deletingAt]）。
 *
 *  抽成一支只为让 [BranchCards] 留在 detekt 的长度上限内；判据见那边原来那段注释：
 *  收尾再核一次「要删的还是不是它」（这 260ms 里用户还能上移 / 下移 / 再删一条，下标会移位），
 *  宁可不删、不能错删；删的是最后一条时不等 —— 那一下整块要变空态，由外层
 *  （空态 与 表格）那套动画接管，两段串起来就太拖了。
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
            draft.scenes.getOrNull(at) ?: run {
                menus.deletingAt = NO_EXITING_ROW
                return@LaunchedEffect
            }
        if (draft.scenes.size > 1) delay(STEP_ROW_EXIT_MS + ROW_EXIT_SETTLE_MS)
        if (menus.deletingAt == at) {
            val now = draft.scenes.indexOf(target)
            if (now >= 0) draft.remove(now)
            menus.deletingAt = NO_EXITING_ROW
        }
    }
}

/**
 * 删除**一条条件**的收尾 —— 与 [BranchDeleteEffect] 同一套：先让那一行收起
 * （`exitingCond` 驱动行上的 `visible`），播完再从草稿里摘掉。
 *
 *  **不能按「确认那一刻的下标」删**：这几百毫秒里菜单虽然关了，行上的「更多」仍然可点
 *  （上移 / 下移 / 删另一条），下标会漂移 —— 按旧下标删就是删掉了另一条条件。
 *  所以确认那一刻先把**那一条条件本身**记下来，播完之后按身份重新定位；找不到、或者那一位
 *  已经不是它了就不删（宁可不删，不能错删）。
 */
@Composable
internal fun SettingsPage.BranchConditionDeleteEffect(
    draft: BranchDraft,
    menus: BranchMenuState,
) {
    LaunchedEffect(menus.exitingCond) {
        val target = menus.exitingCond ?: return@LaunchedEffect
        val cond =
            draft.scenes
                .getOrNull(target.at)
                ?.conditions
                ?.getOrNull(target.index) ?: run {
                menus.exitingCond = null
                return@LaunchedEffect
            }
        delay(VISIBILITY_EXIT_MS + ROW_EXIT_SETTLE_MS)
        if (menus.exitingCond != target) return@LaunchedEffect
        val now = draft.scenes.getOrNull(target.at)?.conditions ?: emptyList()
        val at = now.indexOf(cond).takeIf { it >= 0 } ?: target.index
        if (now.getOrNull(at) != cond) {
            XLog.w("删除条件：那一行在动画期间被改过（位置已变），这一条不删")
        } else {
            deleteBranchCondition(draft, target.at, at)
        }
        menus.exitingCond = null
    }
}

/**
 * 行尾「更多」菜单里「停用 / 启用这一条」那一行 —— 与「按键绑定」页那一行同一个语义：
 * 写的是这一条自己的 `SceneRule.enabled`。
 *
 *  放在这个文件而不是菜单那边：菜单那一支的行数已经贴着 detekt 的 `LongMethod` 上限。
 *
 *  **停用要确认、启用不要**（三处同一条规矩）：停用之后这一条按下去不再命中，而它下面就是
 *  「删除」；启用只是把它开回来，没有可失去的东西。两种都只改**草稿**，
 *  等右下角那颗「确认」才落盘。
 */
internal fun SettingsPage.branchToggleRow(
    rules: List<SceneRule>,
    at: Int,
    menus: BranchMenuState,
): MenuEntry.Row {
    val off = rules.getOrNull(at)?.enabled == false
    return MenuEntry.Row(
        title =
            if (off) {
                I18n.get(Strings.COMMAND_BRANCH_ENABLE, "启用场景")
            } else {
                I18n.get(Strings.COMMAND_BRANCH_DISABLE, "停用场景")
            },
        icon = if (off) PageIcons.KeyUnlocked else PageIcons.KeyLocked,
    ) {
        menus.menuAt = -1
        if (off) {
            rules.getOrNull(at)?.let { branchDraftNow().update(at, it.copy(enabled = true)) }
        } else {
            //  停用先弹确认窗（`BranchToggleDialog`，由 `BranchCards` 挂在页面里）
            menus.toggleAt = at
        }
    }
}

/** 表尾那句注脚 —— 与「自定义命令」页那条权限提示同一个样式（不占卡片、居中、`footnote1`）。 */
@Composable
internal fun SettingsPage.BranchDefaultTip() {
    Text(
        //  只留这一句，不接「：侧键 - 单击」那截详细 —— 那一档顶栏副标题已经写着
        text = I18n.get(Strings.COMMAND_BRANCH_DEFAULT, "以上都不成立时用按键绑定"),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 6.dp),
        textAlign = TextAlign.Center,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * 这一块里要画哪几档动作 —— **与「场景动作」页的档位清单同一份**（`sceneGesturePages()`）。
 *
 *  组合键只有单击 / 长按（双击与多击在组合上没有定义，见 `ComboKey`）；一颗键则是
 *  `GestureTrigger.slotsOn(...)`（单击 / 双击 / 长按，这颗键打开了多击才多出三击 / 四击）。
 *  判据与判定侧同一条：摆一行写进去也永远不会命中的动作，比少一行更糟。
 */
internal fun SettingsPage.blockGestures(): List<GestureTrigger> = sceneGesturePages()

/**
 * 一档动作那一行写什么 —— 应用那一族要带上「具体是哪一个」（`微信(小窗 扫一扫)`）。
 *
 *  整句由 `stepTitleWithTarget`（= core 的 `CommandTitle`）拼，与步骤行、执行提示同一份实现，
 *  所以这里也是「父动作名: 子动作名」（`系统开关: NFC - 切换`）。
 *  目标**整段**交给它（`appTargetLabelOf(action)` 已含括号）—— 走 `stepTitle` 的话
 *  名字回调那层还会再补一次「(小窗)」，真机上就是 `应用(小窗 组件)(小窗)`。
 */
internal fun SettingsPage.actionTextOf(action: Action): String =
    when {
        action is Action.Follow -> I18n.get(Strings.COMMAND_BRANCH_ACTION_FOLLOW, "跟随默认")
        !action.isBound -> I18n.get(Strings.ACTION_NONE, "无操作")
        else -> stepTitleWithTarget(action.token) { target -> appTargetLabelOf(target) }
    }
