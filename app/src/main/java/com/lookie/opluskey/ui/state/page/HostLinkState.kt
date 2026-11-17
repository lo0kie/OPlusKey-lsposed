package com.lookie.opluskey.ui.state.page

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.OwnSettings
import com.lookie.opluskey.core.present.comboKeyNames
import com.lookie.opluskey.core.present.comboMemberSeparator
import com.lookie.opluskey.core.present.keyName
import com.lookie.opluskey.ui.feedback.HintText
import com.lookie.opluskey.ui.model.text.LinkNotice
import com.lookie.opluskey.ui.state.host.InjectStatus
import kotlin.math.abs

/**
 * 「我们自己刚写下去的那一份配置还在路上」的宽限 —— 见 [HostLinkState.configPending]。
 *
 *  取 2 秒：真机实测这一趟（落盘 → 广播 → 注入侧回包）在 10~20ms 量级，2 秒是给忙碌 / 刚解冻
 *  留的余量。它只影响「乐观」这一小段 —— 回包一到就提前结束，等不到也会自己过期。
 */
internal const val CONFIG_IN_FLIGHT_GRACE_MS = 2_000L

/**
 * 「两个进程启动时刻差多少以内算同一个进程」的容差（[HostLinkState.needsLinkCheck]）。
 *
 *  取 1 秒：`ProcClock.startWall()` 已取整到秒，残留的跨秒错位最多 1 秒；而真正的进程重启
 *  （被杀后重起 / 手机重启）与上一次至少差开几秒 —— 这一档既能吃掉抖动，又不会把重启盖掉。
 */
internal const val PROC_SAME_TOLERANCE_MS = 1_000L

/**
 * 「上一趟自检问满所有轮都没人答」之后，隔多久才允许再问一次（[HostLinkState.needsLinkCheck]）。
 *
 *  取 30 秒：注入侧自愈通常发生在「重启后那十几秒」（`SystemContext` 借 Context、uid 晚到），
 *  而一趟自检本身就要 [QUERY_ROUNDS] 轮 × 一档超时。太短会在宿主被冻结时把广播刷爆，
 *  太长则用户盯着整片灰的首页等不到自愈。30 秒是一次「起身去倒杯水回来」的量级。
 */
internal const val LINK_SILENT_RETRY_MS = 30_000L

/**
 * 「链路 / 报活」那一域的状态 —— 首页那条提示与「更多选项 → 运行状态」共用的那几项结论。
 *
 *  两处判据都在这几个字段上：宿主侧有没有在跑（[hostServiceUp] + [hostAliveToken]）、
 *  注入侧回没回包（[injectStatus] + [injectAliveToken]）、以及「设置生效」的配置侧那半
 *  （见 `ui/wire/LinkCheck.kt` 的 `linkReady`）。
 *
 *  为什么**代次**（`*AliveToken`）单独立字段：那几个「在不在」的布尔初值必须是「正常」
 *  （否则每次进页面都会闪一下「未生效」），于是它们不能当判据用；代次初值是 0，
 *  变了才有回音，这才是可判的。
 */
internal object HostLinkState {
    /**
     * 「一趟自检的结论」—— 三样必须一起对：跑完没有、跑的时候宿主是哪个进程、问空了之后什么时候
     * 才允许再问（见 [needsLinkCheck]）。
     *
     *  收成一个不可变值，是为了让「问通了」与「问空了」各自只有**一次**赋值：
     *  原先这三个字段散在 `injectChecked` / `checkedHostStart` / `silentRetryAt` 三处，
     *  由调用方按顺序写两三次 —— 少写一处就得到「跑完了、但宿主进程记的是 0」这种半截账。
     */
    private class CheckLedger(
        val done: Boolean,
        val hostStart: Long,
        val retryAt: Long,
    )

    /**
     * 「上一次注入侧回话」那一笔账 —— 两个值一起才有意义（见 [noteInjectAlive]）。
     */
    private class AliveMark(
        /** `null` = 不知情（盘上那份没读出来 / 没有这个键）—— 见 [injectEverAlive]。 */
        val ever: Boolean?,
        val boot: Long,
    )

    /**
     * 「我们自己刚写下去的配置还在路上」—— 指纹与时刻一起才有意义（见 [configPending]）。
     */
    private class PendingConfig(
        val tag: String,
        val since: Long,
    )

    /** 自检结论那一笔（见 [CheckLedger]）。 */
    private var ledger by mutableStateOf(CheckLedger(done = false, hostStart = 0L, retryAt = 0L))

    /** 「上一次注入侧回话」那一笔（见 [AliveMark]）。 */
    private var aliveMark by mutableStateOf(AliveMark(ever = null, boot = 0L))

    /** 「在途的那一份配置」那一笔（见 [PendingConfig]），`null` = 没有在途的写入。 */
    private var pendingConfig by mutableStateOf<PendingConfig?>(null)

    /**
     * 宿主侧的模块有没有在跑 —— 也就是**执行侧有没有在工作**。
     *
     *  初值取 `true`：首帧还没查，先按「正常」画。首页那个 `LaunchedEffect` 组合完立刻查一次，
     *  查完再按真实结果画。
     */
    var hostServiceUp by mutableStateOf(true)

    /**
     * **调试**：把整条链路当成「所有段都坏了」—— 让「运行状态」那几条一起红，验排版与图标。
     *
     *  只有调试通道会写它（`--es tips broken`，传空串关掉）。读它在 `SettingsPage.linkFacts`
     *  那一处：判据与渲染全都不动，只是**换一份事实**——所以看到的就是真的「全坏」时的样子
     *  （包括整页只读、控件灰掉）。
     */
    var debugAllBroken by mutableStateOf(false)

    /** 宿主侧报活的**代次** —— 每收到一条报活就自增；进页时记下它、等一档再看变没变。 */
    var hostAliveToken by mutableIntStateOf(0)

    /**
     * 注入侧（system_server）回包的内容 —— `null` = 问过但**没回音**（注入侧没装上，
     * 或它那支 AMS 广播入口没挂上）。初值就是 `null`：没问到之前一律按「未就绪」算。
     */
    var injectStatus by mutableStateOf<InjectStatus?>(null)

    /** 注入侧回包的**代次** —— 与 [hostAliveToken] 同一个道理。 */
    var injectAliveToken by mutableIntStateOf(0)

    /**
     * 进首页那一趟**注入侧自检跑完了没有**（问满所有轮，或提前收到回包）。
     *
     *  为什么要单独一位：[injectStatus] 的初值就是 `null`（「没问到之前一律按未就绪算」），
     *  而「未就绪」与「问过三轮都没回音」在判据上是同一份 `null`。少了这一位，重启后那几秒
     *  （system_server 刚起、AMS 广播在排队）会被判成「模块没生效，去勾作用域」—— 一句假故障，
     *  等回音一到又自己消失。有了它，判据就能分清「还没问完」与「问了没人答」。
     *
     *  **只置 true、不再回 false**：自检每回到首页都会重跑，但「曾经问通过」这件事不会变；
     *  回 false 会让重新进首页时那几秒又把结论收起来。
     */
    val injectChecked: Boolean get() = ledger.done

    /**
     * 上一次自检**跑完**时，宿主进程的启动时刻（[hostProcStart]）。
     *
     *  自检不便宜：一趟最多 [QUERY_ROUNDS] 轮广播、每轮等一档超时。原先每回首页都重跑一遍，
     *  从子页退回来、从设置重进模块都要再问 —— 而这段时间里宿主进程根本没换过，问出来的
     *  必然是同一份结论。所以判据改成「**这一趟宿主进程问过就不再问**」：
     *  这一位与 [hostProcStart] 相等就说明问过（两者都是 0 也算 —— 那是「问过、但宿主没答」）。
     *
     *  宿主换了进程（重启 / 被杀后重起）⇒ [hostProcStart] 变 ⇒ 自动重问一次。
     */
    val checkedHostStart: Long get() = ledger.hostStart

    /**
     * 上一趟**问空了**之后，最早什么时候允许再问一次（[SystemClock.elapsedRealtime] 口径，
     * `0` = 不限）。判据见 [needsLinkCheck]，值在 [CheckLedger.retryAt] 里。
     *
     *  为什么需要它：问满所有轮都没人答时，`injectChecked` 会置 true（那是**结论**：
     *  「问了没人答」），于是 `needsLinkCheck` 只剩「宿主换没换进程」一条 —— 而注入侧
     *  自己恢复（借到 Context、uid 晚到）并不换进程 ⇒ 首页从此再也不会重问，整片灰着，
     *  只能杀掉 `:ui` 进程或重启手机。这一位把「问空」与「问通」分开：问空的那一趟
     *  过 [LINK_SILENT_RETRY_MS] 之后允许再问一次，问通的那一趟清零、照旧不问。
     *
     *  **并进 [CheckLedger] 不增加重组**：写它的两个时机（[markCheckPassed] /
     *  [markCheckSilent]）本来就要改 `done`，而那一位是 Compose 状态；`needsLinkCheck()`
     *  只在 `snapshotFlow { screen }` 的协程里被调用、不在组合期读，所以这一位变也不会
     *  「到期那一帧自己重问」。
     */
    fun markCheckPassed(hostStart: Long) {
        ledger = CheckLedger(done = true, hostStart = hostStart, retryAt = 0L)
    }

    /**
     * 这一趟自检**问空了**（问满所有轮都没人答）—— 结论先记下，过 [LINK_SILENT_RETRY_MS]
     * 之后回首页允许再问一次（判据见 [needsLinkCheck]）。
     *
     * @param retryAt 最早什么时候允许再问（`elapsedRealtime` 口径）
     */
    fun markCheckSilent(
        hostStart: Long,
        retryAt: Long,
    ) {
        ledger = CheckLedger(done = true, hostStart = hostStart, retryAt = retryAt)
    }

    /**
     * 「已经说过的那条提醒」（[noticeReason] 的返回值）—— 进程级，跨页面组合的生死。
     *
     *  这条提醒（系统侧 / 宿主侧还跑着旧包）用户口径是「提示一下就好」，不是常驻。
     *  原先「说过了」这一位放在首页那个 `remember` 里，从子页（如「条件执行」）回来时组合被重建，
     *  于是每回一次首页就再弹一次。提醒自己消失时（[noticeReason] 变回 `null`）清零，
     *  下次再出现照说一遍。
     *
     *  **比的是身份不是文案**（[LinkNotice]）：比文案的话，切一次语言就换了一串字，
     *  这一位判不出「已经说过」，同一条提醒会再弹一遍。
     */
    var lastNoticeShown: LinkNotice? = null

    /**
     * 这一趟还要不要再跑自检 —— 判据见 [checkedHostStart]。
     *
     *  `injectChecked` 也一起看：没跑完的那一趟不算数（否则「问了一半就切页」会被当成问过了）。
     *
     *  比的是「两次记下的进程启动时刻**差在 [PROC_SAME_TOLERANCE_MS] 以内**就当作同一个进程」，
     *  不是精确相等。原先写的是 `checkedHostStart == hostProcStart`，于是**偶尔回一次首页就会
     *  重跑一趟自检**（用户口径「回到首页还是会偶尔触发模块自检」）：那个时刻是
     *  `ProcClock.startWall()` 算出来的，`currentTimeMillis` 与 `elapsedRealtime` 两个时钟源
     *  更新粒度不同，同一进程里连续两次调用就能差几毫秒；宿主每回一条报活就重算一次，
     *  一条报活足以把「问过」判成「没问过」。`startWall` 那边已取整到秒，这里再加一档容差兜底。
     */
    fun needsLinkCheck(): Boolean {
        //  一次读进局部变量：三样来自**同一份**结论（分三次读的话，中途被 [markCheckPassed]
        //  换掉就会得到「done 是新的、hostStart 是旧的」这种半截账）。
        val current = ledger
        if (!current.done) return true
        if (!sameProcess(current.hostStart, hostProcStart)) return true
        // 上一趟问空了：退避窗口过去之后允许再问一次（判据见 [markCheckSilent]）。
        return current.retryAt != 0L && SystemClock.elapsedRealtime() >= current.retryAt
    }

    /**
     * 两个「进程启动时刻」是不是同一个进程 —— 允许 [PROC_SAME_TOLERANCE_MS] 的误差。
     *
     *  真正的进程重启（被杀后重起 / 手机重启）与上一次至少差开几秒，容差不会把它盖住；
     *  而 `0` 与 `0` 这一档仍然算同一个（「问过、但宿主没答」）。
     */
    private fun sameProcess(
        a: Long,
        b: Long,
    ): Boolean = a == b || abs(a - b) <= PROC_SAME_TOLERANCE_MS

    /** 宿主报活带回来的「它读到的主开关真值」—— `false` ⇒ 它读到的是默认值（主开关关着 / 还没解锁）。 */
    var hostEnabled by mutableStateOf(false)

    /**
     * 宿主报活带回来的「那份配置文件它读得懂」—— `false` ⇒ 它读到的是 `Config.DEFAULT`。
     *
     *  与 [hostEnabled] **不是一件事**，别合并：读不懂时 `enabled` 也是 false，
     *  两者在 [hostEnabled] 这一位上完全同形，而该做的事完全不同（拨开关 vs 重启服务）。
     *
     *  缺省 `true`：报活里没带这一位（更早的宿主）时不该凭空报「配置读取失败」。
     */
    var hostConfigReadable by mutableStateOf(true)

    /** 宿主报活带回来的「最后一次执行动作」（`elapsedRealtime()`，0 = 从未）。 */
    var hostLastActionAt by mutableLongStateOf(0L)

    /** 宿主报活带回来的「宿主进程启动时刻」（wall clock，0 = 对面没带这一项）。 */
    var hostProcStart by mutableLongStateOf(0L)

    /**
     * 盘上那份模块包的更新时间（`PackageInfo.lastUpdateTime`，0 = 读不到）。
     *
     *  与 [hostProcStart] / [InjectStatus.procStart] 一比，就能判出「哪一侧还跑着旧版」——
     *  进程起得比包早，它加载的就是上一版代码。判据在 `LinkFacts`，这里只存事实。
     */
    var installedAt by mutableLongStateOf(0L)

    /**
     * 系统那一侧**曾经**回过话没有 —— 从 `opluskey_ui.conf` 读进来（见 [UiState]）。
     *
     *  它是「从没用过」与「用过、这次没起来」之间唯一的区别；没有它，界面只能把这两种
     *  现象糊成同一句话。
     */
    val injectEverAlive: Boolean? get() = aliveMark.ever

    /** 上一次回话时的那轮开机（秒级 wall clock，0 = 从没回话）—— 用来判「那次是不是此刻这轮」。 */
    val injectAliveBoot: Long get() = aliveMark.boot

    /**
     * 建页那次把盘上读到的两值灌进来（见 `ui/chrome/PageLoad.kt` 的 `applyPageLoad`）。
     *
     *  与 [noteInjectAlive] 分开：那边是「刚刚收到回包」这件**事**（要顺带落盘），
     *  这边只是「把已经记着的东西读进内存」。
     */
    fun seedAlive(
        ever: Boolean?,
        boot: Long,
    ) {
        aliveMark = AliveMark(ever = ever, boot = boot)
    }

    /** 本轮开机的指纹（`ProcClock.bootWall()`）—— 建页那一刻取一次即可。 */
    var bootWall by mutableLongStateOf(0L)

    /**
     * 记下「注入侧刚回过话」—— 只在值真的要变时才落盘（注入侧每次自检都回包，每次都写是白写）。
     *
     *  注意：这一位**只增不减**。它答的是「这台机器上跑起来过没有」，不是「现在活着」——
     *  「现在活着」由 `injectStatus` 每轮自己回答。
     *
     * @param bootNow 本轮开机指纹（调用方传，避免这里自己再算一次）
     * @param write 落盘动作（带上 `Context` 的那一半留在 `ui` 层，`ui.state` 不碰文件）
     */
    fun noteInjectAlive(
        bootNow: Long,
        write: (Long) -> Boolean,
    ) {
        if (aliveMark.ever == true && aliveMark.boot == bootNow) return
        aliveMark = AliveMark(ever = true, boot = bootNow)
        write(bootNow)
    }

    /**
     * 我们自己**刚写下去**的那一份配置的指纹（`ConfigBroadcast.tag`），`null` = 没有在途的写入。
     *
     *  从「写盘成功」到「注入侧回包」之间，本地指纹已经换了、注入侧手上还是旧的 ——
     *  这一段两边**必然**对不上，见 [configPending]。
     */
    val pendingConfigTag: String? get() = pendingConfig?.tag

    /** 上面那一份写下去的时刻（`SystemClock.uptimeMillis()`），0 = 没有在途的写入。 */
    val pendingConfigSince: Long get() = pendingConfig?.since ?: 0L

    /**
     * 宽限到期那一刻的收尾任务 —— 见 [markConfigPending]。
     *
     *  主线程的 `Handler`：这一位由页面点击写（本来就在主线程），到期后要改的也是
     *  Compose 状态（必须回主线程）。
     */
    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var pendingExpiry: Runnable? = null

    /** 记下「[tag] 这一份刚写下去、注入侧还没回包」。 */
    fun markConfigPending(tag: String) {
        pendingConfig = PendingConfig(tag = tag, since = SystemClock.uptimeMillis())
        schedulePendingExpiry()
    }

    /** 结束「在途」—— 回包对上了，或宽限到期。 */
    fun clearConfigPending() {
        pendingConfig = null
        pendingExpiry?.let { mainHandler.removeCallbacks(it) }
        pendingExpiry = null
    }

    /**
     * 排一次「宽限到期」的收尾。
     *
     * ## 为什么必须有它
     *
     *  [configPending] 只**读**时间窗，不写状态：窗口过期这件事本身不会让任何 Compose 状态变，
     *  于是没有人重组 —— 页面就一直停在「已生效」那一档（乐观档），直到一次无关的重组
     *  （切页、切语言）才被发现。真机表现是「广播丢了、注入侧回的指纹又没变，底部一直说已生效」。
     *
     *  排一次定时任务，到期时把「在途」清掉 ⇒ 状态写 ⇒ 重组 ⇒ 页面按真实指纹重判。
     *  只清**同一趟**：期间可能又写了新的一份（它自己会排新的到期），所以到期时再核一遍时间窗。
     */
    private fun schedulePendingExpiry() {
        pendingExpiry?.let { mainHandler.removeCallbacks(it) }
        val task =
            Runnable {
                pendingExpiry = null
                //  期间可能又写了新的一份（它自己会排新的到期），所以到期时再核一遍时间窗
                val current = pendingConfig ?: return@Runnable
                if (SystemClock.uptimeMillis() - current.since >= CONFIG_IN_FLIGHT_GRACE_MS) {
                    clearConfigPending()
                }
            }
        pendingExpiry = task
        mainHandler.postDelayed(task, CONFIG_IN_FLIGHT_GRACE_MS)
    }

    /**
     * [tag] 这一份是不是**我们自己刚写下去、还在路上**的那一份（且在 [CONFIG_IN_FLIGHT_GRACE_MS] 内）。
     *
     *  为什么需要这一位：`configSideOk` 比的是「注入侧手上那份的指纹」与本地指纹，而写完到回包
     *  之间它们**必然**不相等。不把这一段认成「在途」的话，每拨一个开关都会有一两帧被判成
     *  「设置没送到」—— 整页控件跟着打灰、底部提示被换掉再换回来，看到的就是全页文字闪一下。
     */
    fun configPending(tag: String): Boolean {
        val current = pendingConfig ?: return false
        return current.tag == tag &&
            SystemClock.uptimeMillis() - current.since < CONFIG_IN_FLIGHT_GRACE_MS
    }
}

/**
 * 「独立设置页现在在改谁」—— 一颗键，或一组组合键。**纯界面状态**，不落盘。
 *
 * ## 为什么要有这一层
 *
 *  「按键单独设置」是所有档共有的一件事（每颗键 / 每组组合都能自己决定接口、振动、提示）。
 *  如果页面按「从哪个页面进来的」去猜，就得到处写 `if (是组合) … else …`。
 *  把「改谁」摆成一个值，页面与卡片只认它 —— 入口那一侧只负责写这一个值。
 *
 * ## 与 `KeyWatchState` / `ComboEditState` 的关系
 *
 *  那两个记的是「列表里选中了哪一颗 / 打开了哪一组」，服务的是绑定网格；这一位只服务
 *  独立设置页，生命周期也短（进页时写、出页不必清 —— 下次进页会覆盖）。
 *
 * ## 「这一档能不能用」也归这里
 *
 *  [enabledIn] / [disabledReason] 是「停用的键 / 组合不能进独立设置」这条规则的**唯一实现**：
 *  三个入口（按键菜单那一行、组合菜单那一行、两页顶栏那颗设置键）都问它，谁也不许自己拼一句
 *  「已停用」—— 同一个判据写三份，早晚有一处忘改（见 AGENTS.md 一之二、2）。
 */
internal sealed interface SettingsTarget {
    /** 当前在改的那一份独立设置（读用）—— 配置里找不到时回 `null`，页面据它退场。 */
    fun ownerIn(config: Config): OwnSettings?

    /** 这一档**现在能不能用**（键 / 组合各自的 `enabled`）。停用 ⇒ 设置入口也按不动。 */
    fun enabledIn(config: Config): Boolean

    /**
     * 这一档停用时「为什么按不动」那一句；能用时为 `null`。
     *
     *  与页面里其它按不动的控件**同一句**（`KEY_DISABLED_ITEM` / `COMBO_DISABLED_ITEM`），
     *  并且名字走 `keyName` / `comboLabel` —— 那一份才是「一个键怎么念」的权威实现。
     */
    fun disabledReason(config: Config): HintText?

    data class Key(
        val id: KeyId,
    ) : SettingsTarget {
        override fun ownerIn(config: Config): OwnSettings = config.key(id)

        override fun enabledIn(config: Config): Boolean = config.key(id).enabled

        override fun disabledReason(config: Config): HintText? =
            if (enabledIn(config)) {
                null
            } else {
                HintText(
                    I18n.get(Strings.KEY_DISABLED_ITEM),
                    listOf(keyName(id)),
                )
            }
    }

    data class Combo(
        val id: String,
    ) : SettingsTarget {
        override fun ownerIn(config: Config): OwnSettings? = config.combos.firstOrNull { it.id == id }

        /**
         * 找不到这一档（刚删完、退场那一帧）按**能用**处理 —— 与菜单那边 `?: true` 同一口径：
         * 按停用处理会让顶栏那颗在一帧里闪一句「已停用」，而它下一刻就不在了。
         */
        override fun enabledIn(config: Config): Boolean = config.combos.firstOrNull { it.id == id }?.enabled ?: true

        override fun disabledReason(config: Config): HintText? {
            val combo = config.combos.firstOrNull { it.id == id } ?: return null
            if (combo.enabled) return null
            //  用用户认得出的名字（`侧键 和 音量 +`），不是 `cmb1` 那种内部 id ——
            //  组合多颗时压成一串，提示条里念得出来就够（它不按颗分开排）。
            val names = comboKeyNames(config, combo).joinToString(comboMemberSeparator)
            return HintText(I18n.get(Strings.COMBO_DISABLED_ITEM).format(names))
        }
    }
}

/** 这一位只在「独立设置」这一页里用；写它的只有两个入口（按键绑定页顶栏 / 组合编辑页顶栏）。 */
internal object SettingsTargetState {
    /** 默认值是侧键 —— 理论上进页前一定会被覆盖，给个合法值只是免得页面读到 null 崩掉。 */
    var current: SettingsTarget by mutableStateOf(SettingsTarget.Key(KeyId.PLUS))
}
