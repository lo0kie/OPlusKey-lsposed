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
import io.github.suqi8.coui.kmp.basic.CardListPosition

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
 * @param onWrite 改完之后的新值，调用方负责落到自己那段配置上
 */
@Composable
internal fun SettingsPage.OwnSettingsCard(
    owner: OwnSettings,
    enabled: Boolean,
    onWrite: (OwnSettingsValues) -> Unit,
) {
    var editingTemplate by remember { mutableStateOf(false) }
    CardList(
        rows =
            ownSettingRows(
                owner = owner,
                enabled = enabled,
                onEdit = { onWrite(it) },
                onEditTemplate = { editingTemplate = true },
            ),
    )
    OwnTemplateDialog(
        initial = owner.toastTemplate,
        show = editingTemplate,
        onShowChange = { editingTemplate = it },
    ) { text ->
        onWrite(owner.values().copy(toastTemplate = text))
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
    onEdit: (OwnSettingsValues) -> Unit,
    onEditTemplate: () -> Unit,
): List<CardRow> {
    // 每一行都基于「当前这份值」改一位再交回去 —— 形状与 `CardRow` 的开关一致
    fun edit(transform: (OwnSettingsValues) -> OwnSettingsValues) = onEdit(transform(owner.values()))

    val rowsEnabled = enabled && owner.useOwnSettings
    return buildList {
        add(
            CardRow.Switch(
                title = I18n.get(Strings.SETTING_USE_OWN, "使用独立设置"),
                summaryOn = I18n.get(Strings.SETTING_USE_OWN_ON, "接口、振动、提示分别设置"),
                summaryOff = I18n.get(Strings.SETTING_USE_OWN_OFF, "接口、振动、提示跟随公共设置"),
                icon = PageIcons.SettingsCog,
                checked = owner.useOwnSettings,
                enabled = enabled,
            ) { on ->
                edit { v -> v.copy(useOwn = on) }
            },
        )
        add(
            CardRow.Switch(
                title = I18n.get(Strings.HOME_COLOROS_TITLE, "使用 ColorOS 接口"),
                summaryOn = I18n.get(Strings.HOME_COLOROS_DESC_ON, "部分命令走 ColorOS 专属接口"),
                summaryOff = I18n.get(Strings.HOME_COLOROS_DESC_OFF, "部分命令改用 AOSP 标准接口"),
                icon = PageIcons.ColorOsLink,
                checked = owner.colorOsApi,
                enabled = rowsEnabled,
            ) {
                edit { v -> v.copy(colorOsApi = it) }
            },
        )
        add(
            CardRow.Switch(
                title = I18n.get(Strings.HOME_VIBRATE_TITLE, "振动反馈"),
                summaryOn = I18n.get(Strings.HOME_VIBRATE_DESC_ON, "每次执行动作振动一次"),
                summaryOff = I18n.get(Strings.HOME_VIBRATE_DESC_OFF, "执行动作时不振动"),
                icon = PageIcons.Vibrate,
                checked = owner.vibrate,
                enabled = rowsEnabled,
            ) {
                edit { v -> v.copy(vibrate = it) }
            },
        )
        // 振动强度只在振动开着时出现 —— 关掉振动还摆一根强度滑杆，
        //  等于让人去调一个当前不起作用的东西（与公共页那一行同一条口径）。
        add(
            CardRow.Custom(visible = owner.vibrate) { position ->
                OwnStrengthRow(
                    position = position,
                    level = owner.vibrateStrength,
                    enabled = rowsEnabled,
                ) { level ->
                    edit { v -> v.copy(vibrateStrength = level) }
                }
            },
        )
        addAll(toastRows(owner, rowsEnabled, ::edit, onEditTemplate))
    }
}

/** 「执行时提示 + 文案」那两行 —— 文案只在提示开着时出现。 */
@Composable
private fun SettingsPage.toastRows(
    owner: OwnSettings,
    rowsEnabled: Boolean,
    edit: ((OwnSettingsValues) -> OwnSettingsValues) -> Unit,
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
        ) {
            edit { v -> v.copy(toastOnRun = it) }
        },
        CardRow.Item(
            title = I18n.get(Strings.HOME_TOAST_TEMPLATE_TITLE, "提示文案"),
            //  这一句**不给兜底**：里面带 `$`，兜底审计是按源码字面量逐字比的。
            summary = owner.toastTemplate.ifBlank { I18n.get(Strings.HOME_TOAST_TEMPLATE_DEFAULT) },
            icon = PageIcons.ToastTemplate,
            arrow = true,
            enabled = rowsEnabled,
            visible = owner.toastOnRun,
            onClick = onEditTemplate,
        ),
    )

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
    )

/** 振动强度那根滑杆 —— 与公共页那一行同一套规格（三档、行首数字、行尾说法）。 */
@Composable
private fun SettingsPage.OwnStrengthRow(
    position: CardListPosition,
    level: Int,
    enabled: Boolean,
    onCommit: (Int) -> Unit,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.HOME_VIBRATE_STRENGTH_TITLE, "振动强度"),
                summary = I18n.get(Strings.HOME_VIBRATE_STRENGTH_DESC, "命令触发时振动的力度"),
                icon = PageIcons.vibrateLevel(level),
                min = GlobalConfig.VIBRATE_WEAK,
                max = GlobalConfig.VIBRATE_STRONG,
                value = level,
                default = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
                step = 1,
                enabled = enabled,
                valueText = { strengthLabel(it) },
                keyPoints = null,
            ),
    ) { level ->
        onCommit(level)
        //  滑杆要「写盘成没成」：失败时它把滑块拨回原值。这里是同步落盘，
        //  `edit` 那条已经把结果交给 `write` 了 —— 没有失败路径可报，恒 true。
        true
    }
}

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
    )
