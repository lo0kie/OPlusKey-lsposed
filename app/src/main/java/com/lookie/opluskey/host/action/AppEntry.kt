package com.lookie.opluskey.host.action

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
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

    /**
     * 一个**目标写法**（`app:` / `window:` 动作里那一段）解析成可打开的 Intent；解析不出返回 `null`。
     *
     * 两种写法：
     *  · `包名/类名` —— 直接组显式组件；类名以 `.` 开头（或压根没点）时补上包名前缀；
     *  · 只有包名 —— 走 [resolve] 那套「启动图标 → TV 图标 → 任意 `MAIN` 界面」逐层退。
     *
     *  与 [resolve] 放在一处是因为它们是同一件事的两级：`resolve` 管「包名 → 入口」，
     *  这里管「用户写的那串 → 入口」。原来这一层写在 `AppLauncher` 里，于是要用它的两个动作族
     *  （普通打开与小窗打开）都得挂在同一个类上。
     */
    fun intentOf(
        pm: PackageManager,
        target: String,
    ): Intent? =
        if (target.contains('/')) {
            val pkg = target.substringBefore('/')
            val raw = target.substringAfter('/')
            // 短横线写法（`app:pkg/.MainActivity`）按 `ComponentName.unflattenFromString` 的口径
            //  拼：点开头就是「类名相对包名」，直接接在包名后面 —— 写成 `"$pkg.$raw"` 会拼出
            //  `pkg..MainActivity`（两个点），那个类永远不存在，表现为「应用起不来」。
            val cls =
                if (raw.startsWith(".")) {
                    pkg + raw
                } else if ('.' !in raw) {
                    "$pkg.$raw"
                } else {
                    raw
                }
            Intent().setClassName(pkg, cls)
        } else {
            resolve(pm, target)
        }

    /** 解析不出入口就返回 `null` —— 调用方据此标「不可打开」。 */
    fun resolve(
        pm: PackageManager,
        pkg: String,
    ): Intent? {
        // 注意：**没有代码的包（`android:hasCode="false"`）直接判不可打开**：这类纯资源包声明得了组件、
        //  却一个都起不来（类根本不在包里）。真机上确实踩到 —— OPPO 的 `oplus`
        // （`/system_ext/framework/oplus-framework-res.apk`，**标签也是「Android 系统」**）里有个
        //  `TileLongClickActivity`（`MAIN` + `QS_TILE_PREFERENCES`、`exported`），第三层会把它当成
        //  「这个应用的入口」⇒ 列表里显示成「可以打开」（连「隐藏失效应用」都滤不掉）、动作点下去必失败
        // （用户报「Android 系统打不开但是没有被过滤」就是这个）。读不到 ApplicationInfo 时
        //  也按不可打开处理：连它是什么都问不出来，起它更没戏。
        val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
        if (app.flags and ApplicationInfo.FLAG_HAS_CODE == 0) return null
        // 三层都要包住：这个函数跑在「选择应用」页的整表循环里，一个包查不动不该把
        //  整份查询带崩（第一层裸着的话，抛出去整表返回 null）。
        runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()?.let { return it }
        // TV 图标单列一条路，普通 ROM 上恒为 null
        runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) }.getOrNull()?.let { return it }
        val activity =
            runCatching {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).setPackage(pkg), 0)
            }.getOrNull()
                ?.mapNotNull { it.activityInfo }
                //  这一层只认**真的给用户当入口用**的活动：
                //  · 导出是前提；
                //  · `permission` 非空的不算 —— 那种活动要调用方持有某个（多半是 signature 级的）权限，
                //    而本模块**零权限**，起它就是 `SecurityException`；
                //  · `android:excludeFromRecents` 的不算 —— 那是悬浮面板宿主 / 磁贴设置页这类**辅助活动**的
                //    写法（「不进最近任务」正说明它不是「打开这个应用」的入口）。
                //  后两条都是真机踩出来的（症状同为「列表里显示可以打开、按下去打不开」）：
                //  `oplus` 的 `TileLongClickActivity`（QS 磁贴长按设置页，带 `BIND_QUICK_SETTINGS_TILE`）、
                //  `com.coloros.accessibilityassistant`（「AI语音摘记」）的 `floatpanel.MainActivity`
                //  （悬浮面板宿主：`excludeFromRecents` + `resizeableActivity=false`）。
                ?.filter {
                    it.packageName == pkg && it.exported && it.permission == null &&
                        it.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS == 0
                }?.minWithOrNull(compareBy({ hintRank(it.name) }, { it.name }))
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
