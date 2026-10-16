package com.lookie.opluskey.ui.state

import androidx.compose.runtime.mutableStateOf
import com.lookie.opluskey.core.i18n.I18n

/**
 * 界面层的字典订阅点。
 *
 *  `core/i18n/I18n` 本体不能持 Compose 状态：`core/` 被 CI 那条「注入侧不得引用界面层依赖」的
 *  grep 一并圈在范围内（见 `.github/workflows/build.yml`），那一层出现 `androidx.*` 就等于把发版
 *  断在 CI 上。所以字典本体留在 core（普通 Map），**订阅**这一半放在这里：
 *
 *  - [read]：组合期取文案时经它读下面这份状态 —— 「取一次文案」于是变成一次状态读取，
 *    换字典时组合就重组，`I18n.get` / `I18n.format` 的调用点一个字都不用改；
 *  - [onChanged]：core 换过字典之后把新表灌进来。
 */
private val dict = mutableStateOf<Map<String, String>>(emptyMap())

/**
 * 建页时装一次（见 `SettingsPage.createView`）—— 不装的话界面文案不会跟着语言档重画。
 *
 * 可重入：页面每次重建都装一遍，装的就是同一份状态。
 */
internal fun installI18nBridge() {
    I18n.installUiBridge(
        read = { dict.value },
        onChanged = { dict.value = I18n.snapshot() },
    )
}
