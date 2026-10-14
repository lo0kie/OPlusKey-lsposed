package com.lookie.opluskey

/**
 * 按构建类型各放一份的编译期常量：编译期直接折成字面量，死分支连同日志字符串一起被消掉。
 * 不能用 `BuildConfig.DEBUG` 等价替代 —— proguard 规则 `-keep` 了自己的类，R8 不会折死分支。
 */
@PublishedApi
internal const val VERBOSE_LOG: Boolean = true

/** 构建类型标识，界面底部会显示，便于区分手上这个包是哪种。 */
internal const val BUILD_KIND: String = "debug"

/**
 * 模块 **debug 通道**（`inject/system/ProbeChannel.kt`）的总闸。
 *
 * 打开后，一条 root 广播就能让注入侧在 `system_server` / 桌面进程里跑任意反射
 * （列方法签名、试调用、设圆角）—— 用来绕开「注入侧改一行就要整机重启，一轮试错几分钟」。
 * 用法见 `docs/debug-channel.md`。
 *
 * ⚠️ release 必须为 false：这条通道等于把「任意反射执行」开在系统进程里。
 */
internal const val DEBUG_PROBE: Boolean = true

/**
 * 界面上的版本号要不要带上构建时刻（`1.0.0-debug-183000`）。
 *
 * debug 打开：自用包一天里会出好几个，光看版本号分不出手上这个是哪一次出的；
 * release 关掉（参数名与用法见 `ui/screen/HomeScreen.kt` 的 `appVersionLabel`）。
 */
@PublishedApi
internal const val SHOW_BUILD_TIME: Boolean = true
