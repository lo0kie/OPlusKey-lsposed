package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.withKeyName
import com.lookie.opluskey.core.model.withoutKey
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.HeaderMenuButton
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.MenuPopup
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.STEP_PICKER_MAX_HEIGHT
import com.lookie.opluskey.ui.resetKeyBindings
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

/**
 * 「按键绑定」页顶栏右侧的菜单按钮 + 浮层菜单 —— **多按键的切换与收集入口**。
 *
 *  只服务**「按键绑定」页**那一条顶栏，所以跟着这一屏放（口径见 `ui/PageHeader`：
 *  顶栏只提供 `actions` 槽位，菜单由各页自己摆）。
 *
 * ## 菜单里有什么
 *
 *  1. 「快捷键」—— 侧键那一组；
 *  2. 收集到的自定义按键（有才有），一行一颗；
 *  3. 「添加按键」—— **键清单那一组**的最后一行（它往这张清单里加一颗）；
 *  4. 一条加深分隔（「键清单」与「对这颗键动手」是两件事）；
 *  5. 「重置绑定」「重命名」「删除按键」—— 都是对**当前选中的那一颗**动手，三行之间一律细线。
 *
 * ## 哪几行什么时候是灰的
 *
 *  · **重命名 / 删除**：只在选中一颗自定义按键时可点 —— 侧键的名字与去留是模块自己说了算的；
 *  · **重置绑定**：只在**侧键**那组可点 —— 现在只有侧键有配置段（`KeyId` 还是枚举），
 *    一颗收集来的键还没有绑定可重置。
 *
 * ## 为什么「快捷键」那行点了只切组
 *
 *  它是「你现在看到的这一组」，不是一个动作 —— 点它就是切回侧键那一组（清掉选中）。
 *  真要禁用它（`enabled = false`）反而更糟：库里禁用行连点击都不挂，菜单会一直开着
 *  （见 [MenuPopup] 里那段「禁用行吃事件」的说明），看着像卡住了。
 *
 * ## 行尾图标
 *
 *  这几行**都不带行尾图标**（用户口径）：顶栏菜单里就这么几行，每一行是什么一目了然，
 *  再加一枚箭头反而把「一行操作」读成「进下一层」。所以 `submenu` / `trailingIcon`
 *  两位都不给：库里 `submenu` 是「行尾那一格画不画」的唯一开关，只清 `trailingIcon`
 *  会留下默认的右箭头（见 [MenuEntry.Row] 的说明）。
 */
@Composable
internal fun SettingsPage.BindingKeyMenu() {
    var open by remember { mutableStateOf(false) }
    // 菜单只负责「点了哪一项」，具体哪一个弹窗开着由这一位记 —— 一个变量就够：
    //  同一时刻只可能有一个弹窗，而且点任何一项都会先把菜单收掉。
    var dialog by remember { mutableStateOf<KeyMenuDialog?>(null) }
    val preciseClick = rememberPreciseClickState()

    Box {
        HeaderMenuButton(preciseClick) { open = true }

        MenuPopup(
            show = open,
            preciseClick = preciseClick,
            onDismiss = { open = false },
            // 与「添加命令」「入口格菜单」**同一档**最大高度：超过就滚动。
            //  不传的话面板按内容长，键一多（自定义按键是用户随时能加的）就会顶到屏幕外、
            //  而且滚不动 —— 与系统开关那格（十个开关）是同一道菜单、同一条判据。
            maxHeight = STEP_PICKER_MAX_HEIGHT,
            entries =
                keyMenuEntries(
                    // 选中态是界面状态（`KeyWatchState`），清单是**配置**（`customKeys`）——
                    //  两者在这里凑齐交给菜单，菜单本身不认识任何一个来源。
                    selected = KeyWatchState.editing(configState.config.value),
                    keys = configState.config.value.customKeys,
                    actions =
                        KeyMenuActions(
                            onSelect = { target ->
                                open = false
                                // 点的就是当前那颗 ⇒ 什么都不做（不切换、也不报「已切换到「…」」）：
                                //  菜单里那颗本来就打着勾，再报一句等于说你刚做了一件没发生的事。
                                //  比的是 `selectedId`（`null` = 侧键）：那一位就是菜单打勾的依据，
                                //  与「点了哪一行」是同一套口径。
                                if (target == KeyWatchState.selectedId) {
                                    XLog.w("按键绑定：点的就是当前那颗，不切换")
                                } else {
                                    KeyWatchState.select(target)
                                    // 「切换按键」是一个动作，弹一句说明切到了哪颗（用户口径）。
                                    //  名字取**切换之后**那一颗 —— `editing` 会把「选中的那颗刚被删掉」
                                    //  兜成侧键，所以这里读出来的就是用户眼前正看着的那颗。
                                    val label = KeyWatchState.editing(configState.config.value).label
                                    XLog.w("按键绑定：切换到「$label」")
                                    hint(I18n.get(Strings.TOAST_KEY_SWITCHED, "已切换到「%s」").format(label))
                                }
                            },
                            onCollect = {
                                open = false
                                dialog = KeyMenuDialog.COLLECT
                            },
                            onRename = {
                                open = false
                                dialog = KeyMenuDialog.RENAME
                            },
                            onReset = {
                                open = false
                                dialog = KeyMenuDialog.RESET
                            },
                            onDelete = {
                                open = false
                                dialog = KeyMenuDialog.DELETE
                            },
                        ),
                ),
        )
    }

    KeyMenuDialogHost(dialog, onDismiss = { dialog = null })
}

/** 菜单底下那四个弹窗 —— 同一时刻至多一个。 */
private enum class KeyMenuDialog {
    COLLECT,
    RENAME,
    RESET,
    DELETE,
}

/**
 * 菜单各项的回调 —— 打成一个包只为让 `keyMenuEntries` 的参数个数留在 detekt 的
 * `LongParameterList` 之下（那五条本来就同属「菜单点了什么」）。
 */
private class KeyMenuActions(
    val onSelect: (String?) -> Unit,
    val onCollect: () -> Unit,
    val onRename: () -> Unit,
    val onReset: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * 菜单内容 —— 与组合无关（纯数据 + 回调），单独一支只为让 [BindingKeyMenu] 那支短下来。
 *
 * @param selected 当前在编辑哪一颗（`KeyId.PLUS` = 侧键）
 * @param keys 已经收集到的键（配置里的 `customKeys`，按收集顺序）
 */
private fun keyMenuEntries(
    selected: KeyId,
    keys: List<KeyId>,
    actions: KeyMenuActions,
): List<MenuEntry> =
    buildList {
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.BINDING_SECTION_KEYS, "快捷键"),
                icon = PageIcons.MenuKeys,
                // 停在这组时行尾对勾 + 文字主题色
                selected = !selected.isCustom,
                onPick = { actions.onSelect(null) },
            ),
        )
        // 侧键与**自定义按键**是两组 —— 加深分隔，不是相邻行的细线
        // （口径见 [MenuEntry.GroupLine]）。**没有键时不补**：那会把「快捷键」和紧跟其后的
        //  「添加按键」切成两组，而这两行本来就同属「键清单」那一组。
        if (keys.isNotEmpty()) {
            add(MenuEntry.GroupLine)
            keys.forEachIndexed { index, key ->
                add(
                    MenuEntry.Row(
                        title = key.label,
                        // 第几颗就画哪个数字（`number-1/2/3`，与清单顺序一致）
                        icon = PageIcons.menuKeyNumber(index),
                        selected = selected.id == key.id,
                        onPick = { actions.onSelect(key.id) },
                    ),
                )
            }
        }
        // 键清单与「添加按键」之间那道**显式**细线（[MenuEntry.RowLine]）：
        //  库对相邻两行会自动画一条，但它的左端从 `PopupItemDividerMarginStartWithIcon` 起 ——
        //  行首有图标时那条线**让开图标那一格**，只从文字下面开始。用户口径是要它像别处的
        //  细线一样穿过图标区（[PopupRowDivider] 的左端取 `InsideHorizontalPadding`）。
        //  显式放一条之后，那一段的自动细线也就不会再画（中间隔了非行元素）。
        add(MenuEntry.RowLine)
        // 「添加按键」是**键清单那一组**的最后一行（它往这张清单里加一颗），所以压在下面那条
        //  加深带之前 —— 放进「对这颗键动手」那三行里，读起来像是对选中项的操作。
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.BINDING_ADD_KEY, "添加按键"),
                icon = PageIcons.MenuKeyAdd,
                // 收满 [KeyId.MAX_CUSTOM_KEYS] 颗就置灰：点了也只会 toast 一句「到上限」，
                // 不如直接说「不能再加了」。禁用行会吃掉点击、菜单不关（见 [MenuEntry.Row]）。
                enabled = keys.size < KeyId.MAX_CUSTOM_KEYS,
                onPick = actions.onCollect,
            ),
        )
        // 键清单（含「添加按键」）与下面的「重置 / 重命名 / 删」是两组
        add(MenuEntry.GroupLine)
        addAll(keyActionEntries(selected, actions))
    }

/**
 * 菜单下半那**三行动作** —— 重命名 / 重置 / 删除，全部作用于*当前选中的那一颗*。
 *
 *  单独一支同样是为了让 [keyMenuEntries] 留在 `LongMethod` 之内；三行之间一律**细线**，
 *  与上面那条加深带（键清单 vs 操作）是两回事。
 *
 *  顺序是「重命名 → 重置 → 删除」，按**破坏性**从小到大排（用户口径）：改名字不动任何数据；
 *  重置清掉这颗键的三个手势与参数、键本身还在；删除最狠，压在最下面 —— 越靠下越不容易误点。
 */
private fun keyActionEntries(
    selected: KeyId,
    actions: KeyMenuActions,
): List<MenuEntry> =
    buildList {
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.BINDING_RENAME_KEY, "重命名"),
                icon = PageIcons.CommandEdit,
                // 侧键的名字不归用户改
                enabled = selected.isCustom,
                onPick = actions.onRename,
            ),
        )
        add(MenuEntry.RowLine)
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.BINDING_RESET_KEY, "重置此按键绑定"),
                icon = PageIcons.MenuReset,
                // 每一颗键都有自己的配置段（`Config.keys`），所以都能重置
                enabled = true,
                onPick = actions.onReset,
            ),
        )
        add(MenuEntry.RowLine)
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.BINDING_DELETE_KEY, "删除按键"),
                icon = PageIcons.CommandDelete,
                // 侧键不给删（它不是用户收集来的东西）
                enabled = selected.isCustom,
                danger = true,
                onPick = actions.onDelete,
            ),
        )
    }

/**
 * 四个弹窗的落点。
 *
 *  分开一支的原因有二：`BindingKeyMenu` 那支会顶到 detekt 的 `LongMethod`；以及这几个弹窗
 *  各有各的开合条件，混在菜单主体里读不出「点哪一项会弹什么」。
 */
@Composable
private fun SettingsPage.KeyMenuDialogHost(
    dialog: KeyMenuDialog?,
    onDismiss: () -> Unit,
) {
    // 弹窗里的文案取**当前选中**那一颗（弹窗开着时选中不会变，但取当前值比闭包更稳）
    val config = configState.config.value
    val selected = KeyWatchState.editing(config)

    CollectKeyDialog(
        show = dialog == KeyMenuDialog.COLLECT,
        onDismiss = onDismiss,
    )
    RenameKeyDialog(
        show = dialog == KeyMenuDialog.RENAME,
        initial = selected.label,
        onDismiss = onDismiss,
        onConfirm = { name ->
            // 改名写进配置（身份是 id，绑定不丢）；失败时 `write` 自己会提示，这里照样关窗。
            if (selected.isCustom) confirmRenameKey(selected, name)
            onDismiss()
        },
    )
    ConfirmDialog(
        show = dialog == KeyMenuDialog.RESET,
        title = I18n.get(Strings.BINDING_RESET_KEY, "重置此按键绑定"),
        summary = I18n.get(Strings.BINDING_RESET_KEY_CONFIRM, "单击、双击、长按与按键设置都会回到默认，按键本身保留。"),
        action = I18n.get(Strings.COMMON_RESET, "重置"),
        positiveDanger = true,
        onDismiss = onDismiss,
        onConfirm = {
            resetKeyBindings()
            onDismiss()
        },
    )
    ConfirmDialog(
        show = dialog == KeyMenuDialog.DELETE,
        title = I18n.get(Strings.BINDING_DELETE_KEY, "删除按键"),
        summary =
            I18n
                .get(
                    Strings.BINDING_DELETE_KEY_CONFIRM,
                    "「%s」会从按键清单里删掉。",
                ).format(selected.label),
        action = I18n.get(Strings.COMMON_DELETE, "删除"),
        onDismiss = onDismiss,
        onConfirm = {
            // 清单与绑定两处一起摘（见 `Config.withoutKey`）；选中态收回侧键。
            //  **写成了才收**：写盘失败时界面仍停在那一颗上，与文件里的真实配置一致
            //  （先 forget 会让页面切回侧键，而那颗键其实还在配置里）。
            if (selected.isCustom) {
                val now = configState.config.value
                if (write(now.withoutKey(selected.id), "custom_key_delete=${selected.id}") != CommandsSave.Failed) {
                    KeyWatchState.forget(selected.id)
                    // 删成功才提示：名字得在 forget 之后取，否则 `selected` 已经指向不存在的键
                    hint(I18n.get(Strings.TOAST_KEY_DELETED, "已删除「%s」").format(selected.label))
                }
            }
            onDismiss()
        },
    )
}

/**
 * 「重命名」弹窗点了确定 —— **名字没变就什么都不做**（不落盘、也不报「已重命名为「…」」）。
 *
 *  打开弹窗看一眼再点确定是最常见的误触；报一句反而让人以为真的改了什么。
 *  写盘取**此刻**的配置，不用组合期那份快照：弹窗开着这段时间别处可能已经写过配置，
 *  拿旧快照写回去会把那次改动一起抹掉。
 *
 * @param name 弹窗里那一份（已去掉首尾空白）；回执里取的是**它**，不是 `selected.label`
 *   —— 后者是改名之前的值
 */
private fun SettingsPage.confirmRenameKey(
    selected: KeyId,
    name: String,
) {
    if (name == selected.label) {
        XLog.w("按键绑定：名字没变（${selected.label}），不写盘")
        return
    }
    val now = configState.config.value
    if (write(now.withKeyName(selected.id, name), "custom_key_rename=${selected.id}") == CommandsSave.Failed) return
    hint(I18n.get(Strings.TOAST_KEY_RENAMED, "已重命名为「%s」").format(name))
}
