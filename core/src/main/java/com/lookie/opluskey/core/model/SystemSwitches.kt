package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.rom.RomProfile

/**
 * 系统开关的**种类** —— 每一种就是清单里的一行，也是「这一类开关长什么样」的键。
 *
 * ## 为什么是个枚举，而不是一串字符串常量
 *
 *  原先种类 id 是散在各处的字面量（`"bt"` / `"rotation"` / `"nfc"`… 见 `SettingSwitch` 里那些
 *  `id == "..."`），字形又是界面上一张 `Map<String, () -> ImageVector>` —— 加一条开关要动
 *  **两张表**（[SystemSwitches.ITEMS] 与那张 map），漏了字形**不报错**，界面上只是掉成 `Dots`
 *  （看着像「这条本来就没配图标」）。
 *
 *  收成枚举之后，「加一条开关」= 这里加一项 + [SystemSwitches.ITEMS] 加一行；
 *  而界面那张字形表对枚举做 `when`，**不完备当场编译失败** —— 漏配图标这件事没有了。
 *
 * ## id 为什么还是字符串
 *
 *  它是**落盘取值**的一部分（`setting:<id>`），手改配置里可能出现枚举之外的 id。
 *  所以 `id` 是数据，[of] 认不出时给 `null`（调用方决定退到哪），而不是硬塞一个 `UNKNOWN`
 *  —— 那种兜底值会让「手改坏的配置」看起来像一条正常开关。
 */
enum class SwitchKind(
    val id: String,
) {
    /** 蓝牙。 */
    BT("bt"),

    /** WIFI。 */
    WIFI("wifi"),

    /** 移动数据（写 `Settings.Global`，Phone 自己跟）。 */
    CELL("cell"),

    /** 自动旋转（服务自己读状态取反）。 */
    ROTATION("rotation"),

    /** NFC。 */
    NFC("nfc"),

    /** 飞行模式。 */
    AIRPLANE("airplane"),

    /**
     * 定位服务 —— 开关的真身是 `Settings.Secure.location_mode`（0 = 关，1 = 仅设备、2 = 省电、
     *  3 = 高精度）。`LocationManagerService` 盯着这个键：写完那一刻
     *  `cmd location is-location-enabled` 当场就翻，所以宿主直接写它等于按了那颗开关。
     */
    LOCATION("location"),

    /** 免打扰（`notification.setZenMode`）。 */
    DND("dnd"),

    /**
     * 手电筒：宿主里由 `FlashlightController` 直接调 `CameraManager.setTorchMode`，
     *  **不走** `SettingSwitch` 那条路，所以分派要用 id 认一次。
     */
    FLASHLIGHT("flashlight"),

    /** 暗色模式。 */
    DARKMODE("darkmode"),

    /** 省电模式（写 `Settings.Global`）。 */
    POWERSAVE("powersave"),

    /**
     * 极暗模式 —— 真开关在**颜色显示服务**里（AOSP 的 reduce bright colors）。
     *
     *  要 `CONTROL_DISPLAY_COLOR_TRANSFORMS`（signature|privileged），宿主拿不到
     *  （试过一次：SecurityException），所以这一条整条转给 system_server 去调 `color_display`。
     *  **不是** `PowerManager`（这台机器上反射不到），也**不是** `Settings.*.reduce_brightness`
     *  （那两个键没人维护，写了没反应）。
     */
    EXTRADIM("extradim"),

    /**
     * 自动亮度 —— `Settings.System.SCREEN_BRIGHTNESS_MODE`（1 = 自动）。宿主有 `WRITE_SETTINGS`，
     * 写这个键 DisplayPowerController 自己会跟。
     */
    AUTOBRIGHTNESS("autobrightness"),
    ;

    companion object {
        /** id → 种类；**认不出给 `null`**（手改配置 / 将来 ROM 加的新开关，由调用方兜底）。 */
        fun of(id: String): SwitchKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 「系统开关」那一格点开的那一列开关 —— **由项目自己维护**的一份固定清单：有哪些开关、叫什么、
 * 拿什么 token 落盘，全在这一处。
 *
 * ## 为什么不是「控制中心磁贴」
 *
 *  控制中心里点得动的第三方磁贴必须**先在控制中心里加过**，而用户真正常绑的那几个（WIFI / 蓝牙 /
 *  移动数据 / NFC / 飞行模式 / 自动旋转 …）是 SystemUI 内置的简写 token、**没有组件名**，永远进不了
 *  那张列表。所以这一簇不再借控制中心的任何一种写法，自己定义动作类型：`setting:<id>`
 *  （见 [Action.Setting]）。
 *
 * ## 执行不用 root
 *
 *  宿主（`com.oplus.gesture`，签名权限）负责**读**当前状态并算出目标值；写状态分两条路：
 *  · `cell` / `powersave` 直接写 `Settings.Global`（Phone 与 PowerManagerService 各自有
 *    ContentObserver 盯着）；
 *  · 其余走广播交给 system_server（uid 1000）按名字反射调服务（`bridge/SettingCalls`）。
 *  自动旋转与 NFC 由服务自己读状态取反，宿主不参与。
 *
 * ## 加一条要动两处
 *
 *  [SwitchKind] 加一项（种类，也是字形表的键）、[SystemSwitches.ITEMS] 加一行（字典键与顺序）。
 *  两份字典要加键，真要能执行还要在 `SettingCalls` 里加一支。
 *
 *  **字形不用另加**：界面那张表对 [SwitchKind] 做 `when`，枚举加了而 `when` 没补齐会当场编译失败
 *  —— 原先它是一张 `Map<String, …>`，漏了只是掉成 `Dots`（看着像「这条本来就没配图标」）。
 */
object SystemSwitches {
    /**
     * 清单里的一条。
     *
     * @param kind 哪一种开关 —— 决定字形（界面对这个枚举做 `when`，漏配当场编译失败）
     * @param labelKey 字典键
     */
    class Item(
        val kind: SwitchKind,
        val labelKey: String,
    ) {
        /** 落盘取值 —— 由 [kind] 推，不再各写一遍 `setting:xxx` 字面量。 */
        val token: String = settingToken(kind.id)
    }

    /**
     * `setting:<id>` —— 系统开关的落盘写法。
     *
     *  走 [Action.Setting] 而不是自己拼字符串：`Action.parse` 那一侧的还原、图标表、执行侧
     *  认的都是这个类型带来的 token，自己拼一个近似值只会在某处对不上。
     */
    fun settingToken(id: String): String = Action.Setting(id).token

    /**
     * `setting:bt[:on|:off]` / `flashlight` → **开关种类 id**（`bt` / `flashlight`）—— 唯一一处。
     *
     *  三处要用它：清单条目声明字形（`Spec.glyph = BySwitchKind(...)`）、三档菜单
     *  （`switchStates`）、以及界面那张 `SWITCH_KIND_ICONS` 的查表键。
     *  三处各自 `removePrefix("setting:")` 的话，手电筒那条（token 不是 `setting:` 开头）
     *  就会切出整串 `flashlight` —— 恰好也命中，于是错的地方看不出来；换成别的写法才现形。
     */
    fun kindOf(token: String): String = (Action.parse(token) as? Action.Setting)?.id ?: token.removePrefix(Action.SETTING_PREFIX)

    /** 页面上的开关清单，顺序即显示顺序。 */
    val ITEMS: List<Item> =
        listOf(
            Item(SwitchKind.BT, Strings.SYS_SETTING_BT),
            Item(SwitchKind.WIFI, Strings.SYS_SETTING_WIFI),
            Item(SwitchKind.CELL, Strings.SYS_SETTING_CELL),
            Item(SwitchKind.ROTATION, Strings.SYS_SETTING_ROTATION),
            Item(SwitchKind.NFC, Strings.SYS_SETTING_NFC),
            Item(SwitchKind.AIRPLANE, Strings.SYS_SETTING_AIRPLANE),
            //  定位服务：紧挨着飞行模式（两者都是「无线电开不开」那一类）。宿主能直接写
            //  `Settings.Secure.location_mode`，见 [SwitchKind.LOCATION]。
            Item(SwitchKind.LOCATION, Strings.SYS_SETTING_LOCATION),
            //  免打扰与手电筒**也收进 `setting:` 这一族** —— 三态（切换 / 打开 / 关闭）要求
            //  「目标状态」能写在 token 里，而 `oplus:no_disturb` / `flashlight` 这两种老写法
            //  只能表达「切一下」。执行侧各自分派（见 `ActionRunner` 与 `SettingCalls`），
            //  老配置里那两种写法仍然按原路跑，不迁移、不丢。
            Item(SwitchKind.DND, Strings.SYS_SETTING_DND),
            Item(SwitchKind.FLASHLIGHT, Strings.SYS_SETTING_FLASHLIGHT),
            Item(SwitchKind.DARKMODE, Strings.SYS_SETTING_DARKMODE),
            Item(SwitchKind.POWERSAVE, Strings.SYS_SETTING_POWERSAVE),
            //  极暗：真开关在**颜色显示服务**里（AOSP 的 reduce bright colors）——
            //  `dumpsys color_display` 有一节 `Reduce bright colors: Activated: ...`。
            //  它要 `CONTROL_DISPLAY_COLOR_TRANSFORMS`（signature|privileged），宿主拿不到这个权限
            //  （宿主侧调过一次：SecurityException），所以这一条整条转给 system_server
            //  （`SettingCalls.extraDim` 调 `color_display` 服务）。
            //  注意 **不是** `PowerManager`（这台机器上反射不到），也**不是**
            //  `Settings.*.reduce_brightness`（那两个键没人维护，写了没反应）。
            Item(SwitchKind.EXTRADIM, Strings.SYS_SETTING_EXTRADIM),
            //  自动亮度：`Settings.System.SCREEN_BRIGHTNESS_MODE`（1 = 自动）。宿主有
            //  `WRITE_SETTINGS`，写这个键 DisplayPowerController 自己会跟。
            Item(SwitchKind.AUTOBRIGHTNESS, Strings.SYS_SETTING_AUTOBRIGHTNESS),
        )

    /**
     * **老写法**对应清单里的哪个 id（认不出返回 `null`）。
     *
     *  免打扰与手电筒早先是 `oplus:no_disturb` / `flashlight` 两种独立写法（执行只有「切换」
     *  一种语义）；三态改版后落盘统一成 `setting:dnd` / `setting:flashlight`。
     *  老配置**不迁移** —— 执行侧两种写法都还认（见 `ActionRunner` / `SettingSwitch`），
     *  但界面判定得靠这一条把它们归到同一个开关上，否则升级之后那一格不亮、菜单里也不打勾。
     */
    fun idOfLegacy(token: String): String? =
        when (token) {
            Action.FLASHLIGHT -> SwitchKind.FLASHLIGHT.id
            Action.OPLUS_PREFIX + RomProfile.NAME_NO_DISTURB -> SwitchKind.DND.id
            else -> null
        }
}
