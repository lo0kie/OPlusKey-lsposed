package com.lookie.opluskey.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.screen.binding.CollectKeyDialog
import com.lookie.opluskey.ui.screen.keyEnabled
import com.lookie.opluskey.ui.state.KeyWatchState

// 首页那张「按键」卡 —— 按键清单（侧键 + 收集来的键）与「添加按键」。
//
//  从「按键绑定」页顶栏那份 ⋮ 菜单搬过来（用户口径）：那一页只回答「这颗键现在绑了什么」，
//  「有哪几颗键」是总览，属于首页。菜单里那一份清单连同「添加按键」一起挪走之后，
//  绑定页的菜单只剩对**当前这一颗**动手的那三行（见 `BindingKeyMenu`）。

/**
 * 「按键」卡 —— 一行一颗键，末行是「添加按键」。
 *
 * ## 点一行做什么
 *
 *  选中这颗键（`KeyWatchState.select`）再进「按键绑定」页 —— 与原先菜单里那一行**同一件事**，
 *  只是换了个更该在的地方。绑定页进场会把手势 tab 拨回第一页（见 `PageContent` 的
 *  `PushResetsPagers`），所以进去看到的就是这颗键的单击那一档。
 *
 * ## 停用的键怎么画
 *
 *  两样一起说（用户口径）：
 *   · **行首图标换成禁用**（`pointer-cancel`）—— 一眼扫过去先看见「这颗不接管」；
 *   · 副标题两档都写：「已启用」/「已停用」—— 启用的行也有一句，列表才不会一行高一行矮，
 *     而且「停用」不再只靠颜色区分（灰对色弱不友好）。
 *
 *  停用的键照样能点进去（那一页顶栏也挂同一枚禁用标记，菜单里能开回来）。
 *
 * ## 闸门
 *
 *  与原先那一行「按键绑定」入口**同一档**：整页总闸（[pageEnabled]）。这一页所有会写配置的
 *  入口都吃它，少给一个就会出现「别的都灰着、这一颗还能点进去改」。
 *
 * @param pageEnabled 整页总闸（模块开着 + 链路齐了 + 配置可写，见 `HomeScreen`）
 */
@Composable
internal fun SettingsPage.HomeKeysCard(
    pageEnabled: Boolean,
    ui: HomeUiState,
) {
    val tick = rememberTick()
    val cfg = configState.config.value
    val atLimit = cfg.customKeys.size >= KeyId.MAX_CUSTOM_KEYS

    CardList(
        rows =
            buildList {
                add(
                    CardRow.Item(
                        title = I18n.get(Strings.BINDING_SECTION_KEYS, "快捷键"),
                        summary = stateSummary(keyEnabled(KeyId.PLUS)),
                        //  停用只把行首换成禁用图标，**不压色**：图标一灰，整行看起来像点不了，
                        //  而这一行恰恰还能点进去（停用也照样能进那一页开回来）。
                        icon = keyIcon(PageIcons.MenuKeys, keyEnabled(KeyId.PLUS)),
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
                            // 第几颗就画哪个数字（`number-1/2/3`，与清单顺序一致）；停用换禁用图标 ——
                            //  同样**不压色**（见上面那一行的说明）。
                            icon = keyIcon(PageIcons.menuKeyNumber(i), on),
                            enabled = pageEnabled,
                            arrow = true,
                            onClick = { openKey(key) },
                        ),
                    )
                }
                add(
                    CardRow.Item(
                        title = I18n.get(Strings.BINDING_ADD_KEY, "添加按键"),
                        icon = PageIcons.MenuKeyAdd,
                        // 到上限就灰着，并把原因写在副标题里 —— 禁用态被点一下只会说页面那句笼统的
                        //  「现在不可用」，而这里的原因是**这一行自己**的。
                        summary = if (atLimit) I18n.format(Strings.KEY_WATCH_LIMIT, KeyId.MAX_CUSTOM_KEYS) else null,
                        enabled = pageEnabled && !atLimit,
                        onClick = {
                            tick()
                            ui.collectKeyPending = true
                        },
                    ),
                )
            },
    )

    CollectKeyDialog(
        show = ui.collectKeyPending,
        onDismiss = { ui.collectKeyPending = false },
    )
}

/**
 * 这一行的行首图标 —— 停用的键换成**禁用**（`pointer-cancel`），其余保持原样。
 *
 *  停用只靠「灰一点」表达是不够的：这一页的灰还可能是整页闸门关着（那时所有行都灰），
 *  两件事得能分开看。
 */
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
 *  与原先菜单里那一行同一套：**点的就是当前那颗也照进** —— 这里不再是「切换」动作，
 *  用户在首页点了一颗键，期望的是进那颗键的页面，而不是「什么都没发生」。
 */
private fun SettingsPage.openKey(key: KeyId) {
    // 判据用 `editing()`，**不能拿 `selectedId` 直接比 `key.id`**：`selectedId == null` 本来
    //  就表示侧键（见 `KeyWatchState`），直接比会在「首次点侧键」时把 `null != "plus"` 当成切换，
    //  弹一句假的「已切换到「侧键」」—— 用户还没切过任何东西。
    if (KeyWatchState.editing(configState.config.value) != key) {
        // 侧键的规范写法是 `null`，别把 "plus" 写进去（两处口径不一致，读回来又要归一一次）
        KeyWatchState.select(if (key == KeyId.PLUS) null else key.id)
        // **不发回执**：这一页点一行就是「进那颗键的页面」，不是菜单里那种「切换」——
        //  弹一句「已切换到「侧键」」等于把一次跳转说成了一次状态变更。
        //  日志留着：用户问「为什么进的是这颗键」时，这一行是唯一的判据。
        XLog.w("首页按键清单：切到「${key.label}」")
    }
    navTo(Screen.BINDING)
}
