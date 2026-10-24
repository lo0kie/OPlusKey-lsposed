package com.lookie.opluskey.ui

import com.lookie.opluskey.core.model.ActionCatalog

/**
 * 「哪一格开着」+「这一层菜单要不要给只在长按成立的行」—— 打成一个包，只为让
 * [ModeMenuPopups] 的参数个数留在 detekt 的 `LongParameterList` 之下：这两件事永远同源
 * （都是「这一格在这一页」的属性），分开传迟早出现「行按长按算、标题按别的页算」。
 *
 * @param tile 开着的那一格（标记格 / 入口格 / 自定义命令格）
 * @param withLongOnly 要不要给「只在长按成立」的那一行（此刻只有灵感语音）：
 *   网格按「这一页是不是长按」传，命令链那层传 `false`。见 [ActionCatalog.modeMenu]。
 */
internal data class ModeMenuTarget(
    val tile: ActionCatalog.Spec,
    val withLongOnly: Boolean = true,
)
