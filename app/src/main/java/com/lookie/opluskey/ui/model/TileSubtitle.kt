package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.brightnessTitle
import com.lookie.opluskey.core.model.varSetTitle
import com.lookie.opluskey.core.model.volumeTitle

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
    when (spec.token) {
        // 「应用控制」那一格 / 抽屉里的「应用控制」那一行：小字**只写应用名**（`微信`）。
        //
        //  **不加「打开应用:」前缀、也不带「具体是哪一个」**（用户口径「只要应用名，不用打开应用
        // 和细节之类的」）：这一行只有一格宽，前缀一占、名字就只剩一半；而「打开 / 结束」这件事
        //  行首那颗字形已经说了（`PageIcons.AppOpen` / `AppStop`，与菜单那两行同一对），
        //  再写一遍就是把同一句话说了两次。
        ActionCatalog.APP_CONTROL_TILE, ActionCatalog.APP_TILE -> {
            appLabel?.takeIf { it.isNotBlank() }
        }

        //  系统开关那一格再细一档：「WIFI - 打开」/「NFC - 切换」—— 只写开关名时
        //  看不出绑的是哪一档（三档里连「切换」也要写，不然它和只写名字没区别）。
        //  **必须判格子**：`switchStateLabel` 只看「当前绑的是什么」，而 `current` 每一格
        //  都相同 —— 不判的话绑一次「打开」，整片网格的每一格都会挂上「打开」。
        ActionCatalog.SWITCH_TILE -> {
            listOfNotNull(boundMode?.label, ActionCatalog.switchStateLabel(current))
                .joinToString(" - ")
                .ifEmpty { null }
        }

        //  变量那一格：小字写**绑的是哪一步**（`「计数」+= 1`）。
        //  只写格名「自定义变量」的话，绑没绑、绑的是加法还是取反，一点都看不出来；
        //  而 `自定义变量: ` 那个前缀在这只有一格宽的格子里放不下（格名自己就写着它），
        //  所以取 [varSetTitle] 那半句。
        ActionCatalog.VARS_TILE -> {
            (current as? Action.VarSet)?.let { varSetTitle(it) }
        }

        //  亮度那一格：小字写**这一档 + 数值**（`增加 15%`）—— 只写「增加」看不出加多少。
        //  与步骤标题、执行提示同一句（`brightnessTitle`），不在这里另拼一份。
        ActionCatalog.BRIGHT_TILE -> {
            (current as? Action.Brightness)?.let { brightnessTitle(it) }
        }

        //  音量那一格同理：小字写「增加 10%」，与步骤标题同一句
        ActionCatalog.VOLUME_TILE -> {
            (current as? Action.Volume)?.let { volumeTitle(it) }
        }

        else -> {
            boundMode?.label
        }
    }
