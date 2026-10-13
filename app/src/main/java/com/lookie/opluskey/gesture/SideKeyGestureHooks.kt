package com.lookie.opluskey.gesture

import android.content.Context
import android.os.Bundle
import android.os.Message
import com.lookie.opluskey.Reflect
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.ConfigStore
import com.lookie.opluskey.config.KeyId
import com.lookie.opluskey.hook.SystemActionButtonConfig
import com.lookie.opluskey.hookGuarded
import io.github.libxposed.api.XposedModule

/**
 * 劫持系统自己判好的侧键手势（宿主主进程 `com.oplus.gesture`）。
 */
internal object SideKeyGestureHooks {

    /** 「打开应用」场景的宿主服务（框架按场景表里的 `click_action_info` 绑它）。 */
    private const val START_APP_SERVICE =
        "com.oplus.gesture.actionbuttonkey.quicklaunch.StartAppService"

    /** 其余场景的宿主服务。 */
    private const val KEY_SERVICE =
        "com.oplus.gesture.server.ActionButtonKeyService"

    /**
     * 收到它时宿主要按系统当前场景去开页面 / 起服务；模块接管侧键后必须把它吞掉，
     * 否则系统那套动作会和模块的动作一起执行。
     */
    private const val SCENE_DISPATCH_WHAT = 1

    /** 「放行」后允许把场景派发让给原生的时长（毫秒）——要盖过 [SCENE_DECIDE_DELAY_MS]。 */
    private const val SCENE_PASSTHROUGH_WINDOW_MS = 1500L

    /** 扣住一条场景派发、等判定结果的最长时长（毫秒）——见 [deferSceneDispatch]。 */
    private const val SCENE_DECIDE_DELAY_MS = 700L

    /** 配额上限：突发兜底闸之外再压一道，避免判定侧异常刷屏时把原生动作连着放出去。 */
    private const val SCENE_ALLOWANCE_MAX = 3

    @Volatile
    private var installed = false

    /** 装上的 hook 数，供日志与验收用。 */
    @Volatile
    var hookCount: Int = 0
        private set

    /** 最近一次「放行」判定到点的时刻（[notePassthrough] 写）——0 = 还没放过。 */
    @Volatile
    private var passthroughAt = 0L

    /** 还剩几条「放行」的场景派发配额（一次放行一条，见 [notePassthrough]）。 */
    @Volatile
    private var sceneAllowance = 0

    /** 扣住等判定的那条场景派发（只留一条，后来的顶掉）。 */
    private var pendingScene: android.os.Message? = null
    private var pendingSceneHandler: android.os.Handler? = null

    /** 已决定放行、正在补投的那条（按 identity 认领，别再被吞一次）。 */
    private var allowedScene: android.os.Message? = null

    /**
     * 注入侧把一次按键判成「放行」（native / passthrough）时记一笔（`GestureReceiver` 收到即调）。
     *
     * 用途：这次按压在框架侧还会再派发一条 `what=1` 的场景消息（原生动作的载体），
     * 宿主侧的钩子据此把它放过去 —— 不放的话「放行」就成了空转（模块吞了键、原生动作也没了）。
     */
    internal fun notePassthrough() {
        passthroughAt = android.os.SystemClock.elapsedRealtime()
        // 一次「放行」只允许放一条场景派发：同一次按压可能同时有「按压自己的那条」与
        // 「重注入的 735 触发的那条」，两条都放会执行两次原生动作。
        sceneAllowance = (sceneAllowance + 1).coerceAtMost(SCENE_ALLOWANCE_MAX)
    }

    /**
     * 领一条「放行」配额：窗口 [SCENE_PASSTHROUGH_WINDOW_MS] 内、且还有余额才算数。
     */
    private fun claimSceneAllowance(): Boolean {
        val at = passthroughAt
        if (at == 0L) return false
        if (android.os.SystemClock.elapsedRealtime() - at > SCENE_PASSTHROUGH_WINDOW_MS) {
            sceneAllowance = 0
            return false
        }
        if (sceneAllowance <= 0) return false
        sceneAllowance--
        return true
    }

    /**
     * 判定结果还没到（宿主主线程忙时，「放行」广播会排在 `what=1` **后面**——真机实测
     * 22:02:22 / 22:02:24 两次误吞）⇒ 先把这条扣住，[SCENE_DECIDE_DELAY_MS] 后再看：
     * 期间判定若到了就补投给原 Handler，没到就丢弃（模块动作那条本来就该吞）。
     */
    private fun deferSceneDispatch(handler: android.os.Handler?, msg: android.os.Message?) {
        if (handler == null || msg == null) return
        pendingScene = msg
        pendingSceneHandler = handler
        handler.postDelayed({
            val m = pendingScene
            val h = pendingSceneHandler
            pendingScene = null
            pendingSceneHandler = null
            if (m == null || h == null || m !== msg) return@postDelayed
            if (!claimSceneAllowance()) {
                XLog.d("已吞掉系统场景派发（what=${m.what}，等判定超时）—— 侧键只走模块配置的动作")
                return@postDelayed
            }
            // 必须**复制**一条新 Message 再投：原实例在本次 `handleMessage` 返回后就被 Looper
            // 回收进复用池（再拿出来就是别人的了），直接投原实例是未定义行为。
            val copy = android.os.Message.obtain(h, m.what, m.arg1, m.arg2, m.obj)
            runCatching {
                m.data?.let { copy.data = android.os.Bundle(it) }
                copy.replyTo = m.replyTo
            }
            allowedScene = copy
            XLog.w("场景派发放行（判定为「放行」，延迟补投）")
            runCatching { h.sendMessage(copy) }
                .onFailure { XLog.w("补投场景派发失败：${it.message}") }
        }, SCENE_DECIDE_DELAY_MS)
    }

    fun install(module: XposedModule, loader: ClassLoader) {
        if (installed) {
            XLog.i("手势劫持 Hook 已经装过，跳过重复安装")
            return
        }

        // 先在宿主进程读一遍设置页写的那份配置（同 uid，理应读得到）—— 判「配置通道通不通」。
        logConfig()

        // 再读一次系统侧键配置（纯诊断）。只能在宿主进程读 —— system_server 里 ContentResolver 必 NPE。
        probeSystemConfig(loader)

        var n = 0
        n += hookService(module, loader, START_APP_SERVICE)
        n += hookService(module, loader, KEY_SERVICE)

        hookCount = n
        installed = n > 0
        if (n > 0) {
            XLog.w("侧键手势劫持 Hook 安装完成：$n 个接管点（宿主主进程）")
        } else {
            XLog.w("侧键手势接管点一个都没找到 —— 宿主改了服务名？本机退化为「不劫持手势」")
        }
    }

    /**
     * 给一个宿主服务装 Hook：内部 `$a extends Handler` 的 `handleMessage(Message)`
     * —— 手势消息真正的落点。
     *
     * @return 这次装上的 hook 数
     */
    private fun hookService(module: XposedModule, loader: ClassLoader, className: String): Int {
        val cls = Reflect.findClass(loader, className)
        if (cls == null) {
            XLog.w("没找到宿主服务：$className（本机可能不是 ColorOS，或服务改名了）")
            return 0
        }

        val short = className.substringAfterLast('.')

        // 内部 Handler：`XxxService$a`。类名是混淆后的（$a），但同一个 ROM 内稳定，
        // 找不到就只记日志 —— 说明 ROM 改版了，别硬猜。
        val handlerCls = Reflect.findClass(loader, "$className\$a")
        if (handlerCls == null) {
            XLog.w("没找到 $short 的内部 Handler 类（$className\$a）")
            return 0
        }
        val m = Reflect.method(handlerCls, "handleMessage", Message::class.java) ?: run {
            XLog.w("$short\$a 上没有 handleMessage(Message)")
            return 0
        }
        return if (module.hookGuarded(m) { chain ->
                val msg = chain.args.getOrNull(0) as? Message
                val what = msg?.what ?: -1
                // 这条是每次 Message 都打的（含每条按键消息），只能 XLog.d ——
                XLog.d("侧键手势消息：$short\$a#handleMessage | ${describeMessage(msg)}")

                // 必须吞掉系统自己的场景派发：框架除走策略类拦截外，还会给宿主这个 Handler 发一条，
                // 宿主那套（打开设置页）会照跑 —— 与框架真正的场景路由（按场景派发给场景宿主）是两回事。
                // 例外：这次按键被判「放行」时那条要放过去，它是原生动作的载体（见 [notePassthrough]）。
                if (what == SCENE_DISPATCH_WHAT) {
                    val sceneMsg = msg
                    when {
                        // 主开关关着 ⇒ 模块完全让位：场景派发放行，原生动作照常执行（行为与原生一致）。
                        !ConfigStore.read().key(KeyId.PLUS).enabled -> {
                            XLog.d("主开关已关，场景派发放行（原生行为）")
                            return@hookGuarded chain.proceed()
                        }
                        // 补投回来的那条（我们自己的决定），直接放过去
                        sceneMsg != null && sceneMsg === allowedScene -> {
                            allowedScene = null
                            return@hookGuarded chain.proceed()
                        }

                        claimSceneAllowance() -> {
                            XLog.w("场景派发放行（本次按键判为「放行」⇒ 执行原生动作）")
                            return@hookGuarded chain.proceed()
                        }
                        // 判定结果可能还在路上（主线程忙时广播排在 what=1 后面）⇒ 扣住等一会儿
                        sceneMsg != null -> {
                            deferSceneDispatch(
                                chain.thisObject as? android.os.Handler,
                                sceneMsg,
                            )
                            return@hookGuarded null
                        }

                        else -> return@hookGuarded null
                    }
                }

                chain.proceed()
            } != null
        ) {
            1
        } else {
            0
        }
    }

    /** 把 `Message` 摊开打印：`what` / `arg1` / `arg2` / `obj` / `data` 的每一个键值。 */
    private fun describeMessage(arg: Any?): String {
        val msg = arg as? Message ?: return "不是 Message：${arg?.javaClass?.name}"
        val sb = StringBuilder("what=${msg.what} arg1=${msg.arg1} arg2=${msg.arg2}")
        msg.obj?.let { sb.append(" obj=${it.javaClass.name}($it)") }
        sb.append(" data=${describeBundle(msg.data)}")
        return sb.toString()
    }

    /** `Bundle` 里每个键值都打出来（手势类型这种信息就在这层）。 */
    private fun describeBundle(b: Bundle?): String {
        if (b == null) return "(null)"
        val keys = try {
            b.keySet()
        } catch (e: Throwable) {
            return "(取键失败：${e.message})"
        }
        if (keys.isEmpty()) return "(空)"
        return keys.joinToString(prefix = "{", postfix = "}") { k ->
            val v = try {
                // `Bundle.get(String)` 自 API 34 起 deprecated（官方建议用 typed getter），
                // 但这里是诊断用的键值全量 dump、键类型未知，所以照原样取 `Any?` 并压掉警告。
                @Suppress("DEPRECATION")
                b.get(k)
            } catch (e: Throwable) {
                "读取失败"
            }
            "$k=$v"
        }
    }

    /**
     * 读一次系统侧键配置，把场景名列出来（纯诊断，读不到不影响任何功能）。
     * 必须在宿主进程读 —— `ContentResolver` 在 system_server 里必 NPE。
     */
    private fun probeSystemConfig(loader: ClassLoader) {
        val ctx = appContext(loader) ?: run {
            XLog.d("拿不到 Application Context，跳过系统侧键配置探测")
            return
        }
        XLog.guard("读系统侧键配置") {
            val rows = SystemActionButtonConfig.readAll(ctx)
            when {
                rows == null -> XLog.w("系统侧键配置读取失败（诊断项，不影响接管）")
                rows.isEmpty() -> XLog.i("系统侧键配置为空（用户还没在「设置 → 快捷键」里配过）")
                else -> {
                    XLog.i(
                        "系统侧键配置共 ${rows.size} 个场景：${
                        rows.joinToString(", ") { it.sceneName }
                    }")
                    rows.firstOrNull()?.let { r ->
                        XLog.d(
                            "首个场景「${r.sceneName}」的列值：${
                            r.values.entries.joinToString(", ") { "${it.key}=${it.value}" }
                        }")
                    }
                }
            }
        }
    }

    /**
     * 取宿主进程的 Application Context（安装时可能还没挂上，拿不到就返回 null）。
     * 宿主是普通 App 进程，`ActivityThread.currentApplication()` 在这儿有效。
     */
    private fun appContext(loader: ClassLoader): Context? = try {
        val cls = Reflect.findClass(loader, "android.app.ActivityThread")
        val app = if (cls == null) null else Reflect.callStatic(cls, "currentApplication")
        app as? Context
    } catch (e: Throwable) {
        XLog.d("取 Application Context 失败：${e.message}")
        null
    }

    /**
     * 在宿主进程里读一遍配置，证明「设置页写的那份文件这里读得到」——
     * 这是注入侧读不到（EACCES）的那一份。
     */
    private fun logConfig() {
        XLog.guard("读取宿主进程内的配置") {
            val cfg = ConfigStore.read()
            val key = cfg.key(com.lookie.opluskey.config.KeyId.PLUS)
            XLog.w(
                "宿主进程读到配置：接管=${key.enabled}" +
                        " 单击=${key.single.token} 双击=${key.double.token} 长按=${key.long.token}" +
                        "（文件 ${ConfigStore.sharedFile().path}）",
            )
        }
    }
}
