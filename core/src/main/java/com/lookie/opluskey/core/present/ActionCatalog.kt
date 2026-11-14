package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.KsuCompat
import com.lookie.opluskey.core.model.SystemSwitches
import com.lookie.opluskey.core.rom.RomProfile

/** 设置页上可选的「动作清单」（界面用的展示层数据）。 */
object ActionCatalog {
    /**
     * 界面字形名 —— **纯名字的封闭枚举**，一个条目一支字形。
     *
     *  为什么不是直接放图标对象：`core` 是注入侧也会加载的包，一、1 不许它认识界面库。
     *  这里只登记「用哪支字形」，`ui/model/icon/ActionIcons.kt` 把名字翻成图标（唯一一处映射）。
     *  加一档动作时：**要新字形才动那张映射表**，复用已有字形的条目一个字都不用改。
     */
    enum class Glyph : GlyphSource {
        /** 「响铃状态」那一格。名字说的是**槽位**而不是字形 —— 字形会换（现在是 `bell-cog`）。 */
        RingerTile,

        /** 响铃菜单首行「循环」（与上面共用一个 token，字形不同 ⇒ 只能按 glyph 分）。 */
        Repeat,

        /** 「媒体控制」那一格（与它菜单首行共用一个 token，同理；现在是 `photo-video`）。 */
        MediaTile,

        /** 「系统导航」标记格。 */
        NavigationCog,

        /** 「系统按键」标记格。 */
        Click,

        /** 「灵感语音」那一层入口行（开始 / 结束两档，格子代表不了其中任何一个）。 */
        VoiceMemory,

        /** 「通知与控制中心」标记格。 */
        Panels,

        /** 「前台转小窗」标记格。 */
        MiniWindow,

        /** 「系统开关」标记格。 */
        SystemSwitch,

        /** 「屏幕亮度」标记格。 */
        Brightness,

        /** 亮度菜单的「增加」那一行（太阳带加号角标），与格子同一族、角标不同。 */
        BrightnessUp,

        /** 亮度菜单的「减少」那一行（太阳带减号角标）。 */
        BrightnessDown,

        /** 亮度菜单的「设置为」那一行（太阳带齿轮角标）—— 与格子那颗不是同一颗。 */
        BrightnessSet,

        /** 「音量」标记格 —— 三档各不相同，格子代表不了任何一个，只能按 glyph 分。 */
        Volume,

        /** 「铃声音量」标记格 —— 与上面那颗并列的第二条命令（写铃声流，不是媒体流）。 */
        RingerVolume,

        /** 音量菜单的「增加」那一行（喇叭带向上箭头）。 */
        VolumeUp,

        /** 音量菜单的「减少」那一行（喇叭带向下箭头）。 */
        VolumeDown,

        /** 音量菜单的「设置为」那一行（喇叭带滑轨游标）—— 与格子那颗不是同一颗。 */
        VolumeSet,

        /** 铃声音量菜单的「增加」那一行（铃铛带加号角标）—— 底图与上面那三行不同，见 [RingerVolume]。 */
        RingerVolumeUp,

        /** 铃声音量菜单的「减少」那一行（铃铛带减号角标）。 */
        RingerVolumeDown,

        /** 铃声音量菜单的「设置为」那一行（铃铛带齿轮角标）。 */
        RingerVolumeSet,

        /** 「小布 AI」入口格（`sparkle-highlight`）：四件小布能力收在这一格后面。 */
        XiaobuAi,

        /**
         * 录屏那一格 / 挑三态那个弹窗的标题：摄像机底图。
         *
         *  **三档不再各画一颗**（用户口径「录屏不用换图标了，改成用选择器选三态」）：三档是
         *  同一个动作的三种状态，格子、弹窗、步骤行画的都是这一颗；「是开始还是结束」由
         *  小字 / 文案说（`录屏 - 开始`），不靠字形。
         */
        ScreenRecord,

        /**
         * 录音那一格（`ai:recording`）：麦克风底图，三档共用一颗 —— 理由与 [ScreenRecord] 同一条，
         * 「是开始还是结束」由小字 / 文案说（`录音 - 开始`），不靠字形。
         */
        Record,

        // ── 下面是**固定字形**的那一批：清单条目直接声明 [Glyph]。`actionIcon(token)` 保留原样，
        //    只服务「不在清单里的 token」（步骤行、手写配置）。

        /** 「无操作」（`none`）。 */
        NoOp,

        /**
         * 「跟随默认」（`follow`）—— 只给场景块那三个槽位用。
         *
         *  **不与 [NoOp] 共用一颗**：那一颗是禁止符号（「命中后什么都不做」），而这一档说的是
         *  「这一档不归这一块管，走按键绑定里那一档」—— 两件事在同一张网格里挨着摆，
         *  同形会串味。
         */
        Follow,

        /** 手电筒 —— 自绘那颗斜放的手电（图标库那颗角度不对）。 */
        Flashlight,

        /** 电源键（`keyevent:26`）—— 与「电源菜单」必须不同字形。 */
        PowerKey,

        /** 电源菜单（关机 / 重启那一层）—— 滑杆 + 齿轮（「可调的那一层菜单」）。 */
        PowerMenu,

        /** 一键闪记（侧键场景表那一条）。 */
        FlashNote,

        /** 唤醒小布（语音助手）—— 带 AI 角标的对话框。 */
        VoiceAssist,

        /** 灵感语音 · 结束 —— 方块（「停下」），与开始那颗侧脸同一族。 */
        VoiceMemoryStop,

        /** 小布识屏（侧边栏识别屏幕）。 */
        ScreenRecognition,

        /** 屏幕翻译 —— `language` 就是「翻译」的通用读法。 */
        ScreenTranslate,

        /** 关闭翻译 —— 同一个字形划掉（`language-off`），与上面那条一眼分得开。 */
        ScreenTranslateStop,

        /** 锁屏快捷里的相机 —— 相机自己的字形，不与应用四宫格混。 */
        Camera,

        /** 锁屏快捷里的计算器。 */
        Calculator,

        /** 截屏。 */
        Screenshot,

        /** 「计时器」那一格 —— 沙漏（等一段时间、往下漏）。 */
        Timer,

        /** 系统导航 · 主屏幕。 */
        Home,

        /** 系统导航 · 返回。 */
        Back,

        /** 系统按键 · 最近任务。 */
        Recents,

        /** 系统按键 · 通知中心。 */
        Notification,

        /** 系统按键 · 控制中心。 */
        ControlCenter,

        /** 媒体控制菜单的**首行** —— 与格子（[MediaTile]）共用 token，但那一格是「一类东西」、
         *  这一行是「一个具体取值」，只能靠字形分。 */
        MediaSwitch,

        /** 媒体 · 播放。 */
        MediaPlay,

        /** 媒体 · 暂停。 */
        MediaPause,

        /** 媒体 · 上一首。 */
        MediaPrev,

        /** 媒体 · 下一首。 */
        MediaNext,

        /** 音量 + 键（注入一次音量键）—— 与「音量增加」同一颗，但语义是「按了一下键」。 */
        VolumeKeyUp,

        /** 音量 − 键。 */
        VolumeKeyDown,

        /** 前台转小窗 · 展开（窗口 + 底部块）。 */
        MiniExpand,

        /** 前台转小窗 · 收起（画面挂在顶部那条）。 */
        MiniCollapse,

        /** 响铃 · 正常。 */
        RingerNormal,

        /** 响铃 · 振动。 */
        RingerVibrate,

        /** 响铃 · 静音。 */
        RingerSilent,

        /** 「选择应用」那一行（抽屉里那一步）—— 一堆应用的读法。 */
        Apps,

        /** 「自定义变量」入口格（`variable`）：点它当场挑一步变量操作，不是取值。 */
        Variables,

        /** 「锁屏快捷」入口格（`lock`）：相机 / 计算器两档收在这一格后面。 */
        Keyguard,

        /** 「应用控制」入口格（`appctl:`）：打开 / 结束两个状态收在这一格后面，点它弹一层菜单。 */
        AppControl,

        /** 「应用控制」菜单里「打开应用」那一行 —— 播放三角（与结束那颗方块配成一对）。 */
        AppOpen,

        /** 「应用控制」菜单里「结束应用」那一行 —— 与执行那一档（`proc:stop:`）同一颗字形。 */
        StopApp,
    }

    /**
     * 一条清单条目的图标**从哪儿来** —— [Spec.glyph] 的类型。
     *
     *  分两种「不是常数」的来源，因为「这一格画什么」有两个决定因素本来就不在清单里：
     *   · 自定义命令的字形由**它自己**决定（单条 / 多条 / 几步 / 是否提权）；
     *   · 「系统开关」的字形由**开关种类**决定（蓝牙 / WIFI / NFC…），而种类是一张数据表。
     *
     *  把它们硬塞进 [Glyph] 会逼出 `Glyph.Command` 这种占位值 —— 占位值一出现，
     *  「必填」就又退化成「随便填一个能编译的值」，约束白设。
     *
     *  **[Glyph] 本身就是这个接口的一种实现**：枚举的每个取值就是「一支固定字形」，
     *  所以清单里每一处 `glyph = Glyph.X` 都不用套一层包装（`Fixed(Glyph.X)` 那种写法
     *  除了啰嗦没有别的作用）。
     */
    sealed interface GlyphSource {
        /**
         * 由命令本身决定：单条 / 多条、几步、是否以 root 执行。
         *
         *  三个字段照抄 [Spec] 上那三位 —— 它们本来就在 Spec 里，这里是「字形也看它们」的声明。
         */
        data class ByCommand(
            val kind: CommandKind,
            val steps: Int,
            val root: Boolean,
            /** 单条命令的正文 —— 按开头的前缀挑格式图标（`shell:` → 终端…）；多条命令不看它。 */
            val body: String = "",
        ) : GlyphSource

        /**
         * 由开关**种类**决定（`bt` / `wifi` / `nfc`…）—— 同一个开关的三档共用同一颗种类字形。
         *
         *  @param id 与 `SwitchKind.id` 同一个口径
         */
        data class BySwitchKind(
            val id: String,
        ) : GlyphSource
    }

    /**
     * 一个可选动作的展示信息。
     *
     * @param name 展示名 —— **内置动作给字典键**（`Strings.*`，界面读 [label] 时过字典）；
     *   自定义命令给**用户起的名字**（[commandKind] 非空时原样显示、不过字典）。
     * @param token `Action.parse()` 认得的那种写法
     * @param commandKind 自定义命令的类型；内置动作恒为 `null`。图标据此选字形：网格格子只有一行文字，单条 / 多条只能靠图标区分。
     * @param commandSteps 自定义命令里「多条命令」的步骤数；单条与内置动作恒为 `0`。
     *   多条命令的图标按它换 `box-multiple-N`，光靠 [commandKind] 只能分出「是多条」、分不出「有几步」。
     * @param dividerBefore 弹菜单时**这一行前面**用一条**加深带**（`MenuEntry.GroupLine`）而不是
     *   相邻两行那条细线 —— 把一组菜单切成两段。目前两处：媒体控制（「播放 / 暂停 / 播放 / 暂停」
     *   与「上一首 / 下一首」之间）、响铃状态（「循环」与那三档之间）。
     *   网格格子、步骤行、格子里那行小字都不读它 —— 只有菜单的分隔线看这个字段。
     */
    data class Spec(
        val name: String,
        val token: String,
        val commandKind: CommandKind? = null,
        val commandSteps: Int = 0,
        /**
         * 这条自定义命令**是否以 root 执行**（`Command.root`）—— 只有单条命令用得上。
         *
         *  界面据此把「su / 非 su」画成两颗字形（见 `ui/model/ActionIcons.commandIcon`），
         *  所以它必须跟着条目一起走到渲染层：抽屉与网格里那一行也是从命令库来的，
         *  光有类型与步数分不出这一档。
         */
        val commandRoot: Boolean = false,
        val dividerBefore: Boolean = false,
        /**
         * 这一格的图标**从哪儿来** —— 必填，没有「不声明」这一档。
         *
         * ## 为什么改成必填
         *
         *  原先这里是 `Glyph? = null`，`null` 的意思是「界面按 token 自己去猜」。于是同一个问题
         *  有两张表：清单里的 [Glyph]，界面 `ui/model/ActionIcons.actionIcon` 里按 token 的兜底
         *  分支。两张表必须手工同步 —— 加一条动作漏了图标**不报错**，只是掉到 `Dots`，
         *  看着像「这条本来就没配图标」。
         *
         *  现在字形随条目一起**必填**：漏了当场编译失败。
         *
         * ## 为什么不是直接 `Glyph`
         *
         *  有两类条目的字形本来就由**别的字段**决定，塞进 [Glyph] 会逼出占位值：
         *   · 自定义命令 —— 单条 / 多条、几步、是否提权（[ByCommand]）；
         *   · 「系统开关」的每一种开关 —— 字形由种类 id 决定，而种类是数据（[BySwitchKind]）。
         *
         *  所以这里是「字形的来源」而不是「字形」本身。`ui/model/ActionIcons.specIcon` 对它做
         *  `when`，三档各自取图标；`actionIcon(token)` 退回**兜底**位置 —— 它只服务
         *  「不在清单里的 token」（步骤行、手写配置）。
         */
        val glyph: GlyphSource,
    ) {
        /**
         * 界面上的叫法 —— [name] 过一遍字典。
         *
         *  **自定义命令不过字典**：[commandKind] 非空 = 这条来自命令库，而它的 [name] 是**用户起的
         *  名字**。拿去查字典会把「截图」「录音」「静音」这类名字显示成内置动作的译文
         *  （切到英文后更明显）—— 内置那一批的 [commandKind] 恒为 `null`，正是这条判据的依据。
         *
         *  取值 [token] 与所有判定都不受影响：写进配置的永远是 token。
         *
         *  查不到键时显示的是**键本身**（`I18n.get` 的兜底口径），不是某一段中文 ——
         *  这一处刻意不留中文兜底：字典缺项时宁可显示得像「少了点什么」，也不要静默显示中文
         *  （那正是原来那张「中文原文当键」的表最难发现的地方）。字典覆盖由 CI 逐键核对。
         */
        val label: String
            get() = if (commandKind == null) I18n.get(name) else name
    }

    // 「交还系统」那一格（`native` / `passthrough`）**已删**：
    //  做不到单个手势级的交还 —— 一交还就是整键，同一把键上双击 / 长按的命令也跟着被系统吞掉。
    //  取舍与依据写在 `docs/runtime/key-to-gesture.md` 的「交还系统为什么不提供」。

    /**
     * 「打开应用」这一步的取值 —— 永不写进配置，只是「点它去挑应用」的关卡标记。
     *
     *  它**不在任何清单里**：网格与「添加命令」抽屉那一格都是 [APP_CONTROL_TILE]（应用控制，
     *  打开 / 结束两个状态）。这个常量只剩 `app:` 前缀本身在用（解析与「打开应用」那一族的判定）。
     */
    const val APP_TILE = Action.APP_PREFIX

    /** 「系统开关」这一格的取值 —— 永不写进配置，只是「点它去挑一个开关」的关卡标记。 */
    const val SWITCH_TILE = Action.SETTING_PREFIX

    /**
     * 「自定义变量」这一格的取值 —— 与 [APP_TILE] 同一个性质：**永不写进配置**，
     * 只是「点它当场挑一步变量操作」的关卡标记（见 `ui/screen/variables/`）。
     *
     *  与别的入口格不同，它那一族取值（`setvar:`）**不在静态菜单里**：变量是运行时收集的，
     *  所以「点开之后列什么」由界面层现读变量清单，`MODE_MENUS` 里没有它。
     */
    const val VARS_TILE = "vars:"

    /**
     * 小布识屏那条 shell 命令 —— 取值照搬 KSU 模块，但模块侧不走 shell，
     * 而是把同一串 intent 直接 `startForegroundService`（见 `host/action/AppLauncher.startSidebar`）。
     */
    private val SIDEBAR = Action.SHELL_PREFIX + KsuCompat.SIDEBAR_CMD

    /** 截图 —— 取值是 KSU 的 `service call`，模块侧换成注入 `KEYCODE_SYSRQ`。 */
    private val SCREENSHOT = Action.SHELL_PREFIX + KsuCompat.SCREENSHOT_CMD

    /** 电源菜单 —— KSU 没有这项；执行侧在 system_server 里反射调 PWM，不走按键长按。 */
    private val POWER_MENU = Action.POWER_MENU

    /**
     * 把**前台**那个应用收成小窗 —— 执行侧走的是 `window:` 同一族小窗接口，只把目标换成当前前台活动。
     *
     * 展开档的取值。与 [MINI_COLLAPSE] 同属**入口格**：[MINI_TILE]，点它弹 [MINI_MODES] 菜单。
     */
    private val MINI_WINDOW = Action.ZOOM_MINI

    /**
     * 收起档：把前台应用收成右上角那条最小化窗（`zoom:mini:collapse`）。
     *
     * 与展开档的差别只在尺寸档 —— 服务端凭 `mZoomLaunchFlags` 分流（`2` = 展开、`32` = 收起），
     * 落点几何由服务端自己算。见 `core/model/Action.MiniWindow`。
     */
    private val MINI_COLLAPSE = Action.ZOOM_MINI_COLLAPSE

    /** 「无操作」的取值（[STEP_GROUPS] 要把它剔掉，所以单独给个常量）。 */
    private val NONE_TOKEN = Action.NONE

    /** 「跟随默认」的取值 —— 只出现在 [BRANCH_GROUPS] 里（见那张表的注释）。 */
    private val FOLLOW_TOKEN = Action.FOLLOW

    /**
     * 系统侧键场景表里的三档（落点见 `core/model/RomProfile`）：一键闪记 / 免打扰 / 录音。
     * 系统自己的侧键设置里就是这三项，调用目标逐字抄自它的场景表。
     */
    private val FLASH_MEMORY = Action.OPLUS_PREFIX + RomProfile.NAME_FLASH_MEMORY

    /** 录屏 —— 与其它 `oplus:` 档同一条：名字在 [RomProfile] 那一处，这里只拼 token。 */
    private val SCREEN_RECORD = Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_RECORD

    /** 录屏 —— **只开始** / **只结束**（三态里的另外两档，见 [SCREEN_RECORD_MODES]）。 */
    private val SCREEN_RECORD_START = Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_RECORD_START
    private val SCREEN_RECORD_STOP = Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_RECORD_STOP

    /**
     * 「录屏」那一行的标记（`ai:screen_record`）—— 与 [VOICE_TILE] 同类：**永不写进配置**。
     *
     *  开始 / 结束 / 切换是三档真取值，格子代表不了其中任何一档 —— 但这里**没有中间那层菜单**：
     *  格子本身就是「录屏」这个动作，点它直接弹一个三行单选弹窗（见 [stateChoices]）。用户口径
     *  「网格 / 菜单里视为一个动作，点击弹 dialog 选，行尾不用 selector」。
     */
    const val SCREEN_RECORD_TILE = "ai:screen_record"

    /**
     * 「录屏」那三档：**切换 / 开始 / 结束**，顺序即弹窗行序。
     *
     *  「切换」就是原来那一档（宿主按进程内记账翻面）：单绑一档手势时它最顺手，所以排在
     *  最上面（用户口径「录屏三态选择器的切换移到最上面」）；拆出开始 / 结束之后，想
     *  「一键只开始」「一键只停」也有地方放 —— 前者给同一个界面、不登记停止广播，
     *  后者只发那条停止广播（见 `RomProfile.BuiltIn`）。
     *
     *  **三行共用那一颗摄像机**（[Glyph.ScreenRecord]）：三档只在那个单选弹窗里露面，而弹窗是
     *  纯文字行（见 `SinglePickerDialog`）—— 字形用不上，但 `Spec.glyph` 是必填的。
     *  给「那个动作自己的字形」而不是随便填一个：这几行确实是「录屏」的三档，将来弹窗要画图标
     *  时不必再回来改这里。
     *
     *  **名字只是动词**（`切换` / `开始` / `结束`），宾语（`录屏`）不在字典里：选择器那一层
     *  用 `common.verb_name_format` 拼成「切换录屏」，句子那一侧由 `CommandTitle` 拼成
     *  「录屏 - 切换」。同一个动作两处各写一份，改字典时必然只改到一处 —— 那正是原先
     *  「切换录屏 / 录屏 - 切换」两套说法的来源。
     */
    val SCREEN_RECORD_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_SCREEN_RECORD_TOGGLE, token = SCREEN_RECORD, glyph = Glyph.ScreenRecord),
            Spec(name = Strings.CATALOG_SCREEN_RECORD_START, token = SCREEN_RECORD_START, glyph = Glyph.ScreenRecord),
            Spec(name = Strings.CATALOG_SCREEN_RECORD_STOP, token = SCREEN_RECORD_STOP, glyph = Glyph.ScreenRecord),
        )

    /** 屏幕翻译（侧边栏那一项）：起 ColorOS 翻译的前台服务，屏上出翻译胶囊。 */
    private val SCREEN_TRANSLATE = Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_TRANSLATE

    /** 屏幕翻译 —— **关闭**：停掉上面那条前台服务（同一个落点，执行侧走 `stopService`）。 */
    private val SCREEN_TRANSLATE_STOP = Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_TRANSLATE_STOP

    /**
     * 「屏幕翻译」那一格的标记（`ai:screen_translate`）—— 与 [SCREEN_RECORD_TILE] 同类：
     * **永不写进配置**，只是「点它挑打开还是关闭」的关卡标记。
     *
     *  它走 [STATE_ONLY_TILES] 那一档（用户口径 2026-11-11「翻译改成单动作多状态」）：
     *  格子名就是动作名，点它**直接弹一行一档的单选弹窗**（打开 / 关闭），文案念
     *  `屏幕翻译 - 打开`。
     */
    const val SCREEN_TRANSLATE_TILE = "ai:screen_translate"

    /**
     * 「屏幕翻译」那两档：打开 / 关闭，顺序即行序。
     *
     *  这两档是**同一个动作的两个状态**（对着同一个落点，一个起服务、一个停服务），所以文案按
     *  `名字 - 状态` 念：`屏幕翻译 - 打开`（见 `CommandTitle` 与 [STATE_ONLY_TILES]）——
     *  与录屏那三档（`录屏 - 切换`）同一套读法，不是父子动作的冒号。
     *
     *  行名只写动词（`打开` / `关闭`）：弹窗标题写着「屏幕翻译」，行里再写一遍就是同一句话说两次
     *  （与录屏那三档同一条规矩）。
     */
    val SCREEN_TRANSLATE_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_MODE_TURN_ON, token = SCREEN_TRANSLATE, glyph = Glyph.ScreenTranslate),
            Spec(
                name = Strings.CATALOG_MODE_TURN_OFF,
                token = SCREEN_TRANSLATE_STOP,
                glyph = Glyph.ScreenTranslateStop,
            ),
        )

    /** 录音 —— **切换**（老配置里绑的就是这一串，取值不能改）。 */
    private val RECORDING = Action.OPLUS_PREFIX + RomProfile.NAME_RECORDING

    /** 录音 —— **只开始** / **只结束**（三态里的另外两档，见 [RECORDING_MODES]）。 */
    private val RECORDING_START = Action.OPLUS_PREFIX + RomProfile.NAME_RECORDING_START
    private val RECORDING_STOP = Action.OPLUS_PREFIX + RomProfile.NAME_RECORDING_STOP

    /**
     * 「录音」那一格的标记（`ai:recording`）—— 与 [SCREEN_RECORD_TILE] 同类：**永不写进配置**。
     *
     *  开始 / 结束 / 切换是三档真取值，格子代表不了其中任何一档，所以它只当入口：点它直接弹那个
     *  三行单选弹窗（见 [stateChoices]），没有中间那层菜单。
     */
    const val RECORD_TILE = "ai:recording"

    /**
     * 「录音」那三档：**切换 / 开始 / 结束**，顺序即弹窗行序（与 [SCREEN_RECORD_MODES] 同一套）。
     *
     *  切换排最上面：单绑一档手势时它最顺手；「只开始」与「只结束」是给「明明在录、我不想它停」
     *  和「只想停」这两种情形留的 —— 那条意图本身是开关，没有这两档就表达不出只有一边。
     */
    val RECORDING_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_RECORDING_TOGGLE, token = RECORDING, glyph = Glyph.Record),
            Spec(name = Strings.CATALOG_RECORDING_START, token = RECORDING_START, glyph = Glyph.Record),
            Spec(name = Strings.CATALOG_RECORDING_STOP, token = RECORDING_STOP, glyph = Glyph.Record),
        )

    //  「计时」那一族（秒表 / 倒计时）不在这张表里：它自带一份清单与取值形态，单独放在
    //  `ClockCatalog` —— 理由（体量 + 归属）写在那个文件的类注释里。

    /**
     * 相机 / 计算器 —— 走的是**锁屏快捷功能**那两条入口（左下 / 右下角那两颗）。
     *
     *  与别的 `oplus:` 档不同，这两条不是侧键场景表里的能力，而是照抄 SystemUI 那两颗快捷功能的
     *  启动配方（动作 + extra + flag），于是锁屏下按侧键也能直接显示出来 —— 普通「打开应用」
     *  在锁屏上会被键盘锁盖住（计算器），或者丢掉「从锁屏进来」的那一支界面（相机）。
     *  取证与配方见 `docs/device/lsposed.md` 四十五。
     *
     *  界面里这两档收在 [KEYGUARD_TILE] 后面（点格子弹菜单）：它们都是「锁屏上直接打开某样东西」，
     *  单独摆两格只会把「快捷功能」这一段撑成几条同类项。
     */
    private val CAMERA = Action.OPLUS_PREFIX + RomProfile.NAME_CAMERA
    private val CALCULATOR = Action.OPLUS_PREFIX + RomProfile.NAME_CALCULATOR

    /**
     * 「锁屏快捷」那一格的标记（`lock:`）—— 与 [NAV_TILE] / [KEY_TILE] 同类：
     * **永不写进配置**，点它弹 [KEYGUARD_MODES] 里那两档。
     */
    const val KEYGUARD_TILE = "lock:"

    /** 「锁屏快捷」的二级菜单：相机 / 计算器，顺序即行序。两行都是真取值。 */
    private val KEYGUARD_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_CAMERA, token = CAMERA, glyph = Glyph.Camera),
            Spec(name = Strings.CATALOG_CALCULATOR, token = CALCULATOR, glyph = Glyph.Calculator),
        )

    /**
     * 灵感语音 —— **开始**采集。
     *
     *  与 [VOICE_MEMORY_STOP] 是平级的两个取值：ROM 那侧只有「开始 / 结束」两个事件，
     *  没有「再按一次就停」（见 `docs/device/actions/voice.md` 四十六）。两档都收在 [VOICE_MODES]
     *  那一层里 —— 界面只露一行「灵感语音」，点开再选开始 / 结束，松手不替用户收尾。
     */
    private val VOICE_MEMORY = Action.OPLUS_PREFIX + RomProfile.NAME_VOICE_MEMORY

    /** 灵感语音 —— **结束**采集：同一条服务，`longPressEventType=2`。 */
    private val VOICE_MEMORY_STOP = Action.OPLUS_PREFIX + RomProfile.NAME_VOICE_MEMORY_STOP

    /**
     * 「灵感语音」那一格的标记 —— 与 [NAV_TILE] 同类：**永不写进配置**。
     *
     *  开始 / 结束是两档真取值，格子代表不了其中任何一个，所以它只当入口：
     *  菜单里露一行，点开再弹一层选择器（[VOICE_MODES]）。
     */
    const val VOICE_TILE = "ai:voice_memory"

    /**
     * 「灵感语音」那一层：开始 / 结束两档，顺序即行序。
     *
     *  两行的名字**只写「开始」「结束」**：这里存的是三段式里的**状态**那一截，整句由
     *  [commandTitleOf] 拼成 `小布 AI: 灵感语音 - 开始`（父格 → 中间层 → 状态）。
     *
     *  **不要为了弹窗好看把这两条改成「开始灵感语音」** —— 同一个 `name` 也喂给
     *  [commandTitleOf]，那样整句会变成「灵感语音 - 开始灵感语音」。弹窗里那两行的完整短语
     *  由界面侧拼（`ModeMenuPopups` 的 `stateRowLabel`，与系统开关、录屏三态同一条规矩：
     *  菜单给动词，父名由那一层补）。
     */
    val VOICE_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.COMMON_START, token = VOICE_MEMORY, glyph = Glyph.VoiceMemory),
            Spec(name = Strings.COMMON_STOP, token = VOICE_MEMORY_STOP, glyph = Glyph.VoiceMemoryStop),
        )

    /**
     * 唤醒小布（语音助手）。取值与「侧键场景表」那几档同一个写法，但**落点不在宿主**：
     * 它由注入侧在 system_server 里照 ROM 长按电源键那一条配方起（见 [RomProfile.NAME_VOICE_ASSIST]）。
     */
    private val VOICE_ASSIST = Action.OPLUS_PREFIX + RomProfile.NAME_VOICE_ASSIST

    /**
     * 小布 AI 那一层菜单 —— 四件能力。灵感语音只占**一行**（[VOICE_TILE] 标记），
     * 点开它再弹一层选择器选「开始 / 结束」。
     *
     *  为什么不摊成两行：用户口径是「菜单里只留灵感语音，点了再选」；两档都是同一个能力的
     *  两种操作，摊开会让小布 AI 那一格变成五项并列（见 `docs/device/actions/voice.md` 四十六）。
     */
    private val AI_MODES: List<Spec> =
        listOf(
            // 取值照搬 KSU 模块，但模块侧不走 shell —— 见 `host/action/AppLauncher.startSidebar`
            Spec(name = Strings.TOOL_SCREEN_RECOGNITION, token = SIDEBAR, glyph = Glyph.ScreenRecognition),
            // 小布助手（Breeno）：起法不在宿主，见 `RomProfile.NAME_VOICE_ASSIST` 与 `bridge/StatusBarBridge`
            Spec(name = Strings.CATALOG_VOICE_ASSIST, token = VOICE_ASSIST, glyph = Glyph.VoiceAssist),
            Spec(name = Strings.CATALOG_FLASH_NOTE, token = FLASH_MEMORY, glyph = Glyph.FlashNote),
            // 入口行：**永不写进配置**，点它弹 [VOICE_MODES]（见 `ModeMenuPopups` 的 `NestedPickerPopup`）
            Spec(
                name = Strings.CATALOG_VOICE_MEMORY,
                token = VOICE_TILE,
                glyph = Glyph.VoiceMemory,
            ),
        )

    /**
     * 「媒体控制」那一格**本身**（[GROUPS] 里那一行）—— 与菜单首行（[MEDIA_MODES] 的第一条）
     * 共用同一串 token（[MEDIA_TILE]），但两者**字形不同**：这一格是「一类东西」（音符，入口），
     * 菜单首行是「一个具体取值」（开关键）。认领只能靠 `spec ===`，见 `ui/model/icon/ActionIcons.kt`
     * 的 [specIcon] —— 别顺手并成同一个。
     *
     * 声明在 [GROUPS] **之前**：`GROUPS` 的初始化会读它；放到后面会在 object 初始化期读到
     * 还没赋值的引用。
     */
    val MEDIA_TILE_SPEC: Spec =
        Spec(name = Strings.CATEGORY_MEDIA, token = MEDIA_TILE, glyph = Glyph.MediaTile)

    /**
     * 「系统开关」那一格 —— 与 [MEDIA_TILE_SPEC] 同类：**入口格**，点它弹 [SWITCH_MODES]。
     *
     *  格子本身不代表任何取值（[SWITCH_TILE] 是标记 token，永不写进配置）。
     *  声明在 [GROUPS] 之前：那张表要读它。
     */
    val SWITCH_TILE_SPEC: Spec =
        Spec(name = Strings.TOOL_SYSTEM_SWITCHES, token = SWITCH_TILE, glyph = Glyph.SystemSwitch)

    /**
     * 「屏幕亮度」那一格的标记 —— 与 [NAV_TILE] / [KEY_TILE] 同类：**永不写进配置**。
     *
     *  三档（增加 / 减少 / 设置为）各不相同，格子代表不了其中任何一个，所以点它弹 [BRIGHT_MODES]。
     *  取值就是 `bright:` 前缀本身：`Action.parse("bright:")` 认不出百分比、按 `none` 处理，
     *  所以它不可能被当成一条真动作执行。
     */
    const val BRIGHT_TILE = Action.BRIGHT_PREFIX

    /**
     * 亮度菜单三行的**占位写法**（`bright:+` / `bright:-` / `bright:=`）—— 这三行不落盘。
     *
     *  点开一行之后还要在弹窗里挑一个百分比（用户口径「slider，step 5%」），落盘的才是
     *  `bright:+15` 那种带数值的写法（见 `Action.Brightness`）。三行不能直接写成
     *  「+10 / -10 / 50」：那会让人以为数值是写死的。
     */
    private const val BRIGHT_ADD_ROW = Action.BRIGHT_PREFIX + "+"
    private const val BRIGHT_SUB_ROW = Action.BRIGHT_PREFIX + "-"
    private const val BRIGHT_SET_ROW = Action.BRIGHT_PREFIX + "="

    /**
     * 「屏幕亮度」菜单的三行，顺序即行序。三行都是**入口**：点下去还要再问一个百分比。
     *
     *  与别的入口格菜单不同，这一层点完不是结论 —— 界面侧按 [brightnessModeOf] 认出这三行、
     *  弹百分比弹窗，确认之后才把 `Action.Brightness` 交给调用方落盘（`ui/ModeMenuPopups`）。
     */
    val BRIGHT_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_BRIGHTNESS_ADD, token = BRIGHT_ADD_ROW, glyph = Glyph.BrightnessUp),
            Spec(name = Strings.CATALOG_BRIGHTNESS_SUB, token = BRIGHT_SUB_ROW, glyph = Glyph.BrightnessDown),
            // 「设置为」另有字形（太阳带齿轮角标）：与格子共用的话，菜单里两行看着像同一件事
            Spec(name = Strings.CATALOG_BRIGHTNESS_SET, token = BRIGHT_SET_ROW, glyph = Glyph.BrightnessSet),
        )

    /**
     * 「音量」那一格的标记 —— 与 [BRIGHT_TILE] 同类：三档各不相同，格子代表不了任何一个，
     * 点它弹 [VOLUME_MODES]。取值就是 `volume:` 前缀本身（`Action.parse("volume:")` 认不出、
     * 按 `none` 处理，永远不会被当成一条真动作执行）。
     */
    const val VOLUME_TILE = Action.VOLUME_PREFIX

    /** 音量菜单三行的占位写法（`volume:+` / `volume:-` / `volume:=`）—— 这三行不落盘。 */
    private const val VOLUME_ADD_ROW = Action.VOLUME_PREFIX + "+"
    private const val VOLUME_SUB_ROW = Action.VOLUME_PREFIX + "-"
    private const val VOLUME_SET_ROW = Action.VOLUME_PREFIX + "="

    /**
     * 「音量」菜单的三行，顺序即行序 —— 与 [BRIGHT_MODES] 一模一样：三行都是入口，
     * 点下去还要在弹窗里挑一个百分比，确认之后才落盘（`volume:+10`）。
     */
    val VOLUME_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_VOLUME_ADD, token = VOLUME_ADD_ROW, glyph = Glyph.VolumeUp),
            Spec(name = Strings.CATALOG_VOLUME_SUB, token = VOLUME_SUB_ROW, glyph = Glyph.VolumeDown),
            // 「设置为」另有字形（滑轨游标）：与格子那颗共用的话，菜单里两行看着像同一件事
            Spec(name = Strings.CATALOG_VOLUME_SET, token = VOLUME_SET_ROW, glyph = Glyph.VolumeSet),
        )

    /**
     * 「铃声音量」那一格的标记 —— 与 [VOLUME_TILE] 并列的**第二条独立命令**：它写 `STREAM_RING`，
     * 媒体音量写 `STREAM_MUSIC`，两者互不影响。
     *
     *  为什么不并进同一个菜单：来电话响不响，跟播放器的音量是两件事；ColorOS 自己的小布指令
     *  也把这两档拆成两个任务（`MediaVolumeTask` / `AudioRingProxy`）。
     */
    const val RINGER_VOLUME_TILE = Action.RINGER_VOLUME_PREFIX

    /** 铃声音量菜单三行的占位写法（`ringervolume:+` / `-` / `=`）—— 这三行不落盘。 */
    private const val RINGER_VOLUME_ADD_ROW = Action.RINGER_VOLUME_PREFIX + "+"
    private const val RINGER_VOLUME_SUB_ROW = Action.RINGER_VOLUME_PREFIX + "-"
    private const val RINGER_VOLUME_SET_ROW = Action.RINGER_VOLUME_PREFIX + "="

    /**
     * 「铃声音量」菜单的三行 —— 行名与「音量」那三行**共用同一批文案**（「增加 / 减少 / 设置为」
     * 说的是动作，不是对象），靠组标题区分对象；字形不共用：这三行是**铃铛**底图 + 角标
     * （见 [Glyph.RingerVolumeUp]），媒体音量那三行是喇叭。
     */
    val RINGER_VOLUME_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_VOLUME_ADD, token = RINGER_VOLUME_ADD_ROW, glyph = Glyph.RingerVolumeUp),
            Spec(name = Strings.CATALOG_VOLUME_SUB, token = RINGER_VOLUME_SUB_ROW, glyph = Glyph.RingerVolumeDown),
            Spec(name = Strings.CATALOG_VOLUME_SET, token = RINGER_VOLUME_SET_ROW, glyph = Glyph.RingerVolumeSet),
        )

    /**
     * 「自定义变量」的入口露不露出来 —— 现行 `false`（这个功能还没发布，入口先收起来）。
     *
     *  **一处开关管三道入口**：按键行为网格里那一格（[VARS_TILE]）、「添加命令」菜单里那一样
     * （都来自 [GROUPS]）、首页「其他」卡那一条（`HomeCards` 读同一个常量）。
     *  变量本身（存储、`setvar:`、`${名字}`）一律不动 —— 已经用上变量的命令照常跑。
     *
     *  改回 `true` 三处一起回来；那时 `BindingGrids` / `CommandMenus` 里对 [VARS_TILE] 的分支
     *  也还是齐的，不必重写。
     */
    const val SHOW_VARIABLES = false

    /**
     * 「应用控制」那一格 —— 打开应用与结束应用**合成一格**，与 [APP_TILE] 同一个性质：
     * **永不写进配置**，只是关卡标记。
     *
     *  点它先弹一层菜单（[APP_CONTROL_MODES]：打开应用 / 结束应用），选完进**同一张**挑应用页：
     *  打开那一档落成 `app:<包名>`，结束那一档落成 `proc:stop:<包名>`。两档的页面是同一份，
     *  差别只在写出去的 token（见 `AppPickerState.stopApp`）。
     *
     *  它不是一点就落盘的动作：打开 / 结束哪个应用都要**当场挑一个**，格子本身代表不了任何一个包名。
     *  两张候选表（[STEP_GROUPS] / [BRANCH_GROUPS]）都原样保留它；点开那一层菜单后，打开 / 结束
     *  两行各自可以成为一步（分别落成 `app:` 与 `proc:stop:`）。
     */
    const val APP_CONTROL_TILE = "appctl:"

    /** 「应用控制」那一层菜单的两行 —— **都不是取值**，只是「这一趟去挑什么」。 */
    const val APP_CONTROL_OPEN = "appctl:open"
    const val APP_CONTROL_STOP = "appctl:stop"

    /** 「应用控制」点开的那一层：打开应用 / 结束应用。 */
    val APP_CONTROL_MODES: List<Spec> =
        listOf(
            // 打开：与「添加命令」里那一行同名同义，落成 `app:<包名>`
            Spec(name = Strings.TOOL_OPEN_APP, token = APP_CONTROL_OPEN, glyph = Glyph.AppOpen),
            Spec(name = Strings.TOOL_STOP_APP, token = APP_CONTROL_STOP, glyph = Glyph.StopApp),
        )

    /**
     * 内置命令分成两类 —— 界面上各一个小标题，组内顺序即显示顺序。
     *
     * [ALL] 是把两组按顺序拍平的**扁平表**：焦点索引、图标预热等调用方只看它，
     * 分组只是展示层切分，不影响取值与判定（选中的比法、写进配置的 token 都不变）。
     */
    val GROUPS: List<Pair<String, List<Spec>>> =
        listOf(
            // 无操作在最前：它是「解除绑定」的语义，得一眼能找到。
            // 组名不叫「系统按键」：这一组里通知与控制中心 / 媒体控制 / 响铃状态 / 电源菜单都不是「按键」。
            Strings.CATALOG_SYSTEM_ACTIONS to
                listOf(
                    Spec(name = Strings.ACTION_NONE, token = NONE_TOKEN, glyph = Glyph.NoOp),
                    // 「主屏幕 / 返回 / 最近任务」三档收进这一格：点它弹 [NAV_MODES] 菜单。
                    // 与 [MEDIA_TILE] / [RINGER_TILE] 不同，这三档**各不相同**，格子本身代表不了任何一个取值
                    // ⇒ 与 [APP_TILE] 同类，是个**标记**：永不写进配置，也不能作为多条命令的一步。
                    Spec(name = Strings.CATEGORY_SYSTEM_NAV, token = NAV_TILE, glyph = Glyph.NavigationCog),
                    // 「通知与控制中心」那一格：点它弹 [PANEL_MODES] 菜单（原来是「通知栏」一条，
                    // 「通知栏」一档拆成两条）。两条各是一个真取值，格子代表不了其中任何一条
                    // ⇒ 与 [NAV_TILE] / [MINI_TILE] 同类，是个**标记**格，永不写进配置。
                    Spec(name = Strings.CATEGORY_PANEL, token = PANEL_TILE, glyph = Glyph.Panels),
                    // 这一格与「响铃状态」同属**入口格**（点它弹 [MEDIA_MODES] 菜单，不直接写配置）；
                    // 菜单首行才是真取值，能作为多条命令的一步。
                    // 「播放 / 暂停」收进这里 —— 同一个取值不给两个入口。
                    MEDIA_TILE_SPEC,
                    Spec(name = Strings.CATEGORY_RINGER, token = RINGER_TILE, glyph = Glyph.RingerTile),
                    // 「音量加 / 音量减 / 电源键」三档收进这一格（点它弹 [KEY_MODES] 菜单），同样是标记。
                    Spec(name = Strings.CATALOG_SYSTEM_KEYS, token = KEY_TILE, glyph = Glyph.Click),
                    // 系统开关入口：点它弹 [SWITCH_MODES] 菜单（WIFI / 蓝牙 / 手电筒 …），
                    // 选完绑成 `setting:<id>` 或那条动作本身的 token（见 [SystemSwitches]）。
                    //  它属于「系统操作」这一组：动的是系统状态，不是某一颗按键。
                    SWITCH_TILE_SPEC,
                    // 屏幕亮度：点它弹 [BRIGHT_MODES]（增加 / 减少 / 设置为），每一档再问一个百分比。
                    //   与「系统开关」同一个理由归在这一组 —— 动的是系统状态，不是某一颗按键。
                    Spec(name = Strings.CATALOG_BRIGHTNESS, token = BRIGHT_TILE, glyph = Glyph.Brightness),
                    // 音量：与屏幕亮度同一个形状 —— 点它弹 [VOLUME_MODES]（增加 / 减少 / 设置为），
                    //   每一档再问一个百分比，落盘成 `volume:+10` 这种带数值的写法。
                    Spec(name = Strings.CATALOG_VOLUME, token = VOLUME_TILE, glyph = Glyph.Volume),
                    // 铃声音量：与音量并列的第二条命令（写 STREAM_RING）
                    Spec(
                        name = Strings.CATALOG_RINGER_VOLUME,
                        token = RINGER_VOLUME_TILE,
                        glyph = Glyph.RingerVolume,
                    ),
                    // KSU 没有这项；执行侧在 system_server 里反射调 PWM，不走按键长按
                    Spec(name = Strings.PANEL_POWER_MENU, token = POWER_MENU, glyph = Glyph.PowerMenu),
                ),
            // 后三项是系统侧键场景表里的档位：起的是各功能自己的 KeyMagic / 闪记服务
            Strings.CATALOG_QUICK_TOOLS to
                listOfNotNull(
                    // 取值是 KSU 的 `service call`，模块侧换成注入 `KEYCODE_SYSRQ`
                    Spec(name = Strings.CATALOG_SCREENSHOT, token = SCREENSHOT, glyph = Glyph.Screenshot),
                    // 计时器：**格子本身就是那个动作**（点它直接挑时长，落成 `timer:<秒>`）——
                    //  与录屏 / 录音同一个形态，中间不再夹一层只写着同一句的小标题。
                    //  标记 token 永不写进配置。
                    ClockCatalog.TILE_SPEC,
                    // 锁屏快捷：相机 / 计算器收在这一格后面（点它弹 [KEYGUARD_MODES]）。两档各是一个
                    //  真取值，格子代表不了其中任何一个 ⇒ 与 [AI_TILE] 同类，是**标记格**，永不写进配置。
                    Spec(name = Strings.CATALOG_KEYGUARD, token = KEYGUARD_TILE, glyph = Glyph.Keyguard),
                    // 小布 AI 入口格：识屏 / 语音 / 闪记 / 灵感语音四件都收在这一格后面
                    //  （点它弹 [AI_MODES]）。四件各是一个真取值，格子代表不了其中任何一个
                    //  ⇒ 与 [NAV_TILE] / [MINI_TILE] 同类，是**标记格**，永不写进配置。
                    Spec(name = Strings.TOOL_XIAOBU_AI, token = AI_TILE, glyph = Glyph.XiaobuAi),
                    // 录音：起录音机自己的透明界面（落点见 RomProfile）。
                    //  入口行：**永不写进配置**，点它弹 [RECORDING_MODES]（开始 / 结束 / 切换）
                    Spec(name = Strings.CATALOG_RECORDING, token = RECORD_TILE, glyph = Glyph.Record),
                    // 录屏：起 ColorOS 录屏应用自己的 JumpActivity，一起就进录制（落点见 RomProfile）
                    // 入口行：**永不写进配置**，点它弹 [SCREEN_RECORD_MODES]（开始 / 结束 / 切换）
                    Spec(
                        name = Strings.CATALOG_SCREEN_RECORD,
                        token = SCREEN_RECORD_TILE,
                        glyph = Glyph.ScreenRecord,
                    ),
                    // 屏幕翻译：系统侧边栏那一项，起翻译包的前台服务（配方与判据见 RomProfile）。
                    //  「格子即动作」：打开 / 关闭是它的两个状态，点它直接弹单选弹窗
                    //  （见 [STATE_ONLY_TILES]），文案念 `屏幕翻译 - 打开`。
                    Spec(
                        name = Strings.CATALOG_SCREEN_TRANSLATE,
                        token = SCREEN_TRANSLATE_TILE,
                        glyph = Glyph.ScreenTranslate,
                    ),
                    // 与 `window:` 同一族小窗接口，只是目标不同：那条按包名解析入口，这条取当前前台活动。
                    // 两档尺寸（展开 / 收起）各不相同，格子代表不了其中任何一个 ⇒ 按 [MINI_TILE] 当入口格、弹菜单
                    Spec(name = Strings.TOOL_MINI_WINDOW, token = MINI_TILE, glyph = Glyph.MiniWindow),
                    // 应用控制：**一格两个状态** —— 点它先弹一层菜单（打开应用 / 结束应用），
                    //  再进同一张挑应用页。格子代表不了任何一个应用 ⇒ 标记 token 永不写进配置。
                    Spec(name = Strings.TOOL_APP_CONTROL, token = APP_CONTROL_TILE, glyph = Glyph.AppControl),
                    // 同为入口不是取值：点它当场挑一颗变量、再挑对它做什么（见 `ui/screen/variables/`）。
                    //  与 [APP_TILE] 一样按 token 认领 —— `vars:` 不是任何动作的写法。
                    //  **不发布**：这一格随 [SHOW_VARIABLES] 收起来（网格与「添加命令」菜单同源，
                    //  一处关掉两边都没有）。
                    if (SHOW_VARIABLES) {
                        Spec(name = Strings.TOOL_VARIABLES, token = VARS_TILE, glyph = Glyph.Variables)
                    } else {
                        null
                    },
                ),
        )

    /** 界面上按顺序列出的动作 —— [GROUPS] 拍平后的扁平表（以 KSU 的 `ACTION_PRESETS` 去掉 `custom` 为底）。 */
    val ALL: List<Spec> = GROUPS.flatMap { it.second }

    /**
     * 「响铃状态」那一格的取值（= 三档循环，照搬 KSU 的 `keyevent:164`）。
     *
     * 与 [APP_TILE] 不同，它是真取值（`Action.parse` 后是 `KeyEvent(164)`，执行侧转给
     * `bridge/RingerController.cycle`），既能写进配置、也能作为多条命令的一步。
     * 它同时是入口格标记：网格里点它不写这个取值，而是弹 [RINGER_MODES] 菜单。
     */
    const val RINGER_TILE = Action.KEYEVENT_PREFIX + KeyId.KEYCODE_VOLUME_MUTE

    /** 取值就是 [RINGER_TILE] —— 「点一下格子」与「点开菜单再选循环」写进的是同一个值。 */
    val RINGER_CYCLE: Spec =
        Spec(name = Strings.CATALOG_MODE_CYCLE, token = RINGER_TILE, glyph = Glyph.Repeat)

    /**
     * 响铃状态菜单的四行，顺序即行序。第一项是 [RINGER_CYCLE]（KSU 那套三档循环），
     * 后三项是模块扩展写法 `ringer:normal|vibrate|silent`（见 `core/model/Action.Ringer`）。
     *
     * 判定哪一行当前绑定要用 [isBound] 逐行比，不能用 [isSelected] —— 后者把四行都算作
     * 「响铃状态那一格选中」，打勾会让四行同时亮起来。
     */
    val RINGER_MODES: List<Spec> =
        listOf(
            RINGER_CYCLE,
            // 加深带：上一条是 KSU 那套「按一下循环三档」，下面三条是直接指定某一档 —— 两件事，分两段
            Spec(
                name = Strings.CATALOG_MODE_RING,
                token = Action.RINGER_PREFIX + Action.Ringer.NORMAL,
                dividerBefore = true,
                glyph = Glyph.RingerNormal,
            ),
            Spec(
                name = Strings.CATALOG_MODE_VIBRATE,
                token = Action.RINGER_PREFIX + Action.Ringer.VIBRATE,
                glyph = Glyph.RingerVibrate,
            ),
            Spec(
                name = Strings.CATALOG_MODE_SILENT,
                token = Action.RINGER_PREFIX + Action.Ringer.SILENT,
                glyph = Glyph.RingerSilent,
            ),
        )

    /**
     * 「媒体控制」那一格的取值 —— 就是「播放 / 暂停」（`keyevent:85`）。
     *
     * 与 [RINGER_TILE] 同属**入口格**：网格里点它不直接写这个取值，而是弹 [MEDIA_MODES] 菜单。
     * 它同时是真取值（`Action.parse` 后是 `KeyEvent(85)`），能写进配置、也能作为多条命令的一步。
     */
    const val MEDIA_TILE = Action.KEYEVENT_PREFIX + KeyId.KEYCODE_MEDIA_PLAY_PAUSE

    /**
     * 媒体控制菜单的五条，顺序即行序。五条都是普通 `keyevent:` —— 首行就是格子自己那个取值。
     *
     * 播放与暂停拆成两条（`KEYCODE_MEDIA_PLAY` / `KEYCODE_MEDIA_PAUSE`）而不是复用首行那个
     * 播放暂停键：首行那个码交给系统按**当前状态**翻面（在放就停、停就放），这两条是「无论如何
     * 都要播 / 要停」—— 三者在没在播放两种情形下行为不同，不能互相代替。
     */
    val MEDIA_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.MEDIA_PLAY_PAUSE, token = MEDIA_TILE, glyph = Glyph.MediaSwitch),
            Spec(
                name = Strings.CATALOG_MODE_PLAY,
                token = Action.keyToken(KeyId.KEYCODE_MEDIA_PLAY),
                glyph = Glyph.MediaPlay,
            ),
            Spec(
                name = Strings.CATALOG_MODE_PAUSE,
                token = Action.keyToken(KeyId.KEYCODE_MEDIA_PAUSE),
                glyph = Glyph.MediaPause,
            ),
            // 加深带：上三档是「这一格自己」（按当前状态播 / 停），下两档是切歌 —— 两件事，分两段
            Spec(
                name = Strings.MEDIA_PREV,
                token = Action.keyToken(KeyId.KEYCODE_MEDIA_PREVIOUS),
                dividerBefore = true,
                glyph = Glyph.MediaPrev,
            ),
            Spec(
                name = Strings.MEDIA_NEXT,
                token = Action.keyToken(KeyId.KEYCODE_MEDIA_NEXT),
                glyph = Glyph.MediaNext,
            ),
        )

    /**
     * 「系统导航」那一格的取值 —— 与 [APP_TILE] 一样是**标记**（永不写进配置）。
     *
     * 这一组的三档（主屏幕 / 返回 / 最近任务）各不相同，格子代表不了其中任何一个，
     * 所以不能像 [MEDIA_TILE] / [RINGER_TILE] 那样「点一下格子 = 写那个取值」。
     */
    const val NAV_TILE = "nav:"

    /** 「系统按键」那一格的标记，理由同 [NAV_TILE]。 */
    const val KEY_TILE = "key:"

    /**
     * 「通知与控制中心」那一格的标记，理由同 [NAV_TILE]。
     *
     *  与 [APP_TILE] 一样是**前缀本身**（`panel:`）——菜单里那两条真取值是 `keyevent:83` 与
     *  `panel:control_center`，判定走的是整串相等（见 [hasDeeperLevel] / [isSelected]），不会撞。
     */
    const val PANEL_TILE = Action.PANEL_PREFIX

    /**
     * 「控制中心」那一档的取值（见 `core/model/Action.Panel`）。
     *
     *  另一档「通知中心」沿用历史写法 `keyevent:83`（改名不改取值 —— 改了用户已有的绑定会失效）。
     */
    val PANEL_CONTROL = Action.PANEL_PREFIX + Action.Panel.CONTROL_CENTER

    /**
     * 「小布 AI」那一格的标记 —— 与 [NAV_TILE] / [KEY_TILE] 同类：永不写进配置。
     *
     *  四件能力（识屏 / 语音 / 闪记 / 灵感语音）各是一个真取值，格子代表不了其中任何一个，
     *  所以是**标记格**；点它弹 [AI_MODES]。其中灵感语音那一行自己还带一层（[VOICE_MODES]）。
     */
    const val AI_TILE = "ai:"

    /**
     * 「前台转小窗」那一格的标记 —— 一个**普通入口格**（与系统开关 / 系统导航同一档）。
     *
     *  两档（展开的普通小窗 / 收起的最小化窗）是同一个动作的两种取值，但**文案按父子动作念**
     *  （`前台转小窗: 展开`）：这两档各自单念就说得清，不需要再补一个「格子名 - 状态」的形状
     *  —— 那是录屏 / 录音那种「档位名是动词」的格子才需要的（见 [STATE_ONLY_TILES]）。
     *  用户口径 2026-11-08：「把前台应用转小窗改成父子动作」，即点它弹 [MINI_MODES] 菜单
     *  （一格两行、行尾挂选择器），不再走那个两行单选弹窗。
     *
     *  格子本身仍是**标记**（永不写进配置），菜单里那两档才是真取值。
     */
    const val MINI_TILE = "zoom:"

    /**
     * 「前台转小窗」菜单的两行，顺序即行序。两行都是真取值（见 `core/model/Action.MiniWindow`）：
     *  · **展开** = 居中大面积的普通小窗，服务端 `setUserPositionInfo`（`mZoomLaunchFlags = 2`）；
     *  · **收起** = 右上角一条的最小化窗，服务端 `fullToMini`（`mZoomLaunchFlags = 32`）。
     *
     * 执行路径也不同：展开档优先走 `system_server` 的原生动画，收起档只有 recents 正门这一条。
     */
    val MINI_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_MODE_EXPAND, token = MINI_WINDOW, glyph = Glyph.MiniExpand),
            Spec(name = Strings.CATALOG_MODE_COLLAPSE, token = MINI_COLLAPSE, glyph = Glyph.MiniCollapse),
        )

    /**
     * 「系统开关」那一格的二级菜单 —— 每一条都跟着 [SystemSwitches.ITEMS]（名字与顺序同源）。
     *
     *  与别的入口格同一套：一格代表不了这么多个取值，就不在网格里摊成十几格；
     *  菜单里的行**本身就是真取值**（`setting:<id>` / `flashlight`），点中即写配置。
     */
    val SWITCH_MODES: List<Spec> =
        SystemSwitches.ITEMS.map {
            Spec(name = it.labelKey, token = it.token, glyph = GlyphSource.BySwitchKind(SystemSwitches.kindOf(it.token)))
        }

    /** 「系统导航」菜单的三行，顺序即行序。 */
    val NAV_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_MODE_HOME, token = Action.keyToken(KeyId.KEYCODE_HOME), glyph = Glyph.Home),
            Spec(name = Strings.CATALOG_MODE_BACK, token = Action.keyToken(KeyId.KEYCODE_BACK), glyph = Glyph.Back),
            // 执行时不是注入按键：注入 KEYCODE_APP_SWITCH 在这台 ROM 上进不了 PM 的处理分支，
            // 执行侧换成原生调用（见 `host/action/ActionRunner.runKeyEvent`）。
            Spec(
                name = Strings.NAV_RECENTS,
                token = Action.keyToken(KeyId.KEYCODE_APP_SWITCH),
                glyph = Glyph.Recents,
            ),
        )

    /**
     * 「通知与控制中心」菜单的两行，顺序即行序。两行都是真取值。
     *
     *  · **通知中心** = `keyevent:83`（历史写法，改名不改取值）；执行侧这个码不走注入，
     *    转给 `StatusBarBridge` 让 system_server 去 `togglePanel`（见 `ActionRunner.runKeyEvent`）。
     *  · **控制中心** = `panel:control_center`（新档，见 `core/model/Action.Panel`）；
     *    同一条通道，落点是我们自己在 system_server 里试出来的候选（表里没有这一行）。
     */
    val PANEL_MODES: List<Spec> =
        listOf(
            Spec(
                name = Strings.CATALOG_MODE_NOTIFICATIONS,
                token = Action.keyToken(KeyId.KEYCODE_NOTIFICATION),
                glyph = Glyph.Notification,
            ),
            Spec(
                name = Strings.CATALOG_MODE_CONTROL_CENTER,
                token = PANEL_CONTROL,
                glyph = Glyph.ControlCenter,
            ),
        )

    /**
     * 「系统按键」菜单的三行，顺序即行序。
     *
     *  音量键在这里是**动作**（绑给侧键后注入一次音量键），与「不接管物理音量键」那条纪律
     * 不冲突 —— 后者说的是不去拦截音量键本身（上下一同按要留给系统截屏，见 `core/model/KeyId`）。
     */
    val KEY_MODES: List<Spec> =
        listOf(
            Spec(
                name = Strings.MEDIA_VOL_UP,
                token = Action.keyToken(KeyId.KEYCODE_VOLUME_UP),
                glyph = Glyph.VolumeKeyUp,
            ),
            Spec(
                name = Strings.CATALOG_MODE_VOL_DOWN,
                token = Action.keyToken(KeyId.KEYCODE_VOLUME_DOWN),
                glyph = Glyph.VolumeKeyDown,
            ),
            Spec(
                name = Strings.CATALOG_MODE_POWER,
                token = Action.keyToken(KeyId.KEYCODE_POWER),
                glyph = Glyph.PowerKey,
            ),
        )

    /**
     * 入口格表：`格子 token` → **点开后那一层菜单的行**。
     *
     * 网格点击、菜单构造、格子里那行小字、二级菜单、步骤分组、步骤标题都从这一张表取。
     * 本文件只有两张要手写的表（[GROUPS] 与这一张），别处都是从它俩推出来的。
     *
     * 两种格子（只影响「这一格能不能写进配置」；网格与浮层都把它当入口用）：
     *  · **取值格**（[MEDIA_TILE] / [RINGER_TILE]）：菜单首行就是格子自己那个取值；
     *  · **标记格**（[NAV_TILE] / [KEY_TILE] / [PANEL_TILE] / [MINI_TILE]）：菜单里没有格子那个
     *    token —— 它永不写进配置，菜单里那几档才是真取值。
     */
    private val MODE_MENUS: Map<String, List<Spec>> =
        mapOf(
            MEDIA_TILE to MEDIA_MODES,
            RINGER_TILE to RINGER_MODES,
            NAV_TILE to NAV_MODES,
            KEY_TILE to KEY_MODES,
            PANEL_TILE to PANEL_MODES,
            MINI_TILE to MINI_MODES,
            SWITCH_TILE to SWITCH_MODES,
            BRIGHT_TILE to BRIGHT_MODES,
            VOLUME_TILE to VOLUME_MODES,
            RINGER_VOLUME_TILE to RINGER_VOLUME_MODES,
            AI_TILE to AI_MODES,
            VOICE_TILE to VOICE_MODES,
            SCREEN_RECORD_TILE to SCREEN_RECORD_MODES,
            RECORD_TILE to RECORDING_MODES,
            SCREEN_TRANSLATE_TILE to SCREEN_TRANSLATE_MODES,
            KEYGUARD_TILE to KEYGUARD_MODES,
            APP_CONTROL_TILE to APP_CONTROL_MODES,
            //  「计时器」不在这张表里：它**没有下一层菜单**（点格子就是挑时长，见 `ClockCatalog`）
        )

    /**
     * 分组标题的**译文** —— 网格那张表与「添加命令」浮层共用这一处。
     *
     *  组名是 [GROUPS] / [STEP_GROUPS] 里的中文原文（静态表），译文在界面层过。两处各写一遍
     *  时，新增一个分组只会漏掉其中一边、而且 `else` 分支静默显示中文，谁也不会发现。
     */
    fun groupTitleLabel(groupTitle: String): String = I18n.get(groupTitle)

    /**
     * **内置动作分组**（按展示顺序）。
     *
     *  为什么网格必须用这一份、而不是继续按 [GROUPS] 的组大小切 [forGesture] 的结果：
     *  清单哪天真的按手势少了某一格，而组大小没少 ⇒ 最后一组（「快捷功能」）会多吃进
     *  **第一条自定义命令**（真机反馈过「单击 / 双击的自定义命令跑到快捷功能里了」）。
     *  清单与切分必须同源：谁剔条目，就由谁给出分组。
     *
     *  **没有手势参数**：清单对三档完全相同（没有任何一项再按手势剔），
     *  留一个收下却不读的参数，等于在签名里撒谎。
     */
    fun groupsFor(): List<Pair<String, List<Spec>>> = GROUPS

    //  「拍平成一屏」那两支（`builtInCount` / `forGesture`）**已删**：网格改成
    //  「分组表 + 自定义命令」两段拼（见 `BindingGridState` 的 `groups` / `specs`），
    //  场景那一页还要换另一张分组表 —— 一份现成的「拍平结果」已经没人读得对。
    //  切点由 `groups.sumOf { it.second.size }` 给，与分组同源。

    /**
     * 一条自定义命令的展示条目。
     *
     * 取值 `cmd:<名字>` 与 `core/model/Action.kt` 的 [Action.Custom] 同构，选出 / 回写配置走现成那
     * 条路，执行由 `host/action/CustomCommandRunner.run` 展开；命令内容会被省略号截掉，只有名字认得出来。
     * 带上 [Spec.commandKind]，单条与多条在网格里才分得出；多条还要带 [Command.steps] 的条数，
     * 那一格的图标才是「有几步」。
     */
    fun commandSpec(command: Command): Spec =
        Spec(
            name = command.name,
            token = command.token,
            commandKind = command.kind,
            commandSteps = command.steps.size,
            //  单条命令的两档字形靠它分（su / 非 su），抽屉与网格摆的就是这张候选表
            commandRoot = command.root,
            //  字形由命令自己决定：单条 / 多条、几步、是否提权 —— 三者都是 `commandIcon` 的入参
            glyph = GlyphSource.ByCommand(command.kind, command.steps.size, command.root, command.single),
        )

    /**
     * 「添加命令」浮层用的**分组** —— 与网格**同一张表**（[GROUPS]），只剔掉一样：
     * 「无操作」（它代表「吞掉按键」、不是一步动作）。
     *
     *  抽屉里那一行也叫「应用控制」，点开同样是 打开 / 结束 两档，
     *  结束那一档落成 `proc:stop:包名` —— 不再有「把它换成一条 `app:` 选择应用」的适配。
     *
     * 「自定义变量」那一格**留在表里**：它在抽屉里同样是个关卡 —— 点它弹「变量操作」窗挑一步
     * （见 `ui/screen/variables/VarOpsDialog`），抽屉自己**不**把它落成 token。所以它既不能剔掉，
     * 也不能摆成普通一行（`vars:` 落成一步只会写出一个 `Action.parse` 认不出的废串）——
     * 落地那一支在 `StepPickerSheet` 的 `land()` 里单独接走。
     *
     * 注意：入口格在这里保持收拢：点开二级菜单，二级与网格格子弹的是同一张 [MODE_MENUS]。
     * 把档位平铺成独立行会让同一个清单长出两副面孔，**别这么做**。
     */
    val STEP_GROUPS: List<Pair<String, List<Spec>>> =
        GROUPS.map { (title, group) ->
            // 「无操作」代表吞掉按键、不是一步动作
            // （「灵感语音」不在顶层 —— 它落在小布 AI 那层菜单里，那里两行都是真动作）
            title to group.filterNot { it.token == NONE_TOKEN }
        }

    //  「跟随默认」那一格 —— `BRANCH_GROUPS` 比 `GROUPS` 只多这一个。收成一份常量是为了让
    //  `specOf` 也查得到它：查不到时 `label` 退回原始 token（回执成了「已绑定 [follow]」）。
    private val FOLLOW_SPEC = Spec(name = Strings.COMMAND_BRANCH_ACTION_FOLLOW, token = FOLLOW_TOKEN, glyph = Glyph.Follow)

    /**
     * 「系统场景」那一档的候选表 —— 与 [STEP_GROUPS] 同一张表，只把**「无操作」加回来**。
     *
     *  场景的一条规则必须有个动作，而「无操作」在那里是一档真取值：它说的是「这个场景命中时
     *  什么都不做」，用来盖住排在后面的兜底场景。所以保存校验不再要求「每条场景都要选一个动作」。
     *
     *  **只在场景里加**：多条命令的步骤仍走 [STEP_GROUPS]（剔掉它）—— 步骤不是按键，
     *  「无操作」那一步等于白占一格，还让「有几步」这件事数不准。
     *
     *  第一组最前面还有**「跟随默认」**（[Action.Follow]）：它只属于场景块的那三个槽位，
     *  说的是「这一档不由这一块接管」，是所有新块的默认值。放进 [GROUPS] 的话，按键绑定、
     *  组合键、多条命令的步骤都会多出一档语义上不成立的取值 —— 所以只在这一张表里加。
     */
    val BRANCH_GROUPS: List<Pair<String, List<Spec>>> =
        //  与 [STEP_GROUPS] 同一张表：场景要落成的也是一条**动作**，所以「应用控制」照样留在表里
        GROUPS.mapIndexed { index, (title, group) ->
            if (index == 0) {
                //  排在「无操作」**前面**：它是三档的默认值，比「无操作」更常被找
                title to (listOf(FOLLOW_SPEC) + group)
            } else {
                title to group
            }
        }

    /**
     * 某一项是不是当前选中的动作。
     *
     * 必须比「规范化之后」的写法，不能直接比 [Spec.token]：清单里的 token 是源码写法
     * （照搬 KSU 模块），两边都过一遍 `Action.parse`，写法差异才不影响判定。
     */
    fun isSelected(
        spec: Spec,
        action: Action,
    ): Boolean =
        // 写成表达式而不是「一串 `if (...) return`」：后者五个出口，detekt 的 ReturnCount
        //  按 4 计（这条是入口格的判据，本来也没有「中途返回」要表达）。
        if (spec.token == APP_TILE) {
            // 「打开应用」那一步是入口：只要绑任意 `app:` / `window:` 动作就算选中，否则绑完回来一个亮的都没有
            action.isAppOpen
        } else if (spec.token == VARS_TILE) {
            // 「自定义变量」那一格代表**一整族**取值（`setvar:` 有无数种写法，清单里列不出来）：
            //  绑了任意一步变量操作就算这一格选中 —— 与入口格同一套判据，只是那一族由运行时收集。
            action is Action.VarSet
        } else if (spec.token == APP_CONTROL_TILE) {
            // 「应用控制」代表**两族**：打开（`app:` / `window:`）与结束（`proc:stop:`）。
            //  任意一条都算这一格选中 —— 点开之后那一层菜单里再各自精确打勾。
            action.isAppOpen || action is Action.Process
        } else {
            // 入口格代表**一整组取值**：绑其中任意一档就算选中，否则格里一个亮的都没有
            modeMenu(spec.token)?.any { isBound(it, action) } ?: isBound(spec, action)
        }

    /**
     * 一行一行地比某一项是不是当前绑定 —— 菜单打勾用它，不用 [isSelected]。
     *
     * 两者差别只在入口格那一组：菜单必须精确到行，否则整组同时亮起对勾。
     * 网格格子的判定也走它，规范化那套只有这一份实现（见 [canonicalToken]）。
     */
    fun isBound(
        spec: Spec,
        action: Action,
    ): Boolean {
        //  「还有一层的行」（此刻只有灵感语音：开始 / 结束）—— 绑其中任意一档都算这一行绑上了。
        //   只比整串的话，绑了「结束」时那一行不打勾、父格「小布 AI」也不亮（父格走 [isSelected]，
        //   而它同样逐行问 [isBound]）。
        modeMenu(spec.token)?.let { leaves ->
            //  **排除这一行自己**：取值表里可能含与它同 token 的那一项 —— 「媒体控制」那一格与菜单
            //  首行共用 `media:` 这串 token（见 `MEDIA_TILE_SPEC`），直接展开就是无限递归
            //  （真机：打开设置页当场 StackOverflowError）。
            val nested = leaves.filter { it.token != spec.token }
            //  只有**一条都没剔**（= 它是标记行，表里那几行才是它的子档）才展开。
            //  剔掉了东西 ⇒ 这一行本身就是那张表里的一行（取值格的首行：媒体控制「播放/暂停」、
            //  响铃状态「循环」），剩下的几条是它的**兄弟**、不是它的子档 —— 展开会让「绑了播放」
            //  把「播放/暂停」一起点亮（真机反馈「点播放会同时选中播放/暂停」）。这种行落到下面
            //  按整串比，与其余叶子同一把尺子。
            if (nested.size == leaves.size && nested.isNotEmpty()) return nested.any { isBound(it, action) }
        }
        //  「亮度 / 音量 / 倒计时」这几行先判：它们比的是**占位写法的符号**，不是整串（见 [placeholderRowBound]）。
        placeholderRowBound(spec.token, action)?.let { return it }
        //  「应用控制」那两行不是取值（`appctl:open` / `appctl:stop`），比的是**绑的是哪一族**：
        //  打开那行认 `app:` / `window:` / `appshortcut:`，结束那行认 `proc:stop:`。菜单里的勾靠它。
        //
        //  快捷方式**也算「打开应用」**（[isAppOpen] 按「取得到应用目标」算，`appshortcut:` 有目标）：
        //  它确实是把那个应用叫起来，只是起的是它注册的那一条（扫一扫 / 收付款）。
        //  曾经为了让独立的那一页菜单行打勾，这里把 `Action.AppShortcut` 从打开那一行剔出去过 ——
        //  那一页撤掉之后，剔了就是「绑了快捷方式，菜单里一行都不亮」（真机反馈）。
        if (spec.token == APP_CONTROL_OPEN || spec.token == APP_CONTROL_STOP) {
            return if (spec.token == APP_CONTROL_OPEN) {
                action.isAppOpen
            } else {
                action is Action.Process
            }
        }
        val bound = canonicalToken(spec.token)
        //  「系统开关」那一族：`setting:wifi` 与 `setting:wifi:on` / `:off` 是**同一个开关**的三个状态
        //  —— 菜单那一行（「切换」）与网格那一格都得认得出「这个开关已经绑了」。
        //   只比整串的话，绑了「打开」之后勾子、高亮、格子里那行小字全都不出现，
        //   在真机上就是「三态没有效果」。具体落在哪一档另由 `isBoundExactly` 判。
        //  绑的这一头也认**老写法**（`oplus:no_disturb` / `flashlight`）：老配置不迁移，
        //  执行照旧，但界面不能因此认不出「这一格绑的就是那个开关」。
        //  只在这一头放宽 —— 反过来（spec 是老写法）仍按整串比，否则「快捷功能」里的手电筒格
        //  会和「系统开关」格同时亮。
        return if (!bound.startsWith(Action.SETTING_PREFIX)) {
            bound == action.token
        } else {
            val theirs =
                if (action.token.startsWith(Action.SETTING_PREFIX)) {
                    switchIdOf(action.token)
                } else {
                    SystemSwitches.idOfLegacy(action.token)
                }
            switchIdOf(bound) == theirs
        }
    }

    /**
     * 「屏幕亮度」「音量」「铃声音量」「倒计时」这几行的判定 —— 行是**占位写法**（`bright:+` / `-` / `=`，
     * 不带数值），真正落盘的是 `bright:+15` / `timer:300` 这种带数值的串。按整串比的话，绑过之后
     * 菜单一行都不打勾、格子里也没有小字，所以这两族**只比符号、不比数值**。
     *
     *  必须走在 [canonicalToken] 之前：占位写法 `Action.parse` 认不出，它被解析一次就会刷一行
     *  「百分比 / 秒数认不出」的日志，而 `isBound` 是每格每次重组都会走的地方。
     *
     * @return `null` = 这一行不归这几族管，交给调用方接着判
     */
    private fun placeholderRowBound(
        token: String,
        action: Action,
    ): Boolean? =
        when {
            token.startsWith(Action.BRIGHT_PREFIX) -> {
                (action as? Action.Brightness)?.let { token == rowOf(it.mode) } ?: false
            }

            token.startsWith(Action.VOLUME_PREFIX) -> {
                (action as? Action.Volume)?.let { token == rowOf(it.mode) } ?: false
            }

            //  铃声音量那一族同理（两个前缀互不为前缀，先后无所谓）
            token.startsWith(Action.RINGER_VOLUME_PREFIX) -> {
                (action as? Action.RingerVolume)?.let { token == ringerRowOf(it.mode) } ?: false
            }

            //  计时器那一格：只要绑的是**一条带时长的倒计时**，这一格就算绑上了 —— 具体多少秒
            //  是它的取值，不是它的身份（`clock:` 只是这一格的标记，永不写进配置）。
            token == ClockCatalog.TILE -> {
                action is Action.Timer && action.seconds != null
            }

            else -> {
                null
            }
        }

    /**
     * 这一行是不是「屏幕亮度」那三档之一；是的话给出它对应的档位。
     *
     *  界面层据此决定「点这一行要不要再弹一个百分比」（`ui/ModeMenuPopups`）—— 判据留在模型里，
     *  `ui` 不认 token 字面量。与 [rowOf] 互为逆。
     */
    fun brightnessModeOf(token: String): Action.Brightness.Mode? =
        when (token) {
            BRIGHT_ADD_ROW -> Action.Brightness.Mode.ADD
            BRIGHT_SUB_ROW -> Action.Brightness.Mode.SUB
            BRIGHT_SET_ROW -> Action.Brightness.Mode.FIX
            else -> null
        }

    /** [brightnessModeOf] 的逆：某一档在菜单里占哪一行。 */
    private fun rowOf(mode: Action.Brightness.Mode): String =
        when (mode) {
            Action.Brightness.Mode.ADD -> BRIGHT_ADD_ROW
            Action.Brightness.Mode.SUB -> BRIGHT_SUB_ROW
            Action.Brightness.Mode.FIX -> BRIGHT_SET_ROW
        }

    /** 这一行是不是「音量」那三档之一；是的话给出它对应的档位。与 [brightnessModeOf] 同一形状。 */
    fun volumeModeOf(token: String): Action.Volume.Mode? =
        when (token) {
            VOLUME_ADD_ROW -> Action.Volume.Mode.ADD
            VOLUME_SUB_ROW -> Action.Volume.Mode.SUB
            VOLUME_SET_ROW -> Action.Volume.Mode.FIX
            else -> null
        }

    /** [volumeModeOf] 的逆：某一档在菜单里占哪一行。 */
    private fun rowOf(mode: Action.Volume.Mode): String =
        when (mode) {
            Action.Volume.Mode.ADD -> VOLUME_ADD_ROW
            Action.Volume.Mode.SUB -> VOLUME_SUB_ROW
            Action.Volume.Mode.FIX -> VOLUME_SET_ROW
        }

    /** 这一行是不是「铃声音量」那三档之一；是的话给出它对应的档位。与 [volumeModeOf] 同一形状。 */
    fun ringerVolumeModeOf(token: String): Action.Volume.Mode? =
        when (token) {
            RINGER_VOLUME_ADD_ROW -> Action.Volume.Mode.ADD
            RINGER_VOLUME_SUB_ROW -> Action.Volume.Mode.SUB
            RINGER_VOLUME_SET_ROW -> Action.Volume.Mode.FIX
            else -> null
        }

    /** [ringerVolumeModeOf] 的逆：某一档在菜单里占哪一行。 */
    private fun ringerRowOf(mode: Action.Volume.Mode): String =
        when (mode) {
            Action.Volume.Mode.ADD -> RINGER_VOLUME_ADD_ROW
            Action.Volume.Mode.SUB -> RINGER_VOLUME_SUB_ROW
            Action.Volume.Mode.FIX -> RINGER_VOLUME_SET_ROW
        }

    /**
     * 音量**选定数值之后**的那一条（`volume:+10`）—— 与 [brightnessSpecOf] 同一支：
     * 菜单那三行不带数值，落盘的必须是拼好的那一条。
     */
    fun volumeSpecOf(
        mode: Action.Volume.Mode,
        percent: Int,
    ): Spec {
        val action = Action.Volume(mode, percent)
        return Spec(
            name = actionDetail(action) ?: action.token,
            token = action.token,
            //  与菜单里那三行同一颗：这几条就是那三行选定数值之后的落盘形态
            glyph =
                when (mode) {
                    Action.Volume.Mode.ADD -> Glyph.VolumeUp
                    Action.Volume.Mode.SUB -> Glyph.VolumeDown
                    Action.Volume.Mode.FIX -> Glyph.VolumeSet
                },
        )
    }

    /** 铃声音量**选定数值之后**的那一条（`ringervolume:+10`）—— 与 [volumeSpecOf] 同构。 */
    fun ringerVolumeSpecOf(
        mode: Action.Volume.Mode,
        percent: Int,
    ): Spec {
        val action = Action.RingerVolume(mode, percent)
        return Spec(
            name = actionDetail(action) ?: action.token,
            token = action.token,
            glyph =
                when (mode) {
                    Action.Volume.Mode.ADD -> Glyph.VolumeUp
                    Action.Volume.Mode.SUB -> Glyph.VolumeDown
                    Action.Volume.Mode.FIX -> Glyph.VolumeSet
                },
        )
    }

    /**
     * 亮度**选定数值之后**的那一条（`bright:+15`）—— 弹窗按完「确定」时用它交给落盘那一侧。
     *
     *  菜单里那三行是占位写法（不带数值），所以不能拿行本身的 token 落盘；这一支把
     *  「哪一档 + 状态」拼成真正的取值。名字即 [actionDetail]（「增加 - 15%」）——
     *  `label` 那一刻会再查一次字典、查不到就原样显示，正好是这句拼好的话。
     */
    fun brightnessSpecOf(
        mode: Action.Brightness.Mode,
        percent: Int,
    ): Spec {
        val action = Action.Brightness(mode, percent)
        return Spec(
            name = actionDetail(action) ?: action.token,
            token = action.token,
            //  与菜单里那三行同一颗：这几条就是那三行选定数值之后的落盘形态
            glyph =
                when (mode) {
                    Action.Brightness.Mode.ADD -> Glyph.BrightnessUp
                    Action.Brightness.Mode.SUB -> Glyph.BrightnessDown
                    Action.Brightness.Mode.FIX -> Glyph.BrightnessSet
                },
        )
    }

    /**
     * 精确到**状态**的比法 —— 三档菜单（切换 / 打开 / 关闭）里打勾用它。
     *
     *  与 [isBound] 的差别只有一处：后者把 `setting:wifi:on` 与 `setting:wifi` 当同一个开关
     *  （菜单里那行「WIFI」要认得出已绑），三级那三行则必须分得开。
     */
    fun isBoundExactly(
        spec: Spec,
        action: Action,
    ): Boolean = canonicalToken(spec.token) == action.token

    /** 系统开关去掉「状态」那一维之后剩下的那一半（`setting:wifi:on` → `wifi`）。 */
    private fun switchIdOf(token: String): String =
        token
            .removePrefix(Action.SETTING_PREFIX)
            .removeSuffix(Action.ON_SUFFIX)
            .removeSuffix(Action.OFF_SUFFIX)
            .trim()

    /**
     * 「系统开关」那一族当前落在**哪个状态**上（`切换` / `打开` / `关闭`）；别的动作返回 `null`。
     *
     *  网格那一格的小字要它：只写「WIFI」时看不出绑的是哪一档。**「切换」也要写**——
     *  它和「打开 / 关闭」一样是一个具体取值，不写的话那一格的小字与开关名一模一样，
     *  一眼看不出是「按一下取反」还是「定成开 / 关」。
     */
    fun switchStateLabel(action: Action): String? {
        val setting = action as? Action.Setting
        if (setting != null) {
            return when (setting.on) {
                true -> I18n.get(Strings.CATALOG_MODE_TURN_ON, "打开")
                false -> I18n.get(Strings.CATALOG_MODE_TURN_OFF, "关闭")
                null -> I18n.get(Strings.CATALOG_MODE_TOGGLE, "切换")
            }
        }
        //  **老写法**里只有「切一下」的那两档（`flashlight` / `oplus:no_disturb`）：状态词就是「切换」。
        //  它们不在 `setting:` 那一族里，走不到上面那一支 —— 不补这一支，`系统开关: 手电筒` 就少了
        //  后半句（提示与格子小字只剩「手电筒」，读不出这一次是切了一下）。
        //  老配置不迁移（执行侧照旧认这两个 token），所以念法也不能比新写法少一段。
        return if (SystemSwitches.idOfLegacy(action.token) != null) {
            I18n.get(Strings.CATALOG_MODE_TOGGLE, "切换")
        } else {
            null
        }
    }

    /**
     * 「亮度 / 音量 / 铃声音量」这几族当前落在**哪个数值**上（`(10%)`）；别的动作返回 `null`。
     *
     *  与 [switchStateLabel] 是同一件事的两半：那几族的父格是「屏幕亮度 / 媒体音量 / 铃声音量」、
     *  子动作是「增加 / 减少 / 设置为」，剩下的那个数就是**状态** —— 所以它与开关的
     *  「打开 / 关闭」占同一个位置（`铃声音量: 增加 - (10%)`），不编进子动作里。
     *
     *  **每加一条「带百分比」的命令都要在这里补一支**：漏了不会报错，只是那个数消失 ——
     *  弹窗里那行字只剩「增加」、格子小字也只剩「增加」（铃声音量第一版就是这么漏的）。
     *
     *  **那个数带半角括号**：横杠后面直接跟数字会被读成负号（`设置为 - 90%`）。
     */
    fun percentStateLabel(action: Action): String? {
        val percent =
            when (action) {
                is Action.Brightness -> action.percent
                is Action.Volume -> action.percent
                is Action.RingerVolume -> action.percent
                else -> return null
            }
        return I18n.format(Strings.COMMON_PERCENT_PAREN_FORMAT, percent)
    }

    /**
     * 这个 token 是不是**入口格**；是的话返回它那一层菜单的行，否则 `null`。
     *
     *  三档手势与命令链**拿到的是同一份**：清单里没有任何一行再按手势或按来处剔
     *  （灵感语音那一行是小布 AI 菜单里的入口，点开还有开始 / 结束两层）。
     */
    fun modeMenu(token: String): List<Spec>? = MODE_MENUS[token]

    /**
     * 「系统开关」再下一层：某个开关的**三档动作** —— 切换 / 打开 / 关闭。
     *
     *  `setting:` 那一族各有明确的开 / 关写法（`setting:wifi:on` / `setting:wifi:off`）；
     *  手电筒与免打扰不是 `setting:`，它们的执行只有「切换」一种语义，所以只给一档 ——
     *  不假装能指定开 / 关（写了也没人认）。
     *
     * @param token 开关在 [SWITCH_MODES] 里那一行的 token
     */
    fun switchStates(token: String): List<Spec> = switchStatesCache.getOrPut(token) { buildSwitchStates(token) }

    /**
     * [switchStates] 的实体 —— 每次调用要新造 1~3 个 [Spec]（`Spec` 是 data class，名字还要过一次
     * i18n）。它被 `stateMenu` → 入口格菜单、以及网格里每一格的小字反复问到，键只有
     * 「开关的 token」那么几个 —— 按 token 记一份，返回的 List 只读，调用方不要改。
     */
    private fun buildSwitchStates(token: String): List<Spec> {
        //  **开关 id 只能由 `SystemSwitches.kindOf` 定**（它走 `Action.parse`，认得状态写法）：
        //  直接 `removePrefix` 遇到「状态 token」（`setting:wifi:on`）会把 id 取成 `wifi:on`，
        //  再拼一次就成了 `setting:wifi:on:on` —— 这种行点下去谁也认不出。
        val isSetting = token.startsWith(Action.SETTING_PREFIX)
        val id = if (isSetting) SystemSwitches.kindOf(token) else token
        //  三档共用同一颗**种类**字形（蓝牙就是蓝牙、WIFI 就是 WIFI）：
        //  「打开 / 关闭」不换字形，状态由行首那句「打开蓝牙 / 关闭蓝牙」说。
        val kind = GlyphSource.BySwitchKind(id)
        //  「切换」那一行的 token 也归一：传进来的是状态写法时，它该回到「切换」语义
        val toggle =
            Spec(
                name = Strings.CATALOG_MODE_TOGGLE,
                token = if (isSetting) Action.Setting(id).token else token,
                glyph = kind,
            )
        if (!isSetting) return listOf(toggle)
        return listOf(
            toggle,
            Spec(name = Strings.CATALOG_MODE_TURN_ON, token = Action.Setting(id, on = true).token, glyph = kind),
            Spec(name = Strings.CATALOG_MODE_TURN_OFF, token = Action.Setting(id, on = false).token, glyph = kind),
        )
    }

    /**
     * 入口格菜单里的**这一行**点下去还有没有再一层 —— 有的话直接把那几档给出来。
     *
     *  只有「系统开关」那一族有（切换 / 打开 / 关闭）；其余档位点一下就是最终取值，返回 `null`。
     *  判据写成「[switchStates] 多于一条」而不是「token 是不是 [SWITCH_TILE] 的」：以后哪个开关
     *  只有一种写法（手电筒 / 免打扰）自然就只有一条、不再进下一层，界面侧不用跟着改。
     */
    fun stateMenu(token: String): List<Spec>? = switchStates(token).takeIf { it.size > 1 }

    //  下面三张表是同一个口径：**键只由 token 决定**，取值集合 = 清单 + 用户建过的命令，
    //  随配置大小封顶、不随运行时间增长（所以不需要 LRU）；并发下允许算重复一次，结果相同。
    //
    //  它们换掉的是这几处的一次实算 —— 都是「每次重组 / 每行每帧」的调用点：
    //   · [actionOf]      —— `Action.parse`（切前缀 + `toIntOrNull` + 造对象）；
    //   · [modeHitOf]     —— 扫一遍 [ALL]（四十余项），并对每个入口格再扫一遍它那层菜单；
    //   · [switchStates]  —— 每次新造 1~3 个 `Spec`（data class，名字还要过一次 i18n）。

    /** `token` → 解析出来的动作。动作都是不可变的，可以整只存下来。 */
    private val parsedActions = java.util.concurrent.ConcurrentHashMap<String, Action>()

    fun actionOf(token: String): Action = parsedActions.getOrPut(token) { Action.parse(token) }

    /** `action.token` → 落在入口格哪一格哪一行（只存**命中**的那份）。 */
    private val modeHits = java.util.concurrent.ConcurrentHashMap<String, ModeHit>()

    /** 明确问过、确实不属于任何入口格的 token —— 不记的话 `cmd:` 那类每次都要重扫一遍全表。 */
    private val modeMisses =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<String>()

    private val switchStatesCache = java.util.concurrent.ConcurrentHashMap<String, List<Spec>>()

    /**
     * `token` → 规范化之后的写法（走一遍 `Action.parse` 再取回它的 `token`）。
     *
     *  为什么要缓存：清单里的 token 是**源码写法**（照搬 KSU 模块），与用户配置里存的不一定逐字
     *  相同，所以判定必须比规范化那份。而这件事原来在每个格子的**每一次重组**里都重做一遍
     *  —— 一页十几格、pager 同时在场三页、`isSelected` 对入口格还要按菜单逐行再比一轮，
     *  合起来是每帧上百次字符串解析加等量临时对象。解析结果只由 token 决定，而 token 的取值集合
     *  就是清单加用户自己的 `cmd:<名字>`，是个小集合 —— 按 token 记一份即可。
     *
     *  内存：键是 token 字符串，取值集合由「清单 + 用户建过多少条命令」封顶，不随运行时间增长
     *  （所以这里不需要 LRU）。并发下允许重复解析一次，结果相同。
     */
    private val canonicalTokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun canonicalToken(token: String): String = canonicalTokens.getOrPut(token) { actionOf(token).token }

    /**
     * 当前动作落在入口格的哪一档（行里的标题：循环 / 静音 / 上一首 …）；不在任何一组时返回 `null`。
     *
     *  **只给档位名，不带状态**：要「档位 - 状态」那半句（`增加 - 10%` / `WIFI - 打开`）走
     *  `actionDetail`，两者不是一回事。档位名与格子怎么配对只在这一处决定（与
     *  `ui/model/text/CommandText.kt` 的 `stepTitle` 同源）。
     */
    fun modeName(action: Action): String? = modeHitOf(action)?.mode?.label

    /**
     * 这个动作落在「入口格 → 档位」两层的**哪一格、哪一行** —— `null` = 它不属于任何入口格菜单。
     *
     *  「格子里那行小字」只要档位名就走 [modeName]；会同时用到**父格**与**那一行**的地方
     *  （条件执行页里「响铃状态 - 循环」那种写法、以及那一行自己的字形）用它：
     *
     *   · 文案要父格名 —— 光写「循环」看不出是谁在循环；
     *   · 图标要**那一行的**字形 —— 「循环」写的是 `keyevent:164`，与响铃那一格**同一个 token**，
     *     按 token 取图标只能拿到格子那颗（响铃状态），而档位自己的字形（重复箭头）只登记在
     *     [Spec.glyph] 上。拿不到这一行，就等于把档位的身份丢在了主题层面。
     */
    fun modeHitOf(action: Action): ModeHit? {
        val token = action.token
        modeHits[token]?.let { return it }
        if (token in modeMisses) return null
        val hit = searchModeHit(action)
        if (hit == null) modeMisses.add(token) else modeHits[token] = hit
        return hit
    }

    /** [modeHitOf] 的实算那一半 —— 只在缓存没命中时跑一次。 */
    private fun searchModeHit(action: Action): ModeHit? {
        //  `tile` 是按「token 在 MODE_MENUS 里」挑出来的，那一层菜单必然存在 —— 不写
        //  `?: action.token` 那种回退：回退出来的不是档位名（是整串 token），调用方会当名字用。
        val tile = ALL.firstOrNull { it.token in MODE_MENUS && isSelected(it, action) } ?: return null
        val path = modePathOf(tile, action)
        return ModeHit(tile, path.last(), path)
    }

    /**
     * 这个动作从**格子**一路落到**最里面那一档**经过的每一行 —— `[格, 中间层?, 档]`。
     *
     *  为什么不能只留两头（[ModeHit.tile] / [ModeHit.mode]）：有的格子里还夹着一层
     *  「一个东西 + 一个状态」—— 灵感语音就是（`小布 AI: 灵感语音 - 开始`）。只给两头时，
     *  中间那层「灵感语音」没地方放，句子只剩「小布 AI: 开始」，读不出在说哪个功能。
     *
     *  自指的那一行（媒体控制 / 响铃状态的首行与格子共用同一串 token）**收在最后一段就停**：
     *  再往下就是它自己那张表，会绕回去。
     */
    private fun modePathOf(
        tile: Spec,
        action: Action,
    ): List<Spec> {
        val path = mutableListOf(tile)
        //  逐层往下：每轮先问「落到哪一行」，到站时 `current` 置空、循环自然收尾。
        //  用「下一轮还走不走」表达两个终点（没有下一行 / 下一行就是它自己），不在循环里写早退。
        var current: Spec? = tile
        while (current != null) {
            val from = current
            val row = modeMenu(from.token)?.firstOrNull { isBound(it, action) }
            if (row != null) path += row
            //  同 token ⇒ 它是「取值格的首行」（媒体控制 / 响铃状态那一行）：加进路径（它自己的
            //  字形与格子那颗不同），但不再往下 —— 再往下就是它自己那张表，会绕回去。
            current = row?.takeIf { it.token != from.token }
        }
        return path
    }

    /** 见 [modeHitOf]：父格 + 那一行（+ 中间层，见 [path]）。 */
    data class ModeHit(
        val tile: Spec,
        val mode: Spec,
        /** 从格到最里面那一档的整条链（含两头的 `[格, …, 档]`），见 [modePathOf]。 */
        val path: List<Spec>,
    )

    /**
     * 点这一格 / 这一行**会发生什么**。
     *
     *  这几档不是「显示成什么样」，是**行为**：点下去弹菜单、弹弹窗、换一页、还是当场落盘。
     *  界面上的每一样指示（行尾箭头 / 选择器 / 勾选框、要不要先起一层菜单）都是它的**后果**，
     *  不是并列的判据。
     *
     * ## 为什么要有这个枚举
     *
     *  原先界面各处自己判：`BindingTile` 判「有没有菜单」画选择器、`ModeMenuPopups` 判
     *  `token == APP_CONTROL_OPEN/STOP` 画箭头、`actionRowView` 又判一次「有没有菜单」与
     *  `APP_TILE`。同一格因此能长出两种指示 —— 而且真的长出来了：「应用控制」那一格在网格里
     *  画选择器（它弹原地菜单，对的），在抽屉里画右箭头（说它跳页，错的）。
     *  「前台转小窗」那次改动也是这个形状：菜单形态一变，行尾记号得跟着改三处。
     *
     *  现在界面只 `when (clickKind)`。
     */
    enum class ClickKind {
        /** 点一下就是一个完整动作，当场落盘（截屏 / 手电筒 / 自定义命令 / 菜单里的「主屏幕」）。 */
        DIRECT,

        /** 原地弹一层**菜单**（系统开关 / 系统导航 / 媒体控制 / 小布 AI / 应用控制那一格）。 */
        MENU,

        /** 原地弹一行一档的**单选弹窗** —— 这一格本身就是那个动作（录屏 / 录音）。 */
        STATE,

        /** 原地弹**数值**弹窗 —— 一根滑杆 + 一行实时文案（亮度 / 音量的百分比、倒计时的时长）。 */
        NUMBER,

        /** 原地弹**开关三态**弹窗（系统开关的「切换 / 打开 / 关闭」那几行）。 */
        SWITCH,

        /** 换**另一张页面**：挑应用（「应用控制」菜单里的打开 / 结束两行）。 */
        PAGE_APP,

        /** 换**另一张页面**：变量操作（「自定义变量」那一格）。 */
        PAGE_VARS,
    }

    /**
     * 这一格 / 这一行的点击行为 —— **唯一判据**，见 [ClickKind]。
     *
     *  顺序即优先级，四条「更具体的先说」：
     *   · 换页的两族最具体（`appctl:open` / `appctl:stop` 与 `vars:` 各有自己的页面，
     *     **去向还不一样** —— 合成一个 `PAGE` 的话，界面还得回去判 token 才知道跳哪一页）；
     *   · 再是「这一格就是那个动作」的状态弹窗 —— 它**也有** [modeMenu]（三行档位），
     *     所以要排在 [modeMenu] 之前，否则会被当成「还有一层菜单」；
     *   · 百分比 / 开关三态 / 时长都是**菜单行**的后续，判据在各自那张表上；
     *   · 最后才是「这一格自己有菜单」。判据用 `modeMenu(spec.token) != null` —— 那正是
     *     「这一格」的定义。**不要换成 [hasDeeperLevel]**：它是为「媒体控制 / 响铃状态的首行与
     *     格子共用同一串 token」设的自指闸，拿它判**格子**会把这两格判成「点一下就落盘」
     *     （真机症状：行尾没有选择器、点下去直接绑上首行那个取值）。
     *
     *  [stateMenu] 排在 [modeMenu] 之后是必须的：它只看前缀，连格子那串标记 token（`setting:`）
     *  也会被它当成「某个开关的三档」，于是系统开关那一格会被判成 [ClickKind.SWITCH]。
     */
    fun clickKind(spec: Spec): ClickKind =
        when {
            spec.token == VARS_TILE -> ClickKind.PAGE_VARS
            spec.token == APP_CONTROL_OPEN || spec.token == APP_CONTROL_STOP -> ClickKind.PAGE_APP
            stateChoices(spec.token) != null -> ClickKind.STATE
            isNumberRow(spec.token) -> ClickKind.NUMBER
            modeMenu(spec.token) != null -> ClickKind.MENU
            stateMenu(spec.token) != null -> ClickKind.SWITCH
            else -> ClickKind.DIRECT
        }

    /**
     * 这一格 / 这一行是不是「还要再问一个数」的那几档（亮度 / 媒体音量 / 铃声音量的百分比、
     * 计时器的时长）。
     *
     *  各族判据各自成表（[brightnessModeOf] / [volumeModeOf] / [ringerVolumeModeOf] /
     *  `ClockCatalog.isTimerTile`），这里只是把「有没有落在其中任何一张表上」合成一个布尔 ——
     *  [clickKind] 那一支原先写的是两条 `!= null` 并联，再加一族就顶破行长，收在这里读起来
     *  也更像一句话。
     *
     *  这几族的共同点只有一条：**行 / 格本身写不出结论**，还差一个数（`bright:+`、`clock:`）——
     *  所以界面弹的是同一个「滑杆 + 一句实时文案」的弹窗，只是量程与念法不同。
     */
    private fun isNumberRow(token: String): Boolean =
        brightnessModeOf(token) != null ||
            volumeModeOf(token) != null ||
            ringerVolumeModeOf(token) != null ||
            ClockCatalog.isTimerTile(token)

    /**
     * 这几格**点开直接弹一行一档的单选弹窗**（录屏 / 录音：开始 / 结束 / 切换）。
     *
     *  「一个动作的几种状态」与「一整类动作的清单」不是一回事：灵感语音那层菜单里有「灵感语音」
     *  这个中间层，小布 AI 那一格才有东西可列；而录屏 / 录音的格子名就是动作名，中间再夹一张
     *  只写着「录屏」「录音」的小标题 + 三行的菜单，等于把同一句话说两遍 —— 所以这两档直接弹弹窗
     *  （用户口径「用 dialog 选择器，网格 / 菜单里视为一个动作，点击弹 dialog 选」）。
     *
     *  **仍然留在 [MODE_MENUS] 里**：那一张表同时喂着「这一格算不算选中」（[isSelected]）、
     *  格子里那行小字（`TileSubtitle`）与文案的父名（`modeHitOf`）—— 只是**露出的方式**不同。
     *
     *  @return 弹窗里那几行（顺序即行序）；不是这一类格返回 `null`
     */
    fun stateChoices(token: String): List<Spec>? = MODE_MENUS[token]?.takeIf { token in STATE_ONLY_TILES }

    /**
     * 这几格**本身就是那个动作**，菜单里那几行只是它的状态（录屏 / 录音 / 屏幕翻译）。
     *
     *  三件事都由它决定，所以只能有一份名单：
     *   · 点开弹的是弹窗不是菜单（[stateChoices]，即 [clickKind] 的 `STATE` 档）；
     *   · 文案没有「父动作」可写 —— `录屏 - 切换`，而不是 `录屏: 切换`（`commandTitleOf`）；
     *   · 格子行尾不挂选择器字形（`clickKind` 不是 `MENU` ⇒ 界面画勾选框）。
     *
     *  **屏幕翻译在这份名单里**（用户口径 2026-11-11「翻译改成单动作多状态，打开和关闭状态」）：
     *  打开 / 关闭念作 `屏幕翻译 - 打开` —— 横杠那一套读法，不是父子动作的冒号。
     *
     *  **前台转小窗不在这份名单里**（它曾经在）：那两档（展开 / 收起）单念就说得清，不需要
     *  再补一个格子名，于是它按普通入口格走 —— 点它弹 [MINI_MODES] 菜单、文案是父子动作
     *  `前台转小窗: 展开`（用户口径 2026-11-08「把前台应用转小窗改成父子动作」）。
     *  换句话说：要不要「格子即动作」这一形态，判据是**文案需不需要补格子名**，
     *  不是「这一格有没有好几档」。
     */
    private val STATE_ONLY_TILES: Set<String> = setOf(SCREEN_RECORD_TILE, RECORD_TILE, SCREEN_TRANSLATE_TILE)

    /**
     * 这一行点下去**还有它自己那一层**的下一问 —— 此刻只有灵感语音（开始 / 结束）。
     *
     *  与 [modeMenu] 不是一回事，差别全在**取值格的首行**上：媒体控制那层菜单的第一行
     *  （「播放/暂停」）与格子自己共用同一串 token（[MEDIA_TILE]），`modeMenu(那一行.token)`
     *  同样非空 —— 可那是**它所属的那张表**，不是它的下一层。照 `modeMenu != null` 判，
     *  点「播放/暂停」会在菜单上面再弹一张一模一样的清单（真机口径「点播放/暂停时弹出个多余的
     *  选择器」）。响铃状态的首行（「循环」）同理。
     *
     *  判据：那张表里**没有一行与它同 token** ⇒ 它是标记行（永不写进配置），表里那几行才是它的
     *  子档。取值格那两行的下一层由 [hasDeeperLevel] 判（那里问的是「这一行点开还有没有自己那一层」，
     *  正该是真的）。
     */
    fun hasDeeperLevel(spec: Spec): Boolean = modeMenu(spec.token)?.let { leaves -> leaves.none { it.token == spec.token } } ?: false

    /** 按动作找展示信息；清单外一律返回 null（清单是照搬的封闭集合，不为自定义写法现造条目）。 */
    fun specOf(action: Action): Spec? = (ALL + FOLLOW_SPEC).firstOrNull { isSelected(it, action) }

    /**
     * 按 **token** 找清单条目；不在清单里回 `null`。
     *
     *  唯一用途是「这一格画什么」：字形只由 `Spec.glyph` 声明一次，界面层按 token 查这一处，
     *  不再自己维护第二份「token → 图标」表（见 `ActionIcons.actionIcon`）。
     *
     *  **网格与二级菜单一起算**：网格那 100 多格之外，`keyevent:26` / 响铃三档 / 媒体那几条
     *  只出现在二级菜单里，而它们照样会被写进配置、照样要画在步骤行上。
     */
    fun specOfToken(token: String): Spec? = BY_TOKEN[token]

    /** [specOfToken] 的索引 —— 条目是静态表，第一次问时建一次。 */
    private val BY_TOKEN: Map<String, Spec> by lazy {
        //  同一个 token 可能既是**格子**、又在某张二级菜单里出现（响铃那一档就是这样）：
        //  先到先得，格子那一份赢 —— 它才是「这一格」的身份，菜单里那行是它的一个取值。
        val out = HashMap<String, Spec>()
        for (s in ALL + MODE_MENUS.values.flatten() + FOLLOW_SPEC) out.putIfAbsent(s.token, s)
        out
    }

    /**
     * 这个动作落在入口格的**哪一档**上（`循环` / `静音` / `Wi-Fi 打开` …）；入口格之外返回 `null`。
     *
     *  **网格格子与步骤抽屉那一行的行首字形共用它**：两处摆的是同一张清单，行首就得同一个判据。
     *  原先网格里就地写了一遍 `modes.firstOrNull { isBound(…) }`、抽屉里没写，于是同一个
     *  「响铃状态」在两处长得不一样（真机口径「抽屉和网格的图标不一致」）。
     *
     * @param action 当前绑的动作；`null`（抽屉那边的草稿还没有当前值）⇒ 也当作没命中
     */
    fun boundModeOf(
        spec: Spec,
        action: Action?,
    ): Spec? {
        if (action == null) return null
        val row = modeMenu(spec.token)?.firstOrNull { isBound(it, action) } ?: return null
        //  这一行自己还带一层（灵感语音 → 开始 / 结束）：小字与字形都取**最里面那一档**，
        //  否则格子里只写得出「灵感语音」，看不出这一步是开还是停。
        return modeMenu(row.token)?.firstOrNull { isBound(it, action) } ?: row
    }

    /**
     * 这个动作在**人前**叫什么 —— 优先级只有一处：档位名（入口格那些 `nav:` / `key:` / `panel:` /
     * `zoom:` 的「主屏幕」「音量+」）→ 清单里的标题 → `null`（认不出来，交给调用方的兜底写法）。
     *
     *  兜底**不在这里定**：界面那一侧显示配置里的**原样 token**（手改过的文件要看得见），
     *  执行时提示那一侧显示 `action.token`（规范化后的写法）。两处以前各写一遍 `?:` 链 ——
     *  真机踩过「界面上叫主屏幕、提示里蹦出 `nav:home`」，就是其中一处少判了档位名那一档。
     */
    fun label(action: Action): String? =
        when (action) {
            //  变量操作不是清单里的条目（变量是运行时收集的，`setvar:` 又有无数种写法），
            //  「人前叫什么」只能由动作自己按变量名与运算符拼 —— 与执行时提示同一支（`CommandTitle`）。
            is Action.VarSet -> {
                commandTitleOf(action).forSentence(action, { "" }, action.token)
            }

            else -> {
                modeName(action) ?: specOf(action)?.label
            }
        }
}
