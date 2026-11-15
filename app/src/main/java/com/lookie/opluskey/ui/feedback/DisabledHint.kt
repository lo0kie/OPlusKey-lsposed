package com.lookie.opluskey.ui.feedback

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.page.APP_STATE_ICON_SIZE
import com.lookie.opluskey.ui.model.page.APP_STATE_TEXT_GAP
import com.lookie.opluskey.ui.model.page.PAGE_MARGIN
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.write.write
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.LocalDisabledClick
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「提示怎么弹」与「点不动时说哪句」—— 控件层拿不到页面实例，这两条通道都从上面传。

/**
 * 「点一下振一次」—— 由页面在根上提供（实现是 `haptics.tick`，见 `PageHaptics`）。
 *
 *  为什么要有这一条：**能点的行默认就该振**（用户口径），而这一下振动发生在
 *  `CardList` / `PickerRow` 这些**控件层**——它们不带 `SettingsPage` 接收者，拿不到页面实例。
 *  所以和 [LocalDisabledHint] 一样从上面传下来，控件只管「点的时候叫一声」。
 *
 *  没提供时是空实现：这些控件在别的上下文里照样能用，只是不振动。
 *
 *  列表里「不振动」的少数几行，用 `CardRow.Item(vibrate = false)` 显式关掉 ——
 *  默认开、个别关，比「每处都要记得振」少一种漏法。
 */
internal val LocalTick = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 一句话 + 要按颗归位的那几个名字 —— 与 `SettingsPage.hint(text, chips)` 同一份口径。
 *
 *  为什么要有这个载体：文案模板（`%s`）与名字必须**一起**走完「控件层 → 页面」那一段 ——
 *  只传模板会在界面上留下裸 `%s`，只传名字则丢掉了语序（各语言的语序不同）。
 *
 *  只在这一层用：那是「点不动时说的那句话」的两个半边，和 `HintItem` 的 `text` + `chips`
 *  是同一种东西，区别只在它还没有落到队列里。
 */
internal class HintText(
    val template: String,
    val chips: List<String> = emptyList(),
)

/**
 * 「这一下为什么点不动」的投递位 —— 由页面在根上提供，**落到页面那个「禁用原因」语义位**
 * （`HINT_SLOT_DISABLED`，见 `ui/screen/PageContent.kt`）。
 *
 *  控件层只管把**一句原因**交出来（经 [DisabledHint]）；落到哪个位、要不要 `remember`、
 *  连点几下会不会叠条，全在提供方那一条上定死，业务层一个都不用碰。
 *
 *  没提供时是空实现：控件在别的上下文里照样能用，只是不出声。
 */
internal val LocalDisabledHint = staticCompositionLocalOf<(HintText) -> Unit> { {} }

/**
 * 「这一块里点不动的东西，被点一下就说 [text] 那句。」
 *
 *  投递口（`LocalDisabledClick`）、语义位（`HINT_SLOT_DISABLED`）与 lambda 的 `remember`
 *  都在这里，调用点只给**一句话**：
 *  ```
 *  DisabledHint(I18n.get(Strings.COMMAND_STEP_SORTING_LOCKED)) { 整块控件 }
 *  ```
 *  以前每个调用点自己拼 `CompositionLocalProvider(LocalDisabledClick provides remember { … })`
 *  —— 三行、还要自己记住「槽位别漏、lambda 得 `remember`」，漏一处就是连点叠一串提示条
 *  或每次重组让读这条通道的控件白白重组。
 *
 *  @param text `null` = 这一块没有自己的说法（或它根本能点）⇒ 不覆盖，交给页面那条
 *   「整页为什么改不了」；非 null = 这一块自己的限制（「最多三步」「已经是第一步」），
 *   那是**另一件事**，不能拿页面那句顶替。
 */
@Composable
internal fun DisabledHint(
    text: String?,
    content: @Composable () -> Unit,
) = DisabledHint(text?.let { HintText(it) }, content)

/**
 * 同上，但带上**要按颗归位的名字**（`%s` 在 [HintText.template] 里）。
 *
 *  带键名的那几条（「按键 %s 只参与非首键的按住组合」「%s 已停用…」）走这一支：名字按颗排，
 *  与提示条里其它回执是同一种排法。纯文字那十几处继续用上面那条重载，一行都不用改。
 */
@Composable
internal fun DisabledHint(
    text: HintText?,
    content: @Composable () -> Unit,
) {
    //  **空串与 `null` 同档**：这一条通道只负责「这一块有自己的说法」；给一句空话等于让这次点击
    //  什么都不说，而屏幕上还留着上一条提示 —— 用户看到的就是「回退到上一个消息文案」。
    if (text == null || text.template.isBlank()) {
        content()
    } else {
        val say = LocalDisabledHint.current
        //  文案按**最近一次组合**那一刻取：这条原因本身就是状态派生来的，原因一变，递它的那一层
        //  本来就会重组。`remember` 住 lambda（键是 [say]）—— 每次重组换一个新实例，会让所有读
        //  这条通道的控件跟着白白重组；文案换了也不换实例，点击时才读最新的那句。
        val latest = rememberUpdatedState(text)
        val onClick: () -> Unit = remember(say) { { say(latest.value) } }
        CompositionLocalProvider(LocalDisabledClick provides onClick, content = content)
    }
}

/**
 * 行尾那半句状态 —— **场景卡那一族**用的（`RuleStateHint` 的 OFF 档，以及条件行）。
 *
 *  「这一项现在不生效」在那一页是同一件事，字号 / 颜色 / 与右侧那颗图标的间距必须一样；
 *  各写一遍的结果是几处同一句话看起来不是一回事。
 *
 *  **列表页（按键 / 组合按键）不用它**：那两页把这句话写成副标题（`CardRow.Item` 的 `summary`），
 *  行尾那一格留给箭头 / 勾选框。
 *
 *  @param text 那一句话本身 —— 默认「已停用」
 */
@Composable
internal fun DisabledStateText(
    text: String = I18n.get(Strings.COMMAND_BRANCH_DISABLED),
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantActions,
        maxLines = maxLines,
        //  **单行时省略**：这一格与标题、箭头挤在同一行，状态一长就只能收尾 ——
        //  不收的话它会把行尾那颗箭头整个顶出可视区（用户口径「角标把行尾箭头挤掉了」）。
        overflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier.padding(end = 6.dp),
    )
}

/**
 * 列表行**副标题**该写的那半句话 —— `null` = 什么都不写。
 *
 * ## 三态互斥（用户口径「和停用 / 启用三选一，启用不用显示」）
 *
 *  · 停着没 → 「已停用」；
 *  · 「只参与非首键的按住组合」→ 「只参与非首键的按住组合」；
 *  · 正常启用 → `null`，这一行不带副标题。
 *
 *  这两件事**不是一回事**：停用是「这颗键不接管任何按键」，只参与非首键的按住组合是「单键手势交还系统、
 *  模块只用它凑组合」—— 后者照样在干活，只是干的不是单键那一份。所以写成**同一行副标题**
 *  而不是两条：这一行本来就只放一句状态，两条一起写会挤掉标题。
 *
 *  判据放在这里而不是列表那一层：按键列表有两处（内置侧键 / 自定义键）各建一次行，
 *  各写一遍 `when` 的话，加第三种状态时一定会漏一处 —— 真机上就是一行写「已停用」、
 *  另一行什么都不写。组合键列表也调它（`comboOnly` 恒传 `false`：那一位只属于单键），
 *  两处都是**副标题**、判据同一份。
 *
 * @param enabled 这一项自己开着没有
 * @param comboOnly 是不是「只参与非首键的按住组合」（见 `KeyConfig.comboOnly`）
 */
internal fun keyStateLabel(
    enabled: Boolean,
    comboOnly: Boolean,
): String? =
    when {
        !enabled -> I18n.get(Strings.COMMAND_BRANCH_DISABLED)
        comboOnly -> I18n.get(Strings.BINDING_COMBO_ONLY_BADGE)
        else -> null
    }

/**
 * 行尾那一格：**一句状态 + 勾选框** —— 编辑态里勾选框代替箭头。
 *
 *  状态那一格留给**列表页**用（`state` 给 [keyStateLabel] 的结论）；按键列表与组合按键列表
 *  只借那颗勾选框（`state = null`）—— 那两页的状态已经写成副标题了。
 *
 *  @param picked 这一行是否被勾中（编辑态）
 *  @param enabled 勾选框本身是否可按 —— 整页闸门关着时按下无意义
 *  @param state 旁边那句状态（见 [keyStateLabel]）；`null` = 只画勾选框
 */
@Composable
internal fun DisabledStateWithPick(
    picked: Boolean,
    enabled: Boolean,
    state: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        //  与 [DisabledStateWithArrow] 同一条：勾选框是控件，不能被状态文字挤出去
        if (state != null) DisabledStateText(state, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
        Checkbox(
            state = if (picked) ToggleableState.On else ToggleableState.Off,
            onClick = null,
            enabled = enabled,
        )
    }
}

/**
 * 设置页的**触觉反馈**那一域：两个系统服务 + 「该不该振」的判据 + 真的振一下。
 *
 * ## 为什么服务要在建页期就拿好
 *
 *  第一次 `View.performHapticFeedback` 会同步走 `SystemVibrator Created`（binder + 服务查询），
 *  砸在 push 动画第一帧上，就是「进子页偶发卡顿」。提前拿到这里，点击路径上只剩一次 `vibrate`。
 *  宿主已持有 `android.permission.VIBRATE`（`dumpsys` 确认过，模块跑在宿主进程里共用）。
 *
 * ## 判据：系统状态 + 模块自己的「界面振动反馈」
 *
 *  [allowed] 看两样：系统铃声模式（静音不振，跟随系统礼节），以及模块的「界面振动反馈」
 *  （`GlobalConfig.uiVibrate`，「更多选项 → 外观」里那一颗）。它**不看**「振动反馈」开关
 *  —— 后者管的是命令执行时的振动，判定在 `host/GestureReceiver`。
 *  判定放在每次振动时现查（[tick] 每振一次查一次），拨完开关 / 系统静音一变立刻生效。
 *
 * ## 为什么这一组要收
 *
 *  两个服务字段 + 两处判据原先散在 [SettingsPage] 与 `PageFeedback.kt` 里，而它们说的是
 *  同一件事：「这台机器的触觉现在能不能用」。摊开时「为什么静音不振」要在两个文件之间找。
 */
internal class PageHaptics(
    context: Context,
    /**
     * 模块自己的「界面振动反馈」开关（`GlobalConfig.uiVibrate`）。
     *
     *  每次现查（[tick] 每振一次查一次），拨完开关立刻生效，不必重建页面。
     *  默认 `true`：没有配置来源时（别的入口 new 一个）按「振」处理，与 [allowed] 读不到
     *  响铃模式时宁可多振同一个口径。
     */
    private val uiVibrate: () -> Boolean = { true },
) {
    private val vibrator: android.os.Vibrator? =
        try {
            context.getSystemService(android.os.Vibrator::class.java)
        } catch (e: Throwable) {
            XLog.w("拿 Vibrator 失败（触觉反馈不可用）：${e.message}")
            null
        }

    private val audioManager: android.media.AudioManager? =
        try {
            context.getSystemService(android.media.AudioManager::class.java)
        } catch (e: Throwable) {
            XLog.w("拿 AudioManager 失败（响铃状态不可读）：${e.message}")
            null
        }

    /**
     * 这一刻该不该振：**模块的「界面振动反馈」开着 且 系统没静音**。
     *
     *  静音不振是跟随系统礼节；模块那一颗管的是「这套界面要不要有触觉」——两者都得成立。
     *  读不到响铃模式时**振**（宁可多振），但开关关着时一律不振。
     */
    fun allowed(): Boolean = uiVibrate() && audioManager?.ringerMode != android.media.AudioManager.RINGER_MODE_SILENT

    /**
     * 实际执行一次点击触觉。
     *
     *  `MissingPermission` 这一条按「谁在执行」判：设置页整套跑在**宿主**进程
     *  （`com.oplus.gesture` / `com.oplus.gesture.ui`）里，而宿主自己声明并被授予了
     *  `android.permission.VIBRATE`（真机 `dumpsys package` 核对过）。模块清单里不声明它 ——
     *  写上也管不到宿主那个 UID。
     */
    @SuppressLint("MissingPermission")
    fun tick() {
        if (!allowed()) return
        try {
            vibrator?.vibrate(
                android.os.VibrationEffect.createPredefined(
                    android.os.VibrationEffect.EFFECT_CLICK,
                ),
            )
        } catch (e: Throwable) {
            XLog.w("触觉反馈失败（忽略）：${e.message}")
        }
    }
}

/**
 * 「这里什么都没有 / 正在等」那一块 —— 一颗字形 + 一句标题（可选）+ 一句说明。
 *
 *  全项目**只有这一份**排版：字形 32dp 定框、标题 `body1`、说明 `footnote1` 次要色、整体居中、
 *  说明左右各留 [PAGE_MARGIN] + 16dp（长句到边会难读）。三处空态都走它：
 *  「选择应用」那三块（加载中 / 没查到 / 搜不到）、「条件执行」的空表、「自定义命令」的空网格。
 *
 *  字形用**插槽**而不是 `ImageVector`：选择应用那块要放转圈（`RotatingProgressIndicator`），
 *  那不是图标。取色由这一份给（次要文字色），调用点只管画形状。
 *
 *  字形占一个**定死的方框**：几种状态互相切换时，下面那行字不会跟着上下跳。
 *
 * @param glyph 上面那颗字形（图标 / 转圈），参数是它该用的颜色
 * @param text 下面那句说明
 * @param title 上面那句标题（`null` = 不要标题，只有一行说明）
 */
@Composable
internal fun Feedback(
    glyph: @Composable (Color) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(APP_STATE_ICON_SIZE),
            contentAlignment = Alignment.Center,
        ) { glyph(COUITheme.colorScheme.onSurfaceVariantSummary) }
        Spacer(Modifier.height(APP_STATE_TEXT_GAP))
        if (title != null) {
            Text(
                text = title,
                style = COUITheme.textStyles.body1,
                //  与下面那句**同色**（用户口径）：空态整块是「说明」而不是「标题 + 正文」，
                //  上面那行只用字号拉开层次，不再用更重的颜色抢注意力。
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            //  标题与说明之间那 2dp = 卡片里「标题 / 摘要」那一档，全项目同一个呼吸
            Spacer(Modifier.height(FEEDBACK_TITLE_GAP))
        }
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = PAGE_MARGIN + 16.dp),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 标题与说明之间的距离 —— 2dp，与卡片里「标题 / 摘要」那一档同值。 */
private val FEEDBACK_TITLE_GAP = 2.dp

// 触觉屏蔽：把库自己发出的振动反馈按住（我们的点击语义与库不一致）。

/**
 * **开关的振动只走通用通道**（[rememberTick] / [tickNow]）。
 *
 * 库的 `Switch` 在 `checked` 变化时**无条件**振一次（经 `LocalView` → 宿主
 * `View.performHapticFeedback`）—— 那条路不看系统静音，于是拨开关会比点别的控件多出一次
 * 不受系统状态约束的振动。
 *
 * 做法：在**那一行**外面把 `LocalHapticFeedback` 换成本实现（见 [NoSwitchHaptic]）。
 * 只包开关那一行 —— 滑杆 / 浮层 / 网格那些格子的触觉不走这里（它们已经没有长按手势了）。
 */
internal val NoHapticFeedback: HapticFeedback =
    object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }

/**
 * 开关那一行的 `LocalView` —— 只为一件事：把库 `Switch` 自己那次振动吞掉。
 *
 * 库的 `Switch` 在 `checked` 变化时**无条件**振一次 `CouiHapticEffect.Switch`（`SwitchKt`
 * 里那个 `LaunchedEffect` 先调 `onCheckedChange`、紧接着调振动），不看系统静音。
 * 于是拨开关会比点别的控件多出一次不受系统状态约束的振动。
 *
 *  它走的是 **`LocalView.performHapticFeedback`**、不是 `LocalHapticFeedback`，
 * 所以 [NoHapticFeedback] 拦不住，必须连 `LocalView` 一起换。
 *  库里读 `LocalView` 的有三处：`CouiHaptic`（就是本行开关那次振动）、coui-core 的
 * `Utils.android.kt`（`getSystemCornerRadius` / `RemovePlatformDialogDefaultEffects`）、
 * coui-nav 的 `NavSystemCornerRadius`。后两处在页面 / 弹窗 / 导航层，**都在这条开关这一行
 * 作用域之外** —— 所以拿 [NoHapticView] 顶替整行的 `LocalView` 仍是安全的。
 *  库的 `ListPopup` 读的是 `LocalViewConfiguration` 而不是 `LocalView`，不在这一列。
 */
internal class NoHapticView(
    context: Context,
) : View(context) {
    override fun performHapticFeedback(feedbackConstant: Int): Boolean = false

    override fun performHapticFeedback(
        feedbackConstant: Int,
        flags: Int,
    ): Boolean = false
}

/**
 * 把一行开关包进「触觉只走通用通道」的作用域 —— 见 [NoHapticView] 与 [NoHapticFeedback]。
 *
 * 包两层是必要的：库自己的振动走 `LocalView`，而 `Modifier.clickable` 那一系的
 * 长按振动走 `LocalHapticFeedback`，两条路都得堵，开关的振动才只剩 [tickNow] 一处。
 */
@Composable
internal fun NoSwitchHaptic(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = remember(context) { NoHapticView(context) }
    CompositionLocalProvider(
        LocalView provides view,
        LocalHapticFeedback provides NoHapticFeedback,
    ) { content() }
}

/**
 * 把库那一次振动**转投到通用通道**的 `LocalView` —— 与 [NoHapticView] 是一对：那个吞掉，这个转投。
 *
 * ## 治的是什么
 *
 *  浮层菜单「按住不放、上下滑动换行」时，每换一行库就振一次（`PopupListGestureHost.willChangeRow`
 *  → `CouiHaptic` → `LocalView.performHapticFeedback`）。那条路**不看** [PageHaptics.allowed]
 *  （系统静音照样振），也不与页面里其它点击同一档 —— 用户口径「菜单按住滑动时的振动没走通用振动」。
 *
 *  库那条路读的是 `LocalView`、不是 `LocalHapticFeedback`，所以 [NoHapticFeedback] 拦不住；
 *  把浮层内容那一层的 `LocalView` 换成这一份，那一次振动就落到 [LocalTick] 上，
 *  判据与时长跟点一下菜单行完全一致。
 *
 * ## 换掉 `LocalView` 的边界（重要）
 *
 *  这个作用域**只能包「没有输入框、也不会自己开窗口」的内容** —— 现在只有浮层菜单那一列
 *  （`PageMenuPopup`）用它。**弹窗整体不能套**：
 *
 *   - Compose 的 `Popup`（输入框的光标 / 选区手柄就是它画的）从 `LocalView` 上取
 *     `ViewTreeLifecycleOwner` 与窗口 token，假 View 两样都没有 ⇒ `PopupLayout` 在挂窗口那一刻
 *     抛 `IllegalStateException`，**宿主进程直接崩**；
 *   - 输入框那一侧的「把光标挪到末尾」是往 `LocalView` 派发按键事件，发给假 View 等于没发。
 *
 *  这两条都是真机踩出来的（见 `docs/device/coui.md` 四十三）。库会振的字面量控件各自收口：
 *  开关在 `SwitchRow` 里、滑杆在 `TrackSlider` 里（关成 `None`），不必靠这一层兜整棵子树。
 *
 * @param onHaptic 转投的去处 —— 调用点给 `LocalTick` 那一条（见 [TickHapticScope]）
 */
internal class TickHapticView(
    context: Context,
    private val onHaptic: () -> Unit,
) : View(context) {
    override fun performHapticFeedback(feedbackConstant: Int): Boolean {
        onHaptic()
        return true
    }

    override fun performHapticFeedback(
        feedbackConstant: Int,
        flags: Int,
    ): Boolean {
        onHaptic()
        return true
    }
}

/**
 * 把这一层里**库自己发出的振动**改走通用通道（见 [TickHapticView]）。
 *
 *  `LocalTick` 在这里**跟着读**（`rememberUpdatedState`）：它由页面提供、每次重组可能是新实例，
 *  而这个 View 是 `remember` 住的 —— 直接闭包会把创建那一刻的那个钉死。
 */
@Composable
internal fun TickHapticScope(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val tick by rememberUpdatedState(LocalTick.current)
    val view = remember(context) { TickHapticView(context) { tick() } }
    CompositionLocalProvider(LocalView provides view) { content() }
}

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
    /**
     * 文案里那几个 `%s` 位置换成**名字**（键名 / 应用名按颗归位，见 `ChipText`）。
     * 空表 = 整句纯文字；[text] 里带着 `%s` 却给了空表时原样显示（那是调用点写漏了）。
     */
    chips: List<String> = emptyList(),
) {
    //  **`d` 而不是 `w`**：提示条本身就是用户看得见的回执，它不在「判据级」那张清单里
    //  （装没装上 / 这次按键是不是侧键 / 判成什么手势 / 配置到没到），而它是全页最高频的一条 ——
    //  挂在 `w` 上会把真正的告警挤掉。调试包照样留着一行。
    XLog.d("提示：$text${if (chips.isEmpty()) "" else "（名字：${chips.joinToString()}）"}${if (slot != null) "（位：$slot）" else ""}")
    hints.hint(text, slot, holdMs, icon, chips)
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
        .get(Strings.TOAST_SWITCHED_TO)
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
    /**
     * 回执里那几个名字（键名 / 应用名按颗归位，见 [hint] 的 `chips`）——
     * lambda 与 [notice] 同一个理由：写盘之后才取，切语言那一条才不会取到旧名字。
     */
    chips: () -> List<String> = { emptyList() },
    notice: () -> String,
) {
    if (write(next, what) != CommandsSave.Ok) return
    hint(notice(), slot = slot, chips = chips())
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
 *  **只有 `ui/row/AppScrollBar` 用它**：浮层菜单、选择应用列表、图标网格、更新弹窗那几条
 *  滚动条都走那一份，别再在调用点各挂一遍。
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
