package com.lookie.opluskey.inject

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect
import java.lang.reflect.Method

/** ColorOS 侧键的接管点。 */
internal object Framework {

    /**
     * ColorOS 侧键专用策略类（外层 `actionInterceptKeyBeforeQueueing`）。
     *
     * 不挂 `...$2`：它是外层方法的纯转发（12 条指令，恒 `preCondition` 为 true），
     * 挂它 0 收益，只会让同一次按键被回调两次（见 `docs/device-findings.md` §14.2）。
     */
    private val STRATEGY_CANDIDATES = listOf(
        "com.android.server.policy.StrategyActionButtonKeyLaunchApp",
        "com.oplus.server.policy.StrategyActionButtonKeyLaunchApp",
    )

    /**
     * 各层唯一要挂的入口方法名（精确匹配，见 `docs/device-findings.md` §2 / §14）。
     * 不能退化成 `contains("interceptKeyBefore")`：`actionIntercept…` 里的 `Intercept` 是
     * 大写 I，小写 token 一个都匹配不上（策略类层静默归零）；反而会命中 AOSP 的
     * `interceptKeyBeforeDispatching`（派发层），重注入的侧键会被再认领一次（幽灵长按）。
     */
    private val STRATEGY_ENTRIES = setOf("actionInterceptKeyBeforeQueueing")

    /** ColorOS 对 PhoneWindowManager 的覆写入口（`override` 前缀那套命名）。 */
    private val PM_EXT_ENTRIES = setOf(
        "overrideInterceptKeyBeforeQueueing",
        "interceptKeyBeforeQueueing",
    )

    /** AOSP 原生入口。 */
    private val PM_ENTRIES = setOf("interceptKeyBeforeQueueing")

    /** ColorOS 的 PhoneWindowManager 覆写实现（方法名带 `override` 前缀）。 */
    private val PM_EXT_CANDIDATES = listOf(
        "com.android.server.policy.PhoneWindowManagerExtImpl",
    )

    /** AOSP 原生 PhoneWindowManager（兜底）。 */
    private val PM_CANDIDATES = listOf(
        "com.android.server.policy.PhoneWindowManager",
    )

    /**
     * 宿主（`com.oplus.gesture`）侧的接管点：系统自己判好的侧键手势真正的落点。
     *
     * 注意：这两个服务名与下面那个混淆 Handler 的类名**只有本文件写**（一、7）：它们散在
     * `host/SideKeyGestureHooks`、`host/GestureReceiver`、`inject/input/GestureForwarder`
     * 三处各写一份，改 ROM 时漏改任何一处，表现都是「按键拦下来了但什么都不做」，
     * 而且从日志上看不出漏在哪一层。
     */

    /** 其余场景的宿主服务 —— 也是「启动它把宿主抬出可冻结范围」用的那个服务。 */
    const val HOST_KEY_SERVICE = "com.oplus.gesture.server.ActionButtonKeyService"

    /** 「打开应用」场景的宿主服务（框架按场景表里的 `click_action_info` 绑它）。 */
    const val HOST_START_APP_SERVICE =
        "com.oplus.gesture.actionbuttonkey.quicklaunch.StartAppService"

    /** 宿主服务内部 Handler 的类名后缀（`XxxService$a`；混淆名，同一 ROM 内稳定）。 */
    const val HOST_HANDLER_SUFFIX = "a"

    /** 那个内部 Handler 上手势消息的落点方法名。 */
    const val HOST_HANDLE_MESSAGE = "handleMessage"

    /**
     * 宿主服务内部 Handler 上「系统场景派发」那条消息的 `what`（真机实测 = 1）。
     *
     *  收它的时候宿主要按系统当前场景去开页面 / 起服务，模块接管侧键后必须把它吞掉。
     *  一、7：这类取值只写在本候选表里，不许散在 Hook 实现里。
     */
    const val HOST_SCENE_DISPATCH_WHAT = 1

    /**
     * 宿主「设置 → 侧键」那一页的 Activity —— 换页 Hook 的候选表（一、7）。
     *
     *  注意：这两个类名只写在本候选表里，不许就地写在 `ui/SettingsPageHooks` 里：Hook 面
     *  必须优先落在宿主 ROM 的专用入口上，而候选类名只写在本候选表里 —— 就地写字面量的话，
     *  ROM 一改名就没法逐层降级，表现是「装了但页面没换」，日志上还看不出漏在哪一层。
     */

    /** 宿主侧键设置页的**基类**（页面骨架在这里，换页回调按它过滤实例）。 */
    const val HOST_SETTINGS_BASE_ACTIVITY = "com.oplus.gesture.actionbuttonkey.BaseActionActivity"

    /**
     * 侧键设置页的**实际** Activity（基类的子类）。
     *
     *  「重启服务」杀掉本进程之后要靠它把页面重新拉起来：那一枪是进程死了之后由系统投递的，
     *  拿不到当前 Activity，只能有一个写死的组件名兜底。
     */
    const val HOST_SETTINGS_ACTIVITY =
        "com.oplus.gesture.actionbuttonkey.ActionButtonSettingsActivity"

    /**
     * AMS 的广播入口 —— 本模块「主开关 / 配置 / 状态栏指令 / 小窗指令 / 链路自检问」全从这里收。
     *
     * 类名与方法名只写在本候选表里（一、7）：Hook 实现不许就地写字面量，否则 ROM 一改名就
     * 没法逐层降级，只会静默失配。
     */
    private const val AMS_CANDIDATE = "com.android.server.am.ActivityManagerService"

    /**
     * AMS 广播入口的**方法名**（精确匹配，见 [collect] 的口径）。
     *
     * `broadcastIntentWithFeature` 是新版本入口，老版本只有 `broadcastIntent`，两个都收；
     * 但不认模糊匹配 —— 抓错同族方法（`broadcastIntentLocked` / `broadcastIntentInPackage`）
     * 会把我们**自己发出去**的广播也拦一遍（回包被当成「问」再回一次）。
     */
    private val AMS_BROADCAST_ENTRIES = setOf("broadcastIntentWithFeature", "broadcastIntent")

    // 侧键 keyCode 定义在 `core/model/KeyId`（唯一一处），本文件不留别名常量：本机实测侧键
    // 在不同路径上报不同的码（原始上报 735、策略类内部注入 780 / 782），单一常量会误导。

    /**
     * 找 AMS 的广播入口方法（[AMS_CANDIDATE] 上的 [AMS_BROADCAST_ENTRIES]，精确匹配）。
     *
     * @return 命中的方法；找不到返回空表（调用方按「三条通道整体失效」记日志，不硬猜）
     */
    fun findBroadcastPoints(loader: ClassLoader): List<Method> {
        val cls = Reflect.findClass(loader, AMS_CANDIDATE)
        if (cls == null) {
            XLog.w("没找到 $AMS_CANDIDATE —— 广播入口接管点落空")
            return emptyList()
        }
        val all = Reflect.methodsOf(cls).filter { !it.isSynthetic && !it.name.startsWith("access$") }
        val hit = all.filter { it.name in AMS_BROADCAST_ENTRIES }
        if (hit.isEmpty()) {
            val near = all.map { it.name }
                .filter { it.contains("broadcastIntent") }
                .distinct()
            XLog.w(
                "$AMS_CANDIDATE 上没有 ${AMS_BROADCAST_ENTRIES.joinToString("/")}()" +
                    "（同族方法：${near.joinToString(", ").ifEmpty { "一个都没有" }}）",
            )
        }
        return hit
    }

    /**
     * 找侧键的接管点，按 `owner#method` 去重（同名方法不能装两次，否则一次按键会回调多次）。
     *
     * @return 去重后的接管点；调用方逐个尝试安装，装到哪个算哪个
     */
    fun findSideKeyPoints(loader: ClassLoader): List<InterceptionPoint> {
        val out = LinkedHashSet<InterceptionPoint>()

        // 逐层降级而非全装：同一次按压若被多层各喂一次判定器，去重窗只盖得住 instantSingle
        // 那一支，双击会变成「双击 + 单击」。找到更高一层的接管点就不再收集低层。
        for (name in STRATEGY_CANDIDATES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            XLog.w("找到 ColorOS 侧键策略类：$name")
            collect(out, cls, STRATEGY_ENTRIES, InterceptionPoint.Kind.OPLUS_STRATEGY)
        }
        if (out.isEmpty()) {
            for (name in PM_EXT_CANDIDATES) {
                val cls = Reflect.findClass(loader, name) ?: continue
                XLog.w("找到 ColorOS PM 覆写类：$name")
                collect(out, cls, PM_EXT_ENTRIES, InterceptionPoint.Kind.OPLUS_PM_EXT)
            }
        }
        if (out.isEmpty()) {
            for (name in PM_CANDIDATES) {
                val cls = Reflect.findClass(loader, name) ?: continue
                XLog.w("找到 AOSP PhoneWindowManager（兜底）")
                collect(out, cls, PM_ENTRIES, InterceptionPoint.Kind.AOSP_PM)
            }
        }

        return out.toList()
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
        for (m in hit) out += InterceptionPoint(cls, m, kind)
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
