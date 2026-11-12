package com.lookie.opluskey.bridge.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.rom.RomProfile
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
 *     没有超时参数。所以事务放到一条独立线程上发，这里只等剩下的预算：卡住的那条线程
 *     随它挂着（daemon，且**全进程只有一条**，见 [worker]），调用方（宿主执行线程 / 设置页）照常收尾。
 *
 *  两道合起来只花 [BUDGET_MS] 那一份预算：这条链跑在 `ActionRunner` 的锁里与设置页线程上，
 *  一次动作最多等这么久。
 */
internal object RecorderStateBridge {
    /** 单步（等连接 / 等事务）的上限（毫秒）。 */
    const val TIMEOUT_MS = 1500L

    /**
     * **一次调用**的总预算（毫秒）—— 等连接与等事务共用这一份。
     *
     *  原先两段各等 [TIMEOUT_MS]，于是一次 `state()` 最坏 3 秒、`state()` + `stop()` 最坏 6 秒；
     *  而这条链跑在 `ActionRunner` 的锁里（另一颗键的执行要等它）与设置页的「测试」线程上。
     *  收成一个总预算之后，一次动作最坏 2 秒，且「对端卡住」这件事的状态不变（照样报失败）。
     */
    private const val BUDGET_MS = 2_000L

    /** 事务号 `0` 那一档：不要 oneway、不要异步（AIDL 的常规同步调用）。 */
    private const val TRANSACT_SYNC = 0

    /**
     * 发事务的那一条后台线程 —— **全进程共一条**，不随调用次数增长。
     *
     *  对端一旦卡在 `mCondition.await()` 上，那条线程就再也回不来（binder 调用没有超时参数）。
     *  原先每次调用新建一条线程：每失败一次就多泄一条 daemon 线程与它手上的两个 `Parcel`。
     *  换成单线程池之后最坏只挂一条；后续调用排队后照样按预算超时失败，不会再新增线程 / Parcel。
     */
    private val worker: java.util.concurrent.ExecutorService by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "opluskey-recorder-aidl").apply { isDaemon = true }
        }
    }

    /**
     * 一次调用的**不变部分**：服务、事务号、日志主语。
     *
     *  这四样要原样穿过 [withService] / [transact] 两层；摊成行参时四个实参传三处同样的东西，
     *  改一处漏一处就会让日志主语串到别的事务上。
     */
    private class Call(
        val owner: String,
        val aidl: RomProfile.AidlControl,
        val code: Int,
        val tag: String,
    )

    /** 这一次调用还剩多少预算（毫秒），`<= 0` = 已经用完。 */
    private fun remaining(deadline: Long): Long = deadline - android.os.SystemClock.uptimeMillis()

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
        val deadline = android.os.SystemClock.uptimeMillis() + BUDGET_MS
        val call = Call(owner, aidl, aidl.queryTransact, tag)
        val raw =
            withService(ctx, call, deadline) { binder ->
                transact(binder, call, deadline) { reply ->
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
    ): Boolean {
        val deadline = android.os.SystemClock.uptimeMillis() + BUDGET_MS
        val call = Call(owner, aidl, aidl.stopTransact, tag)
        return withService(ctx, call, deadline) { binder -> transact(binder, call, deadline, null) } != null
    }

    /**
     * 绑定 → [block] → 解绑。返回 block 的结果（`null` = 没走通，断在哪一环已记日志）。
     *
     * @param deadline 这一次调用的总预算终点（`uptimeMillis`）—— 等连接只花还剩的那部分，
     *   剩下的留给事务（见 [BUDGET_MS]）
     * @param block 拿到 binder 之后要发的那条事务（`null` 也算没走通）
     */
    private fun withService(
        ctx: Context,
        call: Call,
        deadline: Long,
        block: (IBinder) -> Int?,
    ): Int? {
        val tag = call.tag
        val short = "${call.owner}/${call.aidl.service.substringAfterLast('.')}"
        val connection = Connection()
        val bound =
            try {
                ctx.bindService(
                    Intent().setComponent(ComponentName(call.owner, call.aidl.service)),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            } catch (e: Throwable) {
                XLog.w("$tag：bind $short 抛了 ${e.javaClass.simpleName}: ${e.message}")
                return null
            }
        if (!bound) {
            //  同步返回 false = 组件不存在 / 权限不够，`onServiceConnected` 永远不会来，
            //  不能还等预算：那会把原因说成「没连上」，掩盖真正的错。
            XLog.w("$tag：绑不上 $short（bindService 返回 false：组件不存在或权限不够）")
            return null
        }
        val wait = remaining(deadline).coerceAtMost(TIMEOUT_MS)
        return try {
            if (wait <= 0 || !connection.latch.await(wait, TimeUnit.MILLISECONDS)) {
                XLog.w("$tag：$short 在 ${wait}ms 内没连上，放弃这一次")
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
     * 真发那条事务 —— **在共用那一条后台线程上发，这里只等剩下的预算**（理由见类注释第 2 条）。
     *
     * @param deadline 这一次调用的总预算终点（`uptimeMillis`）
     * @return 事务的读值；超时与抛异常都是 `null`（两种情形各有一条 `w`，断在哪一环能看出来）
     */
    private fun transact(
        binder: IBinder,
        call: Call,
        deadline: Long,
        read: ((Parcel) -> Int)?,
    ): Int? {
        val task =
            worker.submit<Int?> {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(call.aidl.descriptor)
                    binder.transact(call.code, data, reply, TRANSACT_SYNC)
                    //  服务端的 `enforceCallingPermission` 失败会从这里抛出来（SecurityException）
                    reply.readException()
                    read?.invoke(reply) ?: 0
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        val wait = remaining(deadline).coerceAtMost(TIMEOUT_MS)
        if (wait <= 0) {
            XLog.w("${call.tag}：预算已经用完，事务 ${call.code} 没发就放弃")
            return null
        }
        return try {
            task.get(wait, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            //  对端卡在 `mCondition.await()` 上：这条线程会一直挂着（binder 调用没有超时参数），
            //  但**只挂这一条**（单线程池），不会再为下一次调用新建线程、也不再泄一份 Parcel。
            XLog.w(
                "${call.tag}：事务 ${call.code} 在 ${wait}ms 内没返回（对端绑定时会无限等），这一次算失败" +
                    "（那条线程继续挂着，本进程不再为它新建线程）",
                e,
            )
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            XLog.w("${call.tag}：等事务 ${call.code} 返回时被打断", e)
            null
        } catch (e: java.util.concurrent.ExecutionException) {
            val failure = e.cause ?: e
            XLog.w("${call.tag}：事务 ${call.code} 失败：${failure.javaClass.simpleName}: ${failure.message}", failure)
            null
        }
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
