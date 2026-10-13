package com.lookie.opluskey.hook

import android.content.Intent
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Config
import com.lookie.opluskey.config.ConfigBroadcast
import com.lookie.opluskey.config.KeyId

/** 注入侧持有的配置：从宿主广播过来，供按键判定使用。 */
internal object ConfigChannel {

    /**
     * 主开关（`null` = 还没收到过广播）。没收到过时按「接管」处理 —— 引导条件：
     * 唤醒宿主的那次转发由注入侧发起，默认「不接管」会永久失效。
     */
    @Volatile
    private var switchFromBroadcast: Boolean? = null

    /** 完整侧键配置；还没收到过就是 null。只信广播 —— 注入侧读宿主私有目录必 `EACCES`。 */
    @Volatile
    private var received: Config? = null

    /** 主开关当前值；没收到过广播时按 `true`（接管）处理。 */
    val takeOver: Boolean
        get() = switchFromBroadcast ?: true

    /** 当前配置；还没收到过广播时用 [Config.DEFAULT] 顶着。 */
    val config: Config
        get() = received ?: Config.DEFAULT

    /** 收下一条配置广播。调用点在 AMS 广播入口（热路径），只做解析 / 赋值 / 记日志。 */
    fun read(intent: Intent) {
        val fresh = ConfigBroadcast.parse(intent)
        val key = fresh.key(KeyId.PLUS)

        if (switchFromBroadcast != key.enabled) {
            switchFromBroadcast = key.enabled
            XLog.w("收到主开关广播：侧键接管=${key.enabled}（注入侧立即生效）")
        }

        received = fresh
        // 这一行（及上面主开关）必须是 W：注入侧 i/d 在 LSPosed 日志页整档看不到（docs/device-findings.md 十五），
        // 它们是「配置到没到注入侧」的唯一判据；只在启动 / 解锁 / 写盘时机各来一条，不刷屏。
        XLog.w(
            "配置已更新：长按=${key.longPressMs}ms" +
                " 双击窗口=${fresh.global.doubleClickMs}ms" +
                " 立即单击=${fresh.global.instantSingle}" +
                " 单击=${key.single.token}",
        )
    }
}
