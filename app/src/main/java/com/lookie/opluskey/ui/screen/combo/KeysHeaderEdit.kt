package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.DisabledHint
import com.lookie.opluskey.ui.HeaderIconButton
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.disabledClickReason
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.KeysEditState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「按键功能」页顶栏右上角那颗「编辑 / 完成」—— 与 `PageTopBar` 的 `PageActions` 那几颗同一位置、同一尺寸。

/**
 * 顶栏右上角那颗「编辑」—— 按一下进这一页的**编辑态**（列表里每一颗键的行换成多选框，
 * 选中任意一颗就从右下角滑进「删除」；见 [KeysListCard] 与 `KeysScreen`）。
 *
 *  编辑态里它自己换成「完成」（用户口径）：**一颗按钮两档**，退出编辑不用去别处找。
 *  退出时选中一起清掉 —— 「完成」的意思是这一趟改完了，留着选中只会让下次进来先看到一排勾。
 *
 *  整页闸门关着（总闸关 / 链路没通 / 配置只读）时置灰并说清原因：编辑态的终点是删配置，
 *  写不进去的时候让人先勾一堆再发现删不掉，比一开始就按不动更费事。
 */
@Composable
internal fun SettingsPage.KeysHeaderEdit() {
    val editing = KeysEditState.editing
    val canEdit = pageWritable()
    //  「按一下振一下」由顶栏那一层统一挂（见 `PageHeader` 的 `headerPressTick`），这里不再自己振
    DisabledHint(if (canEdit) null else disabledClickReason()) {
        HeaderIconButton(
            onClick = { KeysEditState.toggleEditing() },
            enabled = canEdit,
        ) {
            Icon(
                if (editing) PageIcons.Confirm else PageIcons.CommandEdit,
                contentDescription =
                    if (editing) {
                        I18n.get(Strings.COMMON_DONE, "完成")
                    } else {
                        I18n.get(Strings.COMMON_EDIT, "编辑")
                    },
                // 顶栏按钮不给图标上色 ⇒ 禁用态得自己给（与行首图标同一个禁用色）
                tint =
                    if (canEdit) {
                        COUITheme.colorScheme.onSurfaceContainer
                    } else {
                        COUITheme.colorScheme.disabledOnSecondaryVariant
                    },
            )
        }
    }
}
