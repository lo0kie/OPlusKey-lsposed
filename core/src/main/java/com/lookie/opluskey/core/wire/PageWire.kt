package com.lookie.opluskey.core.wire

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.log.XLog

/**
 * 发往宿主那个包（设置页进程与宿主主进程共用的落点）的**通知广播** —— 发送侧一律经这里。
 *
 *  这几条线的发送方散在 `ui/` 与 `host/` 两处（同包两个进程），收方是同一个包名下的接收器，
 *  所以 `setPackage` 那一位的取值都一样：由调用点传进来（本层不认识「宿主包名」是谁）。
 *
 *  收在这里的是那一条纪律：**发不出去只记一条日志，绝不把宿主进程带走**。「广播发出去」会抛
 *  —— 队列满、进程正在退、binder 死了都算 —— 而这几个调用点分别落在组合期、按键执行路径与
 *  报活路径上，漏出去就是宿主被自己发出去的一声通知打死。原来每个调用点各写一份保护与同一段
 *  说明，口径还分了 `try/catch` 与 `runCatching` 两种。
 *
 *  返回布尔而不是吞掉结果：调用方要据此决定「要不要接着报一句」——
 *  报活那条只记日志即可，重启服务那条**不能**杀自己（`send` 失败就没人把它叫回来了）。
 */
object PageWire {
    /**
     * @param pkg 收方所在的包名（广播因此不再是隐式广播，也只有那一个包的接收器收得到）。
     * @return 是否真的发出去了。失败只记一条 `w` 级判据（带上 action），不外抛。
     */
    fun send(
        context: Context,
        pkg: String,
        intent: Intent,
    ): Boolean {
        val action = intent.action.orEmpty()
        return try {
            context.sendBroadcast(intent.setPackage(pkg))
            true
        } catch (e: Throwable) {
            XLog.w("发给 $pkg 的广播没发出去：$action（${e.javaClass.simpleName}）", e)
            false
        }
    }
}
