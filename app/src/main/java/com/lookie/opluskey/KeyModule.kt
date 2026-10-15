package com.lookie.opluskey

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.DeviceSupport
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.host.GestureReceiver
import com.lookie.opluskey.host.SideKeyGestureHooks
import com.lookie.opluskey.inject.KeyHooks
import com.lookie.opluskey.inject.system.ProbeChannel
import com.lookie.opluskey.ui.SettingsPageHooks
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** 模块入口。 */
class KeyModule : XposedModule() {
    override fun onModuleLoaded(module: XposedModuleInterface.ModuleLoadedParam) {
        XLog.bind(this)
        val role =
            when {
                module.isSystemServer -> "侧键接管（system_server）"

                module.processName == PAGE_PROCESS -> "设置页替换（$PAGE_PROCESS）"

                // 宿主主进程的角色在 [onPackageReady] 里才落地，这里也要标出来 ——
                // 否则真机日志会把它打成「无关进程（不做任何事）」，排查时被误导（实测撞到）
                module.processName == HOST_PROCESS -> "宿主侧手势劫持与执行（$HOST_PROCESS）"

                else -> "无关进程（不做任何事）"
            }
        XLog.w("模块已加载：process=${module.processName} → $role")
        XLog.w(
            "本机机型：${DeviceSupport.describe(MODEL)}；系统：${DeviceSupport.systemOf(ANDROID, ROM)}" +
                "；允许运行=$deviceSupported",
        )
        if (!deviceSupported && module.processName != PAGE_PROCESS) {
            // 适配清单外的组合**只注入界面层**（见 [DeviceSupport]）——
            // 这一条是「为什么按了没反应」的第一判据行，必须是 w
            XLog.w("${whyNotInjecting()} ⇒ 本进程不注入（只保留界面层 $PAGE_PROCESS）")
        }
        if (!ENFORCE_DEVICE_GATE) {
            // 本包不做适配清单这道闸（只有 debug 会这样，见 `BuildFlags`）：上面那行照样打机型与
            //  系统版本，这里补一句为什么清单外的机器也「允许运行」—— 免得对着图排查时对不上。
            //  编译期常量 ⇒ release 里这一整块（含这条日志的字面量）都被折掉。
            XLog.w("本包不做适配清单这道闸 ⇒ 任何机型 / 系统版本都照常注入")
        }
    }

    /**
     * system_server 启动完成 —— 注入 system_server 的正确入口。
     * system_server 有专属回调，不需要（也没法）自己做进程判断；能力差异见
     * `docs/device-findings.md` 的「回调能力表」。
     */
    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        if (!deviceSupported) {
            // 适配清单外的组合：一个 Hook 都不挂。挂了就等于替一台没验过的 ROM 吞掉侧键，
            //  而后面那些反射找的类/方法在这版 ROM 上是真是假我们无从判断（见 [DeviceSupport]）。
            XLog.w("${whyNotInjecting()} ⇒ 不装按键 Hook（侧键不接管，只保留界面层）")
            return
        }
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
            PAGE_PROCESS -> {
                // 界面层**无论什么机型都装**：这是「不在适配清单里」时留给用户的唯一窗口
                // （页面会整页禁用，并说清是不认识的机型、还是这一版系统还没适配）。
                XLog.w("侧键设置页进程启动，开始安装换页 Hook")

                // 设置页进程崩了只死一个页面，但同样不把异常抛回宿主，最坏是页面回到系统原版。
                XLog.guard("安装设置页 Hook") {
                    SettingsPageHooks.install(this, param.classLoader)
                }
            }

            HOST_PROCESS -> {
                if (!deviceSupported) {
                    XLog.w("${whyNotInjecting()} ⇒ 不装宿主侧手势劫持与接收器（只保留界面层）")
                    return
                }
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
         * 桌面（`com.android.launcher`）—— **只为 debug 通道**而接的第二个落点。
         *
         * 理由：「前台 → 小窗」要拿到带 root 的窗口过渡，服务端的条件是「调用方 pid == 桌面进程 pid」
         * （`isStartFromLauncher()`，见 `docs/device-findings.md` 附九），所以必须能在**桌面进程里**
         * 执行代码。注意：它不参与任何产品功能；要不要写进 `scope.list` 由调试需要决定
         * （见 `docs/debug-channel.md`）。
         */
        const val LAUNCHER_PACKAGE = "com.android.launcher"

        /**
         * 模块自己的包名（= `build.gradle.kts` 的 `applicationId`），与 [PAGE_PACKAGE] 不是一回事：
         * 设置页跑在宿主进程里，`context.packageName` 拿到的是宿主包名，显示模块版本必须用这个
         * 包名去 `PackageManager` 查。硬编码是因为本工程关了 `BuildConfig`，拿不到 `APPLICATION_ID`。
         */
        const val MODULE_PACKAGE = "io.github.lo0kie.opluskey"

        /**
         * system_server → 宿主的手势转交广播（宿主私有目录 system_server 读不了，`/data/system`
         * 宿主写不了，广播是两端都能用的通道）。发送时必须 `setPackage(HOST_PROCESS)`：指定包名的
         * 广播不算隐式广播，也只有宿主注册的接收器能收。带的是判好的手势（判定挪回
         * system_server —— 宿主会被冻结、广播积压到解冻才灌，原始按键判定会错乱），宿主只负责执行。
         */
        const val ACTION_GESTURE = "com.lookie.opluskey.GESTURE"

        /** 广播里携带的键 id（`KeyId.id`，本模块只有 `plus`）。 */
        const val EXTRA_KEY = "key"

        /** 广播里携带的**手势**（`Gesture.id`：`single` / `double` / `long`）。 */
        const val EXTRA_PHASE = "phase"

        /** 广播里携带的「是否长按连续触发的重复派发」（执行侧据此跳过振动）。 */
        const val EXTRA_REPEAT = "repeat"

        /**
         * 广播携带的发送时刻（`SystemClock.elapsedRealtime()`，跨进程同源）。
         * 宿主据此丢弃冻结期间积压、解冻才送到的过期手势，判据见 `host/GestureReceiver` 的 `STALE_MS`。
         */
        const val EXTRA_SENT_AT = "sent_at"

        /**
         * 宿主进程 → 设置页：**宿主侧的模块已经加载**（由 `host/GestureReceiver` 在拿到 Context 后发出，
         * 那一步只有模块注入成功才跑得到）。
         *
         *  这条是首页「模块有没有生效」的**唯一可靠判据**：查宿主那支侧键服务是不行的 ——
         * 它是宿主自己的常驻服务，**系统会自己把它拉起**，服务在跑 ≠ 模块在跑。
         */
        const val ACTION_HOST_SERVICE_UP = "com.lookie.opluskey.HOST_SERVICE_UP"

        /**
         * 设置页 → 宿主进程：**问一声宿主侧的模块在不在**，宿主收到就回一条 [ACTION_HOST_SERVICE_UP]。
         *
         *  要这一问是因为广播是**一次性**的：宿主报活时设置页可能还没起来（那声就错过了）。
         */
        const val ACTION_HOST_SERVICE_QUERY = "com.lookie.opluskey.HOST_SERVICE_QUERY"

        /**
         * 设置页 → 宿主主进程：**「重启服务」请你等一下把设置页拉回来**（执行在 `host/PageRelaunch`）。
         *
         *  被杀掉的进程没法把自己叫回来，而「从进程外叫」那条路在真机上被后台启动策略拦掉
         *（闹钟挂 `PendingIntent` 拉起 Activity 会撞上 `Background activity launch blocked!`）；
         * 宿主主进程自己 `startActivity` 才放行 —— 本包拿着 `START_ACTIVITIES_FROM_BACKGROUND`。
         * 所以这一声必须发到**宿主主进程**（收方是 [GestureReceiver] 那一个接收器），不是宿主本身就算数。
         */
        const val ACTION_PAGE_RELAUNCH = "com.lookie.opluskey.PAGE_RELAUNCH"

        // ── 链路自检：设置页 ↔ 注入侧（system_server） ──
        //  这一对是**问 / 答**，只为「首页能不能说清是哪一段断了」而存在（见 `ui/state/LinkCheck.kt`）。
        //  注入侧不必为它额外挂 Hook：它已经挂在 AMS 的广播入口上，全系统每条广播都过那里；
        //  来源校验比的是 uid，而设置页与宿主**同 uid**（`docs/device-findings.md` 六之七）。

        /** 设置页 → 注入侧：**问一声注入侧那套装好没有**。 */
        const val ACTION_INJECT_QUERY = "com.lookie.opluskey.INJECT_QUERY"

        /**
         * 注入侧 → 设置页：回 [ACTION_INJECT_QUERY] 的状态包。
         *
         *  注意：**不要把这个 action 加进注入侧 `KeyHooks.readBroadcast` 的过滤表** ——
         * 那会让注入侧去处理自己发出去的包（多绕一圈，且毫无意义）。
         */
        const val ACTION_INJECT_READY = "com.lookie.opluskey.INJECT_READY"

        /** [ACTION_INJECT_READY] 里带「装上几个侧键接管点」（0 = 一个都没装上）。 */
        const val EXTRA_INJECT_HOOKS = "inject_hooks"

        /** [ACTION_INJECT_READY] 里带「注入侧收到过配置没有」（false ⇒ 注入侧还在用默认值顶着）。 */
        const val EXTRA_INJECT_CONFIG_OK = "inject_config_ok"

        /**
         * [ACTION_INJECT_READY] 里带「注入侧最后一次判出手势是什么时候」。
         *
         * `SystemClock.elapsedRealtime()` —— 跨进程同源，页面直接与自己的时钟相减就是「多久以前」。
         * 0 = 开机以来从未判出过（侧键一次都没被接管）。
         */
        const val EXTRA_INJECT_LAST_GESTURE_AT = "inject_last_gesture_at"

        /**
         * [ACTION_INJECT_READY] 里带「注入侧手上那份配置的指纹」（见 `ConfigBroadcast.tag`）。
         *
         *  设置页拿它与自己手上那份比：**相等才叫「设置生效」**。「收到过一条」不等于「就是最新那份」——
         *  开机收到过之后哪条广播丢了，注入侧还按旧配置跑，只回一个布尔的话界面照样报「正常」。
         *  还没收到过配置时这一项为空串。
         */
        const val EXTRA_INJECT_CONFIG_TAG = "inject_config_tag"

        /** [ACTION_HOST_SERVICE_UP] 里带「宿主读到的主开关真值」——页面据此判它是不是读到了真配置。 */
        const val EXTRA_HOST_ENABLED = "host_enabled"

        /** [ACTION_HOST_SERVICE_UP] 里带「宿主最后一次执行动作是什么时候」（同上，0 = 从未）。 */
        const val EXTRA_HOST_LAST_ACTION_AT = "host_last_action_at"

        /**
         * 每次出包手动更新；CI 靠它给产物命名（`build.yml` 的 `dist` 那一步）。
         *
         * 它还是**构建时刻读不到时的回退值**（`ui/screen/HomeScreen` 的 `moduleBuildStamp`）
         * —— 那时界面版本行显示的整串就是它，所以它自己带着日期。
         */
        const val BUILD_ID = "2026-10-15-0001"

        /**
         * 本机机型（`android.os.Build.MODEL`）。
         *
         * 注意：用的是 `MODEL` **不是** `ro.product.device`：真机上 `MODEL` = `PLK110`、
         * `DEVICE` = `OP60FFL1`，适配清单按前者写（见 [DeviceSupport]）。各进程读到的都一样。
         */
        private val MODEL: String = android.os.Build.MODEL

        /** 本机 Android 版本（`android.os.Build.VERSION.RELEASE`，真机是 `17`）。 */
        private val ANDROID: String = android.os.Build.VERSION.RELEASE

        /**
         * 本机 ROM 版本号 —— 从 `android.os.Build.DISPLAY`（`ro.build.display.id`）里取。
         *
         * 不读 `ro.build.version.oplusrom`：那是 hidden 的 `SystemProperties`，宿主进程里会被
         * 非 SDK 接口限制拦下（界面层与注入侧都要判这一项），而 `Build.DISPLAY` 是公开 API。
         * 取法见 [DeviceSupport.romSeriesOf]。
         */
        private val ROM: String = DeviceSupport.romSeriesOf(android.os.Build.DISPLAY)

        /**
         * 本机「机型 + 系统版本」这一组在不在**适配清单**里（见 [DeviceSupport]）——
         * **决定「注入整套」还是「只注入界面层」**。
         *
         * 只在进程启动时算一次：机型与系统版本都不会中途变。清单外的组合，注入侧与宿主侧的 Hook
         * 一个都不挂，只保留设置页替换（[PAGE_PROCESS]）。
         */
        private val support: DeviceSupport.Support = DeviceSupport.check(MODEL, ANDROID, ROM)

        /** 清单命中的简写（判定本体是 [support]，这里只是省掉各处比枚举）。 */
        private val deviceSupported: Boolean = support == DeviceSupport.Support.ADAPTED

        /**
         * 「为什么没注入」那一句 —— 机型不认识、还是这一版系统没适配，两档分开说：
         * 前者是「本机用不了」，后者是「等作者适配」，用户该做的事完全不同。
         */
        private fun whyNotInjecting(): String =
            when (support) {
                DeviceSupport.Support.PENDING -> "机型在适配清单里，但系统版本不是那一档（等待适配）"
                else -> "机型不在适配清单里"
            }

        /**
         * 取当前进程名。不用 hidden 的 `ActivityThread.currentProcessName()`（不同版本行为不一），
         * 直接读 `/proc/self/cmdline`，对任何进程都成立。
         */
        private fun currentProcessName(): String? =
            try {
                java.io
                    .File("/proc/self/cmdline")
                    .readText()
                    .trimEnd('\u0000')
                    .trim()
            } catch (e: Throwable) {
                XLog.w("读 /proc/self/cmdline 失败，无法判断进程名", e)
                null
            }
    }
}
