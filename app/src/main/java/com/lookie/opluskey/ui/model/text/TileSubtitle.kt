package com.lookie.opluskey.ui.model.text

import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.present.ActionCatalog
import com.lookie.opluskey.core.present.commandTitleOf

/**
 * 「这个动作绑的是什么」—— **网格格子的小字**与**抽屉候选行的子标题**共用这一份。
 *
 *  两处摆的是同一张清单（[ActionCatalog.GROUPS]），同一个「系统开关」在两处写成两句话的话，
 *  用户看到的就是两套说法。所以判据只留在这里，界面两侧都只负责把它填进各自那个槽
 *  （网格走 `ActionTile.subtitle`、抽屉走 `CardRow.Item.summary`）。
 *
 *  只有「格子 / 那一行 + 当前绑定」一起看才知道该写什么：`current` 是同一个动作，不判格子的
 *  那类写法会让整片网格都挂上同一句（见下面「系统开关」那一段）。
 *
 *  @param spec 这一格 / 这一行
 *  @param current 当前绑的动作（非空：调用方先判「有没有绑定」）
 *  @param boundMode 这一格 / 这一行绑的是哪一档（入口格专用；非入口格传 `null`）
 *  @param appLabel 「打开应用 / 选择应用」那几行的应用名，由调用方按当前绑定解析
 *    （要走 `PackageManager`，见 `boundAppName`）
 */
internal fun tileSubtitle(
    spec: ActionCatalog.Spec,
    current: Action,
    boundMode: ActionCatalog.Spec?,
    appLabel: String?,
): String? =
    when {
        // 「应用控制」那一格 / 抽屉里的「应用控制」那一行：小字**只写应用名**（`微信`）。
        //
        //  **不加「打开应用:」前缀、也不带括号注记**（用户口径「只要应用名，不用打开应用
        // 和细节之类的」）：这一行只有一格宽，前缀一占、名字就只剩一半；而「打开 / 结束」这件事
        //  行首那颗字形已经说了（`PageIcons.AppOpen` / `AppStop`），再写一遍就是把同一句说两次。
        //  所以这一支**不走** `CommandTitle` 的任何出口，直接取应用名 —— 那一族的三段材料里
        //  「名字」是动词（`打开应用`）、「状态」是目标，哪一段都不是这一格要的。
        spec.token == ActionCatalog.APP_CONTROL_TILE || spec.token == ActionCatalog.APP_TILE -> {
            appLabel?.takeIf { it.isNotBlank() }
        }

        // 其余每一格：**先确认当前动作真的落在这一格**，再问 `forTile`（那一格小字那一支）。
        //
        //  判据是 `modeHitOf(current)?.tile`，不是「这一格叫什么」—— 原来「系统开关」那一支只判
        //  `spec.token`，于是别的格绑了动作（`前台转小窗 - 展开`）它也跟着念一句，系统开关格
        //  下面凭空多出一行小字（真机撞到过）。
        //
        //  一句话由 `CommandTitle` 拼，这里只选出口：开关格要 `WIFI - 打开`、导航格要 `主屏幕`、
        //  变量格要 `「计数」+= 1` —— 都是「不带父动作」那一份，不必再按格子分支。
        belongsToThisTile(spec, current) -> {
            commandTitleOf(current).forTile(current, { "" }, current.token)
        }

        // 这一格此刻没绑到它自己那一族（或者绑的是一个不属于任何入口格的动作）：
        // 退回「这一格绑的是哪一档」的档位名。
        else -> {
            boundMode?.label
        }
    }

/**
 * 当前动作是不是落在 [spec] 这一格里。
 *
 *  不在任何入口格里（顶层格 `截屏`、自定义命令）时返回 `false` —— 那些动作没有「格」可言，
 *  不该让某一格替它显出一行小字。
 */
private fun belongsToThisTile(
    spec: ActionCatalog.Spec,
    current: Action,
): Boolean = ActionCatalog.modeHitOf(current)?.tile?.token == spec.token
