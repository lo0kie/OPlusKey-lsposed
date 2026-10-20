package com.lookie.opluskey.core

import android.os.Process
import android.os.SystemClock

/**
 * 进程启动时刻与开机时刻的 **wall clock 换算** —— 注入侧、宿主侧、设置页三处共用这一套口径。
 *
 *  为什么需要它：判「这个进程跑的是不是盘上那份包」只需要一个不等式 ——
 *  **进程启动时刻 < 安装包的更新时间** ⇒ 这个进程起得比包还早，它加载的是上一版代码。
 *  而 `PackageInfo.lastUpdateTime` 是 wall clock，进程启动时刻只能从 `elapsedRealtime` 那一族里拿，
 *  所以必须换算一次。换算公式三处必须一致，否则比出来的结论会差出「开机时长」那么多。
 *
 *  为什么不用 `Process.getStartUptimeMillis()`：那个是 `uptimeMillis` 基准（不含深睡），
 *  与 `elapsedRealtime`（含深睡）不是一个时钟 —— 息屏过一段时间之后两者差得很远。
 *  用 [Process.getStartElapsedRealtime] 与 [SystemClock.elapsedRealtime] 才是同一基准。
 *
 *  这两个方法都只读本进程的状态，不需要 Context、不需要权限，在 system_server 里也安全
 *  （见 `docs/device-findings.md` 六之五 那条「系统进程里能安全用哪些东西」）。
 */
object ProcClock {
    /**
     * 本进程启动时刻（wall clock 毫秒）。
     *
     *  取「现在」减去「本进程已经活了多久」。所以它是个**常量**：同一进程里任何时候调用都得到
     *  同一个值（除非用户改了系统时间，那种情况下它也跟着整体平移，而我们只做「与包更新时间比大小」，
     *  平移不影响结论的量级）。
     */
    fun startWall(): Long = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())

    /**
     * 本轮开机时刻（wall clock 毫秒，秒级精度）。
     *
     *  取它当「这是哪一轮开机」的指纹：同一轮开机内，任何进程算出来都一样（差在毫秒级），
     *  跨一轮重启就一定不同。用来判「上次收到报活是不是也在这轮开机里」——
     *  光看「收到过报活」答不了这个，那可能是上一轮的事。
     *
     *  取整到秒：两次调用相差几毫秒时，直接比原始值会判成「不同一轮」，而它本来只是个粗指纹。
     */
    fun bootWall(): Long = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000L * 1000L
}
