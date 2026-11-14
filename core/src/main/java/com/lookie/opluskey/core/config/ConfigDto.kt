package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.CommonCommand
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.ConfigFields
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.core.model.trigger
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
internal data class ConfigDto(
    val version: Int = 0,
    val keys: Map<String, KeyDto> = emptyMap(),
    @SerialName("custom_keys") val customKeys: List<CustomKeyDto> = emptyList(),
    val global: GlobalDto = GlobalDto(),
    val disclaimerAccepted: Boolean = false,
    val commandDisclaimerAccepted: Boolean = false,
    @SerialName("app_names") val appNames: Map<String, String> = emptyMap(),
    /** 组合按键（`Config.combos`）—— 与 `app_names` 同一套：单独一段，只走落盘。 */
    val combos: List<ComboDto> = emptyList(),
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
        //  **额外档位的丢弃也在这里记账**（真正的丢弃在 [KeyDto.toKeyConfig] 的 `mapNotNull` 里）：
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
                val key = (keys[id.id] ?: KeyDto()).toKeyConfig(id, legacyDouble)
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
        ): ConfigDto =
            ConfigDto(
                version = version,
                keys = config.allKeys.associate { it.id to KeyDto.from(config.key(it)) },
                customKeys = config.customKeys.map { CustomKeyDto(it.id, it.label, it.keyCode) },
                global = GlobalDto.from(config.global),
                disclaimerAccepted = config.disclaimerAccepted,
                commandDisclaimerAccepted = config.commandDisclaimerAccepted,
                appNames = config.appNames,
                combos = config.combos.map { ComboDto.of(it) },
                profileId = config.profileId,
                profileName = config.profileName,
                profileOrder = config.profileOrder,
                md5 = md5,
            )
    }
}

/** 一颗键的绑定。 */
@Serializable
internal data class KeyDto(
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
     *  `null` = 这份文件里没写（老文件 / 手写）：读的时候退回 `global` 那份，见 [ConfigDto.toConfig]。
     *  新写出来的文件一定是具体值。
     */
    val doubleClickMs: Int? = null,
    /**
     * 「这颗键只参与组合」—— 单键手势交还系统，模块只用它凑组合（见 `KeyConfig.comboOnly`）。
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
    /** 这颗键自己的「执行时提示」/「提示走流体云」/「提示文案」。 */
    val toastOnRun: Boolean? = null,
    val toastFluid: Boolean? = null,
    val toastTemplate: String? = null,
    /**
     * 上面那几项**被公共设置播过种没有**（见 `OwnSettings.ownSettingsSeeded`）。
     *
     *  `null` = 老文件没有这一项 ⇒ 没播过，第一次打开总开关时会播一次（与新装一致）。
     */
    val ownSettingsSeeded: Boolean? = null,
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
                ConfigFields.F_WAIT_MULTI_WINDOW to (waitMultiWindow ?: false),
                //  **与上面几档同一条纪律：上下限只定义在 `ConfigFields` 里**。不钳的话，手改出来的
                //  `600000` 会原样成为判定窗口 —— 那颗键此后每按一次都要等十分钟才回落。
                ConfigFields.F_MULTI_WAIT_MS to
                    clamp(ConfigFields.F_MULTI_WAIT_MS, multiWaitMs ?: KeyConfig.FOLLOW_DOUBLE_CLICK_MS),
                ConfigFields.F_OWN_SETTINGS to (useOwnSettings ?: false),
                ConfigFields.F_KEY_COLOR_OS_API to (colorOsApi ?: GlobalConfig.DEFAULT_COLOR_OS_API),
                ConfigFields.F_KEY_VIBRATE to (vibrate ?: false),
                ConfigFields.F_KEY_VIBRATE_STRENGTH to
                    clamp(ConfigFields.F_KEY_VIBRATE_STRENGTH, vibrateStrength ?: GlobalConfig.DEFAULT_VIBRATE_STRENGTH),
                ConfigFields.F_KEY_TOAST_ON_RUN to (toastOnRun ?: GlobalConfig.DEFAULT_TOAST_ON_RUN),
                ConfigFields.F_KEY_TOAST_FLUID to (toastFluid ?: GlobalConfig.DEFAULT_TOAST_FLUID),
                ConfigFields.F_KEY_TOAST_TEMPLATE to (toastTemplate ?: GlobalConfig.DEFAULT_TOAST_TEMPLATE),
                ConfigFields.F_OWN_SETTINGS_SEEDED to (ownSettingsSeeded ?: false),
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
        )

    /** 线格式 → 一颗键的配置（含状态分支）。几个 `legacy*` 是迁移兜底，见 [ConfigDto.toConfig]。 */
    fun toKeyConfig(
        id: KeyId,
        legacyDoubleClickMs: Int,
    ): KeyConfig =
        KeyConfig
            .ofFields(id, toValues(legacyDoubleClickMs), mergeScenes(scenes, rules))
            .copy(
                //  认不出的次数 / 动作整条丢掉（与 `rules`、`custom_keys` 同一条纪律：丢东西要留账，
                //  账在 `ConfigDto.toConfig` 那一层按整份文件打一次）
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
        fun from(key: KeyConfig): KeyDto =
            KeyDto(
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
                toastOnRun = key.toastOnRun,
                toastFluid = key.toastFluid,
                toastTemplate = key.toastTemplate,
                ownSettingsSeeded = key.ownSettingsSeeded,
                scenes = key.scenes.map { SceneRuleDto.of(it) },
                extraClicks = key.extraClicks.mapKeys { it.key.toString() }.mapValues { it.value.token },
                //  写侧一律给具体值：给 `null` 的话下次读回来会走「按表倒推」那条老路
                extraClicksOn = JsonArray(key.extraClicksOn.sorted().map { JsonPrimitive(it) }),
                clickThenLongOn = key.clickThenLongOn,
                clickThenLong = key.clickThenLong.token,
                clickThenLongPressMs = key.clickThenLongPressMs,
                clickThenLongRepeat = key.clickThenLongRepeat,
                clickThenLongRepeatIntervalMs = key.clickThenLongRepeatIntervalMs,
                clickThenLongRepeatVibrate = key.clickThenLongRepeatVibrate,
                clickThenLongReleaseOn = key.clickThenLongReleaseOn,
                clickThenLongRelease = key.clickThenLongRelease.token,
            )
    }
}

/**
 * **老的**「一条规则一个手势」那一项 —— **只读**：新写的文件不再写这一列（见 [KeyDto.scenes]）。
 *
 *  留着它只为把已经存在的配置读上来：[mergeLegacy] 会把「条件完全相同的那几条」并成一块。
 *  删掉这一格的话，老文件里那一整批条件分支会在升级那一刻凭空消失。
 *
 *  两格条件字段：老文件只有 [condition]（单条），后一版的文件还写了 [conditions]（全量）。
 *  读的时候优先看 [conditions]，它空着才回落到 [condition]。
 */
@Serializable
internal data class StateRuleDto(
    /** 手势取 `Gesture.id`（`single` / `double` / `long`），不是枚举名。 */
    val gesture: String = "",
    /** 老写法：单条条件（`app:包名` / `ringer:vibrate` / `screen_on`），见 `BranchCondition.token`。 */
    val condition: String = "",
    /** 这一条的**全部**条件，按书写顺序（同种类之间「或」、不同种类之间「且」）。 */
    val conditions: List<String> = emptyList(),
    val action: String = Action.NONE,
    /** 这一条参不参与判定（老文件没有这一格 ⇒ 启用）。 */
    val enabled: Boolean = true,
) {
    /**
     * 这一条的条件（解析后）；`null` = 认不出 —— **整条丢掉**。
     *
     *  为什么不兜成「永远成立」：那样一条写错的条件会静默接管这颗键的所有手势。
     *  同理，一条里**只要有一条**条件认不出，整条丢掉 —— 留着其余几条会把「且」悄悄放宽。
     */
    fun conditionsOrNull(): List<BranchCondition>? {
        if (conditions.isEmpty()) {
            val single = BranchCondition.parse(condition)
            if (single == null) XLog.w("配置：老的场景规则里那条条件认不出（丢掉这一条）：$condition")
            return single?.let(::listOf)
        }
        val parsed = conditions.map { BranchCondition.parse(it) }
        val bad = parsed.indexOfFirst { it == null }
        if (bad >= 0) {
            XLog.w("配置：老的场景规则里第 ${bad + 1} 条条件认不出（整条丢掉）：${conditions.getOrNull(bad)}")
            return null
        }
        return parsed.filterNotNull()
    }
}

/**
 * `scenes` 的一项 —— **一个场景块**：一组条件 + 它接管的那几档动作（见 `SceneRule`）。
 *
 *  动作表 [actions] 的键是**档位 id**（`single` / `double` / `long` / `click3` …，与
 *  `GestureTrigger.idOf` 同一套字面量）。**表里没有的档位 = 这一档不参与**，
 *  与显式写 `follow` 同义；`"none"` 则是「命中后这一档什么都不做」。
 *
 *  老的 [single] / [double] / [long] 三格**只读不写**：留着它们是为了把已经存在的配置读上来
 *  （[toScene] 在 [actions] 空着时读那三格）。写下去的一律只有 [actions]。
 */
@Serializable
internal data class SceneRuleDto(
    /** 这一块的全部条件（`BranchCondition.token`），同种类之间「或」、不同种类之间「且」。空 = 还没配完。 */
    val conditions: List<String> = emptyList(),
    /** 档位 id → 动作 token；缺项 = 这一档不参与（见类注释）。 */
    val actions: Map<String, String> = emptyMap(),
    /**
     * **老的**三格 —— 只读（读到就并进 [actions]，见 [toScene]）。
     *
     *  写侧仍然会把它们写出来（`encodeDefaults = true`，值是 `follow`）：降级回上一版时，
     *  那一版只认这三格 —— 写 `follow` 等于「这一档不接管」，与表里没有那一项同义，
     *  比写 `null` 稳（`null` 落进非空字段要靠 `coerceInputValues` 兜）。
     */
    val single: String = Action.FOLLOW,
    val double: String = Action.FOLLOW,
    val long: String = Action.FOLLOW,
    /** 「松手执行额外动作」的开关（`SceneRule.releaseOn`）。 */
    val releaseOn: Boolean = false,
    /** 松手那一档的动作 token；默认 `none` = 这一块命中时松手什么都不做。 */
    val release: String = Action.NONE,
    /** 「单击后长按」的松手 —— 与 [releaseOn] / [release] 同一对，挂在另一档上。 */
    val chainReleaseOn: Boolean = false,
    val chainRelease: String = Action.NONE,
    val enabled: Boolean = true,
) {
    /**
     * 线格式 → 一块场景；**认不出就整块丢掉**（返回 `null`）。
     *
     *  条件空着的块也算认不出：它在判定侧永不成立，留着只会让「这张表里有什么」对不上。
     *
     *  档位 id 认不出的那一条**单独丢掉并留账**（不连坐整块）：那是手改 / 降级留下的，
     *  丢掉它只影响那一档；整块丢掉会把用户其余几档一起抹了。
     */
    fun toScene(): SceneRule? {
        val parsed = conditions.map { BranchCondition.parse(it) }
        val bad = parsed.indexOfFirst { it == null }
        if (parsed.isEmpty() || bad >= 0) {
            XLog.w("配置：场景块的条件认不出（整块丢掉）：${conditions.getOrNull(bad) ?: "空"}")
            return null
        }
        return SceneRule(
            conditions = parsed.filterNotNull(),
            actions = slotActions(),
            releaseOn = releaseOn,
            release = Action.parse(release),
            chainReleaseOn = chainReleaseOn,
            chainRelease = Action.parse(chainRelease),
            enabled = enabled,
        )
    }

    /**
     * 档位 → 动作 —— **新格优先，老格只在对应档位没写过时补**。
     *
     *  两个来源同时存在只可能是「新版写过一次、某个旧版又存过一次」那种混杂文件：
     *  那时新格里的是上次的完整结果，老格最多是其中三档的一份旧副本。
     */
    private fun slotActions(): Map<GestureTrigger, Action> {
        val out = LinkedHashMap<GestureTrigger, Action>()
        actions.forEach { (id, token) ->
            val trigger = GestureTrigger.ofId(id)
            if (trigger == null) {
                XLog.w("配置：场景块里有认不出的档位（丢掉这一档）：$id")
            } else {
                out[trigger] = Action.parse(token)
            }
        }
        //  老的三格：只补新格没写到的档位，且 `follow` 不算「写了」（它就是「不参与」）
        listOf("single" to single, "double" to double, "long" to long).forEach { (id, token) ->
            val trigger = Gesture.ofId(id)?.trigger() ?: return@forEach
            val action = Action.parse(token)
            if (action !is Action.Follow) out.putIfAbsent(trigger, action)
        }
        return out
    }

    companion object {
        /**
         * 一块场景 → 线格式。
         *
         *  条件空着的块**不写**（调用方已经滤过）；这里再兜一次是防手滑 ——
         *  写下去读回来会被 [toScene] 整块丢掉，等于白写。
         *
         *  只写 [actions]：老的三格留空，读侧会把「没写到」当成「这一档不参与」。
         */
        fun of(scene: SceneRule): SceneRuleDto =
            SceneRuleDto(
                conditions = scene.conditions.map { it.token },
                actions =
                    scene.actions
                        .filterValues { it !is Action.Follow }
                        .mapKeys { GestureTrigger.idOf(it.key) }
                        .mapValues { it.value.token },
                releaseOn = scene.releaseOn,
                release = scene.release.token,
                chainReleaseOn = scene.chainReleaseOn,
                chainRelease = scene.chainRelease.token,
                enabled = scene.enabled,
            )
    }
}

/**
 * 新旧两列合成一张场景表 —— **新列写了就用它**，它空着才按老列迁移。
 *
 *  两条同时存在只可能是「新版写过一次、但某个旧版又存过一次」那种混杂文件：
 *  那时新列是上一次的完整结果，比老列（可能只覆盖部分手势）更可信。
 */
private fun mergeScenes(
    scenes: List<SceneRuleDto>,
    rules: List<StateRuleDto>,
): List<SceneRule> = if (scenes.isNotEmpty()) scenes.mapNotNull { it.toScene() } else mergeLegacy(rules)

/**
 * 老列 → 场景表：**条件完全相同的那几条并成一块**，各填自己那一档。
 *
 *  这就是翻转的迁移口径 —— 老配置的每一条规则本来只认一个手势，并起来正好是一块多档：
 *  · 顺序按**第一次出现**的那一块，与老的「从上往下找」一致；
 *  · 同一块同一档出现多次时**取最靠前的那条**（老判定就是「第一条赢」）；
 *  · **停用的老规则直接跳过**：它的那一档留「跟随默认」⇒ 回落默认绑定，与停用前的行为逐字一致。
 *
 *  老列里的手势 id 只有 `single` / `double` / `long` 三种（`Gesture.ofId` 认的那三个）——
 *  多击是后来才有的档位，老列里写不出来。
 */
private fun mergeLegacy(rules: List<StateRuleDto>): List<SceneRule> {
    val merged = LinkedHashMap<List<String>, SceneRule>()
    for (rule in rules) {
        //  三样都可能让这一条进不了表：停用、档位认不出、条件认不出。合成一条判据，
        //  一处 `continue` —— 分开写会顶到 detekt 的「循环里跳转太多」。
        val trigger = Gesture.ofId(rule.gesture)?.trigger()
        val conditions = rule.conditionsOrNull()
        if (!rule.enabled || trigger == null || conditions == null) continue
        val key = conditions.map { it.token }
        val block = merged[key] ?: SceneRule(conditions = conditions)
        if (block.actionOf(trigger) is Action.Follow) {
            merged[key] = block.withAction(trigger, Action.parse(rule.action))
        }
    }
    return merged.values.toList()
}

/** `combos` 的一项 —— 一档组合键。 */
@Serializable
internal data class ComboDto(
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
     * 这一档的**系统场景在什么时候定下来** —— `true` = 首键按下，`false` = 成员键按下。
     *
     *  老文件缺这一格 ⇒ `null` = 跟随公共设置（布尔没有第三个值，靠 `null` 表达「跟随」）。
     */
    val sceneOnStarter: Boolean? = null,
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
     * 「按键单独设置」—— 与单键 `KeyDto` 那几项**同一套**（`OwnSettings` 契约）：
     * 这一组组合要不要自己决定接口 / 振动 / 提示。老文件缺它们 ⇒ 跟随公共设置。
     */
    val useOwnSettings: Boolean? = null,
    val colorOsApi: Boolean? = null,
    val vibrate: Boolean? = null,
    val vibrateStrength: Int? = null,
    val toastOnRun: Boolean? = null,
    val toastFluid: Boolean? = null,
    val toastTemplate: String? = null,
    /** 上面那几项被公共设置播过种没有 —— 与单键那条同义（`OwnSettings.ownSettingsSeeded`）。 */
    val ownSettingsSeeded: Boolean? = null,
    /**
     * 这一档自己的「按键之间的等待」（毫秒）；`0` = 跟随公共设置。只在 [ComboKey.MODE_WAIT] 上算数。
     *
     *  老文件没有这一项 ⇒ 0 ⇒ 行为与这一位出现之前完全一致，不需要迁移。
     */
    val windowMs: Int = ComboKey.WINDOW_FOLLOW_GLOBAL,
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
        return ComboKey(
            id = id,
            keys = ordered.take(ComboKey.MAX_JUDGE_KEYS),
            enabled = enabled,
            //  触发方式：成员够两颗就两种都认（按住那一档不再限于两颗，见 `ComboKey.modeFor`）
            mode = ComboKey.modeFor(resolved, ordered.size),
            //  老文件把按住那一套动作放在 `singleA`/`longA`（或 B）上，已在 `LegacyCombo` 里搬进
            //  这一对通用槽；新文件（`mode` 有值）一律读通用的那两位 —— 不看遗留槽，就不会两套同时生效。
            single = legacy?.single ?: Action.parse(single),
            long = legacy?.long ?: Action.parse(long),
            // 长按阈值与单键共用同一个区间（`ConfigFields.F_LONG_PRESS_MS`）：它量的是同一件事。
            longPressMs = clamp(ConfigFields.F_LONG_PRESS_MS, longPressMs),
            longRepeat = longRepeat,
            longRepeatIntervalMs = clamp(ConfigFields.F_REPEAT_INTERVAL_MS, longRepeatIntervalMs),
            longRepeatVibrate = longRepeatVibrate,
            //  「松手」那两位与单键同一个口径（老文件没有这一段 ⇒ 关着 + 无操作）。
            longFireOnRelease = longFireOnRelease,
            longReleaseOn = longReleaseOn,
            longRelease = Action.parse(longRelease),
            //  条件表：认不出的条目整条丢掉（与单键那条同一个 `mapNotNull` 口径）——
            //  一条读不懂的规则如果兜成「永远成立」，会把这一档的动作整个抢走。
            scenes = mergeScenes(scenes, rules),
            //  「按键单独设置」：缺项一律按「跟随公共设置」，与新装的默认一致
            useOwnSettings = useOwnSettings ?: false,
            colorOsApi = colorOsApi ?: GlobalConfig.DEFAULT_COLOR_OS_API,
            vibrate = vibrate ?: false,
            vibrateStrength = clamp(ConfigFields.F_KEY_VIBRATE_STRENGTH, vibrateStrength ?: GlobalConfig.DEFAULT_VIBRATE_STRENGTH),
            toastOnRun = toastOnRun ?: GlobalConfig.DEFAULT_TOAST_ON_RUN,
            toastFluid = toastFluid ?: GlobalConfig.DEFAULT_TOAST_FLUID,
            toastTemplate = toastTemplate ?: GlobalConfig.DEFAULT_TOAST_TEMPLATE,
            ownSettingsSeeded = ownSettingsSeeded ?: false,
            //  「按键之间的等待」：0 表示跟随公共设置，必须原样留着 —— `clamp` 会把它抬到下限 20，
            //  那就成了「这一组显式设了 20ms」，界面上再也回不到跟随。
            windowMs = if (windowMs <= 0) ComboKey.WINDOW_FOLLOW_GLOBAL else clamp(ConfigFields.F_COMBO_WINDOW_MS, windowMs),
            //  「首键要先按住多久」：与上面那个窗口同一条 —— 0 是「跟随公共」，不能被 `clamp`
            //  抬到下限 40（那就成了「这一组显式设了 40ms」）。
            holdMinMs =
                if (holdMinMs <= 0) {
                    ComboKey.HOLD_MIN_FOLLOW_GLOBAL
                } else {
                    clamp(ConfigFields.F_COMBO_HOLD_MIN_MS, holdMinMs)
                },
            sceneOnStarter = sceneOnStarter,
        )
    }

    companion object {
        fun of(combo: ComboKey): ComboDto =
            ComboDto(
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
                sceneOnStarter = combo.sceneOnStarter,
                scenes = combo.scenes.map { SceneRuleDto.of(it) },
                useOwnSettings = combo.useOwnSettings,
                colorOsApi = combo.colorOsApi,
                vibrate = combo.vibrate,
                vibrateStrength = combo.vibrateStrength,
                toastOnRun = combo.toastOnRun,
                toastFluid = combo.toastFluid,
                toastTemplate = combo.toastTemplate,
                ownSettingsSeeded = combo.ownSettingsSeeded,
                windowMs = combo.windowMs,
                mode = combo.mode,
                holdMinMs = combo.holdMinMs,
            )
    }
}

/** 全局配置。 */
@Serializable
internal data class GlobalDto(
    /**
     * 老文件里「单击延迟 / 忽视无操作双击」在全局这一段 —— 现在归每颗键（[KeyDto]）。
     * 这两个字段只为**迁移**读一次，写出去时是 `null`（新文件不再有它们）。
     *
     *  **v65 起它又是「公共设置里的单击延迟」**：没开「使用独立设置」的键读这一份
     *  （见 `KeyConfig.fieldValues(global)` 与 `GlobalConfig.doubleClickMs`）。老文件里它本来就
     *  是全局单击延迟，语义正好接上；新文件里它是一个活字段，不再是 `null`。
     */
    val doubleClickMs: Int? = null,
    /**
     * 公共设置里的**判定时机**（`GlobalConfig.waitMultiWindow`）。
     *
     *  `null` = 老文件没有这一项 ⇒ 按默认（`false`，固定窗口起点）。
     */
    @SerialName("global_wait_multi_window")
    val waitMultiWindow: Boolean? = null,
    /**
     * 公共设置里的**等待窗口**（`GlobalConfig.multiWaitMs`）。
     *
     *  `null` = 老文件没有这一项 ⇒ 0 = 跟随 [doubleClickMs]。
     */
    @SerialName("global_multi_wait_ms")
    val multiWaitMs: Int? = null,
    /**
     * **模块总闸**。`null` = 这份文件是拆开之前写的 —— 那时「主开关」就是 `keys.plus.enabled`，
     * 迁移见 [ConfigDto.toConfig]；新写出来的文件一定是具体值。
     */
    val enabled: Boolean? = null,
    val vibrate: Boolean = false,
    /** 振动强度（三档）—— 老文件没有这一项，取值会退回字段表的默认值（中档）。 */
    @SerialName("vibrate_strength")
    val vibrateStrength: Int = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
    val toastOnRun: Boolean = GlobalConfig.DEFAULT_TOAST_ON_RUN,
    /** 执行时提示走流体云 —— 老文件没有这一项，退回默认（开，见 v63 那次语义迁移）。 */
    @SerialName("toast_fluid")
    val toastFluid: Boolean = GlobalConfig.DEFAULT_TOAST_FLUID,
    /** 「执行时提示」的文案模板（见 `GlobalConfig.toastTemplate`）—— 老文件没有这一项，退回默认那句。 */
    @SerialName("toast_template")
    val toastTemplate: String = GlobalConfig.DEFAULT_TOAST_TEMPLATE,
    val language: String = GlobalConfig.DEFAULT_LANGUAGE,
    /** 系统配色的 id（见 `GlobalConfig.palette`）—— 老文件没有这一项，取值退回默认档。 */
    val palette: String = GlobalConfig.DEFAULT_PALETTE,
    /** 界面颜色（见 `GlobalConfig.theme`）—— 老文件没有这一项，退回「跟随系统」。 */
    val theme: String = GlobalConfig.DEFAULT_THEME,
    val colorOsApi: Boolean = GlobalConfig.DEFAULT_COLOR_OS_API,
    val miniWindowAnim: Boolean = GlobalConfig.DEFAULT_MINI_WINDOW_ANIM,
    /** 「跟随系统显示大小」—— 老文件没有这一项，取值退回默认（开）。 */
    @SerialName("follow_display_size")
    val followDisplaySize: Boolean = GlobalConfig.DEFAULT_FOLLOW_DISPLAY_SIZE,
    /** 页面缩放（百分比）—— 老文件没有这一项，退回出厂值 100%。 */
    @SerialName("page_scale")
    val pageScale: Int = GlobalConfig.DEFAULT_PAGE_SCALE,
    /** 组合键两颗键之间的等待窗口（毫秒）—— 老文件没有这一项，退回出厂值 80。 */
    @SerialName("combo_window_ms")
    val comboWindowMs: Int = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
    /** 按住那两档里起点键要先按住多久（毫秒）—— 老文件没有这一项，退回出厂值 100。 */
    @SerialName("combo_hold_min_ms")
    val comboHoldMinMs: Int = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
    /** 按住那两档的场景在什么时候定下来（见 `GlobalConfig.comboSceneOnStarter`）—— 只落盘。 */
    val comboSceneOnStarter: Boolean = GlobalConfig.DEFAULT_COMBO_SCENE_ON_STARTER,
    /** 随机图标配色的种子（`0` = 关）—— 老文件没有这一项，退回默认（关）。 */
    @SerialName("icon_tint_seed")
    val iconTintSeed: Int = GlobalConfig.DEFAULT_ICON_TINT_SEED,
    /** 按键绑定页显不显示「常用命令」那一段 —— 老文件没有这一项，退回默认（显示）。 */
    @SerialName("show_common_commands")
    val showCommonCommands: Boolean = GlobalConfig.DEFAULT_SHOW_COMMON_COMMANDS,
    /**
     * 「常用命令」那张用户自己的清单 —— **老文件没有这一项**（`null`）。
     *
     *  `null` 与空表的区别要留住：前者是「从没动过，用模块自带那几条」，后者是「进过那一页、
     * 把它们全删了」。所以这里不写成 `emptyList()`。
     */
    @SerialName("common_commands")
    val commonCommands: List<CommonCommandDto> = emptyList(),
) {
    fun toValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                // 老文件这里给的是 null，取值会退回字段表的默认值（false）；真正的迁移在
                //  `ConfigDto.toConfig` 里做 —— 它还要顺手改 `keys.plus` 那一位
                ConfigFields.F_MODULE_ENABLED to (enabled ?: GlobalConfig.DEFAULT_ENABLED),
                ConfigFields.F_VIBRATE to vibrate,
                ConfigFields.F_VIBRATE_STRENGTH to vibrateStrength,
                ConfigFields.F_TOAST_ON_RUN to toastOnRun,
                ConfigFields.F_TOAST_FLUID to toastFluid,
                ConfigFields.F_TOAST_TEMPLATE to toastTemplate,
                ConfigFields.F_LANGUAGE to language,
                ConfigFields.F_PALETTE to palette,
                ConfigFields.F_THEME to theme,
                ConfigFields.F_COLOR_OS_API to colorOsApi,
                ConfigFields.F_MINI_WINDOW_ANIM to miniWindowAnim,
                ConfigFields.F_FOLLOW_DISPLAY_SIZE to followDisplaySize,
                ConfigFields.F_PAGE_SCALE to pageScale,
                ConfigFields.F_COMBO_WINDOW_MS to comboWindowMs,
                ConfigFields.F_COMBO_HOLD_MIN_MS to comboHoldMinMs,
                ConfigFields.F_COMBO_SCENE_ON_STARTER to comboSceneOnStarter,
                ConfigFields.F_ICON_TINT_SEED to iconTintSeed,
                ConfigFields.F_SHOW_COMMON_COMMANDS to showCommonCommands,
                //  公共设置里的「单击」三项：老文件缺项按默认读（见各自字段的 KDoc）
                ConfigFields.F_GLOBAL_DOUBLE_CLICK_MS to (doubleClickMs ?: KeyConfig.DEFAULT_DOUBLE_CLICK_MS),
                ConfigFields.F_GLOBAL_WAIT_MULTI_WINDOW to (waitMultiWindow ?: false),
                ConfigFields.F_GLOBAL_MULTI_WAIT_MS to (multiWaitMs ?: KeyConfig.FOLLOW_DOUBLE_CLICK_MS),
            ),
        )

    companion object {
        fun from(g: GlobalConfig): GlobalDto =
            GlobalDto(
                enabled = g.enabled,
                vibrate = g.vibrate,
                vibrateStrength = g.vibrateStrength,
                toastOnRun = g.toastOnRun,
                toastFluid = g.toastFluid,
                toastTemplate = g.toastTemplate,
                language = g.language,
                palette = g.palette,
                theme = g.theme,
                colorOsApi = g.colorOsApi,
                miniWindowAnim = g.miniWindowAnim,
                followDisplaySize = g.followDisplaySize,
                pageScale = g.pageScale,
                comboWindowMs = g.comboWindowMs,
                comboHoldMinMs = g.comboHoldMinMs,
                comboSceneOnStarter = g.comboSceneOnStarter,
                iconTintSeed = g.iconTintSeed,
                showCommonCommands = g.showCommonCommands,
                //  公共设置里的「单击」三项（v65 起是活字段，不再是 `null`）
                doubleClickMs = g.doubleClickMs,
                waitMultiWindow = g.waitMultiWindow,
                multiWaitMs = g.multiWaitMs,
                //  `null` 原样留着（= 用默认那几条），不要在这里换成空表 —— 见这个字段的说明
                commonCommands = g.commonCommands.map { CommonCommandDto.of(it) },
            )
    }
}

/** 「常用命令」清单的一项（`global.common_commands`）。 */
@Serializable
internal data class CommonCommandDto(
    val token: String = "",
    val name: String = "",
    /**
     * 这一条要不要出现在按键绑定页那一段（每条各管各的，没有总闸）。
     *
     *  老配置里这一位可能被写成 `true`（v59 那版曾把写回固定成 `true`），读回来就等于
     *  「全部显示」—— 与那版的行为一致，不会让用户的清单突然少几条。
     */
    @SerialName("show_in_binding")
    val showInBinding: Boolean = true,
    /**
     * 这一条换了哪颗图标（`FluidIcons` 的 id）—— 空串 = 用它本来那颗。
     *
     *  纯界面选择：常用命令只出现在模块自己的界面里，画的是 `ImageVector`，不落文件。
     */
    val icon: String = "",
) {
    /**
     * 读成模型；**解析不出来的写法直接丢**（与命令页同一条尺子：`Action.parse` 认不出的东西
     * 存进去也只会在执行时变成 `none`）。
     *
     *  不能用 `Action.hasSupportedFormat` 当判据：那一条**故意**不含 `cmd:`（命令正文不引用别的
     *  命令），而这张表的默认内容恰恰全是 `cmd:common.*`。
     */
    fun toCommonCommand(): CommonCommand? {
        val text = token.trim()
        if (text.isEmpty() || Action.parse(text) is Action.None) return null
        return CommonCommand(
            token = text,
            name = name.trim(),
            showInBinding = showInBinding,
            icon = icon.trim().lowercase(),
        )
    }

    companion object {
        fun of(cmd: CommonCommand): CommonCommandDto =
            CommonCommandDto(
                token = cmd.token,
                name = cmd.name,
                showInBinding = cmd.showInBinding,
                icon = cmd.icon,
            )
    }
}

/**
 * 一整张清单读成模型。
 *
 *  坏条目丢掉并报一行 —— 不声不响消失的清单项最难查（用户在页面上看不到某一条，
 *  却没有任何线索）。
 */
private fun List<CommonCommandDto>.parsed(): List<CommonCommand> {
    val parsed = mapNotNull { it.toCommonCommand() }
    if (parsed.size != size) {
        XLog.w("配置：${size - parsed.size} 条常用命令认不出（已丢弃）")
    }
    return parsed
}

/** `custom_keys` 的一项。 */
@Serializable
internal data class CustomKeyDto(
    val id: String = "",
    val name: String = "",
    val code: Int = 0,
) {
    /** 没有 id 或没有正键码的读不懂（前者当不了配置键名，后者认领不了）。 */
    fun toKeyId(): KeyId? {
        if (id.isEmpty() || code <= 0) return null
        return KeyId.custom(id, name.ifEmpty { id }, code)
    }
}

/** 按字段表钳一个整数字段的取值范围。 */
private fun clamp(
    field: ConfigFields.Field,
    value: Int,
): Int {
    val kind = field.kind as? ConfigFields.Kind.Number ?: return value
    return value.coerceIn(kind.min, kind.max)
}

/**
 * `extra_clicks` 里那一位连击次数认不认得出来 —— 必须是 **≥ 3 的整数**（第 3 击起才是额外档）。
 *
 *  收在一个函数里：读的时候（[KeyDto.toKeyConfig]）与记账的时候（[ConfigDto.toConfig]）
 *  必须用同一套判据，两处各写一遍会演进成「日志说丢了一条、实际丢的是另一条」。
 */
private fun extraClickCount(raw: String): Int? = raw.toIntOrNull()?.takeIf { it >= 3 }
