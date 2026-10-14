package com.lookie.opluskey.core.wire

import android.content.Intent
import android.os.Bundle
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId

/** 配置的广播线格式：宿主 / 设置页发往注入侧（system_server）的编解码。 */
object ConfigBroadcast {

    const val ACTION_CONFIG = "com.lookie.opluskey.CONFIG"

    /** 配置广播里的各项（键名沿用旧 `config.conf` 的写法，历史对照用；文件格式现已是 JSON）。 */
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_SINGLE = "plus_single"
    const val EXTRA_DOUBLE = "plus_double"
    const val EXTRA_LONG = "plus_long"
    const val EXTRA_LONG_PRESS_MS = "long_press_ms"
    const val EXTRA_LONG_REPEAT = "long_repeat"
    const val EXTRA_REPEAT_INTERVAL_MS = "long_repeat_interval_ms"

    /**
     * 长按绑的是不是「多条命令」（`KeyConfig.longIsSerial`）。必须随广播过来：注入侧算不出来
     * （算它要读命令库文件，那个目录 system_server 进不去，见 `KeyConfig.longIsSerial` 注释），
     * 不带这一位「多条命令不支持连续触发」的闸门在真正判定路径上恒失效。
     */
    const val EXTRA_LONG_SERIAL = "long_is_serial"
    const val EXTRA_DOUBLE_CLICK_MS = "double_click_ms"
    const val EXTRA_VIBRATE = "vibrate"
    const val EXTRA_INSTANT_SINGLE = "instant_single"

    /**
     * 一条配置广播的**内容指纹**（仅用于「设置页手上这份」与「注入侧那份」是不是同一个）。
     *
     *  注入侧原来只回一个「收到过配置没有」的布尔 —— 那答不了「后来改过的那一次到了没有」：
     *  开机时收到过一条，之后哪条广播丢了，它那边还是旧配置，界面照样报「正常」。
     *  这里两边算同一个指纹，设置页比一次就知道同步没同步。
     *
     *  按 **extras** 算而不是按 [Config] 算：注入侧拿到的是广播解出来的对象，若按对象算，
     *  `longIsSerial` 这类由发送侧现算的字段、以及 `normalizeForGesture` 的归一化都会让
     *  同一份配置在两边的指纹不同 —— 那是假警报。指纹只回答「发出去的那条 = 收到的那条」。
     *
     *  ⚠️ **只做同源比对，不做安全判据**（`hashCode` 有碰撞可能）：用途是让「设置生效」
     *  那一段说实话，不是访问控制。
     */
    fun tag(intent: Intent): String {
        val extras = intent.extras
        val keys = extras?.keySet()?.sorted().orEmpty()
        val sb = StringBuilder()
        for (k in keys) sb.append(k).append('=').append(extras?.let { rawExtra(it, k) }).append(';')
        return sb.toString().hashCode().toUInt().toString(16)
    }

    /**
     * 取一个 extra 的原始值（只给 [tag] 用）。
     *
     * `Bundle.get(String)` 在 API 33 起弃用（返回 `Any?`、类型不安全），替代品是各类型的 getter
     * ⇒ 这里按 [intent] 里 `putExtra` 的**实际类型**分派。本广播的 extra 只有 Boolean / Int /
     * String 三类；将来加新 extra 时，类型不在这三类里的要在这里补一行。
     *
     *  ⚠️ **不能「挨个类型试」**：类型不匹配时 `getString` 抛 `ClassCastException`（不是返回 null）。
     *  ⚠️ 本函数在**注入侧**（system_server）也会被调到（见 `ConfigChannel`）⇒ 整体 catch 兜住，
     *  取值失败只当「这个键没值」（与旧的 `Bundle.get` 返回 null 同一个结果），绝不让异常越出去。
     */
    private fun rawExtra(extras: Bundle, k: String): Any? = try {
        when (k) {
            EXTRA_ENABLED, EXTRA_LONG_REPEAT, EXTRA_LONG_SERIAL,
            EXTRA_VIBRATE, EXTRA_INSTANT_SINGLE,
            -> extras.getBoolean(k)

            EXTRA_LONG_PRESS_MS, EXTRA_REPEAT_INTERVAL_MS, EXTRA_DOUBLE_CLICK_MS -> extras.getInt(k)

            else -> extras.getString(k)
        }
    } catch (e: Throwable) {
        null
    }

    /** [tag] 的发送侧入口：按 [intent] 的口径算，与接收侧逐字节对得上。 */
    fun tag(config: Config): String = tag(intent(config))

    /**
     * 把一份配置编成广播；宿主与设置页共用此构造器，格式只在这里写一次。
     * [EXTRA_LONG_SERIAL] 在这里现算：两个调用方（宿主主进程 / 设置页 `:ui` 进程）都与宿主
     * 同 uid、读得到命令库，判定与界面共用 [CommandLibrary.isSerial] 同一份口径；此路径不在
     * 按键热路径上（只在启动 / 解锁 / 写盘时走），读一次文件的代价可接受。
     */
    fun intent(config: Config): Intent {
        val key = config.key(KeyId.PLUS)
        return Intent(ACTION_CONFIG)
            .putExtra(EXTRA_ENABLED, key.enabled)
            .putExtra(EXTRA_SINGLE, key.single.token)
            .putExtra(EXTRA_DOUBLE, key.double.token)
            .putExtra(EXTRA_LONG, key.long.token)
            .putExtra(EXTRA_LONG_PRESS_MS, key.longPressMs)
            .putExtra(EXTRA_LONG_REPEAT, key.longRepeat)
            .putExtra(EXTRA_REPEAT_INTERVAL_MS, key.longRepeatIntervalMs)
            .putExtra(
                EXTRA_LONG_SERIAL,
                CommandLibrary.isSerial(CommandLibrary.loadCached(), key.long.token),
            )
            .putExtra(EXTRA_DOUBLE_CLICK_MS, config.global.doubleClickMs)
            .putExtra(EXTRA_VIBRATE, config.global.vibrate)
            .putExtra(EXTRA_INSTANT_SINGLE, config.global.instantSingle)
    }

    /**
     * 把一条配置广播解回 [Config]。
     * 调用点在 AMS 广播入口的 hooker（热路径）：只取 extra 拼对象，不做查找与 IO。
     * 拼装不得抛异常，否则 system_server 崩溃；缺项取安全默认（不接管、单击保留原生）。
     */
    /** 与 [ConfigCodec] 同一口径的钳制：广播值可能来自手改文件，越界值按边界处理。 */
    private fun clampInt(v: Int, min: Int, max: Int): Int = v.coerceIn(min, max)

    fun parse(intent: Intent): Config {
        val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        return Config(
            keys = mapOf(
                KeyId.PLUS to KeyConfig(
                    id = KeyId.PLUS,
                    enabled = enabled,
                    single = Action.parse(intent.getStringExtra(EXTRA_SINGLE) ?: "none"),
                    double = Action.parse(intent.getStringExtra(EXTRA_DOUBLE) ?: "none"),
                    long = Action.parse(intent.getStringExtra(EXTRA_LONG) ?: "none"),
                    longPressMs = clampInt(
                        intent.getIntExtra(EXTRA_LONG_PRESS_MS, KeyConfig.DEFAULT_LONG_PRESS_MS),
                        KeyConfig.MIN_LONG_PRESS_MS, KeyConfig.MAX_LONG_PRESS_MS,
                    ),
                    longRepeat = intent.getBooleanExtra(EXTRA_LONG_REPEAT, false),
                    longRepeatIntervalMs = clampInt(
                        intent.getIntExtra(EXTRA_REPEAT_INTERVAL_MS, KeyConfig.DEFAULT_REPEAT_INTERVAL_MS),
                        KeyConfig.MIN_REPEAT_INTERVAL_MS, KeyConfig.MAX_REPEAT_INTERVAL_MS,
                    ),
                    longIsSerial = intent.getBooleanExtra(EXTRA_LONG_SERIAL, false),
                ),
            ),
            global = GlobalConfig(
                doubleClickMs = clampInt(
                    intent.getIntExtra(EXTRA_DOUBLE_CLICK_MS, GlobalConfig.DEFAULT_DOUBLE_CLICK_MS),
                    GlobalConfig.MIN_DOUBLE_CLICK_MS, GlobalConfig.MAX_DOUBLE_CLICK_MS,
                ),
                vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, false),
                instantSingle = intent.getBooleanExtra(
                    EXTRA_INSTANT_SINGLE, GlobalConfig.DEFAULT_INSTANT_SINGLE,
                ),
            ),
        )
    }
}
