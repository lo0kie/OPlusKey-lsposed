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
import com.lookie.opluskey.core.model.resolveAppName
import com.lookie.opluskey.core.model.resolveTargetName
import com.lookie.opluskey.core.present.appTargetLabel
import com.lookie.opluskey.ui.model.cloneLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 一个绑定目标该显示的名字 —— 网格小字 / 顶栏「已选择」/ 步骤名都走它。
 *
 *  **顺序不在这里**：[resolveAppName] 是唯一声明处（现查系统 → 落盘那份 → 老 `|名字` → 包名）。
 *  这里只回答两件事：现查怎么查（[appLabelOrNull]，带缓存）、落盘那份在哪儿（`Config.appNames`）。
 *
 *  这一条原先自己排过一遍顺序，而且与宿主侧**相反**（配置名在 `PackageManager` 前面）——
 *  同一个应用因此在界面上叫旧名、在提示里叫新名。两端理由各自都成立，但不可能同时成立，
 *  所以顺序收到 core 一处，两侧只负责「这一档取到什么」。
 *
 *  第 1 档补分身槽号：主用户的微信与分身微信包名相同，只查名字两行都叫「微信」。
 *  槽号查不到（那一份已经删了）时挂 user id（`微信 · 998`），见 [cloneLabel]。
 *
 *  @param target 绑定的目标写法（`999@包名/类名`、`999@包名/快捷方式id` 或纯包名）
 */
internal fun SettingsPage.appNameOf(target: String): String {
    val userId = AppTarget.userId(target)
    return resolveAppName(
        target = target,
        fromSystem = { pkg ->
            //  分身：主用户与分身的包名相同，只查名字两行都叫「微信」—— 补一个槽号区分
            appLabelOrNull(pkg)?.let { cloneLabel(it, AppCloneCatalog.slotOf(context, userId), userId) }
        },
        fromSaved = { id -> configState.config.value.appNames[id] },
    )
}

/**
 * 「打开应用」那一族的**完整目标**写法 —— **应用名[ - 小窗][ - 组件名 / 快捷方式名]**（`微信 - 扫一扫`）。
 *
 *  三处共用这一句：网格里「应用控制」格点开那层菜单的两行子标题、格子自己的小字、
 *  以及「选择应用」页顶栏那行小字。以前三处各拼一半：网格只写应用名、菜单写
 *  `微信: 扫一扫`（冒号）、页头靠老写法的 `|名字` —— 于是同一个绑定在三处长成三个样子，
 *  而且**重新进页时页头只剩应用名**（新配置的名字在 `Config.appNames`，不在 token 后缀里）。
 *
 * ## 「具体是哪一个」从哪儿来
 *
 *  顺序在 [resolveTargetName]（草稿 → 落盘那份 → 老 `|名字`），这里只回答「落盘那份在哪儿」。
 *  取不到时**组件退回类名最后一段**（`...LauncherUI` → `LauncherUI`）；快捷方式没有这一档
 *  （`包名/id` 里的 id 是给系统看的，摆到界面上没有意义）。
 *
 *  应用名一律**只按包名**取：`包名/快捷方式id` 那个身份在 `appNames` 里存的是快捷方式名，
 *  拿它当应用名会写成「扫一扫 - 扫一扫」。
 *
 * @param shortcut 这一趟绑的是不是快捷方式（只有它才「没有组件」这一说）
 * @param window 是不是以小窗那一档 —— 目标里标「小窗」，否则同一个应用的两条绑定分不出来
 * @param draftLabel 这一趟刚挑中的名字（没挑过给 `null`）
 * @param stockAnim 是不是「使用原版动画」那一档（`app:a:…`）—— 与 [window] 互斥，同一个理由标出来
 */
internal fun SettingsPage.appTargetLabelOf(
    target: String,
    shortcut: Boolean,
    window: Boolean,
    draftLabel: String?,
    stockAnim: Boolean = false,
): String {
    val app = appNameOf(AppTarget.format(AppTarget.pkg(target), AppTarget.userId(target)))
    val own = resolveTargetName(target, draftLabel) { id -> configState.config.value.appNames[id] }
    val detail =
        own?.takeIf { it != app } ?: if (shortcut) null else AppTarget.activity(target)?.substringAfterLast('.')
    return appTargetLabel(app, window, detail, stockAnim)
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
        stockAnim = (action as? Action.App)?.stockAnim == true,
    )

/**
 * 包名 → 应用名缓存（进程级，活到 `:ui` 进程结束）。
 *
 *  为什么要有：步骤行标题与网格小字每次重组都要算，名称框里每敲一个字也重组一遍。
 *  **只缓存查成功的** —— 把失败写进去（值 = 包名）会让一次暂时的查失败永久固化成包名。
 *
 *  **必须是并发安全的**：写它的两个入口不在同一个线程上 —— 组合期（主线程，[appLabelOf]）与
 *  `Dispatchers.IO`（[boundAppName] 那一侧）。普通 `HashMap` 并发 put 在扩容时会丢条目，
 *  极端情况下结构损坏导致死循环。
 */
internal val appLabelCache: ConcurrentHashMap<String, String> = ConcurrentHashMap()

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

internal fun SettingsPage.appLabelOf(packageName: String): String = appLabelOrNull(packageName) ?: packageName

/**
 * 同 [appLabelOf]，但**查不到给 `null`** —— 名字优先级链需要这个区分。
 *
 *  「查不到」与「名字恰好就是包名」是两件事：前者该让 [resolveAppName] 继续往下走
 *  （落盘那份 / 老后缀），后者不该。合成一个「查不到返回包名」的话，下面三档永远走不到 ——
 *  应用卸载之后界面上就只剩包名，而配置里明明记着它从前叫什么。
 */
private fun SettingsPage.appLabelOrNull(packageName: String): String? {
    appLabelCache[packageName]?.let { return it }
    val label =
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Throwable) {
            XLog.d("取应用名失败：$packageName（${e.javaClass.simpleName}）")
            // 失败不写缓存，下次再查一次
            return null
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
                        // 原版动画那一档同理（`app:a:…`）—— 两者互斥，最多一个为真
                        stockAnim = (current as? Action.App)?.stockAnim == true,
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
