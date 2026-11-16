package com.lookie.opluskey.host.action.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.shizuku.ShizukuExec
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 宿主侧「以 Shizuku 执行」的入口 —— 决定**在哪个进程跑**，其余交给 [ShizukuExec]。
 *
 * ## 两条通路，先本进程、后模块进程
 *
 *  1. **本进程（Sui）**：Sui 是 zygisk 那一路，桥注进了每个 app 进程 —— 宿主进程自己
 *     `Sui.init(包名)` 就有 binder，命令就在宿主进程里跑。真 Shizuku 没有这一档：binder 只推到
 *     声明了 `ShizukuProvider` 的那个进程（本模块）。
 *  2. **模块进程（真 Shizuku）**：命令得转给 [com.lookie.opluskey.shizuku.ShizukuShellService]，
 *     由它跑完把退出码回过来。这条路要求**模块进程在跑** —— ColorOS 对第三方一律不许被拉起
 *     （`OplusAppStartupManager`，连 system_server 都拒，见 `docs/device/lsposed.md` 四十二），
 *     所以它由本应用自己立成常驻前台服务（`ShizukuShellService`），宿主 bind 只是建连接。
 *
 *  判据只有一条：**本进程拿不拿得到 binder**（[ShizukuExec.available]）。拿不到才回落，
 *  不按「装的是 Sui 还是 Shizuku」猜。授权是**另一个问题**：拿得到 binder 却没授权时当场报
 *  「没有可用的 Shizuku」（[exec]），不回落 —— 那一侧授权的是同一个 uid，回落只是白等。
 */
internal object ShizukuHost {
    /**
     * 让**本进程**拿到 Shizuku 的 binder；拿不到就是 false（此时才轮到模块进程那一路）。
     *
     *  `Sui.init` 的入参是 Shizuku 记的「服务端上下文」，库自己传的就是包名（`ShizukuProvider`
     *  `onCreate` 里那句），这里跟着传。
     */
    fun ensureBinder(pkg: String): Boolean {
        if (ShizukuExec.available()) {
            logServer()
            return true
        }
        val viaSui = runCatching { Sui.init(pkg) }.getOrDefault(false)
        val ok = ShizukuExec.available()
        if (ok || !viaSui) XLog.w("Shizuku 本进程：Sui.init=$viaSui ping=$ok（$pkg）")
        if (ok) logServer()
        return ok
    }

    /**
     * 打一次「服务端是谁、什么版本」—— 只在这一进程第一次拿到 binder 时打。
     *
     *  `requestPermission` 在这台机器上发出去没有任何反应（框不来、回执也不来），而回执的投递
     *  方式随服务端 API 版本变过（v11 与 v13 不同）：先把这个事实固定下来，再判断是不是
     *  版本对不上，而不是继续在「线程」「UI 权限」上猜。
     *
     *  `isSui` 一并打：binder 到底是 Sui 给的还是 Shizuku 给的，决定后面该用哪一套说法。
     */
    private fun logServer() {
        if (serverLogged) return
        serverLogged = true
        runCatching {
            XLog.w(
                "Shizuku 服务端：isSui=${Sui.isSui()} version=${Shizuku.getVersion()} " +
                    "latest=${Shizuku.getLatestServiceVersion()} serverUid=${Shizuku.getUid()} " +
                    "preV11=${Shizuku.isPreV11()} selinux=${Shizuku.getSELinuxContext()}",
            )
        }.onFailure { XLog.w("Shizuku 服务端：取版本信息失败", it) }
    }

    /** [logServer] 只打一次（每次申请授权、每次执行都会先 `ensureBinder`）。 */
    private var serverLogged = false

    /**
     * **现在有没有授权**（不发弹窗）。别在主线程调。
     *
     *  消费方只有一个：[requestGrant] 开头那次预检 —— 已经有授权就别再弹一次框。**界面不拿它
     *  当勾的显示判据**（见 `ExecSwitchCard` 的函数头）：读一次要跨进程问模块进程，而它回
     *  「没有」的几种情形（没装 / 那一下没连上 / 授权被撤）在界面上会长成同一副样子 ——
     *  「用户存着开，编辑时却变成关」。
     *
     * ## 必须问**将来执行的那个进程**
     *
     *  授权按 uid 记，宿主（`com.oplus.gesture`）与模块（本应用）是两个 uid。真 Shizuku 那一路
     *  binder 只推到模块进程，宿主这边 `checkSelfPermission` 永远返回拒绝 —— 只看本进程就会把
     * 「模块已授权」误判成「没授权」，而执行时走的正是模块那一侧。
     *
     *  所以分岔与 [exec] 严格同一条：本进程拿得到 binder → 判据就是本进程这一份（那是将来执行
     *  的进程）；拿不到 binder → 问模块进程那一侧（[ShizukuShellClient.readGranted]，跨进程
     *  bind，几百毫秒）。
     */
    fun readGranted(ctx: Context): Boolean = probe(ctx) == ShizukuShellClient.GrantState.GRANTED

    /**
     * 与 [readGranted] 同一件事，但**不把结论压成一个布尔** —— 申请授权那一侧要分档（见
     * [requestGrant]）：「有 Shizuku、只是没授权」才该去弹框；「这台机器上没有可用 Shizuku」
     * 去了也弹不出来，那一轮就是白等。
     */
    private fun probe(ctx: Context): ShizukuShellClient.GrantState {
        //  与 [exec] 同一条分岔：本进程拿得到 binder，执行就在本进程，判据也只有本进程这一份；
        //  拿不到 binder 才是模块进程那一侧的事。
        if (ensureBinder(ctx.packageName)) {
            return if (ShizukuExec.granted()) {
                ShizukuShellClient.GrantState.GRANTED
            } else {
                ShizukuShellClient.GrantState.NOT_GRANTED
            }
        }
        return ShizukuShellClient.probe(ctx)
    }

    /**
     * 预检，但给**刚起来的那个进程**一点宽限期（见 [replaceProcess] 的调用点）。
     *
     * ## 为什么要等
     *
     *  新进程起来之后 binder 是服务端**异步**推过来的：`ShizukuProvider` 也才刚创建，
     *  那一刻去问只会得到「没有 binder」。只问一次就下结论，用户看到的就是
     *  「刚换的进程也说没有 Shizuku」。
     *
     *  只对「没有可用 Shizuku」重试：连不上模块进程（[ShizukuShellClient.GrantState.CHANNEL_DOWN]）
     *  重试也没用 —— 那说明跳板这次没把进程拉起来，再问几轮只是白等。
     */
    private fun probeWithGrace(ctx: Context): ShizukuShellClient.GrantState {
        var state = probe(ctx)
        var round = 1
        while (state == ShizukuShellClient.GrantState.NO_SHIZUKU && round < PROBE_ROUNDS) {
            XLog.w("Shizuku：还没有 binder，${PROBE_GAP_MS}ms 后再问（第 $round 轮）")
            Thread.sleep(PROBE_GAP_MS)
            state = probe(ctx)
            round++
        }
        return state
    }

    /** [probeWithGrace] 最多问几轮 / 两轮之间隔多久 —— 合起来约十秒，够新进程把 binder 拿到手。 */
    private const val PROBE_ROUNDS = 5

    private const val PROBE_GAP_MS = 2_500L

    /**
     * 把一个**新进程**换上来，再问一次状态（[requestGrant] 那一轮用）。
     *
     * ## 为什么必须换进程
     *
     *  binder 只推给**那之后新起**的进程，活着的老进程挂多久都拿不到（真机实测，见
     *  `ShizukuShellService` 的 binder 监听）。所以「连不上」与「连得上却没 binder」这两档
     *  在原地等下去都是白等，该做的是把老的请走、再起一个新的。
     *
     * ## 起新进程只剩跳板那一条路
     *
     *  ColorOS 的启动管理把 `startService` / `bindService` 那一档对所有调用方关死（连
     *  `system_server` 自己都拒，见 `docs/device/lsposed.md` 四十二），而**模块那颗跳板 Activity**
     *  不在那道闸的判定里：宿主在前台时起一个 exported 的 Activity 是放行的。跳板一起来就会把
     *  后台通道立成常驻（`EntryActivity` → `BackgroundChannel`），之后 bind 只是建连接。
     *
     *  跳板不画东西（透明 + `noHistory`），被这一档拉起时只把进程与后台通道拉起来、**不碰页面**
     *  （带 [ModuleWire.EXTRA_WAKE_ONLY]）—— 用户此刻正开着设置页，再转一次会把页里那摞子页
     *  清掉、退回第一页。
     *
     * @return 换完之后问到的状态（换不出来就是 [ShizukuShellClient.GrantState.CHANNEL_DOWN]）
     */
    private fun replaceProcess(
        ctx: Context,
        state: ShizukuShellClient.GrantState,
    ): ShizukuShellClient.GrantState {
        XLog.w("Shizuku：通道拿不到实时状态（$state）—— 换一个新进程再问")
        //  活着的旧进程要先请它退场：它不走，起来的还是同一个进程（照样拿不到 binder）。
        //  连不上就是压根没有进程挡在路上 —— 那一档再去 bind 一次只是撞同一道启动闸，白等。
        if (state != ShizukuShellClient.GrantState.CHANNEL_DOWN) ShizukuShellClient.retire(ctx)
        if (!wakeModule(ctx)) return ShizukuShellClient.GrantState.CHANNEL_DOWN
        //  新进程起来之后 binder 是异步到的，给它几轮宽限（见 [probeWithGrace]）
        return probeWithGrace(ctx)
    }

    /**
     * 拉起模块的跳板，让它在**新进程**里跑一次（见 [replaceProcess]）。
     *
     *  带上 [ModuleWire.EXTRA_WAKE_ONLY]：跳板那一侧只把进程与后台通道拉起来，**不转设置页**
     *  —— 换 binder 这一下用户正开着那一页，转一次会把页里那摞子页清掉（退回第一页）。
     *
     * @return 拉起请求有没有被系统受理（**不代表进程已经起来**，那由随后的 [probe] 判）
     */
    private fun wakeModule(ctx: Context): Boolean =
        try {
            ctx.startActivity(
                Intent()
                    .setComponent(ComponentName(ModuleInfo.PACKAGE, ENTRY_CLASS))
                    .putExtra(ModuleWire.EXTRA_WAKE_ONLY, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            XLog.w("Shizuku：已请模块跳板拉起新进程")
            true
        } catch (e: Throwable) {
            XLog.w("Shizuku：拉起模块跳板失败（${e.javaClass.simpleName}: ${e.message}）", e)
            false
        }

    /**
     * 跳板的类名（`root` 包的 `EntryActivity`）。
     *
     *  这里只留名字、不引那个类：`host` 引 `root` 会造出一条反向的包级依赖，而这件事只需要
     *  一个组件名（与 `ShizukuShellClient.SERVICE_CLASS` 同一个写法）。
     */
    private const val ENTRY_CLASS = "com.lookie.opluskey.EntryActivity"

    /**
     * 跑一条命令，返回退出码（`0` = 成功；负数见 [ShizukuExec] / [ShizukuShellClient]）。
     *
     *  **阻塞**：调用方（`ShellRunner` 的池线程）本来就是后台线程。
     *
     *  有 binder 但没授权时**当场就报**（[ShizukuShellClient.NO_SHIZUKU]），不回落模块进程：
     *  那一侧在同一个 uid 上授权也是同一台机器的事，回落只会让一次注定失败的执行多等 40 秒
     *  （`ShizukuShellClient.exec` 的上限）。分岔与 [readGranted] 严格同一条。
     */
    fun exec(
        ctx: Context?,
        command: String,
        onLine: ((String) -> Unit)? = null,
    ): Int {
        if (ctx != null && ensureBinder(ctx.packageName)) {
            if (!ShizukuExec.granted()) {
                XLog.w("Shizuku 本进程：这个 uid 还没被授权")
                return ShizukuShellClient.NO_SHIZUKU
            }
            return ShizukuExec.exec(command, onLine)
        }
        XLog.w("Shizuku 本进程拿不到 binder，转交模块进程")
        return ctx?.let { ShizukuShellClient.exec(it, command, onLine) } ?: ShizukuShellClient.CONNECT_FAIL
    }

    /**
     * 申请一次授权（「以 Shizuku 执行」打开时）。结果在**主线程**回，只回一次。
     *
     * ## 先问一句「是不是已经有了」
     *
     *  整段跑在 [worker] 那条后台线程上（拿 binder、bind 模块进程都是 IPC，主线程不能被它卡住）。
     *  已经有授权的（判据同 [readGranted]，问的是**将来执行的那个进程**）直接回
     *  [GrantOutcome.GRANTED]、不弹框 —— 用户可能只是把开关关掉又打开；问错了 uid 则会白弹一次框。
     *
     * ## 问到「没有」先换进程，别把活推给用户
     *
     *  模块进程手上没有 binder（[ShizukuShellClient.GrantState.NO_SHIZUKU]）或连不上
     *  （[ShizukuShellClient.GrantState.CHANNEL_DOWN]）时，答案是**这个进程不行**而不是「没有
     *  Shizuku」：binder 只推给新起的进程。所以先按 [replaceProcess] 换一个，再判 ——
     *  这一下不该变成一句「去点 LSPosed 管理器里的模块图标」。
     *
     * ## 请求从主线程发，等结果不靠回执
     *
     *  请求那一步（`Shizuku.requestPermission`）**必须在主线程**：Sui 那一档要由它自己在
     *  「有前台窗口的进程」里弹框，实测从后台线程发起时框根本不出现。它本身只把请求交给
     *  Sui / Shizuku，不等用户点，所以不会卡住界面。
     *
     *  等结果**不信回执、只信状态**：`addRequestPermissionResultListener` 不一定被回调
     *  （真机：把授权框直接关掉，两边都没有任何回执），而 `checkSelfPermission()` 可靠 ——
     *  所以另起一条线程轮询它，**谁先到都算数**（[done] 保证只回一次）。轮询上限见
     *  [GRANT_POLL_MS]：它就是「框被关掉之后还要干等多久」。
     *
     * ## 一次只跑一条
     *
     *  还在检查中又点一下，**什么都不做**（[granting]）：不排队、不重复弹框、不发第二份请求。
     *  这条链路由单线程 worker 串起来，允许重复进来就变成「上一次等满超时，下一次才开始」。
     *
     * ## 为什么区分「拒绝」与「问不到结论」
     *
     *  用户点了拒绝、且回执到了，是 [GrantOutcome.DENIED] —— 界面什么都不用报（开关本来就停在
     *  「关」上）。而**超时**与**没问成**都是「不知道结论」（Sui 那一档框关掉后既没有回执、
     *  状态也不变），一律报 [GrantOutcome.UNAVAILABLE]：那句话正好是「不可用或尚未授权」。
     *  静默处理会让用户看着提示条转完圈、一个字都没说，只能猜是不是坏了。
     */
    fun requestGrant(
        ctx: Context,
        onResult: (GrantOutcome) -> Unit,
    ) {
        //  一次只跑一条：还在检查中又点一下，**什么都不做**（不排队、不重复弹框、不发第二份请求）。
        //  这条链路原先是一条单线程队列，于是「上一次等满超时、下一次才开始」—— 用户看到的是
        //  「点了没反应，过一会儿才动」。所以这里挡住重复进来，而不是让它排队。
        if (!granting.compareAndSet(false, true)) {
            XLog.w("Shizuku：上一次检查还没收场，这次忽略")
            return
        }
        val main = Handler(Looper.getMainLooper())
        val done = AtomicBoolean(false)

        fun settle(outcome: GrantOutcome) {
            if (done.compareAndSet(false, true)) {
                //  闸与结果一起放：下一次点击只有在这一条收场之后才可能进来
                granting.set(false)
                main.post { onResult(outcome) }
            }
        }

        worker.execute {
            try {
                //  先问一遍「是不是已经有了」：拿 binder / bind 模块进程都是一次 IPC（首次可能还要
                //  `Sui.init`），放在这条后台线程上 —— 主线程不能被它卡住。已经授权的直接回，
                //  不再弹框：用户可能只是关掉又打开这个开关。
                var state = probe(ctx)
                //  两档「问不到实时状态」都是**进程的问题**，不是 Shizuku 的问题：连不上是通道没在跑
                //  （多半是 Shizuku 重启时它自己退场了），连得上却没 binder 是那个进程太老。
                //  两种都常住不动，原地等下去还是同一个答案 —— 换一个新进程再问（[replaceProcess]）。
                if (state == ShizukuShellClient.GrantState.NO_SHIZUKU ||
                    state == ShizukuShellClient.GrantState.CHANNEL_DOWN
                ) {
                    //  带着刚才那一问的结论进去，不再问第二遍
                    state = replaceProcess(ctx, state)
                }
                when (state) {
                    ShizukuShellClient.GrantState.GRANTED -> {
                        settle(GrantOutcome.GRANTED)
                        return@execute
                    }

                    //  换过进程也拿不到：这台机器上确实没有可用的 Shizuku，再走一遍请求授权只会
                    //  白等一轮。与「连不上通道」分开报（[GrantOutcome.UNAVAILABLE]）。
                    ShizukuShellClient.GrantState.NO_SHIZUKU -> {
                        settle(GrantOutcome.UNAVAILABLE)
                        return@execute
                    }

                    //  跳板也没把它拉起来：通道这半天真的不在（见 [GrantOutcome.CHANNEL_DOWN]）。
                    ShizukuShellClient.GrantState.CHANNEL_DOWN -> {
                        settle(GrantOutcome.CHANNEL_DOWN)
                        return@execute
                    }

                    //  有 Shizuku 但没授权（或问不到）：往下走，去弹框
                    ShizukuShellClient.GrantState.NOT_GRANTED, ShizukuShellClient.GrantState.UNKNOWN -> {
                        //  不做事，落到下面那条「去请求授权」
                    }
                }
                //  本进程拿不到 binder：真 Shizuku 那一路，要授权的是模块进程（与执行同一侧）。
                if (!ensureBinder(ctx.packageName)) {
                    ShizukuShellClient.requestGrant(ctx) { result -> settle(outcomeOf(result)) }
                    return@execute
                }
                //  请求那一步**必须在主线程**（见函数头）。
                main.post {
                    ShizukuExec.requestGrantBlocking { result -> settle(outcomeOf(result)) }
                }
                //  等结果不信回执、只信状态：另起一条轮询（这条线程本来就是后台的，占着没关系）
                var waited = 0L
                while (waited < GRANT_POLL_MS && !done.get()) {
                    Thread.sleep(GRANT_POLL_STEP_MS)
                    waited += GRANT_POLL_STEP_MS
                    if (ShizukuExec.granted()) {
                        settle(GrantOutcome.GRANTED)
                        return@execute
                    }
                }
                if (!done.get()) {
                    XLog.w("Shizuku：等了 ${waited}ms 还没授权（框可能被关掉了）")
                    //  超时**不等于「用户拒绝」**：拒绝会走回执，回执里明确是拒绝、那一档静默。
                    //  超时是「问不到结论」，必须报一句 —— 否则用户看到的就是提示条转完圈、
                    //  一个字都没说，只能猜是坏了。
                    settle(GrantOutcome.UNAVAILABLE)
                }
            } catch (t: Throwable) {
                //  闸必须有人放：这条链路上任何一次意外都不能让「检查」永久挂在「进行中」——
                //  那样用户再点就永远没反应了（那正是这次要消灭的观感）。
                XLog.w("Shizuku：检查授权时抛异常（${t.javaClass.simpleName}: ${t.message}）", t)
                settle(GrantOutcome.UNAVAILABLE)
            }
        }
    }

    /**
     * 一次申请的三态 → 界面那一侧的结局（两条路共用这一处口径）。
     *
     *  拒绝与「没问成」必须分开：前者用户已经表过态、界面一个字都不用报；
     *  后者（含超时）是**问不到结论**，必须报一句，否则用户只看到提示条转完圈、什么都没说。
     */
    private fun outcomeOf(result: ShizukuExec.GrantResult): GrantOutcome =
        when (result) {
            ShizukuExec.GrantResult.GRANTED -> GrantOutcome.GRANTED
            ShizukuExec.GrantResult.DENIED -> GrantOutcome.DENIED
            ShizukuExec.GrantResult.FAILED -> GrantOutcome.UNAVAILABLE
        }

    /** 一次授权申请的四种结局（见 [requestGrant]）。 */
    enum class GrantOutcome {
        /** 拿到了。 */
        GRANTED,

        /** 有 Shizuku，但这次没授权 —— 用户明确拒绝（回了拒绝回执）。**不是故障**，不报错。 */
        DENIED,

        /**
         * 换过新进程之后仍然没有可用的 Shizuku，**或者这一次根本没问成**
         * （没有 binder / 发起失败 / 等到超时也没结论）。
         *
         *  超时归这一档而不是 [DENIED]：拒绝会有回执，超时是「不知道」—— 不知道就得说一声。
         */
        UNAVAILABLE,

        /**
         * **换成新进程之后仍然连不上** —— 跳板这次没把它拉起来（见 [wakeModule]）。
         *
         *  这一档才能说「得再来一次」：它已经排除了「进程太老」这个成因（那由 [replaceProcess]
         *  处理掉了），剩下的只是这一次没起来。
         */
        CHANNEL_DOWN,
    }

    /**
     * 轮询授权状态的总时长 / 步长。
     *
     *  **这是「框被关掉」时唯一会走完的那条路**（回执不来、状态也不变），所以它等于用户把框
     *  关掉之后还要干等多久。原先给 30 秒是按「用户可能在框上想很久」算的，真机上看到的就是
     *  「框早没了，界面还在转」。10 秒足够点一下允许，也短到不会把等待拖成故障。
     */
    private const val GRANT_POLL_MS = 10_000L

    private const val GRANT_POLL_STEP_MS = 500L

    /**
     * 一次申请还在跑 —— 再点就忽略（见 [requestGrant] 开头）。
     *
     *  它取代的是「排队」：这条链路由单线程 worker 串起来，允许重复进来就变成
     *  「上一次等满超时，下一次才开始」。
     */
    private val granting = AtomicBoolean(false)

    /** 授权 / 执行那条链路的后台线程（同时只会有一条，串行排着）。 */
    private val worker =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "shizuku-host").apply { isDaemon = true }
        }
}
