package com.lookie.opluskey.host

import android.annotation.SuppressLint
import android.content.Context
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.host.action.ActionRunner
import com.lookie.opluskey.host.action.ConditionProbe
import com.lookie.opluskey.host.action.FailureNotice
import com.lookie.opluskey.host.action.actionNoticeText

/**
 * **一次手势的执行**：过期丢弃 → 去重 → 读配置 → 判两级开关 → 振动 → 提示 → 交给执行器。
 *
 *  与 `GestureReceiver` 的分工：那个对象管**宿主进程的启动与进出**（取 Context、保活、报活、
 *  注册广播、收到手势后转给这里）；这个对象只管**一条手势进来之后发生的事**。两者原先同在一个
 *  572 行的单例里 —— 入口编排与业务执行混住，而这个文件是宿主进程崩溃风险最高的一处
 *  （任何异常顺着 `onReceive` 抛出去，宿主就反复重启），分开之后读的人能一眼看清哪段在做什么。
 *
 *  装配由 `GestureReceiver` 在拿到 Application Context 之后调 [attach] 完成：执行器与 Context
 *  都由它给，「动作执行完了」这一笔回执也由它接（那是报活的事，不是执行的事）。
 */
internal object GestureExec {
    private var runner: ActionRunner? = null

    /** 宿主进程的 Application Context —— 振动反馈与「执行时提示」要用它。 */
    @Volatile
    private var appContext: Context? = null

    /**
     * 最后一次**真的执行了动作**的时刻（`elapsedRealtime()`，0 = 从未）。
     *
     * 链路自检的两笔端到端实证之一：注入侧那笔（`KeyHooks.lastGestureAt`）证明「判出了手势」，
     * 这一笔证明「动作真被执行了」—— 两笔都有才叫整条链跑通。报活广播读它（`GestureReceiver.sendAlive`）。
     */
    @Volatile
    var lastActionAt: Long = 0L
        private set

    /**
     * 最近处理过的 (键, 手势) 的 `sent_at` —— 压掉转发侧「立刻 + 补发」两条的重复。
     *
     * 只有 `KeyId.entries` × 三种手势这几档，读写都在主线程，不需要额外同步。
     * 它是进程内的：宿主进程若在这 300ms 里被杀又重启，补发那条会被当成新的。
     */
    private val lastHandledAt = HashMap<String, Long>()

    /**
     * 手势的「保质期」（毫秒）：超过它视为冻结期间积压的过期手势，丢弃不执行。
     *
     * 正常链路送达延迟 ≈ 转发侧的 300ms + 排队，2s 已是宽裕上限；
     * 丢动作比「积压后一次性全执行」好（后者是不可预期的连环触发）。
     */
    private const val STALE_MS = 2000L

    /** 触觉反馈的时长（毫秒）—— 与最初宿主侧那路同一档（30ms 短振）。 */
    private const val HAPTIC_MS = 30L

    /**
     * 三档强度对应的振幅（0..255，见 `VibrationEffect.createOneShot`）。
     *
     *  三档都写成**具体值**、不用 `DEFAULT_AMPLITUDE`：那一档交给设备自己决定（各家 128~255 不等），
     *  真机上可能出现「中」比「强」还重。档次是本模块自己的语义，不该由 ROM 的默认值定义。
     */
    private val HAPTIC_AMPLITUDES =
        intArrayOf(
            60, // 弱
            140, // 中（默认）
            255, // 强
        )

    /**
     * 装配：执行器、Context，以及「刚执行完一个动作」的回执。
     *
     * 回执做成回调而不是让这里直接发广播：报活是宿主进程的入口编排职责（`GestureReceiver`），
     * 放在这一层会让「执行」认识「广播」。每执行一次动作都要补发一条报活 —— 单靠启动那一条，
     * 「设置生效之后真的按过」这一笔永远送不到设置页（用户按完去看「关于模块」，那行仍写着
     * 「未生效」，把成功报成了故障）。
     */
    fun attach(
        runner: ActionRunner,
        context: Context,
        onExecuted: (Config) -> Unit,
    ) {
        this.runner = runner
        appContext = context
        onExecutedHook = onExecuted
    }

    /** 上面的回执 —— 见 [attach]。 */
    @Volatile
    private var onExecutedHook: ((Config) -> Unit)? = null

    /**
     * 这一条该不该**直接丢掉** —— 过期与重复两道闸。
     *
     *  两段都用 `sentAt` 判，所以并成一个函数：`sentAt <= 0`（旧版本转发侧 / 手搓的广播缺这个
     *  extra）时**两段都跳过**，不能让它把后续手势全挡掉；`age < 0`（未来的时间戳）必须当过期
     *  丢弃 —— 否则它会以「最新时刻」写进去重表，之后所有正常手势都被当成补发永久吞掉。
     *
     * @return `true` = 丢掉（已经打过判据日志）
     */
    private fun staleOrDuplicate(
        key: KeyId,
        phaseId: String?,
        sentAt: Long,
    ): Boolean {
        if (sentAt <= 0L) return false
        val age = android.os.SystemClock.elapsedRealtime() - sentAt
        if (age > STALE_MS || age < 0L) {
            XLog.w("${key.label} 的 $phaseId 已过期或时间戳异常（age=${age}ms），丢弃不执行")
            return true
        }
        // 去重：转发侧对同一次手势会发两条（立刻 + 300ms 后补发，见 inject/input/GestureForwarder），
        // 两条 sent_at 相同 —— 按 (键, 手势) 记下最近处理过的时刻，sent_at <= 它的一律忽略。
        // 判据用 <= 而非 ==：两次按压间隔小于补发延迟时，补发那条会晚于下一次按压到达。
        val token = "${key.id}/$phaseId"
        val prev = lastHandledAt[token]
        if (prev != null && sentAt <= prev) {
            // 「按了没反应」有一整类是这个去重干的：补发那条被当成第二次手势时，
            //  日志里得有这一行，否则与「压根没收到」分不开。
            XLog.w("${key.label} 的 $phaseId 这一条已经处理过（补发的那条），忽略")
            return true
        }
        lastHandledAt[token] = sentAt
        return false
    }

    /**
     * 一个判好的手势：读配置 → 执行动作。
     *
     * 配置每次都重读（`ConfigStore.read()`）—— 文件在宿主私有目录里、同 uid 直接读，
     * 所以「改完即时生效」在这一侧天然成立。
     * 开关的执行侧闸门：判定在 system_server、配置来自广播，这里再按文件真值拦一道
     * （判据只有一处：`Config.isLive` = 总闸 且 这一颗键），两边不会各说各话。
     */
    fun handle(
        keyId: String?,
        phaseId: String?,
        repeat: Boolean,
        sentAt: Long,
    ) {
        val key = resolveKey(keyId, phaseId) ?: return

        // 松手不是手势：它只给「按下开始、松手结束」的动作收尾（此刻只有灵感语音）。
        //  放在过期 / 去重两道闸**之前**：那两道是给「冻结期间积压的手势」用的，松手即使晚到
        //  也只会被 `ActionRunner.release` 那道 `voiceMemoryOn` 闸挡住（两处判据不重叠）。
        if (phaseId == ModuleWire.PHASE_RELEASE) {
            onRelease(key)
            return
        }

        if (staleOrDuplicate(key, phaseId, sentAt)) return

        val gesture =
            when (phaseId) {
                Gesture.SINGLE.id -> {
                    Gesture.SINGLE
                }

                Gesture.DOUBLE.id -> {
                    Gesture.DOUBLE
                }

                Gesture.LONG.id -> {
                    Gesture.LONG
                }

                else -> {
                    XLog.w("不认识的手势：$phaseId")
                    return
                }
            }

        dispatch(key, gesture, repeat)
    }

    /**
     * 键身份按**当前配置**解析（`Config.keyIdOf`）：自定义按键是用户运行时收集来的，
     *  只存在于配置的 `custom_keys` 那段里。注入侧认领时用的也是这份身份。
     *  `ConfigStore.read()` 有带指纹的读缓存，这里多读一次只是一次 `stat` 比对。
     *
     *  执行器没建好与「键不认识」都是「这一下不管」，放在同一个出口里 —— 也免得 [handle]
     *  光前置检查就攒了五六个 `return`。
     */
    private fun resolveKey(
        keyId: String?,
        phaseId: String?,
    ): KeyId? {
        val key = ConfigStore.read().keyIdOf(keyId.orEmpty())
        if (key == null) {
            XLog.w("不认识的键 id：$keyId")
            return null
        }
        if (runner == null) {
            XLog.w("ActionRunner 还没建好，忽略 ${key.label} 的 $phaseId")
            return null
        }
        return key
    }

    /** 两级开关与执行 —— handle 的最后一站。 */
    private fun dispatch(
        key: KeyId,
        gesture: Gesture,
        repeat: Boolean,
    ) {
        val config = ConfigStore.read()
        val keyConfig = config.key(key)
        if (!config.isLive(key)) {
            val why = if (!config.global.enabled) "总闸已关" else "这颗键已停用"
            XLog.w("${key.label}：$why，忽略这次 ${gesture.label}（模块不生效）")
            return
        }
        runAction(key, gesture, keyConfig, repeat, config)
    }

    /**
     * 「松手」 —— 给「按下开始、松手结束」的动作收尾。
     *
     *  判据是 [KeyConfig.longIsVoiceMemory]：判定侧边也用它（不许连续触发），两处同一个口径 ——
     *  「长按绑了灵感语音」就等于「这一按松手要收尾」，没有第二种组合。
     *
     *  开关照旧拦一道：关掉模块之后不该再替用户去动别人的麦克风。
     */
    private fun onRelease(key: KeyId) {
        val config = ConfigStore.read()
        val keyConfig = config.key(key)
        if (!config.isLive(key)) {
            XLog.w("${key.label}：模块或这颗键已关，忽略这一下松手")
            return
        }
        if (!keyConfig.longIsVoiceMemory) {
            XLog.d("${key.label}·${Gesture.LONG.label} 松手：绑的不是灵感语音，没有要收尾的东西")
            return
        }
        runner?.release()
    }

    /**
     * 执行一个手势对应的动作。
     *
     * 注意：**触觉反馈的唯一一处就在这里**（先说「只要不无操作立即振动」、
     * 又说「整体服务没起来时按键不要振动，不要会误以为功能能用」—— 后者把位置钉死在宿主侧）。
     *
     * 「振了」必须等价于「服务在跑、动作马上要执行」：
     *  · 宿主进程没起来 / 模块没注入进宿主 ⇒ 这个方法根本走不到 ⇒ **不振**（就是要这样：
     *    振了却什么都没发生，用户会以为功能好用，然后去怪系统）；
     *  · 判定侧（system_server）振得不到这个保证 —— 那边判出手势就振，可手势转给宿主之后
     *    宿主在不在、能不能执行，它并不知道（它只发广播）。
     *
     *  代价是比「按下那刻就振」晚一趟广播（宿主常驻时几毫秒，感知不到；宿主刚被拉起时要等
     *  那 300ms 的补发，但那时**动作**也是 300ms 才发生 —— 振与动作仍然同时）。
     *
     * 「振动反馈」开关关着、以及「无操作」（吞键什么都不做）都不振。连续触发的重复由那颗键自己的
     * 「连续触发时振动」决定：关着时只在长按那一下振一次，开着时每次重复都振。
     */
    private fun runAction(
        id: KeyId,
        gesture: Gesture,
        keyConfig: KeyConfig,
        repeat: Boolean,
        config: Config,
    ) {
        // 条件执行：先在这颗键这一档的**顺序表**里找，命中第一条就执行它；一条都不中就走
        //  [KeyConfig.actionOf] 那一档本来的绑定 —— 所以分叉不占用绑定，两件事互不影响。
        //  判据现判（按下的那一刻）：缓存会让「切到微信再按」仍按上一次的应用走。
        val action =
            keyConfig.actionFor(gesture) { condition ->
                appContext?.let { ConditionProbe.matches(condition, it) } == true
            }
        val r =
            runner ?: run {
                XLog.w("ActionRunner 还没建好，忽略 ${id.label} 的 ${gesture.id}")
                return
            }
        // 这条是按键的可见证据（release 里也保留），所以只在第一次执行时打 W；
        val log =
            "${id.label}·${gesture.label}${if (repeat) "（连续触发）" else ""} → ${XLog.redactToken(action.token)}" +
                "（宿主进程执行）"
        if (repeat) XLog.d(log) else XLog.w(log)
        // 链路自检：这一笔是「动作真的执行了」的凭证（与注入侧那笔 lastGestureAt 配对）
        lastActionAt = android.os.SystemClock.elapsedRealtime()
        //  报活广播是**一次性**的（装在启动那一刻发一条），单靠它，「设置生效之后真的按过」这一笔
        //  永远送不到设置页 —— 用户按完侧键去看「关于模块」，那行仍写着「未生效」，把成功报成了故障。
        //  所以每执行一次动作补发一条（连续触发的重复不发：那是同一次按压，一条就够）。
        if (!repeat) onExecutedHook?.invoke(config)
        if (shouldVibrate(repeat, keyConfig, action, config)) haptic(config)
        if (!repeat) noticeIfNeeded(config, action)
        r.run(action, "${id.label}·${gesture.label}")
    }

    /**
     * 这一下要不要**在动作之前**振一下。
     *
     *  连续触发的重复由那颗键自己的「连续触发时振动」决定：关着时只在长按判出来的那一下
     *  振一次，开着时每次重复都振。三种情况不振：
     *  · 「无操作」—— 吞键什么都不做，振了就成了「做了点什么」的假证据；
     *  · **切响铃这一档走 ColorOS 接口时** —— 那一下由执行侧在切完之后放
     *    （`bridge/host/RingerHaptics`，与系统侧键切档同一份效果号），这里再振就是两下；
     *    AOSP 接口那条路不走例外（没有 ColorOS 的效果可用），照旧用这一下通用的。
     */
    private fun shouldVibrate(
        repeat: Boolean,
        keyConfig: KeyConfig,
        action: Action,
        config: Config,
    ): Boolean {
        if (action is Action.None) return false
        if (!repeat || keyConfig.longRepeatVibrate) return !colorOsRinger(action, config)
        return false
    }

    /**
     * 「执行时提示」：判据是全局开关（`GlobalConfig.toastOnRun`），内容是**这次执行的是哪个动作**。
     *
     *  钉在这一处（一次手势只走一遍）而不是 `CustomCommandRunner` 里 —— 那一处只覆盖 `cmd:`
     *  自定义命令，绑的是内置动作（小布识屏 / 截图 / 手电筒 …）时按下去一直没动静
     *  （用户报「执行命令时系统提示没效果」）。连续触发的重复不弹：那是同一次按压。
     */
    private fun noticeIfNeeded(
        config: Config,
        action: Action,
    ) {
        if (!config.global.toastOnRun) return
        // 语言档跟配置走：设置页那边建页时设过一次，但那是**另一个进程**（`:ui`）里的实例，
        //  宿主主进程这份一直停在 AUTO（跟系统语言）。配置里选了 English 而系统是中文时，
        //  界面英文、提示中文 —— 这里每次执行前对一次，两边同一个口径。
        //  同一个文件不重读（`reload` 里按文件名早返回），所以这一行在按键路径上是白给的。
        I18n.currentLanguage = AppLanguage.fromCode(config.global.language)
        // 名字表随配置来（`Config.appNames`）：快捷方式那档的名字只存在那里，token 里没有
        val notice = actionNoticeText(appContext, action, config.appNames)
        if (notice.isEmpty()) return
        XLog.w("执行时提示：$notice")
        FailureNotice.toast(appContext, notice)
    }

    /**
     * 动作即将执行前那一下触觉反馈 —— 位置与判据见 [runAction] 的 KDoc。
     *
     * 跑在主线程上，一切异常就地吞掉：振不动不该影响动作执行，更不该让宿主进程崩。
     *
     *  `MissingPermission` 这一条按「谁在执行」判：这段跑在**宿主**（`com.oplus.gesture`）里，
     *  而它自己声明并被授予了 `android.permission.VIBRATE`（真机 `dumpsys package` 核对过）。
     *  模块清单里**不**声明这一条 —— 写上它也管不到宿主那个 UID，只会让人以为模块自己有个
     *  进程在振。
     */
    @SuppressLint("MissingPermission")
    private fun haptic(config: Config) {
        if (!config.global.vibrate) return
        try {
            val ctx = appContext ?: return
            val v = ctx.getSystemService(android.os.Vibrator::class.java) ?: return
            // 越界的强度在 `GlobalConfig.ofFields` 那一层已经钳过（手改文件也能进来），
            //  这里再兜一次：索引越界会抛 IndexOutOfBounds，而这段跑在按键路径上。
            val amplitude = HAPTIC_AMPLITUDES[config.global.vibrateStrength.coerceIn(HAPTIC_AMPLITUDES.indices)]
            v.vibrate(android.os.VibrationEffect.createOneShot(HAPTIC_MS, amplitude))
        } catch (e: Throwable) {
            XLog.w("触觉反馈失败（忽略）：${e.message}")
        }
    }
}

/**
 * 这一次「切响铃」是不是走 ColorOS 接口（振动的去处不同，见 [GestureExec] 里的 `shouldVibrate`）。
 *
 *  拓扑上是纯函数（只看动作与开关），放在对象外：`GestureExec` 的成员已经贴着
 *  `TooManyFunctions` 的阈值，而这一条不值得为它挤掉一个真正属于「执行」的方法。
 */
private fun colorOsRinger(
    action: Action,
    config: Config,
): Boolean {
    if (!config.global.colorOsApi) return false
    if (action is Action.Ringer) return true
    return action is Action.KeyEvent && action.keyCode == KeyId.KEYCODE_VOLUME_MUTE
}
