package com.lookie.opluskey.ui.model.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.ui.model.theme.AppTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.filled.CircleCheck
import io.github.lyxnx.compose.ui.tablericons.outline.Abc
import io.github.lyxnx.compose.ui.tablericons.outline.AdjustmentsAlt
import io.github.lyxnx.compose.ui.tablericons.outline.AlertCircle
import io.github.lyxnx.compose.ui.tablericons.outline.AlertSquare
import io.github.lyxnx.compose.ui.tablericons.outline.AlignBoxCenterMiddle
import io.github.lyxnx.compose.ui.tablericons.outline.AlignLeft
import io.github.lyxnx.compose.ui.tablericons.outline.AlphabetChinese
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindow
import io.github.lyxnx.compose.ui.tablericons.outline.Apps
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowsDoubleNeSw
import io.github.lyxnx.compose.ui.tablericons.outline.BrandAlipay
import io.github.lyxnx.compose.ui.tablericons.outline.BrandGithub
import io.github.lyxnx.compose.ui.tablericons.outline.BrandQq
import io.github.lyxnx.compose.ui.tablericons.outline.BrandTelegram
import io.github.lyxnx.compose.ui.tablericons.outline.BrandWechat
import io.github.lyxnx.compose.ui.tablericons.outline.Cancel
import io.github.lyxnx.compose.ui.tablericons.outline.CapsuleHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.CategoryMinus
import io.github.lyxnx.compose.ui.tablericons.outline.CategoryPlus
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronDown
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronUp
import io.github.lyxnx.compose.ui.tablericons.outline.Circle
import io.github.lyxnx.compose.ui.tablericons.outline.CircleCheck
import io.github.lyxnx.compose.ui.tablericons.outline.Click
import io.github.lyxnx.compose.ui.tablericons.outline.ClockHour4
import io.github.lyxnx.compose.ui.tablericons.outline.CloudSearch
import io.github.lyxnx.compose.ui.tablericons.outline.CodePlus
import io.github.lyxnx.compose.ui.tablericons.outline.ColorSwatch
import io.github.lyxnx.compose.ui.tablericons.outline.Contrast
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceIpadCancel
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceIpadCheck
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobilePause
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobileVibration
import io.github.lyxnx.compose.ui.tablericons.outline.DotsCircleHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.DotsVertical
import io.github.lyxnx.compose.ui.tablericons.outline.Download
import io.github.lyxnx.compose.ui.tablericons.outline.Edit
import io.github.lyxnx.compose.ui.tablericons.outline.Eraser
import io.github.lyxnx.compose.ui.tablericons.outline.ExternalLink
import io.github.lyxnx.compose.ui.tablericons.outline.EyeCog
import io.github.lyxnx.compose.ui.tablericons.outline.FileDownload
import io.github.lyxnx.compose.ui.tablericons.outline.FileMinus
import io.github.lyxnx.compose.ui.tablericons.outline.FilePlus
import io.github.lyxnx.compose.ui.tablericons.outline.FileUpload
import io.github.lyxnx.compose.ui.tablericons.outline.FileX
import io.github.lyxnx.compose.ui.tablericons.outline.Filter2X
import io.github.lyxnx.compose.ui.tablericons.outline.Fingerprint
import io.github.lyxnx.compose.ui.tablericons.outline.Flag
import io.github.lyxnx.compose.ui.tablericons.outline.Flask2
import io.github.lyxnx.compose.ui.tablericons.outline.GridDots
import io.github.lyxnx.compose.ui.tablericons.outline.HandClick
import io.github.lyxnx.compose.ui.tablericons.outline.HandFinger
import io.github.lyxnx.compose.ui.tablericons.outline.HandGrab
import io.github.lyxnx.compose.ui.tablericons.outline.HandMove
import io.github.lyxnx.compose.ui.tablericons.outline.HandRingFinger
import io.github.lyxnx.compose.ui.tablericons.outline.HandThreeFingers
import io.github.lyxnx.compose.ui.tablericons.outline.HandTwoFingers
import io.github.lyxnx.compose.ui.tablericons.outline.Hash
import io.github.lyxnx.compose.ui.tablericons.outline.History
import io.github.lyxnx.compose.ui.tablericons.outline.HomeCancel
import io.github.lyxnx.compose.ui.tablericons.outline.HomeCheck
import io.github.lyxnx.compose.ui.tablericons.outline.Infinity2
import io.github.lyxnx.compose.ui.tablericons.outline.Keyboard
import io.github.lyxnx.compose.ui.tablericons.outline.LabelImportant
import io.github.lyxnx.compose.ui.tablericons.outline.Link
import io.github.lyxnx.compose.ui.tablericons.outline.ListDetails
import io.github.lyxnx.compose.ui.tablericons.outline.Lock
import io.github.lyxnx.compose.ui.tablericons.outline.LockOpen
import io.github.lyxnx.compose.ui.tablericons.outline.Logs
import io.github.lyxnx.compose.ui.tablericons.outline.Message
import io.github.lyxnx.compose.ui.tablericons.outline.Message2
import io.github.lyxnx.compose.ui.tablericons.outline.MoodEmpty
import io.github.lyxnx.compose.ui.tablericons.outline.MoodHeart
import io.github.lyxnx.compose.ui.tablericons.outline.Moon
import io.github.lyxnx.compose.ui.tablericons.outline.NavigationCog
import io.github.lyxnx.compose.ui.tablericons.outline.Notes
import io.github.lyxnx.compose.ui.tablericons.outline.Opacity
import io.github.lyxnx.compose.ui.tablericons.outline.Packages
import io.github.lyxnx.compose.ui.tablericons.outline.Palette
import io.github.lyxnx.compose.ui.tablericons.outline.Pencil
import io.github.lyxnx.compose.ui.tablericons.outline.PencilCog
import io.github.lyxnx.compose.ui.tablericons.outline.Photo
import io.github.lyxnx.compose.ui.tablericons.outline.PictureInPictureOn
import io.github.lyxnx.compose.ui.tablericons.outline.Pin
import io.github.lyxnx.compose.ui.tablericons.outline.PlugConnected
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Pointer
import io.github.lyxnx.compose.ui.tablericons.outline.Pointer2
import io.github.lyxnx.compose.ui.tablericons.outline.PointerPause
import io.github.lyxnx.compose.ui.tablericons.outline.PointerUp
import io.github.lyxnx.compose.ui.tablericons.outline.Refresh
import io.github.lyxnx.compose.ui.tablericons.outline.Reload
import io.github.lyxnx.compose.ui.tablericons.outline.RosetteNumber1
import io.github.lyxnx.compose.ui.tablericons.outline.RosetteNumber2
import io.github.lyxnx.compose.ui.tablericons.outline.RosetteNumber3
import io.github.lyxnx.compose.ui.tablericons.outline.RosetteNumber4
import io.github.lyxnx.compose.ui.tablericons.outline.RosetteNumber5
import io.github.lyxnx.compose.ui.tablericons.outline.RosetteNumber6
import io.github.lyxnx.compose.ui.tablericons.outline.RosetteNumber7
import io.github.lyxnx.compose.ui.tablericons.outline.RosetteNumber8
import io.github.lyxnx.compose.ui.tablericons.outline.RosetteNumber9
import io.github.lyxnx.compose.ui.tablericons.outline.SelectAll
import io.github.lyxnx.compose.ui.tablericons.outline.Selector
import io.github.lyxnx.compose.ui.tablericons.outline.Server
import io.github.lyxnx.compose.ui.tablericons.outline.Settings
import io.github.lyxnx.compose.ui.tablericons.outline.Settings2
import io.github.lyxnx.compose.ui.tablericons.outline.SettingsCancel
import io.github.lyxnx.compose.ui.tablericons.outline.SpacingHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.SquareNumber1
import io.github.lyxnx.compose.ui.tablericons.outline.SquareNumber2
import io.github.lyxnx.compose.ui.tablericons.outline.SquareNumber3
import io.github.lyxnx.compose.ui.tablericons.outline.Stack2
import io.github.lyxnx.compose.ui.tablericons.outline.Sun
import io.github.lyxnx.compose.ui.tablericons.outline.SunMoon
import io.github.lyxnx.compose.ui.tablericons.outline.Table
import io.github.lyxnx.compose.ui.tablericons.outline.Terminal2
import io.github.lyxnx.compose.ui.tablericons.outline.TextSize
import io.github.lyxnx.compose.ui.tablericons.outline.ToggleRight
import io.github.lyxnx.compose.ui.tablericons.outline.Tooltip
import io.github.lyxnx.compose.ui.tablericons.outline.Trash
import io.github.lyxnx.compose.ui.tablericons.outline.UserScreen
import io.github.lyxnx.compose.ui.tablericons.outline.UsersGroup
import io.github.lyxnx.compose.ui.tablericons.outline.Versions
import io.github.lyxnx.compose.ui.tablericons.outline.Window
import io.github.lyxnx.compose.ui.tablericons.outline.World
import io.github.lyxnx.compose.ui.tablericons.outline.X
import io.github.lyxnx.compose.ui.tablericons.outline.ZoomIn

// 页面自己的小图标 —— 从 `ActionIcons.kt` 分出来一支：那个文件已经贴着「单文件 ≤ 1000 行」
// 的判据（`docs/architecture/decoupling.md` 判据 4），再加一颗字形就会越线。
// 这里只放**页面骨架**用的图标（顶栏、行尾、行首、浮层菜单、状态标记），
// **动作 → 图标**那张映射表留在 `ActionIcons.kt`。

/**
 * 页面自己的小图标（顶栏返回键 / 行尾箭头 / 「按键绑定」行首 / 浮层菜单两行）—— 基本出自 Tabler。
 *
 *  图标库里没有、要靠 path 画的那几支**不在这里**：见 `DrawnIcons`。这一处只留「名字 → 图标」
 *  的查表（外加 `accentSwatch` 那条按主题挑形状的判断）。
 */
internal object PageIcons {
    /** 顶栏返回键。 */
    val Back: ImageVector get() = TablerIcons.Outline.ArrowLeft

    /**
     * 「按键设置」那一行 / 那一页 —— `settings-2`（两颗滑块的齿轮）。
     *
     *  用户口径指定这一颗：这一页装的是「整个模块怎么跑」的全局项（接口选择、振动、执行提示）。
     *  换掉了原来的 `settings-cog` —— 那颗与「语言自动」那行的 `settings` 太像，两行摆在一起
     *  分不出谁是谁。
     */
    val SettingsCog: ImageVector get() = TablerIcons.Outline.Settings2

    /**
     * 「启用按键绑定」那一行的行首 —— 拨到右的开关（`toggle-right`）：这一行是**模块总闸**。
     *
     *  原先用电源符号，但它与「停用这颗键」那行同形（两处含义不同、长得一样），而且
     *  `power` 在这台机器上还容易被读成「电源键」（模块里恰好有这颗自定义键）。
     *
     * 那一行必须给图标：它紧跟「按键绑定」行，同卡两行都有 `startAction` 标题才对得齐，
     * 只给上面那行的话下面标题会左移一个图标宽。
     */
    val MasterSwitch: ImageVector get() = TablerIcons.Outline.ToggleRight

    /**
     * 「停用这颗键」那行的行首 —— 锁（`lock`）：这颗键不再接管。
     *
     *  不用 `Ban`（那是「无操作」那个动作的行首）、也不用 `Cancel`（那是「这颗键停用了」那枚
     *  行尾标记），同一张菜单 / 同一张网格里同形会串味。
     */
    val KeyLocked: ImageVector get() = TablerIcons.Outline.Lock

    /** 「启用这颗键」那行的行首 —— 开锁（`lock-open`），与 [KeyLocked] 成对。 */
    val KeyUnlocked: ImageVector get() = TablerIcons.Outline.LockOpen

    /**
     * 「这颗键停用了」—— 圆圈 + 叉（`cancel`）。
     *
     *  用在「按键绑定」页顶栏的
     *  尾随小图标：与行首那枚键位数字不同形，一眼能分出「这是第几颗键」与「这颗键现在不接管」。
     *
     *  不用 `Ban`（那是「无操作」那个动作的行首，摆在同一张网格里会串味），也不用
     *  [KeyLocked]（那是「停用这颗键」那**一行**的行首）—— 同一张菜单里同一个字形读两种意思。
     */
    val Cancel: ImageVector get() = TablerIcons.Outline.Cancel

    /**
     * 「关掉这一层」那一颗 —— 光秃秃的叉（`x`）。
     *
     *  与 [Cancel]（圆圈叉）分开用：圆圈叉读作「取消 / 停用」，是**动作的否定**；
     *  全屏预览那种「收起这一屏」要的是一个不带圈的叉，读作「关掉」。
     */
    val Close: ImageVector get() = TablerIcons.Outline.X

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
     * 「前台转小窗」格的行首 —— 画中画（`picture-in-picture-on`）。
     *
     * 与 [SmallWindow] / [TileSmallWindow] 同字形但**各留一份名字**（三处语义不同：入口格 /
     * 「选择应用」菜单项 / 格子上的状态标记），以后要分开改的时候不用先拆共用。
     *  它菜单里那两档仍是自己的字形（展开 = 窗口 + 底部块、收起 = 画中画挂顶，
     * 见 `actionIcon` 里 `zoom:mini` 那两行）。
     */
    val MiniWindow: ImageVector get() = TablerIcons.Outline.PictureInPictureOn

    /**
     * 「打开应用」格绑成**小窗打开**（`window:`）时，标题右边那枚小标记 ——
     * 画中画（`picture-in-picture-on`）。
     *
     *  与行尾那颗右箭头（[EnterArrow]）分工不同：那颗说明「点开进另一个页面」，这颗说明
     *  **这一格绑的是小窗起** —— 这一格只有一行标题，不点开「选择应用」菜单看不出绑的是哪一档。
     *  与 [SmallWindow]（菜单项）/ [MiniWindow]（入口格）同字形，各留一份名字。
     */
    val TileSmallWindow: ImageVector get() = TablerIcons.Outline.PictureInPictureOn

    /**
     * 「用哪条接口接管侧键」—— 首页那行开关的行首图标（Tabler 的 `plug-connected`）。
     *
     *  一根插进插座的线，读作「从哪条口子接进来」；比 `arrows-exchange`（两条箭头）少一层
     * 「来回换」的误读 —— 这里选的是**一条**通道，不是两条之间同步。
     */
    val ColorOsLink: ImageVector get() = TablerIcons.Outline.PlugConnected

    /**
     * 竖三点（`dots-vertical`）—— 顶栏右侧的菜单按钮，也是**行尾溢出菜单**的按钮。
     *
     *  两处同一个字形、同一层意思「这里还有一组动作」：顶栏那颗弹页面菜单（`I18n` 的
     *  `common.menu`「菜单」），「多条命令」步骤行那颗弹上移 / 下移 / 删除（`common.more`「更多」）。
     */
    val Menu: ImageVector get() = TablerIcons.Outline.DotsVertical

    /**
     * 按键菜单里「重命名」那一行 —— `edit`（铅笔压一行字）。
     *
     *  与 [CommandEdit]（`pencil`，光一支铅笔）分开用：那一颗是「编辑这一条命令」，
     *  这一行只改**名字**，压一行字更贴。
     */
    val MenuRename: ImageVector get() = TablerIcons.Outline.Edit

    /** 浮层菜单「打开默认界面」—— 窗口框，读作「另一个界面」。 */
    val MenuStock: ImageVector get() = TablerIcons.Outline.AppWindow

    /**
     * 「固定窗口起点」那一行的行首 —— `window`（一个窗口框）。
     *
     *  原先用 `Click`（一根手指点）：那一颗说的是「单击」这件事，而这一行管的是**收集窗口**
     *  —— 窗口内数到的多击算一次触发。字形跟着「窗口」这件事走，标题读起来才对得上。
     */
    val CollectWindow: ImageVector get() = TablerIcons.Outline.Window

    /** 浮层菜单「重置配置」—— `eraser`（橡皮擦）。 */
    val MenuReset: ImageVector get() = TablerIcons.Outline.Eraser

    /** 浮层菜单「导出日志」—— `logs`（一叠日志行）。 */
    val MenuExport: ImageVector get() = TablerIcons.Outline.Logs

    /**
     * 清单里「第几条」—— 按它在这一列里的位置取**花结数字**：`rosette-number-1` … `rosette-number-9`
     * （[index] 从 0 起）。
     *
     *  两个调用点共用这一支笔：首页「按键」卡里**收集到的键**（上限五颗，
     *  `KeyId.MAX_CUSTOM_KEYS`）、以及「组合键」列表里的**第几组**（上限九组，
     *  `MAX_COMBOS`）。数字跟着**列表位置**走，删掉中间那条之后后面的各自前移一位 ——
     *  与清单里看到的顺序一致。
     *
     *  用**带轮廓**的数字而不是裸数字（`number-N`）：裸数字在 22dp 上只剩几根细笔画、外面没有任何
     *  轮廓，与旁边那些有外框的字形（手机 + 侧键、加号）不是一个体量，一行里看着像掉了个字。
     *  花结那圈花瓣给它一个与别的图标同样的外轮廓，这一排才读得成同一组条目。
     *
     *  只到 9：图标库的花结数字正好到这里，而两个上限都没越过它（五颗键 / 九组组合）——
     *  越界兜到 9 就说明上限判据与这排数字又各说了一个数，**不要在这里手绘第 10 档**。
     */
    fun menuKeyNumber(index: Int): ImageVector =
        when (index) {
            0 -> TablerIcons.Outline.RosetteNumber1
            1 -> TablerIcons.Outline.RosetteNumber2
            2 -> TablerIcons.Outline.RosetteNumber3
            3 -> TablerIcons.Outline.RosetteNumber4
            4 -> TablerIcons.Outline.RosetteNumber5
            5 -> TablerIcons.Outline.RosetteNumber6
            6 -> TablerIcons.Outline.RosetteNumber7
            7 -> TablerIcons.Outline.RosetteNumber8
            else -> TablerIcons.Outline.RosetteNumber9
        }

    /**
     * 「按键功能」页「按键列表」末行「添加按键」—— `plus`（加号）。
     *
     *  这一行是**往清单里再加一颗**（弹收集弹窗），加号直接说这件事；
     *  收集到的那几行用 [menuKeyNumber]，两者不共用同一颗字形。
     */
    val MenuKeyAdd: ImageVector get() = TablerIcons.Outline.Plus

    /**
     * 首页「按键操作」卡那一行「按键列表」（指向按键列表页）—— `list-details`（一份带条目的清单）。
     *
     *  用户口径指定这一颗：那一行说的是「点进去看清单」（按键列表 + 组合按键），
     *  清单字形比手指更贴 —— 手指说的是「按下」，而这一行是「去哪儿」。
     *
     *  页内那两处（列表的空态行、设置卡的行首）用全项目通用那颗空态字形
     *  （[NoMatch]，`mood-empty`）—— 那里说的是「这一类东西 / 还没配」，不是「按键」。
     */
    val MenuKeyList: ImageVector get() = TablerIcons.Outline.ListDetails

    /**
     * 场景块里那三档动作行的行首 —— **一档一颗手势字形**（用户指定）：
     * 单击 `hand-finger`（一根手指指点）、双击 `hand-two-fingers`（两根手指）、
     * 长按 `hand-click`（按下那一下）。
     *
     *  这一列原先画的是**动作自己的字形**：三行扫下来分不出哪一行是哪一档，只剩标题里
     *  那两个字能区分。手势是这一行的骨架 —— 换成手势字形之后，一列看下去先认出
     *  「这一行是哪一档」，再看它右边写的是什么。
     *
     *  与 [MenuKeyList] 不同名也不同形：那一颗说的是「按键列表」这个去处。
     */
    fun gestureIcon(gesture: Gesture): ImageVector =
        when (gesture) {
            Gesture.SINGLE -> TablerIcons.Outline.HandFinger
            Gesture.DOUBLE -> TablerIcons.Outline.HandTwoFingers
            Gesture.LONG -> TablerIcons.Outline.HandClick
        }

    /**
     * 一档**触发条件**的行首字形 —— 场景块的动作行与「场景动作」页的手势带都按它画。
     *
     *  三档沿用原来那三颗（一根手指 / 两根手指 / 按下那一下）；第 3 击起那几档与双击**共用**
     *  「两根手指」—— 连击本来就是「多按几下」，波形上没有新东西可画。哪一档由标题里那两个字
     *  （「三击 | 手电筒」）说，与其它行「左边图标管去处、右边文字管具体」的分工一致。
     */
    fun gestureIcon(trigger: GestureTrigger): ImageVector =
        when (trigger) {
            GestureTrigger.LongPress -> {
                TablerIcons.Outline.HandClick
            }

            //  松手那一档不单独占一行（它是长按的收尾，见 `GestureTrigger.Release`）
            GestureTrigger.Release -> {
                TablerIcons.Outline.HandClick
            }

            //  「单击后长按」用「移动的手」（`hand-move`，用户指定）：它说的是「两段之间换了形态」，
            //  与前几档的「几根手指」不是一个轴，换一颗字形才区分得开。
            GestureTrigger.ClickThenLong -> {
                TablerIcons.Outline.HandMove
            }

            //  这一档的松手不单独占一行（与 `Release` 同一个道理：它是那一档的收尾）
            GestureTrigger.ClickThenLongRelease -> {
                TablerIcons.Outline.HandMove
            }

            is GestureTrigger.Click -> {
                when (trigger.count) {
                    1 -> TablerIcons.Outline.HandFinger

                    2 -> TablerIcons.Outline.HandTwoFingers

                    3 -> TablerIcons.Outline.HandThreeFingers

                    //  四击起用「无名指」（`hand-ring-finger`，用户指定）——
                    //  Tabler 没有四根手指的字形，这一颗是那套里最接近「又一个具体手指」的
                    else -> TablerIcons.Outline.HandRingFinger
                }
            }
        }

    /**
     * 首页「其他 → 更多选项」那一行的行首 —— 圈里三点（`dots-circle-horizontal`，
     * 用户指定；先试过 `table-options`，换回这颗）。
     *
     * 圈里三点（Tabler 的 `dots-circle-horizontal`，网格入口格原先也用这颗）：读作「还有更多」，
     * 而这一行是「进另一个页面」（行尾另有 [EnterArrow] 说明这件事），不是弹菜单。
     */
    val MoreOptions: ImageVector get() = TablerIcons.Outline.DotsCircleHorizontal

    /**
     * 「反馈问题」菜单三行的字形 —— 菜单里三行都是「一个去处」，只有各自的字形能一眼分清。
     *
     *  「群」没有品牌字形，用「一群人」（`users-group`）；另两支是品牌字形。三支各留一份名字。
     */
    val FeedbackGroup: ImageVector get() = TablerIcons.Outline.UsersGroup

    val BrandTelegram: ImageVector get() = TablerIcons.Outline.BrandTelegram

    val BrandGithub: ImageVector get() = TablerIcons.Outline.BrandGithub

    /**
     * 「捐赠打赏」那一行的行首 —— `mood-heart`（笑脸 + 爱心）。
     *
     *  与 [Language] 那一族分开：同卡相邻几行各是各的语义，共用字形会分不出哪行是哪行。
     *  菜单里那一项（[DonateList]，名单页）是另一颗字形 —— 那一行说的是「谢谢谁」，不是「怎么给」。
     */
    val Donate: ImageVector get() = TablerIcons.Outline.MoodHeart

    /**
     * 菜单最后那一行（进打赏名单，叫「捐赠列表」）的行首，也是名单页空态那一行的行首 ——
     * `table`（一张带行列线的表格，用户指定）。
     *
     *  那一页就是**一张两列的表**（昵称、金额），表格比一页纸更贴「点进去会看到什么」。
     *  与 [Donate] 那颗心分开：那颗管「怎么给」，这一颗管「谢谢谁」。
     */
    val DonateList: ImageVector get() = TablerIcons.Outline.Table

    /**
     * 捐赠菜单三行的品牌字形 —— 菜单里三行都是「一个去处」，只有品牌图能一眼分清。
     *
     *  三支同族、各留一份名字（以后要分开改时不用先拆共用）。「作者」菜单里酷安那一行的字形
     *  **不在这张表里**：图标库没有它的品牌字形，那颗是手绘的（见 `DrawnIcons.BrandCoolapk`）。
     */
    val BrandQq: ImageVector get() = TablerIcons.Outline.BrandQq

    val BrandWechat: ImageVector get() = TablerIcons.Outline.BrandWechat

    val BrandAlipay: ImageVector get() = TablerIcons.Outline.BrandAlipay

    /** 「更多选项」页「语言」—— `world`（地球：用地球表示语言）。 */
    val Language: ImageVector get() = TablerIcons.Outline.World

    /** 「更多选项」页「系统配色」—— `palette`（这一行改的是配色）。 */
    val ThemeMonet: ImageVector get() = TablerIcons.Outline.Palette

    /**
     * 强调色那一行的色块 —— **按主题选**（色块本身是手绘的，见 `PaletteIcons`）：
     *  · Monet：四格色阶（[PaletteIcons.Swatch]）—— 那一档是系统按壁纸生成**一整套**色，
     *    色块要读得出「一整套」，四档透明度正好是同一套色的色阶；
     *  · 其余主题档：**一个整块**（[PaletteIcons.Block]）—— 那一档只有「强调色」这一个颜色被换掉
     *    （底子仍是 COUI 的），画成四格会读成「四块互不相干的色」。
     *
     *  当前生效的那一行行首**换成对勾**（见 `PageMenuPopup.kt` 的 `MenuRow`），但颜色仍是这一套的色
     *  —— 「是哪一套色」由对勾的颜色给，不靠那块形状。
     */
    fun accentSwatch(theme: AppTheme): ImageVector = if (theme == AppTheme.MONET) PaletteIcons.Swatch else PaletteIcons.Block

    /** 「外观」卡「页面缩放」—— `zoom-in`（放大镜加号：这一行是缩放）。 */
    val ZoomIn: ImageVector get() = TablerIcons.Outline.ZoomIn

    /** 「外观」卡「跟随系统显示大小」—— `text-size`（大小 A→A）。 */
    val TextSize: ImageVector get() = TablerIcons.Outline.TextSize

    /** 「更多选项」页「界面颜色」—— `sun-moon`（一半日一半月：深浅由这一行定）。 */
    val Theme: ImageVector get() = TablerIcons.Outline.SunMoon

    // 「界面颜色」菜单里那三项各自的字形 —— 与语言那三颗同一个道理：共用一颗的话菜单三行长得一样。

    // 「随机图标配色」那一行 —— 一块色板（`ColorSwatch`），与强调色那颗色块区分开
    val RandomIconTint: ImageVector get() = TablerIcons.Outline.ColorSwatch

    /**
     * 「跟随系统」—— `contrast`（半黑半白的圆：明暗随系统那一档）。
     *
     *  原先用 `brightness-auto`（一个「A」+ 太阳），那颗读作「自动亮度」而不是「界面颜色
     *  跟随系统」—— 与浅色 `sun` / 深色 `moon` 摆在一起时，它是四档里唯一不像明暗的那颗。
     */
    val ThemeAuto: ImageVector get() = TablerIcons.Outline.Contrast

    /** 「浅色」—— `sun`。 */
    val ThemeLight: ImageVector get() = TablerIcons.Outline.Sun

    /** 「深色」—— `moon`。 */
    val ThemeDark: ImageVector get() = TablerIcons.Outline.Moon

    // 语言菜单里那三项各自的字形 —— **不共用上面那颗地球**：共用的话菜单里三行看起来一模一样，
    //  行首这一颗是唯一能一眼分出「跟随系统 / 中文 / English」的东西。

    /** 「跟随系统」—— `settings`（齿轮：这份交给系统定）。 */
    val LanguageAuto: ImageVector get() = TablerIcons.Outline.Settings

    /** 「简体中文」—— `alphabet-chinese`（汉字「文」）。 */
    val LanguageChinese: ImageVector get() = TablerIcons.Outline.AlphabetChinese

    /** 「English」—— `abc`（拉丁字母）。 */
    val LanguageEnglish: ImageVector get() = TablerIcons.Outline.Abc

    /** 「更多选项」页「导出配置」—— `file-upload`（文件 + 向上的箭头）：把这份配置交出去。 */
    val ConfigExport: ImageVector get() = TablerIcons.Outline.FileUpload

    /** 「更多选项」页「导入配置」—— `file-download`（文件 + 向下的箭头）：把一份文件收进来。 */
    val ConfigImport: ImageVector get() = TablerIcons.Outline.FileDownload

    /** 「选择应用」页顶栏菜单的「显示系统应用」—— `GridDots`（点阵格）。 */
    val SystemApps: ImageVector get() = TablerIcons.Outline.GridDots

    /**
     * 「选择应用」页顶栏菜单的「显示失效应用」—— `filter-2-x`（漏斗 + 一个叉）。
     *
     *  这一档是**纯显示过滤**（默认关，只在「打开应用」那一趟出现）：漏斗说的是「这是个筛子」，
     *  叉说的是「筛出来的是点不开的那批」—— 比原先框里一个叉更贴「过滤」这件事。
     */
    val ShowUnopenable: ImageVector get() = TablerIcons.Outline.Filter2X

    /**
     * 「应用控制」那一格绑成**打开**那一档时格子里的字形 —— `category-plus`。
     *
     *  与 [AppStop] 配成一对：打开 / 结束在网格里靠这两颗分开，与菜单里那两行同一对
     *  （同一族图标，见 [ActionIcons] 里那三支）。绑的是某个界面或快捷方式时也用这一对
     *  （用户口径：格子里不按组件 / 快捷方式换字形，只用菜单那两个状态的图标）。
     */
    val AppOpen: ImageVector get() = TablerIcons.Outline.CategoryPlus

    /**
     * 「选择应用」页顶栏那颗**「以小窗口打开」开关** —— 画中画（`picture-in-picture-on`）。
     *
     *  与 [TileSmallWindow] / [MiniWindow] 同字形但**各留一份名字**（三处语义不同：顶栏开关 /
     * 格子上的状态标记 / 入口格），以后要分开改的时候不用先拆共用。
     */
    val SmallWindow: ImageVector get() = TablerIcons.Outline.PictureInPictureOn

    /**
     * 「选择应用」页 ⋮ 菜单里**「使用原版动画」**那一行的行首 —— `opacity`（半透明的圆，用户口径）。
     *
     *  与 [SmallWindow] 各留一份名字：两档互斥、语义各是一回事（一个是「窗口怎么来」，
     *  一个是「这一次启动套不套原版那层转场」），以后要分开改不用先拆共用。
     *
     *  **这颗字形到 tabler 3.50 才进**（3.49 里没有，版本下限见 `app/build.gradle.kts` 那一档）；
     *  它画的是「呈现 / 透明度」那一层，比转场箭头更贴这一档的意思：它不是另一种打开方式，
     *  只决定这次启动外面套不套那层动画。
     */
    val StockAnim: ImageVector get() = TablerIcons.Outline.Opacity

    /** 「选择应用」空状态（清单本身为空：系统限制了可见列表）—— `Packages`（一摞盒子）。 */
    val EmptyApps: ImageVector get() = TablerIcons.Outline.Packages

    /** 「选择应用」空状态（搜索无结果）—— `MoodEmpty`（不用划掉的放大镜：斜杠与 `Ban` 太像）。 */
    val NoMatch: ImageVector get() = TablerIcons.Outline.MoodEmpty

    /** 「自定义命令」页里命令行尾的删除图标 —— `Trash`，用 `error` 色，调用点给 20dp。 */
    val CommandDelete: ImageVector get() = TablerIcons.Outline.Trash

    /**
     * 命令类型「单条命令」的**兜底底图** —— `Terminal2`（带框的终端）。
     *
     *  命令卡 / 网格格子 / 步骤行那几处的底图现在按**命令自己那种写法**取（`shell:` → [FormatShell]、
     *  `notify:` → [FormatNotify] …，见 `commandIcon`），这一颗只在写法认不出时兜底。
     *  以 root 执行不再换整颗字 —— 改成右下角一颗井号角标（[CommandRoot] 那颗字形拼上去）。
     */
    val CommandSingle: ImageVector get() = TablerIcons.Outline.Terminal2

    /**
     * 「以 root 执行」—— `Hash`（井号 = root 的提示符）。
     *
     *  两处说的是同一件事「这条命令换个身份跑」：命令设置里那张开关行的行首（这一处仍是整颗），
     *  以及命令卡右下角那颗**角标**（字形取自这里，拼接见 `commandIcon` → `badgedIcon`）。
     *
     *  为什么是井号：shell 里普通用户是 `$`、root 是 `#`，这颗字形就是那个提示符本身
     *  （superuser 的经典图腾也是它），比任何「提权 / 权限」的转喻都直接。
     *  与 [CommandSingle] 的终端框也一眼分得开：一个是**提示符**，一个是**命令框**。
     */
    val CommandRoot: ImageVector get() = TablerIcons.Outline.Hash

    /**
     * 「系统场景」（条件执行）那条命令的类型图标 —— `adjustments-alt`（三根竖着的滑杆）。
     *
     *  与「多条命令」那族 `box-multiple-N` 区分开：那是一串盒子（按顺序走完），这是「按情境调」——
     *  竖滑杆读作「几个可调的量」，与 [Scene] 的 `adjustments`（横滑杆）同一族、换了个轴：
     *  同一张页里这两颗一上一下（分岔这件事 / 具体某个场景），同族不同轴，既分得开又看得出是一家。
     */
    val CommandBranch: ImageVector get() = TablerIcons.Outline.AdjustmentsAlt

    /**
     * **场景**（界面上原来叫「条件」）—— `adjustments-alt`（三根竖着的滑杆），与 [CommandBranch] 同一颗。
     *
     *  两处说的本来就是一件事：分岔是「按情境走哪一支」这个**类型**的记号（规则表空态、表尾注脚），
     *  场景是它**具体那一格**（卡片里「未选择场景」那一行、菜单里指到场景本身的几处、
     *  按键 `⋮` 菜单里那一行）。同族同轴，摆在同一张页里不会串味。
     */
    val Scene: ImageVector get() = TablerIcons.Outline.AdjustmentsAlt

    /** **多配置** —— `stack-2`（一叠纸）：切换就是抽哪一张。 */
    val Profiles: ImageVector get() = TablerIcons.Outline.Stack2

    /**
     * **只参与组合** —— `arrows-double-ne-sw`（一支朝东北、一支朝西南的双向斜箭头）。
     *
     *  双向是这一位的要害：单键手势交还系统、模块只用它凑组合 —— 这颗键**两头都有去处**，
     *  不是「坏了」也不是「只剩一半」。
     *
     *  原先与 [Profiles] 共用 `stack-2`（两枚叠一起），理由是「叠在一起 = 要跟另一颗一起按」；
     *  但用户口径换成了这一颗 —— 同时 `stack-2` 在 [Profiles] 那里本来就指「多份配置叠在一起」，
     *  两处共用一颗字，早先只是碰巧读得通。
     */
    val ComboOnly: ImageVector get() = TablerIcons.Outline.ArrowsDoubleNeSw

    //  「按法」那三档（`ComboKey.windowOn` / `holdOn` 那一族）：同一档手势下这三种按法各有一套动作，
    //  选择器里每行一颗。字形取「这三种按法各自要害在哪」——
    //
    //   · 等窗口 → `clock-hour-4`：这条判据量的是**时间**（两颗键之间最多隔多久）；
    //   · 先按住第一颗 → `hand-grab`：**抓着不放**，另一颗是后补的；
    //   · 先按住第二颗 → `hand-click`：同一个动作换个起点，字形与上一颗同一族（都是手），
    //     只差「抓」与「点」—— 两行并排看得出是同一件事的两种顺序，而不是两件不相干的事。
    //
    //  不复用下面的 [DelayHold] / [DelayStep]：那两颗是「延时」那一族（长按延迟、连发间隔），
    //  与按法分属两处设置，共用字会让两个概念在界面上长得一样。

    /**
     * **等窗口** —— 两颗键在等待窗口内先后按下（见 `ComboKey.windowOn`）。
     *
     *  同一个字形也作公共设置那颗「使用等待手势」开关的图标：那一位管的正是这种按法认不认。
     */
    val HoldWindow: ImageVector get() = TablerIcons.Outline.ClockHour4

    /** **先按住第一颗**，再按剩下的 —— 按住手势的第一种顺序。 */
    val HoldFirst: ImageVector get() = TablerIcons.Outline.HandGrab

    /** **先按住第二颗**，再按剩下的 —— 按住手势的第二种顺序。 */
    val HoldSecond: ImageVector get() = TablerIcons.Outline.HandClick

    /**
     * **按法**（组合动作页右下角那颗 FAB）—— hand-click：点在「先按住哪一颗」这件事上。
     *
     *  与 [HoldSecond] 同一颗字形，但**意图不同**：那个是「先按住第二颗」这一种顺序，
     *  这个是「换一种按法」这个动作 —— 将来任一颗要换字形时不必连带另一颗。
     */
    val PressMode: ImageVector get() = TablerIcons.Outline.HandClick

    /** **新建配置** —— `file-plus`：加一份。 */
    val ProfileAdd: ImageVector get() = TablerIcons.Outline.FilePlus

    //  配置菜单里**对某一份动手**的那两行 —— 重置 / 删除，与 [ProfileAdd] 同一套形状
    //  （一张纸 + 中间那一笔）：摆在一起读得出「这一组改的都是同一份配置」，只差中间那一笔。
    //
    //  「重命名」不在这套形状里：全项目所有「改名」都用 [MenuRename]（`edit`）—— 按键改名、
    //  常用命令改名、配置改名是同一个动作，同一个记号（用户口径「重命名图标统一用重命名按键的那个」）。
    //  原先这里有一颗 `file-pencil`（[ProfileRename]），与按键菜单那颗并存，同一件事长出两种长相。

    /** **重置配置** —— `file-minus`：把这一份的内容清空（纸还在，内容没了）。 */
    val ProfileReset: ImageVector get() = TablerIcons.Outline.FileMinus

    /** **删除配置** —— `file-x`：整份删掉。 */
    val ProfileDelete: ImageVector get() = TablerIcons.Outline.FileX

    /**
     * 分叉条件「某个应用在前台」—— `apps`（九宫格）。
     *
     *  与 [CommandBranch] 分开：「选择条件」那张菜单里每种条件一行一颗，`adjustments-alt` 是
     *  「这一支怎么走」这件事的记号，不是「哪个应用」；同一张菜单里既当行首又当条件名会读混。
     */
    val AppFront: ImageVector get() = TablerIcons.Outline.Apps

    /**
     * 「按键绑定」页右下角那颗 FAB —— 进「自定义命令」页新增一条命令（`Plus`）。
     *
     * 与 [StepAdd] 同字形但语义不同（新增命令 / 给多条命令加一步），各留一个名字。
     */
    val CommandAdd: ImageVector get() = TablerIcons.Outline.Plus

    /**
     * 「自定义命令」那两处入口的行首 —— `code-plus`（代码符号 + 加号）。
     *
     *  两处：首页「其他」卡那一行、以及「按键绑定」页那块同名入口卡。
     *  与 [CommandAdd]（纯加号）分开：[CommandAdd] 是「**加一条**」这个动作（FAB / 加一步 /
     *  添加分支），这一颗是「**那一页**」这个去处 —— 入口行的图标该表示目的地，不是动作。
     */
    val CommandCode: ImageVector get() = TablerIcons.Outline.CodePlus

    /**
     * 「显示常用命令」那一行 —— `eye-cog`（一只眼睛 + 齿轮，用户指定）。
     *
     *  说的是「这一段**露不露**」：眼睛管「看得见」，齿轮点出「露出来之后长什么样 / 能配什么」。
     *  与下面「管理常用命令」那一行（[CommonCommandsManage]，`notes`）分开 ——
     *  同一张卡相邻两行，一个开关一个去处，字形不重。
     */
    val CommonCommands: ImageVector get() = TablerIcons.Outline.EyeCog

    /**
     * 「管理常用命令」那一行 —— `notes`（一页写满字的纸，用户指定，原名单页那一行用的字形）。
     *
     *  说的是「清单里有哪几条、哪些勾着要出现」：这一行点开就是一整页清单，一页纸正好是它。
     *  与 [CommonCommands]（`eye-cog`，管露不露）分工清楚 —— 一个开关一个去处，字形不重。
     */
    val CommonCommandsManage: ImageVector get() = TablerIcons.Outline.Notes

    /**
     * 「固定显示在按键绑定页」—— `pin`（一枚图钉）。
     *
     *  这一项**不再是开关**：常用命令一律出现在按键绑定页那段里（用户口径「改成固定」），
     *  所以字形要表达的是「钉住了」而不是「可以选择要不要」。原先借的是 [CommonCommands]
     *  那颗 `eye-cog`，那是「这一段露不露」的开关字形，用在一个不可切换的结论上会让人去点它。
     */
    val PinnedInBinding: ImageVector get() = TablerIcons.Outline.Pin

    /**
     * 「图标」那一行的行首 —— `photo`（用户指定）。
     *
     *  说的是「这条命令的胶囊左边画哪一张图」：点开就是一格一格的图形可选，一颗照片位正好是它。
     *  与 [Click]（那颗「点一下」的指针，原先借来用）分开：那颗讲的是**手势**，不是**图**。
     */
    val FluidIcon: ImageVector get() = TablerIcons.Outline.Photo

    /** 「步骤」卡片里「添加步骤」那一行的行首图标。 */
    val StepAdd: ImageVector get() = TablerIcons.Outline.Plus

    // ── 「支持的命令格式」卡里那几行的行首 —— 一行一颗，互不相同 ──
    //
    //  这一列是「能写什么」的图例，所以每颗都要能一眼对上它那一行的说明：终端 / 键盘 / 发出去 /
    //  通知 / 胶囊 / 提示 / 快捷方式。**不与 [CommandSingle] 抢**：那颗是「命令」这个整体（输入框行首），
    //  这里是它的几种写法 —— 同一个字形摆在几行里的第一行，读起来像「只有 shell 是命令」。

    /** `shell:<命令>` —— `terminal-2`（终端窗口），与网格里 shell 那一格同源（见 `actionIcon`）。 */
    val FormatShell: ImageVector get() = TablerIcons.Outline.Terminal2

    /**
     * `keyevent:<按键>` —— `pointer-2`（一只点下去的手指），「模拟按键」就是按一下。
     *
     *  不用 `keyboard` 那张键盘：那一条讲的是「敲一个键码」，键盘整块的形状在这一列里与
     *  `shell:` 的终端框、`notify:` 的气泡挤在一起时读不出区别；一只手指点下去更直接。
     */
    val FormatKeyEvent: ImageVector get() = TablerIcons.Outline.Pointer2

    /**
     * `intent:<Action>` —— `app-window`（一块界面）。
     *
     *  与网格里 intent 那一格同源（`ActionIcons` 那一支用的是同一颗）：intent 的结果就是「打开
     *  另一个界面」，比一只纸飞机更贴这行说明。
     */
    val FormatIntent: ImageVector get() = TablerIcons.Outline.AppWindow

    /** `notify:<正文>` —— `message`（一条消息）：「通知中心里会留下一条」的那件事。 */
    val FormatNotify: ImageVector get() = TablerIcons.Outline.Message

    /** `fluid:<正文>` —— `capsule-horizontal`（横放的胶囊）：流体云在状态栏上就是那么一颗。 */
    val FormatFluid: ImageVector get() = TablerIcons.Outline.CapsuleHorizontal

    /**
     * `toast:<正文>` —— `tooltip`（带小尾巴的气泡，浮在内容上方）。
     *
     *  与 [FormatNotify] 的 `message`（一条消息）刻意区分：那条是「落到通知中心里的一条」，
     *  这条是**浮在屏幕上、说完就没**的一句话 —— 一颗没有尾巴的气泡读起来与消息那条太像。
     */
    val FormatToast: ImageVector get() = TablerIcons.Outline.Tooltip

    /**
     * `shortcut:<ID>` —— `external-link`（一个方框 + 外跳箭头），网格里快捷方式那一格也用这颗。
     *
     * ## 为什么不是链条（[Link]）
     *
     *  `link` 在本项目里已经是**「跳外部链接」**那颗（关于页那几行菜单的行尾，见 `CardRow` /
     *  `MenuPopup` 的约定）。拿它表示快捷方式，等于把「一个网址」和「桌面上钉好的那个入口」
     *  画成同一张脸 —— 而这一列里 `intent:` 已经是「跳出去」的意思了，再来一个链环只会更糊。
     *
     * ## 为什么不是图钉
     *
     *  `pin` 在图标语言里主要是**「置顶 / 固定住」**（把东西钉在当前位置），而这一格要表达的是
     *  **「触发别处那个入口」** —— 主语是「那个入口」，不是「固定」这个动作。
     *  而且这个模块里「固定」已经有自己的位置（场景页那颗「松手沿用按下时的场景」，见 `ReleasePin`），
     *  同一屏出现两种「钉」的读法不划算。
     *
     *  `external-link` 的形状（方框 + 外跳箭头）正好是**「从这里出去，到另一个入口」**，
     *  与 [FormatIntent] 的「一块界面」也分得开：那个是**界面本身**，这个是**跳过去的那个动作**。
     */
    val FormatShortcut: ImageVector get() = TablerIcons.Outline.ExternalLink

    /** 「自定义命令」卡片右上角 action 按钮 —— `Flask2`（锥形瓶），读作「把这条试跑一遍」。 */
    val TestCommand: ImageVector get() = TablerIcons.Outline.Flask2

    /** 步骤行尾的「上移」/「下移」—— 竖人字。 */
    val StepUp: ImageVector get() = TablerIcons.Outline.ChevronUp

    val StepDown: ImageVector get() = TablerIcons.Outline.ChevronDown

    /** 编辑用铅笔 —— `Pencil`（编辑最不会误读的字形）。 */
    val CommandEdit: ImageVector get() = TablerIcons.Outline.Pencil

    /**
     * 「调整步骤顺序」那颗 FAB 的铅笔 —— `PencilCog`（铅笔带齿轮）。
     *
     *  与 [CommandEdit] 分开：这一颗说的是「调这一条命令的**结构**」（挪步骤 / 删步骤），
     *  菜单里那支铅笔说的是「改这条命令的内容」。同一个字形在同一个页面上读两种意思会混。
     */
    val CommandEditCog: ImageVector get() = TablerIcons.Outline.PencilCog

    /** 「振动反馈」行的行首 —— `DeviceMobileVibration`（手机 + 两道声波）。 */
    val Vibrate: ImageVector get() = TablerIcons.Outline.DeviceMobileVibration

    /**
     * 「振动强度」滑杆的行首 —— **档位数字**（`SquareNumber1/2/3`）。
     *
     *  行尾不再写「弱 / 中 / 强」，档位全靠这一枚图标：方框里那个数字就是第几档，
     *  一眼能看出「一共三档、现在第几档」—— 比一行字省地方，也不用再翻四份字典。
     *
     * @param level [GlobalConfig.vibrateStrength] 的取值；越界按中档（与执行侧的钳制同口径）
     */
    fun vibrateLevel(level: Int): ImageVector =
        when (level) {
            0 -> TablerIcons.Outline.SquareNumber1
            2 -> TablerIcons.Outline.SquareNumber3
            else -> TablerIcons.Outline.SquareNumber2
        }

    /** 「版本」行的行首 —— `versions`（版本 / 分支符号，比通用信息符号更具体）。 */
    val Versions: ImageVector get() = TablerIcons.Outline.Versions

    /**
     * 「版本」菜单里**检查更新**那一行 —— `cloud-search`（云 + 放大镜）：去远端问一句有没有新版。
     *
     *  不能借用 [ModuleRestart]（`reload`，回转箭头 + 竖线）：那一颗是首页顶栏的「重启模块」，
     *  两颗同形会被读成同一个动作。这一颗说的是「去远端查一下」，与「重启」分得开。
     */
    val CheckUpdate: ImageVector get() = TablerIcons.Outline.CloudSearch

    /**
     * 「版本」菜单里**已经查到新版**时那一行 —— `download`（向下的箭头落进托盘）。
     *
     *  替掉平时那颗 [CheckUpdate]：那时这一行的意思已经变成「有新东西，
     *  下一步是去拿」，再挂一个「查一遍」的图标就与它自己那句话对不上。
     */
    val Download: ImageVector get() = TablerIcons.Outline.Download

    /**
     * 「签名」行的行首 —— `fingerprint`（指纹）。
     *
     *  「签名」在这里指的就是**指纹**：给用户拿去和官方发布页上那串对一遍（见 `core/seal/Seal`
     *  的 `ownFingerprint`）。指纹字形比证书 / 印章更贴这件事本身。
     */
    val Signature: ImageVector get() = TablerIcons.Outline.Fingerprint

    /** 「执行时提示」行的行首 —— `tooltip`（气泡提示）。 */
    val CommandToast: ImageVector get() = TablerIcons.Outline.Tooltip

    /**
     * 「提示文案」行的行首 —— `align-left`（四条参差的横线）。
     *
     *  那一行改的是**这句话怎么写**，不是「弹不弹」：与上面那颗气泡分开，一眼才看得出
     *  「这一行是文字设置」。不用 `notes` / `pencil`：前者是捐赠名单那颗，后者是「编辑命令」。
     */
    val ToastTemplate: ImageVector get() = TablerIcons.Outline.AlignLeft

    /**
     * 提示条的前置图标**默认档** —— `alert-circle`（圈里一个感叹号）。
     *
     *  提示条上的图标是「这句话是什么性质」的记号，不是每条都要单独挑一颗：调用方不指定时
     *  一律用它（见 `PageSnackbar.HintCapsule`）。圈里感叹号读作「告诉你一件事」，比打勾 / 打叉
     *  中性 —— 绝大多数回执（已保存、已删除、已切换）本来就没有成败可言。
     */
    val HintAlert: ImageVector get() = TablerIcons.Outline.AlertCircle

    /**
     * 「名称」这一类输入框的行首图标 —— `label-important`（标签）。
     *
     *  两个调用点：自定义命令页那个「名称」、以及「添加按键」弹窗里给新键起名的那一格
     *  —— 填的都是**一个名字**，用同一颗字形，用户在两处看到的是同一件事。
     */
    val LabelImportant: ImageVector get() = TablerIcons.Outline.LabelImportant

    /**
     * 变量表单里「初始值」那一格的行首图标 —— `flag`（一面起点旗）。
     *
     *  这一格填的是「这个变量**一开始**是什么」，旗读作「起点」，与同排另外两格分得开
     *  （名称是标签、类型是 `abc` / `123` / 开关）。
     *
     *  原先借用的是变量自己的字形（`variable`）：与列表页每一行行首那颗**一模一样**，
     *  抽屉里看就成了「又是一行变量」，看不出这一格是在填值。
     */
    val VarInitial: ImageVector get() = TablerIcons.Outline.Flag

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

    /**
     * 「关于」卡里「反馈与交流」那一行的行首 —— `message-2`（对话气泡）。
     *
     *  这一行不再是「报 bug」那一件事：点开是**三个去处**（issue / 群 / 频道），
     *  底下既有反馈也有交流，所以用**对话气泡**而不是 `bug`。
     */
    val Feedback: ImageVector get() = TablerIcons.Outline.Message2

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
     *  摆在同行里明显两种风格。选中态见 [TileChecked]；两态之间的颜色走 `BindingPagerScreen` 的
     *  `entryIndicatorColor`（本来就是 `animateColorAsState`，是渐变过去的）。
     */
    val TileUnchecked: ImageVector get() = TablerIcons.Outline.Circle

    /** 同上，选中态 —— **实心**的 `circle-check`（实心圆里一个勾；与上面那枚空心的一实一虚成对）。 */
    val TileChecked: ImageVector get() = TablerIcons.Filled.CircleCheck

    /**
     * **空心**的圆圈勾（`circle-check`）—— 与 [TileChecked] 同一个字形、不填底。
     *
     *  给「压在整块色底上、面积很大」的那一处（首页状态卡的水印）：实心版放到 112dp 上是一整块
     *  实心圆，读起来像一块色斑；空心版只有一圈线，压在底色上才像水印。
     */
    val CircleCheckOutline: ImageVector get() = TablerIcons.Outline.CircleCheck

    /**
     * 「应用控制」那一格绑成**结束**那一档时格子里的字形 —— `category-minus`。
     *
     *  与 [AppOpen] 同一族：打开与结束两档在网格里靠这一颗分开，
     *  只看应用名是分不出来的（两档写的是同一个应用）。
     */
    val AppStop: ImageVector get() = TablerIcons.Outline.CategoryMinus

    /** 命令编辑页底部工具栏的主操作（「保存 / 修改」）—— `Check`，主次靠颜色分（主操作走 `primary`）。 */
    val Confirm: ImageVector get() = TablerIcons.Outline.Check

    /**
     * 「按键功能」页编辑态顶栏那颗「全选」—— `select-all`（四角括号 + 一条斜线）。
     *
     *  与 [Confirm] 分开：全选是「一次勾满一列」，不是「这一趟确认完了」——
     *  用同一颗对勾会把两种意思混成一个。
     */
    val SelectAll: ImageVector get() = TablerIcons.Outline.SelectAll

    /**
     * 浮层菜单里「这一档就是当前值」的对勾 —— 画在**行首**（选中那一行的图标位换成它）。
     *
     *  以前它在**行尾**（补丁 `0003` 给 `DropdownItem` 开的 `checkIcon` 覆盖口）：那一格是恒画的
     *  —— 一颗 24dp 字形 + 起始间距，未选中时只是画成透明，**布局上照样占着**，每行右边都挂着
     *  一块永远空着的位子。补丁 `0006` 加了 `showCheck = false` 之后连那一格都不占，选中态改由
     *  行首这颗表示（见 `PageMenuPopup` 的 `MenuRow`）。
     *
     *  字形仍是 Tabler 这颗 `Check`：库自带的 `COUIIcons.Basic.Check` 描边 1.4，与页面其余
     *  Tabler 图标（描边 2）不是一支笔。
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

    /**
     * 「松手执行额外动作」行的行首 —— `pointer-up`（一根手指点着、上方一支向上的箭头）。
     *
     *  读作「抬手那一下」：与「长按延迟」那只 [DelayHold]（`hand-grab`，一把抓住）分开 ——
     *  那一行管「按住多久算长按」，这一行管「松手才执行」。
     */
    val LongRelease: ImageVector get() = TablerIcons.Outline.PointerUp

    /**
     * 「松手沿用按下时的场景」那一行 —— 一把锁：把这一按**锁在**按下那一刻命中的那一块上。
     *
     *  与 [LongRelease]（抬手）分工：那颗说的是「松手再执行一个动作」，这颗说的是「归哪一块」。
     *  图标库里没有图钉，锁与它有同一层意思（锁住 / 不再重判），也能一眼与那一族里其它三颗分开。
     */
    val ReleasePin: ImageVector get() = TablerIcons.Outline.Lock

    /**
     * 「松手才触发」行的行首 —— `pointer-pause`（光标 + 暂停记号）。
     *
     *  这一行管的是**长按到点之后先停一下**：按到长按时长不立刻执行，等抬手那一刻才走。
     *  「暂停」正是那个停顿（与 [LongRelease] 那颗「抬手」分工：那颗说的是**松手再执行一个**，
     *  这颗说的是**到点先别动**）。
     *
     *  同一颗也用于组合键那颗「使用按住手势」开关：那一位要的同样是「按下之后停一会儿」
     *  （按住一颗、再按另一颗）。两处同字形是**有意**的 —— 说的是同一种「停住」；
     *  按法菜单里那两颗手形（[HoldFirst] / [HoldSecond]）留给「先按哪一颗」那一层。
     */
    val LongFireOnRelease: ImageVector get() = TablerIcons.Outline.PointerPause

    /** 「命令间隔时长」—— `clock-hour-4`（指到四点的钟：等一段时间再走下一步）。 */
    val DelayStep: ImageVector get() = TablerIcons.Outline.ClockHour4

    /** 模块重启 —— `Reload`（回转箭头 + 竖线），读作「重新来一遍」。 */
    val ModuleRestart: ImageVector get() = TablerIcons.Outline.Reload

    /**
     * 「重新读一遍」—— `Refresh`（两段回转箭头），顶栏那颗刷新键用。
     *
     *  **与 [ModuleRestart] 分得开**：那一颗带竖线，是「把模块整个重启」；这一颗只是
     *  「再查一次数据」，没有重启的意思（两件事混用会让用户以为按一下会断链路）。
     */
    val Refresh: ImageVector get() = TablerIcons.Outline.Refresh

    /**
     * 「运行状态」里**整体状态**那一行 —— 好不好看两颗字形：`home-check` / `home-cancel`。
     *
     *  下面四条明细各自有自己的字形（见 [LinkService] / [LinkKeyResponse] / [LinkConfig] /
     *  [LinkUsage]），汇总这一行则回答「合起来怎么样」，所以它只分两档 ——
     *  **字形说结论，颜色也一起说**（红 / 常规色）。
     *
     *  **首页那条顶栏提示不用这两颗**：它统一 `alert-square`（见 [HomeStatusAlert]）。
     */
    val LinkOk: ImageVector get() = TablerIcons.Outline.HomeCheck

    /** 同 [LinkOk]，整体状态**不正常**时那一颗 —— `home-cancel`。 */
    val LinkBroken: ImageVector get() = TablerIcons.Outline.HomeCancel

    /**
     * 首页顶部**那条「有一件事要处理」**的行首图标 —— `alert-square`（方框里一个叹号）。
     *
     *  只给这一条用：它可能是「系统侧旧版」，也可能是「某一段异常」，两种都是**待办**。
     *  旁边那条「有新版本」仍是 `download`（[PageIcons.Download]）—— 那条说的是「有东西可取」，
     *  与「有事要处理」不是一回事，别混成一颗。
     */
    val HomeStatusAlert: ImageVector get() = TablerIcons.Outline.AlertSquare

    /** 「运行状态」明细：**服务运行** —— `server`（宿主那一侧有没有在跑）。 */
    val LinkService: ImageVector get() = TablerIcons.Outline.Server

    /** 「运行状态」明细：**按键响应** —— `keyboard`（按键那一侧的接管点）。 */
    val LinkKeyResponse: ImageVector get() = TablerIcons.Outline.Keyboard

    /** 「运行状态」明细：**设置生效** —— `settings-cancel`（设置没送到 / 不是这一份）。 */
    val LinkConfig: ImageVector get() = TablerIcons.Outline.SettingsCancel

    /** 「运行状态」明细：**使用验证** —— `history`（真按过的那笔痕迹）。 */
    val LinkUsage: ImageVector get() = TablerIcons.Outline.History

    /**
     * 「运行状态」明细：**按键接管** —— `pointer`（按键到底落没落到模块手上）。
     *
     *  这一段回答的是前面几段答不了的那个问题：接管点挂上了（`hooks > 0`）、配置也送到了，
     *  但只要总闸关着（或每颗键都停用），仍然**一颗键都不会被接住** —— 表现就是
     *  「自检全绿、按下去却是系统的功能」。真机上撞到过一次。
     */
    val LinkTakeOver: ImageVector get() = TablerIcons.Outline.Pointer
}
