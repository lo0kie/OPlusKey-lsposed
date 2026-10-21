package com.lookie.opluskey

import com.lookie.opluskey.core.DEBUG_PROBE
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.DeviceSupport
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.MiniWindowGeometry
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.seal.Seal
import com.lookie.opluskey.core.seal.Verdict
import com.lookie.opluskey.host.GestureReceiver
import com.lookie.opluskey.host.SideKeyGestureHooks
import com.lookie.opluskey.inject.KeyHooks
import com.lookie.opluskey.inject.ProbeChannel
import com.lookie.opluskey.ui.hook.SettingsPageHooks
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** 模块入口。 */
class KeyModule : XposedModule() {
    override fun onModuleLoaded(module: XposedModuleInterface.ModuleLoadedParam) {
        XLog.bind(this)
        // 这一支原先没包 guard，而它首次触发 DeviceSupport / RomProfile / MiniWindowGeometry
        //  的 object 初始化并拼串 —— 任何 Error 都会顺回调抛回框架（system_server 那条路径上
        //  就是开机循环）。与其余三个回调同口径。
        XLog.guard("模块加载日志") {
            val role =
                when {
                    module.isSystemServer -> "侧键接管（system_server）"

                    module.processName == HostApp.PAGE_PROCESS -> "设置页替换（${HostApp.PAGE_PROCESS}）"

                    // 宿主主进程的角色在 [onPackageReady] 里才落地，这里也要标出来 ——
                    // 否则真机日志会把它打成「无关进程（不做任何事）」，排查时被误导（实测撞到）
                    module.processName == HostApp.PACKAGE -> "宿主侧手势劫持与执行（${HostApp.PACKAGE}）"

                    else -> "无关进程（不做任何事）"
                }
            XLog.w("模块已加载：process=${module.processName} → $role")
            XLog.w(
                "本机机型：${DeviceSupport.describe(
                    DeviceSupport.currentModel,
                )}；系统：${DeviceSupport.systemOf(DeviceSupport.currentAndroid, DeviceSupport.currentRomSeries)}" +
                    "；适配清单=${if (DeviceSupport.isAdapted) "已适配" else "未适配" }",
            )
            if (!DeviceSupport.isAdapted) {
                // **不再据此拒绝注入**（适配清单现在只是「验过没有」的提示）：这一行留给排查 ——
                //  清单外的机器上「按了没反应」时，先看这里是不是它。
                XLog.w("${DeviceSupport.whyNotInjecting()} ⇒ 照常注入（清单外的机器可能不生效）")
            }
            // 档位 —— 与上面那行**分开报**：清单管「这一组验过没有」，档位表管「注入之后那些落点
            //  认不认得」。真机报「按了没反应」时，这几句一起看就知道断在哪一环。
            XLog.w(
                "适配档位：内置命令=${RomProfile.describeCurrent()}；" +
                    "小窗几何=${MiniWindowGeometry.describeCurrent()}",
            )
        }
    }

    /**
     * system_server 启动完成 —— 注入 system_server 的正确入口。
     * system_server 有专属回调，不需要（也没法）自己做进程判断；能力差异见
     * `docs/device-findings.md` 的「回调能力表」。
     */
    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
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
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val process = currentProcessName()
        when (process) {
            HostApp.PAGE_PROCESS -> {
                // 界面层**无论什么机型都装**：清单外的机器也照常进来改配置（页面不再整页禁用，
                //  只会在「更多选项」里提示这一组没验过）。
                XLog.w("侧键设置页进程启动，开始安装换页 Hook")

                // 设置页进程崩了只死一个页面，但同样不把异常抛回宿主，最坏是页面回到系统原版。
                XLog.guard("安装设置页 Hook") {
                    SettingsPageHooks.install(this, param.classLoader)
                }
            }

            HostApp.PACKAGE -> {
                if (sealRejected(param.classLoader)) return
                // 宿主主进程：框架判好的侧键手势派发到这里，执行也放这一侧（配置同 uid 直接读得到）。
                XLog.w("侧键宿主主进程启动，开始安装手势劫持 Hook")
                XLog.guard("安装手势劫持 Hook") {
                    SideKeyGestureHooks.install(this, param.classLoader)
                }
                XLog.guard("安装手势接收器") {
                    GestureReceiver.install(param.classLoader)
                }
            }

            LAUNCHER_PACKAGE -> {
                // 桌面进程：**只为 debug 通道**而接的第二个落点（不参与任何产品功能）。
                // 「前台 → 小窗」那条要拿到带 root 的窗口过渡，服务端的条件是「调用方 pid == 桌面进程 pid」，
                // 所以必须能在桌面进程里执行代码 —— 见 docs/debug-channel.md 与 device-findings 附九。
                // release（`DEBUG_PROBE=false`）里这条通道整个不存在：连日志都不打 ——
                // 否则 release 的日志页会出现「桌面进程启动，装 debug 通道落点」，
                // 而实际上一个落点都没装，排查时会被这句话带偏。
                if (DEBUG_PROBE) {
                    XLog.w("桌面进程启动，装 debug 通道落点")
                    XLog.guard("在桌面进程装 debug 通道") {
                        ProbeChannel.installInProcess(param.classLoader, LAUNCHER_PACKAGE)
                    }
                }
            }

            else -> {
                // 其余进程什么都不做，也不打日志 —— 否则日志会被刷满。
            }
        }
    }

    /**
     * 签名闸：本包签名与**出厂指纹**不符（= 被改过并重签）⇒ **不装 Hook**。
     *
     *  拦的是「改包再发」那条路：换掉收款码 / 换掉别的东西重签名，包就再也解不开自己那份封存资产
     *  （见 `core/seal/Seal`），这里再把功能一并停掉 —— 一个被改过的包，装上去也只是块砖。
     *
     *  **只在这一处（宿主主进程）判**：`system_server` 那次注入（[onSystemServerStarting]）此刻拿不到
     *  可用的 Context —— `SystemContext.get` 明说了「安装阶段不要调」（那时还没有别的 App attach 过，
     *  借不到真的那份，调了只会把降级品缓存下来，反而把广播弄坏）。而宿主进程这里 `currentApplication`
     *  可能还是 null（Application 尚未挂上），那时**不拦** —— 交给 `host/GestureReceiver` 那一道：
     *  它在重试到 Context 之后判，必得着。**宁可晚一步，也不把「读不到」当成「被改过」。**
     *
     *  debug 包里这一整段是死代码（[Seal.verdict] 直接返回 OFFICIAL，见 debug 源集那份 `SealPin`）。
     */
    private fun sealRejected(loader: ClassLoader): Boolean {
        val context = ActivityThreadCompat.currentApplication(loader) ?: return false
        if (Seal.verdict(context) != Verdict.UNOFFICIAL) return false
        XLog.e("本包签名与出厂指纹不符 ⇒ 不装 Hook（见「关于 → 签名」；换回官方包即恢复）")
        return true
    }

    companion object {
        /**
         * 桌面（`com.android.launcher`）—— **只为 debug 通道**而接的第二个落点。
         *
         * 理由：「前台 → 小窗」要拿到带 root 的窗口过渡，服务端的条件是「调用方 pid == 桌面进程 pid」
         * （`isStartFromLauncher()`，见 `docs/device-findings.md` 附九），所以必须能在**桌面进程里**
         * 执行代码。注意：它不参与任何产品功能；要不要写进 `scope.list` 由调试需要决定
         * （见 `docs/debug-channel.md`）。
         */
        const val LAUNCHER_PACKAGE = "com.android.launcher"

        /**
         * 取当前进程名。不用 hidden 的 `ActivityThread.currentProcessName()`（不同版本行为不一），
         * 直接读 `/proc/self/cmdline`，对任何进程都成立。
         *
         * `/proc/<pid>/cmdline` 的格式是**参数逐个用 `\0` 隔开、末尾再补一个 `\0`**。进程名只是第一段，
         * 后面的 argv 与它无关。所以必须**截到第一个 `\0` 为止**，不能只削尾部：以
         * `com.oplus.gesture\0--some-arg\0` 为例，削尾会留下 `com.oplus.gesture\0--some-arg`，
         * 与包名比对永远不相等 —— 表现是「进程明明在，却一个 Hook 都没装」，且不报错。
         *
         * 用 `takeWhile` 而不是 `split('\0').first()`：后者会为整段 argv 建一个 List，
         * 而这里只要前缀。空 cmdline（内核线程）得到空串，调用方走 `else ->` 分支，符合预期。
         */
        private fun currentProcessName(): String? =
            try {
                java.io
                    .File("/proc/self/cmdline")
                    .readText()
                    .takeWhile { it != '\u0000' }
                    .trim()
            } catch (e: Throwable) {
                XLog.w("读 /proc/self/cmdline 失败，无法判断进程名", e)
                null
            }
    }
}
