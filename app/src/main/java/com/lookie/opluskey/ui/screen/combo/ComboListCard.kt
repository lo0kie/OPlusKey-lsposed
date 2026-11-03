package com.lookie.opluskey.ui.screen.combo

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SectionTitleClick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.keyBrackets
import com.lookie.opluskey.ui.state.ComboEditState
import com.lookie.opluskey.ui.state.KeysEditState
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 组合标题里分隔符两侧的间距 —— 与 `ROW_TITLE_EXTRA_GAP` 同一档，贴着两列不挤。 */
private val COMBO_PAIR_GAP = 6.dp

/** 两列标题里第一列最多占整行的多少 —— 再长的名字也只在自己那格里省略。 */
private const val COMBO_FIRST_COLUMN_MAX_RATIO = 0.45f

// 「按键功能」页下半张卡 —— 组合按键列表、它的筛选标题与筛选菜单。
//
// 从 `KeysScreen.kt` 拆出来：那边是这一页的骨架（两张清单 + 编辑态 + 那几个窗），
// 这一块只回答「有哪几组、按哪颗键筛、每一行写什么」。

/**
 * 「组合按键列表」那一块 —— 小标题 + 那张卡 + 它那个**筛选菜单**。
 *
 *  小标题在这张卡上方（用户口径）：一列「有哪几档」需要一个名字，滚起来之后顶栏折成一条，
 *  只看行名认不出这一列是什么。
 *
 *  **点小标题弹筛选菜单**（用户口径：按按键筛这一列，单选）—— 小标题是这一列唯一的名字，
 *  把「筛」挂在这儿，比在卡片顶上再占一行更省地方；文字末尾那颗箭头跟着菜单开合转。
 *
 *  菜单的开合、锚点、选中的那颗键（`ComboEditState.filterKey`）全归这一块：
 *  它们是同一件事的三面（「按哪颗键筛」），摆在页面那一层只会让它再多三样不相干的变量。
 */
@Composable
internal fun SettingsPage.ComboListSection(
    cfg: Config,
    writable: Boolean,
    atLimit: Boolean,
) {
    val filterKey = ComboEditState.filterKey
    var menu by remember { mutableStateOf(false) }
    val anchor = rememberPreciseClickState()

    Column(Modifier.preciseClickAnchor(anchor)) {
        SectionTitle(
            text = comboListTitle(cfg, filterKey),
            click = SectionTitleClick.Menu(expanded = menu) { menu = true },
        )
        //  两列的宽度**整列量一次**（见 [comboPairWidths]）：每行各量各的就会一行一个宽度。
        CardList(rows = comboRows(cfg, writable, atLimit, filterKey, comboPairWidths(cfg)))
    }
    //  行表空着就是「不显示」（与别处 `PageMenu` 的用法一致）
    PageMenu(
        entries =
            if (menu) {
                comboFilterEntries(cfg, filterKey) { key ->
                    ComboEditState.filterKey = key
                    menu = false
                }
            } else {
                emptyList()
            },
        preciseClick = anchor,
        onDismiss = { menu = false },
    )
}

/**
 * 这张卡的每一行 —— 一档组合一行，末行「添加组合键」。
 *
 *  每行的内容在 [comboRow] / [addComboRow] 里：这一层只负责「筛掉不合的、按顺序摆」。
 *
 *  编辑态（见 [KeysEditState]）里与按键列表同一套：行尾换成多选框、点一行是勾选而不是进编辑页；
 *  末行「添加组合按键」整行退场（编辑态在改「已有哪几组」，不是加一组）。
 */
private fun SettingsPage.comboRows(
    cfg: Config,
    writable: Boolean,
    atLimit: Boolean,
    /** 只看含这颗键的组合（`null` = 全部）—— 行首的「第几组」仍按**全部**里的位置算。 */
    filter: String?,
    /** 两列标题与分隔符的宽度 —— 整列同一个数，见 [comboPairWidths]。 */
    widths: ComboPairWidths,
): List<CardRow> =
    buildList {
        cfg.combos.forEachIndexed { index, combo ->
            if (filter != null && filter !in combo.keys) return@forEachIndexed
            add(comboRow(cfg, combo, index, writable, widths))
        }
        add(addComboRow(cfg, writable, atLimit))
    }

/** 一档组合那一行 —— 点它是进编辑页（编辑态里是勾选）。 */
private fun SettingsPage.comboRow(
    cfg: Config,
    combo: ComboKey,
    index: Int,
    writable: Boolean,
    widths: ComboPairWidths,
): CardRow.Item {
    val editing = KeysEditState.editing
    val picked = KeysEditState.pickedCombos
    val dead = comboHasMissingKey(cfg, combo)
    return CardRow.Item(
        title = comboLabel(cfg, combo, bracketed = true),
        //  标题按**两列**画（见 [ComboPairTitle]）：拼成一整串时，分隔符落在哪由前一个名字的
        //  长短决定，一列下来每行都错开（真机反馈「单独某个名字过长时全部都没对齐」）。
        titleContent = { ComboPairTitle(cfg, combo, writable, widths) },
        //  副标题说**这一档开没开**（用户口径：与首页「按键列表」一致，两档都写一句，
        //  列表才不会一行高一行矮，而且「停用」不再只靠颜色区分）。
        //  动作摘要（`短按：手电筒 · 长按：…`）仍然不写：一列下来全是小字、每行还都不
        //  一样长，扫一眼只能看见一片灰 —— 两档动作进页面看。
        summary = comboStateSummary(combo.enabled),
        //  行首用**第几组**的数字图标（1~5，与 `MAX_COMBOS` 同一档，用户口径）：一列下来能数清现在有几组，
        //  与首页「按键」卡那几颗自定义键同一套写法（`menuKeyNumber`）。
        //  停用的那一档换成禁用图标（与按键列表同一颗、同一个位置）；成员被删掉的那一档
        //  同样是禁用图标（判定那边凑不齐），数字就看不到了 —— 那一组本来就废了。
        icon =
            if (dead || !combo.enabled) {
                PageIcons.Cancel
            } else {
                PageIcons.menuKeyNumber(index)
            },
        enabled = writable,
        //  编辑态：行尾换多选框（勾选框自己不挂点击 —— 整行都是它的点击区）
        trailing =
            if (editing) {
                {
                    Checkbox(
                        state = if (combo.id in picked) ToggleableState.On else ToggleableState.Off,
                        onClick = null,
                        enabled = writable,
                    )
                }
            } else {
                null
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
 * 组合行标题 —— **两列**：`[侧键]` ｜ `×` ｜ `[电量 +]`。
 *
 *  两列各取**整列里最长那个名字**的宽度（[comboPairWidths]，全列表同一份），于是每行的键名起点、
 *  第二个键名都竖着对齐：两列各占一半时短名字后面会空出一大片（真机反馈「这两个名字隔得也太远了」），
 *  而每行各按自己的长短排又会「单独某个名字过长时全部都没对齐」—— 定宽是这两条反馈之间那个落点。
 *  名字再长也只在自己那一列省略号，不会把后面那列挤走。
 *
 *  ## 分隔符摆在哪
 *
 *  第一列是**按整列最长的那个名字**定宽的，短名字那一行就会在自己这格的右边留下一段空 ——
 *  直接把 `×` 接在格子后面的话，左边（名字 → `×`）比右边（`×` → 第二个键名）宽一截
 *  （真机反馈「`×` 两边的留白不一样」）。
 *
 *  所以 `×` 摆在**那段空的正中间**：两边各让一半，两个键名到 `×` 的距离相等。
 *  第二个键名的起点不变（格宽 + 分隔符那一段的总宽没变），两列的对齐因此一点没动 ——
 *  变位置的只有 `×` 自己。
 *
 *  成员被删到不足两颗（手改的配置）时退回一整串：那时没有「两列」可言，写得出来比对齐重要。
 *
 * @param widths 两列与分隔符的宽度（调用点整列量一次，见 [comboPairWidths]）
 */
@Composable
private fun SettingsPage.ComboPairTitle(
    cfg: Config,
    combo: ComboKey,
    writable: Boolean,
    widths: ComboPairWidths,
) {
    val style = COUITheme.textStyles.headline1
    val color =
        if (writable) {
            COUITheme.colorScheme.onSurfaceContainer
        } else {
            COUITheme.colorScheme.disabledOnSecondaryVariant
        }
    val names = combo.keys.mapNotNull { id -> cfg.keyIdOf(id)?.let(::keyName) }
    if (names.size < 2) {
        Text(
            text = comboLabel(cfg, combo, bracketed = true),
            style = style,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val (open, close) = keyBrackets()
    val first = "$open${names[0]}$close"
    val second = "$open${names[1]}$close"
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    //  与格子同一份字重（标题是 Medium）—— 只带样式不带字重会量窄一格
    val cellStyle = style.copy(fontWeight = FontWeight.Medium)
    //  **这一行自己**的名字有多宽（列宽是整列最长那个，长名字会被封顶截短，所以还要取小）
    val ink =
        remember(first, cellStyle, measurer) {
            measurer.measure(AnnotatedString(first), style = cellStyle, maxLines = 1).size.width
        }
    val inkDp = with(density) { ink.toDp() }
    //  可用宽是**这一格实际拿到的**宽（行里扣掉行首图标与行尾箭头之后那一段）——
    //  拿卡片宽算会让长名字撑出格去；各行的可用宽相同，逐行算不会破坏对齐。
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val room = (maxWidth - widths.separator).coerceAtLeast(0.dp)
        //  第一列封顶 [COMBO_FIRST_COLUMN_MAX_RATIO]：某个名字特别长时它自己那一格省略，
        //  而不是把整列撑开、把第二个名字挤到看不见。第二列拿剩下的全部，同样封顶。
        val column = widths.first.coerceAtMost(room * COMBO_FIRST_COLUMN_MAX_RATIO)
        val cell = inkDp.coerceAtMost(column)
        //  这一格空出来的那一半 —— `×` 左右各让这么多，两个名字到它的距离才相等
        val half = ((column - cell) / 2).coerceAtLeast(0.dp)
        val tail = widths.second.coerceAtMost((room - column).coerceAtLeast(0.dp))
        //  块本身**靠左摆**（块宽 = 两列 + 分隔符，与居中无关）：行里其余留白落在第二个键名之后，
        //  与别处的行一致。这里只调 `×` 的位置，不动排版。
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            //  两格都**定宽 + 左对齐**：一列里每行的键名左边缘对齐，才是列表的读法。
            ComboKeyCell(first, style, color, Modifier.width(cell))
            Spacer(Modifier.width(half))
            Text(
                text = COMBO_PAIR_SEPARATOR,
                style = style,
                color = color,
                modifier = Modifier.padding(horizontal = COMBO_PAIR_GAP),
            )
            Spacer(Modifier.width(half))
            ComboKeyCell(second, style, color, Modifier.width(tail))
        }
    }
}

/**
 * 两列与分隔符各该多宽 —— 每一列取**整列里最长的那个键名**，量一次给整列用。
 *
 *  写死一个 dp 不行：系统字号调大、切英文界面（`Side key`）之后名字会变长，写死只会一起省略。
 *  量的时候用与格子同一份字重（标题是 Medium）—— 只带样式不带字重会量窄一格。
 *
 *  封顶与居中都在画的那一头做（见 [ComboPairTitle]）：这里给的是**文字本身的宽度**，
 *  与实际可用宽无关，所以行与行之间算出来的永远是同一份。
 */
@Composable
private fun SettingsPage.comboPairWidths(cfg: Config): ComboPairWidths {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val (open, close) = keyBrackets()
    val cellStyle = COUITheme.textStyles.headline1.copy(fontWeight = FontWeight.Medium)
    return remember(cfg.combos, cellStyle, measurer) {
        //  一格键名（带方括号）的宽度；这一颗键已经不在配置里时按 0 算
        fun cellWidth(
            combo: ComboKey,
            at: Int,
        ): Int {
            val name = combo.keys.getOrNull(at)?.let { cfg.keyIdOf(it)?.let(::keyName) } ?: return 0
            return measurer.measure(AnnotatedString("$open$name$close"), style = cellStyle, maxLines = 1).size.width
        }
        val first = cfg.combos.maxOfOrNull { cellWidth(it, 0) } ?: 0
        val second = cfg.combos.maxOfOrNull { cellWidth(it, 1) } ?: 0
        val separator = measurer.measure(AnnotatedString(COMBO_PAIR_SEPARATOR), style = cellStyle, maxLines = 1).size.width
        with(density) {
            ComboPairWidths(
                first = first.toDp(),
                second = second.toDp(),
                separator = separator.toDp() + COMBO_PAIR_GAP * 2,
            )
        }
    }
}

/** 组合标题里的一格键名 —— 一行、装不下就省略号（列宽与对齐由调用点给）。 */
@Composable
private fun ComboKeyCell(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** 末行「添加组合按键」——编辑态里整行退场。 */
private fun SettingsPage.addComboRow(
    cfg: Config,
    writable: Boolean,
    atLimit: Boolean,
): CardRow.Item {
    //  组合要**两颗键**：只有内置侧键、又一组都没配过时这一行按不动 —— 选键弹窗里只有一行，
    //  根本配不出来（与首页「按键操作」卡那一行同一条判据）。
    //  **已经配过的那几组要放行**：收着的自定义键删光之后它们成了废配置，删除入口在列表里，
    //  这里也禁掉的话用户就再也删不掉了。
    val canCombo = cfg.customKeys.isNotEmpty() || cfg.combos.isNotEmpty()
    val needKey = I18n.get(Strings.COMBO_NEED_KEY, "先添加一颗按键")
    return CardRow.Item(
        title = I18n.get(Strings.COMBO_ADD, "添加组合按键"),
        //  副标题两档都有：配不出来 / 满员各说清为什么加不了，能加就说这一步做什么
        summary =
            when {
                !canCombo -> needKey
                atLimit -> I18n.format(Strings.COMBO_LIMIT, MAX_COMBOS)
                else -> I18n.get(Strings.COMBO_ADD_DESC, "选两颗键一起按")
            },
        icon = PageIcons.MenuKeyAdd,
        enabled = writable && !atLimit && canCombo,
        //  编辑态下整行退场（与按键列表末行「添加按键」同一口径）
        visible = !KeysEditState.editing,
        //  这一行自己的原因：「只有一颗键」优先于「满员」（满员时那一组也已存在，两说都对，
        //  但用户眼前的事实是弹窗挑不出第二颗键）
        disabledHint =
            when {
                !canCombo -> needKey
                atLimit -> I18n.format(Strings.COMBO_LIMIT, MAX_COMBOS)
                else -> null
            },
        onClick = {
            ComboEditState.pickKeysPending = true
        },
    )
}

/**
 * 「已启用」/「已停用」—— 与首页「按键列表」**同一对文案**（`binding.key_enabled_state` /
 * `binding.key_disabled_state`）：两处是同一种状态的同一种说法，另起一套只会各走各的措辞。
 *
 *  两档都给一句，行高才一致；停用也不只靠颜色区分（灰对色弱不友好）。
 */
private fun comboStateSummary(enabled: Boolean): String =
    if (enabled) {
        I18n.get(Strings.BINDING_KEY_ENABLED_STATE, "已启用")
    } else {
        I18n.get(Strings.BINDING_KEY_DISABLED_STATE, "已停用")
    }
