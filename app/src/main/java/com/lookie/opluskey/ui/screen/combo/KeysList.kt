package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.keyEnabled
import com.lookie.opluskey.ui.state.ComboEditState
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.state.KeysEditState
import com.lookie.opluskey.ui.writeAndNotice
import io.github.suqi8.coui.kmp.basic.Checkbox

// 「按键功能」页上半张卡 —— 按键列表（侧键 + 收集来的键）与末行「添加按键」。
//
//  从 `KeysScreen.kt` 拆出来：那边是这一页的骨架（两张清单 + 筛选 + 两个弹窗），
//  这一块只回答「有哪几颗键、怎么再加一颗」。

/**
 * 「按键列表」那一块 —— 一行一颗键（侧键 + 收集来的键）。
 *
 * ## 点一行做什么
 *
 *  选中这颗键（）再进「按键绑定」页 —— 绑定的动作属于那一页，
 *  这一页只回答「有哪几颗键、点进去改哪一颗」。进页会把手势 tab 拨回第一档。
 *
 * ## 停用的键怎么画
 *
 *  行首图标换成禁用 + 副标题写「已启用 / 已停用」：停用只靠颜色区分对色弱不友好，
 *  而这一页的灰还可能是整页闸门关着，两件事得能分开看。停用的键照样能点进去（那一页能开回来）。
 *
 * ## 编辑态（见 [KeysEditState]）
 *
 *  顶栏那颗「编辑」按下去之后，**收集来的键**每行末尾换成多选框、点一行是勾选而不是进页；
 *  侧键那行置灰并说明它删不掉（内置键没有删除这回事），「添加按键」那一行整行退场
 *  （编辑态在改「已有哪几颗」，不是加一颗）。勾了任意一颗，页面那一层就从右下角滑进「删除」。
 *
 * ## 末行「添加按键」
 *
 *  收集一颗新键的入口在这里（用户口径：从首页那张「按键操作」卡搬进来）——
 *  它做的就是「往这张清单里加一行」，与清单同一张卡才读得通；摆在首页那张卡上时，
 *  它与上面那行「按键功能」是两件不相干的事。
 *
 * @param pageEnabled 整页总闸（模块开着 + 链路齐了 + 配置可写）
 * @param onAddKey 点末行「添加按键」—— 收集窗挂在页面那一层（[KeysScreen]）
 */
@Composable
internal fun SettingsPage.KeysListCard(
    pageEnabled: Boolean,
    onAddKey: () -> Unit,
) {
    val cfg = configState.config.value
    val editing = KeysEditState.editing
    val picked = KeysEditState.pickedKeys

    CardList(
        rows =
            buildList {
                //  内置侧键：编辑态下**置灰**而不是消失 —— 它在这张清单里确实存在、也确实删不掉，
                //  点一下由 `disabledHint` 说清是为什么（凭空少一行更像出了 bug）。
                add(
                    CardRow.Item(
                        title = I18n.get(Strings.BINDING_SECTION_KEYS, "快捷键"),
                        summary =
                            if (editing) {
                                I18n.get(Strings.KEYS_BUILTIN_KEEP, "内置侧键不能删除")
                            } else {
                                stateSummary(keyEnabled(KeyId.PLUS))
                            },
                        //  停用只把行首换成禁用图标，**不压色**：图标一灰，整行看起来像点不了，
                        //  而这一行恰恰还能点进去（停用也照样能进那一页开回来）。
                        icon = keyIcon(PageIcons.MenuSideKey, keyEnabled(KeyId.PLUS)),
                        enabled = pageEnabled && !editing,
                        disabledHint = I18n.get(Strings.KEYS_BUILTIN_KEEP, "内置侧键不能删除"),
                        arrow = !editing,
                        onClick = { openKey(KeyId.PLUS) },
                    ),
                )
                cfg.customKeys.forEachIndexed { i, key ->
                    val on = cfg.key(key).enabled
                    add(
                        CardRow.Item(
                            title = key.label,
                            summary = stateSummary(on),
                            // 第几颗就画哪个数字（`number-1/2/3`，与清单顺序一致）；停用换禁用图标
                            icon = keyIcon(PageIcons.menuKeyNumber(i), on),
                            enabled = pageEnabled,
                            //  编辑态：行尾那一格换成多选框，点一行是**勾选**（不消费点击的就是勾选框，
                            //  所以勾选框自己不挂点击 —— 整行都是它的点击区，双击会派发两次）。
                            trailing =
                                if (editing) {
                                    {
                                        Checkbox(
                                            state = if (key.id in picked) ToggleableState.On else ToggleableState.Off,
                                            onClick = null,
                                            enabled = pageEnabled,
                                        )
                                    }
                                } else {
                                    null
                                },
                            arrow = !editing,
                            onClick = {
                                if (editing) KeysEditState.toggleKey(key.id) else openKey(key)
                            },
                        ),
                    )
                }
                add(addKeyRow(cfg, pageEnabled, onAddKey, visible = !editing))
            },
    )
}

/**
 * 「添加按键」那一行 —— 按键列表的末行。
 *
 *  副标题两档说的都是「这一行现在是什么状态」：满员 ⇒ 为什么加不了；
 *  没满 ⇒ 点下去是开一次**按键收集**（标题四个字看不出这一步在模块外面）。
 *
 * @param visible 编辑态下整行退场（连它上面那条细线一起，见 `CardRow.Item.visible`）
 */
private fun SettingsPage.addKeyRow(
    cfg: Config,
    pageEnabled: Boolean,
    onAddKey: () -> Unit,
    visible: Boolean,
): CardRow.Item {
    val atLimit = cfg.customKeys.size >= KeyId.MAX_CUSTOM_KEYS
    return CardRow.Item(
        title = I18n.get(Strings.BINDING_ADD_KEY, "添加按键"),
        icon = PageIcons.MenuKeyAdd,
        summary =
            if (atLimit) {
                I18n.format(Strings.KEY_WATCH_LIMIT, KeyId.MAX_CUSTOM_KEYS)
            } else {
                I18n.get(Strings.BINDING_ADD_KEY_DESC, "收集自定义按键")
            },
        enabled = pageEnabled && !atLimit,
        //  这一行自己的原因只有「满员」那一档；整页闸门关着时留给整页那条
        disabledHint = if (atLimit) I18n.format(Strings.KEY_WATCH_LIMIT, KeyId.MAX_CUSTOM_KEYS) else null,
        visible = visible,
        onClick = onAddKey,
    )
}

/** 这一行的行首图标 —— 停用的键换成**禁用**（`pointer-cancel`），其余保持原样。 */
private fun keyIcon(
    normal: ImageVector,
    enabled: Boolean,
): ImageVector = if (enabled) normal else PageIcons.Cancel

/** 「已启用」/「已停用」—— 两档都给一句，行高才一致。 */
private fun stateSummary(enabled: Boolean): String =
    if (enabled) {
        I18n.get(Strings.BINDING_KEY_ENABLED_STATE, "已启用")
    } else {
        I18n.get(Strings.BINDING_KEY_DISABLED_STATE, "已停用")
    }

/**
 * 选中 [key] 再进「按键绑定」页。
 *
 *  点的就是当前那颗也照进 —— 这里不是「切换」动作，用户点了一颗键，期望的是进那颗键的页面。
 */
private fun SettingsPage.openKey(key: KeyId) {
    // 判据用 `editing()`，**不能拿 `selectedId` 直接比 `key.id`**：`selectedId == null` 本来
    //  就表示侧键，直接比会在「首次点侧键」时把 `null != "plus"` 当成切换，弹一句假的「已切换」。
    if (KeyWatchState.editing(configState.config.value) != key) {
        // 侧键的规范写法是 `null`，别把 "plus" 写进去（两处口径不一致，读回来又要归一一次）
        KeyWatchState.select(if (key == KeyId.PLUS) null else key.id)
        // **不发回执**：点一行就是「进那颗键的页面」，不是菜单里那种「切换」
        XLog.w("按键页：切到「${key.label}」")
    }
    navTo(Screen.BINDING)
}

/**
 * 删掉编辑态里勾中的那些项（按键 + 组合按键）—— 右下角那颗「删除」→ 确认窗之后调。
 *
 *  **一次落盘**：勾了几项就写一遍，回执报总数（「已删除 3 项」）。分两次写会播两条通知、
 *  「谁删了谁」还会互相盖掉。
 *
 *  两类的连带影响不一样，各自写清：
 *   · **按键** —— 内置侧键不在其中（编辑态里它勾不到）。引用了这些键的组合**保留**、变成
 *     「成员缺失」那一档：组合的删除入口在组合列表里，顺手级联删掉别的配置，用户在别处就再也
 *     看不到「我配过这么一组」这件事了。正在编辑的那颗键被删掉时，选中态收回侧键
 *     （[KeyWatchState.forget]）—— 不收回的话绑定页会指着一颗不存在的键去读写。
 *   · **组合** —— 整组连它的动作一起没；正在编辑的是它时，把 `ComboEditState` 的编辑目标清掉
 *     （否则「组合键」编辑页还停在一个已经删掉的 id 上）。
 */
internal fun SettingsPage.removePicked() {
    val cfg = configState.config.value
    val keyIds = KeysEditState.pickedKeys
    val comboIds = KeysEditState.pickedCombos
    val count = keyIds.size + comboIds.size
    if (count == 0) return
    val victims = cfg.customKeys.filter { it.id in keyIds }
    victims.forEach { KeyWatchState.forget(it.id) }
    if (ComboEditState.editingId in comboIds) ComboEditState.clear()
    XLog.w(
        "按键页：删除 $count 项" +
            "（按键 ${victims.joinToString { it.label }}；组合 ${comboIds.joinToString()}）",
    )
    val next =
        cfg.copy(
            customKeys = cfg.customKeys.filterNot { it.id in keyIds },
            //  这一颗键自己的设置（停用 / 单击延迟…）跟着一起走，别在配置里留一串没人认领的条目
            keys = cfg.keys.filterKeys { !it.isCustom || it.id !in keyIds },
            combos = cfg.combos.filterNot { it.id in comboIds },
        )
    writeAndNotice(next, "keys_del=$count") {
        I18n.format(Strings.KEYS_DELETED, count)
    }
}
