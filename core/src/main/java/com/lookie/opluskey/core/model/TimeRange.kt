package com.lookie.opluskey.core.model

import java.util.Locale

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
            val hour = parts[0].trim().toIntOrNull()?.takeIf { it in 0..23 }
            val minute = parts[1].trim().toIntOrNull()?.takeIf { it in 0..59 }
            return if (hour != null && minute != null) hour * 60 + minute else null
        }

        /**
         * `H:mm` / `HH:mm` → 当天第几分钟；格式不对或越界返回 `null`。
         *
         *  一律两位补零、`Locale.ROOT`：这个串要写进配置文件，不能被设备语言影响
         *  （与 `CommandKind.fromId` 用 `Locale.ROOT` 同一条纪律）。
         */
        private fun clock(minuteOfDay: Int): String {
            val h = (minuteOfDay / 60).coerceIn(0, 23)
            val m = (minuteOfDay % 60).coerceIn(0, 59)
            return String.format(Locale.ROOT, "%02d:%02d", h, m)
        }
    }
}
