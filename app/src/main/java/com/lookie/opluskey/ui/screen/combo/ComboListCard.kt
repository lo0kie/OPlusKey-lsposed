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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.COMBO_PAIR_SEPARATOR
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.comboLabel
import com.lookie.opluskey.core.model.keyName
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.DisabledStateText
import com.lookie.opluskey.ui.DisabledStateWithArrow
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SectionTitleClick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.TitleCounter
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.opticalCenter
import com.lookie.opluskey.ui.rememberMenuState
import com.lookie.opluskey.ui.state.ComboEditState
import com.lookie.opluskey.ui.state.KeysEditState
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 组合标题里分隔符两侧的间距 —— 比「贴在一起」松一档，`×` 才不像粘在圆角框上。 */
private val COMBO_PAIR_GAP = 6.dp

/** 键名框左右的内边距 —— 量列宽时要一起算进去（见 [comboTitleWidths]）。 */
private val COMBO_CHIP_H_PAD = 6.dp

/**
 * 键名框里的字号相对行标题的缩放 —— 0.85 倍。
 *
 *  为什么不能直接用行标题那一档：一行标题里要塞**几枚框 + 几个 `×`**，按 `headline1` 原样量，
 *  4 个字的键名（「音量 +」）在 360dp 屏上就差约 18dp 装不下 —— 于是尾字变省略号
 *  （真机口径「较长的键名显示不全」）。缩到 0.85 之后两列 + 分隔符正好落在标题槽内，
 *  而小一号的字与正文仍是同一档观感，看不出「这一行字小了」。
 *
 *  **量宽度与画字必须用同一个比例**（见 [comboChipStyle]）：两边不一致时，装不装得下会判错。
 */
private const val COMBO_CHIP_FONT_SCALE = 0.85f

/**
 * 每枚框在「量出来的文字宽」之外**必须**多给的一点余量 —— 2dp。
 *
 *  没有它的话框宽正好等于文字宽，而 `measure` 出来的是整像素、`toDp()` 再换算回来会丢一点精度，
 *  加上字形边界与 `letterSpacing` 的取整，`Text` 会判成「差一点点」⇒ 尾字照样变省略号。
 *  真机口径「文字变小了但省略号还在」就是这一档：缩小字号只改了字，没改这个「刚好卡在边界」。
 */
private val COMBO_CHIP_SLACK = 2.dp

/** 键名框上下的内边距 —— 比左右小得多：框高跟着字高走，别把行撑高。 */
private val COMBO_CHIP_V_PAD = 2.dp

/**
 * 一排框装不下时，**每一枚**最少留这么宽（左右内边距 + 一个字宽的那档）。
 *
 *  为什么要有个下限：宽度从右往左让位（见 [ComboTitle]），左边照给之后右边只剩零头时，
 *  不留这一档那一枚会连框带 `×` 一起消失 —— 三颗的组合看起来像只配了两颗。
 *  留一个字的宽度，至少还看得见「这一枚在那儿，只是被裁了」。
 */
private val COMBO_CHIP_MIN_W = COMBO_CHIP_H_PAD * 2 + 16.dp

/** 键名框的圆角 —— 圆角矩形：比小标签那档（4dp）大一点点，与这一行的大字号相称，又不像胶囊。 */
private val COMBO_CHIP_RADIUS = 6.dp

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
    val menu = rememberMenuState()

    Column(Modifier.preciseClickAnchor(menu.anchor)) {
        SectionTitle(
            text = comboListTitle(cfg, filterKey),
            click = SectionTitleClick.Menu(expanded = menu.open) { menu.show() },
            //  行尾是**已用 / 上限**（与「命令名称」那行同一个 `TitleCounter`）：
            //  标题本身是筛选入口（筛了某颗键时它显示的是那颗键的名字），计数只报「共配了几档 / 上限」，
            //  与筛选无关 —— 否则筛一下就变成「已用 2 / 上限 9」，读起来像丢了几组。
            trailing = { TitleCounter(cfg.combos.size, MAX_COMBOS) },
        )
        //  每一列的宽度**整列量一次**（见 [comboTitleWidths]）：每行各量各的就会一行一个宽度。
        val rows = comboRows(cfg, writable, filterKey, comboTitleWidths(cfg))
        //  「添加组合按键」是这一列的**最后一行**（用户口径：与列表拼在一起，不另起卡）。
        //  一档都没配 / 全被筛掉时这张卡就只剩它一行 —— 仍然只画**一张**卡：
        //  `CardList` 收到空表仍会画一张零行卡片，边框与圆角还在，看着像坏了一块。
        CardList(rows = rows + addComboRow(cfg, writable, atLimit))
    }
    //  行表空着就是「不显示」（与别处 `PageMenu` 的用法一致）
    PageMenu(menu) {
        comboFilterEntries(cfg, filterKey) { key ->
            ComboEditState.filterKey = key
            menu.dismiss()
        }
    }
}

/**
 * 这张卡的每一行 —— 一档组合一行（**不含**「添加组合按键」，那一行由调用点接在末尾）。
 *
 *  每行的内容在 [comboRow] 里：这一层只负责「筛掉不合的、按顺序摆」。可能一行都不剩。
 *
 *  编辑态（见 [KeysEditState]）里与按键列表同一套：行尾换成多选框、点一行是勾选而不是进编辑页；
 *  「添加组合按键」那一行禁用（编辑态在改「已有哪几组」，不是加一组）。
 */
private fun SettingsPage.comboRows(
    cfg: Config,
    writable: Boolean,
    /** 只看含这颗键的组合（`null` = 全部）—— 行首的「第几组」仍按**全部**里的位置算。 */
    filter: String?,
    /** 每列标题与分隔符的宽度 —— 整列同一个数，见 [comboTitleWidths]。 */
    widths: ComboTitleWidths,
): List<CardRow> =
    buildList {
        cfg.combos.forEachIndexed { index, combo ->
            if (filter != null && filter !in combo.keys) return@forEachIndexed
            add(comboRow(cfg, combo, index, writable, widths))
        }
    }

/** 一档组合那一行 —— 点它是进编辑页（编辑态里是勾选）。 */
private fun SettingsPage.comboRow(
    cfg: Config,
    combo: ComboKey,
    index: Int,
    writable: Boolean,
    widths: ComboTitleWidths,
): CardRow.Item {
    val editing = KeysEditState.editing
    val picked = KeysEditState.pickedCombos
    val dead = comboHasMissingKey(cfg, combo)
    //  **跑不起来**：自己关着，或成员里任一键被停用（判据在 `Config.comboOff`，按成员集合算，
    //  多按键组合同一条规则）。行尾那半句与行首那颗禁用图标都读它。
    val off = cfg.comboOff(combo)
    return CardRow.Item(
        title = comboLabel(cfg, combo),
        //  标题按**每颗键一枚框**画（见 [ComboTitle]）：拼成一整串时，分隔符落在哪由前一个名字的
        //  长短决定，一列下来每行都错开（真机反馈「单独某个名字过长时全部都没对齐」）。
        titleContent = { ComboTitle(cfg, combo, writable, widths) },
        //  副标题**不写**（用户口径：启停状态挪到行尾、与按键列表同一套）。动作摘要也不写：
        //  一列下来全是小字、每行还都不一样长，扫一眼只能看见一片灰 —— 两档动作进页面看。
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
        //  行尾**只有一格**（`CardRow` 里 `trailing` 优先于 `arrow`）：编辑态是勾选框，平时停用
        //  那一档用与场景卡共用的「已停用 + 箭头」（`DisabledStateWithArrow`），全启用给 `null`
        //  让 `arrow` 那条默认路径画（用户口径「启停态和箭头共存」）。
        trailing =
            when {
                editing -> {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (off) DisabledStateText()
                            Checkbox(
                                state = if (combo.id in picked) ToggleableState.On else ToggleableState.Off,
                                onClick = null,
                                enabled = writable,
                            )
                        }
                    }
                }

                off -> {
                    { DisabledStateWithArrow() }
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

/** 上一次打出去的那一行（见 [logPairOnce]）。 */
private var lastPairLog: String? = null

/**
 * 判据行的**去重出口**：同一行只打一次 W。
 *
 *  内容由调用点自己拼：这里只管「变没变」，不再逐个接那几组宽度 ——
 *  那几个数在调用点上本来就是现成的，摆成参数表只是把一行日志撑成六个位置参数。
 */
private fun logPairOnce(line: String) {
    if (line == lastPairLog) return
    lastPairLog = line
    XLog.w(line)
}

/**
 * 组合行标题 —— **每颗键一枚圆角框**：`侧键` ｜ `×` ｜ `电量 +`（三颗就再来一段）。
 *
 *  键名各装进一个圆角矩形（底色是文字色的低透明度，6dp 圆角）：**框本身就是「这是一个键名」
 *  的记号**，所以框里不再套方括号 —— 两道框叠在一行字上读起来是重复的装饰。
 *  只有这一列之外的场合（顶栏副标题、删除确认、回执那些「一句话」）才带方括号，
 *  那是 `core/model/KeyNames.kt` 里 `comboLabel` 的事。
 *
 *  ## 为什么不再「两颗才排框、三颗退回一整串」
 *
 *  一列里同一件事有两种长相（有的行是框、有的行是一行字），扫下来像两种不同类型的条目；
 *  而框本来就该按颗数长，不是按「两颗」这个数字。三颗那档如今真的造得出来了
 *  （`ComboKey.MAX_KEYS`），所以这一支按 `names.size` 摊开画。
 *
 *  ## 宽度怎么定
 *
 *  每一列取**整列里最长那个名字**的宽度（[comboTitleWidths]，全列表同一份），于是每行的框左右边缘
 *  都竖着对齐：两个名字一长一短时，短的那个**在自己框里左右补白**、字居中，同一列的框一样宽。
 *  （逐行按自己的名字收紧，一列下来就是一排参差不齐的框 —— 那正是这个列表最早就有的毛病。）
 *  名字再长也只在自己那个框里省略号，不会把旁边的挤走。
 *
 *  装不下时（三颗那一档在窄屏上很常见）**从右边开始裁**：左边的框按自己需要的宽度照给，
 *  右边那几枚分剩下的零头、由框里的省略号收尾 —— 用户要的是「一眼认出前面几颗」，
 *  而不是三枚一起变成三个「…」。
 *
 *  `×` 摆在两枚框之间，左右各一档 [COMBO_PAIR_GAP]：框边缘对齐了，这个间距自然就是对称的。
 *
 *  成员被删到不足两颗（手改的配置）时退回一整串：那时没有「对齐」可言，写得出来比对齐重要。
 *
 * @param widths 每列与分隔符的宽度（调用点整列量一次，见 [comboTitleWidths]）
 */
@Composable
private fun SettingsPage.ComboTitle(
    cfg: Config,
    combo: ComboKey,
    writable: Boolean,
    widths: ComboTitleWidths,
) {
    //  框里的字用 [comboChipStyle]（比行标题小一档、字重 Medium），`×` 仍用行标题那一档 ——
    //  框是「键名标签」，比正文小一点本来就对；量宽度用的也是这一份（见 `comboTitleWidths`）。
    val chipStyle = comboChipStyle()
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
        label = "ComboTitle",
    )
    val names = combo.keys.mapNotNull { id -> cfg.keyIdOf(id)?.let(::keyName) }
    //  不足两颗（成员被删 / 手改的配置）退回一整串 —— 那时「几枚框对齐」不成立
    if (names.size < ComboKey.MIN_KEYS) {
        Text(
            text = comboLabel(cfg, combo),
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
        //  分隔符先扣掉：`n` 枚框之间有 `n - 1` 个，每格含 `×` 自己与两侧间距
        val budget = (maxWidth - widths.separator * (names.size - 1)).coerceAtLeast(0.dp)
        //  **+ [COMBO_CHIP_SLACK]**：框宽必须比文字宽多出一点，否则刚好卡在边界上照样省略
        val needs =
            names.indices.map { at ->
                (widths.columns.getOrNull(at) ?: 0.dp) + COMBO_CHIP_H_PAD * 2 + COMBO_CHIP_SLACK
            }
        val totalNeed = needs.fold(0.dp) { acc, need -> acc + need }
        val given = distributeChipWidths(needs, budget)
        //  判据行：这一行拿到多宽、每列各要多少、实给多少 —— 排查「键名被省略」时先看它（同值只打一次）。
        //  `fits=false` 说明整行真的装不下（名字太长 / 标题槽太窄 / 颗数多），被裁的是右边那几枚；
        //  `fits=true` 却仍被截，说明框宽算对了、问题在别处。没有这一行只能靠猜。
        logPairOnce("组合键标题：room=$budget 需要=$needs fits=${totalNeed <= budget} 实给=$given")
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            names.forEachIndexed { at, name ->
                //  分隔符画在前面（第一枚之前没有），与框同序 —— 这样 `names` 与 `given` 一一对应
                if (at > 0) {
                    Text(
                        text = COMBO_PAIR_SEPARATOR,
                        style = style,
                        color = color,
                        modifier = Modifier.padding(horizontal = COMBO_PAIR_GAP),
                    )
                }
                ComboKeyChip(name, chipStyle, color, writable, Modifier.width(given[at]))
            }
        }
    }
}

/**
 * 一排框各分多宽 —— 见 [ComboTitle] 的「装不下时从右边开始裁」。
 *
 *  装得下时逐枚按 [needs] 照给（同一列的框一样宽、边缘竖着对齐，谁也不省略）。
 *  装不下时**从右往左让位**：左边的框按自己需要的宽度照给，只把「剩下的那点」留给右边的框
 *  —— 被裁的于是永远是右边那一枚。早先按各列需要的同一比例缩，三颗那一档在窄屏上会三枚
 *  一起变省略号：三个名字都读不全，而用户其实只需要认得出前两颗。
 *
 *  给后面每一枚预留 [COMBO_CHIP_MIN_W]：不留的话「左边照给」会把右边挤成 0 宽 ——
 *  那一枚连同它的 `×` 一起消失，读起来像「这一档只配了两颗」。
 */
private fun distributeChipWidths(
    needs: List<Dp>,
    budget: Dp,
): List<Dp> {
    val given = ArrayList<Dp>(needs.size)
    var remaining = budget
    needs.indices.forEach { at ->
        val reserved = COMBO_CHIP_MIN_W * (needs.size - at - 1)
        val cap = (remaining - reserved).coerceAtLeast(COMBO_CHIP_MIN_W)
        val give = if (needs[at] <= cap) needs[at] else cap
        given += give
        remaining -= give
    }
    return given
}

/**
 * 每列与分隔符各该多宽 —— 每一列取**整列里最长的那个键名**，量一次给整列用。
 *
 *  写死一个 dp 不行：系统字号调大、切英文界面（`Side key`）之后名字会变长，写死只会一起省略。
 *  量的时候用与格子同一份字重（标题是 Medium）—— 只带样式不带字重会量窄一格。
 *
 *  封顶与等比缩都在画的那一头做（见 [ComboTitle]）：这里给的是**文字本身的宽度**，
 *  与实际可用宽无关，所以行与行之间算出来的永远是同一份。
 */
@Composable
private fun comboChipStyle(): TextStyle {
    val base = COUITheme.textStyles.headline1
    //  字重并进样式：`opticalCenter` 与 `measure` 都按样式算，写在 `Text` 参数上会两边不一致
    return base.copy(fontWeight = FontWeight.Medium, fontSize = base.fontSize * COMBO_CHIP_FONT_SCALE)
}

@Composable
private fun SettingsPage.comboTitleWidths(cfg: Config): ComboTitleWidths {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    //  **与画字同一份样式**（含那 0.85 的字号）：量大了会误判「装不下」，量小了框不够宽
    val cellStyle = comboChipStyle()
    //  分隔符按**画它的那一档**量（`×` 用行标题字号，见 [ComboTitle]），不是框里那份小字号：
    //  一行三颗就有两个分隔符，差出来的那点会一起挤到最后一枚框上。
    val separatorStyle = COUITheme.textStyles.headline1
    //  **`density` 必须进 key**：页面缩放（「页面缩放」/「跟随系统显示大小」）换的就是
    //  `LocalDensity` —— 不进 key 的话量出来的是上一档密度下的像素，换算成 dp 就偏了，
    //  缩小时正好偏窄 ⇒ 键名被截成「侧…」（真机口径「缩小界面后键名显示不全」）。
    return remember(cfg.combos, cellStyle, separatorStyle, measurer, density) {
        //  一格键名的宽度（框里只写名字，**不套方括号** —— 框本身已经说明这是键名）；
        //  这一颗键已经不在配置里时按 0 算
        fun cellWidth(
            combo: ComboKey,
            at: Int,
        ): Int {
            val name = combo.keys.getOrNull(at)?.let { cfg.keyIdOf(it)?.let(::keyName) } ?: return 0
            return measurer.measure(AnnotatedString(name), style = cellStyle, maxLines = 1).size.width
        }
        //  **按位置取整列最大值**：第 0 列是所有组的第一个键名，第 1 列是第二个……
        //  三颗那一档于是有自己那第三列，前两列仍与两列的行竖着对齐。
        val count = cfg.combos.maxOfOrNull { it.keys.size } ?: 0
        val columns = List(count) { at -> cfg.combos.maxOfOrNull { cellWidth(it, at) } ?: 0 }
        val separator = measurer.measure(AnnotatedString(COMBO_PAIR_SEPARATOR), style = separatorStyle, maxLines = 1).size.width
        with(density) {
            ComboTitleWidths(
                columns = columns.map { it.toDp() },
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

/** 「添加组合按键」—— 清单的**最后一行**（与清单同卡）；编辑态里**禁用**而不是退场（与「添加按键」同一口径）。 */
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
