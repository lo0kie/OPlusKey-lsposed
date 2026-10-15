package com.lookie.opluskey.ui.state

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.DeviceSupport
import com.lookie.opluskey.core.wire.ConfigBroadcast
import com.lookie.opluskey.ui.SettingsPage

/**
 * 注入侧（system_server）回包的内容 —— 应答 `KeyModule.ACTION_INJECT_QUERY`。
 *
 * @param hooks 装上的侧键接管点数（0 = 一个都没装上）
 * @param configOk 注入侧收到过配置没有（false ⇒ 它还在拿 `Config.DEFAULT` 顶着跑）
 * @param configTag 注入侧手上那份配置的指纹（`ConfigBroadcast.tag`，空 = 没收到过）。
 *   页面拿它与**自己手上那份**比：相等才叫「设置生效」—— 只看 [configOk] 答不了
 *   「后来改的那一次到了没有」。
 * @param lastGestureAt 最后一次判出手势的时刻（`elapsedRealtime()`，0 = 开机以来从未）
 */
internal data class InjectStatus(
    val hooks: Int,
    val configOk: Boolean,
    val configTag: String,
    val lastGestureAt: Long,
)

/**
 * 链路自检里的一段（界面上叫「运行状态」，见 `AboutScreen`）。
 *
 * **文案是给用户看的，不是给开发看的**（两轮）：不出现「注入侧 / 回话 /
 * 接管点 / 真配置 / 就绪 / 同步 / 读取」这类词，也不报时间戳；**「哪一段断了」这件事本身
 * 也只写到段名为止**，往下怎么坏的去看日志（`XLog`）。
 *
 * @param blocking 这一段不通**要不要报成故障**。「功能还没被验证过」是信息、不是故障 ——
 *   拿它去弹常驻提示会把一台正常的机器一直骂着（见 [SettingsPage.linkChecks]）。
 */
internal data class LinkCheck(
    val name: String,
    val ok: Boolean,
    val detail: String,
    val blocking: Boolean = true,
)

/**
 * 本机「机型 + 系统版本」在不在**适配清单**里（三档，见 `core/model/DeviceSupport`）。
 *
 *  清单外的组合：模块**只注入界面层**（注入侧与宿主侧的 Hook 一个都不挂，那是 `KeyModule` 的事），
 * 于是链路自检永远收不到回包 ⇒ 页面本来就整页禁用。这里单独判一次是为了把**原因说清**：
 * 首页那条常驻提示会报「未知设备，此设备不可用」或「当前系统版本等待适配」，而不是让用户干等
 * 一句「正在等待系统响应」（那句会让人以为等一会儿就好）。
 *
 *  进程内算一次就够：机型与系统版本都不会中途变。**只读 `Build`**，判定本体在 core 里
 *（那边不碰 Android API）。
 */
internal val deviceSupport: DeviceSupport.Support = DeviceSupport.check(
    android.os.Build.MODEL,
    android.os.Build.VERSION.RELEASE,
    // ROM 版本从 `Build.DISPLAY` 取（`ro.build.version.oplusrom` 是 hidden 接口，界面进程读不到）
    DeviceSupport.romSeriesOf(android.os.Build.DISPLAY),
)

/**
 * 清单命中的简写（判定本体是 [deviceSupport]）—— 决定整页能不能改。
 *
 * 与 [devicePending] 一样是从上面那一档里投出来的：**不另存一份判据**，免得两边慢慢跑偏。
 */
internal val deviceSupported: Boolean = deviceSupport == DeviceSupport.Support.ADAPTED

/** 机型在清单里、系统版本不是那一档 —— 提示说「等待适配」，与「未知设备」分开（见 [PENDING_DEVICE_HINT]）。 */
internal val devicePending: Boolean = deviceSupport == DeviceSupport.Support.PENDING

/**
 * 本机代号（`android.os.Build.MODEL`，真机是 `PLK110`）—— 「适配清单」里「本机」那一行的
 * 主标题要摆它出来（见 `AboutScreen` 的 `localDeviceTitle`）。
 *
 * 与 [deviceSupport] 同一个来路（进程内不变的 `Build` 值）：那边只投出判定档位，拿不到原串，
 * 所以这里单独存一份。
 */
internal val deviceModel: String = android.os.Build.MODEL?.trim().orEmpty()

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
        val cur = config.value
        // 缓存键必须**同时**带上命令库：`ConfigBroadcast.tag` 里有一项是「长按绑的是不是串行命令」，
        //  它读的是命令库；而保存命令库（`saveCommands`）只换 `commands` 那一份状态、**不换 Config 实例**
        //  —— 只按 Config 失效的话，页面会一直拿着「按旧命令库算出来的」指纹，
        //  与注入侧刚收到的那份对不上，自检就假报「未同步」。
        //  用 `===` 比引用即可：`saveCommands` 每次都换一个新的 list 实例。
        val cmds = commands.value
        localTagCache?.let { if (it.first === cur && it.second === cmds) return it.third }
        val tag = ConfigBroadcast.tag(cur)
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
        if (configReadOnly) return false
        val inj = injectStatus ?: return false
        if (!inj.configOk) return false
        return inj.configTag == localConfigTag
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
    get() = hostServiceUp && (injectStatus?.hooks ?: 0) > 0 && configSideOk

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
    val hostOk = hostServiceUp
    val inj = injectStatus
    val respondOk = inj != null && inj.hooks > 0
    // 「设置生效」的判据是**两边指纹相等**，不是「注入侧收到过一条」：
    //  后者答不了「后来改的那一次到了没有」—— 开机收到过之后哪条广播丢了，
    //  注入侧还按旧配置吞键，界面却照样报「正常」。指纹不同 = 明确知道它手上不是这份。
    // 本地那份指纹按配置缓存（见 [localConfigTag]），只有配置变了才重算。
    val configSynced = inj?.configTag?.isNotEmpty() == true &&
        inj.configTag == localConfigTag
    val configSide = configSideOk

    return listOf(
        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_SERVICE_STATUS, "服务运行"),
            ok = hostOk,
            detail = if (hostOk) {
                I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
            } else {
                I18n.get(Strings.LINK_CHECK_NOT_RUNNING, "未运行")
            },
        ),

        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_KEY_RESPONSE, "按键响应"),
            ok = respondOk,
            detail = when {
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
            ok = configSide && hostEnabled,
            // 「主开关已关闭」**不算故障**（见 [configSideOk] 与 [linkReady] 的注释）：
            //  只有「配置侧没问题、单纯是用户关了主开关」这一种不 ok 时不 block。
            //  其余（配置读不懂 / 没送到注入侧 / 两边指纹对不上）都真会让设置不生效 —— 照旧 block。
            //  一律 block（`blocking` 走默认值）的话，用户主动关掉主开关，
            //  整体状态就报「设置生效异常」—— 那是用户的意图，不是故障。
            blocking = !(configSide && !hostEnabled),
            detail = when {
                // 真因优先：文件读不懂时宿主读到的是默认值（enabled=false），
                // 再往下会报成「主开关已关闭」—— 那会把用户引到错的方向（他根本没关）。
                configReadOnly -> I18n.get(Strings.LINK_CHECK_CONFIG_FAILED, "配置读取失败")
                inj == null -> I18n.get(Strings.LINK_CHECK_WAITING_RESPONSE, "等待响应")
                !inj.configOk -> I18n.get(Strings.LINK_CHECK_NOT_EFFECTIVE, "未生效")
                // 收到过、但指纹对不上：改过的那一次没送到它手上（日志里能看到两边指纹）
                !configSynced -> I18n.get(Strings.LINK_CHECK_NOT_SYNCED, "未同步")
                // 读到默认值只有两种来路：主开关被关了，或者开机还没解锁
                !hostEnabled -> I18n.get(Strings.LINK_CHECK_SWITCH_OFF, "主开关已关闭")
                else -> I18n.get(Strings.LINK_CHECK_NORMAL, "正常")
            },
        ),

        // 这一段永远不 block：还没被验证过不是故障，只是一条还没拿到的证据。
        //  文案里**不出现「按过没按过」**——那是内部证据的说法（用户点名删的）。
        LinkCheck(
            name = I18n.get(Strings.LINK_CHECK_USAGE_VERIFIED, "使用验证"),
            ok = hostLastActionAt > 0,
            // 判到过手势、却一次都没执行 —— 多半是这一档绑的是「无操作」
            detail = when {
                hostLastActionAt > 0 -> I18n.get(Strings.LINK_CHECK_VERIFIED, "已生效")
                inj != null && inj.lastGestureAt > 0 -> I18n.get(Strings.LINK_CHECK_NOT_EFFECTIVE, "未生效")
                else -> I18n.get(Strings.LINK_CHECK_NOT_USED_YET, "尚未使用")
            },
            blocking = false,
        ),
    )
}
