package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.lookie.opluskey.ui.state.KeyWatchState

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

    CardList(
        rows =
            buildList {
                add(
                    CardRow.Item(
                        title = I18n.get(Strings.BINDING_SECTION_KEYS, "快捷键"),
                        summary = stateSummary(keyEnabled(KeyId.PLUS)),
                        //  停用只把行首换成禁用图标，**不压色**：图标一灰，整行看起来像点不了，
                        //  而这一行恰恰还能点进去（停用也照样能进那一页开回来）。
                        icon = keyIcon(PageIcons.MenuSideKey, keyEnabled(KeyId.PLUS)),
                        enabled = pageEnabled,
                        arrow = true,
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
                            arrow = true,
                            onClick = { openKey(key) },
                        ),
                    )
                }
                add(addKeyRow(cfg, pageEnabled, onAddKey))
            },
    )
}

/**
 * 「添加按键」那一行 —— 按键列表的末行。
 *
 *  副标题两档说的都是「这一行现在是什么状态」：满员 ⇒ 为什么加不了；
 *  没满 ⇒ 点下去是开一次**按键收集**（标题四个字看不出这一步在模块外面）。
 */
private fun SettingsPage.addKeyRow(
    cfg: Config,
    pageEnabled: Boolean,
    onAddKey: () -> Unit,
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
