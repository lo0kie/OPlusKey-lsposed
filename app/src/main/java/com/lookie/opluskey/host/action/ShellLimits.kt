package com.lookie.opluskey.host.action

// shell 执行的**并发与线程寿命**参数 —— 与 `ui/model/PageMetrics` 同一类东西
//  （性能参数集中处），但**不能放在那边**：`PageMetrics` 在 `ui/`，而 `host/` 不许依赖 `ui/`
//  （`docs/decoupling.md` 动作 1、2，`tools/audit-coupling.py` 会当场报错）。
//  同包里已有先例：`MiniWindowFacts.kt` 也是把一族的常量从执行类里拎出来单独放。

/**
 * 同时挂着的 shell 子进程上限 —— **全机**（不是每进程，见 `ShellRunner.acquireSlot`）。
 *
 * 长按连续触发最密 100ms 一条，不设限会堆上百个进程 / 线程。槽位本身由 `FileLock` 跨进程守，
 * 所以这个数说的是「两个宿主进程加起来最多几条」，不是「每条最多几个」。
 *
 * `ShellRunner` 的**两条线程池上限也跟着它走**：池里多出来的线程没有槽位可用，
 * 两个数必须一起改，否则要么白占线程、要么池先被占满（见那边的 KDoc）。
 */
internal const val SHELL_MAX_CONCURRENT = 3

/**
 * 池内空闲线程多久回收（秒）。
 *
 * 按键是低频动作（连发最密 100ms 一条），池不该常驻：60 秒没有活就让它退出，
 * 下次按键再按需创建。取 60 而不是几秒，是为了让「连按几下然后停一会儿再按」
 * 这一串落在同一个池里，不至于每段间隔都重建一轮线程。
 */
internal const val SHELL_THREAD_IDLE_SECONDS = 60L
