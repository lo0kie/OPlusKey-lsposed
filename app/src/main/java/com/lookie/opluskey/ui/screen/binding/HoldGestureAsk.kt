package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.writeAndNotice
import com.lookie.opluskey.ui.state.binding.ComboEditState

// 「添加一种按住顺序」之前的那一步。
//
//  按住手势是「按住一颗、再按另一颗」—— **先按住的那一颗**常常要按一会儿。它的长按若在按住
//  期间就执行了，这一按就同时出了两个动作；判定侧那条上界（`SideKeyInterceptor` 传给
//  `ComboDetector` 的 `longPressPassed`）也会在那一刻把组合判据整条截止，于是「按住久一点」
//  这个最自然的用法反而按不出来。
//
//  所以每加一种顺序（先按住 A / 先按住 B）都要看一下**那一颗**键的长按时机：
//  已经是「松手时触发」⇒ 直接加；不是 ⇒ 先问一句「切换」（`KeyConfig.longFireOnRelease`）。
//  只查那一颗，另一颗只是后补的那一下 —— 它不需要改（用户口径）。

/**
 * 待确认的那一问 —— 只存「要改哪一颗键」与「加到哪一组的哪一种顺序」，确认时同一笔落盘。
 *
 *  与 `KeysAsk` 同一套写法（页面级 object + 一页挂一次窗）：这一问会跨「点添加 → 回答」两个
 *  组合过程，`remember` 在点添加那一帧就没了。
 */
internal object HoldGestureAsk {
    /** 待确认要改成「松手时触发」的那**一颗**键（空 = 没有待确认的事）。 */
    var keys by mutableStateOf<List<KeyId>>(emptyList())
        private set

    /** 加到哪一组组合（`null` = 没有待确认的事）。 */
    var comboId by mutableStateOf<String?>(null)
        private set

    /** 加的是哪一种顺序（[ComboKey.HOLD_FIRST] / [ComboKey.HOLD_SECOND]）。 */
    var order by mutableStateOf(0)
        private set

    /** 有没有在等回答的事。 */
    val asking: Boolean get() = keys.isNotEmpty()

    fun ask(
        keys: List<KeyId>,
        comboId: String?,
        order: Int,
    ) {
        this.keys = keys
        this.comboId = comboId
        this.order = order
    }

    fun clear() {
        keys = emptyList()
        comboId = null
        order = 0
    }
}

/**
 * 往这一组里**加一种按住顺序** —— 先看先按住的那一颗键的长按时机。
 *
 * @param order [ComboKey.HOLD_FIRST] / [ComboKey.HOLD_SECOND]
 */
internal fun SettingsPage.addHoldOrder(
    combo: ComboKey,
    order: Int,
) {
    val cfg = configState.config.value
    //  先按住的是第几颗：`HOLD_FIRST` = 成员里的第一颗，`HOLD_SECOND` = 第二颗
    val key = cfg.keyIdOf(combo.keys.getOrNull(order - 1).orEmpty())
    if (key == null) {
        XLog.w("按住顺序：这一组的第 $order 颗成员认不出（配置被手改过），不加")
        return
    }
    if (cfg.key(key).longFireOnRelease) {
        writeHoldOrder(emptyList(), combo.id, order)
        return
    }
    XLog.w("按住顺序：${key.label} 还不是「松手时触发」，先问一次（${combo.id} / 顺序 $order）")
    HoldGestureAsk.ask(listOf(key), combo.id, order)
}

/**
 * 真正写下去的那一笔 —— **加入顺序** + 那颗键的长按时机，一次落盘（理由同上）。
 *
 *  加顺序同时把这一组的「使用按住手势」打开：顺序本身就是「这一组要认按住」的明确表态，
 *  否则加完发现按不出来还得回独立设置再拨一次。
 *
 * @param keys 要改成「松手时触发」的那几颗键（空 = 不用动键）
 */
private fun SettingsPage.writeHoldOrder(
    keys: List<KeyId>,
    comboId: String,
    order: Int,
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
                    if (c.id == comboId) {
                        c.copy(holdOn = true, holdOrders = (c.holdOrders + order).distinct().sorted())
                    } else {
                        c
                    }
                },
        )
    val what =
        buildString {
            append("combo:$comboId:hold_order=$order")
            if (keys.isNotEmpty()) append(" fire_on_release=${keys.joinToString(",") { it.id }}")
        }
    writeAndNotice(next, what, slot = HINT_SLOT_COMBO) {
        I18n.format(Strings.COMBO_HOLD_ORDER_ADDED, holdOrderLabel(order))
    }
    //  加完就切到这一套上：用户刚点的是「加这一种顺序」，下一步必然是想配它的动作
    ComboEditState.selectHold(ComboEditState.page.coerceIn(0, COMBO_HOLD_PAGES - 1), order)
}

/**
 * 「这一种顺序需要松手时触发」那一问 —— 只在**组合动作页**挂一次（顺序是那一页的东西）。
 *
 *  取消/点蒙层 = 什么都不写（顺序没加、键也没改）。
 */
@Composable
internal fun SettingsPage.HoldGestureAskDialog() {
    val mine = HoldGestureAsk.asking && HoldGestureAsk.comboId != null
    //  这一问只针对**先按住的那一颗**：正文里点名，用户才知道改的是哪一颗
    val label =
        HoldGestureAsk.keys
            .firstOrNull()
            ?.label
            .orEmpty()
    ConfirmDialog(
        show = mine,
        title = I18n.get(Strings.COMBO_HOLD_ASK_TITLE, "按住手势需要松手时触发"),
        summary = I18n.format(Strings.COMBO_HOLD_ASK_BODY, label),
        action = I18n.get(Strings.COMBO_HOLD_ASK_ACTION, "切换并添加"),
        //  不是破坏性动作：这一步是把长按挪到抬手执行，好让「按住久一点再补一颗」成立
        positiveDanger = false,
        onDismiss = { HoldGestureAsk.clear() },
        onConfirm = {
            val pending = HoldGestureAsk.keys
            val target = HoldGestureAsk.comboId
            val order = HoldGestureAsk.order
            HoldGestureAsk.clear()
            if (target != null && order != 0) writeHoldOrder(pending, target, order)
        },
    )
}

/** 这一种顺序的界面名字 —— 只用于回执那一句（`先按住 A` 这种由菜单自己拼）。 */
private fun holdOrderLabel(order: Int): String =
    if (order == ComboKey.HOLD_FIRST) {
        I18n.get(Strings.COMBO_HOLD_ORDER_FIRST, "先按住第一颗")
    } else {
        I18n.get(Strings.COMBO_HOLD_ORDER_SECOND, "先按住第二颗")
    }

/**
 * 这一颗键**正被哪些组合的按住手势当成「先按住的那一颗」**。
 *
 *  关掉它的「松手时触发」会让这些手势不再成立：判定侧那条上界（`longPressPassed`）把
 *  「已经执行过自己动作」的键排除在按住手势之外 —— 而那正是「按住久一点再补一颗」最常见的用法。
 *
 *  只算**加过的那几种顺序**：没加过的顺序判定侧本来就不认，谈不上失效。
 */
internal fun Config.holdGesturesHeldBy(keyId: KeyId): List<Pair<ComboKey, Int>> =
    combos.flatMap { combo ->
        combo.holdOrders.mapNotNull { order ->
            if (combo.keys.getOrNull(order - 1) == keyId.id) combo to order else null
        }
    }

/**
 * 「关掉松手时触发」之前的那一问 —— 这颗键是某些按住手势的起点时弹（只弹一次、一次说清全部）。
 *
 *  与 [HoldGestureAsk] 同一套写法（页面级 object + 一页挂一次窗）：确认要跨「点开关 → 回答」
 *  两个组合过程。
 */
internal object FireReleaseOffAsk {
    /** 待确认的那颗键（`null` = 没有待确认的事）。 */
    var key by mutableStateOf<KeyId?>(null)
        private set

    /** 受影响的组合（去重；正文里逐个列名字）。 */
    var combos by mutableStateOf<List<ComboKey>>(emptyList())
        private set

    val asking: Boolean get() = key != null

    fun ask(
        key: KeyId,
        combos: List<ComboKey>,
    ) {
        this.key = key
        this.combos = combos
    }

    fun clear() {
        key = null
        combos = emptyList()
    }
}

/**
 * 拨「松手时触发」的**唯一入口**（单键那一页把它接在开关上）。
 *
 *  关掉时先看这颗键是不是某些按住手势的起点：不是就直接写；是就先问一句
 *  （[FireReleaseOffAsk]），确认后**同一次落盘**关掉这一位、并把这几种顺序一并移除 ——
 *  留着它们只会在「按法」窗里给出一个永远不成立的选项。
 */
internal fun SettingsPage.setFireOnRelease(
    key: KeyId,
    on: Boolean,
) {
    if (on) {
        writeFireOnRelease(key, on = true, dropHoldOrders = false)
        return
    }
    val affected =
        configState.config.value
            .holdGesturesHeldBy(key)
            .map { it.first }
            .distinctBy { it.id }
    if (affected.isEmpty()) {
        writeFireOnRelease(key, on = false, dropHoldOrders = false)
        return
    }
    XLog.w("松手时触发：${key.label} 是 ${affected.size} 种按住手势的起点，先问一次")
    FireReleaseOffAsk.ask(key, affected)
}

/**
 * 落盘那一位（必要时连按住顺序一起收）。
 *
 *  @param dropHoldOrders 关掉「松手时触发」时把**这颗键当起点**的那几种顺序一起移除
 */
private fun SettingsPage.writeFireOnRelease(
    key: KeyId,
    on: Boolean,
    dropHoldOrders: Boolean,
) {
    val cfg = configState.config.value
    val combos =
        if (!dropHoldOrders) {
            cfg.combos
        } else {
            cfg.combos.map { combo ->
                val keep = combo.holdOrders.filterNot { order -> combo.keys.getOrNull(order - 1) == key.id }
                if (keep == combo.holdOrders) combo else combo.copy(holdOrders = keep)
            }
        }
    val next =
        cfg.copy(
            keys = cfg.keys + (key to cfg.key(key).copy(longFireOnRelease = on)),
            combos = combos,
        )
    val what =
        if (dropHoldOrders) {
            "long_fire_on_release=0 drop_hold_orders=${key.id}"
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
        //  用户要判断的正是「这几组是不是我在用的那几组」。
        summary =
            I18n.format(
                Strings.BINDING_FIRE_OFF_BODY,
                target.label,
                combos.joinToString("\n") { comboLabel(cfg, it) },
            ),
        action = I18n.get(Strings.BINDING_FIRE_OFF_ACTION, "关闭并移除"),
        positiveDanger = true,
        onDismiss = { FireReleaseOffAsk.clear() },
        onConfirm = {
            FireReleaseOffAsk.clear()
            writeFireOnRelease(target, on = false, dropHoldOrders = true)
        },
    )
}

/**
 * **修补当前按法** —— 把这一种按住顺序里「先按住的那一颗」重新改回「松手时触发」。
 *
 *  什么时候会需要它：那颗键的「松手时触发」后来被关掉了（可能是为了连续触发、也可能是在
 *  别的页面顺手关的）。判定侧那条上界于是把这档按住手势整条截止 —— 界面上看不出任何异常，
 *  按下去就是「什么都没发生」。组合这一侧的菜单留一个入口，比让用户自己去找那颗键、
 *  猜是哪一位要快得多。
 *
 *  @param order [ComboKey.HOLD_FIRST] / [ComboKey.HOLD_SECOND]
 */
internal fun SettingsPage.repairPressMode(
    combo: ComboKey,
    order: Int,
) {
    val cfg = configState.config.value
    val key = cfg.keyIdOf(combo.keys.getOrNull(order - 1).orEmpty()) ?: return
    if (cfg.key(key).longFireOnRelease) return
    val next = cfg.copy(keys = cfg.keys + (key to cfg.key(key).copy(longFireOnRelease = true)))
    writeAndNotice(next, "combo:${combo.id}:repair_press_mode=$order", slot = HINT_SLOT_COMBO) {
        I18n.get(Strings.COMBO_REPAIR_DONE, "已修补当前按法")
    }
}

/**
 * 当前按法**是不是需要修补** —— 菜单那一行据此决定能不能点。
 *
 *  「需要」= 当前选的是按住那两种顺序之一、那一种确实加过、且先按住的那颗键已经不是「松手时触发」。
 */
internal fun Config.needsPressModeRepair(
    combo: ComboKey,
    hold: Int,
): Boolean {
    if (hold != ComboKey.HOLD_FIRST && hold != ComboKey.HOLD_SECOND) return false
    if (!combo.hasHoldOrder(hold)) return false
    val key = keyIdOf(combo.keys.getOrNull(hold - 1).orEmpty()) ?: return false
    return !key(key).longFireOnRelease
}

/** 组合动作页有几档手势（短按 / 长按）—— 与 `ComboEditState.holds` 同长。 */
private const val COMBO_HOLD_PAGES = 2
