package com.lookie.opluskey.gesture

import android.content.Context
import android.os.Bundle
import android.os.Message
import com.lookie.opluskey.Reflect
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.ConfigStore
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

    @Volatile
    private var installed = false

    /** 装上的 hook 数，供日志与验收用。 */
    @Volatile
    var hookCount: Int = 0
        private set

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
                // 宿主那套（打开设置页）会照跑 —— 与框架真正的场景路由（按场景派发给场景宿主）是两回事，
                // 所以无条件吞；其余消息（如 what=3 开关状态）照常放行。
                if (what == SCENE_DISPATCH_WHAT) {
                    // 每次按键都会走到这里，同样只能 XLog.d；「吞没吞对」靠行为验证。
                    XLog.d("已吞掉系统场景派发（what=$what）—— 侧键只走模块配置的动作")
                    return@hookGuarded null
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
                    XLog.i("系统侧键配置共 ${rows.size} 个场景：${
                        rows.joinToString(", ") { it.sceneName }
                    }")
                    rows.firstOrNull()?.let { r ->
                        XLog.d("首个场景「${r.sceneName}」的列值：${
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
