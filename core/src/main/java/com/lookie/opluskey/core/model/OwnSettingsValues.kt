package com.lookie.opluskey.core.model

// 「独立设置」那份值的**可变载体**，以及「按字段合并」的那一小块 —— 这两样东西**不是界面**：
// 写回规则只有一份，卡片（`ui/row`）与写回层（`ui/write`）都用它。
//
//  住在 `core` 而不是 `ui`：这几位是**配置的形状**（`KeyConfig` / `ComboKey` 都实现
//  [OwnSettings]），落到哪张表才是界面层的事（`KeySettingsScreen.applyOwnSettings`）。

/**
 * 「独立设置」里**被点的那一项** —— 写回时只覆盖它，其余项一律以配置现值为准。
 *
 *  为什么需要它：卡片把整份 [OwnSettingsValues] 交回来，而那份值是**渲染这一帧时**从
 *  `owner` 读的。写盘 → 重组之间只要错开一步，交回来的就是上一帧的整份值 —— 用户点
 *  「执行时提示」，顺带把「使用独立设置」一起写回旧值（真机反馈：「打开独立设置，再点
 *  执行时提示，那一颗自己又关回去了」）。按字段合并之后，**只有手指真点到的那一项会变**，
 *  别的项有多旧都不影响。
 */
enum class OwnField { USE_OWN, VIBRATE, VIBRATE_ON_NONE, STRENGTH, MODE, TEMPLATE, STEP_NOTICE }

/** 用 [src] 里那一项的值替换自己这一项（其余字段不动）。 */
fun OwnSettingsValues.withField(
    field: OwnField,
    src: OwnSettingsValues,
): OwnSettingsValues =
    when (field) {
        OwnField.USE_OWN -> {
            copy(useOwn = src.useOwnSettings)
        }

        OwnField.VIBRATE -> {
            copy(vibrate = src.vibrate)
        }

        OwnField.VIBRATE_ON_NONE -> {
            copy(vibrateOnNone = src.vibrateOnNone)
        }

        OwnField.STRENGTH -> {
            copy(vibrateStrength = src.vibrateStrength)
        }

        //  「执行时提示」是**一格**（四档），所以三颗布尔一起搬 —— 分开搬会写出
        //  「通知 + 流体云同时为真」那种配置里表达不出来的状态。
        OwnField.MODE -> {
            copy(
                toastOnRun = src.toastOnRun,
                toastNotify = src.toastNotify,
                toastFluid = src.toastFluid,
            )
        }

        OwnField.TEMPLATE -> {
            copy(toastTemplate = src.toastTemplate)
        }

        OwnField.STEP_NOTICE -> {
            copy(stepNotice = src.stepNotice)
        }
    }

/**
 * 独立设置那份值的**可变载体** —— 界面内部改一位就用它 `copy`，改完整体交回调用方。
 *
 *  为什么不直接用 [OwnSettings]：接口没有 `copy`，逐个回调又会把「改哪个字段」摊成六个参数。
 */
data class OwnSettingsValues(
    private val useOwn: Boolean,
    override val colorOsApi: Boolean,
    override val vibrate: Boolean,
    override val vibrateStrength: Int,
    override val vibrateOnNone: Boolean,
    override val toastOnRun: Boolean,
    override val toastFluid: Boolean,
    override val toastNotify: Boolean,
    override val toastTemplate: String,
    override val stepNotice: Boolean,
) : OwnSettings {
    override val useOwnSettings: Boolean get() = useOwn
}

/** 把任一档的独立设置读成可改的那份载体。 */
fun OwnSettings.values(): OwnSettingsValues =
    OwnSettingsValues(
        useOwn = useOwnSettings,
        colorOsApi = colorOsApi,
        vibrate = vibrate,
        vibrateStrength = vibrateStrength,
        vibrateOnNone = vibrateOnNone,
        toastOnRun = toastOnRun,
        toastFluid = toastFluid,
        toastNotify = toastNotify,
        toastTemplate = toastTemplate,
        stepNotice = stepNotice,
    )

/**
 * 四档 → 这一份值的三颗布尔（与 [NoticeMode.of] 成对）。
 *
 *  三颗**一起写**：分开写就会留下「通知 + 流体云同时为真」那种配置里表达不出来的形状。
 */
fun OwnSettingsValues.withNoticeMode(mode: NoticeMode): OwnSettingsValues =
    copy(
        toastOnRun = mode != NoticeMode.NONE,
        toastNotify = mode == NoticeMode.NOTIFY,
        toastFluid = mode == NoticeMode.FLUID,
    )

/**
 * 把这份值落到一颗键上 —— 字段同名同义，两张表的 `copy` 只是表不同。
 *
 *  **这里必须把 [OwnSettings] 的每一位都抄到**：漏一位的表现不是报错，而是「那一行拨了
 *  不动」—— 值算对了、也交回来了，写盘时被丢掉，重组后又读回旧值（`vibrateOnNone`
 *  就这么漏过一次）。
 */
fun KeyConfig.withOwn(values: OwnSettingsValues): KeyConfig =
    copy(
        useOwnSettings = values.useOwnSettings,
        colorOsApi = values.colorOsApi,
        vibrate = values.vibrate,
        vibrateStrength = values.vibrateStrength,
        vibrateOnNone = values.vibrateOnNone,
        toastOnRun = values.toastOnRun,
        toastFluid = values.toastFluid,
        toastNotify = values.toastNotify,
        toastTemplate = values.toastTemplate,
        stepNotice = values.stepNotice,
    )

/** 把这份值落到一组组合键上 —— 与上面那份一字不差。 */
fun ComboKey.withOwn(values: OwnSettingsValues): ComboKey =
    copy(
        useOwnSettings = values.useOwnSettings,
        colorOsApi = values.colorOsApi,
        vibrate = values.vibrate,
        vibrateStrength = values.vibrateStrength,
        vibrateOnNone = values.vibrateOnNone,
        toastOnRun = values.toastOnRun,
        toastFluid = values.toastFluid,
        toastNotify = values.toastNotify,
        toastTemplate = values.toastTemplate,
        stepNotice = values.stepNotice,
    )
