package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「自定义命令」页那一域的状态 —— 草稿本体 + 「重置」那套行级退场的三个标记。
 *
 * 这一份收在页面对象上（不是页面里 `remember`）：草稿要**跨重组**活着 —— 从别的页面回来时
 * 不该被重新组合清掉；每次进这一页由「按键绑定」那张列表的跳转按钮显式 reset。
 * 三个标记跟着草稿一同生共死，所以同属这一域。
 */
internal class CommandDraftState {
    /** 正在编辑的那条命令的草稿。 */
    val draft = CommandDraft()

    /**
     * 「重置」走**行级退场**时的挂起标记。
     *
     *  工具条的重置对「有多条步骤的多条命令」不直接 `reset`：整批清空会让所有步骤行瞬时消失。
     *  先置这里，步骤卡里每一行播完自己的 shrink 退场之后，由它真正 reset（见收尾协程）。
     */
    var resetPending by mutableStateOf(false)

    /**
     * 「重置」收尾**被放弃**的那一次（收尾协程发现退场期间用户又改了表单）。
     *
     *  为什么要单独一位：步骤卡在 [resetPending] 置位那一刻就把每一行标了退场（`exiting = true`），
     *  而退场行会被摘除、重建那道闸的 key 是 `draft.steps` 的内容 —— 内容没变就不会重跑
     *  ⇒ 步骤在界面上凭空消失、数据还在（看不到也删不掉）。置这一位把那些行收回来，
     *  处理完它自己清掉。
     */
    var resetAborted by mutableStateOf(false)

    /**
     * 「重置」收尾那 240ms 的**基线指纹**（[resetPending] 置位那一刻记下）。
     *
     *  收尾时只有指纹没变才真正 reset —— 这段时间用户可能已经在输入框里敲字了，
     *  无条件 reset 会把刚敲的内容一起吞掉（见 `CommandDraft.contentFingerprint`）。
     */
    var resetBaseline: String = ""
}
