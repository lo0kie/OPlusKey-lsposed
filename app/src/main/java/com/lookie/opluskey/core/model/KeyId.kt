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

        // 下面这一批是「系统导航」/「系统按键」/「媒体控制」三张菜单里的档位取值。
        //  它们是**动作**（绑给侧键之后注入一次），与「不接管物理音量键 / 电源键」那条纪律
        //  不冲突 —— 后者说的是不去拦截那几个键本身（见本枚举顶部的说明）。
        //  与上面几条同一口径：一、5 要求键码只定义在本枚举一处，取值由 `Action.keyToken` 拼。

        /** 主屏幕（`KEYCODE_HOME`）—— 「系统导航」菜单的一档。 */
        const val KEYCODE_HOME = 3

        /** 返回（`KEYCODE_BACK`）。 */
        const val KEYCODE_BACK = 4

        /** 音量 +（`KEYCODE_VOLUME_UP`）。 */
        const val KEYCODE_VOLUME_UP = 24

        /** 音量 −（`KEYCODE_VOLUME_DOWN`）。 */
        const val KEYCODE_VOLUME_DOWN = 25

        /** 电源键（`KEYCODE_POWER`）。 */
        const val KEYCODE_POWER = 26

        /** 播放 / 暂停（`KEYCODE_MEDIA_PLAY_PAUSE`）—— 「媒体控制」那一格的取值。 */
        const val KEYCODE_MEDIA_PLAY_PAUSE = 85

        /** 下一首（`KEYCODE_MEDIA_NEXT`）。 */
        const val KEYCODE_MEDIA_NEXT = 87

        /** 上一首（`KEYCODE_MEDIA_PREVIOUS`）。 */
        const val KEYCODE_MEDIA_PREVIOUS = 88

        /** 播放（`KEYCODE_MEDIA_PLAY`）—— 「无论如何都要播」。 */
        const val KEYCODE_MEDIA_PLAY = 126

        /** 暂停（`KEYCODE_MEDIA_PAUSE`）—— 「无论如何都要停」。 */
        const val KEYCODE_MEDIA_PAUSE = 127
    }
}
