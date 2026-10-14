package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import org.json.JSONObject

/**
 * 配置文件的解析与序列化：纯函数，不碰文件 IO。
 * 格式为 JSON（`org.json` 平台自带，不引依赖）。
 * 界面与执行侧都要解析同一份文本，解析逻辑只有这一份。
 */
object ConfigCodec {

    /** 当前格式版本，解析时不做版本兼容，只写进文件备查。 */
    const val VERSION = 1

    private const val FIELD_VERSION = "version"
    private const val KEY_KEYS = "keys"
    private const val KEY_GLOBAL = "global"
    private const val FIELD_ENABLED = "enabled"
    private const val FIELD_SINGLE = "single"
    private const val FIELD_DOUBLE = "double"
    private const val FIELD_LONG = "long"
    private const val FIELD_LONG_PRESS_MS = "longPressMs"
    private const val FIELD_LONG_REPEAT = "longRepeat"
    private const val FIELD_REPEAT_INTERVAL_MS = "longRepeatIntervalMs"
    private const val FIELD_DOUBLE_CLICK_MS = "doubleClickMs"
    private const val FIELD_VIBRATE = "vibrate"
    private const val FIELD_INSTANT_SINGLE = "instantSingle"
    private const val FIELD_TOAST_ON_RUN = "toastOnRun"

    /**
     * 解析配置文本，**读不懂返回 null**（空文本 / 坏 JSON / 缺 `keys` 段）。
     *
     * 与 [parse] 的区别是它把两件事分开了，这个区分是必须的：
     *  · 「读懂了、但某些字段缺失」 ⇒ 缺的字段走默认值（老文件 / 手写文件的兼容，[fromJson] 负责）；
     *  · 「压根读不懂」 ⇒ **null**，绝不能退化成默认值。
     * 因为默认值里 `enabled=false`、三个手势全是「无操作」、`vibrate=false` ——
     * **写方**一旦把这份默认值当成真相继续用、再照常写回，用户配的东西就被整份替换掉了
     *（2026-10-14 真机上真发生过一次「振动开关自己变 false」，根因就是这里只有「有值」一条路）。
     *
     * 文件本身是原子写（`.tmp` + rename，见 `ConfigStore.writeAtomically`），所以「读到半截」
     * 只会来自**外部改动**（root 直接改、别的进程手写）。真撞上了，这一层就是那道闸。
     */
    fun parseOrNull(text: String): Config? = try {
        val root = JSONObject(text)
        if (root.optJSONObject(KEY_KEYS) == null) {
            XLog.d("配置里没有 keys 段 —— 不算一份配置")
            null
        } else {
            fromJson(root)
        }
    } catch (e: Throwable) {
        // 只给 d：宿主每条按键前都要读一遍，这条会成为噪声；判据看调用方（页面那条 w）
        XLog.d("配置解析失败（读不懂）：${e.message}")
        null
    }

    /**
     * [parseOrNull] 的「一定有值」版：读不懂退化成 [Config.DEFAULT]。
     *
     * ⚠️ **只给只读方用**（宿主执行侧：读不到就按「不接管」处理，行为与原生一致）。
     * 写方要走 [parseOrNull] / `ConfigStore.load()`，见上面那段。
     */
    fun parse(text: String): Config = parseOrNull(text) ?: Config.DEFAULT

    /**
     * 取一个字符串字段。不能直接用 `optString(key, fallback)`：键不存在才走 fallback，
     * 手改文件写成 `"long": null` 时它返回字符串 "null"（`JSONObject.NULL.toString()`），
     * `Action.parse("null")` 兜底成 `Shell("null")`，按那个手势会真去跑 `sh -c null` 而非「无操作」。
     * 只有 optString 有这个坑：`optBoolean` / `optInt` 类型不匹配（含 `JSONObject.NULL`）一律返回 fallback。
     */
    private fun textOf(o: JSONObject?, key: String, fallback: String): String =
        if (o == null || o.isNull(key)) fallback else o.optString(key, fallback)

    /** 从 JSON 构造配置。缺失的项一律走默认值（整段缺、单个字段缺都算）。 */
    private fun fromJson(root: JSONObject): Config {
        val keysJson = root.optJSONObject(KEY_KEYS)
        val keys = KeyId.entries.associateWith { id ->
            val o = keysJson?.optJSONObject(id.id)
            KeyConfig(
                id = id,
                enabled = o?.optBoolean(FIELD_ENABLED) ?: false,
                // 缺省 `none`：这一档没写就是「无操作」（`Config.DEFAULT` 同值）
                single = Action.parse(textOf(o, FIELD_SINGLE, "none")),
                double = Action.parse(textOf(o, FIELD_DOUBLE, "none")),
                long = Action.parse(textOf(o, FIELD_LONG, "none")),
                longPressMs = clamp(
                    o?.optInt(FIELD_LONG_PRESS_MS, KeyConfig.DEFAULT_LONG_PRESS_MS)
                        ?: KeyConfig.DEFAULT_LONG_PRESS_MS,
                    KeyConfig.MIN_LONG_PRESS_MS,
                    KeyConfig.MAX_LONG_PRESS_MS,
                ),
                longRepeat = o?.optBoolean(FIELD_LONG_REPEAT) ?: false,
                longRepeatIntervalMs = clamp(
                    o?.optInt(FIELD_REPEAT_INTERVAL_MS, KeyConfig.DEFAULT_REPEAT_INTERVAL_MS)
                        ?: KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                    KeyConfig.MIN_REPEAT_INTERVAL_MS,
                    KeyConfig.MAX_REPEAT_INTERVAL_MS,
                ),
            )
        }

        val g = root.optJSONObject(KEY_GLOBAL)
        val global = GlobalConfig(
            doubleClickMs = clamp(
                g?.optInt(FIELD_DOUBLE_CLICK_MS, GlobalConfig.DEFAULT_DOUBLE_CLICK_MS)
                    ?: GlobalConfig.DEFAULT_DOUBLE_CLICK_MS,
                GlobalConfig.MIN_DOUBLE_CLICK_MS,
                GlobalConfig.MAX_DOUBLE_CLICK_MS,
            ),
            vibrate = g?.optBoolean(FIELD_VIBRATE) ?: false,
            instantSingle = g?.optBoolean(FIELD_INSTANT_SINGLE)
                ?: GlobalConfig.DEFAULT_INSTANT_SINGLE,
            // 执行时提示缺省不弹
            toastOnRun = g?.optBoolean(FIELD_TOAST_ON_RUN)
                ?: GlobalConfig.DEFAULT_TOAST_ON_RUN,
        )

        return Config(keys, global)
    }

    /** 序列化成 JSON 文本（回写时用），缩进两格。 */
    fun serialize(config: Config): String {
        val keys = JSONObject()
        for (id in KeyId.entries) {
            val k = config.key(id)
            keys.put(
                id.id,
                JSONObject()
                    .put(FIELD_ENABLED, k.enabled)
                    .put(FIELD_SINGLE, k.single.token)
                    .put(FIELD_DOUBLE, k.double.token)
                    .put(FIELD_LONG, k.long.token)
                    .put(FIELD_LONG_PRESS_MS, k.longPressMs)
                    .put(FIELD_LONG_REPEAT, k.longRepeat)
                    .put(FIELD_REPEAT_INTERVAL_MS, k.longRepeatIntervalMs),
            )
        }
        val global = JSONObject()
            .put(FIELD_DOUBLE_CLICK_MS, config.global.doubleClickMs)
            .put(FIELD_VIBRATE, config.global.vibrate)
            .put(FIELD_INSTANT_SINGLE, config.global.instantSingle)
            .put(FIELD_TOAST_ON_RUN, config.global.toastOnRun)

        return JSONObject()
            .put(FIELD_VERSION, VERSION)
            .put(KEY_KEYS, keys)
            .put(KEY_GLOBAL, global)
            .toString(2) + "\n"
    }

    private fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)
}
