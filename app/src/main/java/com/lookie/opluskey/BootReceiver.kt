package com.lookie.opluskey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.log.XLog

/**
 * 开机 / 换包之后把「后台通道」立起来（[ShizukuShellService] 的常驻档）。
 *
 * ## 为什么需要它
 *
 *  ColorOS 的启动管理不许任何调用方拉起本应用的进程（连 system_server 都拒，实测见
 *  `docs/device/lsposed.md` 四十二），所以侧键那条 Shizuku 命令的前提是**本进程本来就在跑**。
 *  前台服务活不过重启，`EntryActivity` 又要用户手动打开一次 —— 这条广播是唯一能自己补上的时机：
 *  `BOOT_COMPLETED` 属于「允许从后台起前台服务」的系统例外，而 ColorOS 那一侧的判据是「自启动」，
 *  用户在本应用的启动管理里打开它即可。
 *
 * ## 两条 action
 *
 *  · `BOOT_COMPLETED`：重启后补上。
 *  · `MY_PACKAGE_REPLACED`：换包（`adb install -r` 也是）之后补上 —— 换包会杀掉进程，
 *    否则用户装完新版本就发现侧键那条 Shizuku 命令又失效了。
 *
 *  两条都只做一件事：把 [ShizukuShellService] 起成常驻前台服务。失败只记日志 ——
 *  最坏是回到「打开一次本应用」那条手动路径。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        XLog.w("后台通道：开机/换包后补一次（$action）")
        BackgroundChannel.start(context)
    }
}
