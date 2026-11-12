package com.lookie.opluskey.host

import android.app.Service
import android.content.Intent
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

    /** 宿主服务的启动入口方法名（`Service` 的覆写名）—— 见 [hookKeyServiceStart]。 */
    private val ON_START_COMMAND = Framework.HOST_ON_START_COMMAND

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
     *  `docs/device/side-key.md` 十六），所以按「刚刚有没有认领过」判，不做精确配对。
     *
     *  取值受 [CLAIM_WINDOW_MAX_MS] 约束：**不得大于扣留时长**（理由见那一项）。所以它就是
     *  [HOLD_MS] 这个数 —— 判定与派发之间的实测时延是几毫秒到几十毫秒，150ms 已经宽裕；
     *  再往上（曾经是 300ms）会盖住**下一次**按压：那一次的真实派发被当成「上一次认领的」
     *  吞掉，表现成偶发的「按了没反应」。
     */
    private const val CLAIM_WINDOW_MS = 150L

    /**
     * 没认领时**扣住**多久再看一眼（毫秒）。
     *
     *  150ms：够判定那条广播追上派发（实测同机几毫秒到几十毫秒），又不至于让「停用的键」的原生
     *  动作明显变慢 —— 那一档的代价就是这半拍。
     */
    private const val HOLD_MS = 150L

    /**
     * 认领窗口的**上限** —— 就是扣留时长（[HOLD_MS]）。
     *
     *  两个窗口的大小关系是硬判据：**认领窗口不得大于扣留时长**。扣留期满会补投原生动作，而认领
     *  窗口如果更长，一条在补投**之后**才到的认领仍然算「这一下是模块的」—— 原生动作已经补投、
     *  模块动作又执行一次，一次按压出两个动作。只有扣留时长盖住整个认领窗口，那条迟到的认领才
     *  轮不到（到那时它已经超出窗口、按「不是这一下」处理）。
     *
     *  越界时 [install] 记一行 W（见 [checkWindowOrder]）：这两个数是各自按经验定的，改一个忘了
     *  另一个时，日志里至少留一句，别让它在真机上只表现成「偶尔两个动作」。
     */
    private const val CLAIM_WINDOW_MAX_MS = HOLD_MS

    /**
     * 两个窗口满足上面那条关系吗 —— 判据本体收在这里，[install] 读它（见 [checkWindowOrder]）。
     *
     *  不在判据里直接比两个 `const val`：那是一段编译期常量比较，编译器会报「条件恒为真」，
     *  而本工程开着 `allWarningsAsErrors`。写成属性之后值仍由那两个常量算出、运行期不会变，
     *  只是不再被当成字面量条件。
     */
    private val windowOrderOk: Boolean = CLAIM_WINDOW_MS <= CLAIM_WINDOW_MAX_MS

    /**
     * 已经补投出去、还没被自己再看到的那几条派发（按**实例身份**记）。
     *
     *  补投回来的那一条会再过一次本 Hook，不认出来就会「再扣一次、再补投一次」，永远转圈。
     *  原先记的是一个**全局计数**：补投还没回来时，任何一条别的派发都会把这个计数吃掉、
     *  被当成回放直接放行 —— 那一下本该被吞的原生动作就跟着弹了出来。按实例认不会串。
     */
    private val replays: MutableSet<Message> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    @Volatile
    private var installed = false

    /**
     * 「`onStartCommand` 正文已掐掉」那行判据报过没有 —— 那段正文每次唤醒都走，只报第一次
     * （见 [hookKeyServiceStart]）。
     */
    @Volatile
    private var startSwallowedLogged = false

    /** 主线程队列 —— 把诊断探测挪出安装回调用（见 [install]）。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    // 场景派发（`what=1`）的处置：**模块认领的那一下吞掉，没认领的放行**。
    //  「放行（`native` / `passthrough`）动作档」那一套早就删了（见 `docs/runtime/key-to-gesture.md`
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

        // 两个窗口的大小关系（认领窗口 <= 扣留时长）—— 越界只记一行，不阻断安装
        checkWindowOrder()

        // 先在宿主进程读一遍设置页写的那份配置（同 uid，理应读得到）—— 判「配置通道通不通」。
        logConfig()

        // 再读一次系统侧键配置（纯诊断）。它要 `ContentResolver.query`，而 install 跑在目标进程的
        //  **包加载主线程**上 —— 同步查会把这条线程占住、拖慢安装，所以丢给主线程队列延后做：
        //  安装回调先返回，探测在随后一条主线程消息里跑。读不到不影响任何功能（见 [probeSystemConfig]）。
        mainHandler.post { probeSystemConfig(loader) }

        var n = 0
        n += hookService(module, loader, START_APP_SERVICE)
        n += hookService(module, loader, KEY_SERVICE)
        n += hookKeyServiceStart(module, loader)

        installed = n > 0
        if (n > 0) {
            XLog.w("侧键手势劫持 Hook 安装完成：$n 个接管点（宿主主进程）")
        } else {
            XLog.w("侧键手势接管点一个都没找到 —— 宿主改了服务名？本机退化为「不劫持手势」")
        }
    }

    /**
     * 判一次两个窗口的大小关系（见 [CLAIM_WINDOW_MAX_MS]）—— 越界记 W，不阻断安装。
     *
     *  只在安装时问一次：两个数都是常量，运行期不会变；而这条判据本身是「改代码时忘了对齐」的提示，
     *  放在按键路径上只会每按一次刷一行。
     */
    private fun checkWindowOrder() {
        if (windowOrderOk) return
        XLog.w(
            "侧键窗口越界：认领窗口 ${CLAIM_WINDOW_MS}ms > 扣留 ${HOLD_MS}ms —— " +
                "补投之后才到的认领仍会被判成「模块的」，同一次按压可能出原生 + 模块两个动作",
        )
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
     * 掐掉宿主服务 `onStartCommand` 的正文 —— 模块启动它只为**唤醒 / 保活**，不是要它开页面。
     *
     * ## 正文里有什么（`ActionButtonKeyService.onStartCommand`，反编译核对）
     *
     * ```
     * if (GestureUtil.isSupportActionButtonKey() && lb.d.a().d()) d()
     *   → Handler.obtainMessage(1)  ⇒  handleMessage(what=1) → ActionButtonKeyService.a() → e()
     *   → e() 就是「起 ActionButtonSettingsActivity」，也就是**被模块替换掉的模块页**
     * ```
     *
     * `lb.d.d()` 是「当前有任一拦截条件成立」：锁屏 / 熄屏 / 游戏模式 / 免打扰 / 超级省电 /
     * 用户还没设置完。**开机后那一段正是锁屏**，而模块的唤醒（`inject/input/GestureForwarder` 与
     * `host/GestureReceiver` 的 keepProcessAwake）都走 `startService` ⇒ 用户看到的是
     * 「刚开机按一下侧键，模块页自己弹出来」。
     *
     * ## 为什么整段掐掉是安全的
     *
     * - 唤醒：绑定那条路（`BIND_AUTO_CREATE`）本来就能把进程创建出来，不依赖这个正文；
     * - 保活：要的是「本进程持有一个已启动的服务」这件事本身，正文对进程级别没有贡献；
     * - 原生动作：框架找宿主走的是 **bind + `Messenger`**（`StartAppService` / 本服务的 `onBind`），
     *   与 `onStartCommand` 无关 —— 掐掉它不影响任何一路手势派发。
     *
     * 失败的降级就是「锁屏时按侧键偶尔弹一下模块页」，不影响按键接管本身。
     *
     * @return 这一次装上算一个接管点（0 = 没装上，已记日志）
     */
    private fun hookKeyServiceStart(
        module: XposedModule,
        loader: ClassLoader,
    ): Int {
        val cls =
            Reflect.findClass(loader, KEY_SERVICE) ?: run {
                XLog.w("没找到宿主服务：$KEY_SERVICE（它的 $ON_START_COMMAND 不去碰）")
                return 0
            }
        val m =
            Reflect.method(
                cls,
                ON_START_COMMAND,
                Intent::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            ) ?: run {
                XLog.w("${cls.name} 上没有 $ON_START_COMMAND(Intent, I, I) —— 这个方法名也随 ROM 变")
                return 0
            }
        return if (module.hookGuarded(m) {
                //  判据级：这一行是「模块页为什么不再自己弹出来」的唯一凭据，只能用 w；
                //  而这条正文每次唤醒都会走（每个手势一条），只报第一次，之后压回 d 免刷屏。
                if (!startSwallowedLogged) {
                    startSwallowedLogged = true
                    XLog.w("宿主服务 $ON_START_COMMAND：启动它只为唤醒 / 保活，不再顺带把设置页带到前台")
                } else {
                    XLog.d("宿主服务 $ON_START_COMMAND：正文已掐掉（这一下只为唤醒 / 保活）")
                }
                Service.START_NOT_STICKY
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
     *  宿主主线程忙时派发会早到几十毫秒（`docs/device/side-key.md` 十六实测过另一个方向）。
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
        //  少了这个出口就会再扣一次、再补投一次，永远转圈；按实例认，别的派发不会误吃。
        if (msg != null && replays.remove(msg)) {
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
                    replays.add(copy)
                    try {
                        handler.sendMessage(copy)
                    } catch (e: Throwable) {
                        //  补投失败只是这一次原生动作丢了（键看着没反应），不能让异常上主线程
                        replays.remove(copy)
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

                //  主文件坏、但 `.bak` 读得懂：跑的是**备份那份**（比最后一次成功写入旧一个版本）。
                //  这一行要说出来 —— 它是「用户某一项改动不见了」的唯一线索。
                is ConfigStore.Load.Recovered -> {
                    val key = l.config.key(KeyId.PLUS)
                    XLog.w(
                        "宿主进程读配置：主文件读不懂，已用 .bak 恢复（接管=${key.enabled}" +
                            " 单击=${XLog.redactToken(key.single.token)}）",
                    )
                }
            }
        }
    }
}
