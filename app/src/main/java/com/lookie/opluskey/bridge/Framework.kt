package com.lookie.opluskey.bridge

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.DeviceSupport
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.core.reflect.Reflect
import java.lang.reflect.Method

/** ColorOS 侧键的接管点。 */
internal object Framework {
    /**
     * 一档 ROM 的接管点候选 —— **加一档就在这里加一个 [HookSet]**，别的文件一个字不用改。
     *
     * 一、7 要求候选类名与方法名只写在本文件；这里再加一层版本维度。加它的直接理由：
     * 同一层里的多个候选**不该同时装**（两个都命中时各装一个接管点，一次按压会被喂判定器两遍，
     * 双击变「双击 + 单击」），而「哪些候选属于哪一版」只有真机验过才知道 —— 按档写下来，
     * 就不必在一台机器上试别版的候选。
     *
     * 键与 `core/model/RomProfile` 同一个（`romSeries`）：适配一版新 ROM 要加的是**两张表**
     * （本文件 + 那张反射面 / 内置命令表），两边一起加档。**档位名与验证记录只在
     * `RomProfile` 那一处登记**（本表按 `romSeries` 反查，见 [HookSet.label]）—— 同一句话在两张表里
     * 各写一遍时，改一处漏一处只表现为日志里报着过期的档位名，谁也不会发现。
     *
     * @param romSeries 适用的 ROM 系列（`DeviceSupport.romSeriesOf` 的口径）
     * @param strategyCandidates ColorOS 侧键专用策略类（外层 `actionInterceptKeyBeforeQueueing`）。
     *   不挂 `...$2`：它是外层方法的纯转发（12 条指令，恒 `preCondition` 为 true），挂它 0 收益，
     *   只会让同一次按键被回调两次（见 `docs/device-findings.md` §14.2）
     * @param strategyEntries 策略类上要挂的入口方法名（精确匹配，见 `docs/device-findings.md` §2 / §14）。
     *   不能退化成 `contains("interceptKeyBefore")`：`actionIntercept…` 里的 `Intercept` 是大写 I，
     *   小写 token 一个都匹配不上（策略类层静默归零）；反而会命中 AOSP 的
     *   `interceptKeyBeforeDispatching`（派发层），重注入的侧键会被再认领一次（幽灵长按）
     * @param pmExtCandidates ColorOS 的 PhoneWindowManager 覆写实现（方法名带 `override` 前缀）
     * @param pmExtEntries 覆写层要挂的入口方法名（`override` 前缀那套命名）
     * @param pmCandidates AOSP 原生 PhoneWindowManager（兜底）
     * @param pmEntries AOSP 原生入口
     * @param amsCandidate AMS 的广播入口 —— 本模块「主开关 / 配置 / 状态栏指令 / 小窗指令 /
     *   链路自检问」全从这里收
     * @param amsBroadcastEntries AMS 广播入口的方法名（精确匹配），**按优先级排列**：
     *   `broadcastIntentWithFeature` 是新版入口，老版本只有 `broadcastIntent`；[findBroadcastPoints]
     *   只取第一个存在的名字（前者会转调后者，两个都挂等于同一条广播处理两遍）。不认模糊匹配 ——
     *   抓错同族方法（`broadcastIntentLocked` / `broadcastIntentInPackage`）会把我们**自己发出去**的
     *   广播也拦一遍
     * @param hostKeyService 其余场景的宿主服务 —— 也是「启动它把宿主抬出可冻结范围」用的那个服务
     * @param hostStartAppService 「打开应用」场景的宿主服务（框架按场景表里的 `click_action_info` 绑它）
     * @param hostHandlerSuffixes 宿主服务内部 Handler 的类名后缀**候选**（`XxxService$a` 里的 `a`）。
     *   它是**混淆名**：同一 ROM 内稳定、跨 ROM 不可预期。所以按候选写、运行时逐个试（与
     *   [strategyCandidates] 同一套口径），命中的那一个由 `findHostHandlerClass` 打进日志 ——
     *   拿单值去猜，换一版 ROM 就只剩一句「没找到 Handler 类」，而那是「服务类没找到」还是
     *   「后缀变了」在日志上看不出来（五、2）
     * @param hostHandleMessage 那个内部 Handler 上手势消息的落点方法名
     * @param hostSceneDispatchWhat 该 Handler 上「系统场景派发」那条消息的 `what`（真机实测 = 1）。
     *   收它的时候宿主要按系统当前场景去开页面 / 起服务，模块接管侧键后必须把它吞掉
     * @param hostSettingsBaseActivity 宿主侧键设置页的**基类**（页面骨架在这里，换页回调按它过滤实例）
     * @param hostSettingsActivity 侧键设置页的**实际** Activity（基类的子类）。
     *   「重启服务」杀掉本进程之后要靠它把页面重新拉起来：那一枪是进程死了之后由系统投递的，
     *   拿不到当前 Activity，只能有一个写死的组件名兜底
     *
     *  与 `core/model/RomProfile` 的表同理：参数多正是「一档要登记的那些名字」的样子，
     *  拆开会让「加一档」变成改好几处（`LongParameterList` 因此就地抑制）。
     */
    @Suppress("LongParameterList")
    private class HookSet(
        val romSeries: String,
        val strategyCandidates: List<String>,
        val strategyEntries: Set<String>,
        val pmExtCandidates: List<String>,
        val pmExtEntries: Set<String>,
        val pmCandidates: List<String>,
        val pmEntries: Set<String>,
        val amsCandidate: String,
        val amsBroadcastEntries: Set<String>,
        val hostKeyService: String,
        val hostStartAppService: String,
        val hostHandlerSuffixes: List<String>,
        val hostHandleMessage: String,
        val hostSceneDispatchWhat: Int,
        val hostSettingsBaseActivity: String,
        val hostSettingsActivity: String,
    ) {
        /**
         * 给人看的档位名 —— 从 `core/model/RomProfile` 那一档反查（**一处登记**）。
         *
         *  反查不到时退回 `romSeries` 本身：这只可能是「这一档只在接管点表里、落点表里没登记」，
         *  那种不一致由 [describeTables] 在安装期报出来 —— 这里不猜、也不抄一份名字出来遮住它。
         */
        val label: String get() = RomProfile.bySeries(romSeries)?.label ?: romSeries
    }

    /**
     * **各版本的接管点候选 —— 加一档就在这里加一个 [HookSet]**。
     *
     * 候选类的来路见 `docs/device-findings.md`（§2 / §14 的接管点、§六 的宿主侧落点）。
     * 加档之后要在真机上把每一层重新验一遍 —— 这些类与方法都是 ROM 私有的，换版本会整体换名换形。
     */
    private val HOOK_SETS: List<HookSet> =
        listOf(
            HookSet(
                romSeries = "17.0.0",
                strategyCandidates =
                    listOf(
                        "com.android.server.policy.StrategyActionButtonKeyLaunchApp",
                        "com.oplus.server.policy.StrategyActionButtonKeyLaunchApp",
                    ),
                strategyEntries = setOf("actionInterceptKeyBeforeQueueing"),
                pmExtCandidates = listOf("com.android.server.policy.PhoneWindowManagerExtImpl"),
                pmExtEntries =
                    setOf(
                        "overrideInterceptKeyBeforeQueueing",
                        "interceptKeyBeforeQueueing",
                    ),
                pmCandidates = listOf("com.android.server.policy.PhoneWindowManager"),
                pmEntries = setOf("interceptKeyBeforeQueueing"),
                amsCandidate = "com.android.server.am.ActivityManagerService",
                amsBroadcastEntries = setOf("broadcastIntentWithFeature", "broadcastIntent"),
                hostKeyService = "com.oplus.gesture.server.ActionButtonKeyService",
                hostStartAppService = "com.oplus.gesture.actionbuttonkey.quicklaunch.StartAppService",
                // 混淆名，本机实测是 `a`。换 ROM 时这里加一个候选即可（别的文件一个字不用改）
                hostHandlerSuffixes = listOf("a"),
                hostHandleMessage = "handleMessage",
                hostSceneDispatchWhat = 1,
                hostSettingsBaseActivity = "com.oplus.gesture.actionbuttonkey.BaseActionActivity",
                hostSettingsActivity = "com.oplus.gesture.actionbuttonkey.ActionButtonSettingsActivity",
            ),
        )

    /**
     * 本机这一档 —— 未适配时**照最后一档试**并打一行 `w`。
     *
     * 为什么这里可以「猜」：候选落空只表现为**查找不到类 / 方法**（记 `w` 后逐层降级），
     * 不会去动任何东西；适配清单不拦注入，所以任何 ROM 上都照这一档试。
     * 与内置命令落点那条「不猜」的区别就在这（那边猜错会真去起别的服务），见 `core/model/RomProfile`。
     *
     * `HOOK_SETS` 是常量表、不会是空的；真为空时这一档全落空、各层各记一行 `w`（五、3 的口径）。
     */
    private val hooks: HookSet by lazy {
        val rom = DeviceSupport.currentRomSeries
        HOOK_SETS.firstOrNull { it.romSeries == rom }
            ?: HOOK_SETS.last().also {
                // 落空只表现为「找不到类 / 方法」（各层各记一行 w 后降级），不会去动别的东西 ——
                //  这一行是排查「按了没反应」时要看的，所以写清「照哪一档试」。
                XLog.w("本机 ROM $rom 不在接管点档位表里 ⇒ 按 ${it.label} 的候选试（落空只表现为找不到类 / 方法）")
            }
    }

    // 宿主（`com.oplus.gesture`）侧的接管点：系统自己判好的侧键手势真正的落点。
    //
    // 注意：这两个服务名与那个混淆 Handler 的类名**只有本文件写**（一、7）：它们散在
    // `host/SideKeyGestureHooks`、`host/GestureReceiver`、`inject/input/GestureForwarder`
    // 三处各写一遍，改 ROM 时漏改任何一处，表现都是「按键拦下来了但什么都不做」，
    // 而且从日志上看不出漏在哪一层。
    //
    //  下面这几个都从本机那一档里取（见 [hooks]），所以「加一档」时它们跟着走，调用点不用改。

    /** 其余场景的宿主服务 —— 也是「启动它把宿主抬出可冻结范围」用的那个服务。 */
    val HOST_KEY_SERVICE: String get() = hooks.hostKeyService

    /** 「打开应用」场景的宿主服务（框架按场景表里的 `click_action_info` 绑它）。 */
    val HOST_START_APP_SERVICE: String get() = hooks.hostStartAppService

    /** 宿主服务内部 Handler 的类名后缀候选（`XxxService$a` 里的 `a`；见 [HookSet] 的说明）。 */
    val HOST_HANDLER_SUFFIXES: List<String> get() = hooks.hostHandlerSuffixes

    /** 那个内部 Handler 上手势消息的落点方法名。 */
    val HOST_HANDLE_MESSAGE: String get() = hooks.hostHandleMessage

    /** 宿主服务内部 Handler 上「系统场景派发」那条消息的 `what`（真机实测 = 1）。 */
    val HOST_SCENE_DISPATCH_WHAT: Int get() = hooks.hostSceneDispatchWhat

    /** 宿主侧键设置页的**基类**（页面骨架在这里，换页回调按它过滤实例）。 */
    val HOST_SETTINGS_BASE_ACTIVITY: String get() = hooks.hostSettingsBaseActivity

    /** 侧键设置页的**实际** Activity（基类的子类）—— 「重启服务」之后靠它把页面拉回来。 */
    val HOST_SETTINGS_ACTIVITY: String get() = hooks.hostSettingsActivity

    /**
     * 找宿主服务内部那个 Handler 类（`XxxService$后缀`）—— 侧键手势消息真正的落点。
     *
     *  后缀是混淆名（[HOST_HANDLER_SUFFIXES]），所以按候选**逐个试**（一、7 的候选表 + 五、3 的
     *  「对不上就退到下一层候选」）：命中的那一行记 `w` 说明用的是哪一个；全部落空时把试过的后缀
     *  一起打出来。这两行的区别正是「服务类没找到」与「服务类在、Handler 后缀变了」的分界 ——
     *  少了它，换一版 ROM 之后只能看到一句「没找到 Handler 类」，断在哪一环说不出来（五、2）。
     *
     * @param serviceClass 宿主服务类的全限定名（[HOST_KEY_SERVICE] / [HOST_START_APP_SERVICE]）
     * @return 命中的类；一个都没命中返回 null（调用方只记日志、不硬猜一个名字去挂）
     */
    fun findHostHandlerClass(
        loader: ClassLoader,
        serviceClass: String,
    ): Class<*>? {
        for (suffix in hooks.hostHandlerSuffixes) {
            val cls = Reflect.findClass(loader, "$serviceClass\$$suffix")
            if (cls != null) {
                XLog.w("宿主内部 Handler 命中：$serviceClass\$$suffix")
                return cls
            }
        }
        XLog.w(
            "没找到 $serviceClass 的内部 Handler 类 —— 试过的后缀：" +
                hooks.hostHandlerSuffixes.joinToString(", ") { "\$$it" } +
                "（ROM 改版会换掉这个混淆名）",
        )
        return null
    }

    /**
     * 「本次开机这一档 ROM 在**三张表**里都登记了吗」—— 安装期打一行 `w`（判据行）。
     *
     * 三张表：`DeviceSupport.ADAPTED`（**验过没有**，不拦注入）、本文件的 [HOOK_SETS]（接管点候选）、
     * `RomProfile.PROFILES`（内置命令落点 + 反射面）。加一版 ROM 要往三处各加一行，而**漏一处不会
     * 编译报错**，三种漏法的表现分别是「一直显示等待适配」「接管点一个都没找到」「照别版的落点去起
     * 服务/读反射面」—— 都不好查。这一行把「哪一档只在部分表里」直接说出来，省掉一轮猜。
     *
     * @return 一句话；三张表一致时结尾是「三张表一致」，不一致时列出每个系列缺哪张表
     */
    fun describeTables(): String {
        val gates = DeviceSupport.adaptedSeries().sorted()
        val hooks = HOOK_SETS.map { it.romSeries }.distinct().sorted()
        val landing = RomProfile.series.sorted()
        val head =
            "ROM 档位对账：本机 ${DeviceSupport.currentRomSeries.ifEmpty { "读不出来" }}" +
                "；适配清单[${gates.joinToString(", ")}]" +
                "；接管点候选[${hooks.joinToString(", ")}]" +
                "；落点表[${landing.joinToString(", ")}]"
        val gaps =
            (gates + hooks + landing).distinct().sorted().mapNotNull { series ->
                val missing =
                    buildList {
                        if (series !in gates) add("适配清单")
                        if (series !in hooks) add("接管点候选")
                        if (series !in landing) add("落点表")
                    }
                if (missing.isEmpty()) null else "$series 缺 ${missing.joinToString("/")}"
            }
        return if (gaps.isEmpty()) "$head（三张表一致）" else "$head —— 不一致：${gaps.joinToString("；")}"
    }

    /**
     * 找 AMS 的广播入口方法（[AMS_CANDIDATE] 上的 [AMS_BROADCAST_ENTRIES]，精确匹配）。
     *
     * **同一族里只挂最靠里的那一个**：新版 AMS 上 `broadcastIntent(...)` 内部会转调
     * `broadcastIntentWithFeature(...)`，两个都挂就是同一条广播被 [KeyHooks] 处理两遍
     * （状态栏 toggle、探针 `call` 这类非幂等动作会执行两次）。所以按候选表顺序取
     * **第一个存在的名字**，只返回这个名字下的全部重载；老 ROM 上没有 WithFeature 时自然退到
     * `broadcastIntent`。
     *
     * @return 命中的方法；找不到返回空表（调用方按「三条通道整体失效」记日志，不硬猜）
     */
    fun findBroadcastPoints(loader: ClassLoader): List<Method> {
        val cls = Reflect.findClass(loader, hooks.amsCandidate)
        if (cls == null) {
            XLog.w("没找到 ${hooks.amsCandidate} —— 广播入口接管点落空")
            return emptyList()
        }
        val all = Reflect.methodsOf(cls).filter { !it.isSynthetic && !it.name.startsWith("access$") }
        val hit = all.filter { it.name in hooks.amsBroadcastEntries }
        if (hit.isEmpty()) {
            val near =
                all
                    .map { it.name }
                    .filter { it.contains("broadcastIntent") }
                    .distinct()
            XLog.w(
                "${hooks.amsCandidate} 上没有 ${hooks.amsBroadcastEntries.joinToString("/")}()" +
                    "（同族方法：${near.joinToString(", ").ifEmpty { "一个都没有" }}）",
            )
            return emptyList()
        }
        val terminal = hooks.amsBroadcastEntries.firstOrNull { name -> hit.any { it.name == name } }
        val chosen = hit.filter { it.name == terminal }
        // 判据行：挂的是哪一族决定了「一条广播走几次」，排查重复派发时先看这一行
        XLog.w("广播入口：挂 $terminal（${chosen.size} 个重载；同族其余名字不挂，避免同一条广播走两遍）")
        return chosen
    }

    /**
     * 找侧键的接管点。去重口径是 **owner 类 + Method 对象 + kind**（[InterceptionPoint] 是 data
     * class，相等即这三项都相同），所以同一个类上同一个 Method 不会装两次；但同名的**不同**
     * Method（重载，或父子类各声明一个）仍会各装一次 —— 后者由 [collect] 里的 PM_EXT 同层互斥兜住。
     *
     * @return 去重后的接管点；调用方逐个尝试安装，装到哪个算哪个
     */
    fun findSideKeyPoints(loader: ClassLoader): List<InterceptionPoint> {
        val out = LinkedHashSet<InterceptionPoint>()

        // 逐层降级而非全装：同一次按压若被多层各喂一次判定器，去重窗只盖得住 instantSingle
        // 那一支，双击会变成「双击 + 单击」。**前一层收到了就不再往下走**。
        if (collectLayer(out, loader, Layer.STRATEGY)) return out.toList()
        if (collectLayer(out, loader, Layer.PM_EXT)) return out.toList()
        collectLayer(out, loader, Layer.AOSP_PM)
        return out.toList()
    }

    /**
     * 收一层的接管点候选 —— **层内第一个命中的类就停**。
     *
     *  层与层之间的逐层降级在上面那三行；层内也要停：同一层的几个候选是同一个类的两种包名写法
     *（AOSP / OPLUS），两个都存在时各装一个接管点，同样会把一次按压喂判定器两遍 —— 这一层原来
     *  漏了这道闸（只挡住了「层与层之间」，没挡住「同层的两个候选」）。
     *
     * @return 这一层有没有收到接管点
     */
    private fun collectLayer(
        out: MutableSet<InterceptionPoint>,
        loader: ClassLoader,
        layer: Layer,
    ): Boolean {
        for (name in layer.candidates(hooks)) {
            val cls = Reflect.findClass(loader, name) ?: continue
            XLog.w("找到 ${layer.label}：$name")
            collect(out, cls, layer.entries(hooks), layer.kind)
            if (out.isNotEmpty()) return true
        }
        return false
    }

    /**
     * 接管点的一层 —— 候选类名 / 入口方法名 / 判定 kind 都从本机那一档（[hooks]）里取。
     *
     *  抽成枚举是为了让 [findSideKeyPoints] 那三行读起来就是「逐层降级」本身；每层取哪几个字段
     *  写死在下面两个 `when` 里，加一层要动的是这里（而不是再复制一段循环）。
     */
    private enum class Layer(
        val label: String,
        val kind: InterceptionPoint.Kind,
    ) {
        /** ColorOS 侧键专用策略类。 */
        STRATEGY("ColorOS 侧键策略类", InterceptionPoint.Kind.OPLUS_STRATEGY),

        /** ColorOS 对 PhoneWindowManager 的覆写。 */
        PM_EXT("ColorOS PM 覆写类", InterceptionPoint.Kind.OPLUS_PM_EXT),

        /** AOSP 原生 PhoneWindowManager（兜底）。 */
        AOSP_PM("AOSP PhoneWindowManager（兜底）", InterceptionPoint.Kind.AOSP_PM),
        ;

        fun candidates(set: HookSet): List<String> =
            when (this) {
                STRATEGY -> set.strategyCandidates
                PM_EXT -> set.pmExtCandidates
                AOSP_PM -> set.pmCandidates
            }

        fun entries(set: HookSet): Set<String> =
            when (this) {
                STRATEGY -> set.strategyEntries
                PM_EXT -> set.pmExtEntries
                AOSP_PM -> set.pmEntries
            }
    }

    /**
     * 把 [cls] 上名为 [entries] 里的方法收进 [out]（精确匹配，见 [STRATEGY_ENTRIES]）。
     * 找不到时打 W 级日志（注入侧 `XLog.i` 在 LSPosed 日志页里看不到，见 `docs/device-findings.md`
     * §十五），并列出同类命名的方法名，让「层找到但方法改名」与「类没找到」一眼可分。
     */
    private fun collect(
        out: MutableSet<InterceptionPoint>,
        cls: Class<*>,
        entries: Set<String>,
        kind: InterceptionPoint.Kind,
    ) {
        val all = Reflect.methodsOf(cls).filter { !it.isSynthetic && !it.name.startsWith("access$") }
        val hit = all.filter { it.name in entries }
        if (hit.isEmpty()) {
            val near = all.map { it.name }.filter { it.contains("ntercept") }.distinct()
            XLog.w(
                "${cls.name} 上没有 ${entries.joinToString("/")}()，本层跳过" +
                    "（同族方法：${near.joinToString(", ").ifEmpty { "一个都没有" }}）",
            )
            return
        }
        // 只有 PM_EXT 这一族做「同层互斥」：它的两个候选名是「覆盖写法 + 父类实现」，
        //  `Reflect.methodsOf` 沿父类链收集方法，父子类各声明一个时会同时命中 —— 两个都装
        //  会把一次按压喂判定器两遍（双击被吃成两次单击、长按枪反复作废）。**其余 kind 一律不做**：
        //  `AMS_BROADCAST_ENTRIES`（`broadcastIntent` 的多个重载）每个重载都必须装，漏一个就等于
        //  收不到配置广播；`STRATEGY_ENTRIES` 只有一个名字、没有同层重复可言，也不能按名字去砍。
        if (kind == InterceptionPoint.Kind.OPLUS_PM_EXT) {
            collectPmExtExclusive(out, cls, entries, hit, kind)
            return
        }
        for (m in hit) out += InterceptionPoint(cls, m, kind)
    }

    /**
     * [InterceptionPoint.Kind.OPLUS_PM_EXT] 的同层互斥：同一个类上命中多个候选名时，按
     * [HookSet.pmExtEntries] 的书写次序只留第一个名字下的方法，其余整个丢弃并打 W 级日志（判据行）。
     *
     * 次序取自 `setOf` 的插入序（`LinkedHashSet`）：第一个是 ColorOS 的覆盖写法，第二个是它父类的
     * 实现 —— 保留覆盖、丢掉被覆盖的那个。互斥只按**名字**做，同一个名字下的多重载仍全收。
     */
    private fun collectPmExtExclusive(
        out: MutableSet<InterceptionPoint>,
        cls: Class<*>,
        entries: Set<String>,
        hit: List<Method>,
        kind: InterceptionPoint.Kind,
    ) {
        val winner = entries.firstOrNull { name -> hit.any { it.name == name } } ?: return
        val dropped = hit.filter { it.name != winner }.map { it.name }.distinct()
        if (dropped.isNotEmpty()) {
            XLog.w(
                "${cls.name} 同层多命中：按 ${entries.joinToString("/")} 的次序只留 $winner()，" +
                    "已丢弃 ${dropped.joinToString(", ")}" +
                    "（都装会把一次按压喂判定器两遍：双击被吃成两次单击、长按枪反复作废）",
            )
        }
        for (m in hit) if (m.name == winner) out += InterceptionPoint(cls, m, kind)
    }

    /**
     * **执行落点**上的框架名字 —— 类名 / 方法名 / 服务名，全项目只在这里写一次。
     *
     *  与上面 `HookSet` 那张**接管点**表分开：接管点是「按键从哪进来」（有 kind、能按 ROM 降级、
     *  不命中就不接管）；这里是「动作往哪发」（`ServiceManager` / `LocalServices` 那几个入口，
     *  没有第二个可选名字）。两者都在 一、7 的管辖内 —— 名字只写一处，ROM 改了改这里。
     *
     *  为什么不散在实现里：这些名字分布在 `StatusBarBridge` / `KeyInjector` 两处实现里，
     *  同一串 `android.os.ServiceManager` 各写了三遍。换一版 ROM 时没人能一眼看全「我们要碰的
     *  框架名字有哪些」；而漏改一处的表现是那条链路**静默失效**（反射查不到就记日志返回，
     *  不是崩溃）—— 正是 `docs/device-findings.md` 十九 那种最难查的形态。
     */
    internal object Calls {
        // ── ServiceManager：按服务名换 binder 的通用入口（AOSP 公开，一直没改过名） ──

        /** `android.os.ServiceManager`。 */
        const val SERVICE_MANAGER = "android.os.ServiceManager"

        /** `ServiceManager.getService(String)`。 */
        const val GET_SERVICE = "getService"

        /** 状态栏服务在 `ServiceManager` 里登记的名字。 */
        const val SERVICE_STATUS_BAR = "statusbar"

        /** 窗口服务在 `ServiceManager` 里登记的名字。 */
        const val SERVICE_WINDOW = "window"

        /** 输入服务在 `ServiceManager` 里登记的名字。 */
        const val SERVICE_INPUT = "input"

        // ── 状态栏 / 全局菜单那几条指令（ROM 私有，改名的风险最高） ──

        /** `com.android.server.statusbar.StatusBarManagerInternal`。 */
        const val STATUS_BAR_INTERNAL = "com.android.server.statusbar.StatusBarManagerInternal"

        /** `com.android.server.LocalServices` —— 拿上面那个 internal 接口的入口。 */
        const val LOCAL_SERVICES = "com.android.server.LocalServices"

        /** 状态栏服务上「收起 / 展开快捷面板」那一族。 */
        const val TOGGLE_PANEL = "togglePanel"

        /** 展开「设置」面板（带一个 `String` 参数）。 */
        const val EXPAND_SETTINGS_PANEL = "expandSettingsPanel"

        /** 旧路径：从 `mBar` 上展开设置面板。 */
        const val ANIMATE_EXPAND_SETTINGS_PANEL = "animateExpandSettingsPanel"

        /** 状态栏服务内部持有的实际实现字段。 */
        const val FIELD_M_BAR = "mBar"

        /** 「最近任务」开关（在 `StatusBarManagerInternal` 上）。 */
        const val TOGGLE_RECENT_APPS = "toggleRecentApps"

        /** 窗口服务内部持有的 `PhoneWindowManager` 字段。 */
        const val FIELD_M_POLICY = "mPolicy"

        /** 长按电源那个全局菜单（走 `PhoneWindowManager`）。 */
        const val SHOW_GLOBAL_ACTIONS = "showGlobalActions"

        // ── 输入注入（AOSP 公开） ──

        /** `android.view.KeyEvent` —— 只为 `injectInputEvent` 拿它的 `ACTION_*` 常量。 */
        const val KEY_EVENT = "android.view.KeyEvent"

        /** `android.view.InputEvent` —— `injectInputEvent` 第一个形参的类型。 */
        const val INPUT_EVENT = "android.view.InputEvent"

        /** `InputManager.injectInputEvent(InputEvent, int)`。 */
        const val INJECT_INPUT_EVENT = "injectInputEvent"

        /** `android.hardware.input.IInputManager$Stub`（`$` 是内部类分隔符）。 */
        const val I_INPUT_MANAGER_STUB = "android.hardware.input.IInputManager\$Stub"

        /** Binder stub 的 `asInterface`。 */
        const val AS_INTERFACE = "asInterface"
    }

    /**
     * 一个可安装的接管点。
     *
     * [kind] 决定怎么判定「这下是不是侧键」（实现见 `inject/SideKeyInterceptor.decide`）：
     * `OPLUS_STRATEGY` 按**全部键**的 `acceptedKeyCodes` 查（含侧键的 735/780/781/782，也含自定义键）；
     * `OPLUS_PM_EXT` 与 `AOSP_PM` 只按**原始码**查（侧键只有 735），免得同一次按键被两条路各判一次。
     * 两张查询表都来自当前配置（`Config.keyForCode` / `Config.keyForRawCode`）——
     * `KeyId` 上**没有**「按键码反查」的接口（那只认得到内置键，自定义键会静默落空）。
     */
    data class InterceptionPoint(
        val owner: Class<*>,
        val method: Method,
        val kind: Kind,
    ) {
        override fun toString(): String = "${owner.name}#${method.name}"

        enum class Kind {
            /** ColorOS 侧键专用策略类。 */
            OPLUS_STRATEGY,

            /** ColorOS 对 PhoneWindowManager 的覆写。 */
            OPLUS_PM_EXT,

            /** AOSP 原生 PhoneWindowManager（兜底）。 */
            AOSP_PM,
        }
    }
}
