package com.lookie.opluskey.bridge

/**
 * 「缓存值 + 取它的时刻」—— 一个值和一个时刻，**必须一起换**。
 *
 *  这一对原先在四处各写一遍两个字段（`cachedHostUid` / `cachedHostUidAt`、
 *  `cachedUsers` / `cachedAt`、`intent` / `intentAt`、`lastText` / `lastAt`），
 *  每处各写一遍过期判据。两处独立的 `@Volatile` 字段还有个更细的问题：读它们要读两次，
 *  中间可能被另一条线程换掉 —— 读到「新的值 + 旧的时刻」就会把一份刚取到的缓存判成过期
 *  （或反过来）。收成一个不可变对象之后，一次读拿到的一定是同一份。
 *
 *  写法上仍然是「一个 `@Volatile var` 字段」：整体替换引用，读侧不需要锁。
 *
 *  @param value 缓存下来的那份东西
 *  @param at 取它的时刻 —— 与调用方判过期时用的 `now` 必须是**同一个时钟源**
 *    （`elapsedRealtime` 与 `uptimeMillis` 不能混着比）
 */
internal class TimedCache<T>(
    val value: T,
    val at: Long,
) {
    /**
     * [now] 这一刻还算不算新鲜（距 [at] 不到 [ttlMs]）。
     *
     *  边界取「不到」而不是「不超过」：四处里三处本来就是 `<`，差那 1ms 没有意义。
     */
    fun fresh(
        now: Long,
        ttlMs: Long,
    ): Boolean = now - at < ttlMs
}
