package com.lookie.opluskey.inject.input

import android.os.Handler
import android.os.SystemClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import java.util.concurrent.ConcurrentHashMap

/**
 * 窗口的**兜底值**（毫秒）—— 只在配置还没送到、或送来的值不合法时用。
 *
 *  正常取值走 [ComboDetector] 的 `windowMs`（用户可在「组合按键设置」里调）。真机实测：
 *  电源与音量下同时按，两条 DOWN 相差 3ms（同一线程、同一串事件），出厂 80ms 既盖得住人手的
 *  同时按，又不会把「先按 A、松手后再按 B」这种连按算成组合。
 */
private const val FALLBACK_WINDOW_MS = 80L

/**
 * 「长按连续触发」的外部终止上限（毫秒）—— 与单键那条同一个数与同一条理由
 * （见 `GestureDetector.REPEAT_MAX_MS`）：抬手事件丢了、没有任何回调会停这条链。
 */
private const val COMBO_REPEAT_MAX_MS = 30_000L

/**
 * 组合键的判定 —— **任意两颗已接管的键**在窗口内先后按下，就当作一次组合，不再各自判单击 / 长按。
 *
 *  判出组合之后执行什么，由装配方决定（`onCombo`）：这里只回答「哪一档 + 短按还是长按」。
 *
 *  与 [GestureDetector] 的分工：那个判单颗键的手势，这个只判「两颗键的重叠」。一旦认出组合，
 *  立刻把这两颗键的单键状态机**摘掉**（[resetKey]），否则先按下的那颗会在组合期间判出长按 ——
 *  一次按键执行两个动作。
 *
 * ## 先问 [bindingFor]
 *
 *  组合成立就会摘掉两颗键的单键状态机。没有绑定还照样摘，用户看到的就是「两键同按什么反应都没有」——
 *  而这正是最容易被当成「模块坏了」的那种表现。所以**先判有没有这一档，再动任何状态**：
 *  查不到就把这一下原样交回单键判定器，两颗键各走各的。
 *
 * ## 顺序按下也算组合（组合的等待压过单键的单击）
 *
 *  判据只是「两颗键的 DOWN 相差不超过窗口」，**不要求两颗同时按着**：先按 A、松手、紧接着按 B
 *  同样算这一档组合。代价面在单键那一侧 —— 参与组合的键按下后要**等满一个窗口**才进单键判定器
 *  （见 `HeldPresses`），所以「A 的单击」不会在 B 到来之前先跑掉。用户口径：先后按两颗组合键，
 *  只该触发组合，不该两个人各来一次。
 *
 *  长按是另一条判据：必须两颗**都还按着**（读 [down]）—— 先松开一颗就凑不出「一起按住不放」。
 *
 * ## 三颗键同时按着时只出一组
 *
 *  组合进行中再来的按键整条吞掉（按下 + 抬手），所以「三颗里有两组组合」也只出**先凑成的那一组**。
 *  配对时窗口内可能有好几颗候选：**配不上的候选不占位**（见 [onDown] 里那段），都配得上才按
 *  「按下时刻由近到远」取最近的一颗。
 *
 * ## 长按阈值取组合自己的
 *
 *  `ComboKey.longPressMs` 是这一档自己的（默认 500ms，与单键默认值同档）。不用某颗成员键的阈值：
 *  两颗键可能各配一个值，取谁的都是任选其一；而「一起按住多久算长按」是这一档的语义。
 *
 * 线程：与 [GestureDetector] 同一条输入线程（DOWN/UP 都在它上面进来），长按那一枪走
 * [handler]（main looper）。跨线程的字段一律 `@Volatile`。
 */
internal class ComboDetector(
    private val handler: Handler,
    /**
     * 判出一次组合：哪一档 + 哪一档手势 + 是不是**连续触发**的重复派发。
     *
     *  `repeat` 与单键那条 `onGesture` 的第三位同义：重复派发不再振动、不再报活（那是同一次按压）。
     */
    private val onCombo: (ComboKey, Gesture, Boolean) -> Unit,
    /** 摘掉某颗键的单键计时器（组合成立时用，见类注释）。 */
    private val resetKey: (KeyId) -> Unit,
    /**
     * 这几颗键凑成的是哪一档 —— `null` = 没有绑定，组合不成立（见类注释）。
     *
     *  由装配方接 `ConfigChannel.comboFor`（读注入侧手上那份配置的 `combos`）。
     */
    private val bindingFor: (List<KeyId>) -> ComboKey?,
    /**
     * 当前的等待窗口（毫秒）—— 两颗键的 DOWN 相差不超过它才算同时按下。
     *
     *  由装配方接 `ConfigChannel.comboWindowMs`（用户在「组合按键设置」里可调）。
     */
    private val windowMs: () -> Long,
    /**
     * 这一颗键参与某一组已启用的组合吗 —— 决定它按下之后要不要**先等一个窗口**。
     *
     *  参与的键等着看「第二颗键会不会来」（这期间不喂单键判定器，用户能感觉到的就是
     *  「参与组合的键按下去晚一点才有反应」）；不参与的键立刻交回单键，一点不受影响。
     */
    private val joinsCombo: (KeyId) -> Boolean,
) {
    /**
     * 一次 DOWN 的处理结果 —— 调用方据此决定「吞掉 / 延迟喂 / 立刻喂」。
     */
    enum class Down {
        /** 归组合：这一下已经由组合接管，调用方**不要再喂单键判定器**。 */
        TAKEN,

        /** 这颗键参与组合、但组合还没凑成：调用方先压住一个窗口，到点再喂单键。 */
        HOLD,

        /** 与组合无关：立刻喂单键判定器，行为与没有组合键时完全一致。 */
        PASS,
    }

    /**
     * 这一刻按着的那几颗键（key → 按下时刻）。
     *
     *  **必须是并发容器**：写在输入线程（`onDown` / `onUp`），读在主 looper 的定时任务里
     *  （`scheduleLong` / `scheduleRepeat` 里那两处「还按着没有」）—— 普通 `HashMap` 在这条路径上
     *  既没有可见性保证、也受并发修改影响，表现是「组合长按有时候不触发」。
     *  类注释那条「跨线程的字段一律 `@Volatile`」对集合不成立（volatile 只管引用），
     *  所以这里用 `ConcurrentHashMap` 顶替，语义不变（`entries` 迭代是弱一致的）。
     *
     *  **判「按着没有」一律写 `containsKey(it)`、不能写 `it in down`**：`ConcurrentHashMap`
     *  继承下来的 `contains` 实际是 `containsValue`（不是 `containsKey`），`in` 会编译报错；
     *  真按值去比也会得到「永远不成立」。
     */
    private val down = ConcurrentHashMap<KeyId, Long>()

    /**
     * 配对的候选（key → 最近一次按下的时刻）—— **抬手也留着**，只按窗口过期。
     *
     *  为什么要与 [down] 分成两张表：「先按 A、松手、紧接着按 B」在用户口径里也算组合
     *  （判据仍是两颗键的 DOWN 相差在窗口内），而「一起按住才算长按」必须看还按着没有 ——
     *  一张表兼两用会让其中一条走样。早先只有 [down] 且抬手就删，于是松手后的 B 配不上 A：
     *  A 的单击先跑掉了，B 也自判单击，用户看到的就是「两颗键各自的单击都触发、组合也触发」。
     */
    private val recent = ConcurrentHashMap<KeyId, Long>()

    /**
     * 这一次按压已经被组合吃掉的键 —— 它们的抬手也要吞掉。
     *
     *  不清的话：组合的第一颗键先松开、组合结束，第二颗键的抬手会让单键判定器当成
     *  「孤立抬手」补判一次单击。
     */
    private val consumed = HashSet<KeyId>()

    /** 正在进行的组合；`null` = 没有。 */
    @Volatile
    private var combo: Combo? = null

    private class Combo(
        /** 这一档是谁 —— 执行什么由它决定（`single` / `long` 两格）。 */
        val key: ComboKey,
        val keys: List<KeyId>,
        /** 组合成立那一刻（连发上限从它开始量）。 */
        val pressedAt: Long,
        /** 长按那一枪，抬手 / 重置时摘掉。 */
        @Volatile var shot: Runnable? = null,
        /** 已经判过「一起按住不放」。 */
        @Volatile var longFired: Boolean = false,
        /** 连发那一枪（`longRepeat` 打开时才有），抬手 / 重置时摘掉。 */
        @Volatile var repeatShot: Runnable? = null,
    )

    /**
     * @return 见 [Down]：`TAKEN` = 归组合（调用方不要再喂单键）；`HOLD` = 先压一个窗口；
     *   `PASS` = 与组合无关，立刻交给单键判定器。
     */
    fun onDown(
        id: KeyId,
        now: Long = SystemClock.uptimeMillis(),
    ): Down {
        //  **同一颗键再次按下、而它的上一次抬手没收到** ⇒ 上一次的 UP 丢了（锁屏打断、
        //  事件被系统丢掉那一类）。这是一条必须清的账：
        //   · 上一次可能正是一组组合 —— `combo` 不清就永远挂着，之后**每一颗键**的按下都走
        //     下面「组合进行中」那一支被吞掉，抬手也被 `consumed` 吞掉；
        //     表现就是「整块键盘像死了一样」，直到下一条配置广播才恢复；
        //   · 也可能只是单键 —— `consumed` 里留着它，那这一下的抬手会被误吞。
        //  正常输入不会对同一颗键连发两个 DOWN（重复按键带的是 REPEAT 标记，不走这里），
        //  所以这条判据不会误伤长按。
        if (down.put(id, now) != null) {
            XLog.w("组合键：${id.label} 又按下但没等到上一次抬手（UP 丢了）⇒ 清空组合状态重新算")
            reset()
            down[id] = now
        }
        recent[id] = now
        // 已经在组合里：第三颗键按下来也不掺和，仍按「组合进行中」吞掉。
        //  **记进 [consumed]**：不记的话它没有「按下」，抬手那条 UP 会被单键判定器当成
        //  「孤立抬手」补判一次单击 —— 组合进行中又冒出第三颗键的动作。
        if (combo != null) {
            consumed += id
            return Down.TAKEN
        }
        val window = windowMs().takeIf { it > 0 } ?: FALLBACK_WINDOW_MS
        //  配对看 [recent]（含**刚松手**的那一颗）：顺序按下也算组合，见那张表的注释。
        //
        //  **候选要全过一遍、挑「真配得上」的那一颗**，不能只拿窗口内任意一颗去配：
        //  三颗键交错按（A → C → B，只有 A+B 配了组合）时 `ConcurrentHashMap` 的迭代序是散列序，
        //  可能先递给你 C —— 一配不上就整个落回单击，A+B 那一档直接落空（三颗各判各的）。
        //  多颗都配得上时按**按下时刻由近到远**取：最近按下的那颗与当前这一下最像同一次组合。
        val paired =
            recent.entries
                .filter { (key, at) -> key != id && now - at <= window }
                .sortedByDescending { it.value }
                .firstNotNullOfOrNull { (key, at) ->
                    bindingFor(listOf(key, id))?.let { Triple(key, at, it) }
                }
        // 没凑成：只有「参与组合的键」才值得先等一个窗口，其余立刻交回单键
        if (paired == null) return if (joinsCombo(id)) Down.HOLD else Down.PASS
        val (other, otherAt, hit) = paired
        val keys = listOf(other, id)
        val c = Combo(hit, keys, pressedAt = now)
        combo = c
        //  只有**还按着**的那几颗需要吞抬手：早松开的那颗不会再有 UP 到来，记进 [consumed]
        //  会一直留着 —— 下一次按它时那条正常抬手就被吞了（表现是「这颗键偶尔按了没反应」）
        keys.filterTo(consumed) { down.containsKey(it) }
        for (k in keys) {
            //  已经松手的那一颗这一下已经算进这档组合了，别再去配后面按下的键
            if (!down.containsKey(k)) recent.remove(k)
            //  先按下的那颗已经排上了长按枪，组合成立就把它摘掉 —— 否则组合期间会判出单键长按
            resetKey(k)
        }
        // 判据级：这一行是「组合键到底认没认出来」的唯一证据。
        XLog.w(
            "组合键：${keys.joinToString(" + ") { it.label }} 同时按下（${hit.id}）" +
                "（间隔 ${now - otherAt}ms，窗口 ${window}ms）",
        )
        scheduleLong(c, startedAt = now)
        return Down.TAKEN
    }

    /**
     * @return true = 这一下被组合接管。
     *
     *  **没有 `now` 这个参数**：抬手这一侧只用得到「谁松了」，时长全在按下那一侧算
     *  （`onDown` 拿 `now` 排长按）。留一个收了不读的参数，签名就在撒谎。
     */
    fun onUp(id: KeyId): Boolean {
        down.remove(id)
        if (id !in consumed) return false
        consumed.remove(id)
        val c = combo
        if (c != null && id in c.keys) {
            // 组合结束：没判过长按 ⇒ 这一次是「一起短按」
            if (!c.longFired) {
                XLog.w("组合键：${c.keys.joinToString(" + ") { it.label }} 短按（先松开 ${id.label}）")
                fire(c, Gesture.SINGLE, repeat = false)
            }
            cancelLong(c)
            cancelRepeat(c)
            combo = null
        }
        // 组合的另一颗键可能还按着 —— 它的抬手同样要吞（`consumed` 里还留着它）
        return true
    }

    /**
     * 清空（配置变化 / 主开关关掉）。
     *
     *  与 `SideKeyInterceptor.cancelPending` 同一处调用：关掉某颗键时，它按住不放的那一下
     *  不能一路走到长按。
     */
    fun reset() {
        combo?.let {
            cancelLong(it)
            cancelRepeat(it)
        }
        combo = null
        down.clear()
        recent.clear()
        consumed.clear()
    }

    /**
     * 排长按那一枪。
     *
     * @param startedAt 第二颗键按下的时刻（组合成立的时刻）—— 阈值从这一刻开始量，
     *   而不是从第一颗键按下的时刻：两颗键可能相差 80ms，按第一颗算会让长按早到最多 80ms。
     */
    private fun scheduleLong(
        c: Combo,
        startedAt: Long,
    ) {
        val shot =
            Runnable {
                try {
                    if (combo !== c || c.longFired) return@Runnable
                    c.shot = null
                    // 两颗键还都按着才算「一起按住不放」
                    if (!c.keys.all { down.containsKey(it) }) return@Runnable
                    c.longFired = true
                    XLog.w(
                        "组合键：${c.keys.joinToString(" + ") { it.label }} 长按" +
                            "（${SystemClock.uptimeMillis() - startedAt}ms，阈值 ${c.key.longPressMs}ms）",
                    )
                    fire(c, Gesture.LONG, repeat = false)
                    // 「长按连续触发」：两颗键一起按住不放就一直重来。
                    //  串行命令（`cmd:` 指多条命令）与灵感语音那两档不连发 —— 与单键那条同一条判据，
                    //  但组合这边**不做这两档的过滤**：组合动作里串行命令重复重放与单键一样没意义，
                    //  而判定侧读不到命令库（`longIsSerial` 只在单键广播里由发送侧现算）。
                    if (c.key.longRepeat) scheduleRepeat(c)
                } catch (e: Throwable) {
                    // 这一枪跑在 system_server 主线程上，抛出去就是整机崩
                    XLog.w("组合键长按判定失败（忽略）：${e.javaClass.simpleName}", e)
                }
            }
        c.shot = shot
        handler.postDelayed(shot, c.key.longPressMs.toLong())
    }

    private fun cancelLong(c: Combo) {
        c.shot?.let { handler.removeCallbacks(it) }
        c.shot = null
    }

    /**
     * 「长按连续触发」的重复派发 —— 与单键那条（`GestureDetector.scheduleRepeat`）同一个形状：
     * 每 [ComboKey.longRepeatIntervalMs] 派一次，抬手 / 重置 / 超过上限时停。
     *
     *  上限兜底是必需的：抬手事件丢了（锁屏打断、非 CANCELED 的丢弃）时没有任何回调会停这条链，
     *  而它每 100ms 自循环派发一次动作 —— 那是用户看不见的连环触发。
     */
    private fun scheduleRepeat(c: Combo) {
        val interval =
            c.key.longRepeatIntervalMs
                .coerceAtLeast(KeyConfig.MIN_REPEAT_INTERVAL_MS)
                .toLong()
        val shot =
            Runnable {
                try {
                    if (combo !== c || !c.longFired || c.keys.any { !down.containsKey(it) }) {
                        c.repeatShot = null
                        return@Runnable
                    }
                    if (SystemClock.uptimeMillis() - (c.pressedAt) > COMBO_REPEAT_MAX_MS) {
                        XLog.w(
                            "组合键：${c.keys.joinToString(" + ") { it.label }} 连续触发超过" +
                                " ${COMBO_REPEAT_MAX_MS}ms 未见抬手，强制停止（UP 可能已丢）",
                        )
                        c.repeatShot = null
                        return@Runnable
                    }
                    fire(c, Gesture.LONG, repeat = true)
                    scheduleRepeat(c)
                } catch (e: Throwable) {
                    XLog.w("组合键连续触发失败（已停止）：${e.javaClass.simpleName}", e)
                }
            }
        c.repeatShot = shot
        handler.postDelayed(shot, interval)
    }

    private fun cancelRepeat(c: Combo) {
        c.repeatShot?.let { handler.removeCallbacks(it) }
        c.repeatShot = null
    }

    private fun fire(
        c: Combo,
        gesture: Gesture,
        repeat: Boolean,
    ) {
        try {
            onCombo(c.key, gesture, repeat)
        } catch (e: Throwable) {
            // 输入线程上抛异常会把 system_server 带崩
            XLog.w("组合键派发失败（已忽略）：${e.javaClass.simpleName}", e)
        }
    }
}
