package com.lookie.opluskey.ui.screen.about

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 「更多选项」页上那三个**行内菜单**的锚点与开关（语言 / 反馈问题 / 作者）。
 *
 *  三样东西长得一样，以前是三对 `remember { }` 摊在 `AboutScreen` 的开头 —— 谁和谁是一对
 *  只能靠变量名认；收成一个对象之后，`ui.langClick` / `ui.showLangMenu` 天然成组，
 *  菜单那三个 composable 也只需要一个参数。
 *
 *  注意：锚点必须**跟着那一行**（`preciseClickAnchor` 挂在行上），所以它建在这里、由行与菜单
 *  两边共用一份 —— 各建一份的话菜单会弹在别处。
 */
internal class AboutUiState {
    /** 「语言」那一行。 */
    val langClick = PreciseClickState()
    var showLangMenu by mutableStateOf(false)

    /** 「反馈问题」那一行（三个反馈渠道）。 */
    val feedbackClick = PreciseClickState()
    var showFeedbackMenu by mutableStateOf(false)

    /** 「作者」那一行（GitHub / 酷安两个去处，见 [com.lookie.opluskey.ui.model.AuthorTarget]）。 */
    val authorClick = PreciseClickState()
    var showAuthorMenu by mutableStateOf(false)

    /** 「界面颜色」那一行（跟随系统 / 浅色 / 深色）。 */
    val themeClick = PreciseClickState()
    var showThemeMenu by mutableStateOf(false)

    /** 「系统配色」那一行（五套强调色，见 [com.lookie.opluskey.ui.model.PagePalette]）。 */
    val paletteClick = PreciseClickState()
    var showPaletteMenu by mutableStateOf(false)

    /**
     * 「签名」那一行**展开了没有** —— 指纹平时收着，点一下才列出来（见 `ui/ExpandableRow`）。
     *
     *  放进这里而不是 `remember` 在行里：这一位要在整页重组（比如切语言）之后仍保持，
     *  与上面几个菜单开关同一档。
     */
    var signatureExpanded by mutableStateOf(false)
}
