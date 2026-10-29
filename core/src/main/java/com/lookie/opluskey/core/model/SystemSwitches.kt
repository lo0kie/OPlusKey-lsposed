package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

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
 * ## 加一条要动三处
 *
 *  这里（清单 + 字典键）、`ActionIcons.actionIcon`（`setting:*` 的字形表）、两份字典；
 *  真要能执行还要在 `SettingCalls` 里加一支 id。
 */
object SystemSwitches {
    /**
     * 清单里的一条。
     *
     * @param token 落盘取值 —— 必须是 `Action.parse` 还原得回来的一串（`setting:wifi` / `flashlight`）
     * @param labelKey 字典键
     * @param label 兜底中文（字典缺这条时显示它）
     */
    class Item(
        val token: String,
        val labelKey: String,
        val label: String,
    )

    /**
     * `setting:<id>` —— 系统开关的落盘写法。
     *
     *  走 [Action.Setting] 而不是自己拼字符串：`Action.parse` 那一侧的还原、图标表、执行侧
     *  认的都是这个类型带来的 token，自己拼一个近似值只会在某处对不上。
     */
    fun settingToken(id: String): String = Action.Setting(id).token

    /**
     * 手电筒在清单里的 id（落盘 `setting:flashlight`）。
     *
     *  它的执行**不在** `SettingSwitch` 那条路上 —— 宿主里由 `FlashlightController` 直接调
     *  `CameraManager.setTorchMode`，所以分派要用这个 id 认一次。
     */
    const val ID_FLASHLIGHT = "flashlight"

    /** 免打扰的 id（落盘 `setting:dnd`）—— system_server 侧走 `notification.setZenMode`。 */
    const val ID_DND = "dnd"

    /** 极暗模式的 id（落盘 `setting:extradim`）—— 走 `PowerManager` 的 reduce-brightness。 */
    const val ID_EXTRADIM = "extradim"

    /** 自动亮度的 id（落盘 `setting:autobrightness`）—— 写 `screen_brightness_mode`。 */
    const val ID_AUTOBRIGHTNESS = "autobrightness"

    /**
     * 定位服务的 id（落盘 `setting:location`）。
     *
     *  开关的真身是 `Settings.Secure.location_mode`（0 = 关，1 = 仅设备、2 = 省电、3 = 高精度）。
     *  `LocationManagerService` 盯着这个键：写完那一刻 `cmd location is-location-enabled` 当场就翻，
     *  所以宿主写它等于按了那颗开关，不必借 system_server 调 `ILocationManager`。
     */
    const val ID_LOCATION = "location"

    /** 页面上的开关清单，顺序即显示顺序。 */
    val ITEMS: List<Item> =
        listOf(
            Item(settingToken("bt"), Strings.SYS_SETTING_BT, "蓝牙"),
            Item(settingToken("wifi"), Strings.SYS_SETTING_WIFI, "WIFI"),
            Item(settingToken("cell"), Strings.SYS_SETTING_CELL, "移动数据"),
            Item(settingToken("rotation"), Strings.SYS_SETTING_ROTATION, "自动旋转"),
            Item(settingToken("nfc"), Strings.SYS_SETTING_NFC, "NFC"),
            Item(settingToken("airplane"), Strings.SYS_SETTING_AIRPLANE, "飞行模式"),
            //  定位服务：紧挨着飞行模式（两者都是「无线电开不开」那一类）。宿主能直接写
            //  `Settings.Secure.location_mode`，见 [ID_LOCATION]。
            Item(settingToken(ID_LOCATION), Strings.SYS_SETTING_LOCATION, "定位服务"),
            //  免打扰与手电筒**也收进 `setting:` 这一族** —— 三态（切换 / 打开 / 关闭）要求
            //  「目标状态」能写在 token 里，而 `oplus:no_disturb` / `flashlight` 这两种老写法
            //  只能表达「切一下」。执行侧各自分派（见 `ActionRunner` 与 `SettingCalls`），
            //  老配置里那两种写法仍然按原路跑，不迁移、不丢。
            Item(settingToken(ID_DND), Strings.SYS_SETTING_DND, "免打扰"),
            Item(settingToken(ID_FLASHLIGHT), Strings.SYS_SETTING_FLASHLIGHT, "手电筒"),
            Item(settingToken("darkmode"), Strings.SYS_SETTING_DARKMODE, "暗色模式"),
            Item(settingToken("powersave"), Strings.SYS_SETTING_POWERSAVE, "省电模式"),
            //  极暗：真开关在**颜色显示服务**里（AOSP 的 reduce bright colors）——
            //  `dumpsys color_display` 有一节 `Reduce bright colors: Activated: ...`。
            //  它要 `CONTROL_DISPLAY_COLOR_TRANSFORMS`（signature|privileged），宿主拿不到这个权限
            //  （宿主侧调过一次：SecurityException），所以这一条整条转给 system_server
            //  （`SettingCalls.extraDim` 调 `color_display` 服务）。
            //  注意 **不是** `PowerManager`（这台机器上反射不到），也**不是**
            //  `Settings.*.reduce_brightness`（那两个键没人维护，写了没反应）。
            Item(settingToken(ID_EXTRADIM), Strings.SYS_SETTING_EXTRADIM, "极暗模式"),
            //  自动亮度：`Settings.System.SCREEN_BRIGHTNESS_MODE`（1 = 自动）。宿主有
            //  `WRITE_SETTINGS`，写这个键 DisplayPowerController 自己会跟。
            Item(settingToken(ID_AUTOBRIGHTNESS), Strings.SYS_SETTING_AUTOBRIGHTNESS, "自动亮度"),
        )

    /** 某个落盘 token 对应清单里的哪一条（不是这十种写法就返回 `null`）。 */
    fun find(token: String): Item? = ITEMS.firstOrNull { it.token == token }

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
            Action.FLASHLIGHT -> ID_FLASHLIGHT
            Action.OPLUS_PREFIX + RomProfile.NAME_NO_DISTURB -> ID_DND
            else -> null
        }

    /**
     * 按 `setting:<id>` 里的 `<id>` 找那一条 —— 三态写法（`setting:wifi:on`）剥掉后缀之后用这个。
     */
    fun titleOfId(id: String): String? = find(settingToken(id))?.let { I18n.get(it.labelKey, it.label) }

    /** 某个 token 的显示名（当前语言）—— 步骤行 / 执行提示 / 顶栏小标题共用这一处。 */
    fun titleOf(token: String): String? = find(token)?.let { I18n.get(it.labelKey, it.label) }
}
