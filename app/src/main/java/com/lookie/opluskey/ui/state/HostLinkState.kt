package com.lookie.opluskey.ui.state

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 「我们自己刚写下去的那一份配置还在路上」的宽限 —— 见 [HostLinkState.configPending]。
 *
 *  取 2 秒：真机实测这一趟（落盘 → 广播 → 注入侧回包）在 10~20ms 量级，2 秒是给忙碌 / 刚解冻
 *  留的余量。它只影响「乐观」这一小段 —— 回包一到就提前结束，等不到也会自己过期。
 */
internal const val CONFIG_IN_FLIGHT_GRACE_MS = 2_000L

/**
 * 操作提示停留多久（毫秒）。库的 `SnackbarDuration.Short` 是 4s，这里短一点：
 * 回执只是一句「已删除」，看完就走，不该一直压着页面底部。
 */
private const val HINT_HOLD_MS = 2200L

/**
 * 「链路 / 报活」那一域的状态 —— 首页那条提示与「更多选项 → 运行状态」共用的那几项结论。
 *
 *  两处判据都在这几个字段上：宿主侧有没有在跑（[hostServiceUp] + [hostAliveToken]）、
 *  注入侧回没回包（[injectStatus] + [injectAliveToken]）、以及「设置生效」的配置侧那半
 *  （见 `ui/LinkCheck.kt` 的 `linkReady`）。
 *
 *  为什么**代次**（`*AliveToken`）单独立字段：那几个「在不在」的布尔初值必须是「正常」
 *  （否则每次进页面都会闪一下「未生效」），于是它们不能当判据用；代次初值是 0，
 *  变了才有回音，这才是可判的。
 */
internal class HostLinkState {
    /**
     * 宿主侧的模块有没有在跑 —— 也就是**执行侧有没有在工作**。
     *
     *  初值取 `true`：首帧还没查，先按「正常」画。首页那个 `LaunchedEffect` 组合完立刻查一次，
     *  查完再按真实结果画。
     */
    var hostServiceUp by mutableStateOf(true)

    /** 宿主侧报活的**代次** —— 每收到一条报活就自增；进页时记下它、等一档再看变没变。 */
    var hostAliveToken by mutableIntStateOf(0)

    /**
     * 注入侧（system_server）回包的内容 —— `null` = 问过但**没回音**（注入侧没装上，
     * 或它那支 AMS 广播入口没挂上）。初值就是 `null`：没问到之前一律按「未就绪」算。
     */
    var injectStatus by mutableStateOf<InjectStatus?>(null)

    /** 注入侧回包的**代次** —— 与 [hostAliveToken] 同一个道理。 */
    var injectAliveToken by mutableIntStateOf(0)

    /** 宿主报活带回来的「它读到的主开关真值」—— `false` ⇒ 它读到的是默认值（主开关关着 / 还没解锁）。 */
    var hostEnabled by mutableStateOf(false)

    /** 宿主报活带回来的「最后一次执行动作」（`elapsedRealtime()`，0 = 从未）。 */
    var hostLastActionAt by mutableLongStateOf(0L)

    /**
     * 我们自己**刚写下去**的那一份配置的指纹（`ConfigBroadcast.tag`），`null` = 没有在途的写入。
     *
     *  从「写盘成功」到「注入侧回包」之间，本地指纹已经换了、注入侧手上还是旧的 ——
     *  这一段两边**必然**对不上，见 [configPending]。
     */
    var pendingConfigTag by mutableStateOf<String?>(null)
        private set

    /** 上面那一份写下去的时刻（`SystemClock.uptimeMillis()`），0 = 没有在途的写入。 */
    var pendingConfigSince by mutableLongStateOf(0L)
        private set

    /** 记下「[tag] 这一份刚写下去、注入侧还没回包」。 */
    fun markConfigPending(tag: String) {
        pendingConfigTag = tag
        pendingConfigSince = SystemClock.uptimeMillis()
    }

    /** 结束「在途」—— 回包对上了，或宽限到期。 */
    fun clearConfigPending() {
        pendingConfigTag = null
        pendingConfigSince = 0L
    }

    /**
     * [tag] 这一份是不是**我们自己刚写下去、还在路上**的那一份（且在 [CONFIG_IN_FLIGHT_GRACE_MS] 内）。
     *
     *  为什么需要这一位：`configSideOk` 比的是「注入侧手上那份的指纹」与本地指纹，而写完到回包
     *  之间它们**必然**不相等。不把这一段认成「在途」的话，每拨一个开关都会有一两帧被判成
     *  「设置没送到」—— 整页控件跟着打灰、底部提示被换掉再换回来，看到的就是全页文字闪一下。
     */
    fun configPending(tag: String): Boolean =
        pendingConfigTag == tag &&
            pendingConfigSince > 0 &&
            SystemClock.uptimeMillis() - pendingConfigSince < CONFIG_IN_FLIGHT_GRACE_MS

    /**
     * 提示用的作用域 —— 页面级，关页时由 [dispose] 收掉。
     *
     *  `Main.immediate`：提示是 Compose 状态，写它就该在主线程；而 `hint()` 的调用点有一半
     *  在事件回调里（已经在主线程），`immediate` 让那几处不必多绕一次消息队列。
     *
     *  **必须声明在两份 [HintState] 之前**：Kotlin 的属性按书写顺序初始化，放到后面那份
     *  `HintState(scope, …)` 拿到的会是 `null`（编译期不报，运行时就是一次 NPE）。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 首页那条「模块未生效」**常驻**结论 —— 一直挂着，直到结论变了或被代码收掉。
     *
     *  与 [actionHint] 分成两份状态，因为两者的生命周期正相反：这条不会自己走，
     *  另一条几秒就消失。放在同一份里会互相顶掉（收常驻那条时把刚做的动作回执一起收掉）。
     *  实现见 [HintState]（为什么不用库的 `SnackbarHostState`，那里写着）。
     */
    val statusHint = HintState(scope, holdMs = 0)

    /**
     * **操作提示** —— 用户做完一个动作之后的回执（已删除、保存失败、已切换到某颗键……）。
     *
     *  两秒多就自己走；新的一句**直接换掉**旧的一句，不排队（见 [HintState] 的类注释）。
     */
    val actionHint = HintState(scope, holdMs = HINT_HOLD_MS)

    /**
     * 弹一条**操作提示**（取代原来的 Android Toast）。
     *
     *  只换字符串：没有队列、没有「先收再弹」—— 那套是给库的 host 准备的，
     *  也正是「第二句从更高的地方瞬回」的来源（见 [HintState]）。
     */
    fun hint(text: String) {
        actionHint.show(text)
    }

    /** 换掉那条常驻提示（结论变了）；传 `null` = 收掉。 */
    fun showStatus(text: String?) {
        if (text == null) statusHint.dismiss() else statusHint.show(text)
    }

    /** 收掉那条常驻提示（离开首页 / 关页时）。 */
    fun dismissStatus() {
        statusHint.dismiss()
    }

    /** 关页时收掉作用域 —— 还在计时的提示跟着停，不会有人再往旧实例里写。 */
    fun dispose() {
        //  先把两条提示收成 null，再收作用域：只 cancel 的话，组合树上那两个 HintSlot
        //  还读着旧字符串 —— 旧页面在拆除前最后一帧会再画一遍（而 `show` 此后已经拒绝写入）。
        statusHint.dismiss()
        actionHint.dismiss()
        scope.cancel()
    }
}
