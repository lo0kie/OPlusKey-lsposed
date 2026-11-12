package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action

// 「一个动作怎么念」—— 全项目**唯一**实现。
//
// 原先这句话有四个拼装函数（`ActionLabels.builtInTitle` / `ModeHit.detail` /
// `ActionLabels.appControlTitle` / `ActionLabels.varSetTitle`）和五个对外包装，调用点各挑一个，
// 同一个动作能出三种句子。改一处漏一处就是这么来的。
// 现在只剩这里：一个 `join`（private，谁碰不到）拼句子，两个**按位置命名**的出口。
//
// 出口为什么按位置、不按裁剪命名：按裁剪（完整 / 去掉父 / 只有名字）命名，等于让每个调用点
// 自己判断「我这儿该要哪半句」—— 今晚四处重复里有三处就是判错的。按位置命名之后，
// 调用点只说「我在哪儿显示」，挑错半句在设计上就没有位置。

/**
 * 一个动作的三段材料 —— 父是谁、叫什么、状态是什么。
 *
 * 三段凑出的四种形态（**推出来的，不是登记出来的**：加一档动作不会因为忘标类型而写错句子）：
 *
 * | [parent] | [state] | 形态 | 例子 |
 * |---|---|---|---|
 * | null | null | 单一功能 | `截屏` |
 * | 有 | null | 父子动作 | `锁屏快捷: 相机` |
 * | 有 | 有 | 父子动作 + 状态 | `系统开关: WIFI - 打开` |
 * | null | 有 | 单一功能 + 状态 | `录屏 - 切换` |
 *
 * @param parent 父动作名（已过字典）；`null` = 这一档自己就是那个动作
 * @param name 子动作名 / 自身名；空串表示「没有名字那一段」（此时 [state] 直接当整句）
 * @param state 状态那一段（已过字典）；`null` = 这一档没有状态
 * @param nameIsTheTile [name] **就是那一格的格子名**（第 4 类：录屏 / 录音 / 前台转小窗这类
 *   「格子本身即动作」的）。网格小字据此省掉名字那一段 —— 格子标题已经写着它了，
 *   小字再写一遍就是同一句话说两遍（真机反馈：「前台转小窗」那一格的小字是「前台转小窗」）。
 *   句子那一侧照写（`录屏 - 切换`）：提示 / 步骤行上面没有格子名可看。
 */
internal data class TitleSegments(
    val parent: String?,
    val name: String,
    val state: String?,
    val nameIsTheTile: Boolean = false,
)

/**
 * 「这个动作叫什么」—— 全项目唯一出口。
 *
 * ## 两个出口，按**显示位置**命名
 *
 *  · [forSentence] —— 完整一句。执行提示、步骤行、命令卡摘要、系统场景格、长按「额外动作」
 *    那一行都用它；
 *  · [forTile] —— 网格 / 抽屉里那一格的小字。**比 [forSentence] 省一档**：
 *    「格子本身即动作」的那几格（录屏 / 录音 / 前台转小窗）只写状态（`切换`）——
 *    那一格的标题就是那个动作，小字再写一遍等于把标题抄一遍。
 *
 *  只有这两个，因为**只有这两处「上面写着什么」不同**：网格小字上面印着格子名，省得掉；
 *  其余每一处上面要么没有标题、要么写的是「额外动作」这种不指名的四个字，省掉父名就读不懂
 *  （`WIFI - 打开` 不知道是哪一格的）。所以「额外动作」那一行走 [forSentence]。
 *
 *  「应用控制」那一格的小字不在这两个出口里（只要应用名，见 `ui/model/TileSubtitle`）——
 *  那一族的三段材料里，「名字」是动词、「状态」是目标，直接取哪一段都不对，所以由调用点
 *  自己拿 `appLabel`。这条豁免写在 `TileSubtitle` 那边，这里不开口子。
 *
 * ## 为什么句子是**每次调用现拼**
 *
 *  字典是运行时切的（`I18n.currentLanguage`）。构造期拼会把文案冻在当时那门语言上，
 *  切语言后整页还是旧文。这几个对象都无状态，缓存它们即可，不需要缓存句子。
 *
 * ## 为什么 `appName` 与 `fallback` 是**方法参数**
 *
 *  它们取决于调用方：宿主进程现查 `PackageManager`，界面进程先查这次清单再查配置里记的名字；
 *  `fallback` 更是一边用配置里的原始 token、一边用规范化后的 token。放进对象就等于把
 *  某个调用方的口径焊死在复用实例里。
 *
 * @param segmentsOf 由 [commandTitleOf] 选的「怎么取三段」，见文件末尾那四个常量
 * @param plain [of] 造出来的实例带的那份固定材料（其余实例为 null）
 */
class CommandTitle internal constructor(
    private val segmentsOf: (Action, (Action) -> String, String) -> TitleSegments,
    private val plain: TitleSegments? = null,
) {
    companion object {
        /**
         * **材料已经齐了**的那个入口 —— 手上没有 [Action] 的场合（「选择应用」页顶栏那种，
         * 它只有动词与目标）。
         *
         *  存在的理由：少了它，那几处就只好自己 `I18n.format` 一遍（原先 `appControlTitle`
         *  就是这么旁路的）—— 而旁路一旦留下，下一个人就会照着抄。
         */
        fun of(
            parent: String?,
            name: String,
            state: String?,
        ): CommandTitle {
            val seg = TitleSegments(parent = parent, name = name, state = state)
            return CommandTitle({ _, _, _ -> seg }, plain = seg)
        }
    }

    /**
     * 完整一句 —— **只管把这句话念完整**，不管「这一刻该不该念」。
     *
     *  「无操作」在这里就是「无操作」四个字。别处要「不弹提示」，那是**那个位置**的规矩，
     *  由那一处自己判（`GestureExec.noticeIfNeeded` 第一行就是）。
     *
     *  为什么这条界线要划在这里：位置语义漏进工厂的代价是**同一个动作在两个位置念出两种结果**
     *  （提示位置给空串、步骤行给名字），而每个调用点都得再猜一次「我这儿算不算提示位置」。
     *  工厂一旦对某个动作返回空串，「空串」就成了四种形态之外的第五种，`forSentence` 这个名字
     *  也就不实了。
     */
    fun forSentence(
        action: Action,
        appName: (Action) -> String,
        fallback: String,
    ): String = join(segmentsOf(action, appName, fallback), withParent = true)

    /**
     * 网格 / 抽屉里那一格的小字。
     *
     *  与 [forSentence] 只差一档：**「格子本身即动作」那几格（录屏 / 录音）
     *  只写状态**（`切换`），不写名字 —— 那一格的标题就是那个动作，小字再写
     *  `录屏 - 切换` 等于把标题抄一遍。其余档位省掉的是**父名**（`WIFI - 打开` 在「系统开关」
     *  那一格里读得通，`展开` 在「前台转小窗」那一格里同理）。
     */
    fun forTile(
        action: Action,
        appName: (Action) -> String,
        fallback: String,
    ): String {
        val seg = segmentsOf(action, appName, fallback)
        return if (seg.nameIsTheTile) seg.state.orEmpty() else join(seg, withParent = false)
    }

    /**
     * [of] 造出来的那一份材料，直接拼成完整一句。
     *
     *  只对 `of(...)` 造的实例有效 —— 其余实例没有「固定材料」，调它是编程错误。
     */
    internal fun sentence(): String {
        val seg = requireNotNull(plain) { "sentence() 只给 CommandTitle.of(...) 造的实例用" }
        return join(seg, withParent = true)
    }

    /**
     * 拼装 —— **唯一实现**。
     *
     *  两段之间的关系只有一种：横杠（`%s - %s`，字典给）；父与子之间才用冒号（`%s: %s`）。
     *  前者由 [link] 逐段折叠，后者在这里套一层 —— 全文件没有第二处拼分隔符的地方。
     */
    private fun join(
        seg: TitleSegments,
        withParent: Boolean,
    ): String {
        val body = link(listOfNotNull(seg.name.takeIf { it.isNotEmpty() }, seg.state))
        val parent = seg.parent.takeIf { withParent } ?: return body
        return I18n.format(Strings.COMMON_PREFIX_NAME_FORMAT, parent, body)
    }
}

/** 一串名字逐段用横杠连起来；只有一段时就是它自己。空段在这里被滤掉。 */
private fun link(names: List<String>): String =
    names.reduceOrNull { acc, part -> I18n.format(Strings.COMMON_NAME_STATE_FORMAT, acc, part) }.orEmpty()

/**
 * 内置动作的三段 —— 从清单里那一格取。
 *
 *  **一格的两种读法**（判据 [ActionCatalog.stateChoices]）：
 *
 *   · 这一格**不是**那个动作（系统开关 / 系统导航 / 灵感语音 / 前台转小窗）：格子名是**父**，
 *     往下那一层是**名字**，动作自带的那一段是**状态**
 *     → `系统开关: WIFI - 打开`、`系统导航: 主屏幕`、`前台转小窗: 展开`；
 *   · 这一格**就是**那个动作（录屏 / 录音）：格子名是**名字**、没有父，往下那一层是**状态**
 *     → `录屏 - 切换`（网格小字只写 `切换`，见 [forTile]）。
 *
 *  第二支的格子名与那一档的名字都从字典来：名字那一段是**动词**（`catalog.screen_record_toggle`
 *  = 「切换」），不是整句「切换录屏」—— 整句只出现在选择器里（由 `ui/ModeMenuPopups` 拼
 *  「切换录屏」）。同一个动作两处各写一份，改字典时必然只改到一处。
 *
 *  不在任何入口格里（顶层格 `截屏` / 自定义命令）：只有名字、没有父也没有状态；
 *  连清单都认不出（手改过的配置）才退到调用方给的 [fallback]。
 */
private fun builtInSegments(
    action: Action,
    fallback: String,
): TitleSegments {
    //  自定义命令：名字是**用户起的那个**（`action.name`），不是清单里的名字。
    //  它不在清单里（命令库是运行时收集的），查 `label` 只会拿到 null 再退到原始 token
    //  （`cmd:喝水`）—— 那是手改配置才该看到的样子。
    //
    //  例外是模块内置的那几条（`CommonCommands`：`cmd:common.wechat_scan`）—— 它们的 `name`
    //  是**稳定 id**、不是给人看的名字，念的时候要按当前语言过一遍字典。
    if (action is Action.Custom) {
        return TitleSegments(parent = null, name = CommonCommands.labelOf(action.name) ?: action.name, state = null)
    }
    //  两个「没有」意思不同：没有那一档 = 它不属于任何入口格；清单标题也没有才是「谁也认不出」。
    //  只有后者可以退到 fallback，所以不能写成 `?: ...` 串下去。
    val hit =
        ActionCatalog.modeHitOf(action)
            ?: return TitleSegments(parent = null, name = ActionCatalog.label(action) ?: fallback, state = null)
    val rest = hit.path.drop(1).map { it.label }
    return if (ActionCatalog.stateChoices(hit.tile.token) != null) {
        //  这一格本身就是那个动作：格子名当名字，往下那一层那档当状态（`录屏 - 切换`）。
        //  名字那一段是**动词**（字典里只有「切换」），整句「切换录屏」只由选择器那一层拼。
        TitleSegments(
            parent = null,
            name = hit.tile.label,
            state = rest.let(::link).takeIf { it.isNotEmpty() } ?: stateLabel(action),
            nameIsTheTile = true,
        )
    } else {
        TitleSegments(
            parent = hit.tile.label,
            name = rest.let(::link),
            state = stateLabel(action),
        )
    }
}

/**
 * 这一档的**状态**那一词 —— 开关是「打开 / 关闭 / 切换」，亮度 / 音量是那个数（`10%`）。
 *
 *  其余档位返回 `null`：它们的状态已经写在名字里（`静音` / `主屏幕` / `循环`），再补一个
 *  状态词就成了「静音 - 静音」。
 */
private fun stateLabel(action: Action): String? = ActionCatalog.switchStateLabel(action) ?: ActionCatalog.percentStateLabel(action)

/**
 * 「应用控制」那一族 —— 父名是**动词所在的格子**，名字是动词，状态是目标。
 *
 *  `应用控制: 打开应用 - 微信(小窗 扫一扫)`
 *
 *  动词只有两个：打开应用（含小窗、快捷方式三档）/ 结束应用。目标由调用方拼好
 *  （应用名 + 括号注记，见 [appTargetLabel]）—— token 里没有名字，这一层查不到。
 */
private fun targetSegments(
    verbKey: String,
    verbFallback: String,
): (Action, (Action) -> String, String) -> TitleSegments =
    { action, appName, _ ->
        TitleSegments(
            parent = I18n.get(Strings.TOOL_APP_CONTROL, "应用控制"),
            name = I18n.get(verbKey, verbFallback),
            state = appName(action).takeIf { it.isNotBlank() },
        )
    }

/**
 * 自定义变量 —— `自定义变量: 「计数」+= 1`。
 *
 *  表达式那一段（`+= 1`）是**名字的一部分**，不是状态：它是配置里写的原文，
 *  里面可能还写着变量占位符，展开是执行时的事。
 */
private val variableSegments: (Action, (Action) -> String, String) -> TitleSegments =
    { action, _, _ ->
        TitleSegments(
            parent = I18n.get(Strings.TOOL_VARIABLES, "自定义变量"),
            name = if (action is Action.VarSet) varSetTitle(action) else "",
            state = null,
        )
    }

/** 内置动作那一条 —— 其余动作都走它。 */
private val builtInTitle =
    //  写成具名参数：`plain` 是最后一个参数（有默认值），尾随 lambda 会被绑到它身上
    CommandTitle(segmentsOf = { action, _, fallback -> builtInSegments(action, fallback) })

/** `打开应用` 那一族（app: / window: / appshortcut:）。 */
private val openAppTitle =
    CommandTitle(targetSegments(Strings.TOOL_OPEN_APP, "打开应用"))

/** `结束应用`（`proc:stop:`）。 */
private val stopAppTitle =
    CommandTitle(targetSegments(Strings.TOOL_STOP_APP, "结束应用"))

/** 自定义变量。 */
private val variableTitle = CommandTitle(variableSegments)

/**
 * 动作 → 文案对象。
 *
 *  **`Action` 是 sealed，所以这里逐个列全、不写 `else`**：新增一个动作子类时，
 *  `when` 不完备会当场编译失败、点名那一支 —— 那正是工厂该有的强度。
 *  写成 `else -> builtInTitle` 的话，「新增一族忘了登记」会静默降级成内置文案，不报错。
 */
fun commandTitleOf(action: Action): CommandTitle =
    when (action) {
        is Action.App, is Action.AppWindow, is Action.AppShortcut -> openAppTitle

        is Action.Process -> stopAppTitle

        is Action.VarSet -> variableTitle

        is Action.None,
        is Action.Follow,
        is Action.KeyEvent,
        is Action.Shell,
        is Action.Custom,
        is Action.Intent,
        is Action.Notify,
        is Action.Fluid,
        is Action.MiniWindow,
        is Action.Shortcut,
        is Action.Flashlight,
        is Action.Ringer,
        is Action.Brightness,
        is Action.Volume,
        is Action.RingerVolume,
        is Action.PowerMenu,
        is Action.Oplus,
        is Action.Panel,
        is Action.Setting,
        -> builtInTitle
    }

/**
 * 「子动作 - 状态」那**半句**（`增加 - 10%` / `WIFI - 打开` / `主屏幕`）；不属于任何入口格时返回 `null`。
 *
 *  网格格子的小字、百分比弹窗那一行，以及「选定数值之后的那一条」（`ActionCatalog.brightnessSpecOf`）
 *  都读它 —— 它们只有一格宽，写不下父名，而父名就写在格子 / 弹窗标题上。
 *
 *  **与 [CommandTitle.forTile] 的差别只有一点**：没有入口格时给 `null` 而不是名字。
 *  那几处调用点靠这个 `null` 判「这一档不属于任何一格」（不是「没有名字」）。
 */
fun actionDetail(action: Action): String? =
    ActionCatalog.modeHitOf(action)?.let { commandTitleOf(action).forTile(action, { "" }, action.token) }
