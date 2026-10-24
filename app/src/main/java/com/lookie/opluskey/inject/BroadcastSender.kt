package com.lookie.opluskey.inject

import com.lookie.opluskey.bridge.inject.SystemContext
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp

// 「这条广播是谁发的」—— 从 `KeyHooks` 分出来一支：那三支判据（宿主 / root-system / uid 缓存）
// 只在广播入口用，和「装 Hook、接管按键」不是一件事；分开之后 `KeyHooks` 那个 object 的函数数量
// 也回到 detekt 的 `TooManyFunctions` 之下。

/**
 * 广播来源校验 —— 功能通道只认**宿主**，debug 通道只认 **root / system**。
 *
 *  不校验来源的话，任意 App 发一条同名广播就能以 system 身份开最近任务、翻主开关、
 *  改长按 / 双击时序。
 */
internal object BroadcastSender {
    /**
     * 这条广播是不是宿主进程发出来的。
     *
     *  两条通道都只有宿主 / 设置页（同 uid）会发；在原方法体跑之前读 `Binder.getCallingUid()`
     *  拿到的就是发送方 uid，宿主 uid 解析一次后缓存。
     *  **解析不到时丢弃（fail-closed）**：放行等于任意 App 都能伪造这两条通道，而配置广播只在
     *  启动 / 解锁 / 写盘时发，下一轮会重取 uid，不会因一次失败把通道永久锁死。
     */
    fun isFromHost(loader: ClassLoader?): Boolean {
        val calling = android.os.Binder.getCallingUid()
        val host = hostUid(loader)
        if (host <= 0) {
            // fail-closed：宿主 uid 拿不到时丢弃 —— 放行等于任意 App 可伪造这两条通道
            // （翻主开关 / 以 system 身份开最近任务）。配置广播只在启动 / 解锁 / 写盘时发，
            // 下一轮会重取 uid，不会因一次失败把通道锁死。
            XLog.w("宿主 uid 未解析到，丢弃广播（fail-closed，下轮重取）")
            return false
        }
        if (calling != host) {
            XLog.w("丢弃来源不明的广播：callingUid=$calling（宿主 uid=$host）")
            return false
        }
        return true
    }

    /**
     * 这条广播**该不该处理** —— 两个入口收成一个：功能通道只认宿主，debug 通道只认 root / system。
     *
     *  调用点原本写成 `if (if (isProbe) !isFromRootOrSystem() else !isFromHost(loader)) return`
     *  （一行两个否定 + 一个三目），既难读又把 `isFromHost` 的函数长度顶过了 detekt 的
     *  `LongMethod`。这里一个名字说清整件事。
     */
    fun isTrusted(
        probe: Boolean,
        loader: ClassLoader?,
    ): Boolean = if (probe) isFromRootOrSystem() else isFromHost(loader)

    /**
     * debug 通道（[ProbeChannel]）的来源校验：只放行 **root / system**。
     *
     * 与 [isFromHost] 分开是因为它的发送方**本来就不是宿主** —— 调试时是
     * `adb shell su -c am broadcast`，没有宿主身份可用。普通 App（uid ≥ 10000）依旧进不来，
     * 而且整个通道只在 debug 包存在（`DEBUG_PROBE` 是编译期常量，release 里这些代码全被折掉）。
     */
    fun isFromRootOrSystem(): Boolean {
        val calling = android.os.Binder.getCallingUid()
        if (calling == 0 || calling == android.os.Process.SYSTEM_UID) return true
        XLog.w("debug 通道：丢弃非 root/system 来源（callingUid=$calling）")
        return false
    }

    /** 宿主包的 uid，解析成功后缓存（-1 = 还没解析到；失败不缓存，下一轮广播重取）。 */
    @Volatile
    private var cachedHostUid: Int = -1

    private fun hostUid(loader: ClassLoader?): Int {
        val cached = cachedHostUid
        if (cached != -1) return cached
        val uid =
            try {
                val ctx = SystemContext.get(loader ?: return -1) ?: return -1
                ctx.packageManager.getPackageUid(HostApp.PACKAGE, 0)
            } catch (e: Throwable) {
                XLog.w("解析宿主 uid 失败（这次丢弃，下轮重取）：${e.message}")
                -1
            }
        if (uid > 0) {
            cachedHostUid = uid
            XLog.w("宿主 uid = $uid（广播来源校验用）")
        }
        return uid
    }
}
