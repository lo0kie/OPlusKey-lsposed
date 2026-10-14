package com.lookie.opluskey.ui

import android.view.ViewGroup
import com.lookie.opluskey.core.config.ConfigBundle
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.inject.hookGuarded
import com.lookie.opluskey.ui.model.UNKNOWN_CONFIG_FORMAT
import com.lookie.opluskey.ui.screen.appVersionText
import com.lookie.opluskey.ui.screen.saveCommands
import com.lookie.opluskey.ui.state.toast
import com.lookie.opluskey.ui.state.write
import com.lookie.opluskey.ui.theme.pageBackgroundColor
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/**
 * 把系统的「设置 → 侧键」页换成模块自己的页面。
 */
internal object SettingsPageHooks {

    /** 宿主 Activity 基类 —— 页面骨架在这里，见类注释。 */
    private const val BASE_ACTIVITY =
        "com.oplus.gesture.actionbuttonkey.BaseActionActivity"

    /**
     * 侧键设置页的**实际** Activity（[BASE_ACTIVITY] 的子类）。
     *
     *  「重启服务」杀掉本进程之后要靠它把页面重新拉起来（见 `ui/state/ConfigActions`
     * 的 `schedulePageRelaunch`）：那一枪是进程死了之后由系统投递的，拿不到当前 Activity，
     * 只能有一个写死的组件名兜底。照旧保守取 `activity?.componentName`，取到就不用它。
     *  真机 `dumpsys package com.oplus.gesture` 核对（它的 intent-filter 里带着
     * `com.oplus.gesture.StartActionButtonSettingsActivity` 这个 action）。
     */
    internal const val SETTINGS_ACTIVITY =
        "com.oplus.gesture.actionbuttonkey.ActionButtonSettingsActivity"

    /**
     * `R.id.container` / `R.id.appbar_layout` 的数值、宿主包名、浅色主题名 ——
     * 全部搬到了 `ui/HostWindow.kt`（连同 [findContainer] / [pageContext] / [isNight]
     * 那几个纯读取的辅助函数）。本对象只留换页与 Hook 编排。
     */

    @Volatile
    private var installed = false

    /**
     * 安装入口带进来的 `module`。
     *
     * 留着是因为**返回拦截**要装 Hook，而 `install()` 之后才会真正换页
     * （那时已经没有 `module` 参数了）。
     */
    private var moduleRef: XposedModule? = null

    /**
     * 当前被换页的 Activity 与页面。
     *
     * 返回拦截必须**按实例判断** —— 我们 hook 的是平台类 `android.app.Activity` 的方法，
     * 不认实例的话会把宿主进程里别的 Activity 一起拦掉。
     */
    private var currentActivity: android.app.Activity? = null
    private var currentPage: SettingsPage? = null

    /**
     * 宿主那个基类本身（[BASE_ACTIVITY]）—— 换页回调**按它过滤实例**。
     *
     *  [Reflect.method] 是**沿父链上溯**取最靠下的那份声明：宿主若没自己重写 `onCreate`，
     *  取到的就是平台 `Activity#onCreate` —— 那个 hook 挂在**平台类**上，
     *  等于这个进程里**所有** Activity 都会进来。只判 `is Activity` 挡不住，
     *  必须判「是不是我们这个基类的实例」，否则别的页面也会被改主题 / 盖模块页。
     */
    private var baseClass: Class<*>? = null

    /** 平台类 `Activity#onBackPressed` 那个 hook 装没装（**进程内只装一次**）。 */
    private var legacyBackHooked = false

    /** 宿主**自己**声明的那层 `onBackPressed` 装没装（见 [installBackInterception] 的 ③'）。 */
    private var hostBackHooked = false

    /** 「导出日志」的请求码 —— 本进程里区分哪个 startActivityForResult 是我们的。 */
    private const val REQ_EXPORT_LOG = 0x4F4C

    /** 「导出配置」的请求码（同上，只是导出的是配置包而不是日志）。 */
    private const val REQ_EXPORT_CONFIG = 0x4F43

    /** 「导入配置」的请求码 —— 这条是**打开**文件（`ACTION_OPEN_DOCUMENT`），不是保存。 */
    private const val REQ_IMPORT_CONFIG = 0x4943

    /** 导出文件的**内容**时间戳（精确到秒，方便对齐别处的记录）。 */
    private val HEADER_STAMP: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 导出文件的**文件名**时间戳（精确到分，同分钟再导会由系统给「保留两者」）。 */
    private val FILE_STAMP: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    /** 平台类 `Activity#onActivityResult` 那个 hook 装没装（进程内只装一次）。 */
    private var exportResultHooked = false

    /**
     * 待写盘的导出文本 —— 用户在「保存到」里挑完位置之后由 [writeExportFile] 写进那个 uri。
     *
     * 导出日志与导出配置**共用这一格**：SAF 选择器同一时刻只可能有一个（弹着的时候点不了别处），
     * 结果回来时按请求码就知道这份文本是哪一种。
     */
    private var pendingExport: String? = null

    /**
     * 已经注册过 `OnBackInvokedCallback` 的那个 **Activity 实例**。
     *
     * 「push 的两个页面返回经常会直接回到设置，而不是按栈的顺序排」）：
     * 退出页面时回调里会把自己**注销**掉，下次再进这个页面是**另一个 Activity 实例**
     * （宿主是 `launchMode=singleInstance`，`finish` 之后重进必然新建实例）。
     * 若还拿一个进程级的布尔量挡着，新实例就永远没有返回回调 ⇒ 系统按默认行为
     * 直接关页 ⇒ 表现就是「从子页按返回，直接回到系统设置」。
     */
    private var backCallbackActivity: android.app.Activity? = null

    /**
     * 模块页面的根 View 与宿主原顶栏 —— 「打开自带的快捷键界面」靠切换这两者的可见性实现。
     *
     *  宿主 Activity 是 `launchMode=singleInstance`（真机 aapt 核对），再 start 只会走
     * `onNewIntent`、`onCreate` 不再跑，所以不能靠「起一个不换页的实例」。
     * 就地切换还有个前提：宿主那棵 View 树一直活着（[replaceContent] 只覆盖不清空）。
     *
     *  与上面那几个字段一样，**页面关掉时要一起松掉**（[clearPageRefs]）——
     * 不然它们会钉住一个已销毁的 Activity 直到下次进页。
     */
    private var pageView: android.view.View? = null
    private var hostAppbar: android.view.View? = null

    /** 换页时的内容容器（露出原版页面时要重刷状态栏配色）。 */
    private var hostContainer: ViewGroup? = null

    /**
     * 宿主原版页面的底色。
     *
     * 原版页是宿主写死的**深色**（清单主题 `ActionButtonThemeAppNoTitle` 的 parent 是
     * `Theme.COUI.Main.Dark`，见 [applyHostTheme]）—— 所以露出它时状态栏也得是黑的，
     * 不能留我们页面的浅色底（真机反馈「打开自带的界面后状态栏是白的、页面是黑的」）。
     */
    private const val STOCK_BACKDROP = 0xFF000000.toInt()

    /** 现在露的是不是宿主原版页面（true 时系统返回 = 切回模块界面）。 */
    @Volatile
    private var stockRevealed = false

    /** 装上的 hook 数，供日志与验收用。 */
    @Volatile
    var hookCount: Int = 0
        private set

    /**
     * 顶栏标题：宿主的原文（首页用）。
     *
     * 顶栏自绘后（2026-10-11），标题由模块页面自己渲染；这里只负责把宿主 Activity
     * 的标题读出来交给页面（子页标题「按键绑定」也归页面管）。
     */
    private var hostTitle: String = ""

    /**
     * 状态栏那块实心遮罩（见 [coverStatusBar]）。
     *
     *  **2026-10-12 起只在「露出宿主原版页面」那一档存在**（见 [revealStock] /
     * [removeStatusFiller]）：模块页面自己就是 edge-to-edge、那条早就是它画的，
     * 盖遮罩反而把它挡在模块控制之外。
     */
    private var statusFiller: android.view.View? = null

    /**
     * 按键绑定子页的标题挪进了页面（`SettingsPage.BINDING_TITLE`）——
     * 顶栏自绘后（2026-10-11）标题归页面自己渲染。
     */

    /**
     * 调试旁路：这些路径里**任意一个存在**就跳过换页，让系统原版页面显示出来。
     *
     * 存在的唯一理由是**视觉对齐**：要照着原版把新页面做得像，就得先把原版截下来看。
     * 造这个开关比反复改代码方便（`touch` 一下即可来回切换）。
     *
     * 多条路径是因为**设置页进程能读哪个路径并不确定**
     * （它是宿主的 `platform_app` 域，读不了 `/data/system`）——
     * 两个都试，哪个能创建就用哪个。第二条跟着配置文件走（[ConfigStore.filesDir]），
     * 免得配置挪了地方、这个路径还指着旧址。
     */
    private val BYPASS_PATHS = listOf(
        "/data/local/tmp/opluskey_bypass",
        ConfigStore.filesDir().resolve("opluskey_bypass").path,
    )

    private fun bypassed(): Boolean {
        for (p in BYPASS_PATHS) {
            try {
                if (java.io.File(p).exists()) {
                    XLog.w("命中调试旁路（$p 存在）→ 本次不换页，显示系统原版页面")
                    return true
                }
            } catch (e: Throwable) {
                XLog.d("检查旁路标记失败（忽略）：$p | ${e.message}")
            }
        }
        return false
    }

    fun install(module: XposedModule, loader: ClassLoader) {
        if (bypassed()) return
        moduleRef = module

        if (installed) {
            XLog.w("设置页 Hook 已经装过，跳过重复安装")
            return
        }

        val base = com.lookie.opluskey.core.reflect.Reflect.findClass(loader, BASE_ACTIVITY)
        if (base == null) {
            XLog.w(
                "没找到 $BASE_ACTIVITY —— 本机不是 ColorOS，或侧键设置页改了名。" +
                        "模块不换页，但设置页仍是系统原版（功能正常）",
            )
            return
        }

        val onCreate = com.lookie.opluskey.core.reflect.Reflect.method(
            base,
            "onCreate",
            android.os.Bundle::class.java,
        )
        if (onCreate == null) {
            XLog.w("$BASE_ACTIVITY 上没找到 onCreate(Bundle)，无法换页")
            return
        }
        baseClass = base

        val handle = module.hookGuarded(onCreate) { chain ->
            onAfterCreate(chain)
        }

        // 「导出日志 / 导出配置 / 导入配置」都要从宿主的 onActivityResult 收结果
        //（宿主 Activity 不是我们的，SAF 的结果只走它）
        installFileResultHook(module)

        // 生命周期转发：**从「设置 → 快捷键」重新进来**时把页面栈清回首页
        //   宿主这个 Activity 被系统复用、再进来不重建，栈会残留在子页）。
        //
        //  判据是 `Activity#onNewIntent`，**不是**「onResume / onUserLeaveHint」那一对：
        //   · 设置里点「快捷键」⇒ 宿主是 `singleInstance`，系统把这次启动当**新 Intent**
        //   · 从最近任务切回来 / 熄屏亮屏 / 通知栏来回 ⇒ 只是把任务提到前台，**不派发** ⇒ 不清栈。
        //
        //  用 [com.lookie.opluskey.core.reflect.Reflect.method] 从宿主类往上找（它取**最靠下**的那份声明）：
        //    宿主若自己重写了 onNewIntent 就挂它那份（不调 super 也能收到），
        //    没重写就落到平台的 `Activity#onNewIntent` 上。与 onCreate 同一挂法；
        //    按实例过滤，不吃宿主进程里其它 Activity。
        val lifecycleHooks = intArrayOf(0)
        val onNewIntent = com.lookie.opluskey.core.reflect.Reflect.method(
            base,
            "onNewIntent",
            android.content.Intent::class.java,
        )
        if (onNewIntent != null) {
            if (module.hookGuarded(onNewIntent) { chain ->
                    // 前置动作单独兜住：它抛了也不能把宿主自己的 onNewIntent 一并跳过
                    try {
                        if (chain.thisObject === currentActivity) {
                            // 从设置重进：若上一轮停在「露出宿主原版页面」那一档
                            //（用户从 ⋮ 菜单点过「打开自带的快捷键界面」，按 Home 走了、
                            //  再从设置进来），必须先把模块页面切回来 —— 否则重进看到的
                            // 是原版页，还会以为模块坏了。宿主是 singleInstance，重进走的是
                            // 这里、`onCreate` 不会再跑，所以这条恢复只能挂在这儿。
                            if (restoreModuleUi()) {
                                XLog.w("从设置重进：上一轮露着宿主原版页 ⇒ 先切回模块页面")
                            }
                            currentPage?.onHostReentered()
                        }
                    } catch (e: Throwable) {
                        XLog.w("onHostReentered 失败（onNewIntent 照常 proceed）", e)
                    }
                    chain.proceed()
                } != null) {
                lifecycleHooks[0]++
            }
        } else {
            XLog.w("$BASE_ACTIVITY 上没找到 onNewIntent(Intent)，从设置重进不会清栈")
        }

        if (handle != null) {
            hookCount = 1 + lifecycleHooks[0]
            installed = true
            XLog.w(
                "设置页换页 Hook 安装完成：换页 1/1 + 生命周期 ${lifecycleHooks[0]}/1" +
                        "（$BASE_ACTIVITY）",
            )
        } else {
            XLog.w("设置页换页 Hook 装不上（见上面的失败原因）")
        }
    }

    /**
     * `onCreate` 的 after 回调。
     *
     *  顺序是「先让原方法跑完，再换内容」—— 必须这样：
     *  - 原方法里才有 `setContentView`，不跑完就没有 `container` 可找；
     *  - 顶部栏、窗口配色也都在原方法里装好，我们只在其后动内容区。
     *
     * 在**原方法之前**做的事只有一件：把宿主主题换成浅色变体（见 [applyHostTheme]）——
     * 主题必须在 `setContentView` 之前定下来，晚了顶栏不会跟着变。
     *
     * 原方法自身的异常**原样抛出**（不是我们该吞的），
     * 只有「换页」这一步的异常才归我们消化。
     */
    private fun onAfterCreate(chain: XposedInterface.Chain): Any? {
        //  **先按实例过滤，再动任何东西**：`Reflect.method` 沿父链上溯，宿主没重写
        // `onCreate` 时这个 hook 落在平台 `Activity#onCreate` 上 —— 那时这个进程里
        // 每一个 Activity 都会进来。判 `is baseClass` 才算「这是侧键设置页」；
        // 判不出来的（`baseClass` 为空）按「不是」处理，宁可这次不换页，
        // 也不能去改别的页面的主题、更不能往别人的 container 上盖模块页。
        //  主题必须在 `setContentView` 之前定下来，所以这条过滤得排在 `applyHostTheme` 前面。
        if (baseClass?.isInstance(chain.thisObject) != true) {
            return chain.proceed()
        }
        (chain.thisObject as? android.app.Activity)?.let { applyHostTheme(it) }

        // 再把宿主自己的 onCreate 跑完。这一步失败是宿主的 bug，让它照常崩。
        val result = chain.proceed()

        val activity = chain.thisObject
        if (activity !is android.app.Activity) {
            XLog.w("hook 到的不是 Activity（${activity?.javaClass?.name}），跳过换页")
            return result
        }

        //  **必须在 `chain.proceed()` 之后**（= `setContentView` 之后）才设得上：
        // `Window.setWindowAnimations()` 在 `mWindowAttributes` 还没建起来时直接 return，
        // 而 `mWindowAttributes` 是 `setContentView` → `installDecor` 那一步才初始化的。
        // 窗口的 `wanim` 依旧是非 0 的宿主主题值，等于没设。
        //  但要清楚：**设上了也治不了「从设置进来黑一下」** —— 那截黑是宿主 splash，
        // 后面那 0.26s 淡入由 ShellTransitions 执行、不看 `wanim`。实测数据见
        // [disableWindowTransition] 的注释，别把这个调用当成那条 bug 的修复。
        disableWindowTransition(activity)

        try {
            replaceContent(activity)
        } catch (e: Throwable) {
            // 换页失败不能让设置页打不开 —— 退回系统原版即可。
            XLog.w("设置页换页失败（保留系统原版页面）", e)
        }
        return result
    }

    /** 系统是浅色时，把宿主主题从写死的深色换成同族的浅色变体。 */
    private fun applyHostTheme(activity: android.app.Activity) {
        //  `isNight` 已改用**系统全局配置**（见其注释）：宿主声明了 `configChanges=uiMode`，
        val night = isNight(activity)
        XLog.d("夜间状态（系统全局）：night=$night")

        //  转场动画**不在这里**关：`Window.setWindowAnimations()` 在 `mWindowAttributes`
        // 还是 null 时会**静默返回**，而这一步跑在 `setContentView` 之前 ⇒ 调了等于没调。
        // 真机 `dumpsys window` 实测过：窗口的 `wanim` 仍然是宿主主题的 `0x7f120024`。
        // 现在挪到 [onAfterCreate] 里 `chain.proceed()` 之后（见 [disableWindowTransition]）。
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
    private fun disableWindowTransition(activity: android.app.Activity) {
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

    /** 找到 `container` 并盖上模块页面。 */
    private fun replaceContent(activity: android.app.Activity) {
        val container = findContainer(activity)
        if (container == null) {
            XLog.w(
                "找不到 R.id.container（0x${Integer.toHexString(ID_CONTAINER)}）" +
                        "—— 可能是 ROM 改了布局，换页放弃，页面仍是系统原版",
            )
            return
        }

        //  全量迁移（2026-10-11）：页面改成「**宿主真·COUI 控件 + 平台组件**」，
        // 不再走 Compose / MD3 —— 那条路是「MD 组件 + COUI 取色」的混搭，观感与系统页对不上。
        // 新页面用反射造宿主的 COUISwitch（类名未混淆、构造器是标准 View 构造器、
        // 平台方法名改不了），其余用框架控件 + 宿主的 COUI 资源名，
        // 依据见 docs/device-findings.md 第十节。
        //  全量迁移（2026-10-11 第二轮）：页面改成 **COUI（Compose 组件库）**，
        // 不再用宿主控件反射、也不再从宿主主题取色 —— 依据见 SettingsPage 的类注释
        // 与 app/build.gradle.kts 的依赖段。
        // ⇒ ComposeView 需要 ViewTree owner，本文件在 `container.addView` **之后**
        //   回调 `page.attachOwnersUpward(view)`（宿主那棵树上一个 owner 都没有）。
        val pageCtx = pageContext(activity)
        val view: android.view.View
        val pageBg: Int
        val page = SettingsPage(pageCtx)
        view = page.createView() ?: run {
            XLog.w("模块页面构建失败 —— 保持系统原版页面")
            return
        }
        pageBg = page.pageBackgroundColor

        // 「从设置进模块页面会瞬间黑再亮」）。
        //
        //  2026-10-11 更正定责：这里刷的是**本 Activity 自己的 window / decorView /
        // （splash，纯黑，由宿主清单主题决定、在 SystemUI 侧画）。完整的录屏数据与
        // 结论见 [disableWindowTransition] 的注释。
        // 这一层刷色仍然保留：splash 撤掉之后、模块页面首帧画出来之前，露出来的是
        // 本 Activity 的 window 底色，刷成页面底色可保证那一瞬不跳色（保险性质）。
        //
        // 只改背景色，不动结构、不动 insets（与 [applyStatusBarArea] 同一纪律）。
        paintWindowBackdrop(activity, container, pageBg)

        // 不透明 + 吃触摸：盖住宿主内容，同时不让点击穿透到下面
        view.isClickable = true
        view.isFocusable = true

        container.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        //  **必须在 addView 之后**给整条祖先链挂 ViewTree owner：ComposeView 在
        // `onAttachedToWindow` 里就要用（那个时机在 try/catch 之外，缺了直接崩），
        // 而它只沿 **parent 链**找 owner —— addView 之前 parent 还是 null。
        page.attachOwnersUpward(view)

        // 首帧诊断（2026-10-11）：记下模块页面**真的画到屏幕上**的时刻。
        // 用途：录屏逐帧分析「从设置进来黑多久」时，靠它把那段黑切成两截 ——
        // 「宿主 splash 还在」与「splash 撤了、我们的页面还没画出来」。
        // 只看录屏是分不清的（两种情况都是一片黑），这是唯一能定责的锚点。
        // 只打一次；移除监听走 `post`（在 onDraw 里直接 remove 会踩并发修改）。
        try {
            val probe = view
            probe.viewTreeObserver.addOnDrawListener(
                object : android.view.ViewTreeObserver.OnDrawListener {
                    private var done = false
                    override fun onDraw() {
                        if (done) return
                        done = true
                        XLog.w("模块页面首帧已绘制")
                        probe.post {
                            runCatching { probe.viewTreeObserver.removeOnDrawListener(this) }
                        }
                    }
                },
            )
        } catch (e: Throwable) {
            XLog.d("挂首帧诊断失败（忽略）：${e.message}")
        }

        // 标题与返回键由模块页面自己的 `TopAppBar` 渲染，宿主的 appbar 整条藏掉。
        // 只改可见性、不 remove —— 换页第一纪律（宿主可能持有引用，remove = 埋雷）。
        // 藏掉之后也就不需要避让（原来的 reserveAppbarSpace）、不需要给宿主顶栏
        // 刷底色 / 换标题 / 染图标（那些函数一并移除）。
        try {
            val appbar = activity.findViewById<android.view.View>(ID_APPBAR)
            if (appbar == null) {
                // 不伪造能力：没找到就说没找到，别打「已隐藏」（排查时会被带偏）
                XLog.w("没找到宿主顶栏（R.id.appbar_layout）—— 顶栏可能与页面重叠")
            } else {
                appbar.visibility = android.view.View.GONE
                hostAppbar = appbar
                XLog.w("宿主顶栏已隐藏 —— 标题与返回键由模块页面自绘")
            }
        } catch (e: Throwable) {
            XLog.w("隐藏宿主顶栏失败（顶栏可能与页面重叠，功能不受影响）", e)
        }

        // 见 [revealStock] —— 宿主那棵 View 树一直都在，露出来就是原版页面。
        pageView = view
        hostContainer = container
        page.onOpenStock = { revealStock(activity) }
        page.onExportLog = { exportLog(activity) }
        page.onExportConfig = { exportConfig(activity) }
        page.onImportConfig = { importConfig(activity) }

        // 状态栏区域已归页面自己管：模块页面本身是 edge-to-edge 的（Scaffold
        // `contentWindowInsets = 0`、顶栏 107dp 含状态栏 inset、底色铺到 y=0），
        // 那一条早就是页面自己画的，之前盖在上面的 [coverStatusBar] 遮罩反而把它
        // 挡在模块控制之外 —— 顶栏的模糊在那儿断了、浮层的 dim 也到不了。
        // 现在只剩「露出宿主原版页面」时才盖（那一档模块页面是 GONE 的，没人画那一条）。

        // 首页标题交给页面（子页标题「按键绑定」在页面里）
        hostTitle = try {
            activity.title?.toString().orEmpty()
        } catch (e: Throwable) {
            XLog.d("读宿主标题失败（忽略）：${e.message}")
            ""
        }
        page.homeTitle = hostTitle

        // 返回：子页（按键绑定）→ 首页，首页 → 关页面。
        // ① 页面顶栏自己的返回键（页面内部处理）；
        // ② 系统返回 / 返回手势（终点是 `Activity#finish`，见 [installBackInterception]）。
        currentActivity = activity
        currentPage = page
        moduleRef?.let { installBackInterception(it, activity) }

        // 系统切深浅时：页面自己会换色板，但**状态栏是我们刷在宿主 View 上的**，
        // 不会跟着变 —— 不重刷就会出现「页面已经变深色、状态栏还是浅色」的割裂。
        page.onSystemThemeChanged = {
            XLog.w("系统深浅变了：重刷状态栏配色")
            paintChrome(activity, pageCtx, container, page.pageBackgroundColor)
        }

        // 状态栏的配色统一走 paintChrome（见上面那条回调）。
        //  先拆掉可能残留在窗上的状态栏遮罩（上一档「露出宿主原版页面」盖的）：
        // 这一步之后那一条就完全归模块页面自己画，见 [coverStatusBar] 的注释。
        removeStatusFiller()
        paintChrome(activity, pageCtx, container, pageBg)

        //  深浅切换只靠页面里的每秒轮询（读系统全局配置）。
        // 抓到它是「亮色进页先黑 1 秒再变白」的元凶：宿主声明了 `configChanges=uiMode`，
        // 它的配置**停在旧的 night 值**（见 [pageContext] 注释），回调把这份过期配置喂给
        // 页面 ⇒ 页面刚画完浅色就被翻成深色，一秒后轮询才纠正回来。删。

        // 状态栏图标的明暗已经由上面那句 `paintChrome` 里的
        // `setStatusBarIconAppearance` 按页面底色刷过（浅底 → 深色图标）。

        XLog.w(
            "设置页已盖上模块页面（container 原有 ${container.childCount - 1} 个子 View 保留未动）",
        )
    }

    /**
     * 露出宿主**原版**快捷键界面：藏掉模块页面、恢复宿主顶栏（菜单项「打开自带的快捷键界面」）。
     *
     * 宿主的内容一直活着（[replaceContent] 只覆盖不清空），所以只是切可见性 ——
     * 不动结构、不重造页面，返回时再切回来即可（[restoreModuleUi]）。
     */
    private fun revealStock(activity: android.app.Activity) {
        try {
            pageView?.visibility = android.view.View.GONE
            hostAppbar?.visibility = android.view.View.VISIBLE
            stockRevealed = true
            // 状态栏跟着原版页面走（原版是深色底，见 STOCK_BACKDROP）。
            //  这一档是 [coverStatusBar] **唯一**还在用的地方：模块页面已经 GONE，
            // 那一条没人画，而宿主原版页顶栏的配色我们碰不了 ⇒ 直接盖一块实心遮罩最稳。
            hostContainer?.let {
                paintStatusBarChrome(activity, it, STOCK_BACKDROP)
                coverStatusBar(activity, STOCK_BACKDROP)
            }
            XLog.w("已露出宿主原版快捷键界面（状态栏切深色底；系统返回切回模块界面）")
        } catch (e: Throwable) {
            XLog.w("露出宿主原版页面失败（忽略）：${e.message}")
        }
    }

    /**
     * 切回模块界面（在宿主原版界面上按系统返回时）。
     *
     * @return true 表示这次返回被消费掉了（不要再关页面）。
     */
    private fun restoreModuleUi(): Boolean {
        if (!stockRevealed) return false
        return try {
            hostAppbar?.visibility = android.view.View.GONE
            pageView?.visibility = android.view.View.VISIBLE
            stockRevealed = false
            // 状态栏跟着模块页面走（按当前深浅取页面底色）。
            //  必须把 [coverStatusBar] 盖的那层**拿掉**：它在 decorView 最上面，
            // 留着的话状态栏那一条就又脱离模块控制了（顶栏的模糊 / 浮层的 dim 都到不了）。
            removeStatusFiller()
            val activity = currentActivity
            val bg = currentPage?.pageBackgroundColor
            if (activity != null && bg != null) {
                hostContainer?.let { paintStatusBarChrome(activity, it, bg) }
            }
            XLog.w("系统返回：从宿主原版界面切回模块界面（不关设置页）")
            true
        } catch (e: Throwable) {
            XLog.w("切回模块界面失败（忽略）：${e.message}")
            false
        }
    }

    /** 只刷状态栏那一条 —— 换页时 / 露出原版页面 / 切回模块界面 三处共用。 */
    private fun paintStatusBarChrome(
        activity: android.app.Activity,
        container: ViewGroup,
        color: Int,
    ) {
        disableStatusBarScrim(activity)
        applyStatusBarArea(activity, container, color)
        setStatusBarIconAppearance(activity, isLightColor(color))
    }

    /** 拦截「系统返回 / 返回手势」，让子页先回上一层。 */
    private fun installBackInterception(module: XposedModule, activity: android.app.Activity) {
        currentActivity = activity

        // ③ 老路子：hook 平台类 `Activity#onBackPressed`
        if (!legacyBackHooked) {
            legacyBackHooked = hookOnBackPressed(module, platformOnBackPressed())
        }

        // ③' 宿主自己那层。 不会重复消费：③' 一旦判定「消费」，宿主方法体就不执行
        //     ⇒ 它那句 `super.onBackPressed()` 也不会发生 ⇒ ③ 收不到。
        if (!hostBackHooked) {
            hostBackHooked = hookOnBackPressed(module, hostOnBackPressed(activity))
        }

        // ② 官方入口：每个 Activity 实例各注册一次
        var registered = false
        if (backCallbackActivity !== activity) {
            registered = registerBackCallback(activity)
            if (registered) backCallbackActivity = activity
        }

        XLog.w(
            "返回拦截：平台 onBackPressed=$legacyBackHooked，" +
                    "宿主 onBackPressed=$hostBackHooked，本次注册返回回调=$registered",
        )
        if (!legacyBackHooked && !hostBackHooked && backCallbackActivity !== activity) {
            XLog.w("返回拦截一条都没装上 —— 子页返回请用顶栏那个返回箭头")
        }
    }

    /**
     * 松掉换页期间钉住的那些引用（当前 Activity / 页面 / 页面根 View / 宿主顶栏 / 容器 /
     * 「露着原版页面」标记）。**页面关掉时调**（见 [handleDiscreteBack] 的「交还宿主」那支）。
     *
     *  这些字段都是进程级的 `var`（界面换页是这个进程唯一的事，没必要每实例一份），
     *  不主动清就会一直钉住**上一个已销毁的 Activity**，直到下次进页才被覆盖 ——
     *  有界泄漏，只影响这个 `:ui` 进程，但既然有关页这个明确的时点就该清掉。
     */
    private fun clearPageRefs(activity: android.app.Activity) {
        if (currentActivity !== activity) return
        //  状态栏遮罩也要一起撤：它挂在 **decorView** 上（不是宿主那棵树），
        //  `pageView` 那个引用一松就再没人认得出它 —— 留在窗上会盖住下一轮进来的页面
        //  （表现为「重进设置页后状态栏那一条是死的、不吃点击也不跟着换色」）。
        removeStatusFiller()
        currentActivity = null
        currentPage = null
        pageView = null
        hostAppbar = null
        hostContainer = null
        stockRevealed = false
    }

    /**
     * 「返回」的**第一优先级**：键盘开着就先收键盘，这次返回到此为止
     *
     * 平台约定里 IME 比页面导航更靠前，但我们在 `OnBackInvokedDispatcher` 上注册的是
     * `PRIORITY_OVERLAY`（2000000）—— 比 IME 自己那个回调优先级高，返回会被我们抢走、
     * 退回了「按键绑定」。
     *
     *  判据只认 [isImeVisible]（IME inset 可见），不认「有没有字段持有焦点」——
     * 后者在键盘已收起时照样成立，会把本该退页面的返回吃掉。
     *
     * @return true = 键盘本来是开着的（已经收起来，调用方不要再往下走）
     */
    private fun consumeBackByIme(): Boolean {
        val activity = currentActivity ?: return false
        if (!isImeVisible(activity)) return false
        XLog.w("系统返回：键盘开着 ⇒ 只收键盘，不退页面")
        hideIme(activity)
        return true
    }

    /**
     * 把同一套「返回先给页面用」的判定挂到一个 `onBackPressed` 上。
     *
     * @return 挂上了没有（[origin] 为 null 时直接 false）
     */
    private fun hookOnBackPressed(
        module: XposedModule,
        origin: java.lang.reflect.Method?,
    ): Boolean {
        if (origin == null) return false
        return module.hookGuarded(origin) { chain ->
            if (chain.thisObject === currentActivity) {
                when {
                    //  键盘优先于页面栈：与 `registerBackCallback` 那条保持同一个顺序，
                    //    两条路谁先收到都不能让「键盘开着却退了页面」。
                    consumeBackByIme() -> null
                    //  浮窗开着 ⇒ 自己消费这次返回：收掉浮窗，不退页面（同 handleDiscreteBack）
                    currentPage?.hasOpenOverlays() == true -> {
                        XLog.w("系统返回（onBackPressed）：浮窗开着 ⇒ 收掉浮窗，不退页面")
                        currentPage?.closeOverlays("系统返回收浮窗")
                        null
                    }

                    currentPage?.goBack() == true -> {
                        XLog.w("系统返回（onBackPressed）：子页 → 首页（不关设置页）")
                        null // 不 proceed = 这一次返回被消费
                    }

                    restoreModuleUi() -> null // 原版界面 → 模块界面，同样消费掉
                    else -> chain.proceed()
                }
            } else {
                chain.proceed()
            }
        } != null
    }

    /**
     * 给这个 Activity 实例注册 `OnBackInvokedCallback`（API 33+；宿主没 opt-in 会抛，catch 掉）。
     *
     *  回调里要把「不在子页」的返回**交还给系统**：注册过回调之后系统不会再自己关页面，
     * 所以先注销自己、再 finish —— 不这么做的话首页按返回会没反应。
     *
     *  API 34 起注册 [android.window.OnBackAnimationCallback]：系统会把预测返回手势的
     * **逐帧进度**也送进来，转发给页面（[SettingsPage.navBackInput]）后，coui-nav 自己驱动
     * 「页面跟着手指走」的转场。API 33（ColorOS 13）没有这个接口，注册原来的普通回调，
     * 返回退化成「松手才弹」的一次性动作。
     *
     *  ⚠️ 优先级仍是 `PRIORITY_OVERLAY`：这一层本来就要盖过宿主与系统默认的返回处理，
     * 换成动画回调不改变它「抢下返回」这件事。
     */
    private fun registerBackCallback(activity: android.app.Activity): Boolean = try {
        var callback: android.window.OnBackInvokedCallback? = null
        callback = if (android.os.Build.VERSION.SDK_INT >= 34) {
            PredictiveBackCallback(activity)
        } else {
            android.window.OnBackInvokedCallback { handleDiscreteBack(activity, callback) }
        }
        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
        true
    } catch (e: Throwable) {
        // 宿主没在清单里开 `android:enableOnBackInvokedCallback` 时就是这条
        XLog.w("注册返回回调失败（宿主未 opt-in）：${e.javaClass.simpleName}: ${e.message}")
        false
    }

    /**
     * 「一次性返回」的判定 —— 键盘 → 页面栈 → 露回模块界面 → 交还宿主。
     *
     *  两条没有手势进度的路都走这里：API 33 上的 `OnBackInvokedCallback`，以及
     * API 34+ 上「回调没接管手势」（首页按返回、键盘优先）时的提交。
     *
     * @param callback 自己注册的那个回调 —— 「交还宿主」时先注销它再关页面
     *   （不注销的话系统不会再自己关页面，首页按返回就没反应了）
     */
    private fun handleDiscreteBack(
        activity: android.app.Activity,
        callback: android.window.OnBackInvokedCallback?,
    ) {
        XLog.w("系统返回（OnBackInvoked）：收到")
        when {
            // 键盘开着 ⇒ 这一次返回只用来收键盘（见 consumeBackByIme 的说明）
            consumeBackByIme() -> Unit
            // 浮窗开着 ⇒ 自己消费这次返回：把浮窗收掉，不退页面
            //  2026-10-13 用户定案：不接的话，浮窗盖在上面、底下的页面却被退掉。
            currentPage?.hasOpenOverlays() == true -> {
                XLog.w("系统返回：浮窗开着 ⇒ 收掉浮窗，不退页面")
                currentPage?.closeOverlays("系统返回收浮窗")
            }

            currentPage?.goBack() == true -> {
                XLog.w("系统返回（OnBackInvoked）：子页 → 首页（不关设置页）")
            }

            restoreModuleUi() -> {
                // 原版界面 → 模块界面，消费掉这次返回
            }

            else -> {
                XLog.w("系统返回（OnBackInvoked）：不在子页 ⇒ 交还宿主（关页面）")
                try {
                    activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback!!)
                } catch (e: Throwable) {
                    XLog.d("注销返回回调失败（忽略）：${e.message}")
                }
                backCallbackActivity = null
                // 页面关掉了 = 这一份换页用的引用用完了，一起松掉：
                // 不松的话当前 Activity / SettingsPage / 两个 View / 容器会一直挂到**下次进页**
                // 才被覆盖（表现为「钉住一个已销毁的 Activity」，只影响这个 :ui 进程）。
                clearPageRefs(activity)
                activity.finish()
            }
        }
    }

    /**
     * 预测返回的手势回调（API 34+）。
     *
     *  会话状态是**三态**而不是布尔 —— 「键盘开着」这条在 `onBackStarted` 就把返回消费掉了
     *（收键盘），但系统并不知道，提交时照样会调 [onBackInvoked]；那次提交**必须不再往下走**
     *（键盘已经收了，再走一遍就会把页面也退掉）。
     *
     *  只有接管了的会话才把进度喂给页面：喂了，coui-nav 就会驱动「页面跟着手指走」的转场。
     */
    private class PredictiveBackCallback(
        private val activity: android.app.Activity,
    ) : android.window.OnBackAnimationCallback {

        private companion object {
            /** 还没开始 / 已结束。 */
            private const val SESSION_NONE = 0

            /** 这一手势被「收键盘」消费掉了 —— 后续提交与进度都忽略。 */
            private const val SESSION_IME = 1

            /** 已交给页面（[SettingsPage.navBackInput]），进度与提交都喂给它。 */
            private const val SESSION_PAGE = 2
        }

        private var session = SESSION_NONE

        override fun onBackStarted(backEvent: android.window.BackEvent) {
            val page = currentPage
            session = when {
                consumeBackByIme() -> SESSION_IME
                page != null && page.wouldConsumeBack() -> {
                    session = SESSION_PAGE
                    page.navBackInput.started(backEvent.toNavigationEvent())
                    SESSION_PAGE
                }

                else -> SESSION_NONE
            }
        }

        override fun onBackProgressed(backEvent: android.window.BackEvent) {
            if (session == SESSION_PAGE) {
                currentPage?.navBackInput?.progressed(backEvent.toNavigationEvent())
            }
        }

        override fun onBackInvoked() {
            when (session) {
                SESSION_PAGE -> currentPage?.navBackInput?.completed()
                // 键盘那次已经收掉了，这次提交别再走「一次性」判定（否则会把页面也退掉）
                SESSION_IME -> Unit
                else -> handleDiscreteBack(activity, this)
            }
            session = SESSION_NONE
        }

        override fun onBackCancelled() {
            if (session == SESSION_PAGE) currentPage?.navBackInput?.cancelled()
            session = SESSION_NONE
        }
    }

    /** 平台手势事件 → coui-nav 的事件（两边的 `EDGE_LEFT/RIGHT` 常量取值一致）。 */
    private fun android.window.BackEvent.toNavigationEvent() =
        androidx.navigationevent.NavigationEvent(
            swipeEdge = swipeEdge,
            progress = progress,
            touchX = touchX,
            touchY = touchY,
            frameTimeMillis = frameTimeMillis,
        )

    /**
     * 把「窗口的底」刷成页面底色 —— 防**首帧之前露底跳色**（**不是**那段黑的主因）。
     */
    private fun paintWindowBackdrop(
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
     *（见 [revealStock]，配套 [removeStatusFiller] 在切回模块界面时拿掉）。
     */
    private fun coverStatusBar(activity: android.app.Activity, color: Int) {
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
    private fun removeStatusFiller() {
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
     * 是 API 30+ 平台 API，直接可用（本文件不引 androidx，只用平台 API）。
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
     * 顶栏自绘后（2026-10-11）这里只剩状态栏这一件事：宿主不会跟着系统深浅改
     * 全部只改颜色 / 只加自己的 View，不动宿主结构（见 [replaceContent] 的第一纪律）。
     */
    private fun paintChrome(
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

    // `isLightColor` / `pageContext` / `findContainer` 已搬到 `ui/HostWindow.kt`
    // （纯读取与包装，与换页编排无关）。

    /**
     * “导出日志”：弹系统的“保存到”选择器（SAF），用户挑完位置再把文本写进那个 uri。
     *
     *  结果只能从宿主的 `Activity#onActivityResult` 收（宿主 Activity 不是我们的），
     * 所以那条路要挂 hook（见 [installExportResultHook]）；文本先存 [pendingExport]。
     */
    private fun exportLog(activity: android.app.Activity) {
        val body = XLog.snapshot()
        if (body.isEmpty()) {
            currentPage?.toast("暂无日志")
            return
        }
        val now = java.time.LocalDateTime.now()
        // 文件头带版本 + 当前时间：日志发出来时不用再问「哪个版本、什么时候的」
        val text = buildString {
            append("OPlusKey ").append(currentPage?.appVersionText().orEmpty())
            append(" | ").append(now.format(HEADER_STAMP))
            append("\n\n").append(body).append('\n')
        }
        val name = "OPlusKey-" + now.format(FILE_STAMP) + ".log"
        pendingExport = text
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TITLE, name)
        }
        try {
            activity.startActivityForResult(intent, REQ_EXPORT_LOG)
            XLog.w("导出日志：已弹出保存位置选择器（${body.length} 字符）")
        } catch (e: Throwable) {
            pendingExport = null
            XLog.w("导出日志：拉不起选择器：${e.message}")
            currentPage?.toast("导出失败")
        }
    }

    /**
     * 「导出配置」：弹系统的「保存到」选择器（SAF），把**主配置 + 命令库**写成一份文件。
     *
     * 内容由 [ConfigBundle] 合成（JSON 信封，见它的注释）；写盘与导出日志同一条路 ——
     * 结果同样只能从宿主的 `onActivityResult` 收，文本先存 [pendingExport]。
     */
    private fun exportConfig(activity: android.app.Activity) {
        val page = currentPage
        if (page == null) {
            XLog.w("导出配置：页面没了，放弃")
            return
        }
        // 导出**页面手上这份**（= 用户看到的），不是重读一遍文件：两边正常时等价，
        // 而页面这份已经过 `configReadOnly` 那道闸 —— 读不懂的状态下压根进不来这一页。
        val text = ConfigBundle.export(page.config.value, page.commands.value)
        if (text.isEmpty()) {
            page.toast("导出失败")
            return
        }
        val now = java.time.LocalDateTime.now()
        pendingExport = text
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            // 导出的是一份**混淆过的**文本（见 `ConfigBundle`），**不是 JSON** —— 别再报
            // `application/json`：那样系统会建议 `.json` 后缀，用户拿别的 App 一开就是一堆解码错误。
            type = "application/octet-stream"
            putExtra(
                android.content.Intent.EXTRA_TITLE,
                "OPlusKey-" + now.format(FILE_STAMP) + ".opk",
            )
        }
        try {
            activity.startActivityForResult(intent, REQ_EXPORT_CONFIG)
            XLog.w("导出配置：已弹出保存位置选择器（${text.length} 字符）")
        } catch (e: Throwable) {
            pendingExport = null
            XLog.w("导出配置：拉不起选择器：${e.message}")
            page.toast("导出失败")
        }
    }

    /**
     * 「导入配置」：弹系统的「打开」选择器（SAF），读回文件后写进配置与命令库。
     *
     * 写盘走页面那两条既有出口（`write` / `saveCommands`）—— 不自己碰文件：
     * 只读态拒绝、写盘失败回滚、写完广播给注入侧，这一整套纪律都在那两处。
     * 读回来的内容在结果回调里处理（见 [applyImportedConfig]）。
     */
    private fun importConfig(activity: android.app.Activity) {
        if (currentPage == null) {
            XLog.w("导入配置：页面没了，放弃")
            return
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            // 收 `*/*`：用户可能把导出的 .json 改过名 / 存成了 .txt，按 MIME 卡会把正常文件挡在外面
            type = "*/*"
        }
        try {
            activity.startActivityForResult(intent, REQ_IMPORT_CONFIG)
            XLog.w("导入配置：已弹出文件选择器")
        } catch (e: Throwable) {
            XLog.w("导入配置：拉不起选择器：${e.message}")
            currentPage?.toast("导入失败")
        }
    }

    /**
     * 把用户挑中的文件读进来并落地。
     *
     * ⚠️ **全量校验通过才覆写**（2026-10-14 用户定的）：整份文件在 [ConfigBundle.parse] 里过一遍，
     * 认不出 / 有任一段读不懂就**一个字节都不写**，只报一句 [UNKNOWN_CONFIG_FORMAT]。
     * 用户界面上不解释「哪一段坏了」——那是日志的事（见 [XLog]）。
     */
    private fun applyImportedConfig(uri: android.net.Uri) {
        val page = currentPage ?: return
        val text = try {
            page.context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Throwable) {
            XLog.w("导入配置：读文件失败：${e.message}")
            null
        }
        val parsed = text?.let { ConfigBundle.parse(it) }
        if (parsed == null) {
            XLog.w("导入配置：校验没过，未改动任何配置（${text?.length ?: 0} 字符）")
            page.toast(UNKNOWN_CONFIG_FORMAT)
            return
        }
        // 两段都过了**校验**才动手，但写入还要分开判：先配置（`write` 会拒只读态并 toast），
        // 配置没落盘就**一个字节都不再动命令库** —— 否则就成了「半份导入」：命令库被整份换成
        // 文件里那份、配置却还是原来的，用户看到的是「导入了」而不是「你的命令全被换掉了」。
        // （只读态下 `write` 必然被拒，这条是唯一会造成数据丢失的路径。）
        if (!page.write(parsed.config, "导入配置")) {
            XLog.w("导入配置：配置未落盘（只读态 / 写失败），命令库保持不动")
            return
        }
        val commandsOk = page.saveCommands(parsed.commands, "导入配置")
        XLog.w("导入配置：配置=true 命令库=$commandsOk")
        if (commandsOk) page.toast("已导入")
    }

    /** 收「保存到 / 打开」的结果 —— 平台 `Activity#onActivityResult`，进程内只装一次。 */
    private fun installFileResultHook(module: XposedModule) {
        if (exportResultHooked) return
        val origin = try {
            android.app.Activity::class.java.getDeclaredMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                android.content.Intent::class.java,
            )
        } catch (e: Throwable) {
            XLog.w("文件选择：找不到 Activity#onActivityResult：${e.message}")
            null
        } ?: return
        exportResultHooked = module.hookGuarded(origin) { chain ->
            val requestCode = chain.args.getOrNull(0) as? Int
            val resultCode = chain.args.getOrNull(1) as? Int
            val uri = (chain.args.getOrNull(2) as? android.content.Intent)?.data
            when (requestCode) {
                REQ_EXPORT_LOG, REQ_EXPORT_CONFIG -> {
                    writeExportFile(
                        uri = uri,
                        ok = resultCode == android.app.Activity.RESULT_OK,
                        what = if (requestCode == REQ_EXPORT_LOG) "导出日志" else "导出配置",
                    )
                    null // 自己的请求码，不再往下传
                }

                REQ_IMPORT_CONFIG -> {
                    if (resultCode == android.app.Activity.RESULT_OK && uri != null) {
                        applyImportedConfig(uri)
                    } else {
                        XLog.w("导入配置：用户取消")
                    }
                    null
                }

                else -> chain.proceed()
            }
        } != null
        XLog.w("文件选择：结果回调 hook=${exportResultHooked}")
    }

    /** 把 [pendingExport] 写进用户挑的位置。 */
    private fun writeExportFile(uri: android.net.Uri?, ok: Boolean, what: String) {
        val text = pendingExport
        pendingExport = null
        if (!ok || uri == null || text == null) {
            XLog.w("$what：用户取消")
            return
        }
        val written = try {
            val out = currentActivity?.contentResolver?.openOutputStream(uri, "wt")
            out?.use { it.write(text.toByteArray()) }
            out != null
        } catch (e: Throwable) {
            XLog.w("$what：写入失败：${e.message}")
            false
        }
        currentPage?.toast(if (written) "已导出" else "导出失败")
        if (written) XLog.w("$what：已写入（${text.length} 字符）")
    }
}
