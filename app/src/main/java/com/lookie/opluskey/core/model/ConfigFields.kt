package com.lookie.opluskey.core.model

/**
 * 配置字段的**两套对外名字，成对写在一处** —— 别的文件只引用这里的常量。
 *
 * 同一个逻辑字段有两张脸：
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
 */
internal object ConfigFields {
    // 正文结构 —— `ConfigCodec` 的顶层与两个段。广播不带这些：注入侧拿到的是一份已经解好的配置。
    const val VERSION_JSON = "version"
    const val KEYS_JSON = "keys"
    const val GLOBAL_JSON = "global"

    /**
     * 正文自带的完整性字段，**写在最后一位**（`ConfigCodec` 校验时把它摘掉再序列化比对）。
     * 顺序是格式的一部分，动它等于改文件格式。
     */
    const val MD5_JSON = "md5"

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
}
