package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.model.Command
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 「自定义命令」页上那几个**一次只会开一个**的浮层与弹窗 —— 谁开着、为谁开着。
 *
 *  收在一个对象里而不是摊成四个 `remember { mutableStateOf(...) }`：它们的读写点分布在
 *  两个文件、四处组合里（页内的命令卡长按 / 顶栏那一侧没有 / 五个确认窗），摊开时
 *  「这个位是谁置的、谁清的」得来回翻；收起来之后一眼能列全。
 *
 *  这些位**不落盘**、也不跨进页存活：`CommandUiState` 跟着这一页的组合走，
 *  出页即丢（与 [CommandDraftState] 同一条纪律 —— 但草稿是用户内容，这里是界面的瞬时态）。
 */
internal class CommandUiState {
    /**
     * 首次进这一页的风险提示还在等确认（见 `CommandScreen` 里那段 `LaunchedEffect`）。
     *
     *  这一页能让用户写任意 shell 命令，而我们**不做任何检验或保护**。不确认就退出这一页，
     *  确认了才把那一位记进配置。`configReadOnly` 时不弹 —— 那时写盘会被拒，确认了也记不下。
     */
    var disclaimerPending by mutableStateOf(false)

    /**
     * 哪一张命令卡的菜单开着、**锚在哪**（`null` = 没开着）。
     *
     *  名字是主键 —— 命令名在库里唯一，用它认身份比用位次稳（删一条之后位次会整体前移）。
     *  锚点跟着**从哪儿打开**走：长按走卡片自己那份（弹在手指那一点），右上角那颗「更多」走它自己那份
     *  （弹在那颗字上）。合成一个对象，于是「开着谁、从哪儿弹」只有一处真相。
     */
    var menu by mutableStateOf<TileMenu?>(null)

    /** 待确认的删除：非空 = 弹着确认窗（真删在确认之后）。 */
    var deleting by mutableStateOf<Command?>(null)

    /**
     * 待确认的「填前缀」：非空 = 弹着确认窗。
     *
     *  点「支持的命令格式」那一行是把前缀**整段替换**进输入框（不是插入），输入框里已经有内容时
     *  那是不可逆的，先问一句。
     */
    var pendingFormatPrefix by mutableStateOf<String?>(null)

    /**
     * 「把焦点交给『命令』输入框」的请求 —— 点一下「支持的命令格式」那一行就 +1。
     *
     *  用**自增的计数**而不是一个布尔位：连着点两行时布尔会停在同一个值上（true → true），
     *  第二次就不再是「变化」，`LaunchedEffect` 不会重跑，光标也不会重新挪到末尾。计数每次
     *  都不同，天然可重复触发；初值 0 是「还没点过」，接收方据此跳过首次组合。
     *
     *  要的是三件事一起发生：滚回视野 → 拿焦点（键盘起来）→ 光标落到末尾。三件都由输入框
     *  自己按这个计数去做 —— 触发点在下面的「支持的命令格式」卡上，它够不着输入框的
     *  `FocusRequester`。
     */
    var singleFocusRequest by mutableStateOf(0)
}

/**
 * 一张命令卡的菜单：[name] 是哪一条命令，[anchor] 是**这一下**按在哪 —— 浮层菜单的落点。
 *
 *  两处都能开它（卡片长按 / 右上角那颗「更多」），各自手里只有自己那份触点账本，
 *  所以由开的那一处把锚点一起递上来，菜单层不再猜。
 */
internal class TileMenu(
    val name: String,
    val anchor: PreciseClickState,
)
