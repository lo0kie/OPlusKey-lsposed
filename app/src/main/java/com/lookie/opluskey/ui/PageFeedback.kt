package com.lookie.opluskey.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.ui.state.CommandsSave

/**
 * 「按键绑定」页里**所有操作回执共用的那一个语义位**。
 *
 * ## 为什么整页只给一个位
 *
 *  这一页的动作密度是全模块最高的：挑动作、停用键、改名、删键、收集按键、长按连续触发与它
 *  那几个下属开关……原先是**各给各的位**（绑定那条按 `bind:${spec.token}` 分发，开关那条按
 *  `what` 的前缀分发），于是连着改两格就是「已绑定「录音」」+「已绑定「微信」」两条叠在顶上。
 *  可这两句说的是**同一件事的先后两态**，不是两件并列的事。
 *
 *  这也正是这批提示的出身：它们取代的是 Android `Toast`（见上面 [SettingsPage.hint] 的类注释），
 *  而 Toast 的天然语义就是**后来的顶掉前一条** —— 屏幕上永远只有一句。`HintQueue` 的「位」
 *  把这件事落在状态层：同一个位上再来一句话**改那一条的字**、id 不变，于是界面走的是换句动画
 *  （宽度过渡 + 文字淡入淡出），既不重新入场、也不多占一格。
 *
 * ## 边界
 *
 *  只管**按键绑定这一页**（含它那几个弹窗与卡片）。别处仍按「改的是哪一项」分位
 *  （[writeAndNotice] 默认取 `what` 的前缀）—— 那些页一次只改一项，分位才看得出「这条说的是哪个开关」。
 */
internal const val HINT_SLOT_BINDING = "binding"

/**
 * 「自定义命令」页里操作回执共用的语义位 —— 与 [HINT_SLOT_BINDING] 同一套道理。
 *
 *  工具条那颗「保存 / 修改」按下去会报「已保存 / 已修改 / 已覆盖」；同一页还会报「已触发」
 *  「已删除「X」」「最多 5 步」这些。各说各的时候它们是几条互不相干的提示条，连着点两下
 *  （存一条、再试跑一次）屏幕上就叠了两句；共用这一位之后是**一句话被改写**
 *
 *  同样只管这一页：别的页面仍按「改的是哪一项」分位。
 */
internal const val HINT_SLOT_COMMAND = "command"

/**
 * **本页杂项** —— 所有「没特别指定语义位」的提示都落在这里。
 *
 *  以前不传 `slot` 等于「每次新开一条」：同一个按钮连点两下（保存失败再试一次）会叠出两条
 *  几乎一样的条子，用户看到的就是「一直弹新的」。收到这一个位上之后，同一页的杂项提示
 *  永远只有一条、原地改字。
 */
internal const val HINT_SLOT_PAGE = "page"

/**
 * 「这一下点不动」那一条的语义位 —— 页面上**所有**禁用态控件共用它。
 *
 *  为什么需要一个位：这条回执原来没给位（`hint(disabledClickReason())`），于是每点一下都是
 *  **新的一条**（新 id ⇒ 重新入场），连点几下就看见提示条叠起来、被挤下去的那条往下滑走 ——
 *  真机口径「来回刷」。而「还有条件没选完」那种给了位，永远是一条在原地改字。
 *  同一个控件连点两下本来就该只有一句话，所以这里也收成一位。
 */
internal const val HINT_SLOT_DISABLED = "disabled"

/**
 * 「组合键」那两页共用的语义位 —— 加一档、删一档、写盘失败都报在这里。
 *
 *  整页一次只改一件事（加一组 / 删一组），共用一个位就够：连着删两档时是那一条在改字，
 *  而不是两条叠着往下推。
 */
internal const val HINT_SLOT_COMBO = "combo"

/**
 * 「独立设置」页那几颗开关共用的语义位 —— 与公共页每一颗开关各占一个位的口径不同，这里收成一位。
 *
 *  这一页整屏改的是**同一份**独立设置（接口 / 振动 / 强度 / 提示 / 文案，组合再多两项），
 *  连着拨两颗时应当是那一条在改字（「已开启「振动反馈」」→「已开启「执行时提示」」），
 *  而不是两条叠着往下推。滑杆与文案窗不占这个位（它们不弹拨动回执）。
 */
internal const val HINT_SLOT_OWN_SETTINGS = "own_settings"

/**
 * 「正在检查模块…」那一条的语义位 —— 它跟别的回执不一样：**挂在自检结束上**，不按秒数走。
 * 自检一跑完（`LinkFacts.injectChecked`）就由 `PageCheckingHint` 把它收掉，结论接着顶上来。
 */
internal const val HINT_SLOT_CHECKING = "checking"

/**
 * 操作提示 —— 用户做完一个动作之后的回执（一句话，页面顶端短暂出现）。
 *
 *  文案一律走这里，调用点只传一句结论；写盘失败这类没有可见结果的也报一句，免得「点了没反应」。
 *
 *  ## 为什么不是 Android Toast
 *
 *  Toast 是**系统窗口**：它的样式跟着系统走，与 COUI 那套胶囊完全两回事 —— 同一页里
 *  一条圆角磨砂的常驻提示旁边飘一个方头方脑的 Toast，看着就不是一个应用。而且 Toast
 *  在设置页这种「整页覆盖层」里还容易被宿主的窗口层级压住。所以统一走 COUI 的 `Snackbar`：
 *  与那条常驻提示同一个组件、同一个落点，样式自动跟着主题。
 *
 *  落点在页面级那个提示槽（`ui/screen/PageContent.kt` 的 Scaffold **浮层槽**，
 *  排在弹窗宿主之后）—— 各页都能看到、不随内容滚动，而且**压在弹窗之上**。
 *
 *  **不进提示条的唯一一处**：命令执行失败那条（`host/action/CommandFailure`）—— 它发生在模块
 *  **不在前台**的时候，页面根本没在画。
 *
 *  （「收集按键」弹窗里的拒绝理由原先也走正文，是因为当时提示条在 `snackbarHost` 槽、
 *  永远被弹窗盖住；2026-10-19 提示槽挪到浮层槽之后那条理由不成立了，它已改走 [SettingsPage.hint]。）
 *
 * @param slot 语义位：同一个位上的话**改那一条**而不是再叠一条（见 `HintQueue.push`）。
 *   开关 / 切换类都带位（`writeAndNotice` 按写盘说明的前缀给）；
 *   **不给位时落到「本页杂项」这一个位上**（[HINT_SLOT_PAGE]），不再每次新开一条 ——
 *   以前 `null` 的含义是「各说各的」，结果连点同一个按钮也会叠出一串几乎一样的条子。
 */
internal fun SettingsPage.hint(
    text: String,
    slot: String? = HINT_SLOT_PAGE,
    /** 这一条自己的停留时长（`null` = 队列统一那一档）。见 [HintQueue.push] 的 `holdMs`。 */
    holdMs: Long? = null,
    /** 前置图标（`null` = 界面画默认的 alert-circle）。见 `HintItem.icon`。 */
    icon: ImageVector? = null,
) {
    //  **`d` 而不是 `w`**：提示条本身就是用户看得见的回执，它不在「判据级」那张清单里
    //  （装没装上 / 这次按键是不是侧键 / 判成什么手势 / 配置到没到），而它是全页最高频的一条 ——
    //  挂在 `w` 上会把真正的告警挤掉。调试包照样留着一行。
    XLog.d("提示：$text${if (slot != null) "（位：$slot）" else ""}")
    hints.hint(text, slot, holdMs, icon)
}

/** 把某个语义位上的提示**提前收掉**（播完退场再摘，不硬切）—— 见 [HintQueue.retire]。 */
internal fun SettingsPage.retireHint(slot: String) = hints.retire(slot)

/**
 * 拨开关的回执 —— 「已开启「振动反馈」」/「已关闭「振动反馈」」。
 *
 *  [title] 由调用点把自己那一行的标题递进来（含译文），不在这里按功能列表反查：开关散在
 *  四个文件里，一处分档就得改三处；由那一行自己报名字，改文案只跟着那一行走。
 */
internal fun switchNotice(
    title: String,
    on: Boolean,
): String =
    I18n
        .get(
            if (on) Strings.TOAST_SWITCH_ON else Strings.TOAST_SWITCH_OFF,
            if (on) "已开启 [%s]" else "已关闭 [%s]",
        ).format(title)

/**
 * 「换了一档」的回执 —— 「强调色 已切换到「蓝色」」。
 *
 *  比 [switchNotice] 多一个**主语**：[switchNotice] 那句「已开启「振动反馈」」自己就说明了对象
 *  （开关的标题就在句子里）；而「已切换到「蓝色」」没有主语时，用户看不出是主题、强调色还是语言
 *  变了 —— 这三行同一个句式：主语 + 变了什么。
 *
 *  [subject] 与 [value] 都由调用点传译文，不在这里按功能列表反查（理由同 [switchNotice]）。
 */
internal fun switchToNotice(
    subject: String,
    value: String,
): String =
    I18n
        .get(Strings.TOAST_SWITCHED_TO, "%s 已切换到 [%s]")
        .format(subject, value)

/**
 * 写一次配置 + 报一句回执（拨开关、改绑定这类「改完就完事」的动作都走它）。
 *
 *  **只在 [CommandsSave.Ok] 时报**：[write] 对「没落盘」与「落盘了但广播没出去」各自已经报过
 *  自己的那一条，这里再补一句「已开启」会把那条警告顶掉 —— 底部只有一行操作回执。
 *
 *  [notice] 用 lambda 而不是字符串：切语言那一条的名字必须在**写盘之后**取
 *  （[write] 里换了 [I18n.currentLanguage]），先算好的话整句话还是旧语言。
 */
internal fun SettingsPage.writeAndNotice(
    next: Config,
    what: String,
    //  **[what] 的前缀就是这一条的语义位**：它长这样 `vibrate=1` / `palette=dark` / `theme=light`，
    //  等号前那一截正是「这次改的是哪一项」。同一个开关连点两下时，屏幕上应当是**一条在改字**
    //  （「已开启「振动反馈」」→「已关闭「振动反馈」」），而不是两条叠着（用户口径
    //  「同态的提示拿引用修改而不是弹一条新的」）。
    //  极少数「写的是 A、回的却是 B」的场合（首次启用把免责声明与总闸一起写）用 [slot] 覆盖。
    slot: String? = what.substringBefore('='),
    notice: () -> String,
) {
    if (write(next, what) != CommandsSave.Ok) return
    hint(notice(), slot = slot)
}

// UI 触觉反馈的唯一判据是 `PageHaptics.allowed`：只看系统状态（静音不振），模块的「振动反馈」
//  开关不参与 —— 它管的是命令执行时的振动（判定在 `host/GestureExec`）。

/**
 * 通用振动通道 —— 界面所有点击 / 长按的触觉都走这里（实现在 [PageHaptics.tick]）。
 */
@Composable
internal fun SettingsPage.rememberTick(): () -> Unit = remember { { tickNow() } }

/**
 * 点滚动条那一下的振动 —— 判据是「这次按下**已经被滚动条 consume**」。
 *
 *  滚动条把 DOWN 吃掉（点在拇指或轨道上）才会走到这里；页面别处、行与卡片都不 consume，
 *  于是「按到滚动条」与「按到内容」靠这一条就能分开，不必自己算那条 12dp 的窄带。
 *  两处滚动条共用它：浮层菜单（`MenuScrollBar`）与更新弹窗的说明区 —— 同一种手感只写一处。
 */
@Composable
internal fun Modifier.scrollBarTick(): Modifier {
    val tick = LocalTick.current
    return pointerInput(tick) {
        awaitEachGesture {
            //  `requireUnconsumed = false`：要的正是「**已经**被 consume」的那一次按下
            if (awaitFirstDown(requireUnconsumed = false).isConsumed) tick()
        }
    }
}

/**
 * 实际执行一次点击触觉。判据只有 [PageHaptics.allowed]（系统状态），与模块开关无关。
 * `MissingPermission` 为什么成立写在 [PageHaptics.tick] 那里。
 */
internal fun SettingsPage.tickNow() = haptics.tick()
