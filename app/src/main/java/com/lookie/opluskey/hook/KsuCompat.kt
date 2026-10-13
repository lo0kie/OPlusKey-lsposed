package com.lookie.opluskey.hook

/** 照搬 KSU 模块（同目录 `OPlusKey`）的那些取值 —— 配置兼容用的别名表。 */
internal object KsuCompat {

    /** KSU 预设里「截图」的取值（模块侧换成注入 `KEYCODE_SYSRQ`）。 */
    const val SCREENSHOT_CMD = "service call color_screenshot 1"

    /** KSU 预设里「小布识屏」那条命令里的 service action。 */
    const val SIDEBAR_SERVICE = "oplus.intent.action.DIRECT_SIDEBAR_SERVICE"

    /**
     * 该 action 的真实实现所在包（真机 `dumpsys package` 实测）。
     * 命令串里的 `com.coloros.smartsidebar` 只是它的一个 extra，不是实现包。
     */
    const val SIDEBAR_PKG = "com.coloros.colordirectservice"

    /** 该 action 的真实实现类名。 */
    const val SIDEBAR_CLASS = "com.coloros.colordirectservice.InvokeEntranceService"

    /** 同上：命令里的 `-e triggered_app com.coloros.smartsidebar`。 */
    const val SIDEBAR_TARGET_APP = "com.coloros.smartsidebar"
}
