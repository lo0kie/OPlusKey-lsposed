package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

/**
 * 作者的去处 —— 「作者」那一行的浮动菜单用它（**顺序即菜单行序**）。
 *
 *  与 [FeedbackTarget] / [DonateTarget] 同一套：菜单行首的字形与目标地址都写在这一处，
 *  界面只负责摆出来。
 *
 *  **两处一律可点**（与「反馈问题」三档同一条口径）：菜单里不留灰行 —— 没配就置灰，
 *  用户读到的不是「还没接上」而是「这个功能坏了」。
 *
 *  **酷安排第一**（国内用户更常用的那个），GitHub 第二 —— 别按字母序「顺手」把它调回去。
 */
internal enum class AuthorTarget(
    val id: String,
    val labelKey: String,
    val url: String,
) {
    COOLAPK("coolapk", Strings.AUTHOR_COOLAPK, COOLAPK_URL),
    GITHUB("github", Strings.AUTHOR_GITHUB, PROFILE_URL),
    ;

    /** 菜单行首的字形（见各自的取值处）。 */
    val icon: ImageVector
        get() =
            when (this) {
                COOLAPK -> PageIcons.BrandCoolapk
                GITHUB -> PageIcons.BrandGithub
            }

    /** 界面上的名字（字典缺项时显示 [id]，与全项目同一条兜底口径）。 */
    val label: String get() = I18n.get(labelKey, id)
}
