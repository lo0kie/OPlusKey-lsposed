package com.lookie.opluskey.core.model

/**
 * **允许运行的机型表**（只在一加 15 上跑，其他机型「注入不进行，只注入 UI 层」）。
 *
 * 为什么要这张表：本模块靠**反射**挂进 ColorOS 的侧键实现（`StrategyActionButtonKeyLaunchApp` 那几个
 * 接管点、`SystemActionButtonConfig` 的场景表、`FlexibleTaskController` 的小窗入口都是 ROM 私有的）。
 * 换一版 ROM / 换一个机型，这些类与方法可能整体换名换形 —— 那时模块会「拦下来但什么都不做」，
 * 用户读到的是「按了没反应，可它还振了一下」，比直接不支持更糟（见 `docs/device-findings.md` 十九）。
 * 所以干脆**在允许表外一律不注入**：注入侧那一套（system_server 里的按键 Hook、宿主侧的
 * 手势劫持与接收器）全部跳过，**只保留界面层**（设置页替换）—— 页面按「不可用」整页禁用，
 * 用户至少能看到一句「未知设备，此设备不可用」。
 *
 * 注意：**本文件是纯数据 + 纯函数**：判定只吃一个机型字符串，**不读 `android.os.Build`**。
 * 这样它才能被注入侧（只准依赖 `core/model` + `core/log` + `core/reflect` + `core/wire`）
 * 和界面层同时引用；`Build.MODEL` 由各自的调用点自己传（见 `KeyModule` / `ui/state/LinkCheck.kt`）。
 */
object DeviceSupport {

    /**
     * 允许的机型（`android.os.Build.MODEL`，真机实测值不是 `ro.product.device`）。
     *
     * 目前只有 **一加 15 = `PLK110`**。加机型就在这里加一行，并在 [NAMES] 里补上中文名。
     */
    private val ALLOWED_MODELS = setOf("PLK110")

    /** 机型 → 人能读的名字（只用于日志；不在表里的原样打出来）。 */
    private val NAMES = mapOf("PLK110" to "一加 15（PLK110）")

    /**
     * 本机机型是否允许运行。
     *
     * 大小写与首尾空白都容错（`Build.MODEL` 各 ROM 写法偶有出入），空值一律不支持。
     */
    fun isSupported(model: String?): Boolean {
        val m = model?.trim()?.uppercase() ?: return false
        return m.isNotEmpty() && ALLOWED_MODELS.any { it.uppercase() == m }
    }

    /** 日志用：把机型翻成人看得懂的名字；不在表里就这样打出来，方便照抄进 [ALLOWED_MODELS]。 */
    fun describe(model: String?): String {
        val m = model?.trim().orEmpty()
        if (m.isEmpty()) return "（机型读不出来）"
        return NAMES.entries.firstOrNull { it.key.equals(m, ignoreCase = true) }?.value
            ?: "$m（不在允许表里）"
    }
}
