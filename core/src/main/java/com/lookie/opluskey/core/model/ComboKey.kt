package com.lookie.opluskey.core.model

/**
 * 组合按键：**几颗键一起按下**（或在一个窗口内先后按下）时执行的动作。
 *
 * ## 成员数：界面最多三颗
 *
 *  [MIN_KEYS] = 2 是下限（一颗不叫组合），[MAX_KEYS] = 3 是**界面 / 收集**那一侧的上限，
 *  [MAX_JUDGE_KEYS] 才是判定、落盘、广播三处认的上限（宽松挡垃圾用）。
 *  两个上限分开：界面只造得出三颗，而判定侧从三颗起就走「多成员凑齐」那条路
 *  （`ComboDetector.multiFor`），再多也跑得通 —— 手改配置文件写进来的四颗以上照常生效。
 *
 * ## 只支持短按与长按
 *
 * 双击这一档在组合键上不存在：判双击要的是「两次按压都落在同一个窗口内」，而组合键先要判
 * 「这几颗键同时按着」，两个窗口叠在一起没有能讲清楚的口径。所以 [actionOf] 对
 * [Gesture.DOUBLE] 恒返回 [Action.None] —— 不是「还没实现」，是这个档位在这里没有定义。
 *
 * ## 身份是 [keys] 这个集合，不是按下的顺序
 *
 * 两颗键「同时按」时，上报顺序由驱动决定、用户也控制不了。拿顺序当判据等于让配置依赖硬件细节，
 * 所以判等只看集合（[matches]）。
 *
 * ## 与单键的关系
 *
 * 参与组合的键同时也是普通的键：它们各自的单击 / 双击 / 长按照常在 `KeyConfig` 里配。谁先响应、
 * 要不要把单键那一下吞掉，是**判定侧**的事（不在本类）—— 这里只回答「这几颗键凑齐了是哪一档」。
 */
data class ComboKey(
    /** 稳定身份（`cmb1` `cmb2`）—— 与 `KeyId.id` 同一套用法：改成员不换身份。 */
    val id: String,
    /** 参与的键（`KeyId.id`，去重后 [MIN_KEYS]..[MAX_JUDGE_KEYS] 颗）。 */
    val keys: List<String>,
    val enabled: Boolean = true,
    /** 短按：按下之后在 [longPressMs] 之内松开。 */
    val single: Action = Action.None,
    /** 长按：一起按住超过 [longPressMs]。 */
    val long: Action = Action.None,
    override val longPressMs: Int = KeyConfig.DEFAULT_LONG_PRESS_MS,
    /**
     * 「长按连续触发」—— 两颗键一起按住不放时，按 [longRepeatIntervalMs] 反复执行 [long]。
     *
     *  与单键那一档同一套语义（`KeyConfig.longRepeat`）：松手就停，抬手事件丢了有上限兜底。
     *  默认关：按住不放重复执行是个响得很大的行为，不该一开始就有。
     */
    override val longRepeat: Boolean = false,
    /** 连续触发的间隔（毫秒）—— 只在 [longRepeat] 为真时有意义。 */
    override val longRepeatIntervalMs: Int = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
    /** 连续触发时每一次重复都振动（关 = 只在判出长按那一下振一次）。 */
    override val longRepeatVibrate: Boolean = false,
    /**
     * 「长按**松手才触发**」—— 与单键 `KeyConfig.longFireOnRelease` 同一档语义。
     *
     *  开着时长按那一下不立即执行，抬手时补发一次；与 [longRelease] 互斥（同一刻只能有一个）。
     */
    override val longFireOnRelease: Boolean = false,
    /** 「松手执行额外动作」的开关 —— 与单键 `KeyConfig.longReleaseOn` 同一档语义。 */
    override val longReleaseOn: Boolean = false,
    /** 松手那一档执行的动作（`Action.None` = 没挑）。 */
    override val longRelease: Action = Action.None,
    /**
     * **系统场景** —— 与单键 `KeyConfig.scenes` 同一套 [SceneRule]、同一个判定口径。
     *
     *  按顺序找「条件成立、且这一档有值」的第一块；一块都不中才走 [actionOf] 那一档绑定。
     *  空表（默认）⇒ 行为与没有这一项完全相同 —— 老配置读回来就是这个状态。
     *
     *  为什么组合键也值得有：组合键本身就是「我特意按了两颗键」，它比单键更常需要
     *  「在什么情况下才做」的区分（比如「在公司电脑上才切窗口」）。
     *
     *  手势只有 [Gesture.SINGLE] / [Gesture.LONG] 两档（见类注释），所以块里的 [SceneRule.double]
     *  在这条路上永远不会被读到；手改配置写进去也只是存着。
     */
    val scenes: List<SceneRule> = emptyList(),
    /**
     * 「这一档用自己那套公共项」—— 与单键 `KeyConfig.useOwnSettings` **同一个契约**
     * （[OwnSettings]），执行侧不区分单键还是组合。
     *
     *  默认关 = 跟随公共设置；开着时下面两位说了算。
     */
    override val useOwnSettings: Boolean = false,
    /** 这一档自己的「使用 ColorOS 接口」—— 只在 [useOwnSettings] 开着时算数。 */
    override val colorOsApi: Boolean = GlobalConfig.DEFAULT_COLOR_OS_API,
    /** 这一档自己的「振动反馈」—— 只在 [useOwnSettings] 开着时算数。 */
    override val vibrate: Boolean = false,
    /** 这一档自己的「振动强度」—— 只在 [useOwnSettings] 开着时算数。 */
    override val vibrateStrength: Int = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
    /** 这一档自己的「执行时提示」—— 只在 [useOwnSettings] 开着时算数。 */
    override val toastOnRun: Boolean = GlobalConfig.DEFAULT_TOAST_ON_RUN,
    /** 这一档自己的「提示走流体云」—— 只在 [useOwnSettings] 开着时算数。 */
    override val toastFluid: Boolean = GlobalConfig.DEFAULT_TOAST_FLUID,
    /** 这一档自己的提示文案模板（空串 = 默认那句）—— 只在 [useOwnSettings] 开着时算数。 */
    override val toastTemplate: String = GlobalConfig.DEFAULT_TOAST_TEMPLATE,
    /** 上面那几项被公共设置播过种没有 —— 见 [OwnSettings.ownSettingsSeeded]。 */
    override val ownSettingsSeeded: Boolean = false,
    /**
     * **这一组自己的**「无操作时执行按键命令」—— `null` = 跟随公共设置。
     *
     *  与 `GlobalConfig.comboPassthrough` 是同一件事，只是这一组可以另设一份：
     *  「配了 A+B，但只给长按绑了动作」时，短按到底要不要让两颗键各干各的，
     *  不同组往往不一样（临时组合希望透传、专用组合希望吞掉）。
     *
     *  **受 `useOwnSettings` 管**，与那几项同一条规则：关着就整份跟公共，开着才用这一位。
     *  打开独立设置那一刻这一位会被**播成当时的公共值**（`KeySettingsScreen.withSeededComboFields`），
     *  所以「开着独立设置却还在跟随公共」只会出现在还没被拨过的老配置上（`null` 就是那一档）。
     *
     *  **只在 [windowOn] 那位开着时算数**：窗口关掉（这一组只认按住）时这一位不参与取值 ——
     *  那两条按法各有自己明确的槽，不存在「按出来了但没绑动作」这个形状。
     *  取值只走 `Config.comboPassthroughFor`，执行侧不自己看全局。
     */
    val passthrough: Boolean? = null,
    /**
     * 这一组自己的「两颗键按下之间最多隔多久」（毫秒）。
     *
     *  这个数同时管两件事：**算不算同时按下**，以及**第一颗键要等多久才回落单击**。
     *  一个数管两件事就必然会打架 —— 三颗的组合需要宽一点，而两颗的调宽了会让那颗键
     *  整套单击都变钝。所以允许每组各设一份。
     *
     *  它量的是**相邻两颗**之间：三颗那一档整串的容忍跨度是它 × (成员数-1)（`Config.comboSpanFor`），
     *  所以三颗也不必把它调到「两颗手感」的两倍以上。
     *
     *  **同样受 `useOwnSettings` 管**：关着整份看公共；打开那一刻被播成当时的公共值。
     *  [WINDOW_FOLLOW_GLOBAL]（0）只剩老配置会读到，界面上也没有「拖回跟随」那一档 ——
     *  要回公共就关掉这一组的总开关（`Config.comboWindowFor` 的第一支）。
     *
     *  取值一律走 `Config.comboWindowFor`（判定侧与界面都只走这一处，谁也不自己看全局）。
     */
    val windowMs: Int = WINDOW_FOLLOW_GLOBAL,
    /**
     * **认不认「窗口内等齐成员」这一种按法** —— `null` = 跟随公共设置（`GlobalConfig.comboWindowOn`）。
     *
     *  与 [holdOn] 是**两条独立的触发方式**，各有各的开关，谁也不取代谁：这一位关掉只是不再
     *  按时间配这一档，按住那条照旧可用。两位都关 ⇒ 这一档组合永远不会成立（配置允许写出来，
     *  判定侧只是永远配不上）。
     *
     *  受 `useOwnSettings` 管，与 [windowMs] 同一条：关着整份看公共；打开独立设置那一刻这一位
     *  会被**播成当时的公共值**（`KeySettingsScreen.withSeededComboFields`）。
     *
     *  取值一律走 `Config.comboWindowOnFor` —— 判定侧与界面都只走这一处。
     */
    val windowOn: Boolean? = null,
    /**
     * **除了「窗口内等齐成员」，还接受「按住一个成员、再按剩下的成员」** —— 两种触发方式并存，
     *  **互不取代**：窗口那条判据（[windowMs]）照旧，这里只是多开一条路。
     *
     *  `null` = 跟随公共设置（`GlobalConfig.comboHoldOn`），与 [windowOn] 同一套覆盖规则。
     *
     *  按住这一条只对**两个成员**成立（三颗及以上没有「哪一颗先按住」这个唯一的起点）。
     *  三种触发方式各有自己的动作槽：
     *
     *  · 窗口内等齐 ⇒ [single] / [long]；
     *  · 先按住第一颗、再按第二颗 ⇒ [singleA] / [longA]；
     *  · 先按住第二颗、再按第一颗 ⇒ [singleB] / [longB]。
     *
     *  为什么三套分开而不是共用：窗口那条判的是「同时按」，按住判的是「一前一后」——
     *  同一组手势在两种按法下要做的事往往不一样（同时按是快捷开关，按住是「先按这颗再按那颗」的
     *  组合拳）。共用一套的话，用户改了一种按法，另一种也跟着变。
     *
     *  上界是第一颗自己的长按：判出来之后它已经执行过自己的动作了，再认组合就是一次按压
     *  两个动作（判据在 `ComboDetector` 的 `longPressPassed`）。
     *
     *  老文件没有这一项 ⇒ `null`（跟随公共，公共默认关）⇒ 判定与这一位出现之前逐字一致。
     */
    val holdOn: Boolean? = null,
    /**
     * **手动加过的那几种按住顺序** —— 取值是 [HOLD_FIRST] / [HOLD_SECOND]。
     *
     *  按住那两种顺序不再「开一位就自动都有」：用户得自己往这一档里加（界面在「按法」菜单里
     *  提供「添加顺序」）。理由是每加一条顺序就要把那颗键改成一个特殊的长按时机
     *  （「松手时触发」，见 `HoldGestureAsk`）—— 这是要用户点头的事，不能替他默默改。
     *
     *  空表 = 一条都没加 ⇒ 判定侧不认按住那条（哪怕 [holdOn] 开着）；老文件同样读成空表
     *  —— 这一位出现之前根本没有「按住顺序」这个概念，而按住手势本身也还没发布。
     */
    val holdOrders: List<Int> = emptyList(),
    /**
     * 这一组自己的「**首键要先按住多久**」（毫秒）—— 按住手势的下限。
     *
     *  含义：先按住的那颗必须**按满这么久、且还没抬手**，第二颗在这之后落下才算这一档组合。
     *  它把「一起按」（两颗键相差 0~40ms，谁先上报看硬件）与「先按住一颗再按另一颗」分开：
     *  没有这条下限时，一起按会按物理先后落进某一种顺序里 —— 而那个顺序用户未必加过，
     *  于是同一物理动作有时命中、有时整条落空。
     *
     *  **受 `useOwnSettings` 管**，与 [windowMs] 同一条：关着整份看公共
     *  （`GlobalConfig.comboHoldMinMs`）；打开那一刻被播成当时的公共值。
     *  [HOLD_MIN_FOLLOW_GLOBAL]（0）只剩老配置会读到。
     *
     *  取值一律走 `Config.comboHoldMinFor`（判定侧与界面都只走这一处）。
     */
    val holdMinMs: Int = HOLD_MIN_FOLLOW_GLOBAL,
    /** 按住手势「先按住 **第一颗**」那一套（见 [holdOn]）。 */
    val singleA: Action = Action.None,
    /** 见 [singleA]。 */
    val longA: Action = Action.None,
    /** 按住手势「先按住 **第二颗**」那一套（见 [holdOn]）。 */
    val singleB: Action = Action.None,
    /** 见 [singleB]。 */
    val longB: Action = Action.None,
) : OwnSettings,
    LongPressParams {
    /** 这一档现在算不算数 —— 关掉了、或成员不足两键（配置文件被手改坏）都不算。 */
    val isUsable: Boolean
        get() = enabled && keys.size >= MIN_KEYS

    /**
     * [trigger] 这一档的动作。
     *
     *  组合只认单击 / 长按两档（[COMBO_SLOTS]）：双击与第 3 击起那些档位在这一层没有槽位，
     *  问到它们就是「这一档没定义」—— **不退回单击**，否则按两下会执行一下。
     *  松手那一档回 [longRelease]（与单键 `KeyConfig.actionOf` 同一口径）。
     */
    fun actionOf(trigger: GestureTrigger): Action =
        when (trigger) {
            is GestureTrigger.Click -> if (trigger.count == 1) single else Action.None

            GestureTrigger.LongPress -> long

            GestureTrigger.Release -> longRelease

            //  组合没有「单击后长按」：它的两段都建立在单键连击上（见 [COMBO_SLOTS]）
            GestureTrigger.ClickThenLong, GestureTrigger.ClickThenLongRelease -> Action.None
        }

    /**
     * 这一种按住顺序**加过没有** —— 判定侧用它当闸（见 [holdOrders]），界面用它决定那一行是
     * 「选择」还是「添加」。
     */
    fun hasHoldOrder(hold: Int): Boolean = hold in holdOrders

    /**
     * 按住那几套里**有没有绑过动作** —— `ChordGuard` 判「这一对该不该吞掉 ROM 的判定」时用。
     *
     *  少了这一位，只给按住手势配了动作的组合在注入侧看起来就是「一个动作都没绑」，
     *  于是按「透传开着」放行 ⇒ 系统那一下照常发生、模块这一下也不执行（真机上就是「按了没反应」）。
     */
    val anyHoldBound: Boolean
        get() = singleA.isBound || longA.isBound || singleB.isBound || longB.isBound

    /**
     * **按「这一下是怎么按出来的」取动作** —— `hold = 0` 是窗口那条，`1` 是先按住第一颗，
     * `2` 是先按住第二颗（见 [holdOn]）。
     *
     *  为什么不并进 [actionOf]：那个方法的语义是「这一档手势执行什么」，而这里多一个维度
     *  （同一档手势有三种按法）。并进去的话，所有只关心手势的调用点都要多传一个它用不上的参数。
     */
    fun actionOfHold(
        trigger: GestureTrigger,
        hold: Int,
    ): Action =
        when (hold) {
            HOLD_FIRST -> slotAction(trigger, singleA, longA)

            HOLD_SECOND -> slotAction(trigger, singleB, longB)

            //  窗口那条（以及任何认不出的取值）落到原来那两个槽
            else -> actionOf(trigger)
        }

    /**
     * 一档手势在**某一对动作槽**（短按一颗 + 长按一颗）上取什么 —— 按住那两种按法共用这一支。
     *
     *  判据与 [actionOf] 逐条对齐（双击及第 3 击起仍然「没有定义」，不退回单击），只是把
     *  「落到哪两个槽」交给调用方：三种按法的差别全在槽位上，不在判据上。
     *
     *  松手那两档回 [Action.None]：松手执行额外动作**不按按法分化**（`longRelease` 只有一个槽，
     *  按住那两种按法按出来的一次按压，松手仍走这一组自己的那一格）。
     */
    private fun slotAction(
        trigger: GestureTrigger,
        singleAction: Action,
        longAction: Action,
    ): Action =
        when (trigger) {
            is GestureTrigger.Click -> if (trigger.count == 1) singleAction else Action.None
            GestureTrigger.LongPress -> longAction
            else -> Action.None
        }

    /**
     * 松手那一下**真的有事可做** —— 开关开着、挑过动作、且不是「松手才触发」那条。
     *
     *  与单键 `KeyConfig.releaseBound` 同一份判据（那一边的解释更细）：四位缺一位都不算，
     *  其中第一位是「**长按这一档本身**有没有动作」—— 长按是无操作时，松手再补一个动作
     *  没有主语（界面那一侧同样把那一族压灰，见 `LongPressSpec.releaseAvailable`）。
     *  场景块里配的松手动作也算一位 —— 组合的场景表**本来就随广播带过来**（`ComboWire`），
     *  所以这一位在注入侧能就地算出来，不必像单键那样另开一个广播字段。
     */
    override val releaseBound: Boolean
        get() =
            (long.isBound || scenes.any { (it.actions[GestureTrigger.LongPress] ?: Action.None).isBound }) &&
                !longFireOnRelease &&
                ((longReleaseOn && longRelease.isBound) || scenes.any { it.hasRelease })

    /**
     * **带场景表**的取动作：先看 [scenes]（顺序、「条件成立且这一档有值」的第一块赢），
     * 都不中才落到 [actionOf]。
     *
     *  实现在 [pickAction] 一处 —— 与单键 `KeyConfig.actionFor` 是**同一个函数**。
     *
     *  `matches` 由调用方给（读系统开关的那一层，见 `ConditionProbe`）：这一层不认识系统接口。
     */
    fun resolve(
        trigger: GestureTrigger,
        matches: (BranchCondition) -> Boolean,
    ): Action = pickAction(scenes, trigger, actionOf(trigger), matches)

    /**
     * 这一档**有没有被某个场景块接管** —— 「松手执行额外动作」要靠它让位。
     *
     *  与 [resolve] 同一条判据（条件成立 + 这一档有值），只是不要动作、只要一个是非：
     *  长按那一档被场景接管了，松手那一刻就不该再补一刀。
     */
    fun sceneTakes(
        trigger: GestureTrigger,
        matches: (BranchCondition) -> Boolean,
    ): Boolean = sceneTakes(scenes, trigger, matches)

    /**
     * **松手那一刻执行什么** —— 长按被某一块接管时用那一块的松手格，否则用 [longRelease]。
     *
     *  实现在 [pickReleaseAction] 一处 —— 与单键 `KeyConfig` 那一支是**同一个函数**
     *  （「一次按压一个结果」：块接管了长按，就不再补这一组自己的松手动作）。
     *  [longReleaseOn] 关着同样传 `None`：开关与动作是两位，关掉之后动作还留着。
     */
    fun resolveRelease(matches: (BranchCondition) -> Boolean): Action =
        pickReleaseAction(scenes, if (longReleaseOn) longRelease else Action.None, matches)

    /** [down] 里包含这一档的全部成员吗（不看顺序、允许同时按着别的键）。 */
    fun matches(down: Set<String>): Boolean = keys.all { it in down }

    companion object {
        /** 组合键 id 的前缀：`cmb1` / `cmb2` ……（序号由收集侧维护，只增不减）。 */
        const val ID_PREFIX = "cmb"

        /** 至少两颗 —— 一颗不叫组合。 */
        const val MIN_KEYS = 2

        /** [windowMs] 取这个值表示「跟随公共设置」—— 它不是「等 0 毫秒」。 */
        const val WINDOW_FOLLOW_GLOBAL = 0

        /** [holdMinMs] 取这个值表示「跟随公共设置」（同上：不是「按住 0 毫秒」）。 */
        const val HOLD_MIN_FOLLOW_GLOBAL = 0

        /**
         * 「这一下是怎么按出来的」—— 窗口内等齐 / 先按住第一颗 / 先按住第二颗。
         *
         *  三个取值同时是**配置列名之外的运行时编号**：判定侧把它随广播带下去，执行侧按它取
         *  `single`·`singleA`·`singleB` 那一族槽（见 [actionOfHold]）。数字进线格式，
         *  所以只有「加在末尾」这一种改法。
         */
        const val HOLD_WINDOW = 0
        const val HOLD_FIRST = 1
        const val HOLD_SECOND = 2

        /**
         * 成员上限：**三颗**（用户口径）。两颗仍是最常见的那一档，三颗是给「三键齐按」留的口子。
         *
         *  这是**界面与收集**那一侧的上限：收集入口（`ComboActions` / `KeysScreen`）按它拦，
         *  「最多 %d 颗」那条提示也用它。判定侧不吃这个数 —— 那边认到 [MAX_JUDGE_KEYS]。
         *
         *  两个上限为什么分开：判定侧从三颗起就走「多成员凑齐」那条路（`ComboDetector.multiFor`），
         *  界面放开到三颗之后那条路才真的有人走。数据层要是跟着界面卡在一个小数字上，
         *  解析时就被截断，判定侧永远见不到多成员形态。
         */
        const val MAX_KEYS = 3

        /**
         * **判定侧**认的成员上限：九颗 —— 解析（`ConfigDto` / `ComboWire`）、判定
         * （`ComboDetector` 的多成员路径）、日志（`ComboPlan`）三处都按它收口。
         *
         *  取 9 只是个「挡垃圾」的宽松上限：整机能收集到的键（内置 1 颗 + 自定义 5 颗）本来也
         *  到不了这个数，真正要防的是手改坏的文件里写几十颗，让一档组合吃掉整块键盘。
         *  下限仍是 [MIN_KEYS]：两颗那一档走 `ComboDetector` 的两两配对，3 颗起走多成员凑齐。
         */
        const val MAX_JUDGE_KEYS = 9

        /** 第 [seq] 档组合键的 id。 */
        fun idOf(seq: Int): String = ID_PREFIX + seq
    }
}
