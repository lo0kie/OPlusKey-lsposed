package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.log.XLog

/**
 * 配置字段的**广播侧声明表** —— 一个逻辑字段在广播里的名字、类型、默认值与取值范围只在这里写全。
 *
 * ## 只管广播这一条通道
 *
 *  · `*_EXTRA` —— 配置广播的 intent extra 名（`core/wire/ConfigBroadcast` 用）；
 *  · [Field.extra] 为 `null` 的那几项**不广播**（只落盘）：它们只作为 [Values] 的键存在，
 *    取值由 `ConfigDto` / `Config.ofFields` 自己映射。
 *
 * **落盘格式的真源是 `core/config/ConfigDto`**（`@SerialName` 与字段顺序）。本表**不登记** JSON 键：
 * 原先那半列没有一行代码读它，改 `@SerialName` 时它也不会跟着动 —— 两处各说一套，迟早分叉。
 *
 * 两列的稳定性本来就不同：
 *  · 落盘键**不能改名** —— 它已经落在用户机器的 `opluskey_config.conf` 里，改名等于换一份格式；
 *  · extra **可以改名** —— 发送与接收都是本模块、同一个版本，只要在同一次改动里两边一起改。
 *
 * ## 加一个配置字段要改几处
 *
 * 1. 本文件：加一条 `*_EXTRA` 常量，并在 [KEY_FIELDS] / [GLOBAL_FIELDS] 里加一行 [Field]
 *    （类型、默认值与上下限一起写：`Kind.Bool` / `Kind.Number` / `Kind.Text`）；
 * 2. `core/model/Config.kt`：给数据类加属性，并在它那**一对** `fieldValues()` / `ofFields()` 里加一行；
 * 3. `core/config/ConfigDto.kt`：线格式由 kotlinx.serialization 定义，字段名与默认值写在那边的
 *    属性与 `@SerialName` 上。
 *
 * **例外：「只落盘、不广播」的字段**（目前是 [F_KEY_VIBRATE_ON_NONE] 这一类）——
 * 第 1 步照旧加 `Field`，但 extra 传 `null` 且**不加进 [KEY_FIELDS]**。
 * 那种字段只有宿主读得到、注入侧不需要，进广播等于白占每次按键的 IPC 体积。
 * （曾经还有过 `F_OWN_SETTINGS_SEEDED` 这一类「只服务界面自己状态」的位，已随
 *  「独立设置不再抄公共值」一起删掉，见 `docs/changelog/2026-11-17-独立设置去掉播种.md`。）
 *
 * 于是两条通道各有自己的生成源：**广播**由本表驱动（extra + [Kind]），**落盘**由 DTO 驱动，
 * 取值区间两边都回到本表来钳。
 */
object ConfigFields {
    // `KeyConfig` —— 主开关与三个手势绑定。
    //
    //  这几条 `*_EXTRA` 是**基础名**，真正写进广播的名字前面还挂一个 `<keyId>_` 前缀
    //  （`plus_single` / `ck1_single` ……）—— 自定义按键是运行时才知道有几颗的，广播里必须
    //  按键分开存，否则所有键的绑定会互相覆盖。拼前缀的地方只有 `ConfigBroadcast.putFields`。
    const val ENABLED_EXTRA = "enabled"
    const val SINGLE_EXTRA = "single"
    const val DOUBLE_EXTRA = "double"
    const val LONG_EXTRA = "long"

    // 广播里那份「有哪些键」的清单（逗号分隔的 id）与每颗键的身份。
    //  落盘用的是 `custom_keys` 那段数组（见 `ConfigDto.customKeys`），只有广播才用这种扁平写法。
    const val KEY_IDS_EXTRA = "key_ids"

    /** 每颗键一条，实际 extra 名 = `<id>_code`。 */
    const val KEY_CODE_EXTRA = "code"

    /** 每颗键一条，实际 extra 名 = `<id>_name`。 */
    const val KEY_NAME_EXTRA = "name"

    // `KeyConfig` —— 长按的三个参数。
    const val LONG_PRESS_MS_EXTRA = "long_press_ms"
    const val LONG_REPEAT_EXTRA = "long_repeat"
    const val REPEAT_INTERVAL_MS_EXTRA = "long_repeat_interval_ms"

    /**
     * 「松手执行额外动作」（`KeyConfig.longRelease`）—— 长按照旧到点执行，抬手那一刻**再**执行这一档。
     *
     *  不改长按阈值、也不改长按本身的派发时机：它只是给「这一按已经判成长按」的抬手补一个动作
     *  （`GestureDetector.finishLong` 那一支）。
     */
    const val LONG_RELEASE_EXTRA = "long_release"

    /** 长按连续触发时，每一次重复都振动一次（关 = 只在长按那一下振一次）。 */
    const val REPEAT_VIBRATE_EXTRA = "long_repeat_vibrate"

    /**
     * 长按绑的是不是「多条命令」（`KeyConfig.longIsSerial`）—— **只有广播这一列**。
     * 它是发送侧**现算**的（要读命令库文件，注入侧读不到那个目录），不落盘。
     */
    const val LONG_SERIAL_EXTRA = "long_is_serial"

    /**
     * **某个场景块配了松手动作**（`KeyConfig.sceneRelease`）—— **只有广播这一列**。
     *
     *  注入侧要它才决定抬手那一刻**发不发** `GestureTrigger.Release`：场景表不走广播
     *  （见 `KeyConfig.scenes`），注入侧手上只有这一位是非。少了它，块里配的松手动作
     *  连事件都收不到 —— 界面配得出来、按下去什么都不发生。
     *
     *  同样由宿主**现算**（扫一遍这颗键的场景表），不落盘。
     */
    const val SCENE_RELEASE_EXTRA = "scene_release"

    /** 某个场景块给**长按**配了动作 —— 同样只有广播这一列（见 `KeyConfig.sceneLong`）。 */
    const val SCENE_LONG_EXTRA = "scene_long"

    /** 某个场景块给**「单击后长按」的松手**配了动作 —— 同样只有广播这一列。 */
    const val SCENE_CHAIN_RELEASE_EXTRA = "scene_chain_release"

    /**
     * **某个场景块给「单击后长按」这一档本身配了动作** —— 同样只有广播这一列。
     *
     *  与 [SCENE_LONG_EXTRA] 同一个用途：注入侧看不到场景表，而它要回答「这一档到底有没有人要」
     *  （`KeyConfig.clickThenLongActionExists` → `clickThenLongCounts`）。少了这一位，只在场景里
     *  配了这一档的键会被当成「开着但无操作」—— 判定侧连第 2 击的长按枪都不排，那个动作永远轮不到。
     */
    const val SCENE_CHAIN_EXTRA = "scene_chain"

    /**
     * 宿主算出的**最高绑定击数**（`KeyConfig.judgeClicks`）—— **只有广播这一列**。
     *
     *  与 [LONG_SERIAL_EXTRA] 同一条道理：注入侧读不到配置文件，而它必须知道「这颗键最多
     *  配到第几击」才能决定单击 / 双击要不要等着看后面的连击 —— 这个数由宿主从动作表算好带过来。
     *
     *  发的是**没有加过下限**的那个数：「单击后长按 ⇒ 数到第 2 击」那一抬由两侧各自加一次
     *  （宿主 `Config.clicksForJudge`、注入侧同一属性），所以两边算出来的结果逐字相同；
     *  收到抬过的数就分不出「双击这一档真绑了动作」与「只是要数到第 2 击」两种情形。
     *  0 = 老版本发来的（不认识这一项）⇒ 注入侧退回「只认三档」。
     */
    const val JUDGE_CLICKS_EXTRA = "judge_clicks"

    /**
     * **哪几档多击开着**（`KeyConfig.extraClicksOn`，元素是击数）—— 走广播。
     *
     *  注入侧要知道它：注入侧看不到 `extra_clicks` 那张表（它只随落盘走），而它必须回答
     *  「这颗键要不要数到第 3 / 4 击」——[JUDGE_CLICKS_EXTRA] 那个数由宿主算好，可**界面**也要
     *  按这一位决定那几页出不出现；两处读同一个来源才不会有「判定认三击、界面说没有」。
     *
     *  取值是**逗号分隔的击数**（`"3"` / `"3,4"` / 空串）：[Kind] 只有 Bool / Number / Text，
     *  集合走文本最省事，解析在 `KeyConfig.clicksOnOf`（写侧排序，指纹才稳定）。
     *
     *  默认空 = 老版本发来的广播里没有这一项 ⇒ 读成「没有多击」⇒ 与这一位出现之前一致
     *  （那时手改出来的 `extra_clicks` 只是存着）。
     */
    const val EXTRA_CLICKS_ON_EXTRA = "extra_clicks_on"

    /**
     * **「单击后长按」这一档启用没有**（`KeyConfig.clickThenLongOn`）—— 走广播。
     *
     *  注入侧必须读它：要不要在连击链的第 2 击上**排长按那一枪**由这一位决定。
     *  不带它的话，注入侧只能按「没开」处理 —— 用户在界面上打开这一档也不会生效。
     *
     *  与 [EXTRA_CLICKS_ON_EXTRA] 分两位：那一位管「数到第几下」，这一位管「第 2 击按住」，
     *  两者都不延长对方的窗口。
     */
    const val CLICK_THEN_LONG_ON_EXTRA = "click_then_long_on"

    /**
     * 「单击后长按」这一档**绑的动作**（`KeyConfig.clickThenLong`）—— 走广播。
     *
     *  注入侧必须读它：`finishLong` 派发这一档的松手之前要先问 `clickThenLongActionExists`
     *  （松手动作是「这一档执行完之后」的收尾，主语没有就不该有谓语），而那一问的第一位就是
     *  这一格有没有绑定。不给它，注入侧手上永远是 `Action.None` ⇒ 那一关恒假 ⇒ 用户在卡片里
     *  配的动作只剩「场景块里的松手」走通，卡片自己那一档的松手全链路静默不派发。
     *
     *  它确实进了广播 —— 与本表里 [LONG_RELEASE_EXTRA] / [CTL_RELEASE_EXTRA] 同一条：
     *  「注入侧不挑动作」只对**执行**成立，对**判据**不成立。
     */
    const val CTL_EXTRA = "click_then_long"

    // 「单击后长按」那一档自己的参数 —— 与长按那一族**同名不同槽**
    // （`long_press_ms` / `long_repeat` … 是长按的，下面这几条只属于两段式那一档）。
    const val CTL_PRESS_MS_EXTRA = "click_then_long_press_ms"
    const val CTL_REPEAT_EXTRA = "click_then_long_repeat"
    const val CTL_REPEAT_INTERVAL_MS_EXTRA = "click_then_long_repeat_interval_ms"
    const val CTL_REPEAT_VIBRATE_EXTRA = "click_then_long_repeat_vibrate"
    const val CTL_RELEASE_ON_EXTRA = "click_then_long_release_on"
    const val CTL_RELEASE_EXTRA = "click_then_long_release"

    /**
     * 「单击后长按」的「松手时触发」（`KeyConfig.clickThenLongFireOnRelease`）—— 与长按那一档
     * 的 [LONG_FIRE_ON_RELEASE_EXTRA] 同义：这一档的动作**等抬手才执行**。
     *
     *  为了让两档的「松手时触发」长得一模一样，它同样把「松手执行额外动作」与「连续触发」
     *  那三行挤掉（判定侧读 [KeyConfig.clickThenLongRepeats]，界面读 `LongPressGates`）。
     */
    const val CTL_FIRE_ON_RELEASE_EXTRA = "click_then_long_fire_on_release"

    /**
     * 「单击后长按」绑的是不是**多条命令** —— **只有广播这一列**（与 [LONG_SERIAL_EXTRA] 同一条）：
     * 注入侧要读命令库才知道，而那个文件它看不到，由宿主现算带过来。
     */
    const val CTL_SERIAL_EXTRA = "click_then_long_is_serial"

    /**
     * 「这颗键只参与非首键的按住组合」（`KeyConfig.comboOnly`）—— 单键手势整条不接管，只等组合成员。
     *
     *  走广播：注入侧判定要用（它决定这一下吞不吞）。
     */
    const val COMBO_ONLY_EXTRA = "combo_only"

    /**
     * 「双击这一档开着没有」（`KeyConfig.doubleClickOn`）—— 关掉时第二下按下就是新的一轮。
     *
     *  走广播：判定侧要按它封顶（`judgeCap`），界面的档位表也按它排。
     */
    const val DOUBLE_CLICK_ON_EXTRA = "double_click_on"

    /**
     * 「多击窗口内逐次等满」（`KeyConfig.waitMultiWindow`）—— 到档也等窗口走完再判。
     *
     *  走广播：这是判定时序，注入侧说了算。
     */
    const val WAIT_MULTI_WINDOW_EXTRA = "wait_multi_window"

    /**
     * 「多击窗口」（`KeyConfig.multiWaitMs`）—— 逐次等满那一档自己的窗口，0 = 跟随单击延迟。
     *
     *  走广播：与上一项同一条理由（判定时序，注入侧说了算）。
     */
    const val MULTI_WAIT_MS_EXTRA = "multi_wait_ms"

    // `GlobalConfig`。
    const val DOUBLE_CLICK_MS_EXTRA = "double_click_ms"
    const val VIBRATE_EXTRA = "vibrate"

    /**
     * 「这颗键用自己那套公共项」的总开关（`KeyConfig.useOwnSettings`），以及它底下那两项。
     *
     *  三个名字都带 `key_` 前缀、与全局那两项（[COLOR_OS_API_EXTRA] / 提示那一项）**刻意分开**：
     *  同名会让广播里逐键的 `<id>_color_os_api` 与全局那一格撞车（`ConfigBroadcast` 按 extra 名取值）。
     */
    const val OWN_SETTINGS_EXTRA = "use_own_settings"
    const val KEY_COLOR_OS_API_EXTRA = "key_color_os_api"
    const val KEY_VIBRATE_EXTRA = "key_vibrate"
    const val KEY_VIBRATE_STRENGTH_EXTRA = "key_vibrate_strength"
    const val KEY_TOAST_ON_RUN_EXTRA = "key_toast_on_run"
    const val KEY_TOAST_FLUID_EXTRA = "key_toast_fluid"
    const val KEY_TOAST_NOTIFY_EXTRA = "key_toast_notify"
    const val KEY_TOAST_TEMPLATE_EXTRA = "key_toast_template"

    /** 「多条命令逐条提示」的逐键那一格（`KeyConfig.stepNotice`）。 */
    const val KEY_STEP_NOTICE_EXTRA = "key_step_notice"

    const val LANGUAGE_EXTRA = "language"
    const val COLOR_OS_API_EXTRA = "color_os_api"

    /** 组合键的四格窗口（等待 / 按住 × 固定窗口起点 开 / 关）—— 判定要用，都走广播。 */
    const val COMBO_WAIT_WINDOW_MS_EXTRA = "combo_wait_window_ms"
    const val COMBO_WAIT_PAIR_MS_EXTRA = "combo_wait_pair_ms"
    const val COMBO_HOLD_WINDOW_MS_EXTRA = "combo_hold_window_ms"
    const val COMBO_HOLD_FOLLOWER_MS_EXTRA = "combo_hold_follower_ms"

    /** 按住那两档里**起点键要先按住多久**（毫秒）—— 同样判定在注入侧，走广播。 */
    const val COMBO_HOLD_MIN_MS_EXTRA = "combo_hold_min_ms"

    /**
     * 按住组合的首键**松手后仍执行它自己的长按** —— 判定在注入侧（`ComboDetector.onUp`
     * 那一支要读它决定补不补派），所以**必须走广播**：注入侧读不到配置文件，只吃广播。
     */
    const val COMBO_STARTER_KEEP_LONG_EXTRA = "combo_starter_keep_long"

    /** 等待组合的**固定窗口起点** —— 判定在注入侧（`ComboDetector`），走广播。 */
    const val COMBO_FIXED_WINDOW_START_EXTRA = "combo_fixed_window_start"

    /**
     * 按住组合的**固定窗口起点** —— 与 [COMBO_FIXED_WINDOW_START_EXTRA] 是**两个变量**（两张卡各管一种按法）。
     *
     *  同样走广播：`ComboDetector` 判按住档的成员接收窗口时读它（开 = 从首成员起一个 `hold` 窗口，
     *  关 = 每颗各自往前一个 `holdFollower`），而注入侧只吃广播。
     */
    const val COMBO_HOLD_FIXED_WINDOW_START_EXTRA = "combo_hold_fixed_window_start"

    /**
     * **模块总闸**（首页「启用按键绑定」那一行）—— 与 [ENABLED_EXTRA] 是两件事，别混：
     *
     *  · 本项：整个模块接管不接管（关掉 ⇒ 一颗键都不接管，页面整片灰）；
     *  · [ENABLED_EXTRA]：**这一颗键**接管不接管（⋮ 菜单里「停用这颗键」，侧键也有）。
     *
     *  以前只有后者，侧键那一份同时当两个用 ⇒「停用侧键」被迫等于「关掉模块」。拆开之后
     *  菜单里停用侧键只停那一颗，自定义键与页面都不受影响。
     */
    const val MODULE_ENABLED_EXTRA = "module_enabled"

    /**
     * 字段的取值类型 —— 广播那条路按它选 getter / setter，**默认值也在这里**。
     *
     * 为什么默认值要挂在这里：它有两个用处 —— 老文件 / 手写文件里缺这一项时补什么，以及
     * 「整段都缺」时补什么。原先是写在两条通道各自的读代码里（同一个默认值在两处各写一遍），
     * 于是「默认值改动一处漏一处」是可能的，而它的表现是「刚装上时界面上显示的值不对」。
     */
    sealed class Kind {
        /** 布尔。 */
        class Bool(
            val default: Boolean,
        ) : Kind()

        /**
         * 整数。[min] / [max] 是**两条通道共用**的钳制范围。
         *
         * 原先这两个界各写两份（`ConfigCodec.clamp` 与 `ConfigBroadcast.clampInt` 的调用点），
         * 手改文件写了越界值时两边的收口方式一模一样地重复着。
         */
        class Number(
            val default: kotlin.Int,
            val min: kotlin.Int,
            val max: kotlin.Int,
        ) : Kind()

        /** 文本。 */
        class Text(
            val default: String,
        ) : Kind()
    }

    /**
     * 一个逻辑字段在广播里的坐标 + 类型（也是 [Values] 里的键）。
     *
     * @param extra 广播 extra 名；`null` = 这一项不广播
     * @param kind 类型 + 默认值（+ 整数的钳制范围）
     */
    class Field(
        val extra: String?,
        val kind: Kind,
    )

    // ── `KeyConfig` 的字段

    /**
     * **这一颗键**接管不接管（`KeyConfig.enabled`）—— 与模块总闸 [F_MODULE_ENABLED] 是两件事。
     *
     *  侧键也走它：「停用这颗键」只停那一颗，模块与别的键照常。
     */
    val F_ENABLED = Field(ENABLED_EXTRA, Kind.Bool(false))

    /** 单击那一档的绑定（`Action` 的 token）。 */
    val F_SINGLE = Field(SINGLE_EXTRA, Kind.Text(Action.NONE))

    /** 双击那一档的绑定。 */
    val F_DOUBLE = Field(DOUBLE_EXTRA, Kind.Text(Action.NONE))

    /** 长按那一档的绑定。 */
    val F_LONG = Field(LONG_EXTRA, Kind.Text(Action.NONE))

    /** 长按阈值。 */
    val F_LONG_PRESS_MS =
        Field(
            LONG_PRESS_MS_EXTRA,
            Kind.Number(
                KeyConfig.DEFAULT_LONG_PRESS_MS,
                KeyConfig.MIN_LONG_PRESS_MS,
                KeyConfig.MAX_LONG_PRESS_MS,
            ),
        )

    /** 长按是否连续触发。 */
    val F_LONG_REPEAT = Field(LONG_REPEAT_EXTRA, Kind.Bool(false))

    /**
     * 「松手执行额外动作」的**开关**（`KeyConfig.longReleaseOn`）—— 打开时才在下面露出「额外动作」那一行。
     *
     *  与动作分两位：开关打开、动作还没挑时是「开着但没设置」，此时松手什么都不做；若把「有没有动作」
     *  直接当开关，用户在开关上点一下就会立刻执行一个他没挑过的动作。
     */
    const val LONG_RELEASE_ON_EXTRA = "long_release_on"

    /**
     * 「松手执行额外动作」（`KeyConfig.longRelease`）—— 长按**之后松手**那一刻执行的另一个动作。
     *
     *  默认 `none`（无操作）= 与这一位出现之前一致：判定侧不会发那一条松手广播。
     *  与 [F_LONG_REPEAT] **不冲突**（按住期间照常连续触发，松手再执行这一档）；
     *  只与「松手才触发」互斥。判定侧的判据见 `GestureDetector`。
     */
    val F_LONG_RELEASE = Field(LONG_RELEASE_EXTRA, Kind.Text(Action.NONE))

    /** 「松手执行额外动作」的开关（见 [LONG_RELEASE_ON_EXTRA]）。 */
    val F_LONG_RELEASE_ON = Field(LONG_RELEASE_ON_EXTRA, Kind.Bool(false))

    /**
     * 「松手才触发」（`KeyConfig.longFireOnRelease`）—— 长按动作本身**等抬手才执行**。
     *
     *  与「长按连续触发」「松手执行额外动作」互斥：它描述的是长按这一档的时序本身，
     *  同时开着没法定义「到底哪一下执行什么」。判定侧只读 `KeyConfig.longRepeats` 与
     *  `KeyConfig.releaseBound` 这两位（都把它排除在外）。
     */
    const val LONG_FIRE_ON_RELEASE_EXTRA = "long_fire_on_release"

    val F_LONG_FIRE_ON_RELEASE = Field(LONG_FIRE_ON_RELEASE_EXTRA, Kind.Bool(false))

    /** 连发间隔。 */
    val F_REPEAT_INTERVAL_MS =
        Field(
            REPEAT_INTERVAL_MS_EXTRA,
            Kind.Number(
                KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                KeyConfig.MIN_REPEAT_INTERVAL_MS,
                KeyConfig.MAX_REPEAT_INTERVAL_MS,
            ),
        )

    /**
     * 连续触发时是否每一下都振动（`KeyConfig.longRepeatVibrate`）。
     *
     *  默认 false = 与这一位出现之前一致：长按判出来的那一下振一次，之后每次重复不振。
     */
    val F_REPEAT_VIBRATE = Field(REPEAT_VIBRATE_EXTRA, Kind.Bool(false))

    /** 「单击延迟」—— **每颗键各一份**（原来在全局，见 `KeyConfig.doubleClickMs`）。 */
    val F_DOUBLE_CLICK_MS =
        Field(
            DOUBLE_CLICK_MS_EXTRA,
            Kind.Number(
                KeyConfig.DEFAULT_DOUBLE_CLICK_MS,
                KeyConfig.MIN_DOUBLE_CLICK_MS,
                KeyConfig.MAX_DOUBLE_CLICK_MS,
            ),
        )

    /** 「这颗键只参与非首键的按住组合」—— 单键手势交还系统，只等组合成员（默认关）。 */
    val F_COMBO_ONLY = Field(COMBO_ONLY_EXTRA, Kind.Bool(false))

    /** 「双击这一档开着没有」—— 默认开（双击是基础档，与三击 / 四击默认关相反）。 */
    val F_DOUBLE_CLICK_ON = Field(DOUBLE_CLICK_ON_EXTRA, Kind.Bool(true))

    /** 「多击窗口内逐次等满」—— 到档也等窗口走完再判（默认关 = 按完就判）。 */
    val F_WAIT_MULTI_WINDOW = Field(WAIT_MULTI_WINDOW_EXTRA, Kind.Bool(true))

    /**
     * 「多击窗口」—— 逐次等满那一档的窗口（`KeyConfig.multiWaitMs`）。
     *
     *  **下限是 0**：0 = 跟随「单击延迟」（老配置没有这一位，见那一位的 KDoc）。
     *  钳到 [KeyConfig.MIN_DOUBLE_CLICK_MS] 会把「跟随」这个值当场改写成那个下限 ——
     *  那正是升级之后手感突变的那类 bug。
     */
    val F_MULTI_WAIT_MS =
        Field(
            MULTI_WAIT_MS_EXTRA,
            Kind.Number(
                KeyConfig.FOLLOW_DOUBLE_CLICK_MS,
                0,
                KeyConfig.MAX_MULTI_WAIT_MS,
            ),
        )

    /**
     * **这颗键要不要用自己那套公共项**（`KeyConfig.useOwnSettings`）—— 总开关，默认关。
     *
     *  关着：走公共设置（接口选择 / 执行时提示 …），这三行在按键设置页置灰；
     *  开着：走这颗键自己那份（下面两个字段）。**按键级优先于公共设置**就是这一位在管事。
     */
    val F_OWN_SETTINGS = Field(OWN_SETTINGS_EXTRA, Kind.Bool(false))

    /** 这颗键自己的「使用 ColorOS 接口」—— 只在 [F_OWN_SETTINGS] 开着时算数。 */
    val F_KEY_COLOR_OS_API = Field(KEY_COLOR_OS_API_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_COLOR_OS_API))

    /** 这颗键自己的「振动反馈」—— 只在 [F_OWN_SETTINGS] 开着时算数。 */
    val F_KEY_VIBRATE = Field(KEY_VIBRATE_EXTRA, Kind.Bool(false))

    /** 这颗键自己的「振动强度」—— 只在 [F_OWN_SETTINGS] 开着时算数。 */
    val F_KEY_VIBRATE_STRENGTH =
        Field(
            KEY_VIBRATE_STRENGTH_EXTRA,
            Kind.Number(GlobalConfig.DEFAULT_VIBRATE_STRENGTH, GlobalConfig.VIBRATE_WEAK, GlobalConfig.VIBRATE_STRONG),
        )

    /** 这颗键自己的「无操作时振动」—— 只在 [F_OWN_SETTINGS] 开着时算数；宿主侧读，不进广播。 */
    val F_KEY_VIBRATE_ON_NONE = Field(null, Kind.Bool(GlobalConfig.DEFAULT_VIBRATE_ON_NONE))

    /** 这颗键自己的「执行时提示」—— 只在 [F_OWN_SETTINGS] 开着时算数。 */
    val F_KEY_TOAST_ON_RUN = Field(KEY_TOAST_ON_RUN_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_TOAST_ON_RUN))

    /** 这颗键自己的「提示走流体云」—— 只在 [F_OWN_SETTINGS] 开着时算数。 */
    val F_KEY_TOAST_FLUID = Field(KEY_TOAST_FLUID_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_TOAST_FLUID))

    /** 这颗键自己的「提示走通知」—— 只有落盘那一列（与 [F_KEY_VIBRATE_ON_NONE] 同一条：注入侧不弹提示）。 */
    val F_KEY_TOAST_NOTIFY = Field(null, Kind.Bool(GlobalConfig.DEFAULT_TOAST_NOTIFY))

    /** 这颗键自己的提示文案模板 —— 只在 [F_OWN_SETTINGS] 开着时算数。 */
    val F_KEY_TOAST_TEMPLATE = Field(KEY_TOAST_TEMPLATE_EXTRA, Kind.Text(GlobalConfig.DEFAULT_TOAST_TEMPLATE))

    /** 这颗键自己的「多条命令逐条提示」—— 只有落盘那一列（注入侧不弹提示，同 [F_KEY_TOAST_NOTIFY]）。 */
    val F_KEY_STEP_NOTICE = Field(null, Kind.Bool(GlobalConfig.DEFAULT_STEP_NOTICE))

    /** 长按绑的是不是串行命令 —— **只有广播那一列**，见 [LONG_SERIAL_EXTRA] 的说明。 */
    val F_LONG_SERIAL = Field(LONG_SERIAL_EXTRA, Kind.Bool(false))

    /** 某个场景块配了松手动作 —— **只有广播那一列**，见 [SCENE_RELEASE_EXTRA] 的说明。 */
    val F_SCENE_RELEASE = Field(SCENE_RELEASE_EXTRA, Kind.Bool(false))

    /** 某个场景块给长按配了动作 —— **只有广播那一列**，见 [SCENE_LONG_EXTRA] 的说明。 */
    val F_SCENE_LONG = Field(SCENE_LONG_EXTRA, Kind.Bool(false))

    /** 某个场景块配了「单击后长按」的松手动作 —— **只有广播那一列**，见 [SCENE_CHAIN_RELEASE_EXTRA]。 */
    val F_SCENE_CHAIN_RELEASE = Field(SCENE_CHAIN_RELEASE_EXTRA, Kind.Bool(false))

    /** 某个场景块给「单击后长按」这一档本身配了动作 —— **只有广播那一列**，见 [SCENE_CHAIN_EXTRA]。 */
    val F_SCENE_CHAIN = Field(SCENE_CHAIN_EXTRA, Kind.Bool(false))

    /**
     * 判定要认到第几击（见 [JUDGE_CLICKS_EXTRA]）。上限与「一屏步骤数」那档同宽：
     * 再多击的判定链已经没有实际意义（人的连击上限就三五下），手改出来的值在这里就钳掉。
     *
     *  **这个上限是唯一一处**：广播解析（[F_JUDGE_CLICKS]）与本地算出来的
     *  `Config.clicksForJudge`（它还要兜住手改过的 `extraClicks` 键）都引用它 ——
     *  两边各写一个数时，宿主按 20 击等、注入侧只认 9，第 10 击以后永远派发不出来。
     */
    const val MAX_JUDGE_CLICKS = 9

    val F_JUDGE_CLICKS = Field(JUDGE_CLICKS_EXTRA, Kind.Number(0, 0, MAX_JUDGE_CLICKS))

    /**
     * 「哪几档多击开着」（见 [EXTRA_CLICKS_ON_EXTRA]）—— 默认空串 = 一档都没有。
     *
     *  它**不跟随**「使用独立设置」那颗总闸（`KeyConfig.useOwnSettings`）：那是「用公共那份
     *  还是自己那份」，而这一位是这颗键自己的形态，两者正交。
     */
    val F_EXTRA_CLICKS = Field(EXTRA_CLICKS_ON_EXTRA, Kind.Text(""))

    /**
     * 「单击后长按」启用没有（见 [CLICK_THEN_LONG_ON_EXTRA]）—— 默认关，与「新装上没有这一档」一致。
     *
     *  它同样**不跟随**「使用独立设置」那颗总闸（理由见 `KeyConfig.clickThenLongOn`）。
     */
    val F_CLICK_THEN_LONG_ON = Field(CLICK_THEN_LONG_ON_EXTRA, Kind.Bool(false))

    /**
     * 「单击后长按」绑的动作（见 [CTL_EXTRA]）—— 默认 `none` = 与这一位出现之前一致。
     *
     *  与 [F_CLICK_THEN_LONG_ON] 分两位（开关 / 动作）：开关打开而动作还没挑时是「开着但没设置」，
     *  这一档不成立、松手也不补 —— 与「松手执行额外动作」那一族同一条口径。
     */
    val F_CTL = Field(CTL_EXTRA, Kind.Text(Action.NONE))

    /** 「单击后长按」的长按延迟（第二段按住多久算数）—— 上下限与长按那一档同一套。 */
    val F_CTL_PRESS_MS =
        Field(
            CTL_PRESS_MS_EXTRA,
            Kind.Number(
                KeyConfig.DEFAULT_LONG_PRESS_MS,
                KeyConfig.MIN_LONG_PRESS_MS,
                KeyConfig.MAX_LONG_PRESS_MS,
            ),
        )

    /** 「单击后长按」按住期间连续触发。 */
    val F_CTL_REPEAT = Field(CTL_REPEAT_EXTRA, Kind.Bool(false))

    /** 连续触发的间隔 —— 上下限与长按那一档同一套。 */
    val F_CTL_REPEAT_INTERVAL_MS =
        Field(
            CTL_REPEAT_INTERVAL_MS_EXTRA,
            Kind.Number(
                KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                KeyConfig.MIN_REPEAT_INTERVAL_MS,
                KeyConfig.MAX_REPEAT_INTERVAL_MS,
            ),
        )

    /** 连续触发时每次都振动。 */
    val F_CTL_REPEAT_VIBRATE = Field(CTL_REPEAT_VIBRATE_EXTRA, Kind.Bool(false))

    /** 「单击后长按」松手时再执行一个动作 —— 开关与动作两位（与 [F_LONG_RELEASE_ON] 同一条口径）。 */
    val F_CTL_RELEASE_ON = Field(CTL_RELEASE_ON_EXTRA, Kind.Bool(false))
    val F_CTL_RELEASE = Field(CTL_RELEASE_EXTRA, Kind.Text(Action.NONE))

    /**
     * 「单击后长按」的「松手时触发」（见 [CTL_FIRE_ON_RELEASE_EXTRA]）。
     *
     *  与长按那一档同一条互斥：开着它，这一档到点不执行，「松手执行额外动作」与「连续触发」
     *  都没有立足点 —— 判定侧由 [KeyConfig.clickThenLongRepeats] 收口，界面由 `LongPressGates` 置灰。
     */
    val F_CTL_FIRE_ON_RELEASE = Field(CTL_FIRE_ON_RELEASE_EXTRA, Kind.Bool(false))

    /** 「单击后长按」绑的是不是多条命令 —— **只有广播那一列**，见 [CTL_SERIAL_EXTRA]。 */
    val F_CTL_SERIAL = Field(CTL_SERIAL_EXTRA, Kind.Bool(false))

    /** `KeyConfig` 的全部广播字段。 */
    val KEY_FIELDS: List<Field> =
        listOf(
            F_ENABLED,
            F_SINGLE,
            F_DOUBLE,
            F_LONG,
            F_LONG_PRESS_MS,
            F_LONG_REPEAT,
            F_LONG_RELEASE,
            F_LONG_RELEASE_ON,
            F_LONG_FIRE_ON_RELEASE,
            F_REPEAT_INTERVAL_MS,
            F_REPEAT_VIBRATE,
            F_DOUBLE_CLICK_MS,
            F_COMBO_ONLY,
            F_DOUBLE_CLICK_ON,
            F_WAIT_MULTI_WINDOW,
            F_MULTI_WAIT_MS,
            F_OWN_SETTINGS,
            F_KEY_COLOR_OS_API,
            F_KEY_VIBRATE,
            F_KEY_VIBRATE_STRENGTH,
            F_KEY_TOAST_ON_RUN,
            F_KEY_TOAST_FLUID,
            F_KEY_TOAST_NOTIFY,
            F_KEY_TOAST_TEMPLATE,
            F_KEY_STEP_NOTICE,
            F_LONG_SERIAL,
            F_SCENE_RELEASE,
            F_SCENE_LONG,
            F_SCENE_CHAIN_RELEASE,
            F_SCENE_CHAIN,
            F_JUDGE_CLICKS,
            F_EXTRA_CLICKS,
            F_CLICK_THEN_LONG_ON,
            F_CTL,
            F_CTL_PRESS_MS,
            F_CTL_REPEAT,
            F_CTL_REPEAT_INTERVAL_MS,
            F_CTL_REPEAT_VIBRATE,
            F_CTL_RELEASE_ON,
            F_CTL_RELEASE,
            F_CTL_FIRE_ON_RELEASE,
            F_CTL_SERIAL,
        )

    // ── `GlobalConfig` 的字段

    /** 模块总闸（见 [MODULE_ENABLED_EXTRA]）。 */
    val F_MODULE_ENABLED = Field(MODULE_ENABLED_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_ENABLED))

    /** 触觉反馈。 */
    val F_VIBRATE = Field(VIBRATE_EXTRA, Kind.Bool(false))

    /**
     * 振动强度（三档：弱 / 中 / 强）—— **只有落盘那一列**（与 [F_TOAST_ON_RUN] 同理）：
     * 振那一下的是宿主进程里的执行器，它本来就读配置文件；注入侧不振，没必要广播。
     */
    val F_VIBRATE_STRENGTH =
        Field(null, Kind.Number(GlobalConfig.DEFAULT_VIBRATE_STRENGTH, GlobalConfig.VIBRATE_WEAK, GlobalConfig.VIBRATE_STRONG))

    /** 「无操作时振动」—— **只有落盘那一列**（宿主侧读，注入侧不振）。 */
    val F_VIBRATE_ON_NONE = Field(null, Kind.Bool(GlobalConfig.DEFAULT_VIBRATE_ON_NONE))

    /** 执行时提示 —— **只有落盘那一列**：它由宿主侧弹，注入侧不读它。 */
    val F_TOAST_ON_RUN = Field(null, Kind.Bool(GlobalConfig.DEFAULT_TOAST_ON_RUN))

    /**
     * 执行时提示走流体云 —— **只有落盘那一列**（与 [F_TOAST_ON_RUN] 同理）：
     * 换的是宿主执行器把提示交给谁（Toast / 流体云），注入侧两条都不参与。
     */
    val F_TOAST_FLUID = Field(null, Kind.Bool(GlobalConfig.DEFAULT_TOAST_FLUID))

    /**
     * 执行时提示走通知 —— **只有落盘那一列**（与 [F_TOAST_FLUID] 同理）：
     * 换的是宿主执行器把提示交给谁（Toast / 流体云 / 通知），注入侧三条都不参与。
     */
    val F_TOAST_NOTIFY = Field(null, Kind.Bool(GlobalConfig.DEFAULT_TOAST_NOTIFY))

    /**
     * 「执行时提示」那一句的**文案模板**（`$key` / `$gesture` / `$action` 与 `$action` 拆开的三段，
     * 见 `ToastNotice`）—— **只有落盘那一列**（与 [F_TOAST_ON_RUN] 同理）：它由宿主侧弹，注入侧不读它。
     */
    val F_TOAST_TEMPLATE = Field(null, Kind.Text(GlobalConfig.DEFAULT_TOAST_TEMPLATE))

    /**
     * 多条命令**逐条提示**（每条步骤各念一句；关着时整条只念一句命令名）。
     *
     *  只有落盘那一列：念不念、念什么全在宿主执行侧，注入侧不参与。
     */
    val F_STEP_NOTICE = Field(null, Kind.Bool(GlobalConfig.DEFAULT_STEP_NOTICE))

    /** 界面语言。 */
    val F_LANGUAGE = Field(LANGUAGE_EXTRA, Kind.Text(GlobalConfig.DEFAULT_LANGUAGE))

    /**
     * 系统配色 —— **只有落盘那一列**：换的是这个页面自己的配色，注入侧不读它。
     *
     *  取值是色板 id（字符串），上下限与合法清单都在界面层（`ui/model/PagePalette`）——
     *  共用层不认识「有哪些色板」，理由见 `GlobalConfig.palette` 的说明。
     */
    val F_PALETTE = Field(null, Kind.Text(GlobalConfig.DEFAULT_PALETTE))

    /**
     * 界面颜色（跟随系统 / 固定浅 / 固定深）—— **只有落盘那一列**：它只决定这个页面自己的深浅。
     *
     *  注入侧不读它（那边没有界面），广播里也就不必带。
     */
    val F_THEME = Field(null, Kind.Text(GlobalConfig.DEFAULT_THEME))

    /**
     * 使用 Monet 配色 —— **只有落盘那一列**：换的是这个页面自己的整套色板，注入侧不读它。
     *
     *  与 [F_THEME] 分成两格：深浅与配色各占一行（界面上也是两行），组合起来才有四种样子。
     *  拆之前它压在 [F_THEME] 的值里（`light_monet` 那种），一个字段读不出来两件事。
     */
    val F_MONET_COLORS = Field(null, Kind.Bool(GlobalConfig.DEFAULT_MONET_COLORS))

    /** 使用 ColorOS 专属系统接口（手电筒 / 免打扰 / 响铃模式等）。关闭时走 AOSP 标准接口。 */
    val F_COLOR_OS_API = Field(COLOR_OS_API_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_COLOR_OS_API))

    /**
     * 「前台转小窗」走带动画那条路 —— **只有落盘那一列**（与 [F_TOAST_ON_RUN] 同理）：
     * 读它的是宿主进程里的执行器，它本来就读配置文件。
     */
    val F_MINI_WINDOW_ANIM = Field(null, Kind.Bool(GlobalConfig.DEFAULT_MINI_WINDOW_ANIM))

    /**
     * 组合键的四格窗口（`GlobalConfig.comboWaitWindowMs` 那一段，毫秒）。
     *
     *  都走**广播**那一列 —— 判定它们的是注入侧（`ComboDetector` 跑在 system_server），
     *  那边读不到配置文件、只吃广播。
     */
    val F_COMBO_WAIT_WINDOW_MS =
        Field(
            COMBO_WAIT_WINDOW_MS_EXTRA,
            Kind.Number(
                GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                GlobalConfig.COMBO_WINDOW_MIN_MS,
                GlobalConfig.COMBO_WINDOW_MAX_MS,
            ),
        )

    val F_COMBO_WAIT_PAIR_MS =
        Field(
            COMBO_WAIT_PAIR_MS_EXTRA,
            Kind.Number(
                GlobalConfig.DEFAULT_COMBO_PAIR_MS,
                GlobalConfig.COMBO_WINDOW_MIN_MS,
                GlobalConfig.COMBO_PAIR_MAX_MS,
            ),
        )

    val F_COMBO_HOLD_WINDOW_MS =
        Field(
            COMBO_HOLD_WINDOW_MS_EXTRA,
            Kind.Number(
                GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                GlobalConfig.COMBO_WINDOW_MIN_MS,
                GlobalConfig.COMBO_WINDOW_MAX_MS,
            ),
        )

    val F_COMBO_HOLD_FOLLOWER_MS =
        Field(
            COMBO_HOLD_FOLLOWER_MS_EXTRA,
            Kind.Number(
                GlobalConfig.DEFAULT_COMBO_PAIR_MS,
                GlobalConfig.COMBO_WINDOW_MIN_MS,
                GlobalConfig.COMBO_PAIR_MAX_MS,
            ),
        )

    /**
     * 组合键：**按住那两档里起点键要先按住多久**（毫秒）。
     *
     *  与 [F_COMBO_WINDOW_MS] 同一条：判定在注入侧，所以走广播。它管的是「一起按」与
     *  「按住了一颗再按另一颗」的分界 —— 两颗键相差不到这个数就不算按住。
     */
    val F_COMBO_HOLD_MIN_MS =
        Field(
            COMBO_HOLD_MIN_MS_EXTRA,
            Kind.Number(
                GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
                GlobalConfig.COMBO_HOLD_MIN_MIN_MS,
                GlobalConfig.COMBO_HOLD_MIN_MAX_MS,
            ),
        )

    /**
     * 按住那两档的**场景在什么时候定下来**（首键按下 / 成员键按下）。
     *
     *  只落盘、不进广播：判场景的是宿主（`ConditionProbe`），注入侧只负责把「首键按下」
     *  「组合凑成」两个时刻报上来，用哪一份快照由宿主按这一位决定。
     */
    val F_COMBO_SCENE_ON_STARTER = Field(null, Kind.Bool(GlobalConfig.DEFAULT_COMBO_SCENE_ON_STARTER))

    /**
     * 按住组合的首键**松手后仍执行它自己的长按**（`GlobalConfig.comboStarterKeepLong`）。
     *
     *  **走广播**（[COMBO_STARTER_KEEP_LONG_EXTRA]）：判据在注入侧 —— `ComboDetector.onUp`
     *  收尾时要读这一位决定补不补派，而注入侧读不到配置文件、只吃广播。
     *
     *  这一位原先是 `Field(null, …)`，理由写的是「抬手本身已经在注入侧判完了，走广播只是
     *  让改动晚一拍生效」—— 那句把因果说反了：**正因为判据在注入侧，才非走广播不可**。
     *  漏了它之后的表现是：设置页写得进去、界面也显示已开启，但注入侧拿到的永远是默认
     *  `false`，开关拨了没有任何效果，连配置指纹都不变（指纹按 extras 算，这一位不在其中）。
     */
    val F_COMBO_STARTER_KEEP_LONG =
        Field(COMBO_STARTER_KEEP_LONG_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_COMBO_STARTER_KEEP_LONG))

    /**
     * 等待组合的**固定窗口起点**（`GlobalConfig.comboFixedWindowStart`）。
     *
     *  **走广播**（[COMBO_FIXED_WINDOW_START_EXTRA]）：判据在注入侧 —— `ComboDetector` 判
     *  窗口起点时要读这一位，而注入侧读不到配置文件、只吃广播。
     */
    val F_COMBO_FIXED_WINDOW_START =
        Field(COMBO_FIXED_WINDOW_START_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START))

    /**
     * 按住组合的**固定窗口起点**（`GlobalConfig.comboHoldFixedWindowStart`）。
     *
     *  **走广播**（[COMBO_HOLD_FIXED_WINDOW_START_EXTRA]）：`ComboDetector` 判按住档的成员接收窗口时
     *  读这一位，而注入侧读不到配置文件。少了它，注入侧那一位永远停在默认 `false` ⇒
     *  「按住组合」卡上那颗开关拨了没反应（而等待组合那颗照常生效，更难看出是哪一半没接上）。
     */
    val F_COMBO_HOLD_FIXED_WINDOW_START =
        Field(COMBO_HOLD_FIXED_WINDOW_START_EXTRA, Kind.Bool(GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START))

    /**
     * 界面缩放（百分比）—— 同样只有落盘那一列。上下限就是滑杆两端（[GlobalConfig.PAGE_SCALE_MIN]
     * ~ [GlobalConfig.PAGE_SCALE_MAX]）：手改文件写进 10 或 900 会被这一层钳回来。
     */
    val F_PAGE_SCALE =
        Field(
            null,
            Kind.Number(GlobalConfig.DEFAULT_PAGE_SCALE, GlobalConfig.PAGE_SCALE_MIN, GlobalConfig.PAGE_SCALE_MAX),
        )

    /**
     * 随机图标配色的种子（`0` = 关）—— 同样只有落盘那一列：颜色只在界面层算，
     * 注入侧没有图标可上色。
     */
    val F_ICON_TINT_SEED =
        Field(null, Kind.Number(GlobalConfig.DEFAULT_ICON_TINT_SEED, 0, Int.MAX_VALUE))

    /**
     * 按键绑定页露不露出「常用命令」那一段 —— **只有落盘那一列**：那是页面自己的显示项，
     * 注入侧不读它（它也不认识界面）。
     */
    val F_SHOW_COMMON_COMMANDS = Field(null, Kind.Bool(GlobalConfig.DEFAULT_SHOW_COMMON_COMMANDS))

    /**
     * 「减少界面图标」—— **只有落盘那一列**：卡片行画不画行首那颗字形是页面自己的事，
     * 判定与执行都不读（页面级通过 `LocalReduceRowIcons` 递给每一行）。
     */
    val F_REDUCE_ICONS = Field(null, Kind.Bool(GlobalConfig.DEFAULT_REDUCE_ICONS))

    /**
     * 组合两列的排列次序（`GlobalConfig.waitComboSort` / `holdComboSort`）—— **只有落盘那一列**：
     * 那是页面自己的显示偏好，判定与执行都不读。
     *
     *  存文本 id（见 `GlobalConfig.SORT_BY_CREATED` 那一组）：枚举在界面层，
     * 与 [F_PALETTE] / [F_THEME] 同一条。
     */
    val F_WAIT_COMBO_SORT = Field(null, Kind.Text(GlobalConfig.DEFAULT_COMBO_SORT))
    val F_HOLD_COMBO_SORT = Field(null, Kind.Text(GlobalConfig.DEFAULT_COMBO_SORT))

    //  —— 公共设置里的「单击」三项 ——
    //
    //  **只落盘、不进广播**（`Field(null, …)`）：注入侧读的是**每颗键**那三个 extra
    //  （`<keyId>_double_click_ms` 等），而那三格在广播前已经按「这颗键开没开独立设置」
    //  解析成生效值了（见 `KeyConfig.fieldValues(global)`）。再单独广播一份公共值，
    //  注入侧还得自己判一次该用哪一份 —— 两份判据迟早对不上。

    /** 公共设置里的单击延迟（毫秒）。 */
    val F_GLOBAL_DOUBLE_CLICK_MS =
        Field(null, Kind.Number(KeyConfig.DEFAULT_DOUBLE_CLICK_MS, KeyConfig.MIN_DOUBLE_CLICK_MS, KeyConfig.MAX_DOUBLE_CLICK_MS))

    /** 公共设置里的判定时机（`false` = 固定窗口起点）。 */
    val F_GLOBAL_WAIT_MULTI_WINDOW = Field(null, Kind.Bool(false))

    /** 公共设置里的等待窗口（0 = 跟随单击延迟）。 */
    val F_GLOBAL_MULTI_WAIT_MS =
        Field(null, Kind.Number(KeyConfig.FOLLOW_DOUBLE_CLICK_MS, KeyConfig.FOLLOW_DOUBLE_CLICK_MS, KeyConfig.MAX_MULTI_WAIT_MS))

    /**
     * **界面振动反馈**（`GlobalConfig.uiVibrate`）—— 点行 / 拨开关 / 拖滑杆那一下的触觉。
     *
     *  只落盘、不进广播：它只影响**界面自己**振不振，注入侧与宿主执行器都不读
     *  （执行时那一下是 [F_VIBRATE]，另一颗）。
     */
    val F_GLOBAL_UI_VIBRATE = Field(null, Kind.Bool(true))

    /**
     * **使用预测返回手势**（`GlobalConfig.predictiveBack`）—— 返回时让页面**跟着手指走**。
     *
     *  与 [F_GLOBAL_UI_VIBRATE] 同一档：只落盘、不进广播。读它的是宿主进程里的返回拦截
     *  （`ui/hook/BackInterception`），那份配置与页面共用同一个 `ConfigState`。
     *
     *  默认开：平台给了逐帧进度就用上，那是系统级的手感；关掉退回「松手才弹」的一次性返回。
     */
    val F_GLOBAL_PREDICTIVE_BACK = Field(null, Kind.Bool(true))

    /**
     * **显示更详细的按键设置**（`GlobalConfig.detailedKeySettings`）—— 按键设置页收不收全部参数。
     *
     *  与 [F_GLOBAL_UI_VIBRATE] 同一档：只落盘、不进广播。它只管**页面画多少行**，
     *  判定与执行一个字都不读它，注入侧也就没有必要知道。
     *
     *  默认关：先按「常用项」这一档给最省事的界面；要细调的人自己打开。
     */
    val F_GLOBAL_DETAILED_KEY_SETTINGS = Field(null, Kind.Bool(false))

    /** `GlobalConfig` 的全部广播字段。 */
    val GLOBAL_FIELDS: List<Field> =
        listOf(
            F_MODULE_ENABLED,
            F_VIBRATE,
            F_VIBRATE_STRENGTH,
            F_TOAST_ON_RUN,
            F_TOAST_NOTIFY,
            F_STEP_NOTICE,
            F_LANGUAGE,
            F_PALETTE,
            F_THEME,
            F_MONET_COLORS,
            F_COLOR_OS_API,
            F_MINI_WINDOW_ANIM,
            F_COMBO_WAIT_WINDOW_MS,
            F_COMBO_WAIT_PAIR_MS,
            F_COMBO_HOLD_WINDOW_MS,
            F_COMBO_HOLD_FOLLOWER_MS,
            F_COMBO_HOLD_MIN_MS,
            F_COMBO_SCENE_ON_STARTER,
            F_COMBO_STARTER_KEEP_LONG,
            F_COMBO_FIXED_WINDOW_START,
            F_COMBO_HOLD_FIXED_WINDOW_START,
            F_PAGE_SCALE,
            F_ICON_TINT_SEED,
            F_SHOW_COMMON_COMMANDS,
            F_REDUCE_ICONS,
            F_WAIT_COMBO_SORT,
            F_HOLD_COMBO_SORT,
            F_GLOBAL_DETAILED_KEY_SETTINGS,
        )

    // ── `Config` 自己那两位（正文顶层、都只落盘，不进广播）

    /** 已确认「风险提示与免责声明」。 */
    val F_DISCLAIMER = Field(null, Kind.Bool(false))

    /** 已确认「自定义命令不做任何检验」那一份提示。 */
    val F_COMMAND_DISCLAIMER = Field(null, Kind.Bool(false))

    /**
     * 某一项的取值 —— 读取方（两条通道）与写入方都过这一个壳，于是「字段类型」这件事只在一处判。
     *
     * 取值缺项 / 类型不符一律退回该字段 [Kind] 自带的默认值：读取方本会为每个字段填上值，
     * 所以这一层是「表与数据类对不上」时的最后一道 —— 宁可退回默认值，也不要抛异常
     * （广播那条路跑在 system_server 的热路径上，抛异常就是系统进程崩，见 `AGENTS.md` 五之二）。
     */
    class Values(
        private val map: Map<Field, Any>,
    ) {
        fun bool(f: Field): Boolean = map[f] as? Boolean ?: (f.kind as? Kind.Bool)?.default ?: false

        fun number(f: Field): kotlin.Int = map[f] as? kotlin.Int ?: (f.kind as? Kind.Number)?.default ?: 0

        fun text(f: Field): String = map[f] as? String ?: (f.kind as? Kind.Text)?.default.orEmpty()

        /** 建这份取值表的原始映射（写方按字段表遍历时用）。 */
        fun raw(): Map<Field, Any> = map
    }

    /**
     * 某个 extra 名对应的类型 —— 广播那条「按类型取原始值」的路（`ConfigBroadcast.rawExtra`）用它。
     *
     *  返回 `null` = 这个名字没登记在表里。调用方按「文本」兜底（与本表出现之前的兜底分支一致），
     *  于是「新增 extra 忘了登记类型」不会变成异常，只是取法保守一档。
     */
    fun kindOfExtra(name: String): Kind? = (KEY_FIELDS + GLOBAL_FIELDS).firstOrNull { it.extra == name }?.kind

    /**
     * 广播 extra 名 → **基础名**（去掉 `<keyId>_` 前缀）。不认识的前缀原样返回。
     *
     *  逐键那几项在广播里叫 `plus_single` / `ck1_single`，而字段表登记的是 `single`。
     *  [ConfigBroadcast.tag] 要按**类型**取 extra（类型不对 `getString` 会抛 `ClassCastException`），
     *  所以取类型之前必须先剥掉前缀 —— 否则指纹会把所有逐键字段都当成「取不到值」，
     *  两份不同的配置算出同一个指纹，界面就会报「已同步」。
     *
     *  只认两种前缀：内置键的 id（`plus`）与自定义键的 `ck<数字>`。两者都由 [KeyId] 给出，
     *  不在这里另抄一份字面量。
     */
    fun baseExtraName(name: String): String = KEY_EXTRA_PREFIX.matchEntire(name)?.groupValues?.get(1) ?: name

    private val KEY_EXTRA_PREFIX =
        Regex(
            "^(?:" + Regex.escape(KeyId.PLUS.id) + "|" + Regex.escape(KeyId.CUSTOM_ID_PREFIX) + "\\d+)_(.+)$",
        )
}

// 「按键清单」的增删改 —— 与 `Config` 那批**只读取值**的方法分开放。
//
//  为什么不写在 `Config` 类里：那几个方法让类的方法数撞上 detekt 的 `TooManyFunctions`
//  （上限 11，而 `Config` 已经 8 个）。拆成扩展函数既守住闸门，也把「怎么改一份配置」
//  与「怎么读一份配置」分成两屏看。

/**
 * 下一个可用的自定义键 id —— **现有最大序号 + 1**。
 *
 *  不回收「中间」的号：删掉第 2 颗再收一颗，拿到的仍是最大 + 1，不会撞上还活着的 id。
 *  **但删掉最大的那颗之后，下一个会重新用上它** —— 配置里没有「历史最高号」这一位，
 *  所以这里额外把**组合成员引用到的 id** 也算进「used」：那些引用还活在 `combos` 里，
 *  复用同一个 id 会让旧组合突然指向新收的这颗键。
 */
fun Config.nextCustomKeyId(): String {
    //  **三份数据都要扫**：`customKeys` 是「有哪些键」，`keys` 里还可能留着已从清单摘掉的条目
    //  （见 `ConfigActions` 那段说明），`combos` 里还留着旧组合的成员 id。
    //  只扫前者时，新分配的 id 会撞上那条还活着的条目，`withCustomKey` 直接把它覆盖掉。
    val ids = customKeys.map { it.id } + keys.keys.map { it.id } + combos.flatMap { it.keys }
    val used = ids.mapNotNull { it.removePrefix(KeyId.CUSTOM_ID_PREFIX).toIntOrNull() }
    return KeyId.customId((used.maxOrNull() ?: 0) + 1)
}

/**
 * 收下一颗自定义键 —— 同时建好它的绑定，并**直接打开接管**。
 *
 *  两处一起写是必须的：`customKeys` 是「有哪些键」，`keys` 是「每颗键的绑定」。
 *  只写前者会让 `key(id)` 退回默认值而界面看着像「刚收的键绑定丢了」；
 *  只写后者则这颗键根本不会被广播出去（`allKeys` 不含它）⇒ 注入侧永远认领不到。
 *
 * ## 为什么 `enabled` 直接给 `true`
 *
 *  `KeyConfig.defaultFor` 对**自定义键**给 `enabled = false`（"装上模块不改变行为" 是默认值，
 *  见它的注释）。自定义键不能照搬：那颗键**没有独立的总开关**，页面闸门就是它自己的
 *  `enabled` —— 默认关着的话，用户刚收完键，绑定页是整片灰的、一个动作都选不了，
 *  而他又没有别的地方能把它打开（死锁）。
 *
 *  「按下想添加的按键」这个动作本身就是用户的明确意愿，收集即接管。
 *  三档绑定此时都是 `Action.None`（按下什么都不做），与侧键「开着但还没配」同一个表现。
 */
fun Config.withCustomKey(key: KeyId): Config =
    copy(
        keys = keys + (key to KeyConfig.defaultFor(key).copy(enabled = true)),
        // 先摘掉同 id 的旧条目再追加：`customKeys + key` 会留下两条同 id 的，
        //  之后「删一颗」只删掉一条、另一条还活着（清单与 keys 就此对不上）。
        customKeys = customKeys.filterNot { it.id == key.id } + key,
    )

/**
 * 删掉一颗键（只对自定义键有意义）；三处一起摘，理由同 [withCustomKey]。
 *
 * ## 为什么连组合一起摘
 *
 *  组合的成员存的是**键 id**（`ComboKey.keys`）。只摘 `keys` 与 `customKeys` 的话，含这颗键的
 *  那几档会留下一个指向不存在键的 id —— 执行侧按 id 找键，找不到就是**静默失效**：那一档永远
 *  凑不成，而界面上还画得好好的。
 *
 *  界面那条删除路径已经先反查过、有组合就拦下来弹「无法删除」（见 `combosUsing`），所以正常
 *  使用不会走到「删掉的键还在组合里」。这一句是给**将来别的调用点**兜底的：函数自己保证
 *  不留下死引用，而不是把「调用前必须先反查」这条契约留在注释里靠人记。
 */
fun Config.withoutKey(id: String): Config {
    val target = keyIdOf(id) ?: return this
    // 内置键**不给删**：删掉 `plus` 等于把侧键整条从 keys 里抹掉，读回来退回默认配置，
    //  而界面上只是「删了一颗键」——静默丢配置。删键只对收集来的键有意义。
    if (!target.isCustom) return this
    return copy(
        keys = keys - target,
        customKeys = customKeys.filterNot { it.id == target.id },
        //  连带摘掉含它的组合：它们已经不可能凑成了
        combos = combos.filterNot { target.id in it.keys },
    )
}

/**
 * 给某一颗自定义键改名。
 *
 *  **身份仍是 id**，这里只换显示名 —— 所以绑定不会丢（`KeyId` 按 id 判等）。
 *  但 `keys` 里那一条也得换成新对象：`KeyConfig.id` 是**属性**，留在旧对象上会让
 *  「从配置取名字」的调用点（`pick` 里的 `key.id.label`、日志里的键名）一直显示旧名。
 *
 *  内置键不允许改名（`customKeys` 里找不到就原样返回）。
 */
fun Config.withKeyName(
    id: String,
    name: String,
): Config {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return this
    val index = customKeys.indexOfFirst { it.id == id }
    if (index < 0) return this
    val old = customKeys[index]
    if (old.label == trimmed) return this
    //  **重名不接**：名字是用户认键的唯一线索（绑定页、组合成员、提示全按它念），
    //  两颗同名的键在界面上分不出谁是谁 —— 与「新建」那条查重同一个口径。
    if (customKeys.any { it.id != id && it.label == trimmed }) {
        XLog.w("按键改名：已经有一颗叫「$trimmed」了，忽略（id=$id）")
        return this
    }
    val renamed = old.copyName(trimmed)
    val renamedKeys =
        keys.entries.associate { (k, v) ->
            if (k.id == id) renamed to v.copy(id = renamed) else k to v
        }
    return copy(
        keys = renamedKeys,
        customKeys = customKeys.toMutableList().also { it[index] = renamed },
    )
}

/**
 * 配置里**每一处能放动作**的位置 —— 全项目唯一的遍历点。
 *
 * ## 为什么必须有这一处
 *
 *  「一条命令被改名 / 被删掉」时，配置里所有指向它的 `cmd:<名字>` 都得跟着挪或清。
 *  能放动作的地方分布在两种条目里：
 *
 *  · 每颗键的三档手势（`single` / `double` / `long`）与两格松手（`longRelease`）；
 *  · 每颗键「单击后长按」那一档（主档 `clickThenLong` 与它自己的松手 `clickThenLongRelease`）；
 *  · 每颗键的额外连击（`extraClicks`，第 3 击起，见 [KeyConfig.actionOf]）；
 *  · 每颗键的系统场景（`scenes[]` 的动作表 + 松手两格）；
 *  · 每个组合键的三档（`single` / `long` / `longRelease`，组合没有双击）；
 *  · 每个组合键的系统场景（同上）。
 *
 *  这几处**先后被漏过四次**（删命令只清三格 → 补上场景 → 组合键那两档又漏 → 额外连击与
 *  组合场景又漏），因为每个调用点都自己写一遍 `copy(single = …, double = …, long = …, scenes = …)`。
 *  只要遍历散在调用点上，「下次多一处动作位」就必然再漏一次 —— 所以收成这几个函数：
 *  **加新的动作位只改这个文件**，改名的路径、删命令的路径、删变量的反查都不必再动。
 *  「松手执行额外动作」那两格（`longRelease`）就是这样漏进来一次：加字段时这个文件没跟着改，
 *  改名之后那两格里留着指向旧名的 `cmd:`，删变量的反查也数不到它们。
 *
 * ## 遍历的是 `keys` 全表，不是 `allKeys`
 *
 *  `allKeys` = 内置键 + `customKeys` 清单；而 `keys` 里可能还留着**已从清单删掉**的自定义键
 *  条目（`customKeys` 与 `keys` 是两份数据）。残留条目本身不会被执行侧认领（`keysByCode`
 *  只按 `allKeys` 建表），但它上面的引用照样要清 —— 重新加回这颗键时就不会带出一条悬空的
 *  `cmd:`。只看 `allKeys` 会让这部分引用留在文件里。
 *
 * @param transform 逐个动作换一份；返回同一个实例表示这一处不动
 */
fun Config.mapActions(transform: (Action) -> Action): Config =
    copy(
        keys =
            keys.mapValues { (_, key) ->
                key.copy(
                    single = transform(key.single),
                    double = transform(key.double),
                    long = transform(key.long),
                    longRelease = transform(key.longRelease),
                    //  「单击后长按」那一档的主档与松手各一格：少这两行，改名之后这两处留着
                    //  指向旧名的 `cmd:`，删命令时 `bindsToken` 也数不到它们（库删了、引用留着）。
                    clickThenLong = transform(key.clickThenLong),
                    clickThenLongRelease = transform(key.clickThenLongRelease),
                    extraClicks = key.extraClicks.mapValues { transform(it.value) },
                    scenes = key.scenes.map { it.mapActions(transform) },
                )
            },
        combos =
            combos.map { combo ->
                combo.copy(
                    single = transform(combo.single),
                    long = transform(combo.long),
                    longRelease = transform(combo.longRelease),
                    //  **成员连击**那几格同样是动作位（双击 / 第 3 击起 / 单击后长按）——
                    //  少这几行，改名之后它们留着指向旧名的 `cmd:`，删命令时 `bindsToken`
                    //  也数不到（与单键那几行同一个道理）。
                    memberDouble = transform(combo.memberDouble),
                    memberClicks = combo.memberClicks.mapValues { transform(it.value) },
                    memberClickThenLong = transform(combo.memberClickThenLong),
                    scenes = combo.scenes.map { it.mapActions(transform) },
                )
            },
        //  **「常用命令」也是动作位**：它存的是一条条指向动作的快捷方式（`CommonCommand.token`
        //  是 token 原文，不是 `Action`）。不一起走的话，改名之后清单里那一条仍指着旧名 ——
        //  点下去 NOT_FOUND，而界面上看不出任何悬空（与「五处动作位」漏掉时同一种形状）。
        global =
            global.copy(
                commonCommands =
                    global.commonCommands.map { row ->
                        val action = Action.parse(row.token)
                        val mapped = if (action is Action.None) action else transform(action)
                        //  认不出的写法、以及换成「无操作」的那一档都**不动这一行**：
                        //  `none` 不是清单能存的东西（加不进来），删命令那条路自己会把指向它的
                        //  条目整条摘掉（见 `clearCommandBindings`）。
                        if (mapped is Action.None) row else row.copy(token = mapped.token)
                    },
            ),
    )

/**
 * 配置里**所有**动作的 token（顺序无关，可能有重复）—— 与 [mapActions] 同一份清单。
 *
 *  删除变量前的反查（`VariableRefs`）用它，免得那一边再抄一份「六处」的清单：
 *  抄一份的下场是「删命令认得组合规则、删变量不认得」，而两边都自称是唯一的遍历点。
 */
fun Config.actionTokens(): List<String> =
    buildList {
        keys.values.forEach { key ->
            add(key.single.token)
            add(key.double.token)
            add(key.long.token)
            add(key.longRelease.token)
            add(key.clickThenLong.token)
            add(key.clickThenLongRelease.token)
            key.extraClicks.values.forEach { add(it.token) }
            key.scenes.forEach { it.actionTokens().forEach(::add) }
        }
        combos.forEach { combo ->
            add(combo.single.token)
            add(combo.long.token)
            add(combo.longRelease.token)
            add(combo.memberDouble.token)
            combo.memberClicks.values.forEach { add(it.token) }
            add(combo.memberClickThenLong.token)
            add(combo.memberClickThenLongRelease.token)
            combo.scenes.forEach { it.actionTokens().forEach(::add) }
        }
        //  「常用命令」那几条同样是动作位（见 [mapActions]）：删变量前的反查要能看进去，
        //  否则一条写着 `${变量}` 的常用命令不会被算成引用，删变量时它就成了悬空的那一处。
        global.commonCommands.forEach { add(it.token) }
    }

/**
 * 配置里**还有没有**任何一处绑着 [token]（`cmd:<名字>`）—— 与 [mapActions] 同一份清单。
 *
 *  用途：删命令时先清引用、再删库；库没删成要把引用写回去。判「有没有清过」与「要不要回滚」
 *  都得问这一个问题，两处若各写一遍，就会各自漏掉不同的动作位。
 */
fun Config.bindsToken(token: String): Boolean = actionTokens().any { it == token }

/**
 * 一块场景里的**全部动作位** —— 「换动作」与「列出动作」两条路共用，免得一处改了这里、另一处漏了那里。
 *
 *  档位是个**表**（`SceneRule.actions`）而不是三个固定字段：表里有什么就改什么，
 *  多一档 / 少一档都不用来改这段。**表之外**还有两格独立字段（松手 `release` 与
 *  「单击后长按」的松手 `chainRelease`）—— 它们不在表里，所以必须在这里各列一次：
 *  漏了 `chainRelease` 就等于「改名对那一格不生效、删命令时看不见它还引用着」。
 */
private fun SceneRule.mapActions(transform: (Action) -> Action): SceneRule =
    copy(
        actions = actions.mapValues { transform(it.value) },
        release = transform(release),
        chainRelease = transform(chainRelease),
    )

/** 一块场景里**写过**的那几格动作的 token（没配的档不算）。 */
private fun SceneRule.actionTokens(): List<String> = (actions.values + release + chainRelease).filter { it.isBound }.map { it.token }
