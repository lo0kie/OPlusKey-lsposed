package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.rom.DeviceSupport

// 「一个绑定该显示成什么名字」—— 全项目**唯一**一条优先级。
//
// 这句话有两个进程各答一次（`:ui` 的 `AppNaming`、宿主里的 `ActionNotice`），因为它们能查到的
// 东西不一样：界面那份有 `Config`、有一份查名字的缓存；宿主那份只有现读的配置和 `PackageManager`。
// 跨进程合不成一个函数 —— 但**顺序不该有两份**。
//
// 原先两处各写了一遍，靠注释声明「逐档对齐」。实际顺序是反的：界面把「落盘时记下的名字」摆在
// `PackageManager` 前面（理由是「冷启动清单还没回来」），宿主摆在后面（理由是「现查更准」）。
// 两个理由都成立，但它们不可能同时成立 —— 于是同一个应用在两个地方可能显示两个名字。
//
// 现在顺序在这里，两侧只回答「这一档你取到什么」：`fromSystem` 现查、`fromSaved` 查落盘那份。
// 另外两档（老配置后缀、包名）不需要外部信息，由这里自己切。

/**
 * **应用名**的优先级 —— 四条，从强到弱。
 *
 *  1. **现查系统**（`PackageManager`）—— 最准：应用改过名、换过图标包都跟得上，而且不依赖
 *     配置有没有落盘。这也是唯一一条「两个进程都能做到」的档；
 *  2. **落盘时记下的名字**（`Config.appNames`）—— 应用**已经卸载**时只剩它有值（PM 查不到，
 *     但用户明明是从来装着的那些里挑的）。它排在现查之后：应用还在的时候，现查永远更新；
 *  3. **老配置塞在 token 末尾的 `|名字`** —— 旧写法落下的，只读不写，用户重新挑一次就迁到第 2 档；
 *  4. **包名** —— 兜底，永远有值（手改配置 / 包被卸载且没记过名字）。
 *
 * ## 为什么「落盘那份」不排第一
 *
 *  第 2 档的名字是**绑定那一刻**抄下来的。应用后来改了名，它就成了旧文；而 `PackageManager`
 *  每次问都是当下的答案。界面那边原先把它排第一，理由是「冷启动直接进页、清单还没查回来时
 *  只有它有名字」—— 但 `appLabelOf` 走的是 `PackageManager` 的同步查询，与「清单有没有回来」
 *  无关，那条理由不成立。真正需要第 2 档的只有「应用已卸载」这一种情况，而那种情况下
 *  `fromSystem` 给 `null`，顺序自然落到它。
 *
 * @param target 绑定的目标写法（`999@包名`、`999@包名/类名`、`999@包名/快捷方式id`，可带 `|名字`）
 * @param fromSystem 现查系统名；**查不到必须给 `null`** —— 给包名会让下面三档全都走不到
 * @param fromSaved 查落盘那份（键是 [AppTarget.identity]，见 `Config.appName`）；没有给 `null`
 */
inline fun resolveAppName(
    target: String,
    fromSystem: (pkg: String) -> String?,
    fromSaved: (identity: String) -> String?,
): String {
    fromSystem(AppTarget.pkg(target))?.takeIf { it.isNotBlank() }?.let { return it }
    fromSaved(AppTarget.identity(target))?.takeIf { it.isNotBlank() }?.let { return it }
    AppTarget.label(target)?.takeIf { it.isNotBlank() }?.let { return it }
    return AppTarget.pkg(target)
}

/**
 * **目标自己的名字**（组件名 `LauncherUI` / 快捷方式名 `扫一扫`）—— 与 [resolveAppName] 是两件事。
 *
 *  界面那边这一段的顺序是「这一趟刚挑中的草稿 → 落盘那份 → 老配置后缀」，宿主那边没有草稿
 *  （它不在挑应用的现场），只有后两档。差别只有 [draft] 这一个入参，所以收进同一个函数。
 *
 *  取不到给 `null` —— 调用方据此决定「不补括号」还是「退回类名最后一段」（见 `appTargetLabel`
 *  的三段拼法）。这里不替它决定：组件与快捷方式的退路不一样（快捷方式没有类名可退）。
 *
 * @param draft 这一趟刚挑中、还没落盘的名字（只有「选择应用」页头有）
 * @param fromSaved 同 [resolveAppName]
 */
inline fun resolveTargetName(
    target: String,
    draft: String?,
    fromSaved: (identity: String) -> String?,
): String? {
    draft?.takeIf { it.isNotBlank() }?.let { return it }
    fromSaved(AppTarget.identity(target))?.takeIf { it.isNotBlank() }?.let { return it }
    return AppTarget.label(target)?.takeIf { it.isNotBlank() }
}

/**
 * 小窗（ColorOS 柔性窗口）**跨进程对接**用的那几组取值 —— 一处定义，三侧共用。
 *
 * 为什么必须收在一处：这几个名字与数字要在**不同的进程**里逐字对得上 ——
 *
 * ```
 *   宿主主进程（发送）  host/action/MiniWindowLauncher · bridge/MiniWindowSupport
 *   诊断探针           inject/ProbeChannel
 *   system_server      bridge/FlexibleWindowBridge
 * ```
 *
 * 收发两端各写一遍时，改一边漏一边的**表现是「小窗指令收到但形状不对 / 没反应」**，而两边各自
 * 都编得过、日志也各自自洽 —— 这类错在真机上只能靠猜（`AGENTS.md` 五、2 要的是「断在哪一环」，
 * 名字对不上时连断在哪一环都说不出来）。取值本来就在 `core/model` 里放得下，没必要各写一份。
 *
 * 每个取值都是**平台事实**（`OplusZoomWindowManager` 与 `ActivityOptions` 的接口），不是本模块的
 * 选择：改它们等于改对接对象，必须回到 `docs/features/zoom-window/api.md` 的尺寸档对照表重新核一遍。
 */
object ZoomWindowWire {
    /**
     * `ActivityOptions` setter 里「窗口模式」那个 key —— **点号**形态。
     *
     * 与 [EXTRA_WINDOW_MODE_KEY] 同义不同形（那是 `Bundle` 直接塞给
     * `OplusZoomWindowManager` 时用的下划线形态），两个都要有，别把其中一个当成另一个的笔误。
     */
    const val LAUNCH_WINDOWING_MODE_KEY = "android.activity.windowingMode"

    /**
     * `ActivityOptions` setter 里尺寸档那个 key。
     *
     * 服务端据此分流：[LAUNCH_FLAG_EXPANDED] 走 `setUserPositionInfo`、
     * [LAUNCH_FLAG_COLLAPSED] 走 `fullToMini`。
     */
    const val ZOOM_LAUNCH_FLAGS_KEY = "android:activity.mZoomLaunchFlags"

    /** 直接递给 `OplusZoomWindowManager` 的那个 `Bundle` 里「窗口模式」的 key（下划线形态）。 */
    const val EXTRA_WINDOW_MODE_KEY = "extra_window_mode"

    /** [EXTRA_WINDOW_MODE_KEY] 取这个值才是小窗 —— `OplusZoomWindowManager.WINDOWING_MODE_ZOOM`。 */
    const val WINDOWING_MODE_ZOOM = 100

    /** [ZOOM_LAUNCH_FLAGS_KEY] 的**展开**档：居中的普通小窗，沿用上次的位置与尺寸。 */
    const val LAUNCH_FLAG_EXPANDED = 2

    /** [ZOOM_LAUNCH_FLAGS_KEY] 的**收起**档：右上角一条的最小化窗（几何由服务端自己算）。 */
    const val LAUNCH_FLAG_COLLAPSED = 32
}

/**
 * 「按键级覆盖公共项」这一组设置 —— **单键与组合键共用这一份契约**。
 *
 * ## 这一族回答什么
 *
 *  公共设置页（`Screen.GENERAL`）里那几项「按键怎么工作」的开关，每一档（一颗键 / 一组
 *  组合键）都可以自己决定要不要另设一份：走不走 ColorOS 专有接口、振不振、振多重、
 *  弹不弹提示、提示写什么。这就是「按键单独设置」——**不是**把手势判定参数收拢到一页
 *  （那些本来就是每颗键一份，见 `KeyConfig.doubleClickMs` 那一族）。
 *
 * ## 为什么是一个接口
 *
 *  `KeyConfig` 与 `ComboKey` 在这一件事上**没有任何区别**，执行侧也不该为组合写第二条分支
 *  （那种分支正是把组合键当成独立存在的来源）。取值出口只有 `Config` 上那五个
 *  `xxxFor(owner)`：传谁进来都一样。
 *
 * ## 总开关怎么管
 *
 *  [useOwnSettings] 关着（默认）⇒ 下面那几项一律看公共设置，本对象上那几个值不参与取值；
 *  开着 ⇒ **全部**用本对象这份（没写过的那几格是**出厂默认**，不是公共值的拷贝）。
 *  不做「逐项开关」：一个总开关 + 一整份值，
 *  用户不用为每一项先想「这一项我要不要单独设」。
 */
interface OwnSettings {
    /**
     * 这一档要不要用自己那套公共项 —— 默认关 = 跟随公共设置。
     *
     *  开着就是**这一档自己那份值**：没写过的那几格取**出厂默认**，不抄公共值
     *  （口径见 `docs/changelog/2026-11-17-独立设置去掉播种.md`）。
     */
    val useOwnSettings: Boolean

    /** 这一档自己的「使用 ColorOS 接口」—— 只在 [useOwnSettings] 开着时算数。 */
    val colorOsApi: Boolean

    /** 这一档自己的「振动反馈」—— 只在 [useOwnSettings] 开着时算数。 */
    val vibrate: Boolean

    /** 这一档自己的「振动强度」（弱 / 中 / 强，见 `GlobalConfig.VIBRATE_STRENGTH_RANGE`）。 */
    val vibrateStrength: Int

    /**
     * 这一档自己的「**无操作时振动**」—— 只在 [useOwnSettings] 开着时算数。
     *
     *  与 [vibrate] 分开：那一位管「真的执行了动作」的那一下；这一位管「按了、但这一档
     *  什么动作都没有」时也给一次触觉回执 —— 否则用户分不出「这一档没绑」与「模块没生效」。
     */
    val vibrateOnNone: Boolean

    /** 这一档自己的「执行时提示」—— 只在 [useOwnSettings] 开着时算数。 */
    val toastOnRun: Boolean

    /**
     * 这一档自己的「提示走流体云」—— 只在 [useOwnSettings] 开着时算数。
     *
     *  它只决定「提示长在哪儿」：关着走 Toast（注入侧那颗系统身份气泡），开着走流体云胶囊
     *  （宿主自己的 Seedling SDK，见 `host/action/notice/FluidAction`）。文案仍是
     *  [toastTemplate] 那一句 —— 换个落点不该换一套说法。
     */
    val toastFluid: Boolean

    /** 这一档自己的「提示走通知」—— 只在 [useOwnSettings] 开着时算数（与 [toastFluid] 互斥，见 [noticeMode]）。 */
    val toastNotify: Boolean

    /**
     * 这一档**生效的那一档落点** —— 界面菜单读它显示当前选中项，执行侧读它决定怎么发。
     *
     *  唯一出口是 [NoticeMode.of]（三颗布尔 → 四档）：这里只是把这一档自己的那三颗递过去。
     *  「不提示」不是一个开关，而是 [toastOnRun] 关着这一种状态。
     */
    val noticeMode: NoticeMode
        get() = NoticeMode.of(toastOnRun, toastNotify, toastFluid)

    /** 这一档自己的提示文案模板（空串 = 用默认那句，口径同 `GlobalConfig.toastTemplate`）。 */
    val toastTemplate: String
}

/**
 * 流体云胶囊那颗图标的**落点** —— 界面层与宿主侧各写一半，名字必须同一份。
 *
 *  `fluid:` 的 `logo` 可以是 `content://`（系统自己弹相机胶囊时就这么传），它指向宿主
 *  `FileProvider` 映射的那个目录；文件**只由界面层画**（`ui/model/icon/FluidCardArt`，
 *  那里有 Compose，能把 Tabler 的 `ImageVector` 画成位图），宿主侧只负责「找到它、组出 uri」。
 *
 *  **没选图标就不留文件、也不画默认那颗**：那种命令（`fluid:正文`）执行时 `logo` 传空，
 *  胶囊左边那一格空着。以前这里有一套「默认图标 + 宿主兜底」的链路，于是一模一样的胶囊分不出
 *  「选过默认那颗」和「压根没选」，2026-11-13 按用户口径拆掉了。
 */
object FluidCard {
    /** 宿主 `files/` 下的共享目录（`FileProvider` 的 `share_images` 路径段）。 */
    const val SHARE_DIR = "share_images"

    /**
     * 「空 id」那一档的文件名。
     *
     *  现在**没有任何一条路会写出这个文件**（没选图标时执行侧直接给空 `logo`，不经这里）。
     *  留着是因为 [nameOf] 收的是一个 String、理论上能被传空 —— 让它落到一个确定的名字上，
     *  比拼出 `okey-fluid-.png` 这种残名再去查文件好。
     */
    const val ICON_NAME = "okey-fluid.png"

    /** 渲染尺寸（模板里那个槽就按这个量级画）。 */
    const val ICON_SIZE = 36

    /**
     * 某个图标 id 对应的文件名。
     *
     *  一个 id 一个文件：选择器里点一下就把那颗渲染成 png 存下来，命令里只记 id，
     *  执行时按 id 找回文件 —— 换图标不用重渲染别的，也不会互相覆盖。
     */
    fun nameOf(icon: String): String = if (icon.isEmpty()) ICON_NAME else "okey-fluid-$icon.png"

    /**
     * 「执行时提示」走流体云时，**被执行的那个动作**的图标文件名。
     *
     *  与 `fluid:` 那一档的区别：那边是用户点选的字形（id 就存在命令里），这边的图标由
     *  **动作本身**决定（`actionCellIcon`），而 token 可能是一整条 shell 命令 —— 直接拼进
     *  文件名会越界 / 带非法字符，所以按 [String.hashCode] 取一个稳定的短名。
     *
     *  两头必须同一份：界面层按这个名字落盘（`ui/model/icon/ActionCardArt`），宿主执行时
     *  按同一个名字找回来（`host/action/notice/FluidAction`）。
     *
     *  **不能只取 `hashCode`**：32 位哈希真的会撞（撞了就是后渲染的那张图覆盖前一张，
     *  提示气泡里显示成另一个动作的图标，清理旧图时也会把两条当成一条）。所以取 token 的
     *  摘要前 16 位十六进制 —— 仍然定长、仍然只含 `[0-9a-f]`，撞的概率降到可忽略。
     */
    fun actionNameOf(
        token: String,
        identity: String = "",
    ): String = "$ACTION_PREFIX${digestOf(if (identity.isEmpty()) token else "$token|$identity")}.png"

    /**
     * 执行身份 → 图标名的那一段后缀（空串 = 默认那档）。
     *
     *  **同一条命令按身份分三张图**（sh / root / Shizuku）：图标本身由 `commandIcon` 按
     *  `root` / `viaShizuku` 挑，而文件名若只认 token，改完开关之后那张旧图会一直留着
     *  （渲染那边「已经有就不重画」）—— 表现是「换成 Shizuku 了，胶囊还是终端图标」。
     *
     *  顺序与 `commandIcon` 的分支一致：Shizuku 优先（执行侧也先判它）。
     */
    fun identityOf(
        root: Boolean,
        viaShizuku: Boolean,
    ): String =
        when {
            viaShizuku -> "sui"
            root -> "root"
            else -> ""
        }

    /** token 的稳定短摘要（12 个十六进制字符）—— 见 [actionNameOf] 里为什么不用 `hashCode`。 */
    private fun digestOf(token: String): String =
        try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md
                .digest(token.toByteArray(Charsets.UTF_8))
                .take(DIGEST_BYTES)
                .joinToString("") { byte -> "%02x".format(byte) }
        } catch (e: Throwable) {
            //  取摘要都不成（理论上不会）：退回老的 hashCode，至少名字定长且不是空
            XLog.w("动作图标名：取摘要失败，退回 hashCode（${e.javaClass.simpleName}）")
            token.hashCode().toUInt().toString(HEX_RADIX)
        }

    /**
     * [actionNameOf] 那一族文件名共用的前缀 —— 清理已经不在配置里的旧图时靠它认出来
     * （`ui/model/icon/ActionCardArt`），别拿去认 `fluid:` 那一档。
     */
    const val ACTION_PREFIX = "okey-action-"

    /** [actionNameOf] 里那个进制的基数 —— 只是为了让那一行不出现裸数字。 */
    private const val HEX_RADIX = 16

    /** [digestOf] 取摘要的前几个字节（12 个十六进制字符）—— 够短、也够不撞。 */
    private const val DIGEST_BYTES = 6
}

/**
 * 「系统状态变量」—— 把**系统场景那几档判定**当成只读变量写进命令的文本里。
 *
 * ## 两种写法：输出值 / 文案
 *
 *  与自定义变量共用同一套 `${名字}`（展开见 [Vars.expand]），名字是 `sys.<条件 id>`：
 *
 * ```
 * fluid:现在是${sys.ringer.text}          → 现在是静音
 * shell:echo ${sys.ringer} > /tmp/mode    → 写 silent
 * shell:echo ${sys.app}                   → 写 com.tencent.mm
 * fluid:前台是${sys.app.text}              → 前台是微信
 * ```
 *
 *   · **不带后缀** = 输出值，即**场景条件那一套词表**（`normal` / `vibrate` / `silent`、
 *     `on` / `off` / `locked` …）。要拿去比较 / 记日志 / 写给别的程序就用它：它与
 *     「场景里写 `ringer:vibrate` 会命中」是同一个串，两边共用同一份判据（`ConditionProbe`）。
 *   · **[SYSTEM_VAR_TEXT_SUFFIX]**（`${sys.ringer.text}`）= **文案**：界面上念的那几个字
 *     （`静音` / `开` / `亮屏`），跟着界面语言走。要弹给人看就用它。
 *     「前台应用」那一档的文案是**应用名**（`微信`），输出值是包名。
 *
 *  界面上点一行时先问「要哪一种」，两行的样子就是此刻这个值分别在两种写法下的结果。
 *  不默认替你选：一段弹给人的字拿去比较会永远不相等，反过来一句英文键名弹出来也不像话。
 *
 * ## 哪些档能当变量
 *
 *  [ConditionKind.APP] 能：取值就是**此刻前台那个应用的包名**（输出值 `com.tencent.mm`、
 *  文案值是应用名「微信」）—— 它回答的正是「此刻是什么」这个问法。
 *
 *  [ConditionKind.APP_NOT] 不能：那一档的取值是一组包名，问的是「前台在不在这一组里」，
 *  没有「此刻是哪一组」这个说法，所以不进 [SYSTEM_VAR_KINDS]。
 *
 *  [ConditionKind.TIME] 是**另一种值**：它没有候选档。**文案值**是此刻的时刻（`HH:mm`，补零，
 *  `fluid:现在${sys.time.text}` → 「现在14:30」）；**输出值**是**时间戳**（毫秒，
 *  `System.currentTimeMillis()`）—— 场景条件那边比的是区间，而命令里要的通常是「这一刻是什么
 *  时候」这个能比较、能相减的数。
 *
 * ## 判不出就原样留着
 *
 *  读不到（系统服务拿不到、手电筒那个键还没出现过）时**没有值**，`${sys.xxx}` 按原文留在命令里
 *  —— 与「认不出的变量名原样保留」同一条纪律（见 [Vars.expand] 的类注释）。时刻不存在这一档：
 *  时钟总是读得到。
 *
 * ## 改不了
 *
 *  它们**只读**：`setvar:` 只认用户表里那几颗（见 [Vars.assign]），所以 `setvar:sys.ringer=silent`
 *  会按「变量不存在」失败 —— 状态归系统管，不归命令管。
 *
 * ## 与用户变量撞名
 *
 *  `sys.` 只是**约定**，不是保留字：查值先查用户那份表（[Vars.find] 取第一个命中），所以用户真建了
 *  一颗叫 `sys.ringer` 的变量时，**用户那一颗赢**。`sys.` 前缀要的就是「正常不会撞」。
 */
const val SYSTEM_VAR_PREFIX = "sys."

/**
 * 「要文案」那一档的后缀：`${sys.wifi.text}` 展开成「开」，`${sys.wifi}` 展开成 `on`。
 *
 *  取 `.text` 而不是再加一个前缀，是为了让两种写法留在同一个名字族里 —— 界面上那一行点下去
 *  复制的就是这两串之一，眼睛能直接对上。
 */
const val SYSTEM_VAR_TEXT_SUFFIX = ".text"

/** 这一档在变量里的名字（`sys.ringer`）；说不清「此刻是什么」的那一档（「前台应用以外」）返回 `null`。 */
val ConditionKind.systemVarName: String?
    get() =
        when (this) {
            //  取值是一组包名、问的是「在不在这一组里」：没有「此刻是哪一组」这个说法，所以不当变量
            ConditionKind.APP_NOT -> null

            //  `APP` 能当变量：取值就是此刻前台那个应用的包名（见 `SYSTEM_VAR_PREFIX` 那段）
            else -> SYSTEM_VAR_PREFIX + id
        }

/** 这一档的**输出值**写法（`${sys.ringer}`）—— 界面那一列与文案共用这一处。 */
val ConditionKind.systemVarRef: String?
    get() = systemVarName?.let { "\${$it}" }

/** 这一档的**文案**写法（`${sys.ringer.text}`）—— 同上。 */
val ConditionKind.systemVarTextRef: String?
    get() = systemVarName?.let { "\${$it$SYSTEM_VAR_TEXT_SUFFIX}" }

/**
 * 能当变量用的那几档，顺序与「系统场景」的条件菜单同一份（[ConditionKind.entries]）。
 *
 *  界面上这一列就是它 —— **不要再手写第二张名单**：场景那边加一种条件，这里自动多一行；
 *  两种清单各写一份的话，加条件的人一定只记得改其中一处。
 */
val SYSTEM_VAR_KINDS: List<ConditionKind> = ConditionKind.entries.filter { it.systemVarName != null }

/** 一个系统变量引用解析出来的两样：哪一档 + 要**文案**还是要**输出值**。 */
data class SystemVar(
    val kind: ConditionKind,
    /** `true` = 带 [SYSTEM_VAR_TEXT_SUFFIX]，要界面上那句文案；`false` = 要场景条件那一套取值。 */
    val asText: Boolean = false,
)

/**
 * `${sys.xxx}` 里的名字 → 哪一档、要哪一种；不是系统变量 / 认不出 / 这一档说不清「此刻是什么」
 * 都返回 `null`。
 *
 *  执行侧拿它把「一个名字」翻成「去读哪一档状态、念哪一种」（见 `ConditionProbe.varValue`）。
 */
fun systemVarOf(name: String): SystemVar? {
    val text = name.trim()
    if (!text.startsWith(SYSTEM_VAR_PREFIX)) return null
    val body = text.removePrefix(SYSTEM_VAR_PREFIX)
    val asText = body.endsWith(SYSTEM_VAR_TEXT_SUFFIX)
    val id = if (asText) body.removeSuffix(SYSTEM_VAR_TEXT_SUFFIX) else body
    val kind = ConditionKind.fromId(id)?.takeIf { it.systemVarName != null } ?: return null
    return SystemVar(kind, asText)
}

/** 只要「这是哪一档」（两种写法都算同一档）；认不出返回 `null`。 */
fun systemVarKind(name: String): ConditionKind? = systemVarOf(name)?.kind

/**
 * **按机型标定的小窗几何** —— 展开档小窗的落点与缩放，**一台机器一档**。
 *
 * ## 为什么键是「机型」而不是 ROM
 *
 * 这两个数由**屏幕尺寸与 dpi** 决定（本机 `1272×2772 @560dpi` 上量出来的），换一块屏就得重新量；
 * 而 [RomProfile] 那张表按 **ROM 系列**分档（同一版 ROM 在不同机型上落点相同）。
 * 两张表的键不同，正是这个原因 —— 别把它们并成一张。
 *
 * ## 为什么收成一张表
 *
 * 宿主侧（下发几何）与注入侧（宿主没带参数时的兜底）原来各写一份**同一组数**：
 * 改一处漏一处时，表现是「宿主下发的与注入侧兜底的对不上」—— 而兜底那条路只在宿主没带参数时
 * 才走得到，平时完全看不出来。
 *
 * ## 加一台机型要做什么
 *
 * 1. 这里加一个 [Slot]（`model` 与 [DeviceSupport] 同口径：`Build.MODEL`）；
 * 2. 在 [DeviceSupport.ADAPTED] 里加那一行（那道闸管「能不能注入」，与本表各管一件事）；
 * 3. 真机上量一遍：拿**原生小窗**的 `Task.mBounds` 反查配置矩形，取法与两个坑见
 *    `docs/features/zoom-window/notes.md` 与 `docs/features/zoom-window/api.md` 附七。
 *
 * ## 本机没适配时「照最后一档试」
 *
 * 与 `RomProfile` 里「接管点 / 反射面」同一档待遇（而不是内置命令落点那种「不猜」）：
 * 几何猜错的表现是**窗口位置或大小不对**（看得见、能改），不会去动别的东西；而 debug 包关掉了
 * 清单，开发要在任意机器上能试。落档时会打一行 `w` 说明用的是哪一档。
 */
object MiniWindowGeometry {
    /**
     * 一台机器的小窗几何。
     *
     * @param model `android.os.Build.MODEL` 的真机实测值（与 [DeviceSupport] 同口径）
     * @param label 给人看的机型名（日志用）
     * @param verified 真机验证记录（日期 + 量法出处）
     * @param left / @param top / @param right / @param bottom **应用配置矩形**的四个边 ——
     *   不是屏幕矩形：系统会把它的尺寸**再乘一次 [scale]** 才是屏幕上的实际大小
     *   （`1272 × 2261 × 0.8017 = 1020 × 1812`，与真机一致）
     * @param scale 显示缩放
     */
    class Slot(
        val model: String,
        val label: String,
        val verified: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val scale: Float,
    ) {
        /**
         * 应用配置矩形 —— **不是**屏幕矩形。
         *
         * 踩过的坑：曾经直接填**屏幕矩形**（1020×1812），结果被系统又缩了一次
         * （`1020 × 0.8017 = 817`），窗口整整齐齐小了 0.8017 倍，表现为「小窗是对的，
         * 但比 ColorOS 原生的小一圈」。正确值是拿真机「原生小窗」的 `dumpsys activity activities`
         * → `Task.mBounds` 反查出来的（见 `docs/features/zoom-window/api.md` 附七）。
         *
         * 落点（left / top）是**直接照搬到屏幕坐标**的、不含缩放，所以两头是同一个数。
         *
         * 不要拿 `getMiniFlexibleTaskInfo` 的值代替 —— 那是**收起档**的几何
         *（约 `Rect(898,168-1203,710)` / `0.2397`），会把窗口缩成右上角一条。
         */
        val bounds: android.graphics.Rect get() = android.graphics.Rect(left, top, right, bottom)
    }

    /** **各机型的小窗几何 —— 加一台机器就在这里加一个 [Slot]**。 */
    private val SLOTS: List<Slot> =
        listOf(
            Slot(
                model = "PLK110",
                label = "OnePlus 15（PLK110）",
                verified = "1272×2772 @560dpi；2026-10-14 按原生小窗 Task.mBounds 反查",
                left = 126,
                top = 319,
                right = 1398,
                bottom = 2580,
                scale = 0.8017f,
            ),
        )

    /**
     * 本机这一档；`null` = 这台机器还没量过。
     *
     * 进程内算一次：机型不会中途变，而这两个数在**每次播小窗动画**时都要读。
     */
    val matched: Slot? by lazy {
        val model = DeviceSupport.currentModel
        SLOTS.firstOrNull { it.model.equals(model, ignoreCase = true) }
    }

    /**
     * 实际用的那一档 —— 本机没量过时**照最后一档试**并打一行 `w`（理由见类注释）。
     *
     * `SLOTS` 是常量表、不会是空的。
     */
    val effective: Slot by lazy {
        val model = DeviceSupport.currentModel
        matched
            ?: SLOTS.last().also {
                XLog.w("本机机型 $model 不在小窗几何表里 ⇒ 照 ${it.label} 那一档试（窗口大小可能不对）")
            }
    }

    /** 本机档位的**一句话描述** —— 安装期判据行用它。 */
    fun describeCurrent(): String {
        val model = DeviceSupport.currentModel
        val slot = matched ?: return "未标定（本机机型 ${model.ifEmpty { "读不出来" }}）"
        return "${slot.label}（${slot.left},${slot.top}-${slot.right},${slot.bottom} × ${slot.scale}）"
    }
}

/** 引用写法的开头：`${`。 */
private const val OPEN = "\${"

/** 引用写法的结尾。 */
private const val CLOSE = "}"

/**
 * 一份变量的**当前值**，按名字查、按类型改。
 *
 * ## 引用怎么写
 *
 *  在命令的文本里写 `${名字}`，执行前整串替换成它的值（见 [expand]）：
 *
 * ```
 * shell:am broadcast -a test --ei count ${计数}
 * ```
 *
 *  认不出的名字**原样留着**（`${x}` 就这么执行）—— 宁可让用户看见命令里有个没替换的占位，
 *  也不要静默换成空串：那种「命令少了一个参数」在日志里几乎看不出来。
 *
 * ## 修改怎么写
 *
 *  命令里写一条 `setvar:<名字>=<值>`（`+=` 相加 / 拼接、`-=` 相减、`!` 取反，见 [VarOp]），
 *  执行侧走 [assign]。**变量必须已经存在** —— 类型是建的时候定死的，命令不能顺手造一个出来。
 */
data class Vars(
    val all: List<Variable> = emptyList(),
) {
    /** 按名字找（名字首尾空白不算数）；不在返回 `null`。 */
    fun find(name: String): Variable? = all.firstOrNull { it.name == name.trim() }

    /**
     * 把 [text] 里的 `${名字}` 全换成值。
     *
     *  单趟扫描（不是反复 `replace`）：替换进去的值里若也写着 `${...}`，**不再展开** ——
     *  否则两个变量互相引用时这里会一直展开下去。
     *
     * @param fallback 用户表里**查不到**这个名字时再问一句（系统状态变量 `${sys.xxx}` 走这条，
     *   见 [systemVarRef]）：返回 `null` = 也认不出，按「原样保留」处理。
     *   **只在查不到时才调** —— 正常命令里一个系统变量都不写，就一次也不会碰到它。
     */
    fun expand(
        text: String,
        fallback: (String) -> String? = { null },
    ): String {
        if (!text.contains(OPEN)) return text
        val out = StringBuilder(text.length + 16)
        var at = 0
        while (at < text.length) {
            val start = text.indexOf(OPEN, at)
            val end = if (start < 0) -1 else text.indexOf(CLOSE, start + OPEN.length)
            if (start < 0 || end < 0) {
                //  剩下的部分没有完整引用（或引用没写完）：原样带上、结束
                out.append(text, at, text.length)
                break
            }
            out.append(text, at, start)
            val name = text.substring(start + OPEN.length, end).trim()
            //  用户表优先（同名时用户那一颗赢，见 `SYSTEM_VAR_PREFIX` 那段说明）
            val value = find(name)?.value ?: fallback(name)
            if (value == null) {
                //  认不出的名字原样保留（见类注释）
                out.append(text, start, end + CLOSE.length)
            } else {
                out.append(value)
            }
            at = end + CLOSE.length
        }
        return out.toString()
    }

    /**
     * 按 [set] 改一个变量，返回**新的一份**（[Vars] 是不可变的，调用方负责存回去）。
     *
     * @return 变更后的值；变量不存在 / 类型对不上 / 值归一不了都返回 `null`（命令算失败，不改盘）
     */
    fun assign(set: Action.VarSet): Vars? {
        val target = find(set.name) ?: return null
        val next = target.applied(set, this) ?: return null
        return Vars(all.map { if (it.name == target.name) next else it })
    }
}

/**
 * 一个变量被 [Action.VarSet] 改过之后的新样子；类型不符 / 值归一不了都返回 `null`。
 *
 *  值里的 `${...}` 先按 [vars] 展开 —— 于是 `setvar:总数+=${单条}` 这种「拿另一个变量算」
 *  不需要额外的表达式语法。
 */
private fun Variable.applied(
    set: Action.VarSet,
    vars: Vars,
): Variable? {
    if (set.op == VarOp.NOT) return type.negate(value)?.let { copy(value = it) }
    val text = vars.expand(set.value)
    val next =
        when (set.op) {
            VarOp.SET -> type.coerce(text)
            VarOp.ADD -> type.add(value, text)
            VarOp.SUB -> type.sub(value, text)
            VarOp.NOT -> null
        }
    return next?.let { copy(value = it) }
}

/**
 * 把动作里的**文本载荷**按当前变量展开一次。
 *
 *  为什么不在 `Action.parse` 里展开：解析发生在**存配置时**，而值要到**执行时**才知道。
 *  所以解析照旧留下 `${名字}` 原样（那是用户写的东西，得能被编辑），执行前才在这里换一次。
 *
 *  只碰「会进命令 / 目标 / 正文 / 名字」的那几种：[Action.VarSet] 自己的值不在这里展开
 *  （它在赋值那一步展开，且要用同一份 [Vars]），键码那种数字载荷也不碰。
 *
 *  **`shell:` 不在这里**：那一族有三条进来的路（内置动作、单条命令的正文、多条命令的步骤），
 *  只有内置那一支会经过这里 —— 放在这儿的话，自定义命令里的 `${…}` 会静默不展开。所以它的展开点
 *  沉到唯一那个出口（`ActionRunner.runShell`），三条路共用一份。
 *
 * @param fallback 见 [Vars.expand]：用户表查不到时再问一句（系统状态变量）。
 */
fun Action.expandedWith(
    vars: Vars,
    fallback: (String) -> String? = { null },
): Action =
    when (this) {
        is Action.Intent -> copy(action = vars.expand(action, fallback))

        is Action.App -> copy(target = vars.expand(target, fallback))

        is Action.AppWindow -> copy(target = vars.expand(target, fallback))

        is Action.Custom -> copy(name = vars.expand(name, fallback))

        //  通知 / 流体云 / 提示的**正文**：最想写变量的就是这三处（「弹一下现在的响铃状态」）
        is Action.Notify -> copy(message = vars.expand(message, fallback))

        is Action.Fluid -> copy(message = vars.expand(message, fallback))

        is Action.Toast -> copy(message = vars.expand(message, fallback))

        else -> this
    }
