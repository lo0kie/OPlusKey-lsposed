package com.lookie.opluskey.ui.screen.binding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.ui.NoHapticFeedback
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.TILE_HEIGHT
import com.lookie.opluskey.ui.TILE_SIDE_PADDING
import com.lookie.opluskey.ui.TileFocusRing
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.ROW_ICON_SIZE
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT
import com.lookie.opluskey.ui.opticalTextCenter
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.PressFeedbackType

/**
 * 行尾那颗指示器的边长 —— 与原先那颗 COUI `RadioButton` 的 `requiredSize(24.dp)` 同尺寸。
 *
 * 三种指示器在同一位置三选一（见 [ActionTile] 的 `endActions`：两种入口字形与单选圈），
 * 尺寸不一致会让格子里的内容跟着挪。
 */
internal val ENTRY_INDICATOR_SIZE = ROW_END_ICON_SIZE

/**
 * 动作网格里的一格。
 *
 * @param enabled 首页那个「按键绑定」主开关为关时传 false —— 整格置灰、不吃点击
 */
@Composable
internal fun SettingsPage.ActionTile(
    spec: ActionCatalog.Spec,
    icon: ImageVector?,
    selected: Boolean,
    focused: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * 标题下面那行**小字**（空则不画）：内置的「打开应用」格放绑定的应用名、
     * 入口格（系统导航 / 系统按键 / 响铃状态 / 媒体控制）放当前档位，自定义命令那些格子放**命令间隔时长**。
     */
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    // 这一档是三态（禁用 / 选中 / 常规）切换，**带过渡** —— 与下面 [tileMutedColor]、
    // 外面那圈 [TileFocusRing]、以及页里其他状态色同一组参数。它同时是 `Card` 的
    // `contentColor`（标题 / 图标那一整片都吃它）⇒ 一次过渡整格同步。
    val contentColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> scheme.disabledOnSecondaryVariant
                selected -> scheme.onTertiaryContainer
                else -> scheme.onSurfaceContainer
            },
        label = "TileContent",
    )
    // 行尾箭头与选中小字共用的那档「弱化色」，可用/禁用切换带过渡
    val tileMutedColor by animateColorAsState(
        targetValue = if (enabled) scheme.onSurfaceVariantSummary else contentColor,
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
        label = "TileEntryIndicator",
    )

    // 外层 `Box` 现在**只为焦点圈**留一个浮层位置（见 [TileFocusRing]）——
    // 长按不再自己写了：库的**交互式 `Card`** 自带 `onLongPress`
    // （v1.1.0 `Card.kt` 第二个重载，内部就是 `Modifier.combinedClickable`，
    // 长按之后那一下抬手不会再触发点击，两个手势互不抢）。
    //
    //  主开关为关时**两个手势一起停**（下面把两个回调都置 null ⇒
    // 库的 `isClickable` 为 false，整个 `Card` 不可点）。
    Box(modifier.height(TILE_HEIGHT)) {
        // 长按的振动**只走通用通道**（[rememberTick] / [tickNow]）：库的 `combinedClickable`
        // 默认还会自己振一次 `HapticFeedbackType.LongPress` —— 那条路不看系统静音。
        // 把这一格的局部触觉反馈换成空实现（见 [NoHapticFeedback]）。
        CompositionLocalProvider(LocalHapticFeedback provides NoHapticFeedback) {
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
                colors =
                    CardDefaults.defaultColors(
                        color = scheme.surfaceContainer,
                        contentColor = contentColor,
                        pressedColor = scheme.surfaceContainerHigh,
                    ),
                // 按压反馈**只要涟漪**：`showIndication` 让 `combinedClickable` 用
                // `LocalIndication`，与 `BasicComponent` 那个 `clickable` 同一份，
                // 视觉与换之前逐像素一致。`Tint`（底色渐变到 `pressedColor`）显式关掉 ——
                pressFeedbackType = PressFeedbackType.None,
                showIndication = true,
                onClick = if (enabled) onClick else null,
                onLongPress = if (enabled) onLongClick else null,
            ) {
                BasicComponent(
                    //  **不给 `onClick`**：行内容自己那个 `clickable` 会在 Main 趟
                    // 把事件消费掉，外层 `Card` 的 `combinedClickable` 就永远等不到
                    // 轻点 / 长按 / 涟漪**全部**由外层 `Card` 负责。
                    startAction = {
                        // 图标列只有这一枚 22dp（尺寸沿用全页的 22dp 纪律，见 ActionIcons 的注释）。
                        //  入口格的行首随档位变，见网格里 `icon = boundMode ?: spec` 那一句。
                        icon?.let { Icon(it, null, Modifier.size(ROW_ICON_SIZE)) }
                    },
                    endActions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            //  入口格不再画单选圈（那读作「这一格是一个可选项」），按**点开去哪**分两种字形：
                            //  · 弹一层菜单（系统导航 / 系统按键 / 媒体控制 / 响铃状态）→ 选择器字形
                            //    （[PageIcons.Selector]：一个框 + 右下角小三角，读作「点了要选点什么」）；
                            //  · 进另一个页面（「打开应用」→ 挑应用）→「窗口 + 加号」。
                            //  尺寸与配色逐档对齐（[ENTRY_INDICATOR_SIZE] / [entryIndicatorColor]），
                            // 换掉之后占位与观感不变。
                            when {
                                spec.token == ActionCatalog.APP_TILE -> {
                                    Icon(
                                        PageIcons.BrowserPlus,
                                        contentDescription = null,
                                        modifier = Modifier.size(ENTRY_INDICATOR_SIZE),
                                        tint = entryIndicatorColor,
                                    )
                                }

                                ActionCatalog.modeMenu(spec.token) != null -> {
                                    Icon(
                                        PageIcons.Selector,
                                        contentDescription = null,
                                        modifier = Modifier.size(ENTRY_INDICATOR_SIZE),
                                        tint = entryIndicatorColor,
                                    )
                                }

                                //  取值格（不是入口格）的选中指示 —— 空心方框 / 带勾方框，见 [PageIcons.TileUnchecked]。
                                //  形状与另两种字形同尺寸，不给触达区（点击交给整行）。
                                else -> {
                                    Icon(
                                        if (selected) PageIcons.TileChecked else PageIcons.TileUnchecked,
                                        contentDescription = null,
                                        modifier = Modifier.size(ENTRY_INDICATOR_SIZE),
                                        tint = entryIndicatorColor,
                                    )
                                }
                            }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = spec.label,
                            style = COUITheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 账见 [opticalTextCenter]：文字在 52dp 的格里居中，汉字墨迹却比盒子中心
                            //  高一档（行盒给基线以下留的 descent 中文不占），沉 0.1em 才落在正中。
                            //  `weight(fill = false)`：标题只占自己要的那点宽度、装不下才缩到
                            //  省略号（标题右边不再有标记，这个权重仍按「只占自己那点」算）。
                            modifier =
                                Modifier
                                    .weight(1f, fill = false)
                                    .opticalTextCenter(COUITheme.textStyles.body2.fontSize, FontWeight.Medium),
                        )
                    }
                    // 只写一行标题时看不出这一格绑的是什么（应用名 / 入口格的档位 / 命令间隔时长），
                    // 这行就是那个值。格子高 52dp、BasicComponent 最小高 48dp：两行（14sp + 12sp）装得下。
                    //  注意：这里**不能**用 [AnimatedFade]（只淡、不动高度）：那样这行字的
                    //  **布局高度是立刻占上的**，上面那行标题会**瞬间**被顶上去 ——
                    //  而要的正是「标题给这行字让位 / 复位」这个**位移本身滑出来**。
                    //  所以走 [VISIBILITY_ENTER] / [VISIBILITY_EXIT]（淡入 + 竖向展开，
                    //  与页里其他显隐同一套规格）：高度从 0 长到满，标题随之平滑上移；
                    //  收起时反向。两行（14sp + 12sp）在 52dp 的格子里装得下，不会溢出。
                    //  `MutableTransitionState(false)` 起手：首次组合也走一遍展开 ——
                    //  `AnimatedVisibility` 直接吃 `visible` 时，初始为 true 不播入场，
                    //  进页面第一眼就会是「啪」地占好位。
                    val subtitleShown = remember { MutableTransitionState(false) }
                    subtitleShown.targetState = !subtitle.isNullOrBlank()
                    AnimatedVisibility(
                        visibleState = subtitleShown,
                        enter = VISIBILITY_ENTER,
                        exit = VISIBILITY_EXIT,
                    ) {
                        Text(
                            text = subtitle.orEmpty(),
                            style = COUITheme.textStyles.footnote1,
                            color = tileMutedColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 与标题同一套视觉居中：按**各自字号**取同一比例（13sp 与 14sp 差 0.1dp，
                            //  行距基本不变），两行一起落在格的视觉中心
                            modifier = Modifier.opticalTextCenter(COUITheme.textStyles.footnote1),
                        )
                    }
                }
            }
        }
        // 禁用时不画焦点圈（`focused` 可能还是旧值，但整格已经不吃输入了）
        TileFocusRing(focused && enabled)
    }
}

// TileFocusRing 已搬到 ui/TileWidgets.kt（与 ActionTile 共用 TILE_HEIGHT / TILE_SIDE_PADDING / 焦点圈逻辑）。
