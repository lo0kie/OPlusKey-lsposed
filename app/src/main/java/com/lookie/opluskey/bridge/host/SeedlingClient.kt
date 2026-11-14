package com.lookie.opluskey.bridge.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.lookie.opluskey.core.log.XLog

/**
 * ColorOS 流体云（Seedling / 悬浮胶囊通知）客户端。
 *
 * 通过绑定 SystemUI 内部的 `BaseService` 并发送 Messenger 消息，触发原生流体云悬浮胶囊展示：
 * - 响铃模式流体云（`ringModeEvent`）：静音（0）、振动（1）、响铃（2）
 *
 * 运行在宿主进程 `com.oplus.gesture`（拥有 `com.oplus.seedlingservice.permission.BASE_SERVICE` 权限）。
 */
internal object SeedlingClient {
    private const val ACTION_SEEDLING_SERVICE = "com.oplus.seedlingservice.action.SEEDLING_SERVICE"
    private const val SYSTEMUI_PACKAGE = "com.android.systemui"

    private const val MSG_WHAT_SEEDLING_UPDATE = 1
    private const val KEY_SEEDLING_EVENT = "seedling_event"
    private const val EVENT_RING_MODE = "ringModeEvent"
    private const val KEY_RING_MODE_TYPE = "ringModeType"

    @Volatile
    private var serverMessenger: Messenger? = null

    @Volatile
    private var isConnecting = false

    /** 「绑上了但一直没连上」的复位闸（见 [send]）。 */
    private const val CONNECT_TIMEOUT_MS = 3_000L

    private val handler = Handler(Looper.getMainLooper())
    private val pendingMessages = mutableListOf<Bundle>()
    private val lock = Any()

    /**
     * 队列上限 —— 服务一直连不上时，只保留**最近**这么多条。
     *
     *  这些消息是「响铃档位变了」这类瞬时通知：连上之后补发十条旧档位毫无意义，
     *  反而会按顺序把界面刷过一遍。原先没有上限，SystemUI 长期不响应就一直堆。
     */
    private const val MAX_PENDING = 16

    /** 上一次 `bindService` 用的 Application（`onBindingDied` 要拿它 `unbindService`）。 */
    @Volatile
    private var boundContext: Context? = null

    /** 绑定的轮次（每发起一次 `bindService` 加一）—— 超时任务靠它认「自己是不是当前这一轮」。 */
    private var bindGeneration = 0

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val messenger = Messenger(service)
                val toSend: List<Bundle>
                synchronized(lock) {
                    serverMessenger = messenger
                    isConnecting = false
                    toSend = pendingMessages.toList()
                    pendingMessages.clear()
                }
                XLog.w("流体云服务已连接（$name），发送队列中 ${toSend.size} 条消息")
                for (bundle in toSend) {
                    sendDirect(messenger, bundle)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                XLog.w("流体云服务已断开（$name）")
                synchronized(lock) {
                    serverMessenger = null
                    isConnecting = false
                }
            }

            override fun onBindingDied(name: ComponentName?) {
                XLog.w("流体云服务绑定失效（$name）")
                synchronized(lock) {
                    serverMessenger = null
                    isConnecting = false
                }
                //  必须**解绑**：绑定死了但连接还挂着，之后再 `bindService` 会直接复用那条
                //  死连接（`onServiceConnected` 不再来），表现是流体云从此一条都收不到。
                unbindQuietly()
            }
        }

    /** 静默解绑 —— 没绑上 / 已经解过都只是空操作（`unbindService` 会抛 IllegalArgumentException）。 */
    private fun unbindQuietly() {
        val app = boundContext
        boundContext = null
        if (app == null) return
        try {
            app.unbindService(connection)
        } catch (e: Throwable) {
            XLog.d("解绑流体云服务失败（忽略）：${e.javaClass.simpleName}")
        }
    }

    /**
     * 通知 ColorOS 流体云展示响铃模式切换。
     *
     * @param context 宿主上下文
     * @param mode AudioManager 对应的档位：0=静音, 1=振动, 2=响铃
     */
    fun notifyRingMode(
        context: Context,
        mode: Int,
    ) {
        val bundle =
            Bundle().apply {
                putString(KEY_SEEDLING_EVENT, EVENT_RING_MODE)
                putInt(KEY_RING_MODE_TYPE, mode)
            }
        send(context, bundle)
    }

    private fun send(
        context: Context,
        bundle: Bundle,
    ) {
        val messenger = serverMessenger
        if (messenger != null) {
            if (sendDirect(messenger, bundle)) return
            synchronized(lock) {
                serverMessenger = null
            }
        }

        synchronized(lock) {
            pendingMessages.add(bundle)
            // 只留最近 [MAX_PENDING] 条（见那个常量的说明）：连不上的时候不能无限堆。
            while (pendingMessages.size > MAX_PENDING) {
                pendingMessages.removeAt(0)
            }
            if (!isConnecting) {
                isConnecting = true
                //  这一次绑定的轮次 —— 那枪超时任务靠它认「我还是不是当前这一轮」：
                //  上一轮的超时任务若在新一轮绑定期间才醒过来，它会把**新一轮**的连接掐掉
                //  （`isConnecting` 又被置回 false、连接被解绑），下一轮 `onServiceConnected` 就永远不来。
                val gen = ++bindGeneration
                //  重绑之前先解掉上一条：连接还挂着时再 bindService 会直接复用那条死连接
                //  （`onServiceConnected` 不再来），表现是流体云从此一条都收不到。
                unbindQuietly()
                try {
                    val intent =
                        Intent(ACTION_SEEDLING_SERVICE).apply {
                            setPackage(SYSTEMUI_PACKAGE)
                        }
                    // 用 Application 去绑：传进来的可能是 Activity，绑住的连接会把它一起钉住。
                    val app = context.applicationContext ?: context
                    val bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    if (bound) boundContext = app
                    XLog.w("绑定流体云服务结果: $bound")
                    if (!bound) {
                        //  **队列不清**：刚才那一条还在里头，下一条消息会再发起一次绑定并把它一起补发。
                        //  清掉等于「这一次的响铃档位永远到不了界面」，而绑失败多半只是这一次不走运。
                        isConnecting = false
                    } else {
                        // **绑上 ≠ 连上**：SystemUI 卡住 / 服务没起来时 `onServiceConnected` 永远不来，
                        //  `isConnecting` 就一直是 true ⇒ 之后所有消息只往队列里堆、再也不发起重绑。
                        handler.postDelayed(
                            {
                                val stale =
                                    synchronized(lock) {
                                        //  只认自己那一轮：`gen` 变了说明这枪已经过期，
                                        //  再动就会掐掉新一轮的连接（见上面 `val gen` 那句）
                                        if (gen != bindGeneration) {
                                            false
                                        } else if (isConnecting && serverMessenger == null) {
                                            isConnecting = false
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                if (stale) {
                                    XLog.w(
                                        "流体云服务 ${CONNECT_TIMEOUT_MS}ms 未连上 ⇒ 解掉这条死绑定，" +
                                            "下一条消息重新绑",
                                    )
                                    //  **必须解绑**（与 `onBindingDied` 同一条理由）：连接还挂着时再
                                    //  `bindService` 会直接复用那条死连接、`onServiceConnected` 不再来
                                    //  —— 表现就是流体云从此一条都收不到。
                                    unbindQuietly()
                                }
                            },
                            CONNECT_TIMEOUT_MS,
                        )
                    }
                } catch (e: Throwable) {
                    XLog.w("绑定流体云服务异常：${e.javaClass.simpleName}: ${e.message}")
                    //  同上：队列留着，下一条消息重新绑时一起补发
                    isConnecting = false
                }
            }
        }
    }

    private fun sendDirect(
        messenger: Messenger,
        data: Bundle,
    ): Boolean =
        try {
            val msg =
                Message.obtain().apply {
                    what = MSG_WHAT_SEEDLING_UPDATE
                    this.data = data
                }
            messenger.send(msg)
            XLog.w("向流体云发送消息成功：event=${data.getString(KEY_SEEDLING_EVENT)}, mode=${data.getInt(KEY_RING_MODE_TYPE)}")
            true
        } catch (e: Throwable) {
            XLog.w("向流体云发送消息失败：${e.javaClass.simpleName}: ${e.message}")
            false
        }
}
