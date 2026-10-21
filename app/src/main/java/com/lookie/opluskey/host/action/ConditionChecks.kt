package com.lookie.opluskey.host.action

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings

// 「条件执行」里那些**带取值的判据** —— 一组条件一条，`ConditionProbe` 只负责派发。
//
// 从 `ConditionProbe` 拆出来：那边是「哪一种条件怎么判」的总入口，随着条件种类往上加，
// 一个 object 里塞进了十四个函数（detekt 的 `TooManyFunctions` 顶到 11 就报）。
// 拆法按「有没有取值」切：这里全是「要看 value 才成立」的档（屏幕 / 充电 / Wi-Fi / 媒体 /
// 响铃 / 方向），`ConditionProbe` 留下派发与那几个无取值的。
//
//  这些函数都是**顶层 internal**：同一个 `when` 里派发时不需要带接收者，
//  与 `ConditionProbe` 里原来那几个私有函数一一对应（逻辑一字未改）。

/**
 * **屏幕状态**：`on`（亮屏）/ `locked`（锁屏）。
 *
 *  两者**不是互补**：熄屏且无锁屏时两个都不成立。判据分开读（`isInteractive` / `isKeyguardLocked`），
 *  不拿其中一个取反 —— 那会把「熄屏」错认成「锁屏」。
 */
internal fun screenMatches(
    ctx: Context,
    value: String,
): Boolean =
    when (value) {
        "locked" -> keyguard(ctx)?.isKeyguardLocked == true
        "on" -> power(ctx)?.isInteractive == true
        else -> false
    }

/**
 * **充电状态**：`charging`（充电中，含充满）/ `idle`（没在充电）。
 *
 *  `idle` 就是「不在充电」，取反即可 —— 与屏幕那两档不同：这两个是真正的正反两面
 *  （电量广播要么在充、要么不在），不存在「第三种都不知道」的状态。
 */
internal fun chargingMatches(
    ctx: Context,
    value: String,
): Boolean {
    val charging = isCharging(ctx)
    return when (value) {
        "idle" -> !charging
        "charging" -> charging
        else -> false
    }
}

/** Wi-Fi **开关状态**：`on` / `off`（`WIFI_ON` 1 = 开）。取不到或认不出的取值 ⇒ 不成立。 */
internal fun wifiMatches(
    ctx: Context,
    value: String,
): Boolean {
    val on = Settings.Global.getInt(ctx.contentResolver, Settings.Global.WIFI_ON, 0) == 1
    return when (value) {
        "off" -> !on
        "on" -> on
        else -> false
    }
}

/**
 * 媒体**播放状态**：`playing` / `paused`。
 *
 *  `isMusicActive` 为 `true` = 眼下有音频在放。反过来只能说明「没有音频在放」——
 *  可能是暂停、也可能是根本没起过播放器。对「暂停中」这一档来说那就是用户的意思
 *  （现在没声音），所以直接取反；要做到「真有会话且暂停」得读 `MediaSessionManager`，
 *  那需要通知读取权限，宿主 uid 拿不到。
 */
internal fun mediaMatches(
    ctx: Context,
    value: String,
): Boolean {
    val playing = audio(ctx)?.isMusicActive == true
    return when (value) {
        "paused" -> !playing
        "playing" -> playing
        else -> false
    }
}

/** 响铃档位：`normal` / `vibrate` / `silent`。取不到或认不出的取值 ⇒ 不成立。 */
internal fun ringerMatches(
    ctx: Context,
    value: String,
): Boolean {
    val mode = audio(ctx)?.ringerMode ?: return false
    val now =
        when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> "normal"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            AudioManager.RINGER_MODE_SILENT -> "silent"
            else -> return false
        }
    return now == value
}

/** 屏幕方向：`portrait` / `landscape`。取不到或认不出的取值 ⇒ 不成立。 */
internal fun orientationMatches(
    ctx: Context,
    value: String,
): Boolean {
    val now =
        when (ctx.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "landscape"
            Configuration.ORIENTATION_PORTRAIT -> "portrait"
            else -> return false
        }
    return now == value
}

/**
 * 充电中（含充满）。
 *
 *  用 **sticky 广播**而不是 `BatteryManager` 那几个 `isCharging`（那是 API 33 才有的静态属性，
 *  而且与 `status` 语义略有出入）：`registerReceiver(null, …)` 拿到的就是最近一次电量广播，
 *  不需要注册、也不需要权限。
 */
private fun isCharging(ctx: Context): Boolean {
    val intent: Intent =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

/** 三个系统服务各取一次 —— `getSystemService` 本身不会返回错类型，直接转。 */
internal fun power(ctx: Context): PowerManager? = ctx.getSystemService(PowerManager::class.java)

internal fun keyguard(ctx: Context): KeyguardManager? = ctx.getSystemService(KeyguardManager::class.java)

internal fun audio(ctx: Context): AudioManager? = ctx.getSystemService(AudioManager::class.java)
