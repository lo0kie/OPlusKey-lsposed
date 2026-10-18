package com.lookie.opluskey.inject

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire

/**
 * 「添加按键」页要的那条**旁路监听**：把按下的键原样报回宿主。
 *
 * ## 为什么必须在这里做
 *
 *  界面拿不到按键：设置页是宿主 Activity 里的覆盖层，焦点在宿主控件上；音量 / 电源这类更是
 *  在应用之前就被系统处理掉了。**只有注入侧看得见每一颗键**。
 *
 * ## 它是只读的
 *
 *  [report] 只复制一份事件发回去，**不改**原有认领 / 放行判定 —— 监听期间系统行为与平时完全一致。
 *  漏看一颗键只是「这次没听到」，多吞一颗键是「用户按了没反应」，两者代价不对等。
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

    /** 由安装入口递进来一个「要用时才取」的 Context。 */
    fun install(borrow: () -> Context?) {
        context = borrow
    }

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
        val ctx = context?.invoke() ?: return
        runCatching {
            ctx.sendBroadcast(
                Intent(ModuleWire.ACTION_KEY_SEEN)
                    .setPackage(HostApp.PACKAGE)
                    .putExtra(ModuleWire.EXTRA_SEEN_CODE, event.keyCode)
                    .putExtra(ModuleWire.EXTRA_SEEN_NAME, KeyEvent.keyCodeToString(event.keyCode)),
            )
        }.onFailure { XLog.d("按键监听：上报失败（忽略）：${it.javaClass.simpleName}") }
    }
}
