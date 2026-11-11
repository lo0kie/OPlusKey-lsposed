package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.SceneRule

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

    /** 把第 [from] 块挪到 [to] —— 顺序就是判定顺序，所以这就是「上移 / 下移」。 */
    fun move(
        from: Int,
        to: Int,
    ) {
        if (from !in scenes.indices || to !in scenes.indices || from == to) return
        scenes.add(to, scenes.removeAt(from))
        if (from in ids.indices) {
            val id = ids.removeAt(from)
            if (to <= ids.size) ids.add(to, id)
        }
        //  槽位跟着行走：挪块改的是顺序，不是「这一块在配置里换了位置」
        if (from in slots.indices) {
            val slot = slots.removeAt(from)
            if (to <= slots.size) slots.add(to, slot)
        }
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
