package com.lookie.opluskey.ui.screen.combo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.MultiPickerDialog
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.ComboEditState
import com.lookie.opluskey.ui.switchNotice
import com.lookie.opluskey.ui.writeAndNotice
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
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

    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical()
            .verticalScroll(scroll),
    ) {
        //  与其它页同一档：顶栏叠在内容上，顶部这一块自己让出来（动态值）
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior) + PAGE_TOP_PADDING))
        //  小标题在这张卡上方（用户口径）：一列「有哪几档」需要一个名字，
        //  滚起来之后顶栏折成一条，只看行名认不出这一列是什么。
        SmallTitle(I18n.get(Strings.COMBO_LIST_TITLE, "组合按键列表"))
        CardList(rows = comboRows(cfg, writable, atLimit))
        //  设置块：这一页唯一一个「不针对某一组」的项（见 [ComboPassthroughCard]）
        Spacer(Modifier.height(GRID_ROW_GAP))
        SmallTitle(I18n.get(Strings.COMBO_SETTINGS_SECTION, "组合按键设置"))
        ComboPassthroughCard(writable)
    }

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
): List<CardRow> =
    buildList {
        cfg.combos.forEachIndexed { index, combo ->
            val dead = comboHasMissingKey(cfg, combo)
            add(
                CardRow.Item(
                    title = comboLabel(cfg, combo, bracketed = true),
                    //  行内**不写副标题**（用户口径）：`短按：手电筒 · 长按：…` 一列下来全是小字、
                    //  每行还都不一样长，扫一眼只能看见一片灰。两档动作进页面看，
                    //  这一列只回答「有哪几组」。
                    //  行首用**第几组**的数字图标（1~5，与 `MAX_COMBOS` 同一档，用户口径）：一列下来能数清现在有几组，
                    //  与首页「按键」卡那几颗自定义键同一套写法（`menuKeyNumber`）。
                    //  成员被删掉的那一档换成禁用图标（判定那边凑不齐），数字就看不到了 —— 那一组本来就废了。
                    icon = if (dead) PageIcons.Cancel else PageIcons.menuKeyNumber(index),
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
                onClick = {
                    ComboEditState.pickKeysPending = true
                },
            ),
        )
    }

/**
 * 「组合键设置」那张卡 —— 现在只有一行：组合绑的是「无操作」时，两颗键怎么办。
 *
 *  开关打开：那一下按组合处理（吞掉），然后**两颗键各按自己这一档照常执行**；
 *  关闭：什么都不做（与这一位出现之前一致）。
 *
 *  为什么值得一个开关：「配了组合、但只给长按绑了动作」很常见 —— 这时短按那一格是空的，
 *  用户按下去要么希望「两颗键各干各的」（像没配过组合），要么希望「这颗组合键短按没功能」。
 *  两种都是合理意图，只能由用户定。
 */
@Composable
private fun SettingsPage.ComboPassthroughCard(enabled: Boolean) {
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
            ),
    )
}
