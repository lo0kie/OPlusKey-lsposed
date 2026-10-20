package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lookie.opluskey.core.model.GlobalConfig
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme

// 「系统配色」—— 页面强调色的几套预设，以及由强调色推出整套色板的那一个函数。
//
//  清单**只在界面层**：`:core` 只存一个 id 字符串（`GlobalConfig.palette`），不认识有哪几套
//  （共用层没有 `:app` 的 classpath，也不该为配色多一份枚举）。

/**
 * 一套配色 —— 界面上一个色点，配置里一个 [id]。
 *
 *  [seed] 就是这一套的**强调色**（主色），其余颜色按它推（见 [colorsOf]）。
 *  顺序即色点顺序：第一档必须是默认档（用户最先看到的就是「原来那个蓝」）。
 */
internal enum class PagePalette(
    val id: String,
    val seed: Color,
) {
    /** COUI 自己那套蓝 —— 与 `lightColorScheme()` / `darkColorScheme()` 的 `primary` 是同一个值。 */
    DEFAULT(GlobalConfig.DEFAULT_PALETTE, Color(0xFF0066FF)),
    GREEN("green", Color(0xFF0C9A5B)),
    ORANGE("orange", Color(0xFFE0700B)),
    PURPLE("purple", Color(0xFF7C4DFF)),
    PINK("pink", Color(0xFFD4407E)),
    ;

    companion object {
        /**
         * 按 id 找色板；认不出一律落回默认档。
         *
         *  **不抛异常、不报错**：id 存在配置文件里，人手改过、或者从更新版本导回来都可能带进
         *  不认识的档 —— 那时最合理的结果是「页面还是原来那个样子」，而不是整页读不出配色。
         */
        fun ofId(id: String?): PagePalette = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 某个配色在某个深浅下对应的整套色板（`COUITheme(colors = …)` 的入参）。
 *
 * ## 只换「强调色那一族」
 *
 *  主色 / 主色变体 / 主色容器 / 三档禁用主色 / 三级色容器 / 滑块关键点前景 —— 就这些。
 *  背景、表面、文本、分隔线、错误色**一律不动**：它们与强调色无关，跟着换等于整页换皮
 *  （深浅两套的 `surface` 是页面底色的来源，动了它连宿主那条状态栏配色都要跟着重算）。
 *
 * ## 默认档原样返回库色板
 *
 *  「没选过」与「选了默认档」必须是同一个像素结果，所以这一档**不经过任何推导**、直接返回
 *  `LIGHT_SCHEME` / `DARK_SCHEME`。其它档是推导出来的，色值与原色板的对应关系按「同一个明暗
 *  关系」贴着抄（注释里的原值就是被抄的那一个）。
 */
internal fun colorsOf(
    night: Boolean,
    palette: PagePalette,
): Colors {
    if (palette == PagePalette.DEFAULT) return if (night) DARK_SCHEME else LIGHT_SCHEME
    return if (night) darkWith(palette.seed) else lightWith(palette.seed)
}

/**
 * 浅色档：种子色直接当主色。
 *
 * 括号里的原值是默认档（默认蓝）在同一位置上的值 —— 提亮比例按它反推，换色后明暗关系不走形。
 */
private fun lightWith(seed: Color): Colors =
    lightColorScheme(
        primary = seed,
        primaryVariant = seed,
        primaryContainer = seed,
        // 0x4D ≈ 30% —— 原 `disabledPrimary` 就是 `0x4D0066FF`
        disabledPrimary = seed.copy(alpha = PALETTE_DISABLED_ALPHA),
        disabledPrimaryButton = seed.copy(alpha = PALETTE_DISABLED_ALPHA),
        // 原值是中性灰 `#EBEBEB`（很淡的一格）：换色后保持「淡到只剩个形」，不跟着染成彩色
        disabledPrimarySlider = seed.copy(alpha = PALETTE_DIMMEST_ALPHA),
        // 原 `#AECDFF` ≈ 主色朝白 0.68
        onPrimaryVariant = seed.paletteLift(0.68f),
        // 0x26 ≈ 15% —— 原 `tertiaryContainer` 就是 `0x260066FF`
        tertiaryContainer = seed.copy(alpha = PALETTE_TERTIARY_ALPHA),
        onTertiaryContainer = seed,
        // 原 `#6EB5FF` ≈ 主色朝白 0.45
        sliderKeyPointForeground = seed.paletteLift(0.45f),
    )

/**
 * 深色档：主色要比浅色档**亮一档**（原 `#247CFF` 之于 `#0066FF` 就是朝白 0.14），
 * 主色容器再亮一档（原 `#5C9DFF` ≈ 深色主色朝白 0.26）。
 */
private fun darkWith(seed: Color): Colors {
    val main = seed.paletteLift(PALETTE_DARK_LIFT)
    val container = main.paletteLift(PALETTE_CONTAINER_LIFT)
    return darkColorScheme(
        primary = main,
        primaryVariant = main,
        primaryContainer = container,
        disabledPrimary = main.copy(alpha = PALETTE_DISABLED_ALPHA),
        disabledPrimaryButton = main.copy(alpha = PALETTE_DISABLED_ALPHA),
        // 深色档的 `disabledPrimarySlider` 原值是中性 `#262626`，不覆盖
        // 原 `#99C7F1` ≈ 深色主色朝白 0.60
        onPrimaryVariant = main.paletteLift(0.60f),
        // 原 `0x40247CFF` ≈ 25%
        tertiaryContainer = main.copy(alpha = PALETTE_TERTIARY_ALPHA_DARK),
        onTertiaryContainer = container,
        // 原 `#5DAAFF` ≈ 深色主色朝白 0.26
        sliderKeyPointForeground = container,
    )
}

/** 启用但不可点的那一格 —— 与库里 `0x4D` 同一个透明度。 */
private const val PALETTE_DISABLED_ALPHA = 0.30f

/** 「淡到只剩个形」那一格：滑块处于禁用时的底色（原值 `#EBEBEB` 是个 12% 的灰）。 */
private const val PALETTE_DIMMEST_ALPHA = 0.12f

/** 三级色容器（浅色档）：原值 `0x26xxxxxx` ≈ 15%。 */
private const val PALETTE_TERTIARY_ALPHA = 0.15f

/** 三级色容器（深色档）：原值 `0x40xxxxxx` ≈ 25%。 */
private const val PALETTE_TERTIARY_ALPHA_DARK = 0.25f

/** 深色档主色相对种子色提亮的比例（`#0066FF` → `#247CFF` 反推出来的）。 */
private const val PALETTE_DARK_LIFT = 0.14f

/** 主色容器相对深色主色再提亮的比例（`#247CFF` → `#5C9DFF`）。 */
private const val PALETTE_CONTAINER_LIFT = 0.26f

/** 朝白色提亮 [f]（0 = 原色，1 = 纯白）。 */
private fun Color.paletteLift(f: Float): Color = lerp(this, Color.White, f)
