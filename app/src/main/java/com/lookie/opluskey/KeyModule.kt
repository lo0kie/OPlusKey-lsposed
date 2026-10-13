package com.lookie.opluskey

import com.lookie.opluskey.gesture.GestureReceiver
import com.lookie.opluskey.gesture.SideKeyGestureHooks
import com.lookie.opluskey.hook.KeyHooks
import com.lookie.opluskey.page.SettingsPageHooks
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** 模块入口。 */
class KeyModule : XposedModule() {

    override fun onModuleLoaded(
        module: XposedModuleInterface.ModuleLoadedParam,
    ) {
        XLog.bind(this)
        val role = when {
            module.isSystemServer -> "侧键接管（system_server）"
            module.processName == PAGE_PROCESS -> "设置页替换（$PAGE_PROCESS）"
            else -> "无关进程（不做任何事）"
        }
        XLog.i("模块已加载：process=${module.processName} → $role")
    }

    /**
     * system_server 启动完成 —— 注入 system_server 的正确入口。
     * system_server 有专属回调，不需要（也没法）自己做进程判断；能力差异见
     * `docs/device-findings.md` 的「回调能力表」。
     */
    override fun onSystemServerStarting(
        param: XposedModuleInterface.SystemServerStartingParam,
    ) {
        XLog.w("system_server 启动，开始安装按键 Hook")

        // 最后一道保险：任何 Throwable（含 Error 系）都不能漏出去 —— 漏出去就是开机循环。
        XLog.guard("安装按键 Hook") {
            KeyHooks.install(this, param.classLoader)
        }
    }

    /**
     * 普通进程加载完成 —— 用来接住设置页进程与宿主主进程。判断依据用进程名而不是包名：
     * 宿主把设置页放进了独立进程 `com.oplus.gesture.ui`，与 `com.oplus.gesture` 同包不同进程。
     */
    override fun onPackageReady(
        param: XposedModuleInterface.PackageReadyParam,
    ) {
        val process = currentProcessName()
        when (process) {
            PAGE_PROCESS -> {
                XLog.w("侧键设置页进程启动，开始安装换页 Hook")

                // 设置页进程崩了只死一个页面，但同样不把异常抛回宿主，最坏是页面回到系统原版。
                XLog.guard("安装设置页 Hook") {
                    SettingsPageHooks.install(this, param.classLoader)
                }
            }

            HOST_PROCESS -> {
                // 宿主主进程：框架判好的侧键手势派发到这里，执行也放这一侧（配置同 uid 直接读得到）。
                XLog.w("侧键宿主主进程启动，开始安装手势劫持 Hook")
                XLog.guard("安装手势劫持 Hook") {
                    SideKeyGestureHooks.install(this, param.classLoader)
                }
                XLog.guard("安装手势接收器") {
                    GestureReceiver.install(param.classLoader)
                }
            }

            else -> {
                // 其余进程什么都不做，也不打日志 —— 否则日志会被刷满。
            }
        }
    }

    companion object {
        /**
         * 侧键设置页实际所在的进程名（`ActionButtonSettingsActivity` 的 `android:process` 属性）。
         * 是进程名不是包名，**不要**写进 `scope.list`；用于 [onPackageReady] 从同包多进程中挑出设置页。
         */
        const val PAGE_PROCESS = "com.oplus.gesture.ui"

        /**
         * 侧键手势的落点进程：宿主 App 主进程。框架把判好的手势 bind 给场景宿主，而宿主的
         * 两个服务（`quicklaunch.StartAppService` / `server.ActionButtonKeyService`）都声明在
         * 默认进程里 —— 手势劫持装在这一侧（见 [SideKeyGestureHooks]）。
         */
        const val HOST_PROCESS = "com.oplus.gesture"

        /** 设置页宿主包名（`scope.list` 里写的就是它）。 */
        const val PAGE_PACKAGE = "com.oplus.gesture"

        /**
         * 模块自己的包名（= `build.gradle.kts` 的 `applicationId`），与 [PAGE_PACKAGE] 不是一回事：
         * 设置页跑在宿主进程里，`context.packageName` 拿到的是宿主包名，显示模块版本必须用这个
         * 包名去 `PackageManager` 查。硬编码是因为本工程关了 `BuildConfig`，拿不到 `APPLICATION_ID`。
         */
        const val MODULE_PACKAGE = "io.github.lo0kie.opluskey"

        /**
         * system_server → 宿主的手势转交广播（宿主私有目录 system_server 读不了，`/data/system`
         * 宿主写不了，广播是两端都能用的通道）。发送时必须 `setPackage(HOST_PROCESS)`：指定包名的
         * 广播不算隐式广播，也只有宿主注册的接收器能收。带的是判好的手势（2026-10-12 起判定挪回
         * system_server —— 宿主会被冻结、广播积压到解冻才灌，原始按键判定会错乱），宿主只负责执行。
         */
        const val ACTION_GESTURE = "com.lookie.opluskey.GESTURE"

        /** 广播里携带的键 id（`KeyId.id`，本模块只有 `plus`）。 */
        const val EXTRA_KEY = "key"

        /** 广播里携带的**手势**（`Gesture.id`：`single` / `double` / `long`，或 [PHASE_PASSTHROUGH]）。 */
        const val EXTRA_PHASE = "phase"

        /** 广播里携带的「是否长按连续触发的重复派发」（执行侧据此跳过振动）。 */
        const val EXTRA_REPEAT = "repeat"

        /**
         * 广播携带的发送时刻（`SystemClock.elapsedRealtime()`，跨进程同源）。
         * 宿主据此丢弃冻结期间积压、解冻才送到的过期手势，判据见 `gesture/GestureReceiver` 的 `STALE_MS`。
         */
        const val EXTRA_SENT_AT = "sent_at"

        /** 手势：放行（单击 / 双击 / 长按配了 `native` / `passthrough`）—— 宿主重新注入一次按键。 */
        const val PHASE_PASSTHROUGH = "passthrough"

        /** 每次出包手动更新；CI 靠它给产物命名。 */
        const val BUILD_ID = "2026-10-13-0002"

        /**
         * 取当前进程名。不用 hidden 的 `ActivityThread.currentProcessName()`（不同版本行为不一），
         * 直接读 `/proc/self/cmdline`，对任何进程都成立。
         */
        private fun currentProcessName(): String? = try {
            java.io.File("/proc/self/cmdline").readText().trimEnd('\u0000').trim()
        } catch (e: Throwable) {
            XLog.w("读 /proc/self/cmdline 失败，无法判断进程名", e)
            null
        }
    }
}
