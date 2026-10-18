package com.lookie.opluskey.host.action

import com.lookie.opluskey.core.model.MiniWindowGeometry
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.core.model.ZoomWindowWire

/**
 * 小窗拉起要用的反射面与常量 —— 从 [MiniWindowLauncher] 的 companion 拆出来。
 *
 *  理由只有一条：那是两百多行纯事实（类名 / 方法名 / 字段名 / 时序常量），与「怎么拉起」的
 *  逻辑混在一个文件里时，找一条时序常量要先翻过一整套反射调用。取值来源没有变
 *  （`RomProfile` 与 `MiniWindowGeometry`）。
 */
internal object MiniWindowFacts {
    /** 本机这一档 ROM 的反射面 —— 未适配的 ROM 照最后一档试（见 [RomProfile]）。 */
    val ROM = RomProfile.effective.reflection

    /** ColorOS 小窗接口的类名 —— 按名字反射，不直接引用（注入侧不许碰宿主框架的私有类型）。 */
    val ZOOM_MANAGER = ROM.zoomManager

    /** `OplusZoomWindowManager#getCurrentZoomWindowState()` → `OplusZoomWindowInfo`。 */
    val CURRENT_ZOOM_STATE_METHOD = ROM.zoomStateMethod

    /** `OplusZoomWindowInfo` 上那个**屏幕矩形**字段（乘过 scale 的；不是 `appBounds`）。 */
    val ZOOM_STATE_RECT_FIELD = ROM.zoomStateRectField

    /** `startZoomWindow` 的形参个数：`Intent, Bundle, int, String`。 */
    const val ZOOM_ARGS = 4

    /**
     * Bundle 里指定窗口模式的 key —— `OplusZoomWindowManager.EXTRA_WINDOW_MODE`。
     *
     *  取值收在 `core/model/ZoomWindowWire` 一处：同一个 key 还在诊断探针（`inject/ProbeChannel`）
     *  与状态门面（[MiniWindowSupport]）里用，各写一遍时改一处漏一处只会表现为「探针试出来能行、
     *  真正执行时不行」。
     */
    const val ZOOM_EXTRA_WINDOW_MODE = ZoomWindowWire.EXTRA_WINDOW_MODE_KEY

    /** 上面那个 key 取这个值才是小窗 —— `OplusZoomWindowManager.WINDOWING_MODE_ZOOM`。 */
    const val WINDOWING_MODE_ZOOM = ZoomWindowWire.WINDOWING_MODE_ZOOM

    /** 取各屏前台应用信息：`OplusActivityManager#getAllTopAppInfos()` → `List<OplusAppInfo>`。 */
    val TOP_APP_INFOS_METHOD = ROM.zoomTopAppInfosMethod

    /** `OplusAppInfo` 的字段名（按名字取，不引宿主框架的类）。 */
    const val TOP_APP_ACTIVITY_FIELD = "topActivity"

    const val TOP_APP_TASK_ID_FIELD = "taskId"

    const val TOP_APP_WINDOWING_MODE_FIELD = "windowingMode"

    const val TOP_APP_PACKAGE_FIELD = "packageName"

    /**
     * `OplusAppInfo.topActivity` 若是 `ComponentInfo` 形状，类名在这个字段里。
     * 只有拿得到类名才查得到 `ActivityInfo.resizeMode`（那是 activity 级属性），见 [componentOf]。
     */
    const val TOP_APP_CLASS_FIELD = "className"

    /** `ActivityInfo` 上的尺寸可调性字段 —— 编译期 stub 里没有，只能按名字反射读。 */
    val RESIZE_MODE_FIELD = ROM.resizeModeField

    /**
     * `ActivityInfo.RESIZE_MODE_UNRESIZEABLE` 的值（= 0）。不引常量是因为它和 [RESIZE_MODE_FIELD]
     * 一样不在编译期 stub 里；真机上 `com.heytap.market` 读出来的就是这个值。
     */
    const val RESIZE_MODE_UNRESIZEABLE = 0

    /**
     * 「不可调尺寸（`resizeMode == 0`）的应用**直接不进小窗**」开关 —— **默认关**。
     *
     * 注意：真机教训：这条判据**太宽**，`resizeMode == 0` 不只命中
     * 「转换会失败的 app」，它把**所有游戏**都命中了：
     *
     * ```
     * com.tencent.tmgp.supercell.clashofclans/…GameAppTencent **不可调尺寸**（resizeMode=0）
     * com.happyelements.AndroidAnimal/…SplashActivity      **不可调尺寸**（resizeMode=0）
     * ```
     *
     * 真机上游戏**是能进小窗的**（只是横屏时尺寸会偏大，见 [animGeometryMatchesOrientation]），
     * 所以打开这个开关会导致「横屏的游戏一按下去什么都不发生」。
     *
     * 想开它的人要清楚代价：**它拦的是"所有 resizeMode=0"，不是"转换会失败的那些"** ——
     * 后者只能靠「转换完 350ms 复查 `windowingMode`」那条事后判据（[verifyMiniOrFallback]），
     * 或者把判据换成框架自己的 `FlexibleTaskController` 结论（要改注入侧、要重启）。
     */
    const val MINI_SKIP_UNRESIZEABLE = false

    /**
     * `OplusAppInfo.appBounds` —— 小窗的**应用配置矩形**（不含显示缩放）。
     *
     * 两档小窗都是 `1272×2261`，所以它**分不出档位**；它验的是「预置几何有没有被吃住」，
     * 见 [taskZoomConfigRatio]。
     */
    const val TOP_APP_BOUNDS_FIELD = "appBounds"

    /**
     * 配置宽低于它 × 屏宽，就认为预置几何**没被吃住**（配置矩形被喂小了）。
     *
     * 正常值 ≈ `1.0`（配置宽 = 满屏宽 1272）；那个多缩一次的 bug 读到 `0.80`。
     * 留 5% 余量，免得机型/边距差异误报。
     */
    const val ZOOM_CONFIG_WIDTH_MIN_RATIO = 0.95f

    /**
     * 展开档小窗的**应用配置矩形**与显示缩放 —— 随小窗指令下发给注入侧，供**伪造态**那条路用。
     *
     * 注意：默认路径（recents 正门）**不看这两个值** —— 它的落点由 `setUserPositionInfo` 沿用
     * **系统记住的位置**（= 用户上次的落点）。所以别以为改这里能挪默认路径的窗口。
     *
     *
     * 注意：**要调几何去改 `core/model/MiniWindowGeometry`**（按机型标定那张表，宿主与注入两侧
     * 都从它取）—— 宿主侧改完 `kill` 掉 `com.oplus.gesture` 就生效、**不需要整机重启**，
     * 这正是把它们从注入侧搬出来的原因（见 `FlexibleWindowBridge.EXTRA_LEFT` 一段的说明）。
     *
     * 语义（真机实测，`docs/device-findings.md` 附七）：
     * 这是**配置矩形**不是屏幕矩形，`屏幕尺寸 = Rect.size × scale`：
     * `1272 × 2261 × 0.8017 = 1020 × 1812`；`left/top` 不参与缩放、直接照搬屏幕坐标。
     * 值随机型走（表里一台机器一个 `Slot`），加机型要在真机上重新量。
     */
    val EXPANDED_CONFIG_BOUNDS: android.graphics.Rect get() = MiniWindowGeometry.effective.bounds

    val EXPANDED_SCALE: Float get() = MiniWindowGeometry.effective.scale

    /**
     * 动画起点：**全屏外观** —— `startScale=1.0` 配 `startRect` 的 `(0,0)` 就是恒等变换，
     * 起来那一帧和用户按下去前看到的画面完全一致。
     *
     * 真机确认（用户亲眼所见）：动画可见且**可用** —— 全屏应用向屏幕中心缩成小窗。
     * 之所以能确定每帧 transform 真的作用在画面那层 surface 上，靠的是一次诊断：
     * 把这里临时改成 `0.2f`，窗口就从**左上角**冒出来再移到中间 ——
     * 缩放绕的是 surface 自己的原点，所以缩小版停在左上角，正好证明改的就是画面那块
     * （⇒ 不需要 `Builder.setAnimationLeash` 自己建 leash）。
     */
    const val MINI_ANIM_START_SCALE = 1.0f

    const val MINI_ANIM_START_ALPHA = 1.0f

    /**
     * 动画终点的不透明度。设成 0 就是「边缩边淡出」；现行给 1.0（不淡出）——
     * 落点精确（先真转换再播），收尾没有跳变要盖。
     */
    const val MINI_ANIM_FINISH_ALPHA = 1.0f

    /**
     * 交给框架的时长。注意：spring 档位的实际收敛时长由**阻尼比 / 刚度**决定
     * （框架写死 `0.86 / 250`），这个值主要进日志。
     */
    const val MINI_ANIM_DURATION_MS = 400L

    /**
     * 真转换之后等多久再启动动画。**0 = 立刻**。
     *
     * 现在用的那条入口（`startFlexibleSpringAnimation`）**没有** `isInTransition` 过滤，所以
     * 转换完立刻播是安全的；留这个旋钮只是给「真机上观感不对」时一个不用改注入侧的退路。
     */
    const val MINI_ANIM_AFTER_CONVERT_DELAY_MS = 0L

    /**
     * 展开档走哪条路：`false`（默认）= 应用侧 **recents 正门**；`true` = `system_server` 伪造态。
     *
     * 为什么默认 `false` —— 真机实测，伪造态那条开出来的窗口是**半成品**：
     * 几何对、档位对，但**没有圆角、没有操作条 / ⋮ 菜单、点一下会被当成拖动**，应用内容还叠两份。
     * 根因是框架的「柔性任务注册」那一步没做（操作条视图 / 圆角 / 拖拽与输入都配在那里），
     * 而伪造态只捅了三个状态字段 + `switchState`。
     *
     * 留着它是因为它是目前唯一能「看起来」拿到**原生动画**的路 —— 但复测把它也判死了，
     * 见下面那段。**不要**在没有新证据前打开它。
     *
     * 注意：复测结论（`device-findings.md` 附十一 / 附十二）：**这条也走不通，而且不是
     * 「缺几个调用」——它压根没真转换任务**。真机日志：`switchState` 之后
     * `onTaskInfoChanged taskid 125 … mWindowingMode=fullscreen` **自始至终是全屏**，
     * 只是把 surface 缩到 `scale=0.8014` 又画了一条操作条 —— 一个「穿着小窗外衣的全屏任务」。
     * 后果就是历史记录里那三条，且都有根因：
     *  · `handleButtonRegion = Rect(0,0-0,0)`（柔性任务注册那步没做）→ 框架退化成
     *    「**整个窗口都是拖拽把手**」→ 点一下就成拖动，一拖就 `exitFlexibleTaskWindowInnerLocked
     *    exitTo:6` 把小窗甩掉、任务退回全屏；`updateTouchRegion()` 补不上它。
     *  · 没圆角 / 没菜单，也同源。
     * 补壳子（`corner` / 操作条）能改善观感，但改不了「它不是真小窗」这个事实。
     */
    const val USE_FORGED_ANIMATION_ROUTE = false

    /** 「原地收成小窗」那支挂在 AIDL 门面 `OplusActivityTaskManager` 上，不在 `OplusActivityManager`。 */
    val ZOOM_TASK_MANAGER = ROM.zoomTaskManager

    /** `startActivityFromRecentsForFlexible(int taskId, Bundle options)` 及其形参个数。 */
    val RECENTS_FLEXIBLE_METHOD = ROM.zoomRecentsMethod

    val RECENTS_FLEXIBLE_ARGS = ROM.zoomRecentsArgs

    /** `ActivityOptions#fromBundle` 认的窗口模式 key —— **点号**，与 `extra_window_mode` 不同。 */
    const val OPT_LAUNCH_WINDOWING_MODE = ZoomWindowWire.LAUNCH_WINDOWING_MODE_KEY

    /** `mZoomLaunchFlags` 在 options 里的 key。 */
    const val OPT_ZOOM_LAUNCH_FLAGS = ZoomWindowWire.ZOOM_LAUNCH_FLAGS_KEY

    /** `mZoomLaunchFlags = 2`（服务端 `setUserPositionInfo`）：展开的普通小窗，沿用上次的位置与尺寸。 */
    const val ZOOM_LAUNCH_FLAG_USER_POSITION = ZoomWindowWire.LAUNCH_FLAG_EXPANDED

    /**
     * `mZoomLaunchFlags = 32`（服务端 `fullToMini` → `getMiniFlexibleTaskInfo`）：
     * 收起的最小化窗（右上角一条）。取值见 `docs/device-findings.md` §17.6.3 的尺寸档对照表。
     */
    const val ZOOM_LAUNCH_FLAG_FULL_TO_MINI = ZoomWindowWire.LAUNCH_FLAG_COLLAPSED

    /**
     * 起完等多久再核「有没有真变成小窗」+「窗口落在屏幕内」。
     *
     * 注意：**不能太早**（真机踩过）：收小窗那一下会先落一个**过渡帧**，约 600ms 后
     * 才定成最终几何。横屏 bilibili 实测：
     *
     * ```
     * +0ms    Rect(119, 730 - 1152, 1311)   scale=0.46116072   ← 过渡帧，1033×581，在屏幕内
     * +591ms  Rect(141, 266 - 1457, 1007)   scale=0.5875       ← 最终态，1316×741，右侧溢出
     * ```
     *
     * 按 350ms 去核会读到过渡帧，把「最后会溢出」的窗口报成「落在屏幕内」—— 又是一条假绿灯。
     */
    const val MINI_VERIFY_MS = 700L

    /**
     * **冷启动**时验收最多再等几轮（每轮 [MINI_VERIFY_MS]）。
     *
     *  只有一种情形会用到它：到点时目标包**连任务都还没有**（`foregroundTaskFor` 读到 -1）。
     *  最近任务里根本没这个包，说明应用正在冷启动，判「options 没被吃住」是误判 ——
     *  照原样降级会在 700ms 后再起一次全屏，把刚开好的小窗顶掉（真机症状：小窗闪一下就没了）。
     *
     *  两轮（合计 ≈2.1s）够微信用冷启动起一个界面；再多就是「真的不会成」了，降级反而拖时间。
     */
    const val SHORTCUT_VERIFY_RETRIES = 2

    /** `START_DELIVERED_TO_TOP`：intent 被交给栈顶那个活动消化，屏幕不会有任何变化。 */
    const val START_DELIVERED_TO_TOP = 3

    /** 上面那档时先回桌面，等桌面过渡铺开再起小窗（毫秒）。 */
    const val HOME_SETTLE_MS = 400L
}
