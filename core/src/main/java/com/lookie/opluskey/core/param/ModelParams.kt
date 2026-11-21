package com.lookie.opluskey.core.param

import com.lookie.opluskey.core.model.ConfigFields
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig

// 三个作用域各自的**设置项注册表** —— 加一项设置只改这里的表。
//
//  落盘结构不再有「一格一个字段」：`KeyDto` / `ComboDto` 各带一层 `Map<String, String>`，
//  键就是这里的 `id`。所以加一项设置**不需要**改 DTO、Codec、广播列与迁移 —— 那四处都按
//  [ALL] 遍历，表里多一行就自动跟着走。
//
//  `id` 统一用 snake_case：它只在新格式里出现（老格式由 `Migration` 逐字段读），
//  所以不必迁就老 DTO 那套 camelCase / snake_case 混用的历史写法。

/**
 * 单键的设置项。
 *
 *  与 `GestureTrigger` 的分工：这里只管**值**（延迟、开关、时序）；动作走
 *  `KeyConfig.actions`（档位 → 动作）。所以加一档手势不会往这张表里加东西，
 *  加一项设置也不会碰手势声明表。
 */
object KeyParams {
    val LONG_PRESS_MS = IntParam("long_press_ms", KeyConfig.DEFAULT_LONG_PRESS_MS, 50..5000)
    val LONG_REPEAT = BoolParam("long_repeat", false)
    val LONG_REPEAT_INTERVAL_MS = IntParam("long_repeat_interval_ms", KeyConfig.DEFAULT_REPEAT_INTERVAL_MS, 30..2000)
    val LONG_REPEAT_VIBRATE = BoolParam("long_repeat_vibrate", false)
    val LONG_RELEASE_ON = BoolParam("long_release_on", false)
    val LONG_FIRE_ON_RELEASE = BoolParam("long_fire_on_release", false)

    val DOUBLE_CLICK_MS = IntParam("double_click_ms", KeyConfig.DEFAULT_DOUBLE_CLICK_MS, 100..1000)
    val COMBO_ONLY = BoolParam("combo_only", false)
    val WAIT_MULTI_WINDOW = BoolParam("wait_multi_window", true)
    val MULTI_WAIT_MS = IntParam("multi_wait_ms", KeyConfig.DEFAULT_MULTI_WAIT_MS, 100..500)

    val COLOR_OS_API = BoolParam("color_os_api", GlobalConfig.DEFAULT_COLOR_OS_API)
    val VIBRATE = BoolParam("vibrate", false)
    val VIBRATE_STRENGTH = IntParam("vibrate_strength", GlobalConfig.DEFAULT_VIBRATE_STRENGTH, GlobalConfig.VIBRATE_STRENGTH_RANGE)
    val VIBRATE_ON_NONE = BoolParam("vibrate_on_none", GlobalConfig.DEFAULT_VIBRATE_ON_NONE)
    val TOAST_ON_RUN = BoolParam("toast_on_run", GlobalConfig.DEFAULT_TOAST_ON_RUN)
    val TOAST_FLUID = BoolParam("toast_fluid", GlobalConfig.DEFAULT_TOAST_FLUID)
    val TOAST_NOTIFY = BoolParam("toast_notify", GlobalConfig.DEFAULT_TOAST_NOTIFY)
    val TOAST_TEMPLATE = TextParam("toast_template", GlobalConfig.DEFAULT_TOAST_TEMPLATE)
    val STEP_NOTICE = BoolParam("step_notice", GlobalConfig.DEFAULT_STEP_NOTICE)

    /** 注入侧要的派生位（宿主算好、随广播带过来，见 `ConfigBroadcast`）。 */
    val LONG_IS_SERIAL = BoolParam("long_is_serial", false)
    val SCENE_RELEASE = BoolParam("scene_release", false)
    val SCENE_LONG = BoolParam("scene_long", false)
    val SCENE_CHAIN = BoolParam("scene_chain", false)
    val SCENE_CHAIN_RELEASE = BoolParam("scene_chain_release", false)

    val DOUBLE_CLICK_ON = BoolParam("double_click_on", true)
    val EXTRA_CLICKS_ON = SetParam("extra_clicks_on", emptySet())

    val CLICK_THEN_LONG_ON = BoolParam("click_then_long_on", false)
    val CLICK_THEN_LONG_PRESS_MS = IntParam("click_then_long_press_ms", KeyConfig.DEFAULT_LONG_PRESS_MS, 50..5000)
    val CLICK_THEN_LONG_REPEAT = BoolParam("click_then_long_repeat", false)
    val CLICK_THEN_LONG_REPEAT_INTERVAL_MS =
        IntParam("click_then_long_repeat_interval_ms", KeyConfig.DEFAULT_REPEAT_INTERVAL_MS, 30..2000)
    val CLICK_THEN_LONG_REPEAT_VIBRATE = BoolParam("click_then_long_repeat_vibrate", false)
    val CLICK_THEN_LONG_IS_SERIAL = BoolParam("click_then_long_is_serial", false)
    val CLICK_THEN_LONG_RELEASE_ON = BoolParam("click_then_long_release_on", false)
    val CLICK_THEN_LONG_FIRE_ON_RELEASE = BoolParam("click_then_long_fire_on_release", false)

    val DOUBLE_THEN_LONG_ON = BoolParam("double_then_long_on", false)
    val TRIPLE_THEN_LONG_ON = BoolParam("triple_then_long_on", false)
    val DOUBLE_THEN_LONG_REPEAT = BoolParam("double_then_long_repeat", false)
    val DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS =
        IntParam("double_then_long_repeat_interval_ms", KeyConfig.DEFAULT_REPEAT_INTERVAL_MS, 30..2000)
    val DOUBLE_THEN_LONG_REPEAT_VIBRATE = BoolParam("double_then_long_repeat_vibrate", false)
    val DOUBLE_THEN_LONG_RELEASE_ON = BoolParam("double_then_long_release_on", false)
    val DOUBLE_THEN_LONG_FIRE_ON_RELEASE = BoolParam("double_then_long_fire_on_release", false)
    val DOUBLE_THEN_LONG_IS_SERIAL = BoolParam("double_then_long_is_serial", false)

    val TRIPLE_THEN_LONG_REPEAT = BoolParam("triple_then_long_repeat", false)
    val TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS =
        IntParam("triple_then_long_repeat_interval_ms", KeyConfig.DEFAULT_REPEAT_INTERVAL_MS, 30..2000)
    val TRIPLE_THEN_LONG_REPEAT_VIBRATE = BoolParam("triple_then_long_repeat_vibrate", false)
    val TRIPLE_THEN_LONG_RELEASE_ON = BoolParam("triple_then_long_release_on", false)
    val TRIPLE_THEN_LONG_FIRE_ON_RELEASE = BoolParam("triple_then_long_fire_on_release", false)
    val TRIPLE_THEN_LONG_IS_SERIAL = BoolParam("triple_then_long_is_serial", false)

    val JUDGE_CLICKS = IntParam("judge_clicks", 0, 0..ConfigFields.MAX_JUDGE_CLICKS)

    /**
     * 全部项 —— 序列化按它**全量写**（见 `Params.all` 与 md5 口径）。
     *
     *  顺序无所谓（写出是 map），但**新增项必须加进来**：漏加一项的表现是「这一项永远存不进
     *  文件」，而运行时读的是默认值。`ParamsRegistryTest` 用反射比对「注册表项数 = 值包键数」
     *  锁住这一条。
     */
    val ALL: List<Param<*>> =
        listOf(
            LONG_PRESS_MS,
            LONG_REPEAT,
            LONG_REPEAT_INTERVAL_MS,
            LONG_REPEAT_VIBRATE,
            LONG_RELEASE_ON,
            LONG_FIRE_ON_RELEASE,
            DOUBLE_CLICK_MS,
            COMBO_ONLY,
            WAIT_MULTI_WINDOW,
            MULTI_WAIT_MS,
            COLOR_OS_API,
            VIBRATE,
            VIBRATE_STRENGTH,
            VIBRATE_ON_NONE,
            TOAST_ON_RUN,
            TOAST_FLUID,
            TOAST_NOTIFY,
            TOAST_TEMPLATE,
            STEP_NOTICE,
            LONG_IS_SERIAL,
            SCENE_RELEASE,
            SCENE_LONG,
            SCENE_CHAIN,
            SCENE_CHAIN_RELEASE,
            DOUBLE_CLICK_ON,
            EXTRA_CLICKS_ON,
            CLICK_THEN_LONG_ON,
            CLICK_THEN_LONG_PRESS_MS,
            CLICK_THEN_LONG_REPEAT,
            CLICK_THEN_LONG_REPEAT_INTERVAL_MS,
            CLICK_THEN_LONG_REPEAT_VIBRATE,
            CLICK_THEN_LONG_IS_SERIAL,
            CLICK_THEN_LONG_RELEASE_ON,
            CLICK_THEN_LONG_FIRE_ON_RELEASE,
            DOUBLE_THEN_LONG_ON,
            TRIPLE_THEN_LONG_ON,
            DOUBLE_THEN_LONG_REPEAT,
            DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS,
            DOUBLE_THEN_LONG_REPEAT_VIBRATE,
            DOUBLE_THEN_LONG_RELEASE_ON,
            DOUBLE_THEN_LONG_FIRE_ON_RELEASE,
            DOUBLE_THEN_LONG_IS_SERIAL,
            TRIPLE_THEN_LONG_REPEAT,
            TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS,
            TRIPLE_THEN_LONG_REPEAT_VIBRATE,
            TRIPLE_THEN_LONG_RELEASE_ON,
            TRIPLE_THEN_LONG_FIRE_ON_RELEASE,
            TRIPLE_THEN_LONG_IS_SERIAL,
            JUDGE_CLICKS,
        )
}

/**
 * 组合键的设置项 —— 与 [KeyParams] 逐项同构的那一族（长按、独立设置、成员连击）。
 *
 *  差别只有一处：组合那几格「用公共的还是自己的」是**三态**（`null` = 跟随），
 *  所以 `sceneOnStarter` / `starterKeepLong` / `fixedWindowStart` / `holdFixedWindowStart`
 *  用 [OptBoolParam]。
 */
object ComboParams {
    val MODE = IntParam("mode", 0, 0..9)
    val ORDERED = BoolParam("ordered", false)

    val LONG_PRESS_MS = IntParam("long_press_ms", KeyConfig.DEFAULT_LONG_PRESS_MS, 50..5000)
    val LONG_REPEAT = BoolParam("long_repeat", false)
    val LONG_REPEAT_INTERVAL_MS = IntParam("long_repeat_interval_ms", KeyConfig.DEFAULT_REPEAT_INTERVAL_MS, 30..2000)
    val LONG_REPEAT_VIBRATE = BoolParam("long_repeat_vibrate", false)
    val LONG_FIRE_ON_RELEASE = BoolParam("long_fire_on_release", false)
    val LONG_RELEASE_ON = BoolParam("long_release_on", false)

    val COLOR_OS_API = BoolParam("color_os_api", GlobalConfig.DEFAULT_COLOR_OS_API)
    val VIBRATE = BoolParam("vibrate", false)
    val VIBRATE_STRENGTH = IntParam("vibrate_strength", GlobalConfig.DEFAULT_VIBRATE_STRENGTH, GlobalConfig.VIBRATE_STRENGTH_RANGE)
    val VIBRATE_ON_NONE = BoolParam("vibrate_on_none", GlobalConfig.DEFAULT_VIBRATE_ON_NONE)
    val TOAST_ON_RUN = BoolParam("toast_on_run", GlobalConfig.DEFAULT_TOAST_ON_RUN)
    val TOAST_FLUID = BoolParam("toast_fluid", GlobalConfig.DEFAULT_TOAST_FLUID)
    val TOAST_NOTIFY = BoolParam("toast_notify", GlobalConfig.DEFAULT_TOAST_NOTIFY)
    val TOAST_TEMPLATE = TextParam("toast_template", GlobalConfig.DEFAULT_TOAST_TEMPLATE)
    val STEP_NOTICE = BoolParam("step_notice", GlobalConfig.DEFAULT_STEP_NOTICE)

    /** 窗口四格 + 按住下限：`0` = 跟随公共（与 `ComboKey.WINDOW_FOLLOW_GLOBAL` 同义）。 */
    val WAIT_WINDOW_MS = IntParam("wait_window_ms", 0, 0..5000)
    val WAIT_PAIR_MS = IntParam("wait_pair_ms", 0, 0..5000)
    val HOLD_WINDOW_MS = IntParam("hold_window_ms", 0, 0..5000)
    val HOLD_FOLLOWER_MS = IntParam("hold_follower_ms", 0, 0..5000)
    val HOLD_MIN_MS = IntParam("hold_min_ms", 0, 0..5000)

    val SCENE_ON_STARTER = OptBoolParam("scene_on_starter", null)
    val STARTER_KEEP_LONG = OptBoolParam("starter_keep_long", null)
    val FIXED_WINDOW_START = OptBoolParam("fixed_window_start", null)
    val HOLD_FIXED_WINDOW_START = OptBoolParam("hold_fixed_window_start", null)

    val MEMBER_DOUBLE_ON = BoolParam("member_double_on", true)
    val MEMBER_CLICKS_ON = SetParam("member_clicks_on", emptySet())

    val MEMBER_CLICK_THEN_LONG_ON = BoolParam("member_click_then_long_on", false)
    val MEMBER_CLICK_THEN_LONG_PRESS_MS =
        IntParam("member_click_then_long_press_ms", 0, 0..5000)
    val MEMBER_CLICK_THEN_LONG_FIRE_ON_RELEASE = BoolParam("member_click_then_long_fire_on_release", false)
    val MEMBER_CLICK_THEN_LONG_REPEAT = BoolParam("member_click_then_long_repeat", false)
    val MEMBER_CLICK_THEN_LONG_REPEAT_INTERVAL_MS =
        IntParam("member_click_then_long_repeat_interval_ms", KeyConfig.DEFAULT_REPEAT_INTERVAL_MS, 30..2000)
    val MEMBER_CLICK_THEN_LONG_REPEAT_VIBRATE = BoolParam("member_click_then_long_repeat_vibrate", false)
    val MEMBER_CLICK_THEN_LONG_RELEASE_ON = BoolParam("member_click_then_long_release_on", false)
    val MEMBER_CLICK_THEN_LONG_IS_SERIAL = BoolParam("member_click_then_long_is_serial", false)

    val MEMBER_DOUBLE_THEN_LONG_ON = BoolParam("member_double_then_long_on", false)
    val MEMBER_TRIPLE_THEN_LONG_ON = BoolParam("member_triple_then_long_on", false)
    val MEMBER_DOUBLE_THEN_LONG_REPEAT = BoolParam("member_double_then_long_repeat", false)
    val MEMBER_DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS =
        IntParam("member_double_then_long_repeat_interval_ms", KeyConfig.DEFAULT_REPEAT_INTERVAL_MS, 30..2000)
    val MEMBER_DOUBLE_THEN_LONG_REPEAT_VIBRATE = BoolParam("member_double_then_long_repeat_vibrate", false)
    val MEMBER_DOUBLE_THEN_LONG_RELEASE_ON = BoolParam("member_double_then_long_release_on", false)
    val MEMBER_DOUBLE_THEN_LONG_FIRE_ON_RELEASE = BoolParam("member_double_then_long_fire_on_release", false)
    val MEMBER_DOUBLE_THEN_LONG_IS_SERIAL = BoolParam("member_double_then_long_is_serial", false)
    val MEMBER_TRIPLE_THEN_LONG_REPEAT = BoolParam("member_triple_then_long_repeat", false)
    val MEMBER_TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS =
        IntParam("member_triple_then_long_repeat_interval_ms", KeyConfig.DEFAULT_REPEAT_INTERVAL_MS, 30..2000)
    val MEMBER_TRIPLE_THEN_LONG_REPEAT_VIBRATE = BoolParam("member_triple_then_long_repeat_vibrate", false)
    val MEMBER_TRIPLE_THEN_LONG_RELEASE_ON = BoolParam("member_triple_then_long_release_on", false)
    val MEMBER_TRIPLE_THEN_LONG_FIRE_ON_RELEASE = BoolParam("member_triple_then_long_fire_on_release", false)
    val MEMBER_TRIPLE_THEN_LONG_IS_SERIAL = BoolParam("member_triple_then_long_is_serial", false)

    val ALL: List<Param<*>> =
        listOf(
            MODE,
            ORDERED,
            LONG_PRESS_MS,
            LONG_REPEAT,
            LONG_REPEAT_INTERVAL_MS,
            LONG_REPEAT_VIBRATE,
            LONG_FIRE_ON_RELEASE,
            LONG_RELEASE_ON,
            COLOR_OS_API,
            VIBRATE,
            VIBRATE_STRENGTH,
            VIBRATE_ON_NONE,
            TOAST_ON_RUN,
            TOAST_FLUID,
            TOAST_NOTIFY,
            TOAST_TEMPLATE,
            STEP_NOTICE,
            WAIT_WINDOW_MS,
            WAIT_PAIR_MS,
            HOLD_WINDOW_MS,
            HOLD_FOLLOWER_MS,
            HOLD_MIN_MS,
            SCENE_ON_STARTER,
            STARTER_KEEP_LONG,
            FIXED_WINDOW_START,
            HOLD_FIXED_WINDOW_START,
            MEMBER_DOUBLE_ON,
            MEMBER_CLICKS_ON,
            MEMBER_CLICK_THEN_LONG_ON,
            MEMBER_CLICK_THEN_LONG_PRESS_MS,
            MEMBER_CLICK_THEN_LONG_FIRE_ON_RELEASE,
            MEMBER_CLICK_THEN_LONG_REPEAT,
            MEMBER_CLICK_THEN_LONG_REPEAT_INTERVAL_MS,
            MEMBER_CLICK_THEN_LONG_REPEAT_VIBRATE,
            MEMBER_CLICK_THEN_LONG_RELEASE_ON,
            MEMBER_CLICK_THEN_LONG_IS_SERIAL,
            MEMBER_DOUBLE_THEN_LONG_ON,
            MEMBER_TRIPLE_THEN_LONG_ON,
            MEMBER_DOUBLE_THEN_LONG_REPEAT,
            MEMBER_DOUBLE_THEN_LONG_REPEAT_INTERVAL_MS,
            MEMBER_DOUBLE_THEN_LONG_REPEAT_VIBRATE,
            MEMBER_DOUBLE_THEN_LONG_RELEASE_ON,
            MEMBER_DOUBLE_THEN_LONG_FIRE_ON_RELEASE,
            MEMBER_DOUBLE_THEN_LONG_IS_SERIAL,
            MEMBER_TRIPLE_THEN_LONG_REPEAT,
            MEMBER_TRIPLE_THEN_LONG_REPEAT_INTERVAL_MS,
            MEMBER_TRIPLE_THEN_LONG_REPEAT_VIBRATE,
            MEMBER_TRIPLE_THEN_LONG_RELEASE_ON,
            MEMBER_TRIPLE_THEN_LONG_FIRE_ON_RELEASE,
            MEMBER_TRIPLE_THEN_LONG_IS_SERIAL,
        )
}

/**
 * 公共设置的作用域项。
 *
 *  它同时是**独立设置兜底的那一层**：某颗键 / 某档组合没设这一项时，`Config.effectiveFor`
 *  读的就是这里的值。
 */
object GlobalParams {
    val ENABLED = BoolParam("enabled", GlobalConfig.DEFAULT_ENABLED)
    val UI_VIBRATE = BoolParam("ui_vibrate", true)
    val PREDICTIVE_BACK = BoolParam("predictive_back", true)
    val DETAILED_KEY_SETTINGS = BoolParam("detailed_key_settings", GlobalConfig.DEFAULT_DETAILED_KEY_SETTINGS)

    val DOUBLE_CLICK_MS = IntParam("double_click_ms", KeyConfig.DEFAULT_DOUBLE_CLICK_MS, 100..1000)
    val WAIT_MULTI_WINDOW = BoolParam("wait_multi_window", true)
    val MULTI_WAIT_MS = IntParam("multi_wait_ms", KeyConfig.DEFAULT_MULTI_WAIT_MS, 100..500)
    val CLICK_THEN_LONG_PRESS_MS = IntParam("click_then_long_press_ms", KeyConfig.DEFAULT_LONG_PRESS_MS, 50..5000)
    val COMBO_MEMBER_CTL_PRESS_MS =
        IntParam("combo_member_click_then_long_press_ms", KeyConfig.DEFAULT_LONG_PRESS_MS, 50..5000)

    val VIBRATE = BoolParam("vibrate", false)
    val VIBRATE_STRENGTH = IntParam("vibrate_strength", GlobalConfig.DEFAULT_VIBRATE_STRENGTH, GlobalConfig.VIBRATE_STRENGTH_RANGE)
    val VIBRATE_ON_NONE = BoolParam("vibrate_on_none", GlobalConfig.DEFAULT_VIBRATE_ON_NONE)
    val TOAST_ON_RUN = BoolParam("toast_on_run", GlobalConfig.DEFAULT_TOAST_ON_RUN)
    val TOAST_FLUID = BoolParam("toast_fluid", GlobalConfig.DEFAULT_TOAST_FLUID)
    val TOAST_NOTIFY = BoolParam("toast_notify", GlobalConfig.DEFAULT_TOAST_NOTIFY)
    val STEP_NOTICE = BoolParam("step_notice", GlobalConfig.DEFAULT_STEP_NOTICE)
    val TOAST_TEMPLATE = TextParam("toast_template", GlobalConfig.DEFAULT_TOAST_TEMPLATE)

    val LANGUAGE = TextParam("language", GlobalConfig.DEFAULT_LANGUAGE)
    val PALETTE = TextParam("palette", GlobalConfig.DEFAULT_PALETTE)
    val THEME = TextParam("theme", GlobalConfig.DEFAULT_THEME)
    val MONET_COLORS = BoolParam("monet_colors", GlobalConfig.DEFAULT_MONET_COLORS)
    val PAGE_SCALE = IntParam("page_scale", GlobalConfig.DEFAULT_PAGE_SCALE, GlobalConfig.PAGE_SCALE_MIN..GlobalConfig.PAGE_SCALE_MAX)
    val ICON_TINT_SEED = IntParam("icon_tint_seed", GlobalConfig.DEFAULT_ICON_TINT_SEED, 0..Int.MAX_VALUE)
    val SHOW_COMMON_COMMANDS = BoolParam("show_common_commands", GlobalConfig.DEFAULT_SHOW_COMMON_COMMANDS)
    val REDUCE_ICONS = BoolParam("reduce_icons", GlobalConfig.DEFAULT_REDUCE_ICONS)

    val WAIT_COMBO_SORT = TextParam("wait_combo_sort", GlobalConfig.DEFAULT_COMBO_SORT)
    val HOLD_COMBO_SORT = TextParam("hold_combo_sort", GlobalConfig.DEFAULT_COMBO_SORT)

    val COLOR_OS_API = BoolParam("color_os_api", GlobalConfig.DEFAULT_COLOR_OS_API)
    val MINI_WINDOW_ANIM = BoolParam("mini_window_anim", GlobalConfig.DEFAULT_MINI_WINDOW_ANIM)

    val COMBO_WAIT_WINDOW_MS =
        IntParam(
            "combo_wait_window_ms",
            GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
            GlobalConfig.COMBO_WINDOW_MIN_MS..GlobalConfig.COMBO_WINDOW_MAX_MS,
        )
    val COMBO_WAIT_PAIR_MS =
        IntParam(
            "combo_wait_pair_ms",
            GlobalConfig.DEFAULT_COMBO_PAIR_MS,
            GlobalConfig.COMBO_WINDOW_MIN_MS..GlobalConfig.COMBO_PAIR_MAX_MS,
        )
    val COMBO_HOLD_WINDOW_MS =
        IntParam(
            "combo_hold_window_ms",
            GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
            GlobalConfig.COMBO_WINDOW_MIN_MS..GlobalConfig.COMBO_WINDOW_MAX_MS,
        )
    val COMBO_HOLD_FOLLOWER_MS =
        IntParam(
            "combo_hold_follower_ms",
            GlobalConfig.DEFAULT_COMBO_PAIR_MS,
            GlobalConfig.COMBO_WINDOW_MIN_MS..GlobalConfig.COMBO_PAIR_MAX_MS,
        )
    val COMBO_HOLD_MIN_MS =
        IntParam(
            "combo_hold_min_ms",
            GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
            GlobalConfig.COMBO_HOLD_MIN_MIN_MS..GlobalConfig.COMBO_HOLD_MIN_MAX_MS,
        )
    val COMBO_SCENE_ON_STARTER = BoolParam("combo_scene_on_starter", GlobalConfig.DEFAULT_COMBO_SCENE_ON_STARTER)
    val COMBO_STARTER_KEEP_LONG = BoolParam("combo_starter_keep_long", GlobalConfig.DEFAULT_COMBO_STARTER_KEEP_LONG)
    val COMBO_FIXED_WINDOW_START =
        BoolParam("combo_fixed_window_start", GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START)
    val COMBO_HOLD_FIXED_WINDOW_START =
        BoolParam("combo_hold_fixed_window_start", GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START)

    val ALL: List<Param<*>> =
        listOf(
            ENABLED,
            UI_VIBRATE,
            PREDICTIVE_BACK,
            DETAILED_KEY_SETTINGS,
            DOUBLE_CLICK_MS,
            WAIT_MULTI_WINDOW,
            MULTI_WAIT_MS,
            CLICK_THEN_LONG_PRESS_MS,
            COMBO_MEMBER_CTL_PRESS_MS,
            VIBRATE,
            VIBRATE_STRENGTH,
            VIBRATE_ON_NONE,
            TOAST_ON_RUN,
            TOAST_FLUID,
            TOAST_NOTIFY,
            STEP_NOTICE,
            TOAST_TEMPLATE,
            LANGUAGE,
            PALETTE,
            THEME,
            MONET_COLORS,
            PAGE_SCALE,
            ICON_TINT_SEED,
            SHOW_COMMON_COMMANDS,
            REDUCE_ICONS,
            WAIT_COMBO_SORT,
            HOLD_COMBO_SORT,
            COLOR_OS_API,
            MINI_WINDOW_ANIM,
            COMBO_WAIT_WINDOW_MS,
            COMBO_WAIT_PAIR_MS,
            COMBO_HOLD_WINDOW_MS,
            COMBO_HOLD_FOLLOWER_MS,
            COMBO_HOLD_MIN_MS,
            COMBO_SCENE_ON_STARTER,
            COMBO_STARTER_KEEP_LONG,
            COMBO_FIXED_WINDOW_START,
            COMBO_HOLD_FIXED_WINDOW_START,
        )
}
