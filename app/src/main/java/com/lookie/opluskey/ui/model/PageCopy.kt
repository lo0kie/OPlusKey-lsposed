package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Gesture

// 页面文案与标签函数。 —— 从 PageConst.kt 拆出（同包搬迁，调用点不变）。

/** 「按键绑定」子页的标题。 */
internal val BINDING_TITLE: String
    get() = I18n.get(Strings.BINDING_TITLE, "按键绑定")

/** 「自定义命令」子页的标题（第三级页面，从「按键绑定」页尾部网格进入）。 */
internal val COMMANDS_TITLE: String
    get() = I18n.get(Strings.ACTION_CUSTOM_COMMAND, "自定义命令")

/** 「选择应用」页的标题。 */
internal val APP_PICKER_TITLE: String
    get() = I18n.get(Strings.APP_PICKER_TITLE, "选择应用")

/**
 * 「更多选项」页的标题（首页「其他 → 更多选项」那一行进入；这是**唯一入口**）。
 *
 * 用户要求从「关于模块」改叫「更多」、随后定稿为「更多选项」：这一页早就不只是
 * 「关于」—— 除了最下面「关于」那张卡（版本 / 作者 / 查看地址 / 反馈问题），还有**运行状态**、
 * **适配清单**与**配置导入导出**。
 * 内部符号仍叫 `ABOUT_*`（页面枚举 `Screen.ABOUT` 等同理），只改**给用户看的**这一处。
 */
internal val ABOUT_TITLE: String
    get() = I18n.get(Strings.MORE_OPTIONS_TITLE, "更多选项")

/**
 * 「捐赠列表」页的标题（首页「其他 → 捐赠打赏」那层菜单的最后一项进入；这是**唯一入口**）。
 *
 *  与那一行菜单项**共用同一个键**：菜单里写什么、进去之后顶上就写什么，两处串了没有意义。
 */
internal val DONATE_LIST_TITLE: String
    get() = I18n.get(Strings.DONATE_LIST_TITLE, "捐赠列表")

/**
 * 首页那条常驻提示的文案（页面被闸住时弹，见 [HomeScreen] 的 `blockHint`）。
 *
 * **一档一句、只说结论、不点技术细节**：用户界面不需要知道是哪一段断了，
 * 「哪一段」是给「更多选项 → 运行状态」看的；这一条只回答「现在能不能用、要我做什么」。
 * 五句一句话说清一件事，逐条见下面各条自己的说明。
 */
internal val MODULE_UNAVAILABLE_HINT: String
    get() = I18n.get(Strings.STATUS_UNAVAILABLE, "模块暂不可用，请稍候")

/**
 * 首页那条常驻提示的**另一种**文案：这次**配置没读成功**（文件在、但读不了 / 读不懂）。
 *
 * 与 [MODULE_UNAVAILABLE_HINT] 分开写，是因为两者要用户做的事不同：
 * 这一种用户自己就能解决 —— **重进页面**会重读一次；说「请稍候」会把人引到错方向
 *（等多久都没用）。此时页面整页只读（见 `SettingsPage.configReadOnly`）。
 */
internal val CONFIG_READ_FAILED_HINT: String
    get() = I18n.get(Strings.STATUS_CONFIG_FAILED, "配置读取失败，请重进页面")

/**
 * 首页那条常驻提示的第三种文案：**本机机型不在适配清单里**（见 `core/model/DeviceSupport`）。
 *
 *  这一种与前两种都不同：不是「等一等会好」，而是**这台机器上就是不能用**（模块只注入了界面层）。
 * 所以既不写「请稍候」也不写「重进页面」—— 那两句都会把人引去做没用的事。
 */
internal val UNSUPPORTED_DEVICE_HINT: String
    get() = I18n.get(Strings.STATUS_UNSUPPORTED_DEVICE, "此机型不适配")

/**
 * 首页那条常驻提示的第四种文案：**机型在清单里、但这一版系统没适配**
 *（见 `core/model/DeviceSupport` 的 `Support.PENDING`）。
 *
 *  与 [UNSUPPORTED_DEVICE_HINT] 分开：那一种是「等也没用」，这一种恰恰是「**只能等作者适配**」
 * —— 同一版 ROM 里那些私有类可能换了形，得重新验过接管点才敢注入（见 `docs/device-findings.md` 十九）。
 * 两句都短，用户扫一眼就知道该不该等。
 */
internal val PENDING_DEVICE_HINT: String
    get() = I18n.get(Strings.STATUS_PENDING_VERSION, "系统版本等待适配")

/**
 * 首页那条常驻提示的第五种文案：**主开关关着**（`KeyConfig.enabled = false`）。
 *
 *  这一档不是故障、也不该让人等：**页面整片灰就是它关着导致的**，说清这一点用户自己拨回去即可
 *（所以与 [MODULE_UNAVAILABLE_HINT] 分开 —— 那一句会让人以为要等）。主开关那一行本身不吃这个闸，
 * 拨得回来（见 [HomeScreen] 主开关那段注释）。
 */
internal val KEY_DISABLED_HINT: String
    get() = I18n.get(Strings.STATUS_DISABLED, "按键绑定已关闭")

/**
 * 「导入配置」校验没过时的那**一句**提示。
 *
 * 用户界面上只给结论、不点细节：不解释「哪一段坏了 / 第几条认不出」——那是日志的事
 *（见 `XLog`）。措辞与 [UNSUPPORTED_DEVICE_HINT] 同一档：说清「怎么办」，不说内部结构。
 */
internal val UNKNOWN_CONFIG_FORMAT: String
    get() = I18n.get(Strings.CONFIG_UNKNOWN_FORMAT, "未知的配置格式，请检查后重试")

/** 「风险提示与免责声明」那个弹窗的标题（首次启用、以及清空配置之后再启用时弹）。 */
internal val DISCLAIMER_TITLE: String
    get() = I18n.get(Strings.DIALOG_DISCLAIMER_TITLE, "风险提示与免责声明")

/**
 * 弹窗正文：一句风险、一句让用户自己拍板。
 *
 * 只说要发生什么，不写实现细节（注入哪个进程、靠什么 Hook）；「开不了机」是用户能懂的说法，
 * 真出事时该怎么救写在 README 里 —— 那一步不放在这里（出问题时打不开这个页面）。
 */
internal val DISCLAIMER_TEXT: String
    get() =
        I18n.get(
            Strings.DIALOG_DISCLAIMER_CONTENT,
            "本模块会将代码注入系统进程，属于非官方改动。可能出现：\n\n" +
                "- 按键失灵、功能异常\n" +
                "- 极端情况下无法开机\n\n" +
                "请确认你已知晓并**自行承担风险**。",
        )

/** 「温馨提醒」那个弹窗的标题 —— 免责声明**确认之后**接着弹（见 `ui/screen/HomeScreen`）。 */
internal val FREE_NOTICE_TITLE: String
    get() = I18n.get(Strings.DIALOG_FREE_NOTICE_TITLE, "温馨提醒")

/**
 * 「温馨提醒」的正文：模块免费、认准官方正版。
 *
 *  与上面那份免责声明**分开**：那一份讲「这件事有风险」，这一份讲「别为它付钱」——
 *  两件事，硬拼成一段会让人以为风险可以用钱解决。
 *  关键那两句加粗（正文认 `**…**` 记号，见 `ConfirmDialog` 的 `summary`）。
 */
internal val FREE_NOTICE_TEXT: String
    get() =
        I18n.get(
            Strings.DIALOG_FREE_NOTICE_CONTENT,
            "此模块**完全免费**，所有功能都不需要收费，请确认你使用的是**官方正版**。",
        )

/** 「自定义命令」那一页的风险提示标题（首次进那一页弹；不确认就退出那一页）。 */
internal val COMMAND_DISCLAIMER_TITLE: String
    get() = I18n.get(Strings.DIALOG_COMMAND_DISCLAIMER_TITLE, "自定义命令风险提示")

/**
 * 「自定义命令」那一份的正文 —— **自成一段**，不复用首页那份免责声明（[DISCLAIMER_TEXT]）。
 *
 *  两份说的是两件事：那一份讲的是「注入系统进程」这件事本身（极端情况开不了机那类），
 *  这一份讲的是「你写的命令不受任何约束」—— 把前者抄过来只会稀释后者，读起来像通用模板。
 *  风险承担那句话仍要自己说一遍，但用自己的措辞。
 *
 *  正文认 `**…**` 记号（`ConfirmDialog` 的 `summary` 走 [EmphasisText]）—— 关键那两句加粗。
 */
internal val COMMAND_DISCLAIMER_TEXT: String
    get() =
        I18n.get(
            Strings.DIALOG_COMMAND_DISCLAIMER_CONTENT,
            "自定义命令是**高度危险**的功能。\n\n" +
                "本模块**不会对的命令做任何检验或保护**。\n\n" +
                "请自行确认命令安全并承担相应后果。",
        )

/**
 * 「支持的命令格式」卡上的几行 —— 讲**命令输入框里能写什么**。
 *
 *  注意：**收录规则：只列「没有内置入口」的格式**。内置命令已经给了入口的那些
 *  不在这里重复教手写：`app:` / `window:` 有「打开应用」那一格（小窗是它菜单里的开关）、
 *  `zoom:mini[:collapse]` 有「应用转小窗」那两档 —— 同一个能力摆两个入口，手写那条还更容易写错。
 *  剩下的这几行是**没有图形入口**的：shell / keyevent / intent / shortcut / cmd。
 *
 *  点带 `prefix` 的那一行 = 把前缀**替换**进命令输入框（不是追加，见渲染点的注释）。
 */
internal val COMMAND_FORMAT_LINES: List<CommandFormatLine>
    get() =
        listOf(
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_DEFAULT_SHELL, "默认为 shell 命令")),
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_SHELL, "shell:<命令> —— 执行 shell 命令"), Action.SHELL_PREFIX),
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_KEYEVENT, "keyevent:<按键> —— 模拟按键"), Action.KEYEVENT_PREFIX),
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_INTENT, "intent:<Action> —— 发送 Intent"), Action.INTENT_PREFIX),
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_SHORTCUT, "shortcut:<ID> —— 触发快捷方式"), Action.SHORTCUT_PREFIX),
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_CMD, "cmd:<名字> —— 调用另一条单条命令"), Command.PREFIX),
        )

/**
 * 「单击 / 双击 / 长按」三个分段标题 —— 分段控件（`TabRowWithContour`）只收字符串。
 *
 *  [Gesture.label] 是枚举里的中文原文，注入侧与宿主侧也读它（那些地方不引界面层），
 *  所以译文在界面这一层过 —— 别去改枚举。
 */
internal fun gestureLabel(gesture: Gesture): String =
    when (gesture) {
        Gesture.SINGLE -> I18n.get(Strings.GESTURE_SINGLE, "单击")
        Gesture.DOUBLE -> I18n.get(Strings.GESTURE_DOUBLE, "双击")
        Gesture.LONG -> I18n.get(Strings.GESTURE_LONG, "长按")
    }

/** 「单条命令 / 多条命令」两个分段标题，理由同上。 */
internal fun commandKindLabel(kind: CommandKind): String =
    when (kind) {
        CommandKind.SINGLE -> I18n.get(Strings.COMMAND_TYPE_SINGLE, "单条命令")
        CommandKind.SERIAL -> I18n.get(Strings.COMMAND_TYPE_SERIAL, "多条命令")
    }

/**
 * 语言档位在界面上叫什么 —— 「语言」那一行的副标题与它那一层菜单共用这一份。
 *
 * 显示名只从这里出：[AppLanguage] 枚举上只留 `code`（那是原样进配置文件、注入侧与宿主侧也读它
 * 的字段），没有可以直接摆到界面上的文案字段。
 */
internal fun languageLabel(lang: AppLanguage): String =
    when (lang) {
        AppLanguage.AUTO -> I18n.get(Strings.MORE_LANGUAGE_AUTO, "跟随系统")
        AppLanguage.ZH_CN -> I18n.get(Strings.MORE_LANGUAGE_ZH, "简体中文")
        AppLanguage.EN -> I18n.get(Strings.MORE_LANGUAGE_EN, "English")
    }
