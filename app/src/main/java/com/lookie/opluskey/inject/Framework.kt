package com.lookie.opluskey.inject

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.DeviceSupport
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
     * （本文件 + 那张反射面 / 内置命令表），两边一起加档。
     *
     * @param romSeries 适用的 ROM 系列（`DeviceSupport.romSeriesOf` 的口径）
     * @param label 给人看的档位名
     * @param verified 真机验证记录（机型 + 日期）
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
     * @param amsBroadcastEntries AMS 广播入口的方法名（精确匹配）。`broadcastIntentWithFeature`
     *   是新版本入口，老版本只有 `broadcastIntent`，两个都收；但不认模糊匹配 —— 抓错同族方法
     *   （`broadcastIntentLocked` / `broadcastIntentInPackage`）会把我们**自己发出去**的广播也拦一遍
     * @param hostKeyService 其余场景的宿主服务 —— 也是「启动它把宿主抬出可冻结范围」用的那个服务
     * @param hostStartAppService 「打开应用」场景的宿主服务（框架按场景表里的 `click_action_info` 绑它）
     * @param hostHandlerSuffix 宿主服务内部 Handler 的类名后缀（`XxxService$a`；混淆名，同一 ROM 内稳定）
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
        val label: String,
        val verified: String,
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
        val hostHandlerSuffix: String,
        val hostHandleMessage: String,
        val hostSceneDispatchWhat: Int,
        val hostSettingsBaseActivity: String,
        val hostSettingsActivity: String,
    )

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
                label = "ColorOS 17.0.0",
                verified = "OnePlus 15（PLK110）/ 2026-10-13",
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
                hostHandlerSuffix = "a",
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
     * 不会去动任何东西；而 debug 包关掉了适配闸，开发要在任意 ROM 上能试。
     * 与内置命令落点那条「不猜」的区别就在这（那边猜错会真去起别的服务），见 `core/model/RomProfile`。
     *
     * `HOOK_SETS` 是常量表、不会是空的；真为空时这一档全落空、各层各记一行 `w`（五、3 的口径）。
     */
    private val hooks: HookSet by lazy {
        val rom = DeviceSupport.currentRomSeries
        HOOK_SETS.firstOrNull { it.romSeries == rom }
            ?: HOOK_SETS.last().also {
                XLog.w("本机 ROM $rom 不在接管点档位表里 ⇒ 按 ${it.label} 的候选试（适配闸仍会拦住清单外的机器）")
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

    /** 宿主服务内部 Handler 的类名后缀（`XxxService$a`；混淆名，同一 ROM 内稳定）。 */
    val HOST_HANDLER_SUFFIX: String get() = hooks.hostHandlerSuffix

    /** 那个内部 Handler 上手势消息的落点方法名。 */
    val HOST_HANDLE_MESSAGE: String get() = hooks.hostHandleMessage

    /** 宿主服务内部 Handler 上「系统场景派发」那条消息的 `what`（真机实测 = 1）。 */
    val HOST_SCENE_DISPATCH_WHAT: Int get() = hooks.hostSceneDispatchWhat

    /** 宿主侧键设置页的**基类**（页面骨架在这里，换页回调按它过滤实例）。 */
    val HOST_SETTINGS_BASE_ACTIVITY: String get() = hooks.hostSettingsBaseActivity

    /** 侧键设置页的**实际** Activity（基类的子类）—— 「重启服务」之后靠它把页面拉回来。 */
    val HOST_SETTINGS_ACTIVITY: String get() = hooks.hostSettingsActivity

    /**
     * 找 AMS 的广播入口方法（[AMS_CANDIDATE] 上的 [AMS_BROADCAST_ENTRIES]，精确匹配）。
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
        }
        return hit
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
     * 一个可安装的接管点。
     *
     * [kind] 决定怎么判定「这下是不是侧键」：[Kind.OPLUS_STRATEGY] 走白名单
     * （`KeyId.fromAnyKeyCode`，含 735/780/781/782）；[Kind.OPLUS_PM_EXT] 与 [Kind.AOSP_PM]
     * 只认原始码（`KeyId.fromKeyCode`，仅 735），免得同一次按键被两条路各判一次。
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
