package com.lookie.opluskey.host

import android.os.SystemClock

/**
 * 「这一下按键是模块自己认领的」—— 宿主进程内的一个时间戳。
 *
 *  只服务一件事：判断框架那条场景派发（`ActionButtonKeyService$a#handleMessage what=1`）
 *  **是不是被模块接管的那一次按压带出来的**。
 *
 *  ## 为什么需要它
 *
 *  那条派发**不带键的身份**（`data` 空、`arg1`/`arg2` 恒 0），所以宿主侧没法从消息本身看出
 *  「这一次派发对应哪颗键」。而无条件吞 / 无条件放行两边都错：
 *   · 一律放行 ⇒ **停用的键**（模块不认领、原生动作照跑）之外，那些被模块接管的键也会漏一条
 *     原生动作过去，表现就是「按什么都把设置页拉起来一次」；
 *   · 一律吞 ⇒ 停用的侧键连原生动作也没了，表现是「停用之后这颗键彻底没用」。
 *
 *  能用的信号只有「模块刚刚有没有认领过一次按压」：认领发生在注入侧判出手势之后，紧跟着
 *  （同一轮按压内）经广播送到这一侧（[GestureReceiver]），比那条派发早或晚几毫秒。
 *
 *  ## 记在**广播到达**那一刻，不记在执行那一刻
 *
 *  注入侧只在「这颗键归模块」时才会发这条广播（停用的键在拦截路径上就放行了，见
 *  `inject/key/SideKeyInterceptor`），所以**广播本身就是认领**。而执行排在那条串行队列上，
 *  前一条动作慢（shell 上限 10 秒 / 录音查询那些 binder）时，认领会晚到几秒 —— 那期间下
 *  一次按压的原生派发会被当成「没认领」补投回去，一次按压出两个动作。所以账记在广播到达
 *  这一侧（[GestureReceiver] 的接收回调），执行侧不再碰它。
 *
 *  补发那几条（立即 + 两次补发）复用同一个 `sent_at`（见 `GestureForwarder.nextSentAt`），
 *  按它去重：否则同一次按压会被记三笔，吞键窗口一路推到最后一发之后。
 *
 *  ## 边界（宁可多放一条，也不要多吞一条）
 *
 *  窗口内没认领 ⇒ **放行**（当成原生动作）。判据反了会吞掉原生动作 —— 那是用户直接看得见的
 *  「键失灵」；多放一条最多是「页面又弹了一下」，且日志里有 `since` 可查。
 */
internal object PressTrace {
    /** 上一次记过账的那条广播的 `sent_at` —— 补发那几条复用同一个值，据此认成同一笔。 */
    @Volatile
    private var lastSentAt = 0L

    @Volatile
    private var lastClaimAt = 0L

    /**
     * 一次**归模块的**按压到宿主了。
     *
     * @param sentAt 广播自带的 `sent_at`（`GestureForwarder.nextSentAt` 铸的递增时间戳）；
     *   `0` = 没有这个 extra（手搓的广播），照记一笔。
     */
    fun noteArrival(sentAt: Long) {
        if (sentAt != 0L && sentAt == lastSentAt) return
        lastSentAt = sentAt
        lastClaimAt = SystemClock.uptimeMillis()
    }

    /** 最近一次认领距今多少毫秒；**还没认领过**时返回 [Long.MAX_VALUE]。 */
    fun sinceLastClaim(): Long {
        val at = lastClaimAt
        return if (at == 0L) Long.MAX_VALUE else SystemClock.uptimeMillis() - at
    }
}
