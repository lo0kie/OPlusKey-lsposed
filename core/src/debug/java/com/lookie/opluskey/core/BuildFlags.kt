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
const val ENFORCE_DEVICE_GATE: Boolean = false

/**
 * **内置命令落点**在未适配的机器上要不要照最后一档试 —— debug 打开。
 *
 * 适配闸（[ENFORCE_DEVICE_GATE]）管「能不能注入」，这一条管「注入之后那四条内置命令（一键闪记 /
 * 免打扰 / 录音 / 小布识屏，落点见 `core/model/RomProfile`）认不认得出落点」——**同一道「认不认
 * 适配清单」的问法落在另一层上**，所以是两个常量而不是一个：那边拦的是整套注入，这边拦的只是一条命令。
 *
 * 打开（debug）：本机 ROM 不在档位表里 ⇒ 按最后一档的落点试，并打一行 `w`（一个进程一行）。
 * 关掉（release）：落点返回 `null` ⇒ 执行侧记 `w` 后跳过，**不猜一个落点去起别的服务**。
 *
 * 为什么 debug 要猜：开发时手边的机器 / ROM 版本随时在变，而这几条内置命令正是**拿来试接管点最顺手
 * 的动作**；每换一台机器就得先补一张档位表，等于把「能不能试」与「表全不全」绑死。
 */
const val GUESS_UNVERIFIED_LANDING: Boolean = true

/**
 * 首页大标题那条副标题要不要带上构建时刻（`1.0.0-debug-2026-10-15 15:07:12`）。
 *
 * debug 打开：自用包一天里会出好几个，光看版本号分不出手上这个是哪一次出的；
 * release 关掉（「更多选项」页的「版本」行不受它影响，那一行总是带，见
 * `ui/screen/HomeScreen` 的 `appVersionLabel` / `appVersionText`）。
 */
const val SHOW_BUILD_TIME: Boolean = true
