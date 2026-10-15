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

/**
 * 适配清单（机型 + 系统版本）这道闸要不要**据此拒绝注入** —— release **必须** true。
 *
 * 打开时 [com.lookie.opluskey.core.model.DeviceSupport.check] 按清单判；关掉时一律按「已适配」
 * 走（判定本身照算、日志照打，只是不据此拒绝注入）。debug 关掉的理由见 debug 源集那份注释。
 */
internal const val ENFORCE_DEVICE_GATE: Boolean = true

/**
 * 首页大标题那条副标题带不带构建时刻 —— release 不带（`1.0.0-release`）。
 *
 * 注意它管不到「更多选项」页的「版本」行：那一行是模块自己的信息页，**总是**带构建时刻。
 * 理由见 debug 源集那份的注释。
 */
@PublishedApi
internal const val SHOW_BUILD_TIME: Boolean = false
