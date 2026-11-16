package com.lookie.opluskey.ui.screen.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.mergeCommandSeeds
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.DialogButtonRow
import com.lookie.opluskey.ui.dialog.DialogPositiveStyle
import com.lookie.opluskey.ui.dialog.FrostedDialog
import com.lookie.opluskey.ui.dialog.PickerOption
import com.lookie.opluskey.ui.dialog.PickerRows
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.row.AddedTag
import com.lookie.opluskey.ui.screen.addBuiltInCommands
import com.lookie.opluskey.ui.state.page.builtInCommandSeeds

// 「内置常用命令」那一窗 —— 加号先问来源（内置 / 手动），这一支是「内置」那一半。
//
// 与抽屉那一条的分工：抽屉从**所有动作**里挑一条（命令库、应用、系统开关……），这一窗只列模块
// 自带的那几条（[builtInCommandSeeds]），勾选后**一次落两张表**（自定义命令 + 常用命令）。
// 所以它是一条「批量收录」的路，不是「挑一条动作」的路 —— 落点与去重都在 `addBuiltInCommands`。

/**
 * 内置常用命令的勾选窗（居中）。
 *
 * ## 为什么默认全勾
 *
 *  用户点进这一窗就是来收录内置那几条的：一条条点一遍没有信息量 —— 要减哪一条，点它一下取消即可。
 *  **已经收过的那几条不勾**（并且灰着、行尾标「已添加」）：它进了这一趟也只会被去重跳过。
 *
 * ## 已添加怎么认
 *
 *  认的是 token（`cmd:<名字>`），而名字要取**合并之后的命令库**里那一条：库里若已有一条同正文、
 *  改了名的命令，收录时会复用它（见 [mergeCommandSeeds]），清单里钉的也是那个名字 ——
 *  拿种子自己的名字去比会漏判，于是这一行明明收不了却还能勾。
 *
 * ## 确认之后
 *
 *  走 [addBuiltInCommands]：先按 [mergeCommandSeeds] 补命令库（正文相同 / 同名都跳过），再按
 *  `cmd:<名字>` 钉进常用命令清单（已有则不重复钉）。两处各自去重，回执报「加了几条 / 跳过几条」。
 *  写失败时**不关窗**（可以直接再点一次）。
 *
 * @param show 开着没有 —— 原样递给 [FrostedDialog]（退场动画靠它，别在外面判）
 * @param onDismiss 收起
 */
@Composable
internal fun SettingsPage.BuiltInCommandsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    //  **不进 `remember`**：名字按当前语言取（[builtInCommandSeeds] 里那几条是界面语言的字面量），
    //  换语言之后这一窗要跟着变。四条而已，每帧重算一遍不值得为它加一层缓存与失效判据。
    val seeds = builtInCommandSeeds()
    //  这四条**落成命令库之后**的 token（见上面「已添加怎么认」）
    val tokens =
        mergeCommandSeeds(configState.commands.value, seeds.map { it.command })
            .associateBy { it.single.trim() }
            .let { byBody -> seeds.map { seed -> byBody[seed.command.single.trim()]?.let { Command.PREFIX + it.name } } }
    val existing =
        configState.config.value.global.commonCommands
            .mapTo(HashSet()) { it.token }
    val added = tokens.map { it != null && it in existing }
    //  勾选的是**下标**：名字会随语言变，拿名字当键会在换语言那一刻全部失配。
    //  每次打开都回到「全勾」（已添加的除外）—— 上一趟留下的选择不该带进下一趟。
    var picked by remember(show) { mutableStateOf(seeds.indices.filterNot { added[it] }.toSet()) }
    FrostedDialog(
        show = show,
        title = I18n.get(Strings.COMMON_COMMANDS_BUILTIN_TITLE),
        onDismiss = onDismiss,
        //  居中是**这一窗的要求**（用户口径）：它是一段短清单，不是要往下拖着看的抽屉。
        centered = true,
        footer = {
            DialogButtonRow(
                onNegative = onDismiss,
                onPositive = {
                    //  写失败时不关窗：用户可以直接再点一次（失败提示已由写入口报过）
                    if (addBuiltInCommands(seeds.filterIndexed { index, _ -> index in picked })) onDismiss()
                },
                positiveStyle =
                    DialogPositiveStyle(
                        //  一条都没勾时添加没有意义（勾一下就有；全收过时这一颗就是灰的）
                        enabled = picked.isNotEmpty(),
                        label = I18n.get(Strings.COMMON_COMMANDS_BUILTIN_ADD),
                    ),
            )
        },
    ) {
        PickerRows(
            options =
                seeds.mapIndexed { index, seed ->
                    val done = added[index]
                    PickerOption(
                        label = seed.command.name,
                        //  已经在清单里：这一条收不进去了（再收也只会被去重跳过），灰着
                        enabled = !done,
                        //  行尾写明「已添加」—— 只有灰字的话读起来像坏了
                        trailing = if (done) ({ AddedTag() }) else null,
                    )
                },
            isSelected = { it in picked },
            onClick = { index -> picked = if (index in picked) picked - index else picked + index },
            //  退场那一段不再派发（与选择器那一族同一条口径）
            interactive = show,
            //  点灰行说一句为什么（不然那一下毫无反应）
            onDisabledClick = { hint(I18n.get(Strings.COMMON_COMMANDS_ADDED_HINT)) },
        )
    }
}
