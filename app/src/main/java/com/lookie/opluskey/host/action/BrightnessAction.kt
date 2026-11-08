package com.lookie.opluskey.host.action

import android.content.ContentResolver
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.lookie.opluskey.bridge.StatusBarBridge
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action

/**
 * 屏幕亮度（`bright:50` / `bright:+10` / `bright:-10`）—— 宿主侧唯一实现。
 *
 * ## 百分比说的是「亮度条上的位置」
 *
 *  用户眼里的百分比是 `(raw − min) / (max − min)`，量程从 `screen_brightness_range` 读
 *  （本机 `2,4095`），读不到才退回 1..255。框架那个 `get-brightness --unit percentage` 报的是
 *  另一套 HLG 感知值：按它换算，`+10%` 在条上会走三十多格（见 `docs/device-findings.md` 三十八）。
 *
 * ## 自动亮度开着时那条路只能走 root
 *
 *  自动亮度（`screen_brightness_mode = 1`）下 `DisplayPowerController` 不拿 `screen_brightness`
 *  当目标 —— 只写它，显示一点不动。要让它跟手就得写 `screen_auto_brightness_adj`（值是**目标
 *  raw 本身**，不是 -1..1 的偏移），这正是小布助手 `j40.j.r(int)` 那两步（见 38.2）。
 *
 *  但 `screen_auto_brightness_adj` 在 `Settings.System.PRIVATE_SETTINGS` 里：SettingsProvider 的
 *  `enforceRestrictedSystemSettingsMutationForCallingPackage` 只对 uid 0 / 1000 / 2000 直接放行，
 *  其余包要么是 privileged app、要么在 OPPO 的白名单里。宿主是 `platform_app`、不是 privileged
 *  ⇒ 真机实测 `Settings.System.putFloat` 抛 `IllegalArgumentException: You cannot change private
 *  secure settings.`（`screen_brightness` 是 PUBLIC_SETTINGS，不受这条限制，写它没事）。
 *
 *  注入侧也接不了这一棒：system_server 里没有可用的 `ContentResolver`（见 六之五）。所以自动亮度
 *  那一档改成一条 `settings put` 交给 root —— 与用户自己写 `shell:` 走的是同一条通道。
 *  自动亮度**关着**的时候不需要 root：直接写 `screen_brightness` 就能落到显示上。
 *
 *  两个键写在**同一条命令**里是有意的：顺序必须是先 adj 再亮度（照小布），而 `su` 那条是异步的，
 *  拆成两次调用就保证不了先后。
 *
 * ## 光写库，控制中心的亮度条不一定动
 *
 *  那根条有两条刷新路径：DPC 的 display 事件（「这块屏的背光真的变了」）和 `screen_brightness`
 *  的 ContentObserver。后者在**自动亮度开着时会被 SystemUI 直接丢掉**（38.4），前者要看 DPC 的
 *  变化判定 —— 所以「有时候动、有时候不动」。小布靠的是前者（`setTemporaryBrightness` 把背光
 *  强行推到目标值），这里两者都补：先发 `StatusBarBridge.CMD_BRIGHTNESS` 推背光，落库之后再写
 *  `screen_brightness_duration` 当「刷新口令」（[nudgeSlider]）。
 */
internal object BrightnessAction {
    /**
     * 按 [action] 调一次亮度。
     *
     *  读不到当前值时**只有绝对档能继续**（加减档没有基准，直接放弃并记一行）；写不进去在日志里
     *  说清断在哪一步，并走 [onFailure] 报出去 —— 这里在按键回调的下游，不向外抛。
     */
    fun run(
        context: Context?,
        action: Action.Brightness,
        onFailure: (CommandFailure) -> Unit,
    ) {
        if (context == null) {
            XLog.w("没有 Context，亮度调整跳过")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        try {
            val cr = context.contentResolver
            val range = readRange(cr)
            val span = range.last - range.first
            val current = readRaw(cr)
            val target =
                targetOf(action, current, range.first, span) ?: run {
                    onFailure(CommandFailure.UNKNOWN)
                    return
                }
            val auto = Settings.System.getInt(cr, KEY_MODE, MODE_MANUAL) == MODE_AUTO
            XLog.w(
                "亮度：${action.token} → raw=$target" +
                    "（量程 ${range.first}..${range.last}，当前 ${current ?: "读不到"}" +
                    "${if (auto) "，自动亮度开着 → 走 root 写 adj" else ""}）",
            )
            // 先让 system_server 把背光推到目标值 —— 小布 `j40.j.r()` 的第三步，屏幕会立刻跟上。
            // 它顺带可能换来一次 display 事件，但那次事件不保证（38.4 的闸二）：
            // 真正保证亮度条跟着走的是下面那句 nudgeSlider。
            StatusBarBridge.sendBrightness(context, target)
            if (auto) {
                //  root 那条是**排队**的：`ShellRunner.exec` 立刻返回，真正落库在池子里。
                //  所以刷新口令必须挂在它的完成回调上 —— 直接写下一行的话，SystemUI 读到的
                //  还是旧值，亮度条不动。
                //  **失败时不再发口令**：`onDone` 是「跑完就调、成功失败都算」（见 `ShellRunner.exec`），
                //  写盘被拒还去写 `screen_brightness_duration`，等于告诉 SystemUI「亮度变了」——
                //  控制中心那根条会跳到一个根本没生效的值上。
                var failed = false
                writeViaRoot(
                    target,
                    onFailure = { f ->
                        failed = true
                        onFailure(f)
                    },
                ) { if (!failed) nudgeSlider(cr, target, current) }
            } else {
                //  手动档是同步写 `Settings`，**写成功**才发口令（顺序反了的话 SystemUI 读到的还是旧值）
                if (writeBrightness(cr, target, onFailure)) nudgeSlider(cr, target, current)
            }
        } catch (e: Throwable) {
            XLog.w("亮度调整失败（已忽略）：${e.javaClass.simpleName}: ${e.message}")
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /** 目标 raw 值（已夹到量程）；加减档读不到当前值时返回 `null`。 */
    private fun targetOf(
        action: Action.Brightness,
        current: Int?,
        min: Int,
        span: Int,
    ): Int? {
        //  百分比按**量程**折成 raw：`span * percent / 100`，与当前值在哪无关
        val step = span * action.percent / 100
        val value =
            when (action.mode) {
                Action.Brightness.Mode.FIX -> min + step
                Action.Brightness.Mode.ADD -> current?.plus(step)
                Action.Brightness.Mode.SUB -> current?.minus(step)
            } ?: run {
                XLog.w("亮度：读不到当前值，加减档没有基准，这次不动")
                return null
            }
        return value.coerceIn(min, min + span)
    }

    /** 量程：`screen_brightness_range` 写成 `"min,max"`；读不到按 AOSP 的 1..255。 */
    private fun readRange(cr: ContentResolver): IntRange {
        val text = runCatching { Settings.System.getString(cr, KEY_RANGE) }.getOrNull()
        val bounds = text?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
        if (bounds != null && bounds.size == 2 && bounds[1] > bounds[0]) return bounds[0]..bounds[1]
        XLog.w("亮度：读不到 screen_brightness_range，按 $DEFAULT_MIN..$DEFAULT_MAX 换算")
        return DEFAULT_MIN..DEFAULT_MAX
    }

    /** 当前 raw 值；读不到返回 `null`（不拿 0 顶替 —— 那会被当成「最暗」写回去）。 */
    private fun readRaw(cr: ContentResolver): Int? =
        runCatching {
            Settings.System
                .getString(cr, KEY_BRIGHTNESS)
                ?.trim()
                ?.toIntOrNull()
        }.getOrNull()
            ?: runCatching { Settings.System.getInt(cr, KEY_BRIGHTNESS) }.getOrNull()

    /**
     * 自动亮度那一档：一条 root 命令写两个键（顺序见类注释）。
     *
     *  用 `shell:` 同一个执行器（[ShellRunner]）：并发槽位、超时、失败分档与失败提示都是现成的。
     *  这里不写 `tip:` —— 亮度变化本身在屏幕上看得到。
     */
    private fun writeViaRoot(
        target: Int,
        onFailure: (CommandFailure) -> Unit,
        onDone: () -> Unit,
    ) {
        ShellRunner.exec(
            "$SETTINGS put system $KEY_ADJ $target.0; $SETTINGS put system $KEY_BRIGHTNESS $target",
            onFailure = onFailure,
            onDone = onDone,
        )
    }

    /** 自动亮度关着：`screen_brightness` 是公开键，宿主自己就能写，不必起 shell。 */
    private fun writeBrightness(
        cr: ContentResolver,
        target: Int,
        onFailure: (CommandFailure) -> Unit,
    ): Boolean {
        val wrote =
            runCatching { Settings.System.putString(cr, KEY_BRIGHTNESS, target.toString()) }
                .getOrElse {
                    XLog.w("亮度：写 screen_brightness 抛了 ${it.javaClass.simpleName}", it)
                    false
                }
        if (!wrote) {
            XLog.w("亮度：screen_brightness 被拒")
            onFailure(CommandFailure.UNKNOWN)
        }
        return wrote
    }

    /**
     * 让控制中心那根条跟着走 —— 写 `screen_brightness_duration` 当「刷新口令」。
     *
     * ## 为什么非它不可
     *
     *  自动亮度开着时 SystemUI 对 `screen_brightness` 的变化是**直接丢掉**的：
     *  `BrightnessController$$ExternalSyntheticLambda5` 里
     *  `if (mAutomatic && mIsScreenBrightnessAnimationSupport) return;`
     *  （本机 `ro.display.brightness.new.animation = 1`，恒走这一支 —— 反编译见 38.4）。
     *  它只认 `screen_brightness_duration`：那个 uri 一变就 `mUpdateSliderAnimationRunnable`，
     *  读 `screen_brightness` 把滑杆挪过去。系统自己拖条时走的就是这条路。
     *
     *  另一条路是上面那句「推临时亮度」换来的 display 事件，但它只在
     *  `DisplayPowerController.saveBrightnessInfo()` 判成「这块屏的背光真的变了」时才发 ——
     *  DPC 还算了一档 `z19`（亮度原因 7 且没应用临时自动亮度调整时**不发**，见 38.4）。
     *  背光没被推上去、或者推的值正好等于当前值，条就停在旧位置。这就是「有时候不更新」。
     *
     * ## 值怎么写
     *
     *  照系统那份：`[目标, 时长, 起点时刻, 起始值, 速率, 模式]`。只有 1 / 2 / 3 三项会被
     *  `getScreenBrightnessArray()` 读走，其余**原样抄上一次的**（别处若解析也不至于读到怪值）。
     *
     *  起点时刻取**过去**：`(now − start) / duration > 1`，滑杆一步落到目标位置、不起动画。
     *  取将来会让它停在起始值不动 —— 那恰好就是我们要避免的现象。
     */
    private fun nudgeSlider(
        cr: ContentResolver,
        target: Int,
        from: Int?,
    ) {
        val prev = runCatching { Settings.System.getString(cr, KEY_DURATION) }.getOrNull()
        val parts = prev?.trim()?.removeSurrounding("[", "]")?.split(',') ?: emptyList()

        fun part(
            index: Int,
            fallback: String,
        ): String = parts.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
        val text =
            "[$target,$DURATION_MS,${SystemClock.uptimeMillis() - START_BACK_MS},${from ?: target}," +
                "${part(4, RATE_DEFAULT)},${part(5, MODE_DEFAULT)}]"
        val wrote =
            runCatching { Settings.System.putString(cr, KEY_DURATION, text) }
                .getOrElse {
                    XLog.w("亮度：写 screen_brightness_duration 抛了 ${it.javaClass.simpleName}", it)
                    false
                }
        if (!wrote) XLog.w("亮度：screen_brightness_duration 被拒，控制中心的条可能不会动")
    }
}

/** 亮度值（raw）。 */
private const val KEY_BRIGHTNESS = "screen_brightness"

/** 自动亮度开关：1 = 自动、0 = 手动。与 `SettingSwitch` 里那个键同值，那边判开关、这边判该走哪条路。 */
private const val KEY_MODE = "screen_brightness_mode"

/** 自动亮度下的目标亮度（raw）。见类注释：这是 `PRIVATE_SETTINGS`，宿主写不了。 */
private const val KEY_ADJ = "screen_auto_brightness_adj"

/** 亮度条的取值范围，写成 `"min,max"`。 */
private const val KEY_RANGE = "screen_brightness_range"

/**
 * 控制中心亮度条的「刷新口令」—— 写它就会触发 `mUpdateSliderAnimationRunnable`（见 [nudgeSlider]）。
 *
 *  值形如 `[1530,4500,210196,1497,91.7,7]`，本机系统自己写的时候就是这个形状。
 */
private const val KEY_DURATION = "screen_brightness_duration"

/** [KEY_DURATION] 里的动画时长（毫秒）。起点取在过去，实际不会真的跑这么久。 */
private const val DURATION_MS = 200

/** 起点时刻往前挪多少毫秒 —— 保证「此刻」已经越过终点，滑杆直接落到目标位置。 */
private const val START_BACK_MS = 500L

/** 读不到上一次的 `screen_brightness_duration` 时，后两项用的兜底值（抄本机系统值）。 */
private const val RATE_DEFAULT = "91.7"

private const val MODE_DEFAULT = "7"

/** `settings` 这个 CLI —— 走 root 时用它写两个键。 */
private const val SETTINGS = "settings"

private const val MODE_AUTO = 1

private const val MODE_MANUAL = 0

/** 读不到量程时的退路：AOSP 默认的 1..255（本机是 2..4095）。 */
private const val DEFAULT_MIN = 1

private const val DEFAULT_MAX = 255
