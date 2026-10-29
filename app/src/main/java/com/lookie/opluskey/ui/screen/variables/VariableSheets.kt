package com.lookie.opluskey.ui.screen.variables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.VarType
import com.lookie.opluskey.core.model.VariableName
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.IconInputView
import com.lookie.opluskey.ui.InputSpec
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.PageSheet
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.SHEET_TITLE_MARGIN
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SheetAction
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.sheetCardColors
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ToggleLeft
import io.github.lyxnx.compose.ui.tablericons.outline.ToggleRight
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

/** 新建表单里「初始值」一格最多多少字 —— 比名字宽一档（值可能是「一段话」）。 */
private const val VAR_VALUE_MAX = 64

// 「新建变量」那个抽屉与它的表单 —— 页面骨架在 `VariableScreen.kt`。
//
// 拆成两个文件只为让两边各自留在 detekt 的「单文件函数数」上限内（11）：那一页里
//   **布局**与**浮层**本来也是两件事，各自的开关状态互不共享。

/**
 * 新建变量的草稿 —— **提在 [CreateSheet] 这一层**，因为右上角那颗提交键与下面的表单是同一份数据
 * （按钮亮不亮要按它算）。放进 [CreateSheetBody] 里的话，壳那边读不到。
 */
@Stable
private class VarDraft {
    var name by mutableStateOf("")
    var type by mutableStateOf(VarType.STRING)
    var initial by mutableStateOf("")
}

/**
 * **新建变量**抽屉 —— 骨架全在 [PageSheet] 里；表单在 [CreateSheetBody]。
 *
 *  `show` 原样递给壳（**不再 `if (!show) return`**）：一 return 整棵子树就没了，
 *  收起时是硬切，没有退场那一段。
 *
 *  提交键在标题行**右端**（`endAction`）：抽屉只有一个动作时，摆在右上角与「弹出层里的确定键」
 *  同一个位置，比在表单末尾再占一整行更像这一层的收尾键。
 */
@Composable
internal fun SettingsPage.CreateSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, VarType, String) -> Unit,
) {
    val draft = remember { VarDraft() }
    //  每次打开都是空表单（收起时要留着内容播退场，所以在这里按「打开」重置，不在包里按 `show`）
    LaunchedEffect(show) {
        if (show) {
            draft.name = ""
            draft.type = VarType.STRING
            draft.initial = ""
        }
    }
    PageSheet(
        show = show,
        title = I18n.get(Strings.VARIABLES_ADD, "新建变量"),
        onDismiss = onDismiss,
        endAction = {
            //  **先错哪条说哪条**：名字空着 → 提醒填；名字不合规 → 说规则；初始值不合类型 → 说类型。
            //  与表单里那两句红字同一份文案（同一个键），两处不会各说一套。
            val blocked =
                when {
                    draft.name.isBlank() -> {
                        I18n.get(Strings.VAR_NAME_HINT, "请输入名称")
                    }

                    !VariableName.isValid(draft.name) -> {
                        I18n.get(Strings.VAR_NAME_INVALID, "不能含空格与 = + - ! $ : { } 这些符号，最多 32 个字")
                    }

                    draft.type.coerce(draft.initial) == null -> {
                        I18n.get(Strings.VAR_VALUE_INVALID, "这个值不符合所选类型")
                    }

                    else -> {
                        null
                    }
                }
            //  一颗**图标按钮**（对勾），不是文字键：这一层只有一个动作，图标省掉一行字的宽度。
            //  灰着的时候点了要说清为什么 —— 那件事（含压暗与原因）在 [SheetAction] 里，
            //  这里只把「为什么不能点」交出去。
            SheetAction(
                icon = PageIcons.Confirm,
                description = I18n.get(Strings.VARIABLES_CREATE, "新建"),
                onClick = { onCreate(draft.name.trim(), draft.type, draft.initial) },
                hint = blocked,
            )
        },
    ) {
        CreateSheetBody(draft)
    }
}

/**
 * 新建表单：**名称 / 类型 / 初始值**三块（每块自带小标题）。
 *
 *  类型**建完就不能改**（它是身份，不是内容，见 `VarType` 的类注释）—— 所以这一处是唯一的
 *  类型入口，之后没有任何地方能改它。
 *
 *  三块之间靠 [SectionTitle] 分隔而不是靠行距：抽屉里这三样各是一件事，只留白分不出
 *  「哪一行属于哪一块」（小标题自带上下间距，块之间不再补 `Spacer`）。
 *
 *  提交键不在这里 —— 它在标题行右端（见 [CreateSheet] 的 `endAction`）；状态也在那一层，
 *  这一支只负责把草稿画出来。
 */
@Composable
private fun SettingsPage.CreateSheetBody(draft: VarDraft) {
    val nameOk = VariableName.isValid(draft.name)
    val initialOk = draft.type.coerce(draft.initial) != null

    Column(Modifier.fillMaxWidth()) {
        SectionTitle(I18n.get(Strings.VAR_FIELD_NAME, "名称"), insideMargin = SHEET_TITLE_MARGIN)
        //  与「自定义命令」页那个「名称」同一颗字形（`label-important`）。这一格**不加左右内边距**：
        //  抽屉的 `insideMargin` 已经给过一档，再加一档真机上就是「输入框比卡片宽出一截」。
        IconInputView(
            spec = InputSpec(PageIcons.LabelImportant, I18n.get(Strings.VAR_NAME_HINT, "请输入名称"), VariableName.MAX_LENGTH),
            value = draft.name,
            onValueChange = { draft.name = it },
            errorMessage =
                if (draft.name.isNotEmpty() &&
                    !nameOk
                ) {
                    I18n.get(Strings.VAR_NAME_INVALID, "不能含空格与 = + - ! $ : { } 这些符号，最多 32 个字")
                } else {
                    ""
                },
        )
        SectionTitle(I18n.get(Strings.VAR_FIELD_INITIAL, "初始值"), insideMargin = SHEET_TITLE_MARGIN)
        //  布尔只有真假两个取值：给输入框是让人手打 `true` / `false`，打错了才提示一句
        //  「这个值不符合所选类型」；摆成菜单之后没有可打错的地方
        if (draft.type == VarType.BOOLEAN) {
            BoolPicker(draft.initial) { draft.initial = it }
        } else {
            IconInputView(
                spec = InputSpec(PageIcons.VarInitial, I18n.get(Strings.VAR_INITIAL_HINT, "请输入初始值"), VAR_VALUE_MAX),
                value = draft.initial,
                onValueChange = { draft.initial = it },
                errorMessage = if (draft.initial.isNotEmpty() && !initialOk) I18n.get(Strings.VAR_VALUE_INVALID, "这个值不符合所选类型") else "",
            )
        }
        SectionTitle(I18n.get(Strings.VAR_FIELD_TYPE, "类型"), insideMargin = SHEET_TITLE_MARGIN)
        TypePicker(draft.type) { picked ->
            draft.type = picked
            //  换档时把已经写了一半的初值按新档归一；归一不了就落到新档的空值 ——
            //  布尔例外，落 `false`：它只有两个取值，留空的话这一格永远是「还没填对」
            draft.initial = picked.coerce(draft.initial) ?: if (picked == VarType.BOOLEAN) VarType.TEXT_FALSE else ""
        }
    }
}

/**
 * 布尔那一档的**取值** —— 一行「当前值 + 选择器字形」+ 一层 `True` / `False` 菜单。
 *
 *  这一格原先与其他两档共用一个输入框：布尔只认 `true` / `false`（见 `VarType.coerce`），
 *  而界面上显示的是 `True` / `False` —— 让用户手打那两个小写英文词，打错了才知道不行。
 *  菜单里**只出现在界面上用的那两个写法**，存储用的 `true` / `false` 根本不出现。
 *
 * @param value 当前写法（存的是 `true` / `false`，见 [VarType.TEXT_TRUE]）
 */
@Composable
private fun SettingsPage.BoolPicker(
    value: String,
    onPick: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val anchor = rememberPreciseClickState()
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    title = boolLabel(value),
                    icon = boolIcon(value),
                    trailing = { RowEndIcon(PageIcons.Selector) },
                    modifier = Modifier.preciseClickAnchor(anchor),
                    onClick = { menu = true },
                ),
            ),
        margin = 0.dp,
        colors = sheetCardColors(),
    )
    PageMenu(
        entries =
            if (menu) {
                //  顺序恒为 True → False：按当前值排的话，点一次两项就换位置，第二次点会点错
                listOf(VarType.TEXT_TRUE, VarType.TEXT_FALSE).map { candidate ->
                    MenuEntry.Row(
                        title = boolLabel(candidate),
                        icon = boolIcon(candidate),
                        selected = candidate == value,
                    ) {
                        onPick(candidate)
                        menu = false
                    }
                }
            } else {
                emptyList()
            },
        preciseClick = anchor,
        onDismiss = { menu = false },
    )
}

/** 布尔的显示名：存的是 `true` / `false`，界面上是 `True` / `False`。 */
private fun boolLabel(value: String): String =
    if (value == VarType.TEXT_TRUE) {
        I18n.get(Strings.VAR_VALUE_TRUE, "True")
    } else {
        I18n.get(Strings.VAR_VALUE_FALSE, "False")
    }

/** 布尔的图标：开 / 关各一颗，与类型那一格的 `toggle-left` 同一族。 */
private fun boolIcon(value: String): ImageVector =
    if (value == VarType.TEXT_TRUE) TablerIcons.Outline.ToggleRight else TablerIcons.Outline.ToggleLeft

/**
 * 类型那一格 —— 一行「当前档 + 选择器字形」+ 一层菜单。
 *
 *  摆成菜单而不是三行并列：类型只有一个取值，三行会把名字 / 初始值那两块挤下去；
 *  菜单里三档各有自己的图标（[typeIcon]），一眼就能认出哪一档是哪个。
 *
 *  这一行**只说当前是哪一档**（「类型」这三个字在小标题上，不再在这一行重复一遍）。
 *
 *  行尾是 `selector` 而不是右箭头：箭头读作「点进去还有一层」，而这里点开的是**一层菜单**
 *  —— 全项目同一套口径（见 `MenuEntry` 的类注释）。
 */
@Composable
private fun SettingsPage.TypePicker(
    type: VarType,
    onPick: (VarType) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val anchor = rememberPreciseClickState()
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    title = typeLabel(type),
                    icon = typeIcon(type),
                    trailing = { RowEndIcon(PageIcons.Selector) },
                    modifier = Modifier.preciseClickAnchor(anchor),
                    onClick = { menu = true },
                ),
            ),
        margin = 0.dp,
        colors = sheetCardColors(),
    )
    PageMenu(
        entries =
            if (menu) {
                VarType.entries.map { t ->
                    MenuEntry.Row(
                        title = typeLabel(t),
                        icon = typeIcon(t),
                        selected = t == type,
                    ) {
                        onPick(t)
                        menu = false
                    }
                }
            } else {
                emptyList()
            },
        preciseClick = anchor,
        onDismiss = { menu = false },
    )
}

/**
 * 这一页的浮层开关 —— **页面级**（不是组合内的 `remember`）：抽屉关掉之后那一帧还要播退场动画，
 * 换成组合内状态的话，一清空整棵抽屉就没了，退场是硬切。
 *
 *  只剩「新建」一位：点一颗变量的那些操作搬进了「变量操作」页（那里是一整页，不用抽屉）。
 */
internal object VariablePageState {
    /** 「新建变量」抽屉开着没有。 */
    var creating: Boolean by mutableStateOf(false)
}
