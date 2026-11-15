package com.lookie.opluskey.ui.hook

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lookie.opluskey.bridge.hookGuarded
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.config.ConfigCodec
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.rom.DeviceSupport
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.appVersionText
import com.lookie.opluskey.ui.state.page.deviceModel
import com.lookie.opluskey.ui.state.page.deviceSupport
import com.lookie.opluskey.ui.wire.registerLocalReceiver
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap

/**
 * 「导出日志」的**收集**那一半 —— 把另外两个进程的缓冲收上来，与本进程的合并成一份。
 *
 * ## 为什么要跨进程收
 *
 *  设置页那份 `XLog` 缓冲只有**本进程**的行（`:ui`）。真正的判据却散在另外两个进程：
 *  `system`（接管点装没装、按键认没认领）与 `host`（收到手势没有、动作执行了没有）。
 *  用户交回来的日志常常「一条按键行都没有」，上一次排查侧键就是这样卡住的。
 *
 * ## 一问多答，收固定时长
 *
 *  发出 [ModuleWire.ACTION_LOG_PULL] 后等 [WAIT_MS]，期间把 [ModuleWire.ACTION_LOG_CHUNK]
 *  攒起来；到点就不等了。**不按「总数收齐」判断结束**：某个进程没起来时永远等不到，
 *  而导出是一次用户动作，宁可少一节也不要卡住界面。
 *
 *  等待放在 IO 线程上（调用方在 `ConfigTransfer.fileScope` 里），广播回调仍在主线程 —— 不冲突。
 */
internal object LogPull {
    /** 等另外两个进程回包的上限（毫秒）。实测两片以内，本地广播几十毫秒就回来了。 */
    private const val WAIT_MS = 700L

    /**
     * 一次收集的结果 —— 不只是正文，还有「这一侧是哪次构建、缓冲里几行」。
     *
     *  后两项进导出文件头（见 `LogHeader`）：三个进程的版本可以不一致（换包没重启时
     *  `system_server` 里还是旧的），而「某进程没起来」与「它在跑但没日志」也得分开 ——
     *  前者没回包，后者回了一片空的。
     */
    class Pulled(
        /** 进程标识 → 正文。 */
        val text: Map<String, String>,
        /** 进程标识 → 它报的 `ModuleInfo.BUILD_ID`（老版本注入侧不带这一位时缺项）。 */
        val build: Map<String, String>,
        /** 进程标识 → 它缓冲里一共几行。 */
        val lines: Map<String, Int>,
    ) {
        /** 回过包的进程（含「在跑但没日志」那种：它也发了一片空的）。 */
        val answered: Set<String> get() = text.keys
    }

    /**
     * 收另外两个进程的日志。
     *
     * @return 三个进程各自的正文 / 构建标识 / 行数；没人应答就是空表
     */
    fun collect(
        context: Context,
        pkg: String,
    ): Pulled {
        // 序号 → 那一片的正文；按进程分组。用并发容器：回调在主线程、读取在这次调用的线程上。
        val chunks = ConcurrentHashMap<String, MutableMap<Int, String>>()
        // 发送方声明的总片数：只用来**核对完整性**，不拿它当结束条件（见类注释）。
        val totals = ConcurrentHashMap<String, Int>()
        //  这两张表同样是并发容器：每片都带（只有第一次写入生效），后写的覆盖同值无妨
        val builds = ConcurrentHashMap<String, String>()
        val counts = ConcurrentHashMap<String, Int>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action != ModuleWire.ACTION_LOG_CHUNK) return
                    val proc = intent.getStringExtra(ModuleWire.EXTRA_LOG_PROC) ?: return
                    val text = intent.getStringExtra(ModuleWire.EXTRA_LOG_TEXT) ?: return
                    val index = intent.getIntExtra(ModuleWire.EXTRA_LOG_INDEX, 0)
                    val total = intent.getIntExtra(ModuleWire.EXTRA_LOG_TOTAL, -1)
                    if (total > 0) totals[proc] = total
                    //  构建标识与行数由**每一片**都带（见 `LogWire.reply`），这里后到覆盖先到 —— 同值
                    intent.getStringExtra(ModuleWire.EXTRA_LOG_BUILD)?.let { builds[proc] = it }
                    val lines = intent.getIntExtra(ModuleWire.EXTRA_LOG_LINES, -1)
                    if (lines >= 0) counts[proc] = lines
                    runCatching {
                        chunks.getOrPut(proc) { ConcurrentHashMap() }[index] = text
                    }.onFailure { XLog.w("导出日志：收到 $proc 的分片但存不下（忽略）：${it.javaClass.simpleName}") }
                }
            }
        // 发送方是宿主主进程（同 uid）与 system_server（uid 1000）—— 后者对 NOT_EXPORTED
        // 接收器照样可达，与 `CollectKeyDialog` 收按键上报同一条口径。
        val registered =
            context.registerLocalReceiver("导出日志（这次只能导本进程的）", receiver, ModuleWire.ACTION_LOG_CHUNK)
        PageWire.send(context, pkg, Intent(ModuleWire.ACTION_LOG_PULL))
        //  收集窗口用 try/finally 兜住：`Thread.sleep` 被中断时抛的是 InterruptedException，
        //  以前那条路会跳过注销 —— 接收器与 `chunks` 就永久留在进程里。
        try {
            Thread.sleep(WAIT_MS)
        } catch (e: InterruptedException) {
            XLog.w("导出日志：收集被中断，提前收尾")
            Thread.currentThread().interrupt()
        } finally {
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }

        val out = LinkedHashMap<String, String>()
        for ((proc, parts) in chunks) {
            val expected = totals[proc] ?: -1
            if (expected > 0) {
                val missing = (0 until expected).filter { it !in parts }
                //  少片必须看得见：日志是本模块唯一自救通道，缺几段又看不出来的日志比没有更误导。
                if (missing.isNotEmpty()) {
                    XLog.w("导出日志：$proc 只收到 ${parts.size}/$expected 片，缺编号 $missing —— 这一节不完整")
                }
            }
            out[proc] = parts.toSortedMap().values.joinToString("\n")
        }
        for (proc in totals.keys) {
            if (chunks[proc] == null) XLog.w("导出日志：$proc 声明了 ${totals[proc]} 片，但一片都没收到")
        }
        XLog.w("导出日志：收到 ${out.size} 个进程的回包（${out.map { "${it.key}=${it.value.lines().size} 行" }}）")
        return Pulled(out, builds, counts)
    }

    /**
     * 「导出日志」那一行副标题要的那一句：缓冲区现在多少行、来自几个进程。
     *
     *  与 [collect] 走同一条路（真把几份收上来再数），所以**慢** —— 最多等 `WAIT_MS`。
     *  调用方必须放在 IO 上，且只在菜单打开那一刻取一次，不能进组合期。
     *
     * @return `null` = 连本进程都是空的（这时候没什么可导的），那一行就不带副标题
     */
    fun summary(context: Context): String? {
        val remote = collect(context, HostApp.PACKAGE).text
        val local = XLog.lines().count { it.isNotBlank() }
        val remoteLines = remote.values.sumOf { text -> text.lines().count { it.isNotBlank() } }
        val lines = local + remoteLines
        if (lines <= 0) return null
        return I18n.format(Strings.MENU_LOG_BUFFER, lines)
    }

    /**
     * 把本进程与收到的几份**按时间合并**成一节，每行前面标上进程名。
     *
     *  为什么不分成几个小节各自列出：排查时看的是**时序**（按下 → 判定 → 转交 → 执行），
     *  分节之后要在几段之间来回跳，最容易漏掉的正是「另一头压根没动」这种。
     *  每行的时间戳是 `yyyy-MM-dd HH:mm:ss.SSS`，首尾相接正好 23 个字符，按它排序即可。
     *  带年份：导出的几份日志可能跨年，只取 `MM-dd` 会把次年 1 月的行排到最前面。
     *
     * @param local 本进程的 [XLog.snapshot]
     * @param remote [collect] 的结果
     */
    fun compose(
        local: String,
        remote: Map<String, String>,
    ): String {
        val all = ArrayList<Pair<String, String>>()
        local.lines().forEach { if (it.isNotBlank()) all.add(LOCAL_PROC to it) }
        remote.forEach { (proc, text) ->
            text.lines().forEach { if (it.isNotBlank()) all.add(proc to it) }
        }
        if (all.isEmpty()) return ""
        // stable sort：同一毫秒的行保持原始先后（各进程内部本来就是有序的）
        return all
            .sortedBy { (_, line) -> timeKey(line) }
            .joinToString("\n") { (proc, line) -> "[$proc] $line" }
    }

    /** 设置页自己那一份的进程标识。 */
    private const val LOCAL_PROC = "ui"

    /** 取排序键：`W 2026-10-18 22:22:41.955 msg` → `2026-10-18 22:22:41.955`。 */
    private fun timeKey(line: String): String = line.substringAfter(' ', "").take(23)
}

/**
 * 把「宿主 Activity 的结果回调」补挂到**宿主自己重写的那一层**。
 *
 *  平台类 `Activity#onActivityResult` 上那份 hook 只在宿主**没有重写**、或重写后调了 `super` 时
 *  才收得到。宿主在自己的 Activity（或中间那个基类）里覆盖了它、又不调 `super` 的话，
 *  回调到那一层就断了 —— 用户挑完文件回来「什么都没发生」，而且**日志里一条失败行都没有**
 *  （这条链路上没有任何地方会抛）。
 *
 *  所以弹选择器之前沿类链找**第一处声明**，找到就单独挂一份同样的处理：
 *  挂上之后返回 `null`、`super` 不会被调用，平台那份 hook 也就不会重复触发。
 *  同一个类只挂一次（[hookedClasses] 记住）。
 */
internal object FileResultHook {
    private const val METHOD = "onActivityResult"

    /**
     * 已经补挂过的宿主 Activity 类（同类只挂一次）。
     *
     *  以前只记**一个**类：宿主若出现第二种设置页 Activity，再 arm 时会把记录覆盖掉，
     *  回头第一个类被当成没挂过、再挂一份 ⇒ 导入/导出回调跑两遍。类对象本身活到进程结束，
     *  这个集合不会长出无用项。
     */
    private val hookedClasses = mutableSetOf<Class<*>>()

    /** 已经**挂成功的那个方法声明在哪一层**（见 [hookedClasses] 旁边那段说明）—— 防重复挂钩。 */
    private val hookedDeclarations = mutableSetOf<Class<*>>()

    /** 装平台 hook 时记下的模块实例（本文件在 `ui/hook`，与那处安装同一个进程）。 */
    private var module: XposedModule? = null

    fun install(module: XposedModule) {
        this.module = module
    }

    /**
     * @param activity 宿主的 Activity（要弹选择器的那个）
     * @param hooker 与平台那份 hook **完全相同**的处理（返回 `null` = 吞掉这次回调）
     */
    fun arm(
        activity: Activity,
        hooker: XposedInterface.Hooker,
    ) {
        val module =
            this.module ?: run {
                //  静默 return 的表现是「点了没反应、日志一行都没有」——这一行是那种现象的唯一线索。
                XLog.w("文件选择：模块实例还没装上，这次 arm 放弃（导入/导出会收不到结果）")
                return
            }
        val cls = activity.javaClass
        if (cls in hookedClasses) return
        var cur: Class<*>? = cls
        //  有没有哪一层**声明了**这个方法 —— 与「声明了但挂钩失败」是两件事，收尾要分开说：
        //  前者说明宿主没覆盖（以后也不用再试），后者说明这次没接上（下次还得重试）。
        var anyDeclared = false
        while (cur != null && cur != Activity::class.java) {
            val declared = declaredResult(cur)
            if (declared != null) {
                anyDeclared = true
                //  同一个**声明类**上这个方法已经挂过 ⇒ 这一层什么都不用做，只把叶子类记账收工。
                if (declared.declaringClass in hookedDeclarations) {
                    hookedClasses += cls
                    XLog.w("文件选择：${declared.declaringClass.simpleName}#$METHOD 已经挂过，这一层跳过")
                    return
                }
                //  挂钩**成功**才记账。没记上就把 `hookedClass` 提前写上、还打一句「已挂钩」，
                //  后面每次进来都会被开头那句 `hookedClass === cls` 挡回去 —— 结果是这条路
                //  **一次都没接上、日志还说自己接上了**（导入导出整条静默归零）。
                if (module.hookGuarded(declared, hooker) != null) {
                    hookedClasses += cls
                    //  **声明类也记一笔**：同一个方法可能被不同的 Activity 子类各 arm 一次
                    //  （分身、不同入口各是一个叶子类），记了它就不会把同一个方法挂第二遍
                    //  （挂两遍 = 回调跑两次，导入导出各收一次）。
                    hookedDeclarations += declared.declaringClass
                    XLog.w("文件选择：宿主的 ${cur.simpleName}#$METHOD 已单独挂钩（它不往上调也能收到）")
                    return
                }
                XLog.w("文件选择：宿主 ${cur.simpleName}#$METHOD 挂钩失败，继续往上找")
            }
            cur = cur.superclass
        }
        if (anyDeclared) {
            //  **这一趟不记账**：记了等于「以后不再试」，而这里失败的原因是挂钩抛了异常
            //  （框架侧一时的问题），下一次 arm 值得再试一遍。日志也要说清是哪一种失败。
            XLog.w("文件选择：宿主覆盖了 $METHOD，但每一层都挂钩失败（本次不记账，下次 arm 重试）")
            return
        }
        //  宿主没覆盖 ⇒ 以后也不用再扫这一层链，照样记账（否则每次 arm 都要把父类链走一遍）。
        hookedClasses += cls
        //  **判据级**：导入导出「点了没反应」时，先要看的就是走没走到平台那个 hook ⇒ `w`。
        XLog.w("文件选择：宿主没有覆盖 $METHOD，走平台那个 hook")
    }

    /** 这一层有没有声明结果回调；没有就是没有（继续往父类看）。 */
    private fun declaredResult(cls: Class<*>): java.lang.reflect.Method? =
        try {
            cls.getDeclaredMethod(
                METHOD,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Intent::class.java,
            )
        } catch (e: Throwable) {
            //  「没声明」与「反射失败」在这里同形，所以留一行 `d`：真出问题（签名变了）时才查得到。
            XLog.d("文件选择：${cls.simpleName} 上没有 $METHOD（${e.javaClass.simpleName}）")
            null
        }
}

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
