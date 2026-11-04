package com.lookie.opluskey.host.action

import com.lookie.opluskey.core.log.XLog

/**
 * 「申请一次 root 授权」—— 「以 root 执行」那个开关**打开的那一刻**由界面调。
 *
 *  为什么要有它：`su` 的授权是 root 管理器在**第一次执行**时弹的，而一条命令要等到用户真去按侧键
 *  才会被执行 —— 于是授权框出现在用户看不到前因后果的时刻（按一下侧键，屏幕上多一个陌生的框）。
 *  在开关上先跑一次，授权就发生在「他刚刚说过这条要 root」的那一刻。
 *
 *  **与真执行走同一条链路**（[ShellRunner.exec]）：解释器、并发槽位、失败分档全同一套 ——
 *  这里说「拿到了」，按侧键时也一定拿得到；这里报没授权，按下去也是同一句提示。
 *
 *  成功怎么认：不新开回调通道，就用命令自己的回执（[ShellRunner.TIP_PREFIX]，`shell:` 动作本来
 *  就靠它报结果）。所以探测命令是 `id -u && echo 'tip: …'` —— `&&` 保证 `id -u` 没跑成时那行提示
 *  根本不出现，于是「有回执」= 「`su` 起来且真的拿到了 uid 0」。
 */
internal object RootGrant {
    /**
     * 申请一次；结果经 [onResult] 回（**后台线程**）。
     *
     * @param onResult `null` = 已拿到 root；否则是这次失败的分档（`SHELL_NO_ROOT` 就是没授权），
     *   文案交给调用方 —— 界面报的是它自己那一页的提示条，不是宿主进程的 Toast。
     */
    fun request(onResult: (CommandFailure?) -> Unit) {
        XLog.w("申请 root：以 su 跑一次探测命令（$PROBE）")
        ShellRunner.exec(
            command = PROBE,
            onFailure = { onResult(it) },
            onTip = { onResult(null) },
        )
    }

    /**
     * 探测命令：`id -u` 证明「`su` 起得来」且「拿到的真是 uid 0」（输出 `0`），
     * 后面那行 `echo` 是成功回执（见类注释）。
     */
    private val PROBE = "id -u && echo '${ShellRunner.TIP_PREFIX} root-granted'"
}
