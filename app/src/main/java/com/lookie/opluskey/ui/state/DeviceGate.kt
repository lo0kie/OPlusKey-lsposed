package com.lookie.opluskey.ui.state

import com.lookie.opluskey.core.model.DeviceSupport

// 「适配闸」那一档：本机「机型 + 系统版本」在不在适配清单里。
//
// 与链路自检（`LinkCheck.kt`）**不是一域**：那一边答的是「这一趟跑起来断在哪一段」，
// 这一边答的是「本机压根适不适配」—— 清单外的组合只注入界面层，链路自检永远收不到回包，
// 页面本来就整页禁用。两者都被首页那条常驻提示读，但判据、生命周期、读的东西全不一样。

/**
 * 本机「机型 + 系统版本」在不在**适配清单**里（三档，见 `core/model/DeviceSupport`）。
 *
 *  清单外的组合：模块**只注入界面层**（注入侧与宿主侧的 Hook 一个都不挂，那是 `KeyModule` 的事），
 * 于是链路自检永远收不到回包 ⇒ 页面本来就整页禁用。这里单独判一次是为了把**原因说清**：
 * 首页那条常驻提示会报「未知设备，此设备不可用」或「当前系统版本等待适配」，而不是让用户干等
 * 一句「正在等待系统响应」（那句会让人以为等一会儿就好）。
 *
 *  进程内算一次就够：机型与系统版本都不会中途变。**只读 `Build`**，判定本体在 core 里
 *（那边不碰 Android API）。
 */
internal val deviceSupport: DeviceSupport.Support =
    DeviceSupport.check(
        DeviceSupport.currentModel,
        android.os.Build.VERSION.RELEASE,
        // ROM 版本取法与判定同源（见 DeviceSupport.currentRomSeries）
        DeviceSupport.currentRomSeries,
    )

/**
 * 清单命中的简写（判定本体是 [deviceSupport]）—— 决定整页能不能改。
 *
 * 与 [devicePending] 一样是从上面那一档里投出来的：**不另存一份判据**，免得两边慢慢跑偏。
 */
internal val deviceSupported: Boolean = deviceSupport == DeviceSupport.Support.ADAPTED

/** 机型在清单里、系统版本不是那一档 —— 提示说「等待适配」，与「未知设备」分开（见 [PENDING_DEVICE_HINT]）。 */
internal val devicePending: Boolean = deviceSupport == DeviceSupport.Support.PENDING

/**
 * 本机代号（`android.os.Build.MODEL`，真机是 `PLK110`）—— 「适配清单」里「本机」那一行的
 * 主标题要摆它出来（见 `AboutScreen` 的 `localDeviceTitle`）。
 *
 * 与 [deviceSupport] 同一个来路（进程内不变的 `Build` 值）：那边只投出判定档位，拿不到原串，
 * 所以这里单独存一份。
 */
internal val deviceModel: String =
    android.os.Build.MODEL
        ?.trim()
        .orEmpty()
