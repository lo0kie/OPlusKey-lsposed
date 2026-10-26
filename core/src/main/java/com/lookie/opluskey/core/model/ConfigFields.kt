package com.lookie.opluskey.core.model

/**
 * 配置字段的**广播侧声明表** —— 一个逻辑字段在广播里的名字、类型、默认值与取值范围只在这里写全。
 *
 * ## 只管广播这一条通道
 *
 *  · `*_EXTRA` —— 配置广播的 intent extra 名（`core/wire/ConfigBroadcast` 用）；
 *  · [Field.extra] 为 `null` 的那几项**不广播**（只落盘）：它们只作为 [Values] 的键存在，
 *    取值由 `ConfigDto` / `Config.ofFields` 自己映射。
 *
 * **落盘格式的真源是 `core/config/ConfigDto`**（`@SerialName` 与字段顺序）。本表**不登记** JSON 键：
 * 原先那半列没有一行代码读它，改 `@SerialName` 时它也不会跟着动 —— 两处各说一套，迟早分叉。
 *
 * 两列的稳定性本来就不同：
 *  · 落盘键**不能改名** —— 它已经落在用户机器的 `opluskey_config.conf` 里，改名等于换一份格式；
 *  · extra **可以改名** —— 发送与接收都是本模块、同一个版本，只要在同一次改动里两边一起改。
 *
 * ## 加一个配置字段要改几处
 *
 * 1. 本文件：加一条 `*_EXTRA` 常量，并在 [KEY_FIELDS] / [GLOBAL_FIELDS] 里加一行 [Field]
 *    （类型、默认值与上下限一起写：`Kind.Bool` / `Kind.Number` / `Kind.Text`）；
 * 2. `core/model/Config.kt`：给数据类加属性，并在它那**一对** `fieldValues()` / `ofFields()` 里加一行；
 * 3. `core/config/ConfigDto.kt`：线格式由 kotlinx.serialization 定义，字段名与默认值写在那边的
 *    属性与 `@SerialName` 上。
 *
 * 于是两条通道各有自己的生成源：**广播**由本表驱动（extra + [Kind]），**落盘**由 DTO 驱动，
 * 取值区间两边都回到本表来钳。
 */
object ConfigFields {
    // `KeyConfig` —— 主开关与三个手势绑定。
    //
    //  这几条 `*_EXTRA` 是**基础名**，真正写进广播的名字前面还挂一个 `<keyId>_` 前缀
    //  （`plus_single` / `ck1_single` ……）—— 自定义按键是运行时才知道有几颗的，广播里必须
    //  按键分开存，否则所有键的绑定会互相覆盖。拼前缀的地方只有 `ConfigBroadcast.putFields`。
    const val ENABLED_EXTRA = "enabled"
    const val SINGLE_EXTRA = "single"
    const val DOUBLE_EXTRA = "double"
    const val LONG_EXTRA = "long"

    // 广播里那份「有哪些键」的清单（逗号分隔的 id）与每颗键的身份。
    //  落盘用的是 `custom_keys` 那段数组（见 `ConfigDto.customKeys`），只有广播才用这种扁平写法。
    const val KEY_IDS_EXTRA = "key_ids"

    /** 每颗键一条，实际 extra 名 = `<id>_code`。 */
    const val KEY_CODE_EXTRA = "code"

    /** 每颗键一条，实际 extra 名 = `<id>_name`。 */
    const val KEY_NAME_EXTRA = "name"

    // `KeyConfig` —— 长按的三个参数。
    const val LONG_PRESS_MS_EXTRA = "long_press_ms"
    const val LONG_REPEAT_EXTRA = "long_repeat"
    const val REPEAT_INTERVAL_MS_EXTRA = "long_repeat_interval_ms"

    /** 长按连续触发时，每一次重复都振动一次（关 = 只在长按那一下振一次）。 */
    const val REPEAT_VIBRATE_EXTRA = "long_repeat_vibrate"

    /**
     * 长按绑的是不是「多条命令」（`KeyConfig.longIsSerial`）—— **只有广播这一列**。
     * 它是发送侧**现算**的（要读命令库文件，注入侧读不到那个目录），不落盘。
     */
    const val LONG_SERIAL_EXTRA = "long_is_serial"

    // `GlobalConfig`。
    const val DOUBLE_CLICK_MS_EXTRA = "double_click_ms"
    const val VIBRATE_EXTRA = "vibrate"
    const val INSTANT_SINGLE_EXTRA = "instant_single"
    const val LANGUAGE_EXTRA = "language"
    const val COLOR_OS_API_EXTRA = "color_os_api"

    /** 组合键两颗键之间的等待窗口（毫秒）—— 注入侧判定要用，所以走广播。 */
    const val COMBO_WINDOW_MS_EXTRA = "combo_window_ms"

    /**
     * **模块总闸**（首页「启用按键绑定」那一行）—— 与 [ENABLED_EXTRA] 是两件事，别混：
     *
     *  · 本项：整个模块接管不接管（关掉 ⇒ 一颗键都不接管，页面整片灰）；
     *  · [ENABLED_EXTRA]：**这一颗键**接管不接管（⋮ 菜单里「停用这颗键」，侧键也有）。
     *
     *  以前只有后者，侧键那一份同时当两个用 ⇒「停用侧键」被迫等于「关掉模块」。拆开之后
     *  菜单里停用侧键只停那一颗，自定义键与页面都不受影响。
     */
    const val MODULE_ENABLED_EXTRA = "module_enabled"

    /**
     * 字段的取值类型 —— 广播那条路按它选 getter / setter，**默认值也在这里**。
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
         * 原先这两个界各写两份（`ConfigCodec.clamp` 与 `ConfigBroadcast.clampInt` 的调用点），
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
     * 一个逻辑字段在广播里的坐标 + 类型（也是 [Values] 里的键）。
     *
     * @param extra 广播 extra 名；`null` = 这一项不广播
     * @param kind 类型 + 默认值（+ 整数的钳制范围）
     */
    class Field(
        val extra: String?,
        val kind: Kind,
    )

    // ── `KeyConfig` 的字段

    /**
     * **这一颗键**接管不接管（`KeyConfig.enabled`）—— 与模块总闸 [F_MODULE_ENABLED] 是两件事。
     *
     *  侧键也走它：「停用这颗键」只停那一颗，模块与别的键照常。
     */
    val F_ENABLED = Field(ENABLED_EXTRA, Kind.Bool(false))

    /** 单击那一档的绑定（`Action` 的 token）。 */
    val F_SINGLE = Field(SINGLE_EXTRA, Kind.Text(Action.NONE))

    /** 双击那一档的绑定。 */
    val F_DOUBLE = Field(DOUBLE_EXTRA, Kind.Text(Action.NONE))

    /** 长按那一档的绑定。 */
    val F_LONG = Field(LONG_EXTRA, Kind.Text(Action.NONE))

    /** 长按阈值。 */
    val F_LONG_PRESS_MS =
        Field(
            LONG_PRESS_MS_EXTRA,
            Kind.Number(
                KeyConfig.DEFAULT_LONG_PRESS_MS,
                KeyConfig.MIN_LONG_PRESS_MS,
                KeyConfig.MAX_LONG_PRESS_MS,
            ),
        )

    /** 长按是否连续触发。 */
    val F_LONG_REPEAT = Field(LONG_REPEAT_EXTRA, Kind.Bool(false))

    /** 连发间隔。 */
    val F_REPEAT_INTERVAL_MS =
        Field(
            REPEAT_INTERVAL_MS_EXTRA,
            Kind.Number(
                KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                KeyConfig.MIN_REPEAT_INTERVAL_MS,
                KeyConfig.MAX_REPEAT_INTERVAL_MS,
            ),
        )

    /**
     * 连续触发时是否每一下都振动（`KeyConfig.longRepeatVibrate`）。
     *
     *  默认 false = 与这一位出现之前一致：长按判出来的那一下振一次，之后每次重复不振。
     */
    val F_REPEAT_VIBRATE = Field(REPEAT_VIBRATE_EXTRA, Kind.Bool(false))

    /** 「单击延迟」—— **每颗键各一份**（原来在全局，见 `KeyConfig.doubleClickMs`）。 */
    val F_DOUBLE_CLICK_MS =
        Field(
            DOUBLE_CLICK_MS_EXTRA,
            Kind.Number(
                KeyConfig.DEFAULT_DOUBLE_CLICK_MS,
                KeyConfig.MIN_DOUBLE_CLICK_MS,
                KeyConfig.MAX_DOUBLE_CLICK_MS,
            ),
        )

    /** 「忽视无操作双击手势」—— **每颗键各一份**。 */
    val F_INSTANT_SINGLE = Field(INSTANT_SINGLE_EXTRA, Kind.Bool(KeyConfig.DEFAULT_INSTANT_SINGLE))

    /** 长按绑的是不是串行命令 —— **只有广播那一列**，见 [LONG_SERIAL_EXTRA] 的说明。 */
    val F_LONG_SERIAL = Field(LONG_SERIAL_EXTRA, Kind.Bool(false))

    /** `KeyConfig` 的全部广播字段。 */
    val KEY_FIELDS: List<Field> =
        listOf(
            F_ENABLED,
            F_SINGLE,
            F_DOUBLE,
            F_LONG,
            F_LONG_PRESS_MS,
            F_LONG_REPEAT,
            F_REPEAT_INTERVAL_MS,
            F_REPEAT_VIBRATE,
            F_DOUBLE_CLICK_MS,
            F_INSTANT_SINGLE,
            F_LONG_SERIAL,
        )

    // ── `GlobalConfig` 的字段

    /** 模块总闸（见 [MODULE_ENABLED_EXTRA]）。 */
    val F_MODULE_ENABLED = Field(MODULE_ENABLED_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_ENABLED))

    /** 触觉反馈。 */
    val F_VIBRATE = Field(VIBRATE_EXTRA, Kind.Bool(false))

    /**
     * 振动强度（三档：弱 / 中 / 强）—— **只有落盘那一列**（与 [F_TOAST_ON_RUN] 同理）：
     * 振那一下的是宿主进程里的执行器，它本来就读配置文件；注入侧不振，没必要广播。
     */
    val F_VIBRATE_STRENGTH =
        Field(null, Kind.Number(GlobalConfig.DEFAULT_VIBRATE_STRENGTH, GlobalConfig.VIBRATE_WEAK, GlobalConfig.VIBRATE_STRONG))

    /** 执行时提示 —— **只有落盘那一列**：它由宿主侧弹，注入侧不读它。 */
    val F_TOAST_ON_RUN = Field(null, Kind.Bool(GlobalConfig.DEFAULT_TOAST_ON_RUN))

    /** 界面语言。 */
    val F_LANGUAGE = Field(LANGUAGE_EXTRA, Kind.Text(GlobalConfig.DEFAULT_LANGUAGE))

    /**
     * 系统配色 —— **只有落盘那一列**：换的是这个页面自己的配色，注入侧不读它。
     *
     *  取值是色板 id（字符串），上下限与合法清单都在界面层（`ui/model/PagePalette`）——
     *  共用层不认识「有哪些色板」，理由见 `GlobalConfig.palette` 的说明。
     */
    val F_PALETTE = Field(null, Kind.Text(GlobalConfig.DEFAULT_PALETTE))

    /**
     * 界面颜色（跟随系统 / 固定浅 / 固定深）—— **只有落盘那一列**：它只决定这个页面自己的深浅。
     *
     *  注入侧不读它（那边没有界面），广播里也就不必带。
     */
    val F_THEME = Field(null, Kind.Text(GlobalConfig.DEFAULT_THEME))

    /** 使用 ColorOS 专属系统接口（手电筒 / 免打扰 / 响铃模式等）。关闭时走 AOSP 标准接口。 */
    val F_COLOR_OS_API = Field(COLOR_OS_API_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_COLOR_OS_API))

    /**
     * 「前台转小窗」走带动画那条路 —— **只有落盘那一列**（与 [F_TOAST_ON_RUN] 同理）：
     * 读它的是宿主进程里的执行器，它本来就读配置文件。
     */
    val F_MINI_WINDOW_ANIM = Field(null, Kind.Bool(GlobalConfig.DEFAULT_MINI_WINDOW_ANIM))

    /**
     * 组合键的**无操作透传** —— **只有落盘那一列**（同上）：执行在宿主，注入侧只判手势。
     *
     *  开 = 组合那一格没绑动作时，两颗键各按自己这一档照常执行；关 = 什么都不做。
     */
    val F_COMBO_PASSTHROUGH = Field(null, Kind.Bool(GlobalConfig.DEFAULT_COMBO_PASSTHROUGH))

    /**
     * 组合键：**两颗键按下之间最多隔多久**（毫秒）。
     *
     *  走**广播**那一列 —— 判定它的是注入侧（`ComboDetector` 跑在 system_server），那边读不到
     *  配置文件、只吃广播。
     *
     *  调大 ⇒ 手笨一点也能搓出组合；代价是参与组合的键要先等这么久（见 `ComboScreen` 那一行的副标题）。
     */
    val F_COMBO_WINDOW_MS =
        Field(
            COMBO_WINDOW_MS_EXTRA,
            Kind.Number(
                GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                GlobalConfig.COMBO_WINDOW_MIN_MS,
                GlobalConfig.COMBO_WINDOW_MAX_MS,
            ),
        )

    /**
     * 「跟随系统显示大小」—— **只有落盘那一列**（与 [F_TOAST_ON_RUN] 同理）：
     * 它只决定这个页面自己那棵树的密度，注入侧没有界面、不读它。
     */
    val F_FOLLOW_DISPLAY_SIZE = Field(null, Kind.Bool(GlobalConfig.DEFAULT_FOLLOW_DISPLAY_SIZE))

    /**
     * 页面缩放（百分比）—— 同样只有落盘那一列。上下限就是滑杆两端（[GlobalConfig.PAGE_SCALE_MIN]
     * ~ [GlobalConfig.PAGE_SCALE_MAX]）：手改文件写进 10 或 900 会被这一层钳回来。
     */
    val F_PAGE_SCALE =
        Field(
            null,
            Kind.Number(GlobalConfig.DEFAULT_PAGE_SCALE, GlobalConfig.PAGE_SCALE_MIN, GlobalConfig.PAGE_SCALE_MAX),
        )

    /** `GlobalConfig` 的全部广播字段。 */
    val GLOBAL_FIELDS: List<Field> =
        listOf(
            F_MODULE_ENABLED,
            F_VIBRATE,
            F_VIBRATE_STRENGTH,
            F_TOAST_ON_RUN,
            F_LANGUAGE,
            F_PALETTE,
            F_THEME,
            F_COLOR_OS_API,
            F_MINI_WINDOW_ANIM,
            F_COMBO_PASSTHROUGH,
            F_COMBO_WINDOW_MS,
            F_FOLLOW_DISPLAY_SIZE,
            F_PAGE_SCALE,
        )

    // ── `Config` 自己那两位（正文顶层、都只落盘，不进广播）

    /** 已确认「风险提示与免责声明」。 */
    val F_DISCLAIMER = Field(null, Kind.Bool(false))

    /** 已确认「自定义命令不做任何检验」那一份提示。 */
    val F_COMMAND_DISCLAIMER = Field(null, Kind.Bool(false))

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

    /**
     * 广播 extra 名 → **基础名**（去掉 `<keyId>_` 前缀）。不认识的前缀原样返回。
     *
     *  逐键那几项在广播里叫 `plus_single` / `ck1_single`，而字段表登记的是 `single`。
     *  [ConfigBroadcast.tag] 要按**类型**取 extra（类型不对 `getString` 会抛 `ClassCastException`），
     *  所以取类型之前必须先剥掉前缀 —— 否则指纹会把所有逐键字段都当成「取不到值」，
     *  两份不同的配置算出同一个指纹，界面就会报「已同步」。
     *
     *  只认两种前缀：内置键的 id（`plus`）与自定义键的 `ck<数字>`。两者都由 [KeyId] 给出，
     *  不在这里另抄一份字面量。
     */
    fun baseExtraName(name: String): String = KEY_EXTRA_PREFIX.matchEntire(name)?.groupValues?.get(1) ?: name

    private val KEY_EXTRA_PREFIX =
        Regex(
            "^(?:" + Regex.escape(KeyId.PLUS.id) + "|" + Regex.escape(KeyId.CUSTOM_ID_PREFIX) + "\\d+)_(.+)$",
        )
}
