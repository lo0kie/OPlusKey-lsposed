package com.lookie.opluskey.ui.component

/**
 * 文本按**格**算长度的两件小事 —— 弹窗正文与菜单标题共用同一套口径，不各写一份。
 *
 *  「格」是等宽视角下的半角单位：一个汉字（或全角标点）两格，一个拉丁字母 / 数字一格。
 *  界面上的两处判据（正文算不算「短到能居中」、菜单标题要不要截断）都按格数说规格，
 *  而不是按 `String.length` —— 后者把「中文」算两个字符宽得多、"English" 反而算七个。
 */

/**
 * 这个字符占两格（全角）。
 *
 *  只取 Unicode 里中日韩与全角标点那几段：这两处要的只是「汉字 / 全角标点 vs 其余」这个粗判，
 *  不追求覆盖所有宽字符（真漏了，结果也只是把那一格算窄，不影响可用性）。
 */
internal fun isFullWidth(c: Char): Boolean = when (c.code) {
    in 0x1100..0x115F -> true // 谚文字母
    in 0x2E80..0xA4CF -> true // 中日韩部首、假名、汉字、注音
    in 0xAC00..0xD7A3 -> true // 谚文音节
    in 0xF900..0xFAFF -> true // 中日韩兼容汉字
    in 0xFE30..0xFE6F -> true // 中日韩兼容形式
    in 0xFF00..0xFF60 -> true // 全角形式
    in 0xFFE0..0xFFE6 -> true
    else -> false
}

/** 把一串文本折成**格数**（全角两格、其余一格）。 */
internal fun displayWidth(text: String): Int {
    var width = 0
    for (c in text) width += if (isFullWidth(c)) 2 else 1
    return width
}
