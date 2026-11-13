package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.present.comboKeyNames
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.writeAndNotice
import com.lookie.opluskey.ui.state.signal.PendingAsk

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
 */
internal object ComboModeAsk {
    /** 一次待确认的载荷：改哪几颗键的长按时机 + 切哪一档组合的哪种触发方式。 */
    class Payload(
        val keys: List<KeyId>,
        val comboId: String,
        val mode: Int,
    )

    private val pending = PendingAsk<Payload>()

    val asking: Boolean get() = pending.asking

    /** 待确认要改成「松手时触发」的那**一颗**键（空 = 没有待确认的事）。 */
    val keys: List<KeyId> get() = pending.pending?.keys.orEmpty()

    /** 切到哪一档组合。 */
    val comboId: String? get() = pending.pending?.comboId

    /** 切到哪一种触发方式（[ComboKey.MODE_HOLD_FIRST] / [ComboKey.MODE_HOLD_SECOND]）。 */
    val mode: Int get() = pending.pending?.mode ?: ComboKey.MODE_WAIT

    fun ask(
        keys: List<KeyId>,
        comboId: String,
        mode: Int,
    ) {
        pending.set(Payload(keys, comboId, mode))
    }

    fun clear() = pending.clear()
}

/**
 * 切这一档的**触发方式** —— 唯一入口（选择窗与 ⋮ 菜单都走它）。
 *
 *  按住那两档要先看**起点键**是不是「松手时触发」：是就直接写；不是就先问一句
 *  （[ComboModeAsk]），确认后同一次落盘（改它的长按时机 + 切方式）。
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
    writeAndNotice(next, what, slot = HINT_SLOT_COMBO) {
        I18n.get(Strings.COMBO_MODE_SWITCHED, "已切换触发方式：") + modeText(mode, next, combo)
    }
}

/** 回执里那一段「按住 A 后再按 B」—— 用这一档成员的名字拼。 */
private fun modeText(
    mode: Int,
    cfg: Config,
    combo: ComboKey?,
): String {
    val names = combo?.let { comboKeyNames(cfg, it) }.orEmpty()
    val first = names.getOrNull(0).orEmpty()
    val second = names.getOrNull(1).orEmpty()
    return when (mode) {
        ComboKey.MODE_HOLD_FIRST -> I18n.format(Strings.COMBO_MODE_HOLD, first, second)
        ComboKey.MODE_HOLD_SECOND -> I18n.format(Strings.COMBO_MODE_HOLD, second, first)
        else -> I18n.format(Strings.COMBO_MODE_WAIT, first, second)
    }
}

/**
 * 「这一种触发方式需要起点键松手时触发」那一问 —— 只在**组合动作页**挂一次。
 *
 *  取消 / 点蒙层 = 什么都不写（方式没切、键也没改）。
 */
@Composable
internal fun SettingsPage.ComboModeAskDialog() {
    val mine = ComboModeAsk.asking
    val label =
        ComboModeAsk.keys
            .firstOrNull()
            ?.label
            .orEmpty()
    ConfirmDialog(
        show = mine,
        title = I18n.get(Strings.COMBO_MODE_ASK_TITLE, "按住手势需要松手时触发"),
        summary = I18n.format(Strings.COMBO_MODE_ASK_BODY, label),
        action = I18n.get(Strings.COMBO_MODE_ASK_ACTION, "切换并添加"),
        //  不是破坏性动作：这一步是把长按挪到抬手执行，好让「按住久一点再补一颗」成立
        positiveDanger = false,
        onDismiss = { ComboModeAsk.clear() },
        onConfirm = {
            val keys = ComboModeAsk.keys
            val target = ComboModeAsk.comboId
            val mode = ComboModeAsk.mode
            ComboModeAsk.clear()
            if (target != null && mode != ComboKey.MODE_WAIT) writeComboMode(keys, target, mode)
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

/**
 * 「关掉它会让按住手势失效」那一问 —— 只在这一页挂一次；取消 / 点蒙层 = 什么都不改。
 */
@Composable
internal fun SettingsPage.FireReleaseOffDialog() {
    val target = FireReleaseOffAsk.key
    val combos = FireReleaseOffAsk.combos
    if (target == null) return
    val cfg = configState.config.value
    ConfirmDialog(
        show = true,
        title = I18n.get(Strings.BINDING_FIRE_OFF_TITLE, "关掉 [松手时触发] 会让按住手势失效"),
        //  正文里列的是**这些组合本身**（`[快捷键]×[音量加]`，名字各带一对括号）：
        //  用户要判断的正是「这几档是不是我在用的那几档」。
        summary =
            I18n.format(
                Strings.BINDING_FIRE_OFF_BODY,
                target.label,
                combos.joinToString("\n") { comboLabel(cfg, it) },
            ),
        action = I18n.get(Strings.BINDING_FIRE_OFF_ACTION, "关闭并改回等窗口"),
        positiveDanger = true,
        onDismiss = { FireReleaseOffAsk.clear() },
        onConfirm = {
            FireReleaseOffAsk.clear()
            writeFireOnRelease(target, on = false, resetStarters = true)
        },
    )
}
