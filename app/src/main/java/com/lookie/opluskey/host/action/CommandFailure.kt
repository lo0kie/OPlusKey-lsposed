package com.lookie.opluskey.host.action

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.GlobalConfig

/**
 * 失败提示表：Toast 里只出现 [hint]（说清哪里不对 + 怎么办），原始信息照旧进日志
 * （按枚举名 grep）。新增一类失败就加一个分量，要按数值分流时才加映射函数。
 */
internal enum class CommandFailure(val hint: String) {
    /** `cmd:<名字>` 在命令库里找不到（被删了 / 改名了 / 文件被手改坏）。 */
    NOT_FOUND("这条命令已经不在了，去「自定义命令」里看看"),

    /** 命令是空的（单条没填内容，或多条一步都没有）。 */
    EMPTY("这条命令是空的，补上内容再试"),

    /** `cmd:` 嵌套超过 [CustomCommandRunner] 的上限（手改出来的环）。 */
    TOO_DEEP("命令之间引用得太绕了，检查一下"),

    /** `ProcessBuilder` 直接起不来（SELinux / 进程数上限 …）。 */
    SHELL_REJECTED("系统不让执行这条命令"),

    /** shell 命令超时还没结束（在这个身份下挂住了）—— 进程已被杀掉。 */
    SHELL_TIMEOUT("这条命令卡住了，已经中止"),

    /** shell 退出码 127 —— 命令本身不存在。 */
    SHELL_MISSING("找不到这个命令，检查一下拼写"),

    /** shell 退出码 126 —— 找到了但没有执行权限。 */
    SHELL_NO_PERMISSION("这条命令没有执行权限"),

    /** 其它非 0 退出码 —— 命令跑起来了但自己报错。 */
    SHELL_FAILED("命令没跑成功，检查一下内容"),

    /** `app:` 解析不到界面（包名错 / 应用已卸载）。 */
    APP_MISSING("找不到这个应用，可能已经卸载了"),

    /** `app:` 应用装着但没有可启动界面（与 [APP_MISSING] 分开，报「已卸载」会误导）。 */
    APP_NO_ENTRY("这个应用没有可启动的界面"),

    /** 小布快捷指令那几个 action 都没被系统接受。 */
    SHORTCUT_FAILED("小布快捷指令没能触发"),

    /** 兜底：执行时抛了异常，但说不上是哪一类。 */
    UNKNOWN("这次没执行成功"),

    /**
     * 小窗：这个应用**收不起来小窗**（服务端没受理，或收完没出窗口）。
     *
     * 只提示、**什么都不做** —— 不再退「按包名另起一份实例」( 用户口径)：
     * 那条会先回桌面再起，用户看到的是「一按侧键就回桌面重开」，比没反应更糟
     * （见 `docs/zoom-window.md` §2.2）。
     */
    APP_NO_MINI_WINDOW("前台应用不支持小窗"),

    /**
     * 上一条还没结束又来一条 —— 两种来源：
     *  · `shell:` 并发超上限（`ShellRunner` 那一侧的闸）；
     *  · 命令链还在跑时又触发了一条（`CustomCommandRunner` 的「一次只跑一条」闸）。
     */
    OVERLOADED("上一条命令还没跑完，稍后再试"),
    ;

    companion object {
        /**
         * shell 退出码 → 提示：只认 126 / 127（`sh` 的约定），其余非 0 一律归 [SHELL_FAILED]。
         */
        fun ofShellExit(code: Int): CommandFailure = when (code) {
            126 -> SHELL_NO_PERMISSION
            127 -> SHELL_MISSING
            else -> SHELL_FAILED
        }
    }
}

/** 把失败提示弹成 Toast。 */
internal object FailureNotice {

    /** 主线程 Handler；取不到（理论上不会）就是 null ⇒ 不弹。 */
    private val main: Handler? by lazy {
        try {
            Handler(Looper.getMainLooper())
        } catch (e: Throwable) {
            XLog.d("拿不到主线程 Looper，失败提示只记日志：${e.message}")
            null
        }
    }

    /**
     * 上一条弹过的提示与它的时刻 —— [toast] 的去重账（长按连续触发会反复派发同一句）。
     *
     * 注意：这两个字段**必须按同一把锁读写**：[toast] 不只从主线程进 ——
     * `ShellRunner` 的失败回调跑在后台线程上，两条并发进来会**同时通过**「查重 → 记账」这段
     * check-then-set，同一句话照样连弹两条。
     */
    private val dedupLock = Any()
    private var lastToastText: String? = null
    private var lastToastAt = 0L

    /** 同一句话在这么久之内不重复弹（`Toast.LENGTH_SHORT` 大约 2s，取略短的一档）。 */
    private const val TOAST_DEDUP_MS = 1500L

    /** 弹一条失败提示；context 为 null 时只记日志。 */
    fun show(context: Context?, failure: CommandFailure) {
        // 日志这一行带枚举名，是排查时的锚点
        XLog.w("执行失败：${failure.name} —— ${failure.hint}")
        toast(context, failure.hint)
    }

    /**
     * 弹一条普通提示（非失败），目前只给「执行时提示」（`GlobalConfig.toastOnRun`）用
     * —— 调用点在 `host/GestureReceiver.runAction`，内容由 [actionNoticeText] 给。
     *
     * @param context 宿主 Context；null 或空串时什么都不做。
     */
    fun toast(context: Context?, text: String) {
        if (context == null || text.isEmpty()) return
        // 同一句话短时间内的重复派发只弹一次：长按连续触发按「触发间隔」反复执行同一条命令，
        //  不去重就是一条接一条排队刷屏。
        //  查重与记账在同一把锁里（跨线程，见 [dedupLock]）。
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(dedupLock) {
            if (text == lastToastText && now - lastToastAt < TOAST_DEDUP_MS) return
            lastToastText = text
            lastToastAt = now
        }

        val handler = main ?: return
        try {
            handler.post {
                try {
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    XLog.d("弹 Toast 失败：${e.message}")
                }
            }
        } catch (e: Throwable) {
            XLog.d("安排 Toast 失败：${e.message}")
        }
    }
}
