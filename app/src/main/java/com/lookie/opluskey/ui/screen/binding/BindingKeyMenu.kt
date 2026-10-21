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
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.withKeyName
import com.lookie.opluskey.core.model.withoutKey
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.HINT_SLOT_BINDING
import com.lookie.opluskey.ui.HeaderMenuButton
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.BRANCHES_TITLE
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

/**
 * 「按键绑定」页顶栏右侧的菜单按钮 + 浮层菜单 —— 对**当前这一颗键**动手的地方。
 *
 * ## 菜单里有什么
 *
 *  1. 「条件执行」—— 进那一页（`Screen.BRANCHES`）。它不是对这颗键的操作，所以压在上面、
 *     并用一条**加深分隔**（下面第一行的 `dividerBefore`）与那三行分开；
 *  2. 「重命名」「停用 / 启用这颗键」「删除按键」—— 都是对**当前选中的那一颗**动手，
 *     顺序按**破坏性**从小到大（改名不动数据 → 停用只是不接管 → 删除最狠，压在最下面）。
 *
 * ## 按键清单**不在这里**
 *
 *  切换 / 添加按键都搬到**首页**了（首页那张「按键」卡，见 `HomeKeysCard`）：那一页才是
 *  「有哪几颗键」的总览，而这一页只回答「这颗键现在绑了什么」。菜单里再放一份清单，
 *  等于把同一件事摆在两处，且每次切键都要在这个只有几行的浮层里找。
 *  随之**「重置绑定」也删掉了** —— 它清的是这颗键的三个手势与参数，而它旁边就是那三档绑定
 *  本身，删掉之后少一条「按下去什么都没了、还不知道该从哪改回来」的路。
 *
 * ## 哪几行是灰的
 *
 *  · **重命名 / 删除**：只在选中一颗自定义按键时可点 —— 侧键的名字与去留是模块自己说了算的；
 *  · **停用 / 启用**：每一颗键都有，**侧键也有**（「快捷键也允许被禁用」）—— 它写的是
 *    **这一颗键**的 `enabled`，与首页那个模块总闸是两件事：停用侧键只停这颗键，
 *    模块与其它键照常（总闸那一档才要过免责声明，也只在首页）。
 *
 * ## 键名后面那枚禁用标记
 *
 *  这一页的顶栏标题已经挂了一枚（见 `PageTopBar` 的 `trailingSubtitleIcon`），菜单里不再重复：
 *  菜单只剩四行，「这一颗键停用了」从页面整片变灰与标题旁那枚记号都看得出来。
 */
@Composable
internal fun SettingsPage.BindingKeyMenu() {
    var open by remember { mutableStateOf(false) }
    // 菜单只负责「点了哪一项」，具体哪一个弹窗开着由这一位记 —— 一个变量就够：
    //  同一时刻只可能有一个弹窗，而且点任何一项都会先把菜单收掉。
    var dialog by remember { mutableStateOf<KeyMenuDialog?>(null) }
    val preciseClick = rememberPreciseClickState()
    // 当前在编辑哪一颗 —— 下半那几行「对谁动手」、「停用还是启用」都读这一份。
    val editing = KeyWatchState.editing(configState.config.value)

    Box {
        HeaderMenuButton(preciseClick) { open = true }

        PageMenu(
            // 只有四行，不再需要最大高度（键清单搬走之后这一层不会长）。
            //  行尾一律不给箭头 / 选择器：库的 `submenu` 是「行尾那一格画不画」的唯一开关，
            //  连 `trailingIcon` 一起省掉，读起来才是「一行操作」而不是「进下一层」。
            //  「条件执行」这一行例外 —— 它确实是进下一页，所以那一行给行尾箭头（见下）。
            entries =
                keyMenuEntries(
                    selected = editing,
                    cfg = configState.config.value,
                    actions =
                        KeyMenuActions(
                            onOpenBranches = {
                                open = false
                                openBranchesPage()
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
                            onDelete = {
                                open = false
                                dialog = KeyMenuDialog.DELETE
                            },
                        ),
                ).takeIf { open }
                    .orEmpty(),
            preciseClick = preciseClick,
            onDismiss = { open = false },
        )
    }

    KeyMenuDialogHost(dialog, onDismiss = { dialog = null })
}

/**
 * 进「条件执行」页 —— 只切栈。
 *
 *  那一页有自己的草稿（`branchDraft`），进页时由它自己按「这颗键 + 这一档手势」`load`
 *  （见 `BranchScreen` 开头那段 effect），不经过命令页的 `commandDraft`。
 *  离栈时草稿由 `discardPageStateOf` 清，所以下次进来是重新载入的一份。
 */
private fun SettingsPage.openBranchesPage() {
    XLog.w("按键绑定：进「${BRANCHES_TITLE}」页")
    navTo(Screen.BRANCHES)
}

/** 菜单底下那两个弹窗 —— 同一时刻至多一个。 */
private enum class KeyMenuDialog {
    RENAME,
    DELETE,
}

/**
 * 菜单各项的回调 —— 打成一个包只为让 `keyMenuEntries` 的参数个数留在 detekt 的
 * `LongParameterList` 之下（那四条本来就同属「菜单点了什么」）。
 */
private class KeyMenuActions(
    val onOpenBranches: () -> Unit,
    val onRename: () -> Unit,
    /** 停用 / 启用当前这一颗键（`true` = 启用）。 */
    val onSetEnabled: (Boolean) -> Unit,
    val onDelete: () -> Unit,
)

/**
 * 「条件执行」入口**是否露在按键菜单里** —— 现在放开（`true`）。
 *
 *  它曾被收起过一版：页面、草稿、判定那一整套都还在，只是菜单里不给这一行（用户口径
 *  「把条件执行入口在菜单里隐藏」），同一版又要求「别的什么都不要改」，于是留了这个开关
 *  而不是删代码。要再收起来只改这一行 —— 不留注释掉的代码块，也不删 `onOpenBranches`
 *  那条回调链。
 */
private const val SHOW_BRANCHES_ENTRY = true

/**
 * 菜单内容 —— 与组合无关（纯数据 + 回调），单独一支只为让 [BindingKeyMenu] 那支短下来。
 *
 * @param selected 当前在编辑哪一颗（`KeyId.PLUS` = 侧键）
 * @param cfg 配置快照 —— 每颗键的启用状态、那几行灰不灰，都读它。
 *   传配置而不是传几个派生值：这几样必须同一份快照，分开传迟早出现「清单是新的、状态是旧的」。
 */
private fun keyMenuEntries(
    selected: KeyId,
    cfg: Config,
    actions: KeyMenuActions,
): List<MenuEntry> =
    buildList {
        // 「条件执行」是**进下一页**，不是对这颗键动手 —— 压在顶上，下面用加深分隔隔开。
        //  行尾给箭头（库的 `submenu` 一给就会画那一格），它与下面三行「一行操作」不是一回事。
        //  这一行受 [SHOW_BRANCHES_ENTRY] 控制（收起来时不删这一支，随时能开回来）。
        if (SHOW_BRANCHES_ENTRY) {
            add(
                MenuEntry.Row(
                    title = BRANCHES_TITLE,
                    icon = PageIcons.CommandBranch,
                    submenu = true,
                    onPick = actions.onOpenBranches,
                ),
            )
        }
        addAll(keyActionEntries(selected, cfg, actions))
    }

/**
 * 菜单下半那**三行动作** —— 重命名 / 停用启用 / 删除，全部作用于*当前选中的那一颗*。
 *
 *  单独一支同样是为了让 [keyMenuEntries] 留在 `LongMethod` 之内；行之间一律**细线**，
 *  与上面那条加深带（进页 vs 操作）是两回事。
 *
 *  顺序按**破坏性**从小到大排（用户口径）：改名字不动任何数据；停用只是不接管
 *  （菜单里一字之差就能改回来）；删除最狠，压在最下面 —— 越靠下越不容易误点。
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
                // 上面那一行是「进下一页」，这三行是「对这颗键动手」—— 一条加深带分开
                dividerBefore = true,
                onPick = actions.onRename,
            ),
        )
        add(
            MenuEntry.Row(
                title =
                    if (enabled) {
                        I18n.get(Strings.BINDING_DISABLE_KEY, "停用按键")
                    } else {
                        I18n.get(Strings.BINDING_ENABLE_KEY, "启用按键")
                    },
                icon = PageIcons.Power,
                onPick = { actions.onSetEnabled(!enabled) },
            ),
        )
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
 * 两个弹窗的落点。
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
                        hint(
                            I18n.get(Strings.TOAST_KEY_DELETED, "已删除「%s」").format(selected.label),
                            slot = HINT_SLOT_BINDING,
                        )
                    }
                    // 删掉的正是这一页**正在编辑的那一颗** —— 留在这里已经没有对象了（选中态会
                    //  落回侧键，页面内容跟着换成侧键的绑定，看着像「删错了」）。直接回首页：
                    //  清单在那一页，删完就能看见它少了一行、也能立刻接着挑别的键。
                    //  写盘失败**不退**：那颗键其实还在配置里，退走会让人以为删成功了。
                    navTo(Screen.SHORTCUTS)
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
    // 只在完全成功时报（见下一条同一笔账）
    if (write(now.withKeyName(selected.id, name), "custom_key_rename=${selected.id}") != CommandsSave.Ok) return
    hint(
        I18n.get(Strings.TOAST_KEY_RENAMED, "已重命名为「%s」").format(name),
        slot = HINT_SLOT_BINDING,
    )
}
