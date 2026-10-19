package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
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
 *  5. 「重命名」「停用 / 启用这颗键」「重置绑定」「删除按键」—— 都是对**当前选中的那一颗**
 *     动手，行之间一律细线，顺序按**破坏性**从小到大（改名不动数据 → 停用只是不接管 →
 *     重置清掉绑定 → 删除最狠）。
 *
 * ## 哪几行什么时候出现 / 是灰的
 *
 *  · **重命名 / 删除**：只在选中一颗自定义按键时可点 —— 侧键的名字与去留是模块自己说了算的；
 *  · **停用 / 启用**：每一颗键都有，**侧键也有**（「快捷键也允许被禁用」）—— 它写的是
 *    **这一颗键**的 `enabled`，与首页那个模块总闸是两件事：停用侧键只停这颗键，
 *    模块与其它键照常（总闸那一档才要过免责声明，也只在首页）；
 *  · **重置绑定**：每一颗键都能点 —— `Config.keys` 里自定义键也有自己的配置段。
 *
 * ## 为什么「快捷键」那行点了只切组
 *
 *  它是「你现在看到的这一组」，不是一个动作 —— 点它就是切回侧键那一组（清掉选中）。
 *  真要禁用它（`enabled = false`）反而更糟：库里禁用行连点击都不挂，菜单会一直开着
 *  （见 [MenuPopup] 里那段「禁用行吃事件」的说明），看着像卡住了。
 *
 * ## 键名后面那枚禁用标记
 *
 *  这一组行是**切换**动作，键停用了它照样可点 —— 于是名字后面得有个记号，否则
 *  「停在了一颗停用的键上」只能从整页变灰反推出来。
 *  它走库的 `hint` 槽（`MenuEntry.Row.stateIcon`），**不是**行尾那一格 ——
 *  所以这几行仍然都不带行尾图标（用户口径）：顶栏菜单里就这么几行，每一行是什么一目了然，
 *  再加一枚箭头反而把「一行操作」读成「进下一层」。`submenu` / `trailingIcon` 两位都不给：
 *  库里 `submenu` 是「行尾那一格画不画」的唯一开关，只清 `trailingIcon` 会留下默认的右箭头。
 */
@Composable
internal fun SettingsPage.BindingKeyMenu() {
    var open by remember { mutableStateOf(false) }
    // 菜单只负责「点了哪一项」，具体哪一个弹窗开着由这一位记 —— 一个变量就够：
    //  同一时刻只可能有一个弹窗，而且点任何一项都会先把菜单收掉。
    var dialog by remember { mutableStateOf<KeyMenuDialog?>(null) }
    val preciseClick = rememberPreciseClickState()
    // 当前在编辑哪一颗 —— 打勾、下半那几行「对谁动手」、以及「停用还是启用」都读这一份。
    val editing = KeyWatchState.editing(configState.config.value)

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
                    selected = editing,
                    // 「停用 / 启用」那一行写哪个字、键名后面挂不挂「已停用」，都读这一份配置
                    cfg = configState.config.value,
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
                            // 一步到位、不弹确认：停用不丢任何数据，菜单里一字之差就能改回来
                            onSetEnabled = { on ->
                                open = false
                                setKeyEnabledFromMenu(editing, on)
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
    /** 停用 / 启用当前这一颗键（`true` = 启用）。 */
    val onSetEnabled: (Boolean) -> Unit,
    val onReset: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * 菜单内容 —— 与组合无关（纯数据 + 回调），单独一支只为让 [BindingKeyMenu] 那支短下来。
 *
 * @param selected 当前在编辑哪一颗（`KeyId.PLUS` = 侧键）
 * @param cfg 配置快照 —— 键清单（`customKeys`）、每颗键的启用状态、下半那几行写哪个字，都读它。
 *   传配置而不是传几个派生值：这几样必须同一份快照，分开传迟早出现「清单是新的、状态是旧的」。
 */
private fun keyMenuEntries(
    selected: KeyId,
    cfg: Config,
    actions: KeyMenuActions,
): List<MenuEntry> =
    buildList {
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.BINDING_SECTION_KEYS, "快捷键"),
                icon = PageIcons.MenuKeys,
                // 停在这组时行尾对勾 + 文字主题色
                selected = !selected.isCustom,
                // 侧键停用了就在名字后面挂那枚禁用图标（见 [disabledMark]）
                stateIcon = disabledMark(cfg, KeyId.PLUS),
                onPick = { actions.onSelect(null) },
            ),
        )
        // 侧键与**自定义按键**是两组 —— 加深分隔，不是相邻行的细线
        // （口径见 [MenuEntry.GroupLine]）。**没有键时不补**：那会把「快捷键」和紧跟其后的
        //  「添加按键」切成两组，而这两行本来就同属「键清单」那一组。
        if (cfg.customKeys.isNotEmpty()) {
            add(MenuEntry.GroupLine)
            cfg.customKeys.forEachIndexed { index, key ->
                // 两颗键之间那道线**显式画**（[MenuEntry.RowLine]）：库对相邻两行也会自动画一条，
                //  但自动那条左端从 `PopupItemDividerMarginStartWithIcon` 起 —— 行首有图标时它
                //  **让开图标那一格**、只从文字下面开始，与别处那道穿图标区的细线不是同一支笔。
                //  用户口径：自定义按键之间也要通用细线。显式放一条之后，那段的自动线不再画。
                if (index > 0) add(MenuEntry.RowLine)
                add(
                    MenuEntry.Row(
                        title = key.label,
                        // 第几颗就画哪个数字（`number-1/2/3`，与清单顺序一致）
                        icon = PageIcons.menuKeyNumber(index),
                        selected = selected.id == key.id,
                        stateIcon = disabledMark(cfg, key),
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
                // 收满 [KeyId.MAX_CUSTOM_KEYS] 颗就置灰；点一下说清「到上限」——
                //  这一行自己的限制，不是「整页为什么改不了」，所以逐行填原因。
                enabled = cfg.customKeys.size < KeyId.MAX_CUSTOM_KEYS,
                disabledHint = I18n.format(Strings.KEY_WATCH_LIMIT, KeyId.MAX_CUSTOM_KEYS),
                onPick = actions.onCollect,
            ),
        )
        // 键清单（含「添加按键」）与下面的「重置 / 重命名 / 删」是两组
        add(MenuEntry.GroupLine)
        addAll(keyActionEntries(selected, cfg, actions))
    }

/**
 * 键名后面那枚**禁用标记** —— 这颗键此刻不接管时才有值，否则 `null`（不画）。
 *
 *  图形是 `cancel`（见 [PageIcons.Cancel]）：不用文字，是因为库那一槽只给 40dp
 *  —— 14sp 的三个汉字 42dp、11sp 虽然放得下但又小得不像话；也更不必为它多一条翻译。
 */
private fun disabledMark(
    cfg: Config,
    key: KeyId,
): ImageVector? = if (cfg.key(key).enabled) null else PageIcons.Cancel

/**
 * 菜单下半那**三行动作** —— 重命名 / 停用启用 / 重置 / 删除，全部作用于*当前选中的那一颗*。
 *
 *  单独一支同样是为了让 [keyMenuEntries] 留在 `LongMethod` 之内；行之间一律**细线**，
 *  与上面那条加深带（键清单 vs 操作）是两回事。
 *
 *  顺序是「重命名 → 停用 → 重置 → 删除」，按**破坏性**从小到大排（用户口径）：改名字不动任何数据；
 *  停用只是不接管（菜单里一字之差就能改回来）；重置清掉这颗键的三个手势与参数、键本身还在；
 *  删除最狠，压在最下面 —— 越靠下越不容易误点。
 *
 *  「停用 / 启用」**每颗键都有**（用户口径：「快捷键也允许被禁用」）。它写的是**这一颗键**的
 *  `KeyConfig.enabled` —— 与首页那个模块总闸（`GlobalConfig.enabled`）是两层，别混：
 *  总闸关 ⇒ 一颗键都不接管；某颗键关 ⇒ 只那一颗不接管。
 */
private fun keyActionEntries(
    selected: KeyId,
    cfg: Config,
    actions: KeyMenuActions,
): List<MenuEntry> {
    val enabled = cfg.key(selected).enabled
    return buildList {
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.BINDING_RENAME_KEY, "重命名"),
                icon = PageIcons.CommandEdit,
                // 侧键的名字不归用户改
                enabled = selected.isCustom,
                disabledHint = I18n.get(Strings.BINDING_SIDE_KEY_NO_RENAME, "侧键不能改名"),
                onPick = actions.onRename,
            ),
        )
        add(MenuEntry.RowLine)
        add(
            MenuEntry.Row(
                title =
                    if (enabled) {
                        I18n.get(Strings.BINDING_DISABLE_KEY, "停用这颗键")
                    } else {
                        I18n.get(Strings.BINDING_ENABLE_KEY, "启用这颗键")
                    },
                icon = PageIcons.Power,
                onPick = { actions.onSetEnabled(!enabled) },
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
                disabledHint = I18n.get(Strings.BINDING_SIDE_KEY_NO_DELETE, "侧键不能删除"),
                danger = true,
                onPick = actions.onDelete,
            ),
        )
    }
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
                val deleted = write(now.withoutKey(selected.id), "custom_key_delete=${selected.id}")
                if (deleted != CommandsSave.Failed) {
                    KeyWatchState.forget(selected.id)
                    //  只在**完全成功**时报：广播没发出去时 [write] 已经报过那条，别顶掉它
                    if (deleted == CommandsSave.Ok) {
                        // 名字得在 forget 之后取，否则 `selected` 已经指向不存在的键
                        hint(I18n.get(Strings.TOAST_KEY_DELETED, "已删除「%s」").format(selected.label))
                    }
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
    // 只在完全成功时报（见上一条同一笔账）
    if (write(now.withKeyName(selected.id, name), "custom_key_rename=${selected.id}") != CommandsSave.Ok) return
    hint(I18n.get(Strings.TOAST_KEY_RENAMED, "已重命名为「%s」").format(name))
}
