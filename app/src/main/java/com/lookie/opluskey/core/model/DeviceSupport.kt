package com.lookie.opluskey.core.model

import com.lookie.opluskey.ENFORCE_DEVICE_GATE
import java.util.Locale

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
 *  2) 机型在清单里、系统版本不是这一行 `systems` 里验过的**任何一项** ⇒ **等待适配**
 *     （等作者针对这一版重新验接管点）；
 *  3) 机型不在清单里 ⇒ 本机用不了（等也没用）。
 * 第 2 档与第 3 档在界面上各说各的话，见 `ui/model/PageConst` 的两句提示。
 *
 * 注意：**判定本体是纯函数**：只吃三个字符串（机型 / Android 版本 / ROM 版本），`Build` 那三个值
 * 由各自的调用点自己传进来。这样它才能被注入侧（只准依赖 `core/model` + `core/log` +
 * `core/reflect` + `core/wire`）和界面层同时引用。
 *  唯一的例外是 [currentRomSeries]：它自己读一次 `Build.DISPLAY`（公开 API，注入侧同样可用）——
 *  「本机是哪一档 ROM」这个输入被三处要用（适配闸、内置命令档位表、安装期判据行），
 *  各读一遍再各解析一遍，口径迟早会分叉，而那是「本机判成哪一档」这种最不该出错的地方。
 *
 * 加机型时：这里加一行 [Adapted]（**同一台机器多验一版 ROM 就在它那行的 `systems` 里加一项，
 * 不再多写一行**），**并且**去 `core/model/RomProfile` 与 `bridge/Framework` 各加一档
 * （内置命令的落点随 ROM 变，是另一张表；两道闸各管一件事）。
 *
 * **debug 包不做这道闸**：`ENFORCE_DEVICE_GATE = false` 的包里 [check] 一律返回 [Support.ADAPTED]
 * —— 判定照算、日志照打，只是不据此拒绝注入（理由见 `BuildFlags` 里那一份的注释）。release 必须打开。
 */
object DeviceSupport {
    /**
     * 一组验过的**系统版本** —— 这台机器上「Android 版本 + ROM 系列」的一个组合。
     *
     * @param android `android.os.Build.VERSION.RELEASE`
     * @param rom ROM 版本号：**[romSeriesOf] 从 `Build.DISPLAY` 里取出来的那一串** —— [check]
     *   收到的就是它（两个调用点都传 [currentRomSeries]），所以这里必须按它写。
     *   注意：**别照抄 `ro.build.version.oplusrom`**：两者只是**碰巧**在 PLK110 上一致
     *   （`V17.0.0` ↔ display 里的 `17.0.0`）；真机实测 OnePlus 12 / ColorOS 16 上并不一致
     *   （`V16.1.0` ↔ display 里的 `16.0.10`）—— 照抄那一串，这一项永远命不中。
     */
    private class SystemVersion(
        val android: String,
        val rom: String,
    )

    /**
     * 清单里的一台机器，以及这台机器上**验过的那些系统版本**。
     *
     * @param model `android.os.Build.MODEL` 的真机实测值（**不是** `ro.product.device`）
     * @param name 给人看的机型名（日志与「更多选项 → 适配清单」共用）。**用英文名** ——
     *   真机 `ro.vendor.oplus.market.enname` 那一份（`OnePlus 15`）。**不运行时读那个属性**：
     *   它是 hidden 接口，界面进程会被非 SDK 限制拦下（与 `ro.build.version.oplusrom` 同一类，
     *   见文件头），所以照抄进这张表
     * @param systems 验过的组合；**一台机器一行**，同一台机器多一版 ROM 就往这里加一项
     *   （不必再多写一行 [Adapted]）。命中口径：本机那一组与其中**任意一项**相同即算适配
     */
    private class Adapted(
        val model: String,
        val name: String,
        val systems: List<SystemVersion>,
    )

    /**
     * 适配清单（目前只有一台：OnePlus 15 = `PLK110`）。
     *
     * **加机型加一行 [Adapted]，同一机型多一版 ROM 在它的 [Adapted.systems] 里加一项**；
     * 两种都要**在真机上把接管点重新验一遍** —— 那些类与方法都是 ROM 私有的，
     * 没验过就写进来等于赌用户来做实验。
     *
     * `rom` 要**逐字**等于那台机器从 `Build.DISPLAY` 里取出来的系列串（取法见 [romSeriesOf]，
     * 口径的坑见 [SystemVersion.rom]）：对不上的表现是「同一台机器一升小版本就变成等待适配」。
     * 往这里加项之前，先在那台机器上量一次 `getprop ro.build.display.id`。
     *
     * **清单里放的是「这台机器上模块能用、且接管点验过」**，不是「能装上去」：机器连侧键都没有时
     * 不该进这张表（进了就是让 release 去一台根本没有那个键的机器上挂 Hook）。已经量过、明确
     * **不上**清单的一台：OnePlus 12 = `PJD110`（2026-10-16 实测）—— 这台是**三段式按键、没有侧键**，
     * 模块的核心（接管侧键）对它本就不适用；当时拿它验的是**版本锁**（判定三档）与那套口径，
     * 与「能不能用」是两件事。往这里加机型之前先确认那条键在。
     *
     * **没量过就不许进这张表**（一、13）：猜进来的那一档会让本机判成 [Support.ADAPTED]，
     * 于是注入照做、而接管点其实没验过 —— 表现正是文件头说的「拦下来但什么都不做」，
     * 用户读到的是「按了没反应，可它还振了一下」，比直接不支持更难查。
     * 已经撤掉的一条：PLK110 在 Android 16 上的 `16.0.0` —— 那是按「一加 15 出厂系列」猜的，
     * 而这个猜法**本身已被证伪过一次**（OnePlus 12 / Android 16 实测到的是 `16.0.10`，不是 `16.0.0`）。
     * ⇒ 真到了 PLK110 跑 Android 16 的机器上，先量一次 `getprop ro.build.display.id`，
     *  把拿到的那一串**逐字**补进 `systems`；在那之前 PLK110 + Android 16 走「等待适配」那一档。
     */
    private val ADAPTED =
        listOf(
            Adapted(
                model = "PLK110",
                name = "OnePlus 15（PLK110）",
                systems =
                    listOf(
                        // 本机实测：PLK110 / ColorOS 17.0.0（`Build.DISPLAY` 里取出的系列串）。
                        // 这台机器再验一版 ROM，就在这一行的 `systems` 里加一项，不要多写一行 [Adapted]。
                        SystemVersion(android = "17", rom = "17.0.0"),
                    ),
            ),
        )

    /**
     * 清单里登记过的 ROM 系列 —— **与另外两张表（`RomProfile.PROFILES` / `Framework.HOOK_SETS`）
     * 对账用**。
     *
     *  加一版 ROM 要往三处各加一行（能注入 / 有落点 / 接管点候选），漏一处不会编译报错，
     *  表现是「等待适配」「接管点一个都没找到」「照别版的落点去起服务」三种难查的故障
     *  —— 所以这里把清单读出来，供安装期那行对账日志比一遍。
     */
    fun adaptedSeries(): Set<String> = ADAPTED.flatMap { it.systems }.map { it.rom }.toSet()

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
     * 大小写转换**显式给 `Locale.ROOT`**：不带 Locale 的 `uppercase()` 吃系统区域设置，
     * 土耳其语环境里 `i` → `İ`，同一个机型在那种环境就会被判成「不在清单里」。
     * 版本**读不出来时不拦**（按 [Support.ADAPTED] 走）：读不出来是「我们没读到」，不是「用户版本
     * 不对」—— 拿它去锁，一台版本其实正常的机器会被整页禁用（要拦的是「读到了、且不是那一档」）。
     */
    fun check(
        model: String?,
        android: String?,
        rom: String?,
    ): Support {
        if (!ENFORCE_DEVICE_GATE) return Support.ADAPTED
        val m = model?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (m.isEmpty()) return Support.UNSUPPORTED
        val hits = ADAPTED.filter { it.model.uppercase(Locale.ROOT) == m }
        if (hits.isEmpty()) return Support.UNSUPPORTED
        val a = android?.trim().orEmpty()
        val r = rom?.trim().orEmpty()
        if (a.isEmpty() || r.isEmpty()) return Support.ADAPTED
        // 同一台机器只要有**任一项**验过的组合命中就算适配（多版 ROM 都写在同一行的 [Adapted.systems] 里）
        val hit =
            hits.any { device ->
                device.systems.any { system -> system.android == a && system.rom == r }
            }
        return if (hit) Support.ADAPTED else Support.PENDING
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

    /**
     * 本机 ROM 系列（`Build.DISPLAY` → [romSeriesOf]），**进程内算一次**。
     *
     *  收在这里的理由见文件头：适配闸、内置命令档位表、安装期判据行都要它。
     *  `Build.DISPLAY` 是公开 API（`ro.build.version.oplusrom` 是 hidden 的，界面进程读不到），
     *  取法与判据同 [romSeriesOf]。
     */
    val currentRomSeries: String by lazy { romSeriesOf(android.os.Build.DISPLAY) }

    /**
     * 本机机型（`Build.MODEL`）—— 同样是**进程内算一次**。
     *
     *  用 `MODEL` 而不是 `ro.product.device`：真机上 `MODEL` = `PLK110`、`DEVICE` = `OP60FFL1`，
     *  本文件那张清单按前者写（各进程读到的都一样）。
     *  与 [currentRomSeries] 收在一处的理由相同：适配闸、小窗几何表、安装期判据行都要它。
     */
    val currentModel: String by lazy {
        android.os.Build.MODEL
            .orEmpty()
    }

    /** 本机 Android 版本（`android.os.Build.VERSION.RELEASE`，真机是 `17`）。 */
    val currentAndroid: String = android.os.Build.VERSION.RELEASE

    /**
     * 本机那一组（机型 + Android + ROM）过这道闸的结果 —— **进程内算一次**。
     *
     *  机型与系统版本都不会中途变，算一次就够。
     *
     *  为什么收在这里：这三样（机型 / Android / ROM）与判定结果原来都挂在 `KeyModule` 的 companion
     *  上，而那是**入口**类 —— 机型身份不是它的职责（审计项「`KeyModule` 一个类装了三件事」里的
     *  第三件）。收进本对象之后，「本机是哪一档」全项目只有这一处能回答。
     */
    val verdict: Support by lazy { check(currentModel, currentAndroid, currentRomSeries) }

    /** 清单命中的简写（判定本体是 [verdict]）—— 「注入整套」还是「只注入界面层」看它。 */
    val isAdapted: Boolean get() = verdict == Support.ADAPTED

    /**
     * 「为什么没注入」那一句 —— 机型不认识、还是这一版系统没适配，两档分开说：
     * 前者是「本机用不了」，后者是「等作者适配」，用户该做的事完全不同。
     */
    fun whyNotInjecting(): String =
        when (verdict) {
            Support.PENDING -> "机型在适配清单里，但系统版本不是那一档（等待适配）"
            else -> "机型不在适配清单里"
        }

    /** 日志用：把机型翻成人看得懂的名字；不在清单里就这样打出来，方便照抄进 [ADAPTED]。 */
    fun describe(model: String?): String {
        val m = model?.trim().orEmpty()
        if (m.isEmpty()) return "（机型读不出来）"
        return ADAPTED.firstOrNull { it.model.equals(m, ignoreCase = true) }?.name
            ?: "$m（不在适配清单里）"
    }

    /**
     * 清单里那台机器的**给人看的名字**（自带代号，如「OnePlus 15（PLK110）」）——
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
