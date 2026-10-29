package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.filled.CircleCheck
import io.github.lyxnx.compose.ui.tablericons.filled.Square
import io.github.lyxnx.compose.ui.tablericons.outline.Abc
import io.github.lyxnx.compose.ui.tablericons.outline.Adjustments
import io.github.lyxnx.compose.ui.tablericons.outline.AlignBoxCenterMiddle
import io.github.lyxnx.compose.ui.tablericons.outline.AlphabetChinese
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindow
import io.github.lyxnx.compose.ui.tablericons.outline.Apps
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowsSplit
import io.github.lyxnx.compose.ui.tablericons.outline.BrandAlipay
import io.github.lyxnx.compose.ui.tablericons.outline.BrandGithub
import io.github.lyxnx.compose.ui.tablericons.outline.BrandQq
import io.github.lyxnx.compose.ui.tablericons.outline.BrandTelegram
import io.github.lyxnx.compose.ui.tablericons.outline.BrandWechat
import io.github.lyxnx.compose.ui.tablericons.outline.BrightnessAuto
import io.github.lyxnx.compose.ui.tablericons.outline.Cancel
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronDown
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronUp
import io.github.lyxnx.compose.ui.tablericons.outline.Circle
import io.github.lyxnx.compose.ui.tablericons.outline.Click
import io.github.lyxnx.compose.ui.tablericons.outline.ClockHour4
import io.github.lyxnx.compose.ui.tablericons.outline.CodePlus
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceIpadCancel
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceIpadCheck
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobilePause
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobileVibration
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceScreen
import io.github.lyxnx.compose.ui.tablericons.outline.DotsCircleHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.DotsVertical
import io.github.lyxnx.compose.ui.tablericons.outline.Edit
import io.github.lyxnx.compose.ui.tablericons.outline.Eraser
import io.github.lyxnx.compose.ui.tablericons.outline.EyeCancel
import io.github.lyxnx.compose.ui.tablericons.outline.FileArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.FileArrowRight
import io.github.lyxnx.compose.ui.tablericons.outline.FileMinus
import io.github.lyxnx.compose.ui.tablericons.outline.FilePlus
import io.github.lyxnx.compose.ui.tablericons.outline.Fingerprint
import io.github.lyxnx.compose.ui.tablericons.outline.Flag
import io.github.lyxnx.compose.ui.tablericons.outline.Flask2
import io.github.lyxnx.compose.ui.tablericons.outline.GridDots
import io.github.lyxnx.compose.ui.tablericons.outline.HandGrab
import io.github.lyxnx.compose.ui.tablericons.outline.HandTwoFingers
import io.github.lyxnx.compose.ui.tablericons.outline.HomeCancel
import io.github.lyxnx.compose.ui.tablericons.outline.HomeCheck
import io.github.lyxnx.compose.ui.tablericons.outline.Infinity2
import io.github.lyxnx.compose.ui.tablericons.outline.InputX
import io.github.lyxnx.compose.ui.tablericons.outline.LabelImportant
import io.github.lyxnx.compose.ui.tablericons.outline.Link
import io.github.lyxnx.compose.ui.tablericons.outline.Lock
import io.github.lyxnx.compose.ui.tablericons.outline.LockOpen
import io.github.lyxnx.compose.ui.tablericons.outline.Logs
import io.github.lyxnx.compose.ui.tablericons.outline.Message2
import io.github.lyxnx.compose.ui.tablericons.outline.MoodEmpty
import io.github.lyxnx.compose.ui.tablericons.outline.MoodHeart
import io.github.lyxnx.compose.ui.tablericons.outline.Moon
import io.github.lyxnx.compose.ui.tablericons.outline.NavigationCog
import io.github.lyxnx.compose.ui.tablericons.outline.Notes
import io.github.lyxnx.compose.ui.tablericons.outline.Number1
import io.github.lyxnx.compose.ui.tablericons.outline.Number2
import io.github.lyxnx.compose.ui.tablericons.outline.Number3
import io.github.lyxnx.compose.ui.tablericons.outline.Number4
import io.github.lyxnx.compose.ui.tablericons.outline.Number5
import io.github.lyxnx.compose.ui.tablericons.outline.Packages
import io.github.lyxnx.compose.ui.tablericons.outline.Palette
import io.github.lyxnx.compose.ui.tablericons.outline.Pencil
import io.github.lyxnx.compose.ui.tablericons.outline.PencilCog
import io.github.lyxnx.compose.ui.tablericons.outline.PictureInPictureOn
import io.github.lyxnx.compose.ui.tablericons.outline.PlugConnected
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Reload
import io.github.lyxnx.compose.ui.tablericons.outline.Repeat
import io.github.lyxnx.compose.ui.tablericons.outline.Selector
import io.github.lyxnx.compose.ui.tablericons.outline.Settings
import io.github.lyxnx.compose.ui.tablericons.outline.Settings2
import io.github.lyxnx.compose.ui.tablericons.outline.SpacingHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.SquareNumber1
import io.github.lyxnx.compose.ui.tablericons.outline.SquareNumber2
import io.github.lyxnx.compose.ui.tablericons.outline.SquareNumber3
import io.github.lyxnx.compose.ui.tablericons.outline.Stack2
import io.github.lyxnx.compose.ui.tablericons.outline.Sun
import io.github.lyxnx.compose.ui.tablericons.outline.SunMoon
import io.github.lyxnx.compose.ui.tablericons.outline.TableShortcut
import io.github.lyxnx.compose.ui.tablericons.outline.Terminal2
import io.github.lyxnx.compose.ui.tablericons.outline.TextSize
import io.github.lyxnx.compose.ui.tablericons.outline.ToggleRight
import io.github.lyxnx.compose.ui.tablericons.outline.Tooltip
import io.github.lyxnx.compose.ui.tablericons.outline.Trash
import io.github.lyxnx.compose.ui.tablericons.outline.UserScreen
import io.github.lyxnx.compose.ui.tablericons.outline.UsersGroup
import io.github.lyxnx.compose.ui.tablericons.outline.Versions
import io.github.lyxnx.compose.ui.tablericons.outline.World
import io.github.lyxnx.compose.ui.tablericons.outline.ZoomIn

// 页面自己的小图标 —— 从 `ActionIcons.kt` 分出来一支：那个文件已经贴着「单文件 ≤ 1000 行」
// 的判据（`docs/decoupling.md` 判据 4），再加一颗字形就会越线。
// 这里只放**页面骨架**用的图标（顶栏、行尾、行首、浮层菜单、状态标记），
// **动作 → 图标**那张映射表留在 `ActionIcons.kt`。

/** 页面自己的小图标（顶栏返回键 / 行尾箭头 / 「按键绑定」行首 / 浮层菜单两行）—— 同样出自 Tabler。 */
internal object PageIcons {
    /** 顶栏返回键。 */
    val Back: ImageVector get() = TablerIcons.Outline.ArrowLeft

    /**
     * 「通用设置」那一行 / 那一页 —— `settings-2`（两颗滑块的齿轮）。
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

    /** 浮层菜单「清空配置」—— `eraser`（橡皮擦）。 */
    val MenuReset: ImageVector get() = TablerIcons.Outline.Eraser

    /** 浮层菜单「导出日志」—— `logs`（一叠日志行）。 */
    val MenuExport: ImageVector get() = TablerIcons.Outline.Logs

    /**
     * 首页「按键」卡里侧键那一行 —— **手绘**：手机外框 + 左边凸出来的一颗侧键。
     *
     *  与应用图标同一个 motif（那颗图标的字形就是「手机 + 凸起的侧键 + 面板」，见
     *  `res/drawable/ic_module_foreground.xml`）；24 视口上只留最能读懂的两件：外框与那颗凸起，
     *  面板与箭头在这种尺寸下会糊成一团。
     *
     *  笔画按 Tabler 那一档（1.9 圆头）落在 24 视口上，与相邻的 [menuKeyNumber] 那几颗数字
     *  同一支笔；与它们必须不同字形：清单里第一行是侧键、往下是收集来的键，只靠行首这一颗分谁是谁。
     */
    val MenuSideKey: ImageVector by lazy {
        //  构图：手机偏右（8.4～18.4），侧键在左边（x=5.6）与它并排 —— 整个字形左右居中在 24 格里
        ImageVector
            .Builder(
                name = "MenuSideKey",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                addPath(
                    pathData =
                        PathData {
                            val left = 8.4f
                            val top = 3f
                            val right = left + 10f
                            val bottom = 21f
                            val corner = 2.6f
                            moveTo(left + corner, top)
                            lineTo(right - corner, top)
                            quadTo(right, top, right, top + corner)
                            lineTo(right, bottom - corner)
                            quadTo(right, bottom, right - corner, bottom)
                            lineTo(left + corner, bottom)
                            quadTo(left, bottom, left, bottom - corner)
                            lineTo(left, top + corner)
                            quadTo(left, top, left + corner, top)
                            close()
                        },
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = SIDE_KEY_STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                addPath(
                    // 一条圆头短线：圆帽就是这颗键的两端
                    pathData =
                        PathData {
                            moveTo(5.6f, 8.4f)
                            lineTo(5.6f, 12.4f)
                        },
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = SIDE_KEY_STROKE,
                    strokeLineCap = StrokeCap.Round,
                )
            }.build()
    }

    /**
     * 清单里「第几条」—— 按它在这一列里的位置取数字：`number-1` … `number-5`
     * （[index] 从 0 起）。
     *
     *  两个调用点共用这一支笔：首页「按键」卡里**收集到的键**（上限三颗，
     *  `KeyId.MAX_CUSTOM_KEYS`）、以及「组合键」列表里的**第几组**（上限五组，
     *  `MAX_COMBOS`）。数字跟着**列表位置**走，删掉中间那条之后后面的各自前移一位 ——
     *  与清单里看到的顺序一致。
     *
     *  只到 5：五组是组合键的上限（用户口径），再往上那一档没有图标可用，
     *  越界就兜到 5 —— 真的越界说明上限判据与这排数字又各说了一个数。
     *
     *  与 [MenuKeyAdd]（加号）分工：那一行是「再加一颗」，这几行是「已经收下的那条」。
     */
    fun menuKeyNumber(index: Int): ImageVector =
        when (index) {
            0 -> TablerIcons.Outline.Number1
            1 -> TablerIcons.Outline.Number2
            2 -> TablerIcons.Outline.Number3
            3 -> TablerIcons.Outline.Number4
            else -> TablerIcons.Outline.Number5
        }

    /**
     * 首页「按键」卡末行「添加按键」—— `plus`（加号）。
     *
     *  这一行是**往清单里再加一颗**（弹收集弹窗），加号直接说这件事；
     *  收集到的那几行用 [menuKeyNumber]，两者不共用同一颗字形。
     */
    val MenuKeyAdd: ImageVector get() = TablerIcons.Outline.Plus

    /**
     * 首页「按键操作」卡里那一行「组合按键」—— `hand-two-fingers`（两只手指）。
     *
     *  用户口径指定这一颗：这一行说的是「**两颗键一起按**」，两只手指比 ⌘ 更直接 ——
     *  ⌘ 是桌面键盘的组合键符号，手机上不出现在任何系统界面里。
     *
     *  页内那两处（列表的空态行、设置卡的行首）用全项目通用那颗空态字形
     *  （[NoMatch]，`mood-empty`）—— 那里说的是「这一类东西 / 还没配」，不是「按键」。
     */
    val MenuComboFingers: ImageVector get() = TablerIcons.Outline.HandTwoFingers

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
     */
    val Donate: ImageVector get() = TablerIcons.Outline.MoodHeart

    /**
     * 「捐赠列表」那一行的行首 —— `notes`（一页写满字的纸）。
     *
     *  与 [Donate] 那颗心分开：那一行是「怎么给」、这一行是「谢谢谁」—— 名单与金额排成一张表，
     *  `notes` 就是「一张记着事的单子」的记号。
     */
    val DonateList: ImageVector get() = TablerIcons.Outline.Notes

    /**
     * 「作者」菜单里「酷安」那一项的字形 —— `apps`（九宫格）。
     *
     *  Tabler 没有酷安的品牌字形，用通用的「应用集合」标记；**不要**借
     *  `brand-google-play` / `brand-appgallery` 这类**具体品牌**（会把用户导去认另一个牌子）。
     */
    val BrandCoolapk: ImageVector get() = TablerIcons.Outline.Apps

    /**
     * 捐赠菜单三行的品牌字形 —— 菜单里三行都是「一个去处」，只有品牌图能一眼分清。
     *
     *  三支同族、各留一份名字（以后要分开改时不用先拆共用）。
     */
    val BrandQq: ImageVector get() = TablerIcons.Outline.BrandQq

    val BrandWechat: ImageVector get() = TablerIcons.Outline.BrandWechat

    val BrandAlipay: ImageVector get() = TablerIcons.Outline.BrandAlipay

    /** 「更多选项」页「语言」—— `world`（地球：用地球表示语言）。 */
    val Language: ImageVector get() = TablerIcons.Outline.World

    /** 「更多选项」页「系统配色」—— `palette`（这一行改的是配色）。 */
    val Palette: ImageVector get() = TablerIcons.Outline.Palette

    /**
     * 「系统配色」菜单里每一行行首那块**色块** —— `square-filled`（实心方块）。
     *
     *  形状五套共用，颜色由 `MenuEntry.Row.iconTint` 逐行给（色块本身就是这一行的身份）。
     *  用**实心**那一档：描边的图标上色之后只是一圈彩线，看不出是「这一套色」。
     */
    val PaletteSwatch: ImageVector get() = TablerIcons.Filled.Square

    /** 「外观」卡「页面缩放」—— `zoom-in`（放大镜加号：这一行是缩放）。 */
    val ZoomIn: ImageVector get() = TablerIcons.Outline.ZoomIn

    /** 「外观」卡「跟随系统显示大小」—— `text-size`（大小 A→A）。 */
    val TextSize: ImageVector get() = TablerIcons.Outline.TextSize

    /** 「更多选项」页「界面颜色」—— `sun-moon`（一半日一半月：深浅由这一行定）。 */
    val Theme: ImageVector get() = TablerIcons.Outline.SunMoon

    // 「界面颜色」菜单里那三项各自的字形 —— 与语言那三颗同一个道理：共用一颗的话菜单三行长得一样。

    /** 「跟随系统」—— `brightness-auto`（自动亮度那种「A」）。 */
    val ThemeAuto: ImageVector get() = TablerIcons.Outline.BrightnessAuto

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

    /** 「更多选项」页「导出配置」—— `file-arrow-right`（文件 + 向外去的箭头）。 */
    val ConfigExport: ImageVector get() = TablerIcons.Outline.FileArrowRight

    /** 「更多选项」页「导入配置」—— `file-arrow-left`（文件 + 向内进的箭头），与 [ConfigExport] 成一出一进。 */
    val ConfigImport: ImageVector get() = TablerIcons.Outline.FileArrowLeft

    /** 「选择应用」页顶栏菜单的「显示系统应用」—— `GridDots`（点阵格）。 */
    val SystemApps: ImageVector get() = TablerIcons.Outline.GridDots

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
     * 「条件执行」那条命令的类型图标 —— `arrows-split`（一路分两岔）。
     *
     *  与「多条命令」那族 `box-multiple-N` 区分开：那是一串盒子（按顺序走完），这是岔路
     *  （按条件只走其中一条），形状本身就是两种语义。
     */
    val CommandBranch: ImageVector get() = TablerIcons.Outline.ArrowsSplit

    /**
     * **场景**（界面上原来叫「条件」）—— `adjustments`（一组可调的设定）。
     *
     *  与 [CommandBranch]（`arrows-split`）分工：那颗说的是「按它**走哪一支**」（规则表的空态、
     *  表尾注脚），这颗说的是「**在什么情境下**」——卡片里「未选择场景」那一行、以及菜单里
     *  指到场景本身的那几处都用它（含按键 `⋮` 菜单里那一行）。两者混用会让「场景」与「分岔」
     *  读成一件事。
     */
    val Scene: ImageVector get() = TablerIcons.Outline.Adjustments

    /** **多配置** —— `stack-2`（一叠纸）：切换就是抽哪一张。 */
    val Profiles: ImageVector get() = TablerIcons.Outline.Stack2

    /** **新建配置** —— `file-plus`：加一份。 */
    val ProfileAdd: ImageVector get() = TablerIcons.Outline.FilePlus

    /** **删除配置** —— `file-minus`：减一份（与「新建」同一套形状，只换中间那一笔）。 */
    val ProfileDelete: ImageVector get() = TablerIcons.Outline.FileMinus

    /**
     * 分叉条件「某个应用在前台」—— `apps`（九宫格）。
     *
     *  与 [CommandBranch] 分开：「选择条件」那张菜单里每种条件一行一颗，`arrows-split` 是
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

    /** 「步骤」卡片里「添加步骤」那一行的行首图标。 */
    val StepAdd: ImageVector get() = TablerIcons.Outline.Plus

    /** 「已保存的命令」卡片右上角 action 按钮 —— `Flask2`（锥形瓶），读作「把这条试跑一遍」。 */
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
     * 「签名」行的行首 —— `fingerprint`（指纹）。
     *
     *  「签名」在这里指的就是**指纹**：给用户拿去和官方发布页上那串对一遍（见 `core/seal/Seal`
     *  的 `ownFingerprint`）。指纹字形比证书 / 印章更贴这件事本身。
     */
    val Signature: ImageVector get() = TablerIcons.Outline.Fingerprint

    /** 「执行时提示」行的行首 —— `tooltip`（气泡提示）。 */
    val CommandToast: ImageVector get() = TablerIcons.Outline.Tooltip

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

    /**
     * 「这一行绑的是某个**具体界面**」—— 行尾那枚提示（Tabler 的 `device-screen`）。
     *
     *  与 [TileChecked] 二选一：绑的是 `app:包名/类名` 时用它，一眼看出「不是打开首页，
     *  是打开里面某一页」；绑的是纯包名 / 快捷方式时仍是那颗勾。
     */
    val ActivityScreen: ImageVector get() = TablerIcons.Outline.DeviceScreen

    /**
     * 「这一行绑的是某个**快捷方式**」—— 行尾那枚提示（Tabler 的 `table-shortcut`）。
     *
     *  与 [ActivityScreen] 二选一：两者在配置里都写成长度相近的 `包名/xxx`，光看字符串分不出来
     *  （一边是类名、一边是快捷方式 id），只能靠 `PickedDetail.shortcutId` 判断。
     */
    val ShortcutMark: ImageVector get() = TablerIcons.Outline.TableShortcut

    /** 命令编辑页底部工具栏的主操作（「保存 / 修改」）—— `Check`，主次靠颜色分（主操作走 `primary`）。 */
    val Confirm: ImageVector get() = TablerIcons.Outline.Check

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

    /** 「命令间隔时长」—— `clock-hour-4`（指到四点的钟：等一段时间再走下一步）。 */
    val DelayStep: ImageVector get() = TablerIcons.Outline.ClockHour4

    /**
     * 「忽视无操作双击手势」行的行首 —— `eye-cancel`（眼睛带叉，读作「不认这次双击」；
     *  这套 Tabler 里没有 `eye-x`，`eye-cancel` 是同一颗字形）。
     *
     *  原来用的是 `Bolt`（闪电），读起来像「立刻触发 / 加速」，而这一行管的是**不承认**那个手势，
     *  语义正好反了。
     */
    val IgnoreDouble: ImageVector get() = TablerIcons.Outline.EyeCancel

    /** 模块重启 —— `Reload`（回转箭头 + 竖线），读作「重新来一遍」。 */
    val ModuleRestart: ImageVector get() = TablerIcons.Outline.Reload

    /** 「运行状态」里**正常**的那一段 —— `home-check`（房子 + 勾）。 */
    val LinkOk: ImageVector get() = TablerIcons.Outline.HomeCheck

    /** 「运行状态」里**不正常**的那一段 —— `home-cancel`（房子 + 叉）。 */
    val LinkBroken: ImageVector get() = TablerIcons.Outline.HomeCancel
}

/**
 * 手绘字形（[PageIcons.MenuSideKey]）的笔画宽度 —— 与 Tabler 那一档（2dp）对齐，
 * 收 0.1 是为了让圆头笔画的两端不顶到 24 格的边界。
 */
private const val SIDE_KEY_STROKE = 1.9f
