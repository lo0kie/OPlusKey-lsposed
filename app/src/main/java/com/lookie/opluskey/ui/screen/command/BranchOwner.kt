package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.present.comboKeyNames
import com.lookie.opluskey.core.present.comboLabelOf
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.screen.editingKeyName
import com.lookie.opluskey.ui.state.binding.BranchDraft
import com.lookie.opluskey.ui.state.binding.SceneBinding
import com.lookie.opluskey.ui.write.branchKeyNow

// 「系统场景」页**在编谁** —— 哪颗键 / 哪一组组合键、当前用的是哪一份草稿。
//
//  从 `BranchScreen.kt` 拆出来：那边是页面骨架与保存，这里只有「这一页说的是哪一份东西」。
//  五处读者分散在顶栏、FAB、退出判据与页内注脚上，收在一处才不会各算一遍。

/**
 * 这一页「在编谁」的标识 —— 某颗键、或某档组合键（两边场景表形状一样，
 *  只有「读哪张表 / 写回哪张表」不同，判据是 `BranchDraftState.comboId`）。
 *
 *  **翻转之后没有手势这一半**：一张表里每块自带三档，换手势不再换草稿。
 */
internal fun SettingsPage.branchOwnerOf(): String {
    val comboId = branchDraft.comboId
    return if (comboId != null) BranchDraft.comboOwnerOf(comboId) else BranchDraft.ownerOf(branchKeyNow())
}

/**
 * 这一页那一份草稿 —— FAB / 退出判据 / 保存 / 顶栏「添加」都看它。
 *
 *  页内那几行读的也是同一份（这一页只有一页，见 `BranchScreen`）。
 */
internal fun SettingsPage.branchDraftNow(): BranchDraft = branchDraft.now()

/**
 * 「这一趟在编哪一颗键 / 哪一组组合键」—— 顶栏那行小标题与表尾注脚共用这一句。
 *
 *  **成员名字由 [branchOwnerNames] 给**，这里只负责压成一串（单键不套框、多颗各套一对 `[ ]`）：
 *  顶栏副标题要的是**逐个上样式的名单**（每颗画一枚圆角框），拼好的句子只能给纯文字的地方用。
 *
 *  **单键不套**（用户口径：只有一个键名时不要方框）：这一行本来就只装一个名字，
 *  套上 `[ ]` 只是多一层噪声 —— 而 `快捷键` 三个字旁边还有副标题的位置说明它是什么。
 */
internal fun SettingsPage.branchOwnerLabel(): String =
    comboLabelOf(branchOwnerNames(configState.config.value, branchDraft.comboId, editingKeyName()))

/**
 * 同一个「在编谁」的**成员键名**（一档组合有几颗就是几个名字）—— 顶栏副标题按名单画框。
 *
 *  「单键叫什么、组合的成员是谁」只有这一份实现：[branchOwnerLabel] 由它压成一串，
 *  两个读者（顶栏、表尾注脚）不会长出不同叫法。
 *
 *  三样都由调用点给（而不是收成 `SettingsPage` 的扩展）：这一支是纯取名、不碰页面状态，
 *  于是「页面扩展成员」那本账不会因为多一个读者就涨一格。
 */
internal fun branchOwnerNames(
    cfg: Config,
    comboId: String?,
    keyLabel: String,
): List<String> =
    if (comboId != null) {
        cfg.combos.firstOrNull { it.id == comboId }?.let { comboKeyNames(cfg, it) } ?: listOf(comboId)
    } else {
        listOf(keyLabel)
    }

/**
 * 点场景块里**某一档的动作行** → 进「场景动作」页编那一档（用户口径：不再是弹抽屉）。
 *
 *  「编第几块」与「停在哪一档」都记在页面级（[SettingsPage.sceneBinding]（`at` / `gesture` / `baseline` 三样一个值）
）：栈里这一页只有一个位置，参数走 `Screen` 枚举带不进去
 *  （与「组合键」编辑页的 `ComboEditState.editingId` 同一个做法）。
 *
 *  **顺手记下这一块进来时长什么样**（`SceneBinding.baseline`）：那一页的「改没改」
 *  与「放弃要不要回滚」都按它判 —— 整份草稿的 `dirty` 会把上一页的改动算到这一页头上。
 *
 *  上一页（这一页）**留在栈里**：草稿是页面级的，退回来时改动还在，右下角那颗「确认」随之出现。
 */
internal fun SettingsPage.openSceneBinding(
    at: Int,
    trigger: GestureTrigger,
) {
    sceneBinding = SceneBinding(at, trigger, branchDraftNow().scenes.getOrNull(at))
    XLog.w("进「场景动作」页：第 ${at + 1} 块 · ${trigger.id}")
    navTo(Screen.SCENE_BINDING)
}

/**
 * 一个场景块要编**哪几档** —— 组合键只有短按 / 长按（`ComboKey` 上没有双击、也没有多击）；
 *  一颗键则是 `slotsOn`：那几档自己要了才有（`KeyConfig.extraClicksOn`），没要的就不列。
 *
 *  判据与 `blockGestures()` 同一条：两处各写一遍迟早出现「带子上有五档、翻到第五页是空的」。
 *
 *  **这一位是「一块有几档」的唯一来源**：场景页的动作行（`RuleActionRows`）与「场景动作」页的
 *  手势带都按它排。清单本身在 core（`GestureTrigger.slotsOn` / `COMBO_SLOTS`），
 *  于是「再加一档」只改那一个列表 —— 界面这一层不再有第二份档位清单。
 */
internal fun SettingsPage.sceneGesturePages(): List<GestureTrigger> {
    val comboId = branchDraft.comboId
    if (comboId != null) return GestureTrigger.COMBO_SLOTS
    //  键那一支读**在编那颗键**的开关：与「按键绑定」页同一个判据，两页的档数才不会一页三一页六
    val key = configState.config.value.key(branchKeyNow())
    return GestureTrigger.slotsOn(key.extraClicksOn, key.clickThenLongOn, key.doubleClickOn)
}

/**
 * 进来时该停在第几页 —— `SceneBinding.gesture` 按当前那张表换算成页号。
 *
 *  存的是手势而不是页号：同一份草稿可能先是某颗键的（三档）、再是某组组合的（两档），
 *  页号的含义会变，手势不会。
 */
internal fun SettingsPage.sceneGesturePage(): Int = sceneGesturePages().indexOf(sceneBinding.gesture).coerceAtLeast(0)

/**
 * 第 [at] 块自己**开没开** —— 停用的场景块命中也不会执行（判定侧只认启用的块），界面上整片灰。
 *
 *  找不到那块时回 `true`：那一帧这一页正在往下退（见 `BindingPagerScreen` 的「不在了」那一支），
 *  再压暗一次只会看到闪一下。
 */
internal fun SettingsPage.sceneBlockEnabled(at: Int): Boolean = branchDraftNow().scenes.getOrNull(at)?.enabled != false
