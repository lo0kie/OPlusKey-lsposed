package com.lookie.opluskey.ui.wire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import com.lookie.opluskey.command.platform.AppCloneCatalog
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.resolveAppName
import com.lookie.opluskey.core.model.resolveTargetName
import com.lookie.opluskey.core.present.appTargetLabel
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.NetReply
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.target.cloneLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * 一个绑定目标该显示的名字 —— 网格小字 / 顶栏「已选择」/ 步骤名都走它。
 *
 *  **顺序不在这里**：[resolveAppName] 是唯一声明处（现查系统 → 落盘那份 → 老 `|名字` → 包名）。
 *  这里只回答两件事：现查怎么查（[appLabelOrNull]，带缓存）、落盘那份在哪儿（`Config.appNames`）。
 *
 *  这一条原先自己排过一遍顺序，而且与宿主侧**相反**（配置名在 `PackageManager` 前面）——
 *  同一个应用因此在界面上叫旧名、在提示里叫新名。两端理由各自都成立，但不可能同时成立，
 *  所以顺序收到 core 一处，两侧只负责「这一档取到什么」。
 *
 *  第 1 档补分身槽号：主用户的微信与分身微信包名相同，只查名字两行都叫「微信」。
 *  槽号查不到（那一份已经删了）时挂 user id（`微信 · 998`），见 [cloneLabel]。
 *
 *  @param target 绑定的目标写法（`999@包名/类名`、`999@包名/快捷方式id` 或纯包名）
 */
internal fun SettingsPage.appNameOf(target: String): String {
    val userId = AppTarget.userId(target)
    return resolveAppName(
        target = target,
        fromSystem = { pkg ->
            //  分身：主用户与分身的包名相同，只查名字两行都叫「微信」—— 补一个槽号区分
            appLabelOrNull(pkg)?.let { cloneLabel(it, AppCloneCatalog.slotOf(context, userId), userId) }
        },
        fromSaved = { id -> configState.config.value.appNames[id] },
    )
}

/**
 * 「打开应用」那一族的**完整目标**写法 —— **应用名[ - 小窗][ - 组件名 / 快捷方式名]**（`微信 - 扫一扫`）。
 *
 *  三处共用这一句：网格里「应用控制」格点开那层菜单的两行子标题、格子自己的小字、
 *  以及「选择应用」页顶栏那行小字。以前三处各拼一半：网格只写应用名、菜单写
 *  `微信: 扫一扫`（冒号）、页头靠老写法的 `|名字` —— 于是同一个绑定在三处长成三个样子，
 *  而且**重新进页时页头只剩应用名**（新配置的名字在 `Config.appNames`，不在 token 后缀里）。
 *
 * ## 「具体是哪一个」从哪儿来
 *
 *  顺序在 [resolveTargetName]（草稿 → 落盘那份 → 老 `|名字`），这里只回答「落盘那份在哪儿」。
 *  取不到时**组件退回类名最后一段**（`...LauncherUI` → `LauncherUI`）；快捷方式没有这一档
 *  （`包名/id` 里的 id 是给系统看的，摆到界面上没有意义）。
 *
 *  应用名一律**只按包名**取：`包名/快捷方式id` 那个身份在 `appNames` 里存的是快捷方式名，
 *  拿它当应用名会写成「扫一扫 - 扫一扫」。
 *
 * @param shortcut 这一趟绑的是不是快捷方式（只有它才「没有组件」这一说）
 * @param window 是不是以小窗那一档 —— 目标里标「小窗」，否则同一个应用的两条绑定分不出来
 * @param draftLabel 这一趟刚挑中的名字（没挑过给 `null`）
 * @param stockAnim 是不是「使用原版动画」那一档（`app:a:…`）—— 与 [window] 互斥，同一个理由标出来
 */
internal fun SettingsPage.appTargetLabelOf(
    target: String,
    shortcut: Boolean,
    window: Boolean,
    draftLabel: String?,
    stockAnim: Boolean = false,
): String {
    val app = appNameOf(AppTarget.format(AppTarget.pkg(target), AppTarget.userId(target)))
    val own = resolveTargetName(target, draftLabel) { id -> configState.config.value.appNames[id] }
    val detail =
        own?.takeIf { it != app } ?: if (shortcut) null else AppTarget.activity(target)?.substringAfterLast('.')
    return appTargetLabel(app, window, detail, stockAnim)
}

/**
 * 同一个动作的**完整目标**（应用名 + 小窗 / 组件 / 快捷方式），给「手上有 [Action]、但拿不到
 * 草稿」的调用点用（系统场景那一格）。
 *
 *  与 [appTargetLabelOf] 同一个拼法，只是把「从动作到目标」这一小段收进来：调用点不必再各写
 *  一遍「哪个字段是目标、哪一个算小窗」的判据（写两遍就会有一处漏掉小窗，真机就多出一层括号）。
 */
internal fun SettingsPage.appTargetLabelOf(action: Action): String =
    appTargetLabelOf(
        target = action.appTarget ?: (action as? Action.Process)?.pkg.orEmpty(),
        shortcut = action is Action.AppShortcut,
        window = action is Action.AppWindow || (action as? Action.AppShortcut)?.window == true,
        draftLabel = null,
        stockAnim = (action as? Action.App)?.stockAnim == true,
    )

/**
 * 包名 → 应用名缓存（进程级，活到 `:ui` 进程结束）。
 *
 *  为什么要有：步骤行标题与网格小字每次重组都要算，名称框里每敲一个字也重组一遍。
 *  **只缓存查成功的** —— 把失败写进去（值 = 包名）会让一次暂时的查失败永久固化成包名。
 *
 *  **必须是并发安全的**：写它的两个入口不在同一个线程上 —— 组合期（主线程，[appLabelOf]）与
 *  `Dispatchers.IO`（[boundAppName] 那一侧）。普通 `HashMap` 并发 put 在扩容时会丢条目，
 *  极端情况下结构损坏导致死循环。
 */
internal val appLabelCache: ConcurrentHashMap<String, String> = ConcurrentHashMap()

/** 缓存上限 —— 只是省 binder 的一层；不设上限会跟着滚动一路长下去。 */
private const val APP_LABEL_CACHE_MAX = 512

/**
 * 清掉应用名缓存 —— **重进模块时调用**（见 `SettingsPage.onHostReentered`）。
 *
 *  这张表是进程级、没有别的失效路径：应用在这期间改名 / 卸载 / 换图标包，缓存里留着的
 *  就是旧名字（真机上表现为「列表里还是旧名，点进去又是新名」）。
 */
internal fun clearAppLabelCache() {
    appLabelCache.clear()
}

internal fun SettingsPage.appLabelOf(packageName: String): String = appLabelOrNull(packageName) ?: packageName

/**
 * 同 [appLabelOf]，但**查不到给 `null`** —— 名字优先级链需要这个区分。
 *
 *  「查不到」与「名字恰好就是包名」是两件事：前者该让 [resolveAppName] 继续往下走
 *  （落盘那份 / 老后缀），后者不该。合成一个「查不到返回包名」的话，下面三档永远走不到 ——
 *  应用卸载之后界面上就只剩包名，而配置里明明记着它从前叫什么。
 */
private fun SettingsPage.appLabelOrNull(packageName: String): String? {
    appLabelCache[packageName]?.let { return it }
    val label =
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Throwable) {
            XLog.d("取应用名失败：$packageName（${e.javaClass.simpleName}）")
            // 失败不写缓存，下次再查一次
            return null
        }
    if (appLabelCache.size >= APP_LABEL_CACHE_MAX) {
        // 整表清而不是逐条淘汰：这里省的是 binder，清一次最多让下一屏重新查一遍
        appLabelCache.clear()
    }
    appLabelCache[packageName] = label
    return label
}

/**
 * 「当前绑的是哪一个应用」—— 两处以**两种详细度**用同一份（判据只有这里）：
 *
 *  · **网格里那一格的小字 / 抽屉里那一行的小字** → `withDetail = false`：**只写应用名**
 *    （`微信`）。那一行只有一格宽，`微信 - 扫一扫` 会被省略号吃掉（用户口径「格子子标题只写
 *    应用名就行」）；
 *  · **点开那层菜单里「打开应用 / 结束应用」两行的子标题** → `withDetail = true`：
 *    **应用名 - 组件名 / 快捷方式名**（`微信 - 扫一扫`）。菜单行宽，这两行也只能靠这句说清
 *    绑的是哪个界面的哪一个（行标题已经写了「打开应用」）。
 *
 *  **结束应用**（`proc:stop:`）只有应用名：它只存包名，没有界面 / 快捷方式那一层。
 *  其余动作（不是应用那一族的）：`null`，调用方那边就没有这一行小字。
 *
 *  名字查一次就缓存（入参是动作 + 详细度，所以只在换动作 / 名字表变了时重查）。
 *
 *  **查询放 IO**：`appNameOf` 走 `PackageManager`（分身那档还要问 `UserManager`），
 *  在组合期做就是每次进页几十次 binder。这里先给 `null`、查到了再填 —— 小字晚一帧出现，
 *  比首帧卡在 binder 上强。
 *
 * @param withDetail 要不要带「具体是哪一个」（见上：格子不带、菜单带）
 */
@Composable
internal fun SettingsPage.boundAppName(
    current: Action,
    withDetail: Boolean,
): String? {
    val appNames = configState.config.value.appNames
    var label by remember(current, appNames, withDetail) { mutableStateOf<String?>(null) }
    LaunchedEffect(current, appNames, withDetail) {
        // 应用那一族之外（含「无操作」）没有这一行小字
        val target =
            when (current) {
                is Action.App, is Action.AppWindow -> current.appTarget
                is Action.AppShortcut -> current.plainTarget
                is Action.Process -> current.pkg
                else -> null
            } ?: return@LaunchedEffect
        label =
            withContext(Dispatchers.IO) {
                if (withDetail) {
                    appTargetLabelOf(
                        target,
                        shortcut = current is Action.AppShortcut,
                        // 以小窗那一档标出来：`window:` 与「小窗快捷方式」两种
                        window = current is Action.AppWindow || (current is Action.AppShortcut && current.window),
                        draftLabel = null,
                        // 原版动画那一档同理（`app:a:…`）—— 两者互斥，最多一个为真
                        stockAnim = (current as? Action.App)?.stockAnim == true,
                    )
                } else {
                    //  只写**应用名**：快捷方式的身份（`包名/id`）在 `appNames` 里存的是快捷方式名，
                    //  直接拿它当应用名会写成「扫一扫」，所以这里只按包名取。
                    appNameOf(AppTarget.format(AppTarget.pkg(target), AppTarget.userId(target)))
                }
            }
    }
    return label
}

/**
 * 注册一个**同包内部**的广播接收器，失败只记一行、返回 `false`。
 *
 * ## 它替掉的是四份同一段代码
 *
 *  网络代取（`NetBridge`）、导出日志（`LogPull`）、链路自检（`PageContent`）、收集按键
 *  （`CollectKeyDialog`）原先各写一遍
 *  `runCatching { registerReceiver(receiver, IntentFilter(action), RECEIVER_NOT_EXPORTED) }`。
 *  真正要紧的是第三参数：**它是不导出**（普通 App 发同名广播进不来，`system_server` 仍可达）——
 *  一处抄漏就是一个外部可注入的入口，而它藏在一段一眼扫过去的注册代码里。
 *
 * ## 它不管什么
 *
 *  **注销仍归调用方**：那件事和生命周期绑在一起（组合期 `onDispose`、挂起函数 `finally`、
 *  或者「没注册上就不用注销」），四处的时机各不相同，收进来只会变成一个带开关的通用件。
 *
 * @param what 出问题的是哪一环（原样进日志；有「后果」的调用点自己带上，别让它只说「失败」）
 * @param receiver 要挂上去的接收器
 * @param actions 只收这几类广播
 * @return 注册成功没有 —— 失败已经打过日志，调用方只决定「这一趟还做不做」
 */
internal fun Context.registerLocalReceiver(
    what: String,
    receiver: BroadcastReceiver,
    vararg actions: String,
): Boolean {
    val filter = IntentFilter().apply { actions.forEach(::addAction) }
    return runCatching {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }.onFailure {
        XLog.w("$what：注册接收器失败（已忽略）：${it.javaClass.simpleName}", it)
    }.isSuccess
}

// 页面 → 注入侧「替我取一个 URL」的一问一答。
//
// 为什么不能自己发：宿主的清单里没有 `INTERNET`，而界面代码跑在**宿主进程**（真机实证
// `SecurityException`）。能出网的是 system_server 那一侧（`inject/NetChannel`，见
// `docs/device/lsposed.md` 四十一）。协议那一头在 `ModuleWire.ACTION_NET_REQUEST`。

/**
 * 请注入侧取一次 [url] 的正文，最多等 [timeoutMs]。
 *
 *  一问一答靠 [ModuleWire.EXTRA_NET_ID] 配对：同一刻可能有别的代取在跑，回包只认自己那一号。
 *
 *  **收不到就返回 `null`**（不是抛异常）：检查更新是「有就提示、没有就算」的事，
 *  超时、广播发不出去、接收器注册失败在这个调用点全都归成「这次没查到」——
 *  调用方只关心「有没有结论」，不关心断在哪一环（每种断法都有一行 W 日志）。
 *
 *  跑在调用方的协程里（IO 不做 IO 阻塞：真正的网络在注入侧那条后台线程上），
 *  所以这里只等回包，**不占主线程**。
 */
internal suspend fun SettingsPage.fetchText(
    url: String,
    timeoutMs: Long = ModuleWire.NET_TIMEOUT_MS.toLong(),
): NetReply? {
    val id = NEXT_NET_ID.incrementAndGet()
    val reply = CompletableDeferred<NetReply>()
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context?,
                intent: Intent?,
            ) {
                if (intent == null || intent.action != ModuleWire.ACTION_NET_RESULT) return
                if (intent.getLongExtra(ModuleWire.EXTRA_NET_ID, -1L) != id) return
                reply.complete(
                    NetReply(
                        status = intent.getIntExtra(ModuleWire.EXTRA_NET_STATUS, 0),
                        body = intent.getStringExtra(ModuleWire.EXTRA_NET_BODY).orEmpty(),
                        error = intent.getStringExtra(ModuleWire.EXTRA_NET_ERROR).orEmpty(),
                    ),
                )
            }
        }
    val registered = context.registerLocalReceiver("网络代取（这一趟作罢）", receiver, ModuleWire.ACTION_NET_RESULT)
    if (!registered) return null
    try {
        val sent =
            PageWire.send(
                context,
                HostApp.PACKAGE,
                Intent(ModuleWire.ACTION_NET_REQUEST).apply {
                    putExtra(ModuleWire.EXTRA_NET_ID, id)
                    putExtra(ModuleWire.EXTRA_NET_URL, url)
                },
            )
        if (!sent) {
            XLog.w("网络代取：广播发不出去（这一趟作罢）")
            return null
        }
        val result = withTimeoutOrNull(timeoutMs) { reply.await() }
        if (result == null) {
            XLog.w("网络代取：等回包超时 ${timeoutMs}ms（id=$id）")
        } else {
            XLog.w(
                "网络代取：id=$id status=${result.status} 长度=${result.body.length}" +
                    if (result.error.isEmpty()) "" else " 错误=${result.error}",
            )
        }
        return result
    } finally {
        runCatching { context.unregisterReceiver(receiver) }
    }
}

/** 这一趟的编号 —— 进程内自增即可（配对只用它，不跨进程存）。 */
private val NEXT_NET_ID = AtomicLong(0)

/**
 * 一段手势的「位移账本」。
 *
 *  用来抓「滑动被当成一次点击」这种偶发现场：行的点击是在**抬指那一刻**派发的，判「这是滑动还是
 * 点一下」的只有各处组件自己那点逻辑，出问题时日志里只剩一个结果（某一行被点了 / 某个菜单开了），
 * 看不出那一下其实滑了多远、列表跟着动没有。这里把按下→抬起的**竖直位移 / 峰值 / 移动次数 / 用时**
 * 与列表内容有没有跟着移位一并记下，抬指时打一行，点击那一行再打一行 —— 两行配起来就是判据。
 *
 *  注意：**它不只是「记数」** —— 浮层那处真的按 [isDrag] 决定要不要派发行点击
 *  （`ui/dialog/MenuPopup.kt` 里 `MenuRow` 的「滑动不许选中」那道闸）。所以：
 *   · 它**必须**在 release 里也照常工作（不是纯诊断件，别想用构建类型把它关掉）；
 *   · 判断用的 `peak` / `slop` 在每次 [onDown] 归零 —— 读之前必须先经过一次 [onDown]。
 *
 *  [label] 只进日志前缀，用来分清是哪一块（浮层列表 / 步骤卡 / 添加命令那一行）。
 */
@Stable
internal class GestureProbe(
    private val label: String,
) {
    private var downAtMs = 0L
    private var downY = 0f
    private var lastY = 0f
    private var peak = 0f
    private var moves = 0
    private var slop = 0f
    private var contentY = 0f
    private var contentYAtDown = 0f

    fun onDown(
        y: Float,
        slopPx: Float,
    ) {
        downAtMs = android.os.SystemClock.uptimeMillis()
        downY = y
        lastY = y
        peak = 0f
        moves = 0
        slop = slopPx
        contentYAtDown = contentY
    }

    fun onMove(y: Float) {
        moves++
        lastY = y
        peak = maxOf(peak, abs(y - downY))
    }

    /**
     * 记一下某一行在窗口里的位置 —— 它是列表的「有没有滚」探针：位置变了就是滚了。
     *
     *  挂在一行的**内容**上（列表内层），不能挂在列表外面那一圈：滚的是内层，
     *  外圈那层不会动（浮层窗口自己也不动）。
     */
    fun onContentPosition(y: Float) {
        contentY = y
    }

    /**
     * 账本归零 —— 浮层重新打开时清掉上一次的基线（见 `MenuPopup`）。
     *
     *  账本对象是 `remember` 的、**跨浮层开合不重建**：上一次留在
     *  `contentY` / `contentYAtDown` 里的位置是上一个面板的，不归零的话下一次的
     *  「列表内容移位」就是从错误基线算出来的 —— `isDrag()` 不受影响（[onDown] 会把
     *  `peak` 归零），但这行日志是排查时的判据，还是得对。
     */
    fun reset() {
        downAtMs = 0L
        downY = 0f
        lastY = 0f
        peak = 0f
        moves = 0
        contentY = 0f
        contentYAtDown = 0f
    }

    /** 这一段该算滑动（位移越过阈值），不是点一下。 */
    fun isDrag(): Boolean = peak > slop

    /** 一句话账本，供抬指那行与点击那行共用。 */
    fun describe(): String {
        val scrolled = (contentY - contentYAtDown).toInt()
        return "位移 ${(lastY - downY).toInt()}px / 峰值 ${peak.toInt()}px（阈值 ${slop.toInt()}px），" +
            "移动 $moves 次，用时 ${android.os.SystemClock.uptimeMillis() - downAtMs}ms" +
            (if (scrolled != 0) "，列表内容移位 ${scrolled}px" else "，列表没动")
    }

    fun logPrefix(): String = "$label："
}

/** 建一份账本；[label] 进日志前缀。 */
@Composable
internal fun rememberGestureProbe(label: String): GestureProbe = remember(label) { GestureProbe(label) }

/**
 * 挂账本：读这一段手势的位移（`PointerEventPass.Initial`，抢在任何消费之前）。
 *
 *  **不消费任何事件**（`requireUnconsumed = false` 且不调 `consume`）—— 滚动、点击、拖动全照旧，
 *  这一层只记账；抬指时若位移越过阈值就打一行「这一段是滑动」。
 */
@Composable
internal fun Modifier.gestureProbe(probe: GestureProbe): Modifier {
    val slop = LocalViewConfiguration.current.touchSlop
    return pointerInput(probe, slop) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                probe.onDown(down.position.y, slop)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    probe.onMove(change.position.y)
                    if (change.changedToUpIgnoreConsumed()) break
                    if (!change.pressed) break
                }
                if (probe.isDrag()) {
                    // 注意：用 `d` 而不是 `w`：这一段是**每一次滑动**都会打
                    //  一行，而它只是诊断用的账本、不是判据 —— 用 `w` 会让 release 包里每次滑动
                    //  都刷一行（反向问题：刷屏）。`d` 在 release 被编译期消除，debug 里照样看得见。
                    //  账本本身**必须保留**：`MenuRow` 的「滑动不许选中」那道闸要读 `isDrag()`。
                    XLog.d("${probe.logPrefix()}这一段是滑动 —— ${probe.describe()}")
                }
            }
        }
    }
}
