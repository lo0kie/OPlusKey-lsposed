package com.lookie.opluskey.ui.screen.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.update.ReleaseInfo
import com.lookie.opluskey.ui.DialogBody
import com.lookie.opluskey.ui.DialogButtonRow
import com.lookie.opluskey.ui.DialogPositiveStyle
import com.lookie.opluskey.ui.FrostedDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.moduleVersionName
import com.lookie.opluskey.ui.screen.home.openUrl
import com.lookie.opluskey.ui.state.UpdateState
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「发现新版本」那个窗 —— 启动自检查到有新版才弹（见 `ui/state/UpdateState.kt`）。
//
//  为什么不放进「运行状态」那张卡：那是**诊断**（现在这一版跑得对不对），这是**招呼一声**
//  （有新东西了）。诊断看与不看由用户决定，招呼只能主动说一次 —— 说完就不再纠缠。

/** 更新说明最多显示几行 —— 有的 release 正文是整篇 changelog，摊开会把按钮挤出窗。 */
private const val UPDATE_NOTES_MAX_LINES = 6

/**
 * 启动自检里「检查更新」那一趟 —— 一趟宿主进程只跑一次（判据在 [UpdateState.needsCheck]）。
 *
 *  与链路自检挂在同一个页面级副作用里（见 `PageContent`）：两者都是「这一页打开时问一句外面」，
 *  而且都只该问一次。跑在 `LaunchedEffect` 里是因为它要等回包（注入侧那条广播），
 *  最长 10 秒（见 `UpdateState`），这期间页面照常能用 —— 不挡任何操作。
 */
@Composable
internal fun SettingsPage.UpdateSelfCheck() {
    //  `LaunchedEffect` 的块是 `CoroutineScope.()`，里面那个 `this` 不是页面 —— 先把它抓出来
    val page = this
    LaunchedEffect(Unit) {
        if (UpdateState.needsCheck()) UpdateState.checkUpdateOnce(page)
    }
}

/**
 * 有新版时那个弹窗 —— 标题、当前 / 最新版本、更新说明、「不再提示」勾选框、两颗按钮。
 *
 *  **只在 [UpdateState.offer] 非空时出现**（查不到 / 已是最新 / 勾过「不再提示」都不弹）：
 *  打开设置页就弹一个「你已是最新版本」，比不查更烦人。
 */
@Composable
internal fun SettingsPage.UpdateDialog() {
    val info = UpdateState.offer
    FrostedDialog(
        show = info != null,
        title = I18n.get(Strings.UPDATE_TITLE, "发现新版本"),
        onDismiss = { UpdateState.dismiss() },
        footer = {
            DialogButtonRow(
                negative = I18n.get(Strings.UPDATE_LATER, "稍后"),
                positive = I18n.get(Strings.UPDATE_DOWNLOAD, "去下载"),
                onNegative = { UpdateState.dismiss() },
                onPositive = {
                    //  先去浏览器，再收窗：`takeDownload` 会把勾选框那一笔一起记下
                    val url = UpdateState.takeDownload()
                    if (url != null) openUrl(url)
                },
                //  更新不是破坏性动作，动作项用中性色
                positiveStyle = DialogPositiveStyle(danger = false, enabled = true),
            )
        },
    ) {
        if (info != null) UpdateBody(info, moduleVersionName().orEmpty())
    }
}

/** 窗里那块正文（拆出来只为让 [UpdateDialog] 那一支短一点）。 */
@Composable
private fun ColumnScope.UpdateBody(
    info: ReleaseInfo,
    current: String,
) {
    val summary = COUITheme.textStyles.body2
    val caption = COUITheme.textStyles.footnote2
    DialogBody(I18n.format(Strings.UPDATE_BODY, current, info.version))
    if (info.notes.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = I18n.get(Strings.UPDATE_NOTES, "更新说明"),
            style = caption,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = info.notes,
            style = summary,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            maxLines = UPDATE_NOTES_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(12.dp))
    //  「不再提示」整行可点：只勾那个小方块太难点中（与「显示系统应用」那些开关行同一套读法）
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { UpdateState.dontRemind = !UpdateState.dontRemind }
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            state = if (UpdateState.dontRemind) ToggleableState.On else ToggleableState.Off,
            onClick = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = I18n.get(Strings.UPDATE_DONT_REMIND, "不再提示这个版本"),
            style = summary,
            color = COUITheme.colorScheme.onSurfaceContainer,
        )
    }
}
