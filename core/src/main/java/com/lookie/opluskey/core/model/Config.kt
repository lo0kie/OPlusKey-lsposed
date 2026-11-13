package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.rom.RomProfile

// 配置的数据结构：三种手势、单键配置、全局配置、整份配置。

/** 三种手势。 */
enum class Gesture(
    val id: String,
    val label: String,
) {
    SINGLE("single", "单击"),
    DOUBLE("double", "双击"),
    LONG("long", "长按"),
    ;

    companion object {
        /**
         * 按 [id] 找手势 —— 配置与广播里存的一直是 id（`single` / `double` / `long`，见
         * `ConfigFields` 的 `<键>_<档>`），不认枚举名。认不出返回 `null`，由调用方丢掉那一条。
         */
        fun ofId(id: String?): Gesture? = entries.firstOrNull { it.id == id }
    }
}

/** 一个键的完整配置。 */
data class KeyConfig(
    val id: KeyId,
    val enabled: Boolean,
    val single: Action,
    val double: Action,
    val long: Action,
    override val longPressMs: Int,
    override val longRepeat: Boolean,
    override val longRepeatIntervalMs: Int,
    /**
     * 「连续触发时振动」—— 连续触发的每一次重复都振一下。
     *
     *  只在 [longRepeat] 为真时有意义（界面那一行也只在那时出现）。默认 false = 只在长按
     *  被判出来的那一下振一次，重复不振动。
     */
    override val longRepeatVibrate: Boolean = false,
    /**
     * **「松手执行额外动作」** —— 长按之后松手那一刻要执行的**另一个动作**（与 [long] 分开配置）。
     *
     *  两个事件是分开的：到长按时长先执行 [long]（照旧），松手时再执行这一档。判定侧只在这一按
     *  **已经判成长按**时才发那一条（`GestureTrigger.Release`），执行侧按这个字段取动作。
     *
     *  默认 `Action.None` = 不发那条广播、行为与这一位出现之前完全一致。
     *
     *  与 [longRepeat] **不冲突**：按住期间照常连续触发，松手那一刻再执行这一档 —— 一个管
     *  「按住时发生几次」，一个管「抬手时再做一件」，两者可以同时开着。
     */
    override val longRelease: Action = Action.None,
    /**
     * 「松手执行额外动作」的**开关** —— 打开时界面才露出「额外动作」那一行。
     *
     *  与 [longRelease] 分成两位：开关开着、动作还没挑是合法状态（松手什么都不做）。合成一位
     *  （「有动作 = 开」）的话，用户在开关上点一下就会立刻执行一个他没挑过的动作。
     */
    override val longReleaseOn: Boolean = false,
    /**
     * **「松手才触发」** —— 长按动作本身等**抬手**那一刻才执行（默认是到点即触发）。
     *
     *  「什么时候执行长按这一档」只有两种答案，所以这两种**互斥**（同时开着无法定义「哪一下执行什么」）：
     *  · 到点即触发（默认，本条关闭）；
     *  · 到点只标记、**松手才执行**（本条）。
     *  另外 [longRelease]（松手执行**另一个**动作）与这条也互斥 —— 它同样是「松手那一下」的语义。
     *  它与 [longRepeat] 也互斥：到点没有执行过，就没有可重复的起点。
     *
     *  互斥在数据层收口：`longRepeats` / `releaseBound` 都把它排掉，于是判定侧与界面只读那两位，
     *  不必各自再判一次。
     */
    override val longFireOnRelease: Boolean = false,
    /**
     * 「单击延迟」—— **这颗键自己的**双击窗口（毫秒）。
     *
     *  以前它是 `GlobalConfig` 的一项（所有键共用一份），自定义键出现之后就成了
     *  「改一颗键的判定、另一颗跟着变」—— 用户口径是每颗键各管各的。
     */
    val doubleClickMs: Int = DEFAULT_DOUBLE_CLICK_MS,
    /**
     * **这颗键只参与组合** —— 单键手势（单击 / 双击 / 长按）整条不接管，只留「等组合的另一半」。
     *
     *  默认 false = 与这一位出现之前完全一致。开着时：
     *  · 按下**先吞掉**（压一个窗口），再问组合：凑成了就永久吞，没凑成由注入侧把原生短按补发回去；
     *  · 于是**组合成立时原生事件不会发生** —— 这是 2026-11-13 改的。之前是「先放行、成了组合再补
     *    一次」，那颗键的原生动作（音量 ± / 电源）已经跑过一遍，撤不回来；
     *  · 代价：**单按会晚一个窗口**才出效果（要等满窗口才敢确定「这不是组合的第一颗」）。
     *
     *  ## 与「交还系统」那一档的区别
     *
     *  它不是已删除的「交还系统」动作档：那一档要的是「同一颗键的某一次按压归系统」，
     *  而判定手势必须先吞 DOWN，两者互斥（见 `docs/runtime/key-to-gesture.md` 三）。这一位要的是
     *  「这一颗键**从来不判单键手势**」—— 它压住的窗口是给**组合**用的，不是给单键判定用的。
     *
     *  与 [enabled] 的区别：停用是「这颗键整个不归模块」（组合也一起失效），这一位是
     *  「单键不归模块、组合仍然算」。
     */
    val comboOnly: Boolean = false,
    /**
     * **窗口从什么时候开始算** —— 两种判定时机，页面上那颗「固定窗口起点」开着
     * 等于这里 `false`（界面态与存储位相反，取反在 `BindingSettings` 那一行）。
     *
     *  ## `false` = 固定窗口起点（默认）
     *
     *  窗口在**第一击抬手**那一刻开始，之后每一击的抬手都不再延长它：一个窗口内数到的几击
     *  算一次触发。窗口到点、或数到这颗键配的最高档，就开始判。
     *  按下时按住多久都不吃掉窗口预算（起点是抬手，不是按下）。
     *
     *  ## `true` = 每次松手重新起算
     *
     *  每次**抬手**都把窗口推到「此刻 + [multiWaitMs]」：只要还在按，判定就一直往后延。
     *  最后一次按下之后再等满一个窗口没有新的按下，就按实际击数判。
     *
     *  ## 两种的共同点
     *
     *  数到最高档（[judgeCap]）都**当场判**，不再等窗口 —— 已经收够了，窗口里只可能是
     *  超出档位的第 N+1 下。
     */
    val waitMultiWindow: Boolean = false,
    /**
     * 「逐次等满」那一档的窗口（毫秒）—— **与 [doubleClickMs] 分开**。
     *
     *  ## 为什么不复用「单击延迟」
     *
     *  关着 [waitMultiWindow] 时，一个窗口同时表达两件事（两击之间最多隔多久 + 单击要等多久），
     *  所以 [doubleClickMs] 一个值就够。开着时这两件事被拆成了两条时间线：连击的节奏由用户
     *  按得快不快决定，而「等满」是模块额外压上去的犹豫期 —— 复用同一个值时，想把单击压短
     *  就得同时把连击窗口压短，两者互相牵着（用户口径：「等满多击窗口和单击延迟是冲突的」）。
     *
     *  ## 0 = 跟随 [doubleClickMs]
     *
     *  这一位是后加的（version 48），老配置里没有它。**默认 0 而不是某个具体毫秒**：
     *  用户可能已把 [doubleClickMs] 调成 500 并开着等满，默认写死 300 会让升级后那一下
     *  突然变快。0 = 「没有自己的意见，跟着单击延迟走」⇒ 升级前后行为一字不差；动过这根
     *  滑杆之后才固定成具体值。
     *
     *  界面上一根滑杆按 [waitMultiWindow] 切换显示：开着显示这一位、关着显示 [doubleClickMs]
     *  —— 同一时刻只有一根在起作用，不该摆两根让人猜哪根算数。
     */
    val multiWaitMs: Int = FOLLOW_DOUBLE_CLICK_MS,
    /**
     * **这颗键要不要用自己那套公共项** —— 默认关（跟随公共设置）。
     *
     *  它管的就是「按键单独设置」这件事：每颗键可以自己决定走不走 ColorOS 专有接口、
     *  执行时弹不弹提示，而不是被公共设置整页绑死。**按键级优先于公共设置**：
     *  开着时看下面两位，关着时看 `GlobalConfig` 里那两位。
     *
     *  取值只有一处出口：`Config.effectiveFor` —— 执行侧不许自己拼「开关 + 值」这个判断，
     *  拼错一处就是「这台机器上这颗键不听话」。
     */
    override val useOwnSettings: Boolean = false,
    /** 这颗键自己的「使用 ColorOS 接口」—— 只在 [useOwnSettings] 开着时算数。 */
    override val colorOsApi: Boolean = GlobalConfig.DEFAULT_COLOR_OS_API,
    /** 这颗键自己的「振动反馈」—— 只在 [useOwnSettings] 开着时算数。 */
    override val vibrate: Boolean = false,
    /** 这颗键自己的「振动强度」—— 只在 [useOwnSettings] 开着时算数。 */
    override val vibrateStrength: Int = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
    /** 这颗键自己的「执行时提示」—— 只在 [useOwnSettings] 开着时算数。 */
    override val toastOnRun: Boolean = GlobalConfig.DEFAULT_TOAST_ON_RUN,
    /** 这颗键自己的「提示走流体云」—— 只在 [useOwnSettings] 开着时算数。 */
    override val toastFluid: Boolean = GlobalConfig.DEFAULT_TOAST_FLUID,
    /** 这颗键自己的提示文案模板（空串 = 默认那句）—— 只在 [useOwnSettings] 开着时算数。 */
    override val toastTemplate: String = GlobalConfig.DEFAULT_TOAST_TEMPLATE,
    /**
     * 上面那几项**被公共设置播过种没有** —— 见 [OwnSettings.ownSettingsSeeded]。
     *
     *  只影响界面「第一次打开总开关时要不要抄一份公共值」，执行侧不读它。
     */
    override val ownSettingsSeeded: Boolean = false,
    /**
     * 长按绑的是不是「多条命令」（`cmd:<名字>` 且命令库里那条是串行命令）。
     * 只由宿主算好、随配置广播带过来（`ConfigFields.LONG_SERIAL_EXTRA`），注入侧不自己算：
     * 算它要读宿主私有目录的命令库，system_server 进不去带 MCS 分类的目录
     * （`docs/device/lsposed.md` 六之五 / 九之一），读不到就恒 false，闸门失效。
     * 默认 false = 与早先行为一致（配置文件里没有这一位，不回写）。
     */
    val longIsSerial: Boolean = false,
    /**
     * **某个场景块配了松手动作** —— 只由宿主算好、随配置广播带过来（见 `ConfigFields.SCENE_RELEASE_EXTRA`）。
     *
     *  注入侧读不到场景表（[scenes] 只走落盘），而它必须在抬手那一刻决定**发不发**
     *  `GestureTrigger.Release`（`GestureDetector.finishLong`）—— 这一位就是那个答案。
     *  由它参与 [releaseBound]，所以「块里配了松手动作」与「按键自己配了」在判定侧是同一件事。
     *
     *  默认 `false` = 与早先行为一致（没有这一位的配置读进来逐字不变）。
     */
    val sceneRelease: Boolean = false,
    /**
     * **某个场景块给长按配了动作** —— 与 [sceneRelease] 同一套：只由宿主算好、随广播带过来。
     *
     *  判定侧靠它回答「长按这一档到底有没有人要」（[longActionExists]）。少了这一半，
     *  只在场景里配了长按的键会被当成「长按无操作」，那一档的松手动作也就跟着没主语。
     */
    val sceneLong: Boolean = false,
    /**
     * **某个场景块给「单击后长按」这一档本身配了动作** —— 与 [sceneLong] 同一套（只由宿主算好带过来）。
     *
     *  判定侧靠它回答「这一档到底有没有人要」（[clickThenLongActionExists]）。少了这一半，
     *  只在场景里配了这一档的键会被当成「开着但无操作」：判定侧不排第 2 击的长按枪，
     *  那个只存在于场景里的动作也就永远轮不到执行。
     */
    val sceneChain: Boolean = false,
    /**
     * **某个场景块配了「单击后长按」的松手动作** —— 与 [sceneRelease] 同一套（只由宿主算好带过来）。
     *
     *  判定侧要靠它决定抬手那一刻发不发 `ClickThenLongRelease`：场景表不广播，它自己算不出来。
     */
    val sceneChainRelease: Boolean = false,
    /**
     * **系统场景** —— 一张顺序表，一块 = 一组条件 + 完整的三档手势（[SceneRule]）。
     *
     *  按顺序找「条件成立、且这一档有值」的第一块，用它那一档的动作（[actionFor]）；
     *  一块都不中就走 [actionOf] 那一档本来的绑定。
     *
     *  **不占用绑定**：它是叠在绑定之上的一张顺序表，所以「加一个场景」与「这一档绑什么」
     *  是两件事，互不影响。
     *
     *  **只走落盘这一列**（与 [Config.appNames] 同一套做法）：它是一张**列表**，塞不进
     *  [ConfigFields] 那种扁平字段表（一格一个值），也不该在每次按键时把整张表广播给
     *  `system_server` —— 注入侧只判手势、不挑动作，它不需要这张表。
     *
     *  空表（默认）= 与没有这一档完全相同。
     */
    val scenes: List<SceneRule> = emptyList(),
    /**
     * 第 3 击起的**额外档位**：连击次数 → 动作（`click3` / `click4` …）。
     *
     *  与 [scenes] 同一套：它是张表，进不了 `ConfigFields` 那种「一格一个值」的字段表，于是只走落盘。
     *  界面入口是「按键绑定」页的第 4、5 页（三击 / 四击）—— 与前三档走同一张动作网格、同一条写盘
     *  路径（[withAction]），所以界面上配出来的与手改 / 导入的在这里是同一份数据。
     *  空表（默认）⇒ 行为与没有这一项完全相同。
     *
     *  判定侧要知道的只有 [judgeClicks]（广播出去），动作本身**不广播**：注入侧只判手势、不挑动作。
     */
    val extraClicks: Map<Int, Action> = emptyMap(),
    /**
     * **双击这一档开着没有** —— 与 [extraClicksOn] 同一族（「要自己要才有」），只是它管的是第 2 击。
     *
     *  默认 `true`：双击是基础档，装上就在（与三击 / 四击默认关相反）。
     *
     *  关掉时与「关三击」同一条口径：
     *  · 「按键绑定」页不排双击那一页、场景块的动作行也不列它（列一行写进去也永远不会命中）；
     *  · [boundClicks] / [sceneClicks] 不算它 ⇒ 判定侧不数第二击，第二下按下就是**新的一轮**；
     *  · 执行侧 [actionOf] 对双击恒回「无操作」。
     *
     *  **开着但没绑动作时同样不等**：判定封顶按动作表算（[judgeCap]），没绑就没有第 2 击要等。
     *  所以这一位现在只决定「绑定页出不出现这一格」，不再影响单击的手感。
     *
     *  表（[double]）本身**不动**：关掉再打开，之前配好的动作原样回来（与 [extraClicksOn] 同一条）。
     *  **不跟随** [useOwnSettings]：这颗键开着哪几档手势是它自己的形态。
     */
    val doubleClickOn: Boolean = true,
    /**
     * **哪几档多击开着**（第 3 击起，元素即击数：`{3}` / `{3,4}` / `{4}`）—— 逐档一个开关。
     *
     *  默认空集：多击是「要自己要才会有的档位」，装上就在的话单击会因为**根本没用上**的
     *  第 4 击而白等三个窗。某一档没开时：
     *  · 「按键绑定」页不排那一页、「系统场景」块的动作行也不列它（列一行写进去也永远不会命中）；
     *  · 判定侧不把它算进 `clicksForJudge`，执行侧 [actionOf] 对它恒回「无操作」。
     *
     *  为什么不收成一位：三击与四击的**代价不一样** —— 三击只让单击多等一个窗，四击还要让
     *  三击也等。只要三击的人不该被迫接受四击那一个窗。
     *
     *  表（[extraClicks]）本身**不动**：关掉再打开，之前配好的那一档原样回来（与「松手执行
     *  额外动作」开关和动作分两位同一条口径）。读取侧一律走 [actionOf] / [boundClicks] /
     *  [sceneClicks] 收口，别的地方不要自己去读这张表。
     *
     *  **它不跟随 [useOwnSettings]**：那是「这一档用公共那份还是自己那份」的总闸，
     *  而「这颗键开着哪几档多击」是这颗键自己的形态 —— 关着总闸的键照样可以有三击。
     */
    val extraClicksOn: Set<Int> = emptySet(),
    /**
     * **「单击后长按」这一档启用没有**（`GestureTrigger.ClickThenLong`）。
     *
     *  与 [extraClicksOn] 同一族（「要自己要才有」），但它是**另一位**而不是击数集合里的一员：
     *  它描述的不是「数到第几下」，而是**第 2 击按住不放**这另一种形态 —— 与双击互斥。
     *
     *  默认 `false`。关着时判定侧根本不在第 2 击上排长按那一枪，所以**不多等一个窗口** ——
     *  这正是它不能与多击共用一位的原因（多击延长的是连击链，这一档不延长）。
     *
     *  同样**不跟随** [useOwnSettings]（理由同 [extraClicksOn]）。
     */
    val clickThenLongOn: Boolean = false,
    /** 「单击后长按」绑定什么 —— 只在 [clickThenLongOn] 开着时算数。 */
    val clickThenLong: Action = Action.None,
    /**
     * 「单击后长按」那一档自己的**长按延迟** —— 与 [longPressMs] 分开。
     *
     *  为什么分开：两段式里第二次按住的起点，用户往往希望比普通长按更短（因为已经先点了一下）。
     *  共用一位的话，调短这一档会把普通长按也一起改短。
     */
    val clickThenLongPressMs: Int = DEFAULT_LONG_PRESS_MS,
    /** 「单击后长按」按住期间**连续触发**（与 [longRepeat] 各自一份）。 */
    val clickThenLongRepeat: Boolean = false,
    val clickThenLongRepeatIntervalMs: Int = DEFAULT_REPEAT_INTERVAL_MS,
    /** 连续触发时每次重复都振动（与 [longRepeatVibrate] 各自一份）。 */
    val clickThenLongRepeatVibrate: Boolean = false,
    /**
     * 「单击后长按」绑的是不是**多条命令** —— 只由宿主算好、随广播带过来（与 [longIsSerial] 同一条）。
     *
     *  注入侧看不到命令库，而它要在排连发之前回答「这一档该不该重复」——
     *  多条命令反复重放整条链没有意义（与长按那一档同一条规则）。
     */
    val clickThenLongIsSerial: Boolean = false,
    /** 「单击后长按」**松手时**再执行一个动作（与 [longRelease] 各自一份）。 */
    val clickThenLongReleaseOn: Boolean = false,
    val clickThenLongRelease: Action = Action.None,
    /**
     * 宿主算好的**最高绑定击数**（含场景里绑的那几档）—— **只由宿主算好、随配置广播带过来**
     * （同 [longIsSerial] 那一套）。
     *
     *  它**不含**「单击后长按 ⇒ 数到第 2 击」那个下限：那一抬在两边各加一次（见 [judgeLimit] 与
     *  [clicksForJudge]）。留着的正是「后面到底有没有人要」这个事实 —— 判定侧靠它决定单击要不要
     *  等着看后面的连击。
     *
     *  0 = 这一位没给（老版本发的广播 / 从没写过这一项的配置）⇒ 判定侧退回 [boundClicks]
     *  的兜底，也就是「只看三档」。
     */
    val judgeClicks: Int = 0,
) : OwnSettings,
    LongPressParams {
    fun actionOf(gesture: Gesture): Action = actionOf(gesture.trigger())

    /**
     * 按**触发条件**取动作 —— 自定义手势（第 3 击起）只有这一条通路。
     *
     *  三档与 [actionOf] 完全同源（那边就是转调这里）；`Click(n≥3)` 去 [extraClicks] 取，
     *  没配就是「无操作」—— **不退回单击**：少配一档就少一档，拿单击顶替等于按两下执行了一下。
     *
     *  [extraClicksOn] 里没有这一档时第 3 击起一律「无操作」：表里配着的东西不算数（见那一项的说明）。
     *  这一支是**读侧唯一的出口** —— 界面、判定、执行都经过它，所以「开关关了却还在执行」
     *  只可能是有人绕开这里去读了 [extraClicks]。
     */
    fun actionOf(trigger: GestureTrigger): Action =
        when (trigger) {
            GestureTrigger.LongPress -> {
                long
            }

            //  「长按后松手」是**另一个槽位**（[longRelease]）：与长按那一档分开配、分开执行
            GestureTrigger.Release -> {
                longRelease
            }

            //  「单击后长按」是**另一个槽位**（[clickThenLong]）—— 不是长按那一档：
            //  它由第二击的形态决定，与长按的「按住第一击」不是同一件事。
            //  判据用 [clickThenLongCounts]（开着**且**有动作）：开着但动作是无操作时，
            //  这一档按「不存在」处理 —— 判定侧那一侧根本不会派发它（见那边的三处读点）。
            GestureTrigger.ClickThenLong -> {
                if (clickThenLongCounts) clickThenLong else Action.None
            }

            //  这一档的松手也**自成一路**：用它自己那一格，不借长按的 [longRelease]。
            //  **主档无操作时整条不成立**：松手动作是「这一档执行完之后」的收尾，
            //  主语没有就不该有谓语（用户口径「设为无操作时已设置的额外动作也不应该触发」）。
            GestureTrigger.ClickThenLongRelease -> {
                clickThenLongReleaseAction
            }

            is GestureTrigger.Click -> {
                when (trigger.count) {
                    1 -> single

                    //  双击关着时恒回「无操作」：与「没开的那几档多击」同一条口径
                    2 -> if (doubleClickOn) double else Action.None

                    else -> if (trigger.count in extraClicksOn) extraClicks[trigger.count] ?: Action.None else Action.None
                }
            }
        }

    /**
     * 把 [trigger] 那一档换成 [action] —— 写绑定的**唯一**入口（[actionOf] 的写侧，一个读一个写），
     * 界面那一层不许自己拼 `copy(single = …)`。
     *
     *  三档落在 [single] / [double] / [long]，第 3 击起落进 [extraClicks]。写成「无操作」时把
     *  那一格**从表里删掉**而不是存一条 `none`：`extraClicks[n] ?: Action.None` 读回来是同一个
     *  答案，而 `extra_clicks` 里留一串 `"3": "none"` 只会让配置多出几行没人读的字。
     *
     *  [GestureTrigger.Release] 写的是 [longRelease] —— 它是与长按**分开**的另一个槽位。
     *  调用方（界面）不走这一档：松手格有自己的卡片（`LongPressCard`），开关与动作是两位。
     *
     *  [extraClicksOn] 里没有这一档时**不写表**：它在界面上根本不可见，写进去只会留下一份
     *  「开关一开就冒出来」的隐藏绑定 —— 用户会以为那是自己配的。
     */
    fun withAction(
        trigger: GestureTrigger,
        action: Action,
    ): KeyConfig =
        when (trigger) {
            GestureTrigger.LongPress -> {
                copy(long = action)
            }

            GestureTrigger.Release -> {
                copy(longRelease = action)
            }

            GestureTrigger.ClickThenLong -> {
                if (clickThenLongOn) copy(clickThenLong = action) else this
            }

            GestureTrigger.ClickThenLongRelease -> {
                if (clickThenLongOn) copy(clickThenLongRelease = action) else this
            }

            is GestureTrigger.Click -> {
                when {
                    trigger.count == 1 -> copy(single = action)

                    //  双击关着时不写：它在界面上根本不可见，写进去只会留下一份
                    //  「开关一开就冒出来」的隐藏绑定（与下面多击那一支同一条）
                    trigger.count == 2 -> if (doubleClickOn) copy(double = action) else this

                    trigger.count in extraClicksOn -> copy(extraClicks = clickSlot(trigger.count, action))

                    else -> this
                }
            }
        }

    /** [withAction] 的第三击起那一支 —— 只改表里那一格，其余档位原样留着。 */
    private fun clickSlot(
        count: Int,
        action: Action,
    ): Map<Int, Action> = if (action is Action.None) extraClicks - count else extraClicks + (count to action)

    /**
     * 这颗键**真的绑了动作**的最高击数（0 = 连单击都没绑）—— 宿主的判据。
     *
     *  与 `KeyGesturePlan` 的取法同一个（那边按 `slots` 取），这里单独留一份是因为判定侧
     *  只看得到广播里的 [judgeClicks]，取不到动作表。
     *
     *  **条件分支也算绑定**：只给「双击」配了一条场景、不给 [double] 绑动作，是配置允许的常态。
     *  漏掉它的话判定侧认为最高只有一击，第一次抬手就把单击判掉了，
     *  那条双击场景永远等不到第二击 —— 而且日志里看不出任何异常。
     */
    val boundClicks: Int
        get() =
            maxOf(
                if (single.isBound) 1 else 0,
                //  双击那一档关着时不算（它在执行侧本来就回「无操作」，见 [doubleClickOn]）
                if (doubleClickOn && double.isBound) 2 else 0,
                //  **没开的那几档不算**：留着它们会让单击白等三个窗，
                //  而它们在执行侧本来就回「无操作」（见 [extraClicksOn]）。
                extraClicks
                    .filterKeys { it in extraClicksOn }
                    .filterValues { it.isBound }
                    .keys
                    .maxOrNull() ?: 0,
                sceneClicks,
            )

    /**
     * [scenes] 里按切出来的最高击数（1 = 只有单击那一档有动作；2 = 双击那一档有动作；0 = 没有可执行的）。
     *
     *  停用的、以及「条件还没配完」的块都不算 —— 它们永远不会执行，算进来只会让判定侧白等一个
     *  窗口（宁可按「没有」处理）。**只算真的绑了动作的档**（`isBound`）：某一块写着「双击 = 无操作」
     *  时不需要为它多等一次抬手 —— 那一档本来就什么都不做。
     *
     *  **第 3 击起也算**（那一档在 [extraClicksOn] 里时）：只在场景里配了「三击 → 某动作」、直接绑定
     *  一格没填，是配置允许的常态；漏掉它的话判定侧认为最高只有一击，那条三击场景永远等不到第 3 下。
     *  没开的那几档同样不算 —— 与 [boundClicks] 同一条口径。
     */
    private val sceneClicks: Int
        get() =
            scenes.maxOfOrNull { scene ->
                if (!scene.enabled || !scene.isReady) {
                    0
                } else {
                    scene.actions.entries
                        .filter { (trigger, action) -> trigger is GestureTrigger.Click && action.isBound }
                        .filter { (trigger, _) ->
                            //  双击那一档没开时不算：与「没开的多击」同一条口径
                            (trigger as GestureTrigger.Click).count.let { it == 1 || (it == 2 && doubleClickOn) || it in extraClicksOn }
                        }.maxOfOrNull { (trigger, _) -> (trigger as GestureTrigger.Click).count } ?: 0
                }
            } ?: 0

    /**
     * 判定侧**这一刻该用多长的窗口** —— 两击间隔与「逐次等满」共用的那一个数。
     *
     *  · [waitMultiWindow] 关着：只有 [doubleClickMs] 一件事可表，直接用它；
     *  · 开着：用 [multiWaitMs]，而它是 0（[FOLLOW_DOUBLE_CLICK_MS]）时仍回落到 [doubleClickMs]
     *    —— 老配置没带这一位，回落才保证升级前后一字不差。
     *
     *  收成一个属性而不是让注入侧自己 `if`：这条判据有两处读者（排窗 `scheduleDoubleWindow`
     *  与到点判定），各写一遍的话「排窗按 300、到点按 400」这种错位只会表现成偶发的手感异常。
     */
    val clickWindowMs: Int
        get() =
            if (waitMultiWindow) {
                multiWaitMs.takeIf { it > FOLLOW_DOUBLE_CLICK_MS } ?: doubleClickMs
            } else {
                doubleClickMs
            }

    /**
     * 判定侧实际要用的那一档：广播那一列（[judgeLimit]）优先，没给就按动作表算（老版本兼容），
     * 再抬上「单击后长按」那个下限。
     *
     *  **按广播那一列的上限钳一次**：`boundClicks` 来自 `extraClicks` 的键，手改配置可以写成 20，
     *  而注入侧收到的 `judgeClicks` 在 [ConfigFields.F_JUDGE_CLICKS] 那里被钳到 9 ——
     *  不钳的话宿主等 20 击、注入侧只派发到 9，第 10 击以后永远出不来。
     *
     *  **[clickThenLongCounts] 时下限是 2**：这一档要数到第 2 击才判得出来。不这么写的话，
     *  只绑了「单击后长按」的键 `boundClicks` 是 0，注入侧会认为「最多一击」，第 1 击抬手时
     *  就把单击派发掉，第二击永远等不到 —— 表现就是这个手势怎么按都不生效。
     *  它是**同一个数的下限**而不是另一个开关：注入侧只认 `judge_clicks` 这一位，
     *  多一位就多一处「两边不一致」的可能。
     */
    val clicksForJudge: Int
        get() =
            maxOf(
                judgeLimit,
                if (clickThenLongCounts) 2 else 0,
            ).coerceIn(0, ConfigFields.MAX_JUDGE_CLICKS)

    /**
     * 判定侧真正数到第几击就**封顶**（至少 1）。
     *
     *  写成属性只为一件事：判定器里「数到哪一档立刻判」与「逐次等时把多按的那几下
     *  夹回来」必须是同一个数。两处各写一遍，改一处漏一处就是「配了双击、按三下却判成无操作」。
     *
     *  **按「开了哪几档」算**（[doubleClickOn] / [extraClicksOn] / [clickThenLongOn]），
     *  不看那一档绑没绑动作：只要开着，判定侧就得数到那一档、就得多等窗口。
     *  用户口径是「只要开了就要等；不想等就把多击手势全关掉」——
     *  理由很直接：同时开两档、其中一档是无操作时，另一档照样要等那次窗口，
     *  所以「没绑动作就不等」这条捷径在部分生效的配置上本来就不成立，只会让手感
     *  跟着「绑没绑」跳。
     *
     *  [clicksForJudge] 仍然要取大：三击 / 四击那种「只在场景里绑了动作」的配置，
     *  档位开关可能没开，但那一档真要执行。
     */
    val judgeCap: Int
        get() =
            maxOf(
                if (doubleClickOn) 2 else 1,
                extraClicksOn.maxOrNull() ?: 1,
                if (clickThenLongOn) 2 else 1,
                clicksForJudge,
            ).coerceAtMost(ConfigFields.MAX_JUDGE_CLICKS)

    /**
     * 判定侧**只按动作表**该数到第几击 —— 广播那一列（[judgeClicks]）优先，老版本没给时按
     * [boundClicks] 算。
     *
     *  与 [clicksForJudge] 的差别只有「单击后长按那个下限」：[clicksForJudge] 在它之上再抬到 2，
     *  而这里只回答「按动作表该数到几击」—— 抬过下限的那个数分不出「双击绑了动作」
     *  与「只是单击后长按要数到第 2 击」。
     *
     *  场景里绑的双击也在这个数里（`judgeClicks` 是宿主按场景表算好广播过来的），所以调用方
     *  不必再单独问「场景有没有给双击配动作」。
     */
    private val judgeLimit: Int
        get() = if (judgeClicks > 0) judgeClicks else boundClicks

    /**
     * 这次手势该执行哪个动作 —— **条件分支的唯一入口**。
     *
     *  先按顺序找第一条成立的分支，一条都没有就退回 [actionOf] 那一档本来的绑定。
     *  实现只有 [pickAction] 一处（组合键 `ComboKey.resolve` 调的是同一个函数）。
     *  执行侧不要自己写这套 `firstOrNull`：条件维度以后会长，判据散在
     *  多处时「某个路径上条件分支不生效」这种 bug 只会在真机上被看见。
     *
     *  一个块可以带**好几条**条件，同种类之间「或」、不同种类之间「且」—— 那是
     *  [SceneRule.appliesTo] 的账，这里只负责顺序与「第一条赢」。
     *
     *  参数是**触发条件**而不是那个三值枚举：一块能接管哪几档由它自己的
     *  `SceneRule.actions` 说，所以「三击 / 四击也吃场景」不需要在这里多一个分支。
     *
     *  [matches] 是调用方给的判据（读系统开关的那一层，见 `ConditionProbe`）—— 这一层不认识
     *  任何系统接口。
     */
    fun actionFor(
        trigger: GestureTrigger,
        matches: (BranchCondition) -> Boolean,
    ): Action = pickAction(scenes, trigger, actionOf(trigger), matches)

    /**
     * 这一档**有没有被某个场景块接管** —— 「松手执行额外动作」要靠它让位。
     *
     *  与 [actionFor] 同一条判据（条件成立 + 这一档有值），只是不要动作、只要一个是非：
     *  长按那一档被场景接管了，松手那一刻就不该再补一刀。
     */
    fun sceneTakes(
        trigger: GestureTrigger,
        matches: (BranchCondition) -> Boolean,
    ): Boolean = sceneTakes(scenes, trigger, matches)

    /**
     * **松手那一刻执行什么** —— 长按被某一块接管时用那一块的松手格，否则用 [longRelease]。
     *
     *  实现在 [pickReleaseAction] 一处（组合键 `ComboKey.resolveRelease` 调的是同一个函数）。
     *  块命中时**不**再补 [longRelease]：一次按压一个结果 —— 那一块已经决定这一按做什么了。
     *
     *  **[longReleaseOn] 关着就传 `None` 进去**：开关关掉之后动作还留着（开关与动作是两位），
     *  不在这里收口的话，一块都不中时照样会执行一个用户以为已经关掉的动作。
     */
    fun resolveRelease(matches: (BranchCondition) -> Boolean): Action =
        pickReleaseAction(scenes, if (longReleaseOn) longRelease else Action.None, matches)

    /**
     * 长按绑的是不是**灵感语音那一族**（`oplus:voice_memory` / `oplus:voice_memory_stop`）。
     *
     *  两处判据必须是同一个口径（判定侧不排「长按连续触发」、界面写同一句原因）：
     *  连发对这两个动作都没有语义 —— 开始会被 ROM 当重入忽略（`reentrant collect.`），
     *  结束在没有采集时是空操作。见 `RomProfile.isVoiceMemory` 与
     *  `docs/device/actions/voice.md` 四十六。
     *
     *  写成计算属性、不落成字段：它是 [long] 的投影，落字段就要在 `ConfigFields` 那条链上
     *  多存一份**可以和 [long] 不一致**的真值。
     */
    val longIsVoiceMemory: Boolean
        get() = (long as? Action.Oplus)?.feature?.let(RomProfile::isVoiceMemory) == true

    /**
     * 「松手执行额外动作」这一刻**真的有事可做** —— 开关开着、并且已经挑了一个动作。
     *
     *  开关与动作是两位（见 [longReleaseOn]），只判其中一位都会错：
     *  · 只判动作：开关关掉之后动作还留着，松手会执行一个用户以为已经关掉的动作；
     *  · 只判开关：挑了动作才算数 —— 开着但没挑时松手本来就不做事。
     *
     *  第三位是[longFireOnRelease]：那条开着时长按本身已经推迟到松手，这一档再补一个
     *  「额外动作」就成了同一刻执行两个动作 —— 它让位，但**值原样留着**（关掉那一条之后回来）。
     *
     *  与 [longRepeat] **不冲突**：按住期间照常连续触发，松手那一刻再执行这一档 —— 两者是
     *  同一按的两个阶段（按住 / 抬手），不是互相占位的时序。
     *
     *  第四位是场景块里配的松手动作，两个来源取或：**[sceneRelease]**（注入侧那一份的替身 ——
     *  它手上没有场景表）与 `scenes.any { it.hasRelease }`（宿主 / 设置页这一份手上有表，就地算）。
     *  两处都在这里收口，是因为它同时服务两个判据：判定侧「发不发 `Release`」与界面
     *  「『松手时触发』那一行能不能拨」—— 只算一个来源的话，块里配了松手动作时界面还让人
     *  打开「松手时触发」，而那一开，块里的松手动作就再也不会执行了。
     *
     *  判定侧（`GestureDetector` 发不发那条广播）与界面都只读这一位。
     */
    override val releaseBound: Boolean
        get() =
            longActionExists &&
                !longFireOnRelease &&
                ((longReleaseOn && longRelease.isBound) || sceneRelease || scenes.any { it.hasRelease })

    /**
     * 长按这一档**本身**有事可做 —— 直接绑了动作，或某块场景里给长按配了动作
     * （场景表在注入侧是空的，那边靠广播来的 [sceneLong] 补上这一半）。
     *
     *  为什么松手那一档要看它：松手动作是「这一按长按**之后**再补的一个动作」——
     *  长按本身是无操作时，这一按根本不算执行过，再补一个松手动作就没有主语。
     *  界面那一侧也据它把「松手执行额外动作」整族压灰（见 `LongPressGates.releaseEnabled`）。
     */
    val longActionExists: Boolean
        get() = long.isBound || sceneLong || scenes.any { (it.actions[GestureTrigger.LongPress] ?: Action.None).isBound }

    /**
     * 「单击后长按」这一档**本身**有事可做 —— 直接绑了动作，或某块场景里给它配了动作。
     *
     *  与 [longActionExists] 同一个用途（松手那一档要看它），区别只是档位不同。
     *  注入侧场景表是空的，所以那一边只认直接绑定 —— 场景接管的松手由宿主侧判。
     */
    val clickThenLongActionExists: Boolean
        get() =
            clickThenLong.isBound ||
                sceneChain ||
                scenes.any { (it.actions[GestureTrigger.ClickThenLong] ?: Action.None).isBound }

    /**
     * 「单击后长按」这一档**此刻算不算数** —— 开着，**且**这一档真有动作（直接绑定 / 场景里配过）。
     *
     *  「开着但动作是无操作」等同于「这一档不存在」：这一档的代价全在判定链上（第 2 击按住变长按、
     *  `judge_clicks` 下限抬到 2），没有动作时这些代价只表现为「按住什么都不发生」。
     *  与 [longActionExists] 同一条口径。
     */
    val clickThenLongCounts: Boolean
        get() = clickThenLongOn && clickThenLongActionExists

    /**
     * 「单击后长按」松手那一刻该执行什么（按键自己那一格，不含场景）。
     *
     *  三位缺一位都是 `None`：这一档开着、松手开关开着、且**主档本身有事可做**
     *  （松手动作是那一档执行完之后的收尾，主语没有就不该有谓语）。
     */
    val clickThenLongReleaseAction: Action
        get() =
            if (clickThenLongOn && clickThenLongReleaseOn && clickThenLongActionExists) {
                clickThenLongRelease
            } else {
                Action.None
            }

    /**
     * 这一档要不要排「长按连续触发」—— 判定侧的**唯一判据**。
     *
     *  三条理由合成一处：
     *  · [longRepeat] 本身关着；
     *  · 走的是「松手才触发」（[longFireOnRelease]）：那一档到点什么都不执行、没有可重复的起点；
     *  · 长按绑的是多条命令（[longIsSerial]）：反复重放整条命令链没有意义；
     *  · 长按绑的是灵感语音那一族（[longIsVoiceMemory]）：开始 / 结束都是「按一下就完事」，
     *    重复拨没有语义。
     *
     *  写成属性而不是让判定侧现拼：`GestureDetector` 里那一处 `if` 原本已经几个条件，
     *  再加一条就顶破 detekt 的复杂度上限 —— 而这条口径本来也只该有一份。
     */
    val longRepeats: Boolean
        get() = longRepeat && !longFireOnRelease && !longIsSerial && !longIsVoiceMemory

    /**
     * 「单击后长按」要不要排连发 —— 与 [longRepeats] 同一条口径，只少「松手才触发」那一项
     * （这一档没有那个开关）。
     *
     *  多条命令与灵感语音都不重复：理由与长按那一档一字不差（见 [longRepeats]）。
     *  界面那一侧读同一份规则（`repeatBlockedReason`），两处不许各判一套。
     */
    val clickThenLongRepeats: Boolean
        get() =
            clickThenLongRepeat &&
                !clickThenLongIsSerial &&
                !((clickThenLong as? Action.Oplus)?.feature?.let(RomProfile::isVoiceMemory) == true)

    /**
     * 本对象在每个配置字段上的取值 —— 「字段 → 属性」映射的**广播那一半**。
     *
     *  广播（`ConfigBroadcast.intent`）按它取值，读回来是 [ofFields]；**落盘另一条通道**
     *  （`ConfigDto.toValues` → 同样落到 [ofFields]）也从同一张 `ConfigFields` 表出发，但它的**取值**
     *  在 DTO 上另写了一遍 —— 两张表必须同时改：只在这边加一行，广播里有、文件里没有（`click_then_long`
     *  就这么丢过一次，注入侧那一档的动作恒为「无操作」）。
     *
     *  **成对纪律**：这里加一个键，[ofFields] 必须同时读回来；改字段集合时先看 `ConfigFields` 那张表的
     *  「加一个配置字段要改几处」。
     */
    fun fieldValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_ENABLED to enabled,
                ConfigFields.F_SINGLE to single.token,
                ConfigFields.F_DOUBLE to double.token,
                ConfigFields.F_LONG to long.token,
                ConfigFields.F_LONG_PRESS_MS to longPressMs,
                ConfigFields.F_LONG_REPEAT to longRepeat,
                ConfigFields.F_LONG_RELEASE to longRelease.token,
                ConfigFields.F_LONG_RELEASE_ON to longReleaseOn,
                ConfigFields.F_LONG_FIRE_ON_RELEASE to longFireOnRelease,
                ConfigFields.F_REPEAT_INTERVAL_MS to longRepeatIntervalMs,
                ConfigFields.F_REPEAT_VIBRATE to longRepeatVibrate,
                ConfigFields.F_DOUBLE_CLICK_MS to doubleClickMs,
                ConfigFields.F_COMBO_ONLY to comboOnly,
                ConfigFields.F_DOUBLE_CLICK_ON to doubleClickOn,
                ConfigFields.F_WAIT_MULTI_WINDOW to waitMultiWindow,
                ConfigFields.F_MULTI_WAIT_MS to multiWaitMs,
                ConfigFields.F_OWN_SETTINGS to useOwnSettings,
                ConfigFields.F_KEY_COLOR_OS_API to colorOsApi,
                ConfigFields.F_KEY_VIBRATE to vibrate,
                ConfigFields.F_KEY_VIBRATE_STRENGTH to vibrateStrength,
                ConfigFields.F_KEY_TOAST_ON_RUN to toastOnRun,
                ConfigFields.F_KEY_TOAST_FLUID to toastFluid,
                ConfigFields.F_KEY_TOAST_TEMPLATE to toastTemplate,
                ConfigFields.F_OWN_SETTINGS_SEEDED to ownSettingsSeeded,
                ConfigFields.F_LONG_SERIAL to longIsSerial,
                ConfigFields.F_SCENE_RELEASE to sceneRelease,
                //  「这一档有没有人配」（= 场景里的 [sceneChain]）由宿主现算、随广播带过来，
                //  与 [F_LONG_SERIAL] / [F_SCENE_RELEASE] 同一条路（见 `ConfigBroadcast.intent`）。
                ConfigFields.F_SCENE_CHAIN to sceneChain,
                //  发的是 [judgeLimit]（宿主按动作表算出的**最高绑定击数**，含场景），不是抬过下限的
                //  [clicksForJudge]：「单击后长按 ⇒ 下限 2」那一抬两边各加一次（注入侧的 `clicksForJudge`
                //  做的是同一件事，结果逐字相同），收到抬过的数就分不出「双击绑了动作」与
                //  「只是要数到第 2 击」。
                ConfigFields.F_JUDGE_CLICKS to judgeLimit,
                //  哪几档多击开着要广播：判定侧据此决定数不数到第 3 / 4 击
                //  （它手上没有 `extra_clicks` 那张表，见 `ConfigFields.EXTRA_CLICKS_ON_EXTRA`）。
                //  编成 `"3,4"`：`Kind` 只有 Bool / Number / Text 三档，集合走文本最省事；
                //  排序一次让广播指纹稳定（集合本身无序，直接 join 会随实现变）。
                ConfigFields.F_EXTRA_CLICKS to extraClicksOn.sorted().joinToString(","),
                //  「单击后长按」那两位：注入侧要知道排不排第 2 击的长按枪，以及**这一档绑了没有**
                //  （`GestureTrigger.ClickThenLongRelease` 派发前要问 `clickThenLongActionExists`）。
                //  动作给 token 而不是 `Action` 对象 —— 理由与下面 `F_CTL_RELEASE` 那一行相同。
                ConfigFields.F_CLICK_THEN_LONG_ON to clickThenLongOn,
                ConfigFields.F_CTL to clickThenLong.token,
                //  「单击后长按」那几项参数：注入侧要它们排第二段的长按枪与连发，
                //  所以整族都广播（动作那两格仍只落盘 —— 注入侧不挑动作）
                ConfigFields.F_CTL_PRESS_MS to clickThenLongPressMs,
                ConfigFields.F_CTL_REPEAT to clickThenLongRepeat,
                ConfigFields.F_CTL_REPEAT_INTERVAL_MS to clickThenLongRepeatIntervalMs,
                ConfigFields.F_CTL_REPEAT_VIBRATE to clickThenLongRepeatVibrate,
                ConfigFields.F_CTL_RELEASE_ON to clickThenLongReleaseOn,
                //  **给 token 而不是 `Action` 对象**：这一格的类型是 `Kind.Text`，广播那一层按
                //  `map[f] as? String` 取值 —— 塞对象进去会静默退回默认值（空串），
                //  注入侧 `isBound` 恒假，松手那一档永远不发（真机上就是「配了不生效」）。
                ConfigFields.F_CTL_RELEASE to clickThenLongRelease.token,
            ),
        )

    companion object {
        /** 「单击延迟」的默认值（毫秒）。 */
        const val DEFAULT_DOUBLE_CLICK_MS = 300

        /**
         * 多击的**第一档**（第 3 击起；1 / 2 落在 [single] / [double]）与界面排在后面的那几档。
         *
         *  这三处（本条、[DEFAULT_EXTRA_CLICKS]、判定上限 [ConfigFields.MAX_JUDGE_CLICKS]）一起
         *  把「多击能到第几击」说全。各写一个字面量迟早出现「界面排到第 4 页、判定只认到第 3 击」。
         */
        const val MIN_EXTRA_CLICKS = 3

        val DEFAULT_EXTRA_CLICKS: List<Int> = listOf(3, 4)

        /**
         * 一串击数 → 打开的档位集合 —— 只留第 3 击起、且不超判定上限的那几个。
         *
         *  落盘（JSON 数组）与广播（逗号文本）两条读法都经过这里：两处各写一遍过滤条件，
         *  迟早出现「手改的 `5` 在广播里被认、在文件里被忽略」这种只有一边生效的状态。
         *  脏值丢掉那一项而不是整串作废 —— 少认一档只是「那一档不执行」，整串作废是「全都不要了」。
         */
        fun clicksOnOf(counts: Iterable<Int>): Set<Int> = counts.filter { it in MIN_EXTRA_CLICKS..ConfigFields.MAX_JUDGE_CLICKS }.toSet()

        /** 广播里那串 `"3,4"` → 打开的档位集合（`fieldValues()` 的逆）。 */
        fun clicksOnOf(text: String): Set<Int> = clicksOnOf(text.split(',').mapNotNull { it.trim().toIntOrNull() })

        /** 双击窗口下限 —— 100ms 比「两次完整按压 + 抬手」的物理最短时间还短，是给用户留的余量档。 */
        const val MIN_DOUBLE_CLICK_MS = 100
        const val MAX_DOUBLE_CLICK_MS = 1000

        /**
         * [multiWaitMs] 的「跟随 [doubleClickMs]」取值 —— 见那一位的 KDoc。
         *
         *  用 0 而不是 `-1`：0 本来就是「没有窗口」这个合法极值之外的空位，而负数会让
         *  `ConfigFields.Kind.Number` 的下限校验多一条要解释的特例。
         */
        const val FOLLOW_DOUBLE_CLICK_MS = 0

        /**
         * 「多击窗口」的上限 —— **与 [MAX_DOUBLE_CLICK_MS] 是同一个数**。
         *
         *  两根值现在共用同一根滑杆（见 `BindingSettings` 的 `waitWindowRow`），上下限必须一致：
         *  两个上限各写一份时，切换判定时机那一下滑杆的可用范围会跳一截，看着像值被改了。
         *  保留这个别名只为「多击窗口」那一位自己的文档里能指名道姓。
         */
        const val MAX_MULTI_WAIT_MS = MAX_DOUBLE_CLICK_MS

        const val DEFAULT_LONG_PRESS_MS = 500

        /**
         * 长按阈值下限 —— **200ms 已经高于人手一次自然按压（80~150ms）**，所以任何一档下
         * 「正常按一下」都不会被判成长按，单击 / 双击都按得出来。
         *
         * 原来这一档是 100ms（落在自然按压里面）：「稍按久一点就变长按」，单击基本按不出来。
         * 下限抬到 200ms 就是为了堵掉这个组合，用户不必先踩一次才知道。
         */
        const val MIN_LONG_PRESS_MS = 200

        /**
         * 长按阈值上限 —— **2000ms**（用户口径）。
         *
         *  上一档是 1000ms：想「按住两秒才算长按」的人调到滑杆尽头也调不出来，只能手改配置，
         *  而手改的值还会被 `ConfigFields` 的钳制压回 1000 —— 上限与滑杆必须同一个数。
         *  上限本身不参与判定正确性：判定只拿它排长按那一枪，越长只是越不容易误判成长按，
         *  代价是长按动作要等更久（`GestureDetector.scheduleLongPress`）。
         */
        const val MAX_LONG_PRESS_MS = 2000
        const val DEFAULT_REPEAT_INTERVAL_MS = 300

        /** 连发间隔下限 —— 50ms（用户口径）：再快就与「按住不放」的体感脱节，且按键注入本身也要时间。 */
        const val MIN_REPEAT_INTERVAL_MS = 50

        const val MAX_REPEAT_INTERVAL_MS = 3000

        /**
         * 某个键的默认值：**内置键默认接管、自定义键默认不接管**。
         *
         *  「装上模块不改变行为」现在由**模块总闸**负责（`GlobalConfig.enabled` 默认 false），
         *  不再靠这颗键的开关 —— 所以内置的侧键可以默认是开的：用户第一次打开总闸时它立刻可用。
         *  反过来，侧键也默认关着的话，第一次打开总闸的表现是「开了没反应」，得先去绑一个动作
         *  才活（`pick` 会顺手置开）—— 那是把一个初始化细节漏给了用户。
         *
         *  自定义键仍默认关：它们是被**收集**进来的，收集那一步自己置开（`Config.withCustomKey`）；
         *  而「配置里认不出的键」也落在这一档 —— 不接管比接管安全，且删键时
         *  `ConfigChannel` 靠「查不到 ⇒ enabled=false」判出「这一颗刚消失」。
         *
         * 注意：单击那档的默认值是「无操作」而不是「交还系统」—— 后者已删（见 `Action` 的注释）。
         * 总闸开着而这一档还没配时，按键被吞掉什么都不做；这也是「还没配」最不容易误读的表现。
         */
        fun defaultFor(id: KeyId) =
            KeyConfig(
                id = id,
                enabled = !id.isCustom,
                single = Action.None,
                double = Action.None,
                long = Action.None,
                longPressMs = DEFAULT_LONG_PRESS_MS,
                longRepeat = false,
                longRepeatIntervalMs = DEFAULT_REPEAT_INTERVAL_MS,
                longRepeatVibrate = false,
                doubleClickMs = DEFAULT_DOUBLE_CLICK_MS,
            )

        /**
         * 从取值表还原一个键的配置 —— 与 [KeyConfig.fieldValues] 成对，两条通道共用。
         *
         *  这里**不再钳制区间**：钳制是读取方对「外来文本」做的事（上下限就在
         *  `ConfigFields` 的字段表里），走到这里时值已经收口过。
         *
         *  [scenes] 不在取值表里（那是列表，不是扁平的一格，见 [KeyConfig.scenes]）——
         *  由落盘那一侧单独读出来传进来；广播那条路不传，注入侧拿到的就是空表。
         */
        fun ofFields(
            id: KeyId,
            v: ConfigFields.Values,
            scenes: List<SceneRule> = emptyList(),
        ): KeyConfig =
            KeyConfig(
                id = id,
                enabled = v.bool(ConfigFields.F_ENABLED),
                single = Action.parse(v.text(ConfigFields.F_SINGLE)),
                double = Action.parse(v.text(ConfigFields.F_DOUBLE)),
                long = Action.parse(v.text(ConfigFields.F_LONG)),
                longPressMs = v.number(ConfigFields.F_LONG_PRESS_MS),
                longRepeat = v.bool(ConfigFields.F_LONG_REPEAT),
                longRelease = Action.parse(v.text(ConfigFields.F_LONG_RELEASE)),
                longReleaseOn = v.bool(ConfigFields.F_LONG_RELEASE_ON),
                longFireOnRelease = v.bool(ConfigFields.F_LONG_FIRE_ON_RELEASE),
                longRepeatIntervalMs = v.number(ConfigFields.F_REPEAT_INTERVAL_MS),
                longRepeatVibrate = v.bool(ConfigFields.F_REPEAT_VIBRATE),
                doubleClickMs = v.number(ConfigFields.F_DOUBLE_CLICK_MS),
                comboOnly = v.bool(ConfigFields.F_COMBO_ONLY),
                doubleClickOn = v.bool(ConfigFields.F_DOUBLE_CLICK_ON),
                waitMultiWindow = v.bool(ConfigFields.F_WAIT_MULTI_WINDOW),
                multiWaitMs = v.number(ConfigFields.F_MULTI_WAIT_MS),
                //  「三档同一事件」与「独立设置」那一族 —— **读的这一半必须与 `fieldValues()` 成对**：
                //  只写进 `fieldValues()` 而不在这里读回来，表现就是「界面上改了、也落盘了，
                //  但判定与执行那边照旧」——配置里有值、内存里是默认值，最难查的一种。
                useOwnSettings = v.bool(ConfigFields.F_OWN_SETTINGS),
                colorOsApi = v.bool(ConfigFields.F_KEY_COLOR_OS_API),
                vibrate = v.bool(ConfigFields.F_KEY_VIBRATE),
                vibrateStrength = v.number(ConfigFields.F_KEY_VIBRATE_STRENGTH),
                toastOnRun = v.bool(ConfigFields.F_KEY_TOAST_ON_RUN),
                toastFluid = v.bool(ConfigFields.F_KEY_TOAST_FLUID),
                toastTemplate = v.text(ConfigFields.F_KEY_TOAST_TEMPLATE),
                ownSettingsSeeded = v.bool(ConfigFields.F_OWN_SETTINGS_SEEDED),
                longIsSerial = v.bool(ConfigFields.F_LONG_SERIAL),
                sceneRelease = v.bool(ConfigFields.F_SCENE_RELEASE),
                scenes = scenes,
                judgeClicks = v.number(ConfigFields.F_JUDGE_CLICKS),
                //  **读的这一半必须与 `fieldValues()` 成对**（同上）：只写不读回来，
                //  界面上开了多击、注入侧却仍按「没有」判 —— 三击按到底也不执行。
                extraClicksOn = clicksOnOf(v.text(ConfigFields.F_EXTRA_CLICKS)),
                clickThenLongOn = v.bool(ConfigFields.F_CLICK_THEN_LONG_ON),
                clickThenLong = Action.parse(v.text(ConfigFields.F_CTL)),
                clickThenLongPressMs = v.number(ConfigFields.F_CTL_PRESS_MS),
                clickThenLongRepeat = v.bool(ConfigFields.F_CTL_REPEAT),
                clickThenLongRepeatIntervalMs = v.number(ConfigFields.F_CTL_REPEAT_INTERVAL_MS),
                clickThenLongRepeatVibrate = v.bool(ConfigFields.F_CTL_REPEAT_VIBRATE),
                clickThenLongIsSerial = v.bool(ConfigFields.F_CTL_SERIAL),
                clickThenLongReleaseOn = v.bool(ConfigFields.F_CTL_RELEASE_ON),
                clickThenLongRelease = Action.parse(v.text(ConfigFields.F_CTL_RELEASE)),
                sceneLong = v.bool(ConfigFields.F_SCENE_LONG),
                sceneChain = v.bool(ConfigFields.F_SCENE_CHAIN),
                sceneChainRelease = v.bool(ConfigFields.F_SCENE_CHAIN_RELEASE),
            )
    }
}

/** 全局配置（不属于任何单个键）。 */
data class GlobalConfig(
    /**
     * **模块总闸**：关掉 ⇒ 一颗键都不接管（首页「启用按键绑定」那一行）。
     *
     *  与 `KeyConfig.enabled`（这一颗键接管不接管）是两件事 —— 拆开之前侧键那一份同时当两个用，
     *  于是「停用侧键」被迫等于「关掉整个模块」（页面上其它键也跟着灰）。判定侧的闸门是
     *  「总闸 **且** 这一颗键」（见 `inject/ConfigChannel.enabledOf`）。
     *
     *  默认关：装上模块不改变任何行为。
     */
    val enabled: Boolean = DEFAULT_ENABLED,
    override val vibrate: Boolean,
    /**
     * **振动强度** —— 三档（[VIBRATE_WEAK] / [VIBRATE_MEDIUM] / [VIBRATE_STRONG]）。
     *
     *  只在 [vibrate] 为真时有意义（那一条滑块也只在那时出现）。读它的是宿主进程里的
     *  执行器（`host/GestureExec.haptic`），所以**只走落盘那一列**、不进广播 —— 注入侧不振。
     */
    override val vibrateStrength: Int = DEFAULT_VIBRATE_STRENGTH,
    /** 执行**任意动作**时弹一条提示（内容是动作名；自定义命令取命令名）。 */
    override val toastOnRun: Boolean = DEFAULT_TOAST_ON_RUN,
    /**
     * 「执行时提示」走**流体云**（状态栏 / AOD 上那颗胶囊）而不是 Toast，默认关。
     *
     *  它只换落点，不换说法：文案仍由 [toastTemplate] 拼，图标取**被执行的那个动作**自己的
     *  字形（`FluidCard.actionNameOf`，界面层预渲染、宿主执行时按同一名字找）。
     *
     *  与 [toastOnRun] 同一档：只在 [toastOnRun] 为真时有意义，读它的是宿主执行器，
     *  所以**只走落盘那一列**、不进广播（注入侧不弹提示）。
     */
    override val toastFluid: Boolean = DEFAULT_TOAST_FLUID,
    /**
     * 「执行时提示」那一句的**文案模板** —— `$key` / `$gesture` / `$action` 三段可自由摆放与增删
     *  （见 `ToastNotice`）。默认就是拆分之前那句 `侧键 - 单击 → 手电筒`。
     *
     *  与 [toastOnRun] 同一档：只在 [toastOnRun] 为真时有意义，读它的是宿主执行器，
     *  所以**只走落盘那一列**、不进广播（注入侧不弹提示）。
     */
    override val toastTemplate: String = DEFAULT_TOAST_TEMPLATE,
    /** 界面语言（auto = 跟随系统，zh_CN = 简体中文，en = English）。 */
    val language: String = DEFAULT_LANGUAGE,
    /**
     * **系统配色** —— 页面强调色用哪一套（取值是色板 id，见界面层的色板表）。
     *
     *  这里只存**字符串**、不存枚举：色板清单与它那一族颜色全在界面层
     *  （`ui/model/PagePalette`），而 `:core` 的 classpath 里没有 `:app` —— 共用层不认识
     *  「有哪些色板」，也不该认识（注入侧与配色无关）。
     *
     *  于是手改文件写进一个不认识的 id 时，这一层原样收下、界面侧读不到就落回默认档
     *  （`PagePalette.ofId` 的兜底）—— 不在这里钳，否则每加一档就要改一次 core。
     */
    val palette: String = DEFAULT_PALETTE,
    /**
     * **界面颜色** —— 跟随系统 / 固定浅色 / 固定深色（取值见 [THEME_AUTO] 那一组）。
     *
     *  与 [palette] 同一套：这里只存字符串，三档的枚举在界面层（`ui/model/AppTheme`）。
     *
     *  它决定的是页面与**宿主那条状态栏 / 顶栏**一起的深浅（`chrome.dark` 由它算出来，见
     *  `ui/PageTheme.effectiveNight`）—— 所以选「固定深色」时，按宿主页面的观感是一整块深色，
     *  不是「模块这一层深、宿主那层浅」。
     */
    val theme: String = DEFAULT_THEME,
    /**
     * **跟随系统显示大小** —— 关掉后模块页面按**出厂标准**的密度与字号渲染。
     *
     *  为什么要这一档：系统的「显示大小 / 字体大小」调到最大时，本模块那些按 dp 定死的行高、
     *  52dp 的格子、单行标题都会挤在一起（标题被省略号吃掉、副标题顶到下一行）—— 用户要么
     *  把系统调回去，要么就只能忍着。这一档让人**只让模块页面**保持标准大小。
     *
     *  实现：关掉时页面根上 provide 一份 `Density`，取 `Resources.getSystem()` 那份**未被
     *  应用层覆盖**的 `displayMetrics`（系统配置，不含「显示大小」那层改写），字号固定 1.0。
     *  默认开 —— 不改变任何人的现状。
     */
    val followDisplaySize: Boolean = DEFAULT_FOLLOW_DISPLAY_SIZE,
    /**
     * **页面缩放**（百分比，[PAGE_SCALE_MIN] ~ [PAGE_SCALE_MAX]）。
     *
     *  只在 [followDisplaySize] 为假时生效（那一根滑杆也只在那时出现）：跟随系统时页面大小
     *  由系统那一档说了算，再乘一个缩放会让两处都能调、谁也不清楚最后是多少。
     *
     *  实现：把页面根上那份 `Density.density` 乘上 `pageScale / 100`（见 `PageContent`）——
     *  只改模块这一棵页面树，宿主的顶栏 / 状态栏不动。
     */
    val pageScale: Int = DEFAULT_PAGE_SCALE,
    /**
     * **随机图标配色** —— `0` = 关；非 0 = 开，且这一份随机结果就是由它派生出来的。
     *
     *  为什么存的是**种子**而不是一张「图标 → 颜色」的表：颜色本来就是从种子 + 图标身份算出来的，
     *  存表既大又会跟图标改名脱钩；存种子等于「一次生成、永久复用」，关掉再打开才换一批。
     *  只在界面层用（注入侧没有图标），所以**只有落盘那一列**。
     */
    val iconTintSeed: Int = DEFAULT_ICON_TINT_SEED,
    /**
     * **按键绑定页要不要露出「常用命令」那一段**（微信扫一扫这类现成命令的快捷入口）。
     *
     *  它是纯显示项：关掉只是那些行不画，已经绑上去的绑定一个字都不动。
     *  只在界面层读 —— 注入侧不认识界面，所以**只有落盘那一列**、不进广播。
     *  默认开：装上就看得见，嫌占地方再关（关的入口在「按键设置」页）。
     */
    val showCommonCommands: Boolean = DEFAULT_SHOW_COMMON_COMMANDS,
    /**
     * 「常用命令」那张清单 —— **只由用户自己收录**，模块不预置任何条目。
     *
     *  默认空表：开关打开后是一张空清单，用户在「常用命令」页自己挑动作加进来。
     *  原先预置的那四条（微信 / 支付宝的扫一扫与收付款）已经去掉 —— 要不要放、放哪几条，
     *  交给用户；模块只保留 [com.lookie.opluskey.core.present.CommonCommands] 那张表给
     *  **老配置里已绑过的 `cmd:common.*`** 兜底（执行侧与显示名还认得它们）。
     *  只在界面层读 ⇒ 只有落盘那一列、不进广播。但它跟着配置走：条目都是动作引用，目标在这一份
     *  自己的绑定与命令库里 —— 新建空白配置时清空（`ConfigProfiles.createAs`）。
     */
    val commonCommands: List<CommonCommand> = emptyList(),
    /** 使用 ColorOS 专属系统接口（手电筒 / 免打扰 / 响铃模式等）。关闭时走 AOSP 标准接口。 */
    override val colorOsApi: Boolean = DEFAULT_COLOR_OS_API,
    /**
     * 「前台转小窗」要不要播**缩小动画**。
     *
     *  关（默认）：原地收（recents 正门），一步到位、没有窗口动画。
     *  开：同样原地收成小窗，紧接着让框架自己的动画器播那一段（`alpha 1→1`、`scale 1→0.8017`）——
     *  插值 / 矩阵 / spring 全是框架在跑，模块只交起止参数。**只对竖屏生效**：动画终点用的是
     *  竖屏标定的那组落点，横屏时不播、照旧原地收。配方与坑见 `docs/features/zoom-window/notes.md` §十二。
     */
    val miniWindowAnim: Boolean = DEFAULT_MINI_WINDOW_ANIM,
    /**
     * 「组合键绑的是无操作时，执行两颗键各自的绑定」—— 为真时那一档组合不吞键，
     * 两颗键各按自己这一档手势照常执行；为假时什么都不做（与这一位出现之前一致）。
     *
     *  只在组合的**那一格**是 [Action.None] 时生效：绑了动作就以组合为准，
     *  否则「配了组合还要两颗键各跑一遍」会变成一次按压执行三个动作。
     *
     *  读它的是宿主里的执行器（`host/GestureExec.handleCombo`），注入侧的 `ChordGuard` 判
     *  「吞不吞 ROM 的组合判定」时也读它 —— 所以走广播（`ConfigFields.F_COMBO_PASSTHROUGH`）。
     */
    val comboPassthrough: Boolean = DEFAULT_COMBO_PASSTHROUGH,
    /**
     * 组合键：**两颗键按下之间最多隔多久**（毫秒）—— 用户口径「按键之间互相的等待时间」。
     *
     *  判定在注入侧（`ComboDetector`），所以这一位**走广播**（`ConfigFields.F_COMBO_WINDOW_MS`）。
     *
     *  它的代价是真实的：参与组合的键要先等这么久才轮到自己的单键手势（等的是「第二颗键会不会来」）。
     *  不参与任何组合的键不受影响 —— 那条路上没有可等的东西。
     */
    val comboWindowMs: Int = DEFAULT_COMBO_WINDOW_MS,
    /**
     * **认不认「窗口内等齐成员」这一种按法** —— 组合的两条触发方式之一（另一条是
     * [comboHoldOn]），默认开：这一位出现之前窗口那条一直生效，老配置读回来行为逐字一致。
     *
     *  关掉之后「两颗键在一个窗口内先后按下」不再算这一档组合 —— 判定侧那条判据整条不生效
     *  （`ComboDetector.pairFor` 的 `windowed`），**「参与组合的键要先等一个窗口」那条代价
     *  也一起消失**：等的是「同伴会不会来」，这一档不认窗口之后那个问题就不存在（用户口径：
     *  不开等待手势时，成员键各自不再等这个窗口）。想「一起按」就只能把这一位打开。
     *
     *  每组可以另设一份（`ComboKey.windowOn`，受那一组的「使用独立设置」管）——
     *  这一位是公共默认，取值一律走 `Config.comboWindowOnFor`。
     *
     *  判定在注入侧，所以**走广播**（`ConfigFields.F_COMBO_WINDOW_ON`）。
     */
    val comboWindowOn: Boolean = DEFAULT_COMBO_WINDOW_ON,
    /**
     * **认不认「按住一个成员、再按剩下的成员」** —— 组合的另一条触发方式，默认关
     * （这一位出现之前没有这种按法）。
     *
     *  每组可以另设一份（`ComboKey.holdOn`，受那一组的「使用独立设置」管）；
     *  取值一律走 `Config.comboHoldFor`。判定在注入侧 ⇒ **走广播**（`F_COMBO_HOLD_ON`）。
     */
    val comboHoldOn: Boolean = DEFAULT_COMBO_HOLD_ON,
    /**
     * 组合键：**按住手势里首键要先按住多久**（毫秒）—— 用户口径「首键必须按住超过这个时间」。
     *
     *  判定在注入侧（`ComboDetector.pairFor` 的 `held`），所以这一位**走广播**
     *  （`ConfigFields.F_COMBO_HOLD_MIN_MS`）。每组可以另设一份（`ComboKey.holdMinMs`，
     *  受那一组的「使用独立设置」管）—— 这一位是公共默认，取值一律走 `Config.comboHoldMinFor`。
     *
     *  它把「一起按」与「按住了一颗再按另一颗」分开：没有下限时两颗键相差几毫秒也算按住，
     *  而哪一颗算「先按住」全看硬件先报谁 —— 同一个物理动作于是有时命中、有时整条落空。
     */
    val comboHoldMinMs: Int = DEFAULT_COMBO_HOLD_MIN_MS,
) : OwnSettings {
    /**
     * 公共那份**永远是「用自己的」**：它自己就是那个「公共设置」。
     *
     *  这一位让 [effectiveFor] 能把公共那份与「某一档自己的设置」当成同一种东西返回 ——
     *  于是「独立设置开着就用自己那份」这条规则只有一处，调用点直接读字段。
     */
    override val useOwnSettings: Boolean get() = true

    /** 本对象在每个配置字段上的取值 —— 与 `KeyConfig.fieldValues` 同一套（见那边的说明）。 */
    fun fieldValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_MODULE_ENABLED to enabled,
                ConfigFields.F_VIBRATE to vibrate,
                ConfigFields.F_VIBRATE_STRENGTH to vibrateStrength,
                ConfigFields.F_TOAST_ON_RUN to toastOnRun,
                ConfigFields.F_TOAST_FLUID to toastFluid,
                ConfigFields.F_TOAST_TEMPLATE to toastTemplate,
                ConfigFields.F_LANGUAGE to language,
                ConfigFields.F_PALETTE to palette,
                ConfigFields.F_THEME to theme,
                ConfigFields.F_COLOR_OS_API to colorOsApi,
                ConfigFields.F_MINI_WINDOW_ANIM to miniWindowAnim,
                ConfigFields.F_FOLLOW_DISPLAY_SIZE to followDisplaySize,
                ConfigFields.F_PAGE_SCALE to pageScale,
                ConfigFields.F_ICON_TINT_SEED to iconTintSeed,
                ConfigFields.F_SHOW_COMMON_COMMANDS to showCommonCommands,
                ConfigFields.F_COMBO_PASSTHROUGH to comboPassthrough,
                ConfigFields.F_COMBO_WINDOW_MS to comboWindowMs,
                ConfigFields.F_COMBO_WINDOW_ON to comboWindowOn,
                ConfigFields.F_COMBO_HOLD_ON to comboHoldOn,
                ConfigFields.F_COMBO_HOLD_MIN_MS to comboHoldMinMs,
            ),
        )

    companion object {
        /** [GlobalConfig.enabled] 的默认值：**默认关** —— 装上模块不改变任何行为。 */
        const val DEFAULT_ENABLED = false

        /** [GlobalConfig.toastOnRun] 的默认值：默认不弹。 */
        const val DEFAULT_TOAST_ON_RUN = false

        /** [GlobalConfig.toastFluid] 的默认值：默认走 Toast（与这一档出现之前一致）。 */
        const val DEFAULT_TOAST_FLUID = false

        /**
         * [GlobalConfig.toastTemplate] 的默认值 —— 与拆分之前那句话**逐字一致**，
         * 老配置没有这一项时补的也是它（见 `ToastNotice.DEFAULT_TEMPLATE`）。
         */
        const val DEFAULT_TOAST_TEMPLATE = ToastNotice.DEFAULT_TEMPLATE

        /** [GlobalConfig.vibrateStrength] 的三档取值 —— 弱 / 中 / 强。 */
        const val VIBRATE_WEAK = 0
        const val VIBRATE_MEDIUM = 1
        const val VIBRATE_STRONG = 2

        /** [GlobalConfig.vibrateStrength] 的默认值：中档（与「开振动」原本那一下手感对齐）。 */
        const val DEFAULT_VIBRATE_STRENGTH = VIBRATE_MEDIUM

        /** [GlobalConfig.vibrateStrength] 的取值范围（滑杆两端与钳制共用）。 */
        val VIBRATE_STRENGTH_RANGE = VIBRATE_WEAK..VIBRATE_STRONG

        /** [GlobalConfig.language] 的默认值：跟随系统。 */
        const val DEFAULT_LANGUAGE = "auto"

        /**
         * [GlobalConfig.palette] 的默认值 —— **COUI 自己那套蓝**（`lightColorScheme()` 的 `primary`）。
         *
         *  字面量只写在这里：界面层那枚举的默认档用这个常量当 id，默认档与「没选过」是同一个东西。
         */
        const val DEFAULT_PALETTE = "default"

        /** [GlobalConfig.theme] 的三档 —— 与界面层 `AppTheme` 的 code 一一对应。 */
        const val THEME_AUTO = "auto"

        /** 跟随系统，但**整页用系统那套 Monet 配色**（底色 / 卡片 / 强调色全换）。 */
        const val THEME_MONET = "monet"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        /** [GlobalConfig.theme] 的默认值：跟随系统。 */
        const val DEFAULT_THEME = THEME_AUTO

        /** [GlobalConfig.colorOsApi] 的默认值：默认开启。 */
        const val DEFAULT_COLOR_OS_API = true

        /** 「跟随系统显示大小」的默认值 —— 开（与升级前的行为一致）。 */
        const val DEFAULT_FOLLOW_DISPLAY_SIZE = true

        /** 页面缩放的三档：下限 / 出厂值 / 上限（百分比）。 */
        const val PAGE_SCALE_MIN = 70

        /** 随机图标配色：`0` = 关（默认），其余值当种子用。 */
        const val DEFAULT_ICON_TINT_SEED = 0

        /** [GlobalConfig.showCommonCommands] 的默认值 —— 默认露出（装上就看得到）。 */
        const val DEFAULT_SHOW_COMMON_COMMANDS = true
        const val DEFAULT_PAGE_SCALE = 100
        const val PAGE_SCALE_MAX = 150

        /** [GlobalConfig.miniWindowAnim] 的默认值：默认关闭（原地收、无动画）。 */
        const val DEFAULT_MINI_WINDOW_ANIM = false

        /**
         * [GlobalConfig.comboPassthrough] 的默认值：**开**。
         *
         *  组合键是用户主动配的，而「配了一组却忘了给某一格绑动作」很常见 ——
         *  默认吞掉两颗键的全部反应，用户会以为模块坏了。开着时这种半成品至少还保留两颗键本来的行为。
         */
        const val DEFAULT_COMBO_PASSTHROUGH = true

        /**
         * [GlobalConfig.comboWindowMs] 的三档 —— 下限 / 出厂值 / 上限（毫秒）。
         *
         *  出厂 80 就是这一位出现之前写死的那个窗口：老配置读回来行为完全不变。
         *  下限 20：再短就几乎是「必须精确同时按」，组合键会挑手。
         *
         *  上限放到 1000（原 300）：这个数同时管「算不算同时按下」与「第一颗键等多久才回落单击」，
         *  手慢的人 / 三颗的组合 / 需要先按住再补另一颗的场景，300ms 明显不够。放宽的代价是
         *  滑杆右端更钝 —— 副标题那句「调大会让参与组合的按键多等这么久」就是给它的说明。
         */
        const val COMBO_WINDOW_MIN_MS = 20
        const val DEFAULT_COMBO_WINDOW_MS = 80
        const val COMBO_WINDOW_MAX_MS = 1000

        /**
         * [GlobalConfig.comboWindowOn] 的默认值：**开**。
         *
         *  「窗口内先后按下」是组合键从第一天起就有的按法，老配置里没有这一位 ⇒ 必须还是它，
         *  否则升级之后所有组合键一起失灵。
         */
        const val DEFAULT_COMBO_WINDOW_ON = true

        /**
         * [GlobalConfig.comboHoldOn] 的默认值：**关**。
         *
         *  「按住一颗再按另一颗」是后加的一条路，默认不开 —— 装上之后手感与这一位出现之前一致。
         */
        const val DEFAULT_COMBO_HOLD_ON = false

        /**
         * [GlobalConfig.comboHoldMinMs] 的三档 —— 下限 / 出厂值 / 上限（毫秒）。
         *
         *  出厂 100：实测「一起按」两颗键相差 0~40ms（谁先上报看硬件），而真要按住一颗再补
         *  另一颗，人手一般在 150ms 以上 —— 100 落在中间，两边都不误伤。
         *
         *  下限 40：再短就已经盖不住「一起按」的抖动（那正是这一位要分开的那一半）。
         *  上限 500 与组合长按阈值同量级：调得比那颗键自己的长按还宽时，它早就先判出长按、
         *  按住那条本来就作废了 —— 留这么宽只是不拦手改的配置。
         */
        const val COMBO_HOLD_MIN_MIN_MS = 40
        const val DEFAULT_COMBO_HOLD_MIN_MS = 100
        const val COMBO_HOLD_MIN_MAX_MS = 500

        /**
         * 「常用命令」最多几条 —— 20。
         *
         *  这一张表只放「一眼就想点的那几条」：它在绑定页占一整个网格，条目再多就变成
         *  第二份命令库，与「在这个格子里直接拿到一条能用的命令」这个初衷偏离。
         */
        const val MAX_COMMON_COMMANDS = 20

        val DEFAULT =
            GlobalConfig(
                enabled = DEFAULT_ENABLED,
                vibrate = false,
                colorOsApi = DEFAULT_COLOR_OS_API,
            )

        /** 从取值表还原全局配置 —— 与 [GlobalConfig.fieldValues] 成对，两条通道共用。 */
        fun ofFields(v: ConfigFields.Values): GlobalConfig =
            GlobalConfig(
                enabled = v.bool(ConfigFields.F_MODULE_ENABLED),
                vibrate = v.bool(ConfigFields.F_VIBRATE),
                // 手改过的文件可能写进 7 —— 越界的幅度会直接交给 `VibrationEffect`，
                //  所以在这一层就钳住（滑杆两端与这里同一个区间）。
                vibrateStrength = v.number(ConfigFields.F_VIBRATE_STRENGTH).coerceIn(VIBRATE_STRENGTH_RANGE),
                toastOnRun = v.bool(ConfigFields.F_TOAST_ON_RUN),
                toastFluid = v.bool(ConfigFields.F_TOAST_FLUID),
                toastTemplate = v.text(ConfigFields.F_TOAST_TEMPLATE),
                language = v.text(ConfigFields.F_LANGUAGE),
                palette = v.text(ConfigFields.F_PALETTE),
                theme = v.text(ConfigFields.F_THEME),
                colorOsApi = v.bool(ConfigFields.F_COLOR_OS_API),
                miniWindowAnim = v.bool(ConfigFields.F_MINI_WINDOW_ANIM),
                followDisplaySize = v.bool(ConfigFields.F_FOLLOW_DISPLAY_SIZE),
                // 与 vibrateStrength 同一档：手改文件写进越界值，在这一层就钳回滑杆两端
                pageScale = v.number(ConfigFields.F_PAGE_SCALE).coerceIn(PAGE_SCALE_MIN, PAGE_SCALE_MAX),
                // 与 pageScale 同一档：手改文件写进负数在这一层钳回「关」
                iconTintSeed = v.number(ConfigFields.F_ICON_TINT_SEED).coerceAtLeast(0),
                showCommonCommands = v.bool(ConfigFields.F_SHOW_COMMON_COMMANDS),
                comboPassthrough = v.bool(ConfigFields.F_COMBO_PASSTHROUGH),
                //  与 pageScale 同一档：手改文件写进越界值，在这一层钳回滑杆两端
                comboWindowMs = v.number(ConfigFields.F_COMBO_WINDOW_MS).coerceIn(COMBO_WINDOW_MIN_MS, COMBO_WINDOW_MAX_MS),
                comboWindowOn = v.bool(ConfigFields.F_COMBO_WINDOW_ON),
                comboHoldOn = v.bool(ConfigFields.F_COMBO_HOLD_ON),
                //  与 comboWindowMs 同一档：越界值在这一层钳回滑杆两端
                comboHoldMinMs =
                    v
                        .number(ConfigFields.F_COMBO_HOLD_MIN_MS)
                        .coerceIn(COMBO_HOLD_MIN_MIN_MS, COMBO_HOLD_MIN_MAX_MS),
            )
    }
}

/**
 * 整份配置。
 *
 *  函数数贴着 detekt 的 `TooManyFunctions` 阈值：多出来的那一族是**「公共项的按键级取值」**
 *  （`effectiveFor` / `comboPassthroughFor`）—— 它们就该长在这一层：判据只有一句
 *  「这一档开了独立设置就看自己那份，否则看公共设置」，拆到别处等于让每个调用方自己拼，
 *  而拼错一处的表现是「这台机器上这颗键不听话」。
 */
data class Config(
    val keys: Map<KeyId, KeyConfig>,
    /**
     * 用户收集来的按键（顺序 = 收集顺序）。
     *
     *  **内置键不在这里** —— 内置与自定义的全集是 [allKeys]。把内置键也塞进来会让
     *  「哪些是用户的东西」和「哪些是编译期就有的」混成一锅，删除 / 改名那些判据就得再各写一遍。
     *
     *  自定义键的**身份**是 `KeyId.id`（`ck1` `ck2`），键码只是它的一个属性：换一次驱动上报
     *  换了个码，绑定不该跟着丢（见 [KeyId.custom]）。
     */
    val customKeys: List<KeyId> = emptyList(),
    val global: GlobalConfig,
    /**
     * 用户是否已经确认过「风险提示与免责声明」—— 只由**本地**产生，决定「启用」之前要不要弹那一次。
     *
     * 存在配置里（而不是界面状态里）是**有意**的：
     *  · 清空配置按 [DEFAULT] 重建 ⇒ 这一位跟着没了 ⇒ 下次启用照样弹（「清空之后同样弹」就是这么来的）；
     *  · 它是**本机这一次的接受行为**，所以导出／导入一律把它丢掉（见 `ConfigBundle`）——
     *    一份文件不该替别人说「我已经接受了风险」。
     */
    val disclaimerAccepted: Boolean = false,
    /**
     * 用户是否已经确认过「自定义命令」那一页的风险提示 —— 与 [disclaimerAccepted] **分开记**。
     *
     *  两者说的是两件事：那一份讲的是「注入系统进程」这件事本身，这一份讲的是
     *  「自定义命令**不做任何检验或保护**，写什么就跑什么」。在首页确认过前者，不等于知道后者，
     *  所以不能共用一位。
     *
     *  同样存在配置里（理由与 [disclaimerAccepted] 一样）：清空配置按 [DEFAULT] 重建
     *  ⇒ 这一位跟着没了 ⇒ 下次进那一页照样弹；导出／导入也一律丢掉（见 `ConfigBundle`）。
     */
    val commandDisclaimerAccepted: Boolean = false,
    /**
     * 「打开应用」那一族的**显示名**：键是绑定的**身份**（`AppTarget.identity` —— `999@包名/类名`、
     * `999@包名/快捷方式id`），值是界面上那个名字（`微信 2` / `扫一扫`）。
     *
     * ## 为什么是单独一张表、不再塞进 token
     *
     *  早先名字是跟在目标后面写进 token 的（`app:999@包名/类名|微信 2`），每个读它的地方都得先
     *  切一刀 —— 漏切一处就退化成包名（真机：「选完快捷方式 / 界面，再进来显示的是包名」）。
     *  名字不是目标的一部分，是**显示信息**，所以单独存。
     *
     * ## 为什么不放进 `ConfigFields` 的字段表
     *
     *  那张表是**扁平**的（一个字段一个值），而且两条通道共用它 —— 这张表是映射，又只有界面
     *  读（执行侧只需要「起哪个组件」），进字段表就等于每次按键都把整张表广播给 `system_server`。
     *  所以它只走落盘这一条路：JSON 里单独一段（`ConfigDto.appNames`，键名 `app_names`），
     *  `ConfigCodec` 单独读写。注入侧读到的 `Config` 里这一项恒为空，没人读它。
     *
     *  与应用改名无关：名字只是显示，身份是 `AppTarget.identity`。改过名之后旧名字还在表里，
     *  界面优先用「这次清单里的新名字」（见 `ui/...` 的名字优先级），表只是冷启动时的兜底。
     */
    val appNames: Map<String, String> = emptyMap(),
    /**
     * **组合按键**（顺序 = 界面上的顺序，也是成员数相同时的优先级）。
     *
     *  与 `customKeys` / [appNames] 一样**只走落盘这一列**：它是一张列表，进不了
     *  [ConfigFields] 那种扁平字段表。判定侧的接线是**另一条广播**（`core/wire/ComboWire`）——
     *  那边逐档带成员、动作与长按那几档参数，注入侧据此自己组 `ComboKey`；
     *  也就是说这里的 `combos` 不进 `Config` 的扁平广播，不代表判定侧拿不到。
     */
    val combos: List<ComboKey> = emptyList(),
    /**
     * 这份配置的**名字** —— 多配置切换用。`""` = 「默认」那一份。
     *
     *  默认那份**不许改名、不许删除**（见 `core/config/ConfigProfiles`）：它是升级时自动落下的
     *  那一份，删掉就没地方回去了。其余配置必须起名字。
     *
     *  随落盘走（`ConfigDto.profile_name`），所以导出一份配置时名字跟着出去 ——
     *  导到另一台机器上它还叫原来的名字。
     */
    val profileName: String = "",
    /**
     * 这份配置的**唯一标识** —— 与名字、槽号都无关。
     *
     *  名字是**给人看的**、可以被改成任意字面量（甚至被抹成空），槽号会随着「切换 = 两份内容互换」
     *  在文件之间搬家：拿它们当身份，一旦名字丢了就会出现「两份都叫默认配置、谁都改不了名」
     *  这种死局（真机踩过）。所以身份单独一位：
     *
     *  · 「默认」那一份固定是 `ConfigProfiles.DEFAULT_PROFILE_ID`；
     *  · 其余每一份在新建 / 导入时分配一个随机 id，此后**跟着内容走**（与 [profileName] 同一条）。
     *
     *  空串 = 老文件（这个字段之前没有）—— 读的时候由 `ConfigProfiles` 按「非当前槽优先」补一个，
     *  下一次写盘自然带上。
     */
    val profileId: String = "",
    /**
     * 这份配置在**多配置列表里的位次**（0 = 「默认」那份，1、2、3… 按创建顺序）。
     *
     *  用它而不是槽号排序：切换配置是**两份内容互换**，内容会在槽之间搬家 —— 按槽号排的话，
     *  切一次某个配置就从第 3 行跳到第 1 行。位次跟着**内容**走（随落盘），所以列表顺序始终不变。
     *
     *  0 固定留给「默认」配置：它永远排在最上面。新建 / 导入时取「现有最大值 + 1」。
     */
    val profileOrder: Int = 0,
) {
    fun key(id: KeyId): KeyConfig = keys[id] ?: KeyConfig.defaultFor(id)

    /**
     * **这颗键现在生效吗** —— 两级相与：[GlobalConfig.enabled]（模块总闸）与 [KeyConfig.enabled]
     * （这一颗键自己的开关）。
     *
     *  这是**唯一判据**：注入侧的认领（`ConfigChannel.enabledOf`）与宿主侧的执行 / 松手
     *  （`GestureExec` 的那几处闸门）都调它，
     *  不许再就地写「`global.enabled && key(...).enabled`」—— 那一套曾经在四个地方各写一遍，
     *  漏改一处就是「某个路径上停用的键还在吞键」，而编译期与 CI 都看不见。
     *
     *  **宿主侧的「场景派发」不在其列**（`SideKeyGestureHooks`）：那条消息不带键的身份，
     *  它要回答的是「这一下键到底归不归模块」，判据是宿主侧的 `PressTrace`（刚有没有认领过），
     *  下到某一颗键就会拿 A 键的开关决定 B 键那次派发的去向。
     *
     *  界面侧要的往往只是**一半**：首页总开关那行看 `global.enabled`，⋮ 菜单那行看
     *  `key(id).enabled` —— 那些地方继续各取一半，别拿这个方法去替代（会让总闸锁死自己）。
     */
    fun isLive(id: KeyId): Boolean = global.enabled && key(id).enabled

    /**
     * 公共项的**按键级取值** —— 这一档开了「独立设置」就看自己那份，否则看公共设置。
     *
     *  返回的是**已经解析好的那一份**（`OwnSettings` 本体），调用点直接读字段：
     *  `config.effectiveFor(keyConfig).vibrate` / `.toastOnRun` / `.toastTemplate` …
     *  原先这里是五个 `xxxFor(owner)`，五份逐字同构的 `if (owner.useOwnSettings) owner.X else global.X`
     *  —— 规则只有一条，却要每个调用点记住「该问哪个方法、把谁当 owner 传」，改一条得改五处。
     *
     *  参数是 [OwnSettings] 而不是某个具体类型：单键与组合键在这一件事上**没有区别**，
     *  执行侧传谁进来都一样，不该有「组合那条走全局」这种分支 —— 那种分支正是把组合键
     *  当成独立存在的来源。
     */
    fun effectiveFor(owner: OwnSettings): OwnSettings = if (owner.useOwnSettings) owner else global

    /**
     * 这一组组合**实际**在「无操作」时怎么办。
     *
     *  与 [effectiveFor] 同一条规则：**总开关关着就跟随公共设置**（哪怕这一组以前设过自己那份）——
     *  否则界面灰着、执行侧却照旧用自己那份，是「关了没关掉」。
     *
     *  开着时看 [ComboKey.passthrough] —— 注意 `null` 现在**只有老配置会读到**：打开独立设置的
     *  那一刻会把公共值一并播进这一位（`KeySettingsScreen.withSeededComboFields`），
     *  所以「开着独立设置、这一项却还在跟随公共」只出现在还没被拨过的旧配置上（拨一下任何一项就补上）。
     *
     *  **只在「使用等待手势」开着时算数**（[comboWindowOnFor]）：透传的语义是「窗口那条按出来、
     *  可这一格没绑动作」，只认按住的那一档不经过这个形状（按住按出来的每一套都有自己明确的槽）。
     *  窗口关掉时这里恒为假 —— 界面那边同一份判据决定这一行显不显示，两处不会各说各的。
     */
    fun comboPassthroughFor(combo: ComboKey): Boolean =
        comboWindowOnFor(combo) && comboField(combo, combo.passthrough, global.comboPassthrough)

    /**
     * 这一组**认不认「窗口内等齐成员」** —— 规则与 [comboPassthroughFor] 同一条：
     * 独立设置关着就跟随公共（`GlobalConfig.comboWindowOn`），开着看这一组自己写没写过。
     *
     *  两位（窗口 / 按住）都走这一个口径 —— 它们说的是同一件事的两种按法，
     *  「跟随公共」这一层不该出现两套判据。
     */
    fun comboWindowOnFor(combo: ComboKey): Boolean = comboField(combo, combo.windowOn, global.comboWindowOn)

    /** 这一组**认不认「按住一个成员、再按剩下的成员」**（见 [comboWindowOnFor]）。 */
    fun comboHoldFor(combo: ComboKey): Boolean = comboField(combo, combo.holdOn, global.comboHoldOn)

    /**
     * 组合键上那几位「这一组自己写过没有」的公共项 —— 唯一一处判据。
     *
     *  `null` 是「没写过 ⇒ 跟公共」，但**总开关一关就整份看公共**（哪怕写过）——
     *  否则界面灰着、执行侧却照旧用自己那份，是「关了没关掉」。
     */
    private fun comboField(
        combo: ComboKey,
        own: Boolean?,
        public: Boolean,
    ): Boolean = if (!combo.useOwnSettings) public else own ?: public

    /**
     * 这一组**自己存着**的按键间隔（毫秒）—— 没写过（`WINDOW_FOLLOW_GLOBAL` / 老配置）就退回公共那份。
     *
     *  与 [comboWindowFor] 的差别只有一处：**不看总开关**。界面上那根滑杆要的正是这一份 ——
     *  它显示的得是「这一组自己的数」，否则拨一下总开关它就跟着换一份，看着像刚调好的值被改了
     *  （与单键那张卡里振动强度那一行同一条口径）。
     *
     *  判据只写在这里：`comboWindowFor` 也复用它，「没写过就退回公共」不会在两处各算一遍。
     */
    fun comboStoredWindowFor(combo: ComboKey): Int = combo.windowMs.takeIf { it > 0 } ?: global.comboWindowMs

    /**
     * 这一组**实际生效**的按键间隔（毫秒）—— 规则与 [comboPassthroughFor] 同一条：
     * 独立设置关着就跟随公共，开着就是这一组自己那份。
     *
     *  `WINDOW_FOLLOW_GLOBAL`（0）同样只剩**老配置**会读到：打开独立设置时这一位会被播成当时的
     *  公共值；界面上也没有「拖回跟随」这一档 —— 要回公共就关掉那一组的总开关。
     *
     *  为什么这条规则长在 [Config] 而不是判定侧：判定侧（`inject/`）手上只有这份 Config，
     *  界面手上也只有它 —— 两处各拼一次，「关了独立设置却不生效」这种错只会出现在其中一边。
     */
    fun comboWindowFor(combo: ComboKey): Int =
        if (!combo.useOwnSettings) {
            global.comboWindowMs
        } else {
            comboStoredWindowFor(combo)
        }

    /**
     * 这一组**自己存着**的「首键要先按住多久」（毫秒）—— 与 [comboStoredWindowFor] 同一条：
     * 不看总开关，滑杆要的就是这一份。
     */
    fun comboStoredHoldMinFor(combo: ComboKey): Int = combo.holdMinMs.takeIf { it > 0 } ?: global.comboHoldMinMs

    /**
     * 这一组**实际生效**的「首键要先按住多久」（毫秒）—— 规则与 [comboWindowFor] 同一条：
     * 独立设置关着就跟随公共，开着就是这一组自己那份。
     *
     *  判定侧（`ComboDetector` 的 `holdMinFor`）与界面都只走这一处。
     */
    fun comboHoldMinFor(combo: ComboKey): Int =
        if (!combo.useOwnSettings) {
            global.comboHoldMinMs
        } else {
            comboStoredHoldMinFor(combo)
        }

    /**
     * 这一档**整串**的容忍跨度（毫秒）—— 相邻两颗最多隔 [comboWindowFor]，n 颗就要 (n-1) 个窗口。
     *
     *  ## 为什么判定不能也按「一个窗口」算
     *
     *  窗口的语义一直是「**相邻两颗**之间最多隔多久」（[ComboPlan.collectMs] 从写下来那天起就是
     *  这句），两颗那一档里它也确实是这个意思。但三颗要是照样拿「第一颗到最后那颗只隔一个窗口」
     *  判，80ms 的两颗手感换成三颗就成了「三根手指必须几乎同时落」—— 真机表现是
     *  「三颗的组合怎么按都没反应，两颗的照常」，而日志里连一行都留不下。
     *
     *  所以整串的跨度按成员数放大：三颗 = 2 个窗口（默认 160ms）。判定（`ComboDetector.multiFor`
     *  与它的镜像 `ComboSequence`）、扫候选（[comboScanWindowMs]）、「按下先等多久」（
     *  `ConfigChannel.holdWindows`）三处都读这一支 —— 少改一处就是「扫得到、等不到」或反过来。
     *
     *  放大倍数封顶在 [ComboKey.MAX_JUDGE_KEYS] - 1 —— 那正是判定侧认的成员上限，多出来的倍数
     *  没有意义（成员数本身就到不了）。**别再按 [ComboKey.MAX_KEYS] 封**：那个是界面与收集那一侧
     *  的上限（三颗），拿它封会把四颗以上的整串跨度压成两个窗口，那几档于是在多成员那条路上
     *  永远凑不齐 —— 而表现与「这一档没配」完全一样，日志里只有「间隔超出窗口」那一行。
     *  这一位同时决定「参与组合的键按下后先等多久才回落单键」，所以极端的手改配置（九颗 + 1s 窗口）
     *  确实会让那几颗键等上几秒 —— 那是配置写出来的，不是判定算错的。
     */
    fun comboSpanFor(combo: ComboKey): Int {
        val gaps = (combo.keys.size - 1).coerceIn(1, ComboKey.MAX_JUDGE_KEYS - 1)
        return comboWindowFor(combo) * gaps
    }

    /**
     * 判定侧**扫描**用的窗口：这份配置里最长的那个按键间隔（至少是公共那一档）。
     *
     *  按下当前这一颗键时还不知道会凑成哪一组，所以扫候选必须按**最宽**的那一档来扫，
     *  命中之后再拿那一组自己的窗口复核（见 `ComboDetector`）—— 否则设了短窗口的组会被
     *  设了长窗口的组抢走配对，或者反过来永远配不上。
     *
     *  取的是每档的**整串跨度**（[comboSpanFor]）而不是单个窗口：三颗那一档的第三颗按下时，
     *  第一颗已经隔了两个窗口 —— 按一个窗口扫就永远看不到它，三颗的组合于是永远凑不齐。
     *
     *  只算 [liveCombos]：凑不齐的那一档按再宽的窗口扫也永远命中不了，让它参与取最大值
     *  只会把这一段扫描时长白撑出去。
     */
    val comboScanWindowMs: Int
        get() =
            maxOf(
                global.comboWindowMs,
                liveCombos().maxOfOrNull { comboSpanFor(it) } ?: 0,
            )

    /** 某个目标该显示的名字（按 [AppTarget.identity] 查）；没记过返回 `null`。 */
    fun appName(target: String): String? = appNames[AppTarget.identity(target)]

    /**
     * 记下（或覆盖）一个目标的名字；[name] 为空时**删掉**这一条。
     *
     *  写成「返回新对象」而不是改字段：配置是 `data class`、写盘走 [copy]，改字段那一套在这里
     *  没有先例（`write` 那一层按整份 `Config` 落盘并发广播）。
     */
    fun withAppName(
        target: String,
        name: String?,
    ): Config {
        val id = AppTarget.identity(target)
        return when {
            name.isNullOrBlank() -> if (id in appNames) copy(appNames = appNames - id) else this
            appNames[id] == name -> this
            else -> copy(appNames = appNames + (id to name))
        }
    }

    /**
     * 内置 + 自定义的**全集**（界面列键清单、广播打包都按它走）。
     *
     *  `by lazy`：注入侧每条按键事件都要按码查一次键（`keyForCode` → `allKeys`），
     *  每次现拼一个列表就是每按一次键多两三个分配 —— 而这跑在 `InputReader` 线程上。
     *  配置是不可变的（写配置走 `copy`），所以缓存一次就够。
     */
    val allKeys: List<KeyId> by lazy { KeyId.builtins + customKeys }

    /** 按 id 找键本身（要拿 `label` / `isCustom` 时用）；不认识返回 null。 */
    fun keyIdOf(id: String): KeyId? = allKeys.firstOrNull { it.id == id }

    /** 按 id 找那个键的配置（自定义键也认）；不认识返回 null。 */
    fun keyForId(id: String): KeyConfig? = keyIdOf(id)?.let { key(it) }

    /**
     * 键码 → 那一颗键的配置 —— [keyForCode] 的**索引**，建一次。
     *
     *  [allKeys] 那次 `lazy` 省掉的是「每次按键现拼一个列表」，剩下的一半仍在每次按键上：
     *  **线性扫一遍** `allKeys`、每颗键再 `List.contains` 一次自己的码表。而这条路径收的是
     *  系统里的**每一个**按键（不认识的随即放行），一次按键扫两遍（`keyForCode` 与
     *  [keyForRawCode] 各一遍）。建成表之后每次按键只剩一次 `HashMap` 查找。
     *
     *  重复码取**先出现**的那一颗（与原先 `firstOrNull` 的结果逐字一致）：用 `putIfAbsent`。
     *  `lazy` 默认带锁 —— 建表可能发生在 `InputReader` 线程上，别换成 `NONE`。
     */
    private val keysByCode: Map<Int, KeyConfig> by lazy {
        val map = HashMap<Int, KeyConfig>(allKeys.size * 2)
        for (k in allKeys) {
            val cfg = key(k)
            for (code in k.acceptedKeyCodes) map.putIfAbsent(code, cfg)
        }
        map
    }

    /** 同上，按**原始键码**建表 —— [keyForRawCode] 那条路。 */
    private val keysByRawCode: Map<Int, KeyConfig> by lazy {
        val map = HashMap<Int, KeyConfig>(allKeys.size * 2)
        for (k in allKeys) map.putIfAbsent(k.keyCode, key(k))
        map
    }

    /**
     * 按 keyCode 找那个键的配置 —— **认领路径的唯一入口**。
     *
     *  注入侧不再问「这是不是侧键」，改问「这个码在配置里是不是某一颗键」。查不到就放行：
     *  电源 / 音量那些必须原样交给系统，这一点没变。
     */
    fun keyForCode(code: Int): KeyConfig? = keysByCode[code]

    /**
     * 按**原始键码**找键 —— 与 [keyForCode] 的区别是**不含** ColorOS 的内部注入码。
     *
     *  PM（`PhoneWindowManager`）那条兜底路径能收到所有按键，策略类那条已经处理过 780 / 781 / 782，
     *  这里再认一次会让同一次按压被判两遍（策略类那条路径的说明见 `inject/SideKeyInterceptor`）。
     */
    fun keyForRawCode(code: Int): KeyConfig? = keysByRawCode[code]

    /**
     * 当前按着 [down] 这几颗键（`KeyId.id`）时，该由哪一档组合键接管 —— 没有就是 `null`。
     *
     *  **成员多的优先**：`{侧键, 音量上}` 与 `{侧键, 音量上, 音量下}` 同时成立时，用户按了三颗
     *  显然是冲更具体的那一档去的。成员数一样时取 [combos] 里靠前的那一档 —— 与界面上谁排在
     *  前面是同一个口径（`maxByOrNull` 保留第一个最大值）。
     *
     *  判据只看**集合包含**（[ComboKey.matches]），不看按下的先后：两颗键「同时按」时上报顺序
     *  由驱动决定，拿它当判据等于让配置依赖硬件细节。
     *
     *  成员里可能有**已经删掉的键**（自定义键删了、组合键还指着它）：那一档这辈子凑不齐，等于
     *  停用。不在读取时清理，是因为「删键」与「改组合」是两条独立的路，清理要跨两处同步 ——
     *  少一个成员反而会把用户写的「三键组合」悄悄降成两键。
     *
     *  ## 判据与 [comboOff] 的差别（**不是同一份**，别照那句话改）
     *
     *  这一处只问 [ComboKey.isUsable]（自己没关、成员够数）。**「成员键还都接管着」那一半不在
     *  这里判**：它由调用方按手上的真值补 —— 判定侧是 `inject/ConfigChannel.comboFor`
     *  （`ownedKeyIds`：任一颗不归模块管就整组不成立），界面 / 计划那一侧走 [liveCombos]
     *  （= [comboOff] 为假）。两处合起来才等于 [liveCombos]，这里单独用会把「成员被停用」的
     *  一档也选出来。
     *
     *  这里是「底层接口」那一半：判定侧（`inject/input`）判出「这几颗键一起按着」之后调它取档位，
     *  再按短按 / 长按取 [ComboKey.actionOf]。接线不在此刻。
     */
    fun comboFor(down: Set<String>): ComboKey? = combos.filter { it.isUsable && it.matches(down) }.maxByOrNull { it.keys.size }

    /**
     * 含有 [base] 这几颗键、且**成员更多**的那一档（`null` = 没有更宽的一档）。
     *
     *  这是「更宽的那一档还来不来得及」那道等待的判据（`ComboDetector.awaitWider`）：小的一档凑成
     *  之后要先问一句「还有更大的吗」，否则后者永远只剩「所有手指同时落」才成立 —— 小的一档在
     *  松手那一刻就派发了。四颗以上的链同理（换过去之后再问一次），判据里没有「两颗 / 三颗」
     *  这种数字，全按**真子集**算。
     *
     *  只看 [liveCombos]（成员键都还接管着的那些）：成员里有一颗被停用的一档永远凑不齐，
     *  为它等一个窗口只是让更小那一档白慢一拍。成员数不多于 [base] 的不算「更宽」。
     *
     *  参数收**键本身**而不是 id 集合：调用点在按键判定链上（`inject/input`），每按压一次都要过，
     *  在那里把 id 收进一个 `HashSet` 等于每次按压多一次分配（`tools/audit-perf.py` 的硬判据）。
     */
    fun widerComboOf(base: List<KeyId>): ComboKey? =
        liveCombos()
            //  「更宽的那一档」只可能是多成员那一档，而多成员只走窗口那条判据 —— 窗口关着的
            //  一档永远凑不齐，为它等一个窗口只是让更小那一档白慢一拍。
            .filter { c -> c.keys.size > base.size && base.all { k -> k.id in c.keys } && comboWindowOnFor(c) }
            .maxByOrNull { it.keys.size }

    /**
     * 这一档组合**现在跑不起来**吗 —— 自己关着，或**成员里任一键被停用**。
     *
     *  判据按成员集合算，不按颗数：两颗的组合与手改配置写进来的多按键组合（判定侧认到
     *  [ComboKey.MAX_JUDGE_KEYS]）走同一条规则 —— 少任何一颗键都凑不齐，这一档就是废的。
     *  界面据此标「已停用」、启停切换也据此判「要不要连带」。
     */
    fun comboOff(combo: ComboKey): Boolean =
        !combo.enabled ||
            combo.keys.any { member ->
                //  **成员键已经不在这张表里**（被删掉）与「被停用」同一档：这一档同样永远凑不齐。
                //  原先只查 `enabled`，这种组合会被界面显示成「还在用」，而判定侧再也不会有它 ——
                //  与 [comboFor] 的判据、以及「少任何一颗键这一档就是废的」那条注释都相反。
                val key = keys.entries.firstOrNull { it.key.id == member }?.value ?: return@any true
                !key.enabled
            }

    /**
     * 判定侧**真正要算**的那几档组合 —— [comboOff] 为假的那些。
     *
     *  与 [comboFor] 同一条口径（那颗键还接管着，才谈得上「凑得齐」），比 `ComboKey.isUsable`
     *  严一位：`isUsable` 只看这一档自己关没关、成员够不够数，**不看成员键是否被停用**。
     *
     *  三处读者都从这里取：注入侧每颗键「等多久」的账（`ConfigChannel.holdWindows`）、
     *  「参不参与组合」的成员表（`rebuildComboIndex`）、以及判定计划（`comboPlans`）。
     *  各自按 `isUsable` 筛的后果是「这一档永远凑不齐，参与它的键却照样等满一个窗口」——
     *  最长 1000ms 的白等，且列表上已经显示「已停用」。
     */
    fun liveCombos(): List<ComboKey> = combos.filterNot(::comboOff)

    /** 顶层那两位的取值 —— 与另两段同一套（见 `KeyConfig.fieldValues` 的说明）。 */
    fun fieldValues(): ConfigFields.Values =
        ConfigFields.Values(
            mapOf(
                ConfigFields.F_DISCLAIMER to disclaimerAccepted,
                ConfigFields.F_COMMAND_DISCLAIMER to commandDisclaimerAccepted,
            ),
        )

    companion object {
        val DEFAULT =
            Config(
                keys = KeyId.builtins.associateWith { KeyConfig.defaultFor(it) },
                global = GlobalConfig.DEFAULT,
            )

        /**
         * 拼一份配置 —— 扁平字段从取值表来，几张「表」的段由调用方读好传进来。
         *
         *  [keys] / [global] 由调用方各自按 [KeyConfig.ofFields] / [GlobalConfig.ofFields] 建好。
         *  [customKeys] 与 [keys] **必须对得上**：每一个自定义键都得在 `keys` 里有一条，
         *  否则 `key(id)` 会退回默认值，界面上看着像「刚收集的键绑定全丢了」。
         *
         *  `@Suppress("LongParameterList")`：这不是「参数太多、该拆对象」那种函数 —— 参数个数就是
         *  配置里那几张表的个数（扁平取值表 + 三张进不了字段表的段）。拆成一个 `Sections` 壳只是
         *  把它们挪个地方，读取处还得一层层解引用，而每加一张表就多一个要同步的落点。
         */
        fun ofFields(
            keys: Map<KeyId, KeyConfig>,
            global: GlobalConfig,
            v: ConfigFields.Values,
            /** 用户收集来的按键（`custom_keys` 那一段）—— 不在字段表里，由 `ConfigCodec` 单独读。 */
            customKeys: List<KeyId> = emptyList(),
            /** 名字表（`app_names` 那一段）—— 同上，由 [ConfigCodec] 单独读出来传进来。 */
            appNames: Map<String, String> = emptyMap(),
            /** 组合按键（`combos` 那一段）—— 同上。 */
            combos: List<ComboKey> = emptyList(),
        ): Config =
            Config(
                keys = keys,
                customKeys = customKeys,
                global = global,
                disclaimerAccepted = v.bool(ConfigFields.F_DISCLAIMER),
                commandDisclaimerAccepted = v.bool(ConfigFields.F_COMMAND_DISCLAIMER),
                appNames = appNames,
                combos = combos,
            )
    }
}

/**
 * 「常用命令」清单里的一条 —— 用户钉进那一段的一个动作。
 *
 *  与命令库是**两套东西**：这里存的是一条**动作写法**（`cmd:` / `app:` / `keyevent:` / `shell:` …），
 *  不是命令库里那条记录；于是它既能指回模块自带的几条（`cmd:common.wechat_scan`），
 *  也能直接指任意一个动作。写入绑定时原样落 [token]，执行侧不需要认识这张表。
 *
 * @param token 动作写法（`Action.parse` 认的那种）
 * @param name 用户起的名字；**空串 = 按动作自己念**（`cmd:` 走 `CommonCommands.labelOf`，
 *   其余走 `commandTitleOf`）—— 默认那几条因此能跟着界面语言变
 * @param showInBinding 要不要出现在「按键绑定页」那一段（关掉只是那一段不画，绑定本身不动）
 */
data class CommonCommand(
    val token: String,
    val name: String = "",
    val showInBinding: Boolean = true,
)
