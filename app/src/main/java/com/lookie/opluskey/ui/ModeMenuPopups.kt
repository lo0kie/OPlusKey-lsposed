package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.specIcon
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 入口格那**两层**浮层 —— 「入口格的档位」与「某一档再往下那一层」，连守卫一起，全项目只此一份。
 *
 *  出现过的三种写法（网格的入口格 / 「添加命令」浮层 / 条件执行的动作菜单）本来就是同一件事：
 *  点一格 → 列出它那几档 → 其中某个开关还要再问一句「切换 / 打开 / 关闭」。
 *  以前每处各拼一遍 `MenuPopup` + 各写一遍「三级开着时别被二级的 dismiss 带走」的守卫，
 *  结果只有一处漏了守卫，真机表现就是「点一个开关，两层一起消失」。
 *
 * ## 调用方只表达两件事
 *
 *  · [tile] 是哪一格开着（`null` = 关）：行与小标题都由它算，调用方不用挑模式；
 *  · [onPick] 选中了哪一档（三级的「打开 / 关闭」也是同一个回调）。
 *
 *  哪一行点下去还有下一层由模型回答（[ActionCatalog.stateMenu]）—— 界面侧不判 token。
 *
 * @param preciseClick 弹层锚点：网格传那一格自己的，浮层里传浮层内部那个触点
 * @param current 当前绑的动作（`null` = 不知道，谁都不打勾）
 * @param onDismiss 这一格菜单该收起时调（含「点空白处」）。**只这一层**，不含上层浮层
 */
@Composable
internal fun SettingsPage.ModeMenuPopups(
    preciseClick: PreciseClickState,
    tile: ActionCatalog.Spec?,
    current: Action?,
    onPick: (ActionCatalog.Spec) -> Unit,
    onDismiss: () -> Unit,
) {
    // 第二层开着哪一档（`null` = 没开）。只在组件内部流转，调用方看不见也不用管。
    var stateEntry by remember { mutableStateOf<ActionCatalog.Spec?>(null) }
    // 整格菜单收起（含退场）后回到第一层：`remember` 不因 `tile = null` 清空，
    //  不重置会停在第二层、下次点开另一个开关时直接看见上一次那三行。
    LaunchedEffect(tile) {
        if (tile == null) stateEntry = null
    }
    val icons = tileIcons.value
    // 第一层里**手指按下的那一点** —— 第二层弹在它上面，而不是跟着上一层那一格的位置。
    //  `PreciseClickState` 把 DOWN 换算成窗口坐标，库以它为零尺寸锚点
    //  （横向居中、下面放不下就翻到上方），所以手指点到哪一块、下一层就贴在哪一块。
    val rowTouch = remember { PreciseClickState() }

    PageMenu(
        // 整列挂手指锚点：第一层只负责把「按了哪一点」记下来，给第二层当锚点。
        columnAnchor = rowTouch,
        preciseClick = preciseClick,
        //  第二层开着时这一层**不接**关闭请求 —— 否则第二层窗口一抢焦点，这一层就收到
        //  dismiss、`tile` 被置空，第二层跟着被上面那条 effect 一起清掉（两层一起消失）。
        onDismiss = { if (stateEntry == null) onDismiss() },
        entries =
            tile
                ?.let { t ->
                    ActionCatalog.modeMenu(t.token)?.let { modes ->
                        modeEntries(t.label, modes, icons, current) { picked ->
                            // 这一档还有下一层就叠上去（这一下**不关**本层）；否则它就是最终取值
                            if (ActionCatalog.stateMenu(picked.token) != null) {
                                stateEntry = picked
                            } else {
                                onDismiss()
                                onPick(picked)
                            }
                        }
                    }
                }.orEmpty(),
    )

    // 第二层：某一档的后续写法（切换 / 打开 / 关闭），各带自己的字形
    //  （`切换` 是开关本身、`打开` toggle-right、`关闭` toggle-left，见 `ActionIcons.systemSettingIcon`）。
    //  锚点是**第一层里手指按下的那一点** —— 与「添加命令」浮层那条路同一个手感。
    PageMenu(
        preciseClick = rowTouch,
        dim = true,
        onDismiss = { stateEntry = null },
        entries =
            stateEntry
                ?.let { entry ->
                    listOf(
                        MenuEntry.Section(
                            title = entry.label,
                            rows =
                                ActionCatalog.switchStates(entry.token).map { state ->
                                    MenuEntry.Row(
                                        title = state.label,
                                        icon = icons[state] ?: specIcon(state),
                                        //  必须精确到状态（`isBoundExactly`）：这几行之间只差一个后缀，
                                        //  用 `isBound`（把同一个开关的几档当一回事）会全部打勾。
                                        selected = current?.let { ActionCatalog.isBoundExactly(state, it) } ?: false,
                                    ) {
                                        stateEntry = null
                                        onDismiss()
                                        onPick(state)
                                    }
                                },
                        ),
                    )
                }.orEmpty(),
    )
}

/**
 * 入口格那一层菜单的行（响铃四档 / 媒体五档 / 系统开关那串）。
 *
 *  住在**组件层**而不是某个屏里：网格的入口格、「添加命令」的二级、条件执行的动作菜单都用它，
 *  三处各写一份的话「这一行点了还有没有下一层」的判据就会走形（`ui` 也不许反过来认识具体屏）。
 *
 * 第一行是**小标题**（库的 `DropdownHeaderItem`），写的是**父标题** —— 也就是一级菜单里
 * 那一行的标题（响铃状态 / 媒体控制 / 系统导航 / 系统按键）。二级是另一张浮层、会把一级压暗，
 * 没有这行小标题时只看得到「静音 / 振动」这几项，认不出自己是点哪个入口进来的。
 *
 * 行之间的分隔线按行自己带的那一档给（[ActionCatalog.Spec.dividerBefore]）：标了的用**加深带**
 * 把菜单切成两段（媒体控制的「播放 / 暂停 / 播放 / 暂停」与「上一首 / 下一首」、响铃状态的
 * 「循环」与那三档），其余仍是相邻两行那条细线。
 *
 * @param parentTitle 父标题（一级菜单里那一行的标题）
 * @param icons 页面级那一份图标表（`SettingsPage.tileIcons`）—— 各层的行都从它取字形。
 * @param current 当前绑的动作（`null` = 不知道），用来把那一档标出来。
 */
private fun modeEntries(
    parentTitle: String,
    modes: List<ActionCatalog.Spec>,
    icons: Map<ActionCatalog.Spec, ImageVector>,
    current: Action?,
    onPick: (ActionCatalog.Spec) -> Unit,
): List<MenuEntry> =
    // 这一层只有「标题 + 一组选项」：小标题下面那条细线与行间细线都由菜单自己加。
    //  组内再要分组靠 `spec.dividerBefore`（那一行前面换成加深带）。
    listOf(
        MenuEntry.Section(
            title = parentTitle,
            rows =
                modes.map { spec ->
                    // 这一档点下去**还有一层**（系统开关的切换 / 打开 / 关闭）⇒ 行尾给选择器字形，
                    //  与「添加命令」一级菜单里入口格那一行同一个记号；最终取值的档位（播放 / 静音…）
                    //  点了就结账，不给行尾那一格。判据仍由模型回答，界面不认 token。
                    val deeper = ActionCatalog.stateMenu(spec.token) != null
                    MenuEntry.Row(
                        title = spec.label,
                        icon =
                            icons.getOrElse(spec) {
                                XLog.w("图标表缺项：${spec.token} 用兜底字形")
                                specIcon(spec)
                            },
                        //  必须用 [ActionCatalog.isBound]（一行一行比），不能用 `isSelected` ——
                        // 后者对整组都算选中，会让二级菜单同时勾上多项。
                        selected = current?.let { ActionCatalog.isBound(spec, it) } ?: false,
                        submenu = deeper,
                        trailingIcon = if (deeper) PageIcons.Selector else null,
                        dividerBefore = spec.dividerBefore,
                        onPick = { onPick(spec) },
                    )
                },
        ),
    )
