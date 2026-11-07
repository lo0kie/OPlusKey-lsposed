package com.lookie.opluskey.ui

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.LongPressEdit
import com.lookie.opluskey.core.model.LongPressParams
import com.lookie.opluskey.core.model.StateRule
import com.lookie.opluskey.core.model.withLongPress
import com.lookie.opluskey.ui.model.BRANCHES_TITLE

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
 *  `gesture` 仍然对外暴露：网格按它读这一档现在绑的是什么、写哪一格（清单本身不按手势分化）。
 */
internal sealed interface BindingTarget {
    /** 这一档是哪一档手势 —— 网格按它读写这一档的绑定。 */
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

    /**
     * 这一档**所属的那一份设置是谁** —— 场景入口、独立设置、停用提示都按它取。
     *
     *  单键回 `KeyConfig`、组合回 `ComboKey`，两者在**能共用契约的地方**（`OwnSettings`）
     *  直接是同一份用法；页面与卡片只认这一处，不再各自 `when (是不是组合)`。
     */
    fun ownerIn(cfg: Config): Any?

    /** 这一档的**场景表**（`StateRule`）—— 场景入口那句「有几个判断」与横幅都按它数。 */
    fun rulesIn(cfg: Config): List<StateRule>

    /** 这一档属于哪一组组合键；单键是 `null` —— 挑应用那条路只认这一个值。 */
    val comboId: String? get() = null

    /** 这一档的长按参数 —— 单键与组合都实现 `LongPressParams`，卡片只认那个接口。 */
    fun longPress(cfg: Config): LongPressParams?

    /** 把一次长按改动写回这一档所在的配置。 */
    fun withLongPress(
        cfg: Config,
        edit: LongPressEdit,
    ): Config

    /**
     * 这一档现在**能不能进场景表** —— 停用的那一份不行。
     *
     *  判据放在这里而不是各页面自己拼：两个页面（单键 / 组合）读的必须是同一句，
     *  否则「单键灰了、组合还亮着」这种事迟早出现。
     *
     *  @return `null` = 能进；否则是点它时该说的那句话
     */
    fun scenarioBlockedReason(cfg: Config): String?

    /** 一颗键的一档。 */
    data class Key(
        val id: KeyId,
        override val gesture: Gesture,
    ) : BindingTarget {
        override fun actionIn(cfg: Config): Action = cfg.key(id).actionOf(gesture)

        override val what: String get() = gesture.id

        override fun ownerIn(cfg: Config): Any? = cfg.key(id)

        override fun rulesIn(cfg: Config): List<StateRule> = cfg.key(id).rules.filter { it.gesture == gesture }

        override fun longPress(cfg: Config): LongPressParams = cfg.key(id)

        override fun withLongPress(
            cfg: Config,
            edit: LongPressEdit,
        ): Config = cfg.copy(keys = cfg.keys + (id to cfg.key(id).withLongPress(edit)))

        override fun scenarioBlockedReason(cfg: Config): String? {
            val key = cfg.key(id)
            return if (key.enabled) {
                null
            } else {
                I18n.format(Strings.KEY_DISABLED_ITEM, id.label)
            }
        }
    }

    /** 一档组合键的一档。 */
    data class Combo(
        val id: String,
        override val gesture: Gesture,
    ) : BindingTarget {
        override fun actionIn(cfg: Config): Action = cfg.combos.firstOrNull { it.id == id }?.actionOf(gesture) ?: Action.None

        override val what: String get() = "combo:$id:${gesture.id}"

        override fun ownerIn(cfg: Config): Any? = cfg.combos.firstOrNull { it.id == id }

        override fun rulesIn(cfg: Config): List<StateRule> =
            cfg.combos
                .firstOrNull { it.id == id }
                ?.rules
                ?.filter { it.gesture == gesture }
                .orEmpty()

        override val comboId: String get() = id

        override fun longPress(cfg: Config): LongPressParams? = cfg.combos.firstOrNull { it.id == id }

        override fun withLongPress(
            cfg: Config,
            edit: LongPressEdit,
        ): Config =
            cfg.copy(
                combos =
                    cfg.combos.map {
                        if (it.id == id) it.withLongPress(edit) else it
                    },
            )

        override fun scenarioBlockedReason(cfg: Config): String? {
            val combo = cfg.combos.firstOrNull { it.id == id } ?: return null
            return if (combo.enabled) {
                null
            } else {
                I18n.format(Strings.COMBO_DISABLED_ITEM, id)
            }
        }
    }
}

/**
 * 进「系统场景」编**这一档** —— 两页共用这一支。
 *
 *  「为谁编」与「编哪一档」都由 target 给：单键清掉组合目标、组合带上自己的 id。
 *  之前在单键页与组合页各写了一遍，漏一处就会「进去编的是上一次那一档」。
 */
internal fun SettingsPage.openBranchesFor(target: BindingTarget) {
    branchDraft.comboId = target.comboId
    branchGesture = target.gesture.ordinal
    XLog.w("进「$BRANCHES_TITLE」页：${target.what}")
    navTo(Screen.BRANCHES)
}
