package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.OwnSettings
import com.lookie.opluskey.core.model.ToastNotice
import com.lookie.opluskey.ui.model.PageIcons

/**
 * 「按键单独设置」那张卡 —— **单键与组合键共用这一张**。
 *
 * ## 它是什么
 *
 *  公共设置页（`Screen.GENERAL`）里那几项「按键怎么工作」的开关，这一档可以自己另设一份：
 *  走不走 ColorOS 专有接口、振不振、振多重、弹不弹提示、提示写什么。
 *  顶上那颗总开关（[OwnSettings.useOwnSettings]，默认关）决定用哪份 —— 关着就是跟随公共设置。
 *
 * ## 为什么不认识 `KeyConfig` / `ComboKey`
 *
 *  这张卡只认 [OwnSettings]（两者共用的契约）与 [OwnSettingsValues]（改完之后交回去的那份值）。
 *  谁调用谁负责把值写回自己那一段配置 —— 卡里**没有**「这是单键还是组合」的分支，
 *  这正是「组合键不特殊对待」在界面这一层的落法。
 *
 * ## 置灰
 *
 *  总开关关着时，下面那几行整片灰（它们此刻不参与取值，亮着就是在骗人可以调）；
 *  [enabled] 是页面闸门（模块关 / 链路未通 / 只读），它一并管住总开关自己。
 *
 * @param owner 这一档当前的独立设置（只读；改由 [onWrite] 交回去）
 * @param enabled 页面闸门 —— 关着时整张卡不吃点击
 * @param onWrite 改完之后的新值 + **这次改的是哪一项**（后者决定调用方只覆盖哪一格，
 *   见 [OwnField]），调用方负责落到自己那段配置上
 */
@Composable
internal fun SettingsPage.OwnSettingsCard(
    owner: OwnSettings,
    enabled: Boolean,
    onWrite: (OwnSettingsValues, OwnField) -> Unit,
) {
    var editingTemplate by remember { mutableStateOf(false) }
    CardList(
        rows =
            ownSettingRows(
                owner = owner,
                enabled = enabled,
                onEdit = { values, field -> onWrite(values, field) },
                onEditTemplate = { editingTemplate = true },
            ),
    )
    OwnTemplateDialog(
        initial = owner.toastTemplate,
        show = editingTemplate,
        onShowChange = { editingTemplate = it },
    ) { text ->
        onWrite(owner.values().copy(toastTemplate = text), OwnField.TEMPLATE)
    }
}

/**
 * 这张卡的**行表** —— 与 [OwnSettingsCard] 分开只为长度（一行一个开关，摊在主函数里 80 多行）。
 *
 *  形状与公共页那两张卡一致：**总开关在最前**，其余每行都吃「总开关 + 页面闸门」。
 */
@Composable
private fun SettingsPage.ownSettingRows(
    owner: OwnSettings,
    enabled: Boolean,
    onEdit: (OwnSettingsValues, OwnField) -> Unit,
    onEditTemplate: () -> Unit,
): List<CardRow> {
    // 每一行都基于「当前这份值」改一位再交回去 —— 形状与 `CardRow` 的开关一致。
    // **顺手写下改的是哪一项**：调用方按它只覆盖那一格（见 [OwnField]）。
    fun edit(
        field: OwnField,
        transform: (OwnSettingsValues) -> OwnSettingsValues,
    ) = onEdit(transform(owner.values()), field)

    val rowsEnabled = enabled && owner.useOwnSettings
    //  灰着时点一下该说的话：整页闸门关着 ⇒ `null`（用整页那句：主开关 / 链路 / 只读）；
    //  整页没事、只是这颗总开关没开 ⇒ 说清该先打开哪一项。
    val ownHint =
        if (enabled && !owner.useOwnSettings) {
            I18n.get(Strings.KEY_SETTINGS_NEED_OWN, "先打开「使用独立设置」")
        } else {
            null
        }
    return buildList {
        addAll(commonRows(owner, enabled, rowsEnabled, ownHint, ::edit))
        add(strengthRow(owner, rowsEnabled, ownHint, ::edit))
        addAll(toastRows(owner, rowsEnabled, ownHint, ::edit, onEditTemplate))
    }
}

/**
 * 前三个开关：**总开关自己** + 接口 + 振动。
 *
 *  总开关吃 [enabled]（页面闸门），另两个吃 [rowsEnabled]（闸门 **且** 总开关开着）——
 *  这三个摆在一起才看得出「谁管谁」。
 */
@Composable
private fun SettingsPage.commonRows(
    owner: OwnSettings,
    enabled: Boolean,
    rowsEnabled: Boolean,
    ownHint: String?,
    edit: (OwnField, (OwnSettingsValues) -> OwnSettingsValues) -> Unit,
): List<CardRow> =
    listOf(
        CardRow.Switch(
            title = I18n.get(Strings.SETTING_USE_OWN, "使用独立设置"),
            summaryOn = I18n.get(Strings.SETTING_USE_OWN_ON, "接口、振动、提示分别设置"),
            summaryOff = I18n.get(Strings.SETTING_USE_OWN_OFF, "接口、振动、提示跟随公共设置"),
            //  与「启用按键绑定」那一行同一颗图标：两者都是「这一个开关决定下面那一组算不算数」
            icon = PageIcons.MasterSwitch,
            checked = owner.useOwnSettings,
            enabled = enabled,
        ) { on ->
            edit(OwnField.USE_OWN) { v -> v.copy(useOwn = on) }
        },
        CardRow.Switch(
            title = I18n.get(Strings.HOME_COLOROS_TITLE, "使用 ColorOS 接口"),
            summaryOn = I18n.get(Strings.HOME_COLOROS_DESC_ON, "部分命令走 ColorOS 专属接口"),
            summaryOff = I18n.get(Strings.HOME_COLOROS_DESC_OFF, "部分命令改用 AOSP 标准接口"),
            icon = PageIcons.ColorOsLink,
            checked = owner.colorOsApi,
            enabled = rowsEnabled,
            disabledHint = ownHint,
        ) {
            edit(OwnField.COLOR_OS) { v -> v.copy(colorOsApi = it) }
        },
        CardRow.Switch(
            title = I18n.get(Strings.HOME_VIBRATE_TITLE, "振动反馈"),
            summaryOn = I18n.get(Strings.HOME_VIBRATE_DESC_ON, "每次执行动作振动一次"),
            summaryOff = I18n.get(Strings.HOME_VIBRATE_DESC_OFF, "执行动作时不振动"),
            icon = PageIcons.Vibrate,
            checked = owner.vibrate,
            enabled = rowsEnabled,
            disabledHint = ownHint,
        ) {
            edit(OwnField.VIBRATE) { v -> v.copy(vibrate = it) }
        },
    )

/**
 * 振动强度那一行 —— 只在振动开着时出现（关掉振动还摆一根强度滑杆，
 * 等于让人去调一个当前不起作用的东西；与公共页那一行同一条口径）。
 *
 * 规格与公共页那一行同一套（三档、行首随档换字形、行尾写「弱 / 中 / 强」）。
 *
 *  @param hint 灰着被点一下时的说法 —— 交给 `SliderSpec.disabledHint`（投递在 [SliderRow]
 *   那一层，所有滑杆共用一条通道）；`null` = 整页闸门关着，用页面那句。
 */
@Composable
private fun SettingsPage.strengthRow(
    owner: OwnSettings,
    rowsEnabled: Boolean,
    hint: String?,
    edit: (OwnField, (OwnSettingsValues) -> OwnSettingsValues) -> Unit,
): CardRow =
    CardRow.Custom(visible = owner.vibrate) { position ->
        SliderRow(
            position = position,
            spec =
                SliderSpec(
                    title = I18n.get(Strings.HOME_VIBRATE_STRENGTH_TITLE, "振动强度"),
                    summary = I18n.get(Strings.HOME_VIBRATE_STRENGTH_DESC, "命令触发时振动的力度"),
                    icon = PageIcons.vibrateLevel(owner.vibrateStrength),
                    min = GlobalConfig.VIBRATE_WEAK,
                    max = GlobalConfig.VIBRATE_STRONG,
                    value = owner.vibrateStrength,
                    default = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
                    step = 1,
                    enabled = rowsEnabled,
                    disabledHint = hint,
                    valueText = { strengthLabel(it) },
                    keyPoints = null,
                ),
        ) { level ->
            edit(OwnField.STRENGTH) { v -> v.copy(vibrateStrength = level) }
            //  滑杆要「写盘成没成」：失败时它把滑块拨回原值。这里是同步落盘，
            //  `edit` 那条已经把结果交给 `write` 了 —— 没有失败路径可报，恒 true。
            true
        }
    }

/** 「执行时提示 + 文案」那两行 —— 文案只在提示开着时出现。 */
@Composable
private fun SettingsPage.toastRows(
    owner: OwnSettings,
    rowsEnabled: Boolean,
    ownHint: String?,
    edit: (OwnField, (OwnSettingsValues) -> OwnSettingsValues) -> Unit,
    onEditTemplate: () -> Unit,
): List<CardRow> =
    listOf(
        CardRow.Switch(
            title = I18n.get(Strings.HOME_TOAST_TITLE, "执行时提示"),
            summaryOn = I18n.get(Strings.HOME_TOAST_DESC_ON, "每次执行动作弹一条提示"),
            summaryOff = I18n.get(Strings.HOME_TOAST_DESC_OFF, "执行动作时不弹提示"),
            icon = PageIcons.CommandToast,
            checked = owner.toastOnRun,
            enabled = rowsEnabled,
            disabledHint = ownHint,
        ) {
            edit(OwnField.TOAST) { v -> v.copy(toastOnRun = it) }
        },
        CardRow.Item(
            title = I18n.get(Strings.HOME_TOAST_TEMPLATE_TITLE, "提示文案"),
            //  这一句**不给兜底**：里面带 `$`，兜底审计是按源码字面量逐字比的。
            summary = owner.toastTemplate.ifBlank { I18n.get(Strings.HOME_TOAST_TEMPLATE_DEFAULT) },
            icon = PageIcons.ToastTemplate,
            arrow = true,
            enabled = rowsEnabled,
            disabledHint = ownHint,
            visible = owner.toastOnRun,
            onClick = onEditTemplate,
        ),
    )

/** 三档强度的说法（弱 / 中 / 强）—— 与公共页同一个映射，越界落到「中」。 */
private fun strengthLabel(level: Int): String =
    when (level) {
        GlobalConfig.VIBRATE_WEAK -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_WEAK, "弱")
        }

        GlobalConfig.VIBRATE_STRONG -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_STRONG, "强")
        }

        else -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_MEDIUM, "中")
        }
    }

/**
 * 提示文案的编辑窗 —— 与公共页那个同名弹窗同一套行为（空格子 = 用默认那句，可重置）。
 *
 *  不复用公共页那一支：那个直接写全局配置（`setToastTemplate`），这里要把值交回调用方。
 *  形状一样、落点不同，拆成两支比塞一个「写到哪」的参数更直白。
 */
@Composable
private fun SettingsPage.OwnTemplateDialog(
    initial: String,
    show: Boolean,
    onShowChange: (Boolean) -> Unit,
    onConfirm: (String) -> Unit,
) {
    NameDialog(
        show = show,
        title = I18n.get(Strings.HOME_TOAST_TEMPLATE_TITLE, "提示文案"),
        label = I18n.get(Strings.HOME_TOAST_TEMPLATE_PLACEHOLDER, "留空将使用默认提示"),
        hint = I18n.get(Strings.HOME_TOAST_TEMPLATE_HINT),
        icon = PageIcons.ToastTemplate,
        initial = initial,
        maxLength = OWN_TEMPLATE_MAX,
        allowEmpty = true,
        onReset = {
            onShowChange(false)
            onConfirm(ToastNotice.DEFAULT_TEMPLATE)
        },
        onDismiss = { onShowChange(false) },
        onConfirm = { text ->
            onShowChange(false)
            onConfirm(text)
        },
    )
}

/** 与公共页那一档同一个上限（一整句话，三个占位符摆上就二十来个字符了）。 */
private const val OWN_TEMPLATE_MAX = 60

/** 把这份值落到一颗键上 —— 六个字段同名同义，两处 `copy` 只是表不同。 */
internal fun KeyConfig.withOwn(values: OwnSettingsValues): KeyConfig =
    copy(
        useOwnSettings = values.useOwnSettings,
        colorOsApi = values.colorOsApi,
        vibrate = values.vibrate,
        vibrateStrength = values.vibrateStrength,
        toastOnRun = values.toastOnRun,
        toastTemplate = values.toastTemplate,
        ownSettingsSeeded = values.ownSettingsSeeded,
    )

/** 把这份值落到一组组合键上 —— 与 [withOwn] 那份一字不差。 */
internal fun ComboKey.withOwn(values: OwnSettingsValues): ComboKey =
    copy(
        useOwnSettings = values.useOwnSettings,
        colorOsApi = values.colorOsApi,
        vibrate = values.vibrate,
        vibrateStrength = values.vibrateStrength,
        toastOnRun = values.toastOnRun,
        toastTemplate = values.toastTemplate,
        ownSettingsSeeded = values.ownSettingsSeeded,
    )
