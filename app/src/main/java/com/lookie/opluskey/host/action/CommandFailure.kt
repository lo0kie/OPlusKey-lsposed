package com.lookie.opluskey.host.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire

/**
 * 失败提示表：Toast 里只出现 [hint]（说清哪里不对 + 怎么办），原始信息照旧进日志
 * （按枚举名 grep）。新增一类失败就加一个分量，要按数值分流时才加映射函数。
 */
internal enum class CommandFailure(
    val key: String,
    val fallback: String,
) {
    /** `cmd:<名字>` 在命令库里找不到（被删了 / 改名了 / 文件被手改坏）。 */
    NOT_FOUND(Strings.COMMAND_FAILURE_NOT_FOUND, "命令已不存在"),

    /** 命令是空的（单条没填内容，或多条一步都没有）。 */
    EMPTY(Strings.COMMAND_FAILURE_EMPTY, "这条命令是空的，补上内容再试"),

    /** `cmd:` 嵌套超过 [CustomCommandRunner] 的上限（手改出来的环）。 */
    TOO_DEEP(Strings.COMMAND_FAILURE_TOO_DEEP, "命令互相引用太深"),

    /** `ProcessBuilder` 直接起不来（SELinux / 进程数上限 …）。 */
    SHELL_REJECTED(Strings.COMMAND_FAILURE_SHELL_REJECTED, "系统不让执行这条命令"),

    /** shell 命令超时还没结束（在这个身份下挂住了）—— 进程已被杀掉。 */
    SHELL_TIMEOUT(Strings.COMMAND_FAILURE_SHELL_TIMEOUT, "这条命令卡住了，已经中止"),

    /** shell 退出码 127 —— 命令本身不存在。 */
    SHELL_MISSING(Strings.COMMAND_FAILURE_SHELL_MISSING, "系统里找不到这条命令（检查拼写或路径）"),

    /** shell 退出码 126 —— 找到了但没有执行权限。 */
    SHELL_NO_PERMISSION(Strings.COMMAND_FAILURE_SHELL_NO_PERMISSION, "这条命令要更高权限：检查文件执行位，或给「手势体感」授权"),

    /**
     * 起不了**选定的**那个 shell 解释器 —— 最常见的原因是**没有 root 授权**。
     *
     *  未授权时 KernelSU 直接让 `/system/bin/su` **不可见**（`sh` 报 `inaccessible or not found`），
     *  而宿主所在的 `platform_app` 域又没有 `/system/bin/sh` 的执行权 ⇒ 两条路各自都断，
     *  所以这里没有「换另一个解释器再试一次」这种退路（见 `ShellRunner.startProcess`）。
     *  提示里只说**宿主**「手势体感」：命令跑在宿主进程里，授权要授给那个包，不是模块自己的包
     *  （真机实测两条授权互不影响，见 `docs/runtime/key-to-gesture.md`）。
     */
    SHELL_NO_ROOT(Strings.COMMAND_FAILURE_SHELL_NO_ROOT, "「手势体感」没有 root 权限"),

    /**
     * 命令自己**没勾**「以 root 执行」，而它要 root 才跑得起来。
     *
     *  与 [SHELL_NO_ROOT] 分开是因为**该做的事完全不同**：这一档去「命令设置」里把「以 root 执行」
     *  打开就行，`root` 早就在了；那一档才是去 root 管理器给宿主授权。
     *  两者按「先看命令的开关，再看机器有没有 root」的顺序判（见 `ShellRunner.classifyFailure`）。
     */
    SHELL_ROOT_OFF(Strings.COMMAND_FAILURE_SHELL_ROOT_OFF, "此命令需要 root 权限"),

    /** 其它非 0 退出码 —— 命令跑起来了但自己报错。 */
    SHELL_FAILED(Strings.COMMAND_FAILURE_SHELL_FAILED, "命令没跑成功"),

    /** `app:` 解析不到界面（包名错 / 应用已卸载）。 */
    APP_MISSING(Strings.COMMAND_FAILURE_APP_MISSING, "找不到这个应用，可能已经卸载了"),

    /** `app:` 应用装着但没有可启动界面（与 [APP_MISSING] 分开，报「已卸载」会误导）。 */
    APP_NO_ENTRY(Strings.COMMAND_FAILURE_APP_NO_ENTRY, "这个应用没有可启动的 Activity"),

    /** 小布快捷指令那几个 action 都没被系统接受。 */
    SHORTCUT_FAILED(Strings.COMMAND_FAILURE_SHORTCUT_FAILED, "小布快捷指令没能触发"),

    /** 兜底：执行时抛了异常，但说不上是哪一类。 */
    UNKNOWN(Strings.COMMAND_FAILURE_UNKNOWN, "这次没执行成功"),

    /**
     * 小窗：这个应用**收不起来小窗**（服务端没受理，或收完没出窗口）。
     *
     * 只提示、**什么都不做** —— 不再退「按包名另起一份实例」( 用户口径)：
     * 那条会先回桌面再起，用户看到的是「一按侧键就回桌面重开」，比没反应更糟
     * （见 `docs/features/zoom-window/notes.md` §2.2）。
     */
    APP_NO_MINI_WINDOW(Strings.COMMAND_FAILURE_APP_NO_MINI_WINDOW, "前台应用不支持小窗"),

    /**
     * 上一条还没结束又来一条 —— 两种来源：
     *  · `shell:` 并发超上限（`ShellRunner` 那一侧的闸）；
     *  · 命令链还在跑时又触发了一条（`CustomCommandRunner` 的「一次只跑一条」闸）。
     */
    OVERLOADED(Strings.COMMAND_FAILURE_OVERLOADED, "上一条命令还没跑完，稍后再试"),

    /**
     * 免打扰（AOSP 接口那一档）：宿主没有「勿扰访问」授权。
     *
     *  `ACCESS_NOTIFICATION_POLICY` 装包时就 granted，但那只是权限位；真正管用的是
     *  appop（真机上 `enabled_notification_policy_access_packages` 是空的 ⇒ 没授）。
     *  没授时 `setZenMode` 抛 `SecurityException`，并进 [UNKNOWN] 只会说「这次没执行成功」，
     *  用户不知道要去哪儿开。这一档专门说清「缺的是哪一项授权」。
     */
    DND_NOT_GRANTED(Strings.COMMAND_FAILURE_DND_NOT_GRANTED, "免打扰缺「勿扰权限」：设置 → 通知与状态栏 → 勿扰，允许「手势体感」"),
    ;

    open val hint: String
        get() = I18n.get(key, fallback)

    companion object {
        /**
         * shell 退出码 → 提示：只认 126 / 127（`sh` 的约定），其余非 0 一律归 [SHELL_FAILED]。
         */
        fun ofShellExit(code: Int): CommandFailure =
            when (code) {
                126 -> SHELL_NO_PERMISSION
                127 -> SHELL_MISSING
                else -> SHELL_FAILED
            }
    }
}

/**
 * 「这个包还在不在」—— 解析不到界面 / 拉起被拒时**报哪一档失败**的判据。
 *
 *  判据本身只有一句：`getApplicationInfo` 问得到就是还在。但有两个坑写在调用点上：
 *  · **分身要用那个用户自己的 PM**：同包名在 0 与 998/999 里各有一份，拿主用户 PM 去问，
 *    答的是另一份的事 —— 调用方要把 [AppCloneCatalog.packageManagerFor] 的结果传进来，
 *    别拿「主用户 PM 兜底」那一份来问分身；
 *  · 显式组件**被停用 / 组件不存在**时抛的也是 `ActivityNotFoundException`，一律报
 *    [CommandFailure.APP_MISSING] 会把用户带偏 —— 包明明还在。
 *
 *  分身拿不到自己那份 PM 时（`createContextAsUser` 在宿主上必失败）**保持现状、按「还在」算**：
 *  报「可能已卸载」是拿一个问不到的答案当结论。这一档记 `d`（它每次开机都可能来）。
 *
 *  `runCatching` 在这里表达的是「预期内的失败 = 没找到」，不是「保护宿主」（那一类用 `XLog.guard`）。
 */
internal fun appInstalled(
    pm: PackageManager?,
    pkg: String,
    userId: Int,
): Boolean {
    if (pm == null) {
        if (userId != 0) {
            XLog.d("appInstalled：分身 user=$userId 没有该用户的 PM，按「还在」处理（保持现状）")
            return true
        }
        return false
    }
    return runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess
}

/** 上面那个布尔 → 失败档位：「还在」= 没有可用入口，「不在」= 可能已卸载。 */
internal fun entryFailure(installed: Boolean): CommandFailure = if (installed) CommandFailure.APP_NO_ENTRY else CommandFailure.APP_MISSING

/**
 * 把失败提示弹出来。
 *
 *  提示**不走本地 `Toast`**（那个受系统的按包限流）：统一交给注入侧的 [requestSystemToast]。
 *  去重也一并交给它那一侧（`inject/SystemToast` 的 1 秒同句去重），这里不再自己记账。
 */
internal object FailureNotice {
    /** 弹一条失败提示；context 为 null 时只记日志。 */
    fun show(
        context: Context?,
        failure: CommandFailure,
    ) {
        // 日志这一行带枚举名，是排查时的锚点
        XLog.w("执行失败：${failure.name} —— ${failure.hint}")
        toast(context, failure.hint)
    }

    /**
     * 弹一条普通提示（非失败）：「执行时提示」（`GlobalConfig.toastOnRun`）、命令自己那行 `tip:`、
     * 以及各类失败原因，全都从这里出去。
     *
     *  **不再本地 `Toast`：一律请注入侧替我们弹**（[requestSystemToast]）。宿主自己的包受系统
     *  的按包限流（3 条 / 20 秒、5 条 / 42 秒、6 条 / 68 秒，见 `docs/device/actions/feedback.md` 四十），
     *  连按几下之后后面那些被 `NotificationService` 直接丢掉 —— 真机表现就是「执行了但没有提示」。
     *  系统自己（包名 `android`）持有 `UNLIMITED_TOASTS`，改由它发话这条路已经现成
     *  （`inject/SystemToast`，命令里 `tip:` 一直走的就是它）。
     *
     * @param context 宿主 Context；null 或空串时什么都不做。
     */
    fun toast(
        context: Context?,
        text: String,
    ) {
        if (context == null || text.isEmpty()) return
        requestSystemToast(context, text)
    }
}

/**
 * 请注入侧（`system_server`）替我们弹这条提示 —— 全模块**唯一的提示出口**。
 *
 *  收方是注入侧挂在 AMS 广播入口上的 hook（`inject/KeyHooks.readBroadcast` → `inject/SystemToast`），
 *  它用系统 Context（包名 `android`，免限流）弹；它自己还带一层 1 秒去重（长按连发时同一句
 *  不刷屏）。hook 挂在 AMS 入口上、在那之前就看见整条 intent，所以 `setPackage` **不影响**它收。
 *
 *  **必须 `setPackage`**：这句话里有用户的应用名 / 命令名，裸着发就是一条谁都能注册接收的隐式广播
 *  （任意应用写个同名 action 的 receiver 就能读到）。
 *
 *  发不出去（没有 Context / 广播被拒）只记一行：提示丢了不报错 —— 它本来就是「顺带说一句」，
 *  不该因为它把执行路径的意思反过来说成失败。
 */
internal fun requestSystemToast(
    context: Context?,
    text: String,
) {
    if (context == null || text.isEmpty()) return
    runCatching {
        context.sendBroadcast(
            Intent(ModuleWire.ACTION_TOAST)
                .setPackage(HostApp.PACKAGE)
                .putExtra(ModuleWire.EXTRA_TOAST_TEXT, text),
        )
    }.onFailure { XLog.w("提示没发出去（注入侧弹不了）：${it.javaClass.simpleName}", it) }
}
