package com.lookie.opluskey.config

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

    // 历史：曾有 key(suffix)（拼 plus_enabled 键名），2026-10-13 配置换 JSON 后零调用方已删；键名现由 ConfigCodec 独占。

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
    }
}
