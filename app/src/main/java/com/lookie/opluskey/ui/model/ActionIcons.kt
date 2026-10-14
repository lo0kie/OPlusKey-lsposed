package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.CommandKind
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AdjustmentsBolt
import io.github.lyxnx.compose.ui.tablericons.outline.AlertHexagon
import io.github.lyxnx.compose.ui.tablericons.outline.AlignBoxCenterMiddle
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindow
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindowBottom
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowBackUp
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.Ban
import io.github.lyxnx.compose.ui.tablericons.outline.BellOff
import io.github.lyxnx.compose.ui.tablericons.outline.BellRinging
import io.github.lyxnx.compose.ui.tablericons.outline.Bolt
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple1
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple2
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple3
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple4
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple5
import io.github.lyxnx.compose.ui.tablericons.outline.BrowserPlus
import io.github.lyxnx.compose.ui.tablericons.outline.Bulb
import io.github.lyxnx.compose.ui.tablericons.outline.Camera
import io.github.lyxnx.compose.ui.tablericons.outline.Cards
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronDown
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronUp
import io.github.lyxnx.compose.ui.tablericons.outline.CirclePlay
import io.github.lyxnx.compose.ui.tablericons.outline.CircleStop
import io.github.lyxnx.compose.ui.tablericons.outline.Command
import io.github.lyxnx.compose.ui.tablericons.outline.Click
import io.github.lyxnx.compose.ui.tablericons.outline.ClockPause
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobileVibration
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import io.github.lyxnx.compose.ui.tablericons.outline.DotsCircleHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.DotsVertical
import io.github.lyxnx.compose.ui.tablericons.outline.EyeOff
import io.github.lyxnx.compose.ui.tablericons.outline.FileExport
import io.github.lyxnx.compose.ui.tablericons.outline.FileImport
import io.github.lyxnx.compose.ui.tablericons.outline.Flask2
import io.github.lyxnx.compose.ui.tablericons.outline.HandFinger
import io.github.lyxnx.compose.ui.tablericons.outline.Home
import io.github.lyxnx.compose.ui.tablericons.outline.Hourglass
import io.github.lyxnx.compose.ui.tablericons.outline.InfoCircle
import io.github.lyxnx.compose.ui.tablericons.outline.LayoutGrid
import io.github.lyxnx.compose.ui.tablericons.outline.Link
import io.github.lyxnx.compose.ui.tablericons.outline.MessageCircle
import io.github.lyxnx.compose.ui.tablericons.outline.Moon
import io.github.lyxnx.compose.ui.tablericons.outline.Microphone
import io.github.lyxnx.compose.ui.tablericons.outline.MoodEmpty
import io.github.lyxnx.compose.ui.tablericons.outline.NavigationCog
import io.github.lyxnx.compose.ui.tablericons.outline.Notification
import io.github.lyxnx.compose.ui.tablericons.outline.Packages
import io.github.lyxnx.compose.ui.tablericons.outline.Pencil
import io.github.lyxnx.compose.ui.tablericons.outline.PictureInPictureTop
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerSkipBack
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerSkipForward
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Pointer
import io.github.lyxnx.compose.ui.tablericons.outline.Power
import io.github.lyxnx.compose.ui.tablericons.outline.Refresh
import io.github.lyxnx.compose.ui.tablericons.outline.Reload
import io.github.lyxnx.compose.ui.tablericons.outline.Repeat
import io.github.lyxnx.compose.ui.tablericons.outline.Restore
import io.github.lyxnx.compose.ui.tablericons.outline.Screenshot
import io.github.lyxnx.compose.ui.tablericons.outline.Tag
import io.github.lyxnx.compose.ui.tablericons.outline.Terminal2
import io.github.lyxnx.compose.ui.tablericons.outline.TextRecognition
import io.github.lyxnx.compose.ui.tablericons.outline.ToggleRight
import io.github.lyxnx.compose.ui.tablericons.outline.Trash
import io.github.lyxnx.compose.ui.tablericons.outline.User
import io.github.lyxnx.compose.ui.tablericons.outline.Volume
import io.github.lyxnx.compose.ui.tablericons.outline.Volume4
import io.github.lyxnx.compose.ui.tablericons.outline.WindowMinimize

/** 动作 → 图标的映射（列表行首那个图标）。 */
internal fun actionIcon(token: String): ImageVector = when (token) {
    "none" -> TablerIcons.Outline.Ban

    // 全项目只用 Tabler 一支笔，不混图标库
    "flashlight" -> TablerIcons.Outline.Bulb
    "intent:android.media.action.STILL_IMAGE_CAMERA" -> TablerIcons.Outline.Camera

    // keyevent 一律是数字形态
    "keyevent:26" -> TablerIcons.Outline.Power // 电源键（⏻）
    // 与「电源键」必须不同字形：同样一颗 ⏻ 摆在一起分不出按下的那个是按键还是菜单。
    // 「电源菜单」= 弹出关机 / 重启那一层，用「滑杆 + 闪电」。
    "power:menu" -> TablerIcons.Outline.AdjustmentsBolt

    // 系统侧键场景表里的三档
    "oplus:flash_memory" -> TablerIcons.Outline.Pencil // 一键闪记
    "oplus:no_disturb" -> TablerIcons.Outline.Moon // 免打扰（月亮：勿扰/夜间的通用读法）
    "oplus:recording" -> TablerIcons.Outline.Microphone // 录音
    "keyevent:223" -> TablerIcons.Outline.Moon // 熄屏
    "keyevent:164" -> TablerIcons.Outline.BellRinging // 响铃状态
    "keyevent:3" -> TablerIcons.Outline.Home // Home
    "keyevent:4" -> TablerIcons.Outline.ArrowBackUp // 返回
    "keyevent:187" -> TablerIcons.Outline.Cards // 最近任务
    "keyevent:83" -> TablerIcons.Outline.Notification // 通知中心（改名不改字形）
    // 控制中心（`panel:control_center`）—— 用「⌘」那颗：它是系统里「控制」那一类的通用读法
    "panel:${Action.Panel.CONTROL_CENTER}" -> TablerIcons.Outline.Command
    // 媒体控制那一格（= 播放 / 暂停）与它菜单里的另外四条
    "keyevent:85" -> TablerIcons.Outline.ToggleRight // 播放 / 暂停（一个开关键，交给系统翻面）
    "keyevent:126" -> TablerIcons.Outline.CirclePlay // 播放
    "keyevent:127" -> TablerIcons.Outline.CircleStop // 暂停
    "keyevent:88" -> TablerIcons.Outline.PlayerSkipBack // 上一首
    "keyevent:87" -> TablerIcons.Outline.PlayerSkipForward // 下一首
    "keyevent:24" -> TablerIcons.Outline.Volume // 音量 +（两条声波）
    "keyevent:25" -> TablerIcons.Outline.Volume4 // 音量 −
    // 把前台应用收成小窗 —— 展开档用「窗口 + 底部块」（`app-window-bottom`）
    "zoom:mini" -> TablerIcons.Outline.AppWindowBottom
    // 收起档（右上角一条）—— 用画中画那颗（画面挂在**顶部**），与展开档一眼分得开
    "zoom:mini:collapse" -> TablerIcons.Outline.PictureInPictureTop

    // 响铃档位（`ringer:normal|vibrate|silent`）；「循环」那一档不在这里 ——
    // 它写的是 `keyevent:164`，就对应上面那行 `keyevent:164 -> BellRinging`。
    "ringer:normal" -> TablerIcons.Outline.BellRinging
    "ringer:vibrate" -> TablerIcons.Outline.DeviceMobileVibration
    "ringer:silent" -> TablerIcons.Outline.BellOff

    else -> when {
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

/**
 * 一条清单条目（[ActionCatalog.Spec]）的图标 —— 界面调用点一律走这个，不要直接调 [actionIcon]。
 *
 * 与 [actionIcon] 的唯一差别是自定义命令：两种命令 token 都是 `cmd:<名字>`，网格格子只有
 * 名字一行，单条 / 多条只能靠图标分辨，所以类型由 [ActionCatalog.Spec.commandKind] 带来。
 */
internal fun specIcon(spec: ActionCatalog.Spec): ImageVector = when {
    spec.commandKind != null -> commandIcon(spec.commandKind, spec.commandSteps)
    // 「循环」与「响铃状态」格共用写法 `keyevent:164`，靠 token 分不出 —— 只能按 spec 认领字形。
    spec === ActionCatalog.RINGER_CYCLE -> PageIcons.RingerCycle
    // 两个**标记**入口格同理：token（`nav:` / `key:`）`Action.parse` 不出有意义的动作，
    // 走 [actionIcon] 只会落到兜底字形 —— 按 spec 认领。
    spec.token == ActionCatalog.NAV_TILE -> PageIcons.NavigationCog
    spec.token == ActionCatalog.KEY_TILE -> PageIcons.Click
    // 「通知与控制中心」同理：标记 token（`panel:`）不是任何一条真取值，按 spec 认领字形
    //（2026-10-14 用户指定 `align-box-center-middle`：上下两块面板）。
    spec.token == ActionCatalog.PANEL_TILE -> PageIcons.Panels
    // 「系统小窗」同理：标记 token（`zoom:`）不是任何一条真取值，按 spec 认领字形
    //（2026-10-14 用户指定 `app-window`：入口是窗口框，菜单里那两档另有两支）。
    spec.token == ActionCatalog.MINI_TILE -> PageIcons.MiniWindow
    else -> actionIcon(Action.parse(spec.token).token)
}

/**
 * 一条自定义命令的类型图标：单条 → [PageIcons.CommandSingle]，多条 → 按步骤数的 [commandSerialIcon]。
 *
 * 三个地方共用这一份：网格格子与「已保存的命令」卡片（[ActionCatalog.Spec] 带过来的类型 / 步数）、
 * 步骤行里引用另一条命令的那枚小图标（[stepIcon] 从命令库查出被引用的那条之后转给它）。
 */
internal fun commandIcon(kind: CommandKind, steps: Int): ImageVector =
    if (kind == CommandKind.SERIAL) commandSerialIcon(steps) else PageIcons.CommandSingle

/**
 * 「多条命令」的类型图标 —— 按步骤数取 `box-multiple-N`，一眼能看出这条有几步。
 *
 * 档位与 `Command.MAX_STEPS` 同宽（1~5）；0 步（还没加步骤的草稿）与超出上限的都以首尾两档收口，
 * 图标只说明「几步」，不承担校验。
 */
internal fun commandSerialIcon(steps: Int): ImageVector =
    SERIAL_STEP_ICONS[steps.coerceIn(1, SERIAL_STEP_ICONS.size) - 1]

/** 步骤数 → 字形，下标 0 是 1 步。长度必须等于 `Command.MAX_STEPS`，否则上一步的钳位会串档。 */
private val SERIAL_STEP_ICONS: List<ImageVector> = listOf(
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
     *  2026-10-14 用户指定。与 [NavigationCog] / [Click] 同属入口格字形：这一格后面是一组面板动作。
     */
    val Panels: ImageVector get() = TablerIcons.Outline.AlignBoxCenterMiddle

    /**
     * 「系统小窗」格的行首 —— 窗口框（`app-window`，2026-10-14 用户指定）。
     *
     * 与 [MenuStock]（浮层菜单的「打开默认界面」）是同一个字形，但语义不同、各留一份名字 ——
     * 以后要分开改的时候不用先拆共用。
     *  与它菜单里那两档合起来是一组三个字形：入口 = 窗口框、展开 = 窗口 + 底部块、
     * 收起 = 画中画（见 `actionIcon` 里 `zoom:mini` 那两行）。
     */
    val MiniWindow: ImageVector get() = TablerIcons.Outline.AppWindow

    /**
     * 网格里**入口格**行尾那颗「圈里三点」（`dots-circle-horizontal`）—— 点开会**弹一层菜单**。
     *
     * 替掉那些格子原来那枚单选圈：单选圈读作「这一格是一个可选项」，而入口格点开**还有一层**。
     * 尺寸与配色按 COUI 的 `RadioButton` 逐档对齐（见 `BindingScreen` 的 `ActionTile`），
     * 换掉之后占位与观感都不变。
     */
    val DotsCircleHorizontal: ImageVector get() = TablerIcons.Outline.DotsCircleHorizontal

    /**
     * 网格里**跳页面**那格（「打开应用」）行尾的「窗口 + 加号」（`browser-plus`）。
     *
     * 与 [DotsCircleHorizontal] 分两种字形：那颗表示「点开弹一层菜单」，这颗表示
     * 「点开进另一个页面」（挑应用）。尺寸与配色同上。
     */
    val BrowserPlus: ImageVector get() = TablerIcons.Outline.BrowserPlus

    /** 顶栏右侧的菜单按钮（竖三点）。 */
    val Menu: ImageVector get() = TablerIcons.Outline.DotsVertical

    /** 浮层菜单「打开默认界面」—— 窗口框，读作「另一个界面」。 */
    val MenuStock: ImageVector get() = TablerIcons.Outline.AppWindow

    /** 浮层菜单「清空配置」—— `Restore`（单圈回退）。 */
    val MenuReset: ImageVector get() = TablerIcons.Outline.Restore

    /** 浮层菜单「导出日志」—— 文件 + 向外去的箭头那个字形。 */
    val MenuExport: ImageVector get() = TablerIcons.Outline.FileExport

    /**
     * 首页「其他 → 更多选项」那一行的行首 —— 圈里三点（`dots-circle-horizontal`，
     * 2026-10-14 用户指定；先试过 `table-options`，换回这颗）。
     *
     * 与网格入口格那颗 [DotsCircleHorizontal] 同字形：两处都读作「还有更多」，
     * 而这一行是「进另一个页面」（行尾另有 [EnterArrow] 说明这件事），不是弹菜单。
     */
    val MoreOptions: ImageVector get() = TablerIcons.Outline.DotsCircleHorizontal

    /** 「更多选项」页「导出配置」—— 文件 + 向外去的箭头；与 [MenuExport] 同字形但**不同用途**（那颗导出的是日志）。 */
    val ConfigExport: ImageVector get() = TablerIcons.Outline.FileExport

    /** 「更多选项」页「导入配置」—— 文件 + 向内去的箭头，正好与 [ConfigExport] 成一进一出。 */
    val ConfigImport: ImageVector get() = TablerIcons.Outline.FileImport

    /** 「选择应用」页顶栏菜单的「显示系统应用」—— `LayoutGrid`（应用网格）。 */
    val SystemApps: ImageVector get() = TablerIcons.Outline.LayoutGrid

    /** 「选择应用」页顶栏菜单的「隐藏不可打开」—— `EyeOff`（划掉的眼睛）。 */
    val HideUnopenable: ImageVector get() = TablerIcons.Outline.EyeOff

    /** 「选择应用」菜单的「小窗打开」—— 与 `window:` 同一个字形（那两档 `zoom:mini` 已另有三支）。 */
    val SmallWindow: ImageVector get() = TablerIcons.Outline.WindowMinimize

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

    /** 「版本」行的行首 —— 信息符号。 */
    val Info: ImageVector get() = TablerIcons.Outline.InfoCircle

    /** 「执行时提示」行的行首 —— 信息符号（`InfoCircle`，与 [Info] 同字形）。 */
    val CommandToast: ImageVector get() = TablerIcons.Outline.InfoCircle

    /** 「名称」输入框的行首图标 —— `Tag`，读作「这是给这条命令起的名字」。 */
    val InputName: ImageVector get() = TablerIcons.Outline.Tag

    /** 「查看地址」行的行首 —— 链接符号。 */
    val Address: ImageVector get() = TablerIcons.Outline.Link

    /** 「更多选项」页的「作者」行首 —— 人形。 */
    val Author: ImageVector get() = TablerIcons.Outline.User

    /** 「反馈问题」行的行首 —— 对话气泡（issue 的读法）。 */
    val Feedback: ImageVector get() = TablerIcons.Outline.MessageCircle

    /** 「响铃状态」菜单第一档「循环」—— `Repeat`（两箭头成圈，靠 [specIcon] 按 spec 认领）。 */
    val RingerCycle: ImageVector get() = TablerIcons.Outline.Repeat

    /**
     * 进下一层的行尾右箭头（「按键绑定」格子、浮层菜单里进二级的行）。
     *
     * 只给点了会进下一页的地方 —— 其余格子是取值，给箭头会被读成「点进去还有一层」。
     * 菜单那颗由补丁 `patches/coui/0003` 的 `chevronIcon` 覆盖口传进来按 24dp 正方形画。
     */
    val EnterArrow: ImageVector get() = TablerIcons.Outline.ChevronRight

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

    /** 「单击延迟」—— 沙漏（等「还有没有第二下」，不是「时刻」的钟）。 */
    val DelayTap: ImageVector get() = TablerIcons.Outline.Hourglass

    /** 「长按延迟」—— `HandFinger`（按住不放多久算长按），与 [TouchApp] 的 `Pointer` 不同页。 */
    val DelayHold: ImageVector get() = TablerIcons.Outline.HandFinger

    /** 「触发间隔」—— `Repeat`（长按重复的间隔，与 [RingerCycle] 同字形但不同页）。 */
    val DelayRepeat: ImageVector get() = TablerIcons.Outline.Repeat

    /** 「长按连续触发」行的行首 —— `Refresh`（两箭头成环）：管「要不要一直来」，与管「隔多久来一次」的 [DelayRepeat] 不同字形。 */
    val LongRepeat: ImageVector get() = TablerIcons.Outline.Refresh

    /** 「命令间隔时长」—— `ClockPause`（带暂停记号的钟）。 */
    val DelayStep: ImageVector get() = TablerIcons.Outline.ClockPause

    /** 「立即触发单击」行的行首 —— `Bolt`（闪电，读作「不等了，立刻」）。 */
    val InstantTrigger: ImageVector get() = TablerIcons.Outline.Bolt

    /** 模块重启 —— `Reload`（回转箭头 + 竖线），读作「重新来一遍」。 */
    val ModuleRestart: ImageVector get() = TablerIcons.Outline.Reload

    /**
     * 「链路自检」里**通**的那一段 —— 与菜单里的对勾同一个字形。
     *
     *  各自留名字而不是共用 [MenuCheck]：一个说的是「菜单里选中了这条」，一个说的是「这段链路是活的」，
     * 以后想换字形时不必连带改另一边（[Confirm] 与 [MenuCheck] 也是同一个字形、两个名字，同一惯例）。
     */
    val LinkOk: ImageVector get() = TablerIcons.Outline.Check

    /**
     * 「链路自检」里**断了**的那一段 —— `AlertHexagon`（六边警告）。
     *
     *  别去找 `AlertTriangle`：Tabler 后来把 `alert-triangle` 改名成了 `alert-hexagon`，
     * 本仓依赖的 3.49 里已经没有前者（2026-10-14 编译报 Unresolved reference 才发现的）。
     */
    val LinkBroken: ImageVector get() = TablerIcons.Outline.AlertHexagon
}
