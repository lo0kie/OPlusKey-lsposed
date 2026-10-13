package com.lookie.opluskey.page

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.key
import com.lookie.opluskey.KeyModule
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Config
import com.lookie.opluskey.config.ConfigBroadcast
import com.lookie.opluskey.config.ConfigStore
import com.lookie.opluskey.config.KeyId

/** 就地露出宿主原版快捷键页面：宿主 Activity 是 singleInstance（真机 aapt 核对过），再 start 只走 onNewIntent、hook 没机会跳过换页，只能藏掉模块页面就地切换、切回靠系统返回拦截。 */
internal fun SettingsPage.openStockPage() {
    XLog.i("菜单：打开自带的快捷键界面（就地露出宿主原版页面）")
    onOpenStock?.invoke()
}

/** 重置所有配置为出厂默认（默认不接管侧键，重置后需重新启用）。 */
internal fun SettingsPage.resetAll() {
    XLog.i("菜单：重置所有配置")
    if (write(Config.DEFAULT, "reset_all")) toast("已重置")
}

/** 重启模块：杀宿主主进程（com.oplus.gesture，不是自身 :ui）让系统把可见页面整棵拉起；AlarmManager 自启会被判后台启动拦截。点了没反应时 logcat -s OplusKey 的重启日志就是判据，页面上也会弹一条提示（2026-10-13 起）。 */
internal fun SettingsPage.restartModule() {
    XLog.i("菜单：重新启动模块 —— 杀宿主主进程")
    tickNow()
    val main = hostMainPids()
    // 只打本包相关的进程（整机那张表一行几 KB，没必要）。真机上报「点了没反应」时，
    // 这三行就是判据：`main` 为空 ⇒ [hostMainPids] 两条路都没摸着。
    val mine = context.getSystemService(android.app.ActivityManager::class.java)
        ?.runningAppProcesses
        ?.filter { it.processName == KeyModule.PAGE_PACKAGE }
        ?.joinToString(prefix = "[", postfix = "]") { "${it.processName}:${it.pid}" }
        ?: "null"
    XLog.i("重启：宿主主进程 pid=$main（本进程 ${android.os.Process.myPid()}） 本包进程=$mine")
    if (main.isEmpty()) {
        XLog.i("重启：拿不到宿主主进程，放弃")
        // 点了没反应最容易被当成「按钮坏了」—— 这一句把「没找到进程」与「杀掉了」分开
        toast("没找到宿主进程，重启失败")
        return
    }
    main.forEach { pid ->
        XLog.i("重启：杀主进程 pid=$pid")
        android.os.Process.killProcess(pid)
    }
    // 杀完等系统把宿主主进程重新拉起（本页跑在 `:ui` 进程里，不受影响）。
    //  这一条是「菜单那个重启」的反馈：不带它的话，用户只能靠「过一会儿动作生效了没有」猜。
    toast("已重启模块")
}

/** 宿主主进程 pid 列表：runningAppProcesses 与 /proc cmdline 两条路互为兜底，均排除自身 :ui 进程。 */
private fun SettingsPage.hostMainPids(): List<Int> {
    val pkg = KeyModule.PAGE_PACKAGE
    val me = android.os.Process.myPid()
    val out = LinkedHashSet<Int>()

    runCatching {
        context.getSystemService(android.app.ActivityManager::class.java)
            ?.runningAppProcesses
            ?.forEach { if (it.processName == pkg && it.pid != me) out += it.pid }
    }.onFailure { XLog.w("重启：runningAppProcesses 失败：${it.javaClass.simpleName}") }

    runCatching {
        java.io.File("/proc").listFiles { f ->
            f.isDirectory && f.name.isNotEmpty() && f.name.all { ch -> ch.isDigit() }
        }?.forEach { dir ->
            val cmdline = java.io.File(dir, "cmdline").let { f ->
                if (f.canRead()) f.readText().trim('\u0000', ' ', '\n') else ""
            }
            if (cmdline == pkg && dir.name.toInt() != me) out += dir.name.toInt()
        }
    }.onFailure { XLog.w("重启：扫 /proc 失败：${it.javaClass.simpleName}") }

    return out.toList()
}

/** 切「振动反馈」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setVibrate(on: Boolean) {
    val cfg = config.value
    write(cfg.copy(global = cfg.global.copy(vibrate = on)), "vibrate=${if (on) 1 else 0}")
}

/**
 * 切「立即触发单击」（双击为无操作时不等双击窗口）—— 行点击与开关共用这一个入口。
 *
 * 语义与判定侧见 `GlobalConfig.instantSingle` / `gesture/GestureDetector.onKeyUp`。
 */
internal fun SettingsPage.setInstantSingle(on: Boolean) {
    val cfg = config.value
    write(
        cfg.copy(global = cfg.global.copy(instantSingle = on)),
        "instant_single=${if (on) 1 else 0}",
    )
}

/**
 * 切「执行时提示」（执行命令时弹一条 Toast）—— 行点击与开关共用这一个入口。
 * 2026-10-13 起从每条命令自己的字段提成全局开关（见 `GlobalConfig.toastOnRun`）；
 * 判定侧在 `hook/CustomCommandRunner`，宿主进程每次执行现读配置，拨完立刻生效。
 */
internal fun SettingsPage.setToastOnRun(on: Boolean) {
    val cfg = config.value
    write(
        cfg.copy(global = cfg.global.copy(toastOnRun = on)),
        "toast_on_run=${if (on) 1 else 0}",
    )
}


/** 写 [KeyConfig.enabled] 总闸：关闭后绑定页控件置灰，侧键不再被接管。 */
internal fun SettingsPage.setKeyEnabled(on: Boolean) {
    val cfg = config.value
    write(
        cfg.copy(keys = cfg.keys + (KeyId.PLUS to cfg.key(KeyId.PLUS).copy(enabled = on))),
        "plus_enabled=${if (on) 1 else 0}",
    )
}

/** 切「长按连续触发」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setLongRepeat(on: Boolean) {
    val cfg = config.value
    write(
        cfg.copy(
            keys = cfg.keys + (KeyId.PLUS to cfg.key(KeyId.PLUS).copy(longRepeat = on)),
        ),
        "long_repeat=${if (on) 1 else 0}",
    )
}

/**
 * 写配置并同步 [config] 状态；每次写盘把完整配置广播给注入侧，让参数改动立即生效。
 * 落盘失败就什么都不提交（不更新 [config]、不发广播）—— 早先「先更新状态、失败也照发广播」
 * 会三条腿各说各话（界面新值、文件旧值、注入侧按新广播吞键）。
 *
 * @return 是否真的落盘 —— 调用方据此决定要不要报「已…」（失败时这里已报过「保存失败」）。
 */
internal fun SettingsPage.write(next: Config, what: String): Boolean {
    var saved = false
    try {
        saved = ConfigStore.write(next)
        if (saved) {
            XLog.i("设置页写入：$what")
        } else {
            XLog.w("设置页写入失败（所有候选路径都不可写）：$what")
        }
    } catch (e: Throwable) {
        XLog.w("设置页写配置失败：$what", e)
    }
    if (!saved) {
        toast("保存失败")
        return false
    }
    config.value = next
    publishConfig(next)
    return true
}

/** 把完整配置广播给注入侧（hook/ConfigChannel，跑在 system_server）。 */
internal fun SettingsPage.publishConfig(config: Config) {
    try {
        context.sendBroadcast(
            ConfigBroadcast.intent(config).setPackage(context.packageName),
        )
        XLog.i("配置广播已发出（enabled=${config.key(KeyId.PLUS).enabled}）")
    } catch (e: Throwable) {
        XLog.w("发配置广播失败：${e.message}")
    }
}
