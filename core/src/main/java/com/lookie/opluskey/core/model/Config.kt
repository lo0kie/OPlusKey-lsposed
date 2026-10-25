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
    ;

    companion object {
        /**
         * 按 [id] 找手势 —— 配置与广播里存的一直是 id（`single` / `double` / `long`，见
         * `ConfigFields` 的 `<键>_<档>`），不认枚举名。认不出返回 `null`，由调用方丢掉那一条。
         */
        fun ofId(id: String?): Gesture? = entries.firstOrNull { it.id == id }
    }
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
     * 「连续触发时振动」—— 连续触发的每一次重复都振一下。
     *
     *  只在 [longRepeat] 为真时有意义（界面那一行也只在那时出现）。默认 false = 只在长按
     *  被判出来的那一下振一次，重复不振动。
     */
    val longRepeatVibrate: Boolean = false,
    /**
     * 「单击延迟」—— **这颗键自己的**双击窗口（毫秒）。
     *
     *  以前它是 `GlobalConfig` 的一项（所有键共用一份），自定义键出现之后就成了
     *  「改一颗键的判定、另一颗跟着变」—— 用户口径是每颗键各管各的。
     */
    val doubleClickMs: Int = DEFAULT_DOUBLE_CLICK_MS,
    /** 「忽视无操作双击手势」—— **这颗键自己的**判定（同上，原来在全局）。 */
    val instantSingle: Boolean = DEFAULT_INSTANT_SINGLE,
    /**
     * 长按绑的是不是「多条命令」（`cmd:<名字>` 且命令库里那条是串行命令）。
     * 只由宿主算好、随配置广播带过来（`ConfigFields.LONG_SERIAL_EXTRA`），注入侧不自己算：
     * 算它要读宿主私有目录的命令库，system_server 进不去带 MCS 分类的目录
     * （`docs/device-findings.md` 六之五 / 九之一），读不到就恒 false，闸门失效。
     * 默认 false = 与早先行为一致（配置文件里没有这一位，不回写）。
     */
    val longIsSerial: Boolean = false,
    /**
     * **条件执行** —— 这颗键在某种情况下改执行别的动作（[StateRule]），按顺序匹配、
     * 第一条成立的赢（[actionFor]）；一条都不中就走 [actionOf] 那一档本来的绑定。
     *
     *  分叉**不占用绑定**：它只是叠在绑定之上的一张顺序表，所以「加一条条件」与
     *  「这一档绑什么」是两件事，互不影响。
     *
     *  **只走落盘这一列**（与 [Config.appNames] 同一套做法）：它是一张**列表**，塞不进
     *  [ConfigFields] 那种扁平字段表（一格一个值），也不该在每次按键时把整张表广播给
     *  `system_server` —— 注入侧只判手势、不挑动作，它不需要这张表。
     *
     *  空表（默认）= 与没有这一档完全相同。
     */
    val rules: List<StateRule> = emptyList(),
) {
    fun actionOf(gesture: Gesture): Action =
        when (gesture) {
            Gesture.SINGLE -> single
            Gesture.DOUBLE -> double
            Gesture.LONG -> long
        }

    /**
     * 这次手势该执行哪个动作 —— **条件分支的唯一入口**。
     *
     *  先按顺序找第一条成立的分支（[StateRule.appliesTo]），一条都没有就退回 [actionOf]
     *  那一档本来的绑定。执行侧不要自己写这套 `firstOrNull`：条件维度以后会长，判据散在
     *  多处时「某个路径上条件分支不生效」这种 bug 只会在真机上被看见。
     *
     *  一个块可以带**好几条**条件，判定时逐条都要成立（且）—— 那是 [StateRule.appliesTo] 的账，
     *  这里只负责顺序与「第一条赢」。
     *
     *  [matches] 是调用方给的判据（读系统开关的那一层，见 `ConditionProbe`）—— 这一层不认识
     *  任何系统接口。
     */
    fun actionFor(
        gesture: Gesture,
        matches: (BranchCondition) -> Boolean,
    ): Action = rules.firstOrNull { it.appliesTo(gesture, matches) }?.action ?: actionOf(gesture)

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
                ConfigFields.F_REPEAT_VIBRATE to longRepeatVibrate,
                ConfigFields.F_DOUBLE_CLICK_MS to doubleClickMs,
                ConfigFields.F_INSTANT_SINGLE to instantSingle,
                ConfigFields.F_LONG_SERIAL to longIsSerial,
            ),
        )

    companion object {
        /** 「单击延迟」的默认值（毫秒）。 */
        const val DEFAULT_DOUBLE_CLICK_MS = 300

        /** 双击窗口下限 —— 100ms 比「两次完整按压 + 抬手」的物理最短时间还短，是给用户留的余量档。 */
        const val MIN_DOUBLE_CLICK_MS = 100
        const val MAX_DOUBLE_CLICK_MS = 1000

        /** 「忽视无操作双击手势」的默认值（不等窗口）。 */
        const val DEFAULT_INSTANT_SINGLE = true

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

        /** 连发间隔下限 —— 50ms（用户口径）：再快就与「按住不放」的体感脱节，且按键注入本身也要时间。 */
        const val MIN_REPEAT_INTERVAL_MS = 50

        const val MAX_REPEAT_INTERVAL_MS = 3000

        /**
         * 某个键的默认值：**内置键默认接管、自定义键默认不接管**。
         *
         *  「装上模块不改变行为」现在由**模块总闸**负责（`GlobalConfig.enabled` 默认 false），
         *  不再靠这颗键的开关 —— 所以内置的侧键可以默认是开的：用户第一次打开总闸时它立刻可用。
         *  反过来，侧键也默认关着的话，第一次打开总闸的表现是「开了没反应」，得先去绑一个动作
         *  才活（`pick` 会顺手置开）—— 那是把一个初始化细节漏给了用户。
         *
         *  自定义键仍默认关：它们是被**收集**进来的，收集那一步自己置开（`Config.withCustomKey`）；
         *  而「配置里认不出的键」也落在这一档 —— 不接管比接管安全，且删键时
         *  `ConfigChannel` 靠「查不到 ⇒ enabled=false」判出「这一颗刚消失」。
         *
         * 注意：单击那档的默认值是「无操作」而不是「交还系统」—— 后者已删（见 `Action` 的注释）。
         * 总闸开着而这一档还没配时，按键被吞掉什么都不做；这也是「还没配」最不容易误读的表现。
         */
        fun defaultFor(id: KeyId) =
            KeyConfig(
                id = id,
                enabled = !id.isCustom,
                single = Action.None,
                double = Action.None,
                long = Action.None,
                longPressMs = DEFAULT_LONG_PRESS_MS,
                longRepeat = false,
                longRepeatIntervalMs = DEFAULT_REPEAT_INTERVAL_MS,
                longRepeatVibrate = false,
                doubleClickMs = DEFAULT_DOUBLE_CLICK_MS,
                instantSingle = DEFAULT_INSTANT_SINGLE,
            )

        /**
         * 从取值表还原一个键的配置 —— 与 [KeyConfig.fieldValues] 成对，两条通道共用。
         *
         *  这里**不再钳制区间**：钳制是读取方对「外来文本」做的事（上下限就在
         *  `ConfigFields` 的字段表里），走到这里时值已经收口过。
         *
         *  [rules] 不在取值表里（那是列表，不是扁平的一格，见 [KeyConfig.rules]）——
         *  由落盘那一侧单独读出来传进来；广播那条路不传，注入侧拿到的就是空表。
         */
        fun ofFields(
            id: KeyId,
            v: ConfigFields.Values,
            rules: List<StateRule> = emptyList(),
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
                longRepeatVibrate = v.bool(ConfigFields.F_REPEAT_VIBRATE),
                doubleClickMs = v.number(ConfigFields.F_DOUBLE_CLICK_MS),
                instantSingle = v.bool(ConfigFields.F_INSTANT_SINGLE),
                longIsSerial = v.bool(ConfigFields.F_LONG_SERIAL),
                rules = rules,
            )
    }
}

/** 全局配置（不属于任何单个键）。 */
data class GlobalConfig(
    /**
     * **模块总闸**：关掉 ⇒ 一颗键都不接管（首页「启用按键绑定」那一行）。
     *
     *  与 `KeyConfig.enabled`（这一颗键接管不接管）是两件事 —— 拆开之前侧键那一份同时当两个用，
     *  于是「停用侧键」被迫等于「关掉整个模块」（页面上其它键也跟着灰）。判定侧的闸门是
     *  「总闸 **且** 这一颗键」（见 `inject/ConfigChannel.enabledOf`）。
     *
     *  默认关：装上模块不改变任何行为。
     */
    val enabled: Boolean = DEFAULT_ENABLED,
    val vibrate: Boolean,
    /**
     * **振动强度** —— 三档（[VIBRATE_WEAK] / [VIBRATE_MEDIUM] / [VIBRATE_STRONG]）。
     *
     *  只在 [vibrate] 为真时有意义（那一条滑块也只在那时出现）。读它的是宿主进程里的
     *  执行器（`host/GestureExec.haptic`），所以**只走落盘那一列**、不进广播 —— 注入侧不振。
     */
    val vibrateStrength: Int = DEFAULT_VIBRATE_STRENGTH,
    /** 执行**任意动作**时弹一条 Toast（内容是动作名；自定义命令取命令名）。 */
    val toastOnRun: Boolean = DEFAULT_TOAST_ON_RUN,
    /** 界面语言（auto = 跟随系统，zh_CN = 简体中文，en = English）。 */
    val language: String = DEFAULT_LANGUAGE,
    /**
     * **系统配色** —— 页面强调色用哪一套（取值是色板 id，见界面层的色板表）。
     *
     *  这里只存**字符串**、不存枚举：色板清单与它那一族颜色全在界面层
     *  （`ui/model/PagePalette`），而 `:core` 的 classpath 里没有 `:app` —— 共用层不认识
     *  「有哪些色板」，也不该认识（注入侧与配色无关）。
     *
     *  于是手改文件写进一个不认识的 id 时，这一层原样收下、界面侧读不到就落回默认档
     *  （`PagePalette.ofId` 的兜底）—— 不在这里钳，否则每加一档就要改一次 core。
     */
    val palette: String = DEFAULT_PALETTE,
    /**
     * **界面颜色** —— 跟随系统 / 固定浅色 / 固定深色（取值见 [THEME_AUTO] 那一组）。
     *
     *  与 [palette] 同一套：这里只存字符串，三档的枚举在界面层（`ui/model/AppTheme`）。
     *
     *  它决定的是页面与**宿主那条状态栏 / 顶栏**一起的深浅（`chrome.dark` 由它算出来，见
     *  `ui/PageTheme.effectiveNight`）—— 所以选「固定深色」时，按宿主页面的观感是一整块深色，
     *  不是「模块这一层深、宿主那层浅」。
     */
    val theme: String = DEFAULT_THEME,
    /**
     * **跟随系统显示大小** —— 关掉后模块页面按**出厂标准**的密度与字号渲染。
     *
     *  为什么要这一档：系统的「显示大小 / 字体大小」调到最大时，本模块那些按 dp 定死的行高、
     *  52dp 的格子、单行标题都会挤在一起（标题被省略号吃掉、副标题顶到下一行）—— 用户要么
     *  把系统调回去，要么就只能忍着。这一档让人**只让模块页面**保持标准大小。
     *
     *  实现：关掉时页面根上 provide 一份 `Density`，取 `Resources.getSystem()` 那份**未被
     *  应用层覆盖**的 `displayMetrics`（系统配置，不含「显示大小」那层改写），字号固定 1.0。
     *  默认开 —— 不改变任何人的现状。
     */
    val followDisplaySize: Boolean = DEFAULT_FOLLOW_DISPLAY_SIZE,
    /**
     * **页面缩放**（百分比，[PAGE_SCALE_MIN] ~ [PAGE_SCALE_MAX]）。
     *
     *  只在 [followDisplaySize] 为假时生效（那一根滑杆也只在那时出现）：跟随系统时页面大小
     *  由系统那一档说了算，再乘一个缩放会让两处都能调、谁也不清楚最后是多少。
     *
     *  实现：把页面根上那份 `Density.density` 乘上 `pageScale / 100`（见 `PageContent`）——
     *  只改模块这一棵页面树，宿主的顶栏 / 状态栏不动。
     */
    val pageScale: Int = DEFAULT_PAGE_SCALE,
    /** 使用 ColorOS 专属系统接口（手电筒 / 免打扰 / 响铃模式等）。关闭时走 AOSP 标准接口。 */
    val colorOsApi: Boolean = DEFAULT_COLOR_OS_API,
    /**
     * 「前台转小窗」要不要播**缩小动画**。
     *
     *  关（默认）：原地收（recents 正门），一步到位、没有窗口动画。
     *  开：同样原地收成小窗，紧接着让框架自己的动画器播那一段（`alpha 1→1`、`scale 1→0.8017`）——
     *  插值 / 矩阵 / spring 全是框架在跑，模块只交起止参数。**只对竖屏生效**：动画终点用的是
     *  竖屏标定的那组落点，横屏时不播、照旧原地收。配方与坑见 `docs/zoom-window.md` §十二。
     */
    val miniWindowAnim: Boolean = DEFAULT_MINI_WINDOW_ANIM,
    /**
     * 「组合键绑的是无操作时，执行两颗键各自的绑定」—— 为真时那一档组合不吞键，
     * 两颗键各按自己这一档手势照常执行；为假时什么都不做（与这一位出现之前一致）。
     *
     *  只在组合的**那一格**是 [Action.None] 时生效：绑了动作就以组合为准，
     *  否则「配了组合还要两颗键各跑一遍」会变成一次按压执行三个动作。
     *
     *  读它的是宿主里的执行器（`host/GestureExec.handleCombo`），所以**只走落盘**、不进广播。
     */
    val comboPassthrough: Boolean = DEFAULT_COMBO_PASSTHROUGH,
) {
    /** 本对象在每个配置字段上的取值 —— 与 `KeyConfig.fieldValues` 同一套（见那边的说明）。 */
    fun fieldValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_MODULE_ENABLED to enabled,
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
                ConfigFields.F_COMBO_PASSTHROUGH to comboPassthrough,
            ),
        )

    companion object {
        /** [GlobalConfig.enabled] 的默认值：**默认关** —— 装上模块不改变任何行为。 */
        const val DEFAULT_ENABLED = false

        /** [GlobalConfig.toastOnRun] 的默认值：默认不弹。 */
        const val DEFAULT_TOAST_ON_RUN = false

        /** [GlobalConfig.vibrateStrength] 的三档取值 —— 弱 / 中 / 强。 */
        const val VIBRATE_WEAK = 0
        const val VIBRATE_MEDIUM = 1
        const val VIBRATE_STRONG = 2

        /** [GlobalConfig.vibrateStrength] 的默认值：中档（与「开振动」原本那一下手感对齐）。 */
        const val DEFAULT_VIBRATE_STRENGTH = VIBRATE_MEDIUM

        /** [GlobalConfig.vibrateStrength] 的取值范围（滑杆两端与钳制共用）。 */
        val VIBRATE_STRENGTH_RANGE = VIBRATE_WEAK..VIBRATE_STRONG

        /** [GlobalConfig.language] 的默认值：跟随系统。 */
        const val DEFAULT_LANGUAGE = "auto"

        /**
         * [GlobalConfig.palette] 的默认值 —— **COUI 自己那套蓝**（`lightColorScheme()` 的 `primary`）。
         *
         *  字面量只写在这里：界面层那枚举的默认档用这个常量当 id，默认档与「没选过」是同一个东西。
         */
        const val DEFAULT_PALETTE = "default"

        /** [GlobalConfig.theme] 的三档 —— 与界面层 `AppTheme` 的 code 一一对应。 */
        const val THEME_AUTO = "auto"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        /** [GlobalConfig.theme] 的默认值：跟随系统。 */
        const val DEFAULT_THEME = THEME_AUTO

        /** [GlobalConfig.colorOsApi] 的默认值：默认开启。 */
        const val DEFAULT_COLOR_OS_API = true

        /** 「跟随系统显示大小」的默认值 —— 开（与升级前的行为一致）。 */
        const val DEFAULT_FOLLOW_DISPLAY_SIZE = true

        /** 页面缩放的三档：下限 / 出厂值 / 上限（百分比）。 */
        const val PAGE_SCALE_MIN = 70
        const val DEFAULT_PAGE_SCALE = 100
        const val PAGE_SCALE_MAX = 150

        /** [GlobalConfig.miniWindowAnim] 的默认值：默认关闭（原地收、无动画）。 */
        const val DEFAULT_MINI_WINDOW_ANIM = false

        /**
         * [GlobalConfig.comboPassthrough] 的默认值：**开**。
         *
         *  组合键是用户主动配的，而「配了一组却忘了给某一格绑动作」很常见 ——
         *  默认吞掉两颗键的全部反应，用户会以为模块坏了。开着时这种半成品至少还保留两颗键本来的行为。
         */
        const val DEFAULT_COMBO_PASSTHROUGH = true

        val DEFAULT =
            GlobalConfig(
                enabled = DEFAULT_ENABLED,
                vibrate = false,
                colorOsApi = DEFAULT_COLOR_OS_API,
            )

        /** 从取值表还原全局配置 —— 与 [GlobalConfig.fieldValues] 成对，两条通道共用。 */
        fun ofFields(v: ConfigFields.Values): GlobalConfig =
            GlobalConfig(
                enabled = v.bool(ConfigFields.F_MODULE_ENABLED),
                vibrate = v.bool(ConfigFields.F_VIBRATE),
                // 手改过的文件可能写进 7 —— 越界的幅度会直接交给 `VibrationEffect`，
                //  所以在这一层就钳住（滑杆两端与这里同一个区间）。
                vibrateStrength = v.number(ConfigFields.F_VIBRATE_STRENGTH).coerceIn(VIBRATE_STRENGTH_RANGE),
                toastOnRun = v.bool(ConfigFields.F_TOAST_ON_RUN),
                language = v.text(ConfigFields.F_LANGUAGE),
                palette = v.text(ConfigFields.F_PALETTE),
                theme = v.text(ConfigFields.F_THEME),
                colorOsApi = v.bool(ConfigFields.F_COLOR_OS_API),
                miniWindowAnim = v.bool(ConfigFields.F_MINI_WINDOW_ANIM),
                followDisplaySize = v.bool(ConfigFields.F_FOLLOW_DISPLAY_SIZE),
                // 与 vibrateStrength 同一档：手改文件写进越界值，在这一层就钳回滑杆两端
                pageScale = v.number(ConfigFields.F_PAGE_SCALE).coerceIn(PAGE_SCALE_MIN, PAGE_SCALE_MAX),
                comboPassthrough = v.bool(ConfigFields.F_COMBO_PASSTHROUGH),
            )
    }
}

/** 整份配置。 */
data class Config(
    val keys: Map<KeyId, KeyConfig>,
    /**
     * 用户收集来的按键（顺序 = 收集顺序）。
     *
     *  **内置键不在这里** —— 内置与自定义的全集是 [allKeys]。把内置键也塞进来会让
     *  「哪些是用户的东西」和「哪些是编译期就有的」混成一锅，删除 / 改名那些判据就得再各写一遍。
     *
     *  自定义键的**身份**是 `KeyId.id`（`ck1` `ck2`），键码只是它的一个属性：换一次驱动上报
     *  换了个码，绑定不该跟着丢（见 [KeyId.custom]）。
     */
    val customKeys: List<KeyId> = emptyList(),
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
     *  所以它只走落盘这一条路：JSON 里单独一段（`ConfigDto.appNames`，键名 `app_names`），
     *  `ConfigCodec` 单独读写。注入侧读到的 `Config` 里这一项恒为空，没人读它。
     *
     *  与应用改名无关：名字只是显示，身份是 `AppTarget.identity`。改过名之后旧名字还在表里，
     *  界面优先用「这次清单里的新名字」（见 `ui/...` 的名字优先级），表只是冷启动时的兜底。
     */
    val appNames: Map<String, String> = emptyMap(),
    /**
     * **组合按键**（顺序 = 界面上的顺序，也是成员数相同时的优先级）。
     *
     *  与 `customKeys` / [appNames] 一样**只走落盘这一列**：它是一张列表，进不了
     *  [ConfigFields] 那种扁平字段表。组合键的判定目前还没接（判定侧要的是一张「哪些键算一组」
     *  的表，得另走一条广播），所以注入侧读到的 `Config` 里这一项恒为空。
     */
    val combos: List<ComboKey> = emptyList(),
) {
    fun key(id: KeyId): KeyConfig = keys[id] ?: KeyConfig.defaultFor(id)

    /**
     * **这颗键现在生效吗** —— 两级相与：[GlobalConfig.enabled]（模块总闸）与 [KeyConfig.enabled]
     * （这一颗键自己的开关）。
     *
     *  这是**唯一判据**：注入侧的认领（`ConfigChannel.enabledOf`）、宿主侧的执行与松手
     *  （`GestureReceiver`）、宿主侧的场景派发放行（`SideKeyGestureHooks`）三处都调它，
     *  不许再就地写「`global.enabled && key(...).enabled`」—— 那一套曾经在四个地方各写一遍，
     *  漏改一处就是「某个路径上停用的键还在吞键」，而编译期与 CI 都看不见。
     *
     *  界面侧要的往往只是**一半**：首页总开关那行看 `global.enabled`，⋮ 菜单那行看
     *  `key(id).enabled` —— 那些地方继续各取一半，别拿这个方法去替代（会让总闸锁死自己）。
     */
    fun isLive(id: KeyId): Boolean = global.enabled && key(id).enabled

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

    /**
     * 内置 + 自定义的**全集**（界面列键清单、广播打包都按它走）。
     *
     *  `by lazy`：注入侧每条按键事件都要按码查一次键（`keyForCode` → `allKeys`），
     *  每次现拼一个列表就是每按一次键多两三个分配 —— 而这跑在 `InputReader` 线程上。
     *  配置是不可变的（写配置走 `copy`），所以缓存一次就够。
     */
    val allKeys: List<KeyId> by lazy { KeyId.builtins + customKeys }

    /** 按 id 找键本身（要拿 `label` / `isCustom` 时用）；不认识返回 null。 */
    fun keyIdOf(id: String): KeyId? = allKeys.firstOrNull { it.id == id }

    /** 按 id 找那个键的配置（自定义键也认）；不认识返回 null。 */
    fun keyForId(id: String): KeyConfig? = keyIdOf(id)?.let { key(it) }

    /**
     * 按 keyCode 找那个键的配置 —— **认领路径的唯一入口**。
     *
     *  注入侧不再问「这是不是侧键」，改问「这个码在配置里是不是某一颗键」。查不到就放行：
     *  电源 / 音量那些必须原样交给系统，这一点没变。
     */
    fun keyForCode(code: Int): KeyConfig? = allKeys.firstOrNull { code in it.acceptedKeyCodes }?.let { key(it) }

    /**
     * 按**原始键码**找键 —— 与 [keyForCode] 的区别是**不含** ColorOS 的内部注入码。
     *
     *  PM（`PhoneWindowManager`）那条兜底路径能收到所有按键，策略类那条已经处理过 780 / 781 / 782，
     *  这里再认一次会让同一次按压被判两遍（策略类那条路径的说明见 `inject/SideKeyInterceptor`）。
     */
    fun keyForRawCode(code: Int): KeyConfig? = allKeys.firstOrNull { it.keyCode == code }?.let { key(it) }

    /**
     * 当前按着 [down] 这几颗键（`KeyId.id`）时，该由哪一档组合键接管 —— 没有就是 `null`。
     *
     *  **成员多的优先**：`{侧键, 音量上}` 与 `{侧键, 音量上, 音量下}` 同时成立时，用户按了三颗
     *  显然是冲更具体的那一档去的。成员数一样时取 [combos] 里靠前的那一档 —— 与界面上谁排在
     *  前面是同一个口径（`maxByOrNull` 保留第一个最大值）。
     *
     *  判据只看**集合包含**（[ComboKey.matches]），不看按下的先后：两颗键「同时按」时上报顺序
     *  由驱动决定，拿它当判据等于让配置依赖硬件细节。
     *
     *  成员里可能有**已经删掉的键**（自定义键删了、组合键还指着它）：那一档这辈子凑不齐，等于
     *  停用。不在读取时清理，是因为「删键」与「改组合」是两条独立的路，清理要跨两处同步 ——
     *  少一个成员反而会把用户写的「三键组合」悄悄降成两键。
     *
     *  这里是「底层接口」那一半：判定侧（`inject/input`）判出「这几颗键一起按着」之后调它取档位，
     *  再按短按 / 长按取 [ComboKey.actionOf]。接线不在此刻。
     */
    fun comboFor(down: Set<String>): ComboKey? = combos.filter { it.isUsable && it.matches(down) }.maxByOrNull { it.keys.size }

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
                keys = KeyId.builtins.associateWith { KeyConfig.defaultFor(it) },
                global = GlobalConfig.DEFAULT,
            )

        /**
         * 拼一份配置 —— 扁平字段从取值表来，几张「表」的段由调用方读好传进来。
         *
         *  [keys] / [global] 由调用方各自按 [KeyConfig.ofFields] / [GlobalConfig.ofFields] 建好。
         *  [customKeys] 与 [keys] **必须对得上**：每一个自定义键都得在 `keys` 里有一条，
         *  否则 `key(id)` 会退回默认值，界面上看着像「刚收集的键绑定全丢了」。
         *
         *  `@Suppress("LongParameterList")`：这不是「参数太多、该拆对象」那种函数 —— 参数个数就是
         *  配置里那几张表的个数（扁平取值表 + 三张进不了字段表的段）。拆成一个 `Sections` 壳只是
         *  把它们挪个地方，读取处还得一层层解引用，而每加一张表就多一个要同步的落点。
         */
        @Suppress("LongParameterList")
        fun ofFields(
            keys: Map<KeyId, KeyConfig>,
            global: GlobalConfig,
            v: ConfigFields.Values,
            /** 用户收集来的按键（`custom_keys` 那一段）—— 不在字段表里，由 `ConfigCodec` 单独读。 */
            customKeys: List<KeyId> = emptyList(),
            /** 名字表（`app_names` 那一段）—— 同上，由 [ConfigCodec] 单独读出来传进来。 */
            appNames: Map<String, String> = emptyMap(),
            /** 组合按键（`combos` 那一段）—— 同上。 */
            combos: List<ComboKey> = emptyList(),
        ): Config =
            Config(
                keys = keys,
                customKeys = customKeys,
                global = global,
                disclaimerAccepted = v.bool(ConfigFields.F_DISCLAIMER),
                commandDisclaimerAccepted = v.bool(ConfigFields.F_COMMAND_DISCLAIMER),
                appNames = appNames,
                combos = combos,
            )
    }
}
