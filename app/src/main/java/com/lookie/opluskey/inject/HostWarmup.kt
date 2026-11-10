package com.lookie.opluskey.inject

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp

/**
 * 开机就把宿主进程拉起来 —— 模块的**执行侧**全在宿主进程里，进程不在，判定出来了动作也没处跑。
 *
 *  宿主进程平时由 ROM 的 `com.oplus.exsystemservice` 绑上来（实测它要等到开机后 ~15s），
 *  那段时间按侧键什么都不发生。本模块在 system_server 里，从这里发一条 `startService`
 *  就能把那个进程提前拉起来 —— system_server 对组件的导出 / 权限检查全放行
 *  （`ActivityManager.checkComponentPermission` 对本进程的调用恒 GRANTED），
 *  这是普通应用做不到、只有注入侧做得到的事。
 *
 *  ## 什么时候跑、跑几次
 *
 *  [schedule] 在 `onSystemServerStarting` 里被调，那时 `ActivityManager` 还没就绪，
 *  所以起一条守护线程**按固定间隔重试**：成功（或到点放弃）即停。重试期间的所有异常都只记日志
 *  —— 这一环失败的最坏结果是「宿主进程晚一点被 ROM 自己拉起来」，与不装本模块一样。
 *
 *  启动用的就是宿主自己的 [Framework.HOST_KEY_SERVICE]：它既把进程抬出可冻结范围，
 *  又与宿主侧 `keepProcessAwake` 那一下是同一个服务（进程已在时只是多发一次空 `onStartCommand`，无害）。
 */
internal object HostWarmup {
    /** 重试到放弃的时限 —— 正常开机用不了这么久；到点放弃，等 ROM 自己去绑（与不装一样）。 */
    private const val GIVE_UP_MS = 40_000L

    /** 重试间隔 —— `ActivityManager` 没就绪时多发几次空转的反射查询，代价可忽略。 */
    private const val RETRY_MS = 400L

    @Volatile
    private var scheduled = false

    /** 只排一次（`install` 理论上只跑一次，这里防的是将来有人重复调用）。 */
    fun schedule() {
        if (scheduled) return
        scheduled = true
        Thread(
            {
                XLog.guard("开机拉起宿主") { retryUntilStarted() }
            },
            "OPlusKey-HostWarmup",
        ).apply {
            isDaemon = true
        }.start()
    }

    private fun retryUntilStarted() {
        val startedAt = SystemClock.uptimeMillis()
        var firstFailure: String? = null
        while (true) {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            if (elapsed > GIVE_UP_MS) {
                XLog.w("开机拉起宿主：${GIVE_UP_MS / 1000}s 没成功，放弃（失败原因：$firstFailure）—— 等 ROM 自己去绑")
                return
            }
            val failure = tryStartService()
            if (failure == null) {
                XLog.w("开机拉起宿主：已发出 startService（开机后 ${elapsed}ms）—— 宿主进程一起来，执行侧即就位")
                return
            }
            if (firstFailure == null) {
                //  第一条失败原因必须留下：AMS 没就绪、服务名变了、权限变了，各是不同的排查方向
                firstFailure = failure
                XLog.w("开机拉起宿主：第一次尝试没成（$failure）—— 继续重试")
            }
            Thread.sleep(RETRY_MS)
        }
    }

    /**
     * 发一次 `startService`。
     *
     * @return `null` = 发出去了（或进程已在）；其余 = 失败原因（继续重试）
     */
    private fun tryStartService(): String? {
        //  `ActivityManager.getService()` 拿的是 IActivityManager（隐藏 API）：system_server 里
        //  调隐藏 API 不受限，且这条自 API 26 起就在，签名随版本多一个 `callingFeatureId` ——
        //  所以不锁参数个数，按「第 2 个参数是 Intent」挑重载，其余按类型填缺省。
        //  类名与方法名来自 `Framework.Calls`（一、7），这里只负责调。
        val am =
            runCatching {
                Class
                    .forName(Framework.Calls.ACTIVITY_MANAGER)
                    .getDeclaredMethod(Framework.Calls.GET_SERVICE)
                    .invoke(null)
            }.getOrNull() ?: return "拿不到 IActivityManager（AMS 还没就绪）"

        val method =
            am.javaClass.methods.firstOrNull {
                it.name == "startService" && it.parameterTypes.size >= 6 && it.parameterTypes[1] == Intent::class.java
            } ?: return "IActivityManager 上没有 startService 重载"

        val intent =
            Intent().setComponent(ComponentName(HostApp.PACKAGE, Framework.HOST_KEY_SERVICE))
        val args =
            method.parameterTypes
                .mapIndexed { index, type ->
                    when {
                        index == 1 -> intent

                        //  `callingPackage`：system uid 不做包名一致性校验，报宿主自己的包名即可
                        index == 4 && type == String::class.java -> HostApp.PACKAGE

                        type == Boolean::class.javaPrimitiveType -> false

                        type == Int::class.javaPrimitiveType -> 0

                        else -> null
                    }
                }.toTypedArray()

        return runCatching {
            method.invoke(am, *args)
            null
        }.getOrElse { "${it.cause?.javaClass?.simpleName ?: it.javaClass.simpleName}: ${it.message}" }
    }
}
