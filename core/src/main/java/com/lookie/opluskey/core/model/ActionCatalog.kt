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
        /** 「响铃状态」那一格（叶子图标）。 */
        Bell,

        /** 响铃菜单首行「循环」（与上面共用一个 token，字形不同 ⇒ 只能按 glyph 分）。 */
        Repeat,

        /** 「媒体控制」那一格（与它菜单首行共用一个 token，同理）。 */
        Music,

        /** 「系统导航」标记格。 */
        NavigationCog,

        /** 「系统按键」标记格。 */
        Click,

        /** 「通知与控制中心」标记格。 */
        Panels,

        /** 「前台转小窗」标记格。 */
        MiniWindow,
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

    /** 「打开应用」这一格的取值 —— 永不写进配置，只是「点它去挑应用」的关卡标记。 */
    const val APP_TILE = Action.APP_PREFIX

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

    /** 手电筒（KSU 的 `flashlight:`；语义是切换，由模块自己判断当前状态）。 */
    private val FLASHLIGHT = Action.FLASHLIGHT

    /** 「无操作」的取值（[STEP_GROUPS] 要把它剔掉，所以单独给个常量）。 */
    private val NONE_TOKEN = Action.NONE

    /**
     * 系统侧键场景表里的三档（落点见 `core/model/RomProfile`）：一键闪记 / 免打扰 / 录音。
     * 系统自己的侧键设置里就是这三项，调用目标逐字抄自它的场景表。
     */
    private val FLASH_MEMORY = Action.OPLUS_PREFIX + RomProfile.NAME_FLASH_MEMORY
    private val NO_DISTURB = Action.OPLUS_PREFIX + RomProfile.NAME_NO_DISTURB
    private val RECORDING = Action.OPLUS_PREFIX + RomProfile.NAME_RECORDING

    /**
     * 灵感语音（AI 记忆的长按录音）。**只在长按格子出现**，也不能作为多条命令的一步 ——
     * 它起的是麦克风采集，短按一下、或排在命令链中间都没有语义（见 [forGesture] / [STEP_GROUPS]）。
     */
    private val VOICE_MEMORY = Action.OPLUS_PREFIX + RomProfile.NAME_VOICE_MEMORY

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
        Spec(name = Strings.CATEGORY_MEDIA, token = MEDIA_TILE, glyph = Glyph.Music)

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
                    Spec(name = Strings.CATEGORY_RINGER, token = RINGER_TILE, glyph = Glyph.Bell),
                    // 「音量 + / 音量 − / 电源键」三档收进这一格（点它弹 [KEY_MODES] 菜单），同样是标记。
                    Spec(name = Strings.CATALOG_SYSTEM_KEYS, token = KEY_TILE, glyph = Glyph.Click),
                    // KSU 没有这项；执行侧在 system_server 里反射调 PWM，不走按键长按
                    Spec(name = Strings.PANEL_POWER_MENU, token = POWER_MENU),
                ),
            // 后三项是系统侧键场景表里的档位：起的是各功能自己的 KeyMagic / 闪记服务
            Strings.CATALOG_QUICK_TOOLS to
                listOf(
                    // KSU 的 `flashlight:`；语义是切换，由 daemon / 模块自己判断当前状态
                    Spec(name = Strings.CATALOG_FLASHLIGHT, token = FLASHLIGHT),
                    // 取值是 KSU 的 `service call`，模块侧换成注入 `KEYCODE_SYSRQ`
                    Spec(name = Strings.CATALOG_SCREENSHOT, token = SCREENSHOT),
                    // 取值照搬 KSU 模块，但模块侧不走 shell —— 见 `host/action/AppLauncher.startSidebar`
                    Spec(name = Strings.TOOL_SCREEN_RECOGNITION, token = SIDEBAR),
                    Spec(name = Strings.CATALOG_FLASH_NOTE, token = FLASH_MEMORY),
                    // 灵感语音：只在长按那张网格里出现（[forGesture] 过滤），见 [VOICE_MEMORY] 的说明
                    Spec(name = Strings.CATALOG_VOICE_MEMORY, token = VOICE_MEMORY),
                    Spec(name = Strings.CATALOG_DND, token = NO_DISTURB),
                    Spec(name = Strings.CATALOG_RECORDING, token = RECORDING),
                    // 与 `window:` 同一族小窗接口，只是目标不同：那条按包名解析入口，这条取当前前台活动。
                    // 两档尺寸（展开 / 收起）各不相同，格子代表不了其中任何一个 ⇒ 按 [MINI_TILE] 当入口格、弹菜单
                    Spec(name = Strings.TOOL_MINI_WINDOW, token = MINI_TILE, glyph = Glyph.MiniWindow),
                    // 这一条是入口不是取值：点它跳「打开应用」页挑应用，选完绑成 `app:<包名>`（[Action.App]）。
                    // 网格点击靠比这个常量认领；不能靠「`Action.parse` 能否匹配」——`app:` 本身就是合法
                    // 的空 App 动作，走 `select()` 会把配置写成 `app:`（拉不起任何应用）。
                    Spec(name = Strings.TOOL_OPEN_APP, token = APP_TILE),
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
     * 这一档手势的**内置动作分组** —— [GROUPS] 按手势剔掉在这一档不成立的条目（此刻只有「灵感语音」）。
     *
     *  为什么网格必须用这一份、而不是继续按 [GROUPS] 的组大小切 [forGesture] 的结果：
     *  `forGesture` 少了「灵感语音」那一格，而组大小没少 ⇒ 最后一组（「快捷功能」）会多吃进
     *  **第一条自定义命令**（真机反馈「单击 / 双击的自定义命令跑到快捷功能里了」）。
     *  清单与切分必须同源：谁剔条目，就由谁给出分组。
     */
    fun groupsFor(gesture: Gesture): List<Pair<String, List<Spec>>> =
        if (gesture == Gesture.LONG) {
            GROUPS
        } else {
            GROUPS.map { (title, group) -> title to group.filterNot { it.token == VOICE_MEMORY } }
        }

    /** 这一档手势**内置动作的条数** —— 「自定义命令」那一段从它开始（见 `BindingGridState.builtInCount`）。 */
    fun builtInCount(gesture: Gesture): Int = groupsFor(gesture).sumOf { it.second.size }

    /** 保留此函数而非让调用方直接读 [ALL]：清单按手势分化随时可能回来，调用点不必跟着改。 */
    fun forGesture(
        gesture: Gesture,
        commands: List<Command> = emptyList(),
    ): List<Spec> {
        // 「灵感语音」只挂在长按上：短按 / 双击拨它等于「按一下就开录」，那不是这个能力的语义。
        //  网格按手势各建一份（`rememberBindingGridState` 按 g 取），所以只在这里挡一次就够。
        val base = groupsFor(gesture).flatMap { it.second }
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
        )

    /**
     * 界面处处共用的**那一条队列** —— 网格那张表（[forGesture]）、「添加命令」浮层那张表
     * （[STEP_GROUPS]）、入口格点开的各层菜单（[MODE_MENUS]）、自定义命令，本来就是同一批
     * [Spec] 的不同投影（「打开应用」那格在浮层里换成 [APP_STEP]、「无操作」被剔掉，其余是
     * 同一批对象）；这里给出并集，`distinct` 按结构去重。
     *
     * 图标表按它建**一次**（见 `SettingsPage` 的 `tileIcons`）：网格格子、格子里那行小字、
     * 「添加命令」的行、入口格菜单的行一律从那一份里取 —— 界面层不再各自现调 [specIcon]，
     * 同一格在哪一处露面都是同一颗字形。
     */
    fun queue(commands: List<Command> = emptyList()): List<Spec> =
        buildList {
            addAll(ALL)
            addAll(STEP_GROUPS.flatMap { it.second })
            MODE_MENUS.values.forEach { addAll(it) }
            commands.forEach { add(commandSpec(it)) }
        }.distinct()

    /**
     * 「添加命令」浮层用的**分组** —— 就是网格那张 [GROUPS]，只做两处适配：
     * 「无操作」剔掉（它代表「吞掉按键」、不是一步动作），「打开应用」那格**就地换成** [APP_STEP]
     * （同一个 token，点它进「打开应用」页挑一个）。
     *
     * 注意：入口格在这里保持收拢：点开二级菜单，二级与网格格子弹的是同一张 [MODE_MENUS]。
     * 把档位平铺成独立行会让同一个清单长出两副面孔，**别这么做**。
     */
    val STEP_GROUPS: List<Pair<String, List<Spec>>> =
        GROUPS.map { (title, group) ->
            title to
                group
                    // 「无操作」代表吞掉按键、不是一步动作；「灵感语音」是长按才成立的采集，排进命令链没有语义
                    .filterNot { it.token == NONE_TOKEN || it.token == VOICE_MEMORY }
                    .map { if (it.token == APP_TILE) APP_STEP else it }
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
    ): Boolean {
        // 「打开应用」那格是入口：只要绑任意 `app:` / `window:` 动作就算选中，否则绑完回来一个亮的都没有
        if (spec.token == APP_TILE) return action.isAppOpen
        // 入口格代表**一整组取值**：绑其中任意一档就算选中，否则格里一个亮的都没有
        return modeMenu(spec.token)?.any { isBound(it, action) } ?: isBound(spec, action)
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
    ): Boolean = canonicalToken(spec.token) == action.token

    /** 这个 token 是不是**入口格**；是的话返回它那一层菜单的行，否则 `null`。 */
    fun modeMenu(token: String): List<Spec>? = MODE_MENUS[token]

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

    fun canonicalToken(token: String): String = canonicalTokens.getOrPut(token) { Action.parse(token).token }

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
        ALL.firstOrNull { it.token in MODE_MENUS && isSelected(it, action) }?.let { tile ->
            modeMenu(tile.token)?.let { modeTitle(it, action) } ?: action.token
        }

    /**
     * 这一格点了会进下一层（跳「打开应用」页 / 弹一格菜单）。
     *
     * 只有「添加命令」浮层拿它标 `hasSubmenu` —— 网格里的格子不画行尾箭头。
     */
    fun isEntryTile(spec: Spec): Boolean = spec.token == APP_TILE || modeMenu(spec.token) != null

    /** 按动作找展示信息；清单外一律返回 null（清单是照搬的封闭集合，不为自定义写法现造条目）。 */
    fun specOf(action: Action): Spec? = ALL.firstOrNull { isSelected(it, action) }
}
