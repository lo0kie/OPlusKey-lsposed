// **v98 及更早的落盘格式，只读。**
//
//  这一份是改造前 `ConfigDto.kt` 里那三个类的冻结副本：逐档字段、独立设置十格、成员连击三十格。
//  它只做一件事 —— 把老文件读成 `Config`（`LegacyConfigDto.toConfig()`），读出之后不再参与任何
//  写入。新格式只有 `actions` + `params` 两层（见 `ConfigDto`），所以这里保留下来的字段名**就是
//  历史契约**，一个都不许改、也不许再往里加新字段。
//
//  为什么整份冻结而不是「逐字段读 JSON」：读的一侧与写的一侧必须成对，而写的那一侧已经没了。
//  留一份完整的旧类型，`toConfig()` 里那些 `clamp` / `clampOrFollow` / `mergeScenes` 的口径
//  就还是原样一份，不会因为「照着老代码手抄一遍」而抄错。

package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.ConfigFields
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.trigger
import com.lookie.opluskey.core.param.ComboParams
import com.lookie.opluskey.core.param.Params
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * 配置文件的**线格式模型**（`@Serializable`）—— 与 `Config` 那套数据类分开。
 *
 * ## 为什么不直接给 `Config` 加 `@Serializable`
 *
 *  · `Config.keys` 是 `Map<KeyId, KeyConfig>`，而 [KeyId] 有私有构造、按 `id` 判等 ——
 *    要序列化就得给它写一个自定义 `KSerializer`，把这个「身份类型」的细节摊到线格式层；
 *  · 线格式要**稳定**（用户机器上的文件），而 `Config` 是会跟着功能演进的领域模型。
 *    两者合一的话，以后往 `Config` 加字段就等于改文件格式。
 *
 * 所以这里只描述「文件里长什么样」，转换在两边的 `toConfig` / `from` 里。
 *
 * ## 默认值
 *
 *  每个字段都给了默认值 ⇒ **缺失字段不再需要手工兜底**（这正是换掉 org.json 的动机：
 *  `optBoolean` 在键缺失时返回 false、`optString` 遇到 `null` 字面量返回字符串 `"null"`，
 *  那两个坑在这里都不存在）。
 *
 *  但**取值范围不在这里钳**：上下限是 [ConfigFields] 那张表的事（表里 `Kind.Number(min, max)`），
 *  转换时按表钳 —— 一处定义，广播与落盘两条通道共用同一套范围。
 *
 *  ## 键名的真源就是这里
 *
 *  属性名（或 `@SerialName`）**就是**文件里的键。`ConfigFields` 不再登记 JSON 那一列（它没有代码读，
 *  改 `@SerialName` 时会静默分叉）；所以改这里的任何一个键名 / 加字段都等于**改文件格式** ——
 *  老文件只能靠默认值兜底，不能靠改名兼容。
 *
 *  ## 加 / 挪字段必须同时抬 `ConfigCodec.VERSION`
 *
 *  当前版本的 md5 是「按**这个 DTO** 重新编码」算的（`encodeDefaults = true` ⇒ 新字段会进正文），
 *  所以只加字段不抬版本 ⇒ **每一份现有文件**重编码后都对不上、全被判「读不懂」，
 *  页面立刻顶着「配置读取失败」、整页只读。这个坑踩过两次（v2→v3 搬单击延迟、v3→v4 拆总闸）。
 */
@Serializable
internal data class LegacyConfigDto(
    val version: Int = 0,
    val keys: Map<String, LegacyKeyDto> = emptyMap(),
    @SerialName("custom_keys") val customKeys: List<CustomKeyDto> = emptyList(),
    val global: GlobalDto = GlobalDto(),
    val disclaimerAccepted: Boolean = false,
    val commandDisclaimerAccepted: Boolean = false,
    @SerialName("app_names") val appNames: Map<String, String> = emptyMap(),
    /** 组合按键（`Config.combos`）—— 与 `app_names` 同一套：单独一段，只走落盘。 */
    val combos: List<LegacyComboDto> = emptyList(),
    /** 这份配置的唯一标识（`Config.profileId`）；空串 = 老文件，读的时候按规则补。 */
    @SerialName("profile_id") val profileId: String = "",
    /** 这份配置的名字（`Config.profileName`）；空串 = 还没起名字（**不等于**「默认」那份）。 */
    @SerialName("profile_name") val profileName: String = "",
    /** 这份配置在列表里的位次（`Config.profileOrder`）；0 = 「默认」那份。 */
    @SerialName("profile_order") val profileOrder: Int = 0,
    /** 正文完整性字段；只有 `version == ConfigCodec.VERSION` 时才校验（见 `ConfigCodec`）。 */
    val md5: String = "",
) {
    /** 线格式 → 领域模型。**字段映射只有这一处**。 */
    fun toConfig(): Config {
        val custom = customKeys.mapNotNull { it.toKeyId() }.distinctBy { it.id }
        if (custom.size != customKeys.size) {
            //  手改 / 导入的文件里一条坏条目会让整颗键连同它的绑定一起消失 —— 至少要留一行账
            XLog.w("配置：${customKeys.size - custom.size} 个自定义键认不出（已丢弃）")
        }
        val allKeys = (KeyId.builtins + custom).distinctBy { it.id }
        //  `keys` 里有、但 `allKeys` 里没有的条目（自造的 id、或某一颗内置键在本版被删掉了）
        //  会**整个丢掉** —— 与上面自定义键、下面组合键同一条纪律：丢东西要留一行账，
        //  否则用户只会看到「那颗键的绑定没了」，找不到是哪儿丢的
        val known = allKeys.mapTo(HashSet()) { it.id }
        val orphans = keys.keys.filterNot { it in known }
        if (orphans.isNotEmpty()) {
            XLog.w("配置：${orphans.size} 个按键条目认不出（已丢弃）：${orphans.joinToString()}")
        }
        //  **额外档位的丢弃也在这里记账**（真正的丢弃在 [LegacyKeyDto.toKeyConfig] 的 `mapNotNull` 里）：
        //  连击次数不是「≥ 3 的整数」的条目会整条消失，而用户只会看到「第 3 击那一档没了」。
        //  按**整份文件**聚合一次计数，不逐条刷。
        val droppedClicks = keys.values.sumOf { key -> key.extraClicks.keys.count { extraClickCount(it) == null } }
        if (droppedClicks > 0) {
            XLog.w("配置：$droppedClicks 条额外档位认不出（已丢弃）")
        }
        // 老文件把「单击延迟」放在 `global` 里：逐键字段缺失时拿它兜底（迁移用）
        val legacyDouble = global.doubleClickMs ?: KeyConfig.DEFAULT_DOUBLE_CLICK_MS
        //  —— 总闸的迁移 ——
        //  拆开之前没有 `global.enabled`：那时「主开关」就是 `keys.plus.enabled`，侧键那一份
        //  同时当「总闸」与「这一颗键」两个用。老文件里那一位要读成**总闸**（用户当时的意图），
        //  而「这一颗键」迁移成开着 —— 否则升级后用户打开总闸，侧键仍然是关的
        //  （表现是「开了没反应」，还得去 ⋮ 菜单里再开一次）。
        val masterFromFile = global.enabled
        val legacyMaster = masterFromFile == null
        val master = masterFromFile ?: keys[KeyId.PLUS.id]?.enabled ?: false
        val keyConfigs =
            allKeys.associateWith { id ->
                val key = (keys[id.id] ?: LegacyKeyDto()).toKeyConfig(id, legacyDouble)
                if (legacyMaster && id.id == KeyId.PLUS.id) key.copy(enabled = true) else key
            }
        //  「常用命令」清单不进字段表（它不参与广播，注入侧不认识它）——
        //  与 `appNames` / `combos` 同一条，只走落盘这一列。
        //  **v63 的语义迁移**：「提示走流体云」从「可选增强、默认关」改成「ColorOS 接口模式下的
        //  默认落点」（用户口径「使用 ColorOS 接口时应该弹流体云」）。老文件里存着的 `false`
        //  是**旧默认**而不是用户的选择，所以就地折成开；这一版之后关掉的才算数（读到的版本
        //  已经是 63，不再改）。
        val globalConfig =
            GlobalConfig
                .ofFields(global.toValues())
                .copy(
                    commonCommands = global.commonCommands.parsed(),
                    toastFluid =
                        if (version < ConfigCodec.VERSION_FLUID_DEFAULT) true else global.toastFluid,
                )
        val parsedCombos = combos.mapNotNull { it.toCombo() }
        if (parsedCombos.size != combos.size) {
            //  与自定义键同一条：坏条目不能无声消失（组合键丢了 = 那一组按键整组失效）
            XLog.w("配置：${combos.size - parsedCombos.size} 个组合键认不出（已丢弃）")
        }
        return Config
            .ofFields(
                keys = keyConfigs,
                global = if (legacyMaster) globalConfig.copy(enabled = master) else globalConfig,
                v =
                    ConfigFields.Values(
                        mapOf(
                            ConfigFields.F_DISCLAIMER to disclaimerAccepted,
                            ConfigFields.F_COMMAND_DISCLAIMER to commandDisclaimerAccepted,
                        ),
                    ),
                customKeys = custom,
                appNames = appNames,
                combos = parsedCombos,
            )
            //  名字与位次都不进 `ConfigFields`（那不是「一按就生效」的东西，也不需要广播给注入侧），
            //  只走落盘这一列 —— 与 `appNames` / `combos` 同一条。
            .copy(profileId = profileId, profileName = profileName, profileOrder = profileOrder)
    }

    companion object {
        /** 领域模型 → 线格式。 */
        fun from(
            config: Config,
            version: Int,
            md5: String,
        ): LegacyConfigDto =
            LegacyConfigDto(
                version = version,
                keys = config.allKeys.associate { it.id to LegacyKeyDto.from(config.key(it)) },
                customKeys = config.customKeys.map { CustomKeyDto(it.id, it.label, it.keyCode) },
                global = GlobalDto.from(config.global),
                disclaimerAccepted = config.disclaimerAccepted,
                commandDisclaimerAccepted = config.commandDisclaimerAccepted,
                appNames = config.appNames,
                combos = config.combos.map { LegacyComboDto.of(it) },
                profileId = config.profileId,
                profileName = config.profileName,
                profileOrder = config.profileOrder,
                md5 = md5,
            )
    }
}

/** 一颗键的绑定。 */
@Serializable
internal data class LegacyKeyDto(
    val enabled: Boolean = false,
    val single: String = Action.NONE,
    val double: String = Action.NONE,
    val long: String = Action.NONE,
    val longPressMs: Int = KeyConfig.DEFAULT_LONG_PRESS_MS,
    val longRepeat: Boolean = false,
    val longRepeatIntervalMs: Int = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
    /** 连续触发时是否每一次重复都振动（关 = 只在长按那一下振一次）。 */
    val longRepeatVibrate: Boolean = false,
    /**
     * 「松手执行额外动作」—— 长按之后松手那一刻要执行的**另一个**动作（动作 token，与长按那档分开）。
     *
     *  老文件没有这一格 ⇒ `none`（无操作）⇒ 行为与这一项出现之前完全一致，不需要迁移。
     *
     *  **类型故意收成 [JsonElement] 而不是 `String`**：这一档中间有过一版把它当**布尔开关**写进过
     *  别人的配置文件（`"longRelease": false`）。字段类型写成 `String` 时，那种文件一读就抛
     *  「Expected quotation mark」⇒ 整份配置判「读不懂」⇒ 页面只读、注入侧不接管（真机踩到）。
     *  收成 [JsonElement] 之后，布尔一律按「未设置」读（见 [releaseToken]），下次写盘自然变成
     *  动作 token。
     */
    val longRelease: JsonElement = JsonPrimitive(Action.NONE),
    /** 「松手执行额外动作」的开关 —— 与动作分开（见 `KeyConfig.longReleaseOn`）。 */
    val longReleaseOn: Boolean = false,
    /** 「松手才触发」—— 长按动作本身等抬手才执行（与连续触发、松手执行额外动作互斥）。 */
    val longFireOnRelease: Boolean = false,
    /**
     * 「单击延迟」—— **每颗键各一份**（以前在 `global` 段里）。
     *
     *  `null` = 这份文件里没写（老文件 / 手写）：读的时候退回 `global` 那份，见 [LegacyConfigDto.toConfig]。
     *  新写出来的文件一定是具体值。
     */
    val doubleClickMs: Int? = null,
    /**
     * 「这颗键只参与非首键的按住组合」—— 单键手势交还系统，模块只用它凑组合（见 `KeyConfig.comboOnly`）。
     *
     *  `null` = 老文件没有这一项 ⇒ 关（与这一位出现之前一致，不需要迁移）。
     */
    @SerialName("combo_only") val comboOnly: Boolean? = null,
    /**
     * 「双击这一档开着没有」（`KeyConfig.doubleClickOn`）。
     *
     *  `null` = 老文件没有这一项 ⇒ **开**（与这一位出现之前一致，不需要迁移）。
     */
    @SerialName("double_click_on") val doubleClickOn: Boolean? = null,
    /** 「多击窗口内逐次等满」—— 到档也等窗口走完再判（`KeyConfig.waitMultiWindow`）。 */
    @SerialName("wait_multi_window") val waitMultiWindow: Boolean? = null,
    /**
     * 「逐次等满」那一档的窗口（`KeyConfig.multiWaitMs`）。
     *
     *  `null`（老文件没有这一项）与写 `0` **同义**：跟随「单击延迟」——
     *  见 `KeyConfig.multiWaitMs` 的 KDoc，回落是为了让升级前后手感一字不差。
     */
    @SerialName("multi_wait_ms") val multiWaitMs: Int? = null,
    /**
     * 「按键单独设置」—— 这一颗键要不要用自己那套公共项（接口 / 振动 / 提示）。
     *
     *  `null` = 老文件没有这一项 ⇒ 跟随公共设置（与新装默认一致，不需要迁移）。
     *  下面四项只在它为 `true` 时算数，所以老文件缺它们也没有影响。
     */
    val useOwnSettings: Boolean? = null,
    /** 这颗键自己的「使用 ColorOS 接口」。 */
    val colorOsApi: Boolean? = null,
    /** 这颗键自己的「振动反馈」/「振动强度」。 */
    val vibrate: Boolean? = null,
    val vibrateStrength: Int? = null,
    /** 这颗键自己的「无操作时振动」。 */
    @SerialName("vibrate_on_none")
    val vibrateOnNone: Boolean? = null,
    /** 这颗键自己的「执行时提示」/「提示走流体云」/「提示走通知」/「提示文案」/「逐条提示」。 */
    val toastOnRun: Boolean? = null,
    val toastFluid: Boolean? = null,
    val toastNotify: Boolean? = null,
    val toastTemplate: String? = null,
    val stepNotice: Boolean? = null,
    /**
     * **老的「一条规则一个手势」那一列** —— 只读：新写的文件不再写它，读到就迁移（见 [mergeScenes]）。
     *
     *  留着它只为把已经存在的配置读上来：删掉这一格的话，老文件里那一整批条件分支会在升级
     *  那一刻凭空消失。
     */
    val rules: List<StateRuleDto> = emptyList(),
    /**
     * **系统场景**（`KeyConfig.scenes`）—— 只走落盘：它是列表，进不了 `ConfigFields` 的字段表。
     *
     *  一块 = 一组条件 + 三档动作（见 `SceneRule`）。老文件没有这一段 ⇒ 空 ⇒ 由 [rules] 迁移出来。
     */
    val scenes: List<SceneRuleDto> = emptyList(),
    /**
     * 第 3 击起的额外档位（`KeyConfig.extraClicks`）—— 键是连击次数、值是动作 token。
     *
     *  与 [rules] 同一套：表结构进不了 `ConfigFields`，所以只走落盘。老文件没有这一段 ⇒ 空表 ⇒
     *  行为与这一项出现之前完全一致。
     */
    @SerialName("extra_clicks") val extraClicks: Map<String, String> = emptyMap(),
    /**
     * **哪几档多击开着**（`KeyConfig.extraClicksOn`）—— 只管 [extraClicks] 那张表里哪几档算数。
     *
     *  写出来是**击数数组**（`[3]` / `[3,4]`）。读的时候认三种历史形态：
     *  · 数组（本版）；
     *  · 布尔（上一版只收成一个开关：`true` = 表里那几档全算）；
     *  · 缺这一格（老文件）⇒ 按「表里有什么」倒推（见 [clicksOn]）。
     *
     *  为什么老文件要倒推：这一格出现之前**手改 `extra_clicks` 是能用的**（判定侧那时直接
     *  按表算最高击数，没有开关这一道）。缺格就判「没有」，升级上来的人会发现三击突然不灵了，
     *  而配置里那几个动作还好端端躺着 —— 最难查的一种。
     *
     *  反过来，**新写出来的文件一定带这一格**（[of] 同步写出）：用户在界面上把它关掉时表还在、
     *  但这一格已经是明确的空数组，不会再被倒推回去。
     */
    @SerialName("extra_clicks_on") val extraClicksOn: JsonElement? = null,
    /**
     * **「单击后长按」这一档启用没有**（`KeyConfig.clickThenLongOn`、[KeyConfig.clickThenLong]）。
     *
     *  动作与开关分两格（与「松手执行额外动作」同一条口径）：开关打开、动作还没挑时是
     *  「开着但没设置」，此时这一档什么都不做；若把「有没有动作」直接当开关，用户打开那一刻
     *  就会执行一个他没挑过的动作。
     *
     *  老文件没有这两格 ⇒ 默认关 + `none`，行为与这一位出现之前完全一致。
     */
    @SerialName("click_then_long_on") val clickThenLongOn: Boolean? = null,
    @SerialName("click_then_long") val clickThenLong: String = Action.NONE,
    /**
     * 「双击后长按」/「三击后长按」—— 与 [clickThenLongOn] / [clickThenLong] 同一对口径，
     *  只差前面那几击的击数。老文件没有这四格 ⇒ 默认关 + `none`，行为与这两档出现之前一致。
     */
    @SerialName("double_then_long_on") val doubleThenLongOn: Boolean? = null,
    @SerialName("double_then_long") val doubleThenLong: String = Action.NONE,
    @SerialName("triple_then_long_on") val tripleThenLongOn: Boolean? = null,
    @SerialName("triple_then_long") val tripleThenLong: String = Action.NONE,
    @SerialName("double_then_long_repeat") val doubleThenLongRepeat: Boolean = false,
    @SerialName("double_then_long_repeat_interval_ms") val doubleThenLongRepeatIntervalMs: Int =
        KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
    @SerialName("double_then_long_repeat_vibrate") val doubleThenLongRepeatVibrate: Boolean = false,
    @SerialName("double_then_long_release_on") val doubleThenLongReleaseOn: Boolean = false,
    @SerialName("double_then_long_release") val doubleThenLongRelease: String = Action.NONE,
    @SerialName("double_then_long_fire_on_release") val doubleThenLongFireOnRelease: Boolean = false,
    @SerialName("double_then_long_is_serial") val doubleThenLongIsSerial: Boolean = false,
    @SerialName("triple_then_long_repeat") val tripleThenLongRepeat: Boolean = false,
    @SerialName("triple_then_long_repeat_interval_ms") val tripleThenLongRepeatIntervalMs: Int =
        KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
    @SerialName("triple_then_long_repeat_vibrate") val tripleThenLongRepeatVibrate: Boolean = false,
    @SerialName("triple_then_long_release_on") val tripleThenLongReleaseOn: Boolean = false,
    @SerialName("triple_then_long_release") val tripleThenLongRelease: String = Action.NONE,
    @SerialName("triple_then_long_fire_on_release") val tripleThenLongFireOnRelease: Boolean = false,
    @SerialName("triple_then_long_is_serial") val tripleThenLongIsSerial: Boolean = false,
    /**
     * 「单击后长按」那一档自己的参数 —— 与长按那几格（`long_press_ms` / `long_repeat` …）
     *  **同名不同槽**：调这一档不会动普通长按。
     *
     *  老文件没有这几格 ⇒ 走默认值（与长按那一档的默认相同），行为只会出现在用户
     *  主动打开这一档之后。
     */
    @SerialName("click_then_long_press_ms") val clickThenLongPressMs: Int? = null,
    @SerialName("click_then_long_repeat") val clickThenLongRepeat: Boolean? = null,
    @SerialName("click_then_long_repeat_interval_ms") val clickThenLongRepeatIntervalMs: Int? = null,
    @SerialName("click_then_long_repeat_vibrate") val clickThenLongRepeatVibrate: Boolean? = null,
    @SerialName("click_then_long_release_on") val clickThenLongReleaseOn: Boolean? = null,
    @SerialName("click_then_long_release") val clickThenLongRelease: String = Action.NONE,
    /**
     * 「单击后长按」的「松手时触发」—— 与长按那一档的 `long_fire_on_release` 同义、各自一格。
     *
     *  老文件没有这一格 ⇒ `null` ⇒ 走 false：与这一位出现之前一致（到点即执行）。
     */
    @SerialName("click_then_long_fire_on_release") val clickThenLongFireOnRelease: Boolean? = null,
) {
    /**
     * [longRelease] 那一格的**动作 token** —— 布尔（上一版把这一档当开关写过）一律当「未设置」。
     *
     *  只认字符串：其他类型（`false` / `null` / 数字）都退回 [Action.NONE]，与「没有这一格」同一个结果。
     */
    private val releaseToken: String
        get() = (longRelease as? JsonPrimitive)?.takeIf { it.isString }?.content ?: Action.NONE

    /**
     * [extraClicksOn] 真正读成什么 —— 三种历史形态收成同一份集合。
     *
     *  「缺这一格」与「上一版的布尔 `true`」都落到**表里配着的那几档**（[tableClicks]）：
     *  那正是这两种配置写下时的语义（表里的东西会执行）。
     */
    private val clicksOn: Set<Int>
        get() =
            when (val raw = extraClicksOn) {
                null -> {
                    tableClicks
                }

                //  上一版写的是布尔：`true` = 表里那几档全算；`false` 或别的状态值一律当没有
                is JsonPrimitive -> {
                    if (raw.booleanOrNull == true) {
                        tableClicks
                    } else {
                        KeyConfig.clicksOnOf(raw.content)
                    }
                }

                is JsonArray -> {
                    KeyConfig.clicksOnOf(raw.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() })
                }

                else -> {
                    emptySet()
                }
            }

    /** [extraClicks] 那张表里认得出的档位（`click3` / `click4` …）—— 老配置倒推用。 */
    private val tableClicks: Set<Int>
        get() = KeyConfig.clicksOnOf(extraClicks.keys.mapNotNull { extraClickCount(it) })

    /** 按 [ConfigFields] 那张表把取值钳进各自的上下限 —— 上下限只定义在表里。 */
    fun toValues(legacyDoubleClickMs: Int): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_ENABLED to enabled,
                ConfigFields.F_SINGLE to single,
                ConfigFields.F_DOUBLE to double,
                ConfigFields.F_LONG to long,
                ConfigFields.F_LONG_PRESS_MS to clamp(ConfigFields.F_LONG_PRESS_MS, longPressMs),
                ConfigFields.F_LONG_REPEAT to longRepeat,
                ConfigFields.F_LONG_RELEASE to releaseToken,
                ConfigFields.F_LONG_RELEASE_ON to longReleaseOn,
                ConfigFields.F_LONG_FIRE_ON_RELEASE to longFireOnRelease,
                ConfigFields.F_REPEAT_INTERVAL_MS to clamp(ConfigFields.F_REPEAT_INTERVAL_MS, longRepeatIntervalMs),
                ConfigFields.F_REPEAT_VIBRATE to longRepeatVibrate,
                ConfigFields.F_DOUBLE_CLICK_MS to
                    clamp(ConfigFields.F_DOUBLE_CLICK_MS, doubleClickMs ?: legacyDoubleClickMs),
                ConfigFields.F_COMBO_ONLY to (comboOnly ?: false),
                ConfigFields.F_DOUBLE_CLICK_ON to (doubleClickOn ?: true),
                ConfigFields.F_WAIT_MULTI_WINDOW to (waitMultiWindow ?: true),
                //  **与上面几档同一条纪律：上下限只定义在 `ConfigFields` 里**。不钳的话，手改出来的
                //  `600000` 会原样成为判定窗口 —— 那颗键此后每按一次都要等十分钟才回落。
                ConfigFields.F_MULTI_WAIT_MS to
                    clamp(ConfigFields.F_MULTI_WAIT_MS, multiWaitMs ?: KeyConfig.FOLLOW_DOUBLE_CLICK_MS),
                ConfigFields.F_OWN_SETTINGS to (useOwnSettings ?: false),
                ConfigFields.F_KEY_COLOR_OS_API to (colorOsApi ?: GlobalConfig.DEFAULT_COLOR_OS_API),
                ConfigFields.F_KEY_VIBRATE to (vibrate ?: false),
                ConfigFields.F_KEY_VIBRATE_STRENGTH to
                    clamp(ConfigFields.F_KEY_VIBRATE_STRENGTH, vibrateStrength ?: GlobalConfig.DEFAULT_VIBRATE_STRENGTH),
                ConfigFields.F_KEY_VIBRATE_ON_NONE to (vibrateOnNone ?: GlobalConfig.DEFAULT_VIBRATE_ON_NONE),
                ConfigFields.F_KEY_TOAST_ON_RUN to (toastOnRun ?: GlobalConfig.DEFAULT_TOAST_ON_RUN),
                ConfigFields.F_KEY_TOAST_FLUID to (toastFluid ?: GlobalConfig.DEFAULT_TOAST_FLUID),
                ConfigFields.F_KEY_TOAST_NOTIFY to (toastNotify ?: GlobalConfig.DEFAULT_TOAST_NOTIFY),
                ConfigFields.F_KEY_TOAST_TEMPLATE to (toastTemplate ?: GlobalConfig.DEFAULT_TOAST_TEMPLATE),
                ConfigFields.F_KEY_STEP_NOTICE to (stepNotice ?: GlobalConfig.DEFAULT_STEP_NOTICE),
                //  「哪几档多击开着」也要走这张表：`toValues` 是两条通道共用的入口，
                //  这里给文本（`Kind` 只有 Bool / Number / Text），与 `fieldValues()` 同一个编码
                ConfigFields.F_EXTRA_CLICKS to clicksOn.sorted().joinToString(","),
                ConfigFields.F_CLICK_THEN_LONG_ON to (clickThenLongOn ?: false),
            ) + ctlValuesMap(),
        )

    /**
     * 「单击后长按」那一族字段（这一档的动作 / 长按延迟 / 连发 / 间隔 / 每次振动 / 松手开关与动作）。
     *
     *  单独一支只为让 [toValues] 的圈复杂度留在 detekt 的线内 —— 这一族有七个 `?:`，
     *  摊在主线上等于给那个函数再加七条分支。
     */
    private fun ctlValuesMap(): Map<ConfigFields.Field, Any> =
        mapOf(
            //  动作那一格也在表里：**落盘与广播读同一个字段**，两处各写一套时
            //  「广播里带、文件里不带」这种半通的状态必然出现（`click_then_long` 就是那样丢过）。
            ConfigFields.F_CTL to clickThenLong,
            ConfigFields.F_DOUBLE_THEN_LONG_ON to (doubleThenLongOn ?: false),
            ConfigFields.F_DOUBLE_THEN_LONG to doubleThenLong,
            ConfigFields.F_TRIPLE_THEN_LONG_ON to (tripleThenLongOn ?: false),
            ConfigFields.F_TRIPLE_THEN_LONG to tripleThenLong,
            ConfigFields.F_DOUBLE_THEN_LONG_REPEAT to doubleThenLongRepeat,
            ConfigFields.F_DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS to
                clamp(ConfigFields.F_DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS, doubleThenLongRepeatIntervalMs),
            ConfigFields.F_DOUBLE_THEN_LONG_REPEAT_VIBRATE to doubleThenLongRepeatVibrate,
            ConfigFields.F_DOUBLE_THEN_LONG_RELEASE_ON to doubleThenLongReleaseOn,
            ConfigFields.F_DOUBLE_THEN_LONG_RELEASE to doubleThenLongRelease,
            ConfigFields.F_DOUBLE_THEN_LONG_FIRE_ON_RELEASE to doubleThenLongFireOnRelease,
            ConfigFields.F_DOUBLE_THEN_LONG_SERIAL to doubleThenLongIsSerial,
            ConfigFields.F_TRIPLE_THEN_LONG_REPEAT to tripleThenLongRepeat,
            ConfigFields.F_TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS to
                clamp(ConfigFields.F_TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS, tripleThenLongRepeatIntervalMs),
            ConfigFields.F_TRIPLE_THEN_LONG_REPEAT_VIBRATE to tripleThenLongRepeatVibrate,
            ConfigFields.F_TRIPLE_THEN_LONG_RELEASE_ON to tripleThenLongReleaseOn,
            ConfigFields.F_TRIPLE_THEN_LONG_RELEASE to tripleThenLongRelease,
            ConfigFields.F_TRIPLE_THEN_LONG_FIRE_ON_RELEASE to tripleThenLongFireOnRelease,
            ConfigFields.F_TRIPLE_THEN_LONG_SERIAL to tripleThenLongIsSerial,
            ConfigFields.F_CTL_PRESS_MS to
                clamp(ConfigFields.F_CTL_PRESS_MS, clickThenLongPressMs ?: KeyConfig.DEFAULT_LONG_PRESS_MS),
            ConfigFields.F_CTL_REPEAT to (clickThenLongRepeat ?: false),
            ConfigFields.F_CTL_REPEAT_INTERVAL_MS to
                clamp(
                    ConfigFields.F_CTL_REPEAT_INTERVAL_MS,
                    clickThenLongRepeatIntervalMs ?: KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                ),
            ConfigFields.F_CTL_REPEAT_VIBRATE to (clickThenLongRepeatVibrate ?: false),
            ConfigFields.F_CTL_RELEASE_ON to (clickThenLongReleaseOn ?: false),
            ConfigFields.F_CTL_RELEASE to clickThenLongRelease,
            ConfigFields.F_CTL_FIRE_ON_RELEASE to (clickThenLongFireOnRelease ?: false),
        )

    /** 线格式 → 一颗键的配置（含状态分支）。几个 `legacy*` 是迁移兜底，见 [LegacyConfigDto.toConfig]。 */
    fun toKeyConfig(
        id: KeyId,
        legacyDoubleClickMs: Int,
    ): KeyConfig =
        KeyConfig
            .ofFields(id, toValues(legacyDoubleClickMs), mergeScenes(scenes, rules))
            .copy(
                //  认不出的次数 / 动作整条丢掉（与 `rules`、`custom_keys` 同一条纪律：丢东西要留账，
                //  账在 `LegacyConfigDto.toConfig` 那一层按整份文件打一次）
                extraClicks =
                    extraClicks
                        .mapNotNull { (clicks, token) ->
                            extraClickCount(clicks)?.let { it to Action.parse(token) }
                        }.toMap(),
                //  只补**那张表**：它是一个字段表装不下的结构（`extraClicks`），别的扁平字段
                //  （含「单击后长按」的动作与它的松手）一律走 `toValues` / `ofFields` 那一对 ——
                //  在这里再抄一遍动作等于给同一条规则第二份实现，漏抄就是「广播里有、文件里没有」。
            )

    companion object {
        fun from(key: KeyConfig): LegacyKeyDto =
            LegacyKeyDto(
                enabled = key.enabled,
                single = key.single.token,
                double = key.double.token,
                long = key.long.token,
                longPressMs = key.longPressMs,
                longRepeat = key.longRepeat,
                longRepeatIntervalMs = key.longRepeatIntervalMs,
                longRepeatVibrate = key.longRepeatVibrate,
                longRelease = JsonPrimitive(key.longRelease.token),
                longReleaseOn = key.longReleaseOn,
                longFireOnRelease = key.longFireOnRelease,
                doubleClickMs = key.doubleClickMs,
                comboOnly = key.comboOnly,
                doubleClickOn = key.doubleClickOn,
                waitMultiWindow = key.waitMultiWindow,
                multiWaitMs = key.multiWaitMs,
                useOwnSettings = key.useOwnSettings,
                colorOsApi = key.colorOsApi,
                vibrate = key.vibrate,
                vibrateStrength = key.vibrateStrength,
                vibrateOnNone = key.vibrateOnNone,
                toastOnRun = key.toastOnRun,
                toastFluid = key.toastFluid,
                toastNotify = key.toastNotify,
                toastTemplate = key.toastTemplate,
                stepNotice = key.stepNotice,
                scenes = key.scenes.map { SceneRuleDto.of(it) },
                extraClicks = key.extraClicks.mapKeys { it.key.toString() }.mapValues { it.value.token },
                //  写侧一律给具体值：给 `null` 的话下次读回来会走「按表倒推」那条老路
                extraClicksOn = JsonArray(key.extraClicksOn.sorted().map { JsonPrimitive(it) }),
                clickThenLongOn = key.clickThenLongOn,
                clickThenLong = key.clickThenLong.token,
                doubleThenLongOn = key.doubleThenLongOn,
                doubleThenLong = key.doubleThenLong.token,
                tripleThenLongOn = key.tripleThenLongOn,
                tripleThenLong = key.tripleThenLong.token,
                doubleThenLongRepeat = key.doubleThenLongRepeat,
                doubleThenLongRepeatIntervalMs = key.doubleThenLongRepeatIntervalMs,
                doubleThenLongRepeatVibrate = key.doubleThenLongRepeatVibrate,
                doubleThenLongReleaseOn = key.doubleThenLongReleaseOn,
                doubleThenLongRelease = key.doubleThenLongRelease.token,
                doubleThenLongFireOnRelease = key.doubleThenLongFireOnRelease,
                doubleThenLongIsSerial = key.doubleThenLongIsSerial,
                tripleThenLongRepeat = key.tripleThenLongRepeat,
                tripleThenLongRepeatIntervalMs = key.tripleThenLongRepeatIntervalMs,
                tripleThenLongRepeatVibrate = key.tripleThenLongRepeatVibrate,
                tripleThenLongReleaseOn = key.tripleThenLongReleaseOn,
                tripleThenLongRelease = key.tripleThenLongRelease.token,
                tripleThenLongFireOnRelease = key.tripleThenLongFireOnRelease,
                tripleThenLongIsSerial = key.tripleThenLongIsSerial,
                clickThenLongPressMs = key.clickThenLongPressMs,
                clickThenLongRepeat = key.clickThenLongRepeat,
                clickThenLongRepeatIntervalMs = key.clickThenLongRepeatIntervalMs,
                clickThenLongRepeatVibrate = key.clickThenLongRepeatVibrate,
                clickThenLongReleaseOn = key.clickThenLongReleaseOn,
                clickThenLongRelease = key.clickThenLongRelease.token,
                clickThenLongFireOnRelease = key.clickThenLongFireOnRelease,
            )
    }
}

/**
 * **老的**「一条规则一个手势」那一项 —— **只读**：新写的文件不再写这一列（见 [LegacyKeyDto.scenes]）。
 *
 *  留着它只为把已经存在的配置读上来：[mergeLegacy] 会把「条件完全相同的那几条」并成一块。
 *  删掉这一格的话，老文件里那一整批条件分支会在升级那一刻凭空消失。
 *
 *  两格条件字段：老文件只有 [condition]（单条），后一版的文件还写了 [conditions]（全量）。
 *  读的时候优先看 [conditions]，它空着才回落到 [condition]。
 */
@Serializable
internal data class LegacyComboDto(
    val id: String = "",
    /** 参与的键（`KeyId.id`）。 */
    val keys: List<String> = emptyList(),
    val enabled: Boolean = true,
    val single: String = Action.NONE,
    val long: String = Action.NONE,
    val longPressMs: Int = KeyConfig.DEFAULT_LONG_PRESS_MS,
    /** 长按连续触发与其两个参数 —— 老文件没有这一段，读回来就是「不连发」。 */
    val longRepeat: Boolean = false,
    val longRepeatIntervalMs: Int = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
    val longRepeatVibrate: Boolean = false,
    /** 「长按松手才触发」—— 与单键那一档同名同义（老文件没有 ⇒ 关着）。 */
    val longFireOnRelease: Boolean = false,
    /** 「松手执行额外动作」的开关（老文件没有 ⇒ 关着）。 */
    val longReleaseOn: Boolean = false,
    /** 松手那一档的动作 token。 */
    val longRelease: String = Action.NONE,
    /**
     * **成员连击**（按住首键、唯一成员键连击）—— 动作 token 与开关分两格（与单键多击同族）。
     *  老文件没有这一段 ⇒ 全关着 / 无操作，与这一位出现之前一致，不需要迁移。
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
    /** 成员「双击后长按」/「三击后长按」—— 与上一对同一口径，只差前面那几击的击数。 */
    @SerialName("member_double_then_long")
    val memberDoubleThenLong: String = Action.NONE,
    @SerialName("member_double_then_long_on")
    val memberDoubleThenLongOn: Boolean = false,
    @SerialName("member_triple_then_long")
    val memberTripleThenLong: String = Action.NONE,
    @SerialName("member_triple_then_long_on")
    val memberTripleThenLongOn: Boolean = false,
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
    @SerialName("member_double_then_long_fire_on_release")
    val memberDoubleThenLongFireOnRelease: Boolean = false,
    @SerialName("member_double_then_long_is_serial")
    val memberDoubleThenLongIsSerial: Boolean = false,
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
    @SerialName("member_triple_then_long_fire_on_release")
    val memberTripleThenLongFireOnRelease: Boolean = false,
    @SerialName("member_triple_then_long_is_serial")
    val memberTripleThenLongIsSerial: Boolean = false,
    /**
     * 成员那三档「后长按」共用的按住时长（毫秒，`ComboKey.memberClickThenLongPressMs`）；
     * `0` = 跟随公共设置（`global_combo_member_then_long_press_ms`）。
     *
     *  老文件缺这一项 ⇒ 0 ⇒ 跟随公共（与 [holdMinMs] 同一条哨兵）。
     */
    @SerialName("member_click_then_long_press_ms")
    val memberClickThenLongPressMs: Int = ComboKey.MEMBER_CTL_PRESS_MS_FOLLOW_GLOBAL,
    /**
     * 成员「单击后长按」的**时序与收尾** —— 与组合长按那一套（`long_fire_on_release` /
     * `long_repeat` / `long_release*`）各自一份：它们挂在两个不同的档位上。
     *
     *  老文件缺这一段 ⇒ 全关着 / 默认间隔 / 无操作，与这一位出现之前一致，不需要迁移。
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
     * 这一档的**系统场景在什么时候定下来** —— `true` = 首键按下，`false` = 成员键按下。
     *
     *  老文件缺这一格 ⇒ `null` = 跟随公共设置（布尔没有第三个值，靠 `null` 表达「跟随」）。
     */
    val sceneOnStarter: Boolean? = null,
    /**
     * 这一档自己的「首键松手后仍执行它自己的长按」；`null` = 跟随公共设置。
     *
     *  老文件没有这一项 ⇒ `null` ⇒ 行为与这一位出现之前完全一致，不需要迁移。
     */
    val starterKeepLong: Boolean? = null,
    /**
     * **按顺序触发**（`ComboKey.ordered`）—— 老文件没有这一项 ⇒ `false`（只看集合），
     *  行为与这一位出现之前完全一致。`keys` 的顺序在这一位为真时就是「按下的先后」。
     */
    val ordered: Boolean = false,
    /**
     * 等待档的**固定窗口起点**（`ComboKey.fixedWindowStart`）；`null` = 跟随公共设置。
     *
     *  老文件没有这一项 ⇒ `null` ⇒ 跟随公共（默认逐次等满）。
     */
    val fixedWindowStart: Boolean? = null,
    /**
     * 按住档的**固定窗口起点**（`ComboKey.holdFixedWindowStart`）；`null` = 跟随公共设置。
     *
     *  与上面那一位是**两个变量**（两张卡各管一种按法）。老文件没有这一项 ⇒ `null`
     *  ⇒ 跟随公共（默认逐次等满），与这一位出现之前一致，不需要迁移。
     */
    @SerialName("hold_fixed_window_start")
    val holdFixedWindowStart: Boolean? = null,
    /**
     * **老的「一条规则一个手势」那一列** —— 只读，读到就迁移（见 [mergeScenes]）；与单键那份同型。
     */
    val rules: List<StateRuleDto> = emptyList(),
    /**
     * **系统场景**（`ComboKey.scenes`）—— 与单键那份同一个 [SceneRuleDto]，只是挂在不同对象上。
     *
     *  组合键只用得到单击 / 长按两档，块里的 `double` 在这条路上不会被读到。
     */
    val scenes: List<SceneRuleDto> = emptyList(),
    /**
     * 「按键单独设置」—— 与单键 `LegacyKeyDto` 那几项**同一套**（`OwnSettings` 契约）：
     * 这一组组合要不要自己决定接口 / 振动 / 提示。老文件缺它们 ⇒ 跟随公共设置。
     */
    val useOwnSettings: Boolean? = null,
    val colorOsApi: Boolean? = null,
    val vibrate: Boolean? = null,
    val vibrateStrength: Int? = null,
    @SerialName("vibrate_on_none")
    val vibrateOnNone: Boolean? = null,
    val toastOnRun: Boolean? = null,
    val toastFluid: Boolean? = null,
    val toastNotify: Boolean? = null,
    val toastTemplate: String? = null,
    val stepNotice: Boolean? = null,
    /**
     * **这一档自己的**四格窗口（毫秒，等待 / 按住 × 固定窗口起点 开 / 关）；`0` = 没写过 ⇒ 出厂默认。
     *
     *  v74 起分开存（见 `ComboWindows`）；老文件只有 [windowMs] 一个数，由 [toCombo] 铺到前三格。
     */
    val waitWindowMs: Int = ComboKey.WINDOW_FOLLOW_GLOBAL,
    @SerialName("wait_pair_ms")
    val waitPairMs: Int = ComboKey.WINDOW_FOLLOW_GLOBAL,
    val holdWindowMs: Int = ComboKey.WINDOW_FOLLOW_GLOBAL,
    @SerialName("hold_follower_ms")
    val holdFollowerMs: Int = ComboKey.WINDOW_FOLLOW_GLOBAL,
    /**
     * **遗留（v73 及以前）**：那一个「等待窗口」。
     *
     *  v74 之前叫 `windowMs`（`window_ms`）；现在只在读老文件时给前三格铺底，写出去永远是 0。
     */
    @SerialName("window_ms")
    val legacyWindowMs: Int = 0,
    /**
     * **遗留（v72 及以前）**：那一版把窗口拆成两笔账，这是「固定窗口起点下整串的窗口」那一份。
     *
     *  文件里若只有它（更老的那版），同样拿来铺底。写回永远是 0。
     */
    @SerialName("fixed_window_ms")
    val fixedWindowMs: Int = 0,
    /**
     * **触发方式**（`ComboKey.mode`）—— `0` 窗口内先后按下 / `1` 按住（首键 = `keys` 第一颗）。
     *
     *  `2` 是**旧编号**（= 先按住第二颗），现在只剩读旧文件时出现：见 [toCombo] 里那条迁移。
     *  `null` = **更老的文件**（v57 及以前没有这一位），由 [toCombo] 按下面那几个遗留字段推出来。
     */
    val mode: Int? = null,
    /**
     * 这一档自己的「起点键要先按住多久」（毫秒，`ComboKey.holdMinMs`）；`0` = 跟随公共设置。
     *
     *  老文件没有这一项 ⇒ 0 ⇒ 跟随公共（默认 100ms）。
     */
    val holdMinMs: Int = ComboKey.HOLD_MIN_FOLLOW_GLOBAL,
    /**
     * **遗留（v58 以前）**：那两条触发方式的开关与手动加过的顺序 —— 现在只剩迁移时读一次
     * （推演在 `LegacyCombo.kt`），写回永远是 `null` / 空表。
     *
     *  留着是因为 `ignoreUnknownKeys` 会把不认识的键直接丢掉：字段一删，老文件里那几位就再也
     *  读不到，「用户配过按住手势」这件事会静默变成「这一档只是等窗口」。
     */
    val windowOn: Boolean? = null,
    val holdOn: Boolean? = null,
    val holdOrders: List<Int> = emptyList(),
    /** **遗留（v58 以前）**：按住那两套动作槽 —— 同上，只在迁移时读一次。 */
    val singleA: String = Action.NONE,
    val longA: String = Action.NONE,
    val singleB: String = Action.NONE,
    val longB: String = Action.NONE,
) {
    /**
     * 线格式 → 一档组合键；id 空、或成员不足两颗的读不懂（返回 `null`）。
     *
     *  前者当不了身份（界面上选中哪一行、删哪一行都靠它），后者判不出来（一颗不叫组合）。
     *  成员去重之后按 [ComboKey.MAX_JUDGE_KEYS] 截断 —— 手改文件写进几十颗也不该让它进来。
     *
     *  截断用**判定侧**那个上限而不是界面的 `MAX_KEYS`：界面最多造三颗，
     *  但手改文件写进来的三颗组合判定侧认（判定 / 执行 / 日志都跑得通），读配置时截成两颗
     *  等于让那条链路永远收不到多成员形态。
     */
    fun toCombo(): ComboKey? {
        val members = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (id.isEmpty() || members.size < ComboKey.MIN_KEYS) return null
        //  老文件没有 `mode`：触发方式与它当年用的那对动作槽由 `LegacyCombo` 推一次（`null` = 新文件）
        val legacy = legacySlot()
        val resolved = legacy?.mode ?: mode ?: ComboKey.MODE_WAIT
        //  ## 旧编号 2（= 先按住第二颗）的迁移
        //
        //  现在「谁先按住」由 `keys` 的第一位表达，不再有第二个模式位。所以读到 2 就把**第二颗提到
        //  首位**、模式归一成 [ComboKey.MODE_HOLD] —— 于是 `[A, B]` + 2 与 `[B, A]` + 1 变成同一份
        //  数据，界面、判定、日志三处都不必再各分一次叉。
        //
        //  `LegacyCombo` 也可能给出 2（它把 v57 的「加过哪种顺序」推成这一位）：那条路一并在这里
        //  归一。它算出来的动作槽（`singleA` / `singleB`）与键序无关，所以先取动作、后动键序是安全的。
        val ordered =
            if (resolved == ComboKey.MODE_HOLD_SECOND) {
                members.take(2).reversed() + members.drop(2)
            } else {
                members
            }
        val singleAction = legacy?.single ?: Action.parse(single)
        val longAction = legacy?.long ?: Action.parse(long)
        //  动作 → 档位表；「成员第几击」与「单键第几击」是同一套编号（`Click(n)`），
        //  原先「组合侧 count、单键侧 count+1」那层换算在这里就消失了。
        val actions =
            buildMap {
                if (singleAction.isBound) put(GestureTrigger.Click(1), singleAction)
                if (longAction.isBound) put(GestureTrigger.LongPress, longAction)
                //  「无操作」不写进表：缺一档与写 `none` 同义（`act()` 对两者都回 None）。
                //  判据用 `isBound` 而不是 `!is None`：这是全项目唯一那把尺子 ——
                //  `follow` 只属于场景块，旧文件里手写进来也同样是「没绑东西」，一并落在表外。
                listOf(
                    GestureTrigger.Release to longRelease,
                    GestureTrigger.Click(2) to memberDouble,
                    GestureTrigger.ClickThenLong(1) to memberClickThenLong,
                    GestureTrigger.ClickThenLongRelease(1) to memberClickThenLongRelease,
                    GestureTrigger.ClickThenLong(2) to memberDoubleThenLong,
                    GestureTrigger.ClickThenLongRelease(2) to memberDoubleThenLongRelease,
                    GestureTrigger.ClickThenLong(3) to memberTripleThenLong,
                    GestureTrigger.ClickThenLongRelease(3) to memberTripleThenLongRelease,
                ).forEach { (trigger, raw) ->
                    val action = Action.parse(raw)
                    if (action.isBound) put(trigger, action)
                }
                memberClicks.forEach { (n, token) ->
                    val action = Action.parse(token)
                    if (n >= 3 && action.isBound) put(GestureTrigger.Click(n), action)
                }
            }
        val params =
            Params.EMPTY
                .with(ComboParams.MODE, ComboKey.modeFor(resolved, ordered.size))
                .with(ComboParams.ORDERED, this.ordered)
                .with(ComboParams.LONG_PRESS_MS, clamp(ConfigFields.F_LONG_PRESS_MS, longPressMs))
                .with(ComboParams.LONG_REPEAT, longRepeat)
                .with(ComboParams.LONG_REPEAT_INTERVAL_MS, clamp(ConfigFields.F_REPEAT_INTERVAL_MS, longRepeatIntervalMs))
                .with(ComboParams.LONG_REPEAT_VIBRATE, longRepeatVibrate)
                .with(ComboParams.LONG_FIRE_ON_RELEASE, longFireOnRelease)
                .with(ComboParams.LONG_RELEASE_ON, longReleaseOn)
                .with(ComboParams.MEMBER_DOUBLE_ON, memberDoubleOn)
                .with(ComboParams.MEMBER_CLICKS_ON, memberClicksOn.filter { it >= 3 }.toSet())
                .with(ComboParams.MEMBER_CLICK_THEN_LONG_ON, memberClickThenLongOn)
                .with(ComboParams.MEMBER_DOUBLE_THEN_LONG_ON, memberDoubleThenLongOn)
                .with(ComboParams.MEMBER_TRIPLE_THEN_LONG_ON, memberTripleThenLongOn)
                .with(ComboParams.MEMBER_DOUBLE_THEN_LONG_REPEAT, memberDoubleThenLongRepeat)
                .with(ComboParams.MEMBER_DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS, memberDoubleThenLongRepeatIntervalMs)
                .with(ComboParams.MEMBER_DOUBLE_THEN_LONG_REPEAT_VIBRATE, memberDoubleThenLongRepeatVibrate)
                .with(ComboParams.MEMBER_DOUBLE_THEN_LONG_RELEASE_ON, memberDoubleThenLongReleaseOn)
                .with(ComboParams.MEMBER_DOUBLE_THEN_LONG_FIRE_ON_RELEASE, memberDoubleThenLongFireOnRelease)
                .with(ComboParams.MEMBER_DOUBLE_THEN_LONG_IS_SERIAL, memberDoubleThenLongIsSerial)
                .with(ComboParams.MEMBER_TRIPLE_THEN_LONG_REPEAT, memberTripleThenLongRepeat)
                .with(ComboParams.MEMBER_TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS, memberTripleThenLongRepeatIntervalMs)
                .with(ComboParams.MEMBER_TRIPLE_THEN_LONG_REPEAT_VIBRATE, memberTripleThenLongRepeatVibrate)
                .with(ComboParams.MEMBER_TRIPLE_THEN_LONG_RELEASE_ON, memberTripleThenLongReleaseOn)
                .with(ComboParams.MEMBER_TRIPLE_THEN_LONG_FIRE_ON_RELEASE, memberTripleThenLongFireOnRelease)
                .with(ComboParams.MEMBER_TRIPLE_THEN_LONG_IS_SERIAL, memberTripleThenLongIsSerial)
                //  成员「后长按」三档的按住时长：0 是「跟随公共」，不能被 `clamp` 抬到下限
                //  （那就成了「这一档显式设了 200ms」，界面上再也回不到跟随）。
                .with(
                    ComboParams.MEMBER_CLICK_THEN_LONG_PRESS_MS,
                    if (memberClickThenLongPressMs <= 0) {
                        ComboKey.MEMBER_CTL_PRESS_MS_FOLLOW_GLOBAL
                    } else {
                        clamp(ConfigFields.F_LONG_PRESS_MS, memberClickThenLongPressMs)
                    },
                ).with(ComboParams.MEMBER_CLICK_THEN_LONG_FIRE_ON_RELEASE, memberClickThenLongFireOnRelease)
                .with(ComboParams.MEMBER_CLICK_THEN_LONG_REPEAT, memberClickThenLongRepeat)
                .with(
                    ComboParams.MEMBER_CLICK_THEN_LONG_REPEAT_INTERVAL_MS,
                    clamp(ConfigFields.F_REPEAT_INTERVAL_MS, memberClickThenLongRepeatIntervalMs),
                ).with(ComboParams.MEMBER_CLICK_THEN_LONG_REPEAT_VIBRATE, memberClickThenLongRepeatVibrate)
                .with(ComboParams.MEMBER_CLICK_THEN_LONG_RELEASE_ON, memberClickThenLongReleaseOn)
                //  「单击后长按」那一档的 isSerial 不落盘（与单键同一个口径：它由命令库现算）。
                //  窗口四格：0 是「跟随公共」，不能被 `clamp` 抬到下限。
                .with(ComboParams.WAIT_WINDOW_MS, clampOrFollow(waitWindowMs, legacyWindowMs, fixedWindowMs))
                .with(ComboParams.WAIT_PAIR_MS, clampOrFollow(waitPairMs, legacyWindowMs, fixedWindowMs))
                .with(ComboParams.HOLD_WINDOW_MS, clampOrFollow(holdWindowMs, legacyWindowMs, fixedWindowMs))
                .with(ComboParams.HOLD_FOLLOWER_MS, clampOrFollow(holdFollowerMs, legacyWindowMs, fixedWindowMs))
                .with(
                    ComboParams.HOLD_MIN_MS,
                    if (holdMinMs <= 0) {
                        ComboKey.HOLD_MIN_FOLLOW_GLOBAL
                    } else {
                        clamp(ConfigFields.F_COMBO_HOLD_MIN_MS, holdMinMs)
                    },
                ).with(ComboParams.SCENE_ON_STARTER, sceneOnStarter)
                .with(ComboParams.STARTER_KEEP_LONG, starterKeepLong)
                .with(ComboParams.FIXED_WINDOW_START, fixedWindowStart)
                .with(ComboParams.HOLD_FIXED_WINDOW_START, holdFixedWindowStart)
                .with(ComboParams.COLOR_OS_API, colorOsApi ?: GlobalConfig.DEFAULT_COLOR_OS_API)
                .with(ComboParams.VIBRATE, vibrate ?: false)
                .with(
                    ComboParams.VIBRATE_STRENGTH,
                    clamp(ConfigFields.F_KEY_VIBRATE_STRENGTH, vibrateStrength ?: GlobalConfig.DEFAULT_VIBRATE_STRENGTH),
                ).with(ComboParams.VIBRATE_ON_NONE, vibrateOnNone ?: GlobalConfig.DEFAULT_VIBRATE_ON_NONE)
                .with(ComboParams.TOAST_ON_RUN, toastOnRun ?: GlobalConfig.DEFAULT_TOAST_ON_RUN)
                .with(ComboParams.TOAST_FLUID, toastFluid ?: GlobalConfig.DEFAULT_TOAST_FLUID)
                .with(ComboParams.TOAST_NOTIFY, toastNotify ?: GlobalConfig.DEFAULT_TOAST_NOTIFY)
                .with(ComboParams.TOAST_TEMPLATE, toastTemplate ?: GlobalConfig.DEFAULT_TOAST_TEMPLATE)
                .with(ComboParams.STEP_NOTICE, stepNotice ?: GlobalConfig.DEFAULT_STEP_NOTICE)
        return ComboKey(
            id = id,
            keys = ordered.take(ComboKey.MAX_JUDGE_KEYS),
            enabled = enabled,
            actions = actions,
            params = params,
            //  条件表：认不出的条目整条丢掉（一条读不懂的规则兜成「永远成立」会把动作整个抢走）。
            scenes = mergeScenes(scenes, rules),
            useOwnSettings = useOwnSettings ?: false,
        )
    }

    companion object {
        fun of(combo: ComboKey): LegacyComboDto =
            LegacyComboDto(
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
                memberDoubleThenLong = combo.memberDoubleThenLong.token,
                memberDoubleThenLongOn = combo.memberDoubleThenLongOn,
                memberTripleThenLong = combo.memberTripleThenLong.token,
                memberTripleThenLongOn = combo.memberTripleThenLongOn,
                memberDoubleThenLongRepeat = combo.memberDoubleThenLongRepeat,
                memberDoubleThenLongRepeatIntervalMs = combo.memberDoubleThenLongRepeatIntervalMs,
                memberDoubleThenLongRepeatVibrate = combo.memberDoubleThenLongRepeatVibrate,
                memberDoubleThenLongReleaseOn = combo.memberDoubleThenLongReleaseOn,
                memberDoubleThenLongRelease = combo.memberDoubleThenLongRelease.token,
                memberDoubleThenLongFireOnRelease = combo.memberDoubleThenLongFireOnRelease,
                memberDoubleThenLongIsSerial = combo.memberDoubleThenLongIsSerial,
                memberTripleThenLongRepeat = combo.memberTripleThenLongRepeat,
                memberTripleThenLongRepeatIntervalMs = combo.memberTripleThenLongRepeatIntervalMs,
                memberTripleThenLongRepeatVibrate = combo.memberTripleThenLongRepeatVibrate,
                memberTripleThenLongReleaseOn = combo.memberTripleThenLongReleaseOn,
                memberTripleThenLongRelease = combo.memberTripleThenLongRelease.token,
                memberTripleThenLongFireOnRelease = combo.memberTripleThenLongFireOnRelease,
                memberTripleThenLongIsSerial = combo.memberTripleThenLongIsSerial,
                memberClickThenLongPressMs = combo.memberClickThenLongPressMs,
                memberClickThenLongFireOnRelease = combo.memberClickThenLongFireOnRelease,
                memberClickThenLongRepeat = combo.memberClickThenLongRepeat,
                memberClickThenLongRepeatIntervalMs = combo.memberClickThenLongRepeatIntervalMs,
                memberClickThenLongRepeatVibrate = combo.memberClickThenLongRepeatVibrate,
                memberClickThenLongReleaseOn = combo.memberClickThenLongReleaseOn,
                memberClickThenLongRelease = combo.memberClickThenLongRelease.token,
                sceneOnStarter = combo.sceneOnStarter,
                starterKeepLong = combo.starterKeepLong,
                ordered = combo.ordered,
                fixedWindowStart = combo.fixedWindowStart,
                holdFixedWindowStart = combo.holdFixedWindowStart,
                scenes = combo.scenes.map { SceneRuleDto.of(it) },
                useOwnSettings = combo.useOwnSettings,
                colorOsApi = combo.colorOsApi,
                vibrate = combo.vibrate,
                vibrateStrength = combo.vibrateStrength,
                vibrateOnNone = combo.vibrateOnNone,
                toastOnRun = combo.toastOnRun,
                toastFluid = combo.toastFluid,
                toastNotify = combo.toastNotify,
                toastTemplate = combo.toastTemplate,
                stepNotice = combo.stepNotice,
                waitWindowMs = combo.waitWindowMs,
                waitPairMs = combo.waitPairMs,
                holdWindowMs = combo.holdWindowMs,
                holdFollowerMs = combo.holdFollowerMs,
                mode = combo.mode,
                holdMinMs = combo.holdMinMs,
            )
    }
}
