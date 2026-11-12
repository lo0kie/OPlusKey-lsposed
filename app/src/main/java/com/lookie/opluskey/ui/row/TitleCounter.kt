package com.lookie.opluskey.ui.row

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
    TitleSmallText(if (max == null) "$length" else "$length/$max")
}

/**
 * 行尾那格小字的**样式本体** —— 这一格要写的任何一句话都走它。
 *
 *  两处在用：字数统计 / 条数（[TitleCounter]），以及滑杆输入窗里的**取值区间**（`480–2000`
 *  那种，见 `NameDialog` 的 `range`）。两者是同一格、同一个位置，样式必须一份 ——
 *  各写一遍迟早走形，而这一格的字号本来就是照邻居（小标题）定的。
 */
@Composable
internal fun TitleSmallText(text: String) {
    Text(
        text = text,
        fontSize = COUNTER_FONT_SIZE,
        color = COUITheme.colorScheme.onSurfaceContainerHigh,
    )
}

/**
 * 「标题 (条数)」—— 把条数**拼进标题那一段字**里，而不是行尾另起一格。
 *
 *  与浮层菜单的小标题同一条口径（`响铃状态 (4)`，见 `PopupSectionTitle`）：半角括号、
 *  括号前留一个半角空格、**与标题同一份样式** —— 不降字重、不压透明度。它读作标题的一部分，
 *  不是「旁边还有一条信息」，所以不做差异处理。
 *
 *  用在**分组标题**上（动作网格那几组、抽屉里那几组、「自定义命令」那一段）：那一行没有
 *  行尾槽位可占，也不该为它多摆一个控件。
 *
 *  条数**由调用点从自己那张表里数**（`group.size` / `specs.size`）—— 同一个组名在
 *  `ActionCatalog` 的三张表里条数不一样（场景动作多一个「跟随默认」、命令步骤少「无操作」），
 *  写死一个数，换个页面就少一个 / 多一个。
 */
internal fun titleWithCount(
    title: String,
    count: Int,
): String = "$title ($count)"
