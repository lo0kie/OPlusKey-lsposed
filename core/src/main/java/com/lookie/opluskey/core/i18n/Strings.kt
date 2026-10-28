package com.lookie.opluskey.core.i18n

/**
 * 集中管理所有多语言文案 Key 常量。
 * 对应多语言字典存放在 assets/locales/zh-CN.json 等 JSON 文件中。
 */
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
    const val APP_PICKER_CONFIRM_SELECTION = "app_picker.confirm_selection"
    const val APP_PICKER_EMPTY_HINT_BROKEN = "app_picker.empty_hint_broken"
    const val APP_PICKER_EMPTY_OPENABLE = "app_picker.empty_openable"
    const val APP_PICKER_LOAD_FAILED = "app_picker.load_failed"
    const val APP_PICKER_LOADING_APPS = "app_picker.loading_apps"
    const val APP_PICKER_NO_SEARCH_RESULT = "app_picker.no_search_result"
    const val APP_PICKER_NO_SEARCH_RESULT_ACTIVITY = "app_picker.no_search_result_activity"
    const val APP_PICKER_PULL_REFRESH = "app_picker.pull_refresh"
    const val APP_PICKER_REFRESH_DONE = "app_picker.refresh_done"
    const val APP_PICKER_REFRESHING = "app_picker.refreshing"
    const val APP_PICKER_RELEASE_REFRESH = "app_picker.release_refresh"
    const val APP_PICKER_SECTION_ACTIVITIES = "app_picker.section_activities"
    const val APP_PICKER_SECTION_SHORTCUTS = "app_picker.section_shortcuts"
    const val APP_PICKER_SEARCH_ACTIVITY = "app_picker.search_activity"
    const val APP_PICKER_SEARCH_APP = "app_picker.search_app"
    const val APP_PICKER_SELECTED_FORMAT = "app_picker.selected_format"
    const val APP_PICKER_TAB_SYSTEM = "app_picker.tab_system"
    const val APP_PICKER_TAB_USER = "app_picker.tab_user"
    const val APP_PICKER_TAB_XPOSED = "app_picker.tab_xposed"
    const val APP_PICKER_TAG_UNOPENABLE = "app_picker.tag_unopenable"

    /** 应用名旁边那枚「分身」标签（多开槽里的那一份）。 */
    const val APP_PICKER_CLONE_TAG = "app_picker.clone_tag"
    const val APP_PICKER_TITLE = "app_picker.title"
    const val APP_PICKER_UNSELECTED = "app_picker.unselected"
    const val BINDING_CREATE_CUSTOM_COMMAND = "binding.create_custom_command"

    const val BINDING_CREATE_CUSTOM_COMMAND_DESC = "binding.create_custom_command_desc"
    const val BINDING_DELETE_KEY = "binding.delete_key"

    /** 按键菜单里那一行：把这颗自定义键**停用**（键还在，只是不接管）。 */
    const val BINDING_DISABLE_KEY = "binding.disable_key"

    /** 同一行的另一半：重新启用。 */
    const val BINDING_ENABLE_KEY = "binding.enable_key"

    /**
     * 停用那颗键之前问一句（`%s` = 键名）。
     *
     *  与 [BINDING_DELETE_KEY_CONFIRM] 正好把两种后果说开：一个是「不再接管但都还在」，
     *  一个是「从清单里删掉」。这两行在菜单里只差一个字，不确认的话很容易点错。
     */
    const val BINDING_DISABLE_KEY_CONFIRM = "binding.disable_key_confirm"
    const val BINDING_DELETE_KEY_CONFIRM = "binding.delete_key_confirm"
    const val BINDING_ADD_KEY = "binding.add_key"

    /**
     * 「添加按键」那一行的副标题 —— **还没到上限**时说的那句；到上限时这一格换成原因（`key_watch.limit`）。
     *
     *  这一行点下去是开一次**按键收集**（弹窗里按下要加的那颗键），光看「添加按键」四个字看不出
     *  这一步发生在模块外面。满员那一格被原因占着，所以这句只在未满时出现。
     */
    const val BINDING_ADD_KEY_DESC = "binding.add_key_desc"

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
    const val BINDING_DOUBLE_CLICK_WAIT = "binding.double_click_wait"

    /**
     * 「忽视无操作双击手势」那行开关的副标题 —— **开 / 关各一句**，两句都说「现在是什么行为」，
     * 不写「关掉会怎样」（全项目 switch 副标题同一条口径，见 `SwitchRow` 的说明）。
     */
    const val BINDING_INSTANT_SINGLE_OFF = "binding.instant_single_off"
    const val BINDING_INSTANT_SINGLE_ON = "binding.instant_single_on"
    const val BINDING_LONG_PRESS_DELAY_DESC = "binding.long_press_delay_desc"
    const val BINDING_LONG_PRESS_DELAY_TITLE = "binding.long_press_delay_title"
    const val BINDING_LONG_PRESS_REPEAT_DESC_OFF = "binding.long_press_repeat_desc_off"
    const val BINDING_LONG_PRESS_REPEAT_DESC_ON = "binding.long_press_repeat_desc_on"
    const val BINDING_LONG_PRESS_REPEAT_TITLE = "binding.long_press_repeat_title"

    /** 「按键绑定」页大标题下面那行小标题：当前这一组是「快捷键」。 */
    const val BINDING_SECTION_KEYS = "binding.section_keys"
    const val BINDING_REPEAT_INTERVAL_DESC = "binding.repeat_interval_desc"
    const val BINDING_REPEAT_INTERVAL_TITLE = "binding.repeat_interval_title"
    const val BINDING_REPEAT_VIBRATE_DESC_OFF = "binding.repeat_vibrate_desc_off"
    const val BINDING_REPEAT_VIBRATE_DESC_ON = "binding.repeat_vibrate_desc_on"
    const val BINDING_REPEAT_VIBRATE_TITLE = "binding.repeat_vibrate_title"
    const val BINDING_SERIAL_UNSUPPORTED_REPEAT = "binding.serial_unsupported_repeat"
    const val BINDING_VOICE_UNSUPPORTED_REPEAT = "binding.voice_unsupported_repeat"
    const val BINDING_SETTINGS_SECTION = "binding.settings_section"
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

    /**
     * 列表页的标题 —— 「组合按键列表」。
     *
     *  与 [COMBO_TITLE] 分开：列表页说的是「这里有几档」，编辑页说的是「这一档」，
     *  两页同一个标题时顶栏看不出自己在哪一层。
     */
    const val COMBO_LIST_TITLE = "combo.list_title"

    /** 首页「按键操作」卡里那一行的标题 —— 「组合按键」。 */
    const val COMBO_ENTRY = "combo.entry"

    /** 列表末行「添加组合按键」。 */
    const val COMBO_ADD = "combo.add"

    /** 选键弹窗那颗确认按钮 —— 只写「添加」（窗里已经说了选的是按键，再补三个字太挤）。 */
    const val COMBO_ADD_CONFIRM = "combo.add_confirm"

    /** 「添加组合键」那一行的副标题（没满员时）。 */
    const val COMBO_ADD_DESC = "combo.add_desc"

    /** 组合键的两档：列表副标题与编辑页小标题共用。 */
    const val COMBO_SHORT = "combo.short"
    const val COMBO_LONG = "combo.long"

    /** 选键弹窗的标题。 */
    const val COMBO_PICK_KEYS = "combo.pick_keys"

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

    /** 停用那一档之前的确认正文（`%s` = 成员名，已带各自的括号）—— 一句就够，别写成说明书。 */
    const val COMBO_DISABLE_MESSAGE = "combo.disable_message"

    /** 组合键满员时「添加组合键」那一行的副标题（格式：上限组数）。 */
    const val COMBO_LIMIT = "combo.limit"

    /** 首页那一行的副标题：已经配了几组（格式：组数）。 */
    const val COMBO_COUNT = "combo.count"

    /** 列表页筛选菜单的第一行：不筛（列出全部组合）。 */
    const val COMBO_FILTER_ALL = "combo.filter_all"

    /**
     * 一颗自定义键都还没收时，首页那行「组合按键」的副标题与「为什么灰着」的说明。
     *
     *  组合要**两颗键**：只有默认侧键时根本配不出来（选键弹窗里只有一行），
     *  所以那一行在这一档直接置灰，并把原因写在副标题上 —— 不用用户去点一下才发现。
     */
    const val COMBO_NEED_KEY = "combo.need_key"

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

    /** 组合键：按键之间的等待（两颗键按下最多隔多久才算一起按下）。 */
    const val COMBO_WINDOW_TITLE = "combo.window_title"

    /** 组合键：等待时间的副标题（提示调大会带来额外的等待）。 */
    const val COMBO_WINDOW_DESC = "combo.window_desc"

    /** 灵感语音 —— 一键闪记的「长按录音」那一档（只在长按网格出现）。 */
    const val CATALOG_VOICE_MEMORY = "catalog.voice_memory"

    /** 唤醒小布 —— 与系统「长按电源键」同一个能力（`oplus:voice_assist`）。 */
    const val CATALOG_VOICE_ASSIST = "catalog.voice_assist"
    const val CATALOG_MODE_BACK = "catalog.mode_back"
    const val CATALOG_MODE_COLLAPSE = "catalog.mode_collapse"
    const val CATALOG_MODE_CONTROL_CENTER = "catalog.mode_control_center"
    const val CATALOG_MODE_CYCLE = "catalog.mode_cycle"

    /** 系统开关再下一层的三档：切换 / 打开 / 关闭。 */
    const val CATALOG_MODE_TOGGLE = "catalog.mode_toggle"
    const val CATALOG_MODE_TURN_ON = "catalog.mode_turn_on"
    const val CATALOG_MODE_TURN_OFF = "catalog.mode_turn_off"

    /** 同上的**带开关名**写法（「切换免打扰」）—— 选择器里逐行读的地方用。 */
    const val CATALOG_MODE_TOGGLE_OF = "catalog.mode_toggle_of"
    const val CATALOG_MODE_TURN_ON_OF = "catalog.mode_turn_on_of"
    const val CATALOG_MODE_TURN_OFF_OF = "catalog.mode_turn_off_of"
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
    const val CATALOG_QUICK_TOOLS = "catalog.quick_tools"
    const val CATALOG_RECORDING = "catalog.recording"
    const val CATALOG_SCREEN_RECORD = "catalog.screen_record"
    const val CATALOG_SCREENSHOT = "catalog.screenshot"
    const val CATALOG_SYSTEM_ACTIONS = "catalog.system_actions"
    const val CATALOG_SYSTEM_KEYS = "catalog.system_keys"
    const val CATEGORY_MEDIA = "category.media"
    const val CATEGORY_PANEL = "category.panel"
    const val CATEGORY_RINGER = "category.ringer"
    const val CATEGORY_SYSTEM_NAV = "category.system_nav"
    const val COMMAND_ADD = "command.add"
    const val COMMAND_CMD_HINT = "command.cmd_hint"
    const val COMMAND_CYCLE_ERROR = "command.cycle_error"
    const val COMMAND_DELAY_DESC = "command.delay_desc"
    const val COMMAND_DELAY_LABEL = "command.delay_label"
    const val COMMAND_FORMAT_CMD = "command.format_cmd"

    /** 「支持的命令格式」卡右列那一句说明 —— 与左列的前缀文案分开两条，好在界面上对成一列。 */
    const val COMMAND_FORMAT_CMD_DESC = "command.format_cmd_desc"
    const val COMMAND_FORMAT_INTENT = "command.format_intent"
    const val COMMAND_FORMAT_INTENT_DESC = "command.format_intent_desc"
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
    const val COMMAND_PREFIX_FILL = "command.prefix_fill"
    const val COMMAND_SAVED_COMMANDS = "command.saved_commands"
    const val COMMAND_SAVED_COUNT = "command.saved_count"
    const val COMMAND_SINGLE_HINT = "command.single_hint"
    const val COMMAND_STEP_SELECT_TITLE = "command.step_select_title"

    /** 「换掉这一步 / 换掉这一条动作」那张抽屉的标题 —— 与 [COMMAND_STEP_SELECT_TITLE] 同一处、两种来处。 */
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

    /** 表单第一条小标题：名称输入框（原先这里写的是「修改命令 / 新增命令」）。 */
    const val COMMAND_NAME_TITLE = "command.name_title"

    /** 多条命令那条小标题：命令间隔时长那张卡（这一组是「跑这条命令时的设置」）。 */
    const val COMMAND_SETTINGS_TITLE = "command.settings_title"

    /** 表单第二条小标题：命令正文（单条命令那个输入框）。 */
    const val COMMAND_DETAIL_TITLE = "command.detail_title"
    const val COMMAND_TYPE_SERIAL = "command.type_serial"
    const val COMMAND_TYPE_BRANCH = "command.type_branch"

    const val COMMAND_BRANCH_ADD = "command.branch_add"
    const val COMMAND_BRANCH_WHEN_APP = "command.branch_when_app"
    const val COMMAND_BRANCH_ELSE = "command.branch_else"
    const val COMMAND_BRANCH_ANY = "command.branch_any"

    /** 一条条件的小标题（「条件 1」）。 */
    const val COMMAND_BRANCH_ITEM = "command.branch_item"
    const val COMMAND_BRANCH_NO_COMMAND = "command.branch_no_command"
    const val COMMAND_BRANCH_DUPLICATE = "command.branch_duplicate"

    /**
     * **某一个取值**已经用掉了（二级那层）—— 与上面那条「整档都加满了」不是一回事：
     * 屏幕状态加过「亮屏」之后，「锁屏」照样能加。
     */
    const val COMMAND_BRANCH_DUPLICATE_VALUE = "command.branch_duplicate_value"

    /**
     * 「再加一条也没有意义了」—— 表里某一档条件的取值全在，无论什么状态前面都有成立的一条。
     * 点「添加」时说的就是这句。
     */
    const val COMMAND_BRANCH_SATURATED = "command.branch_saturated"

    /**
     * 死分支那一行的小标题后缀（「条件 3 · 不会执行」）—— 它前面某一档条件已经凑齐，
     * 这一条永远轮不到。与 [COMMAND_BRANCH_SATURATED] 是同一件事的两种说法：
     * 那条是点「确认」时的整句，这条是行上的标签。
     */
    const val COMMAND_BRANCH_DEAD = "command.branch_dead"

    /**
     * 「这一条被前面那条更宽的条件盖住了」—— 点「确认」时说的另一句话。
     *
     *  与 [COMMAND_BRANCH_SATURATED] 分开：那条说的是「某一档取值凑齐、整张表恒成立」，
     *  这条说的是「前面某一行的条件更宽且被这一行包含」—— 组合条件（一块好几条）之后才出现的情形，
     *  拿「恒成立」那句去说它是不对的（前面那条并不恒成立）。
     */
    const val COMMAND_BRANCH_SUBSUMED = "command.branch_subsumed"

    /** 「前台应用」可以多条，但**同一个应用**只能当一次条件。 */
    const val COMMAND_BRANCH_DUPLICATE_APP = "command.branch_duplicate_app"
    const val COMMAND_BRANCH_EMPTY = "command.branch_empty"

    /**
     * 停用 / 启用一条场景 —— 行尾那颗「更多」菜单里的那一行，以及停用点下去那道确认窗。
     *
     *  与按键那一对（`BINDING_DISABLE_KEY` / `BINDING_ENABLE_KEY`）同一个语义：写的是这一条自己的
     *  `StateRule.enabled`。**启用没有确认窗**（与按键、组合键同一条规矩）。
     */
    const val COMMAND_BRANCH_DISABLE = "command.branch_disable"
    const val COMMAND_BRANCH_ENABLE = "command.branch_enable"

    /** 确认窗的正文 —— 一句话说清「停用之后会怎样」。 */
    const val COMMAND_BRANCH_DISABLE_MESSAGE = "command.branch_disable_message"

    /** 停用那一条行上的标签（「条件 3」行尾那半句，与 [COMMAND_BRANCH_DEAD] 同一个位置）。 */
    const val COMMAND_BRANCH_DISABLED = "command.branch_disabled"

    /** 条件页空态的**标题**（上面那行）—— 下面那行说的是「现在会发生什么」。 */
    const val COMMAND_BRANCH_EMPTY_TITLE = "command.branch_empty_title"

    /** 规则行里「条件那一格还没选」—— 与「未选择命令」成对，行首图标同时置红。 */
    const val COMMAND_BRANCH_PICK_CONDITION = "command.branch_pick_condition"
    const val COMMAND_BRANCH_NEED_COMMAND = "command.branch_need_command"
    const val COMMAND_BRANCH_NEED_CONDITION = "command.branch_need_condition"
    const val COMMAND_BRANCH_DEFAULT = "command.branch_default"

    /** 条件执行里每一种条件的叫法（分支行、条件菜单、摘要共用）。 */
    const val COMMAND_COND_APP = "command.cond_app"
    const val COMMAND_COND_RINGER_NAME = "command.cond_ringer_name"
    const val COMMAND_COND_ORIENTATION_NAME = "command.cond_orientation_name"
    const val COMMAND_COND_SCREEN_ON = "command.cond_screen_on"
    const val COMMAND_COND_LOCKED = "command.cond_locked"
    const val COMMAND_COND_SCREEN = "command.cond_screen"
    const val COMMAND_COND_SCREEN_NAME = "command.cond_screen_name"
    const val COMMAND_COND_CHARGING = "command.cond_charging"
    const val COMMAND_COND_CHARGING_NAME = "command.cond_charging_name"
    const val COMMAND_COND_CHARGING_OFF = "command.cond_charging_off"
    const val COMMAND_COND_HEADSET = "command.cond_headset"
    const val COMMAND_COND_HEADSET_NAME = "command.cond_headset_name"
    const val COMMAND_COND_HEADSET_OFF = "command.cond_headset_off"
    const val COMMAND_COND_MEDIA = "command.cond_media"
    const val COMMAND_COND_MEDIA_NAME = "command.cond_media_name"
    const val COMMAND_COND_MEDIA_PLAYING = "command.cond_media_playing"
    const val COMMAND_COND_MEDIA_PAUSED = "command.cond_media_paused"
    const val COMMAND_COND_WIFI = "command.cond_wifi"
    const val COMMAND_COND_WIFI_NAME = "command.cond_wifi_name"
    const val COMMAND_COND_WIFI_ON = "command.cond_wifi_on"
    const val COMMAND_COND_WIFI_OFF = "command.cond_wifi_off"
    const val COMMAND_COND_RINGER = "command.cond_ringer"
    const val COMMAND_COND_RINGER_NORMAL = "command.cond_ringer_normal"
    const val COMMAND_COND_RINGER_VIBRATE = "command.cond_ringer_vibrate"
    const val COMMAND_COND_RINGER_SILENT = "command.cond_ringer_silent"
    const val COMMAND_COND_ORIENTATION = "command.cond_orientation"
    const val COMMAND_COND_ORIENTATION_PORTRAIT = "command.cond_orientation_portrait"
    const val COMMAND_COND_ORIENTATION_LANDSCAPE = "command.cond_orientation_landscape"

    //  时间区间与另外三个系统开关（蓝牙 / 移动数据 / 飞行模式）——
    //  `_name` 是菜单里那种类名（「时间区间」「蓝牙」），不带 `_name` 的是格子里那句话
    //  （「时间 22:00-07:00」「蓝牙 开」）。
    const val COMMAND_COND_TIME = "command.cond_time"
    const val COMMAND_COND_TIME_NAME = "command.cond_time_name"
    const val COMMAND_COND_TIME_OVERLAP = "command.cond_time_overlap"
    const val COMMAND_COND_BLUETOOTH = "command.cond_bluetooth"
    const val COMMAND_COND_BLUETOOTH_NAME = "command.cond_bluetooth_name"
    const val COMMAND_COND_CELLULAR = "command.cond_cellular"
    const val COMMAND_COND_CELLULAR_NAME = "command.cond_cellular_name"
    const val COMMAND_COND_AIRPLANE = "command.cond_airplane"
    const val COMMAND_COND_AIRPLANE_NAME = "command.cond_airplane_name"

    /** 一块条件里再分几条时的那几句（第几条 / 添加条件）。 */
    const val COMMAND_BRANCH_CONDITION_INDEX = "command.branch_condition_index"
    const val COMMAND_BRANCH_ADD_CONDITION = "command.branch_add_condition"

    /** 「添加条件」被拦下时那句话：这一块最后一条还是「未选择条件」，再插一条只是多一行空的。 */
    const val COMMAND_BRANCH_CONDITION_PENDING = "command.branch_condition_pending"
    const val COMMAND_BRANCH_TOP_REACHED = "command.branch_top_reached"
    const val COMMAND_BRANCH_BOTTOM_REACHED = "command.branch_bottom_reached"

    /** 抽屉里那个过滤框的占位文案与空结果 —— 内置动作 + 已保存命令一起搜。 */
    const val COMMAND_SEARCH = "command.search"
    const val COMMAND_SEARCH_EMPTY = "command.search_empty"

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
    const val COMMAND_FAILURE_SHELL_REJECTED = "command_failure.shell_rejected"
    const val COMMAND_FAILURE_SHELL_TIMEOUT = "command_failure.shell_timeout"
    const val COMMAND_FAILURE_SHORTCUT_FAILED = "command_failure.shortcut_failed"
    const val COMMAND_FAILURE_TOO_DEEP = "command_failure.too_deep"
    const val COMMAND_FAILURE_UNKNOWN = "command_failure.unknown"
    const val COMMON_BACK = "common.back"
    const val COMMON_CANCEL = "common.cancel"
    const val COMMON_CONFIRM = "common.confirm"
    const val COMMON_DELETE = "common.delete"

    /** 停用确认窗上那颗按钮 —— 与 [COMMON_DELETE] 同一档：一颗动词，不带宾语。 */
    const val COMMON_DISABLE = "common.disable"
    const val COMMON_DISCARD = "common.discard"
    const val COMMON_EDIT = "common.edit"
    const val COMMON_MENU = "common.menu"
    const val COMMON_MODIFY = "common.modify"
    const val COMMON_MOVE_DOWN = "common.move_down"
    const val COMMON_MORE = "common.more"
    const val COMMON_MOVE_UP = "common.move_up"
    const val COMMON_OPEN_APP_FORMAT = "common.open_app_format"
    const val COMMON_OPEN_MINI_WINDOW_FORMAT = "common.open_mini_window_format"
    const val COMMON_OPEN_SHORTCUT_FORMAT = "common.open_shortcut_format"

    /** 「系统开关」里那几颗在执行提示 / 步骤行里的写法（`切换 WIFI`）。 */
    const val COMMON_TOGGLE_SETTING_FORMAT = "common.toggle_setting_format"

    /** 系统开关「打开 / 关闭」那两档的步骤标题。 */
    const val COMMON_TURN_ON_SETTING_FORMAT = "common.turn_on_setting_format"
    const val COMMON_TURN_OFF_SETTING_FORMAT = "common.turn_off_setting_format"
    const val COMMON_REPLACE = "common.replace"
    const val COMMON_RESET = "common.reset"
    const val COMMON_SAVE = "common.save"
    const val COMMON_STEP_INDEX = "common.step_index"
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

    /** 多配置：默认 */
    const val PROFILE_DEFAULT = "profile.default"

    /** 多配置：配置 %d */
    const val PROFILE_NEW_NAME = "profile.new_name"

    /** 多配置：已切到「%s」 */
    const val PROFILE_SWITCHED = "profile.switched"

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

    /** 多配置：最多三份配置（除「默认」之外） */
    const val PROFILE_FULL = "profile.full"

    /** 多配置：删除配置确认窗的标题 */
    const val DIALOG_DELETE_PROFILE_TITLE = "dialog.delete_profile_title"

    /** 多配置：删除配置确认窗的正文（「**%s**」是被删那份的名字） */
    const val DIALOG_DELETE_PROFILE_MESSAGE = "dialog.delete_profile_message"

    const val DIALOG_CLEAR_CONFIG_MESSAGE = "dialog.clear_config_message"
    const val DIALOG_CLEAR_CONFIG_TITLE = "dialog.clear_config_title"

    /** 删掉「条件执行」里的一条（那条条件与它的动作一起没了，顺序会往前顶）。 */
    const val DIALOG_DELETE_CONDITION_MESSAGE = "dialog.delete_condition_message"
    const val DIALOG_DELETE_CONDITION_TITLE = "dialog.delete_condition_title"
    const val DIALOG_DELETE_ONE_CONDITION_MESSAGE = "dialog.delete_one_condition_message"
    const val DIALOG_DELETE_ONE_CONDITION_TITLE = "dialog.delete_one_condition_title"
    const val DIALOG_DELETE_COMMAND_SERIAL_MESSAGE = "dialog.delete_command_serial_message"
    const val DIALOG_DELETE_COMMAND_SINGLE_MESSAGE = "dialog.delete_command_single_message"
    const val DIALOG_DELETE_COMMAND_TITLE = "dialog.delete_command_title"
    const val DIALOG_DISCARD_COMMAND_EDIT_MESSAGE = "dialog.discard_command_edit_message"
    const val DIALOG_DISCARD_COMMAND_TITLE = "dialog.discard_command_title"

    /** 「条件执行」页那一份（那一页草稿里是条件表，不是命令）—— 与上面那条各说各的页。 */
    const val DIALOG_DISCARD_CONDITION_TITLE = "dialog.discard_condition_title"

    /** 「支持的命令格式」点前缀时输入框里已有内容 —— 覆盖前问一句（那里是整段替换，不是插入）。 */
    const val DIALOG_FILL_PREFIX_TITLE = "dialog.fill_prefix_title"

    /** 同上，正文里那一个是**将要填进去的前缀**（`%s`）。 */
    const val DIALOG_FILL_PREFIX_MESSAGE = "dialog.fill_prefix_message"
    const val DIALOG_COMMAND_DISCLAIMER_CONTENT = "dialog.command_disclaimer_content"
    const val DIALOG_COMMAND_DISCLAIMER_TITLE = "dialog.command_disclaimer_title"

    /** 通用那颗「确认」—— 目前只有「温馨提醒」用（两颗按钮都只是关窗，见 `ui/screen/HomeScreen`）。 */
    const val DIALOG_CONFIRM = "dialog.confirm"
    const val DIALOG_DISCLAIMER_ACKNOWLEDGE = "dialog.disclaimer_acknowledge"
    const val DIALOG_DISCLAIMER_CONTENT = "dialog.disclaimer_content"
    const val DIALOG_DISCLAIMER_TITLE = "dialog.disclaimer_title"

    /** 「温馨提醒」：免责声明确认之后接着弹的那一个（模块免费、认准官方正版）。 */
    const val DIALOG_FREE_NOTICE_TITLE = "dialog.free_notice_title"
    const val DIALOG_FREE_NOTICE_CONTENT = "dialog.free_notice_content"

    /** 内置键「侧键」的名字 —— 提示里的主语用它，自定义键名是用户起的、不译。 */
    const val KEY_SIDE = "key.side"

    const val GESTURE_DOUBLE = "gesture.double"
    const val GESTURE_LONG = "gesture.long"
    const val GESTURE_SINGLE = "gesture.single"
    const val HOME_BINDING_CARD_TITLE = "home.binding_card_title"
    const val HOME_DONATE = "home.donate"
    const val HOME_DONATE_SUMMARY = "home.donate_summary"
    const val HOME_MORE_OPTIONS_SUMMARY = "home.more_options_summary"
    const val HOME_OTHER_SECTION = "home.other_section"

    /** 首页「按键列表」那一节的小标题（只有键清单 —— 收集来的那几颗）。 */
    const val HOME_KEYS_SECTION = "home.keys_section"

    /**
     * 首页「按键操作」那一节的小标题 —— 组合按键与「添加按键」两行。
     *
     *  这两行原先并进 [HOME_KEYS_SECTION] 那张卡里，于是那张卡同时装着「已经有哪些键」与
     *  「还能做什么」两件事，读起来像同一类东西排在一起（用户口径：分开）。
     */
    const val HOME_KEY_OPS_SECTION = "home.key_ops_section"

    /**
     * 首页第一节的小标题 —— 那一节只有模块总闸一行。
     *
     *  **不能复用 [BINDING_TITLE]**：那一个是「按键绑定」**子页**的标题（顶栏用，见 `PageTopBar`），
     *  两处共用一个键的话，改首页小标题会顺手把子页也改名。
     */
    const val HOME_MASTER_SECTION = "home.master_section"

    /** 首页主开关（「启用按键绑定」）的副标题 —— 开 / 关各一句。 */
    const val HOME_SWITCH_DESC_OFF = "home.switch_desc_off"
    const val HOME_SWITCH_DESC_ON = "home.switch_desc_on"

    /** 首页「使用 ColorOS 接口」那行开关的标题与副标题。 */
    const val HOME_COLOROS_TITLE = "home.coloros_title"

    const val HOME_COLOROS_DESC_OFF = "home.coloros_desc_off"
    const val HOME_COLOROS_DESC_ON = "home.coloros_desc_on"
    const val HOME_TITLE = "home.title"
    const val HOME_MINI_ANIM_DESC_OFF = "home.mini_anim_desc_off"
    const val HOME_MINI_ANIM_DESC_ON = "home.mini_anim_desc_on"
    const val HOME_MINI_ANIM_TITLE = "home.mini_anim_title"
    const val HOME_TOAST_DESC_OFF = "home.toast_desc_off"
    const val HOME_TOAST_DESC_ON = "home.toast_desc_on"
    const val HOME_TOAST_TITLE = "home.toast_title"
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
    const val LINK_CHECK_VERIFIED = "link_check.verified"
    const val LINK_CHECK_WAITING_RESPONSE = "link_check.waiting_response"
    const val MEDIA_NEXT = "media.next"
    const val MEDIA_PLAY_PAUSE = "media.play_pause"
    const val MEDIA_PREV = "media.prev"
    const val MEDIA_VOL_UP = "media.vol_up"
    const val MENU_CLEAR_CONFIG = "menu.clear_config"
    const val MENU_DEFAULT_PAGE = "menu.default_page"
    const val MENU_EXPORT_LOG = "menu.export_log"
    const val MENU_OPEN_IN_MINI_WINDOW = "menu.open_in_mini_window"
    const val MENU_RESTART_SERVICE = "menu.restart_service"
    const val MENU_SHOW_INVALID_APPS = "menu.show_invalid_apps"
    const val MENU_SHOW_SYSTEM_APPS = "menu.show_system_apps"
    const val MORE_ABOUT_AUTHOR = "more.about_author"
    const val MORE_ABOUT_FEEDBACK = "more.about_feedback"

    /** 「通用设置」页（标题与首页那一行共用）与它里面那节「接口选择」。 */
    const val GENERAL_TITLE = "general.title"
    const val GENERAL_SECTION_INTERFACE = "general.section_interface"

    /** 首页「通用设置」那一行的摘要。 */
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

    /** 「更多选项」页「外观」那一卡：界面颜色（跟随系统 / 浅色 / 深色）。 */
    const val MORE_THEME = "more.theme"

    /** 「跟随系统显示大小」那一行（开关）。 */
    const val MORE_FOLLOW_DISPLAY_SIZE = "more.follow_display_size"
    const val MORE_FOLLOW_DISPLAY_SIZE_ON = "more.follow_display_size_on"
    const val MORE_FOLLOW_DISPLAY_SIZE_OFF = "more.follow_display_size_off"

    /** 「页面缩放」那一行（只在「跟随系统显示大小」关掉时出现）。 */
    const val MORE_PAGE_SCALE = "more.page_scale"
    const val MORE_PAGE_SCALE_DESC = "more.page_scale_desc"
    const val MORE_THEME_AUTO = "more.theme_auto"
    const val MORE_THEME_LIGHT = "more.theme_light"
    const val MORE_THEME_DARK = "more.theme_dark"
    const val MORE_SIGNATURE_DEBUG = "more.signature_debug"
    const val MORE_SIGNATURE_MISMATCH = "more.signature_mismatch"
    const val MORE_SIGNATURE_UNKNOWN = "more.signature_unknown"
    const val NAV_RECENTS = "nav.recents"
    const val PANEL_POWER_MENU = "panel.power_menu"
    const val SETTING_DOUBLE_CLICK_DELAY = "setting.double_click_delay"
    const val SETTING_ENABLE_BINDING = "setting.enable_binding"
    const val SETTING_IGNORE_DOUBLE = "setting.ignore_double"
    const val SETTING_KEY_BINDING = "setting.key_binding"
    const val STATUS_API_MISMATCH = "status.api_mismatch"
    const val STATUS_CHECKING = "status.checking"
    const val STATUS_CONFIG_FAILED = "status.config_failed"
    const val STATUS_CONFIG_NOT_SYNCED = "status.config_not_synced"
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
    const val TOAST_CONFIG_EXPORT_FAILED = "toast.config_export_failed"
    const val TOAST_CONFIG_EXPORTED = "toast.config_exported"
    const val TOAST_CONFIG_IMPORT_FAILED = "toast.config_import_failed"
    const val TOAST_CONFIG_READ_FAILED = "toast.config_read_failed"
    const val TOAST_CONFIG_SAVE_BROADCAST_FAILED = "toast.config_save_broadcast_failed"
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

    /**
     * 「执行时提示」那一条（`GlobalConfig.toastOnRun`）—— 两个占位符：
     * **这次是哪一下**（`侧键·单击`）与**执行了什么**（`手电筒`）。
     *
     *  只说动作名时，连发几次不同的手势看不出分别（用户口径「换成更精确的」）；
     *  触发源那一半用的是界面上的同一个词（`KeyId.label` · `Gesture.label`），不另造说法。
     */
    const val TOAST_RUN_ACTION = "toast.run_action"
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
    const val KEY_WATCH_LIMIT = "key_watch.limit"
    const val KEY_WATCH_ADDED = "key_watch.added"
    const val KEY_WATCH_DUPLICATE = "key_watch.duplicate"

    /**
     * 「系统开关」那一格点开的菜单里那十条的名字 ——
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
}
