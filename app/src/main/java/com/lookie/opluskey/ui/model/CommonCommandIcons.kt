package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.present.CommonCommands

// 「常用命令」清单项的行首字形 —— 绑定页那一段与「常用命令」页共用这一份。
//
// 为什么单独一个文件：那两处一个在 `screen/binding`、一个在 `screen/common`，各自写一份 `when`
// 就会漂（加一个品牌时只改一处，另一处继续画错字形）。而 `PageIcons` 那一张表是「字形本身」，
// 不该认识 `CommonCommands` 这个业务类型 —— 转译层放这里。

/**
 * 模块自带那几条的品牌 → 行首字形。
 *
 *  品牌标识由 `:core` 给（`CommonCommands.Brand`，那边引不到 `PageIcons`），字形只在这一层有。
 */
internal val CommonCommands.Brand.icon: ImageVector
    get() =
        when (this) {
            CommonCommands.Brand.WECHAT -> PageIcons.BrandWechat
            CommonCommands.Brand.ALIPAY -> PageIcons.BrandAlipay
        }

/**
 * 一条清单项的行首字形 —— 自带那几条用自己的品牌字形，其余按动作取。
 *
 *  品牌要单独一支：`cmd:common.wechat_scan` 是一个 `cmd:` 动作，按动作取只会得到一个
 *  「命令」的通用字形，而这一段的读法恰恰是「哪一家的扫一扫」。
 */
internal fun commonCommandIcon(token: String): ImageVector = CommonCommands.entryOf(token)?.brand?.icon ?: actionIcon(token)
