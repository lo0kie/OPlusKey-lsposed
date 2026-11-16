package com.lookie.opluskey

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.shizuku.ShizukuShellService

/**
 * 把「后台通道」立起来 —— [ShizukuShellService] 常驻那一档的**唯一入口**。
 *
 * ## 只有一个入口的原因
 *
 *  这条通道能成立，靠的全是「本进程一直在跑」；而 ColorOS 的启动管理不放任何调用方拉起它
 *  （连 system_server 都被拒，实测与反编译见 `docs/device/lsposed.md` 四十二）—— 能起它的只有
 *  本应用自己在前台的那一刻（[EntryActivity]）和系统广播（[BootReceiver]）。两处都走这里，
 *  免得「服务该怎么起」这件事在两处各写一遍。
 *
 * ## 通知的文案资源为什么在这里给
 *
 *  `shizuku/` 那一档只允许认识 `core`：直接在那里写 `R.xxx` 就等于依赖 `root` 包，会造出
 *  `root → host → shizuku → root` 的包级环（`tools/audit-coupling.py` 拦的就是这条）。
 *  所以由这里把资源 id 塞进 Intent，服务那边只负责用（见 `ShizukuShellService.promoteForeground`）。
 */
internal object BackgroundChannel {
    /**
     * 起一次（重复调用是幂等的：同一个服务、同一条通知）。
     *
     * @return 请求交给系统了没有 —— **不表示服务已经起来**（后台起前台服务仍可能被系统拒）。
     */
    fun start(context: Context): Boolean =
        try {
            context.startForegroundService(
                Intent(context, ShizukuShellService::class.java)
                    .putExtra(ShizukuShellService.EXTRA_ICON, R.drawable.ic_module_foreground)
                    .putExtra(ShizukuShellService.EXTRA_TITLE, R.string.app_name)
                    .putExtra(ShizukuShellService.EXTRA_TEXT, R.string.keep_text),
            )
            XLog.w("后台通道：已请它常驻（宿主之后可以直接 bind）")
            true
        } catch (e: Throwable) {
            XLog.w("后台通道：起不来（${e.javaClass.simpleName}: ${e.message}）")
            false
        }
}
