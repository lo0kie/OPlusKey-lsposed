package com.lookie.opluskey.ui.screen.apppicker

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.icu.text.Transliterator
import android.os.SystemClock
import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.LaunchableActivity

// 「选择应用」页第二级：一个应用**能打开哪些界面**。
//
// 与 `AppList.kt` 的 `loadLaunchableApps` 是同一件事的两级：那一层回答「本机有哪些应用」，
// 这一层回答「这个应用里哪个界面」。分开成两个文件而不是塞进 `AppList.kt`：那一层的清单是
// 200+ 应用、每个包 1~3 次 binder；这一层的清单是**一个包**、一次整包 parcel（声明了多少个活动
// 就有多少个 `ActivityInfo`），成本形态完全不同，混在一起会把两种预算的注释搅成一团。

/** 排位：带 `MAIN` + `LAUNCHER`（桌面图标那一个）—— 排最前。 */
internal const val RANK_LAUNCHER = 0

/** 排位：只带 `MAIN`（电视版入口这类）—— 排在其余之前。 */
internal const val RANK_MAIN = 1

/**
 * 一次查询的**附注** —— 目前只有一件：快捷方式那一节是不是没查出来。
 *
 *  为什么不并进返回值：清单本身是 `List<LaunchableActivity>?`（null = 整次失败），
 *  而「界面查到了、快捷方式没查到」是第三种状态。编码成哨兵行或改返回类型，会让每个
 *  只关心清单的调用点都跟着改；而它唯一的用处是「别把 `appshortcut:` 降级成 `app:`」
 *  （见 [resolveSelection]）。
 */
internal class LoadNotes {
    var shortcutsFailed = false
}

/**
 * 一个应用里**能从模块这边拉起来**的界面，按「启动图标 → 带 MAIN → 其余」再按名字排序。
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
internal fun SettingsPage.loadActivities(
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
private fun SettingsPage.loadActivitiesUnguarded(
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
            ranks = if (pm != null) launcherRanks(pm, pkg) else launcherRanksAsUser(pkg, userId),
            romanizer = romanizerOrNull(),
        )
    val activities =
        visible
            .map { entryOf(query, it) }
            .sortedWith(
                compareBy<LaunchableActivity>(
                    { !it.launcher },
                    { !it.mainAction },
                    { it.sortKey },
                    { it.name },
                ),
            )
    // 快捷方式（长按桌面图标那几个）另起一节 —— 它们不是界面，名字也由应用自己给（多半是中文）。
    //  `null` = 这一节没查到（第三种状态，见 [LoadNotes]）：页面据此**不把**已绑的
    //  `appshortcut:` 降级成 `app:`（见 `resolveSelection`）。
    val shortcuts = shortcutsOf(pkg, userId, query.romanizer)
    if (shortcuts == null) notes?.shortcutsFailed = true
    XLog.w(
        "界面清单：$pkg（user=$userId）声明 ${all.size} 个 → 导出 ${candidates.size} 个 → " +
            "运行时可用 ${visible.size} 个，快捷方式 ${shortcuts?.size ?: 0} 个" +
            (if (shortcuts == null) "（这一节没查到）" else "") +
            "，耗时 ${SystemClock.uptimeMillis() - t0}ms",
    )
    // **按 target 去重**：列表那边是 `items(key = { it.target })`，key 重复会让 Compose 当场抛
    // 「Key was already used」并崩掉整个 `:ui` 进程（真机在贴吧那一页崩过一次：同一个
    // `AuthorizeSignActivity` 在清单里出现了两条）。`getPackageInfo` 返回的 `activities`
    // 本身可能带重复条目（同一个类被声明 / 合并成多条），这里按**我们自己的目标写法**收一次，
    // 顺带也保证用户看不到两条一模一样的界面。
    return (activities + shortcuts.orEmpty()).distinctBy { it.target }
}

/**
 * 这个应用的**快捷方式** —— 走 `LauncherApps.getShortcuts`（不是 `ShortcutManager`：那个只看得见
 * **自己这个包**的快捷方式）。
 *
 *  权限：系统侧那道闸是 `hasShortcutHostPermission`，非桌面应用要靠 `ACCESS_SHORTCUTS`
 *  （宿主声明并已 grant，真机实测过）。拿不到就**这一节一条不列**并记一行 `w` —— 不是「这个应用没有」，
 *  是「我们读不到」，两件事别混。
 *
 *  @return 查不到（应用一个都没注册）返回**空清单**（有效结论）；读不到返回 **`null`**
 *    —— 后者要能区分出来，否则页面会把「这次没查到」当成「那一项已经没了」，
 *    把用户绑的 `appshortcut:` 静默降级成 `app:`。
 */
private fun SettingsPage.shortcutsOf(
    pkg: String,
    userId: Int,
    romanizer: Transliterator?,
): List<LaunchableActivity>? {
    val launcherApps =
        try {
            context.getSystemService(android.content.pm.LauncherApps::class.java)
        } catch (e: Throwable) {
            XLog.w("取 LauncherApps 失败", e)
            null
        } ?: return null
    val query =
        android.content.pm.LauncherApps
            .ShortcutQuery()
            .setPackage(pkg)
            .setQueryFlags(
                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
    val shortcuts =
        try {
            launcherApps.getShortcuts(query, AppCloneCatalog.handleOf(userId)).orEmpty()
        } catch (e: Throwable) {
            XLog.w("查快捷方式失败：$pkg（user=$userId，宿主要持有 ACCESS_SHORTCUTS）", e)
            return null
        }
    return shortcuts
        .map { info ->
            // 本 SDK 里 `getId()` 非空，直接取（旧版本标的是可空，真为空的快捷方式本来也起不来）
            val id = info.id
            val label =
                info.shortLabel.toString().takeIf { it.isNotBlank() }
                    ?: info.longLabel.toString().takeIf { it.isNotBlank() }
                    ?: id
            LaunchableActivity(
                // 与界面那几行同一套目标写法：`包名/id|名字`。`/` 后面是 id 不是类名，
                // 靠 `shortcutId` 区分，写回时走 `appshortcut:` 那一档。
                target = AppTarget.format("$pkg/$id", userId),
                name = "$pkg/$id",
                label = label,
                // 小字放 id：快捷方式之间就靠它区分（名字可能重）
                summary = id,
                launcher = false,
                mainAction = false,
                sortKey = sortKeyOf(label, romanizer),
                shortcutId = id,
            )
        }.sortedBy { it.sortKey }
}

/**
 * 「启动图标 → 带 MAIN → 其余」三档排位：两个意图各查一次，命中的组件记进同一张表。
 *
 * 先写 `MAIN`、后写 `LAUNCHER` —— 后者覆盖前者，带启动图标的那一档自然排最前。
 * 单独一支是为了让 [loadActivities] 别顶破 detekt 的长度上限（那一支本来也够长）。
 */
private fun launcherRanks(
    pm: PackageManager,
    pkg: String,
): Map<String, Int> {
    val ranks = HashMap<String, Int>()
    for (component in componentsOf(pm, Intent(Intent.ACTION_MAIN).setPackage(pkg))) {
        ranks[component] = RANK_MAIN
    }
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
    for (component in componentsOf(pm, launcherIntent)) {
        ranks[component] = RANK_LAUNCHER
    }
    return ranks
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

/** 某个意图在这台机器上能命中的组件集合（`包名/类名`）—— 查失败当作一个都没有。 */
private fun componentsOf(
    pm: PackageManager,
    intent: Intent,
): Set<String> =
    try {
        pm
            .queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            .mapNotNullTo(HashSet()) { ri ->
                ri.activityInfo?.let { "${it.packageName}/${it.name}" }
            }
    } catch (e: Throwable) {
        XLog.d("查意图命中的组件失败（${intent.action}）：${e.javaClass.simpleName}")
        emptySet()
    }

/**
 * 一次查询的固定上下文 —— 交给 [entryOf] 的那几样「整份清单共用」的东西。
 *
 *  单独拎出来是因为它会让 `entryOf` 变成七个参数的函数（detekt 的 `LongParameterList`
 *  默认上限是 6）：参数一多，调用点按顺序排错一位编译照样过。
 */
private class ActivityQuery(
    /** 主用户那份 `PackageManager`；分身是 `null`（那一路只能走 binder，见 `AppClonePackages`）。 */
    val pm: PackageManager?,
    val pkg: String,
    val userId: Int,
    /** 组件 → 排位（[RANK_LAUNCHER] / [RANK_MAIN]）；不在这张表里的排最后。 */
    val ranks: Map<String, Int>,
    val romanizer: Transliterator?,
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
    val rank = query.ranks[key]
    return LaunchableActivity(
        target = AppTarget.format(key, query.userId),
        name = ai.name,
        label = label,
        summary = path,
        launcher = rank == RANK_LAUNCHER,
        // 两个意图都是 `MAIN` 查出来的 ⇒ 在这张表里就等于「带 MAIN」
        mainAction = rank != null,
        // 与 `loadLaunchableApps` 同一套排序键：拼音能搜到汉字名
        sortKey = sortKeyOf(label, query.romanizer),
    )
}

/** 汉字 → 拼音的转写器；取不到返回 null（排序退回原名），不影响清单本身。 */
private fun romanizerOrNull(): Transliterator? =
    try {
        Transliterator.getInstance("Han-Latin")
    } catch (e: Throwable) {
        XLog.w("取不到 Han-Latin 转写器，界面清单退回原名排序：${e.javaClass.simpleName}")
        null
    }
