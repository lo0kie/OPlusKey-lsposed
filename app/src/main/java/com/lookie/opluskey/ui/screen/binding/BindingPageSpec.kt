package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.trigger
import com.lookie.opluskey.ui.BindingTarget
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.gestureLabel
import com.lookie.opluskey.ui.screen.command.branchDraftNow
import com.lookie.opluskey.ui.screen.command.sceneBlockEnabled
import com.lookie.opluskey.ui.screen.command.sceneGesturePages
import com.lookie.opluskey.ui.screen.keyEnabled
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.BandHeight
import com.lookie.opluskey.ui.state.ComboEditState
import com.lookie.opluskey.ui.state.KeyWatchState

// 「这一页**是什么**」的那几个纯函数 —— 从 `BindingPagerScreen` 拆出来。
//
//  它们只看页面状态、不碰布局：在编哪几档、这一页开不开、要不要压暗、手势带量到哪份高度、
//  那几档叫什么。页面骨架那边只负责把它们接进顶栏与网格。
//
//  拆出来的直接原因很实际：三页合一之后 `BindingPagerScreen.kt` 里的函数数顶到了 detekt 的
//  上限（一个文件 11 个）。按「判据 / 布局」切开之后，两边各自都还有余量。

/**
 * 这一页**每档一份**的滚动位置。
 *
 *  `ScrollState` 内部只有一个 `scrollableState`，几档共用一个会互相打架（滑一档另一档跟着动），
 *  所以按档数一档一份。
 *
 *  **寄在页面级**（[SettingsPage.bindingScrolls]）而不是就地 `remember`：被「选择应用」盖住时
 *  这一页的整棵组合会被销毁，就地 remember 的那份跟着没 —— 退回来列表直接回顶部
 *  （用户口径「点 FAB 返回绑定页后滚动距离会归零」）。
 *
 *  档数会变（组合键三档 / 场景两档 / 按键三档），上一次那一份尺寸对不上就重开一份 ——
 *  把旧的那份按新档数续用会张冠李戴（第 2 档用上第 3 档的偏移）。
 */
@Composable
internal fun SettingsPage.bindingScrollsFor(
    s: Screen,
    pages: Int,
): List<ScrollState> =
    remember(pages) {
        bindingScrolls[s]
            ?.takeIf { it.size == pages }
            ?: List(pages) { ScrollState(0) }.also { bindingScrolls[s] = it }
    }

/**
 * 这一页在编的**几档**，顺序即分页器页序；`null` = 在编的那一份已经不在了（该退页）。
 *
 *  「不在了」只有两处会真发生：组合键在别处被删、场景块被删 / 草稿被重置 ——
 *  一颗键永远回得出一份（`Config.key` 有默认值兜底），所以 `BINDING` 那一支不会给 `null`。
 */
internal fun SettingsPage.bindingTargetsOf(s: Screen): List<BindingTarget>? =
    when (s) {
        Screen.COMBO_EDIT -> {
            val combo = editingComboOrNull() ?: return null
            COMBO_GESTURES.map { BindingTarget.Combo(combo.id, it) }
        }

        Screen.SCENE_BINDING -> {
            val draft = branchDraftNow()
            val at = sceneBindingAt
            if (draft.scenes.getOrNull(at) == null) return null
            //  「这一档属于哪一组组合」随目标一起带过去：组合的场景表背后没有某一颗键，
            //  长按那几张卡要写回**那一组**的参数（见 `BindingTarget.Scene.comboId`）
            sceneGesturePages().map { BindingTarget.Scene(draft, at, it, branchDraft.comboId) }
        }

        else -> {
            val cfg = configState.config.value
            val id = KeyWatchState.editing(cfg)
            val key = cfg.key(id)
            //  三击 / 四击 / 单击后长按那几页**跟着这颗键自己那几颗开关走**
            //  （`KeyConfig.extraClicksOn` / `clickThenLongOn`）：没开的档根本不存在，
            //  不是「存在但灰着」—— 页都少一页，带子上也不列。
            GestureTrigger.slotsOn(key.extraClicksOn, key.clickThenLongOn).map { BindingTarget.Key(id, it) }
        }
    }

/** 这一页现在**能不能改** —— 整页那几道闸 × 在编的那一份自己开没开。 */
internal fun SettingsPage.bindingTargetEnabled(s: Screen): Boolean =
    when (s) {
        // 组合键**不叠加**「这颗键开没开」：它有自己的开关（那一组），所以吃的是整页闸门
        //  （主开关 + 链路 + 只读）+ 自己那一位。
        Screen.COMBO_EDIT -> pageWritable() && editingComboOrNull()?.let { it.enabled } != false

        // **场景整条路只看整页闸门**（主开关 + 链路 + 只读）：这一块 / 这一颗键自己停用了
        //  照样要能先把动作配好、再回去启用 —— 与「独立设置」页同一条口径。
        Screen.SCENE_BINDING -> pageWritable()

        // 一颗键那一页只吃它自己的 `enabled`：模块总闸另有一层（关着时整页走 `blockedReason`）。
        else -> keyEnabled(KeyWatchState.editing(configState.config.value))
    }

/**
 * 在编的那一份**自己被停用**了吗 —— 只服务顶栏那行小标题的压暗。
 *
 *  与 [bindingTargetEnabled] **不是一回事**：压暗只在「这一份被停用」时说，
 *  不掺整页闸门 —— 主开关关掉时内容已经整片在渐变，标题跟着一起压暗会读成「这一份坏了」。
 */
internal fun SettingsPage.bindingTargetOff(s: Screen): Boolean =
    when (s) {
        Screen.COMBO_EDIT -> editingComboOrNull()?.let { !it.enabled } == true
        Screen.SCENE_BINDING -> !sceneBlockEnabled(sceneBindingAt)
        else -> !keyEnabled(KeyWatchState.editing(configState.config.value))
    }

/**
 * 这一页那条手势带**量到哪一份高度** —— 三页各一份（`BandHeight`）。
 *
 *  不共用：转场时相邻两页同时在组合里，共用一个值只会互相改对方的占位。
 */
internal fun SettingsPage.bindingBandOf(s: Screen): BandHeight =
    when (s) {
        Screen.COMBO_EDIT -> comboBandHeight
        Screen.SCENE_BINDING -> sceneBandHeight
        else -> bindingBandHeight
    }

/**
 * 手势带上那几档的**文案** —— 组合键那两档叫「短按 / 长按」，其余叫「单击 / 双击 / 长按 / 三击 / 四击」。
 *
 *  这套叫法与「组合键」页其余文案一致（`ComboKey` 只有短按 / 长按），不能统一成一套：
 *  组合没有双击，把短按写成「单击」会让人去找那颗并不存在的单击。
 */
internal fun bindingBandLabels(targets: List<BindingTarget>): List<String> =
    if (targets.firstOrNull() is BindingTarget.Combo) {
        listOf(
            I18n.get(Strings.COMBO_SHORT, "短按"),
            I18n.get(Strings.COMBO_LONG, "长按"),
        )
    } else {
        targets.map { gestureLabel(it.gesture) }
    }

/** 组合键那一页只有两档（`ComboKey` 上没有双击）—— 顺序即分页器页序。 */
private val COMBO_GESTURES: List<GestureTrigger> = listOf(Gesture.SINGLE, Gesture.LONG).map { it.trigger() }

/** 在编的那一档组合（`null` = 没在编 / 刚被删）—— 三处判据都从这一支取。 */
private fun SettingsPage.editingComboOrNull() =
    ComboEditState.editingId?.let { id ->
        configState.config.value.combos
            .firstOrNull { it.id == id }
    }
