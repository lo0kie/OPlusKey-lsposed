package com.lookie.opluskey.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import io.github.suqi8.coui.kmp.basic.InputField

/**
 * **单行输入框** —— 全项目包住库 `InputField` 的**唯一一处**。
 *
 * ## 为什么要包这一层
 *
 *  库那个 `InputField` 是单行的，但它**不管粘进来的换行**：剪贴板里带 `\n` 的一段字原样写进去，
 *  可见那一行止于第一个换行、光标却落在最后一个换行之后 —— 框里看着一个字都没有，值其实还在。
 *  这个坑与「哪个调用点」无关（重命名弹窗、命令名称、变量值、两页搜索框都会中），所以治在
 *  **这一层**：值从这里过一道，谁都不用记得自己折一次。
 *
 * ## 换行怎么折
 *
 *  默认折成**一个空格**（`a\n\nb` → `a b`）：名字 / 变量值 / 搜索词里换行没有语义，粘进来两行
 *  本来就该读成两段字。**不 trim 首尾** —— 那会把正在输入的空格吃掉（与 `singleLine` 同一条账）。
 *
 *  命令正文那一栏例外：它要把换行折成 `; `（多行脚本粘进来 = 依次执行，见 `singleLine`），
 *  折成空格会得到一条语法错误的命令 —— 所以 [sanitize] 留给调用点换。
 *
 * ## 其余几位
 *
 *  `expanded` / `onExpandedChange` / `onSearch` 恒是「不展开、搜索键不做额外动作」：这一层包的
 *  都是**输入栏**，不是「点一下撑开一页结果」的搜索入口（库那套展开态在这里没有去处）。
 *
 * ## 键盘收走就失焦
 *
 *  收起键盘之后框里还留着光标与聚焦描边、看着像「还在编辑」，所以这一层统一挂 [ImeFocusRelease]：
 *  键盘从露着变成没了 ⇒ 清焦点。弹窗里的那一档要等 `ImeGuard` 的守卫窗口过去（见
 *  [AppInputOptions.imeHoldMs]），其余输入栏一收即失焦。
 *
 * @param query 当前值
 * @param onQueryChange 值变了（**已经过 [AppInputOptions.sanitize]**，拿到的串里不会有 `\r` / `\n`）
 * @param label 占位文案
 * @param options 其余可选档（禁用、行首图标、换行怎么折、长度上限）—— 收成一个对象。
 */
@Composable
internal fun AppInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    options: AppInputOptions = AppInputOptions(),
) {
    //  配色只有这一份：各处输入框都要在 Monet 下调淡（见 `couiInputColors`），摊成参数只会让
    //  每处再抄一遍 —— 收在这一层，调用点就不会各写各的。
    val colors = couiInputColors()
    //  这一栏此刻有没有焦点 —— 「键盘收走就失焦」的判据（见 [ImeFocusRelease]）
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    ImeFocusRelease(focused = focused, holdMs = options.imeHoldMs) { focusManager.clearFocus() }
    InputField(
        query = query,
        onQueryChange = { raw ->
            //  先折换行、再截长度：折完才是「真正会存下去的那一串」，按它数才数得准。
            val folded = options.sanitize(raw)
            onQueryChange(if (options.maxLength == null) folded else folded.takeCodePoints(options.maxLength))
        },
        // 键盘上的「搜索」键不做额外动作：这一栏不是搜索入口，输入即生效
        onSearch = { },
        // 展开态那套是「点搜索框撑开一页结果」用的，这里不展开
        expanded = false,
        onExpandedChange = { },
        enabled = options.enabled,
        //  焦点状态挂在**外层**：真正拿到焦点的是库内部的输入框节点，外层只会看到
        //  `isFocused = false` / `hasFocus = true`（判据与 `IconInputView` 那条同源）
        modifier = modifier.onFocusChanged { focused = it.hasFocus },
        label = label,
        leadingIcon = options.leadingIcon,
        colors = colors,
    )
}

/**
 * [AppInputField] 的那几个可选档 —— 默认值就是「能打字的一栏」。
 *
 * @param enabled 禁用时整栏收进库的灰档（外层的压暗由调用点自己做）
 * @param leadingIcon 行首图标；`null` = 库默认那颗放大镜
 * @param sanitize 值进来时怎么处理 —— **必须保证返回值里没有 `\r` / `\n`**
 * @param maxLength 最多收多少个字符（按**码点**数，见 [takeCodePoints]）；`null` = 不设上限。
 *  超出的部分在 [AppInputField] 里直接吃掉，于是框里显示的、右下的计数、真正存下去的值
 *  永远是同一份。
 * @param imeHoldMs 「键盘收走就失焦」开头的守卫窗口（毫秒，见 [ImeFocusRelease]）：
 *  弹窗里的输入框传 `ImeGuard` 那一档（开窗那一段由它补键盘，这里先让位），页面里的输入栏传 0。
 */
internal class AppInputOptions(
    val enabled: Boolean = true,
    val leadingIcon: (@Composable () -> Unit)? = null,
    val sanitize: (String) -> String = ::foldLineBreaks,
    val maxLength: Int? = null,
    val imeHoldMs: Long = 0L,
)

/**
 * 把换行折成**一个空格** —— [AppInputField] 的默认那道。
 *
 *  连续几个换行（含 CRLF、单独 CR）一起折成一个空格：`a\n\nb` 折成 `a b` 而不是 `a  b`，
 *  名字里多出一个看不见的空格比少一个更难查。
 *
 *  **没有换行就原样返回**：正常打字一个字符都不碰（与 `singleLine` 同一条口径）。
 */
internal fun foldLineBreaks(raw: String): String {
    if (!raw.contains('\n') && !raw.contains('\r')) return raw
    return raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("\n+"), " ")
}

/**
 * 按**码点**取前 [max] 个字符 —— 长度上限不能按 UTF-16 单元切。
 *
 *  Kotlin 的 `String.take(n)` 数的是 `Char`，而一个 emoji / 扩展区汉字占两个 `Char`；正好切在
 *  代理对中间时，留下的是半个码点（界面上是一颗替换符，写进配置也还原不回来）。
 */
internal fun String.takeCodePoints(max: Int): String =
    if (max <= 0) {
        ""
    } else {
        substring(0, offsetByCodePoints(0, minOf(max, codePointCount(0, length))))
    }
