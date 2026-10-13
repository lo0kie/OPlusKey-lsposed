package com.lookie.opluskey.hook

import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.Reflect
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.ConfigBroadcast
import com.lookie.opluskey.hookGuarded
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

    /** 注入侧的主线程 Handler —— 判定定时器与转发都走它。 */
    private val handler: Handler = Handler(Looper.getMainLooper())

    /**
     * 安装侧键 Hook。
     *
     * 每个接管点独立安装 —— 一个装不上不影响其他。
     */
    fun install(module: XposedModule, loader: ClassLoader) {
        if (installed) {
            XLog.w("已经装过，跳过重复安装")
            return
        }

        systemLoader = loader

        XLog.w("主开关：等宿主上报（上报前按「接管」处理，见 ConfigChannel 类注释）")

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
        val interceptor = SideKeyInterceptor(handler) { id, gesture, repeat ->
            forwarder.forward(id, gesture, repeat)
        }

        var ok = 0
        for (point in points) {
            val handle = module.hookGuarded(point.method) { chain ->
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
    private fun installBroadcastHook(module: XposedModule, loader: ClassLoader): Int {
        val amsCls = Reflect.findClass(loader, "com.android.server.am.ActivityManagerService")
        if (amsCls == null) {
            XLog.w("挂广播入口失败：找不到 ActivityManagerService")
            return 0
        }
        var n = 0
        for (m in Reflect.methodsOf(amsCls)) {
            if (m.name != "broadcastIntentWithFeature" && m.name != "broadcastIntent") continue
            if (module.hookGuarded(m) { chain ->
                    readBroadcast(chain.args)
                    chain.proceed()
                } != null
            ) {
                n++
            }
        }
        if (n == 0) {
            // 广播入口没挂上 ⇒ 主开关与状态栏指令两条通道整体失效（侧键照旧被吞但无动作），
            // 必须 W 级且把后果写清楚，别只写一个数字。
            XLog.w(
                "广播入口 Hook 一个都没装上（AMS 上没有 broadcastIntent*）—— " +
                    "「主开关」与「状态栏指令」两条通道整体失效：侧键被吞但没有任何动作",
            )
        } else {
            XLog.w("广播入口 Hook：$n 个（AMS）")
        }
        return n
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
                if (action != ConfigBroadcast.ACTION_CONFIG &&
                    action != StatusBarBridge.ACTION_STATUSBAR
                ) {
                    continue
                }
                // 只有宿主（与同 uid 的设置页）有资格发这两条 —— 其余来源一律丢弃。
                if (!isFromHost()) return
                if (action == ConfigBroadcast.ACTION_CONFIG) {
                    XLog.guard("收配置广播") { ConfigChannel.read(intent) }
                } else {
                    // 通知栏 / 最近任务：让本机（system_server）去执行。
                    StatusBarBridge.handle(intent, systemLoader)
                }
                return
            }
        } catch (e: Throwable) {
            XLog.w("读广播实参失败（已忽略，原调用照常）：${e.javaClass.simpleName}", e)
        }
    }

    /**
     * 这条广播是不是宿主进程发出来的。两条通道都只有宿主 / 设置页（同 uid）会发；不校验来源的话，
     * 任意 App 发一条同名广播就能以 system 身份开最近任务、翻主开关、改长按 / 双击时序。
     * 在原方法体跑之前读 `Binder.getCallingUid()` 拿到的就是发送方 uid；宿主 uid 解析一次后缓存，
     * 解析不到（理论上不该发生）只记 W 并放行 —— 不让「查不到 uid」把整条功能通道锁死。
     */
    private fun isFromHost(): Boolean {
        val calling = android.os.Binder.getCallingUid()
        val host = hostUid()
        if (host <= 0 || calling == host) return true
        XLog.w("丢弃来源不明的广播：callingUid=$calling（宿主 uid=$host）")
        return false
    }

    /** 宿主包的 uid，解析成功后缓存（-1 = 还没解析到）。 */
    @Volatile
    private var cachedHostUid: Int = -1

    private fun hostUid(): Int {
        val cached = cachedHostUid
        if (cached != -1) return cached
        val uid = try {
            val ctx = SystemContext.get(systemLoader ?: return -1) ?: return -1
            ctx.packageManager.getPackageUid(com.lookie.opluskey.KeyModule.PAGE_PACKAGE, 0)
        } catch (e: Throwable) {
            XLog.w("解析宿主 uid 失败（这一次不校验来源）：${e.message}")
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
     * 返回 `chain.proceed()` 的原值 = 不接管，系统照常处理（主开关关着时走这条）；
     * 返回 `null` = 接管，系统原本的侧键功能不执行。
     */
    private fun onIntercept(
        interceptor: SideKeyInterceptor,
        point: Framework.InterceptionPoint,
        chain: XposedInterface.Chain,
    ): Any? {
        val handled: Boolean = try {
            interceptor.handle(point, chain)
        } catch (e: Throwable) {
            XLog.w("侧键处理失败（放行给系统）：$point", e)
            false
        }

        if (handled) return null // 接管：吞掉原方法

        return try {
            chain.proceed()
        } catch (e: Throwable) {
            XLog.w("原方法执行失败：$point", e)
            null
        }
    }
}
