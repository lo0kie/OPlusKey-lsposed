package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 行尾那格小字的字号 —— **与小标题同档**（`SmallTitle` 是 12sp / Medium）。
 *
 *  原先取的是库给输入框用的 `TextFieldDefaults.CounterFontSize`（10sp）：那是「贴在输入框
 *  右下角」那一档的字号，而这一格现在的邻居是小标题 —— 差 2sp，加上左边是 Medium、这边是
 *  常规体，看起来比「小两号」还弱，像一截掉队的灰字。
 *
 *  字号跟着邻居走、**字重与颜色仍降一档**（常规体 + `onSurfaceContainerHigh`）：主次靠这两样分，
 *  不靠把字号再缩小一号。
 */
private val COUNTER_FONT_SIZE = 12.sp

/**
 * **行尾那一格的小字** —— 字数统计（`12/24`）或条数（`3`），给「小标题 / 弹窗标题」的行尾槽用。
 *
 *  三个地方共用：命令名称（`n/24`）、命令详情的全屏编辑（`n/1024`）、
 *  已保存的命令列表里那两段的**条数**（没有上限，所以只有 `n`）。
 *  字号、颜色必须一致，各写一份迟早走形；位置不在这里 —— 由各自那一层摆
 *  （小标题推到行尾、弹窗里紧随标题且不参与居中）。
 *
 *  为什么不放在输入框里（库的 `maxCount`）：库把计数器画在输入框右下角，离正在读的那一行很远，
 *  读起来像另一条信息。
 *
 * @param max 上限；`null` = 没有上限，只画条数
 */
@Composable
internal fun TitleCounter(
    length: Int,
    max: Int? = null,
) {
    Text(
        text = if (max == null) "$length" else "$length/$max",
        fontSize = COUNTER_FONT_SIZE,
        color = COUITheme.colorScheme.onSurfaceContainerHigh,
    )
}
