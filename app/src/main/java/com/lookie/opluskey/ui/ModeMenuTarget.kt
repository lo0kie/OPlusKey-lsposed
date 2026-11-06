package com.lookie.opluskey.ui

import com.lookie.opluskey.core.model.ActionCatalog

/**
 * 「哪一格开着」—— 打成一个包，只为让 [ModeMenuPopups] 的参数个数留在 detekt 的
 * `LongParameterList` 之下；以后要带上这一格的别的属性，也往这里加。
 *
 * @param tile 开着的那一格（标记格 / 入口格 / 自定义命令格）
 */
internal data class ModeMenuTarget(
    val tile: ActionCatalog.Spec,
)
