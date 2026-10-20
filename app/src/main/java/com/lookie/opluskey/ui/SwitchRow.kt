package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.preference.SwitchPreference

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
    onCheckedChange: (Boolean) -> Unit,
) {
    // 颜色带过渡，与 [SliderRow] 同一套参数：主开关一拨，这个图标与库里那些标题一起渐变。
    //  硬切会出现「图标在淡、字在跳」——长按那张卡里 SwitchRow 与 SliderRow 就是挨着的。
    val iconColor by rememberRowIconColor(enabled, label = "SwitchIcon")

    NoSwitchHaptic {
        SwitchPreference(
            title = title,
            summary = if (checked) summaryOn else summaryOff,
            checked = checked,
            onCheckedChange = onCheckedChange,
            cardListPosition = position,
            startAction = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                )
            },
            // `enabled = false` 由 COUI 负责：标题与副标题走 `disabledColor`、
            // 行尾开关跟着灰、点击不吃（`Component.kt` 的 `clickableModifier` 只在 enabled 时才挂）。
            enabled = enabled,
        )
    }
}
