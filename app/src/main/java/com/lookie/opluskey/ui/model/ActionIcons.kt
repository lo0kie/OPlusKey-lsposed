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
import io.github.lyxnx.compose.ui.tablericons.outline.AdjustmentsCog
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindow
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindowBottom
import io.github.lyxnx.compose.ui.tablericons.outline.Apps
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowBackUp
import io.github.lyxnx.compose.ui.tablericons.outline.Ban
import io.github.lyxnx.compose.ui.tablericons.outline.BatteryEco
import io.github.lyxnx.compose.ui.tablericons.outline.BellCog
import io.github.lyxnx.compose.ui.tablericons.outline.BellOff
import io.github.lyxnx.compose.ui.tablericons.outline.BellRinging
import io.github.lyxnx.compose.ui.tablericons.outline.Bluetooth
import io.github.lyxnx.compose.ui.tablericons.outline.Bolt
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple1
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple2
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple3
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple4
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple5
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple6
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple7
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple8
import io.github.lyxnx.compose.ui.tablericons.outline.BoxMultiple9
import io.github.lyxnx.compose.ui.tablericons.outline.BrightnessAuto
import io.github.lyxnx.compose.ui.tablericons.outline.Calculator
import io.github.lyxnx.compose.ui.tablericons.outline.Camera
import io.github.lyxnx.compose.ui.tablericons.outline.Cards
import io.github.lyxnx.compose.ui.tablericons.outline.Category
import io.github.lyxnx.compose.ui.tablericons.outline.CategoryMinus
import io.github.lyxnx.compose.ui.tablericons.outline.CategoryPlus
import io.github.lyxnx.compose.ui.tablericons.outline.CellSignal4
import io.github.lyxnx.compose.ui.tablericons.outline.Click
import io.github.lyxnx.compose.ui.tablericons.outline.Command
import io.github.lyxnx.compose.ui.tablericons.outline.Cpu
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobileVibration
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import io.github.lyxnx.compose.ui.tablericons.outline.Home
import io.github.lyxnx.compose.ui.tablericons.outline.Language
import io.github.lyxnx.compose.ui.tablericons.outline.Link
import io.github.lyxnx.compose.ui.tablericons.outline.Location
import io.github.lyxnx.compose.ui.tablericons.outline.MessageAi
import io.github.lyxnx.compose.ui.tablericons.outline.Microphone
import io.github.lyxnx.compose.ui.tablericons.outline.Moon
import io.github.lyxnx.compose.ui.tablericons.outline.MoonStars
import io.github.lyxnx.compose.ui.tablericons.outline.NavigationCog
import io.github.lyxnx.compose.ui.tablericons.outline.Nfc
import io.github.lyxnx.compose.ui.tablericons.outline.Notification
import io.github.lyxnx.compose.ui.tablericons.outline.PencilAi
import io.github.lyxnx.compose.ui.tablericons.outline.PhotoVideo
import io.github.lyxnx.compose.ui.tablericons.outline.PictureInPictureTop
import io.github.lyxnx.compose.ui.tablericons.outline.Plane
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerPlay
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerStop
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerTrackNext
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerTrackPrev
import io.github.lyxnx.compose.ui.tablericons.outline.Power
import io.github.lyxnx.compose.ui.tablericons.outline.Repeat
import io.github.lyxnx.compose.ui.tablericons.outline.RotateRectangle
import io.github.lyxnx.compose.ui.tablericons.outline.Screenshot
import io.github.lyxnx.compose.ui.tablericons.outline.SettingsCog
import io.github.lyxnx.compose.ui.tablericons.outline.SparkleHighlight
import io.github.lyxnx.compose.ui.tablericons.outline.SunLow
import io.github.lyxnx.compose.ui.tablericons.outline.Switch3
import io.github.lyxnx.compose.ui.tablericons.outline.TextScanAi
import io.github.lyxnx.compose.ui.tablericons.outline.ThinkingLow
import io.github.lyxnx.compose.ui.tablericons.outline.ToggleLeft
import io.github.lyxnx.compose.ui.tablericons.outline.ToggleRight
import io.github.lyxnx.compose.ui.tablericons.outline.Variable
import io.github.lyxnx.compose.ui.tablericons.outline.Video
import io.github.lyxnx.compose.ui.tablericons.outline.Wifi
import io.github.lyxnx.compose.ui.tablericons.outline.WindowMinimize

/** 动作 → 图标的映射（列表行首那个图标）。 */
internal fun actionIcon(token: String): ImageVector =
    when (token) {
        // 无操作
        Action.NONE -> {
            TablerIcons.Outline.Ban
        }

        // 手电筒 —— 自绘那颗（见 DrawnIcons.Flashlight）：图标库的 `flashlight` 是斜放的
        Action.FLASHLIGHT -> {
            DrawnIcons.Flashlight
        }

        // 电源键（`keyevent:` 一律是数字形态）
        Action.keyToken(KeyId.KEYCODE_POWER) -> {
            TablerIcons.Outline.Power
        }

        // 电源菜单（弹出关机 / 重启那一层）—— 必须与「电源键」不同字形：同样一颗电源图标摆在一起，
        //  分不出按下的那个是按键还是菜单，所以用「滑杆 + 齿轮」（可调的那一层菜单）。
        Action.POWER_MENU -> {
            TablerIcons.Outline.AdjustmentsCog
        }

        // —— 系统侧键场景表里的四条 ——
        // 一键闪记
        Action.OPLUS_PREFIX + RomProfile.NAME_FLASH_MEMORY -> {
            TablerIcons.Outline.PencilAi
        }

        // 免打扰（月亮：勿扰 / 夜间的通用读法）
        Action.OPLUS_PREFIX + RomProfile.NAME_NO_DISTURB -> {
            TablerIcons.Outline.Moon
        }

        // 灵感语音 · 开始 —— `thinking-low`（侧脸 + 一个气泡点）：读作「在想点子」，与「录音」那颗麦克风、
        //  「唤醒小布」那颗对话框都分得开。用户口径指定这一颗。
        Action.OPLUS_PREFIX + RomProfile.NAME_VOICE_MEMORY -> {
            TablerIcons.Outline.ThinkingLow
        }

        // 灵感语音 · 结束 —— `player-stop`（方块）：与「开始」那颗侧脸同一族但一眼读作「停下」，
        //  不与「录音」的麦克风、「录屏」的摄像机撞形。
        Action.OPLUS_PREFIX + RomProfile.NAME_VOICE_MEMORY_STOP -> {
            TablerIcons.Outline.PlayerStop
        }

        // 唤醒小布（语音助手）—— `message-ai`（带 AI 角标的对话框）：与「录音」的麦克风、
        //  「灵感语音」的闪光都不同，一眼读作「叫起那个会说话的助手」。
        Action.OPLUS_PREFIX + RomProfile.NAME_VOICE_ASSIST -> {
            TablerIcons.Outline.MessageAi
        }

        // 录音
        Action.OPLUS_PREFIX + RomProfile.NAME_RECORDING -> {
            TablerIcons.Outline.Microphone
        }

        // 录屏 —— 与「录音」那颗麦克风分开：一个是录画面、一个是录声音，同字形会读错。
        //  用摄像机那一颗（`video`）：用户口径指定。
        Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_RECORD,
        Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_RECORD_START,
        -> {
            TablerIcons.Outline.Video
        }

        // 「只结束」那一档：停止那颗 —— 菜单里与上面两档并排时，一眼分得出哪一行是停。
        Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_RECORD_STOP -> {
            TablerIcons.Outline.PlayerStop
        }

        // 屏幕翻译 —— `language`（「A 文」那个字形）就是「翻译」的通用读法。
        //  与「语言」那几行不撞：那几行用的是 `world` / `abc` / `alphabet-chinese`，
        //  而这一格是「把屏上的字翻掉」这个动作，不是「选语言」。
        Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_TRANSLATE -> {
            TablerIcons.Outline.Language
        }

        // 相机 / 计算器 —— 就是那两件东西自己的字形，别与「打开应用」那颗四宫格混起来：
        //  这两格的价值在于「锁屏上也能直接显示」，一颗通用图标看不出它和打开应用的区别。
        Action.OPLUS_PREFIX + RomProfile.NAME_CAMERA -> {
            TablerIcons.Outline.Camera
        }

        Action.OPLUS_PREFIX + RomProfile.NAME_CALCULATOR -> {
            TablerIcons.Outline.Calculator
        }

        // 响铃状态（那一格；菜单里的「循环」见 [specIcon]）
        //  `bell-cog`：铃铛带齿轮 —— 这一格进的是「设置哪一档」，不是「响铃」这个动作本身。
        ActionCatalog.RINGER_TILE -> {
            TablerIcons.Outline.BellCog
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

        // 音量 + / −（注入一次音量键）—— 与「增加 / 减少音量」那两档**同一颗**：
        //  菜单里那两行说的是「加一档 / 减一档」，这两条说的是「按一下音量键」，落到用户眼里都是
        //  「让声音大一点 / 小一点」，给两套字形反而要多认一次；分开画的只有格子那颗（不带角标）。
        Action.keyToken(KeyId.KEYCODE_VOLUME_UP) -> {
            DrawnIcons.VolumeAdd
        }

        Action.keyToken(KeyId.KEYCODE_VOLUME_DOWN) -> {
            DrawnIcons.VolumeSub
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
                    TablerIcons.Outline.TextScanAi
                }

                //  单条命令那一族（`shell:` 原文 / `cmd:` 名字）与类型图标**同一颗** —— 字形只定义
                //  在 `PageIcons.CommandSingle` 一处，这里引用它，别再各写一个终端字形
                token.startsWith(Action.SHELL_PREFIX) -> {
                    PageIcons.CommandSingle
                }

                // 区别只在内容存哪（`shell:` 是原文，`cmd:` 是名字）
                token.startsWith(Command.PREFIX) -> {
                    PageIcons.CommandSingle
                }

                // 快捷方式（`appshortcut:`）与「打开应用」共用一颗字形：都是「把某个东西叫起来」。
                //  `apps`（四宫格里各是一个应用形状）读作「一堆应用」，比原来的 `layout-grid`
                //  （四个方块）更能与「显示系统应用」那颗 `grid-dots` 分开。
                token.startsWith(Action.APP_PREFIX) || token.startsWith(Action.APP_SHORTCUT_PREFIX) -> {
                    TablerIcons.Outline.Apps
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

                // 系统开关（`setting:<id>` / `:on` / `:off`）—— 每一条都有自己的字形，从清单里
                //  一眼分得开；三态里「打开 / 关闭」另换开关键字形（见 [systemSettingIcon]）
                token.startsWith(Action.SETTING_PREFIX) -> {
                    systemSettingIcon(token)
                }

                // 「以小窗口打开」（`window:目标`）：新起一个窗口 —— 与「把前台的收成小窗」那两档
                // （`zoom:mini` / `zoom:mini:collapse`，见上面）是**三支不同字形**，别顺手并回来。
                token.startsWith(Action.WINDOW_PREFIX) -> {
                    TablerIcons.Outline.WindowMinimize
                }

                // 自定义变量（`setvar:计数+=1`）—— 变量自己的字形，与变量页那一列同一颗
                token.startsWith(Action.SETVAR_PREFIX) -> {
                    TablerIcons.Outline.Variable
                }

                // 结束应用（`proc:stop:包名`）—— 方块（「停掉」）；认不出（手改坏了）给那颗芯片
                token.startsWith(Action.PROCESS_PREFIX) -> {
                    if (ActionCatalog.actionOf(token) is Action.Process) {
                        TablerIcons.Outline.PlayerStop
                    } else {
                        TablerIcons.Outline.Cpu
                    }
                }

                // 屏幕亮度 / 音量（`bright:+15` / `volume:+10`）：**token 带数值**，上面的常量分支
                // 接不住，漏了这里就掉到下面那颗 `Dots` —— 步骤行里看着像「没配图标」。
                // 三档的取法与菜单里那三行同一套（见 `Glyph` 那段）。
                token.startsWith(Action.BRIGHT_PREFIX) -> {
                    brightnessGlyph(token)
                }

                token.startsWith(Action.VOLUME_PREFIX) -> {
                    volumeGlyph(token)
                }

                else -> {
                    TablerIcons.Outline.Dots
                }
            }
        }
    }

/**
 * 亮度那一档在步骤行里的图标 —— 与菜单里那三行同一套（底图 + 右下角角标，见 `DrawnIcons`）：
 * 更亮是加号、更暗是减号、「设置为」是齿轮。
 *
 *  认不出（手改坏了）时给「设置为」那颗：那是这一族里最中性的一档。
 */
private fun brightnessGlyph(token: String): ImageVector =
    when ((ActionCatalog.actionOf(token) as? Action.Brightness)?.mode) {
        Action.Brightness.Mode.ADD -> DrawnIcons.BrightnessAdd
        Action.Brightness.Mode.SUB -> DrawnIcons.BrightnessSub
        else -> DrawnIcons.BrightnessSet
    }

/**
 * 音量那一档同上：同一颗喇叭底图，右下角换角标（加 / 减 / 齿轮），格子那颗不带角标。
 */
private fun volumeGlyph(token: String): ImageVector =
    when ((ActionCatalog.actionOf(token) as? Action.Volume)?.mode) {
        Action.Volume.Mode.ADD -> DrawnIcons.VolumeAdd

        Action.Volume.Mode.SUB -> DrawnIcons.VolumeSub

        //  认不出（手改坏了）时给「设置为」那颗：这一族里最中性的一档
        else -> DrawnIcons.VolumeSet
    }

/**
 * 「系统开关」里那几条开关的字形 —— `setting:<id>[:on|:off]` 在这里翻译成图标。
 *
 *  **两种维度叠在一起**：
 *   · 开关**种类**决定基础字形（蓝牙 / WIFI / 手电筒 …）；
 *   · **状态**决定要不要换成开关键: 明确「打开」用 `toggle-right`、明确「关闭」用 `toggle-left`
 *     —— 切换那一档留开关自己的字形（它表达的是「这个开关」，不是某个状态）。
 *  这样三行（切换 / 打开 / 关闭）在菜单里字形互不相同，扫一眼就分得清。
 *
 *  **全项目唯一一处**（与 [actionIcon] 里那条前缀判断配套）：清单本身在
 *  `core/model/SystemSwitches`，那层没有 Compose，字形只能落在这里。加一条开关 = 在那个清单里
 * 加一行 + 这里加一支 —— 漏了这里会掉到 [TablerIcons.Outline.Dots]，看着像「没配图标」。
 */
private fun systemSettingIcon(token: String): ImageVector {
    //  与 `stepTitle` 同一口径：按 token 取记忆的解析结果，不在这条「每次重组都走」的路上现解析。
    val setting = ActionCatalog.actionOf(token) as? Action.Setting
    return when (setting?.on) {
        true -> TablerIcons.Outline.ToggleRight
        false -> TablerIcons.Outline.ToggleLeft
        null -> systemSwitchKindIcon(setting?.id ?: token.removePrefix(Action.SETTING_PREFIX))
    }
}

/**
 * 开关**种类**那一层的字形表 —— 一条开关一颗字形，表里没有的掉到 [TablerIcons.Outline.Dots]。
 *
 *  表里存的是 **lambda 而不是现成的矢量**：这些都是顶层 `val`，写成 `"bt" to TablerIcons.Outline.Bluetooth`
 *  就会在类初始化那一刻把十几颗全建出来 —— 与 [specIcon] 那条「读一颗建一颗」的取舍正好相反
 *  （整张表预建是冷启动后第一次进页掉帧的那笔账）。
 *
 *  「老配置里可能还留着 `setting:dnd`」那一支与「手电筒用网格里同一颗字形」那两条理由，
 *  原先写在各支注释里，搬到这里一并保留：
 *   · `dnd` —— 现在「免打扰」走 `oplus:` 那条（见 [actionIcon]），留着只为旧文件的图标不变成 Dots；
 *   · `flashlight` —— 与网格里那一格（`Action.FLASHLIGHT`）同一颗，同一个意思不该有两个长相；
 *   · `extradim` —— 半个太阳（「再压一档亮度」），与 `darkmode` 的月亮分开：那条改配色，这条改屏幕暗到什么程度。
 */
private val SWITCH_KIND_ICONS: Map<String, () -> ImageVector> =
    mapOf(
        "bt" to { TablerIcons.Outline.Bluetooth },
        "wifi" to { TablerIcons.Outline.Wifi },
        "cell" to { TablerIcons.Outline.CellSignal4 },
        "rotation" to { TablerIcons.Outline.RotateRectangle },
        "nfc" to { TablerIcons.Outline.Nfc },
        "airplane" to { TablerIcons.Outline.Plane },
        // 定位服务：与系统「位置信息」那颗准星同一套读法
        "location" to { TablerIcons.Outline.Location },
        "dnd" to { TablerIcons.Outline.Moon },
        "flashlight" to { DrawnIcons.Flashlight },
        "darkmode" to { TablerIcons.Outline.MoonStars },
        "powersave" to { TablerIcons.Outline.BatteryEco },
        "extradim" to { TablerIcons.Outline.SunLow },
        //  自动亮度：太阳 + 一个 A（与「跟随系统」那行同一颗），与上下两档「亮度本身」分开
        "autobrightness" to { TablerIcons.Outline.BrightnessAuto },
    )

/** 开关**种类**那一层的字形 —— 查 [SWITCH_KIND_ICONS]。 */
private fun systemSwitchKindIcon(id: String): ImageVector = SWITCH_KIND_ICONS[id]?.invoke() ?: TablerIcons.Outline.Dots

/**
 * 界面处处共用的那一颗图标 —— [specIcon] 的**按需版**，带进程内缓存。
 *
 *  网格格子、格子里那行小字、「添加命令」浮层的行、入口格菜单的行，一律走这里 ——
 *  同一格在哪一处露面都是同一颗字形（同一个 `ImageVector` 实例），也不再各自现调 [specIcon]。
 *
 *  ## 为什么是「读一颗建一颗」而不是先把整张表建出来
 *
 *  内置动作 + 各层菜单的取值 + 命令库，合起来有一百五十多项，而一屏只画得出二三十格。
 *  原来是在页面级 `derivedStateOf` 里把整条队列 `associateWith { specIcon(it) }`：第一次有人读，
 *  就在**组合期**把那一百多颗全建一遍（每颗要建一串 path 节点）—— 冷启动后第一次进
 *  「按键绑定」页那一下轻微掉帧就是这一笔。改成读一颗建一颗之后，首帧只建真正画到的那几十颗，
 *  其余等它被画到时再建；取值完全一样，先建后建不影响结果。
 *
 *  缓存放在**文件级**而不是某个页面对象里：读它的那几处在**不同的树**里（网格在
 *  `BindingScreen`、「添加命令」浮层在 `CommandScreen`），各自记会各建一份。
 *  命令库变了**不清缓存**：键是 [ActionCatalog.Spec]（含 token 与标题，是 `data class`），
 *  改个名字就是新键；旧键留在表里只是几个旧引用。
 */
private val iconCache = HashMap<ActionCatalog.Spec, ImageVector>()

internal fun tileIcon(spec: ActionCatalog.Spec): ImageVector = iconCache.getOrPut(spec) { specIcon(spec) }

/**
 * 一条清单条目（[ActionCatalog.Spec]）的图标 —— **界面不直接调它**，一律走 [tileIcon]
 * （那里带缓存，且只在这一格真被画到时才建）。也不要直接调 [actionIcon]。
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
        //  单条命令按它自己的 root 档取字形 —— 抽屉里那一行与命令列表里那张卡必须是同一颗
        commandKind != null -> commandIcon(commandKind, spec.commandSteps, spec.commandRoot)

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
 *
 * ## 为什么压掉 `CyclomaticComplexMethod`
 *
 *  这是一张**纯映射**：分支数恒等于 [ActionCatalog.Glyph] 的项数，加一档动作就多一支 ——
 *  拆成几个小函数只是把计数挪走，既不降复杂度也不增可读性。保留 `when` 而不是改成查表，
 *  是为了「加了枚举值忘了配图标」在**编译期**就报错（查表会退化成运行时的兜底图标）。
 */
@Suppress("CyclomaticComplexMethod")
private fun glyphIcon(glyph: ActionCatalog.Glyph): ImageVector =
    when (glyph) {
        ActionCatalog.Glyph.RingerTile -> TablerIcons.Outline.BellCog

        ActionCatalog.Glyph.Repeat -> PageIcons.RingerCycle

        ActionCatalog.Glyph.MediaTile -> TablerIcons.Outline.PhotoVideo

        ActionCatalog.Glyph.NavigationCog -> PageIcons.NavigationCog

        ActionCatalog.Glyph.Click -> PageIcons.Click

        ActionCatalog.Glyph.Panels -> PageIcons.Panels

        ActionCatalog.Glyph.MiniWindow -> PageIcons.MiniWindow

        // 「系统开关」入口格：带齿的齿轮（`settings-cog`，这一格进的是清单，不是某个取值）
        ActionCatalog.Glyph.SystemSwitch -> TablerIcons.Outline.SettingsCog

        // 「屏幕亮度」那一格：一颗太阳（亮度本身）
        ActionCatalog.Glyph.Brightness -> DrawnIcons.BrightnessTile

        // 菜单的「增加 / 减少 / 设置为」：太阳缩一档，右下角换角标（加 / 减 / 齿轮）——
        //  与「音量」那张菜单同一套角标，两族只有底图不同。
        ActionCatalog.Glyph.BrightnessUp -> DrawnIcons.BrightnessAdd

        ActionCatalog.Glyph.BrightnessDown -> DrawnIcons.BrightnessSub

        ActionCatalog.Glyph.BrightnessSet -> DrawnIcons.BrightnessSet

        // 「音量」那一格：喇叭 + 两道声波（这一格说的就是音量本身）
        ActionCatalog.Glyph.Volume -> DrawnIcons.VolumeTile

        // 菜单的「增加 / 减少 / 设置为」：同一颗喇叭缩一档 + 右下角角标，与亮度那张菜单同尺
        ActionCatalog.Glyph.VolumeUp -> DrawnIcons.VolumeAdd

        ActionCatalog.Glyph.VolumeDown -> DrawnIcons.VolumeSub

        ActionCatalog.Glyph.VolumeSet -> DrawnIcons.VolumeSet

        // 「小布 AI」入口格：`sparkle-highlight`（闪光带高亮框）—— 与「灵感语音」那颗普通闪光
        //  分得开：这一格是**一类东西**（四件小布能力），不是「灵感语音」那个取值本身。
        ActionCatalog.Glyph.XiaobuAi -> TablerIcons.Outline.SparkleHighlight

        // 「自定义变量」入口格：`variable`（那个 `x` 里带 y 的符号本身）—— 这一格进的是变量清单
        ActionCatalog.Glyph.Variables -> TablerIcons.Outline.Variable

        // 「锁屏快捷」入口格：一道闪电 —— 说的是「按一下就开」这件事本身（相机 / 计算器在菜单里）。
        //  不用锁：锁是「锁屏」这个**状态**的读法，而这一格的动作是「打开」，摆一把锁会被读成
        //  「锁定 / 锁屏」那一档动作（也与「手电筒」那颗灯泡太近）。
        ActionCatalog.Glyph.Keyguard -> TablerIcons.Outline.Bolt

        // 「应用控制」入口格：`category`（一个框 + 里面两小格）—— 管的是「哪一个应用」。
        ActionCatalog.Glyph.AppControl -> TablerIcons.Outline.Category

        // 「应用控制」菜单里「打开应用」那一行：`category-plus`，与结束那一颗 `category-minus`
        //  配成一对（同一族的底子、角上换符号）。
        //  为什么不再自己拼（一叠方块 + 右下角三角 / 方块）：那套拼法的底图必须给右下角让位，
        //  而这一族的辨识度全在「四块」上 —— 少一块就只剩三个方框，读不出是「应用」
        //  （用户口径「别画了，换成 category / category-minus / category-plus」）。
        ActionCatalog.Glyph.AppOpen -> TablerIcons.Outline.CategoryPlus

        // 「应用控制」菜单里「结束应用」那一行：`category-minus` —— 与打开那一颗同一族。
        ActionCatalog.Glyph.StopApp -> TablerIcons.Outline.CategoryMinus

        // 「灵感语音」那一行（点开再选开始 / 结束）：与开始那一档同一颗侧脸，
        //  这一行代表的是**这一族**，具体哪一档在选择器里说。
        ActionCatalog.Glyph.VoiceMemory -> TablerIcons.Outline.ThinkingLow

        // 「录屏」那一格 / 那一行 / 三态弹窗的标题：**同一颗摄像机**。
        //  三档不再各画一颗（用户口径「录屏不用换图标了，改成用选择器选三态」）：它们是同一个
        //  动作的三种状态，而这套角标在图里占的是右下角那块地 —— 一颗底图要同时给三角 / 方块
        //  留位，底图本身就得缩，录屏那颗摄像机瘦下去之后反而认不出是摄像机了。
        ActionCatalog.Glyph.ScreenRecord -> DrawnIcons.ScreenRecordTile
    }

/**
 * 一条自定义命令的类型图标：单条 → [PageIcons.CommandSingle] / [PageIcons.CommandRoot]，
 * 多条 → 按步骤数的 [commandSerialIcon]。
 *
 *  **单条分两档**：以 root 执行（只走 `su`）与不申请 root（只走 `sh`）是两颗字 —— 命令列表里
 *  一眼要能看出哪几条会提权。多条命令没有这一档（它的 `root` 只管正文里那些 `shell:` 步骤，
 *  不是这条命令的身份），仍按步骤数取方框。
 *
 * 三个地方共用这一份：网格格子与「已保存的命令」卡片、步骤行里引用另一条命令的那枚小图标
 * （[stepIcon] 从命令库查出被引用的那条之后转给它）。
 *
 * @param root 单条命令是否以 root 执行（`Command.root`）；多条忽略
 */
internal fun commandIcon(
    kind: CommandKind,
    steps: Int,
    root: Boolean = false,
): ImageVector =
    when (kind) {
        CommandKind.SERIAL -> commandSerialIcon(steps)
        CommandKind.SINGLE -> if (root) PageIcons.CommandRoot else PageIcons.CommandSingle
    }

/**
 * 「多条命令」的类型图标 —— 按步骤数取 `box-multiple-N`，一眼能看出这条有几步。
 *
 * 档位与 `Command.MAX_STEPS` 同宽（1~9）；0 步（还没加步骤的草稿）与超出上限的都以首尾两档收口，
 * 图标只说明「几步」，不承担校验。
 */
internal fun commandSerialIcon(steps: Int): ImageVector = SERIAL_STEP_ICONS[steps.coerceIn(1, SERIAL_STEP_ICONS.size) - 1]

/**
 * 步骤数 → 字形，下标 0 是 1 步。长度必须等于 `Command.MAX_STEPS`，否则上一步的钳位会串档。
 *
 *  上限取 9 正是因为图标库的数字字形只画到这里：再多一步就没有「第 10 步」可画，
 *  要么拿一颗不带数字的图标糊过去，要么上限跟着数字走 —— 这里选后者。
 */
private val SERIAL_STEP_ICONS: List<ImageVector> =
    listOf(
        TablerIcons.Outline.BoxMultiple1,
        TablerIcons.Outline.BoxMultiple2,
        TablerIcons.Outline.BoxMultiple3,
        TablerIcons.Outline.BoxMultiple4,
        TablerIcons.Outline.BoxMultiple5,
        TablerIcons.Outline.BoxMultiple6,
        TablerIcons.Outline.BoxMultiple7,
        TablerIcons.Outline.BoxMultiple8,
        TablerIcons.Outline.BoxMultiple9,
    )
