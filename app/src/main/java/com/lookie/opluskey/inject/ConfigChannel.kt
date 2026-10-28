package com.lookie.opluskey.inject

import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.ConfigFields
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.wire.ConfigBroadcast

/** 注入侧持有的配置：从宿主广播过来，供按键判定使用。 */
internal object ConfigChannel {
    /**
     * **模块总闸**（`null` = 还没收到过广播）。没收到过时按「接管」处理 —— 引导条件：
     * 唤醒宿主的那次转发由注入侧发起，默认「不接管」会永久失效。
     *
     *  它是 `GlobalConfig.enabled`（`module_enabled` 那一项），**不是**侧键那一份 `enabled`：
     *  后者只是「这一颗键接管不接管」，两颗一起看才是最终判据（见 [enabledOf]）。
     */
    @Volatile
    private var switchFromBroadcast: Boolean? = null

    /** 完整侧键配置；还没收到过就是 null。只信广播 —— 注入侧读宿主私有目录必 `EACCES`。 */
    @Volatile
    private var received: Config? = null

    /**
     * 最后一次收到的那条配置广播的**内容指纹**（见 `ConfigBroadcast.tag`）。
     *
     *  链路自检回包时原样带回，设置页拿它与自己手上那份比 —— 光回一个「收到过」答不了
     *  「后来改的那一次到了没有」：开机收到一条后哪条丢了，这边还是旧配置，界面照样报「正常」。
     */
    @Volatile
    private var receivedTag: String? = null

    /**
     * 「参与某一组**可用**组合」的键 id 集合 —— 收到配置时算一次（[rebuildComboIndex]），
     * 供 [joinsCombo] 每次 DOWN 直接查。
     */
    @Volatile
    private var comboMembers: Set<String> = emptySet()

    /** 主开关当前值；没收到过广播时按 `true`（接管）处理。 */
    val takeOver: Boolean
        get() = switchFromBroadcast ?: true

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
     */
    fun enabledOf(id: KeyId): Boolean = takeOver && (received?.key(id)?.enabled ?: true)

    /**
     * [keys] 这几颗键一起按下时，该由哪一档组合键接管 —— 没有就是 `null`。
     *
     *  这是 `ComboDetector` 那道「有没有绑定」的闸（见它的 `bindingFor`）：答否时**组合不成立**，
     *  两颗键照旧各走各的单键判定。答是时会摘掉两颗键的单键状态机 —— 所以这道闸必须准：
     *  答错了，用户按下去什么反应都没有。
     *
     *  三条判据：
     *  · 还没收到过配置 ⇒ 没有组合键（**不能**按「没配置 = 默认开着」处理：`enabledOf` 对未知键
     *    返回 true，那样会拿一份空表判出「有绑定」，两颗键一起按就静默失效）；
     *  · 参与的那几颗都得归模块管（[enabledOf] 已含总闸）—— 一颗被停用或删掉，这一档就凑不齐；
     *  · 档位本身要算数（[ComboKey.isUsable]：没关掉、成员够数）。
     *
     *  「成员数取多」与「同数按配置顺序」都在 [Config.comboFor] 里，这一层不重复判。
     */
    fun comboFor(keys: Collection<KeyId>): ComboKey? {
        val cfg = received ?: return null
        // 一个组合都没配时直接收工：`Config.comboFor` 里那句 `filter {}` 会在**每次按键**上
        //  分配一个空列表 —— 绝大多数用户压根没配组合键。
        if (cfg.combos.isEmpty()) return null
        return ownedKeyIds(keys)?.let { cfg.comboFor(it) }
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
     * 组合键的**等待窗口**（毫秒）—— 两颗键按下之间最多隔多久，`ComboDetector` 的判据。
     *
     *  还没收到过广播时给出厂值（80，与这一位出现之前写死的那个数一致）。
     */
    fun comboWindowMs(): Long =
        received
            ?.global
            ?.comboWindowMs
            ?.takeIf { it > 0 }
            ?.toLong() ?: GlobalConfig.DEFAULT_COMBO_WINDOW_MS.toLong()

    /**
     * 这一颗键**参与某一组已启用的组合**吗 —— 只有这种键才值得为组合先等一个窗口。
     *
     *  不参与的键不等：那条路上没有「第二颗键会不会来」这个问题，让它们跟着等只是白慢。
     *
     *  集合在收到配置时算好（[rebuildComboIndex]）：这条判据每次 DOWN 都要问一遍，
     *  原先的写法是**扫一遍 `combos`、每档再做一次 `in`**。
     */
    fun joinsCombo(id: KeyId): Boolean = received != null && id.id in comboMembers

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

    /** 当前配置；还没收到过广播时用 [Config.DEFAULT] 顶着。 */
    val config: Config
        get() = received ?: Config.DEFAULT

    /**
     * 收到过配置没有 —— 链路自检用。
     *
     * `false` 是个真实且**静默**的故障：注入侧会拿 [Config.DEFAULT] 顶着跑（总闸按接管处理、
     * 内置键默认开着，但动作全是「无操作」），表现是「侧键被吞掉、什么都不做」，
     * 而日志里看不出缺了哪一段。
     */
    val receivedConfig: Boolean
        get() = received != null

    /**
     * 最后一次收到的那条配置广播的指纹；还没收到过就是 null。
     *
     *  设置页拿它与自己手上那份的指纹比：**相等才叫「设置生效」**。「收到过」不等于「就是这份」。
     */
    val configTag: String?
        get() = receivedTag

    /** 收下一条配置广播。调用点在 AMS 广播入口（热路径），只做解析 / 赋值 / 记日志。 */
    fun read(intent: Intent) {
        val fresh = ConfigBroadcast.parse(intent)
        val before = received
        val key = fresh.key(KeyId.PLUS)

        // **任一键**（含侧键）从「接管」翻成「不接管」也要撤掉已排下的手势：那一颗被关掉时，
        //  按住不放的那一下否则会一路派发到连发上限。总闸那一位另有下面那个分支（它还要改
        //  `switchFromBroadcast` 并打一行 W），两处都要撤，重复撤一次是幂等的。
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
        //  静默设成关闭，还会触发下面那段撤销回调。缺项视为「这条不是一份权威配置」，保持现值不动。
        val master = fresh.global.enabled
        if (intent.hasExtra(ConfigFields.MODULE_ENABLED_EXTRA) && switchFromBroadcast != master) {
            switchFromBroadcast = master
            XLog.w("收到总闸广播：模块接管=$master（注入侧立即生效）")
            // 翻到「不接管」这一刻撤销已排下的手势（长按枪 / 双击窗 / 连发任务）。
            //  为什么不能写在拦截点那条 early return 里：那条只在有新按键事件时才走到，
            //  而按住不放期间没有新事件进来 —— 撤销写在那边等于永不执行。
            //  本回调跑在 AMS 广播 hook 内，异常一律吞掉，绝不外抛。
            if (!master) {
                try {
                    onTakeOverOff?.invoke()
                } catch (e: Throwable) {
                    XLog.w("总闸关闭时撤销待定手势失败（已忽略）：${e.javaClass.simpleName}", e)
                }
            }
        }

        if (newlyOff) {
            try {
                onTakeOverOff?.invoke()
            } catch (e: Throwable) {
                XLog.w("自定义键关闭时撤销待定手势失败（已忽略）：${e.javaClass.simpleName}", e)
            }
        }

        // 先算索引再换 `received`：`joinsCombo` 那句 `received != null &&` 挡的是「配置还没来过」，
        //  两者之间那几纳秒里它看到的是旧集合，与旧配置正好对得上。
        comboMembers = rebuildComboIndex(fresh)
        received = fresh
        // 指纹按广播本身算（与设置页那边同一份 intent 口径），不是按解析后的对象算
        receivedTag = ConfigBroadcast.tag(intent)
        // 这一行（及上面主开关）必须是 W：注入侧 i/d 在 LSPosed 日志页整档看不到（docs/device-findings.md 十五），
        // 它们是「配置到没到注入侧」的唯一判据；只在启动 / 解锁 / 写盘时机各来一条，不刷屏。
        //  token 要过 [XLog.redactToken]：`shell:` / `intent:` 后面是整条正文（可能带 token、密码），
        //  而这一行是 W —— 既进 logcat 也进「导出日志」那份文件。
        XLog.w(
            "配置已更新：长按=${key.longPressMs}ms" +
                " 双击窗口=${fresh.key(KeyId.PLUS).doubleClickMs}ms（侧键那份）" +
                " 立即单击=${fresh.key(KeyId.PLUS).instantSingle}" +
                " 单击=${XLog.redactToken(key.single.token)}",
        )
        // 上面那行只打**侧键那份**参数；自定义键各有一份自己的键码与长按阈值（见 [logCustomKeys]）。
        logCustomKeys(fresh)
        logCombos(fresh)
    }

    /**
     * 重算 [comboMembers] —— 配置到达时算一次（[read]）。
     *
     *  口径与 `Config.comboFor` 一致：[ComboKey.isUsable] 不成立的那几档永远不会被它判出来
     *  （停用 / 成员不够数），让参与它们的键去等一个窗口只是白慢，所以不进这张表。
     *
     *  单独成函数只为让 [read] 留在 `LongMethod` 阈值内（它已经攒了主开关 / 撤销 / 指纹 / 三行日志）。
     */
    private fun rebuildComboIndex(fresh: Config): Set<String> {
        val members = HashSet<String>(fresh.combos.size * 4)
        for (c in fresh.combos) {
            if (c.isUsable) members += c.keys
        }
        return members
    }

    /**
     * 把组合键打一行 W。
     *
     *  「两颗键一起按没反应」有两个完全不同的原因：注入侧**没收到**这一份配置（`combos` 为空 ⇒
     *  组合不成立、两颗键各走各的），或者收到了但**没配上**这一组。这一行是分辨它们的地方 ——
     *  没有它，日志里只有「DOWN / UP」，看不出注入侧手上到底有几档组合键。
     */
    private fun logCombos(fresh: Config) {
        if (fresh.combos.isEmpty()) return
        XLog.w(
            "组合键：" +
                fresh.combos.joinToString("、") { c ->
                    "${c.id}=${c.keys.joinToString("+")}" +
                        " 短按=${XLog.redactToken(c.single.token)}" +
                        " 长按=${XLog.redactToken(c.long.token)}" +
                        " 阈值=${c.longPressMs}ms${if (c.enabled) "" else "（已停用）"}"
                },
        )
    }

    /**
     * 把每颗自定义键的「键码 + 长按阈值」打一行 W。
     *
     *  上一行（`配置已更新`）只写侧键那份参数：自定义键的码是运行时收集来的、阈值也是每颗各一份，
     *  两者任一不对那颗键的长按就永远不响，而日志里除了 `DOWN` 看不到任何线索 —— 这一行是
     *  「自定义键长按没反应」时唯一能自证的配置快照。
     *
     *  单独成函数只为让 [read] 留在 `LongMethod` 阈值内（它已经攒了主开关 / 撤销 / 指纹三段）。
     */
    private fun logCustomKeys(fresh: Config) {
        if (fresh.customKeys.isEmpty()) return
        XLog.w(
            "自定义键：" +
                fresh.customKeys.joinToString("、") { k ->
                    "${k.label}=${k.keyCode} 长按${fresh.key(k).longPressMs}ms"
                },
        )
    }
}
