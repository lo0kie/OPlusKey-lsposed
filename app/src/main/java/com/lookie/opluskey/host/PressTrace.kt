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
 *  ## 边界（宁可多放一条，也不要多吞一条）
 *
 *  窗口内没认领 ⇒ **放行**（当成原生动作）。判据反了会吞掉原生动作 —— 那是用户直接看得见的
 *  「键失灵」；多放一条最多是「页面又弹了一下」，且日志里有 `since` 可查。
 */
internal object PressTrace {
    @Volatile
    private var lastClaimAt = 0L

    /** 记一次认领（注入侧刚判出这一下归模块）。 */
    fun noteClaim() {
        lastClaimAt = SystemClock.uptimeMillis()
    }

    /** 最近一次认领距今多少毫秒；**还没认领过**时返回 [Long.MAX_VALUE]。 */
    fun sinceLastClaim(): Long {
        val at = lastClaimAt
        return if (at == 0L) Long.MAX_VALUE else SystemClock.uptimeMillis() - at
    }
}
