package com.lookie.opluskey.host

import android.content.Context
import android.os.Bundle
import android.os.Message
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.inject.Framework
import com.lookie.opluskey.inject.hookGuarded
import com.lookie.opluskey.inject.system.SystemActionButtonConfig
import io.github.libxposed.api.XposedModule

/**
 * 劫持系统自己判好的侧键手势（宿主主进程 `com.oplus.gesture`）。
 */
internal object SideKeyGestureHooks {

    /** 「打开应用」场景的宿主服务（框架按场景表里的 `click_action_info` 绑它）。 */
    private const val START_APP_SERVICE = Framework.HOST_START_APP_SERVICE

    /** 其余场景的宿主服务。 */
    private const val KEY_SERVICE = Framework.HOST_KEY_SERVICE

    /**
     * 收到它时宿主要按系统当前场景去开页面 / 起服务；模块接管侧键后必须把它吞掉，
     * 否则系统那套动作会和模块的动作一起执行。
     *
     * 注意：取值本体在 `inject/Framework.kt` 的候选表里（一、7），这里只是别名。
     */
    private const val SCENE_DISPATCH_WHAT = Framework.HOST_SCENE_DISPATCH_WHAT

    @Volatile
    private var installed = false

    /** 装上的 hook 数，供日志与验收用。 */
    @Volatile
    var hookCount: Int = 0
        private set

    // 「放行」（`native` / `passthrough`）那一档**整条已删**，
    //  随之删掉的还有这一整套「放行配额 + 场景派发扣留补投」的机器：
    //  它存在的唯一理由是「放行时把原生动作那条场景消息放过去」，现在没有任何一档会放行。
    //  ⇒ 场景派发**一律吞掉**（主开关关着时除外，那一档模块完全让位）。
    //  取舍与依据见 `docs/implementation.md` 的「交还系统为什么不提供」。

    fun install(module: XposedModule, loader: ClassLoader) {
        if (installed) {
            XLog.w("手势劫持 Hook 已经装过，跳过重复安装")
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

        // 内部 Handler：`XxxService$a`。类名是混淆后的，但同一个 ROM 内稳定，
        // 找不到就只记日志 —— 说明 ROM 改版了，别硬猜。名字来自 `Framework` 的候选表（一、7）。
        val handlerCls = Reflect.findClass(loader, "$className\$${Framework.HOST_HANDLER_SUFFIX}")
        if (handlerCls == null) {
            XLog.w("没找到 $short 的内部 Handler 类（$className\$${Framework.HOST_HANDLER_SUFFIX}）")
            return 0
        }
        val method = Framework.HOST_HANDLE_MESSAGE
        val m = Reflect.method(handlerCls, method, Message::class.java) ?: run {
            XLog.w("$short\$a 上没有 $method(Message)")
            return 0
        }
        return if (module.hookGuarded(m) { chain ->
                val msg = chain.args.getOrNull(0) as? Message
                val what = msg?.what ?: -1
                // 这条是每次 Message 都打的（含每条按键消息），只能 XLog.d ——
                //  并且**必须用惰性重载**：`describeMessage` 会把整个 Bundle 摊平成一个字符串，
                //  参数若在调用点先求值，release 下（`if (VERBOSE_LOG)` 把函数体消掉）也照跑一遍。
                XLog.d { "侧键手势消息：$short\$a#handleMessage | ${describeMessage(msg)}" }

                // 必须吞掉系统自己的场景派发：框架除走策略类拦截外，还会给宿主这个 Handler 发一条，
                // 宿主那套（打开设置页）会照跑 —— 与框架真正的场景路由（按场景派发给场景宿主）是两回事。
                //  注意：这里**没有任何例外** —— 「本次按键判为放行就放它过去」那条不能有，它正是「按下侧键把被替换的设置页拉起来」的来源。
                //  唯一还放行的只有下面这一档：主开关关着时模块完全让位，行为与原生一致。
                if (what == SCENE_DISPATCH_WHAT) {
                    if (!ConfigStore.read().key(KeyId.PLUS).enabled) {
                        // 判据级：这两条是「侧键到底走没走模块」的判据 —— 必须用 w，
                        //  用 d 会在 release 里被编译期消除 ⇒ 线上完全无痕，只能靠猜。
                        XLog.w("主开关已关，场景派发放行（原生行为）")
                        return@hookGuarded chain.proceed()
                    }
                    XLog.w("场景派发已吞掉（模块接管中）—— 侧键只走模块配置的动作")
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
        val ctx = ActivityThreadCompat.currentApplication(loader) ?: run {
            XLog.d("拿不到 Application Context，跳过系统侧键配置探测")
            return
        }
        XLog.guard("读系统侧键配置") {
            val rows = SystemActionButtonConfig.readAll(ctx)
            when {
                rows == null -> XLog.w("系统侧键配置读取失败（诊断项，不影响接管）")
                rows.isEmpty() -> XLog.w("系统侧键配置为空（用户还没在「设置 → 快捷键」里配过）")
                else -> {
                    XLog.w(
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
     * 在宿主进程里读一遍配置，证明「设置页写的那份文件这里读得到」——
     * 这是注入侧读不到（EACCES）的那一份。
     */
    private fun logConfig() {
        XLog.guard("读取宿主进程内的配置") {
            // 注意：必须走**三态**的 `ConfigStore.load()`，不能用 `read()`：
            //  `read()` 把「文件在但读不懂」塌缩成 `Config.DEFAULT` —— 于是这条**自证
            //  「设置页写的那份文件这里读得到」**的日志，在文件其实读不懂时照样打出一份漂亮的
            //  默认值，把正是它要自证的那件事盖掉了（排查时会把「读不懂」误读成「读到了默认配置」）。
            when (val l = ConfigStore.load()) {
                is ConfigStore.Load.Ok -> {
                    val key = l.config.key(com.lookie.opluskey.core.model.KeyId.PLUS)
                    XLog.w(
                        "宿主进程读到配置：接管=${key.enabled}" +
                                " 单击=${key.single.token} 双击=${key.double.token} 长按=${key.long.token}" +
                                "（文件 ${ConfigStore.sharedFile().path}）",
                    )
                }

                ConfigStore.Load.Absent -> XLog.w(
                    "宿主进程读配置：文件还不存在（首装 / 刚清过数据）—— 按默认值走，这是正常的一档",
                )

                ConfigStore.Load.Unreadable -> XLog.w(
                    "宿主进程读配置：**文件在但读不懂**（${ConfigStore.sharedFile().path}）" +
                            " —— 侧键会按「不接管」处理，别把默认值当成用户的配置",
                )
            }
        }
    }
}
