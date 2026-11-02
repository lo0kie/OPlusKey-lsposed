package com.lookie.opluskey.ui.model

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lookie.opluskey.core.model.GlobalConfig
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.platformDynamicColors

// 「系统配色」—— 页面强调色的几套预设，以及由强调色推出整套色板的那一个函数。
//
//  清单**只在界面层**：`:core` 只存一个 id 字符串（`GlobalConfig.palette`），不认识有哪几套
//  （共用层没有 `:app` 的 classpath，也不该为配色多一份枚举）。

/**
 * 一套配色 —— 界面上一个色点，配置里一个 [id]。
 *
 *  [seed] 给出这一套的**强调色**（主色），其余颜色按它推（见 [colorsOf]）。
 *  顺序即色点顺序：第一档是默认档（认不出的 id 也落回它）。
 */
internal enum class PagePalette(
    val id: String,
    /** 常量种子色；`null` = 这一档的种子要现取（见 [seed]）。 */
    private val fixedSeed: Color?,
) {
    /**
     * **默认** —— 跟随系统取色：种子取系统的强调色（`android.R.color.system_accent1_500`），
     * 也就是「壁纸取色」那一族的主色。用户换了壁纸 / 换了系统配色，这一档跟着变。
     *
     *  取的是**资源里的色值**、不是 Material3 的整套 dynamic scheme：整套色板仍由 [colorsOf] 按种子推。
     *  Android 12 以下没有这组资源，[systemAccent] 落回 COUI 那个蓝。
     *
     *  这是第一档（菜单里排最上）：没选过、或者配置里是认不出的 id 都落回它。
     */
    DEFAULT(GlobalConfig.DEFAULT_PALETTE, null),

    /** COUI 自己那套蓝 —— 与 `lightColorScheme()` / `darkColorScheme()` 的 `primary` 是同一个值。 */
    BLUE("blue", PALETTE_DEFAULT_SEED),
    GREEN("green", Color(0xFF0C9A5B)),
    ORANGE("orange", Color(0xFFE0700B)),
    PURPLE("purple", Color(0xFF7C4DFF)),
    PINK("pink", Color(0xFFD4407E)),
    ;

    /**
     * 这一套的强调色。
     *
     * @param context 只有 [DEFAULT] 用它（其余档是常量，取不取都一样）
     */
    fun seed(context: Context): Color = fixedSeed ?: systemAccent(context)

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
 * 某一档配色 + 某一档主题在某个深浅下对应的整套色板（`COUITheme(colors = …)` 的入参）。
 *
 * ## 底子分两种
 *
 *  · **主题 = 跟随系统 (Monet)**：底子取系统那套 MD3 角色映射（`platformDynamicColors`）——
 *    底色、卡片、文本、分隔线全都跟着系统的壁纸取色走（用户口径：「Moment 配色要把应用底色 /
 *    cardlist 的背景色都换了」）。
 *  · 其余主题档：底子是 COUI 的 `LIGHT_SCHEME` / `DARK_SCHEME`，只有强调色那一族被换掉。
 *
 * ## 强调色一律由「强调色」那一行说了算
 *
 *  主色 / 主色变体 / 主色容器 / 三档禁用主色 / 三级色容器 / 滑块关键点前景 —— 就这些被盖上去
 *  （见 [withAccent]）。选中「默认」时种子就是系统强调色，与 Monet 的底子同源；选了蓝 / 绿 / …
 *  则是在底子上换一个点缀。
 */
@Composable
internal fun colorsOf(
    night: Boolean,
    palette: PagePalette,
    theme: AppTheme,
    context: Context,
): Colors {
    val seed = palette.seed(context)
    //  **只有主题选了 Monet** 才整套走系统取色：默认档 = 系统那套原样（含系统自己选的调色风格），
    //  选了别的色 = 按那个种子重新生成整套（换强调色时底子也跟着换）。
    //  其余主题档一律是 COUI 的底子 —— 强调色只换那一族，不会把底色也染成 Monet。
    if (theme == AppTheme.MONET) {
        return if (palette == PagePalette.DEFAULT) {
            platformDynamicColors(dark = night)
        } else {
            ThemeController(
                colorSchemeMode = if (night) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                keyColor = seed,
            ).currentColors()
        }
    }
    val base = if (night) DARK_SCHEME else LIGHT_SCHEME
    return base.withAccent(seed, night)
}

/** 默认档那个蓝 —— 也是「系统取色」取不到时的兜底。 */
private val PALETTE_DEFAULT_SEED = Color(0xFF0066FF)

/**
 * 系统强调色（Android 12 起由壁纸取色生成的那一族）。
 *
 *  取 `system_accent1_500`：它是那套调色板的**主色档**，与页面强调色的角色一一对应。
 *  取不到（Android 12 以下、或资源被裁掉的 ROM）就落回默认蓝 —— 配色这一档不该因为取不到色
 *  而让整页读不出来。
 */
private fun systemAccent(context: Context): Color {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return PALETTE_DEFAULT_SEED
    return runCatching { Color(context.getColor(android.R.color.system_accent1_500)) }
        .getOrDefault(PALETTE_DEFAULT_SEED)
}

/**
 * 把**强调色那一族**盖到任意底子上（底子可能是 COUI 的色板，也可能是 Monet 那套）。
 *
 *  浅色档：种子色直接当主色；深色档：主色比种子色亮一档、容器再亮一档。
 *  括号里的原值是默认档（默认蓝）在同一位置上的值 —— 提亮比例按它反推，换色后明暗关系不走形。
 */
private fun Colors.withAccent(
    seed: Color,
    night: Boolean,
): Colors {
    if (!night) {
        return copy(
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
    }
    val main = seed.paletteLift(PALETTE_DARK_LIFT)
    val container = main.paletteLift(PALETTE_CONTAINER_LIFT)
    return copy(
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
