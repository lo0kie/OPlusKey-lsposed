package com.lookie.opluskey.inject.key

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import com.lookie.opluskey.bridge.FlexibleWindowBridge
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.bridge.StatusBarBridge
import com.lookie.opluskey.bridge.hookGuarded
import com.lookie.opluskey.bridge.inject.SystemContext
import com.lookie.opluskey.core.DEBUG_PROBE
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.ProcClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.core.wire.ConfigBroadcast
import com.lookie.opluskey.core.wire.LogWire
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.inject.input.GestureForwarder
import com.lookie.opluskey.inject.system.ShortcutRead
import com.lookie.opluskey.inject.system.SystemToast
import com.lookie.opluskey.inject.system.SystemVolume
import com.lookie.opluskey.inject.wire.ConfigChannel
import com.lookie.opluskey.inject.wire.NetChannel
import com.lookie.opluskey.inject.wire.ProbeChannel
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
     * 注入侧自己的**单线程**执行器 —— 「替宿主干活」那一类动作（状态栏 / 小窗 / 按键注入 /
     * 导出日志 / 弹提示 / 链路自检回包）都在这儿跑，**不占 `system_server` 主线程**。
     *
     *  为什么必须离开主线程：这几条里全是 binder 往返（`StatusBarBridge` / `FlexibleWindowBridge` /
     *  按键注入），对端卡住时调用线程会一直挂着 —— 而 `system_server` 主线程被挂住就是 watchdog
     *  直接杀进程（整机重启）。判定定时器仍然留在主 looper（[handler]）：那是「按毫秒卡时间」的
     *  东西，换线程等于换一套时序。
     *
     *  为什么是**一条**线程而不是每次开一条：长按连发会把这几条指令连着打进来，单线程排队既保序
     *  又不会堆线程。它同样满足「不许在 AMS 的广播入口里做这些事」—— 换一条线程就够了。
     */
    private val worker: Handler by lazy {
        Handler(HandlerThread("opluskey-inject").apply { start() }.looper)
    }

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

        //  游戏里那条音量条的旁路：系统把按键派发给观察者（`OplusKeyEventUtil`）那一步。
        //  与侧键那条**分开装** —— 挂不上只是「游戏里照旧弹条」，音量键本身不受影响。
        XLog.guard("挂按键观察者派发") { KeyObserverHooks.install(module, loader) }

        //  这一段是安装路径上唯一可能抛的一步（其余各步都包了 guard）：它一旦抛出去，
        //  后面的组合键 Hook、hookCount、安装判据日志会全部跳过。按「本机不支持」降级。
        val layers =
            try {
                Framework.findSideKeyLayers(loader)
            } catch (e: Throwable) {
                XLog.w("查找侧键接管点失败（按「本机不支持」处理，不影响系统）", e)
                emptyList()
            }
        val total = layers.sumOf { it.points.size }
        if (layers.isEmpty()) {
            XLog.w(
                "没找到任何侧键接管点 —— 本机 ROM 可能不是 ColorOS，或侧键实现改了名。" +
                    "模块不生效，但不影响系统",
            )
            //  **不在这里 return**：组合键那条与侧键那条并列（见 `installChordHooks` 的注释）——
            //  换 ROM 只有侧键方法改名、输入层方法还在时，这里一 return 就连组合拦截也一起不装了，
            //  而日志只会说「侧键没找到」，看起来像整条链路都没救。
            XLog.guard("挂组合键接管点（侧键落空之后）") { installChordHooks(module, loader) }
            XLog.w("侧键 Hook 0/0 个接管点 —— 侧键完全不被接管（组合键那条见上一行）")
            return
        }

        // 借 Context 的时机推迟到真正转交手势那一刻：安装阶段还没有 App attach 过，只能借到降级品。
        val forwarder = GestureForwarder({ SystemContext.get(loader) })
        // 「自定义按键」页那条旁路监听同样推迟取 Context（只在真要上报时才借，见 `KeyWatch`）
        KeyWatch.install { SystemContext.get(loader) }
        // 触觉反馈**不在这边**：它在宿主侧、动作即将执行那一刻（`host/GestureReceiver.runAction`）。
        //  判定侧振不出「服务在跑」这个事实 —— 判出手势就振，可宿主在不在、能不能执行它并不知道，
        // 于是会出现「振了却什么都没发生」；用户明确要求把它去掉（见那边的 KDoc）。
        val interceptor =
            SideKeyInterceptor(
                handler,
                holdAwake = { ms -> holdAwake(loader, ms) },
                forward = { id, gesture, repeat ->
                    forwarder.forward(id, gesture, repeat)
                },
                onGestureNoted = ::noteGesture,
                // 组合键（两颗键一起按）：判出来就**直接转给宿主执行** —— 动作在
                //  `ComboKey` 上（短按 / 长按两格），判定侧只负责认档与转发，与单键手势同一分工。
                onCombo = { combo, trigger, repeat ->
                    noteGesture()
                    logComboDispatch(combo, trigger, repeat)
                    forwarder.forwardCombo(combo.id, trigger, repeat)
                },
            )
        // 主开关翻到「不接管」时撤销已排下的手势（长按枪 / 双击窗 / 连发任务）。
        //  撤销只能由「收到配置广播」触发，不能挂在拦截点那条 early return 上（按住不放时
        //  没有新事件进来）—— 回调在 AMS 广播 hook 内跑，其内部已包 catch（见 ConfigChannel.read）。
        ConfigChannel.onTakeOverOff = { interceptor.cancelPending() }

        fun installPoints(points: List<Framework.InterceptionPoint>): Int {
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
                            "（kind=${point.kind}，参数 ${point.method.parameterTypes.size} 个，" +
                            //  返回类型一并记：接管分支返回的是 `null`（见 [onIntercept]），
                            //  它与「按声明类型回填零值」那一条口径不同 —— 真机要看得出这个方法的
                            //  声明类型是 `int` 还是 `boolean`，才能判那一处要不要改。
                            "返回 ${point.method.returnType.name}）",
                    )
                }
            }
            return ok
        }
        var ok = 0
        for ((index, layer) in layers.withIndex()) {
            val hit = installPoints(layer.points)
            if (hit > 0) {
                if (index > 0) {
                    XLog.w("侧键接管点退到第 ${index + 1} 档（${layer.label}）：装上 $hit 个")
                }
                ok = hit
                break
            }
            XLog.w("${layer.label} 的 ${layer.points.size} 个接管点一个都没装上 ⇒ 试下一档")
        }
        hookCount = ok
        // 输入层的组合键（电源 + 音量）：与侧键那条**分开装** —— 它不参与手势判定，只把 ROM 已经
        //  判好的组合吞掉。装不上时侧键照常工作，各记一行 w。
        XLog.guard("挂组合键接管点") { installChordHooks(module, loader) }
        //  **只要有接管点就算装过**，不能用 `ok > 0`：接管点 0 个时安装照样已经把广播入口、
        //  组合键 Hook、KeyWatch 挂上了，再进来一次会各挂第二份（同一条广播处理两遍）。
        installed = true
        reportHookInstall(ok, total)
    }

    /**
     * 挂输入层的组合键接管点（见 [ChordGuard]）。
     *
     *  一个都没装上也只记日志 —— 侧键那条链路不受影响（两条各管一件事）。
     *
     * @return 装上几个
     */
    private fun installChordHooks(
        module: XposedModule,
        loader: ClassLoader,
    ): Int {
        val points = Framework.findChordPoints(loader)
        if (points.isEmpty()) return 0
        var ok = 0
        for (p in points) {
            if (module.hookGuarded(p.method) { chain -> ChordGuard.handle(chain) } != null) {
                ok++
                XLog.w("已 Hook 组合键接管点：$p（参数 ${p.method.parameterTypes.size} 个）")
            }
        }
        XLog.w("组合键 Hook 安装完成：$ok/${points.size} 个接管点")
        return ok
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
     * 等于在广播路径里重入广播路径。整条投递 post 出去，hooker 里只留这一次 post。
     * **投到 [worker]，不投主线程**：`sendBroadcast` 是同步 binder 往返，挂在 `system_server`
     * 主线程上会被 watchdog 算进卡死（见 [worker] 的注释）；这一条只是答「你那套装好没有」，
     * 晚几毫秒没有代价。
     */
    private fun reportStatus() {
        val loader = systemLoader ?: return
        worker.post {
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
                        .putExtra(ModuleWire.EXTRA_INJECT_BUILD_ID, ModuleInfo.BUILD_ID)
                        // 「我这个进程是什么时候起来的」—— 页面拿它与安装包的更新时间比，
                        //  不需要人记得改任何常量（见该常量的说明）
                        .putExtra(ModuleWire.EXTRA_INJECT_PROC_START, ProcClock.startWall()),
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
                // 我们认识的广播就这几条 —— 不在表里的直接丢。注意**不含** ACTION_INJECT_READY：
                // 那是我们自己发出去的答，认它只会多绕一圈（见 `core/wire/ModuleWire` 那两条常量）。
                val isOurs =
                    action == ConfigBroadcast.ACTION_CONFIG ||
                        action == StatusBarBridge.ACTION_STATUSBAR ||
                        action == FlexibleWindowBridge.ACTION ||
                        action == ModuleWire.ACTION_INJECT_QUERY ||
                        action == ModuleWire.ACTION_KEY_WATCH ||
                        action == ModuleWire.ACTION_LOG_PULL ||
                        action == ModuleWire.ACTION_SHORTCUT_QUERY ||
                        action == ModuleWire.ACTION_INJECT_KEY ||
                        action == ModuleWire.ACTION_SET_VOLUME ||
                        action == ModuleWire.ACTION_NET_REQUEST ||
                        action == ModuleWire.ACTION_TOAST
                if (!isProbe && !isOurs) {
                    continue
                }
                // 功能通道：只有宿主（与同 uid 的设置页）有资格发 —— 其余来源一律丢弃。
                // debug 通道：只放行 root / system（调试从 adb 发，没有宿主身份）。
                if (!BroadcastSender.isTrusted(isProbe, systemLoader)) return
                dispatchAction(action, intent)
                return
            }
        } catch (e: Throwable) {
            XLog.w("读广播实参失败（已忽略，原调用照常）：${e.javaClass.simpleName}", e)
        }
    }

    /**
     * 把一段「只有注入侧才能做」的处理 post 到 [worker]，并整段兜 catch。
     *
     *  **必须 post**：这条回调跑在 AMS 的广播入口里，在里面再做注入 / `sendBroadcast`
     *  等于在广播路径里重入一次（见 [reportStatus] 那条注释）。
     *  **必须不在主线程**：这些处理是 binder 往返，挂在 `system_server` 主线程上会被 watchdog
     *  算进卡死（见 [worker] 的注释）。
     *  **必须兜 catch**：漏一个异常出去就是整机重启。
     *
     * @param subject 日志主语（出事时能看出是哪一件失败了）
     */
    private fun postGuarded(
        subject: String,
        block: () -> Unit,
    ) {
        worker.post {
            try {
                block()
            } catch (e: Throwable) {
                XLog.w("$subject 失败（已忽略）：${e.javaClass.simpleName}", e)
            }
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
                postGuarded("状态栏指令") { StatusBarBridge.handle(intent, systemLoader) }
            }

            // 小窗指令：同上，只有这边能拿到 FlexibleTaskController。
            FlexibleWindowBridge.ACTION -> {
                postGuarded("小窗指令") { FlexibleWindowBridge.handle(intent, systemLoader) }
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

            // 导出日志（把本进程缓冲回给设置页）与「读桌面上钉住的快捷方式」：两条都是「只有注入侧
            // 才能替宿主做的一件事」，合成一支只为把这张表的分叉留在 detekt 的复杂度口径内 ——
            // 各自的处理体在 [onContextQuery] 里。
            ModuleWire.ACTION_LOG_PULL, ModuleWire.ACTION_SHORTCUT_QUERY -> {
                onContextQuery(action, intent, systemLoader)
            }

            // 宿主请我们替它弹一条提示（toast 按包限流，注入侧用系统身份弹才免限流，见 ModuleWire）
            ModuleWire.ACTION_TOAST -> {
                postGuarded("替宿主弹提示") {
                    SystemToast.show(
                        systemLoader ?: return@postGuarded,
                        intent.getStringExtra(ModuleWire.EXTRA_TOAST_TEXT).orEmpty(),
                    )
                }
            }

            // 宿主请我们注入一颗键（内置「截图」：宿主自己注入的那一下 ColorOS 不认，见 ModuleWire）
            ModuleWire.ACTION_INJECT_KEY -> {
                postGuarded("注入侧注入") {
                    injectKeyFromSystem(systemLoader, intent.getIntExtra(ModuleWire.EXTRA_INJECT_CODE, -1))
                }
            }

            // 宿主请我们把音量设到某一档（宿主自己写会被 ColorOS 丢掉，见 ModuleWire）
            ModuleWire.ACTION_SET_VOLUME -> {
                postGuarded("注入侧设音量") {
                    val loader = systemLoader ?: return@postGuarded
                    SystemVolume.set(
                        loader,
                        intent.getIntExtra(ModuleWire.EXTRA_VOLUME_STREAM, -1),
                        intent.getIntExtra(ModuleWire.EXTRA_VOLUME_INDEX, -1),
                    )
                }
            }

            // 宿主请我们代取一个地址（宿主没 INTERNET，见 `NetChannel`）。
            ModuleWire.ACTION_NET_REQUEST -> {
                NetChannel.dispatch(intent, systemLoader)
            }

            // debug 通道：反射 / 探针，见 docs/runtime/debug-channel.md
            //  `post` 与兜底都在 [ProbeChannel.dispatch] 里（这一层只留一行分派）
            ProbeChannel.ACTION -> {
                ProbeChannel.dispatch(intent, systemLoader)
            }
        }
    }

    /**
     * 一个接管点的处理。不可能抛异常 —— 它是 Hook 回调本体，抛出去就是 system_server 崩。
     *
     * 返回 `chain.proceed()` 的原值 = 不接管，系统照常处理（主开关关着时走这条）；
     * **接管**时按原方法的声明返回类型回填（见 [swallowValueOf]）：`void` ⇒ `null`，`boolean` ⇒
     * `true`，其余原始类型 ⇒ 零值。不能一律返回 `null` —— 喂给原始类型返回点会在框架侧拆箱时
     * NPE，把「这一下不生效」升级成 system_server 崩溃。**原方法自己抛了**时同样按声明返回类型
     * 给零值，那是「我们没接管、系统自己也没成」的那一档。
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

        if (handled) return swallowValueOf(chain, "侧键接管 $point") // 接管：吞掉原方法

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

    /**
     * 组合键那一条的判定日志（判出哪一档、走了哪个槽位）。
     *
     *  连续触发的重复压到 d（release 裁掉）：一次按住会来几十条，W 会把日志淹掉。
     */
    private fun logComboDispatch(
        combo: ComboKey,
        trigger: GestureTrigger,
        repeat: Boolean,
    ) {
        //  日志里那一档动作：松手这一档是**另一个槽位**（`ComboKey.longRelease`），
        //  与单键 `actionOf(Release)` 同一条口径。
        val shown = combo.actionOf(trigger)
        val log =
            "组合键：${combo.id}（${combo.keys.joinToString(" + ")}）${trigger.label}" +
                "${if (repeat) "（连续触发）" else ""}" +
                " → ${XLog.redactToken(shown.token)}"
        if (repeat) XLog.d(log) else XLog.w(log)
    }

    /**
     * 安装结果的最后两行（「装没装上」的唯一分水岭）。
     *
     *  **必须 W 而非 i**：注入侧 i 会被 LSPosed 进程级日志级别整档丢掉、release 里更被编译期消除，
     *  用 i 打就等于彻底静默。
     *
     * @param ok 装上的 Hook 数
     * @param total 找到的接管点总数
     */
    private fun reportHookInstall(
        ok: Int,
        total: Int,
    ) {
        if (ok == 0) {
            XLog.w("侧键 Hook 一个都没装上：0/$total 个接管点 —— 侧键完全不被接管")
        } else {
            XLog.w("侧键 Hook 安装完成：$ok/$total 个接管点")
        }
    }

    /**
     * 屏幕此刻亮着没有（`PowerManager.isInteractive`）—— [holdAwake] 用它决定「这一下要不要顶住 CPU」。
     *
     *  熄屏时抬手之后设备会进深度睡眠，`postDelayed` 排的那一枪（按 `uptimeMillis` 走）不会到点；
     *  所以要配一段唤醒锁把窗口期顶住（见 [holdAwake] 与 `GestureDetector` 类注释）。
     *
     *  **拿不到一律按「亮着」**：那条路上还有双击窗口兜着，宁可多等 300ms，也别因为借不到
     *  Context 就把双击拆成两次单击。`getSystemService` 在注入侧是允许的（见 `SystemContext`），
     *  `ContentResolver` 那类才不行。
     */
    private fun screenInteractive(loader: ClassLoader): Boolean =
        try {
            val pm = SystemContext.get(loader)?.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isInteractive ?: true
        } catch (e: Throwable) {
            //  这里保护的是宿主服务调用，不是「日志出口 / 反射查找」那两处例外 —— 用 catch 而不是
            //  runCatching：`isInteractive` 的失败若来自 Error 系，也要按「说不出来就当亮着」收掉。
            XLog.w("屏幕状态读不到（按亮着处理）：${e.javaClass.simpleName}")
            true
        }

    /**
     * 屏幕黑着就让 CPU 在这段窗口里别睡 —— 熄屏判双击用（见 `GestureDetector` 类注释的「熄屏也判双击」）。
     *
     *  **屏幕亮着时什么都不做**：窗口那一枪本来就到得了点，不必为此付一段唤醒锁。
     *
     *  **带超时、不主动放**：漏放一次就是 CPU 再也不睡（只有重启能救），多醒几百毫秒则是这一档
     *  付得起的代价。`setReferenceCounted(false)` 保证超时那一次是真释放，不受此前叠加过几次影响。
     *
     *  取不到 Context / 服务时**静默降级**：这一下退化成「熄屏这一按判不出双击」，按键本身不受影响 ——
     *  判定侧绝不为了顶醒 CPU 把异常抛回输入线程（那会把系统进程带下去）。
     */
    private fun holdAwake(
        loader: ClassLoader,
        ms: Long,
    ) {
        try {
            if (screenInteractive(loader)) return
            val pm =
                SystemContext.get(loader)?.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm == null) {
                XLog.w("熄屏双击的唤醒锁没拿到（取不到 PowerManager，这一按可能判不出双击）")
                return
            }
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OPlusKey:doubleWindow")
            lock.setReferenceCounted(false)
            lock.acquire(ms)
            XLog.w("熄屏，排窗期间顶住 CPU ${ms}ms（判双击用）")
        } catch (e: Throwable) {
            XLog.w("熄屏双击的唤醒锁没拿到（这一按可能判不出双击，按键本身不受影响）：${e.javaClass.simpleName}", e)
        }
    }

    /**
     * 「借一份系统 Context 干一件事」那两条（导出日志 / 读快捷方式）合成一支。
     *
     *  为什么要有它：`dispatchAction` 那张 `when` 已经贴着 detekt 的圈复杂度上限，而这两条
     *  各自还要写「有没有 Context」那一层 —— 合起来之后，那张表里每条就只剩一次调用。
     *  与 [postGuarded] 同一条纪律（post 到 worker、整段兜 catch、失败只记一行），所以直接走它。
     */
    private fun withSystemContext(
        loader: ClassLoader?,
        subject: String,
        block: (android.content.Context) -> Unit,
    ) {
        postGuarded(subject) {
            val ctx = SystemContext.get(loader ?: return@postGuarded) ?: return@postGuarded
            block(ctx)
        }
    }

    /**
     * 「借系统身份干一件事」那两条的分发表 —— 导出日志 / 读桌面上钉住的快捷方式。
     *
     *  两条的处理体都靠各自的类（`LogWire` / `ShortcutRead`），这里只回答「哪一条走哪个」。
     */
    private fun onContextQuery(
        action: String,
        intent: Intent,
        loader: ClassLoader?,
    ) {
        if (action == ModuleWire.ACTION_LOG_PULL) {
            withSystemContext(loader, "导出日志（注入侧）") { ctx -> LogWire.reply(ctx, HostApp.PACKAGE, "system") }
        } else {
            postGuarded("读快捷方式（系统侧）") { ShortcutRead.reply(loader, intent) }
        }
    }
}

// 「这条广播是谁发的」—— 从 `KeyHooks` 分出来一支：那三支判据（宿主 / root-system / uid 缓存）
// 只在广播入口用，和「装 Hook、接管按键」不是一件事。

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
            //  高频路径（别的 App 的每一条广播都会走到这里）⇒ d：它不影响模块自身的判据，
            //  留在 w 上会把真正的判据行冲干净（见 AGENTS 二、1.2）。
            XLog.d { "丢弃来源不明的广播：callingUid=$calling（宿主 uid=$host）" }
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
