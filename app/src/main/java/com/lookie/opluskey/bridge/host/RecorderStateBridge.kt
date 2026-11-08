package com.lookie.opluskey.bridge.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.RomProfile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * `RomProfile.AidlControl` 那条通道的执行器 —— **绑定导出服务后直接 `IBinder.transact`**。
 *
 * 现在只有录音用它：录音机没有「停止录音」的广播（全 dex 跟录音有关的 action 只有三条
 * `START_*`），唯一的外部通道是 `RecordStateService` 上的 `ISoundRecordInterface`
 * （查状态 / 停 / 暂停 / 继续）。取证与事务号见 `docs/device/actions/capture.md` 十七之七。
 *
 * ## 为什么不引用目标的 AIDL 类型
 *
 *  那些 Stub / Proxy / Parcelable 都在录音机自己的包路径下，跨包引用编译不过、也没法在本地
 *  反射出来（类只随它自己的应用加载）。所以这里只拿 [RomProfile.AidlControl] 里那几个
 *  **字符串与事务号**，自己拼 `Parcel` —— 与注入侧「按名字找、不 import」同一条纪律。
 *
 * ## 为什么要两道超时
 *
 *  1. **等连接**：`bindService` 是异步的，服务侧起不来时 `onServiceConnected` 永远不来。
 *  2. **等事务**：对端 `SoundRecordBinder.stopRecord` 里是 `mCondition.await()` —— **没有超时**。
 *     录音机内部那次「绑定录音服务」卡住时，这条跨进程调用会一直不返回，而 binder 调用本身
 *     没有超时参数。所以事务放到一条独立线程上发，这里只等它 [TIMEOUT_MS]：卡住的那条线程
 *     随它挂着（daemon，只在真出故障时发生一次），调用方（宿主执行线程 / 设置页）照常收尾。
 */
internal object RecorderStateBridge {
    /** 等连接 / 等事务各自的上限（毫秒）。 */
    const val TIMEOUT_MS = 1500L

    /** 事务号 `0` 那一档：不要 oneway、不要异步（AIDL 的常规同步调用）。 */
    private const val TRANSACT_SYNC = 0

    /** 录音机的真实状态。[UNKNOWN] = 这一趟没问到（原因已记日志）。 */
    enum class State {
        /** 不在录（空闲，或回包说未知 —— 见 [state]）。 */
        IDLE,

        /** 正在录（含已暂停）。 */
        RECORDING,

        /** 没问到：服务绑不上 / 连不上 / 事务失败 / 超时。 */
        UNKNOWN,
    }

    /**
     * 问一次当前状态。
     *
     *  `queryStatus` 的回包是 `RecordStatus`（`writeTypedObject` 的写法）：`writeNoException` 之后
     *  先一个**存在位**，非零再跟 4 个 int —— `currentRecordState` / `otherState` / `errorCode` /
     *  `extField`。只有第一个有用（`1` 正在录 / `2` 已暂停 / 其余空闲；`-1` 那种「还没上报过」
     *  也落在空闲这一侧，与录音机自己的 `manager.s.c()` 同一口径）。
     */
    fun state(
        ctx: Context,
        owner: String,
        aidl: RomProfile.AidlControl,
        tag: String,
    ): State {
        val raw =
            withService(ctx, owner, aidl, tag) { binder ->
                transact(binder, aidl.descriptor, aidl.queryTransact, tag) { reply ->
                    if (reply.readInt() != 0) reply.readInt() else -1
                }
            } ?: return State.UNKNOWN
        val recording = raw in aidl.busyStates
        XLog.w("$tag：录音机状态 $raw（${if (recording) "在录" else "空闲"}）")
        return if (recording) State.RECORDING else State.IDLE
    }

    /**
     * 停录（`stopRecord`）。返回**这条事务有没有成功发出去** —— 对端停没停成是它自己的事
     * （没有采集时它是空操作），本函数不替它断言。
     */
    fun stop(
        ctx: Context,
        owner: String,
        aidl: RomProfile.AidlControl,
        tag: String,
    ): Boolean =
        withService(ctx, owner, aidl, tag) { binder ->
            transact(binder, aidl.descriptor, aidl.stopTransact, tag, null)
        } != null

    /**
     * 绑定 → [block] → 解绑。返回 block 的结果（`null` = 没走通，断在哪一环已记日志）。
     *
     * @param block 拿到 binder 之后要发的那条事务（`null` 也算没走通）
     */
    private fun withService(
        ctx: Context,
        owner: String,
        aidl: RomProfile.AidlControl,
        tag: String,
        block: (IBinder) -> Int?,
    ): Int? {
        val short = "$owner/${aidl.service.substringAfterLast('.')}"
        val connection = Connection()
        val bound =
            try {
                ctx.bindService(
                    Intent().setComponent(ComponentName(owner, aidl.service)),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            } catch (e: Throwable) {
                XLog.w("$tag：bind $short 抛了 ${e.javaClass.simpleName}: ${e.message}")
                return null
            }
        if (!bound) {
            //  同步返回 false = 组件不存在 / 权限不够，`onServiceConnected` 永远不会来，
            //  不能还等 TIMEOUT_MS：那会把原因说成「没连上」，掩盖真正的错。
            XLog.w("$tag：绑不上 $short（bindService 返回 false：组件不存在或权限不够）")
            return null
        }
        return try {
            if (!connection.latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                XLog.w("$tag：$short 在 ${TIMEOUT_MS}ms 内没连上，放弃这一次")
                null
            } else {
                val binder = connection.binder
                if (binder == null) {
                    XLog.w("$tag：$short 连上了但没给 binder，放弃这一次")
                    null
                } else {
                    block(binder)
                }
            }
        } catch (e: InterruptedException) {
            //  被要求中断时别把中断标志吃掉（那一层还要靠它收尾）
            Thread.currentThread().interrupt()
            XLog.w("$tag：等 $short 时被打断")
            null
        } finally {
            try {
                ctx.unbindService(connection)
            } catch (e: Throwable) {
                //  解不掉只记 d：那是系统侧的事，这一趟该做的已经做完了
                XLog.d("$tag：解绑 $short 失败（已忽略）：${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * 真发那条事务 —— **在独立线程上发，这里只等 [TIMEOUT_MS]**（理由见类注释第 2 条）。
     *
     * @return 事务的读值；超时与抛异常都是 `null`（两种情形各有一条 `w`，断在哪一环能看出来）
     */
    private fun transact(
        binder: IBinder,
        descriptor: String,
        code: Int,
        tag: String,
        read: ((Parcel) -> Int)?,
    ): Int? {
        var result: Int? = null
        var error: Throwable? = null
        val worker =
            Thread(
                {
                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(descriptor)
                        binder.transact(code, data, reply, TRANSACT_SYNC)
                        //  服务端的 `enforceCallingPermission` 失败会从这里抛出来（SecurityException）
                        reply.readException()
                        result = read?.invoke(reply) ?: 0
                    } catch (e: Throwable) {
                        error = e
                    } finally {
                        reply.recycle()
                        data.recycle()
                    }
                },
                "opluskey-recorder-aidl",
            )
        worker.isDaemon = true
        worker.start()
        try {
            worker.join(TIMEOUT_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            XLog.w("$tag：等事务 $code 返回时被打断")
            return null
        }
        if (worker.isAlive) {
            XLog.w("$tag：事务 $code 在 ${TIMEOUT_MS}ms 内没返回（对端绑定时会无限等），这一次算失败")
            return null
        }
        val failure = error
        if (failure != null) {
            XLog.w("$tag：事务 $code 失败：${failure.javaClass.simpleName}: ${failure.message}")
            return null
        }
        return result
    }

    /** 一次绑定的连接对象 —— `latch` 就是「`onServiceConnected` 来过没有」。 */
    private class Connection : ServiceConnection {
        val latch = CountDownLatch(1)

        @Volatile
        var binder: IBinder? = null

        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder,
        ) {
            binder = service
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            //  只连一次、发完就解绑：断开这件事没有后续动作要做（超时由上面那支管）
        }
    }
}
