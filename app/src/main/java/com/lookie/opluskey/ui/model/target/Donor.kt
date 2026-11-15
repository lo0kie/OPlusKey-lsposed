package com.lookie.opluskey.ui.model.target

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.seal.Seal
import com.lookie.opluskey.ui.model.icon.DrawnIcons
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.COOLAPK_URL
import com.lookie.opluskey.ui.model.page.ISSUE_URL
import com.lookie.opluskey.ui.model.page.PROFILE_URL
import com.lookie.opluskey.ui.model.page.QQ_GROUP_URL
import com.lookie.opluskey.ui.model.page.TELEGRAM_URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * 匿名那一档在表格里显示成什么 —— 数据里**不写这个串**（`name` 写 `null` 或省略），
 * 显示时才替换成它。
 *
 *  为什么不把「匿名」两个字写进数据：那是**呈现**，跟着界面语言该不该变是另一回事，
 *  而写进 JSON 就等于把它固化成了一个人的名字 —— 与 `amount` 存**分**同一条理由
 *  （数据只描述事实，怎么画由界面定）。三个星号也不含任何语言信息。
 */
internal const val DONOR_ANONYMOUS = "***"

/**
 * 「捐赠列表」的那个**人**：一个名字 + 一笔金额（+ 一笔到账时间）。
 *
 *  [amountCents] 收的是**分**：金额是数才排得了序、加得了总，写成「¥30」那种显示串就等于把一次
 *  格式化固化进了数据里；而用**分**做整数存，浮点那点误差也进不来（文件里写的仍是**元**，
 *  可以带两位小数，换算在 [readDonors] 里做）。
 *
 *  [at] 是**只记录、不上屏**的那一位：给维护者看「这笔是什么时候到的」。页面不显示它，
 *  但**显示顺序按它排** —— 最早的在上（见 [sortedByTime]）。
 */
internal data class Donor(
    /** 上屏用的名字 —— **一定非空**：匿名在 [readDonors] 那一层就已经换成了 [DONOR_ANONYMOUS]。 */
    val name: String,
    /** 金额，单位**分**（`520` = ¥5.20）。 */
    val amountCents: Long,
    /** 到账时间 —— 文件里那串原样保留（`2026-10-17 19:36`），只记录、不上屏，但决定显示顺序。 */
    val at: String = "",
) {
    /**
     * 界面上显示的那一格 —— `¥` 是人民币记号（中英两档都一样），**始终两位小数**（显示到分）。
     */
    val amountText: String
        get() {
            val cents = amountCents.coerceAtLeast(0L)
            return "¥${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
        }
}

/**
 * 「捐赠列表」页的**原始数据** —— 源文件是 `core/seal/donors.json`，
 * 一行一个名字 + 一笔金额（+ 一笔到账时间）。**页面按 `at` 升序显示**（最早的在上），
 *  文件里数组的先后不影响；没写 `at` 的那几条排在最后。
 *
 *  数据与呈现分家：这里只负责「有哪些人给了多少」，**排成什么样**（两列、格子居中、分隔线、
 *  表头那些）在页面里，见 `ui/screen/DonateListScreen`。
 *
 *  要加人就往那份文件里加行 —— 一行一个 `{ "name": …, "amount": …, "at": … }`，加在哪一段都行
 *  （顺序由 `at` 排出来，见 [sortedByTime]）。
 *  `amount` 写**元**（可带两位小数，如 `5.20`），`at` 不上屏、但决定顺序；
 *  匿名的行 `name` 写 `null`（省略也行），表格里显示成 [DONOR_ANONYMOUS]。
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

/**
 * 读出名单（进程内只读一次）。读不到时是空表，不抛。
 *
 *  **只有真读到才缓存**：`Seal.open` 返回 `null` 是「这一次没读到」（资产不在包里 / 签名那一关
 *  没过，后者可能是瞬时状态），把它当成「名单是空的」缓存下来的话，这一次进入页面之后
 *  就再也不会重读了 —— 表现是「某次之后捐赠名单一直是空的」。
 */
internal fun donors(context: Context): List<Donor> = cache ?: (readDonors(context)?.let(::sortedByTime)?.also { cache = it } ?: emptyList())

/**
 * 名单的显示顺序 —— **按到账时间升序**（最早的在上）。
 *
 *  `at` 是 `YYYY-MM-DD HH:mm`，定长且高位在前 ⇒ **字典序就是时间序**，不必解析成时间对象，
 *  也就没有时区 / 本地化那一堆事。
 *
 *  没写 `at` 的那几条（空串）**排在最后**：空串字典序最小，直接比会跑到最上面去 ——
 *  它们是「时间没记」，不是「很早」。
 *
 *  稳定排序：同一分钟里的几条保持文件里的先后。
 */
private fun sortedByTime(rows: List<Donor>): List<Donor> = rows.sortedWith(compareBy({ it.at.isEmpty() }, { it.at }))

/**
 * 取出一个字段的文本；缺项 / 不是标量 / 是 JSON `null` 都算空串。
 *
 *  `JsonNull` 也是 `JsonPrimitive`，它的 `content` 是字符串 `"null"` —— 不排掉它，
 *  一条 `{"name": null}` 的记录会变成一颗叫「null」的捐赠者。
 */
private fun JsonElement?.text(): String = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()

/**
 * 真读一次：`assets/donors.json` → 对象数组 → 每个人的名字与金额。
 *
 *  `name` 写成 `null` / 空串 / 干脆不写 = **匿名**，上屏统一是 [DONOR_ANONYMOUS]
 *  （见那个常量的注释：`***` 不写进数据）。所以这里**不再丢行** —— 早先「没名字就丢掉」
 *  是为了不让表格第一格空着，现在匿名有明确的显示值，那一格不会空。
 */
private fun readDonors(context: Context): List<Donor>? {
    val bytes = Seal.open(context, DONORS_ASSET) ?: return null
    return try {
        val rows = Json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonArray
        rows.mapNotNull { row ->
            val o = row as? JsonObject ?: return@mapNotNull null
            //  名字去两端空白；空 ⇒ 匿名。**不丢行**：一笔到账总是要记的，匿不匿名是两说
            val name = o["name"].text().trim().ifEmpty { DONOR_ANONYMOUS }
            Donor(
                name = name,
                // 文件里写的是**元**（可以带两位小数），这里换算成**分**存。
                //  `round` 是为了吃掉二进制浮点那点误差：`5.20 * 100 = 519.9999…`。
                //  结果按 `Long` 收：金额超出 `Int` 范围时不能绕回负数再夹成 0（那会把一笔大额
                //  显示成「¥0.00」）。负数在这里就夹掉：文件里写错一个负号，界面上会画成「¥-5.-20」
                amountCents =
                    Math
                        .round((o["amount"].text().toDoubleOrNull() ?: 0.0) * 100)
                        .coerceAtLeast(0L),
                // 只记录、不上屏（见 [Donor.at]）；缺了就是空串，不报错
                at = o["at"].text().trim(),
            )
        }
    } catch (e: Throwable) {
        XLog.w("捐赠列表：$DONORS_ASSET 解不成名单（退成空态，下次进页重试）：${e.javaClass.simpleName}: ${e.message}")
        null
    }
}

/**
 * 「这个应用的哪一个界面」—— 选择应用页第二级的一行。
 *
 * 与 [LaunchableApp] 同一套思路：**只装界面要用的那几样**，`PackageManager` 的查询结果不进模型
 * （`ActivityInfo` 带着 `ApplicationInfo`、`metaData` 等一大坨，留在模型里等于让界面层持有 PM 对象）。
 *
 * @property target 写进配置的**完整目标写法**（`包名/类名`，带 `999@` 分身前缀时也一并带上）。
 *   它就是这一行的身份：选中态比较、去重、以及 `onPick` 交出去的都用它，不要再从别的字段拼。
 * @property name 完整类名（`com.tencent.mm.ui.LauncherUI`）—— 写日志与排错用。
 * @property label 展示名：活动自己声明了 `android:label` 就用它，否则用 [summary]
 *   （`ActivityInfo.loadLabel` 在没有自己的 label 时**回落成应用名**，一列全是同一个名字，读不出区别）。
 * @property summary 行尾那行小字：去掉包名前缀的类名（`ui.LauncherUI`）—— 与执行端
 *   `AppEntry.intentOf` 认的写法同源，用户对着它手改配置文件也认。
 * @property sortKey 名字的排序键（罗马化之后的小写 ASCII 串，见 `sortKeyOf`）：
 *   排序与搜索共用，拼音搜得到汉字名。
 * @property shortcutId 非空 = 这一行是**应用的快捷方式**（扫一扫 / 收付款），不是界面。
 *   此时 [target] 里的 `包名/…` 是快捷方式 id 而不是类名，写回时要走 `appshortcut:` 那一档。
 */
internal data class LaunchableActivity(
    val target: String,
    val name: String,
    val label: String,
    val summary: String,
    val sortKey: String,
    val shortcutId: String? = null,
)

/**
 * 捐赠的三个去处 —— 「捐赠打赏」那一行的浮动菜单与收款码窗口共用这一处（**顺序即菜单行序**）。
 *
 * @param id 收款码图在 assets 里的文件名（`donate/<id>.webp`，900×900 近乎黑白 + 无损 WebP），
 *   也是日志里的标识
 * @param labelKey 界面上的名字（字典键，见 [Strings]）
 */
internal enum class DonateTarget(
    val id: String,
    val labelKey: String,
) {
    QQ("qq", Strings.DONATE_QQ),
    WECHAT("wechat", Strings.DONATE_WECHAT),
    ALIPAY("alipay", Strings.DONATE_ALIPAY),
    ;

    /** 菜单行首 / 收款码缺席时的兜底字形。 */
    val icon: ImageVector
        get() =
            when (this) {
                QQ -> PageIcons.BrandQq
                WECHAT -> PageIcons.BrandWechat
                ALIPAY -> PageIcons.BrandAlipay
            }

    /** 界面上的名字（字典缺项时显示 [id]，与全项目同一条兜底口径）。 */
    val label: String get() = I18n.getOr(labelKey, id)
}

/**
 * 作者的去处 —— 「作者」那一行的浮动菜单用它（**顺序即菜单行序**）。
 *
 *  与 [FeedbackTarget] / [DonateTarget] 同一套：菜单行首的字形与目标地址都写在这一处，
 *  界面只负责摆出来。
 *
 *  **两处一律可点**（与「反馈问题」三档同一条口径）：菜单里不留灰行 —— 没配就置灰，
 *  用户读到的不是「还没接上」而是「这个功能坏了」。
 *
 *  **酷安排第一**（国内用户更常用的那个），GitHub 第二 —— 别按字母序「顺手」把它调回去。
 */
internal enum class AuthorTarget(
    val id: String,
    val labelKey: String,
    val url: String,
) {
    COOLAPK("coolapk", Strings.AUTHOR_COOLAPK, COOLAPK_URL),
    GITHUB("github", Strings.AUTHOR_GITHUB, PROFILE_URL),
    ;

    /** 菜单行首的字形（见各自的取值处）。 */
    val icon: ImageVector
        get() =
            when (this) {
                COOLAPK -> DrawnIcons.BrandCoolapk
                GITHUB -> PageIcons.BrandGithub
            }

    /** 界面上的名字（字典缺项时显示 [id]，与全项目同一条兜底口径）。 */
    val label: String get() = I18n.getOr(labelKey, id)
}

/**
 * 反馈渠道 —— 「反馈问题」那一行的浮动菜单用它（**顺序即菜单行序**）。
 *
 *  与 [DonateTarget] 同一套：菜单行首的字形与目标地址都写在这一处，界面只负责摆出来。
 *
 *  **三档一律可点** —— 菜单里不留灰行：没配就置灰，用户读到的不是「还没接上」而是
 *  「这个功能坏了」。三档地址现在都是真的（QQ 群 / Telegram / Issue）。
 */
internal enum class FeedbackTarget(
    val id: String,
    val labelKey: String,
    val url: String,
) {
    QQ_GROUP("qq_group", Strings.FEEDBACK_QQ_GROUP, QQ_GROUP_URL),
    TELEGRAM("telegram", Strings.FEEDBACK_TELEGRAM, TELEGRAM_URL),
    ISSUE("issue", Strings.FEEDBACK_ISSUE, ISSUE_URL),
    ;

    /** 菜单行首的字形。 */
    val icon: ImageVector
        get() =
            when (this) {
                // 「群」没有品牌字形，用「一群人」那颗
                QQ_GROUP -> PageIcons.FeedbackGroup

                TELEGRAM -> PageIcons.BrandTelegram

                ISSUE -> PageIcons.BrandGithub
            }

    /** 界面上的名字（字典缺项时显示 [id]，与全项目同一条兜底口径）。 */
    val label: String get() = I18n.getOr(labelKey, id)
}

/**
 * 「打开应用」页里的一条 —— 界面要用的几样。
 *
 *  **不带图标**：查列表时不预取（两百多项全量取图标就是两百多次跨进程调用、还会把一批 `Drawable`
 *  钉在内存里），只记包名，真滚到哪一行才由 [AppIcon] 取哪一行，取到的那份记进 LRU。
 */
internal data class LaunchableApp(
    val packageName: String,
    val label: String,
    /**
     * 是不是系统应用（`FLAG_SYSTEM`）——「显示系统应用」开着时拿去画「系统 / 用户」小标签。
     *
     * 用 `FLAG_SYSTEM` 而非「来自 `getInstalledApplications` 那一批」：launcher 图标查询里
     * 本就混着系统应用，按来源分会把它们当用户应用。
     */
    val system: Boolean,
    /**
     * 是不是 Xposed 模块。标签三选一：「模块」压过「系统」压过「用户」——
     * 一个模块同时是系统应用时按模块报。
     */
    val xposed: Boolean,
    /**
     * **两个显示开关都关着时**，这一行该不该出现。
     *
     *  为 `true` 的两批：有启动图标的应用（`MAIN + CATEGORY_LAUNCHER` 查得到），以及 Xposed 模块
     *  （模块默认就该在列表里，哪怕它没有启动图标）。其余是 `getInstalledApplications` 补回来的
     *  那一批 —— 没有启动图标的应用 / 系统应用，要拨「显示系统应用」才列。
     *
     *  这一位是**取清单时一次判死的**（来源就决定了它），所以「显示」这件事不再需要在
     *  「查哪一档清单」上做文章：清单一次取全，两个开关纯过滤（见 `AppPickerUiState.shown`）。
     */
    val byDefault: Boolean,
    /**
     * 解得出来入口 —— `app:` 那一步真的拉得起来。
     *
     * 与执行端（[AppLauncher.launchApp]）同一把尺子，拉不起来就在行尾挂「不可打开」提示。
     * 系统应用缺入口的居多，用户应用里也有（被停用 / 非导出入口 / 装了一半）。
     */
    val launchable: Boolean,
    /**
     * 这个名字的**排序键**（罗马化之后的小写 ASCII 字母数字串，见 [sortKeyOf]）。
     *
     *  随模型一起带出来、只算一次：列表按它排序，搜索也按它比（只比原名 / 包名的话，
     *  输入 `shezhi` 搜不到「设置」——名字与包名里都没有这串字母）。
     *  罗马化不便宜，所以既不能放进比较器里每比一次算一次，也不能在过滤时逐条现算。
     */
    val sortKey: String,
    /**
     * 这一份属于哪个用户 —— `0` 是主用户，其余是多开（分身）的 user id。
     *
     *  同一行在列表里的**身份**由 [target] 决定，不是 [packageName]：主用户的微信与分身微信
     *  包名相同，只按包名比会让两行互相顶上（选中态、去重、写回都是）。
     */
    val userId: Int = 0,
) {
    /** 是不是分身（多开槽里的那一份）—— 列表行据此挂那枚「分身」标签。 */
    val clone: Boolean get() = userId != 0

    /**
     * 这一份的**目标写法**（`999@com.tencent.mm`，见 `AppTarget`）—— 选中态与写回都用它。
     *
     *  **不带名字**：名字是显示信息、单独存在配置里（`Config.appNames`），不再拼进目标。
     *  以前拼进来，每个读它的地方都得先按 `|` 切一刀，漏一处就退化成包名。
     */
    val target: String get() =
        com.lookie.opluskey.core.model.AppTarget
            .format(packageName, userId)
}

/**
 * 分身那一份的显示名 —— `微信 1`（`slot` 由 `AppCloneCatalog.slotOf` 给，`0` = 主用户，不加编号）。
 *
 *  选择应用页的行名与「打开应用」格的小标题**都调这一处**：两处各拼一套的话，网格里的
 *  「微信 1」与列表里的「微信 1」可能指向不同用户。
 *
 *  @param slot 槽号：`> 0` 就是它，`0` = 主用户；
 *    [AppCloneCatalog.UNKNOWN_SLOT]（负数）= **查不到槽号**（那个分身已经删了，或者那个 user
 *    根本不是分身类型）—— 这时改用 `· user id`（`微信 · 998`），**不能**退回不带编号的
 *    `微信`：那与真·主用户那一行同名，一眼分不出是谁。
 */
internal fun cloneLabel(
    base: String,
    slot: Int,
    userId: Int,
): String =
    when {
        slot > 0 -> "$base $slot"
        userId != 0 -> "$base - $userId"
        else -> base
    }
