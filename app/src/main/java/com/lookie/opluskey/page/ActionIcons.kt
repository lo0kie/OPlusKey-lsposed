package com.lookie.opluskey.page

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Flashlight
import com.composables.icons.lucide.Lucide
import com.lookie.opluskey.config.Action
import com.lookie.opluskey.config.CommandKind
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindow
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.Ban
import io.github.lyxnx.compose.ui.tablericons.outline.Bell
import io.github.lyxnx.compose.ui.tablericons.outline.BellOff
import io.github.lyxnx.compose.ui.tablericons.outline.BellRinging
import io.github.lyxnx.compose.ui.tablericons.outline.Bolt
import io.github.lyxnx.compose.ui.tablericons.outline.Camera
import io.github.lyxnx.compose.ui.tablericons.outline.Cards
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronDown
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronUp
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronsUp
import io.github.lyxnx.compose.ui.tablericons.outline.ClockPause
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobileVibration
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import io.github.lyxnx.compose.ui.tablericons.outline.DotsVertical
import io.github.lyxnx.compose.ui.tablericons.outline.EyeOff
import io.github.lyxnx.compose.ui.tablericons.outline.FileExport
import io.github.lyxnx.compose.ui.tablericons.outline.Flask2
import io.github.lyxnx.compose.ui.tablericons.outline.HandFinger
import io.github.lyxnx.compose.ui.tablericons.outline.Home
import io.github.lyxnx.compose.ui.tablericons.outline.Hourglass
import io.github.lyxnx.compose.ui.tablericons.outline.InfoCircle
import io.github.lyxnx.compose.ui.tablericons.outline.LayoutGrid
import io.github.lyxnx.compose.ui.tablericons.outline.Link
import io.github.lyxnx.compose.ui.tablericons.outline.Moon
import io.github.lyxnx.compose.ui.tablericons.outline.MoodEmpty
import io.github.lyxnx.compose.ui.tablericons.outline.Packages
import io.github.lyxnx.compose.ui.tablericons.outline.Pencil
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerPause
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Pointer
import io.github.lyxnx.compose.ui.tablericons.outline.Power
import io.github.lyxnx.compose.ui.tablericons.outline.Refresh
import io.github.lyxnx.compose.ui.tablericons.outline.Reload
import io.github.lyxnx.compose.ui.tablericons.outline.Repeat
import io.github.lyxnx.compose.ui.tablericons.outline.Restore
import io.github.lyxnx.compose.ui.tablericons.outline.Screenshot
import io.github.lyxnx.compose.ui.tablericons.outline.Stack2
import io.github.lyxnx.compose.ui.tablericons.outline.Tag
import io.github.lyxnx.compose.ui.tablericons.outline.Terminal
import io.github.lyxnx.compose.ui.tablericons.outline.TextRecognition
import io.github.lyxnx.compose.ui.tablericons.outline.Trash
import io.github.lyxnx.compose.ui.tablericons.outline.Volume
import io.github.lyxnx.compose.ui.tablericons.outline.Volume2

/** 动作 → 图标的映射（列表行首那个图标）。 */
internal fun actionIcon(token: String): ImageVector = when (token) {
    "none" -> TablerIcons.Outline.Ban
    "native", "passthrough" -> TablerIcons.Outline.Restore

    // 唯一留在 Lucide 的一处：Tabler 没有手电筒字形
    "flashlight" -> Lucide.Flashlight
    "intent:android.media.action.STILL_IMAGE_CAMERA" -> TablerIcons.Outline.Camera

    // keyevent 一律是数字形态
    "keyevent:26" -> TablerIcons.Outline.Power // 电源键（⏻）
    "keyevent:223" -> TablerIcons.Outline.Moon // 熄屏
    "keyevent:164" -> TablerIcons.Outline.BellRinging // 响铃状态
    "keyevent:3" -> TablerIcons.Outline.Home // Home
    "keyevent:4" -> TablerIcons.Outline.ArrowLeft // 返回
    "keyevent:187" -> TablerIcons.Outline.Cards // 最近任务
    "keyevent:83" -> TablerIcons.Outline.Bell // 通知栏
    "keyevent:85" -> TablerIcons.Outline.PlayerPause // 播放 / 暂停
    "keyevent:24" -> TablerIcons.Outline.Volume // 音量 +（两条声波）
    "keyevent:25" -> TablerIcons.Outline.Volume2 // 音量 −（一条声波）

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
        token.startsWith("shell:") -> TablerIcons.Outline.Terminal
        // 区别只在内容存哪（`shell:` 是原文，`cmd:` 是名字）
        token.startsWith("cmd:") -> TablerIcons.Outline.Terminal
        token.startsWith("app:") -> TablerIcons.Outline.LayoutGrid
        else -> TablerIcons.Outline.Dots
    }
}

/**
 * 一条清单条目（[ActionCatalog.Spec]）的图标 —— 界面调用点一律走这个，不要直接调 [actionIcon]。
 *
 * 与 [actionIcon] 的唯一差别是自定义命令：两种命令 token 都是 `cmd:<名字>`，网格格子只有
 * 名字一行，单条 / 多条只能靠图标分辨，所以类型由 [ActionCatalog.Spec.commandKind] 带来。
 */
internal fun specIcon(spec: ActionCatalog.Spec): ImageVector =
    if (spec.commandKind == CommandKind.SERIAL) {
        PageIcons.CommandSerial
    } else if (spec === ActionCatalog.RINGER_CYCLE) {
        // 「循环」与「响铃状态」格共用写法 `keyevent:164`，靠 token 分不出 —— 只能按 spec 认领字形。
        PageIcons.RingerCycle
    } else {
        actionIcon(Action.parse(spec.token).token)
    }

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

    /** 顶栏右侧的菜单按钮（竖三点）。 */
    val Menu: ImageVector get() = TablerIcons.Outline.DotsVertical

    /** 浮层菜单「打开默认界面」—— 窗口框，读作「另一个界面」。 */
    val MenuStock: ImageVector get() = TablerIcons.Outline.AppWindow

    /** 浮层菜单「重置所有配置」—— `Restore`（单圈回退），与 [actionIcon] 里 `native` / `passthrough` 同字形。 */
    val MenuReset: ImageVector get() = TablerIcons.Outline.Restore

    /** 浮层菜单「导出日志」—— 文件 + 向外去的箭头那个字形。 */
    val MenuExport: ImageVector get() = TablerIcons.Outline.FileExport

    /** 「选择应用」页顶栏菜单的「显示系统应用」—— `LayoutGrid`（应用网格）。 */
    val SystemApps: ImageVector get() = TablerIcons.Outline.LayoutGrid

    /** 「选择应用」页顶栏菜单的「隐藏不可打开」—— `EyeOff`（划掉的眼睛）。 */
    val HideUnopenable: ImageVector get() = TablerIcons.Outline.EyeOff

    /** 「选择应用」空状态（清单本身为空：系统限制了可见列表）—— `Packages`（一摞盒子）。 */
    val EmptyApps: ImageVector get() = TablerIcons.Outline.Packages

    /** 「选择应用」空状态（搜索无结果）—— `MoodEmpty`（不用划掉的放大镜：斜杠与 `Ban` 太像）。 */
    val NoMatch: ImageVector get() = TablerIcons.Outline.MoodEmpty

    /** 「自定义命令」页里命令行尾的删除图标 —— `Trash`，用 `error` 色，调用点给 20dp。 */
    val CommandDelete: ImageVector get() = TablerIcons.Outline.Trash

    /** 命令类型「单条命令」—— `Terminal`，当列表行首（网格里自定义命令格子也按类型取它）。 */
    val CommandSingle: ImageVector get() = TablerIcons.Outline.Terminal

    /**
     * 命令类型「多条命令」—— `Stack2`（叠着的几条）。
     *
     * 画的是「数量」而非「顺序」：行首只有 22dp，顺序（`ListNumbers` / `Timeline` 那类）读不出来。
     */
    val CommandSerial: ImageVector get() = TablerIcons.Outline.Stack2

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

    /** 「响铃状态」菜单第一档「循环」—— `Repeat`（两箭头成圈，靠 [specIcon] 按 spec 认领）。 */
    val RingerCycle: ImageVector get() = TablerIcons.Outline.Repeat

    /**
     * 进下一层的行尾右箭头（「按键绑定」格子、浮层菜单里进二级的行）。
     *
     * 只给点了会进下一页的地方 —— 其余格子是取值，给箭头会被读成「点进去还有一层」。
     * 菜单那颗由补丁 `patches/coui/0003` 的 `chevronIcon` 覆盖口传进来按 24dp 正方形画。
     */
    val EnterArrow: ImageVector get() = TablerIcons.Outline.ChevronRight

    /** 「选择应用」页那颗 FAB —— `ChevronsUp`（双人字向上），与 [StepUp] 的 `ChevronUp` 分得开。 */
    val ScrollToTop: ImageVector get() = TablerIcons.Outline.ChevronsUp

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
}
