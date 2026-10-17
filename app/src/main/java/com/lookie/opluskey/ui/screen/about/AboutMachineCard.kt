package com.lookie.opluskey.ui.screen.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.DeviceSupport
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.ABOUT_ROWS
import com.lookie.opluskey.ui.model.AUTHOR
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.REPO_URL
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.screen.home.openUrl
import com.lookie.opluskey.ui.state.deviceModel
import com.lookie.opluskey.ui.state.deviceSupport
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「关于」卡 —— 本机 / 版本 / 签名 / 作者 / 反馈问题，四行固定 + 一行只读（见 [ABOUT_ROWS]）。
 *
 *  放在整页**最下面**：上面两张答的是「怎么用、现在什么状态」，这一张是「这台机器 + 这个包」的
 *  自述，看过一次就够。
 *
 *  @param version 版号 + 构建类型 + 构建时刻（含日期），见 `appVersionText`
 *  @param signature 指纹那一行（debug 包另有「不校验签名」的限定）
 *  @param signatureTitle 平时是「签名」，不符时改口成结论（见 `signatureTitle`）
 *  @param signatureOfficial 签名与本包是否相符 —— 只决定字形颜色
 */
@Composable
internal fun SettingsPage.MachineCard(
    version: String,
    signature: String,
    signatureTitle: String,
    signatureOfficial: Boolean,
    ui: AboutUiState,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        DeviceRow()
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        VersionRow(version)
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        SignatureRow(signatureTitle, signature, signatureOfficial)
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        AuthorRow(ui)
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        FeedbackRow(ui)
    }
}

/**
 * 本机那一行（机型 + 是否适配）—— 只读、**不给 `onClick`**（给了反而像能点）。
 *
 *  与「版本」同属「这台机器 / 这个包」的自述，所以并进这张卡、摆在版本上面。
 */
@Composable
private fun DeviceRow() {
    val adapted = deviceSupport == DeviceSupport.Support.ADAPTED
    BasicComponent(
        title = localDeviceTitle(),
        summary = adaptVerdict(deviceSupport),
        startAction = {
            Icon(
                // 适配命中 / 没命中 —— 用「这台机器认不认得」两个字形（见 [PageIcons.DeviceAdapted]）
                if (adapted) PageIcons.DeviceAdapted else PageIcons.DeviceUnsupported,
                contentDescription = null,
                // 与「运行状态」同一套色：命中用常规色、没命中用 error
                tint =
                    if (adapted) {
                        COUITheme.colorScheme.onSurfaceContainer
                    } else {
                        COUITheme.colorScheme.error
                    },
            )
        },
        cardListPosition = cardListPositionOf(0, ABOUT_ROWS),
    )
}

/** 版本那一行 —— 点一下开项目仓库（与「作者 / 反馈问题」一样是跳外部链接）。 */
@Composable
private fun SettingsPage.VersionRow(version: String) {
    val tick = rememberTick()
    BasicComponent(
        title = I18n.get(Strings.MORE_ABOUT_VERSION, "版本"),
        summary = version,
        onClick = {
            tick()
            openUrl(REPO_URL)
        },
        startAction = { Icon(PageIcons.Versions, null) },
        endActions = { RowEndIcon(PageIcons.Link) },
        cardListPosition = cardListPositionOf(1, ABOUT_ROWS),
    )
}

/** 签名那一行 —— 只读，结论在标题上（见 `signatureTitle`），摘要只有指纹。 */
@Composable
private fun SignatureRow(
    signatureTitle: String,
    signature: String,
    signatureOfficial: Boolean,
) {
    BasicComponent(
        title = signatureTitle,
        summary = signature,
        startAction = {
            Icon(
                PageIcons.Signature,
                contentDescription = null,
                // 与「关于本机」同一套色：官方用常规色、「不是这包」用 error
                tint =
                    if (signatureOfficial) {
                        COUITheme.colorScheme.onSurfaceContainer
                    } else {
                        COUITheme.colorScheme.error
                    },
            )
        },
        cardListPosition = cardListPositionOf(2, ABOUT_ROWS),
    )
}

/** 作者那一行 —— 点开的是一层菜单（GitHub / 酷安两个去处），不直接跳外部。 */
@Composable
private fun SettingsPage.AuthorRow(ui: AboutUiState) {
    val tick = rememberTick()
    BasicComponent(
        title = I18n.get(Strings.MORE_ABOUT_AUTHOR, "作者"),
        summary = AUTHOR,
        onClick = {
            tick()
            ui.showAuthorMenu = true
        },
        startAction = { Icon(PageIcons.UserScreen, null) },
        // 行尾给选择器字形（不是链接符号）：链接符号留给菜单里那两行。
        endActions = { RowEndIcon(PageIcons.Selector) },
        cardListPosition = cardListPositionOf(3, ABOUT_ROWS),
        modifier = Modifier.preciseClickAnchor(ui.authorClick),
    )
}

/** 反馈问题那一行 —— 同样是一层菜单；不给副标题（渠道名在菜单里逐条列着）。 */
@Composable
private fun SettingsPage.FeedbackRow(ui: AboutUiState) {
    val tick = rememberTick()
    BasicComponent(
        title = I18n.get(Strings.MORE_ABOUT_FEEDBACK, "反馈问题"),
        onClick = {
            tick()
            ui.showFeedbackMenu = true
        },
        startAction = { Icon(PageIcons.Bug, null) },
        // 行尾给选择器字形（不是链接符号）：这一行点开的是**一层菜单**，
        //  与「语言」那一行同一件（链接符号留给菜单里那三行）。
        endActions = { RowEndIcon(PageIcons.Selector) },
        cardListPosition = cardListPositionOf(4, ABOUT_ROWS),
        modifier = Modifier.preciseClickAnchor(ui.feedbackClick),
    )
}

/**
 * 「关于」卡里本机那一行的主标题 —— 机型号与代号都摆出来。
 *
 *  清单里那台机器用清单上那份名字（自带代号，「OnePlus 15（PLK110）」）；不在清单里就只有代号，
 *  毕竟它的名字我们也不知道。代号都读不出来时给空串（标题不画）—— 读不出来是「没读到」、
 *  不是结论，副标题那句结论照常给。
 */
private fun localDeviceTitle(): String = DeviceSupport.adaptedNameOf(deviceModel).ifEmpty { deviceModel }

/**
 * 「适配清单」里「本机」那一行的结论 —— 判定三档各一句（见 `core/model/DeviceSupport`）。
 *
 * 只有「等待适配」这一档是「等作者」而不是「本机不行」（首页那条提示分两句，见 `ui/model/PageConst`），
 * 所以三句话不合并成两档。
 */
private fun adaptVerdict(support: DeviceSupport.Support): String =
    when (support) {
        DeviceSupport.Support.ADAPTED -> I18n.get(Strings.ADAPTATION_ADAPTED, "已适配")
        DeviceSupport.Support.PENDING -> I18n.get(Strings.ADAPTATION_PENDING, "等待适配")
        DeviceSupport.Support.UNSUPPORTED -> I18n.get(Strings.ADAPTATION_UNSUPPORTED, "不在适配清单")
    }
