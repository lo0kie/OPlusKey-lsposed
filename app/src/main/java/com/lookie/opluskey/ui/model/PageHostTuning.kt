package com.lookie.opluskey.ui.model

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
 * 没有回音就当模块没加载（整页禁用 + 一条提示）。给得偏宽，是因为这一步可能要等**宿主进程
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
 * 适配清单外的机器本来就该整页禁用，无限轮询只是白耗电。
 */
internal const val QUERY_ROUNDS = 3
