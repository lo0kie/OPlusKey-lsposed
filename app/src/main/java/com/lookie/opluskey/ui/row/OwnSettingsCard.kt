package com.lookie.opluskey.ui.row

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.NoticeMode
import com.lookie.opluskey.core.model.OwnField
import com.lookie.opluskey.core.model.OwnSettings
import com.lookie.opluskey.core.model.OwnSettingsValues
import com.lookie.opluskey.core.model.ToastNotice
import com.lookie.opluskey.core.model.values
import com.lookie.opluskey.core.model.withNoticeMode
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.NameDialog
import com.lookie.opluskey.ui.model.icon.PageIcons

/**
 * 「按键单独设置」那张卡 —— **单键与组合键共用这一套**。
 *
 * ## 它是什么
 *
 *  公共设置页（`Screen.GENERAL`）里那几项「按键怎么工作」的开关，这一档可以自己另设一份：
 *  走不走 ColorOS 专有接口、振不振、振多重、弹不弹提示、提示写什么。
 *  顶上那颗总开关（[OwnSettings.useOwnSettings]，默认关）决定用哪份 —— 关着就是跟随公共设置。
 *
 * ## 显示的是**这一档自己那份**，不跟总开关跳
 *
 *  总开关关着时这几项不参与取值（执行侧看公共设置），但这一页显示的仍是这一档自己存的那几个值
 *  —— **显示与写入同源**（都是 [owner]）。
 *
 *  为什么不让它跟着开关走：拨一下总开关，整页数字会换成另一份（这一档没写过时就是出厂默认），
 *  读起来像「刚配的东西被重置了」，而其实一个字节都没写。用户口径：**切换那颗开关时只该
 *  「禁用 / 启用」这些行，值一个都不许变**。所以开关只决定 [rowsEnabled] 与「哪几行出现」
 *  （振动关着就没有强度那一行），不参与任何取数；关着时整片灰着并挂一句「需先开启 使用独立设置」，
 *  不会有人把灰着的数字当成此刻的行为。
 *
 *  同一页下半的单击卡（`ClickDelayCard`）与组合参数卡（`ComboParamCards`）也是这条口径：
 *  读的是这一档自己的值，总开关只管可编辑性。
 *
 * ## 为什么是**两张**卡，且各带一个小标题
 *
 *  总开关单独一张（[useOwnRow]），其余各项一张（[ownSettingRows]）：前者回答「这一档算不算数」，
 *  后者回答「算数时是什么样」—— 混在同一张卡里，那颗总开关读起来像下面几行的第一行。
 *  于是两张卡各配一个小标题（[switchTitle] / [itemsTitle]，由页面给），
 *  与「按键设置」页同构：**一个小标题带一张卡**。页面顶部不再另放一个与顶栏同名的标题。
 *
 * ## 为什么不认识 `KeyConfig` / `ComboKey`
 *
 *  这套卡只认 [OwnSettings]（两者共用的契约）与 [OwnSettingsValues]（改完之后交回去的那份值）。
 *  谁调用谁负责把值写回自己那一段配置 —— 卡里**没有**「这是单键还是组合」的分支，
 *  这正是「组合键不特殊对待」在界面这一层的落法。
 *
 * ## 置灰
 *
 *  总开关关着时，下面那几行整片灰（它们此刻不参与取值，亮着就是在骗人可以调）；
 *  [gate] 是这一页那几道闸（见 [EditGate]），它一并管住总开关自己。
 *
 * @param owner 这一档当前的独立设置（只读；改由 [onWrite] 交回去）
 * @param gate 页面的闸门（整页 / 单键手势 / 总开关）—— 卡里不再自己拼这几个条件
 * @param titles 三张卡各自的小标题（见 [OwnCardTitles]）
 * @param onWrite 改完之后的新值 + **这次改的是哪一项**（后者决定调用方只覆盖哪一格，
 *   见 [OwnField]），调用方负责落到自己那段配置上
 */
@Composable
internal fun SettingsPage.OwnSettingsCard(
    owner: OwnSettings,
    gate: EditGate,
    titles: OwnCardTitles,
    onWrite: (OwnSettingsValues, OwnField) -> Unit,
) {
    var editingTemplate by remember { mutableStateOf(false) }
    //  「执行时提示」四档菜单开着没有 —— 与常规页那一份同一套（见 `NoticeModeRow`）
    val noticeMenu = rememberNoticeModeMenu()
    //  一个小标题带一张卡（与「按键设置」页同构）。间距由 `SectionTitle` 自带，不再自己补间距。
    //  三张卡的标题与「按键设置」页**逐字相同**（同一个字典键）：两页读到的是一套分类，
    //  只是这一页改的是「这一档自己那份」。总开关那张是这一页独有的，所以单独给了个标题。
    val rowsEnabled = gate.ownRows
    //  灰着时点一下该说的话：整页闸门关着 ⇒ `null`（用整页那句：主开关 / 链路 / 只读）；
    //  整页没事、只是这颗总开关没开 ⇒ 说清该先打开哪一项。两张卡共用这一句。
    val ownHint = gate.ownHint

    // 每一行都基于「当前这份值」改一位再交回去 —— 形状与 `CardRow` 的开关一致。
    // **顺手写下改的是哪一项**：调用方按它只覆盖那一格（见 [OwnField]）。
    //  基值取 [owner]（写入的落点）：`withField` 只取被改的那一格，其余项以配置现值为准。
    fun edit(
        field: OwnField,
        transform: (OwnSettingsValues) -> OwnSettingsValues,
    ) = onWrite(transform(owner.values()), field)

    SectionTitle(titles.master)
    //  总开关**另起一张卡**：它管的是「下面那几张卡算不算数」，不是「调成什么样」。
    CardList(rows = listOf(useOwnRow(owner, gate.page, onWrite)))
    SectionTitle(titles.interfaceTitle)
    CardList(rows = listOf(interfaceRow(owner, rowsEnabled, ownHint, ::edit)))
    SectionTitle(titles.behavior)
    CardList(
        rows =
            buildList {
                add(vibrateRow(owner, rowsEnabled, ownHint, ::edit))
                add(vibrateOnNoneRow(owner, rowsEnabled, ownHint, ::edit))
                add(strengthRow(owner, rowsEnabled, ownHint, ::edit))
                //  「执行时提示」：四档菜单（不提示 / 通知 / Toast / 流体云），与常规页同一个形状
                add(
                    noticeModeRow(
                        mode = owner.noticeMode,
                        enabled = rowsEnabled,
                        anchor = noticeMenu.anchor,
                        disabledHint = ownHint,
                        onClick = noticeMenu::show,
                    ),
                )
                //  「多条命令逐条提示」：与公共页那一颗同一个名字、同一个显隐判据（「不提示」时这一项
                //  无从谈起）。值取这一档自己那份 —— 显示与写入同源，见文件头那段说明。
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.HOME_TOAST_STEP_NOTICE_TITLE),
                        icon = PageIcons.StepNotice,
                        checked = owner.stepNotice,
                        enabled = rowsEnabled,
                        disabledHint = ownHint,
                        visible = owner.noticeMode != NoticeMode.NONE,
                    ) {
                        edit(OwnField.STEP_NOTICE) { v -> v.copy(stepNotice = it) }
                    },
                )
                add(toastTemplateRow(owner, rowsEnabled, ownHint) { editingTemplate = true })
            },
    )
    noticeModeMenu(
        state = noticeMenu,
        current = owner.noticeMode,
        enabled = rowsEnabled,
        colorOsApi = owner.colorOsApi,
    ) { mode ->
        edit(OwnField.MODE) { v -> v.withNoticeMode(mode) }
    }
    OwnTemplateDialog(
        //  窗里编辑的是这一档自己那句 —— 与行尾显示同一个值，否则打开窗先看到一句屏幕上没出现过的文本。
        initial = owner.toastTemplate,
        show = editingTemplate,
        onShowChange = { editingTemplate = it },
    ) { text ->
        onWrite(owner.values().copy(toastTemplate = text), OwnField.TEMPLATE)
    }
}

/**
 * 「独立设置」页三张卡的小标题。
 *
 *  **后两个由页面按公共那一页的同一个字典键给**（`GENERAL_SECTION_INTERFACE` /
 *  `HOME_BINDING_CARD_TITLE`）：两页读到的分类名逐字相同，用户不会觉得「同一件事换了个说法」。
 *  [master] 是这一页独有的 —— 公共页没有「这一档算不算数」这件事。
 *
 *  为什么不直接摊成三个 `String` 参数：`LongParameterList` 在 6 个参数上就报，而这三个天生是一组
 *  （回答同一件事：这一页有哪几张卡）。
 */
internal class OwnCardTitles(
    /** 总开关那张卡（这一页独有）。 */
    val master: String,
    /** 「接口选择」那张卡 —— 与按键设置页同一个字典键。 */
    val interfaceTitle: String,
    /** 「按键行为」那张卡 —— 与按键设置页同一个字典键。 */
    val behavior: String,
)

/**
 * 「使用独立设置」那颗总开关 —— 单独一张卡里唯一的一行。
 *
 *  它只吃整页闸门（`EditGate.page`）：下面那些行还要再吃它自己（关着就是跟随公共设置）。
 */
@Composable
private fun SettingsPage.useOwnRow(
    owner: OwnSettings,
    enabled: Boolean,
    onEdit: (OwnSettingsValues, OwnField) -> Unit,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.SETTING_USE_OWN),
        //  与「启用按键绑定」那一行同一颗图标：两者都是「这一个开关决定下面那一组算不算数」
        icon = PageIcons.MasterSwitch,
        checked = owner.useOwnSettings,
        enabled = enabled,
    ) { on ->
        onEdit(owner.values().copy(useOwn = on), OwnField.USE_OWN)
    }

/**
 * 「接口选择」那张卡的行表 —— 与公共页同一张卡同一个标题，这里只有「使用 ColorOS 接口」一行。
 *
 * @param show 这一档自己那份（见 [OwnSettingsCard]：总开关只管能不能改，不管显示什么）
 */
@Composable
private fun SettingsPage.interfaceRow(
    show: OwnSettings,
    rowsEnabled: Boolean,
    ownHint: String?,
    edit: (OwnField, (OwnSettingsValues) -> OwnSettingsValues) -> Unit,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.HOME_COLOROS_TITLE),
        icon = PageIcons.ColorOsLink,
        checked = show.colorOsApi,
        enabled = rowsEnabled,
        disabledHint = ownHint,
    ) {
        edit(OwnField.COLOR_OS) { v -> v.copy(colorOsApi = it) }
    }

/** 「按键行为」那张卡的第一行 —— 振动反馈（强度那一行只在它开着时出现，见 [strengthRow]）。 */
@Composable
private fun SettingsPage.vibrateRow(
    show: OwnSettings,
    rowsEnabled: Boolean,
    ownHint: String?,
    edit: (OwnField, (OwnSettingsValues) -> OwnSettingsValues) -> Unit,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.HOME_VIBRATE_TITLE),
        icon = PageIcons.Vibrate,
        checked = show.vibrate,
        enabled = rowsEnabled,
        disabledHint = ownHint,
    ) {
        edit(OwnField.VIBRATE) { v -> v.copy(vibrate = it) }
    }

/**
 * 「无操作时振动」—— 与 [vibrateRow] 是两颗开关：那一颗管「执行了动作」，
 * 这一颗管「按了、但这一档什么动作都没有」。
 *
 *  **它依赖振动反馈**（`show.vibrate`，判据与 [strengthRow] 同一条、取的也是这一档自己那份）：
 *  振动关着时整个振动链路都不走，这一颗摆着就是让人调一个不起作用的东西。
 *  显隐复用 `visible` 那套进出场动画，与公共页那一行同一个做法。
 */
@Composable
private fun SettingsPage.vibrateOnNoneRow(
    show: OwnSettings,
    rowsEnabled: Boolean,
    ownHint: String?,
    edit: (OwnField, (OwnSettingsValues) -> OwnSettingsValues) -> Unit,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.HOME_VIBRATE_ON_NONE_TITLE),
        icon = PageIcons.VibrateOnNone,
        checked = show.vibrateOnNone,
        enabled = rowsEnabled,
        disabledHint = ownHint,
        visible = show.vibrate,
    ) {
        edit(OwnField.VIBRATE_ON_NONE) { v -> v.copy(vibrateOnNone = it) }
    }

/**
 * 振动强度那一行 —— 只在振动开着时出现（关掉振动还摆一根强度滑杆，
 * 等于让人去调一个当前不起作用的东西；与公共页那一行同一条口径）。
 *
 * 规格与公共页那一行同一套（三档、行首随档换字形、行尾写「弱 / 中 / 强」）。
 *
 * @param show 这一档自己那份 —— 滑杆值（`show.vibrateStrength`）与「这一行出不出现」
 *   （`show.vibrate`）都从它取，与写入同源（见 [OwnSettingsCard] 那段说明）。
 * @param hint 灰着被点一下时的说法 —— 交给 `SliderSpec.disabledHint`（投递在 [SliderRow]
 *   那一层，所有滑杆共用一条通道）；`null` = 整页闸门关着，用页面那句。
 */
@Composable
private fun SettingsPage.strengthRow(
    show: OwnSettings,
    rowsEnabled: Boolean,
    hint: String?,
    edit: (OwnField, (OwnSettingsValues) -> OwnSettingsValues) -> Unit,
): CardRow =
    CardRow.Custom(visible = show.vibrate) { position ->
        SliderRow(
            position = position,
            spec =
                SliderSpec(
                    title = I18n.get(Strings.HOME_VIBRATE_STRENGTH_TITLE),
                    icon = PageIcons.vibrateLevel(show.vibrateStrength),
                    min = GlobalConfig.VIBRATE_WEAK,
                    max = GlobalConfig.VIBRATE_STRONG,
                    value = show.vibrateStrength,
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

/**
 * 「提示文案」那一行 —— 文案只在「执行时提示」不是「不提示」时出现。
 *
 *  菜单那一行（四档）由 [noticeModeRow] 画：常规页与这一页共用同一个形状，
 *  「流体云能不能选」也由那一份按「使用 ColorOS 接口」判。
 *
 * @param show 这一档自己那份 —— 值、可见性（`show.noticeMode`）都从它取，与写入同源
 *   （见 [OwnSettingsCard] 那段说明）。
 */
@Composable
private fun SettingsPage.toastTemplateRow(
    show: OwnSettings,
    rowsEnabled: Boolean,
    ownHint: String?,
    onEditTemplate: () -> Unit,
): CardRow =
    CardRow.Item(
        title = I18n.get(Strings.HOME_TOAST_TEMPLATE_TITLE),
        //  这一句**不给兜底**：里面带 `$`，兜底审计是按源码字面量逐字比的。
        summary = show.toastTemplate.ifBlank { I18n.get(Strings.HOME_TOAST_TEMPLATE_DEFAULT) },
        icon = PageIcons.ToastTemplate,
        arrow = true,
        enabled = rowsEnabled,
        disabledHint = ownHint,
        //  「不提示」这一档没有文案可改 —— 与公共页那条同一条口径
        visible = show.noticeMode != NoticeMode.NONE,
        onClick = onEditTemplate,
    )

/** 三档强度的说法（弱 / 中 / 强）—— 与公共页同一个映射，越界落到「中」。 */
private fun strengthLabel(level: Int): String =
    when (level) {
        GlobalConfig.VIBRATE_WEAK -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_WEAK)
        }

        GlobalConfig.VIBRATE_STRONG -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_STRONG)
        }

        else -> {
            I18n.get(Strings.HOME_VIBRATE_STRENGTH_MEDIUM)
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
        title = I18n.get(Strings.HOME_TOAST_TEMPLATE_TITLE),
        label = I18n.get(Strings.HOME_TOAST_TEMPLATE_PLACEHOLDER),
        hint = I18n.get(Strings.HOME_TOAST_TEMPLATE_HINT),
        //  与公共页那一支同一个清单（变量名单独排一档，顺序由 `ToastNotice.PLACEHOLDERS` 定）
        hintChips = ToastNotice.PLACEHOLDERS,
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
