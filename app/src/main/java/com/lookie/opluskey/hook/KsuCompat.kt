package com.lookie.opluskey.hook

/** 照搬 KSU 模块（同目录 `OPlusKey`）的那些取值 —— 配置兼容用的别名表。 */
internal object KsuCompat {

    /** KSU 预设里「截图」的取值（模块侧换成注入 `KEYCODE_SYSRQ`）。 */
    const val SCREENSHOT_CMD = "service call color_screenshot 1"

    /** 上面那条的匹配 token（contains 判据用，容忍参数差异）。 */
    const val SCREENSHOT_CMD_TOKEN = "color_screenshot"

    /** KSU 预设里「小布识屏」那条命令里的 service action。 */
    const val SIDEBAR_SERVICE = "oplus.intent.action.DIRECT_SIDEBAR_SERVICE"
}
