package com.lookie.opluskey.ui.model

import android.content.Context
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.seal.Seal
import org.json.JSONArray

/**
 * 「捐赠列表」的那个**人**：一个名字 + 一笔金额（+ 一笔到账时间）。
 *
 *  [amountCents] 收的是**分**：金额是数才排得了序、加得了总，写成「¥30」那种显示串就等于把一次
 *  格式化固化进了数据里；而用**分**做整数存，浮点那点误差也进不来（文件里写的仍是**元**，
 *  可以带两位小数，换算在 [readDonors] 里做）。
 *
 *  [at] 是**只记录、不上屏**的那一位：给维护者看「这笔是什么时候到的」。页面不显示它，
 *  也**不**拿它排序 —— 顺序仍以文件里数组的先后为准。
 */
internal data class Donor(
    val name: String,
    /** 金额，单位**分**（`520` = ¥5.20）。 */
    val amountCents: Int,
    /** 到账时间 —— 文件里那串原样保留（`2026-10-17 19:36`），只记录、不上屏。 */
    val at: String = "",
) {
    /**
     * 界面上显示的那一格 —— `¥` 是人民币记号（中英两档都一样），**始终两位小数**（显示到分）。
     */
    val amountText: String
        get() = "¥${amountCents / 100}.${(amountCents % 100).toString().padStart(2, '0')}"
}

/**
 * 「捐赠列表」页的**原始数据** —— 源文件是 `core/seal/donors.json`，
 * 一行一个名字 + 一笔金额（+ 一笔到账时间），**顺序即显示顺序**。
 *
 *  数据与呈现分家：这里只负责「有哪些人给了多少」，**排成什么样**（两列、格子居中、分隔线、
 *  表头那些）在页面里，见 `ui/screen/DonateListScreen`。
 *
 *  要加人就往那份文件里加行 —— 一行一个 `{ "name": …, "amount": …, "at": … }`，顺序即显示顺序。
 *  `amount` 写**元**（可带两位小数，如 `5.20`），`at` 只作记录、不上屏。
 *  写成什么样由旁边那份 `donors.schema.json` 定（编辑器照它给提示与报错），加行前先看它。
 *  空数组 `[]` 是合法状态 —— 页面走「还没有捐赠记录」那一档。
 *
 *  读法走 `core/seal` 的 [Seal]：**release 包里这份文件是封着的**（构建期由 `:core` 的封存任务
 *  打进 `assets/sealed.bin`，明文不落地），debug 才直接读明文。不管哪一侧，都**不能用
 *  `context.assets`** —— 那是**宿主** APK 的资源，本模块的资源要按包名问 `PackageManager` 要
 *  （[SealAssets] 两条路都试）。
 *
 *  读不到 / 解析不了时同样返回**空表**（如资源缺失、JSON 写坏）：页面据此走空态那一档；
 *  同时留一句 `w` 当判据 —— 空态本身也是合法状态，两者的区别就在这句日志上
 *  （「读不到资产」那句在 [Seal]，这一层只管「解析不了」）。
 *
 *  文件名跟着里面的类走（`Donor`）—— 读取那几行与它同属一件事，一并放在这里。
 */
private const val DONORS_ASSET = "donors.json"

/**
 * 进程内缓存的那一份 —— 名单是静态数据，不值得每次进这一页都把模块 APK 再当 zip 开一遍。
 *
 *  `null` = 还没读过（与「读到了空表」是两件事，后者是合法结果）。
 */
@Volatile
private var cache: List<Donor>? = null

/** 读出名单（进程内只读一次）。读不到时是空表，不抛。 */
internal fun donors(context: Context): List<Donor> = cache ?: readDonors(context).also { cache = it }

/** 真读一次：`assets/donors.json` → 对象数组 → 名字非空的行。 */
private fun readDonors(context: Context): List<Donor> {
    val bytes = Seal.open(context, DONORS_ASSET) ?: return emptyList()
    return try {
        val array = JSONArray(String(bytes, Charsets.UTF_8))
        (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            val name = row.optString("name", "").trim()
            // 没有名字的行画不出来（表格的第一个格子会空在那里），直接丢
            if (name.isEmpty()) {
                null
            } else {
                Donor(
                    name = name,
                    // 文件里写的是**元**（可以带两位小数），这里换算成**分**存。
                    //  `round` 是为了吃掉二进制浮点那点误差：`5.20 * 100 = 519.9999…`。
                    amountCents = Math.round(row.optDouble("amount", 0.0) * 100).toInt(),
                    // 只记录、不上屏（见 [Donor.at]）；缺了就是空串，不报错
                    at = row.optString("at", "").trim(),
                )
            }
        }
    } catch (e: Throwable) {
        XLog.w("捐赠列表：$DONORS_ASSET 解不成名单（退成空态）：${e.javaClass.simpleName}: ${e.message}")
        emptyList()
    }
}
