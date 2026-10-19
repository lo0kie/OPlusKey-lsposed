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
            if (!isConnecting) {
                isConnecting = true
                try {
                    val intent =
                        Intent(ACTION_SEEDLING_SERVICE).apply {
                            setPackage(SYSTEMUI_PACKAGE)
                        }
                    // 用 Application 去绑：传进来的可能是 Activity，绑住的连接会把它一起钉住。
                    val app = context.applicationContext ?: context
                    val bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    XLog.w("绑定流体云服务结果: $bound")
                    if (!bound) {
                        isConnecting = false
                        pendingMessages.clear()
                    } else {
                        // **绑上 ≠ 连上**：SystemUI 卡住 / 服务没起来时 `onServiceConnected` 永远不来，
                        //  `isConnecting` 就一直是 true ⇒ 之后所有消息只往队列里堆、再也不发起重绑。
                        handler.postDelayed(
                            {
                                synchronized(lock) {
                                    if (isConnecting && serverMessenger == null) {
                                        isConnecting = false
                                        XLog.w("流体云服务 ${CONNECT_TIMEOUT_MS}ms 未连上 ⇒ 复位，下一条消息重新绑定")
                                    }
                                }
                            },
                            CONNECT_TIMEOUT_MS,
                        )
                    }
                } catch (e: Throwable) {
                    XLog.w("绑定流体云服务异常：${e.javaClass.simpleName}: ${e.message}")
                    isConnecting = false
                    pendingMessages.clear()
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
