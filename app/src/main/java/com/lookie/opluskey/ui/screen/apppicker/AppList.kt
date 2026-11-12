package com.lookie.opluskey.ui.screen.apppicker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.icu.text.Transliterator
import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.bridge.AppEntry
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.target.LaunchableApp
import com.lookie.opluskey.ui.model.target.cloneLabel

/**
 * 本机装着的应用，按名字排序 —— **一次取全**。
 *
 * 两条来源：
 *  1. 有启动图标的那一批（`ACTION_MAIN` + `CATEGORY_LAUNCHER`），外加 Xposed 模块
 *     （模块默认就该在列表里，见 [xposedModules]）；
 *  2. `getInstalledApplications` 补回来的其余应用 —— 没有启动图标的系统应用 / 第三方应用。
 *
 * ## 为什么不再「按开关分两档取」
 *
 *  上一版把「显示系统应用 / 显示失效应用」做成**取法**（`includeAll`）：拨开关 → 换一种查询 →
 *  重查一遍 → 换缓存。这等于是把「这一行该不该出现」押在两件极容易对不上的账上 ——
 *  上一趟留下的取法（`lastMode`）、缓存是哪一档查的（`cachedAppsIncludeAll`）、
 *  以及「重查失败」时那句没人看得见的日志。任一处错位，用户看到的就是**开关按了没反应**；
 *  「结束应用」那一趟更是直接踩坑：它的 `stopApp` 没被算进取法，于是要停那些没有启动图标的应用时，
 *  清单里压根没有它们。
 *
 *  现在清单一次取全，两个开关都只是 `AppPickerUiState.shown` 上的**过滤**
 *  （`LaunchableApp.byDefault` / `launchable`）。拨开关不触发查询、不看缓存是哪一档，
 *  也就没有能对不上的账。
 *
 *  代价只有一处：全量取法要几百个 `ApplicationInfo` 与它们的 label。所以「点不点得开」
 *  **不逐包问** [AppEntry.resolve]，改由 [AppEntry.launchablePackages] 一次问全机
 *  —— 逐包问是几百次 binder 调用，一次问是三次查询。
 *
 * @param onDefaultTier **默认档**（有启动图标的 + Xposed 模块 + 分身）算好就先交出去一次 ——
 *   页面关着「显示系统应用」时，这一批**就是**它能显示的全部；剩下那几百个没有图标的应用
 *   （要各自 `loadLabel` + 罗马化）等它之后才算。这一档就是「当前过滤判据下的那批」：
 *   先把它交出去，用户等的时间从「全量」缩到「一档」。
 * @return 全量清单；**查失败返回 null**（不是空清单）—— 空清单是一份有效结论
 *   （「这台机器上确实一个可启动的应用都没有」），拿它去覆盖缓存会把上一次的好清单抹掉，
 *   界面显示成「没有找到可以打开的应用」（见 [AppPickerScreen] 的调用点）。
 */
internal suspend fun loadLaunchableApps(
    context: android.content.Context,
    onDefaultTier: (suspend (List<LaunchableApp>) -> Unit)? = null,
): List<LaunchableApp>? {
    val t0 = android.os.SystemClock.uptimeMillis()
    val pm = context.packageManager
    val intent =
        android.content
            .Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    val romanizer = hanLatin()
    val collator = chinaCollator()
    return try {
        // 有启动图标的应用
        val launched =
            pm
                .queryIntentActivities(intent, 0)
                .asSequence()
                .mapNotNull { it.activityInfo?.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != ModuleInfo.PACKAGE }
                .toList()
        val (xposedList, xposedPkgs) = xposedModules(pm, launched)
        // 默认档（两个开关都关着）就该出现的是这两批：有启动图标的、以及模块。
        //  它同时是「其余应用」要排除的那一份。
        val known =
            (launched + xposedList).mapTo(HashSet()) { it.packageName }
        // 「点得开吗」一次问全机 —— 见 [AppEntry.launchablePackages]（逐包 `resolve` 太贵）。
        //  它留在默认档这一侧：那一批的「显示失效应用」也要判，而它只有三次查询。
        val launchable = AppEntry.launchablePackages(pm)
        //  其余装着的应用（`getInstalledApplications` 那几百个 + 逐个 label）**挪到默认档交出去之后**：
        //  它要几百个 `ApplicationInfo` 再各自 `loadLabel` + 罗马化，而默认档那批根本用不到它 ——
        //  关着「显示系统应用」时那些行一行都不显示。
        val sources = TierSources(launched, xposedList, xposedPkgs, known, launchable)
        val defaultRows = defaultTier(context, pm, sources, romanizer, collator)
        // **默认档先交出去**：关着「显示系统应用」时，这一批就是页面上要显示的全部。
        //  调用方据此立刻结束加载态，剩下那几百个没有图标的应用在后台接着算（见函数注释）。
        XLog.w(
            "可启动应用：默认档 ${defaultRows.size} 个先上屏，耗时 " +
                "${android.os.SystemClock.uptimeMillis() - t0}ms（其余在后台接着算）",
        )
        onDefaultTier?.invoke(defaultRows)
        // 其余装着的应用 —— 只有打开「显示系统应用」时才用得上。
        //  不在这里筛「拉不拉得起来」：能不能打开是列表那一层的事（`LaunchableApp.launchable`），
        //  取清单这一层只负责把东西**取全**。能拿到全量包列表靠宿主自己声明了 `QUERY_ALL_PACKAGES`
        //  —— 界面代码跑在宿主进程，包可见性按宿主算。
        val others =
            pm
                .getInstalledApplications(0)
                .asSequence()
                .filter { it.packageName !in known }
                .filter { it.packageName != ModuleInfo.PACKAGE }
                .toList()
        val otherRows =
            others.map { app ->
                val pkg = app.packageName
                appRow(pm, app, RowFacts(pkg in xposedPkgs, false, pkg in launchable), romanizer)
            }
        // 三条来源必须跨来源去重：Xposed 模块的规范写法是「一个 launcher activity + 一个挂
        // MODULE_SETTINGS 的 activity-alias」，同一个包在 launcher 与模块两批里各出现一次。
        // 不去重 ⇒ 列表两行同包名 ⇒ `items(key = ...)` 抛「Key ... was already used」，异常落在
        // 宿主 Activity 的组合里 ⇒ 打开「选择应用」页即崩宿主页面。
        val all = (defaultRows + otherRows).distinctBy { it.target }.sortedWith(appOrder(collator))

        // 打头几条 + 耗时便于核对（转写器是否生效、这次查了多久）
        XLog.w(
            "可启动应用：${all.size} 个（默认档 ${all.count { it.byDefault }} 个、" +
                "有启动图标 ${launched.size} 个、模块 ${xposedList.size} 个、" +
                "额外追加 ${others.size} 个、点得开 ${all.count { it.launchable }} 个），" +
                "耗时 ${android.os.SystemClock.uptimeMillis() - t0}ms，" +
                "排序（前 10）：" + all.take(10).joinToString(" / ") { it.label },
        )
        all
    } catch (e: Throwable) {
        XLog.w("查可启动应用失败（本页保留上一次的清单 / 显示空提示）", e)
        null
    }
}

/**
 * 一个应用名的排序键 —— 罗马化之后的小写 ASCII 字母数字串。
 *
 * 汉字经 ICU 的 `Han-Latin` 转成拼音，再走 NFKD 拆出组合符号、只留字母数字：不能只滤非
 * a-z，声调是组合符号（`ǎ` = `a` + U+030C），按字符过滤会把元音一起丢掉（`bǎi` → `bi`）。
 * 转写器为 null / 抛异常 / 结果为空时退回原名小写。
 */
internal fun sortKeyOf(
    label: String,
    romanizer: android.icu.text.Transliterator?,
): String {
    val romanized =
        try {
            romanizer?.transliterate(label) ?: label
        } catch (e: Throwable) {
            label
        }
    val decomposed =
        java.text.Normalizer.normalize(
            romanized,
            java.text.Normalizer.Form.NFKD,
        )
    val sb = StringBuilder(decomposed.length)
    for (ch in decomposed) {
        // 全角字母 / 数字经 NFKD 已折成半角，这里只剩 ASCII 判据
        if (ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z') {
            sb.append(ch.lowercaseChar())
        }
    }
    return sb.toString().ifEmpty { label.lowercase(java.util.Locale.ROOT) }
}

/**
 * 本机的 **Xposed 模块**（旧式 + 新式 libxposed）—— 默认就列，不看「显示系统应用」开关。
 *
 *  1) 旧式：清单里挂 `MODULE_SETTINGS` 这个 category（`xposedminversion` 那类 metaData 在
 *    `dumpsys` 里不落地，翻 APK 的 `META-INF/xposed/` 太贵）；
 *  2) 新式 libxposed：**不声明** `MODULE_SETTINGS`，而是挂一个名叫
 *    `io.github.libxposed.service.XposedProvider` 的 provider（签名里那个
 *    `io.github.libxposed.service` 包就是它的身份）。真机上 `com.wizpizz.reddidnt` 2.0.0 即此：
 *    `query-activities -c …MODULE_SETTINGS` 查不到它，于是列表里一行「模块」标签都没有。
 *
 *  为什么不逐包翻 APK 里的 `META-INF/xposed/module.prop`：那是「200+ 次开 zip」的贵路子；
 *  而 `queryContentProviders(null, 0, 0)` 是**一次** binder 调用拿回全机 provider，筛个名字就够了
 *  —— 与上面那次 `queryIntentActivities` 同量级。
 *
 * @param launched 已经有 launcher 图标的那一批（新式模块里没图标的那部分才要单独补进来）
 * @return 模块的 `ApplicationInfo` 清单 + 它们的包名集合（后者给「这一行是不是模块」用）
 */
private fun xposedModules(
    pm: android.content.pm.PackageManager,
    launched: List<android.content.pm.ApplicationInfo>,
): Pair<List<android.content.pm.ApplicationInfo>, Set<String>> {
    val xposedList =
        pm
            .queryIntentActivities(
                android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(
                    XPOSED_MODULE_SETTINGS_CATEGORY,
                ),
                0,
            ).asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ModuleInfo.PACKAGE }
            .toList()
    val libXposedPkgs =
        runCatching { pm.queryContentProviders(null, 0, 0) }
            .onFailure { XLog.w("查 provider 失败，新式 libxposed 模块认不出来", it) }
            .getOrNull()
            ?.asSequence()
            ?.filter { it.name == LIBXPOSED_PROVIDER }
            ?.mapNotNull { it.packageName }
            ?.filter { it != ModuleInfo.PACKAGE }
            ?.toSet()
            .orEmpty()
    // 新式模块里**没有 launcher 图标**的那些，也要像旧式模块一样默认出现在列表里
    // （有图标的本就在 `launched` 那一批）。拿不到 `ApplicationInfo` 的跳过。
    val libXposedList =
        libXposedPkgs
            .filter { pkg -> (launched + xposedList).none { it.packageName == pkg } }
            .mapNotNull { pkg -> runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() }
    if (libXposedPkgs.isNotEmpty()) {
        XLog.w("新式 libxposed 模块：${libXposedPkgs.joinToString()}")
    }
    val pkgs = (xposedList.map { it.packageName } + libXposedPkgs).toCollection(HashSet())
    return (xposedList + libXposedList) to pkgs
}

/**
 * **分身**（同一个应用在别的用户里的那一份）—— 它们是另一个 Android 用户，所以在主用户的
 * 清单里根本看不到：换用户查包、槽号、名字都由 `AppCloneCatalog` 那边给。
 *
 *  这里只拿到「包名 + 名字 + 槽号」，其余与普通应用一样。
 */
internal fun cloneRows(
    context: android.content.Context,
    romanizer: android.icu.text.Transliterator?,
): List<LaunchableApp> =
    AppCloneCatalog.entries(context).map { entry ->
        val label = cloneLabel(entry.label, entry.slot, entry.userId)
        //  「能不能打开」对分身也要真判一次（主清单那一行本来就判了）：
        //   写死 `true` 会让分身永远不被标「不可打开」——「隐藏失效应用」与确认键的闸
        //   都判不到它（五、3 不伪造能力）。
        //   判不出来（取不到 per-user 的 PM）时仍按「能打开」算：分身与主用户同包名，
        //   拿主用户的 PM 问必抛，一律判 false 会把好端端的分身从列表里滤掉。
        val clonePm = AppCloneCatalog.packageManagerFor(context, entry.userId)
        val launchable = clonePm?.let { AppEntry.resolve(it, entry.packageName) != null } ?: true
        LaunchableApp(
            packageName = entry.packageName,
            label = label,
            system = entry.system,
            launchable = launchable,
            //  分身的「默认档」跟着**能不能打开**走（与改造前一致：那时默认档是
            //  「有启动图标的应用」再按 `launchable` 过一遍，而分身不属于那一批来源，
            //  全靠这条过滤才进得来）。判成打不开的那一份要拨「显示失效应用」才看得到。
            byDefault = launchable,
            xposed = false,
            sortKey = sortKeyOf(label, romanizer),
            userId = entry.userId,
        )
    }

/**
 * 一条 `ApplicationInfo` 该是哪一档 —— 取清单时**一次判死**，不再往 `appRow` 里多塞参数。
 *
 * @param xposed 这个包是不是 Xposed 模块（三个来源里只有模块那两批需要标）
 * @param byDefault 默认档（两个显示开关都关着）该不该列出它 —— 有启动图标的、以及模块
 * @param launchable 点不点得开。**整表一次判好**（[AppEntry.launchablePackages]），
 *   不在 `appRow` 里逐包 [`AppEntry.resolve`]：几百行就是几百次 binder 调用
 */
internal class RowFacts(
    val xposed: Boolean,
    val byDefault: Boolean,
    val launchable: Boolean,
)

/** 一条 `ApplicationInfo` → 界面要的那一行。launcher / 模块 / 系统应用三条来源共用。 */
internal fun appRow(
    pm: android.content.pm.PackageManager,
    ai: android.content.pm.ApplicationInfo,
    facts: RowFacts,
    romanizer: android.icu.text.Transliterator?,
): LaunchableApp {
    val label =
        try {
            ai.loadLabel(pm).toString()
        } catch (e: Throwable) {
            ai.packageName
        }
    // 没有代码的包（`android:hasCode="false"`）一个入口都给不出来：查得到活动、类却不在包里。
    //  `AppEntry.resolve` 里同样有这一道（真机上 OPPO 的 `oplus` 就栽在这），这里补上以免
    //  「批量算出能开、逐包问却说不能开」。
    val hasCode = ai.flags and android.content.pm.ApplicationInfo.FLAG_HAS_CODE != 0
    return LaunchableApp(
        packageName = ai.packageName,
        label = label,
        system = ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
        launchable = facts.launchable && hasCode,
        byDefault = facts.byDefault,
        // 注意：这里**不再**顺手问「支不支持小窗」：那个问法自带一条系统提示（见 [MiniWindowSupport]），
        //    整表问一遍就是「进页弹一串 toast」。改到点确认键那一刻只问一次。
        xposed = facts.xposed,
        // 排序键在这里算一次（罗马化不便宜）：排序与搜索共用它，见 [LaunchableApp.sortKey]
        sortKey = sortKeyOf(label, romanizer),
    )
}

/**
 * 汉字 → 拼音的转写器（ICU 自带，`icudt*.dat` 里有 `Han-Latin`）；取不到退回原名排序。
 *
 *  两页共用同一把排序尺子（见 [nameOrder]）：各造一个转写器没有意义 —— 排序键必须是同一个来源，
 *  否则同一个应用在两处排的位置不同。
 */
internal fun hanLatin(): android.icu.text.Transliterator? =
    try {
        android.icu.text.Transliterator
            .getInstance("Han-Latin")
    } catch (e: Throwable) {
        XLog.w("取不到 Han-Latin 转写器，应用列表退回原名排序：${e.javaClass.simpleName}")
        null
    }

/** 兜底比较器（排序键相同时比原名）；取不到就只按键比，仍稳定。 */
internal fun chinaCollator(): java.text.Collator? =
    try {
        java.text.Collator.getInstance(java.util.Locale.CHINA)
    } catch (e: Throwable) {
        null
    }

/**
 * 「按名字排」的那把尺子 —— 应用那一级、界面那一级、进程那一页**共用这一份**。
 *
 *  三个维度，**顺序不能换**：
 *  1. **首字母**（`sortKey` 的第一个字符）—— 决定它落在哪一段；
 *  2. 同一首字母下，**中文始终排在英文前面**（`微信` 在 `WeChat` 之前）——
 *     只按拼音串比的话 `wechat` < `weixin`，中英会穿插着排（用户口径：不行）；
 *  3. 同语言内部再按**拼音串**比下去（中文之间比的是第一个字之后的那些音，
 *     也就是「取后面的字」—— `微博 weibo` 在 `微信 weixin` 之前）。
 *
 *  再往下才是 [Collator] 比原名、[tieBreak] 兜底键（应用比包名、界面比类名、进程比 PID）——
 *  这两级只在前面完全打平时才用到，保证顺序稳定。
 *
 *  排序键本身**不含语言标记**：它还兼着搜索（`sortKey.contains(拼音)`），往里塞一位标记就把
 *  搜索弄坏了。语言这件事在比较时现判（见 [containsHan]）。
 *
 * @param sortKey 这一项的名字排序键（纯拼音，见 [sortKeyOf]）
 * @param label 这一项的展示名（判语言看它）
 * @param tieBreak 前三级都相同时的兜底键（必须唯一，否则顺序不稳定）
 */
internal fun <T> nameOrder(
    sortKey: (T) -> String,
    label: (T) -> String,
    tieBreak: (T) -> String,
    collator: java.text.Collator?,
): Comparator<T> =
    Comparator { a, b ->
        val keyA = sortKey(a)
        val keyB = sortKey(b)
        val byHead = (keyA.firstOrNull() ?: ' ').compareTo(keyB.firstOrNull() ?: ' ')
        if (byHead != 0) {
            byHead
        } else {
            val hanA = containsHan(label(a))
            val hanB = containsHan(label(b))
            if (hanA != hanB) {
                if (hanA) -1 else 1
            } else {
                val byKey = keyA.compareTo(keyB)
                if (byKey != 0) {
                    byKey
                } else {
                    val byName = collator?.compare(label(a), label(b)) ?: 0
                    if (byName != 0) byName else tieBreak(a).compareTo(tieBreak(b))
                }
            }
        }
    }

/**
 * 这个名字里有没有汉字 —— 「同一首字母下中文排前面」的判据。
 *
 *  判的是**原文**（不是罗马化之后的串）：`Han-Latin` 只转汉字，一个纯英文名不会因此多出汉字。
 *  范围取 CJK 统一表意文字那一块（含扩展 A 与兼容区）：常见的中文应用名都落在 U+4E00–U+9FFF，
 *  多扫两个区只为「生僻字名字也算中文」，代价可以忽略。
 */
private fun containsHan(text: String): Boolean =
    text.any { ch ->
        ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF || ch.code in 0xF900..0xFAFF
    }

/** 应用列表的排序：见 [nameOrder]（兜底键是包名）。 */
internal fun appOrder(collator: java.text.Collator?): Comparator<LaunchableApp> =
    nameOrder({ it.sortKey }, { it.label }, { it.packageName }, collator)

// 「选择应用」那一页清单的**默认档**：默认档要的那几批来源（[TierSources]）与拿它们
// 组装出清单的 [defaultTier]。
//
// 这一支的取数预算与「其余应用」那批完全不同：这一批要全部算完才有意义（它就是要先上屏
// 的那一份），那一批几百个包各自 `loadLabel`，能拖就往后拖。

/**
 * 一次查询的几批「已知来源」—— 交给 [defaultTier] 的那几样「整趟共用」的东西。
 *
 *  单独拎出来是因为它会让 `defaultTier` 变成九个参数的函数：参数一多，调用点按顺序排错
 *  一位编译照样过。
 *
 * @param launched 有启动图标的应用（`ACTION_MAIN` + `CATEGORY_LAUNCHER`）
 * @param xposedList Xposed 模块（按模块设置入口那一类查到的）
 * @param xposedPkgs 上面那批的包名 —— 行上那个「模块」标签按它判
 * @param known `launched + xposedList` 的包名 —— 「其余应用」要排除的那一份
 * @param launchable 点得开的包名（一次问全机，见 `AppEntry.launchablePackages`）
 */
internal class TierSources(
    val launched: List<ApplicationInfo>,
    val xposedList: List<ApplicationInfo>,
    val xposedPkgs: Set<String>,
    val known: Set<String>,
    val launchable: Set<String>,
)

/**
 * **默认档** —— 两个显示开关都关着时页面上会出现的那一批：有启动图标的 + Xposed 模块 + 分身。
 *
 *  这一批算完就该**立刻交出去**（见 `loadLaunchableApps` 的 `onDefaultTier`）：关着「显示系统
 *  应用」时，它就是页面上要显示的全部 —— 用户等的时间从「全量」缩到「一档」，剩下那几百个
 *  没有图标的应用在它之后才算。
 *
 *  分身行**并进主清单再统一排序**：上一版是 `apps + clones`（分身一律吊在末尾），于是
 *  「微信 1」跑到「支付宝」后面去了；用户要的是按名字混排。
 *  并按 target 去重 —— 列表那边是 `items(key = { it.target })`，key 重复会让 Compose 抛
 *  「Key was already used」直接崩掉 `:ui` 进程（二级页在贴吧那一页真崩过一次，见 `loadActivities`）。
 *  主清单与分身理论上不会撞（分身带 `999@` 前缀），但合成两条来源之后补一道，比赌它不撞便宜。
 */
internal fun defaultTier(
    context: Context,
    pm: PackageManager,
    sources: TierSources,
    romanizer: Transliterator?,
    collator: java.text.Collator?,
): List<LaunchableApp> {
    // 默认档那批：有启动图标的 + Xposed 模块。`byDefault` 按包名现判、与拼接顺序无关。
    val apps =
        (sources.launched + sources.xposedList)
            .distinctBy { it.packageName }
            .map { app ->
                val pkg = app.packageName
                val facts =
                    RowFacts(
                        xposed = pkg in sources.xposedPkgs,
                        byDefault = pkg in sources.known,
                        launchable = pkg in sources.launchable,
                    )
                appRow(pm, app, facts, romanizer)
            }
    // —— 分身：同一个应用在**别的用户**里的那一份 ——
    //
    //  分身是另一个 Android 用户，所以在主用户的清单里**根本看不到**：换用户查包、槽号、
    //  名字都由 `AppCloneCatalog` 那边给（它认识 `UserManager` / `IPackageManager` 那些
    //  私有面，见那个类的注释）。这一层只拿到「包名 + 名字 + 槽号」，剩下的与普通应用一样。
    val clones = cloneRows(context, romanizer)
    XLog.w("可启动应用：分身 ${clones.size} 行")
    //  排序尺子与「其余应用」那批同一把（`appOrder` = 拼音键 → 原名 → 包名）
    return (apps + clones).distinctBy { it.target }.sortedWith(appOrder(collator))
}
