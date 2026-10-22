package com.lookie.opluskey.ui.state

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 步骤列表**展示层**的一行 —— `draft.steps` 里某个 token 的镜像，带一个稳定 id。
 *
 *  动画只活在展示层：数据（`draft.steps`）是立即改的，行是「播完退场再摘」。
 */
internal class StepRowItem(
    val id: Int,
    val token: String,
) {
    val state = MutableTransitionState(false).apply { targetState = true }

    /**
     * 退场中：target 已切 false，播完 shrink+fade 就从展示层摘除。
     *
     *  组合期会读它（`count { !it.exiting }`、几处 `enabled = !item.exiting`），所以必须是 state：
     *  普通 `var` 只在「每个写点都恰好与 `state.targetState` 同帧写」时看着能工作（靠那一次重组
     *  顺带把读点重读一遍），那是巧合、不是保证。
     */
    var exiting by mutableStateOf(false)

    /**
     * 退场行的插回锚。**只在 [StepRowDisplay.sync] / [StepRowDisplay.restoreAll] 里读写**，
     * 组合期不读 ⇒ 普通 `var` 就够，不必是 state。
     */
    var anchorId = -1
}

/**
 * 步骤行的展示层镜像 —— 增 / 删 / 换 / 移都在这里做**差分**。
 *
 * ## 为什么住在页面级状态，而不是 `SerialSteps()` 里的 `remember`
 *
 *  「自定义命令」页是两个 tab 共用一个分页器，切 tab 时**那一页的组合会被注销**：
 *  展示层跟着没了，切回来重新建一遍 ⇒ 整张表重播一次进场动画（真机反馈
 *  「切换 tab，步骤的列表动画又跑一遍」就是这个）。放到页面级之后，切回来时
 *  行还是原来那些、`AnimatedVisibility` 的 currentState 仍是 true，不会再播。
 *
 * ## 差分怎么算
 *
 *  未退场的展示行按展示顺序与 `steps` 做**多重集匹配**（同 token 按先后配对）：
 *  配上的保留、配不上的播退场（记下前面最近的保留行当锚，收缩期间插回它下面）、
 *  缺的建新条目播入场。
 */
internal class StepRowDisplay {
    val rows = mutableStateListOf<StepRowItem>()

    /** 下一个新行的 id。只在 [sync] 里读写（组合期不读）⇒ 普通 `var` 就够。 */
    private var nextId = 0

    /** 与 [steps] 对齐（见类注释里那段差分规则）。 */
    fun sync(steps: List<String>) {
        val kept = rows.filter { !it.exiting }
        val matched = arrayOfNulls<StepRowItem>(steps.size)
        val pool = kept.toMutableList()
        steps.forEachIndexed { si, token ->
            val i = pool.indexOfFirst { it.token == token }
            if (i >= 0) matched[si] = pool.removeAt(i)
        }
        // 没匹配上的 → 退场；锚 = 前面最近的保留行（收缩期间插回它下面）
        var prevId = -1
        kept.forEach { row ->
            if (matched.contains(row)) {
                prevId = row.id
            } else if (!row.exiting) {
                row.exiting = true
                row.anchorId = prevId
                row.state.targetState = false
            }
        }
        // 重建展示顺序：steps 顺序放匹配行（缺的建新行），其间插入挂锚的退场行
        val exitingByAnchor = rows.filter { it.exiting }.groupBy { it.anchorId }
        val newOrder = mutableListOf<StepRowItem>()
        newOrder += exitingByAnchor[-1].orEmpty().sortedBy { it.id }
        steps.forEachIndexed { si, token ->
            val m = matched[si]
            if (m == null) {
                newOrder += StepRowItem(nextId++, token).also(rows::add)
            } else {
                newOrder += m
            }
            newOrder += exitingByAnchor[m?.id].orEmpty().sortedBy { it.id }
        }
        rows.clear()
        rows.addAll(newOrder)
    }

    /**
     * 全部标成退场（整卡重置那一刻）。
     *
     *  注意：必须**连 `exiting` 一起置**：只置 `targetState` 的话行会淡出，
     *  但摘除那道闸（行里 `if (item.exiting && …)`）永远不成立 —— 而摘除 effect 的 key 是
     *  `item.state.currentState`，它在这里已经变成 false、之后再不会变 ⇒ 那些行**永留展示层**：
     *  分隔线一直画着、`cardListPositionOf(index, rows.size + 1)` 一直按旧行数算。
     */
    fun exitAll() {
        rows.forEach {
            it.exiting = true
            it.state.targetState = false
        }
    }

    /**
     * 把 [exitAll] 标掉的行**收回来**（重置收尾被放弃那一次）。
     *
     *  它们本来会被摘除，而 `draft.steps` 没变又不会重建 ⇒ 步骤在界面上凭空消失
     *  （数据还在，看不到也删不掉）。
     */
    fun restoreAll() {
        rows.forEach {
            if (it.exiting) {
                it.exiting = false
                it.anchorId = -1
                it.state.targetState = true
            }
        }
    }

    /** 页面出栈：清空 —— 下一趟进来从干净的一份开始（id 也从头数）。 */
    fun clear() {
        rows.clear()
        nextId = 0
    }
}
