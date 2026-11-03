package com.lookie.opluskey.ui.screen.combo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.opticalCenter
import com.lookie.opluskey.ui.state.ComboEditState
import com.lookie.opluskey.ui.state.KeysEditState
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 组合标题里分隔符两侧的间距 —— 比「贴在一起」松一档，`×` 才不像粘在两枚圆角框上。 */
private val COMBO_PAIR_GAP = 10.dp

/** 两列标题里第一列最多占整行的多少 —— 再长的名字也只在自己那格里省略。 */
private const val COMBO_FIRST_COLUMN_MAX_RATIO = 0.45f

/** 键名框左右的内边距 —— 量列宽时要一起算进去（见 [comboPairWidths]）。 */
private val COMBO_CHIP_H_PAD = 8.dp

/** 键名框上下的内边距 —— 比左右小得多：框高跟着字高走，别把行撑高。 */
private val COMBO_CHIP_V_PAD = 2.dp

/** 键名框的圆角 —— 圆角矩形：比小标签那档（4dp）大一点点，与这一行的大字号相称，又不像胶囊。 */
private val COMBO_CHIP_RADIUS = 6.dp

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
 *  末行「添加组合按键」禁用（编辑态在改「已有哪几组」，不是加一组）。
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
 * 组合行标题 —— **两列圆角框**：`侧键` ｜ `×` ｜ `电量 +`。
 *
 *  两颗键名各装进一个圆角矩形（底色是文字色的低透明度，6dp 圆角）：**框本身就是「这是一个键名」
 *  的记号**，所以框里不再套方括号 —— 两道框叠在一行字上读起来是重复的装饰。
 *  只有这一列之外的场合（顶栏副标题、删除确认、回执那些「一句话」）才带括号，见 `keyBrackets()`。
 *
 *  ## 宽度怎么定
 *
 *  每一列取**整列里最长那个名字**的宽度（[comboPairWidths]，全列表同一份），于是每行的框左右边缘
 *  都竖着对齐：两个名字一长一短时，短的那个**在自己框里左右补白**、字居中，两个框一样宽。
 *  （逐行按自己的名字收紧，一列下来就是一排参差不齐的框 —— 那正是这个列表最早就有的毛病。）
 *  名字再长也只在自己那个框里省略号，不会把另一个挤走。
 *
 *  `×` 摆在两个框之间，左右各一档 [COMBO_PAIR_GAP]：框边缘对齐了，这个间距自然就是对称的。
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
    //  两档标题色带过渡：整页闸门一拨，这一列与同一页其它行一起变灰 / 恢复，别只有这里硬切。
    val color by animateColorAsState(
        targetValue =
            if (writable) {
                COUITheme.colorScheme.onSurfaceContainer
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "ComboPairTitle",
    )
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
    //  可用宽是**这一格实际拿到的**宽（行里扣掉行首图标与行尾箭头之后那一段）——
    //  拿卡片宽算会让长名字撑出格去；各行的可用宽相同，逐行算不会破坏对齐。
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val room = (maxWidth - widths.separator).coerceAtLeast(0.dp)
        //  第一枚封顶 [COMBO_FIRST_COLUMN_MAX_RATIO]：某个名字特别长时它自己那枚省略，
        //  而不是把整列撑开、把第二个名字挤到看不见。第二枚拿剩下的全部，同样封顶。
        val head = (widths.first + COMBO_CHIP_H_PAD * 2).coerceAtMost(room * COMBO_FIRST_COLUMN_MAX_RATIO)
        val tail = (widths.second + COMBO_CHIP_H_PAD * 2).coerceAtMost((room - head).coerceAtLeast(0.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComboKeyChip(names[0], style, color, writable, Modifier.width(head))
            Text(
                text = COMBO_PAIR_SEPARATOR,
                style = style,
                color = color,
                modifier = Modifier.padding(horizontal = COMBO_PAIR_GAP),
            )
            ComboKeyChip(names[1], style, color, writable, Modifier.width(tail))
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
    val cellStyle = COUITheme.textStyles.headline1.copy(fontWeight = FontWeight.Medium)
    return remember(cfg.combos, cellStyle, measurer) {
        //  一格键名的宽度（框里只写名字，**不套方括号** —— 框本身已经说明这是键名）；
        //  这一颗键已经不在配置里时按 0 算
        fun cellWidth(
            combo: ComboKey,
            at: Int,
        ): Int {
            val name = combo.keys.getOrNull(at)?.let { cfg.keyIdOf(it)?.let(::keyName) } ?: return 0
            return measurer.measure(AnnotatedString(name), style = cellStyle, maxLines = 1).size.width
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

/**
 * 组合标题里的一个键名框 —— 一行、装不下就省略号；**宽度由调用点给**（整列同一个数）。
 *
 *  圆角矩形（不是胶囊）：圆角只吃四角，字在框里**居中** —— 短名字那个两边各多出一点白，
 *  两个框因此一样宽、边缘竖着对齐（用户口径「上下宽度不一致时往短的填 padding」）。
 *
 *  底色是文字色的低透明度 —— 与「模块 / 系统 / 用户」那几枚小标签同一套做法（`AppKindTag`）：
 *  颜色只有一处来源，启用 / 禁用两档跟着标题色一起变，不必再为底色单独记一份色。
 */
@Composable
private fun ComboKeyChip(
    text: String,
    style: TextStyle,
    color: Color,
    enabled: Boolean,
    modifier: Modifier,
) {
    val scheme = COUITheme.colorScheme
    //  底色与描边**与顶栏那颗图标按钮同一档**（`HeaderIconButton`）：同一个界面里「一块可以点的面」
    //  只有一种长相 —— 底色取 `surfaceContainer`，描边取 `dividerLine` 再减半（整档压在卡片上偏重）。
    //  两档都带过渡：闸门关 / 开时框与里面的字在同一段时间里一起变，不会出现「字在淡、框在跳」。
    val fill by animateColorAsState(
        targetValue = if (enabled) scheme.surfaceContainer else scheme.disabledSecondaryVariant,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "ComboChipFill",
    )
    val border by animateColorAsState(
        targetValue =
            if (enabled) {
                scheme.dividerLine.copy(alpha = scheme.dividerLine.alpha * 0.5f)
            } else {
                scheme.disabledSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "ComboChipBorder",
    )
    val shape = RoundedCornerShape(COMBO_CHIP_RADIUS)
    //  字重并进样式里再交给 [opticalCenter]：它按**样式**量字形边界，字重写在 `Text` 的参数上
    //  会让量出来的是常规体、画出来是 Medium —— 差的那一点正好落在上下留白上。
    val chipStyle = style.copy(fontWeight = FontWeight.Medium)
    Box(
        modifier =
            modifier
                .background(color = fill, shape = shape)
                .border(width = 1.dp, color = border, shape = shape)
                .padding(horizontal = COMBO_CHIP_H_PAD, vertical = COMBO_CHIP_V_PAD),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = chipStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            //  **上下留白靠这一行修正**：行盒里汉字墨迹的重心比几何中心偏高一点（下行留白多），
            //  直接居中的结果是「上面紧、下面松」。与 `AppKindTag` 那几枚小标签同一条通道。
            modifier = Modifier.fillMaxWidth().opticalCenter(chipStyle),
        )
    }
}

/** 末行「添加组合按键」——编辑态里**禁用**而不是退场（与「添加按键」同一口径）。 */
private fun SettingsPage.addComboRow(
    cfg: Config,
    writable: Boolean,
    atLimit: Boolean,
): CardRow.Item {
    val editing = KeysEditState.editing
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
        enabled = writable && !atLimit && canCombo && !editing,
        //  这一行自己的原因：「只有一颗键」>「满员」>「编辑态」（编辑态在删、不在加，
        //  但行要一直在，用户才知道这一列还能再添）
        disabledHint =
            when {
                !canCombo -> needKey
                atLimit -> I18n.format(Strings.COMBO_LIMIT, MAX_COMBOS)
                editing -> I18n.get(Strings.KEYS_EDIT_NO_ADD, "编辑中不能添加，请先完成")
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
