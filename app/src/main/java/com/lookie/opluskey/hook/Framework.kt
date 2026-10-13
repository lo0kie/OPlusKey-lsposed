package com.lookie.opluskey.hook

import com.lookie.opluskey.Reflect
import com.lookie.opluskey.XLog
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

    // 侧键 keyCode 定义在 `config/KeyId`（唯一一处），本文件不留别名常量：本机实测侧键
    // 在不同路径上报不同的码（原始上报 735、策略类内部注入 780 / 782），单一常量会误导。

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
