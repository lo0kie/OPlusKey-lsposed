package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.DEBUG_PROBE
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
import com.lookie.opluskey.ui.model.HOST_STALE_HINT
import com.lookie.opluskey.ui.model.INJECT_NOT_LOADED_HINT
import com.lookie.opluskey.ui.model.KEY_DISABLED_HINT
import com.lookie.opluskey.ui.model.LinkNotice
import com.lookie.opluskey.ui.model.PENDING_OR_SCOPE_HINT
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.SERVICE_NOT_RUNNING_HINT
import com.lookie.opluskey.ui.model.SYSTEM_STALE_HINT
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
 *  也因此这里是普通 `val`，不是 `@Composable`（`pick()` 那种事件回调里也要读）。
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
 * 在**建页那趟 IO** 里先把指纹算好 —— 组合期读 [localConfigTag] 时就只命中缓存。
 *
 *  `ConfigBroadcast.tag` 内部要读一次命令库（`longIsSerial`），那是磁盘 IO；
 *  而组合期读它是硬红线。所以「知道这两份状态刚换过」的地方（建页、写盘、存命令库）
 *  都应该在后台线程上先调这个函数 —— 读的那一侧不必知道有没有缓存。
 */
internal fun primeLocalTag(
    config: Config,
    commands: List<Command>,
) {
    val tag = ConfigBroadcast.tag(config) { CommandLibrary.longIsSerial(config, it) }
    localTagCache = Triple(config, commands, tag)
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
    /**
     * **执行侧**（宿主主进程）读得懂那份配置文件。
     *
     *  [configSideOk] 答的是「广播到没到 system_server」，这一位答的是「真执行动作的那个进程
     *  把文件解出来了没有」—— 两件事，两条路（广播 / 文件），必须分开问。
     *  `false` 时它读到的是 `Config.DEFAULT`，与「用户关了主开关」在 `hostEnabled` 上同形。
     */
    val hostConfigReadable: Boolean,
    /** 宿主报活里带回来的「它读到的主开关」。只用来与本地这份对账（见 [hostConfigBroken] 第 2 条）。 */
    val hostEnabled: Boolean,
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

    /**
     * **执行侧那份配置用不了** —— 两条来路，必须合成一条判据：
     *
     *  1. 宿主自己报「读不懂」（[hostConfigReadable] = false，本版新增的那一位）；
     *  2. **旧宿主不带那一位**（缺省按 true 处理，免得误报），于是改用对账反推：
     *     它跑着旧包、报的主开关是关的，而本地这份主开关是开的、也不是用户关的
     *     ⇒ 它读到的不是这份文件，是 `Config.DEFAULT`（旧代码按旧结构校 md5，必然失败）。
     *
     *  第 2 条是**本次故障的现场**：装完包没重启，宿主还在跑换包前的代码 —— 那时它连
     *  `EXTRA_HOST_CONFIG_READABLE` 都不会发，只靠第 1 条永远判不出来，界面继续报「通过」。
     *
     *  不会误报的三种情形：旧宿主读得懂（报的 enabled 与本地一致）；用户自己关了主开关
     *  （`masterOff`）；首装还没写过配置（本地就是默认的关，`masterOff` 也为真）。
     */
    val hostConfigBroken: Boolean
        get() = !hostConfigReadable || (hostStale && !hostEnabled && !masterOff)

    /**
     * 执行侧读到的主开关是**关的**，而你这边开着 —— 「设置没生效」最直接的那一档。
     *
     *  与 [hostConfigBroken] 分工：[那个][hostConfigBroken] 管「宿主读不懂配置」（含旧宿主对账反推），
     *  这一位管「它读得懂，但读到的那份里主开关是关的」。两条来路：
     *
     *   · **开机还没解锁**：`opluskey_config.conf` 落在 credential-encrypted 存储里，解锁前读不到文件，
     *     宿主于是按 `Config.DEFAULT` 报上来（主开关关）。界面这半是解锁后才起来的，看到的却是
     *     「全部正常、按键无反应」；
     *   · 配置刚写完、宿主那次读取早于写盘 —— `configPending` 那道宽限只管**注入侧**，
     *     宿主这半没有对应的宽限。
     *
     *  为什么必须单独有位：这一档下 `hostUp` / `respondOk` / `configSideOk` 可能全绿，
     *  `hostConfigBroken` 也假（新宿主报了 `readable = true`），于是首页说「已激活」、
     *  整页可编辑，而实际一个动作都不执行。
     *
     *  不会误报：`masterOff` 为真时那是用户自己关的，不算异常。
     */
    val hostSwitchMissing: Boolean
        get() = !hostEnabled && !masterOff

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

    /**
     * **整页总闸**：「运行状态」里任何一段不 ok 就关（口径见 [SettingsPage.linkReady]）。
     *
     *  含 [hostConfigBroken]（执行侧那份配置用不了）：它一个动作都做不出来 —— 放用户去改设置
     *  就是**假可用**（改了也到不了那只手，而页面全绿）。
     *
     *  它进闸的前提是「重启服务」已经不吃这个闸（见 `HomeHeaderMenu` 里那一行的 `enabled = true`）：
     *  这一档的修法正是重启服务，闸住它就成了「提示让你重启服务、而重启服务是灰的」。
     *  以后要往这里再加一位，先确认对应的自救入口不在闸内。
     */
    val ready: Boolean get() = hostUp && respondOk && configSideOk && !hostConfigBroken && !hostSwitchMissing
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
 *
 *  调试通道把 [HostLinkState.debugAllBroken] 打开时，这里整份换成 [ALL_BROKEN_FACTS]：
 *  判据（`linkChecks` / `blockedReason` / `linkReady`）与渲染都不动，只是**换一份事实** ——
 *  所以「所有段都坏」时看到的就是真的那个样子（含整页只读、控件全灰）。
 */
internal val SettingsPage.linkFacts: LinkFacts
    get() {
        //  `DEBUG_PROBE &&` 不是多余的：它是**编译期常量**，release 里这个分支连比较都不存在
        //  （整套 `ALL_BROKEN_FACTS` 随之被裁掉）。少了它，这一段会在正式包里留着一个恒假判断。
        if (DEBUG_PROBE && link.debugAllBroken) return ALL_BROKEN_FACTS
        return LinkFacts(
            readOnly = configState.readOnly,
            masterOff = !configState.config.value.global.enabled,
            hostUp = link.hostServiceUp,
            inject = link.injectStatus,
            configSideOk = configSideOk,
            hostConfigReadable = link.hostConfigReadable,
            hostEnabled = link.hostEnabled,
            injectChecked = link.injectChecked,
            installedAt = link.installedAt,
            hostProcStart = link.hostProcStart,
            injectEverAlive = link.injectEverAlive,
            injectAliveBoot = link.injectAliveBoot,
            bootNow = link.bootWall,
        )
    }

/**
 * 「所有段都坏」那份事实（**只给调试通道用**，见 [HostLinkState.debugAllBroken]）。
 *
 *  每一格都取「坏」的那一档，且`injectChecked = true`（否则会停在「正在检查」那句中性的，
 *  看不到结论）；`installedAt` / `hostProcStart` 留 0 —— 那样不会判「跑着旧版」，
 *  首页那句就不会被「重启手机」顶掉，能老实说「哪一段异常」。
 */
private val ALL_BROKEN_FACTS =
    LinkFacts(
        readOnly = true,
        masterOff = false,
        hostUp = false,
        inject = null,
        configSideOk = false,
        hostConfigReadable = false,
        hostEnabled = false,
        injectChecked = true,
        installedAt = 0L,
        hostProcStart = 0L,
        injectEverAlive = false,
        injectAliveBoot = 0L,
        bootNow = 0L,
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
 * 6) 系统侧跑着旧包 ⇒ 重启手机（排在「设置没送到」前：旧包里指纹口径不同，落到下面会说「重进本页」，
 *    而重进修不好它）；
 * 7) **执行侧**读不懂配置 ⇒ 重启服务（跑着旧包时）/ 重进页面（文件真读不懂）——
 *    这一档原来与「主开关关着」同形，于是被当成用户的意图放过去；
 * 8) 执行侧**读得懂、但读到的那份里主开关是关的**（最常见：开机还没解锁）⇒ 等它自己重读 ——
 *    这一档原先 `hostUp` / `respondOk` / 配置指纹全绿，卡片说「已激活」而一个动作都不执行；
 * 9) 设置没送到 ⇒ 重进本页会重发一遍；
 * 10) **模块总闸关着** ⇒ 用户自己拨回来。
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
 * ## 不变式：`ready == false` ⟹ 这里一定给得出话
 *
 *  反过来不成立 —— 用户自己关主开关时 `ready` 可以是 true，而这里给「按键绑定已关闭」
 *  （主开关不进总闸，否则它锁死自己，见 [configSideOk] 的注释）。
 *
 *  这条单向关系是**加档时的检查表**：往 [LinkFacts.ready] 里加一项，就必须在这里加一档，
 *  否则出现「整页灰着、大卡片报异常、却说不清为什么」—— 用户只看到一句
 *  `MODULE_UNAVAILABLE_HINT`（「页面还没就绪，重进本页试试」），而重进根本不好使。
 *  `hostSwitchMissing` 那一档就是这么漏的。
 *
 * @return `null` = 可以用
 */
internal fun SettingsPage.blockedReason(): String? {
    val f = linkFacts
    return when {
        //  读不懂有两条来路，要用户做的事完全不同：
        //   · 文件真有问题（坏 / 手改 / 格式版本没抬）⇒ 重进页面会重读一次；
        //   · **宿主跑着旧版** ⇒ 它按旧结构校 md5，任何一份新文件都判读不懂 —— 重进页面永远好不了，
        //     要做的是「重启服务」。真机上撞过：页面说「配置读取失败，请重进页面」，
        //     而实际是装完包宿主没重启，照那句话做多少次都没用。
        f.readOnly -> {
            if (f.hostStale) HOST_STALE_HINT else CONFIG_READ_FAILED_HINT
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

        // 系统侧跑着旧包（换包没重启）：**界面已经是新版、执行侧还是旧的** —— 改什么都到不了
        //  那只手。这一档必须排在「设置未同步」前面：旧包里没有新的配置字段 / 指纹口径，
        //  两边指纹自然对不上，落到下面那条就会说「重进本页即可」—— 而重进**修不好**它
        //  （只有重启手机能）。同一页的逐段结论（`linkChecks`）早就报的是「需要重启」，
        //  两处说法不一致时用户会照着错的那条去试。
        f.restartNeeded -> {
            SYSTEM_STALE_HINT
        }

        // **执行侧**那份配置用不了：它一个动作都做不出来，而 system_server 那半照样吞键 ——
        //  症状就是「按了没反应」，且上面几段全是绿的。
        //  排在「设置未同步」前面：那是「送到没送到」，这是「收到了也用不了」，后者更靠前。
        //  跑着旧包时（换包没重启）它就是那件事的影子，对应的修法是「重启服务」，不是重启手机。
        //  判据用 [LinkFacts.hostConfigBroken]，不用 `hostConfigReadable`：旧宿主根本不发那一位，
        //  而**本次故障恰恰是旧宿主**（装完包没重启）—— 只认那一位就永远看不见它。
        f.hostConfigBroken -> {
            if (f.hostStale) HOST_STALE_HINT else CONFIG_READ_FAILED_HINT
        }

        //  宿主读得懂配置，但读到的那份里主开关是关的，而你这边开着（见 [LinkFacts.hostSwitchMissing]）：
        //  开机还没解锁是最常见的一条。这句话与「设置未同步」同一句 —— 对用户来说都是
        //  「另一侧手上不是这一份」，能做的也都是「等它同步 / 重进本页」。
        //  少了这一档：卡片说「已激活」、整页可编辑，而一个动作都不执行（真机现场）。
        f.hostSwitchMissing -> {
            CONFIG_NOT_SYNCED_HINT
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
    //  **配置是从备份恢复来的**：这一条与链路自检无关（`injectChecked` 还没跑完也照样成立），
    //  而且它解释的是「我配的那一项为什么不见了」—— 比「跑着旧包」更该先说。
    if (configState.configRecovered) return LinkNotice.ConfigRecovered
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
 * 「能用，但有件事该说一句」—— 首页状态卡下半段那一行**只收这一类**。
 *
 * ## 判据与职责
 *
 *  它答的是「**还能用**、只是这次没落到新代码上」（系统侧 / 宿主侧跑着旧包），所以两档都是
 *  「重启一下就好」，不拦任何控件。
 *
 *  真故障**不在这里**：那些话归状态卡大字下面那句（`ActivationState.detail(reason)` 取
 *  `blockedReason()`）——「页面为什么不能用」每一档都写着下一步做什么，
 *  而这里原先回的是 `LINK_CHECK_BROKEN_FORMAT` 拼出来的「设置生效异常」：
 *  只报段落名、不说该做什么，用户口径是「显示的是默认文案」。
 *  同一条原因在两处各说一遍（一份具体、一份模板）只会让人觉得界面在猜。
 *
 *  与 [blockedReason] / [noticeReason] 同一族、同一个判据源（[linkFacts]）——
 *  换一处展示不该把它搬成第二份。
 *
 * @return `null` = 没有要提的事
 */
@Composable
internal fun SettingsPage.statusTipText(): String? {
    val f = linkFacts
    // **自检没跑完就不下结论**：这一趟的数据还没齐（注入侧的回包可能刚到、也可能还没到），
    //  此时说「系统侧还是旧版」会跟同一块里那句「正在检查模块…」互相打架 ——
    //  一句话说「它跑着旧包」、另一句又说「还在问」，读起来就是界面在乱猜。
    //  等这一趟问完（[LinkFacts.injectChecked]）再判，结论与数据同源。
    if (!f.injectChecked) return null
    return when {
        f.restartNeeded -> SYSTEM_STALE_HINT
        f.hostStale -> HOST_STALE_HINT
        else -> null
    }
}

/**
 * 「**这一下**点不动」时该说什么 —— 先说**眼前这个控件**的原因，最后才轮到整页那条。
 *
 * ## 为什么不是「整页总闸优先」
 *
 *  以前第一句就是 `blockedReason()`，于是换包没重启那阵子，点**任何**一个灰控件都会回一句
 *  「系统侧还是旧版，重启手机后生效」。用户口径：「点禁用的控件却提示要重启」—— 他点的是一个
 *  **因为主开关关着才灰**的行，那句重启既不是他问的、也不是他刚做的事，只剩下疑惑。
 *
 *  分档按「用户能就地做什么」排：配置没读成功（什么都改不了）→ 总闸关 → 这一颗键停用 →
 *  最后才是链路那几句（宿主没起 / 没注入 / 接管点 0 / 系统侧旧包 / 设置没同步）。
 *  链路那几句解释的是**整页为什么都不对**，进页那一次 [noticeReason] 已经说过一遍，
 *  不必在每一次点击上重复。
 *
 * ## 两种键同一句话
 *
 *  停用**任何一颗**（含侧键）之后，打开它的入口都是同一个 ⋮ 菜单里那一行。
 */
internal fun SettingsPage.disabledClickReason(): String {
    val f = linkFacts
    val cfg = configState.config.value
    val key = KeyWatchState.editing(cfg)
    return when {
        //  配置都没读成功：什么都改不了，说别的都是空话
        f.readOnly -> CONFIG_READ_FAILED_HINT

        //  总闸关着：用户自己关的，能就地开回来
        f.masterOff -> KEY_DISABLED_HINT

        !cfg.key(key).enabled -> I18n.format(Strings.KEY_DISABLED_ITEM, key.label)

        //  这一项自己没有原因 ⇒ 才轮到「整页为什么不对」（重启 / 勾作用域那几句）；
        //  页面是通的、这一项仍然点不动（比如行要求先选一颗键）：说结论，不编一个「没就绪」
        else -> blockedReason() ?: I18n.get(Strings.CONTROL_UNAVAILABLE, "这一项现在不可用")
    }
}

/**
 * 「按键接管」那一段 —— 回答「**现在到底有没有键会被模块接住**」。
 *
 *  前面几段都答不了这件事：服务在跑、接管点挂着、配置也送到了，只要总闸关着（或每颗键都停用），
 *  按下去仍然是系统自己的功能。真机上撞过一次 —— 自检全绿、按侧键却打开了系统页面，因为总闸是关的，
 *  而「主开关关着」当时被当成「用户意图」静默掉了：不报红、也不进明细。
 *
 *  **仍然不报红**（关开关是用户的意图，不该骂人），但走 [LinkCheck.inSummary]：
 *  整体状态那一行会直接说「主开关已关闭，所有按键都不接管」。
 *
 *  单独一个函数只为让 [linkChecks] 留在 `LongMethod` 阈值内。
 */
private fun SettingsPage.takeOverCheck(): LinkCheck {
    val cfg = configState.config.value
    //  配置都没读出来时，`cfg` 是默认值（总闸 false）—— 那时说「主开关已关闭」是误诊：
    //  用户看到的那一行明明是开的，说关了只会让他去查一个没问题的地方。
    val readOnly = configState.readOnly
    val masterOn = cfg.global.enabled
    val anyKeyOn = cfg.allKeys.any { cfg.key(it).enabled }
    return LinkCheck(
        icon = PageIcons.LinkTakeOver,
        name = I18n.get(Strings.LINK_CHECK_TAKE_OVER, "按键接管"),
        ok = masterOn && anyKeyOn,
        detail =
            when {
                readOnly -> I18n.get(Strings.LINK_CHECK_CONFIG_FAILED, "配置读取失败")
                !masterOn -> I18n.get(Strings.LINK_CHECK_MASTER_OFF_ALL, "主开关已关闭，所有按键都不接管")
                !anyKeyOn -> I18n.get(Strings.LINK_CHECK_ALL_KEYS_OFF, "所有按键都已停用")
                else -> I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
            },
        blocking = false,
        inSummary = true,
    )
}

/**
 * 「使用验证」那一段 —— 单独一个函数只为让 [linkChecks] 留在 `LongMethod` 阈值内
 * （那一段本身与上面三段没有共享的判据，搬出去不改变任何结论）。
 *
 *  **永远不 block**：还没被验证过不是故障，只是一条还没拿到的证据。
 *  文案里**不出现「按过没按过」**——那是内部证据的说法（用户点名删的）。
 */
private fun SettingsPage.usageCheck(inj: InjectStatus?): LinkCheck =
    LinkCheck(
        icon = PageIcons.LinkUsage,
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
    )

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
            icon = PageIcons.LinkService,
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
            icon = PageIcons.LinkKeyResponse,
            name = I18n.get(Strings.LINK_CHECK_KEY_RESPONSE, "按键响应"),
            ok = respondOk,
            detail =
                when {
                    inj == null -> I18n.get(Strings.LINK_CHECK_WAITING_RESPONSE, "等待响应")
                    inj.hooks <= 0 -> I18n.get(Strings.LINK_CHECK_NOT_RESPONDING, "未响应")
                    else -> I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
                },
        ),
        // 「按键接管」：接管点挂上 ≠ 按键真被接住 —— 总闸关着时上面那段照样绿，
        //  而一颗键都不会被接住（真机撞过：自检全绿、所有按键都没反应）。
        takeOverCheck(),
        // 「设置同步」（配置送到注入侧没有）与「设置读取」（宿主读到的不是默认值）
        // 合成一段：对用户来说都是「设置到底生效了没有」，分开报只是把内部结构摆到脸上。
        LinkCheck(
            icon = PageIcons.LinkConfig,
            name = I18n.get(Strings.LINK_CHECK_CONFIG_EFFECTIVE, "设置生效"),
            // 与总闸**同源**：配置侧那半直接用 [configSideOk]，免得两边各写一份慢慢跑偏。
            //  第三项是**执行侧**用不用得上那份配置 —— 前两项都在 system_server 那条线上，
            //  真正执行动作的宿主主进程读不懂时它们照样全绿（真机撞过：所有按键都没反应、自检全绿）。
            //  「用不用得上」是 [hostConfigBroken]：它同时覆盖「新宿主报读不懂」与「旧宿主不带那一位、
            //  靠 enabled 对账反推出来」两条来路。
            ok = configSide && !f.hostConfigBroken && link.hostEnabled,
            // 「主开关已关闭」**不算故障**（见 [configSideOk] 与 [linkReady] 的注释）：
            //  只有「配置侧没问题、单纯是用户关了主开关」这一种不 ok 时不 block。
            //  其余（配置读不懂 / 没送到注入侧 / 两边指纹对不上）都真会让设置不生效 —— 照旧 block。
            //  一律 block（`blocking` 走默认值）的话，用户主动关掉主开关，
            //  整体状态就报「设置生效异常」—— 那是用户的意图，不是故障。
            //  **读不懂必须单独排在最前**：那时 `hostEnabled` 也是 false，落到下面那条会被
            //  判成「用户关的」，于是不 block、detail 还说「主开关已关闭」—— 把人指去拨一个
            //  本来就开着的开关（本次故障的现场）。
            blocking = f.hostConfigBroken || !(configSide && !link.hostEnabled),
            //  换包没重启时它必然红，而那句话就是「需要重启」—— 首页据此把它排到最后
            //  （别的故障先说，只剩这条影子时才说重启）
            staleOnly = restartNeeded || (f.hostStale && f.hostConfigBroken),
            detail =
                when {
                    // 真因优先：文件读不懂时宿主读到的是默认值（enabled=false），
                    // 再往下会报成「主开关已关闭」—— 那会把用户引到错的方向（他根本没关）。
                    //  与 `blockedReason` 同一档分法：跑着旧版时「配置读取失败」是误诊 ——
                    //  文件没问题，是这一侧的结构比文件旧。
                    f.readOnly && f.hostStale -> {
                        I18n.get(Strings.LINK_CHECK_RESTART_REQUIRED, "需要重启")
                    }

                    f.readOnly -> {
                        I18n.get(Strings.LINK_CHECK_CONFIG_FAILED, "配置读取失败")
                    }

                    // **执行侧**那份配置用不了（设置页自己读得懂）：它与「主开关关着」在
                    //  `hostEnabled` 上同形，所以必须排在下面那条 `!link.hostEnabled` 之前 ——
                    //  否则这一行会说「主开关已关闭」，而用户根本没关。
                    //  跑着旧包时它是那件事的影子（说「需要重启」，指重启服务）；否则是真读不懂。
                    f.hostConfigBroken -> {
                        if (f.hostStale) {
                            I18n.get(Strings.LINK_CHECK_RESTART_REQUIRED, "需要重启")
                        } else {
                            I18n.get(Strings.LINK_CHECK_CONFIG_FAILED, "配置读取失败")
                        }
                    }

                    inj == null -> {
                        I18n.get(Strings.LINK_CHECK_WAITING_RESPONSE, "等待响应")
                    }

                    !inj.configOk -> {
                        I18n.get(Strings.LINK_CHECK_NOT_EFFECTIVE, "未生效")
                    }

                    restartNeeded -> {
                        I18n.get(Strings.LINK_CHECK_RESTART_REQUIRED, "需要重启")
                    }

                    // 收到过、但指纹对不上（且不是换包没重启造成的）：改过的那一次没送到它手上
                    //  判据与上面 `ok` **同源**（[configSideOk]）—— 这里原来另算了一遍，两处条件
                    //  还差一个 `isNotEmpty()`，于是「横幅说重启、这一行说未同步」。
                    !configSide -> {
                        I18n.get(Strings.LINK_CHECK_NOT_SYNCED, "未同步")
                    }

                    // 读到默认值只有两种来路：主开关被关了，或者开机还没解锁
                    !link.hostEnabled -> {
                        I18n.get(Strings.LINK_CHECK_SWITCH_OFF, "主开关已关闭")
                    }

                    else -> {
                        I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
                    }
                },
        ),
        // 这一段永远不 block：还没被验证过不是故障，只是一条还没拿到的证据。
        usageCheck(inj),
    )
}
