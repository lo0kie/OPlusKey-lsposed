package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.KsuCompat
import com.lookie.opluskey.core.model.RomProfile
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.filled.CircleCheck
import io.github.lyxnx.compose.ui.tablericons.filled.Square
import io.github.lyxnx.compose.ui.tablericons.outline.Abc
import io.github.lyxnx.compose.ui.tablericons.outline.AdjustmentsBolt
import io.github.lyxnx.compose.ui.tablericons.outline.AlignBoxCenterMiddle
import io.github.lyxnx.compose.ui.tablericons.outline.AlphabetChinese
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindow
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindowBottom
import io.github.lyxnx.compose.ui.tablericons.outline.Apps
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowBackUp
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.Ban
import io.github.lyxnx.compose.ui.tablericons.outline.BatteryEco
import io.github.lyxnx.compose.ui.tablericons.outline.Bell
import io.github.lyxnx.compose.ui.tablericons.outline.BellOff
import io.github.lyxnx.compose.ui.tablericons.outline.BellRinging
import io.github.lyxnx.compose.ui.tablericons.outline.Bluetooth
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple1
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple2
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple3
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple4
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple5
import io.github.lyxnx.compose.ui.tablericons.outline.BrandAlipay
import io.github.lyxnx.compose.ui.tablericons.outline.BrandGithub
import io.github.lyxnx.compose.ui.tablericons.outline.BrandQq
import io.github.lyxnx.compose.ui.tablericons.outline.BrandTelegram
import io.github.lyxnx.compose.ui.tablericons.outline.BrandWechat
import io.github.lyxnx.compose.ui.tablericons.outline.BrightnessAuto
import io.github.lyxnx.compose.ui.tablericons.outline.Bulb
import io.github.lyxnx.compose.ui.tablericons.outline.Cancel
import io.github.lyxnx.compose.ui.tablericons.outline.Cards
import io.github.lyxnx.compose.ui.tablericons.outline.CellSignal4
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
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceScreen
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import io.github.lyxnx.compose.ui.tablericons.outline.DotsCircleHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.DotsVertical
import io.github.lyxnx.compose.ui.tablericons.outline.Eraser
import io.github.lyxnx.compose.ui.tablericons.outline.EyeCancel
import io.github.lyxnx.compose.ui.tablericons.outline.FileArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.FileArrowRight
import io.github.lyxnx.compose.ui.tablericons.outline.Fingerprint
import io.github.lyxnx.compose.ui.tablericons.outline.Flask2
import io.github.lyxnx.compose.ui.tablericons.outline.HandGrab
import io.github.lyxnx.compose.ui.tablericons.outline.Home
import io.github.lyxnx.compose.ui.tablericons.outline.HomeCancel
import io.github.lyxnx.compose.ui.tablericons.outline.HomeCheck
import io.github.lyxnx.compose.ui.tablericons.outline.Infinity2
import io.github.lyxnx.compose.ui.tablericons.outline.InputX
import io.github.lyxnx.compose.ui.tablericons.outline.Keyboard
import io.github.lyxnx.compose.ui.tablericons.outline.LabelImportant
import io.github.lyxnx.compose.ui.tablericons.outline.LayoutGrid
import io.github.lyxnx.compose.ui.tablericons.outline.Link
import io.github.lyxnx.compose.ui.tablericons.outline.Logs
import io.github.lyxnx.compose.ui.tablericons.outline.Message2
import io.github.lyxnx.compose.ui.tablericons.outline.Microphone
import io.github.lyxnx.compose.ui.tablericons.outline.MoodEmpty
import io.github.lyxnx.compose.ui.tablericons.outline.MoodHeart
import io.github.lyxnx.compose.ui.tablericons.outline.Moon
import io.github.lyxnx.compose.ui.tablericons.outline.MoonStars
import io.github.lyxnx.compose.ui.tablericons.outline.Music
import io.github.lyxnx.compose.ui.tablericons.outline.NavigationCog
import io.github.lyxnx.compose.ui.tablericons.outline.Nfc
import io.github.lyxnx.compose.ui.tablericons.outline.Notes
import io.github.lyxnx.compose.ui.tablericons.outline.Notification
import io.github.lyxnx.compose.ui.tablericons.outline.Number1
import io.github.lyxnx.compose.ui.tablericons.outline.Number2
import io.github.lyxnx.compose.ui.tablericons.outline.Number3
import io.github.lyxnx.compose.ui.tablericons.outline.Packages
import io.github.lyxnx.compose.ui.tablericons.outline.Palette
import io.github.lyxnx.compose.ui.tablericons.outline.Pencil
import io.github.lyxnx.compose.ui.tablericons.outline.PictureInPictureOn
import io.github.lyxnx.compose.ui.tablericons.outline.PictureInPictureTop
import io.github.lyxnx.compose.ui.tablericons.outline.Plane
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerPlay
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerStop
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerTrackNext
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerTrackPrev
import io.github.lyxnx.compose.ui.tablericons.outline.PlugConnected
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Pointer
import io.github.lyxnx.compose.ui.tablericons.outline.Power
import io.github.lyxnx.compose.ui.tablericons.outline.RecordMail
import io.github.lyxnx.compose.ui.tablericons.outline.Reload
import io.github.lyxnx.compose.ui.tablericons.outline.Repeat
import io.github.lyxnx.compose.ui.tablericons.outline.RotateRectangle
import io.github.lyxnx.compose.ui.tablericons.outline.Screenshot
import io.github.lyxnx.compose.ui.tablericons.outline.Selector
import io.github.lyxnx.compose.ui.tablericons.outline.Settings
import io.github.lyxnx.compose.ui.tablericons.outline.SettingsCog
import io.github.lyxnx.compose.ui.tablericons.outline.SpacingHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.SquareNumber1
import io.github.lyxnx.compose.ui.tablericons.outline.SquareNumber2
import io.github.lyxnx.compose.ui.tablericons.outline.SquareNumber3
import io.github.lyxnx.compose.ui.tablericons.outline.Sun
import io.github.lyxnx.compose.ui.tablericons.outline.SunMoon
import io.github.lyxnx.compose.ui.tablericons.outline.Switch3
import io.github.lyxnx.compose.ui.tablericons.outline.TableShortcut
import io.github.lyxnx.compose.ui.tablericons.outline.Terminal2
import io.github.lyxnx.compose.ui.tablericons.outline.TextRecognition
import io.github.lyxnx.compose.ui.tablericons.outline.Tooltip
import io.github.lyxnx.compose.ui.tablericons.outline.Trash
import io.github.lyxnx.compose.ui.tablericons.outline.UserScreen
import io.github.lyxnx.compose.ui.tablericons.outline.UsersGroup
import io.github.lyxnx.compose.ui.tablericons.outline.Versions
import io.github.lyxnx.compose.ui.tablericons.outline.Volume
import io.github.lyxnx.compose.ui.tablericons.outline.Volume4
import io.github.lyxnx.compose.ui.tablericons.outline.Wifi
import io.github.lyxnx.compose.ui.tablericons.outline.WindowMinimize
import io.github.lyxnx.compose.ui.tablericons.outline.World

/** 动作 → 图标的映射（列表行首那个图标）。 */
internal fun actionIcon(token: String): ImageVector =
    when (token) {
        // 无操作
        Action.NONE -> {
            TablerIcons.Outline.Ban
        }

        // 手电筒（全项目只用 Tabler 一支笔，不混图标库）
        Action.FLASHLIGHT -> {
            TablerIcons.Outline.Bulb
        }

        // 电源键（`keyevent:` 一律是数字形态）
        Action.keyToken(KeyId.KEYCODE_POWER) -> {
            TablerIcons.Outline.Power
        }

        // 电源菜单（弹出关机 / 重启那一层）—— 必须与「电源键」不同字形：同样一颗电源图标摆在一起，
        //  分不出按下的那个是按键还是菜单，所以用「滑杆 + 闪电」。
        Action.POWER_MENU -> {
            TablerIcons.Outline.AdjustmentsBolt
        }

        // —— 系统侧键场景表里的四条 ——
        // 一键闪记
        Action.OPLUS_PREFIX + RomProfile.NAME_FLASH_MEMORY -> {
            TablerIcons.Outline.Pencil
        }

        // 免打扰（月亮：勿扰 / 夜间的通用读法）
        Action.OPLUS_PREFIX + RomProfile.NAME_NO_DISTURB -> {
            TablerIcons.Outline.Moon
        }

        // 灵感语音
        Action.OPLUS_PREFIX + RomProfile.NAME_VOICE_MEMORY -> {
            TablerIcons.Outline.RecordMail
        }

        // 录音
        Action.OPLUS_PREFIX + RomProfile.NAME_RECORDING -> {
            TablerIcons.Outline.Microphone
        }

        // 响铃状态（那一格；菜单里的「循环」见 [specIcon]）
        ActionCatalog.RINGER_TILE -> {
            TablerIcons.Outline.Bell
        }

        // 系统导航三档
        Action.keyToken(KeyId.KEYCODE_HOME) -> {
            TablerIcons.Outline.Home
        }

        Action.keyToken(KeyId.KEYCODE_BACK) -> {
            TablerIcons.Outline.ArrowBackUp
        }

        // 最近任务
        Action.keyToken(KeyId.KEYCODE_APP_SWITCH) -> {
            TablerIcons.Outline.Cards
        }

        // 通知中心（改名不改字形）
        Action.keyToken(KeyId.KEYCODE_NOTIFICATION) -> {
            TablerIcons.Outline.Notification
        }

        // 控制中心（`panel:control_center`）—— 用 Command 那颗：它是系统里「控制」那一类的通用读法
        ActionCatalog.PANEL_CONTROL -> {
            TablerIcons.Outline.Command
        }

        // 媒体控制菜单的五条。**格子**那枚不走这里 —— 它跟首行共用同一串 token，由 [specIcon]
        // 按 spec 认领成音符（那是「一类东西」），这里的开关键说的是「那一个具体取值」。
        ActionCatalog.MEDIA_TILE -> {
            TablerIcons.Outline.Switch3
        }

        // 播放 / 暂停 / 上一首 / 下一首（前两条交给系统按当前状态翻面）
        Action.keyToken(KeyId.KEYCODE_MEDIA_PLAY) -> {
            TablerIcons.Outline.PlayerPlay
        }

        Action.keyToken(KeyId.KEYCODE_MEDIA_PAUSE) -> {
            TablerIcons.Outline.PlayerStop
        }

        Action.keyToken(KeyId.KEYCODE_MEDIA_PREVIOUS) -> {
            TablerIcons.Outline.PlayerTrackPrev
        }

        Action.keyToken(KeyId.KEYCODE_MEDIA_NEXT) -> {
            TablerIcons.Outline.PlayerTrackNext
        }

        // 音量 + / −
        Action.keyToken(KeyId.KEYCODE_VOLUME_UP) -> {
            TablerIcons.Outline.Volume
        }

        Action.keyToken(KeyId.KEYCODE_VOLUME_DOWN) -> {
            TablerIcons.Outline.Volume4
        }

        // 把前台应用收成小窗 —— 展开档用「窗口 + 底部块」（`app-window-bottom`）
        Action.ZOOM_MINI -> {
            TablerIcons.Outline.AppWindowBottom
        }

        // 收起档（右上角一条）—— 用画中画那颗（画面挂在**顶部**），与展开档一眼分得开
        Action.ZOOM_MINI_COLLAPSE -> {
            TablerIcons.Outline.PictureInPictureTop
        }

        // 响铃档位（`ringer:normal|vibrate|silent`）；「循环」那一档不在这里 ——
        // 它写的是 `keyevent:164`，由 [specIcon] 按 spec 认领 [PageIcons.RingerCycle]（铃铛 + 一圈箭头）。
        Action.RINGER_PREFIX + Action.Ringer.NORMAL -> {
            TablerIcons.Outline.BellRinging
        }

        Action.RINGER_PREFIX + Action.Ringer.VIBRATE -> {
            TablerIcons.Outline.DeviceMobileVibration
        }

        Action.RINGER_PREFIX + Action.Ringer.SILENT -> {
            TablerIcons.Outline.BellOff
        }

        else -> {
            //  shell 正文（去掉 `shell:` 前缀）—— 两条 KSU 别名的认领看的是正文那一层
            val shell = token.removePrefix(Action.SHELL_PREFIX)
            when {
                // 截图那条 shell（清单里是 `service call color_screenshot 1`）。
                //  判据走 [KsuCompat.isScreenshotAlias]，也就是执行侧那一把尺子（前缀匹配 +
                //  这一条里没串别的命令），**不能**用 `token.contains(...)`：
                //  写 `shell:echo oplus.intent.action.DIRECT_SIDEBAR_SERVICE` 这种只是**提到**它的
                //  命令也会被认成「识屏」—— 图标与执行侧的实际行为对不上（那边根本不认）。
                KsuCompat.isScreenshotAlias(shell) -> {
                    TablerIcons.Outline.Screenshot
                }

                // 小布识屏那条 shell（`DIRECT_SIDEBAR_SERVICE`）—— 语义是「识屏」。判据同上
                KsuCompat.isSidebarAlias(shell) -> {
                    TablerIcons.Outline.TextRecognition
                }

                token.startsWith(Action.SHELL_PREFIX) -> {
                    TablerIcons.Outline.Terminal2
                }

                // 区别只在内容存哪（`shell:` 是原文，`cmd:` 是名字）
                token.startsWith(Command.PREFIX) -> {
                    TablerIcons.Outline.Terminal2
                }

                // 快捷方式（`appshortcut:`）与「打开应用」共用一颗字形：都是「把某个东西叫起来」
                token.startsWith(Action.APP_PREFIX) || token.startsWith(Action.APP_SHORTCUT_PREFIX) -> {
                    TablerIcons.Outline.LayoutGrid
                }

                // 快捷方式 id（`shortcut:<id>`）—— 与上面那族同义，但写的是 id 不是包名，
                //  给它一颗自己的字形，别落 `Dots`（清单里这两档都教用户手写，图标必须是可辨的）。
                token.startsWith(Action.SHORTCUT_PREFIX) -> {
                    PageIcons.Link
                }

                // `intent:ACTION` —— 按 action 起界面
                token.startsWith(Action.INTENT_PREFIX) -> {
                    TablerIcons.Outline.AppWindow
                }

                // 系统开关（`setting:<id>`）—— 每一条都有自己的字形，从清单里一眼分得开
                //  （清单本体见 `core/model/SystemSwitches`）
                token.startsWith(Action.SETTING_PREFIX) -> {
                    systemSettingIcon(token.removePrefix(Action.SETTING_PREFIX))
                }

                // 「以小窗口打开」（`window:目标`）：新起一个窗口 —— 与「把前台的收成小窗」那两档
                // （`zoom:mini` / `zoom:mini:collapse`，见上面）是**三支不同字形**，别顺手并回来。
                token.startsWith(Action.WINDOW_PREFIX) -> {
                    TablerIcons.Outline.WindowMinimize
                }

                else -> {
                    TablerIcons.Outline.Dots
                }
            }
        }
    }

/**
 * 「系统开关」里那几条开关的字形 —— `setting:<id>` 里 `<id>` 那一节在这里翻译成图标。
 *
 *  **全项目唯一一处**（与 [actionIcon] 里那条前缀判断配套）：清单本身在
 *  `core/model/SystemSwitches`，那层没有 Compose，字形只能落在这里。加一条开关 = 在那个清单里
 * 加一行 + 这里加一支 —— 漏了这里会掉到 [TablerIcons.Outline.Dots]，看着像「没配图标」。
 */
private fun systemSettingIcon(id: String): ImageVector =
    when (id) {
        "bt" -> TablerIcons.Outline.Bluetooth

        "wifi" -> TablerIcons.Outline.Wifi

        "cell" -> TablerIcons.Outline.CellSignal4

        "rotation" -> TablerIcons.Outline.RotateRectangle

        "nfc" -> TablerIcons.Outline.Nfc

        "airplane" -> TablerIcons.Outline.Plane

        // 老配置里可能还留着 `setting:dnd`（现在「免打扰」走 `oplus:` 那条，见上方 actionIcon），
        //  留着这一支只为旧文件的图标不变成 Dots。
        "dnd" -> TablerIcons.Outline.Moon

        "darkmode" -> TablerIcons.Outline.MoonStars

        "powersave" -> TablerIcons.Outline.BatteryEco

        else -> TablerIcons.Outline.Dots
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
internal fun specIcon(spec: ActionCatalog.Spec): ImageVector {
    // 先落到局部 val 再判空：`spec` 来自 `:core` 模块，而那两位是**另一个模块**里的
    //  public 属性 —— Kotlin 不给跨模块的属性做智能转换（理论上可能被 override）。
    val commandKind = spec.commandKind
    val glyph = spec.glyph
    return when {
        commandKind != null -> commandIcon(commandKind, spec.commandSteps)

        // 注册时声明过字形的（入口格 / 标记格 / 一处 token 两支字形那几种），按声明给
        glyph != null -> glyphIcon(glyph)

        // 其余按 token 推 —— 与配置里那一串写法同一套（`Action.parse` 的规范化结果）
        else -> actionIcon(ActionCatalog.canonicalToken(spec.token))
    }
}

/**
 * 字形名 → 图标 —— **全项目唯一一处映射**（[ActionCatalog.Glyph] 是封闭枚举，加一档字形才碰它）。
 * 这几种「一个 token 两支字形」和「标记格」原来散在 [specIcon] 里用 `spec ===` / `token ==` 认领，
 * 那等于把注册信息漏在渲染层；现在字形随条目一起注册、这里只翻译。
 */
private fun glyphIcon(glyph: ActionCatalog.Glyph): ImageVector =
    when (glyph) {
        ActionCatalog.Glyph.Bell -> TablerIcons.Outline.Bell

        ActionCatalog.Glyph.Repeat -> PageIcons.RingerCycle

        ActionCatalog.Glyph.Music -> TablerIcons.Outline.Music

        ActionCatalog.Glyph.NavigationCog -> PageIcons.NavigationCog

        ActionCatalog.Glyph.Click -> PageIcons.Click

        ActionCatalog.Glyph.Panels -> PageIcons.Panels

        ActionCatalog.Glyph.MiniWindow -> PageIcons.MiniWindow

        // 「系统开关」入口格：齿轮（这一格进的是清单，不是某个取值）
        ActionCatalog.Glyph.SystemSwitch -> TablerIcons.Outline.Settings
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

    /**
     * 「通用设置」那一行 / 那一页 —— `settings-cog`（齿轮）。
     *
     *  用户口径指定这一颗：这一页装的是「整个模块怎么跑」的全局项（接口选择、振动、执行提示），
     *  齿轮比别的字形都更直接。
     */
    val SettingsCog: ImageVector get() = TablerIcons.Outline.SettingsCog

    /** 「按键绑定」行首 —— 箭头光标 `Pointer`，读作「按下去那一下」。 */
    val TouchApp: ImageVector get() = TablerIcons.Outline.Pointer

    /**
     * 「启用按键绑定」那一行的行首 —— 电源符号。
     *
     * 那一行必须给图标：它紧跟「按键绑定」行，同卡两行都有 `startAction` 标题才对得齐，
     * 只给上面那行的话下面标题会左移一个图标宽。
     */
    val Power: ImageVector get() = TablerIcons.Outline.Power

    /**
     * 「这颗键停用了」—— 圆圈 + 叉（`cancel`）。
     *
     *  用在浮层菜单里那颗键的行尾（`MenuEntry.Row.stateIcon`）与「按键绑定」页顶栏的
     *  尾随小图标：与行首那枚键位数字不同形，一眼能分出「这是第几颗键」与「这颗键现在不接管」。
     *
     *  不用 `Ban`（那是「无操作」那个动作的行首，摆在同一张网格里会串味），也不用 `Power`
     *  ——「停用这颗键」那**一行**的行首就是它，同一张菜单里同一个字形读两种意思。
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

    /** 顶栏右侧的菜单按钮（竖三点）。 */
    val Menu: ImageVector get() = TablerIcons.Outline.DotsVertical

    /** 浮层菜单「打开默认界面」—— 窗口框，读作「另一个界面」。 */
    val MenuStock: ImageVector get() = TablerIcons.Outline.AppWindow

    /** 浮层菜单「清空配置」—— `eraser`（橡皮擦）。 */
    val MenuReset: ImageVector get() = TablerIcons.Outline.Eraser

    /** 浮层菜单「导出日志」—— `logs`（一叠日志行）。 */
    val MenuExport: ImageVector get() = TablerIcons.Outline.Logs

    /**
     * 「按键绑定」页顶栏菜单的「快捷键」—— `keyboard`（一块键盘）。
     *
     *  这一项说的是「**现在这一组**是快捷键」，点它只收菜单（见 `BindingKeyMenu`）。
     *  与 [MenuKeyCard] 必须不同字形：菜单里两行都是「一组按键」，只靠行首这一颗分谁是谁。
     */
    val MenuKeys: ImageVector get() = TablerIcons.Outline.Keyboard

    /**
     * 「按键绑定」页顶栏菜单里**收集到的键** —— 按它在清单里的位置取数字：`number-1` / `-2` / `-3`
     * （[index] 从 0 起）。
     *
     *  用户口径：三颗键各拿一个数字，一眼能数出「现在有几颗」。上限就是三颗
     *  （`KeyId.MAX_CUSTOM_KEYS`），所以 `else` 兜到 3；数字跟着**列表位置**走，
     *  删掉中间那颗之后后面的会各自前移一位 —— 与菜单里看到的顺序一致。
     *
     *  与 [MenuKeyAdd]（加号）分工：那一行是「再加一颗」，这几行是「已经收下的那颗」。
     */
    fun menuKeyNumber(index: Int): ImageVector =
        when (index) {
            0 -> TablerIcons.Outline.Number1
            1 -> TablerIcons.Outline.Number2
            else -> TablerIcons.Outline.Number3
        }

    /**
     * 「按键绑定」页顶栏菜单的「添加按键」—— `plus`（加号）。
     *
     *  这一行是**往清单里再加一颗**（弹收集弹窗），加号直接说这件事；
     *  收集到的那几行用 [MenuKeyCard]，两者不再共用同一颗字形。
     */
    val MenuKeyAdd: ImageVector get() = TablerIcons.Outline.Plus

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

    /** 「已保存的命令」卡片右上角 action 按钮 —— `Flask2`（锥形瓶），读作「把这条试跑一遍」。 */
    val TestCommand: ImageVector get() = TablerIcons.Outline.Flask2

    /** 步骤行尾的「上移」/「下移」—— 竖人字。 */
    val StepUp: ImageVector get() = TablerIcons.Outline.ChevronUp

    val StepDown: ImageVector get() = TablerIcons.Outline.ChevronDown

    /** 编辑用铅笔 —— `Pencil`（编辑最不会误读的字形）。 */
    val CommandEdit: ImageVector get() = TablerIcons.Outline.Pencil

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
