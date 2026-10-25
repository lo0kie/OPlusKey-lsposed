package com.lookie.opluskey.core.wire

/**
 * 跨进程的**线格式** —— 三条广播通道上每个 action 与 extra 的字符串，都在这一处定义。
 *
 *  调试通道那条 action 是例外（它不属于这三条通道，也不该让 `inject/` 反过来依赖本文件）：
 *  `inject/ProbeChannel` 与 `app/src/debug/…/PageDebugChannel` 各自留着自己的那一份。
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

    /** 广播里携带的键 id（`KeyId.id`：内置侧键或收集来的自定义键）。 */
    const val EXTRA_KEY = "key"

    /**
     * 广播里携带的**组合键 id**（`ComboKey.id`：`cmb1` / `cmb2`）。
     *
     *  带了这一项就说明这一条是「两颗键一起按」，宿主按**组合**那条路走（动作在 `ComboKey`
     *  上、没有单颗键的 `KeyConfig`）；不带则仍是 [EXTRA_KEY] 那条单键路径。
     *
     *  两者**不混用**：组合同时把 [EXTRA_KEY] 填成 `cmb1` 的话，宿主日志与去重表里
     *  「键」这一列会混进一种不是键的东西。
     */
    const val EXTRA_COMBO = "combo"

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

    /** 广播里携带的「是否长按连续触发的重复派发」（执行侧据此决定要不要振动、要不要补发报活）。 */
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

    /**
     * 模块入口（管理器那颗 FAB 背后的 `EntryActivity`）→ 宿主主进程：**把侧键设置页拉到前台**。
     *
     *  为什么绕这一趟：那个 Activity 在宿主清单里带
     *  `android:permission="oplus.permission.OPLUS_COMPONENT_SAFE"`，普通 app 直接 `startActivity`
     *  必被 `SecurityException` 拦；而宿主起**自己包**的页面没有这道检查。
     *
     *  这一条的接收器必须是 `RECEIVER_EXPORTED`：发送方是**另一个 uid** 的普通 app（模块自己的包），
     *  与 [ACTION_HOST_SERVICE_QUERY] / [ACTION_PAGE_RELAUNCH] 那几条「同 uid 的设置页进程」不同。
     *  它只做「把设置页拉到前台」一件事，不读配置、不改状态。
     */
    const val ACTION_OPEN_SETTINGS = "com.lookie.opluskey.OPEN_SETTINGS"

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
     *  判「换过包但没重启」的**备用尺子**：主尺子是 [EXTRA_INJECT_PROC_START]（进程启动时刻 vs
     *  包更新时间，不需要人记得改任何常量）。这一项留下的理由有两个 ——
     *   · 旧版注入侧不含时间戳那一项时，只能靠它（读到空串 = 对面连它都没带，按「不判」处理）；
     *   · 它是「同一次构建」的**身份**，日志里对得上人看得出来是哪一版。
     *  代价是它要人手维护：忘了改就量不出差别，所以**不能让它单独决定「要不要重启」**。
     */
    const val EXTRA_INJECT_BUILD_ID = "inject_build_id"

    /**
     * [ACTION_INJECT_READY] 里带「注入侧所在进程的启动时刻」（wall clock 毫秒，见 `ProcClock.startWall`）。
     *
     *  判「换过包但没重启」的**主尺子**：与安装包的 `lastUpdateTime` 一比就知道 ——
     *  进程起得比包早 ⇒ 它加载的是上一版代码。
     *  0 = 对面是还没带这一项的旧版，此时退回 [EXTRA_INJECT_BUILD_ID] 那条。
     */
    const val EXTRA_INJECT_PROC_START = "inject_proc_start"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主进程的启动时刻」（同上）。宿主是普通应用进程，重开即可，不必重启手机。 */
    const val EXTRA_HOST_PROC_START = "host_proc_start"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主读到的主开关真值」——页面据此判它是不是读到了真配置。 */
    const val EXTRA_HOST_ENABLED = "host_enabled"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主最后一次执行动作是什么时候」（同上，0 = 从未）。 */
    const val EXTRA_HOST_LAST_ACTION_AT = "host_last_action_at"

    // ── 「自定义按键」页：监听按键（设置页 ↔ 注入侧） ──
    //
    //  一对「开 / 关」+ 一对「听见了」。设置页进页时开、出页时关；注入侧在监听期间把每一颗
    //  按下的键原样报回来（见 `inject/KeyWatch`，**只读旁路**，不改认领与放行）。
    //
    //  注意：[ACTION_KEY_SEEN] 的接收方是**设置页自己**注册的接收器（与设置页同进程），
    //  不经 `GestureReceiver` —— 那边属 `host/`，而 host/ 不许认识界面层
    //  （`docs/decoupling.md` 动作 1、2 与 `tools/audit-coupling.py`）。

    /** 设置页 → 注入侧：开 / 关那条旁路监听（开关在 [EXTRA_WATCH_ON]）。 */
    const val ACTION_KEY_WATCH = "com.lookie.opluskey.KEY_WATCH"

    /** [ACTION_KEY_WATCH] 里带「开还是关」。 */
    const val EXTRA_WATCH_ON = "watch_on"

    /**
     * 「等按键」这一趟最多持续多久。
     *
     *  它是**两侧共用的契约**：注入侧到点自己停发键码（`KeyWatch` 的兜底），页面按同一个数
     *  显示倒计时并在同一刻收场。两处各写一个 60 秒的话，界面上的倒计时会与真实窗口对不上。
     */
    const val KEY_WATCH_WINDOW_MS = 60_000L

    /** 注入侧 → 设置页：听见一颗键（**只报按下那一下**）。 */
    const val ACTION_KEY_SEEN = "com.lookie.opluskey.KEY_SEEN"

    /** [ACTION_KEY_SEEN] 里带「键码」。 */
    const val EXTRA_SEEN_CODE = "seen_code"

    // ── 导出日志：把另外两个进程的日志一起收上来 ──
    //
    //  设置页那格「导出」只导**本进程**的缓冲（见 `XLog.snapshot`），而判据行散在三个进程里：
    //  `system`（system_server 的接管与按键判定）、`host`（宿主主进程的抓取与执行）、
    //  `ui`（设置页自己）。用户抓回来的日志因此常常「一条按键行都没有」—— 排查时等于瞎。
    //
    //  一问多答：[ACTION_LOG_PULL] 由设置页发出，另外两个进程各自把缓冲切片回 [ACTION_LOG_CHUNK]；
    //  设置页收几百毫秒、拼起来再落盘。**分片**是必需的：一次广播塞几百 KB 会撞事务上限。

    /** 设置页 → 注入侧 / 宿主主进程：把你的日志缓冲回给我。 */
    const val ACTION_LOG_PULL = "com.lookie.opluskey.LOG_PULL"

    /** 任一进程 → 设置页：一段日志（分片，序号与总数见下面两个 extra）。 */
    const val ACTION_LOG_CHUNK = "com.lookie.opluskey.LOG_CHUNK"

    /** [ACTION_LOG_CHUNK] 里带「这一段是哪个进程的」（`system` / `host` / `ui`）。 */
    const val EXTRA_LOG_PROC = "log_proc"

    /** [ACTION_LOG_CHUNK] 里带「第几片」（从 0 起）。 */
    const val EXTRA_LOG_INDEX = "log_index"

    /** [ACTION_LOG_CHUNK] 里带「一共几片」。 */
    const val EXTRA_LOG_TOTAL = "log_total"

    /** [ACTION_LOG_CHUNK] 里带「这一段的正文」。 */
    const val EXTRA_LOG_TEXT = "log_text"
}
