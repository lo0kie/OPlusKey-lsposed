package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.OwnSettings
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.core.present.keyName

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
 *
 * ## 「这一档能不能用」也归这里
 *
 *  [enabledIn] / [disabledReason] 是「停用的键 / 组合不能进独立设置」这条规则的**唯一实现**：
 *  三个入口（按键菜单那一行、组合菜单那一行、两页顶栏那颗设置键）都问它，谁也不许自己拼一句
 *  「已停用」—— 同一个判据写三份，早晚有一处忘改（见 AGENTS.md 一之二、2）。
 */
internal sealed interface SettingsTarget {
    /** 当前在改的那一份独立设置（读用）—— 配置里找不到时回 `null`，页面据它退场。 */
    fun ownerIn(config: Config): OwnSettings?

    /** 这一档**现在能不能用**（键 / 组合各自的 `enabled`）。停用 ⇒ 设置入口也按不动。 */
    fun enabledIn(config: Config): Boolean

    /**
     * 这一档停用时「为什么按不动」那一句；能用时为 `null`。
     *
     *  与页面里其它按不动的控件**同一句**（`KEY_DISABLED_ITEM` / `COMBO_DISABLED_ITEM`），
     *  并且名字走 `keyName` / `comboLabel` —— 那一份才是「一个键怎么念」的权威实现。
     */
    fun disabledReason(config: Config): String?

    data class Key(
        val id: KeyId,
    ) : SettingsTarget {
        override fun ownerIn(config: Config): OwnSettings = config.key(id)

        override fun enabledIn(config: Config): Boolean = config.key(id).enabled

        override fun disabledReason(config: Config): String? =
            if (enabledIn(config)) null else I18n.format(Strings.KEY_DISABLED_ITEM, keyName(id))
    }

    data class Combo(
        val id: String,
    ) : SettingsTarget {
        override fun ownerIn(config: Config): OwnSettings? = config.combos.firstOrNull { it.id == id }

        /**
         * 找不到这一档（刚删完、退场那一帧）按**能用**处理 —— 与菜单那边 `?: true` 同一口径：
         * 按停用处理会让顶栏那颗在一帧里闪一句「已停用」，而它下一刻就不在了。
         */
        override fun enabledIn(config: Config): Boolean = config.combos.firstOrNull { it.id == id }?.enabled ?: true

        override fun disabledReason(config: Config): String? {
            val combo = config.combos.firstOrNull { it.id == id } ?: return null
            return if (combo.enabled) null else I18n.format(Strings.COMBO_DISABLED_ITEM, comboLabel(config, combo))
        }
    }
}

/** 这一位只在「独立设置」这一页里用；写它的只有两个入口（按键绑定页顶栏 / 组合编辑页顶栏）。 */
internal object SettingsTargetState {
    /** 默认值是侧键 —— 理论上进页前一定会被覆盖，给个合法值只是免得页面读到 null 崩掉。 */
    var current: SettingsTarget by mutableStateOf(SettingsTarget.Key(KeyId.PLUS))
}
