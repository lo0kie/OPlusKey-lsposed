package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 「导入配置 / 导出配置」的文件格式与解析，全项目只有这一处。
 *
 * 磁盘上是**混淆过的**（[FileObfuscation]，魔数 [MAGIC]）：首行是魔数，之后是正文经两层变换
 * （异或 + Base64，再按一张替身字母表换写）压成的一行。为什么这么做、怎么手动解开，
 * 见 `FileObfuscation` 的类注释。
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
 * 不存在「导入解析与运行解析两把尺子」。配置那段里的 `md5`（[ConfigCodec]）跟着一起进来、
 * 也由 `ConfigCodec.parseOrNull` 校验。
 *
 * 注意：**主开关状态与「已确认」不随文件走** —— 导出的那份把这两样一律写成「关 / 未确认」，
 * 导入时这里再归零一次（文件可能被人手改过）。它们是「这台机器上你本人的决定」，一份配置文件不该
 * 替别人做这个决定，也不该让人靠导入绕开「启用之前先确认」那一步。
 *
 * 但**本机已经确认过**的那两位不会被一次导入抹掉：`ConfigTransfer.applyImportedConfig` 在写盘前
 * 把它们换回本机此刻的值（用户口径「已经是确认免责协议就不需要再清除这个状态了」）。
 * 这里仍然输出 false —— 文件本身不该带走「别人已确认」这件事。
 *
 * 但**逐键的开关不在此列**：导出与导入一律把它们写成开着（见 [withShareableKeys]）——
 * 否则导回来的键全是关的，而那几个开关藏在 ⋮ 菜单里，用户不会想到去那儿找。
 *
 * 注意：**导入是全量校验**：整份文件（混淆层 + 信封 + 版本 + 两段）都过关才落盘，任何一处不过就一个字节都不写
 * —— 半份配置比没导入更难收拾（见 [parse]）。
 *
 * 注意：**界面状态（`UiState`）不进这个包**：它不是用户配置，落在自己那份文件里（见 `UiState` 的类注释；
 * 导出它等于把「这台机器上怎么看列表」这种本地偏好带到别的机器上）。
 */
object ConfigBundle {
    /** 信封版本 —— 目前只有 1；将来若真要改结构，靠它认旧文件（版本对不上直接拒收）。 */
    private const val VERSION = 1

    /**
     * 导出 / 导入文件的首行：固定前缀 + 信封版本。
     *
     * 一物两用：认得出「这是本模块导出的文件」（挡住随手挑个文件就导入），
     * 也是 [parse] 找解混淆入口的钥匙。
     */
    private const val MAGIC = "OPlusKey-Config-$VERSION"

    // 信封自己的三个键。与 `CommandLibrary.LIB_VERSION` / `ConfigCodec.VERSION` **同字面量
    //  不同义**（信封版本 / 命令库版本 / 主配置版本各一个），所以名字带 `ENVELOPE` 区分。
    private const val ENVELOPE_VERSION = "version"
    private const val ENVELOPE_CONFIG = "config"
    private const val ENVELOPE_COMMANDS = "commands"

    /** 信封的编解码器 —— 与另外两处同一个口径（缩进两格、只写给出的字段）。 */
    private val bundleJson =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    /** 解析结果 —— **两段都一定有值**（少一段就当整份不过关，见 [parse]）。 */
    data class Parsed(
        val config: Config,
        val commands: List<Command>,
    )

    /** 把主配置 + 命令库合成一份导出文本：先拼 JSON 信封，再套一层外皮（[FileObfuscation]）。 */
    fun export(
        config: Config,
        commands: List<Command>,
    ): String =
        try {
            // 落进文件的那份：主开关写关、确认记录写未确认（理由见类注释）——
            //  在**写出去之前**就剥掉，别人拿到手再怎么改也改不出「一导入就是已启用」。
            val shareable =
                config.withShareableKeys().copy(
                    disclaimerAccepted = false,
                    commandDisclaimerAccepted = false,
                )
            val json =
                bundleJson.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put(ENVELOPE_VERSION, VERSION)
                        // 两份序列化结果都是 JSON 文本，原样嵌进来（不再拆一遍字段，避免两处维护同一份键名）
                        put(ENVELOPE_CONFIG, bundleJson.parseToJsonElement(ConfigCodec.serialize(shareable)))
                        put(ENVELOPE_COMMANDS, bundleJson.parseToJsonElement(CommandLibrary.serialize(commands)))
                    },
                )
            FileObfuscation.wrap(MAGIC, json)
        } catch (e: Throwable) {
            // 序列化失败是「模块自己的问题」，不该让页面崩；调用方拿到空串会按「导出失败」处理
            XLog.w("导出配置：合成失败：${e.message}")
            ""
        }

    /**
     * 解析并**全量校验**导入的文本。
     *
     * 注意：**只有整份都过关才返回 [Parsed]**：认得出信封、版本认识、主配置与命令库两段都解析成功
     * —— 任何一条不过就返回 `null`，调用方据此**一个字节都不写**。
     * 半份配置比没导入更难收拾：绑定指向不存在的命令、开关与动作对不上，用户还以为导进去了。
     *
     * @return 全部通过时返回 [Parsed]；否则 `null`（调用方只报一句「未知的配置格式，请检查后重试」，
     *   具体哪一段不过关看日志）。
     */
    fun parse(text: String): Parsed? {
        // 先过外皮：**认不出魔数就拒** —— 否则「随手挑个 JSON 文件来导入」会一路过到底
        val json = FileObfuscation.unwrap(MAGIC, text) ?: return null
        val root =
            try {
                bundleJson.parseToJsonElement(json).jsonObject
            } catch (e: Throwable) {
                XLog.w("导入配置：解开之后不是 JSON：${e.message}")
                return null
            }
        // 版本先认：信封结构将来变了，旧版程序读新版文件是「不知道自己在读什么」，一律拒
        val version = (root[ENVELOPE_VERSION] as? JsonPrimitive)?.intOrNull ?: 0
        if (version != VERSION) {
            XLog.w("导入配置：信封版本 $version ≠ 本版 $VERSION，拒收")
            return null
        }

        //  `as? JsonObject` 而不是 `.jsonObject`：后者在「这一段不是对象」（手改过的导入文件
        //  把 config 写成字符串 / 数组）时抛 IllegalArgumentException，而本函数的约定是
        //  「任何一条不过就返回 null、调用方一个字节都不写」—— 异常漏到调用方就破坏了这个约定。
        val configObj = root[ENVELOPE_CONFIG] as? JsonObject
        if (configObj == null) {
            XLog.w("导入配置：没有 $ENVELOPE_CONFIG 段")
            return null
        }
        val config = ConfigCodec.parseOrNull(configObj.toString())
        if (config == null) {
            XLog.w("导入配置：主配置那段读不懂")
            return null
        }

        val commandsObj = root[ENVELOPE_COMMANDS] as? JsonObject
        if (commandsObj == null) {
            XLog.w("导入配置：没有 $ENVELOPE_COMMANDS 段")
            return null
        }
        // 用 parseOrNull：**空表是合法值**（用户的命令库本来就可以是空的），
        // 只有「读不懂」才是 null —— 不能把读不懂当成空表拿去覆盖
        val commands = CommandLibrary.parseOrNull(commandsObj.toString())
        if (commands == null) {
            XLog.w("导入配置：命令库那段读不懂（有条目认不出）")
            return null
        }

        // 导入进来的那份**不接受**主开关状态与「已确认」：我们导出的文件本来就不带，
        //  但这份文件也可能被人手改过 —— 所以在这里再强制归零一次。导入之后必须自己去开、
        //  自己再确认一遍（那一步会弹风险提示，见 `ui/screen/HomeScreen`）。
        //  自定义键那几颗相反：它们没有独立开关，一律置开（见 [withShareableKeys]）。
        return Parsed(
            config.withShareableKeys().copy(
                disclaimerAccepted = false,
                commandDisclaimerAccepted = false,
            ),
            commands,
        )
    }

    /**
     * 导出 / 导入时对开关的处理 —— **只关总闸，每一颗键一律开着**。
     *
     *  总闸现在是一位**独立字段**（`GlobalConfig.enabled`），不再是「侧键那份 `enabled`」：
     *  拆开之前它俩共用一个位，于是这里只能按「自定义键留着、侧键那一位写关」来凑出「别替我
     *  打开模块」的意思 —— 代价是导出的文件里侧键那颗看着是「停用」的。
     *
     * ## 为什么每一颗键都要一律开
     *
     *  文件可能被人手改过（外层混淆只是防误开，不是防篡改）。逐键的 `enabled` 原值是 `false`
     *  的话，导进来就是一堆看得见、用不了的键（早先对自定义键就踩过：导回来的键全是关的、
     *  绑定页整片灰，而界面上没有开关能打开它们）。强制开才不会把用户卡死在这一格；
     *  真要停用哪一颗，导入之后再从 ⋮ 菜单里关就是了。
     */
    private fun Config.withShareableKeys(): Config =
        copy(
            global = global.copy(enabled = false),
            keys = keys.mapValues { (_, key) -> key.copy(enabled = true) },
        )
}
