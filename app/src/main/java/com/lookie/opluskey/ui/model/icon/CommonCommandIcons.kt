package com.lookie.opluskey.ui.model.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.CommonCommand
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

/**
 * **这一条现在该画哪颗字形** —— 换过图标就用换的那颗（[FluidIcons] 里那一批），否则按动作取。
 *
 *  **只在这里判，不落文件**：常用命令是模块自己界面里的一格（绑定页那一段、「常用命令」页的
 *  清单），画 `ImageVector` 就够。它不像 `fluid:` 与「执行时提示走流体云」那样要被宿主进程画
 *  到胶囊上 —— 那两处没有 Compose 运行时，才必须先把字形渲染成 png（见 `ActionCardArt`）。
 *
 *  换的 id 认不出来（手改过配置）也退回按动作取 —— 不留一格空白。
 *
 *  绑定页那一段与「常用命令」页共用这一份：两处各判一次就会漂。
 */
internal fun commonCommandIcon(cmd: CommonCommand): ImageVector =
    cmd.icon.takeIf { it.isNotEmpty() }?.let { FluidIcons.of(it) } ?: commonCommandIcon(cmd.token)
