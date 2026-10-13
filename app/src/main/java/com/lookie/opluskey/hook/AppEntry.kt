package com.lookie.opluskey.hook

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 把一个包名解析成「能打开的入口」。
 *
 * `pm.getLaunchIntentForPackage` 只认 `ACTION_MAIN` + `CATEGORY_LAUNCHER`，而**把桌面图标藏起来
 * 的应用**往往正是把那个带 `LAUNCHER` 的分量（常是个 `activity-alias`）`disabled` 掉 —— 于是它
 * 判成「没有入口」，可包里另有一个带 `MAIN` 的界面是好的，**显式组件**起得起来
 * （`com.ss.android.ugc.aweme.yyds` 即此：`…awemes.Home` 带 LAUNCHER 且被禁用，
 * `…awemes.MainActivity` 带 MODULE_SETTINGS 且可用）。
 *
 * 所以按「启动图标 → TV 图标 → 任意 `MAIN` 界面」逐层退，最后一律用显式组件起 —— 显式起不受
 * `intent-filter` 匹配限制，只要求组件导出。
 *
 * 选择列表的「不可打开」标记与 `app:` 动作共用本函数：两边判据必须一致，否则会出现
 * 「列表里点得动、按下去报找不到」。
 */
internal object AppEntry {

    /** 名字里带这些词的界面算更像入口，排前面。 */
    private val NAME_HINTS = listOf("main", "launcher", "home", "entry", "welcome", "splash", "start")

    /** 解析不出入口就返回 `null` —— 调用方据此标「不可打开」。 */
    fun resolve(pm: PackageManager, pkg: String): Intent? {
        pm.getLaunchIntentForPackage(pkg)?.let { return it }
        // TV 图标单列一条路，普通 ROM 上恒为 null
        runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) }.getOrNull()?.let { return it }
        val activity = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).setPackage(pkg), 0)
        }.getOrNull()
            ?.mapNotNull { it.activityInfo }
            ?.filter { it.packageName == pkg && it.exported }
            ?.minWithOrNull(compareBy({ hintRank(it.name) }, { it.name }))
            ?: return null
        return Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, activity.name))
    }

    /** 名字里第一个命中的提示词的下标；一个都没命中排最后。 */
    private fun hintRank(name: String): Int {
        val lower = name.lowercase()
        val hit = NAME_HINTS.indexOfFirst { lower.contains(it) }
        return if (hit < 0) NAME_HINTS.size else hit
    }
}
