package com.lookie.opluskey.ui.model

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.nav.runtime.NavProgrammaticEasing
import io.github.suqi8.coui.kmp.nav.transition.NavMotion
import io.github.suqi8.coui.kmp.nav.transition.NavSettleSpec
import io.github.suqi8.coui.kmp.nav.transition.NavTransition
import io.github.suqi8.coui.kmp.nav.transition.NavTransitions

// 时长 / 缓动 / 转场 / 显隐动画。 —— 从 PageConst.kt 拆出（同包搬迁，调用点不变）。
//
//  **缓动一律取 COUI 的**（`io.github.suqi8.coui.kmp.anim`）：那几条是 ColorOS 自己的动效
//  标定，与系统控件（卡片 / 弹窗 / 菜单）的加减速一致。原先这里用的是 Material 的
//  `LinearOutSlowInEasing` + 两条手写 `CubicBezierEasing`，与 COUI 控件摆在一起时，
//  同一次点击触发的两段动画节奏对不上。
//
//  分档只有两条，别再加第三档（加了就又出现「同一个页面上两种节奏」）：
//   · **进 / 涨**（淡入、展开、淡入式位移）→ [DecelerateEasing]（t² 的镜像，起步快、收尾缓）；
//   · **出 / 缩**（淡出、收缩、离场位移）→ [AccelerateEasing]（t²，起步缓、收尾快）。

/** 全局统一颜色过渡曲线与时长（200ms 柔和减速，供全仓状态颜色切换）。 */
internal val COLOR_TRANSITION_SPEC =
    tween<Color>(
        durationMillis = 200,
        easing = DecelerateEasing(),
    )

/** 进场减速曲线 —— COUI 的减速档（初段轻快响应、末段平滑收敛）。 */
internal val EASING_EMPHASIZED_DECEL = DecelerateEasing()

/** 出场与收缩曲线 —— COUI 的加速档（起步即时响应、全程平顺收拢）。 */
internal val EASING_EMPHASIZED_ACCEL = AccelerateEasing()

/** 动态显隐控件进场时长（展开）。 */
internal const val VISIBILITY_ENTER_MS = 260

/** 动态显隐控件出场时长（收起）。 */
internal const val VISIBILITY_EXIT_MS = 200

/** 贴边控件进场时那一下淡入 / 出场时那一下淡出的时长。 */
private const val VISIBILITY_FADE_IN_MS = 180

private const val VISIBILITY_FADE_OUT_MS = 140

/**
 * 命令页「单条 / 多条」两栏切换的时长。
 *
 *  与上面那两档分开：Tab 翻页是**整块内容的淡入淡出 + 分页器尺寸动画**，不是「控件从有到无的
 *  展开 / 收缩」。共用一个数会让「调显隐时长」顺手改掉翻页观感，两件事本来各量各的。
 */
internal const val TAB_PAGE_SWITCH_MS = 260

/** 动态显隐统一进场：淡入 + 纵向柔和展开（对齐顶边缘向下推开）。 */
internal val VISIBILITY_ENTER: EnterTransition =
    fadeIn(tween(180, easing = DecelerateEasing())) +
        expandVertically(
            animationSpec = tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL),
            expandFrom = Alignment.Top,
        )

/** 统一出场：先快淡出（避免文字被截断挤压）+ 纵向利落收缩。 */
internal val VISIBILITY_EXIT: ExitTransition =
    fadeOut(tween(120, easing = AccelerateEasing())) +
        shrinkVertically(
            animationSpec = tween(VISIBILITY_EXIT_MS, easing = EASING_EMPHASIZED_ACCEL),
            shrinkTowards = Alignment.Top,
        )

/**
 * 「贴边控件」的进场 / 出场 —— 从自身高度之外（再加 [extraPx] 那一档留白）滑上来 / 滑下去。
 *
 *  右下角那颗 FAB 与底部那条工具栏共用这一份：两者的差别只在 [extraPx]
 *  （FAB 多算一档底边距、工具栏要滑过它浮在底边上方的那段距离），时长与缓动必须一致 ——
 *  同页两个贴边控件一前一后地动，看着像卡了一下。
 *
 *  @param extraPx 自身高度之外还要多滑的距离（像素），由调用点按自己的贴边留白算
 */
internal fun edgeSlideIn(extraPx: Int): EnterTransition =
    slideInVertically(
        animationSpec = tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL),
    ) { it + extraPx } + fadeIn(tween(VISIBILITY_FADE_IN_MS, easing = DecelerateEasing()))

internal fun edgeSlideOut(extraPx: Int): ExitTransition =
    slideOutVertically(
        animationSpec = tween(VISIBILITY_EXIT_MS, easing = EASING_EMPHASIZED_ACCEL),
    ) { it + extraPx } + fadeOut(tween(VISIBILITY_FADE_OUT_MS, easing = AccelerateEasing()))

/**
 * 多条命令列表步骤专属进场动效：
 * 展开高度 + 顶部轻微位移浮入 + 淡入，带来极其自然的“新步骤插入槽位”物理实感。
 */
internal val STEP_ROW_ENTER: EnterTransition =
    fadeIn(tween(200, easing = DecelerateEasing())) +
        expandVertically(
            animationSpec = tween(280, easing = EASING_EMPHASIZED_DECEL),
            expandFrom = Alignment.Top,
        ) +
        slideInVertically(
            animationSpec = tween(280, easing = EASING_EMPHASIZED_DECEL),
        ) { -it / 3 }

/**
 * 步骤行退场**最长那一条**的时长（高度收缩 / 位移动画）—— 谁要「等退场播完」就等这一个数。
 *
 *  以前「重置」的收尾协程等的是 `TAB_PAGE_SWITCH_MS + 60`（320ms），那是**换 tab** 的时长，
 *  与这里没关系：多等的那 100ms 里步骤行早已收完，表单却还杵着不动。
 */
internal const val STEP_ROW_EXIT_MS = 220

/**
 * 多条命令列表步骤专属退场动效：
 * 文字先隐去 + 轻微位移离场 + 高度平滑收缩，使得后续项与底部添加行丝滑吸附上移。
 */
internal val STEP_ROW_EXIT: ExitTransition =
    fadeOut(tween(120, easing = AccelerateEasing())) +
        shrinkVertically(
            animationSpec = tween(STEP_ROW_EXIT_MS, easing = EASING_EMPHASIZED_ACCEL),
            shrinkTowards = Alignment.Top,
        ) +
        slideOutVertically(
            animationSpec = tween(STEP_ROW_EXIT_MS, easing = EASING_EMPHASIZED_ACCEL),
        ) { -it / 4 }

/**
 * 「支持的命令格式」卡里的一行。
 *
 * @param text 整行文案
 * @param prefix 行尾那颗小 `+` 往「命令」输入框里填的前缀（`shell:` / `keyevent:` …）；
 *   `null` = 这一行没有可填的前缀，行尾不画 `+`。
 */
internal data class CommandFormatLine(
    val text: String,
    val prefix: String? = null,
)

/**
 * 整页转场（push / pop）的时长 —— 350ms（真机录屏逐帧拟合定下来的，不跟库默认的 500ms 走）。
 */
private const val PAGE_TRANSITION_MS = 350

/**
 * 手势提交 / 取消那两档弹簧的刚度 —— 293f（按收敛包络反推：350ms ⇒ k = (5.9915/0.35)² ≈ 293）。
 *
 * 带速度的 settle 不吃 `programmatic` 曲线、退回弹簧，刚度过低会让「点返回 350ms、手势松手 ~500ms」；阻尼仍是临界。
 */
internal const val PAGE_SETTLE_STIFFNESS = 293f

/**
 * 整页转场 —— 几何直接用库的 [NavTransitions.COUIDefault]，只把 settle 换成真机核对过的规格。
 *
 * `COUIDefault` 的 `relativeDepth -> translationX` 是「d ≤ 0（顶层之上）整幅 -d·width，d > 0（被盖页）视差 -d·width/4」，
 * 并把 d 钳在 `[-1, 0]`、位移吸附整像素。settle 不用库默认的 500ms，改用 350ms + 库自己那条
 * programmatic 曲线 [NavProgrammaticEasing]（`response = 0.8 / damping = 0.95` 的弹簧阶跃烘焙成的
 * Easing，miuix 导航同一条，库把它公开出来就是为了给自定义 `NavSettleSpec.Tween` 复用）；
 * 手势的两档见 [PAGE_SETTLE_STIFFNESS]。
 *
 * 这里原先是手写的 `CubicBezierEasing(0.2f, 0f, 0f, 1f)`：形状与库那条同族（前段快、尾巴长），
 * 但具体落点不同 —— 统一到库那条之后，页面推拉与库自己的导航（菜单 / 弹窗的进出）走同一条曲线。
 */
internal val PAGE_TRANSITION: NavTransition by lazy {
    object : NavTransition by NavTransitions.COUIDefault {
        override val motion: NavMotion =
            NavMotion(
                programmatic =
                    NavSettleSpec.Tween(
                        durationMillis = PAGE_TRANSITION_MS,
                        easing = NavProgrammaticEasing,
                    ),
                //  手势提交 / 取消必须是弹簧：带速度的 settle 不吃 programmatic 曲线（Tween 带不上速度，库退回默认弹簧 ≈500ms）；
                // 刚度抬到 [PAGE_SETTLE_STIFFNESS]，让点返回与手势松手都收敛在 ~350ms。
                commit = NavSettleSpec.Spring(stiffness = PAGE_SETTLE_STIFFNESS),
                cancel = NavSettleSpec.Spring(stiffness = PAGE_SETTLE_STIFFNESS),
            )
    }
}
