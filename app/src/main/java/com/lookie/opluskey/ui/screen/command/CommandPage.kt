package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.CommandUiState
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.utils.overScrollVertical

// 「自定义命令」页的一页（单条 / 多条各一份）—— 从 `CommandScreen.kt` 拆出来。
//
//  这一块原来整个嵌在 `HorizontalPager` 的 content lambda 里（连同 pager 外面那五个确认窗），
//  一个函数 626 行。按「这一段是谁的内容」切开之后：
//   · 本文件     —— 一页的滚动骨架 + 表单各段（名称 / 单条输入 / 间隔 / 格式 / 步骤 / 已保存）；
//   · `SavedCommands.kt` —— 「已保存的命令」那片网格与里面的单张卡；
//   · `CommandDialogs.kt` —— 页外那五个确认窗。

/**
 * 「自定义命令」页的**一页** —— 单条与多条各走一次。
 *
 * @param kind 这一页是哪一类。页内所有 `kind` 判断都指它，**不是** `draft.kind`
 *   （后者跟着停下来的那一页走，过渡期两者可能不同）。
 * @param scroll 本页的滚动骨架（见 [CommandPageScroll]）。
 * @param onScrollTop 「点一下已保存的命令」之后滚回顶部 —— 表单在页面顶部，不滚回去看不出点开了哪一张。
 */
@Composable
internal fun SettingsPage.CommandPage(
    kind: CommandKind,
    scroll: CommandPageScroll,
    ui: CommandUiState,
    tick: () -> Unit,
    onScrollTop: () -> Unit,
) {
    // 这一页只列自己那一类（`saved` 与同名的外层变量无关）
    //  记住它：这一页在每次重组上都要 `filter` 一遍整个命令库并新建一个 List，
    //  而下面那几处都会打脏本页（滚动手势、开关、输入……）。
    val saved = remember(configState.commands.value, kind) { configState.commands.value.filter { it.kind == kind } }
    Column(
        Modifier
            .fillMaxSize()
            // 大标题随滚动折叠（`nestedScroll` 要在 `verticalScroll` 之前）
            .nestedScroll(scroll.behavior.nestedScrollConnection)
            // 越界回弹：`overScrollVertical` 挂在滚动容器外侧，所以内容不满一屏时照样弹。
            .overScrollVertical()
            .verticalScroll(scroll.state)
            // 页底留白挂在滚动内容内侧（`padding` 在 `verticalScroll` 之后）——
            // 尾部 Spacer 实测不进滚动内容高度，会让最大滚动位置少一截。
            .padding(bottom = PAGE_BOTTOM_PADDING),
    ) {
        // 顶栏叠在内容上，顶部这块要自己让出来；再把类型选择器固定带（`tabBlockDp`）一起让出。
        Spacer(Modifier.height(currentHeaderHeight(scroll.behavior) + scroll.tabBlockDp))

        // 编辑态：editingName 非 null 即正在改某条已存在的命令。
        val editing = commandDraft.draft.editingName != null
        SmallTitle(
            if (editing) {
                I18n.get(Strings.COMMAND_TITLE_EDIT, "修改命令")
            } else {
                I18n.get(Strings.COMMAND_TITLE_NEW, "新增命令")
            },
        )

        // 输入项用 COUI 的「卡片输入」形态（[IconInputView]）：一个输入项一张卡、无边框，
        // 行首图标会把标题与输入框一起挤开 —— 库的 `InputView` 做不到，所以自己拼。
        // 抽成独立的 composable 是为了把 `draft.name` 的读取关在里面（见它的注释）。
        CommandNameField()

        // 单条命令的正文 —— 与「名称」同一套卡片输入；多条命令走下面那张「步骤」卡。
        if (kind == CommandKind.SINGLE) {
            Spacer(Modifier.height(GRID_GAP))
            SingleCommandField()
        }

        // `keyevent:` 注入同毫秒生效，而「最近任务 / 通知中心 / 控制中心」要跨四个进程、`shell:` 更
        // 是另起进程 —— 不等待的话连续几步会乱。只有多条命令有这个值，所以只在这一栏显示。
        if (kind == CommandKind.SERIAL) {
            Spacer(Modifier.height(GRID_GAP))
            StepDelayCard(tick)
        }

        // 「支持的命令格式」卡 —— 说的是「命令输入框里能写什么」，多条命令没有那个
        // 输入框，所以只在单条那一栏出现。
        if (kind == CommandKind.SINGLE) {
            Spacer(Modifier.height(12.dp))
            SmallTitle(I18n.get(Strings.COMMAND_FORMATS_TITLE, "支持的命令格式"))
            CommandFormatsCard(tick, ui, onScrollTop)
        }

        // 多条命令的步骤单独一张卡：它是一串行，塞进上面那张卡会多嵌两层内边距。
        if (kind == CommandKind.SERIAL) {
            Spacer(Modifier.height(12.dp))
            SmallTitle(I18n.get(Strings.COMMAND_LIST_TITLE, "命令列表"))
            //  加 / 删步骤的过渡不在这里：行让位 / 回位由 SerialSteps 每行的 expand / shrink 驱动，
            // 两层高度动画叠在一起会让卡片背景慢半拍。
            Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
                SerialSteps()
            }
        }

        // 「已保存的命令」这一段**始终在**：一条都没有时给一张空状态卡 ——
        //  以前是 `if (saved.isNotEmpty())` 整段不出现，用户看不出这块地方该有东西、也分不清
        //  「还没保存过」与「这块地方根本不存在」。
        Spacer(Modifier.height(12.dp))
        SmallTitle(I18n.get(Strings.COMMAND_SAVED_COMMANDS, "已保存的命令"))
        SavedCommandsSection(saved, ui, tick, onScrollTop)
    }
}

/**
 * 单条命令的正文输入（「命令」那一栏）。
 *
 *  多行粘贴要先洗掉换行：这个输入框是单行的（`IconInputView` 内部 `singleLine = true`），
 *  多行粘进来的换行**显示不出来**，存下去的却带着 —— 界面看着对、跑起来那条命令跑不通。
 *
 *  折成 `; ` 而**不是空格**：shell 里换行是命令分隔符（等价 `;`），当空白处理会把
 *  `setenforce 0` + 换行 + `getenforce` 折成一条带多余参数的命令 —— 解析得出、执行跑不通
 *  （被当成 `setenforce` 的第二个参数），而界面上看不出任何异常。
 *  再掐掉粘贴带进来的尾随空白；名称框走的是 `sanitizeNameInput`，同一套纪律。
 */
@Composable
private fun SettingsPage.SingleCommandField() {
    IconInputView(
        icon = PageIcons.CommandSingle,
        value = commandDraft.draft.single,
        onValueChange = { raw ->
            commandDraft.draft.single =
                if (raw.any { it == '\n' || it == '\r' }) {
                    raw.replace(Regex("[\\r\\n]+"), "; ").trimEnd()
                } else {
                    raw
                }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
        title = I18n.get(Strings.COMMAND_CMD_LABEL, "命令"),
        label = I18n.get(Strings.COMMAND_CMD_HINT, "请输入命令"),
    )
}

/** 多条命令的「命令间隔时长」卡 —— 只有一行：等多久再派发下一条。 */
@Composable
private fun SettingsPage.StepDelayCard(tick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        SliderRow(
            title = I18n.get(Strings.COMMAND_DELAY_LABEL, "命令间隔时长"),
            icon = PageIcons.DelayStep,
            summary = I18n.get(Strings.COMMAND_DELAY_DESC, "等多久再执行下一条"),
            position = cardListPositionOf(0, SERIAL_CARD_ROWS),
            min = Command.MIN_STEP_DELAY_MS,
            max = Command.MAX_STEP_DELAY_MS,
            value = commandDraft.draft.stepDelayMs,
            default = Command.DEFAULT_STEP_DELAY_MS,
        ) { v ->
            tick()
            commandDraft.draft.stepDelayMs = v
            // 只改草稿、不落盘（随「保存」一起入库）—— 没有失败可言，恒 true。
            true
        }
    }
}
