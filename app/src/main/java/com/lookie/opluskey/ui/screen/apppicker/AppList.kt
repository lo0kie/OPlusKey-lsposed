package com.lookie.opluskey.ui.screen.apppicker

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ModuleInfo
import com.lookie.opluskey.host.action.AppEntry
import com.lookie.opluskey.host.action.MiniWindowSupport
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.LaunchableApp

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
        // 里不落地，翻 APK 的 `META-INF/xposed/` 太贵）。没声明该 category 的模块认不出来，行不带「模块」标签。
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
        val xposedPkgs = xposedList.mapTo(HashSet()) { it.packageName }
        // 「显示系统应用」开着时再追加的一批 —— 既不在上一批、也不是模块的其余应用（主体是没有
        // launcher 图标的系统应用）。不筛「拉不拉得起来」：能拿到全量包列表靠宿主自己声明了
        // `QUERY_ALL_PACKAGES` —— 界面代码跑在宿主进程，包可见性按宿主算。
        val others =
            if (!includeSystem) {
                emptyList()
            } else {
                val known = (launched + xposedList).mapTo(HashSet()) { it.packageName }
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
        val apps =
            ((launched + xposedList).distinctBy { it.packageName } + others)
                .map { entry(it, it.packageName in xposedPkgs) }
                // 排序键在 `entry` 里就算好了（罗马化不便宜）：这里只比，不重算
                .sortedWith { a, b ->
                    val byKey = a.sortKey.compareTo(b.sortKey)
                    if (byKey != 0) {
                        byKey
                    } else {
                        val byName = collator?.compare(a.label, b.label) ?: 0
                        if (byName != 0) byName else a.packageName.compareTo(b.packageName)
                    }
                }.toList()
        // 打头几条 + 耗时便于核对（转写器是否生效、这次查了多久）
        XLog.w(
            "可启动应用：${apps.size} 个（显示系统应用=$includeSystem，模块 ${xposedList.size} 个、" +
                "额外追加 ${others.size} 个），" +
                "耗时 ${android.os.SystemClock.uptimeMillis() - t0}ms，" +
                "排序（前 10）：" + apps.take(10).joinToString(" / ") { it.label },
        )
        apps
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

/**
 * 被「不支持以小窗口打开」拦下的那一个 —— 列表侧的临时状态，所以与清单那几条放在同一文件。
 *
 * @param fromPick 这次是从**点确认键**触发的那一道（三处查询里的最后一道）。它决定「确认」要不要
 *   顺手写下去：从确认键来的按 `app:<包名>` 存（用户最初要的「取消勾选保存」），
 *   从前两处（切应用 / 点菜单）来的只把开关拨回关 —— 那时还没有「这一次的选择」可存。
 */
internal data class SmallWindowBlock(
    val app: LaunchableApp,
    val fromPick: Boolean,
)
