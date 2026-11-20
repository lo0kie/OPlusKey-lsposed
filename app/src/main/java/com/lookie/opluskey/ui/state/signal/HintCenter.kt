package com.lookie.opluskey.ui.state.signal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.lookie.opluskey.ui.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 操作提示停留多久（毫秒）。库的 `SnackbarDuration.Short` 是 4s，这里短一点：
 * 回执只是一句「已删除」，看完就走，不该一直压着页面顶部。
 */
private const val HINT_HOLD_MS = 2200L

/**
 * **页面上那一叠提示条**，以及它们的作用域。
 *
 *  **只有会自己走的那种**：那条「常驻结论」（页面为什么是灰的）已经整条删掉 —— 它与回执
 *  要么互相顶、要么并列说同一件事（用户口径「去掉常驻提示，不允许发」）。见 `HintQueue` 的类注释。
 *
 *  与「链路 / 报活」那一位（`HostLinkState`）分开：两者的生命周期正相反，原先混在一个类里，
 *  于是「链路诊断状态」莫名其妙带上了 `dispose()` —— 任何一个只想读 `hostServiceUp` 的地方，
 *  都得先背着一份会 `cancel()` 作用域的对象。
 *
 *  这里只管「这些话怎么排」：写什么内容、什么时候该显示，是调用方的业务。排序 / 淘汰 / 计时
 *  都在 [HintQueue] 里（那份类注释讲了为什么自己排而不用库的 host）。
 */
internal class HintCenter {
    /**
     * 提示用的作用域 —— 页面级，关页时由 [dispose] 收掉。
     *
     *  `Main.immediate`：提示是 Compose 状态，写它就该在主线程；而 [hint] 的调用点有一半
     *  在事件回调里（已经在主线程），`immediate` 让那几处不必多绕一次消息队列。
     *
     *  **必须声明在 [queue] 之前**：Kotlin 的属性按书写顺序初始化，放到后面那份
     *  `HintQueue(scope, …)` 拿到的会是 `null`（编译期不报，运行时就是一次 NPE）。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 那一叠提示 —— 后来的在上面，最多 [MAX_VISIBLE] 条。 */
    val queue = HintQueue(scope, holdMs = HINT_HOLD_MS)

    /**
     * 弹一条**操作提示**（取代原来的 Android Toast）—— 压到最上面。
     *
     * @param slot 语义位：同一个位上再来一句话是**改那一条的字**，不是再加一条（见 [HintQueue.push]）。
     *   不传 = 每次都是新的一条（保存失败这类一次性回执就该这样）。
     * @param icon 前置图标（`null` = 界面画默认的 alert-circle，见 `HintItem.icon`）。
     * @param loading 前置图标画成转圈的 loading（见 `HintItem.loading`）。
     */
    fun hint(
        text: String,
        slot: String? = null,
        holdMs: Long? = null,
        icon: ImageVector? = null,
        loading: Boolean = false,
    ) {
        queue.push(text, slot, holdMs, icon, loading)
    }

    /** 把某个语义位上的提示提前收掉（见 [HintQueue.retire]）。 */
    fun retire(slot: String) = queue.retire(slot)

    /** 关页时收掉作用域 —— 还在计时的提示跟着停，不会有人再往旧实例里写。 */
    fun dispose() {
        //  先清空队列再收作用域：只 cancel 的话，组合树上那几个 HintSlot 还读着旧字符串 ——
        //  旧页面在拆除前最后一帧会再画一遍（而 `push` 此后已经拒绝写入）。
        queue.clear()
        scope.cancel()
    }
}

/**
 * 「变量清单该重读一遍了」的记号 —— 两页（「自定义变量」/「变量操作」）各自 `remember` 住一份清单，
 * 而这份清单的**来源**有两种变化方式：
 *
 *  · 自己改的：新建一颗、改一个值、删一颗（同一趟里那两页都不会自己重读）；
 *  · **换了配置**：变量跟着配置走（见 `core/config/ProfileVars`），切一份配置就换了一整张表。
 *
 *  两件事对页面的要求是同一个（重读一遍），所以共用一个计数器而不是各记一份：
 *  分开记的话，切换配置之后只有记得那一页会刷新，另一页还是上一份的变量。
 *
 *  放在 `ui/state` 而不是某一页的页级状态里：改这一位的是**配置**那一侧（`about` 包的切换动作），
 *  读它的是变量那两页 —— 谁都不该去 import 对方的页级对象。
 */
internal object VarListSignal {
    /** 变过几次 —— 页面的 `LaunchedEffect` 拿它当键（初值 0 就是首次进页那一次读盘）。 */
    var generation by mutableIntStateOf(0)
        private set

    /** 记号 +1：让在栈里的变量页下次组合时重读清单。 */
    fun bump() {
        generation++
    }
}

/**
 * 页面级的**一问一答**：一个待确认的载荷，加上「有没有人在等回答」这一位。
 *
 * ## 为什么收成一个
 *
 *  项目里那几问（停用 / 启用键与组合的连带、切触发方式、关掉「松手时触发」）原先各写一个
 *  `object`，每条字段各占一个 `mutableStateOf`，`ask()` 挨个赋值、`clear()` 挨个归零。
 *  三个毛病：
 *
 *  · **组合状态会被读到半截** —— `ask()` 赋完 `kind` 还没赋 `keys` 的那一帧，
 *    已经有人在读「问的是什么、要改哪几项」；
 *  · **归零漏一位就挂住** —— 少清一个字段，下一问就带着上一问的残留（某一问的「连带项」）。
 *  · 同一个模式复制三份，`asking` / `clear` 的正确性要在三处各修一遍。
 *
 *  一个载荷对象换一个字段（`mutableStateOf` 的读写在快照上是一个整体），上面三条一起消失。
 *
 * ## 它只做三件事
 *
 *  · [set] 挂上一问（同一时刻只有一问，后到的覆盖前的）；
 *  · [get] 读那一问（`null` = 没人等回答）；
 *  · [clear] 收掉。
 *
 *  业务判据（问什么、答了之后改哪几位）留在各自的 `object` 里 —— 这一层不认识键、组合或触发方式。
 */
internal class PendingAsk<T : Any> {
    private var payload by mutableStateOf<T?>(null)

    /** 有没有人在等回答 —— 窗该不该露面。 */
    val asking: Boolean get() = payload != null

    /** 当前那一问（`null` = 没有）。 */
    val pending: T? get() = payload

    /** 挂上一问 —— 覆盖前一个（同一时刻只该有一问）。 */
    fun set(value: T) {
        payload = value
    }

    /** 收掉（取消 / 答完都要走这里，唯一的归零点）。 */
    fun clear() {
        payload = null
    }
}

/**
 * 顶栏那条**固定带**的高度 —— 带子在顶栏 `bottomContent` 那棵树里自己量，页内容那棵树里读。
 *
 *  为什么要有这条通道：三条带子（「按键绑定」的分段控件 / 「自定义命令」的类型选择器 /
 *  「选择应用」的搜索框）都挂在**顶栏**里 —— 位置、底色、模糊、下沿那条细线全由顶栏那一层
 *  负责（见 `PageHeader` 的 `bottomContent`），而页内容顶上那个等高的占位 Spacer 在**另一棵树**
 *  里，两边只有走页面级状态这一条路能对齐。
 *
 *  一页一份，**不共用**：页面转场期间相邻两屏可能同时在组合里，共用一个值只会互相改对方的占位。
 *
 *  首帧还没量到时是 0（占位少一截），下一帧自纠 —— 不要为此在带子里写死高度：
 *  带子高度随字号与语言变过（[report] 进来的就是实测值）。
 */
internal class BandHeight(
    initialPx: Int = 0,
    /**
     * 量到之后回写一份（按页缓存，见 `PageChrome.headerHeights`）。
     *
     *  为什么需要它：页面出栈再回来时这一份状态是**重新建**的，起点回到 0 就只能按兜底常量
     *  排版一帧，而实测值往往与那个常量差几个 dp —— 真机表现就是「从子页退回，内容整体上移
     *  一点点」。有缓存就从上次量到的值起步。
     */
    private val onReport: (Int) -> Unit = {},
) {
    private val px = mutableStateOf(initialPx)

    /** 带子那一侧：把 `Modifier.onSizeChanged` 报来的高度原样交进来。 */
    fun report(heightPx: Int) {
        px.value = heightPx
        onReport(heightPx)
    }

    /** 页内容那一侧：读成 dp —— 组合期读，跟着测量结果重组。 */
    @Composable
    fun dp(): Dp = with(LocalDensity.current) { px.value.toDp() }
}

/**
 * 上面那份 [BandHeight] 的**按页分档** —— 一页一份，第一次用到时现建。
 *
 *  为什么是一张表而不是每页一个字段：带子是「页面自带的一样东西」，每加一页就得往页面对象上
 *  挂一个新字段，而那些字段唯一的差别就是名字（曾经有七个，其中一个还没人读）。按 `Screen`
 *  索引之后，「这一页的带子」只有一种取法（`bands[screen]`），加页面不必再动页面对象。
 *
 *  **仍然不共用**：同一个 `Screen` 一个实例，转场期间相邻两页各拿各的，不会互相改占位。
 */
internal class BandHeights {
    private val heights = HashMap<Screen, BandHeight>()

    /** 这一页的带子（第一次问时现建一份）。 */
    operator fun get(screen: Screen): BandHeight = heights.getOrPut(screen) { BandHeight() }
}
