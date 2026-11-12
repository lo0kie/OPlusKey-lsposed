package com.lookie.opluskey.ui.dialog

import com.lookie.opluskey.core.present.ActionCatalog

/**
 * 「哪一格开着」—— 打成一个包；以后要带上这一格的别的属性，也往这里加。
 *
 * @param tile 开着的那一格（标记格 / 入口格 / 自定义命令格）
 */
internal data class ModeMenuTarget(
    val tile: ActionCatalog.Spec,
)
