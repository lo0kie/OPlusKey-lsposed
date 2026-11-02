package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.Color

/**
 * 「随机图标配色」用的那一套色 —— **手挑的色相**，不是随机 RGB。
 *
 * ## 为什么不是 `Random.nextInt(0xFFFFFF)`
 *
 *  真随机在色相环上是均匀的，于是必然抽到大量「黄绿」「土黄」「灰紫」这类在浅色底上发脏、
 *  在深色底上发闷的颜色；两颗相邻的图标还会撞成几乎同色。随机配色要好看，**随机的是「用哪一个」，
 *  不是「什么颜色」** —— 颜色本身得先挑好。
 *
 * ## 挑法
 *
 *  · 色相：11 档，绕开 60°~120° 那一段（黄 / 黄绿在浅色底上最容易脏），其余按「冷色多、暖色少」排；
 *  · 饱和度 / 亮度：浅色底取 (0.58, 0.46) —— 够艳但不刺眼；深色底整体提亮到 (0.62, 0.66)，
 *    否则整片糊在深色背景里（对比度不够，看着像「没上色」）。
 *
 * ## 怎么保证「一次生成、永久一样」
 *
 *  颜色 = f(种子, 图标身份)。种子存在配置里（`GlobalConfig.iconTintSeed`），图标身份取
 *  `ImageVector.name` —— 同一颗图标每次算出来都一样，与开关、顺序、重组次数都无关。
 *  换一批只发生在「关掉再打开」那一次（那时会重新生成种子）。
 */
internal object IconTint {
    /** 挑好的色相（度）。刻意避开 60~120 的黄绿段。 */
    private val HUES = intArrayOf(212, 246, 268, 292, 322, 344, 14, 32, 172, 192, 142)

    private const val LIGHT_SATURATION = 0.58f
    private const val LIGHT_LIGHTNESS = 0.46f
    private const val DARK_SATURATION = 0.62f
    private const val DARK_LIGHTNESS = 0.66f

    /**
     * 这颗图标该用哪一个色相 —— `seed * 31` 与图标名的散列混合，再取模。
     *
     *  乘 31 是为了让相邻的种子把整个色环错开（直接取模的话，种子 +1 只会让所有图标
     *  一起挪一格，看着像「换了一套整体配色」而不是「重新洗过」）。
     */
    fun colorOf(
        seed: Int,
        key: String,
        dark: Boolean,
    ): Color =
        Color.hsl(
            HUES[Math.floorMod(seed * 31 + key.hashCode(), HUES.size)].toFloat(),
            if (dark) DARK_SATURATION else LIGHT_SATURATION,
            if (dark) DARK_LIGHTNESS else LIGHT_LIGHTNESS,
        )
}
