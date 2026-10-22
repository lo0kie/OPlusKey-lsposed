package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.ConfigFields
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.StateRule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        // 老文件把「单击延迟 / 忽视双击」放在 `global` 里：逐键字段缺失时拿它兜底（迁移用）
        val legacyDouble = global.doubleClickMs ?: KeyConfig.DEFAULT_DOUBLE_CLICK_MS
        val legacyInstant = global.instantSingle ?: KeyConfig.DEFAULT_INSTANT_SINGLE
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
                val key = (keys[id.id] ?: KeyDto()).toKeyConfig(id, legacyDouble, legacyInstant)
                if (legacyMaster && id.id == KeyId.PLUS.id) key.copy(enabled = true) else key
            }
        val globalConfig = GlobalConfig.ofFields(global.toValues())
        val parsedCombos = combos.mapNotNull { it.toCombo() }
        if (parsedCombos.size != combos.size) {
            //  与自定义键同一条：坏条目不能无声消失（组合键丢了 = 那一组按键整组失效）
            XLog.w("配置：${combos.size - parsedCombos.size} 个组合键认不出（已丢弃）")
        }
        return Config.ofFields(
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
     * 「单击延迟」与「忽视无操作双击手势」—— **每颗键各一份**（以前在 `global` 段里）。
     *
     *  `null` = 这份文件里没写（老文件 / 手写）：读的时候退回 `global` 那份，见 [ConfigDto.toConfig]。
     *  新写出来的文件一定是具体值。
     */
    val doubleClickMs: Int? = null,
    val instantSingle: Boolean? = null,
    /**
     * **状态分支**（`KeyConfig.rules`）—— 只走落盘：它是列表，进不了 `ConfigFields` 的字段表。
     *
     *  老文件没有这一段 ⇒ 空表 ⇒ 行为与这一档出现之前完全一致。
     */
    val rules: List<StateRuleDto> = emptyList(),
) {
    /** 按 [ConfigFields] 那张表把取值钳进各自的上下限 —— 上下限只定义在表里。 */
    fun toValues(
        legacyDoubleClickMs: Int,
        legacyInstantSingle: Boolean,
    ): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_ENABLED to enabled,
                ConfigFields.F_SINGLE to single,
                ConfigFields.F_DOUBLE to double,
                ConfigFields.F_LONG to long,
                ConfigFields.F_LONG_PRESS_MS to clamp(ConfigFields.F_LONG_PRESS_MS, longPressMs),
                ConfigFields.F_LONG_REPEAT to longRepeat,
                ConfigFields.F_REPEAT_INTERVAL_MS to clamp(ConfigFields.F_REPEAT_INTERVAL_MS, longRepeatIntervalMs),
                ConfigFields.F_REPEAT_VIBRATE to longRepeatVibrate,
                ConfigFields.F_DOUBLE_CLICK_MS to
                    clamp(ConfigFields.F_DOUBLE_CLICK_MS, doubleClickMs ?: legacyDoubleClickMs),
                ConfigFields.F_INSTANT_SINGLE to (instantSingle ?: legacyInstantSingle),
            ),
        )

    /** 线格式 → 一颗键的配置（含状态分支）。两个 `legacy*` 是迁移兜底，见 [ConfigDto.toConfig]。 */
    fun toKeyConfig(
        id: KeyId,
        legacyDoubleClickMs: Int,
        legacyInstantSingle: Boolean,
    ): KeyConfig = KeyConfig.ofFields(id, toValues(legacyDoubleClickMs, legacyInstantSingle), rules.mapNotNull { it.toRule() })

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
                doubleClickMs = key.doubleClickMs,
                instantSingle = key.instantSingle,
                rules = key.rules.map { StateRuleDto.of(it) },
            )
    }
}

/** `rules` 的一项 —— 一条状态分支。 */
@Serializable
internal data class StateRuleDto(
    /** 手势取 `Gesture.id`（`single` / `double` / `long`），不是枚举名。 */
    val gesture: String = "",
    /** 条件写法（`app:包名` / `ringer:vibrate` / `screen_on`），见 `BranchCondition.token`。 */
    val condition: String = "",
    val action: String = Action.NONE,
) {
    /**
     * 线格式 → 一条分支；**认不出就整条丢掉**（返回 `null`）。
     *
     *  为什么不兜成「永远成立」：那样一条写错的分支会静默接管这颗键的所有手势
     *  （见 `BranchCondition.parse`）。丢一条规则只是「这一条不生效」，用户能看得出来。
     */
    fun toRule(): StateRule? {
        val parsedGesture = Gesture.ofId(gesture)
        if (parsedGesture == null) {
            XLog.w("配置：状态分支的手势认不出（丢掉这一条）：$gesture")
            return null
        }
        val parsedCondition = BranchCondition.parse(condition)
        if (parsedCondition == null) {
            XLog.w("配置：状态分支的条件认不出（丢掉这一条）：$condition")
            return null
        }
        return StateRule(parsedGesture, parsedCondition, Action.parse(action))
    }

    companion object {
        /**
         * 一条规则 → 线格式。
         *
         *  条件空着（界面上那一行还没选条件）就写成空串 —— 这种行保存前就被拦了，正常到不了
         *  这里；万一到了，读回来时按「条件认不出」整条丢掉，而不是当成「永远成立」。
         */
        fun of(rule: StateRule): StateRuleDto = StateRuleDto(rule.gesture.id, rule.condition?.token.orEmpty(), rule.action.token)
    }
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
) {
    /**
     * 线格式 → 一档组合键；id 空、或成员不足两颗的读不懂（返回 `null`）。
     *
     *  前者当不了身份（界面上选中哪一行、删哪一行都靠它），后者判不出来（一颗不叫组合）。
     *  成员去重之后按 [ComboKey.MAX_KEYS] 截断 —— 手改文件写进十颗也不该让它进来。
     */
    fun toCombo(): ComboKey? {
        val members = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (id.isEmpty() || members.size < ComboKey.MIN_KEYS) return null
        return ComboKey(
            id = id,
            keys = members.take(ComboKey.MAX_KEYS),
            enabled = enabled,
            single = Action.parse(single),
            long = Action.parse(long),
            // 长按阈值与单键共用同一个区间（`ConfigFields.F_LONG_PRESS_MS`）：它量的是同一件事。
            longPressMs = clamp(ConfigFields.F_LONG_PRESS_MS, longPressMs),
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
            )
    }
}

/** 全局配置。 */
@Serializable
internal data class GlobalDto(
    /**
     * 老文件里「单击延迟 / 忽视无操作双击」在全局这一段 —— 现在归每颗键（[KeyDto]）。
     * 这两个字段只为**迁移**读一次，写出去时是 `null`（新文件不再有它们）。
     */
    val doubleClickMs: Int? = null,
    /**
     * **模块总闸**。`null` = 这份文件是拆开之前写的 —— 那时「主开关」就是 `keys.plus.enabled`，
     * 迁移见 [ConfigDto.toConfig]；新写出来的文件一定是具体值。
     */
    val enabled: Boolean? = null,
    val vibrate: Boolean = false,
    /** 振动强度（三档）—— 老文件没有这一项，取值会退回字段表的默认值（中档）。 */
    @SerialName("vibrate_strength")
    val vibrateStrength: Int = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
    val instantSingle: Boolean? = null,
    val toastOnRun: Boolean = GlobalConfig.DEFAULT_TOAST_ON_RUN,
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
                ConfigFields.F_LANGUAGE to language,
                ConfigFields.F_PALETTE to palette,
                ConfigFields.F_THEME to theme,
                ConfigFields.F_COLOR_OS_API to colorOsApi,
                ConfigFields.F_MINI_WINDOW_ANIM to miniWindowAnim,
                ConfigFields.F_FOLLOW_DISPLAY_SIZE to followDisplaySize,
                ConfigFields.F_PAGE_SCALE to pageScale,
            ),
        )

    companion object {
        fun from(g: GlobalConfig): GlobalDto =
            GlobalDto(
                enabled = g.enabled,
                vibrate = g.vibrate,
                vibrateStrength = g.vibrateStrength,
                toastOnRun = g.toastOnRun,
                language = g.language,
                palette = g.palette,
                theme = g.theme,
                colorOsApi = g.colorOsApi,
                miniWindowAnim = g.miniWindowAnim,
                followDisplaySize = g.followDisplaySize,
                pageScale = g.pageScale,
            )
    }
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
