package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.ConfigBundle
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.inject.hookGuarded
import com.lookie.opluskey.ui.model.UNKNOWN_CONFIG_FORMAT
import com.lookie.opluskey.ui.screen.appVersionText
import com.lookie.opluskey.ui.screen.saveCommands
import com.lookie.opluskey.ui.state.toast
import com.lookie.opluskey.ui.state.write
import io.github.libxposed.api.XposedModule

/**
 * 配置与日志的导入导出 —— SAF 选择器、请求码、结果回调整套。
 *
 * 结果只能从**宿主的** `Activity#onActivityResult` 收（那个 Activity 不是我们的），
 * 所以 [installFileResultHook] 是这一族唯一的 Hook。
 */
internal object ConfigTransfer {

    /** 「导出日志」的请求码 —— 本进程里区分哪个 startActivityForResult 是我们的。 */
    private const val REQ_EXPORT_LOG = 0x4F4C

    /** 「导出配置」的请求码（同上，只是导出的是配置包而不是日志）。 */
    private const val REQ_EXPORT_CONFIG = 0x4F43

    /** 「导入配置」的请求码 —— 这条是**打开**文件（`ACTION_OPEN_DOCUMENT`），不是保存。 */
    private const val REQ_IMPORT_CONFIG = 0x4943

    /** 导出文件的**内容**时间戳（精确到秒，方便对齐别处的记录）。 */
    private val HEADER_STAMP: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 导出文件的**文件名**时间戳（精确到分，同分钟再导会由系统给「保留两者」）。 */
    private val FILE_STAMP: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    /**
     * 导出包的文件类型 —— 导出交给「保存到」、导入拿来过滤「打开」，两边共用这一个值。
     *
     * 用 `application/octet-stream` 而不是 `application/json`：导出的是混淆过的一层、不是 JSON，
     * 报 json 会让系统建议 `.json` 后缀，别的 App 一开就是一堆解码错误。
     *
     * 注意：**「保存到」对话框按这个 MIME 决定扩展名，会盖掉 title 里写的那个**（真机实测）：
     * title 写 `OPlusKey-<时间戳>.opk` 时，对话框显示的是 `OPlusKey-<时间戳>` + `.bin`
     * —— `application/octet-stream` 对应的就是 `.bin`。所以 [exportConfig] 的 title 直接写 `.bin`，
     * 让对话框显示的名字、盘上的名字、导入时列得出来的名字**三处一致**。
     */
    private const val EXPORT_MIME = "application/octet-stream"

    /** 平台类 `Activity#onActivityResult` 那个 hook 装没装（进程内只装一次）。 */
    private var exportResultHooked = false

    /**
     * 待写盘的导出文本 —— 用户在「保存到」里挑完位置之后由 [writeExportFile] 写进那个 uri。
     *
     * 导出日志与导出配置**共用这一格**：SAF 选择器同一时刻只可能有一个（弹着的时候点不了别处），
     * 结果回来时按请求码就知道这份文本是哪一种。
     */
    private var pendingExport: String? = null

    /**
     * “导出日志”：弹系统的“保存到”选择器（SAF），用户挑完位置再把文本写进那个 uri。
     *
     *  结果只能从宿主的 `Activity#onActivityResult` 收（宿主 Activity 不是我们的），
     * 所以那条路要挂 hook（见 [installFileResultHook]）；文本先存 [pendingExport]。
     */
    internal fun exportLog(activity: android.app.Activity) {
        val body = XLog.snapshot()
        if (body.isEmpty()) {
            HostSession.page?.toast("暂无日志")
            return
        }
        val now = java.time.LocalDateTime.now()
        // 文件头带版本 + 当前时间：日志发出来时不用再问「哪个版本、什么时候的」
        val text = buildString {
            append("OPlusKey ").append(HostSession.page?.appVersionText().orEmpty())
            append(" | ").append(now.format(HEADER_STAMP))
            append("\n\n").append(body).append('\n')
        }
        //  后缀要跟下面那个 `type` 对得上：「保存到」按 MIME 决定扩展名，`text/plain` 对应 `.txt`
        // —— 写 `.log` 也会被对话框换成 `.txt`（同 [EXPORT_MIME] 注释里那条实测）。
        val name = "OPlusKey-" + now.format(FILE_STAMP) + ".txt"
        pendingExport = text
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TITLE, name)
        }
        try {
            activity.startActivityForResult(intent, REQ_EXPORT_LOG)
            XLog.w("导出日志：已弹出保存位置选择器（${body.length} 字符）")
        } catch (e: Throwable) {
            pendingExport = null
            XLog.w("导出日志：拉不起选择器：${e.message}")
            HostSession.page?.toast("导出失败")
        }
    }

    /**
     * 「导出配置」：弹系统的「保存到」选择器（SAF），把**主配置 + 命令库**写成一份文件。
     *
     * 内容由 [ConfigBundle] 合成（JSON 信封，见它的注释）；写盘与导出日志同一条路 ——
     * 结果同样只能从宿主的 `onActivityResult` 收，文本先存 [pendingExport]。
     */
    internal fun exportConfig(activity: android.app.Activity) {
        val page = HostSession.page
        if (page == null) {
            XLog.w("导出配置：页面没了，放弃")
            return
        }
        // 导出**页面手上这份**（= 用户看到的），不是重读一遍文件：两边正常时等价，
        // 而页面这份已经过 `configReadOnly` 那道闸 —— 读不懂的状态下压根进不来这一页。
        val text = ConfigBundle.export(page.config.value, page.commands.value)
        if (text.isEmpty()) {
            page.toast("导出失败")
            return
        }
        val now = java.time.LocalDateTime.now()
        pendingExport = text
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = EXPORT_MIME
            putExtra(
                android.content.Intent.EXTRA_TITLE,
                "OPlusKey-" + now.format(FILE_STAMP) + ".bin",
            )
        }
        try {
            activity.startActivityForResult(intent, REQ_EXPORT_CONFIG)
            XLog.w("导出配置：已弹出保存位置选择器（${text.length} 字符）")
        } catch (e: Throwable) {
            pendingExport = null
            XLog.w("导出配置：拉不起选择器：${e.message}")
            page.toast("导出失败")
        }
    }

    /**
     * 「导入配置」：弹系统的「打开」选择器（SAF），读回文件后写进配置与命令库。
     *
     * 写盘走页面那两条既有出口（`write` / `saveCommands`）—— 不自己碰文件：
     * 只读态拒绝、写盘失败回滚、写完广播给注入侧，这一整套纪律都在那两处。
     * 读回来的内容在结果回调里处理（见 [applyImportedConfig]）。
     */
    internal fun importConfig(activity: android.app.Activity) {
        if (HostSession.page == null) {
            XLog.w("导入配置：页面没了，放弃")
            return
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            //  按导出包的类型过滤（[EXPORT_MIME]）：类型对不上系统直接不显示。
            //  这一档能收窄到「二进制」这一族，但收不到「只有我们自己导的」——
            //  `.bin` / `.data` 之类在系统眼里是同一个类型，而 SAF 不支持按扩展名或文件名过滤。
            //  以前收 `*/*`，等于让用户在一整块存储里翻。
            //  内容那一层的闸不动：`ConfigBundle.parse` 认不出仍然一个字节都不写。
            type = EXPORT_MIME
        }
        try {
            activity.startActivityForResult(intent, REQ_IMPORT_CONFIG)
            XLog.w("导入配置：已弹出文件选择器")
        } catch (e: Throwable) {
            XLog.w("导入配置：拉不起选择器：${e.message}")
            HostSession.page?.toast("导入失败")
        }
    }

    /**
     * 把用户挑中的文件读进来并落地。
     *
     * 注意：**全量校验通过才覆写**：整份文件在 [ConfigBundle.parse] 里过一遍，
     * 认不出 / 有任一段读不懂就**一个字节都不写**，只报一句 [UNKNOWN_CONFIG_FORMAT]。
     * 用户界面上不解释「哪一段坏了」——那是日志的事（见 [XLog]）。
     */
    private fun applyImportedConfig(uri: android.net.Uri) {
        val page = HostSession.page ?: return
        val text = try {
            page.context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Throwable) {
            XLog.w("导入配置：读文件失败：${e.message}")
            null
        }
        val parsed = text?.let { ConfigBundle.parse(it) }
        if (parsed == null) {
            XLog.w("导入配置：校验没过，未改动任何配置（${text?.length ?: 0} 字符）")
            page.toast(UNKNOWN_CONFIG_FORMAT)
            return
        }
        // 两段都过了**校验**才动手，但写入还要分开判：先配置（`write` 会拒只读态并 toast），
        // 配置没落盘就**一个字节都不再动命令库** —— 否则就成了「半份导入」：命令库被整份换成
        // 文件里那份、配置却还是原来的，用户看到的是「导入了」而不是「你的命令全被换掉了」。
        // （只读态下 `write` 必然被拒，这条是唯一会造成数据丢失的路径。）
        if (!page.write(parsed.config, "导入配置")) {
            XLog.w("导入配置：配置未落盘（只读态 / 写失败），命令库保持不动")
            return
        }
        val commandsOk = page.saveCommands(parsed.commands, "导入配置")
        XLog.w("导入配置：配置=true 命令库=$commandsOk")
        // 文案点一句「主开关保持关闭」：导入**不带**主开关状态（见 `ConfigBundle`），不说的话
        //  用户会以为「导入完就该生效」，实际还得自己去首页打开、并在那一步确认风险提示。
        if (commandsOk) page.toast("已导入配置，主开关保持关闭")
    }

    /** 收「保存到 / 打开」的结果 —— 平台 `Activity#onActivityResult`，进程内只装一次。 */
    internal fun installFileResultHook(module: XposedModule) {
        if (exportResultHooked) return
        val origin = try {
            android.app.Activity::class.java.getDeclaredMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                android.content.Intent::class.java,
            )
        } catch (e: Throwable) {
            XLog.w("文件选择：找不到 Activity#onActivityResult：${e.message}")
            null
        } ?: return
        exportResultHooked = module.hookGuarded(origin) { chain ->
            val requestCode = chain.args.getOrNull(0) as? Int
            val resultCode = chain.args.getOrNull(1) as? Int
            val uri = (chain.args.getOrNull(2) as? android.content.Intent)?.data
            when (requestCode) {
                REQ_EXPORT_LOG, REQ_EXPORT_CONFIG -> {
                    writeExportFile(
                        uri = uri,
                        ok = resultCode == android.app.Activity.RESULT_OK,
                        what = if (requestCode == REQ_EXPORT_LOG) "导出日志" else "导出配置",
                    )
                    null // 自己的请求码，不再往下传
                }

                REQ_IMPORT_CONFIG -> {
                    if (resultCode == android.app.Activity.RESULT_OK && uri != null) {
                        applyImportedConfig(uri)
                    } else {
                        XLog.w("导入配置：用户取消")
                    }
                    null
                }

                else -> chain.proceed()
            }
        } != null
        XLog.w("文件选择：结果回调 hook=${exportResultHooked}")
    }

    /** 把 [pendingExport] 写进用户挑的位置。 */
    private fun writeExportFile(uri: android.net.Uri?, ok: Boolean, what: String) {
        val text = pendingExport
        pendingExport = null
        if (!ok || uri == null || text == null) {
            XLog.w("$what：用户取消")
            return
        }
        val written = try {
            val out = HostSession.activity?.contentResolver?.openOutputStream(uri, "wt")
            out?.use { it.write(text.toByteArray()) }
            out != null
        } catch (e: Throwable) {
            XLog.w("$what：写入失败：${e.message}")
            false
        }
        HostSession.page?.toast(if (written) "已导出配置" else "导出配置失败")
        if (written) XLog.w("$what：已写入（${text.length} 字符）")
    }
}
