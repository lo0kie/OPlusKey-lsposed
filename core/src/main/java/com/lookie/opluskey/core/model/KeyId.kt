package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.rom.RomKeyFamilies
import java.util.Locale

/**
 * 一个可重映射的按键 —— **身份由 [id] 决定**（配置的键名、广播的 extra 前缀、日志、去重都用它）。
 *
 * ## 为什么不是枚举了
 *
 *  侧键是编译期就知道的那一颗；**自定义按键是用户运行时收集来的**，数量与键码都不在编译期。
 *  枚举装不下这种东西，而把「运行时的清单」塞进枚举的伴生对象（一个可写注册表）会让
 *  `core` 带上跨调用可变状态 —— 那个状态在三个进程里各有一份，谁先写谁说了算，
 *  正是本项目一直在躲的那类 bug。
 *
 *  所以：**清单只属于 `Config`**（`Config.customKeys`），本类只负责「一颗键长什么样」。
 *  要按 id / 键码找键，一律从你手上那份 `Config` 找：`keyForId` / `keyIdOf` / `keyForCode` /
 *  `keyForRawCode`。本类**不提供**「按 id / 键码反查」的接口 —— 那只认得到内置键，
 *  自定义键会静默落空（原先那三个 `from*` 就是这么被误用的）。
 *
 * ## 判等按 [id]
 *
 *  `Map<KeyId, KeyConfig>`、去重、广播解包都靠它。同样的 `id` 就是同一颗键，
 *  名字（[label]）改了不影响身份 —— 改名是改显示，不是换一颗键。
 */
class KeyId private constructor(
    /** 稳定身份：内置键是 `plus`，自定义键是 `ck1` `ck2`（见 [customId]）。 */
    val id: String,
    /** 显示名。自定义键是用户收集那一刻定下的字面量，不随界面语言变。 */
    val label: String,
    /** 注入「放行」时用的原始 keyCode（真机按键上报的那个）。 */
    val keyCode: Int,
    /**
     * 认领按键时接受的 keyCode 白名单。
     * ColorOS 策略类（`StrategyActionButtonKeyLaunchApp`）派发的是它自己注入的内部码
     * 780 / 781 / 782，只认原始码 735 会把侧键放行，表现为模块装了却完全不被接管。
     *
     *  自定义键认**整族**：压感拍照键这类多码键（[RomKeyFamilies]），收集到族里任何一个码，
     *  整族都要认 —— 只认一个码的话其余手势的码会漏给系统。内部码 780 / 781 / 782 是策略类
     *  给**侧键**专用的，别的键不走那条路（见 `docs/device/`）。
     */
    val acceptedKeyCodes: Set<Int>,
    /** 是不是用户收集来的。收集来的可改名 / 可删，内置的不可 —— 判定只在这一处。 */
    val isCustom: Boolean,
) {
    // 手写而不是 data class：data class 会把 label / keyCode 也算进判等，
    // 于是「改个名」在 `Map<KeyId, KeyConfig>` 里就成了另一颗键（配置查不到、绑定全丢）。

    override fun equals(other: Any?): Boolean = this === other || (other is KeyId && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "KeyId($id)"

    /**
     * 换一个显示名 —— **身份（[id]）不变**，所以改完仍是同一颗键、绑定不丢。
     * 内置键不该走这条路（界面上它不可改名），但这里不另设闸：闸在 `Config.withKeyName` 那层。
     */
    fun copyName(name: String): KeyId = KeyId(id, name, keyCode, acceptedKeyCodes, isCustom)

    // 配置里的键名由 ConfigCodec 独占，本类只负责「一颗键有哪些属性」。

    companion object {
        /** 侧键 —— **唯一**的内置键；界面上它叫「快捷键」（与页面标题同一个词，见 `key.side`）。 */
        val PLUS = KeyId("plus", "快捷键", 735, setOf(735, 780, 781, 782), isCustom = false)

        /** 编译期就存在的那些键。界面要「内置 + 自定义」全列时用 `Config.allKeys`。 */
        val builtins: List<KeyId> = listOf(PLUS)

        /**
         * 这个键码是**内置键**认领的吗（含侧键那一族的 780 / 781 / 782）。
         *
         *  ## 为什么要有一个函数，而不是让调用点自己摊开写
         *
         *  这句话有两处要问，而且**问错的后果都是"又一个自持循环"或"用户自己的键失灵"**：
         *
         *   · 动作解析（[Action.parseKeyEvent]）：`keyevent:<码>` 若指向模块自己认领的码，
         *     注入出去会被拦截路径再认领一次 —— 在 `system_server` 里每 ~100ms 一轮；
         *   · 注入前复核（`ActionRunner.claimedByModule`）：同一条自持循环，只是漏在另一侧。
         *
         *  原先两处各把 `builtins.flatMap { acceptedKeyCodes }` 摊开写了一遍（其中一处还
         *  摊成了个 `CLAIMED_CODES` 常量）。`builtins` 加一颗键、或者某颗键的认领集合改口径时，
         *  漏改一处的表现是上面那两种之一，而且**都不会编译报错**。
         *
         *  ## 不含自定义键
         *
         *  那些码只存在于配置里（用户运行时收集），`core` 这一层拿不到 —— 所以「现在归不归
         *  模块管」的完整判断还得由调用点再叠一层 `Config.keyForCode`。本函数只回答它答得了
         *  的那一半，而且**只有这一份答案**。
         */
        fun claimsBuiltin(code: Int): Boolean = builtins.any { code in it.acceptedKeyCodes }

        /** 自定义键 id 的前缀：`ck1` / `ck2` ……（序号只增不减，与 `Config.customKeys` 的顺序无关）。 */
        const val CUSTOM_ID_PREFIX = "ck"

        /**
         * 自定义键的**数量上限** —— 五颗（用户口径）。
         *
         *  收集入口按它拦（`ui/screen/binding/CollectKeyDialog.collectKey`）；清单行首的数字图标
         *  （`PageIcons.menuKeyNumber`）也正好够画到这一档，不至于一堆键去抢同一颗物理键。
         */
        const val MAX_CUSTOM_KEYS = 5

        /** 第 [seq] 颗自定义键的 id —— 序号由调用方维护（它要保证不被复用）。 */
        fun customId(seq: Int): String = CUSTOM_ID_PREFIX + seq

        /**
         * 一颗用户收集来的键。
         *
         * @param id 必须是 [customId] 生成的那个稳定 id，**不能用键码**：同一颗物理键上报的码
         *   可能随驱动 / 系统版本变化，而绑定不该跟着丢。
         */
        fun custom(
            id: String,
            name: String,
            keyCode: Int,
        ): KeyId =
            // 族内键（如压感拍照键）：收集到族里任何一个码，认领集合就扩成**整族** ——
            //  只认一个码的话，其余手势的码全部漏给系统（真机表现：双击拍照键跳到原生相机）。
            //  哪些码同族、哪个码是什么手势，见 [RomKeyFamilies]。
            KeyId(id, name, keyCode, RomKeyFamilies.familyOf(keyCode) ?: setOf(keyCode), isCustom = true)

        // 「一个物理键、好几个码」的族表**不在这里** —— 那是 ROM 适配知识，跟着 ROM 版本走、
        //  与「一颗键的身份」无关，住在 [RomKeyFamilies]（加一族只改那个文件）。
        //  本类只在 [custom] 里按主码推整族。

        /**
         * 截图按键码（`KEYCODE_SYSRQ`）—— 「截图」动作**请注入侧代注入**它（宿主自己注入的那一下
         *  ColorOS 不处理）。留在 [INJECTABLE_CODES] 里是为「注入之后它会再走一遍按键路径，
         *  这一下不能当侧键认领」。
         *
         * 放在这里只因为一、5 要求「键码只定义在 [KeyId] 一处」：执行侧不许在
         * `bridge/host/KeyInjector` 的伴生对象里另留一份字面量 —— 那正是这条规矩要禁止的写法。
         */
        const val KEYCODE_SYSRQ = 120

        /**
         * 通知中心（`KEYCODE_NOTIFICATION`）—— 内置「通知中心」动作注入的就是它。
         *
         *  一、5 要求键码只定义在一处，执行侧不许另留字面量。
         */
        const val KEYCODE_NOTIFICATION = 83

        /** 最近任务（`KEYCODE_APP_SWITCH`）。 */
        const val KEYCODE_APP_SWITCH = 187

        /** 静音键（`KEYCODE_VOLUME_MUTE`）—— 「响铃循环」那条动作注入的就是它。 */
        const val KEYCODE_VOLUME_MUTE = 164

        // 下面这一批是「系统导航」/「系统按键」/「媒体控制」三张菜单里的档位取值。
        //  它们是**动作**（绑给侧键之后注入一次），与「不接管物理音量键 / 电源键」那条纪律
        //  不冲突 —— 后者说的是不去拦截那几个键本身（见本类顶部的说明）。
        //  与上面几条同一口径：一、5 要求键码只定义在一处，取值由 `Action.keyToken` 拼。

        /** 主屏幕（`KEYCODE_HOME`）—— 「系统导航」菜单的一档。 */
        const val KEYCODE_HOME = 3

        /** 返回（`KEYCODE_BACK`）。 */
        const val KEYCODE_BACK = 4

        /** 音量 +（`KEYCODE_VOLUME_UP`）。 */
        const val KEYCODE_VOLUME_UP = 24

        /** 音量 −（`KEYCODE_VOLUME_DOWN`）。 */
        const val KEYCODE_VOLUME_DOWN = 25

        /** 电源键（`KEYCODE_POWER`）。 */
        const val KEYCODE_POWER = 26

        /** 播放 / 暂停（`KEYCODE_MEDIA_PLAY_PAUSE`）—— 「媒体控制」那一格的取值。 */
        const val KEYCODE_MEDIA_PLAY_PAUSE = 85

        /** 下一首（`KEYCODE_MEDIA_NEXT`）。 */
        const val KEYCODE_MEDIA_NEXT = 87

        /** 上一首（`KEYCODE_MEDIA_PREVIOUS`）。 */
        const val KEYCODE_MEDIA_PREVIOUS = 88

        /** 播放（`KEYCODE_MEDIA_PLAY`）—— 「无论如何都要播」。 */
        const val KEYCODE_MEDIA_PLAY = 126

        /** 暂停（`KEYCODE_MEDIA_PAUSE`）—— 「无论如何都要停」。 */
        const val KEYCODE_MEDIA_PAUSE = 127

        /**
         * **动作会注入的那一批码** —— 「这一下是不是我们自己注入的合成事件」就按这张表判
         * （见 `inject/SideKeyInterceptor.isSynthetic`）。注入的键会再走一遍按键路径，
         * 认领了就是自持循环，所以这批码一律放行、不认领。
         */
        val INJECTABLE_CODES: Set<Int> =
            setOf(
                KEYCODE_SYSRQ,
                KEYCODE_NOTIFICATION,
                KEYCODE_APP_SWITCH,
                KEYCODE_VOLUME_MUTE,
                KEYCODE_HOME,
                KEYCODE_BACK,
                KEYCODE_VOLUME_UP,
                KEYCODE_VOLUME_DOWN,
                KEYCODE_POWER,
                KEYCODE_MEDIA_PLAY_PAUSE,
                KEYCODE_MEDIA_NEXT,
                KEYCODE_MEDIA_PREVIOUS,
                KEYCODE_MEDIA_PLAY,
                KEYCODE_MEDIA_PAUSE,
            )

        /**
         * **不许被收集成自定义键**的那一批 —— 就是 [INJECTABLE_CODES] 去掉音量±与电源。
         *
         *  留下的这些是「收下就会自持循环」的码（主屏幕 / 返回 / 媒体 / 截图 / 通知中心 /
         *  最近任务 / 静音）：动作会注入它们，收集成键之后按一下等于自己触发自己。
         *
         *  **音量±与电源允许自定义**（用户口径）：它们同样在可注入表里，但注入的那一下仍由
         *  [INJECTABLE_CODES] 认出来、放行给系统，所以收下它们不会形成循环。代价是收下并启用
         *  之后这两颗键归模块管（没收集时照常放行给系统）。
         */
        val RESERVED_CODES: Set<Int> =
            INJECTABLE_CODES -
                setOf(
                    KEYCODE_VOLUME_UP,
                    KEYCODE_VOLUME_DOWN,
                    KEYCODE_POWER,
                )
    }
}

/** 照搬 KSU 模块（同目录 `OPlusKey`）的那些取值 —— 配置兼容用的别名表。 */
object KsuCompat {
    /**
     * KSU 预设里「截图」那条命令的**前缀**（预设原值是 `service call color_screenshot 1`）——
     * 模块侧**不走 shell**：换成请注入侧注入 `KEYCODE_SYSRQ`（见 `ActionRunner.requestSystemInject`），
     * 这里的串只用来认「这一行是不是截图」、给界面挑字形。认领时用 `startsWith`，**不要用 `contains`**。
     *
     *  `contains` 会把「用户自己的命令正文里正好出现这个串」也当成 KSU 那条劫持掉
     *（自定义命令写 `echo color_screenshot` 就会被改成注入 SYSRQ，
     *  原命令不执行、也没有任何提示）。前缀匹配照样容得下参数差异
     *（`service call color_screenshot 2` 也认）。
     */
    const val SCREENSHOT_CMD_PREFIX = "service call color_screenshot"

    /**
     * 「截图」那条命令的**完整写法**（KSU 预设原文）—— 清单里的取值就是它。
     *
     *  执行时**不走 shell、也不要 root**：请注入侧（`system_server`）注入 `KEYCODE_SYSRQ` ——
     *  宿主自己注入的那一下 ColorOS 不处理（返回成功、屏幕没反应），
     *  而注入侧的身份就是系统本身（见 `host/action/ActionRunner.requestSystemInject`）。
     */
    const val SCREENSHOT_CMD = "$SCREENSHOT_CMD_PREFIX 1"

    /** KSU 预设里「小布识屏」那条命令里的 service action。 */
    const val SIDEBAR_SERVICE = "oplus.intent.action.DIRECT_SIDEBAR_SERVICE"

    /** 「小布识屏」那条命令的**前缀**（理由同 [SCREENSHOT_CMD_PREFIX]）。 */
    const val SIDEBAR_CMD_PREFIX = "am start-foreground-service -a $SIDEBAR_SERVICE"

    // 小布识屏那两个 extra —— 清单里那条 shell 写法与 `core/model/RomProfile` 的
    //  `startForegroundService` 调用目标共用这一份取值（两处各写一遍时改一处就漏一处）。

    /** 「小布识屏」的入口功能名（`full_screen_ocr` = 整屏识屏）。 */
    const val SIDEBAR_EXTRA_ENTRANCE = "extra_entrance_function"
    const val SIDEBAR_EXTRA_ENTRANCE_VALUE = "full_screen_ocr"

    /** 「小布识屏」对外报的调用方 —— 报侧边栏而不是真实调用方，理由见 [SIDEBAR_SERVICE] 的调用点。 */
    const val SIDEBAR_EXTRA_APP = "triggered_app"
    const val SIDEBAR_EXTRA_APP_VALUE = "com.coloros.smartsidebar"

    /**
     * 「小布识屏」那条命令的**完整写法**（KSU 预设原文）—— 清单里的取值就是它。
     *  模块侧执行时不走 shell：按 [SIDEBAR_SERVICE] 直接 `startForegroundService`
     *（见 `host/action/AppLauncher.startSidebar`）。
     */
    const val SIDEBAR_CMD =
        "$SIDEBAR_CMD_PREFIX" +
            " -e $SIDEBAR_EXTRA_ENTRANCE $SIDEBAR_EXTRA_ENTRANCE_VALUE" +
            " -e $SIDEBAR_EXTRA_APP $SIDEBAR_EXTRA_APP_VALUE"

    /** 这条 shell 文本是不是「截图」那条 KSU 别名。 */
    fun isScreenshotAlias(command: String): Boolean = matches(SCREENSHOT_CMD_PREFIX, command)

    /** 这条 shell 文本是不是「小布识屏」那条 KSU 别名。 */
    fun isSidebarAlias(command: String): Boolean = matches(SIDEBAR_CMD_PREFIX, command)

    /**
     * 别名认领的**唯一口径**：前缀匹配 + **这一条里没串别的命令**。
     *
     *  前缀匹配（不是 `contains`）：容得下参数差异（`service call color_screenshot 2` 也认），
     *  又不会把「用户自己的命令正文里正好出现这个串」当成 KSU 那条劫持掉。
     *
     *  注意：**串了别的命令就不认**：别名会被翻译成一次原生调用
     *  （注入 SYSRQ / 起小布识屏），`service call color_screenshot 1; echo hi` 这种一条里串两条的写法
     *  若整条认成别名，`echo hi` 就被**悄悄丢掉**了 —— 原命令不执行、也没有任何提示。
     *  判据是裸字符（`;` / `&` / `|` / 换行）：引号里的这些字符同样会命中，但两条别名的写法里
     *  不可能出现引号，宁可靠保守的一边（多认一次 shell、少丢一条命令）。
     */
    private fun matches(
        prefix: String,
        command: String,
    ): Boolean {
        if (hasCommandChain(command)) return false
        val text = command.trim()
        if (!text.startsWith(prefix)) return false
        //  **前缀之后必须是空格或结尾**：`service call color_screenshot_x 1` 只是长得像，
        //  认下来会把原命令整条换成一次原生截图 —— 原命令被静默丢弃、也没有提示。
        return text.length == prefix.length || text[prefix.length].isWhitespace()
    }

    /**
     * 这条 shell 文本里是不是串了别的命令。
     *
     *  除了 `;` / `&` / `|` / 换行，**重定向与命令替换也算**（`>` / `<` / `` ` `` / `$(` / `${`）：
     *  那些写法会把「长得像别名」的那条命令拆成两段执行，而别名匹配只认整串 ——
     *  认下来就等于把原命令整条换成一次原生调用，后半截被静默丢掉。
     */
    private fun hasCommandChain(command: String): Boolean =
        command.any { it == ';' || it == '&' || it == '|' || it == '\n' || it == '\r' || it == '>' || it == '<' || it == '`' } ||
            command.contains("\$(") ||
            command.contains("\${")
}

/**
 * 条件里的**时间区间** —— `22:00-07:00` 这一段。
 *
 *  存进配置的是 `time:22:00-07:00`（[ConditionKind.TIME] 的取值），这里只管「这一段算不算
 *  『现在』」与两种格式之间的换算。判定在宿主（`ConditionChecks.timeMatches`）、显示在界面
 *  （`CommandConditionText`），两边都要解析同一个字符串，所以解析这一份实现放在 core。
 *
 * ## 一律按分钟数比，不碰 `LocalTime`
 *
 *  区间比较说到底就是两个整数比大小，用 `LocalTime` 反而要处理日期、时区、夏令时那一堆
 *  与「一天里的第几分钟」无关的东西。取「当天的第几分钟」也简单：`hour * 60 + minute`。
 *
 * ## 跨午夜
 *
 *  `22:00-07:00` 的开始大于结束 ⇒ 这一段跨过了午夜，判定是**或**（`>= 22:00` 或 `< 07:00`）；
 *  其余情况是**与**。这是这类条件最容易写错的一处。
 *
 * ## 端点
 *
 *  区间是**左闭右开** `[start, end)`：`22:00-07:00` 含 22:00、不含 07:00 —— 相邻两段
 *  （`18:00-22:00` 与 `22:00-07:00`）因此不会在 22:00 这一分钟同时成立，也不会同时不成立。
 *
 *  `start == end`（`07:00-07:00`）按**全天**算：写成零长度区间的话这条条件永不成立，
 *  而用户多半是想表达「一直」，那是更接近本意的那一档。
 */
data class TimeRange(
    /** 起点的「当天第几分钟」（0..1439）。 */
    val startMinute: Int,
    /** 终点的「当天第几分钟」（0..1439）。 */
    val endMinute: Int,
) {
    /** 这一分钟落在区间里吗（[minute] 取 0..1439）。 */
    fun contains(minute: Int): Boolean =
        when {
            startMinute == endMinute -> true

            startMinute < endMinute -> minute >= startMinute && minute < endMinute

            // 跨午夜：或
            else -> minute >= startMinute || minute < endMinute
        }

    /**
     * 与 [other] 有没有重叠 —— 端点按 [contains] 那套左闭右开算，相邻两段（`18:00-22:00` 与
     * `22:00-07:00`）**不算重叠**，22:00 那一分钟只归后一段。
     *
     *  为什么要这个判据：同一档手势下两条区间一重叠，先命中的那条永远赢、后一条永远轮不到，
     *  而界面上两行都好好列着 —— 用户配了个不会生效的规则却看不出来。
     *  `start == end`（全天）与任何区间都重叠。
     */
    fun overlaps(other: TimeRange): Boolean = segments().any { a -> other.segments().any { b -> a.first < b.second && b.first < a.second } }

    /** 把区间拆成当天内的若干 `[起, 止)` 段 —— 跨午夜的那一档拆成两段（`22:00-24:00` 与 `00:00-07:00`）。 */
    private fun segments(): List<Pair<Int, Int>> =
        when {
            // 全天：写成一个覆盖整天的段
            startMinute == endMinute -> listOf(0 to MINUTES_PER_DAY)

            startMinute < endMinute -> listOf(startMinute to endMinute)

            else -> listOf(startMinute to MINUTES_PER_DAY, 0 to endMinute)
        }

    /** 线格式 / 界面文案（`22:00-07:00`）—— 两端都补零，长度恒定，格子那一行不会因时间不同而抖。 */
    override fun toString(): String = "${clock(startMinute)}-${clock(endMinute)}"

    companion object {
        /** 一天有多少分钟 —— 跨午夜那一段要用它当右端点。 */
        private const val MINUTES_PER_DAY = 24 * 60

        /**
         * 解析线格式。
         *
         *  宽松两处，其余不认：
         *   · 允许 `7:00` 这种不补零的写法（手改配置 / 导入别人的文件）；
         *   · 分隔符认 `-`（线格式）与 `~`（有人习惯这么写）。
         *
         *  认不出 / 越界一律返回 `null` —— 调用方把这个条件当「判不出」处理（不成立），
         *  绝不拿一个猜出来的区间去接管按键。
         */
        fun parse(raw: String): TimeRange? {
            val text = raw.trim()
            val cut = text.indexOfFirst { it == '-' || it == '~' }
            if (cut <= 0 || cut == text.lastIndex) return null
            val start = minuteOf(text.substring(0, cut)) ?: return null
            val end = minuteOf(text.substring(cut + 1)) ?: return null
            return TimeRange(start, end)
        }

        /** 把两个 `hour:minute` 拼成线格式 —— 界面那边选完时间落盘用。 */
        fun format(
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int,
        ): String = "${clock(startHour * 60 + startMinute)}-${clock(endHour * 60 + endMinute)}"

        /** `H:mm` / `HH:mm` → 当天第几分钟；格式不对或越界返回 `null`。 */
        private fun minuteOf(text: String): Int? {
            val parts = text.trim().split(':')
            if (parts.size != 2) return null
            //  两段各自「解析不出来」与「越界」都收进 `takeIf`，最后只剩一个结论 ——
            //  逐条 `?: return null` 会顶到 detekt 的 ReturnCount（4 档）
            val hour = parts[0].trim().toIntOrNull()?.takeIf { it in 0..24 }
            val minute = parts[1].trim().toIntOrNull()?.takeIf { it in 0..59 }
            //  **`24:00` 是合法终点**（[clock] 写的也是它）；`24:01` 之类仍然认不出
            if (hour == 24 && minute != 0) return null
            return if (hour != null && minute != null) hour * 60 + minute else null
        }

        /**
         * 当天第几分钟 → `HH:mm`。线格式、界面上的区间文案，以及 `${sys.time}` 的值都走这一处。
         *
         *  一律两位补零、`Locale.ROOT`：这个串要写进配置文件 / 命令，不能被设备语言影响
         *  （与 `CommandKind.fromId` 用 `Locale.ROOT` 同一条纪律）。越界值夹到两端。
         */
        fun clock(minuteOfDay: Int): String {
            //  **一天的结束写成 `24:00`**（= [MINUTES_PER_DAY]）：区间末端用它表示「到当天结束」。
            //  一律夹到 23 的话它会变成 `23:00` —— 少一小时，而且 [minuteOf] 也认不回原值。
            if (minuteOfDay >= MINUTES_PER_DAY) return "24:00"
            val m0 = minuteOfDay.coerceAtLeast(0)
            return String.format(Locale.ROOT, "%02d:%02d", m0 / 60, m0 % 60)
        }
    }
}

/**
 * 一个**场景块** —— 一组条件 + **这颗键认得的每一档手势**（`GestureTrigger` → 动作）。
 *
 * ## 为什么是「块里装全部档位」，而不是「档里列场景」
 *
 *  一条规则只认一个手势时，用户要写好几遍「在微信里」才能把各档都配出来；而这些档位本来是
 *  同一个场景的几件事 —— 切到微信之后单击做什么、双击做什么、按住做什么。
 *
 *  现在一块就是那个场景：`conditions` 成立 ⇒ 这一块在**它配过的每一档**上都接管
 *  （单击 / 双击 / 长按 / 三击 / 四击…）。于是「加一个场景」是一次动作，不是三次。
 *
 * ## 档位是数据，不是那个三值枚举
 *
 *  [actions] 的键是 [GestureTrigger] —— 与「按键绑定」页排页、判定器数击数、执行器取动作
 *  用的是同一套触发条件。所以「多加一档」（比如第 5 击）在这里**不用再改一个格子**：
 *  它本来就是「连击次数 = 5」这一个取值。
 *
 *  **键缺项与 `Action.Follow` 同义**：都表示「这一档不由这一块接管」。
 *  写入侧（[withAction]）看到 `Follow` 就把那一项**删掉**，于是配置里只留真的配过的档位。
 *
 * ## `Follow` 与 `None` 是两件事
 *
 *  · `Follow`（界面上「跟随默认」，也是**没配过**那一档的答案）⇒ 这一档**不被这一块接管**，
 *    继续往下找条件成立的块，最后回落到按键绑定里这一档绑的动作 —— 老配置迁移过来就是这个状态
 *    （见 `ConfigDto`），所以升级前后行为逐字一致；
 *  · `None`（界面上选的「无操作」）⇒ 这一块命中时**这一档什么都不做**（吞掉）。
 *
 *  合并成一个的话，「只给长按配了动作的场景」会把其它档一起变成无操作 —— 那不是用户写的。
 *
 * ## 同种类之间「或」，不同种类之间「且」
 *
 *  [conditions] 里**同一个种类**可以有好几条（上限是 `choices.size - 1`，见 `blockKindsOf`）：
 *  它们说的是「这几种状态里任一种」—— `[亮屏, 熄屏]` 就是「没停在锁屏界面」，三条全加等于没有
 *  条件，所以上限卡在少一档。不同种类之间仍是**且**：`[亮屏, 熄屏]` 与 `[微信]` 要同时成立。
 *
 *  一张表从上往下找，**第一条「条件成立且这一档有值」的块赢**（见 `pickAction`）。
 *  一块条件都没配 = 还没配完：它在判定侧**永不成立**（[isReady] 为假，界面上拦着不许保存）。
 *
 * ## 判据不在这里
 *
 *  条件成不成立要读系统开关（前台应用 / 亮屏 / 响铃状态…），那是宿主侧的事
 *  （`ConditionProbe`）。所以匹配由调用方传进来，这一层只管顺序、「或 / 且」与「第一条赢」。
 *
 * ## 组合键只有两档
 *
 *  组合键没有双击、也没有多击（`ComboKey` 的类注释），所以**组合的场景块里只会出现**
 *  单击 / 长按两个键 —— 数据层不拦（同一个类型两处共用），是界面与判定只给这两档。
 */
data class SceneRule(
    /** 这一块的**全部**条件（顺序即书写顺序，同种类之间「或」、不同种类之间「且」）。空 = 还没选。 */
    val conditions: List<BranchCondition> = emptyList(),
    /** 档位 → 动作；**缺项 = 这一档不参与**（与 [Action.Follow] 同义，见类注释）。 */
    val actions: Map<GestureTrigger, Action> = emptyMap(),
    /**
     * 「松手执行额外动作」的开关 —— 这一块命中长按时，抬手那一刻再执行 [release] 那一格。
     *
     *  与 `KeyConfig.longReleaseOn` 同义、同一个理由分开存：开着但还没挑动作是合法状态（松手什么都不做）。
     */
    val releaseOn: Boolean = false,
    /**
     * **松手那一档**（`GestureTrigger.Release`）的动作；默认 [Action.None]。
     *
     *  为什么它不像别的档那样有「跟随默认」：长按一旦被这一块接管，**按键自己那个松手动作就不会
     *  再执行**（`GestureExec.resolveAction` 的「一次按压一个结果」）。所以这一格
     *  [Action.None] 就是「这一块命中时松手什么都不做」—— 也正是这一格出现之前的行为，
     *  老配置读进来逐字不变。
     */
    val release: Action = Action.None,
    /**
     * **「单击后长按」的松手**（`GestureTrigger.ClickThenLongRelease`）—— 与 [releaseOn] / [release]
     * 同一对，只是挂在另一档上。
     *
     *  为什么这一档也要进场景块：两块式手势的收尾同样是「这一块做什么」的一半 ——
     *  同一按在不同条件下（前台应用不同）想要的收尾动作本来就可能不一样。
     *
     *  [chainReleaseOn] 关着 = 这一块**不接管**这一档的松手（`actionOf` 回 [Action.Follow]），
     *  与其它档的「缺项」同义：于是 `pickAction` 会跳过这一块，看下一块或回落按键自己那一格。
     */
    val chainReleaseOn: Boolean = false,
    val chainRelease: Action = Action.None,
    /**
     * 这一块**参不参与判定**（界面上的「停用场景」）—— 停用的块整块跳过，条件与各档动作都还留着。
     *
     *  与「删掉」是两件事：删了就没了，停用只是让它这一趟不生效。
     *  判定侧只在 [matches] 一处读它，所以「停用」对按键与组合键两条路同时生效。
     */
    val enabled: Boolean = true,
) {
    /** [trigger] 这一档在这一块里写的动作（[Action.Follow] = 这一块没接管这一档）。 */
    fun actionOf(trigger: GestureTrigger): Action =
        when (trigger) {
            //  「单击后长按」的松手**不在 `actions` 表里**（它是这一块的收尾，与长按的松手同一类）：
            //  开关关着回 `Follow`，于是 `matches` 为假、`pickAction` 会看下一块 —— 与「缺项」同义。
            is GestureTrigger.ClickThenLongRelease -> if (chainReleaseOn) chainRelease else Action.Follow

            else -> actions[trigger] ?: Action.Follow
        }

    /**
     * 把 [trigger] 这一档换成 [action]（[Action.Follow] = 退回「跟随默认」，那一项**从表里删掉**）。
     *
     *  删而不是存一个 `follow`：读取侧 `actions[trigger] ?: Follow` 给的是同一个答案，
     *  而表里少一项就意味着配置里少一行 —— 「这一块到底配了哪几档」看一眼就知道。
     */
    fun withAction(
        trigger: GestureTrigger,
        action: Action,
    ): SceneRule = copy(actions = if (action is Action.Follow) actions - trigger else actions + (trigger to action))

    /**
     * 这一块**有没有自己的松手动作** —— 开关开着且挑过动作。
     *
     *  两个读者：判定侧（`releaseBound` 要不要为真）与广播（宿主算给注入侧那一位）。
     *  停用的块不算：它命中不了，抬手那一刻也不该为它发 `Release`。
     */
    val hasRelease: Boolean get() = enabled && isReady && releaseOn && release.isBound

    /**
     * 这一块**有没有自己的「单击后长按」松手动作** —— 与 [hasRelease] 同一条判据。
     *
     *  读者也是两个：判定侧（`finishLong` 要不要发 `ClickThenLongRelease`）与广播
     *  （宿主算给注入侧那一位 —— 注入侧看不到场景表）。
     */
    val hasChainRelease: Boolean get() = enabled && isReady && chainReleaseOn && chainRelease.isBound

    /**
     * 这一块**开着、且长按这一档配了动作** —— 「这一档到底有没有动作」只认这种块。
     *
     *  与 [hasRelease] 同一条纪律：停用的块命中不了（`matches` 里第一句就是它），它配的长按动作
     *  也就不该让「松手额外动作」成立。算上它的话，判定侧会白等一个窗口并发一条 `Release`，
     *  界面上「松手时触发」那一格还压不灰 —— 两处都指向一个不会发生的动作。
     */
    val longBound: Boolean get() = enabled && isReady && (actions[GestureTrigger.LongPress] ?: Action.None).isBound

    /** 同上，这一块给「单击后长按」配了动作。 */
    val chainBound: Boolean
        get() = enabled && isReady && (actions[GestureTrigger.ClickThenLong(1)] ?: Action.None).isBound

    /**
     * 配完了没有 —— 至少一条条件、且每一条都**认得出并且取值填了**（`isUsable`）。
     *
     *  「配完」只说明每一条都填了取值，不代表这块会命中：成不成立要看当时的系统状态
     *  （[appliesTo]），这一层不认识系统接口。
     */
    val isReady: Boolean get() = conditions.isNotEmpty() && conditions.all { it.isUsable }

    /**
     * 这块里**应用那一族某一档**（[ConditionKind.APP] / [ConditionKind.APP_NOT]）的下标（`-1` = 没有）。
     *
     *  按种类问而不是「所有应用条件」：两档在同一块里可以同时存在（`前台是 A` 且 `前台不是 B`），
     *  挑应用页写回时必须知道这一趟换的是哪一档，拿「第一条应用条件」去换会把另一档覆盖掉。
     */
    fun appConditionIndex(kind: ConditionKind): Int = conditions.indexOfFirst { it.kind == kind }

    /**
     * 这块里应用那一族**某一档**的那一条（`null` = 没有）。
     *
     *  同一档在一块里**只该有一条**：那一条自己就能多选（`包1||包2`），再开一行只是把同一件事
     *  写成两处（判定侧按「或」算，两行同样成立，但没有第二种必要）。
     *  写回去时按 [appConditionIndex] 换槽位，不会多加一条。
     */
    fun appCondition(kind: ConditionKind): BranchCondition? = conditions.getOrNull(appConditionIndex(kind))

    /**
     * 条件成不成立 —— **同种类之间「或」，不同种类之间「且」**。
     *
     *  按种类分组之后逐组问「这一组里有没有一条成立」：屏幕那两行 `亮屏` / `熄屏` 任一条成立即可
     *  （合起来就是「没停在锁屏界面」），而「屏幕」这一组与「前台应用」那一组之间仍要同时成立。
     *
     *  为什么必须只有这一份：块内几条条件的组合方式（且 / 或）一旦在界面、判定、提示里各写一遍，
     *  差一处就是「界面上配得出来、按下去不走那一支」，而且只在某个开关打开那天现形、不报任何错。
     */
    fun appliesTo(matches: (BranchCondition) -> Boolean): Boolean =
        conditions.groupBy { it.kind }.values.all { sameKind -> sameKind.any(matches) }

    /**
     * 这一块在 [trigger] 这一档下成立吗 —— [appliesTo] 是条件那一半的判据。
     *
     *  一条条件都没有的块**不成立**：宁可落到下一块 / 落到按键绑定那一档，也不执行一条用户
     *  还没想清楚的条件（那是「填了一半的规则」）。停用的块（[enabled] 为假）一律不成立。
     *
     *  **动作有没有配也在这一步判**（[Action.Follow] = 这一档不接管），[pickAction] 直接取赢家。
     */
    fun matches(
        trigger: GestureTrigger,
        matches: (BranchCondition) -> Boolean,
    ): Boolean = enabled && conditions.isNotEmpty() && appliesTo(matches) && actionOf(trigger) !is Action.Follow

    /**
     * 换掉第 [at] 条条件 —— **其余的留着**。
     *
     *  · 表是空的 ⇒ 直接放进去（等价于「添第一条」）；
     *  · [at] 越界 ⇒ 退回改第一条（调用方算错了下标时，宁可变错一面，也别把这一行清干净）。
     */
    fun replacingCondition(
        at: Int,
        condition: BranchCondition,
    ): SceneRule =
        when {
            conditions.isEmpty() -> copy(conditions = listOf(condition))
            at !in conditions.indices -> replacingCondition(0, condition)
            else -> copy(conditions = conditions.toMutableList().also { it[at] = condition })
        }

    /** 在这块**末尾再添一条**条件（同一种类能加几条由 `blockKindsOf` 管，判定侧按「或」算）。 */
    fun addingCondition(condition: BranchCondition): SceneRule = copy(conditions = conditions + condition)

    /** 去掉第 [at] 条条件（越界时原样返回）。 */
    fun withoutCondition(at: Int): SceneRule =
        if (at !in conditions.indices) this else copy(conditions = conditions.filterIndexed { index, _ -> index != at })

    companion object {
        /**
         * 一颗键（或一档组合）最多几个场景块 —— 二十块（用户口径）。
         *
         *  一块顶过去最多三条规则，所以这个数比原来那张表「宽松」不了多少：
         *  判定侧每按一次要走一遍这张表（时间区间 + 系统开关），而它是顺序匹配、第一条成立就赢。
         *
         *  到这一档时「添加场景」那颗按钮**置灰并说明原因**（见 `BranchHeaderAdd`），
         *  加行入口本身也拦一道（`addScene`）—— 手改配置 / 别的入口不该能越过它。
         */
        const val MAX_SCENES = 20

        /**
         * 一块里最多几条条件 —— 九条（用户口径）。
         *
         *  比「同一种类最多几条」（`blockKindsOf`，按各档取值数封顶）高一层：那一条管的是
         *  「同一种类别写重了」，这一条管的是**一块整体**的规模 —— 判定侧每块都要逐条过一遍，
         *  九条以上的一块已经读不动了（界面上也一屏放不下），而真需要那么多条件时，
         *  本来该拆成两块。
         *
         *  到这一档时：块尾那行「添加场景」置灰并说明原因，一级菜单里每一档也一并点不动
         *  （见 `appendCondition` 与 `conditionKindRows` 的 `blockFull`）。
         */
        const val MAX_CONDITIONS = 9

        /** 空块（一档都没配）—— 「添加场景」插的就是它。 */
        val EMPTY = SceneRule()
    }
}

/**
 * 从一张**场景表**里挑这一档该执行的动作 —— 全项目唯一实现。
 *
 *  语义两句：
 *  1. 从上往下找**条件成立、且这一档有值**的第一块，用它那一档的动作；
 *  2. 一块都不中 ⇒ 落到 [fallback]（这一档直接绑的那个动作）。表是空的 ⇒ 直接给 [fallback]。
 *
 *  「这一档有值」是 [Action.Follow] 与 [Action.None] 的区别（见 [SceneRule] 类注释）：没配的档不算接管，
 *  所以「只给长按配了动作的场景」不会把别的档一起吞掉。
 *
 *  为什么这句必须只有一份：条件分支的错**只在真机上看得见** —— 某个系统开关打开时本该走
 *  另一块，结果走了默认那条；两份实现里只要有一份判据差一点，就只会在那个开关打开那天现形，
 *  且不报任何错。
 *
 *  [trigger] 是**触发条件**（单击 / 双击 / 长按 / 三击 / 四击…）：一块能接管哪几档完全由它自己的
 *  [SceneRule.actions] 说，这里不假设档数。
 *
 * @param matches 条件是否成立 —— 由调用方给（读系统开关的那一层是 `ConditionProbe`）：
 *   这一层不认识系统接口。
 */
internal fun pickAction(
    scenes: List<SceneRule>,
    trigger: GestureTrigger,
    fallback: Action,
    matches: (BranchCondition) -> Boolean,
): Action = scenes.firstOrNull { it.matches(trigger, matches) }?.actionOf(trigger) ?: fallback

/**
 * 这一档**命中的是第几块**（`null` = 没有块接管，落到 [fallback] 那一档去了）。
 *
 *  与 [pickAction] **同一套判据**（条件成立 + 这一档有值 + 启用），只是不要动作、要身份。
 *  用在「按住组合的场景由首键按下时固定」那一档：按下那一刻记下命中的块，派发时按它取动作。
 *  两处各写一遍 `firstOrNull { it.matches(...) }` 的话，「哪一块赢了」迟早两边各答一套。
 *
 *  公开（不是 `internal`）：记归属的那一半在 `:app`（`host/GestureExec`），跨模块看不到 `:core` 的 internal。
 */
fun sceneIndexOf(
    scenes: List<SceneRule>,
    trigger: GestureTrigger,
    matches: (BranchCondition) -> Boolean,
): Int? = scenes.indexOfFirst { it.matches(trigger, matches) }.takeIf { it >= 0 }

/**
 * 这一档**有没有被某个场景块接管** —— 松手那一档要判「长按那一档命中系统场景就让位」。
 *
 *  与 [pickAction] 同一条判据（条件成立 + 这一档有值），只是不要动作、只要一个是非。
 */
internal fun sceneTakes(
    scenes: List<SceneRule>,
    trigger: GestureTrigger,
    matches: (BranchCondition) -> Boolean,
): Boolean = scenes.any { it.matches(trigger, matches) }

/**
 * 「松手」那一档该执行什么 —— 全项目唯一实现（[pickAction] 的松手版）。
 *
 *  长按被某一块接管时，这一次按压归**那一块**：它自己那一格说了算 —— **[SceneRule.hasRelease]**
 *  （开关开着 + 挑过动作）为真才执行那个动作，为假就是 `None`：这一下什么都不做，
 *  **不回落**按键自己那个松手动作。一块都不中才落到 [fallback]。
 *
 *  为什么这一格必须归块：松手动作是「这一档做什么」的另一半。留在按键身上时，场景一命中
 *  它就整条失效（真机口径「配了额外动作，按下去什么也没发生」）。
 *
 *  **开关与动作必须一起看**（[SceneRule.hasRelease] / 调用方传进来的 [fallback] 已经带过开关）：
 *  只看「存下来的动作」，块里挑过动作之后再把开关关掉照样会执行 —— 表现就是
 *  「这一档明明没开额外动作，按下去却冒出来一个」，也就是用户口中的「穿透」。
 */
internal fun pickReleaseAction(
    scenes: List<SceneRule>,
    fallback: Action,
    matches: (BranchCondition) -> Boolean,
): Action {
    val owner = scenes.firstOrNull { it.matches(GestureTrigger.LongPress, matches) } ?: return fallback
    return if (owner.hasRelease) owner.release else Action.None
}
