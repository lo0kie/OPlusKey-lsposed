package com.lookie.opluskey.ui.hook

import android.os.Build
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.config.ConfigCodec
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.rom.DeviceSupport
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.appVersionText
import com.lookie.opluskey.ui.state.page.deviceModel
import com.lookie.opluskey.ui.state.page.deviceSupport

// 导出的日志**文件头** —— 设备环境、三个进程的应答、界面状态、配置摘要。
//
// 为什么要有：正文全是时序行，答不了「这是谁的机器」「三个进程都活没活」「界面自己说哪里断了」，
// 而这三件恰恰是判断一份日志该怎么读的前提。原先只有版本 + 时间那一行，于是每次排查都要反过来
// 问用户要这几条，用户报「没生效」时又说不清是哪一段没生效。

/** 文件头收尾那一行 —— 下面接的才是按时间合并的日志正文。 */
private const val LOG_SECTION = "==== 日志 ===="

/**
 * 拼这份文件头（不含正文，也不含异常栈 —— 那两段由调用方接在后面）。
 *
 *  全部 `runCatching` 兜住：导出是自救通道，**任何一项读不出来都不该让整份日志导不出**。
 *  读不到的那一项直接少一行，而不是写一句「未知」占位 —— 少一行还看得出缺，占位看不出。
 *
 * @param pulled 另外两个进程的回包（哪几个应答了、各几行、各是哪次构建）
 * @param stamp 导出时刻（`yyyy-MM-dd HH:mm:ss`）
 */
internal fun SettingsPage.logHeader(
    pulled: LogPull.Pulled,
    stamp: String,
): String =
    buildString {
        appendLine("==== 环境 ====")
        appendLine("机型  $deviceModel · ${supportText()}")
        appendLine(
            "系统  ${DeviceSupport.currentRomSeries} · Android ${DeviceSupport.currentAndroid}" +
                "（API ${Build.VERSION.SDK_INT}）",
        )
        lsposedVersion()?.let { appendLine("框架  LSPosed $it") }
        appendLine("模块  ${appVersionText()}")
        appendLine("导出  $stamp")

        //  —— 三个进程 ——
        //  「没回包」与「在跑但 0 行」是两件事：前者进程没起来 / 没装模块，后者只是还没打过日志。
        //  界面那一份是本进程的，不走回包，所以单独一支（它的行数还要报被裁掉多少）。
        appendLine("==== 进程 ====")
        appendLine(processLine(pulled, "system", "注入侧", local = false))
        appendLine(processLine(pulled, "host", "宿主", local = false))
        appendLine(processLine(pulled, "ui", "界面", local = true))

        //  —— 界面自己那份结论 ——
        //  这是排查时第一眼看的（「需不需要重启 / 是哪一段断了」），原先只活在界面上。
        //  读的是 `HostLinkState`（页面自己那份状态，非组合期也能取）；**不走 `linkFacts`**：
        //  那个 getter 里有组合期依赖，导出这条路不在组合期。
        appendLine("==== 状态 ====")
        appendLine("主开关  ${if (configState.config.value.global.enabled) "开" else "关"}")
        //  取一次存进局部：`injectStatus` 是委托属性，直接连写三处编译器不让智能转换
        val inj = link.injectStatus
        appendLine(
            "注入侧  " +
                when {
                    !link.injectChecked -> "还在自检（这一趟还没问完）"
                    inj == null -> "没有回包"
                    inj.hooks > 0 -> "已接管（${inj.hooks} 个 Hook）" + buildAt(inj.buildId)
                    else -> "回了包，但接管点一个都没挂上"
                },
        )
        appendLine("宿主    " + if (link.hostServiceUp) "在跑（报活正常）" else "没报活")
        appendLine(
            "配置    " +
                when {
                    configState.readOnly -> "整页只读（建页那次没读成功）"
                    !link.hostConfigReadable -> "执行侧读不懂这份文件"
                    else -> "可读"
                },
        )

        //  —— 配置摘要 ——
        //  「配置读不懂」是最容易被误判成「模块坏了」的一档，所以版本号与两个只读标志都摆出来。
        appendLine("==== 配置 ====")
        appendLine(
            "版本    ${ConfigCodec.VERSION}（模块）" +
                (if (configState.readOnly) " · 整页只读" else "") +
                (if (configState.commandsReadOnly) " · 命令库读不懂" else ""),
        )
        val combos = configState.config.value.combos
        appendLine("组合    ${combos.size} 档（等待 ${combos.count { it.isWait }} / 按住 ${combos.count { it.isHold }}）")
        appendLine("常用    ${configState.config.value.global.commonCommands.size} 条")
        appendLine("侧键    长按 ${configState.config.value.key(KeyId.PLUS).longPressMs}ms")

        appendLine(LOG_SECTION)
    }

/**
 * 一个进程那一行 —— `system  注入侧  在跑 · 1342 行 @2026-11-12-0002`。
 *
 * @param local 本进程（设置页自己）：它的行数与被裁条数直接从 [XLog] 取，不走回包
 */
private fun SettingsPage.processLine(
    pulled: LogPull.Pulled,
    proc: String,
    label: String,
    local: Boolean,
): String {
    val head = "$proc  $label  "
    if (local) {
        val lines = XLog.lines().size
        val dropped = XLog.droppedLines()
        //  被裁掉多少条必须写出来：日志开头缺一截时，看不出是「没发生过」还是「发生过但没留下来」
        val cut = if (dropped > 0) "（更早的 $dropped 条已被裁掉）" else ""
        return "$head 在跑 · $lines 行$cut @${ModuleInfo.BUILD_ID}"
    }
    if (proc !in pulled.answered) {
        return "$head **没有回包**（进程没起来 / 没装模块 / 广播没到）"
    }
    val lines = pulled.lines[proc] ?: 0
    val build = buildAt(pulled.build[proc].orEmpty())
    return "$head 在跑 · $lines 行$build"
}

/** `@构建标识` 那一段；对面是还没带这一位的旧版时明说，而不是留空。 */
private fun buildAt(build: String): String = if (build.isEmpty()) " @旧版（回包不带构建标识）" else " @$build"

/** 适配清单的三档结论（与「关于」页那一行同一份判据，措辞压短）。 */
private fun SettingsPage.supportText(): String =
    when (deviceSupport) {
        DeviceSupport.Support.ADAPTED -> "已适配"
        DeviceSupport.Support.PENDING -> "此系统版本未实测"
        DeviceSupport.Support.UNSUPPORTED -> "此型号未实测"
    }

/**
 * LSPosed 管理器的版本号 —— 读不到就返回 `null`（那一行整行不写）。
 *
 *  管理器不一定是这个包名（有分支版），所以失败是常态、不是异常。
 */
private fun SettingsPage.lsposedVersion(): String? =
    runCatching { context.packageManager.getPackageInfo("org.lsposed.manager", 0).versionName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
