package com.lookie.opluskey.ui.state.binding

import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.SceneRule

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
