package com.lookie.opluskey.host.action

import android.content.Context
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.resolveAppName
import com.lookie.opluskey.core.model.resolveTargetName
import com.lookie.opluskey.core.present.appTargetLabel
import com.lookie.opluskey.core.present.commandTitleOf

/**
 * 「执行时提示」（`GlobalConfig.toastOnRun`）里那行字：**这次执行的是哪个动作**。
 *
 *  名字与界面上那一格同一来源（[ActionCatalog] 是**纯数据** —— 只有取值 / 标题 / 宿主 drawable 名，
 * 不引 androidx，宿主进程取它不会把界面那一层拉进来），免得出现「界面上叫小布识屏、提示里换个说法」。
 * 认不出来的取值（手改过配置文件）原样显示 —— 与界面 `ui/model/CommandText.stepTitle` 同一条兜底。
 *
 *  用的是 [commandTitleOf] —— 全项目唯一那一份「一个动作怎么念」：入口格带父名
 *  （`系统开关: WIFI - 打开`），与步骤行、命令卡摘要、系统场景那一格说的是同一句。
 *  以前这条路上另有一份 `actionFinalTitle`，同一个动作因此在提示里和步骤行里两个样子。
 *
 *  **「无操作」在这一层不弹**（判在 `GestureExec.noticeIfNeeded`，不在这里）：这个函数只管
 *  把句子念出来，`None` 念出来就是「无操作」四个字。该不该弹是提示这个位置的规矩。
 */
internal fun actionNoticeText(
    context: Context?,
    action: Action,
    /**
     * 「打开应用」那一族的显示名（`Config.appNames`，键是 `AppTarget.identity`）——
     * 快捷方式的「扫一扫」这类名字**只在配置里**，token 里不再带（见 `AppTarget` 的类注释）。
     * 它是名字优先级链的第二档（[resolveAppName]），不是第一档：现查 `PackageManager` 更准，
     * 只有应用已经卸载时这一份才有用。
     */
    appNames: Map<String, String> = emptyMap(),
): String =
    commandTitleOf(action).forSentence(
        action = action,
        // 顺序不在这里：`resolveAppName` / `resolveTargetName` 是唯一声明处（现查系统 →
        //  落盘那份 → 老 `|名字` → 包名）。这里只回答「宿主这一侧那两档拿什么」——
        //  与界面 `ui/AppNaming` 同一份顺序，两处否则会像以前那样各自排一遍、还排反了。
        appName = { a ->
            when (a) {
                //  整个应用 / 某个界面：`微信` / `微信(LauncherUI)`。界面那一截由
                //  「具体是哪一个」补上（与界面那边 `appTargetLabelOf` 同一个拼法）。
                is Action.App -> {
                    appTargetLabel(
                        hostAppName(context, a.target, appNames),
                        window = false,
                        AppTarget.activity(a.target)?.substringAfterLast('.'),
                        stockAnim = a.stockAnim,
                    )
                }

                //  以小窗打开：动词只有「打开应用」一个词，小窗是目标的注记（`微信(小窗)`）
                is Action.AppWindow -> {
                    appTargetLabel(
                        hostAppName(context, a.target, appNames),
                        window = true,
                        AppTarget.activity(a.target)?.substringAfterLast('.'),
                    )
                }

                //  快捷方式：应用名 + 它自己的名字（`微信(扫一扫)`）。
                //  应用名**只按包名取** —— `包名/快捷方式id` 那个身份在名字表里存的是
                //  **快捷方式名**，拿它当应用名会写成「扫一扫（扫一扫）」。
                is Action.AppShortcut -> {
                    val target = a.appTarget.orEmpty()
                    val app = hostAppName(context, AppTarget.format(AppTarget.pkg(target), AppTarget.userId(target)), appNames)
                    val own = resolveTargetName(target, draft = null) { id -> appNames[id] }
                    appTargetLabel(app, window = a.window, own)
                }

                //  结束应用（`proc:stop:`）：目标直接就是包名（没有 `/界面`、也没有分身前缀）
                is Action.Process -> {
                    hostAppName(context, a.pkg, appNames)
                }

                else -> {
                    ""
                }
            }
        },
        // 认不出来时用**规范化后的** token（界面那边用配置里那串原始写法）
        fallback = action.token,
    )

/**
 * 宿主侧那两个名字来源 —— 顺序由 [resolveAppName] 定，这里只回答「这一档取到什么」。
 *
 *  与界面那边（`ui/AppNaming.appNameOf`）**同一份顺序**：现查系统 → 落盘那份 → 老 `|名字` →
 *  包名。区别只在实现：那边有名字缓存、还要补分身槽号；这边一次提示一个名字，不缓存。
 */
private fun hostAppName(
    context: Context?,
    target: String,
    appNames: Map<String, String>,
): String =
    resolveAppName(
        target = target,
        fromSystem = { pkg -> pmLabel(context, pkg) },
        fromSaved = { id -> appNames[id] },
    )

/**
 * 包名 → 应用名；**查不到给 `null`**。
 *
 *  「查不到」与「名字恰好是包名」是两件事：前者要让 [resolveAppName] 继续往下走（落盘那份 /
 * 老后缀），后者不该。合成一个「查不到返回包名」的话，应用卸载之后提示里就只剩包名 ——
 * 而配置里明明记着它从前叫什么。
 *
 *  包名走 [AppTarget.pkg] 而不是 `substringBefore('/')`：分身的写法是 `999@包名/Activity`，
 *  直接切会剩下 `999@包名`，`getApplicationInfo` 必然查不到、提示里就成了包名。
 */
private fun pmLabel(
    context: Context?,
    pkg: String,
): String? {
    val ctx = context ?: return null
    return try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Throwable) {
        XLog.d("执行时提示：取应用名失败（$pkg）${e.javaClass.simpleName}")
        null
    }
}
