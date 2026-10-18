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

    /** `NfcAdapter.STATE_ON` —— `getState()` 的 3 与 4（TURNING_OFF）都算「开着」。 */
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
            // 状态由服务自己报 —— `on` 传 null，在这里读一次再取反
            "rotation" -> {
                rotation(loader)
            }

            "nfc" -> {
                nfc(loader)
            }

            else -> {
                XLog.w("系统开关：$id 没有接执行")
                false
            }
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
        val r = ServiceCalls.call(s, "setWifiEnabled", listOf(PKG, target))
        // 这个方法不抛异常、只返回 false 表示「被拒」—— 不把它打出来就只剩一句「没反应」
        if (r?.value == false) XLog.w("wifi：setWifiEnabled 返回 false（被系统拒了，不是异常）")
        return r != null
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
        return if (target) {
            ServiceCalls.call(s, "enable", listOf(source)) != null
        } else {
            ServiceCalls.call(s, "disable", listOf(source, true)) != null
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
     *  状态值是 `NfcAdapter.STATE_*`：OFF=1 / TURNING_ON=2 / ON=3 / TURNING_OFF=4 ——
     *  3 与 4 都算「已经是开着」（4 只是正在关，再点一次应当开回来）。
     *  `persist` 传 false：只关这一次；传 true 会写进「关机也保持」的持久档。
     */
    private fun nfc(loader: ClassLoader): Boolean {
        val s =
            ServiceCalls.service(loader, "nfc", "android.nfc.INfcAdapter") ?: run {
                XLog.w("nfc：取不到 nfc 服务")
                return false
            }
        val state =
            ServiceCalls.call(s, "getState")?.value as? Int ?: run {
                XLog.w("nfc：读不到 getState（服务对象是 ${s.javaClass.simpleName}）")
                return false
            }
        // STATE_ON = 3、STATE_TURNING_OFF = 4：这两个都算「已经开着」
        val on = state >= NFC_STATE_ON
        XLog.w("nfc：getState=$state（${if (on) "开" else "关"}）")
        return if (on) {
            ServiceCalls.call(s, "disable", listOf(false, PKG)) != null
        } else {
            ServiceCalls.call(s, "enable", listOf(PKG)) != null
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
    private fun rotation(loader: ClassLoader): Boolean {
        val s = ServiceCalls.service(loader, "window", "android.view.IWindowManager") ?: return false
        val frozen = ServiceCalls.call(s, "isRotationFrozen")?.value as? Boolean ?: return false
        if (frozen) {
            return ServiceCalls.call(s, "thawDisplayRotation", listOf(0, REASON)) != null
        }
        // 冻在「当前方向」上：这版 IWindowManager 没有 `getRotation()`，改版之后就叫这个名
        val rotation = ServiceCalls.call(s, "getDefaultDisplayRotation")?.value as? Int ?: 0
        return ServiceCalls.call(s, "freezeDisplayRotation", listOf(rotation, 0, REASON)) != null
    }
}
