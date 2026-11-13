package com.lookie.opluskey.core.model

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
 * ```
 *
 *   · **不带后缀** = 输出值，即**场景条件那一套词表**（`normal` / `vibrate` / `silent`、
 *     `on` / `off` / `locked` …）。要拿去比较 / 记日志 / 写给别的程序就用它：它与
 *     「场景里写 `ringer:vibrate` 会命中」是同一个串，两边共用同一份判据（`ConditionProbe`）。
 *   · **[SYSTEM_VAR_TEXT_SUFFIX]**（`${sys.ringer.text}`）= **文案**：界面上念的那几个字
 *     （`静音` / `开` / `亮屏`），跟着界面语言走。要弹给人看就用它。
 *
 *  界面上点一行时先问「要哪一种」，两行的样子就是此刻这个值分别在两种写法下的结果。
 *  不默认替你选：一段弹给人的字拿去比较会永远不相等，反过来一句英文键名弹出来也不像话。
 *
 * ## 哪些档能当变量
 *
 *  [ConditionKind.APP] / [ConditionKind.APP_NOT] 的取值是一组包名，没有「此刻是哪一组」这个说法，
 *  所以不进 [SYSTEM_VAR_KINDS]。
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

/** 这一档在变量里的名字（`sys.ringer`）；说不清「此刻是什么」的那两档（前台应用 / 以外）返回 `null`。 */
val ConditionKind.systemVarName: String?
    get() =
        when (this) {
            //  取值是一组包名：没有「此刻是哪一组」这个说法，所以不当变量
            ConditionKind.APP, ConditionKind.APP_NOT -> null

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
