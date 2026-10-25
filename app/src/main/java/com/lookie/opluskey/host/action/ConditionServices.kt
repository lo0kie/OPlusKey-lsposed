package com.lookie.opluskey.host.action

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager

// 条件判据要用的那几个系统服务与一次电量广播 —— 从 `ConditionChecks` 拆出来：
// 那边是「每种条件怎么判」，这边是「判之前从系统问什么」，两件事各自涨。
//
//  都是**顶层 internal**：同一个 `when` / 同一个判据函数里直接调，不需要带接收者。

/**
 * 充电中（含充满）；**读不到电量广播返回 `null`（判不出）**。
 *
 *  用 **sticky 广播**而不是 `BatteryManager` 那几个 `isCharging`（那是 API 33 才有的静态属性，
 *  而且与 `status` 语义略有出入）：`registerReceiver(null, …)` 拿到的就是最近一次电量广播，
 *  不需要注册、也不需要权限。
 *
 *  返回 `null` 而不是 `false`：这一档的「不在充电」是**取反**得来的，把「没读到」当成 `false`
 *  会让 `idle` 凭空成立（调用方见 `chargingMatches`）。
 */
internal fun isCharging(ctx: Context): Boolean? {
    val intent: Intent =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

/** 三个系统服务各取一次 —— `getSystemService` 本身不会返回错类型，直接转。 */
internal fun power(ctx: Context): PowerManager? = ctx.getSystemService(PowerManager::class.java)

internal fun keyguard(ctx: Context): KeyguardManager? = ctx.getSystemService(KeyguardManager::class.java)

internal fun audio(ctx: Context): AudioManager? = ctx.getSystemService(AudioManager::class.java)
