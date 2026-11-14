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
 *   2. 宿主 与 设置页：[ACTION_HOST_SERVICE_UP] / [ACTION_HOST_SERVICE_QUERY] /
 *      [ACTION_PAGE_RELAUNCH]（链路自检与把页面拉回来）
 *   3. 设置页 与 注入侧：[ACTION_INJECT_QUERY] / [ACTION_INJECT_READY]（问 / 答那一对）
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

    /** 广播里携带的「是否长按连续触发的重复派发」（执行侧据此决定要不要振动、要不要补发报活）。 */
    const val EXTRA_REPEAT = "repeat"

    /**
     * 广播里携带的**场景快照请求**（`starter` / `formed`）—— 这一条不是手势，是「某个时刻到了」。
     *
     *  带了这一项就说明本条只用来让宿主把场景定下来（见 `GestureExec.handleComboScene`），
     *  不执行任何动作：按住组合的起点键按下 / 组合凑成，各报一次。
     */
    const val EXTRA_SCENE_STAGE = "scene_stage"

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
     * 宿主主进程 → 设置页：**上面那条请求收到了，我会把页面拉回来**（回执）。
     *
     *  必须要有这一条：`sendBroadcast` 不抛异常只说明「系统受理了这条广播」，**不说明有人接**。
     *  宿主主进程活着但接收器没装上（签名闸拦下 / 换了包 / 那个进程是旧的）时，设置页照旧杀自己，
     *  于是页面没了、也没人再把它叫回来 —— 只能靠用户手动重进。收到回执再杀，那条路才不存在。
     */
    const val ACTION_PAGE_RELAUNCH_ACK = "com.lookie.opluskey.PAGE_RELAUNCH_ACK"

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

    // ── 链路自检：设置页 与 注入侧（system_server） ──
    //  这一对是**问 / 答**，只为「首页能不能说清是哪一段断了」而存在（见 `ui/wire/LinkCheck.kt`）。
    //  注入侧不必为它额外挂 Hook：它已经挂在 AMS 的广播入口上，全系统每条广播都过那里；
    //  来源校验比的是 uid，而设置页与宿主**同 uid**（`docs/device/lsposed.md` 六之七）。

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

    /**
     * 宿主 → 注入侧：**替我注入一颗按键**（[EXTRA_INJECT_CODE]）。
     *
     *  现在只有「截图」用：宿主注入 `KEYCODE_SYSRQ` 时 `injectInputEvent` 返回成功、屏幕没反应
     *  —— ColorOS 不认普通 app 注入的那一下。注入侧跑在 `system_server`，身份就是系统本身
     *  （与 `input keyevent` 同级），那一下它认。
     *
     *  为什么不让宿主 `su` 跑 `service call color_screenshot`：内置动作不该为了截一张图去要 root
     *  （弹授权、慢半拍、没 root 的机器上直接不可用）。这条通道既不走 shell、也不要授权。
     *
     *  收方是 `inject/KeyHooks` 那个 AMS 广播入口（注入侧不给它单独挂接收器）——
     *  它看得见全系统每一条广播，`setPackage` 不影响它收；但发送侧（`ActionRunner.requestSystemInject`）
     *  仍然 `setPackage(宿主包名)` 定向：裸发就是一条谁都能注册接收的隐式广播。
     */
    const val ACTION_INJECT_KEY = "com.lookie.opluskey.INJECT_KEY"

    /** [ACTION_INJECT_KEY] 里带「要注入的键码」（`KeyId.KEYCODE_SYSRQ` 之类）。 */
    const val EXTRA_INJECT_CODE = "inject_code"

    /**
     * 宿主 → 注入侧：**替它弹一条提示**（[EXTRA_TOAST_TEXT]）。
     *
     *  为什么挪到注入侧弹：系统 toast 按**包**限流（3 条 / 20 秒、5 条 / 42 秒、6 条 / 68 秒，
     *  见 `docs/device/actions/feedback.md` 四十），宿主的包连按几下之后后面那些直接被丢。注入侧跑在
     *  `system_server`，用 `ActivityThread.getSystemContext()`（包名 `android`）弹，那个包持有
     *  `UNLIMITED_TOASTS` ⇒ 免限流。同一句话、同一个模块，只是换了发话人。
     *
     *  收方同样是 `inject/KeyHooks` 那个 AMS 广播入口；发送侧（`host/action/CommandFailure`）
     *  照旧 `setPackage(宿主包名)` 定向（理由同上一条）。
     */
    const val ACTION_TOAST = "com.lookie.opluskey.TOAST"

    /** [ACTION_TOAST] 里带「要弹的那句话」。 */
    const val EXTRA_TOAST_TEXT = "toast_text"

    /**
     * 宿主 → 注入侧：**替我把音量设到某一档**（[EXTRA_VOLUME_STREAM] / [EXTRA_VOLUME_INDEX]）。
     *
     *  为什么挪到注入侧做：宿主进程里的 `setStreamVolume` 会被 ColorOS 静默丢掉
     *  （`docs/device/actions/audio.md` 三十四 的实测），只剩「注入音量键」这一条退路 ——
     *  而一颗键就是一整格（量程的 1/16），`±1%` 这种细步进根本走不出来。注入侧跑在
     *  `system_server`，身份是系统本身，那条策略不挡它，于是又能拿到**精确档位**。
     *
     *  与 [ACTION_INJECT_KEY] 同一条通道、同一条纪律：收方是 `inject/KeyHooks` 的 AMS 广播入口，
     *  发送侧 `setPackage(宿主包名)` 定向。带 `FLAG_SHOW_UI` —— 与宿主自己写时同一个口径
     *  （改完看得见那条面板）。
     */
    const val ACTION_SET_VOLUME = "com.lookie.opluskey.SET_VOLUME"

    /** [ACTION_SET_VOLUME] 里带「哪一路流」（`AudioManager.STREAM_*` 的编号）。 */
    const val EXTRA_VOLUME_STREAM = "volume_stream"

    /** [ACTION_SET_VOLUME] 里带「目标档位」（该流的原始下标，不是百分比）。 */
    const val EXTRA_VOLUME_INDEX = "volume_index"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主进程的启动时刻」（同上）。宿主是普通应用进程，重开即可，不必重启手机。 */
    const val EXTRA_HOST_PROC_START = "host_proc_start"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主读到的主开关真值」——页面据此判它是不是读到了真配置。 */
    const val EXTRA_HOST_ENABLED = "host_enabled"

    /**
     * [ACTION_HOST_SERVICE_UP] 里带「宿主**读得懂**那份配置文件」。
     *
     *  这一位与 [EXTRA_HOST_ENABLED] **必须分开**：读不懂时 `read()` 退化成
     *  `Config.DEFAULT`，它的 `enabled` 也是 false —— 与「用户自己关了主开关」在
     *  [EXTRA_HOST_ENABLED] 这一位上**完全同形**。只看后者的话，执行侧一个动作都做不出来
     *  而页面报「主开关已关闭」，把人指去拨一个本来就开着的开关（真机撞过：
     *  `配置正文与 md5 对不上` → 退回默认 → 所有按键都没反应，自检却全绿）。
     *
     *  **缺省按 true 处理**：这一位是本版新加的，对面若是更早的宿主则不带它 —— 那时不该
     *  凭空报一条「配置读取失败」。
     */
    const val EXTRA_HOST_CONFIG_READABLE = "host_config_readable"

    /** [ACTION_HOST_SERVICE_UP] 里带「宿主最后一次执行动作是什么时候」（同上，0 = 从未）。 */
    const val EXTRA_HOST_LAST_ACTION_AT = "host_last_action_at"

    // ── 「自定义按键」页：监听按键（设置页 与 注入侧） ──
    //
    //  一对「开 / 关」+ 一对「听见了」。设置页进页时开、出页时关；注入侧在监听期间把每一颗
    //  按下的键原样报回来（见 `inject/KeyWatch`，**只读旁路**，不改认领与放行）。
    //
    //  注意：[ACTION_KEY_SEEN] 的接收方是**设置页自己**注册的接收器（与设置页同进程），
    //  不经 `GestureReceiver` —— 那边属 `host/`，而 host/ 不许认识界面层
    //  （`docs/architecture/decoupling.md` 动作 1、2 与 `tools/audit-coupling.py`）。

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

    /**
     * [ACTION_LOG_CHUNK] 里带「回包这一侧跑的是**哪一次构建**」（`ModuleInfo.BUILD_ID`）。
     *
     *  三个进程的版本可以不一致 —— 换包没重启时 `system_server` 里还是上一版代码，而它照样
     *  能回日志。收方把这一位写进导出文件头，「宿主 / 注入是旧版」于是不用再靠猜。
     */
    const val EXTRA_LOG_BUILD = "log_build"

    /**
     * [ACTION_LOG_CHUNK] 里带「这一侧缓冲一共几行」。
     *
     *  与「没回包」分开：0 行 = 这个进程在跑、只是还没打过日志；没回包 = 它压根没起来
     *  （或没装模块）。两种情形在排查里含义完全不同。
     */
    const val EXTRA_LOG_LINES = "log_lines"

    // ── 快捷方式：请注入侧（system_server）读**快捷方式服务的活数据** ──
    //
    //  「选择界面」里那份清单在宿主进程读，而 `LauncherApps` 对「钉住」这一档按**调用方**过滤：
    //  宿主不是桌面宿主读不到，换成系统身份（uid 1000）走同一个 API 也是同一结果（真机实测
    //  `动态 3 / 清单 0 / 钉住 0`）。注入侧就住在 `system_server` 里，能直接读那个服务手上的活数据
    //  （`ShortcutUser` 里的 `ShortcutInfo`，含 `isPinned`）—— 见 `ShortcutRead`。
    //
    //  一问一答：[ACTION_SHORTCUT_QUERY] 由宿主发，注入侧回 [ACTION_SHORTCUT_RESULT]，
    //  带 [EXTRA_SHORTCUT_REQ] 配对（一次可能并发几条）。

    /** 宿主 → 注入侧：**这个包在桌面上钉了哪几条**（按系统身份读）。 */
    const val ACTION_SHORTCUT_QUERY = "com.lookie.opluskey.SHORTCUT_QUERY"

    /** 注入侧 → 宿主：上面那条的结果（`id` 与名字各一份列表，等长）。 */
    const val ACTION_SHORTCUT_RESULT = "com.lookie.opluskey.SHORTCUT_RESULT"

    /** 两条都带：**这一趟的编号**（谁发谁收靠它配对）。 */
    const val EXTRA_SHORTCUT_REQ = "shortcut_req"

    /** [ACTION_SHORTCUT_QUERY] 里带「读哪个包」。 */
    const val EXTRA_SHORTCUT_PKG = "shortcut_pkg"

    /** [ACTION_SHORTCUT_QUERY] 里带「哪个用户」（0 = 主用户，999 = 分身）。 */
    const val EXTRA_SHORTCUT_USER = "shortcut_user"

    /**
     * [ACTION_SHORTCUT_RESULT] 里带「快捷方式的 id」与「名字」两份列表。
     *
     *  两份分开传而不是拼一个字符串：名字里什么字符都可能有（中文、冒号、逗号、换行），
     *  拼了就得自己定一套转义 —— 而这条通道只用一次，不值当。
     */
    const val EXTRA_SHORTCUT_IDS = "shortcut_ids"

    /** 见 [EXTRA_SHORTCUT_IDS]。 */
    const val EXTRA_SHORTCUT_LABELS = "shortcut_labels"

    // ── 注入侧代发网络请求：宿主没 INTERNET，只有 system_server 有 ──
    //
    //  宿主的清单里**没有** `INTERNET`（`reference/gesture.apk` 的 49 条权限里只有
    //  `ACCESS_NETWORK_STATE`），而界面代码跑在宿主进程 —— 所以「检查更新」这类要出网的事，
    //  只能请注入侧代取。能力本身在 `inject/NetChannel`，实测结论见 `docs/device/lsposed.md` 四十一
    //  （能连，但必须先绑默认网络再解析 DNS）。
    //
    //  一问一答：[ACTION_NET_REQUEST] 由宿主发（同 uid 的设置页也行），注入侧取回正文后
    //  用 [ACTION_NET_RESULT] 回给宿主。**只做 GET、只认 https** —— 这是一个「取一段文本」的
    //  通道，不是通用代理。

    /** 宿主 → 注入侧：**替我取一个 URL**（GET，只接受 https）。 */
    const val ACTION_NET_REQUEST = "com.lookie.opluskey.NET_REQUEST"

    /** 注入侧 → 宿主：上面那条的结果。 */
    const val ACTION_NET_RESULT = "com.lookie.opluskey.NET_RESULT"

    /** 两条都带：**这一趟的编号** —— 一次可能并发好几条，靠它配对（谁发谁收）。 */
    const val EXTRA_NET_ID = "net_id"

    /** [ACTION_NET_REQUEST] 里带「要取的地址」。 */
    const val EXTRA_NET_URL = "net_url"

    /** [ACTION_NET_RESULT] 里带「HTTP 状态码」；`0` = 压根没连上（原因见 [EXTRA_NET_ERROR]）。 */
    const val EXTRA_NET_STATUS = "net_status"

    /** [ACTION_NET_RESULT] 里带「正文」（超过 [NET_BODY_LIMIT] 的部分截掉）。 */
    const val EXTRA_NET_BODY = "net_body"

    /** [ACTION_NET_RESULT] 里带「失败原因」；成功时是空串。 */
    const val EXTRA_NET_ERROR = "net_error"

    /**
     * 正文上限（字符数）—— 一次广播塞几百 KB 会撞 binder 事务上限。
     *
     *  检查更新只要几 KB（releases 那个 JSON），32 KB 留了十倍余量；超了截断并把
     *  `error` 写成「正文超长已截断」，让调用方知道这不是完整正文。
     */
    const val NET_BODY_LIMIT = 32 * 1024

    /** 这一趟最多等多久（毫秒）—— 连接与读取各用这一个值。 */
    const val NET_TIMEOUT_MS = 8_000
}
