package com.lookie.opluskey.host

import android.annotation.SuppressLint
import android.content.Context
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ComboKey
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
 *
 *  成员数贴着 `TooManyFunctions` 的阈值：这是一颗执行器的**完整方法集**（判定重复、派发、
 *  收尾、振动、提示各一支），拆到别的文件里每个调用点都要跨文件跳一次，换不来可读性。
 */
@Suppress("TooManyFunctions")
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
     * @param subject 去重表里的那一列。单键传 `KeyId.id`，组合传 `combo:<id>` ——
     *   两者共用一张表，但**不能撞名**（组合同样是「一次按压只执行一次」，补发那条的
     *   `sent_at` 与立刻那条相同）。
     * @return `true` = 丢掉（已经打过判据日志）
     */
    private fun staleOrDuplicate(
        subject: String,
        phaseId: String?,
        sentAt: Long,
    ): Boolean {
        if (sentAt <= 0L) return false
        val age = android.os.SystemClock.elapsedRealtime() - sentAt
        if (age > STALE_MS || age < 0L) {
            XLog.w("$subject 的 $phaseId 已过期或时间戳异常（age=${age}ms），丢弃不执行")
            return true
        }
        // 去重：转发侧对同一次手势会发两条（立刻 + 300ms 后补发，见 inject/input/GestureForwarder），
        // 两条 sent_at 相同 —— 按 (键, 手势) 记下最近处理过的时刻，sent_at <= 它的一律忽略。
        // 判据用 <= 而非 ==：两次按压间隔小于补发延迟时，补发那条会晚于下一次按压到达。
        val token = "$subject/$phaseId"
        val prev = lastHandledAt[token]
        if (prev != null && sentAt <= prev) {
            // 「按了没反应」有一整类是这个去重干的：补发那条被当成第二次手势时，
            //  日志里得有这一行，否则与「压根没收到」分不开。
            XLog.w("$subject 的 $phaseId 这一条已经处理过（补发的那条），忽略")
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
        //  也只会被 `ActionRunner.stopVoiceCapture` 那道 `voiceMemoryOn` 闸挡住（两处判据不重叠）。
        if (phaseId == ModuleWire.PHASE_RELEASE) {
            onRelease(key)
            return
        }

        if (staleOrDuplicate(key.id, phaseId, sentAt)) return

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
     * 一次**组合键**（两颗键一起按）：读配置 → 认那一档 → 两级闸门 → 振动 → 提示 → 执行。
     *
     *  与 [handle] 分开两支：组合没有单颗键的 `KeyConfig`（没有三档绑定、没有条件执行、
     *  没有连发），硬塞进同一条路会让那边到处判 `keyConfig` 在不在 —— 而这两件事本来就只有
     *  「一次按压执行一个动作」这一点相同。
     *
     *  判两遍是有意的：注入侧判一次（`ConfigChannel.comboFor`，没绑定就不成立组合），
     *  这里按**文件真值**再判一次（总闸、成员键、档位）。判定在 system_server、配置在文件，
     *  两边各按手上的真值走 —— 与单键那条 `Config.isLive` 闸同一个道理。
     */
    fun handleCombo(
        comboId: String,
        phaseId: String?,
        sentAt: Long,
        repeat: Boolean,
    ) {
        val config = ConfigStore.read()
        val combo = liveCombo(config, comboId) ?: return
        if (staleOrDuplicate("combo:${combo.id}", phaseId, sentAt)) return
        // 双击在组合上没有定义（`ComboKey.actionOf` 恒返回「无操作」）；认不出的 phase
        //  只可能来自手搓的广播。
        val gesture = comboGesture(phaseId) ?: return
        //  条件执行：与单键那一支同一套 —— 先在这档的顺序表里找，第一条成立的就执行它，
        //  一条都不中才落到 `actionOf` 那一档绑定。判据现判（按下的那一刻），且**一次按下的
        //  一次判定里同一个条件只判一次**（一块可以带好几条，几条规则之间又常重复）。
        val judged = HashMap<BranchCondition, Boolean>()
        val action =
            combo.resolve(gesture) { condition ->
                judged.getOrPut(condition) { appContext?.let { ConditionProbe.matches(condition, it) } == true }
            }
        //  这一格是「无操作」且开关打开 ⇒ **不吞键**：两颗键各按自己这一档照常执行
        //  （见 `GlobalConfig.comboPassthrough`）。这就是「配了组合、但只给长按绑了动作」时
        //   短按那颗依旧能用的那条路 —— 为假时什么都不做，与这一位出现之前一致。
        if (action is Action.None && config.global.comboPassthrough) {
            runComboMembers(combo, gesture, config)
            return
        }
        executeCombo(combo, gesture, action, config, repeat)
    }

    /**
     * 这一档现在算不算数 —— 三步并成一个出口：认得出、没停用、两道闸门都放行。
     *
     *  @return `null` = 这一下不执行（原因已打日志）
     */
    private fun liveCombo(
        config: Config,
        comboId: String,
    ): ComboKey? {
        val combo =
            config.combos.firstOrNull { it.id == comboId } ?: run {
                XLog.w("不认识的组合键 id：$comboId")
                return null
            }
        if (!combo.isUsable) {
            XLog.w("组合键 ${combo.id} 已停用（或成员不足两键），忽略这次")
            return null
        }
        return if (comboGate(config, combo)) combo else null
    }

    /** 组合键上认得出的两档（双击在组合上没有定义，见 `ComboKey`）。 */
    private fun comboGesture(phaseId: String?): Gesture? =
        when (phaseId) {
            Gesture.SINGLE.id -> {
                Gesture.SINGLE
            }

            Gesture.LONG.id -> {
                Gesture.LONG
            }

            else -> {
                XLog.w("组合键不支持的手势：$phaseId")
                null
            }
        }

    /**
     * 组合键那一下的收尾 —— 日志、振动、提示、执行、报活。
     *
     *  拆出来只为让 [handleCombo] 留在 detekt 的复杂度 / 出口数之内：那一个函数现在
     *  要先过五道闸（认档 / 闸门 / 去重 / 手势 / 透传），再进这一段。
     */
    private fun executeCombo(
        combo: ComboKey,
        gesture: Gesture,
        action: Action,
        config: Config,
        repeat: Boolean,
    ) {
        val r =
            runner ?: run {
                XLog.w("ActionRunner 还没建好，忽略组合键 ${combo.id} 的 ${gesture.id}")
                return
            }
        // 语言档先对一次，下面拼主语才用得上新语言（与单键那条同一处口径）。
        syncLanguage(config)
        val subject = comboTriggerLabel(combo, gesture, config)
        val log =
            "$subject${if (repeat) "（连续触发）" else ""} → ${XLog.redactToken(action.token)}（宿主进程执行）"
        //  一次按住会来几十条，重复那条压到 d（release 裁掉），免得把判据行淹掉。
        if (repeat) XLog.d(log) else XLog.w(log)
        // 振动 / 提示 / 报活与单键那套一致：
        //  · 重复派发只在「连续触发时振动」打开时振（否则一次按住会震几十下）；
        //  · 提示与报活只报第一次 —— 那是同一次按压，不是几十次操作。
        if (comboShouldVibrate(action, config) && (!repeat || combo.longRepeatVibrate)) haptic(config)
        if (!repeat) noticeIfNeeded(config, action, subject)
        r.run(action, subject)
        if (action !is Action.None) lastActionAt = android.os.SystemClock.elapsedRealtime()
        if (!repeat) onExecutedHook?.invoke(config)
    }

    /**
     * 组合那一格是「无操作」时：**两颗键各按自己这一档执行**。
     *
     *  顺序就是 [ComboKey.keys] 的书写顺序（没有「谁先谁后」的语义，串行执行只是别让两条命令
     *  同时抢同一个前台）。每颗键走它自己的 `actionFor`（含条件执行）—— 用户说的是
     *  「执行两颗按键绑定的命令」，那两格本来就该按它们自己的条件分支判。
     *
     *  闸门与单键那条一致：这一颗键自己得开着（[Config.isLive]）。
     */
    private fun runComboMembers(
        combo: ComboKey,
        gesture: Gesture,
        config: Config,
    ) {
        val r =
            runner ?: run {
                XLog.w("ActionRunner 还没建好，忽略组合键 ${combo.id} 的透传")
                return
            }
        //  同一次按压里，同一个条件只判一次（与单键那条同一个理由）
        val judged = HashMap<BranchCondition, Boolean>()
        syncLanguage(config)
        combo.keys.forEach { id ->
            val key = config.keyIdOf(id) ?: return@forEach
            if (!config.isLive(key)) {
                XLog.w("${keyName(key)}：这颗键已停用，组合透传时跳过")
                return@forEach
            }
            val keyConfig = config.key(key)
            val action =
                keyConfig.actionFor(gesture) { condition ->
                    judged.getOrPut(condition) { appContext?.let { ConditionProbe.matches(condition, it) } == true }
                }
            val subject = "${keyName(key)} - ${gestureName(gesture)}（组合透传）"
            XLog.w("$subject → ${XLog.redactToken(action.token)}（宿主进程执行）")
            if (config.global.vibrate && action !is Action.None) haptic(config)
            noticeIfNeeded(config, action, subject)
            r.run(action, subject)
            if (action !is Action.None) lastActionAt = android.os.SystemClock.elapsedRealtime()
        }
        onExecutedHook?.invoke(config)
    }

    /**
     * 组合键的**两级闸门**：总闸 + 每一颗成员键（[Config.isLive]）。
     *
     *  @return `false` = 这一下不执行（已打日志）
     */
    private fun comboGate(
        config: Config,
        combo: ComboKey,
    ): Boolean {
        if (!config.global.enabled) {
            XLog.w("组合键 ${combo.id}：总闸已关，忽略（模块不生效）")
            return false
        }
        // 成员键可能是**已经删掉的自定义键**（配置里 combos 还指着它）：`keyIdOf` 找不到 ⇒
        //  这一档这辈子凑不齐，等于停用 —— 与注入侧 `ConfigChannel.comboFor` 同一条判据。
        val dead = combo.keys.firstOrNull { id -> config.keyIdOf(id)?.let { config.isLive(it) } != true }
        if (dead != null) {
            XLog.w("组合键 ${combo.id}：成员 $dead 已停用或已删除，忽略")
            return false
        }
        return true
    }

    /**
     * 键身份按**当前配置**解析（`Config.keyIdOf`）：自定义按键是用户运行时收集来的、
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
     *  判据只有执行侧那一个 `voiceMemoryOn`（见 `ActionRunner.stopVoiceCapture`）：这一按到底
     *  有没有起过采集，只有执行侧知道。**不在这里按配置再判一次** —— 静态绑定与这一刻的实际
     *  动作可能不是一回事，两种情形都要靠执行侧兜住：
     *  · 条件执行把长按这一档改成了别的动作：静态绑定仍是语音，但这一按根本没起采集；
     *  · 按下之后用户关掉了模块 / 这颗键：已经开始的录音更该收掉，不能留在系统里。
     */
    private fun onRelease(key: KeyId) {
        val runner = runner
        if (runner == null) {
            XLog.d("${key.label} 松手：执行器还没建好，没有要收尾的东西")
            return
        }
        runner.stopVoiceCapture.invoke()
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
        //  一次按下的**一次判定**里，同一个条件只判一次：一块可以带好几条条件，几张块之间又常
        //  重复（好几条都要求「亮屏」）。判据里有反射与小窗那张表，不是纯函数级别的便宜货。
        //  缓存只活在这一下里（下面这个 map 是局部的）—— 跨次缓存会让「切到微信再按」仍按上一次的应用走。
        val judged = HashMap<BranchCondition, Boolean>()
        val action =
            keyConfig.actionFor(gesture) { condition ->
                judged.getOrPut(condition) { appContext?.let { ConditionProbe.matches(condition, it) } == true }
            }
        val r =
            runner ?: run {
                XLog.w("ActionRunner 还没建好，忽略 ${id.label} 的 ${gesture.id}")
                return
            }
        // 这条是按键的可见证据（release 里也保留），所以只在第一次执行时打 W；
        val log =
            "${id.label} - ${gesture.label}${if (repeat) "（连续触发）" else ""} → ${XLog.redactToken(action.token)}" +
                "（宿主进程执行）"
        if (repeat) XLog.d(log) else XLog.w(log)
        // 链路自检那一笔必须在 `run` **之后**，且「无操作」不算执行过：
        //  `run` 内部吞掉执行异常，绑了个解析不出来的 token 也走同一条路 —— 提前置位会让
        //  「关于模块」的「使用验证」在什么都没发生时显示已生效（假绿灯）。
        if (shouldVibrate(repeat, keyConfig, action, config)) haptic(config)
        if (!repeat) {
            // 语言档在拼主语**之前**对一次：`triggerLabel` 里的键名 / 手势名走的都是字典。
            syncLanguage(config)
            noticeIfNeeded(config, action, triggerLabel(id, gesture))
        }
        r.run(action, "${id.label} - ${gesture.label}")
        if (action !is Action.None) lastActionAt = android.os.SystemClock.elapsedRealtime()
        //  报活广播是**一次性**的（装在启动那一刻发一条），单靠它，「设置生效之后真的按过」这一笔
        //  永远送不到设置页 —— 用户按完侧键去看「关于模块」，那行仍写着「未生效」，把成功报成了故障。
        //  所以每执行一次动作补发一条（连续触发的重复不发：那是同一次按压，一条就够）。
        //  这一笔**不按动作类型过滤**：它同时是「模块活着」的凭证，绑了「无操作」也该回。
        if (!repeat) onExecutedHook?.invoke(config)
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
     * 组合键那一下要不要**在动作之前**振一下。
     *
     *  组合没有「连续触发」（`repeat` 恒为 false），所以只判两件：动作为空不振、切响铃走
     *  ColorOS 接口时由执行侧振（[colorOsRinger]）—— 两者与单键那条的口径一字不差。
     *  单键那条还要看那颗键的「连续触发时振动」，组合没有这一位（它没有连发）。
     */
    private fun comboShouldVibrate(
        action: Action,
        config: Config,
    ): Boolean = action !is Action.None && !colorOsRinger(action, config)

    /**
     * 「执行时提示」：判据是全局开关（`GlobalConfig.toastOnRun`），内容是
     * **这一下是哪颗键哪一档 + 执行了什么**（`侧键·单击 → 手电筒`）。
     *
     *  钉在这一处（一次手势只走一遍）而不是 `CustomCommandRunner` 里 —— 那一处只覆盖 `cmd:`
     *  自定义命令，绑的是内置动作（小布识屏 / 截图 / 手电筒 …）时按下去一直没动静
     *  （用户报「执行命令时系统提示没效果」）。连续触发的重复不弹：那是同一次按压。
     *
     *  主语（[subject]）只在动作名之前加一段：只说「手电筒」时，连按几下不同手势
     *  看不出分别是哪一下触发的。单键与组合键各拼各的（[triggerLabel] / [comboTriggerLabel]）——
     *  这一层只管「这句提示要不要弹、弹什么」。
     *
     *  语言由调用方先对好（[syncLanguage]）：主语里那几段名字是字典查出来的，
     *  在这里设语言就晚了半拍（调用方已经拼完主语）。
     */
    private fun noticeIfNeeded(
        config: Config,
        action: Action,
        subject: String,
    ) {
        if (!config.global.toastOnRun) return
        // 名字表随配置来（`Config.appNames`）：快捷方式那档的名字只存在那里，token 里没有
        val what = actionNoticeText(appContext, action, config.appNames)
        if (what.isEmpty()) return
        val notice = I18n.get(Strings.TOAST_RUN_ACTION, "%s → %s").format(subject, what)
        XLog.w("执行时提示：$notice")
        FailureNotice.toast(appContext, notice)
    }

    /**
     * 语言档跟配置走。
     *
     *  设置页那边建页时设过一次，但那是**另一个进程**（`:ui`）里的实例，宿主主进程这份一直停在
     *  AUTO（跟系统语言）。配置里选了 English 而系统是中文时，界面英文、提示中文 ——
     *  每次执行前对一次，两边同一个口径。同一个文件不重读（`reload` 里按文件名早返回），
     *  所以这一行在按键路径上是白给的。
     */
    private fun syncLanguage(config: Config) {
        I18n.currentLanguage = AppLanguage.fromCode(config.global.language)
    }

    /**
     * 单键提示里的主语 —— 「侧键 - 单击」。
     *
     *  手势名与内置键名都走字典：`Gesture.label` 与 `KeyId.PLUS.label` 是写死的中文常量，
     *  直接拼会在英文界面下变成「侧键·单击 → Flashlight」这种半截。
     *  自定义键的名字是用户起的，原样用（[keyName]）。
     */
    private fun triggerLabel(
        id: KeyId,
        gesture: Gesture,
    ): String = "${keyName(id)} - ${gestureName(gesture)}"

    /**
     * 组合键提示里的主语 —— 「侧键 + 音量上 - 短按」。
     *
     *  组合没有「一颗键的名字」，就把成员的按键名连起来：用户配的就是这一组，日志与提示里
     *  也得是这一组。成员可能已经被删掉（配置里还指着它）：那样这一档根本走不到提示
     *  （[comboGate] 已经拦下），`mapNotNull` 只是不让它抛。
     */
    private fun comboTriggerLabel(
        combo: ComboKey,
        gesture: Gesture,
        config: Config,
    ): String {
        val keys = combo.keys.mapNotNull { id -> config.keyIdOf(id)?.let(::keyName) }
        return "${keys.joinToString(" + ")} - ${gestureName(gesture)}"
    }

    /** 一颗键在提示里的名字（内置键走字典，自定义键用用户起的名字）。 */
    private fun keyName(id: KeyId): String = if (id.isCustom) id.label else I18n.get(Strings.KEY_SIDE, id.label)

    /** 一档手势在提示里的名字（字典优先，缺了就退回模型上那个中文常量）。 */
    private fun gestureName(gesture: Gesture): String =
        when (gesture) {
            Gesture.SINGLE -> I18n.get(Strings.GESTURE_SINGLE, gesture.label)
            Gesture.DOUBLE -> I18n.get(Strings.GESTURE_DOUBLE, gesture.label)
            Gesture.LONG -> I18n.get(Strings.GESTURE_LONG, gesture.label)
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
