package com.lookie.opluskey.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.HintBadge
import com.lookie.opluskey.ui.LocalTick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.linkChecks
import com.lookie.opluskey.ui.linkFacts
import com.lookie.opluskey.ui.model.HOST_STALE_HINT
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_START_ICON_GAP
import com.lookie.opluskey.ui.model.ROW_TITLE_INSET
import com.lookie.opluskey.ui.model.SYSTEM_STALE_HINT
import com.lookie.opluskey.ui.state.UpdateState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopTipsDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

// 首页顶部那几条提示（同一个位置、同一个样式）：
//   · 有新版本 —— 让「查到新版却没人下」不至于白查（红点只说「这儿有新东西」，得点进去才看得到）；
//   · 系统侧跑着旧版 —— 界面已是新版、执行侧还是旧的，改什么都到不了那只手；
//   · 自检异常 —— 「运行状态」里有一条阻塞故障。
//
//  后两条原来各自只有一次性提示条（几秒自己走），而它们是**常驻状态**（不重启 / 不修就不消失）：
//  用户错过那几秒，就只剩下「按了没反应」「改什么都不生效」这类没有任何解释的现象。

/**
 * 文案（与尾部那个动作）**整体下移**多少 —— 14sp 汉字在行盒里的视觉重心比盒子的几何中心
 * 高一档，几何居中之后整行字看着「浮起来」。
 *
 *  这一档与 `SECTION_TITLE_TEXT_OPTICAL_Y` 是同一件事、同一个量：全工程补的是**文字**，
 *  不是与它并排的图标（补图标只会让图标和字错开）。
 */
private val TIP_TEXT_OPTICAL_Y = 1.dp

/**
 * 两条提示同时出现时它们之间的间距 —— 与「卡片之间」那一档对齐。
 *
 *  不加的话两条会贴成一条（各自的左右内缩仍在，但中间没有缝，读起来像一条被劈成两半）。
 */
private val TIP_STACK_GAP = 8.dp

/**
 * 条带内容距自己左缘多少 —— **由卡片行那一套倒推出来**，不是库给的那 12dp。
 *
 *  卡片行里：卡片左缘 → 图标格 16dp → 图标 24dp → 间距 16dp → 正文，正文距卡片左缘
 *  `ROW_TITLE_INSET`（56）。提示条的左边缘与卡片左边缘同在 `PAGE_MARGIN` 上，所以只要
 *  左边距、图标格、间距这三样与卡片行一致，图标格与正文就落进**同一列**。
 *
 *  原先沿用的是库的 `ContentPadding`（12dp）：比卡片行少 4dp —— 图标与正文整体左移 4dp，
 *  真机上就是「差一点点，怎么看怎么别扭」的那种不对齐。
 */
private val TIP_START_PADDING =
    ROW_TITLE_INSET - TopTipsDefaults.IconSize - (TopTipsDefaults.IconSpacing + ROW_START_ICON_GAP)

/** 尾部那个动作的点击区形状 —— 与卡片行、小标题同一档圆角（只用来收住涟漪）。 */
private val TIP_ACTION_SHAPE = RoundedCornerShape(8.dp)

/**
 * 动作点击区在字形左右各留多少 —— **两边必须一样**。
 *
 *  这一格是涟漪的边界：一侧留 12、另一侧留 0 时，涟漪中心会偏到字形左边 6dp，
 *  看着像「点歪了」（真机上就是这么看出来的）。纵向不靠 padding 撑：加了会把整行顶高，
 *  这一格的高度由 [TopTipsDefaults.IconSize] 给，与图标同高。
 */
private val TIP_ACTION_TAP_H = 8.dp

/**
 * 提示里那颗图标的大小 —— **比库给的 24dp 小一档**。
 *
 *  那一格右下要给红点让位：图标满格时红点只能压在字形上，看着像图标缺了一角；
 *  缩到 20dp 之后，红点落在它右上角外的空处，两样都认得出来。
 */
private val TIP_ICON_SIZE = 20.dp

/**
 * 红点往图标外挪多少 —— 2dp 就够它离开字形，又不至于被提示条的内缩裁掉。
 *
 *  放大到 4dp 以上时，红点与 20dp 的图标之间会撕出一道缝，读成「两个标记」而不是「这一颗在提示」。
 */
private val TIP_BADGE_OUTSET = 2.dp

/**
 * 首页顶部这几条提示的**宿主** —— 谁该出现谁出现，都出现时按同一档间距排开。
 *
 *  抽出来只为「位置、顺序与间距」有一处判据：各写一份 `Spacer` 的话，两条同时出现时它们会贴成
 *  一条，只出现一条时又平白多出一档空白。
 *
 *  顺序 = 「这条消息有多该被看见」：新版本（有东西可取）→ 系统侧旧版（要做一次重启）→
 *  自检异常（要照着那句话去修）。**后两条互斥**：系统侧旧版本身就是自检里那条「设置生效」的红，
 *  两条一起挂就是同一件事说两遍，而且上面那句更具体（说的是「去重启」而不是「异常了」）。
 */
@Composable
internal fun SettingsPage.HomeTips() {
    val hasUpdate = UpdateState.pending != null
    //  「有一件事需要你处理」那一条的文案；`null` = 这一条不出现
    val status = statusTipText()
    if (!hasUpdate && status == null) return
    Column {
        if (hasUpdate) HomeUpdateTip()
        if (status != null) {
            if (hasUpdate) Spacer(Modifier.height(TIP_STACK_GAP))
            HomeStatusTip(status)
        }
    }
}

/**
 * 「有一件事需要你处理」那一条该说什么 —— **系统侧旧版与自检异常共用这一格**。
 *
 *  两者本来就是同一件事的两种说法（跑着旧版必然让「设置生效」那一段红），各占一条会把同一件事
 *  说两遍，而且上下两条还互相打架（一条说「异常」，一条说「重启就好」）。所以只在**同一个**
 *  top tips 里说一句，按这个顺序取：
 *
 *   1. **真故障** ⇒ 说哪一段异常（与「运行状态」那一页的汇总行**同一个格式串**）。
 *      它排在「跑着旧版」前面：那条影子会把别的故障全盖住，用户就永远看不到真正坏在哪一环；
 *   2. 只剩旧版影子时 —— 系统侧 ⇒ 说重启手机（`SYSTEM_STALE_HINT`）；
 *   3. 宿主侧 ⇒ 说重启服务（`HOST_STALE_HINT`，比「异常」具体，且不用重启手机）。
 *
 *  判据与那一页同源（`linkFacts` + `linkChecks`），不许在这里另算一份 —— 两处说不一样的话，
 *  用户会照着错的那条去试。
 */
@Composable
private fun SettingsPage.statusTipText(): String? {
    val f = linkFacts
    val blocking = linkChecks().filter { !it.ok && it.blocking }
    //  **真故障排在「跑着旧版」前面**：换包没重启会让「设置生效」必然红，可它只是那件事的影子
    //  （`LinkCheck.staleOnly`）—— 拿影子去压住别的故障，「哪一环真坏了」就永远看不到。
    //  只剩影子时，才说「重启手机」那句（比「设置生效异常」有用得多）。
    val real = blocking.firstOrNull { !it.staleOnly }
    return when {
        real != null -> I18n.format(Strings.LINK_CHECK_BROKEN_FORMAT, real.name)

        f.restartNeeded -> SYSTEM_STALE_HINT

        f.hostStale -> HOST_STALE_HINT

        //  只剩那几条「旧版影子」：仍然说异常，别让整体红了、首页却说「一切正常」
        blocking.isNotEmpty() -> I18n.format(Strings.LINK_CHECK_BROKEN_FORMAT, blocking.first().name)

        else -> null
    }
}

/**
 * 「有新版本」那一条 —— 点「查看」开的是那个「发现新版本」弹窗（[UpdateState.reopen]），
 * 与「更多选项 → 版本」走同一个窗；点了**不把这一条收掉** —— 收不收由 [UpdateState.pending] 管
 * （窗里点「去下载」或勾「不再提示」才清）。
 */
@Composable
internal fun SettingsPage.HomeUpdateTip() {
    val release = UpdateState.pending ?: return
    HomeTopTip(
        icon = PageIcons.Download,
        text = I18n.format(Strings.UPDATE_TIP, release.displayVersion),
        action = I18n.get(Strings.UPDATE_VIEW, "查看"),
        onAction = UpdateState::reopen,
        //  只有这一条挂红点：「有新东西可取」是它的意思；旁边那条不该被读成新消息
        badge = true,
    )
}

/**
 * 「有一件事需要你处理」那一条 —— 文案由 [statusTipText] 给，这一层只管画。
 *
 *  **没有动作按钮**：这一条能做的只有重启手机 / 重启服务 / 照着「运行状态」去修，都不是点一下
 *  UI 就能完成的事 —— 给一颗假的「查看」只会让人以为点进去就修好了。
 *  也不挂红点：它不是「有新东西」。
 */
@Composable
internal fun SettingsPage.HomeStatusTip(text: String) {
    HomeTopTip(
        icon = PageIcons.HomeStatusAlert,
        text = text,
    )
}

/**
 * 一条顶栏提示 —— 上面三条共用这一份布局，所以颜色、圆角、字号、行内边距、文字的光学下移
 * 全自动一致，不会各调各的（用户口径：与更新提示一样）。
 *
 *  ## 为什么不用库的 `TopTips`
 *
 *  那条组件的纵向内边距（12 / 12）与文案行盒都是写死的，且**不暴露任何内容位移**：
 *  文字比条带中心高一档这件事在组件内没法补 —— 补图标只会让图标与字错开。
 *  这里自己铺一条，只重画布局：**颜色、圆角、字号仍取 `TopTipsDefaults`**（深浅两套、
 *  14sp 正文、强调色按钮都跟着库走），所以它与 ColorOS 那条长得一样，只是文字能落回正中。
 *  代价是丢掉了库那 250ms 的进出缩放：这一条只在「出现 / 消失」这两个时刻变化。
 *
 * @param icon 行首图标（吃主题强调色：这三条都是「有件事要你知道 / 要你做」，不是报错）
 * @param text 正文
 * @param action 行尾那颗动作的字（`null` = 没有动作，正文独占整行）
 * @param onAction 点击动作；与 [action] 必须同时给
 * @param badge 行首图标右上角那颗红点（只有「有新东西」那一条该有）
 */
@Composable
private fun SettingsPage.HomeTopTip(
    icon: ImageVector,
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    badge: Boolean = false,
) {
    val colors = TopTipsDefaults.topTipsColors()
    val textStyle = TopTipsDefaults.textStyle()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                //  左右与卡片同一档（`PAGE_MARGIN`）：它铺在卡片上方，边线必须与卡片对齐
                .padding(horizontal = PAGE_MARGIN)
                .clip(RoundedCornerShape(TopTipsDefaults.CornerRadius))
                .background(colors.containerColor)
                .padding(
                    //  左边距**不是**库那一档（12），而是「卡片行里图标格距卡片左缘的 16dp」：
                    //  条带的左边缘与卡片左边缘同一条线（都是 `PAGE_MARGIN`），所以这一格对齐了，
                    //  图标格与正文的左缘才会与下面的卡片行落在同一条竖线上（见 [TIP_START_PADDING]）。
                    start = TIP_START_PADDING,
                    top = TopTipsDefaults.VerticalPadding,
                    end = TopTipsDefaults.ContentPadding,
                    bottom = TopTipsDefaults.VerticalPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        //  图标格与库那一格同尺寸（24dp），图标本身缩一档给红点让位
        Box(Modifier.size(TopTipsDefaults.IconSize)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(TIP_ICON_SIZE),
                tint = COUITheme.colorScheme.primary,
            )
            if (badge) {
                HintBadge(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = TIP_BADGE_OUTSET, y = -TIP_BADGE_OUTSET),
                )
            }
        }
        //  图标与正文的间距**与卡片行同一档**：库给的那一档（`IconSpacing`）之后，卡片行还补了
        //  `ROW_START_ICON_GAP`（它补的是同一边——库的行内左边距比图标右边距宽一倍）。
        //  这里照抄那一笔，于是首页顶上的提示与下面的卡片，图标到文字的距离是同一条线。
        Spacer(Modifier.width(TopTipsDefaults.IconSpacing + ROW_START_ICON_GAP))
        Text(
            text = text,
            style = textStyle,
            color = colors.contentColor,
            //  文字补上那一档，才与图标、条带内边距三方对齐
            modifier = Modifier.weight(1f).offset(y = TIP_TEXT_OPTICAL_Y),
        )
        if (action != null && onAction != null) {
            TipAction(action, colors.actionColor, onAction)
        }
    }
}

/**
 * 行尾那颗动作（目前只有「查看」）—— 单独一个函数只为让 [HomeTopTip] 留在 `LongMethod` 阈值内。
 *
 *  横向留白**两边对称**，涟漪才与字同心（见 [TIP_ACTION_TAP_H]）；高度取图标那一档，
 *  整格在行里居中，涟漪也就落在条带正中。
 */
@Composable
private fun SettingsPage.TipAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    val tick = LocalTick.current
    Box(
        modifier =
            Modifier
                .clip(TIP_ACTION_SHAPE)
                .clickable {
                    //  与页面别处一样走通用振动通道（见 `LocalTick`）
                    tick()
                    onClick()
                }.height(TopTipsDefaults.IconSize)
                .padding(horizontal = TIP_ACTION_TAP_H),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TopTipsDefaults.actionTextStyle(),
            color = color,
            //  与正文补同一档，两颗字落在同一条基线上
            modifier = Modifier.offset(y = TIP_TEXT_OPTICAL_Y),
        )
    }
}
