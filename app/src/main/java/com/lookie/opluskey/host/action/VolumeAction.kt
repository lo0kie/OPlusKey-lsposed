package com.lookie.opluskey.host.action

import android.content.Context
import android.media.AudioManager
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import kotlin.math.roundToInt

/**
 * 媒体音量（`volume:50` / `volume:+10` / `volume:-10`）—— 宿主侧唯一实现。
 *
 * ## 百分比说的是「音量条上的位置」
 *
 *  与屏幕亮度那一档同一套口径：`(当前档 / 最大档)`，三个档的百分比都按**整条量程**算
 *  （`max * percent / 100`），不是按当前值的比例；越界夹到两端，不报错。
 *
 *  与 `keyevent:24` / `keyevent:25` 的区别就在这里：那两条是「按一下」，一次一格；这一条是
 *  「按百分比一段」。
 *
 * ## 只碰媒体流
 *
 *  `STREAM_MUSIC` 是用户在音量面板里默认调的那一路，也是唯一不需要额外权限的：
 *  铃声 / 通知（`STREAM_RING`）在免打扰下会被拦，闹钟（`STREAM_ALARM`）要另判。
 *  写它不需要 `MODIFY_AUDIO_SETTINGS` 之外的东西，宿主本来就持有。
 *
 * ## 写入时带 `FLAG_SHOW_UI`
 *
 *  与按物理音量键同一个反馈：屏幕上出音量条。flags 给 0 也能改音量，但全程静默 ——
 *  按一下侧键什么都没看见，会被当成没生效。
 */
internal object VolumeAction {
    /** 调哪一路流 —— 媒体流。 */
    private const val STREAM: Int = AudioManager.STREAM_MUSIC

    /**
     * 按 [action] 调一次音量。
     *
     *  取不到 `AudioManager` / 最大档为 0 时**直接放弃并记一行**；写入抛异常走 [onFailure] 报出去 ——
     *  这里在按键回调的下游，不向外抛。
     */
    fun run(
        context: Context?,
        action: Action.Volume,
        onFailure: (CommandFailure) -> Unit,
    ) {
        val am =
            context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: run {
                XLog.w("没有 AudioManager，音量调整跳过")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
        try {
            val max = am.getStreamMaxVolume(STREAM)
            if (max <= 0) {
                XLog.w("音量调整跳过：媒体流的最大档是 $max")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
            val current = am.getStreamVolume(STREAM)
            //  百分比按**整条量程**折算成档数（不是按当前值的比例）
            val step = (max * action.percent / 100.0).roundToInt()
            val target =
                when (action.mode) {
                    Action.Volume.Mode.FIX -> step
                    Action.Volume.Mode.ADD -> current + step
                    Action.Volume.Mode.SUB -> current - step
                }.coerceIn(0, max)
            XLog.w("音量：${action.token} → 档 $target/$max（当前 $current）")
            //  带 FLAG_SHOW_UI：与按物理音量键一样在屏幕上出音量条。flags 给 0 是**静默写入** ——
            //  音量确实变了，但屏幕上一个提示都没有，用户会以为这一下没生效。
            am.setStreamVolume(STREAM, target, AudioManager.FLAG_SHOW_UI)
        } catch (e: Throwable) {
            XLog.w("音量调整失败（已忽略）：${e.javaClass.simpleName}: ${e.message}")
            onFailure(CommandFailure.UNKNOWN)
        }
    }
}
