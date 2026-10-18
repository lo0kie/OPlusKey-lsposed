package com.lookie.opluskey.bridge

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.MiniWindowGeometry
import com.lookie.opluskey.core.model.RomProfile

/**
 * 小窗指令的跨进程桥：把前台任务收成小窗这件事，除了应用侧那条路，还有一条要在 `system_server` 里做 ——
 * 只有那边拿得到 `FlexibleTaskController`，用它自己的 `switchState(task, 1, …)` 才是带原生动画的那条。
 *
 * 与 [StatusBarBridge] 同一套：宿主发一条限定包名的广播，注入侧在 AMS 广播入口接住、在本机执行。
 */
internal object FlexibleWindowBridge {
    /** 小窗指令的转交广播 —— 宿主发，注入侧接住并在本机执行。 */
    const val ACTION = "com.lookie.opluskey.FLEXIBLE"

    /** 指令：把指定任务切到小窗态。 */
    const val CMD_MINI = "mini"

    /**
     * 指令：**用框架自己的动画器**把任务从「全屏外观」缩到「小窗外观」。
     *
     * 与 [CMD_MINI] 的分工：`mini` 是**真的转换**（改窗口模式，一步到位、无动画）；
     * 本指令只动**画面**（对 surface 做 `setPosition/setMatrix/setAlpha`），不动窗口模式。
     * 所以两个要配合用，顺序由宿主侧排，且**只能是「先转换、后动画」**：
     * 动画器每帧的守卫要求任务已经是「带标题栏的柔性任务」，普通全屏任务**每帧都被挡回去**
     * （先动画后转换那条已证伪，见 `docs/zoom-window.md` §12.3）。
     */
    const val CMD_MINI_ANIM = "mini_anim"

    /** 上面那条广播里带指令名 / 任务号的 extra。 */
    const val EXTRA_CMD = "cmd"
    const val EXTRA_TASK_ID = "task_id"

    /**
     * 落点几何由**宿主侧下发**，不写死在注入侧。
     *
     * 理由是「改哪边就得重启谁」：注入侧跑在 `system_server` 里，LSPosed 只在**进程启动时**
     * 注入 dex，所以注入侧的代码改一行就要**整机重启**才加载；而宿主侧的改动只要 kill 掉
     * `com.oplus.gesture`，几秒钟就生效。落点几何是要反复试的参数，放宿主侧 = 一轮试错
     * 从「重启一次（几分钟）」降到「几秒钟」。
     *
     * 拆成 4 个 int + 1 个 float，而不是传一个 `Rect`：跨进程传 Parcelable 要管类加载器，
     * 广播这条路上多一事不如少一事。
     */
    const val EXTRA_LEFT = "left"
    const val EXTRA_TOP = "top"
    const val EXTRA_RIGHT = "right"
    const val EXTRA_BOTTOM = "bottom"
    const val EXTRA_SCALE = "scale"

    /**
     * [CMD_MINI_ANIM] 的起止状态，全部由宿主下发 —— 和几何同理，**改参数不用重启**。
     *
     * 只有 `left/top` 参与动画（框架 `updateAnimationLayer` 只读 rect 的 left/top，
     * 尺寸走 scale），但这里仍然四边都带：`FlexibleAnimationInfo` 的 rect 在别处也会被读到，
     * 缺一项就是给框架喂半个 Rect。alpha 用来试「收尾淡出盖住跳变」。
     */
    const val EXTRA_START_LEFT = "start_left"
    const val EXTRA_START_TOP = "start_top"
    const val EXTRA_START_RIGHT = "start_right"
    const val EXTRA_START_BOTTOM = "start_bottom"
    const val EXTRA_START_SCALE = "start_scale"
    const val EXTRA_START_ALPHA = "start_alpha"
    const val EXTRA_FINISH_LEFT = "finish_left"
    const val EXTRA_FINISH_TOP = "finish_top"
    const val EXTRA_FINISH_RIGHT = "finish_right"
    const val EXTRA_FINISH_BOTTOM = "finish_bottom"
    const val EXTRA_FINISH_SCALE = "finish_scale"
    const val EXTRA_FINISH_ALPHA = "finish_alpha"

    /** 动画时长（毫秒）。 */
    const val EXTRA_DURATION = "duration"

    /**
     * 宿主进程调：把一条小窗指令转给注入侧执行。
     *
     * 必须 `setPackage(宿主包名)`：指定包名的广播不算隐式广播，不会被「隐式广播限制」拦掉，
     * 也只有宿主进程里注册的接收器能收到。任何失败只记日志 —— 最坏是「这一下没反应」。
     *
     * @param bounds 应用配置矩形（**不是**屏幕矩形，语义见 [EXPANDED_BOUNDS]）；传 null 用注入侧兜底值
     * @param scale  显示缩放；传 null 用注入侧兜底值
     */
    fun send(
        context: Context,
        cmd: String,
        taskId: Int,
        bounds: android.graphics.Rect? = null,
        scale: Float? = null,
    ) {
        try {
            val intent =
                Intent(ACTION)
                    .setPackage(HostApp.PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(EXTRA_CMD, cmd)
                    .putExtra(EXTRA_TASK_ID, taskId)
            if (bounds != null) {
                intent.putExtra(EXTRA_LEFT, bounds.left)
                intent.putExtra(EXTRA_TOP, bounds.top)
                intent.putExtra(EXTRA_RIGHT, bounds.right)
                intent.putExtra(EXTRA_BOTTOM, bounds.bottom)
            }
            if (scale != null) intent.putExtra(EXTRA_SCALE, scale)
            context.sendBroadcast(intent)
            XLog.w("flexible:$cmd(#$taskId) 已转给 system_server 执行（落点 $bounds scale=$scale）")
        } catch (e: Throwable) {
            XLog.w("发 flexible 广播失败：$cmd", e)
        }
    }

    /**
     * [CMD_MINI_ANIM] 的起止状态 —— 宿主侧构造。**这些就是要反复试的参数**，
     * 所以它们走广播下发（与几何同理），改它们不用重启注入侧。
     */
    class MiniAnimParams(
        val startRect: android.graphics.Rect,
        val finishRect: android.graphics.Rect,
        val startScale: Float,
        val finishScale: Float,
        val startAlpha: Float,
        val finishAlpha: Float,
        val durationMs: Long,
    )

    /** 宿主进程调：让注入侧用框架自己的动画器把任务从「全屏外观」缩到「小窗外观」。 */
    fun sendMiniAnim(
        context: Context,
        taskId: Int,
        p: MiniAnimParams,
    ) {
        try {
            val intent =
                Intent(ACTION)
                    .setPackage(HostApp.PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(EXTRA_CMD, CMD_MINI_ANIM)
                    .putExtra(EXTRA_TASK_ID, taskId)
                    .putExtra(EXTRA_START_LEFT, p.startRect.left)
                    .putExtra(EXTRA_START_TOP, p.startRect.top)
                    .putExtra(EXTRA_START_RIGHT, p.startRect.right)
                    .putExtra(EXTRA_START_BOTTOM, p.startRect.bottom)
                    .putExtra(EXTRA_FINISH_LEFT, p.finishRect.left)
                    .putExtra(EXTRA_FINISH_TOP, p.finishRect.top)
                    .putExtra(EXTRA_FINISH_RIGHT, p.finishRect.right)
                    .putExtra(EXTRA_FINISH_BOTTOM, p.finishRect.bottom)
                    .putExtra(EXTRA_START_SCALE, p.startScale)
                    .putExtra(EXTRA_FINISH_SCALE, p.finishScale)
                    .putExtra(EXTRA_START_ALPHA, p.startAlpha)
                    .putExtra(EXTRA_FINISH_ALPHA, p.finishAlpha)
                    .putExtra(EXTRA_DURATION, p.durationMs)
            context.sendBroadcast(intent)
            XLog.w(
                "flexible:$CMD_MINI_ANIM(#$taskId) 已转给 system_server" +
                    "（start=${p.startRect}@${p.startScale}a${p.startAlpha}" +
                    " → finish=${p.finishRect}@${p.finishScale}a${p.finishAlpha}）",
            )
        } catch (e: Throwable) {
            XLog.w("发 flexible 广播失败：$CMD_MINI_ANIM", e)
        }
    }

    /**
     * 调用点在 AMS 广播入口的 hooker 里（热路径 + system_server），整体再包一层 catch：
     * 漏一个异常出去就是系统崩。
     *
     * @param loader system_server 的 ClassLoader（本机反射用；null 则只记日志不执行）
     */
    fun handle(
        intent: Intent,
        loader: ClassLoader?,
    ): Boolean {
        if (intent.action != ACTION) return false
        val cmd =
            try {
                intent.getStringExtra(EXTRA_CMD).orEmpty()
            } catch (e: Throwable) {
                XLog.w("读小窗指令 extra 失败：${e.message}")
                ""
            }
        val taskId =
            try {
                intent.getIntExtra(EXTRA_TASK_ID, -1)
            } catch (e: Throwable) {
                -1
            }
        // 落点几何随指令一起来（宿主侧下发）。读不到就走注入侧的兜底默认值 ——
        // 这样「宿主是新版、注入侧是旧版」时也不会退化成没有几何。
        val bounds = readBounds(intent)
        val scale =
            try {
                if (intent.hasExtra(EXTRA_SCALE)) intent.getFloatExtra(EXTRA_SCALE, EXPANDED_SCALE) else null
            } catch (e: Throwable) {
                null
            }
        try {
            when (cmd) {
                CMD_MINI -> switchToMiniLocal(loader, taskId, bounds, scale)
                CMD_MINI_ANIM -> miniAnimLocal(loader, taskId, intent)
                else -> XLog.w("不认识的小窗指令：$cmd")
            }
        } catch (e: Throwable) {
            XLog.w("执行小窗指令失败（忽略）：$cmd", e)
        }
        return true
    }

    /**
     * 读宿主下发的**应用配置矩形**；四项缺任何一项就整体当「没下发」，返回 null 让调用方走兜底。
     *
     * 四项是**一起有或一起没有**的（[send] 要么全带上要么全不带），所以是整体兜底、不是逐项兜底 ——
     * 下面 `getIntExtra` 的默认值取不到（前面已判过 `hasExtra`）。
     *
     * 和 [handle] 同理整体包 catch：调用点在 AMS 广播入口，漏一个异常出去就是系统崩。
     */
    internal fun readBounds(intent: Intent): android.graphics.Rect? =
        try {
            if (intent.hasExtra(EXTRA_LEFT) && intent.hasExtra(EXTRA_TOP) &&
                intent.hasExtra(EXTRA_RIGHT) && intent.hasExtra(EXTRA_BOTTOM)
            ) {
                android.graphics.Rect(
                    intent.getIntExtra(EXTRA_LEFT, 0),
                    intent.getIntExtra(EXTRA_TOP, 0),
                    intent.getIntExtra(EXTRA_RIGHT, 0),
                    intent.getIntExtra(EXTRA_BOTTOM, 0),
                )
            } else {
                null
            }
        } catch (e: Throwable) {
            XLog.w("读宿主下发的落点几何失败：${e.message}")
            null
        }

    // 剥到最内层 cause 的那份实现已收到 `Reflect.deepestCause`（与 `ProbeChannel` 共用一份）。

    /** 本机这一档 ROM 的反射面 —— 未适配的 ROM 照最后一档试（见 [RomProfile]）。 */
    internal val ROM = RomProfile.effective.reflection

    /** 动画器 / 参数对象的类名与常量 —— 全部来自 `oplus-services.jar` 的离线反编译（§十二）。 */
    internal val METHOD_MINI_ANIM = ROM.miniAnimMethod

    /**
     * 首选入口：`startFlexibleSpringAnimation(info, Rect, Task, Handler, Transaction, callback, String)`。
     *
     * 比 [METHOD_MINI_ANIM] 好在**没有 `isInTransition` 过滤**（真机实测：转换刚做完时任务正好还在
     * 过渡里，[METHOD_MINI_ANIM] 会把任务静默剔掉，日志只有 `current task is in transition`）——
     * 于是可以「转换完立刻播」，不会先露出一个静止的小窗。它自带的 spring 是 `0.87 / 261`
     * （[METHOD_MINI_ANIM] 是 `0.86 / 250`）。
     */
    internal val METHOD_SPRING_ANIM = ROM.springAnimMethod

    internal val CALLBACK_CLS = ROM.animCallbackClass
    internal val INFO_BUILDER_CLS = ROM.animInfoBuilderClass

    /** `FlexibleAnimationInfo.type` 要等于这个值，`endAnimation(205)` 才会走「结束上一段」分支。 */
    internal val ANIM_TYPE_TRANSIT_IME = ROM.animTypeTransitIme

    /**
     * `reason` 用框架自己那个字符串 —— 动画器开头用它选
     * `isInTransition(task, reason.equals("maySwitchStateToMini"))` 这一档过滤。
     */
    internal val REASON_MINI_ANIM = ROM.reasonMiniAnim

    /** 动画时长兜底（宿主会下发；spring 的实际收敛时长由阻尼/刚度决定，这个值主要用于日志与 A 档的排期）。 */
    internal const val DEFAULT_ANIM_DURATION = 400L

    /** 按名字反射，不直接引用（注入侧不许碰宿主框架的私有类型）。 */
    internal val SERVICE = ROM.flexibleWindowService
    internal const val FIELD_ATMS = "mAtms"
    internal const val FIELD_ROOT = "mRootWindowContainer"

    // 这一族是本模块**单方面**使用的框架语义数字（进的是宿主框架的私有方法 / 私有字段），
    //  既不在 `RomProfile` 的反射面里，也不来自任何配置 —— 所以就地给名字，别让它们以裸数字
    //  散在流程里：`1` 在下面的调用点里同时出现在「目标状态」「场景」「matchMode」三种含义上，
    //  看代码时分不出哪个是哪个，改错一个只会表现为「小窗形状不对」。
    //
    //  这些值都对着 `oplus-services.jar` 的离线反编译核过（§十二 / §17.6.3）；**它们会随 ROM 变**，
    //  换版本时按下面对应的名字逐个回查，不要靠「相邻的那个也是 1」来推。

    /** `switchState(task, state, reason)` 的目标状态：`1` = 小窗态。 */
    internal const val STATE_MINI = 1

    /** `TaskExt` 上「场景」字段要冒充的值：`1` = `launchScenario`（柔性全屏）。 */
    internal const val SCENARIO_FULL_SCREEN = 1

    /**
     * `TaskExt` 上「柔性缩放态」字段要冒充的值：`2` = 全屏态。
     *
     * 注意：**它和 [STATE_MINI] 不是一回事** —— 前者说「这个任务现在占满屏」，后者说「把它切到小窗」。
     * 两个值都由框架自己定义，只是恰好一个 1 一个 2，别互相抄。
     */
    internal const val ZOOM_STATE_FULL_SCREEN = 2

    /** `getCaptionName(scenario)` 的场景档：与 [SCENARIO_FULL_SCREEN] 同属「柔性全屏」那一档。 */
    internal const val CAPTION_SCENARIO_FULL_SCREEN = 1

    /**
     * `anyTaskForId(taskId, matchMode)` 的**允许档位**，依次放宽：`0` 只找已 attach 的、
     * `1` 再加 recents（只读）。**顺序是语义的一部分**：从最窄的一档开始试，先命中先返回。
     *
     * ## 为什么不含 `2`
     *
     *  `2` 是 `MATCH_TASK_IN_STACKS_OR_RECENT_TASKS_AND_RESTORE` —— 那个 `AND_RESTORE` 是**真恢复**：
     *  任务已被销毁、只剩 recents 里一条记录时，它会把任务**重新建出来**。这是一次状态改变，
     *  而调用方（`switchToMiniLocal` / `CMD_MINI_ANIM`，还有跟着一起走的探针）以为只是在**查**一个
     *  任务 —— 语义上「查」变成「顺手把它拉起来」。
     *
     *  真机上的后果：宿主刚看到前台任务、发命令，命令执行时任务已被销毁（用户按了返回 / 应用崩了），
     *  带 `2` 的查找会把这个已经关掉的应用**从 recents 恢复出来再收成小窗** —— 用户看到的是
     *  「按一下侧键，一个我刚关掉的应用以小窗弹出来」。宁可「找不到 ⇒ 命令落空」，也不要凭空复活。
     *
     *  探针跟生产**同一份**（本文件的整个理由，见 `FlexibleWindowLookup.kt` 头注释）：探针要问的是
     *  「生产路径会看到什么」，两边档位不一致时日志给出的就是与事实相反的结论。
     */
    internal val TASK_MATCH_MODES = intArrayOf(0, 1)

    /** `switchState` 的 reason 参数，会进服务端日志，用来分辨是不是本模块调的。 */
    internal const val REASON = "OplusKey"

    /**
     * 展开档小窗的**兜底默认**几何与缩放 —— PLK110（1272×2772 @560dpi）的真机实测值。
     *
     * 正常路径下这两个值由**宿主侧下发**（见 [EXTRA_LEFT] 一段的说明），这里只是「宿主没带」
     * 时的兜底，避免退化成没有几何。要调几何请改 `core/model/MiniWindowGeometry`（按机型标定
     * 的那张表，两侧都从它取）—— 宿主侧改完不用重启。
     *
     * 注意：[EXPANDED_BOUNDS] 是**应用配置矩形**，不是屏幕矩形：系统会把它的尺寸
     * **再乘一次 [EXPANDED_SCALE]** 才是屏幕上的实际大小，即
     *
     * ```
     * 屏幕尺寸 = Rect.size × scale
     * 1272 × 2261  ×  0.8017  =  1020 × 1812   与真机一致
     * ```
     *
     * 踩过的坑：这里曾经直接填**屏幕矩形** `Rect(126,319-1146,2131)`（1020×1812），
     * 结果被系统又缩了一次 —— `1020 × 0.8017 = 817`，窗口整整齐齐小了 0.8017 倍（真机实测
     * 817×1452，宽占比 0.6423 而不是 0.8019），表现为「小窗是对的，但比 ColorOS 原生的小一圈」。
     * 正确值是拿真机「原生小窗」的 `dumpsys activity activities` → `Task.mBounds` 反查出来的
     * （= `Rect(126,319 - 1398,2580)`），见 `docs/device-findings.md` 附七。
     *
     * 落点（left/top）则是**直接照搬到屏幕坐标**的，不含缩放 —— 所以 (126,319) 两头都是这个数。
     *
     * **不要**拿 `getMiniFlexibleTaskInfo` 的值代替 —— 那是收起档的几何
     * （约 `Rect(898,168-1203,710)` / `0.2397`），会把窗口缩成右上角一条。
     * 这几个数与适配清单绑定：加机型要连同它们重测（见 `docs/zoom-window.md` §五）。
     */
    internal val EXPANDED_BOUNDS: android.graphics.Rect get() = MiniWindowGeometry.effective.bounds
    internal val EXPANDED_SCALE: Float get() = MiniWindowGeometry.effective.scale

    /**
     * 配置矩形 × scale = 屏幕落点，只为打印验收日志用（`left/top` 不参与缩放）。
     *
     * 不缓存成常量：几何现在是宿主下发的，写死一个「预期屏幕矩形」反而会跟实际参数脱节。
     */
    internal fun screenBoundsOf(
        bounds: android.graphics.Rect,
        scale: Float,
    ): android.graphics.Rect =
        android.graphics.Rect(
            bounds.left,
            bounds.top,
            bounds.left + (bounds.width() * scale).toInt(),
            bounds.top + (bounds.height() * scale).toInt(),
        )
}
