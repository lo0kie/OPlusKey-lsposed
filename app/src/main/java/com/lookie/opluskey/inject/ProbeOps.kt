package com.lookie.opluskey.inject

import android.content.Intent
import android.os.Handler
import android.view.MotionEvent
import com.lookie.opluskey.bridge.extOf
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.ComboPlan
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.inject.input.ComboDown
import com.lookie.opluskey.inject.input.ComboSequence
import com.lookie.opluskey.inject.input.RawInput
import com.lookie.opluskey.inject.input.Swipe
import com.lookie.opluskey.inject.input.TouchJudge

// 探针的四个 op：看任务状态（`state`）、给窗口设圆角（`corner`）、「补壳」（`dress`）与其内部动作。
// 从 ProbeChannel 分出来（object 不能用两个文件装，走扩展）；它用的那几个小工具留在那边。

// ── op: state ────────────────────────────────────────────────────────────

/** 只读打印：目标任务的三样东西 —— 窗口模式、几何、以及 TaskExt 上那组柔性状态字段。 */
internal fun ProbeChannel.state(
    loader: ClassLoader,
    intent: Intent,
) {
    // `taskOf` 第一步就是 `FlexibleWindowManagerService.getInstance()`，那会触发宿主类的 `<clinit>`
    //  —— 与 `call` 同一条险路，所以同样要显式 opt-in（见 [allowUnsafe]）。
    if (!allowUnsafe(SERVICE_CLASS, OP_STATE, intent)) return
    val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
    val task = taskOf(loader, taskId)
    if (task == null) {
        XLog.w("probe/state：拿不到任务 #$taskId")
        return
    }
    XLog.w("probe/state 任务 #$taskId bounds=${Reflect.call(task, "getBounds")}")
    val ext = extOf(task)
    if (ext == null) {
        XLog.w("probe/state：读不到 TaskExt")
        return
    }
    listOf("mScenario", "mHasCaption", "mFlexibleZoomState", "mCaptionName", "mFlexibleWindowState")
        .forEach { f ->
            val v =
                try {
                    Reflect.fieldValue(ext, ext.javaClass, f) ?: "（没有这个字段）"
                } catch (e: Throwable) {
                    "（读失败 ${e.javaClass.simpleName}）"
                }
            XLog.w("probe/state  ext.$f = $v")
        }
    // 操作条视图到底建出来了没有 —— 这是 C 方案要回答的第一个问题
    listOf("getFlexibleCaptionView", "getCaptionView", "mCaptionView").forEach { n ->
        val v =
            try {
                Reflect.call(ext, n) ?: Reflect.fieldValue(ext, ext.javaClass, n)
            } catch (e: Throwable) {
                null
            }
        if (v != null) {
            XLog.w("probe/state  ext.$n → $v")
            // 视图在不在是一回事，它有没有真的显示出来是另一回事 —— 这一位就是「挂上/显示了没有」
            XLog.w("probe/state  ·mCaptionViewSurfaceShow = ${fieldOf(v, "mCaptionViewSurfaceShow")}")
            stateOfCaptionView(v)
        }
    }
}

// ── op: corner ───────────────────────────────────────────────────────────

/**
 * 给目标任务的窗口 surface 设圆角 —— C 方案的第一半。
 *
 * `SurfaceControl.Transaction#setCornerRadius` 是**公开 API**，所以这一半不依赖任何私有签名；
 * 困难只在「从 Task 拿到 SurfaceControl」，候选名逐个试并把命中的写进日志。
 */
internal fun ProbeChannel.corner(
    loader: ClassLoader,
    intent: Intent,
) {
    if (!allowUnsafe(SERVICE_CLASS, OP_CORNER, intent)) return
    val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
    val radius =
        if (intent.hasExtra(EXTRA_RADIUS)) {
            intent.getFloatExtra(EXTRA_RADIUS, DEFAULT_CORNER_RADIUS)
        } else {
            DEFAULT_CORNER_RADIUS
        }
    val task = taskOf(loader, taskId)
    if (task == null) {
        XLog.w("${describe(OP_CORNER)}：拿不到任务 #$taskId")
        return
    }
    cornerOn(task, radius, taskId)
}

/** 给一个任务的窗口 surface 设圆角（`corner` 与 `dress` 共用）。 */
internal fun ProbeChannel.cornerOn(
    task: Any,
    radius: Float,
    taskId: Int,
) {
    val sc = surfaceControlOf(task)
    if (sc == null) {
        XLog.w("${describe(OP_CORNER)}：从任务 #$taskId 上拿不到 SurfaceControl（候选名都没命中）")
        return
    }
    val tx = android.view.SurfaceControl.Transaction()
    try {
        // `setCornerRadius(SurfaceControl, float)` 在公开 SDK 里没有（`SurfaceControl` 类本身有），
        // 所以这里也得走反射 —— 同样按**原始类型**取 Method（Float 装箱坑）。
        val m =
            Reflect.method(
                tx.javaClass,
                "setCornerRadius",
                android.view.SurfaceControl::class.java,
                java.lang.Float.TYPE,
            )
        if (m == null) {
            XLog.w("${describe(OP_CORNER)}：这个 ROM 的 Transaction 上没有 setCornerRadius(SurfaceControl,float)")
            return
        }
        Reflect.call(tx, m, sc, radius)
        Reflect.call(tx, "apply")
        XLog.w("${describe(OP_CORNER)}：任务 #$taskId 的 surface 圆角已设为 $radius")
    } catch (e: Throwable) {
        XLog.w("${describe(OP_CORNER)} setCornerRadius 失败：${e.javaClass.simpleName}: ${e.message}")
    } finally {
        // 这条 Transaction 是**自己建、自己 apply** 的：不关就泄一个 native 对象
        //  （每一个提前 return 的分支都会漏一份）。
        //  保护写 `catch (Throwable)` 而不是 `runCatching`：后者只给日志出口与反射查找用
        //  （AGENTS.md 五之二、2）—— 这里要的是「关不掉也绝不外抛」，与同目录 `KeyWatch` 一致。
        try {
            tx.close()
        } catch (e: Throwable) {
            XLog.d("关闭 Transaction 失败（已忽略）：${e.javaClass.simpleName}")
        }
    }
}

/**
 * `dress` —— 「补壳」：把伪造态小窗缺的那两样补上（**圆角** + **操作条挂载/显示**）。
 *
 * 三个开关分开传，一次重启后就能任意组合试（每一步单独打日志）：
 * `radius`（给了才做圆角）/ `caption`（挂到任务上）/ `show`（显示）。
 *
 * 配方来自用本通道 `dump` 出来的真签名：
 * `FlexibleWindowManagerService.createFlexibleCaptionView(Task, String)`、
 * `FlexibleCaptionView.initCaptionViewAndAttachToTask(Task, Rect)`、`showCaptionViewWithAnim()`。
 */
internal fun ProbeChannel.dress(
    loader: ClassLoader,
    intent: Intent,
) {
    if (!allowUnsafe(SERVICE_CLASS, OP_DRESS, intent)) return
    val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
    val task = taskOf(loader, taskId)
    if (task == null) {
        XLog.w("${describe(OP_DRESS)}：拿不到任务 #$taskId")
        return
    }
    if (intent.hasExtra(EXTRA_RADIUS)) {
        cornerOn(task, intent.getFloatExtra(EXTRA_RADIUS, DEFAULT_CORNER_RADIUS), taskId)
    }
    val doCaption = intent.getBooleanExtra(EXTRA_CAPTION, false)
    val doShow = intent.getBooleanExtra(EXTRA_SHOW, false)
    if (!doCaption && !doShow) {
        XLog.w("${describe(OP_DRESS)}：这一把只做了圆角（没带 caption / show）")
        return
    }

    val view = captionViewOf(loader, task)
    if (view == null) {
        XLog.w("${describe(OP_DRESS)}：既拿不到、也建不出 FlexibleCaptionView")
        return
    }
    XLog.w("${describe(OP_DRESS)}：caption view = $view")
    val doInit = intent.getBooleanExtra(EXTRA_INIT, false)
    // attach 的第二个参数**就是操作条自己的尺寸**（不是任务 bounds）——
    // 真机实测：传任务 bounds 它就把视图撑成整窗（getWidth/Height == 任务尺寸）。
    // 所以这里允许显式传 rect（`--ei rect_r 1272 --ei rect_b 157` 一类），不传才退回任务 bounds。
    val bounds =
        if (intent.hasExtra(EXTRA_RECT_R)) {
            android.graphics.Rect(
                intent.getIntExtra(EXTRA_RECT_L, 0),
                intent.getIntExtra(EXTRA_RECT_T, 0),
                intent.getIntExtra(EXTRA_RECT_R, 0),
                intent.getIntExtra(EXTRA_RECT_B, 0),
            )
        } else {
            Reflect.call(task, "getBounds")
        }

    // 注意：**这两步必须在视图自己的线程上做**。视图属于 `android.ui` 线程，而从 AMS 广播入口进来是
    // binder 线程 —— 直接调会抛 `CalledFromWrongThreadException`（真机实测）。
    // 用**视图自己的 Handler**（`View.getHandler()`）post 过去，线程一定对，不用猜是哪个 looper。
    val work =
        Runnable {
            //  这一段可能被 post 到 **system_server 的 `android.ui` 线程**（见上），而里面每一行
            //  都是一次反射调用 —— 任何一条抛出来都是那个线程上的未捕获异常，代价是整个
            //  system_server 崩掉（开机循环）。所以整段过 guard。
            XLog.guard("${describe(OP_DRESS)}：操作条补壳") {
                if (doCaption) {
                    // 先 init 再 attach：attach 假定视图已经初始化过
                    if (doInit) {
                        XLog.w("${describe(OP_DRESS)}：initCaptionView → ${invokeOn(view, "initCaptionView")}")
                    }
                    XLog.w(
                        "${describe(OP_DRESS)}：initCaptionViewAndAttachToTask($bounds) → " +
                            invokeOn(view, "initCaptionViewAndAttachToTask", task, bounds),
                    )
                }
                if (doShow) {
                    XLog.w("${describe(OP_DRESS)}：showCaptionViewWithAnim → ${invokeOn(view, "showCaptionViewWithAnim")}")
                    XLog.w(
                        "${describe(OP_DRESS)}：updateFlexibleTaskCaptionVisible(true) → " +
                            invokeOn(view, "updateFlexibleTaskCaptionVisible", task, true),
                    )
                }
                // 尺寸对了之后还得让它重排 / 换模式：`handleButtonRegion` 空着说明手柄区没算出来
                if (intent.getBooleanExtra(EXTRA_RELAYOUT, false)) {
                    XLog.w("${describe(OP_DRESS)}：onRelayoutCaptionView → ${invokeOn(view, "onRelayoutCaptionView")}")
                }
                if (intent.getBooleanExtra(EXTRA_MODE, false)) {
                    XLog.w("${describe(OP_DRESS)}：updateCaptionViewMode(true) → ${invokeOn(view, "updateCaptionViewMode", true)}")
                }
                if (intent.getBooleanExtra(EXTRA_TOUCH, false)) {
                    // 触及区域是它算的：不算就没有 handleButtonRegion，操作条画得出来但点不动
                    XLog.w("${describe(OP_DRESS)}：updateTouchRegion → ${invokeOn(view, "updateTouchRegion")}")
                }
                XLog.w("${describe(OP_DRESS)}：mCaptionViewSurfaceShow = ${fieldOf(view, "mCaptionViewSurfaceShow")}")
                stateOfCaptionView(view)
            }
        }
    val viewHandler =
        try {
            Reflect.call(view, "getHandler") as? Handler
        } catch (e: Throwable) {
            null
        }
    if (viewHandler != null) {
        XLog.w("${describe(OP_DRESS)}：把补壳两步 post 到视图自己的线程（${viewHandler.looper.thread.name}）")
        viewHandler.post(work)
    } else {
        XLog.w("${describe(OP_DRESS)}：视图没有 Handler，只能就地做（大概率 WrongThread）")
        work.run()
    }
}

// ── op: net ──────────────────────────────────────────────────────────────

/**
 * 在本进程（system_server）里试一次联网 —— 验「注入侧能不能查更新」。
 *
 *  **必须另起线程**：这条广播在 AMS 的广播入口上跑，而联网是阻塞 IO —— 在主线程上做等于
 *  把整个系统的广播分发停住几秒（五之二、3：注入侧绝不拖慢系统，更不能把异常抛回去）。
 *
 *  分三步打日志，因为这三步是三个不同的闸，一次问清：
 *  1. **裸 TCP**（字面 IP、不经 DNS）—— 过了就说明域允许 inet socket，与 DNS 无关；
 *  2. **DNS**（`Network#getAllByName`，绑在默认网络上解析）—— system_server 自己的 socket
 *     没有「进程默认网络」，裸 `InetAddress.getByName` 会 `UnknownHostException`（实测），
 *     必须走这个 API；
 *  3. **HTTP**（拿第 2 步的地址走 `HttpURLConnection`）—— 真正要用的那一步。
 *
 *  `MissingPermission`（下面那颗抑制）：这段跑在 **system_server**（uid 1000），
 *  `ACCESS_NETWORK_STATE` 它本来就有（`device-findings.md` 四十一）；模块清单里不声明任何权限，
 *  同 `NetChannel.activeNetwork`。
 */
@android.annotation.SuppressLint("MissingPermission")
internal fun ProbeChannel.net(
    intent: Intent,
    loader: ClassLoader,
) {
    val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_PROBE_URL
    val host = runCatching { java.net.URI(url).host }.getOrNull() ?: "api.github.com"
    val uid = android.os.Process.myUid()
    Thread(
        {
            val cm =
                runCatching {
                    com.lookie.opluskey.bridge.inject.SystemContext
                        .get(loader)
                        ?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                }.getOrNull()
            val network = cm?.activeNetwork
            XLog.w("probe/net：uid=$uid activeNetwork=$network")

            // 1) 裸 TCP：字面 IP，不经 DNS —— 验的是「域允不允许 inet socket」
            try {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress("223.5.5.5", 443), PROBE_NET_TIMEOUT_MS)
                    XLog.w("probe/net：裸 TCP 223.5.5.5:443 通了")
                }
            } catch (e: Throwable) {
                XLog.w("probe/net：裸 TCP 不通 → ${e.javaClass.name}: ${e.message}")
            }

            // 2) DNS：必须绑在默认网络上解析（system_server 的 socket 没有进程默认网络）
            val addrs =
                try {
                    network?.getAllByName(host).also {
                        XLog.w("probe/net：DNS $host → ${it?.joinToString() ?: "null"}")
                    }
                } catch (e: Throwable) {
                    XLog.w("probe/net：DNS 失败 → ${e.javaClass.name}: ${e.message}")
                    null
                }

            // 3) 真正要用的那一步：HTTP
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = PROBE_NET_TIMEOUT_MS
                conn.readTimeout = PROBE_NET_TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "OPlusKey-probe")
                XLog.w("probe/net：HTTP ${conn.responseCode}（$url，DNS 拿到 ${addrs?.size ?: 0} 个地址）")
                conn.disconnect()
            } catch (e: Throwable) {
                XLog.w("probe/net：HTTP 失败 → ${e.javaClass.name}: ${e.message}")
            }
        },
        "probe-net",
    ).start()
}

/** `net` 那一趟的超时 —— 5 秒：够一次 DNS + TLS，又不至于把线程吊在那儿。 */
private const val PROBE_NET_TIMEOUT_MS = 5_000

// ── op: touch ────────────────────────────────────────────────────────────

/**
 * `touch` —— 判定层的**自检**：在进程内造几条合成轨迹，跑一遍
 * 适配（`MotionEvent` → `RawInput`）→ 滑动判定，逐条打出「期望 / 实得」。
 *
 *  为什么在探针里做：触摸事件目前在系统进程里**没有采集点**（入口还没在真机上确认，见 `TouchJudge`
 *  的类注释），可判定与适配不能是「写完没跑过」的代码。合成轨迹把这条链跑通，等于先证明
 *  「同一个几何算出来的结论是对的」，等真实入口确认后只需接上线。
 *
 *  期望值写死在断言里（这些都是判据，不是手感）：点按不产出、快扫报方向、慢拖不产出、
 *  多指整段作废。任何一条与期望不符，日志里那一行就是现场。
 */
internal fun ProbeChannel.touch() {
    //  合成事件的时间轴：判定只取**同一条轨迹内**的差值，基准取哪儿都行
    val down = android.os.SystemClock.uptimeMillis()

    /** 造一条事件喂进去，返回这一段判出来的滑动（只有抬手那一条会有结论）。 */
    fun feed(
        action: Int,
        atMs: Long,
        x: Float,
        y: Float,
    ): Swipe? {
        val e = MotionEvent.obtain(down, down + atMs, action, x, y, 0)
        return try {
            TouchJudge.onMotionEvent(e)
        } finally {
            runCatching { e.recycle() }
        }
    }

    fun report(
        name: String,
        expected: String,
        actual: Swipe?,
    ) {
        XLog.w("probe/touch：$name → 期望 $expected，实得 ${actual ?: "不产出"}")
    }

    // 1) 点按：位移 3px，还在 slop（28px）里
    feed(MotionEvent.ACTION_DOWN, 0, 500f, 1000f)
    report("点按", "不产出", feed(MotionEvent.ACTION_UP, 30, 503f, 1002f))

    // 2) 快扫：向右 340px / 150ms
    feed(MotionEvent.ACTION_DOWN, 200, 500f, 1500f)
    feed(MotionEvent.ACTION_MOVE, 240, 600f, 1502f)
    feed(MotionEvent.ACTION_MOVE, 280, 700f, 1508f)
    feed(MotionEvent.ACTION_MOVE, 320, 840f, 1510f)
    report("右扫", "RIGHT 340px", feed(MotionEvent.ACTION_UP, 350, 840f, 1510f))

    // 3) 慢拖：同样的位移，但跨了 1000ms（超过 800ms 那一档）
    feed(MotionEvent.ACTION_DOWN, 400, 500f, 2000f)
    feed(MotionEvent.ACTION_MOVE, 800, 700f, 2000f)
    feed(MotionEvent.ACTION_MOVE, 1200, 840f, 2000f)
    report("慢拖", "不产出", feed(MotionEvent.ACTION_UP, 1400, 840f, 2000f))

    // 4) 多指：`MotionEvent.obtain(down, …)` 只能造单指事件，这一档直接喂归一化事件
    //    （判据是「手指数 > 1 就作废」，与走不走适配器无关）
    TouchJudge.submit(RawInput.TouchDown(0, 500f, 2500f, pointers = 2, atMs = 2000))
    TouchJudge.submit(RawInput.TouchUp(0, 900f, 2500f, pointers = 2, atMs = 2100))
    XLog.w("probe/touch：多指 → 期望 不产出（上面「滑动：」那行不该出现第二条）")
}

// ── op: combo ────────────────────────────────────────────────────────────

/**
 * `combo` —— 组合键判定层的**自检**：造几档组合与几串按下，跑一遍「凑没凑齐」的判据。
 *
 *  为什么放在探针里：真机上组合判定由 `ComboDetector` 那条事件状态机扛着，一次按压只有一条
 *  证据、判错了没人能重放。同一套规则在这里写成**无状态的纯判据**（`ComboSequence`），
 *  合成序列跑通就等于先证明「同一串按下的结论是对的」—— 判据错了在这一步就红，而不是等真机。
 *
 *  **两边必须同改**（见 `ComboSequence` 的类注释）：这里期望的每一条都是判定器口径的镜像，
 *  多成员（3 颗起）这一轮已经接线，所以「三颗」那一档现在就该凑齐。
 *
 *  期望值写死在断言里（都是判据，不是手感）：两颗窗口内凑齐、超窗不成立、只按一颗报还差、
 *  中间夹一颗不相干的键不影响、三颗凑齐成员多的那一档。
 */
internal fun ProbeChannel.combo() {
    //  成员键 id 全部取自 `KeyId`（内置侧键 + 第 1~3 颗自定义键）—— 这里不写字面量，
    //  免得前缀一改这条自检就悄悄测了一堆不存在的键。
    val plus = KeyId.PLUS.id
    val ck1 = KeyId.customId(1)
    val ck2 = KeyId.customId(2)
    val ck3 = KeyId.customId(3)

    fun plan(
        id: String,
        vararg keys: String,
    ): ComboPlan =
        ComboPlan(
            id = id,
            keys = keys.toSet(),
            collectMs = 300,
            longPressMs = 500,
            //  与 `ComboKey.plan()` 同一条口径：够两颗就判（多成员已接线，上限不再卡在两颗）
            judgeable = keys.size >= ComboKey.MIN_KEYS,
        )

    val plans = listOf(plan("cmb1", plus, ck1), plan("cmb2", plus, ck1, ck2), plan("cmb3", plus, ck1, ck2, ck3))

    fun report(
        name: String,
        expected: String,
        downs: List<Pair<String, Long>>,
    ) {
        val gather =
            ComboSequence.resolve(
                downs.map { (key, at) -> ComboDown(key, at) },
                plans.filter { it.judgeable },
            )
        XLog.w("probe/combo：$name → 期望 $expected，实得 ${ComboSequence.describe(gather)}")
    }

    // 1) 两颗顺序按下、间隔 100ms（窗口 300）—— 真机上走的就是这条
    report("两颗 / 间隔 100ms", "凑齐 cmb1", listOf(plus to 0L, ck1 to 100L))

    // 2) 同一对超过窗口 ⇒ 哪一档都不成立
    report("两颗 / 间隔 400ms", "不成立", listOf(plus to 0L, ck1 to 400L))

    // 3) 只按了第一颗 ⇒ 这条路仍成立，还差一颗
    report("只按一颗", "还差 1 颗", listOf(plus to 0L))

    // 4) 中间夹一颗不相干的键 ⇒ 不影响（判定器那边叫「配不上的候选不占位」）
    report("中间夹一颗别的键", "凑齐 cmb1", listOf(plus to 0L, KeyId.customId(9) to 50L, ck1 to 100L))

    // 5) 三颗按顺序 ⇒ 凑齐成员多的那一档（`maxByOrNull { keys.size }` 那条）
    report("三颗 / 间隔 80ms", "凑齐 cmb2", listOf(plus to 0L, ck1 to 80L, ck2 to 160L))

    // 6) 同一颗按了两次 ⇒ 按最后那一次算（判定器的 `recent` 只留最后一条）
    report("同一颗按两次", "凑齐 cmb1", listOf(plus to 0L, plus to 40L, ck1 to 80L))
}
