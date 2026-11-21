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
import com.lookie.opluskey.core.param.ComboParams
import com.lookie.opluskey.core.param.KeyParams
import com.lookie.opluskey.core.param.Params
import com.lookie.opluskey.core.param.encodeFrom
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
        //  **认不出的档位 id 也在这里记账**（真正的丢弃在 [KeyDto.toKeyConfig] 的 `mapNotNull` 里）：
        //  按**整份文件**聚合一次计数，不逐条刷。
        val droppedTiers = keys.values.sumOf { key -> key.actions.keys.count { GestureTrigger.ofId(it) == null } }
        if (droppedTiers > 0) {
            XLog.w("配置：$droppedTiers 个认不出的档位（已丢弃）")
        }
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
                val key = (keys[id.id] ?: KeyDto()).toKeyConfig(id)
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
    /**
     * 键 id（`plus` / `custom:名字` …）。
     *
     *  老格式里这一格不存在（id 是外层表的键）；新格式写出来，读的时候以它为准。
     */
    val id: String? = null,
    //  **默认停用**：与改造前那一格逐字相同 —— 文件里缺这一格的键按「不接管」读，
    //  写成 true 会让「缺一段的文件」静默多接管一颗键。
    val enabled: Boolean = false,
    /**
     * 档位 id → 动作 token（`single` / `click3` / `click_then_long` …）—— **缺一档 = 无操作**。
     *
     *  键就是 `GestureTrigger.id`，所以加一档手势**不用改这个 DTO**：表里多一行，这里多一个键。
     */
    val actions: Map<String, String> = emptyMap(),
    /**
     * `Param.id` → 值 —— 设置项（延迟、开关、独立设置十格、注入侧派生位）全在这里。
     *
     *  写出时按 `KeyParams.ALL` **全量写**（含默认值），见 `ConfigCodec.digestMatches` 的口径。
     *  加一项设置**不用改这个 DTO**：注册表里多一行，这里多一个键。
     */
    val params: Map<String, String> = emptyMap(),
    val useOwnSettings: Boolean = false,
    /** 系统场景那张表 —— 结构沿用 v36 之后那一份（一块 = 条件 + 各档动作）。 */
    val scenes: List<SceneRuleDto> = emptyList(),
) {
    /**
     * 线格式 → 运行时。
     *
     *  认不出的档位 id 单独丢掉（与场景块对认不出的档位同一个口径）：丢掉只影响那一档，
     *  整份丢掉会把用户其余几档一起抹了。
     */
    fun toKeyConfig(
        id: KeyId,
        scenes: List<SceneRule> = this.scenes.mapNotNull { it.toScene() },
    ): KeyConfig =
        KeyConfig(
            id = id,
            enabled = enabled,
            actions =
                actions.entries
                    .mapNotNull { (triggerId, token) ->
                        GestureTrigger.ofId(triggerId)?.let { it to Action.parse(token) }
                    }.toMap(),
            params = Params.of(params).normalized(KeyParams.ALL),
            scenes = scenes,
            useOwnSettings = useOwnSettings,
        )

    companion object {
        fun from(key: KeyConfig): KeyDto =
            KeyDto(
                id = key.id.id,
                enabled = key.enabled,
                actions = key.actions.entries.associate { (trigger, action) -> trigger.id to action.token },
                //  **全量写**（含默认值）：写出必须能被同一份 DTO 逐字节重编码复现，
                //  否则「按当前 DTO 重新编码算 md5」会假红，存量配置被判读不懂。
                params = KeyParams.ALL.associate { it.id to it.encodeFrom(key.params) },
                useOwnSettings = key.useOwnSettings,
                scenes = key.scenes.map(SceneRuleDto::of),
            )
    }
}

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
internal fun mergeScenes(
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
internal fun mergeLegacy(rules: List<StateRuleDto>): List<SceneRule> {
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
    val keys: List<String> = emptyList(),
    val enabled: Boolean = true,
    /** 档位 id → 动作 token —— 与 `KeyDto.actions` 同一套键（`Click(n)` 两边同编号）。 */
    val actions: Map<String, String> = emptyMap(),
    /** `Param.id` → 值（`ComboParams.ALL`），全量写。 */
    val params: Map<String, String> = emptyMap(),
    val useOwnSettings: Boolean = false,
    val scenes: List<SceneRuleDto> = emptyList(),
) {
    /**
     * 线格式 → 一档组合键；id 空、或成员不足两颗的读不懂（返回 `null`）。
     *
     *  前者当不了身份（界面选中哪一行、删哪一行都靠它），后者判不出来（一颗不叫组合）。
     *  成员去重之后按 [ComboKey.MAX_JUDGE_KEYS] 截断 —— 手改文件写进几十颗也不该让它进来。
     */
    fun toCombo(): ComboKey? {
        val members = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (id.isEmpty() || members.size < ComboKey.MIN_KEYS) return null
        val raw = Params.of(params)
        val resolved = raw[ComboParams.MODE]
        //  ## 旧编号 2（= 先按住第二颗）的兜底
        //
        //  新格式写的是归一后的 1，但手改 / 降级写进来的 2 仍要认：读到 2 就把第二颗提到首位、
        //  模式归一成 [ComboKey.MODE_HOLD]，于是 `[A, B]` + 2 与 `[B, A]` + 1 是同一份数据。
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
            actions =
                actions.entries
                    .mapNotNull { (triggerId, token) ->
                        GestureTrigger.ofId(triggerId)?.let { it to Action.parse(token) }
                    }.toMap(),
            params =
                raw
                    .normalized(ComboParams.ALL)
                    .with(ComboParams.MODE, ComboKey.modeFor(resolved, ordered.size)),
            scenes = scenes.mapNotNull { it.toScene() },
            useOwnSettings = useOwnSettings,
        )
    }

    companion object {
        fun of(combo: ComboKey): ComboDto =
            ComboDto(
                id = combo.id,
                keys = combo.keys,
                enabled = combo.enabled,
                actions = combo.actions.entries.associate { (trigger, action) -> trigger.id to action.token },
                params = ComboParams.ALL.associate { it.id to it.encodeFrom(combo.params) },
                useOwnSettings = combo.useOwnSettings,
                scenes = combo.scenes.map(SceneRuleDto::of),
            )
    }
}

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
     * 公共设置里的**「单击和多击和长按延迟」**（`GlobalConfig.clickThenLongPressMs`）。
     *
     *  `null` = 老文件没有这一项 ⇒ 出厂 [KeyConfig.DEFAULT_LONG_PRESS_MS]（与这一位出现之前一致：
     *  那时三档「后长按」用的就是每颗键出厂那一档）。
     */
    @SerialName("global_click_then_long_press_ms")
    val clickThenLongPressMs: Int? = null,
    /**
     * 公共设置里的**组合成员「连击后长按延迟」**（`GlobalConfig.comboMemberClickThenLongPressMs`）。
     *
     *  `null` = 老文件没有这一项 ⇒ 出厂 [KeyConfig.DEFAULT_LONG_PRESS_MS]（与这一位出现之前一致：
     *  那时成员那三档「后长按」用的就是出厂那一档）。
     */
    @SerialName("global_combo_member_then_long_press_ms")
    val comboMemberThenLongPressMs: Int? = null,
    /**
     * **界面振动反馈**（`GlobalConfig.uiVibrate`）。
     *
     *  `null` = 老文件没有这一项 ⇒ 开（与这一位出现之前一致：界面那一路一直有触觉）。
     */
    @SerialName("global_ui_vibrate")
    val uiVibrate: Boolean? = null,
    /**
     * 组合键**四格窗口**（`GlobalConfig` 那四位，毫秒）—— 等待 / 按住 × 固定窗口起点 开 / 关。
     *
     *  老文件没有这四位：由 [mergedComboWindowMs] 把 v73 那一个数铺到前三格，
     *  第四格（按住组合、起点关）用出厂 [GlobalConfig.DEFAULT_COMBO_PAIR_MS]（那一格原先是写死的常量）。
     */
    @SerialName("combo_wait_window_ms")
    val comboWaitWindowMs: Int? = null,
    @SerialName("combo_wait_pair_ms")
    val comboWaitPairMs: Int? = null,
    @SerialName("combo_hold_window_ms")
    val comboHoldWindowMs: Int? = null,
    @SerialName("combo_hold_follower_ms")
    val comboHoldFollowerMs: Int? = null,
    /**
     * **使用预测返回手势**（`GlobalConfig.predictiveBack`）。
     *
     *  `null` = 老文件没有这一项 ⇒ 开（这一位出现之前一直走预测返回）。
     */
    @SerialName("global_predictive_back")
    val predictiveBack: Boolean? = null,
    /**
     * **显示更详细的按键设置**（`GlobalConfig.detailedKeySettings`）。
     *
     *  `null` = 老文件没有这一项 ⇒ 关（与这一位出现之前一致：按键设置页只画常用项）。
     */
    @SerialName("global_detailed_key_settings")
    val detailedKeySettings: Boolean? = null,
    /**
     * **模块总闸**。`null` = 这份文件是拆开之前写的 —— 那时「主开关」就是 `keys.plus.enabled`，
     * 迁移见 [ConfigDto.toConfig]；新写出来的文件一定是具体值。
     */
    val enabled: Boolean? = null,
    val vibrate: Boolean = false,
    /** 振动强度（三档）—— 老文件没有这一项，取值会退回字段表的默认值（中档）。 */
    @SerialName("vibrate_strength")
    val vibrateStrength: Int = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
    /** 无操作时振动 —— 老文件没有这一项，退回默认（关）。 */
    @SerialName("vibrate_on_none")
    val vibrateOnNone: Boolean = GlobalConfig.DEFAULT_VIBRATE_ON_NONE,
    val toastOnRun: Boolean = GlobalConfig.DEFAULT_TOAST_ON_RUN,
    /** 执行时提示走流体云 —— 老文件没有这一项，退回默认（开，见 v63 那次语义迁移）。 */
    @SerialName("toast_fluid")
    val toastFluid: Boolean = GlobalConfig.DEFAULT_TOAST_FLUID,
    /** 执行时提示走通知 —— 老文件没有这一项，退回默认（关；四档由 `NoticeMode.of` 派生）。 */
    @SerialName("toast_notify")
    val toastNotify: Boolean = GlobalConfig.DEFAULT_TOAST_NOTIFY,
    /** 「执行时提示」的文案模板（见 `GlobalConfig.toastTemplate`）—— 老文件没有这一项，退回默认那句。 */
    @SerialName("toast_template")
    val toastTemplate: String = GlobalConfig.DEFAULT_TOAST_TEMPLATE,
    /** 多条命令逐条提示 —— 老文件没有这一项，退回默认（关）。 */
    @SerialName("step_notice")
    val stepNotice: Boolean = GlobalConfig.DEFAULT_STEP_NOTICE,
    val language: String = GlobalConfig.DEFAULT_LANGUAGE,
    /** 系统配色的 id（见 `GlobalConfig.palette`）—— 老文件没有这一项，取值退回默认档。 */
    val palette: String = GlobalConfig.DEFAULT_PALETTE,
    /** 界面颜色（见 `GlobalConfig.theme`）—— 老文件没有这一项，退回「跟随系统」。 */
    val theme: String = GlobalConfig.DEFAULT_THEME,
    /**
     * 使用 Monet 配色（见 `GlobalConfig.monetColors`）—— 老文件没有这一项，退回默认（关）。
     *
     *  v89 及以前这一位压在 `theme` 里（`monet` / `light_monet` / `dark_monet` 三个值）；
     *  那三个值读进来时由 [legacyThemeCode] / [legacyThemeMonet] 翻成「三档 + 这一位」。
     */
    @SerialName("monet_colors")
    val monetColors: Boolean = GlobalConfig.DEFAULT_MONET_COLORS,
    val colorOsApi: Boolean = GlobalConfig.DEFAULT_COLOR_OS_API,
    val miniWindowAnim: Boolean = GlobalConfig.DEFAULT_MINI_WINDOW_ANIM,
    /** 界面缩放（百分比）—— 老文件没有这一项，退回出厂值 100%。 */
    @SerialName("page_scale")
    val pageScale: Int = GlobalConfig.DEFAULT_PAGE_SCALE,
    /**
     * **遗留（v73）**：那一个「等待窗口」（毫秒）—— 现在只在读老文件时当四格窗口的铺底值。
     *
     *  写出去永远是出厂那个数（`DEFAULT_COMBO_WINDOW_MS`，新文件不再用它）；读回来时由
     *  [legacyWindowMs] 交给 [comboWaitWindowMs] 那三位。
     */
    @SerialName("combo_window_ms")
    val comboWindowMs: Int = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
    /** 按住那两档里起点键要先按住多久（毫秒）—— 老文件没有这一项，退回出厂值 100。 */
    @SerialName("combo_hold_min_ms")
    val comboHoldMinMs: Int = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
    /** 按住那两档的场景在什么时候定下来（见 `GlobalConfig.comboSceneOnStarter`）—— 只落盘。 */
    val comboSceneOnStarter: Boolean = GlobalConfig.DEFAULT_COMBO_SCENE_ON_STARTER,
    /**
     * 等待组合的**固定窗口起点**（见 `GlobalConfig.comboFixedWindowStart`）。
     *
     *  **两列都走**：落盘这一份之外还进配置广播（`ConfigFields.F_COMBO_FIXED_WINDOW_START`）——
     *  判定在注入侧，它读不到配置文件。
     */
    @SerialName("combo_fixed_window_start")
    val comboFixedWindowStart: Boolean = GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START,
    /**
     * 按住组合的**固定窗口起点**（见 `GlobalConfig.comboHoldFixedWindowStart`）—— **另一个变量**。
     *
     *  与上面那一位同一条：两列都走，判据在注入侧（`ComboDetector` 判按住档的成员接收窗口）。
     *  老文件没有这一项 ⇒ 默认关（逐次等满），与这一位出现之前一致。
     */
    @SerialName("combo_hold_fixed_window_start")
    val comboHoldFixedWindowStart: Boolean = GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START,
    /**
     * 按住组合的首键松手后仍执行它自己的长按（见 `GlobalConfig.comboStarterKeepLong`）。
     *
     *  **两列都走**：落盘这一份之外还进配置广播（`ConfigFields.F_COMBO_STARTER_KEEP_LONG`
     *  有 extra 名）—— 判定在注入侧，它读不到配置文件。
     */
    @SerialName("combo_starter_keep_long")
    val comboStarterKeepLong: Boolean = GlobalConfig.DEFAULT_COMBO_STARTER_KEEP_LONG,
    /** 随机图标配色的种子（`0` = 关）—— 老文件没有这一项，退回默认（关）。 */
    @SerialName("icon_tint_seed")
    val iconTintSeed: Int = GlobalConfig.DEFAULT_ICON_TINT_SEED,
    /** 按键绑定页显不显示「常用命令」那一段 —— 老文件没有这一项，退回默认（显示）。 */
    @SerialName("show_common_commands")
    val showCommonCommands: Boolean = GlobalConfig.DEFAULT_SHOW_COMMON_COMMANDS,
    /** 「减少界面图标」—— 老文件没有这一项，退回默认（画）。 */
    @SerialName("reduce_icons")
    val reduceIcons: Boolean = GlobalConfig.DEFAULT_REDUCE_ICONS,
    /**
     * 组合两列的排列次序（文本 id，取值见 `GlobalConfig.SORT_BY_CREATED` 那一组）。
     *
     *  **跟着配置文件走**：换一份配置就是换一套显示偏好；老文件没有这一项，退回「不排序」。
     */
    @SerialName("wait_combo_sort")
    val waitComboSort: String = GlobalConfig.DEFAULT_COMBO_SORT,
    @SerialName("hold_combo_sort")
    val holdComboSort: String = GlobalConfig.DEFAULT_COMBO_SORT,
    /**
     * 「常用命令」那张用户自己的清单 —— **老文件没有这一项**（`null`）。
     *
     *  `null` 与空表的区别要留住：前者是「从没动过，用模块自带那几条」，后者是「进过那一页、
     * 把它们全删了」。所以这里不写成 `emptyList()`。
     */
    @SerialName("common_commands")
    val commonCommands: List<CommonCommandDto> = emptyList(),
) {
    /**
     * v73 及以前那**一个**等待窗口（[comboWindowMs]）—— 四格窗口缺项时用它铺底。
     *
     *  老文件没有这四位：前三格（等待 / 等待对 / 按住）都铺成这一个数，
     *  第四格（按住、起点关）用出厂 [GlobalConfig.DEFAULT_COMBO_PAIR_MS] —— 那一格原先是写死的常量。
     */
    private val legacyWindowMs: Int
        get() = comboWindowMs

    fun toValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                // 老文件这里给的是 null，取值会退回字段表的默认值（false）；真正的迁移在
                //  `ConfigDto.toConfig` 里做 —— 它还要顺手改 `keys.plus` 那一位
                ConfigFields.F_MODULE_ENABLED to (enabled ?: GlobalConfig.DEFAULT_ENABLED),
                ConfigFields.F_VIBRATE to vibrate,
                ConfigFields.F_VIBRATE_STRENGTH to vibrateStrength,
                ConfigFields.F_VIBRATE_ON_NONE to vibrateOnNone,
                ConfigFields.F_TOAST_ON_RUN to toastOnRun,
                ConfigFields.F_TOAST_FLUID to toastFluid,
                ConfigFields.F_TOAST_NOTIFY to toastNotify,
                ConfigFields.F_TOAST_TEMPLATE to toastTemplate,
                ConfigFields.F_STEP_NOTICE to stepNotice,
                ConfigFields.F_LANGUAGE to language,
                ConfigFields.F_PALETTE to palette,
                ConfigFields.F_THEME to legacyThemeCode(theme),
                ConfigFields.F_MONET_COLORS to (monetColors || legacyThemeMonet(theme)),
                ConfigFields.F_COLOR_OS_API to colorOsApi,
                ConfigFields.F_MINI_WINDOW_ANIM to miniWindowAnim,
                ConfigFields.F_PAGE_SCALE to pageScale,
                ConfigFields.F_COMBO_WAIT_WINDOW_MS to (comboWaitWindowMs ?: legacyWindowMs),
                ConfigFields.F_COMBO_WAIT_PAIR_MS to (comboWaitPairMs ?: legacyWindowMs),
                ConfigFields.F_COMBO_HOLD_WINDOW_MS to (comboHoldWindowMs ?: legacyWindowMs),
                ConfigFields.F_COMBO_HOLD_FOLLOWER_MS to (comboHoldFollowerMs ?: GlobalConfig.DEFAULT_COMBO_PAIR_MS),
                ConfigFields.F_COMBO_HOLD_MIN_MS to comboHoldMinMs,
                ConfigFields.F_COMBO_SCENE_ON_STARTER to comboSceneOnStarter,
                ConfigFields.F_COMBO_FIXED_WINDOW_START to comboFixedWindowStart,
                ConfigFields.F_COMBO_HOLD_FIXED_WINDOW_START to comboHoldFixedWindowStart,
                ConfigFields.F_COMBO_STARTER_KEEP_LONG to comboStarterKeepLong,
                ConfigFields.F_ICON_TINT_SEED to iconTintSeed,
                ConfigFields.F_SHOW_COMMON_COMMANDS to showCommonCommands,
                ConfigFields.F_REDUCE_ICONS to reduceIcons,
                ConfigFields.F_WAIT_COMBO_SORT to waitComboSort,
                ConfigFields.F_HOLD_COMBO_SORT to holdComboSort,
                //  公共设置里的「单击」三项：老文件缺项按默认读（见各自字段的 KDoc）
                ConfigFields.F_GLOBAL_DOUBLE_CLICK_MS to (doubleClickMs ?: KeyConfig.DEFAULT_DOUBLE_CLICK_MS),
                ConfigFields.F_GLOBAL_WAIT_MULTI_WINDOW to (waitMultiWindow ?: true),
                ConfigFields.F_GLOBAL_MULTI_WAIT_MS to (multiWaitMs ?: KeyConfig.FOLLOW_DOUBLE_CLICK_MS),
                ConfigFields.F_GLOBAL_CTL_PRESS_MS to (clickThenLongPressMs ?: KeyConfig.DEFAULT_LONG_PRESS_MS),
                ConfigFields.F_GLOBAL_COMBO_MEMBER_CTL_PRESS_MS to
                    (comboMemberThenLongPressMs ?: KeyConfig.DEFAULT_LONG_PRESS_MS),
                ConfigFields.F_GLOBAL_UI_VIBRATE to (uiVibrate ?: true),
                ConfigFields.F_GLOBAL_PREDICTIVE_BACK to (predictiveBack ?: true),
                ConfigFields.F_GLOBAL_DETAILED_KEY_SETTINGS to (detailedKeySettings ?: false),
            ),
        )

    companion object {
        fun from(g: GlobalConfig): GlobalDto =
            GlobalDto(
                enabled = g.enabled,
                vibrate = g.vibrate,
                vibrateStrength = g.vibrateStrength,
                vibrateOnNone = g.vibrateOnNone,
                toastOnRun = g.toastOnRun,
                toastFluid = g.toastFluid,
                toastNotify = g.toastNotify,
                toastTemplate = g.toastTemplate,
                stepNotice = g.stepNotice,
                language = g.language,
                palette = g.palette,
                theme = g.theme,
                monetColors = g.monetColors,
                colorOsApi = g.colorOsApi,
                miniWindowAnim = g.miniWindowAnim,
                pageScale = g.pageScale,
                comboWaitWindowMs = g.comboWaitWindowMs,
                comboWaitPairMs = g.comboWaitPairMs,
                comboHoldWindowMs = g.comboHoldWindowMs,
                comboHoldFollowerMs = g.comboHoldFollowerMs,
                comboHoldMinMs = g.comboHoldMinMs,
                comboSceneOnStarter = g.comboSceneOnStarter,
                comboFixedWindowStart = g.comboFixedWindowStart,
                comboHoldFixedWindowStart = g.comboHoldFixedWindowStart,
                comboStarterKeepLong = g.comboStarterKeepLong,
                iconTintSeed = g.iconTintSeed,
                showCommonCommands = g.showCommonCommands,
                reduceIcons = g.reduceIcons,
                waitComboSort = g.waitComboSort,
                holdComboSort = g.holdComboSort,
                //  公共设置里的「单击」三项（v65 起是活字段，不再是 `null`）
                doubleClickMs = g.doubleClickMs,
                waitMultiWindow = g.waitMultiWindow,
                multiWaitMs = g.multiWaitMs,
                clickThenLongPressMs = g.clickThenLongPressMs,
                comboMemberThenLongPressMs = g.comboMemberClickThenLongPressMs,
                uiVibrate = g.uiVibrate,
                predictiveBack = g.predictiveBack,
                detailedKeySettings = g.detailedKeySettings,
                //  `null` 原样留着（= 用默认那几条），不要在这里换成空表 —— 见这个字段的说明
                commonCommands = g.commonCommands.map { CommonCommandDto.of(it) },
            )
    }
}

/**
 * v89 及以前那三个「带 Monet」的旧主题值 —— 那时深浅与配色压在同一个字符串里。
 *
 *  v90 把配色拆成独立一格（`GlobalConfig.monetColors`），于是读老文件时要翻成「三档 + 这一位」：
 *  不翻的话那三个值在新枚举里认不出、整份配置会掉回「跟随系统 + 不用配色」。
 */
private const val LEGACY_THEME_MONET = "monet"
private const val LEGACY_THEME_LIGHT_MONET = "light_monet"
private const val LEGACY_THEME_DARK_MONET = "dark_monet"

/** 老主题值 → 三档里的那一档（不认识的、以及三档本身的，原样返回）。 */
internal fun legacyThemeCode(code: String): String =
    when (code) {
        LEGACY_THEME_MONET -> GlobalConfig.THEME_AUTO
        LEGACY_THEME_LIGHT_MONET -> GlobalConfig.THEME_LIGHT
        LEGACY_THEME_DARK_MONET -> GlobalConfig.THEME_DARK
        else -> code
    }

/** 老主题值自己带的那一位「用 Monet 配色」—— 只有那三个旧值会返回 `true`。 */
internal fun legacyThemeMonet(code: String): Boolean =
    code == LEGACY_THEME_MONET || code == LEGACY_THEME_LIGHT_MONET || code == LEGACY_THEME_DARK_MONET

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
internal fun List<CommonCommandDto>.parsed(): List<CommonCommand> {
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
internal fun clamp(
    field: ConfigFields.Field,
    value: Int,
): Int {
    val kind = field.kind as? ConfigFields.Kind.Number ?: return value
    return value.coerceIn(kind.min, kind.max)
}

/**
 * 组合四格窗口里**某一格**的判读：这一格自己写过就用它，否则拿老文件那一个数铺底。
 *
 *  @param own 这一格的值（`0` = 跟随公共设置）
 *  @param legacy 老文件里的那一个数（v73 的 `window_ms`、v72 更早的 `fixed_window_ms`，按优先级）
 */
internal fun clampOrFollow(
    own: Int,
    vararg legacy: Int,
): Int {
    val raw = if (own > 0) own else legacy.firstOrNull { it > 0 } ?: return ComboKey.WINDOW_FOLLOW_GLOBAL
    return clamp(ConfigFields.F_COMBO_WAIT_WINDOW_MS, raw)
}

/**
 * `extra_clicks` 里那一位连击次数认不认得出来 —— 必须是 **≥ 3 的整数**（第 3 击起才是额外档）。
 *
 *  收在一个函数里：读的时候（[KeyDto.toKeyConfig]）与记账的时候（[ConfigDto.toConfig]）
 *  必须用同一套判据，两处各写一遍会演进成「日志说丢了一条、实际丢的是另一条」。
 */
internal fun extraClickCount(raw: String): Int? = raw.toIntOrNull()?.takeIf { it >= 3 }
