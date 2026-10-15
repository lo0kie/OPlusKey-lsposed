package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 配置文件的解析与序列化：纯函数，不碰文件 IO。
 * 格式为 JSON（`org.json` 平台自带，不引依赖）。
 * 界面与执行侧都要解析同一份文本，解析逻辑只有这一份。
 *
 * 本层只管**正文那份 JSON**：磁盘上正文之外还套了一层外皮（[FileObfuscation]，`ConfigStore` 负责剥/包）。
 *
 * 正文顶层带一个 `md5` 字段（[FIELD_MD5]）：它是**把这一格摘掉之后**那份 JSON 的 MD5。
 *  两道用处：文件被截断 / 传坏时不用等到后面才露馅；被改过而没把这一格一起重算的，直接判读不懂
 *  （[digestMatches]）。算的是**文本的字节**而不是语义 —— 只把缩进 / 换行弄乱照样认。
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
    private const val FIELD_LANGUAGE = "language"

    /**
     * 正文自带的完整性字段，见 [serialize] / [digestMatches]。
     * **写在最后一位** —— 校验时把它摘掉再序列化，得到的必须与写盘那一刻扣掉它之后的那份逐字节
     * 相同，顺序一变就对不上。
     */
    private const val FIELD_MD5 = "md5"

    /** 拼十六进制用，见 [md5Hex]。 */
    private const val HEX = "0123456789abcdef"

    /** [Config.disclaimerAccepted] 的键名 —— 与 `keys` / `global` 同一层，不在它们里面。 */
    private const val FIELD_DISCLAIMER = "disclaimerAccepted"

    /**
     * 解析配置文本，**读不懂返回 null**（空文本 / 坏 JSON / 缺 `keys` 段 / 缺 `md5` 或对不上）。
     *
     * 与 [parse] 的区别是它把两件事分开了，这个区分是必须的：
     *  · 「读懂了、但某些字段缺失」 ⇒ 缺的字段走默认值（老文件 / 手写文件的兼容，[fromJson] 负责）；
     *  · 「压根读不懂」 ⇒ **null**，绝不能退化成默认值。
     * 因为默认值里 `enabled=false`、三个手势全是「无操作」、`vibrate=false` ——
     * **写方**一旦把这份默认值当成真相继续用、再照常写回，用户配的东西就被整份替换掉了
     *（真机上真发生过一次「振动开关自己变 false」，根因就是这里只有「有值」一条路）。
     *
     * 文件本身是原子写（`.tmp` + rename，见 `ConfigStore.writeAtomically`），所以「读到半截」
     * 只会来自**外部改动**（root 直接改、别的进程手写）。真撞上了，这一层就是那道闸。
     */
    fun parseOrNull(text: String): Config? = try {
        val root = JSONObject(text)
        if (root.optJSONObject(KEY_KEYS) == null) {
            XLog.d("配置里没有 keys 段 —— 不算一份配置")
            null
        } else if (!digestMatches(root)) {
            // 缺 md5 / 对不上都算读不懂；日志在 digestMatches 里给（这里不能每条按键都刷一遍）
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
            language = g?.optString(FIELD_LANGUAGE, GlobalConfig.DEFAULT_LANGUAGE)
                ?: GlobalConfig.DEFAULT_LANGUAGE,
        )

        // 缺这一格一律按「没确认过」（老文件 / 手写的文件）—— 那是安全的那一侧：多弹一次不吃亏。
        return Config(
            keys = keys,
            global = global,
            disclaimerAccepted = root.optBoolean(FIELD_DISCLAIMER),
        )
    }

    /**
     * 序列化成 JSON 文本（回写时用），缩进两格，末位带上 `md5`。
     *
     * 顺序是**先算再写**：把不含 `md5` 的那份算一个 MD5，再把这一格加到最后 ——
     * 所以它不覆盖它自己，校验时才有得比（[digestMatches]）。
     */
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
            .put(FIELD_LANGUAGE, config.global.language)

        val root = JSONObject()
            .put(FIELD_VERSION, VERSION)
            .put(KEY_KEYS, keys)
            .put(KEY_GLOBAL, global)
            .put(FIELD_DISCLAIMER, config.disclaimerAccepted)
        val digest = md5Hex(textWithoutDigest(root))
        return root.put(FIELD_MD5, digest).toString(2) + "\n"
    }

    /**
     * 核对正文的 `md5`。
     *
     * 比法：把 `md5` 那一格摘掉，按同一套缩进重新序列化，与文件里那个数比 —— 比的是**文本字节**，
     * 所以「改了值没重算」当场露馅；只把缩进 / 换行 / 键序弄乱的（值没动）照样认，手边工具重新
     * 格式化一遍不至于读不出来。
     *
     * 摘之前先 `toString` 再 `JSONObject(...)` 复制一份：直接摘调用方那只的话，后面 [fromJson]
     * 就用不上原本那份了。
     */
    private fun digestMatches(root: JSONObject): Boolean {
        val stored = if (root.isNull(FIELD_MD5)) "" else root.optString(FIELD_MD5, "")
        if (stored.isEmpty()) {
            XLog.w("配置里没有 $FIELD_MD5 —— 判为读不懂")
            return false
        }
        val body = JSONObject(root.toString())
        body.remove(FIELD_MD5)
        val expect = md5Hex(textWithoutDigest(body))
        if (expect == stored) return true
        XLog.w("配置正文与 $FIELD_MD5 对不上（被改过 / 传坏）：算出来 $expect，文件里是 $stored")
        return false
    }

    /**
     * 拿去算 `md5` 的那份文本：**不含 `md5`** 的 [body]，缩进两格、末尾一个换行。
     *
     * **写与校必须走这一个函数**：两边各写一遍缩进的话，将来改了一处就会出现「自己写的文件
     * 自己读不出来」。
     */
    private fun textWithoutDigest(body: JSONObject): String = body.toString(2) + "\n"

    /** 小写十六进制 MD5。自己拼十六进制而不走 `String.format`：后者吃系统区域设置。 */
    private fun md5Hex(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)
}
