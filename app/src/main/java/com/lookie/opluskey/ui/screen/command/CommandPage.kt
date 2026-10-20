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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.CommandUiState
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.theme.COUITheme
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

        // 表单的每一栏都带一条小标题（「命令名称」「命令详细」）：输入框里的字是**占位文案**
        //  （「请输入名称」），滚到这一屏时那行字可能已经被填满，光看框分不清哪个是名字、
        //  哪个是命令本体。原先这一条写的是「修改命令 / 新增命令」—— 那说的是**这一页在干什么**，
        //  而顶栏已经写着「自定义命令」、工具栏那颗键写着「保存 / 修改」，这里该说的是**这一栏是什么**。
        SmallTitle(I18n.get(Strings.COMMAND_NAME_TITLE, "命令名称"))

        // 输入项用 COUI 的「卡片输入」形态（[IconInputView]）：一个输入项一张卡、无边框，
        // 行首图标会把标题与输入框一起挤开 —— 库的 `InputView` 做不到，所以自己拼。
        // 抽成独立的 composable 是为了把 `draft.name` 的读取关在里面（见它的注释）。
        CommandNameField()

        // 单条命令的正文 —— 与「名称」同一套卡片输入；多条命令走下面那张「步骤」卡。
        if (kind == CommandKind.SINGLE) {
            Spacer(Modifier.height(12.dp))
            SmallTitle(I18n.get(Strings.COMMAND_DETAIL_TITLE, "命令详细"))
            SingleCommandField()
        }

        // `keyevent:` 注入同毫秒生效，而「最近任务 / 通知中心 / 控制中心」要跨四个进程、`shell:` 更
        // 是另起进程 —— 不等待的话连续几步会乱。只有多条命令有这个值，所以只在这一栏显示。
        //  它自己那条行标题（「命令间隔时长」）说的是**这一行是什么**，小标题说的是**这一组是什么**
        //  —— 以后这一组还会长（比如「失败后停止」），先按「命令设置」归拢。
        if (kind == CommandKind.SERIAL) {
            Spacer(Modifier.height(12.dp))
            SmallTitle(I18n.get(Strings.COMMAND_SETTINGS_TITLE, "命令设置"))
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
 * 单条命令的正文输入（「命令」那一栏）—— **单行**（多行那一档已按用户口径撤掉）。
 *
 *  输入框本体是库的搜索框样式（见 `IconInputView`），一行就够：命令本身不该带换行 ——
 *  带换行的那串以前还得靠 `sh -c` 按行依次执行，语义反而更难说清；要串两步请用「多条命令」。
 *
 *  仍然只归一换行符：粘贴来的多半是 CRLF，混着两种会让存下去的值里夹进不可见的 `\r`。
 */
@Composable
private fun SettingsPage.SingleCommandField() {
    val raw = commandDraft.draft.single
    // 格式不对时在这一栏下面出红字 —— 保存按钮同时是灰的（见 `commandPayloadReady`），
    //  用户得知道**为什么**存不进去。空白不算错（还没开始写）。
    val formatError =
        if (raw.isNotBlank() && !Action.hasSupportedFormat(raw)) {
            I18n.get(Strings.COMMAND_FORMAT_REQUIRED, "开头要写 shell: / cmd: 这类前缀")
        } else {
            ""
        }
    Column {
        IconInputView(
            spec =
                InputSpec(
                    PageIcons.CommandSingle,
                    I18n.get(Strings.COMMAND_CMD_HINT, "请输入命令"),
                    MAX_SINGLE_LENGTH,
                ),
            value = raw,
            onValueChange = { value ->
                // 粘贴来的多半是 CRLF：只归一，不动内容
                commandDraft.draft.single = value.replace("\r\n", "\n").replace('\r', '\n')
            },
            errorMessage = formatError,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
        )
        // 权限边界的提示：这一栏写的是 shell 命令，而它跑在宿主进程里、身份是「手势体感」这个 App ——
        // `am` / `cmd` / `settings` 这类要 shell 身份的命令会失败，得提权（root）才跑得起来。
        // 放在输入框下面一行小字：不占卡片、也不挡输入。
        //  左右缩进取 `SmallTitleDefaults.InsideMargin`（32dp），但**文字在这条带子里居中**
        //  —— 与输入框那边对齐的是**边界**，不是第一个字（用户口径：像 CSS 的
        //  `padding: 0 32px` + `text-align: center`）。所以要先铺满再居中，只给 padding 的话
        //  Text 的宽度就是文字本身，居中没有可居的余量。
        //  上下**不取那一档**（32dp 会把提示推得离输入框很远）：只留 6dp，贴着输入框说话。
        Text(
            text = I18n.get(Strings.COMMAND_SINGLE_HINT, "am / cmd 等命令需 root 给「手势体感」授权"),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = HINT_SIDE_PADDING, end = HINT_SIDE_PADDING, top = 6.dp),
            textAlign = TextAlign.Center,
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 单条命令能输入多少字（超出的部分在输入框里直接吃掉）。 */
private const val MAX_SINGLE_LENGTH = 512

/** 权限提示那行字的左右缩进 —— 与 `SmallTitleDefaults.InsideMargin` 同值（32dp）。 */
private val HINT_SIDE_PADDING = 32.dp

/** 多条命令的「命令间隔时长」卡 —— 只有一行：等多久再派发下一条。 */
@Composable
private fun SettingsPage.StepDelayCard(tick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        SliderRow(
            position = cardListPositionOf(0, SERIAL_CARD_ROWS),
            spec =
                SliderSpec(
                    title = I18n.get(Strings.COMMAND_DELAY_LABEL, "命令间隔时长"),
                    icon = PageIcons.DelayStep,
                    summary = I18n.get(Strings.COMMAND_DELAY_DESC, "等多久再执行下一条"),
                    min = Command.MIN_STEP_DELAY_MS,
                    max = Command.MAX_STEP_DELAY_MS,
                    value = commandDraft.draft.stepDelayMs,
                    default = Command.DEFAULT_STEP_DELAY_MS,
                ),
        ) { v ->
            tick()
            commandDraft.draft.stepDelayMs = v
            // 只改草稿、不落盘（随「保存」一起入库）—— 没有失败可言，恒 true。
            true
        }
    }
}
