package com.lookie.opluskey.bridge

import android.content.Context
import android.content.Intent
import android.os.Binder
import com.lookie.opluskey.core.log.XLog

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
 *  借来的 Context 用 ContentResolver 必 NPE，见 `docs/device-findings.md` 六之五），
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
