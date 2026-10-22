package com.lookie.opluskey.bridge

import com.lookie.opluskey.core.log.XLog

/**
 * 「系统开关」那几条开关的**具体调用** —— 一条 id 一支，全在 system_server 里跑。
 *
 * ## 签名都是 dump 出来的，不是猜的
 *
 *  用 `PROBE --es op dump`（`docs/debug-channel.md`）把对应接口的方法连参数类型打出来之后照抄：
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
 */
internal object SettingCalls {
    /** 调系统方法时写进 `reason` 的那一串（便于在系统日志里认出是谁改的）。 */
    private const val REASON = "opluskey"

    /** 系统身份调这些方法时传的包名（uid 1000 名下就是 `android`）。 */
    private const val PKG = "android"

    /** `NfcAdapter.STATE_TURNING_ON` —— 正在开，同样按「开着」处理（再点一下该关）。 */
    private const val NFC_STATE_TURNING_ON = 2

    /** `NfcAdapter.STATE_ON`。 */
    private const val NFC_STATE_ON = 3

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

    /** WLAN：`setWifiEnabled(String packageName, boolean enable)`。 */
    private fun wifi(
        loader: ClassLoader,
        on: Boolean?,
    ): Boolean {
        val s =
            ServiceCalls.service(loader, "wifi", "android.net.wifi.IWifiManager") ?: run {
                XLog.w("wifi：取不到 wifi 服务")
                return false
            }
        val target = on ?: return false
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
        val target = on ?: return false
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
        val target = on ?: return false
        return ServiceCalls.call(s, "setAirplaneMode", listOf(target)) != null
    }

    /** 暗色模式：`setNightMode(int)`（`MODE_NIGHT_NO = 1`、`MODE_NIGHT_YES = 2`）。 */
    private fun darkMode(
        loader: ClassLoader,
        on: Boolean?,
    ): Boolean {
        val s = ServiceCalls.service(loader, "uimode", "android.app.IUiModeManager") ?: return false
        val target = on ?: return false
        return ServiceCalls.call(s, "setNightMode", listOf(if (target) 2 else 1)) != null
    }

    /**
     * NFC：`getState()` 读，`enable(String pkg)` / `disable(boolean persist, String pkg)` 写。
     *
     *  这个 ROM 的 `INfcAdapter` **没有 `isEnabled()`**（真机 dump 出来只有 `getState()`），
     *  状态值是 `NfcAdapter.STATE_*`：OFF=1 / TURNING_ON=2 / ON=3 / TURNING_OFF=4。
     *  「开」只认 2 与 3；4（正在关）算「关」—— 再点一次应当**开回来**。
     *  `persist` 传 false：只关这一次；传 true 会写进「关机也保持」的持久档。
     */
    private fun nfc(
        loader: ClassLoader,
        target: Boolean?,
    ): Boolean {
        val s =
            ServiceCalls.service(loader, "nfc", "android.nfc.INfcAdapter") ?: run {
                XLog.w("nfc：取不到 nfc 服务")
                return false
            }
        val on =
            target ?: run {
                val state =
                    ServiceCalls.call(s, "getState")?.value as? Int ?: run {
                        XLog.w("nfc：读不到 getState（服务对象是 ${s.javaClass.simpleName}）")
                        return false
                    }
                //  只认 2 / 3 为「开着」。写成 `state >= 3` 会把 4（TURNING_OFF）也当成开 ⇒ 再点一次
                //  又发一遍 disable，用户看到的是「点了没反应」，而此刻正是最该把它开回来的时候。
                XLog.w("nfc：getState=$state")
                //  **这里要取反**：`target` 为空 = 切换，目标是「与现在相反」。
                //  早先漏了这一下 —— 把「当前开着」直接当成「目标开着」，于是切换永远变成
                //  「设置成它现在的样子」，表现就是「NFC 那一档按了没反应」（真机日志：
                //  `getState=3` 紧跟着 `目标=开`）。
                !(state == NFC_STATE_ON || state == NFC_STATE_TURNING_ON)
            }
        XLog.w("nfc：目标=${if (on) "开" else "关"}")
        //  同蓝牙那一支：返回 boolean，false = 被拒，不能按「非 null 就是成功」判。
        return if (on) {
            ServiceCalls.call(s, "enable", listOf(PKG))?.value == true
        } else {
            ServiceCalls.call(s, "disable", listOf(false, PKG))?.value == true
        }
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
        // 冻在「当前方向」上：这版 IWindowManager 没有 `getRotation()`，改版之后就叫这个名
        val rotation = ServiceCalls.call(s, "getDefaultDisplayRotation")?.value as? Int ?: 0
        // 签名是 `freezeDisplayRotation(int displayId, int rotation, String caller)` ——
        //  第一参是**屏号**、第二参才是方向。反过来传在横屏下等于去冻一个不存在的 displayId，
        //  系统直接抛异常，表现是「横屏时自动旋转锁不住」（竖屏恰好蒙对，所以更容易漏）。
        return ServiceCalls.call(s, "freezeDisplayRotation", listOf(0, rotation, REASON)) != null
    }
}
