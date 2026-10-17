package com.lookie.opluskey.ui.state

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.suqi8.coui.kmp.basic.SnackbarHostState

/**
 * 「我们自己刚写下去的那一份配置还在路上」的宽限 —— 见 [HostLinkState.configPending]。
 *
 *  取 2 秒：真机实测这一趟（落盘 → 广播 → 注入侧回包）在 10~20ms 量级，2 秒是给忙碌 / 刚解冻
 *  留的余量。它只影响「乐观」这一小段 —— 回包一到就提前结束，等不到也会自己过期。
 */
internal const val CONFIG_IN_FLIGHT_GRACE_MS = 2_000L

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
    var hostEnabled by mutableStateOf(true)

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
     * 首页那条「模块未生效」提示的宿主状态 —— COUI 的 `SnackbarHost` 用它排队 / 显隐。
     *
     *  提示由首页弹，宿主却挂在这一层：`SnackbarHost` 要的是「底部对齐、不随内容滚动」的位置，
     *  而首页自己整个是滚动容器 —— 挂在页内就只能跟着内容滚了。
     */
    val snackbar = SnackbarHostState()
}
