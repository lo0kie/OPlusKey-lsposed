package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.CommandOutput
import com.lookie.opluskey.core.present.commandExecIdentity
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.FrostedDialog
import com.lookie.opluskey.ui.dialog.dialogSideMargin
import com.lookie.opluskey.ui.model.page.SUMMARY_REF_GAP
import com.lookie.opluskey.ui.model.text.durationText
import com.lookie.opluskey.ui.model.text.isTypingCommandPrefix
import com.lookie.opluskey.ui.model.text.serialSummarySteps
import com.lookie.opluskey.ui.model.text.stepIcon
import com.lookie.opluskey.ui.state.command.CommandUiState
import com.lookie.opluskey.ui.wire.appNameOf
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「已保存的命令」卡点开后的**命令预览**（轻点卡片、或菜单里那一行「预览」）。
//
//  外壳走项目通用那一个（[FrostedDialog] → 库的 `OverlayDialog`，强制居中），与「捐赠打赏」那张
//  收款码窗口**同一个窗、同一段进出场动画**（真机口径「动画改成和捐赠二维码的一样」）：
//  居中、缩放淡入、点遮罩或返回收起，都在库那一层，这里不再自己写一层 Popup 与透明度动画。

/** 「以 root 执行」与正文之间那一档 —— 与标题行到正文那一档同一个量级。 */
private val DETAIL_HEAD_GAP = 10.dp

/** 执行身份与「以 toast 提示」两行之间 —— 取 [DETAIL_HEAD_GAP] 的一半：这两行是同一组属性。 */
private val DETAIL_META_GAP = 5.dp

/** 多条命令里，步骤行行首那枚图标的边长 —— 跟 `body2` 的字配（卡上那份摘要用 14dp 是跟着 `footnote1`）。 */
private val DETAIL_STEP_ICON = 18.dp

/**
 * 多条命令里，步骤行之间的距离 —— 「间隔」那一行到步骤列表之间也是这一档。
 *
 *  同一个量只写一次：这两处都是「两条并列的小字之间空多少」，分开定义迟早一处改了一处没改。
 *  单独给行距开过一档 10dp，行与行之间空得太开、一屏能看的步骤少了一截（用户口径「行高太多了
 *  收一点」），收回这一档 —— 行内是图与字的 4dp，行间 6dp，仍是「行距 > 行内间距」。
 */
private val DETAIL_STEP_GAP = 6.dp

/**
 * 正文最多这么高 —— 再长就让正文自己滚，窗不顶到上下边缘。
 *
 *  与 `dialogSideMargin` 那套同一档的量级：420dp 在竖屏上约占可见高度六成，
 *  再加标题与内边距仍在屏幕内。
 */
private val DETAIL_MAX_HEIGHT = 420.dp

/**
 * 命令预览 —— 窗口标题是命令名，正文是全文。
 *
 *  退场动画期间 `show` 已经是 false，但面板还要再画一次 —— 内容不能跟着清空，否则那一帧是个
 *  空面板（与「捐赠打赏」那张窗同一条账）。所以留一份 `last`：普通对象、**不是 state**，
 *  写它不该触发重组。
 */
@Composable
internal fun SettingsPage.CommandDetailOverlay(ui: CommandUiState) {
    val target = ui.detail
    val last = remember { LastCommand() }
    if (target != null) last.value = target
    val shown = last.value ?: return
    FrostedDialog(
        show = target != null,
        title = shown.name,
        //  与「捐赠打赏」那张收款码窗口同一个位置：屏幕正中（库在窄屏上默认是底部滑入）
        centered = true,
        onDismiss = { ui.detail = null },
    ) {
        CommandDetailBody(shown)
    }
}

/** 退场期间要画的那一份（见 [CommandDetailOverlay]）。 */
private class LastCommand {
    var value: Command? = null
}

/**
 * 面板里的正文：整段内容 + 一两句小字（多条写间隔、单条写 root）。
 *
 *  与卡片上那份摘要**不是**同一份渲染：卡上受格子限制要截断（单条 3 行、多条每步 1 行），
 *  这里正好相反 —— 它就是「看全文」的地方，所以不省略、允许折行。
 *  太长时自己滚（[FrostedDialog] 的正文槽不替调用方兜滚动）。
 */
@Composable
private fun SettingsPage.CommandDetailBody(cmd: Command) {
    Column(
        Modifier
            .fillMaxWidth()
            //  `FrostedDialog` 的正文槽**不给左右内边距**（库的原话：那一槽留白是给按钮条横贯用的），
            //  不自己给的话正文会贴着面板左右边线。用全项目那一支（[dialogSideMargin]），
            //  正文左缘才与标题严格对齐。
            .dialogSideMargin()
            .heightIn(max = DETAIL_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
    ) {
        //  「以 root / 以 Shizuku 执行」排在**正文上面**：它说的是「这一整段怎么跑」（前提），
        //  压在正文底下时读起来像一句附注，而它其实是执行条件。
        //  判据（哪一档有这一句、两条通道谁优先）只在 `commandExecIdentity` 一处 ——
        //  网格格子的小字读的也是它，两处各写一遍时同一条命令会念成两种执行方式。
        val identity = commandExecIdentity(cmd)
        val hasOutput = cmd.output != CommandOutput.NONE
        if (identity != null) {
            Text(
                text = identity,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            //  下面紧跟着提示类型那一行时两行贴紧一档（同一组属性）；否则与正文留正常那一档。
            Spacer(Modifier.height(if (hasOutput) DETAIL_META_GAP else DETAIL_HEAD_GAP))
        }
        //  「显示执行返回结果」选中的那一档 —— 与执行身份同一处（都在正文上面）：
        //  它说的同样是「这条命令跑起来是什么样」，而不是正文的一部分。`不显示` 不占一行。
        if (hasOutput) {
            Text(
                text = I18n.format(Strings.COMMAND_OUTPUT_PREFIX, I18n.get(cmd.output.labelKey())),
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(DETAIL_HEAD_GAP))
        }
        when (cmd.kind) {
            CommandKind.SINGLE -> {
                Text(
                    text = cmd.single,
                    style = COUITheme.textStyles.body2,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            CommandKind.SERIAL -> {
                if (cmd.stepDelayMs > 0) {
                    Text(
                        //  带前缀：单看一个「3 秒」不知道是谁的间隔（真机口径「delay 加前缀 执行间隔:」）
                        text = I18n.format(Strings.COMMAND_DELAY_PREFIX, durationText(cmd.stepDelayMs)),
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(DETAIL_STEP_GAP))
                }
                //  步骤行与行之间留一档：原来几行贴着排，一长串步骤读起来像一段文字（真机口径
                //  「步骤列表的行高增加」）。间距给在**行与行之间**，最后一行下面不留 —— 窗底那截
                //  白由面板自己的内边距给，两处都留会多出一档。
                Column(verticalArrangement = Arrangement.spacedBy(DETAIL_STEP_GAP)) {
                    //  步骤行与正文同一档字号（`body2`）：这一窗就是「看全文」的地方，卡上那份摘要
                    //  用 `footnote1` 是格子挤，这里不必跟着挤（真机口径「多条命令步骤太小了」）。
                    serialSummarySteps(cmd.steps, ::appNameOf).forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                stepIcon(line.token, configState.commands.value),
                                contentDescription = null,
                                modifier = Modifier.size(DETAIL_STEP_ICON),
                                tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.width(SUMMARY_REF_GAP))
                            Text(
                                text = line.title,
                                style = COUITheme.textStyles.body2,
                                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 载荷**还差什么**（`null` = 够了）—— 「保存」能不能点、灰着时说什么，判据只有这一处。
 *
 *  单条那一档三句话各自对应一种真实的半成品：没写 / 没写对格式 / 写了前缀没写正文。最后那档
 *  （`shell:` 这种只有前缀的）没有一句在说它 —— 保存按钮灰着、点一下只回一句
 *  「这一项现在不可用」，说不出是哪一种。多条那一档只差「有没有步骤」。
 *
 *  **这一支必须能在填好之后返回 `null`**：`saveBlockedReason()` 拿它当唯一判据，
 *  永远非空就等于「保存」按钮永远点不动。
 */
private fun SettingsPage.payloadBlockedReason(): String? =
    when (commandDraft.draft.kind) {
        CommandKind.SINGLE -> {
            singlePayloadBlockedReason()
        }

        CommandKind.SERIAL -> {
            if (commandDraft.draft.steps.isEmpty()) I18n.get(Strings.COMMAND_NEED_STEP) else null
        }
    }

/** 单条那一档的载荷判据（[payloadBlockedReason] 的一半，判据仍只有那一处）。 */
private fun SettingsPage.singlePayloadBlockedReason(): String? {
    val raw = commandDraft.draft.single
    if (raw.isBlank()) return I18n.get(Strings.COMMAND_CMD_HINT)
    //  **只打了个前缀**（`keyevent:`）不算「格式不支持」：那是还没写完，说「必须以支持的格式开头」
    //  等于自相矛盾 —— 它明明就是从支持的前缀开头的。判据与输入框底下那条红字共用一处
    //  （`isTypingCommandPrefix`），否则一处不报了、点保存又被同一句拦住。
    if (!Action.hasSupportedFormat(raw) && !isTypingCommandPrefix(raw)) {
        return I18n.get(Strings.COMMAND_FORMAT_REQUIRED)
    }
    // 写法合法之后，再看这段字**解析出来到底做不做事**：`shell:` / `cmd:` 这种只有前缀的
    //  （[Action.isBarePrefix]），以及 `keyevent:` 空正文 / 认不出的键码 / `none`（解析成
    //  [Action.None]）——存下去都是一条注定什么都不做的命令。
    val action = Action.parse(raw)
    return if (action.isBarePrefix || !action.isBound) {
        I18n.get(Strings.COMMAND_PAYLOAD_INCOMPLETE)
    } else {
        // 这里**必须**能返回 `null`：它是「能不能存」的唯一判据，一直非空就等于保存按钮永远灰着。
        null
    }
}

/**
 * 名字那一格的红字 —— 与「保存被禁用时那句话」共用这一支（同一件事只说一处）。
 *
 *  判据本身在 `CommandLibrary`（重名扫一遍命令库），这里只负责措辞。
 */
internal fun SettingsPage.commandNameProblem(): String? =
    if (commandNameDuplicated(commandDraft.draft.name, commandDraft.draft.editingName)) {
        I18n.get(Strings.COMMAND_NAME_DUPLICATE)
    } else {
        null
    }

/**
 * 「保存」此刻**为什么**点不动（`null` = 能点）—— 保存按钮的 `enabled` 就是「它是不是 `null`」。
 *
 *  灰着被点一下时说的也是这一句（见 [ToolbarButtons] 那层 `DisabledHint`）：兜底的
 *  「这一项现在不可用」对「写了 `shell:`、正文还空着」的人等于什么都没说。
 */
internal fun SettingsPage.saveBlockedReason(): String? {
    if (commandDraft.draft.name.isBlank()) return I18n.get(Strings.COMMAND_NAME_HINT)
    payloadBlockedReason()?.let { return it }
    return commandNameProblem()
}

/**
 * 「重置」这一下做的事 —— 顶栏那颗「重置 / 取消」调它。
 *
 *  **只清当前这一页那份表单**：两页各自独立（见 `CommandForm`），在「单条」页点重置不该把
 *  「多条」页写了一半的步骤一起清掉 —— 那时候另一页的内容根本不在屏幕上。
 *
 *  清的是 [CommandDraft.form]，步骤行不会「瞬时消失」—— `steps` 一变，
 *  `StepRowDisplay.sync` 把对不上的行标成退场，各自 shrink 完才摘除（见那个类的注释）。
 *
 *  不要再引入「先置挂起标记、等退场播完再由页面级协程收尾」那一套：那套的收尾协程一旦没接上
 *  （或那段时间里用户又敲了字），`reset()` 就永远不执行 —— 表现正是「点了重置、表单还在」。
 */
internal fun SettingsPage.resetCommandDraft() {
    commandDraft.draft.form.reset()
}

/**
 * 「这一页现在有没有值得保存的东西」—— 右下角那颗 FAB 出不出现看它，顶栏那颗「重置」也看它
 * （同一个判据，不会出现「FAB 在、重置不在」这种半套）。
 *
 *  排序态（`stepEditing`）下 FAB 仍在，只是换了文案（「完成」，见 `CommandFloatingButton`）：
 *  那时改的是步骤顺序、不是命令内容，保存没有意义，出路只有那一颗。顶栏那颗「重置」不画。
 */
internal fun SettingsPage.commandDraftHasContent(): Boolean =
    !commandDraft.stepEditing &&
        (commandDraft.draft.editingName != null || commandDraft.draft.name.isNotBlank() || payloadBlockedReason() == null)
