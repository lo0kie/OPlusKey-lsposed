package com.lookie.opluskey.ui.screen.combo

import androidx.compose.ui.unit.Dp

/**
 * 组合行标题里那枚框的宽度 —— **整列量一次**，五行十行都用同一个数。
 *
 *  为什么不能每行各量各的：框宽一旦逐行浮动，一列下来每行的框边缘都错开
 *  （`NameChip` 那几格之所以定宽就是为了防这件事）。
 *
 *  **只留一个数**（原来按位置分成几列）：名字框现在会**换行**（见 `ChipFlow`），
 *  一行里的第几枚不再等于「第几颗键」，按列分宽度没有意义了；取全部组合里最长的那个键名，
 *  所有框一样宽、左边仍然竖着对齐。
 */
internal class ComboTitleWidths(
    /** 每枚框的宽度（整列里最长那个键名 + 左右内边距 + 一点余量）。 */
    val chip: Dp,
)
