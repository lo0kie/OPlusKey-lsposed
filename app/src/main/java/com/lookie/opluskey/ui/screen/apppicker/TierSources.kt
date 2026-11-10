package com.lookie.opluskey.ui.screen.apppicker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.icu.text.Transliterator
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.LaunchableApp

// 「选择应用」那一页清单的**默认档** —— 从 `AppList.kt` 分出来。
//
// 本文件两样东西：默认档要的那几批来源（[TierSources]）与拿它们组装出清单的 [defaultTier]。
// 为什么单独一个文件：`loadLaunchableApps` 那段贴着 detekt 的 `LongMethod` 上限（它要装
// 「两种取法合成一次取全」的那一整套理由），把默认档这一支留在里面只能靠压注释长度过关。
// 而这一支的取数预算与「其余应用」那批完全不同：这一批要全部算完才有意义（它就是要先上屏
// 的那一份），那一批几百个包各自 `loadLabel`，能拖就往后拖 —— 分开放更好说清这笔账。

/**
 * 一次查询的几批「已知来源」—— 交给 [defaultTier] 的那几样「整趟共用」的东西。
 *
 *  单独拎出来是因为它会让 `defaultTier` 变成九个参数的函数（detekt 的 `LongParameterList`
 *  默认上限是 6）：参数一多，调用点按顺序排错一位编译照样过。
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
