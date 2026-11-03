package com.lookie.opluskey.ui.screen.apppicker

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.lookie.opluskey.bridge.AppClonePackages
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage

// 「界面清单」的**取数来源**：同一个应用，主用户走 `PackageManager`，分身走 binder
// （`IPackageManager`，带 userId）。与 `ActivityList.kt` 分开是因为那边管「怎么筛、怎么排」，
// 这里只管「从哪拿」—— 两件事的变化理由不同：ROM 改 API 影响这里，产品规则影响那边。
//
// 为什么分身只能走 binder：`Context#createContextAsUser(999)` 真机上必抛
// `Own package not found for user 999`（宿主的包不在那个用户里），而
// `PackageManager#queryIntentActivitiesAsUser` 被这个 ROM 砍了。见 `AppClonePackages` 的类注释。

/**
 * 取这个包的 `PackageInfo`。
 *
 * @param pm 主用户的 `PackageManager`；分身传 `null`（那一路只能走 binder）
 * @return 读不到返回 `null`（调用方据此报「没读出来」，**不退回主用户**）
 */
internal fun SettingsPage.packageInfoOf(
    pkg: String,
    userId: Int,
    pm: PackageManager?,
): PackageInfo? =
    if (pm != null) {
        try {
            pm.getPackageInfo(
                pkg,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()),
            )
        } catch (e: Throwable) {
            XLog.w("查界面清单失败：$pkg（user=$userId）", e)
            null
        }
    } else {
        AppClonePackages.packageInfoAsUser(pkg, PackageManager.GET_ACTIVITIES.toLong(), userId)
    }
