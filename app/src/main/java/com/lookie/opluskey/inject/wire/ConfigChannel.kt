package com.lookie.opluskey.inject.wire

import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.ConfigFields
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.comboPlans
import com.lookie.opluskey.core.model.gesturePlan
import com.lookie.opluskey.core.wire.ConfigBroadcast

/** 注入侧持有的配置：从宿主广播过来，供按键判定使用。 */
internal object ConfigChannel {
    /**
     * 上一次收到的配置 —— 内容 / 指纹 / 组合成员表 / **那一刻的总闸**一起换（见 [Arrived]）；
     * 还没收到过就是 null。
     *
     *  只信广播 —— 注入侧读宿主私有目录必 `EACCES`。
     */
    @Volatile
    private var arrived: Arrived? = null

    /**
     * 一条配置广播解出来的全部东西，打成一个包一起生效。
     *
     *  为什么不能分成几个字段逐个赋值：中间那几个纳秒里读者会看到「新配置 + 旧指纹」（链路自检
     *  据此回「已生效」，而设置页比完指纹就以为这一版到了）、或者「新配置 + 旧组合成员表」
     *  （参与组合的那颗键这一下不等窗口，组合按不出来），又或者「新配置 + 旧总闸」（配置说接管、
     *  总闸还停在关）。一个引用一次替换，几样就是一个整体 —— 读侧要么读到旧的一份、要么读到新的。
     *
     *  @param tag 这条广播自己的内容指纹（见 `ConfigBroadcast.tag`），不是按解析后的对象算
     *  @param combos 组合那三张派生表（见 [ComboDerived]）—— 同一次遍历算出来，一起生效
     *  @param master 这一份生效时的**模块总闸**（`GlobalConfig.enabled`）；`null` = 还没收到过
     *    带 `module_enabled` 的广播 —— 那时按「接管」处理（见 [takeOver]）
     */
    private class Arrived(
        val config: Config,
        val tag: String?,
        val combos: ComboDerived,
        val master: Boolean?,
    )

    /**
     * 主开关当前值；没收到过广播时按 `true`（接管）处理。
     *
     *  它是 `GlobalConfig.enabled`（`module_enabled` 那一项），**不是**侧键那一份 `enabled`：
     *  后者只是「这一颗键接管不接管」，两颗一起看才是最终判据（见 [enabledOf]）。
     */
    val takeOver: Boolean
        get() = arrived?.master ?: true

    /**
     * 「配置还没到、这一下按接管处理」那行 W 是否已经打过。
     *
     *  只打一次：这条判据在**每次按键**上都会问一遍（认领闸门 + 组合闸门），逐次打就是刷屏；
     *  而它要回答的只是「第一次按键时为什么被吞了却没有动作」—— 一行就够。
     */
    @Volatile
    private var announcedMissingConfig = false

    /**
     * 某一颗键当前该不该接管 —— **认领闸门的唯一判据**（`SideKeyInterceptor.takesOver`）。
     *
     *  两级相与：**总闸** [takeOver]（它带着「缺那一项的畸形广播别改总闸」那道闸，以及
     *  「还没收到过广播时按接管」的引导条件）**且** 这一颗键自己的 `enabled`。
     *
     *  拆开之前侧键只走总闸、自定义键只走自己那份 —— 于是「侧键的 `enabled`」被迫兼任总闸，
     *  停用侧键就等于关掉整个模块。现在两者各管一层：总闸关 ⇒ 一颗键都不接管；
     *  总闸开 ⇒ 逐键那一位说了算（侧键也一样）。
     *
     *  配置里认不出的键（还没收到过广播 / 那颗键被删了）按不接管 —— `Config.key` 对未知键
     *  返回 `defaultFor`，自定义键那一档的 `enabled = false`。
     *
     *  **它与 [Config.isLive] 不是同一件事，别合并**：这里的一级是 [takeOver]，不是
     *  `received.global.enabled`。理由有两条 —— 还没收到过广播时要按「接管」引导；而缺
     *  `module_enabled` 那一项的畸形广播里 `global.enabled` 会被 `ConfigBroadcast.parse`
     *  兜底成 `false`，用 [Config.isLive] 会让整个模块被一条残缺广播静默关掉。
     *  [takeOver] 的取值已经把这两条都吃在里面了。
     *
     *  ## 「还没收到过广播」的那一档为什么要留一行判据
     *
     *  按接管引导意味着**这一次按键会被吞掉**，而手上是 [Config.DEFAULT]（动作全是「无操作」）——
     *  用户看到的是「按键没反应」，日志里却一切正常。首次开机 / 宿主还没发过广播时就是这个形状，
     *  所以第一次走到这里补一行 W 把原因说出来（只一次，见 [announcedMissingConfig]）。
     */
    fun enabledOf(id: KeyId): Boolean {
        val cfg = arrived
        if (cfg == null && !announcedMissingConfig) {
            announcedMissingConfig = true
            XLog.w(
                "配置还没到（还没收到过宿主广播）⇒ ${id.label} 按「接管」处理：" +
                    "这一下会被认下来，动作是全默认的「无操作」—— 首次按键只报这一次",
            )
        }
        return takeOver && (cfg?.config?.key(id)?.enabled ?: true)
    }

    /**
     * [keys] 这几颗键一起按下时，可能由**哪几档**组合键接管 —— 空表就是没有。
     *
     *  这是 `ComboDetector` 那道「有没有绑定」的闸（见它的 `bindingsFor`）：答空时**组合不成立**，
     *  那几颗键照旧各走各的单键判定。答非空时会摘掉它们的单键状态机 —— 所以这道闸必须准：
     *  答错了，用户按下去什么反应都没有。
     *
     *  **返回列表而不是一档**：同一组键可以配几档、各自一种触发方式（[ComboKey.mode]），
     *  三条判据各不相同，得让判定侧逐档试。成员多的在前（[Config.combosFor] 给的顺序）。
     *
     *  **成员数不设限**：两颗那一档由 `ComboDetector.pairFor` 一对一地问，三颗以上由
     *  `multiFor` 把窗口内按下的那一组整体递进来。
     *
     *  三条判据：
     *  · 还没收到过配置 ⇒ 没有组合键（**不能**按「没配置 = 默认开着」处理：`enabledOf` 对未知键
     *    返回 true，那样会拿一份空表判出「有绑定」，几颗键一起按就静默失效）；
     *  · 参与的那几颗都得归模块管（[enabledOf] 已含总闸）—— 一颗被停用或删掉，这一档就凑不齐；
     *  · 档位本身要算数（[ComboKey.isUsable]：没关掉、成员够数）。
     */
    fun combosFor(keys: Collection<KeyId>): List<ComboKey> {
        val cfg = arrived?.config ?: return NO_COMBOS
        // 一个组合都没配时直接收工：`Config.combosFor` 里那个 `ArrayList` 会在**每次按键**上
        //  分配一次 —— 绝大多数用户压根没配组合键。
        if (cfg.combos.isEmpty()) return NO_COMBOS
        val down = ownedKeyIds(keys) ?: return NO_COMBOS
        return cfg.combosFor(down)
    }

    /**
     * [keys] 全归模块管时返回它们的 id 集合；有一颗不归（或一颗都没有）就是 `null` ——
     * 组合不成立，两颗键照旧各走各的单键判定。
     *
     *  一次遍历既判归属又攒集合，省掉原先 `keys.map {}.toSet()` 那个中间列表；
     *  短路口径不变：任何一颗不接管就整组不成立。
     */
    private fun ownedKeyIds(keys: Collection<KeyId>): Set<String>? {
        if (keys.isEmpty()) return null
        val down = HashSet<String>(keys.size * 2)
        for (k in keys) {
            if (!enabledOf(k)) return null
            down += k.id
        }
        return down
    }

    /**
     * 组合键的**扫描窗口**（毫秒）—— 找同伴键时按这个宽度扫候选。
     *
     *  它是这份配置里**最宽**的那一档（见 `Config.comboScanWindowMs`）：按下当前这一颗键时
     *  还不知道会凑成哪一组，扫窄了就把宽窗口那一档直接漏掉。命中之后由 [windowFor] 复核。
     *
     *  还没收到过广播时给出厂值（80，与这一位出现之前写死的那个数一致）。
     */
    fun scanWindowMs(): Long =
        arrived
            ?.config
            ?.comboScanWindowMs
            ?.takeIf { it > 0 }
            ?.toLong() ?: GlobalConfig.DEFAULT_COMBO_WINDOW_MS.toLong()

    /** [combo] **自己**那一档按键间隔（没设过就跟随公共）—— 配对命中之后用它复核间隔。 */
    fun windowFor(combo: ComboKey): Long =
        arrived
            ?.config
            ?.comboWindowFor(combo)
            ?.takeIf { it > 0 }
            ?.toLong() ?: GlobalConfig.DEFAULT_COMBO_WINDOW_MS.toLong()

    /**
     * [combo] 的**首键要先按住多久**（毫秒）—— 按住那条的下限，每组可另设一份
     * （没写过就跟随公共，见 `Config.comboHoldMinFor`）。
     *
     *  还没收到过广播时按公共默认（100ms）：那是这一位的出厂值。
     */
    fun holdMinFor(combo: ComboKey): Long =
        arrived
            ?.config
            ?.comboHoldMinFor(combo)
            ?.takeIf { it > 0 }
            ?.toLong() ?: GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS.toLong()

    /**
     * [combo] 这一档**整串**的容忍跨度（= 相邻两颗的窗口 × (成员数-1)，见 `Config.comboSpanFor`）
     * —— 多成员那条路（`ComboDetector.multiFor`）按它复核「凑没凑齐」。
     *
     *  三颗按一个窗口复核等于要求三根手指同时落，真机上永远凑不齐；两颗时它与 [windowFor] 同值。
     */
    fun spanFor(combo: ComboKey): Long =
        arrived
            ?.config
            ?.comboSpanFor(combo)
            ?.takeIf { it > 0 }
            ?.toLong()
            ?: (GlobalConfig.DEFAULT_COMBO_WINDOW_MS.toLong() * (combo.keys.size - 1).coerceIn(1, ComboKey.MAX_JUDGE_KEYS - 1))

    /**
     * 这一颗键按下之后要**先等多久**（毫秒）：它参与的几组里最长的那个**整串跨度**。
     *
     *  按最长的而不是按某一组：等短了，宽窗口那一组永远凑不齐（第二颗键到时这颗已经回落单击）；
     *  等多了只是这根手指慢半拍，而它参与的组合本来就要求等这么久。
     *  n 颗的组合要等的是 (n-1) 个窗口 —— 按一个窗口等的话，第一颗在第三颗到来之前就已经
     *  回落成单击了，三颗的组合永远只在「三根手指同时落」时才成立。
     */
    fun holdWindowMsFor(id: KeyId): Long = arrived?.combos?.holdWindows?.get(id.id) ?: scanWindowMs()

    /**
     * 这一颗键**参与某一组已启用、且认窗口那条的组合**吗 —— 只有这种键才值得为组合先等一个窗口。
     *
     *  不参与的键不等：那条路上没有「第二颗键会不会来」这个问题，让它们跟着等只是白慢。
     *  **按住那几档的成员也不让等**（判据就是 `ComboKey.isWait`）：那一档不认
     *  「窗口内先后按下」，等一个窗口换不来任何东西 —— 成员键按下去就立刻走自己的单键判定。
     *
     *  集合在收到配置时算好（[deriveCombos] 的 `windowMembers`）：这条判据每次 DOWN 都要问一遍，
     *  原先的写法是**扫一遍 `combos`、每档再做一次 `in`**。
     */
    fun joinsCombo(id: KeyId): Boolean {
        val members = arrived?.combos?.windowMembers ?: return false
        return id.id in members
    }

    /**
     * 这颗键是**任何一档**组合（含按住档）的成员吗 —— 多成员判定那条路的头道闸。
     *
     *  与 [joinsCombo] 分开：那个答「按下之后要不要压一个窗口」，只有等待档要；
     *  这个只答「归不归组合管」。按住档的后续成员不等窗口，但它照样可能凑成某一档 ——
     *  拿 [joinsCombo] 挡，按住组合的三颗以上就永远凑不齐（第一颗不配、第三颗进不来）。
     */
    fun inAnyCombo(id: KeyId): Boolean {
        val members = arrived?.combos?.allMembers ?: return false
        return id.id in members
    }

    /**
     * 这一颗键**可能是某一组按住组合里先按住的那一颗**吗 —— 是就把它按下那一下压到抬手。
     *
     *  只服务「只参与非首键的按住组合」那条路（见 `ComboOnlyNative`）。判据为什么是「先按住的那一颗」
     *  而不是「任一成员」：按住那条只在**另一颗还按着**时成立，所以成员在按下那一刻就能定论 ——
     *  轮到自己当先按住的那颗，之后可能有人来配（要压）；只当后补的那一颗，按下时另一颗若按着
     *  组合当场就成立（`ComboDetector.onDown` 回 `TAKEN`），没按着则这一下永远配不上。
     *
     *  只看这一档的**起点键**（`ComboKey.starterKey`）：按住那条只在「另一颗还按着」时成立，
     *  而「已经被按住的那一颗」就是起点键 —— 别的成员按下去时要么组合当场成立（另一颗按着），
     *  要么永远配不上，压住它只是白等。
     */
    fun holdStarter(id: KeyId): Boolean {
        val starters = arrived?.combos?.holdStarters ?: return false
        return id.id in starters
    }

    /**
     * 「某一颗键从接管翻成不接管」那一刻要执行的撤销动作，由 `KeyHooks.install` 装配上
     * （实现是 `SideKeyInterceptor.cancelPending`，它把所有键的待定手势一起撤掉）。
     *
     *  落点只能在这里：按住不放时不会再有按键事件走进 `SideKeyInterceptor.handle`，
     *  撤销若是写在认领闸门（`enabledOf`）那一侧就永远不会执行；而已经排下的长按枪 /
     *  双击窗到点会自己响、连发任务又从不看闸门，不撤就会一路派发到 `REPEAT_MAX_MS`。
     */
    @Volatile
    var onTakeOverOff: (() -> Unit)? = null

    /**
     * 执行一次撤销回调，整段兜住 —— 两处触发点都在 AMS 广播 hook 内，漏一个异常出去就是
     * system_server 崩（五之二、3）。
     */
    private fun cancelPending(subject: String) {
        try {
            onTakeOverOff?.invoke()
        } catch (e: Throwable) {
            XLog.w("$subject：${e.javaClass.simpleName}", e)
        }
    }

    /** 当前配置；还没收到过广播时用 [Config.DEFAULT] 顶着。 */
    val config: Config
        get() = arrived?.config ?: Config.DEFAULT

    /**
     * 收到过配置没有 —— 链路自检用。
     *
     * `false` 是个真实且**静默**的故障：注入侧会拿 [Config.DEFAULT] 顶着跑（总闸按接管处理、
     * 内置键默认开着，但动作全是「无操作」），表现是「侧键被吞掉、什么都不做」，
     * 而日志里看不出缺了哪一段。
     */
    val receivedConfig: Boolean
        get() = arrived != null

    /**
     * 最后一次收到的那条配置广播的指纹；还没收到过就是 null。
     *
     *  设置页拿它与自己手上那份的指纹比：**相等才叫「设置生效」**。「收到过」不等于「就是这份」。
     */
    val configTag: String?
        get() = arrived?.tag

    /** 收下一条配置广播。调用点在 AMS 广播入口（热路径），只做解析 / 赋值 / 记日志。 */
    fun read(intent: Intent) {
        // 解析自己兜住畸形广播（返回 null 并已记 w，见 `ConfigBroadcast.parse`）：这一条作废，
        //  上一份配置与总闸原样留着 —— 一条坏广播不该把已经好的配置换掉。
        val fresh = ConfigBroadcast.parse(intent) ?: return
        val prev = arrived
        val before = prev?.config
        val key = fresh.key(KeyId.PLUS)

        // **任一键**（含侧键）从「接管」翻成「不接管」也要撤掉已排下的手势：那一颗被关掉时，
        //  按住不放的那一下否则会一路派发到连发上限。总闸那一位另有下面那个分支（它还要改
        //  总闸并打一行 W），两处都要撤，重复撤一次是幂等的。
        // 比的是**旧配置**那份清单（`prev.allKeys`）：一颗自定义键被**删掉**时，新配置里
        //  已经没有它，遍历 `fresh.allKeys` 就永远看不到这一颗，已排下的手势也就撤不掉
        //  （删键那一刻按住不放会一路派发到连发上限）。`fresh.key(已删的键)` 退回默认值
        //  （自定义键那一档 `enabled=false`），所以这一条对「删键」与「关掉」同样成立。
        val newlyOff =
            before?.let { prev ->
                prev.allKeys.any { prev.key(it).enabled && !fresh.key(it).enabled }
            } ?: false

        // 只有这条广播**带** `module_enabled` 这一项时才用它改总闸：`ConfigBroadcast.parse` 对缺项
        //  兜底成 false，而本对象「没收到过 = 接管」的方向相反 —— 一条缺这一项的畸形广播会把总闸
        //  静默设成关闭，还会触发下面那段撤销回调。缺项视为「这条不是一份权威配置」，沿用上一份的值。
        val master =
            if (intent.hasExtra(ConfigFields.MODULE_ENABLED_EXTRA)) fresh.global.enabled else prev?.master
        if (master != null && prev?.master != master) {
            XLog.w("收到总闸广播：模块接管=$master（注入侧立即生效）")
            // 翻到「不接管」这一刻撤销已排下的手势（长按枪 / 双击窗 / 连发任务）。
            //  为什么不能写在拦截点那条 early return 里：那条只在有新按键事件时才走到，
            //  而按住不放期间没有新事件进来 —— 撤销写在那边等于永不执行。
            //  本回调跑在 AMS 广播 hook 内，异常一律吞掉，绝不外抛。
            if (!master) cancelPending("总闸关闭时撤销待定手势失败（已忽略）")
        }

        if (newlyOff) cancelPending("自定义键关闭时撤销待定手势失败（已忽略）")

        //  全部一起换（见 [Arrived]）：内容、指纹、组合成员表、总闸若逐个赋值，中间态会被上面的
        //  读者看到 —— 「新配置 + 旧总闸」就是「配置说接管、按键却仍不接管」。先构造好整份，
        //  再一次性替换引用，没有中间态。
        arrived =
            Arrived(
                fresh,
                ConfigBroadcast.tag(intent),
                deriveCombos(fresh),
                master,
            )
        // 这一行（及上面主开关）必须是 W：注入侧 i/d 在 LSPosed 日志页整档看不到（docs/device/lsposed.md 十五），
        // 它们是「配置到没到注入侧」的唯一判据；只在启动 / 解锁 / 写盘时机各来一条，不刷屏。
        //  token 要过 [XLog.redactToken]：`shell:` / `intent:` 后面是整条正文（可能带 token、密码），
        //  而这一行是 W —— 既进 logcat 也进「导出日志」那份文件。
        XLog.w(
            "配置已更新：长按=${key.longPressMs}ms" +
                " 双击窗口=${fresh.key(KeyId.PLUS).doubleClickMs}ms（侧键那份）" +
                //  组合那一档的窗口 —— 「每组的独立等待没作用」时先看这一行：
                //  它是最宽的扫描窗口（各组里最长的那个），各组自己的值在 `ComboDetector` 复核时用
                " 组合窗口=${fresh.comboScanWindowMs}ms（${fresh.combos.size}组）" +
                " 单击=${XLog.redactToken(key.single.token)}",
        )
        // 上面那行只打**侧键那份**参数；自定义键各有一份自己的键码与长按阈值（见 `ConfigArrivalLog`）。
        ConfigArrivalLog.customKeys(fresh)
        ConfigArrivalLog.combos(fresh)
        ConfigArrivalLog.plans(fresh)
    }
}

/**
 * 「这一下没有组合键接得住」—— 共享的空表。
 *
 *  按键判定链上每次 DOWN 都要问一遍 [ConfigChannel.combosFor]，绝大多数用户压根没配组合键；
 *  每次建一个空表等于白送一次分配（`tools/audit-perf.py` 判据 2 盯的就是这类）。
 */
private val NO_COMBOS: List<ComboKey> = emptyList()

/**
 * 组合那三张**派生表** —— 配置到达时按 `combos` 算一次，DOWN 路径上只查表。
 *
 *  三张表回答三个问题，但都只从**同一次**遍历 [Config.liveCombos] 里出得来，所以合成一趟：
 *  原先 `comboMemberIds` / `comboHoldWindows` / `comboHoldStarters` 各过滤一遍、各扫一遍，
 *  一次配置到达就把同一份清单的过滤做了三次。
 *
 *  **同一组键可以有几档**（三种触发方式各一档）：某个键可能同时是「等窗口那档的成员」与
 *  「按住那档的起点」，两张表都会收它 —— 判定侧按「起点键优先（压到抬手）」处理，
 *  那一档的先后按因此退化成「第二颗在 holdMin 之后落下」（见 `ComboOnlyNative` 的四级判据）。
 *
 *  @param windowMembers 「要押住等一个窗口」的键 id —— 见 [ConfigChannel.joinsCombo]
 *  @param holdWindows 每颗键按下之后要等的时长（它参与的几组里最长的那个窗口）——
 *    见 [ConfigChannel.holdWindowMsFor]
 *  @param holdStarters 「能当某一档按住组合的起点键」的键 id —— 见 [ConfigChannel.holdStarter]
 */
private class ComboDerived(
    val windowMembers: Set<String>,
    /**
     * **任何一档**（含按住档）的成员 —— 与 [windowMembers] 分开：那一张只回答「按下之后要不要
     * 压一个窗口」（只有等待档要），这一张只回答「这颗键归不归组合管」。
     * 多成员判定那条路要先过这一道闸（见 `ComboDetector.multiFor`）——按住档的第三颗虽然不等
     * 窗口，但它照样可能凑成某一档，拿 [windowMembers] 挡就把按住档的多成员整条挡死了。
     */
    val allMembers: Set<String>,
    val holdWindows: Map<String, Long>,
    val holdStarters: Set<String>,
)

/**
 * 一趟扫出上面那三张表。
 *
 *  **只算 [Config.liveCombos]**（成员键都还接管着）：凑不齐的组判不出来，让参与它们的键白等一个窗口。
 *  原先只看 `ComboKey.isUsable`，于是「停用其中一颗成员键」之后另一颗照样等满那个窗口 ——
 *  列表上已经显示「已停用」，按键却还是慢半拍（最长 1000ms）。
 *
 *  **窗口那两张只算认窗口的档**（`isWait`）：等窗口这件事是「会不会有第二颗键在一个窗口内落下来」
 *  这个问题的代价，不认窗口的档那个问题就不存在 —— 成员键按下去就该立刻走自己的单键判定
 *  （用户口径：不认窗口时，成员键各自不再等这个窗口）。窗口取的是**整串跨度**
 *  （`Config.comboSpanFor`，n 颗 = (n-1) 个窗口），不是单个窗口：三颗那一档的第三颗到来时，
 *  第一颗已经隔了两个窗口 —— 按一个窗口等，它早就回落成单击了。
 *
 *  **起点键只从按住那两档来**（`starterKey`）：后补的那一颗不必压 —— 它按下时组合要么当场成立、
 *  要么永远成立不了。按住那两档只对两颗成立（三颗已被 `modeFor` 挡住）。
 */
private fun deriveCombos(fresh: Config): ComboDerived {
    val members = HashSet<String>(fresh.combos.size * 4)
    val all = HashSet<String>(fresh.combos.size * 4)
    val windows = HashMap<String, Long>(fresh.combos.size * 4)
    val starters = HashSet<String>(fresh.combos.size * 2)
    for (c in fresh.liveCombos()) {
        //  任何一档的成员都收进这张表（判定多成员时要先知道「这颗归不归组合」）
        all += c.keys
        if (c.isWait) {
            members += c.keys
            val span = fresh.comboSpanFor(c).toLong()
            for (k in c.keys) {
                if (span > (windows[k] ?: 0L)) windows[k] = span
            }
        } else {
            c.starterKey?.let { starters += it }
        }
    }
    return ComboDerived(members, all, windows, starters)
}

// 配置到达时那三行**判据级日志** —— 从 `ConfigChannel` 分出来一支：那边已经贴着 detekt 的
// `TooManyFunctions` 上限，而这三支确实是一件事（「这一份配置长什么样」的投影），
// 且都只读 `Config`、不碰通道的任何状态。

/**
 * 配置到达时打的那几行 —— 「按了没反应」时全靠它们分辨「配置没到」与「判定不对」。
 *
 *  三支都只读 [Config]：不碰 `ConfigChannel` 的任何字段，所以放在这里与放在那边完全等价。
 */
internal object ConfigArrivalLog {
    /**
     * 把每颗自定义键的「键码 + 长按阈值」打一行 W。
     *
     *  上一行（`配置已更新`）只写侧键那份参数：自定义键的码是运行时收集来的、阈值也是每颗各一份，
     *  两者任一不对那颗键的长按就永远不响，而日志里除了 `DOWN` 看不到任何线索 —— 这一行是
     *  「自定义键长按没反应」时唯一能自证的配置快照。
     */
    fun customKeys(fresh: Config) {
        if (fresh.customKeys.isEmpty()) return
        XLog.w(
            "自定义键：" +
                fresh.customKeys.joinToString("、") { k ->
                    "${k.label}=${k.keyCode} 长按${fresh.key(k).longPressMs}ms"
                },
        )
    }

    /**
     * 把组合键打一行 W。
     *
     *  「两颗键一起按没反应」有两个完全不同的原因：注入侧**没收到**这一份配置（`combos` 为空 ⇒
     *  组合不成立、两颗键各走各的），或者收到了但**没配上**这一组。这一行是分辨它们的地方 ——
     *  没有它，日志里只有「DOWN / UP」，看不出注入侧手上到底有几档组合键。
     */
    fun combos(fresh: Config) {
        if (fresh.combos.isEmpty()) return
        XLog.w(
            "组合键：" +
                fresh.combos.joinToString("、") { c ->
                    "${c.id}=${c.keys.joinToString("+")}" +
                        " 短按=${XLog.redactToken(c.single.token)}" +
                        " 长按=${XLog.redactToken(c.long.token)}" +
                        //  触发方式：这是这一档的身份，判据与槽位都看它 ——
                        //  「按了没反应」先看这一位对不对（三颗只可能是等窗口）
                        " 触发=${if (c.isWait) "等窗口" else "先按住第 ${c.mode} 颗"}" +
                        " 阈值=${c.longPressMs}ms${if (c.enabled) "" else "（已停用）"}"
                },
        )
        //  每档组合的**判定计划**（`ComboPlan`）：要几颗、等多久、判定器认不认。
        //  成员数已经不影响能不能判（两颗走配对、3 颗起走凑齐），所以这一行现在读的是
        //  「这一档要几颗、窗口多大、长按阈值多少」——「按了没反应」时对得上按法就是配置问题，
        //  对不上才是判定问题，两者靠它分开。
        XLog.guard("组合判定计划") {
            fresh.comboPlans().forEach { XLog.d("组合计划：$it") }
        }
    }

    /**
     * 每颗键「这一档手势会判成什么」的投影。
     *
     *  用 `d` 不用 `w`：它**不是**判据（判定器现在还没读这份计划），只是给排查用的投影；
     *  注入侧的 `d` 在 LSPosed 日志页看不到，要用 logcat 看（`docs/device/lsposed.md` 十五）。
     */
    fun plans(fresh: Config) {
        XLog.guard("判定计划") {
            fresh.allKeys.forEach { id -> XLog.d("判定计划：${id.id} → ${fresh.key(id).gesturePlan()}") }
        }
    }
}
