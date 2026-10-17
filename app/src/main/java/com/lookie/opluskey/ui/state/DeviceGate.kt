package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.model.DeviceSupport

// 「适配清单」那一档：本机「机型 + 系统版本」在不在清单里（只用于提示，不拦任何东西）。
//
// 与链路自检（`LinkCheck.kt`）**不是一域**：那一边答的是「这一趟跑起来断在哪一段」，
// 这一边答的是「这一组验过没有」—— 结论只进「更多选项」里那一行，不参与任何闸。

/**
 * 调试通道塞进来的机型（`--es model CPH9999`，见 `SettingsPageDebug`）—— `null` = 用真机值。
 *
 *  给「不换机也能看另外两档长什么样」用。清单只影响「更多选项」里那一行的措辞、不拦任何东西，
 * 所以这个覆盖项也只动显示，不碰注入也不碰任何闸。
 *  只有 debug 包设得了：那条广播的接收器只在 `src/debug/` 源集里存在（release 是空壳）。
 */
internal var fakeModel by mutableStateOf<String?>(null)

/**
 * 本机代号（真机是 `PLK110`）—— 「适配清单」里「本机」那一行的主标题要摆它出来
 *（见 `AboutMachineCard` 的 `localDeviceTitle`）。
 *
 *  与 [deviceSupport] 同一个来路（`DeviceSupport.currentModel`），被调试覆盖时跟着变：
 *  两者必须同源，否则会出现「标题写着 PLK110、结论却按别的机型算」。
 */
internal val deviceModel: String
    get() = fakeModel ?: DeviceSupport.currentModel

/**
 * 本机「机型 + 系统版本」在不在**适配清单**里（三档，见 `core/model/DeviceSupport`）。
 *
 *  清单外的组合照样注入、照样能用（`KeyModule` 不拿它做任何判断）—— 这里算一次只是为了
 * 「更多选项」里那一行能说清「这一组验过没有」。
 *
 *  现算而不是缓存：机型可以被调试通道覆盖（见 [fakeModel]），而 `check` 只是扫一遍清单、比几个
 *  字符串，重组里读它够便宜。系统版本两个输入仍是进程内的定值。
 */
internal val deviceSupport: DeviceSupport.Support
    get() =
        DeviceSupport.check(
            deviceModel,
            DeviceSupport.currentAndroid,
            // ROM 版本取法与判定同源（见 DeviceSupport.currentRomSeries）
            DeviceSupport.currentRomSeries,
        )
