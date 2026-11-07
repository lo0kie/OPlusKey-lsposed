package com.lookie.opluskey.ui

import com.lookie.opluskey.core.model.OwnSettings

// 「独立设置」那份值的载体与「按字段合并」的那一小块 —— 从 `OwnSettingsCard.kt` 搬出来，
// 一是那支卡片本身已经够长（detekt 的文件级函数数上限），二是这两样东西**不是界面**：
// 写回按字段合并的规则只有一份，卡片与页面都用它。

/**
 * 这一页里**被点的那一项** —— 写回时只覆盖它，其余项一律以配置现值为准。
 *
 *  为什么需要它：卡片把整份 [OwnSettingsValues] 交回来，而那份值是**渲染这一帧时**从 `owner`
 *  读的。写盘 → 重组之间只要错开一步，交回来的就是上一帧的整份值 —— 用户点「执行时提示」，
 *  顺带把「使用独立设置」一起写回旧值（真机反馈：「打开独立设置，再点执行时提示，
 *  那一颗自己又关回去了」）。按字段合并之后，**只有手指真点到的那一项会变**，
 *  别的项有多旧都不影响。
 */
internal enum class OwnField { USE_OWN, COLOR_OS, VIBRATE, STRENGTH, TOAST, TEMPLATE }

/** 用 [src] 里那一项的值替换自己这一项（其余字段不动）。 */
internal fun OwnSettingsValues.withField(
    field: OwnField,
    src: OwnSettingsValues,
): OwnSettingsValues =
    when (field) {
        OwnField.USE_OWN -> copy(useOwn = src.useOwnSettings)
        OwnField.COLOR_OS -> copy(colorOsApi = src.colorOsApi)
        OwnField.VIBRATE -> copy(vibrate = src.vibrate)
        OwnField.STRENGTH -> copy(vibrateStrength = src.vibrateStrength)
        OwnField.TOAST -> copy(toastOnRun = src.toastOnRun)
        OwnField.TEMPLATE -> copy(toastTemplate = src.toastTemplate)
    }

/**
 * 独立设置那份值的**可变载体** —— 界面内部改一位就用它 `copy`，改完整体交回调用方。
 *
 *  为什么不直接用 [OwnSettings]：接口没有 `copy`，逐个回调又会把「改哪个字段」摊成六个参数。
 */
internal data class OwnSettingsValues(
    private val useOwn: Boolean,
    override val colorOsApi: Boolean,
    override val vibrate: Boolean,
    override val vibrateStrength: Int,
    override val toastOnRun: Boolean,
    override val toastTemplate: String,
    override val ownSettingsSeeded: Boolean = false,
) : OwnSettings {
    override val useOwnSettings: Boolean get() = useOwn
}

/** 把任一档的独立设置读成可改的那份载体。 */
internal fun OwnSettings.values(): OwnSettingsValues =
    OwnSettingsValues(
        useOwn = useOwnSettings,
        colorOsApi = colorOsApi,
        vibrate = vibrate,
        vibrateStrength = vibrateStrength,
        toastOnRun = toastOnRun,
        toastTemplate = toastTemplate,
        ownSettingsSeeded = ownSettingsSeeded,
    )
