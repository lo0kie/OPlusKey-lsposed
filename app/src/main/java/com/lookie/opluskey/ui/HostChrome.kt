package com.lookie.opluskey.ui

import android.view.ViewGroup
import com.lookie.opluskey.core.log.XLog

/**
 * 宿主窗口 / 状态栏的绘制 —— 换页时把宿主的主题、窗口底色与状态栏那一条刷成我们需要的样式。
 *
 * 纪律（来自 [SettingsPageHooks.replaceContent] 的第一条）：**只改颜色、只加自己的 View，
 * 不动宿主的 View 结构**。宿主那棵 View 树一直活着，我们改的是可见性、加的是自己的东西 ——
 * 宿主可能持有引用，`remove` 等于埋雷。
 */
internal object HostChrome {

    /**
     * 状态栏那块实心遮罩（见 [coverStatusBar]）。
     *
     *  ** 起只在「露出宿主原版页面」那一档存在**（见 [SettingsPageHooks.revealStock] /
     * [removeStatusFiller]）：模块页面自己就是 edge-to-edge、那条早就是它画的，
     * 盖遮罩反而把它挡在模块控制之外。
     */
    private var statusFiller: android.view.View? = null

    /** 系统是浅色时，把宿主主题从写死的深色换成同族的浅色变体。 */
    internal fun applyHostTheme(activity: android.app.Activity) {
        //  `isNight` 已改用**系统全局配置**（见其注释）：宿主声明了 `configChanges=uiMode`，
        val night = isNight(activity)
        XLog.d("夜间状态（系统全局）：night=$night")

        //  转场动画**不在这里**关：`Window.setWindowAnimations()` 在 `mWindowAttributes`
        // 还是 null 时会**静默返回**，而这一步跑在 `setContentView` 之前 ⇒ 调了等于没调。
        // 真机 `dumpsys window` 实测过：窗口的 `wanim` 仍然是宿主主题的 `0x7f120024`。
        // 现在挪到 [SettingsPageHooks.onAfterCreate] 里 `chain.proceed()` 之后（见 [disableWindowTransition]）。
        // 别把它当修复看 —— 理由与数据都在 [disableWindowTransition] 的注释里。

        // 深色模式：宿主主题本来就写死深色（`Theme.COUI.Main.Dark`），**原样保留**。
        // 但 window 背景仍要按深色刷一遍 —— 理由见下（转场黑底）。
        if (night) {
            XLog.w("系统是深色模式，沿用宿主自带的深色主题")
            paintWindowBeforeContent(activity, 0xFF000000.toInt(), "深色页面底 #ff000000")
            return
        }

        //  这里**不**去改本 Activity 的资源配置：真机试过
        // `activity.applyOverrideConfiguration(...)`，抛
        // `IllegalStateException: getResources() or getAssets() has already been called`
        // —— 走到这个 hook 时宿主的资源早就被访问过了。
        // 改配置这件事挪到 [pageContext]（用一个资源还没被访问过的 ContextThemeWrapper）。

        // 把主题换成宿主自带的浅色无标题主题
        val id = try {
            activity.resources.getIdentifier(THEME_LIGHT, "style", HOST_PACKAGE)
        } catch (e: Throwable) {
            XLog.w("查 $THEME_LIGHT 失败（忽略）", e)
            0
        }
        if (id == 0) {
            XLog.w("宿主里没有 $THEME_LIGHT —— 保持宿主的深色主题（页面会是深色）")
            return
        }

        try {
            activity.setTheme(id)
            // 宿主主题里 windowNoTitle=true，换主题后补一次，免得冒出 ActionBar
            activity.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            XLog.w("系统是浅色模式，宿主主题已切到 $THEME_LIGHT")
        } catch (e: Throwable) {
            XLog.w("切浅色主题失败（保留宿主深色主题）", e)
        }

        paintWindowBeforeContent(activity, 0xFFF0F1F2.toInt(), "浅色页面底 #fff0f1f2")
    }

    /** 关掉本 Activity 的窗口转场动画 —— 试过，但对「从设置进来黑一下」实测无效。 */
    internal fun disableWindowTransition(activity: android.app.Activity) {
        try {
            val window = activity.window ?: return
            // 窗口自身动画置 0：不淡入不滑动，直接出现
            window.setWindowAnimations(0)
            // 不看读回值就分不清「设上了」和「又空转了一次」。
            val now = try {
                window.attributes.windowAnimations
            } catch (e: Throwable) {
                -1
            }
            XLog.w("窗口转场动画已置 0（读回 wanim=$now，0 = 生效）")
        } catch (e: Throwable) {
            XLog.w("关闭窗口转场动画失败（忽略）：${e.message}")
        }
    }

    /** 在 `setContentView` 之前把 window 背景刷成页面底色 —— 防「splash 撤掉到首帧之间露底跳色」。 */
    private fun paintWindowBeforeContent(
        activity: android.app.Activity,
        color: Int,
        desc: String,
    ) {
        try {
            activity.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
            XLog.d("window 背景已预刷为 $desc（防转场黑底）")
        } catch (e: Throwable) {
            XLog.d("预刷 window 背景失败（忽略）：${e.message}")
        }
    }

    /** 只刷状态栏那一条 —— 换页时 / 露出原版页面 / 切回模块界面 三处共用。 */
    internal fun paintStatusBarChrome(
        activity: android.app.Activity,
        container: ViewGroup,
        color: Int,
    ) {
        disableStatusBarScrim(activity)
        applyStatusBarArea(activity, container, color)
        setStatusBarIconAppearance(activity, isLightColor(color))
    }

    /**
     * 把「窗口的底」刷成页面底色 —— 防**首帧之前露底跳色**（**不是**那段黑的主因）。
     */
    internal fun paintWindowBackdrop(
        activity: android.app.Activity,
        container: ViewGroup,
        color: Int,
    ) {
        try {
            val opaque = color or 0xFF000000.toInt()
            activity.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(opaque))
            XLog.d("window 背景已刷成 #${Integer.toHexString(opaque)}（消除打开瞬间的黑底）")
        } catch (e: Throwable) {
            XLog.d("刷 window 背景失败（忽略）：${e.message}")
        }
        try {
            val opaque = color or 0xFF000000.toInt()
            activity.window?.decorView?.setBackgroundColor(opaque)
        } catch (e: Throwable) {
            XLog.d("刷 decorView 背景失败（忽略）：${e.message}")
        }
        try {
            val opaque = color or 0xFF000000.toInt()
            container.setBackgroundColor(opaque)
        } catch (e: Throwable) {
            XLog.d("刷 container 背景失败（忽略）：${e.message}")
        }
    }

    /**
     * 让状态栏区域也是页面底色（「沉浸式」）。
     */
    private fun applyStatusBarArea(
        activity: android.app.Activity,
        container: ViewGroup,
        color: Int,
    ) {
        try {
            container.setBackgroundColor(color)
        } catch (e: Throwable) {
            XLog.w("刷 container 背景失败（忽略）", e)
        }
        try {
            activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
        } catch (e: Throwable) {
            XLog.w("刷 window 背景失败（忽略）", e)
        }
        try {
            activity.window.decorView.setBackgroundColor(color)
        } catch (e: Throwable) {
            XLog.w("刷 decorView 背景失败（忽略）", e)
        }

        // 诊断：状态栏那块到底归谁管 —— 把关键坐标打出来（ROM 改版时对照用）
        try {
            val cLoc = IntArray(2)
            container.getLocationOnScreen(cLoc)
            val appbar = activity.findViewById<android.view.View>(ID_APPBAR)
            val aLoc = IntArray(2)
            appbar?.getLocationOnScreen(aLoc)
            XLog.d(
                "状态栏区域诊断：container top=${cLoc[1]} 高=${container.height}" +
                        "；appbar top=${aLoc[1]} 高=${appbar?.height}",
            )
        } catch (e: Throwable) {
            XLog.d("状态栏诊断日志失败（忽略）：${e.message}")
        }

        XLog.d("状态栏区域底色已刷成 #${Integer.toHexString(color)}（container + window + decorView 三层背景）")
    }

    /**
     * 关掉状态栏的「对比度薄纱」（contrast scrim）。
     */
    @Suppress("DEPRECATION")
    private fun disableStatusBarScrim(activity: android.app.Activity) {
        try {
            activity.window.setStatusBarContrastEnforced(false)
            XLog.w("已关闭状态栏对比度薄纱（setStatusBarContrastEnforced=false）")
        } catch (e: Throwable) {
            XLog.w("关闭状态栏薄纱失败（状态栏可能仍有一条色带）", e)
        }
    }

    /**
     * 在状态栏那块盖一层**实心 View** —— 现在**只给「露出宿主原版页面」那一档用**
     *（见 [SettingsPageHooks.revealStock]，配套 [removeStatusFiller] 在切回模块界面时拿掉）。
     */
    internal fun coverStatusBar(activity: android.app.Activity, color: Int) {
        val decor = activity.window?.decorView as? ViewGroup

        //  已经盖过、而且还挂在**当前这个** decorView 上 → 只改颜色，并且**重新放到最上层**。
        //
        // decorView 上塞自己的「状态栏保护」View —— 我们的遮罩会被压到它下面，
        // 于是「改颜色」改了个看不见的东西：切到深色后状态栏那条还是浅的
        // （真机反馈「状态栏区域没有切换颜色」）。`removeView` + `addView` 让它回到最后一层。
        //
        //  判定用 `parent === decor`：宿主重建 Activity 时 decorView 会换一个，
        // 旧遮罩留在死掉的窗口上，只改它的颜色同样看不见 —— 那种情况要**重新盖一个**。
        val existing = statusFiller
        if (existing != null && decor != null && existing.parent === decor) {
            try {
                existing.setBackgroundColor(color)
                val lp = existing.layoutParams
                decor.removeView(existing)
                decor.addView(existing, lp)
                XLog.d("状态栏遮罩颜色已更新为 #${Integer.toHexString(color)} 并重新置顶")
            } catch (e: Throwable) {
                XLog.d("更新状态栏遮罩失败（忽略）：${e.message}")
            }
            return
        }

        if (decor == null) {
            XLog.d("拿不到 decorView，跳过状态栏遮罩")
            return
        }

        val height = statusBarHeight(activity)
        if (height <= 0) {
            XLog.d("量不到状态栏高度，跳过状态栏遮罩")
            return
        }

        val filler = android.view.View(activity)
        filler.setBackgroundColor(color)
        try {
            decor.addView(
                filler,
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    height,
                    android.view.Gravity.TOP,
                ),
            )
        } catch (e: Throwable) {
            XLog.w("状态栏遮罩加不上（忽略，状态栏可能仍有一条色带）", e)
            return
        }
        statusFiller = filler

        filler.setOnApplyWindowInsetsListener { v, insets ->
            try {
                val h = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
                val lp = v.layoutParams
                if (h > 0 && lp != null && lp.height != h) {
                    lp.height = h
                    v.layoutParams = lp
                    XLog.d("状态栏遮罩高度跟随 inset 调整为 ${h}px")
                }
            } catch (e: Throwable) {
                XLog.d("调整状态栏遮罩高度失败（忽略）：${e.message}")
            }
            insets
        }

        XLog.w("状态栏区域已盖上页面底色实心遮罩（高 ${height}px，decorView 最后一层）")
    }

    /**
     * 拆掉 [coverStatusBar] 那一层（切回模块界面时调）。
     *
     *  只 `removeView` **我们自己加的**那个 View（`statusFiller` 自己记着引用），
     * 从不碰宿主的东西 —— 与「只加不删」的第一纪律不冲突：那条纪律说的是**宿主的** View。
     * 留着它会让状态栏那一条重新脱离模块控制（顶栏的模糊 / 浮层的 dim 都会被它挡掉）。
     *
     * 静默失败即可：拆不掉最多是那一条继续盖着实心色，不影响功能。
     */
    internal fun removeStatusFiller() {
        val filler = statusFiller ?: return
        statusFiller = null
        try {
            (filler.parent as? ViewGroup)?.removeView(filler)
            XLog.w("状态栏遮罩已拆掉 —— 那一条交回模块页面自己画")
        } catch (e: Throwable) {
            XLog.w("拆状态栏遮罩失败（忽略）：${e.message}")
        }
    }

    /**
     * 设置状态栏图标的明暗外观。
     *
     * 页面 / [coverStatusBar] 垫在状态栏底下的是什么颜色，系统在上面的那层图标
     *（时钟、电量…）就得反着来 —— 颜色由 `APPEARANCE_LIGHT_STATUS_BARS` 控制：
     * - **light=true**（浅色页面）→ 图标变深色（黑）；
     * - **light=false**（深色页面）→ 图标变浅色（白）。
     *
     * 不设时用宿主主题的默认值。宿主主题写死深色（`Theme.COUI.Main.Dark`），
     * 所以系统给浅色图标；切到浅色后页面底色变浅但图标仍白 ⇒ 全白看不见
     *
     *  minSdk=33，`View.getWindowInsetsController()` / `WindowInsetsController`
     * 是 API 30+ 平台 API，直接可用。
     *
     *  本文件只用平台 API（`WindowInsetsController`），不引 androidx —— 但要守的其实只是
     *  「**注入侧**不引 androidx」（AGENTS 一、1 与 CI 那道扫描）；`ui/` 里只在设置页那个进程跑、
     *  不在此列（同包的 `BackInterception` 就引了 androidx 的 `NavigationEvent`）。
     */
    private fun setStatusBarIconAppearance(activity: android.app.Activity, light: Boolean) {
        try {
            val controller = activity.window?.decorView?.windowInsetsController ?: run {
                XLog.d("拿不到 WindowInsetsController，跳过状态栏图标外观")
                return
            }
            if (light) {
                controller.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                )
            } else {
                controller.setSystemBarsAppearance(
                    0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                )
            }
            XLog.d("状态栏图标外观：${if (light) "深色图标（浅色页面）" else "浅色图标（深色页面）"}")
        } catch (e: Throwable) {
            XLog.w("设置状态栏图标外观失败（忽略）", e)
        }
    }

    /**
     * 状态栏的配色（页面建好时调一次；系统切深浅时由页面回调再调一次）。
     *
     * 顶栏自绘后这里只剩状态栏这一件事：宿主不会跟着系统深浅改
     * 全部只改颜色 / 只加自己的 View，不动宿主结构（见 [SettingsPageHooks.replaceContent] 的第一纪律）。
     */
    internal fun paintChrome(
        activity: android.app.Activity,
        pageCtx: android.content.Context,
        container: ViewGroup,
        pageBg: Int,
    ) {
        // 状态栏那一条：关掉系统薄纱 + 把「本来就露底」的那几层刷成页面色。
        //  **不在这里盖实心遮罩** —— 模块页面在显示时那一条就是它自己画的，
        // 盖上去等于把它挡在模块控制之外（见 [coverStatusBar] 的注释）。
        paintStatusBarChrome(activity, container, pageBg)
    }
}
