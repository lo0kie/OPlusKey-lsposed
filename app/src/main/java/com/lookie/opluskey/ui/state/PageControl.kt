package com.lookie.opluskey.ui.state

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.SettingsPage

// 动**宿主页面与进程**的那几件：就地露出宿主原版页、重启服务（连本页那个 `:ui` 进程一起重来）。
//
// 与配置那两族分开：这里一行配置都不写，动的是「进程还在不在、页面显示谁」。

/** 就地露出宿主原版快捷键页面：宿主 Activity 是 singleInstance（真机 aapt 核对过），再 start 只走 onNewIntent、hook 没机会跳过换页，只能藏掉模块页面就地切换、切回靠系统返回拦截。 */
internal fun SettingsPage.openStockPage() {
    XLog.w("菜单：打开自带的快捷键界面（就地露出宿主原版页面）")
    onOpenStock?.invoke()
}

/**
 * 重启服务：**两层一起重来** —— 宿主主进程（[HostApp.PACKAGE]，手势劫持装在那一侧）与本页自己
 * 所在的 `:ui` 进程（[HostApp.PAGE_PROCESS]，界面代码换了新包只有重进才加载得到）。
 * 点了没反应时 `logcat -s OplusKey` 的重启日志就是判据，页面上也会弹一条提示（。
 *
 *  真正动手的是**宿主主进程**（`host/PageRelaunch`）：本页杀了自己就没人能把页面叫回来，
 *  而「从进程外叫」那条路走不通 —— 真机实测闹钟挂 `PendingIntent` 拉起 Activity 会被后台启动策略
 *  拦掉（`ActivityTaskManager: Background activity launch blocked!`，判据见 `docs/device-findings.md`
 *  附十三之五）；宿主的进程自己 `startActivity` 才放行。
 *  所以本页只做三件事：确认宿主主进程在、把那一声请求发出去、然后杀掉自己。
 *
 *  叫「服务」不叫「模块」：这两步都不动 system_server 里那份注入（那个只能整机重启）。
 * 菜单上的文案跟着这条走，别再写回「重启模块」。
 */
internal fun SettingsPage.restartModule() {
    XLog.w("菜单：重启服务 —— 页面进程与宿主主进程各重来一遍")
    tickNow()
    val main = hostMainPids()
    // 只打本包相关的进程（整机那张表一行几 KB，没必要）。真机上报「点了没反应」时，
    // 这三行就是判据：`main` 为空 ⇒ [hostMainPids] 两条路都没摸着。
    val mine =
        context
            .getSystemService(android.app.ActivityManager::class.java)
            ?.runningAppProcesses
            ?.filter { it.processName == HostApp.PACKAGE }
            ?.joinToString(prefix = "[", postfix = "]") { "${it.processName}:${it.pid}" }
            ?: "null"
    XLog.w("重启：宿主主进程 pid=$main（本进程 ${android.os.Process.myPid()}） 本包进程=$mine")
    if (main.isEmpty()) {
        XLog.w("重启：宿主主进程不在，没人能把页面拉回来，放弃")
        // 点了没反应最容易被当成「按钮坏了」—— 这一句把「没找到进程」与「杀掉了」分开。
        //  文案里不提「宿主进程」：那是内部结构（用户界面不需要技术细节），只回答成没成。
        toast(I18n.get(Strings.TOAST_SERVICE_RESTART_FAILED, "重启失败，请稍后重试"))
        return
    }
    // 把「把页面拉回来 + 自己重来一遍」整个交给宿主主进程（见 host/PageRelaunch）。
    //  请求发不出去时**不能**杀自己：那样页面没了、也没人再把它叫回来。
    val asked =
        PageWire.send(
            context,
            HostApp.PACKAGE,
            android.content.Intent(ModuleWire.ACTION_PAGE_RELAUNCH),
        )
    if (!asked) {
        toast(I18n.get(Strings.TOAST_SERVICE_RESTART_FAILED, "重启失败，请稍后重试"))
        return
    }
    XLog.w("重启：已请宿主主进程稍后拉起页面")
    // 这一条是「菜单那个重启」的反馈：不带它的话，用户只能靠「过一会儿动作生效了没有」猜。
    toast(I18n.get(Strings.TOAST_SERVICE_RESTARTED, "已重启服务"))
    killPageProcess()
}

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
                        if (f.canRead()) f.readText().trim('\u0000', ' ', '\n') else ""
                    }
                if (cmdline == pkg && dir.name.toInt() != me) out += dir.name.toInt()
            }
    }.onFailure { XLog.w("重启：扫 /proc 失败：${it.javaClass.simpleName}") }

    return out.toList()
}
