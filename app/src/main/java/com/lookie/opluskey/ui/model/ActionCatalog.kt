package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Gesture

/** 设置页上可选的「动作清单」（界面用的展示层数据）。 */
internal object ActionCatalog {

    /**
     * 一个可选动作的展示信息。
     *
     * @param title 界面上的名称（与 KSU 模块的显示文本一致）
     * @param summary 写进配置里的原始写法（界面上不显示，留着对照）
     * @param token `Action.parse()` 认得的那种写法
     * @param middleIcon 中央大图标的 drawable 名（宿主资源，可为空）
     * @param tabIcon 底部条图标的 drawable 名（宿主资源，可为空）
     * @param oval 圆形底的 drawable 名（宿主资源，可为空）
     * @param appPackage `app:` 动作的目标包名 —— 宿主没给图标时取目标 App 的真图标
     * @param commandKind 自定义命令的类型；内置动作恒为 `null`。图标据此选字形：网格格子只有一行文字，单条 / 多条只能靠图标区分。
     * @param commandSteps 自定义命令里「多条命令」的步骤数；单条与内置动作恒为 `0`。
     *   多条命令的图标按它换 `box-multiple-N`，光靠 [commandKind] 只能分出「是多条」、分不出「有几步」。
     * @param dividerBefore 弹菜单时**这一行前面**用一条**加深带**（`MenuEntry.GroupLine`）而不是
     *   相邻两行那条细线 —— 把一组菜单切成两段。目前两处：媒体控制（「播放 / 暂停 / 播放 / 暂停」
     *   与「上一首 / 下一首」之间）、响铃状态（「循环」与那三档之间）。
     *   网格格子、步骤行、格子里那行小字都不读它 —— 只有菜单的分隔线看这个字段。
     */
    data class Spec(
        val title: String,
        val summary: String,
        val token: String,
        val middleIcon: String? = null,
        val tabIcon: String? = null,
        val oval: String? = null,
        val appPackage: String? = null,
        val commandKind: CommandKind? = null,
        val commandSteps: Int = 0,
        val dividerBefore: Boolean = false,
    )

    /** 「无操作」那套图标（宿主里就是「一个斜杠圈」）。 */
    private const val ICON_NONE_MIDDLE = "ac_no_operation_middle_icon"
    private const val ICON_NONE_TAB = "ac_tab_icon_nothing"
    private const val ICON_NONE_OVAL = "action_button_oval_non_operation"

    /** 手电筒（KSU 的 `flashlight:`；语义是切换，由 daemon / 模块自己判断当前状态）。 */
    private const val FLASHLIGHT = "flashlight"

    /** 「无操作」的取值（[STEP_GROUPS] 要把它剔掉，所以单独给个常量）。 */
    private const val NONE_TOKEN = "none"

    // 「交还系统」那一格（`native` / `passthrough`）**已删**（2026-10-14 用户要求）：
    //  做不到单个手势级的交还 —— 一交还就是整键，同一把键上双击 / 长按的命令也跟着被系统吞掉。
    //  取舍与依据写在 `docs/implementation.md` 的「交还系统为什么不提供」。

    /** 「打开应用」这一格的取值 —— 永不写进配置，只是「点它去挑应用」的关卡标记。 */
    const val APP_TILE = "app:"

    /**
     * 小布识屏那条 shell 命令 —— 取值照搬 KSU 模块，但模块侧不走 shell，
     * 而是把同一串 intent 直接 `startForegroundService`（见 `host/action/AppLauncher.startSidebar`）。
     */
    private const val SIDEBAR =
        "shell:am start-foreground-service -a oplus.intent.action.DIRECT_SIDEBAR_SERVICE" +
            " -e extra_entrance_function full_screen_ocr" +
            " -e triggered_app com.coloros.smartsidebar"

    /** 截图 —— 取值是 KSU 的 `service call`，模块侧换成注入 `KEYCODE_SYSRQ`。 */
    private const val SCREENSHOT = "shell:service call color_screenshot 1"

    /** 电源菜单 —— KSU 没有这项；执行侧在 system_server 里反射调 PWM，不走按键长按。 */
    private const val POWER_MENU = "power:menu"

    /**
     * 把**前台**那个应用收成小窗 —— 执行侧走的是 `window:` 同一族小窗接口，只把目标换成当前前台活动。
     *
     * 展开档的取值。与 [MINI_COLLAPSE] 同属**入口格**：[MINI_TILE]，点它弹 [MINI_MODES] 菜单。
     */
    private const val MINI_WINDOW = "zoom:mini"

    /**
     * 收起档：把前台应用收成右上角那条最小化窗（`zoom:mini:collapse`）。
     *
     * 与展开档的差别只在尺寸档 —— 服务端凭 `mZoomLaunchFlags` 分流（`2` = 展开、`32` = 收起），
     * 落点几何由服务端自己算。见 `core/model/Action.MiniWindow`。
     */
    private const val MINI_COLLAPSE = "zoom:mini:collapse"

    /**
     * 系统侧键场景表里的三档（取值见 `host/action/OplusFeature`）：一键闪记 / 免打扰 / 录音。
     * 系统自己的侧键设置里就是这三项，调用目标逐字抄自它的场景表。
     */
    private const val FLASH_MEMORY = "oplus:flash_memory"
    private const val NO_DISTURB = "oplus:no_disturb"
    private const val RECORDING = "oplus:recording"

    /**
     * 内置命令分成两类 —— 界面上各一个小标题，组内顺序即显示顺序。
     *
     * [ALL] 是把两组按顺序拍平的**扁平表**：焦点索引、图标预热等调用方只看它，
     * 分组只是展示层切分，不影响取值与判定（选中的比法、写进配置的 token 都不变）。
     */
    val GROUPS: List<Pair<String, List<Spec>>> = listOf(
        // 无操作在最前：它是「解除绑定」的语义，得一眼能找到（2026-10-14 用户定的）。
        // 组名不叫「系统按键」：这一组里通知与控制中心 / 媒体控制 / 响铃状态 / 电源菜单都不是「按键」。
        "系统操作" to listOf(
            Spec(
                title = "无操作",
                summary = "按键被吞掉，什么都不做",
                token = "none",
                middleIcon = ICON_NONE_MIDDLE,
                tabIcon = ICON_NONE_TAB,
                oval = ICON_NONE_OVAL,
            ),
            // 「主屏幕 / 返回 / 最近任务」三档收进这一格：点它弹 [NAV_MODES] 菜单（2026-10-14）。
            // 与 [MEDIA_TILE] / [RINGER_TILE] 不同，这三档**各不相同**，格子本身代表不了任何一个取值
            // ⇒ 与 [APP_TILE] 同类，是个**标记**：永不写进配置，也不能作为多条命令的一步。
            Spec(title = "系统导航", summary = NAV_TILE, token = NAV_TILE),
            // 「通知与控制中心」那一格：点它弹 [PANEL_MODES] 菜单（2026-10-14 用户要求把原来的
            // 「通知栏」一档拆成两条）。两条各是一个真取值，格子代表不了其中任何一条
            // ⇒ 与 [NAV_TILE] / [MINI_TILE] 同类，是个**标记**格，永不写进配置。
            Spec(title = "通知与控制中心", summary = PANEL_TILE, token = PANEL_TILE),
            // 这一格与「响铃状态」同属**入口格**（点它弹 [MEDIA_MODES] 菜单，不直接写配置），
            // 与「打开应用」一起归 [isEntryTile]；但它俩是真取值，能作为多条命令的一步。
            // 「播放 / 暂停」原来在网格里单占一格，现在收进这里 —— 同一个取值不给两个入口。
            Spec(title = "媒体控制", summary = MEDIA_TILE, token = MEDIA_TILE),
            Spec(title = "响铃状态", summary = RINGER_TILE, token = RINGER_TILE),
            // 「音量 + / 音量 − / 电源键」三档收进这一格（点它弹 [KEY_MODES] 菜单），同样是标记。
            Spec(title = "系统按键", summary = KEY_TILE, token = KEY_TILE),
            // KSU 没有这项；执行侧在 system_server 里反射调 PWM，不走按键长按
            Spec(title = "电源菜单", summary = POWER_MENU, token = POWER_MENU),
        ),
        // 后三项是系统侧键场景表里的档位：起的是各功能自己的 KeyMagic / 闪记服务
        "快捷功能" to listOf(
            // KSU 的 `flashlight:`；语义是切换，由 daemon / 模块自己判断当前状态
            Spec(title = "手电筒", summary = FLASHLIGHT, token = FLASHLIGHT),
            // 取值是 KSU 的 `service call`，模块侧换成注入 `KEYCODE_SYSRQ`
            Spec(title = "截图", summary = SCREENSHOT, token = SCREENSHOT),
            // 取值照搬 KSU 模块，但模块侧不走 shell —— 见 `host/action/AppLauncher.startSidebar`
            Spec(title = "小布识屏", summary = SIDEBAR, token = SIDEBAR),
            Spec(title = "一键闪记", summary = FLASH_MEMORY, token = FLASH_MEMORY),
            Spec(title = "免打扰", summary = NO_DISTURB, token = NO_DISTURB),
            Spec(title = "录音", summary = RECORDING, token = RECORDING),
            // 与 `window:` 同一族小窗接口，只是目标不同：那条按包名解析入口，这条取当前前台活动。
            // 两档尺寸（展开 / 收起）各不相同，格子代表不了其中任何一个 ⇒ 按 [MINI_TILE] 当入口格、弹菜单
            Spec(title = "系统小窗", summary = MINI_TILE, token = MINI_TILE),
            // 这一条是入口不是取值：点它跳「打开应用」页挑应用，选完绑成 `app:<包名>`（[Action.App]）。
            // 网格点击靠比这个常量认领；不能靠「`Action.parse` 能否匹配」——`app:` 本身就是合法
            // 的空 App 动作，走 `select()` 会把配置写成 `app:`（拉不起任何应用）。
            Spec(
                title = "打开应用",
                summary = "点进去挑一个本机应用（绑成 app:<包名>）",
                token = APP_TILE,
            ),
        ),
    )

    /** 界面上按顺序列出的动作 —— [GROUPS] 拍平后的扁平表（以 KSU 的 `ACTION_PRESETS` 去掉 `custom` 为底）。 */
    val ALL: List<Spec> = GROUPS.flatMap { it.second }

    /**
     * 「响铃状态」那一格的取值（= 三档循环，照搬 KSU 的 `keyevent:164`）。
     *
     * 与 [APP_TILE] 不同，它是真取值（`Action.parse` 后是 `KeyEvent(164)`，执行侧转给
     * `inject/system/RingerController.cycle`），既能写进配置、也能作为多条命令的一步。
     * 它同时是入口格标记：网格里点它不写这个取值，而是弹 [RINGER_MODES] 菜单。
     */
    const val RINGER_TILE = "keyevent:164"

    /** 取值就是 [RINGER_TILE] —— 「点一下格子」与「点开菜单再选循环」写进的是同一个值。 */
    val RINGER_CYCLE: Spec = Spec(title = "循环", summary = RINGER_TILE, token = RINGER_TILE)

    /**
     * 响铃状态菜单的四行，顺序即行序。第一项是 [RINGER_CYCLE]（KSU 那套三档循环），
     * 后三项是模块扩展写法 `ringer:normal|vibrate|silent`（见 `core/model/Action.Ringer`）。
     *
     * 判定哪一行当前绑定要用 [isBound] 逐行比，不能用 [isSelected] —— 后者把四行都算作
     * 「响铃状态那一格选中」，打勾会让四行同时亮起来。
     */
    val RINGER_MODES: List<Spec> = listOf(
        RINGER_CYCLE,
        // 加深带：上一条是 KSU 那套「按一下循环三档」，下面三条是直接指定某一档 —— 两件事，分两段
        Spec(
            title = "响铃",
            summary = "ringer:normal",
            token = "ringer:normal",
            dividerBefore = true,
        ),
        Spec(title = "振动", summary = "ringer:vibrate", token = "ringer:vibrate"),
        Spec(title = "静音", summary = "ringer:silent", token = "ringer:silent"),
    )

    /**
     * 「媒体控制」那一格的取值 —— 就是原来的「播放 / 暂停」（`keyevent:85`）。
     *
     * 与 [RINGER_TILE] 同属**入口格**：网格里点它不直接写这个取值，而是弹 [MEDIA_MODES] 菜单。
     * 它同时是真取值（`Action.parse` 后是 `KeyEvent(85)`），能写进配置、也能作为多条命令的一步。
     */
    const val MEDIA_TILE = "keyevent:85"

    /**
     * 媒体控制菜单的五条，顺序即行序。五条都是普通 `keyevent:` —— 首行就是格子自己那个取值。
     *
     * 播放与暂停拆成两条（`KEYCODE_MEDIA_PLAY` / `KEYCODE_MEDIA_PAUSE`）而不是复用首行那个
     * 播放暂停键：首行那个码交给系统按**当前状态**翻面（在放就停、停就放），这两条是「无论如何
     * 都要播 / 要停」—— 三者在没在播放两种情形下行为不同，不能互相代替。
     */
    val MEDIA_MODES: List<Spec> = listOf(
        Spec(title = "播放 / 暂停", summary = MEDIA_TILE, token = MEDIA_TILE), // KEYCODE_MEDIA_PLAY_PAUSE
        Spec(title = "播放", summary = "keyevent:126", token = "keyevent:126"), // KEYCODE_MEDIA_PLAY
        Spec(title = "暂停", summary = "keyevent:127", token = "keyevent:127"), // KEYCODE_MEDIA_PAUSE
        // 加深带：上三档是「这一格自己」（按当前状态播 / 停），下两档是切歌 —— 两件事，分两段
        Spec(
            title = "上一首",
            summary = "keyevent:88",
            token = "keyevent:88",
            dividerBefore = true,
        ), // KEYCODE_MEDIA_PREVIOUS
        Spec(title = "下一首", summary = "keyevent:87", token = "keyevent:87"), // KEYCODE_MEDIA_NEXT
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
    const val PANEL_TILE = "panel:"

    /**
     * 「控制中心」那一档的取值（见 `core/model/Action.Panel`）。
     *
     *  另一档「通知中心」沿用历史写法 `keyevent:83`（改名不改取值 —— 改了用户已有的绑定会失效）。
     */
    const val PANEL_CONTROL = "panel:${Action.Panel.CONTROL_CENTER}"

    /**
     * 「系统小窗」那一格的标记 —— 与 [NAV_TILE] / [KEY_TILE] 同类。
     *
     * 两档尺寸（展开的普通小窗 / 收起的最小化窗）各不相同，格子代表不了其中任何一个，
     * 所以是**标记**（永不写进配置），菜单里那两档才是真取值。
     */
    const val MINI_TILE = "zoom:"

    /**
     * 「系统小窗」菜单的两行，顺序即行序。两行都是真取值（见 `core/model/Action.MiniWindow`）：
     *  · **展开** = 居中大面积的普通小窗，服务端 `setUserPositionInfo`（`mZoomLaunchFlags = 2`）；
     *  · **收起** = 右上角一条的最小化窗，服务端 `fullToMini`（`mZoomLaunchFlags = 32`）。
     *
     * 执行路径也不同：展开档优先走 `system_server` 的原生动画，收起档只有 recents 正门这一条。
     */
    val MINI_MODES: List<Spec> = listOf(
        Spec(title = "展开小窗", summary = MINI_WINDOW, token = MINI_WINDOW),
        Spec(title = "收起小窗", summary = MINI_COLLAPSE, token = MINI_COLLAPSE),
    )

    /** 「系统导航」菜单的三行，顺序即行序。 */
    val NAV_MODES: List<Spec> = listOf(
        Spec(title = "主屏幕", summary = "keyevent:3", token = "keyevent:3"),
        Spec(title = "返回", summary = "keyevent:4", token = "keyevent:4"),
        // 执行时不是注入按键：注入 KEYCODE_APP_SWITCH 在这台 ROM 上进不了 PM 的处理分支，
        // 执行侧换成原生调用（见 `host/action/ActionRunner.runKeyEvent`）。
        Spec(title = "最近任务", summary = "keyevent:187", token = "keyevent:187"),
    )

    /**
     * 「通知与控制中心」菜单的两行，顺序即行序。两行都是真取值。
     *
     *  · **通知中心** = `keyevent:83`（历史写法，改名不改取值）；执行侧这个码不走注入，
     *    转给 `StatusBarBridge` 让 system_server 去 `togglePanel`（见 `ActionRunner.runKeyEvent`）。
     *  · **控制中心** = `panel:control_center`（新档，见 `core/model/Action.Panel`）；
     *    同一条通道，落点是我们自己在 system_server 里试出来的候选（表里没有这一行）。
     */
    val PANEL_MODES: List<Spec> = listOf(
        Spec(title = "通知中心", summary = "keyevent:83", token = "keyevent:83"),
        Spec(title = "控制中心", summary = PANEL_CONTROL, token = PANEL_CONTROL),
    )

    /**
     * 「系统按键」菜单的三行，顺序即行序。
     *
     *  音量键在这里是**动作**（绑给侧键后注入一次音量键），与「不接管物理音量键」那条纪律
     * 不冲突 —— 后者说的是不去拦截音量键本身（上下一同按要留给系统截屏，见 `core/model/KeyId`）。
     */
    val KEY_MODES: List<Spec> = listOf(
        Spec(title = "音量 +", summary = "keyevent:24", token = "keyevent:24"), // KEYCODE_VOLUME_UP
        Spec(title = "音量 −", summary = "keyevent:25", token = "keyevent:25"), // KEYCODE_VOLUME_DOWN
        Spec(title = "电源键", summary = "keyevent:26", token = "keyevent:26"), // KEYCODE_POWER
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
    private val MODE_MENUS: Map<String, List<Spec>> = mapOf(
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
    val APP_STEP: Spec = Spec(
        title = "选择应用",
        summary = "挑一个本机应用，作为一条步骤（绑成 app:<包名>）",
        token = APP_TILE,
    )

    /** 保留此函数而非让调用方直接读 [ALL]：清单按手势分化随时可能回来，调用点不必跟着改。 */
    fun forGesture(
        @Suppress("UNUSED_PARAMETER") gesture: com.lookie.opluskey.core.model.Gesture,
        commands: List<Command> = emptyList(),
    ): List<Spec> = ALL + commands.map { commandSpec(it) }

    /**
     * 一条自定义命令的展示条目。
     *
     * 取值 `cmd:<名字>` 与 `core/model/Action.kt` 的 [Action.Custom] 同构，选出 / 回写配置走现成那
     * 条路，执行由 `host/action/CustomCommandRunner.run` 展开；命令内容会被省略号截掉，只有名字认得出来。
     * 带上 [Spec.commandKind]，单条与多条在网格里才分得出；多条还要带 [Command.steps] 的条数，
     * 那一格的图标才是「有几步」。
     */
    fun commandSpec(command: Command): Spec = Spec(
        title = command.name,
        summary = command.token,
        token = command.token,
        commandKind = command.kind,
        commandSteps = command.steps.size,
    )

    /**
     * 「添加命令」浮层用的**分组** —— 就是网格那张 [GROUPS]，只做两处适配：
     * 「无操作」剔掉（它代表「吞掉按键」、不是一步动作），「打开应用」那格**就地换成** [APP_STEP]
     * （同一个 token，点它进「打开应用」页挑一个）。
     *
     * ⚠️ 入口格在这里保持收拢：点开二级菜单，二级与网格格子弹的是同一张 [MODE_MENUS]。
     * 把档位平铺成独立行会让同一个清单长出两副面孔，**别这么做**。
     */
    val STEP_GROUPS: List<Pair<String, List<Spec>>> = GROUPS.map { (title, group) ->
        title to group
            .filterNot { it.token == NONE_TOKEN }
            .map { if (it.token == APP_TILE) APP_STEP else it }
    }

    /**
     * 某一项是不是当前选中的动作。
     *
     * 必须比「规范化之后」的写法，不能直接比 [Spec.token]：清单里的 token 是源码写法
     * （照搬 KSU 模块），两边都过一遍 `Action.parse`，写法差异才不影响判定。
     */
    fun isSelected(spec: Spec, action: Action): Boolean {
        // 「打开应用」那格是入口：只要绑任意 `app:` / `window:` 动作就算选中，否则绑完回来一个亮的都没有
        if (spec.token == APP_TILE) return action is Action.App || action is Action.AppWindow
        // 入口格代表**一整组取值**：绑其中任意一档就算选中，否则格里一个亮的都没有
        return modeMenu(spec.token)?.any { isBound(it, action) } ?: isBound(spec, action)
    }

    /**
     * 一行一行地比某一项是不是当前绑定 —— 菜单打勾用它，不用 [isSelected]。
     *
     * 两者差别只在入口格那一组：菜单必须精确到行，否则整组同时亮起对勾。
     * 网格格子的判定也走它，规范化那套只有这一份实现。
     */
    fun isBound(spec: Spec, action: Action): Boolean =
        Action.parse(spec.token).token == action.token

    /** 这个 token 是不是**入口格**；是的话返回它那一层菜单的行，否则 `null`。 */
    fun modeMenu(token: String): List<Spec>? = MODE_MENUS[token]

    /**
     * 当前动作落在入口格的哪一档上（行里那些标题：循环 / 静音 / 上一首 …）；不在任何一组时返回 `null`。
     *
     * 那几格格子只有一行组名，不点开菜单看不出绑的是哪一档，所以格子里补的这行小字要走它。
     * 算法与 `ui/model/CommandText.kt` 的 `stepTitle` 同源。
     */
    fun modeTitle(modes: List<Spec>, action: Action): String? =
        modes.firstOrNull { isBound(it, action) }?.title

    /**
     * 当前动作落在入口格的**哪一档**（`主屏幕` / `上一首` / `静音` …）；不在任何一组时返回 `null`。
     *
     * 步骤标题（`ui/model/CommandText.kt`）用它 —— 只要档位名，**不带上格子名**
     *（2026-10-15 用户要求：除「打开应用」外一律拍平，`系统导航:主屏幕` → `主屏幕`）。
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
    fun specOf(action: Action): Spec? =
        ALL.firstOrNull { isSelected(it, action) }
}
