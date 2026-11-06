package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog

/**
 * 「这一行长什么样」—— 网格那一格与抽屉那一行**共用这一份**。
 *
 * ## 为什么要有它
 *
 *  两处摆的是同一张清单（`ActionCatalog.GROUPS`），但渲染是两套代码：网格走 `BuiltInTile`、
 *  抽屉走 `stepSheetRow`。清单同源不等于展示同源 —— 每加一条「怎么显示」的规则（行首随绑定
 *  那一档换、小字写什么、行尾要不要箭头），都得在两边各写一遍，漏一遍就成「同一个系统开关
 *  在两处长不一样」。这个坑踩过三次：行首字形（抽屉早期直接 `tileIcon(spec)`）、行尾箭头
 *  （早先只在抽屉有）、小字（抽屉压根没有）。
 *
 *  现在规则只在这里；两侧各自塞自己的槽（网格 `ActionTile`、抽屉 `CardRow.Item`），
 *  以后加规则只改这一处，从结构上不会再走形。清单本身的差异（抽屉把 `appctl:` 换成
 *  `app:`「选择应用」）仍由 `ActionCatalog.STEP_GROUPS` 负责，那是**内容**不是**展示**。
 *
 * @property trailing 行尾箭头。**网格的格子没有行尾**，只有抽屉那个槽用它。
 */
internal data class ActionRowView(
    val title: String,
    val icon: ImageVector,
    val subtitle: String?,
    val selected: Boolean,
    val trailing: ImageVector?,
)

/**
 * @param spec 这一格 / 这一行
 * @param current 当前绑的动作（`null` = 没绑定过 / 不知道，整行按「未选中的清单条目」画）
 * @param appLabel 「应用控制 / 选择应用」那几行的应用名（要走 `PackageManager`，由调用方查好递进来）
 */
internal fun actionRowView(
    spec: ActionCatalog.Spec,
    current: Action?,
    appLabel: String? = null,
): ActionRowView {
    //  这一格 / 这一行绑的是哪一档 —— 判据只在 `ActionCatalog` 一处
    val boundMode = current?.let { ActionCatalog.boundModeOf(spec, it) }
    val selected = current?.let { ActionCatalog.isSelected(spec, it) } ?: false
    return ActionRowView(
        title = spec.label,
        //  行首那颗：绑在「应用 / 选择应用」这一族时按绑定细分 → 命中那一档的档位字形 →
        //  这一格自己的大类字形。顺序即优先级，两侧不再各拼一遍。
        icon =
            (if (selected) boundAppIcon(spec, current) else null)
                ?: boundMode?.let { tileIcon(it) }
                ?: tileIcon(spec),
        subtitle = current?.let { tileSubtitle(spec, it, boundMode, appLabel) },
        selected = selected,
        trailing = rowTrailingIcon(spec),
    )
}

/**
 * 「应用控制」（网格那一格）与「选择应用」（抽屉那一行）绑成什么样，就画什么样 ——
 * 但只分**菜单里那两个状态**：打开族一颗、结束一颗。
 *
 *  ## 为什么不再按组件 / 快捷方式细分
 *
 *  这一格代表的是「打开 / 结束」两个状态，与它下面那层菜单的两行**一一对应**：菜单里那两颗
 *  是什么，格子里就是什么（`PageIcons.AppOpen` / `PageIcons.AppStop`）。以前还按绑的具体形态
 *  换成「界面」「快捷方式」两颗字，代价是同一格在绑了不同东西时长得像另一个动作 ——
 *  而「绑的是哪个应用的哪个快捷方式」本来就有下面那行小字在说（用户口径：格子里不要换
 *  组件 / 快捷方式的图标，只用菜单那两个状态的图标）。
 *
 *  必须判**是不是这一族**：`current` 对整片网格是同一个动作，不判格子就会让导航 / 响铃 /
 *  媒体那几格全画成应用那一族的字形。
 *  也必须判 `selected`（调用点已判）：抽屉里的「选择应用」是**一步**，当前绑的是「结束应用」
 *  时它不是这一步 —— 那时画一颗「结束」等于说反了。
 */
private fun boundAppIcon(
    spec: ActionCatalog.Spec,
    current: Action?,
): ImageVector? {
    if (current == null) return null
    if (spec.token != ActionCatalog.APP_CONTROL_TILE && spec.token != ActionCatalog.APP_TILE) return null
    return when (current) {
        //  结束那一档：「停掉」的方块 —— 与菜单「结束应用」那一行同一颗
        is Action.Process -> {
            PageIcons.AppStop
        }

        //  打开那一族（整个应用 / 某个界面 / 快捷方式）：与菜单「打开应用」那一行同一颗
        is Action.App, is Action.AppWindow, is Action.AppShortcut -> {
            PageIcons.AppOpen
        }

        else -> {
            null
        }
    }
}

/**
 * 行尾那颗箭头（只有抽屉用）：点了去**另一张页面**的给右箭头，点开还有**一层菜单**的给选择器，
 * 点一下就落成一步的不给。
 *
 *  判据用 token / `modeMenu`，不看字形 —— 字形是「画什么」，与「点了去哪」无关。
 */
private fun rowTrailingIcon(spec: ActionCatalog.Spec): ImageVector? =
    when {
        spec.token == ActionCatalog.APP_TILE || spec.token == ActionCatalog.VARS_TILE -> {
            PageIcons.EnterArrow
        }

        ActionCatalog.modeMenu(spec.token) != null -> {
            PageIcons.Selector
        }

        else -> {
            null
        }
    }
