package com.lookie.opluskey.host

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.host.action.CommandFailure
import com.lookie.opluskey.host.action.ShellRunner
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 「结束应用」的执行侧 —— 把 `proc:stop:包名` 落到系统上。
 *
 *  与 `host/action/` 下那些执行器同一层：它们都是「模块自己进程里真正动系统」的那一层。
 *
 * ## 为什么要 root
 *
 *  宿主跑在 `platform_app` 域，别的应用它既看不见也停不掉（`forceStopPackage` 那条路对非系统 uid
 *  不成立），所以整条命令走 `su`。
 *
 * ## 为什么是异步的
 *
 *  键那条路跑在宿主主线程的按键回调里（`GestureReceiver` 那套），在这里等一条 `su` 命令
 *  就是卡住宿主的 UI 线程。
 */
internal object ProcessController {
    /**
     * 结束一个应用 —— **异步**，结果经 [onDone]（后台线程）。
     *
     *  **三步**（见 [stopCommand]）：先请系统停（`am force-stop`，会连后台限制一起收干净），
     *  再把残留的强杀，**最后核实真没了才算成功**。
     *
     *  @param onDone `null` = 成功；否则是**具体哪一档失败**（没 root / 权限不够 / 命令没跑成），
     *   直接透传给上层报给用户 —— 只回一个布尔会把「没 root」说成「这次没成功」，用户不知道去授权。
     */
    fun perform(
        pkg: String,
        onDone: (CommandFailure?) -> Unit,
    ) {
        if (!Action.isPackageName(pkg)) {
            //  包名会被拼进 shell（`am force-stop <包名>`），形状不对一律不执行
            XLog.w("结束应用：包名不像包名，拒绝执行（$pkg）")
            onDone(CommandFailure.SHELL_FAILED)
            return
        }
        XLog.w("结束应用：$pkg")
        exec(stopCommand(pkg), onDone)
    }

    /**
     * 结束一个应用的那一串命令 —— **先记下要杀哪几个 pid**，系统停 → 强杀这些 → 核实它们真没了。
     *
     * ## 为什么不能只发一条 `am force-stop`
     *
     *  真机实测（PLK110 / ColorOS 17）：`am force-stop com.android.systemui` **退出码 0**、什么也
     *  没结束 —— 那一行进程的 `ETIME` 一动不动（`system_server` 会拦住对系统应用的强行停止）。
     *  而原来的成功判据就是「`am` 没报错」，于是用户看到的是「日志写着已执行，屏幕上一点反应都没有」。
     *  所以判据改成了**事实**：这几个 pid 还在不在。
     *
     * ## 判据为什么是「这几个 pid 没了」，而不是「这个包没有进程了」
     *
     *  系统界面这类常驻应用被真杀掉之后，`system_server` 会**立刻**把它重新拉起来（新 pid）——
     *  按「包名查不到进程」判，就会把一个成功的结束报成失败。要的是「我结束的这几份没了」。
     *
     * ## 为什么强杀要自己挑 pid，不用 `pkill -f 包名`
     *
     *  `pkill -f` 按**整条命令行**匹配，而这条命令自己的 `sh -c …` 里就带着那个包名 ——
     *  一枪先把自己这层 shell 打了，后面的 `echo` 提示永远等不到，于是成功也报成失败。
     *  按 `ps` 第 2 列（进程名）挑就不会误伤：那是「名字等于包名」或「名字以 `包名:` 开头」，
     *  `:push` / `:service` 这些子进程一并收掉，别的应用一个不碰。
     *
     * ## 中间那两次 `sleep`
     *
     *  `am force-stop` 是让 `system_server` 去杀，它是异步的；不等它落地就查，查到的是还活着的
     *  旧 pid 而白杀一遍。两次合计 0.8 秒，远在 [ShellRunner] 那 10 秒上限之内。
     */
    private fun stopCommand(pkg: String): String =
        "todo=\$(${pidsOf(pkg)}); am force-stop $pkg; sleep $STOP_SETTLE_S; " +
            "for pid in \$todo; do kill -9 \$pid 2>/dev/null; done; sleep $STOP_VERIFY_S; " +
            "left=0; for pid in \$todo; do kill -0 \$pid 2>/dev/null && left=1; done; [ \$left = 0 ]"

    /**
     * 「这个包现在有哪几个 pid」—— 一行一个，拿来当「要结束的清单」。
     *
     *  第 2 列是进程名（`ps -A -o PID,ARGS` 的两列写法）。两个条件缺一不可：
     *  `$2==p` 命中主进程（`com.tencent.mm`），`index($2,q)==1`（q 是 `包名:`）命中子进程
     *  （`com.tencent.mm:push`）。
     *
     *  **用 `awk` 而不是 `grep`**：`grep -F 包名` 会把 `com.tencent.mm` 也匹配到
     *  `com.tencent.mmxxx` 上，且 `grep` 自己那一行也在 `ps` 里（要么 `-v grep`、要么被误杀）。
     */
    private fun pidsOf(pkg: String): String = "ps -A -o PID,ARGS | awk -v p=$pkg -v q=$pkg: '\$2==p||index(\$2,q)==1{print \$1}'"

    /**
     * 跑一条「要成功/失败结论」的命令 —— 用命令自己那行提示当成功凭证（见 [ShellRunner.TIP_PREFIX]）。
     *
     *  失败时把 [ShellRunner] 给的**那一档**原样透出去（[onDone] 收 `CommandFailure?`）：
     *  「起不来 `su`」是 [CommandFailure.SHELL_NO_ROOT]、「权限被拒」是 [CommandFailure.SHELL_NO_PERMISSION]，
     *  上层据此说清去哪修 —— 而不是一律「这次没成功」。
     */
    private fun exec(
        command: String,
        onDone: (CommandFailure?) -> Unit,
    ) {
        //  [onDone] **只回一次**：调用方里有「直接拿去弹提示」的（`ProcessAction`），回两次就是两句提示
        val replied = AtomicBoolean(false)
        val reply: (CommandFailure?) -> Unit = { failure -> if (replied.compareAndSet(false, true)) onDone(failure) }
        ShellRunner.exec(
            //  `&& echo` 是关键：命令自己失败时那行提示不会出现，于是「有提示」= 真的干成了
            command = "$command && echo '${ShellRunner.TIP_PREFIX} ok'",
            onFailure = { failure -> reply(failure) },
            onTip = { reply(null) },
        )
    }

    /** 结束进程时等 `system_server` 真的杀完的时长（秒）—— 它异步，不等就是白杀。 */
    private const val STOP_SETTLE_S = "0.3"

    /** 强杀之后、查表核实的间隔（秒）：让 `kill -9` 有时间把进程收尸（否则查到的还是它）。 */
    private const val STOP_VERIFY_S = "0.5"
}
