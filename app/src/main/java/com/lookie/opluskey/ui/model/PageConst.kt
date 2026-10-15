package com.lookie.opluskey.ui.model

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Gesture
import io.github.suqi8.coui.kmp.basic.FloatingToolbarDefaults
import io.github.suqi8.coui.kmp.basic.SliderDefaults
import io.github.suqi8.coui.kmp.nav.transition.NavMotion
import io.github.suqi8.coui.kmp.nav.transition.NavSettleSpec
import io.github.suqi8.coui.kmp.nav.transition.NavTransition
import io.github.suqi8.coui.kmp.nav.transition.NavTransitions
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme

/**
 * 页面共享常量（尺寸、文案、动画时长、色板）。
 */

internal const val STEP_MS = 50

/**
 * 顶栏两颗图标按钮（返回 / ⋮）的触达区边长 —— 圆角取它的一半（= 圆形）。
 *
 * 库 `IconButton` 的按压高亮是画在 `defaultMinSize` 那一层的 squircle，所以高亮圈多大完全由这两个数决定
 * （库默认 48dp / 圆角 24dp）。
 */
internal val HEADER_ICON_SIZE = 40.dp

/**
 * 磨砂浮层的模糊半径（dp，给 `blur(radiusX, radiusY)` 之前换算成 px）—— 顶栏与浮层菜单共用这一个值。
 *
 * 取 24dp：顶栏高 107dp，再大把底下滚过的文字糊成一片，再小又和没糊差不多。
 */
internal val FROSTED_BLUR_RADIUS = 24.dp

/**
 * 磨砂面上给不透明底色降 alpha 用的那一档（顶栏 + 底部工具条）。
 *
 *  注意：右下角那颗 FAB **不参与这一档** —— 它把底色调成**主题原色**（`colorScheme.primary`），
 *  乘这一档会掺进底下的糊里、显得发白，与主题色对不上。别再照着顶栏 / 工具条给它乘回去。
 *
 *  一律**按比例缩**（`color.alpha * 这一档`），不要 `copy(alpha = …)` 整个换 —— 深浅两套色板里
 *  同名色可能一个是实色、一个是半透（库深色板的 `surfaceContainer` 就是半透白），替换会把后者顶成亮色。
 */
internal const val FROSTED_SURFACE_ALPHA = 0.67f

/**
 * 浮层菜单面板的圆角 —— 库 `ListPopupContent` 的 `PopupCornerRadius`（12dp，对应 COUI
 * `couiRoundCornerM`），库内是 internal，这里照抄一份只为**磨砂取样收边**：那一层圆角**不裁子级**，
 * 不自己收边的话糊出来的方块会盖过面板的圆角。
 */
internal val POPUP_PANEL_RADIUS = 12.dp

/**
 * 浮层面板的本色 —— 浅色照搬库 `ListPopupContent` 那一对常量（COUI 的 `couiColorSurfaceTop`：
 * `coui_color_surface_top` 浅色 `#FFFFFF` / 夜间 `#333333`，库内是 private，只能照抄一份）；
 * 夜间那一档见 [POPUP_SURFACE_DARK]（比库原生的亮，与 [POPUP_FROSTED_ALPHA] 配套）。
 *
 *  为什么不能拿主题里那几档 `surface*` 顶替：面板要**比页面亮一档**——暗色下页面是纯黑
 *（实测 `#000000`）、卡片是 `#1a1a1a`，而 `surfaceContainerHighest` 只有 `#252525`，
 *  拿它当磨砂底色会比库原生面板还暗（真机反馈「暗色下底色太暗」）。
 */
internal val POPUP_SURFACE_LIGHT = Color.White

/**
 * [POPUP_SURFACE_LIGHT] 的夜间那一档 —— `#333333` 是库原生的值，这里取到 `#3d3d3d`、
 *  **亮一档**：面板底色降到半透（见 [POPUP_FROSTED_ALPHA]）之后，底下的糊会透过一部分，
 *  本色不抬这一档，落定后的面板就比库原生面板暗（真机反馈过「暗色下底色太暗」）。
 *  两笔账合起来是把**落定亮度**钉在库原生那一档，透上来的糊才有得看。
 */
internal val POPUP_SURFACE_DARK = Color(0xFF3D3D3D)

/**
 * 浮层磨砂底色的 alpha（**只给浮层菜单用**）—— 这一档就是「底下的糊能透上来多少」：
 *  越小糊越明显。它是**和 [POPUP_SURFACE_DARK] 一起调的**：糊的是底下的页面，暗色下页面接近纯黑，
 *  透得越多面板越黑 —— 所以本色抬亮一档，把落定亮度钉在库原生面板（`#333333`）那一档上。
 *  0.72 是比顶栏（[FROSTED_SURFACE_ALPHA]）略保一点、又明显看得见糊的位置；顶栏可以更低，
 *  是因为它背后恒是页面自己的滚动内容，而浮层可能压在任意画面上。
 *
 *  消费方只有浮层菜单（`ui/component/MenuPopup.kt` 里 `MenuPopup` 那层）。确认弹窗的面板走**另一档**
 *  [DIALOG_FROSTED_ALPHA]：同一门账，只是它面板大、底下更平，要更透才看得见糊。
 */
internal const val POPUP_FROSTED_ALPHA = 0.72f

/**
 * 确认弹窗面板底色的 alpha —— 比浮层菜单那一档（[POPUP_FROSTED_ALPHA]）**更透**。
 *
 *  这一档就是「底下的糊能透上来多少」：0.72 时糊只占 28%，而弹窗面板比菜单大得多、底下的页面又平
 *（一张卡片加一片空白），同一档看下来只剩一层底色、看不出背后糊过（真机反馈「生效了但不太明显」）。
 *  0.6 把糊的占比提到 40%，面板底下是糊过的内容一眼能认出来；再低正文压在花底上就发虚，到头了。
 *
 *  底色本色与浮层菜单共用（[popupSurfaceColor]），只有这一档分家。
 */
internal const val DIALOG_FROSTED_ALPHA = 0.6f

/**
 * [SliderRow] 那一行的几何契约 ——「点轨道跳值」（[tapSliderToSeek]）按这几笔账圈出滑杆占的那条带子：
 * [SLIDER_SIDE_INSET] / [SLIDER_BOTTOM_INSET] 是行内容的内边距（`insideMargin`），
 * [SLIDER_ICON_INSET] 是行首图标额外挤开的那一段。
 */
internal val SLIDER_SIDE_INSET = 16.dp
internal val SLIDER_BOTTOM_INSET = 10.dp

/**
 * 行首图标把滑杆挤开的距离 —— 图标本身 22dp + 库在图标与标题之间固定的那 8dp（`Component.kt` 的 `spacerPx`）。
 *
 * [SliderRow] 的滑杆按它左内缩，于是滑杆与标题文字同一个左边界；「点轨道跳值」的账里也要带上它。
 */
internal val SLIDER_ICON_INSET = 30.dp

internal val SLIDER_INSIDE_MARGIN =
    PaddingValues(
        horizontal = SLIDER_SIDE_INSET,
        vertical = SLIDER_BOTTOM_INSET,
    )

/** 卡片组首尾行在 `BasicComponent` 里额外补的上下边距（`coui_list_card_head_or_tail_padding`）。 */
internal val CARD_EDGE_PADDING = 2.dp

/** 「点轨道跳值」认作滑杆的那条带子的高度。 */
internal val SLIDER_TAP_ZONE = SliderDefaults.MinHeight + SLIDER_BOTTOM_INSET + CARD_EDGE_PADDING

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
 * 语言档位在界面上叫什么 —— 「语言」那一行的副标题与它那一层菜单共用这一份
 *（[AppLanguage.label] 是枚举里的中文原文）。
 */
internal fun languageLabel(lang: AppLanguage): String =
    when (lang) {
        AppLanguage.AUTO -> I18n.get(Strings.MORE_LANGUAGE_AUTO, "跟随系统")
        AppLanguage.ZH_CN -> I18n.get(Strings.MORE_LANGUAGE_ZH, "简体中文")
        AppLanguage.EN -> I18n.get(Strings.MORE_LANGUAGE_EN, "English")
    }

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

/**
 * 「添加命令」选择器浮层的最大高度 —— 取 320dp。
 *
 * COUI 的 `dropdownPositionProvider` 是「下方放不下翻到上方，两边都放不下就居中」，居中会把浮层压到屏幕中间、盖住锚点；
 * 320dp 下两种情形都能翻到锚点某一侧。行高 48dp + 组标题 32dp ⇒ 一屏约 6~7 行，其余滚动（`ListPopupColumn` 自带滚动）。
 */
internal val STEP_PICKER_MAX_HEIGHT = 320.dp

/**
 * 「按键行为」网格的列数 —— 取 2：机身 363dp 宽下每格 ≈163dp（焦点圈不占宽度），14sp 放得下最长的一行；
 * 16sp（库 `title` 槽位写死那档）会被省略号截掉，所以 [ActionTile] 用 content 重载自己放 `Text`。
 */
internal const val COLUMNS = 2

/**
 * 「快捷键」首页「按键行为」卡的行数 —— 两行（振动反馈 + 执行时提示）。
 *
 * 行不再随状态增减，所以写成常量而不是 `buildList`。
 */
internal const val HOME_ACTION_ROWS = 2

/** 「更多选项」页「关于」那张卡的行数 —— 本机 / 版本 / 作者 / 反馈问题，四行固定。 */
internal const val ABOUT_ROWS = 4

/**
 * 「更多选项」页「配置」那张卡的行数 —— 语言 / 导出配置 / 导入配置，三行固定。
 *
 * 与 [ABOUT_ROWS] 分开：两张卡各算各的圆角（`cardListPositionOf` 的首尾各补 2dp），
 * 合成一个数会让上面那张卡的最后一行被当成「中间行」。
 */
internal const val MORE_CONFIG_ROWS = 3

/**
 * 首页「其他」卡的行数 —— 只有「更多选项」一行。
 *
 * 与 [HOME_ACTION_ROWS] 分开同样是圆角账：两张卡的首尾各自算。
 */
internal const val HOME_MORE_ROWS = 1

/**
 * 「已保存的命令」卡片里图标到标题的间距 —— 卡片高度至少等于宽度、步骤多时会跟着长高，
 * 所以这里只是排版间距，不承担纵向预算。
 */
internal val TILE_TITLE_GAP = 8.dp

/**
 * 「已保存的命令」卡片里，摘要行**每一步**行首小图标的边长 —— 比步骤行的
 * [INPUT_ICON_SIZE]（22dp）小一档，它跟着的是摘要那行小字（`footnote1`）。
 */
internal val SUMMARY_REF_ICON = 14.dp

/** 那枚小图标与它后面那行摘要之间的间距。 */
internal val SUMMARY_REF_GAP = 4.dp

/**
 * 首页问一句「宿主侧的模块在不在」之后，等回音的时长（毫秒）。
 *
 * 没有回音就当模块没加载（整页禁用 + 一条提示）。给得偏宽，是因为这一步可能要等**宿主进程
 * 被这条广播拉起来**：进程启动、LSPosed 注入、模块注册接收器，都在这段时间里。
 */
internal const val HOST_ALIVE_TIMEOUT_MS = 3000L

/**
 * 首页最多问几轮「两端在不在」。
 *
 * 一轮只够发现「早就加载好了」那一档；注入侧借不到 Context、或它的宿主 uid 还没解析出来时，
 * 它会 fail-closed 把那句问句丢掉（见 `KeyHooks.isFromHost`），而这**会自愈** —— 只是不是立刻。
 * 所以要多问几轮，免得用户看着一个灰页面无从下手。
 *
 *  两端都答上就提前收工（正常机器上仍然只问一轮）；一直问不齐也会停：
 * 适配清单外的机器本来就该整页禁用，无限轮询只是白耗电。
 */
internal const val QUERY_ROUNDS = 3

/**
 * 「查看地址」行点开的目标 —— 本仓库的线上地址（`AboutScreen` 那行的 `summary` 与 `onClick` 都直接引它）。
 */
internal const val REPO_URL = "https://github.com/lo0kie/OPlusKey-lsposed"

/** 「作者」行的值 —— 与仓库所有者同名。 */
internal const val AUTHOR = "lo0kie"

/** 「作者」行点开的目标 —— 作者的 GitHub 主页。 */
internal const val PROFILE_URL = "https://github.com/$AUTHOR"

/**
 * 「反馈问题」行点开的目标 —— 本仓库的 issue 列表（`AboutScreen` 的 `summary` 与 `onClick` 都直接引它）。
 */
internal const val ISSUE_URL = "$REPO_URL/issues"

/**
 * 「按键绑定」那张卡的行数：第 1 行主开关、第 2 行进子页 —— 主开关是独立一行，不挂在跳转行行尾。
 *
 * 行数不再随状态变，所以写成常量。
 */
internal const val HOME_BINDING_ROWS = 2

/**
 * 页面左右边距 —— 取 12dp，网格列距与分段控件共用同一值。
 *
 * 不用 16dp：网格那边 16dp 撑不下（机身 363dp 宽，中间槽位只剩 ~63dp，而最长的一项「锁屏 / 电源」
 * 在 14sp 下要 ~70dp）。
 */
internal val PAGE_MARGIN = 12.dp

/**
 * 各页内容区底部 padding —— 统一取 32dp（各页不统一的话翻页时会看出来）。
 */
internal val PAGE_BOTTOM_PADDING = 32.dp

/**
 * 内容区距顶栏底边的那一档 —— 8dp。
 *
 * 补不补看首行是什么：首行是卡片的那一页（「选择应用」）要补一档，因为它没有
 * `SmallTitle` 自带的上内边距；首行是 `SmallTitle` 的那几页不补。两条固定带只带下边这一档。
 */
internal val PAGE_TOP_PADDING = 8.dp

/**
 * 网格格子里文字的视觉居中微调 —— 往下挪 1.5dp。
 *
 * 文字布局盒本身居中，但汉字字形墨迹在行盒里偏上（行盒要给基线以下留 descent，汉字几乎不占），
 * 真机量到视觉上高约 1.6dp。用 `offset` 而非 `padding`：只改绘制位置、不改测量。
 */
internal val TILE_TEXT_OPTICAL_SHIFT = 1.5.dp

/**
 * 网格格子标题**右边**那枚小标记（现在只有「打开应用」格的「小窗起」用它）的边长 —— 取 16dp。
 *
 * 它跟标题同一行，尺寸按 `body2`（14sp）的行高取；再大会把标题挤到省略号
 *（格子两列、正文那一栏本来就窄，见 `TILE_SIDE_PADDING` 的宽度账）。
 */
internal val TILE_TITLE_MARKER_SIZE = 16.dp

/** 上面那枚标记与标题之间的间距 —— 取 4dp，与 [SUMMARY_REF_GAP] 同档。 */
internal val TILE_TITLE_MARKER_GAP = 4.dp

/**
 * 首页那条链路提示（`SnackbarHost`）**内容盒外面**要留出的、给胶囊那圈影子落下的空间。
 *
 * ## 为什么必须留（真机量出来的）
 *
 *  库的 `SnackbarHost` 是「内容多高就多高」的 `LazyColumn`（内部 `reverseLayout`），
 *  items 的**外面那层盒子就是它的裁切边界** —— 影子的可见范围被卡在
 *  「胶囊 ∓ 库自己给的内边距」那一圈上。库给的是 `padding(horizontal = 16.dp, vertical = 4.dp)`，
 *  所以影子横向只画得出 16dp、**纵向往上只画得出 4dp**，而胶囊那圈投影是
 *  `Shadow(elevation = 26.dp, offset = (0, 4.dp))` —— 往上要 ~22dp。差的那一截就在
 *  胶囊顶上方 4dp 处被**齐平切断**（真机取样：断口上下差 10 个灰阶，一眼看得见那道直边）。
 *  底下为什么没事：库给 `LazyColumn` 的 `contentPadding` 底部留了 28dp，
 *  往下有 4 + 28 = 32dp 的地方，正好够 ~30dp 的溢出。
 *
 * ## 为什么补在 item 里，而不是给 `SnackbarHost` 加 `padding`
 *
 *  加在 `SnackbarHost` 上是从**视窗里往外抠**：那个视窗跟着内容一起缩，越抠越没地方
 *  —— 试过（补 16dp），结果切痕跟着胶囊一起上移，一点没修好。
 *  只有撑大 **item 自己**（走 `SnackbarHost` 的 `content` 槽位）才会把裁切边界一起撑出去。
 *
 * 取 20dp：往上要 ~22dp、库已经给了 4dp，补到 24dp 有富余；横向补到 36dp（要 ~26dp）；
 * 往下 20 + 4 + 28 = 52dp（要 ~30dp）。四周都够，且 item 总宽还放得下那行提示。
 */
internal val SNACKBAR_SHADOW_ROOM = 20.dp

/**
 * 「自定义命令」页底部操作工具栏（[CommandActions]）距屏幕底边的距离 —— 基准取 [PAGE_BOTTOM_PADDING] × 2（64dp），
 * 与「按键绑定」页那颗 FAB 的下边距同一基准。
 *
 * `FloatingToolbar` 内部自带一圈 `OutSidePadding`（上下 8dp、左右 12dp），`padding` 加在它外面 ⇒
 * 减掉那一圈，胶囊底边才与 FAB 齐平。
 */
internal val COMMAND_TOOLBAR_BOTTOM: Dp =
    PAGE_BOTTOM_PADDING * 2 - FloatingToolbarDefaults.OutSidePadding.calculateBottomPadding()

/** 工具栏里那两颗图标按钮（[CommandActions]）的边长 —— 40dp，与顶栏那两颗（[HEADER_ICON_SIZE]）同款。 */
internal val TOOLBAR_ICON_SIZE = 40.dp

/**
 * 工具栏（[CommandActions]）里按钮到胶囊边缘的留白与按钮之间的间距 —— 取同一个值，
 * `Row` 的 `padding` 与 `spacedBy` 都用它。
 */
internal val TOOLBAR_EDGE_GAP = 6.dp

/**
 * 「选择应用」页列表头部占的项数 —— 1（「顶栏让位 + 固定搜索带」那一项）。
 *
 * 任何按下标的滚动（进场定位到已选应用）都要加上这个偏移。
 */
internal const val APP_LIST_HEADER_ITEMS = 1

/**
 * 「打开应用」页每行定死的高度 —— 只为让行首那枚应用图标能整高。
 *
 * 图标要 `fillMaxHeight().aspectRatio(1f)` 才跟着行高走，而 `fillMaxHeight()` 在无界约束下退化成 0
 * （`LazyColumn` 的项无界）⇒ 行高必须先定死。64dp 是这一行自然高度向上取整；代价是系统字号调大时行不会变高。
 */
internal val APP_ROW_HEIGHT = 64.dp

/** 应用图标位图的取样边长 —— 48dp，比实际渲染的 40dp 大一档，缩下来才清晰。 */
internal val APP_ICON_BITMAP_SIZE = 48.dp

/**
 * 「往下滚就藏、往上滚就露」判据的方向翻转门槛 —— 取 0.5dp（真机 560dpi 上是 2px）。
 *
 * 不设门槛时手指轻抖就会把 FAB 翻得一闪一闪，门槛太大又会让慢速拖动判不出来。
 */
internal val FAB_SCROLL_DIRECTION_THRESHOLD = 0.5.dp

/**
 * 「选择应用」页那两处状态图的边长 —— 32dp（加载转圈与空态大图标同档，两者不同框）。
 *
 * 转圈不能只改 `size`：库按 26dp 画布推出来的三个数（ring 24.14 / stroke 1.857）得按同一比例一起放大，
 * 只放大画布等于只多留白、圈没变大。
 */
internal val APP_STATE_ICON_SIZE = 32.dp

/** 那两处状态图与下面那句话之间的间距 —— 12dp，三种形态共用。 */
internal val APP_STATE_TEXT_GAP = 12.dp

/**
 * 应用名右边那枚「模块 / 系统 / 用户」小标签（[AppKindTag]）的间距与外观。
 *
 * 底色走**文字色的低透明度**，所以颜色只有一处来源：三档各给一个色（见 `appKindTagTint`），
 * 用户 = 中性、系统 = 主题蓝、模块 = 下面那对紫。
 */
internal val APP_KIND_TAG_GAP = 6.dp
internal val APP_KIND_TAG_RADIUS = 4.dp
internal val APP_KIND_TAG_INSIDE = PaddingValues(horizontal = 5.dp, vertical = 1.dp)
internal const val APP_KIND_TAG_BG_ALPHA = 0.10f

/**
 * 应用种类标签里「模块」那一档的颜色 —— 紫，两套主题各一值。
 *
 *  为什么不取库的色板：COUI 那套只有蓝（`primary`）与红（`error`）两个色相 —— 蓝给了「系统」、
 *  红是「不可打开」的警告档，第三个色相只能自己定一份。判据同 `popupSurfaceColor`（按 `background`
 *  亮度切），两档都取中等亮度：标签底是它自己的 10% 透明版，太深太浅都压不住。
 */
internal val APP_KIND_MODULE_LIGHT = Color(0xFF7C4DFF)
internal val APP_KIND_MODULE_DARK = Color(0xFFB9A3FF)

/**
 * 小标签相对「与大标题盒子居中」再往上抬的这一档 —— 0.5dp。
 *
 * COUI 的 `LineHeightStyle` 是 `Top` + `Trim.None`，多出来的行距全落在字面下面 ⇒ 与行盒居中的标签会被推下去，
 * 抬回 0.5dp 两个字形中线才重合。
 */
internal val APP_KIND_TAG_RAISE = 0.5.dp

/**
 * 应用行里标题与摘要之间的间距 —— 2dp，与库 `support_preference_margin_between_line` 同值；
 * 这一页标题行要自己排（往名字右边塞标签），所以这个数自己写一份。
 */
internal val APP_TITLE_SUMMARY_GAP = 2.dp

/**
 * 网格的横向间距：页面边距与列距共用同一个值（[PAGE_MARGIN]）；行距另见 [GRID_ROW_GAP]。
 * 焦点圈画在格子之上、不占空间，格子占满分配宽度，不需要留补偿。
 */
internal val GRID_GAP = PAGE_MARGIN

/** 网格的行距 —— 与横向间距 [GRID_GAP] 同值（改了一边另一边跟着变）。 */
internal val GRID_ROW_GAP = GRID_GAP

/** 全局统一颜色过渡曲线与时长（200ms 柔和减速，供全仓状态颜色切换）。 */
internal val COLOR_TRANSITION_SPEC =
    tween<Color>(
        durationMillis = 200,
        easing = LinearOutSlowInEasing,
    )

/** 进场减速曲线：初段轻快响应、末段平滑收敛（ColorOS 系统标定减速曲线）。 */
internal val EASING_EMPHASIZED_DECEL = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** 出场与收缩曲线：起步即时响应、全程平顺收拢，消灭初段停滞与末段急坠感。 */
internal val EASING_EMPHASIZED_ACCEL = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/** 动态显隐控件进场时长（展开）。 */
internal const val VISIBILITY_ENTER_MS = 260

/** 动态显隐控件出场时长（收起）。 */
internal const val VISIBILITY_EXIT_MS = 200

/**
 * 命令页「单条 / 多条」两栏切换的时长。
 *
 *  与上面那两档分开：Tab 翻页是**整块内容的淡入淡出 + 分页器尺寸动画**，不是「控件从有到无的
 *  展开 / 收缩」。共用一个数会让「调显隐时长」顺手改掉翻页观感，两件事本来各量各的。
 */
internal const val TAB_PAGE_SWITCH_MS = 260

/** 动态显隐统一进场：淡入 + 纵向柔和展开（对齐顶边缘向下推开）。 */
internal val VISIBILITY_ENTER: EnterTransition =
    fadeIn(tween(180, easing = LinearOutSlowInEasing)) +
        expandVertically(
            animationSpec = tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL),
            expandFrom = Alignment.Top,
        )

/** 统一出场：先快淡出（避免文字被截断挤压）+ 纵向利落收缩。 */
internal val VISIBILITY_EXIT: ExitTransition =
    fadeOut(tween(120, easing = FastOutLinearInEasing)) +
        shrinkVertically(
            animationSpec = tween(VISIBILITY_EXIT_MS, easing = EASING_EMPHASIZED_ACCEL),
            shrinkTowards = Alignment.Top,
        )

/**
 * 多条命令列表步骤专属进场动效：
 * 展开高度 + 顶部轻微位移浮入 + 淡入，带来极其自然的“新步骤插入槽位”物理实感。
 */
internal val STEP_ROW_ENTER: EnterTransition =
    fadeIn(tween(200, easing = LinearOutSlowInEasing)) +
        expandVertically(
            animationSpec = tween(280, easing = EASING_EMPHASIZED_DECEL),
            expandFrom = Alignment.Top,
        ) +
        slideInVertically(
            animationSpec = tween(280, easing = EASING_EMPHASIZED_DECEL),
        ) { -it / 3 }

/**
 * 多条命令列表步骤专属退场动效：
 * 文字先隐去 + 轻微位移离场 + 高度平滑收缩，使得后续项与底部添加行丝滑吸附上移。
 */
internal val STEP_ROW_EXIT: ExitTransition =
    fadeOut(tween(120, easing = FastOutLinearInEasing)) +
        shrinkVertically(
            animationSpec = tween(220, easing = EASING_EMPHASIZED_ACCEL),
            shrinkTowards = Alignment.Top,
        ) +
        slideOutVertically(
            animationSpec = tween(220, easing = EASING_EMPHASIZED_ACCEL),
        ) { -it / 4 }

/**
 * 「支持的命令格式」卡里的一行。
 *
 * @param text 整行文案
 * @param prefix 行尾那颗小 `+` 往「命令」输入框里填的前缀（`shell:` / `keyevent:` …）；
 *   `null` = 这一行没有可填的前缀，行尾不画 `+`。
 */
internal data class CommandFormatLine(
    val text: String,
    val prefix: String? = null,
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
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_SHELL, "shell:<命令> —— 执行 shell 命令"), "shell:"),
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_KEYEVENT, "keyevent:<按键> —— 模拟按键"), "keyevent:"),
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_INTENT, "intent:<Action> —— 发送 Intent"), "intent:"),
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_SHORTCUT, "shortcut:<ID> —— 触发快捷方式"), "shortcut:"),
            CommandFormatLine(I18n.get(Strings.COMMAND_FORMAT_CMD, "cmd:<名字> —— 调用另一条单条命令"), "cmd:"),
        )

/**
 * 「按键绑定」主开关为关时分段控件的不透明度 —— 0.4。
 *
 * 只有分段控件需要这一手：它没有 `enabled` 参数；网格格子 / 长按卡都走 COUI 自己的
 * `enabled = false` 通道（用 `disabledColor`，不是把颜色整体调淡）。
 */
internal const val DISABLED_ALPHA = 0.4f

/** COUI 的浅色 / 深色色板（值来自库，写死在这里只是为了不必每次重建）。 */
internal val LIGHT_SCHEME = lightColorScheme()
internal val DARK_SCHEME = darkColorScheme()

/**
 * 栈顶之下那一页的压暗程度 —— 取 0.25（黑的 alpha）。
 *
 * 库在被盖页底下垫一层全屏黑，alpha = [PAGE_COVER_DIM] × 该页相对深度，静止时恒定压暗、推入渐深、弹出渐亮；
 * 再小压不住，再大暗得发灰。
 */
internal const val PAGE_COVER_DIM = 0.25f

/**
 * 整页转场（push / pop）的时长 —— 350ms（真机录屏逐帧拟合定下来的，不跟库默认的 500ms 走）。
 */
internal const val PAGE_TRANSITION_MS = 350

/**
 * 手势提交 / 取消那两档弹簧的刚度 —— 293f（按收敛包络反推：350ms ⇒ k = (5.9915/0.35)² ≈ 293）。
 *
 * 带速度的 settle 不吃 `programmatic` 曲线、退回弹簧，刚度过低会让「点返回 350ms、手势松手 ~500ms」；阻尼仍是临界。
 */
internal const val PAGE_SETTLE_STIFFNESS = 293f

/**
 * 整页转场 —— 几何直接用库的 [NavTransitions.COUIDefault]，只把 settle 换成真机核对过的规格。
 *
 * `COUIDefault` 的 `relativeDepth -> translationX` 是「d ≤ 0（顶层之上）整幅 -d·width，d > 0（被盖页）视差 -d·width/4」，
 * 并把 d 钳在 `[-1, 0]`、位移吸附整像素。settle 不用库默认的 500ms + `NavSettleEasing`，
 * 改用 350ms + `CubicBezierEasing(0.2f, 0f, 0f, 1f)`；手势的两档见 [PAGE_SETTLE_STIFFNESS]。
 */
internal val PAGE_TRANSITION: NavTransition by lazy {
    object : NavTransition by NavTransitions.COUIDefault {
        override val motion: NavMotion =
            NavMotion(
                programmatic =
                    NavSettleSpec.Tween(
                        durationMillis = PAGE_TRANSITION_MS,
                        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
                    ),
                //  手势提交 / 取消必须是弹簧：带速度的 settle 不吃 programmatic 曲线（Tween 带不上速度，库退回默认弹簧 ≈500ms）；
                // 刚度抬到 [PAGE_SETTLE_STIFFNESS]，让点返回与手势松手都收敛在 ~350ms。
                commit = NavSettleSpec.Spring(stiffness = PAGE_SETTLE_STIFFNESS),
                cancel = NavSettleSpec.Spring(stiffness = PAGE_SETTLE_STIFFNESS),
            )
    }
}

/**
 * 浮层菜单每项图标的尺寸 —— 取 20dp。
 *
 * 库的规格 24dp（`DropdownDefaults.IconMinSize`）是给两行菜单项配的，这里两行都是单行 16sp 正文，24dp 比字还高。
 * 库没有「每项图标尺寸」参数，只能从 `DropdownItem.icon` 的 `(Modifier) -> Unit` 槽位补（见 [ResetMenu]）。
 */
internal val MENU_ICON_SIZE = 20.dp

/**
 * 首页「按键绑定」那行行尾箭头（[PageIcons.EnterArrow]）的尺寸 —— 取 24dp，
 * 与库那颗 `COUIIcons.Basic.ArrowRight` 同为 24dp 下折线占 12dp 的字形。
 */
internal val HOME_BINDING_ARROW_SIZE = 24.dp

/** 只用来注册监听，不读它的值 —— 见 [SyncToHost]。 */
internal const val NIGHT_MODE_KEY = "ui_night_mode"

/**
 * 深浅兜底轮询的间隔 —— 300ms，只管「设置项不变」那种定时切换的情况，[SyncToHost] 的推送走另一条。
 */
internal const val NIGHT_POLL_MS = 300L

/**
 * [SyncToHost] 推送醒来之后连查系统配置的等待序列（毫秒，第一项 0 = 立刻查一次）——
 * 给三次补救机会，查到就 break。
 */
internal val NIGHT_RETRY_MS = longArrayOf(0L, 60L, 90L, 150L)
