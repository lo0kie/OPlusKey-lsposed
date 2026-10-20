package com.lookie.opluskey.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.ui.model.STEP_MS
import com.lookie.opluskey.ui.model.durationText

/**
 * 一根滑杆的**全部规格**：显示什么（标题 / 摘要 / 行首图标）+ 量什么（区间、当前值、出厂值、步长）。
 *
 *  合成一个类型是为了让 [SettingsPage.SliderRow] 的参数表停在一只手数得过来的量级 ——
 *  这些本来就是一件事（「这一行是一根什么样的滑杆」），一起给比散在五个形参里更好读。
 *
 * @param step 相邻两档的步长 —— 轨道上有几档由它算（默认是毫秒那几档的步长）
 * @param valueText 行尾那个「当前值」怎么写（毫秒那几档默认走 [durationText]）；
 *   `null` = **不画行尾**（档位那种一眼看得出当前值的滑杆，再写一遍是重复）
 * @param keyPoints 要在轨道上**画出来的**刻度点；`null` = 只画出厂默认值那一个圆点。
 *   库那边 `showKeyPoints` 单独打开不画东西 —— 它读的是 `keyPoints`，两者要一起给
 *   （见 COUI Slider 文档：`showKeyPoints` 仅当 `keyPoints` 不为 null 时有效）。
 */
internal data class SliderSpec(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val min: Int,
    val max: Int,
    val value: Int,
    val default: Int,
    val step: Int = STEP_MS,
    val enabled: Boolean = true,
    val valueText: ((Int) -> String)? = { durationText(it) },
    val keyPoints: List<Float>? = null,
)
