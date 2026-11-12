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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.FrostedDialog
import com.lookie.opluskey.ui.dialog.dialogSideMargin
import com.lookie.opluskey.ui.model.page.SUMMARY_REF_GAP
import com.lookie.opluskey.ui.model.page.opticalCenter
import com.lookie.opluskey.ui.model.text.durationText
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
        //  「以 root 执行」排在**正文上面**：它说的是「这一整段怎么跑」（前提），
        //  压在正文底下时读起来像一句附注，而它其实是执行条件。
        //  它只属于**单条**命令：多条那档没有这一个开关，每一步的 `shell:` 都按自己那一档走
        //  （执行侧一律提权，见 `CustomCommandRunner` 的 `execShell` 第二参）。
        if (cmd.kind == CommandKind.SINGLE && cmd.root) {
            Text(
                text = I18n.get(Strings.COMMAND_ROOT_TITLE, "以 root 执行"),
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
                                modifier = Modifier.opticalCenter(COUITheme.textStyles.body2),
                            )
                        }
                    }
                }
            }
        }
    }
}
