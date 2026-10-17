package com.lookie.opluskey.core

// beta 源集（发布用 debug）的编译期常量。
//
// 三档的差别只在这几个常量上，靠源集物理隔离保证「打包时根本不存在」，而不是运行时 if：
//
//   档      机型闸  落点允许猜  调试通道  详细日志  防篡改
//   debug   关      开          开        开        无
//   beta    关      开          关        开        有
//   release 开      关          关        关        有
//
// 换句话说：beta 与 release 只差「机型闸」这一件事 —— 给适配清单外的机器用的发布包；
// 与 debug 则差得远：调试通道必须关（那条通道等于把「任意反射执行」开在系统进程里，
// 见 DEBUG_PROBE），资产走封存与签名校验（SealPin 由 SealReleaseAssets 生成）。

/** 详细日志：beta 保留 —— 这一档的用户多半是要把日志交回来的。 */
const val VERBOSE_LOG: Boolean = true

/** 构建类型标识，界面底部会显示，便于区分手上这个包是哪种。 */
const val BUILD_KIND: String = "beta"

/**
 * 模块 **debug 通道**（`inject/ProbeChannel.kt`）的总闸。
 *
 * **beta 必须为 false**，与 release 同一条安全边界：这条通道让一条 root 广播就能在
 * `system_server` / 桌面进程里跑任意反射（列方法签名、试调用、设圆角）。
 */
const val DEBUG_PROBE: Boolean = false

/**
 * 适配清单（机型 + 系统版本）这道闸要不要**据此拒绝注入** —— beta 关掉。
 *
 * 这就是这一档存在的理由：清单外的机器照样能用，不必先等作者把那一版 ROM 验过。
 * 关掉之后「更多选项 → 适配清单」里「本机」那一行会跟着报「已适配」，
 * 那是**行为**的如实反映（这一包确实不拦），不是判定错了。
 */
const val ENFORCE_DEVICE_GATE: Boolean = false

/**
 * **内置命令落点**在未适配的机器上要不要照最后一档试 —— beta 打开。
 *
 * 与 [ENFORCE_DEVICE_GATE] 是两个问法落在两层上：那边管「能不能注入」，这边管
 * 「注入之后那四条内置命令（一键闪记 / 免打扰 / 录音 / 小布识屏）认不认得出落点」。
 * 既然这一档放开了注入，落点也得跟着放开 —— 否则闸开了、命令还是点不动，等于半残。
 */
const val GUESS_UNVERIFIED_LANDING: Boolean = true

/**
 * 首页大标题那条副标题要不要带上构建时刻。
 *
 * beta 打开：同一版会反复出包（给不同机型的人试），光看版本号分不出是哪一次出的。
 */
const val SHOW_BUILD_TIME: Boolean = true
