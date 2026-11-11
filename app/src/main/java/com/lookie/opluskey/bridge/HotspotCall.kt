package com.lookie.opluskey.bridge

import com.lookie.opluskey.core.log.XLog

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
