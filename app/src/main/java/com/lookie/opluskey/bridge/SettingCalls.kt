package com.lookie.opluskey.bridge

import com.lookie.opluskey.core.log.XLog

/**
 * 「系统开关」那几条开关的**具体调用** —— 一条 id 一支，全在 system_server 里跑。
 *
 * ## 签名都是 dump 出来的，不是猜的
 *
 *  用 `PROBE --es op dump`（`docs/runtime/debug-channel.md`）把对应接口的方法连参数类型打出来之后照抄：
 *  ‹wifi› `setWifiEnabled(String, boolean)`、‹bt› `enable(AttributionSource)` /
 *  `disable(AttributionSource, boolean)`、‹airplane› `setAirplaneMode(boolean)`、
 *  ‹nfc› `enable(String)` / `disable(boolean, String)`、
 *  ‹rotation› `isRotationFrozen()` + `freezeDisplayRotation(int, int, String)` /
 *  `thawDisplayRotation(int, String)`。
 *
 *  四种不在这里：
 *   · 免打扰 / 手电筒 —— 本来就有实现（`Action.Oplus` 走宿主侧 AppLauncher、`Action.Flashlight`
 *     走 `FlashlightController`），再写一套只会两处慢慢走偏；
 *   · 移动数据 / 省电模式 —— 系统自己盯着 `Settings.Global.mobile_data` / `low_power` 这两个键
 *     （Phone 的 `DataEnabledSettings`、`PowerManagerService` 各有一个 ContentObserver），
 *     而宿主有 `WRITE_SECURE_SETTINGS`，写一下就够了，不必再借 system_server。
 *
 *  热点虽然也从这里分派，但实现单独立在 [HotspotCall]（与 [NfcToggle] 同一条理由：这个 object
 *  的函数数贴着 detekt 的上限，热点那一支又有自己的状态机）。
 */
internal object SettingCalls {
    /** 调系统方法时写进 `reason` 的那一串（便于在系统日志里认出是谁改的）。 */
    private const val REASON = "opluskey"

    /** 系统身份调这些方法时传的包名（uid 1000 名下就是 `android`）。 */
    private const val PKG = "android"

    fun run(
        loader: ClassLoader,
        id: String,
        on: Boolean?,
    ): Boolean =
        when (id) {
            "wifi" -> {
                wifi(loader, on)
            }

            "bt" -> {
                bluetooth(loader, on)
            }

            "airplane" -> {
                airplane(loader, on)
            }

            "darkmode" -> {
                darkMode(loader, on)
            }

            // 这两条没有可读的 Settings 键（旋转锁不在 Settings 里、`nfc_on` 读不到），
            // 状态由服务自己报 —— `on` 为 null 时在这里读一次再取反；明确开 / 关时直接用。
            "rotation" -> {
                rotation(loader, on)
            }

            "nfc" -> {
                nfc(loader, on)
            }

            "dnd" -> {
                dnd(loader, on)
            }

            // 极暗：**只能**从 system_server 调 —— 颜色显示服务要
            // `CONTROL_DISPLAY_COLOR_TRANSFORMS`（signature|privileged），宿主那份是拿不到的
            // （宿主侧调过一次：`SecurityException: Access denied, requires: ...CONTROL_DISPLAY_COLOR_TRANSFORMS`）。
            "extradim" -> {
                extraDim(loader, on)
            }

            // 热点：没有 Settings 键可读可写，整条由 `wifi` 服务管（见 `HotspotCall`）。
            "hotspot" -> {
                HotspotCall.run(loader, on)
            }

            else -> {
                XLog.w("系统开关：$id 没有接执行")
                false
            }
        }

    /**
     * 免打扰：`notification` 服务的 `getZenMode()` 读、`setZenMode(int, Uri, String)` 写。
     *
     *  `ZEN_MODE_OFF = 0`，其余三档（重要打扰 / 完全不打扰 / 只闹钟）都算「开着」——
     *  与宿主侧 `SettingSwitch.readOn("dnd")` 读 `Settings.Global.zen_mode` 同一把尺子。
     *  这**不能**靠写 Settings 生效：zen 那一个键由 NotificationManagerService 自己维护，
     *  直接改只是改了一个没人理的数。
     *
     *  `conditionId` 传 null：内置那几档没有条件 id，系统接受 null。
     */
    private fun dnd(
        loader: ClassLoader,
        target: Boolean?,
    ): Boolean {
        val s =
            ServiceCalls.service(loader, "notification", "android.app.INotificationManager") ?: run {
                XLog.w("dnd：取不到 notification 服务")
                return false
            }
        val on =
            target ?: run {
                val mode =
                    ServiceCalls.call(s, "getZenMode")?.value as? Int ?: run {
                        XLog.w("dnd：读不到 getZenMode")
                        return false
                    }
                XLog.w("dnd：getZenMode=$mode")
                //  **取反**：`target` 为空 = 切换，目标是「与现在相反」——
                //  把当前值当目标的话，这一档就永远是「设置成它现在的样子」（NFC 那条踩过同一个坑）。
                mode == 0
            }
        XLog.w("dnd：目标=${if (on) "开" else "关"}")
        val mode = if (on) 1 else 0
        // 三参是这一族的通用签名；个别版本多一个 `boolean`（persist），一起试
        return ServiceCalls.call(
            s,
            "setZenMode",
            listOf(mode, null, REASON),
            listOf(mode, null, REASON, false),
        ) != null
    }

    /** WIFI：`setWifiEnabled(String packageName, boolean enable)`。 */
    private fun wifi(
        loader: ClassLoader,
        on: Boolean?,
    ): Boolean {
        val s =
            ServiceCalls.service(loader, "wifi", "android.net.wifi.IWifiManager") ?: run {
                XLog.w("wifi：取不到 wifi 服务")
                return false
            }
        val target =
            on ?: run {
                //  **别静默返回**：目标值缺了时这一档什么都不做，而界面上那个开关照样播了动画 ——
                //  日志里没有这一行就只能猜「是不是压根没执行」。
                XLog.w("这条系统开关指令没有目标值（on=null），不执行")
                return false
            }
        //  两代签名都要试（五、3 的「退到下一层候选」）：新 ROM 是
        //  `setWifiEnabled(String packageName, boolean enable)`，AOSP 老版只有 `setWifiEnabled(boolean)`。
        //  只试新签名的话，老 ROM 上这一档**静默失败**（`call` 解析不到方法就返回 null）。
        //  先新后老：老签名在 uid 1000 下也能过（没有包名参数就不查调用方包）。
        val r =
            ServiceCalls.call(s, "setWifiEnabled", listOf(PKG, target))
                ?: ServiceCalls.call(s, "setWifiEnabled", listOf(target))
        // 这个方法不抛异常、只返回 false 表示「被拒」—— 不把它打出来就只剩一句「没反应」
        if (r?.value == false) XLog.w("wifi：setWifiEnabled 返回 false（被系统拒了，不是异常）")
        // 成败看**返回值**：`r != null` 只说明「调用进去了」，被系统拒时同样是 true
        //  ⇒ 上游会把它当成「切成功了」，而状态一点没变。
        return r?.value == true
    }

    /**
     * 蓝牙：`enable(AttributionSource)` / `disable(AttributionSource, boolean persist)`。
     *
     *  `persist` **必须传 true**（真机踩过）：传 false 只是「临时关」——`Settings.Global.bluetooth_on`
     *  仍然留在 1，于是 BluetoothManagerService 会在下一次状态校正里把它恢复成开
     *  （现象就是「关了一下又自己打开」）。传 true 等于「用户就是要关」，与设置页 / 控制中心
     *  那颗磁贴同一条语义，也顺手把那个键写对，宿主下一次读到的状态才不会反过来。
     */
    private fun bluetooth(
        loader: ClassLoader,
        on: Boolean?,
    ): Boolean {
        val s = ServiceCalls.service(loader, "bluetooth_manager", "android.bluetooth.IBluetoothManager") ?: return false
        val target =
            on ?: run {
                //  **别静默返回**：目标值缺了时这一档什么都不做，而界面上那个开关照样播了动画 ——
                //  日志里没有这一行就只能猜「是不是压根没执行」。
                XLog.w("这条系统开关指令没有目标值（on=null），不执行")
                return false
            }
        val source = ServiceCalls.attributionSource(loader) ?: return false
        //  `?.value == true` 而不是 `!= null`：这两个方法返回 boolean，**被系统拒绝时返回的是
        //  false** 而不是 null —— 只判 null 会把「拒了」报成「切了」，界面状态与实际相反。
        return if (target) {
            ServiceCalls.call(s, "enable", listOf(source))?.value == true
        } else {
            ServiceCalls.call(s, "disable", listOf(source, true))?.value == true
        }
    }

    /** 飞行模式：`setAirplaneMode(boolean)`。 */
    private fun airplane(
        loader: ClassLoader,
        on: Boolean?,
    ): Boolean {
        val s = ServiceCalls.service(loader, "connectivity", "android.net.IConnectivityManager") ?: return false
        val target =
            on ?: run {
                //  **别静默返回**：目标值缺了时这一档什么都不做，而界面上那个开关照样播了动画 ——
                //  日志里没有这一行就只能猜「是不是压根没执行」。
                XLog.w("这条系统开关指令没有目标值（on=null），不执行")
                return false
            }
        return ServiceCalls.call(s, "setAirplaneMode", listOf(target)) != null
    }

    /** 暗色模式：`setNightMode(int)`（`MODE_NIGHT_NO = 1`、`MODE_NIGHT_YES = 2`）。 */
    private fun darkMode(
        loader: ClassLoader,
        on: Boolean?,
    ): Boolean {
        val s = ServiceCalls.service(loader, "uimode", "android.app.IUiModeManager") ?: return false
        val target =
            on ?: run {
                //  **别静默返回**：目标值缺了时这一档什么都不做，而界面上那个开关照样播了动画 ——
                //  日志里没有这一行就只能猜「是不是压根没执行」。
                XLog.w("这条系统开关指令没有目标值（on=null），不执行")
                return false
            }
        return ServiceCalls.call(s, "setNightMode", listOf(if (target) 2 else 1)) != null
    }

    /**
     * NFC：`getState()` 读，`enable(String pkg)` / `disable(boolean persist, String pkg)` 写。
     *
     *  这个 ROM 的 `INfcAdapter` **没有 `isEnabled()`**（真机 dump 出来只有 `getState()`），
     *  状态值是 `NfcAdapter.STATE_*`：OFF=1 / TURNING_ON=2 / ON=3 / TURNING_OFF=4。
     *  「开」只认 2 与 3；4（正在关）算「关」—— 再点一次应当**开回来**。
     *
     *  `persist` **必须传 true**，与蓝牙那条同一条教训：传 false 只关这一次，重启会开回来。
     *  读状态与取反都在 [NfcToggle]：`getState` 在关闭那 3 秒里会停在 3，直接取反会把连按的
     *  第二下吃掉（那里记着实测数据与那一条的来龙去脉）。
     */
    private fun nfc(
        loader: ClassLoader,
        target: Boolean?,
    ): Boolean = NfcToggle.run(loader, target)

    /**
     * 极暗：`color_display` 服务的 `isReduceBrightColorsActivated()` 读、
     * `setReduceBrightColorsActivated(boolean)` 写。
     *
     *  这就是「设置 → 显示与亮度 → 极暗」那一颗开关的后台（`dumpsys color_display` 里的
     *  `Reduce bright colors: Activated: ...`）。它要 `CONTROL_DISPLAY_COLOR_TRANSFORMS`，
     *  宿主（普通系统应用）调会被 `SecurityException` 挡掉，所以走这一侧（uid 1000 自带该权限）。
     *
     *  `target` 为空 = 切换：**在服务这边读一次再取反**（与 rotation / nfc 同一条），
     *  宿主那边不读 —— `Settings.*.reduce_brightness` 那两个键在这台机器上没人维护，读了是假的。
     */
    private fun extraDim(
        loader: ClassLoader,
        target: Boolean?,
    ): Boolean {
        val s =
            ServiceCalls.service(loader, "color_display", "android.hardware.display.IColorDisplayManager") ?: run {
                XLog.w("extradim：取不到 color_display 服务")
                return false
            }
        val on =
            target ?: run {
                val now =
                    ServiceCalls.call(s, "isReduceBrightColorsActivated")?.value as? Boolean ?: run {
                        XLog.w("extradim：读不到 isReduceBrightColorsActivated")
                        return false
                    }
                XLog.w("extradim：现在=${if (now) "开" else "关"}，取反")
                !now
            }
        return ServiceCalls.call(s, "setReduceBrightColorsActivated", listOf(on)) != null
    }

    /**
     * 自动旋转：读「现在锁没锁」→ 取反。
     *
     *  `window` 服务上没有「把自动旋转打开」这种直白的方法，只有冻结 / 解冻：
     *  `freezeDisplayRotation(rotation, displayId, reason)` 与 `thawDisplayRotation(displayId, reason)`。
     *  状态也就只能从它自己那里读（`isRotationFrozen()`）—— `Settings.System` 里那个键写不了
     *  （`system_server` 用不了 ContentResolver），两边各说各的反而更糟。
     */
    private fun rotation(
        loader: ClassLoader,
        target: Boolean?,
    ): Boolean {
        val s = ServiceCalls.service(loader, "window", "android.view.IWindowManager") ?: return false
        // 「自动旋转开着」= 没冻结。明确开 / 关时不用读当前状态。
        //  切换那一档：目标 unfreeze = 「现在**冻结着**」—— 两个否定叠起来正好是取反
        //  （`frozen` 本身是「自动旋转关着」），别把它当成「当前值当目标值」那个坑。
        val unfreeze =
            target ?: run {
                val frozen = ServiceCalls.call(s, "isRotationFrozen")?.value as? Boolean ?: return false
                XLog.w("rotation：isRotationFrozen=$frozen")
                frozen
            }
        if (unfreeze) {
            return ServiceCalls.call(s, "thawDisplayRotation", listOf(0, REASON)) != null
        }
        return freezeAtCurrent(s)
    }

    /**
     * 冻在「当前方向」上：这版 `IWindowManager` 没有 `getRotation()`，改版之后就叫它
     * `getDefaultDisplayRotation`。
     *
     *  **读不到就不冻**：以前拿 `?: 0` 顶上，横屏下会把方向冻成 0（竖屏）——
     *  用户看到的是「屏幕被硬掰回竖屏」，而且是静默的。宁可报失败，也不伪造一个方向。
     */
    private fun freezeAtCurrent(s: Any): Boolean {
        val rotation =
            ServiceCalls.call(s, "getDefaultDisplayRotation")?.value as? Int ?: run {
                XLog.w("rotation：读不到 getDefaultDisplayRotation（本机没有这个方法）—— 冻结方向无从判断，这次不动")
                return false
            }
        // 签名是 `freezeDisplayRotation(int displayId, int rotation, String caller)` ——
        //  第一参是**屏号**、第二参才是方向。反过来传在横屏下等于去冻一个不存在的 displayId，
        //  系统直接抛异常，表现是「横屏时自动旋转锁不住」（竖屏恰好蒙对，所以更容易漏）。
        return ServiceCalls.call(s, "freezeDisplayRotation", listOf(0, rotation, REASON)) != null
    }
}

/**
 * 个人热点的开 / 关 —— 全在 system_server（uid 1000）里做，宿主那头只把 id 转过来。
 *
 * ## 为什么不在宿主
 *
 *  热点没有 Settings 键：状态只有 `wifi` 服务知道，开关也要 `NETWORK_SETTINGS` 和
 *  `TETHER_PRIVILEGED`（真机 `dumpsys package android` 里两条都是 `granted=true`）——
 *  宿主是 `platform_app`，这两条都没有。
 *
 * ## 为什么单开一个文件
 *
 *  与 [NfcToggle] 同一条理由：`SettingCalls` 那个 object 的函数数贴着 detekt 的上限，
 *  这一支又要自己的状态机（过渡态不切），塞回去只会把它顶过线。
 *
 * ## 三个方法都是 dump 出来的
 *
 *  `getWifiApEnabledState()` 读、`startTetheredHotspot(SoftApConfiguration, String)` 开、
 *  `stopSoftAp()` 关（`PROBE --es op dump --cls android.net.wifi.IWifiManager`）。
 *  「开」传的是 `getSoftApConfiguration()` 拿回来的那一份配置 —— 开的就是用户自己那套热点名 /
 *  密码；`cmd wifi start-softap <ssid> <pass>` 那条会把现给的写回配置，所以不用它。
 */
internal object HotspotCall {
    /** 系统身份调这些方法时传的包名（uid 1000 名下就是 `android`）。 */
    private const val PKG = "android"

    /**
     * `WifiManager.WIFI_AP_STATE_*` 的那几个值 —— **不引用宿主常量**：读 `WifiManager` 的静态字段
     * 会触发它的 `<clinit>`，而注入侧碰宿主类初始化是红线（AGENTS.md 五之二、1）。
     * 这几个数是 AOSP 公开常量，真机 `getWifiApEnabledState()` 的返回值按它读。
     */
    private const val AP_STATE_DISABLING = 10

    private const val AP_STATE_DISABLED = 11

    private const val AP_STATE_ENABLING = 12

    private const val AP_STATE_ENABLED = 13

    /**
     * @param target `null` = 切换（先读一次状态再取反）、`true` / `false` = 明确的打开 / 关闭
     * @return 真的调下去了没有 —— 读不到状态、判定在过渡态、服务拒绝都算 `false`
     */
    fun run(
        loader: ClassLoader,
        target: Boolean?,
    ): Boolean {
        val service =
            ServiceCalls.service(loader, "wifi", "android.net.wifi.IWifiManager") ?: run {
                XLog.w("hotspot：取不到 wifi 服务")
                return false
            }
        val on = target ?: nextTarget(service) ?: return false
        XLog.w("hotspot：目标=${if (on) "开" else "关"}")
        return if (on) start(service) else ServiceCalls.call(service, "stopSoftAp")?.value == true
    }

    /**
     * 「这一次该切成什么」—— 读当前状态再取反；读不到 / 处在过渡态时给 `null`（这次不切）。
     *
     *  过渡态那一档与 NFC 同一条尺子：正在关（10）时再点一下应当是**开回来**，所以它算「关着」；
     *  正在开（12）算「开着」。读到 14（失败）就当读不到 —— 猜一个值会把开着的热点关掉。
     */
    private fun nextTarget(service: Any): Boolean? {
        val state =
            ServiceCalls.call(service, "getWifiApEnabledState")?.value as? Int ?: run {
                XLog.w("hotspot：读不到 getWifiApEnabledState")
                return null
            }
        XLog.w("hotspot：getWifiApEnabledState=$state")
        return when (state) {
            AP_STATE_ENABLED, AP_STATE_ENABLING -> {
                false
            }

            AP_STATE_DISABLED, AP_STATE_DISABLING -> {
                true
            }

            else -> {
                XLog.w("hotspot：热点处在过渡态（$state），这次不切")
                null
            }
        }
    }

    /** 用**现存那一份** SoftApConfiguration 开热点 —— 取不到配置就不开（别让系统拿空配置顶）。 */
    private fun start(service: Any): Boolean {
        val config =
            ServiceCalls.call(service, "getSoftApConfiguration")?.value ?: run {
                XLog.w("hotspot：读不到 getSoftApConfiguration，不开")
                return false
            }
        return ServiceCalls.call(service, "startTetheredHotspot", listOf(config, PKG))?.value == true
    }
}
