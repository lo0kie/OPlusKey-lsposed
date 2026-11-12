package com.lookie.opluskey.ui.chrome

import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.state.host.UpdateState
import com.lookie.opluskey.ui.wire.fetchText

/**
 * 把页面能力收成「检查更新要的那三样」—— 见 `UpdateState.Host`。
 *
 *  为什么适配放在 `ui` 根：`ui.state` **不许反向认识页面**（分层是单向的
 *  `ui.screen → ui → ui.state`，见 `docs/architecture/decoupling.md`）。这一层只做「换成 lambda」，
 *  不掺任何判断 —— 判断留在状态层，能力留在页面。
 */
internal fun SettingsPage.updateHost(): UpdateState.Host =
    UpdateState.Host(
        context = context,
        versionName = { moduleVersionName() },
        fetch = { url, timeoutMs -> fetchText(url, timeoutMs) },
    )
