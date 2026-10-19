package com.lookie.opluskey.inject

import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.ConfigFields
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

    /**
     * 某一颗键当前该不该接管 —— **认领闸门的唯一判据**（`SideKeyInterceptor.takesOver`）。
     *
     *  侧键走 [takeOver]（它带着「缺 enabled 那一项的畸形广播别改主开关」那道闸，
     *  以及「还没收到过广播时按接管」的引导条件）；自定义键走配置里自己的 `enabled`，
     *  配置里认不出的键按不接管（`Config.key` 对未知键返回 `defaultFor`，`enabled = false`）。
     */
    fun enabledOf(id: KeyId): Boolean = if (id.id == KeyId.PLUS.id) takeOver else received?.key(id)?.enabled ?: true

    /**
     * 「某一颗键从接管翻成不接管」那一刻要执行的撤销动作，由 `KeyHooks.install` 装配上
     * （实现是 `SideKeyInterceptor.cancelPending`，它把所有键的待定手势一起撤掉）。
     *
     *  落点只能在这里：按住不放时不会再有按键事件走进 `SideKeyInterceptor.handle`，
     *  撤销若是写在认领闸门（`enabledOf`）那一侧就永远不会执行；而已经排下的长按枪 /
     *  双击窗到点会自己响、连发任务又从不看闸门，不撤就会一路派发到 `REPEAT_MAX_MS`。
     */
    @Volatile
    var onTakeOverOff: (() -> Unit)? = null

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
        val before = received
        val key = fresh.key(KeyId.PLUS)

        // 侧键之外，**任一键**从「接管」翻成「不接管」也要撤掉已排下的手势：自定义键被关掉时，
        //  按住不放的那一下否则会一路派发到连发上限（侧键那一位另有下面那个分支，它还要
        //  改 `switchFromBroadcast` 并打一行 W，所以这里把它排除掉，免得同一次撤销做两遍）。
        // 比的是**旧配置**那份清单（`prev.allKeys`）：一颗自定义键被**删掉**时，新配置里
        //  已经没有它，遍历 `fresh.allKeys` 就永远看不到这一颗，已排下的手势也就撤不掉
        //  （删键那一刻按住不放会一路派发到连发上限）。`fresh.key(已删的键)` 退回默认值
        //  （`enabled=false`），所以这一条对「删键」与「关掉」同样成立。
        val newlyOff =
            before?.let { prev ->
                prev.allKeys.any {
                    it.id != KeyId.PLUS.id && prev.key(it).enabled && !fresh.key(it).enabled
                }
            } ?: false

        // 只有这条广播**带** enabled 这一项时才用它改主开关：`ConfigBroadcast.parse` 对缺项兜底成
        //  false，而本对象「没收到过 = 接管」的方向相反 —— 一条缺 enabled 的畸形广播会把主开关静默
        //  设成关闭，还会触发下面那段撤销回调。缺项视为「这条不是一份权威配置」，保持现值不动。
        if (intent.hasExtra(ConfigBroadcast.extraName(KeyId.PLUS.id, ConfigFields.ENABLED_EXTRA)) &&
            switchFromBroadcast != key.enabled
        ) {
            switchFromBroadcast = key.enabled
            XLog.w("收到主开关广播：侧键接管=${key.enabled}（注入侧立即生效）")
            // 翻到「不接管」这一刻撤销已排下的手势（长按枪 / 双击窗 / 连发任务）。
            //  为什么不能写在拦截点那条 early return 里：那条只在有新按键事件时才走到，
            //  而按住不放期间没有新事件进来 —— 撤销写在那边等于永不执行。
            //  本回调跑在 AMS 广播 hook 内，异常一律吞掉，绝不外抛。
            if (!key.enabled) {
                try {
                    onTakeOverOff?.invoke()
                } catch (e: Throwable) {
                    XLog.w("主开关关闭时撤销待定手势失败（已忽略）：${e.javaClass.simpleName}", e)
                }
            }
        }

        if (newlyOff) {
            try {
                onTakeOverOff?.invoke()
            } catch (e: Throwable) {
                XLog.w("自定义键关闭时撤销待定手势失败（已忽略）：${e.javaClass.simpleName}", e)
            }
        }

        received = fresh
        // 指纹按广播本身算（与设置页那边同一份 intent 口径），不是按解析后的对象算
        receivedTag = ConfigBroadcast.tag(intent)
        // 这一行（及上面主开关）必须是 W：注入侧 i/d 在 LSPosed 日志页整档看不到（docs/device-findings.md 十五），
        // 它们是「配置到没到注入侧」的唯一判据；只在启动 / 解锁 / 写盘时机各来一条，不刷屏。
        //  token 要过 [XLog.redactToken]：`shell:` / `intent:` 后面是整条正文（可能带 token、密码），
        //  而这一行是 W —— 既进 logcat 也进「导出日志」那份文件。
        XLog.w(
            "配置已更新：长按=${key.longPressMs}ms" +
                " 双击窗口=${fresh.key(KeyId.PLUS).doubleClickMs}ms（侧键那份）" +
                " 立即单击=${fresh.key(KeyId.PLUS).instantSingle}" +
                " 单击=${XLog.redactToken(key.single.token)}",
        )
    }
}
