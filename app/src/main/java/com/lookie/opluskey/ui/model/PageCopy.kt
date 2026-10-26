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

/**
 * 「条件执行」页的标题 —— 与「按键绑定」页菜单里那一项**共用同一个键**
 * （`command.type_branch`）：点的那一行与进去之后看到的标题同名。
 */
internal val BRANCHES_TITLE: String
    get() = I18n.get(Strings.COMMAND_TYPE_BRANCH, "系统场景")

/**
 * 「通用设置」页的标题 —— 首页「其他」卡第一行进来（**唯一入口**，在「更多选项」上面）。
 *
 *  那一行与这一页顶上的标题**共用同一个键**：进去之后看到的与点的那一行同名。
 */
internal val GENERAL_TITLE: String
    get() = I18n.get(Strings.GENERAL_TITLE, "通用设置")

/**
 * 「组合按键」—— **列表页与编辑页共用这一个标题**（用户口径：顶栏不要「列表」两个字）。
 *
 *  两页看顶栏会觉得同名，但认得出在哪一层：编辑页顶栏底下一行副标题写着「在编哪一组」
 *  （`「侧键」+「电量 +」`），列表页没有；再往下还有一个短按 / 长按的分段控件。
 *  列表页卡上方那句小标题仍是「组合按键列表」（见 `ComboScreen`），说的是**那一列是什么**，
 *  与顶栏的「这一页叫什么」不是同一件事。
 */
internal val COMBO_TITLE: String
    get() = I18n.get(Strings.COMBO_TITLE, "组合按键")

/** 「选择应用」页的标题。 */
internal val APP_PICKER_TITLE: String
    get() = I18n.get(Strings.APP_PICKER_TITLE, "选择应用")

/**
 * 「选择界面」页的标题 —— 「选择应用」页上**点**某个应用进来（二级页）。
 *
 *  叫「应用组件」而不是「Activity」：这一页列的是「这个应用能打开的界面 + 快捷方式」，
 *  用户不需要知道 Activity 这个词（用户定稿口径）。
 */
internal val ACTIVITY_PICKER_TITLE: String
    get() = I18n.get(Strings.APP_PICKER_ACTIVITY_TITLE, "应用组件")

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
    get() = I18n.get(Strings.STATUS_UNAVAILABLE, "页面还没就绪，重进本页试试")

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
 * 首页那条常驻提示的第五种文案：**模块总闸关着**（`GlobalConfig.enabled = false`）。
 *
 *  这一档不是故障、也不该让人等：**页面整片灰就是它关着导致的**，说清这一点用户自己拨回去即可
 *（所以与 [MODULE_UNAVAILABLE_HINT] 分开 —— 那一句会让人以为要等）。主开关那一行不吃「整页闸」，
 * 拨得回来（见 [HomeCards] 主开关那段注释）。
 *
 *  **它排在链路的几档之后**（见 `blockedReason` 的顺序）：那一行自己吃 `linkReady`，链路没齐时
 *  它是灰的、拨不动 —— 先说这句等于让人去开一个开不了的开关。反过来，**这句能出现就说明链路是通的、
 *  那一行此刻可点**，所以「拨回去」这个建议永远是可执行的。
 *
 *  **逐键的停用不走这一条**：停用一颗键（含侧键）时页面照常可用，只是那一颗的控件变灰、
 *  点一下说「可在 ⋮ 菜单里启用」（见 `disabledClickReason`）。
 */
internal val KEY_DISABLED_HINT: String
    get() = I18n.get(Strings.STATUS_KEY_DISABLED, "按键绑定已关闭，侧键交还系统")

/**
 * 首页那条常驻提示的第六种文案：**宿主那一路服务没起来**（`link.hostServiceUp = false`）。
 *
 *  与「模块未启用」分开：这一档说明模块**在这个进程里是加载了的**（否则连提示都不该出现），
 *  只是宿主主进程没报活 —— 要么它被杀了，要么它那一侧的手势劫持没装上。用户能做的是重进页面 / 重启。
 */
internal val SERVICE_NOT_RUNNING_HINT: String
    get() = I18n.get(Strings.STATUS_SERVICE_NOT_RUNNING, "后台服务未运行")

/**
 * 首页那条常驻提示的第七种文案：**注入侧没回音** —— 两种来路，动作是同一句：
 * LSPosed 作用域里漏了 `system`，或者换过包但没整机重启（system_server 里那份还是旧代码）。
 *
 *  能走到这一档说明**模块本身是启用的**（否则界面根本替换不出来），所以措辞不写「装没装上」，
 *  只写这两件要做的事。
 */
internal val INJECT_NOT_LOADED_HINT: String
    get() =
        I18n.get(
            Strings.STATUS_DISABLED,
            "模块没生效：在 LSPosed 里给模块勾上「系统框架」和「手势体感」，改完重启手机",
        )

/**
 * 自检**还没问完**时那条提示 —— 这一小段（开机后几秒、或刚进页面那一下）此前什么都不显示。
 *
 *  为什么值得单独一句：什么都没显示时，用户看到的是「页面正常但点了没反应」；
 *  而这一档只是**还没问完**，不是故障 —— 一句中性的「正在检查」既不说谎，也解释了那几秒。
 *
 *  它不能排进「未生效」那一档：那会在每次进页面时闪一下假故障（`LinkFacts.injectChecked`
 *  就是为这件事加的）。
 */
internal val CHECKING_HINT: String
    get() = I18n.get(Strings.STATUS_CHECKING, "正在检查模块…")

/**
 * 「装了新版、系统侧还没用上」与「作用域里撤掉了系统框架」**共用**的一句话。
 *
 *  这两件事在模块眼里是同一组信号：本轮系统侧没回话、而后者的包又往往刚好是「这次开机之后才装的」。
 *  作用域只有框架自己知道（宿主与 system_server 都读不到 `/data/adb`），所以不硬猜是哪一种 ——
 *  一句话把两步都说了：先重启，重启后还见这句就去勾作用域。
 */
internal val PENDING_OR_SCOPE_HINT: String
    get() =
        I18n.get(
            Strings.STATUS_PENDING_OR_SCOPE,
            "模块已更新：重启手机后生效；若重启后仍见本行，请到 LSPosed 里确认「系统框架」已勾选",
        )

/**
 * 首页那条常驻提示的第十二种文案：**系统侧回着话，但它跑的是旧包**（进程比盘上这个包起得还早）。
 *
 *  与「系统侧压根没回话」那一档分工不同：那种除了重启没别的可能；
 *  这一档**界面已经在新版上了**（页面跑在刚装的那份代码里），只有系统侧还是旧的。
 *  文案只给动作（重启手机）：界面已生效这件事在这条提示里不必说，用户看的这一页就是它。
 */
internal val SYSTEM_STALE_HINT: String
    get() =
        I18n.get(
            Strings.STATUS_SYSTEM_STALE,
            "系统侧还是旧版，重启手机后生效",
        )

/**
 * 首页那条常驻提示的第十一种文案：**宿主侧跑着旧包** —— 那个进程比盘上这个包起得还早。
 *
 *  与「要重启手机」分开写，是因为它**不需要重启**：宿主是普通应用进程，重开就换新代码
 *  （`AGENTS.md` 五之一、6）。把它混进「重启手机」那一档，等于让人为一次装包多付一次重启。
 */
internal val HOST_STALE_HINT: String
    get() = I18n.get(Strings.STATUS_HOST_STALE, "后台服务还是旧版：用「重启服务」即可，不用重启手机")

/**
 * 「能用，只是这次没落到新代码上」那两档提醒的**身份** —— 去重比的是它，不是文案。
 *
 *  比文案的话「整趟进程只说一遍」这一位随时会失效：切一次语言，同一件事的两句话就变成两个
 *  字符串，`lastNoticeShown` 判不出「这条已经说过」，系统侧那条提醒于是又弹一遍。
 *
 *  [text] 是**取的时候现查**（不缓存）：语言切了以后同一档要跟着换成新的那句话。
 */
internal enum class LinkNotice {
    /** 系统侧跑着旧包：换过包但没重启。 */
    SystemStale,

    /** 宿主侧跑着旧包：它是普通应用进程，重开就好。 */
    HostStale,
    ;

    fun text(): String =
        when (this) {
            SystemStale -> SYSTEM_STALE_HINT
            HostStale -> HOST_STALE_HINT
        }
}

/**
 * 首页那条常驻提示的第九种文案：回包了、但**一个接管点都没装上** —— 这一版 ROM 把那些私有类改了形。
 *
 *  用户什么也做不了（只能等适配），所以不写任何「请重试」。
 */
internal val API_MISMATCH_HINT: String
    get() = I18n.get(Strings.STATUS_API_MISMATCH, "系统接口不匹配，等待适配")

/**
 * 首页那条常驻提示的第十种文案：**设置没同步到系统那一侧**（两边指纹对不上）。
 *
 *  这一档重进页面就会重发一遍配置，所以写的动作是「重进本页」，不是「等」。
 */
internal val CONFIG_NOT_SYNCED_HINT: String
    get() = I18n.get(Strings.STATUS_CONFIG_NOT_SYNCED, "设置未同步，重进本页即可")

/**
 * 「导入配置」校验没过时的那**一句**提示。
 *
 * 用户界面上只给结论、不点细节：不解释「哪一段坏了 / 第几条认不出」——那是日志的事
 *（见 `XLog`）。措辞说清「怎么办」，不说内部结构。
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
                "本模块**不会对编写的命令做任何检验或保护**。\n\n" +
                "请自行确认命令安全并承担相应后果。",
        )

/**
 * 「支持的命令格式」卡上的几行 —— 讲**命令输入框里能写什么**。
 *
 *  注意：**收录规则：只列「没有内置入口」的格式**。内置命令已经给了入口的那些
 *  不在这里重复教手写：`app:` / `window:` 有「打开应用」那一格（小窗是它菜单里的开关）、
 *  `zoom:mini[:collapse]` 有「前台转小窗」那两档 —— 同一个能力摆两个入口，手写那条还更容易写错。
 *  剩下的这几行是**没有图形入口**的：shell / keyevent / intent / shortcut / cmd。
 *
 *  点带 `prefix` 的那一行 = 把前缀**替换**进命令输入框（不是追加，见渲染点的注释）。
 */
internal val COMMAND_FORMAT_LINES: List<CommandFormatLine>
    get() =
        listOf(
            //  **没有「默认为 shell 命令」那一行了**：现在命令必须显式带前缀（用户口径
            //  「去掉默认为 shell 和 cmd:，对命令详情限制，必须以支持的格式开头」）。
            //  这一列就是「能写什么」，每行都可点、把前缀整段填进输入框。
            //  左列写法 / 右列说明分成两条键取 —— 卡片排的是两列，各自要能单独对齐。
            CommandFormatLine(
                label = I18n.get(Strings.COMMAND_FORMAT_SHELL, "shell:<命令>"),
                desc = I18n.get(Strings.COMMAND_FORMAT_SHELL_DESC, "执行 shell 命令"),
                prefix = Action.SHELL_PREFIX,
            ),
            CommandFormatLine(
                label = I18n.get(Strings.COMMAND_FORMAT_KEYEVENT, "keyevent:<按键>"),
                desc = I18n.get(Strings.COMMAND_FORMAT_KEYEVENT_DESC, "模拟按键"),
                prefix = Action.KEYEVENT_PREFIX,
            ),
            CommandFormatLine(
                label = I18n.get(Strings.COMMAND_FORMAT_INTENT, "intent:<Action>"),
                desc = I18n.get(Strings.COMMAND_FORMAT_INTENT_DESC, "发送 Intent"),
                prefix = Action.INTENT_PREFIX,
            ),
            CommandFormatLine(
                label = I18n.get(Strings.COMMAND_FORMAT_SHORTCUT, "shortcut:<ID>"),
                desc = I18n.get(Strings.COMMAND_FORMAT_SHORTCUT_DESC, "触发快捷方式"),
                prefix = Action.SHORTCUT_PREFIX,
            ),
            CommandFormatLine(
                label = I18n.get(Strings.COMMAND_FORMAT_CMD, "cmd:<名字>"),
                desc = I18n.get(Strings.COMMAND_FORMAT_CMD_DESC, "调用另一条单条命令"),
                prefix = Command.PREFIX,
            ),
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

/** 两个分段标题（单条 / 多条），理由同上。 */
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

/**
 * 系统配色在界面上叫什么 —— 「系统配色」那一行的副标题与它那一层菜单共用这一份。
 *
 *  一套色板在界面上就是一枚颜色，没有别的可写：菜单里只能靠名字区分，所以五个名字都得给。
 *  与 [languageLabel] 同一个理由：[PagePalette] 上只留 `id` 与种子色（`id` 原样进配置文件）。
 */
internal fun paletteLabel(palette: PagePalette): String =
    when (palette) {
        PagePalette.DEFAULT -> I18n.get(Strings.MORE_PALETTE_BLUE, "蓝色")
        PagePalette.GREEN -> I18n.get(Strings.MORE_PALETTE_GREEN, "绿色")
        PagePalette.ORANGE -> I18n.get(Strings.MORE_PALETTE_ORANGE, "橙色")
        PagePalette.PURPLE -> I18n.get(Strings.MORE_PALETTE_PURPLE, "紫色")
        PagePalette.PINK -> I18n.get(Strings.MORE_PALETTE_PINK, "玫红色")
    }

/**
 * 界面颜色那三档在界面上叫什么 —— 「界面颜色」那一行的副标题与它那一层菜单共用这一份。
 *
 *  与 [languageLabel] 同一个理由：[AppTheme] 上只留 `code`（原样进配置文件）。
 */
internal fun themeLabel(theme: AppTheme): String =
    when (theme) {
        AppTheme.AUTO -> I18n.get(Strings.MORE_THEME_AUTO, "跟随系统")
        AppTheme.LIGHT -> I18n.get(Strings.MORE_THEME_LIGHT, "浅色")
        AppTheme.DARK -> I18n.get(Strings.MORE_THEME_DARK, "深色")
    }

/**
 * 键名外面那对括号 —— 中文用对角方括号（`「」`，与停用提示「「侧键」已停用…」同一对），
 * 英文用弯引号（`“”`，正是英文停用提示里那一对）。同一句话在两种语言里各自像本地写法。
 *
 *  全项目只有这一处：条件执行的空态、组合键列表那一行与顶栏副标题都读它 ——
 *  同一个「一颗键的名字」在三处不该有三种长相。
 */
internal fun keyBrackets(): Pair<String, String> = if (I18n.effectiveLanguage == AppLanguage.ZH_CN) "「" to "」" else "“" to "”"
