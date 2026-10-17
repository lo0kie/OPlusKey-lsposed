package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme

// 页面上的颜色与透明度：磨砂 / 浮层 / 菜单分隔 / 主题色板。 —— 从 PageConst.kt 拆出（同包搬迁，调用点不变）。

/**
 * 磨砂面上给不透明底色降 alpha 用的那一档（顶栏 + 底部工具条）。
 *
 *  注意：右下角那颗 FAB **不参与这一档** —— 它把底色调成**主题原色**（`colorScheme.primary`），
 *  乘这一档会掺进底下的糊里、显得发白，与主题色对不上。别再照着顶栏 / 工具条给它乘回去。
 *
 *  一律**按比例缩**（`color.alpha * 这一档`），不要 `copy(alpha = …)` 整个换 —— 深浅两套色板里
 *  同名色可能一个是实色、一个是半透（库深色板的 `surfaceContainer` 就是半透白），替换会把后者顶成亮色。
 */
internal const val FROSTED_SURFACE_ALPHA = 0.67f

/**
 * 浮层面板的本色 —— 浅色照搬库 `ListPopupContent` 那一对常量（COUI 的 `couiColorSurfaceTop`：
 * `coui_color_surface_top` 浅色 `#FFFFFF` / 夜间 `#333333`，库内是 private，只能照抄一份）；
 * 夜间那一档见 [POPUP_SURFACE_DARK]（比库原生的亮，与 [POPUP_FROSTED_ALPHA] 配套）。
 *
 *  为什么不能拿主题里那几档 `surface*` 顶替：面板要**比页面亮一档**——暗色下页面是纯黑
 *（实测 `#000000`）、卡片是 `#1a1a1a`，而 `surfaceContainerHighest` 只有 `#252525`，
 *  拿它当磨砂底色会比库原生面板还暗（真机反馈「暗色下底色太暗」）。
 */
internal val POPUP_SURFACE_LIGHT = Color.White

/**
 * [POPUP_SURFACE_LIGHT] 的夜间那一档 —— `#333333` 是库原生的值，这里取到 `#3d3d3d`、
 *  **亮一档**：面板底色降到半透（见 [POPUP_FROSTED_ALPHA]）之后，底下的糊会透过一部分，
 *  本色不抬这一档，落定后的面板就比库原生面板暗（真机反馈过「暗色下底色太暗」）。
 *  两笔账合起来是把**落定亮度**钉在库原生那一档，透上来的糊才有得看。
 */
internal val POPUP_SURFACE_DARK = Color(0xFF3D3D3D)

/**
 * 浮层磨砂底色的 alpha（**只给浮层菜单用**）—— 这一档就是「底下的糊能透上来多少」：
 *  越小糊越明显。它是**和 [POPUP_SURFACE_DARK] 一起调的**：糊的是底下的页面，暗色下页面接近纯黑，
 *  透得越多面板越黑 —— 所以本色抬亮一档，把落定亮度钉在库原生面板（`#333333`）那一档上。
 *  0.72 是比顶栏（[FROSTED_SURFACE_ALPHA]）略保一点、又明显看得见糊的位置；顶栏可以更低，
 *  是因为它背后恒是页面自己的滚动内容，而浮层可能压在任意画面上。
 *
 *  消费方只有浮层菜单（`ui/MenuPopup.kt` 里 `MenuPopup` 那层）。确认弹窗的面板走**另一档**
 *  [DIALOG_FROSTED_ALPHA]：同一门账，只是它面板大、底下更平，要更透才看得见糊。
 */
internal const val POPUP_FROSTED_ALPHA = 0.72f

/**
 * 确认弹窗面板底色的 alpha —— 比浮层菜单那一档（[POPUP_FROSTED_ALPHA]）**更透**。
 *
 *  这一档就是「底下的糊能透上来多少」：0.72 时糊只占 28%，而弹窗面板比菜单大得多、底下的页面又平
 *（一张卡片加一片空白），同一档看下来只剩一层底色、看不出背后糊过（真机反馈「生效了但不太明显」）。
 *  0.6 把糊的占比提到 40%，面板底下是糊过的内容一眼能认出来；再低正文压在花底上就发虚，到头了。
 *
 *  底色本色与浮层菜单共用（[popupSurfaceColor]），只有这一档分家。
 */
internal const val DIALOG_FROSTED_ALPHA = 0.6f

/**
 * 「按键绑定」主开关为关时分段控件的不透明度 —— 0.4。
 *
 * 只有分段控件需要这一手：它没有 `enabled` 参数；网格格子 / 长按卡都走 COUI 自己的
 * `enabled = false` 通道（用 `disabledColor`，不是把颜色整体调淡）。
 */
internal const val DISABLED_ALPHA = 0.4f

/**
 * 浮层菜单里两种分隔的 alpha。
 *
 *  库默认那一档在磨砂面板上几乎看不见，所以自己给：**细线**暗色走亮线、浅色走黑线；
 *  **组间那条槽**暗色要压得更重（`#333` 面板上压到 `#1f` 才看得出是一道槽）。
 */
internal const val MENU_ROW_LINE_DARK_ALPHA = 0.14f

internal const val MENU_ROW_LINE_LIGHT_ALPHA = 0.10f

internal const val MENU_GROUP_LINE_DARK_ALPHA = 0.32f

internal const val MENU_GROUP_LINE_LIGHT_ALPHA = 0.08f

/** 底部工具栏那颗胶囊的阴影 alpha（[CommandScreen] 的工具栏）。 */
internal const val TOOLBAR_SHADOW_ALPHA = 0.1f

internal const val APP_KIND_TAG_BG_ALPHA = 0.10f

/**
 * 应用种类标签里「模块」那一档的颜色 —— 紫，两套主题各一值。
 *
 *  为什么不取库的色板：COUI 那套只有蓝（`primary`）与红（`error`）两个色相 —— 蓝给了「系统」、
 *  红是「不可打开」的警告档，第三个色相只能自己定一份。判据同 `popupSurfaceColor`（按 `background`
 *  亮度切），两档都取中等亮度：标签底是它自己的 10% 透明版，太深太浅都压不住。
 */
internal val APP_KIND_MODULE_LIGHT = Color(0xFF7C4DFF)

internal val APP_KIND_MODULE_DARK = Color(0xFFB9A3FF)

/**
 * 应用名旁边那枚「分身」标签的颜色 —— 明暗各一值。
 *
 *  **只有一档**：槽位已经写在名字里了（`微信 1` / `微信 2`），标签只负责说「这一行是分身」，
 *  不需要再用五种颜色去区分第几个 —— 那会让同一屏里出现五六种标签色，反而更难读。
 *
 *  色相避开库里的两支与模块紫：蓝（`primary`）是「系统」、红（`error`）是「不可打开」、
 *  紫是「模块」。判据与 [APP_KIND_MODULE_LIGHT] 同一套（按 `background` 亮度切）。
 */
internal val CLONE_TAG_LIGHT = Color(0xFF00897B)

internal val CLONE_TAG_DARK = Color(0xFF4DD0B1)

internal val LIGHT_SCHEME = lightColorScheme()

internal val DARK_SCHEME = darkColorScheme()

/**
 * 栈顶之下那一页的压暗程度 —— 取 0.25（黑的 alpha）。
 *
 * 库在被盖页底下垫一层全屏黑，alpha = [PAGE_COVER_DIM] × 该页相对深度，静止时恒定压暗、推入渐深、弹出渐亮；
 * 再小压不住，再大暗得发灰。
 */
internal const val PAGE_COVER_DIM = 0.25f
