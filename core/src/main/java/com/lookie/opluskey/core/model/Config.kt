package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.judge.ThenLongParams
import com.lookie.opluskey.core.param.GlobalParams
import com.lookie.opluskey.core.param.KeyParams
import com.lookie.opluskey.core.param.Param
import com.lookie.opluskey.core.param.Params
import com.lookie.opluskey.core.rom.RomProfile

// 配置的数据结构：三种手势、单键配置、全局配置、整份配置。

/** 三种手势。 */
enum class Gesture(
    val id: String,
    val label: String,
) {
    SINGLE("single", "单击"),
    DOUBLE("double", "双击"),
    LONG("long", "长按"),
    ;

    companion object {
        /**
         * 按 [id] 找手势 —— 配置与广播里存的一直是 id（`single` / `double` / `long`，见
         * `ConfigFields` 的 `<键>_<档>`），不认枚举名。认不出返回 `null`，由调用方丢掉那一条。
         */
        fun ofId(id: String?): Gesture? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 一个键的完整配置。
 *
 * ## 存储只有三样
 *
 *  · [actions]：档位 → 动作（`GestureTrigger` 本身就是键，所以**加一档手势不动这个类**）；
 *  · [params]：设置项（`Param.id` → 值，见 `KeyParams`），**加一项设置只动注册表**；
 *  · [scenes] / [enabled] / [useOwnSettings]：结构。
 *
 *  原先这里是六十多个构造参数 —— 加一项设置要同步改运行时字段、DTO、Codec、广播、独立设置
 *  枚举五处，漏一处就是「界面配得出来、存下去没了」。
 *
 * ## 所有旧字段名都保留为只读属性
 *
 *  所以读的地方一行不用改；写的地方走 [copy]（参数全可空，没传的保持原值）或 [with]。
 *  [copy] 的签名与 data class 生成的那一版逐参数相同，调用点因此零改动。
 */
class KeyConfig(
    val id: KeyId,
    val enabled: Boolean = true,
    val actions: Map<GestureTrigger, Action> = emptyMap(),
    val params: Params = Params.EMPTY,
    val scenes: List<SceneRule> = emptyList(),
    override val useOwnSettings: Boolean = false,
) : OwnSettings,
    ClickChainParams {
    /** 一档动作；没配的档位一律「无操作」。 */
    fun act(trigger: GestureTrigger): Action = actions[trigger] ?: Action.None

    // ── 动作（读侧全部从这里取，写侧走 `withAction`）──
    val single: Action get() = act(GestureTrigger.Click(1))
    val double: Action get() = act(GestureTrigger.Click(2))
    val long: Action get() = act(GestureTrigger.LongPress)
    override val longRelease: Action get() = act(GestureTrigger.Release)
    val clickThenLong: Action get() = act(GestureTrigger.ClickThenLong(1))
    override val clickThenLongRelease: Action get() = act(GestureTrigger.ClickThenLongRelease(1))
    val doubleThenLong: Action get() = act(GestureTrigger.ClickThenLong(2))
    val doubleThenLongRelease: Action get() = act(GestureTrigger.ClickThenLongRelease(2))
    val tripleThenLong: Action get() = act(GestureTrigger.ClickThenLong(3))
    val tripleThenLongRelease: Action get() = act(GestureTrigger.ClickThenLongRelease(3))

    /**
     * 第 3 击起的**额外档位**：连击次数 → 动作（`click3` / `click4` …）。
     *
     *  从 [actions] 里筛出来，没有单独存储；键是击数（≥ 3），与 `GestureTrigger.Click.count`
     *  同一个编号。空表 ⇒ 行为与没有这一项完全相同。
     */
    val extraClicks: Map<Int, Action>
        get() =
            buildMap {
                actions.forEach { (trigger, action) ->
                    val count = (trigger as? GestureTrigger.Click)?.count ?: return@forEach
                    if (count >= 3) put(count, action)
                }
            }

    // ── 设置项（全部走注册表；同名属性只是读法）──
    override val longPressMs: Int get() = params[KeyParams.LONG_PRESS_MS]
    override val longRepeat: Boolean get() = params[KeyParams.LONG_REPEAT]
    override val longRepeatIntervalMs: Int get() = params[KeyParams.LONG_REPEAT_INTERVAL_MS]
    override val longRepeatVibrate: Boolean get() = params[KeyParams.LONG_REPEAT_VIBRATE]
    override val longReleaseOn: Boolean get() = params[KeyParams.LONG_RELEASE_ON]
    override val longFireOnRelease: Boolean get() = params[KeyParams.LONG_FIRE_ON_RELEASE]
    val doubleClickMs: Int get() = params[KeyParams.DOUBLE_CLICK_MS]
    val comboOnly: Boolean get() = params[KeyParams.COMBO_ONLY]
    override val waitMultiWindow: Boolean get() = params[KeyParams.WAIT_MULTI_WINDOW]
    val multiWaitMs: Int get() = params[KeyParams.MULTI_WAIT_MS]
    override val colorOsApi: Boolean get() = params[KeyParams.COLOR_OS_API]
    override val vibrate: Boolean get() = params[KeyParams.VIBRATE]
    override val vibrateStrength: Int get() = params[KeyParams.VIBRATE_STRENGTH]
    override val vibrateOnNone: Boolean get() = params[KeyParams.VIBRATE_ON_NONE]
    override val toastOnRun: Boolean get() = params[KeyParams.TOAST_ON_RUN]
    override val toastFluid: Boolean get() = params[KeyParams.TOAST_FLUID]
    override val toastNotify: Boolean get() = params[KeyParams.TOAST_NOTIFY]
    override val toastTemplate: String get() = params[KeyParams.TOAST_TEMPLATE]
    override val stepNotice: Boolean get() = params[KeyParams.STEP_NOTICE]

    /** 长按绑的是不是「多条命令」—— 只由宿主算好、随广播带过来（见 `ConfigFields`）。 */
    val longIsSerial: Boolean get() = params[KeyParams.LONG_IS_SERIAL]

    /** 某个场景块配了松手动作 —— 只由宿主算好、随广播带过来；注入侧读不到场景表。 */
    val sceneRelease: Boolean get() = params[KeyParams.SCENE_RELEASE]

    /** 某个场景块给长按配了动作 —— 与 [sceneRelease] 同一套。 */
    val sceneLong: Boolean get() = params[KeyParams.SCENE_LONG]

    /** 某个场景块给「单击后长按」这一档本身配了动作 —— 与 [sceneLong] 同一套。 */
    val sceneChain: Boolean get() = params[KeyParams.SCENE_CHAIN]

    /** 某个场景块配了「单击后长按」的松手动作 —— 与 [sceneRelease] 同一套。 */
    override val sceneChainRelease: Boolean get() = params[KeyParams.SCENE_CHAIN_RELEASE]

    /**
     * **双击这一档开着没有** —— 默认 `true`（基础档，装上就在）。
     *
     *  关掉时：绑定页不排双击那一页、判定侧不数第二击（第二下按下就是新的一轮）、执行侧对它
     *  恒回「无操作」。表（[double]）本身不动，关掉再打开原样回来。
     *  **不跟随** [useOwnSettings]：这颗键开着哪几档手势是它自己的形态。
     */
    override val doubleClickOn: Boolean get() = params[KeyParams.DOUBLE_CLICK_ON]

    /** **哪几档多击开着**（第 3 击起，元素即击数）—— 逐档一个开关，关掉不动表。 */
    val extraClicksOn: Set<Int> get() = params[KeyParams.EXTRA_CLICKS_ON]

    /** **「单击后长按」这一档启用没有** —— 与 [extraClicksOn] 同一族，但它是另一位（不是击数集合里的一员）。 */
    override val clickThenLongOn: Boolean get() = params[KeyParams.CLICK_THEN_LONG_ON]

    /** 「单击后长按」那一档自己的**长按延迟** —— 与 [longPressMs] 分开。 */
    override val clickThenLongPressMs: Int get() = params[KeyParams.CLICK_THEN_LONG_PRESS_MS]

    /** 「单击后长按」按住期间**连续触发**。 */
    val clickThenLongRepeat: Boolean get() = params[KeyParams.CLICK_THEN_LONG_REPEAT]
    override val clickThenLongRepeatIntervalMs: Int get() = params[KeyParams.CLICK_THEN_LONG_REPEAT_INTERVAL_MS]

    /** 连续触发时每次重复都振动。 */
    val clickThenLongRepeatVibrate: Boolean get() = params[KeyParams.CLICK_THEN_LONG_REPEAT_VIBRATE]

    /** 「单击后长按」绑的是不是**多条命令** —— 只由宿主算好、随广播带过来。 */
    val clickThenLongIsSerial: Boolean get() = params[KeyParams.CLICK_THEN_LONG_IS_SERIAL]
    override val clickThenLongReleaseOn: Boolean get() = params[KeyParams.CLICK_THEN_LONG_RELEASE_ON]
    override val clickThenLongFireOnRelease: Boolean get() = params[KeyParams.CLICK_THEN_LONG_FIRE_ON_RELEASE]

    /** **「双击后长按」（第 3 击按住）/「三击后长按」（第 4 击按住）** 那两档的开关。 */
    override val doubleThenLongOn: Boolean get() = params[KeyParams.DOUBLE_THEN_LONG_ON]
    override val tripleThenLongOn: Boolean get() = params[KeyParams.TRIPLE_THEN_LONG_ON]
    val doubleThenLongRepeat: Boolean get() = params[KeyParams.DOUBLE_THEN_LONG_REPEAT]
    val doubleThenLongRepeatIntervalMs: Int get() = params[KeyParams.DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS]
    val doubleThenLongRepeatVibrate: Boolean get() = params[KeyParams.DOUBLE_THEN_LONG_REPEAT_VIBRATE]
    val doubleThenLongReleaseOn: Boolean get() = params[KeyParams.DOUBLE_THEN_LONG_RELEASE_ON]
    val doubleThenLongFireOnRelease: Boolean get() = params[KeyParams.DOUBLE_THEN_LONG_FIRE_ON_RELEASE]
    val doubleThenLongIsSerial: Boolean get() = params[KeyParams.DOUBLE_THEN_LONG_IS_SERIAL]
    val tripleThenLongRepeat: Boolean get() = params[KeyParams.TRIPLE_THEN_LONG_REPEAT]
    val tripleThenLongRepeatIntervalMs: Int get() = params[KeyParams.TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS]
    val tripleThenLongRepeatVibrate: Boolean get() = params[KeyParams.TRIPLE_THEN_LONG_REPEAT_VIBRATE]
    val tripleThenLongReleaseOn: Boolean get() = params[KeyParams.TRIPLE_THEN_LONG_RELEASE_ON]
    val tripleThenLongFireOnRelease: Boolean get() = params[KeyParams.TRIPLE_THEN_LONG_FIRE_ON_RELEASE]
    val tripleThenLongIsSerial: Boolean get() = params[KeyParams.TRIPLE_THEN_LONG_IS_SERIAL]

    /**
     * 宿主算好的**最高绑定击数**（含场景里绑的那几档）—— 只由宿主算好、随配置广播带过来。
     *
     *  不含「单击后长按 ⇒ 数到第 2 击」那个下限（那一抬在 [clicksForJudge] 里）。
     *  `0` = 这一位没给 ⇒ 判定侧退回 [boundClicks] 的兜底。
     */
    val judgeClicks: Int get() = params[KeyParams.JUDGE_CLICKS]

    /** 写入口：改一项设置。 */
    fun <T> with(
        spec: Param<T>,
        value: T,
    ): KeyConfig = KeyConfig(id, enabled, actions, params.with(spec, value), scenes, useOwnSettings)

    /**
     * 写入口：整体换动作表。
     *
     *  只给「导入 / 复位 / 迁移」那一类需要一次改多档的地方用；界面改单档走 [withAction]。
     */
    fun withActions(next: Map<GestureTrigger, Action>): KeyConfig = KeyConfig(id, enabled, next, params, scenes, useOwnSettings)

    /** 写入口：整体换设置项（`Config.effectiveFor` 的兜底复制、迁移）。 */
    fun withParams(next: Params): KeyConfig = KeyConfig(id, enabled, actions, next, scenes, useOwnSettings)

    /**
     * 与 data class 那一版 `copy` **逐参数同名同义**：没传的字段保持原值。
     *
     *  参数用可空表示「没传」—— 这些字段本身都不是可空类型，所以不会与「显式设成 null」混淆。
     *  保留它是为了让上百处 `it.copy(xxx = v)` 的调用点零改动（见类注释）。
     *
     *  长度与复杂度**就是参数个数的线性叠加**（每个参数一句 `if (x != null)`），不是纠缠的分支：
     *  拆成「按 map 收集」只会给上百个调用点背后多一层壳，读起来更绕。
     */
    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    fun copy(
        id: KeyId? = null,
        enabled: Boolean? = null,
        useOwnSettings: Boolean? = null,
        scenes: List<SceneRule>? = null,
        actions: Map<GestureTrigger, Action>? = null,
        params: Params? = null,
        single: Action? = null,
        double: Action? = null,
        long: Action? = null,
        longRelease: Action? = null,
        clickThenLong: Action? = null,
        clickThenLongRelease: Action? = null,
        doubleThenLong: Action? = null,
        doubleThenLongRelease: Action? = null,
        tripleThenLong: Action? = null,
        tripleThenLongRelease: Action? = null,
        extraClicks: Map<Int, Action>? = null,
        longPressMs: Int? = null,
        longRepeat: Boolean? = null,
        longRepeatIntervalMs: Int? = null,
        longRepeatVibrate: Boolean? = null,
        longReleaseOn: Boolean? = null,
        longFireOnRelease: Boolean? = null,
        doubleClickMs: Int? = null,
        comboOnly: Boolean? = null,
        waitMultiWindow: Boolean? = null,
        multiWaitMs: Int? = null,
        colorOsApi: Boolean? = null,
        vibrate: Boolean? = null,
        vibrateStrength: Int? = null,
        vibrateOnNone: Boolean? = null,
        toastOnRun: Boolean? = null,
        toastFluid: Boolean? = null,
        toastNotify: Boolean? = null,
        toastTemplate: String? = null,
        stepNotice: Boolean? = null,
        longIsSerial: Boolean? = null,
        sceneRelease: Boolean? = null,
        sceneLong: Boolean? = null,
        sceneChain: Boolean? = null,
        sceneChainRelease: Boolean? = null,
        doubleClickOn: Boolean? = null,
        extraClicksOn: Set<Int>? = null,
        clickThenLongOn: Boolean? = null,
        clickThenLongPressMs: Int? = null,
        clickThenLongRepeat: Boolean? = null,
        clickThenLongRepeatIntervalMs: Int? = null,
        clickThenLongRepeatVibrate: Boolean? = null,
        clickThenLongIsSerial: Boolean? = null,
        clickThenLongReleaseOn: Boolean? = null,
        clickThenLongFireOnRelease: Boolean? = null,
        doubleThenLongOn: Boolean? = null,
        tripleThenLongOn: Boolean? = null,
        doubleThenLongRepeat: Boolean? = null,
        doubleThenLongRepeatIntervalMs: Int? = null,
        doubleThenLongRepeatVibrate: Boolean? = null,
        doubleThenLongReleaseOn: Boolean? = null,
        doubleThenLongFireOnRelease: Boolean? = null,
        doubleThenLongIsSerial: Boolean? = null,
        tripleThenLongRepeat: Boolean? = null,
        tripleThenLongRepeatIntervalMs: Int? = null,
        tripleThenLongRepeatVibrate: Boolean? = null,
        tripleThenLongReleaseOn: Boolean? = null,
        tripleThenLongFireOnRelease: Boolean? = null,
        tripleThenLongIsSerial: Boolean? = null,
        judgeClicks: Int? = null,
    ): KeyConfig {
        var a = actions ?: this.actions
        if (single != null) a = a + (GestureTrigger.Click(1) to single)
        if (double != null) a = a + (GestureTrigger.Click(2) to double)
        if (long != null) a = a + (GestureTrigger.LongPress to long)
        if (longRelease != null) a = a + (GestureTrigger.Release to longRelease)
        if (clickThenLong != null) a = a + (GestureTrigger.ClickThenLong(1) to clickThenLong)
        if (clickThenLongRelease != null) a = a + (GestureTrigger.ClickThenLongRelease(1) to clickThenLongRelease)
        if (doubleThenLong != null) a = a + (GestureTrigger.ClickThenLong(2) to doubleThenLong)
        if (doubleThenLongRelease != null) a = a + (GestureTrigger.ClickThenLongRelease(2) to doubleThenLongRelease)
        if (tripleThenLong != null) a = a + (GestureTrigger.ClickThenLong(3) to tripleThenLong)
        if (tripleThenLongRelease != null) a = a + (GestureTrigger.ClickThenLongRelease(3) to tripleThenLongRelease)
        if (extraClicks != null) {
            a =
                a.filterKeys { !(it is GestureTrigger.Click && it.count >= 3) } +
                extraClicks.map { (count, action) -> GestureTrigger.Click(count) to action }
        }

        var p = params ?: this.params
        if (longPressMs != null) p = p.with(KeyParams.LONG_PRESS_MS, longPressMs)
        if (longRepeat != null) p = p.with(KeyParams.LONG_REPEAT, longRepeat)
        if (longRepeatIntervalMs != null) p = p.with(KeyParams.LONG_REPEAT_INTERVAL_MS, longRepeatIntervalMs)
        if (longRepeatVibrate != null) p = p.with(KeyParams.LONG_REPEAT_VIBRATE, longRepeatVibrate)
        if (longReleaseOn != null) p = p.with(KeyParams.LONG_RELEASE_ON, longReleaseOn)
        if (longFireOnRelease != null) p = p.with(KeyParams.LONG_FIRE_ON_RELEASE, longFireOnRelease)
        if (doubleClickMs != null) p = p.with(KeyParams.DOUBLE_CLICK_MS, doubleClickMs)
        if (comboOnly != null) p = p.with(KeyParams.COMBO_ONLY, comboOnly)
        if (waitMultiWindow != null) p = p.with(KeyParams.WAIT_MULTI_WINDOW, waitMultiWindow)
        if (multiWaitMs != null) p = p.with(KeyParams.MULTI_WAIT_MS, multiWaitMs)
        if (colorOsApi != null) p = p.with(KeyParams.COLOR_OS_API, colorOsApi)
        if (vibrate != null) p = p.with(KeyParams.VIBRATE, vibrate)
        if (vibrateStrength != null) p = p.with(KeyParams.VIBRATE_STRENGTH, vibrateStrength)
        if (vibrateOnNone != null) p = p.with(KeyParams.VIBRATE_ON_NONE, vibrateOnNone)
        if (toastOnRun != null) p = p.with(KeyParams.TOAST_ON_RUN, toastOnRun)
        if (toastFluid != null) p = p.with(KeyParams.TOAST_FLUID, toastFluid)
        if (toastNotify != null) p = p.with(KeyParams.TOAST_NOTIFY, toastNotify)
        if (toastTemplate != null) p = p.with(KeyParams.TOAST_TEMPLATE, toastTemplate)
        if (stepNotice != null) p = p.with(KeyParams.STEP_NOTICE, stepNotice)
        if (longIsSerial != null) p = p.with(KeyParams.LONG_IS_SERIAL, longIsSerial)
        if (sceneRelease != null) p = p.with(KeyParams.SCENE_RELEASE, sceneRelease)
        if (sceneLong != null) p = p.with(KeyParams.SCENE_LONG, sceneLong)
        if (sceneChain != null) p = p.with(KeyParams.SCENE_CHAIN, sceneChain)
        if (sceneChainRelease != null) p = p.with(KeyParams.SCENE_CHAIN_RELEASE, sceneChainRelease)
        if (doubleClickOn != null) p = p.with(KeyParams.DOUBLE_CLICK_ON, doubleClickOn)
        if (extraClicksOn != null) p = p.with(KeyParams.EXTRA_CLICKS_ON, extraClicksOn)
        if (clickThenLongOn != null) p = p.with(KeyParams.CLICK_THEN_LONG_ON, clickThenLongOn)
        if (clickThenLongPressMs != null) p = p.with(KeyParams.CLICK_THEN_LONG_PRESS_MS, clickThenLongPressMs)
        if (clickThenLongRepeat != null) p = p.with(KeyParams.CLICK_THEN_LONG_REPEAT, clickThenLongRepeat)
        if (clickThenLongRepeatIntervalMs != null) {
            p = p.with(KeyParams.CLICK_THEN_LONG_REPEAT_INTERVAL_MS, clickThenLongRepeatIntervalMs)
        }
        if (clickThenLongRepeatVibrate != null) {
            p = p.with(KeyParams.CLICK_THEN_LONG_REPEAT_VIBRATE, clickThenLongRepeatVibrate)
        }
        if (clickThenLongIsSerial != null) p = p.with(KeyParams.CLICK_THEN_LONG_IS_SERIAL, clickThenLongIsSerial)
        if (clickThenLongReleaseOn != null) p = p.with(KeyParams.CLICK_THEN_LONG_RELEASE_ON, clickThenLongReleaseOn)
        if (clickThenLongFireOnRelease != null) {
            p = p.with(KeyParams.CLICK_THEN_LONG_FIRE_ON_RELEASE, clickThenLongFireOnRelease)
        }
        if (doubleThenLongOn != null) p = p.with(KeyParams.DOUBLE_THEN_LONG_ON, doubleThenLongOn)
        if (tripleThenLongOn != null) p = p.with(KeyParams.TRIPLE_THEN_LONG_ON, tripleThenLongOn)
        if (doubleThenLongRepeat != null) p = p.with(KeyParams.DOUBLE_THEN_LONG_REPEAT, doubleThenLongRepeat)
        if (doubleThenLongRepeatIntervalMs != null) {
            p = p.with(KeyParams.DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS, doubleThenLongRepeatIntervalMs)
        }
        if (doubleThenLongRepeatVibrate != null) {
            p = p.with(KeyParams.DOUBLE_THEN_LONG_REPEAT_VIBRATE, doubleThenLongRepeatVibrate)
        }
        if (doubleThenLongReleaseOn != null) p = p.with(KeyParams.DOUBLE_THEN_LONG_RELEASE_ON, doubleThenLongReleaseOn)
        if (doubleThenLongFireOnRelease != null) {
            p = p.with(KeyParams.DOUBLE_THEN_LONG_FIRE_ON_RELEASE, doubleThenLongFireOnRelease)
        }
        if (doubleThenLongIsSerial != null) p = p.with(KeyParams.DOUBLE_THEN_LONG_IS_SERIAL, doubleThenLongIsSerial)
        if (tripleThenLongRepeat != null) p = p.with(KeyParams.TRIPLE_THEN_LONG_REPEAT, tripleThenLongRepeat)
        if (tripleThenLongRepeatIntervalMs != null) {
            p = p.with(KeyParams.TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS, tripleThenLongRepeatIntervalMs)
        }
        if (tripleThenLongRepeatVibrate != null) {
            p = p.with(KeyParams.TRIPLE_THEN_LONG_REPEAT_VIBRATE, tripleThenLongRepeatVibrate)
        }
        if (tripleThenLongReleaseOn != null) p = p.with(KeyParams.TRIPLE_THEN_LONG_RELEASE_ON, tripleThenLongReleaseOn)
        if (tripleThenLongFireOnRelease != null) {
            p = p.with(KeyParams.TRIPLE_THEN_LONG_FIRE_ON_RELEASE, tripleThenLongFireOnRelease)
        }
        if (tripleThenLongIsSerial != null) p = p.with(KeyParams.TRIPLE_THEN_LONG_IS_SERIAL, tripleThenLongIsSerial)
        if (judgeClicks != null) p = p.with(KeyParams.JUDGE_CLICKS, judgeClicks)

        return KeyConfig(
            id = id ?: this.id,
            enabled = enabled ?: this.enabled,
            actions = a,
            params = p,
            scenes = scenes ?: this.scenes,
            useOwnSettings = useOwnSettings ?: this.useOwnSettings,
        )
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is KeyConfig &&
                    id == other.id &&
                    enabled == other.enabled &&
                    actions == other.actions &&
                    params == other.params &&
                    scenes == other.scenes &&
                    useOwnSettings == other.useOwnSettings
            )

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + actions.hashCode()
        result = 31 * result + params.hashCode()
        result = 31 * result + scenes.hashCode()
        result = 31 * result + useOwnSettings.hashCode()
        return result
    }

    override fun toString(): String =
        "KeyConfig(id=$id, enabled=$enabled, actions=$actions, params=${params.toMap()}, useOwnSettings=$useOwnSettings)"

    fun actionOf(gesture: Gesture): Action = actionOf(gesture.trigger())

    /**
     * 按**触发条件**取动作 —— 自定义手势（第 3 击起）只有这一条通路。
     *
     *  三档与 [actionOf] 完全同源（那边就是转调这里）；`Click(n≥3)` 去 [extraClicks] 取，
     *  没配就是「无操作」—— **不退回单击**：少配一档就少一档，拿单击顶替等于按两下执行了一下。
     *
     *  [extraClicksOn] 里没有这一档时第 3 击起一律「无操作」：表里配着的东西不算数（见那一项的说明）。
     *  这一支是**读侧唯一的出口** —— 界面、判定、执行都经过它，所以「开关关了却还在执行」
     *  只可能是有人绕开这里去读了 [extraClicks]。
     */
    fun actionOf(trigger: GestureTrigger): Action =
        when (trigger) {
            GestureTrigger.LongPress -> {
                long
            }

            //  「长按后松手」是**另一个槽位**（[longRelease]）：与长按那一档分开配、分开执行
            GestureTrigger.Release -> {
                longRelease
            }

            //  「单击后长按」是**另一个槽位**（[clickThenLong]）—— 不是长按那一档：
            //  它由第二击的形态决定，与长按的「按住第一击」不是同一件事。
            //  判据用 [clickThenLongCounts]（开着**且**有动作）：开着但动作是无操作时，
            //  这一档按「不存在」处理 —— 判定侧那一侧根本不会派发它（见那边的三处读点）。
            is GestureTrigger.ClickThenLong -> {
                when (trigger.count) {
                    1 -> if (clickThenLongCounts) clickThenLong else Action.None
                    2 -> if (doubleThenLongOn) doubleThenLong else Action.None
                    else -> if (tripleThenLongOn) tripleThenLong else Action.None
                }
            }

            //  这一档的松手也**自成一路**：用它自己那一格，不借长按的 [longRelease]。
            //  **主档无操作时整条不成立**：松手动作是「这一档执行完之后」的收尾，
            //  主语没有就不该有谓语（用户口径「设为无操作时已设置的额外动作也不应该触发」）。
            is GestureTrigger.ClickThenLongRelease -> {
                thenLongReleaseAction(trigger.count)
            }

            is GestureTrigger.Click -> {
                when (trigger.count) {
                    1 -> single

                    //  双击关着时恒回「无操作」：与「没开的那几档多击」同一条口径
                    2 -> if (doubleClickOn) double else Action.None

                    else -> if (trigger.count in extraClicksOn) extraClicks[trigger.count] ?: Action.None else Action.None
                }
            }
        }

    /**
     * 把 [trigger] 那一档换成 [action] —— 写绑定的**唯一**入口（[actionOf] 的写侧，一个读一个写），
     * 界面那一层不许自己拼 `copy(single = …)`。
     *
     *  三档落在 [single] / [double] / [long]，第 3 击起落进 [extraClicks]。写成「无操作」时把
     *  那一格**从表里删掉**而不是存一条 `none`：`extraClicks[n] ?: Action.None` 读回来是同一个
     *  答案，而 `extra_clicks` 里留一串 `"3": "none"` 只会让配置多出几行没人读的字。
     *
     *  [GestureTrigger.Release] 写的是 [longRelease] —— 它是与长按**分开**的另一个槽位。
     *  调用方（界面）不走这一档：松手格有自己的卡片（`LongPressCard`），开关与动作是两位。
     *
     *  [extraClicksOn] 里没有这一档时**不写表**：它在界面上根本不可见，写进去只会留下一份
     *  「开关一开就冒出来」的隐藏绑定 —— 用户会以为那是自己配的。
     */
    fun withAction(
        trigger: GestureTrigger,
        action: Action,
    ): KeyConfig =
        when (trigger) {
            GestureTrigger.LongPress -> {
                copy(long = action)
            }

            GestureTrigger.Release -> {
                copy(longRelease = action)
            }

            is GestureTrigger.ClickThenLong -> {
                when (trigger.count) {
                    1 -> if (clickThenLongOn) copy(clickThenLong = action) else this
                    2 -> if (doubleThenLongOn) copy(doubleThenLong = action) else this
                    else -> if (tripleThenLongOn) copy(tripleThenLong = action) else this
                }
            }

            is GestureTrigger.ClickThenLongRelease -> {
                when (trigger.count) {
                    1 -> if (clickThenLongOn) copy(clickThenLongRelease = action) else this
                    2 -> if (doubleThenLongOn) copy(doubleThenLongRelease = action) else this
                    else -> if (tripleThenLongOn) copy(tripleThenLongRelease = action) else this
                }
            }

            is GestureTrigger.Click -> {
                when {
                    trigger.count == 1 -> copy(single = action)

                    //  双击关着时不写：它在界面上根本不可见，写进去只会留下一份
                    //  「开关一开就冒出来」的隐藏绑定（与下面多击那一支同一条）
                    trigger.count == 2 -> if (doubleClickOn) copy(double = action) else this

                    trigger.count in extraClicksOn -> copy(extraClicks = clickSlot(trigger.count, action))

                    else -> this
                }
            }
        }

    /** [withAction] 的第三击起那一支 —— 只改表里那一格，其余档位原样留着。 */
    private fun clickSlot(
        count: Int,
        action: Action,
    ): Map<Int, Action> = if (action is Action.None) extraClicks - count else extraClicks + (count to action)

    /**
     * 这颗键**真的绑了动作**的最高击数（0 = 连单击都没绑）—— 宿主的判据。
     *
     *  与 `KeyGesturePlan` 的取法同一个（那边按 `slots` 取），这里单独留一份是因为判定侧
     *  只看得到广播里的 [judgeClicks]，取不到动作表。
     *
     *  **条件分支也算绑定**：只给「双击」配了一条场景、不给 [double] 绑动作，是配置允许的常态。
     *  漏掉它的话判定侧认为最高只有一击，第一次抬手就把单击判掉了，
     *  那条双击场景永远等不到第二击 —— 而且日志里看不出任何异常。
     */
    val boundClicks: Int
        get() =
            maxOf(
                if (single.isBound) 1 else 0,
                //  双击那一档关着时不算（它在执行侧本来就回「无操作」，见 [doubleClickOn]）
                if (doubleClickOn && double.isBound) 2 else 0,
                //  **没开的那几档不算**：留着它们会让单击白等三个窗，
                //  而它们在执行侧本来就回「无操作」（见 [extraClicksOn]）。
                extraClicks
                    .filterKeys { it in extraClicksOn }
                    .filterValues { it.isBound }
                    .keys
                    .maxOrNull() ?: 0,
                sceneClicks,
            )

    /**
     * [scenes] 里按切出来的最高击数（1 = 只有单击那一档有动作；2 = 双击那一档有动作；0 = 没有可执行的）。
     *
     *  停用的、以及「条件还没配完」的块都不算 —— 它们永远不会执行，算进来只会让判定侧白等一个
     *  窗口（宁可按「没有」处理）。**只算真的绑了动作的档**（`isBound`）：某一块写着「双击 = 无操作」
     *  时不需要为它多等一次抬手 —— 那一档本来就什么都不做。
     *
     *  **第 3 击起也算**（那一档在 [extraClicksOn] 里时）：只在场景里配了「三击 → 某动作」、直接绑定
     *  一格没填，是配置允许的常态；漏掉它的话判定侧认为最高只有一击，那条三击场景永远等不到第 3 下。
     *  没开的那几档同样不算 —— 与 [boundClicks] 同一条口径。
     */
    private val sceneClicks: Int
        get() =
            scenes.maxOfOrNull { scene ->
                if (!scene.enabled || !scene.isReady) {
                    0
                } else {
                    scene.actions.entries
                        .filter { (trigger, action) -> trigger is GestureTrigger.Click && action.isBound }
                        .filter { (trigger, _) ->
                            //  双击那一档没开时不算：与「没开的多击」同一条口径
                            (trigger as GestureTrigger.Click).count.let { it == 1 || (it == 2 && doubleClickOn) || it in extraClicksOn }
                        }.maxOfOrNull { (trigger, _) -> (trigger as GestureTrigger.Click).count } ?: 0
                }
            } ?: 0

    /**
     * 判定侧**这一刻该用多长的窗口** —— 两击间隔与「逐次等满」共用的那一个数。
     *
     *  · [waitMultiWindow] 关着：只有 [doubleClickMs] 一件事可表，直接用它；
     *  · 开着：用 [multiWaitMs]，而它是 0（[FOLLOW_DOUBLE_CLICK_MS]）时仍回落到 [doubleClickMs]
     *    —— 老配置没带这一位，回落才保证升级前后一字不差。
     *
     *  收成一个属性而不是让注入侧自己 `if`：这条判据有两处读者（排窗 `scheduleDoubleWindow`
     *  与到点判定），各写一遍的话「排窗按 300、到点按 400」这种错位只会表现成偶发的手感异常。
     */
    override val clickWindowMs: Int
        get() =
            if (waitMultiWindow) {
                multiWaitMs.takeIf { it > FOLLOW_DOUBLE_CLICK_MS } ?: doubleClickMs
            } else {
                doubleClickMs
            }

    /**
     * 判定侧实际要用的那一档：广播那一列（[judgeLimit]）优先，没给就按动作表算（老版本兼容），
     * 再抬上「单击后长按」那个下限。
     *
     *  **按广播那一列的上限钳一次**：`boundClicks` 来自 `extraClicks` 的键，手改配置可以写成 20，
     *  而注入侧收到的 `judgeClicks` 在 [ConfigFields.F_JUDGE_CLICKS] 那里被钳到 9 ——
     *  不钳的话宿主等 20 击、注入侧只派发到 9，第 10 击以后永远出不来。
     *
     *  **[thenLongClicks] 的最高那一击就是下限**：这三档要数到那一击才判得出来（单击后长按是第 2 击、
     *  双击后长按是第 3 击、三击后长按是第 4 击）。不这么写的话，只绑了「双击后长按」的键
     *  `boundClicks` 是 0，注入侧会认为「最多一击」，第 1 击抬手时就把单击派发掉，那一档永远等不到
     *  —— 表现就是这个手势怎么按都不生效。
     *  它是**同一个数的下限**而不是另一个开关：注入侧只认 `judge_clicks` 这一位，
     *  多一位就多一处「两边不一致」的可能。
     */
    val clicksForJudge: Int
        get() =
            maxOf(
                judgeLimit,
                //  「后长按」那几档的下限：判定侧要数到那一击才判得出来（2 / 3 / 4 见 `thenLongClicks`）
                thenLongClicks.maxOrNull() ?: 0,
            ).coerceIn(0, ConfigFields.MAX_JUDGE_CLICKS)

    /**
     * 判定侧真正数到第几击就**封顶**（至少 1）。
     *
     *  写成属性只为一件事：判定器里「数到哪一档立刻判」与「逐次等时把多按的那几下
     *  夹回来」必须是同一个数。两处各写一遍，改一处漏一处就是「配了双击、按三下却判成无操作」。
     *
     *  **按「开了哪几档」算**（[doubleClickOn] / [extraClicksOn] / [thenLongClicks]），
     *  **不看那一档绑没绑动作**。用户口径（2026-11-15 真机反馈）：**多击开关开着就要等**，
     *  不想等就把那一档关掉。理由也直接：同时开两档、其中一档暂时是「无操作」时，
     *  另一档照样要等那次窗口，所以「没绑动作就不等」这条捷径在部分生效的配置上本来就不成立，
     *  只会让手感跟着「绑没绑」跳。
     *
     *  [clicksForJudge] 仍然要取大：三击 / 四击那种「只在场景里绑了动作」的配置，
     *  档位开关可能没开，但那一档真要执行。
     */
    override val judgeCap: Int
        get() =
            maxOf(
                if (doubleClickOn) 2 else 1,
                extraClicksOn.maxOrNull() ?: 1,
                thenLongClicks.maxOrNull() ?: 1,
                clicksForJudge,
            ).coerceAtMost(ConfigFields.MAX_JUDGE_CLICKS)

    /** 单击恒在；双击看 [doubleClickOn]；三击 / 四击就是 [extraClicksOn] 里开着的那几档。 */
    override val clickTiers: Set<Int>
        get() =
            buildSet {
                add(1)
                if (doubleClickOn) add(2)
                addAll(extraClicksOn)
            }

    /**
     * 判定侧**只按动作表**该数到第几击 —— 广播那一列（[judgeClicks]）优先，老版本没给时按
     * [boundClicks] 算。
     *
     *  与 [clicksForJudge] 的差别只有「单击后长按那个下限」：[clicksForJudge] 在它之上再抬到 2，
     *  而这里只回答「按动作表该数到几击」—— 抬过下限的那个数分不出「双击绑了动作」
     *  与「只是单击后长按要数到第 2 击」。
     *
     *  场景里绑的双击也在这个数里（`judgeClicks` 是宿主按场景表算好广播过来的），所以调用方
     *  不必再单独问「场景有没有给双击配动作」。
     *
     *  它是 `internal`：广播那一层要取这个数（`ConfigBroadcast.intent` 的 `JUDGE_CLICKS`）。
     */
    internal val judgeLimit: Int
        get() = if (judgeClicks > 0) judgeClicks else boundClicks

    /**
     * 这次手势该执行哪个动作 —— **条件分支的唯一入口**。
     *
     *  先按顺序找第一条成立的分支，一条都没有就退回 [actionOf] 那一档本来的绑定。
     *  实现只有 [pickAction] 一处（组合键 `ComboKey.resolve` 调的是同一个函数）。
     *  执行侧不要自己写这套 `firstOrNull`：条件维度以后会长，判据散在
     *  多处时「某个路径上条件分支不生效」这种 bug 只会在真机上被看见。
     *
     *  一个块可以带**好几条**条件，同种类之间「或」、不同种类之间「且」—— 那是
     *  [SceneRule.appliesTo] 的账，这里只负责顺序与「第一条赢」。
     *
     *  参数是**触发条件**而不是那个三值枚举：一块能接管哪几档由它自己的
     *  `SceneRule.actions` 说，所以「三击 / 四击也吃场景」不需要在这里多一个分支。
     *
     *  [matches] 是调用方给的判据（读系统开关的那一层，见 `ConditionProbe`）—— 这一层不认识
     *  任何系统接口。
     */
    fun actionFor(
        trigger: GestureTrigger,
        matches: (BranchCondition) -> Boolean,
    ): Action = pickAction(scenes, trigger, actionOf(trigger), matches)

    /**
     * 这一档**有没有被某个场景块接管** —— 「松手执行额外动作」要靠它让位。
     *
     *  与 [actionFor] 同一条判据（条件成立 + 这一档有值），只是不要动作、只要一个是非：
     *  长按那一档被场景接管了，松手那一刻就不该再补一刀。
     */
    fun sceneTakes(
        trigger: GestureTrigger,
        matches: (BranchCondition) -> Boolean,
    ): Boolean = sceneTakes(scenes, trigger, matches)

    /**
     * **松手那一刻执行什么** —— 长按被某一块接管时用那一块的松手格，否则用 [longRelease]。
     *
     *  实现在 [pickReleaseAction] 一处（组合键 `ComboKey.resolveRelease` 调的是同一个函数）。
     *  块命中时**不**再补 [longRelease]：一次按压一个结果 —— 那一块已经决定这一按做什么了。
     *
     *  **[longReleaseOn] 关着就传 `None` 进去**：开关关掉之后动作还留着（开关与动作是两位），
     *  不在这里收口的话，一块都不中时照样会执行一个用户以为已经关掉的动作。
     */
    fun resolveRelease(matches: (BranchCondition) -> Boolean): Action =
        pickReleaseAction(scenes, if (longReleaseOn) longRelease else Action.None, matches)

    /**
     * 长按绑的是不是**灵感语音那一族**（`oplus:voice_memory` / `oplus:voice_memory_stop`）。
     *
     *  两处判据必须是同一个口径（判定侧不排「长按连续触发」、界面写同一句原因）：
     *  连发对这两个动作都没有语义 —— 开始会被 ROM 当重入忽略（`reentrant collect.`），
     *  结束在没有采集时是空操作。见 `RomProfile.isVoiceMemory` 与
     *  `docs/device/actions/voice.md` 四十六。
     *
     *  写成计算属性、不落成字段：它是 [long] 的投影，落字段就要在 `ConfigFields` 那条链上
     *  多存一份**可以和 [long] 不一致**的真值。
     */
    val longIsVoiceMemory: Boolean
        get() = (long as? Action.Oplus)?.feature?.let(RomProfile::isVoiceMemory) == true

    /**
     * 「松手执行额外动作」这一刻**真的有事可做** —— 开关开着、并且已经挑了一个动作。
     *
     *  开关与动作是两位（见 [longReleaseOn]），只判其中一位都会错：
     *  · 只判动作：开关关掉之后动作还留着，松手会执行一个用户以为已经关掉的动作；
     *  · 只判开关：挑了动作才算数 —— 开着但没挑时松手本来就不做事。
     *
     *  第三位是[longFireOnRelease]：那条开着时长按本身已经推迟到松手，这一档再补一个
     *  「额外动作」就成了同一刻执行两个动作 —— 它让位，但**值原样留着**（关掉那一条之后回来）。
     *
     *  与 [longRepeat] **不冲突**：按住期间照常连续触发，松手那一刻再执行这一档 —— 两者是
     *  同一按的两个阶段（按住 / 抬手），不是互相占位的时序。
     *
     *  第四位是场景块里配的松手动作，两个来源取或：**[sceneRelease]**（注入侧那一份的替身 ——
     *  它手上没有场景表）与 `scenes.any { it.hasRelease }`（宿主 / 设置页这一份手上有表，就地算）。
     *  两处都在这里收口，是因为它同时服务两个判据：判定侧「发不发 `Release`」与界面
     *  「『松手时触发』那一行能不能拨」—— 只算一个来源的话，块里配了松手动作时界面还让人
     *  打开「松手时触发」，而那一开，块里的松手动作就再也不会执行了。
     *
     *  判定侧（`GestureDetector` 发不发那条广播）与界面都只读这一位。
     */
    override val releaseBound: Boolean
        get() =
            longActionExists &&
                !longFireOnRelease &&
                ((longReleaseOn && longRelease.isBound) || sceneRelease || scenes.any { it.hasRelease })

    /**
     * 长按这一档**本身**有事可做 —— 直接绑了动作，或某块场景里给长按配了动作
     * （场景表在注入侧是空的，那边靠广播来的 [sceneLong] 补上这一半）。
     *
     *  为什么松手那一档要看它：松手动作是「这一按长按**之后**再补的一个动作」——
     *  长按本身是无操作时，这一按根本不算执行过，再补一个松手动作就没有主语。
     *  界面那一侧也据它把「松手执行额外动作」整族压灰（见 `LongPressGates.releaseEnabled`）。
     */
    val longActionExists: Boolean
        get() = long.isBound || sceneLong || scenes.any { it.longBound }

    /**
     * 「单击后长按」这一档**本身**有事可做 —— 直接绑了动作，或某块场景里给它配了动作。
     *
     *  与 [longActionExists] 同一个用途（松手那一档要看它），区别只是档位不同。
     *  注入侧场景表是空的，所以那一边只认直接绑定 —— 场景接管的松手由宿主侧判。
     */
    override val clickThenLongActionExists: Boolean
        get() =
            clickThenLong.isBound ||
                sceneChain ||
                scenes.any { it.chainBound }

    /**
     * 「单击后长按」这一档**有没有事可做** —— 开着，**且**这一档真有动作（直接绑定 / 场景里配过）。
     *
     *  **它只回答「执行什么」，不再决定「这一档存不存在」**：判定侧认这一档看的是开关
     * （`clickThenLongOn`），动作是无操作时照样把第 2 击按住收走、只是不执行 —— 否则
     * 那一击会退到 `Click(2)` 再被逐级回退成**单击命令**（2026-11-15 真机口径）。
     *  现在它的读者是松开开关那几处（松手动作要不要补、广播里 `judge_clicks` 的下限）。
     */
    val clickThenLongCounts: Boolean
        get() = clickThenLongOn && clickThenLongActionExists

    /**
     * 「单击后长按」松手那一刻该执行什么（按键自己那一格，不含场景）。
     *
     *  四位缺一位都是 `None`：这一档开着、松手开关开着、**主档本身有事可做**
     *  （松手动作是那一档执行完之后的收尾，主语没有就不该有谓语），以及**没开「松手时触发」**
     *  —— 那一条把这一档的动作本身推到了抬手，再补一个额外动作就成了同一刻两个动作
     *  （与长按那一档的 `releaseBound` 同一条）。值原样留着，关掉那一位就回来。
     */
    val clickThenLongReleaseAction: Action
        get() =
            if (clickThenLongOn && clickThenLongReleaseOn && clickThenLongActionExists && !clickThenLongFireOnRelease) {
                clickThenLongRelease
            } else {
                Action.None
            }

    /**
     * 这一档要不要排「长按连续触发」—— 判定侧的**唯一判据**。
     *
     *  三条理由合成一处：
     *  · [longRepeat] 本身关着；
     *  · 走的是「松手才触发」（[longFireOnRelease]）：那一档到点什么都不执行、没有可重复的起点；
     *  · 长按绑的是多条命令（[longIsSerial]）：反复重放整条命令链没有意义；
     *  · 长按绑的是灵感语音那一族（[longIsVoiceMemory]）：开始 / 结束都是「按一下就完事」，
     *    重复拨没有语义。
     *
     *  写成属性而不是让判定侧现拼：`GestureDetector` 里那一处 `if` 原本已经几个条件，
     *  再加一条就顶破 detekt 的复杂度上限 —— 而这条口径本来也只该有一份。
     */
    override val longRepeats: Boolean
        get() = longRepeat && !longFireOnRelease && !longIsSerial && !longIsVoiceMemory

    /**
     * 「单击后长按」要不要排连发 —— 与 [longRepeats] **完全同一条口径**（四个条件一字不差）：
     * 开关关着 / 走「松手时触发」/ 绑的是多条命令 / 绑的是灵感语音，任一就排不出连发。
     *
     *  「松手时触发」（[clickThenLongFireOnRelease]）那一项进来之后，两档的连发判据就只剩字段名不同：
     * 到点不执行，就没有可重复的起点 —— 与长按那一档是同一条理由。
     *  界面那一侧读同一份规则（`repeatBlockedReason` + `LongPressGates.repeatEnabled`），两处不许各判一套。
     */
    override val clickThenLongRepeats: Boolean
        get() =
            clickThenLongRepeat &&
                !clickThenLongFireOnRelease &&
                !clickThenLongIsSerial &&
                !((clickThenLong as? Action.Oplus)?.feature?.let(RomProfile::isVoiceMemory) == true)

    /**
     * 「双击后长按」/「三击后长按」的松手动作 —— 与 [clickThenLongReleaseAction] **同一个四位判据**：
     * 开关开着、松手开关开着、主档本身有动作，且没开「松手时触发」（那一条把动作推到了抬手）。
     *
     *  「主档有动作」这两档只认**直接绑定**（`isBound`）：注入侧能读到的场景标记只有「单击后长按」
     * 那一份（`F_SCENE_CHAIN`），为这两档再开广播字段就成了两份判据，注入侧还要自己挑一份用。
     */
    val doubleThenLongReleaseAction: Action
        get() =
            if (doubleThenLongOn && doubleThenLongReleaseOn && doubleThenLong.isBound && !doubleThenLongFireOnRelease) {
                doubleThenLongRelease
            } else {
                Action.None
            }

    val tripleThenLongReleaseAction: Action
        get() =
            if (tripleThenLongOn && tripleThenLongReleaseOn && tripleThenLong.isBound && !tripleThenLongFireOnRelease) {
                tripleThenLongRelease
            } else {
                Action.None
            }

    /** 第 [count] 击「后长按」的松手动作（1 / 2 / 3 各一格）—— 读侧的唯一出口。 */
    private fun thenLongReleaseAction(count: Int): Action =
        when (count) {
            1 -> clickThenLongReleaseAction
            2 -> doubleThenLongReleaseAction
            else -> tripleThenLongReleaseAction
        }

    /**
     * 「双击后长按」/「三击后长按」要不要排连发 —— 与 [clickThenLongRepeats] 四条一字不差
     * （开关 / 没走「松手时触发」/ 不是多条命令 / 不是灵感语音）。
     */
    val doubleThenLongRepeats: Boolean
        get() =
            doubleThenLongRepeat &&
                !doubleThenLongFireOnRelease &&
                !doubleThenLongIsSerial &&
                !((doubleThenLong as? Action.Oplus)?.feature?.let(RomProfile::isVoiceMemory) == true)

    val tripleThenLongRepeats: Boolean
        get() =
            tripleThenLongRepeat &&
                !tripleThenLongFireOnRelease &&
                !tripleThenLongIsSerial &&
                !((tripleThenLong as? Action.Oplus)?.feature?.let(RomProfile::isVoiceMemory) == true)

    /**
     * 第 [clicks] 击那一档「后长按」的判定参数（`clicks` = 2 / 3 / 4 = 单击 / 双击 / 三击后长按）——
     * `null` = 这一档没开。
     *
     *  **按住时长三档共用一个**（[clickThenLongPressMs]）：两段式里「第二段按住多久算数」是同一件事，
     *  按击数各调一遍只会让手感分叉。其余时序与收尾各读自己那一份（见 `ThenLongParams`）。
     */
    override fun thenLongAt(clicks: Int): ThenLongParams? =
        when (clicks) {
            2 -> {
                if (clickThenLongOn) {
                    ThenLongParams(
                        pressMs = clickThenLongPressMs,
                        fireOnRelease = clickThenLongFireOnRelease,
                        repeatIntervalMs = clickThenLongRepeatIntervalMs,
                        repeats = clickThenLongRepeats,
                        releaseActionExists = clickThenLongActionExists,
                    )
                } else {
                    null
                }
            }

            3 -> {
                if (doubleThenLongOn) {
                    ThenLongParams(
                        pressMs = clickThenLongPressMs,
                        fireOnRelease = doubleThenLongFireOnRelease,
                        repeatIntervalMs = doubleThenLongRepeatIntervalMs,
                        repeats = doubleThenLongRepeats,
                        releaseActionExists = doubleThenLongReleaseAction.isBound,
                    )
                } else {
                    null
                }
            }

            4 -> {
                if (tripleThenLongOn) {
                    ThenLongParams(
                        pressMs = clickThenLongPressMs,
                        fireOnRelease = tripleThenLongFireOnRelease,
                        repeatIntervalMs = tripleThenLongRepeatIntervalMs,
                        repeats = tripleThenLongRepeats,
                        releaseActionExists = tripleThenLongReleaseAction.isBound,
                    )
                } else {
                    null
                }
            }

            else -> {
                null
            }
        }

    companion object {
        /**
         * 「**固定等**」那一档的默认值（毫秒）—— 350：固定窗口起点**开着**时整串共用的那个窗口，
         * 与组合四格里「固定等」那两格同数（[GlobalConfig.DEFAULT_COMBO_WINDOW_MS]）。
         *
         *  取哪一位的判据在 [clickWindowMs]：`waitMultiWindow` 为假（固定窗口起点开）读**本值**，
         *  为真（每次松手重新起算）读 [multiWaitMs]。它同时是 [multiWaitMs] 为 0 时的回落值
         *  ——老配置没有「等待窗口」这一项，跟着它走才保证升级前后手感一字不差。
         */
        const val DEFAULT_DOUBLE_CLICK_MS = 350

        /**
         * 多击的**第一档**（第 3 击起；1 / 2 落在 [single] / [double]）与界面排在后面的那几档。
         *
         *  这三处（本条、[DEFAULT_EXTRA_CLICKS]、判定上限 [ConfigFields.MAX_JUDGE_CLICKS]）一起
         *  把「多击能到第几击」说全。各写一个字面量迟早出现「界面排到第 4 页、判定只认到第 3 击」。
         */
        const val MIN_EXTRA_CLICKS = 3

        val DEFAULT_EXTRA_CLICKS: List<Int> = listOf(3, 4)

        /**
         * 一串击数 → 打开的档位集合 —— 只留第 3 击起、且不超判定上限的那几个。
         *
         *  落盘（JSON 数组）与广播（逗号文本）两条读法都经过这里：两处各写一遍过滤条件，
         *  迟早出现「手改的 `5` 在广播里被认、在文件里被忽略」这种只有一边生效的状态。
         *  脏值丢掉那一项而不是整串作废 —— 少认一档只是「那一档不执行」，整串作废是「全都不要了」。
         */
        fun clicksOnOf(counts: Iterable<Int>): Set<Int> = counts.filter { it in MIN_EXTRA_CLICKS..ConfigFields.MAX_JUDGE_CLICKS }.toSet()

        /** 广播里那串 `"3,4"` → 打开的档位集合（`fieldValues()` 的逆）。 */
        fun clicksOnOf(text: String): Set<Int> = clicksOnOf(text.split(',').mapNotNull { it.trim().toIntOrNull() })

        /**
         * 等待窗口的下限 —— **50ms**：六根窗口滑杆（单击延迟、多击窗口、组合四格）共用这一档。
         *
         *  实测「一起按」两颗键的相差在 0~40ms（谁先上报看硬件），50 刚好盖住那段抖动；
         *  再往下调就几乎是「必须精确同时按」，组合键会挑手。
         */
        const val MIN_DOUBLE_CLICK_MS = 50

        /**
         * 「**固定等**」那一档的上限 —— 1000ms（固定窗口起点开着：整串只给一次预算）。
         *
         *  三颗以上的连击、手慢的人确实需要宽一档；而这一档的窗口同时也是「单击要等多久才
         *  回落」，上限就是它的代价。
         */
        const val MAX_DOUBLE_CLICK_MS = 1000

        /**
         * [multiWaitMs] 的「跟随 [doubleClickMs]」取值 —— 见那一位的 KDoc。
         *
         *  用 0 而不是 `-1`：0 本来就是「没有窗口」这个合法极值之外的空位，而负数会让
         *  `ConfigFields.Kind.Number` 的下限校验多一条要解释的特例。
         */
        const val FOLLOW_DOUBLE_CLICK_MS = 0

        /**
         * 「**逐次等**」那一档的默认值（毫秒）—— 250：每次松手重新起算时用的那个窗口，
         * 与组合四格里「逐次等」那两格同数（[GlobalConfig.DEFAULT_COMBO_PAIR_MS]）。
         *
         *  **0 仍是「跟随 [DEFAULT_DOUBLE_CLICK_MS]」**（[FOLLOW_DOUBLE_CLICK_MS]）：老配置没有
         *  这一项时读到的就是 0，跟着单击延迟走才能保证升级前后一字不差。出厂值只影响新装 / 复位。
         */
        const val DEFAULT_MULTI_WAIT_MS = 250

        /**
         * 「**逐次等**」那一档的上限 —— 800ms（每次松手重新起算时用的那个窗口）。
         *
         *  这一档量的是**相邻两击之间**。单看两击，人类自然间隔是 150~250ms；但**三击 / 四击**
         *  的后面几下往往比前两下松得多（第三下发力的时机最难踩准），500ms 那一档实测抓不住。
         *
         *  与 [MAX_DOUBLE_CLICK_MS]（固定等 1000）仍然**刻意不同**：以两者分开为代价，那根滑杆在
         *  切换判定时机时右端会跳一截 —— 这是两档各存一份的必然结果。
         */
        const val MAX_MULTI_WAIT_MS = 800

        /**
         * 「长按延迟」的出厂值 —— 350ms。
         *
         *  比人手一次自然按压（80~150ms）宽一档，又比原来的 500ms 跟手；判定只拿它排长按那一枪，
         *  调小只是更容易判成长按。逐键、组合、成员「单击后长按」三处都读它（各自可在界面上另设）。
         */
        const val DEFAULT_LONG_PRESS_MS = 350

        /**
         * 长按阈值下限 —— **200ms 已经高于人手一次自然按压（80~150ms）**，所以任何一档下
         * 「正常按一下」都不会被判成长按，单击 / 双击都按得出来。
         *
         * 原来这一档是 100ms（落在自然按压里面）：「稍按久一点就变长按」，单击基本按不出来。
         * 下限抬到 200ms 就是为了堵掉这个组合，用户不必先踩一次才知道。
         */
        const val MIN_LONG_PRESS_MS = 200

        /**
         * 长按阈值上限 —— **800ms**（用户口径）。
         *
         *  上限不参与判定正确性：判定只拿它排长按那一枪，越长只是越不容易误判成长按，
         *  代价是长按动作要等更久（`GestureDetector.scheduleLongPress`）。它与三档「后长按」共用
         *  这一档：那三档是同一件事的不同击数，按住多久的可行区间不该按档分叉。
         *
         *  滑杆与 `ConfigFields` 的钳制都取这一个数 —— 各写一份时，滑杆尽头的值与落盘被压回的
         *  值会不一样。
         */
        const val MAX_LONG_PRESS_MS = 800

        /**
         * 「长按连续触发」的出厂间隔 —— 200ms。
         *
         *  一秒里约五下：看得出在连发，又不至于糊成一片。逐键、组合、成员「单击后长按」三处
         *  都读它（各自可在界面上另设，区间见下面两条）。
         */
        const val DEFAULT_REPEAT_INTERVAL_MS = 200

        /**
         * 连发间隔下限 —— 50ms（用户口径）：再快就与「按住不放」的体感脱节，且按键注入本身也要时间。
         *
         *  判定层（`inject/input/ChainSpec`）另有一份**同值**副本：那一层不吃 `core.model`，
         *  引不到这里。改这个数要两处一起改，否则判定层会静默顶掉用户配到下限以下的值。
         */
        const val MIN_REPEAT_INTERVAL_MS = 50

        /**
         * 连发间隔上限 —— **500ms**：一秒两下是「看得出在连发」的极限。
         *
         *  再往上，一次按住一秒里只重复一次，「连续触发」就不成其为连续了 —— 那种节奏该由
         *  动作自己去排（计时器 / 多步命令）。滑杆与 `ConfigFields` 的钳制都取这一个数。
         */
        const val MAX_REPEAT_INTERVAL_MS = 500

        /**
         * 某个键的默认值：**内置键默认接管、自定义键默认不接管**。
         *
         *  「装上模块不改变行为」现在由**模块总闸**负责（`GlobalConfig.enabled` 默认 false），
         *  不再靠这颗键的开关 —— 所以内置的侧键可以默认是开的：用户第一次打开总闸时它立刻可用。
         *  反过来，侧键也默认关着的话，第一次打开总闸的表现是「开了没反应」，得先去绑一个动作
         *  才活（`pick` 会顺手置开）—— 那是把一个初始化细节漏给了用户。
         *
         *  自定义键仍默认关：它们是被**收集**进来的，收集那一步自己置开（`Config.withCustomKey`）；
         *  而「配置里认不出的键」也落在这一档 —— 不接管比接管安全，且删键时
         *  `ConfigChannel` 靠「查不到 ⇒ enabled=false」判出「这一颗刚消失」。
         *
         * 注意：单击那档的默认值是「无操作」而不是「交还系统」—— 后者已删（见 `Action` 的注释）。
         * 总闸开着而这一档还没配时，按键被吞掉什么都不做；这也是「还没配」最不容易误读的表现。
         */
        fun defaultFor(id: KeyId) =
            //  出厂值全在注册表里（`KeyParams` 的 default）—— 这里不再列一遍，
            //  否则「默认关 / 默认开」会有两处真源。
            KeyConfig(
                id = id,
                enabled = !id.isCustom,
            )

        /**
         * 从取值表还原一个键的配置 —— 与 [KeyConfig.fieldValues] 成对，两条通道共用。
         *
         *  这里**不再钳制区间**：钳制是读取方对「外来文本」做的事（上下限就在
         *  `ConfigFields` 的字段表里），走到这里时值已经收口过。
         *
         *  [scenes] 不在取值表里（那是列表，不是扁平的一格，见 [KeyConfig.scenes]）——
         *  由落盘那一侧单独读出来传进来；广播那条路不传，注入侧拿到的就是空表。
         */
        fun ofFields(
            id: KeyId,
            v: ConfigFields.Values,
            scenes: List<SceneRule> = emptyList(),
        ): KeyConfig {
            //  动作 → 档位表；设置项 → 值包。广播这条通道只在这里收一次口：
            //  两边（`fieldValues` 与这里）必须成对 —— 只写不读回来的表现是「界面上改了、
            //  也广播了，但判定与执行那边照旧」，配置里有值、内存里是默认值。
            val actions =
                listOf(
                    GestureTrigger.Click(1) to v.text(ConfigFields.F_SINGLE),
                    GestureTrigger.Click(2) to v.text(ConfigFields.F_DOUBLE),
                    GestureTrigger.LongPress to v.text(ConfigFields.F_LONG),
                    GestureTrigger.Release to v.text(ConfigFields.F_LONG_RELEASE),
                    GestureTrigger.ClickThenLong(1) to v.text(ConfigFields.F_CTL),
                    GestureTrigger.ClickThenLongRelease(1) to v.text(ConfigFields.F_CTL_RELEASE),
                    GestureTrigger.ClickThenLong(2) to v.text(ConfigFields.F_DOUBLE_THEN_LONG),
                    GestureTrigger.ClickThenLongRelease(2) to v.text(ConfigFields.F_DOUBLE_THEN_LONG_RELEASE),
                    GestureTrigger.ClickThenLong(3) to v.text(ConfigFields.F_TRIPLE_THEN_LONG),
                    GestureTrigger.ClickThenLongRelease(3) to v.text(ConfigFields.F_TRIPLE_THEN_LONG_RELEASE),
                ).map { (trigger, raw) -> trigger to Action.parse(raw) }
                    //  「无操作」不写进表：表里缺一档与写着 `none` 同义，写进去只会让
                    //  往返比较多出一层「元素相同但表示不同」的噪声。判据走 `isBound`
                    //  （全项目唯一那把尺子），`follow` 对按键没有意义，一并落在表外。
                    .filter { (_, action) -> action.isBound }
                    .toMap()
            val params =
                Params.EMPTY
                    .with(KeyParams.LONG_PRESS_MS, v.number(ConfigFields.F_LONG_PRESS_MS))
                    .with(KeyParams.LONG_REPEAT, v.bool(ConfigFields.F_LONG_REPEAT))
                    .with(KeyParams.LONG_REPEAT_INTERVAL_MS, v.number(ConfigFields.F_REPEAT_INTERVAL_MS))
                    .with(KeyParams.LONG_REPEAT_VIBRATE, v.bool(ConfigFields.F_REPEAT_VIBRATE))
                    .with(KeyParams.LONG_RELEASE_ON, v.bool(ConfigFields.F_LONG_RELEASE_ON))
                    .with(KeyParams.LONG_FIRE_ON_RELEASE, v.bool(ConfigFields.F_LONG_FIRE_ON_RELEASE))
                    .with(KeyParams.DOUBLE_CLICK_MS, v.number(ConfigFields.F_DOUBLE_CLICK_MS))
                    .with(KeyParams.COMBO_ONLY, v.bool(ConfigFields.F_COMBO_ONLY))
                    .with(KeyParams.WAIT_MULTI_WINDOW, v.bool(ConfigFields.F_WAIT_MULTI_WINDOW))
                    .with(KeyParams.MULTI_WAIT_MS, v.number(ConfigFields.F_MULTI_WAIT_MS))
                    .with(KeyParams.COLOR_OS_API, v.bool(ConfigFields.F_KEY_COLOR_OS_API))
                    .with(KeyParams.VIBRATE, v.bool(ConfigFields.F_KEY_VIBRATE))
                    .with(KeyParams.VIBRATE_STRENGTH, v.number(ConfigFields.F_KEY_VIBRATE_STRENGTH))
                    .with(KeyParams.VIBRATE_ON_NONE, v.bool(ConfigFields.F_KEY_VIBRATE_ON_NONE))
                    .with(KeyParams.TOAST_ON_RUN, v.bool(ConfigFields.F_KEY_TOAST_ON_RUN))
                    .with(KeyParams.TOAST_FLUID, v.bool(ConfigFields.F_KEY_TOAST_FLUID))
                    .with(KeyParams.TOAST_NOTIFY, v.bool(ConfigFields.F_KEY_TOAST_NOTIFY))
                    .with(KeyParams.TOAST_TEMPLATE, v.text(ConfigFields.F_KEY_TOAST_TEMPLATE))
                    .with(KeyParams.STEP_NOTICE, v.bool(ConfigFields.F_KEY_STEP_NOTICE))
                    .with(KeyParams.LONG_IS_SERIAL, v.bool(ConfigFields.F_LONG_SERIAL))
                    .with(KeyParams.SCENE_RELEASE, v.bool(ConfigFields.F_SCENE_RELEASE))
                    .with(KeyParams.SCENE_LONG, v.bool(ConfigFields.F_SCENE_LONG))
                    .with(KeyParams.SCENE_CHAIN, v.bool(ConfigFields.F_SCENE_CHAIN))
                    .with(KeyParams.SCENE_CHAIN_RELEASE, v.bool(ConfigFields.F_SCENE_CHAIN_RELEASE))
                    .with(KeyParams.DOUBLE_CLICK_ON, v.bool(ConfigFields.F_DOUBLE_CLICK_ON))
                    .with(KeyParams.EXTRA_CLICKS_ON, clicksOnOf(v.text(ConfigFields.F_EXTRA_CLICKS)))
                    .with(KeyParams.CLICK_THEN_LONG_ON, v.bool(ConfigFields.F_CLICK_THEN_LONG_ON))
                    .with(KeyParams.CLICK_THEN_LONG_PRESS_MS, v.number(ConfigFields.F_CTL_PRESS_MS))
                    .with(KeyParams.CLICK_THEN_LONG_REPEAT, v.bool(ConfigFields.F_CTL_REPEAT))
                    .with(KeyParams.CLICK_THEN_LONG_REPEAT_INTERVAL_MS, v.number(ConfigFields.F_CTL_REPEAT_INTERVAL_MS))
                    .with(KeyParams.CLICK_THEN_LONG_REPEAT_VIBRATE, v.bool(ConfigFields.F_CTL_REPEAT_VIBRATE))
                    .with(KeyParams.CLICK_THEN_LONG_IS_SERIAL, v.bool(ConfigFields.F_CTL_SERIAL))
                    .with(KeyParams.CLICK_THEN_LONG_RELEASE_ON, v.bool(ConfigFields.F_CTL_RELEASE_ON))
                    .with(KeyParams.CLICK_THEN_LONG_FIRE_ON_RELEASE, v.bool(ConfigFields.F_CTL_FIRE_ON_RELEASE))
                    .with(KeyParams.DOUBLE_THEN_LONG_ON, v.bool(ConfigFields.F_DOUBLE_THEN_LONG_ON))
                    .with(KeyParams.TRIPLE_THEN_LONG_ON, v.bool(ConfigFields.F_TRIPLE_THEN_LONG_ON))
                    .with(KeyParams.DOUBLE_THEN_LONG_REPEAT, v.bool(ConfigFields.F_DOUBLE_THEN_LONG_REPEAT))
                    .with(KeyParams.DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS, v.number(ConfigFields.F_DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS))
                    .with(KeyParams.DOUBLE_THEN_LONG_REPEAT_VIBRATE, v.bool(ConfigFields.F_DOUBLE_THEN_LONG_REPEAT_VIBRATE))
                    .with(KeyParams.DOUBLE_THEN_LONG_RELEASE_ON, v.bool(ConfigFields.F_DOUBLE_THEN_LONG_RELEASE_ON))
                    .with(KeyParams.DOUBLE_THEN_LONG_FIRE_ON_RELEASE, v.bool(ConfigFields.F_DOUBLE_THEN_LONG_FIRE_ON_RELEASE))
                    .with(KeyParams.DOUBLE_THEN_LONG_IS_SERIAL, v.bool(ConfigFields.F_DOUBLE_THEN_LONG_SERIAL))
                    .with(KeyParams.TRIPLE_THEN_LONG_REPEAT, v.bool(ConfigFields.F_TRIPLE_THEN_LONG_REPEAT))
                    .with(KeyParams.TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS, v.number(ConfigFields.F_TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS))
                    .with(KeyParams.TRIPLE_THEN_LONG_REPEAT_VIBRATE, v.bool(ConfigFields.F_TRIPLE_THEN_LONG_REPEAT_VIBRATE))
                    .with(KeyParams.TRIPLE_THEN_LONG_RELEASE_ON, v.bool(ConfigFields.F_TRIPLE_THEN_LONG_RELEASE_ON))
                    .with(KeyParams.TRIPLE_THEN_LONG_FIRE_ON_RELEASE, v.bool(ConfigFields.F_TRIPLE_THEN_LONG_FIRE_ON_RELEASE))
                    .with(KeyParams.TRIPLE_THEN_LONG_IS_SERIAL, v.bool(ConfigFields.F_TRIPLE_THEN_LONG_SERIAL))
                    .with(KeyParams.JUDGE_CLICKS, v.number(ConfigFields.F_JUDGE_CLICKS))
            return KeyConfig(
                id = id,
                enabled = v.bool(ConfigFields.F_ENABLED),
                actions = actions,
                params = params,
                scenes = scenes,
                useOwnSettings = v.bool(ConfigFields.F_OWN_SETTINGS),
            )
        }
    }
}

/**
 * 全局配置（不属于任何单个键）。
 *
 *  与 `KeyConfig` 同一套存储纪律：一切设置项在 [params]（`GlobalParams`），只剩 [commonCommands]
 *  是结构（一张表）。加一项设置只动注册表；所有旧字段名保留为只读属性，读的地方零改动。
 */
class GlobalConfig(
    val params: Params = Params.EMPTY,
    val commonCommands: List<CommonCommand> = emptyList(),
) : OwnSettings {
    /** **模块总闸**：关掉 ⇒ 一颗键都不接管。默认关（装上模块不改变任何行为）。 */
    val enabled: Boolean get() = params[GlobalParams.ENABLED]

    /** **界面振动反馈** —— 点行、拨开关、拖滑杆时那一下触觉（与 [vibrate] 是两件事）。默认开。 */
    val uiVibrate: Boolean get() = params[GlobalParams.UI_VIBRATE]

    /** **使用预测返回手势** —— 只在 API 34+ 有效。默认开。 */
    val predictiveBack: Boolean get() = params[GlobalParams.PREDICTIVE_BACK]

    /** **按键设置页显示更详细的参数** —— 纯显示项，判定与执行都不读它。默认关。 */
    val detailedKeySettings: Boolean get() = params[GlobalParams.DETAILED_KEY_SETTINGS]

    /** 公共设置里的**单击延迟**（毫秒）—— 没开「使用独立设置」的键都用这一份。 */
    val doubleClickMs: Int get() = params[GlobalParams.DOUBLE_CLICK_MS]

    /** 公共设置里的**判定时机**（`false` = 固定窗口起点，`true` = 每次松手重新起算；默认后者）。 */
    val waitMultiWindow: Boolean get() = params[GlobalParams.WAIT_MULTI_WINDOW]

    /** 公共设置里的**等待窗口**（出厂 250；0 = 跟随 [doubleClickMs]，只留给老配置）。 */
    val multiWaitMs: Int get() = params[GlobalParams.MULTI_WAIT_MS]

    /** 公共设置里的**「单击和多击和长按延迟」**—— 三档「后长按」共用的那个按住时长。 */
    val clickThenLongPressMs: Int get() = params[GlobalParams.CLICK_THEN_LONG_PRESS_MS]

    /** 组合键成员「后长按」的默认按住时长（那一档没开独立设置时读它）。 */
    val comboMemberClickThenLongPressMs: Int get() = params[GlobalParams.COMBO_MEMBER_CTL_PRESS_MS]

    override val vibrate: Boolean get() = params[GlobalParams.VIBRATE]
    override val vibrateStrength: Int get() = params[GlobalParams.VIBRATE_STRENGTH]
    override val vibrateOnNone: Boolean get() = params[GlobalParams.VIBRATE_ON_NONE]
    override val toastOnRun: Boolean get() = params[GlobalParams.TOAST_ON_RUN]
    override val toastFluid: Boolean get() = params[GlobalParams.TOAST_FLUID]
    override val toastNotify: Boolean get() = params[GlobalParams.TOAST_NOTIFY]
    override val stepNotice: Boolean get() = params[GlobalParams.STEP_NOTICE]
    override val toastTemplate: String get() = params[GlobalParams.TOAST_TEMPLATE]

    val language: String get() = params[GlobalParams.LANGUAGE]
    val palette: String get() = params[GlobalParams.PALETTE]
    val theme: String get() = params[GlobalParams.THEME]
    val monetColors: Boolean get() = params[GlobalParams.MONET_COLORS]
    val pageScale: Int get() = params[GlobalParams.PAGE_SCALE]
    val iconTintSeed: Int get() = params[GlobalParams.ICON_TINT_SEED]
    val showCommonCommands: Boolean get() = params[GlobalParams.SHOW_COMMON_COMMANDS]
    val reduceIcons: Boolean get() = params[GlobalParams.REDUCE_ICONS]

    /** 两种组合的排序（`wait` / `hold` 各一份）—— 认不出的 id 原样留着，界面落回「不排序」。 */
    val waitComboSort: String get() = params[GlobalParams.WAIT_COMBO_SORT]
    val holdComboSort: String get() = params[GlobalParams.HOLD_COMBO_SORT]

    override val colorOsApi: Boolean get() = params[GlobalParams.COLOR_OS_API]
    val miniWindowAnim: Boolean get() = params[GlobalParams.MINI_WINDOW_ANIM]

    /** 组合键四格窗口 + 首键按住下限（`0` = 跟随出厂值，见 `ComboKey` 那几个 `*_FOLLOW_GLOBAL`）。 */
    val comboWaitWindowMs: Int get() = params[GlobalParams.COMBO_WAIT_WINDOW_MS]
    val comboWaitPairMs: Int get() = params[GlobalParams.COMBO_WAIT_PAIR_MS]
    val comboHoldWindowMs: Int get() = params[GlobalParams.COMBO_HOLD_WINDOW_MS]
    val comboHoldFollowerMs: Int get() = params[GlobalParams.COMBO_HOLD_FOLLOWER_MS]
    val comboHoldMinMs: Int get() = params[GlobalParams.COMBO_HOLD_MIN_MS]

    /** 按住组合里点首键那一下要不要也跑场景（与「首键自己的长按」是两件事）。 */
    val comboSceneOnStarter: Boolean get() = params[GlobalParams.COMBO_SCENE_ON_STARTER]
    val comboStarterKeepLong: Boolean get() = params[GlobalParams.COMBO_STARTER_KEEP_LONG]
    val comboFixedWindowStart: Boolean get() = params[GlobalParams.COMBO_FIXED_WINDOW_START]
    val comboHoldFixedWindowStart: Boolean get() = params[GlobalParams.COMBO_HOLD_FIXED_WINDOW_START]

    /** 公共这一层永远是「自己的值」（没有更上一层可跟随）。 */
    override val useOwnSettings: Boolean get() = true

    /** 写入口：改一项设置。 */
    fun <T> with(
        spec: Param<T>,
        value: T,
    ): GlobalConfig = GlobalConfig(params.with(spec, value), commonCommands)

    /**
     * 与 data class 那一版 `copy` 逐参数同名同义：没传的字段保持原值（调用点零改动）。
     *
     *  同上：长度与复杂度是参数个数的线性叠加（每个参数一句 `if (x != null)`），拆开只会多一层壳。
     */
    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    fun copy(
        params: Params? = null,
        commonCommands: List<CommonCommand>? = null,
        enabled: Boolean? = null,
        uiVibrate: Boolean? = null,
        predictiveBack: Boolean? = null,
        detailedKeySettings: Boolean? = null,
        doubleClickMs: Int? = null,
        waitMultiWindow: Boolean? = null,
        multiWaitMs: Int? = null,
        clickThenLongPressMs: Int? = null,
        comboMemberClickThenLongPressMs: Int? = null,
        vibrate: Boolean? = null,
        vibrateStrength: Int? = null,
        vibrateOnNone: Boolean? = null,
        toastOnRun: Boolean? = null,
        toastFluid: Boolean? = null,
        toastNotify: Boolean? = null,
        stepNotice: Boolean? = null,
        toastTemplate: String? = null,
        language: String? = null,
        palette: String? = null,
        theme: String? = null,
        monetColors: Boolean? = null,
        pageScale: Int? = null,
        iconTintSeed: Int? = null,
        showCommonCommands: Boolean? = null,
        reduceIcons: Boolean? = null,
        waitComboSort: String? = null,
        holdComboSort: String? = null,
        colorOsApi: Boolean? = null,
        miniWindowAnim: Boolean? = null,
        comboWaitWindowMs: Int? = null,
        comboWaitPairMs: Int? = null,
        comboHoldWindowMs: Int? = null,
        comboHoldFollowerMs: Int? = null,
        comboHoldMinMs: Int? = null,
        comboSceneOnStarter: Boolean? = null,
        comboStarterKeepLong: Boolean? = null,
        comboFixedWindowStart: Boolean? = null,
        comboHoldFixedWindowStart: Boolean? = null,
    ): GlobalConfig {
        var p = params ?: this.params
        if (enabled != null) p = p.with(GlobalParams.ENABLED, enabled)
        if (uiVibrate != null) p = p.with(GlobalParams.UI_VIBRATE, uiVibrate)
        if (predictiveBack != null) p = p.with(GlobalParams.PREDICTIVE_BACK, predictiveBack)
        if (detailedKeySettings != null) p = p.with(GlobalParams.DETAILED_KEY_SETTINGS, detailedKeySettings)
        if (doubleClickMs != null) p = p.with(GlobalParams.DOUBLE_CLICK_MS, doubleClickMs)
        if (waitMultiWindow != null) p = p.with(GlobalParams.WAIT_MULTI_WINDOW, waitMultiWindow)
        if (multiWaitMs != null) p = p.with(GlobalParams.MULTI_WAIT_MS, multiWaitMs)
        if (clickThenLongPressMs != null) p = p.with(GlobalParams.CLICK_THEN_LONG_PRESS_MS, clickThenLongPressMs)
        if (comboMemberClickThenLongPressMs != null) {
            p = p.with(GlobalParams.COMBO_MEMBER_CTL_PRESS_MS, comboMemberClickThenLongPressMs)
        }
        if (vibrate != null) p = p.with(GlobalParams.VIBRATE, vibrate)
        if (vibrateStrength != null) p = p.with(GlobalParams.VIBRATE_STRENGTH, vibrateStrength)
        if (vibrateOnNone != null) p = p.with(GlobalParams.VIBRATE_ON_NONE, vibrateOnNone)
        if (toastOnRun != null) p = p.with(GlobalParams.TOAST_ON_RUN, toastOnRun)
        if (toastFluid != null) p = p.with(GlobalParams.TOAST_FLUID, toastFluid)
        if (toastNotify != null) p = p.with(GlobalParams.TOAST_NOTIFY, toastNotify)
        if (stepNotice != null) p = p.with(GlobalParams.STEP_NOTICE, stepNotice)
        if (toastTemplate != null) p = p.with(GlobalParams.TOAST_TEMPLATE, toastTemplate)
        if (language != null) p = p.with(GlobalParams.LANGUAGE, language)
        if (palette != null) p = p.with(GlobalParams.PALETTE, palette)
        if (theme != null) p = p.with(GlobalParams.THEME, theme)
        if (monetColors != null) p = p.with(GlobalParams.MONET_COLORS, monetColors)
        if (pageScale != null) p = p.with(GlobalParams.PAGE_SCALE, pageScale)
        if (iconTintSeed != null) p = p.with(GlobalParams.ICON_TINT_SEED, iconTintSeed)
        if (showCommonCommands != null) p = p.with(GlobalParams.SHOW_COMMON_COMMANDS, showCommonCommands)
        if (reduceIcons != null) p = p.with(GlobalParams.REDUCE_ICONS, reduceIcons)
        if (waitComboSort != null) p = p.with(GlobalParams.WAIT_COMBO_SORT, waitComboSort)
        if (holdComboSort != null) p = p.with(GlobalParams.HOLD_COMBO_SORT, holdComboSort)
        if (colorOsApi != null) p = p.with(GlobalParams.COLOR_OS_API, colorOsApi)
        if (miniWindowAnim != null) p = p.with(GlobalParams.MINI_WINDOW_ANIM, miniWindowAnim)
        if (comboWaitWindowMs != null) p = p.with(GlobalParams.COMBO_WAIT_WINDOW_MS, comboWaitWindowMs)
        if (comboWaitPairMs != null) p = p.with(GlobalParams.COMBO_WAIT_PAIR_MS, comboWaitPairMs)
        if (comboHoldWindowMs != null) p = p.with(GlobalParams.COMBO_HOLD_WINDOW_MS, comboHoldWindowMs)
        if (comboHoldFollowerMs != null) p = p.with(GlobalParams.COMBO_HOLD_FOLLOWER_MS, comboHoldFollowerMs)
        if (comboHoldMinMs != null) p = p.with(GlobalParams.COMBO_HOLD_MIN_MS, comboHoldMinMs)
        if (comboSceneOnStarter != null) p = p.with(GlobalParams.COMBO_SCENE_ON_STARTER, comboSceneOnStarter)
        if (comboStarterKeepLong != null) p = p.with(GlobalParams.COMBO_STARTER_KEEP_LONG, comboStarterKeepLong)
        if (comboFixedWindowStart != null) p = p.with(GlobalParams.COMBO_FIXED_WINDOW_START, comboFixedWindowStart)
        if (comboHoldFixedWindowStart != null) {
            p = p.with(GlobalParams.COMBO_HOLD_FIXED_WINDOW_START, comboHoldFixedWindowStart)
        }
        return GlobalConfig(p, commonCommands ?: this.commonCommands)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is GlobalConfig && params == other.params && commonCommands == other.commonCommands)

    override fun hashCode(): Int = 31 * params.hashCode() + commonCommands.hashCode()

    override fun toString(): String = "GlobalConfig(params=${params.toMap()}, commonCommands=$commonCommands)"

    /** 本对象在每个配置字段上的取值 —— 与 `KeyConfig.fieldValues` 同一套（见那边的说明）。 */
    fun fieldValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_MODULE_ENABLED to enabled,
                ConfigFields.F_VIBRATE to vibrate,
                ConfigFields.F_VIBRATE_STRENGTH to vibrateStrength,
                ConfigFields.F_VIBRATE_ON_NONE to vibrateOnNone,
                ConfigFields.F_TOAST_ON_RUN to toastOnRun,
                ConfigFields.F_TOAST_FLUID to toastFluid,
                ConfigFields.F_TOAST_NOTIFY to toastNotify,
                ConfigFields.F_TOAST_TEMPLATE to toastTemplate,
                ConfigFields.F_STEP_NOTICE to stepNotice,
                ConfigFields.F_LANGUAGE to language,
                ConfigFields.F_PALETTE to palette,
                ConfigFields.F_THEME to theme,
                ConfigFields.F_MONET_COLORS to monetColors,
                ConfigFields.F_COLOR_OS_API to colorOsApi,
                ConfigFields.F_MINI_WINDOW_ANIM to miniWindowAnim,
                ConfigFields.F_PAGE_SCALE to pageScale,
                ConfigFields.F_ICON_TINT_SEED to iconTintSeed,
                ConfigFields.F_SHOW_COMMON_COMMANDS to showCommonCommands,
                ConfigFields.F_REDUCE_ICONS to reduceIcons,
                ConfigFields.F_WAIT_COMBO_SORT to waitComboSort,
                ConfigFields.F_HOLD_COMBO_SORT to holdComboSort,
                ConfigFields.F_GLOBAL_DOUBLE_CLICK_MS to doubleClickMs,
                ConfigFields.F_GLOBAL_WAIT_MULTI_WINDOW to waitMultiWindow,
                ConfigFields.F_GLOBAL_MULTI_WAIT_MS to multiWaitMs,
                ConfigFields.F_GLOBAL_CTL_PRESS_MS to clickThenLongPressMs,
                ConfigFields.F_GLOBAL_COMBO_MEMBER_CTL_PRESS_MS to comboMemberClickThenLongPressMs,
                ConfigFields.F_GLOBAL_UI_VIBRATE to uiVibrate,
                ConfigFields.F_GLOBAL_PREDICTIVE_BACK to predictiveBack,
                ConfigFields.F_GLOBAL_DETAILED_KEY_SETTINGS to detailedKeySettings,
                ConfigFields.F_COMBO_WAIT_WINDOW_MS to comboWaitWindowMs,
                ConfigFields.F_COMBO_WAIT_PAIR_MS to comboWaitPairMs,
                ConfigFields.F_COMBO_HOLD_WINDOW_MS to comboHoldWindowMs,
                ConfigFields.F_COMBO_HOLD_FOLLOWER_MS to comboHoldFollowerMs,
                ConfigFields.F_COMBO_HOLD_MIN_MS to comboHoldMinMs,
                ConfigFields.F_COMBO_SCENE_ON_STARTER to comboSceneOnStarter,
                ConfigFields.F_COMBO_STARTER_KEEP_LONG to comboStarterKeepLong,
                ConfigFields.F_COMBO_FIXED_WINDOW_START to comboFixedWindowStart,
                ConfigFields.F_COMBO_HOLD_FIXED_WINDOW_START to comboHoldFixedWindowStart,
            ),
        )

    /**
     * 四格窗口（等待 / 按住 × 固定窗口起点 开 / 关）。
     *
     *  只回答「这几个数现在是多少」：界面那两根滑杆与 [Config.comboStoredWindowsFor] 都读它，
     *  「独立设置开着看自己那份」的判据仍在 `Config` 那一层。
     *
     *  **有序无序共用这一份**：档位要不要按先后落，与「多久之内落完」是两件事。
     */
    fun windows(): ComboWindows =
        ComboWindows(
            wait = comboWaitWindowMs,
            waitPair = comboWaitPairMs,
            hold = comboHoldWindowMs,
            holdFollower = comboHoldFollowerMs,
        )

    companion object {
        /** [GlobalConfig.enabled] 的默认值：**默认关** —— 装上模块不改变任何行为。 */
        const val DEFAULT_ENABLED = false

        /** [GlobalConfig.toastOnRun] 的默认值：默认不弹。 */
        const val DEFAULT_TOAST_ON_RUN = false

        /**
         * [GlobalConfig.toastFluid] 的默认值：**默认走流体云**。
         *
         *  用户口径「使用 ColorOS 接口时应该弹流体云」—— 而「使用 ColorOS 接口」出厂就是开的，
         *  所以回执的默认落点跟着它走胶囊。真要 Toast 气泡，在设置里把这一行关掉即可；
         *  关掉「使用 ColorOS 接口」则自动回落 Toast（见 [GlobalConfig.toastFluid]）。
         */
        const val DEFAULT_TOAST_FLUID = true

        /** [GlobalConfig.toastNotify] 的默认值：**关**（新装仍然是「不提示」，与之前一致）。 */
        const val DEFAULT_TOAST_NOTIFY = false

        /** [GlobalConfig.stepNotice] 的默认值：**关**（一条命令链只念一句，与之前一致）。 */
        const val DEFAULT_STEP_NOTICE = false

        /**
         * [GlobalConfig.toastTemplate] 的默认值 —— 与拆分之前那句话**逐字一致**，
         * 老配置没有这一项时补的也是它（见 `ToastNotice.DEFAULT_TEMPLATE`）。
         */
        const val DEFAULT_TOAST_TEMPLATE = ToastNotice.DEFAULT_TEMPLATE

        /** [GlobalConfig.vibrateStrength] 的三档取值 —— 弱 / 中 / 强。 */
        const val VIBRATE_WEAK = 0
        const val VIBRATE_MEDIUM = 1
        const val VIBRATE_STRONG = 2

        /** [GlobalConfig.vibrateStrength] 的默认值：中档（与「开振动」原本那一下手感对齐）。 */
        const val DEFAULT_VIBRATE_STRENGTH = VIBRATE_MEDIUM

        /** 「无操作时振动」的默认值：**关**（与「振动反馈」默认关同一条）。 */
        const val DEFAULT_VIBRATE_ON_NONE = false

        /** [GlobalConfig.vibrateStrength] 的取值范围（滑杆两端与钳制共用）。 */
        val VIBRATE_STRENGTH_RANGE = VIBRATE_WEAK..VIBRATE_STRONG

        /** [GlobalConfig.language] 的默认值：跟随系统。 */
        const val DEFAULT_LANGUAGE = "auto"

        /**
         * [GlobalConfig.palette] 的默认值 —— **系统默认**（跟随系统取色，界面上那一档就叫这四个字）。
         *
         *  这里只认「哪一档」，不认识颜色：那一档取什么色由界面层决定（`PagePalette.seed`，
         *  系统强调色取不到时依次退到宿主主题主色、常量蓝）。早先 `default` 是 COUI 自己那套蓝，
         *  现在那个蓝另立一档 `blue`（`PagePalette.BLUE`）。
         *
         *  字面量只写在这里：界面层那枚举的默认档用这个常量当 id，默认档与「没选过」是同一个东西。
         */
        const val DEFAULT_PALETTE = "default"

        /**
         * [GlobalConfig.theme] 的三档 —— 与界面层 `AppTheme` 的 code 一一对应。
         *
         *  只管**深浅**（跟随系统 / 浅色 / 深色）。用不用 Monet 配色是另一格
         *  （[GlobalConfig.monetColors]）：两件事各占一行，深浅与配色就能自由组合。
         *
         *  v90 之前这里是六档（`monet` / `light_monet` / `dark_monet` 三档把搭配写死在一个值里）——
         *  拆分时那三个旧值由 `ConfigDto` 读进来那一刻翻成「三档 + 配色开关」，见那边的注释。
         */
        const val THEME_AUTO = "auto"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        /** [GlobalConfig.theme] 的默认值：跟随系统。 */
        const val DEFAULT_THEME = THEME_AUTO

        /**
         * [GlobalConfig.monetColors] 的默认值：**关**（底子照旧是 COUI 那一套，不改变任何人的现状）。
         *
         *  默认关的另一个理由：Monet 是「系统按壁纸生成一整套色」，没壁纸取色的机器上它退化成
         *  一个灰蓝 —— 主动打开的人自己那一档才有意义。
         */
        const val DEFAULT_MONET_COLORS = false

        /** [GlobalConfig.colorOsApi] 的默认值：默认开启。 */
        const val DEFAULT_COLOR_OS_API = true

        /** 界面缩放的三档：下限 / 出厂值 / 上限（百分比）。 */
        const val PAGE_SCALE_MIN = 70

        /** 随机图标配色：`0` = 关（默认），其余值当种子用。 */
        const val DEFAULT_ICON_TINT_SEED = 0

        /** [GlobalConfig.showCommonCommands] 的默认值 —— 默认露出（装上就看得到）。 */
        const val DEFAULT_SHOW_COMMON_COMMANDS = true

        /** [GlobalConfig.reduceIcons] 的默认值：**关**（图标照旧画，不改变任何人的现状）。 */
        const val DEFAULT_REDUCE_ICONS = false

        /**
         * 组合列表那两列的排序三档 —— 与界面层 `ComboSort` 的 code 一一对应。
         *
         *  **不排序 = 创建先后**（`cfg.combos` 自己的次序），所以它也是默认档：
         *  没进过这一页的配置不该被读出一种「排过」的样子。
         */
        const val SORT_BY_CREATED = "created"
        const val SORT_ORDERED_FIRST = "ordered"
        const val SORT_UNORDERED_FIRST = "unordered"
        const val DEFAULT_COMBO_SORT = SORT_BY_CREATED

        /** [GlobalConfig.detailedKeySettings] 的默认值 —— 默认只显示常用项。 */
        const val DEFAULT_DETAILED_KEY_SETTINGS = false
        const val DEFAULT_PAGE_SCALE = 100
        const val PAGE_SCALE_MAX = 150

        /** [GlobalConfig.miniWindowAnim] 的默认值：默认关闭（原地收、无动画）。 */
        const val DEFAULT_MINI_WINDOW_ANIM = false

        /**
         * 四格窗口的下限 —— **50ms**（与 [KeyConfig.MIN_DOUBLE_CLICK_MS] 同一档）：
         *  八根窗口滑杆（不分顺序四格 + 顺序组合四格）的下限统一，再短就几乎是
         *  「必须精确同时按」，组合键会挑手。
         *
         *  **上限按「固定等 / 逐次等」分成两档**（见下面两个常量）：前者量整串、后者量相邻两颗。
         */
        const val COMBO_WINDOW_MIN_MS = 50

        /**
         * 「**固定等**」那几格（`wait` / `hold` 与顺序组合的同名两格）的上限 —— 1000ms。
         *
         *  这一档是「整串只给一次预算、从第一颗起算」，三颗以上的组合、手慢的人确实需要宽；
         *  而这个数同时管「算不算凑齐」与「第一颗键等多久才回落单击」，所以上限就是它的代价。
         */
        const val COMBO_WINDOW_MAX_MS = 1000

        /**
         * 「**逐次等**」那几格（`waitPair` / `holdFollower` 与顺序组合的同名两格）的上限
         * —— 800ms。
         *
         *  这一档量的是**相邻两颗之间**（按住档则是「后续成员各自往前等」）。两颗键相继落下的
         *  间隔在**三颗组合**里往往比两颗松得多（第三颗最难踩），500ms 实测抓不住。
         *
         *  它会被**乘法放大**：`n` 颗的组合整串跨度 = 本值 × (n-1)。800 之下三颗 = 1600ms，
         *  正好覆盖人手按完三颗的常见区间（600~900ms）还留足余量；同时参与组合的键按下后
         *  最多也只等这么久（`ConfigChannel.holdWindowMsFor`），不会拖成秒级单击延迟。
         */
        const val COMBO_PAIR_MAX_MS = 800

        /**
         * 「**固定等**」那一档的出厂值（固定窗口起点**开**：整串共用一个窗口）—— 350ms。
         *
         *  四格里的 `wait` / `hold` 与顺序组合的同名两格都读它。起点开着时窗口从**第一颗**起算、
         *  整串只给一次预算，用户在那一档表达的是「我打算一口气按完这一串」，出厂给宽一档
         *  才不会让他先按出一串单击再回头调滑杆。
         */
        const val DEFAULT_COMBO_WINDOW_MS = 350

        /**
         * 「**逐次等**」那一档的出厂值（固定窗口起点**关**：相邻两颗各一个窗口 / 每颗各自往前等）
         * —— 250ms。
         *
         *  四格里的 `waitPair` / `holdFollower` 与顺序组合的同名两格都读它。逐次等量的是
         *  **相邻两颗之间**的间隔，比整串那一档窄一档；两档分开存正是为了让人换格之后手感
         *  按自己的预期变（见 `GlobalConfig.comboWaitWindowMs` 那一段）。
         */
        const val DEFAULT_COMBO_PAIR_MS = 250

        /**
         * [GlobalConfig.comboHoldMinMs] 的三档 —— 下限 / 出厂值 / 上限（毫秒）。
         *
         *  出厂 100：实测「一起按」两颗键相差 0~40ms（谁先上报看硬件），而真要按住一颗再补
         *  另一颗，人手一般在 150ms 以上 —— 100 落在中间，两边都不误伤。
         *
         *  下限 **50**（与 [KeyConfig.MIN_DOUBLE_CLICK_MS] 同一档）：再短就已经盖不住「一起按」的抖动
         *  （那正是这一位要分开的那一半）。
         *  上限 500 与组合长按阈值同量级：调得比那颗键自己的长按还宽时，它早就先判出长按、
         *  按住那条本来就作废了 —— 留这么宽只是不拦手改的配置。
         */
        const val COMBO_HOLD_MIN_MIN_MS = 50
        const val DEFAULT_COMBO_HOLD_MIN_MS = 100
        const val COMBO_HOLD_MIN_MAX_MS = 500

        /**
         * [GlobalConfig.comboSceneOnStarter] 的出厂值 —— **开**：按住组合的场景按首键按下那一刻定。
         *
         *  按住组合的起点键要按满 `comboHoldMinMs` 才凑得成，凑成之后还有长按阈值；按「派发那一刻」
         *  判场景会把这段时间里的切换也算进去。首键按下才是用户心里「这一下要干什么」的起点。
         */
        const val DEFAULT_COMBO_SCENE_ON_STARTER = true

        /**
         * [GlobalConfig.comboStarterKeepLong] 的出厂值 —— **关**：与这一位出现之前完全一致。
         *
         *  打开它等于「一次按压执行两个动作」（组合的 + 首键自己的长按），那是用户显式要的
         *  行为，不该默认落到所有人头上。
         */
        const val DEFAULT_COMBO_STARTER_KEEP_LONG = false

        /**
         * [GlobalConfig.comboFixedWindowStart] 的出厂值 —— **关**（逐次等满）：与这一位出现之前
         *  完全一致。开 = 从首个成员按下起一个固定窗口，中途再按成员也不重置计时。
         */
        const val DEFAULT_COMBO_FIXED_WINDOW_START = false

        /**
         * 「常用命令」最多几条 —— 20。
         *
         *  这一张表只放「一眼就想点的那几条」：它在绑定页占一整个网格，条目再多就变成
         *  第二份命令库，与「在这个格子里直接拿到一条能用的命令」这个初衷偏离。
         */
        const val MAX_COMMON_COMMANDS = 20

        //  出厂值全在注册表（`GlobalParams` 的 default）—— 这里不再列一遍。
        val DEFAULT = GlobalConfig()

        /** 从取值表还原全局配置 —— 与 [GlobalConfig.fieldValues] 成对，两条通道共用。 */
        fun ofFields(v: ConfigFields.Values): GlobalConfig =
            GlobalConfig(
                params =
                    Params.EMPTY
                        .with(GlobalParams.ENABLED, v.bool(ConfigFields.F_MODULE_ENABLED))
                        .with(GlobalParams.VIBRATE, v.bool(ConfigFields.F_VIBRATE))
                        .with(GlobalParams.VIBRATE_STRENGTH, v.number(ConfigFields.F_VIBRATE_STRENGTH))
                        .with(GlobalParams.VIBRATE_ON_NONE, v.bool(ConfigFields.F_VIBRATE_ON_NONE))
                        .with(GlobalParams.TOAST_ON_RUN, v.bool(ConfigFields.F_TOAST_ON_RUN))
                        .with(GlobalParams.TOAST_FLUID, v.bool(ConfigFields.F_TOAST_FLUID))
                        .with(GlobalParams.TOAST_NOTIFY, v.bool(ConfigFields.F_TOAST_NOTIFY))
                        .with(GlobalParams.TOAST_TEMPLATE, v.text(ConfigFields.F_TOAST_TEMPLATE))
                        .with(GlobalParams.STEP_NOTICE, v.bool(ConfigFields.F_STEP_NOTICE))
                        .with(GlobalParams.LANGUAGE, v.text(ConfigFields.F_LANGUAGE))
                        .with(GlobalParams.PALETTE, v.text(ConfigFields.F_PALETTE))
                        .with(GlobalParams.THEME, v.text(ConfigFields.F_THEME))
                        .with(GlobalParams.MONET_COLORS, v.bool(ConfigFields.F_MONET_COLORS))
                        .with(GlobalParams.COLOR_OS_API, v.bool(ConfigFields.F_COLOR_OS_API))
                        .with(GlobalParams.MINI_WINDOW_ANIM, v.bool(ConfigFields.F_MINI_WINDOW_ANIM))
                        .with(GlobalParams.PAGE_SCALE, v.number(ConfigFields.F_PAGE_SCALE))
                        .with(GlobalParams.ICON_TINT_SEED, v.number(ConfigFields.F_ICON_TINT_SEED))
                        .with(GlobalParams.SHOW_COMMON_COMMANDS, v.bool(ConfigFields.F_SHOW_COMMON_COMMANDS))
                        .with(GlobalParams.REDUCE_ICONS, v.bool(ConfigFields.F_REDUCE_ICONS))
                        .with(GlobalParams.WAIT_COMBO_SORT, v.text(ConfigFields.F_WAIT_COMBO_SORT))
                        .with(GlobalParams.HOLD_COMBO_SORT, v.text(ConfigFields.F_HOLD_COMBO_SORT))
                        .with(GlobalParams.DOUBLE_CLICK_MS, v.number(ConfigFields.F_GLOBAL_DOUBLE_CLICK_MS))
                        .with(GlobalParams.WAIT_MULTI_WINDOW, v.bool(ConfigFields.F_GLOBAL_WAIT_MULTI_WINDOW))
                        .with(GlobalParams.MULTI_WAIT_MS, v.number(ConfigFields.F_GLOBAL_MULTI_WAIT_MS))
                        .with(GlobalParams.CLICK_THEN_LONG_PRESS_MS, v.number(ConfigFields.F_GLOBAL_CTL_PRESS_MS))
                        .with(
                            GlobalParams.COMBO_MEMBER_CTL_PRESS_MS,
                            v.number(ConfigFields.F_GLOBAL_COMBO_MEMBER_CTL_PRESS_MS),
                        ).with(GlobalParams.UI_VIBRATE, v.bool(ConfigFields.F_GLOBAL_UI_VIBRATE))
                        .with(GlobalParams.PREDICTIVE_BACK, v.bool(ConfigFields.F_GLOBAL_PREDICTIVE_BACK))
                        .with(GlobalParams.DETAILED_KEY_SETTINGS, v.bool(ConfigFields.F_GLOBAL_DETAILED_KEY_SETTINGS))
                        .with(GlobalParams.COMBO_WAIT_WINDOW_MS, v.number(ConfigFields.F_COMBO_WAIT_WINDOW_MS))
                        .with(GlobalParams.COMBO_WAIT_PAIR_MS, v.number(ConfigFields.F_COMBO_WAIT_PAIR_MS))
                        .with(GlobalParams.COMBO_HOLD_WINDOW_MS, v.number(ConfigFields.F_COMBO_HOLD_WINDOW_MS))
                        .with(GlobalParams.COMBO_HOLD_FOLLOWER_MS, v.number(ConfigFields.F_COMBO_HOLD_FOLLOWER_MS))
                        .with(GlobalParams.COMBO_HOLD_MIN_MS, v.number(ConfigFields.F_COMBO_HOLD_MIN_MS))
                        .with(GlobalParams.COMBO_SCENE_ON_STARTER, v.bool(ConfigFields.F_COMBO_SCENE_ON_STARTER))
                        .with(GlobalParams.COMBO_STARTER_KEEP_LONG, v.bool(ConfigFields.F_COMBO_STARTER_KEEP_LONG))
                        .with(
                            GlobalParams.COMBO_FIXED_WINDOW_START,
                            v.bool(ConfigFields.F_COMBO_FIXED_WINDOW_START),
                        ).with(
                            GlobalParams.COMBO_HOLD_FIXED_WINDOW_START,
                            v.bool(ConfigFields.F_COMBO_HOLD_FIXED_WINDOW_START),
                        ),
            )
    }
}

/**
 * 整份配置。
 *
 *  函数数贴着 detekt 的 `TooManyFunctions` 阈值：多出来的那一族是**「公共项的按键级取值」**
 *  （`effectiveFor` / `comboWindowsFor`）—— 它们就该长在这一层：判据只有一句
 *  「这一档开了独立设置就看自己那份，否则看公共设置」，拆到别处等于让每个调用方自己拼，
 *  而拼错一处的表现是「这台机器上这颗键不听话」。
 */
data class Config(
    val keys: Map<KeyId, KeyConfig>,
    /**
     * 用户收集来的按键（顺序 = 收集顺序）。
     *
     *  **内置键不在这里** —— 内置与自定义的全集是 [allKeys]。把内置键也塞进来会让
     *  「哪些是用户的东西」和「哪些是编译期就有的」混成一锅，删除 / 改名那些判据就得再各写一遍。
     *
     *  自定义键的**身份**是 `KeyId.id`（`ck1` `ck2`），键码只是它的一个属性：换一次驱动上报
     *  换了个码，绑定不该跟着丢（见 [KeyId.custom]）。
     */
    val customKeys: List<KeyId> = emptyList(),
    val global: GlobalConfig,
    /**
     * 用户是否已经确认过「风险提示与免责声明」—— 只由**本地**产生，决定「启用」之前要不要弹那一次。
     *
     * 存在配置里（而不是界面状态里）是**有意**的：
     *  · 清空配置按 [DEFAULT] 重建 ⇒ 这一位跟着没了 ⇒ 下次启用照样弹（「清空之后同样弹」就是这么来的）；
     *  · 它是**本机这一次的接受行为**，所以导出／导入一律把它丢掉（见 `ConfigBundle`）——
     *    一份文件不该替别人说「我已经接受了风险」。
     */
    val disclaimerAccepted: Boolean = false,
    /**
     * 用户是否已经确认过「自定义命令」那一页的风险提示 —— 与 [disclaimerAccepted] **分开记**。
     *
     *  两者说的是两件事：那一份讲的是「注入系统进程」这件事本身，这一份讲的是
     *  「自定义命令**不做任何检验或保护**，写什么就跑什么」。在首页确认过前者，不等于知道后者，
     *  所以不能共用一位。
     *
     *  同样存在配置里（理由与 [disclaimerAccepted] 一样）：清空配置按 [DEFAULT] 重建
     *  ⇒ 这一位跟着没了 ⇒ 下次进那一页照样弹；导出／导入也一律丢掉（见 `ConfigBundle`）。
     */
    val commandDisclaimerAccepted: Boolean = false,
    /**
     * 「打开应用」那一族的**显示名**：键是绑定的**身份**（`AppTarget.identity` —— `999@包名/类名`、
     * `999@包名/快捷方式id`），值是界面上那个名字（`微信 2` / `扫一扫`）。
     *
     * ## 为什么是单独一张表、不再塞进 token
     *
     *  早先名字是跟在目标后面写进 token 的（`app:999@包名/类名|微信 2`），每个读它的地方都得先
     *  切一刀 —— 漏切一处就退化成包名（真机：「选完快捷方式 / 界面，再进来显示的是包名」）。
     *  名字不是目标的一部分，是**显示信息**，所以单独存。
     *
     * ## 为什么不放进 `ConfigFields` 的字段表
     *
     *  那张表是**扁平**的（一个字段一个值），而且两条通道共用它 —— 这张表是映射，又只有界面
     *  读（执行侧只需要「起哪个组件」），进字段表就等于每次按键都把整张表广播给 `system_server`。
     *  所以它只走落盘这一条路：JSON 里单独一段（`ConfigDto.appNames`，键名 `app_names`），
     *  `ConfigCodec` 单独读写。注入侧读到的 `Config` 里这一项恒为空，没人读它。
     *
     *  与应用改名无关：名字只是显示，身份是 `AppTarget.identity`。改过名之后旧名字还在表里，
     *  界面优先用「这次清单里的新名字」（见 `ui/...` 的名字优先级），表只是冷启动时的兜底。
     */
    val appNames: Map<String, String> = emptyMap(),
    /**
     * **组合按键**（顺序 = 界面上的顺序，也是成员数相同时的优先级）。
     *
     *  与 `customKeys` / [appNames] 一样**只走落盘这一列**：它是一张列表，进不了
     *  [ConfigFields] 那种扁平字段表。判定侧的接线是**另一条广播**（`core/wire/ComboWire`）——
     *  那边逐档带成员、动作与长按那几档参数，注入侧据此自己组 `ComboKey`；
     *  也就是说这里的 `combos` 不进 `Config` 的扁平广播，不代表判定侧拿不到。
     */
    val combos: List<ComboKey> = emptyList(),
    /**
     * 这份配置的**名字** —— 多配置切换用。`""` = 「默认」那一份。
     *
     *  默认那份**不许改名、不许删除**（见 `core/config/ConfigProfiles`）：它是升级时自动落下的
     *  那一份，删掉就没地方回去了。其余配置必须起名字。
     *
     *  随落盘走（`ConfigDto.profile_name`），所以导出一份配置时名字跟着出去 ——
     *  导到另一台机器上它还叫原来的名字。
     */
    val profileName: String = "",
    /**
     * 这份配置的**唯一标识** —— 与名字、槽号都无关。
     *
     *  名字是**给人看的**、可以被改成任意字面量（甚至被抹成空），槽号会随着「切换 = 两份内容互换」
     *  在文件之间搬家：拿它们当身份，一旦名字丢了就会出现「两份都叫默认配置、谁都改不了名」
     *  这种死局（真机踩过）。所以身份单独一位：
     *
     *  · 「默认」那一份固定是 `ConfigProfiles.DEFAULT_PROFILE_ID`；
     *  · 其余每一份在新建 / 导入时分配一个随机 id，此后**跟着内容走**（与 [profileName] 同一条）。
     *
     *  空串 = 老文件（这个字段之前没有）—— 读的时候由 `ConfigProfiles` 按「非当前槽优先」补一个，
     *  下一次写盘自然带上。
     */
    val profileId: String = "",
    /**
     * 这份配置在**多配置列表里的位次**（0 = 「默认」那份，1、2、3… 按创建顺序）。
     *
     *  用它而不是槽号排序：切换配置是**两份内容互换**，内容会在槽之间搬家 —— 按槽号排的话，
     *  切一次某个配置就从第 3 行跳到第 1 行。位次跟着**内容**走（随落盘），所以列表顺序始终不变。
     *
     *  0 固定留给「默认」配置：它永远排在最上面。新建 / 导入时取「现有最大值 + 1」。
     */
    val profileOrder: Int = 0,
) {
    fun key(id: KeyId): KeyConfig = keys[id] ?: KeyConfig.defaultFor(id)

    /**
     * **这颗键现在生效吗** —— 两级相与：[GlobalConfig.enabled]（模块总闸）与 [KeyConfig.enabled]
     * （这一颗键自己的开关）。
     *
     *  这是**唯一判据**：注入侧的认领（`ConfigChannel.enabledOf`）与宿主侧的执行 / 松手
     *  （`GestureExec` 的那几处闸门）都调它，
     *  不许再就地写「`global.enabled && key(...).enabled`」—— 那一套曾经在四个地方各写一遍，
     *  漏改一处就是「某个路径上停用的键还在吞键」，而编译期与 CI 都看不见。
     *
     *  **宿主侧的「场景派发」不在其列**（`SideKeyGestureHooks`）：那条消息不带键的身份，
     *  它要回答的是「这一下键到底归不归模块」，判据是宿主侧的 `PressTrace`（刚有没有认领过），
     *  下到某一颗键就会拿 A 键的开关决定 B 键那次派发的去向。
     *
     *  界面侧要的往往只是**一半**：首页总开关那行看 `global.enabled`，⋮ 菜单那行看
     *  `key(id).enabled` —— 那些地方继续各取一半，别拿这个方法去替代（会让总闸锁死自己）。
     */
    fun isLive(id: KeyId): Boolean = global.enabled && key(id).enabled

    /**
     * 公共项的**按键级取值** —— 这一档开了「独立设置」就看自己那份，否则看公共设置。
     *
     *  返回的是**已经解析好的那一份**（`OwnSettings` 本体），调用点直接读字段：
     *  `config.effectiveFor(keyConfig).vibrate` / `.toastOnRun` / `.toastTemplate` …
     *  原先这里是五个 `xxxFor(owner)`，五份逐字同构的 `if (owner.useOwnSettings) owner.X else global.X`
     *  —— 规则只有一条，却要每个调用点记住「该问哪个方法、把谁当 owner 传」，改一条得改五处。
     *
     *  参数是 [OwnSettings] 而不是某个具体类型：单键与组合键在这一件事上**没有区别**，
     *  执行侧传谁进来都一样，不该有「组合那条走全局」这种分支 —— 那种分支正是把组合键
     *  当成独立存在的来源。
     */
    fun effectiveFor(owner: OwnSettings): OwnSettings = if (owner.useOwnSettings) owner else global

    /**
     * 组合键上那几位「这一档自己写过没有」的公共项 —— 唯一一处判据。
     *
     *  总开关一关就整份看公共（哪怕写过）—— 否则界面灰着、执行侧却照旧用自己那份，
     *  是「关了没关掉」。开着时：写过就用写的那个，**没写过取出厂默认**（[default]），
     *  **不抄公共值** —— 独立设置是「这一档自己一份」，不是「公共值的一份快照」
     *  （口径见 `docs/changelog/2026-11-17-独立设置去掉播种.md`）。
     *
     *  现在只剩这几颗「没写过 ⇒ 出厂值」的开关用它：`windowOn` / `holdOn` 那一对已经收进
     *  [ComboKey.mode]（触发方式是这一档的身份），不再有「跟随公共」这回事。
     *
     * @param global 总开关关着时用的那个数（公共设置现值）
     * @param default 总开关开着、这一档又没写过时用的那个数（出厂默认）
     */
    private fun comboField(
        combo: ComboKey,
        own: Boolean?,
        global: Boolean,
        default: Boolean,
    ): Boolean = if (combo.useOwnSettings) own ?: default else global

    /**
     * 「独立设置开着看自己那份、关着看公共」—— **全项目只有这一处实现**。
     *
     *  组合那几位自有字段（`windowMs` / `holdMinMs` / `sceneOnStarter`）都按它取值。原先每项各写一遍
     *  `if (!combo.useOwnSettings) global.X else combo.X`：规则一样、改一处要改五处。
     *
     *  **自己那份的回落值不由它决定**：交给调用方（现在一律是出厂默认）。
     */
    private fun <T> effective(
        useOwnSettings: Boolean,
        global: T,
        own: T,
    ): T = if (useOwnSettings) own else global

    /**
     * 这一档自己**存着**的那一份 —— 没写过（`null` / `0`）就退回 [fallback]。
     *
     *  [fallback] 现在是**出厂默认**，不是公共现值：独立设置开着却一格没写过时，
     *  看到 / 用到的就是出厂那一份（口径见 `docs/changelog/2026-11-17-独立设置去掉播种.md`）。
     *
     * 与 [effective] 的分工：这一半只管「写过没有」，不看总开关；界面上那几根滑杆要的正是它
     * （理由见 [comboStoredWindowsFor]）。
     */
    private fun <T : Any> stored(
        own: T?,
        fallback: T,
    ): T = own ?: fallback

    /**
     * 这一组**四个窗口**（等待 / 按住 × 固定窗口起点 开 / 关）—— **实际生效**的那一份：
     * 独立设置关着整份看公共，开着就是这一组自己那份（没写过的那几格 = 出厂默认）。
     *
     *  **有序无序共用这一份**：档位要不要按配置的先后落，与「多久之内落完」是两件事 ——
     *  所以窗口只有一套，`ComboKey.ordered` 只影响判定收不收乱序。取值只走这里 ——
     *  判定侧（`inject/`）与界面手上都只有这份 Config，两处各拼一次的话，
     *  「关了独立设置却不生效」这种错只会出现在其中一边。
     *
     *  四格分开存（见 `GlobalConfig.comboWaitWindowMs` 那一段）：一个数同时管两种按法的两种时序，
     *  会把四件不同的事压成一件。
     */
    fun comboWindowsFor(combo: ComboKey): ComboWindows =
        effective(
            combo.useOwnSettings,
            global.windows(),
            comboStoredWindowsFor(combo),
        )

    /**
     * 这一组**自己存着**的四格窗口 —— 与 [comboWindowsFor] 的差别只有一处：**不看总开关**。
     *
     *  界面上那几根滑杆要的正是这一份：它显示的得是「这一组自己的数」，否则拨一下总开关
     *  它就跟着换一份，看着像刚调好的值被改了（与单键那张卡里振动强度那一行同一条口径）。
     *  `0`（`WINDOW_FOLLOW_GLOBAL`）只剩老配置会读到 —— 现在退回的是**出厂那一份**
     *  （[ComboWindows.DEFAULT]），不是公共现值。
     *
     *  **这一份就是界面那两行管的那四格**（有序无序共用，见 [comboWindowsFor]）。
     *
     *  **总开关关着时，界面拿到的不是「此刻生效的数」**：它显示的是「这一档一旦打开会用的数」
     *  （没写过 ⇒ 出厂默认）。当前生效值是 [comboWindowsFor] 那一份 —— 两者只在总开关开着时相等。
     *  这一页的滑杆在总开关关着时本来就灰着、还挂一句「需先开启 使用独立设置」，所以显示
     *  「打开之后会怎样」比显示「现在实际怎样」更贴近那一下要拨的东西。
     */
    fun comboStoredWindowsFor(combo: ComboKey): ComboWindows {
        val d = ComboWindows.DEFAULT
        return ComboWindows(
            wait = windowClamped(stored(combo.waitWindowMs.takeIf { it > 0 }, d.wait), pair = false),
            waitPair = windowClamped(stored(combo.waitPairMs.takeIf { it > 0 }, d.waitPair), pair = true),
            hold = windowClamped(stored(combo.holdWindowMs.takeIf { it > 0 }, d.hold), pair = false),
            holdFollower = windowClamped(stored(combo.holdFollowerMs.takeIf { it > 0 }, d.holdFollower), pair = true),
        )
    }

    /**
     * 某一格读回来时按**那一格自己的上限**钳一次（固定等 1000 / 逐次等 800）。
     *
     *  公共那八格有 `GlobalConfig.ofFields` 钳，某一档自己存的那几格没有（它们在 `ComboKey`
     *  上、随 `ComboWire` 落地）—— 老配置里手改过的超限值不钳的话，滑杆会拿到一个超出自己
     *  右端的值，判定侧也会按那个数判。
     */
    private fun windowClamped(
        ms: Int,
        pair: Boolean,
    ): Int =
        ms.coerceIn(
            GlobalConfig.COMBO_WINDOW_MIN_MS,
            if (pair) GlobalConfig.COMBO_PAIR_MAX_MS else GlobalConfig.COMBO_WINDOW_MAX_MS,
        )

    /**
     * 按住这一档的**场景在什么时候定下来** —— 首键按下（`true`）还是成员键按下（`false`）。
     *
     *  与 [comboWindowsFor] 同一条规则：独立设置关着就跟随公共，开着就是这一档自己那份；
     *  `null` = 这一档还没写过 ⇒ 取**出厂默认**（不抄公共现值）。
     *  两种时机都只在**按住**那两档上有意义（等窗口那一档没有「先按住哪一颗」，
     *  判定侧也不会为首键发那条通知）。
     */
    fun comboSceneOnStarterFor(combo: ComboKey): Boolean =
        comboField(
            combo,
            combo.sceneOnStarter,
            global.comboSceneOnStarter,
            GlobalConfig.DEFAULT_COMBO_SCENE_ON_STARTER,
        )

    /**
     * 这一组开了「**首键松手后仍执行它自己的长按**」吗 —— 规则与 [comboSceneOnStarterFor] 同一条。
     *
     *  判定侧（`ComboDetector` 的 `starterKeepLong`）与界面都只走这一处。
     */
    fun comboStarterKeepLongFor(combo: ComboKey): Boolean =
        comboField(
            combo,
            combo.starterKeepLong,
            global.comboStarterKeepLong,
            GlobalConfig.DEFAULT_COMBO_STARTER_KEEP_LONG,
        )

    /**
     * 这一组**等待组合**的固定窗口起点 —— 规则与 [comboSceneOnStarterFor] 同一条。
     *
     *  `true` = 从首个成员按下起一个固定窗口；`false` = 每个成员按下都清掉计时重新等。
     *
     *  **与 [comboHoldFixedWindowStartFor] 是两个变量**：两张卡各管一种按法。
     */
    fun comboFixedWindowStartFor(combo: ComboKey): Boolean =
        comboField(
            combo,
            combo.fixedWindowStart,
            global.comboFixedWindowStart,
            GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START,
        )

    /**
     * 这一组**按住组合**的固定窗口起点 —— 与 [comboFixedWindowStartFor] 同一条规则、**另一个变量**。
     *
     *  `true` = 后续成员要在首个成员按下后的一个窗口内落完（用 `hold` 那一格）；
     *  `false` = 后续成员每颗各自往前等一格（用 `holdFollower` 那一格）。
     *
     *  判定侧只有一处读它：`ComboDetector` 的 `fixedWindowStartFor`。
     */
    fun comboHoldFixedWindowStartFor(combo: ComboKey): Boolean =
        comboField(
            combo,
            combo.holdFixedWindowStart,
            global.comboHoldFixedWindowStart,
            GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START,
        )

    /**
     * 这一档**这一刻**该按哪一颗固定窗口开关取格 —— 判定侧（`ComboDetector` 的
     * `holdFixedWindowStartFor` 与 `comboSpanFor`）只认这一支，不再分有序无序（窗口只有一套）。
     */
    fun comboWindowStartFor(combo: ComboKey): Boolean = comboFixedWindowStartFor(combo)

    /** 按住档的同一位 —— 与 [comboWindowStartFor] 同一条规则，落的是按住那一对开关。 */
    fun comboHoldWindowStartFor(combo: ComboKey): Boolean = comboHoldFixedWindowStartFor(combo)

    /**
     * 这一档的**成员连击参数** —— 按住首键、那颗唯一成员键连击时，判定侧（`GestureDetector`）
     * 按这一份判。
     *
     *  只在 `mode == MODE_HOLD` 且 `followers.size == 1` 时有意义；多成员组合上连击没有判据，
     *  调用方不该问它（判定侧在 [ComboDetector] 里先按这一条拦掉）。
     *
     *  ## 窗口与判定时机取**按住组合那张卡**那两行（用户口径）
     *
     *  连击节奏就是「这颗成员键两击之间最多隔多久」，不再另开一套滑块：
     *  窗口取 [comboWindowsFor] 里按住那一半（[comboHoldWindowStartFor] 开 = `hold`、
     *  关 = `holdFollower`），判定时机取那颗「固定窗口起点」本身。
     *
     *  `ClickChainParams.waitMultiWindow` 是那颗开关的**反面**（见 `KeyConfig.waitMultiWindow`：
     *  false = 固定窗口起点、true = 每次松手重新起算），所以这里传 `!fixedStart`。
     *
     *  于是那两行改一次，成员之间接收与成员连击一起变 —— 不要再在这里另读单击那三项，
     *  两套数并存时表现就是「按住卡上那根滑杆拨了，连击手感一点不变」。
     */
    fun comboMemberChain(combo: ComboKey): ClickChainParams {
        val windows = comboWindowsFor(combo)
        val fixedStart = comboHoldWindowStartFor(combo)
        return MemberChainView(
            combo,
            clickWindowMs = if (fixedStart) windows.hold else windows.holdFollower,
            //  `waitMultiWindow` 是界面上那颗「固定窗口起点」的反面
            waitMultiWindow = !fixedStart,
        )
    }

    /**
     * 这一组**自己存着**的「首键要先按住多久」（毫秒）—— 与 [comboStoredWindowsFor] 同一条：
     * 不看总开关，滑杆要的就是这一份；没写过 ⇒ 出厂默认。
     */
    fun comboStoredHoldMinFor(combo: ComboKey): Int = stored(combo.holdMinMs.takeIf { it > 0 }, GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS)

    /**
     * 这一组**实际生效**的「首键要先按住多久」（毫秒）—— 规则与 [comboWindowsFor] 同一条：
     * 独立设置关着就跟随公共，开着就是这一组自己那份。
     *
     *  判定侧（`ComboDetector` 的 `holdMinFor`）与界面都只走这一处。
     */
    fun comboHoldMinFor(combo: ComboKey): Int = effective(combo.useOwnSettings, global.comboHoldMinMs, comboStoredHoldMinFor(combo))

    /**
     * 这一组**自己存着**的「成员连击后长按延迟」（毫秒）—— 与 [comboStoredHoldMinFor] 同一条：
     * 不看总开关，滑杆要的就是这一份；没写过（[ComboKey.MEMBER_CTL_PRESS_MS_FOLLOW_GLOBAL]）
     * ⇒ 出厂默认。
     */
    fun comboStoredMemberThenLongPressFor(combo: ComboKey): Int =
        stored(
            combo.memberClickThenLongPressMs.takeIf { it > ComboKey.MEMBER_CTL_PRESS_MS_FOLLOW_GLOBAL },
            KeyConfig.DEFAULT_LONG_PRESS_MS,
        )

    /**
     * 这一组**实际生效**的「成员连击后长按延迟」（毫秒）—— 与 [comboHoldMinFor] 同一条：
     * 独立设置关着就跟随公共，开着就是这一组自己那份。
     *
     *  界面（那一根滑杆的取值）与广播（`ConfigBroadcast` 把它盖进组合那份副本）都只走这一处；
     *  判定侧读的是广播里那一格（`ComboWire`），不自己再解析一遍。
     */
    fun comboMemberThenLongPressFor(combo: ComboKey): Int =
        effective(
            combo.useOwnSettings,
            global.comboMemberClickThenLongPressMs,
            comboStoredMemberThenLongPressFor(combo),
        )

    /**
     * 这一档**整串**的容忍跨度（毫秒）—— **按这一档自己的触发方式取格**。
     *
     *  · 等待档：起点开着用 `wait`，关着用 `waitPair × (n-1)`；
     *  · 按住档：起点开着用 `hold`，关着用 `holdFollower × (n-1)`。
     *
     *  **顺序组合另取一套**：窗口来自 [comboWindowsFor]（它按 `ordered` 挑那一组），开关来自
     *  [comboWindowStartFor] / [comboHoldWindowStartFor]（同上）。于是这一支不必自己分叉 ——
     *  判定侧、扫候选、界面三处问到的都是「这一档此刻真正的跨度」。
     *
     *  早先这一支不分档、两种按法都读等待那两格与 `comboFixedWindowStartFor` —— 于是把
     *  「按住组合的成员接收」调宽（独立设置里那一档的 `holdFollower`）之后，[comboScanWindowMs]
     *  仍按等待档的窗口算，第三颗落下时第一颗已经出了扫描窗，那一档**永远凑不齐**。
     *  两种按法的窗口既然分成四格，跨度也必须分档取。
     *
     *  按住档的**首键**不吃窗口（按多久都行，`ComboDetector.multiFor` 靠 `down` 把它收进候选），
     *  所以这里的跨度管的是「后续成员之间」—— 取两格里的宽者，宁宽勿窄。
     *
     *  判定（`ComboDetector.multiFor` 与它的镜像 `ComboSequence`）、扫候选（[comboScanWindowMs]）、
     *  「按下先等多久」（`ConfigChannel.holdWindows`，只算等待档）几处都读这一支 ——
     *  少改一处就是「扫得到、等不到」或反过来。
     */
    fun comboSpanFor(combo: ComboKey): Int {
        val w = comboWindowsFor(combo)
        val gaps = (combo.keys.size - 1).coerceIn(1, ComboKey.MAX_JUDGE_KEYS - 1)
        if (combo.isHold) {
            return if (comboHoldWindowStartFor(combo)) {
                maxOf(w.hold, w.holdFollower)
            } else {
                w.holdFollower * gaps
            }
        }
        if (comboWindowStartFor(combo)) return w.wait
        return w.waitPair * gaps
    }

    /**
     * 判定侧**扫描**用的窗口：这份配置里最长的那个跨度（至少是公共那几格）。
     *
     *  按下当前这一颗键时还不知道会凑成哪一组，所以扫候选必须按**最宽**的那一档来扫，
     *  命中之后再拿那一组自己的窗口复核（见 `ComboDetector`）—— 否则设了短窗口的组会被
     *  设了长窗口的组抢走配对，或者反过来永远配不上。
     *
     *  取的是每档的**整串跨度**（[comboSpanFor]）而不是单个窗口：逐次等满那一档的第三颗按下时，
     *  第一颗已经隔了两个窗口 —— 按一个窗口扫就永远看不到它。
     *
     *  只算 [liveCombos]：凑不齐的那一档按再宽的窗口扫也永远命中不了，让它参与取最大值
     *  只会把这一段扫描时长白撑出去。
     */
    val comboScanWindowMs: Int
        get() =
            maxOf(
                global.comboWaitWindowMs,
                global.comboWaitPairMs,
                global.comboHoldWindowMs,
                global.comboHoldFollowerMs,
                liveCombos().maxOfOrNull { comboSpanFor(it) } ?: 0,
            )

    /** 某个目标该显示的名字（按 [AppTarget.identity] 查）；没记过返回 `null`。 */
    fun appName(target: String): String? = appNames[AppTarget.identity(target)]

    /**
     * 记下（或覆盖）一个目标的名字；[name] 为空时**删掉**这一条。
     *
     *  写成「返回新对象」而不是改字段：配置是 `data class`、写盘走 [copy]，改字段那一套在这里
     *  没有先例（`write` 那一层按整份 `Config` 落盘并发广播）。
     */
    fun withAppName(
        target: String,
        name: String?,
    ): Config {
        val id = AppTarget.identity(target)
        return when {
            name.isNullOrBlank() -> if (id in appNames) copy(appNames = appNames - id) else this
            appNames[id] == name -> this
            else -> copy(appNames = appNames + (id to name))
        }
    }

    /**
     * 内置 + 自定义的**全集**（界面列键清单、广播打包都按它走）。
     *
     *  `by lazy`：注入侧每条按键事件都要按码查一次键（`keyForCode` → `allKeys`），
     *  每次现拼一个列表就是每按一次键多两三个分配 —— 而这跑在 `InputReader` 线程上。
     *  配置是不可变的（写配置走 `copy`），所以缓存一次就够。
     */
    val allKeys: List<KeyId> by lazy { KeyId.builtins + customKeys }

    /** 按 id 找键本身（要拿 `label` / `isCustom` 时用）；不认识返回 null。 */
    fun keyIdOf(id: String): KeyId? = allKeys.firstOrNull { it.id == id }

    /** 按 id 找那个键的配置（自定义键也认）；不认识返回 null。 */
    fun keyForId(id: String): KeyConfig? = keyIdOf(id)?.let { key(it) }

    /**
     * 键码 → 那一颗键的配置 —— [keyForCode] 的**索引**，建一次。
     *
     *  [allKeys] 那次 `lazy` 省掉的是「每次按键现拼一个列表」，剩下的一半仍在每次按键上：
     *  **线性扫一遍** `allKeys`、每颗键再 `List.contains` 一次自己的码表。而这条路径收的是
     *  系统里的**每一个**按键（不认识的随即放行），一次按键扫两遍（`keyForCode` 与
     *  [keyForRawCode] 各一遍）。建成表之后每次按键只剩一次 `HashMap` 查找。
     *
     *  重复码取**先出现**的那一颗（与原先 `firstOrNull` 的结果逐字一致）：用 `putIfAbsent`。
     *  `lazy` 默认带锁 —— 建表可能发生在 `InputReader` 线程上，别换成 `NONE`。
     */
    private val keysByCode: Map<Int, KeyConfig> by lazy {
        val map = HashMap<Int, KeyConfig>(allKeys.size * 2)
        for (k in allKeys) {
            val cfg = key(k)
            for (code in k.acceptedKeyCodes) map.putIfAbsent(code, cfg)
        }
        map
    }

    /** 同上，按**原始键码**建表 —— [keyForRawCode] 那条路。 */
    private val keysByRawCode: Map<Int, KeyConfig> by lazy {
        val map = HashMap<Int, KeyConfig>(allKeys.size * 2)
        for (k in allKeys) map.putIfAbsent(k.keyCode, key(k))
        map
    }

    /**
     * 按 keyCode 找那个键的配置 —— **认领路径的唯一入口**。
     *
     *  注入侧不再问「这是不是侧键」，改问「这个码在配置里是不是某一颗键」。查不到就放行：
     *  电源 / 音量那些必须原样交给系统，这一点没变。
     */
    fun keyForCode(code: Int): KeyConfig? = keysByCode[code]

    /**
     * 按**原始键码**找键 —— 与 [keyForCode] 的区别是**不含** ColorOS 的内部注入码。
     *
     *  PM（`PhoneWindowManager`）那条兜底路径能收到所有按键，策略类那条已经处理过 780 / 781 / 782，
     *  这里再认一次会让同一次按压被判两遍（策略类那条路径的说明见 `inject/SideKeyInterceptor`）。
     */
    fun keyForRawCode(code: Int): KeyConfig? = keysByRawCode[code]

    /**
     * 当前按着 [down] 这几颗键（`KeyId.id`）时，该由哪一档组合键接管 —— 没有就是 `null`。
     *
     *  **成员多的优先**：`{侧键, 音量上}` 与 `{侧键, 音量上, 音量下}` 同时成立时，用户按了三颗
     *  显然是冲更具体的那一档去的。成员数一样时取 [combos] 里靠前的那一档 —— 与界面上谁排在
     *  前面是同一个口径（`maxByOrNull` 保留第一个最大值）。
     *
     *  判据只看**集合包含**（[ComboKey.matches]），不看按下的先后：两颗键「同时按」时上报顺序
     *  由驱动决定，拿它当判据等于让配置依赖硬件细节。
     *
     *  成员里可能有**已经删掉的键**（自定义键删了、组合键还指着它）：那一档这辈子凑不齐，等于
     *  停用。不在读取时清理，是因为「删键」与「改组合」是两条独立的路，清理要跨两处同步 ——
     *  少一个成员反而会把用户写的「三键组合」悄悄降成两键。
     *
     *  ## 判据与 [comboOff] 的差别（**不是同一份**，别照那句话改）
     *
     *  这一处只问 [ComboKey.isUsable]（自己没关、成员够数）。**「成员键还都接管着」那一半不在
     *  这里判**：它由调用方按手上的真值补 —— 判定侧是 `inject/ConfigChannel.comboFor`
     *  （`ownedKeyIds`：任一颗不归模块管就整组不成立），界面 / 计划那一侧走 [liveCombos]
     *  （= [comboOff] 为假）。两处合起来才等于 [liveCombos]，这里单独用会把「成员被停用」的
     *  一档也选出来。
     *
     *  这里是「底层接口」那一半：判定侧（`inject/input`）判出「这几颗键一起按着」之后调它取档位，
     *  再按短按 / 长按取 [ComboKey.actionOf]。接线不在此刻。
     */
    fun comboFor(down: Set<String>): ComboKey? = combosFor(down).firstOrNull()

    /**
     * [down] 这几颗键能凑成的**全部**档位 —— 成员多的在前，成员数相同时保持配置顺序。
     *
     *  为什么是列表而不是「最大那一档」：同一组键可以有**几档、各自一种触发方式**
     *  （等窗口 / 先按住第一颗 / 先按住第二颗，见 [ComboKey.mode]）—— 那是三种不同的判据，
     *  得逐档问「这一次按压符不符合你」，不能只看成员数就把别的档排除掉。
     *  判定侧（`ComboDetector.pairFor`）按这个顺序逐档试，按住那几档优先（前提更严）。
     *
     *  判据与 [comboFor] 完全一样（只问 [ComboKey.isUsable]；「成员键还都接管着」由调用方补），
     *  排序也是原来那一份：`maxByOrNull { keys.size }` 等价于「按成员数降序取第一个」。
     */
    fun combosFor(down: Set<String>): List<ComboKey> {
        val matched = ArrayList<ComboKey>(2)
        for (c in combos) {
            if (c.isUsable && c.matches(down)) matched.add(c)
        }
        //  只有一档时不必排序：`ArrayList` 建出来就是结果，省掉一次复制
        if (matched.size > 1) matched.sortByDescending { it.keys.size }
        return matched
    }

    /**
     * 含有 [base] 这几颗键、且**成员更多**的那一档（`null` = 没有更宽的一档）。
     *
     *  这是「更宽的那一档还来不来得及」那道等待的判据（`ComboDetector.awaitWider`）：小的一档凑成
     *  之后要先问一句「还有更大的吗」，否则后者永远只剩「所有手指同时落」才成立 —— 小的一档在
     *  松手那一刻就派发了。四颗以上的链同理（换过去之后再问一次），判据里没有「两颗 / 三颗」
     *  这种数字，全按**真子集**算。
     *
     *  只看 [liveCombos]（成员键都还接管着的那些）：成员里有一颗被停用的一档永远凑不齐，
     *  为它等一个窗口只是让更小那一档白慢一拍。成员数不多于 [base] 的不算「更宽」。
     *
     *  参数收**键本身**而不是 id 集合：调用点在按键判定链上（`inject/input`），每按压一次都要过，
     *  在那里把 id 收进一个 `HashSet` 等于每次按压多一次分配（`tools/audit-perf.py` 的硬判据）。
     */
    fun widerComboOf(base: List<KeyId>): ComboKey? =
        liveCombos()
            //  「更宽的那一档」两种触发方式都算：等窗口档按**整串跨度**判，按住档按**后续成员的
            //  接收窗口**判 —— 两种都是「再按一颗还赶得上」，只是等多久不一样，那个差由调用方
            //  （`ComboDetector.awaitWider`）按这一档的方式算。
            //
            //  **按住档曾经被排除在这里**：那时只认「整串跨度」这一种量法，于是 `{A,B}` 与
            //  `{A,B,C}` 都配成按住时，`{A,B}` 一成立就立刻收尾（松手短按 / 长按到点），
            //  `{A,B,C}` 永远轮不到 —— 真机表现就是「按住 A 再按 B 就触发了 AB 那一档，
            //  BC 那档怎么按都不出现」。
            .filter { c -> c.keys.size > base.size && base.all { k -> k.id in c.keys } }
            .maxByOrNull { it.keys.size }

    /**
     * 这一档组合**现在跑不起来**吗 —— 自己关着，或**成员里任一键被停用**。
     *
     *  判据按成员集合算，不按颗数：两颗的组合与手改配置写进来的多按键组合（判定侧认到
     *  [ComboKey.MAX_JUDGE_KEYS]）走同一条规则 —— 少任何一颗键都凑不齐，这一档就是废的。
     *  界面据此标「已停用」、启停切换也据此判「要不要连带」。
     */
    fun comboOff(combo: ComboKey): Boolean =
        !combo.enabled ||
            combo.keys.any { member ->
                //  **成员键已经不在这张表里**（被删掉）与「被停用」同一档：这一档同样永远凑不齐。
                //  原先只查 `enabled`，这种组合会被界面显示成「还在用」，而判定侧再也不会有它 ——
                //  与 [comboFor] 的判据、以及「少任何一颗键这一档就是废的」那条注释都相反。
                val key = keys.entries.firstOrNull { it.key.id == member }?.value ?: return@any true
                !key.enabled
            }

    /**
     * 判定侧**真正要算**的那几档组合 —— [comboOff] 为假的那些。
     *
     *  与 [comboFor] 同一条口径（那颗键还接管着，才谈得上「凑得齐」），比 `ComboKey.isUsable`
     *  严一位：`isUsable` 只看这一档自己关没关、成员够不够数，**不看成员键是否被停用**。
     *
     *  三处读者都从这里取：注入侧每颗键「等多久」的账（`ConfigChannel.holdWindows`）、
     *  「参不参与组合」的成员表（`rebuildComboIndex`）、以及判定计划（`comboPlans`）。
     *  各自按 `isUsable` 筛的后果是「这一档永远凑不齐，参与它的键却照样等满一个窗口」——
     *  白等到秒级（整串跨度 = 窗口 × (n-1)），且列表上已经显示「已停用」。
     */
    fun liveCombos(): List<ComboKey> = combos.filterNot(::comboOff)

    /** 顶层那两位的取值 —— 与另两段同一套（见 `KeyConfig.fieldValues` 的说明）。 */
    fun fieldValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_DISCLAIMER to disclaimerAccepted,
                ConfigFields.F_COMMAND_DISCLAIMER to commandDisclaimerAccepted,
            ),
        )

    companion object {
        val DEFAULT =
            Config(
                keys = KeyId.builtins.associateWith { KeyConfig.defaultFor(it) },
                global = GlobalConfig.DEFAULT,
            )

        /**
         * 拼一份配置 —— 扁平字段从取值表来，几张「表」的段由调用方读好传进来。
         *
         *  [keys] / [global] 由调用方各自按 [KeyConfig.ofFields] / [GlobalConfig.ofFields] 建好。
         *  [customKeys] 与 [keys] **必须对得上**：每一个自定义键都得在 `keys` 里有一条，
         *  否则 `key(id)` 会退回默认值，界面上看着像「刚收集的键绑定全丢了」。
         *
         *  `@Suppress("LongParameterList")`：这不是「参数太多、该拆对象」那种函数 —— 参数个数就是
         *  配置里那几张表的个数（扁平取值表 + 三张进不了字段表的段）。拆成一个 `Sections` 壳只是
         *  把它们挪个地方，读取处还得一层层解引用，而每加一张表就多一个要同步的落点。
         */
        fun ofFields(
            keys: Map<KeyId, KeyConfig>,
            global: GlobalConfig,
            v: ConfigFields.Values,
            /** 用户收集来的按键（`custom_keys` 那一段）—— 不在字段表里，由 `ConfigCodec` 单独读。 */
            customKeys: List<KeyId> = emptyList(),
            /** 名字表（`app_names` 那一段）—— 同上，由 [ConfigCodec] 单独读出来传进来。 */
            appNames: Map<String, String> = emptyMap(),
            /** 组合按键（`combos` 那一段）—— 同上。 */
            combos: List<ComboKey> = emptyList(),
        ): Config =
            Config(
                keys = keys,
                customKeys = customKeys,
                global = global,
                disclaimerAccepted = v.bool(ConfigFields.F_DISCLAIMER),
                commandDisclaimerAccepted = v.bool(ConfigFields.F_COMMAND_DISCLAIMER),
                appNames = appNames,
                combos = combos,
            )
    }
}

/**
 * 「常用命令」清单里的一条 —— 用户钉进那一段的一个动作。
 *
 *  与命令库是**两套东西**：这里存的是一条**动作写法**（`cmd:` / `app:` / `keyevent:` / `shell:` …），
 *  不是命令库里那条记录；于是它既能指回模块自带的几条（`cmd:common.wechat_scan`），
 *  也能直接指任意一个动作。写入绑定时原样落 [token]，执行侧不需要认识这张表。
 *
 * @param token 动作写法（`Action.parse` 认的那种）
 * @param name 用户起的名字；**空串 = 按动作自己念**（走 `commandTitleOf`）
 * @param showInBinding 这一条要不要出现在按键绑定页那一段。**每条各管各的**：常用命令只是
 *   一条快捷方式，哪几条值得常按由用户自己挑 —— 没有「全显示 / 全隐藏」那种总闸。
 *   关掉只影响绑定页那一段，这一条仍在「常用命令」页的清单里（还能绑给别的目标）。
 * @param icon 给这一条换个图标 —— 与 `fluid:` 的图标**同一套字形**（`FluidIcons` 的 id，
 *   空串 = 用它本来那颗）。**只是一个界面上的选择**：常用命令只出现在模块自己的界面里
 *   （绑定页那一段、这一页的清单），画的是 `ImageVector`，不必也不该渲染成 png 交给宿主。
 */
data class CommonCommand(
    val token: String,
    val name: String = "",
    val showInBinding: Boolean = true,
    val icon: String = "",
)

/**
 * 一组组合键的**四个窗口**（毫秒）—— 等待 / 按住 × 固定窗口起点 开 / 关。
 *
 *  为什么是四个而不是一个：一个数同时管两种按法的两种时序，会把四件不同的事压成一件 ——
 *  调宽「按住组合的成员接收」会连带拖钝「等待组合的收集」，而开关一拨那个数还会
 *  从「整串」变成「相邻两颗」。所以四格各存一份（公共一份、每组可另设一份）。
 *
 * @param wait 等待组合、固定窗口起点**开**：从首个成员按下起，整串共用一个窗口
 * @param waitPair 等待组合、**关**：相邻两颗之间（n 颗整串 = 它 × (n-1)）
 * @param hold 按住组合、**开**：后续成员要在首个成员按下后的这个窗口内落完
 * @param holdFollower 按住组合、**关**：后续成员每颗各自往前等多久
 */
data class ComboWindows(
    val wait: Int,
    val waitPair: Int,
    val hold: Int,
    val holdFollower: Int,
) {
    companion object {
        /**
         * **出厂那一份** —— 固定等那两格读 [GlobalConfig.DEFAULT_COMBO_WINDOW_MS]（350）、
         *  逐次等那两格读 [GlobalConfig.DEFAULT_COMBO_PAIR_MS]（250）。
         *
         *  两处都用它：公共页「双击滑杆回落」，以及某一组「开着独立设置、可这一格没写过」。
         *  后者原先回落成**当前公共值**，于是「独立设置」在没写过时等于公共设置的一份快照 ——
         *  改一次公共，那一档跟着变（口径见 `docs/changelog/2026-11-17-独立设置去掉播种.md`）。
         */
        val DEFAULT: ComboWindows =
            ComboWindows(
                wait = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                waitPair = GlobalConfig.DEFAULT_COMBO_PAIR_MS,
                hold = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                holdFollower = GlobalConfig.DEFAULT_COMBO_PAIR_MS,
            )
    }
}
