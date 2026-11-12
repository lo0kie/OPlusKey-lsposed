package com.lookie.opluskey.ui.screen.combo

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.HEADER_ACTION_GAP
import com.lookie.opluskey.ui.chrome.HeaderIconButton
import com.lookie.opluskey.ui.feedback.DisabledHint
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.theme.COLOR_FADE_SPEC
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.binding.KeysEditState
import com.lookie.opluskey.ui.wire.disabledClickReason
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「按键功能」页顶栏右上角那两颗 —— 编辑态多一颗「全选」，与 `PageTopBar` 的 `PageActions`
// 同一位置、同一尺寸（那一格是 `RowScope`，两颗按顺序排、中间隔 [HEADER_ACTION_GAP]）。

/**
 * 顶栏右上角那颗「编辑」—— 按一下进这一页的**编辑态**（列表里每一颗键的行换成多选框，
 * 选中任意一颗就从右下角滑进「删除」；见 [KeysListCard] 与 `KeysScreen`）。
 *
 *  编辑态里它自己换成「完成」（用户口径）：**一颗按钮两档**，退出编辑不用去别处找。
 *  退出时选中一起清掉 —— 「完成」的意思是这一趟改完了，留着选中只会让下次进来先看到一排勾。
 *
 *  编辑态里它**左边**还多一颗「全选」（见 [KeysHeaderSelectAll]）：一列键 + 一列组合都勾上
 *  要点十几下，而「删掉全部自定义键」是这一页最常见的整批操作。
 *
 *  整页闸门关着（总闸关 / 链路没通 / 配置只读）时置灰并说清原因：编辑态的终点是删配置，
 *  写不进去的时候让人先勾一堆再发现删不掉，比一开始就按不动更费事。
 */
@Composable
internal fun SettingsPage.KeysHeaderEdit() {
    val editing = KeysEditState.editing
    val canEdit = pageWritable()
    val iconTint = keysHeaderTint(canEdit)

    //  **只在编辑态出现**：平时这一格没有可勾的东西，摆一颗永远灰着的按钮只是噪音。
    if (editing) KeysHeaderSelectAll(canEdit)

    //  「按一下振一下」由顶栏那一层统一挂（见 `PageHeader` 的 `headerPressTick`），这里不再自己振
    //  字形随编辑态换（编辑 与 完成）：形状没法插值 ⇒ 交叉淡过去；图标色与它同一条曲线，
    //  免得「色在渐变、字形啪地换掉」。
    DisabledHint(if (canEdit) null else disabledClickReason()) {
        HeaderIconButton(
            onClick = { KeysEditState.toggleEditing() },
            enabled = canEdit,
        ) {
            Crossfade(
                targetState = editing,
                animationSpec = COLOR_FADE_SPEC,
                label = "KeysEditGlyph",
            ) { on ->
                Icon(
                    if (on) PageIcons.Confirm else PageIcons.CommandEdit,
                    contentDescription =
                        if (on) {
                            I18n.get(Strings.COMMON_DONE, "完成")
                        } else {
                            I18n.get(Strings.COMMON_EDIT, "编辑")
                        },
                    // 顶栏按钮不给图标上色 ⇒ 禁用态得自己给（与行首图标同一个禁用色）
                    tint = iconTint,
                )
            }
        }
    }
}

/**
 * 编辑态里那颗「全选」—— 按一下把两张清单里能勾的**一次勾满**，已经勾满时再按一下**收回**。
 *
 *  「全勾上了没有」由 [KeysEditState.toggleAll] 现算（两份清单 + 当前选中），这一层只管
 *  画哪一档文案：收回那一档如果还写「全选」，用户会以为按了没反应。
 */
@Composable
private fun SettingsPage.KeysHeaderSelectAll(canEdit: Boolean) {
    val cfg = configState.config.value
    val allKeys = cfg.allKeys.map { it.id }.toSet()
    val allCombos = cfg.combos.map { it.id }.toSet()
    val all = KeysEditState.pickedKeys == allKeys && KeysEditState.pickedCombos == allCombos
    //  勾满那一档用**强调色**（与「选择应用」页那颗小窗开关选中时同一套）：这颗按钮说的是
    //  「现在是不是全勾上了」，只看图标本身分不出两档 —— 文案在无障碍里，眼睛看不到。
    val iconTint = keysHeaderTint(canEdit, active = all)
    DisabledHint(if (canEdit) null else disabledClickReason()) {
        HeaderIconButton(
            onClick = { KeysEditState.toggleAll(allKeys, allCombos) },
            enabled = canEdit,
        ) {
            Icon(
                PageIcons.SelectAll,
                contentDescription =
                    if (all) {
                        I18n.get(Strings.KEYS_DESELECT_ALL, "取消全选")
                    } else {
                        I18n.get(Strings.KEYS_SELECT_ALL, "全选")
                    },
                tint = iconTint,
            )
        }
    }
    //  间距挂在**左边那颗**身上（与选择应用页那两颗同一套写法）：两颗都带就会叠成两档
    Spacer(Modifier.width(HEADER_ACTION_GAP))
}

/** 顶栏图标在这一页的着色（可点 / 置灰两档，带过渡）—— 两颗按钮共用一份判据。
 *
 *  @param active 这一颗「正生效」（全选已经勾满）—— 走强调色；默认 `false` = 普通那档。
 */
@Composable
private fun keysHeaderTint(
    canEdit: Boolean,
    active: Boolean = false,
): Color =
    animateColorAsState(
        targetValue =
            when {
                !canEdit -> COUITheme.colorScheme.disabledOnSecondaryVariant
                active -> COUITheme.colorScheme.primary
                else -> COUITheme.colorScheme.onSurfaceContainer
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "KeysEditIcon",
    ).value
