package com.lookie.opluskey.ui.screen.command

import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.host.action.RootGrant
import com.lookie.opluskey.ui.HINT_SLOT_COMMAND
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hint

/**
 * 打开「以 root 执行」时**当场申请一次 root 授权**（「命令设置」那一行）。
 *
 *  为什么不等真正执行：`su` 的授权框是 root 管理器在**第一次执行**时弹的，而那要等到用户去按侧键
 *  —— 授权框会在他看不到前因后果的时刻跳出来（按一下侧键，屏幕上多一个陌生的框）。既然他刚在
 *  这一行说过「这条要 root」，授权就该发生在此刻。
 *
 *  判据与真执行同一条（[RootGrant.request] 走的就是 `ShellRunner.exec`）：这里报「已拿到」，按侧键
 *  时也一定拿得到；这里报没授权，按下去也是同一句失败提示。
 *
 *  开关**关掉**时不申请任何东西 —— 那一档只用 `sh`，没有要 root 的地方。
 */
internal fun SettingsPage.requestRootGrant() {
    // 提示条是 Compose 状态（`HintQueue.push` 直接写 `mutableStateOf`），而结果回在 shell 线程上
    // ⇒ 回主线程再报。
    val main = Handler(Looper.getMainLooper())
    try {
        RootGrant.request { failure ->
            main.post {
                hint(
                    // 失败那一侧直接用 `CommandFailure` 表里那句：与按侧键执行失败时一字不差，
                    // 不另写一份「没授权」的说法（同一件事只有一处文案）。
                    failure?.hint ?: I18n.get(Strings.COMMAND_ROOT_GRANTED, "已获取root权限"),
                    slot = HINT_SLOT_COMMAND,
                )
            }
        }
    } catch (e: Throwable) {
        // 页面动作绝不外抛（与 `testCommand` 同一条纪律）：抛出去会落到 Compose 的点击处理里
        XLog.w("申请 root 失败（已忽略）：${e.message}")
    }
}
