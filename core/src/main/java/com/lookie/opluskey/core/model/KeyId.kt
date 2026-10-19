package com.lookie.opluskey.core.model

/**
 * 一个可重映射的按键 —— **身份由 [id] 决定**（配置的键名、广播的 extra 前缀、日志、去重都用它）。
 *
 * ## 为什么不是枚举了
 *
 *  侧键是编译期就知道的那一颗；**自定义按键是用户运行时收集来的**，数量与键码都不在编译期。
 *  枚举装不下这种东西，而把「运行时的清单」塞进枚举的伴生对象（一个可写注册表）会让
 *  `core` 带上跨调用可变状态 —— 那个状态在三个进程里各有一份，谁先写谁说了算，
 *  正是本项目一直在躲的那类 bug。
 *
 *  所以：**清单只属于 `Config`**（`Config.customKeys`），本类只负责「一颗键长什么样」。
 *  要按 id / 键码找键，从你手上那份 `Config` 找（`Config.keyForId` / `Config.keyForCode`）——
 *  [fromId] / [fromKeyCode] / [fromAnyKeyCode] 只认**内置键**，是「还没拿到配置」时的判定；
 *  生产路径（拦截点认领、界面解析）一律走 `Config` 那两张表，自定义键才认得到。
 *
 * ## 判等按 [id]
 *
 *  `Map<KeyId, KeyConfig>`、去重、广播解包都靠它。同样的 `id` 就是同一颗键，
 *  名字（[label]）改了不影响身份 —— 改名是改显示，不是换一颗键。
 */
class KeyId private constructor(
    /** 稳定身份：内置键是 `plus`，自定义键是 `ck1` `ck2`（见 [customId]）。 */
    val id: String,
    /** 显示名。自定义键是用户收集那一刻定下的字面量，不随界面语言变。 */
    val label: String,
    /** 注入「放行」时用的原始 keyCode（真机按键上报的那个）。 */
    val keyCode: Int,
    /**
     * 认领按键时接受的 keyCode 白名单。
     * ColorOS 策略类（`StrategyActionButtonKeyLaunchApp`）派发的是它自己注入的内部码
     * 780 / 781 / 782，只认原始码 735 会把侧键放行，表现为模块装了却完全不被接管。
     *
     *  自定义键只有「收集时看到的那一个码」—— 内部码是策略类给**侧键**专用的，
     *  别的键不走那条路（见 `docs/device-findings.md`）。
     */
    val acceptedKeyCodes: Set<Int>,
    /** 是不是用户收集来的。收集来的可改名 / 可删，内置的不可 —— 判定只在这一处。 */
    val isCustom: Boolean,
) {
    // 手写而不是 data class：data class 会把 label / keyCode 也算进判等，
    // 于是「改个名」在 `Map<KeyId, KeyConfig>` 里就成了另一颗键（配置查不到、绑定全丢）。

    override fun equals(other: Any?): Boolean = this === other || (other is KeyId && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "KeyId($id)"

    /**
     * 换一个显示名 —— **身份（[id]）不变**，所以改完仍是同一颗键、绑定不丢。
     * 内置键不该走这条路（界面上它不可改名），但这里不另设闸：闸在 `Config.withKeyName` 那层。
     */
    fun copyName(name: String): KeyId = KeyId(id, name, keyCode, acceptedKeyCodes, isCustom)

    // 配置里的键名由 ConfigCodec 独占，本类只负责「一颗键有哪些属性」。

    companion object {
        /** 侧键 —— **唯一**的内置键。 */
        val PLUS = KeyId("plus", "侧键", 735, setOf(735, 780, 781, 782), isCustom = false)

        /** 编译期就存在的那些键。界面要「内置 + 自定义」全列时用 `Config.allKeys`。 */
        val builtins: List<KeyId> = listOf(PLUS)

        /** 自定义键 id 的前缀：`ck1` / `ck2` ……（序号只增不减，与 `Config.customKeys` 的顺序无关）。 */
        const val CUSTOM_ID_PREFIX = "ck"

        /**
         * 自定义键的**数量上限** —— 三颗（用户口径）。
         *
         *  收集入口按它拦（`ui/screen/binding/CollectKeyDialog.collectKey`）；菜单里那几行一屏放得下，
         *  也不至于一堆键去抢同一颗物理键。
         */
        const val MAX_CUSTOM_KEYS = 3

        /** 第 [seq] 颗自定义键的 id —— 序号由调用方维护（它要保证不被复用）。 */
        fun customId(seq: Int): String = CUSTOM_ID_PREFIX + seq

        /**
         * 一颗用户收集来的键。
         *
         * @param id 必须是 [customId] 生成的那个稳定 id，**不能用键码**：同一颗物理键上报的码
         *   可能随驱动 / 系统版本变化，而绑定不该跟着丢。
         */
        fun custom(
            id: String,
            name: String,
            keyCode: Int,
        ): KeyId = KeyId(id, name, keyCode, setOf(keyCode), isCustom = true)

        /** 按 id 找**内置**键；自定义键返回 null（它们只存在于配置里，见类注释）。 */
        fun fromId(id: String): KeyId? = builtins.firstOrNull { it.id == id }

        /**
         * 按原始 keyCode 认领**内置**键（PM 兜底路径用），其余一律返回 null 放行。
         * 故意不含内部注入码：策略类路径已处理它们，这里再认会同一次按键会被判两次。
         * 电源键 / 音量键等必须原样交给系统（电源菜单、紧急呼叫、同按截屏都在系统侧）。
         */
        fun fromKeyCode(code: Int): KeyId? = builtins.firstOrNull { it.keyCode == code }

        /** 按白名单认领**内置**键（策略类路径用，含 ColorOS 内部注入码 780 / 781 / 782）。 */
        fun fromAnyKeyCode(code: Int): KeyId? = builtins.firstOrNull { code in it.acceptedKeyCodes }

        /**
         * 截图按键码（`KEYCODE_SYSRQ`）—— 内置「截图」动作注入的就是它，**不是侧键**、不参与认领。
         *
         * 放在这里只因为一、5 要求「键码只定义在 [KeyId] 一处」：执行侧不许在
         * `bridge/KeyInjector` 的伴生对象里另留一份字面量 —— 那正是这条规矩要禁止的写法。
         */
        const val KEYCODE_SYSRQ = 120

        /**
         * 通知中心（`KEYCODE_NOTIFICATION`）—— 内置「通知中心」动作注入的就是它。
         *
         *  一、5 要求键码只定义在一处，执行侧不许另留字面量。
         */
        const val KEYCODE_NOTIFICATION = 83

        /** 最近任务（`KEYCODE_APP_SWITCH`）。 */
        const val KEYCODE_APP_SWITCH = 187

        /** 静音键（`KEYCODE_VOLUME_MUTE`）—— 「响铃循环」那条动作注入的就是它。 */
        const val KEYCODE_VOLUME_MUTE = 164

        // 下面这一批是「系统导航」/「系统按键」/「媒体控制」三张菜单里的档位取值。
        //  它们是**动作**（绑给侧键之后注入一次），与「不接管物理音量键 / 电源键」那条纪律
        //  不冲突 —— 后者说的是不去拦截那几个键本身（见本类顶部的说明）。
        //  与上面几条同一口径：一、5 要求键码只定义在一处，取值由 `Action.keyToken` 拼。

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

        /**
         * **动作会注入的那一批码** —— 「这一下是不是我们自己注入的合成事件」就按这张表判
         * （见 `inject/SideKeyInterceptor.isSynthetic`）。注入的键会再走一遍按键路径，
         * 认领了就是自持循环，所以这批码一律放行、不认领。
         */
        val INJECTABLE_CODES: Set<Int> =
            setOf(
                KEYCODE_SYSRQ,
                KEYCODE_NOTIFICATION,
                KEYCODE_APP_SWITCH,
                KEYCODE_VOLUME_MUTE,
                KEYCODE_HOME,
                KEYCODE_BACK,
                KEYCODE_VOLUME_UP,
                KEYCODE_VOLUME_DOWN,
                KEYCODE_POWER,
                KEYCODE_MEDIA_PLAY_PAUSE,
                KEYCODE_MEDIA_NEXT,
                KEYCODE_MEDIA_PREVIOUS,
                KEYCODE_MEDIA_PLAY,
                KEYCODE_MEDIA_PAUSE,
            )

        /**
         * **不许被收集成自定义键**的那一批 —— 就是 [INJECTABLE_CODES] 去掉音量±与电源。
         *
         *  留下的这些是「收下就会自持循环」的码（主屏幕 / 返回 / 媒体 / 截图 / 通知中心 /
         *  最近任务 / 静音）：动作会注入它们，收集成键之后按一下等于自己触发自己。
         *
         *  **音量±与电源允许自定义**（用户口径）：它们同样在可注入表里，但注入的那一下仍由
         *  [INJECTABLE_CODES] 认出来、放行给系统，所以收下它们不会形成循环。代价是收下并启用
         *  之后这两颗键归模块管（没收集时照常放行给系统）。
         */
        val RESERVED_CODES: Set<Int> =
            INJECTABLE_CODES -
                setOf(
                    KEYCODE_VOLUME_UP,
                    KEYCODE_VOLUME_DOWN,
                    KEYCODE_POWER,
                )
    }
}
