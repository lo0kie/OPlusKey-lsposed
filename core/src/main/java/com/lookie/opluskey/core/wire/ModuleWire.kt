package com.lookie.opluskey.core.wire

/**
 * 跨进程的**线格式** —— 三条广播通道上每个 action 与 extra 的字符串，全项目唯一一处定义。
 *
 * ## 为什么它不在 `KeyModule` 上
 *
 *  这一批常量原来挂在 `KeyModule` 的 companion 上。而 `KeyModule` 是**入口**（`java_init.list`
 *  写死了那个全限定名，它自己要去 `host/` 与 `ui/` 装东西），把线格式挂上去之后，
 *  **每一个要用一条广播字符串的人都得 import 入口那个类** —— 于是：
 *
 *   - `host/` 依赖 `KeyModule`，而 `KeyModule` 又要分发到 `host/` ⇒ 包级**成环**
 *     （`host → root → host`，`tools/audit-coupling.py` 抓到的第一条）；
 *   - `ui/` 与 `inject/` 同理，为了一个字符串常量去加载注入入口。
 *
 *  挪进 `core/wire` 之后：三方都只依赖最底层，入口类只剩「派发」这一件事。
 *  取值与语义一条没改，只是换了家 —— `KeyModule` 里那批同名的已删，别再往回加。
 *
 * ## 三条通道
 *
 *   1. system_server → 宿主：[ACTION_GESTURE]（判好的手势，宿主只执行）
 *   2. 宿主 ↔ 设置页：[ACTION_HOST_SERVICE_UP] / [ACTION_HOST_SERVICE_QUERY] /
 *      [ACTION_PAGE_RELAUNCH]（链路自检与把页面拉回来）
 *   3. 设置页 ↔ 注入侧：[ACTION_INJECT_QUERY] / [ACTION_INJECT_READY]（问 / 答那一对）
 *
 *  发送方都必须 `setPackage(...)` 指定包名（指定包名的广播不算隐式广播，
 *  也只有目标注册的接收器能收）—— 包名取自 `core/model/HostApp`。
 */
object ModuleWire {
    /**
     * system_server → 宿主的手势转交广播（宿主私有目录 system_server 读不了，`/data/system`
     * 宿主写不了，广播是两端都能用的通道）。发送时必须指定宿主包名：指定包名的广播不算隐式广播，
     * 也只有宿主注册的接收器能收。带的是判好的手势（判定挪回 system_server —— 宿主会被冻结、
     * 广播积压到解冻才灌，原始按键判定会错乱），宿主只负责执行。
     */
    const val ACTION_GESTURE = "com.lookie.opluskey.GESTURE"

    /** 广播里携带的键 id（`KeyId.id`，本模块只有 `plus`）。 */
    const val EXTRA_KEY = "key"

    /** 广播里携带的**手势**（`Gesture.id`：`single` / `double` / `long`）。 */
    const val EXTRA_PHASE = "phase"

    /**
     * [EXTRA_PHASE] 的一个特殊取值：**这一按松手了**（它不是手势，是「按压结束」）。
     *
     *  为什么必须单独占一格而不是复用 `long`：宿主侧的 `handle` 按 (键, phase) 去重、按 phase
     *  映射手势，松手既不是手势、也不能和判长按那一条互相去重 —— 它是一条**独立的信号**，
     *  而且晚于长按那条到达（长按到点先发，松手才后发）。
     *
     *  为什么由判定侧发、而不是让宿主自己收尾：松手发生在 `system_server` 的按键路径上
     *  （`SideKeyInterceptor.feedDetector` 的 ACTION_UP），宿主根本看不到原始按键 ——
     *  它只收「判好的东西」。所以「松手」也得像手势一样转过来。
     *
     *  只有**「按下开始、松手结束」**的动作在乎它（此刻只有 `oplus:voice_memory`）：
     *  其余动作按一下就完事，没有结束形态，宿主收到这个 phase 直接忽略。
     */
    const val PHASE_RELEASE = "release"

    /** 广播里携带的「是否长按连续触发的重复派发」（执行侧据此跳过振动）。 */
    const val EXTRA_REPEAT = "repeat"

    /**
     * 广播携带的发送时刻（`SystemClock.elapsedRealtime()`，跨进程同源）。
     * 宿主据此丢弃冻结期间积压、解冻才送到的过期手势，判据见 `host/GestureReceiver` 的 `STALE_MS`。
     */
    const val EXTRA_SENT_AT = "sent_at"

    /**
     * 宿主进程 → 设置页：**宿主侧的模块已经加载**（由 `host/GestureReceiver` 在拿到 Context 后发出，
     * 那一步只有模块注入成功才跑得到）。
     *
     *  这条是首页「模块有没有生效」的**唯一可靠判据**：查宿主那支侧键服务是不行的 ——
     * 它是宿主自己的常驻服务，**系统会自己把它拉起**，服务在跑 ≠ 模块在跑。
     */
    const val ACTION_HOST_SERVICE_UP = "com.lookie.opluskey.HOST_SERVICE_UP"

    /**
     * 设置页 → 宿主进程：**问一声宿主侧的模块在不在**，宿主收到就回一条 [ACTION_HOST_SERVICE_UP]。
     *
     *  要这一问是因为广播是**一次性**的：宿主报活时设置页可能还没起来（那声就错过了）。
     */
    const val ACTION_HOST_SERVICE_QUERY = "com.lookie.opluskey.HOST_SERVICE_QUERY"

    /**
     * 设置页 → 宿主主进程：**「重启服务」请你等一下把设置页拉回来**（执行在 `host/PageRelaunch`）。
     *
     *  被杀掉的进程没法把自己叫回来，而「从进程外叫」那条路在真机上被后台启动策略拦掉
     *（闹钟挂 `PendingIntent` 拉起 Activity 会撞上 `Background activity launch blocked!`）；
     * 宿主主进程自己 `startActivity` 才放行 —— 本包拿着 `START_ACTIVITIES_FROM_BACKGROUND`。
     * 所以这一声必须发到**宿主主进程**（收方是 `host/GestureReceiver` 那一个接收器），
     * 不是宿主本身就算数。
     */
    const val ACTION_PAGE_RELAUNCH = "com.lookie.opluskey.PAGE_RELAUNCH"

    // ── 链路自检：设置页 ↔ 注入侧（system_server） ──
    //  这一对是**问 / 答**，只为「首页能不能说清是哪一段断了」而存在（见 `ui/LinkCheck.kt`）。
    //  注入侧不必为它额外挂 Hook：它已经挂在 AMS 的广播入口上，全系统每条广播都过那里；
    //  来源校验比的是 uid，而设置页与宿主**同 uid**（`docs/device-findings.md` 六之七）。

    /** 设置页 → 注入侧：**问一声注入侧那套装好没有**。 */
    const val ACTION_INJECT_QUERY = "com.lookie.opluskey.INJECT_QUERY"

    /**
     * 注入侧 → 设置页：回 [ACTION_INJECT_QUERY] 的状态包。
     *
     *  注意：**不要把这个 action 加进注入侧 `KeyHooks.readBroadcast` 的过滤表** ——
     * 那会让注入侧去处理自己发出去的包（多绕一圈，且毫无意义）。
     */
    const val ACTION_INJECT_READY = "com.lookie.opluskey.INJECT_READY"

    /** [ACTION_INJECT_READY] 里带「装上几个侧键接管点」（0 = 一个都没装上）。 */
    const val EXTRA_INJECT_HOOKS = "inject_hooks"

    /** [ACTION_INJECT_READY] 里带「注入侧收到过配置没有」（false ⇒ 注入侧还在用默认值顶着）。 */
    const val EXTRA_INJECT_CONFIG_OK = "inject_config_ok"

    /**
     * [ACTION_INJECT_READY] 里带「注入侧最后一次判出手势是什么时候」。
     *
     * `SystemClock.elapsedRealtime()` —— 跨进程同源，页面直接与自己的时钟相减就是「多久以前」。
     * 0 = 开机以来从未判出过（侧键一次都没被接管）。
     */
    const val EXTRA_INJECT_LAST_GESTURE_AT = "inject_last_gesture_at"

    /**
     * [ACTION_INJECT_READY] 里带「注入侧手上那份配置的指纹」（见 `ConfigBroadcast.tag`）。
     *
     *  设置页拿它与自己手上那份比：**相等才叫「设置生效」**。「收到过一条」不等于「就是最新那份」——
     *  开机收到过之后哪条广播丢了，注入侧还按旧配置跑，只回一个布尔的话界面照样报「正常」。
     *  还没收到过配置时这一项为空串。
     */
    const val EXTRA_INJECT_CONFIG_TAG = "inject_config_tag"

    /**
     * [ACTION_INJECT_READY] 里带「注入侧跑的是哪一次构建」（`ModuleInfo.BUILD_ID`）。
     *
     *  它是**唯一**能判「换过包但没重启」的信号：system_server 里那份代码只在开机那一刻加载，
     *  所以换包不重启时它照样回包、各项自检也照样过，只是 `BUILD_ID` 还是上一版。
     *  页面拿它与自己那份比：不等 ⇒ 明确是「要重启」，与「作用域漏了 system」不是一回事
     *  （后者**根本不会有回包**）。
     *  旧版注入侧不带这一项时读到空串，按「不判」处理（升级过程中的一次性过渡）。
     */
    const val EXTRA_INJECT_BUILD_ID = "inject_build_id"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主读到的主开关真值」——页面据此判它是不是读到了真配置。 */
    const val EXTRA_HOST_ENABLED = "host_enabled"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主最后一次执行动作是什么时候」（同上，0 = 从未）。 */
    const val EXTRA_HOST_LAST_ACTION_AT = "host_last_action_at"
}
