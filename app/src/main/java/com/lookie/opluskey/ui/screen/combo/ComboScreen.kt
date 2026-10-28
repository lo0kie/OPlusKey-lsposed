package com.lookie.opluskey.ui.screen.combo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.MultiPickerDialog
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SectionTitleClick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.ComboEditState
import com.lookie.opluskey.ui.switchNotice
import com.lookie.opluskey.ui.writeAndNotice
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.utils.overScrollVertical

// 「组合按键」列表页 —— 从首页「按键操作」卡里那一行进来。
//
// 一档组合一行；点一行进编辑页（`Screen.COMBO_EDIT`）。
// 末行「添加组合按键」弹选键窗；选完落盘之后停在列表（新加的那一行就在眼前）。

/**
 * 组合按键列表。
 *
 *  **一行都没有时什么都不摆**（用户口径：那句「还没有组合按键」去掉）——只剩末行
 * 「添加组合按键」。这一页本来就只有两件事（这一列有哪几组 / 再加一组），
 * 空的时候「加一组」这一行自己就把话说完了，再补一句说明只是多一句废话。
 */
@Composable
internal fun SettingsPage.ComboScreen(scrollBehavior: ScrollBehavior) {
    val cfg = configState.config.value
    val writable = pageWritable()
    val atLimit = cfg.combos.size >= MAX_COMBOS
    val scroll: ScrollState = rememberScrollState()
    //  筛选：点小标题挑一颗键，只看含这颗键的组合（单选，不落盘）
    var filterMenu by remember { mutableStateOf(false) }
    val filterAnchor = rememberPreciseClickState()
    val filterKey = ComboEditState.filterKey
    //  筛的那颗键已经不在任何组合里了（那一组被删 / 那颗键被删）⇒ 退回「全部」，
    //  否则回到这一页只会看到一张空表，还不知道是筛出来的。
    LaunchedEffect(filterKey, cfg.combos) {
        if (filterKey != null && cfg.combos.none { filterKey in it.keys }) ComboEditState.filterKey = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical()
            .verticalScroll(scroll)
            //  页底留白 —— 与全项目同一档、同一种写法（挂在滚动内容里，不用尾部 Spacer）
            .padding(bottom = PAGE_BOTTOM_PADDING),
    ) {
        //  与其它页同一档：顶栏叠在内容上，顶部这一块自己让出来（动态值）。
        //  **不再另加 [PAGE_TOP_PADDING]**：这一页首行就是小标题，它自己带 16 + 8 的上间距
        //  （与首页、更多选项两页同一笔账）；再加一档就比别的页多出 8dp。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))
        //  小标题在这张卡上方（用户口径）：一列「有哪几档」需要一个名字，
        //  滚起来之后顶栏折成一条，只看行名认不出这一列是什么。
        //  **点它弹筛选菜单**（用户口径：按按键筛这一列，单选）—— 小标题是这一列唯一的名字，
        //  把「筛」挂在这儿，比在卡片顶上再占一行更省地方。文字末尾那颗箭头跟着菜单开合转。
        Column {
            //  筛选标题自己那一块：标题 + 表格（标题与表格的紧贴关系由这一层 Column 负责）
            Column(Modifier.preciseClickAnchor(filterAnchor)) {
                SectionTitle(
                    text = comboListTitle(cfg, filterKey),
                    click = SectionTitleClick.Menu(expanded = filterMenu) { filterMenu = true },
                )
                CardList(rows = comboRows(cfg, writable, atLimit, filterKey))
            }
            //  设置块：这一页唯一一个「不针对某一组」的项（见 [ComboPassthroughCard]）
            SectionTitle(I18n.get(Strings.COMBO_SETTINGS_SECTION, "组合按键设置"))
            ComboSettingsCard(writable)
        }
    }

    //  筛选菜单 —— 行表空着就是「不显示」（与别处 `PageMenu` 的用法一致）
    PageMenu(
        entries =
            if (filterMenu) {
                comboFilterEntries(cfg, filterKey) { key ->
                    ComboEditState.filterKey = key
                    filterMenu = false
                }
            } else {
                emptyList()
            },
        preciseClick = filterAnchor,
        onDismiss = { filterMenu = false },
    )

    ComboKeysDialog(
        show = ComboEditState.pickKeysPending,
        onDismiss = { ComboEditState.pickKeysPending = false },
        onConfirm = { keys ->
            ComboEditState.pickKeysPending = false
            comboWriteGuard("添加组合键") { addCombo(keys) }
        },
    )
}

/**
 * 「选择按键」弹窗 —— 这一档组合由哪几颗键组成。
 *
 *  成员数恒为 [ComboKey.MAX_KEYS]（两颗，用户口径）：判定侧也只认两颗，多勾的那颗永远等不到。
 *  所以**勾满两颗之后第三颗勾不上** —— 顺手弹条说「一个组合最多 2 颗按键」。
 *  （让用户勾出个三颗的组合、再在「添加」那颗灰按钮上告诉他不行，等于把同一个规矩说两遍。）
 *
 *  「为什么还不能添加」只有 [blockedReason] 一处判据：已存在 / 还没选够。
 *  不能确定时按钮是灰的，点一下弹**通用消息条**（不在弹窗里另画一行）。
 */
@Composable
private fun SettingsPage.ComboKeysDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<KeyId>) -> Unit,
) {
    val cfg = configState.config.value
    val keys = cfg.allKeys
    //  每次露出都从空选起：上一次点了取消还留着一半勾选，读起来像「上次已经选好了」
    var picked by remember(show) { mutableStateOf(emptySet<String>()) }
    //  这一组键已经有一档了 ⇒ 「添加」按不动：判定那边取第一档，第二档永远轮不到
    //  （同一个成员集合在 `Config.comboFor` 里按配置顺序取靠前的那个），加进去等于配一档死配置。
    val duplicate = picked.size >= ComboKey.MIN_KEYS && cfg.combos.any { it.keys.sorted() == picked.sorted() }
    //  「为什么还不能添加」只有一处判据：`null` = 可以加。点那颗灰按钮时由
    //  `MultiPickerDialog` 的 `onBlockedClick` 把它送进通用消息条 —— 灰着点了没反应最像「坏了」。
    //  颗数超出那一档**不在这里判**：勾满两颗之后第三颗勾不上（见 `onToggle`），走不到这儿。
    val blockedReason =
        when {
            duplicate -> I18n.get(Strings.COMBO_DUPLICATE, "这组按键已经添加过了")
            picked.size < ComboKey.MIN_KEYS -> I18n.format(Strings.COMBO_MIN_KEYS, ComboKey.MIN_KEYS)
            else -> null
        }

    //  样式走公共的多选选择器（`PagePicker`）：与「系统开关」那层三态选择器同一份行高、内缩、
    //  选中底色与对勾 —— 两处是同一类东西（弹窗里挑一列取值），不该各自一套。
    MultiPickerDialog(
        show = show,
        title = I18n.get(Strings.COMBO_PICK_KEYS, "选择按键"),
        //  标题下面那句「选 2 颗」去掉（用户口径）：一个组合就是两颗，标题「选择按键」已经够；
        //  真要选错颗数，勾满两颗之后第三颗勾不上时会有话说（见 `onToggle`）。
        hint = null,
        labels = keys.map { keyName(it) },
        selected = keys.indices.filter { keys[it].id in picked }.toSet(),
        onToggle = { index ->
            val key = keys.getOrNull(index) ?: return@MultiPickerDialog
            val on = key.id in picked
            //  勾满上限之后再点第三颗：**勾不上**，顺手弹条说为什么 —— 只允许两颗这件事
            //  在勾选那一步就说清，别让用户先勾出一个不成立的组合、再去猜「添加」为什么是灰的。
            //  取消勾选永远放行（那是往回收，不受上限管）。
            if (!on && picked.size >= ComboKey.MAX_KEYS) {
                hint(I18n.format(Strings.COMBO_MAX_KEYS, ComboKey.MAX_KEYS), slot = HINT_SLOT_COMBO)
            } else {
                picked = if (on) picked - key.id else picked + key.id
            }
        },
        confirmText = I18n.get(Strings.COMBO_ADD_CONFIRM, "添加"),
        confirmEnabled = blockedReason == null,
        //  点那颗灰按钮 → 弹**通用消息条**（不在弹窗里另画一行）：被拦住的说法全应用只有一种形态。
        //  没原因就是真按不动（不该有这种情况，兜住而已）。
        onBlockedClick = blockedReason?.let { reason -> { hint(reason, slot = HINT_SLOT_COMBO) } },
        onDismiss = onDismiss,
        onConfirm = { onConfirm(keys.filter { it.id in picked }) },
    )
}

/**
 * 这张卡的每一行 —— 一档组合一行，末行「添加组合键」。
 *
 *  抽出来只为让 [ComboScreen] 留在 detekt 的 `LongMethod` 阈值内：这一页只有一件正事
 *  （摆一张卡），而「每一行写什么」本身就有一屏。
 */
private fun SettingsPage.comboRows(
    cfg: Config,
    writable: Boolean,
    atLimit: Boolean,
    /** 只看含这颗键的组合（`null` = 全部）—— 行首的「第几组」仍按**全部**里的位置算。 */
    filter: String?,
): List<CardRow> =
    buildList {
        cfg.combos.forEachIndexed { index, combo ->
            if (filter != null && filter !in combo.keys) return@forEachIndexed
            val dead = comboHasMissingKey(cfg, combo)
            add(
                CardRow.Item(
                    title = comboLabel(cfg, combo, bracketed = true),
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
                    arrow = true,
                    onClick = {
                        XLog.w("组合键：进编辑 ${combo.id}")
                        ComboEditState.open(combo.id)
                        navTo(Screen.COMBO_EDIT)
                    },
                ),
            )
        }
        add(
            CardRow.Item(
                title = I18n.get(Strings.COMBO_ADD, "添加组合按键"),
                //  副标题两档都有：满员说清为什么加不了，没满就说这一步做什么
                summary =
                    if (atLimit) {
                        I18n.format(Strings.COMBO_LIMIT, MAX_COMBOS)
                    } else {
                        I18n.get(Strings.COMBO_ADD_DESC, "选两颗键一起按")
                    },
                icon = PageIcons.MenuKeyAdd,
                enabled = writable && !atLimit,
                //  这一行自己的原因只有「满员」那一档
                disabledHint = if (atLimit) I18n.format(Strings.COMBO_LIMIT, MAX_COMBOS) else null,
                onClick = {
                    ComboEditState.pickKeysPending = true
                },
            ),
        )
    }

/**
 * 列表标题 —— 筛着的时候把「在看哪颗键」接在后面（`组合按键列表 - 侧键`）。
 *
 *  不写的话，从别处回到这一页只会看到一张短了的表：不知道那是筛出来的，第一反应是「组合丢了」。
 *  连接符用半角连字符（用户口径），与页内别处的「键 - 手势」同一套写法。
 */
private fun SettingsPage.comboListTitle(
    cfg: Config,
    filter: String?,
): String {
    val base = I18n.get(Strings.COMBO_LIST_TITLE, "组合按键列表")
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
private fun SettingsPage.comboFilterEntries(
    cfg: Config,
    filter: String?,
    onPick: (String?) -> Unit,
): List<MenuEntry> =
    buildList {
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.COMBO_FILTER_ALL, "全部"),
                icon = PageIcons.MenuComboFingers,
                selected = filter == null,
            ) { onPick(null) },
        )
        //  顺序按**首页「按键列表」那一套**：侧键在前，自定义键按收集顺序（即「第几颗」的号）。
        //  直接拿 `combos.flatMap { it.keys }.distinct()` 的次序是「在组合表里第一次出现的顺序」，
        //  真机上就成了「侧键 / 1 / 3 / 2」这种看不懂的序 —— 那不是任何一处列表的顺序。
        cfg.combos
            .flatMap { it.keys }
            .distinct()
            .mapNotNull { cfg.keyIdOf(it) }
            .sortedBy { key -> cfg.customKeys.indexOfFirst { it.id == key.id } }
            .forEach { key ->
                add(
                    MenuEntry.Row(
                        title = keyName(key),
                        icon = keyFilterIcon(cfg, key),
                        selected = filter == key.id,
                    ) { onPick(key.id) },
                )
            }
    }

/** 筛选菜单里那颗键的行首图标 —— 与首页「按键列表」同一套（侧键那颗 / 第几颗自定义键）。 */
private fun keyFilterIcon(
    cfg: Config,
    key: KeyId,
): ImageVector =
    if (key.isCustom) {
        PageIcons.menuKeyNumber(cfg.customKeys.indexOfFirst { it.id == key.id }.coerceAtLeast(0))
    } else {
        PageIcons.MenuSideKey
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

/**
 * 「组合键设置」那张卡 —— 两行，都不针对某一组：
 *
 *  1. **无操作时执行按键命令**：组合绑的是「无操作」时，两颗键怎么办（开关打开 = 各按自己那一档
 *     照常执行；关闭 = 什么都不做）。为什么值得一个开关：「配了组合、但只给长按绑了动作」很常见，
 *     这时短按那一格是空的，用户要么希望「两颗键各干各的」，要么希望「这颗组合键短按没功能」。
 *  2. **按键之间的等待**：两颗键最多隔多久才算「一起按下」。这一行有代价，副标题里写清楚了。
 */
@Composable
private fun SettingsPage.ComboSettingsCard(enabled: Boolean) {
    val cfg = configState.config.value
    CardList(
        rows =
            listOf(
                CardRow.Switch(
                    title = I18n.get(Strings.COMBO_PASSTHROUGH_TITLE, "无操作时执行按键命令"),
                    summaryOn =
                        I18n.get(Strings.COMBO_PASSTHROUGH_DESC_ON, "组合按键绑定无操作时，两颗键各执行自己的绑定"),
                    summaryOff = I18n.get(Strings.COMBO_PASSTHROUGH_DESC_OFF, "组合按键绑定无操作时什么都不做"),
                    icon = PageIcons.NoMatch,
                    checked = cfg.global.comboPassthrough,
                    enabled = enabled,
                ) { on ->
                    writeAndNotice(
                        cfg.copy(global = cfg.global.copy(comboPassthrough = on)),
                        "combo_passthrough=${if (on) 1 else 0}",
                        slot = HINT_SLOT_COMBO,
                    ) {
                        switchNotice(I18n.get(Strings.COMBO_PASSTHROUGH_TITLE, "无操作时执行按键命令"), on)
                    }
                },
                //  第 2 行：两颗键之间的等待窗口 —— 滑杆另起一支（这一行比开关长得多）
                CardRow.Custom { position -> ComboWindowRow(position, enabled) },
            ),
    )
}

/**
 * 「按键之间的等待」那一行 —— 两颗键按下最多隔多久才算「一起按下」。
 *
 *  **这一行有代价**，副标题里写明：参与组合的键按下去要先等这么久（等第二颗键会不会来），
 *  这段时间里它自己的单击 / 长按都还没开始判。不参与任何组合的键一点不受影响 ——
 *  这一点也得说，否则用户会以为整页都变慢。
 */
@Composable
private fun SettingsPage.ComboWindowRow(
    position: CardListPosition,
    enabled: Boolean,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.COMBO_WINDOW_TITLE, "按键之间的等待"),
                summary = I18n.get(Strings.COMBO_WINDOW_DESC, "调大会让参与组合的按键多等这么久"),
                //  「等一段时间再继续」那一颗钟（与「命令间隔时长」同一个字形）
                icon = PageIcons.DelayStep,
                min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                max = GlobalConfig.COMBO_WINDOW_MAX_MS,
                value = configState.config.value.global.comboWindowMs,
                default = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                //  10ms 一档：20~300 之间 29 档，够细也不至于挪一格要半天
                step = 10,
                enabled = enabled,
                //  行尾写「80 ms」—— 这一行问的就是「等多久」，不写数看不出来
                valueText = { "$it ms" },
            ),
    ) { setComboWindow(it) }
}
