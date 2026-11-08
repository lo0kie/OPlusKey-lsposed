package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.model.SUMMARY_REF_GAP
import com.lookie.opluskey.ui.model.SUMMARY_REF_ICON
import com.lookie.opluskey.ui.model.StepLine
import com.lookie.opluskey.ui.model.opticalCenter
import com.lookie.opluskey.ui.model.serialSummarySteps
import com.lookie.opluskey.ui.model.stepIcon
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 命令卡上那段**摘要** —— 单条一行字、多条一行一步，装不下时自己收尾成「…」。
//  从 `SavedCommands.kt` 分出来一支：那边已经贴着 detekt 的 `TooManyFunctions` 上限，
//  而这里四支确实是一件事（摘要怎么排、放不下怎么办），不是按字母切。

/** 摘要：一行一条全部列出（装不下时由 [summaryLineRows] 收尾）；每行截断交给 `Text`，这层不估宽度。 */
@Composable
internal fun SettingsPage.CommandTileSummary(cmd: Command) {
    when (cmd.kind) {
        CommandKind.SINGLE -> {
            Text(
                text = cmd.single,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                // 与行首图标对齐（行盒居中不等于墨迹居中，见 `opticalCenter`）
                modifier = Modifier.opticalCenter(COUITheme.textStyles.footnote1),
                // 卡片高度封顶在正方形（见 `CommandTileRow` 的 `maxHeightRatio`），摘要再长也画不下：
                //  三行封顶 + 省略号，好过被父级的高度约束**从中间裁断**（那连省略号都没有）。
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        CommandKind.SERIAL -> {
            summaryLineRows(serialSummarySteps(cmd.steps, ::appNameOf))
        }
    }
}

/**
 * 摘要里的那几行 —— 「多条命令」与「条件执行」同一套画法：一行一条，行首一枚小图标。
 *
 *  行头那枚图标走 [stepIcon]：引用另一条命令（`cmd:<名字>`）的行用**被引用那条的真实类型图标**
 *  （单条 / 按步数的多条 / 分叉），其余用动作图标 —— 光看文字分不出几类内容。
 *
 * ## 装不下就截，末尾补一行「…」
 *
 *  卡片高度封在正方形（见 `CommandTileRow` 的 `maxHeightRatio`），步骤条数多于格子装得下的行数时，
 *  直接让 `Column` 溢出的话父级会**从某一行中间**裁断 —— 那一行只剩上半截、连省略号都没有，
 *  看着像卡片坏了。所以这里自己量一遍：逐行放，放不下的不画，真截断了才把最后一行让给「…」。
 *
 *  逐行量而不是「可用高度 ÷ 行高」：步骤名会折行，行高不是常数；这里只在 `CommandTileBody` 那个
 *  `weight(1f)` 的定高槽里用，所以量到的 [Constraints.maxHeight] 就是「还剩多少」。
 */
@Composable
private fun SettingsPage.summaryLineRows(lines: List<StepLine>) {
    Layout(
        content = {
            lines.forEach { line -> SummaryStepRow(line) }
            //  省略号那一行**恒参与组合**：截不截断要量完才知道，而一个 measurable 只能 measure
            //  一次 —— 量完发现放不下、再补一个子项是补不进来的。
            SummaryEllipsisRow()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val rows = measurables.dropLast(1)
        val ellipsis = measurables.last()
        //  宽度是定死的（卡宽 − 内边距），高度一律不封：先按自然高度量出来，再决定放几行。
        val rowConstraints = Constraints(maxWidth = constraints.maxWidth)
        val measured = rows.map { it.measure(rowConstraints) }
        val ellipsisPlaceable = ellipsis.measure(rowConstraints)
        val ellipsisHeight = ellipsisPlaceable.height
        //  高度不封顶的场合（滚动容器里）就当「全装得下」：这一层只服务卡片那个定高的格子。
        val available = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        var used = 0
        var shown = 0
        while (shown < measured.size && used + measured[shown].height <= available) {
            used += measured[shown].height
            shown++
        }
        val truncated = shown < measured.size
        //  真截断了就得给末尾那行「…」腾地方 —— 可能因此再退掉一行
        if (truncated) {
            while (shown > 0 && used + ellipsisHeight > available) {
                shown--
                used -= measured[shown].height
            }
        }
        val height =
            if (constraints.hasBoundedHeight) {
                //  格子是定高的（`weight(1f)` 那档 min = max），照给的高度报；放不满的白留在底下
                constraints.maxHeight
            } else {
                used + if (truncated) ellipsisHeight else 0
            }
        layout(constraints.maxWidth, height) {
            var y = 0
            for (index in 0 until shown) {
                measured[index].place(0, y)
                y += measured[index].height
            }
            if (truncated) ellipsisPlaceable.place(0, y)
        }
    }
}

/** 摘要里的一行：行首步骤图标 + 一行字（太长时由 `Text` 自己截）。 */
@Composable
private fun SettingsPage.SummaryStepRow(line: StepLine) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            stepIcon(line.token, configState.commands.value),
            contentDescription = null,
            modifier = Modifier.size(SUMMARY_REF_ICON),
            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(SUMMARY_REF_GAP))
        Text(
            text = line.title,
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            //  同上：与行首那枚步骤图标竖着对中（行盒 ≠ 墨迹，见 `opticalCenter`）
            modifier = Modifier.opticalCenter(COUITheme.textStyles.footnote1),
        )
    }
}

/**
 * 截断记号那一行 —— 只有一枚「…」，与步骤行同一档字号与颜色，所以它占的高度也正好是一行。
 *
 *  不缩进到文字那一列：缩进之后它看着像「又一步」，而它说的是「后面还有，没画出来」。
 */
@Composable
private fun SummaryEllipsisRow() {
    Text(
        text = "…",
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        modifier = Modifier.opticalCenter(COUITheme.textStyles.footnote1),
    )
}
