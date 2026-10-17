package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

/**
 * 捐赠的三个去处 —— 「捐赠打赏」那一行的浮动菜单与收款码窗口共用这一处（**顺序即菜单行序**）。
 *
 * @param id 收款码图在 assets 里的文件名（`donate/<id>.webp`，900×900 近乎黑白 + 无损 WebP），
 *   也是日志里的标识
 * @param labelKey 界面上的名字（字典键，见 [Strings]）
 */
internal enum class DonateTarget(
    val id: String,
    val labelKey: String,
) {
    QQ("qq", Strings.DONATE_QQ),
    WECHAT("wechat", Strings.DONATE_WECHAT),
    ALIPAY("alipay", Strings.DONATE_ALIPAY),
    ;

    /** 菜单行首 / 收款码缺席时的兜底字形。 */
    val icon: ImageVector
        get() =
            when (this) {
                QQ -> PageIcons.BrandQq
                WECHAT -> PageIcons.BrandWechat
                ALIPAY -> PageIcons.BrandAlipay
            }

    /** 界面上的名字（字典缺项时显示 [id]，与全项目同一条兜底口径）。 */
    val label: String get() = I18n.get(labelKey, id)
}
