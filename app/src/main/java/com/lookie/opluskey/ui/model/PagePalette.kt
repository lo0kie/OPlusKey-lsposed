package com.lookie.opluskey.ui.model

import android.content.Context
import android.os.Build
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.HostApp
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
     * **默认** —— 跟随系统取色：种子取系统当前那套强调色（取法见 [systemAccent]），
     * 也就是「壁纸取色」那一族的主色。用户换了壁纸 / 换了系统配色，这一档跟着变。
     *
     *  取的是**一个强调色**、不是 Material3 的整套 dynamic scheme：整套色板仍由 [colorsOf]
     *  按种子推（Monet 那一档的底子才吃系统那套角色映射）。
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
 *  · 其余主题档：底子是 COUI 的 `LIGHT_SCHEME` / `DARK_SCHEME`。
 *
 * ## 强调色一律由「强调色」那一行说了算
 *
 *  主色 / 主色变体 / 主色容器 / 三档禁用主色 / 三级色容器 / 滑块关键点前景 —— 就这些被盖上去
 *  （见 [withAccent]），**两档底子都盖**。底子自己算出来的主色不算数：Monet 那套是按
 *  它自己的种子推的，用户选了「蓝色」却在页面上看到系统那个色，就是这一处漏盖的结果
 *  （选「默认」时种子本来就是系统强调色，两者同源，盖上去等于没动）。
 */
@Composable
internal fun colorsOf(
    night: Boolean,
    palette: PagePalette,
    theme: AppTheme,
    context: Context,
): Colors {
    val seed = palette.seed(context)
    //  **只有主题选了 Monet** 底子才整套走系统取色：默认档 = 系统那套原样（含系统自己选的
    //  调色风格），选了别的色 = 按那个种子重新生成整套（换强调色时底子也跟着换）。
    //  其余主题档一律是 COUI 的底子 —— 底色不跟着染，只有强调色那一族被换掉。
    val base =
        when {
            theme != AppTheme.MONET -> {
                if (night) DARK_SCHEME else LIGHT_SCHEME
            }

            palette == PagePalette.DEFAULT -> {
                platformDynamicColors(dark = night)
            }

            else -> {
                ThemeController(
                    colorSchemeMode = if (night) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                    keyColor = seed,
                ).currentColors()
            }
        }
    val colors = base.withAccent(seed, night)
    logSchemeOnce(theme, palette, night, colors)
    return colors
}

/** 上一次打出去的那一行（见 [logSchemeOnce]）—— `null` = 还没打过。 */
private var lastSchemeLog: String? = null

/**
 * 「这一页现在按哪一档画」—— 一个组合值变一次才打一行 W。
 *
 *  排查「选了 X 却看着像 Y」时的第一条判据：主题 / 强调色 / 深浅有没有真的传到这一层、
 *  最终主色算出来是多少。每次重组都打会把这个信息埋掉，所以只在**组合变了**的时候打。
 */
private fun logSchemeOnce(
    theme: AppTheme,
    palette: PagePalette,
    night: Boolean,
    colors: Colors,
) {
    val line =
        "页面配色：主题=${theme.code} 强调色=${palette.id} 深色=$night" +
            " 主色=#${"%06X".format(colors.primary.toArgb() and 0xFFFFFF)}"
    if (line == lastSchemeLog) return
    lastSchemeLog = line
    XLog.w(line)
}

/** 默认档那个蓝 —— 也是「系统取色」取不到时的兜底。 */
private val PALETTE_DEFAULT_SEED = Color(0xFF0066FF)

/**
 * 系统强调色 —— 「默认」那一档的种子，也是 [AppTheme.MONET] 底子的种子。
 *
 *  按顺序取第一个能拿到的：
 *
 *  1. **框架动态色** `android.R.color.system_accent1_500`（Android 12+ 的壁纸取色）——
 *     与现代 Android 上「系统强调色」的定义一致；
 *  2. **宿主主题主色**（`couiColorPrimary`，宿主自己的 COUI 主题属性）—— 有些 ROM 不做上面
 *     那套动态色，但会把自己主题里的主色跟着系统配色换（`docs/device-findings.md` 里那节
 *     「改为用宿主主题属性取色」就是这条路拿到的「与系统原页面相同」的色）；
 *  3. 常量蓝 [PALETTE_DEFAULT_SEED]。
 *
 *  三级都要有：ROM 可能没有那组资源、可能没有那个属性、也可能压根不做动态取色 ——
 *  取不到就往下一档退，配色这一档不该让整页读不出来。
 *
 *  取到哪一档、色值是多少，打一行 W（一个进程只打一次）：用户报「系统那个色和某个预设一样」
 *  时，先看这一行到底是取了哪个源，还是三级全落空退到了常量蓝。
 */
private fun systemAccent(context: Context): Color {
    frameworkAccent(context)?.let { return it }
    hostThemeAccent(context)?.let { return it }
    logAccentOnce("三级都取不到，退到常量蓝", PALETTE_DEFAULT_SEED)
    return PALETTE_DEFAULT_SEED
}

/** 第 1 级：框架的动态色资源；Android 12 以下没有这组资源。 */
private fun frameworkAccent(context: Context): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val color =
        runCatching { Color(context.getColor(android.R.color.system_accent1_500)) }
            .getOrElse {
                XLog.d("框架动态色取不到：${it.javaClass.simpleName}: ${it.message}")
                return null
            }
    logAccentOnce("框架动态色 system_accent1_500", color)
    return color
}

/**
 * 第 2 级：宿主主题里的主色。
 *
 *  属性**按名字查**（不是写死 id）：宿主资源表随 ROM 重建，id 会漂，名字不会。
 *  两个候选都试：COUI 的 `couiColorPrimary` 是宿主页面自己在用的那一档；
 *  `colorAccent` 是 appcompat 那套的通用位（COUI 主题没定义时还有它）。
 */
private fun hostThemeAccent(context: Context): Color? {
    val theme = context.theme ?: return null
    //  写成 `firstNotNullOfOrNull` 而不是带 `continue` 的循环：后者在 detekt 里算「太多跳转」
    val hit =
        HOST_ACCENT_ATTRS.firstNotNullOfOrNull { name ->
            hostAttrColor(context, theme, name)?.let { name to it }
        }
    hit?.let { (name, color) -> logAccentOnce("宿主主题 $name", color) }
    return hit?.second
}

/**
 * 读宿主主题上某一个属性，**只认字面颜色**（指向 drawable / dimen 的属性在这一档里没有意义）。
 *
 *  属性 id **按名字反查**：宿主资源表随 ROM 重建，数值会漂，名字不会。
 */
private fun hostAttrColor(
    context: Context,
    theme: android.content.res.Resources.Theme,
    name: String,
): Color? {
    val id =
        runCatching { context.resources.getIdentifier(name, "attr", HostApp.PACKAGE) }
            .getOrDefault(0)
    if (id == 0) return null
    val value = TypedValue()
    if (!runCatching { theme.resolveAttribute(id, value, true) }.getOrDefault(false)) return null
    if (value.type !in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) return null
    return Color(value.data)
}

/** 宿主主题里可能放着「主色」的那几个属性 —— 顺序即优先级。 */
private val HOST_ACCENT_ATTRS = listOf("couiColorPrimary", "colorAccent")

/** 一个进程只打一次：这条是「系统强调色取自哪里」的判据，每次重组都打会刷屏。 */
private var accentLogged = false

private fun logAccentOnce(
    source: String,
    color: Color,
) {
    if (accentLogged) return
    accentLogged = true
    XLog.w("系统强调色取自$source：#${"%06X".format(color.toArgb() and 0xFFFFFF)}")
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
