package com.lookie.opluskey.inject

import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.bridge.FlexibleWindowBridge
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.bridge.StatusBarBridge
import com.lookie.opluskey.bridge.hookGuarded
import com.lookie.opluskey.bridge.inject.SystemContext
import com.lookie.opluskey.core.DEBUG_PROBE
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.core.wire.ConfigBroadcast
import com.lookie.opluskey.core.wire.LogWire
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.inject.input.GestureForwarder
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/** 侧键 Hook 的安装入口（本模块只接管侧键）。 */
internal object KeyHooks {
    @Volatile
    private var installed = false

    /** 已装上的 Hook 数（诊断用）。 */
    @Volatile
    var hookCount: Int = 0
        private set

    /** system_server 的 ClassLoader —— 状态栏指令反射用。 */
    @Volatile
    private var systemLoader: ClassLoader? = null

    /**
     * 最后一次**判出手势**的时刻（`elapsedRealtime()`，0 = 开机以来一次都没有）。
     *
     * 这是链路自检里唯一一条**功能性**证据：接管点装上只说明反射找对了方法，
     * 说明不了这版 ROM 的侧键事件真的走到那里 —— 只有真判出过一次才算端到端通了。
     */
    @Volatile
    var lastGestureAt: Long = 0L
        private set

    /** 判定侧每判出一个手势记一笔（装配时以回调注入判定器，见 `SideKeyInterceptor`）。 */
    fun noteGesture() {
        lastGestureAt = android.os.SystemClock.elapsedRealtime()
    }

    /** 注入侧的主线程 Handler —— 判定定时器与转发都走它。 */
    private val handler: Handler = Handler(Looper.getMainLooper())

    /**
     * 安装侧键 Hook。
     *
     * 每个接管点独立安装 —— 一个装不上不影响其他。
     */
    fun install(
        module: XposedModule,
        loader: ClassLoader,
    ) {
        if (installed) {
            XLog.w("已经装过，跳过重复安装")
            return
        }

        systemLoader = loader

        XLog.w("主开关：等宿主上报（上报前按「接管」处理，见 ConfigChannel 类注释）")

        // 三张表（适配清单 / 接管点候选 / 落点表）的对账 —— 判据级，装没装对不上时先看这一行。
        //  加一版 ROM 要往三处各加一行，漏一处不会编译报错，表现为三种都不好查的故障（见那边的说明）。
        //  包一层 guard：它跑在安装路径上，任何一环出错都只能记日志，绝不许连带把安装整条打断（五、1）。
        XLog.guard("ROM 档位对账") { XLog.w(Framework.describeTables()) }

        XLog.guard("挂广播入口") { installBroadcastHook(module, loader) }

        val points = Framework.findSideKeyPoints(loader)
        if (points.isEmpty()) {
            XLog.w(
                "没找到任何侧键接管点 —— 本机 ROM 可能不是 ColorOS，或侧键实现改了名。" +
                    "模块不生效，但不影响系统",
            )
            return
        }

        // 借 Context 的时机推迟到真正转交手势那一刻：安装阶段还没有 App attach 过，只能借到降级品。
        val forwarder = GestureForwarder({ SystemContext.get(loader) }, handler)
        // 「自定义按键」页那条旁路监听同样推迟取 Context（只在真要上报时才借，见 `KeyWatch`）
        KeyWatch.install { SystemContext.get(loader) }
        // 触觉反馈**不在这边**：它在宿主侧、动作即将执行那一刻（`host/GestureReceiver.runAction`）。
        //  判定侧振不出「服务在跑」这个事实 —— 判出手势就振，可宿主在不在、能不能执行它并不知道，
        // 于是会出现「振了却什么都没发生」；用户明确要求把它去掉（见那边的 KDoc）。
        val interceptor =
            SideKeyInterceptor(
                handler,
                forward = { id, gesture, repeat ->
                    forwarder.forward(id, gesture, repeat)
                },
                onGestureNoted = ::noteGesture,
                onRelease = { id ->
                    forwarder.release(id)
                },
            )
        // 主开关翻到「不接管」时撤销已排下的手势（长按枪 / 双击窗 / 连发任务）。
        //  撤销只能由「收到配置广播」触发，不能挂在拦截点那条 early return 上（按住不放时
        //  没有新事件进来）—— 回调在 AMS 广播 hook 内跑，其内部已包 catch（见 ConfigChannel.read）。
        ConfigChannel.onTakeOverOff = { interceptor.cancelPending() }

        var ok = 0
        for (point in points) {
            val handle =
                module.hookGuarded(point.method) { chain ->
                    onIntercept(interceptor, point, chain)
                }
            if (handle != null) {
                ok++
                XLog.w(
                    "已 Hook 侧键接管点：$point" +
                        "（kind=${point.kind}，参数 ${point.method.parameterTypes.size} 个）",
                )
            }
        }

        hookCount = ok
        installed = ok > 0
        // 必须 W 而非 i：注入侧 i 会被 LSPosed 进程级日志级别整档丢掉、release 里更被编译期消除
        // —— 这一行是「装没装上」的唯一分水岭，用 i 打就等于彻底静默。
        if (ok == 0) {
            XLog.w("侧键 Hook 一个都没装上：0/${points.size} 个接管点 —— 侧键完全不被接管")
        } else {
            XLog.w("侧键 Hook 安装完成：$ok/${points.size} 个接管点")
        }
    }

    /**
     * 挂 AMS 的广播入口。热路径（系统每秒都在发广播），回调里必须极便宜：
     * 只做「遍历实参 → 比对 action」，命中才解析，其余原样 `chain.proceed()`。
     *
     * @return 装上几个方法（`broadcastIntentWithFeature` / `broadcastIntent` 可能各有一个重载）
     */
    private fun installBroadcastHook(
        module: XposedModule,
        loader: ClassLoader,
    ): Int {
        // 类名与方法名一律来自 `Framework` 的候选表（一、7）：这里只负责装，不认名字。
        val points = Framework.findBroadcastPoints(loader)
        if (points.isEmpty()) {
            // 广播入口没挂上 ⇒ 主开关与状态栏指令两条通道整体失效（侧键照旧被吞但无动作），
            // 必须 W 级且把后果写清楚，别只写一个数字。
            XLog.w(
                "广播入口 Hook 一个都没装上（AMS 上没有 broadcastIntent*）—— " +
                    "「主开关」「状态栏指令」「小窗指令」三条通道整体失效：侧键被吞但没有任何动作",
            )
            return 0
        }
        var n = 0
        for (m in points) {
            if (module.hookGuarded(m) { chain ->
                    readBroadcast(chain.args)
                    chain.proceed()
                } != null
            ) {
                n++
            }
        }
        if (n < points.size) {
            XLog.w("广播入口 Hook 只装上 $n/${points.size} 个（其余装失败，对应通道会缺一条）")
        } else {
            XLog.w("广播入口 Hook：$n 个（AMS）")
        }
        return n
    }

    /**
     * 回一条「注入侧链路状态」给设置页（应答 [ModuleWire.ACTION_INJECT_QUERY]）。
     *
     * 注意：**绝不能在 hooker 体内直接 `sendBroadcast`**：那是从 AMS 的广播入口内部再调一次 AMS，
     * 等于在广播路径里重入广播路径。整条投递 post 到主线程去发，hooker 里只留这一次 post。
     */
    private fun reportStatus() {
        val loader = systemLoader ?: return
        handler.post {
            try {
                val ctx =
                    SystemContext.get(loader) ?: run {
                        // 借不到 Context 就回不了包 —— 页面那边会按「注入侧未就绪」显示（宁可误报也不能乱报）
                        XLog.w("链路自检：借不到 Context，回不了包")
                        return@post
                    }
                ctx.sendBroadcast(
                    Intent(ModuleWire.ACTION_INJECT_READY)
                        .setPackage(HostApp.PACKAGE)
                        .putExtra(ModuleWire.EXTRA_INJECT_HOOKS, hookCount)
                        .putExtra(ModuleWire.EXTRA_INJECT_CONFIG_OK, ConfigChannel.receivedConfig)
                        .putExtra(ModuleWire.EXTRA_INJECT_LAST_GESTURE_AT, lastGestureAt)
                        .putExtra(
                            ModuleWire.EXTRA_INJECT_CONFIG_TAG,
                            ConfigChannel.configTag ?: "",
                        )
                        // 「我跑的是哪一次构建」—— 页面拿它判「换包后没重启」（见那个常量的说明）
                        .putExtra(ModuleWire.EXTRA_INJECT_BUILD_ID, ModuleInfo.BUILD_ID),
                )
                XLog.w(
                    "链路自检：已回包（接管点=$hookCount 配置=${ConfigChannel.receivedConfig}" +
                        " 指纹=${ConfigChannel.configTag ?: "无"} 接管=${ConfigChannel.takeOver}）",
                )
            } catch (e: Throwable) {
                XLog.w("链路自检：回包失败：${e.message}")
            }
        }
    }

    /**
     * 从广播调用的实参里挑出关心的那几条，命中就处理。每次广播都会走这里，不要
     * `filterIsInstance`（会建临时集合）—— 一趟 for 找 Intent、比过 action，命中才解析。
     * 来源要校验：挂在 AMS 的广播入口上，全系统每条广播都进来，光比 action 挡不住伪造。
     * 整体兜一层 catch：这里在 hooker 体内，unparcel 抛的异常不能往 AMS 的 binder 线程外抛。
     */
    private fun readBroadcast(args: List<Any?>) {
        try {
            for (a in args) {
                val intent = a as? Intent ?: continue
                val action = intent.action ?: continue
                // debug 通道（只 debug 包存在）单独一路：它的发送方**不是宿主**，来源校验也不同。
                val isProbe = DEBUG_PROBE && action == ProbeChannel.ACTION
                // 我们认识的广播就这四条 —— 不在表里的直接丢。注意**不含** ACTION_INJECT_READY：
                // 那是我们自己发出去的答，认它只会多绕一圈（见 `core/wire/ModuleWire` 那两条常量）。
                val isOurs =
                    action == ConfigBroadcast.ACTION_CONFIG ||
                        action == StatusBarBridge.ACTION_STATUSBAR ||
                        action == FlexibleWindowBridge.ACTION ||
                        action == ModuleWire.ACTION_INJECT_QUERY ||
                        action == ModuleWire.ACTION_KEY_WATCH ||
                        action == ModuleWire.ACTION_LOG_PULL
                if (!isProbe && !isOurs) {
                    continue
                }
                // 功能通道：只有宿主（与同 uid 的设置页）有资格发 —— 其余来源一律丢弃。
                // debug 通道：只放行 root / system（调试从 adb 发，没有宿主身份）。
                if (if (isProbe) !isFromRootOrSystem() else !isFromHost()) return
                dispatchAction(action, intent)
                return
            }
        } catch (e: Throwable) {
            XLog.w("读广播实参失败（已忽略，原调用照常）：${e.javaClass.simpleName}", e)
        }
    }

    private fun dispatchAction(
        action: String,
        intent: Intent,
    ) {
        when (action) {
            ConfigBroadcast.ACTION_CONFIG -> {
                XLog.guard("收配置广播") { ConfigChannel.read(intent) }
            }

            // 通知中心 / 控制中心 / 最近任务：让本机（system_server）去执行。
            StatusBarBridge.ACTION_STATUSBAR -> {
                handler.post {
                    try {
                        StatusBarBridge.handle(intent, systemLoader)
                    } catch (e: Throwable) {
                        XLog.w("状态栏指令执行失败（已忽略）：${e.javaClass.simpleName}", e)
                    }
                }
            }

            // 小窗指令：同上，只有这边能拿到 FlexibleTaskController。
            FlexibleWindowBridge.ACTION -> {
                handler.post {
                    try {
                        FlexibleWindowBridge.handle(intent, systemLoader)
                    } catch (e: Throwable) {
                        XLog.w("小窗指令执行失败（已忽略）：${e.javaClass.simpleName}", e)
                    }
                }
            }

            // 链路自检：回答「注入侧那套装好没有」
            ModuleWire.ACTION_INJECT_QUERY -> {
                XLog.guard("回链路自检包") { reportStatus() }
            }

            // 「自定义按键」页的开 / 关：只翻一个 volatile 字段，没有 binder 往返，
            // 不必 post 到主线程（见 `KeyWatch.set`）。
            ModuleWire.ACTION_KEY_WATCH -> {
                KeyWatch.set(intent.getBooleanExtra(ModuleWire.EXTRA_WATCH_ON, false))
            }

            // 导出日志：把注入侧（system_server）那份缓冲切片发回设置页。
            //  必须 post 到主线程：这条回调跑在 AMS 的广播入口里，在它内部再 sendBroadcast
            //  等于在广播路径里重入广播路径（与 `reportStatus` 同一条纪律）。
            ModuleWire.ACTION_LOG_PULL -> {
                handler.post {
                    val ctx = SystemContext.get(systemLoader ?: return@post) ?: return@post
                    LogWire.reply(ctx, HostApp.PACKAGE, "system")
                }
            }

            // debug 通道：反射 / 探针，见 docs/debug-channel.md
            ProbeChannel.ACTION -> {
                handler.post {
                    try {
                        ProbeChannel.handle(intent, systemLoader)
                    } catch (e: Throwable) {
                        XLog.w("探针指令执行失败（已忽略）：${e.javaClass.simpleName}", e)
                    }
                }
            }
        }
    }

    /**
     * 这条广播是不是宿主进程发出来的。两条通道都只有宿主 / 设置页（同 uid）会发；不校验来源的话，
     * 任意 App 发一条同名广播就能以 system 身份开最近任务、翻主开关、改长按 / 双击时序。
     * 在原方法体跑之前读 `Binder.getCallingUid()` 拿到的就是发送方 uid；宿主 uid 解析一次后缓存。
     * **解析不到时丢弃（fail-closed）**：放行等于任意 App 都能伪造这两条通道，而配置广播只在
     * 启动 / 解锁 / 写盘时发，下一轮会重取 uid，不会因一次失败把通道永久锁死。
     */
    private fun isFromHost(): Boolean {
        val calling = android.os.Binder.getCallingUid()
        val host = hostUid()
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
     * debug 通道（[ProbeChannel]）的来源校验：只放行 **root / system**。
     *
     * 与 [isFromHost] 分开是因为它的发送方**本来就不是宿主** —— 调试时是
     * `adb shell su -c am broadcast`，没有宿主身份可用。普通 App（uid ≥ 10000）依旧进不来，
     * 而且整个通道只在 debug 包存在（[DEBUG_PROBE] 是编译期常量，release 里这些代码全被折掉）。
     */
    private fun isFromRootOrSystem(): Boolean {
        val calling = android.os.Binder.getCallingUid()
        if (calling == 0 || calling == android.os.Process.SYSTEM_UID) return true
        XLog.w("debug 通道：丢弃非 root/system 来源（callingUid=$calling）")
        return false
    }

    /** 宿主包的 uid，解析成功后缓存（-1 = 还没解析到；失败不缓存，下一轮广播重取）。 */
    @Volatile
    private var cachedHostUid: Int = -1

    private fun hostUid(): Int {
        val cached = cachedHostUid
        if (cached != -1) return cached
        val uid =
            try {
                val ctx = SystemContext.get(systemLoader ?: return -1) ?: return -1
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

    /**
     * 一个接管点的处理。不可能抛异常 —— 它是 Hook 回调本体，抛出去就是 system_server 崩。
     *
     * 返回 `chain.proceed()` 的原值 = 不接管，系统照常处理（主开关关着时走这条）；
     * 返回 `null` = 接管，系统原本的侧键功能不执行；**原方法自己抛了**时按它的声明返回类型
     * 给一个零值（`boolean` ⇒ `false`），那是「我们没接管、系统自己也没成」的那一档。
     */
    private fun onIntercept(
        interceptor: SideKeyInterceptor,
        point: Framework.InterceptionPoint,
        chain: XposedInterface.Chain,
    ): Any? {
        val handled: Boolean =
            try {
                interceptor.handle(point, chain)
            } catch (e: Throwable) {
                XLog.w("侧键处理失败（放行给系统）：$point", e)
                false
            }

        if (handled) return null // 接管：吞掉原方法

        return try {
            chain.proceed()
        } catch (e: Throwable) {
            //  原方法（系统自己的那个）抛了。**不能回 `null`**：
            //   1) 这个文件里 `null` 的含义是「接管」，而这一档我们什么都没做；
            //   2) 声明成 `boolean` 的那些接管点拿到 null，框架侧拆箱就是 NPE ——
            //      把「系统自己出错」升级成 system_server 崩。
            //  按声明返回类型的零值回填：`false` = 不消费，系统那套走到它自己的异常路径上。
            XLog.w("原方法执行失败（按「不接管」返回零值）：$point", e)
            Reflect.zeroOf((chain.executable as? java.lang.reflect.Method)?.returnType)
        }
    }
}
