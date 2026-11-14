package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.present.comboKeyNames
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.writeAndNotice
import com.lookie.opluskey.ui.row.ChipFlow
import com.lookie.opluskey.ui.row.ChipLook
import com.lookie.opluskey.ui.row.ChipSentence
import com.lookie.opluskey.ui.screen.combo.holdSentenceTemplate
import com.lookie.opluskey.ui.screen.combo.waitSentenceTemplate
import com.lookie.opluskey.ui.screen.combo.writeNewCombo
import com.lookie.opluskey.ui.state.signal.PendingAsk
import com.lookie.opluskey.ui.style.EmphasisText
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「触发方式」那一族 —— 一档组合**怎么触发**（等窗口 / 先按住第一颗 / 先按住第二颗）。
//
// 它是这一档的**身份**（`ComboKey.mode`），不是「同一档手势底下可切换的几套动作」：
// 三种方式共用 `single` / `long` 这一对槽，所以换方式就是换这一档的判据，而不是换一张动作表。
// 换成另一种就是把这一档改成那种（同一组键眼下只留一档，见 `ComboActions.addCombo` 的去重判据）。
//
// 按住那两档还有一条前置：**起点键要是「松手时触发」** —— 否则按住它到长按阈值时动作
// 已经出去，组合再也凑不成。选那两档时顺带查一次，不是就先问一句。

/**
 * 待确认的那一问 —— 只存「要改哪一颗键」与「切到哪一种触发方式」，确认时同一笔落盘。
 *
 *  与 `KeysAsk` 同一套写法（页面级 object + 一个 [PendingAsk]）：这一问会跨「点选项 → 回答」
 *  两个组合过程，`remember` 在点选那一帧就没了。
 *
 * ## 两种来路，差别只在「那一档现在在不在」
 *
 *  · **改已有的一档**（编辑页 / ⋮ 菜单切方式）：记 [Payload.comboId]，确认时只改方式与键；
 *  · **加新的一档**（选键窗点「添加」）：记 [Payload.addKeys]，**确认之前那一档根本没写进配置**——
 *    取消就等于什么都没发生。这是「那一问不是做样子」的唯一实现方式：先写后问的话，
 *    用户点取消，列表里已经多出一条起点键不合规、永远不成立的废档。
 */
internal object ComboModeAsk {
    /** 一次待确认的载荷：改哪几颗键的长按时机 + 切哪一档组合的哪种触发方式。 */
    class Payload(
        val keys: List<KeyId>,
        val mode: Int,
        /** 非空 = 改**已经存在**的这一档。 */
        val comboId: String? = null,
        /** 非空 = 这一档**还没写**，确认之后才添加（成员就是它）。 */
        val addKeys: List<KeyId>? = null,
    )

    private val pending = PendingAsk<Payload>()

    val asking: Boolean get() = pending.asking

    /** 待确认要改成「松手时触发」的那**一颗**键（空 = 没有待确认的事）。 */
    val keys: List<KeyId> get() = pending.pending?.keys.orEmpty()

    /** 切到哪一档组合（只有「改已有的一档」那条路有）。 */
    val comboId: String? get() = pending.pending?.comboId

    /** 待添加那一档的成员（只有「加新的一档」那条路有）。 */
    val addKeys: List<KeyId>? get() = pending.pending?.addKeys

    /** 切到哪一种触发方式（[ComboKey.MODE_HOLD_FIRST] / [ComboKey.MODE_HOLD_SECOND]）。 */
    val mode: Int get() = pending.pending?.mode ?: ComboKey.MODE_WAIT

    /** 改**已有的一档**的方式。 */
    fun ask(
        keys: List<KeyId>,
        comboId: String,
        mode: Int,
    ) {
        pending.set(Payload(keys = keys, mode = mode, comboId = comboId))
    }

    /** **添加**一档按住组合（还没写进配置，确认之后才写）。 */
    fun askAdd(
        starter: List<KeyId>,
        keys: List<KeyId>,
        mode: Int,
    ) {
        pending.set(Payload(keys = starter, mode = mode, addKeys = keys))
    }

    fun clear() = pending.clear()
}

/**
 * 切**已有那一档**的触发方式 —— 按住那两档要先看**起点键**是不是「松手时触发」：
 * 是就直接写；不是就先问一句（[ComboModeAsk]），确认后同一次落盘（改它的长按时机 + 切方式）。
 *
 *  **眼下界面没有调用点**：加了「按住组合两档各自独立」之后，一档只认一种触发方式，
 * 想两种按法就在两列里各建一档 —— 没有「在这一档上改方式」这条路（见 `ComboActions` 的
 * `addCombo`）。留着它是那条语义的唯一实现，将来真要有入口（比如列表 ⋮ 菜单）直接接上即可，
 * 判据不会散成两份。
 */
internal fun SettingsPage.setComboMode(
    combo: ComboKey,
    mode: Int,
) {
    //  三颗及以上只有等窗口：`modeFor` 是唯一判据，界面这一层不再各写一遍
    val target = ComboKey.modeFor(mode, combo.keys.size)
    if (target == ComboKey.MODE_WAIT) {
        writeComboMode(emptyList(), combo.id, target)
        return
    }
    val starter = combo.starterKey?.let { configState.config.value.keyIdOf(it) }
    if (starter == null) {
        XLog.w("触发方式：这一档的起点键认不出（配置被手改过），不改")
        return
    }
    if (configState.config.value
            .key(starter)
            .longFireOnRelease
    ) {
        writeComboMode(emptyList(), combo.id, target)
        return
    }
    XLog.w("触发方式：${starter.label} 还不是「松手时触发」，先问一次（${combo.id} / mode $target）")
    ComboModeAsk.ask(listOf(starter), combo.id, target)
}

/**
 * 真正写下去的那一笔 —— **切方式** + 起点键的长按时机，一次落盘（理由同上）。
 *
 * @param keys 要改成「松手时触发」的那几颗键（空 = 不用动键）
 */
private fun SettingsPage.writeComboMode(
    keys: List<KeyId>,
    comboId: String,
    mode: Int,
) {
    val cfg = configState.config.value
    val keysNext =
        if (keys.isEmpty()) {
            cfg.keys
        } else {
            val change = keys.toHashSet()
            cfg.keys.mapValues { (id, key) -> if (id in change) key.copy(longFireOnRelease = true) else key }
        }
    val next =
        cfg.copy(
            keys = keysNext,
            combos =
                cfg.combos.map { c ->
                    //  成员数变了之后那一位可能不再合法（2 → 3 颗），统一过一遍 `modeFor`
                    if (c.id == comboId) c.copy(mode = ComboKey.modeFor(mode, c.keys.size)) else c
                },
        )
    val what =
        buildString {
            append("combo:$comboId:mode=$mode")
            if (keys.isNotEmpty()) append(" fire_on_release=${keys.joinToString(",") { it.id }}")
        }
    val combo = next.combos.firstOrNull { it.id == comboId }
    writeAndNotice(next, what, slot = HINT_SLOT_COMBO, chips = { modeNames(next, combo) }) {
        I18n.get(Strings.COMBO_MODE_SWITCHED, "已切换触发方式：") + modeText(mode, combo)
    }
}

/**
 * 回执里那一段「按住 A 后再按 B、C」—— **模板**（`%s` 逐颗名字），名字由 [modeNames] 给。
 *
 *  与 `writeAndNotice` 的 chips 是同一句话的两半：模板里 `%s` 的个数必须与名字个数一致，
 *  所以顺序只在一处（[modeNames]）定，模板这边只管语序 —— 成员数不定，模板按 [combo] 的
 *  颗数现算（[holdSentenceTemplate] / [waitSentenceTemplate]）。
 */
private fun modeText(
    mode: Int,
    combo: ComboKey?,
): String {
    val count = combo?.keys?.size ?: ComboKey.MIN_KEYS
    return if (mode == ComboKey.MODE_HOLD) {
        holdSentenceTemplate(count - 1)
    } else {
        waitSentenceTemplate(count)
    }
}

/** [modeText] 模板里那几枚框各是哪个名字 —— 按住档首键在前（`keys` 第一位就是首键）。 */
private fun modeNames(
    cfg: Config,
    combo: ComboKey?,
): List<String> = combo?.let { comboKeyNames(cfg, it) }.orEmpty()

/**
 * 「这一种触发方式需要起点键松手时触发」那一问 —— 只在**组合动作页**挂一次。
 *
 *  取消 / 点蒙层 = 什么都不写：添加那条路上，那一档此刻**还没进配置**（见 [ComboModeAsk]）。
 */
@Composable
internal fun SettingsPage.ComboModeAskDialog() {
    val mine = ComboModeAsk.asking
    //  **照最后画过的那一份画**：`clear()` 发生在退场之前，载荷一空 `label` 也跟着空 ——
    //  不退场那一帧正文会突然变成半句话（`ChipSentence` 那边也得跟着兜底，见其注释）。
    //  与 `FireReleaseOffDialog` 的 `LastFireOff` 同一个理由。
    val last = remember { LastAskLabel() }
    val live =
        ComboModeAsk.keys
            .firstOrNull()
            ?.label
    if (live != null) last.value = live
    val label = live ?: last.value ?: return
    //  正文那两档：与 `DialogBody` 里 `EmphasisText` 同一份（body2 + onBackgroundVariant），
    //  否则同一张窗里句子与名字框两种字号（与 `FireReleaseOffDialog` 同一处口径）
    val textStyle = COUITheme.textStyles.body2
    val textColor = COUITheme.colorScheme.onBackgroundVariant
    ConfirmDialog(
        show = mine,
        title = I18n.get(Strings.COMBO_MODE_ASK_TITLE, "按住手势需要松手时触发"),
        action = I18n.get(Strings.COMBO_MODE_ASK_ACTION, "切换并添加"),
        //  不是破坏性动作：这一步是把长按挪到抬手执行，好让「按住久一点再补一颗」成立
        positiveDanger = false,
        onDismiss = { ComboModeAsk.clear() },
        onConfirm = {
            val keys = ComboModeAsk.keys
            val target = ComboModeAsk.comboId
            val addKeys = ComboModeAsk.addKeys
            val mode = ComboModeAsk.mode
            ComboModeAsk.clear()
            if (mode != ComboKey.MODE_WAIT) {
                when {
                    //  **添加那条路**：这一档至此才真正写进配置（连同起点键的长按时机，一笔落盘）。
                    //  第二参是**首键**：`keys` 里那一颗就是刚才要改「松手时触发」的起点键。
                    addKeys != null -> writeNewCombo(addKeys, keys.firstOrNull(), releaseKeys = keys)

                    //  改已有那一档的方式：只改方式 + 键
                    target != null -> writeComboMode(keys, target, mode)
                }
            }
        },
        //  正文与「关掉松手时触发」那一问同一套画法：句子 + 名字框（键名在这个界面里一直是框）
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(FIRE_OFF_PARAGRAPH_GAP)) {
                ChipSentence(
                    template = I18n.get(Strings.COMBO_MODE_ASK_LEAD, "%s 的长按目前会在按住到时就执行，按住手势会被它打断。切换为松手时触发并添加？"),
                    names = listOf(label),
                    look = ChipLook(textStyle, textColor, enabled = true),
                    style = textStyle,
                    color = textColor,
                )
                EmphasisText(
                    text = I18n.get(Strings.COMBO_MODE_ASK_TAIL, "这颗键的连续触发会随之失效。"),
                    textAlign = TextAlign.Start,
                )
            }
        },
    )
}

/**
 * 这一颗键**正被哪些组合当成起点**（按住那两档的起点）。
 *
 *  关掉它的「松手时触发」会让这些组合不再成立：判定侧那条上界（`longPressPassed`）把
 *  「已经执行过自己动作」的键排除在按住之外。
 */
internal fun Config.combosStartedBy(keyId: KeyId): List<ComboKey> = combos.filter { it.isHold && it.starterKey == keyId.id }

/**
 * 「关掉松手时触发」之前的那一问 —— 这颗键是某些按住档的起点时弹（只弹一次、一次说清全部）。
 */
internal object FireReleaseOffAsk {
    /** 一次待确认的载荷：哪颗键 + 受影响的那些组合。 */
    class Payload(
        val key: KeyId,
        val combos: List<ComboKey>,
    )

    private val pending = PendingAsk<Payload>()

    val asking: Boolean get() = pending.asking

    /** 待确认的那颗键（`null` = 没有待确认的事）。 */
    val key: KeyId? get() = pending.pending?.key

    /** 受影响的组合（去重；正文里逐个列名字）。 */
    val combos: List<ComboKey> get() = pending.pending?.combos.orEmpty()

    fun ask(
        key: KeyId,
        combos: List<ComboKey>,
    ) {
        pending.set(Payload(key, combos))
    }

    fun clear() = pending.clear()
}

/**
 * 拨「松手时触发」的**唯一入口**（单键那一页把它接在开关上）。
 *
 *  关掉时先看这颗键是不是某些按住档的起点：不是就直接写；是就先问一句（[FireReleaseOffAsk]），
 *  确认后**同一次落盘**关掉这一位、并把这些组合退回「等窗口」—— 留着它们只会是一个永远
 *  不成立的档（动作还在，换回等窗口照样能用）。
 */
internal fun SettingsPage.setFireOnRelease(
    key: KeyId,
    on: Boolean,
) {
    if (on) {
        writeFireOnRelease(key, on = true, resetStarters = false)
        return
    }
    val affected =
        configState.config.value
            .combosStartedBy(key)
            .distinctBy { it.id }
    if (affected.isEmpty()) {
        writeFireOnRelease(key, on = false, resetStarters = false)
        return
    }
    XLog.w("松手时触发：${key.label} 是 ${affected.size} 档按住组合的起点，先问一次")
    FireReleaseOffAsk.ask(key, affected)
}

/**
 * 落盘那一位（必要时把以它为起点的组合退回等窗口）。
 *
 *  @param resetStarters 关掉「松手时触发」时把**以这颗键为起点**的按住档改成等窗口
 */
private fun SettingsPage.writeFireOnRelease(
    key: KeyId,
    on: Boolean,
    resetStarters: Boolean,
) {
    val cfg = configState.config.value
    val combos =
        if (!resetStarters) {
            cfg.combos
        } else {
            cfg.combos.map { combo ->
                if (combo.isHold && combo.starterKey == key.id) combo.copy(mode = ComboKey.MODE_WAIT) else combo
            }
        }
    val next =
        cfg.copy(
            keys = cfg.keys + (key to cfg.key(key).copy(longFireOnRelease = on)),
            combos = combos,
        )
    val what =
        if (resetStarters) {
            "long_fire_on_release=0 mode=wait_of=${key.id}"
        } else {
            "long_fire_on_release=${if (on) 1 else 0}:${key.id}"
        }
    writeAndNotice(next, what, slot = HINT_SLOT_COMBO) {
        I18n
            .get(
                if (on) Strings.TOAST_SWITCH_ON else Strings.TOAST_SWITCH_OFF,
                if (on) "已开启 [%s]" else "已关闭 [%s]",
            ).format(I18n.get(Strings.BINDING_LONG_PRESS_FIRE_ON_RELEASE_TITLE, "松手时触发"))
    }
}

/** 退场那两百毫秒里要画的那一份（见 [ComboModeAskDialog]）—— 只留正文要念的那颗键名。 */
private class LastAskLabel {
    var value: String? = null
}

/**
 * 「这颗键只参与组合，当不了按住起点」那一道拦截。
 *
 *  与 [ComboModeAsk] 分开两个状态：那一条是**可以继续**的问句（确认后改键 + 添加），
 *  这一条是**到此为止**的说明 —— 没有可以替他做的补救（那颗键的「松手时触发」在设置页
 *  本来就是灰的，写了也是自相矛盾的配置）。所以这里只记「哪一颗键」，确认即为关闭。
 */
internal object ComboStarterBlocked {
    /** 挡下来的那一条原因 —— 决定弹窗标题与正文。 */
    enum class Reason {
        /** 拿它当**按住组合的起点**（要求「按住到点再松手」，与不判单键手势互斥）。 */
        HOLD_STARTER,

        /** 把它加进**等待组合**（按下要吞一个窗口，与「单键归系统」叠起来两头不讨好）。 */
        WAIT_MEMBER,
    }

    private val pending = PendingAsk<KeyId>()

    val asking: Boolean get() = pending.asking

    /** 阻挡这一档的那颗起点键（`null` = 没有待说明的事）。 */
    val key: KeyId? get() = pending.pending

    /** 这一次拦下来的原因；退场期间沿用上一次那一份（见 [LastBlocked]）。 */
    var reason: Reason = Reason.HOLD_STARTER
        private set

    fun ask(
        key: KeyId,
        why: Reason,
    ) {
        reason = why
        pending.set(key)
    }

    fun clear() = pending.clear()
}

/**
 * 上面那一道拦截的窗口 —— 只在这一个页面上绘制一次（与 [ComboModeAskDialog] 并排）。
 *
 *  正文里那颗键名是**名字框**（与别处同一套记号），句式交给模板、框交给界面（[ChipSentence]）。
 *  退场时照最后画过的那一份画（[LastAskLabel]），否则那两百毫秒里键名会先变空。
 */
@Composable
internal fun SettingsPage.ComboStarterBlockedDialog() {
    val live = ComboStarterBlocked.key
    val last = remember { LastBlocked() }
    if (live != null) last.value = live.label
    //  从没开过时整支不画；开着、或正在退场时照那一份画（见 [LastBlocked]）
    val label = live?.label ?: last.value ?: return
    val textStyle = COUITheme.textStyles.body2
    val textColor = COUITheme.colorScheme.onBackgroundVariant
    ConfirmDialog(
        show = live != null,
        title =
            if (ComboStarterBlocked.reason == ComboStarterBlocked.Reason.WAIT_MEMBER) {
                I18n.get(Strings.COMBO_MEMBER_BLOCKED_TITLE, "无法加入等待组合")
            } else {
                I18n.get(Strings.COMBO_STARTER_BLOCKED_TITLE, "无法用作按住起点")
            },
        action = I18n.get(Strings.COMBO_STARTER_BLOCKED_ACTION, "知道了"),
        positiveDanger = false,
        //  这一条没有「确认之后做点什么」—— 两颗按钮都只是把窗关掉
        onDismiss = { ComboStarterBlocked.clear() },
        onConfirm = { ComboStarterBlocked.clear() },
        body = {
            ChipSentence(
                template =
                    if (ComboStarterBlocked.reason == ComboStarterBlocked.Reason.WAIT_MEMBER) {
                        I18n.get(
                            Strings.COMBO_MEMBER_BLOCKED_BODY,
                            "%s 已设为只参与组合，无法加入等待组合。",
                        )
                    } else {
                        I18n.get(
                            Strings.COMBO_STARTER_BLOCKED_BODY,
                            "%s 已设为只参与组合，无法用它作为按住手势的起点。",
                        )
                    },
                names = listOf(label),
                look = ChipLook(textStyle, textColor, enabled = true),
                style = textStyle,
                color = textColor,
            )
        },
    )
}

/**
 * 拦截窗退场时的那一份键名 —— 与 [LastAskLabel] 同一个理由：清载荷发生在退场之前，
 * 不留一份的话那两百毫秒里正文会先变空。
 */
private class LastBlocked {
    var value: String? = null
}

/**
 * 「这颗键已经是按住组合的起点，不能设成只参与组合」那一道拦截 —— 与 [ComboStarterBlocked]
 * **方向相反**：
 *
 *  · [ComboStarterBlocked]：先开了「只参与组合」，再想拿它当按住起点 ⇒ 拦「加组合」那一边；
 *  · 这一条：先拿它当了按住起点，再想开「只参与组合」⇒ 拦「改这颗键」那一边。
 *
 *  两条说的是同一个矛盾的两端（起点键必须「按住到点」，而只参与组合的键不判单键手势），
 *  所以判据是同一句（`Config.holdComboStartedBy` / `ComboKey.starterKey`），只是问的时机不同。
 *
 *  与 [ComboModeAsk] 也分开：那一条是**可以继续**的问句（确认后替他改键 + 添加），这一条是
 *  **到此为止**的说明 —— 没有可以替他做的补救（真替他删掉那一档组合？用户只是想开个开关）。
 *  所以只记「哪一颗键」，确认即为关闭。
 */
internal object ComboOnlyBlocked {
    /** 挡下来的那一条原因 —— 决定弹窗正文怎么说。 */
    enum class Reason {
        /** 这颗键是某档**按住组合的起点**（要求「按住到点再松手」，与不判单键手势互斥）。 */
        HOLD_STARTER,

        /** 这颗键是某档**等待组合的成员**（按下要吞一个窗口，两头都不讨好）。 */
        WAIT_MEMBER,
    }

    private val pending = PendingAsk<KeyId>()

    val asking: Boolean get() = pending.asking

    /** 拦下这一下的那颗键（`null` = 没有待说明的事）。 */
    val key: KeyId? get() = pending.pending

    /** 这一次拦下来的原因；退场期间沿用上一次那一份（见 [LastComboOnlyBlocked]）。 */
    var reason: Reason = Reason.HOLD_STARTER
        private set

    fun ask(
        key: KeyId,
        why: Reason,
    ) {
        reason = why
        pending.set(key)
    }

    fun clear() = pending.clear()
}

/**
 * 上面那一道拦截的窗口 —— 挂在「独立设置」页（开关就在那一页）。
 *
 *  正文分两段：一句说明**为什么不行**（键名是名字框，与别处同一套记号），一句告诉用户
 *  **怎么解**。第二句不能省 —— 这一条不像「起点键只参与组合」那样无可挽回，用户改掉那一档
 *  组合就能回来开这个开关；只说「不行」会让人以为这一项坏了。
 */
@Composable
internal fun SettingsPage.ComboOnlyBlockedDialog() {
    val live = ComboOnlyBlocked.key
    val last = remember { LastComboOnlyBlocked() }
    if (live != null) last.value = live.label
    //  从没开过时整支不画；开着、或正在退场时照那一份画（见 [LastComboOnlyBlocked]）
    val label = live?.label ?: last.value ?: return
    val textStyle = COUITheme.textStyles.body2
    val textColor = COUITheme.colorScheme.onBackgroundVariant
    ConfirmDialog(
        show = live != null,
        title = I18n.get(Strings.COMBO_ONLY_BLOCKED_TITLE, "无法只参与组合"),
        action = I18n.get(Strings.COMBO_ONLY_BLOCKED_ACTION, "知道了"),
        positiveDanger = false,
        //  这一条没有「确认之后做点什么」—— 两颗按钮都只是把窗关掉
        onDismiss = { ComboOnlyBlocked.clear() },
        onConfirm = { ComboOnlyBlocked.clear() },
        body = {
            val waitMember = ComboOnlyBlocked.reason == ComboOnlyBlocked.Reason.WAIT_MEMBER
            ChipSentence(
                template =
                    if (waitMember) {
                        I18n.get(
                            Strings.COMBO_ONLY_BLOCKED_WAIT_BODY,
                            "%s 是某一档等待组合的成员，不能设为只参与组合。",
                        )
                    } else {
                        I18n.get(
                            Strings.COMBO_ONLY_BLOCKED_BODY,
                            "%s 是某一档按住组合的起点，不能设为只参与组合。",
                        )
                    },
                names = listOf(label),
                look = ChipLook(textStyle, textColor, enabled = true),
                style = textStyle,
                color = textColor,
            )
            //  出路那一句：与上面同一档字号与颜色，只是不夹名字框
            Text(
                text =
                    if (waitMember) {
                        //  等待组合的成员没有「改一档」这条路：它就是这一档的一颗成员，
                        //  要么删掉那一档、要么把它换成别的键
                        I18n.get(
                            Strings.COMBO_ONLY_BLOCKED_WAIT_TAIL,
                            "先把那一档组合删掉，或者把它换成别的键。",
                        )
                    } else {
                        I18n.get(
                            Strings.COMBO_ONLY_BLOCKED_TAIL,
                            "先把那一档组合改成等待组合，或者删掉它。",
                        )
                    },
                style = textStyle,
                color = textColor,
                modifier = Modifier.fillMaxWidth().padding(top = BLOCKED_TAIL_GAP),
            )
        },
    )
}

/** 拦截窗退场时的那一份键名 —— 与 [LastBlocked] 同一条账。 */
private class LastComboOnlyBlocked {
    var value: String? = null
}

/** 拦截窗正文两段之间那一档 —— 与别处「一句引导 + 一句后果」同一个量（见 `FireOff` 那两支）。 */
private val BLOCKED_TAIL_GAP = 6.dp

/**
 * 「关掉它会让按住手势失效」那一问 —— 只在这一页挂一次；取消 / 点蒙层 = 什么都不改。
 *
 * ## 为什么正文自己画（不交给 `summary`）
 *
 *  键名在这个界面里一直是**一枚圆角框**（`NameChip`）：列表行、顶栏副标题、组合标题都用它，
 *  正文里写成一对方括号 `[侧键]` 就成了另一套记号（用户口径「正文的键名用 tag 样式」）。
 *  所以正文走可组合正文槽（`ConfirmDialog.body`），句子本体用 [ChipSentence] 画 ——
 *  语序仍在模板里（`%s 是这些组合里先按住的那一颗：`），框仍归界面。
 *
 * ## 为什么不能 `if (target == null) return`
 *
 *  `show` 是「这一问还在不在」，而清载荷发生在那之前 —— 整支不画的话，窗会在同一帧从组合里
 *  消失，退场动画来不及播（用户口径「也没有出场动画」）。所以画的内容照**最后画过的那一份**
 *  （[LastFireOff]），壳自己按 `show` 播进出场。
 */
@Composable
internal fun SettingsPage.FireReleaseOffDialog() {
    val target = FireReleaseOffAsk.key
    val combos = FireReleaseOffAsk.combos
    val last = remember { LastFireOff() }
    if (target != null) {
        last.key = target
        last.combos = combos
    }
    val shownKey = target ?: last.key
    //  **从没开过**时什么都不画；开着、或正在退场时照那一份画
    if (shownKey == null) return
    val shownCombos = if (target != null) combos else last.combos
    val cfg = configState.config.value
    //  退场那两百毫秒不再派发（与 `PickerRow` 的 `interactive` 同一条）
    val live = target != null
    //  正文那几段文字的样式：与 `DialogBody` 里 `EmphasisText` 同一档（body2 + onBackgroundVariant），
    //  否则同一张窗里两种正文两种字号。
    val textStyle = COUITheme.textStyles.body2
    val textColor = COUITheme.colorScheme.onBackgroundVariant
    ConfirmDialog(
        show = live,
        title = I18n.get(Strings.BINDING_FIRE_OFF_TITLE, "关掉 [松手时触发] 会让按住手势失效"),
        //  **动作项报个数**（用户口径「关闭并改回等窗口 → 禁用 N 个组合」）：这一步真正发生的是
        //  那几档按住组合被退回等窗口，数目比「改回等窗口」这个动词更直接。
        action = I18n.format(Strings.BINDING_FIRE_OFF_ACTION, shownCombos.size),
        positiveDanger = true,
        onDismiss = { FireReleaseOffAsk.clear() },
        onConfirm = {
            FireReleaseOffAsk.clear()
            writeFireOnRelease(shownKey, on = false, resetStarters = true)
        },
        //  正文：一句引导 + 每一档一行键名框 + 一句后果。三段之间留一档（与 `EmphasisText`
        //  自己的 `\n\n` 分段同一个观感），所以这里自己给段间距。
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(FIRE_OFF_PARAGRAPH_GAP)) {
                ChipSentence(
                    template = I18n.get(Strings.BINDING_FIRE_OFF_LEAD, "%s 是这些组合里先按住的那一颗："),
                    names = listOf(shownKey.label),
                    look = ChipLook(textStyle, textColor, enabled = true),
                    style = textStyle,
                    color = textColor,
                )
                Column(verticalArrangement = Arrangement.spacedBy(FIRE_OFF_COMBO_GAP)) {
                    shownCombos.forEach { combo ->
                        //  组合本身仍按列表那一行的读法：`侧键 × 音量 +`，键名各是一枚框
                        ChipFlow(
                            names = comboKeyNames(cfg, combo),
                            look = ChipLook(textStyle, textColor, enabled = true),
                        )
                    }
                }
                EmphasisText(
                    text = I18n.get(Strings.BINDING_FIRE_OFF_TAIL, "继续会同时移除这几种按住顺序。"),
                    textAlign = TextAlign.Start,
                )
            }
        },
    )
}

/** 退场那两百毫秒里要画的那一份（见 [FireReleaseOffDialog]）。 */
private class LastFireOff {
    var key: KeyId? = null
    var combos: List<ComboKey> = emptyList()
}

/** 正文三段之间的间距 —— 与 `EmphasisText` 分段那一档（`\n\n`）同一个观感。 */
private val FIRE_OFF_PARAGRAPH_GAP = 12.dp

/** 正文里每一档组合之间的间距 —— 比段间距小一档，读起来是「同一段里的几个例子」。 */
private val FIRE_OFF_COMBO_GAP = 6.dp
