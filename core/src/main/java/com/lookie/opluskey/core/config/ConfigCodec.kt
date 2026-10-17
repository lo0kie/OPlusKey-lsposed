package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.ConfigFields
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
 * 正文顶层带一个 `md5` 字段（[ConfigFields.MD5_JSON]）：它是**把这一格摘掉之后**那份 JSON 的 MD5。
 *  两道用处：文件被截断 / 传坏时不用等到后面才露馅；被改过而没把这一格一起重算的，直接判读不懂
 *  （[digestMatches]）。算的是**文本的字节**而不是语义 —— 只把缩进 / 换行弄乱照样认。
 */
object ConfigCodec {
    /** 当前格式版本，解析时不做版本兼容，只写进文件备查。 */
    const val VERSION = 1

    /** 拼十六进制用，见 [md5Hex]。 */
    private const val HEX = "0123456789abcdef"

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
     *  「**整个 KeyId 都没写**」也算读不懂（`{"keys":{}}` 配一份自洽的 `md5` 就是这种）：见 [parseOrNull]。
     *
     * 文件本身是原子写（`.tmp` + rename，见 `ConfigStore.writeAtomically`），所以「读到半截」
     * 只会来自**外部改动**（root 直接改、别的进程手写）。真撞上了，这一层就是那道闸。
     */
    fun parseOrNull(text: String): Config? =
        try {
            val root = JSONObject(text)
            // 「id 在、个别字段缺」才是兼容那一档（老格式加过 `instantSingle` / `toastOnRun` 那类
            //  字段，缺了按默认补）。**id 整个都没写**是另一回事：键盘集合只有一个 `PLUS`，少它
            //  意味着文件被裁剪过或被别的写法生成的 —— 按默认值补等于静默把主开关 / 三个绑定 /
            //  振动全抹成默认，比判「读不懂」危险得多，所以连这一道一起要求。
            val keys = root.optJSONObject(ConfigFields.KEYS_JSON)
            if (keys == null || KeyId.entries.any { keys.optJSONObject(it.id) == null }) {
                XLog.d("配置里没有 keys 段 / 缺 KeyId 条目 —— 不算一份配置")
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
    private fun textOf(
        o: JSONObject?,
        key: String,
        fallback: String,
    ): String =
        if (o == null ||
            o.isNull(key)
        ) {
            fallback
        } else {
            o.optString(key, fallback)
        }

    /**
     * 从 JSON 构造配置。缺失的项一律走 `ConfigFields` 那张**字段表**里的默认值（整段缺、单个字段缺都算）。
     *
     *  字段 → 属性的映射不在这里：那在 `core/model/Config.kt` 的 `ofFields` 一处（本层只负责
     *  「按类型把 JSON 里的值取出来」）。
     */
    private fun fromJson(root: JSONObject): Config {
        val keysJson = root.optJSONObject(ConfigFields.KEYS_JSON)
        val keys =
            KeyId.entries.associateWith { id ->
                KeyConfig.ofFields(id, readFields(keysJson?.optJSONObject(id.id), ConfigFields.KEY_FIELDS))
            }
        val global =
            GlobalConfig.ofFields(readFields(root.optJSONObject(ConfigFields.GLOBAL_JSON), ConfigFields.GLOBAL_FIELDS))
        return Config.ofFields(keys, global, readFields(root, ConfigFields.ROOT_FIELDS), readNames(root))
    }

    /**
     * 读「打开应用」那一族显示名的表（`app_names` 那一段）；缺整段 / 不是对象时返回空表。
     *
     *  这是配置里**唯一一段映射**，所以不走 `readFields`（那张表是扁平的）。容错口径与其它段一致：
     *  读不懂的条目**跳过**，不让一个手改坏的行把整份配置判成读不懂 —— 名字只是显示，
     *  丢了最坏也就是退回包名。
     */
    private fun readNames(root: JSONObject): Map<String, String> {
        val o = root.optJSONObject(ConfigFields.APP_NAMES_JSON) ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        val it = o.keys()
        while (it.hasNext()) {
            val key = it.next()
            if (o.isNull(key)) continue
            // **不 trim**：这里读出来的必须与文件里逐字一致，否则重新序列化会算出另一个
            //  md5（`digestMatches` 判「被改过」而整份配置作废）。空值只跳过、不改写。
            val name = o.optString(key, "")
            if (key.isNotEmpty() && name.isNotEmpty()) out[key] = name
        }
        return out
    }

    /**
     * 按字段表从 JSON 里取值 —— 缺项走字段表里登记的默认值（老文件 / 手写文件的兼容）。
     *
     *  布尔这一列有一个**从旧实现原样带过来的坑，故意不动**：`JSONObject.optBoolean(key)` 在键缺失时
     *  返回 `false` 而不是默认值 ⇒「有 `global` 段、但段里没写 `instantSingle`」的文件读出来是
     *  `false`，而它登记的默认值是 `true`。改成「缺项补默认值」会让这类文件的这一档静默翻面 ——
     *  那是**行为变更**，不混在结构改动里做（要改就单独一笔，并在 `changelog/` 里说清影响面）。
     */
    private fun readFields(
        o: JSONObject?,
        fields: List<ConfigFields.Field>,
    ): ConfigFields.Values {
        val values = mutableMapOf<ConfigFields.Field, Any>()
        for (f in fields) {
            val key = f.json ?: continue
            when (val kind = f.kind) {
                is ConfigFields.Kind.Bool -> {
                    values[f] = o?.optBoolean(key) ?: kind.default
                }

                is ConfigFields.Kind.Number -> {
                    val raw = o?.optInt(key, kind.default) ?: kind.default
                    values[f] = raw.coerceIn(kind.min, kind.max)
                }

                is ConfigFields.Kind.Text -> {
                    values[f] = textOf(o, key, kind.default)
                }
            }
        }
        return ConfigFields.Values(values)
    }

    /**
     * 序列化成 JSON 文本（回写时用），缩进两格，末位带上 `md5`。
     *
     * 顺序是**先算再写**：把不含 `md5` 的那份算一个 MD5，再把这一格加到最后 ——
     * 所以它不覆盖它自己，校验时才有得比（[digestMatches]）。
     *
     * 键序由 `ConfigFields` 的字段表给出（表序 = 键序），本层不再逐字段抄一遍。
     */
    fun serialize(config: Config): String {
        val keys = JSONObject()
        for (id in KeyId.entries) {
            val one = JSONObject()
            writeFields(one, config.key(id).fieldValues(), ConfigFields.KEY_FIELDS)
            keys.put(id.id, one)
        }
        val global = JSONObject()
        writeFields(global, config.global.fieldValues(), ConfigFields.GLOBAL_FIELDS)

        val root =
            JSONObject()
                .put(ConfigFields.VERSION_JSON, VERSION)
                .put(ConfigFields.KEYS_JSON, keys)
                .put(ConfigFields.GLOBAL_JSON, global)
        writeFields(root, config.fieldValues(), ConfigFields.ROOT_FIELDS)
        // 名字表：按身份 → 名字。**空表不写这一段**（从没绑过应用的人，文件格式与从前逐字相同），
        //  键按字典序写 —— md5 算的是文本字节，键序会进它，而这张表的来源是 `Map`（无序）。
        if (config.appNames.isNotEmpty()) {
            val names = JSONObject()
            for ((key, name) in config.appNames.toSortedMap()) names.put(key, name)
            root.put(ConfigFields.APP_NAMES_JSON, names)
        }
        val digest = md5Hex(textWithoutDigest(root))
        return root.put(ConfigFields.MD5_JSON, digest).toString(2) + "\n"
    }

    /** 按字段表把一份取值写进一个 JSON 对象（[ConfigFields.Field.json] 为 `null` 的项跳过）。 */
    private fun writeFields(
        o: JSONObject,
        v: ConfigFields.Values,
        fields: List<ConfigFields.Field>,
    ) {
        for (f in fields) {
            val key = f.json ?: continue
            when (f.kind) {
                is ConfigFields.Kind.Bool -> o.put(key, v.bool(f))
                is ConfigFields.Kind.Number -> o.put(key, v.number(f))
                is ConfigFields.Kind.Text -> o.put(key, v.text(f))
            }
        }
    }

    /**
     * 核对正文的 `md5`。
     *
     * 比法：把 `md5` 那一格摘掉，按同一套缩进重新序列化，与文件里那个数比 —— 比的是**文本字节**，
     * 所以「改了值没重算」当场露馅。
     *
     *  注意**键序也会进 md5**：摘掉 `md5` 那一格之后是按「当前解析出来的键序」重新序列化再算的，
     *  用外部工具把键序重排过（值没动）就对不上了 —— 缩进与换行不影响（序列化时统一重排），
     *  键序影响。这一条原来写成「键序弄乱也认」，与实现对不上。
     *
     * 摘之前先 `toString` 再 `JSONObject(...)` 复制一份：直接摘调用方那只的话，后面 [fromJson]
     * 就用不上原本那份了。
     */
    private fun digestMatches(root: JSONObject): Boolean {
        val stored = if (root.isNull(ConfigFields.MD5_JSON)) "" else root.optString(ConfigFields.MD5_JSON, "")
        if (stored.isEmpty()) {
            XLog.w("配置里没有 ${ConfigFields.MD5_JSON} —— 判为读不懂")
            return false
        }
        val body = JSONObject(root.toString())
        body.remove(ConfigFields.MD5_JSON)
        val expect = md5Hex(textWithoutDigest(body))
        if (expect == stored) return true
        XLog.w("配置正文与 ${ConfigFields.MD5_JSON} 对不上（被改过 / 传坏）：算出来 $expect，文件里是 $stored")
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
}
