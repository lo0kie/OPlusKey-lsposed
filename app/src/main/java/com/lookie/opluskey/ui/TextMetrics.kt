package com.lookie.opluskey.ui

// 文本按**格**算长度、按**码点**截断的几件小事 —— 弹窗正文与菜单标题共用同一套口径，不各写一份。
//
//  「格」是等宽视角下的半角单位：一个汉字（或全角标点）两格，一个拉丁字母 / 数字一格。
//  界面上的两处判据（正文算不算「短到能居中」、菜单标题要不要截断）都按格数说规格，
//  而不是按 `String.length` —— 后者把「中文」算两个字符宽得多、"English" 反而算七个。

/**
 * 这个**码点**占两格（全角）。
 *
 *  只取 Unicode 里中日韩与全角标点那几段，外加**补充平面**（emoji 等）：「汉字 / 全角标点
 *  vs 其余」这个粗判不追求覆盖所有宽字符（真漏了，结果也只是把那一格算窄，不影响可用性）。
 *
 *  **按码点判、不按 `Char`**：emoji 是代理对（两个 `Char`），按 `Char` 数会把一个 emoji
 *  算成一格 —— 与「全角占两格」的直觉相反，截断时也会算出比实际更宽的结果。
 */
internal fun isFullWidth(codePoint: Int): Boolean =
    when (codePoint) {
        // 谚文字母
        in 0x1100..0x115F -> true

        // 中日韩部首、假名、汉字、注音
        in 0x2E80..0xA4CF -> true

        // 谚文音节
        in 0xAC00..0xD7A3 -> true

        // 中日韩兼容汉字
        in 0xF900..0xFAFF -> true

        // 中日韩兼容形式
        in 0xFE30..0xFE6F -> true

        // 全角形式
        in 0xFF00..0xFF60 -> true

        // 全角符号
        in 0xFFE0..0xFFE6 -> true

        // 补充平面（emoji 与各类符号）—— 表宽两格
        else -> codePoint > 0xFFFF
    }

/** 把一串文本折成**格数**（全角两格、其余一格）。 */
internal fun displayWidth(text: String): Int {
    var width = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        width += if (isFullWidth(cp)) 2 else 1
        i += Character.charCount(cp)
    }
    return width
}

/**
 * 把文本砍到 [maxColumns] 格以内，末尾补一个省略号；没超就原样返回。
 *
 *  只改**画出来的那一份**：日志与配置里仍是全文（否则截断后的名字在日志里认不出是哪一条）。
 *  按**码点**前进、只在码点边界上记可截位置 —— 按 `Char` 数再 `take(chars)` 会把代理对
 *  （emoji）从中间切开，留下半个代理字符（画出来是乱码）。
 */
internal fun ellipsizeToColumns(
    text: String,
    maxColumns: Int,
): String {
    if (displayWidth(text) <= maxColumns) return text
    var width = 0
    var cut = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val step = Character.charCount(cp)
        val next = width + if (isFullWidth(cp)) 2 else 1
        if (next > maxColumns) break
        width = next
        i += step
        cut = i
    }
    return text.take(cut) + "…"
}

/**
 * 按**码点**截断到 [max] 个码点以内 —— `take()` 数的是 UTF-16 单元，会切开代理对、留下半个字符。
 *
 *  切点落在码点边界上：解析器与渲染拿到的都是完整字符。
 */
internal fun String.truncateToCodePoints(max: Int): String =
    if (max <= 0) {
        ""
    } else {
        substring(0, offsetByCodePoints(0, minOf(max, codePointCount(0, length))))
    }
