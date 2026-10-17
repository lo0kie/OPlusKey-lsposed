package com.lookie.opluskey.core

/**
 * 按构建类型各放一份的编译期常量（release 版）。
 * `VERBOSE_LOG` = false 时 `XLog.d` / `XLog.i` 被编译期消除，降级诊断 `w` / `e` / `guard` 不受影响。
 */
const val VERBOSE_LOG: Boolean = false

/** 构建类型标识，界面底部会显示，便于区分手上这个包是哪种。 */
const val BUILD_KIND: String = "release"

/**
 * 模块 **debug 通道**的总闸 —— release **必须** false。
 *
 * 打开它等于把「任意反射调用」开在 system_server / 桌面进程里，只能是 debug 包的能力；
 * 通道本身与用法见 `docs/debug-channel.md`，开关的理由见 debug 源集那份注释。
 */
const val DEBUG_PROBE: Boolean = false

/**
 * 首页大标题那条副标题带不带构建时刻 —— release 不带（`1.0.0-release`）。
 *
 * 注意它管不到「更多选项」页的「版本」行：那一行是模块自己的信息页，**总是**带构建时刻。
 * 理由见 debug 源集那份的注释。
 */
const val SHOW_BUILD_TIME: Boolean = false

/**
 * 开发期把「界面契约写错」直接抛出来（release / beta 关）。
 *
 *  现在只有一处：页面骨架那条「顶栏高度」通道没被提供时 —— 静默兜底会让内容偏上一截，
 *  而这正是这段时间反复出现的那类问题，所以宁可让 debug 包当场崩。
 */
const val STRICT_UI: Boolean = false
