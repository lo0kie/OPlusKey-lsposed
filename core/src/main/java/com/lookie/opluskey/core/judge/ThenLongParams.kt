package com.lookie.opluskey.core.judge

/**
 * 一档「后长按」的全部判定参数 —— 判定侧要的那几样，不带动作本身。
 *
 * ## 为什么它在 `core.judge`，而不在 `core.model`
 *
 *  它的**消费方**是判定层（`inject/input` 的 `ChainSpec` / `GestureDetector` / `ComboDetector`），
 *  而那一层有一条硬约束：不许 import `core.model`（判据见 `tools/audit-coupling.py` 的
 *  `inject/input` 那一条）。这个值对象全是「毫秒 / 布尔」，本身就不是业务模型 —— 放在业务包里，
 *  只是让判定层为了五个字段去认识一整包配置类型。
 *
 *  反过来不成立：业务侧（`core/model/AppTarget.kt` 的 `ClickChainParams.thenLongAt`）要**产出**
 *  它，而 `:core` 不能引 `inject/`（那是上层）。所以唯一能同时被两边认的地方在 `:core` 里，
 *  且必须是判定侧那一档 —— `core.judge` 现在没有任何跨包 import，加这一个值对象不会引回环。
 *
 *  与 `LongPressParams` 同族：长按那一档一份，三档「后长按」各一份（见 `ClickChainParams.thenLongAt`）。
 *  动作是什么由 `KeyConfig.actionOf` 回答，这里只回答「按多久算数、要不要排连发、松手还有没有事」。
 *
 * @param pressMs 这一档的按住时长（第二段按多久算数）
 * @param fireOnRelease 松手才触发（到点不执行）
 * @param repeatIntervalMs 连续触发的间隔
 * @param repeats 排不排连发（已并入「松手才触发」与串行命令那两条互斥）
 * @param releaseActionExists 松手那一刻还有事可做（决定要不要发 `ClickThenLongRelease`）
 */
data class ThenLongParams(
    val pressMs: Int,
    val fireOnRelease: Boolean,
    val repeatIntervalMs: Int,
    val repeats: Boolean,
    val releaseActionExists: Boolean,
)
