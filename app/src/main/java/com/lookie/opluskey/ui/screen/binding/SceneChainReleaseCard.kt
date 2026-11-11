package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.ui.BindingTarget
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.actionIcon
import com.lookie.opluskey.ui.model.releaseActionTitle
import com.lookie.opluskey.ui.openAppPickerForRelease
import com.lookie.opluskey.ui.screen.command.StepPickerPage
import com.lookie.opluskey.ui.screen.command.StepPickerSheet
import com.lookie.opluskey.ui.screen.command.StepSheetFilter
import com.lookie.opluskey.ui.screen.command.StepSheetSpec
import com.lookie.opluskey.ui.screen.command.branchDraftNow

// 「场景动作」页里**「单击后长按」的松手**那一族 —— 与长按那张卡的松手族同形，行也逐字相同。
//
//  为什么单独一卡而不是塞进 `LongPressCard`：那一张卡的行、判据、写回都认「长按」这一个槽位
//  （`LongPressParams` / `LongPressEdit`），再塞一档就得让那个契约多一个维度。
//  这一页本来就只有「这一块做什么」，多摆一族不影响阅读 —— 用小标题分清是哪一档即可。

/**
 * 场景块里「单击后长按」的松手 —— 开关 + 动作两行，与长按那一族**同一个形状、同一套文案**。
 *
 *  写回走 `bindChainRelease`（只改草稿）：与这一页其它东西同一条纪律 —— 这一页编的是草稿，
 *  提前落盘等于替用户按了那颗「确认」。
 *
 * @param enabled 这一页能不能改（整页闸门 × 这一块的「跟随默认」）
 * @param disabledReason 灰着被点一下时说的那句（`null` = 用整页那句）
 */
@Composable
internal fun SettingsPage.SceneChainReleaseCard(
    target: BindingTarget.Scene,
    enabled: Boolean,
    disabledReason: String? = null,
) {
    val block = branchDraftNow().scenes.getOrNull(target.sceneAt) ?: return
    val action = block.chainRelease.takeUnless { it is Action.Follow } ?: Action.None
    var picking by remember { mutableStateOf(false) }
    CardList(
        rows =
            listOf(
                CardRow.Switch(
                    title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE, "松手执行额外动作"),
                    summaryOn = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_DESC_ON, "松手时再执行一个动作"),
                    summaryOff = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_DESC_OFF, "松手不再执行别的"),
                    icon = PageIcons.LongRelease,
                    checked = block.chainReleaseOn,
                    enabled = enabled,
                    disabledHint = disabledReason,
                ) { on ->
                    target.bindChainRelease(on = on)
                },
                CardRow.Item(
                    title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_ACTION, "额外动作"),
                    summary = releaseActionTitle(action.token) { appNameOf(it) },
                    icon = actionIcon(action.token),
                    arrow = true,
                    enabled = enabled,
                    disabledHint = disabledReason,
                    onClick = { picking = true },
                    visible = block.chainReleaseOn,
                ),
            ),
    )
    //  挑动作：与长按那一族**同一个抽屉**，`chain = true` + `sceneAt` 决定落到草稿的哪一格
    StepPickerSheet(
        show = picking,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE, "松手执行额外动作"),
                current = action,
                onOpenPicker = { page ->
                    picking = false
                    when (page) {
                        StepPickerPage.App -> {
                            openAppPickerForRelease(chain = true, combo = target.comboId, sceneAt = target.sceneAt)
                        }

                        StepPickerPage.AppStop -> {
                            openAppPickerForRelease(stop = true, chain = true, combo = target.comboId, sceneAt = target.sceneAt)
                        }

                        //  变量那一格这一趟不列（`StepSheetFilter.excludeTiles`），走不到这里
                        StepPickerPage.Vars -> {}
                    }
                },
                onPick = { token -> target.bindChainRelease(action = Action.parse(token)) },
                onDismiss = { picking = false },
                filter = StepSheetFilter(excludeTiles = true, includeNone = true),
            ),
    )
}
