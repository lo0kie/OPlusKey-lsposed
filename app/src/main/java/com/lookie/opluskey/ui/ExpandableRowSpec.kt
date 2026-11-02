package com.lookie.opluskey.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.suqi8.coui.kmp.basic.CardListPosition

/**
 * 一行**会折行的长文本**长什么样 —— 把「画什么」与「展没展开」分开，后者由调用方持有。
 *
 * @param text 这一行的值（**一段**文字，界面上可能折成两三行）
 * @param iconTint 行首图标颜色 —— **`null` = 跟着「随机图标配色」走**（见 `LocalIconTint`），
 *   没开就退回常规内容色。要给语义色的（「不是这包」那种红）由调用方显式给。
 */
internal class ExpandableRowSpec(
    val title: String,
    val text: String,
    val icon: ImageVector,
    val position: CardListPosition,
    val iconTint: Color? = null,
)
