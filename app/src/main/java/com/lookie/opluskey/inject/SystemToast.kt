package com.lookie.opluskey.inject

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 由 `system_server` 替宿主弹一条提示（命令里 `echo 'tip: …'` 那行）。
 *
 *  **为什么不在宿主那边弹**：系统的 toast 是**按包限流**的（3 条 / 20 秒、5 条 / 42 秒、
 *  6 条 / 68 秒，反编译与真机日志见 `docs/device/actions/feedback.md` 四十），宿主的包一旦连按超过
 *  这个密度，后面那些直接被 `NotificationService` 丢弃。而**系统自己**（包名 `android`）
 *  持有 `android.permission.UNLIMITED_TOASTS`，`isExemptFromRateLimiting` 判它免限流 ——
 *  所以这条提示改由注入侧用系统 Context 弹：同一个模块、同一句话，只是换了发话人。
 *
 *  两个约束：
 *  1) 用 `ActivityThread.getSystemContext()`（包名 `android`），**不是** [SystemContext] 借来的
 *     那份 App Context —— 后者包名仍是宿主，限流照样跑不掉；
 *  2) 整段跑在主线程、整段兜 `Throwable`：这是 `system_server`，漏一个异常出去就是整机重启
 *     （五之二）。
 */
internal object SystemToast {
    /** 主线程 Handler —— toast 只能在有 Looper 的线程上建。 */
    private val main = Handler(Looper.getMainLooper())

    /** 系统 Context 缓存（拿一次就够；拿不到下次再试）。 */
    private var systemContext: Context? = null

    /** 同一句话在这个间隔内不重复弹（长按连发时同一条命令会连着报同一句）。 */
    private const val DEDUP_MS = 1000L

    private var lastText: String? = null
    private var lastAt = 0L

    /** 请注入侧弹一条提示；任何一环失败都只记日志。 */
    fun show(
        loader: ClassLoader,
        text: String,
    ) {
        if (text.isEmpty()) return
        main.post {
            try {
                val ctx = context(loader) ?: return@post
                val now = android.os.SystemClock.elapsedRealtime()
                if (text == lastText && now - lastAt < DEDUP_MS) return@post
                lastText = text
                lastAt = now
                // 每次**新建**一条、也不 cancel 上一条：同一个实例再 show 会按 token 就地更新
                //  队列里那一条（框架的 `indexOfToastLocked`），连按时就只剩「一条一直在变」——
                //  用户要的是每按一下都完整弹一条，所以让它们排队。配额这边免了（见类注释），
                //  排几条都不会被系统丢掉。
                Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
                XLog.w("注入侧弹提示：$text")
            } catch (e: Throwable) {
                XLog.w("注入侧弹提示失败（已忽略）：${e.javaClass.simpleName}", e)
            }
        }
    }

    /** `ActivityThread.getSystemContext()` —— 包名 `android` 的那一份（免限流就靠它）。 */
    private fun context(loader: ClassLoader): Context? {
        systemContext?.let { return it }
        val at = ActivityThreadCompat.currentActivityThread(loader) ?: return null
        val ctx =
            Reflect.call(at, "getSystemContext") as? Context ?: run {
                XLog.w("拿不到 system_server 的 SystemContext，注入侧弹不了提示")
                return null
            }
        systemContext = ctx
        return ctx
    }
}
