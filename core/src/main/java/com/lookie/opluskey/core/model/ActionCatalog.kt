package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

/** 设置页上可选的「动作清单」（界面用的展示层数据）。 */
object ActionCatalog {
    /**
     * 界面字形名 —— **纯名字的封闭枚举**，一个条目一支字形。
     *
     *  为什么不是直接放图标对象：`core` 是注入侧也会加载的包，一、1 不许它认识界面库。
     *  这里只登记「用哪支字形」，`ui/model/ActionIcons.kt` 把名字翻成图标（唯一一处映射）。
     *  加一档动作时：**要新字形才动那张映射表**，复用已有字形的条目一个字都不用改。
     */
    enum class Glyph {
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

        /** 音量菜单的「增加」那一行（喇叭带向上箭头）。 */
        VolumeUp,

        /** 音量菜单的「减少」那一行（喇叭带向下箭头）。 */
        VolumeDown,

        /** 音量菜单的「设置为」那一行（喇叭带滑轨游标）—— 与格子那颗不是同一颗。 */
        VolumeSet,

        /** 「小布 AI」入口格（`sparkle-highlight`）：四件小布能力收在这一格后面。 */
        XiaobuAi,

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
         * 这一格用哪支字形 —— **只写名字**，不引图标库类型（`core` 在注入侧也会被加载，一、1 那条
         * 「注入侧不许碰界面依赖」对它是硬约束）。名字 → 图标那一步在 `ui/model/ActionIcons.kt`
         * 里做**一次**映射（[Glyph] 是封闭枚举，加一档字形才会碰到那张表）。
         *
         *  `null` = 不特别声明，界面按 [token] 那套照旧推（绝大多数条目都是这样）；
         *  真正需要显式声明的只有「一个 token 对应两支字形」和「标记格」那几处 ——
         *  它们原来是在界面层用 `spec === X` / `token == X` 认领的，那是把**注册信息**漏在了渲染层。
         */
        val glyph: Glyph? = null,
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
    //  取舍与依据写在 `docs/implementation.md` 的「交还系统为什么不提供」。

    /**
     * 「打开应用」这一步的取值 —— 永不写进配置，只是「点它去挑应用」的关卡标记。
     *
     *  它**不再是网格里的一格**：网格那一格是 [APP_CONTROL_TILE]（应用控制，打开 / 结束两个状态）。
     *  这一位现在只服务「添加命令」抽屉里那一行（[APP_STEP]），跳的是同一张挑应用页。
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

    /**
     * 系统侧键场景表里的三档（落点见 `core/model/RomProfile`）：一键闪记 / 免打扰 / 录音。
     * 系统自己的侧键设置里就是这三项，调用目标逐字抄自它的场景表。
     */
    private val FLASH_MEMORY = Action.OPLUS_PREFIX + RomProfile.NAME_FLASH_MEMORY

    /** 录屏 —— 与其它 `oplus:` 档同一条：名字在 [RomProfile] 那一处，这里只拼 token。 */
    private val SCREEN_RECORD = Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_RECORD

    /** 屏幕翻译（侧边栏那一项）：起 ColorOS 翻译的前台服务，屏上出翻译胶囊。 */
    private val SCREEN_TRANSLATE = Action.OPLUS_PREFIX + RomProfile.NAME_SCREEN_TRANSLATE
    private val RECORDING = Action.OPLUS_PREFIX + RomProfile.NAME_RECORDING

    /**
     * 相机 / 计算器 —— 走的是**锁屏快捷功能**那两条入口（左下 / 右下角那两颗）。
     *
     *  与别的 `oplus:` 档不同，这两条不是侧键场景表里的能力，而是照抄 SystemUI 那两颗快捷功能的
     *  启动配方（动作 + extra + flag），于是锁屏下按侧键也能直接显示出来 —— 普通「打开应用」
     *  在锁屏上会被键盘锁盖住（计算器），或者丢掉「从锁屏进来」的那一支界面（相机）。
     *  取证与配方见 `docs/device-findings.md` 四十五。
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
            Spec(name = Strings.CATALOG_CAMERA, token = CAMERA),
            Spec(name = Strings.CATALOG_CALCULATOR, token = CALCULATOR),
        )

    /**
     * 灵感语音 —— **开始**采集。
     *
     *  与 [VOICE_MEMORY_STOP] 是平级的两个取值：ROM 那侧只有「开始 / 结束」两个事件，
     *  没有「再按一次就停」（见 `docs/device-findings.md` 四十六）。两档都收在 [VOICE_MODES]
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

    /** 「灵感语音」那一层：开始 / 结束两档，顺序即行序。 */
    val VOICE_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_VOICE_MEMORY_START, token = VOICE_MEMORY),
            Spec(name = Strings.CATALOG_VOICE_MEMORY_STOP, token = VOICE_MEMORY_STOP),
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
     *  两种操作，摊开会让小布 AI 那一格变成五项并列（见 `docs/device-findings.md` 四十六）。
     */
    private val AI_MODES: List<Spec> =
        listOf(
            // 取值照搬 KSU 模块，但模块侧不走 shell —— 见 `host/action/AppLauncher.startSidebar`
            Spec(name = Strings.TOOL_SCREEN_RECOGNITION, token = SIDEBAR),
            // 小布助手（Breeno）：起法不在宿主，见 `RomProfile.NAME_VOICE_ASSIST` 与 `bridge/StatusBarBridge`
            Spec(name = Strings.CATALOG_VOICE_ASSIST, token = VOICE_ASSIST),
            Spec(name = Strings.CATALOG_FLASH_NOTE, token = FLASH_MEMORY),
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
     * 菜单首行是「一个具体取值」（开关键）。认领只能靠 `spec ===`，见 `ui/model/ActionIcons.kt`
     * 的 [specIcon] —— 别顺手并成同一个。
     *
     * 声明在 [GROUPS] **之前**：`GROUPS` 的初始化会读它，而放在 companion 里会让先初始化
     * companion、那里的 `STEP_GROUPS` 又要回头读还没初始化完的 `GROUPS`。
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
     *  它不是一步动作：打开 / 结束哪个应用都要**当场挑一个**，格子本身代表不了任何一个包名。
     *  所以 [STEP_GROUPS] / [BRANCH_GROUPS] 把它换成 [APP_STEP]（多步里的那一步只能是一个取值）。
     */
    const val APP_CONTROL_TILE = "appctl:"

    /** [APP_CONTROL_TILE] 那一层菜单的两行 —— **都不是取值**，只是「这一趟去挑什么」。 */
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
                    Spec(name = Strings.ACTION_NONE, token = NONE_TOKEN),
                    // 「主屏幕 / 返回 / 最近任务」三档收进这一格：点它弹 [NAV_MODES] 菜单。
                    // 与 [MEDIA_TILE] / [RINGER_TILE] 不同，这三档**各不相同**，格子本身代表不了任何一个取值
                    // ⇒ 与 [APP_TILE] 同类，是个**标记**：永不写进配置，也不能作为多条命令的一步。
                    Spec(name = Strings.CATEGORY_SYSTEM_NAV, token = NAV_TILE, glyph = Glyph.NavigationCog),
                    // 「通知与控制中心」那一格：点它弹 [PANEL_MODES] 菜单（原来是「通知栏」一条，
                    // 「通知栏」一档拆成两条）。两条各是一个真取值，格子代表不了其中任何一条
                    // ⇒ 与 [NAV_TILE] / [MINI_TILE] 同类，是个**标记**格，永不写进配置。
                    Spec(name = Strings.CATEGORY_PANEL, token = PANEL_TILE, glyph = Glyph.Panels),
                    // 这一格与「响铃状态」同属**入口格**（点它弹 [MEDIA_MODES] 菜单，不直接写配置），
                    // 与「打开应用」一起归 [isEntryTile]；但它俩是真取值，能作为多条命令的一步。
                    // 「播放 / 暂停」收进这里 —— 同一个取值不给两个入口。
                    MEDIA_TILE_SPEC,
                    Spec(name = Strings.CATEGORY_RINGER, token = RINGER_TILE, glyph = Glyph.RingerTile),
                    // 「音量 + / 音量 − / 电源键」三档收进这一格（点它弹 [KEY_MODES] 菜单），同样是标记。
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
                    // KSU 没有这项；执行侧在 system_server 里反射调 PWM，不走按键长按
                    Spec(name = Strings.PANEL_POWER_MENU, token = POWER_MENU),
                ),
            // 后三项是系统侧键场景表里的档位：起的是各功能自己的 KeyMagic / 闪记服务
            Strings.CATALOG_QUICK_TOOLS to
                listOfNotNull(
                    // 取值是 KSU 的 `service call`，模块侧换成注入 `KEYCODE_SYSRQ`
                    Spec(name = Strings.CATALOG_SCREENSHOT, token = SCREENSHOT),
                    // 锁屏快捷：相机 / 计算器收在这一格后面（点它弹 [KEYGUARD_MODES]）。两档各是一个
                    //  真取值，格子代表不了其中任何一个 ⇒ 与 [AI_TILE] 同类，是**标记格**，永不写进配置。
                    Spec(name = Strings.CATALOG_KEYGUARD, token = KEYGUARD_TILE, glyph = Glyph.Keyguard),
                    // 小布 AI 入口格：识屏 / 语音 / 闪记 / 灵感语音四件都收在这一格后面
                    //  （点它弹 [AI_MODES]）。四件各是一个真取值，格子代表不了其中任何一个
                    //  ⇒ 与 [NAV_TILE] / [MINI_TILE] 同类，是**标记格**，永不写进配置。
                    Spec(name = Strings.TOOL_XIAOBU_AI, token = AI_TILE, glyph = Glyph.XiaobuAi),
                    Spec(name = Strings.CATALOG_RECORDING, token = RECORDING),
                    // 录屏：起 ColorOS 录屏应用自己的 JumpActivity，一起就进录制（落点见 RomProfile）
                    Spec(name = Strings.CATALOG_SCREEN_RECORD, token = SCREEN_RECORD),
                    // 屏幕翻译：系统侧边栏那一项，起翻译包的前台服务（配方与判据见 RomProfile）
                    Spec(name = Strings.CATALOG_SCREEN_TRANSLATE, token = SCREEN_TRANSLATE),
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
            ),
            Spec(name = Strings.CATALOG_MODE_VIBRATE, token = Action.RINGER_PREFIX + Action.Ringer.VIBRATE),
            Spec(name = Strings.CATALOG_MODE_SILENT, token = Action.RINGER_PREFIX + Action.Ringer.SILENT),
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
            Spec(name = Strings.MEDIA_PLAY_PAUSE, token = MEDIA_TILE),
            Spec(name = Strings.CATALOG_MODE_PLAY, token = Action.keyToken(KeyId.KEYCODE_MEDIA_PLAY)),
            Spec(name = Strings.CATALOG_MODE_PAUSE, token = Action.keyToken(KeyId.KEYCODE_MEDIA_PAUSE)),
            // 加深带：上三档是「这一格自己」（按当前状态播 / 停），下两档是切歌 —— 两件事，分两段
            Spec(
                name = Strings.MEDIA_PREV,
                token = Action.keyToken(KeyId.KEYCODE_MEDIA_PREVIOUS),
                dividerBefore = true,
            ),
            Spec(name = Strings.MEDIA_NEXT, token = Action.keyToken(KeyId.KEYCODE_MEDIA_NEXT)),
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
     *  `panel:control_center`，判定走的是整串相等（见 [isEntryTile] / [isSelected]），不会撞。
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
     * 「前台转小窗」那一格的标记 —— 与 [NAV_TILE] / [KEY_TILE] 同类。
     *
     * 两档尺寸（展开的普通小窗 / 收起的最小化窗）各不相同，格子代表不了其中任何一个，
     * 所以是**标记**（永不写进配置），菜单里那两档才是真取值。
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
            Spec(name = Strings.CATALOG_MODE_EXPAND, token = MINI_WINDOW),
            Spec(name = Strings.CATALOG_MODE_COLLAPSE, token = MINI_COLLAPSE),
        )

    /**
     * 「系统开关」那一格的二级菜单 —— 每一条都跟着 [SystemSwitches.ITEMS]（名字与顺序同源）。
     *
     *  与别的入口格同一套：一格代表不了这么多个取值，就不在网格里摊成十几格；
     *  菜单里的行**本身就是真取值**（`setting:<id>` / `flashlight`），点中即写配置。
     */
    val SWITCH_MODES: List<Spec> =
        SystemSwitches.ITEMS.map { Spec(name = it.labelKey, token = it.token) }

    /** 「系统导航」菜单的三行，顺序即行序。 */
    val NAV_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.CATALOG_MODE_HOME, token = Action.keyToken(KeyId.KEYCODE_HOME)),
            Spec(name = Strings.CATALOG_MODE_BACK, token = Action.keyToken(KeyId.KEYCODE_BACK)),
            // 执行时不是注入按键：注入 KEYCODE_APP_SWITCH 在这台 ROM 上进不了 PM 的处理分支，
            // 执行侧换成原生调用（见 `host/action/ActionRunner.runKeyEvent`）。
            Spec(name = Strings.NAV_RECENTS, token = Action.keyToken(KeyId.KEYCODE_APP_SWITCH)),
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
            Spec(name = Strings.CATALOG_MODE_NOTIFICATIONS, token = Action.keyToken(KeyId.KEYCODE_NOTIFICATION)),
            Spec(name = Strings.CATALOG_MODE_CONTROL_CENTER, token = PANEL_CONTROL),
        )

    /**
     * 「系统按键」菜单的三行，顺序即行序。
     *
     *  音量键在这里是**动作**（绑给侧键后注入一次音量键），与「不接管物理音量键」那条纪律
     * 不冲突 —— 后者说的是不去拦截音量键本身（上下一同按要留给系统截屏，见 `core/model/KeyId`）。
     */
    val KEY_MODES: List<Spec> =
        listOf(
            Spec(name = Strings.MEDIA_VOL_UP, token = Action.keyToken(KeyId.KEYCODE_VOLUME_UP)),
            Spec(name = Strings.CATALOG_MODE_VOL_DOWN, token = Action.keyToken(KeyId.KEYCODE_VOLUME_DOWN)),
            Spec(name = Strings.CATALOG_MODE_POWER, token = Action.keyToken(KeyId.KEYCODE_POWER)),
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
            AI_TILE to AI_MODES,
            VOICE_TILE to VOICE_MODES,
            KEYGUARD_TILE to KEYGUARD_MODES,
            APP_CONTROL_TILE to APP_CONTROL_MODES,
        )

    /**
     * 「添加命令」浮层里那一行入口 —— 挑一个本机应用作为多条命令的一步。
     *
     * 取值也是 [APP_TILE]（`app:`，永不写进配置），图标自动是 `LayoutGrid`。
     * 点它要进「打开应用」页，不能交给 `addStep(spec.token)`：空 `app:` 会被当一步写进去。
     */
    val APP_STEP: Spec =
        Spec(name = Strings.APP_PICKER_TITLE, token = APP_TILE)

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

    /** 内置动作的**条数** —— 「自定义命令」那一段从它开始（见 `BindingGridState.builtInCount`）。 */
    fun builtInCount(): Int = groupsFor().sumOf { it.second.size }

    /**
     * 网格那一屏的全部条目：内置动作 + 自定义命令。
     *
     *  保留此函数而非让调用方直接读 [ALL]：清单按手势分化随时可能回来，调用点不必跟着改。
     *  现在三档**没有分化**（见 [groupsFor]），所以这里不收手势参数。
     */
    fun forGesture(commands: List<Command> = emptyList()): List<Spec> {
        val base = groupsFor().flatMap { it.second }
        return base + commands.map { commandSpec(it) }
    }

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
        )

    /**
     * 「添加命令」浮层用的**分组** —— 就是网格那张 [GROUPS]，只做两处适配：
     * 「无操作」剔掉（它代表「吞掉按键」、不是一步动作），「应用控制」那一格**就地换成** [APP_STEP]
     * （一步只能是一个取值：打开某个应用；结束那一档不出现在步骤里）。
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
            title to
                group
                    // 「无操作」代表吞掉按键、不是一步动作
                    // （「灵感语音」不在顶层 —— 它落在小布 AI 那层菜单里，那里两行都是真动作）
                    .filterNot { it.token == NONE_TOKEN }
                    .map {
                        when (it.token) {
                            // 「应用控制」那一格是关卡，不是取值：多步里那一步只能是「打开某个应用」
                            // （`app:`）。结束那一档不在这里 —— 它是另一条真动作，由那一格自己挑。
                            APP_CONTROL_TILE -> APP_STEP

                            else -> it
                        }
                    }
        }

    /**
     * 「系统场景」那一档的候选表 —— 与 [STEP_GROUPS] 同一张表，只把**「无操作」加回来**。
     *
     *  场景的一条规则必须有个动作，而「无操作」在那里是一档真取值：它说的是「这个场景命中时
     *  什么都不做」，用来盖住排在后面的兜底场景。所以保存校验不再要求「每条场景都要选一个动作」。
     *
     *  **只在场景里加**：多条命令的步骤仍走 [STEP_GROUPS]（剔掉它）—— 步骤不是按键，
     *  「无操作」那一步等于白占一格，还让「有几步」这件事数不准。
     */
    val BRANCH_GROUPS: List<Pair<String, List<Spec>>> =
        GROUPS.map { (title, group) ->
            title to
                group
                    //  同 [STEP_GROUPS]：「应用控制」是关卡，一条场景要落成一条真动作（打开应用）
                    .map {
                        when (it.token) {
                            APP_CONTROL_TILE -> APP_STEP
                            else -> it
                        }
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
            // 「应用控制」代表**两族**：打开（`app:` / `window:` / 快捷方式）与结束（`proc:stop:`）。
            //  两款任意一条都算这一格选中 —— 点开之后那一层菜单里再各自精确打勾。
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
            //  （真机：打开设置页当场 StackOverflowError）。排除之后若一条都不剩，就走下面那套常规判据。
            val nested = leaves.filter { it.token != spec.token }
            if (nested.isNotEmpty()) return nested.any { isBound(it, action) }
        }
        //  「亮度 / 音量」这两族先判：它们比的是**占位写法的符号**，不是整串（见 [percentRowBound]）。
        percentRowBound(spec.token, action)?.let { return it }
        //  「应用控制」那两行不是取值（`appctl:open` / `appctl:stop`），比的是**绑的是哪一族**：
        //  打开那行认 `app:` / `window:` / 快捷方式，结束那行认 `proc:stop:`。菜单里的勾靠它。
        if (spec.token == APP_CONTROL_OPEN || spec.token == APP_CONTROL_STOP) {
            return if (spec.token == APP_CONTROL_OPEN) action.isAppOpen else action is Action.Process
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
     * 「屏幕亮度」「音量」这两组的判定 —— 三行是**占位写法**（`bright:+` / `-` / `=`，不带数值），
     * 真正落盘的是 `bright:+15` 这种带数值的串。按整串比的话，绑过之后菜单一行都不打勾、
     * 格子里也没有小字，所以这两族**只比符号、不比数值**。
     *
     *  必须走在 [canonicalToken] 之前：占位写法 `Action.parse` 认不出，它被解析一次就会刷一行
     *  「百分比认不出」的日志，而 `isBound` 是每格每次重组都会走的地方。
     *
     * @return `null` = 这一行不归这两族管，交给调用方接着判
     */
    private fun percentRowBound(
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

    /**
     * 音量**选定数值之后**的那一条（`volume:+10`）—— 与 [brightnessSpecOf] 同一支：
     * 菜单那三行不带数值，落盘的必须是拼好的那一条。
     */
    fun volumeSpecOf(
        mode: Action.Volume.Mode,
        percent: Int,
    ): Spec {
        val action = Action.Volume(mode, percent)
        return Spec(name = volumeTitle(action), token = action.token)
    }

    /**
     * 亮度**选定数值之后**的那一条（`bright:+15`）—— 弹窗按完「确定」时用它交给落盘那一侧。
     *
     *  菜单里那三行是占位写法（不带数值），所以不能拿行本身的 token 落盘；这一支把
     *  「哪一档 + 几」拼成真正的取值。名字即 [brightnessTitle]（「增加 15%」）——
     *  `label` 那一刻会再查一次字典、查不到就原样显示，正好是这句拼好的话。
     */
    fun brightnessSpecOf(
        mode: Action.Brightness.Mode,
        percent: Int,
    ): Spec {
        val action = Action.Brightness(mode, percent)
        return Spec(name = brightnessTitle(action), token = action.token)
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
        val setting = action as? Action.Setting ?: return null
        return when (setting.on) {
            true -> I18n.get(Strings.CATALOG_MODE_TURN_ON, "打开")
            false -> I18n.get(Strings.CATALOG_MODE_TURN_OFF, "关闭")
            null -> I18n.get(Strings.CATALOG_MODE_TOGGLE, "切换")
        }
    }

    /**
     * 这个 token 是不是**入口格**；是的话返回它那一层菜单的行，否则 `null`。
     *
     *  三档手势与命令链**拿到的是同一份**：清单里没有任何一行再按手势或按来处剔
     *  （灵感语音拆成开始 / 结束两个平级动作之后，两行都是真取值）。
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
        val toggle = Spec(name = Strings.CATALOG_MODE_TOGGLE, token = token)
        if (!token.startsWith(Action.SETTING_PREFIX)) return listOf(toggle)
        val id = token.removePrefix(Action.SETTING_PREFIX)
        return listOf(
            toggle,
            Spec(name = Strings.CATALOG_MODE_TURN_ON, token = Action.Setting(id, on = true).token),
            Spec(name = Strings.CATALOG_MODE_TURN_OFF, token = Action.Setting(id, on = false).token),
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
     * 当前动作落在入口格的哪一档上（行里那些标题：循环 / 静音 / 上一首 …）；不在任何一组时返回 `null`。
     *
     * 那几格格子只有一行组名，不点开菜单看不出绑的是哪一档，所以格子里补的这行小字要走它。
     * 算法与 `ui/model/CommandText.kt` 的 `stepTitle` 同源。
     */
    fun modeTitle(
        modes: List<Spec>,
        action: Action,
    ): String? = modes.firstOrNull { isBound(it, action) }?.label

    /**
     * 当前动作落在入口格的**哪一档**（`主屏幕` / `上一首` / `静音` …）；不在任何一组时返回 `null`。
     *
     * 步骤标题（`ui/model/CommandText.kt`）用它 —— 只要档位名，**不带上格子名**
     *（除「打开应用」外一律拍平，`系统导航:主屏幕` → `主屏幕`）。
     * 档位名与格子怎么配对由这一处决定，网格里那行小字走 [modeTitle]。
     */
    fun modeName(action: Action): String? =
        //  亮度 / 音量那两档把**数值**一起带上（「增加 15%」）：光写「增加」看不出加了多少，
        //  而这是步骤标题与执行提示唯一能表达数值的地方。
        when (action) {
            is Action.Brightness -> brightnessTitle(action)
            is Action.Volume -> volumeTitle(action)
            else -> modeHitOf(action)?.mode?.label
        }

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
        //  「落在哪一行」只有 [boundModeOf] 一处实现（它已经会往下探一层：灵感语音 → 开始 / 结束）
        val mode = boundModeOf(tile, action) ?: return null
        return ModeHit(tile, mode)
    }

    /** 见 [modeHitOf]：父格 + 那一行。 */
    data class ModeHit(
        val tile: Spec,
        val mode: Spec,
    )

    /**
     * 这一格点了会进下一层（跳「打开应用」页 / 跳「控制中心磁贴」页 / 弹一格菜单）。
     *
     * 「添加命令」抽屉**不用它**：那里先把「选择应用」那一格接走，剩下的判据正好是
     * 「这一档有没有一层菜单」，直接看 [modeMenu] 更贴切。网格的绑定路径用它。
     */
    fun isEntryTile(spec: Spec): Boolean =
        spec.token == APP_TILE ||
            modeMenu(spec.token) != null

    /**
     * 行尾那颗指示画成**选择器**（`PageIcons.Selector`：一个框 + 右下角小三角）的那几格。
     *
     *  判据是「点开的东西是**原地**弹出的一层」：入口格的档位菜单（系统导航 / 系统按键 / 媒体控制 /
     *  响铃状态…）—— 都在这一页里选完，不跳页。
     *
     *  与 [isEntryTile] **不是一回事**：同样是入口，「打开应用」（去挑应用）与「自定义变量」是跳页 /
     *  换一层，行尾画的是右箭头。两处判据分开写，才不会因为多加一格入口就把箭头换成选择器。
     */
    fun popsSelector(token: String): Boolean = modeMenu(token) != null

    /** 按动作找展示信息；清单外一律返回 null（清单是照搬的封闭集合，不为自定义写法现造条目）。 */
    fun specOf(action: Action): Spec? = ALL.firstOrNull { isSelected(it, action) }

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
            //  「人前叫什么」只能由动作自己按变量名与运算符拼 —— 与执行时提示同一支（`actionTitle`）。
            is Action.VarSet -> {
                actionTitle(action, { "" }, action.token)
            }

            else -> {
                modeName(action) ?: specOf(action)?.label
            }
        }
}
