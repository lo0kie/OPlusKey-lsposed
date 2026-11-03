package com.lookie.opluskey.ui.screen.combo

import androidx.compose.ui.unit.Dp

/**
 * 组合行标题里那三样宽度 —— **整列量一次**，五行十行都用同一份。
 *
 *  为什么不能每行各量各的：两列的宽一旦逐行浮动，分隔符就会跟着前一个名字的长短走，
 *  一列下来每行都错开（[ComboKeyCell] 那两格之所以定宽就是为了防这件事）。
 *
 *  为什么连分隔符的宽度也一起带上：居中要算「这一块占多宽」，而 `×` 两边各 6dp 的内边距
 *  是画在 `Text` 上的，`Row` 自己不知道。把它算进预算里，块宽才准，居中的左右两段才真的相等。
 */
internal class ComboPairWidths(
    /** 第一列（第一个键名，**带方括号**）—— 整列最长的那一个。 */
    val first: Dp,
    /** 第二列（第二个键名）—— 整列最长的那一个。 */
    val second: Dp,
    /** 分隔符那一格的总宽（`×` 的宽度 + 两侧内边距）。 */
    val separator: Dp,
)
