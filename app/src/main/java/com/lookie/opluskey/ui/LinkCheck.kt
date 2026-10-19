package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.wire.ConfigBroadcast
import com.lookie.opluskey.ui.model.API_MISMATCH_HINT
import com.lookie.opluskey.ui.model.CONFIG_NOT_SYNCED_HINT
import com.lookie.opluskey.ui.model.CONFIG_READ_FAILED_HINT
import com.lookie.opluskey.ui.model.INJECT_NOT_LOADED_HINT
import com.lookie.opluskey.ui.model.KEY_DISABLED_HINT
import com.lookie.opluskey.ui.model.RESTART_REQUIRED_HINT
import com.lookie.opluskey.ui.model.SERVICE_NOT_RUNNING_HINT
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.state.LinkCheck

/**
 * 本地那份配置的指纹（与发送侧 `ConfigBroadcast.tag` 同口径）。
 *
 *  缓存 key 是**配置对象本身**（`Config` 是 data class，每次写盘都换新实例）⇒ 配置一变就重算。
 *  需要缓存是因为 `ConfigBroadcast.tag` 内部要读一次命令库（算「长按绑的是不是串行命令」），
 *  而总闸在**组合期与点按回调里都会被读到** —— 不能每次都去读盘。
 *  也因此这里是普通 `val`，不是 `@Composable`（`BindingScreen.pick()` 那种事件回调里也要读）。
 */
private var localTagCache: Triple<Config, List<Command>, String>? = null

internal val SettingsPage.localConfigTag: String
    get() {
        val cur = configState.config.value
        // 缓存键必须**同时**带上命令库：`ConfigBroadcast.tag` 里有一项是「长按绑的是不是串行命令」，
        //  它读的是命令库；而保存命令库（`saveCommands`）只换 `commands` 那一份状态、**不换 Config 实例**
        //  —— 只按 Config 失效的话，页面会一直拿着「按旧命令库算出来的」指纹，
        //  与注入侧刚收到的那份对不上，自检就假报「未同步」。
        //  用 `===` 比引用即可：`saveCommands` 每次都换一个新的 list 实例。
        val cmds = configState.commands.value
        localTagCache?.let { if (it.first === cur && it.second === cmds) return it.third }
        val tag = ConfigBroadcast.tag(cur) { CommandLibrary.longIsSerial(cur, it) }
        localTagCache = Triple(cur, cmds, tag)
        return tag
    }

/**
 * 「设置生效」那一段里**配置侧**那半：配置读得出来、注入侧收到过、且它手上就是**这一份**。
 *
 *  抽出来是因为它同时供两处用：「设置生效」那一行的 `ok`，以及整页总闸（[linkReady]）——
 *  两处必须同源，否则又会出现「运行状态报『未同步』、页面却照样能改」。
 *
 * 「主开关已关闭」**不算在这一半里**：那是用户的意图、不是异常；而且把它算进总闸会让主开关
 * 锁死自己（关掉 ⇒ 总闸关 ⇒ 它自己变灰 ⇒ 再也开不回来，见 `HomeScreen` 里主开关那一行）。
 */
internal val SettingsPage.configSideOk: Boolean
    get() {
        if (configState.readOnly) return false
        val inj = link.injectStatus ?: return false
        if (!inj.configOk) return false
        val tag = localConfigTag
        if (inj.configTag == tag) return true
        // 这一份是我们自己刚写下去、注入侧还没回包的 ⇒ 不判「没送到」（见 `configPending`）。
        //  少了这一档，每次拨开关都会有一两帧被判成未同步 —— 整页控件打灰再亮回来，就是全页闪。
        return link.configPending(tag)
    }

/**
 * **整页总闸**：「运行状态」里**任何一段**不 ok 就关。
 *
 * 用户定的口径：总闸不再只看「服务运行 + 按键响应」那两段 —— 「设置生效」同样是在答
 *  「改了会不会生效」，它不通时放用户去改就是误导。主开关与 ⋮ 菜单里那几项都吃这个闸。
 *
 *  两处**有理由的**例外，不是漏掉：
 *   · 「主开关已关闭」不算异常：它是用户的意图状态，且算进去会锁死主开关自己（见 [configSideOk]）；
 *   · 「使用验证」不参与（`blocking = false`）：还没被验证过是信息、不是故障。
 *
 *  判据与盘面上的组合一一对应（见 [linkChecks]），两侧读的是同一组信号，不许各写一份。
 */
internal val SettingsPage.linkReady: Boolean
    get() = link.hostServiceUp && (link.injectStatus?.hooks ?: 0) > 0 && configSideOk

/**
 * 「现在能不能用、不能是因为什么」—— **判据只此一处**：首页那条常驻提示与「选动作被拦住」的 toast
 * 都读它（原先两处各写一份 `when`，加一档要改两个文件，迟早分叉）。
 *
 * 按**用户能做的事**分档，顺序就是优先级 —— 越靠前越「说清了就不必往下猜」：
 * 1) 配置没读成功 ⇒ 重进页面就会重读；
 * 2) 主开关关着 ⇒ 用户自己拨回来（**排在链路之前**，理由见 [linkReady] 的注释）；
 * 3) 宿主服务没报活 ⇒ 重进页面 / 重启；
 * 4) 系统那一侧没回音 ⇒ 作用域漏了 `system`（含「装完从没重启过」那一档）；
 * 5) 回音了、但**构建标识不是本包** ⇒ 换过包没重启（system_server 里跑的还是上一版）；
 * 6) 回音了但接管点 0 个 ⇒ 这一版 ROM 把私有类改了形，只能等适配；
 * 7) 设置没送到 ⇒ 重进本页会重发一遍。
 *
 *  **机型清单不在这张表里**：它只是「这一组验过没有」的提示，不拦注入、也不拦页面
 *（AGENTS.md 一、13）—— 所以既没有「机型不在清单」这一档，也没有对应的禁用。
 *
 *  注意：**「模块没启用」不在这张表里，也不需要判** —— 这段文字只可能在「我们的页面被画出来」
 *  时出现，而页面本身就是模块的代码（跑在宿主 `:ui` 进程里）：能画出来就意味着模块已加载、
 *  作用域至少命中宿主包。真没启用时用户看到的是宿主原版页面，这里一个字都不会出现。
 *
 * @return `null` = 可以用
 */
internal fun SettingsPage.blockedReason(): String? {
    val injectStatus = link.injectStatus
    return when {
        configState.readOnly -> CONFIG_READ_FAILED_HINT

        // **模块总闸**，不是「侧键那颗键」—— 后者只是那一颗（见 `disabledClickReason`）
        !configState.config.value.global.enabled -> KEY_DISABLED_HINT

        !link.hostServiceUp -> SERVICE_NOT_RUNNING_HINT

        injectStatus == null -> INJECT_NOT_LOADED_HINT

        // 回了包、但报的不是本包的构建标识 ⇒ system_server 里还是上一版代码（换包没重启）。
        //  空串是还没带这个字段的旧版注入侧，按「不判」处理。
        injectStatus.buildId.isNotEmpty() && injectStatus.buildId != ModuleInfo.BUILD_ID -> RESTART_REQUIRED_HINT

        injectStatus.hooks <= 0 -> API_MISMATCH_HINT

        !configSideOk -> CONFIG_NOT_SYNCED_HINT

        else -> null
    }
}

/**
 * 「这一下点不动」时该说什么 —— 整页总闸优先，其次是**当前编辑的那颗键自己停用了**。
 *
 *  为什么不能沿用 `blockedReason() ?: "页面还没就绪"`：页面上被置灰的控件分两种 ——
 *  整页被闸住（[blockedReason] 有话说），或者只是**这一颗键被停用**（总闸是通的）。
 *  第二种落到「页面还没就绪」会把人引去重进页面，而真正要做的是到 ⋮ 菜单里把键启用回来。
 *
 *  两种键同一句话：停用**任何一颗**（含侧键）之后，打开它的入口都是同一个 ⋮ 菜单里那一行。
 */
internal fun SettingsPage.disabledClickReason(): String {
    blockedReason()?.let { return it }
    val cfg = configState.config.value
    val key = KeyWatchState.editing(cfg)
    if (!cfg.key(key).enabled) {
        return I18n.format(Strings.KEY_DISABLED_ITEM, key.label)
    }
    //  页面是通的、这一项仍然点不动（比如行要求先选一颗键）：说结论，不编一个「没就绪」
    return I18n.get(Strings.CONTROL_UNAVAILABLE, "这一项现在不可用")
}

/**
 * 把设置页手上那几路信号翻成**逐段的**链路结论，顺序就是数据要走的顺序：
 * 服务 → 按键 → 设置 → 实际使用。
 *
 * 为什么要有这个：只看「宿主报活了没有」一个布尔的话，答不了三种真实故障 ——
 * 按键那一侧压根没装上（作用域配错 / ROM 换了方法名）、设置没送到那一侧、以及最隐蔽的那种
 * 「装上了但这版 ROM 的侧键事件根本不走那条路」。前两种现在有判据了；
 * 第三种只能靠最后那段那笔「真的执行过没有」。
 *
 * 前一段断了，后面的就没意义（按键那侧没回话时不去猜它设置到没到）。
 *
 * 注意：**界面粒度是四段（+ 整体那一行），不是原始信号数**：用户要求
 * 「用户界面不需要技术细节」，于是「设置同步」与「设置读取」合并成「设置生效」——
 * 两件事都还在查（判据是一个 `&&`），只是不再分开报给用户。
 */
@Composable
internal fun SettingsPage.linkChecks(): List<LinkCheck> {
    val hostOk = link.hostServiceUp
    val inj = link.injectStatus
    val respondOk = inj != null && inj.hooks > 0
    // 「设置生效」的判据是**两边指纹相等**，不是「注入侧收到过一条」：
    //  后者答不了「后来改的那一次到了没有」—— 开机收到过之后哪条广播丢了，
    //  注入侧还按旧配置吞键，界面却照样报「正常」。指纹不同 = 明确知道它手上不是这份。
    // 本地那份指纹按配置缓存（见 [localConfigTag]），只有配置变了才重算。
    val configSide = configSideOk
    // 换过包没重启：两边**必然**对不上（连配置字段表都可能不是同一份），这时报「未同步」
    //  会把人引去「重进本页」，而真正该做的是重启 —— 与首页那条横幅（[blockedReason]）
    //  同一个优先级、同一句话。这一档以前只写在横幅里，逐段行照样报「未同步」。
    val buildStale =
        inj != null && inj.buildId.isNotEmpty() && inj.buildId != ModuleInfo.BUILD_ID

    return listOf(
        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_SERVICE_STATUS, "服务运行"),
            ok = hostOk,
            detail =
                if (hostOk) {
                    I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
                } else {
                    I18n.get(Strings.LINK_CHECK_NOT_RUNNING, "未运行")
                },
        ),
        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_KEY_RESPONSE, "按键响应"),
            ok = respondOk,
            detail =
                when {
                    inj == null -> I18n.get(Strings.LINK_CHECK_WAITING_RESPONSE, "等待响应")
                    inj.hooks <= 0 -> I18n.get(Strings.LINK_CHECK_NOT_RESPONDING, "未响应")
                    else -> I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
                },
        ),
        // 「设置同步」（配置送到注入侧没有）与「设置读取」（宿主读到的不是默认值）
        // 合成一段：对用户来说都是「设置到底生效了没有」，分开报只是把内部结构摆到脸上。
        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_CONFIG_EFFECTIVE, "设置生效"),
            // 与总闸**同源**：配置侧那半直接用 [configSideOk]，免得两边各写一份慢慢跑偏
            ok = configSide && link.hostEnabled,
            // 「主开关已关闭」**不算故障**（见 [configSideOk] 与 [linkReady] 的注释）：
            //  只有「配置侧没问题、单纯是用户关了主开关」这一种不 ok 时不 block。
            //  其余（配置读不懂 / 没送到注入侧 / 两边指纹对不上）都真会让设置不生效 —— 照旧 block。
            //  一律 block（`blocking` 走默认值）的话，用户主动关掉主开关，
            //  整体状态就报「设置生效异常」—— 那是用户的意图，不是故障。
            blocking = !(configSide && !link.hostEnabled),
            detail =
                when {
                    // 真因优先：文件读不懂时宿主读到的是默认值（enabled=false），
                    // 再往下会报成「主开关已关闭」—— 那会把用户引到错的方向（他根本没关）。
                    configState.readOnly -> I18n.get(Strings.LINK_CHECK_CONFIG_FAILED, "配置读取失败")

                    inj == null -> I18n.get(Strings.LINK_CHECK_WAITING_RESPONSE, "等待响应")

                    !inj.configOk -> I18n.get(Strings.LINK_CHECK_NOT_EFFECTIVE, "未生效")

                    buildStale -> I18n.get(Strings.LINK_CHECK_RESTART_REQUIRED, "需重启")

                    // 收到过、但指纹对不上（且不是换包没重启造成的）：改过的那一次没送到它手上
                    //  判据与上面 `ok` **同源**（[configSideOk]）—— 这里原来另算了一遍，两处条件
                    //  还差一个 `isNotEmpty()`，于是「横幅说重启、这一行说未同步」。
                    !configSide -> I18n.get(Strings.LINK_CHECK_NOT_SYNCED, "未同步")

                    // 读到默认值只有两种来路：主开关被关了，或者开机还没解锁
                    !link.hostEnabled -> I18n.get(Strings.LINK_CHECK_SWITCH_OFF, "主开关已关闭")

                    else -> I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
                },
        ),
        // 这一段永远不 block：还没被验证过不是故障，只是一条还没拿到的证据。
        //  文案里**不出现「按过没按过」**——那是内部证据的说法（用户点名删的）。
        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_USAGE_VERIFIED, "使用验证"),
            ok = link.hostLastActionAt > 0,
            // 判到过手势、却一次都没执行 —— 多半是这一档绑的是「无操作」
            detail =
                when {
                    link.hostLastActionAt > 0 -> I18n.get(Strings.LINK_CHECK_VERIFIED, "已生效")
                    inj != null && inj.lastGestureAt > 0 -> I18n.get(Strings.LINK_CHECK_NOT_EFFECTIVE, "未生效")
                    else -> I18n.get(Strings.LINK_CHECK_NOT_USED_YET, "尚未使用")
                },
            blocking = false,
        ),
    )
}
