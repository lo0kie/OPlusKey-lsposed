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
import com.lookie.opluskey.ui.DisabledHint
import com.lookie.opluskey.ui.HINT_SLOT_BINDING
import com.lookie.opluskey.ui.HeaderIconButton
import com.lookie.opluskey.ui.HeaderMenuButton
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.NameDialog
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.BRANCHES_TITLE
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.combo.KeysAskDialog
import com.lookie.opluskey.ui.screen.command.scenarioMenuSummary
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.state.SettingsTarget
import com.lookie.opluskey.ui.state.SettingsTargetState
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

/**
 * 「按键绑定」页顶栏右侧的菜单按钮 + 浮层菜单 —— 对**当前这一颗键**动手的地方。
 *
 * ## 菜单里有什么
 *
 *  0. 「系统场景」—— 进那一页（`Screen.BRANCHES`）。它不是对这颗键的操作，所以与下一行
 *     同属「进下一页」那一组，下面用一条**加深分隔**（三行里第一行的 `dividerBefore`）分开；
 *     这一行**不看这颗键的启停**：停用的键照样能进去把场景配好、再回去启用 ——
 *     整行灰掉等于先把「先配好再开」这条顺序堵死；
 *  1. 「独立设置」—— 进那一页（`Screen.KEY_SETTINGS`）。与顶栏那颗设置键
 *     （[HeaderSettingsButton]）是**同一个入口的两种摆法**，都走 [openKeySettings]；
 *     与上一行同一组（都是进下一页），但**这一行跟着启停走**：这一颗键停用了它就灰掉
 *     （判据见 [SettingsTarget.disabledReason]）—— 上一行留、这一行不留，是有意的不对称：
 *     场景表是「这一档怎么分流」的配置，停用期间先配好是常态；而这一页改的是它自己的覆盖项，
 *     键都不接管了，改它没有去处；
 *  2. 「停用 / 启用这颗键」「重命名」「删除按键」—— 都是对**当前选中的那一颗**动手，
 *     删除最狠、压在最下面（越靠下越不容易误点），停用与改名在它上面。
 *
 *  上面那两行**场景在前、独立设置在后**（用户口径）：场景说的是这一档「怎么分流」，
 *  优先级高于「这一档的显示 / 触发怎么调」。
 *
 * ## 按键清单**不在这里**
 *
 *  切换 / 添加按键都搬到首页与「按键功能」页了（清单与添加都在 `KeysScreen`）：那里才是
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
 * ## 停用状态不在这张菜单里复述
 *
 *  顶栏副标题挪到**大标题下面**之后只剩一行字（库那个槽位不收图标，见 `PageHeader`），
 *  这一页不再挂那枚禁用记号：「这一颗键停用了」由整页控件变灰、副标题压暗，以及下面
 *  「启用这颗键」那一行的动词共同表达。菜单里不再补一行只做说明的灰行。
 *
 *  「系统场景」那一行**不再按手势筛条数**：翻转之后一张表里每块自带三档，
 *  这一行的子标题报的是这颗键全部场景的条数（见 `scenarioMenuSummary`）。
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
                            //  与顶栏那颗设置键同一个落点（同一条 `openKeySettings`）
                            onOpenSettings = {
                                open = false
                                openKeySettings(SettingsTarget.Key(editing))
                            },
                            onOpenBranches = {
                                open = false
                                openBranchesPage()
                            },
                            onRename = {
                                open = false
                                dialog = KeyMenuDialog.RENAME
                            },
                            //  启停都直接交给 `setKeyEnabledFromMenu`：连带关系（含这颗键的组合要不要
                            //  一起停 / 一起开）由共用的 `KeysAsk` 那一问负责 —— 与按键列表那颗开关
                            //  **完全同一套检查与提示**（用户口径），这里不再另开一道自己的确认窗。
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
 * 顶栏那颗「独立设置」键 —— 「按键绑定」页与「组合键编辑」页**共用**这一颗。
 *
 *  它只做一件事：[openKeySettings] —— 把「这一趟改谁」写进 [SettingsTargetState] 再导航到
 *  那一页。页面自己按 target 读参数与写回，所以单键与组合在那边**没有分支**。
 *
 *  摆在这一档页面的顶栏、⋮ **左边**：右边那颗 ⋮ 是对这一档动手（停用 / 改名 / 删除），
 *  左边这颗改的是它对公共项的覆盖 —— 一左一右正好是「它自己」与「它怎么工作」。
 *  ⋮ 菜单里**另留了一份同样的入口**（用户口径：菜单里也要能进），两处走同一条。
 *
 *  **跟着这一档的启停走**：这一档停用了，这颗也灰掉，点一下给出与页面里其它按不动的控件
 *  同一句话（用户口径：按键 / 组合键禁用时，设置入口也禁用）。判据与那句提示都取自
 *  [SettingsTarget.disabledReason]，与 ⋮ 菜单里那一行是同一份实现 —— 不许在这里再判一次。
 */
@Composable
internal fun SettingsPage.HeaderSettingsButton(target: SettingsTarget) {
    //  配置每次重组都是最新那份（热加载、改完立刻生效），所以这里读到的一定是当下这一次。
    val blocked = target.disabledReason(configState.config.value)
    DisabledHint(blocked) {
        HeaderIconButton(
            onClick = { openKeySettings(target) },
            enabled = blocked == null,
        ) {
            Icon(PageIcons.SettingsCog, contentDescription = I18n.get(Strings.KEY_SETTINGS_TITLE, "独立设置"))
        }
    }
}

/**
 * 进「独立设置」页 —— 顶栏那颗设置键与两页 ⋮ 里那一行**共用这一处**。
 *
 *  只做一件事：把「这一趟改谁」写进 [SettingsTargetState] 再切页。
 *
 * @param target 这一趟改的是哪一颗键 / 哪一组组合（见 [SettingsTarget]）
 */
internal fun SettingsPage.openKeySettings(target: SettingsTarget) {
    SettingsTargetState.current = target
    XLog.w("进「${I18n.get(Strings.KEY_SETTINGS_TITLE, "独立设置")}」页：$target")
    navTo(Screen.KEY_SETTINGS)
}

/**
 * 进「条件执行」页 —— 只切栈。
 *
 *  那一页有自己的草稿（`branchDraft`），进页时由它自己按「这颗键 + 这一档手势」`load`
 *  （见 `BranchScreen` 开头那段 effect），不经过命令页的 `commandDraft`。
 *  离栈时草稿由 `discardPageStateOf` 清，所以下次进来是重新载入的一份。
 */
private fun SettingsPage.openBranchesPage() {
    //  从「按键绑定」进来编的是**这颗键**的这一档 —— 清掉上一趟可能留下的组合键目标。
    //  草稿出栈时本来也会 `reset`，这里再明确一次：两条入口共用一个目标位
    //  （`BranchDraft.comboId`），谁进来谁负责把它指对。
    branchDraft.comboId = null
    XLog.w("按键绑定：进「${BRANCHES_TITLE}」页")
    navTo(Screen.BRANCHES)
}

/** 菜单底下那三个弹窗 —— 同一时刻至多一个。 */
private enum class KeyMenuDialog {
    RENAME,
    DELETE,

    /** 停用这一颗键的确认窗（启用不弹，直接写）。 */
    DISABLE,
}

/**
 * 菜单各项的回调 —— 打成一个包只为让 `keyMenuEntries` 的参数个数留在 detekt 的
 * `LongParameterList` 之下（那几条本来就同属「菜单点了什么」）。
 */
private class KeyMenuActions(
    /** 「独立设置」那一行（进另一页；与顶栏那颗设置键同一个落点）。 */
    val onOpenSettings: () -> Unit,
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
        //  「这一档停用了没有」只算一次：下面「独立设置」那一行读它（判据与提示语都出自
        //  `SettingsTarget.disabledReason`，顶栏那颗设置键读同一份）。
        val blocked = SettingsTarget.Key(selected).disabledReason(cfg)
        // 「系统场景」与「独立设置」都是**进下一页**，所以同在一组、都压在顶上；
        //  下面那三行才是「对这颗键动手」，由它们第一行那条加深带隔开。
        //  顺序（用户口径）：**系统场景在前、独立设置其次** —— 场景是这一档「怎么分流」，
        //  优先级高于「这一档的显示 / 触发怎么调」。
        //  这一行受 [SHOW_BRANCHES_ENTRY] 控制（收起来时不删这一支，随时能开回来）。
        if (SHOW_BRANCHES_ENTRY) {
            //  这一行**不看这颗键的启停**：条件表说的是「这一档怎么分流」，而停用的键照样要能
            //  先把表配好、再回去启用（与「独立设置」同一条口径）。按启停置灰过一版，真机上
            //  停用的键连场景页都进不去 —— 只剩「先启用、再配、再停用」这一条别扭的路。
            add(
                MenuEntry.Row(
                    title = BRANCHES_TITLE,
                    //  子标题报这一档手势的条数（只数启用中的）—— 与页面里那条横幅同一份判据。
                    //  这一行是**进下一页**，所以行尾给箭头；子标题只在菜单里出现（横幅那句在页面上）。
                    summary = scenarioMenuSummary(cfg.key(selected).scenes),
                    icon = PageIcons.Scene,
                    submenu = true,
                    onPick = actions.onOpenBranches,
                ),
            )
        }
        //  「独立设置」**跟着这一颗键的启停走**：停用的键不接管任何按键，而这一页改的全是
        //  它的覆盖项，进去改也没有去处（用户口径：按键 / 组合键禁用时，设置入口也禁用）。
        //  上一行「系统场景」不跟：停用期间先把场景表配好、再回去启用，是常见顺序。
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.KEY_SETTINGS_TITLE, "独立设置"),
                icon = PageIcons.SettingsCog,
                submenu = true,
                enabled = blocked == null,
                disabledHint = blocked,
                onPick = actions.onOpenSettings,
            ),
        )
        addAll(keyActionEntries(selected, cfg, actions))
    }

/**
 * 菜单下半那**三行动作** —— 停用启用 / 重命名 / 删除，全部作用于*当前选中的那一颗*。
 *
 *  单独一支同样是为了让 [keyMenuEntries] 留在 `LongMethod` 之内；行之间一律**细线**，
 *  与上面那条加深带（进页 vs 操作）是两回事。
 *
 *  顺序（用户口径）：停用 / 启用在前、重命名其次、删除压最下面 —— 删除最狠，
 *  越靠下越不容易误点。
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
                title =
                    if (enabled) {
                        I18n.get(Strings.BINDING_DISABLE_KEY, "停用按键")
                    } else {
                        I18n.get(Strings.BINDING_ENABLE_KEY, "启用按键")
                    },
                //  停用 / 启用各一颗（锁 / 开锁）：这一行本来就是随状态换文案的，图标跟着换才一致
                icon = if (enabled) PageIcons.KeyLocked else PageIcons.KeyUnlocked,
                // 上面那一行是「进下一页」，这三行是「对这颗键动手」—— 一条加深带分开
                dividerBefore = true,
                onPick = { actions.onSetEnabled(!enabled) },
            ),
        )
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.BINDING_RENAME_KEY, "重命名"),
                icon = PageIcons.MenuRename,
                // 侧键的名字不归用户改
                enabled = selected.isCustom,
                disabledHint = I18n.get(Strings.BINDING_SIDE_KEY_NO_RENAME, "快捷键不能改名"),
                onPick = actions.onRename,
            ),
        )
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.BINDING_DELETE_KEY, "删除按键"),
                icon = PageIcons.CommandDelete,
                // 侧键不给删（它不是用户收集来的东西）
                enabled = selected.isCustom,
                disabledHint = I18n.get(Strings.BINDING_SIDE_KEY_NO_DELETE, "快捷键不能删除"),
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

    //  「重命名」与「添加按键」是同一件事（填一个名字），正文都走 `NameDialog`。
    //  名字是**存下来的字面量**（见 `WatchedKey`）：改完就写进清单那一项，切界面语言不会再变，
    //  所以这一格是用户自己的命名，不是一条字典文案。
    NameDialog(
        show = dialog == KeyMenuDialog.RENAME,
        title = I18n.get(Strings.BINDING_RENAME_KEY, "重命名"),
        label = I18n.get(Strings.BINDING_KEY_NAME, "按键名称"),
        icon = PageIcons.LabelImportant,
        initial = selected.label,
        onDismiss = onDismiss,
        onConfirm = { name ->
            // 改名写进配置（身份是 id，绑定不丢）；失败时 `write` 自己会提示，这里照样关窗。
            if (selected.isCustom) confirmRenameKey(selected, name)
            onDismiss()
        },
    )
    //  启停 / 连带的那一问「含它的组合要不要一起停 / 一起开」—— 与按键列表那颗开关共用
    //  同一个待确认对象与同一个组件（用户口径：两处完全一样的检查与提示）。
    KeysAskDialog()
    ConfirmDialog(
        show = dialog == KeyMenuDialog.DELETE,
        title = I18n.get(Strings.BINDING_DELETE_KEY, "删除按键"),
        summary =
            I18n
                .get(
                    Strings.BINDING_DELETE_KEY_CONFIRM,
                    "[%s] 会从清单里删掉。",
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
                            I18n.get(Strings.TOAST_KEY_DELETED, "已删除 [%s]").format(selected.label),
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
