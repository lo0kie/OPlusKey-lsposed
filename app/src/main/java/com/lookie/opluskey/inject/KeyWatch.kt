package com.lookie.opluskey.inject

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.wire.ModuleWire

/**
 * 「添加按键」页要的那条**旁路监听**：把按下的键原样报回宿主。
 *
 * ## 为什么必须在这里做
 *
 *  界面拿不到按键：设置页是宿主 Activity 里的覆盖层，焦点在宿主控件上；音量 / 电源这类更是
 *  在应用之前就被系统处理掉了。**只有注入侧看得见每一颗键**。
 *
 * ## 平时只读，收集时吞键
 *
 *  [report] 只复制一份事件发回去，**不改**认领 / 放行判定；真正决定吞不吞的是调用方
 *  （`SideKeyInterceptor`）—— 它看 [active]：监听期间**能收的键一律吞掉**，否则用户按的那颗键
 *  会照常触发系统动作（按音量就真的调音量、按返回就真的退出去），收集这一页等于没法用。
 *
 *  收集期**一律吞**：弹窗是模态的，这时按任何键都是在报键 —— 不吞的话按音量真的调音量、
 *  按电源真的弹菜单（还会把弹窗盖掉）。音量±与电源现在**允许被收集**
 *  （见 [KeyId.RESERVED_CODES]），剩下那几颗收不下的（主屏幕 / 返回 / 媒体 / 截图…）
 *  会 toast 一句原因。影响上限由 [AUTO_OFF_MS] 兜着，弹窗一关就恢复。
 *
 * ## 自己会关
 *
 *  页面退出、进程被杀、广播丢了都可能让「关」那条指令落空，所以带一个 [AUTO_OFF_MS] 的兜底：
 *  到期自动当作关掉，绝不会永久往外发键码。
 */
internal object KeyWatch {
    /** 打开后最多监听这么久（页面正常退出会提前关掉）。 */
    private const val AUTO_OFF_MS = 60_000L

    /** 注入侧借到的 Context（推迟到真要发的时候取，理由同 `GestureForwarder`）。 */
    @Volatile
    private var context: (() -> Context?)? = null

    /** 到期时间（`elapsedRealtime`）；0 = 关着。 */
    @Volatile
    private var until = 0L

    /** 现在是不是在监听（含超时判定）。 */
    private val listening: Boolean
        get() = until > 0L && SystemClock.elapsedRealtime() < until

    /** 监听是否真的开着 —— 认领那条路据此决定「吞不吞这一下」（见类注释）。 */
    val active: Boolean
        get() = listening

    /** 由安装入口递进来一个「要用时才取」的 Context。 */
    fun install(borrow: () -> Context?) {
        context = borrow
    }

    /** 上报用的主线程队列（见 [report]：InputReader 线程上不做同步广播）。 */
    private val handler = Handler(Looper.getMainLooper())

    /** 设置页发来的开 / 关。 */
    fun set(on: Boolean) {
        until = if (on) SystemClock.elapsedRealtime() + AUTO_OFF_MS else 0L
        // 开关是低频动作，用 w 当判据（`d` 在 release 里被编译期消除）
        XLog.w("按键监听：${if (on) "开启（60 秒后自动关）" else "关闭"}")
    }

    /**
     * 旁路报一条按键 —— 只报按下那一下（页面上要的是「按了哪个键」）。
     *
     *  整体 catch：这里在 hooker 体内，`sendBroadcast` 抛出去就是炸 AMS 的调用线程。
     */
    fun report(event: KeyEvent) {
        if (!listening || event.action != KeyEvent.ACTION_DOWN) return
        val ctx =
            try {
                context?.invoke()
            } catch (e: Throwable) {
                // 借 Context 失败 = 这条上报没有去处；吞在内部，绝不外抛（hook 体内抛出就是炸调用线程）
                XLog.w("按键监听：借 Context 失败，丢弃这一条上报：${e.javaClass.simpleName}")
                return
            } ?: return
        // **切到主线程再发**：这里跑在 InputReader 线程上，`sendBroadcast` 是一次同步 binder
        //  调用 —— AMS 那边一次瞬时锁竞争就会卡住全局按键读取（丢帧 / Input ANR），
        //  而这条上报只是「给设置页看按了哪个键」，晚几毫秒没有代价。
        val seen =
            Intent(ModuleWire.ACTION_KEY_SEEN)
                .setPackage(HostApp.PACKAGE)
                .putExtra(ModuleWire.EXTRA_SEEN_CODE, event.keyCode)
                .putExtra(ModuleWire.EXTRA_SEEN_NAME, KeyEvent.keyCodeToString(event.keyCode))
        handler.post {
            // 这里是 hook 体内：保护用 catch (Throwable) 而不是 runCatching —— 后者只允许出现在
            // 日志出口与反射查找（AGENTS.md 五之二、2），表达的不是同一件事。
            try {
                ctx.sendBroadcast(seen)
            } catch (e: Throwable) {
                // 判据级：监听开着却收不到按键，唯一线索就是这一行（`d` 在 release 里被编译期消除）
                XLog.w("按键监听：上报失败（忽略）：${e.javaClass.simpleName}")
            }
        }
    }
}
