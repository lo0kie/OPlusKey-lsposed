package com.lookie.opluskey.config

/**
 * 配置的数据结构：三种手势、单键配置、全局配置、整份配置。
 */

/** 三种手势。 */
enum class Gesture(val id: String, val label: String) {
    SINGLE("single", "单击"),
    DOUBLE("double", "双击"),
    LONG("long", "长按"),
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
     * 长按绑的是不是「多条命令」（`cmd:<名字>` 且命令库里那条是串行命令）。
     * 只由宿主算好、随配置广播带过来（`ConfigBroadcast.EXTRA_LONG_SERIAL`），注入侧不自己算：
     * 算它要读宿主私有目录的命令库，system_server 进不去带 MCS 分类的目录
     * （`docs/device-findings.md` 六之五 / 九之一），读不到就恒 false，闸门失效。
     * 默认 false = 与早先行为一致（配置文件里没有这一位，不回写）。
     */
    val longIsSerial: Boolean = false,
) {
    fun actionOf(gesture: Gesture): Action = when (gesture) {
        Gesture.SINGLE -> single
        Gesture.DOUBLE -> double
        Gesture.LONG -> long
    }

    companion object {
        const val DEFAULT_LONG_PRESS_MS = 500

        /** 长按阈值下限：人手一次按压通常 80~150ms，低于自然按压时长会把每次按下都判成长按。 */
        const val MIN_LONG_PRESS_MS = 250
        const val MAX_LONG_PRESS_MS = 800
        const val DEFAULT_REPEAT_INTERVAL_MS = 300
        const val MIN_REPEAT_INTERVAL_MS = 100

        const val MAX_REPEAT_INTERVAL_MS = 3000

        /** 某个键的默认值：默认不接管，行为与原生完全一致。 */
        fun defaultFor(id: KeyId) = KeyConfig(
            id = id,
            enabled = false,
            single = Action.Native,
            double = Action.None,
            long = Action.None,
            longPressMs = DEFAULT_LONG_PRESS_MS,
            longRepeat = false,
            longRepeatIntervalMs = DEFAULT_REPEAT_INTERVAL_MS,
        )
    }
}

/** 全局配置（不属于任何单个键）。 */
data class GlobalConfig(
    val doubleClickMs: Int,
    val vibrate: Boolean,
    /**
     * 双击为 [Action.None] 时抬手立即触发单击，不等 [doubleClickMs] 的双击窗口。
     * 代价：开启后连按两下会变成两次单击（关闭时第二下会被吞掉）。
     */
    val instantSingle: Boolean = DEFAULT_INSTANT_SINGLE,
    /** 执行自定义命令时弹一条 Toast（内容是命令名）。 */
    val toastOnRun: Boolean = DEFAULT_TOAST_ON_RUN,
) {
    companion object {
        const val DEFAULT_DOUBLE_CLICK_MS = 300

        /** 双击窗口下限：两次完整按压加抬手间隔的最短时间。 */
        const val MIN_DOUBLE_CLICK_MS = 200
        const val MAX_DOUBLE_CLICK_MS = 800

        /** [GlobalConfig.instantSingle] 的默认值（不等窗口）。 */
        const val DEFAULT_INSTANT_SINGLE = true

        /** [GlobalConfig.toastOnRun] 的默认值：默认不弹。 */
        const val DEFAULT_TOAST_ON_RUN = false

        val DEFAULT = GlobalConfig(
            doubleClickMs = DEFAULT_DOUBLE_CLICK_MS,
            vibrate = false,
        )
    }
}

/** 整份配置。 */
data class Config(
    val keys: Map<KeyId, KeyConfig>,
    val global: GlobalConfig,
) {
    fun key(id: KeyId): KeyConfig = keys[id] ?: KeyConfig.defaultFor(id)

    companion object {
        val DEFAULT = Config(
            keys = KeyId.entries.associateWith { KeyConfig.defaultFor(it) },
            global = GlobalConfig.DEFAULT,
        )
    }
}
