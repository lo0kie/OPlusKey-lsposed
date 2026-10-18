package com.lookie.opluskey.core.model

/**
 * 配置字段的**声明表** —— 一个逻辑字段的名字、类型、取值范围只有这一处写全。
 *
 * ## 同一个逻辑字段有两张脸
 *
 *  · `*_JSON`  —— 落盘那份 JSON 的键（`core/config/ConfigCodec` 用）；
 *  · `*_EXTRA` —— 配置广播的 intent extra 名（`core/wire/ConfigBroadcast` 用）。
 *
 * 为什么必须收在一处：两列的取值**不一样**（`longPressMs` 对 `long_press_ms`），改一边时编译器
 * 不会提醒另一边 —— 曾经就有「改了广播那列、落盘那列没跟着改」这种漏，而它在真机上只表现为
 * 「配置读出来还是旧的」，没有任何一处报错。现在同一字段的两行挨着写，改的时候看得见对面那行。
 *
 * 两列的**稳定性不同**，动之前先分档：
 *  · `*_JSON` 已经落在用户机器的文件里（`opluskey_config.conf`）⇒ **不能改名**，只能加新键 +
 *    兼容旧键（`ConfigCodec` 对缺失字段一律走默认值，所以加键是安全的）；
 *  · `*_EXTRA` 只在同一次安装的两端之间传（发送与接收都是本模块、同一个版本）⇒ 可以改名，
 *    但发送侧与接收侧必须在同一次改动里一起改。
 *
 * 只出现在一列里的字段，理由写在各段的小标题下。
 *
 * ## 加一个配置字段要改几处
 *
 * 1. 本文件：加一对 `*_JSON` / `*_EXTRA` 常量，并在下面那张表里加一行 [Field]（类型与默认值
 *    一起写：`Kind.Bool` / `Kind.Number` / `Kind.Text`）；
 * 2. `core/model/Config.kt`：给数据类加属性，并在它那**一对** `fieldValues()` / `ofFields()` 里
 *    加一行 —— 「字段 → 属性」的映射只有那一处。
 *
 * 那两条通道（落盘 JSON / 广播 extra）的读写都按本文件这张表**生成**，不用再去改它们
 * （此前是「改 5 个文件」，其中两处还是各写一遍的钳制上下限）。
 */
object ConfigFields {
    // 正文结构 —— `ConfigCodec` 的顶层与两个段。广播不带这些：注入侧拿到的是一份已经解好的配置。
    const val VERSION_JSON = "version"
    const val KEYS_JSON = "keys"
    const val GLOBAL_JSON = "global"

    /**
     * 正文自带的完整性字段，**写在最后一位**（`ConfigCodec` 校验时把它摘掉再序列化比对）。
     * 顺序是格式的一部分，动它等于改文件格式。
     */
    const val MD5_JSON = "md5"

    /**
     * 「打开应用」那一族的显示名（`{ "999@包名/类名": "微信 2" }`）。
     *
     *  **不在下面那张字段表里**：那张表是扁平的「一个字段一个值」，而这是映射；而且它只有界面读
     *  （执行侧只要「起哪个组件」），进字段表就等于每次按键把整张表广播给 `system_server`。
     *  所以这一项只由 `ConfigCodec` 单独读写，**没有 `*_EXTRA`**（不广播）。
     */
    const val APP_NAMES_JSON = "app_names"

    /** 风险提示的确认记录 —— 与 `keys` / `global` 同一层，不在它们里面。 */
    const val DISCLAIMER_JSON = "disclaimerAccepted"

    /** 自定义命令的风险提示确认记录，同上。 */
    const val COMMAND_DISCLAIMER_JSON = "commandDisclaimerAccepted"

    // `KeyConfig` —— 主开关与三个手势绑定。
    const val ENABLED_JSON = "enabled"
    const val ENABLED_EXTRA = "enabled"
    const val SINGLE_JSON = "single"
    const val SINGLE_EXTRA = "plus_single"
    const val DOUBLE_JSON = "double"
    const val DOUBLE_EXTRA = "plus_double"
    const val LONG_JSON = "long"
    const val LONG_EXTRA = "plus_long"

    // `KeyConfig` —— 长按的三个参数。
    const val LONG_PRESS_MS_JSON = "longPressMs"
    const val LONG_PRESS_MS_EXTRA = "long_press_ms"
    const val LONG_REPEAT_JSON = "longRepeat"
    const val LONG_REPEAT_EXTRA = "long_repeat"
    const val REPEAT_INTERVAL_MS_JSON = "longRepeatIntervalMs"
    const val REPEAT_INTERVAL_MS_EXTRA = "long_repeat_interval_ms"

    /**
     * 长按绑的是不是「多条命令」（`KeyConfig.longIsSerial`）—— **只有广播这一列**。
     * 它是发送侧**现算**的（要读命令库文件，注入侧读不到那个目录），不落盘。
     */
    const val LONG_SERIAL_EXTRA = "long_is_serial"

    // `GlobalConfig`。
    const val DOUBLE_CLICK_MS_JSON = "doubleClickMs"
    const val DOUBLE_CLICK_MS_EXTRA = "double_click_ms"
    const val VIBRATE_JSON = "vibrate"
    const val VIBRATE_EXTRA = "vibrate"
    const val INSTANT_SINGLE_JSON = "instantSingle"
    const val INSTANT_SINGLE_EXTRA = "instant_single"

    /**
     * 执行时提示（弹一条 toast）—— **只有 JSON 这一列**：它由宿主侧弹，注入侧不读它
     * （判定在注入侧、执行在宿主侧，见 `docs/implementation.md`），所以不进广播。
     */
    const val TOAST_ON_RUN_JSON = "toastOnRun"

    const val LANGUAGE_JSON = "language"
    const val LANGUAGE_EXTRA = "language"
    const val MINI_WINDOW_ANIM_JSON = "miniWindowAnim"

    const val COLOR_OS_API_JSON = "colorOsApi"
    const val COLOR_OS_API_EXTRA = "color_os_api"

    /**
     * 字段的取值类型 —— 两条通道各自按它选 getter / setter，**默认值也在这里**。
     *
     * 为什么默认值要挂在这里：它有两个用处 —— 老文件 / 手写文件里缺这一项时补什么，以及
     * 「整段都缺」时补什么。原先是写在两条通道各自的读代码里（同一个默认值在两处各写一遍），
     * 于是「默认值改动一处漏一处」是可能的，而它的表现是「刚装上时界面上显示的值不对」。
     */
    sealed class Kind {
        /** 布尔。 */
        class Bool(
            val default: Boolean,
        ) : Kind()

        /**
         * 整数。[min] / [max] 是**两条通道共用**的钳制范围。
         *
         * 原来这两个界各写两份（`ConfigCodec.clamp` 与 `ConfigBroadcast.clampInt` 的调用点），
         * 手改文件写了越界值时两边的收口方式一模一样地重复着。
         */
        class Number(
            val default: kotlin.Int,
            val min: kotlin.Int,
            val max: kotlin.Int,
        ) : Kind()

        /** 文本。 */
        class Text(
            val default: String,
        ) : Kind()
    }

    /**
     * 一个逻辑字段在两条通道上的坐标 + 类型。
     *
     * @param json 落盘 JSON 里的键；`null` = 这一列没有它（只走广播）
     * @param extra 广播 extra 名；`null` = 只落盘
     * @param kind 类型 + 默认值（+ 整数的钳制范围）
     */
    class Field(
        val json: String?,
        val extra: String?,
        val kind: Kind,
    )

    // ── `KeyConfig` 的字段。**书写顺序就是落盘 JSON 的键序**（`md5` 算的是文本字节，键序会进它，
    //    所以这个顺序是格式的一部分：动它只影响**以后**写出来的文件，老文件仍按它自己那份键序校验）

    /** 主开关（接管 / 不接管）。 */
    val F_ENABLED = Field(ENABLED_JSON, ENABLED_EXTRA, Kind.Bool(false))

    /** 单击那一档的绑定（`Action` 的 token）。 */
    val F_SINGLE = Field(SINGLE_JSON, SINGLE_EXTRA, Kind.Text(Action.NONE))

    /** 双击那一档的绑定。 */
    val F_DOUBLE = Field(DOUBLE_JSON, DOUBLE_EXTRA, Kind.Text(Action.NONE))

    /** 长按那一档的绑定。 */
    val F_LONG = Field(LONG_JSON, LONG_EXTRA, Kind.Text(Action.NONE))

    /** 长按阈值。 */
    val F_LONG_PRESS_MS =
        Field(
            LONG_PRESS_MS_JSON,
            LONG_PRESS_MS_EXTRA,
            Kind.Number(
                KeyConfig.DEFAULT_LONG_PRESS_MS,
                KeyConfig.MIN_LONG_PRESS_MS,
                KeyConfig.MAX_LONG_PRESS_MS,
            ),
        )

    /** 长按是否连续触发。 */
    val F_LONG_REPEAT = Field(LONG_REPEAT_JSON, LONG_REPEAT_EXTRA, Kind.Bool(false))

    /** 连发间隔。 */
    val F_REPEAT_INTERVAL_MS =
        Field(
            REPEAT_INTERVAL_MS_JSON,
            REPEAT_INTERVAL_MS_EXTRA,
            Kind.Number(
                KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                KeyConfig.MIN_REPEAT_INTERVAL_MS,
                KeyConfig.MAX_REPEAT_INTERVAL_MS,
            ),
        )

    /** 长按绑的是不是串行命令 —— **只有广播那一列**，见 [LONG_SERIAL_EXTRA] 的说明。 */
    val F_LONG_SERIAL = Field(null, LONG_SERIAL_EXTRA, Kind.Bool(false))

    /** `KeyConfig` 的全部字段（顺序即落盘键序；[Field.json] 为 `null` 的那一项落盘时跳过）。 */
    val KEY_FIELDS: List<Field> =
        listOf(F_ENABLED, F_SINGLE, F_DOUBLE, F_LONG, F_LONG_PRESS_MS, F_LONG_REPEAT, F_REPEAT_INTERVAL_MS, F_LONG_SERIAL)

    // ── `GlobalConfig` 的字段（顺序同样即落盘键序）

    /** 双击窗口。 */
    val F_DOUBLE_CLICK_MS =
        Field(
            DOUBLE_CLICK_MS_JSON,
            DOUBLE_CLICK_MS_EXTRA,
            Kind.Number(
                GlobalConfig.DEFAULT_DOUBLE_CLICK_MS,
                GlobalConfig.MIN_DOUBLE_CLICK_MS,
                GlobalConfig.MAX_DOUBLE_CLICK_MS,
            ),
        )

    /** 触觉反馈。 */
    val F_VIBRATE = Field(VIBRATE_JSON, VIBRATE_EXTRA, Kind.Bool(false))

    /** 不等双击窗口、抬手立即触发单击。 */
    val F_INSTANT_SINGLE = Field(INSTANT_SINGLE_JSON, INSTANT_SINGLE_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_INSTANT_SINGLE))

    /** 执行时提示 —— **只有落盘那一列**，见 [TOAST_ON_RUN_JSON] 的说明。 */
    val F_TOAST_ON_RUN = Field(TOAST_ON_RUN_JSON, null, Kind.Bool(GlobalConfig.DEFAULT_TOAST_ON_RUN))

    /** 界面语言。 */
    val F_LANGUAGE = Field(LANGUAGE_JSON, LANGUAGE_EXTRA, Kind.Text(GlobalConfig.DEFAULT_LANGUAGE))

    /** 使用 ColorOS 专属系统接口（手电筒 / 免打扰 / 响铃模式等）。关闭时走 AOSP 标准接口。 */
    val F_COLOR_OS_API = Field(COLOR_OS_API_JSON, COLOR_OS_API_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_COLOR_OS_API))

    /**
     * 「前台转小窗」走带动画那条路 —— **只有落盘那一列**（与 [F_TOAST_ON_RUN] 同理）：
     * 读它的是宿主进程里的执行器，它本来就读配置文件。
     */
    val F_MINI_WINDOW_ANIM = Field(MINI_WINDOW_ANIM_JSON, null, Kind.Bool(GlobalConfig.DEFAULT_MINI_WINDOW_ANIM))

    /** `GlobalConfig` 的全部字段。 */
    val GLOBAL_FIELDS: List<Field> =
        listOf(
            F_DOUBLE_CLICK_MS,
            F_VIBRATE,
            F_INSTANT_SINGLE,
            F_TOAST_ON_RUN,
            F_LANGUAGE,
            F_COLOR_OS_API,
            F_MINI_WINDOW_ANIM,
        )

    // ── `Config` 自己那两位（都在正文顶层、都只落盘）

    /** 已确认「风险提示与免责声明」。 */
    val F_DISCLAIMER = Field(DISCLAIMER_JSON, null, Kind.Bool(false))

    /** 已确认「自定义命令不做任何检验」那一份提示。 */
    val F_COMMAND_DISCLAIMER = Field(COMMAND_DISCLAIMER_JSON, null, Kind.Bool(false))

    /** `Config` 顶层的全部字段（写 [VERSION_JSON] / [KEYS_JSON] / [GLOBAL_JSON] 之后、[MD5_JSON] 之前）。 */
    val ROOT_FIELDS: List<Field> = listOf(F_DISCLAIMER, F_COMMAND_DISCLAIMER)

    /**
     * 某一项的取值 —— 读取方（两条通道）与写入方都过这一个壳，于是「字段类型」这件事只在一处判。
     *
     * 取值缺项 / 类型不符一律退回该字段 [Kind] 自带的默认值：读取方本会为每个字段填上值，
     * 所以这一层是「表与数据类对不上」时的最后一道 —— 宁可退回默认值，也不要抛异常
     * （广播那条路跑在 system_server 的热路径上，抛异常就是系统进程崩，见 `AGENTS.md` 五之二）。
     */
    class Values(
        private val map: Map<Field, Any>,
    ) {
        fun bool(f: Field): Boolean = map[f] as? Boolean ?: (f.kind as? Kind.Bool)?.default ?: false

        fun number(f: Field): kotlin.Int = map[f] as? kotlin.Int ?: (f.kind as? Kind.Number)?.default ?: 0

        fun text(f: Field): String = map[f] as? String ?: (f.kind as? Kind.Text)?.default.orEmpty()

        /** 建这份取值表的原始映射（写方按字段表遍历时用）。 */
        fun raw(): Map<Field, Any> = map
    }

    /**
     * 某个 extra 名对应的类型 —— 广播那条「按类型取原始值」的路（`ConfigBroadcast.rawExtra`）用它。
     *
     *  返回 `null` = 这个名字没登记在表里。调用方按「文本」兜底（与本表出现之前的兜底分支一致），
     *  于是「新增 extra 忘了登记类型」不会变成异常，只是取法保守一档。
     */
    fun kindOfExtra(name: String): Kind? = (KEY_FIELDS + GLOBAL_FIELDS).firstOrNull { it.extra == name }?.kind
}
