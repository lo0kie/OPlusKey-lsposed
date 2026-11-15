package com.lookie.opluskey.bridge

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.SystemClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

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
 *  热点虽然也从这里分派，但实现是单独一段（那一支有自己的状态机：过渡态不切）。
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
 * ## 为什么单成一段
 *
 *  这一支有自己的状态机（过渡态不切），与上面那些「写一下 Settings 就完事」的开关不是一类。
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

/**
 * 「系统开关」那几条开关的**执行侧**（跑在 system_server 里，uid 1000）。
 *
 * ## 为什么不是 root shell
 *
 *  这些开关在控制中心里对应 SystemUI 自己的磁贴，而磁贴没有组件名、系统也没留「点一下」的入口。
 *  但它们各自背后的服务都有 binder 方法，而**模块本来就注入在 system_server 里**（uid 1000）——
 *  那个身份过得了这些方法上的权限检查，不必借 `su`：
 *
 *  | id | 服务 | 方法 |
 *  | --- | --- | --- |
 *  | `wifi` | `wifi` | `setWifiEnabled` |
 *  | `bt` | `bluetooth_manager` | `enable` / `disable` |
 *  | `cell` | `phone` | `setDataEnabled`（或 `setDataEnabledForReason`） |
 *  | `airplane` | `connectivity` | `setAirplaneMode` |
 *  | `dnd` | `notification` | `setZenMode` |
 *  | `darkmode` | `uimode` | `setNightMode` |
 *  | `rotation` | `window` | `isRotationFrozen` 读，`freezeDisplayRotation` / `thawDisplayRotation` 写 |
 *  | `powersave` | `power` | `setPowerMode` |
 *
 * ## 状态从哪来
 *
 *  除了自动旋转（它的「锁没锁」只有 `window` 自己知道），其余状态都由**宿主机**从 `Settings`
 *  读出「现在开着没有」，再把**目标值**带过来 —— 注入侧读不到 Settings（`system_server` 里
 *  借来的 Context 用 ContentResolver 必 NPE，见 `docs/device/lsposed.md` 六之五），
 *  会话两半各做一件自己做得来的事。
 *
 * ## 签名按名字 + 参数个数试
 *
 *  各 ROM 的签名不一致（`setWifiEnabled(boolean)` vs `(String,boolean)`），所以逐个试候选、
 *  命不中就记日志 —— 与 `SettingCalls` 里那套口径一致。
 */
internal object SettingBridge {
    /** 宿主进程调：把「切这个开关」转给 system_server（与状态栏指令同一条广播通道）。 */
    fun send(
        context: Context,
        id: String,
        on: Boolean?,
    ) {
        try {
            val intent =
                Intent(StatusBarBridge.ACTION_STATUSBAR)
                    .setPackage(com.lookie.opluskey.core.model.HostApp.PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(StatusBarBridge.EXTRA_CMD, StatusBarBridge.CMD_SETTING)
                    .putExtra(StatusBarBridge.EXTRA_SETTING_ID, id)
            if (on != null) intent.putExtra(StatusBarBridge.EXTRA_SETTING_ON, on)
            context.sendBroadcast(intent)
        } catch (e: Throwable) {
            XLog.w("发系统开关广播失败：$id", e)
        }
    }

    /** 注入侧（system_server）调：从广播里取 id 与目标值，交给 [SettingCalls]。 */
    fun handle(
        intent: Intent,
        loader: ClassLoader?,
    ) {
        val id =
            try {
                intent.getStringExtra(StatusBarBridge.EXTRA_SETTING_ID).orEmpty()
            } catch (e: Throwable) {
                XLog.w("读系统开关 id 失败：${e.message}")
                ""
            }
        if (id.isEmpty()) return
        // 没有 `setting_on` = 「由你自己读当前状态」（目前只有自动旋转走这条）
        val on =
            if (intent.hasExtra(StatusBarBridge.EXTRA_SETTING_ON)) {
                intent.getBooleanExtra(StatusBarBridge.EXTRA_SETTING_ON, false)
            } else {
                null
            }
        apply(loader, id, on)
    }

    /** 真正执行：切一下 [id]。会话身份在系统进程里跑，先清掉调用方身份再调。 */
    fun apply(
        loader: ClassLoader?,
        id: String,
        on: Boolean?,
    ) {
        if (loader == null) {
            XLog.w("系统开关 $id：还没有 system_server 的 ClassLoader，跳过")
            return
        }
        val token = Binder.clearCallingIdentity()
        try {
            val ok =
                try {
                    SettingCalls.run(loader, id, on)
                } catch (e: Throwable) {
                    XLog.w("系统开关 $id 执行失败：${e.javaClass.simpleName}: ${e.message}")
                    false
                }
            if (ok) {
                XLog.w("系统开关 $id → ${on?.let { if (it) "开" else "关" } ?: "取反"}")
            } else {
                XLog.w("系统开关 $id：没命中（这个 ROM 的方法名 / 签名与候选都对不上）")
            }
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }
}

/**
 * 在 system_server 里按名字取服务、按「名字 + 参数个数」调方法的两个小工具。
 *
 *  单列一个 object 的理由是**归属**：这两件事不属于「切开关」—— 别的注入侧调用（状态栏 / 小窗）
 *  要按名字调服务时也用它们。
 */
internal object ServiceCalls {
    /** system_server 自己的 uid —— 造 AttributionSource / 传包名时都用它。 */
    private const val SYSTEM_UID = 1000

    /** 造 `AttributionSource` 时的 attribution tag —— 系统身份调蓝牙那几个方法时**必须**给一个。 */
    private const val ATTRIBUTION_TAG = "opluskey"

    /**
     * 按服务名取**可反射的对象**（取不到返回 `null`）。
     *
     *  注入侧拿到的是两种东西：
     *   · 跑在 system_server 里的服务（wifi / connectivity / window / uimode / bluetooth_manager）——
     *     拿到的就是实现类本身（`Binder` 子类），反射直接能调；
     *   · 跑在**别的进程**里的服务（nfc 在 `com.android.nfc`、phone 在 `com.android.phone`）—— 拿到的
     *     是 `BinderProxy`，**它上面一个业务方法都没有**（只有 `transact`），反射必然「没命中」
     *     （真机实测：`isEnabled/0：BinderProxy 上没有这个方法`）。这种要过一遍 AIDL 的
     *     `Stub.asInterface(binder)` 才变成接口对象。
     *
     * @param name `ServiceManager` 里的名字（`service list` 那份）
     * @param iface AIDL 接口全名，用来找它的 `$Stub`（`android.nfc.INfcAdapter`）
     */
    fun service(
        loader: ClassLoader,
        name: String,
        iface: String,
    ): Any? {
        val binder =
            Reflect
                .findClass(loader, Framework.Calls.SERVICE_MANAGER)
                ?.let { Reflect.callStatic(it, Framework.Calls.GET_SERVICE, name) }
        if (binder == null) {
            XLog.w("取不到服务 $name")
            return null
        }
        if (!binder.javaClass.name.contains("BinderProxy")) return localService(loader, name, iface, binder)
        return proxyService(loader, name, iface, binder)
    }

    /**
     * 本进程里拿到的就是实现类本身（不是 `BinderProxy`）。
     *
     *  主判据：直接返回即可 —— 但个别 ROM 会再包一层（反射看不到业务方法），所以返回之前先试一次
     *  `asInterface`：命中的通常还是它自己，包了壳的会换成接口对象；两种都没坏处，失败就原样返回。
     */
    private fun localService(
        loader: ClassLoader,
        name: String,
        iface: String,
        binder: Any,
    ): Any {
        val localStub = Reflect.findClass(loader, "$iface\$Stub")
        val viaStub = localStub?.let { Reflect.callStatic(it, Framework.Calls.AS_INTERFACE, binder) }
        if (viaStub != null) {
            XLog.d("$name 是本地服务对象，asInterface 兜底命中")
            return viaStub
        }
        XLog.d("$name 是本地服务对象，asInterface 兜底未命中，原样返回")
        return binder
    }

    /** 别的进程里的服务（拿到的是 `BinderProxy`）：过一遍 `Stub.asInterface`，取不到就原样返回。 */
    private fun proxyService(
        loader: ClassLoader,
        name: String,
        iface: String,
        binder: Any,
    ): Any {
        val stub = Reflect.findClass(loader, "$iface\$Stub")
        if (stub == null) {
            XLog.w("$name 是 BinderProxy，但找不到 $iface\$Stub")
            return binder
        }
        return Reflect.callStatic(stub, Framework.Calls.AS_INTERFACE, binder) ?: binder
    }

    /**
     * 造一个 `AttributionSource`（uid 1000、包名 `android`、tag `opluskey`）。
     *
     *  蓝牙那几个方法在 Android 13+ 上都改成了 `enable(AttributionSource)` /
     *  `disable(AttributionSource, boolean)` —— 无参 / 只有一个 boolean 的几版已经删掉了。
     *  **tag 不能是 null**：系统身份调会被 `checkCallingAttributionSource` 直接拒
     *  （真机实测 `IllegalArgumentException: System generic caller must set the Attribution tag`）。
     */
    fun attributionSource(
        loader: ClassLoader,
        packageName: String = "android",
    ): Any? {
        val cls =
            Reflect.findClass(loader, "android.content.AttributionSource") ?: run {
                XLog.w("找不到 android.content.AttributionSource")
                return null
            }
        runCatching {
            return cls
                .getConstructor(Int::class.java, String::class.java, String::class.java)
                .newInstance(SYSTEM_UID, packageName, ATTRIBUTION_TAG)
        }.onFailure { XLog.w("AttributionSource 三参构造不可用：${it.javaClass.simpleName}") }
        return runCatching {
            val builderCls = Reflect.findClass(loader, "android.content.AttributionSource\$Builder")
            if (builderCls == null) {
                XLog.w("找不到 AttributionSource\$Builder")
                return null
            }
            val builder = builderCls.getConstructor(Int::class.java).newInstance(SYSTEM_UID)
            builderCls.getMethod("setPackageName", String::class.java).invoke(builder, packageName)
            runCatching {
                builderCls
                    .getMethod("setAttributionTag", String::class.java)
                    .invoke(builder, ATTRIBUTION_TAG)
            }
            builderCls.getMethod("build").invoke(builder)
        }.onFailure {
            XLog.w("AttributionSource.Builder 也不可用：${it.javaClass.simpleName}: ${it.message}")
        }.getOrNull()
    }

    /** 「调到了」的凭据 —— 方法本身返回 void 时返回值是 null，不能拿它当失败判据。 */
    class Called(
        val value: Any?,
    )

    /**
     * 调一个方法：按顺序试几组实参，第一组能命中的就调。
     *
     * @return 命中且调用成功 = [Called]（里带着返回值，void 方法里是 null）；没命中 / 抛异常 = null
     */
    fun call(
        target: Any,
        name: String,
        vararg argSets: List<Any?>,
    ): Called? {
        val sets = if (argSets.isEmpty()) arrayOf(emptyList<Any?>()) else argSets
        for (args in sets) {
            val m = Reflect.methodByNameAndArity(target.javaClass, name, args.size) ?: continue
            val failure =
                try {
                    m.isAccessible = true
                    return Called(m.invoke(target, *args.toTypedArray()))
                } catch (e: Throwable) {
                    e
                }
            // 真正的拒绝原因在 cause 里（`InvocationTargetException` 自己的 message 恒为 null）
            val cause = Reflect.deepestCause(failure)
            XLog.w("调 $name/${args.size} 被拒：${cause.javaClass.simpleName}: ${cause.message}")
            //  **目标方法自己抛的**（`InvocationTargetException`）= 真被拒（权限 / 状态不对），
            //  换一组实参也一样，直接收工。其余失败是「这组实参对不上这个方法」——
            //  继续试下一组：候选表存在的意义就是同一个方法在别的 ROM 上签名不同。
            if (failure is java.lang.reflect.InvocationTargetException) return null
        }
        // 一个候选都没命中：把接收者是谁打出来 —— 是「服务对象不对」还是「签名不对」一眼能分
        XLog.w("$name/${sets.firstOrNull()?.size ?: 0}：${target.javaClass.simpleName} 上没有这个方法")
        return null
    }
}

/**
 * 「缓存值 + 取它的时刻」—— 一个值和一个时刻，**必须一起换**。
 *
 *  这一对原先在四处各写一遍两个字段（`cachedHostUid` / `cachedHostUidAt`、
 *  `cachedUsers` / `cachedAt`、`intent` / `intentAt`、`lastText` / `lastAt`），
 *  每处各写一遍过期判据。两处独立的 `@Volatile` 字段还有个更细的问题：读它们要读两次，
 *  中间可能被另一条线程换掉 —— 读到「新的值 + 旧的时刻」就会把一份刚取到的缓存判成过期
 *  （或反过来）。收成一个不可变对象之后，一次读拿到的一定是同一份。
 *
 *  写法上仍然是「一个 `@Volatile var` 字段」：整体替换引用，读侧不需要锁。
 *
 *  @param value 缓存下来的那份东西
 *  @param at 取它的时刻 —— 与调用方判过期时用的 `now` 必须是**同一个时钟源**
 *    （`elapsedRealtime` 与 `uptimeMillis` 不能混着比）
 */
internal class TimedCache<T>(
    val value: T,
    val at: Long,
) {
    /**
     * [now] 这一刻还算不算新鲜（距 [at] 不到 [ttlMs]）。
     *
     *  边界取「不到」而不是「不超过」：四处里三处本来就是 `<`，差那 1ms 没有意义。
     */
    fun fresh(
        now: Long,
        ttlMs: Long,
    ): Boolean = now - at < ttlMs
}

/**
 * NFC 那一条开关 —— 与别的开关只差一样东西：它要记一份「用户想要的状态」。
 *
 * ## 为什么不能直接拿 `getState` 取反（真机量出来的）
 *
 *  2026-10-24 在 PLQ110 上连按侧键、同时每 200ms 轮询 `cmd nfc status`，八次按压的结论：
 *
 *   · **打开**：`enable` 返回后 **0.18 ~ 0.27 s** 就报 enabled；
 *   · **关闭**：`disable` 返回后要 **2.9 ~ 3.4 s** 才报 disabled（协议栈 teardown ＋ 安全元件那一段）；
 *   · 而且关闭那 3 秒里 `getState` **一直是 3（STATE_ON）**，不是 4（TURNING_OFF）。
 *
 *  「点了没反应」不是我们慢 —— 模块自己这条链路（宿主收到手势 → 读状态 → 下发）只花 **9 ms**。
 *  慢的是 NFC 自己，而且只有关的那一头慢。
 *
 * ## 所以只留一件事：[intent]
 *
 *  关闭那 3 秒里 `getState` 还停在 3。照「读状态取反」的写法，用户在这个窗口里再按一下，
 *  读到的还是「开着」⇒ 又发一次 `disable` —— 第二下白按，看起来就是「有时候没生效」。
 *
 *  现在记下**用户最后一次想要的状态**（[intent]，[INTENT_WINDOW_MS] 之内算数），窗口内再按
 *  就按它取反，不看那个还停在旧值的 `getState`。落定之后两种算法结果一致（状态本来就是
 *  对的），所以这个窗口只在「状态滞后」那 3 秒里起作用 —— 它要管的也正好只有那 3 秒。
 *
 * ## 没有「迁移中等落定再补发」那一套
 *
 *  上一版还有一支：状态是 TURNING_ON / TURNING_OFF 时把反向动作记成 pending、每 150ms 轮询
 *  `getState`、等落定后再补发。按上面那份实测它几乎是空转 —— 关闭那 3 秒（真正会被连按的
 *  窗口）里 `getState` 停在 3、根本不进那一支，能进去的只剩开启的 0.18~0.27 s。
 *  为一个最宽 200ms 的窗口挂一个 Handler ＋ 4 秒超时，不值：那 55 行换来的效果还是「3 秒后
 *  才生效」，不是「取消」。现在迁移中按一下就按 [desired] 算出的方向直接下发，服务真丢掉的
 *  话就是丢一次（日志里有 `下发…→false`），比吊一个定时器干净。
 *
 *  要再确认这一步是否还值得加回来，量一次就够（关一次 NFC，看序列里 4 出现几个采样点）：
 *
 *  ```
 *  adb shell "su -c 'for i in $(seq -w 1 25); do cmd nfc status; sleep 0.2; done'"
 *  ```
 */
internal object NfcToggle {
    /** `NfcAdapter.STATE_TURNING_ON` —— 正在开。 */
    private const val STATE_TURNING_ON = 2

    /** `NfcAdapter.STATE_ON`。 */
    private const val STATE_ON = 3

    /** `NfcAdapter.STATE_TURNING_OFF` —— 正在关（只用于日志；[desired] 按「不开」算）。 */
    private const val STATE_TURNING_OFF = 4

    /** 系统身份调 `enable` / `disable` 时传的包名（uid 1000 名下就是 `android`）。 */
    private const val PKG = "android"

    /**
     * [intent] 的有效期。
     *
     *  必须盖住关闭那 3.4 s —— 窗口内再按要按「用户意图」算、而不是按 `getState` 那个旧值。
     *  留一点余量取 4 s。
     *
     *  **这个值只管意图窗口**。上一版还兼着「等落定轮询超时」那一份语义（同一个常量两种尺子，
     *  改一处会误伤另一处）；那支轮询已经删了，现在它是单义的。
     */
    private const val INTENT_WINDOW_MS = 4000L

    /**
     * 用户**最后一次想要的状态**（`null` = 还没表达过）。
     *
     *  与「`getState` 说现在是什么」是两件事：迁移期间前者已经变了、后者还没。
     *  连按翻转要按前者算，否则就是把用户刚表达的反方向又发一遍。
     *
     *  只在**下发成功**时更新（见 [fire]）：被系统拒掉的那一下没生效，不能当成用户「已经要到了」
     *  记进来 —— 否则窗口内再按一下会按一个从未发生过的意图取反。
     *
     *  值与时刻收成一个对象（见 [TimedCache]）：只在 [INTENT_WINDOW_MS] 之内算数。
     */
    @Volatile
    private var intentCache: TimedCache<Boolean>? = null

    /**
     * 切一下 NFC。
     *
     * @param target `null` = 切换（按 [intent] / `getState` 取反）；`true` / `false` = 明确的开 / 关
     * @return 这一次**有没有真的下发出去了**（不是「状态已经变了」—— 变没变得等 NFC 自己落定）
     */
    fun run(
        loader: ClassLoader,
        target: Boolean?,
    ): Boolean {
        val s =
            ServiceCalls.service(loader, "nfc", "android.nfc.INfcAdapter") ?: run {
                XLog.w("nfc：取不到 nfc 服务")
                return false
            }
        val want: Boolean
        if (target != null) {
            //  明确开 / 关时有目标就够了：`getState` 在关闭那 3 秒里停在旧值（见类注释），
            //  读了也判不出方向；它读失败时还会把一次本可以下发的指令整条挡掉。
            want = target
            XLog.w("nfc：明确档目标=${label(want)}（不读 getState）")
        } else {
            val state = state(s) ?: return false
            want = desired(state)
            XLog.w("nfc：getState=$state（${stateLabel(state)}）目标=${label(want)}（切换）")
        }
        return fire(s, want)
    }

    /** 目标状态：窗口内听用户上一次的表达，否则按 `getState` 取反。 */
    private fun desired(state: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val recent = intentCache?.takeIf { it.fresh(now, INTENT_WINDOW_MS) }?.value
        if (recent != null) return !recent
        return !(state == STATE_ON || state == STATE_TURNING_ON)
    }

    /** 真发那一下 —— 下发之后按结果记录「用户想要的状态」。 */
    private fun fire(
        s: Any,
        on: Boolean,
    ): Boolean {
        //  同蓝牙那一支：返回 boolean，false = 被拒，不能按「非 null 就是成功」判
        //
        //  `disable` 的第一参 **必须传 true**（persist）。传 false 只是「这次关掉」：
        //  `com.android.nfc` 的 `nfc_on` 不动，于是**重启之后 NFC 自己开回来** —— 用户口径
        //  「侧键绑定切换 NFC，重启会自动打开 NFC」。
        //  这一条与 `SettingCalls.bluetooth` 是同一个坑（那里注释记着同样的现象），
        //  而 NFC 这一处原先传的是 false，等于把「关」这件事只做了一半。
        val ok =
            if (on) {
                ServiceCalls.call(s, "enable", listOf(PKG))?.value == true
            } else {
                ServiceCalls.call(s, "disable", listOf(true, PKG))?.value == true
            }
        //  记在**下发之后**、且只记成功的：被拒的那一下没生效，窗口内再按应当按真实状态重算
        //  （还是同一个方向），而不是按一个没发生过的意图取反。
        if (ok) intentCache = TimedCache(on, SystemClock.uptimeMillis())
        XLog.w("nfc：下发${label(on)} → $ok")
        return ok
    }

    /** 读 `getState`；读不到返回 `null`。 */
    private fun state(s: Any): Int? {
        val v = ServiceCalls.call(s, "getState")?.value as? Int
        if (v == null) XLog.w("nfc：读不到 getState（服务对象是 ${s.javaClass.simpleName}）")
        return v
    }

    private fun label(on: Boolean): String = if (on) "开" else "关"

    /** 状态值的中文名 —— 只给日志看（`getState=3` 单看数字认不出是哪一档）。 */
    private fun stateLabel(state: Int): String =
        when (state) {
            STATE_TURNING_ON -> "正在开"
            STATE_ON -> "开"
            STATE_TURNING_OFF -> "正在关"
            else -> "关"
        }
}
