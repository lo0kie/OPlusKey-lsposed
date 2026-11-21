package com.lookie.opluskey.core.wire

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.ConfigFields
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.core.model.trigger
import kotlinx.serialization.SerialName
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
         * **成员连击**（按住首键、唯一成员键连击）—— 注入侧 `ComboDetector` 判成员连击就取这些，
         * 动作落在这一档组合自己的槽上（与成员键单键多击解耦）。不带的话成员连击整条不生效。
         */
        @SerialName("member_double")
        val memberDouble: String = Action.NONE,
        @SerialName("member_double_on")
        val memberDoubleOn: Boolean = true,
        @SerialName("member_clicks")
        val memberClicks: Map<Int, String> = emptyMap(),
        @SerialName("member_clicks_on")
        val memberClicksOn: List<Int> = emptyList(),
        @SerialName("member_click_then_long")
        val memberClickThenLong: String = Action.NONE,
        @SerialName("member_click_then_long_on")
        val memberClickThenLongOn: Boolean = false,
        @SerialName("member_click_then_long_press_ms")
        val memberClickThenLongPressMs: Int = KeyConfig.DEFAULT_LONG_PRESS_MS,
        /**
         * 成员「单击后长按」的**时序与收尾** —— 判定侧（`GestureDetector` 那条连击链）读它们：
         * 不带的话那一档只能「到点即执行、不连发、没有松手动作」，界面上却摆着这几行。
         */
        @SerialName("member_click_then_long_fire_on_release")
        val memberClickThenLongFireOnRelease: Boolean = false,
        @SerialName("member_click_then_long_repeat")
        val memberClickThenLongRepeat: Boolean = false,
        @SerialName("member_click_then_long_repeat_interval_ms")
        val memberClickThenLongRepeatIntervalMs: Int = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
        @SerialName("member_click_then_long_repeat_vibrate")
        val memberClickThenLongRepeatVibrate: Boolean = false,
        @SerialName("member_click_then_long_release_on")
        val memberClickThenLongReleaseOn: Boolean = false,
        @SerialName("member_click_then_long_release")
        val memberClickThenLongRelease: String = Action.NONE,
        /**
         * 这一档绑的是不是**多条命令** —— 只有广播这一列（与单键 `click_then_long_serial` 同一条）：
         * 注入侧读不到命令库，连发要按它排掉。
         */
        @SerialName("member_click_then_long_serial")
        val memberClickThenLongIsSerial: Boolean = false,
        /**
         * 成员「双击后长按」（成员第 3 击按住）/「三击后长按」（成员第 4 击按住）——
         * 与 [memberClickThenLong] 那一对同一口径，只差前面那几击的击数。
         *
         *  **判定侧要靠这一位**：连击链按「哪几击按住会变长按」数击数（`ChainSpec.thenLongClicks`），
         *  不带的话第 3 击按住不被认，会被当成普通三击派发出去。
         */
        @SerialName("member_double_then_long")
        val memberDoubleThenLong: String = Action.NONE,
        @SerialName("member_double_then_long_on")
        val memberDoubleThenLongOn: Boolean = false,
        @SerialName("member_triple_then_long")
        val memberTripleThenLong: String = Action.NONE,
        @SerialName("member_triple_then_long_on")
        val memberTripleThenLongOn: Boolean = false,
        /** 这两档各自的时序与收尾（与 [memberClickThenLongFireOnRelease] 那一族同名不同槽）。 */
        @SerialName("member_double_then_long_fire_on_release")
        val memberDoubleThenLongFireOnRelease: Boolean = false,
        @SerialName("member_double_then_long_repeat")
        val memberDoubleThenLongRepeat: Boolean = false,
        @SerialName("member_double_then_long_repeat_interval_ms")
        val memberDoubleThenLongRepeatIntervalMs: Int = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
        @SerialName("member_double_then_long_repeat_vibrate")
        val memberDoubleThenLongRepeatVibrate: Boolean = false,
        @SerialName("member_double_then_long_release_on")
        val memberDoubleThenLongReleaseOn: Boolean = false,
        @SerialName("member_double_then_long_release")
        val memberDoubleThenLongRelease: String = Action.NONE,
        @SerialName("member_double_then_long_serial")
        val memberDoubleThenLongIsSerial: Boolean = false,
        @SerialName("member_triple_then_long_fire_on_release")
        val memberTripleThenLongFireOnRelease: Boolean = false,
        @SerialName("member_triple_then_long_repeat")
        val memberTripleThenLongRepeat: Boolean = false,
        @SerialName("member_triple_then_long_repeat_interval_ms")
        val memberTripleThenLongRepeatIntervalMs: Int = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
        @SerialName("member_triple_then_long_repeat_vibrate")
        val memberTripleThenLongRepeatVibrate: Boolean = false,
        @SerialName("member_triple_then_long_release_on")
        val memberTripleThenLongReleaseOn: Boolean = false,
        @SerialName("member_triple_then_long_release")
        val memberTripleThenLongRelease: String = Action.NONE,
        @SerialName("member_triple_then_long_serial")
        val memberTripleThenLongIsSerial: Boolean = false,
        /**
         * 这一组自己的**四格窗口**（毫秒，等待 / 按住 × 固定窗口起点 开 / 关）；`0` = 跟随公共设置。
         *
         *  注入侧判定组合时读的就是它们 —— 不带的话每组只能等公共那几格，
         *  而界面上又能给每组各设一份（一半接线）。
         */
        val waitWindowMs: Int = ComboKey.WINDOW_FOLLOW_GLOBAL,
        val waitPairMs: Int = ComboKey.WINDOW_FOLLOW_GLOBAL,
        val holdWindowMs: Int = ComboKey.WINDOW_FOLLOW_GLOBAL,
        val holdFollowerMs: Int = ComboKey.WINDOW_FOLLOW_GLOBAL,
        /**
         * **这一组开没开「使用独立设置」** —— 少了它，上面那四格等于白带。
         *
         *  `Config.comboWindowsFor` 的第一句就是「独立设置关着就整份看公共」，
         *  而这个函数**跑在注入侧**（`ConfigChannel.windowsFor`）。它解出来的
         *  [ComboKey] 一旦缺这一位就走默认 `false` ⇒ 每组自己的窗口被忽略
         *  （真机症状：「独立的组合等待怎么调都没作用」）。
         */
        val useOwnSettings: Boolean = false,
        /**
         * **触发方式**（`ComboKey.mode`）—— `0` 窗口内先后按下 / `1` 先按住第一颗 / `2` 先按住第二颗。
         *
         *  **必须带**：判定侧（`ComboDetector.pairFor`）按它选判据，`ChordGuard` 按它判「这一档
         * 有没有动作可做」。少了这一项它们读成窗口那条，按住那两档在注入侧根本不成立
         *  —— 界面上配好了、按下去没反应，正是那类一半接线的老毛病。
         */
        val mode: Int = ComboKey.MODE_WAIT,
        /**
         * 这一档自己的「起点键要先按住多久」（毫秒，`ComboKey.holdMinMs`）；`0` = 跟随公共设置。
         *
         *  判定在注入侧（`ComboDetector` 的 `holdMinFor`）—— 不带这一项，每档只能等公共那一档，
         *  而界面上又能给每档各设一份（一半接线）。
         */
        val holdMinMs: Int = ComboKey.HOLD_MIN_FOLLOW_GLOBAL,
        /**
         * **系统场景**（`ComboKey.scenes`）—— 注入侧要它才判得出「这一组到底绑没绑动作」。
         *
         *  `ChordGuard` 决定吞不吞 ROM 的组合判定时，把「场景表非空」也算作有动作可做；
         *  少了这一项，只配了场景的组合在那边看起来一个动作都没绑 ⇒ 透传开着时不吞、
         *  宿主又照常执行场景，于是原生动作与模块动作各来一次。
         */
        val scenes: List<WireScene> = emptyList(),
        /**
         * **首键松手后仍执行它自己的长按**（`ComboKey.starterKeepLong`，`null` = 跟随公共设置）。
         *
         *  判定在注入侧（`ComboDetector.keepStarterLong` 收尾时读它决定补不补派）——
         *  不带这一项，那一档在判定侧永远是 `null` ⇒ 只跟公共值，界面在这里单独拨的一份
         *  等于没拨（真机症状：「开了独立设置、也拨了这一行，还是不补」）。
         *
         *  三态必须原样带过去：`null` 是「跟随公共」，不是 `false`。
         */
        val starterKeepLong: Boolean? = null,
        /**
         * **按顺序触发**（`ComboKey.ordered`）—— 判定在注入侧（`ComboDetector` 按序比对），
         *  不带这一位那一档永远按「只看集合」判，而界面上又能勾（一半接线）。
         */
        val ordered: Boolean = false,
        /**
         * 等待档的**固定窗口起点**（`ComboKey.fixedWindowStart`，`null` = 跟随公共设置）。
         *
         *  判定在注入侧（`ComboDetector` 判窗口起点）—— 不带这一项那一档永远读默认，
         *  而界面上又能给每档各设一份。三态必须原样带过去：`null` 是「跟随公共」，不是 `false`。
         */
        val fixedWindowStart: Boolean? = null,
        /**
         * 按住档的**固定窗口起点**（`ComboKey.holdFixedWindowStart`，`null` = 跟随公共设置）。
         *
         *  与上面那一位是**两个变量**（两张卡各管一种按法）。同样三态原样带 ——
         *  `null` 是「跟随公共」，不是 `false`。
         */
        val holdFixedWindowStart: Boolean? = null,
    )

    /**
     * 一个场景块在广播里的形态 —— 条件是 `BranchCondition.token`，各档动作是 `Action.token`
     * （表里没有的档位 = 这一档不参与）。
     *
     *  为什么在这里再写一份、不复用落盘那个 DTO：`wire` 不许依赖 `core/config`
     *  （注入侧只准依赖 model / log / reflect / wire，见 `docs/architecture/structure.md`），
     *  而这条 extra 正是注入侧在读。
     */
    @Serializable
    private data class WireScene(
        val conditions: List<String> = emptyList(),
        /** 档位 id → 动作 token（`single` / `double` / `long` / `click3` …）。 */
        val actions: Map<String, String> = emptyMap(),
        /**
         * **老的三格** —— 只为「装着新宿主、注入侧还是上一版」那一段过渡期留着。
         *
         *  新写法只认 [actions]；这三位与它同源、由 [of] 一起写出，读侧见到 [actions] 就忽略它们。
         *  注入侧下一次重启换上新代码之后可以整段删掉。
         */
        val single: String = Action.FOLLOW,
        val double: String = Action.FOLLOW,
        val long: String = Action.FOLLOW,
        /**
         * 松手那一格（`SceneRule.releaseOn` / `release`）—— 组合的场景表是**注入侧唯一**
         * 读得到的那一份（它同时给 `ChordGuard` 判「有没有动作可做」），所以松手动作也靠它过去：
         * 少了这两项，块里配的松手动作在判定侧连 `Release` 都发不出来。
         */
        val releaseOn: Boolean = false,
        val release: String = Action.NONE,
        val enabled: Boolean = true,
    ) {
        /** 认不出（空表 / 有一条条件读不懂）就整块丢掉 —— 与落盘那条同一口径。 */
        fun toScene(): SceneRule? {
            val parsed = conditions.map { BranchCondition.parse(it) }
            val bad = parsed.indexOfFirst { it == null }
            if (parsed.isEmpty() || bad >= 0) {
                XLog.w("组合键广播：场景块的条件认不出（整块丢掉）：${conditions.getOrNull(bad) ?: "空"}")
                return null
            }
            return SceneRule(
                conditions = parsed.filterNotNull(),
                actions = slotActions(),
                releaseOn = releaseOn,
                release = Action.parse(release),
                enabled = enabled,
            )
        }

        /** 档位 → 动作：**新格优先**，老格只补它没写到的档位（见 [single] 那段）。 */
        private fun slotActions(): Map<GestureTrigger, Action> {
            val out = LinkedHashMap<GestureTrigger, Action>()
            actions.forEach { (id, token) ->
                val trigger = GestureTrigger.ofId(id)
                if (trigger == null) {
                    XLog.w("组合键广播：场景块里有认不出的档位（丢掉这一档）：$id")
                } else {
                    out[trigger] = Action.parse(token)
                }
            }
            listOf("single" to single, "double" to double, "long" to long).forEach { (id, token) ->
                val trigger = Gesture.ofId(id)?.trigger() ?: return@forEach
                val action = Action.parse(token)
                if (action !is Action.Follow) out.putIfAbsent(trigger, action)
            }
            return out
        }

        companion object {
            fun of(scene: SceneRule): WireScene =
                WireScene(
                    conditions = scene.conditions.map { it.token },
                    actions =
                        scene.actions
                            .filterValues { it !is Action.Follow }
                            .mapKeys { GestureTrigger.idOf(it.key) }
                            .mapValues { it.value.token },
                    //  与 `actions` 同源，只为老读侧那一段过渡期（见字段说明）
                    single = scene.actionOf(Gesture.SINGLE.trigger()).token,
                    double = scene.actionOf(Gesture.DOUBLE.trigger()).token,
                    long = scene.actionOf(Gesture.LONG.trigger()).token,
                    releaseOn = scene.releaseOn,
                    release = scene.release.token,
                    enabled = scene.enabled,
                )
        }
    }

    /**
     * 组合键列表 → 广播里那个字符串。空表也给空串（接收侧按「没有组合键」处理）。
     *
     *  「成员单击后长按绑的是不是多条命令」那一列**由 [ComboKey] 自己带着**
     *  （`memberClickThenLongIsSerial`）：命令库在宿主私有目录、注入侧读不到，所以由发送侧
     *  在组广播之前现算、盖进那份副本里（见 `ConfigBroadcast.intent`）—— 本函数只负责编码。
     */
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
            memberDouble = combo.memberDouble.token,
            memberDoubleOn = combo.memberDoubleOn,
            memberClicks = combo.memberClicks.mapValues { it.value.token },
            memberClicksOn = combo.memberClicksOn.sorted(),
            memberClickThenLong = combo.memberClickThenLong.token,
            memberClickThenLongOn = combo.memberClickThenLongOn,
            //  0 是「跟随公共」的哨兵（`ComboKey.MEMBER_CTL_PRESS_MS_FOLLOW_GLOBAL`）：发送侧
            //  在编之前已经把它解析成生效值盖进副本（见 `ConfigBroadcast`），走到这里还是 0
            //  只可能是有人没盖 —— 那一下按出厂值发，不能把 0 发过去（注入侧会当成「到点即长按」）。
            memberClickThenLongPressMs =
                combo.memberClickThenLongPressMs.takeIf { it > 0 } ?: KeyConfig.DEFAULT_LONG_PRESS_MS,
            memberClickThenLongFireOnRelease = combo.memberClickThenLongFireOnRelease,
            memberClickThenLongRepeat = combo.memberClickThenLongRepeat,
            memberClickThenLongRepeatIntervalMs = combo.memberClickThenLongRepeatIntervalMs,
            memberClickThenLongRepeatVibrate = combo.memberClickThenLongRepeatVibrate,
            memberClickThenLongReleaseOn = combo.memberClickThenLongReleaseOn,
            memberClickThenLongRelease = combo.memberClickThenLongRelease.token,
            memberClickThenLongIsSerial = combo.memberClickThenLongIsSerial,
            memberDoubleThenLong = combo.memberDoubleThenLong.token,
            memberDoubleThenLongOn = combo.memberDoubleThenLongOn,
            memberTripleThenLong = combo.memberTripleThenLong.token,
            memberTripleThenLongOn = combo.memberTripleThenLongOn,
            memberDoubleThenLongFireOnRelease = combo.memberDoubleThenLongFireOnRelease,
            memberDoubleThenLongRepeat = combo.memberDoubleThenLongRepeat,
            memberDoubleThenLongRepeatIntervalMs = combo.memberDoubleThenLongRepeatIntervalMs,
            memberDoubleThenLongRepeatVibrate = combo.memberDoubleThenLongRepeatVibrate,
            memberDoubleThenLongReleaseOn = combo.memberDoubleThenLongReleaseOn,
            memberDoubleThenLongRelease = combo.memberDoubleThenLongRelease.token,
            memberDoubleThenLongIsSerial = combo.memberDoubleThenLongIsSerial,
            memberTripleThenLongFireOnRelease = combo.memberTripleThenLongFireOnRelease,
            memberTripleThenLongRepeat = combo.memberTripleThenLongRepeat,
            memberTripleThenLongRepeatIntervalMs = combo.memberTripleThenLongRepeatIntervalMs,
            memberTripleThenLongRepeatVibrate = combo.memberTripleThenLongRepeatVibrate,
            memberTripleThenLongReleaseOn = combo.memberTripleThenLongReleaseOn,
            memberTripleThenLongRelease = combo.memberTripleThenLongRelease.token,
            memberTripleThenLongIsSerial = combo.memberTripleThenLongIsSerial,
            waitWindowMs = combo.waitWindowMs,
            waitPairMs = combo.waitPairMs,
            holdWindowMs = combo.holdWindowMs,
            holdFollowerMs = combo.holdFollowerMs,
            useOwnSettings = combo.useOwnSettings,
            mode = combo.mode,
            holdMinMs = combo.holdMinMs,
            scenes = combo.scenes.map { WireScene.of(it) },
            starterKeepLong = combo.starterKeepLong,
            ordered = combo.ordered,
            fixedWindowStart = combo.fixedWindowStart,
            holdFixedWindowStart = combo.holdFixedWindowStart,
        )

    /**
     * 一条 → [ComboKey]；认不出返回 `null`。
     *
     *  判据与 `ConfigDto.ComboDto.toCombo` 相同：没有 id 当不了身份（界面上选哪一行靠它），
     *  成员不足 [ComboKey.MIN_KEYS] 颗判不出来（一颗不叫组合）；截断按**判定侧**的
     *  [ComboKey.MAX_JUDGE_KEYS]（理由同那边：界面最多造三颗，但手改文件写进来的
     *  判定侧认，收口在界面的 `MAX_KEYS` 上等于让多成员形态永远到不了判定器）。
     */
    private fun WireCombo.toCombo(): ComboKey? {
        val members = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (id.isEmpty() || members.size < ComboKey.MIN_KEYS) return null
        return ComboKey(
            id = id,
            keys = members.take(ComboKey.MAX_JUDGE_KEYS),
            enabled = enabled,
        ).copy(
            single = Action.parse(single),
            long = Action.parse(long),
            longPressMs = clampLongPress(longPressMs),
            longRepeat = longRepeat,
            longRepeatIntervalMs = clampRepeatInterval(longRepeatIntervalMs),
            longRepeatVibrate = longRepeatVibrate,
            longFireOnRelease = longFireOnRelease,
            longReleaseOn = longReleaseOn,
            longRelease = Action.parse(longRelease),
            //  **成员连击**：与单键多击同一个读法（开关 + 动作分两格，表里只留认得出的）。
            memberDouble = Action.parse(memberDouble),
            memberDoubleOn = memberDoubleOn,
            memberClicks =
                memberClicks.entries
                    .mapNotNull { (n, token) ->
                        val action = Action.parse(token)
                        if (n < 3 || action is Action.None) null else n to action
                    }.toMap(),
            memberClicksOn = memberClicksOn.filter { it >= 3 }.toSet(),
            memberClickThenLong = Action.parse(memberClickThenLong),
            memberClickThenLongOn = memberClickThenLongOn,
            memberClickThenLongPressMs = clampLongPress(memberClickThenLongPressMs),
            //  这一档的时序与收尾（注入侧判定要读）：间隔与单键那一族同一个区间
            memberClickThenLongFireOnRelease = memberClickThenLongFireOnRelease,
            memberClickThenLongRepeat = memberClickThenLongRepeat,
            memberClickThenLongRepeatIntervalMs = clampRepeatInterval(memberClickThenLongRepeatIntervalMs),
            memberClickThenLongRepeatVibrate = memberClickThenLongRepeatVibrate,
            memberClickThenLongReleaseOn = memberClickThenLongReleaseOn,
            memberClickThenLongRelease = Action.parse(memberClickThenLongRelease),
            memberClickThenLongIsSerial = memberClickThenLongIsSerial,
            //  「双击后长按」/「三击后长按」：判定侧按这两档数击数与排长按枪，与上面那一族成对
            memberDoubleThenLong = Action.parse(memberDoubleThenLong),
            memberDoubleThenLongOn = memberDoubleThenLongOn,
            memberTripleThenLong = Action.parse(memberTripleThenLong),
            memberTripleThenLongOn = memberTripleThenLongOn,
            memberDoubleThenLongFireOnRelease = memberDoubleThenLongFireOnRelease,
            memberDoubleThenLongRepeat = memberDoubleThenLongRepeat,
            memberDoubleThenLongRepeatIntervalMs = clampRepeatInterval(memberDoubleThenLongRepeatIntervalMs),
            memberDoubleThenLongRepeatVibrate = memberDoubleThenLongRepeatVibrate,
            memberDoubleThenLongReleaseOn = memberDoubleThenLongReleaseOn,
            memberDoubleThenLongRelease = Action.parse(memberDoubleThenLongRelease),
            memberDoubleThenLongIsSerial = memberDoubleThenLongIsSerial,
            memberTripleThenLongFireOnRelease = memberTripleThenLongFireOnRelease,
            memberTripleThenLongRepeat = memberTripleThenLongRepeat,
            memberTripleThenLongRepeatIntervalMs = clampRepeatInterval(memberTripleThenLongRepeatIntervalMs),
            memberTripleThenLongRepeatVibrate = memberTripleThenLongRepeatVibrate,
            memberTripleThenLongReleaseOn = memberTripleThenLongReleaseOn,
            memberTripleThenLongRelease = Action.parse(memberTripleThenLongRelease),
            memberTripleThenLongIsSerial = memberTripleThenLongIsSerial,
            //  四格窗口：0 = 跟随公共设置，原样留着；设过的钳进那一档区间（手改的越界值在这里收口）
            waitWindowMs = clampFollow(waitWindowMs),
            waitPairMs = clampFollow(waitPairMs),
            holdWindowMs = clampFollow(holdWindowMs),
            holdFollowerMs = clampFollow(holdFollowerMs),
            //  「这一组开了独立设置」—— 与上面那四格是一组：
            //  少了这一位，四格窗口在判定侧永远算不出来（见 `WireCombo` 里那段说明）
            useOwnSettings = useOwnSettings,
            //  触发方式：认不出的取值退回窗口那条，三颗及以上的按住由 `modeFor` 挡回去
            mode = ComboKey.modeFor(mode, members.size),
            //  「起点键要先按住多久」：0 = 跟随公共设置，原样留着；设过的钳进那一档区间
            holdMinMs = if (holdMinMs <= 0) ComboKey.HOLD_MIN_FOLLOW_GLOBAL else clampHoldMin(holdMinMs),
            //  场景表：注入侧只读「非空与否」，但整块带过来才能让两边看到的是同一张表
            scenes = scenes.mapNotNull { it.toScene() },
            //  三态原样带：`null` = 跟随公共设置（见 `WireCombo` 里那段的说明）
            starterKeepLong = starterKeepLong,
            ordered = ordered,
            fixedWindowStart = fixedWindowStart,
            holdFixedWindowStart = holdFixedWindowStart,
        )
    }

    /** 四格窗口里的某一格：`0` 原样留着（= 跟随公共），设过的钳进字段表那一档。 */
    private fun clampFollow(value: Int): Int = if (value <= 0) ComboKey.WINDOW_FOLLOW_GLOBAL else clampWindow(value)

    /** 按键间隔钳进字段表那一档（与公共那份同一个区间，上下限只写一次）。 */
    private fun clampWindow(value: Int): Int {
        val kind = ConfigFields.F_COMBO_WAIT_WINDOW_MS.kind as? ConfigFields.Kind.Number ?: return value
        return value.coerceIn(kind.min, kind.max)
    }

    /** 「首键要先按住多久」同上：区间只在字段表写一次。 */
    private fun clampHoldMin(value: Int): Int {
        val kind = ConfigFields.F_COMBO_HOLD_MIN_MS.kind as? ConfigFields.Kind.Number ?: return value
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

    /** 连发间隔钳进字段表那一档（与单键的「连续触发间隔」同一个区间）。 */
    private fun clampRepeatInterval(value: Int): Int {
        val kind = ConfigFields.F_REPEAT_INTERVAL_MS.kind as? ConfigFields.Kind.Number ?: return value
        return value.coerceIn(kind.min, kind.max)
    }
}

/**
 * 一趟「代取」的结果。
 *
 *  从 `ui` 挪到 `core/wire` 之后**不能再是 `internal`**：`internal` 只在本 Gradle 模块内可见，
 *  而它现在要被 `app` 那一侧（网络适配、检查更新）引用 —— 与 `ModuleWire` / `ConfigBroadcast`
 *  这几个同包类型一样按 public 暴露。
 */
class NetReply(
    /** HTTP 状态码；`0` = 压根没连上（原因见 [error]）。 */
    val status: Int,
    val body: String,
    /** 失败原因；成功时是空串。 */
    val error: String,
) {
    /** 拿到了 2xx 且正文非空 —— 只有这一档才值得去解析。 */
    val ok: Boolean get() = status in 200..299 && body.isNotEmpty()
}

/**
 * 发往宿主那个包（设置页进程与宿主主进程共用的落点）的**通知广播** —— 发送侧一律经这里。
 *
 *  这几条线的发送方散在 `ui/` 与 `host/` 两处（同包两个进程），收方是同一个包名下的接收器，
 *  所以 `setPackage` 那一位的取值都一样：由调用点传进来（本层不认识「宿主包名」是谁）。
 *
 *  收在这里的是那一条纪律：**发不出去只记一条日志，绝不把宿主进程带走**。「广播发出去」会抛
 *  —— 队列满、进程正在退、binder 死了都算 —— 而这几个调用点分别落在组合期、按键执行路径与
 *  报活路径上，漏出去就是宿主被自己发出去的一声通知打死。原来每个调用点各写一份保护与同一段
 *  说明，口径还分了 `try/catch` 与 `runCatching` 两种。
 *
 *  返回布尔而不是吞掉结果：调用方要据此决定「要不要接着报一句」——
 *  报活那条只记日志即可，重启服务那条**不能**杀自己（`send` 失败就没人把它叫回来了）。
 */
object PageWire {
    /**
     * @param pkg 收方所在的包名（广播因此不再是隐式广播，也只有那一个包的接收器收得到）。
     * @return 是否真的发出去了。失败只记一条 `w` 级判据（带上 action），不外抛。
     */
    fun send(
        context: Context,
        pkg: String,
        intent: Intent,
    ): Boolean {
        val action = intent.action.orEmpty()
        return try {
            context.sendBroadcast(intent.setPackage(pkg))
            true
        } catch (e: Throwable) {
            XLog.w("发给 $pkg 的广播没发出去：$action（${e.javaClass.simpleName}）", e)
            false
        }
    }
}

/**
 * 「导出日志」的**回包**那一半 —— 任一进程收到 [ModuleWire.ACTION_LOG_PULL]，就把自己的
 * 环形缓冲切片发回设置页。
 *
 * ## 为什么需要它
 *
 *  设置页那格「导出」原先只导**本进程**的缓冲（`XLog.snapshot`），而判据行散在三个进程：
 *  `system`（接管与按键判定）、`host`（抓取与执行）、`ui`（设置页自己）。用户交回来的日志
 *  因此常常一条按键行都没有 —— 上一次排查「侧键只在电源+侧键时触发」就卡在这里，最后不得不
 *  让对方去 LSPosed 管理器导全进程日志。
 *
 * ## 为什么要分片
 *
 *  缓冲上限 2000 行、几百 KB 量级，一次广播全塞进去会撞 binder 事务上限（发送直接失败，
 *  而且失败是静默的）。所以按**行**切成 [CHUNK_CHARS] 字符左右的片，带上序号与总数，
 *  收方按序号拼回去。单行超过一片时**它独占一片**、不切在行中间 —— 收方是按行重新拼的，
 *  切在行中间会平白多出一个换行，把一条日志劈成两条。
 */
object LogWire {
    /** 一片大概多大 —— 十来 KB，远低于事务上限，也不会切出几百次广播。 */
    private const val CHUNK_CHARS = 12_000

    /**
     * 把本进程的缓冲切好、发回设置页。
     *
     * @param pkg 设置页所在的包名（由调用方传，本层不认识「宿主包名」是谁）
     * @param proc 本进程的标识（`system` / `host`），收方据此分节
     */
    fun reply(
        context: Context,
        pkg: String,
        proc: String,
    ) {
        val lines = XLog.lines()
        val chunks = chunk(lines)
        var sent = 0
        chunks.forEachIndexed { index, text ->
            val ok =
                PageWire.send(
                    context,
                    pkg,
                    Intent(ModuleWire.ACTION_LOG_CHUNK)
                        .putExtra(ModuleWire.EXTRA_LOG_PROC, proc)
                        .putExtra(ModuleWire.EXTRA_LOG_INDEX, index)
                        .putExtra(ModuleWire.EXTRA_LOG_TOTAL, chunks.size)
                        .putExtra(ModuleWire.EXTRA_LOG_TEXT, text)
                        //  这两项每片都带（只有第一片会被采纳）：收方要的是「这一侧是哪次构建、
                        //  有多少行」，而不是逐片的账 —— 分开传反而要在收方再拼一次。
                        .putExtra(ModuleWire.EXTRA_LOG_BUILD, ModuleInfo.BUILD_ID)
                        .putExtra(ModuleWire.EXTRA_LOG_LINES, lines.size),
                )
            if (ok) sent++
        }
        // 一条 W：导出这件事本身要留痕。**报实际发出去几片**：整段宣称「已回 N 片」时，
        //  收方按 total 等最后一片，缺了那片就只剩「这一侧没日志」—— 而日志说它发了。
        if (sent == chunks.size) {
            XLog.w("导出日志：已回 $proc 的缓冲 ${chunks.size} 片（${lines.size} 行 / ${ModuleInfo.BUILD_ID}）")
        } else {
            XLog.w(
                "导出日志：$proc 的缓冲 ${chunks.size} 片只发出去 $sent 片（共 ${lines.size} 行）" +
                    "—— 收方会缺片，先查这一行",
            )
        }
    }

    /** 按行累积到 [CHUNK_CHARS] 就切一片；单行本身就超长时让它独占一片，绝不切在行中间。 */
    private fun chunk(lines: List<String>): List<String> {
        //  **空缓冲也要回一片空的**：一片都不发的话，收方只能按「收到几片」判，
        //  分不出「这个进程在跑但没日志」与「这个进程根本没起来」（排查时会误判某一段缺失）。
        if (lines.isEmpty()) return listOf("")
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (line in lines) {
            //  单行超上限：把它单独成片。收方拿到的每一片都从行首开始、到行尾结束，
            //  拼回去时不会凭空多出一个换行。
            if (line.length > CHUNK_CHARS) {
                if (sb.isNotEmpty()) {
                    out.add(sb.toString())
                    sb.setLength(0)
                }
                out.add(line)
                continue
            }
            //  空缓冲不切：`0 + line + 1` 也可能超上限（单行正好 CHUNK_CHARS 时），
            //  那时会先发一条空分片出去。切之前必须确认这一段真有内容。
            if (sb.isNotEmpty() && sb.length + line.length + 1 > CHUNK_CHARS) {
                out.add(sb.toString())
                sb.setLength(0)
            }
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(line)
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }
}

/**
 * 跨进程的**线格式** —— 三条广播通道上每个 action 与 extra 的字符串，都在这一处定义。
 *
 *  调试通道那条 action 是例外（它不属于这三条通道，也不该让 `inject/` 反过来依赖本文件）：
 *  `inject/ProbeChannel` 与 `app/src/debug/…/PageDebugChannel` 各自留着自己的那一份。
 *
 * ## 为什么它不在 `KeyModule` 上
 *
 *  这一批常量原来挂在 `KeyModule` 的 companion 上。而 `KeyModule` 是**入口**（`java_init.list`
 *  写死了那个全限定名，它自己要去 `host/` 与 `ui/` 装东西），把线格式挂上去之后，
 *  **每一个要用一条广播字符串的人都得 import 入口那个类** —— 于是：
 *
 *   - `host/` 依赖 `KeyModule`，而 `KeyModule` 又要分发到 `host/` ⇒ 包级**成环**
 *     （`host → root → host`，`tools/audit-coupling.py` 抓到的第一条）；
 *   - `ui/` 与 `inject/` 同理，为了一个字符串常量去加载注入入口。
 *
 *  挪进 `core/wire` 之后：三方都只依赖最底层，入口类只剩「派发」这一件事。
 *  取值与语义一条没改，只是换了家 —— `KeyModule` 里那批同名的已删，别再往回加。
 *
 * ## 三条通道
 *
 *   1. system_server → 宿主：[ACTION_GESTURE]（判好的手势，宿主只执行）
 *   2. 宿主 与 设置页：[ACTION_HOST_SERVICE_UP] / [ACTION_HOST_SERVICE_QUERY] /
 *      [ACTION_PAGE_RELAUNCH]（链路自检与把页面拉回来）
 *   3. 设置页 与 注入侧：[ACTION_INJECT_QUERY] / [ACTION_INJECT_READY]（问 / 答那一对）
 *
 *  发送方都必须 `setPackage(...)` 指定包名（指定包名的广播不算隐式广播，
 *  也只有目标注册的接收器能收）—— 包名取自 `core/model/HostApp`。
 */
object ModuleWire {
    /**
     * system_server → 宿主的手势转交广播（宿主私有目录 system_server 读不了，`/data/system`
     * 宿主写不了，广播是两端都能用的通道）。发送时必须指定宿主包名：指定包名的广播不算隐式广播，
     * 也只有宿主注册的接收器能收。带的是判好的手势（判定挪回 system_server —— 宿主会被冻结、
     * 广播积压到解冻才灌，原始按键判定会错乱），宿主只负责执行。
     */
    const val ACTION_GESTURE = "com.lookie.opluskey.GESTURE"

    /** 广播里携带的键 id（`KeyId.id`：内置侧键或收集来的自定义键）。 */
    const val EXTRA_KEY = "key"

    /**
     * 广播里携带的**组合键 id**（`ComboKey.id`：`cmb1` / `cmb2`）。
     *
     *  带了这一项就说明这一条是「两颗键一起按」，宿主按**组合**那条路走（动作在 `ComboKey`
     *  上、没有单颗键的 `KeyConfig`）；不带则仍是 [EXTRA_KEY] 那条单键路径。
     *
     *  两者**不混用**：组合同时把 [EXTRA_KEY] 填成 `cmb1` 的话，宿主日志与去重表里
     *  「键」这一列会混进一种不是键的东西。
     */
    const val EXTRA_COMBO = "combo"

    /** 广播里携带的**手势**（`Gesture.id`：`single` / `double` / `long`）。 */
    const val EXTRA_PHASE = "phase"

    /** 广播里携带的「是否长按连续触发的重复派发」（执行侧据此决定要不要振动、要不要补发报活）。 */
    const val EXTRA_REPEAT = "repeat"

    /**
     * 广播里携带的**场景快照请求**（`starter` / `formed`）—— 这一条不是手势，是「某个时刻到了」。
     *
     *  带了这一项就说明本条只用来让宿主把场景定下来（见 `GestureExec.handleComboScene`），
     *  不执行任何动作：按住组合的起点键按下 / 组合凑成，各报一次。
     */
    const val EXTRA_SCENE_STAGE = "scene_stage"

    /**
     * 广播携带的发送时刻（`SystemClock.elapsedRealtime()`，跨进程同源）。
     * 宿主据此丢弃冻结期间积压、解冻才送到的过期手势，判据见 `host/GestureReceiver` 的 `STALE_MS`。
     */
    const val EXTRA_SENT_AT = "sent_at"

    /**
     * 宿主进程 → 设置页：**宿主侧的模块已经加载**（由 `host/GestureReceiver` 在拿到 Context 后发出，
     * 那一步只有模块注入成功才跑得到）。
     *
     *  这条是首页「模块有没有生效」的**唯一可靠判据**：查宿主那支侧键服务是不行的 ——
     * 它是宿主自己的常驻服务，**系统会自己把它拉起**，服务在跑 ≠ 模块在跑。
     */
    const val ACTION_HOST_SERVICE_UP = "com.lookie.opluskey.HOST_SERVICE_UP"

    /**
     * 设置页 → 宿主进程：**问一声宿主侧的模块在不在**，宿主收到就回一条 [ACTION_HOST_SERVICE_UP]。
     *
     *  要这一问是因为广播是**一次性**的：宿主报活时设置页可能还没起来（那声就错过了）。
     */
    const val ACTION_HOST_SERVICE_QUERY = "com.lookie.opluskey.HOST_SERVICE_QUERY"

    /**
     * 设置页 → 宿主主进程：**「重启服务」请你等一下把设置页拉回来**（执行在 `host/PageRelaunch`）。
     *
     *  被杀掉的进程没法把自己叫回来，而「从进程外叫」那条路在真机上被后台启动策略拦掉
     *（闹钟挂 `PendingIntent` 拉起 Activity 会撞上 `Background activity launch blocked!`）；
     * 宿主主进程自己 `startActivity` 才放行 —— 本包拿着 `START_ACTIVITIES_FROM_BACKGROUND`。
     * 所以这一声必须发到**宿主主进程**（收方是 `host/GestureReceiver` 那一个接收器），
     * 不是宿主本身就算数。
     */
    const val ACTION_PAGE_RELAUNCH = "com.lookie.opluskey.PAGE_RELAUNCH"

    /**
     * 宿主主进程 → 设置页：**上面那条请求收到了，我会把页面拉回来**（回执）。
     *
     *  必须要有这一条：`sendBroadcast` 不抛异常只说明「系统受理了这条广播」，**不说明有人接**。
     *  宿主主进程活着但接收器没装上（签名闸拦下 / 换了包 / 那个进程是旧的）时，设置页照旧杀自己，
     *  于是页面没了、也没人再把它叫回来 —— 只能靠用户手动重进。收到回执再杀，那条路才不存在。
     */
    const val ACTION_PAGE_RELAUNCH_ACK = "com.lookie.opluskey.PAGE_RELAUNCH_ACK"

    /**
     * 模块入口（管理器那颗 FAB 背后的 `EntryActivity`）→ 宿主主进程：**把侧键设置页拉到前台**。
     *
     *  为什么绕这一趟：那个 Activity 在宿主清单里带
     *  `android:permission="oplus.permission.OPLUS_COMPONENT_SAFE"`，普通 app 直接 `startActivity`
     *  必被 `SecurityException` 拦；而宿主起**自己包**的页面没有这道检查。
     *
     *  这一条的接收器必须是 `RECEIVER_EXPORTED`：发送方是**另一个 uid** 的普通 app（模块自己的包），
     *  与 [ACTION_HOST_SERVICE_QUERY] / [ACTION_PAGE_RELAUNCH] 那几条「同 uid 的设置页进程」不同。
     *  它只做「把设置页拉到前台」一件事，不读配置、不改状态。
     */
    const val ACTION_OPEN_SETTINGS = "com.lookie.opluskey.OPEN_SETTINGS"

    /**
     * [ACTION_OPEN_SETTINGS] 的附加位：**这次只借跳板把进程拉起来，不要转设置页**。
     *
     *  用它的只有一处：宿主换 binder 时拉起跳板（见 `ShizukuHost.requestGrant` 的换进程那一步）。
     *  那一档用户正开着设置页，再转一次会把页里那摞子页（命令详情、抽屉 …）清掉、退回第一页。
     */
    const val EXTRA_WAKE_ONLY = "com.lookie.opluskey.extra.WAKE_ONLY"

    // ── 链路自检：设置页 与 注入侧（system_server） ──
    //  这一对是**问 / 答**，只为「首页能不能说清是哪一段断了」而存在（见 `ui/wire/LinkCheck.kt`）。
    //  注入侧不必为它额外挂 Hook：它已经挂在 AMS 的广播入口上，全系统每条广播都过那里；
    //  来源校验比的是 uid，而设置页与宿主**同 uid**（`docs/device/lsposed.md` 六之七）。

    /** 设置页 → 注入侧：**问一声注入侧那套装好没有**。 */
    const val ACTION_INJECT_QUERY = "com.lookie.opluskey.INJECT_QUERY"

    /**
     * 注入侧 → 设置页：回 [ACTION_INJECT_QUERY] 的状态包。
     *
     *  注意：**不要把这个 action 加进注入侧 `KeyHooks.readBroadcast` 的过滤表** ——
     * 那会让注入侧去处理自己发出去的包（多绕一圈，且毫无意义）。
     */
    const val ACTION_INJECT_READY = "com.lookie.opluskey.INJECT_READY"

    /** [ACTION_INJECT_READY] 里带「装上几个侧键接管点」（0 = 一个都没装上）。 */
    const val EXTRA_INJECT_HOOKS = "inject_hooks"

    /** [ACTION_INJECT_READY] 里带「注入侧收到过配置没有」（false ⇒ 注入侧还在用默认值顶着）。 */
    const val EXTRA_INJECT_CONFIG_OK = "inject_config_ok"

    /**
     * [ACTION_INJECT_READY] 里带「注入侧最后一次判出手势是什么时候」。
     *
     * `SystemClock.elapsedRealtime()` —— 跨进程同源，页面直接与自己的时钟相减就是「多久以前」。
     * 0 = 开机以来从未判出过（侧键一次都没被接管）。
     */
    const val EXTRA_INJECT_LAST_GESTURE_AT = "inject_last_gesture_at"

    /**
     * [ACTION_INJECT_READY] 里带「注入侧手上那份配置的指纹」（见 `ConfigBroadcast.tag`）。
     *
     *  设置页拿它与自己手上那份比：**相等才叫「设置生效」**。「收到过一条」不等于「就是最新那份」——
     *  开机收到过之后哪条广播丢了，注入侧还按旧配置跑，只回一个布尔的话界面照样报「正常」。
     *  还没收到过配置时这一项为空串。
     */
    const val EXTRA_INJECT_CONFIG_TAG = "inject_config_tag"

    /**
     * [ACTION_INJECT_READY] 里带「注入侧跑的是哪一次构建」（`ModuleInfo.BUILD_ID`）。
     *
     *  判「换过包但没重启」的**备用尺子**：主尺子是 [EXTRA_INJECT_PROC_START]（进程启动时刻 vs
     *  包更新时间，不需要人记得改任何常量）。这一项留下的理由有两个 ——
     *   · 旧版注入侧不含时间戳那一项时，只能靠它（读到空串 = 对面连它都没带，按「不判」处理）；
     *   · 它是「同一次构建」的**身份**，日志里对得上人看得出来是哪一版。
     *  代价是它要人手维护：忘了改就量不出差别，所以**不能让它单独决定「要不要重启」**。
     */
    const val EXTRA_INJECT_BUILD_ID = "inject_build_id"

    /**
     * [ACTION_INJECT_READY] 里带「注入侧所在进程的启动时刻」（wall clock 毫秒，见 `ProcClock.startWall`）。
     *
     *  判「换过包但没重启」的**主尺子**：与安装包的 `lastUpdateTime` 一比就知道 ——
     *  进程起得比包早 ⇒ 它加载的是上一版代码。
     *  0 = 对面是还没带这一项的旧版，此时退回 [EXTRA_INJECT_BUILD_ID] 那条。
     */
    const val EXTRA_INJECT_PROC_START = "inject_proc_start"

    /**
     * 宿主 → 注入侧：**替我注入一颗按键**（[EXTRA_INJECT_CODE]）。
     *
     *  现在只有「截图」用：宿主注入 `KEYCODE_SYSRQ` 时 `injectInputEvent` 返回成功、屏幕没反应
     *  —— ColorOS 不认普通 app 注入的那一下。注入侧跑在 `system_server`，身份就是系统本身
     *  （与 `input keyevent` 同级），那一下它认。
     *
     *  为什么不让宿主 `su` 跑 `service call color_screenshot`：内置动作不该为了截一张图去要 root
     *  （弹授权、慢半拍、没 root 的机器上直接不可用）。这条通道既不走 shell、也不要授权。
     *
     *  收方是 `inject/KeyHooks` 那个 AMS 广播入口（注入侧不给它单独挂接收器）——
     *  它看得见全系统每一条广播，`setPackage` 不影响它收；但发送侧（`ActionRunner.requestSystemInject`）
     *  仍然 `setPackage(宿主包名)` 定向：裸发就是一条谁都能注册接收的隐式广播。
     */
    const val ACTION_INJECT_KEY = "com.lookie.opluskey.INJECT_KEY"

    /** [ACTION_INJECT_KEY] 里带「要注入的键码」（`KeyId.KEYCODE_SYSRQ` 之类）。 */
    const val EXTRA_INJECT_CODE = "inject_code"

    /**
     * 宿主 → 注入侧：**替它弹一条提示**（[EXTRA_TOAST_TEXT]）。
     *
     *  为什么挪到注入侧弹：系统 toast 按**包**限流（3 条 / 20 秒、5 条 / 42 秒、6 条 / 68 秒，
     *  见 `docs/device/actions/feedback.md` 四十），宿主的包连按几下之后后面那些直接被丢。注入侧跑在
     *  `system_server`，用 `ActivityThread.getSystemContext()`（包名 `android`）弹，那个包持有
     *  `UNLIMITED_TOASTS` ⇒ 免限流。同一句话、同一个模块，只是换了发话人。
     *
     *  收方同样是 `inject/KeyHooks` 那个 AMS 广播入口；发送侧（`host/action/CommandFailure`）
     *  照旧 `setPackage(宿主包名)` 定向（理由同上一条）。
     */
    const val ACTION_TOAST = "com.lookie.opluskey.TOAST"

    /** [ACTION_TOAST] 里带「要弹的那句话」。 */
    const val EXTRA_TOAST_TEXT = "toast_text"

    /**
     * 宿主 → 注入侧：**替我把音量设到某一档**（[EXTRA_VOLUME_STREAM] / [EXTRA_VOLUME_INDEX]）。
     *
     *  为什么挪到注入侧做：宿主进程里的 `setStreamVolume` 会被 ColorOS 静默丢掉
     *  （`docs/device/actions/audio.md` 三十四 的实测），只剩「注入音量键」这一条退路 ——
     *  而一颗键就是一整格（量程的 1/16），`±1%` 这种细步进根本走不出来。注入侧跑在
     *  `system_server`，身份是系统本身，那条策略不挡它，于是又能拿到**精确档位**。
     *
     *  与 [ACTION_INJECT_KEY] 同一条通道、同一条纪律：收方是 `inject/KeyHooks` 的 AMS 广播入口，
     *  发送侧 `setPackage(宿主包名)` 定向。带 `FLAG_SHOW_UI` —— 与宿主自己写时同一个口径
     *  （改完看得见那条面板）。
     */
    const val ACTION_SET_VOLUME = "com.lookie.opluskey.SET_VOLUME"

    /** [ACTION_SET_VOLUME] 里带「哪一路流」（`AudioManager.STREAM_*` 的编号）。 */
    const val EXTRA_VOLUME_STREAM = "volume_stream"

    /** [ACTION_SET_VOLUME] 里带「目标档位」（该流的原始下标，不是百分比）。 */
    const val EXTRA_VOLUME_INDEX = "volume_index"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主进程的启动时刻」（同上）。宿主是普通应用进程，重开即可，不必重启手机。 */
    const val EXTRA_HOST_PROC_START = "host_proc_start"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主读到的主开关真值」——页面据此判它是不是读到了真配置。 */
    const val EXTRA_HOST_ENABLED = "host_enabled"

    /**
     * [ACTION_HOST_SERVICE_UP] 里带「宿主**读得懂**那份配置文件」。
     *
     *  这一位与 [EXTRA_HOST_ENABLED] **必须分开**：读不懂时 `read()` 退化成
     *  `Config.DEFAULT`，它的 `enabled` 也是 false —— 与「用户自己关了主开关」在
     *  [EXTRA_HOST_ENABLED] 这一位上**完全同形**。只看后者的话，执行侧一个动作都做不出来
     *  而页面报「主开关已关闭」，把人指去拨一个本来就开着的开关（真机撞过：
     *  `配置正文与 md5 对不上` → 退回默认 → 所有按键都没反应，自检却全绿）。
     *
     *  **缺省按 true 处理**：这一位是本版新加的，对面若是更早的宿主则不带它 —— 那时不该
     *  凭空报一条「配置读取失败」。
     */
    const val EXTRA_HOST_CONFIG_READABLE = "host_config_readable"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主最后一次执行动作是什么时候」（同上，0 = 从未）。 */
    const val EXTRA_HOST_LAST_ACTION_AT = "host_last_action_at"

    // ── 「自定义按键」页：监听按键（设置页 与 注入侧） ──
    //
    //  一对「开 / 关」+ 一对「听见了」。设置页进页时开、出页时关；注入侧在监听期间把每一颗
    //  按下的键原样报回来（见 `inject/KeyWatch`，**只读旁路**，不改认领与放行）。
    //
    //  注意：[ACTION_KEY_SEEN] 的接收方是**设置页自己**注册的接收器（与设置页同进程），
    //  不经 `GestureReceiver` —— 那边属 `host/`，而 host/ 不许认识界面层
    //  （`docs/architecture/decoupling.md` 动作 1、2 与 `tools/audit-coupling.py`）。

    /** 设置页 → 注入侧：开 / 关那条旁路监听（开关在 [EXTRA_WATCH_ON]）。 */
    const val ACTION_KEY_WATCH = "com.lookie.opluskey.KEY_WATCH"

    /** [ACTION_KEY_WATCH] 里带「开还是关」。 */
    const val EXTRA_WATCH_ON = "watch_on"

    /**
     * 「等按键」这一趟最多持续多久。
     *
     *  它是**两侧共用的契约**：注入侧到点自己停发键码（`KeyWatch` 的兜底），页面按同一个数
     *  显示倒计时并在同一刻收场。两处各写一个 60 秒的话，界面上的倒计时会与真实窗口对不上。
     */
    const val KEY_WATCH_WINDOW_MS = 60_000L

    /** 注入侧 → 设置页：听见一颗键（**只报按下那一下**）。 */
    const val ACTION_KEY_SEEN = "com.lookie.opluskey.KEY_SEEN"

    /** [ACTION_KEY_SEEN] 里带「键码」。 */
    const val EXTRA_SEEN_CODE = "seen_code"

    // ── 导出日志：把另外两个进程的日志一起收上来 ──
    //
    //  设置页那格「导出」只导**本进程**的缓冲（见 `XLog.snapshot`），而判据行散在三个进程里：
    //  `system`（system_server 的接管与按键判定）、`host`（宿主主进程的抓取与执行）、
    //  `ui`（设置页自己）。用户抓回来的日志因此常常「一条按键行都没有」—— 排查时等于瞎。
    //
    //  一问多答：[ACTION_LOG_PULL] 由设置页发出，另外两个进程各自把缓冲切片回 [ACTION_LOG_CHUNK]；
    //  设置页收几百毫秒、拼起来再落盘。**分片**是必需的：一次广播塞几百 KB 会撞事务上限。

    /** 设置页 → 注入侧 / 宿主主进程：把你的日志缓冲回给我。 */
    const val ACTION_LOG_PULL = "com.lookie.opluskey.LOG_PULL"

    /** 任一进程 → 设置页：一段日志（分片，序号与总数见下面两个 extra）。 */
    const val ACTION_LOG_CHUNK = "com.lookie.opluskey.LOG_CHUNK"

    /** [ACTION_LOG_CHUNK] 里带「这一段是哪个进程的」（`system` / `host` / `ui`）。 */
    const val EXTRA_LOG_PROC = "log_proc"

    /** [ACTION_LOG_CHUNK] 里带「第几片」（从 0 起）。 */
    const val EXTRA_LOG_INDEX = "log_index"

    /** [ACTION_LOG_CHUNK] 里带「一共几片」。 */
    const val EXTRA_LOG_TOTAL = "log_total"

    /** [ACTION_LOG_CHUNK] 里带「这一段的正文」。 */
    const val EXTRA_LOG_TEXT = "log_text"

    /**
     * [ACTION_LOG_CHUNK] 里带「回包这一侧跑的是**哪一次构建**」（`ModuleInfo.BUILD_ID`）。
     *
     *  三个进程的版本可以不一致 —— 换包没重启时 `system_server` 里还是上一版代码，而它照样
     *  能回日志。收方把这一位写进导出文件头，「宿主 / 注入是旧版」于是不用再靠猜。
     */
    const val EXTRA_LOG_BUILD = "log_build"

    /**
     * [ACTION_LOG_CHUNK] 里带「这一侧缓冲一共几行」。
     *
     *  与「没回包」分开：0 行 = 这个进程在跑、只是还没打过日志；没回包 = 它压根没起来
     *  （或没装模块）。两种情形在排查里含义完全不同。
     */
    const val EXTRA_LOG_LINES = "log_lines"

    // ── 快捷方式：请注入侧（system_server）读**快捷方式服务的活数据** ──
    //
    //  「选择界面」里那份清单在宿主进程读，而 `LauncherApps` 对「钉住」这一档按**调用方**过滤：
    //  宿主不是桌面宿主读不到，换成系统身份（uid 1000）走同一个 API 也是同一结果（真机实测
    //  `动态 3 / 清单 0 / 钉住 0`）。注入侧就住在 `system_server` 里，能直接读那个服务手上的活数据
    //  （`ShortcutUser` 里的 `ShortcutInfo`，含 `isPinned`）—— 见 `ShortcutRead`。
    //
    //  一问一答：[ACTION_SHORTCUT_QUERY] 由宿主发，注入侧回 [ACTION_SHORTCUT_RESULT]，
    //  带 [EXTRA_SHORTCUT_REQ] 配对（一次可能并发几条）。

    /** 宿主 → 注入侧：**这个包在桌面上钉了哪几条**（按系统身份读）。 */
    const val ACTION_SHORTCUT_QUERY = "com.lookie.opluskey.SHORTCUT_QUERY"

    /** 注入侧 → 宿主：上面那条的结果（`id` 与名字各一份列表，等长）。 */
    const val ACTION_SHORTCUT_RESULT = "com.lookie.opluskey.SHORTCUT_RESULT"

    /** 两条都带：**这一趟的编号**（谁发谁收靠它配对）。 */
    const val EXTRA_SHORTCUT_REQ = "shortcut_req"

    /** [ACTION_SHORTCUT_QUERY] 里带「读哪个包」。 */
    const val EXTRA_SHORTCUT_PKG = "shortcut_pkg"

    /** [ACTION_SHORTCUT_QUERY] 里带「哪个用户」（0 = 主用户，999 = 分身）。 */
    const val EXTRA_SHORTCUT_USER = "shortcut_user"

    /**
     * [ACTION_SHORTCUT_RESULT] 里带「快捷方式的 id」与「名字」两份列表。
     *
     *  两份分开传而不是拼一个字符串：名字里什么字符都可能有（中文、冒号、逗号、换行），
     *  拼了就得自己定一套转义 —— 而这条通道只用一次，不值当。
     */
    const val EXTRA_SHORTCUT_IDS = "shortcut_ids"

    /** 见 [EXTRA_SHORTCUT_IDS]。 */
    const val EXTRA_SHORTCUT_LABELS = "shortcut_labels"

    // ── 注入侧代发网络请求：宿主没 INTERNET，只有 system_server 有 ──
    //
    //  宿主的清单里**没有** `INTERNET`（`reference/gesture.apk` 的 49 条权限里只有
    //  `ACCESS_NETWORK_STATE`），而界面代码跑在宿主进程 —— 所以「检查更新」这类要出网的事，
    //  只能请注入侧代取。能力本身在 `inject/NetChannel`，实测结论见 `docs/device/lsposed.md` 四十一
    //  （能连，但必须先绑默认网络再解析 DNS）。
    //
    //  一问一答：[ACTION_NET_REQUEST] 由宿主发（同 uid 的设置页也行），注入侧取回正文后
    //  用 [ACTION_NET_RESULT] 回给宿主。**只做 GET、只认 https** —— 这是一个「取一段文本」的
    //  通道，不是通用代理。

    /** 宿主 → 注入侧：**替我取一个 URL**（GET，只接受 https）。 */
    const val ACTION_NET_REQUEST = "com.lookie.opluskey.NET_REQUEST"

    /** 注入侧 → 宿主：上面那条的结果。 */
    const val ACTION_NET_RESULT = "com.lookie.opluskey.NET_RESULT"

    /** 两条都带：**这一趟的编号** —— 一次可能并发好几条，靠它配对（谁发谁收）。 */
    const val EXTRA_NET_ID = "net_id"

    /** [ACTION_NET_REQUEST] 里带「要取的地址」。 */
    const val EXTRA_NET_URL = "net_url"

    /** [ACTION_NET_RESULT] 里带「HTTP 状态码」；`0` = 压根没连上（原因见 [EXTRA_NET_ERROR]）。 */
    const val EXTRA_NET_STATUS = "net_status"

    /** [ACTION_NET_RESULT] 里带「正文」（超过 [NET_BODY_LIMIT] 的部分截掉）。 */
    const val EXTRA_NET_BODY = "net_body"

    /** [ACTION_NET_RESULT] 里带「失败原因」；成功时是空串。 */
    const val EXTRA_NET_ERROR = "net_error"

    /**
     * 正文上限（字符数）—— 一次广播塞几百 KB 会撞 binder 事务上限。
     *
     *  检查更新只要几 KB（releases 那个 JSON），32 KB 留了十倍余量；超了截断并把
     *  `error` 写成「正文超长已截断」，让调用方知道这不是完整正文。
     */
    const val NET_BODY_LIMIT = 32 * 1024

    /** 这一趟最多等多久（毫秒）—— 连接与读取各用这一个值。 */
    const val NET_TIMEOUT_MS = 8_000
}
