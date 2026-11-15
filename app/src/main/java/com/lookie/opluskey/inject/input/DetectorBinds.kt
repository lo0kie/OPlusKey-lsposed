package com.lookie.opluskey.inject.input

import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.ComboWindows
import com.lookie.opluskey.core.model.KeyId

/**
 * [ComboDetector] 那几样**装配期才接得上**的回调 —— 打包成一份，构造时一次性传入。
 *
 * ## 为什么是单独一个 data class，而不是三个构造参数
 *
 *  这三样（首键补派、按住档固定窗口起点、四格窗口）都不是「这个判定器必须知道什么」——
 *  它们是**可选**功能，早先因为 [ComboDetector] 的构造参数已贴着 detekt 的
 *  `LongParameterList` 上限，只能写成类体里的 `@Volatile var` + `apply { }` 再设。
 *  那样一来「必须设」被降成了可空，判据侧到处都是 `?.invoke() ?: 默认`，装配也有两个入口。
 *
 *  收进这一份之后：装配方在构造 [ComboDetector] 时**一次**给全，类型是非空的，
 *  `ComboDetector` 内部不再有「接没接上」这种不存在的状态。
 *
 * ## 为什么不收进构造参数清单本身
 *
 *  那 11 个参数回答的是「判定器必须知道什么」（去哪问绑定、扫多宽、跨度多大……），
 *  是它每一条判据的直接输入；这一份是「可选功能怎么接」，是另一层。分开之后加新判据
 *  仍只动参数清单，加新可选功能仍只动这一份，两边不会互相挤。
 */
internal data class DetectorBinds(
    /**
     * 「首键松手后仍执行它自己的长按」—— 判据与派发两半。
     *
     *  `enabled` 判「这一档开了吗」（装配方走 `Config.comboStarterKeepLongFor`），
     *  `fire` 补派首键自己的长按（转给宿主，与普通长按同一条 `forward`）。
     */
    val starterKeepLong: DetectorBinds.StarterKeepLong? = null,
    /**
     * 按住档成员的「固定窗口起点」—— 判「从首个成员起固定窗口」还是「逐次重置」。
     *
     *  这一位与等待组合那颗开关是**两个变量**（两张卡各管一种按法），
     *  装配方接 `Config.comboHoldFixedWindowStartFor`。
     */
    val holdFixedWindowStartFor: (ComboKey) -> Boolean = { false },
    /**
     * 这一档的**四格窗口**（等待 / 按住 × 固定窗口起点 开 / 关）——
     *  按住档的成员接收窗口按它取。装配方接 `ConfigChannel.windowsFor`。
     */
    val windowsFor: (ComboKey) -> ComboWindows? = { null },
) {
    /**
     * 「首键松手后仍执行它自己的长按」那一档的两个回调。
     *
     *  判据在装配方（`Config.comboStarterKeepLongFor`：独立设置关着就看公共那份），
     *  派发也在装配方（转给宿主，走与普通长按同一条 `forward`）。
     */
    class StarterKeepLong(
        /** 这一档开了吗。 */
        val enabled: (ComboKey) -> Boolean,
        /** 补派首键自己的长按。 */
        val fire: (KeyId) -> Unit,
    )
}
