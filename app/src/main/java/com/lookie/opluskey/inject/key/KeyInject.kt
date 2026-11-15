package com.lookie.opluskey.inject.key

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.KeyEvent
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.bridge.inject.SystemContext
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.core.wire.ModuleWire

/**
 * 从 **system_server** 里注入一颗按键 —— 内置「截图」动作走的就是这一条（见 `ModuleWire.ACTION_INJECT_KEY`）。
 *
 * ## 为什么不是宿主自己注入
 *
 *  宿主注入 `KEYCODE_SYSRQ` 时 `injectInputEvent` 返回成功、屏幕却没反应（真机：日志里只有
 *  「截图：注入…」一行，没有任何被拒的记录）—— ColorOS 的截图逻辑不认**普通 app 注入**的那一下。
 *  这一份跑在 `system_server` 里，注入身份就是系统本身，与 `input keyevent` 同级。
 *
 * ## 为什么不是 `su` 去跑 `service call color_screenshot`
 *
 *  内置动作不该为了截一张图去要 root：`su` 会弹一次授权、慢半拍，没有 root 的机器上直接不可用。
 *  这条通道既不经过 shell，也不需要任何授权。
 *
 * ## 为什么要 pin 住 `InputManager` 而不是每次 `getInstance`
 *
 *  `injectInputEvent` 只在这一处调，而它在按键路径上。第一次解析成功之后把它记住（失败不记：
 *  类初始化顺序在 `system_server` 早期还不到，那时取不到是**暂时**的，缓存住会让注入永久失效）。
 *
 * 全程不抛：调用点在 AMS 的广播入口，漏一个异常出去就是 system_server 崩。
 */
internal fun injectKeyFromSystem(
    loader: ClassLoader?,
    keyCode: Int,
) {
    if (keyCode <= 0) {
        XLog.w("注入侧注入：键码不合法（$keyCode），跳过")
        return
    }
    //  **只注入自己认的那一批码**（`KeyId.INJECTABLE_CODES`）：入口收的是一条广播里的整数，
    //  不设闸就等于「谁能发这条广播，谁就能让 system_server 替他按任意键」。
    //  这张表同时是 `isSynthetic` 的判据（注入的键会再走一遍按键路径），两处口径必须同一份。
    if (keyCode !in KeyId.INJECTABLE_CODES) {
        XLog.w("注入侧注入：键码 $keyCode 不在可注入清单里，拒绝（见 KeyId.INJECTABLE_CODES）")
        return
    }
    //  **必须在别的线程上跑**：下面用的是 `WAIT_FOR_FINISH`（会等事件被系统处理完），
    //  而调用点是 `system_server` 的主线程 —— 在主线程上等一次截图链路，等于把整机挂住一下。
    //  每次注入起一个线程：这是「按一下键」级别的低频动作，不值得为它维护一个常驻线程。
    Thread {
        try {
            //  **截图先走「system_server 内部截图」**：个别机型（PLG110 实测）不认注入的那一下 ——
            //  注入成功、事件也进了 policy，就是不截。`ScreenshotHelper` 是系统自己截图用的那条，
            //  与按键无关，成了就不必再注入（否则会截两次）。
            //  没成（类不在 / 签名不对 / 发起即抛）就照旧走注入，两条都在日志里留判据。
            if (keyCode == KeyId.KEYCODE_SYSRQ &&
                ScreenshotViaHelper.take(loader) { loader?.let(SystemContext::get) }
            ) {
                return@Thread
            }
            val manager = inputManager(loader) ?: return@Thread
            val now = SystemClock.uptimeMillis()
            // 5 参构造是**公开** API（`KeyEvent(downTime, eventTime, action, code, repeat)`）：
            //  这里不需要宿主那份 10 参反射 —— 少一层参数表，也就少一处 ROM 差异。
            //
            //  **不要额外加 `FLAG_FROM_SYSTEM`**：`input keyevent` 注入的 flags 就是 0，而它在
            //  绝大多数机器上都能截 —— 加了反而与 `input` 那条已验过的路不一致。个别机器
            //  （PLG110 实测）注入成功、事件也进了 policy，就是不截；那是那台机器自己的口径，
            //  不为它改主路径（真机口径：这条注入在大多数设备上通用）。
            injectOnce(manager, KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0), keyCode)
            injectOnce(manager, KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0), keyCode)
        } catch (e: Throwable) {
            XLog.w("注入侧注入失败：keyevent:$keyCode", e)
        }
    }.start()
}

/**
 * 补发**一条**原生事件 —— 只给「只参与非首键的按住组合」那颗键收尾用（见 `ComboOnlyNative`）。
 *
 * ## 为什么不能复用 [injectKeyFromSystem]
 *
 *  那一条是**外部可达**的入口（一条广播里的整数进来就能让 system_server 代替按键），所以它
 *  卡着 [KeyId.INJECTABLE_CODES] 白名单。这里正相反：它补的是「用户刚刚真的按过的那一颗」，
 *  键码由按键路径自己带过来、外部给不出 —— 白名单在这里只会挡住正常使用。
 *  安全性来自调用方，不来自这张表；所以它是 `internal` 且没有任何广播入口指向它。
 *
 * ## 为什么按「一条」而不是「一对」
 *
 *  短按那条路一次投 DOWN + UP 就够（系统只该看到一闪）；而**按住不放**那条路必须在窗口到点
 *  只投 DOWN、等真实抬手再投 UP —— 系统的长按计时从收到 DOWN 那一刻起算，先投 UP 就等于
 *  把这一按截成一次短按，原生长按（音量连续调 / 电源长按菜单）永远出不来。
 *
 * ## 为什么是 ASYNC
 *
 *  [injectKeyFromSystem] 用 `WAIT_FOR_FINISH` 是因为截图要读结果码；这里只要「事件交出去」，
 *  而调用点在主 looper 的延迟任务上 —— 等它处理完就是把界面卡住一下。
 *
 * ## 时间必须是「现在」
 *
 *  补出来的这一下就发生在投递这一刻：`downTime` 与 `eventTime` 都取当前时刻，不回溯成用户
 *  真正按下的时刻。回溯看着能让长按早一点到，实际是把一条「按下已有一阵子」的事件塞进系统，
 *  与真实事件的时序对不上 —— 而这一下要的只是「系统从现在开始按着」，不是「假装早就按着」。
 *
 * @param repeat 长按过程中的第几发重复（0 = 真正的按下）；转发的真实重复原样带过来
 * @return 提交出去了没有（异步投递，不代表系统已经处理）
 */
internal fun injectNativeKey(
    keyCode: Int,
    action: Int,
    repeat: Int = 0,
): Boolean {
    if (keyCode <= 0) return false
    val manager = inputManager(null) ?: return false
    val now = SystemClock.uptimeMillis()
    // 5 参构造是公开 API（见 `injectKeyFromSystem` 里那段）
    //  结果**透传**出去：调用方（`ComboOnlyNative.forward`）要靠它决定「回声账销不销」——
    //  以前这里恒回 true，那笔账就永远销不掉，2 秒内用户真按这颗键会被当成回声放行。
    return injectOnce(manager, KeyEvent(now, now, action, keyCode, repeat), keyCode, INJECT_MODE_ASYNC)
}

/**
 * 下发一次事件，并按**真实的结果码**留判据。
 *
 * `InputManager.injectInputEvent` 返回的是 `InputEventInjectionResult`：
 *
 *  · `0` = `INPUT_EVENT_INJECTION_SUCCEEDED` —— **成功**
 *  · `1` = `INPUT_EVENT_INJECTION_FAILED`
 *  · `-1` = `INPUT_EVENT_INJECTION_PERMISSION_DENIED`
 *  · 其余负数 = 具体原因（`-2` 目标不存在、`-3` 超时…）
 *
 *  老版本返回 boolean（`true` = 成功）。
 *
 * ## 早先这里判反了
 *
 *  原实现把 `0` 当「失败」、`1` 当「成功」，注释还写着「0 = RESULT_FAILED」—— 于是
 *  **真正失败的那一次（返回 1）反而一行日志都不打**：用户看到的是「请注入侧注入」之后
 *  什么都没有，像是注入成功、屏幕没反应。判据方向错了，比没有判据更难查。
 *
 * @param mode 见 [INJECT_MODE_WAIT_FOR_FINISH] / [INJECT_MODE_ASYNC]
 */
private fun injectOnce(
    manager: Any,
    event: KeyEvent,
    keyCode: Int,
    mode: Int = INJECT_MODE_WAIT_FOR_FINISH,
): Boolean {
    val r = Reflect.call(manager, Framework.Calls.INJECT_INPUT_EVENT, event, mode)
    return when {
        r == null -> {
            XLog.w("注入侧注入：injectInputEvent 没返回（方法没匹配上）：keyevent:$keyCode")
            false
        }

        // 老版 boolean：true = 成功
        r is Boolean -> {
            if (!r) XLog.w("注入侧注入被拒（老版 boolean=false）：keyevent:$keyCode")
            r
        }

        r is Int -> {
            if (r != INPUT_EVENT_INJECTION_SUCCEEDED) {
                XLog.w("注入侧注入被拒（结果码 $r）：keyevent:$keyCode")
            }
            r == INPUT_EVENT_INJECTION_SUCCEEDED
        }

        else -> {
            XLog.w("注入侧注入：返回值不认识（${r.javaClass.simpleName}）：keyevent:$keyCode")
            false
        }
    }
}

/**
 * 取一个能注入的对象 —— 第一次取到就记住（理由见 [injectKeyFromSystem]）。
 *
 *  只留两个入口，顺序按**实测命中率**排，不按新旧：
 *
 *   1. `InputManagerGlobal.getInstance()` —— Android 14 起的正主（`injectInputEvent` 同名同签名）。
 *      本机（ColorOS / Android 16）实测走的正是这条；
 *   2. `InputManager.getInstance()` —— Android 13 及更早只有它。
 *
 *  **为什么不能只写一条**：13 上根本没有 `InputManagerGlobal`（只写第一条 ⇒ 新机能用、老机静默失效）；
 *  而 14 之后 `InputManager.getInstance()` 变成了包装、本机直接返回 null（只写它同样失效）。
 *  两条合起来才盖住整个版本区间。
 *
 *  试一次的代价只在**第一次**注入：命中之后 `cachedManager` 一直用下去，后续零反射。
 *  全部按名字调：注入侧不能 import 那些 @hide 签名（ROM 一改名就是 `NoClassDefFoundError`）。
 */
private fun inputManager(loader: ClassLoader?): Any? {
    cachedManager?.let { return it }
    val inst =
        pickInputManager(loader) ?: run {
            XLog.w("注入侧注入：两个入口都拿不到可注入的实例，这条路走不通")
            return null
        }
    cachedManager = inst
    return inst
}

/** 依次试的那两个静态入口（类名 → 静态方法名），按实测命中率排序；名字来自 `Framework.Calls`（一、7）。 */
private val ENTRIES: List<Pair<String, String>> =
    listOf(
        Framework.Calls.INPUT_MANAGER_GLOBAL to Framework.Calls.GET_INSTANCE,
        Framework.Calls.INPUT_MANAGER to Framework.Calls.GET_INSTANCE,
    )

private fun pickInputManager(loader: ClassLoader?): Any? =
    ENTRIES.firstNotNullOfOrNull { (clsName, methodName) ->
        val cls = Reflect.findClass(loader, clsName) ?: return@firstNotNullOfOrNull null
        Reflect.callStatic(cls, methodName)?.also {
            XLog.w("注入侧注入：入口用 $clsName.$methodName()")
        }
    }

/** 上一次取到的 `InputManager`（只缓存成功）。 */
@Volatile
private var cachedManager: Any? = null

/**
 * `InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH` —— 等这一下被系统**处理完**。
 *
 *  **与 `input keyevent` 完全一样**（`InputShellCommand` 传的就是它）。
 *  宿主那份用的是 `0`（ASYNC，不等）：那边注入的 home / 返回都正常，但截图一直没反应 ——
 *  除了「普通 app 注入不被认」之外，模式也是当时唯一没对齐的一处，这里按 `input` 的写法来。
 *
 *  三个值：`0` = ASYNC、`1` = WAIT_FOR_RESULT、`2` = WAIT_FOR_FINISH。
 *  早先这里写的是 `1`（WAIT_FOR_RESULT，等「分发到目标窗口」而不是「处理完」）——
 *  而截图这类被 policy 吞掉的事件本来就没有窗口消费，WAIT_FOR_RESULT 会返回
 *  `INPUT_EVENT_INJECTION_FAILED`，看着像注入失败。
 */
private const val INJECT_MODE_WAIT_FOR_FINISH = 2

/** `INJECT_INPUT_EVENT_MODE_ASYNC` —— 投出去就返回，不等结果（补发原生事件用它）。 */
private const val INJECT_MODE_ASYNC = 0

/** `InputEventInjectionResult.INPUT_EVENT_INJECTION_SUCCEEDED` —— 只有它算注入成功。 */
private const val INPUT_EVENT_INJECTION_SUCCEEDED = 0

/**
 * 「添加按键」页要的那条**旁路监听**：把按下的键原样报回宿主。
 *
 * ## 为什么必须在这里做
 *
 *  界面拿不到按键：设置页是宿主 Activity 里的覆盖层，焦点在宿主控件上；音量 / 电源这类更是
 *  在应用之前就被系统处理掉了。**只有注入侧看得见每一颗键**。
 *
 * ## 平时只读，收集时吞键
 *
 *  [report] 只复制一份事件发回去，**不改**认领 / 放行判定；真正决定吞不吞的是调用方
 *  （`SideKeyInterceptor`）—— 它看 [active]：监听期间**能收的键一律吞掉**，否则用户按的那颗键
 *  会照常触发系统动作（按音量就真的调音量、按返回就真的退出去），收集这一页等于没法用。
 *
 *  收集期**一律吞**：弹窗是模态的，这时按任何键都是在报键 —— 不吞的话按音量真的调音量、
 *  按电源真的弹菜单（还会把弹窗盖掉）。音量±与电源现在**允许被收集**
 *  （见 [KeyId.RESERVED_CODES]），剩下那几颗收不下的（主屏幕 / 返回 / 媒体 / 截图…）
 *  会 toast 一句原因。影响上限由 [AUTO_OFF_MS] 兜着，弹窗一关就恢复。
 *
 * ## 自己会关
 *
 *  页面退出、进程被杀、广播丢了都可能让「关」那条指令落空，所以带一个 [AUTO_OFF_MS] 的兜底：
 *  到期自动当作关掉，绝不会永久往外发键码。
 */
internal object KeyWatch {
    /** 打开后最多监听这么久（页面正常退出会提前关掉）。与页面上的倒计时同一个数。 */
    private const val AUTO_OFF_MS = com.lookie.opluskey.core.wire.ModuleWire.KEY_WATCH_WINDOW_MS

    /** 注入侧借到的 Context（推迟到真要发的时候取，理由同 `GestureForwarder`）。 */
    @Volatile
    private var context: (() -> Context?)? = null

    /** 到期时间（`elapsedRealtime`）；0 = 关着。 */
    @Volatile
    private var until = 0L

    /** 现在是不是在监听（含超时判定）。 */
    private val listening: Boolean
        get() = until > 0L && SystemClock.elapsedRealtime() < until

    /** 监听是否真的开着 —— 认领那条路据此决定「吞不吞这一下」（见类注释）。 */
    val active: Boolean
        get() = listening

    /** 由安装入口递进来一个「要用时才取」的 Context。 */
    fun install(borrow: () -> Context?) {
        context = borrow
    }

    /**
     * 上报用的队列 —— **自己的单线程**，不是主线程（见 [report]：InputReader 线程与
     * `system_server` 主线程上都不做同步广播）。
     *
     *  用一条独立线程而不是 `KeyHooks.worker`：这条上报只在收集期（最长 [AUTO_OFF_MS]）活着，
     *  与「替宿主干活」那一串指令互不排队 —— 收集页那几秒不该被一次卡住的状态栏指令堵住。
     *  懒加载，名字与那条同风格。
     */
    private val worker: Handler by lazy {
        Handler(HandlerThread("opluskey-keywatch").apply { start() }.looper)
    }

    /** 设置页发来的开 / 关。 */
    fun set(on: Boolean) {
        until = if (on) SystemClock.elapsedRealtime() + AUTO_OFF_MS else 0L
        // 开关是低频动作，用 w 当判据（`d` 在 release 里被编译期消除）
        XLog.w("按键监听：${if (on) "开启（60 秒后自动关）" else "关闭"}")
    }

    /**
     * 旁路报一条按键 —— 只报按下那一下（页面上要的是「按了哪个键」）。
     *
     *  整体 catch：这里在 hooker 体内，`sendBroadcast` 抛出去就是炸 AMS 的调用线程。
     */
    fun report(event: KeyEvent) {
        //  `repeatCount > 0` 一律不问：按住不放时系统每几十毫秒补一发 DOWN，逐发都报就是
        //  「W 刷屏 + 一条一条广播」—— 而页面要的只是「按了哪个键」，第一发已经说清了。
        if (!listening || event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return
        // 判据级：收集期「到底有没有看见这颗键、看见的是哪个码」。
        //  没有这一行时，用户报「收集不到」只能靠界面反应猜 —— 而「键没到我们这层」与
        //  「到了但这颗码收不下」是两种完全不同的故障（前者要往兜底层想办法，后者只是被拒）。
        //  只在监听期（最长 60 秒）逐颗报，不进平时路径。
        XLog.w("按键监听：收到 keyCode=${event.keyCode}（${KeyEvent.keyCodeToString(event.keyCode)}）")
        val ctx =
            try {
                context?.invoke()
            } catch (e: Throwable) {
                // 借 Context 失败 = 这条上报没有去处；吞在内部，绝不外抛（hook 体内抛出就是炸调用线程）
                XLog.w("按键监听：借 Context 失败，丢弃这一条上报：${e.javaClass.simpleName}")
                return
            } ?: return
        // **切到自己的线程再发**：这里跑在 InputReader 线程上，`sendBroadcast` 是一次同步 binder
        //  调用 —— AMS 那边一次瞬时锁竞争就会卡住全局按键读取（丢帧 / Input ANR），
        //  而这条上报只是「给设置页看按了哪个键」，晚几毫秒没有代价。
        val seen =
            Intent(ModuleWire.ACTION_KEY_SEEN)
                .setPackage(HostApp.PACKAGE)
                .putExtra(ModuleWire.EXTRA_SEEN_CODE, event.keyCode)
        worker.post {
            // 这里是 hook 体内：保护用 catch (Throwable) 而不是 runCatching —— 后者只允许出现在
            // 日志出口与反射查找（AGENTS.md 五之二、2），表达的不是同一件事。
            try {
                ctx.sendBroadcast(seen)
            } catch (e: Throwable) {
                // 判据级：监听开着却收不到按键，唯一线索就是这一行（`d` 在 release 里被编译期消除）
                XLog.w("按键监听：上报失败（忽略）：${e.javaClass.simpleName}")
            }
        }
    }
}
