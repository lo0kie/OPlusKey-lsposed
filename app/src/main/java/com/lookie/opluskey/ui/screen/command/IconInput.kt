package com.lookie.opluskey.ui.screen.command

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.ROW_ICON_SIZE
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.InputViewDefaults
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.Text

// 卡片里那种「行首图标 + 输入」的行 —— 底子是库的搜索输入框（`InputField`）。

/** 错误提示淡入 / 淡出的时长（毫秒）与缓动 —— 与库的 `InputView` 同一组值（其文档给出）。 */
private const val ERROR_FADE_IN_MS = 217

private const val ERROR_FADE_OUT_MS = 283

/**
 * [ImeGuard] 的观察窗口（毫秒）—— 从弹窗打开算起，这段时间内每 [IME_GUARD_TICK_FRAMES] 帧看一眼。
 *
 *  取 2s：真机上「按电源键 → 命名窗弹出 → 键盘被 ROM 收走」那一串发生在开窗后 0.3~1s 内，
 *  2s 留一倍余量。窗口再长就开始具扰民：用户在弹窗里主动收键盘也会被弹回来。
 */
private const val IME_GUARD_WINDOW_MS = 2_000L

/** [ImeGuard] 隔几帧看一眼 —— 6 帧 ≈ 100ms，比 IME 收放动画的一帧一步粗，够用且不烧 CPU。 */
private const val IME_GUARD_TICK_FRAMES = 6

private val INPUT_ERROR_EASE = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

/**
 * 一条输入项的**静态那几样**：行首图标、占位文案、长度上限。
 *
 *  打成一个参数只为把 [IconInputView] 的参数个数留在 detekt 之上限以内（那三样天然是一组：
 *  它们说的是「这一栏是什么」，与「这一栏现在是什么值」无关）。
 *
 * @param maxLength 能输入多少字 —— 超出的部分在组件里直接截掉（**不显示计数**，用户口径）
 */
internal class InputSpec(
    val icon: ImageVector,
    val label: String,
    val maxLength: Int,
)

/**
 * **带行首图标的输入项** —— 底子换成库的 `InputField`（搜索框那一副长相）。
 *
 * ## 为什么不再自己拼卡片
 *
 *  先前这一层是「`Card` + `title` + `TextField`」手拼的：`InputView` 的 `leadingIcon` 是直接
 *  透传给内部 `TextField` 的，卡片里那行 `title` 不跟着让位，只好自己拼一份同款。现在这里
 *  不再有独立的标题行 —— 字段名走 [label]（占位文案），于是 `InputField` 一个组件就够了，
 *  与「选择应用 / 选择界面」那两页的搜索框也是同一副长相。
 *
 * ## 行首图标是**原来那颗**
 *
 *  `InputField` 不给 `leadingIcon` 时画的是放大镜 —— 这里是「命令 / 名称」这些具体字段，
 *  所以把原来卡片行首那颗图标原样递进去（[ROW_ICON_SIZE] + 与其他行首同一档的颜色）。
 *
 * ## 只有单行
 *
 *  多行（textarea）那一档**已撤**：命令正文也是一个输入框，换行不再由这里保留。
 *
 * @param icon 行首图标（按 [ROW_ICON_SIZE] 画，与全页图标同一个尺寸纪律）
 * @param label 占位文案（`请输入名称` 这种）—— 现在也是这一栏唯一的文字说明
 *
 * ## 为什么参数到 8 个（`@Suppress("LongParameterList")`）
 *
 *  `spec` / 值 / 回调之外，`modifier`、`errorMessage`、`focusRequester`、`show` 都只是「输入框这一栏」
 *  的修饰位 —— 拆成 options 对象反而让三个调用点各多一层壳，而 Compose 的写法惯例就是把这些
 *  平铺成行参。标注写在这一处、理由写在这里，不进 detekt 基线（那份基线只管历史遗留）。
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage.IconInputView(
    spec: InputSpec,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String = "",
    /**
     * 非 `null` 时把焦点交给这一栏 —— 弹窗「一打开就能打字」用。
     *
     *  调用方传 `FocusOnShow(show, focusRequester)`：作用在**输入框本体**上，
     *  不是外面那层 Column（挂在 Column 上没有可聚焦的节点，请求会抛 `IllegalStateException`）。
     *  这一层只负责「聚焦之后把光标挪到末尾」与「键盘被外力收走后补回来」—— 后者见 [ImeGuard]。
     */
    focusRequester: FocusRequester? = null,
    /**
     * **弹窗此刻开着没有** —— `null` = 这不是弹窗里的输入框（命令页的名称 / 命令那两栏），
     * 不启用 [ImeGuard]。
     *
     *  非 `null` 时把这个值当「守卫的开关」：只有 `true` 期间才补焦点 / 补键盘，`false` 立刻收手。
     *  这正是「弹窗关了还把键盘拉起来」那条的根治点 —— 输入框自己拿不到 `show` 时，
     *  它任何主动动作都分不清「弹窗还开着」还是「正在关」。
     */
    show: Boolean? = null,
    /**
     * 第一次拿到焦点后把光标挪到**末尾** —— 预填了内容、指望用户接着打字的输入框要它
     * （弹窗里的「按键名称」就是这一种）。
     *
     *  不挪的话光标停在**开头**（原因见 [moveCaretToEnd]），接着打字就插到最前面去。
     */
    caretToEndOnFirstFocus: Boolean = false,
) {
    val isError = errorMessage.isNotEmpty()
    // 淡出期间要**留着最后那条非空消息**，否则红字先消失、再走淡出（库同此做法）
    var shownError by remember { mutableStateOf(errorMessage) }
    // 组合期**不写状态**（写它会让本次组合再跑一轮）：挪到副作用里做同一件事。
    LaunchedEffect(isError, errorMessage) {
        if (isError && shownError != errorMessage) shownError = errorMessage
    }
    // 这一栏此刻有没有焦点 —— 「失焦就抢回来」那条守卫要用（见 [ImeGuard]）
    var focused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        InputField(
            query = value,
            // **长度上限在这一层兜**（不靠各调用点自己截）：超出的部分直接吃掉，
            //  于是右下的计数永远与真正存下去的值一致。
            onQueryChange = { raw -> onValueChange(raw.take(spec.maxLength)) },
            // 键盘上的「搜索」键不做额外动作：这一栏不是搜索，输入即生效
            onSearch = { },
            // 展开态那套是「点搜索框撑开一页结果」用的，这里不展开
            expanded = false,
            onExpandedChange = { },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .inputFocusBehavior(focusRequester, caretToEndOnFirstFocus) { focused = it },
            label = spec.label,
            leadingIcon = { InputLeadingIcon(spec.icon) },
        )
        AnimatedVisibility(
            visible = isError,
            enter = fadeIn(tween(ERROR_FADE_IN_MS, easing = INPUT_ERROR_EASE)),
            exit = fadeOut(tween(ERROR_FADE_OUT_MS, easing = INPUT_ERROR_EASE)),
        ) {
            Text(
                text = shownError,
                fontSize = InputViewDefaults.ErrorFontSize,
                color = InputViewDefaults.inputViewColors().errorColor,
                modifier =
                    Modifier.padding(
                        top = InputViewDefaults.ErrorPaddingTop,
                        start = InputViewDefaults.ContentPadding,
                        end = InputViewDefaults.ContentPadding,
                    ),
            )
        }
    }
    // 焦点 / 键盘守卫 —— 只有弹窗里的输入框（`show != null`）才启用（见 [ImeGuard]）
    ImeGuard(show, focusRequester, focused)
}

/**
 * 弹窗输入框的**焦点 / 键盘守卫** —— 打开后的一小段里，东西被外力收走就补回来。
 *
 * ## 它治的是什么
 *
 *  真机录屏抓到的：从「添加按键」进来、**按电源键**收集这颗键 → 命名窗弹出、键盘出现
 *  （约 300ms 后）**键盘自己没了**，而弹窗还开着。
 *  `logcat` 同一时刻有 `OplusKeyEventUtil: mIsPowerKeyDown=true` —— 电源键在 ROM 自己的
 *  处理链上会顺手把 IME 收掉。于是「打开时叫一次键盘」这个做法只管得住前半程，
 *  后半程掉下去就没人接了。
 *
 * ## 为什么必须先拿到 `show`
 *
 *  上一版把「失焦就抢回来」写在纯粹的 `onFocusChanged` 里，它**手里没有 `show`**：
 *  弹窗关掉（退出动画那两百毫秒里输入框还在组合树里）之后它照样 `requestFocus()`，
 *  真机表现就是「弹窗消失时键盘又被拉起来」。所以这里把 `show` 作为**唯一开关**：
 *  `show` 一变 false，effect 立刻取消，什么都不再做。
 *
 * ## 为什么是「定时看几眼」而不是「盯住每次变化」
 *
 *  盯 `focused` / IME 高度当 key 的话，IME 收放动画每帧都在变 —— effect 会被反复取消重启，
 *  最后谁也不确定它跑没跑完。改成在固定窗口内每 [IME_GUARD_TICK_FRAMES] 帧看一眼：
 *  键盘露着 + 焦点在 ⇒ 什么都不做；失焦 ⇒ 抢回来；聚焦着但键盘没了 ⇒ 补叫。
 *
 * @param show 弹窗开着没有（`null` = 这不是弹窗输入框，整段不挂）
 * @param focusRequester 聚焦入口（`null` 同上）
 * @param focused 这一栏此刻有没有焦点 —— 调用方在 `onFocusChanged` 里维护
 */
@Composable
private fun ImeGuard(
    show: Boolean?,
    focusRequester: FocusRequester?,
    focused: Boolean,
) {
    if (show == null || focusRequester == null) return
    val insets = WindowInsets.ime
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    // effect 只随 `show` 重启，闭包里读到的 `focused` 是启动那一刻的旧值 —— 抓当前那份
    val focusedNow by rememberUpdatedState(focused)
    LaunchedEffect(show, focusRequester) {
        if (!show) return@LaunchedEffect
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < IME_GUARD_WINDOW_MS) {
            repeat(IME_GUARD_TICK_FRAMES) { withFrameNanos { } }
            // 键盘露着、焦点也在：这一眼没有要补的
            if (insets.getBottom(density) > 0 && focusedNow) continue
            if (!focusedNow) {
                runCatching { focusRequester.requestFocus() }
            } else {
                keyboard?.show()
            }
        }
    }
}

/**
 * 输入框那一栏的**焦点行为**：第一次拿到焦点时把光标挪到末尾，并把焦点状态报给调用方。
 *
 * ## 「打开就聚焦 / 叫键盘」不在这里
 *
 *  那件事要跟着弹窗的 `show` 一起生灭：聚焦归 `FocusOnShow`，键盘与补焦点归 [ImeGuard]
 *  （它显式收 `show`，关窗即收手）。这一层只做「一次性、无副作用」的挪光标。
 *
 * @param focusRequester `null` = 这一栏不需要程序化聚焦（普通输入行），整段不挂
 * @param caretToEnd 预填了内容那种（弹窗里的按键名称）才需要 —— 见 [moveCaretToEnd]
 * @param onFocusedChange 焦点状态变化时回报（[ImeGuard] 的判据）
 */
@Composable
private fun Modifier.inputFocusBehavior(
    focusRequester: FocusRequester?,
    caretToEnd: Boolean,
    onFocusedChange: (Boolean) -> Unit,
): Modifier {
    if (focusRequester == null) return this
    val view = LocalView.current
    // 只在**第一趟**挪光标：之后用户点出去再点回来，光标该停在他点的地方
    var caretMoved by remember { mutableStateOf(false) }
    return this
        .focusRequester(focusRequester)
        .onFocusChanged { state ->
            // 先回报状态，再处理光标：下面那两句在非首次聚焦时会提前 return
            onFocusedChange(state.isFocused)
            if (!state.isFocused || !caretToEnd || caretMoved) return@onFocusChanged
            caretMoved = true
            moveCaretToEnd(view)
        }
}

/**
 * 把光标挪到末尾 —— 给「打开就能接着打字」的输入框用。
 *
 * ## 为什么要绕这一下
 *
 *  库的 `InputField` 只收 `String`，而 Compose 的 `BasicTextField(String)` 在新建内部状态时
 *  用的是 `TextFieldValue(text)` —— 那个构造器的默认选区是 **0**，于是预填了名字的输入框
 *  一聚焦，光标贴着**开头**：接着打字就插到最前面（真机反馈）。
 *  拿不到 `TextFieldValue` 就没法直接写选区，所以走键盘那条路：
 *  Compose 的 `defaultKeyMapping` 认 `KEYCODE_MOVE_END`（映射到「光标到末尾」那条命令），
 *  往**当前窗口**发一次即可，输入框外观一个字都不用改。
 *
 *  事件要发给**当前有焦点的那棵视图树**。`LocalView` 在这里拿到的是宿主那个 `ComposeView`
 *  —— COUI 的浮层挂在根 Scaffold 的 `popupHost` 上、不是独立的 `Dialog` 窗口（见
 *  `docs/device-findings.md` 第二十六节），所以页面与弹窗共用同一个 view，事件能到弹窗里
 *  那个焦点上。
 *
 *  排一帧再发：焦点刚到时 `CoreTextField` 内部那份状态还没跟上，这一下会被丢掉。
 */
private fun moveCaretToEnd(view: View) {
    view.post {
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MOVE_END))
    }
}

/**
 * 输入框行首那一格图标。
 *
 * ## 内外边距必须自己补
 *
 *  库里自带那颗放大镜是这么画的（`SearchBar.kt` 的默认 `leadingIcon`）：
 *  `Icon(Search, Modifier.padding(start = LeadingIconStartPadding, end = LeadingIconEndPadding))`。
 *  换成自己的图标时**这两档不能省** —— 省了图标就贴着输入框左边缘、跟文字挤在一起
 *  （真机上就是「很诡异」那种观感）。库那两个常量是 public 的，直接用。
 *
 *  尺寸仍取 [ROW_ICON_SIZE]（22dp）：比其他行首图标是同一档，比库自带的 24dp 小一点点，
 *  肉眼看不出来，但全页口径一致。
 */
@Composable
internal fun InputLeadingIcon(icon: ImageVector) {
    // 颜色**取库自己的那一档**（`SearchBarColors.IconColor`）：页面里其他行首用的
    //  `onSurfaceContainer` 在输入框这块底色上偏亮（用户口径「太亮」），与旁边的占位文字
    //  一比就更突兀。用同一个 `searchBarColors()`，将来库调色我们跟着变。
    val colors = SearchBarDefaults.searchBarColors()
    Icon(
        icon,
        contentDescription = null,
        modifier =
            Modifier
                .padding(
                    start = SearchBarDefaults.LeadingIconStartPadding,
                    end = SearchBarDefaults.LeadingIconEndPadding,
                ).size(ROW_ICON_SIZE),
        tint = colors.iconColor,
    )
}
