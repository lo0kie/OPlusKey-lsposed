package com.lookie.opluskey.ui.screen.binding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lookie.opluskey.core.present.ActionCatalog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.ROW_ICON_SIZE
import com.lookie.opluskey.ui.model.page.opticalCenter
import com.lookie.opluskey.ui.model.theme.AppTheme
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.theme.VISIBILITY_ENTER
import com.lookie.opluskey.ui.model.theme.VISIBILITY_EXIT
import com.lookie.opluskey.ui.row.TILE_HEIGHT
import com.lookie.opluskey.ui.row.TILE_SIDE_PADDING
import com.lookie.opluskey.ui.row.TileFocusRing
import com.lookie.opluskey.ui.row.rememberRowIconColor
import com.lookie.opluskey.ui.style.AnimatedFade
import com.lookie.opluskey.ui.style.LocalAppTheme
import com.lookie.opluskey.ui.style.appCardColors
import io.github.suqi8.coui.kmp.basic.Badge
import io.github.suqi8.coui.kmp.basic.BadgeColors
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.PressFeedbackType

/**
 * 网格格子行尾那颗指示器的边长 —— 取 20dp：比行首图标（[ROW_ICON_SIZE] 22dp）再小一档。
 *
 *  格子只有一格宽，行尾那颗是**提示**（「点了还有一层」/「选没选」/「是不是它」），不是内容：与行首同高时
 *  它会和标题抢横向空间（真机反馈「格子行尾的图标偏大」）。行尾那几种指示器（箭头 / 选择器 /
 *  勾选框 / 选中圆点）在同一位置四选一，必须同尺寸，否则格子里的内容会跟着挪。
 *
 *  **不并进 [com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE]（24dp）**：那一档是行尾箭头 /
 *  选中圈的通用尺寸（首页、选择应用、关于页都用它），而那些行的可用宽度比两列的格子宽得多 ——
 *  这一档只属于网格。
 */
internal val ENTRY_INDICATOR_SIZE = 20.dp

/**
 * 格子内容里**文字到行尾那颗指示器的距离** —— 4dp。
 *
 *  行尾这一段必须是我们说了算：交给 `endActions` 槽位的话，间距由库内部决定（库里没写、
 *  文档也没有），而这一格只有一格宽，改不动就永远调不紧。行首图标仍走 `startAction`，
 *  那一段的间距与这一格原来的观感一致、不动。
 */
private val TRAILING_INDICATOR_GAP = 4.dp

/**
 * Monet 档里「格子小字」那行强调色的透明度 —— 取 0.70。
 *
 *  选中格的标题是实色 `primary`，小字若也取实色就与标题同色（真机反馈「小标题和主标题在
 *  monet 下一样了」）。七成不透明度既留住「有色」，又比标题淡一级 —— 与 COUI 那几档弱化色
 *  （`0x66` = 40%、`0x8A` = 54%）比它更亮，因为它压的是「同一色族的标题」，不是中性底。
 */
private const val MONET_MUTED_ALPHA = 0.70f

/**
 * 动作网格里的一格。
 *
 * @param enabled 首页那个「按键绑定」主开关为关时传 false —— 整格置灰、不吃点击
 */
@Composable
internal fun SettingsPage.ActionTile(
    spec: ActionCatalog.Spec?,
    icon: ImageVector?,
    selected: Boolean,
    focused: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * 标题那一行 —— 默认取 [spec] 的名字。
     *
     *  有调用方给得出「格子」但给不出 `Spec`（「常用命令」那一段的条目是模块自带清单，
     *  不在 `ActionCatalog.ALL` 里），于是这一格改成可传名字 / 可传行尾那一档：
     *  `spec = null` + [title] + [trailingDot] 就能复用同一套卡片，不必再拼第二份。
     */
    title: String = spec?.label.orEmpty(),
    /**
     * 标题下面那行**小字**（空则不画）：内置的「打开应用」格放绑定的应用名、
     * 入口格（系统导航 / 系统按键 / 响铃状态 / 媒体控制）放当前档位，自定义命令那些格子放**命令间隔时长**。
     */
    subtitle: String? = null,
    /**
     * 行尾改画一枚**选中圆点**（默认按 [spec] 的 `clickKind` 画那三种字形）。
     *
     *  「常用命令」那一段的条目是模块自带清单、**不在 `ActionCatalog.ALL` 里**（查不出
     *  `clickKind`，给不出 `Spec`），而这一格要说的只是「就是它」—— 圆点的位置、尺寸、
     *  间距仍由这一格自己排，调用方只给这一位。
     */
    trailingDot: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    // 这一档是三态（禁用 / 选中 / 常规）切换，**带过渡** —— 与下面 [tileMutedColor]、
    // 外面那圈 [TileFocusRing]、以及页里其他状态色同一组参数。它同时是 `Card` 的
    // `contentColor`（标题 / 图标那一整片都吃它）⇒ 一次过渡整格同步。
    //
    //  选中那一档取 `primary`（强调色），与行尾指示字形、焦点圈同源 —— 全页「选中」只有这一个色。
    //  **不能用 `onTertiaryContainer`**：那是压在三段容器（`tertiaryContainer`）底上的前景色，
    //  而这张卡的底始终是 `surfaceContainer`，两者不是一对；Monet 那一档它还是三级色相
    //  （与主色不是一个色族），选中格的标题会变成别的颜色（真机反馈「选中后标题取色不对」）。
    val contentColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> scheme.disabledOnSecondaryVariant
                selected -> scheme.primary
                else -> scheme.onSurfaceContainer
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "TileContent",
    )
    //  标题下面那行**小字**的配色（应用名 / 入口格档位 / 命令间隔），可用/禁用切换带过渡。
    //
    //  **Monet 那一档、且这一格是选中的，才换用强调色**：主题选 Monet 时 COUI 把
    //  `onSurfaceVariantSummary` 映射到 MD3 的 `onSurfaceVariant`（见库里 `MonetMapping.kt`）——
    //  那是一档「近乎无彩」的灰，整页底色、卡片、边线都被壁纸染过了，只有这行字还是灰的
    //  （真机反馈「格子的小标题没有 monet 配色」）。
    //  **但未选中的格子不能用它**：整片网格里每格的小字都亮着强调色，「强调色」就不再表示
    //  「这一格绑着当前手势」—— 一屏十几格全在喊，选中那格反而看不出来。所以只有选中格取
    //  70% 的强调色，其余仍是 COUI 的 `onSurfaceVariantSummary`。
    //  **选中格还要比标题弱一档**：标题本身已经是 `primary`，小字若也取实色，两行会一模一样
    //  （真机反馈「小标题和主标题在 monet 下一样了」）—— 同一色族、淡一级，主次仍在。
    //  非 Monet 那几档一律 `onSurfaceVariantSummary`（`0x66000000` / `0x66FFFFFF`）：那一档是压在
    //  中性底子上标定出来的弱化色，换成强调色会在浅色底上糊成一片蓝。
    val mutedBase =
        if (LocalAppTheme.current == AppTheme.MONET && selected) {
            scheme.primary.copy(alpha = MONET_MUTED_ALPHA)
        } else {
            scheme.onSurfaceVariantSummary
        }
    val tileMutedColor by animateColorAsState(
        targetValue = if (enabled) mutedBase else contentColor,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "TileMuted",
    )
    // 行尾那颗指示字形（选择器 / 勾选框 / 窗口 + 加号）的配色。
    //
    //  可用那两档照 COUI 的 `RadioButtonDefaults.radioButtonColors()`（选中 `primary`、未选中
    //  `onSurfaceVariantActions`）。**禁用时不再分选中**：原先选中的那一格用 `disabledPrimary`，
    //  那是一档带主题色的灰蓝 —— 整片网格都灰了，只有它还在「亮着选中的味道」，读起来像
    //  「这一格依旧被选中」，而这一档真正要说的是「这颗键现在不接管」（真机反馈）。
    //  勾选框的**形状**仍在，看得到绑的是哪一格；只是不再用主题色喊。
    val entryIndicatorColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> scheme.disabledSecondaryVariant
                selected -> scheme.primary
                else -> scheme.onSurfaceVariantActions
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "TileEntryIndicator",
    )
    //  行首那颗图标：整格的内容色（启用 / 选中 / 禁用三档）与「随机图标配色」那一档合成一个目标。
    //
    //  **随机配色必须也走这里**：它换的是整屏格子的图标色（用户一拨开关，几十颗一起变），
    //  直接拿 `tinted()` 的结果去画就是整片硬切 —— 硬切在这页最扎眼，比任何一档状态色都明显。
    //  三档语义色仍优先：灰 / 主色是这一格「能不能点、选没选中」的说法，随机色只管剩下的常规态。
    //
    //  三档顺序（禁用 > 选中 > 随机色）与卡片行那一处是同一套，所以直接用
    //  [rememberRowIconColor] —— 两处各写一遍时，改一档就漏一档。
    val iconTint by
        rememberRowIconColor(
            enabled = enabled,
            icon = icon,
            selected = selected,
            label = "TileIcon",
        )

    // 外层 `Box` **只为焦点圈**留一个浮层位置（见 [TileFocusRing]）。
    //
    //  这一格只有轻点一个手势：长按不接（库的 `Card` 在 `onLongPress` 为 null 时走
    //  `Modifier.clickable`，不是 `combinedClickable`）。自定义命令那条「进编辑」因此只剩
    //  「已选中时再点一次」一个入口 —— 见网格里 `CustomTile` 的 `onClick`。
    //
    //  主开关为关时 `onClick` 置 null ⇒ 库的 `isClickable` 为 false，整个 `Card` 不可点。
    Box(modifier.height(TILE_HEIGHT)) {
        Card(
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    //  **必须自己补一个焦点目标**（真机定位）：
                    // 这个版本的 `Modifier.combinedClickable` / `clickable` **不带 focusable** ——
                    // `androidx.compose.foundation.ClickableKt` 里对 `FocusableKt`
                    // 的引用数为 **0**，`AbstractClickableNode` 也只实现
                    // `KeyInputModifierNode`、不是 `FocusTargetModifierNode`。
                    // 后果：格子里没有 FocusTarget ⇒ `FocusRequester.requestFocus()` 找不到目标、
                    // ⇒ 按键事件只发给根节点（`FocusOwnerImpl.dispatchKeyEvent` 在
                    // `findFocusTargetNode()` 为 null 时只走根节点的 key 节点）
                    // ⇒ [BindingList] 那个 `onPreviewKeyEvent` 一次都不会被调用。
                    // 顺序不能反：`focusRequester` 必须在这个焦点目标**之前**（链上更靠外）。
                    .focusTarget(),
            //  三档底色与命令卡、卡片列表同一份（见 [appCardColors]）；这一格的内容色是动画值
            colors = appCardColors(contentColor = contentColor),
            // 按压反馈**只要涟漪**：`showIndication` 让 `clickable` 用
            // `LocalIndication`，与 `BasicComponent` 那个 `clickable` 同一份，
            // 视觉与换之前逐像素一致。`Tint`（底色渐变到 `pressedColor`）显式关掉 ——
            pressFeedbackType = PressFeedbackType.None,
            showIndication = true,
            onClick = if (enabled) onClick else null,
        ) {
            BasicComponent(
                //  **不给 `onClick`**：行内容自己那个 `clickable` 会在 Main 趟
                // 把事件消费掉，外层 `Card` 的 `clickable` 就永远等不到 ——
                // 轻点 / 涟漪**全部**由外层 `Card` 负责。
                //
                //  **行首图标仍走 `startAction`**（间距由库内部给，与这一格原来的观感一致）。
                //  **行尾不用 `endActions`**：那一段库里没写、也不随我们改，而这一格只有一格宽，
                //  行尾那颗离文字多远必须是我们说了算 —— 所以指示器挪进内容里，间距取
                //  [TRAILING_INDICATOR_GAP]。
                startAction = {
                    // 图标列只有这一枚 22dp（尺寸沿用全页的 22dp 纪律，见 ActionIcons 的注释）。
                    //  入口格的行首随档位变，见网格里 `icon = boundMode ?: spec` 那一句。
                    //  「随机图标配色」开着时这一格的行首图标用**它自己那一色**；
                    //  禁用 / 选中两档不上色（灰与主色各自带语义）—— 三档合出来的目标见 [iconTint]。
                    icon?.let {
                        Icon(
                            it,
                            null,
                            Modifier.size(ROW_ICON_SIZE),
                            tint = iconTint,
                        )
                    }
                },
                // 默认 16dp 的内边距在两列的格子里会把标题挤没（见类注释的宽度账）
                insideMargin = PaddingValues(horizontal = TILE_SIDE_PADDING),
                // 格子是独立的卡片，按 COUI 的「卡片组里唯一一行」处理（上下各补 2dp）
                cardListPosition = CardListPosition.Full,
                // `enabled` 只管这一行**内容**的置灰（标题 / 图标 / radio）；
                // 能不能点由外层 `Card` 的两个回调（上面按 `enabled` 置 null）决定
                enabled = enabled,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    //  标题与小字合成一列、占满中间那段 ⇒ 行尾那颗离文字的间距**恒为**
                    //  [TRAILING_INDICATOR_GAP]（原先交给 `endActions` 时，那一段由库决定、改不动）。
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = COUITheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.opticalCenter(COUITheme.textStyles.body2),
                        )
                        // 只写一行标题时看不出这一格绑的是什么（应用名 / 入口格的档位 / 命令间隔时长），
                        // 这行就是那个值。格子高 52dp、BasicComponent 最小高 48dp：两行（14sp + 12sp）装得下。
                        //  注意：这里**不能**用 [AnimatedFade]（只淡、不动高度）：那样这行字的
                        //  **布局高度是立刻占上的**，上面那行标题会**瞬间**被顶上去 ——
                        //  而要的正是「标题给这行字让位 / 复位」这个**位移本身滑出来**。
                        //  所以走 [VISIBILITY_ENTER] / [VISIBILITY_EXIT]（淡入 + 竖向展开，
                        //  与页里其他显隐同一套规格）：高度从 0 长到满，标题随之平滑上移；
                        //  收起时反向。两行（14sp + 12sp）在 52dp 的格子里装得下，不会溢出。
                        //  **起手就取当前这一档**（`!subtitle.isNullOrBlank()`），不写死 `false`：
                        //  写死的话「首帧 = 没展开」是每次**重新组合**都成立的 —— 翻 tab、从子页回来
                        //  都会把这一格新建一遍，那行小字于是每换一页就重播一次展开（高度 0 → 满，
                        //  标题跟着上下动一下）。取当前值之后，只有小字**真的出现 / 消失**时才播。
                        val subtitleShown = remember { MutableTransitionState(!subtitle.isNullOrBlank()) }
                        subtitleShown.targetState = !subtitle.isNullOrBlank()
                        //  字号 12sp：**主题里没有这一档** —— 小两档是 `footnote1` = 13sp、`footnote2` = 11sp，
                        //  中间空着。取 `footnote2` 只把字号提到 12sp（行高仍按它那档 1.143em 走，
                        //  12 × 1.143 ≈ 13.7dp）：11sp 的汉字在这个格子里偏糊，13sp 又太接近标题（14sp）。
                        //  这一行是补充值，与标题同档时两行争主次，格子一窄还先挤它。
                        val subtitleStyle = COUITheme.textStyles.footnote2.copy(fontSize = 12.sp)
                        AnimatedVisibility(
                            visibleState = subtitleShown,
                            enter = VISIBILITY_ENTER,
                            exit = VISIBILITY_EXIT,
                        ) {
                            Text(
                                text = subtitle.orEmpty(),
                                style = subtitleStyle,
                                color = tileMutedColor,
                                modifier = Modifier.opticalCenter(subtitleStyle),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // 与标题同一套视觉居中：按**各自字号**取同一比例（12sp 与 14sp 差不到 0.2dp，
                                //  行距基本不变），两行一起落在格的视觉中心
                            )
                        }
                    }
                    Spacer(Modifier.width(TRAILING_INDICATOR_GAP))
                    //  入口格不再画单选圈（那读作「这一格是一个可选项」），按**点开去哪**分两种字形：
                    //  · 弹一层菜单（系统导航 / 系统按键 / 媒体控制 / 响铃状态 / 应用控制）→ 选择器字形
                    //    （[PageIcons.Selector]：一个框 + 右下角小三角，读作「点了要选点什么」）；
                    //  · 进另一个页面（「自定义变量」→ 变量操作）→ **右箭头**（与菜单、列表那几处同一颗）。
                    //  · 第三种（`trailingDot`）是一枚实心圆点，只出现在「常用命令」那一段 ——
                    //    那里要说的是「这一格就是当前绑的」，不是「点了还有一层」。
                    //
                    //  **只问 `clickKind`**：这一格点下去干什么，清单里已经答了。这里再列一遍 token
                    //  的话，`APP_TILE`（弹原地菜单）就会被写成右箭头 —— 抽屉那边原先正是这么错的。
                    //  尺寸与配色逐档对齐（[ENTRY_INDICATOR_SIZE] / [entryIndicatorColor]），
                    // 换掉之后占位与观感不变。
                    if (trailingDot) {
                        //  「这一格就是当前绑的」那一枚圆点（「常用命令」那一段用它：那一段的条目
                        //  不在清单里，查不出 `clickKind`）。**恒占位**：不占位时选中 / 未选中会让
                        //  标题那列的可用宽度变、长名字跟着跳一下；圆点只在选中时淡入。
                        //
                        //  颜色取整格那个动画值 [contentColor]：选中 `primary`、禁用一档灰 ——
                        //  与标题同源、跟着同一段过渡走，禁用时不会剩下一个还亮着的主题色圆点。
                        Box(
                            modifier = Modifier.size(ENTRY_INDICATOR_SIZE),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedFade(visible = selected) {
                                Badge(
                                    colors =
                                        BadgeColors(
                                            containerColor = contentColor,
                                            contentColor = scheme.onPrimary,
                                            strokeColor = scheme.surfaceContainer,
                                        ),
                                )
                            }
                        }
                    } else {
                        //  不走圆点就必须给得出 `Spec`：两个参数是同一个契约的两半
                        //  （给不出 `Spec` 的调用方，正是那个传 `trailingDot = true` 的）。
                        when (ActionCatalog.clickKind(requireNotNull(spec))) {
                            //  换页的入口（「自定义变量」→ 变量操作、「应用控制」→ 挑应用）
                            //  ⇒ 行尾那颗是右箭头（与菜单、列表那几处同一颗）。
                            ActionCatalog.ClickKind.PAGE_APP,
                            ActionCatalog.ClickKind.PAGE_VARS,
                            -> {
                                Icon(
                                    PageIcons.EnterArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(ENTRY_INDICATOR_SIZE),
                                    tint = entryIndicatorColor,
                                )
                            }

                            //  其余档位那一层是**原地弹**（菜单），不跳页 —— 所以是选择器字形而不是箭头。
                            ActionCatalog.ClickKind.MENU -> {
                                Icon(
                                    PageIcons.Selector,
                                    contentDescription = null,
                                    modifier = Modifier.size(ENTRY_INDICATOR_SIZE),
                                    tint = entryIndicatorColor,
                                )
                            }

                            //  取值格（不是入口格）的选中指示 —— **只有选中时画那一枚带勾方框**，
                            //  未选中时那一格空着（原先画一个空心框，整片网格里每个没选的格子都挂着
                            //  一个空圈，看着像「待办清单」）。
                            //
                            //  **仍恒占位**：外面这层 `Box` 与字形同尺寸，不占位时选中 / 未选中会让
                            //  标题那列的可用宽度变、长名字跟着跳一下。
                            //
                            //  「录屏」那一格也走这里：它点开虽然弹弹窗，但**格子本身就是那个动作**
                            //  （三档只是它的状态），挂选择器字形等于告诉用户「下面还有一层清单」——
                            //  而那一层已经没有了（见 `ActionCatalog.clickKind` 的 `STATE` 档）。
                            else -> {
                                Box(
                                    modifier = Modifier.size(ENTRY_INDICATOR_SIZE),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    //  淡入淡出与旁边那片底色同源（[COLOR_FADE_SPEC]）
                                    AnimatedFade(visible = selected) {
                                        Icon(
                                            PageIcons.TileChecked,
                                            contentDescription = null,
                                            modifier = Modifier.size(ENTRY_INDICATOR_SIZE),
                                            tint = entryIndicatorColor,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 禁用时不画焦点圈（`focused` 可能还是旧值，但整格已经不吃输入了）
        TileFocusRing(focused && enabled)
    }
}

// TileFocusRing 已搬到 ui/row/TileWidgets.kt（与 ActionTile 共用 TILE_HEIGHT / TILE_SIDE_PADDING / 焦点圈逻辑）。
