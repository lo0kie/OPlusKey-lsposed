package com.lookie.opluskey.host.action

import android.content.Intent

/**
 * 交给原版启动链路的那份 intent —— 组件目标要补上「桌面启动」那一对标记。
 *
 *  `app:包名/类名`（「应用组件」那一档）的 intent 是 `Intent().setClassName(…)` 现拼的，
 *  没有 `ACTION_MAIN`，也没有 `CATEGORY_LAUNCHER`。原版那条链路（[OriginalLaunch]）按这两个
 *  认「这是一次桌面启动」来挑转场实现 —— 都没有的时候它只给系统默认动画（用户口径
 *  「组件没有这个动画」）。
 *
 *  两个都必须给：只给 `ACTION_MAIN` 时 ROM 仍认不出「从桌面起」，观感与不加一样。
 *
 *  **只加在这一份副本上**：兜底那条路（[AppLaunchAsUser.start]）用的还是原 intent ——
 *  补了 `CATEGORY_LAUNCHER` 的 intent 拿去起一个没声明这个 category 的组件，可能解析失败。
 *  副本是浅拷贝（extras 共享），原版链路只读不写，够用。
 *
 *  单独成文件只为把它挡在 `AppLauncher.kt` 的函数数上限（11）外面 —— 判据只有这一条，
 *  不值得再往那个文件里塞第二个理由。
 */
internal fun stockLaunchIntent(intent: Intent): Intent =
    if (intent.component != null && intent.action == null) {
        Intent(intent).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
    } else {
        intent
    }
