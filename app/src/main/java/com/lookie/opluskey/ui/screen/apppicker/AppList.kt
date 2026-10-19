package com.lookie.opluskey.ui.screen.apppicker

import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.bridge.AppEntry
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.LaunchableApp
import com.lookie.opluskey.ui.model.cloneLabel

/**
 * 本机可启动的应用，按名字排序。
 *
 * 默认只有有启动图标的那些（`ACTION_MAIN` + `CATEGORY_LAUNCHER`），`includeSystem` 为 true
 * 时再追加一批系统应用。
 *
 * @param includeSystem 顶栏菜单的「显示系统应用」
 * @return 查到的清单；**查失败返回 null**（不是空清单）—— 空清单是一份有效结论
 *   （「这台机器上确实一个可启动的应用都没有」），拿它去覆盖缓存会把上一次的好清单抹掉，
 *   界面显示成「没有找到可以打开的应用」（见 [AppPickerScreen] 的调用点）。
 */
internal fun SettingsPage.loadLaunchableApps(includeSystem: Boolean = false): List<LaunchableApp>? {
    val t0 = android.os.SystemClock.uptimeMillis()
    val pm = context.packageManager
    val intent =
        android.content
            .Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    // 汉字 → 拼音的转写器（ICU 自带，`icudt*.dat` 里有 `Han-Latin`）
    val romanizer =
        try {
            android.icu.text.Transliterator
                .getInstance("Han-Latin")
        } catch (e: Throwable) {
            XLog.w("取不到 Han-Latin 转写器，应用列表退回原名排序：${e.javaClass.simpleName}")
            null
        }
    // 兜底比较器（同键时比原名）；取不到就只按键比，仍稳定
    val collator =
        try {
            java.text.Collator.getInstance(java.util.Locale.CHINA)
        } catch (e: Throwable) {
            null
        }
    return try {
        // 一条 `ApplicationInfo` → 界面要的那几样，两条来源共用；`xposed` 由调用点给。
        fun entry(
            ai: android.content.pm.ApplicationInfo,
            xposed: Boolean,
        ): LaunchableApp {
            // 入口只解一次：`AppEntry.resolve` 自己就要 1~3 次 binder，解两遍纯属浪费
            // （这一页会有 200+ 行，几百次 binder 已经在预算里）
            val intent = AppEntry.resolve(pm, ai.packageName)
            val label =
                try {
                    ai.loadLabel(pm).toString()
                } catch (e: Throwable) {
                    ai.packageName
                }
            return LaunchableApp(
                packageName = ai.packageName,
                label = label,
                system = ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
                launchable = intent != null,
                // 注意：这里**不再**顺手问「支不支持小窗」：那个问法自带一条系统提示（见 [MiniWindowSupport]），
                //    整表问一遍就是「进页弹一串 toast」。改到点确认键那一刻只问一次。
                xposed = xposed,
                // 排序键在这里算一次（罗马化不便宜）：排序与搜索共用它，见 [LaunchableApp.sortKey]
                sortKey = sortKeyOf(label, romanizer),
            )
        }
        // 有启动图标的应用
        val launched =
            pm
                .queryIntentActivities(intent, 0)
                .asSequence()
                .mapNotNull { it.activityInfo?.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != ModuleInfo.PACKAGE }
                .toList()
        // Xposed 模块：默认就列，不看开关 —— 模块往往没有 launcher 图标、不在上一批里，却是最常挑的。
        // 判据是模块自带的入口 category `MODULE_SETTINGS`（`xposedminversion` 那类 metaData 在 dumpsys
        // 里不落地，翻 APK 的 `META-INF/xposed/` 太贵）。
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
        // —— 再加一条判据：**新式 libxposed 模块**——
        //
        //  这一类**不声明** `MODULE_SETTINGS`，而是在清单里挂一个名叫
        //  `io.github.libxposed.service.XposedProvider` 的 provider（签名里那个
        //  `io.github.libxposed.service` 包就是它的身份）。真机上 `com.wizpizz.reddidnt` 2.0.0
        //  即此：`query-activities -c …MODULE_SETTINGS` 查不到它，于是列表里一行「模块」标签都没有。
        //
        //  为什么不逐包翻 APK 里的 `META-INF/xposed/module.prop`：那是这个类注释一开始就否掉的贵路子
        //  （200+ 次开 zip）。而 `queryContentProviders(null, 0, 0)` 是**一次** binder 调用拿回全机
        //  provider，筛个名字就够了 —— 与上面那次 `queryIntentActivities` 同量级。
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
        val xposedPkgs =
            (xposedList.map { it.packageName } + libXposedPkgs).toCollection(HashSet())
        if (libXposedPkgs.isNotEmpty()) {
            XLog.w("新式 libxposed 模块：${libXposedPkgs.joinToString()}")
        }
        // 「显示系统应用」开着时再追加的一批 —— 既不在上一批、也不是模块的其余应用（主体是没有
        // launcher 图标的系统应用）。不筛「拉不拉得起来」：能拿到全量包列表靠宿主自己声明了
        // `QUERY_ALL_PACKAGES` —— 界面代码跑在宿主进程，包可见性按宿主算。
        val others =
            if (!includeSystem) {
                emptyList()
            } else {
                val known =
                    (launched + xposedList + libXposedList).mapTo(HashSet()) { it.packageName }
                pm
                    .getInstalledApplications(0)
                    .asSequence()
                    .filter { it.packageName !in known }
                    .filter { it.packageName != ModuleInfo.PACKAGE }
                    .toList()
            }
        // 两条来源必须跨来源去重：Xposed 模块的规范写法是「一个 launcher activity + 一个挂
        // MODULE_SETTINGS 的 activity-alias」，同一个包在 launcher 与模块两批里各出现一次。
        // 不去重 ⇒ 列表两行同包名 ⇒ `items(key = ...)` 抛「Key ... was already used」，异常落在
        // 宿主 Activity 的组合里 ⇒ 打开「选择应用」页即崩宿主页面（`others` 已按 known 滤过）。
        // 排序键在 `entry` 里就算好了（罗马化不便宜）：这里只比，不重算
        // 抽成变量是因为**分身也要按同一把尺子排**（见函数末尾的 `all`）。
        val order =
            Comparator<LaunchableApp> { a, b ->
                val byKey = a.sortKey.compareTo(b.sortKey)
                if (byKey != 0) {
                    byKey
                } else {
                    val byName = collator?.compare(a.label, b.label) ?: 0
                    if (byName != 0) byName else a.packageName.compareTo(b.packageName)
                }
            }
        val apps =
            ((launched + xposedList + libXposedList).distinctBy { it.packageName } + others)
                .map { entry(it, it.packageName in xposedPkgs) }
        // —— 分身：同一个应用在**别的用户**里的那一份 ——
        //
        //  分身是另一个 Android 用户，所以在主用户的清单里**根本看不到**：换用户查包、槽号、
        //  名字都由 `AppCloneCatalog` 那边给（它认识 `UserManager` / `IPackageManager` 那些
        //  私有面，见那个类的注释）。这一层只拿到「包名 + 名字 + 槽号」，剩下的与普通应用一样。
        val clones =
            AppCloneCatalog.entries(context).map { entry ->
                val label = cloneLabel(entry.label, entry.slot, entry.userId)
                //  「能不能打开」对分身也要真判一次（主清单那一行本来就判了）：
                //   写死 `true` 会让分身永远不被标「不可打开」——「隐藏失效应用」与确认键的闸
                //   都判不到它（五、3 不伪造能力）。
                //   判不出来（取不到 per-user 的 PM）时仍按「能打开」算：分身与主用户同包名，
                //   拿主用户的 PM 问必抛，一律判 false 会把好端端的分身从列表里滤掉。
                val clonePm = AppCloneCatalog.packageManagerFor(context, entry.userId)
                LaunchableApp(
                    packageName = entry.packageName,
                    label = label,
                    system = entry.system,
                    launchable = clonePm?.let { AppEntry.resolve(it, entry.packageName) != null } ?: true,
                    xposed = false,
                    sortKey = sortKeyOf(label, romanizer),
                    userId = entry.userId,
                )
            }
        // 分身行**并进主清单再统一排序** —— 上一版是 `apps + clones`（分身一律吊在末尾），
        //  于是「微信 1」跑到「支付宝」后面去了；用户要的是按名字混排。
        //  **按 target 去重**：列表那边是 `items(key = { it.target })`，key 重复会让 Compose
        //  抛「Key was already used」直接崩掉 `:ui` 进程（二级页在贴吧那一页真崩过一次，
        //  见 `loadActivities`）。主清单与分身理论上不会撞（分身带 `999@` 前缀），
        //  但合成两条来源之后补一道，比赌它不撞便宜。
        val all = (apps + clones).distinctBy { it.target }.sortedWith(order)
        XLog.w("可启动应用：分身 ${clones.size} 行")

        // 打头几条 + 耗时便于核对（转写器是否生效、这次查了多久）
        XLog.w(
            "可启动应用：${all.size} 个（显示系统应用=$includeSystem，模块 ${xposedList.size} 个、" +
                "额外追加 ${others.size} 个），" +
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
internal fun SettingsPage.sortKeyOf(
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
