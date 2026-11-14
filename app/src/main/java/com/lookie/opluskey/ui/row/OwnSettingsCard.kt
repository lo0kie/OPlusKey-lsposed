package com.lookie.opluskey.ui.row

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
 * ## 关着时显示**生效值**，不是这一档自己存的那份
 *
 *  这一点原先写错过：卡片直接读 `owner` 上的那几个值，而总开关关着时它们**不参与取值**。
 *  于是「公共开了振动、这颗键自己那份还是默认的关」时，这一页显示「振动反馈：关（灰）」——
 *  而按下去明明会振。同一页下半的「组合按键设置」几行一直读的是生效值
 *  （`comboWindowFor` / `comboHoldMinFor`），只有上面这张卡不一样。
 *
 *  所以显示统一走 [effective]（`Config.effectiveFor(owner)`），**写入仍落到** [owner]：
 *  关着时看到的就是「现在实际在用的那份」，打开总开关后同一处立刻变成这一档自己的值。
 *  总开关本身与「哪几行能不能点」仍按 [owner] 判 —— 那是「算不算数」，不是「是什么样」。
 *
 * ## 例外：**滑杆显示的是这一档自己的值**，不跟总开关跳
 *
 *  开关那一档必须走生效值（「关着但实际会振」就是骗人），滑杆不行 —— 拨一下总开关，
 *  滑杆会从公共值跳到这一档自己的值，读起来像「我的强度被改了」（用户口径「切换开关
 *  slider 的值会变」）。而滑杆在总开关关着时本来就是灰的、拖不动，显示生效值没有行动价值；
 *  它的写入落点也正是 [owner]，**显示与落点同源**才不会有这一跳。
 *
 *  于是 [strengthRow] 的滑杆值取 [owner]，只有「这一行出不出现」仍按生效值判
 *  （否则公共开着振动、这一档没设过时这一行会凭空消失）。
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
 *  [enabled] 是页面闸门（模块关 / 链路未通 / 只读），它一并管住总开关自己。
 *
 * @param owner 这一档当前的独立设置（只读；改由 [onWrite] 交回去）
 * @param enabled 页面闸门 —— 关着时三张卡都不吃点击
 * @param titles 三张卡各自的小标题（见 [OwnCardTitles]）
 * @param onWrite 改完之后的新值 + **这次改的是哪一项**（后者决定调用方只覆盖哪一格，
 *   见 [OwnField]），调用方负责落到自己那段配置上
 */
@Composable
internal fun SettingsPage.OwnSettingsCard(
    owner: OwnSettings,
    /** **生效**那一份（`Config.effectiveFor(owner)`）—— 只用于显示，写入仍落到 [owner]。 */
    effective: OwnSettings,
    enabled: Boolean,
    titles: OwnCardTitles,
    onWrite: (OwnSettingsValues, OwnField) -> Unit,
) {
    var editingTemplate by remember { mutableStateOf(false) }
    //  一个小标题带一张卡（与「按键设置」页同构）。间距由 `SectionTitle` 自带，不再自己补间距。
    //  三张卡的标题与「按键设置」页**逐字相同**（同一个字典键）：两页读到的是一套分类，
    //  只是这一页改的是「这一档自己那份」。总开关那张是这一页独有的，所以单独给了个标题。
    val rowsEnabled = enabled && owner.useOwnSettings
    //  灰着时点一下该说的话：整页闸门关着 ⇒ `null`（用整页那句：主开关 / 链路 / 只读）；
    //  整页没事、只是这颗总开关没开 ⇒ 说清该先打开哪一项。两张卡共用这一句。
    val ownHint =
        if (enabled && !owner.useOwnSettings) {
            I18n.get(Strings.KEY_SETTINGS_NEED_OWN, "先打开 [使用独立设置]")
        } else {
            null
        }

    // 每一行都基于「当前这份值」改一位再交回去 —— 形状与 `CardRow` 的开关一致。
    // **顺手写下改的是哪一项**：调用方按它只覆盖那一格（见 [OwnField]）。
    //  基值取 [owner]（写入的落点）：`withField` 只取被改的那一格，但 `ownSettingsSeeded`
    //  这类「没被点名」的位要跟着这一档自己的值走，不能跟着公共那份。
    fun edit(
        field: OwnField,
        transform: (OwnSettingsValues) -> OwnSettingsValues,
    ) = onWrite(transform(owner.values()), field)

    SectionTitle(titles.master)
    //  总开关**另起一张卡**：它管的是「下面那几张卡算不算数」，不是「调成什么样」。
    CardList(rows = listOf(useOwnRow(owner, enabled, onWrite)))
    SectionTitle(titles.interfaceTitle)
    CardList(rows = listOf(interfaceRow(effective, rowsEnabled, ownHint, ::edit)))
    SectionTitle(titles.behavior)
    CardList(
        rows =
            buildList {
                add(vibrateRow(effective, rowsEnabled, ownHint, ::edit))
                //  滑杆值取 `owner`（这一档自己的），**不取** `effective` —— 见类注释那段
                //  「滑杆显示的是这一档自己的值」。取生效值的话，拨一下总开关它就从公共值
                //  跳到这一档自己的值，看着像刚调好的强度被改了。
                add(strengthRow(effective, owner.vibrateStrength, rowsEnabled, ownHint, ::edit))
                addAll(toastRows(effective, rowsEnabled, ownHint, ::edit) { editingTemplate = true })
            },
    )
    OwnTemplateDialog(
        //  窗里编辑的是**当前生效**那句：总开关关着时改的虽是这一档自己那份，
        //  但看到的应当与行尾副标题同一个值（否则打开窗先看到一句屏幕上没出现过的文本）。
        initial = effective.toastTemplate,
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
 *  它只吃页面闸门 [enabled]：下面那些行还要再吃它自己（关着就是跟随公共设置）。
 */
@Composable
private fun SettingsPage.useOwnRow(
    owner: OwnSettings,
    enabled: Boolean,
    onEdit: (OwnSettingsValues, OwnField) -> Unit,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.SETTING_USE_OWN, "使用独立设置"),
        //  「等」字是刻意的：组合在这一档上还多几项（按键之间的等待 / 首键按住时长 / 场景时机），
        //  一句只列三项会让「想单独调这一组的窗口」的人以为跟这颗开关无关。
        summaryOn = I18n.get(Strings.SETTING_USE_OWN_ON, "接口、振动、提示等分别设置"),
        summaryOff = I18n.get(Strings.SETTING_USE_OWN_OFF, "接口、振动、提示等跟随公共设置"),
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
 * @param show **生效**那一份 —— 每一行显示的值都从它取（见 [OwnSettingsCard] 那段说明）
 */
@Composable
private fun SettingsPage.interfaceRow(
    show: OwnSettings,
    rowsEnabled: Boolean,
    ownHint: String?,
    edit: (OwnField, (OwnSettingsValues) -> OwnSettingsValues) -> Unit,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.HOME_COLOROS_TITLE, "使用 ColorOS 接口"),
        summaryOn = I18n.get(Strings.HOME_COLOROS_DESC_ON, "部分命令走 ColorOS 专属接口"),
        summaryOff = I18n.get(Strings.HOME_COLOROS_DESC_OFF, "部分命令改用 AOSP 标准接口"),
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
        title = I18n.get(Strings.HOME_VIBRATE_TITLE, "振动反馈"),
        summaryOn = I18n.get(Strings.HOME_VIBRATE_DESC_ON, "每次执行动作振动一次"),
        summaryOff = I18n.get(Strings.HOME_VIBRATE_DESC_OFF, "执行动作时不振动"),
        icon = PageIcons.Vibrate,
        checked = show.vibrate,
        enabled = rowsEnabled,
        disabledHint = ownHint,
    ) {
        edit(OwnField.VIBRATE) { v -> v.copy(vibrate = it) }
    }

/**
 * 振动强度那一行 —— 只在振动开着时出现（关掉振动还摆一根强度滑杆，
 * 等于让人去调一个当前不起作用的东西；与公共页那一行同一条口径）。
 *
 * 规格与公共页那一行同一套（三档、行首随档换字形、行尾写「弱 / 中 / 强」）。
 *
 * @param show 显示用的那一份（生效值）—— 只用来判「这一行出不出现」：
 *   按这一档自己那份判的话，公共开着振动、这一档没设过时这一行会凭空消失。
 * @param value 滑杆上那个值 —— **这一档自己的**（`owner.vibrateStrength`），不是 [show] 里的。
 *   取生效值会让它跟着总开关跳（见 [OwnSettingsCard] 的「滑杆显示的是这一档自己的值」）。
 * @param hint 灰着被点一下时的说法 —— 交给 `SliderSpec.disabledHint`（投递在 [SliderRow]
 *   那一层，所有滑杆共用一条通道）；`null` = 整页闸门关着，用页面那句。
 */
@Composable
private fun SettingsPage.strengthRow(
    show: OwnSettings,
    value: Int,
    rowsEnabled: Boolean,
    hint: String?,
    edit: (OwnField, (OwnSettingsValues) -> OwnSettingsValues) -> Unit,
): CardRow =
    CardRow.Custom(visible = show.vibrate) { position ->
        SliderRow(
            position = position,
            spec =
                SliderSpec(
                    title = I18n.get(Strings.HOME_VIBRATE_STRENGTH_TITLE, "振动强度"),
                    summary = I18n.get(Strings.HOME_VIBRATE_STRENGTH_DESC, "命令触发时振动的力度"),
                    icon = PageIcons.vibrateLevel(value),
                    min = GlobalConfig.VIBRATE_WEAK,
                    max = GlobalConfig.VIBRATE_STRONG,
                    value = value,
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
 * 「执行时提示 + 文案」那两行 —— 文案只在提示开着时出现。
 *
 * @param show 显示用的那一份（生效值）—— 同 [strengthRow]：可见性也按它判。
 */
@Composable
private fun SettingsPage.toastRows(
    show: OwnSettings,
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
            checked = show.toastOnRun,
            enabled = rowsEnabled,
            disabledHint = ownHint,
        ) {
            edit(OwnField.TOAST) { v -> v.copy(toastOnRun = it) }
        },
        CardRow.Switch(
            title = I18n.get(Strings.HOME_TOAST_FLUID_TITLE, "用流体云提示"),
            summaryOn = I18n.get(Strings.HOME_TOAST_FLUID_DESC_ON, "显示在状态栏的流体云里"),
            summaryOff = I18n.get(Strings.HOME_TOAST_FLUID_DESC_OFF, "以 Toast 弹出"),
            icon = PageIcons.FormatFluid,
            checked = show.toastFluid,
            //  流体云是 ColorOS 的能力：这一档随「使用 ColorOS 接口」走，关掉之后由执行侧回落 Toast
            //  （判据在 `GestureExec.noticeIfNeeded`，这里只是不让人去调一个当前不生效的开关）。
            enabled = rowsEnabled && show.colorOsApi,
            disabledHint =
                if (show.colorOsApi) {
                    ownHint
                } else {
                    I18n.get(Strings.HOME_TOAST_FLUID_NEEDS_COLOROS, "需要开启「使用 ColorOS 接口」")
                },
            visible = show.toastOnRun,
        ) {
            edit(OwnField.FLUID) { v -> v.copy(toastFluid = it) }
        },
        CardRow.Item(
            title = I18n.get(Strings.HOME_TOAST_TEMPLATE_TITLE, "提示文案"),
            //  这一句**不给兜底**：里面带 `$`，兜底审计是按源码字面量逐字比的。
            summary = show.toastTemplate.ifBlank { I18n.get(Strings.HOME_TOAST_TEMPLATE_DEFAULT) },
            icon = PageIcons.ToastTemplate,
            arrow = true,
            enabled = rowsEnabled,
            disabledHint = ownHint,
            visible = show.toastOnRun,
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
        //  与公共页那一支同一个清单（变量名画成名字框，顺序由 `ToastNotice.PLACEHOLDERS` 定）
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

/** 把这份值落到一颗键上 —— 六个字段同名同义，两处 `copy` 只是表不同。 */
internal fun KeyConfig.withOwn(values: OwnSettingsValues): KeyConfig =
    copy(
        useOwnSettings = values.useOwnSettings,
        colorOsApi = values.colorOsApi,
        vibrate = values.vibrate,
        vibrateStrength = values.vibrateStrength,
        toastOnRun = values.toastOnRun,
        toastFluid = values.toastFluid,
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
        toastFluid = values.toastFluid,
        toastTemplate = values.toastTemplate,
        ownSettingsSeeded = values.ownSettingsSeeded,
    )

// 「独立设置」那份值的载体与「按字段合并」的那一小块 —— 这两样东西**不是界面**：
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
internal enum class OwnField { USE_OWN, COLOR_OS, VIBRATE, STRENGTH, TOAST, FLUID, TEMPLATE }

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
        OwnField.FLUID -> copy(toastFluid = src.toastFluid)
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
    override val toastFluid: Boolean,
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
        toastFluid = toastFluid,
        toastTemplate = toastTemplate,
        ownSettingsSeeded = ownSettingsSeeded,
    )
