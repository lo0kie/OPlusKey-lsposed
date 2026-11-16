package com.lookie.opluskey.ui.model.theme

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.GlobalConfig
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.nav.runtime.NavProgrammaticEasing
import io.github.suqi8.coui.kmp.nav.transition.NavMotion
import io.github.suqi8.coui.kmp.nav.transition.NavSettleSpec
import io.github.suqi8.coui.kmp.nav.transition.NavTransition
import io.github.suqi8.coui.kmp.nav.transition.NavTransitions
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme

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

/** 状态色 / 状态字形那一条过渡的时长 —— [COLOR_TRANSITION_SPEC] 与 [COLOR_FADE_SPEC] 共用这一个数。 */
private const val COLOR_TRANSITION_MS = 200

/** 全局统一颜色过渡曲线与时长（200ms 柔和减速，供全仓状态颜色切换）。 */
internal val COLOR_TRANSITION_SPEC =
    tween<Color>(
        durationMillis = COLOR_TRANSITION_MS,
        easing = DecelerateEasing(),
    )

/**
 * 同一条曲线的**浮点版** —— 字形 / 图标那种「不是颜色、又要和颜色同时到位」的切换用它。
 *
 *  典型用法是 `Crossfade(animationSpec = COLOR_FADE_SPEC)`：形状没法插值，只能换字形，
 *  而换字形那一淡必须与旁边那片底色同一条曲线、同一个时长，否则同一次点击看着是「先亮后换」。
 *  两档共用 [COLOR_TRANSITION_MS]，别再各自写一份 `tween<Float>(200, …)`。
 */
internal val COLOR_FADE_SPEC =
    tween<Float>(
        durationMillis = COLOR_TRANSITION_MS,
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

private const val VISIBILITY_FADE_OUT_MS = 120

/**
 * 命令页「单条 / 多条」两栏切换的时长。
 *
 *  与上面那两档分开：Tab 翻页是**整块内容的淡入淡出 + 分页器尺寸动画**，不是「控件从有到无的
 *  展开 / 收缩」。共用一个数会让「调显隐时长」顺手改掉翻页观感，两件事本来各量各的。
 */
internal const val TAB_PAGE_SWITCH_MS = 260

/** 动态显隐统一进场：淡入 + 纵向柔和展开（对齐顶边缘向下推开）。 */
internal val VISIBILITY_ENTER: EnterTransition =
    fadeIn(tween(VISIBILITY_FADE_IN_MS, easing = DecelerateEasing())) +
        expandVertically(
            animationSpec = tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL),
            expandFrom = Alignment.Top,
        )

/** 统一出场：先快淡出（避免文字被截断挤压）+ 纵向利落收缩。 */
internal val VISIBILITY_EXIT: ExitTransition =
    fadeOut(tween(VISIBILITY_FADE_OUT_MS, easing = AccelerateEasing())) +
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
    fadeOut(tween(VISIBILITY_FADE_OUT_MS, easing = AccelerateEasing())) +
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
 *  文案拆成 [label] / [desc] 两段（不是一整串），因为卡里排的是**两列**：左边前缀一列等宽对齐，
 *  右边说明一列从同一个 x 起。拼成一串再加分隔符的话，各行的说明会随前缀长短各自缩进，
 *  看着就是一路斜下去。
 *
 * @param icon 行首那颗图标 —— 六行六颗、互不相同（见 `PageIcons.Format*` 那一组）：
 *   这一列是「能写什么」的图例，光靠一列等宽的字扫下来分不出哪行是哪行
 * @param label 左列：能写的写法本身（`shell:<命令>` / `keyevent:<按键>` …）
 * @param desc 右列：这一句说明它干什么、该填什么（`执行 shell 命令`、`模拟按键（keycode 数字）`…）。
 *   括号里的补充（「数字」「广播 action 名」）原先是输入框底下那句格式提示的内容，现已并进这一列，
 *   卡片一行就说清「后面该跟什么」，不再需要单独一句提示。
 * @param prefix 点这一行时往「命令」输入框里填的前缀（`shell:` / `keyevent:` …）；
 *   `null` = 这一行没有可填的前缀，整行不可点。
 */
internal data class CommandFormatLine(
    val icon: ImageVector,
    val label: String,
    val desc: String,
    val prefix: String? = null,
)

/**
 * 整页转场（push / pop）的时长 —— 350ms（真机录屏逐帧拟合定下来的，不跟库默认的 500ms 走）。
 */
internal const val PAGE_TRANSITION_MS = 350

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

/**
 * 当前这套配色是不是暗色。
 *
 *  判据取 `background` 的亮度，不读系统设置：配色档可以被强制成浅色 / 深色 / Monet，
 *  系统那一位和实际画出来的颜色不一定一致。
 *
 *  **只有这一处判据**：菜单、抽屉、应用格子里那些「暗色要换一档色」的分支都读它 ——
 *  各自写一遍 `luminance() < 0.5f` 时，改动阈值就得把每个调用点翻一遍。
 */
internal val Colors.isDarkScheme: Boolean
    get() = background.luminance() < 0.5f

// 与宿主打交道的键名与节拍。 —— 从 PageConst.kt 拆出（同包搬迁，调用点不变）。

/** 只用来注册监听，不读它的值 —— 见 [SyncToHost]。 */
internal const val NIGHT_MODE_KEY = "ui_night_mode"

/**
 * 深浅兜底轮询的间隔 —— 300ms，只管「设置项不变」那种定时切换的情况，[SyncToHost] 的推送走另一条。
 */
internal const val NIGHT_POLL_MS = 300L

/**
 * [SyncToHost] 推送醒来之后连查系统配置的等待序列（毫秒，第一项 0 = 立刻查一次）——
 * 给三次补救机会，查到就 break。
 */
internal val NIGHT_RETRY_MS = longArrayOf(0L, 60L, 90L, 150L)

/**
 * 首页问一句「宿主侧的模块在不在」之后，等回音的时长（毫秒）。
 *
 * 没有回音就按「模块没加载」报（首页那条常驻提示会说明原因；**页面不禁用** —— 一、13）。
 * 给得偏宽，是因为这一步可能要等**宿主进程
 * 被这条广播拉起来**：进程启动、LSPosed 注入、模块注册接收器，都在这段时间里。
 */
internal const val HOST_ALIVE_TIMEOUT_MS = 3000L

/**
 * 首页最多问几轮「两端在不在」。
 *
 * 一轮只够发现「早就加载好了」那一档；注入侧借不到 Context、或它的宿主 uid 还没解析出来时，
 * 它会 fail-closed 把那句问句丢掉（见 `KeyHooks.isFromHost`），而这**会自愈** —— 只是不是立刻。
 * 所以要多问几轮，免得用户看着一个灰页面无从下手。
 *
 *  两端都答上就提前收工（正常机器上仍然只问一轮）；一直问不齐也会停：
 * 一直问不到就该停：无限轮询只是白耗电。
 */
internal const val QUERY_ROUNDS = 3

/**
 * 界面颜色的三档 —— 与 `GlobalConfig.theme` 的取值一一对应（那边只存字符串）。
 *
 *  顺序就是菜单里的顺序，也是「从松到紧」的顺序：跟随系统 → 跟随系统(Monet) → 固定浅 → 固定深。
 */
internal enum class AppTheme(
    val code: String,
) {
    AUTO(GlobalConfig.DEFAULT_THEME),

    /** 跟随系统，且整页用系统那套 Monet 配色（见 `PagePalette.colorsOf`）。 */
    MONET(GlobalConfig.THEME_MONET),
    LIGHT(GlobalConfig.THEME_LIGHT),
    DARK(GlobalConfig.THEME_DARK),
    ;

    companion object {
        /**
         * 按 code 找档位；认不出一律落回「跟随系统」。
         *
         *  与 `PagePalette.ofId` 同一个理由：code 存在配置文件里，手改过、从别的版本导回来都可能
         *  带进不认识的档 —— 那时最合理的结果是回到系统给的那一档，而不是整页读不出主题。
         */
        fun ofCode(code: String?): AppTheme = entries.firstOrNull { it.code == code } ?: AUTO
    }
}

// 页面上的颜色与透明度：磨砂 / 浮层 / 菜单分隔 / 主题色板。 —— 从 PageConst.kt 拆出（同包搬迁，调用点不变）。

/**
 * 磨砂面上给不透明底色降 alpha 用的那一档（顶栏 + 底部工具条）。
 *
 *  注意：右下角那颗 FAB **不参与这一档** —— 它把底色调成**主题原色**（`colorScheme.primary`），
 *  乘这一档会掺进底下的糊里、显得发白，与主题色对不上。别再照着顶栏 / 工具条给它乘回去。
 *
 *  一律**按比例缩**（`color.alpha * 这一档`），不要 `copy(alpha = …)` 整个换 —— 深浅两套色板里
 *  同名色可能一个是实色、一个是半透（库深色板的 `surfaceContainer` 就是半透白），替换会把后者顶成亮色。
 */
internal const val FROSTED_SURFACE_ALPHA = 0.67f

/**
 * 浮层面板的本色 —— 浅色照搬库 `ListPopupContent` 那一对常量（COUI 的 `couiColorSurfaceTop`：
 * `coui_color_surface_top` 浅色 `#FFFFFF` / 夜间 `#333333`，库内是 private，只能照抄一份）；
 * 夜间那一档见 [POPUP_SURFACE_DARK]（比库原生的亮，与 [POPUP_FROSTED_ALPHA] 配套）。
 *
 *  为什么不能拿主题里那几档 `surface*` 顶替：面板要**比页面亮一档**——暗色下页面是纯黑
 *（实测 `#000000`）、卡片是 `#1a1a1a`，而 `surfaceContainerHighest` 只有 `#252525`，
 *  拿它当磨砂底色会比库原生面板还暗（真机反馈「暗色下底色太暗」）。
 */
internal val POPUP_SURFACE_LIGHT = Color.White

/**
 * [POPUP_SURFACE_LIGHT] 的夜间那一档 —— `#333333` 是库原生的值，这里取到 `#3d3d3d`、
 *  **亮一档**：面板底色降到半透（见 [POPUP_FROSTED_ALPHA]）之后，底下的糊会透过一部分，
 *  本色不抬这一档，落定后的面板就比库原生面板暗（真机反馈过「暗色下底色太暗」）。
 *  两笔账合起来是把**落定亮度**钉在库原生那一档，透上来的糊才有得看。
 */
internal val POPUP_SURFACE_DARK = Color(0xFF3D3D3D)

/**
 * 浮层磨砂底色的 alpha（**只给浮层菜单用**）—— 这一档就是「底下的糊能透上来多少」：
 *  越小糊越明显。它是**和 [POPUP_SURFACE_DARK] 一起调的**：糊的是底下的页面，暗色下页面接近纯黑，
 *  透得越多面板越黑 —— 所以本色抬亮一档，把落定亮度钉在库原生面板（`#333333`）那一档上。
 *  0.72 是比顶栏（[FROSTED_SURFACE_ALPHA]）略保一点、又明显看得见糊的位置；顶栏可以更低，
 *  是因为它背后恒是页面自己的滚动内容，而浮层可能压在任意画面上。
 *
 *  消费方只有浮层菜单（`ui/dialog/MenuPopup.kt` 里 `MenuPopup` 那层）。确认弹窗的面板走**另一档**
 *  [DIALOG_FROSTED_ALPHA]：同一门账，只是它面板大、底下更平，要更透才看得见糊。
 */
internal const val POPUP_FROSTED_ALPHA = 0.72f

/**
 * 确认弹窗面板底色的 alpha —— 比浮层菜单那一档（[POPUP_FROSTED_ALPHA]）**更透**。
 *
 *  这一档就是「底下的糊能透上来多少」：0.72 时糊只占 28%，而弹窗面板比菜单大得多、底下的页面又平
 *（一张卡片加一片空白），同一档看下来只剩一层底色、看不出背后糊过（真机反馈「生效了但不太明显」）。
 *  0.6 把糊的占比提到 40%，面板底下是糊过的内容一眼能认出来；再低正文压在花底上就发虚，到头了。
 *
 *  底色本色与浮层菜单共用（[popupSurfaceColor]），只有这一档分家。
 */
internal const val DIALOG_FROSTED_ALPHA = 0.6f

/**
 * 「按键绑定」主开关为关时分段控件的不透明度 —— 0.4。
 *
 * 只有分段控件需要这一手：它没有 `enabled` 参数；网格格子 / 长按卡都走 COUI 自己的
 * `enabled = false` 通道（用 `disabledColor`，不是把颜色整体调淡）。
 */
internal const val DISABLED_ALPHA = 0.4f

/**
 * 顶栏那行小标题在「这一颗键停用」时压暗的倍率 —— 0.5，也就是把它的 alpha 砍一半
 *（`onSurfaceVariantSummary` 本色是 `0x66000000`，压完约等于 0.2）。
 *
 *  **只能乘、不能直接给一个 alpha**：那个 token 自带 0.4 的 alpha，`copy(alpha = 0.5f)` 会把它
 *  **抬高**（第一版就是这么写的，真机上完全看不出变化）。改倍率，别改绝对值。
 */
internal const val SUBTITLE_DIM_FACTOR = 0.5f

/**
 * 「加深带」那一档的 alpha（`ui/style/Dividers.kt` 的 `AppHeavyDivider` 读它）。
 *
 *  **组间那条槽**暗色要压得更重（`#333` 面板上压到 `#1f` 才看得出是一道槽）。
 *  细线那两条（0.14 / 0.10）随「所有分隔线统一出口」一起删掉了 —— 细线现在只认
 *  `AppDivider` 里那一档颜色。
 */
internal const val MENU_GROUP_LINE_DARK_ALPHA = 0.32f

internal const val MENU_GROUP_LINE_LIGHT_ALPHA = 0.08f

internal const val APP_KIND_TAG_BG_ALPHA = 0.10f

/**
 * 应用种类标签里「模块」那一档的颜色 —— 紫，两套主题各一值。
 *
 *  为什么不取库的色板：COUI 那套只有蓝（`primary`）与红（`error`）两个色相 —— 蓝给了「系统」、
 *  红是「不可打开」的警告档，第三个色相只能自己定一份。判据同 `popupSurfaceColor`（按 `background`
 *  亮度切），两档都取中等亮度：标签底是它自己的 10% 透明版，太深太浅都压不住。
 */
internal val APP_KIND_MODULE_LIGHT = Color(0xFF7C4DFF)

internal val APP_KIND_MODULE_DARK = Color(0xFFB9A3FF)

/**
 * 应用名旁边那枚「分身」标签的颜色 —— 明暗各一值。
 *
 *  **只有一档**：槽位已经写在名字里了（`微信 1` / `微信 2`），标签只负责说「这一行是分身」，
 *  不需要再用五种颜色去区分第几个 —— 那会让同一屏里出现五六种标签色，反而更难读。
 *
 *  色相避开库里的两支与模块紫：蓝（`primary`）是「系统」、红（`error`）是「不可打开」、
 *  紫是「模块」。判据与 [APP_KIND_MODULE_LIGHT] 同一套（按 `background` 亮度切）。
 */
internal val CLONE_TAG_LIGHT = Color(0xFF00897B)

internal val CLONE_TAG_DARK = Color(0xFF4DD0B1)

internal val LIGHT_SCHEME = lightColorScheme()

internal val DARK_SCHEME = darkColorScheme()

/**
 * 栈顶之下那一页的压暗程度 —— 取 0.25（黑的 alpha）。
 *
 * 库在被盖页底下垫一层全屏黑，alpha = [PAGE_COVER_DIM] × 该页相对深度，静止时恒定压暗、推入渐深、弹出渐亮；
 * 再小压不住，再大暗得发灰。
 */
internal const val PAGE_COVER_DIM = 0.25f
