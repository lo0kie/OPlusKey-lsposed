package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

/**
 * 反馈渠道 —— 「反馈问题」那一行的浮动菜单用它（**顺序即菜单行序**）。
 *
 *  与 [DonateTarget] 同一套：菜单行首的字形与目标地址都写在这一处，界面只负责摆出来。
 *
 *  **三档一律可点** —— 菜单里不留灰行：没配就置灰，用户读到的不是「还没接上」而是
 *  「这个功能坏了」。三档地址现在都是真的（QQ 频道 / Telegram / Issue）。
 */
internal enum class FeedbackTarget(
    val id: String,
    val labelKey: String,
    val url: String,
) {
    QQ_CHANNEL("qq_channel", Strings.FEEDBACK_QQ_CHANNEL, QQ_CHANNEL_URL),
    TELEGRAM("telegram", Strings.FEEDBACK_TELEGRAM, TELEGRAM_URL),
    ISSUE("issue", Strings.FEEDBACK_ISSUE, ISSUE_URL),
    ;

    /** 菜单行首的字形。 */
    val icon: ImageVector
        get() =
            when (this) {
                // 「频道」没有品牌字形，用「一群人」那颗
                QQ_CHANNEL -> PageIcons.FeedbackChannel

                TELEGRAM -> PageIcons.BrandTelegram

                ISSUE -> PageIcons.BrandGithub
            }

    /** 界面上的名字（字典缺项时显示 [id]，与全项目同一条兜底口径）。 */
    val label: String get() = I18n.get(labelKey, id)
}
