package com.lookie.opluskey.ui

import android.content.res.Configuration
import android.view.ViewGroup
import com.lookie.opluskey.core.log.XLog

/**
 * 宿主窗口的读取与包装：从宿主 Activity 取换页用的容器、状态栏高度、深浅与页面 Context。
 *
 * 用到的两个 `R.id.*` 数值属于宿主 APK 的资源表（跨包资源 id 不成立），只能写死 + 按名字反查兜底；
 * 同一 ROM 内稳定，ROM 升级重建资源表时可能变。
 */

/**
 * `R.id.container` 的具体数值 —— 同一 ROM 内稳定（就是 APK 资源表索引），
 * ROM 升级重建资源表时可能变，所以 [findContainer] 留了按名字兜底的路径。
 */
internal const val ID_CONTAINER = 0x7f0900e9

/**
 * `R.id.appbar_layout` 的数值（顶部栏，含 `COUIToolbar`）。
 *
 * `container` 占满整屏、顶部栏叠在它上面，所以模块页面需要按它的高度避让。
 */
internal const val ID_APPBAR = 0x7f09008d

/** 兜底：按资源名反查 id（`getIdentifier`），数值对不上时用。 */
internal const val NAME_CONTAINER = "container"

/** [ID_APPBAR] 的资源名（与 [NAME_CONTAINER] 同一套兜底）。 */
internal const val NAME_APPBAR = "appbar_layout"

/**
 * 拿宿主的顶栏（`R.id.appbar_layout`）：先按写死的数值、对不上再按名字反查。
 *
 * 注意：必须带名字兜底：`container` 那一路早就有（[NAME_CONTAINER]），
 * 顶栏这一路不能只有写死的数值 —— ROM 重建资源表之后 `findViewById` 会查不到 ⇒
 * 宿主顶栏藏不掉、压在模块页面上，而日志里只有一句「没找到」。与 `findContainer` 同一套口径。
 *
 * @return 顶栏 View；两条路都查不到时返回 null（调用方只影响「顶栏露着」，不崩）
 */
internal fun findAppbar(activity: android.app.Activity): android.view.View? = try {
    activity.findViewById<android.view.View>(ID_APPBAR) ?: run {
        val id = activity.resources.getIdentifier(NAME_APPBAR, "id", HOST_PACKAGE)
        if (id == 0) {
            XLog.w("按名字也查不到 id/$NAME_APPBAR（宿主资源表里没有）")
            return null
        }
        XLog.w(
            "写死的 appbar id 对不上，改用名字反查得到 0x${Integer.toHexString(id)}" +
                "（ROM 可能升级过资源表，建议更新 ID_APPBAR）",
        )
        activity.findViewById<android.view.View>(id)
    }
} catch (e: Throwable) {
    XLog.w("查宿主顶栏失败：${e.javaClass.simpleName}: ${e.message}")
    null
}

/**
 * 宿主主题的浅色变体 `AppNoTitleTheme`（宿主 App 自己的主题，parent 链
 * `AppTheme → AppBaseTheme → Theme.Demo → Theme.COUI.Blue`，浅色为默认，`AppBaseTheme` 的 `(night)` 变体交给系统强制深色）。
 *
 * 不能用 `Theme.COUI.Main.Light`：它 `windowActionBar=true`，宿主 `BaseActionActivity#f0()` 会
 * `setSupportActionBar(...)` ⇒ AppCompat 抛「Activity already has an action bar」；
 * `AppNoTitleTheme` 显式写了 `windowActionBar=false` + `windowNoTitle=true`，与宿主该 Activity 的窗口标志一致。
 */
internal const val THEME_LIGHT = "AppNoTitleTheme"

/** 宿主包名（`Resources.getIdentifier` 要用）。 */
internal const val HOST_PACKAGE = "com.oplus.gesture"

/** 当前是不是深色（night）模式。 */
internal fun isNight(context: android.content.Context): Boolean {
    // 首选：系统全局配置（最准）
    try {
        val sys = android.content.res.Resources.getSystem().configuration.uiMode
        return (sys and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    } catch (e: Throwable) {
        XLog.d("读系统全局 configuration 失败，退回 context 的值：${e.message}")
    }
    return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
}

/** 这个颜色是不是「浅色」（用于决定状态栏图标要深色还是浅色）。 */
internal fun isLightColor(color: Int): Boolean {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    // ITU-R BT.601 亮度，阈值取 0.5
    return (0.299 * r + 0.587 * g + 0.114 * b) > 127.5
}

/**
 * 状态栏高度：先读窗口 inset（最准，含挖孔 / 状态栏显隐），
 * 拿不到（`onCreate` 阶段还没下发 inset）就用系统 dimen 兜底。
 */
internal fun statusBarHeight(activity: android.app.Activity): Int {
    try {
        val insets = activity.window?.decorView?.rootWindowInsets
        val top = insets?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
        if (top > 0) return top
    } catch (e: Throwable) {
        XLog.d("读状态栏 inset 失败：${e.message}")
    }
    return try {
        val id = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id != 0) activity.resources.getDimensionPixelSize(id) else 0
    } catch (e: Throwable) {
        XLog.d("读 status_bar_height 失败：${e.message}")
        0
    }
}

/**
 * 软键盘现在是否露着 —— 判据用 IME 的 inset 可见性（`WindowInsets.Type.ime()`），
 * 不是 `InputMethodManager.isActive()`：后者只表示「当前有输入连接」，字段拿到焦点、键盘收起后照样是 true，
 * 拿它当判据会让「键盘明明关着、返回却被吃掉」。
 *
 * `rootWindowInsets` 在 `onCreate` 早期还没下发，那时读到 null ⇒ 返回 false（键盘本来也不可能开着）。
 */
internal fun isImeVisible(activity: android.app.Activity): Boolean = try {
    activity.window?.decorView?.rootWindowInsets
        ?.isVisible(android.view.WindowInsets.Type.ime()) == true
} catch (e: Throwable) {
    XLog.d("读 IME inset 失败（当作键盘没开）：${e.message}")
    false
}

/**
 * 收起软键盘 —— 走 `InputMethodManager.hideSoftInputFromWindow` 而不是
 * `WindowInsetsController.hide(ime())`：宿主 window 的 `insetsController` 由宿主自己配，
 * IME 那段不一定交给我们控制；IMM 按 window token 直接发命令，与谁配的无关。
 *
 * 不清焦点：字段保持焦点、只收起键盘，再点一下键盘就回来；清焦点会让光标消失。
 */
internal fun hideIme(activity: android.app.Activity) {
    try {
        val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager ?: return
        imm.hideSoftInputFromWindow(activity.window?.decorView?.windowToken, 0)
    } catch (e: Throwable) {
        XLog.d("收键盘失败（忽略）：${e.message}")
    }
}

/**
 * 页面用哪个 Context 建 —— 宿主 Activity 配置是 night、系统是浅色时，套一层
 * `ContextThemeWrapper` 强制浅色构建。
 *
 * DayNight 颜色资源（`couiColorLabelPrimary` / `couiColorCardBackground` 等）按配置走：直接建 View 会得到
 * 「白底 + 白字 + 深色卡片」的混合体。宿主的 Activity 上 `applyOverrideConfiguration` 已太晚（真机报过
 * `getResources() or getAssets() has already been called`），而新建的 wrapper 资源还没被访问过，可以改配置。
 */
internal fun pageContext(activity: android.app.Activity): android.content.Context {
    // 宿主 Activity 自己的配置：必须直接读它的 resources，不能走 [isNight] ——
    // 那个首选系统全局配置，两个调用恒等，「宿主 night、系统浅色」的分支永远进不去。
    val hostNight = try {
        (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    } catch (e: Throwable) {
        XLog.d("读宿主 Activity 配置失败：${e.message}")
        return activity
    }
    if (!hostNight || isNight(activity)) return activity

    return try {
        val id = activity.resources.getIdentifier(THEME_LIGHT, "style", HOST_PACKAGE)
        // `id == 0`（宿主没这个主题）时按默认主题构建：`ContextThemeWrapper(activity, 0)`
        // 就是「不换主题」。
        val wrapper = android.view.ContextThemeWrapper(activity, id)
        val cfg = Configuration(activity.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            Configuration.UI_MODE_NIGHT_NO
        wrapper.applyOverrideConfiguration(cfg)
        XLog.w("宿主 Activity 的配置是 night、系统是浅色 ⇒ 页面改用「强制浅色」的 Context 构建")
        wrapper
    } catch (e: Throwable) {
        XLog.w("强制浅色 Context 失败（页面会按夜间颜色渲染）", e)
        activity
    }
}

/**
 * 查 `container` 容器：先按写死的 id 数值查（同 ROM 内稳定、快），
 * 失配时用 `getIdentifier("container", "id", "com.oplus.gesture")` 反查兜底。
 */
internal fun findContainer(activity: android.app.Activity): ViewGroup? {
    (activity.findViewById<android.view.View>(ID_CONTAINER) as? ViewGroup)?.let { return it }

    val id = activity.resources.getIdentifier(NAME_CONTAINER, "id", HOST_PACKAGE)
    if (id == 0) {
        XLog.w("按名字也查不到 id/$NAME_CONTAINER（宿主资源表里没有）")
        return null
    }
    XLog.w(
        "写死的 container id 对不上，改用名字反查得到 0x${Integer.toHexString(id)}" +
            "（ROM 可能升级过资源表，建议更新 ID_CONTAINER）",
    )
    return activity.findViewById<android.view.View>(id) as? ViewGroup
}

/** 平台类的 `Activity#onBackPressed()`；平台上没有它时返回 null。 */
internal fun platformOnBackPressed(): java.lang.reflect.Method? = try {
    android.app.Activity::class.java.getDeclaredMethod("onBackPressed")
} catch (e: Throwable) {
    XLog.d("平台上没有 Activity#onBackPressed：${e.message}")
    null
}

/**
 * 宿主自己声明的 `onBackPressed()`；它没 override 时返回 null。
 *
 * 注意：**沿父类链找** —— 只查 `activity.javaClass.getDeclaredMethod` 一层的话，
 * 宿主把设置页实现挪到基类（`BaseSettingActivity` 之类）之后就找不到 ⇒ (3)' 那条拦截静默失效，
 * 只剩平台类那条 (3) 兜着，而两条路的「消费」语义并不等价。
 *
 * 失败行用 `w`：这是「返回拦截到底装上没装上」的判据之一（`d` 在 release 被编译期消除）。
 */
internal fun hostOnBackPressed(activity: android.app.Activity): java.lang.reflect.Method? {
    var cur: Class<*>? = activity.javaClass
    while (cur != null && cur != android.app.Activity::class.java) {
        val hit = runCatching { cur.getDeclaredMethod("onBackPressed") }.getOrNull()
        if (hit != null) {
            runCatching { hit.isAccessible = true }
            if (cur != activity.javaClass) {
                XLog.w("宿主 onBackPressed 声明在父类 ${cur.name}（${activity.javaClass.name} 自己没有）")
            }
            return hit
        }
        cur = cur.superclass
    }
    XLog.w("宿主没自己声明 onBackPressed（已沿父类链找到 Activity）：${activity.javaClass.name}")
    return null
}
