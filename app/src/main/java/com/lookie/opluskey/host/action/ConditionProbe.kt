package com.lookie.opluskey.host.action

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 条件执行里每一种条件「此刻成不成立」—— 全项目唯一的判据出口。
 *
 * ## 为什么在这里集中
 *
 *  条件会一种一种往上加，每种都要读一个系统开关。读法散在各处时，「同一个条件两处判得不一样」
 *  只能靠人眼查；收成一个 `when` 之后，加一种条件就是加一行。
 *
 * ## 只有派发在这里
 *
 *  具体怎么读拆在 `ConditionChecks.kt`（带取值的那几档：屏幕 / 充电 / Wi-Fi / 媒体 / 响铃 / 方向）
 *  与本文件下半（无取值的耳机 / 小窗）。加一种条件仍然只动两处：这里加一行、那边加一个函数。
 *
 * ## 权限
 *
 *  这些判据**都不需要额外权限**，而且权限在本项目里是按 **uid** 判的（见 `AndroidManifest.xml`
 *  顶部的说明：界面代码跑在宿主进程里，在清单里声明什么对宿主都没用）。所以这里只用
 *  「宿主 uid 本来就拿得到」的东西：`PowerManager.isInteractive`、sticky 电量广播、
 *  `AudioManager`、`Settings.Global` 读、`resources.configuration`。
 *
 * ## 判不出就返回 `false`
 *
 *  拿不到某个系统服务、读到异常值，一律当**这个条件不成立**（走兜底）。反过来（当成立）会执行
 *  用户完全没预期的那条命令，代价大得多。
 */
internal object ConditionProbe {
    fun matches(
        condition: BranchCondition,
        ctx: Context,
    ): Boolean {
        val kind = condition.kind ?: return false
        if (!condition.isUsable) return false
        return try {
            // 空取值退回这一档的默认值（老的 `wifi` / `media` 就是没有取值的，见 `defaultChoice`）
            judge(kind, condition.effectiveValue, ctx)
        } catch (e: Throwable) {
            XLog.w("分叉条件：判 ${kind.id} 时抛了异常，按不成立处理", e)
            false
        }
    }

    private fun judge(
        kind: ConditionKind,
        value: String,
        ctx: Context,
    ): Boolean =
        when (kind) {
            ConditionKind.APP -> ForegroundApp.packageName()?.let { it == value } == true
            ConditionKind.SCREEN -> screenMatches(ctx, value)
            ConditionKind.CHARGING -> chargingMatches(ctx, value)
            ConditionKind.HEADSET -> headsetMatches(ctx, value)
            ConditionKind.MEDIA -> mediaMatches(ctx, value)
            ConditionKind.WIFI -> wifiMatches(ctx, value)
            ConditionKind.MINI -> hasMiniWindow()
            ConditionKind.RINGER -> ringerMatches(ctx, value)
            ConditionKind.ORIENTATION -> orientationMatches(ctx, value)
        }

    /**
     * **耳机状态**：`connected`（接着）/ `disconnected`（没接）。
     *
     *  `disconnected` 直接取反 —— 设备要么在、要么不在，没有「第三种都不知道」的状态
     *  （与屏幕那两档不同：熄屏且无锁屏时两边都不成立）。
     */
    private fun headsetMatches(
        ctx: Context,
        value: String,
    ): Boolean {
        val connected = hasHeadset(ctx)
        return when (value) {
            "disconnected" -> !connected
            "connected" -> connected
            else -> false
        }
    }

    /**
     * 接着耳机 —— 有线 / USB / 蓝牙 A2DP 三种都算。
     *
     *  `isWiredHeadsetOn` 已废弃且在部分机型上恒 `false`，所以走 `getDevices(GET_DEVICES_OUTPUTS)`
     *  逐个看类型：蓝牙那档只算 A2DP（那是听媒体用的），不算 SCO（通话用）。
     */
    private fun hasHeadset(ctx: Context): Boolean {
        val manager = audio(ctx) ?: return false
        val types =
            manager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .orEmpty()
                .map { it.type }
        return types.any {
            it == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
    }

    /**
     * 屏幕上有没有小窗 —— 扫 `getAllTopAppInfos()`，看有没有 `windowingMode == 100` 的那一条。
     *
     *  与小窗那一族同一个来源（`MiniWindowFacts.WINDOWING_MODE_ZOOM`），不另开一条反射路径。
     */
    private fun hasMiniWindow(): Boolean =
        ForegroundApp.infos().any {
            Reflect.fieldValue(it, it.javaClass, MiniWindowFacts.TOP_APP_WINDOWING_MODE_FIELD) ==
                MiniWindowFacts.WINDOWING_MODE_ZOOM
        }
}
