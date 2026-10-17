package com.lookie.opluskey.core.model

/**
 * 小窗（ColorOS 柔性窗口）**跨进程对接**用的那几组取值 —— 一处定义，三侧共用。
 *
 * 为什么必须收在一处：这几个名字与数字要在**不同的进程**里逐字对得上 ——
 *
 * ```
 *   宿主主进程（发送）  host/action/MiniWindowLauncher · host/action/MiniWindowSupport
 *   诊断探针           inject/ProbeChannel
 *   system_server      bridge/FlexibleWindowBridge
 * ```
 *
 * 收发两端各写一遍时，改一边漏一边的**表现是「小窗指令收到但形状不对 / 没反应」**，而两边各自
 * 都编得过、日志也各自自洽 —— 这类错在真机上只能靠猜（`AGENTS.md` 五、2 要的是「断在哪一环」，
 * 名字对不上时连断在哪一环都说不出来）。取值本来就在 `core/model` 里放得下，没必要各写一份。
 *
 * 每个取值都是**平台事实**（`OplusZoomWindowManager` 与 `ActivityOptions` 的接口），不是本模块的
 * 选择：改它们等于改对接对象，必须回到 `docs/device-findings.md` 的尺寸档对照表重新核一遍。
 */
internal object ZoomWindowWire {
    /**
     * `ActivityOptions` setter 里「窗口模式」那个 key —— **点号**形态。
     *
     * 与 [EXTRA_WINDOW_MODE_KEY] 同义不同形（那是 `Bundle` 直接塞给
     * `OplusZoomWindowManager` 时用的下划线形态），两个都要有，别把其中一个当成另一个的笔误。
     */
    const val LAUNCH_WINDOWING_MODE_KEY = "android.activity.windowingMode"

    /**
     * `ActivityOptions` setter 里尺寸档那个 key。
     *
     * 服务端据此分流：[LAUNCH_FLAG_EXPANDED] 走 `setUserPositionInfo`、
     * [LAUNCH_FLAG_COLLAPSED] 走 `fullToMini`。
     */
    const val ZOOM_LAUNCH_FLAGS_KEY = "android:activity.mZoomLaunchFlags"

    /** 直接递给 `OplusZoomWindowManager` 的那个 `Bundle` 里「窗口模式」的 key（下划线形态）。 */
    const val EXTRA_WINDOW_MODE_KEY = "extra_window_mode"

    /** [EXTRA_WINDOW_MODE_KEY] 取这个值才是小窗 —— `OplusZoomWindowManager.WINDOWING_MODE_ZOOM`。 */
    const val WINDOWING_MODE_ZOOM = 100

    /** [ZOOM_LAUNCH_FLAGS_KEY] 的**展开**档：居中的普通小窗，沿用上次的位置与尺寸。 */
    const val LAUNCH_FLAG_EXPANDED = 2

    /** [ZOOM_LAUNCH_FLAGS_KEY] 的**收起**档：右上角一条的最小化窗（几何由服务端自己算）。 */
    const val LAUNCH_FLAG_COLLAPSED = 32
}
