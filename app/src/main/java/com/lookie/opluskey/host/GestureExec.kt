package com.lookie.opluskey.host

import android.annotation.SuppressLint
import android.content.Context
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.i18n.applyLanguage
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.FluidCard
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.OwnSettings
import com.lookie.opluskey.core.model.ToastNotice
import com.lookie.opluskey.core.model.gestureOrNull
import com.lookie.opluskey.core.model.isReleasePhase
import com.lookie.opluskey.core.model.sceneIndexOf
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.core.present.keyName
import com.lookie.opluskey.host.action.ActionRunner
import com.lookie.opluskey.host.action.FailureNotice
import com.lookie.opluskey.host.action.condition.ConditionProbe
import com.lookie.opluskey.host.action.notice.FluidAction
import com.lookie.opluskey.host.action.notice.actionNoticeReading

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
     * 按住那两档的**场景快照** —— 在「首键按下」或「组合凑成」那一刻判一次，派发时用它。
     *
     *  为什么需要：按住组合要先按住起点键（`comboHoldMinMs`）才凑得成，凑成之后还要等长按阈值
     *  才派发；按派发那一刻判场景，会把「按住不放期间切了应用」也算进去，与用户按下时的意图对不上。
     *  用哪一份由 `Config.comboSceneOnStarterFor` 决定（见 [handleComboScene]）。
     *
     *  覆盖＝下一次按下，清理＝保质期（[COMBO_SCENE_MAX_AGE_MS]）—— 理由见 [takeComboScene]。
     */
    private class ComboSceneMarks {
        var starter: SceneSnapshot? = null
        var formed: SceneSnapshot? = null
    }

    /** 某一刻这一档命中的是哪一块（`null` = 没有块接管）。 */
    private class SceneSnapshot(
        val long: Int?,
        val click: Int?,
        val at: Long,
    )

    private val comboScenes = HashMap<String, ComboSceneMarks>()

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
     * 组合场景快照的**保质期**（毫秒）。
     *
     *  快照到派发之间正常只隔一个长按阈值（默认 500ms）；超过这一档说明中间丢了动作，
     *  那时按一份过期归属去执行，比退回「按当时条件重判」更错。3s 与单键那条 [STALE_MS]
     *  同一量级：都只用来兜「积压 / 丢事件」，不是正常路径。
     */
    private const val COMBO_SCENE_MAX_AGE_MS = 3000L

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
     * @param keepLate 这一档**免检过期**，只走去重。给「松手」用：它不是一次新的按压，
     *   而是**一次按压的收尾** —— 积压的按下可以丢（丢的是「再来一下」），丢一条松手却是
     *   「这一按什么都没发生」（`松手才触发` 那一档唯一的执行就在这儿），只能照执行。
     *   重复那半照旧：转发侧对同一条会发两遍（`sent_at` 相同），第二条仍须挡掉。
     * @return `true` = 丢掉（已经打过判据日志）
     */
    private fun staleOrDuplicate(
        subject: String,
        phaseId: String?,
        sentAt: Long,
        keepLate: Boolean = false,
    ): Boolean {
        if (sentAt <= 0L) return false
        if (!keepLate) {
            val age = android.os.SystemClock.elapsedRealtime() - sentAt
            if (age > STALE_MS || age < 0L) {
                XLog.w("$subject 的 $phaseId 已过期或时间戳异常（age=${age}ms），丢弃不执行")
                return true
            }
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

        //  档位按**触发条件**解析：三档的 id 与 `Gesture.id` 逐字相同，多击是 `clickN`。
        //  认不出才丢掉 —— 手搓的广播、以及将来更新的注入侧发来的新档都会落到这里。
        //  解析**排在两道闸之前**：过不过期这条判据要看是哪一档（松手免检，见 [staleOrDuplicate]）。
        val trigger =
            GestureTrigger.ofId(phaseId) ?: run {
                XLog.w("不认识的手势：$phaseId")
                return
            }

        //  「松手」两档都免检过期：它们都不是一次新的按压，而是一次按压的**收尾**。
        //  `ClickThenLongRelease` 与 `Release` 同一个道理 —— 它也是抬手那一刻才发的那一条。
        if (staleOrDuplicate(key.id, phaseId, sentAt, keepLate = trigger.isReleasePhase)) return

        //  「这一下归模块」那一笔（`PressTrace`）不在这里记：它要等开关闸也过完 ——
        //  停用的键不该把宿主的吞键窗口往后推（见 [dispatch]）。
        dispatch(key, trigger, repeat)
    }

    /**
     * **记一份场景快照** —— 注入侧在「首键按下」或「组合凑成」时发来一条，不执行任何动作。
     *
     *  @param target `starter` 那一档传**起点键的 id**：此刻还不知道会凑成哪一档，
     *    这里把「以它为起点的所有按住档」各记一份；`formed` 那一档传组合 id，只记它自己
     *
     *  为什么在宿主判：条件（前台应用 / 各系统开关）只有宿主查得到；注入侧只能报「时刻」。
     *  判一次、整一按都用它 —— 这是「按住组合的场景由首键按下时固定」的全部实现。
     */
    fun handleComboScene(
        target: String,
        stage: String,
    ) {
        if (target.isEmpty()) return
        val config = ConfigStore.read()
        //  **只记按住那两档**：等窗口那一档既没有「先按住哪一颗」，也不需要这份快照 ——
        //  它照旧在派发那一刻现判（`takeComboScene` 对它恒返回空）。两处判据是同一句，
        //  免得「记了却没人用」多一次条件判定（那里面有反射与小窗那张表）。
        val combos =
            if (stage == "starter") {
                config.combos.filter { it.isHold && it.starterKey == target }
            } else {
                listOfNotNull(config.combos.firstOrNull { it.id == target && it.isHold })
            }
        if (combos.isEmpty()) return
        val now = android.os.SystemClock.elapsedRealtime()
        val judged = HashMap<BranchCondition, Boolean>()

        fun matchesCondition(condition: BranchCondition): Boolean =
            judged.getOrPut(condition) { appContext?.let { ConditionProbe.matches(condition, it) } == true }
        for (combo in combos) {
            val snapshot =
                SceneSnapshot(
                    long = sceneIndexOf(combo.scenes, GestureTrigger.LongPress, ::matchesCondition),
                    click = sceneIndexOf(combo.scenes, GestureTrigger.Click(1), ::matchesCondition),
                    at = now,
                )
            val marks = comboScenes.getOrPut(combo.id) { ComboSceneMarks() }
            if (stage == "starter") marks.starter = snapshot else marks.formed = snapshot
            XLog.w("组合键场景快照：${combo.id} @$stage ⇒ 长按第 ${snapshot.long?.plus(1) ?: "-"} 块 / 单击第 ${snapshot.click?.plus(1) ?: "-"} 块")
        }
    }

    /**
     * 派发时取这一档该用哪一份快照 —— 按 [Config.comboSceneOnStarterFor] 选。
     *
     *  **不在取用时清掉**：同一次按压会派发多次（连续触发一条一条来），每一条都得看同一份，
     *  否则第二次起就退回「按当时条件重判」—— 按住期间切了应用的话，第一次与第二次动作会
     *  落进不同的场景块。覆盖靠下一次按下（`handleComboScene` 写同一格），
     *  清理靠 [COMBO_SCENE_MAX_AGE_MS] 这条保质期。
     *
     *  @return `null` = 不用快照（不是按住那两档 / 没收到 / 太旧），调用方退回「按当时条件重判」
     */
    private fun takeComboScene(
        config: Config,
        combo: ComboKey,
    ): SceneSnapshot? {
        //  等窗口那一档没有「按下那一刻」这回事：凑成即是派发，两种口径没有区别 ——
        //  于是维持原来的「派发时现判」，不给它引入一条没人要求的归属时点。
        if (combo.isWait) return null
        val marks = comboScenes[combo.id] ?: return null
        val snapshot = if (config.comboSceneOnStarterFor(combo)) marks.starter else marks.formed
        if (snapshot == null) return null
        //  超过这个时长就不用：快照与派发之间正常只隔着长按阈值，超出说明中间丢了动作，
        //  拿一份过期归属去执行比按当时重判更错。
        if (android.os.SystemClock.elapsedRealtime() - snapshot.at > COMBO_SCENE_MAX_AGE_MS) return null
        return snapshot
    }

    /**
     * 用快照取动作 —— 那一块没接管这一档时回落到这一档自己的绑定（与 [pickAction] 同一口径）。
     */
    private fun sceneActionOf(
        combo: ComboKey,
        trigger: GestureTrigger,
        snapshot: SceneSnapshot,
    ): Action {
        val index =
            when (trigger) {
                GestureTrigger.LongPress -> snapshot.long
                is GestureTrigger.Click -> snapshot.click
                else -> null
            }
        val block = index?.let { combo.scenes.getOrNull(it) }?.takeIf { it.enabled }
        val fromBlock = block?.actionOf(trigger) ?: Action.Follow
        return if (fromBlock.isBound) fromBlock else combo.actionOf(trigger)
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
        // 双击在组合上没有定义（`ComboKey.actionOf` 恒返回「无操作」）；认不出的 phase
        //  只可能来自手搓的广播。松手那一档（`release`）与单键同一条口径：它是**另一个槽位**。
        val trigger = comboTrigger(phaseId) ?: return
        //  两道上游守卫：认不出 / 已停用 / 闸门不过，或这一下是过期或重复的广播。
        //  `keepLate` 与单键那条一样按**触发档**给 —— 松手是一次按压的收尾，积压时不能当过期丢掉
        //  （漏了它，组合的「松手才触发 / 松手额外动作」在积压时会静默不执行）。
        val combo =
            liveCombo(config, comboId)
                ?.takeIf {
                    !staleOrDuplicate("combo:${it.id}", phaseId, sentAt, keepLate = trigger is GestureTrigger.Release)
                } ?: return
        //  与单键那条同一笔账：过完闸才记「这一下归模块」（理由见 [handle] 里那段）
        PressTrace.noteClaim()
        //  条件执行：与单键那一支同一套 —— 先在这档的顺序表里找，第一条成立的就执行它，
        //  一条都不中才落到 `actionOf` 那一档绑定。判据现判（按下的那一刻），且**一次按下的
        //  一次判定里同一个条件只判一次**（一块可以带好几条，几条规则之间又常重复）。
        val judged = HashMap<BranchCondition, Boolean>()

        fun matchesCondition(condition: BranchCondition): Boolean =
            judged.getOrPut(condition) { appContext?.let { ConditionProbe.matches(condition, it) } == true }
        if (trigger == GestureTrigger.Release) {
            //  「松手执行额外动作」这一档：与其它几档互斥，**三种触发方式同一套**（用户口径）——
            //  按住那两档的长按照样有动作，抬手照样是那一按的收尾。
            //  早先这里挡了一道 `combo.isHold`（那时按住档被定成「抬手只收尾」），
            //  结果是界面能配、配了也不执行。
            //
            //  取动作与单键同一条口径（[pickReleaseAction]）：长按被哪一块接管，就用那一块自己的松手格；
            //  一块都不中才落到这一组的 `longRelease`。块命中时**不**再补一刀 —— 一次按压一个结果。
            //
            //  **按松手那一刻的条件重判**（用户口径：不再沿用按下时命中的那一块）：
            //  松手本来就是一次独立的判定，条件和按下时不一样就该按新的来。
            val fired = combo.resolveRelease(::matchesCondition)
            if (!fired.isBound) {
                XLog.w("组合键 ${combo.id} - 松手：这一下没有可执行的动作（长按命中场景 / 这一组没配松手），不执行")
                return
            }
            executeCombo(combo, trigger, fired, config, repeat)
            return
        }
        //  取动作：三种触发方式共用这一条路（场景块只分短按 / 长按，不构成第二个维度）。
        //  **有快照就用快照**：按住组合的场景在首键按下（或凑成）那一刻已经定下来了。
        //  **留空就是留空**：这一格是「无操作」时什么都不做，不落回两颗键各自的绑定 ——
        //  想「两颗键各干各的」就不要配这一档组合（判据只有这一句，没有例外）。
        val action =
            takeComboScene(config, combo)?.let { sceneActionOf(combo, trigger, it) }
                ?: combo.resolve(trigger, ::matchesCondition)
        executeCombo(combo, trigger, action, config, repeat)
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
    private fun comboTrigger(phaseId: String?): GestureTrigger? =
        when (phaseId) {
            Gesture.SINGLE.id -> {
                GestureTrigger.Click(1)
            }

            Gesture.LONG.id -> {
                GestureTrigger.LongPress
            }

            //  松手那一档：动作在 `ComboKey.longRelease`（另一个槽位），见 `handleCombo`
            GestureTrigger.RELEASE_ID -> {
                GestureTrigger.Release
            }

            else -> {
                XLog.w("组合键不支持的手势：$phaseId")
                null
            }
        }

    /**
     * 组合键那一下的收尾 —— 日志、振动、提示、执行、报活。
     *
     *  单独一支是因为它有两处调用（普通那一档、松手那一档）：两处收尾一模一样，
     *  内联就是把同一段抄两遍。
     */
    private fun executeCombo(
        combo: ComboKey,
        trigger: GestureTrigger,
        action: Action,
        config: Config,
        repeat: Boolean,
    ) {
        val r =
            runner ?: run {
                XLog.w("ActionRunner 还没建好，忽略组合键 ${combo.id} 的 ${trigger.id}")
                return
            }
        // 语言档先对一次，下面拼主语才用得上新语言（与单键那条同一处口径）。
        syncLanguage(config)
        val (keyLabel, gestureLabel) = comboParts(combo, trigger, config)
        val subject = "$keyLabel - $gestureLabel"
        val log =
            "$subject${if (repeat) "（连续触发）" else ""} → ${XLog.redactToken(action.token)}（宿主进程执行）"
        //  一次按住会来几十条，重复那条压到 d（release 裁掉），免得把判据行淹掉。
        if (repeat) XLog.d(log) else XLog.w(log)
        // 振动 / 提示 / 报活与单键那套一致：
        //  · 重复派发只在「连续触发时振动」打开时振（否则一次按住会震几十下）；
        //  · 提示与报活只报第一次 —— 那是同一次按压，不是几十次操作。
        //  两套取值都从 `Config` 那两个出口来（传 combo，传单键的 `KeyConfig` 也一样）：
        //  **组合键在这一层没有特殊待遇**，它只是另一种「可以有自己的设置」的东西。
        //  一次解析：接口 / 振动 / 提示三项都读它（判据见 `Config.effectiveFor`，
        //  「独立设置」开着才看这一组自己那份）；传给 `run` 的第二位也是同一份结论。
        val own = config.effectiveFor(combo)
        val colorOsApi = own.colorOsApi
        if (comboShouldVibrate(action, colorOsApi) && (!repeat || combo.longRepeatVibrate)) haptic(config, combo)
        if (!repeat) noticeIfNeeded(config, action, keyLabel, gestureLabel, combo)
        //  执行结果只有成功才算数：失败回执已经由执行器那边弹过（`notifyFailure`），
        //  这里不再补报；而 `lastActionAt` / 报活是「真的做成了」的凭证，失败时置位就是假绿灯。
        val ok = r.run(action, subject, colorOsApi, own.vibrate)
        if (ok) {
            if (action.isBound) lastActionAt = android.os.SystemClock.elapsedRealtime()
            if (!repeat) onExecutedHook?.invoke(config)
        }
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
        trigger: GestureTrigger,
        repeat: Boolean,
    ) {
        val config = ConfigStore.read()
        val keyConfig = config.key(key)
        if (!config.isLive(key)) {
            val why = if (!config.global.enabled) "总闸已关" else "这颗键已停用"
            XLog.w("${key.label}：$why，忽略这次 ${trigger.label}（模块不生效）")
            return
        }
        //  两块闸都过完才算**这一下归模块**（`PressTrace`）：转发侧对同一次手势会发两条
        //  （立刻 + 300ms 补发），补发那条若也记一笔，宿主的吞键窗口就被推迟到约 600ms，
        //  期间别的键的原生派发会被一起吞掉。**必须放在开关闸之后** —— 停用 / 总闸关的键
        //  会被上面那一支挡掉，那种情况下不该占住宿主的吞键窗口。
        PressTrace.noteClaim()
        runAction(key, trigger, keyConfig, repeat, config)
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
        trigger: GestureTrigger,
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

        fun matchesCondition(condition: BranchCondition): Boolean =
            judged.getOrPut(condition) { appContext?.let { ConditionProbe.matches(condition, it) } == true }
        //  「松手执行额外动作」与系统场景的先后、以及「这一档到底绑了什么」——
        //  这一整段只回答一件事：**这一次到底执行哪个动作**。抽出去与「派发」分开，
        //  `runAction` 那条主线上就只剩顺序，不再一串嵌套判断。
        val fired = resolveAction(id, trigger, keyConfig, ::matchesCondition) ?: return
        val r =
            runner ?: run {
                XLog.w("ActionRunner 还没建好，忽略 ${id.label} 的 ${trigger.id}")
                return
            }
        // 这条是按键的可见证据（release 里也保留），所以只在第一次执行时打 W；
        val log =
            "${id.label} - ${trigger.label}${if (repeat) "（连续触发）" else ""} → ${XLog.redactToken(fired.token)}" +
                "（宿主进程执行）"
        if (repeat) XLog.d(log) else XLog.w(log)
        //  「这颗键用自己那套公共项」—— 接口与提示都从这两个出口取一次，
        //  下面三处（振动、提示、执行）共用，别各判一遍（判据在 `Config` 那一处）
        val own = config.effectiveFor(keyConfig)
        val colorOsApi = own.colorOsApi
        // 链路自检那一笔必须在 `run` **之后**，且「无操作」不算执行过：
        //  `run` 内部吞掉执行异常，绑了个解析不出来的 token 也走同一条路 —— 提前置位会让
        //  「关于模块」的「使用验证」在什么都没发生时显示已生效（假绿灯）。
        if (shouldVibrate(repeat, keyConfig, trigger, fired, colorOsApi)) haptic(config, keyConfig)
        // 提示只在第一次派发时说：连续触发的重复是同一次按压（按住不放），弹几十条没有意义
        if (!repeat) announceOnce(config, keyConfig, id, trigger, fired)
        val ok = r.run(fired, "${id.label} - ${trigger.label}", colorOsApi, own.vibrate)
        //  只有**成功**才置两笔凭证：失败回执由执行器那边弹（`notifyFailure`），这里不再补；
        //  `lastActionAt` 与报活都是「真的做成了」的凭证，失败时置位会让「关于模块」显示假绿灯。
        if (ok) {
            if (fired.isBound) lastActionAt = android.os.SystemClock.elapsedRealtime()
            //  报活广播是**一次性**的（装在启动那一刻发一条），单靠它，「设置生效之后真的按过」这一笔
            //  永远送不到设置页 —— 用户按完侧键去看「关于模块」，那行仍写着「未生效」，把成功报成了故障。
            //  所以每成功执行一次动作补发一条（连续触发的重复不发：那是同一次按压，一条就够）。
            //  这一笔**不按动作类型过滤**：它同时是「模块活着」的凭证，绑了「无操作」也该回。
            if (!repeat) onExecutedHook?.invoke(config)
        }
    }

    /**
     * 这一次**执行哪个动作** —— `null` = 这一下不执行（场景让位）。
     *
     *  两件事按顺序：
     *  1. **松手与系统场景的先后**：长按那一下命中场景 ⇒ 这一下让位（用户口径：一次按压一个结果）；
     *  2. 取这一档的绑定（带条件表，第一条成立的就赢）。
     *
     *  从 [runAction] 抽出来：那条主线只该讲派发顺序，判断留在这一处。
     */
    private fun resolveAction(
        id: KeyId,
        trigger: GestureTrigger,
        keyConfig: KeyConfig,
        matchesCondition: (BranchCondition) -> Boolean,
    ): Action? {
        //  松手是**这一按的收尾**，动作由「长按被谁接管」决定（`pickReleaseAction` 那一处）：
        //  块接管了长按 ⇒ 用那一块自己的松手格（`None` = 这一下什么都不做），**不再**补按键那一格；
        //  一块都不中 ⇒ 才补按键自己的 `longRelease`。判据与长按那一档同一份，且共用同一次按压里
        //  的 `judged` 缓存（不多判一轮）。
        //  **按松手那一刻的条件重判**（用户口径：不再沿用按下时命中的那一块）—— 松手本来就是
        //  一次独立判定，条件变了就该按新的来。
        //  返回非 `null` 就是「这一下有个结论」（动作可能是 `Action.None` = 什么都不做）。
        if (trigger == GestureTrigger.Release) {
            //  长按这一档**本身没有动作**时，松手这一刀整条不成立（见 `KeyConfig.releaseBound` /
            //  `longActionExists`）：松手动作是「这一档执行完之后」的收尾，主语没有就不该有谓语。
            //  这一道与判定侧那一份判据**故意重复**：判定侧是注入在 system_server 里的旧代码时
            //  （改了判定、没重启），它照样会发这一条 —— 在这里拦住，用户不必为了一个开关重启整机。
            if (!keyConfig.releaseBound) {
                XLog.w("${id.label} - 松手：长按这一档是无操作（或松手已让位），不派发")
                return null
            }
            val fired = keyConfig.resolveRelease(matchesCondition)
            if (!fired.isBound) {
                XLog.w("${id.label} - 松手：这一下没有可执行的动作（长按命中场景 / 按键没配松手），不派发")
                return null
            }
            return fired
        }
        //  **多击档没有动作时逐级回退**（用户口径）：窗口结束时判定侧只报「数到了第几击」，
        //  报上来的那一档未必配了动作 —— 比如只开了四击、按了两下，报上来的是 `Click(2)`，
        //  而双击那一格空着。这时退到 `Click(1)`，让这一下仍然执行单击，而不是「按了没反应」。
        //  注入侧看不到动作表（它只认 `judge_clicks`），所以这一层只能在这里做。
        if (trigger is GestureTrigger.Click && trigger.count > 1) {
            for (n in trigger.count downTo 1) {
                val fallback = keyConfig.actionFor(GestureTrigger.Click(n), matchesCondition)
                if (!fallback.isBound) continue
                if (n != trigger.count) {
                    XLog.w(
                        "${id.label} - ${trigger.label}：这一档没有动作，回退到" +
                            (if (n == 1) "单击" else "第 $n 击"),
                    )
                }
                return fallback
            }
            return Action.None
        }
        //  其余档位统一走**触发条件**：场景块能接管哪几档由它自己的 `actions` 说，
        //  所以「只给三击配了场景、直接绑定那一格空着」也照样命中（`pickAction` 一处收口）。
        return keyConfig.actionFor(trigger, matchesCondition)
    }

    /**
     * 一次按压的**「说一句」**：语言档、主语两段、执行时提示 —— 只在第一次派发时做。
     *
     *  连续触发的重复不弹提示：那是同一次按压（按住不放），弹几十条没有意义。
     *  抽出来只是给 [runAction] 降压：它在按键路径上，那一段「什么时候说、说什么」
     *  与「派发什么」本来就是两件事。
     */
    private fun announceOnce(
        config: Config,
        keyConfig: KeyConfig,
        id: KeyId,
        trigger: GestureTrigger,
        fired: Action,
    ) {
        // 语言档在拼主语**之前**对一次：`triggerParts` 里的键名 / 手势名走的都是字典。
        syncLanguage(config)
        val (keyLabel, gestureLabel) = triggerParts(id, trigger)
        noticeIfNeeded(config, fired, keyLabel, gestureLabel, keyConfig)
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
        trigger: GestureTrigger,
        action: Action,
        colorOsApi: Boolean,
    ): Boolean {
        if (!action.isBound) return false
        //  连发时振不振，两档各问各的：「单击后长按」自己有「每次都振动」
        //  （`clickThenLongRepeatVibrate`），不能拿长按那一档的值顶。
        val repeatVibrate =
            if (trigger == GestureTrigger.ClickThenLong) {
                keyConfig.clickThenLongRepeatVibrate
            } else {
                keyConfig.longRepeatVibrate
            }
        if (!repeat || repeatVibrate) return !colorOsRinger(action, colorOsApi)
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
        colorOsApi: Boolean,
    ): Boolean = action.isBound && !colorOsRinger(action, colorOsApi)

    /**
     * 「执行时提示」：判据是全局开关（`GlobalConfig.toastOnRun`），文案由
     * [GlobalConfig.toastTemplate] 拼 —— `$key` / `$gesture` / `$action` 与 `$action` 拆开的三段
     * 可自由摆放增删（见 `ToastNotice`；默认模板就是拆分之前那句 `侧键 - 单击 → 手电筒`）。
     *
     *  钉在这一处（一次手势只走一遍）而不是 `CustomCommandRunner` 里 —— 那一处只覆盖 `cmd:`
     *  自定义命令，绑的是内置动作（小布识屏 / 截图 / 手电筒 …）时按下去一直没动静
     *  （用户报「执行命令时系统提示没效果」）。连续触发的重复不弹：那是同一次按压。
     *
     *  键名与手势分两段传进来（[triggerParts] / [comboParts]）：模板要能**单独**用其中一段，
     *  拼成一句再拆回来做不到。日志那边照样拼成「侧键 - 单击」——它读的是同一对值。
     *
     *  语言由调用方先对好（[syncLanguage]）：这里两段名字都是字典查出来的，
     *  在这里设语言就晚了半拍（调用方已经拼完）。
     */
    private fun noticeIfNeeded(
        config: Config,
        action: Action,
        keyLabel: String,
        gestureLabel: String,
        /** 这一下是谁触发的（单键那一档 / 那一组组合）—— 提示的两项都按它取，不另传布尔。 */
        owner: OwnSettings,
    ) {
        //  一次解析，下面两项都读它（判据见 `Config.effectiveFor`：独立设置开着才看自己那份）
        val own = config.effectiveFor(owner)
        if (!own.toastOnRun) return
        //  「无操作」不弹提示 —— 这是**提示这个位置**的规矩（那一刻没有动作发生，没什么可报），
        //  不是「一个动作怎么念」。所以它在这一侧，不在 `CommandTitle` 里：工厂只负责把一句话
        //  念完整。放回工厂的话，同一个动作在提示里是空串、在步骤行里是 `无操作`，而那两处
        //  本来就该是同一句 —— 回执那边还得再拿 `ifBlank` 兜一次。
        if (!action.isBound) return
        // 名字表随配置来（`Config.appNames`）：快捷方式那档的名字只存在那里，token 里没有
        val what = actionNoticeReading(appContext, action, config.appNames)
        if (what.sentence.isEmpty()) return
        val notice = ToastNotice.render(own.toastTemplate, keyLabel, gestureLabel, what)
        XLog.w("执行时提示：$notice")
        if (own.toastFluid) {
            //  换落点不换说法：同一句 `$key - $gesture → $action`，交给宿主自己的 Seedling SDK。
            //  图标取**这一次执行的动作**（界面层已按 `FluidCard.actionNameOf` 预渲染成 png）；
            //  失败不再回头弹一条 Toast —— 那是「提示失败的提示」，用户要的那一句照样没出现。
            FluidAction.runText(appContext, notice, FluidCard.actionNameOf(action.token)) {}
        } else {
            FailureNotice.toast(appContext, notice)
        }
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
        applyLanguage(config.global.language)
    }

    /**
     * 单键那次的「键名 / 手势」两段。
     *
     *  手势名与内置键名都走字典：`Gesture.label` 与 `KeyId.PLUS.label` 是写死的中文常量，
     *  直接拼会在英文界面下变成「侧键·单击 → Flashlight」这种半截。
     *  自定义键的名字是用户起的，原样用（[keyName]）。
     */
    private fun triggerParts(
        id: KeyId,
        trigger: GestureTrigger,
    ): Pair<String, String> = keyName(id) to gestureLabel(trigger)

    /**
     * 组合那次的「键名 / 手势」两段 —— 键名是「侧键 + 音量上」这一组。
     *
     *  组合没有「一颗键的名字」，就把成员的按键名连起来：用户配的就是这一组，日志与提示里
     *  也得是这一组。成员可能已经被删掉（配置里还指着它）：那样这一档根本走不到提示
     *  （[comboGate] 已经拦下），`mapNotNull` 只是不让它抛。
     */
    private fun comboParts(
        combo: ComboKey,
        trigger: GestureTrigger,
        config: Config,
    ): Pair<String, String> = comboLabel(config, combo) to gestureLabel(trigger)

    /** 一档手势在提示里的名字 —— 第 3 击起的档位没有 `Gesture`，用它自己的标签。 */
    private fun gestureLabel(trigger: GestureTrigger): String = trigger.gestureOrNull()?.let(::gestureName) ?: trigger.label

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
    private fun haptic(
        config: Config,
        owner: OwnSettings,
    ) {
        //  一次解析，振不振与振多强都读它（判据见 `Config.effectiveFor`）
        val own = config.effectiveFor(owner)
        if (!own.vibrate) return
        try {
            val ctx = appContext ?: return
            val v = ctx.getSystemService(android.os.Vibrator::class.java) ?: return
            // 越界的强度在 `GlobalConfig.ofFields` 那一层已经钳过（手改文件也能进来），
            //  这里再兜一次：索引越界会抛 IndexOutOfBounds，而这段跑在按键路径上。
            val amplitude = HAPTIC_AMPLITUDES[own.vibrateStrength.coerceIn(HAPTIC_AMPLITUDES.indices)]
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
    colorOsApi: Boolean,
): Boolean {
    if (!colorOsApi) return false
    if (action is Action.Ringer) return true
    return action is Action.KeyEvent && action.keyCode == KeyId.KEYCODE_VOLUME_MUTE
}
