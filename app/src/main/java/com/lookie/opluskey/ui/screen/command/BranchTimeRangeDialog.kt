package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.core.model.TimeRange
import com.lookie.opluskey.core.present.comboKeyNames
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.core.present.comboLabelOf
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.kit.DialogButtonRow
import com.lookie.opluskey.ui.kit.FrostedDialog
import com.lookie.opluskey.ui.kit.dialogSideMargin
import com.lookie.opluskey.ui.screen.editingKeyName
import com.lookie.opluskey.ui.state.binding.BranchDraft
import com.lookie.opluskey.ui.state.binding.SceneBinding
import com.lookie.opluskey.ui.state.picker.AppPickerState
import com.lookie.opluskey.ui.write.branchKeyNow
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TimePicker
import io.github.suqi8.coui.kmp.basic.TimeValue
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「时间区间」那个选择窗 —— 条件菜单里点【时间区间】弹它。
 *
 * ## 为什么是滚轮而不是输入框
 *
 *  两个 [TimePicker] 是库自己的那两个滚轮（`coui` 的 `TimePicker`），不是手写的输入：
 *  时间这种东西打字的出错面（少补零、全角冒号、24:00）远大于拨两下，
 *  而且滚轮本来就只有合法值 —— 不需要再写一遍校验。
 *
 * ## 两个轮子并排，不上下叠
 *
 *  一个 `TimePicker` 在库里的固定高度是 5 × 45dp ≈ 225dp，上下叠两个再加标题与按钮就顶出
 *  弹窗的最大高度了。并排之后高度只占一份，左右各一列，读作「从 … 到 …」。
 *
 * ## 初值
 *
 *  · 这一条**已经有区间** → 用它（改时间时看到的是当前值，不是又跳回默认）；
 *  · 还没有（刚点「时间区间」）→ [DEFAULT_TIME_RANGE]：`22:00-07:00`。
 *    选这个是因为它跨午夜、又是最常写的那种「夜里」；而且一打开就能看见「这是跨午夜的区间」，
 *    比给 `00:00-00:00` 那种看不出语义的值更省一次思考。
 *
 * @param initial 线格式（`22:00-07:00`）；空串 = 还没有区间
 * @param onConfirm 确定时回调，参数是新区间的线格式（见 [TimeRange.format]）
 */
@Composable
internal fun SettingsPage.BranchTimeRangeDialog(
    show: Boolean,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // 每次打开都以「当前的区间」为起点：改到一半关掉，再打开不该还挂着那半截。
    val seed = remember(show, initial) { TimeRange.parse(initial) ?: DEFAULT_TIME_RANGE }
    var startHour by remember(show, initial) { mutableIntStateOf(seed.startMinute / 60) }
    var startMinute by remember(show, initial) { mutableIntStateOf(seed.startMinute % 60) }
    var endHour by remember(show, initial) { mutableIntStateOf(seed.endMinute / 60) }
    var endMinute by remember(show, initial) { mutableIntStateOf(seed.endMinute % 60) }

    FrostedDialog(
        show = show,
        title = I18n.get(Strings.COMMAND_COND_TIME_NAME),
        onDismiss = onDismiss,
        // 没有输入框，弹窗照常把键盘收掉
        hideImeOnShow = true,
        //  按钮是**底部那一区**（三段式里竖向留白由弹窗统一给）
        footer = {
            DialogButtonRow(
                onNegative = onDismiss,
                onPositive = {
                    onConfirm(
                        TimeRange.format(
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                        ),
                    )
                },
            )
        },
    ) {
        //  内容槽本身就是 `ColumnScope`，不必再套一层 `Column`（多那一层只是多两行）。
        Row(
            modifier = Modifier.fillMaxWidth().dialogSideMargin(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeWheel(
                spec =
                    TimeWheelSpec(
                        label = I18n.get(Strings.COMMAND_COND_TIME_START),
                        hour = startHour,
                        minute = startMinute,
                        onHourChange = { startHour = it },
                        onMinuteChange = { startMinute = it },
                    ),
                modifier = Modifier.weight(1f),
            )
            TimeWheel(
                spec =
                    TimeWheelSpec(
                        label = I18n.get(Strings.COMMAND_COND_TIME_END),
                        hour = endHour,
                        minute = endMinute,
                        onHourChange = { endHour = it },
                        onMinuteChange = { endMinute = it },
                    ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 一列时间要说的全部东西。
 *
 *  打成一个包而不是六个形参：这两列本来就同属「这一列画什么、转了之后告诉谁」。
 */
private class TimeWheelSpec(
    val label: String,
    val hour: Int,
    val minute: Int,
    val onHourChange: (Int) -> Unit,
    val onMinuteChange: (Int) -> Unit,
)

/**
 * 一列时间 —— 小标题 + 库的 `TimePicker`。
 *
 *  24 小时制：这一页的读者是「配一条规则」，`AM/PM` 那第三个轮子会让两列并排更挤，
 *  而 `00-23` 本来就没有歧义。
 */
@Composable
private fun TimeWheel(
    spec: TimeWheelSpec,
    modifier: Modifier = Modifier,
) {
    val hour = spec.hour
    val minute = spec.minute
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = spec.label,
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
        TimePicker(
            value = TimeValue(hour, minute),
            onValueChange = { next ->
                //  两个回调分开给：轮子只报「整个时间变了」，而这里两列各自是独立的两个状态
                if (next.hour != hour) spec.onHourChange(next.hour)
                if (next.minute != minute) spec.onMinuteChange(next.minute)
            },
            is24Hour = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 还没配过区间时的起点 —— `22:00-07:00`。
 *
 *  「夜里」是最常见的用法，而这一段跨午夜：一打开就能看出这一档支持跨过 0 点，
 *  比给一个 `00:00-00:00`（全天）更不容易让人以为「时间区间只能写在同一天里」。
 */
private val DEFAULT_TIME_RANGE = TimeRange(22 * 60, 7 * 60)

/**
 * 分叉编辑那几张菜单的状态。
 *
 *  与 `AppPickerState` 同一条理由：**一组同生共死的字段放进一个对象** ——
 *  散着的时候「谁该在什么时候被清掉」只能靠人读代码。
 */
internal class BranchMenuState {
    /** 左列（条件）的锚点 —— 换成「点的是哪一格」那一份。 */
    var condAnchor by mutableStateOf(PreciseClickState())

    /** 行尾那颗「更多」菜单的锚点。 */
    var menuAnchor by mutableStateOf(PreciseClickState())

    /** 行尾「更多」菜单在说第几条；`-1` = 没开。 */
    var menuAt by mutableIntStateOf(-1)

    /** 「删除」确认窗在问第几条；`-1` = 没在问（关着）。 */
    var deleteAt by mutableIntStateOf(-1)

    /**
     * 「停用 / 启用」确认窗在问第几条；`-1` = 没在问（关着）。
     *
     *  与 [deleteAt] 分开一位：两者是两种后果完全不同的动作，同一时刻也只可能开着一个 ——
     *  但合成一位就得再记「现在开的是哪一种」，读起来更绕。
     */
    var toggleAt by mutableIntStateOf(-1)

    /**
     * 正在**播退场**的那一条；`-1` = 没有。
     *
     *  删除是「先播收缩、再动数据」：确认之后这一位先指过去，行从满高收到 0（[STEP_ROW_EXIT_MS] 左右），
     *  播完才真的从草稿里删。反过来的话行会在同帧消失，用户只看到「上面一行跳上来」。
     */
    var deletingAt by mutableIntStateOf(-1)

    /**
     * 条件菜单在给哪一条挑条件：[AppPickerState.NOT_FOR_BRANCH] 关着、
     * [AppPickerState.NEW_BRANCH] 新加一条、`>= 0` 改第几条。
     *
     *  **不能拿 `-1` 当「关着」** —— 那正好是 `NEW_BRANCH` 的取值，新加那一趟会把菜单判成关着。
     */
    var condAt by mutableIntStateOf(AppPickerState.NOT_FOR_BRANCH)

    /**
     * 时间区间选择窗在给哪一条挑时间段 —— 与 [condAt] 同一套取值
     * （[AppPickerState.NOT_FOR_BRANCH] = 关着、`>= 0` 改第几条）。
     *
     *  **单独一位**而不是复用 `condAt`：条件菜单与时间窗是两件事 —— 菜单那一层先要关掉
     *  （点「时间区间」时已经 `condAt = NOT_FOR_BRANCH`），窗还开着；用同一位的话，
     *  关菜单就等于把窗也关了，用户看不到输入。
     */
    var timeAt by mutableIntStateOf(AppPickerState.NOT_FOR_BRANCH)

    /**
     * 这一趟在改**这块里的第几条条件**：`>= 0` = 改那一条，`-1` = 再添一条。
     *
     *  只在 [condAt] 有效时有意义 —— 每次打开条件菜单时由菜单自己重置（见 `BranchConditionMenu`）。
     *  一块 = 一行 = 一条规则，它可以带好几条条件（**全部成立**才执行），判定与存储一直都支持，
     *  这里补的是「界面能指到第几条」。
     */
    var condIndexAt by mutableIntStateOf(-1)

    /** 时间区间窗要落到的**那一块里的第几条条件** —— 与 [condIndexAt] 同一套取值。 */
    var timeCondIndex by mutableIntStateOf(-1)

    /**
     * 删除**一条条件**的确认窗在问「哪一行的第几条」；`null` = 关着。
     *
     *  为什么不像删整行那样用两个 `Int`（`-1` 当关着）：行号与条号各自都可以是 `-1`（新加一行是
     *  `NEW_BRANCH`、没选槽位是 `-1`），两个哨兵值凑在一起分不出「关着」与「问第 0 行的第 -1 条」。
     *  这一对只在真的点了「删除」之后才有值，用一个可空对象最直接。
     */
    var deletingCond: CondRef? by mutableStateOf(null)

    /**
     * 正在**播退场**的那一条条件；`null` = 没有。
     *
     *  与删整行那一位（[deletingAt]）同一套：确认之后先让这一行收起，播完才真的从草稿里摘掉。
     *  反过来的话它同帧消失，下面的条件当场往上顶 —— 那正是用户报的那一下。
     */
    var exitingCond: CondRef? by mutableStateOf(null)

    /**
     * **刚插入**的那一条条件；`null` = 没有。
     *
     *  新插的行要先以「不可见」组合一帧、再显出来，否则 `AnimatedVisibility` 首帧就可见、
     *  不会播进场（与 `BranchRow` 里 `MutableTransitionState(!spec.fresh)` 同一个道理）。
     *  播完不清：留着它让那一行停在「可见」态，下一次插入自然覆盖。
     */
    var freshCond: CondRef? by mutableStateOf(null)

    /**
     * 收起条件菜单（并抹掉「改第几条」）—— 两处归一次，末尾谁都不许漏掉下标：
     * 留着上一位的话，下次打开会先落在「上次改的那一条」上。
     */
    fun closeConditionMenu() {
        condAt = AppPickerState.NOT_FOR_BRANCH
        condIndexAt = -1
    }
}

/** 「第 [at] 行里的第 [index] 条条件」—— 删除确认窗要记住的那一对（见 [BranchMenuState.deletingCond]）。 */
internal data class CondRef(
    val at: Int,
    val index: Int,
)

// 「这一档配了系统场景」那句常驻提示的文案 —— 从 `CommandBranches.kt` 拆出来：
//  那边是条件表本身（一行一条规则的编辑区），这一支只回答「这一档会不会被场景先拦下」。

/**
 * 这一档配了「系统场景」时该说的那句话；null = 没配（或那几条全停用了）。
 *
 *  消费方是页面里那条**常驻**提示（按键绑定页 / 组合键编辑页）：场景先判、命中就轮不到
 *  这一档绑的动作。不说一句，改完绑定按下去走的不是它。
 *  只算**启用中**的规则 —— 停用的拦不住任何东西，提它只会误导。
 */
internal fun SettingsPage.branchScenarioNotice(key: KeyId): String? =
    scenarioNotice(
        configState.config.value
            .key(key)
            .scenes,
    )

/** 组合键那一侧的同一句话（规则挂在 combos 上，不在 keys 上）。 */
internal fun SettingsPage.comboScenarioNotice(comboId: String): String? {
    val combo =
        configState.config.value.combos
            .firstOrNull { it.id == comboId } ?: return null
    return scenarioNotice(combo.scenes)
}

/** 规则表里有启用中的那几条，就报出条数；一条都没有 / 全停用 → null（整段不画）。 */
private fun scenarioNotice(rules: List<SceneRule>): String? {
    val count = rules.count { it.enabled }
    if (count == 0) return null
    return I18n.format(Strings.COMMAND_BRANCH_SCENARIO_COUNT, count)
}

/**
 * 菜单里「系统场景」那一行的**子标题** —— 与 [scenarioNotice] 同一条判据（只数启用中的），
 * 差别只在一条都没有时**不返回 null**：菜单里那一行始终在，子标题得有个说法
 * （「没有添加系统场景」），空着会读成「这一行没有说明」。
 *
 *  措辞比页面横幅短一档（不带「当前手势」）：菜单行的子标题窄，带上那四个字会折行。
 */
internal fun scenarioMenuSummary(rules: List<SceneRule>): String {
    val count = rules.count { it.enabled }
    return if (count == 0) {
        I18n.get(Strings.COMMAND_BRANCH_SCENARIO_NONE_MENU)
    } else {
        I18n.format(Strings.COMMAND_BRANCH_SCENARIO_COUNT_MENU, count)
    }
}

// 「系统场景」页**在编谁** —— 哪颗键 / 哪一组组合键、当前用的是哪一份草稿。
//
//  从 `BranchScreen.kt` 拆出来：那边是页面骨架与保存，这里只有「这一页说的是哪一份东西」。
//  五处读者分散在顶栏、FAB、退出判据与页内注脚上，收在一处才不会各算一遍。

/**
 * 这一页「在编谁」的标识 —— 某颗键、或某档组合键（两边场景表形状一样，
 *  只有「读哪张表 / 写回哪张表」不同，判据是 `BranchDraftState.comboId`）。
 *
 *  **翻转之后没有手势这一半**：一张表里每块自带三档，换手势不再换草稿。
 */
internal fun SettingsPage.branchOwnerOf(): String {
    val comboId = branchDraft.comboId
    return if (comboId != null) BranchDraft.comboOwnerOf(comboId) else BranchDraft.ownerOf(branchKeyNow())
}

/**
 * 这一页那一份草稿 —— FAB / 退出判据 / 保存 / 顶栏「添加」都看它。
 *
 *  页内那几行读的也是同一份（这一页只有一页，见 `BranchScreen`）。
 */
internal fun SettingsPage.branchDraftNow(): BranchDraft = branchDraft.now()

/**
 * 「这一趟在编哪一颗键 / 哪一组组合键」—— 顶栏那行小标题与表尾注脚共用这一句。
 *
 *  **成员名字由 [branchOwnerNames] 给**，这里只负责压成一串（单键不套框、多颗各套一对 `[ ]`）：
 *  顶栏副标题要的是**逐个上样式的名单**（每颗画一枚圆角框），拼好的句子只能给纯文字的地方用。
 *
 *  **单键不套**（用户口径：只有一个键名时不要方框）：这一行本来就只装一个名字，
 *  套上 `[ ]` 只是多一层噪声 —— 而 `快捷键` 三个字旁边还有副标题的位置说明它是什么。
 */
internal fun SettingsPage.branchOwnerLabel(): String {
    val cfg = configState.config.value
    val id = branchDraft.comboId
    if (id != null) {
        //  组合走 `comboLabel`：按顺序那一档要念「A 后 B 再 C」，只拼成员名读不出先后
        cfg.combos.firstOrNull { it.id == id }?.let { return comboLabel(cfg, it) }
        return comboLabelOf(listOf(id))
    }
    return comboLabelOf(listOf(editingKeyName()))
}

/**
 * 同一个「在编谁」的**成员键名**（一档组合有几颗就是几个名字）—— 顶栏副标题按名单画框。
 *
 *  「单键叫什么、组合的成员是谁」只有这一份实现：[branchOwnerLabel] 由它压成一串，
 *  两个读者（顶栏、表尾注脚）不会长出不同叫法。
 *
 *  三样都由调用点给（而不是收成 `SettingsPage` 的扩展）：这一支是纯取名、不碰页面状态，
 *  于是「页面扩展成员」那本账不会因为多一个读者就涨一格。
 */
internal fun branchOwnerNames(
    cfg: Config,
    comboId: String?,
    keyLabel: String,
): List<String> =
    if (comboId != null) {
        cfg.combos.firstOrNull { it.id == comboId }?.let { comboKeyNames(cfg, it) } ?: listOf(comboId)
    } else {
        listOf(keyLabel)
    }

/**
 * 点场景块里**某一档的动作行** → 进「场景动作」页编那一档（用户口径：不再是弹抽屉）。
 *
 *  「编第几块」与「停在哪一档」都记在页面级（[SettingsPage.sceneBinding]（`at` / `gesture` / `baseline` 三样一个值）
）：栈里这一页只有一个位置，参数走 `Screen` 枚举带不进去
 *  （与「组合键」编辑页的 `ComboEditState.editingId` 同一个做法）。
 *
 *  **顺手记下这一块进来时长什么样**（`SceneBinding.baseline`）：那一页的「改没改」
 *  与「放弃要不要回滚」都按它判 —— 整份草稿的 `dirty` 会把上一页的改动算到这一页头上。
 *
 *  上一页（这一页）**留在栈里**：草稿是页面级的，退回来时改动还在，右下角那颗「确认」随之出现。
 */
internal fun SettingsPage.openSceneBinding(
    at: Int,
    trigger: GestureTrigger,
) {
    sceneBinding = SceneBinding(at, trigger, branchDraftNow().scenes.getOrNull(at))
    XLog.w("进「场景动作」页：第 ${at + 1} 块 · ${trigger.id}")
    navTo(Screen.SCENE_BINDING)
}

/**
 * 一个场景块要编**哪几档** —— 组合键只有短按 / 长按（`ComboKey` 上没有双击、也没有多击）；
 *  一颗键则是 `slotsOn`：那几档自己要了才有（`KeyConfig.extraClicksOn`），没要的就不列。
 *
 *  判据与 `blockGestures()` 同一条：两处各写一遍迟早出现「带子上有五档、翻到第五页是空的」。
 *
 *  **这一位是「一块有几档」的唯一来源**：场景页的动作行（`RuleActionRows`）与「场景动作」页的
 *  手势带都按它排。清单本身在 core（`GestureTrigger.slotsOn` / `comboSlots`），
 *  于是「再加一档」只改那一个列表 —— 界面这一层不再有第二份档位清单。
 */
internal fun SettingsPage.sceneGesturePages(): List<GestureTrigger> {
    val comboId = branchDraft.comboId
    if (comboId != null) {
        //  长按那一档按**这一组**自己的判据收口（`ComboKey.hasLong`）：按顺序触发的那一档没有长按，
        //  这里多摆一行，写进去的动作永远不会命中。
        val combo =
            configState.config.value.combos
                .firstOrNull { it.id == comboId }
        return GestureTrigger.comboSlots(combo?.hasLong ?: true, combo?.memberGestures)
    }
    //  键那一支读**在编那颗键**的开关：与「按键绑定」页同一个判据，两页的档数才不会一页三一页六
    val key = configState.config.value.key(branchKeyNow())
    return GestureTrigger.slotsOn(key.extraClicksOn, key.doubleClickOn, key.thenLongClicks)
}

/**
 * 进来时该停在第几页 —— `SceneBinding.gesture` 按当前那张表换算成页号。
 *
 *  存的是手势而不是页号：同一份草稿可能先是某颗键的（三档）、再是某组组合的（两档），
 *  页号的含义会变，手势不会。
 */
internal fun SettingsPage.sceneGesturePage(): Int = sceneGesturePages().indexOf(sceneBinding.gesture).coerceAtLeast(0)

/**
 * 第 [at] 块自己**开没开** —— 停用的场景块命中也不会执行（判定侧只认启用的块），界面上整片灰。
 *
 *  找不到那块时回 `true`：那一帧这一页正在往下退（见 `BindingPagerScreen` 的「不在了」那一支），
 *  再压暗一次只会看到闪一下。
 */
internal fun SettingsPage.sceneBlockEnabled(at: Int): Boolean = branchDraftNow().scenes.getOrNull(at)?.enabled != false
