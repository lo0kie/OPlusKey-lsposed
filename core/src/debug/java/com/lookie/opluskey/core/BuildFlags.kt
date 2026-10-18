package com.lookie.opluskey.core

/**
 * 按构建类型各放一份的编译期常量：编译期直接折成字面量，死分支连同日志字符串一起被消掉。
 * 靠的是**源集物理隔离**：debug 实现放 `src/debug/`，release 由 `src/release/` 的同名文件顶替，
 * release 产物里根本没有这段代码 —— 不靠 `BuildConfig.DEBUG`，也不靠 R8 折死分支。
 */
const val VERBOSE_LOG: Boolean = true

/** 构建类型标识，界面底部会显示，便于区分手上这个包是哪种。 */
const val BUILD_KIND: String = "debug"

/**
 * 模块 **debug 通道**（`inject/ProbeChannel.kt`）的总闸。
 *
 * 打开后，一条 root 广播就能让注入侧在 `system_server` / 桌面进程里跑任意反射
 * （列方法签名、试调用、设圆角）—— 用来绕开「注入侧改一行就要整机重启，一轮试错几分钟」。
 * 用法见 `docs/debug-channel.md`。
 *
 * 注意：release 必须为 false：这条通道等于把「任意反射执行」开在系统进程里。
 */
const val DEBUG_PROBE: Boolean = true

/**
 * 首页大标题那条副标题要不要带上构建时刻（`1.0.0-debug-2026-10-15 15:07:12`）。
 *
 * debug 打开：自用包一天里会出好几个，光看版本号分不出手上这个是哪一次出的；
 * release 关掉（「更多选项」页的「版本」行不受它影响，那一行总是带，见
 * `ui/screen/HomeScreen` 的 `appVersionLabel` / `appVersionText`）。
 */
const val SHOW_BUILD_TIME: Boolean = true

/**
 * 开发期把「界面契约写错」直接抛出来（release 关）。
 *
 *  现在只有一处：页面骨架那条「顶栏高度」通道没被提供时 —— 静默兜底会让内容偏上一截，
 *  而这正是这段时间反复出现的那类问题，所以宁可让 debug 包当场崩。
 */
const val STRICT_UI: Boolean = true
