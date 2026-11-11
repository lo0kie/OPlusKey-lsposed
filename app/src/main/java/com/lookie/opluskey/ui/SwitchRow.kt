package com.lookie.opluskey.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.ROW_START_ICON_GAP
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalDisabledClick
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import kotlinx.coroutines.delay

/**
 * 每颗开关**上一次落定的位置** —— 页面级一份，按行标题记（见 `PageContent` 的 provide）。
 *
 * ## 为什么需要它
 *
 *  写配置会让整页内容重新挂一遍（`PageContent` 读整份 `config` ⇒ 任何一次写都重组整页，
 *  库那边的 entry 内容跟着重新录制）。库的 `Switch` 在新组合里把那次当成「首次组合」，
 *  直接跳过动画（`Switch.kt` 的 `isFirstComposition`）—— 滑块与轨道于是停在终值，看起来是硬切。
 *
 *  把「上一个位置」记在**行的组合之外**：行被重建时先摆回那个位置（新组合因此是「旧态」），
 *  下一帧再把真值交回去，库就有一条真正的「旧 → 新」可以播。
 */
internal val LocalSwitchPositions = staticCompositionLocalOf<MutableMap<String, Boolean>> { mutableMapOf() }

/** 库 `Switch` 那两条动画（滑块 383ms / 轨道 450ms）落定所需的时间 —— 之后才算「用户看到的」。 */
private const val SWITCH_SETTLE_MS = 500L

/**
 * 单行开关（库 `SwitchPreference` 的项目包装）。
 *
 * 存在的理由是**行首图标必须自己上色**：库只给**它自己画的东西**按 `enabled` 上色，
 * `startAction` 槽位原样渲染 —— 不给的话 `enabled = false` 会「文字灰了、图标还亮着」
 * （在「长按连续触发」那行复现）。
 *
 * 这条规矩不许在各调用点各抄一遍 —— 抄漏一个就不一致。收进这里之后配色要变只改这一处 ——
 * 与 [SliderRow] 同一个道理，两张行挨在同一张卡里时也不会各用一种灰。
 *
 * 库 `Switch` 自己会无条件振一次，这里统一套 [NoSwitchHaptic] 掐掉，振动只走调用方的
 * `tick()` / `tickNow()` 那条通道。
 *
 * ## 副标题（[summaryOn] / [summaryOff]）的口径
 *
 *  **真假各一句，由这里按 `checked` 取**：开关的两个状态说的是两件事，一句描述不了 ——
 *  「关闭后此模块将停用」在已经关着的时候是错的（那时它已经关着了）。
 *
 *  · **精确**：说的是「现在这个状态下会发生什么」。「开启 / 关闭」用结果表达（走哪条接口、
 *    执行一次还是重复、弹不弹提示），不写内部机制（哪个窗口、哪条判据）；
 *  · **精简**：一行放得下（中文约 14 字），多一个字就折行、把整张卡撑高。
 *
 *  两句都由调用点给（文案走字典 `Strings.*_ON` / `*_OFF`），**取哪一句这件事只在这里做** ——
 *  各调用点自己写一遍 `if (checked)`，迟早有一处忘了分、变成「一句管两个状态」。
 *  置灰时那个「为什么点不了」的原因**优先于**这两句，由调用点写成 `reason ?: …` 的两份
 *  （见 `LongRepeatCard`）。
 *
 * @param icon 行首图标。同一张卡里几行都给图标，标题才会对齐（见 `ActionIcons` 里那条账）
 * @param summaryOn 开关**开着**时的副标题
 * @param summaryOff 开关**关着**时的副标题
 * @param enabled false ⇒ 整行置灰、不吃点击。标题 / 副标题 / 开关由库负责，**行首图标由这里负责**
 * @param position 卡片内的行位置（首行 / 中间 / 末行），决定圆角与分隔线
 *
 *  **没有 `SettingsPage.` 接收者**：体内只碰库件与 [NoSwitchHaptic] / [rememberRowIconColor]，
 *  都是顶层声明 —— 接收者一次都用不到。原先挂着它只是为了「与同目录那批页面组件写法一致」，
 *  而那正是「component/ 名不副实」的一部分：**通用控件不该背页面类型**（它会白白把
 *  `SettingsPage` 拉进每个使用者的依赖里）。这是这一批里第一个去掉接收者的，同目录其余
 *  真的用不到接收者的也照此办理。
 *  `@Suppress("LongParameterList")`：一行开关要的东西就是这些（标题 / 两句副标题 / 图标 / 状态 /
 *  行位置 / 是否可用 / 回调），**拆成一个参数对象只是把它们挪个地方**，调用点反而更罗嗦。
 *  原先这一条压在 detekt 基线里，副标题拆成两句时签名变了、那条失效 —— 就地改成注解，
 *  基线条目随之删掉（条目数只减不增）。
 */
@Suppress("LongParameterList")
@Composable
internal fun SwitchRow(
    title: String,
    summaryOn: String,
    summaryOff: String,
    icon: ImageVector,
    checked: Boolean,
    position: CardListPosition,
    enabled: Boolean = true,
    /** 拨这一下振不振 —— 默认振（与 `CardRow.Item.vibrate` 同一条口径）。 */
    vibrate: Boolean = true,
    /**
     * 行内那一块（标题 + 副标题）**整块往下挪多少** —— 光学居中用（`opticalBlockBias`）。
     *
     *  挪法不是「把行挪一点」，而是**上下内边距互相让**：上边 +这个值、下边 −这个值。
     *  内容下移同样多，行高与**可点区域一动不动** —— 波纹画的是可点区域，行一挪，它跟着挪，
     *  按住时波纹的上沿就与上一行让出一道缝（真机口径）。库不给标题插修饰符的口子，
     *  只有内边距这一个能调。
     */
    contentBias: Dp = 0.dp,
    onCheckedChange: (Boolean) -> Unit,
) {
    //  振动走页面那条通道（`LocalTick`）：这一层不带 `SettingsPage` 接收者。
    //  `NoSwitchHaptic` 已经把库自带的那一次关掉了 —— 那个不看系统静音，这里要看。
    val tick = LocalTick.current
    // 颜色带过渡，与 [SliderRow] 同一套参数：主开关一拨，这个图标与库里那些标题一起渐变。
    //  硬切会出现「图标在淡、字在跳」——长按那张卡里 SwitchRow 与 SliderRow 就是挨着的。
    val iconColor by rememberRowIconColor(enabled, icon = icon, label = "SwitchIcon")

    //  这一行画出来的那个位置由 [shown] 说了算：稳态下它就是 `checked`；只有「行刚被重建」的
    //  那一帧，它是页面级记下的**上一个位置**，随后再切回真值 —— 库的开关因此总有一条能播的
    //  「旧 → 新」（见 [LocalSwitchPositions] 与 [SWITCH_SETTLE_MS]）。
    val positions = LocalSwitchPositions.current
    //  **按 [title] 记一份**：这一格的组合槽位被别的行复用时（卡片里条件行增删），
    //  沿用上一行的位置会让开关首帧画成别人的值再回弹。
    //  同一页出现两行同名（多处「振动反馈」）时仍共用 [positions] 里那一格 —— 那要按行 key 记才根治，
    //  而 `LocalSwitchPositions` 现在只认标题。
    var shown by remember(title) { mutableStateOf(positions[title] ?: checked) }
    LaunchedEffect(checked) {
        if (shown == checked) return@LaunchedEffect
        positions[title] = shown
        shown = checked
        delay(SWITCH_SETTLE_MS)
        positions[title] = checked
    }

    //  置灰那一档**点一下要说话**：COUI 在 `enabled = false` 时把行自己的点击摘了，但**行尾那颗
    //  开关仍然会吃掉触摸**（它自己带手势），所以挂在父层的 `clickable` 拿不到事件 ——
    //  实测「点开关没反应、点空白处才有提示」。正确做法是**盖一层**：置灰时在整行之上铺一块
    //  只吃点击的透明层，命中最上层，这一下就稳定落到提示上。
    //  `indication = null`：置灰行不该再冒涟漪（它会看着像「其实能点」）。
    val disabledClick = LocalDisabledClick.current
    //  「整块往下挪」落到内边距上：上边多、下边少，行高与可点区域都不变（理由见 [contentBias]）
    val margin = contentMargin(contentBias)
    Box(Modifier.fillMaxWidth()) {
        NoSwitchHaptic {
            SwitchPreference(
                title = title,
                summary = if (checked) summaryOn else summaryOff,
                checked = shown,
                onCheckedChange = { on ->
                    if (vibrate) tick()
                    onCheckedChange(on)
                },
                cardListPosition = position,
                insideMargin = margin,
                startAction = {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        //  与 `CardRow` 那颗行首图标同一档：补上库少给的那 8dp（见 [ROW_START_ICON_GAP]）
                        modifier = Modifier.padding(end = ROW_START_ICON_GAP),
                    )
                },
                // `enabled = false` 由 COUI 负责：标题与副标题走 `disabledColor`、行尾开关跟着灰
                enabled = enabled,
            )
        }
        if (!enabled && disabledClick != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = disabledClick,
                    ),
            )
        }
    }
}

/**
 * 把「整块下移 [bias]」折成上下内边距 —— 行高与**可点区域**都不动（理由见 [SwitchRow] 的 `contentBias`）。
 *
 *  上边加多少、下边就减多少：内容下移同样多，总高不变，波纹画的那块区域一个像素都不挪。
 *  下边减到 0 就不再减（内边距不能为负；库的默认下边距 10dp 远大于这里要挪的量）。
 */
@Composable
private fun contentMargin(bias: Dp): PaddingValues {
    if (bias == 0.dp) return BasicComponentDefaults.InsideMargin
    val base = BasicComponentDefaults.InsideMargin
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = base.calculateLeftPadding(dir),
        top = base.calculateTopPadding() + bias,
        end = base.calculateRightPadding(dir),
        bottom = (base.calculateBottomPadding() - bias).coerceAtLeast(0.dp),
    )
}
