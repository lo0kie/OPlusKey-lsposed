package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.rom.RomProfile

/**
 * `app:` / `window:` / `appshortcut:` 动作的**目标写法** —— 包名 / 组件名，外加一个可选的多开用户维度。
 *
 * ## 写法
 *
 * ```
 * com.tencent.mm                          纯包名，主用户（user 0）
 * com.tencent.mm/.ui.LauncherUI           显式组件，主用户
 * 999@com.tencent.mm                      纯包名，分身（user 999）
 * 999@com.tencent.mm/.ui.LauncherUI       显式组件，分身
 * ```
 *
 *  前缀规则是 `<userId>@`，**缺省即主用户** —— 老配置里那些 `app:com.tencent.mm/.ui.LauncherUI`
 *  一个字都不用改，它们仍然指向主用户。加一个 0 前缀（`0@pkg`）也认，写出来只是为了显式。
 *
 * ## 为什么用 `@` 而不是别的
 *
 *  包名与类名只允许字母、数字、下划线与点，**不含 `@`**；而 `@` 在人类读起来也像个「地址前缀」，
 *  与 `user@host` 同一套直觉。用 `/` 不行（组件名已经在用），用 `:` 不行（token 前缀在用）。
 *
 * ## 显示名**不再**塞在这里
 *
 *  早先的写法是 `999@包名/类名|微信 2` —— 名字跟在目标后面，靠 `|` 切回来。那是错的：
 *  名字是**显示信息**，不是目标的一部分，塞进去之后每个读它的地方都得先剥一层，剥漏一处就
 *  退化成包名（真机反馈「选完快捷方式 / 界面，再进来显示的是包名」）。现在名字单独存在
 *  `Config.appNames`（按身份索引），见 `AppNames`。
 *
 *  [label] 只为**读老配置**留着：那些文件里已经写着 `|名字`，删掉它老绑定就显示不出名字。
 *  [identity] 是「这个绑定是谁」（去掉那截名字之后的部分），名字表的键、去重、比较都用它 ——
 *  新写的目标不带名字，所以它对新数据就是原串。
 */
object AppTarget {
    /** 用户前缀与目标之间的分隔符（口径见类注释）。 */
    private const val SEPARATOR = '@'

    /** 老配置里目标与显示名之间的分隔符 —— **只读不写**，见类注释。 */
    private const val LEGACY_LABEL_SEPARATOR = '|'

    /**
     * 拼一个目标写法（**不带显示名**，见类注释）。
     *
     * @param userId 多开用户（`UserHandle` 的 id）；传 `0` 表示主用户 —— 那时**不加前缀**，
     *   产出与老配置逐字相同
     */
    fun format(
        target: String,
        userId: Int,
    ): String = if (userId == 0) target else "$userId$SEPARATOR$target"

    /**
     * 老配置里跟在 `|` 后面的显示名；没有这一段返回 `null`。
     *
     *  **只用于读旧数据与兜底显示**：新写的目标不带它（见类注释），新数据里的名字在
     *  `Config.appNames`。
     */
    fun label(target: String): String? =
        target
            .substringAfter(LEGACY_LABEL_SEPARATOR, "")
            .ifEmpty { null }

    /**
     * 剥掉老配置里那截显示名，其余（用户前缀 + 组件）保留 —— 就是**这个绑定的身份**。
     *
     *  新数据里它等于原串（没有 `|` 可剥），所以「身份」这一层可以无条件走它：
     *  `Config.appNames` 的键、去重、比较，全都用同一套口径。
     */
    fun identity(target: String): String = target.substringBefore(LEGACY_LABEL_SEPARATOR)

    /**
     * 目标里的用户 id —— **没有前缀就是 0**（主用户）。
     *
     *  只在 `@` 前面的部分**全是数字**时才认：`@` 出现在别处（比如某个手改坏的包名）时
     *  整串按主用户处理，交给后面的解析去失败 —— 不在这一层抛，避免「一个坏绑定崩掉整页」。
     */
    fun userId(target: String): Int {
        val plain = identity(target)
        val end = userPrefixEnd(plain)
        return if (end < 0) 0 else plain.substring(0, end - 1).toInt()
    }

    /** 去掉用户前缀与（老配置的）显示名之后的那一段（`pkg` 或 `pkg/activity`）—— 解析组件、查包都用它。 */
    fun withoutUser(target: String): String {
        val plain = identity(target)
        val end = userPrefixEnd(plain)
        return if (end < 0) plain else plain.substring(end)
    }

    /**
     * 用户前缀的结尾下标（`@` 之后那一位）；`-1` = **没有前缀**。
     *
     *  [userId] 与 [withoutUser] 必须走同一处判据：分开判时「前缀看起来是数字、但装不进 Int」
     *  （`99999999999@pkg`）会被一边当成 0、另一边却照样剥掉前缀 —— 一个坏前缀静默变成
     *  「主用户 + 合法目标」，而不是按坏绑定失败。
     */
    private fun userPrefixEnd(plain: String): Int {
        val at = plain.indexOf(SEPARATOR)
        if (at <= 0) return -1
        val head = plain.substring(0, at)
        if (!isAsciiDigits(head)) return -1
        return if (head.toIntOrNull() == null) -1 else at + 1
    }

    /** 只要包名（给 `getPackageInfo` / 图标 / 名字用）。 */
    fun pkg(target: String): String = withoutUser(target).substringBefore('/')

    /** 显式组件那一段（`包名/类名` 的类名部分），没有就返回 `null`。 */
    fun activity(target: String): String? = withoutUser(target).substringAfter('/', "").ifEmpty { null }

    /**
     * 两个目标**是不是同一个绑定** —— 忽略显示名，只比用户 + 组件。
     *
     *  选择页拿它比「已选中的那个」与清单里的行：清单那行的名字是这次现查的，
     *  绑定里存的是上次存下的，应用改过名就会对不上。用 `==` 比会让选中凭空失效
     *  （`SelectionPrune` 会把 `pending` 清掉，确认键跟着消失）。
     *
     *  任一方为 `null` 一律 `false`：调用点的「选中项」是可空的，传进来的多半是 `pending`，
     *  拿 `null` 去比不该算「匹配上了」。
     */
    fun same(
        a: String?,
        b: String?,
    ): Boolean =
        a != null && b != null &&
            userId(a) == userId(b) && withoutUser(a) == withoutUser(b)

    /**
     * 两个目标是不是**同一个应用** —— 忽略 `/类名`（或快捷方式的 `/id`）与显示名。
     *
     *  与 [same] 的分工：那个问的是「是不是同一个绑定」，`包名` 与 `包名/类名` 在它眼里是两回事；
     *  这个问的是「是不是同一个应用」——「选择应用」页一行一个包，而绑定里可能带着具体界面，
     *  要判「这一行是不是当前选中的那个应用」只能用它。
     */
    fun sameApp(
        a: String?,
        b: String?,
    ): Boolean =
        a != null && b != null &&
            pkg(a) == pkg(b) && userId(a) == userId(b)
}

/**
 * 全是 ASCII `0-9` 且非空 —— 与 `toIntOrNull()` 同一套口径。
 *
 *  别用 `Char.isDigit()`：它认 Unicode 的十进制数字（全角 `０-９`、阿拉伯-印度数字等），
 *  而 `toIntOrNull()` 只解析 ASCII ⇒ 两者口径不一致时，前缀会被剥掉却解析成 0。
 *
 *  放在文件级而不是 object 里：`AppTarget` 的函数数已经顶到 detekt 的阈值。
 */
private fun isAsciiDigits(s: String): Boolean = s.isNotEmpty() && s.all { it in '0'..'9' }

/**
 * 「同一个应用」在**多选集合 / 去重**里的键 —— `包名`（主用户）或 `999@包名`（分身）。
 *
 *  为什么不能直接用 [AppTarget.pkg]：那一位把用户维度抹掉了，于是主用户与分身共用一格 ——
 *  多选列表里「选一个亮两个」、集合里也分不出是哪一个（真机反馈）。这一位保留用户前缀，
 *  主用户那档与以前逐字相同（`format` 对 `userId == 0` 不加前缀）。
 *
 *  @param target 任何形状的目标写法（`pkg` / `pkg/类名` / `999@pkg/类名`）都行
 */
fun appKeyOf(target: String): String = AppTarget.format(AppTarget.pkg(target), AppTarget.userId(target))

/**
 * 快捷方式目标的写法：`包名/<转义后的 id>`（外加用户前缀）。
 *
 *  为什么要转义：`<pkg>/<id>` 用 `/` 与 `@` 分隔一段段，而**快捷方式的 id 是应用自己起的**，
 *  里面完全可能有 `/`（`share/wechat`）或 `|`（老写法里「目标 | 显示名」的分隔符）；那个 id
 *  一旦原样拼进去，绑定再也读不回来（`substringAfter('/')` 只取到第一段）。
 */
fun shortcutTarget(
    pkg: String,
    id: String,
    userId: Int,
): String = AppTarget.format("$pkg/${shortcutIdEscape(id)}", userId)

/**
 * 快捷方式 id → 目标里的那一段（见 [shortcutTarget]）。
 *
 *  只转义三个字符（`%` / `/` / `|`），其余原样：**不含这三个字符的 id 与以前逐字相同**，
 *  已经绑好的 `appshortcut:` 一个都不用迁移。
 */
fun shortcutIdEscape(id: String): String =
    buildString(id.length) {
        for (ch in id) {
            when (ch) {
                '%' -> append("%25")
                '/' -> append("%2F")
                '|' -> append("%7C")
                else -> append(ch)
            }
        }
    }

/** [shortcutIdEscape] 的逆 —— 认不出的 `%xx` 原样留着（手改坏的值不该在这里吞掉）。 */
fun shortcutIdUnescape(segment: String): String {
    if ('%' !in segment) return segment
    val out = StringBuilder(segment.length)
    var i = 0
    while (i < segment.length) {
        val ch = segment[i]
        if (ch == '%' && i + 2 < segment.length) {
            val code = segment.substring(i + 1, i + 3).toIntOrNull(16)
            if (code != null) {
                out.append(code.toChar())
                i += 3
                continue
            }
        }
        out.append(ch)
        i++
    }
    return out.toString()
}

/**
 * 长按那一档的参数 —— **单键与组合键共用这一份视图**。
 *
 * ## 为什么要有
 *
 *  `KeyConfig` 与 `ComboKey` 的长按参数**一字不差**（同一组字段、同一个语义、判定侧走同一套
 * `LongPressSpec`）。界面那一侧原先各写一张卡（`LongRepeatCard` / `ComboLongCard`），
 * 两处行序、文案、可见性判据迟早对不上 —— 用户要的是「按键和组合键的功能完全一样」。
 *
 *  收成接口之后，长按卡只认 [LongPressParams]，传 `KeyConfig` 还是 `ComboKey` 都一样；
 *  两边各自的 `copy` 留在各自的类型里（[withLongPress] 那两个扩展），卡里**没有分支**。
 */
interface LongPressParams {
    val longPressMs: Int
    val longRepeat: Boolean
    val longRepeatIntervalMs: Int
    val longRepeatVibrate: Boolean
    val longFireOnRelease: Boolean
    val longReleaseOn: Boolean
    val longRelease: Action

    /**
     * 松手那一下**真的有事可做** —— 开关开着、挑过动作、且不是「松手才触发」那条。
     * 两位缺一位都不算（数据层的互斥结论，界面不重算）。
     */
    val releaseBound: Boolean
}

/**
 * **连击链**那几档的判定参数 —— 单键 `KeyConfig` 与**按住组合的成员连击**共用这一份视图。
 *
 * ## 为什么要有
 *
 *  按住组合（`ComboKey.mode == MODE_HOLD`、只有一颗成员）上要能配「按住首键、成员键连击」：
 *  首键按住不放，那颗成员键在窗口内双击 / 三击 / 四击 / 单击后长按。这几档在单键上已经有
 *  一整套连击链状态机（`GestureDetector`：窗口、封顶、第 2 击按住变两段式、连发、松手收尾），
 *  而它读的全部参数就是这十几位。
 *
 *  收成接口之后，`GestureDetector` 的参数类型从 `KeyConfig` 换成这个窄视图，**判定逻辑一行不改**
 *  —— 成员连击那边再给一份实现（那一档组合自己的连击参数），状态机照旧那一套。这正是
 *  「状态机复用单键现成的」：没有第二套连击链，只有第二份参数。
 *
 * ## 与 [LongPressParams] 的关系
 *
 *  两个接口不合并：那一份是「长按这一档」的参数，界面上的长按卡只认它；这一份是「同一颗键
 *  再按几下」的参数，只有判定侧读。合并会让长按卡多看到一堆与它无关的字段。
 *  单键与成员连击两边都实现（成员连击里「单击后长按」照旧要判长按）。
 */
interface ClickChainParams : LongPressParams {
    /** 连击链的窗口（两击之间最多隔多久，也是单击要等多久）。 */
    val clickWindowMs: Int

    /** 两种判定时机：`false` = 固定窗口起点（首击抬手排一次），`true` = 每次松手重新起算。 */
    val waitMultiWindow: Boolean

    /**
     * **界面与文案用的正向说法**：`true` = 固定窗口起点（窗口只在首击抬手排一次）。
     *
     *  它就是 [waitMultiWindow] 的反面，**全项目只在这里翻一次**。存储位沿用的是老名字
     *  （落盘键 `wait_multi_window` 已落在用户机器上，改不得），而界面那颗开关叫「固定窗口起点」
     *  —— 一个 bit 两个方向的名字，读写各反一次，三处注释提醒过仍然错过。
     *  所以规矩是：**读**这一侧一律用本属性，不许再写 `!waitMultiWindow`。
     */
    val fixedWindowStart: Boolean get() = !waitMultiWindow

    /** 判定侧数到第几击就**封顶**（按开着的档算，不看绑没绑动作，至少 1）。 */
    val judgeCap: Int

    val doubleClickOn: Boolean
    val clickThenLongOn: Boolean
    val clickThenLongPressMs: Int
    val clickThenLongReleaseOn: Boolean
    val clickThenLongRelease: Action
    val clickThenLongRepeatIntervalMs: Int

    /**
     * 「单击后长按」的**松手时触发** —— 与 [LongPressParams.longFireOnRelease] 同义、各自一份。
     *
     *  判定侧靠它决定「到点那一下要不要派发」（见 `GestureDetector.fireChained`）：
     *  开着就到点只标状态、抬手才执行，与长按那一档走同一支。
     */
    val clickThenLongFireOnRelease: Boolean

    /** 「单击后长按」这一档**本身**有事可做（直接绑定或场景给过动作）—— 松手动作的主语。 */
    val clickThenLongActionExists: Boolean

    /** 「单击后长按」要不要排连发（`repeat` 开关 + 串行命令 / 灵感语音那几条互斥）。 */
    val clickThenLongRepeats: Boolean

    /** 长按要不要排连发（[LongPressParams.longFireOnRelease] + 串行命令 / 灵感语音那几条互斥）。 */
    val longRepeats: Boolean

    /** 场景块给「单击后长按」配了松手动作 —— 注入侧看不到场景表，这一位由装配方算好带进来。 */
    val sceneChainRelease: Boolean
}

/**
 * [GlobalConfig] 那一份的同名正向说法 —— 与 [ClickChainParams.fixedWindowStart] 同一条规矩。
 *
 *  它没有实现 [ClickChainParams]（判定侧不直接读公共那一份），但公共设置页那颗「固定窗口起点」
 *  读的正是它；放在这里是为了让「反一次」的地方只有这两行，而不是散在界面的几个 lambda 里。
 */
val GlobalConfig.fixedWindowStart: Boolean get() = !waitMultiWindow

/**
 * **成员连击**那一份 [ClickChainParams] 视图 —— 由 `Config.comboMemberChain` 现算，
 * 供判定侧（`GestureDetector`）按「按住首键、成员键连击」那套参数走连击链。
 *
 *  不落在 `ComboKey` 自己身上：窗口与判定时机取自**按住组合那张卡**那两行
 *  （`Config.comboWindowsFor` 的按住那一半 + `Config.comboHoldWindowStartFor`，见
 *  `Config.comboMemberChain`），要按「独立设置 / 顺序组合」解析出这一档生效的那一份，
 *  而 `ComboKey` 拿不到那份 `Config` 上下文。所以由装配方 / `Config` 在判定的那一刻现算一份，
 *  喂给判定器。
 *
 *  长按那几位（[LongPressParams]）直接借 `ComboKey` 自己的（成员连击里「单击后长按」的长按
 *  阈值另用 `memberClickThenLongPressMs`）。
 */
internal class MemberChainView(
    private val combo: ComboKey,
    override val clickWindowMs: Int,
    override val waitMultiWindow: Boolean,
) : ClickChainParams {
    override val longPressMs: Int get() = combo.longPressMs
    override val longRepeat: Boolean get() = combo.longRepeat
    override val longRepeatIntervalMs: Int get() = combo.longRepeatIntervalMs
    override val longRepeatVibrate: Boolean get() = combo.longRepeatVibrate
    override val longFireOnRelease: Boolean get() = combo.longFireOnRelease
    override val longReleaseOn: Boolean get() = combo.longReleaseOn
    override val longRelease: Action get() = combo.longRelease
    override val releaseBound: Boolean get() = combo.releaseBound
    override val doubleClickOn: Boolean get() = combo.memberDoubleOn
    override val clickThenLongOn: Boolean get() = combo.memberClickThenLongOn
    override val clickThenLongPressMs: Int get() = combo.memberClickThenLongPressMs
    override val clickThenLongReleaseOn: Boolean get() = combo.memberClickThenLongReleaseOn
    override val clickThenLongRelease: Action get() = combo.memberClickThenLongRelease

    /** 成员连击的「松手时触发」—— 与长按那一档同义，位是这一档自己的。 */
    override val clickThenLongFireOnRelease: Boolean get() = combo.memberClickThenLongFireOnRelease
    override val clickThenLongRepeatIntervalMs: Int get() = combo.memberClickThenLongRepeatIntervalMs
    override val clickThenLongActionExists: Boolean get() = combo.memberClickThenLongActionExists
    override val clickThenLongRepeats: Boolean get() = combo.memberClickThenLongRepeats
    override val longRepeats: Boolean get() = combo.longRepeat && !combo.longFireOnRelease
    override val sceneChainRelease: Boolean get() = combo.scenes.any { it.hasChainRelease }

    /** 数到第几击封顶 —— 按成员连击开着的档算（与单键 `judgeCap` 同一个口径）。 */
    override val judgeCap: Int
        get() =
            maxOf(
                if (combo.memberDoubleOn) 2 else 1,
                combo.memberClicksOn.maxOrNull() ?: 1,
                if (combo.memberClickThenLongOn) 2 else 1,
            ).coerceAtMost(ConfigFields.MAX_JUDGE_CLICKS)
}

/**
 * 长按卡上的一次改动 —— 只带**改了的那几位**，没带的保持原样。
 *
 *  卡片每次只改一格（拨一个开关、拖一根滑杆），用一个「变更包」交回去，
 *  各类型用一个 `with` 扩展把它落到自己的 `copy` 上 —— 这是唯一需要知道具体类型的地方，
 *  而且只在 core 里、每家五行。
 */
data class LongPressEdit(
    val pressMs: Int? = null,
    val repeat: Boolean? = null,
    val repeatIntervalMs: Int? = null,
    val repeatVibrate: Boolean? = null,
    val fireOnRelease: Boolean? = null,
    val releaseOn: Boolean? = null,
    val release: Action? = null,
)

/** 把一次改动落到这颗键上。 */
fun KeyConfig.withLongPress(edit: LongPressEdit): KeyConfig =
    copy(
        longPressMs = edit.pressMs ?: longPressMs,
        longRepeat = edit.repeat ?: longRepeat,
        longRepeatIntervalMs = edit.repeatIntervalMs ?: longRepeatIntervalMs,
        longRepeatVibrate = edit.repeatVibrate ?: longRepeatVibrate,
        longFireOnRelease = edit.fireOnRelease ?: longFireOnRelease,
        longReleaseOn = edit.releaseOn ?: longReleaseOn,
        longRelease = edit.release ?: longRelease,
    )

/** 把一次改动落到这一组组合键上 —— 与 [KeyConfig.withLongPress] 同一个口径。 */
fun ComboKey.withLongPress(edit: LongPressEdit): ComboKey =
    copy(
        longPressMs = edit.pressMs ?: longPressMs,
        longRepeat = edit.repeat ?: longRepeat,
        longRepeatIntervalMs = edit.repeatIntervalMs ?: longRepeatIntervalMs,
        longRepeatVibrate = edit.repeatVibrate ?: longRepeatVibrate,
        longFireOnRelease = edit.fireOnRelease ?: longFireOnRelease,
        longReleaseOn = edit.releaseOn ?: longReleaseOn,
        longRelease = edit.release ?: longRelease,
    )

/**
 * 系统开关的**种类** —— 每一种就是清单里的一行，也是「这一类开关长什么样」的键。
 *
 * ## 为什么是个枚举，而不是一串字符串常量
 *
 *  原先种类 id 是散在各处的字面量（`"bt"` / `"rotation"` / `"nfc"`… 见 `SettingSwitch` 里那些
 *  `id == "..."`），字形又是界面上一张 `Map<String, () -> ImageVector>` —— 加一条开关要动
 *  **两张表**（[SystemSwitches.ITEMS] 与那张 map），漏了字形**不报错**，界面上只是掉成 `Dots`
 *  （看着像「这条本来就没配图标」）。
 *
 *  收成枚举之后，「加一条开关」= 这里加一项 + [SystemSwitches.ITEMS] 加一行；
 *  而界面那张字形表对枚举做 `when`，**不完备当场编译失败** —— 漏配图标这件事没有了。
 *
 * ## id 为什么还是字符串
 *
 *  它是**落盘取值**的一部分（`setting:<id>`），手改配置里可能出现枚举之外的 id。
 *  所以 `id` 是数据，[of] 认不出时给 `null`（调用方决定退到哪），而不是硬塞一个 `UNKNOWN`
 *  —— 那种兜底值会让「手改坏的配置」看起来像一条正常开关。
 */
enum class SwitchKind(
    val id: String,
) {
    /** 蓝牙。 */
    BT("bt"),

    /** WIFI。 */
    WIFI("wifi"),

    /** 移动数据（写 `Settings.Global`，Phone 自己跟）。 */
    CELL("cell"),

    /** 自动旋转（服务自己读状态取反）。 */
    ROTATION("rotation"),

    /** NFC。 */
    NFC("nfc"),

    /** 飞行模式。 */
    AIRPLANE("airplane"),

    /**
     * 定位服务 —— 开关的真身是 `Settings.Secure.location_mode`（0 = 关，1 = 仅设备、2 = 省电、
     *  3 = 高精度）。`LocationManagerService` 盯着这个键：写完那一刻
     *  `cmd location is-location-enabled` 当场就翻，所以宿主直接写它等于按了那颗开关。
     */
    LOCATION("location"),

    /** 免打扰（`notification.setZenMode`）。 */
    DND("dnd"),

    /**
     * 手电筒：宿主里由 `FlashlightController` 直接调 `CameraManager.setTorchMode`，
     *  **不走** `SettingSwitch` 那条路，所以分派要用 id 认一次。
     */
    FLASHLIGHT("flashlight"),

    /** 暗色模式。 */
    DARKMODE("darkmode"),

    /** 省电模式（写 `Settings.Global`）。 */
    POWERSAVE("powersave"),

    /**
     * 极暗模式 —— 真开关在**颜色显示服务**里（AOSP 的 reduce bright colors）。
     *
     *  要 `CONTROL_DISPLAY_COLOR_TRANSFORMS`（signature|privileged），宿主拿不到
     *  （试过一次：SecurityException），所以这一条整条转给 system_server 去调 `color_display`。
     *  **不是** `PowerManager`（这台机器上反射不到），也**不是** `Settings.*.reduce_brightness`
     *  （那两个键没人维护，写了没反应）。
     */
    EXTRADIM("extradim"),

    /** 自动亮度 —— `Settings.System.SCREEN_BRIGHTNESS_MODE`（1 = 自动）。宿主有 `WRITE_SETTINGS`，
     * 写这个键 DisplayPowerController 自己会跟。
     */
    AUTOBRIGHTNESS("autobrightness"),

    /**
     * 个人热点（WiFi 共享）—— 没有可读可写的 `Settings` 键，整条由 system_server 走 `wifi` 服务：
     * `getWifiApEnabledState()` 读、`startTetheredHotspot(SoftApConfiguration, String)` 开
     * （配置取 `getSoftApConfiguration()` 现存那一份，**不改用户的热点名 / 密码**）、`stopSoftAp()`
     * 关。uid 1000 持有 `NETWORK_SETTINGS` 与 `TETHER_PRIVILEGED`（实测 `granted=true`）。
     */
    HOTSPOT("hotspot"),

    /**
     * 护眼模式 —— 真开关是 `Settings.System.oplus_customize_eye_protect_enable`（0 / 1）。
     * 写完颜色显示服务当场跟上（`dumpsys color_display` 的 `eyeProtectEnabled` 立刻翻），
     * 不需要再转 `color_display` 服务（那条要 `CONTROL_DISPLAY_COLOR_TRANSFORMS`，宿主没有）。
     */
    EYEPROTECT("eyeprotect"),

    /**
     * 旁路充电 —— 真开关是 `Settings.System.bypass_charge_state`（0 / 1）。
     * 写完之后 `com.oplus.battery` 的 `BypassChargeController` 自己把目标值下发到充电 IC
     * （日志 `setBypassToAidl:value=…+switch=…`），宿主写一个键就够了。
     */
    BYPASSCHARGE("bypasscharge"),
    ;

    companion object {
        /** id → 种类；**认不出给 `null`**（手改配置 / 将来 ROM 加的新开关，由调用方兜底）。 */
        fun of(id: String): SwitchKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 「系统开关」那一格点开的那一列开关 —— **由项目自己维护**的一份固定清单：有哪些开关、叫什么、
 * 拿什么 token 落盘，全在这一处。
 *
 * ## 为什么不是「控制中心磁贴」
 *
 *  控制中心里点得动的第三方磁贴必须**先在控制中心里加过**，而用户真正常绑的那几个（WIFI / 蓝牙 /
 *  移动数据 / NFC / 飞行模式 / 自动旋转 …）是 SystemUI 内置的简写 token、**没有组件名**，永远进不了
 *  那张列表。所以这一簇不再借控制中心的任何一种写法，自己定义动作类型：`setting:<id>`
 *  （见 [Action.Setting]）。
 *
 * ## 执行不用 root
 *
 *  宿主（`com.oplus.gesture`，签名权限）负责**读**当前状态并算出目标值；写状态分三条路：
 *  · `cell` / `powersave` 直接写 `Settings.Global`（Phone 与 PowerManagerService 各自有
 *    ContentObserver 盯着）；
 *  · `eyeprotect` / `bypasscharge` 写 `Settings.System`（颜色显示服务与电池应用各自盯着，见那两项）；
 *  · 其余走广播交给 system_server（uid 1000）按名字反射调服务（`bridge/SettingCalls`）。
 *  自动旋转与 NFC 由服务自己读状态取反，宿主不参与；热点没有 Settings 键，也归 system_server。
 *
 * ## 加一条要动两处
 *
 *  [SwitchKind] 加一项（种类，也是字形表的键）、[SystemSwitches.ITEMS] 加一行（字典键与顺序）。
 *  两份字典要加键，真要能执行还要在 `SettingCalls` 里加一支。
 *
 *  **字形不用另加**：界面那张表对 [SwitchKind] 做 `when`，枚举加了而 `when` 没补齐会当场编译失败
 *  —— 原先它是一张 `Map<String, …>`，漏了只是掉成 `Dots`（看着像「这条本来就没配图标」）。
 */
object SystemSwitches {
    /**
     * 清单里的一条。
     *
     * @param kind 哪一种开关 —— 决定字形（界面对这个枚举做 `when`，漏配当场编译失败）
     * @param labelKey 字典键
     */
    class Item(
        val kind: SwitchKind,
        val labelKey: String,
    ) {
        /** 落盘取值 —— 由 [kind] 推，不再各写一遍 `setting:xxx` 字面量。 */
        val token: String = settingToken(kind.id)
    }

    /**
     * `setting:<id>` —— 系统开关的落盘写法。
     *
     *  走 [Action.Setting] 而不是自己拼字符串：`Action.parse` 那一侧的还原、图标表、执行侧
     *  认的都是这个类型带来的 token，自己拼一个近似值只会在某处对不上。
     */
    fun settingToken(id: String): String = Action.Setting(id).token

    /**
     * `setting:bt[:on|:off]` / `flashlight` → **开关种类 id**（`bt` / `flashlight`）—— 唯一一处。
     *
     *  三处要用它：清单条目声明字形（`Spec.glyph = BySwitchKind(...)`）、三档菜单
     *  （`switchStates`）、以及界面那张 `SWITCH_KIND_ICONS` 的查表键。
     *  三处各自 `removePrefix("setting:")` 的话，手电筒那条（token 不是 `setting:` 开头）
     *  就会切出整串 `flashlight` —— 恰好也命中，于是错的地方看不出来；换成别的写法才现形。
     */
    fun kindOf(token: String): String = (Action.parse(token) as? Action.Setting)?.id ?: token.removePrefix(Action.SETTING_PREFIX)

    /** 页面上的开关清单，顺序即显示顺序。 */
    val ITEMS: List<Item> =
        listOf(
            Item(SwitchKind.BT, Strings.SYS_SETTING_BT),
            Item(SwitchKind.WIFI, Strings.SYS_SETTING_WIFI),
            //  热点紧挨着 WIFI：两者都是「这台机器怎么对外联网」，一个收一个放。
            //  它没有 Settings 键，整条走 system_server 的 `wifi` 服务（见 [SwitchKind.HOTSPOT]）。
            Item(SwitchKind.HOTSPOT, Strings.SYS_SETTING_HOTSPOT),
            Item(SwitchKind.CELL, Strings.SYS_SETTING_CELL),
            Item(SwitchKind.ROTATION, Strings.SYS_SETTING_ROTATION),
            Item(SwitchKind.NFC, Strings.SYS_SETTING_NFC),
            Item(SwitchKind.AIRPLANE, Strings.SYS_SETTING_AIRPLANE),
            //  定位服务：紧挨着飞行模式（两者都是「无线电开不开」那一类）。宿主能直接写
            //  `Settings.Secure.location_mode`，见 [SwitchKind.LOCATION]。
            Item(SwitchKind.LOCATION, Strings.SYS_SETTING_LOCATION),
            //  免打扰与手电筒**也收进 `setting:` 这一族** —— 三态（切换 / 打开 / 关闭）要求
            //  「目标状态」能写在 token 里，而 `oplus:no_disturb` / `flashlight` 这两种老写法
            //  只能表达「切一下」。执行侧各自分派（见 `ActionRunner` 与 `SettingCalls`），
            //  老配置里那两种写法仍然按原路跑，不迁移、不丢。
            Item(SwitchKind.DND, Strings.SYS_SETTING_DND),
            Item(SwitchKind.FLASHLIGHT, Strings.SYS_SETTING_FLASHLIGHT),
            Item(SwitchKind.DARKMODE, Strings.SYS_SETTING_DARKMODE),
            //  护眼模式：挂在显示那一簇里（暗色模式之后、亮度那两条之前）。
            Item(SwitchKind.EYEPROTECT, Strings.SYS_SETTING_EYEPROTECT),
            Item(SwitchKind.POWERSAVE, Strings.SYS_SETTING_POWERSAVE),
            //  旁路充电：与省电模式同一簇（都是「电怎么用」）。
            Item(SwitchKind.BYPASSCHARGE, Strings.SYS_SETTING_BYPASSCHARGE),
            //  极暗：真开关在**颜色显示服务**里（AOSP 的 reduce bright colors）——
            //  `dumpsys color_display` 有一节 `Reduce bright colors: Activated: ...`。
            //  它要 `CONTROL_DISPLAY_COLOR_TRANSFORMS`（signature|privileged），宿主拿不到这个权限
            //  （宿主侧调过一次：SecurityException），所以这一条整条转给 system_server
            //  （`SettingCalls.extraDim` 调 `color_display` 服务）。
            //  注意 **不是** `PowerManager`（这台机器上反射不到），也**不是**
            //  `Settings.*.reduce_brightness`（那两个键没人维护，写了没反应）。
            Item(SwitchKind.EXTRADIM, Strings.SYS_SETTING_EXTRADIM),
            //  自动亮度：`Settings.System.SCREEN_BRIGHTNESS_MODE`（1 = 自动）。宿主有
            //  `WRITE_SETTINGS`，写这个键 DisplayPowerController 自己会跟。
            Item(SwitchKind.AUTOBRIGHTNESS, Strings.SYS_SETTING_AUTOBRIGHTNESS),
        )

    /**
     * **老写法**对应清单里的哪个 id（认不出返回 `null`）。
     *
     *  免打扰与手电筒早先是 `oplus:no_disturb` / `flashlight` 两种独立写法（执行只有「切换」
     *  一种语义）；三态改版后落盘统一成 `setting:dnd` / `setting:flashlight`。
     *  老配置**不迁移** —— 执行侧两种写法都还认（见 `ActionRunner` / `SettingSwitch`），
     *  但界面判定得靠这一条把它们归到同一个开关上，否则升级之后那一格不亮、菜单里也不打勾。
     */
    fun idOfLegacy(token: String): String? =
        when (token) {
            Action.FLASHLIGHT -> SwitchKind.FLASHLIGHT.id
            Action.OPLUS_PREFIX + RomProfile.NAME_NO_DISTURB -> SwitchKind.DND.id
            else -> null
        }
}
