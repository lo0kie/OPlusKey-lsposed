package com.lookie.opluskey.inject.input

import android.os.Handler
import android.os.SystemClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId

/**
 * 「两颗键一起按」那一下的窗口（毫秒）—— 两颗键的 DOWN 相差不超过它，才算同时按下。
 *
 *  真机实测：电源与音量下同时按，两条 DOWN 相差 3ms（同一线程、同一串事件）。80ms 既盖得住
 *  人手的同时按，又不会把「先按 A、松手后再按 B」这种连按算成组合。
 */
private const val COMBO_DOWN_WINDOW_MS = 80L

/**
 * 组合键的判定 —— **任意两颗已接管的键**同时按下，就当作一次组合，不再各自判单击 / 长按。
 *
 *  为什么不做成「配置里登记某一对键」：登记需要一套绑定数据与入口（界面），而这一层先只把
 *  「同时按下」这件事认出来。认出来之后要执行什么，由装配方决定（`onCombo`）。
 *
 *  与 [GestureDetector] 的分工：那个判单颗键的手势，这个只判「两颗键的重叠」。一旦认出组合，
 *  立刻把这两颗键的单键状态机**摘掉**（[resetKey]），否则先按下的那颗会在组合期间判出长按 ——
 *  一次按键执行两个动作。
 *
 *  线程：与 [GestureDetector] 同一条输入线程（DOWN/UP 都在它上面进来），长按那一枪走
 *  [handler]（main looper）。跨线程的字段一律 `@Volatile`。
 */
internal class ComboDetector(
    private val handler: Handler,
    /**
     * 判出一次组合。第二个参数 = 是不是「一起按住不放」那一档。
     *
     *  与单键的 `onGesture` 分开：组合没有单颗键的 `KeyConfig`，走不了那条按 key 查动作的路。
     */
    private val onCombo: (keys: List<KeyId>, long: Boolean) -> Unit,
    /** 摘掉某颗键的单键计时器（组合成立时用，见类注释）。 */
    private val resetKey: (KeyId) -> Unit,
    /**
     * 这一组键有没有绑定 —— **没有绑定就不成立组合**，两颗键照旧各走各的。
     *
     *  这一条是「不许静默」的那道闸：组合一旦成立就会把两颗键的单键状态机摘掉，
     *  没有绑定还照样摘，用户看到的就是「两键同按什么反应都没有」。
     */
    private val hasBinding: (List<KeyId>) -> Boolean,
) {
    /** 当前按着的键 → 按下时刻。 */
    private val down = HashMap<KeyId, Long>()

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
        val keys: List<KeyId>,
        /** 长按那一枪，抬手 / 重置时摘掉。 */
        @Volatile var shot: Runnable? = null,
        /** 已经判过「一起按住不放」。 */
        @Volatile var longFired: Boolean = false,
    )

    /** @return true = 这一下被组合接管（调用方不要再喂单键判定器）。 */
    fun onDown(
        id: KeyId,
        config: KeyConfig,
        now: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        down[id] = now
        // 已经在组合里：第三颗键按下来也不掺和，仍按「组合进行中」吞掉
        if (combo != null) return true
        val other =
            down.entries.firstOrNull { (key, at) ->
                key != id && now - at <= COMBO_DOWN_WINDOW_MS
            } ?: return false

        val keys = listOf(other.key, id)
        // 没有绑定 ⇒ 组合不成立：不摘状态机、不记账、把这一下交回单键判定器。
        //  先判这一条再动任何状态 —— 反过来（先摘再判）就回到「按了没反应」。
        if (!hasBinding(keys)) return false
        val c = Combo(keys)
        combo = c
        consumed += keys
        // 先按下的那颗已经排上了长按枪，组合成立就把它摘掉 —— 否则组合期间会判出单键长按
        for (k in keys) resetKey(k)
        // 判据级：这一行是「组合键到底认没认出来」的唯一证据。
        XLog.w(
            "组合键：${keys.joinToString(" + ") { it.label }} 同时按下" +
                "（间隔 ${now - other.value}ms，窗口 ${COMBO_DOWN_WINDOW_MS}ms）",
        )
        scheduleLong(c, config)
        return true
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
                fire(c.keys, long = false)
            }
            cancelLong(c)
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
        combo?.let { cancelLong(it) }
        combo = null
        down.clear()
        consumed.clear()
    }

    private fun scheduleLong(
        c: Combo,
        config: KeyConfig,
    ) {
        val startedAt = down.values.maxOrNull() ?: SystemClock.uptimeMillis()
        val shot =
            Runnable {
                try {
                    if (combo !== c || c.longFired) return@Runnable
                    c.shot = null
                    // 两颗键还都按着才算「一起按住不放」
                    if (!c.keys.all { it in down }) return@Runnable
                    c.longFired = true
                    XLog.w(
                        "组合键：${c.keys.joinToString(" + ") { it.label }} 长按" +
                            "（${SystemClock.uptimeMillis() - startedAt}ms）",
                    )
                    fire(c.keys, long = true)
                } catch (e: Throwable) {
                    // 这一枪跑在 system_server 主线程上，抛出去就是整机崩
                    XLog.w("组合键长按判定失败（忽略）：${e.javaClass.simpleName}", e)
                }
            }
        c.shot = shot
        handler.postDelayed(shot, config.longPressMs.toLong())
    }

    private fun cancelLong(c: Combo) {
        c.shot?.let { handler.removeCallbacks(it) }
        c.shot = null
    }

    private fun fire(
        keys: List<KeyId>,
        long: Boolean,
    ) {
        try {
            onCombo(keys, long)
        } catch (e: Throwable) {
            // 输入线程上抛异常会把 system_server 带崩
            XLog.w("组合键派发失败（已忽略）：${e.javaClass.simpleName}", e)
        }
    }
}
