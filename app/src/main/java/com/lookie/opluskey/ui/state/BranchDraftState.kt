package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.StateRule

/**
 * 「条件执行」页的草稿 —— 当前这颗键、这一档手势的条件表。
 *
 * ## 为什么不再是「一条命令」
 *
 * 分叉不占用绑定：它是叠在按键绑定之上的一张**顺序表**，命中第一条就执行它的动作，
 * 一条都不中就走按键绑定里那一档的命令（见 `KeyConfig.actionFor`）。所以这一页编的是
 * `KeyConfig.rules`，不是命令库里的一条记录 —— 没有名字、不进「已保存的命令」。
 *
 * ## 与 [CommandDraft] 同一条规矩
 *
 * 草稿提到页面级：从这一页 push 到「选择应用」挑完再退回来，已经加好的条件还在。
 * [dirty] 与载入时那份快照比，退出时才问「放弃未保存的」。
 */
internal class BranchDraft {
    val rules = mutableStateListOf<StateRule>()

    /**
     * 与 [rules] 一一对应的**行身份** —— 只服务界面上的进出场动画，不落盘、不参与保存。
     *
     *  为什么草稿要记这个：`AnimatedVisibility` 的状态是按**组合位置**记身份的，而删掉中间一行会让
     *  后面的行整体前移 —— 前移的那一行会继承被删那一行的「已退场」状态，当场再播一遍入场、下方
     *  还会跳一下。有了 id，每行的动画状态跟着自己走，删谁都只动那一行。
     */
    val ids = mutableStateListOf<Int>()

    /** 下一个要发的行身份 —— 只增不减（重载 / 重置时归零）。 */
    private var nextId = 0

    /**
     * 刚加的那一行的身份（[ids] 里的值；[NO_FRESH_ROW] = 没有）—— 只有它播入场。
     *
     *  住在页面级、不住 `BranchCards` 的 `remember`：加一条的入口已经挪到**页外**那颗 FAB 上
     *  （右下角「添加」），它写不到页内的局部状态。
     */
    var freshId by mutableStateOf(NO_FRESH_ROW)

    /**
     * 草稿属于哪颗键、哪一档 —— 换了就重新载入（见 [load]）。
     *
     *  **必须是组合状态**：`BranchCards` 拿它当「草稿载入没有」的闸门（载入前整块不画，
     *  免得空态 → 表格那一下播成进场动画）。普通 `var` 改了不触发重组，那一块会一直不画。
     */
    var owner: String? by mutableStateOf(null)

    /** [load] 进来的那份快照 —— 只服务 [dirty]。 */
    private var loaded: List<StateRule> = emptyList()

    val hasContent: Boolean get() = rules.isNotEmpty()

    /** 有没有改过还没保存的东西。 */
    val dirty: Boolean get() = rules.toList() != loaded

    /**
     * 载入某颗键某一档的条件表。
     *
     * [owner] 是「键 + 手势」的标识：同一份草稿在页面里只建一次，换键 / 换手势时靠它
     * 判「要不要重灌」—— 否则从另一档进来会看到上一档的规则。
     *
     * 行身份一起重发：换了一档就是新的一批行，动画没必要接着上一档的账算。
     */
    fun load(
        owner: String,
        source: List<StateRule>,
    ) {
        this.owner = owner
        rules.clear()
        ids.clear()
        nextId = 0
        source.forEach {
            rules.add(it)
            ids.add(nextId++)
        }
        loaded = source
    }

    fun reset() {
        rules.clear()
        ids.clear()
        nextId = 0
        loaded = emptyList()
        owner = null
    }

    /**
     * 追加一条，返回它的行身份。
     *
     *  界面拿这个 id 认出「刚加的是哪一行」，只给它播入场 —— 其余行是载入时就有的，不该再展开一次。
     */
    fun add(rule: StateRule): Int {
        rules.add(rule)
        ids.add(nextId)
        return nextId++
    }

    fun update(
        index: Int,
        rule: StateRule,
    ) {
        // 行身份不动：换条件 / 换动作是同一条规则改内容，不是一行换成了另一行（否则每选一次都会闪）
        if (index in rules.indices) rules[index] = rule
    }

    fun remove(index: Int) {
        if (index !in rules.indices) return
        rules.removeAt(index)
        if (index in ids.indices) ids.removeAt(index)
    }

    /** 把第 [from] 条挪到 [to] —— 顺序就是判定顺序，所以这就是「上移 / 下移」。 */
    fun move(
        from: Int,
        to: Int,
    ) {
        if (from !in rules.indices || to !in rules.indices || from == to) return
        rules.add(to, rules.removeAt(from))
        if (from in ids.indices) {
            val id = ids.removeAt(from)
            if (to <= ids.size) ids.add(to, id)
        }
    }

    companion object {
        /** 键 + 手势的标识 —— 只用来判「草稿还是不是这一档的」。 */
        fun ownerOf(
            key: KeyId,
            gesture: Gesture,
        ): String = "${key.id}/${gesture.id}"
    }
}

/** 页面级那一份（见 `SettingsPage.branchDraft`）。 */
internal class BranchDraftState {
    val draft = BranchDraft()
}

/** 「没有刚加的那一行」的哨兵值（行身份从 0 起，撞不上）。 */
internal const val NO_FRESH_ROW = -1
