package com.lookie.opluskey.ui.hook

import com.lookie.opluskey.bridge.hookGuarded
import com.lookie.opluskey.core.config.ConfigBundle
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.ui.appVersionText
import com.lookie.opluskey.ui.model.UNKNOWN_CONFIG_FORMAT
import com.lookie.opluskey.ui.saveCommands
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.toast
import com.lookie.opluskey.ui.write
import io.github.libxposed.api.XposedModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 导出文件的**文件名**时间戳（精确到分，同分钟再导会由系统给「保留两者」）。 */
    private val FILE_STAMP: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmm")

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

    /**
     * 导入文件的体积上限（字节）—— 配置与命令库本就是几 KB 量级，一份远超它的文件不可能是本模块
     * 导出的，读进来只会让 `:ui` 进程 OOM。
     *
     * 只在 provider 报得出大小时生效（见 [applyImportedConfig]）：拿不到大小就照读，
     * 不为判上限先全量读一遍。
     */
    private const val MAX_IMPORT_BYTES = 1024 * 1024

    /** 平台类 `Activity#onActivityResult` 那个 hook 装没装（进程内只装一次）。 */
    private var exportResultHooked = false

    /**
     * 用户挑的那个位置上的整文件读写走这个作用域。
     *
     *  为什么必须离开调用线程：这一族是从宿主的 `Activity#onActivityResult` **回调里**发起的，
     *  也就是宿主主线程；而 SAF 给的 uri 可能落在云盘 / 别的进程的 provider 上，
     *  `readBytes` / `write` 在那里是**没有上界**的阻塞调用 —— 文件多大、provider 多慢都由用户挑的那个位置决定。
     *
     *  任务都是一次性的，用完不需要回收：作用域随本进程（`:ui`）结束。
     */
    private val fileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 回主线程报结果用 —— `Toast.makeText` 要求当前线程有 Looper，
     * 而那两步落盘（[write] / [saveCommands]）也要动页面状态，都在主线程上做。
     */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * 导出的是哪一样 —— 日志与配置**只在这一处分开**（请求码、日志前缀、成功与失败那两条提示）。
     *
     *  原来这三样散在调用点与 [writeExportFile] 里，而提示那两条**从不看导的是什么**：导出日志
     *  也会弹「已导出配置」，`Strings.TOAST_LOG_EXPORTED` 定义了却一直没人用。
     */
    private enum class ExportKind(
        /** 日志里那一句（沿用原来的说法）。 */
        val what: String,
        val okKey: String,
        val okDefault: String,
        val failKey: String,
        val failDefault: String,
    ) {
        LOG(
            what = "导出日志",
            okKey = Strings.TOAST_LOG_EXPORTED,
            okDefault = "已导出日志",
            failKey = Strings.TOAST_LOG_EXPORT_FAILED,
            failDefault = "导出日志失败",
        ),
        CONFIG(
            what = "导出配置",
            okKey = Strings.TOAST_CONFIG_EXPORTED,
            okDefault = "已导出配置",
            failKey = Strings.TOAST_CONFIG_EXPORT_FAILED,
            failDefault = "导出配置失败",
        ),
    }

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
            HostSession.page?.toast(I18n.get(Strings.TOAST_LOG_EMPTY, "暂无日志"))
            return
        }
        val now = java.time.LocalDateTime.now()
        // 文件头带版本 + 当前时间：日志发出来时不用再问「哪个版本、什么时候的」
        val text =
            buildString {
                append("OPlusKey ").append(HostSession.page?.appVersionText().orEmpty())
                append(" | ").append(now.format(HEADER_STAMP))
                append("\n\n").append(body).append('\n')
            }
        //  后缀要跟下面那个 `type` 对得上：「保存到」按 MIME 决定扩展名，`text/plain` 对应 `.txt`
        // —— 写 `.log` 也会被对话框换成 `.txt`（同 [EXPORT_MIME] 注释里那条实测）。
        val name = "OPlusKey-" + now.format(FILE_STAMP) + ".txt"
        pendingExport = text
        val intent =
            android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
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
            HostSession.page?.toast(
                I18n.get(ExportKind.LOG.failKey, ExportKind.LOG.failDefault),
            )
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
        val text = ConfigBundle.export(page.configState.config.value, page.configState.commands.value)
        if (text.isEmpty()) {
            page.toast(I18n.get(Strings.TOAST_CONFIG_EXPORT_FAILED, "导出配置失败"))
            return
        }
        val now = java.time.LocalDateTime.now()
        pendingExport = text
        val intent =
            android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
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
            page.toast(I18n.get(ExportKind.CONFIG.failKey, ExportKind.CONFIG.failDefault))
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
        val intent =
            android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
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
            HostSession.page?.toast(I18n.get(Strings.TOAST_CONFIG_IMPORT_FAILED, "导入失败"))
        }
    }

    /**
     * 把用户挑中的文件读进来并落地。
     *
     * 注意：**全量校验通过才覆写**：整份文件在 [ConfigBundle] 里过一遍，
     * 认不出 / 有任一段读不懂就**一个字节都不写**，只报一句 [UNKNOWN_CONFIG_FORMAT]。
     * 用户界面上不解释「哪一段坏了」——那是日志的事（见 [XLog]）。
     *
     * 读 + 解析在 [fileScope] 上（那一整份文件多大由用户挑，见 [fileScope]），
     * 落盘那两步回到主线程做（与别处每一次写盘同一条路：要弹提示、要动页面状态）。
     */
    private fun applyImportedConfig(uri: android.net.Uri) {
        val page = HostSession.page ?: return
        // resolver 在主线程上取好再进协程（[HostSession] 那两个引用本就只在主线程上换）
        val resolver = page.context.contentResolver
        fileScope.launch {
            // 体积上限：读之前先按 provider 报的字节数挡一道（拿不到大小就照读，见 [blockedByImportSize]）
            if (blockedByImportSize(resolver, uri)) return@launch
            val text =
                try {
                    resolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                } catch (e: Throwable) {
                    XLog.w("导入配置：读文件失败：${e.message}")
                    null
                }
            val parsed = text?.let { ConfigBundle.parse(it) }
            mainHandler.post {
                if (parsed == null) {
                    XLog.w("导入配置：校验没过，未改动任何配置（${text?.length ?: 0} 字符）")
                    page.toast(UNKNOWN_CONFIG_FORMAT)
                    return@post
                }
                // 两段都过了**校验**才动手，但写入还要分开判：先配置（`write` 会拒只读态并 toast），
                // 配置没落盘就**一个字节都不再动命令库** —— 否则就成了「半份导入」：命令库被整份换成
                // 文件里那份、配置却还是原来的，用户看到的是「导入了」而不是「你的命令全被换掉了」。
                // （只读态下 `write` 必然被拒，这条是唯一会造成数据丢失的路径。）
                //
                //  反过来那一半也要防：命令库**写失败**时主配置已经生效，那份新配置里的手势会绑到
                //  库里不存在的命令上（两个只读位是分开的，「配置能写、命令库只读」这种组合可达）。
                //  所以这里留一份旧配置，命令库没落盘就把它写回去 —— 两段一起成、或一起不动。
                val previousConfig = page.configState.config.value
                if (page.write(parsed.config, "导入配置") == CommandsSave.Failed) {
                    XLog.w("导入配置：配置未落盘（只读态 / 写失败），命令库保持不动")
                    return@post
                }
                when (page.saveCommands(parsed.commands, "导入配置")) {
                    // 文案点一句「主开关保持关闭」：导入**不带**主开关状态（见 `ConfigBundle`），
                    //  不说的话用户会以为「导入完就该生效」，实际还得自己去首页打开、并在那一步认风险。
                    CommandsSave.Ok -> {
                        page.toast(I18n.get(Strings.TOAST_CONFIG_IMPORTED, "已导入配置，主开关保持关闭"))
                    }

                    // 两份都已落盘，只是没通知到注入侧：saveCommands 自己已经报过那一句，这里不叠。
                    CommandsSave.BroadcastFailed -> {
                        // 什么都不用做 —— 见上面那句
                    }

                    CommandsSave.Failed -> {
                        XLog.w("导入配置：命令库未落盘 ⇒ 把主配置写回导入前那份（不留半份状态）")
                        if (page.write(previousConfig, "导入配置失败：回滚主配置") == CommandsSave.Failed) {
                            XLog.w("导入配置：回滚也没写成 —— 主配置是新那份、命令库是旧的，需退出重进再看")
                        }
                    }
                }
            }
        }
    }

    /**
     * 导入文件的体积闸：问 provider 要「显示名 + 字节数」，把来源报进日志，再按 [MAX_IMPORT_BYTES]
     * 判上限。
     *
     *  名字进日志是因为导入这条链路本身不看文件名，排查时要能对上「用户到底挑了哪个文件」。
     *  字节数只在 provider 报得出时才有：拿不到就跳过上限判断、照读 —— **不为判上限先全量读一遍**，
     *  那是为了省一次读反而多做一次读，且 provider 两次打开还可能读到不同内容。
     *
     * @return true = 文件太大、已弹过提示，调用方不要再读
     */
    private fun blockedByImportSize(
        resolver: android.content.ContentResolver,
        uri: android.net.Uri,
    ): Boolean {
        val (name, size) = readOpenableInfo(resolver, uri)
        val sizeText = size?.let { "$it 字节" } ?: "大小未知"
        XLog.w("导入配置：来源「${name ?: "未知"}」，$sizeText")
        val bytes = size ?: return false
        if (bytes <= MAX_IMPORT_BYTES) return false
        XLog.w("导入配置：这个文件太大（$bytes 字节 > $MAX_IMPORT_BYTES），不像是本模块导出的配置")
        mainHandler.post { HostSession.page?.toast(UNKNOWN_CONFIG_FORMAT) }
        return true
    }

    /**
     * 问 provider 要「显示名 + 字节数」——取不到的那一项给 `null`（调用方据此跳过上限判断）。
     *
     *  拿不到大小**不**为了判上限先全量读一遍：那是为了省一次读反而多做一次读，且 provider 两次打开
     *  还可能读到不同内容。
     */
    private fun readOpenableInfo(
        resolver: android.content.ContentResolver,
        uri: android.net.Uri,
    ): Pair<String?, Long?> {
        val projection =
            arrayOf(
                android.provider.OpenableColumns.DISPLAY_NAME,
                android.provider.OpenableColumns.SIZE,
            )
        val cursor =
            try {
                resolver.query(uri, projection, null, null, null)
            } catch (e: Throwable) {
                // 问不出来就跳过上限判断（照读），不因为「拿不到大小」把导入整个挡掉
                XLog.w("导入配置：查文件信息失败（照读，不判上限）：${e.message}")
                return null to null
            } ?: return null to null
        return cursor.use { c ->
            if (c.moveToFirst()) {
                c.stringOrNull(android.provider.OpenableColumns.DISPLAY_NAME) to
                    c.longOrNull(android.provider.OpenableColumns.SIZE)
            } else {
                null to null
            }
        }
    }

    /** 收「保存到 / 打开」的结果 —— 平台 `Activity#onActivityResult`，进程内只装一次。 */
    internal fun installFileResultHook(module: XposedModule) {
        if (exportResultHooked) return
        val origin =
            Reflect.method(
                android.app.Activity::class.java,
                "onActivityResult",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                android.content.Intent::class.java,
            ) ?: run {
                XLog.w("文件选择：找不到 Activity#onActivityResult")
                null
            } ?: return
        exportResultHooked = module.hookGuarded(origin) { chain ->
            // 这个 hook 挂在**平台类** `Activity#onActivityResult` 上（宿主没自己重写时），
            //  进程里每个 Activity 的回调都会经过它；而返回 null = 把这次回调**吞掉**。
            //  只认宿主的那个 Activity（口径同 BackInterception / SettingsPageHooks）：别的 Activity
            //  撞上我们的请求码（0x4F4C / 0x4F43 / 0x4943）时原样放行 —— 否则它的回调被静默吃掉，
            //  REQ_IMPORT_CONFIG 还会拿它的 uri 去导入。
            //  取舍：SAF 打开期间宿主 Activity 若被重建、而 HostSession.activity 还没指向新实例，
            //  这一次会放行给宿主 —— 宁可不导入，也不误吞宿主回调。
            if (chain.thisObject !== HostSession.activity) return@hookGuarded chain.proceed()
            val requestCode = chain.args.getOrNull(0) as? Int
            val resultCode = chain.args.getOrNull(1) as? Int
            val uri = (chain.args.getOrNull(2) as? android.content.Intent)?.data
            when (requestCode) {
                REQ_EXPORT_LOG, REQ_EXPORT_CONFIG -> {
                    writeExportFile(
                        uri = uri,
                        ok = resultCode == android.app.Activity.RESULT_OK,
                        kind = if (requestCode == REQ_EXPORT_LOG) ExportKind.LOG else ExportKind.CONFIG,
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

                else -> {
                    chain.proceed()
                }
            }
        } != null
        XLog.w("文件选择：结果回调 hook=$exportResultHooked")
    }

    /** 把 [pendingExport] 写进用户挑的位置 —— 整份文本的写盘在 [fileScope] 上，结果回主线程报。 */
    private fun writeExportFile(
        uri: android.net.Uri?,
        ok: Boolean,
        kind: ExportKind,
    ) {
        val text = pendingExport
        pendingExport = null
        if (!ok || uri == null || text == null) {
            XLog.w("${kind.what}：用户取消")
            return
        }
        // resolver 在主线程上取好再进协程（[HostSession] 那两个引用本就只在主线程上换）
        val resolver = HostSession.activity?.contentResolver
        fileScope.launch {
            val written =
                try {
                    val out = resolver?.openOutputStream(uri, "wt")
                    out?.use { it.write(text.toByteArray()) }
                    out != null
                } catch (e: Throwable) {
                    XLog.w("${kind.what}：写入失败：${e.message}")
                    false
                }
            if (written) XLog.w("${kind.what}：已写入（${text.length} 字符）")
            mainHandler.post {
                HostSession.page?.toast(
                    if (written) {
                        I18n.get(kind.okKey, kind.okDefault)
                    } else {
                        I18n.get(kind.failKey, kind.failDefault)
                    },
                )
            }
        }
    }
}

/**
 * 从 SAF 的 Cursor 里取一位字符串 —— **列不存在**或该列为 `null` 都算「没有」。
 *
 *  一律先拿列索引再取值（不硬编码索引）；拿不到就是 `null`，由调用方决定怎么兜底。
 */
private fun android.database.Cursor.stringOrNull(column: String): String? {
    val at = getColumnIndex(column)
    return if (at >= 0 && !isNull(at)) getString(at) else null
}

/** 同上，取一位长整数（列不存在 / 该列是 `null` / 类型不对都返回 `null`）。 */
private fun android.database.Cursor.longOrNull(column: String): Long? {
    val at = getColumnIndex(column)
    return if (at >= 0 && !isNull(at)) getLong(at) else null
}
