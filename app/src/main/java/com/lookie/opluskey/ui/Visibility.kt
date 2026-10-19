package com.lookie.opluskey.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing

// 显隐动画包一层：进场 / 退场各自给一套动画，调用点不重复写 `AnimatedVisibility`。

/**
 * **动态显隐的一段控件** —— 统一动画的唯一入口（规格见 [VISIBILITY_ENTER]）。
 *
 * 为什么要有这个包装：`AnimatedVisibility` 的 `enter` / `exit` 每个调用点各写一遍
 * 容易写岔（时长、方向、少写一半），而「所有动态显隐的控件动画一致」正是这件事的目的。
 * 用法：把 `if (条件) { 控件… }` 直接换成 `AnimatedColumn(visible = 条件) { 控件… }`
 * —— 条件里那个 `if` 没了，**里面的缩进不用动**（多出来的那层 `Column` 在本函数里，
 * 见下）。
 *
 *  注意：里面那个 `Column` **不能省**：`AnimatedVisibility` 的内容槽位不是 `Column`，
 * 多个子项（本项目里常见「一档 `Spacer` + 小标题 + 一张卡」这种组合）会**叠在同一处**。
 * 所以这里固定套一层 `Column` —— 这也正是调用点不必再动缩进的原因。
 *
 * @param visible 显不显示（也就是那个 `if` 的条件）
 */
@Composable
internal fun AnimatedColumn(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = VISIBILITY_ENTER,
        exit = VISIBILITY_EXIT,
    ) {
        Column(content = content)
    }
}

/**
 * 同 [AnimatedColumn]，但**只淡入淡出、不动高度** —— 给「位置本来就在文档流里、
 * 高度由别处定死」的东西用（格子里的那行小字、居中那圈加载提示）：
 * 给它们套展开 / 收起会把所在的行 / 卡片撑一下，反而是抖。
 */
@Composable
internal fun AnimatedFade(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        //  缓动与其余显隐同一条（COUI 的减速 / 加速两档，见 PageMotion 顶部那段）
        enter = fadeIn(tween(durationMillis = 220, easing = DecelerateEasing())),
        exit = fadeOut(tween(durationMillis = 160, easing = AccelerateEasing())),
    ) { content() }
}
