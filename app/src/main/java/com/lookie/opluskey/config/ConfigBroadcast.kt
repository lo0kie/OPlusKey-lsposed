package com.lookie.opluskey.config

import android.content.Intent

/** 配置的广播线格式：宿主 / 设置页发往注入侧（system_server）的编解码。 */
object ConfigBroadcast {

    const val ACTION_CONFIG = "com.lookie.opluskey.CONFIG"

    /** 配置广播里的各项（键名与 `config.conf` 对齐，方便对照）。 */
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
    fun parse(intent: Intent): Config {
        val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        return Config(
            keys = mapOf(
                KeyId.PLUS to KeyConfig(
                    id = KeyId.PLUS,
                    enabled = enabled,
                    single = Action.parse(intent.getStringExtra(EXTRA_SINGLE) ?: "native"),
                    double = Action.parse(intent.getStringExtra(EXTRA_DOUBLE) ?: "none")
                        .let { Action.normalizeForGesture(it, Gesture.DOUBLE) },
                    long = Action.parse(intent.getStringExtra(EXTRA_LONG) ?: "none")
                        .let { Action.normalizeForGesture(it, Gesture.LONG) },
                    longPressMs = intent.getIntExtra(
                        EXTRA_LONG_PRESS_MS, KeyConfig.DEFAULT_LONG_PRESS_MS,
                    ),
                    longRepeat = intent.getBooleanExtra(EXTRA_LONG_REPEAT, false),
                    longRepeatIntervalMs = intent.getIntExtra(
                        EXTRA_REPEAT_INTERVAL_MS, KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                    ),
                    longIsSerial = intent.getBooleanExtra(EXTRA_LONG_SERIAL, false),
                ),
            ),
            global = GlobalConfig(
                doubleClickMs = intent.getIntExtra(
                    EXTRA_DOUBLE_CLICK_MS, GlobalConfig.DEFAULT_DOUBLE_CLICK_MS,
                ),
                vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, false),
                instantSingle = intent.getBooleanExtra(
                    EXTRA_INSTANT_SINGLE, GlobalConfig.DEFAULT_INSTANT_SINGLE,
                ),
            ),
        )
    }
}
