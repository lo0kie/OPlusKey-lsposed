package com.lookie.opluskey.ui.model

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.model.Action
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
 * 磨砂浮层的模糊半径（dp，给 `blur(radiusX, radiusY)` 之前换算成 px）—— 顶栏与工具栏共用这一个值。
 *
 * 取 24dp：顶栏高 107dp，再大把底下滚过的文字糊成一片，再小又和没糊差不多。
 */
internal val FROSTED_BLUR_RADIUS = 24.dp

/**
 * 磨砂浮层给不透明底色降 alpha 用的那一档（现在只有顶栏在用）。
 */
internal const val FROSTED_SURFACE_ALPHA = 0.82f

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

internal val SLIDER_INSIDE_MARGIN = PaddingValues(
    horizontal = SLIDER_SIDE_INSET,
    vertical = SLIDER_BOTTOM_INSET,
)

/** 卡片组首尾行在 `BasicComponent` 里额外补的上下边距（`coui_list_card_head_or_tail_padding`）。 */
internal val CARD_EDGE_PADDING = 2.dp

/** 「点轨道跳值」认作滑杆的那条带子的高度。 */
internal val SLIDER_TAP_ZONE = SliderDefaults.MinHeight + SLIDER_BOTTOM_INSET + CARD_EDGE_PADDING

/** 「按键绑定」子页的标题。 */
internal const val BINDING_TITLE = "按键绑定"

/** 「自定义命令」子页的标题（第三级页面，从「按键绑定」页尾部网格进入）。 */
internal const val COMMANDS_TITLE = "自定义命令"

/** 「选择应用」页的标题。 */
internal const val APP_PICKER_TITLE = "选择应用"

/**
 * 「更多选项」页的标题（首页「其他 → 更多选项」那一行进入；这是**唯一入口**）。
 *
 * 2026-10-14 用户要求从「关于模块」改叫「更多」、随后定稿为「更多选项」：这一页早就不只是
 * 「关于」—— 除了版本 / 作者 / 查看地址 / 反馈问题，还有**运行状态**与**配置导入导出**。
 * 内部符号仍叫 `ABOUT_*`（页面枚举 `Screen.ABOUT` 等同理），只改**给用户看的**这一处。
 */
internal const val ABOUT_TITLE = "更多选项"

/**
 * 首页那条常驻提示的文案（模块有哪一段没就绪时弹，见 [SettingsPage.linkChecks] / `linkReady`）。
 *
 * **只说结论、不点技术细节**（2026-10-14 用户定的）：用户界面不需要知道是哪一段断了，
 * 「哪一段」是给「更多选项 → 运行状态」看的；这一条只回答「现在能不能用」。
 */
internal const val MODULE_UNAVAILABLE_HINT = "模块不可用，正在等待系统响应"

/**
 * 首页那条常驻提示的**另一种**文案：这次**配置没读成功**（文件在、但读不了 / 读不懂）。
 *
 * 与 [MODULE_UNAVAILABLE_HINT] 分开写，是因为两者要用户做的事不同：
 * 这一种用户自己就能解决 —— **重进页面**会重读一次；说「等待系统响应」会把人引到错方向
 *（等多久都没用）。此时页面整页只读（见 `SettingsPage.configReadOnly`）。
 */
internal const val CONFIG_READ_FAILED_HINT = "配置读取失败，请重进页面"

/**
 * 首页那条常驻提示的第三种文案：**本机机型不在允许运行表里**（见 `core/model/DeviceSupport`）。
 *
 *  这一种与前两种都不同：不是「等一等会好」，而是**这台机器上就是不能用**（模块只注入了界面层）。
 * 所以既不写「等待系统响应」也不写「重进页面」—— 那两句都会把人引去做没用的事。
 */
internal const val UNSUPPORTED_DEVICE_HINT = "未知设备，此设备不可用"

/**
 * 「导入配置」校验没过时的那**一句**提示（2026-10-14 用户定的）。
 *
 * 用户界面上只给结论、不点细节：不解释「哪一段坏了 / 第几条认不出」——那是日志的事
 *（见 `XLog`）。措辞与 [UNSUPPORTED_DEVICE_HINT] 同一档：说清「怎么办」，不说内部结构。
 */
internal const val UNKNOWN_CONFIG_FORMAT = "未知的配置格式，请检查后重试"

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

/** 「更多选项」页信息卡的行数 —— 版本 / 作者 / 查看地址 / 反馈问题，四行固定。 */
internal const val ABOUT_ROWS = 4

/**
 * 「更多选项」页「配置」那张卡的行数 —— 导入配置 / 导出配置，两行固定。
 *
 * 与 [ABOUT_ROWS] 分开：两张卡各算各的圆角（`cardListPositionOf` 的首尾各补 2dp），
 * 合成一个数会让上面那张卡的最后一行被当成「中间行」。
 */
internal const val MORE_CONFIG_ROWS = 2

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
 * 机型允许表外的机器本来就该整页禁用，无限轮询只是白耗电。
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
 * 各页内容区底部 padding —— 统一取 32dp（原先只有首页与「自定义命令」页有，翻页时不齐）。
 */
internal val PAGE_BOTTOM_PADDING = 32.dp

/**
 * 内容区距顶栏底边的那一档 —— 8dp。
 *
 * 补不补看首行是什么：首行是卡片的两页（「更多选项」、「选择应用」）要补一档，因为它们没有
 * `SmallTitle` 自带的上内边距；首行是 `SmallTitle` 的三页不补。两条固定带只带下边这一档。
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
 * 首页那条链路提示（`SnackbarHost`）**内容盒外面**要留出的、给胶囊那圈影子落下的空间。
 *
 * ## 为什么必须留（2026-10-14 真机量出来的）
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
 *  ——2026-10-14 试过（补 16dp），结果切痕跟着胶囊一起上移，一点没修好。
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
 * 应用名右边那枚「系统 / 用户」小标签（[AppKindTag]）的间距与外观。
 *
 * 底色走文字色的低透明度，不另取颜色：标签是纯背景件，深浅主题下都跟着内容色走。
 */
internal val APP_KIND_TAG_GAP = 6.dp
internal val APP_KIND_TAG_RADIUS = 4.dp
internal val APP_KIND_TAG_INSIDE = PaddingValues(horizontal = 5.dp, vertical = 1.dp)
internal const val APP_KIND_TAG_BG_ALPHA = 0.10f

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

/**
 * 动态显隐的控件统一的进出场时长 —— 180ms。
 *
 * 比整页转场快（它不是「换了一屏」），比一条提示稍慢（它要连带把卡片高度撑开 / 收起）。
 */
internal const val VISIBILITY_MS = 180

/** 动态显隐的控件统一的进场：淡入 + 纵向展开（只淡会跳、只展会拉，两样一起才自然）。 */
internal val VISIBILITY_ENTER: EnterTransition =
    fadeIn(tween(VISIBILITY_MS)) + expandVertically(tween(VISIBILITY_MS))

/** 统一出场：纵向收起 + 淡出（[VISIBILITY_ENTER] 的镜像）。 */
internal val VISIBILITY_EXIT: ExitTransition =
    fadeOut(tween(VISIBILITY_MS)) + shrinkVertically(tween(VISIBILITY_MS))

/**
 * 「支持的命令格式」卡里的一行。
 *
 * @param text 整行文案
 * @param prefix 行尾那颗小 `+` 往「命令」输入框里填的前缀（`shell:` / `keyevent:` …）；
 *   `null` = 这一行没有可填的前缀，行尾不画 `+`。
 */
internal data class CommandFormatLine(val text: String, val prefix: String? = null)

/**
 * 「支持的命令格式」卡上的几行 —— 讲**命令输入框里能写什么**。
 *
 *  ⚠️ **收录规则：只列「没有内置入口」的格式**（2026-10-14 用户口径）。内置命令已经给了入口的那些
 *  不在这里重复教手写：`app:` / `window:` 有「打开应用」那一格（小窗是它菜单里的开关）、
 *  `zoom:mini[:collapse]` 有「系统小窗」那两档 —— 同一个能力摆两个入口，手写那条还更容易写错。
 *  剩下的这几行是**没有图形入口**的：shell / keyevent / intent / shortcut / cmd。
 *
 *  点带 `prefix` 的那一行 = 把前缀**替换**进命令输入框（不是追加，见渲染点的注释）。
 */
internal val COMMAND_FORMAT_LINES = listOf(
    CommandFormatLine("默认为 shell 命令"),
    CommandFormatLine("shell:<命令> —— 执行 shell 命令", "shell:"),
    CommandFormatLine("keyevent:<按键> —— 模拟按键", "keyevent:"),
    CommandFormatLine("intent:<Action> —— 发送 Intent", "intent:"),
    CommandFormatLine("shortcut:<ID> —— 触发快捷方式", "shortcut:"),
    CommandFormatLine("cmd:<名字> —— 调用另一条单条命令", "cmd:"),
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
        override val motion: NavMotion = NavMotion(
            programmatic = NavSettleSpec.Tween(
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
 * 与库原来那颗 `COUIIcons.Basic.ArrowRight` 同为 24dp 下折线占 12dp 的字形。
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
