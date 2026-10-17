package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.suqi8.coui.kmp.basic.SnackbarHostState

/**
 * 「链路 / 报活」那一域的状态 —— 首页那条提示与「更多选项 → 运行状态」共用的那几项结论。
 *
 *  两处判据都在这几个字段上：宿主侧有没有在跑（[hostServiceUp] + [hostAliveToken]）、
 *  注入侧回没回包（[injectStatus] + [injectAliveToken]）、以及「设置生效」的配置侧那半
 *  （见 `ui/state/LinkCheck.kt` 的 `linkReady`）。
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
     * 首页那条「模块未生效」提示的宿主状态 —— COUI 的 `SnackbarHost` 用它排队 / 显隐。
     *
     *  提示由首页弹，宿主却挂在这一层：`SnackbarHost` 要的是「底部对齐、不随内容滚动」的位置，
     *  而首页自己整个是滚动容器 —— 挂在页内就只能跟着内容滚了。
     */
    val snackbar = SnackbarHostState()
}
