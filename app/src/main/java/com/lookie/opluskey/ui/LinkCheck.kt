package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.wire.ConfigBroadcast
import com.lookie.opluskey.ui.model.API_MISMATCH_HINT
import com.lookie.opluskey.ui.model.CHECKING_HINT
import com.lookie.opluskey.ui.model.CONFIG_NOT_SYNCED_HINT
import com.lookie.opluskey.ui.model.CONFIG_READ_FAILED_HINT
import com.lookie.opluskey.ui.model.INJECT_NOT_LOADED_HINT
import com.lookie.opluskey.ui.model.KEY_DISABLED_HINT
import com.lookie.opluskey.ui.model.LinkNotice
import com.lookie.opluskey.ui.model.PENDING_OR_SCOPE_HINT
import com.lookie.opluskey.ui.model.SERVICE_NOT_RUNNING_HINT
import com.lookie.opluskey.ui.state.InjectPendingKind
import com.lookie.opluskey.ui.state.InjectStatus
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.state.LinkCheck

/**
 * 本地那份配置的指纹（与发送侧 `ConfigBroadcast.tag` 同口径）。
 *
 *  缓存 key 是**配置对象本身**（`Config` 是 data class，每次写盘都换新实例）⇒ 配置一变就重算。
 *  需要缓存是因为 `ConfigBroadcast.tag` 内部要读一次命令库（算「长按绑的是不是串行命令」），
 *  而总闸在**组合期与点按回调里都会被读到** —— 不能每次都去读盘。
 *  也因此这里是普通 `val`，不是 `@Composable`（`BindingScreen.pick()` 那种事件回调里也要读）。
 */
private var localTagCache: Triple<Config, List<Command>, String>? = null

internal val SettingsPage.localConfigTag: String
    get() {
        val cur = configState.config.value
        // 缓存键必须**同时**带上命令库：`ConfigBroadcast.tag` 里有一项是「长按绑的是不是串行命令」，
        //  它读的是命令库；而保存命令库（`saveCommands`）只换 `commands` 那一份状态、**不换 Config 实例**
        //  —— 只按 Config 失效的话，页面会一直拿着「按旧命令库算出来的」指纹，
        //  与注入侧刚收到的那份对不上，自检就假报「未同步」。
        //  用 `===` 比引用即可：`saveCommands` 每次都换一个新的 list 实例。
        val cmds = configState.commands.value
        localTagCache?.let { if (it.first === cur && it.second === cmds) return it.third }
        val tag = ConfigBroadcast.tag(cur) { CommandLibrary.longIsSerial(cur, it) }
        localTagCache = Triple(cur, cmds, tag)
        return tag
    }

/**
 * 「设置生效」那一段里**配置侧**那半：配置读得出来、注入侧收到过、且它手上就是**这一份**。
 *
 *  抽出来是因为它同时供两处用：「设置生效」那一行的 `ok`，以及整页总闸（[linkReady]）——
 *  两处必须同源，否则又会出现「运行状态报『未同步』、页面却照样能改」。
 *
 * 「主开关已关闭」**不算在这一半里**：那是用户的意图、不是异常；而且把它算进总闸会让主开关
 * 锁死自己（关掉 ⇒ 总闸关 ⇒ 它自己变灰 ⇒ 再也开不回来，见 `HomeScreen` 里主开关那一行）。
 */
internal val SettingsPage.configSideOk: Boolean
    get() {
        if (configState.readOnly) return false
        val inj = link.injectStatus ?: return false
        if (!inj.configOk) return false
        val tag = localConfigTag
        if (inj.configTag == tag) return true
        // 这一份是我们自己刚写下去、注入侧还没回包的 ⇒ 不判「没送到」（见 `configPending`）。
        //  少了这一档，每次拨开关都会有一两帧被判成未同步 —— 整页控件打灰再亮回来，就是全页闪。
        return link.configPending(tag)
    }

/**
 * **链路现在到底什么状态** —— 一次把这几个信号读全，再由两处渲染各自取用。
 *
 *  为什么要有这个：`blockedReason()`（首页那条常驻提示 + 「点不动」时的说明）与
 *  `linkChecks()`（「运行状态」里逐段结论）读的本来是**同一组信号**，但各自写了一套并列的
 *  `when`。两套的条件项个数与顺序都得靠人对齐 —— 历史上已经因此分叉过一次（横幅说「需要重启」、
 *  逐段行说「未同步」，因为一边多一个 `isNotEmpty()`）。现在两边只认这一个对象。
 *
 *  字段全是「已经从原始状态算好」的结论，没有副作用，也不持有 Compose 状态。
 */
@Suppress("LongParameterList")
internal class LinkFacts(
    /** 建页那次配置没读成功（整页只读）。 */
    val readOnly: Boolean,
    /** 模块总闸关着。 */
    val masterOff: Boolean,
    /** 宿主侧有没有在跑（报活）。 */
    val hostUp: Boolean,
    /** 注入侧回包；`null` = 问过但没回音。 */
    val inject: InjectStatus?,
    /** 「设置生效」的配置侧那半：读得出来、送到过、且送的就是这一份。 */
    val configSideOk: Boolean,
    /** 注入侧那趟自检**跑完了没有** —— 没跑完时 `inject` 的 `null` 只是初值，不是结论。 */
    val injectChecked: Boolean,
    /** 盘上那份包的更新时间（0 = 读不到，这一档不判）。 */
    val installedAt: Long,
    /** 宿主进程的启动时刻（0 = 对面没带这一项）。 */
    val hostProcStart: Long,
    /** 系统那一侧曾经回过话没有，以及那次是不是本轮开机（见 [InjectPendingKind]）。 */
    val injectEverAlive: Boolean,
    val injectAliveBoot: Long,
    val bootNow: Long,
) {
    /** 注入侧回了包、并且接管点挂上了。 */
    val respondOk: Boolean get() = inject != null && inject.hooks > 0

    /**
     * 回了包、但报的不是本包的构建标识 ⇒ system_server 里还是上一版代码（换包没重启）。
     *
     *  这是**备用尺子**：只在对面没带进程启动时刻时用（旧版注入侧）。主尺子是 [injectStale] ——
     *  它比「包更新时间 vs 进程启动时刻」，不依赖任何人记得去改 `BUILD_ID`。
     */
    val buildStale: Boolean
        get() = inject != null && inject.buildId.isNotEmpty() && inject.buildId != ModuleInfo.BUILD_ID

    /**
     * 注入侧**确实**跑着旧包：它那个进程比盘上这个包起得还早。
     *
     *  为什么这个判据成立：进程里的类只在进程启动那一刻从 APK 加载，之后盘上的包怎么换都不影响它。
     *  留 [STALE_EPSILON_MS] 的余量是给「开机早期包更新时间与进程启动时刻贴得很近」那种情形 ——
     *  换包没重启的间隔至少是分钟级，留几秒不会把正常情况判成过期。
     */
    val injectStale: Boolean
        get() {
            val start = inject?.procStart ?: 0L
            return installedAt > 0 && start > 0 && installedAt > start + STALE_EPSILON_MS
        }

    /**
     * 宿主侧跑着旧包（同上，主尺子是进程启动时刻）。
     *
     *  与注入侧的区别：宿主是普通应用进程，**重开就好**，不用重启手机（`AGENTS.md` 五之一、6）。
     */
    val hostStale: Boolean
        get() = installedAt > 0 && hostProcStart > 0 && installedAt > hostProcStart + STALE_EPSILON_MS

    /** 要不要重启手机：注入侧跑旧包 ⇒ 要。对面没带启动时刻时退回构建标识那把备用尺子。 */
    val restartNeeded: Boolean
        get() = injectStale || (inject != null && inject.procStart == 0L && buildStale)

    /** 「没回包」到底是哪一档 —— 见 [InjectPendingKind] 的注释。 */
    val injectPending: InjectPendingKind
        get() =
            when {
                inject != null -> {
                    InjectPendingKind.NONE
                }

                !injectChecked -> {
                    InjectPendingKind.NONE
                }

                !injectEverAlive -> {
                    InjectPendingKind.FIRST
                }

                injectAliveBoot > 0 && bootNow > 0 &&
                    kotlin.math.abs(injectAliveBoot - bootNow) <= BOOT_MATCH_EPSILON_MS -> {
                    InjectPendingKind.LOST
                }

                //  **这一条是「等重启」与「作用域被撤」之间唯一的分界**：包是这轮开机之后装的
                //  ⇒ 系统侧确实还没来得及用上它（等重启）；包在开机之前就装好了 ⇒ 本轮早就该用上它了，
                //  现在没回话只能是作用域里没有它。少了这一条，撤掉「系统框架」之后会一直报「重启后生效」，
                //  而重启多少次都没用（实测撞到）。
                installedAt > 0 && bootNow > 0 && installedAt > bootNow + REBOOT_EPSILON_MS -> {
                    InjectPendingKind.UPDATED
                }

                else -> {
                    InjectPendingKind.SCOPE_GONE
                }
            }

    /** **整页总闸**：「运行状态」里任何一段不 ok 就关（口径见 [SettingsPage.linkReady]）。 */
    val ready: Boolean get() = hostUp && respondOk && configSideOk
}

/**
 * 「进程启动时刻 vs 包更新时间」这个比大小时留的余量。
 *
 *  取 5 秒：两次时间都来自系统时钟，正常情形（换包之后隔几分钟才开机 / 才开页）差得很远；
 *  只有「包刚更新完、进程几乎同时起来」这种边界才可能落到几秒内，那时**不该**判过期 ——
 *  冤枉一次就要人多重启一次手机。
 */
private const val STALE_EPSILON_MS = 5_000L

/**
 * 判「包是不是这次开机之后装的」时留的余量。
 *
 *  取 60 秒：两个人（装包 / 重启）先后相差通常以分钟计，但开机那一刻系统在忙，
 *  `lastUpdateTime` 与开机时刻贴得很近是完全可能的；贴着时不判成「等重启」更安全 ——
 *  宁可让人去 LSPosed 里看一眼作用域，也别让他为一个早就装好的包反复重启。
 */
private const val REBOOT_EPSILON_MS = 60_000L

/**
 * 判「两次报活是不是同一轮开机」时留的余量。
 *
 *  那个指纹（`ProcClock.bootWall`）**截到整秒**，而两个进程取值时刻不同 —— 一次落在 x.999、
 *  另一次落在 x+1.001，两边就整整差 1000ms。直接比相等会把「本轮开过、现在掉线」误报成
 *  「作用域里没有它」，指去 LSPosed 里白翻一遍。
 *  取 5 秒：跨一轮重启的间隔以分钟计，这个余量吃得下取整误差，吃不掉真重启。
 */
private const val BOOT_MATCH_EPSILON_MS = 5_000L

/**
 * 读一遍上面那些信号 —— **全项目只有这一处**把它们凑成 [LinkFacts]。
 *
 *  `configSideOk` 自己已经封了只读态与「在途」宽限（见那里的注释），所以这里不再重复判。
 */
internal val SettingsPage.linkFacts: LinkFacts
    get() =
        LinkFacts(
            readOnly = configState.readOnly,
            masterOff = !configState.config.value.global.enabled,
            hostUp = link.hostServiceUp,
            inject = link.injectStatus,
            configSideOk = configSideOk,
            injectChecked = link.injectChecked,
            installedAt = link.installedAt,
            hostProcStart = link.hostProcStart,
            injectEverAlive = link.injectEverAlive,
            injectAliveBoot = link.injectAliveBoot,
            bootNow = link.bootWall,
        )

/**
 * **整页总闸**：「运行状态」里**任何一段**不 ok 就关。
 *
 * 用户定的口径：总闸不再只看「服务运行 + 按键响应」那两段 —— 「设置生效」同样是在答
 *  「改了会不会生效」，它不通时放用户去改就是误导。主开关与 ⋮ 菜单里那几项都吃这个闸。
 *
 *  两处**有理由的**例外，不是漏掉：
 *   · 「主开关已关闭」不算异常：它是用户的意图状态，且算进去会锁死主开关自己（见 [configSideOk]）；
 *   · 「使用验证」不参与（`blocking = false`）：还没被验证过是信息、不是故障。
 *
 *  判据与盘面上的组合一一对应（见 [linkChecks]），两侧读的是同一个 [LinkFacts]，不许各写一份。
 */
internal val SettingsPage.linkReady: Boolean
    get() = linkFacts.ready

/**
 * 「现在能不能用、不能是因为什么」—— **判据只此一处**：首页那条常驻提示与「选动作被拦住」的 toast
 * 都读它（原先两处各写一份 `when`，加一档要改两个文件，迟早分叉）。
 *
 * **只收「页面此刻不能用」的那几档**（页面被闸住 / 只读 / 模块整条没生效）——首页把它当**常驻**
 * 提示挂着，因为它解释的是「控件为什么是灰的」。**「能用、只是该提醒一句」的那两档不在这里**，
 * 见 [noticeReason]：那种话要一次性说掉（用户口径：除了模块无法正常运行，别的不要常驻）。
 *
 * 按**用户能做的事**分档，顺序就是优先级 —— 越靠前越「说清了就不必往下猜」：
 * 1) 配置没读成功 ⇒ 重进页面就会重读；
 * 2) 宿主服务没报活 ⇒ 重进页面 / 重启；
 * 3) 自检还没问完 ⇒ 一句中性的「正在检查」，不是故障（问完自己就换掉，不会一直挂着）；
 * 4) 系统那一侧**问过一轮都没回音** ⇒ 按 [InjectPendingKind] 再说一层：
 *    从没起来过（勾作用域 + 重启）/ 装好等重启（只重启）/ 本轮掉线（看日志）；
 *    「还没问完」不是这一档 —— 见 `LinkFacts.injectChecked`；
 * 5) 回音了但接管点 0 个 ⇒ 这一版 ROM 把私有类改了形，只能等适配；
 * 6) 设置没送到 ⇒ 重进本页会重发一遍；
 * 7) **模块总闸关着** ⇒ 用户自己拨回来。
 *
 *  **主开关排在最后一条，不是漏了**：那一行自己吃 `linkReady`（见 `HomeCards`），链路没齐时
 *  它是**灰色、拨不动**的。把它摆在链路前面，等于让人去开一个开不了的开关 —— 真机反馈就是
 *  「首次安装没重启、主开关又关着时，提示条只说『按键绑定已关闭』」，而该做的是重启 / 勾作用域。
 *  链路通了它才是真的「只差用户这一下」（此时那一行也可点）。
 *
 *  **机型清单不在这张表里**：它只是「这一组验过没有」的提示，不拦注入、也不拦页面
 *（AGENTS.md 一、13）—— 所以既没有「机型不在清单」这一档，也没有对应的禁用。
 *
 *  注意：**「模块没启用」不在这张表里，也不需要判** —— 这段文字只可能在「我们的页面被画出来」
 *  时出现，而页面本身就是模块的代码（跑在宿主 `:ui` 进程里）：能画出来就意味着模块已加载、
 *  作用域至少命中宿主包。真没启用时用户看到的是宿主原版页面，这里一个字都不会出现。
 *
 * @return `null` = 可以用
 */
internal fun SettingsPage.blockedReason(): String? {
    val f = linkFacts
    return when {
        f.readOnly -> {
            CONFIG_READ_FAILED_HINT
        }

        !f.hostUp -> {
            SERVICE_NOT_RUNNING_HINT
        }

        // 自检还没跑完：注入侧那几档此刻**没有判据**（`inject` 的空只是初值）。
        //  不能说「未生效」（重启后那几秒会报假故障），但也不能什么都不说 ——
        //  那几秒页面上没有任何解释，看着像「点了没反应」。中性一句「正在检查」。
        !f.injectChecked -> {
            CHECKING_HINT
        }

        // 没回包 —— 四条来路，要做的事不同（见 `InjectPendingKind`）：
        //  · 从没起来过：可能作用域漏了 system，也可能勾了但从没重启过，两句一起说（只此一档分不开）；
        //  · 以前起来过、本轮没来、且包是这次开机之后装的：确实在等重启，别把人赶去 LSPosed；
        //  · 以前起来过、本轮没来、但包开机前就装好了：**作用域被撤了** —— 这一档必须说去勾，
        //    否则「重启后生效」会一直挂着，重启多少次都不变；
        //  · 本轮刚才还在、现在不回了：掉线，说成「没生效」就行，往下看日志。
        f.inject == null -> {
            when (f.injectPending) {
                // 「装好等重启」与「作用域被撤」分不开（见 `PENDING_OR_SCOPE_HINT`）：一句话覆盖两步
                InjectPendingKind.UPDATED -> PENDING_OR_SCOPE_HINT

                else -> INJECT_NOT_LOADED_HINT
            }
        }

        // 回了包，但**一个接管点都没挂上**：这一版 ROM 把私有类改了形，只能等适配。
        //  （「回了包但跑的是旧包」那两档不在这里 —— 页面照样能改，它们只是提醒，见 [noticeReason]。）
        !f.respondOk -> {
            API_MISMATCH_HINT
        }

        !f.configSideOk -> {
            CONFIG_NOT_SYNCED_HINT
        }

        // **模块总闸**，不是「侧键那颗键」—— 后者只是那一颗（见 `disabledClickReason`）。
        //  排在最后一条：链路没齐时主开关那一行是灰的（`enabled = linkReady …`，见 `HomeCards`），
        //  先说它就是让人去开一个开不了的开关（顺序的理由见上面那段 KDoc）。
        f.masterOff -> {
            KEY_DISABLED_HINT
        }

        else -> {
            null
        }
    }
}

/**
 * 「能用，但有件事该提一句」—— 目前两档，都是**跑着旧包**（系统侧 / 宿主侧）。
 *
 *  与 [blockedReason] 分工不同，判据也不同：
 *   · [blockedReason] 答的是「页面为什么不能用」⇒ 首页那条**常驻**提示（控件灰着得有人解释）；
 *   · 这一支答的是「能用，只是这次没落到新代码上」⇒ 一次性说掉、几秒自己走（用户口径：
 *     「除了模块无法正常运行时的提示，其他的不要常驻，比如系统侧还是旧版这种，提示一下就好」）。
 *  两档都能照常改设置，所以**不能**进 [linkReady] / 整页闸 —— 那是另一次判断（见那里）。
 */
internal fun SettingsPage.noticeReason(): LinkNotice? {
    val f = linkFacts
    // **自检没跑完就不下结论**：这一趟的数据还没齐（注入侧的回包可能刚到、也可能还没到），
    //  此时弹「系统侧还是旧版」会跟紧跟着的「正在检查模块…」互相打架 ——
    //  一句话说「它跑着旧包」、下一句又说「还在问」，用户只能理解为界面在乱猜。
    //  等这一趟问完（[LinkFacts.injectChecked]）再判，结论与数据同源。
    if (!f.injectChecked) return null
    return when {
        // 系统侧跑着旧包：换包没重启。**只有这一件事**要做（重启手机）。
        f.restartNeeded -> LinkNotice.SystemStale

        // 宿主侧跑着旧包：它是普通应用进程，重开就好，不必重启手机。
        f.hostStale -> LinkNotice.HostStale

        else -> null
    }
}

/**
 * 「这一下点不动」时该说什么 —— 整页总闸优先，其次是**当前编辑的那颗键自己停用了**。
 *
 *  为什么不能沿用 `blockedReason() ?: "页面还没就绪"`：页面上被置灰的控件分两种 ——
 *  整页被闸住（[blockedReason] 有话说），或者只是**这一颗键被停用**（总闸是通的）。
 *  第二种落到「页面还没就绪」会把人引去重进页面，而真正要做的是到 ⋮ 菜单里把键启用回来。
 *
 *  两种键同一句话：停用**任何一颗**（含侧键）之后，打开它的入口都是同一个 ⋮ 菜单里那一行。
 */
internal fun SettingsPage.disabledClickReason(): String {
    blockedReason()?.let { return it }
    val cfg = configState.config.value
    val key = KeyWatchState.editing(cfg)
    if (!cfg.key(key).enabled) {
        return I18n.format(Strings.KEY_DISABLED_ITEM, key.label)
    }
    //  页面是通的、这一项仍然点不动（比如行要求先选一颗键）：说结论，不编一个「没就绪」
    return I18n.get(Strings.CONTROL_UNAVAILABLE, "这一项现在不可用")
}

/**
 * 把设置页手上那几路信号翻成**逐段的**链路结论，顺序就是数据要走的顺序：
 * 服务 → 按键 → 设置 → 实际使用。
 *
 * 为什么要有这个：只看「宿主报活了没有」一个布尔的话，答不了三种真实故障 ——
 * 按键那一侧压根没装上（作用域配错 / ROM 换了方法名）、设置没送到那一侧、以及最隐蔽的那种
 * 「装上了但这版 ROM 的侧键事件根本不走那条路」。前两种现在有判据了；
 * 第三种只能靠最后那段那笔「真的执行过没有」。
 *
 * 前一段断了，后面的就没意义（按键那侧没回话时不去猜它设置到没到）。
 *
 * 注意：**界面粒度是四段（+ 整体那一行），不是原始信号数**：用户要求
 * 「用户界面不需要技术细节」，于是「设置同步」与「设置读取」合并成「设置生效」——
 * 两件事都还在查（判据是一个 `&&`），只是不再分开报给用户。
 */
@Composable
internal fun SettingsPage.linkChecks(): List<LinkCheck> {
    // 四段结论全部来自这一份事实 —— 与首页那条常驻提示（`blockedReason`）**同一个对象**，
    //  两处不可能再各说一句话。
    val f = linkFacts
    val hostOk = f.hostUp
    val inj = f.inject
    val respondOk = f.respondOk
    // 「设置生效」的判据是**两边指纹相等**，不是「注入侧收到过一条」：
    //  后者答不了「后来改的那一次到了没有」—— 开机收到过之后哪条广播丢了，
    //  注入侧还按旧配置吞键，界面却照样报「正常」。指纹不同 = 明确知道它手上不是这份。
    val configSide = f.configSideOk
    // 换了包没重启时两边**必然**对不上，这时报「未同步」会把人引去「重进本页」，
    //  而真正该做的是重启 —— 与首页那条横幅（`blockedReason`）同一个优先级、同一句话。
    //  判据与横幅同源（`restartNeeded`：时间戳为主、构建标识为备），不许各写一份。
    val restartNeeded = f.restartNeeded

    return listOf(
        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_SERVICE_STATUS, "服务运行"),
            ok = hostOk,
            detail =
                if (hostOk) {
                    I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
                } else {
                    I18n.get(Strings.LINK_CHECK_NOT_RUNNING, "未运行")
                },
        ),
        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_KEY_RESPONSE, "按键响应"),
            ok = respondOk,
            detail =
                when {
                    inj == null -> I18n.get(Strings.LINK_CHECK_WAITING_RESPONSE, "等待响应")
                    inj.hooks <= 0 -> I18n.get(Strings.LINK_CHECK_NOT_RESPONDING, "未响应")
                    else -> I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
                },
        ),
        // 「设置同步」（配置送到注入侧没有）与「设置读取」（宿主读到的不是默认值）
        // 合成一段：对用户来说都是「设置到底生效了没有」，分开报只是把内部结构摆到脸上。
        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_CONFIG_EFFECTIVE, "设置生效"),
            // 与总闸**同源**：配置侧那半直接用 [configSideOk]，免得两边各写一份慢慢跑偏
            ok = configSide && link.hostEnabled,
            // 「主开关已关闭」**不算故障**（见 [configSideOk] 与 [linkReady] 的注释）：
            //  只有「配置侧没问题、单纯是用户关了主开关」这一种不 ok 时不 block。
            //  其余（配置读不懂 / 没送到注入侧 / 两边指纹对不上）都真会让设置不生效 —— 照旧 block。
            //  一律 block（`blocking` 走默认值）的话，用户主动关掉主开关，
            //  整体状态就报「设置生效异常」—— 那是用户的意图，不是故障。
            blocking = !(configSide && !link.hostEnabled),
            detail =
                when {
                    // 真因优先：文件读不懂时宿主读到的是默认值（enabled=false），
                    // 再往下会报成「主开关已关闭」—— 那会把用户引到错的方向（他根本没关）。
                    f.readOnly -> I18n.get(Strings.LINK_CHECK_CONFIG_FAILED, "配置读取失败")

                    inj == null -> I18n.get(Strings.LINK_CHECK_WAITING_RESPONSE, "等待响应")

                    !inj.configOk -> I18n.get(Strings.LINK_CHECK_NOT_EFFECTIVE, "未生效")

                    restartNeeded -> I18n.get(Strings.LINK_CHECK_RESTART_REQUIRED, "需要重启")

                    // 收到过、但指纹对不上（且不是换包没重启造成的）：改过的那一次没送到它手上
                    //  判据与上面 `ok` **同源**（[configSideOk]）—— 这里原来另算了一遍，两处条件
                    //  还差一个 `isNotEmpty()`，于是「横幅说重启、这一行说未同步」。
                    !configSide -> I18n.get(Strings.LINK_CHECK_NOT_SYNCED, "未同步")

                    // 读到默认值只有两种来路：主开关被关了，或者开机还没解锁
                    !link.hostEnabled -> I18n.get(Strings.LINK_CHECK_SWITCH_OFF, "主开关已关闭")

                    else -> I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
                },
        ),
        // 这一段永远不 block：还没被验证过不是故障，只是一条还没拿到的证据。
        //  文案里**不出现「按过没按过」**——那是内部证据的说法（用户点名删的）。
        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_USAGE_VERIFIED, "使用验证"),
            ok = link.hostLastActionAt > 0,
            // 判到过手势、却一次都没执行 —— 多半是这一档绑的是「无操作」
            detail =
                when {
                    link.hostLastActionAt > 0 -> I18n.get(Strings.LINK_CHECK_VERIFIED, "已生效")
                    inj != null && inj.lastGestureAt > 0 -> I18n.get(Strings.LINK_CHECK_NOT_EFFECTIVE, "未生效")
                    else -> I18n.get(Strings.LINK_CHECK_NOT_USED_YET, "尚未使用")
                },
            blocking = false,
        ),
    )
}
