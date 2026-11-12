package com.lookie.opluskey.ui.screen.apppicker

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.icu.text.Transliterator
import android.os.SystemClock
import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.target.LaunchableActivity

// 「选择应用」页第二级：一个应用**能打开哪些界面**。
//
// 与 `AppList.kt` 的 `loadLaunchableApps` 是同一件事的两级：那一层回答「本机有哪些应用」，
// 这一层回答「这个应用里哪个界面」。分开成两个文件而不是塞进 `AppList.kt`：那一层的清单是
// 200+ 应用、每个包 1~3 次 binder；这一层的清单是**一个包**、一次整包 parcel（声明了多少个活动
// 就有多少个 `ActivityInfo`），成本形态完全不同，混在一起会把两种预算的注释搅成一团。
//
// 查询的附注（[LoadNotes]）在 `LoadNotes.kt`：这一份装的是「怎么查」，它是「查完带回来的那张纸」。

/**
 * 一个应用里**能从模块这边拉起来**的界面，按名字排序（与应用那一级同一把尺子）。
 *
 * 目标写法带分身前缀（`999@包名/类名`）时，查询也走**那个用户的 `PackageManager`** ——
 * 主用户看不见分身里的包（见 [AppCloneCatalog]），拿主用户的 PM 查分身只会得到空清单。
 *
 * @param target 应用那一行的目标写法（`包名` 或 `999@包名`）—— 只取包名与用户两段，
 *   里面若带了 `/类名` 也不影响（[AppTarget.pkg] 自己会截断）。
 * @param notes 可选附注（见 [LoadNotes]）—— 页面用它区分「快捷方式没查到」与「没有快捷方式」。
 * @return 查到的清单；**查失败返回 null**（不是空清单）。空清单是一份有效结论
 *   （「这个应用确实没有能外部打开的界面」），拿它去覆盖缓存会把上一次的好清单抹掉。
 */
internal suspend fun SettingsPage.loadActivities(
    target: String,
    notes: LoadNotes? = null,
): List<LaunchableActivity>? =
    //  整段包一层：兄弟路径 `loadLaunchableApps` 就是这么做的（返回 null 让页面保留上一次的
    //  清单）。裸着的话，任何一个叶子之外的 binder / 解析异常都会顺 `withContext(IO)` 抛出去，
    //  落进宿主 `:ui` 进程就是「进这一页就崩」。
    try {
        loadActivitiesUnguarded(target, notes)
    } catch (e: Throwable) {
        XLog.w("查界面清单失败（忽略，页面保留上一次的清单）：$target", e)
        null
    }

/** [loadActivities] 的本体 —— 不包异常，`null` / 空清单的语义见那边。 */
private suspend fun SettingsPage.loadActivitiesUnguarded(
    target: String,
    notes: LoadNotes?,
): List<LaunchableActivity>? {
    val t0 = SystemClock.uptimeMillis()
    val pkg = AppTarget.pkg(target)
    val userId = AppTarget.userId(target)
    //  主用户走 `PackageManager`；分身**只能走 binder** —— `Context#createContextAsUser(999)`
    //  在真机上必抛 `Own package not found for user 999`（宿主的包不在那个用户里），
    //  而 `queryIntentActivitiesAsUser` 又被这个 ROM 砍了（见 `AppClonePackages`）。
    //  拿不到就如实报「没读出来」，**不退回主用户**：那会列出一堆分身根本没有的组件。
    val pm: PackageManager? = if (userId == 0) context.packageManager else null
    val declared =
        packageInfoOf(pkg, userId, pm) ?: run {
            XLog.w("查界面清单：读不出 $pkg（user=$userId）的 PackageInfo")
            return null
        }
    // 纯资源包声明得了组件、类却不在包里 ⇒ 一个都起不来（`AppEntry.resolve` 那条路上踩过）
    if (declared.applicationInfo?.flags?.and(ApplicationInfo.FLAG_HAS_CODE) == 0) {
        XLog.w("界面清单：$pkg 没有代码（纯资源包），一个都不列")
        return emptyList()
    }
    val all = declared.activities ?: emptyArray<ActivityInfo>()
    // 三道筛子，缺一条都会变成「列表里点得动、按下去打不开」：
    //  · `exported` —— 没导出的组件别的应用起不来；
    //  · `permission` —— 本模块**零权限**（清单里一条都没声明），带权限的活动一起就 SecurityException；
    //  · 运行时被 `setComponentEnabledSetting` 关掉的 —— `getPackageInfo` 照样把它报出来。
    val candidates = all.filter { it.exported && it.permission == null && it.enabled }
    //  分身拿不到 `PackageManager`，问不了「运行时被关掉」这一档：按启用处理
    //  （`ActivityInfo.enabled` 已经在上一条里筛过，那一位是清单里声明的默认值）。
    val alive = if (pm != null) candidates.filter { enabledNow(pm, pkg, it.name) } else candidates
    // 别名与它指向的活动会同时出现在清单里（`activity-alias` 的 `targetActivity`），
    // 只留别名那一条：别名才是带图标 / 标签、且被声明成导出的那一个。
    val visible = dropShadowed(alive)
    val query =
        ActivityQuery(
            pm = pm,
            pkg = pkg,
            userId = userId,
            romanizer = romanizerOrNull(),
            collator = chinaCollator(),
        )
    val activities =
        visible
            .map { entryOf(query, it) }
            //  排序与应用那一级**同一把尺子**（[nameOrder]）：拼音键 → 原名 → 类名。
            //  不按「启动图标 / 带 MAIN / 其余」分档 —— 那一档把同一个应用里的界面切成三段，
            //  每段各自按名字排，整体读起来像没排过（用户口径「界面排序和应用用一样的」）。
            .sortedWith(nameOrder({ it.sortKey }, { it.label }, { it.name }, query.collator))
    // 快捷方式（长按桌面图标那几个）另起一节 —— 它们不是界面，名字也由应用自己给（多半是中文）。
    //  `null` = 这一节没查到（第三种状态，见 [LoadNotes]）：页面据此**不把**已绑的
    //  `appshortcut:` 降级成 `app:`（见 `resolveSelection`）。
    //
    //  这一趟里还夹着一次跨进程往返（桌面钉住的那几条只有注入侧读得到，见 `ShortcutPull`），
    //  所以整段比「只查界面」慢几十到几百毫秒 —— 页面那边在 `loaded == null` 期间铺整页转圈。
    //  `notes` 要一路传进去：注入侧那趟快捷方式**没回包**时它也会把 `shortcutsFailed` 置上
    //  （那一趟的清单不全，已绑的 `appshortcut:` 不能被当成「已经没了」）。
    val shortcuts = shortcutsOf(pkg, userId, query.romanizer, notes)
    if (shortcuts == null) notes?.shortcutsFailed = true
    XLog.w(
        "界面清单：$pkg（user=$userId）声明 ${all.size} 个 → 导出 ${candidates.size} 个 → " +
            "运行时可用 ${visible.size} 个，快捷方式 ${shortcuts?.size ?: 0} 个" +
            (if (shortcuts == null) "（这一节没查到）" else "") +
            "，耗时 ${SystemClock.uptimeMillis() - t0}ms",
    )
    // **两节各自去重**（不再跨节 `distinctBy { target }`）：快捷方式的 target 与界面的 target
    //  写法相同（`包名/…`），撞上时跨节去重会**丢掉快捷方式那一行** —— 用户绑的 `appshortcut:`
    //  随即在列表里找不到，被静默降级成 `app:`。列表本身按「节前缀 + target」取键（见
    //  `ActivityPickerList`），两节同名不会再撞 Compose 的 key。
    val merged = activities.distinctBy { it.target } + shortcuts.orEmpty().distinctBy { it.target }
    val clash = merged.groupBy { it.target }.filterValues { it.size > 1 }.keys
    if (clash.isNotEmpty()) {
        XLog.w("界面清单：${clash.size} 个目标同时出现在快捷方式与界面两节（已都保留）：${clash.joinToString()}")
    }
    return merged
}

/**
 * 这个组件**现在**是不是启用的 —— 问不到就按启用处理。
 *
 *  `ActivityInfo.enabled` 只反映清单里写的那个值；被 `setComponentEnabledSetting` 关掉之后
 *  它不会变，`getPackageInfo` 也照旧把它报出来。所以要另问一次。
 *
 *  代价是一次 binder：只对**已经通过前两道筛子**的那些问（真机上微信 39 个、设置 282 个），
 *  不对整份清单（微信 2099 个）问。
 */
private fun enabledNow(
    pm: PackageManager,
    pkg: String,
    name: String,
): Boolean =
    try {
        when (pm.getComponentEnabledSetting(ComponentName(pkg, name))) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false

            else -> true
        }
    } catch (e: Throwable) {
        // 问不出来不等于不能用 —— 不能因为一次查询失败就把用户想选的那一项藏掉
        XLog.d("问组件启用状态失败：$pkg/$name（${e.javaClass.simpleName}）")
        true
    }

/** 去掉「被某个别名指向」的活动，只留别名那一条（理由见调用点）。 */
private fun dropShadowed(list: List<ActivityInfo>): List<ActivityInfo> {
    val aliasTargets = list.mapNotNullTo(HashSet()) { it.targetActivity }
    return list.filter { it.targetActivity != null || it.name !in aliasTargets }
}

/**
 * 一次查询的固定上下文 —— 交给 [entryOf] 的那几样「整份清单共用」的东西。
 *
 *  单独拎出来是因为它会让 `entryOf` 变成七个参数的函数：参数一多，调用点按顺序排错一位
 *  编译照样过。
 */
private class ActivityQuery(
    /** 主用户那份 `PackageManager`；分身是 `null`（那一路只能走 binder，见 `AppClonePackages`）。 */
    val pm: PackageManager?,
    val pkg: String,
    val userId: Int,
    val romanizer: Transliterator?,
    /** 名字相同时的兜底比较器（与应用那一级同一份，见 `chinaCollator`）；取不到就只按键比。 */
    val collator: java.text.Collator?,
)

/**
 * 一条 `ActivityInfo` → 界面要的那几样。
 *
 * ## 名字从哪来（这是这一页唯一「取不全」的东西）
 *
 *  1. 活动自己声明了 `android:label`（`labelRes != 0`）⇒ 用它，多半是应用自己写的中文名
 *     （「分享到朋友圈」这类）；
 *  2. 没声明 ⇒ **包里就没有名字**：`loadLabel` 会回落成**应用名**（一列几十行全叫「微信」），
 *     比类名还没用。这种只能拿类名当标题，取的是**最后一段**（`ShareToTimeLineUI`），
 *     小字留完整的去包名路径（`ui.tools.ShareToTimeLineUI`）—— 两行一样的话等于白占一行。
 *
 *  不是「没去取」，是应用没给：系统自己的分享面板在这种活动上也只能显示应用名。
 */
private fun SettingsPage.entryOf(
    query: ActivityQuery,
    ai: ActivityInfo,
): LaunchableActivity {
    val key = "${query.pkg}/${ai.name}"
    // 去包名的那一段 —— 小字用它，同一个应用里两个同名类靠它区分
    val path = if (ai.name.startsWith("${query.pkg}.")) ai.name.removePrefix("${query.pkg}.") else ai.name
    // 类名最后一段 —— 应用没给名字时当标题
    val simple = ai.name.substringAfterLast('.')
    val declared =
        if (ai.labelRes != 0) {
            //  分身没有 `PackageManager`：这一档取不到名字，退回类名最后一段（见上面第 2 条）
            query.pm?.let { pm ->
                try {
                    ai.loadLabel(pm).toString()
                } catch (e: Throwable) {
                    XLog.d("取界面名失败：${ai.name}（${e.javaClass.simpleName}）")
                    null
                }
            }
        } else {
            null
        }
    val label = declared ?: simple
    return LaunchableActivity(
        target = AppTarget.format(key, query.userId),
        name = ai.name,
        label = label,
        summary = path,
        // 与 `loadLaunchableApps` 同一套排序键：拼音能搜到汉字名
        sortKey = sortKeyOf(label, query.romanizer),
    )
}

/**
 * 汉字 → 拼音的转写器；取不到返回 null（排序退回原名），不影响清单本身。
 *
 *  **不私有**：「选择应用」与「选择界面」两页的清单用的是同一把尺子 ——
 *  两页各取一次 `Transliterator.getInstance` 也能跑，但「取不到怎么办」那一句就会写两遍。
 */
internal fun romanizerOrNull(): Transliterator? =
    try {
        Transliterator.getInstance("Han-Latin")
    } catch (e: Throwable) {
        XLog.w("取不到 Han-Latin 转写器，界面清单退回原名排序：${e.javaClass.simpleName}")
        null
    }
