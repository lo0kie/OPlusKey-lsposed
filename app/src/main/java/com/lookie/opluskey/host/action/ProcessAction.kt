package com.lookie.opluskey.host.action

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.host.ProcessController

/**
 * `proc:stop:包名` 的执行器 —— 把「结束应用」那一格挑出来的应用做成按键动作。
 *
 *  真正的命令拼装与成败判据在 [ProcessController.perform]。这一层只做两件事：把 [Action.Process]
 *  交给它，失败时按项目统一那套报出去。
 *
 *  **异步**：`perform` 立刻返回，结果在 ShellRunner 的池上回调 —— 本函数跑在宿主主线程的
 *  按键回调里，阻塞就把宿主的 UI 卡住了。失败提示由 [onFailure] 走既有的 Toast 通道。
 */
internal object ProcessAction {
    fun run(
        act: Action.Process,
        onFailure: (CommandFailure) -> Unit,
    ) {
        ProcessController.perform(act.pkg) { failure ->
            //  判据级：这条是「动作到底有没有落到系统上」的唯一凭证，用 w。
            //  带具体失败档：没 root / 权限不够 / 命令没跑成，各报各的，不再糊成一句「没成功」。
            XLog.w("proc：${act.token} ${failure?.let { "没成功（${it.name}）" } ?: "已执行"}")
            if (failure != null) onFailure(failure)
        }
    }
}
