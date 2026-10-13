package com.lookie.opluskey.config

import com.lookie.opluskey.XLog
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
     * 解析配置文本，任何问题都退化成 [Config.DEFAULT]，绝不抛异常：
     * 读取失败按不接管（行为与原生一致）处理，而不是让侧键失效。
     */
    fun parse(text: String): Config = try {
        fromJson(JSONObject(text))
    } catch (e: Throwable) {
        XLog.w("配置解析失败（走默认值）：${e.message}")
        Config.DEFAULT
    }

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
                single = Action.parse(textOf(o, FIELD_SINGLE, "native")),
                // 双击 / 长按上的 `native` 会被正常化成 `none`
                double = Action.normalizeForGesture(
                    Action.parse(textOf(o, FIELD_DOUBLE, "none")),
                    Gesture.DOUBLE,
                ),
                long = Action.normalizeForGesture(
                    Action.parse(textOf(o, FIELD_LONG, "none")),
                    Gesture.LONG,
                ),
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
                    // 写盘前重跑 normalizeForGesture：双击 / 长按上的 `native` 正常化成 `none`，
                    // 否则文件与内存永久不等价（下次解析出不同的对象）。
                    .put(
                        FIELD_DOUBLE,
                        Action.normalizeForGesture(k.double, Gesture.DOUBLE).token,
                    )
                    .put(FIELD_LONG, Action.normalizeForGesture(k.long, Gesture.LONG).token)
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
