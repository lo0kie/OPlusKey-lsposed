package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.OwnSettings

/**
 * 「独立设置页现在在改谁」—— 一颗键，或一组组合键。**纯界面状态**，不落盘。
 *
 * ## 为什么要有这一层
 *
 *  「按键单独设置」是所有档共有的一件事（每颗键 / 每组组合都能自己决定接口、振动、提示）。
 *  如果页面按「从哪个页面进来的」去猜，就得到处写 `if (是组合) … else …`。
 *  把「改谁」摆成一个值，页面与卡片只认它 —— 入口那一侧只负责写这一个值。
 *
 * ## 与 `KeyWatchState` / `ComboEditState` 的关系
 *
 *  那两个记的是「列表里选中了哪一颗 / 打开了哪一组」，服务的是绑定网格；这一位只服务
 *  独立设置页，生命周期也短（进页时写、出页不必清 —— 下次进页会覆盖）。
 */
internal sealed interface SettingsTarget {
    /** 当前在改的那一份独立设置（读用）—— 配置里找不到时回 `null`，页面据它退场。 */
    fun ownerIn(config: Config): OwnSettings?

    data class Key(
        val id: KeyId,
    ) : SettingsTarget {
        override fun ownerIn(config: Config): OwnSettings = config.key(id)
    }

    data class Combo(
        val id: String,
    ) : SettingsTarget {
        override fun ownerIn(config: Config): OwnSettings? = config.combos.firstOrNull { it.id == id }
    }
}

/** 这一位只在「独立设置」这一页里用；写它的只有两个入口（按键绑定页顶栏 / 组合编辑页顶栏）。 */
internal object SettingsTargetState {
    /** 默认值是侧键 —— 理论上进页前一定会被覆盖，给个合法值只是免得页面读到 null 崩掉。 */
    var current: SettingsTarget by mutableStateOf(SettingsTarget.Key(KeyId.PLUS))
}
