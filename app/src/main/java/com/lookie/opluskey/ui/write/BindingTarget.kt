package com.lookie.opluskey.ui.write

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.LongPressEdit
import com.lookie.opluskey.core.model.LongPressParams
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.core.model.withLongPress
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.text.BRANCHES_TITLE
import com.lookie.opluskey.ui.state.binding.BranchDraft
import com.lookie.opluskey.ui.state.binding.KeyWatchState

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
 * ## 三处实现，两种落点
 *
 *  · [Key] —— 一颗键的一档（三档手势 + 第 3 击起的自定义档位都走它）；
 *  · [Combo] —— 一档组合键的一档（组合只有短按 / 长按，双击在 `ComboKey` 上就没有定义）；
 *  · [Scene] —— 某个**场景块**的一档。
 *
 *  前两个**落盘**（写进配置、当场广播出去），[Scene] 只**改草稿** ——
 *  场景表是「改完再点确认」的那一套（见 `BranchDraft`），提前落盘等于把用户还没确认的改动
 *  写死在配置里。三种目标的读写都从这一层走，网格与各页面因此不必知道「这次是不是在写草稿」。
 *
 *  `gesture` 仍然对外暴露：网格按它读这一档现在绑的是什么、写哪一格（清单本身不按手势分化）。
 *  它是**触发条件**（`GestureTrigger`）而不是那个三值枚举 —— 「按键绑定」页的另外两页是
 *  三击 / 四击，它们在三值枚举里没有名字（见 `GesturePlan`）。场景块的档位表跟着
 *  `GestureTrigger` 走（`SceneRule.actions`），所以这两档在场景里也编得；组合键只有短按 / 长按，
 *  它那一头拿到别的档位就当「没有定义」（`ComboKey.actionOf`）。
 */
internal sealed interface BindingTarget {
    /** 这一档是哪一档手势 —— 网格按它读写这一档的绑定。 */
    val gesture: GestureTrigger

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
     *
     *  场景那一档回 `null`：它不是「一份设置」，而是别人的一张表里的一行（那一页也不摆
     *  场景入口与独立设置，见 `BindingGroups`）。
     */
    fun ownerIn(cfg: Config): Any?

    /** 这一档的**场景表**（`SceneRule`）—— 场景入口那句「有几个判断」与横幅都按它数。 */
    fun rulesIn(cfg: Config): List<SceneRule>

    /** 这一档属于哪一组组合键；单键与场景是 `null` —— 挑应用那条路只认这一个值。 */
    val comboId: String? get() = null

    /**
     * 这一档在**场景表里的第几块**（`-1` = 不是场景目标）。
     *
     *  与 [comboId] 同一条用途：给这一档挑应用 / 挑变量时要把「写回哪儿」记进页面级状态
     *  （那一趟要压一层「选择应用」页，见 `AppPickerState.sceneAt`），而那一层只认识原始值。
     */
    val sceneAt: Int get() = -1

    /** 这一档的长按参数 —— 单键与组合都实现 `LongPressParams`，卡片只认那个接口。 */
    fun longPress(cfg: Config): LongPressParams?

    /**
     * 「松手执行额外动作」那一族的现行值 —— **落点与另外三族不同**。
     *
     *  时序 / 连发 / 长按延迟调的是「这一档怎么判」，归**这颗键**；松手这一族是「这一档还做什么」，
     *  归**这一块**（[Scene] 读草稿里那一块，单键 / 组合读自己那份）。执行侧同一个口径：
     *  长按被哪一块接管，抬手就执行**那一块**的松手格（见 `pickReleaseAction`）。
     */
    fun releaseSlot(cfg: Config): ReleaseSlot

    /** 把一次长按改动写回这一档所在的配置。 */
    fun withLongPress(
        cfg: Config,
        edit: LongPressEdit,
    ): Config

    /**
     * 这一页的**格子点不动**时该说的那句话。
     *
     *  只有**组合键**那一页有话说（那一组停用了）—— 它的闸门里带着 `ComboKey.enabled`
     *  （见 `bindingTargetEnabled`）。单键那一页由 `LinkCheck.disabledClickReason` 里
     *  「这一颗键停用」那一支说，场景动作那一页的闸门只看整页（见下）。
     *
     *  **系统场景那条路也跟着「这一份停用」走**（用户口径：按键 / 组合键禁用时，页面顶部那条
     *  入口、⋮ 菜单那一行、右下角那颗「进自定义命令」的 FAB、以及「自定义命令」小标题的跳转
     *  一并不给用）—— 判据只有一份（`ui/screen/binding/BindingPagerScreen.bindingTargetStopped`）。
     *  **场景块自己不在其中**：那一页的闸门只看整页，停用的块照样能改（见 `bindingTargetEnabled`）。
     */
    fun pageBlockedReason(cfg: Config): String?

    /** 一颗键的一档。 */
    data class Key(
        val id: KeyId,
        override val gesture: GestureTrigger,
    ) : BindingTarget {
        override fun actionIn(cfg: Config): Action = cfg.key(id).actionOf(gesture)

        override val what: String get() = gesture.id

        override fun ownerIn(cfg: Config): Any? = cfg.key(id)

        //  翻转之后「这一档的场景」不再靠筛选：一块自带三档，所以整张表都与这一档有关
        override fun rulesIn(cfg: Config): List<SceneRule> = cfg.key(id).scenes

        override fun longPress(cfg: Config): LongPressParams = cfg.key(id)

        override fun releaseSlot(cfg: Config): ReleaseSlot = ReleaseSlot.of(cfg.key(id))

        override fun withLongPress(
            cfg: Config,
            edit: LongPressEdit,
        ): Config = cfg.copy(keys = cfg.keys + (id to cfg.key(id).withLongPress(edit)))

        //  单键这一页没有「这一份停用」这一类说法：格子的闸门是 `keyEnabled`，点不动时由
        //  `LinkCheck.disabledClickReason` 统一说「按键已停用」那一句。
        override fun pageBlockedReason(cfg: Config): String? = null
    }

    /** 一档组合键的一档。 */
    data class Combo(
        val id: String,
        override val gesture: GestureTrigger,
    ) : BindingTarget {
        //  组合只有短按 / 长按两档：别的档位在 `ComboKey` 上没有槽位（界面也排不出那一页）
        override fun actionIn(cfg: Config): Action = cfg.combos.firstOrNull { it.id == id }?.actionOf(gesture) ?: Action.None

        override val what: String get() = "combo:$id:${gesture.id}"

        override fun ownerIn(cfg: Config): Any? = cfg.combos.firstOrNull { it.id == id }

        override fun rulesIn(cfg: Config): List<SceneRule> =
            cfg.combos
                .firstOrNull { it.id == id }
                ?.scenes
                .orEmpty()

        override val comboId: String get() = id

        override fun longPress(cfg: Config): LongPressParams? = cfg.combos.firstOrNull { it.id == id }

        override fun releaseSlot(cfg: Config): ReleaseSlot =
            cfg.combos.firstOrNull { it.id == id }?.let(ReleaseSlot::of) ?: ReleaseSlot.EMPTY

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

        override fun pageBlockedReason(cfg: Config): String? {
            val combo = cfg.combos.firstOrNull { it.id == id } ?: return null
            return if (combo.enabled) {
                null
            } else {
                //  这里要的是**用户认得出的名字**（`「侧键」×「电量 +」`），不是 `id` ——
                //  后者是 `com1` 那种内部标识，画到提示条上等于什么都没说。
                I18n.format(Strings.COMBO_DISABLED_ITEM, comboLabel(cfg, combo))
            }
        }
    }

    /**
     * 某个**场景块**的一档 —— 点「系统场景」页里那一行动作进来的那一页在改它。
     *
     * ## 为什么它带着草稿
     *
     *  另外两处写的是配置（`Config`），而场景块写的是**草稿**（`BranchDraft`）——
     *  那张表改完还要点右下角的「确认」才落盘（见 `BranchScreen`）。草稿属于某一页
     *  （页面级对象），不在 `Config` 里，所以「写哪儿」必须随目标一起带过来 ——
     *  否则这一层还得多一个「现在是不是在编场景」的旁路状态，两处判据迟早对不上。
     *
     * ## 「按键设置」那几张卡落在**谁**身上
     *
     *  场景块只是别人的某一档在某种条件下的取值，它没有自己的判据可调。参数归**那一位**：
     *  · 单键的场景表（[comboId] 为 `null`）⇒ 落到这颗键的 `KeyConfig`（与「按键绑定」页同一支）；
     *  · 组合键的场景表（[comboId] 非空）⇒ 落到那一组组合的 `ComboKey` —— 组合的短按 / 长按
     *    时序本来就是整组一套，与成员键各自那一份无关。
     *
     *  [ownerIn] 恒 `null`、[rulesIn] 恒空：这一页不摆「进系统场景」那一行（它就是从那里进来的）、
     *  也不摆独立设置（用户口径「没有额外设置」）。
     */
    data class Scene(
        /** 这一档写进哪份草稿 —— 页面级那一份（`SettingsPage.branchDraft.now()`）。 */
        val draft: BranchDraft,
        /** 第几块（`0` 起）。 */
        val at: Int,
        override val gesture: GestureTrigger,
        /**
         * 这一档所属的**组合键 id**（`null` = 这一趟在编的是单键的场景表）。
         *
         *  与 [Combo.comboId] 同一个含义、同一条用途：那几张「按键设置」卡要把改动写回
         *  「这一位长按怎么判」时，得知道是哪一组组合 —— 场景表自己不带这个字段，
         *  它挂在页面级的 `BranchDraftState.comboId` 上，由构造点递进来。
         */
        override val comboId: String? = null,
    ) : BindingTarget {
        /** 这一块现在长什么样（`null` = 已经被删掉了）。 */
        private val block: SceneRule? get() = draft.scenes.getOrNull(at)

        override fun actionIn(cfg: Config): Action = block?.actionOf(gesture) ?: Action.None

        //  日志里要能一眼分出「这一档是场景里的第几块」，与单键 / 组合那两串不撞
        override val what: String get() = "scene:$at:${gesture.id}"

        override fun ownerIn(cfg: Config): Any? = null

        override fun rulesIn(cfg: Config): List<SceneRule> = emptyList()

        override val sceneAt: Int get() = at

        //  与 [Combo.longPress] 同一支：组合场景读**那一组**的参数，单键场景读这颗键的
        override fun longPress(cfg: Config): LongPressParams? {
            val id = comboId
            if (id != null) return cfg.combos.firstOrNull { it.id == id }
            return draft.owner?.let { cfg.keyForId(it) }
        }

        //  松手那一族**不跟那颗键走**：它是「这一块还做什么」，读草稿里这一块（见 [releaseSlot]）
        override fun releaseSlot(cfg: Config): ReleaseSlot = block?.let(ReleaseSlot::of) ?: ReleaseSlot.EMPTY

        //  长按参数写回**那一位**（不是草稿）：那几张卡改的是「这一档怎么判」，与场景表无关
        override fun withLongPress(
            cfg: Config,
            edit: LongPressEdit,
        ): Config {
            val id = comboId
            if (id != null) {
                return cfg.copy(
                    combos = cfg.combos.map { if (it.id == id) it.withLongPress(edit) else it },
                )
            }
            val keyId = draft.owner?.let { cfg.keyIdOf(it) } ?: return cfg
            return cfg.copy(keys = cfg.keys + (keyId to cfg.key(keyId).withLongPress(edit)))
        }

        /**
         * `null` 恒成立：这一块停用了照样能改它的三档 —— 闸门只有整页那一道
         * （主开关 + 链路 + 只读），「这一块停用」只影响命中，不影响编辑。
         * `null` 也是「那一块已经不在草稿里了（页面正在退）」那一帧的答案。
         */
        override fun pageBlockedReason(cfg: Config): String? = null

        /**
         * 把 [action] 写进这一块的这一档 —— **只改草稿**，一个字节都不落盘。
         *
         *  @return `false` = 那一块已经不在了（草稿在别处被重置过）
         */
        fun bind(action: Action): Boolean {
            val old = block ?: return false
            draft.update(at, old.withAction(gesture, action))
            return true
        }

        /**
         * 把「松手执行额外动作」那一族写进这一块 —— **只改草稿**，点那一页的「确认」才落盘。
         *
         *  与 [bind] 同一个规矩（见 `BranchDraft` 的说明）：这一页编的是草稿，提前落盘等于
         *  把用户还没确认的改动写死进配置。
         *
         *  @return `false` = 那一块已经不在了（草稿在别处被重置过）
         */
        fun bindRelease(
            on: Boolean? = null,
            action: Action? = null,
        ): Boolean {
            val old = block ?: return false
            draft.update(
                at,
                old.copy(
                    releaseOn = on ?: old.releaseOn,
                    release = action ?: old.release,
                ),
            )
            return true
        }

        /**
         * 「单击后长按」的松手那两格（`chainReleaseOn` / `chainRelease`）—— 与 [bindRelease] 同形。
         *
         *  分成两支而不是加参数：两者是**同一块里两个不同的档**，揉进一个函数会让
         *  「这次改的是哪一档」只能靠调用方记住。
         */
        fun bindChainRelease(
            on: Boolean? = null,
            action: Action? = null,
        ): Boolean {
            val old = block ?: return false
            draft.update(
                at,
                old.copy(
                    chainReleaseOn = on ?: old.chainReleaseOn,
                    chainRelease = action ?: old.chainRelease,
                ),
            )
            return true
        }
    }
}

/**
 * 「松手执行额外动作」那一族的现行值 —— 开关 + 那一格的动作。
 *
 *  为什么要这一层：同一张卡在三种目标上画，但这一族**读的地方不一样**（键 / 组合 / 草稿里那一块），
 *  而卡片不该知道「这次是在编场景」。三个工厂函数各认一种来源。
 *
 *  [action] 可能是 [Action.Follow]（老配置里存进来过）：卡片按「无操作」念，与执行侧同一个口径
 *  （见 `LongPressCard` 那一行）。
 */
internal class ReleaseSlot(
    val on: Boolean,
    val action: Action,
) {
    companion object {
        /** 这一份已经不在了（键被删 / 组合被删 / 草稿那一块没了）—— 按「没配」念。 */
        val EMPTY = ReleaseSlot(false, Action.None)

        fun of(key: KeyConfig): ReleaseSlot = ReleaseSlot(key.longReleaseOn, key.longRelease)

        fun of(combo: ComboKey): ReleaseSlot = ReleaseSlot(combo.longReleaseOn, combo.longRelease)

        fun of(block: SceneRule): ReleaseSlot = ReleaseSlot(block.releaseOn, block.release)
    }
}

/**
 * 这一趟在编**哪一颗键** —— 场景表那四页（系统场景 / 场景动作 / 条件菜单 / 退出判据）共用一个答案。
 *
 *  为什么落在这一层（`ui/` 而不是 `ui/screen/command/`）：问它的第一个人不是某一屏，而是
 *  **页面模型** —— [openBranchesFor] 要按这颗键的档位清单换算页号。
 *  它原先住在 `ui/screen/command/CommandBranches.kt`，于是 `ui/write/BindingTarget.kt` 反向 import 了
 *  具体屏，分层审计（`ui → ui.screen` 那条）会拦下：共用件 / 页面模型不许认识屏。
 *  搬到这里之后两个方向都是顺的 —— 屏认识页面模型，页面模型只认识状态（`KeyWatchState`）。
 */
internal fun SettingsPage.branchKeyNow(): KeyId = KeyWatchState.editing(configState.config.value)

/**
 * 进「系统场景」编**这一档** —— 两页共用这一支。
 *
 *  「为谁编」与「编哪一档」都由 target 给：单键清掉组合目标、组合带上自己的 id。
 *  之前在单键页与组合页各写了一遍，漏一处就会「进去编的是上一次那一档」。
 */
internal fun SettingsPage.openBranchesFor(target: BindingTarget) {
    branchDraft.comboId = target.comboId
    //  `branchGesture` 是「按键绑定」页的**页号**（调试通道与「这一页停在哪一档」共用那一位）：
    //  按**这一份的当前清单**换算（`GestureTrigger.slotsOn`，三击 / 四击关着时就只有三页），
    //  与分页器的页序同一份清单。组合键那两档在全集里也找得到（短按 0 / 长按 2）。
    val slots =
        if (target.comboId != null) {
            GestureTrigger.KEY_SLOTS
        } else {
            val key = configState.config.value.key(branchKeyNow())
            GestureTrigger.slotsOn(key.extraClicksOn, key.clickThenLongOn, key.doubleClickOn)
        }
    branchGesture = slots.indexOf(target.gesture).coerceAtLeast(0)
    XLog.w("进「$BRANCHES_TITLE」页：${target.what}")
    navTo(Screen.BRANCHES)
}
