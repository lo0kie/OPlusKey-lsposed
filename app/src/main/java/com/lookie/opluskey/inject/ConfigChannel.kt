package com.lookie.opluskey.inject

import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.wire.ConfigBroadcast

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

    /**
     * 最后一次收到的那条配置广播的**内容指纹**（见 `ConfigBroadcast.tag`）。
     *
     *  链路自检回包时原样带回，设置页拿它与自己手上那份比 —— 光回一个「收到过」答不了
     *  「后来改的那一次到了没有」：开机收到一条后哪条丢了，这边还是旧配置，界面照样报「正常」。
     */
    @Volatile
    private var receivedTag: String? = null

    /** 主开关当前值；没收到过广播时按 `true`（接管）处理。 */
    val takeOver: Boolean
        get() = switchFromBroadcast ?: true

    /** 当前配置；还没收到过广播时用 [Config.DEFAULT] 顶着。 */
    val config: Config
        get() = received ?: Config.DEFAULT

    /**
     * 收到过配置没有 —— 链路自检用。
     *
     * `false` 是个真实且**静默**的故障：注入侧会拿 [Config.DEFAULT] 顶着跑（主开关按接管处理、
     * 但动作全是「无操作」），表现是「侧键被吞掉、什么都不做」，而日志里看不出缺了哪一段。
     */
    val receivedConfig: Boolean
        get() = received != null

    /**
     * 最后一次收到的那条配置广播的指纹；还没收到过就是 null。
     *
     *  设置页拿它与自己手上那份的指纹比：**相等才叫「设置生效」**。「收到过」不等于「就是这份」。
     */
    val configTag: String?
        get() = receivedTag

    /** 收下一条配置广播。调用点在 AMS 广播入口（热路径），只做解析 / 赋值 / 记日志。 */
    fun read(intent: Intent) {
        val fresh = ConfigBroadcast.parse(intent)
        val key = fresh.key(KeyId.PLUS)

        if (switchFromBroadcast != key.enabled) {
            switchFromBroadcast = key.enabled
            XLog.w("收到主开关广播：侧键接管=${key.enabled}（注入侧立即生效）")
        }

        received = fresh
        // 指纹按广播本身算（与设置页那边同一份 intent 口径），不是按解析后的对象算
        receivedTag = ConfigBroadcast.tag(intent)
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
