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
 * 注意：release 必须为 false：这条通道等于把「任意反射执行」开在系统进程里。
 */
internal const val DEBUG_PROBE: Boolean = true

/**
 * 适配清单（机型 + 系统版本）这道闸要不要**据此拒绝注入** —— debug 关掉。
 *
 * 判定的代码路径留着（`DeviceSupport.check` 照样算，日志里机型 / 系统那两段照打），
 * 只是不再拿它拒绝注入 —— 开发时机型 / 系统版本随时在变（手边换台机器、ROM 升一版），
 * 开机先被这道闸拦下来只会让人以为模块坏了。
 *
 * 注意：关掉之后「更多选项 → 适配清单」里「本机」那一行也会跟着报「已适配」——
 * 那是**行为**的如实反映（这一包确实不拦），不是判定错了。
 *
 * release 必须打开：清单外注入正是要防的事（见 `core/model/DeviceSupport` 的文件头）。
 */
internal const val ENFORCE_DEVICE_GATE: Boolean = false

/**
 * 首页大标题那条副标题要不要带上构建时刻（`1.0.0-debug-2026-10-15 15:07:12`）。
 *
 * debug 打开：自用包一天里会出好几个，光看版本号分不出手上这个是哪一次出的；
 * release 关掉（「更多选项」页的「版本」行不受它影响，那一行总是带，见
 * `ui/screen/HomeScreen` 的 `appVersionLabel` / `appVersionText`）。
 */
@PublishedApi
internal const val SHOW_BUILD_TIME: Boolean = true
