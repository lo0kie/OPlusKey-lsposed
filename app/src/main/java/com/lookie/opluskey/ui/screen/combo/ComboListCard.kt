package com.lookie.opluskey.ui.screen.combo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.core.present.keyName
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.dialog.rememberMenuState
import com.lookie.opluskey.ui.feedback.DisabledStateWithArrow
import com.lookie.opluskey.ui.feedback.DisabledStateWithPick
import com.lookie.opluskey.ui.feedback.keyStateLabel
import com.lookie.opluskey.ui.model.icon.DrawnIcons
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.row.CHIP_H_PAD
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.ChipFlow
import com.lookie.opluskey.ui.row.ChipSentence
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.SectionTitleClick
import com.lookie.opluskey.ui.row.TitleCounter
import com.lookie.opluskey.ui.row.chipLook
import com.lookie.opluskey.ui.row.chipStyle
import com.lookie.opluskey.ui.state.binding.ComboEditState
import com.lookie.opluskey.ui.state.binding.KeysEditState
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.SelectAll
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 每枚框在「量出来的文字宽」之外**必须**多给的一点余量 —— 2dp。
 *
 *  没有它的话框宽正好等于文字宽，而 `measure` 出来的是整像素、`toDp()` 再换算回来会丢一点精度，
 *  加上字形边界与 `letterSpacing` 的取整，`Text` 会判成「差一点点」⇒ 尾字照样变省略号。
 *  真机口径「文字变小了但省略号还在」就是这一档：缩小字号只改了字，没改这个「刚好卡在边界」。
 *
 *  只有**组合这一列**用得上（[comboTitleWidths] 量列宽时补）：框本身、左右内边距、圆角与那 0.85
 *  的缩放都搬进了 `ui/row/ChipLabel.kt`（场景条件那一行调的是同一份），这里不再各存一套。
 */
private val COMBO_CHIP_SLACK = 2.dp

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
    val menu = rememberMenuState()

    Column(Modifier.preciseClickAnchor(menu.anchor)) {
        SectionTitle(
            text = comboListTitle(cfg, filterKey, hold),
            click = SectionTitleClick.Menu(expanded = menu.open) { menu.show() },
            //  行尾是**已用 / 上限**（与「命令名称」那行同一个 `TitleCounter`）：
            //  标题本身是筛选入口（筛了某颗键时它显示的是那颗键的名字），计数只报「共配了几档 / 上限」，
            //  与筛选无关 —— 否则筛一下就变成「已用 2 / 上限 9」，读起来像丢了几组。
            trailing = { TitleCounter(cfg.combos.count { it.isHold == hold }, MAX_COMBOS) },
        )
        //  每一列的宽度**整列量一次**（见 [comboTitleWidths]）：每行各量各的就会一行一个宽度。
        val rows = comboRows(cfg, writable, filterKey, comboTitleWidths(cfg), hold)
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
    /** 这一列是「按住组合」还是「等待组合」（另一类的行不画在这一列里）。 */
    hold: Boolean,
): List<CardRow> =
    buildList {
        //  **段内独立编号**（用户口径）：两列各自的「第几组」从 1 数起 ——
        //  按 `cfg.combos` 的全局位次给号，按住那一列会从「等待组合的条数 + 1」开始，
        //  看着像前面缺了好几组（行首那颗数字图标本来就只在告「这是这一列的第几个」）。
        var seq = 0
        cfg.combos.forEach { combo ->
            if (combo.isHold != hold) return@forEach
            if (filter != null && filter !in combo.keys) return@forEach
            add(comboRow(cfg, combo, seq++, writable, widths))
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
                        DisabledStateWithPick(
                            picked = combo.id in picked,
                            enabled = writable,
                            //  组合没有「只参与组合按键」这一位（那一位只属于单键），
                            //  所以这里只有「停用 / 不写」两种 —— 判据仍走同一份
                            state = keyStateLabel(enabled = !off, comboOnly = false),
                        )
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
 *  框宽取**全部组合里最长那个键名**量一次（[comboTitleWidths]，全列表同一份），于是每行的框
 *  左右边缘都竖着对齐：名字一长一短时，短的那个**在自己框里左右补白**、字居中。
 *  （逐行按自己的名字收紧，一列下来就是一排参差不齐的框 —— 那正是这个列表最早就有的毛病。）
 *  名字再长也只在自己那个框里省略号，不会把旁边的挤走。
 *
 *  装不下时**换行**（见 `ui/row/ChipLabel.kt` 的 `ChipFlow`）：每枚框始终是整列统一那个宽度，
 *  放不下的排到下一行 —— 早先按可用宽等比压缩，三颗那一档在窄屏上会被压成三个空壳。
 *
 *  `×` 摆在两枚框之间，左右各一档间距（`CHIP_SEPARATOR_GAP`）：框边缘对齐了，这个间距自然就是对称的。
 *
 *  成员被删到不足两颗（手改的配置）时退回一整串：那时没有「对齐」可言，写得出来比对齐重要。
 *
 * @param widths 框宽（调用点整列量一次，见 [comboTitleWidths]）
 */
@Composable
private fun SettingsPage.ComboTitle(
    cfg: Config,
    combo: ComboKey,
    writable: Boolean,
    widths: ComboTitleWidths,
) {
    //  框里的字用 `chipStyle`（比行标题小一档、字重 Medium），`×` 仍用行标题那一档 ——
    //  框是「键名标签」，比正文小一点本来就对；量宽度用的也是这一份（见 `comboTitleWidths`）。
    //  框本身在 `ui/row/ChipLabel.kt`，场景条件里的应用名调的是同一份（用户口径：复用）。
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
    //  **按住组合写成一句话、键名自己仍是框**（用户口径：「按住 A 后再按 B」，名字用 tag 样式）：
    //  那两档的判据就是「谁先按住」，只摆两枚框读不出顺序，而顺序正是这两档的区分所在；
    //  但名字也不该退化成纯文字 —— 同一颗键在这一列里要一直是同一枚框。
    if (combo.isHold) {
        val ordered = if (combo.mode == ComboKey.MODE_HOLD_SECOND) names.reversed() else names
        ChipSentence(
            template = I18n.get(Strings.COMBO_MODE_HOLD, "按住 %s 后再按 %s"),
            names = ordered,
            look = chipLook(color = color, enabled = writable),
            style = style,
            color = color,
        )
        return
    }
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
    //  **装不下就换行**（用户口径：按键列表里也加换行）。原先按可用宽把每枚框等比压缩，
    //  三颗那一档在窄屏上会被压成三个空壳（名字全省略，只剩圆角矩形）——换成换行之后，
    //  每枚框都按**整列统一的那个宽度**（[ComboTitleWidths.chip]，见 [comboTitleWidths]）
    //  画，放不下的排到下一行，名字始终读得到。
    ChipFlow(
        names = names,
        look = chipLook(color = color, enabled = writable),
        modifier = Modifier.fillMaxWidth(),
        chipWidth = widths.chip,
    )
}

/**
 * 框宽 —— 取**全部组合里最长的那个键名**量一次，所有行共用同一个数。
 *
 *  写死一个 dp 不行：系统字号调大、切英文界面（`Side key`）之后名字会变长，写死只会一起省略。
 *  量的时候用与画字同一份样式（`chipStyle`，含那 0.85 的字号与 Medium 字重）——
 *  量大了会误判、量小了框不够宽。
 *
 *  为什么不按列量（原来那样）：换行之后一行的第几枚不再等于「第几颗键」，按列对齐没有意义；
 *  取全局最长值，一列下来每枚框仍然一样宽、左边仍然竖着对齐。
 */
@Composable
private fun SettingsPage.comboTitleWidths(cfg: Config): ComboTitleWidths {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cellStyle = chipStyle()
    //  **`density` 必须进 key**：页面缩放（「页面缩放」/「跟随系统显示大小」）换的就是
    //  `LocalDensity` —— 不进 key 的话量出来的是上一档密度下的像素，换算成 dp 就偏了，
    //  缩小时正好偏窄 ⇒ 键名被截成「侧…」（真机口径「缩小界面后键名显示不全」）。
    //  `customKeys` 也必须进 key：自定义键改名后键名宽度变了，不重算就仍是旧名字量出来的框宽
    //  （表现是新名字被截断、框与框之间多出空档）。
    //  **`language` 同样必须进 key**：键名是本地化文案（内置键的中文「音量 +」与英文 `Volume +`
    //  宽度差一大截），只按配置对象缓存的话，切到英文后仍按中文量出的宽度 —— 英文被截成 `Volu…`。
    val language = I18n.effectiveLanguage
    return remember(cfg.combos, cfg.customKeys, cellStyle, measurer, density, language) {
        val widest =
            cfg.combos
                .flatMap { combo -> combo.keys.mapNotNull { id -> cfg.keyIdOf(id)?.let(::keyName) } }
                .maxOfOrNull { name -> measurer.measure(AnnotatedString(name), style = cellStyle, maxLines = 1).size.width }
                ?: 0
        with(density) {
            //  **+ [COMBO_CHIP_SLACK]**：框宽必须比文字宽多出一点，否则刚好卡在边界上照样省略
            val chip = widest.toDp() + CHIP_H_PAD * 2 + COMBO_CHIP_SLACK
            //  判据行：真机口径「缩小界面 / 切英文后键名显示不全」时，这一行直接给出量到的宽度
            logPairOnce("组合框宽：chip=${chip.value}dp widest=${widest}px lang=$language")
            ComboTitleWidths(chip = chip)
        }
    }
}

// 组合标题里那一枚键名框现在就是 `ui/row/ChipLabel.kt` 的 `NameChip`：
//  原先它是这里的私有 `ComboKeyChip`，场景条件的「前台应用」那一行要同一种长相
//  （用户口径：应用名改成和组合按键一样的标签样式、复用它），于是框搬去了公共文件，
//  这一列调的是同一份 —— 圆角 / 内边距 / 底色那几档从此只有一处。

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
    val needKey = I18n.get(Strings.COMBO_NEED_KEY, "先添加一颗按键")
    return CardRow.Item(
        //  两列各一颗：这一行加出来的那一档，类别在点下去那一刻就定了（见 `ComboEditState.pendingKind`）
        title =
            if (hold) {
                I18n.get(Strings.COMBO_ADD_HOLD, "添加按住组合")
            } else {
                I18n.get(Strings.COMBO_ADD_WAIT, "添加等待组合")
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
            ComboEditState.pendingKind = if (hold) ComboKey.MODE_HOLD_FIRST else ComboKey.MODE_WAIT
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
 *  两颗以上连起来那一档（`[ 侧键 ]×[ 电量 + ]`）仍然要框 —— 那里框是为了把两个名字分开。
 */
internal fun SettingsPage.comboListTitle(
    cfg: Config,
    filter: String?,
    hold: Boolean,
): String {
    val base =
        if (hold) {
            I18n.get(Strings.COMBO_SECTION_HOLD, "按住组合")
        } else {
            I18n.get(Strings.COMBO_SECTION_WAIT, "等待组合")
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
                title = I18n.get(Strings.COMBO_FILTER_SECTION, "筛选按键"),
                rows =
                    buildList {
                        //  「不筛」那一行也在这一组里（它同样是「按按键筛」的一个档位）；
                        //  与下面真正的键之间用一条**加深带**断开 —— 见下一行那个 `dividerBefore`。
                        add(
                            MenuEntry.Row(
                                title = I18n.get(Strings.COMBO_FILTER_ALL, "全部"),
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
