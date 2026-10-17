package com.lookie.opluskey.core.model

// 配置的数据结构：三种手势、单键配置、全局配置、整份配置。

/** 三种手势。 */
enum class Gesture(
    val id: String,
    val label: String,
) {
    SINGLE("single", "单击"),
    DOUBLE("double", "双击"),
    LONG("long", "长按"),
}

/** 一个键的完整配置。 */
data class KeyConfig(
    val id: KeyId,
    val enabled: Boolean,
    val single: Action,
    val double: Action,
    val long: Action,
    val longPressMs: Int,
    val longRepeat: Boolean,
    val longRepeatIntervalMs: Int,
    /**
     * 长按绑的是不是「多条命令」（`cmd:<名字>` 且命令库里那条是串行命令）。
     * 只由宿主算好、随配置广播带过来（`ConfigFields.LONG_SERIAL_EXTRA`），注入侧不自己算：
     * 算它要读宿主私有目录的命令库，system_server 进不去带 MCS 分类的目录
     * （`docs/device-findings.md` 六之五 / 九之一），读不到就恒 false，闸门失效。
     * 默认 false = 与早先行为一致（配置文件里没有这一位，不回写）。
     */
    val longIsSerial: Boolean = false,
) {
    fun actionOf(gesture: Gesture): Action =
        when (gesture) {
            Gesture.SINGLE -> single
            Gesture.DOUBLE -> double
            Gesture.LONG -> long
        }

    /**
     * 长按绑的是不是「灵感语音」（`oplus:voice_memory`）。
     *
     *  它是唯一**「按下开始、松手结束」**的动作，两处判据必须是同一个口径：
     *   · 判定侧（`inject/input/GestureDetector`）：不排「长按连续触发」—— 连发等于把正在录的那段
     *     掐断重来，系统自己的侧键长按也没有这个语义；
     *   · 执行侧（`host/GestureReceiver`）：收到「松手」（[com.lookie.opluskey.core.wire.ModuleWire.PHASE_RELEASE]）
     *     才去发结束。
     *
     *  写成计算属性、不落成字段：它是 [long] 的投影，落字段就要在 `ConfigFields` 那条链上
     *  多存一份**可以和 [long] 不一致**的真值。
     */
    val longIsVoiceMemory: Boolean
        get() = (long as? Action.Oplus)?.feature == RomProfile.NAME_VOICE_MEMORY

    /**
     * 本对象在每个配置字段上的取值 —— **「字段 → 属性」的映射只有这一处**。
     *
     *  落盘（`ConfigCodec`）与广播（`ConfigBroadcast`）两条通道都按它取值，配合 `ConfigFields`
     *  那张字段表生成读写 —— 于是加一个字段不必再去那两条通道里各写一遍（判据见 `docs/decoupling.md`）。
     */
    fun fieldValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_ENABLED to enabled,
                ConfigFields.F_SINGLE to single.token,
                ConfigFields.F_DOUBLE to double.token,
                ConfigFields.F_LONG to long.token,
                ConfigFields.F_LONG_PRESS_MS to longPressMs,
                ConfigFields.F_LONG_REPEAT to longRepeat,
                ConfigFields.F_REPEAT_INTERVAL_MS to longRepeatIntervalMs,
                ConfigFields.F_LONG_SERIAL to longIsSerial,
            ),
        )

    companion object {
        const val DEFAULT_LONG_PRESS_MS = 500

        /**
         * 长按阈值下限 —— **200ms 已经高于人手一次自然按压（80~150ms）**，所以任何一档下
         * 「正常按一下」都不会被判成长按，单击 / 双击都按得出来。
         *
         * 原来这一档是 100ms（落在自然按压里面）：「稍按久一点就变长按」，单击基本按不出来。
         * 下限抬到 200ms 就是为了堵掉这个组合，用户不必先踩一次才知道。
         */
        const val MIN_LONG_PRESS_MS = 200
        const val MAX_LONG_PRESS_MS = 1000
        const val DEFAULT_REPEAT_INTERVAL_MS = 300
        const val MIN_REPEAT_INTERVAL_MS = 100

        const val MAX_REPEAT_INTERVAL_MS = 3000

        /**
         * 某个键的默认值：**默认不接管**（`enabled = false` ⇒ 拦截路径整条不生效，行为与原生一致）。
         *
         * 注意：单击那档的默认值是「无操作」而不是「交还系统」—— 后者已删（见 `Action` 的注释）。
         * 主开关开着而这一档还没配时，按键被吞掉什么都不做；这也是「还没配」最不容易误读的表现。
         */
        fun defaultFor(id: KeyId) =
            KeyConfig(
                id = id,
                enabled = false,
                single = Action.None,
                double = Action.None,
                long = Action.None,
                longPressMs = DEFAULT_LONG_PRESS_MS,
                longRepeat = false,
                longRepeatIntervalMs = DEFAULT_REPEAT_INTERVAL_MS,
            )

        /**
         * 从取值表还原一个键的配置 —— 与 [KeyConfig.fieldValues] 成对，两条通道共用。
         *
         *  这里**不再钳制区间**：钳制是读取方对「外来文本」做的事（上下限就在
         *  `ConfigFields` 的字段表里），走到这里时值已经收口过。
         */
        fun ofFields(
            id: KeyId,
            v: ConfigFields.Values,
        ): KeyConfig =
            KeyConfig(
                id = id,
                enabled = v.bool(ConfigFields.F_ENABLED),
                single = Action.parse(v.text(ConfigFields.F_SINGLE)),
                double = Action.parse(v.text(ConfigFields.F_DOUBLE)),
                long = Action.parse(v.text(ConfigFields.F_LONG)),
                longPressMs = v.number(ConfigFields.F_LONG_PRESS_MS),
                longRepeat = v.bool(ConfigFields.F_LONG_REPEAT),
                longRepeatIntervalMs = v.number(ConfigFields.F_REPEAT_INTERVAL_MS),
                longIsSerial = v.bool(ConfigFields.F_LONG_SERIAL),
            )
    }
}

/** 全局配置（不属于任何单个键）。 */
data class GlobalConfig(
    val doubleClickMs: Int,
    val vibrate: Boolean,
    /**
     * 双击为 [Action.None] 时抬手立即触发单击，不等 [doubleClickMs] 的双击窗口。
     * 代价：开启后连按两下会变成两次单击（关闭时第二下会被吞掉）。
     */
    val instantSingle: Boolean = DEFAULT_INSTANT_SINGLE,
    /** 执行**任意动作**时弹一条 Toast（内容是动作名；自定义命令取命令名）。 */
    val toastOnRun: Boolean = DEFAULT_TOAST_ON_RUN,
    /** 界面语言（auto = 跟随系统，zh_CN = 简体中文，en = English）。 */
    val language: String = DEFAULT_LANGUAGE,
    /** 使用 ColorOS 专属系统接口（手电筒 / 免打扰 / 响铃模式等）。关闭时走 AOSP 标准接口。 */
    val colorOsApi: Boolean = DEFAULT_COLOR_OS_API,
) {
    /** 本对象在每个配置字段上的取值 —— 与 `KeyConfig.fieldValues` 同一套（见那边的说明）。 */
    fun fieldValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_DOUBLE_CLICK_MS to doubleClickMs,
                ConfigFields.F_VIBRATE to vibrate,
                ConfigFields.F_INSTANT_SINGLE to instantSingle,
                ConfigFields.F_TOAST_ON_RUN to toastOnRun,
                ConfigFields.F_LANGUAGE to language,
                ConfigFields.F_COLOR_OS_API to colorOsApi,
            ),
        )

    companion object {
        const val DEFAULT_DOUBLE_CLICK_MS = 300

        /** 双击窗口下限 —— 100ms 比「两次完整按压 + 抬手」的物理最短时间还短，是给用户留的余量档。 */
        const val MIN_DOUBLE_CLICK_MS = 100
        const val MAX_DOUBLE_CLICK_MS = 1000

        /** [GlobalConfig.instantSingle] 的默认值（不等窗口）。 */
        const val DEFAULT_INSTANT_SINGLE = true

        /** [GlobalConfig.toastOnRun] 的默认值：默认不弹。 */
        const val DEFAULT_TOAST_ON_RUN = false

        /** [GlobalConfig.language] 的默认值：跟随系统。 */
        const val DEFAULT_LANGUAGE = "auto"

        /** [GlobalConfig.colorOsApi] 的默认值：默认开启。 */
        const val DEFAULT_COLOR_OS_API = true

        val DEFAULT =
            GlobalConfig(
                doubleClickMs = DEFAULT_DOUBLE_CLICK_MS,
                vibrate = false,
                colorOsApi = DEFAULT_COLOR_OS_API,
            )

        /** 从取值表还原全局配置 —— 与 [GlobalConfig.fieldValues] 成对，两条通道共用。 */
        fun ofFields(v: ConfigFields.Values): GlobalConfig =
            GlobalConfig(
                doubleClickMs = v.number(ConfigFields.F_DOUBLE_CLICK_MS),
                vibrate = v.bool(ConfigFields.F_VIBRATE),
                instantSingle = v.bool(ConfigFields.F_INSTANT_SINGLE),
                toastOnRun = v.bool(ConfigFields.F_TOAST_ON_RUN),
                language = v.text(ConfigFields.F_LANGUAGE),
                colorOsApi = v.bool(ConfigFields.F_COLOR_OS_API),
            )
    }
}

/** 整份配置。 */
data class Config(
    val keys: Map<KeyId, KeyConfig>,
    val global: GlobalConfig,
    /**
     * 用户是否已经确认过「风险提示与免责声明」—— 只由**本地**产生，决定「启用」之前要不要弹那一次。
     *
     * 存在配置里（而不是界面状态里）是**有意**的：
     *  · 清空配置按 [DEFAULT] 重建 ⇒ 这一位跟着没了 ⇒ 下次启用照样弹（「清空之后同样弹」就是这么来的）；
     *  · 它是**本机这一次的接受行为**，所以导出／导入一律把它丢掉（见 `ConfigBundle`）——
     *    一份文件不该替别人说「我已经接受了风险」。
     */
    val disclaimerAccepted: Boolean = false,
    /**
     * 用户是否已经确认过「自定义命令」那一页的风险提示 —— 与 [disclaimerAccepted] **分开记**。
     *
     *  两者说的是两件事：那一份讲的是「注入系统进程」这件事本身，这一份讲的是
     *  「自定义命令**不做任何检验或保护**，写什么就跑什么」。在首页确认过前者，不等于知道后者，
     *  所以不能共用一位。
     *
     *  同样存在配置里（理由与 [disclaimerAccepted] 一样）：清空配置按 [DEFAULT] 重建
     *  ⇒ 这一位跟着没了 ⇒ 下次进那一页照样弹；导出／导入也一律丢掉（见 `ConfigBundle`）。
     */
    val commandDisclaimerAccepted: Boolean = false,
    /**
     * 「打开应用」那一族的**显示名**：键是绑定的**身份**（`AppTarget.identity` —— `999@包名/类名`、
     * `999@包名/快捷方式id`），值是界面上那个名字（`微信 2` / `扫一扫`）。
     *
     * ## 为什么是单独一张表、不再塞进 token
     *
     *  早先名字是跟在目标后面写进 token 的（`app:999@包名/类名|微信 2`），每个读它的地方都得先
     *  切一刀 —— 漏切一处就退化成包名（真机：「选完快捷方式 / 界面，再进来显示的是包名」）。
     *  名字不是目标的一部分，是**显示信息**，所以单独存。
     *
     * ## 为什么不放进 `ConfigFields` 的字段表
     *
     *  那张表是**扁平**的（一个字段一个值），而且两条通道共用它 —— 这张表是映射，又只有界面
     *  读（执行侧只需要「起哪个组件」），进字段表就等于每次按键都把整张表广播给 `system_server`。
     *  所以它只走落盘这一条路：JSON 里单独一段（`ConfigFields.APP_NAMES_JSON`），
     *  `ConfigCodec` 单独读写。注入侧读到的 `Config` 里这一项恒为空，没人读它。
     *
     *  与应用改名无关：名字只是显示，身份是 `AppTarget.identity`。改过名之后旧名字还在表里，
     *  界面优先用「这次清单里的新名字」（见 `ui/...` 的名字优先级），表只是冷启动时的兜底。
     */
    val appNames: Map<String, String> = emptyMap(),
) {
    fun key(id: KeyId): KeyConfig = keys[id] ?: KeyConfig.defaultFor(id)

    /** 某个目标该显示的名字（按 [AppTarget.identity] 查）；没记过返回 `null`。 */
    fun appName(target: String): String? = appNames[AppTarget.identity(target)]

    /**
     * 记下（或覆盖）一个目标的名字；[name] 为空时**删掉**这一条。
     *
     *  写成「返回新对象」而不是改字段：配置是 `data class`、写盘走 [copy]，改字段那一套在这里
     *  没有先例（`write` 那一层按整份 `Config` 落盘并发广播）。
     */
    fun withAppName(
        target: String,
        name: String?,
    ): Config {
        val id = AppTarget.identity(target)
        return when {
            name.isNullOrBlank() -> if (id in appNames) copy(appNames = appNames - id) else this
            appNames[id] == name -> this
            else -> copy(appNames = appNames + (id to name))
        }
    }

    /** 顶层那两位的取值 —— 与另两段同一套（见 `KeyConfig.fieldValues` 的说明）。 */
    fun fieldValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_DISCLAIMER to disclaimerAccepted,
                ConfigFields.F_COMMAND_DISCLAIMER to commandDisclaimerAccepted,
            ),
        )

    companion object {
        val DEFAULT =
            Config(
                keys = KeyId.entries.associateWith { KeyConfig.defaultFor(it) },
                global = GlobalConfig.DEFAULT,
            )

        /**
         * 拼一份配置 —— 顶层那两位（两个「已确认」标记）从取值表来，与另外两段同一套读法。
         *
         *  [keys] / [global] 由调用方各自按 [KeyConfig.ofFields] / [GlobalConfig.ofFields] 建好。
         */
        fun ofFields(
            keys: Map<KeyId, KeyConfig>,
            global: GlobalConfig,
            v: ConfigFields.Values,
            /** 名字表（`app_names` 那一段）—— 不在字段表里，由 [ConfigCodec] 单独读出来传进来。 */
            appNames: Map<String, String> = emptyMap(),
        ): Config =
            Config(
                keys = keys,
                global = global,
                disclaimerAccepted = v.bool(ConfigFields.F_DISCLAIMER),
                commandDisclaimerAccepted = v.bool(ConfigFields.F_COMMAND_DISCLAIMER),
                appNames = appNames,
            )
    }
}
