package com.lookie.opluskey.core.model

/** 一个可重映射的按键。 */
enum class KeyId(
    val id: String,
    val label: String,
    /** 注入「放行」时用的原始 keyCode（真机按键上报的那个）。 */
    val keyCode: Int,
    /**
     * 认领按键时接受的 keyCode 白名单。
     * ColorOS 策略类（`StrategyActionButtonKeyLaunchApp`）派发的是它自己注入的内部码
     * 780 / 781 / 782，只认原始码 735 会把侧键放行，表现为模块装了却完全不被接管。
     */
    val acceptedKeyCodes: Set<Int>,
) {
    PLUS("plus", "侧键", 735, setOf(735, 780, 781, 782)),
    ;

    // 配置里的键名由 ConfigCodec 独占，本枚举只负责「有哪些键、各自认哪些码」。

    companion object {
        fun fromId(id: String): KeyId? = entries.firstOrNull { it.id == id }

        /**
         * 按原始 keyCode 认领按键（PM 兜底路径用），其余一律返回 null 放行。
         * 故意不含内部注入码：策略类路径已处理它们，这里再认会同一次按键会被判两次。
         * 电源键 / 音量键等必须原样交给系统（电源菜单、紧急呼叫、同按截屏都在系统侧）。
         */
        fun fromKeyCode(code: Int): KeyId? = entries.firstOrNull { it.keyCode == code }

        /** 按白名单认领按键（策略类路径用，含 ColorOS 内部注入码 780 / 781 / 782）。 */
        fun fromAnyKeyCode(code: Int): KeyId? = entries.firstOrNull { code in it.acceptedKeyCodes }

        /**
         * 截图按键码（`KEYCODE_SYSRQ`）—— 内置「截图」动作注入的就是它，**不是侧键**、不参与认领。
         *
         * 放在这里只因为一、5 要求「键码只定义在 [KeyId] 一处」：执行侧不许在
         * `inject/input/KeyInjector` 的伴生对象里另留一份字面量 —— 那正是这条规矩要禁止的写法。
         */
        const val KEYCODE_SYSRQ = 120

        /**
         * 通知中心（`KEYCODE_NOTIFICATION`）—— 内置「通知中心」动作注入的就是它。
         *
         *  一、5 要求键码只定义在本枚举一处，执行侧不许另留字面量。
         */
        const val KEYCODE_NOTIFICATION = 83

        /** 最近任务（`KEYCODE_APP_SWITCH`）。 */
        const val KEYCODE_APP_SWITCH = 187

        /** 静音键（`KEYCODE_VOLUME_MUTE`）—— 「响铃循环」那条动作注入的就是它。 */
        const val KEYCODE_VOLUME_MUTE = 164
    }
}
