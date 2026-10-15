package com.lookie.opluskey.core.model

import com.lookie.opluskey.ENFORCE_DEVICE_GATE

/**
 * **适配清单**：机型 + 系统版本。清单外的组合「注入不进行，只注入界面层」。
 *
 * 为什么要这张表：本模块靠**反射**挂进 ColorOS 的侧键实现（`StrategyActionButtonKeyLaunchApp`
 * 那几个接管点、`SystemActionButtonConfig` 的场景表、`FlexibleTaskController` 的小窗入口都是
 * ROM 私有的）。换一个机型、换一版 ROM，这些类与方法可能整体换名换形 —— 那时模块会「拦下来但
 * 什么都不做」，用户读到的是「按了没反应，可它还振了一下」，比直接不支持更糟
 * （见 `docs/device-findings.md` 十九）。所以清单外的组合一律不注入：注入侧那一套
 * （system_server 里的按键 Hook、宿主侧的手势劫持与接收器）全部跳过，**只保留界面层**
 * （设置页替换）—— 页面按「不可用」整页禁用，并说清是哪一档不对。
 *
 * **为什么要连系统版本一起锁**：机型不变、ROM 升一版，接管点照样可能换形（同一台机器换 ROM 与
 * 换机型是同一类风险）。所以判定不是「机型对不对」，而是「这一组（机型 + 系统版本）验没验过」：
 *  1) 机型与版本都在清单里 ⇒ 正常注入；
 *  2) 机型在清单里、系统版本不是清单上那一档 ⇒ **等待适配**（等作者针对这一版重新验接管点）；
 *  3) 机型不在清单里 ⇒ 本机用不了（等也没用）。
 * 第 2 档与第 3 档在界面上各说各的话，见 `ui/model/PageConst` 的两句提示。
 *
 * 注意：**本文件是纯数据 + 纯函数**：判定只吃三个字符串（机型 / Android 版本 / ROM 版本），
 * **不读 `android.os.Build`**。这样它才能被注入侧（只准依赖 `core/model` + `core/log` +
 * `core/reflect` + `core/wire`）和界面层同时引用；`Build` 那三个值由各自的调用点自己传
 * （见 `KeyModule` / `ui/state/LinkCheck.kt`）。
 *
 * **debug 包不做这道闸**：`ENFORCE_DEVICE_GATE = false` 的包里 [check] 一律返回 [Support.ADAPTED]
 * —— 判定照算、日志照打，只是不据此拒绝注入（理由见 `BuildFlags` 里那一份的注释）。release 必须打开。
 */
object DeviceSupport {
    /**
     * 清单里的一台机器。
     *
     * @param model `android.os.Build.MODEL` 的真机实测值（**不是** `ro.product.device`）
     * @param name 给人看的机型名（日志与「更多选项 → 适配清单」共用）
     * @param android `android.os.Build.VERSION.RELEASE`
     * @param rom ROM 版本号：`ro.build.version.oplusrom`（真机是 `V17.0.0`）去掉开头的 `V`，
     *   与 [romSeriesOf] 从 `Build.DISPLAY` 里取出来的那一串同口径
     */
    private class Adapted(
        val model: String,
        val name: String,
        val android: String,
        val rom: String,
    )

    /**
     * 适配清单（目前只有一加 15 = `PLK110`，Android 17 / ColorOS 17.0.0）。
     *
     * 加机型、加一版 ROM 都在这里加一行（同一机型多版 ROM 就多行），**并在真机上把接管点重新验一遍**
     * —— 那些类与方法都是 ROM 私有的，没验过就写进来等于赌用户来做实验。
     */
    private val ADAPTED =
        listOf(
            Adapted(model = "PLK110", name = "一加 15（PLK110）", android = "17", rom = "17.0.0"),
        )

    /** 判定结果（三档的口径见文件头）。 */
    enum class Support {
        /** 机型与系统版本都在清单里。 */
        ADAPTED,

        /** 机型在清单里，但系统版本不是清单上那一档 —— 等作者适配。 */
        PENDING,

        /** 机型不在清单里 —— 本机用不了。 */
        UNSUPPORTED,
    }

    /**
     * 本机这一组（机型 + 系统版本）在不在清单里。
     *
     * **这道闸在 debug 包里不生效**：`ENFORCE_DEVICE_GATE = false` 的包一律按 [Support.ADAPTED]
     * 返回（判定本身照算、日志照打，只是不据此拒绝注入）—— 开发时机型 / 系统版本随时在变，
     * 开机先被这道闸拦住会让人以为模块坏了（见 `BuildFlags`）。release 必须打开。
     *
     * 机型大小写与首尾空白都容错（`Build.MODEL` 各 ROM 写法偶有出入），空值一律不支持。
     * 版本**读不出来时不拦**（按 [Support.ADAPTED] 走）：读不出来是「我们没读到」，不是「用户版本
     * 不对」—— 拿它去锁，一台版本其实正常的机器会被整页禁用（要拦的是「读到了、且不是那一档」）。
     */
    fun check(
        model: String?,
        android: String?,
        rom: String?,
    ): Support {
        if (!ENFORCE_DEVICE_GATE) return Support.ADAPTED
        val m = model?.trim()?.uppercase().orEmpty()
        if (m.isEmpty()) return Support.UNSUPPORTED
        val hits = ADAPTED.filter { it.model.uppercase() == m }
        if (hits.isEmpty()) return Support.UNSUPPORTED
        val a = android?.trim().orEmpty()
        val r = rom?.trim().orEmpty()
        if (a.isEmpty() || r.isEmpty()) return Support.ADAPTED
        return if (hits.any { it.android == a && it.rom == r }) Support.ADAPTED else Support.PENDING
    }

    /**
     * 从 `android.os.Build.DISPLAY`（= `ro.build.display.id`，真机是 `PLK110_17.0.0.105(CN01)`）
     * 里取出 ROM 版本号（`17.0.0`）。
     *
     * 取法：砍掉地区后缀与机型前缀，再丢掉最后一段构建号 —— 形状是
     * `<机型>_<ROM 版本>.<构建号>(<地区>)`。**构建号必须丢掉**：它随每次 OTA 变（105 → 106），
     * 算进判据里会让一次月度补丁就把用户锁在门外，而那一档接管点通常没动过。
     *
     * 取不出来（段数不够，或段里有非数字 —— 老 ROM 写作 `11.A.06`）就返回空串，
     * 交给 [check] 按「读不出来不拦」处理。
     */
    fun romSeriesOf(display: String?): String {
        val parts =
            display
                ?.substringBefore('(')
                ?.trim()
                ?.substringAfter('_', "")
                ?.split('.')
                ?.filter { it.isNotEmpty() }
                ?: return ""
        if (parts.size < 3) return ""
        val head = parts.take(3)
        return if (head.all { seg -> seg.all { c -> c.isDigit() } }) head.joinToString(".") else ""
    }

    /** 日志用：把机型翻成人看得懂的名字；不在清单里就这样打出来，方便照抄进 [ADAPTED]。 */
    fun describe(model: String?): String {
        val m = model?.trim().orEmpty()
        if (m.isEmpty()) return "（机型读不出来）"
        return ADAPTED.firstOrNull { it.model.equals(m, ignoreCase = true) }?.name
            ?: "$m（不在适配清单里）"
    }

    /**
     * 清单里那台机器的**给人看的名字**（自带代号，如「一加 15（PLK110）」）——
     * 「适配清单」里「本机」那一行的主标题要摆它出来，用户一眼能对上清单上是哪一行。
     *
     *  与 [describe] 分开：那个是**日志**用的，会带上「（不在适配清单里）」这个尾巴；
     *  界面上「在不在清单」由那一行的结论（[Support]）去说，主标题再说一遍就是两句同义话。
     *
     * 不在清单里 / 读不出来一律给空串，调用点拿代号顶上。
     */
    fun adaptedNameOf(model: String?): String {
        val m = model?.trim().orEmpty()
        if (m.isEmpty()) return ""
        return ADAPTED.firstOrNull { it.model.equals(m, ignoreCase = true) }?.name.orEmpty()
    }

    /** 日志用：把系统版本拼成一句；哪一半读不出来就省哪一半。 */
    fun systemOf(
        android: String?,
        rom: String?,
    ): String {
        val a = android?.trim().orEmpty()
        val r = rom?.trim().orEmpty()
        return when {
            a.isNotEmpty() && r.isNotEmpty() -> "Android $a / ColorOS $r"
            a.isNotEmpty() -> "Android $a"
            r.isNotEmpty() -> "ColorOS $r"
            else -> "（系统版本读不出来）"
        }
    }
}
