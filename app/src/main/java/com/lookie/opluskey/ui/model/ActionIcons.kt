package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.KsuCompat
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.core.model.SystemSwitches
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AdjustmentsCog
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindow
import io.github.lyxnx.compose.ui.tablericons.outline.AppWindowBottom
import io.github.lyxnx.compose.ui.tablericons.outline.Apps
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowBackUp
import io.github.lyxnx.compose.ui.tablericons.outline.Ban
import io.github.lyxnx.compose.ui.tablericons.outline.BellCog
import io.github.lyxnx.compose.ui.tablericons.outline.BellOff
import io.github.lyxnx.compose.ui.tablericons.outline.BellRinging
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
import io.github.lyxnx.compose.ui.tablericons.outline.Calculator
import io.github.lyxnx.compose.ui.tablericons.outline.Camera
import io.github.lyxnx.compose.ui.tablericons.outline.Cards
import io.github.lyxnx.compose.ui.tablericons.outline.Category
import io.github.lyxnx.compose.ui.tablericons.outline.CategoryMinus
import io.github.lyxnx.compose.ui.tablericons.outline.CategoryPlus
import io.github.lyxnx.compose.ui.tablericons.outline.Click
import io.github.lyxnx.compose.ui.tablericons.outline.Command
import io.github.lyxnx.compose.ui.tablericons.outline.CornerDownLeft
import io.github.lyxnx.compose.ui.tablericons.outline.Cpu
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobileVibration
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import io.github.lyxnx.compose.ui.tablericons.outline.Home
import io.github.lyxnx.compose.ui.tablericons.outline.Language
import io.github.lyxnx.compose.ui.tablericons.outline.Link
import io.github.lyxnx.compose.ui.tablericons.outline.MessageAi
import io.github.lyxnx.compose.ui.tablericons.outline.Microphone
import io.github.lyxnx.compose.ui.tablericons.outline.Moon
import io.github.lyxnx.compose.ui.tablericons.outline.NavigationCog
import io.github.lyxnx.compose.ui.tablericons.outline.Notification
import io.github.lyxnx.compose.ui.tablericons.outline.PencilAi
import io.github.lyxnx.compose.ui.tablericons.outline.PhotoVideo
import io.github.lyxnx.compose.ui.tablericons.outline.PictureInPictureTop
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerPlay
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerStop
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerTrackNext
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerTrackPrev
import io.github.lyxnx.compose.ui.tablericons.outline.Power
import io.github.lyxnx.compose.ui.tablericons.outline.Repeat
import io.github.lyxnx.compose.ui.tablericons.outline.Screenshot
import io.github.lyxnx.compose.ui.tablericons.outline.SettingsCog
import io.github.lyxnx.compose.ui.tablericons.outline.SparkleHighlight
import io.github.lyxnx.compose.ui.tablericons.outline.Switch3
import io.github.lyxnx.compose.ui.tablericons.outline.TextScanAi
import io.github.lyxnx.compose.ui.tablericons.outline.ThinkingLow
import io.github.lyxnx.compose.ui.tablericons.outline.Variable
import io.github.lyxnx.compose.ui.tablericons.outline.Video
import io.github.lyxnx.compose.ui.tablericons.outline.WindowMinimize

/**
 * **不在清单里的** token → 图标 —— 步骤行、执行提示、手写配置走这里。
 *
 * ## 清单条目不再走这条路
 *
 *  `Spec.glyph` 是必填的（`ActionCatalog.GlyphSource`），所以「这一格画什么」在清单里就定完了，
 *  [specIcon] 直接翻译那个声明，**不问 token**。
 *
 *  这就消掉了原先那个「两张必须手工同步的表」：清单里 25 处 `Glyph` + 这里的 33 支 token 分支，
 *  说的是同一件事；加一条动作漏了图标**不报错**，只是掉到 `Dots`（看着像「这条本来就没配图标」）。
 *  现在漏了当场编译失败，这里只剩「没有 `Spec` 可言」的那一半。
 *
 *  下面这些精确分支（`oplus:` 各场景 / 各 `keyevent:` 码 / 响铃三档…）仍然必要：
 *  步骤行里存的是 **token 本身**，它可能指向一个从清单里挑不到的动作（手写、导入、老配置），
 *  那种值没有 `Spec` 可查。它们是**兜底**，不是第二份真源 —— 清单里那几档的字形由
 *  `Spec.glyph` 声明，两边取到的是同一颗（图片缓存按 `Spec` 建，按 token 建的是另一条路）。
 */
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

        // 录音 —— 切换 / 只开始：同一颗麦克风（两档起的是同一条路，只有「在录时怎么办」不同）
        Action.OPLUS_PREFIX + RomProfile.NAME_RECORDING,
        Action.OPLUS_PREFIX + RomProfile.NAME_RECORDING_START,
        -> {
            TablerIcons.Outline.Microphone
        }

        // 录音 · 只结束：停止那颗 —— 与录屏「只结束」同一个读法，菜单里并排时一眼分得出哪一行是停
        Action.OPLUS_PREFIX + RomProfile.NAME_RECORDING_STOP -> {
            TablerIcons.Outline.PlayerStop
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
 * 「系统开关」里那几条开关的字形 —— 查 [systemSwitchKindIcon]（那张表在 `SwitchKindIcons.kt`：
 * 它对 `SwitchKind` 枚举做 `when`，加一条开关漏配字形当场编译失败）。
 *
 *  **三态共用同一颗「种类」字形**（蓝牙就是蓝牙、WIFI 就是 WIFI）：
 *  原先「打开 / 关闭」两档分别换成 `toggle-right` / `toggle-left`，于是同一个开关的三档
 *  长得像三个不同的东西 —— 而「系统开关」这一族本来就没有自己的字形，它们只是某条开关的
 *  三种取值（用户口径：「它们不是没有图标吗」）。状态由行首那句「打开蓝牙 / 关闭蓝牙」说。
 */
private fun systemSettingIcon(token: String): ImageVector = systemSwitchKindIcon(SystemSwitches.kindOf(token))

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
 *  `BindingPagerScreen`、「添加命令」浮层在 `CommandScreen`），各自记会各建一份。
 *  命令库变了**不清缓存**：键是 [ActionCatalog.Spec]（含 token 与标题，是 `data class`），
 *  改个名字就是新键；旧键留在表里只是几个旧引用。
 *
 *  **必须是并发安全的**：`tileIcon` 大多在主线程的组合期调，但 `specIcon` 里要问
 *  `PackageManager`（自定义命令取应用图标），已经有调用点从 IO 线程进来 —— 普通 `HashMap`
 *  并发 put 在扩容时会丢条目，极端情况下结构损坏。
 */
private val iconCache = java.util.concurrent.ConcurrentHashMap<ActionCatalog.Spec, ImageVector>()

internal fun tileIcon(spec: ActionCatalog.Spec): ImageVector = iconCache.getOrPut(spec) { specIcon(spec) }

/**
 * 一条清单条目（[ActionCatalog.Spec]）的图标 —— **界面不直接调它**，一律走 [tileIcon]
 * （那里带缓存，且只在这一格真被画到时才建）。也不要直接调 [actionIcon]。
 *
 *  ## 为什么这里不按 token 猜
 *
 *  `Spec.glyph` 是**必填**的（`ActionCatalog.GlyphSource`），所以「这一格画什么」在清单里就
 *  定完了，这里只做翻译。原先它是 `glyph ?: actionIcon(canonicalToken)` —— 空着就按 token
 *  猜，而猜的那张表在 [actionIcon] 里，两张表要手工同步：加一条动作漏了图标**不报错**，
 *  只掉到 `Dots`（看着像「这条本来就没配图标」）。
 *
 *  三种来源都是**声明**，不是猜：
 *   · [ActionCatalog.Glyph]（枚举自身就是一种 `GlyphSource`）→ [glyphIcon] 翻译；
 *   · `GlyphSource.ByCommand` → 命令自己的字形（单条 / 多条 / 几步 / 是否提权）；
 *   · `GlyphSource.BySwitchKind` → 开关种类的字形（同一开关三档共用一颗）。
 */
internal fun specIcon(spec: ActionCatalog.Spec): ImageVector =
    when (val glyph = spec.glyph) {
        is ActionCatalog.Glyph -> glyphIcon(glyph)
        is ActionCatalog.GlyphSource.ByCommand -> commandIcon(glyph.kind, glyph.steps, glyph.root)
        is ActionCatalog.GlyphSource.BySwitchKind -> systemSwitchKindIcon(glyph.id)
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

        // 「录音」那一格 / 三态弹窗的标题：与录音那两档取值同一颗麦克风。
        //  三档共一颗的理由同录屏：状态由小字 / 文案说（`录音 - 开始`），不靠字形。
        ActionCatalog.Glyph.Record -> TablerIcons.Outline.Microphone

        // ── 下面这一批是从 `actionIcon` 那张 token 表搬过来的 ──
        //  原先它们靠 `actionIcon(token)` 猜；`Spec.glyph` 必填之后清单条目自己声明，
        //  这里只是「名字 → 矢量」的翻译，不再是第二份判断。
        ActionCatalog.Glyph.NoOp -> TablerIcons.Outline.Ban

        // 「跟随默认」那一格：一支折回左下的箭头（`corner-down-left`），读作「这一档回到上一条路」。
        //  与「无操作」那颗禁止符号分开摆 —— 那一颗说「什么都不做」，这一颗说「交给按键绑定」。
        ActionCatalog.Glyph.Follow -> TablerIcons.Outline.CornerDownLeft

        ActionCatalog.Glyph.Flashlight -> DrawnIcons.Flashlight

        ActionCatalog.Glyph.PowerKey -> TablerIcons.Outline.Power

        ActionCatalog.Glyph.PowerMenu -> TablerIcons.Outline.AdjustmentsCog

        ActionCatalog.Glyph.FlashNote -> TablerIcons.Outline.PencilAi

        ActionCatalog.Glyph.VoiceAssist -> TablerIcons.Outline.MessageAi

        ActionCatalog.Glyph.VoiceMemoryStop -> TablerIcons.Outline.PlayerStop

        ActionCatalog.Glyph.ScreenRecognition -> TablerIcons.Outline.TextScanAi

        ActionCatalog.Glyph.ScreenTranslate -> TablerIcons.Outline.Language

        ActionCatalog.Glyph.Camera -> TablerIcons.Outline.Camera

        ActionCatalog.Glyph.Calculator -> TablerIcons.Outline.Calculator

        ActionCatalog.Glyph.Screenshot -> TablerIcons.Outline.Screenshot

        ActionCatalog.Glyph.Home -> TablerIcons.Outline.Home

        ActionCatalog.Glyph.Back -> TablerIcons.Outline.ArrowBackUp

        ActionCatalog.Glyph.Recents -> TablerIcons.Outline.Cards

        ActionCatalog.Glyph.Notification -> TablerIcons.Outline.Notification

        ActionCatalog.Glyph.ControlCenter -> TablerIcons.Outline.Command

        ActionCatalog.Glyph.MediaSwitch -> TablerIcons.Outline.Switch3

        ActionCatalog.Glyph.MediaPlay -> TablerIcons.Outline.PlayerPlay

        ActionCatalog.Glyph.MediaPause -> TablerIcons.Outline.PlayerStop

        ActionCatalog.Glyph.MediaPrev -> TablerIcons.Outline.PlayerTrackPrev

        ActionCatalog.Glyph.MediaNext -> TablerIcons.Outline.PlayerTrackNext

        ActionCatalog.Glyph.VolumeKeyUp -> DrawnIcons.VolumeAdd

        ActionCatalog.Glyph.VolumeKeyDown -> DrawnIcons.VolumeSub

        ActionCatalog.Glyph.MiniExpand -> TablerIcons.Outline.AppWindowBottom

        ActionCatalog.Glyph.MiniCollapse -> TablerIcons.Outline.PictureInPictureTop

        ActionCatalog.Glyph.RingerNormal -> TablerIcons.Outline.BellRinging

        ActionCatalog.Glyph.RingerVibrate -> TablerIcons.Outline.DeviceMobileVibration

        ActionCatalog.Glyph.RingerSilent -> TablerIcons.Outline.BellOff

        ActionCatalog.Glyph.Apps -> TablerIcons.Outline.Apps
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
