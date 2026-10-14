package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config
import java.util.Base64
import org.json.JSONObject

/**
 * 「导入配置 / 导出配置」的文件格式与解析，全项目只有这一处。
 *
 * 磁盘上是**混淆过的一层**（[obfuscate]）：首行是魔数 [MAGIC]，之后是下面那份 JSON 的
 * Base64、且 Base64 之前先与固定密钥异或：
 *
 *     OPlusKey-Config-1
 *     <76 列折行的 Base64>
 *
 *  这一步**不是加密**，只是「别一眼看懂、别顺手手改」—— 密钥写在代码里、公开也无妨，
 * 它的对手是「好奇的眼睛」而不是「想破解的人」（要真保密得换成真加密 + 密钥管理）。
 *
 * 解开之后是一层 **JSON 信封**，里面套两份**现成的**序列化结果 —— 不另造文本格式：
 *
 *     {
 *       "version": 1,
 *       "config":   { … [ConfigCodec] 那一份 … },
 *       "commands": { … [CommandLibrary] 那一份 … }
 *     }
 *
 * 这样两段各自沿用**原有编解码器**：键名、默认值、容错口径与文件里那份完全一致，
 * 不存在「导入解析与运行解析两把尺子」。
 *
 * ⚠️ **导入是全量校验**：整份文件（混淆层 + 信封 + 版本 + 两段）都过关才落盘，任何一处不过就一个字节都不写
 * —— 半份配置比没导入更难收拾（见 [parse]）。
 *
 * ⚠️ **界面状态（`UiState`）不进这个包**：它不是用户配置，落在自己那份文件里（见 `UiState` 的类注释、
 * 导出它等于把「这台机器上怎么看列表」这种本地偏好带到别的机器上。
 */
internal object ConfigBundle {

    /** 信封版本 —— 目前只有 1；将来若真要改结构，靠它认旧文件（版本对不上直接拒收）。 */
    private const val VERSION = 1

    /**
     * 导出文本的**首行**：固定前缀 + 信封版本。
     *
     * 一物两用：认得出「这是本模块导出的文件」（挡住随手挑个文件就导入），
     * 也是 [parse] 找解混淆入口的钥匙。
     */
    private const val MAGIC = "OPlusKey-Config-$VERSION"

    /** 混淆用的固定密钥（异或，循环使用）—— 写死在代码里，公开也无妨（见类注释）。 */
    private val MASK = "OPlusKey-Config-Obfuscation".toByteArray(Charsets.UTF_8)

    private const val FIELD_VERSION = "version"
    private const val FIELD_CONFIG = "config"
    private const val FIELD_COMMANDS = "commands"

    /** 解析结果 —— **两段都一定有值**（少一段就当整份不过关，见 [parse]）。 */
    data class Parsed(val config: Config, val commands: List<Command>)

    /** 把主配置 + 命令库合成一份导出文本：先拼 JSON 信封，再套一层 [obfuscate]。 */
    fun export(config: Config, commands: List<Command>): String = try {
        val json = JSONObject()
            .put(FIELD_VERSION, VERSION)
            // 两份序列化结果都是 JSON 文本，原样嵌进来（不再拆一遍字段，避免两处维护同一份键名）
            .put(FIELD_CONFIG, JSONObject(ConfigCodec.serialize(config)))
            .put(FIELD_COMMANDS, JSONObject(CommandLibrary.serialize(commands)))
            .toString(2)
        obfuscate(json)
    } catch (e: Throwable) {
        // 序列化失败是「模块自己的问题」，不该让页面崩；调用方拿到空串会按「导出失败」处理
        XLog.w("导出配置：合成失败：${e.message}")
        ""
    }

    /**
     * 解析并**全量校验**导入的文本。
     *
     * ⚠️ **只有整份都过关才返回 [Parsed]**：认得出信封、版本认识、主配置与命令库两段都解析成功
     * —— 任何一条不过就返回 `null`，调用方据此**一个字节都不写**。
     * 半份配置比没导入更难收拾：绑定指向不存在的命令、开关与动作对不上，用户还以为导进去了。
     *
     * @return 全部通过时返回 [Parsed]；否则 `null`（调用方只报一句「未知的配置格式，请检查后重试」，
     *   具体哪一段不过关看日志）。
     */
    fun parse(text: String): Parsed? {
        // 先过混淆层：认不出魔数（不是本模块导出的文件）/ 解不开，都在这一步拒掉
        val json = deobfuscate(text) ?: return null
        val root = try {
            JSONObject(json)
        } catch (e: Throwable) {
            XLog.w("导入配置：解开之后不是 JSON：${e.message}")
            return null
        }
        // 版本先认：信封结构将来变了，旧版程序读新版文件是「不知道自己在读什么」，一律拒
        val version = root.optInt(FIELD_VERSION, 0)
        if (version != VERSION) {
            XLog.w("导入配置：信封版本 $version ≠ 本版 $VERSION，拒收")
            return null
        }

        val configObj = root.optJSONObject(FIELD_CONFIG)
        if (configObj == null) {
            XLog.w("导入配置：没有 $FIELD_CONFIG 段")
            return null
        }
        val config = ConfigCodec.parseOrNull(configObj.toString())
        if (config == null) {
            XLog.w("导入配置：主配置那段读不懂")
            return null
        }

        val commandsObj = root.optJSONObject(FIELD_COMMANDS)
        if (commandsObj == null) {
            XLog.w("导入配置：没有 $FIELD_COMMANDS 段")
            return null
        }
        // 用 parseOrNull：**空表是合法值**（用户的命令库本来就可以是空的），
        // 只有「读不懂」才是 null —— 不能把读不懂当成空表拿去覆盖
        val commands = CommandLibrary.parseOrNull(commandsObj.toString())
        if (commands == null) {
            XLog.w("导入配置：命令库那段读不懂（有条目认不出）")
            return null
        }

        return Parsed(config, commands)
    }

    /**
     * 把信封 JSON 揉成导出文本（见类注释）—— 顺序是「先异或、再 Base64」。
     *
     *  光 Base64 随手一解就是明文；异或之后至少得先知道这里做了什么。折行 76 列：
     * 一是 Base64 的常规写法，二是让文件看起来就是「一段编码过的数据」而不是一行天书。
     */
    private fun obfuscate(json: String): String {
        val raw = json.toByteArray(Charsets.UTF_8)
        val masked = ByteArray(raw.size) { i ->
            (raw[i].toInt() xor MASK[i % MASK.size].toInt()).toByte()
        }
        val body = Base64.getMimeEncoder(76, "\n".toByteArray()).encodeToString(masked)
        return "$MAGIC\n$body\n"
    }

    /**
     * [obfuscate] 的逆。**认不出首行魔数就返回 `null`**（不是本模块导出的文件），解不开也返回 `null`。
     *
     *  先剥 BOM：文件被第三方编辑器另存一次就会多出 BOM，不剥的话首行匹配不上、
     * 整份配置会被判成「格式读不懂」（`ConfigStore.readText` 对配置文件也是这么办的）。
     */
    private fun deobfuscate(text: String): String? {
        val lines = text.removePrefix("\uFEFF").trim().lines()
        if (lines.firstOrNull()?.trim() != MAGIC) {
            XLog.w("导入配置：不是本模块导出的配置（首行不是 $MAGIC）")
            return null
        }
        return try {
            // MIME 解码器会忽略换行与首尾空白 —— 上面折的行它自己认得回来
            val masked = Base64.getMimeDecoder().decode(lines.drop(1).joinToString(""))
            val raw = ByteArray(masked.size) { i ->
                (masked[i].toInt() xor MASK[i % MASK.size].toInt()).toByte()
            }
            String(raw, Charsets.UTF_8)
        } catch (e: Throwable) {
            XLog.w("导入配置：混淆层解不开：${e.message}")
            null
        }
    }
}
