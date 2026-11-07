package com.lookie.opluskey.host

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.bridge.hookGuarded
import com.lookie.opluskey.bridge.host.SystemActionButtonConfig
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.reflect.Reflect
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/**
 * 劫持系统自己判好的侧键手势（宿主主进程 `com.oplus.gesture`）。
 */
internal object SideKeyGestureHooks {
    /** 「打开应用」场景的宿主服务（框架按场景表里的 `click_action_info` 绑它）。 */
    private val START_APP_SERVICE = Framework.HOST_START_APP_SERVICE

    /** 其余场景的宿主服务。 */
    private val KEY_SERVICE = Framework.HOST_KEY_SERVICE

    /**
     * 收到它时宿主要按系统当前场景去开页面 / 起服务；模块接管侧键后必须把它吞掉，
     * 否则系统那套动作会和模块的动作一起执行。
     *
     * 注意：取值本体在 `bridge/Framework.kt` 的候选表里（一、7），这里只是别名。
     */
    private val SCENE_DISPATCH_WHAT = Framework.HOST_SCENE_DISPATCH_WHAT

    /**
     * 「这一下是模块认领的」那个时间窗（毫秒）。
     *
     *  判定与这条派发**同一轮按压里到**，但先后是竞态的（宿主主线程忙时派发会早到，见
     *  `docs/device-findings.md` 十六），所以按「刚刚有没有认领过」判，不做精确配对。
     *  300ms：远小于人手两次按压的间隔（误吞概率低），又远大于判定与派发之间的几十毫秒。
     */
    private const val CLAIM_WINDOW_MS = 300L

    /**
     * 没认领时**扣住**多久再看一眼（毫秒）。
     *
     *  150ms：够判定那条广播追上派发（实测同机几毫秒到几十毫秒），又不至于让「停用的键」的原生
     *  动作明显变慢 —— 那一档的代价就是这半拍。
     */
    private const val HOLD_MS = 150L

    /**
     * 已经补投出去、还没被自己再看到的那几条派发。
     *
     *  补投回来的那一条会再过一次本 Hook，没有这个计数就会「再扣一次、再补投一次」，永远转圈。
     */
    @Volatile
    private var pendingReplay = 0

    @Volatile
    private var installed = false

    /** 主线程队列 —— 把诊断探测挪出安装回调用（见 [install]）。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    // 场景派发（`what=1`）的处置：**模块认领的那一下吞掉，没认领的放行**。
    //  「放行（`native` / `passthrough`）动作档」那一套早就删了（见 `docs/implementation.md`
    //  第三节），这里留下的是另一件事：**停用的键**（`KeyConfig.enabled = false`）在注入侧是
    //  原样放行的，它的原生动作就靠这条派发来跑 —— 一条都不放等于把停用的键弄失灵。
    //  判据在 [PressTrace]：那条消息不带键的身份，只能问「刚刚有没有认领过一下」。

    fun install(
        module: XposedModule,
        loader: ClassLoader,
    ) {
        if (installed) {
            XLog.w("手势劫持 Hook 已经装过，跳过重复安装")
            return
        }

        // 先在宿主进程读一遍设置页写的那份配置（同 uid，理应读得到）—— 判「配置通道通不通」。
        logConfig()

        // 再读一次系统侧键配置（纯诊断）。它要 `ContentResolver.query`，而 install 跑在目标进程的
        //  **包加载主线程**上 —— 同步查会把这条线程占住、拖慢安装，所以丢给主线程队列延后做：
        //  安装回调先返回，探测在随后一条主线程消息里跑。读不到不影响任何功能（见 [probeSystemConfig]）。
        mainHandler.post { probeSystemConfig(loader) }

        var n = 0
        n += hookService(module, loader, START_APP_SERVICE)
        n += hookService(module, loader, KEY_SERVICE)

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
    private fun hookService(
        module: XposedModule,
        loader: ClassLoader,
        className: String,
    ): Int {
        val cls = Reflect.findClass(loader, className)
        if (cls == null) {
            XLog.w("没找到宿主服务：$className（本机可能不是 ColorOS，或服务改名了）")
            return 0
        }

        // 内部 Handler：`XxxService$后缀`。类名是混淆后的（后缀按候选试，见 `Framework` 的候选表，
        // 一、7），命中哪一个由那一层打日志 —— 找不到就只记日志，别硬猜一个名字去挂。
        val handlerCls = Framework.findHostHandlerClass(loader, className) ?: return 0

        // 日志里用**实际命中**的那个 Handler 类名（不是服务名）——后缀是候选试出来的，
        //  写死一个后缀的话换 ROM 之后日志会指着一个不存在的类。
        val handlerSimple = handlerCls.name.substringAfterLast('.')

        val method = Framework.HOST_HANDLE_MESSAGE
        val m =
            Reflect.method(handlerCls, method, Message::class.java) ?: run {
                XLog.w("${handlerCls.name} 上没有 $method(Message) —— 这个方法名也随 ROM 变")
                return 0
            }
        return if (module.hookGuarded(m) { chain ->
                val msg = chain.args.getOrNull(0) as? Message
                val what = msg?.what ?: -1
                // 这条是每次 Message 都打的（含每条按键消息），只能 XLog.d ——
                //  并且**必须用惰性重载**：`describeMessage` 会把整个 Bundle 摊平成一个字符串，
                //  参数若在调用点先求值，release 下（`if (VERBOSE_LOG)` 把函数体消掉）也照跑一遍。
                XLog.d { "侧键手势消息：$handlerSimple#handleMessage | ${describeMessage(msg)}" }

                //  场景派发（`what=1`）的处置单独一支：它要吞、要放、还要「先扣住再看一眼」，
                //  全都摊在这里会把这条 lambda 撑过 detekt 的长度线。判据与理由见 [onSceneDispatch]。
                if (what == SCENE_DISPATCH_WHAT) return@hookGuarded onSceneDispatch(chain, msg)

                chain.proceed()
            } != null
        ) {
            1
        } else {
            0
        }
    }

    /**
     * 场景派发（`what=1`）的处置 —— 吞掉 / 放行 / 扣住再看一眼。
     *
     *  这条消息**不带键的身份**（`data` 空、`arg1`/`arg2` 恒 0），所以判据只能是
     *  「这一下是不是模块认领的」（[PressTrace]）。按某一颗键自己的开关判就会拿 A 键的状态去决定
     *  B 键那次派发的去向；一律吞则会把停用的键的原生动作一起弄没。
     *
     *  ## 为什么还要扣一段时间
     *
     *  认领（注入侧判出手势后发的广播）与这条派发**同一轮按压里到**，先后是竞态的 ——
     *  宿主主线程忙时派发会早到几十毫秒（`docs/device-findings.md` 十六实测过另一个方向）。
     *  只按「已经到没到」判会漏：真机上表现为「偶尔还是弹一下页面」。
     *  所以没认领时**先扣住 [HOLD_MS]**，到点再判一次：期间补到了认领就丢掉，仍没有才补投原生动作。
     *
     * @return `null` = 吞掉；其余 = 放行（`chain.proceed()` 的返回值）
     */
    private fun onSceneDispatch(
        chain: XposedInterface.Chain,
        msg: Message?,
    ): Any? {
        //  补投回来的那一条：上一次已经判过「没认领」⇒ 直接放行。
        //  少了这个出口就会再扣一次、再补投一次，永远转圈。
        if (pendingReplay > 0) {
            pendingReplay--
            XLog.w("场景派发放行（补投回放，原生行为）")
            return chain.proceed()
        }
        val since = PressTrace.sinceLastClaim()
        if (since <= CLAIM_WINDOW_MS) {
            //  判据级：这几条是「这次派发走没走模块」的判据 —— 必须用 w，
            //  用 d 会在 release 里被编译期消除 ⇒ 线上完全无痕，只能靠猜。
            XLog.w("场景派发已吞掉（这一下是模块认领的，判定在 ${since}ms 前到）")
            return null
        }
        val handler = chain.thisObject as? Handler
        if (handler == null || msg == null) {
            XLog.w("场景派发已放行（拿不到 Handler / Message，不扣留 → 原生行为）")
            return chain.proceed()
        }
        //  补投必须**复制**一条：原实例在这次 `handleMessage` 返回后就被 Looper 回收进复用池了
        val copy = Message.obtain(msg)
        mainHandler.postDelayed(
            {
                val after = PressTrace.sinceLastClaim()
                //  `after < since` ⇒ 扣留期间补到了新的认领（时间戳变新了）：这一下归模块，丢掉
                if (after < since) {
                    XLog.w("场景派发扣留后补到认领（${after}ms 前）→ 丢掉，这一下归模块")
                } else {
                    XLog.w("场景派发扣留 ${HOLD_MS}ms 仍没认领 → 补投原生动作")
                    pendingReplay++
                    try {
                        handler.sendMessage(copy)
                    } catch (e: Throwable) {
                        //  补投失败只是这一次原生动作丢了（键看着没反应），不能让异常上主线程
                        pendingReplay--
                        XLog.w("补投场景派发失败（这一次原生动作丢失）：${e.javaClass.simpleName}", e)
                    }
                }
            },
            HOLD_MS,
        )
        return null
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
        val keys =
            try {
                b.keySet()
            } catch (e: Throwable) {
                return "(取键失败：${e.message})"
            }
        if (keys.isEmpty()) return "(空)"
        return keys.joinToString(prefix = "{", postfix = "}") { k ->
            val v =
                try {
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
        val ctx =
            ActivityThreadCompat.currentApplication(loader) ?: run {
                XLog.d("拿不到 Application Context，跳过系统侧键配置探测")
                return
            }
        XLog.guard("读系统侧键配置") {
            val rows = SystemActionButtonConfig.readAll(ctx)
            when {
                rows == null -> {
                    XLog.w("系统侧键配置读取失败（诊断项，不影响接管）")
                }

                rows.isEmpty() -> {
                    XLog.w("系统侧键配置为空（用户还没在「设置 → 快捷键」里配过）")
                }

                else -> {
                    XLog.w(
                        "系统侧键配置共 ${rows.size} 个场景：${
                            rows.joinToString(", ") { it.sceneName }
                        }",
                    )
                    rows.firstOrNull()?.let { r ->
                        XLog.d(
                            "首个场景「${r.sceneName}」的列值：${
                                r.values.entries.joinToString(", ") { "${it.key}=${it.value}" }
                            }",
                        )
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
                    val key = l.config.key(KeyId.PLUS)
                    //  三个 token 都过 [XLog.redactToken]（`shell:` / `intent:` 的正文可能带凭据）
                    XLog.w(
                        "宿主进程读到配置：接管=${key.enabled}" +
                            " 单击=${XLog.redactToken(key.single.token)}" +
                            " 双击=${XLog.redactToken(key.double.token)}" +
                            " 长按=${XLog.redactToken(key.long.token)}" +
                            "（文件 ${ConfigStore.sharedFile().path}）",
                    )
                }

                ConfigStore.Load.Absent -> {
                    XLog.w(
                        "宿主进程读配置：文件还不存在（首装 / 刚清过数据）—— 按默认值走，这是正常的一档",
                    )
                }

                ConfigStore.Load.Unreadable -> {
                    XLog.w(
                        "宿主进程读配置：**文件在但读不懂**（${ConfigStore.sharedFile().path}）" +
                            " —— 侧键会按「不接管」处理，别把默认值当成用户的配置",
                    )
                }
            }
        }
    }
}
