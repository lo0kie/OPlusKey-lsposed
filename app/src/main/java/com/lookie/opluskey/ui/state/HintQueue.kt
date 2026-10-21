package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.log.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 队列里的一条提示。
 *
 * @param id 身份 —— 界面按它做 `key`：插入一条时只有新节点播入场，其余几条原地不动
 * @param slot **语义位**（`null` = 这一条没有位、每次都是新的一条）。同一个位上再来一句话
 *   不是「再加一条」而是**改这一条的字**（id 不变 ⇒ 界面上走换句动画，不重新入场）：
 *   连点同一个开关时，屏幕上应当只有一条在改字，而不是叠出一串「已开启 / 已关闭」。
 */
internal data class HintItem(
    val id: Long,
    val text: String,
    val slot: String? = null,
    /** 正在退场（180ms）。这段时间它仍占一格，界面按它播退场。 */
    val exiting: Boolean = false,
    /**
     * 退场的**出口方向**（只在 [exiting] 为真时有意义）：
     *  · `false` —— 到点自己走的，**原路往上**滑出去（与入场同一条路，看着像收回去了）；
     *  · `true` —— 被后来者挤出队列的（超出 [MAX_VISIBLE]），**往下**滑走 —— 它本来就是被
     *    「顶下去」的那一条（用户口径「到点自动消失往上」「超出队列的往下滑出配合 scale」）。
     */
    val exitingDown: Boolean = false,
)

/**
 * 提示条**队列** —— 后来的在最上面，最多 [MAX_VISIBLE] 条。
 *
 * ## 只有一种提示：会自己走的那种
 *
 *  曾经还有一条「常驻结论」（页面为什么是灰的，一直挂着）。它和回执在同一位置互相顶，
 *  后来改成队列并列之后又变成「两条说同一件事」—— 关主开关时「已关闭「按键绑定」」和
 *  「按键绑定已关闭，侧键交还系统」同时出现（用户口径「到底为什么我点主开关还是有两个提示」）。
 *  **那条通道已经整条删掉**：页面为什么是灰的，只在**点那些灰控件时**用一句回执说明
 *  （见 `disabledClickReason`）。不要再加回来。
 *
 * ## 为什么自己排（不用库的 `SnackbarHostState`）
 *
 *  库的 host 是「一个 `LazyColumn` + 每条 item 外面套一层横向 `AnchoredDraggable`」：
 *
 *   · 横滑容器那一层的锚点要等第一帧之后才量出来，出现那一下 offset 还停在锚点之外
 *     ⇒ 胶囊先往右挪一点、再被弹簧拉回（真机：「打开后向右轻微平移又拉回」）；
 *   · 收一条时条目还留在队列里画完退场动画，新的一条**排在它上面**
 *     ⇒ 连续两句时第二句从更高的地方落回底边（真机：「第二个会从垂直高度更高的地方瞬回」）。
 *
 *  所以这里自己排：队列顺序就是绘制顺序（`items[0]` 在最上面），入场由界面那一层按
 *  [HintItem.id] 播（见 `ui/screen/PageSnackbar.HintSlot`）。
 *
 *  **退场两个口**（都 180ms，见 `PageSnackbar.HintSlot`），这段时间它仍占一格；播完才摘：
 *   · **到点**的走原路往**上**（`exiting`、`exitingDown = false`，[retire]）；
 *   · **被挤掉**的（超出 [MAX_VISIBLE]）往**下**滑走（[enqueue] 补一个 `exitingDown = true`）——
 *     它本来就是被后来的那条顶下去的，方向反了才别扭。
 *
 * @param scope 页面级作用域 —— 关页时由 `HintCenter.dispose` 收掉
 * @param holdMs 自动消失的时长
 */
internal class HintQueue(
    private val scope: CoroutineScope,
    private val holdMs: Long,
) {
    /** 当前队列，`items[0]` 是**最上面**那一条（最新的）。 */
    var items by mutableStateOf<List<HintItem>>(emptyList())
        private set

    /** 每条的到期计时（按 [HintItem.id]）。 */
    private val timers = mutableMapOf<Long, Job>()

    private var nextId = 0L

    /**
     * 压入一条回执 —— **插到最上面**（后来的在上面）。
     *
     * @param slot 语义位（见 [HintItem.slot]）。给了位、且那个位上已经挂着一条时**不新增**：
     *   把那条的字换掉、重新计时（id 不变 ⇒ 界面走换句动画）。同一个开关连点两下，
     *   屏幕上应当是一条在改字，而不是两条「已开启 / 已关闭」叠着。
     */
    fun push(
        text: String,
        slot: String? = null,
        holdMs: Long? = null,
    ) {
        if (text.isEmpty()) return
        //  作用域已经收掉（页面关了）就**不写**：写进去没人计时，会留一条永不过期的提示。
        if (!scope.isActive) return
        //  同一个位上已经有一条（还没开始退场）⇒ 改那条的字，不新开一条
        val same = if (slot == null) null else items.firstOrNull { it.slot == slot && !it.exiting }
        if (same != null) {
            if (same.text != text) {
                items = items.map { if (it.id == same.id) it.copy(text = text) else it }
            }
            schedule(same.id, holdMs)
            return
        }
        val id = ++nextId
        //  同文的旧条**不直接摘**：它已经在屏幕上，摘掉会让它下面的条目瞬移一格。
        //  走与到点退场同一条路（往上收回、180ms），摘由 [scheduleRemoval] 按退场时长安排。
        val duplicates = items.filter { it.text == text }
        duplicates.forEach { scheduleRemoval(it.id) }
        val rest = items.filterNot { it.text == text }
        items =
            enqueue(
                listOf(HintItem(id, text, slot = slot)) + rest + duplicates.map { it.copy(exiting = true) },
            )
        //  被顶掉的旧条目要先摘掉它的计时，否则那个协程到点还会去改一个不存在的 id（无害但白跑）
        pruneTimers()
        schedule(id, holdMs)
    }

    /** 全收（关页时用）—— 这里**不等动画**：页面都要拆了，没人看得到那 100ms。 */
    fun clear() {
        timers.values.forEach { it.cancel() }
        timers.clear()
        items = emptyList()
    }

    /**
     * 给某一条起到期计时：到点直接摘掉。
     *
     *  @param holdMs 这一条自己的停留时长（`null` = 队列统一那一档）。给「挂到某件事结束为止」的
     *   提示用：「正在检查模块…」不该按固定秒数消失 —— 它的意思是「还在问」，问完就该换结论，
     *    与秒数无关（见 `PageCheckingHint`）。这时给一个**足够长的兜底**，真正收场靠 [retire]。
     */
    private fun schedule(
        id: Long,
        holdMs: Long? = null,
    ) {
        val hold = holdMs ?: this.holdMs
        if (hold <= 0) return
        timers.remove(id)?.cancel()
        timers[id] =
            scope.launch {
                delay(hold)
                retire(id)
            }
    }

    /** 把某个语义位上的提示**提前收掉**（播退场，不硬切）。 */
    fun retire(slot: String) {
        items.filter { it.slot == slot && !it.exiting }.forEach { retire(it.id) }
    }

    /**
     * 排队 + 把**超出 [MAX_VISIBLE] 的那条挤出去**。
     *
     *  被挤出去的那条**不直接消失**：跟到点退场一样先标 `exiting`，界面播一段 180ms 的退场，
     *  [EXIT_SETTLE_MS] 之后才摘。区别只在**方向** —— 它是被顶下去的，所以 `exitingDown = true`
     *  （用户口径「超出队列的消息往下滑出配合 scale」）。
     *
     *  两条规矩：
     *   · 正在退场的条目一律排在**末尾**、且**不参与条数计算** —— 否则它们会把新来的挤掉，
     *     队列越挤越短；排末尾也让它们待在原队列的下面，往下走的时候不越过别人。
     *   · 这里只标 `exiting`、不自己摘 —— 摘由 [scheduleRemoval] 按动画时长统一安排。
     *
     * @param list 新条目在最前（后来的在上面）
     */
    private fun enqueue(list: List<HintItem>): List<HintItem> {
        val live = list.filterNot { it.exiting }
        val dying = list.filter { it.exiting }
        val kept = live.take(MAX_VISIBLE)
        val squeezed = live.drop(MAX_VISIBLE)
        if (squeezed.isEmpty()) return kept + dying
        squeezed.forEach { scheduleRemoval(it.id) }
        return kept + dying + squeezed.map { it.copy(exiting = true, exitingDown = true) }
    }

    /**
     * 到点退场 —— 原路往**上**收回去（`exitingDown` 保持 `false`）。
     *
     *  和 [enqueue] 挤掉那条的区别只有方向：这条不是被谁顶掉的，从哪儿来就回哪儿去。
     */
    private fun retire(id: Long) {
        if (items.none { it.id == id && !it.exiting }) return
        timers.remove(id)?.cancel()
        items = items.map { if (it.id == id) it.copy(exiting = true) else it }
        scheduleRemoval(id)
    }

    /** 退场动画播完之后再摘 —— 时长与界面那一层的 `HINT_FLY_MS` 对齐。 */
    private fun scheduleRemoval(id: Long) {
        scope.launch {
            delay(EXIT_SETTLE_MS)
            remove(id)
        }
    }

    private fun remove(id: Long) {
        if (items.none { it.id == id }) return
        //  收拾队列这一步是**每条提示的常规收尾**（每条都要走一遍），不给 w ——
        //  w 留给「判据级」那些（收不下、被拒、链路断了），量大的行一律 d
        XLog.d("提示队列：摘掉 $id")
        items = items.filterNot { it.id == id }
        pruneTimers()
    }

    /** 计时表里已经没有的 id，把它们那些协程收掉。 */
    private fun pruneTimers() {
        val alive = items.mapTo(HashSet()) { it.id }
        val dead = timers.keys.filterNot { it in alive }
        dead.forEach { timers.remove(it)?.cancel() }
    }
}

/**
 * 退场的滑出时长 + 一帧余量 —— 与界面的 `HINT_FLY_MS`（180ms）对齐。
 *
 *  多出的那点余量是给「动画最后一帧才把 alpha 归零」留的：早摘一帧就是硬切。
 *  两个出口（到点往上 / 被挤掉往下）共用这一个时长。
 */
private const val EXIT_SETTLE_MS = 220L

/** 最多同时显示几条。 */
internal const val MAX_VISIBLE = 3
