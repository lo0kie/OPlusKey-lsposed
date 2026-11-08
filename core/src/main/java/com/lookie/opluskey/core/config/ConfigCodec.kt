package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * 配置文件的解析与序列化：纯函数，不碰文件 IO。格式为 JSON。
 *
 * 本层只管**正文那份 JSON**：磁盘上正文之外还套了一层外皮（[FileObfuscation]，`ConfigStore` 负责剥/包）。
 *
 * ## 用 kotlinx.serialization 而不是 `org.json`
 *
 *  `org.json` 有两个把人坑过的行为：`optString` 遇到 `null` 字面量会返回字符串 `"null"`
 *  （于是 `Action.parse("null")` 兜底成 `Shell("null")`，按那个手势会真去跑 `sh -c null`）；
 *  `optBoolean` 在键缺失时返回 `false`，而不是「没写」这个语义。两者都让「缺字段」与
 *  「明确写了 false / null」混在一起，只能靠手写清洗兜住。
 *
 *  kotlinx 直接绑到 [ConfigDto] 上的类型与默认值：缺字段就是缺字段（走 `@Serializable` 的默认值），
 *  类型不对直接抛异常 ⇒ 判「读不懂」，不会静默变成某个合法但错误的值。
 *
 * ## 完整性字段 `md5` 与版本
 *
 *  · **`version == VERSION`**：`md5` 是「把 `md5` 这一格置空之后、**按 [ConfigDto] 重新编码**得到
 *    的那份文本」的 MD5（不是原文逐字节），对不上判读不懂；
 *  · **`version < VERSION`**：**不校验 `md5`**，只要求「能被解析 + 键段完整」。
 *
 *  为什么老版本一律不校验：`md5` 的口径是「按**当前** [ConfigDto] 重新编码」，而 DTO 每加一个字段
 *  （`encodeDefaults = true` 会把它写进正文），老文件重编码出来的字节就必然与它自己那份 `md5`
 *  不同 —— 校验只会把**每一份老配置都判成读不懂**，随后 `ConfigStore.read` 退回默认值、下一次
 *  写盘把用户的绑定与开关整份抹掉。真机上就发生过这一回：「每颗键一份单击延迟」把字段从
 *  `global` 搬到 `KeyDto` 之后，所有 v2 文件当场全被判成读不懂、页面顶着「配置读取失败」。
 *
 *  所以**往 [ConfigDto] 加 / 挪字段必须同时抬 [VERSION]**（见下），老文件读进来之后下一次写盘
 *  自然升级成新版本并带上新 md5。
 */
object ConfigCodec {
    /**
     * 当前格式版本。
     *
     *  1 = `org.json` 那版；2 = kotlinx + 新的 md5 口径；
     *  3 = `KeyDto` 多了 `doubleClickMs` / `instantSingle`（从 `global` 段搬下来，每颗键一份）；
     *  4 = `GlobalDto` 多了 `enabled`（模块总闸从 `keys.plus.enabled` 里拆出来）；
     *  5 = `KeyDto` 多了 `longRepeatVibrate`（连续触发时是否每次重复都振动）；
     *  6 = `GlobalDto` 多了 `vibrateStrength`（振动强度三档）；
     *  7 = `KeyDto` 多了 `rules`（按状态分支的动作），顶层多了 `combos`（组合按键）；
     *  8 = `GlobalDto` 多了 `palette`（页面配色）；
     *  9 = `GlobalDto` 多了 `theme`（界面颜色：跟随系统 / 浅色 / 深色）；
     *  10 = `GlobalDto` 多了 `followDisplaySize` / `pageScale`（跟随系统显示大小 / 页面缩放）；
     *  11 / 12 = （见代码历史，本次未改动）；
     *  13 = 当前上一版；
     *  14 = `ComboDto` 多了 `rules`（组合键的条件执行）；
     *  18 = `StateRuleDto` 多了 `enabled`（场景可以停用；15~17 见代码历史）。
     *  19 = （见代码历史，本次未改动）；
     *  20 = 顶层多了 `profile_id`（多配置的身份从「名字空 + 位次」换成显式 id）。
     *  21 = `KeyDto` 多了 `extra_clicks`（第 3 击起的额外档位；界面没有入口，只对手改 / 导入生效）。
     *  22 = `KeyDto` 多了 `long_release`（长按之后松手要执行的**另一个**动作；组合那一档没有这一项）。
     *  23 / 24 = `long_release` 收成动作 token（兼容早先写成布尔的那批），并拆出开关 `long_release_on`。
     *  25 = `KeyDto` 多了 `long_fire_on_release`（长按「松手才触发」，与连发 / 松手再执行互斥）。
     *  26 = `GlobalDto` 多了 `toast_template`（执行时提示的文案模板，`$key` / `$gesture` / `$action`）。
     *  27 = `ComboDto` 多了 `long_fire_on_release` / `long_release_on` / `long_release`
     *       （组合键的松手两档：长按松手才触发、松手执行额外动作）；
     *  28 = `GlobalDto` 多了 `same_event`（三档同一事件：三档绑同一个动作的键按下即执行）。
     *       28 这个号在真机上已经以**另一种形状**写过盘（`same_event` 当时还在 `KeyDto` / 键段里），
     *       两种 v28 互不兼容 ⇒ 挪段必须再抬一号，让旧形状走「老文件不校验 md5」那一支；
     *  29 = `same_event` 从 `KeyDto`（键段）挪到 `GlobalDto`（全局段，`same_event` 不再逐键）。
     *  30 = `same_event` 从 `GlobalDto` **挪回** `KeyDto`（每颗键自己一份，按键级优先于公共设置）。
     *       29 那版 `GlobalDto.same_event` 仍留在 DTO 里**只当迁移源**读（`ConfigDto.toConfig` 拿它
     *       灌给每一颗键），但不再写出 —— 所以两种形状的正文必然不同，必须抬号让 29 的文件走
     *       「老文件不校验 md5」那一支。
     *  31 = `KeyDto` / `ComboDto` 各多了 **「按键单独设置」那五项**（`use_own_settings` 与
     *       接口 / 振动 / 振动强度 / 提示 / 提示文案）—— 单键与组合共用 `OwnSettings` 契约。
     *       又一个新字段批次 ⇒ 同一条规矩：不抬号，每一份已有配置都会被判成读不懂。
     *  32 = `ComboDto` 多了 `passthrough`（这一组自己的「无操作时执行按键命令」，可空 =
     *       跟随公共设置）。组合专属的一项，不进 `use_own_settings` 那一族。
     *  33 = **删掉**「三档同一事件」（`KeyDto.same_event` 与 `GlobalDto.same_event`）——
     *       功能整个移除，两个字段一并消失。老文件里残留的那两个键按未知键忽略
     *       （`version < VERSION` 走「不校验 md5」那一支，`ignoreUnknownKeys` 把它们丢掉）。
     *  34 = `KeyDto` / `ComboDto` 各多了 `own_settings_seeded`（独立设置那五项被公共值播过种没有）。
     *       又一个「加字段忘了抬号」的现场：加完没抬 33 → 那份 11824 字节的 v33 配置当场被判读不懂，
     *       页面顶着「配置读取失败」、卡片报异常 —— 而文件本身好端端的（魔数对、正文完好）。
     *       症状与 v3、`extra_clicks` 那两次**一模一样**，这次记在这里当第三次例子。
     *  35 = `ComboDto` 多了 `window_ms`（这一组自己的「按键之间的等待」，0 = 跟随公共）——
     *       第四次现场：加完字段忘了抬号，存量 v34 配置当场被判读不懂、页面顶着「配置读取失败」。
     *
     *  注意 **往 [ConfigDto] 加字段必须同一次改动里抬这个数**：下面 [digestMatches] 的口径是
     *  「按**当前** DTO 重新编码算 md5」，`encodeDefaults = true` 会把新字段写进重编码结果 ——
     *  不抬版本的话，`version == VERSION` 那一支会拿新 DTO 去校旧文件，**每一份已有配置都当场
     *  被判读不懂**（页面只读、注入侧不接管，表现就是「配置读不出来」）。`extra_clicks` 这一轮
     *  就漏抬过一次，症状与 v3 那次一模一样。
     */
    const val VERSION = 35

    /** 拼十六进制用，见 [md5Hex]。 */
    private const val HEX = "0123456789abcdef"

    /**
     * 唯一的编解码器 —— **写与校必须用同一份配置**，否则「自己写的文件自己读不出来」。
     *
     * `encodeDefaults = true`：文件里写全每一个字段（老那版也是全写），这样 `md5`
     * 覆盖的是完整正文。
     *
     * `ignoreUnknownKeys` 只对 `version < VERSION`（不校验 md5）有意义：当前的 md5 按 DTO 重新编码算，
     * 正文里多出来的键在重编码时会被丢掉 ⇒ 带未知键的当前版本文件必然对不上、判「读不懂」。
     * 所以新增字段必须同时抬 [VERSION]，不要指望未知键做向前 / 向后兼容。
     */
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            ignoreUnknownKeys = true
            // 显式 `null` 按「缺字段」处理（回退 DTO 上的默认值）：手改 / 外部工具写出来的文件里
            //  一个 `"keys": null` 就够让整份配置被判「读不懂」——页面随即只读，用户怎么改都存不进去。
            coerceInputValues = true
        }

    /**
     * 解析配置文本，**读不懂返回 null**（空文本 / 坏 JSON / 类型不对 / 缺键段 / 当前版本的 md5 对不上）。
     *
     * 为什么必须区分「读懂了但缺字段」与「读不懂」：默认值里 `enabled=false`、三个手势全是
     * 「无操作」—— **写方**一旦把这份默认值当成真相再写回去，用户配的东西就被整份替换掉了
     *（真机上发生过「振动开关自己变 false」）。
     */
    fun parseOrNull(text: String): Config? =
        try {
            val dto = json.decodeFromString<ConfigDto>(text)
            when {
                // 老文件（v0..VERSION-1）：md5 口径按当前 DTO 算，老文件重编码必然不同 —— 不校验
                dto.version < VERSION -> {
                    if (dto.isStructurallySound()) dto.toConfig() else null
                }

                !digestMatches(dto) -> {
                    // 把版本一起报出来：md5 对不上有两种来路，报出版本才能立刻分辨 ——
                    //  · `dto.version < VERSION`：压根进不来这里（上面那条拦住了），所以只要走到这一支，
                    //    版本就一定是**相等**的。相等还对不上，只可能是这两件之一：
                    //      ① 正文真被改过（手改 / 写到一半被杀 / 编码变了）；
                    //      ② **加了 DTO 字段却没抬 VERSION** —— 校验口径是「拿当前 DTO 重新编码再算 md5」，
                    //         多一个键，重编码的字节就与旧文件不同，于是存量配置整批判死。
                    //    ② 的现场看起来像「文件坏了」，排查方向会被带到文件系统 / 权限 / 编码上去
                    //    （真机上就这么绕过一次），所以日志直接把两个假设都摆出来。
                    XLog.w(
                        "配置正文与 md5 对不上（version=${dto.version} / 本版 $VERSION）—— 判为读不懂。" +
                            "两者相等 ⇒ 要么正文被改过，要么给 DTO 加了字段却忘了抬 VERSION（见 ConfigDto 注释）",
                    )
                    null
                }

                !dto.isStructurallySound() -> {
                    null
                }

                else -> {
                    dto.toConfig()
                }
            }
        } catch (e: Throwable) {
            // 只给 d：宿主每条按键前都要读一遍，这条会成为噪声；判据看调用方（页面那条 w）
            XLog.d("配置解析失败（读不懂）：${e.message}")
            null
        }

    /**
     * 序列化成 JSON 文本，缩进两格、末位带上 `md5`。
     *
     * 顺序是**先算再写**：把不含 `md5` 的那份编码出来算 MD5，再把这一格填上重新编码 ——
     * 于是它不覆盖它自己，校验时才有得比（[digestMatches]）。
     *
     * **写出去的是规范化之后的形态**（见 [canonical]）：map 段落按键名排序。这样文件本身
     * 有一个确定的书写顺序，手改时调换两行也不会改变摘要。
     */
    fun serialize(config: Config): String {
        val body = canonical(ConfigDto.from(config, VERSION, md5 = ""))
        val digest = md5Hex(json.encodeToString(body))
        return json.encodeToString(body.copy(md5 = digest))
    }

    /**
     * 摘要用的**规范形态**：把 map 段落按键名排序，其余保持原样。
     *
     *  为什么要有：`keys` / `app_names` 是 `Map`，序列化顺序等于解析顺序（= 文件里的书写顺序）。
     *  摘要却是在**解析后再编码**的字节上算的 —— 于是用户把两行换个位置（内容一字未改）就会
     *  整份判成「读不懂」，整页变只读。排序之后，书写顺序不再是内容的一部分。
     */
    private fun canonical(dto: ConfigDto): ConfigDto =
        dto.copy(
            keys = dto.keys.toSortedMap(),
            appNames = dto.appNames.toSortedMap(),
        )

    /**
     * 「这份配置够不够格算一份配置」—— 缺键段 / 自定义键少了条目都算读不懂。
     *
     * 缺 `keys` 段（`{"keys":{}}` 配一份自洽的 md5 就是这种）意味着文件被裁剪过或被别的写法生成的：
     * 按默认值补等于静默把主开关 / 三个绑定 / 振动全抹成默认，比判「读不懂」危险得多。
     */
    private fun ConfigDto.isStructurallySound(): Boolean =
        keys.size >= KeyId.builtins.size &&
            KeyId.builtins.all { keys.containsKey(it.id) } &&
            customKeys.all { keys.containsKey(it.id) }

    /** 当前版本的完整性校验：把 `md5` 置空重新编码，与文件里那一格比。 */
    private fun digestMatches(dto: ConfigDto): Boolean {
        if (dto.md5.isEmpty()) return false
        //  规范化形态（新写出去的文件）与原始顺序（本版之前写的文件）都认：
        //  只认前者会让升级那一刻所有既有配置都判成「读不懂」；只认后者则永远修不掉
        //  「换个行序就失效」这个毛病。
        val body = dto.copy(md5 = "")
        return md5Hex(json.encodeToString(canonical(body))) == dto.md5 ||
            md5Hex(json.encodeToString(body)) == dto.md5
    }

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
