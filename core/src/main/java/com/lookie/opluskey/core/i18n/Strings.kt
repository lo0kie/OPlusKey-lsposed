package com.lookie.opluskey.core.i18n

/**
 * 集中管理所有多语言文案 Key 常量。
 * 对应多语言字典存放在 assets/locales/zh-CN.json 等 JSON 文件中。
 *
 *  **`LargeClass` 是有意压住的**：这里一行一个常量、按页面分组、组内按字母序 —— 它是一张
 *  「键名 → 字典条目」的表，没有任何行为，行数只会随页面数量单调长。拆成几个对象（`Strings.Keys`
 *  那种）能把行数分散掉，代价是每个调用点多一层前缀、而键名字符串的守卫要靠人记 —— 这层前缀
 *  不对应任何职责。真正该拆的信号是「这里开始出现逻辑」，那时单开文件，而不是给常量换个门牌。
 */
@Suppress("LargeClass")
object Strings {
    const val ACTION_CUSTOM_COMMAND = "action.custom_command"
    const val ACTION_NONE = "action.none"
    const val ADAPTATION_ADAPTED = "adaptation.adapted"
    const val ADAPTATION_PENDING = "adaptation.pending"
    const val ADAPTATION_UNSUPPORTED = "adaptation.unsupported"
    const val APP_PICKER_ACTIVITY_AUTO = "app_picker.activity_auto"
    const val APP_PICKER_ACTIVITY_AUTO_HINT = "app_picker.activity_auto_hint"

    /** 常驻 FAB 在「还没选过任何应用」时按下去的那句提示。 */
    const val APP_PICKER_PICK_APP_FIRST = "app_picker.pick_app_first"
    const val APP_PICKER_ACTIVITY_EMPTY = "app_picker.activity_empty"
    const val APP_PICKER_ACTIVITY_LOAD_FAILED = "app_picker.activity_load_failed"
    const val APP_PICKER_ACTIVITY_TITLE = "app_picker.activity_title"
    const val APP_PICKER_BRANCH_MULTI_HINT = "app_picker.branch_multi_hint"

    /** 同一句提示的**反面那一档**（「前台应用以外」挑的那一趟：都不在前台才通过）。 */
    const val APP_PICKER_BRANCH_MULTI_HINT_NOT = "app_picker.branch_multi_hint_not"

    /** 给条件挑前台应用那一趟：一条都没勾就按确认时的那句（FAB 常驻，见 `AppConfirmFab`）。 */
    const val APP_PICKER_BRANCH_AT_LEAST_ONE = "app_picker.branch_at_least_one"

    /** 多选选到上限（`BranchCondition.MAX_APP_PACKAGES`）之后再点一行的那句提示。 */
    const val APP_PICKER_BRANCH_PICK_LIMIT = "app_picker.branch_pick_limit"

    /** 多选时顶栏那一行：「已选择 3 / 25 个应用」—— 两个参数分别是已选数与上限。 */
    const val APP_PICKER_BRANCH_PICK_COUNT = "app_picker.branch_pick_count"
    const val APP_PICKER_CONFIRM_SELECTION = "app_picker.confirm_selection"
    const val APP_PICKER_EMPTY_HINT_BROKEN = "app_picker.empty_hint_broken"
    const val APP_PICKER_EMPTY_HINT_ICONLESS = "app_picker.empty_hint_iconless"
    const val APP_PICKER_EMPTY_OPENABLE = "app_picker.empty_openable"
    const val APP_PICKER_LOAD_FAILED = "app_picker.load_failed"
    const val APP_PICKER_LOADING_APPS = "app_picker.loading_apps"

    /** 「选择界面」二级页那趟加载 —— 与一级页同一个加载态，只换主语（「应用组件」）。 */
    const val APP_PICKER_LOADING_ACTIVITIES = "app_picker.loading_activities"
    const val APP_PICKER_NO_SEARCH_RESULT = "app_picker.no_search_result"
    const val APP_PICKER_NO_SEARCH_RESULT_ACTIVITY = "app_picker.no_search_result_activity"

    /** 顶栏那一行小字在「这一趟还没选中任何应用」时写的那一句。 */
    const val APP_PICKER_NONE_SELECTED = "app_picker.none_selected"

    /** 「更多选项」页顶栏那一行小字（这一页装了什么）。 */
    const val MORE_SUBTITLE = "more.subtitle"

    /** 「按键设置」（公共那份）顶栏那一行小字。 */
    const val GENERAL_SUBTITLE = "general.subtitle"

    /** 「自定义变量」页顶栏那一行小字。 */
    const val VARIABLES_SUBTITLE = "variables.subtitle"
    const val APP_PICKER_PULL_REFRESH = "app_picker.pull_refresh"
    const val APP_PICKER_REFRESH_DONE = "app_picker.refresh_done"
    const val APP_PICKER_REFRESHING = "app_picker.refreshing"
    const val APP_PICKER_RELEASE_REFRESH = "app_picker.release_refresh"
    const val APP_PICKER_SECTION_ACTIVITIES = "app_picker.section_activities"
    const val APP_PICKER_SECTION_SHORTCUTS = "app_picker.section_shortcuts"
    const val APP_PICKER_SEARCH_ACTIVITY = "app_picker.search_activity"
    const val APP_PICKER_SEARCH_APP = "app_picker.search_app"

    const val APP_PICKER_TAB_SYSTEM = "app_picker.tab_system"
    const val APP_PICKER_TAB_USER = "app_picker.tab_user"
    const val APP_PICKER_TAB_XPOSED = "app_picker.tab_xposed"
    const val APP_PICKER_TAG_UNOPENABLE = "app_picker.tag_unopenable"

    /** 应用名旁边那枚「分身」标签（多开槽里的那一份）。 */
    const val APP_PICKER_CLONE_TAG = "app_picker.clone_tag"
    const val APP_PICKER_TITLE = "app_picker.title"

    /** 「选择应用」页在**多选**那一趟（给条件挑前台应用）的大标题 —— 那一趟点一行是勾选，不是「打开」。 */
    const val APP_PICKER_MULTI_TITLE = "app_picker.multi_title"
    const val BINDING_CREATE_CUSTOM_COMMAND = "binding.create_custom_command"

    const val BINDING_CREATE_CUSTOM_COMMAND_DESC = "binding.create_custom_command_desc"
    const val BINDING_DELETE_KEY = "binding.delete_key"

    /** 按键菜单里那一行：把这颗自定义键**停用**（键还在，只是不接管）。 */
    const val BINDING_DISABLE_KEY = "binding.disable_key"

    /** 同一行的另一半：重新启用。 */
    const val BINDING_ENABLE_KEY = "binding.enable_key"

    const val BINDING_ADD_KEY = "binding.add_key"

    /** 首页按键清单里，一颗**停用的**键后面那句状态（不是动作，别与 `binding.disable_key` 混）。 */
    const val BINDING_KEY_DISABLED_STATE = "binding.key_disabled_state"

    /** 同一处的另一半：这颗键**开着**（与上面那条一起用，两档都有副标题）。 */
    const val BINDING_KEY_ENABLED_STATE = "binding.key_enabled_state"

    const val BINDING_CUSTOM_KEY_NAME = "binding.custom_key_name"
    const val BINDING_KEY_NAME = "binding.key_name"
    const val BINDING_RENAME_KEY = "binding.rename_key"

    /** 侧键不可改名 / 不可删 —— 那两行在菜单里是禁用的，点一下要说清为什么。 */
    const val BINDING_SIDE_KEY_NO_RENAME = "binding.side_key_no_rename"
    const val BINDING_SIDE_KEY_NO_DELETE = "binding.side_key_no_delete"

    /**
     * 「固定窗口起点」那行开关（开 / 关各一句副标题）—— 见 `KeyConfig.waitMultiWindow`。
     *
     *  **这一颗的界面态与存储位是反的**（开着 = 收集多击 = `waitMultiWindow = false`），
     *  取反只在 `BindingSettings` 那一行做，别在别处再翻一次。
     */
    const val BINDING_WAIT_MULTI_WINDOW = "binding.wait_multi_window"
    const val BINDING_WAIT_MULTI_WINDOW_ON = "binding.wait_multi_window_on"
    const val BINDING_WAIT_MULTI_WINDOW_OFF = "binding.wait_multi_window_off"

    /**
     * 「等待窗口」那一根 —— 两根值（单击延迟 / 多击窗口）共用的一根滑杆。
     *
     *  副标题只说明「这一根是给上面那颗判定时机用的」，不复述开 / 关各自的语义：那两句在
     *  上面那颗开关的副标题里已经写着，同一件事在两个地方各说一遍迟早对不上。
     */
    const val BINDING_WAIT_WINDOW = "binding.wait_window"
    const val BINDING_WAIT_WINDOW_DESC = "binding.wait_window_desc"

    /**
     * 两根窗口滑杆灰着被点一下时说的话 —— 这颗键**一个多击手势都没开**，窗口没有可等的对象。
     *
     *  与「先打开使用独立设置」那类提示同一个格式（一句「下一步该做什么」），不解释判定流程。
     */
    const val BINDING_NO_MULTI_GESTURE = "binding.no_multi_gesture"

    /** 「只参与组合按键」那行开关（开 / 关各一句副标题）—— 见 `KeyConfig.comboOnly`。 */
    const val BINDING_COMBO_ONLY = "binding.combo_only"
    const val BINDING_COMBO_ONLY_ON = "binding.combo_only_on"
    const val BINDING_COMBO_ONLY_OFF = "binding.combo_only_off"

    /** 绑定页整页灰掉时那句原因（「只参与组合按键」）—— 参数是这颗键的名字。 */
    const val BINDING_COMBO_ONLY_BLOCKED = "binding.combo_only_blocked"

    /**
     * 按键列表行尾那半句 —— 「只参与组合」（与「已停用」占同一格，三态互斥）。
     *
     *  比 [BINDING_COMBO_ONLY] 短：那一行是整句标题（`只参与组合按键`），而这一格与「已停用」
     *  同宽，太长会把标题挤掉一截。
     */
    const val BINDING_COMBO_ONLY_BADGE = "binding.combo_only_badge"

    const val BINDING_LONG_PRESS_DELAY_DESC = "binding.long_press_delay_desc"
    const val BINDING_LONG_PRESS_DELAY_TITLE = "binding.long_press_delay_title"

    /**
     * 「松手执行额外动作」那一族 —— 开关（开 / 关各一句）+ 开关为真时滑入的「额外动作」那一行。
     *
     *  动作那一行的副标题配了之后直接写动作名（与命令页的步骤同一套念法），所以只有「空」这一句。
     */
    const val BINDING_LONG_PRESS_RELEASE_ACTION = "binding.long_press_release_action"
    const val BINDING_LONG_PRESS_RELEASE_DESC_OFF = "binding.long_press_release_desc_off"
    const val BINDING_LONG_PRESS_RELEASE_DESC_ON = "binding.long_press_release_desc_on"
    const val BINDING_LONG_PRESS_RELEASE_TITLE = "binding.long_press_release_title"

    /**
     * 「松手才触发」那一行 —— 长按到底「到点就执行」还是「等抬手再执行」。
     *
     *  与上面那一族是**两种互斥的时序**：开了它，长按动作只执行一次且在抬手那一刻；关着则是
     *  到点即执行（那一档才谈得上连续触发）。副标题同样真假各一句。
     *  它也与连续触发互斥，与「松手执行额外动作」互斥 —— 三者都由 `KeyConfig` 那两位属性收口。
     */
    const val BINDING_LONG_PRESS_FIRE_ON_RELEASE_TITLE = "binding.long_press_fire_on_release_title"
    const val BINDING_LONG_PRESS_FIRE_ON_RELEASE_DESC_ON = "binding.long_press_fire_on_release_desc_on"
    const val BINDING_LONG_PRESS_FIRE_ON_RELEASE_DESC_OFF = "binding.long_press_fire_on_release_desc_off"
    const val BINDING_LONG_PRESS_REPEAT_DESC_OFF = "binding.long_press_repeat_desc_off"
    const val BINDING_LONG_PRESS_REPEAT_DESC_ON = "binding.long_press_repeat_desc_on"
    const val BINDING_LONG_PRESS_REPEAT_TITLE = "binding.long_press_repeat_title"

    /**
     * 关掉「松手才触发」之前的那一问（标题 + 正文 + 动作项）—— 这颗键正被某些按住手势
     * 当成「先按住的那一颗」时弹。
     *
     *  正文里的 `%s` 两处：第一处是**这颗键的名字**，第二处是**受影响的组合**（一行一个，
     *  名字各带一对括号）。关掉之后判定侧那条上界会把那些组合整条截止，所以确认时
     *  一并把它们改回「等窗口」—— 留着它们只是一个永远按不出来的档。
     */
    const val BINDING_FIRE_OFF_TITLE = "binding.fire_off_title"
    const val BINDING_FIRE_OFF_BODY = "binding.fire_off_body"
    const val BINDING_FIRE_OFF_ACTION = "binding.fire_off_action"

    /** 「按键绑定」页大标题下面那行小标题：当前这一组是「快捷键」。 */
    const val BINDING_SECTION_KEYS = "binding.section_keys"
    const val BINDING_REPEAT_INTERVAL_DESC = "binding.repeat_interval_desc"
    const val BINDING_REPEAT_INTERVAL_TITLE = "binding.repeat_interval_title"
    const val BINDING_REPEAT_VIBRATE_DESC_OFF = "binding.repeat_vibrate_desc_off"
    const val BINDING_REPEAT_VIBRATE_DESC_ON = "binding.repeat_vibrate_desc_on"
    const val BINDING_REPEAT_VIBRATE_TITLE = "binding.repeat_vibrate_title"
    const val BINDING_SERIAL_UNSUPPORTED_REPEAT = "binding.serial_unsupported_repeat"
    const val BINDING_VOICE_UNSUPPORTED_REPEAT = "binding.voice_unsupported_repeat"

    /** 「松手才触发」开着时，连续触发那一行的副标题（说清为什么点不了）。 */
    const val BINDING_FIRE_ON_RELEASE_UNSUPPORTED_REPEAT = "binding.fire_on_release_unsupported_repeat"

    /** 同一时刻「松手才触发」开着 —— 「松手执行额外动作」那一行为什么点不了。 */
    const val BINDING_FIRE_ON_RELEASE_UNSUPPORTED_RELEASE = "binding.fire_on_release_unsupported_release"

    /**
     * 上面那一条在**场景动作页**的说法。
     *
     *  那一页只摆「松手执行额外动作」这一族，「松手时触发」那一行归「按键绑定」页 ——
     *  所以灰着点一下必须**指路**：只说「已改为松手时触发」而这一页根本没有那个开关，
     *  等于告诉用户一件他在这里改不了的事。
     */
    const val BINDING_FIRE_ON_RELEASE_UNSUPPORTED_RELEASE_KEY = "binding.fire_on_release_unsupported_release_key"

    /** 反过来：配了松手额外动作时，「松手才触发」那一行为什么点不了。 */
    const val BINDING_RELEASE_UNSUPPORTED_FIRE = "binding.release_unsupported_fire"

    /** 这一档本身是「无操作」时，「松手执行额外动作」那一族灰着被点一下说的话。 */
    const val BINDING_RELEASE_NEEDS_ACTION = "binding.release_needs_action"

    /** 「松手沿用按下时的场景」那一行（标题 / 开 / 关）—— 只给场景块的松手那一族摆。 */
    const val BINDING_RELEASE_PIN_TITLE = "binding.release_pin_title"
    const val BINDING_RELEASE_PIN_ON = "binding.release_pin_on"
    const val BINDING_RELEASE_PIN_OFF = "binding.release_pin_off"
    const val BINDING_SETTINGS_SECTION = "binding.settings_section"

    /**
     * 按键绑定页那一段「常用命令」的小标题 —— 现成命令的快捷入口（表见 `CommonCommands`）。
     *
     *  条数拼进标题（与内置动作那几组同一个写法）：这一段是「有几条」由表决定的列，不写就看不出有没有漏。
     *  [BINDING_COMMON_COMMANDS_ADD_DESC] 是它为空时那行「添加」的小字。
     */
    const val BINDING_COMMON_COMMANDS = "binding.common_commands"
    const val BINDING_COMMON_COMMANDS_ADD_DESC = "binding.common_commands_add_desc"

    /**
     * 「常用命令」**自己那条命令的名字**（[COMMON_COMMAND_WECHAT_SCAN] 等）。
     *
     *  与界面文案不同：它会作为**命令名**落进用户的配置（`cmd:<名字>`），所以落盘时按当时的
     *  语言取一次字面量，之后跟着配置走 —— 中途换语言不会把已有的那条命令改掉。
     */
    const val COMMON_COMMAND_WECHAT_SCAN = "common_command.wechat_scan"

    /** 同一行下面那行小字：这一条到底做什么。 */
    const val COMMON_COMMAND_WECHAT_SCAN_DESC = "common_command.wechat_scan_desc"

    const val COMMON_COMMAND_WECHAT_PAY = "common_command.wechat_pay"
    const val COMMON_COMMAND_WECHAT_PAY_DESC = "common_command.wechat_pay_desc"
    const val COMMON_COMMAND_ALIPAY_SCAN = "common_command.alipay_scan"
    const val COMMON_COMMAND_ALIPAY_SCAN_DESC = "common_command.alipay_scan_desc"
    const val COMMON_COMMAND_ALIPAY_PAY = "common_command.alipay_pay"
    const val COMMON_COMMAND_ALIPAY_PAY_DESC = "common_command.alipay_pay_desc"

    // —— 「常用命令」页（`Screen.COMMON_COMMANDS`）：那张清单怎么管 ——
    //
    //  这一页与上面那一组是两件事：上面管**模块自带的那几条叫什么**（跟着语言走），
    //  这里管**用户自己钉的那张清单**（钉 / 改名 / 显不显示 / 移除）。

    /** 清单页顶部那条提示（写盘成功时用）。 */
    const val COMMON_COMMANDS_SAVED = "common_commands.saved"

    /** 这张清单到上限了（`GlobalConfig.MAX_COMMON_COMMANDS`），参数是那个上限。 */
    const val COMMON_COMMANDS_LIMIT = "common_commands.limit"

    /** 行菜单 / 改名窗共用的「重命名」。 */
    const val COMMON_COMMANDS_RENAME = "common_commands.rename"

    /** 改名窗里那个输入框的标题。 */
    const val COMMON_COMMANDS_RENAME_LABEL = "common_commands.rename_label"

    /** 改名窗下面那行常驻小字：留空会怎样。 */
    const val COMMON_COMMANDS_RENAME_HINT = "common_commands.rename_hint"

    /** 行菜单里那一项：这一条要不要出现在按键绑定页。 */
    const val COMMON_COMMANDS_SHOW_IN_BINDING = "common_commands.show_in_binding"

    /**
     * 行菜单里那一项（**固定档**）—— 常用命令一律显示在按键绑定页，这一项只是结论、不能切。
     *
     *  与 [COMMON_COMMANDS_SHOW_IN_BINDING] 分开两条：那一条是问句（要不要显示），
     *  这一条是陈述（固定显示）。同一个标题下挂两种语气，用户会去找「怎么点都没反应」的原因。
     */
    const val COMMON_COMMANDS_SHOW_IN_BINDING_FIXED = "common_commands.show_in_binding_fixed"

    /** 点那一行（不可切换）时说的那句：为什么点不动。 */
    const val COMMON_COMMANDS_SHOW_IN_BINDING_LOCKED = "common_commands.show_in_binding_locked"

    /** 行菜单里那一项：从清单里拿掉。 */
    const val COMMON_COMMANDS_REMOVE = "common_commands.remove"

    /** 移除前那道确认窗的标题。 */
    const val COMMON_COMMANDS_REMOVE_TITLE = "common_commands.remove_title"

    /** 同上那段正文（`%s` = 这一条的名字）—— 说清「只从清单里拿掉，绑定不动」。 */
    const val COMMON_COMMANDS_REMOVE_BODY = "common_commands.remove_body"

    /** 右下角那颗加号打开的那层抽屉的标题。 */
    const val COMMON_COMMANDS_ADD = "common_commands.add"

    /** 清单为空时那唯一一行的标题。 */
    const val COMMON_COMMANDS_EMPTY = "common_commands.empty"

    /** 同上那一行的副标题：下一步该按哪儿。 */
    const val COMMON_COMMANDS_EMPTY_DESC = "common_commands.empty_desc"

    /** 「按键设置」页那一行：进这一页。 */
    const val COMMON_COMMANDS_MANAGE = "common_commands.manage"

    /** 同上那一行的副标题。 */
    const val COMMON_COMMANDS_MANAGE_DESC = "common_commands.manage_desc"

    /** 顶栏副标题：这张清单里钉着几条 —— 模板带一个 `%d`（条数嵌进句子，见 `I18n.format`）。 */
    const val COMMON_COMMANDS_SUBTITLE = "common_commands.subtitle"

    /** 同上那一行**一条都没有**时说的是什么（不报数，与「已保存的命令」那一页同一个口径）。 */
    const val COMMON_COMMANDS_SUBTITLE_EMPTY = "common_commands.subtitle_empty"

    /** 抽屉里选到「入口格」那种标记写法时说的是什么（它不是能执行的动作）。 */
    const val COMMON_COMMANDS_NOT_ACTION = "common_commands.not_action"

    /**
     * 「场景动作」页里，某一档选的是「跟随默认」时那几张设置卡为什么点不了。
     *
     *  文案要说**下一步该做什么**（先换成一个动作）：那一档不归这一块管，没有可调的项。
     */
    const val BINDING_FOLLOW_NO_SETTINGS = "binding.follow_no_settings"
    const val BINDING_TITLE = "binding.title"
    const val CATALOG_FLASH_NOTE = "catalog.flash_note"

    // ── 组合键（首页「按键」卡那一行 + 组合键列表页 + 编辑页） ──

    /**
     * 「组合按键」—— **编辑页**的标题（在编某一档时用它）。
     *
     *  全应用统一叫「组合按键」（用户口径）：首页那行入口、列表页、编辑页、删除确认窗
     *  是同一件事，不该首页一套说法、进去以后换一个简称 —— 用户会以为进了别的功能。
     */
    const val COMBO_TITLE = "combo.title"

    /** 「按键」页的标题 —— 按键列表与组合按键列表收在同一页（见 `Screen.KEYS`）。 */
    const val KEYS_TITLE = "keys.title"

    /** 顶栏那颗「编辑」/「完成」里**完成**那一档（`common.edit` 是另一档）。 */
    const val COMMON_DONE = "common.done"

    /** 编辑态里那颗「删除」的确认窗标题。 */
    const val KEYS_DELETE_TITLE = "keys.delete_title"

    /** 确认窗正文 —— 删掉选中的几颗（带颗数）。 */
    const val KEYS_DELETE_CONFIRM = "keys.delete_confirm"

    /** 删完的回执 —— 「已删除 2 颗按键」。 */
    const val KEYS_DELETED = "keys.deleted"

    /** 编辑态那颗开关按钮：这一下把勾中的项**统一启用**（`%d` = 项数）。 */
    const val KEYS_TOGGLE_ON = "keys.toggle_on"

    /** 同上，这一下是**统一停用**。 */
    const val KEYS_TOGGLE_OFF = "keys.toggle_off"

    /** 启用一颗键时那一问的标题：「同时启用组合按键？」。 */
    const val KEYS_ENABLE_COMBOS_TITLE = "keys.enable_combos_title"

    /** 同一问的正文：`%d` = 含这颗键、现在关着的组合数。 */
    const val KEYS_ENABLE_COMBOS_BODY = "keys.enable_combos_body"

    /** 「不启用」—— 那一问的否定项（不用「取消」：两种回答都是在说接着做什么）。 */
    const val KEYS_NOT_ENABLE = "keys.not_enable"

    /** 停用键时的连带回执：`%1$d` = 停用项数，`%2$d` = 一并停用的组合数。 */
    const val KEYS_TOGGLE_OFF_CASCADE = "keys.toggle_off_cascade"

    /** 停用键时那一问的标题：「同时停用组合按键？」。 */
    const val KEYS_DISABLE_CASCADE_TITLE = "keys.disable_cascade_title"

    /** 同一问的正文：`%d` = 会被连带停用的组合数。 */
    const val KEYS_DISABLE_CASCADE_BODY = "keys.disable_cascade_body"

    /** 启用组合时那一问的标题：它成员的键还关着。 */
    const val KEYS_ENABLE_KEYS_TITLE = "keys.enable_keys_title"

    /** 同一问的正文：`%d` = 需要一起启用的键数。 */
    const val KEYS_ENABLE_KEYS_BODY = "keys.enable_keys_body"

    /** 那一问答「不启用」之后的回执：这组组合保持停用（成员的键没开，开了也跑不起来）。 */
    const val KEYS_COMBO_NEEDS_KEYS = "keys.combo_needs_keys"

    /** 开关按钮的无障碍文案：这一下是**启用**。 */
    const val KEYS_ENABLE = "keys.enable"

    /** 开关按钮的无障碍文案：这一下是**停用**。 */
    const val KEYS_DISABLE = "keys.disable"

    /** 确认窗正文里「连带删掉几个组合键」那一句（带个数）—— 只在真有组合被带走时才出现。 */
    const val KEYS_DELETE_COMBOS = "keys.delete_combos"

    /** 勾里含内置侧键时那句「它会从这一批里剔除」（它删不掉，见 `KeysScreen` 的确认窗）。 */
    const val KEYS_DELETE_BUILTIN = "keys.delete_builtin"

    /** 内置侧键在编辑态下的说明 —— 它删不掉。 */
    const val KEYS_BUILTIN_KEEP = "keys.builtin_keep"

    /** 编辑态里「添加按键 / 添加组合按键」两行被禁用时的说明 —— 编辑态在删、不在加。 */
    const val KEYS_EDIT_NO_ADD = "keys.edit_no_add"

    /**
     * 编辑态顶栏那颗「全选」的无障碍文案 —— 两档：还没全选时是 [KEYS_SELECT_ALL]，
     * 已经全选时是 [KEYS_DESELECT_ALL]（同一颗按钮，按一下反过来）。
     */
    const val KEYS_SELECT_ALL = "keys.select_all"
    const val KEYS_DESELECT_ALL = "keys.deselect_all"

    /**
     * 编辑态里按返回：确认窗的标题 / 正文 / 动作。
     *
     *  [KEYS_LEAVE_EDIT_DESC] 带 `%d`（勾了几项）—— 这一问只在勾了东西时出现，
     *  写清丢的是哪几项比一句「会被清空」有用。
     */
    const val KEYS_LEAVE_EDIT_TITLE = "keys.leave_edit_title"
    const val KEYS_LEAVE_EDIT_DESC = "keys.leave_edit_desc"
    const val KEYS_LEAVE_EDIT_OK = "keys.leave_edit_ok"

    /** 首页「按键操作」卡里那一行的标题 —— 「按键功能」（指向「按键」页）。 */
    const val KEYS_ENTRY = "keys.entry"

    /** 同一行在「一组组合都还没配」时的副标题：这一页里有什么。 */
    const val KEYS_ENTRY_DESC = "keys.entry_desc"

    /** 「按键功能」页第一块的小标题 —— 装内置那颗侧键（与「自定义按键」那一块相对）。 */
    const val KEYS_SECTION_DEFAULT = "keys.section_default"

    /** 组合要两颗键时的那句：只有内置侧键、配不出组合（「添加组合按键」那一行的禁用原因）。 */
    const val COMBO_NEED_KEY = "combo.need_key"

    /** 选键弹窗那颗确认按钮 —— 只写「添加」（窗里已经说了选的是按键，再补三个字太挤）。 */
    const val COMBO_ADD_CONFIRM = "combo.add_confirm"

    /** 组合键的两档：列表副标题与编辑页小标题共用。 */
    const val COMBO_SHORT = "combo.short"
    const val COMBO_LONG = "combo.long"

    /**
     * 「触发方式」那一窗 —— 一档组合**怎么触发**：等窗口 / 先按住第一颗 / 先按住第二颗。
     *
     *  它是这一档的**身份**（`ComboKey.mode`），不是可切换的几套动作：三种方式共用 `single` /
     *  `long` 那一对槽。三颗及以上只有「等窗口」（没有唯一起点），窗里也就只列那一行。
     *
     *  `_wait` / `_hold` 的格式串里是**成员键名**（第一颗、第二颗；先按住第二颗那一行两个参数
     *  对调）：光写「先按 A」，人得先记住成员顺序才知道 A 是谁。
     */
    const val COMBO_MODE_TITLE = "combo.mode_title"
    const val COMBO_MODE_WAIT = "combo.mode_wait"
    const val COMBO_MODE_HOLD = "combo.mode_hold"

    /** 三种方式各自那一句**短名**（只给 ⋮ 菜单那一行用，不带键名）。 */
    const val COMBO_MODE_WAIT_SHORT = "combo.mode_wait_short"
    const val COMBO_MODE_HOLD_FIRST = "combo.mode_hold_first"
    const val COMBO_MODE_HOLD_SECOND = "combo.mode_hold_second"

    /**
     * 「已切换触发方式：」—— 换一档之后那条回执的**前缀**，后面接上面两份模板之一。
     *
     *  拼而不是另写三条整句：语序只在 `COMBO_MODE_WAIT` / `_HOLD` 里定义一次。
     */
    const val COMBO_MODE_SWITCHED = "combo.mode_switched"

    /** 选键弹窗的标题。 */
    const val COMBO_PICK_KEYS = "combo.pick_keys"

    /** 组合列表的**两列**（用户口径：等待组合 / 按住组合，各有自己的筛选与上限）。 */
    const val COMBO_SECTION_WAIT = "combo.section_wait"
    const val COMBO_SECTION_HOLD = "combo.section_hold"

    /** 两列各自的添加行 —— 类别在点下去那一刻就定下来。 */
    const val COMBO_ADD_WAIT = "combo.add_wait"
    const val COMBO_ADD_HOLD = "combo.add_hold"

    /** 列表行的菜单 / 删除确认窗标题。 */
    const val COMBO_DELETE = "combo.delete"
    const val COMBO_DELETE_CONFIRM = "combo.delete_confirm"

    /** 加一组 / 删一组的回执。 */
    const val COMBO_ADDED = "combo.added"
    const val COMBO_REMOVED = "combo.removed"

    /**
     * 停用 / 启用这一档组合（⋮ 菜单那一行），以及点完的回执（格式：成员名，已带各自的括号）。
     *
     *  与单键那两支（`BINDING_DISABLE_KEY` / `TOAST_KEY_DISABLED`）同一套语义：
     *  写的是这一档自己的 `ComboKey.enabled`，与模块总闸是两层。
     */
    const val COMBO_DISABLE = "combo.disable"
    const val COMBO_ENABLE = "combo.enable"
    const val COMBO_DISABLED = "combo.disabled"
    const val COMBO_ENABLED = "combo.enabled"

    /** 组合键满员时「添加组合键」那一行的副标题（格式：上限组数）。 */
    const val COMBO_LIMIT = "combo.limit"

    /** 列表页筛选菜单的小标题（置顶那一行；组里有「全部」与真正的那几颗键）。 */
    const val COMBO_FILTER_SECTION = "combo.filter_section"

    /** 筛选菜单里「不筛」那一行（列出全部组合）。 */
    const val COMBO_FILTER_ALL = "combo.filter_all"

    /** 同一组键再加一遍时那句 —— 判定那边只会认第一档，第二档永远轮不到。 */
    const val COMBO_DUPLICATE = "combo.duplicate"

    /** 选键弹窗里「颗数不对」那两句（格式：颗数）—— 点灰按钮时显示在按钮上面。 */
    const val COMBO_MAX_KEYS = "combo.max_keys"
    const val COMBO_MIN_KEYS = "combo.min_keys"

    /** 列表页那张设置卡的小标题。 */
    const val COMBO_SETTINGS_SECTION = "combo.settings_section"

    /** 「组合绑的是无操作时怎么办」那一行 —— 标题 + 开关真假各一句。 */
    const val COMBO_PASSTHROUGH_TITLE = "combo.passthrough_title"
    const val COMBO_PASSTHROUGH_DESC_ON = "combo.passthrough_desc_on"
    const val COMBO_PASSTHROUGH_DESC_OFF = "combo.passthrough_desc_off"

    /**
     * 「首键要先按住多久」那一行（滑杆标题 + 一句说明）—— **按住手势的下限**。
     *
     *  两颗键几乎同时落下（相差几十毫秒、谁先上报看硬件）不算「按住」，那种「一起按」只归
     *  等窗口那一档；这一行划出「多短算一起按、多长才算按住」。
     *  公共页与某一组的「独立设置」页共用这一套文案（同一个参数的两个落点）。
     */
    const val COMBO_HOLD_MIN_TITLE = "combo.hold_min_title"
    const val COMBO_HOLD_MIN_DESC = "combo.hold_min_desc"

    /**
     * 切到「先按住某颗」之前的那一问 —— 起点键还没改成「松手时触发」时弹（标题 + 正文 + 动作项）。
     *
     *  为什么必须问：按住那两档要「按住一颗、再按另一颗」，起点得按住一会儿；它的长按若在按住
     *  期间就执行了，这一按就出两个动作（判定侧那条上界也会直接截止）。改成「松手时触发」之后
     *  长按押到抬手，组合凑成时那颗键的状态机被摘掉，抬手只出组合那一套。
     *
     *  正文里那一个 `%s` 是**起点那颗键的名字**；动作项是「切换并添加」（同一次落盘改这一位、
     *  并把触发方式切过去）。
     */
    const val COMBO_MODE_ASK_TITLE = "combo.mode_ask_title"
    const val COMBO_MODE_ASK_BODY = "combo.mode_ask_body"
    const val COMBO_MODE_ASK_ACTION = "combo.mode_ask_action"

    /** 组合键：按键之间的等待（两颗键按下最多隔多久才算一起按下）。 */
    const val COMBO_WINDOW_TITLE = "combo.window_title"

    /** 组合键：等待时间的副标题（提示调大会带来额外的等待）。 */
    const val COMBO_WINDOW_DESC = "combo.window_desc"

    /** 灵感语音 —— 菜单里那一行（点开再选开始 / 结束）。 */
    const val CATALOG_VOICE_MEMORY = "catalog.voice_memory"

    /** 唤醒小布 —— 与系统「长按电源键」同一个能力（`oplus:voice_assist`）。 */
    const val CATALOG_VOICE_ASSIST = "catalog.voice_assist"

    /** 「屏幕亮度」那一格（点它弹 [CATALOG_BRIGHTNESS_ADD] / [_SUB] / [_SET] 三档）。 */
    const val CATALOG_BRIGHTNESS = "catalog.brightness"
    const val CATALOG_BRIGHTNESS_ADD = "catalog.brightness_add"
    const val CATALOG_BRIGHTNESS_SUB = "catalog.brightness_sub"
    const val CATALOG_BRIGHTNESS_SET = "catalog.brightness_set"

    /** 「音量」那一格与它菜单的三档 —— 与亮度同一套（点格子弹菜单，每一档再问一个百分比）。 */
    const val CATALOG_VOLUME = "catalog.volume"

    /** 「铃声音量」那一格 —— 与「音量」并列的第二条命令（写铃声流）。 */
    const val CATALOG_RINGER_VOLUME = "catalog.ringer_volume"
    const val CATALOG_VOLUME_ADD = "catalog.volume_add"
    const val CATALOG_VOLUME_SUB = "catalog.volume_sub"
    const val CATALOG_VOLUME_SET = "catalog.volume_set"

    const val CATALOG_MODE_BACK = "catalog.mode_back"
    const val CATALOG_MODE_COLLAPSE = "catalog.mode_collapse"
    const val CATALOG_MODE_CONTROL_CENTER = "catalog.mode_control_center"
    const val CATALOG_MODE_CYCLE = "catalog.mode_cycle"

    /**
     * 系统开关再下一层的三档：切换 / 打开 / 关闭。
     *
     *  这三条**只是动词**，宾语（开关名 / 录屏 / 录音）由选择器那一层拼 —— 见
     *  [COMMON_VERB_NAME_FORMAT]。字典里不写「切换 Wi-Fi」这种整句：同一个动作在注册那边
     *  只念动词（`切换`），两处各写一份就是「下层分叉」。
     */
    const val CATALOG_MODE_TOGGLE = "catalog.mode_toggle"
    const val CATALOG_MODE_TURN_ON = "catalog.mode_turn_on"
    const val CATALOG_MODE_TURN_OFF = "catalog.mode_turn_off"

    const val CATALOG_MODE_EXPAND = "catalog.mode_expand"
    const val CATALOG_MODE_HOME = "catalog.mode_home"
    const val CATALOG_MODE_NOTIFICATIONS = "catalog.mode_notifications"
    const val CATALOG_MODE_PAUSE = "catalog.mode_pause"
    const val CATALOG_MODE_PLAY = "catalog.mode_play"
    const val CATALOG_MODE_POWER = "catalog.mode_power"
    const val CATALOG_MODE_RING = "catalog.mode_ring"
    const val CATALOG_MODE_SILENT = "catalog.mode_silent"
    const val CATALOG_MODE_VIBRATE = "catalog.mode_vibrate"
    const val CATALOG_MODE_VOL_DOWN = "catalog.mode_vol_down"

    /** 「锁屏快捷」那一格与它菜单的两档（相机 / 计算器）。 */
    const val CATALOG_KEYGUARD = "catalog.keyguard"
    const val CATALOG_CAMERA = "catalog.camera"
    const val CATALOG_CALCULATOR = "catalog.calculator"
    const val CATALOG_QUICK_TOOLS = "catalog.quick_tools"
    const val CATALOG_RECORDING = "catalog.recording"

    /**
     * 录音那一层选择器的三档 —— **只有动词**（`开始` / `结束` / `切换`），与录屏同一套。
     *
     *  宾语（「录音」）不写在这里：选择器那一行由 `common.verb_name_format` 拼成「开始录音」，
     *  句子那一侧由 `CommandTitle` 拼成「录音 - 开始」。原先字典里存的是整句「开始录音」，
     *  而句子那边又要拼一次「录音 - 开始」—— 同一个动作在字典里被写了两遍（用户口径
     *  2026-11-08：动词只存一份，宾语由各处按自己的语序拼）。
     */
    const val CATALOG_RECORDING_START = "catalog.recording_start"
    const val CATALOG_RECORDING_STOP = "catalog.recording_stop"
    const val CATALOG_RECORDING_TOGGLE = "catalog.recording_toggle"
    const val CATALOG_SCREEN_RECORD = "catalog.screen_record"

    /**
     * 录屏那一层选择器的三档 —— **只有动词**（`开始` / `结束` / `切换`）。
     *
     *  与 [CATALOG_RECORDING_START] 同一套，理由也同一条：`录屏` 二字由拼装那一层加上去
     *  （选择器里是「切换录屏」、句子里是「录屏 - 切换」），不进字典。三行在选择器里仍然读得清
     *  是录屏 —— 弹窗标题写着「录屏」，每行也带着它。
     */
    const val CATALOG_SCREEN_RECORD_START = "catalog.screen_record_start"
    const val CATALOG_SCREEN_RECORD_STOP = "catalog.screen_record_stop"
    const val CATALOG_SCREEN_RECORD_TOGGLE = "catalog.screen_record_toggle"
    const val CATALOG_SCREEN_TRANSLATE = "catalog.screen_translate"
    const val CATALOG_SCREENSHOT = "catalog.screenshot"
    const val CATALOG_SYSTEM_ACTIONS = "catalog.system_actions"
    const val CATALOG_SYSTEM_KEYS = "catalog.system_keys"
    const val CATEGORY_MEDIA = "category.media"
    const val CATEGORY_PANEL = "category.panel"
    const val CATEGORY_RINGER = "category.ringer"
    const val CATEGORY_SYSTEM_NAV = "category.system_nav"
    const val COMMAND_ADD = "command.add"
    const val COMMAND_CMD_HINT = "command.cmd_hint"
    const val COMMAND_DELAY_DESC = "command.delay_desc"
    const val COMMAND_DELAY_LABEL = "command.delay_label"

    /** 命令预览里那一行间隔 —— 带前缀（`执行间隔：3 秒`）：单看一个「3 秒」不知道是谁的间隔。 */
    const val COMMAND_DELAY_PREFIX = "command.delay_prefix"

    /** 「以 root 执行」那一行（每条命令自己的开关）：标题 + 真假两句副标题。 */
    const val COMMAND_ROOT_TITLE = "command.root_title"
    const val COMMAND_ROOT_DESC_ON = "command.root_desc_on"
    const val COMMAND_ROOT_DESC_OFF = "command.root_desc_off"

    /** 打开「以 root 执行」时那次探测成功：root 已授权（失败那一侧用 `COMMAND_FAILURE_SHELL_NO_ROOT`）。 */
    const val COMMAND_ROOT_GRANTED = "command.root_granted"
    const val COMMAND_FORMAT_INTENT = "command.format_intent"
    const val COMMAND_FORMAT_INTENT_DESC = "command.format_intent_desc"

    /** `notify:正文` —— 以宿主身份发一条通知（见 `Action.Notify`）。 */
    const val COMMAND_FORMAT_NOTIFY = "command.format_notify"
    const val COMMAND_FORMAT_NOTIFY_DESC = "command.format_notify_desc"

    /** `fluid:正文` —— 弹一条流体云胶囊（见 `Action.Fluid`）。 */
    const val COMMAND_FORMAT_FLUID = "command.format_fluid"
    const val COMMAND_FORMAT_FLUID_DESC = "command.format_fluid_desc"

    /** `toast:正文` —— 屏幕上闪一条提示（见 `Action.Toast`）。 */
    const val COMMAND_FORMAT_TOAST = "command.format_toast"
    const val COMMAND_FORMAT_TOAST_DESC = "command.format_toast_desc"
    const val COMMAND_FORMAT_KEYEVENT = "command.format_keyevent"
    const val COMMAND_FORMAT_KEYEVENT_DESC = "command.format_keyevent_desc"

    /** 「命令详情」那一栏的格式错误 —— 没带支持的前缀时出红字（保存按钮同时是灰的）。 */
    const val COMMAND_FORMAT_REQUIRED = "command.format_required"
    const val COMMAND_FORMAT_SHELL = "command.format_shell"
    const val COMMAND_FORMAT_SHELL_DESC = "command.format_shell_desc"
    const val COMMAND_FORMAT_SHORTCUT = "command.format_shortcut"
    const val COMMAND_FORMAT_SHORTCUT_DESC = "command.format_shortcut_desc"
    const val COMMAND_FORMATS_TITLE = "command.formats_title"
    const val COMMAND_LIST_TITLE = "command.list_title"

    /** 「自定义命令」页最右那个页签 —— 与 [COMMAND_LIST_TITLE] 说的不是一件事（那是多条命令的步骤段）。 */
    const val COMMAND_TAB_LIST = "command.tab_list"
    const val COMMAND_NAME_DUPLICATE = "command.name_duplicate"

    /** 「保存」被禁用时：单条命令的正文还不完整（只写了 `shell:` 这种前缀也算）。 */
    const val COMMAND_PAYLOAD_INCOMPLETE = "command.payload_incomplete"

    /** 「保存」被禁用时：多条命令还没有任何步骤。 */
    const val COMMAND_NEED_STEP = "command.need_step"
    const val COMMAND_NAME_HINT = "command.name_hint"
    const val COMMAND_NO_SAVED_COMMANDS = "command.no_saved_commands"

    /** 「命令列表」编辑态：删掉勾中那几条的确认窗（标题 / 正文，正文里报条数）。 */
    const val COMMAND_DELETE_PICKED_TITLE = "command.delete_picked_title"
    const val COMMAND_DELETE_PICKED_BODY = "command.delete_picked_body"

    /** 「命令列表」编辑态：按返回时那一问（正文里报已勾几条）。 */
    const val COMMAND_LEAVE_EDIT_TITLE = "command.leave_edit_title"
    const val COMMAND_LEAVE_EDIT_BODY = "command.leave_edit_body"
    const val COMMAND_PREFIX_FILL = "command.prefix_fill"
    const val COMMAND_SAVED_COMMANDS = "command.saved_commands"
    const val COMMAND_SAVED_COUNT = "command.saved_count"

    /** 「换掉这一步 / 换掉这一条动作」那张抽屉的标题 —— 换掉哪一步、挑哪一条动作，两种来处共用。 */
    const val COMMAND_STEP_REPLACE_TITLE = "command.step_replace_title"

    /** 步骤卡右下角那颗 FAB 的两档说明：进排序态 / 退出排序态。 */
    const val COMMAND_STEP_SORT = "command.step_sort"
    const val COMMAND_STEP_SORT_DONE = "command.step_sort_done"

    /** 排序态下点「添加命令」时说的话 —— 那时它在置灰，得说明是「先退出排序」而不是「满了」。 */
    const val COMMAND_STEP_SORTING_LOCKED = "command.step_sorting_locked"
    const val COMMAND_TEST_BUTTON = "command.test_button"

    /** 表单与步骤都空着时，顶栏那颗「重置」为什么点不动 —— 点它一下说这一句。 */
    const val COMMAND_RESET_EMPTY = "command.reset_empty"

    /** 停在「命令列表」那一页点重置时的说法 —— 那一页没有表单，清不了什么。 */
    const val COMMAND_RESET_LIST_PAGE = "command.reset_list_page"

    /** 步骤排序态下点重置时的说法 —— 那时改的是顺序、不是内容，重置没有宾语。 */
    const val COMMAND_RESET_SORTING = "command.reset_sorting"

    /** 表单第一条小标题：名称输入框（原先这里写的是「修改命令 / 新增命令」）。 */
    const val COMMAND_NAME_TITLE = "command.name_title"

    /** 多条命令那条小标题：命令间隔时长那张卡（这一组是「跑这条命令时的设置」）。 */
    const val COMMAND_SETTINGS_TITLE = "command.settings_title"

    /** 表单第二条小标题：命令正文（单条命令那个输入框）。 */
    const val COMMAND_DETAIL_TITLE = "command.detail_title"

    /**
     * 「可用变量」那一档的小标题（卡里的入口行点开是一层菜单，一行一个系统状态）。
     *  菜单顶部那一处带条数（`可用变量 (8)`，调用点用 `titleWithCount` 拼），卡里那一处是纯标题。
     */
    const val COMMAND_VARS_TITLE = "command.vars_title"

    /** 卡里那行入口的标题：进这一行能挑的东西。 */
    const val COMMAND_VARS_ENTRY_TITLE = "command.vars_entry_title"

    /**
     * 「图标」那一档的小标题与行标题（只在正文是 `fluid:` 时出现）。
     *
     *  点开是网格选择器：模块在用的那批 Tabler 字形全在里面，选中即渲染成 png 落盘。
     */
    const val COMMAND_FLUID_ICON_TITLE = "command.fluid_icon_title"

    /**
     * 「图标」那一行的副标题 —— 一共有几颗可选（`当前有 %s 个图标`）。
     *
     *  这一档原先不写副标题：那时小字讲的是「现在用的是哪一颗」，而把 id 念成 `click` 对谁都没帮助
     *  （挑的是图，一张图没法用一行字说清）。换成**总数**就不一样了 —— 它是「这一格后面还有多少
     *  东西」的量，用户据此知道值不值得点进去。
     */
    const val COMMAND_FLUID_ICON_COUNT = "command.fluid_icon_count"

    /** 入口行的小字：这一档的取值从哪儿来（不是状态值本身）。 */
    const val COMMAND_VARS_ENTRY_DESC = "command.vars_entry_desc"

    /** 菜单里那一行 / 选择窗里：这一档**判不出**（读不到系统服务 / 那个开关还没出现过）。 */
    const val COMMAND_VARS_UNKNOWN = "command.vars_unknown"

    /** 点一行之后那两档：**输出值**（`on` / `silent`，与场景条件同一套词表）—— 参数是此刻的值。 */
    const val COMMAND_VARS_PICK_RAW = "command.vars_pick_raw"

    /** 同上一档：**文案值**（「开」/「静音」，跟着界面语言）—— 参数是此刻的值。 */
    const val COMMAND_VARS_PICK_TEXT = "command.vars_pick_text"

    /** 「命令详情」菜单里那一行：打开全屏编辑弹窗。 */
    const val COMMAND_FULLSCREEN_EDIT = "command.fullscreen_edit"
    const val COMMAND_TYPE_SERIAL = "command.type_serial"
    const val COMMAND_TYPE_BRANCH = "command.type_branch"

    /** 「新增一整块场景」的入口文案（顶栏那颗添加）。 */
    const val COMMAND_BRANCH_ADD = "command.branch_add"

    /** 绑定页横幅：这一档有几个启用中的系统场景。 */
    const val COMMAND_BRANCH_SCENARIO_COUNT = "command.branch_scenario_count"

    /** 一个手势的系统场景到上限了（「添加场景」置灰 + 点一下说的那句）。 */
    const val COMMAND_BRANCH_LIMIT = "command.branch_limit"

    /**
     * 命令库里**每一类**（单条 / 多条）到上限了（`Command.MAX_COMMANDS`）。
     *
     *  参数是那个上限；单条与多条各算各的，所以文案里不点名是哪一类 —— 写入闸分不出
     *  「用户想加哪一种」，那时只知道有一类满了。
     */
    const val COMMAND_LIMIT = "command.limit"

    /** 绑定页横幅的另一档：这一档一条都没配（那一行**始终**在，见 `BindingList`）。 */
    const val COMMAND_BRANCH_SCENARIO_NONE = "command.branch_scenario_none"

    /**
     * 「系统场景」那一行**在菜单里**的子标题 —— 一条都没配时那句。
     *
     *  与上面两条分开：那两条带「当前手势」四个字，是给页面横幅用的（那里有整行宽度）；
     *  菜单行的子标题窄，带上那四个字会折行。
     */
    const val COMMAND_BRANCH_SCENARIO_NONE_MENU = "command.branch_scenario_none_menu"

    /** 同上，配了 N 条时那句（只数启用中的）。 */
    const val COMMAND_BRANCH_SCENARIO_COUNT_MENU = "command.branch_scenario_count_menu"

    /**
     * 「前台应用」那一行在菜单里的子标题 —— `已选择 3 个应用`。
     *
     *  那一档能选好几个应用，把名字全列出来比标题还长（菜单会被撑宽、还读不全），
     *  所以子标题只报个数；具体是哪几个在条件表那一行里（那里不限行）。
     */
    const val COMMAND_BRANCH_APP_COUNT = "command.branch_app_count"
    const val COMMAND_BRANCH_ANY = "command.branch_any"

    /** 一条场景的小标题（「场景 1」）。 */
    const val COMMAND_BRANCH_ITEM = "command.branch_item"

    /**
     * 一级菜单里某一档**已经加满**（这一块里同一种类加到了上限，点不动）时那句原因。
     *
     *  上限是 `choices.size - 1`（见 `blockKindsOf`）：屏幕状态能加两条，两档的那几种只能加一条。
     */
    const val COMMAND_BRANCH_KIND_FULL = "command.branch_kind_full"

    /**
     * 一块**整体**加满（[SceneRule.MAX_CONDITIONS] 条）时那句原因 —— 与上面那一句是两个层级：
     * 那一句说「这一种类别写重了」，这一句说「这一块到顶了」。
     */
    const val COMMAND_BRANCH_CONDITION_LIMIT = "command.branch_condition_limit"

    const val COMMAND_BRANCH_EMPTY = "command.branch_empty"

    /**
     * 停用 / 启用一条场景 —— 行尾那颗「更多」菜单里的那一行，以及停用点下去那道确认窗。
     *
     *  与按键那一对（`BINDING_DISABLE_KEY` / `BINDING_ENABLE_KEY`）同一个语义：写的是这一条自己的
     *  `SceneRule.enabled`。**启用没有确认窗**（与按键、组合键同一条规矩）。
     */
    const val COMMAND_BRANCH_DISABLE = "command.branch_disable"
    const val COMMAND_BRANCH_ENABLE = "command.branch_enable"

    /** 停用那一条行上的标签（「场景 3」行尾那半句）。 */
    const val COMMAND_BRANCH_DISABLED = "command.branch_disabled"

    /**
     * 两块场景的条件一样 —— 一块标在行尾（「与上面重复」），一块是点「确认」时那句拦截。
     *
     *  同一组条件在表里只该有一块（一块本来就管三档手势），所以后者那句是「请并成一块」，
     *  不是「不会生效」：两块各管一半手势时确实都还在跑，但那已经是这个块模型要消掉的写法。
     *
     *  与 `COMMAND_BRANCH_KIND_FULL`（「这一档已经加满了」，说的是**块内**同一种类加到了上限）
     *  不是一件事：那一条换个取值 / 删一条就能过，这一条只能改条件、停用或删掉其中一块。
     */
    const val COMMAND_BRANCH_SAME_SCENE = "command.branch_same_scene"
    const val COMMAND_BRANCH_SAME_SCENE_SAVE = "command.branch_same_scene_save"

    /** 条件页空态的**标题**（上面那行）—— 下面那行说的是「现在会发生什么」。 */
    const val COMMAND_BRANCH_EMPTY_TITLE = "command.branch_empty_title"

    /** 规则行里「这一格还没选」—— 文案是「未选择场景」，行首图标同时置灰。 */
    const val COMMAND_BRANCH_PICK_CONDITION = "command.branch_pick_condition"
    const val COMMAND_BRANCH_NEED_CONDITION = "command.branch_need_condition"

    /**
     * 场景块里某一档的**默认取值**：跟随默认（这一档不被这一块接管，继续往下找 /
     * 回落按键绑定）。它不是「未设置」—— 它是用户明确挑得到的一档，也是新块的默认值。
     */
    const val COMMAND_BRANCH_ACTION_FOLLOW = "command.branch_action_follow"

    /**
     * 「场景动作」页的大标题 —— 点场景块里某一档动作行进来的那一页（三档手势各一屏网格）。
     *
     *  **不与「系统场景」同名**：那一页是这张表，这一页是表里某一块的某一档，
     *  两页都写「系统场景」时分不清自己退一层会回到哪里。
     */
    const val COMMAND_BRANCH_BINDING_TITLE = "command.branch_binding_title"

    /** 点「确认」把这档条件表写回去之后的那句回执（写完留在这一页）。 */
    const val COMMAND_BRANCH_SAVED = "command.branch_saved"

    /**
     * 「场景动作」页那颗「确认」的回执 —— 它只写**这一块**（不整表写回），所以话也不能是
     * 「已保存系统场景」：那张表这一趟一个字节都没动。
     */
    const val COMMAND_BRANCH_ACTION_SAVED = "command.branch_action_saved"
    const val COMMAND_BRANCH_DEFAULT = "command.branch_default"

    /** 条件执行里每一种条件的叫法（分支行、条件菜单、摘要共用）。 */
    const val COMMAND_COND_APP = "command.cond_app"

    /** 与 [COMMAND_COND_APP] 同一族的反面：「前台应用以外」= 前台不是这几个应用。 */
    const val COMMAND_COND_APP_NOT = "command.cond_app_not"
    const val COMMAND_COND_RINGER_NAME = "command.cond_ringer_name"
    const val COMMAND_COND_ORIENTATION_NAME = "command.cond_orientation_name"
    const val COMMAND_COND_SCREEN_ON = "command.cond_screen_on"
    const val COMMAND_COND_SCREEN_OFF = "command.cond_screen_off"
    const val COMMAND_COND_LOCKED = "command.cond_locked"

    /** 条件取值还没配（空串）时的那几个字 —— 认不出的取值不走它，直接显示原文。 */
    const val COMMAND_COND_VALUE_UNSET = "command.cond_value_unset"
    const val COMMAND_COND_SCREEN_NAME = "command.cond_screen_name"
    const val COMMAND_COND_CHARGING = "command.cond_charging"
    const val COMMAND_COND_CHARGING_NAME = "command.cond_charging_name"
    const val COMMAND_COND_CHARGING_OFF = "command.cond_charging_off"
    const val COMMAND_COND_HEADSET = "command.cond_headset"
    const val COMMAND_COND_HEADSET_NAME = "command.cond_headset_name"
    const val COMMAND_COND_HEADSET_OFF = "command.cond_headset_off"
    const val COMMAND_COND_MEDIA_NAME = "command.cond_media_name"
    const val COMMAND_COND_MEDIA_PLAYING = "command.cond_media_playing"
    const val COMMAND_COND_MEDIA_PAUSED = "command.cond_media_paused"
    const val COMMAND_COND_WIFI_NAME = "command.cond_wifi_name"
    const val COMMAND_COND_WIFI_ON = "command.cond_wifi_on"
    const val COMMAND_COND_WIFI_OFF = "command.cond_wifi_off"
    const val COMMAND_COND_RINGER_NORMAL = "command.cond_ringer_normal"
    const val COMMAND_COND_RINGER_VIBRATE = "command.cond_ringer_vibrate"
    const val COMMAND_COND_RINGER_SILENT = "command.cond_ringer_silent"
    const val COMMAND_COND_ORIENTATION_PORTRAIT = "command.cond_orientation_portrait"
    const val COMMAND_COND_ORIENTATION_LANDSCAPE = "command.cond_orientation_landscape"

    const val COMMAND_COND_TIME_NAME = "command.cond_time_name"
    const val COMMAND_COND_TIME_OVERLAP = "command.cond_time_overlap"
    const val COMMAND_COND_BLUETOOTH_NAME = "command.cond_bluetooth_name"
    const val COMMAND_COND_CELLULAR_NAME = "command.cond_cellular_name"
    const val COMMAND_COND_AIRPLANE_NAME = "command.cond_airplane_name"
    const val COMMAND_COND_FLASHLIGHT_NAME = "command.cond_flashlight_name"

    /** 一块条件里再分几条时的那几句（第几条 / 添加条件）。 */
    const val COMMAND_BRANCH_CONDITION_INDEX = "command.branch_condition_index"
    const val COMMAND_BRANCH_ADD_CONDITION = "command.branch_add_condition"

    /** 「添加条件」被拦下时那句话：这一块最后一条还是「未选择条件」，再插一条只是多一行空的。 */
    const val COMMAND_BRANCH_CONDITION_PENDING = "command.branch_condition_pending"
    const val COMMAND_BRANCH_TOP_REACHED = "command.branch_top_reached"
    const val COMMAND_BRANCH_BOTTOM_REACHED = "command.branch_bottom_reached"

    /** 时间区间那个选择弹窗：标题与两列的小标题（开始 / 结束）。 */
    const val COMMAND_COND_TIME_START = "command.cond_time_start"
    const val COMMAND_COND_TIME_END = "command.cond_time_end"

    const val COMMAND_TYPE_SINGLE = "command.type_single"
    const val COMMAND_FAILURE_APP_MISSING = "command_failure.app_missing"
    const val COMMAND_FAILURE_APP_NO_ENTRY = "command_failure.app_no_entry"
    const val COMMAND_FAILURE_APP_NO_MINI_WINDOW = "command_failure.app_no_mini_window"
    const val COMMAND_FAILURE_EMPTY = "command_failure.empty"
    const val COMMAND_FAILURE_NOT_FOUND = "command_failure.not_found"
    const val COMMAND_FAILURE_OVERLOADED = "command_failure.overloaded"
    const val COMMAND_FAILURE_DND_NOT_GRANTED = "command_failure.dnd_not_granted"
    const val COMMAND_FAILURE_SHELL_FAILED = "command_failure.shell_failed"
    const val COMMAND_FAILURE_SHELL_MISSING = "command_failure.shell_missing"
    const val COMMAND_FAILURE_SHELL_NO_PERMISSION = "command_failure.shell_no_permission"
    const val COMMAND_FAILURE_SHELL_NO_ROOT = "command_failure.shell_no_root"
    const val COMMAND_FAILURE_SHELL_ROOT_OFF = "command_failure.shell_root_off"
    const val COMMAND_FAILURE_SHELL_REJECTED = "command_failure.shell_rejected"
    const val COMMAND_FAILURE_SHELL_TIMEOUT = "command_failure.shell_timeout"
    const val COMMAND_FAILURE_SHORTCUT_FAILED = "command_failure.shortcut_failed"
    const val COMMAND_FAILURE_REF_NOT_ALLOWED = "command_failure.ref_not_allowed"
    const val COMMAND_FAILURE_REF_ONLY_SINGLE = "command_failure.ref_only_single"
    const val COMMAND_FAILURE_UNKNOWN = "command_failure.unknown"
    const val COMMON_BACK = "common.back"

    /**
     * 搜索框的占位文案带条数：`%1$s` 原来的占位（`搜索应用`）、`%2$d` 条数 —— `搜索应用 (42)`。
     *
     *  两页共用（选择应用 / 应用组件），只在这一处定句式。只在**搜索框空着**
     *  且条数大于 0 时才用（见 `labeledCount`）：一有输入，那行字读的是「你在搜什么」。
     */
    const val COMMON_SEARCH_COUNT_FORMAT = "common.search_count_format"

    const val COMMON_CANCEL = "common.cancel"
    const val COMMON_CONFIRM = "common.confirm"
    const val COMMON_DELETE = "common.delete"

    /** 停用确认窗上那颗按钮 —— 与 [COMMON_DELETE] 同一档：一颗动词，不带宾语。 */
    const val COMMON_DISABLE = "common.disable"
    const val COMMON_DISCARD = "common.discard"
    const val COMMON_EDIT = "common.edit"
    const val COMMON_MENU = "common.menu"
    const val COMMON_MOVE_DOWN = "common.move_down"
    const val COMMON_MORE = "common.more"
    const val COMMON_MOVE_UP = "common.move_up"

    /**
     * 「**东西 - 状态**」的两段式（`手电筒 - 关闭` / `灵感语音 - 开始`）。
     *
     *  全项目只有 `core/model/CommandTitle` 的 `link` 读它 —— 那一处逐段折叠，把「名字」「状态」
     *  （以及系统开关往下那一层档位名）连成一句。**不要**在别的地方再拼一次横杠：
     *  两种语序并存时，同一个动作在界面上就会有两种念法。
     */
    const val COMMON_NAME_STATE_FORMAT = "common.name_state_format"

    /** 上面那条两段式里的状态词（短的那一档）。 */
    const val COMMON_START = "common.start"
    const val COMMON_STOP = "common.stop"

    /**
     * 一个百分比（`(10%)`）—— 「屏幕亮度」「音量」两族的**状态**。
     *
     *  这两族的父格是「屏幕亮度 / 音量」、子动作是「增加 / 减少 / 设置为」，剩下的那个数就是
     *  状态，所以它与开关的「打开 / 关闭」同一个位置（`音量: 增加 - (10%)`），不编进子动作里。
     *
     *  **带括号、且用半角**：横杠后面直接跟数字会被读成负号（`设置为 - 90%` 看着像「负 90%」），
     *  括号一括就只剩「这个数」一种读法；半角与项目里其余括号注记（`微信(小窗)`）同一套写法。
     */
    const val COMMON_PERCENT_PAREN_FORMAT = "common.percent_paren_format"

    /**
     * 「小窗」那一档的标记 —— 「打开应用」的目标里用它标「这一条是以小窗打开」
     * （`微信(小窗 扫一扫)`）。
     */
    const val COMMON_WINDOW_TAG = "common.window_tag"

    /**
     * 「原版动画」那一档的标记 —— 与 [COMMON_WINDOW_TAG] 同一个位置（`微信(原版动画)`），
     * 两者**互斥**（同一格装不下两种起法）。
     */
    const val COMMON_STOCK_ANIM_TAG = "common.stock_anim_tag"

    /**
     * 「打开应用」的目标外层括号：`%1$s` 应用名、`%2$s` 括号里的附加段（空格分隔）。
     *
     *  括号里是**注记**（小窗 / 组件名 / 快捷方式名），不是主句的动作或状态 ——
     *  所以不与 `common.name_state_format` 那条横杠混用（用户口径「写成 微信(小窗 扫一扫)，
     *  不要再加横杠分隔」）。
     */
    const val COMMON_TARGET_PAREN_FORMAT = "common.target_paren_format"

    /**
     * 「**父名: 子名**」—— 一句动作文案的骨架（`系统开关: WIFI - 打开` / `应用控制: 打开应用 - 微信`）。
     *
     *  全项目只有 `core/model/CommandTitle` 的 `join` 读它：所有「一个动作怎么念」的地方
     *  因此逐字一致（步骤行 / 命令卡摘要 / 执行提示 / 系统场景格 / 长按「额外动作」那一行）。
     *  别处出现这个键就是又一套语序（`tools/audit-arch.py` 有一条判据盯着这件事）。
     */
    const val COMMON_PREFIX_NAME_FORMAT = "common.prefix_name_format"

    /**
     * 「**动词 + 宾语**」—— 选择器里逐行读的那句（`切换 Wi-Fi` / `开始录屏`）。
     *
     *  两段都由调用方给：动词是那一档自己的字典值（`catalog.mode_toggle` = 「切换」、
     *  `catalog.screen_record_start` = 「开始」），宾语是它所属那一格 / 那个开关的名字。
     *  **字典里不写整句**（别再有「切换录屏」这种把宾语焊死进名字的条目）：注册那边同一个
     *  动作只念动词（`切换`），两处各写一份就是同一件事的两种说法。
     *
     *  词与名之间要不要空格由译文自己带（中文没有、英文有），所以拼的是格式串而不是字符串相加。
     *  全项目只有 `ui/ModeMenuPopups` 的 `stateRowLabel` 读它。
     */
    const val COMMON_VERB_NAME_FORMAT = "common.verb_name_format"
    const val COMMON_REPLACE = "common.replace"
    const val COMMON_RESET = "common.reset"
    const val COMMON_TIME_MS = "common.time_ms"
    const val COMMON_TIME_SEC = "common.time_sec"

    const val DONATE_ALIPAY = "donate.alipay"
    const val DONATE_HINT = "donate.hint"
    const val DONATE_LIST_COL_AMOUNT = "donate.list_col_amount"
    const val DONATE_LIST_COL_NAME = "donate.list_col_name"
    const val DONATE_LIST_EMPTY_SUMMARY = "donate.list_empty_summary"
    const val DONATE_LIST_EMPTY_TITLE = "donate.list_empty_title"
    const val DONATE_LIST_TITLE = "donate.list_title"
    const val DONATE_NAME_NOTE = "donate.name_note"
    const val DONATE_QQ = "donate.qq"
    const val DONATE_WECHAT = "donate.wechat"

    const val FEEDBACK_ISSUE = "feedback.issue"
    const val FEEDBACK_QQ_GROUP = "feedback.qq_group"
    const val FEEDBACK_TELEGRAM = "feedback.telegram"

    const val AUTHOR_GITHUB = "author.github"
    const val AUTHOR_COOLAPK = "author.coolapk"
    const val CONFIG_UNKNOWN_FORMAT = "config.unknown_format"

    /** 导入的文件超过上限时的提示 —— 与「格式认不出」分开：换文件能解决，改格式不能。 */
    const val CONFIG_TOO_LARGE = "config.too_large"
    const val DIALOG_APP_UNOPENABLE_TITLE = "dialog.app_unopenable_title"
    const val DIALOG_APP_UNSUPPORTED_MINI_WINDOW_MESSAGE = "dialog.app_unsupported_mini_window_message"
    const val DIALOG_APP_UNSUPPORTED_MINI_WINDOW_TITLE = "dialog.app_unsupported_mini_window_title"
    const val DIALOG_CANCEL = "dialog.cancel"
    const val DIALOG_CLEAR_CONFIG_CONFIRM = "dialog.clear_config_confirm"

    /** 「重置模块」确认窗（首页 `⋮` 菜单那一项）。 */
    const val DIALOG_RESET_MODULE_CONFIRM = "dialog.reset_module_confirm"
    const val DIALOG_RESET_MODULE_MESSAGE = "dialog.reset_module_message"
    const val DIALOG_RESET_MODULE_TITLE = "dialog.reset_module_title"

    /** 「重启服务」确认窗：动作（按钮上的那个词）。 */
    const val DIALOG_RESTART_CONFIRM = "dialog.restart_confirm"

    /** 「重启服务」确认窗：正文只提醒代价（未保存的内容会丢），不解释流程。 */
    const val DIALOG_RESTART_MESSAGE = "dialog.restart_message"

    /** 「重启服务」确认窗：标题（与菜单那一项同名）。 */
    const val DIALOG_RESTART_TITLE = "dialog.restart_title"

    /** 多配置：切换配置（「更多选项」页那一行的标题） */
    const val PROFILE_SWITCH_TITLE = "profile.switch_title"

    /** 多配置：新建配置 */
    const val PROFILE_NEW = "profile.new"

    /** 多配置：删除配置 */
    const val PROFILE_DELETE = "profile.delete"

    /** 多配置：默认配置那一行的「重命名」为什么点不了（提示条上那句）。 */
    const val PROFILE_DEFAULT_RENAME_LOCKED = "profile.default_rename_locked"

    /** 多配置：默认配置那一行的「删除」为什么点不了（提示条上那句）。 */
    const val PROFILE_DEFAULT_DELETE_LOCKED = "profile.default_delete_locked"

    /** 多配置：默认 */
    const val PROFILE_DEFAULT = "profile.default"

    /** 多配置：文件读不出来的那一份在列表里的名字（仍然可点、可删）。 */
    const val PROFILE_BROKEN = "profile.broken"

    /** 多配置：删掉读不出来的那一份时的说明。 */
    const val PROFILE_BROKEN_DELETE = "profile.broken_delete"

    /** 多配置：配置 %d */
    const val PROFILE_NEW_NAME = "profile.new_name"

    /** 新建 / 改名时名字被别的配置占了。 */
    const val PROFILE_NAME_TAKEN = "profile.name_taken"

    /** 多配置：已切到「%s」 */
    const val PROFILE_SWITCHED = "profile.switched"

    /** 多配置：没名字的那份在列表里的称呼。 */
    const val PROFILE_UNNAMED = "profile.unnamed"

    /** 多配置：已新建「%s」 */
    const val PROFILE_CREATED = "profile.created"

    /** 多配置：切换失败：那份读不出来 */
    const val PROFILE_SWITCH_FAILED = "profile.switch_failed"

    /** 多配置：新建失败 */
    const val PROFILE_NEW_FAILED = "profile.new_failed"

    /** 多配置：删除失败 */
    const val PROFILE_DELETE_FAILED = "profile.delete_failed"

    /** 多配置：已导入为「%s」 */
    const val PROFILE_IMPORTED = "profile.imported"

    /** 多配置：已覆盖「%s」—— 导入时选了「盖到某一份」而不是「新增一份」的回执 */
    const val PROFILE_OVERWRITTEN = "profile.overwritten"

    /** 多配置：导入选目标那一窗的标题（也是「覆盖」二次确认窗的标题） */
    const val PROFILE_IMPORT_OVERWRITE_TITLE = "profile.import_overwrite_title"

    /** 多配置：导入选目标里最末那一行「新增配置」 */
    const val PROFILE_IMPORT_NEW = "profile.import_new"

    /** 多配置：导入选目标里还没选就点「确定」那句提示 */
    const val PROFILE_IMPORT_PICK_FIRST = "profile.import_pick_first"

    /** 多配置：覆盖确认窗的正文（`%s` = 要覆盖的那份的名字） */
    const val PROFILE_IMPORT_OVERWRITE_MESSAGE = "profile.import_overwrite_message"

    /** 多配置：覆盖确认窗的动作项文案 */
    const val PROFILE_IMPORT_OVERWRITE_ACTION = "profile.import_overwrite_action"

    /** 多配置：重命名配置 */
    const val PROFILE_RENAME = "profile.rename"

    /** 多配置：配置名称（输入框左边的标签） */
    const val PROFILE_NAME = "profile.name"

    /** 多配置：已重命名为「%s」 */
    const val PROFILE_RENAMED = "profile.renamed"

    /** 多配置：默认那份不许改名 */
    const val PROFILE_RENAME_DEFAULT = "profile.rename_default"

    /** 多配置：默认那份不许删除（当前是它时没有地方可以退回去） */
    const val PROFILE_DELETE_DEFAULT = "profile.delete_default"

    /** 多配置：已删除「%1$s」，切到「%2$s」 */
    const val PROFILE_DELETED_SWITCHED = "profile.deleted_switched"

    /** 多配置：已删除（删的是读不出来的那一份，没有名字可点） */
    const val PROFILE_DELETED = "profile.deleted"

    /** 多配置：最多三份配置（除「默认」之外） */
    const val PROFILE_FULL = "profile.full"

    /** 多配置：删除配置确认窗的标题 */
    const val DIALOG_DELETE_PROFILE_TITLE = "dialog.delete_profile_title"

    /** 多配置：删除配置确认窗的正文（「**%s**」是被删那份的名字） */
    const val DIALOG_DELETE_PROFILE_MESSAGE = "dialog.delete_profile_message"

    const val DIALOG_CLEAR_CONFIG_MESSAGE = "dialog.clear_config_message"
    const val DIALOG_CLEAR_CONFIG_TITLE = "dialog.clear_config_title"

    /** 删掉一个**场景块**（整块的条件与三档动作一起没了，后面的块往上顶）。 */
    const val DIALOG_DELETE_CONDITION_MESSAGE = "dialog.delete_condition_message"
    const val DIALOG_DELETE_CONDITION_TITLE = "dialog.delete_condition_title"

    const val DIALOG_DELETE_ONE_CONDITION_TITLE = "dialog.delete_one_condition_title"
    const val DIALOG_DELETE_COMMAND_SERIAL_MESSAGE = "dialog.delete_command_serial_message"
    const val DIALOG_DELETE_COMMAND_SINGLE_MESSAGE = "dialog.delete_command_single_message"
    const val DIALOG_DELETE_COMMAND_TITLE = "dialog.delete_command_title"
    const val DIALOG_DISCARD_COMMAND_EDIT_MESSAGE = "dialog.discard_command_edit_message"
    const val DIALOG_DISCARD_COMMAND_TITLE = "dialog.discard_command_title"

    /** 「条件执行」页那一份（那一页草稿里是条件表，不是命令）—— 与上面那条各说各的页。 */
    const val DIALOG_DISCARD_CONDITION_TITLE = "dialog.discard_condition_title"

    /**
     * 「选择应用」页那一份：二级页已点过确认、还没按提交键就要退页。
     *
     *  正文里那一个是**将要丢掉的那一条**（`%s`，形如 `微信(扫一扫)`）—— 只说「放弃未保存的选择」
     *  的话，用户看不出丢的是哪一个，而这一页列表很长、挑错了往往要滚回去重新找。
     */
    const val DIALOG_DISCARD_PICK_TITLE = "dialog.discard_pick_title"
    const val DIALOG_DISCARD_PICK_MESSAGE = "dialog.discard_pick_message"

    /**
     * 「使用原版动画」开着，却挑了一条**吃不到**它的目标（分身 / 快捷方式 / 具体界面）。
     *
     *  告知而不是询问：那三类必定用不上，开关留着只会让人以为它生效了 —— 当场关掉、说一句
     *  「已取消」，选中本身照常继续。为什么必定用不上见 `docs/features/launch-animation.md` 10.1。
     */
    const val DIALOG_STOCK_ANIM_DROPPED_TITLE = "dialog.stock_anim_dropped_title"
    const val DIALOG_STOCK_ANIM_DROPPED_MESSAGE = "dialog.stock_anim_dropped_message"

    /** 「支持的命令格式」点前缀时输入框里已有内容 —— 覆盖前问一句（那里是整段替换，不是插入）。 */
    const val DIALOG_FILL_PREFIX_TITLE = "dialog.fill_prefix_title"

    /** 同上，正文里那一个是**将要填进去的前缀**（`%s`）。 */
    const val DIALOG_FILL_PREFIX_MESSAGE = "dialog.fill_prefix_message"
    const val DIALOG_COMMAND_DISCLAIMER_CONTENT = "dialog.command_disclaimer_content"
    const val DIALOG_COMMAND_DISCLAIMER_TITLE = "dialog.command_disclaimer_title"

    /** 通用那颗「确认」—— 目前只有「温馨提醒」用（两颗按钮都只是关窗，见 `ui/screen/HomeScreen`）。 */
    const val DIALOG_CONFIRM = "dialog.confirm"

    /** 按钮区最左那颗「重置」—— 只在「这一格可以恢复默认」的地方出现（提示文案模板）。 */
    const val DIALOG_RESET = "dialog.reset"
    const val DIALOG_DISCLAIMER_ACKNOWLEDGE = "dialog.disclaimer_acknowledge"
    const val DIALOG_DISCLAIMER_CONTENT = "dialog.disclaimer_content"
    const val DIALOG_DISCLAIMER_TITLE = "dialog.disclaimer_title"

    /** 「温馨提醒」：免责声明确认之后接着弹的那一个（模块免费、认准官方正版）。 */
    const val DIALOG_FREE_NOTICE_TITLE = "dialog.free_notice_title"
    const val DIALOG_FREE_NOTICE_CONTENT = "dialog.free_notice_content"

    /** 内置键的名字（「快捷键」，与页面标题同一个词）—— 提示里的主语用它，自定义键名是用户起的、不译。 */
    const val KEY_SIDE = "key.side"

    const val GESTURE_DOUBLE = "gesture.double"
    const val GESTURE_LONG = "gesture.long"
    const val GESTURE_SINGLE = "gesture.single"
    const val HOME_BINDING_CARD_TITLE = "home.binding_card_title"
    const val HOME_DONATE = "home.donate"
    const val HOME_DONATE_SUMMARY = "home.donate_summary"
    const val HOME_MORE_OPTIONS_SUMMARY = "home.more_options_summary"
    const val HOME_OTHER_SECTION = "home.other_section"

    /**
     * 首页「按键操作」那一节的小标题 —— 组合按键与「添加按键」两行。
     *
     *  这两行原先并进首页那张「按键列表」卡里，于是那张卡同时装着「已经有哪些键」与
     *  「还能做什么」两件事，读起来像同一类东西排在一起（用户口径：分开）。
     */
    const val HOME_KEY_OPS_SECTION = "home.key_ops_section"

    /**
     * 首页顶上那块「模块状态」—— 四档状态各一句，外加「未激活」那一句说明。
     *
     *  开 / 关那两句说明**不在这里**：它们就是主开关原来的副标题（[HOME_SWITCH_DESC_ON] /
     *  [HOME_SWITCH_DESC_OFF]）—— 那一块现在就是主开关，同一件事不写两份文案。
     */
    const val HOME_ACTIVATION_ACTIVE = "home.activation_active"
    const val HOME_ACTIVATION_DISABLED = "home.activation_disabled"
    const val HOME_ACTIVATION_INACTIVE = "home.activation_inactive"
    const val HOME_ACTIVATION_INACTIVE_DESC = "home.activation_inactive_desc"
    const val HOME_ACTIVATION_CHECKING = "home.activation_checking"

    /**
     * 关着时那句「点击启用此处模块」—— 这一块整块是开关，但界面上没有任何记号说明这件事，
     * 大字只写「已关闭」，用户不会想到去点它。
     */
    const val HOME_ACTIVATION_TAP_TO_ENABLE = "home.activation_tap_to_enable"

    /** 首页主开关（「启用按键绑定」）的副标题 —— 开 / 关各一句。 */
    const val HOME_SWITCH_DESC_OFF = "home.switch_desc_off"
    const val HOME_SWITCH_DESC_ON = "home.switch_desc_on"

    /** 首页「使用 ColorOS 接口」那行开关的标题与副标题。 */
    const val HOME_COLOROS_TITLE = "home.coloros_title"

    const val HOME_COLOROS_DESC_OFF = "home.coloros_desc_off"
    const val HOME_COLOROS_DESC_ON = "home.coloros_desc_on"
    const val HOME_TITLE = "home.title"

    /** 首页大标题下面那一行 —— 一句话说这个模块是干什么的（不是版本号，用户口径）。 */
    const val HOME_SUBTITLE = "home.subtitle"
    const val HOME_MINI_ANIM_DESC_OFF = "home.mini_anim_desc_off"
    const val HOME_MINI_ANIM_DESC_ON = "home.mini_anim_desc_on"
    const val HOME_MINI_ANIM_TITLE = "home.mini_anim_title"
    const val HOME_TOAST_DESC_OFF = "home.toast_desc_off"
    const val HOME_TOAST_DESC_ON = "home.toast_desc_on"
    const val HOME_TOAST_TITLE = "home.toast_title"

    /** 「执行时提示」的**落点**：Toast 还是流体云（只换落点，不换文案）。 */
    const val HOME_TOAST_FLUID_TITLE = "home.toast_fluid_title"
    const val HOME_TOAST_FLUID_DESC_ON = "home.toast_fluid_desc_on"
    const val HOME_TOAST_FLUID_DESC_OFF = "home.toast_fluid_desc_off"

    /** 「执行时提示」的**文案模板**：那一行的标题 / 输入框占位 / 弹窗里的占位符说明 / 为空时那句默认。 */
    const val HOME_TOAST_TEMPLATE_TITLE = "home.toast_template_title"
    const val HOME_TOAST_TEMPLATE_HINT = "home.toast_template_hint"
    const val HOME_TOAST_TEMPLATE_DEFAULT = "home.toast_template_default"
    const val HOME_TOAST_TEMPLATE_PLACEHOLDER = "home.toast_template_placeholder"
    const val HOME_VIBRATE_DESC_OFF = "home.vibrate_desc_off"
    const val HOME_VIBRATE_DESC_ON = "home.vibrate_desc_on"
    const val HOME_VIBRATE_STRENGTH_DESC = "home.vibrate_strength_desc"

    /** 「振动强度」三档的说法（弱 / 中 / 强）—— 那一行**行尾**写的是它。 */
    const val HOME_VIBRATE_STRENGTH_MEDIUM = "home.vibrate_strength_medium"
    const val HOME_VIBRATE_STRENGTH_STRONG = "home.vibrate_strength_strong"
    const val HOME_VIBRATE_STRENGTH_WEAK = "home.vibrate_strength_weak"
    const val HOME_VIBRATE_STRENGTH_TITLE = "home.vibrate_strength_title"
    const val HOME_VIBRATE_TITLE = "home.vibrate_title"
    const val LINK_CHECK_ALL_NORMAL = "link_check.all_normal"
    const val LINK_CHECK_BROKEN_FORMAT = "link_check.broken_format"
    const val LINK_CHECK_CONFIG_EFFECTIVE = "link_check.config_effective"
    const val LINK_CHECK_CONFIG_FAILED = "link_check.config_failed"
    const val LINK_CHECK_KEY_RESPONSE = "link_check.key_response"
    const val LINK_CHECK_NORMAL = "link_check.normal"
    const val LINK_CHECK_NOT_EFFECTIVE = "link_check.not_effective"
    const val LINK_CHECK_NOT_RESPONDING = "link_check.not_responding"
    const val LINK_CHECK_NOT_RUNNING = "link_check.not_running"
    const val LINK_CHECK_NOT_SYNCED = "link_check.not_synced"
    const val LINK_CHECK_NOT_USED_YET = "link_check.not_used_yet"
    const val LINK_CHECK_OVERALL_STATUS = "link_check.overall_status"
    const val LINK_CHECK_RESTART_REQUIRED = "link_check.restart_required"
    const val LINK_CHECK_SERVICE_STATUS = "link_check.service_status"
    const val LINK_CHECK_SWITCH_OFF = "link_check.switch_off"
    const val LINK_CHECK_USAGE_VERIFIED = "link_check.usage_verified"

    /** 「按键接管」这一段 —— 回答「现在到底有没有键会被模块接住」。 */
    const val LINK_CHECK_TAKE_OVER = "link_check.take_over"

    /** 总闸关着时那一段的副标题：**所有**按键都不接管（不是某一颗）。 */
    const val LINK_CHECK_MASTER_OFF_ALL = "link_check.master_off_all"

    /** 总闸开着、但每一颗键都停用了。 */
    const val LINK_CHECK_ALL_KEYS_OFF = "link_check.all_keys_off"
    const val LINK_CHECK_VERIFIED = "link_check.verified"
    const val LINK_CHECK_WAITING_RESPONSE = "link_check.waiting_response"
    const val MEDIA_NEXT = "media.next"
    const val MEDIA_PLAY_PAUSE = "media.play_pause"
    const val MEDIA_PREV = "media.prev"
    const val MEDIA_VOL_UP = "media.vol_up"
    const val MENU_DEFAULT_PAGE = "menu.default_page"
    const val MENU_EXPORT_LOG = "menu.export_log"
    const val MENU_OPEN_IN_MINI_WINDOW = "menu.open_in_mini_window"
    const val MENU_RESET_CONFIG = "menu.reset_config"

    /** 「重置配置」那一行置灰时的说明 —— 链路没通、或这次配置读不出来（见 `AboutMenus.profileManageRows`）。 */
    const val MENU_RESET_CONFIG_OFF = "menu.reset_config_off"

    /** 首页 `⋮` 菜单那一项 —— 重置**整个模块**（当前配置 + 命令库 + 变量），与「重置配置」是两个入口。 */
    const val MENU_RESET_MODULE = "menu.reset_module"
    const val MENU_RESTART_SERVICE = "menu.restart_service"
    const val MENU_SHOW_INVALID_APPS = "menu.show_invalid_apps"
    const val MENU_SHOW_SYSTEM_APPS = "menu.show_system_apps"

    /** 「选择应用」页 `⋮` 菜单那一项 —— 打开应用时套原版那层启动包裹（命令级，与小窗互斥）。 */
    const val MENU_APP_STOCK_ANIM = "menu.app_stock_anim"
    const val MORE_ABOUT_AUTHOR = "more.about_author"
    const val MORE_ABOUT_FEEDBACK = "more.about_feedback"

    /** 「公共设置」页（标题与首页「按键操作」卡那一行共用）与它里面那节「接口选择」。 */
    const val GENERAL_TITLE = "general.title"
    const val GENERAL_SECTION_INTERFACE = "general.section_interface"

    /**
     * 「按键设置」页 —— **这一档**（一颗键 / 一组组合）对公共项的覆盖。
     *
     *  与 [GENERAL_TITLE] 分开：那一页是**公共**那份（所有档共用的默认），这一页是覆盖。
     */
    const val KEY_SETTINGS_TITLE = "key_settings.title"

    /**
     * 「独立设置」页两张卡的小标题 —— 上面那张只有一颗总开关，下面那张是这一档自己的那几项。
     *
     *  原先只有一条（页面顶部一个「独立设置」），与顶栏标题同名、也说不清下面两张卡各是什么；
     *  现在与「按键设置」页同构：一个小标题带一张卡。
     */
    const val KEY_SETTINGS_SECTION = "key_settings.section"

    /** 「独立设置」页最上面那组的标题 —— 三击 / 四击 / 单击后长按：这一档有哪几种多击形态。 */
    const val KEY_SETTINGS_MULTI_CLICK = "key_settings.multi_click"

    /**
     * 「独立设置」页第二组的标题 —— 单击那三行（判定时机 / 单击延迟 / 忽视无操作双击手势）。
     *
     *  它们原先在「按键绑定」页的单击档下面，与「多击手势」分属两页；挪过来之后与那一组并列：
     *  一组说「有几档」，一组说「单击这一档怎么判」。
     */
    const val KEY_SETTINGS_CLICK = "key_settings.click"

    /** 下面那几行灰着被点一下时说的话 —— 「先打开上面那颗总开关」。 */
    const val KEY_SETTINGS_NEED_OWN = "key_settings.need_own"
    const val SETTING_USE_OWN = "key_settings.use_own"
    const val SETTING_USE_OWN_ON = "key_settings.use_own_on"
    const val SETTING_USE_OWN_OFF = "key_settings.use_own_off"

    /**
     * 「多击手势」那两行 —— 三击 / 四击各自一颗开关。
     *
     *  它们**不在**上面那五项里：那五项是「覆盖公共设置」，这两颗是「这一档存不存在」。
     *  所以也不吃上面那颗总开关（见 `KeyConfig.extraClicksOn`）。
     *
     *  为什么分成两颗而不是收成一个：两者的代价不一样 —— 开三击让单击多等一个窗，
     *  开四击还要让三击也等。只要三击的人不该被迫接受四击那一份延迟。
     */
    const val SETTING_CLICK2 = "key_settings.click2"
    const val SETTING_CLICK2_ON = "key_settings.click2_on"
    const val SETTING_CLICK2_OFF = "key_settings.click2_off"
    const val SETTING_CLICK3 = "key_settings.click3"
    const val SETTING_CLICK3_ON = "key_settings.click3_on"
    const val SETTING_CLICK3_OFF = "key_settings.click3_off"
    const val SETTING_CLICK4 = "key_settings.click4"
    const val SETTING_CLICK4_ON = "key_settings.click4_on"
    const val SETTING_CLICK4_OFF = "key_settings.click4_off"
    const val SETTING_CLICK_THEN_LONG = "key_settings.click_then_long"
    const val SETTING_CLICK_THEN_LONG_ON = "key_settings.click_then_long_on"
    const val SETTING_CLICK_THEN_LONG_OFF = "key_settings.click_then_long_off"

    /** 首页「按键设置」那一行的摘要。 */
    const val HOME_GENERAL_DESC = "home.general_desc"

    const val MORE_ABOUT_SIGNATURE = "more.about_signature"
    const val MORE_ABOUT_VERSION = "more.about_version"
    const val MORE_EXPORT_CONFIG = "more.export_config"
    const val MORE_EXPORT_CONFIG_SUMMARY = "more.export_config_summary"
    const val MORE_IMPORT_CONFIG = "more.import_config"
    const val MORE_IMPORT_CONFIG_SUMMARY = "more.import_config_summary"
    const val MORE_LANGUAGE = "more.language"
    const val MORE_LANGUAGE_AUTO = "more.language_auto"
    const val MORE_LANGUAGE_EN = "more.language_en"
    const val MORE_LANGUAGE_ZH = "more.language_zh"

    /** 跳浏览器的确认窗（外链一律先问一句，见 `ExternalLinkDialog`）。 */
    const val MORE_LINK_OPEN_TITLE = "more.link_open_title"
    const val MORE_LINK_OPEN_ACTION = "more.link_open_action"
    const val MORE_OPTIONS_TITLE = "more.title"
    const val MORE_SECTION_ABOUT = "more.section_about"
    const val MORE_SECTION_APPEARANCE = "more.section_appearance"
    const val MORE_SECTION_CONFIG = "more.section_config"
    const val MORE_SECTION_STATUS = "more.section_status"

    /** 「更多选项」页「外观」那一卡：系统配色（页面强调色）与五套色板各自的名字。 */
    const val MORE_PALETTE = "more.palette"
    const val MORE_PALETTE_BLUE = "more.palette_blue"
    const val MORE_PALETTE_GREEN = "more.palette_green"
    const val MORE_PALETTE_ORANGE = "more.palette_orange"
    const val MORE_PALETTE_PURPLE = "more.palette_purple"
    const val MORE_PALETTE_PINK = "more.palette_pink"
    const val MORE_PALETTE_DEFAULT = "more.palette_default"

    /** 「更多选项」页「外观」那一卡：界面颜色（跟随系统 / 浅色 / 深色）。 */
    const val MORE_THEME = "more.theme"

    /** 「跟随系统显示大小」那一行（开关）。 */
    const val MORE_FOLLOW_DISPLAY_SIZE = "more.follow_display_size"
    const val MORE_FOLLOW_DISPLAY_SIZE_ON = "more.follow_display_size_on"
    const val MORE_FOLLOW_DISPLAY_SIZE_OFF = "more.follow_display_size_off"

    /** 「随机图标配色」那一行（外观卡）：标题 + 开 / 关两句副标题。 */
    const val MORE_RANDOM_ICON_TINT = "more.random_icon_tint"
    const val MORE_RANDOM_ICON_TINT_ON = "more.random_icon_tint_on"
    const val MORE_RANDOM_ICON_TINT_OFF = "more.random_icon_tint_off"

    /** 「页面缩放」那一行（只在「跟随系统显示大小」关掉时出现）。 */
    const val MORE_PAGE_SCALE = "more.page_scale"
    const val MORE_PAGE_SCALE_DESC = "more.page_scale_desc"
    const val MORE_THEME_AUTO = "more.theme_auto"

    /** 「跟随系统 (Monet)」—— 跟随系统，且整页用系统那套壁纸取色。 */
    const val MORE_THEME_MONET = "more.theme_monet"

    /**
     * 菜单里 Monet 那一行的**子标题**（`Monet`）—— 标题只写「跟随系统」。
     *
     *  那一行与上一档（纯跟随系统）标题本来就该一样，差别只在「用不用壁纸取色」；
     *  把说明塞进标题会把一行字拉长，也让两行在标题上看不出是同一件事的两档。
     */
    const val MORE_THEME_MONET_TAG = "more.theme_monet_tag"
    const val MORE_THEME_LIGHT = "more.theme_light"
    const val MORE_THEME_DARK = "more.theme_dark"
    const val MORE_SIGNATURE_DEBUG = "more.signature_debug"
    const val MORE_SIGNATURE_MISMATCH = "more.signature_mismatch"
    const val MORE_SIGNATURE_UNKNOWN = "more.signature_unknown"
    const val NAV_RECENTS = "nav.recents"
    const val PANEL_POWER_MENU = "panel.power_menu"
    const val SETTING_ENABLE_BINDING = "setting.enable_binding"
    const val SETTING_KEY_BINDING = "setting.key_binding"

    /**
     * 「按键设置」页那一行：按键绑定页要不要露出「常用命令」那一段。
     *
     *  纯显示项 —— 关掉只是那几行不画，已经绑上去的动作照旧执行。副标题真假各一句，
     *  两句都要说清「只是不显示」（写成「停用」会让人以为那些命令不跑了）。
     */
    const val SETTING_SHOW_COMMON_COMMANDS = "setting.show_common_commands"
    const val SETTING_SHOW_COMMON_COMMANDS_ON = "setting.show_common_commands_on"
    const val SETTING_SHOW_COMMON_COMMANDS_OFF = "setting.show_common_commands_off"

    /**
     * 滑杆行「手动输入」小窗的输入框占位（不带值域 —— 那一句在框下面常驻，见 [SLIDER_INPUT_RANGE]）。
     *
     *  手滑调不准是滑杆的天生短板（200–2000 一档 50ms，36 档挤在一根轨道上），所以每一根滑杆
     *  点一下标题都能打字（用户口径）。小窗本体复用 `NameDialog`，它多收一个 `range`。
     */
    const val SLIDER_INPUT_LABEL = "slider.input_label"

    /**
     * 同一个弹窗里输入框下面那行常驻说明 —— 值域与步进（三个 `%d`：最小 / 最大 / 步长）。
     *
     *  为什么放框下面而不是写进占位：占位在填着字时就看不见了，而「能填多大」与「打进去的数
     *  会被吸到哪一档」正是打字时要看的两件事（用户口径「range 提示优化」）。
     */
    const val SLIDER_INPUT_RANGE = "slider.input_range"
    const val STATUS_API_MISMATCH = "status.api_mismatch"
    const val STATUS_CHECKING = "status.checking"
    const val STATUS_CONFIG_FAILED = "status.config_failed"
    const val STATUS_CONFIG_NOT_SYNCED = "status.config_not_synced"
    const val STATUS_CONFIG_RECOVERED = "status.config_recovered"
    const val STATUS_DISABLED = "status.disabled"
    const val STATUS_HOST_STALE = "status.host_stale"
    const val STATUS_KEY_DISABLED = "status.key_disabled"
    const val STATUS_PENDING_OR_SCOPE = "status.pending_or_scope"
    const val STATUS_SERVICE_NOT_RUNNING = "status.service_not_running"
    const val STATUS_SYSTEM_STALE = "status.system_stale"
    const val STATUS_UNAVAILABLE = "status.unavailable"

    /** 控件置灰、但**不是**整页被闸住时的说法（原先落到「页面还没就绪」，把人引去重进页面）。 */
    const val CONTROL_UNAVAILABLE = "status.control_unavailable"

    /** 当前编辑的那颗键被停用了（`%s` = 键名；侧键与自定义键共用）—— 说清去哪儿重新启用。 */
    const val KEY_DISABLED_ITEM = "status.key_disabled_item"

    /**
     * 当前编辑的那一档组合被停用了（`%s` = 成员名，**已带各自的括号**，见 `comboLabel`）。
     *
     *  为什么不复用 [KEY_DISABLED_ITEM]：那一句给键名外面再套一对「」，而组合名里每个成员
     *  已经各有自己的括号 —— 套两层会读成「一个叫『「侧键」+「电量 +」』的东西」。
     */
    const val COMBO_DISABLED_ITEM = "status.combo_disabled_item"
    const val TOAST_COMMAND_DELETED = "toast.command_deleted"
    const val TOAST_COMMAND_MODIFIED = "toast.command_modified"
    const val TOAST_COMMAND_OVERWRITTEN = "toast.command_overwritten"
    const val TOAST_COMMAND_SAVED = "toast.command_saved"
    const val TOAST_COMMAND_STEP_MISSING = "toast.command_step_missing"
    const val TOAST_COMMAND_STEP_FULL = "toast.command_step_full"

    /** 点了一下禁用的「上移」/「下移」—— 这一步已经在最上面 / 最下面。 */
    const val TOAST_COMMAND_STEP_FIRST = "toast.command_step_first"
    const val TOAST_COMMAND_STEP_LAST = "toast.command_step_last"
    const val TOAST_COMMAND_TRIGGERED = "toast.command_triggered"
    const val TOAST_COMMAND_TEST_FAILED = "toast.command_test_failed"
    const val TOAST_CONFIG_CLEAR_FAILED = "toast.config_clear_failed"

    const val TOAST_CONFIG_CLEARED = "toast.config_cleared"
    const val TOAST_MODULE_RESET = "toast.module_reset"
    const val TOAST_CONFIG_EXPORT_FAILED = "toast.config_export_failed"
    const val TOAST_CONFIG_EXPORTED = "toast.config_exported"
    const val TOAST_CONFIG_IMPORT_FAILED = "toast.config_import_failed"
    const val TOAST_CONFIG_READ_FAILED = "toast.config_read_failed"
    const val TOAST_CONFIG_SAVE_BROADCAST_FAILED = "toast.config_save_broadcast_failed"

    /** 建页那次同步配置没发出去（页面会因此整页灰着，要告诉用户怎么办）。 */
    const val TOAST_CONFIG_SYNC_FAILED = "toast.config_sync_failed"
    const val TOAST_CONFIG_SAVE_FAILED = "toast.config_save_failed"
    const val TOAST_COMMANDS_READ_FAILED = "toast.commands_read_failed"
    const val TOAST_LOG_EMPTY = "toast.log_empty"
    const val TOAST_LOG_EXPORTED = "toast.log_exported"

    /** 删除一颗自定义按键之后（「已删除「音量键」」）。 */
    const val TOAST_KEY_DELETED = "toast.key_deleted"

    /** 停用 / 启用一颗自定义键的回执（`%s` = 键名）。 */
    const val TOAST_KEY_DISABLED = "toast.key_disabled"

    const val TOAST_KEY_ENABLED = "toast.key_enabled"

    /** 把某个动作绑到手势 / 按键上之后（「已绑定「录音」」）。 */
    const val TOAST_BOUND = "toast.bound"

    /** 拨动一个开关之后（「已开启「振动反馈」」/「已关闭「振动反馈」」）。 */
    const val TOAST_SWITCH_ON = "toast.switch_on"
    const val TOAST_SWITCH_OFF = "toast.switch_off"

    /** 重命名一颗自定义按键之后（「已重命名为「音量键」」）。 */
    const val TOAST_KEY_RENAMED = "toast.key_renamed"

    /**
     * 「在一组选项里换了一档」的回执（「强调色 已切换到「蓝色」」）。
     *
     *  语言 / 界面颜色 / 系统配色三处共用：它们说的是同一件事（这一组里换了一档），
     *  句式也一样 —— 各留一个键的话，改一处界面措辞就得记着改三处。
     *
     *  **带主语**（两个占位符：哪一项、哪一档）：只有「已切换到「蓝色」」时不知道切的是什么，
     *  深色 / 强调色 / 语言三行都是这个句式，回执里分不出来（用户口径「换成更精确的」）。
     *
     *  主语是**一组选项里的某一档**（语言 / 深浅 / 配色）。首页「按键清单」点一颗键**不算**：
     *  那是进那颗键的页面，不是切换，所以那一处不发回执。
     */
    const val TOAST_SWITCHED_TO = "toast.switched_to"

    const val TOAST_LOG_EXPORT_FAILED = "toast.log_export_failed"
    const val TOAST_SERVICE_RESTART_FAILED = "toast.service_restart_failed"
    const val TOAST_SERVICE_RESTARTED = "toast.service_restarted"
    const val TOOL_MINI_WINDOW = "tool.mini_window"
    const val TOOL_OPEN_APP = "tool.open_app"
    const val TOOL_SYSTEM_SWITCHES = "tool.system_switches"
    const val TOOL_SCREEN_RECOGNITION = "tool.screen_recognition"

    /** 「小布 AI」入口格 —— 识屏 / 语音 / 闪记 / 灵感语音四件收在这一格后面。 */
    const val TOOL_XIAOBU_AI = "tool.xiaobu_ai"

    /** 「自定义按键」页（监听按键后添加）—— 标题 / 说明 / 等待 / 添加 / 空态。 */
    const val KEY_WATCH_TITLE = "key_watch.title"
    const val KEY_WATCH_HINT = "key_watch.hint"
    const val KEY_WATCH_WAITING = "key_watch.waiting"
    const val KEY_WATCH_COUNTDOWN = "key_watch.countdown"
    const val KEY_WATCH_RESERVED = "key_watch.reserved"

    /** 收集按键时广播里没带按键码（`keyCode <= 0`）。 */
    const val KEY_WATCH_NO_CODE = "key_watch.no_code"
    const val KEY_WATCH_LIMIT = "key_watch.limit"
    const val KEY_WATCH_ADDED = "key_watch.added"
    const val KEY_WATCH_DUPLICATE = "key_watch.duplicate"

    /**
     * 「系统开关」那一格点开的菜单里那几条的名字 ——
     * 与 `core/model/SystemSwitches.ITEMS` 一一对应（加一条时两边一起改）。
     */
    const val SYS_SETTING_BT = "system_setting.bt"
    const val SYS_SETTING_WIFI = "system_setting.wifi"
    const val SYS_SETTING_CELL = "system_setting.cell"
    const val SYS_SETTING_ROTATION = "system_setting.rotation"
    const val SYS_SETTING_NFC = "system_setting.nfc"
    const val SYS_SETTING_AIRPLANE = "system_setting.airplane"
    const val SYS_SETTING_DND = "system_setting.dnd"
    const val SYS_SETTING_FLASHLIGHT = "system_setting.flashlight"
    const val SYS_SETTING_DARKMODE = "system_setting.darkmode"
    const val SYS_SETTING_POWERSAVE = "system_setting.powersave"
    const val SYS_SETTING_EXTRADIM = "system_setting.extradim"
    const val SYS_SETTING_AUTOBRIGHTNESS = "system_setting.autobrightness"
    const val SYS_SETTING_LOCATION = "system_setting.location"
    const val SYS_SETTING_HOTSPOT = "system_setting.hotspot"
    const val SYS_SETTING_EYEPROTECT = "system_setting.eyeprotect"
    const val SYS_SETTING_BYPASSCHARGE = "system_setting.bypasscharge"

    // —— 自定义变量（`ui/screen/variables/`）——

    /** 「快捷功能」组里那一格的名字（也是这一页在顶栏上的名字）。 */
    const val TOOL_VARIABLES = "tool.variables"

    // —— 应用控制（「打开应用」与「结束应用」合成一格，见 `ActionCatalog.APP_CONTROL_TILE`）——

    /** 「快捷功能」组里那一格的名字：点它弹一层菜单（打开应用 / 结束应用）。 */
    const val TOOL_APP_CONTROL = "tool.app_control"

    /** 菜单里「结束应用」那一行（也是绑好之后那一档动作的名字前半句）。 */
    const val TOOL_STOP_APP = "tool.stop_app"

    /** 变量操作落成一步之后，步骤行上那句标题（`%1$s` 变量名 / `%2$s` 值）。 */
    const val VAR_STEP_SET = "var.step.set"
    const val VAR_STEP_ADD = "var.step.add"
    const val VAR_STEP_SUB = "var.step.sub"

    /** 取反没有值，`%1$s` 只是变量名。 */
    const val VAR_STEP_NOT = "var.step.not"

    /** 首页「其他」卡里「自定义变量」那一行的副标题。 */
    const val HOME_VARIABLES_DESC = "home.variables_desc"

    /**
     * 变量页**页内**那句小标题。
     *
     *  与 [TOOL_VARIABLES] 分开：顶栏已经写着「自定义变量」，页内小标题再原样重复一遍等于白占一行；
     *  网格那一格也还用着 [TOOL_VARIABLES]，跟着改会把它撑长。
     */
    const val VARIABLES_TITLE = "variables.title"

    /** 变量页空态。 */
    const val VARIABLES_EMPTY = "variables.empty"

    /** 变量文件读不懂时那一屏（与空态分开：这一档不能引导新建，那样会盖掉盘上那份）。 */
    const val VARIABLES_UNREADABLE = "variables.unreadable"

    /** 新建变量（抽屉标题与右下角那颗加法入口共用）。 */
    const val VARIABLES_ADD = "variables.add"

    /** 新建抽屉右上角那颗按钮 —— 动词，不是标题（标题已经写了「新建变量」）。 */
    const val VARIABLES_CREATE = "variables.create"

    /** 变量类型三档。 */
    const val VAR_TYPE_STRING = "var.type_string"
    const val VAR_TYPE_NUMBER = "var.type_number"
    const val VAR_TYPE_BOOLEAN = "var.type_boolean"

    /** 抽屉里的操作。 */
    const val VAR_OP_SET = "var.op_set"
    const val VAR_OP_TOGGLE = "var.op_toggle"
    const val VAR_OP_INC = "var.op_inc"
    const val VAR_OP_DEC = "var.op_dec"
    const val VAR_OP_RESET = "var.op_reset"

    /** 表单里的三块小标题（也是三格输入 / 那一行的标题）。 */
    const val VAR_FIELD_NAME = "var.field_name"
    const val VAR_FIELD_VALUE = "var.field_value"
    const val VAR_FIELD_INITIAL = "var.field_initial"
    const val VAR_FIELD_TYPE = "var.field_type"

    /** 两格输入的占位文案 —— 小标题已经写了「名字 / 初始值」，占位里不再重复一遍。 */
    const val VAR_NAME_HINT = "var.name_hint"
    const val VAR_INITIAL_HINT = "var.initial_hint"

    /** 布尔的两种取值（存储里是 `true` / `false`，见 `VarType.TEXT_TRUE`）。 */
    const val VAR_VALUE_TRUE = "var.value_true"
    const val VAR_VALUE_FALSE = "var.value_false"

    /** 结果提示。 */
    const val VAR_SAVED = "var.saved"
    const val VAR_FAILED = "var.failed"

    /** 变量到上限了（`Variable.MAX_COUNT`），参数是那个上限。 */
    const val VAR_LIMIT = "var.limit"
    const val VAR_NAME_INVALID = "var.name_invalid"
    const val VAR_VALUE_INVALID = "var.value_invalid"

    /**
     * 绑上一步变量操作之后那句回执（`%s` = 那一步的正文）。
     *
     *  **不复用 `toast.bound`**：那一句自带「」，而变量那一步的正文自己已经用「」框住了变量名
     *  （见 [VAR_STEP_SET]），套两层就成了「已绑定「「计数」+= 1」」。
     */
    const val VAR_BOUND = "var.bound"

    /** 删除二次确认（标题带变量名，用 `I18n.format`）。 */
    const val VAR_DELETE_TITLE = "var.delete_title"
    const val VAR_DELETE_SUMMARY = "var.delete_summary"

    /**
     * 还有人在引用这颗变量，删不了（`%d` = 引用处数）。
     *
     *  比「删掉之后引用它的命令会当它不存在」更进一步：那一句是**告知后果**，这一句是**拦住**——
     *  留下空引用的话，按下去那条命令只会得到一句「变量不存在」，用户还得回头找是谁在用它。
     */
    const val VAR_DELETE_BLOCKED = "var.delete_blocked"

    // ── 检查更新 ──

    /** 弹窗标题：查到新版本。 */
    const val UPDATE_TITLE = "update.title"

    /**
     * 首页顶部那条提示的正文（`%s` = 最新版本号）。
     *
     *  **不复用 [UPDATE_TITLE]**：那句是弹窗的标题、不带版号，两条信息各归一处 ——
     *  钉在首页的那一条要让人一眼看出「新的是哪一版」，又塞不下「当前 / 最新」两行。
     */
    const val UPDATE_TIP = "update.tip"

    /**
     * 首页那条提示尾部的动作项。
     *
     *  **不叫「去下载」**：这一下开的是弹窗（说明、「稍后」、「不再提示」都在窗里），
     *  真正开浏览器的是窗里那颗 —— 两处同名会让人以为点一下就直接开始下载。
     */
    const val UPDATE_VIEW = "update.view"

    /** 正文：「最新 1.4.0」（一个 `%s`，版本号已去掉发布序号，见 `ReleaseInfo.displayVersion`）。 */
    const val UPDATE_BODY = "update.body"

    /** 更新说明那一小块的小标题。 */
    const val UPDATE_NOTES = "update.notes"

    /** 动作项：去浏览器打开这条发布。 */
    const val UPDATE_DOWNLOAD = "update.download"

    /** 取消项：这次不更新。 */
    const val UPDATE_LATER = "update.later"

    /** 勾选框：这一版别再提示了。 */
    const val UPDATE_DONT_REMIND = "update.dont_remind"

    /** 「关于」卡里的「检查更新」那一行（标题，也用在「版本」菜单里）。 */
    const val UPDATE_CHECK_ROW = "update.check_row"

    /**
     * 已经查到新版时，「版本」菜单里那一行的说法（顶掉 [UPDATE_CHECK_ROW]）。
     *
     *  这时候还写「检查更新」等于让人白点一次：结果早就有了。改口成「发现新版本」，
     *  点下去仍然是查（会自动弹窗），但读起来是「这儿有新东西」。
     */
    const val UPDATE_FOUND_ROW = "update.found_row"

    /** 「版本」菜单里的「项目仓库」那一行。 */
    const val MORE_ABOUT_REPO = "more.about_repo"

    /** 手动点「检查更新」、开始查的那一刻那句提示。 */
    const val UPDATE_CHECKING = "update.checking"

    /** 手动点「检查更新」、已经是最新时的那句回执。 */
    const val UPDATE_LATEST = "update.latest"

    /** 手动点「检查更新」、没查到（没网 / 接口失败）时的那句回执。 */
    const val UPDATE_FAILED = "update.failed"

    /** 这条 release 没给下载地址（`html_url` 缺失且拼不出回退地址）时的回执。 */
    const val UPDATE_NO_URL = "update.no_url"
}
