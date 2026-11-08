package com.lookie.opluskey.core.wire

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.ConfigFields
import com.lookie.opluskey.core.model.KeyConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 组合键（`Config.combos`）在**配置广播**里的那一条 extra。
 *
 * ## 为什么组合键必须广播
 *
 *  其余几张「表」（`app_names` / 规则表）只走落盘就够了 —— 读它们的是宿主，宿主本来就读得到那份文件。
 *  组合键不一样：**判「这两颗键凑成的是哪一档」发生在注入侧**（`ComboDetector`），而注入侧
 *  （system_server）进不去宿主的私有目录，手上只有广播解出来的那份 `Config`。
 *  不给它这一条，`Config.combos` 在那边恒为空 ⇒ 「有没有绑定」永远答否 ⇒ 两颗键照旧各走各的
 *  （那正是接线之前的表现）。
 *
 * ## 为什么是 JSON，不是自造分隔符
 *
 *  这一条里要装**动作 token**，而 `shell:` 那一档后面是整条命令 —— 里面 `|` `;` 换行什么都有。
 *  用可见字符当分隔符，迟早会被某条命令切坏。控制字符虽然能避开，但调试时 `dumpsys` / 日志里
 *  完全读不出来。
 *
 *  JSON 两样都占：转义由序列化器负责，人也能直接读。
 *
 * ## 与落盘那份的关系
 *
 *  字段与 `ConfigDto.ComboDto` 那一段**一一对应**，但**不复用那个类**：一个是文件格式、
 *  一个是广播格式，两者的演进节奏不同（广播可以随时加 extra，落盘改名等于换文件格式）。
 *  这也与 `ConfigFields`(广播) / `ConfigDto`(落盘) 分成两处的既有分工一致。
 */
object ComboWire {
    /** 广播里那条 extra 的名字（`ConfigBroadcast` 用）。 */
    const val EXTRA = "combos"

    /**
     * 编解码器 —— `ignoreUnknownKeys` 让「新版发、旧版收」不至于整条丢掉；
     * `encodeDefaults` 让接收侧拿到的每个字段都是明确值（不靠默认值脑补）。
     */
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /**
     * 一档组合的广播形态。
     *
     *  键名与落盘那份取一样的字面量（`keys` / `single` / `long` / `longPressMs`）：两份都看的人
     *  不必在脑子里换算，而它们本来就是同一个模型的两个投影。
     */
    @Serializable
    private data class WireCombo(
        val id: String = "",
        /** 参与的键（`KeyId.id`）。 */
        val keys: List<String> = emptyList(),
        val enabled: Boolean = true,
        val single: String = Action.NONE,
        val long: String = Action.NONE,
        val longPressMs: Int = KeyConfig.DEFAULT_LONG_PRESS_MS,
        /** 长按连续触发（注入侧要它才排得出重复派发）。 */
        val longRepeat: Boolean = false,
        val longRepeatIntervalMs: Int = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
        val longRepeatVibrate: Boolean = false,
        /**
         * 「长按松手才触发」—— 注入侧靠它决定到点是不是立刻派发。
         *
         *  **这三个字段必须一起带**：注入侧的组合键只从这条广播来（`ConfigChannel` 那一支），
         *  它读不到盘。少带一个，那一档在判定侧就永远是默认值 —— 「松手才触发」退化成到点触发、
         *  「松手执行额外动作」整条不派发，而界面上是能配、能落盘的（一半接线）。
         */
        val longFireOnRelease: Boolean = false,
        /** 「松手执行额外动作」的开关（与单键同名那一位同义）。 */
        val longReleaseOn: Boolean = false,
        /** 松手那一档的动作 token。 */
        val longRelease: String = Action.NONE,
        /**
         * 这一组自己的「按键之间的等待」（毫秒）；`0` = 跟随公共设置。
         *
         *  注入侧判定组合时读的就是它 —— 不带的话每组只能等公共那一档，
         *  而界面上又能给每组各设一份（一半接线）。
         */
        val windowMs: Int = ComboKey.WINDOW_FOLLOW_GLOBAL,
        /**
         * **这一组开没开「使用独立设置」** —— 少了它，上面那个 `windowMs` 等于白带。
         *
         *  `Config.comboWindowFor` / `comboPassthroughFor` 的第一句都是「独立设置关着就整份看公共」，
         *  而这两个函数**跑在注入侧**（`ConfigChannel.windowFor` / `ChordGuard`）。它解出来的
         *  [ComboKey] 一旦缺这一位就走默认 `false` ⇒ 每组自己的窗口与透传全被忽略
         *  （真机症状：「独立的组合等待怎么调都没作用」）。
         */
        val useOwnSettings: Boolean = false,
        /** 这一组自己的「无操作时执行按键命令」；`null` = 没写过（跟随公共）。 */
        val passthrough: Boolean? = null,
    )

    /** 组合键列表 → 广播里那个字符串。空表也给空串（接收侧按「没有组合键」处理）。 */
    fun encode(combos: List<ComboKey>): String =
        if (combos.isEmpty()) {
            ""
        } else {
            try {
                json.encodeToString(
                    ListSerializer(WireCombo.serializer()),
                    combos.map(::toWire),
                )
            } catch (e: Throwable) {
                //  拼不出就退回空串：宁可这一版组合键不生效，也不能让发送侧抛出去
                XLog.w("组合键广播编码失败（本次不带组合键）：${e.javaClass.simpleName}", e)
                ""
            }
        }

    /**
     * 广播里那个字符串 → 组合键列表。
     *
     *  认不出的条目**整条丢掉**（与落盘那条同一口径）：一条写坏的组合键如果兜成「永远成立」，
     *  会把两颗键的单键判定一起摘掉 —— 表现是「按了没反应」，比「这一档不生效」难查得多。
     *  整段读不懂就当作没有组合键，绝不上抛（这段跑在 AMS 广播入口，抛出去就是 system_server 崩）。
     */
    fun decode(raw: String?): List<ComboKey> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            val list = json.decodeFromString(ListSerializer(WireCombo.serializer()), raw)
            val parsed = list.mapNotNull { it.toCombo() }
            if (parsed.size != list.size) {
                XLog.w("组合键广播：${list.size - parsed.size} 档认不出（已丢弃）")
            }
            parsed
        } catch (e: Throwable) {
            XLog.w("组合键广播读不懂（按没有组合键处理）：${e.javaClass.simpleName}", e)
            emptyList()
        }
    }

    private fun toWire(combo: ComboKey): WireCombo =
        WireCombo(
            id = combo.id,
            keys = combo.keys,
            enabled = combo.enabled,
            single = combo.single.token,
            long = combo.long.token,
            longPressMs = combo.longPressMs,
            longRepeat = combo.longRepeat,
            longRepeatIntervalMs = combo.longRepeatIntervalMs,
            longRepeatVibrate = combo.longRepeatVibrate,
            longFireOnRelease = combo.longFireOnRelease,
            longReleaseOn = combo.longReleaseOn,
            longRelease = combo.longRelease.token,
            windowMs = combo.windowMs,
            useOwnSettings = combo.useOwnSettings,
            passthrough = combo.passthrough,
        )

    /**
     * 一条 → [ComboKey]；认不出返回 `null`。
     *
     *  判据与 `ConfigDto.ComboDto.toCombo` 相同：没有 id 当不了身份（界面上选哪一行靠它），
     *  成员不足 [ComboKey.MIN_KEYS] 颗判不出来（一颗不叫组合）；截断按**判定侧**的
     *  [ComboKey.MAX_JUDGE_KEYS]（理由同那边：界面造不出三颗以上的组合，但手改文件写进来的
     *  判定侧认，收口在界面的 `MAX_KEYS` 上等于让多成员形态永远到不了判定器）。
     */
    private fun WireCombo.toCombo(): ComboKey? {
        val members = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (id.isEmpty() || members.size < ComboKey.MIN_KEYS) return null
        return ComboKey(
            id = id,
            keys = members.take(ComboKey.MAX_JUDGE_KEYS),
            enabled = enabled,
            single = Action.parse(single),
            long = Action.parse(long),
            longPressMs = clampLongPress(longPressMs),
            longRepeat = longRepeat,
            longRepeatIntervalMs = clampRepeatInterval(longRepeatIntervalMs),
            longRepeatVibrate = longRepeatVibrate,
            longFireOnRelease = longFireOnRelease,
            longReleaseOn = longReleaseOn,
            longRelease = Action.parse(longRelease),
            //  0 = 跟随公共设置，原样留着；设过的钳进那一档区间（老版本 / 手改的越界值在这里收口）
            windowMs = if (windowMs <= 0) ComboKey.WINDOW_FOLLOW_GLOBAL else clampWindow(windowMs),
            //  「这一组开了独立设置、以及它自己的透传」—— 与 `windowMs` 是一组：
            //  少了这两位，上面那个窗口在判定侧永远算不出来（见 `WireCombo` 里那段说明）
            useOwnSettings = useOwnSettings,
            passthrough = passthrough,
        )
    }

    /** 按键间隔钳进字段表那一档（与公共那份同一个区间，上下限只写一次）。 */
    private fun clampWindow(value: Int): Int {
        val kind = ConfigFields.F_COMBO_WINDOW_MS.kind as? ConfigFields.Kind.Number ?: return value
        return value.coerceIn(kind.min, kind.max)
    }

    /**
     * 长按阈值钳进字段表那一档区间。
     *
     *  与单键共用同一个区间（`ConfigFields.F_LONG_PRESS_MS`）：它量的是同一件事，而上下限只该
     *  在那一张表里写一次。手改的广播 / 老版本写进来的越界值在这里收口。
     */
    private fun clampLongPress(value: Int): Int {
        val kind = ConfigFields.F_LONG_PRESS_MS.kind as? ConfigFields.Kind.Number ?: return value
        return value.coerceIn(kind.min, kind.max)
    }

    /** 连发间隔钳进字段表那一档（与单键的「触发间隔」同一个区间）。 */
    private fun clampRepeatInterval(value: Int): Int {
        val kind = ConfigFields.F_REPEAT_INTERVAL_MS.kind as? ConfigFields.Kind.Number ?: return value
        return value.coerceIn(kind.min, kind.max)
    }
}
