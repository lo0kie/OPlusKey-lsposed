package com.lookie.opluskey.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.HintBadge
import com.lookie.opluskey.ui.LocalTick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.UpdateState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopTipsDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

// 首页顶部那条「有新版本」提示。
//
//  与两处红点分工：红点只说「这儿有新东西」（钉在「更多选项」与「版本」两行的行尾，用户得先点进去
//  才看得到），这一条把入口摆在首页第一屏 —— 查到新版却没人下，等于白查。
//  跟谁走：`UpdateState.hasUpdate`（= 手上有一条没处理的新版记录，`稍后` 之后仍在）。

/**
 * 文案（与尾部那个「查看」）**整体下移**多少 —— 14sp 汉字在行盒里的视觉重心比盒子的几何中心
 * 高一档，几何居中之后整行字看着「浮起来」。
 *
 *  这一档与 `SECTION_TITLE_TEXT_OPTICAL_Y` 是同一件事、同一个量：全工程补的是**文字**，
 *  不是与它并排的图标（补图标只会让图标和字错开）。
 */
private val TIP_TEXT_OPTICAL_Y = 1.dp

/** 尾部那个「查看」的点击区形状 —— 与卡片行、小标题同一档圆角（只用来收住涟漪）。 */
private val TIP_ACTION_SHAPE = RoundedCornerShape(8.dp)

/**
 * 「查看」点击区在字形左右各留多少 —— **两边必须一样**。
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
 * 首页顶部那条更新提示 —— 没有新版时**整条不进组合**（不留空档）。
 *
 *  点「查看」开的是那个「发现新版本」弹窗（[UpdateState.reopen]），与「更多选项 → 版本」走同一个窗；
 *  点了**不把这一条收掉** —— 收不收由 [UpdateState.pending] 管（窗里点「去下载」或勾「不再提示」才清）。
 *
 *  ## 为什么不用库的 `TopTips`
 *
 *  那条组件的纵向内边距（12 / 12）与文案行盒都是写死的，且**不暴露任何内容位移**：
 *  文字比条带中心高一档这件事在组件内没法补 —— 补图标只会让图标与字错开。
 *  这里自己铺一条，只重画布局：**颜色、圆角、字号仍取 `TopTipsDefaults`**（深浅两套、
 *  14sp 正文、强调色按钮都跟着库走），所以它与 ColorOS 那条长得一样，只是文字能落回正中。
 *  代价是丢掉了库那 250ms 的进出缩放：这一条只在「查到新版 / 处理完」这两个时刻出现或消失。
 */
@Composable
internal fun SettingsPage.HomeUpdateTip() {
    val release = UpdateState.pending ?: return
    val colors = TopTipsDefaults.topTipsColors()
    val textStyle = TopTipsDefaults.textStyle()
    val actionStyle = TopTipsDefaults.actionTextStyle()
    val tick = LocalTick.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                //  左右与卡片同一档（`PAGE_MARGIN`）：它铺在卡片上方，边线必须与卡片对齐
                .padding(horizontal = PAGE_MARGIN)
                .clip(RoundedCornerShape(TopTipsDefaults.CornerRadius))
                .background(colors.containerColor)
                .padding(
                    horizontal = TopTipsDefaults.ContentPadding,
                    vertical = TopTipsDefaults.VerticalPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        //  图标格与库那一格同尺寸（24dp），图标本身缩一档给红点让位
        Box(Modifier.size(TopTipsDefaults.IconSize)) {
            Icon(
                PageIcons.Download,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(TIP_ICON_SIZE),
                //  图标吃主题强调色：这一条是「有东西可取」，不是报错
                tint = COUITheme.colorScheme.primary,
            )
            HintBadge(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = TIP_BADGE_OUTSET, y = -TIP_BADGE_OUTSET),
            )
        }
        Spacer(Modifier.width(TopTipsDefaults.IconSpacing))
        Text(
            text = I18n.format(Strings.UPDATE_TIP, release.displayVersion),
            style = textStyle,
            color = colors.contentColor,
            //  文字补上那一档，才与图标、条带内边距三方对齐
            modifier = Modifier.weight(1f).offset(y = TIP_TEXT_OPTICAL_Y),
        )
        //  横向留白**两边对称**，涟漪才与字同心（见 [TIP_ACTION_TAP_H]）；
        //  高度取图标那一档，整格在行里居中，涟漪也就落在条带正中。
        Box(
            modifier =
                Modifier
                    .clip(TIP_ACTION_SHAPE)
                    .clickable {
                        //  与页面别处一样走通用振动通道（见 `LocalTick`）
                        tick()
                        UpdateState.reopen()
                    }.height(TopTipsDefaults.IconSize)
                    .padding(horizontal = TIP_ACTION_TAP_H),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = I18n.get(Strings.UPDATE_VIEW, "查看"),
                style = actionStyle,
                color = colors.actionColor,
                //  与正文补同一档，两颗字落在同一条基线上
                modifier = Modifier.offset(y = TIP_TEXT_OPTICAL_Y),
            )
        }
    }
}
