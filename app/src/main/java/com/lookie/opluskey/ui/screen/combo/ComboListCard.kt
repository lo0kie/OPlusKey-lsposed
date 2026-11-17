package com.lookie.opluskey.ui.screen.combo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.core.present.comboOrderSummary
import com.lookie.opluskey.core.present.comboTitleParts
import com.lookie.opluskey.core.present.keyName
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.MenuState
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.dialog.rememberMenuState
import com.lookie.opluskey.ui.feedback.DisabledStateWithPick
import com.lookie.opluskey.ui.feedback.keyStateLabel
import com.lookie.opluskey.ui.model.icon.DrawnIcons
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.ComboSort
import com.lookie.opluskey.ui.model.page.comboSortLabel
import com.lookie.opluskey.ui.model.page.comboSortSummary
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.SectionTitleClick
import com.lookie.opluskey.ui.row.SectionTitleTrailingMore
import com.lookie.opluskey.ui.row.rememberRowIconColor
import com.lookie.opluskey.ui.state.binding.ComboEditState
import com.lookie.opluskey.ui.state.binding.KeysEditState
import com.lookie.opluskey.ui.write.setComboSort
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.SelectAll
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「按键功能」页下半张卡 —— 组合按键列表、它的筛选标题与筛选菜单。
//
// 从 `KeysScreen.kt` 拆出来：那边是这一页的骨架（两张清单 + 编辑态 + 那几个窗），
// 这一块只回答「有哪几组、按哪颗键筛、每一行写什么」。

/**
 * 「组合按键列表」那一块 —— 小标题 + 那张卡（末行是「添加组合按键」）+ 它那个**筛选菜单**。
 *
 *  小标题在这张卡上方（用户口径）：一列「有哪几档」需要一个名字，滚起来之后顶栏折成一条，
 *  只看行名认不出这一列是什么。
 *
 *  **点小标题弹筛选菜单**（用户口径：按按键筛这一列，单选）—— 小标题是这一列唯一的名字，
 *  把「筛」挂在这儿，比在卡片顶上再占一行更省地方；文字末尾那颗箭头跟着菜单开合转。
 *
 *  菜单的开合、锚点、选中的那颗键（`ComboEditState.waitFilter` / `holdFilter`，两列各一份）全归这一块：
 *  它们是同一件事的三面（「按哪颗键筛」），摆在页面那一层只会让它再多三样不相干的变量。
 */
@Composable
internal fun SettingsPage.ComboListSection(
    cfg: Config,
    writable: Boolean,
    atLimit: Boolean,
    /** `true` = 「按住组合」那一列，`false` = 「等待组合」那一列（两列各有自己的筛选与上限）。 */
    hold: Boolean,
) {
    val filterKey = if (hold) ComboEditState.holdFilter else ComboEditState.waitFilter
    //  排序跟着配置文件走（`GlobalConfig.waitComboSort` / `holdComboSort`）——
    //  换一份配置就换一套显示偏好，与配色 / 语言同一档
    val sort =
        ComboSort.ofCode(if (hold) cfg.global.holdComboSort else cfg.global.waitComboSort)
    val menu = rememberMenuState()
    //  行尾那颗「更多」的菜单（排序）—— 与筛选菜单各一份：一个是「看哪几组」，一个是「怎么排」
    val sortMenu = rememberMenuState()

    Column {
        SectionTitle(
            text = comboListTitle(cfg, filterKey, hold),
            //  锚点挂在**这一行小标题**上（不是整列）：菜单弹在点标题那一点，不与下面的卡片混在一起
            modifier = Modifier.preciseClickAnchor(menu.anchor),
            click = SectionTitleClick.Menu(expanded = menu.open) { menu.show() },
            //  行尾是**「更多」+ 已用 / 上限**：计数与「命令名称」那行同一个 `TitleCounter`，
            //  几何与「命令详情 / 命令步骤」那两个小标题同一份（`SectionTitleTrailingMore`）。
            //  点标题仍是筛选（筛了某颗键时它显示的是那颗键的名字），计数只报「共配了几档 / 上限」，
            //  与筛选无关 —— 否则筛一下就变成「已用 2 / 上限 9」，读起来像丢了几组。
            trailing = {
                SectionTitleTrailingMore(
                    anchor = sortMenu.anchor,
                    onMenu = sortMenu::show,
                    length = cfg.combos.count { it.isHold == hold },
                    max = MAX_COMBOS,
                )
            },
        )
        val rows = comboRows(cfg, writable, filterKey, hold)
        //  「添加组合按键」是这一列的**最后一行**（用户口径：与列表拼在一起，不另起卡）。
        //  一档都没配 / 全被筛掉时这张卡就只剩它一行 —— 仍然只画**一张**卡：
        //  `CardList` 收到空表仍会画一张零行卡片，边框与圆角还在，看着像坏了一块。
        CardList(rows = rows + addComboRow(cfg, writable, atLimit, hold))
    }
    //  行表空着就是「不显示」（与别处 `PageMenu` 的用法一致）
    PageMenu(menu) {
        comboFilterEntries(cfg, filterKey, hold) { key ->
            if (hold) ComboEditState.holdFilter = key else ComboEditState.waitFilter = key
            menu.dismiss()
        }
    }
    comboSortMenu(sortMenu, current = sort) { picked -> setComboSort(hold, picked) }
}

/**
 * 那一列小标题行尾「更多」里的三档排序 —— 形状与「命令步骤」那张菜单同一套
 * （一层菜单 + 一段带标题的候选，`MenuEntry.Section` 负责标题与行距）。
 *
 *  三档是**互斥的显示次序**（[ComboSort]），打勾的是现在这一档；挑完就收。
 *  菜单标题就写「排序」：它是从那一列的小标题点开的，这一层不必再把列名说一遍。
 */
@Composable
private fun SettingsPage.comboSortMenu(
    state: MenuState,
    current: ComboSort,
    onPick: (ComboSort) -> Unit,
) {
    fun row(
        sort: ComboSort,
        icon: ImageVector,
    ): MenuEntry.Row =
        MenuEntry.Row(
            //  说法与副标题都读 `ComboSort` 那一份（菜单与回执共用，见 `comboSortLabel`）
            title = comboSortLabel(sort),
            summary = comboSortSummary(sort),
            icon = icon,
            selected = sort == current,
            onPick = {
                //  先收菜单再写：换排序会整列重排，留着菜单会闪一帧旧序
                state.dismiss()
                onPick(sort)
            },
        )
    PageMenu(state) {
        listOf(
            MenuEntry.Section(
                title = I18n.get(Strings.COMBO_SORT_TITLE),
                rows =
                    listOf(
                        //  「不排序」= 创建顺序：副标题「按创建时间」由 `comboSortSummary` 给
                        row(ComboSort.CREATED, PageIcons.SortByCreated),
                        row(ComboSort.ORDERED_FIRST, PageIcons.SortOrderedFirst),
                        row(ComboSort.UNORDERED_FIRST, PageIcons.SortUnorderedFirst),
                    ),
            ),
        )
    }
}

/**
 * 这张卡的每一行 —— 一档组合一行（**不含**「添加组合按键」，那一行由调用点接在末尾）。
 *
 *  每行的内容在 [comboRow] 里：这一层只负责「按 [ComboSort] 排、筛掉不合的」。
 *  可能一行都不剩。
 *
 *  编辑态（见 [KeysEditState]）里与按键列表同一套：行尾换成多选框、点一行是勾选而不是进编辑页；
 *  「添加组合按键」那一行禁用（编辑态在改「已有哪几组」，不是加一组）。
 */
private fun SettingsPage.comboRows(
    cfg: Config,
    writable: Boolean,
    /** 只看含这颗键的组合（`null` = 全部）—— 行首的「第几组」仍按**排序后整列**里的位置算。 */
    filter: String?,
    /** 这一列是「按住组合」还是「等待组合」（另一类的行不画在这一列里）。 */
    hold: Boolean,
): List<CardRow> =
    buildList {
        val sort =
            ComboSort.ofCode(if (hold) cfg.global.holdComboSort else cfg.global.waitComboSort)
        //  **段内独立编号**（用户口径）：两列各自的「第几组」从 1 数起 ——
        //  按 `cfg.combos` 的全局位次给号，按住那一列会从「等待组合的条数 + 1」开始，
        //  看着像前面缺了好几组（行首那颗数字图标本来就只在告「这是这一列的第几个」）。
        //  编号跟着**改后的次序**走：序号若还按创建顺序给，排完之后行首会读成「3 / 1 / 2」。
        //  `sortedBy` 是稳定排序，同一档里仍是创建先后。
        val column =
            cfg.combos
                .filter { it.isHold == hold }
                .let { list ->
                    when (sort) {
                        ComboSort.CREATED -> list
                        ComboSort.ORDERED_FIRST -> list.sortedBy { if (it.ordered) 0 else 1 }
                        ComboSort.UNORDERED_FIRST -> list.sortedBy { if (it.ordered) 1 else 0 }
                    }
                }
        column.forEachIndexed { index, combo ->
            //  序号**先取**再判筛选：这一列的编号是「它在整列里排第几」，筛掉几行不该让它重排。
            if (filter != null && filter !in combo.keys) return@forEachIndexed
            add(comboRow(cfg, combo, index, writable))
        }
    }

/** 一档组合那一行 —— 点它是进编辑页（编辑态里是勾选）。 */
private fun SettingsPage.comboRow(
    cfg: Config,
    combo: ComboKey,
    index: Int,
    writable: Boolean,
): CardRow.Item {
    val editing = KeysEditState.editing
    val picked = KeysEditState.pickedCombos
    val dead = comboHasMissingKey(cfg, combo)
    //  **跑不起来**：自己关着，或成员里任一键被停用（判据在 `Config.comboOff`，按成员集合算，
    //  多按键组合同一条规则）。行尾那半句与行首那颗禁用图标都读它。
    val off = cfg.comboOff(combo)
    return CardRow.Item(
        title = comboLabel(cfg, combo),
        //  标题由 [ComboTitle] 画（纯文字：`侧键 和 音量 +` / `按住 …后 …`）——
        //  `title` 那一份仍给着，供无障碍 / 其它只取字符串的通道用。
        titleContent = { ComboTitle(cfg, combo, writable) },
        //  **副标题只写一个状态**（用户口径）：停用那一档写「已停用」，其余写顺序
        //  （`comboOrderSummary`：按顺序写「先 A 后 B 再 C」，不分顺序写「不分顺序」）。
        //  两者不拼接 —— 这一格本来就只放一句，拼起来会把标题挤掉一截；而且停用时顺序也无意义。
        //  动作摘要仍然不写：一列下来全是小字、每行还都不一样长，扫一眼只能看见一片灰。
        summary = keyStateLabel(enabled = !off, comboOnly = false) ?: comboOrderSummary(cfg, combo),
        summaryMaxLines = 1,
        //  行首用**第几组**的数字图标（1~9，与 `MAX_COMBOS` 同一档，用户口径）：一列下来能数清现在有几组，
        //  与首页「按键」卡那几颗自定义键同一套写法（`menuKeyNumber`）。
        //  停用的那一档换成禁用图标（与按键列表同一颗、同一个位置）；成员被删掉的那一档
        //  同样是禁用图标（判定那边凑不齐），数字就看不到了 —— 那一组本来就废了。
        icon =
            if (dead || off) {
                PageIcons.Cancel
            } else {
                PageIcons.menuKeyNumber(index)
            },
        enabled = writable,
        //  行尾**只剩编辑态的勾选框**；平时给 `null`，回到 `arrow` 那条默认路径 ——
        //  停用那半句已经写成副标题（与按键列表同一套），不再占行尾那一格。
        trailing =
            when {
                editing -> {
                    {
                        DisabledStateWithPick(
                            picked = combo.id in picked,
                            enabled = writable,
                            //  组合没有「只参与非首键的按住组合」这一位（那一位只属于单键），
                            //  停用那半句已经在副标题里，这一格只画勾选框。
                            state = null,
                        )
                    }
                }

                else -> {
                    null
                }
            },
        arrow = !editing,
        onClick = {
            if (editing) {
                KeysEditState.toggleCombo(combo.id)
            } else {
                XLog.w("组合键：进编辑 ${combo.id}")
                ComboEditState.open(combo.id)
                navTo(Screen.COMBO_EDIT)
            }
        },
    )
}

/**
 * 组合行标题 —— **一行纯文字**：`侧键 和 音量 +` / `侧键按住后 音量 +`。
 *
 *  那句话由 [comboTitleParts] 分段给：两档都**不区分顺序**（等待档「和」、按住档「后」），
 *  按不按先后写在那一行的副标题里（`comboOrderSummary`）。**换行交给文本自己**：
 *  装不下才折行、再不行省略号。
 *
 *  ## 只有连接词小一号
 *
 *  键名按标题那一档画，`按住` / `后` / `和` 这些**不是名字的字**缩到 [COMBO_CONNECTOR_SCALE]：
 *  一列下来眼睛先扫到的是几颗键的名字，连接词退成一层薄薄的语法（用户口径）。
 *  比例**乘在标题自己的字号上**，所以适配页面的字号缩放与系统字号：写死一个 sp 值，
 *  放大字号之后连接词会显得越来越小。
 */
@Composable
private fun SettingsPage.ComboTitle(
    cfg: Config,
    combo: ComboKey,
    writable: Boolean,
) {
    //  两档标题色带过渡：整页闸门一拨，这一列与同一页其它行一起变灰 / 恢复，别只有这里硬切。
    //  取色走全项目那一份（`rememberRowIconColor`：启用色 / 禁用色两档 + 统一过渡），
    //  不在这一页再抄一遍两色。
    val color by rememberRowIconColor(enabled = writable, label = "ComboTitle")
    val style = COUITheme.textStyles.headline1
    val connectorSize =
        style.fontSize
            .takeIf { it.isSp }
            ?.let { it * COMBO_CONNECTOR_SCALE }
            ?: TextUnit.Unspecified
    //  分段拼：键名照原样，连接词套一段小字号。**不给段之间留空** —— 空格已经写在连接词那一段里，
    //  再插一次会让「侧键按住后 …」里每处都多一个空格。
    val text =
        buildAnnotatedString {
            comboTitleParts(cfg, combo).forEach { part ->
                if (part.isKeyName) {
                    append(part.text)
                } else {
                    withStyle(SpanStyle(fontSize = connectorSize)) { append(part.text) }
                }
            }
        }
    BasicText(
        text = text,
        style = style.copy(color = color, fontWeight = FontWeight.Medium),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 连接词相对标题字号的缩放 —— 只退一小档：再往下（0.85 及以下）真机上读着偏碎，与名字脱成两层。 */
private const val COMBO_CONNECTOR_SCALE = 0.95f

/** 「添加组合按键」—— 清单的**最后一行**（与清单同卡）；编辑态里**禁用**而不是退场（与「添加按键」同一口径）。 */
private fun SettingsPage.addComboRow(
    cfg: Config,
    writable: Boolean,
    atLimit: Boolean,
    hold: Boolean,
): CardRow.Item {
    val editing = KeysEditState.editing
    //  组合要**两颗键**：只有内置侧键、又一组都没配过时这一行按不动 —— 选键弹窗里只有一行，
    //  根本配不出来（与首页「按键操作」卡那一行同一条判据）。
    //  **已经配过的那几组要放行**：收着的自定义键删光之后它们成了废配置，删除入口在列表里，
    //  这里也禁掉的话用户就再也删不掉了。
    val canCombo = cfg.customKeys.isNotEmpty() || cfg.combos.isNotEmpty()
    val needKey = I18n.get(Strings.COMBO_NEED_KEY)
    return CardRow.Item(
        //  两列各一颗：这一行加出来的那一档，类别在点下去那一刻就定了（见 `ComboEditState.pendingKind`）
        title =
            if (hold) {
                I18n.get(Strings.COMBO_ADD_HOLD)
            } else {
                I18n.get(Strings.COMBO_ADD_WAIT)
            },
        icon = PageIcons.MenuKeyAdd,
        enabled = writable && !atLimit && canCombo && !editing,
        //  这一行自己的原因：「只有一颗键」>「满员」>「编辑态」（编辑态在删、不在加，
        //  但行要一直在，用户才知道这一列还能再添）
        disabledHint =
            when {
                !canCombo -> needKey
                atLimit -> I18n.format(Strings.COMBO_LIMIT, MAX_COMBOS)
                editing -> I18n.get(Strings.KEYS_EDIT_NO_ADD)
                else -> null
            },
        onClick = {
            ComboEditState.pendingKind = if (hold) ComboKey.MODE_HOLD else ComboKey.MODE_WAIT
        },
    )
}

// 「组合按键列表」那块小标题与它的筛选菜单。
//
// 卡片里那一列回答的是「有哪几组、每行写什么」；这一份回答的是「这一列叫什么、按哪颗键筛」。
// 两件事共用同一张卡的位置，但一个是行、一个是菜单。

/**
 * 列表标题 —— 筛着的时候把「在看哪颗键」接在后面（`组合按键列表 - 侧键`）。
 *
 *  不写的话，从别处回到这一页只会看到一张短了的表：不知道那是筛出来的，第一反应是「组合丢了」。
 *  连接符用半角连字符（用户口径），与页内别处的「键 - 手势」同一套写法。
 *
 *  **键名不套方框**（用户口径：只有一个键名时不要框起来）：这一行后面只跟一个名字，
 *  前面那个 ` - ` 已经把它与标题分开了，再套一对 `[ ]` 只是多一层噪声。
 *  两颗以上连起来那一档（`[ 侧键 ]和[ 电量 + ]`）仍然要框 —— 那里框是为了把两个名字分开。
 */
internal fun SettingsPage.comboListTitle(
    cfg: Config,
    filter: String?,
    hold: Boolean,
): String {
    val base =
        if (hold) {
            I18n.get(Strings.COMBO_SECTION_HOLD)
        } else {
            I18n.get(Strings.COMBO_SECTION_WAIT)
        }
    val key = filter?.let { cfg.keyIdOf(it) } ?: return base
    return "$base - ${keyName(key)}"
}

/**
 * 筛选菜单 —— 「全部」+ **出现在组合里**的每一颗键（单选，打勾的是当前那一档）。
 *
 *  只列被某组用到的键：一颗谁都还没用上的键，选了只会得到一张空表。
 *  行首图标与首页「按键列表」同一套（侧键那颗 / 第几颗自定义键），两处读起来是同一种东西；
 *  **顺序也按那套**（侧键在前、自定义键按收集顺序）—— 不排序的话是「组合里第一次出现的次序」。
 *
 * @param onPick `null` = 不筛（「全部」那一行）
 */
internal fun SettingsPage.comboFilterEntries(
    cfg: Config,
    filter: String?,
    hold: Boolean,
    onPick: (String?) -> Unit,
): List<MenuEntry> =
    buildList {
        //  小标题**在最顶上**：这一张菜单整体就是「按按键筛这一列」，先给它一个名字，
        //  下面才是「全部」与那几颗键。不留标题的话，一列裸选项看不出它们是一件事。
        //
        //  顺序仍按**首页「按键列表」那一套**：侧键在前，自定义键按收集顺序（即「第几颗」的号）。
        //  直接拿 `combos.flatMap { it.keys }.distinct()` 的次序是「在组合表里第一次出现的顺序」，
        //  真机上就成了「侧键 / 1 / 3 / 2」这种看不懂的序 —— 那不是任何一处列表的顺序。
        add(
            MenuEntry.Section(
                title = I18n.get(Strings.COMBO_FILTER_SECTION),
                rows =
                    buildList {
                        //  「不筛」那一行也在这一组里（它同样是「按按键筛」的一个档位）；
                        //  与下面真正的键之间用一条**加深带**断开 —— 见下一行那个 `dividerBefore`。
                        add(
                            MenuEntry.Row(
                                title = I18n.get(Strings.COMBO_FILTER_ALL),
                                //  这一行的意思是「不筛」（与「已保存的命令」那张菜单同一颗 `select-all`）
                                icon = TablerIcons.Outline.SelectAll,
                                selected = filter == null,
                            ) { onPick(null) },
                        )
                        //  只列**这一列**用到的键：另一类组合里的键在这一列筛出来只会是空表
                        cfg.combos
                            .filter { it.isHold == hold }
                            .flatMap { it.keys }
                            .distinct()
                            .mapNotNull { cfg.keyIdOf(it) }
                            .sortedBy { key -> cfg.customKeys.indexOfFirst { it.id == key.id } }
                            .forEachIndexed { index, key ->
                                add(
                                    MenuEntry.Row(
                                        title = keyName(key),
                                        icon = keyFilterIcon(cfg, key),
                                        selected = filter == key.id,
                                        //  第一颗键与上面那行「全部」之间来一条加深带
                                        dividerBefore = index == 0,
                                    ) { onPick(key.id) },
                                )
                            }
                    },
            ),
        )
    }

/** 筛选菜单里那颗键的行首图标 —— 与首页「按键列表」同一套（侧键那颗 / 第几颗自定义键）。 */
internal fun keyFilterIcon(
    cfg: Config,
    key: KeyId,
): ImageVector =
    if (key.isCustom) {
        PageIcons.menuKeyNumber(cfg.customKeys.indexOfFirst { it.id == key.id }.coerceAtLeast(0))
    } else {
        DrawnIcons.MenuSideKey
    }
