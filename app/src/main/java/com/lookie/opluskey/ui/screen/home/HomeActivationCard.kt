package com.lookie.opluskey.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.update.ReleaseInfo
import com.lookie.opluskey.ui.LocalAppTheme
import com.lookie.opluskey.ui.LocalTick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appVersionLabel
import com.lookie.opluskey.ui.blockedReason
import com.lookie.opluskey.ui.linkFacts
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.AppTheme
import com.lookie.opluskey.ui.model.CHECKING_HINT
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT
import com.lookie.opluskey.ui.model.isDarkScheme
import com.lookie.opluskey.ui.model.opticalCenter
import com.lookie.opluskey.ui.setModuleEnabled
import com.lookie.opluskey.ui.state.UpdateState
import com.lookie.opluskey.ui.statusTipText
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.utils.PressFeedbackType

// 首页最上面那一块 —— 形制照 LSPosed 管理器首页那张状态卡（Magisk 首页那张同款）：
// 一块带底色的卡，左上是大字状态，底下跟版本与一句说明，右上角压一颗同色水印字形（被卡片边裁掉一角）。
//
//  这一块把首页顶上原先那四件东西收成一件：**模块状态 / 版本 / 两条提示 / 主开关**。
//  它们本来分三四块摆，读起来像三四件事，实际都在答「这个模块现在什么情况」。
//  主开关**不再有自己的一行**（也没有那颗开关）—— **点这一整块就是开关**，见 [SettingsPage.HomeActivationCard]。

/**
 * 「这个模块现在是什么情况」的四档 —— 判据见 [SettingsPage.HomeActivationCard]。
 *
 *  [Disabled] 与 [Inactive] 是两件不同的事，**不能合并**：前者是用户自己关掉的（不报错、不给红），
 *  后者是开着却没在系统里生效（那是故障）。
 */
private enum class ActivationState {
    /** 总闸开着、注入侧回了包、接管点挂上了。 */
    Active,

    /** 总闸关着 —— 用户自己关的，点一下这块就能开回来。 */
    Disabled,

    /** 总闸开着，但自检跑完注入侧仍没回话（或一个接管点都没挂上）。 */
    Inactive,

    /** 自检还没跑完 —— `inject == null` 此刻只是初值，不是结论。 */
    Checking,
}

/** 状态那一句大字 —— 四档各一句。 */
private fun ActivationState.label(): String =
    when (this) {
        ActivationState.Active -> I18n.get(Strings.HOME_ACTIVATION_ACTIVE, "已激活")
        ActivationState.Disabled -> I18n.get(Strings.HOME_ACTIVATION_DISABLED, "已关闭")
        ActivationState.Inactive -> I18n.get(Strings.HOME_ACTIVATION_INACTIVE, "未激活")
        ActivationState.Checking -> I18n.get(Strings.HOME_ACTIVATION_CHECKING, "检查中")
    }

/**
 * 大字下面那一句 —— 说的是**这个状态意味着什么**，不是「该去哪修」。
 *
 *  「为什么不能用 / 该做什么」在 [ActivationState.Inactive] 那一档由 [reason] 说
 *  （与整页总闸同源，见 [SettingsPage.HomeActivationCard]），其余三档没有故障可讲。
 *
 *  开 / 关两句直接用主开关原来那两句副标题（`HOME_SWITCH_DESC_*`）—— 这一块现在就是那个开关，
 *  文案不该跟着 UI 换个地方就换一种说法。
 *
 * @param reason 未激活的**具体原因**（`blockedReason()` 给的、已经能直接照做的那句）。
 *   `null` 时退回那句旧的概括 —— 理论上 [SettingsPage.linkReady] 为假就一定有原因，
 *   这一支只是不让文案在边界上变成空串。
 */
private fun ActivationState.detail(reason: String?): String =
    when (this) {
        ActivationState.Active -> I18n.get(Strings.HOME_SWITCH_DESC_ON, "按键由模块接管，执行绑定动作")

        ActivationState.Disabled -> I18n.get(Strings.HOME_SWITCH_DESC_OFF, "按键交还系统，不执行任何绑定")

        // 未激活 = 整页总闸（`linkReady`）里的某一段不 ok，**不再只说「系统侧未接管」**：
        //  那是其中一种，而「设置没送到 / 执行侧读不懂配置 / 系统侧跑着旧包」都让它为假，
        //  拿那一句去答，用户会照着去勾作用域 —— 白翻一遍。
        ActivationState.Inactive -> reason ?: I18n.get(Strings.HOME_ACTIVATION_INACTIVE_DESC, "系统侧未接管按键")

        ActivationState.Checking -> CHECKING_HINT
    }

/**
 * 这一块用的**整套颜色** —— 底色 / 标题 / 说明 / 水印 / 提示图标。
 *
 *  ## 为什么不只给一个「强调色」
 *
 *  四档里有两档（[ActivationState.Disabled] / [ActivationState.Checking]）**没有色相**。原先它们
 *  拿中性色当强调色再去混：而那两个中性色本身是半透明的（`onSurfaceVariantActions` = 26% 黑），
 *  `lerp` 连 alpha 一起插值，于是
 *   · 卡片底只剩 91% 不透明 ⇒ 压在页面底上几乎同色，这一块看不出是张「卡」；
 *   · 标题只剩 37% 不透明 ⇒ 大字读起来是灰的（对比度 2.5:1，连大字那档 3:1 都不到）；
 *   · 水印 / 图标再 `.copy(alpha = …)` 一次，等于把半透明又压掉一半。
 *  ⇒ 中性两档不该走「掺色」这条路（见 [paletteOf]），所以颜色按**角色**成对给，不给单个种子。
 */
private data class ActivationPalette(
    val container: Color,
    val title: Color,
    val detail: Color,
    val mark: Color,
    val icon: Color,
)

/**
 * 某一档的配色 —— 四档只在这一处分叉（卡片、标题、说明、水印、提示图标都从这里取，
 * 不然加一档状态就得在五个地方各补一支 `when`）。
 *
 * ## 有色相的两档（已激活 / 未激活）
 *
 *  底色是**卡片本来的底往强调色里掺一档**：
 *
 *  · 不是 `accent.copy(alpha = …)` 铺一层：半透明压在**页面底色**上，而页面底在 Monet 下比卡片底
 *    更亮 ⇒ 掺出来比下面那些卡片还淡，看着不像「一张卡」。
 *  · 也不是 `primaryContainer`：Monet 下它是 tone 90 实色，铺满一屏太艳；非 Monet 下 COUI 的
 *    `primaryContainer` **就是主色本身**，拿它当底、字再取主色 ⇒ 同色叠同色，字看不见。
 *  · 深浅两档各一个比例：同一个比例在深色底上明显更淡，不提一档那块「卡」就散了。
 *  · **只有底色与水印跟着强调色**（Monet 下字色与提示图标也是掺出来的）：非 Monet 那套是
 *    「固定中性底 + 一支预设强调色」，字再掺色就成了浅灰卡上写一行蓝字 —— 用户口径是那一档
 *    不要走 Monet 的配色，所以字色与提示图标改取主题的正文色（标题全值、说明压到
 *    [ACTIVATION_DETAIL_PLAIN_ALPHA]，详见 [paletteOf] 的返回处）。反过来，那一套的底色
 *    要比 Monet 更深一些才让人一眼分得出状态 —— 字里不再有色相，色相全压在这一层（见
 *    [MIX_PLAIN_LIGHT]）。
 *
 * ## 中性两档（已关闭 / 检查中）—— **不染色**
 *
 *  · 底色回到 [Colors.surfaceContainer]（普通卡片那一格）。原来是把半透明中性色
 *    （`onSurfaceVariantActions` = 26% 黑）当种子再 `lerp`：**alpha 也一起被插值**，算出来是 91%
 *    不透明的近白色，浅色下与页面底（`surface` #F0F1F2）几乎同色 —— 这一块看不出是张「卡」。
 *  · 字色就是主题那两档（`onSurfaceContainer` / `onSurfaceContainerVariant`）：与下面那些卡片行
 *    同一套主次。同一个 alpha 插值把大字稀释成 37% 不透明，读起来发灰（2.5:1，连大字那档 3:1 都不到）。
 *  · 状态的轻重靠**字本身**与右上角那颗水印，不靠底色发光。
 */
private fun ActivationState.paletteOf(
    scheme: Colors,
    monet: Boolean,
): ActivationPalette {
    val accent =
        when (this) {
            ActivationState.Active -> scheme.primary

            // 关掉是用户的意图，**不上红**（与 `linkChecks` 里「主开关已关闭」不算故障同一条口径）
            ActivationState.Inactive -> scheme.error

            ActivationState.Disabled, ActivationState.Checking -> null
        }
    if (accent == null) {
        return ActivationPalette(
            container = scheme.surfaceContainer,
            title = scheme.onSurfaceContainer,
            detail = scheme.onSurfaceContainerVariant,
            mark = scheme.onSurfaceContainer.copy(alpha = ACTIVATION_NEUTRAL_MARK_ALPHA),
            icon = scheme.onSurfaceContainerVariant,
        )
    }
    val mix =
        when {
            monet -> if (scheme.isDarkScheme) MIX_MONET_DARK else MIX_MONET_LIGHT
            else -> if (scheme.isDarkScheme) MIX_PLAIN_DARK else MIX_PLAIN_LIGHT
        }
    //  字色与提示图标**只在 Monet 下掺强调色**。
    //  非 Monet 那套是「固定中性底 + 一支预设强调色」：底色掺一点已经够把这一块与普通卡分开，
    //  字再跟着掺色就成了「一张浅灰卡上写一行蓝字」—— 用户口径是**不要走 Monet 那套配色**。
    //  所以非 Monet 下退回主题的正文色，色相只留在底色与水印上。
    //
    //  说明那一档**不用 `onSurfaceContainerVariant`**（40% 正文色）：浅色下它比原来那个掺色值
    //  浅一大截，读起来发飘（真机口径「说明的文字太亮了」）。取主正文色压到
    //  [ACTIVATION_DETAIL_PLAIN_ALPHA]：两个模式下都落在「比标题浅一档、比次要色深一档」
    //  的位置，而且一点色相都不带。提示行首那颗图标与它同档 —— 它是注脚，不该与正文差太远。
    val plainDetail = scheme.onSurfaceContainer.copy(alpha = ACTIVATION_DETAIL_PLAIN_ALPHA)
    return ActivationPalette(
        container = lerp(scheme.surfaceContainer, accent, mix),
        title = if (monet) lerp(accent, scheme.onSurfaceContainer, ACTIVATION_TITLE_MIX) else scheme.onSurfaceContainer,
        detail = if (monet) lerp(accent, scheme.onSurfaceContainer, ACTIVATION_DETAIL_MIX) else plainDetail,
        mark = accent.copy(alpha = WATERMARK_ALPHA),
        //  提示行首那颗小图标与「说明」同档：它是注脚的一部分，颜色不该比它说明的那句话更艳
        icon = if (monet) accent.copy(alpha = ACTIVATION_TIP_ICON_ALPHA) else plainDetail,
    )
}

/**
 * 水印字形 —— 管理器那张压的就是一颗大勾，这里用同一支笔，但取**空心**那一枚。
 *
 *  · 已关闭换 `cancel`（同一族的否定形，与那颗勾一实一虚成对）；
 *  · 未激活 / 检查中换 `alert`：前者是故障，后者是「还没结论」，两档靠**颜色**分（红 / 灰）。
 *
 *  不用实心的 [PageIcons.TileChecked]：放到 112dp 上那是一整块实心圆，压在底色上像一块色斑；
 *  也不用行首那几颗 `home-check` / `home-cancel` —— 放大之后它们会读成「一栋房子」，
 *  水印这一格要的是**符号**，不是插画。
 */
private val ActivationState.watermark: ImageVector
    get() =
        when (this) {
            ActivationState.Active -> PageIcons.CircleCheckOutline
            ActivationState.Disabled -> PageIcons.Cancel
            ActivationState.Inactive -> PageIcons.HintAlert
            ActivationState.Checking -> PageIcons.HintAlert
        }

/**
 * 这一块**上方**那一档 —— 取小标题自带的那个 16dp（`SECTION_TITLE_PAD_TOP`）。
 *
 *  这一块没有小标题，而它上面那一档间距本来是由小标题给的（首页以前的第一块就是「主开关」，
 *  它的标题自带这一档）。照抄同一个数，于是它与下面各块的节奏与别处一致。
 */
private val ACTIVATION_TOP_GAP = 16.dp

/** 卡里文字那一圈**横向**内边距 —— 比卡片行的 16 宽一档：这一块是「一整块」，不是一行。 */
private val ACTIVATION_CONTENT_PADDING = 20.dp

/**
 * 卡里文字那一圈**纵向**内边距 —— 14dp，比横向窄一档。
 *
 *  这一块只有三行字，上下各 20dp 会让整块显得「虚」（真机口径「整体上下留白减少」）；
 *  横向那 20dp 不能跟着收 —— 它要与卡片行的文字左缘对齐。
 */
private val ACTIVATION_CONTENT_VERTICAL = 14.dp

/**
 * 上面那一段**收尾**时的下内边距 —— 12dp，只在它下面还有提示段（分隔线）时用。
 *
 *  分隔线两侧各留 12dp 就够：线是块内部的划分，两侧都按外圈那 20dp 会松得像两块卡。
 *  没有提示段时这一块就是整张卡的底，下边距回到 [ACTIVATION_CONTENT_PADDING] ——
 *  那时它要与上边缘那一档对称（实测卡顶到首行墨迹 21.7dp、末行墨迹到卡底 22dp）。
 */
private val ACTIVATION_HEAD_BOTTOM = 12.dp

/** 大字与版本之间那一档 —— 版本是同一段信息的另一半，贴紧一点。 */
private val ACTIVATION_VERSION_GAP = 2.dp

/** 版本与说明之间那一档。 */
private val ACTIVATION_LINE_GAP = 8.dp

/** 说明那一段与下面「提示」那一段之间那条细线的左右内缩 —— 与文字同一档。 */
private val ACTIVATION_DIVIDER_INSET = ACTIVATION_CONTENT_PADDING

/**
 * 提示那一段的**上**内边距 —— 12dp，与上面那一段收尾时的下内边距同一档（见 [ACTIVATION_HEAD_BOTTOM]）。
 *
 *  分隔线两侧必须一样宽，否则线看着偏上 / 偏下（真机口径「分隔线上下边距不一致」）；但这一档
 *  又**不该**跟卡片外圈那 20dp 齐平 —— 线是块**内部**的划分，两侧各 20dp 会松得像两块卡
 *  （真机口径「分割线上下留白太多了」）。
 */
private val ACTIVATION_TIPS_TOP = 12.dp

/** 提示那一段的**下**内边距 —— 与上面那一档一样：这一段的最后一行也只有一行字。 */
private val ACTIVATION_TIPS_BOTTOM = ACTIVATION_TIPS_TOP

/** 提示那一段里两条之间的距离。 */
private val ACTIVATION_TIP_GAP = 10.dp

/** 提示行首那颗图标的边长 —— 比行首图标小一档：它是注脚，不是内容。 */
private val ACTIVATION_TIP_ICON = 18.dp

/** 提示图标与文字之间那一档。 */
private val ACTIVATION_TIP_ICON_GAP = 8.dp

/** 彩色底上提示行首那颗图标的透明度 —— 比正文弱一档，但仍与它同源（强调色）。中性两档不压。 */
private const val ACTIVATION_TIP_ICON_ALPHA = 0.7f

/**
 * 水印字形的边长 —— 取 112dp：比这块卡的文字区高一截，才压得住一角。
 * 与下面的 [WATERMARK_OUTSET] 配合，让它只露出**一角**（管理器那张也是这么裁的）。
 */
private val WATERMARK_SIZE = 112.dp

/** 水印往卡片外挪多少 —— 挪出去的部分由卡片圆角裁掉，露出一角才像水印、不像插图。 */
private val WATERMARK_OUTSET = 24.dp

/** 水印的透明度 —— 压在底色上刚好认得出形状，又不会跟文字抢。 */
private const val WATERMARK_ALPHA = 0.16f

/**
 * 中性两档的水印透明度 —— 比彩色底那档再淡一点。
 *
 *  中性底的对比本来就只有「灰底 vs 深灰字形」这一档，16% 压上去会显成一团实心灰；
 *  10% 刚好只留形状（彩色底那 16% 是**彩色**压在浅底上，本身就比灰色轻）。
 */
private const val ACTIVATION_NEUTRAL_MARK_ALPHA = 0.10f

/**
 * 卡片底色里掺多少强调色 —— **Monet 与非 Monet 两套数**，因为两套色板的「底」不是一个起点。
 *
 *  · **Monet**：`surfaceContainer` 本身就带一层壁纸的色相，掺 8% 已经看得出是「有颜色的卡」；
 *    再高整块就偏深（真机口径「底色颜色太深了」）。这一档不动。
 *  · **非 Monet**：底是 COUI 那套中性灰白，一点色相都没有 —— 掺进去的色相要先把它「染出来」，
 *    才谈得上浓淡。这一档早先按 Monet 的 8% 试过（那是「灰里透一点蓝」），16% 也只到
 *    「看得出是强调色的卡片」，在真机上仍是**浅灰卡**（用户口径「非 monet 下首页卡片底色太淡」）：
 *    底色一淡，右上角那颗水印与卡里那两行字就成了这一块唯一的重量，整张卡读起来是「浮」的。
 *    现在提到 26% —— 那是「一眼认得出这是开启 / 关闭状态的卡」的那一格；再往上（三成以上）
 *    浅色档开始往强调色的中间调走，白底上的正文对比掉下来，所以停在这里。
 *  · 深色档各再提一档：同一个比例在深色底上明显更淡，不提那块「卡」就散了。
 */
private const val MIX_MONET_LIGHT = 0.08f
private const val MIX_MONET_DARK = 0.14f
private const val MIX_PLAIN_LIGHT = 0.26f
private const val MIX_PLAIN_DARK = 0.34f

/**
 * 标题（状态大字）的字色混合比例 —— 强调色往 `onSurfaceContainer` 只拉一成半。
 *
 *  比 [ACTIVATION_DETAIL_MIX] 浅得多：标题是这一块最重的一行，色相要认得出、深度也要够，
 *  但不该和下面那句说明只差一档。
 */
private const val ACTIVATION_TITLE_MIX = 0.15f

/**
 * 版本 / 说明那两行的字色混合比例 —— 强调色往 `onSurfaceContainer` 拉四成五。
 *
 *  只留强调色太艳（那是水印与大字的活），只留内容色就成了中性黑（Monet 下「没跟上主题」）。
 *  四成五落在中间：色相还是这一档的强调色，深度够压住彩色底 —— 实测浅色档落在 `#4A5A6E` 一带，
 *  比卡片行那句说明实、又没到标题那么黑。
 */
private const val ACTIVATION_DETAIL_MIX = 0.45f

/**
 * **非 Monet** 下版本 / 说明那两行的字色 —— 主正文色压到这一档透明度（不带任何色相）。
 *
 *  为什么不是 `onSurfaceContainerVariant`（40% 正文色）：浅色下它比 [ACTIVATION_DETAIL_MIX]
 *  算出来的那个掺色值浅一大截，整行读起来发飘（真机口径「说明的文字太亮了」）。
 *  七成半落在「比标题浅一档、比次要色深一档」：浅色下约等于原来那个 `#4A5A6E` 的深度，
 *  深色下也仍低于标题一档 —— 主次不变，只是不再带色相。
 */
private const val ACTIVATION_DETAIL_PLAIN_ALPHA = 0.75f

/** 行尾那颗动作的点击区形状 —— 与卡片行、小标题同一档圆角（只用来收住涟漪）。 */
private val ACTIVATION_ACTION_SHAPE = RoundedCornerShape(8.dp)

/** 动作点击区在字形四周留多少 —— 四边一样，涟漪才与字同心。 */
private val ACTIVATION_ACTION_TAP = 6.dp

/**
 * 首页顶上那一块「模块状态」—— 形制照 LSPosed 管理器 / Magisk 首页那张状态卡。
 *
 * ## 一块顶四块
 *
 *  页面上其余信号都散着：主开关原先单独一张卡、「运行状态」在「更多选项」里（要先进去才看得到）、
 *  顶上还挂着两条提示条。它们答的其实是同一个问题 —— 「这个模块现在什么情况」。
 *  现在这一块从上到下就是：状态大字 → 版本 → 一句说明 → 两条提示（有才出现）。
 *
 * ## 点这一块 = 拨主开关
 *
 *  **主开关不再有自己的一行**（也没有那颗开关）：整块可点，点一下就在开 / 关之间切换，
 *  大字跟着变成「已激活 / 已关闭」。判据仍然是 `GlobalConfig.enabled` —— 执行侧的总闸
 *  （注入侧 `ConfigChannel.enabledOf` 与宿主侧各有一道 `if (!…enabled) return`），
 *  所以它不只是界面上的开关，是真的把功能关掉。
 *
 *  ## 判据
 *
 *  「已激活」= **整页总闸为真**（`LinkFacts.ready`：宿主报活 + 注入侧回了包且接管点挂上 +
 *  设置送到且指纹对得上 + 执行侧那份配置读得懂）。
 *
 *  早先这里判的是 `respondOk`（只看「注入侧回了包、接管点挂上了」），于是中间任一段断了
 *  —— 设置没送到、执行侧读不懂配置、跑着旧包 —— 页面整片灰、这一块却说「已激活」，
 *  用户看到的就是「状态异常，可大卡片一切正常」。判据与整页总闸**必须同源**，这一块才配叫状态卡。
 *
 *  关着时**不报红**：那是用户的意图（与「运行状态」里「主开关已关闭」不算故障同一条口径）。
 *
 *  自检没跑完（`injectChecked = false`）时是中性的「检查中」：那几秒里 `inject == null` 只是初值、
 *  不是结论，报「未激活」就是假故障（`LinkFacts.injectChecked` 正是为这件事加的）。
 *
 *  ## 什么时候点不动
 *
 *  判据见 [activationToggleable]，只那一处：整块的 `onClick` 与「点按卡片即可开启」那句提示
 *  都读它 —— 两处各写一遍就会出现「提示说能点、其实点不动」。
 */
@Composable
internal fun SettingsPage.HomeActivationCard(ui: HomeUiState) {
    val facts = linkFacts
    val masterOn = configState.config.value.global.enabled
    val state =
        when {
            //  总闸关着是**确定的事实**（用户自己拨的），优先于「还没问完」
            !masterOn -> ActivationState.Disabled

            !facts.injectChecked -> ActivationState.Checking

            facts.ready -> ActivationState.Active

            else -> ActivationState.Inactive
        }
    //  **未激活时那一句具体原因**：与整页总闸同源（`blockedReason` 的每一档都写着下一步做什么）。
    //  它替掉了原先固定那句「系统侧未接管按键」—— 那句只在「注入侧没回包」时成立，
    //  而剩下的档（设置没送到 / 执行侧读不懂 / 跑着旧包）都被它答成同一句，把人指去勾作用域。
    val reason = if (state == ActivationState.Inactive) blockedReason() else null
    //  版本只读一次：这串要读包信息，每帧重读没有意义（与它原先在顶栏里那份同一个口径）
    val version = remember { appVersionLabel() }
    //  下半段那两条提示（有新版本 / 有一件事要处理）—— 一次读全：既回答「有没有下半段」
    //  （上面那一块的内边距要看它），也直接递给 [ActivationTips]，不必让它再读一遍同一组状态。
    val release = UpdateState.pending
    //  上面那句已经说过的**不在下面重说**：未激活时 detail 就是原因，同一条原因在同一块卡里
    //  出现两次，读起来像两个不同的问题。
    val status = statusTipText()?.takeIf { it != reason }
    // 配色按「档」算一次，再让几个角色各自过渡：状态换档那一刻不该硬切。
    //  底色 / 标题 / 说明 / 水印都带过渡 —— 水印是一颗 112dp 的大字形，硬换色比字还显眼；
    //  提示行首那颗 18dp 的小图标不带（它只在有提示时才出现，本来就没有「上一档」可比）。
    val palette = state.paletteOf(COUITheme.colorScheme, monet = LocalAppTheme.current == AppTheme.MONET)
    val container by animateColorAsState(palette.container, COLOR_TRANSITION_SPEC, label = "ActivationContainer")
    val titleColor by animateColorAsState(palette.title, COLOR_TRANSITION_SPEC, label = "ActivationTitle")
    val detailColor by animateColorAsState(palette.detail, COLOR_TRANSITION_SPEC, label = "ActivationDetail")
    val markColor by animateColorAsState(palette.mark, COLOR_TRANSITION_SPEC, label = "ActivationMark")
    val shown = palette.copy(container = container, title = titleColor, detail = detailColor, mark = markColor)
    //  点不动的档：库那个交互式重载**没有 enabled**，不给 `onClick` 就是非交互卡（不吃点击）
    val canToggle = activationToggleable
    val tick = LocalTick.current
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = PAGE_MARGIN, top = ACTIVATION_TOP_GAP, end = PAGE_MARGIN),
        // 卡自己的内边距**清零**：这一块的留白按「一整块」给（见 [ACTIVATION_CONTENT_PADDING]），
        //  叠上库那份行内边距会变成两层，文字离卡边比下面的卡片远一截。
        insideMargin = PaddingValues(0.dp),
        //  内容色跟着标题走：它只影响按压指示（库的分隔线读主题，不读这里），
        //  而标题色正是这一档「文字该是什么色」的唯一口径。
        colors = CardDefaults.defaultColors(color = container, contentColor = shown.title),
        //  **Tilt**：按下去整块跟着触点轻微倾斜 —— 这一块没有开关也没有箭头，倾斜是它唯一
        //  「我是一颗开关」的触感（`Sink` 那种下沉在这么大的块上几乎看不出来）。
        pressFeedbackType = PressFeedbackType.Tilt,
        //  倾斜之外再压暗一档：`Tilt` 只动几何、不改颜色，一大块浅色底在按下去时几乎没有变化
        //  （用户口径「除了偏转再压暗一些」）。`showIndication` 画的就是库那层按压指示。
        showIndication = true,
        onClick =
            if (canToggle) {
                {
                    tick()
                    toggleMaster(ui)
                }
            } else {
                null
            },
    ) {
        ActivationBody(state, version, shown, ActivationExtras(reason, release, status))
    }
}

/**
 * 这一块现在**点不点得动** —— 判据只有这一处（整块可点的那个 `onClick` 与「点按即可开启」那句
 * 提示都读它）：两处各写一遍的话，会出现「提示说能点、其实点不动」。
 *
 *  `linkReady`（宿主侧在跑 **且** 注入侧报到了）**外加** `!configReadOnly`；
 *  **不吃整页总闸** —— 后者含它自己，关掉之后全页禁用，它就再也开不回来了。
 *  `!configReadOnly` 这一条是被真机逼出来的：只读态下刚进页时链路还没报到 ⇒ 这一块是死的；
 *  等注入侧回包一到、`linkReady` 变 true，它就自己活过来，用户当场点了三下（每次都被 `write` 拒掉）。
 *  **「点了没反应」比「一开始就灰着」更难读**。
 */
private val SettingsPage.activationToggleable: Boolean
    get() = linkReady && !configState.readOnly

/**
 * 这一块除了「状态大字 + 版本」还要说的三件事 —— 合成一个类型是为了让
 * [ActivationBody] / [ActivationHead] 的参数表停在一只手数得过来。
 *
 *  这三项本来就是同一件事（「除了状态，这块还有话要说吗」），一起给比散成三个形参更好读。
 *
 *  @param reason 未激活时的**具体原因**（见 [ActivationState.detail]）
 *  @param release 有新版本时那条（`null` = 没有）
 *  @param status 下半段那句「能用但没落到新代码上」（`null` = 没有）
 */
private class ActivationExtras(
    val reason: String?,
    val release: ReleaseInfo?,
    val status: String?,
) {
    /** 这一块下面还有没有提示段（决定上半段的下内边距，见 [ACTIVATION_HEAD_BOTTOM]）。 */
    val hasTips: Boolean get() = release != null || status != null
}

/**
 * 卡的里子：水印 + 上半段 + 下半段。
 *
 *  抽出来只为让 [SettingsPage.HomeActivationCard] 留在 detekt 的 `LongMethod` 长度线内
 *  （那一支里已经塞了状态判定、两个颜色过渡和库那串卡片参数）。
 */
@Composable
private fun SettingsPage.ActivationBody(
    state: ActivationState,
    version: String,
    palette: ActivationPalette,
    extras: ActivationExtras,
) {
    Box(
        // 水印要能被卡片圆角裁掉一角 —— 库的卡不裁子级，这里自己收一次边
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CardDefaults.CornerRadius)),
    ) {
        //  水印**不参与定尺寸**（`matchParentSize` 的子级是父级量完之后才量的）：它 112dp 高，
        //  比这一块的内容还高，直接当兄弟摆会把卡片撑高 —— 表现出来就是「下面多出一截空」，
        //  而且那一截跟内边距无关，怎么调都调不动。
        //
        //  压在**右上角**（不是右下）：右下留给下面那几行提示，压在字底下就成了脏点。
        Box(Modifier.matchParentSize()) {
            Icon(
                state.watermark,
                contentDescription = null,
                tint = palette.mark,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = WATERMARK_OUTSET, y = -WATERMARK_OUTSET)
                        .size(WATERMARK_SIZE),
            )
        }
        Column {
            ActivationHead(state, version, palette, extras)
            ActivationTips(extras.release, extras.status, palette)
        }
    }
}

/**
 * 拨一次主开关 —— 整块可点的那个动作。
 *
 *  打开之前先要一次确认：没确认过就只弹窗、**不写盘**。顺序不能反过来 —— 先写后弹等于
 *  「先启用了再问你同不同意」。关闭不需要确认，直接写。
 */
private fun SettingsPage.toggleMaster(ui: HomeUiState) {
    val on = !configState.config.value.global.enabled
    if (on && !configState.config.value.disclaimerAccepted) {
        XLog.w("免责声明：还没确认过，弹窗（总闸先不打开）")
        ui.disclaimerPending = true
        return
    }
    setModuleEnabled(on)
}

/**
 * 卡的上半段：大字状态 / 版本 / 一句说明。
 *
 * @param extras 未激活时的具体原因，以及「下面还有没有提示段」（决定下内边距：
 *   有提示段时它是「分隔线上面那一段」，收到 [ACTIVATION_HEAD_BOTTOM]；
 *   没有的话它就是整张卡的底，上下对称）。
 *
 *  抽出来只为让 [SettingsPage.HomeActivationCard] 留在 detekt 的 `LongMethod` 长度线内。
 */
@Composable
private fun SettingsPage.ActivationHead(
    state: ActivationState,
    version: String,
    palette: ActivationPalette,
    extras: ActivationExtras,
) {
    val titleStyle = COUITheme.textStyles.title4
    val lineStyle = COUITheme.textStyles.body2
    //  字色一律取这一档配色里给好的那两档（见 [ActivationPalette]）：
    //  · Monet 下有色相的两档：标题 = 强调色往内容色拉一成半（够深、但仍带色相）；
    //  · 非 Monet 下同样两档：直接用主题那两档正文色 —— 那一套是「固定中性底 + 预设强调色」，
    //    字再掺色就是浅灰卡上写一行蓝字（用户口径「非 monet 不要走 monet 配色」）；
    //  · 中性两档：本来就是主题那两档正文色 —— 没有色相可掺，再把大字稀释成半透明只会读不清。
    Column(
        Modifier.padding(
            start = ACTIVATION_CONTENT_PADDING,
            top = ACTIVATION_CONTENT_VERTICAL,
            end = ACTIVATION_CONTENT_PADDING,
            bottom = if (extras.hasTips) ACTIVATION_HEAD_BOTTOM else ACTIVATION_CONTENT_VERTICAL,
        ),
    ) {
        Text(
            text = state.label(),
            style = titleStyle,
            fontWeight = FontWeight.Bold,
            color = palette.title,
        )
        //  版本紧跟在状态字下面 —— 管理器那张就是这个位置（`2.2.1 (7912)`）。
        //  它原先挂在大标题底下（`PageTopBar` 那个槽位）：那一行只为首页存在，而首页要看
        //  「这个包是什么状态」正是这一块，两处各写一次版本迟早一处忘了改。
        //
        //  下面两行都补 [opticalCenter]：汉字墨迹在行盒里偏上，几何居中之后整行看着「浮起来」，
        //  与右上角那颗水印、与下面那几行提示都对不上中线（真机口径「说明的文字是偏上的」）。
        //
        //  彩色底上的说明**不直接用卡片行那两档**：`onSurfaceVariantSummary` /
        //  `onSurfaceContainerVariant` 在 COUI 里是同一个值（40% 黑，实测压在这块彩色底上只剩
        //  `#7E7F7F`，读起来「太亮」），`onSurfaceContainer` 又和标题一样黑、分不出主次 ——
        //  所以那两档的说明取「强调色往内容色拉一档」（色相跟着状态走、深度靠比例给），
        //  中性两档照旧用主题那支次要色（见 [neutralPalette]）。
        Text(
            text = version,
            style = lineStyle,
            color = palette.detail,
            modifier =
                Modifier
                    .padding(top = ACTIVATION_VERSION_GAP)
                    .opticalCenter(lineStyle),
        )
        Text(
            text = state.detail(extras.reason),
            style = lineStyle,
            color = palette.detail,
            modifier =
                Modifier
                    .padding(top = ACTIVATION_LINE_GAP)
                    .opticalCenter(lineStyle),
        )
        //  关着的那一档再补一句「怎么开回来」—— 这一块**整块就是开关**，可界面上没有任何记号
        //  说明这件事：大字只写「已关闭」，用户看到的是一个「关掉的状态」，不会想到去点它。
        //  点不动时不写（链路没就绪 / 只读态）：说「点按即可开启」却点不动，比不写更糟。
        //
        //  走 [VISIBILITY_ENTER] / [VISIBILITY_EXIT]（淡入 + 纵向展开 / 淡出 + 纵向收缩）：
        //  这一行的来去会让**整张卡**高一档，硬切就是卡一秒钟跳一下 —— 开关自己切状态时
        //  本来就有一条 200ms 的颜色过渡，高度这一下也得跟上同一条节奏。
        AnimatedVisibility(
            visible = state == ActivationState.Disabled && activationToggleable,
            enter = VISIBILITY_ENTER,
            exit = VISIBILITY_EXIT,
        ) {
            TapToEnableHint(lineStyle)
        }
    }
}

/**
 * 「点按卡片即可开启」—— 只在**关着且点得动**时出现的那一行。
 *
 *  颜色取主色、字重加半档：它是这一块里唯一一句「可以操作」的话，与下面提示段里那颗
 *  「查看」同一族的观感（其余几行都是陈述）。位置跟在说明后面 —— 先说「现在是关着的、
 *  意味着什么」，再说「怎么开回来」。
 */
@Composable
private fun TapToEnableHint(lineStyle: TextStyle) {
    Text(
        text = I18n.get(Strings.HOME_ACTIVATION_TAP_TO_ENABLE, "点击启用此处模块"),
        style = lineStyle,
        fontWeight = FontWeight.Medium,
        color = COUITheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(top = ACTIVATION_LINE_GAP)
                .opticalCenter(lineStyle),
    )
}

/**
 * 卡的下半段：原先首页顶上那两条提示条 —— **有内容才出现**（一条都没有时整段连同细线一起消失）。
 *
 *  顺序与原来那两条一致：「有新版本」（有东西可取）在前，「有一件事要处理」在后。
 *  后者的文案由 [statusTipText] 给，判据与「运行状态」同源（`linkFacts` + `linkChecks`）。
 *
 *  字色与卡片行同一套（标题常规色 / 说明次要色），细线也回到库那一档 —— 这一块与下面的卡片
 *  是同一种东西，配色不该只有它一套。
 *
 *  行首那颗图标取**这一档配色里给的那支**：Monet 的彩色底上是强调色压到七成，
 *  非 Monet 与中性底上都是主题那次要色 —— 它是注脚，不该比它说明的那句话更艳。
 */
@Composable
private fun SettingsPage.ActivationTips(
    release: ReleaseInfo?,
    status: String?,
    palette: ActivationPalette,
) {
    if (release == null && status == null) return
    Column {
        HorizontalDivider(Modifier.padding(horizontal = ACTIVATION_DIVIDER_INSET))
        Column(
            modifier =
                Modifier.padding(
                    start = ACTIVATION_CONTENT_PADDING,
                    top = ACTIVATION_TIPS_TOP,
                    end = ACTIVATION_CONTENT_PADDING,
                    bottom = ACTIVATION_TIPS_BOTTOM,
                ),
        ) {
            if (release != null) {
                ActivationTipRow(
                    icon = PageIcons.Download,
                    text = I18n.format(Strings.UPDATE_TIP, release.displayVersion),
                    palette = palette,
                    action = I18n.get(Strings.UPDATE_VIEW, "查看"),
                    // 与「发现新版本」那个弹窗走同一个入口（`UpdateState.reopen`）：点了不把这一行收掉，
                    //  收不收由 `UpdateState.pending` 管（窗里点「去下载」或勾「不再提示」才清）。
                    onAction = UpdateState::reopen,
                )
            }
            if (release != null && status != null) Spacer(Modifier.height(ACTIVATION_TIP_GAP))
            if (status != null) {
                //  **没有动作按钮**：这一条能做的只有重启手机 / 重启服务 / 照着「运行状态」去修，
                //  都不是点一下 UI 就能完成的事 —— 给一颗假的「查看」只会让人以为点进去就修好了。
                ActivationTipRow(icon = PageIcons.HomeStatusAlert, text = status, palette = palette)
            }
        }
    }
}

/**
 * 提示那一段里的一行：图标 + 一句 + （可选）行尾那颗动作。
 *
 * @param palette 这一档的配色 —— 行首那颗图标取它给的那支
 * @param action 行尾动作的字；`null` = 没有动作，正文独占整行
 * @param onAction 点击动作；与 [action] 必须同时给
 */
@Composable
private fun ActivationTipRow(
    icon: ImageVector,
    text: String,
    palette: ActivationPalette,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    //  振动走页面那条通用通道（[LocalTick]）—— 与卡片行、行尾动作同一份判据
    val tick = LocalTick.current
    val style = COUITheme.textStyles.body2
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = palette.icon,
            modifier = Modifier.size(ACTIVATION_TIP_ICON),
        )
        Spacer(Modifier.width(ACTIVATION_TIP_ICON_GAP))
        //  文字补 [opticalCenter]：汉字墨迹在行盒里偏上，与旁边那颗图标「几何居中」之后看着偏高
        //  （与上面那两句说明是同一件事、同一条修法）。
        Text(
            text = text,
            style = style,
            color = COUITheme.colorScheme.onSurfaceContainer,
            modifier = Modifier.weight(1f).opticalCenter(style),
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.width(ACTIVATION_TIP_ICON_GAP))
            //  **高度钉在图标那一档、只留横向内边距**：给纵向 padding 会把这一行撑高（那一行本来
            //  只有一行字），整段的下边距就跟着变多（真机口径「只有新版本说明时下边距又偏多了」）。
            //  点击区窄一点不要紧：整块卡本身也是可点的。
            Box(
                modifier =
                    Modifier
                        .clip(ACTIVATION_ACTION_SHAPE)
                        .clickable {
                            tick()
                            onAction()
                        }.height(ACTIVATION_TIP_ICON)
                        .padding(horizontal = ACTIVATION_ACTION_TAP),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = action,
                    style = style,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.primary,
                    modifier = Modifier.opticalCenter(style),
                )
            }
        }
    }
}
