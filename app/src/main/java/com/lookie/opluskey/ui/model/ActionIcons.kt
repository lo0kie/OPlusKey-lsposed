package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.CommandKind
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.filled.CircleCheck
import io.github.lyxnx.compose.ui.tablericons.outline.Abc
import io.github.lyxnx.compose.ui.tablericons.outline.AdjustmentsBolt
import io.github.lyxnx.compose.ui.tablericons.outline.AlignBoxCenterMiddle
import io.github.lyxnx.compose.ui.tablericons.outline.AlphabetChinese
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindow
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindowBottom
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowBackUp
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.Ban
import io.github.lyxnx.compose.ui.tablericons.outline.Bell
import io.github.lyxnx.compose.ui.tablericons.outline.BellOff
import io.github.lyxnx.compose.ui.tablericons.outline.BellRinging
import io.github.lyxnx.compose.ui.tablericons.outline.Bolt
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple1
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple2
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple3
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple4
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple5
import io.github.lyxnx.compose.ui.tablericons.outline.BrowserPlus
import io.github.lyxnx.compose.ui.tablericons.outline.Bug
import io.github.lyxnx.compose.ui.tablericons.outline.Bulb
import io.github.lyxnx.compose.ui.tablericons.outline.Cards
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronDown
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronUp
import io.github.lyxnx.compose.ui.tablericons.outline.Circle
import io.github.lyxnx.compose.ui.tablericons.outline.Click
import io.github.lyxnx.compose.ui.tablericons.outline.ClockHour4
import io.github.lyxnx.compose.ui.tablericons.outline.Command
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceIpadCancel
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceIpadCheck
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobilePause
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobileVibration
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import io.github.lyxnx.compose.ui.tablericons.outline.DotsCircleHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.DotsVertical
import io.github.lyxnx.compose.ui.tablericons.outline.Eraser
import io.github.lyxnx.compose.ui.tablericons.outline.EyeOff
import io.github.lyxnx.compose.ui.tablericons.outline.FileArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.FileArrowRight
import io.github.lyxnx.compose.ui.tablericons.outline.Flask2
import io.github.lyxnx.compose.ui.tablericons.outline.HandGrab
import io.github.lyxnx.compose.ui.tablericons.outline.Home
import io.github.lyxnx.compose.ui.tablericons.outline.HomeCancel
import io.github.lyxnx.compose.ui.tablericons.outline.HomeCheck
import io.github.lyxnx.compose.ui.tablericons.outline.Infinity2
import io.github.lyxnx.compose.ui.tablericons.outline.InputX
import io.github.lyxnx.compose.ui.tablericons.outline.LayoutGrid
import io.github.lyxnx.compose.ui.tablericons.outline.Link
import io.github.lyxnx.compose.ui.tablericons.outline.Logs
import io.github.lyxnx.compose.ui.tablericons.outline.Microphone
import io.github.lyxnx.compose.ui.tablericons.outline.MoodEmpty
import io.github.lyxnx.compose.ui.tablericons.outline.Moon
import io.github.lyxnx.compose.ui.tablericons.outline.Music
import io.github.lyxnx.compose.ui.tablericons.outline.NavigationCog
import io.github.lyxnx.compose.ui.tablericons.outline.Notification
import io.github.lyxnx.compose.ui.tablericons.outline.Packages
import io.github.lyxnx.compose.ui.tablericons.outline.Pencil
import io.github.lyxnx.compose.ui.tablericons.outline.PictureInPictureOn
import io.github.lyxnx.compose.ui.tablericons.outline.PictureInPictureTop
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerPlay
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerStop
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerTrackNext
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerTrackPrev
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Pointer
import io.github.lyxnx.compose.ui.tablericons.outline.Power
import io.github.lyxnx.compose.ui.tablericons.outline.Reload
import io.github.lyxnx.compose.ui.tablericons.outline.Repeat
import io.github.lyxnx.compose.ui.tablericons.outline.Screenshot
import io.github.lyxnx.compose.ui.tablericons.outline.Selector
import io.github.lyxnx.compose.ui.tablericons.outline.Settings
import io.github.lyxnx.compose.ui.tablericons.outline.SpacingHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.Switch3
import io.github.lyxnx.compose.ui.tablericons.outline.Terminal2
import io.github.lyxnx.compose.ui.tablericons.outline.TextOutline
import io.github.lyxnx.compose.ui.tablericons.outline.TextRecognition
import io.github.lyxnx.compose.ui.tablericons.outline.Tooltip
import io.github.lyxnx.compose.ui.tablericons.outline.Trash
import io.github.lyxnx.compose.ui.tablericons.outline.UserScreen
import io.github.lyxnx.compose.ui.tablericons.outline.Versions
import io.github.lyxnx.compose.ui.tablericons.outline.Volume
import io.github.lyxnx.compose.ui.tablericons.outline.Volume4
import io.github.lyxnx.compose.ui.tablericons.outline.WindowMinimize
import io.github.lyxnx.compose.ui.tablericons.outline.World

/** 动作 → 图标的映射（列表行首那个图标）。 */
internal fun actionIcon(token: String): ImageVector =
    when (token) {
        "none" -> {
            TablerIcons.Outline.Ban
        }

        // 全项目只用 Tabler 一支笔，不混图标库
        "flashlight" -> {
            TablerIcons.Outline.Bulb
        }

        // keyevent 一律是数字形态
        "keyevent:26" -> {
            TablerIcons.Outline.Power
        }

        // 电源键（⏻）
        // 与「电源键」必须不同字形：同样一颗 ⏻ 摆在一起分不出按下的那个是按键还是菜单。
        // 「电源菜单」= 弹出关机 / 重启那一层，用「滑杆 + 闪电」。
        "power:menu" -> {
            TablerIcons.Outline.AdjustmentsBolt
        }

        // 系统侧键场景表里的三档
        "oplus:flash_memory" -> {
            TablerIcons.Outline.Pencil
        }

        // 一键闪记
        "oplus:no_disturb" -> {
            TablerIcons.Outline.Moon
        }

        // 免打扰（月亮：勿扰/夜间的通用读法）
        "oplus:recording" -> {
            TablerIcons.Outline.Microphone
        }

        // 录音
        "keyevent:164" -> {
            TablerIcons.Outline.Bell
        }

        // 响铃状态（那一格；菜单里的「循环」见 [specIcon]）
        "keyevent:3" -> {
            TablerIcons.Outline.Home
        }

        // Home
        "keyevent:4" -> {
            TablerIcons.Outline.ArrowBackUp
        }

        // 返回
        "keyevent:187" -> {
            TablerIcons.Outline.Cards
        }

        // 最近任务
        "keyevent:83" -> {
            TablerIcons.Outline.Notification
        }

        // 通知中心（改名不改字形）
        // 控制中心（`panel:control_center`）—— 用「⌘」那颗：它是系统里「控制」那一类的通用读法
        "panel:${Action.Panel.CONTROL_CENTER}" -> {
            TablerIcons.Outline.Command
        }

        // 媒体控制菜单的五条。**格子**那枚不走这里 —— 它跟首行共用同一串 token，由 [specIcon]
        // 按 spec 认领成音符（那是「一类东西」），这里的开关键说的是「那一个具体取值」。
        "keyevent:85" -> {
            TablerIcons.Outline.Switch3
        }

        // 播放 / 暂停（交给系统按当前状态翻面）
        "keyevent:126" -> {
            TablerIcons.Outline.PlayerPlay
        }

        // 播放
        "keyevent:127" -> {
            TablerIcons.Outline.PlayerStop
        }

        // 暂停
        "keyevent:88" -> {
            TablerIcons.Outline.PlayerTrackPrev
        }

        // 上一首
        "keyevent:87" -> {
            TablerIcons.Outline.PlayerTrackNext
        }

        // 下一首
        "keyevent:24" -> {
            TablerIcons.Outline.Volume
        }

        // 音量 +（两条声波）
        "keyevent:25" -> {
            TablerIcons.Outline.Volume4
        }

        // 音量 −
        // 把前台应用收成小窗 —— 展开档用「窗口 + 底部块」（`app-window-bottom`）
        "zoom:mini" -> {
            TablerIcons.Outline.AppWindowBottom
        }

        // 收起档（右上角一条）—— 用画中画那颗（画面挂在**顶部**），与展开档一眼分得开
        "zoom:mini:collapse" -> {
            TablerIcons.Outline.PictureInPictureTop
        }

        // 响铃档位（`ringer:normal|vibrate|silent`）；「循环」那一档不在这里 ——
        // 它写的是 `keyevent:164`，由 [specIcon] 按 spec 认领 [PageIcons.RingerCycle]（铃铛 + 一圈箭头）。
        "ringer:normal" -> {
            TablerIcons.Outline.BellRinging
        }

        "ringer:vibrate" -> {
            TablerIcons.Outline.DeviceMobileVibration
        }

        "ringer:silent" -> {
            TablerIcons.Outline.BellOff
        }

        else -> {
            when {
                // 截图那条 shell（清单里是 `service call color_screenshot 1`）
                token.contains("color_screenshot") -> TablerIcons.Outline.Screenshot

                // 小布识屏那条 shell（`DIRECT_SIDEBAR_SERVICE`）—— 语义是「识屏」
                token.contains("DIRECT_SIDEBAR_SERVICE") -> TablerIcons.Outline.TextRecognition

                token.startsWith("shell:") -> TablerIcons.Outline.Terminal2

                // 区别只在内容存哪（`shell:` 是原文，`cmd:` 是名字）
                token.startsWith("cmd:") -> TablerIcons.Outline.Terminal2

                token.startsWith("app:") -> TablerIcons.Outline.LayoutGrid

                // 「以小窗口打开」（`window:目标`）：新起一个窗口 —— 与「把前台的收成小窗」那两档
                // （`zoom:mini` / `zoom:mini:collapse`，见上面）是**三支不同字形**，别顺手并回来。
                token.startsWith("window:") -> TablerIcons.Outline.WindowMinimize

                else -> TablerIcons.Outline.Dots
            }
        }
    }

/**
 * 一条清单条目（[ActionCatalog.Spec]）的图标 —— **只有一处该调它**：界面按
 * [ActionCatalog.queue] 建那张图标表的时候（见 `SettingsPage` 的 `tileIcons`）；
 * 界面各处一律从那张表里取，别处不要再现调这个、也不要直接调 [actionIcon]。
 *
 * 与 [actionIcon] 的差别有两处。其一是自定义命令：两种命令 token 都是 `cmd:<名字>`，网格格子
 * 只有名字一行，单条 / 多条只能靠图标分辨，类型由 [ActionCatalog.Spec.commandKind] 带来。
 * 其二是 token 认不出取值的条目 —— [ActionCatalog.RINGER_CYCLE]（与「响铃状态」格共用写法）
 * 和几个标记入口格（`nav:` / `key:` / `panel:` / `zoom:`）。
 */
internal fun specIcon(spec: ActionCatalog.Spec): ImageVector =
    when {
        spec.commandKind != null -> commandIcon(spec.commandKind, spec.commandSteps)

        // 「循环」与「响铃状态」格共用写法 `keyevent:164`，靠 token 分不出 —— 只能按 spec 认领字形。
        spec === ActionCatalog.RINGER_CYCLE -> PageIcons.RingerCycle

        // 「媒体控制」那一格与菜单首行共用 `keyevent:85`（`Action.parse` 出来的取值一模一样）——
        //  只能按 spec 认领：**格子**用音符（它是一类东西，入口），**菜单首行**落到下面 `actionIcon`
        //  的开关键那颗（那是一个具体取值）。这两处**要不一样**：入口是「媒体控制」这件事，
        //  首行是「播放 / 暂停」这个取值。
        spec === ActionCatalog.MEDIA_TILE_SPEC -> TablerIcons.Outline.Music

        // 两个**标记**入口格同理：token（`nav:` / `key:`）`Action.parse` 不出有意义的动作，
        // 走 [actionIcon] 只会落到兜底字形 —— 按 spec 认领。
        spec.token == ActionCatalog.NAV_TILE -> PageIcons.NavigationCog

        spec.token == ActionCatalog.KEY_TILE -> PageIcons.Click

        // 「通知与控制中心」同理：标记 token（`panel:`）不是任何一条真取值，按 spec 认领字形
        // （`align-box-center-middle`：上下两块面板）。
        spec.token == ActionCatalog.PANEL_TILE -> PageIcons.Panels

        // 「应用转小窗」同理：标记 token（`zoom:`）不是任何一条真取值，按 spec 认领字形
        // （`picture-in-picture-on`：入口是画中画，菜单里那两档另有两支）。
        spec.token == ActionCatalog.MINI_TILE -> PageIcons.MiniWindow

        // 走 ActionCatalog 那份按 token 记忆的规范化结果，不在这里现 `Action.parse` —— 这条
        //  在网格每个格子的每一次重组上（见 `ActionCatalog.canonicalToken`）。
        else -> actionIcon(ActionCatalog.canonicalToken(spec.token))
    }

/**
 * 一条自定义命令的类型图标：单条 → [PageIcons.CommandSingle]，多条 → 按步骤数的 [commandSerialIcon]。
 *
 * 三个地方共用这一份：网格格子与「已保存的命令」卡片（[ActionCatalog.Spec] 带过来的类型 / 步数）、
 * 步骤行里引用另一条命令的那枚小图标（[stepIcon] 从命令库查出被引用的那条之后转给它）。
 */
internal fun commandIcon(
    kind: CommandKind,
    steps: Int,
): ImageVector =
    if (kind ==
        CommandKind.SERIAL
    ) {
        commandSerialIcon(steps)
    } else {
        PageIcons.CommandSingle
    }

/**
 * 「多条命令」的类型图标 —— 按步骤数取 `box-multiple-N`，一眼能看出这条有几步。
 *
 * 档位与 `Command.MAX_STEPS` 同宽（1~5）；0 步（还没加步骤的草稿）与超出上限的都以首尾两档收口，
 * 图标只说明「几步」，不承担校验。
 */
internal fun commandSerialIcon(steps: Int): ImageVector = SERIAL_STEP_ICONS[steps.coerceIn(1, SERIAL_STEP_ICONS.size) - 1]

/** 步骤数 → 字形，下标 0 是 1 步。长度必须等于 `Command.MAX_STEPS`，否则上一步的钳位会串档。 */
private val SERIAL_STEP_ICONS: List<ImageVector> =
    listOf(
        TablerIcons.Outline.BoxMultiple1,
        TablerIcons.Outline.BoxMultiple2,
        TablerIcons.Outline.BoxMultiple3,
        TablerIcons.Outline.BoxMultiple4,
        TablerIcons.Outline.BoxMultiple5,
    )

/** 页面自己的小图标（顶栏返回键 / 行尾箭头 / 「按键绑定」行首 / 浮层菜单两行）—— 同样出自 Tabler。 */
internal object PageIcons {
    /** 顶栏返回键。 */
    val Back: ImageVector get() = TablerIcons.Outline.ArrowLeft

    /** 「按键绑定」行首 —— 箭头光标 `Pointer`，读作「按下去那一下」。 */
    val TouchApp: ImageVector get() = TablerIcons.Outline.Pointer

    /**
     * 「启用按键绑定」那一行的行首 —— 电源符号。
     *
     * 那一行必须给图标：它紧跟「按键绑定」行，同卡两行都有 `startAction` 标题才对得齐，
     * 只给上面那行的话下面标题会左移一个图标宽。
     */
    val Power: ImageVector get() = TablerIcons.Outline.Power

    /** 「系统导航」格的行首 —— 指北针 + 齿轮（`navigation-cog`）：这一格后面是一组导航动作。 */
    val NavigationCog: ImageVector get() = TablerIcons.Outline.NavigationCog

    /** 「系统按键」格的行首 —— 手指点击（`click`）：这一格后面是一组按键动作。 */
    val Click: ImageVector get() = TablerIcons.Outline.Click

    /**
     * 「通知与控制中心」格的行首 —— `align-box-center-middle`（上下两块面板）。
     *
     * 用户指定。与 [NavigationCog] / [Click] 同属入口格字形：这一格后面是一组面板动作。
     */
    val Panels: ImageVector get() = TablerIcons.Outline.AlignBoxCenterMiddle

    /**
     * 「应用转小窗」格的行首 —— 画中画（`picture-in-picture-on`）。
     *
     * 与 [SmallWindow] / [TileSmallWindow] 同字形但**各留一份名字**（三处语义不同：入口格 /
     * 「选择应用」菜单项 / 格子上的状态标记），以后要分开改的时候不用先拆共用。
     *  它菜单里那两档仍是自己的字形（展开 = 窗口 + 底部块、收起 = 画中画挂顶，
     * 见 `actionIcon` 里 `zoom:mini` 那两行）。
     */
    val MiniWindow: ImageVector get() = TablerIcons.Outline.PictureInPictureOn

    /**
     * 网格里**跳页面**那格（「打开应用」）行尾的「窗口 + 加号」（`browser-plus`）。
     *
     * 与入口格那颗选择器字形（[Selector]）分两种字形：那颗表示「点开弹一层菜单」，这颗表示
     * 「点开进另一个页面」（挑应用）。尺寸与配色同上。
     */
    val BrowserPlus: ImageVector get() = TablerIcons.Outline.BrowserPlus

    /**
     * 「打开应用」格绑成**小窗打开**（`window:`）时，标题右边那枚小标记 ——
     * 画中画（`picture-in-picture-on`）。
     *
     *  与行尾那颗 [BrowserPlus] 分工不同：那颗说明「点开进另一个页面」，这颗说明
     *  **这一格绑的是小窗起** —— 这一格只有一行标题，不点开「选择应用」菜单看不出绑的是哪一档。
     *  与 [SmallWindow]（菜单项）/ [MiniWindow]（入口格）同字形，各留一份名字。
     */
    val TileSmallWindow: ImageVector get() = TablerIcons.Outline.PictureInPictureOn

    /** 顶栏右侧的菜单按钮（竖三点）。 */
    val Menu: ImageVector get() = TablerIcons.Outline.DotsVertical

    /** 浮层菜单「打开默认界面」—— 窗口框，读作「另一个界面」。 */
    val MenuStock: ImageVector get() = TablerIcons.Outline.AppWindow

    /** 浮层菜单「清空配置」—— `eraser`（橡皮擦）。 */
    val MenuReset: ImageVector get() = TablerIcons.Outline.Eraser

    /** 浮层菜单「导出日志」—— `logs`（一叠日志行）。 */
    val MenuExport: ImageVector get() = TablerIcons.Outline.Logs

    /**
     * 首页「其他 → 更多选项」那一行的行首 —— 圈里三点（`dots-circle-horizontal`，
     * 用户指定；先试过 `table-options`，换回这颗）。
     *
     * 圈里三点（Tabler 的 `dots-circle-horizontal`，网格入口格原先也用这颗）：读作「还有更多」，
     * 而这一行是「进另一个页面」（行尾另有 [EnterArrow] 说明这件事），不是弹菜单。
     */
    val MoreOptions: ImageVector get() = TablerIcons.Outline.DotsCircleHorizontal

    /** 「更多选项」页「语言」—— `world`（地球：用地球表示语言）。 */
    val Language: ImageVector get() = TablerIcons.Outline.World

    // 语言菜单里那三项各自的字形 —— **不共用上面那颗地球**：共用的话菜单里三行看起来一模一样，
    //  行首这一颗是唯一能一眼分出「跟随系统 / 中文 / English」的东西。

    /** 「跟随系统」—— `settings`（齿轮：这份交给系统定）。 */
    val LanguageAuto: ImageVector get() = TablerIcons.Outline.Settings

    /** 「简体中文」—— `alphabet-chinese`（汉字「文」）。 */
    val LanguageChinese: ImageVector get() = TablerIcons.Outline.AlphabetChinese

    /** 「English」—— `abc`（拉丁字母）。 */
    val LanguageEnglish: ImageVector get() = TablerIcons.Outline.Abc

    /** 「更多选项」页「导出配置」—— `file-arrow-right`（文件 + 向外去的箭头）。 */
    val ConfigExport: ImageVector get() = TablerIcons.Outline.FileArrowRight

    /** 「更多选项」页「导入配置」—— `file-arrow-left`（文件 + 向内进的箭头），与 [ConfigExport] 成一出一进。 */
    val ConfigImport: ImageVector get() = TablerIcons.Outline.FileArrowLeft

    /** 「选择应用」页顶栏菜单的「显示系统应用」—— `LayoutGrid`（应用网格）。 */
    val SystemApps: ImageVector get() = TablerIcons.Outline.LayoutGrid

    /**
     * 「选择应用」页顶栏菜单的「显示失效应用」—— `input-x`（框里一个叉）。
     *
     *  这一档是**纯显示过滤**（默认关）：读作「这些条目点下去也进不去」。
     */
    val ShowUnopenable: ImageVector get() = TablerIcons.Outline.InputX

    /**
     * 「选择应用」菜单的「以小窗口打开」—— 画中画（`picture-in-picture-on`）。
     *
     *  与 [TileSmallWindow] / [MiniWindow] 同字形但**各留一份名字**（三处语义不同：菜单项 /
     * 格子上的状态标记 / 入口格），以后要分开改的时候不用先拆共用。
     */
    val SmallWindow: ImageVector get() = TablerIcons.Outline.PictureInPictureOn

    /** 「选择应用」空状态（清单本身为空：系统限制了可见列表）—— `Packages`（一摞盒子）。 */
    val EmptyApps: ImageVector get() = TablerIcons.Outline.Packages

    /** 「选择应用」空状态（搜索无结果）—— `MoodEmpty`（不用划掉的放大镜：斜杠与 `Ban` 太像）。 */
    val NoMatch: ImageVector get() = TablerIcons.Outline.MoodEmpty

    /** 「自定义命令」页里命令行尾的删除图标 —— `Trash`，用 `error` 色，调用点给 20dp。 */
    val CommandDelete: ImageVector get() = TablerIcons.Outline.Trash

    /** 命令类型「单条命令」—— `Terminal2`，当列表行首（网格里自定义命令格子也按类型取它）。 */
    val CommandSingle: ImageVector get() = TablerIcons.Outline.Terminal2

    /**
     * 「按键绑定」页右下角那颗 FAB —— 进「自定义命令」页新增一条命令（`Plus`）。
     *
     * 与 [StepAdd] 同字形但语义不同（新增命令 / 给多条命令加一步），各留一个名字。
     */
    val CommandAdd: ImageVector get() = TablerIcons.Outline.Plus

    /** 「步骤」卡片里「添加步骤」那一行的行首图标。 */
    val StepAdd: ImageVector get() = TablerIcons.Outline.Plus

    /** 「支持的命令格式」每行末尾的小 `+` —— 与 [StepAdd] / [CommandAdd] 同字形，画小一档（16dp）。 */
    val InsertPrefix: ImageVector get() = TablerIcons.Outline.Plus

    /** 「已保存的命令」卡片右上角 action 按钮 —— `Flask2`（锥形瓶），读作「把这条试跑一遍」。 */
    val TestCommand: ImageVector get() = TablerIcons.Outline.Flask2

    /** 步骤行尾的「上移」/「下移」—— 竖人字。 */
    val StepUp: ImageVector get() = TablerIcons.Outline.ChevronUp

    val StepDown: ImageVector get() = TablerIcons.Outline.ChevronDown

    /** 编辑用铅笔 —— `Pencil`（编辑最不会误读的字形）。 */
    val CommandEdit: ImageVector get() = TablerIcons.Outline.Pencil

    /** 「振动反馈」行的行首 —— `DeviceMobileVibration`（手机 + 两道声波）。 */
    val Vibrate: ImageVector get() = TablerIcons.Outline.DeviceMobileVibration

    /** 「版本」行的行首 —— `versions`（版本 / 分支符号，比通用信息符号更具体）。 */
    val Versions: ImageVector get() = TablerIcons.Outline.Versions

    /** 「执行时提示」行的行首 —— `tooltip`（气泡提示）。 */
    val CommandToast: ImageVector get() = TablerIcons.Outline.Tooltip

    /** 「名称」输入框的行首图标 —— `text-outline`（文字轮廓：这一格填的是名字）。 */
    val InputName: ImageVector get() = TablerIcons.Outline.TextOutline

    /**
     * 「关于」卡里「作者 / 查看地址 / 反馈问题」**三行的行尾** —— 链接符号（`link`）。
     *
     *  三行点了都是**开一个外部链接**，行尾摆这一颗读作「点了会跳出去」—— 比通用的右箭头具体
     *（那一颗只说「点了有反应」）。
     */
    val Link: ImageVector get() = TablerIcons.Outline.Link

    /** 「关于」卡里「作者」那一行的行首 —— `user-screen`（人的名片，读作「个人页」）。 */
    val UserScreen: ImageVector get() = TablerIcons.Outline.UserScreen

    /** 「关于本机」那一行：**适配命中** —— `device-ipad-check`（这台机器认得）。 */
    val DeviceAdapted: ImageVector get() = TablerIcons.Outline.DeviceIpadCheck

    /** 「关于本机」那一行：**没命中**（等待适配 / 不在清单）—— `device-ipad-cancel`。 */
    val DeviceUnsupported: ImageVector get() = TablerIcons.Outline.DeviceIpadCancel

    /** 「关于」卡里「反馈问题」那一行的行首 —— `bug`（提 issue 就是报 bug）。 */
    val Bug: ImageVector get() = TablerIcons.Outline.Bug

    /** 「响铃状态」菜单第一档「循环」—— `Repeat`（两箭头成圈，靠 [specIcon] 按 spec 认领）。 */
    val RingerCycle: ImageVector get() = TablerIcons.Outline.Repeat

    /**
     * 进下一层的行尾右箭头（「按键绑定」格子、浮层菜单里进二级的行）。
     *
     * 只给点了会进下一页的地方 —— 其余格子是取值，给箭头会被读成「点进去还有一层」。
     * 菜单那颗由补丁 `patches/coui/0003` 的 `chevronIcon` 覆盖口传进来按 24dp 正方形画。
     */
    val EnterArrow: ImageVector get() = TablerIcons.Outline.ChevronRight

    /**
     * 步骤卡里每行行尾那颗**选择器指示**（`selector`：一个框，右下角一个小三角）。
     *
     *  步骤卡里的行点了都会弹选择器（点某一步 = 换成别的、点「添加命令」= 加一步），
     *  行尾摆这一颗读作「这一行点了得选点什么」。
     *
     *  与 [EnterArrow] 分工不同：那颗说的是「点进去还有一层」，只给会进下一层的行；
     *  这一颗说的只是「这里可点、点了要选」。它**不吃点击** —— 弹选择器的是整行自己。
     */
    val Selector: ImageVector get() = TablerIcons.Outline.Selector

    /**
     * 网格里**非入口格**行尾那颗选中指示：没选中 —— `circle`（空心圆）。
     *
     *  这一格原先画的是 COUI 的 `RadioButton`（一个圆点），与全页那套 Tabler 线性图标不是一支笔、
     *  摆在同行里明显两种风格。选中态见 [TileChecked]；两态之间的颜色走 `BindingScreen` 的
     *  `entryIndicatorColor`（本来就是 `animateColorAsState`，是渐变过去的）。
     */
    val TileUnchecked: ImageVector get() = TablerIcons.Outline.Circle

    /** 同上，选中态 —— **实心**的 `circle-check`（实心圆里一个勾；与上面那枚空心的一实一虚成对）。 */
    val TileChecked: ImageVector get() = TablerIcons.Filled.CircleCheck

    /** 命令编辑页底部工具栏的主操作（「保存 / 修改」）—— `Check`，主次靠颜色分（主操作走 `primary`）。 */
    val Confirm: ImageVector get() = TablerIcons.Outline.Check

    /**
     * 浮层菜单行尾那颗「这一档就是当前值」的对勾。
     *
     * 库 `DropdownImpl` 的行尾对勾是写死的 `COUIIcons.Basic.Check`（描边 1.4，与页面其余
     * Tabler 图标不同一支笔），补丁 `patches/coui/0003-overridable-trailing-icons.patch`
     * 给 `DropdownItem` 开了 `checkIcon` 覆盖口，菜单行统一传 Tabler 这颗 `Check`。
     */
    val MenuCheck: ImageVector get() = TablerIcons.Outline.Check

    /** 「单击延迟」—— `device-mobile-pause`（手机屏上一个暂停记号：等「还有没有第二下」）。 */
    val DelayTap: ImageVector get() = TablerIcons.Outline.DeviceMobilePause

    /** 「长按延迟」—— `hand-grab`（一把抓住：按住不放多久算长按）。 */
    val DelayHold: ImageVector get() = TablerIcons.Outline.HandGrab

    /** 「触发间隔」—— `spacing-horizontal`（左右两支箭头拉开：管「隔多久来一次」）。 */
    val DelayRepeat: ImageVector get() = TablerIcons.Outline.SpacingHorizontal

    /** 「长按连续触发」行的行首 —— `infinity-2`（无限符号）：管「要不要一直来」，与管「隔多久来一次」的 [DelayRepeat] 不同字形。 */
    val LongRepeat: ImageVector get() = TablerIcons.Outline.Infinity2

    /** 「命令间隔时长」—— `clock-hour-4`（指到四点的钟：等一段时间再走下一步）。 */
    val DelayStep: ImageVector get() = TablerIcons.Outline.ClockHour4

    /** 「立即触发单击」行的行首 —— `Bolt`（闪电，读作「不等了，立刻」）。 */
    val InstantTrigger: ImageVector get() = TablerIcons.Outline.Bolt

    /** 模块重启 —— `Reload`（回转箭头 + 竖线），读作「重新来一遍」。 */
    val ModuleRestart: ImageVector get() = TablerIcons.Outline.Reload

    /** 「运行状态」里**正常**的那一段 —— `home-check`（房子 + 勾）。 */
    val LinkOk: ImageVector get() = TablerIcons.Outline.HomeCheck

    /** 「运行状态」里**不正常**的那一段 —— `home-cancel`（房子 + 叉）。 */
    val LinkBroken: ImageVector get() = TablerIcons.Outline.HomeCancel
}
