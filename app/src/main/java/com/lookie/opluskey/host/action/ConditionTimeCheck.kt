package com.lookie.opluskey.host.action

import com.lookie.opluskey.core.model.TimeRange
import java.util.Calendar

// 时间区间那一档判据 —— 从 `ConditionChecks` 拆出来：那一组全是「带取值的系统状态」，
// 只有这一档读的是**时钟**（`TimeRange` + 本地时区），与系统服务那边不是一路。

/**
 * **时间区间**：`HH:mm-HH:mm`（`22:00-07:00` 跨午夜）。
 *
 *  解析与比较全在 [TimeRange] 里 —— 跨午夜那个「或」写错一次就是「夜里怎么也不成立」，
 *  所以那种地方只留一份实现。认不出的串 ⇒ 不成立。
 *
 *  「现在几点」取**设备本地时区**（用户说的「晚上十点」就是他手机上的十点），
 *  不读 GMT、也不管夏令时（一天里的第几分钟本来就是本地概念）。
 */
internal fun timeMatches(
    value: String,
    now: Calendar = Calendar.getInstance(),
): Boolean {
    val range = TimeRange.parse(value) ?: return false
    return range.contains(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
}
