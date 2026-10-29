package com.lookie.opluskey.ui.screen.donate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.PageScrollColumn
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.Donor
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.donors
import com.lookie.opluskey.ui.model.opticalCenter
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.VerticalDivider
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「捐赠列表」页 —— 从首页「其他 → 捐赠打赏」那层菜单的最后一项进来（见 [Screen.DONATE_LIST]）。
 *
 *  **数据与呈现分家**：谁给了多少在 `assets/donors.json`（读法见 `ui/model/Donor`），
 *  排成什么样全在这一页 —— 就是一张**两列的表**（名字、金额），格子居中、行列之间有线、
 *  外面一圈框，表头那一行只靠字加深（加粗 + 主文字色，不铺底色）。
 *
 *  **这张表是手写的，不走正文记号那套**：`ui/EmphasisText` 只认**加粗**与
 *  **行首 `- `** 两种记号，是给弹窗正文用的 —— 它根本没有表格，硬上也画不出这张表要的
 *  东西（格子居中、行列都画线、外圈一圈框）。一张两列静态表要的这里全有现成的
 *  （`Row` / `Text` / `HorizontalDivider` / `VerticalDivider`），手写比硬套省事。
 *
 *  **没有小标题**：一页只有这一张表，顶上再叠一行「捐赠列表」（与顶栏大标题同一个键）只是重复。
 *
 *  **不套 COUI 的 `Card`**：表格自己就是一张有底色的板子，再塞进一张卡片里就是盒子套盒子。
 *
 *  名单为空时走**空态**那一档（那一档仍是卡片 + 一行只读说明）：「还没有人」与「这一页坏了」
 *  得能一眼分开。
 */
@Composable
internal fun SettingsPage.DonateListScreen(scrollBehavior: ScrollBehavior) {
    // 静态数据，进页读一次就够（进程内还有一层缓存，见 `ui/model/Donor`）
    val context = LocalContext.current
    val rows = remember { donors(context) }
    PageScrollColumn(scrollBehavior = scrollBehavior) {
        // 顶栏叠在内容上，顶部要自己让出（动态值）。这一页**没有小标题**、首行就是内容本身，
        //  所以按「选择应用」那一页的账另加一档 [PAGE_TOP_PADDING]（见 `AppPicker`）。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior) + PAGE_TOP_PADDING))

        if (rows.isEmpty()) {
            EmptyDonorCard()
        } else {
            DonorTable(rows)
        }
    }
}

/**
 * 表格的线宽 —— **框与格线同一支笔**，直接引 COUI 分隔线那一档（`DividerDefaults.Thickness`）。
 *
 *  不另写一个数：外框与格子里的线粗细/颜色都要一样，而这两样 COUI 已经有一套现成的
 *  （`DividerDefaults`，页面上下 listview 的行线用的就是它）—— 写死就等于多一套要与主题对账的数。
 */
private val TABLE_LINE = DividerDefaults.Thickness

/**
 * 表格的圆角 —— **直接引 [CardDefaults.CornerRadius]**（`coui_round_corner_m`，12dp）：
 *  这一页上下的 listview 就是卡片，圆角取同一档才对得上；写死一个数迟早会和主题走散。
 *
 *  底色同理，取卡片那档默认色 `surfaceContainer`（见 [DonorTable]）—— 一块和卡片一样的板子。
 */
private val TABLE_CORNER = CardDefaults.CornerRadius

/** 格子四周的内边距 —— 上下左右等宽，格子里的字才在正中。 */
private val TABLE_CELL_PADDING = 16.dp

/**
 * 格子里那行字的行高对齐 —— 主题那套把多出来的行距**全压在字下面**
 *  （`Alignment.Top` + `Trim.None`，是给列表里左对齐的整行文字用的），
 *  在这里就等于把字抬到格子偏上的位置：改成平摊到上下两边，字才落在正中。
 *
 *  `Trim.None` 跟着主题：行高仍是主题那一档，行高不变，只是字在里面居中。
 */
private val CELL_LINE_HEIGHT =
    LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * 名单为空时那一档 —— 一行只读说明。
 *
 *  **不给行尾图标**（没有可点的东西，给箭头会读成「点得进去」）。
 */
@Composable
private fun EmptyDonorCard() {
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    title = I18n.get(Strings.DONATE_LIST_EMPTY_TITLE, "还没有捐赠记录"),
                    summary = I18n.get(Strings.DONATE_LIST_EMPTY_SUMMARY, "收到的每一份支持都会记在这里"),
                    icon = PageIcons.Donate,
                ),
            ),
    )
}

/**
 * 那张表 —— 一张板子，里面一行表头 + 一行一条数据。
 *
 *  线一律用 COUI 那两支现成的（[HorizontalDivider] / [VerticalDivider]），颜色与笔宽跟着主题走，
 *  不另引一套「表格线的颜色」。**表头下面那条横线也在这套里**（它就是第一条横线），
 *  不靠「表头自带的边界」——手写的东西不该有两处画线的地方。
 *  **外框也用同一档**（[TABLE_LINE] + 同一个 `dividerLine`）：框和格线不是两种线，
 *  谁粗一点谁深一点都会读成「两套东西拼起来的」。
 *
 *  **圆角靠 `clip`**：表头那一行与横线都要铺满整块板子，不裁的话方角会顶穿圆角。
 *  裁在板子上、描边描在裁之后（`border` 排在 `clip` 后面），框线才不会被裁掉半条。
 */
@Composable
private fun DonorTable(rows: List<Donor>) {
    val scheme = COUITheme.colorScheme
    val shape = RoundedCornerShape(TABLE_CORNER)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_MARGIN)
                .clip(shape)
                .background(scheme.surfaceContainer)
                .border(TABLE_LINE, DividerDefaults.DividerColor, shape),
    ) {
        DonorRow(
            name = I18n.get(Strings.DONATE_LIST_COL_NAME, "捐赠人"),
            amount = I18n.get(Strings.DONATE_LIST_COL_AMOUNT, "金额"),
            header = true,
        )
        rows.forEach { row ->
            // 表头与第一条数据之间也走这一条，别让表头靠「自己的边界」分家
            HorizontalDivider()
            DonorRow(name = row.name, amount = row.amountText, header = false)
        }
    }
}

/**
 * 表格的一行：名字一格、金额一格，两格等宽。
 *
 *  **表头加深只在字上**：与数据行同一档字号，但加粗、且用主文字色（`onBackground`）——
 *  数据行是次级灰（`onBackgroundVariant`），一眼分得出哪一行是列名。
 *  **表头不铺底色**：整张表是**一块**板子（底色一次铺满），表头那一行再压一档会把板子切成两截。
 *
 *  行高按最高的那一格算（`IntrinsicSize.Min`），竖线（`fillMaxHeight`）才撑得住整行。
 */
@Composable
private fun DonorRow(
    name: String,
    amount: String,
    header: Boolean,
) {
    val scheme = COUITheme.colorScheme
    val style =
        COUITheme.textStyles.body2
            .copy(lineHeightStyle = CELL_LINE_HEIGHT)
            .let {
                if (header) {
                    it.copy(fontWeight = FontWeight.Bold, color = scheme.onBackground)
                } else {
                    it.copy(color = scheme.onBackgroundVariant)
                }
            }
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DonorCell(name, style, Modifier.weight(1f))
        VerticalDivider()
        DonorCell(amount, style, Modifier.weight(1f))
    }
}

/**
 * 一格：撑满自己那份宽，字居中；超长按尾省略，不换行把行撑高。
 *
 *  行高由 [CELL_LINE_HEIGHT] 摊平，字按行盒居中 —— 不再对各字体做额外的墨迹修正。
 */
@Composable
private fun DonorCell(
    text: String,
    style: TextStyle,
    modifier: Modifier,
) {
    Box(modifier = modifier.padding(TABLE_CELL_PADDING), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            // 格子高度由行高固定、字在里面竖居中 —— 补一档光学修正（见 `opticalCenter`）
            modifier = Modifier.opticalCenter(style),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
