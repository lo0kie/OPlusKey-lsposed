package com.lookie.opluskey.ui.screen.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.update.ReleaseInfo
import com.lookie.opluskey.ui.DialogBody
import com.lookie.opluskey.ui.DialogButtonRow
import com.lookie.opluskey.ui.DialogPositiveStyle
import com.lookie.opluskey.ui.FrostedDialog
import com.lookie.opluskey.ui.LocalTick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialogSideMargin
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.markdownToAnnotated
import com.lookie.opluskey.ui.screen.home.openUrl
import com.lookie.opluskey.ui.scrollBarTick
import com.lookie.opluskey.ui.state.UpdateState
import com.lookie.opluskey.ui.updateHost
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.VerticalScrollBar
import io.github.suqi8.coui.kmp.basic.rememberScrollBarAdapter
import io.github.suqi8.coui.kmp.interfaces.ExperimentalScrollBarApi
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「发现新版本」那个窗 —— 启动自检查到有新版才弹（见 `ui/state/UpdateState.kt`）。
//
//  为什么不放进「运行状态」那张卡：那是**诊断**（现在这一版跑得对不对），这是**招呼一声**
//  （有新东西了）。诊断看与不看由用户决定，招呼只能主动说一次 —— 说完就不再纠缠。

/**
 * 更新说明区的高度上限 —— 超出就滚动（约十行）。
 *
 *  按**高度**而不是行数限：Markdown 渲染之后「一行」不再等于「一条」，长条目一折行就把
 *  行数额度吃掉了。
 */
private val UPDATE_NOTES_MAX_HEIGHT = 240.dp

/**
 * 滚动条那条窄带的宽度 —— 与浮层菜单那条同档（`MENU_SCROLLBAR_WIDTH`）。
 *
 *  12dp 只占弹窗右侧内缩带（库的 `DialogDefaults.insideMargin` = 24dp）里的一小条，
 *  正文那边不用再额外让位：文字仍在 24dp 处收尾，滚动条收在 16dp 处，中间空 8dp。
 */
private val NOTES_SCROLLBAR_WIDTH = 12.dp

/** 滚动条离弹窗右边那一档 —— 贴着内边而不是被正文内缩推着走。 */
private val NOTES_SCROLLBAR_INSET = 4.dp

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
        if (UpdateState.needsCheck()) UpdateState.checkUpdateOnce(page.updateHost())
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
                onNegative = { UpdateState.dismiss() },
                onPositive = {
                    //  `takeDownload` 无论有没有地址都会把 offer / pending 清掉 —— 地址为空时先拦住：
                    //  否则窗口关了、浏览器没开、首页红点也一起灭掉，用户看不到任何解释。
                    val url = UpdateState.offer?.pageUrl
                    if (url.isNullOrBlank()) {
                        XLog.w("更新：这条 release 没有可打开的地址（version=${UpdateState.offer?.version}）")
                        UpdateState.dismiss()
                        hint(I18n.get(Strings.UPDATE_NO_URL, "这条更新没有下载地址，请到发布页查看"))
                    } else {
                        //  先去浏览器，再收窗：`takeDownload` 会把勾选框那一笔一起记下
                        UpdateState.takeDownload()
                        openUrl(url)
                    }
                },
                //  更新不是破坏性动作，动作项用中性色；字换成「去下载」
                positiveStyle =
                    DialogPositiveStyle(
                        danger = false,
                        enabled = true,
                        label = I18n.get(Strings.UPDATE_DOWNLOAD, "去下载"),
                    ),
            )
        },
    ) {
        if (info != null) UpdateBody(info)
    }
}

/**
 * 更新说明那一块 —— **限高 + 超出滚动**，右侧一条 COUI 滚动条。
 *
 *  为什么不按行数截断（原来的 `maxLines = 10`）：Markdown 渲染之后「一行」不再等于「一条」，
 *  长条目折行会把额度吃掉，用户看到的是半截加省略号 —— 而这段正文恰恰是他决定要不要升级的
 *  依据，截掉比多占几十 dp 更糟。
 *
 *  高度按「约十行」给；滚动条只在真的能滚时出现（`maxValue > 0`），且**常显**：它在这里是
 *  「下面还有」的提示，不是滑动反馈（与浮层菜单那条同一个理由，见 `PageMenuPopup`）。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun NotesBlock(
    notes: AnnotatedString,
    style: TextStyle,
) {
    val scroll = rememberScrollState()
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = UPDATE_NOTES_MAX_HEIGHT),
    ) {
        Text(
            text = notes,
            style = style,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            //  正文的左右内缩留在 `Text` 上、**不套给外层** —— 套给 Box 的话滚动条会被那
            //  24dp 一起推进去，右边凭空多出一大片（真机反馈「滚动条右缩进太多」）。
            //  滚动条自己只让 [NOTES_SCROLLBAR_INSET]，落在弹窗内缩带里。
            modifier =
                Modifier
                    .fillMaxWidth()
                    .dialogSideMargin()
                    .verticalScroll(scroll),
        )
        if (scroll.maxValue > 0) {
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(scroll),
                modifier =
                    Modifier
                        //  按住滑块 / 点轨道那一下的振动 —— 与浮层菜单那条同一个修饰符
                        //  （判据见 `Modifier.scrollBarTick`）
                        .scrollBarTick()
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        //  先让出贴边那一档、再定宽：顺序反了会先按 12dp 定死、再往里挤，
                        //  拇指被压细一半
                        .padding(end = NOTES_SCROLLBAR_INSET)
                        .width(NOTES_SCROLLBAR_WIDTH),
                alwaysVisible = true,
            )
        }
    }
}

/** 窗里那块正文（拆出来只为让 [UpdateDialog] 那一支短一点）。 */
@Composable
private fun ColumnScope.UpdateBody(info: ReleaseInfo) {
    val summary = COUITheme.textStyles.body2
    val caption = COUITheme.textStyles.footnote2
    DialogBody(I18n.format(Strings.UPDATE_BODY, info.displayVersion))
    if (info.notes.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = I18n.get(Strings.UPDATE_NOTES, "更新说明"),
            style = caption,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            //  库只给标题与摘要垫 `insideMargin`，`content` 槽不垫 —— 这几块要自己补，
            //  不然只有第一段（`DialogBody` 里补过）与标题对齐，下面全贴着弹窗边
            modifier = Modifier.dialogSideMargin(),
        )
        Spacer(Modifier.height(4.dp))
        //  更新说明按 Markdown 渲染：release 正文里 `##` / `- ` / `**粗体**` / 行内代码都会出现。
        //  解析只在正文变化时做一次（`remember` 挂 `info.notes`）。
        val accent = COUITheme.colorScheme.primary
        //  行内代码的底色必须是**比弹窗底深一档**的那一级：浅色主题里 `surfaceContainer`
        //  就是纯白，与弹窗底同色 —— 行内代码只剩一副等宽字形，看着像「字体坏了」。
        val codeBackground = COUITheme.colorScheme.surfaceContainerHigh
        val notes =
            remember(info.notes, summary, accent, codeBackground) {
                markdownToAnnotated(info.notes, summary, accent, codeBackground)
            }
        NotesBlock(notes, summary)
    }
    //  已经勾过「不再提示」的这一版不再问第二次（见 `UpdateState.showDontRemind`）：
    //  那一行整块不出现，连它上面那一档留白一起收掉
    if (!UpdateState.showDontRemind) return
    Spacer(Modifier.height(12.dp))
    //  「不再提示」整行可点：只勾那个小方块太难点中（与「显示系统应用」那些开关行同一套读法）
    val tick = LocalTick.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .dialogSideMargin()
                //  波纹跟着这一行的圆角走：不裁的话按下时是一片**直角**矩形铺到弹窗边上，
                //  与弹窗自己的圆角、与这行内容的圆角都对不上。8dp 只收掉四个角，
                //  不把这一行读成胶囊。
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    //  弹窗里的可点控件同样走页面那条通用振动通道（见 `LocalTick`）
                    tick()
                    UpdateState.dontRemind = !UpdateState.dontRemind
                }.padding(vertical = 4.dp),
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
