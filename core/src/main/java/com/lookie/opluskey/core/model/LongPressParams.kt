package com.lookie.opluskey.core.model

/**
 * 长按那一档的参数 —— **单键与组合键共用这一份视图**。
 *
 * ## 为什么要有
 *
 *  `KeyConfig` 与 `ComboKey` 的长按参数**一字不差**（同一组字段、同一个语义、判定侧走同一套
 * `LongPressSpec`）。界面那一侧原先各写一张卡（`LongRepeatCard` / `ComboLongCard`），
 * 两处行序、文案、可见性判据迟早对不上 —— 用户要的是「按键和组合键的功能完全一样」。
 *
 *  收成接口之后，长按卡只认 [LongPressParams]，传 `KeyConfig` 还是 `ComboKey` 都一样；
 *  两边各自的 `copy` 留在各自的类型里（[withLongPress] 那两个扩展），卡里**没有分支**。
 */
interface LongPressParams {
    val longPressMs: Int
    val longRepeat: Boolean
    val longRepeatIntervalMs: Int
    val longRepeatVibrate: Boolean
    val longFireOnRelease: Boolean
    val longReleaseOn: Boolean
    val longRelease: Action

    /**
     * 松手那一下**真的有事可做** —— 开关开着、挑过动作、且不是「松手才触发」那条。
     * 两位缺一位都不算（数据层的互斥结论，界面不重算）。
     */
    val releaseBound: Boolean
}

/**
 * 长按卡上的一次改动 —— 只带**改了的那几位**，没带的保持原样。
 *
 *  卡片每次只改一格（拨一个开关、拖一根滑杆），用一个「变更包」交回去，
 *  各类型用一个 `with` 扩展把它落到自己的 `copy` 上 —— 这是唯一需要知道具体类型的地方，
 *  而且只在 core 里、每家五行。
 */
data class LongPressEdit(
    val pressMs: Int? = null,
    val repeat: Boolean? = null,
    val repeatIntervalMs: Int? = null,
    val repeatVibrate: Boolean? = null,
    val fireOnRelease: Boolean? = null,
    val releaseOn: Boolean? = null,
    val release: Action? = null,
)

/** 把一次改动落到这颗键上。 */
fun KeyConfig.withLongPress(edit: LongPressEdit): KeyConfig =
    copy(
        longPressMs = edit.pressMs ?: longPressMs,
        longRepeat = edit.repeat ?: longRepeat,
        longRepeatIntervalMs = edit.repeatIntervalMs ?: longRepeatIntervalMs,
        longRepeatVibrate = edit.repeatVibrate ?: longRepeatVibrate,
        longFireOnRelease = edit.fireOnRelease ?: longFireOnRelease,
        longReleaseOn = edit.releaseOn ?: longReleaseOn,
        longRelease = edit.release ?: longRelease,
    )

/** 把一次改动落到这一组组合键上 —— 与 [KeyConfig.withLongPress] 同一个口径。 */
fun ComboKey.withLongPress(edit: LongPressEdit): ComboKey =
    copy(
        longPressMs = edit.pressMs ?: longPressMs,
        longRepeat = edit.repeat ?: longRepeat,
        longRepeatIntervalMs = edit.repeatIntervalMs ?: longRepeatIntervalMs,
        longRepeatVibrate = edit.repeatVibrate ?: longRepeatVibrate,
        longFireOnRelease = edit.fireOnRelease ?: longFireOnRelease,
        longReleaseOn = edit.releaseOn ?: longReleaseOn,
        longRelease = edit.release ?: longRelease,
    )
