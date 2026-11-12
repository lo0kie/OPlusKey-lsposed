package com.lookie.opluskey.inject

import android.content.Context
import android.media.AudioManager
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 由 `system_server` 替宿主**把某一路流设到某一档**（`ModuleWire.ACTION_SET_VOLUME`）。
 *
 *  **为什么不在宿主那边设**：宿主进程里 `setStreamVolume` 会被 ColorOS 静默丢掉 —— 写下去、
 *  读回来还是旧值，也不抛异常（`docs/device/actions/audio.md` 三十四）。宿主只剩「注入音量键」
 *  这一条退路，而一颗键就是一整格（量程的 1/16，`0..160` 上 10 格），`±1%`（1.6 格）这种
 *  细步进走不出来：硬按就是「1% 走成 6.25%」，不按就是「按了没反应」。
 *  这一层跑在 `system_server`，调用身份是系统本身，那条策略不挡它 —— 精确档位又能拿到了，
 *  所以**音量那一族正常就走这一条**，宿主自己那几档写只剩「模块没注入时」的兜底。
 *
 *  **带 `FLAG_SHOW_UI`**：改完屏幕上就有那一路流的音量条。**值本来就等于目标**时改走
 *  `adjustStreamVolume(ADJUST_SAME, FLAG_SHOW_UI)` —— `setStreamVolume` 在同值上不发面板
 * （宿主与这一侧都一样，真机实测），而物理音量键按到底时值也没变、面板照样弹，走的就是 adjust
 *  （`docs/device/actions/audio.md` 三十七）。
 *
 *  两条约束（与 [SystemToast] 同一档）：
 *  1) 用 `ActivityThread.getSystemContext()`（包名 `android`）—— 身份与包名对得上，
 *     不用 [com.lookie.opluskey.bridge.inject.SystemContext] 借来的那份 App Context
 *     （那份的包名仍是宿主，身份与包名不一致）；
 *  2) 整段兜 `Throwable`：这是 `system_server`，漏一个异常出去就是整机重启（五之二）。
 *     调用方（`inject/KeyHooks`）已经把它 post 到 worker 线程上，这里不再切线程。
 */
internal object SystemVolume {
    /** 系统 Context 缓存（拿到就不再取；拿不到下次再试）。 */
    private var systemContext: Context? = null

    /**
     * 设为 [index] 档；任何一环失败都只记日志。
     *
     * @param stream `AudioManager.STREAM_MUSIC` / `STREAM_RING` 之类
     */
    fun set(
        loader: ClassLoader,
        stream: Int,
        index: Int,
    ) {
        try {
            val ctx = context(loader) ?: return
            val am =
                ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: run {
                    XLog.w("注入侧设音量：拿不到 AudioManager（路 $stream → $index）")
                    return
                }
            val before = am.getStreamVolume(stream)
            if (before == index) {
                //  值没有要改的 ⇒ 这一档要的只是**把音量条叫出来**。`setStreamVolume` 在同值上
                //  不发面板（这一侧也一样，真机实测），而 `adjustStreamVolume` 会 —— 物理音量键
                //  按到底时值也没变、面板照样弹，走的就是它。`ADJUST_SAME` 不改值；面板是
                //  **当前活跃流**那一条（这台 ROM 实测）：媒体流就是媒体条。
                am.adjustStreamVolume(stream, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                XLog.w("注入侧设音量：路 $stream 已经是 $index ⇒ 值不动，只把音量条叫出来")
                return
            }
            am.setStreamVolume(stream, index, AudioManager.FLAG_SHOW_UI)
            //  判据级：宿主那边看不到结果，只认「写前 / 写后」这两个读数
            XLog.w("注入侧设音量：路 $stream $before → ${am.getStreamVolume(stream)}（目标 $index）")
        } catch (e: Throwable) {
            XLog.w("注入侧设音量失败（已忽略）：路 $stream → $index | ${e.javaClass.simpleName}", e)
        }
    }

    /** `ActivityThread.getSystemContext()` —— 包名 `android` 的那一份。 */
    private fun context(loader: ClassLoader): Context? {
        systemContext?.let { return it }
        val at = ActivityThreadCompat.currentActivityThread(loader) ?: return null
        val ctx =
            Reflect.call(at, "getSystemContext") as? Context ?: run {
                XLog.w("注入侧设音量：拿不到 system_server 的 SystemContext")
                return null
            }
        systemContext = ctx
        return ctx
    }
}
