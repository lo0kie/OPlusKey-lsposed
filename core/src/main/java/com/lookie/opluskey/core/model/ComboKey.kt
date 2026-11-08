package com.lookie.opluskey.core.model

/**
 * 组合按键：**几颗键一起按下**（或在一个窗口内先后按下）时执行的动作。
 *
 * ## 成员数：界面两颗，判定侧可以更多
 *
 *  [MIN_KEYS] = 2 是下限（一颗不叫组合），[MAX_KEYS] = 2 是**界面/收集**的上限（用户口径：
 *  一个组合就是两颗键一起按），[MAX_JUDGE_KEYS] 才是判定、落盘、广播三处认的上限。
 *  两个上限分开是这一轮接线的结果：三颗以上的组合在判定与执行里跑得通，但界面上造不出来
 *  （勾到第二颗就停）—— 手改配置文件写进来的三颗组合照常生效。
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
     * **条件执行**（与单键 `KeyConfig.rules` 同一套 [StateRule]、同一个判定口径）。
     *
     *  按顺序匹配、第一条成立的赢；一条都不中才走 [actionOf] 那一档绑定。
     *  空表（默认）⇒ 行为与没有这一项完全相同 —— 老配置读回来就是这个状态。
     *
     *  为什么组合键也值得有：组合键本身就是「我特意按了两颗键」，它比单键更常需要
     *  「在什么情况下才做」的区分（比如「在公司电脑上才切窗口」）。
     *
     *  手势只有 [Gesture.SINGLE] / [Gesture.LONG] 两档（见类注释），所以这里的规则也只认这两档；
     *  手改配置写进来的 `double` 规则永远不会命中（[actionOf] 对双击恒 `None` 同一条道理）。
     */
    val rules: List<StateRule> = emptyList(),
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
    /** 这一档自己的提示文案模板（空串 = 默认那句）—— 只在 [useOwnSettings] 开着时算数。 */
    override val toastTemplate: String = GlobalConfig.DEFAULT_TOAST_TEMPLATE,
    /** 上面那五项被公共设置播过种没有 —— 见 [OwnSettings.ownSettingsSeeded]。 */
    override val ownSettingsSeeded: Boolean = false,
    /**
     * **这一组自己的**「无操作时执行按键命令」—— `null` = 跟随公共设置。
     *
     *  与 `GlobalConfig.comboPassthrough` 是同一件事，只是这一组可以另设一份：
     *  「配了 A+B，但只给长按绑了动作」时，短按到底要不要让两颗键各干各的，
     *  不同组往往不一样（临时组合希望透传、专用组合希望吞掉）。
     *
     *  **受 `useOwnSettings` 管**，与那五项同一条规则：关着就整份跟公共，开着才用这一位。
     *  打开独立设置那一刻这一位会被**播成当时的公共值**（`KeySettingsScreen.withSeededComboFields`），
     *  所以「开着独立设置却还在跟随公共」只会出现在还没被拨过的老配置上（`null` 就是那一档）。
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
     *  **同样受 `useOwnSettings` 管**：关着整份看公共；打开那一刻被播成当时的公共值。
     *  [WINDOW_FOLLOW_GLOBAL]（0）只剩老配置会读到，界面上也没有「拖回跟随」那一档 ——
     *  要回公共就关掉这一组的总开关（`Config.comboWindowFor` 的第一支）。
     *
     *  取值一律走 `Config.comboWindowFor`（判定侧与界面都只走这一处，谁也不自己看全局）。
     */
    val windowMs: Int = WINDOW_FOLLOW_GLOBAL,
) : OwnSettings,
    LongPressParams {
    /** 这一档现在算不算数 —— 关掉了、或成员不足两键（配置文件被手改坏）都不算。 */
    val isUsable: Boolean
        get() = enabled && keys.size >= MIN_KEYS

    /** [gesture] 这一档的动作；双击恒为 `None`（见类注释）。 */
    fun actionOf(gesture: Gesture): Action =
        when (gesture) {
            Gesture.SINGLE -> single
            Gesture.LONG -> long
            Gesture.DOUBLE -> Action.None
        }

    /**
     * 松手那一下**真的有事可做** —— 开关开着、挑过动作、且不是「松手才触发」那条。
     *
     *  与单键 `KeyConfig.releaseBound` 同一份判据（那一边的解释更细）：三位缺一位都不算。
     */
    override val releaseBound: Boolean
        get() = longReleaseOn && longRelease.isBound && !longFireOnRelease

    /**
     * **带条件表**的取动作：先看 [rules]（顺序、第一条赢），都不中才落到 [actionOf]。
     *
     *  实现在 [pickAction] 一处 —— 与单键 `KeyConfig.actionFor` 是**同一个函数**。
     *  原先两边各写了一遍逐字相同的 `firstOrNull`，注释给的理由是「合并得先让两个类共一个
     *  父类型」；那个理由不成立：要的只是「一张表 + 一个兜底动作」，抽成顶层函数就够。
     *
     *  `matches` 由调用方给（读系统开关的那一层，见 `ConditionProbe`）：这一层不认识系统接口。
     */
    fun resolve(
        gesture: Gesture,
        matches: (BranchCondition) -> Boolean,
    ): Action = pickAction(rules, gesture, actionOf(gesture), matches)

    /** [down] 里包含这一档的全部成员吗（不看顺序、允许同时按着别的键）。 */
    fun matches(down: Set<String>): Boolean = keys.all { it in down }

    companion object {
        /** 组合键 id 的前缀：`cmb1` / `cmb2` ……（序号由收集侧维护，只增不减）。 */
        const val ID_PREFIX = "cmb"

        /** 至少两颗 —— 一颗不叫组合。 */
        const val MIN_KEYS = 2

        /** [windowMs] 取这个值表示「跟随公共设置」—— 它不是「等 0 毫秒」。 */
        const val WINDOW_FOLLOW_GLOBAL = 0

        /**
         * 成员上限：两颗（用户口径 —— 一个组合就是**两颗键一起按**，不多不少）。
         *
         *  这是**界面与收集**那一侧的上限：收集入口（`ComboActions` / `KeysScreen`）按它拦，
         *  「最多 %d 颗」那条提示也用它。判定侧不吃这个数 —— 那边认到 [MAX_JUDGE_KEYS]。
         *
         *  两个上限之所以要分开：三颗以上的组合在判定、执行、日志里都已经跑得通，
         *  但**界面上暂时不加入口**（用户口径未定）。数据层跟着界面一起卡死在两颗的话，
         *  接线就等于白接 —— 解析时就被截成两颗，判定侧永远见不到多成员形态。
         */
        const val MAX_KEYS = 2

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
