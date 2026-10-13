package com.lookie.opluskey.page

import com.lookie.opluskey.config.Action
import com.lookie.opluskey.config.Command
import com.lookie.opluskey.config.CommandKind

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
    )

    /** 「无操作」那套图标（宿主里就是「一个斜杠圈」）。 */
    private const val ICON_NONE_MIDDLE = "ac_no_operation_middle_icon"
    private const val ICON_NONE_TAB = "ac_tab_icon_nothing"
    private const val ICON_NONE_OVAL = "action_button_oval_non_operation"

    /** 手电筒（KSU 的 `flashlight:`；语义是切换，由 daemon / 模块自己判断当前状态）。 */
    private const val FLASHLIGHT = "flashlight"

    /** 「无操作」的取值（[SERIAL_STEPS] 要把它剔掉，所以单独给个常量）。 */
    private const val NONE_TOKEN = "none"

    /** 「打开应用」这一格的取值 —— 永不写进配置，只是「点它去挑应用」的关卡标记。 */
    const val APP_TILE = "app:"

    /**
     * 小布识屏那条 shell 命令 —— 取值照搬 KSU 模块，但模块侧不走 shell，
     * 而是把同一串 intent 直接 `startForegroundService`（见 `hook/AppLauncher.startSidebar`）。
     */
    private const val SIDEBAR =
        "shell:am start-foreground-service -a oplus.intent.action.DIRECT_SIDEBAR_SERVICE" +
            " -e extra_entrance_function full_screen_ocr" +
            " -e triggered_app com.coloros.smartsidebar"

    /** 截图 —— 取值是 KSU 的 `service call`，模块侧换成注入 `KEYCODE_SYSRQ`。 */
    private const val SCREENSHOT = "shell:service call color_screenshot 1"

    /** 界面上按顺序列出的动作 —— 逐条照搬 KSU 模块的 `ACTION_PRESETS`（去掉 `custom`）。 */
    val ALL: List<Spec> = listOf(
        Spec(
            title = "无操作",
            summary = "按键被吞掉，什么都不做",
            token = "none",
            middleIcon = ICON_NONE_MIDDLE,
            tabIcon = ICON_NONE_TAB,
            oval = ICON_NONE_OVAL,
        ),
        Spec(
            title = "手电筒",
            summary = FLASHLIGHT,
            token = FLASHLIGHT,
        ),
        Spec(
            title = "截图",
            summary = SCREENSHOT,
            token = SCREENSHOT,
        ),
        Spec(
            title = "电源键",
            summary = "keyevent:26",
            token = "keyevent:26",
        ),
        Spec(
            title = "主屏幕",
            summary = "keyevent:3",
            token = "keyevent:3",
        ),
        Spec(
            title = "返回",
            summary = "keyevent:4",
            token = "keyevent:4",
        ),
        // 「最近任务」执行时不是注入按键：注入 KEYCODE_APP_SWITCH 在这台 ROM 上进不了 PM
        // 的处理分支，执行侧换成原生调用（`hook/ActionRunner.runKeyEvent`）。
        Spec(
            title = "最近任务",
            summary = "keyevent:187",
            token = "keyevent:187",
        ),
        // 「通知栏」不属于上一类 —— 注入有效
        Spec(
            title = "通知栏",
            summary = "keyevent:83",
            token = "keyevent:83",
        ),
        Spec(
            title = "播放 / 暂停",
            summary = "keyevent:85",
            token = "keyevent:85",
        ),
        Spec(
            title = "小布识屏",
            summary = SIDEBAR,
            token = SIDEBAR,
        ),
        // 这一格是入口格（点它弹 [RINGER_MODES] 菜单，不直接写配置），与「打开应用」同属
        // [isEntryTile]；但与 [APP_TILE] 不同，它是真取值（= 三档循环），能作为多条命令的一步。
        Spec(
            title = "响铃状态",
            summary = RINGER_TILE,
            token = RINGER_TILE,
        ),
        // 这一条是入口不是取值：点它跳「打开应用」页挑应用，选完绑成 `app:<包名>`（[Action.App]）。
        // 网格点击靠比这个常量认领；不能靠「`Action.parse` 能否匹配」——`app:` 本身就是合法
        // 的空 App 动作，走 `select()` 会把配置写成 `app:`（拉不起任何应用）。
        Spec(
            title = "打开应用",
            summary = "点进去挑一个本机应用（绑成 app:<包名>）",
            token = APP_TILE,
        ),
    )

    /**
     * 「响铃状态」那一格的取值（= 三档循环，照搬 KSU 的 `keyevent:164`）。
     *
     * 与 [APP_TILE] 不同，它是真取值（`Action.parse` 后是 `KeyEvent(164)`，执行侧转给
     * `hook/RingerController.cycle`），既能写进配置、也能作为多条命令的一步。
     * 它同时是入口格标记：网格里点它不写这个取值，而是弹 [RINGER_MODES] 菜单。
     */
    const val RINGER_TILE = "keyevent:164"

    /** 取值就是 [RINGER_TILE] —— 「点一下格子」与「点开菜单再选循环」写进的是同一个值。 */
    val RINGER_CYCLE: Spec = Spec(title = "循环", summary = RINGER_TILE, token = RINGER_TILE)

    /**
     * 响铃状态菜单的四行，顺序即行序。第一项是 [RINGER_CYCLE]（KSU 那套三档循环），
     * 后三项是模块扩展写法 `ringer:normal|vibrate|silent`（见 `config/Action.Ringer`）。
     *
     * 判定哪一行当前绑定要用 [isBound] 逐行比，不能用 [isSelected] —— 后者把四行都算作
     * 「响铃状态那一格选中」，打勾会让四行同时亮起来。
     */
    val RINGER_MODES: List<Spec> = listOf(
        RINGER_CYCLE,
        Spec(title = "响铃", summary = "ringer:normal", token = "ringer:normal"),
        Spec(title = "振动", summary = "ringer:vibrate", token = "ringer:vibrate"),
        Spec(title = "静音", summary = "ringer:silent", token = "ringer:silent"),
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
        @Suppress("UNUSED_PARAMETER") gesture: com.lookie.opluskey.config.Gesture,
        commands: List<Command> = emptyList(),
    ): List<Spec> = ALL + commands.map { commandSpec(it) }

    /**
     * 一条自定义命令的展示条目。
     *
     * 取值 `cmd:<名字>` 与 `config/Action.kt` 的 [Action.Custom] 同构，选出 / 回写配置走现成那
     * 条路，执行由 `hook/CustomCommandRunner.run` 展开；命令内容会被省略号截掉，只有名字认得出来。
     * 带上 [Spec.commandKind]，单条与多条在网格里才分得出。
     */
    fun commandSpec(command: Command): Spec = Spec(
        title = command.name,
        summary = command.token,
        token = command.token,
        commandKind = command.kind,
    )

    /**
     * 比 [ALL] 少两项：「无操作」与「打开应用」。前者在多条命令里无意义、只会让人误点；
     * 后者是入口条目（点它跳应用选择页），而这份清单是「点一下就写一步」。
     */
    val SERIAL_STEPS: List<Spec> = ALL.filterNot { it.token == NONE_TOKEN || it.token == APP_TILE }

    /**
     * 某一项是不是当前选中的动作。
     *
     * 必须比「规范化之后」的写法，不能直接比 [Spec.token]：清单里的 token 是源码写法
     * （照搬 KSU 模块），两边都过一遍 `Action.parse`，写法差异才不影响判定。
     */
    fun isSelected(spec: Spec, action: Action): Boolean = when (spec.token) {
        // 「打开应用」那格是入口：只要绑定任意 `app:` 动作就算选中，否则绑完回来一个亮的都没有
        APP_TILE -> action is Action.App
        // 「响铃状态」那格代表一整组取值：绑其中任意一个就算选中，否则格里一个亮的都没有
        RINGER_TILE -> isRinger(action)
        else -> isBound(spec, action)
    }

    /**
     * 一行一行地比某一项是不是当前绑定 —— 菜单打勾用它，不用 [isSelected]。
     *
     * 两者差别只在「响铃状态」那一组：菜单必须精确到行，否则四行同时亮起对勾。
     * 网格格子的判定也走它，规范化那套只有这一份实现。
     */
    fun isBound(spec: Spec, action: Action): Boolean =
        Action.parse(spec.token).token == action.token

    /** [RINGER_TILE] 规范化之后的写法（`keyevent:164`）—— [isRinger] 比的就是它。 */
    private val RINGER_CYCLE_TOKEN: String = Action.parse(RINGER_TILE).token

    /**
     * 当前动作是不是「响铃状态」那一组的取值（循环 / 响铃 / 振动 / 静音）。
     *
     * 循环那一档用 [Action.parse] 现算，不写死 164 —— `hook/ActionRunner` 里已有一处常量。
     */
    fun isRinger(action: Action): Boolean =
        action is Action.Ringer || action.token == RINGER_CYCLE_TOKEN

    /**
     * 当前动作落在哪一档（循环 / 响铃 / 振动 / 静音）；不是这一组时返回 `null`。
     *
     * 那一格只写组名，不点开菜单看不出绑的是哪一档。算法与 `page/CommandText.kt` 的 `stepTitle` 一致。
     */
    fun ringerModeTitle(action: Action): String? =
        RINGER_MODES.firstOrNull { isBound(it, action) }?.title

    /**
     * 这一格点了会进下一层（跳「打开应用」页 / 弹「响铃状态」菜单）。
     *
     * 只有「添加命令」浮层拿它标 `hasSubmenu` —— 网格里的格子不画行尾箭头。
     */
    fun isEntryTile(spec: Spec): Boolean = spec.token == APP_TILE || spec.token == RINGER_TILE

    /** 按动作找展示信息；清单外一律返回 null（清单是照搬的封闭集合，不为自定义写法现造条目）。 */
    fun specOf(action: Action): Spec? =
        ALL.firstOrNull { isSelected(it, action) }
}
