package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.model.CommandKind

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
     * 「步骤排序」编辑态 —— 由「命令列表」卡右下角那颗 FAB 开关。
     *
     *  开着的这段时间里：步骤行行尾换成上移 / 下移 / 删除三颗行内按钮，行的轻点不再换命令，
     *  页面其余控件一律禁用、底部工具条滑出隐藏。它是**整页**的态（表单与列表都要读），
     *  所以住在这一域、而不是某个 composable 的 `remember` 里。
     *
     *  换 tab 时由 `CommandTabSync` 摘掉：另一页没有步骤可排，带着它过去只会让那一页莫名其妙地全灰。
     */
    var stepEditing by mutableStateOf(false)

    /**
     * 步骤行的**展示层**（动画用）—— 住在这一域，不住 `SerialSteps()` 里。
     *
     *  切 tab 时那一页的组合会被注销，`remember` 的展示层跟着没了，切回来整张表重播进场动画。
     *  放到页面级之后，切回来时行还是原来那些，`AnimatedVisibility` 的 currentState 仍是 true，
     *  不会再播一次（见 [StepRowDisplay] 的类注释）。
     */
    val stepRows = StepRowDisplay()

    /**
     * 下一次进「自定义命令」页要停在**命令列表**那一格 —— 一次性意图，读到就清掉。
     *
     *  手势页那条「已保存的命令」标题点下去是「去管命令库」，落点就该是列表那页；
     *  而进页的分页定位在 `PageContent.PushResetsPagers` 里、按草稿类型算，那一层看不到
     *  「用户想从哪进来的」—— 于是由发起方在这儿留一句，那边读一次就清。
     *
     *  消费方一定跑：它挂在 push 那一刻（`LaunchedEffect(navStack.size)`），与这次导航同一拍。
     */
    var openListPage by mutableStateOf(false)

    /** 读一次并清掉 [openListPage]。 */
    fun consumeOpenListPage(): Boolean {
        val want = openListPage
        openListPage = false
        return want
    }

    /**
     * 页面**离开栈**：草稿与 tab 镜像一起归位。
     *
     *  与 [CommandDraft.reset] 的差别只有一处：这里连 `kind` 也清回单条。平时 `kind` 由分页器
     *  单向同步（见那边的注释），但页面出栈之后没人再替它归位，而下次进页时
     *  `PushResetsPagers` 要按它定位 tab —— 不清就会停在上次那一档。
     */
    fun discard() {
        draft.reset()
        draft.kind = CommandKind.SINGLE
        stepEditing = false
        stepRows.clear()
        openListPage = false
    }
}
