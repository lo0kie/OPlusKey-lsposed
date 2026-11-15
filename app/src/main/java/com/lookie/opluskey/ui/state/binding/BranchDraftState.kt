package com.lookie.opluskey.ui.state.binding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.state.signal.PendingAsk

/**
 * 「系统场景」页的草稿 —— 当前这颗键（或这一档组合键）的**整张场景表**。
 *
 * ## 一块是什么
 *
 *  一块 = 一组条件 + **完整的三档手势**（见 `SceneRule`）。翻转之前这里是「一条规则一个手势」，
 *  页面上还按手势分成三页；现在一页就是这张表，三档在每一块里各占一行。
 *
 *  ## 为什么不再是「一条命令」
 *
 *  场景不占用绑定：它是叠在按键绑定之上的一张**顺序表**，命中「条件成立且这一档有值」的第一块
 *  就执行它那一档的动作，一块都不中就走按键绑定里那一档的命令（见 `KeyConfig.actionFor`）。
 *  所以这一页编的是 `KeyConfig.scenes`，不是命令库里的一条记录 —— 没有名字、不进「已保存的命令」。
 *
 * ## 与 [CommandDraft] 同一条规矩
 *
 *  草稿提到页面级：从这一页 push 到「选择应用」挑完再退回来，已经加好的场景还在。
 *  [dirty] 与载入时那份快照比，退出时才问「放弃未保存的」。
 */
internal class BranchDraft {
    /** 整张场景表（顺序即判定顺序）。 */
    val scenes = mutableStateListOf<SceneRule>()

    /**
     * 与 [scenes] 一一对应的**行身份** —— 只服务界面上的进出场动画，不落盘、不参与保存。
     *
     *  为什么草稿要记这个：`AnimatedVisibility` 的状态是按**组合位置**记身份的，而删掉中间一行会让
     *  后面的行整体前移 —— 前移的那一行会继承被删那一行的「已退场」状态，当场再播一遍入场、下方
     *  还会跳一下。有了 id，每行的动画状态跟着自己走，删谁都只动那一行。
     */
    val ids = mutableStateListOf<Int>()

    /**
     * 与 [scenes] 一一对应的**配置槽位** —— 这一块在配置那份表里的下标（`null` = 这一趟新加的块，
     * 配置里还没有它的位置）。
     *
     *  为什么要有它：「场景动作」页那颗「确认」只该写**这一块**（那一页与「系统场景」页是两件
     *  独立的事，整表写回会把上一页还没确认的改动一起落盘）。而块与块之间本来没有身份 ——
     *  这个下标是**载入那一刻**按位置对上的（[load] 的 `source` 就是配置里那张表），之后
     *  删块 / 挪块都跟着行自己走（[remove] / [move] 与 [ids] 同步），所以它仍然指得准。
     */
    val slots = mutableStateListOf<Int?>()

    /** 下一个要发的行身份 —— 只增不减（重载 / 重置时归零）。 */
    private var nextId = 0

    /**
     * 刚加的那一行的身份（[ids] 里的值；[NO_FRESH_ROW] = 没有）—— 只有它播入场。
     *
     *  住在页面级、不住 `BranchCards` 的 `remember`：加一条的入口在**页外**（顶栏右上角那颗），
     *  它写不到页内的局部状态。
     */
    var freshId by mutableStateOf(NO_FRESH_ROW)

    /**
     * 草稿属于哪颗键 / 哪一档组合键 —— 换了就重新载入（见 [load]）。
     *
     *  **必须是组合状态**：`BranchCards` 拿它当「草稿载入没有」的闸门（载入前整块不画，
     *  免得空态 → 表格那一下播成进场动画）。普通 `var` 改了不触发重组，那一块会一直不画。
     */
    var owner: String? by mutableStateOf(null)

    /** [load] 进来的那份快照 —— 只服务 [dirty]。 */
    private var loaded: List<SceneRule> = emptyList()

    val hasContent: Boolean get() = scenes.isNotEmpty()

    /** 有没有改过还没保存的东西。 */
    val dirty: Boolean get() = scenes.toList() != loaded

    /**
     * 载入某颗键（或某一档组合键）的整张场景表。
     *
     *  [owner] 是「在编谁」的标识：换键时靠它判「要不要重灌」—— 否则会看到上一颗键的场景。
     *  行身份一起重发：换了一颗键就是新的一批行，动画没必要接着上一颗的账算。
     */
    fun load(
        owner: String,
        source: List<SceneRule>,
    ) {
        this.owner = owner
        scenes.clear()
        ids.clear()
        slots.clear()
        nextId = 0
        source.forEachIndexed { index, scene ->
            scenes.add(scene)
            ids.add(nextId++)
            //  载入这一刻草稿与配置一一对应：第 i 块就在配置的第 i 格
            slots.add(index)
        }
        loaded = source
    }

    fun reset() {
        scenes.clear()
        ids.clear()
        slots.clear()
        nextId = 0
        loaded = emptyList()
        owner = null
    }

    /**
     * 整表落盘之后的收尾：草稿与配置刚对齐 ⇒ 行身份与槽位一起重发（[load] 那一套）。
     *
     *  不用 `load` 的地方是「只写了一块」那一趟（见 [noteBlockSaved]）：那时草稿仍是屏幕上的
     *  那份，重建只会让退回上一页时那几行重播一遍入场。
     */
    fun rebase(scenes: List<SceneRule>) {
        load(owner ?: return, scenes)
    }

    /**
     * 只写了**一块**之后的收尾：把快照里那一格换成**刚写下去的那一块**，[scenes] 与行身份原样不动。
     *
     *  [scene] 取的是落盘那一块，不一定等于草稿里那一块：「场景动作」页只写三档手势，条件改动
     *  仍留在草稿里（见 `saveSceneBlock`）—— 拿草稿那一块当快照，会把没落盘的条件改动一起算成
     *  「已保存」。
     *
     *  [dirty] 照常按快照算 —— 别的块若还有没确认的改动、或这一块的条件还没确认，上一页那颗
     *  「确认」自己还亮着；都对上了的话，这一步之后整份就是干净的。
     */
    fun noteBlockSaved(
        index: Int,
        scene: SceneRule,
    ) {
        val slot = slots.getOrNull(index) ?: return
        if (slot !in loaded.indices) return
        loaded = loaded.toMutableList().also { it[slot] = scene }
    }

    /** 第 [index] 块在配置那份表里的下标；`null` = 这一趟新加的块（配置里还没有它）。 */
    fun slotOf(index: Int): Int? = slots.getOrNull(index)

    /** 载入时配置里 [slot] 位置那一块 —— 判「配置在别处变过没有」；[slot] 为 `null` 时答 `null`。 */
    fun loadedAt(slot: Int?): SceneRule? = slot?.let { loaded.getOrNull(it) }

    /**
     * 追加一块，返回它的行身份。
     *
     *  界面拿这个 id 认出「刚加的是哪一行」，只给它播入场 —— 其余行是载入时就有的，不该再展开一次。
     *  槽位给 `null`：这一块还没进配置，只有整表落盘才写得下去。
     */
    fun add(scene: SceneRule): Int {
        scenes.add(scene)
        ids.add(nextId)
        slots.add(null)
        return nextId++
    }

    fun update(
        index: Int,
        scene: SceneRule,
    ) {
        // 行身份不动：换条件 / 换动作是同一块改内容，不是一行换成了另一行（否则每选一次都会闪）
        if (index in scenes.indices) scenes[index] = scene
    }

    fun remove(index: Int) {
        if (index !in scenes.indices) return
        scenes.removeAt(index)
        if (index in ids.indices) ids.removeAt(index)
        if (index in slots.indices) slots.removeAt(index)
    }

    companion object {
        /** 键的标识 —— 只用来判「草稿还是不是这一颗的」。 */
        fun ownerOf(key: KeyId): String = key.id

        /**
         * 一档组合键的标识 —— 与 [ownerOf] 同一用途、同一个字符串位置。
         *
         *  加 `combo:` 前缀是必须的：组合键 id 是 `cmb1`、单键 id 是 `pro` / `ck1` 那些，
         *  撞不上，但两种「在编谁」混在同一个字段里，光看一个 id 分不出该去 `combos` 还是
         *  `keys` 找 —— 判据要能从标识本身读出来。
         */
        fun comboOwnerOf(comboId: String): String = "combo:$comboId"
    }
}

/** 页面级那一份（见 `SettingsPage.branchDraft`）。 */
internal class BranchDraftState {
    /**
     * **只有一份草稿** —— 翻转之后这一页不再按手势分页：一张表里每块自带三档，
     *  所以「三页各一份草稿」那套跟着手势维度一起没了。
     */
    private val draft = BranchDraft()

    /**
     * 这一趟在编的是**哪一档组合键**（`ComboKey.id`）；`null` = 在编某颗单键。
     *
     *  两个来源（`Config.keys` / `Config.combos`）的场景表形状完全一样，只有「从哪张表读、
     *  写回哪张表」不同 —— 这一位就是那个判据。
     */
    var comboId: String? by mutableStateOf(null)

    /** 那一份草稿。 */
    fun now(): BranchDraft = draft

    /** 出栈时把草稿与组合键来源一起清掉（下次进来是重新载入的那一份）。 */
    fun discard() {
        draft.reset()
        comboId = null
    }
}

/** 「没有刚加的那一行」的哨兵值（行身份从 0 起，撞不上）。 */
internal const val NO_FRESH_ROW = -1

/**
 * 「场景动作」页这一刻在编**哪一块** —— 三样一起换、一起清。
 *
 * ## 三样为什么是一个值
 *
 *  它们永远同进同出：`openSceneBinding` 一次定下「第几块 + 停在哪一档 + 进来时长什么样」，
 *  出栈时一次清掉。分开三个字段就得靠调用方记住「三个都要写」，而中间那一帧读者可能看到
 *  「新的块号 + 旧的档位」「新的块号 + 上一块的基线」—— 后者会让「改没改」判错（那一页的
 *  「放弃」据此还原）。
 *
 * ## 各是什么
 *
 *  @param at 第几块（`0` 起）；[NONE] 用一个负数表示「不在编任何一块」
 *  @param gesture 进来时停在哪一档手势 —— 顶栏那条分段控件的分页器在 push 那一刻按它归位
 *    （见 `PushResetsPagers`）。存的是**档位本身**（`GestureTrigger`）而不是页号：页号由
 *    `sceneGesturePage()` 按当前那张表换算，而档位不随表变
 *  @param baseline 进来时那一块长什么样 —— 这一页的「改动」与「放弃」都按它判。
 *    不能只看整份草稿的 `dirty`：同一份草稿还有别的块在上一页可能已经改过，
 *    拿它当判据会变成「在上一页改了点东西 → 进这一页看一眼 → 按返回却弹放弃」。
 *    它同时是「放弃」要回滚到的值 —— 直接 `popBackStack` 只把人带回去，改动仍躺在草稿里，
 *    退回上一页那颗「确认」照样能把它写下去
 */
internal class SceneBinding(
    val at: Int,
    val gesture: GestureTrigger,
    val baseline: SceneRule?,
) {
    /** 这一位真的在编一块吗（[NONE] 为假）。 */
    val editing: Boolean get() = at >= 0

    /** 只换「进来时那一块」—— 落盘之后要把基线挪到刚确认的那一块上。 */
    fun withBaseline(rule: SceneRule?): SceneBinding = SceneBinding(at, gesture, rule)

    companion object {
        /** 不在编任何一块。 */
        val NONE = SceneBinding(-1, GestureTrigger.Click(1), null)
    }
}

/**
 * 「现在在编辑哪一颗键」—— **纯界面状态**，不落盘。
 *
 * ## 它只剩这一件事了
 *
 *  自定义按键的清单本身（有哪些键、各叫什么、绑了什么）现在是**配置的一部分**
 *  （`Config.customKeys` + `Config.keys`），由 `ConfigKeys.kt` 那几个扩展函数增删改、
 *  由 `SettingsPage.write` 落盘并广播。早先这里存着一份 `mutableStateListOf`，是「还没接线」
 *  时的临时落脚点：进程一死清单就没了，注入侧也根本不知道有这些键。
 *
 *  留在这里的只有「用户在菜单里点中了哪一颗」—— 它只在**这一趟停留**里有效：
 *  关页（`SettingsPageHooks.clearPageRefs`）与从设置重进（`SettingsPage.onHostReentered`）
 *  都会把它收回侧键。写进配置反而是错的。
 *
 * ## 为什么存 id 而不是 `KeyId` 对象
 *
 *  改名会换一个 `KeyId` 实例（身份不变）。存对象的话，改完名 `selected` 还指着旧实例，
 *  菜单里那个对勾会落到一颗「已经不存在的键」上。存 id、每次从配置现解析，就没有这个缝。
 */
internal object KeyWatchState {
    /** 当前在编辑哪一颗键的 id；`null` = 侧键。 */
    var selectedId by mutableStateOf<String?>(null)
        private set

    /** 在菜单里点了某一组（`null` = 侧键）。 */
    fun select(id: String?) {
        selectedId = id
    }

    /**
     * 当前在编辑的那颗键 —— 从 [config] 现解析。
     *
     *  配置里找不到（那颗键刚被删掉）就退回侧键：宁可让用户看到侧键的绑定，
     *  也不要让页面指着一颗不存在的键去读写。
     */
    fun editing(config: Config): KeyId = selectedId?.let { config.keyIdOf(it) } ?: KeyId.PLUS

    /** 选中的键被删掉之后，把选中态收回侧键（删除那条路调用）。 */
    fun forget(id: String) {
        if (selectedId == id) selectedId = null
    }
}

/**
 * 「组合键」那两页的界面状态 —— 现在在看哪一档、选键弹窗开着没有。
 *
 *  与 [KeyWatchState] 同一套：**界面状态，不落盘**。只存 id 不存 `ComboKey` 对象 ——
 *  每次写盘都会换一份新对象，存下来的那份会过期（改完动作回来还是旧值）。
 *
 *  为什么放在页面级（`object`）而不是 `remember` 在页面里：编辑页与列表页是**两个 Screen**，
 *  压栈 / 出栈都会重建组合，参数又带不进 `Screen` 枚举（它是个无参枚举）。
 */
internal object ComboEditState {
    /** 正在编辑哪一档组合（`null` = 没有）。 */
    var editingId by mutableStateOf<String?>(null)
        private set

    /**
     * 「选择按键」弹窗要为**哪一类**组合选键 —— `null` = 没开。
     *
     *  值是那一类新建时的触发方式：等待组合是 [ComboKey.MODE_WAIT]，按住组合先按
     *  [ComboKey.MODE_HOLD_FIRST] 占位（选完键再问「先按住第一颗还是第二颗」）。
     *  类别在**添加那一刻**就定下来，之后不再改（方式是一档的身份，见 `ComboKey.mode`）。
     */
    var pendingKind by mutableStateOf<Int?>(null)

    /**
     * 等待组合那一列的**筛选**：只看含这颗键的等待组合（`null` = 全部）。
     *
     *  不落盘 —— 它是「这一趟想找哪一组」，不是配置的一部分。单选（菜单里打勾的那一行）。
     *  那一组被删掉 / 那颗键不再出现在该段任何组合里时，列表页会把它退回 `null`（见 `KeysScreen`）。
     *
     *  **两段各一份**：两列各自回答「这一列里有哪些」，共用一个会把另一列一起筛掉。
     */
    var waitFilter by mutableStateOf<String?>(null)

    /** 按住组合那一列的筛选 —— 同上，独立一份。 */
    var holdFilter by mutableStateOf<String?>(null)

    /**
     * 编辑页停在**哪一页**：`0` = 短按、`1` = 长按。
     *
     *  进「条件执行」时要用它：那一页编的是「某一档手势的规则」，而这颗 ⋮ 是在**当前这一页**
     *  点下去的 —— 用户停在长按页、点进去却在编短按的规则，是最自然的一种错。组合键只有这两档。
     */
    var page by mutableStateOf(0)

    fun open(id: String) {
        editingId = id
    }

    /** 离开编辑页时清掉 —— 下次进来不会先画上一档一帧。 */
    fun clear() {
        editingId = null
    }
}

/**
 * 「停用 / 启用」之前的那一问 —— 键与组合的连带关系。
 *
 *  用户口径（三条一起看才闭环）：
 *   · **停用键** ⇒ 含它的组合必然也停，但**先说清有哪几个**（不弹窗的话，组合列表会忽然多出
 *     几行「已停用」，用户不知道是这次按出来的）；
 *   · **启用键** ⇒ 含它的组合不自动开（用户可能正想关掉它们），问一句；
 *   · **启用组合** ⇒ 它成员的键若还关着，这组根本跑不起来，同样先问一句要不要把键也开。
 *
 *  ## 为什么分成「一定要改的」与「一起改的」
 *
 *  [keys] / [combos] 是用户**明确点名的**那几项，答「否」也照改；[extraKeys] / [extraCombos]
 *  是「连带」的那一批，只有答「是」才跟着动。这样三种问法共用一句语义，否定也不会把用户
 *  点名要改的东西一并取消（那会让人以为没点上）。
 *
 *  状态住在这里而不是某个页面：触发点在三处（按键列表的开关、按键绑定页的 ⋯ 菜单、组合页的
 *  开关），窗画在各自页面上 —— 几处共用一个待确认对象，逻辑只需要一份。
 *
 *  五项读成一个 [Payload]（而不是五个 `mutableStateOf`）：`ask()` 中间那一帧不能让人读到
 *  「问的是启用、要改的键还是空的」。归零也只有一处（[PendingAsk.clear]）。
 */
object KeysAsk {
    /** 这一批最终是**停用**还是**启用**。 */
    enum class Kind { DISABLE, ENABLE }

    /** 一次待确认的载荷 —— 五项一起挂、一起收。 */
    class Payload(
        val kind: Kind,
        /** 用户点名的键（答「否」也照改）。 */
        val keys: Set<String>,
        /** 用户点名的组合（答「否」也照改）。 */
        val combos: Set<String>,
        /** 连带一起改的键（组合的成员键）—— 只有答「是」才动。 */
        val extraKeys: Set<String>,
        /** 连带一起改的组合 —— 只有答「是」才动。 */
        val extraCombos: Set<String>,
        /** 这一问从哪来（决定回执落在哪一格提示位）。 */
        val slot: String,
    )

    private val pending = PendingAsk<Payload>()

    /** 有没有人在等回答。 */
    val asking: Boolean get() = pending.asking

    /** 这一批最终是**停用**还是**启用**（`null` = 没有待确认的事）。 */
    val kind: Kind? get() = pending.pending?.kind

    /** 用户点名的键（答「否」也照改）。 */
    val keys: Set<String> get() = pending.pending?.keys.orEmpty()

    /** 用户点名的组合（答「否」也照改）。 */
    val combos: Set<String> get() = pending.pending?.combos.orEmpty()

    /** 连带一起改的键（组合的成员键）—— 只有答「是」才动。 */
    val extraKeys: Set<String> get() = pending.pending?.extraKeys.orEmpty()

    /** 连带一起改的组合 —— 只有答「是」才动。 */
    val extraCombos: Set<String> get() = pending.pending?.extraCombos.orEmpty()

    /** 这一问从哪来（决定回执落在哪一格提示位）。 */
    val slot: String get() = pending.pending?.slot ?: "keys_toggle"

    fun ask(
        kind: Kind,
        keys: Set<String>,
        combos: Set<String> = emptySet(),
        extraKeys: Set<String> = emptySet(),
        extraCombos: Set<String> = emptySet(),
        slot: String = "keys_toggle",
    ) {
        pending.set(Payload(kind, keys, combos, extraKeys, extraCombos, slot))
    }

    fun clear() = pending.clear()
}

/**
 * 「按键功能」页的**编辑态** —— 顶栏那颗「编辑 / 完成」、两张清单里的多选框、右下角那颗「删除」
 * 共用这一份。
 *
 *  与 [KeyWatchState] / [ComboEditState] 同一套：**界面状态，不落盘**。选中集合存的是 id
 *  而不是对象 —— 每写一次盘配置都会换一份新对象，存下来的那份会过期。
 *
 *  ## 为什么分两格装
 *
 *  这一页上能删的东西有两类：收集来的**按键**（`Config.customKeys` 的 id）与**组合按键**
 *  （`ComboKey.id`）。两套 id 空间互不相干，混在一格里就得靠前缀区分、判据也会长出分支 ——
 *  分两格之后「删按键」与「删组合」各自只读自己那一格，删除时再一起算总数。
 *
 *  为什么放在页面级（`object`）而不是 `remember` 在页面里：那颗「编辑 / 完成」长在**顶栏**那棵树
 *  上（`PageTopBar` 的 `PageActions`），与列表分处两个组合；只有页面级这一份能把两边接起来。
 *  返回拦截的判据（`SettingsPage.needsBackInterception` / `goBack`）读的也是它。
 */
internal object KeysEditState {
    /** 现在是不是编辑态。 */
    var editing by mutableStateOf(false)

    /** 勾中的**按键** id（只有编辑态下才有意义；退出即清空）。 */
    var pickedKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 勾中的**组合按键** id。 */
    var pickedCombos by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 一共勾了几项（两类相加）—— 那颗「删除」与确认窗都只看它。 */
    val count: Int get() = pickedKeys.size + pickedCombos.size

    /** 点一颗键那一行：加进选中 / 从选中里去掉。 */
    fun toggleKey(id: String) {
        pickedKeys = if (id in pickedKeys) pickedKeys - id else pickedKeys + id
    }

    /** 点一组组合那一行（同上）。 */
    fun toggleCombo(id: String) {
        pickedCombos = if (id in pickedCombos) pickedCombos - id else pickedCombos + id
    }

    /**
     * 顶栏那颗「全选」：把两张清单里能勾的**一次勾满**，已经勾满时再按一下**收回**。
     *
     *  判据是「现在是不是已经全勾上了」（拿两份清单现算），不是拿缓存下来的数量比 ——
     *  清单的容量随时在变（加了一颗键 / 删了一档组合，「全选」的含义也跟着变）。
     *
     *  内置侧键也在 [allKeys] 里（它能勾、只是删不掉，见 `removePicked`）：全选之后按删除会
     *  被剔除并说明，与用户自己一颗颗勾过去是同一个结果。
     */
    fun toggleAll(
        allKeys: Set<String>,
        allCombos: Set<String>,
    ) {
        val all = pickedKeys == allKeys && pickedCombos == allCombos
        pickedKeys = if (all) emptySet() else allKeys
        pickedCombos = if (all) emptySet() else allCombos
    }

    /** 顶栏那颗按钮：编辑 ⇄ 完成。退出编辑时选中一起清掉。 */
    fun toggleEditing() {
        editing = !editing
        if (!editing) clearPicked()
    }

    /** 只清选中，不动编辑态（落盘之后退出编辑那一趟用）。 */
    fun clearPicked() {
        pickedKeys = emptySet()
        pickedCombos = emptySet()
    }

    /** 离开这一页时清掉，下次进来是干净的一页。 */
    fun clear() {
        editing = false
        clearPicked()
    }
}
