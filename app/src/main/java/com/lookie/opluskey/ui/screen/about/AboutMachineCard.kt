package com.lookie.opluskey.ui.screen.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.ExpandableRow
import com.lookie.opluskey.ui.ExpandableRowSpec
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.ABOUT_ROWS
import com.lookie.opluskey.ui.model.AUTHOR
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.REPO_URL
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.screen.home.openUrl
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「关于」卡 —— 版本 / 签名 / 作者 / 反馈问题，四行固定（见 [ABOUT_ROWS]）。
 *
 *  本机那一行已经挪到「运行状态」卡的最上面：那是「这台机器现在什么状态」，与链路那几段同属一类。
 *
 *  放在整页**最下面**：上面两张答的是「怎么用、现在什么状态」，这一张是「这台机器 + 这个包」的
 *  自述，看过一次就够。
 *
 *  @param version 版号 + 构建类型 + 构建时刻（含日期），见 `appVersionText`
 *  @param signature 签名那一行的四样（标题 / 结论 / 指纹 / 是否官方），见 [SignatureInfo]
 */
@Composable
internal fun SettingsPage.MachineCard(
    version: String,
    signature: SignatureInfo,
    ui: AboutUiState,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        VersionRow(version)
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        SignatureRow(signature, ui)
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        AuthorRow(ui)
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        FeedbackRow(ui)
    }
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
        cardListPosition = cardListPositionOf(0, ABOUT_ROWS),
    )
}

/**
 * 签名那一行 —— 结论在标题上（见 `signatureTitle`），指纹**收起来**、点一下才展开。
 *
 *  指纹是一长串十六进制，摊在摘要里会把这一行撑成两三行高，而它平时并不需要看；
 *  收起之后整张卡的行高齐了，要看时再展开（见 `ui/ExpandableRow`）。
 */
@Composable
private fun SettingsPage.SignatureRow(
    signature: SignatureInfo,
    ui: AboutUiState,
) {
    // 值就是原来那一行的摘要（一句结论 + 指纹，中间是硬换行）。展开后与不折叠时**一模一样**，
    //  收起时压成一行、末尾省略号 —— 差别只在 `maxLines`，见 `ui/ExpandableRow`。
    val text = listOfNotNull(signature.note.ifEmpty { null }, signature.fingerprint).joinToString("\n")
    ExpandableRow(
        spec =
            ExpandableRowSpec(
                title = signature.title,
                text = text,
                icon = PageIcons.Signature,
                // 与「关于本机」同一套色：官方用常规色、「不是这包」用 error
                iconTint =
                    if (signature.official) {
                        COUITheme.colorScheme.onSurfaceContainer
                    } else {
                        COUITheme.colorScheme.error
                    },
                position = cardListPositionOf(1, ABOUT_ROWS),
            ),
        expanded = ui.signatureExpanded,
        onToggle = { ui.signatureExpanded = !ui.signatureExpanded },
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
        cardListPosition = cardListPositionOf(2, ABOUT_ROWS),
        modifier = Modifier.preciseClickAnchor(ui.authorClick),
    )
}

/** 反馈与交流那一行 —— 同样是一层菜单；不给副标题（渠道名在菜单里逐条列着）。 */
@Composable
private fun SettingsPage.FeedbackRow(ui: AboutUiState) {
    val tick = rememberTick()
    BasicComponent(
        title = I18n.get(Strings.MORE_ABOUT_FEEDBACK, "反馈与交流"),
        onClick = {
            tick()
            ui.showFeedbackMenu = true
        },
        startAction = { Icon(PageIcons.Feedback, null) },
        // 行尾给选择器字形（不是链接符号）：这一行点开的是**一层菜单**，
        //  与「语言」那一行同一件（链接符号留给菜单里那三行）。
        endActions = { RowEndIcon(PageIcons.Selector) },
        cardListPosition = cardListPositionOf(3, ABOUT_ROWS),
        modifier = Modifier.preciseClickAnchor(ui.feedbackClick),
    )
}
