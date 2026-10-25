package com.lookie.opluskey.ui.screen.binding

import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId

/**
 * 一张动作网格**正在往哪儿写**。
 *
 * ## 为什么要这一层
 *
 *  网格那套东西（`BindingGridState` + `BindingGrids` + `pick`）原本只服务「一颗键的一档手势」：
 *  写进去的是 `KeyConfig.single/double/long`，读回来的是 `cfg.key(Editing).actionOf(g)`。
 *  组合键也要选动作，而它的动作挂在**另一张表**上（`ComboKey.single/long`）——
 *  如果为此再写一套网格，两处的分组、选中态、入口格菜单、主题色就会各走各的（真机上表现成
 *  「同一个手电筒在两页里长得不一样」）。
 *
 *  所以把「写哪儿」抽成一个值，网格只认它：读当前绑定走 [actionIn]，写走
 *  `pick(spec, target)`，写盘说明由 [what] 给。
 *
 * ## 只有两处实现
 *
 *  · [Key] —— 一颗键的一档（三档手势都走它）；
 *  · [Combo] —— 一档组合键的一档（组合只有短按 / 长按，双击在 `ComboKey` 上就没有定义）。
 *
 *  `gesture` 仍然对外暴露：网格要按它决定「灵感语音那一格出不出现」
 *  （`ActionCatalog.modeMenu(token, withLongOnly = g == LONG)`）。
 */
internal sealed interface BindingTarget {
    /** 这一档是哪一档手势 —— 网格按它过滤清单（长按页多一格「灵感语音」）。 */
    val gesture: Gesture

    /**
     * 这一档**现在绑的是什么** —— 网格的打勾项、入口格小字、行首图标都由它算。
     *
     *  总是回一份具体动作：找不到那一档（键被删了 / 组合被删了）就回 [Action.None]，
     *  网格会落进「什么都没选中」，而不是崩。
     */
    fun actionIn(cfg: Config): Action

    /**
     * 写盘说明里那一段 —— `pick` 写成功后那一行日志与回执的键。
     *
     *  单键那份与这一层出现之前**一字不差**（`single` / `double` / `long`）：日志与
     *  判据脚本一直按它 grep，改格式等于把旧账本作废。组合那份带上 id 与手势，
     *  同一档组合的短按 / 长按在日志里分得开。
     */
    val what: String

    /** 一颗键的一档。 */
    data class Key(
        val id: KeyId,
        override val gesture: Gesture,
    ) : BindingTarget {
        override fun actionIn(cfg: Config): Action = cfg.key(id).actionOf(gesture)

        override val what: String get() = gesture.id
    }

    /** 一档组合键的一档。 */
    data class Combo(
        val id: String,
        override val gesture: Gesture,
    ) : BindingTarget {
        override fun actionIn(cfg: Config): Action = cfg.combos.firstOrNull { it.id == id }?.actionOf(gesture) ?: Action.None

        override val what: String get() = "combo:$id:${gesture.id}"
    }
}
