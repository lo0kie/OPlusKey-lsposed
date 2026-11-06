package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.appTargetLabel
import com.lookie.opluskey.ui.model.cloneLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * 「打开应用」那一族的**完整目标**写法 —— **应用名[ - 小窗][ - 组件名 / 快捷方式名]**（`微信 - 扫一扫`）。
 *
 *  三处共用这一句：网格里「应用控制」格点开那层菜单的两行子标题、格子自己的小字、
 *  以及「选择应用」页顶栏那行小字。以前三处各拼一半：网格只写应用名、菜单写
 *  `微信: 扫一扫`（冒号）、页头靠老写法的 `|名字` —— 于是同一个绑定在三处长成三个样子，
 *  而且**重新进页时页头只剩应用名**（新配置的名字在 `Config.appNames`，不在 token 后缀里）。
 *
 * ## 「具体是哪一个」从哪儿来（顺序只有这一处）
 *
 *  1. [draftLabel]：这一趟刚挑中、还没落盘的那一行自己的名字（页头专用，挑完立刻要看得见）；
 *  2. `Config.appNames`：落盘时与绑定同一次写下的名字 —— 快捷方式存的是**快捷方式名**
 *     （`扫一扫`），组件存的是挑那一行时的标题；
 *  3. 老配置里塞在 token 末尾的 `|名字`（只读，见 [AppTarget] 的类注释）；
 *  4. 组件**退回类名最后一段**（`...LauncherUI` → `LauncherUI`）；快捷方式没有这一档
 *     （`包名/id` 里的 id 是给系统看的，摆到界面上没有意义）。
 *
 *  应用名一律**只按包名**取：`包名/快捷方式id` 那个身份在 `appNames` 里存的是快捷方式名，
 *  拿它当应用名会写成「扫一扫 - 扫一扫」。
 *
 * @param shortcut 这一趟绑的是不是快捷方式（只有它才「没有组件」这一说）
 * @param window 是不是以小窗那一档 —— 目标里标「小窗」，否则同一个应用的两条绑定分不出来
 * @param draftLabel 这一趟刚挑中的名字（没挑过给 `null`）
 */
internal fun SettingsPage.appTargetLabelOf(
    target: String,
    shortcut: Boolean,
    window: Boolean,
    draftLabel: String?,
): String {
    val app = appNameOf(AppTarget.format(AppTarget.pkg(target), AppTarget.userId(target)))
    val own =
        draftLabel?.takeIf { it.isNotBlank() }
            ?: configState.config.value.appName(target)
            ?: AppTarget.label(target)
    val detail =
        own?.takeIf { it != app } ?: if (shortcut) null else AppTarget.activity(target)?.substringAfterLast('.')
    return appTargetLabel(app, window, detail)
}

/**
 * 同一个动作的**完整目标**（应用名 + 小窗 / 组件 / 快捷方式），给「手上有 [Action]、但拿不到
 * 草稿」的调用点用（系统场景那一格）。
 *
 *  与 [appTargetLabelOf] 同一个拼法，只是把「从动作到目标」这一小段收进来：调用点不必再各写
 *  一遍「哪个字段是目标、哪一个算小窗」的判据（写两遍就会有一处漏掉小窗，真机就多出一层括号）。
 */
internal fun SettingsPage.appTargetLabelOf(action: Action): String =
    appTargetLabelOf(
        target = action.appTarget ?: (action as? Action.Process)?.pkg.orEmpty(),
        shortcut = action is Action.AppShortcut,
        window = action is Action.AppWindow || (action as? Action.AppShortcut)?.window == true,
        draftLabel = null,
    )

/**
 * 包名 → 应用名（取不到就退回包名本身）。
 *
 * 走宿主的 `PackageManager`：包可见性按宿主算 —— 绑过的应用本来就在[界面那份可见清单]里，
 * 正常查得到。
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

/**
 * 「当前绑的是哪一个应用」—— 两处以**两种详细度**用同一份（判据只有这里）：
 *
 *  · **网格里那一格的小字 / 抽屉里那一行的小字** → `withDetail = false`：**只写应用名**
 *    （`微信`）。那一行只有一格宽，`微信 - 扫一扫` 会被省略号吃掉（用户口径「格子子标题只写
 *    应用名就行」）；
 *  · **点开那层菜单里「打开应用 / 结束应用」两行的子标题** → `withDetail = true`：
 *    **应用名 - 组件名 / 快捷方式名**（`微信 - 扫一扫`）。菜单行宽，这两行也只能靠这句说清
 *    绑的是哪个界面的哪一个（行标题已经写了「打开应用」）。
 *
 *  **结束应用**（`proc:stop:`）只有应用名：它只存包名，没有界面 / 快捷方式那一层。
 *  其余动作（不是应用那一族的）：`null`，调用方那边就没有这一行小字。
 *
 *  名字查一次就缓存（入参是动作 + 详细度，所以只在换动作 / 名字表变了时重查）。
 *
 *  **查询放 IO**：`appNameOf` 走 `PackageManager`（分身那档还要问 `UserManager`），
 *  在组合期做就是每次进页几十次 binder。这里先给 `null`、查到了再填 —— 小字晚一帧出现，
 *  比首帧卡在 binder 上强。
 *
 * @param withDetail 要不要带「具体是哪一个」（见上：格子不带、菜单带）
 */
@Composable
internal fun SettingsPage.boundAppName(
    current: Action,
    withDetail: Boolean,
): String? {
    val appNames = configState.config.value.appNames
    var label by remember(current, appNames, withDetail) { mutableStateOf<String?>(null) }
    LaunchedEffect(current, appNames, withDetail) {
        // 应用那一族之外（含「无操作」）没有这一行小字
        val target =
            when (current) {
                is Action.App, is Action.AppWindow -> current.appTarget
                is Action.AppShortcut -> current.plainTarget
                is Action.Process -> current.pkg
                else -> null
            } ?: return@LaunchedEffect
        label =
            withContext(Dispatchers.IO) {
                if (withDetail) {
                    appTargetLabelOf(
                        target,
                        shortcut = current is Action.AppShortcut,
                        // 以小窗那一档标出来：`window:` 与「小窗快捷方式」两种
                        window = current is Action.AppWindow || (current is Action.AppShortcut && current.window),
                        draftLabel = null,
                    )
                } else {
                    //  只写**应用名**：快捷方式的身份（`包名/id`）在 `appNames` 里存的是快捷方式名，
                    //  直接拿它当应用名会写成「扫一扫」，所以这里只按包名取。
                    appNameOf(AppTarget.format(AppTarget.pkg(target), AppTarget.userId(target)))
                }
            }
    }
    return label
}
