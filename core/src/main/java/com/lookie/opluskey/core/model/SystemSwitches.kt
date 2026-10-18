package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

/**
 * 「系统开关」那一格点开的十条 —— **由项目自己维护**的一份固定清单：有哪些开关、叫什么、
 * 拿什么 token 落盘，全在这一处。
 *
 * ## 为什么不是「控制中心磁贴」
 *
 *  控制中心里点得动的第三方磁贴必须**先在控制中心里加过**，而用户真正常绑的那几个（WLAN / 蓝牙 /
 *  移动数据 / NFC / 飞行模式 / 自动旋转 …）是 SystemUI 内置的简写 token、**没有组件名**，永远进不了
 *  那张列表。所以这一簇不再借控制中心的任何一种写法，自己定义动作类型：`setting:<id>`
 *  （见 [Action.Setting]）。
 *
 * ## 执行不用 root
 *
 *  宿主（`com.oplus.gesture`，签名权限）负责**读**当前状态并算出目标值；写状态分两条路：
 *  · `mobile_data` / `low_power` 直接写 `Settings.Global`（Phone 与 PowerManagerService 各自有
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

    /** 页面上的十条，顺序即显示顺序。 */
    val ITEMS: List<Item> =
        listOf(
            Item(settingToken("bt"), Strings.SYS_SETTING_BT, "蓝牙"),
            Item(settingToken("wifi"), Strings.SYS_SETTING_WIFI, "WLAN"),
            Item(settingToken("cell"), Strings.SYS_SETTING_CELL, "移动数据"),
            Item(settingToken("rotation"), Strings.SYS_SETTING_ROTATION, "自动旋转"),
            Item(settingToken("nfc"), Strings.SYS_SETTING_NFC, "NFC"),
            Item(settingToken("airplane"), Strings.SYS_SETTING_AIRPLANE, "飞行模式"),
            // 免打扰与手电筒都是**本来就有实现**的动作，不再写第二套：
            //  dnd 走 `Action.Oplus`（宿主侧的 ColorOS/AOSP 通道），flashlight 走 `Action.Flashlight`
            Item(Action.OPLUS_PREFIX + RomProfile.NAME_NO_DISTURB, Strings.SYS_SETTING_DND, "免打扰"),
            // 手电筒复用网格里那一格（`flashlight`）：执行侧本来就有真开关，不必绕 shell
            Item(Action.FLASHLIGHT, Strings.SYS_SETTING_FLASHLIGHT, "手电筒"),
            Item(settingToken("darkmode"), Strings.SYS_SETTING_DARKMODE, "暗色模式"),
            Item(settingToken("powersave"), Strings.SYS_SETTING_POWERSAVE, "省电模式"),
        )

    /** 十条的落盘取值 —— 判「网格里那一格算不算已绑定」用（见 `ActionCatalog.isSelected`）。 */
    val TOKENS: List<String> = ITEMS.map { it.token }

    /** 某个落盘 token 对应清单里的哪一条（不是这十种写法就返回 `null`）。 */
    fun find(token: String): Item? = ITEMS.firstOrNull { it.token == token }

    /** 某个 token 的显示名（当前语言）—— 步骤行 / 执行提示 / 顶栏小标题共用这一处。 */
    fun titleOf(token: String): String? = find(token)?.let { I18n.get(it.labelKey, it.label) }
}
