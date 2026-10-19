package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt

// 汉字墨迹在行盒里**偏高**的那一档修正 —— 全项目只有这一处算法，各页不再各存一个魔法数。
//
//  行盒（line box）的高度是 `ascent + descent`，而汉字墨迹基本只占 baseline **以上**那一段：
//  基线下面那截 descent 是留给西文降部（g / p / y）的，中文用不上。于是 `Alignment.Center`
//  居中的是**行盒**、不是墨迹 —— 墨迹永远比几何中心高一截，反过来就看成「文字偏上」。
//
// ## 比例跟着**字重**走（两次真机实测，不是估的）
//
//   · Medium 一档 —— 提示条 14sp（`couiTextAppearanceButton`，见 `PageSnackbar`）：
//     胶囊 41.14dp / 墨迹质心偏上 **4.8px = 1.37dp = 0.098em**；
//   · Regular 一档 —— 种类标签 11sp（`footnote2`，见 `AppKindTag`）：
//     胶囊 15.14dp / 墨迹偏上 **1.3px = 0.37dp = 0.034em**。
//
//  差了三倍：中文字体按字重**换的是另一个字体文件**，两个文件 hhea 里的 descent 本来就不是一个值。
//  所以不能拿一条 0.1em 通吃 —— 那样 Regular 的小字会被多沉近 1dp
//  （真机反馈「tag里的文字偏下了」，量出来正是多沉了 2.5px = 0.71dp）。
//
//  注意区分**两件不同的事**：
//   · 上面说的「字重」= Regular / Medium 这种**不同字体文件**，descent 不一样，要分档；
//   · 系统的「字体粗细」（ColorOS 那根滑杆 / 可变字体的 wght 轴）**不改** ascent + descent：
//     实测调到更细一档之后，墨迹像素少了 26%、提示条胶囊高度还是 144px、墨迹质心仍落在
//     「原位置 + 0.1em」上（预测 2531.6，量到 2531.7）。所以这一支不用跟着系统字重变。
//
// ## 只该用在这几种地方（其余地方动了就是错）
//
//   · 文字在**有可见边界**的容器里居中（胶囊 / 标签 / 表格单元格 / 网格格）；
//   · 文字与**图标同一行**、且是 `CenterVertically`（图标的视觉中心 = 盒子中心，文字的墨迹
//     却不在盒子中心，两者差的就是这一档）。
//
// ## 不该用的地方
//
//   · 正常文档流里的段落 / 多行文字 —— 眼睛读的是基线，盒子偏不偏看不出来，挪了反而破坏行距；
//   · 与另一行文字**并排**出现的（如「选择应用」列表里的大标题与右边那几枚小标签）——
//     两边都要调、各按自己的字重与字号算，只挪一边会把那一对拆开。
//
//  实现用 `offset { … }` 而不是 `padding`：只改绘制位置，不改测量 —— 容器高度、行距、
//  换行全部不受影响（用 `padding` 会把这些一起改掉，胶囊还会被撑胖）。

/** Medium 及以上那一档的下沉比例（实测 14sp / 1.37dp → 0.098em，取 0.1）。 */
private const val TEXT_OPTICAL_EM_MEDIUM = 0.1f

/** Regular 及以下那一档（实测 11sp / 0.37dp → 0.034em，取 0.035）。 */
private const val TEXT_OPTICAL_EM_REGULAR = 0.035f

/**
 * 把**竖居中**的文字往下压半档，让它落在容器的视觉中心（成因与两档实测见文件顶部）。
 *
 * @param fontSize 这段文字的实际字号 —— 传它自己那份 `TextStyle.fontSize`（不要传容器的、
 *   也不要传估算值：这一档的量正比于字号）
 * @param fontWeight 这段文字的实际字重 —— **别省**：Regular 与 Medium 是两个字体文件，
 *   descent 差三倍（见文件顶部）。调用点自己覆盖了字重的话（`fontWeight = FontWeight.Medium`），
 *   要按覆盖后的传
 */
internal fun Modifier.opticalTextCenter(
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
): Modifier {
    val ratio =
        if (fontWeight >= FontWeight.Medium) TEXT_OPTICAL_EM_MEDIUM else TEXT_OPTICAL_EM_REGULAR
    return offset { IntOffset(x = 0, y = (fontSize.toPx() * ratio).roundToInt()) }
}

/** 同上，直接吃一份 `TextStyle`（字重没被调用点改过的那些地方用这个，省得拆成两半）。 */
internal fun Modifier.opticalTextCenter(style: TextStyle): Modifier =
    //  `TextStyle.fontWeight` 是可空的（没覆盖就是 null = 走默认 Regular）
    opticalTextCenter(fontSize = style.fontSize, fontWeight = style.fontWeight ?: FontWeight.Normal)
