package com.lookie.opluskey

/**
 * 按构建类型各放一份的编译期常量（release 版）。
 * `VERBOSE_LOG` = false 时 `XLog.d` / `XLog.i` 被编译期消除，降级诊断 `w` / `e` / `guard` 不受影响。
 */
@PublishedApi
internal const val VERBOSE_LOG: Boolean = false

/** 构建类型标识，界面底部会显示，便于区分手上这个包是哪种。 */
internal const val BUILD_KIND: String = "release"

/**
 * 模块 **debug 通道**的总闸 —— release **必须** false。
 *
 * 打开它等于把「任意反射调用」开在 system_server / 桌面进程里，只能是 debug 包的能力；
 * 通道本身与用法见 `docs/debug-channel.md`，开关的理由见 debug 源集那份注释。
 */
internal const val DEBUG_PROBE: Boolean = false

/** 界面上的版本号带不带构建时刻 —— release 不带（`1.0.0-release`），理由见 debug 源集那份的注释。 */
@PublishedApi
internal const val SHOW_BUILD_TIME: Boolean = false
