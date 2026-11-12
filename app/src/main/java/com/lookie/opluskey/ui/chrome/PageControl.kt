package com.lookie.opluskey.ui.chrome

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.host.action.requestSystemToast
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.tickNow
import com.lookie.opluskey.ui.wire.registerLocalReceiver

// 动**宿主页面与进程**的那几件：就地露出宿主原版页、重启服务（连本页那个 `:ui` 进程一起重来）。
//
// 与配置那两族分开：这里一行配置都不写，动的是「进程还在不在、页面显示谁」。

/** 就地露出宿主原版快捷键页面：宿主 Activity 是 singleInstance（真机 aapt 核对过），再 start 只走 onNewIntent、hook 没机会跳过换页，只能藏掉模块页面就地切换、切回靠系统返回拦截。 */
internal fun SettingsPage.openStockPage() {
    XLog.w("菜单：打开自带的快捷键界面（就地露出宿主原版页面）")
    hooks.onOpenStock?.invoke()
}

/**
 * 重启服务：**两层一起重来** —— 宿主主进程（[HostApp.PACKAGE]，手势劫持装在那一侧）与本页自己
 * 所在的 `:ui` 进程（[HostApp.PAGE_PROCESS]，界面代码换了新包只有重进才加载得到）。
 * 点了没反应时 `logcat -s OplusKey` 的重启日志就是判据，页面上也会弹一条提示（。
 *
 *  真正动手的是**宿主主进程**（`host/PageRelaunch`）：本页杀了自己就没人能把页面叫回来，
 *  而「从进程外叫」那条路走不通 —— 真机实测闹钟挂 `PendingIntent` 拉起 Activity 会被后台启动策略
 *  拦掉（`ActivityTaskManager: Background activity launch blocked!`，判据见 `docs/features/zoom-window/api-extra.md`
 *  附十三之五）；宿主的进程自己 `startActivity` 才放行。
 *  所以本页只做三件事：确认宿主主进程在、把那一声请求发出去、然后杀掉自己。
 *
 *  叫「服务」不叫「模块」：这两步都不动 system_server 里那份注入（那个只能整机重启）。
 * 菜单上的文案跟着这条走，别再写回「重启模块」。
 */
internal fun SettingsPage.restartModule() {
    XLog.w("菜单：重启服务 —— 页面进程与宿主主进程各重来一遍")
    tickNow()
    //  **这一趟不能在主线程上做**：`runningAppProcesses` 是一次 binder（整机几百条记录），
    //  再叠上 `/proc/<pid>/cmdline` **逐个读文件**（这台机器上三百多个进程目录）——
    //  主线程做完这一圈，用户看到的是「菜单点下去、界面僵住半秒才动」。
    //  查进程在后台，收尾（提示 / 发请求 / 杀自己）回主线程。
    Thread(
        {
            val main = hostMainPids()
            val mine = mineProcesses()
            android.os.Handler(android.os.Looper.getMainLooper()).post { finishRestart(main, mine) }
        },
        "opluskey-restart",
    ).apply { isDaemon = true }.start()
}

/**
 * [restartModule] 的收尾 —— 回主线程之后的那一半（提示与状态都要在主线程碰）。
 *
 * @param main 宿主主进程的 pid 列表（空 = 两条路都没摸着）
 * @param mine 本包此刻的进程表，**只给日志用**（「重启没反应」时的判据）
 */
private fun SettingsPage.finishRestart(
    main: List<Int>,
    mine: String,
) {
    // 只打本包相关的进程（整机那张表一行几 KB，没必要）。真机上报「点了没反应」时，
    // 这三行就是判据：`main` 为空 ⇒ [hostMainPids] 两条路都没摸着。
    XLog.w("重启：宿主主进程 pid=$main（本进程 ${android.os.Process.myPid()}） 本包进程=$mine")
    if (main.isEmpty()) {
        XLog.w("重启：宿主主进程不在，没人能把页面拉回来，放弃")
        // 点了没反应最容易被当成「按钮坏了」—— 这一句把「没找到进程」与「杀掉了」分开。
        //  文案里不提「宿主进程」：那是内部结构（用户界面不需要技术细节），只回答成没成。
        hint(I18n.get(Strings.TOAST_SERVICE_RESTART_FAILED, "重启失败：请手动重开一次设置页"))
        return
    }
    // 把「把页面拉回来 + 自己重来一遍」整个交给宿主主进程（见 host/PageRelaunch）。
    //  请求发不出去时**不能**杀自己：那样页面没了、也没人再把它叫回来。
    //
    //  **发出去了也不等于有人接**：`sendBroadcast` 不抛只说明系统受理了这条广播。宿主主进程那份
    //  代码是旧的（接收器里还没有这一支）、或者签名闸把接收器拦下了时，本条广播就是发给了空气 ——
    //  那时杀自己同样等于把页面弄丢。所以先等宿主那条回执（[ModuleWire.ACTION_PAGE_RELAUNCH_ACK]）：
    //  收到才杀，等不到就报失败、留在原地。
    val acked =
        java.util.concurrent.atomic
            .AtomicBoolean(false)
    val receiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                ctx: android.content.Context?,
                intent: android.content.Intent?,
            ) {
                if (!acked.compareAndSet(false, true)) return
                XLog.w("重启：宿主主进程回了回执（已接下这一轮），${PAGE_KILL_DELAY_MS}ms 后杀本进程")
                // 回执用**注入侧**那条通道弹：本进程马上要死，页面里的提示条留不住；
                // 注入侧（system_server）那个 toast 不随这两个进程消失。
                requestSystemToast(context, I18n.get(Strings.TOAST_SERVICE_RESTARTED, "已重启服务"))
                killPageProcess()
            }
        }
    if (!context.registerLocalReceiver("重启服务：等宿主回执", receiver, ModuleWire.ACTION_PAGE_RELAUNCH_ACK)) {
        hint(I18n.get(Strings.TOAST_SERVICE_RESTART_FAILED, "重启失败：请手动重开一次设置页"))
        return
    }
    val asked =
        PageWire.send(
            context,
            HostApp.PACKAGE,
            android.content.Intent(ModuleWire.ACTION_PAGE_RELAUNCH),
        )
    if (!asked) {
        hint(I18n.get(Strings.TOAST_SERVICE_RESTART_FAILED, "重启失败：请手动重开一次设置页"))
        return
    }
    XLog.w("重启：已请宿主主进程稍后拉起页面（等回执，最多 ${ACK_WAIT_MS}ms）")
    // 等不到回执 ⇒ 没人接这一轮：不杀自己，把「没成」说出来（以前这一档是静默自杀）。
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        if (acked.compareAndSet(false, true)) {
            XLog.w("重启：${ACK_WAIT_MS}ms 内没收到宿主回执 ⇒ 放弃（不杀本进程，页面留着）")
            runCatching { context.unregisterReceiver(receiver) }
            hint(I18n.get(Strings.TOAST_SERVICE_RESTART_FAILED, "重启失败：请手动重开一次设置页"))
        }
    }, ACK_WAIT_MS)
}

/** 等宿主回执的上限：同 uid 进程间一条广播的往返是毫秒级，1 秒已经够宽裕（等不到就是不接）。 */
private const val ACK_WAIT_MS = 1_000L

/** 本包此刻的进程表（`包名:pid` 拼成一行）—— 只进日志。
 *
 *  过滤条件是「主进程**或它的子进程**」（`:ui` 才是本页所在的进程）：只比 `== HostApp.PACKAGE`
 *  的话这一行永远只列出主进程，而它存在的意义正是「重启时本包都有哪些进程」。
 */
private fun SettingsPage.mineProcesses(): String =
    context
        .getSystemService(android.app.ActivityManager::class.java)
        ?.runningAppProcesses
        ?.filter { it.processName == HostApp.PACKAGE || it.processName.startsWith(HostApp.PACKAGE + ":") }
        ?.joinToString(prefix = "[", postfix = "]") { "${it.processName}:${it.pid}" }
        ?: "null"

/**
 * 延迟一点杀掉本页所在的 `:ui` 进程 —— 界面层换了新包之后，只有整个进程重来才会加载新代码。
 *
 *  延迟是留给上面那条提示与那条请求的：提示由系统渲染、请求已经交给宿主主进程，本进程随后死掉
 *  都不影响它们。
 */
private fun SettingsPage.killPageProcess() {
    val pid = android.os.Process.myPid()
    XLog.w("重启：${PAGE_KILL_DELAY_MS}ms 后杀本进程 pid=$pid")
    // 用独立线程而不是主线程 postDelayed：主线程这时可能正忙着收尾这一下点击，睡眠不该占着它；
    // 这个线程活到进程消失为止，不需要回收。
    Thread {
        runCatching { Thread.sleep(PAGE_KILL_DELAY_MS) }
        android.os.Process.killProcess(pid)
    }.start()
}

/** 杀本进程前留给那条提示与请求入队的时间：太短可能还没出门，太长用户会先看到菜单卡在那儿。 */
private const val PAGE_KILL_DELAY_MS = 400L

/** 宿主主进程 pid 列表：runningAppProcesses 与 /proc cmdline 两条路互为兜底，均排除自身 :ui 进程。 */
private fun SettingsPage.hostMainPids(): List<Int> {
    val pkg = HostApp.PACKAGE
    val me = android.os.Process.myPid()
    val out = LinkedHashSet<Int>()

    runCatching {
        context
            .getSystemService(android.app.ActivityManager::class.java)
            ?.runningAppProcesses
            ?.forEach { if (it.processName == pkg && it.pid != me) out += it.pid }
    }.onFailure { XLog.w("重启：runningAppProcesses 失败：${it.javaClass.simpleName}") }

    runCatching {
        java.io
            .File("/proc")
            .listFiles { f ->
                f.isDirectory && f.name.isNotEmpty() && f.name.all { ch -> ch.isDigit() }
            }?.forEach { dir ->
                val cmdline =
                    java.io.File(dir, "cmdline").let { f ->
                        if (f.canRead()) {
                            // 同 KeyModule.currentProcessName：只认第一个 `\0` 之前那段，
                            // 否则带 argv 的进程永远匹配不上，重启就少杀一个。
                            f.readText().takeWhile { it != '\u0000' }.trim()
                        } else {
                            ""
                        }
                    }
                if (cmdline == pkg && dir.name.toInt() != me) out += dir.name.toInt()
            }
    }.onFailure { XLog.w("重启：扫 /proc 失败：${it.javaClass.simpleName}") }

    return out.toList()
}
