package com.lookie.opluskey.bridge

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

// 「柔性窗口」那一族共用的反射查找 —— **生产路径（小窗指令 / 原生动画）与调试探针走同一份**。
//
//  为什么单列这一个文件：探针存在的意义就是「在真机上问出生产路径会看到什么」。两边各写一份时
//  先漂的一定是探针（它取任务时只试 `matchMode=0`，而生产路径逐档试 0/1）—— 于是同一台机器上
//  探针报「任务找不到」、生产却能照常干，日志给出一条**与事实相反**的结论，比没有探针更坏。
//  所以这一族只此一份：改任一档的行为，探针跟着一起变（见 `docs/decoupling.md`）。

/**
 * `task.getWrapper().getExtImpl()` —— 任务的 `TaskExt`（柔性态那几个字段都挂在它上面）。
 *
 * 读不出来只说明这个任务不是柔性任务（或这一版 ROM 换了名字），调用方各自决定怎么降级。
 *
 * 注意：本文件里的 `extOf` 与探针以前那份**逐字相同**，合到这里是为了不再出现第二份。
 */
internal fun extOf(task: Any): Any? =
    try {
        val wrapper = Reflect.call(task, "getWrapper") ?: return null
        Reflect.call(wrapper, "getExtImpl")
    } catch (e: Throwable) {
        // 走到这里说明 `Reflect` 自己都没兜住（它只在 invoke 那一层兜）：不静默 return，
        //  把断在哪个类上说清 —— 读不出 TaskExt 与「反射链断了一环」是两件事（AGENTS.md 五、2）。
        XLog.w("读 TaskExt 失败（getWrapper / getExtImpl）：${e.javaClass.simpleName}: ${e.message}", e)
        null
    }

/**
 * 拿某个类上的单例 —— 先试 `getInstance(null)` 再试 `getInstance()`。
 *
 * 两支都试是因为 OEM 那些 `XxxManager` 的 `getInstance` 有的带一个用不上的参数（给 null 即可）、
 * 有的完全不收参；按「参数个数」找方法比按名字猜省事（见 [Reflect.callStatic]）。
 *
 *  注意：这一步**会触发 `cls` 的 `<clinit>`**（真正危险的是 `invoke`），所以调用方必须在调它
 *  **之前**过完自己的准入闸 —— 探针那边就是 `ProbeChannel.allowUnsafe`（AGENTS.md 五之二、1）。
 */
internal fun instanceOf(cls: Class<*>?): Any? =
    cls?.let { Reflect.callStatic(it, "getInstance", null) }
        ?: cls?.let { Reflect.callStatic(it, "getInstance") }

/**
 * `anyTaskForId` 的 matchMode 逐档试（[FlexibleWindowBridge.TASK_MATCH_MODES]）：`0` 只找已
 * attach 的、`1` 再加 recents。**先命中先返回**。
 *
 *  `2`（`…AND_RESTORE`）**故意不试**：它会把只剩 recents 记录的任务**恢复出来**，而这里只是「查」——
 *  理由写在 [FlexibleWindowBridge.TASK_MATCH_MODES] 的注释里（生产与探针同一份档位）。
 *
 *  **不能用按「参数运行时类」找方法的 [Reflect.call] 重载** —— Kotlin 会把 Int 装箱成 `Integer`，
 *  而真实签名是 `int` 原始类型，永远匹配不上、还静默返回 null（实测踩过）。
 *
 * @param root `mRootWindowContainer`（`mAtms` 上那个字段，两侧各自解析）
 * @param tag 日志前缀 —— 两侧各自的通道名（生产是「小窗指令」/ `CMD_MINI_ANIM`，探针是「probe」），
 *   日志里必须一眼看得出这一行是谁打的。
 * @return 任务；三种都找不到返回 null（调用方自己说断在哪一环）
 */
internal fun anyTask(
    root: Any,
    taskId: Int,
    tag: String,
): Any? {
    val method =
        Reflect.method(root.javaClass, "anyTaskForId", java.lang.Integer.TYPE, java.lang.Integer.TYPE)
            ?: run {
                XLog.w("$tag：找不到 anyTaskForId(int,int)")
                return null
            }
    for (mode in FlexibleWindowBridge.TASK_MATCH_MODES) {
        // `Reflect.call` 自己就把「方法在、调用抛了」吞成 null 并记账（见它的 KDoc），这里不必再包一层
        val task = Reflect.call(root, method, taskId, mode)
        if (task != null) {
            XLog.w("$tag：anyTaskForId(#$taskId, mode=$mode) 命中")
            return task
        }
    }
    // 说清「没试哪一档」，否则看日志的人会以为「三种都试过、任务真没了」—— 那是两回事
    XLog.w(
        "$tag：anyTaskForId(#$taskId) 在 mode=${FlexibleWindowBridge.TASK_MATCH_MODES.joinToString()} 都没命中" +
            "（没试 mode=2：那档会从 recents 把任务恢复出来，是一次状态改变）",
    )
    return null
}
