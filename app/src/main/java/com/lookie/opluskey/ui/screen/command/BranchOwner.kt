package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.keyBrackets
import com.lookie.opluskey.ui.screen.combo.comboLabel
import com.lookie.opluskey.ui.screen.editingKeyName
import com.lookie.opluskey.ui.state.BranchDraft

// 「条件执行」页**在编谁** —— 哪颗键 / 哪一组组合键、有哪几档手势、当前那一页用的是哪一份草稿。
//
//  从 `BranchScreen.kt` 拆出来：那边是三页骨架与保存，这里只有「这一页说的是哪一份东西」。
//  五处读者分散在顶栏、FAB、退出判据与页内注脚上，收在一处才不会各算一遍。

/**
 * 这一页「在编谁」的标识 —— 某颗键的一档，或某档组合键的一档（两边规则表形状一样，
 *  只有「读哪张表 / 写回哪张表」不同，判据是 [BranchDraftState.comboId]）。
 */
internal fun SettingsPage.branchOwnerOf(gesture: Gesture): String {
    val comboId = branchDraft.comboId
    return if (comboId != null) {
        BranchDraft.comboOwnerOf(comboId, gesture)
    } else {
        BranchDraft.ownerOf(branchKeyNow(), gesture)
    }
}

/**
 * 这一页是哪几档 —— 单键三档（单击 / 双击 / 长按）；组合键只有两档（双击在组合上没有定义，
 *  见 `ComboKey` 的类注释），所以那一趟只摆短按 / 长按两页。
 *
 *  分页器的页数与顶栏带子的标签都读这一处，两边不会对不上。
 */
internal fun SettingsPage.branchGestures(): List<Gesture> =
    if (branchDraft.comboId != null) listOf(Gesture.SINGLE, Gesture.LONG) else Gesture.entries

/**
 * 当前停的那一页那份草稿 —— FAB / 退出判据 / 保存 / 顶栏「添加」都看它。
 *
 *  页内那几行读的是**自己那一页**那份（三页同时在场，见 [BranchScreen]）；这一处只服务
 *  「页外」的那几个入口：它们说的都是「现在这一页」。
 */
internal fun SettingsPage.branchDraftNow(): BranchDraft = branchDraft.at(branchGesture)

/**
 * 「这一趟在编哪一颗键 / 哪一组组合键」—— 顶栏那行小标题与表尾注脚共用这一句。
 *
 *  单键写 `「快捷键」`（[keyBrackets]，与组合键编辑页的 `「侧键」+「电量 +」` 同一个长相：
 *  `快捷键` 单看像一句话，套上括号才看得出那是键名）；组合键那一档由 `comboLabel` 给，
 *  它自己带括号，外层不要再套。
 *
 *  **只有键名、不带手势**：哪一档由顶栏下那条手势带说（见 `PageTopBar` 的 `PageBottomBand`），
 *  两边各说一半才不会跟大标题抢宽度。
 */
internal fun SettingsPage.branchOwnerLabel(): String {
    val comboId = branchDraft.comboId
    if (comboId != null) {
        val cfg = configState.config.value
        return cfg.combos.firstOrNull { it.id == comboId }?.let { comboLabel(cfg, it, bracketed = true) } ?: comboId
    }
    val (open, close) = keyBrackets()
    return "$open${editingKeyName()}$close"
}

/**
 * 结尾那一行默认项在界面上怎么写 —— `「快捷键」- 单击`（键名 - 手势）。
 *
 *  这一句现在只出现在**表尾那一行注脚 / 页里空态那句话**里（顶栏那行只写键名，
 *  见 [branchOwnerLabel]）。
 */
internal fun SettingsPage.branchDefaultLabel(): String = "${branchOwnerLabel()} - ${branchGestureNow().label}"
