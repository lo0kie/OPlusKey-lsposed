package com.lookie.opluskey.ui

import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.model.cloneLabel

/**
 * 一个绑定目标该显示的名字 —— **全项目唯一一条优先级**，网格小字 / 顶栏「已选择」/ 步骤名都走它。
 *
 *  1. 配置里**单独存着**的那份（`Config.appNames`，按 `AppTarget.identity` 索引）——
 *     冷启动直接进页、清单还没查回来时，只有它有名字；
 *  2. 老配置里塞在 token 末尾的 `|名字` —— 那一批是旧写法落下的（见 `AppTarget` 的类注释），
 *     只读不写，用户重新挑一次就迁到第 1 档；
 *  3. `PackageManager` 现查：`appLabelOf` 只认包名，所以先按 [AppTarget.pkg] 剥掉用户前缀与
 *     `/组件`（以前直接 `substringBefore('/')`，分身会带着 `999@` 去查、必然查不到）；
 *  4. 包名兜底（手写配置 / 包被卸载）。
 *
 *  第 3 档还要补分身槽号：主用户的微信与分身微信包名相同，只查名字两行都叫「微信」。
 *  槽号查不到（那一份已经删了）时挂 user id（`微信 · 998`），见 [cloneLabel]。
 *
 *  @param target 绑定的目标写法（`999@包名/类名`、`999@包名/快捷方式id` 或纯包名）
 */
internal fun SettingsPage.appNameOf(target: String): String {
    configState.config.value
        .appName(target)
        ?.let { return it }
    AppTarget.label(target)?.let { return it }
    val base = appLabelOf(AppTarget.pkg(target))
    val userId = AppTarget.userId(target)
    return cloneLabel(base, AppCloneCatalog.slotOf(context, userId), userId)
}

/**
 * 包名 → 应用名（取不到就退回包名本身）。
 *
 *  走宿主的 `PackageManager`：包可见性按宿主算 —— 绑过的应用本来就在[界面那份可见清单]里，
 *  正常查得到。
 *
 *  带一层缓存：步骤行标题与网格小字每次重组都要算，名称框里每敲一个字也重组一遍。缓存是
 *  **进程级**顶层 `val`（活到 `:ui` 进程结束），而且**只缓存查成功的** —— 把失败写进去
 *  （值 = 包名）会让一次暂时的查失败永久固化成包名。
 */
internal val appLabelCache = HashMap<String, String>()

/** 缓存上限 —— 只是省 binder 的一层；不设上限会跟着滚动一路长下去。 */
private const val APP_LABEL_CACHE_MAX = 512

/**
 * 清掉应用名缓存 —— **重进模块时调用**（见 `SettingsPage.onHostReentered`）。
 *
 *  这张表是进程级、没有别的失效路径：应用在这期间改名 / 卸载 / 换图标包，缓存里留着的
 *  就是旧名字（真机上表现为「列表里还是旧名，点进去又是新名」）。
 */
internal fun clearAppLabelCache() {
    appLabelCache.clear()
}

internal fun SettingsPage.appLabelOf(packageName: String): String {
    appLabelCache[packageName]?.let { return it }
    val label =
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Throwable) {
            XLog.d("取应用名失败：$packageName（${e.javaClass.simpleName}）")
            // 失败不写缓存，下次再查一次
            return packageName
        }
    if (appLabelCache.size >= APP_LABEL_CACHE_MAX) {
        // 整表清而不是逐条淘汰：这里省的是 binder，清一次最多让下一屏重新查一遍
        appLabelCache.clear()
    }
    appLabelCache[packageName] = label
    return label
}
