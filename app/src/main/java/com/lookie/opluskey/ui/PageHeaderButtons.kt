package com.lookie.opluskey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.HEADER_ICON_SIZE
import com.lookie.opluskey.ui.model.PageIcons
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import io.github.suqi8.coui.kmp.utils.TiltFeedback
import io.github.suqi8.coui.kmp.utils.pressable

// 顶栏上那几颗圆键 —— 返回 / ⋮ / 添加 / 新建 / 重置 / 设置全是同一颗（[HeaderIconButton]），
// 外加它们共用的「按一下振一下」。单独一个文件：`PageHeaderWidget.kt` 已经到了 detekt 的
// `TooManyFunctions` 上限，而这几件事确实是一件事（顶栏上的圆键）。

/**
 * 顶栏两侧按钮共用的「按一下振一下」—— 挂在两个槽位的**容器**上，一次管住这一页所有顶栏按钮。
 *
 *  为什么不是逐个按钮加：顶栏按钮有六七处（返回 / ⋮ / 添加 / 新建 / 重置 / 设置），
 *  漏一处就是「这一颗按下去没手感」，而它们长得一模一样，用户分不出哪颗有哪颗没有；
 *  挂在容器上之后，**新加一颗顶栏按钮自动带振动**，调用点只写自己的 onClick。
 *
 *  不消费事件、不改排版：只在按下那一刻振一下，点击照旧由里面的按钮自己处理
 *  （`Main` 那一趟里子节点先拿到事件，这里既不会抢走点击、也不会重复触发）。
 *  代价是**置灰的按钮也会振一下** —— 那颗按钮此刻确实被按到了，只是不做任何事。
 */
internal fun Modifier.headerPressTick(tick: () -> Unit): Modifier =
    pointerInput(tick) {
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false)
                tick()
                // 等这一下收场再收下一按下：拖动 / 取消都不额外振
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
            }
        }
    }

/**
 * 顶栏圆键**描边**的不透明度系数 —— 乘在 `dividerLine` 自己那一档 alpha 上。
 *
 *  0.3：压在磨砂顶栏上只勾出「这儿有个圆」，不至于读成一枚端着的按钮（用户口径「边框再降
 *  亮度」）。要再淡 / 再亮都只动这一个数，别去换颜色角色 —— 换角色会把 Monet 那一档的
 *  `outlineVariant` 也一起换掉。
 */
private const val HEADER_BORDER_ALPHA = 0.3f

/**
 * 顶栏两颗圆键之间的间距 —— 两个圆不贴在一起（首页那排、选择应用页那两颗、按键页编辑态那两颗
 * 共用这一档；各页各写一个数迟早会差出来）。挂在**左边那颗**后面。
 */
internal val HEADER_ACTION_GAP = 8.dp

/**
 * 顶栏上的一颗圆键 —— 返回 / ⋮ / 添加 / 新建 / 重置 / 设置全走这一份。
 *
 *  顶栏底下是**磨砂**（半透的页面底色 + 模糊），纯透明的圆键压在上面只剩一个字形，看不出
 *  「这里按得动」；所以垫一层**纯色圆底 + 一圈细描边**。
 *
 *  ## 配色只认角色，不判主题
 *
 *  底色 `surfaceContainer`（不是 `surfaceContainerHigh`：高一档压在磨砂顶栏上偏亮，深色下尤其
 *  刺眼），描边 `dividerLine`（分隔线那一档，比 `outline` 弱一级 —— 顶栏上要的只是「这里有一块
 *  可按的地方」，不是一枚端着的按钮）。这两个角色在 Monet 那一档就是 MD3 的
 *  `surfaceContainer` / `outlineVariant`（跟着壁纸走），其余主题档是 COUI 标定过的那两档 ——
 *  所以这里**不需要**按 `LocalAppTheme` 分支，换主题时颜色自己跟着换。
 *
 *  禁用时退到 `disabledSecondaryVariant`（被冲淡的那一档容器色）：灰着的键不该还亮着
 *  （顶栏的「重置」有这一档，见 `CommandHeaderReset`）。
 *
 *  ## 禁用样式是**这一层的能力**（圆底 + 描边 + 字形三件）
 *
 *  这个圆是本项目自己画的（库的 `IconButton` 那层底色置透明），所以「灰」也得自己画全：
 *  只灰底与描边、字形还亮着，看着像「这颗还能按，只是底色淡了」。
 *  而字形是调用点传进来的（`content` 里那颗 `Icon`），默认 tint 取库的 `LocalContentColor`
 *  —— 于是这里把内容色按 `enabled` 两档递下去，**调用点仍然只写 `Icon(图标)`**，
 *  不必逐个记着判启用状态、也不必各自挑一个灰。
 *
 *  能用那一档取**环境里当前那一刻的值**（不是写死某个色）：顶栏上这几颗原本是什么色就是什么色，
 *  这一层只负责在停用时把它换成 [COUITheme.colorScheme] 的 `disabledOnSecondaryVariant`
 *  —— 与行首图标禁用时同一档（见 `rememberRowIconColor`），全项目一处口径。
 *
 * @param enabled 与按钮自己的可用性同一判据 —— 圆底 / 描边 / 字形三样都跟着它走；
 *   点不点得动由 `onClick` 那边定
 * @param content 图标本体（`Icon` 自带 `contentDescription`，不在这一层传）
 */
@Composable
internal fun HeaderIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    //  圆底与描边随 `enabled` 两档（顶栏的「重置」那颗会在编辑途中变灰 / 变亮），带过渡 ——
    //  页里其它禁用来回都走同一条曲线（[COLOR_TRANSITION_SPEC]），顶栏这一颗不能「啪」一下。
    val fill by animateColorAsState(
        targetValue = if (enabled) scheme.surfaceContainer else scheme.disabledSecondaryVariant,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "HeaderButtonFill",
    )
    //  描边：整档 `dividerLine` 压在磨砂顶栏上仍会勾出一圈清楚的轮廓，再压到三成一档就只剩
    //  「圆有个边界」（用户口径「边框再淡」）。按当前档的 alpha 折算，Monet 与 COUI 那两套
    //  都跟着淡下去；嫌淡 / 嫌亮都只改 [HEADER_BORDER_ALPHA] 这一个数。
    val border by animateColorAsState(
        targetValue =
            if (enabled) {
                scheme.dividerLine.copy(alpha = scheme.dividerLine.alpha * HEADER_BORDER_ALPHA)
            } else {
                scheme.disabledSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "HeaderButtonBorder",
    )
    //  按下去跟着触点轻微倾斜 —— 与首页那张「模块状态」卡同一档手感（库的 `TiltFeedback`）：
    //  卡片那边是 `pressFeedbackType = Tilt` 挂的，圆键这颗库没给这个参数，所以自己挂一层。
    //  `pressable` 只做「按下 / 抬起」与那层指示，**不带 onClick、也不消费事件** ——
    //  点击仍旧交给里面那颗库按钮（含它那条「禁用时给提示」的路）。
    //  `delay = null` 与卡片一致：按下即倾，不等那一档长按判定。
    val tiltSource = remember { MutableInteractionSource() }
    //  字形颜色（第三件禁用样式）—— 见上面那段「禁用样式是这一层的能力」。
    //  能用的那一档读**环境值**：这几颗圆键原本吃的是页面给的内容色，写死会改变现有观感。
    val ambientIcon = LocalContentColor.current
    val iconColor by animateColorAsState(
        targetValue = if (enabled) ambientIcon else scheme.disabledOnSecondaryVariant,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "HeaderButtonIcon",
    )
    //  **圆底与描边必须用 `CircleShape` 自己画**：库的 `IconButton` 走的是 squircle（超椭圆），
    //  圆角取边长一半时也只是「很圆的方」，四角仍比正圆鼓一点（用户口径「感觉这个按钮不是圆的」）。
    //  所以库那层底色置透明，圆底 / 描边 / 裁剪全在这里做。
    Box(
        modifier
            //  挂在底色**外面**：倾斜的是整颗圆（圆底 + 描边 + 字形），不是只有里面那支字
            .pressable(interactionSource = tiltSource, indication = TiltFeedback(), delay = null)
            .background(color = fill, shape = CircleShape)
            .border(width = 1.dp, color = border, shape = CircleShape)
            //  最后这一档裁子级：按压涟漪是画在按钮节点上的，不裁就会从圆的四角溢出去
            .clip(CircleShape),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            backgroundColor = Color.Transparent,
            minWidth = HEADER_ICON_SIZE,
            minHeight = HEADER_ICON_SIZE,
            cornerRadius = HEADER_ICON_SIZE / 2,
        ) {
            //  字形跟着圆底一起灰（见上面那段说明）：`Icon` 的默认 tint 就是这一位。
            CompositionLocalProvider(LocalContentColor provides iconColor) { content() }
        }
    }
}

/**
 * 顶栏左侧那颗「返回」—— 退一页；已经在首页就关掉整页。
 *
 *  **末端那 8dp 是给折叠后的标题让位**：库把折叠标题放在「导航图标宽 + 4dp」处
 *  （`TopAppBarDefaults.NavigationIconGap`），那颗圆键收进栏里之后离标题只有 4dp，挤成一团；
 *  把这 8dp 加在**图标槽内部**，圆键本身距屏幕边缘仍是 `PAGE_MARGIN`（与展开的大标题、与卡片
 *  同一条线），只有折叠标题的起点右移。
 *
 *  顺带把这一格的**外框宽度**报出去（[onWidth]）：折叠态副标题要按它算可用宽度
 *  （见 `HeaderSubtitleBar`）。报的是外框、含末端那 8dp —— 正好是库算 `collapsedTitleX` 用的那个数。
 */
@Composable
internal fun SettingsPage.HeaderBackIcon(
    tick: () -> Unit,
    onWidth: (Int) -> Unit,
) {
    Box(
        Modifier
            .headerPressTick(tick)
            .onSizeChanged { onWidth(it.width) }
            .padding(end = 8.dp),
    ) {
        HeaderIconButton(
            onClick = {
                if (!goBack()) {
                    XLog.w("顶栏返回：已在首页，关页面")
                    closeOverlays("顶栏返回关页面")
                    activity?.finish()
                }
            },
        ) {
            Icon(PageIcons.Back, contentDescription = I18n.get(Strings.COMMON_BACK, "返回"))
        }
    }
}

/**
 * 顶栏右侧那颗 ⋮ ——「选择应用」与「重置」两处菜单的触发键**逐字相同**（同一颗图标、同一个
 * 按压槽位、浮层都锚在按下的那一点），收在这里一处。
 */
@Composable
internal fun SettingsPage.HeaderMenuButton(
    preciseClick: PreciseClickState,
    onOpen: () -> Unit,
) {
    HeaderIconButton(
        onClick = onOpen,
        modifier = Modifier.preciseClickAnchor(preciseClick),
    ) {
        Icon(PageIcons.Menu, contentDescription = I18n.get(Strings.COMMON_MENU, "菜单"))
    }
}
