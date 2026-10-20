package com.lookie.opluskey.host.action

import android.content.Context
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.actionTitle

/**
 * 「执行时提示」（`GlobalConfig.toastOnRun`）里那行字：**这次执行的是哪个动作**。
 *
 *  名字与界面上那一格同一来源（[ActionCatalog] 是**纯数据** —— 只有取值 / 标题 / 宿主 drawable 名，
 * 不引 androidx，宿主进程取它不会把界面那一层拉进来），免得出现「界面上叫小布识屏、提示里换个说法」。
 * 认不出来的取值（手改过配置文件）原样显示 —— 与界面 `ui/model/CommandText.stepTitle` 同一条兜底。
 */
internal fun actionNoticeText(
    context: Context?,
    action: Action,
    /**
     * 「打开应用」那一族的显示名（`Config.appNames`，键是 `AppTarget.identity`）——
     * 快捷方式的「扫一扫」这类名字**只在配置里**，token 里不再带（见 `AppTarget` 的类注释）。
     * 应用名不靠它：那一条现查 `PackageManager` 更准。
     */
    appNames: Map<String, String> = emptyMap(),
): String =
    actionTitle(
        action = action,
        // 名字从哪儿来是**宿主侧**的事（界面那边查的是清单与配置）：`app:` / `window:` 现查 PM；
        //  快捷方式先看落盘时记下的名字（`appNames`），再退回**老配置 token 里那一段 `|名字`**、
        //  最后退应用名 / 包名 —— 与界面 `ui/appNameOf` 的优先级逐档对齐（少这一档时，老配置里
        //  绑的快捷方式在提示里显示成应用名，而界面上显示的是快捷方式名）。
        appName = { a ->
            when (a) {
                is Action.App -> {
                    appLabel(context, a.target)
                }

                is Action.AppWindow -> {
                    appLabel(context, a.target)
                }

                is Action.AppShortcut -> {
                    val target = a.appTarget.orEmpty()
                    appNames[AppTarget.identity(target)] ?: AppTarget.label(target) ?: appLabel(context, target)
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
 * 目标 → 应用名；取不到原样回包名。
 *
 *  包名走 [AppTarget.pkg] 而不是 `substringBefore('/')`：分身的写法是 `999@包名/Activity`，
 *  直接切会剩下 `999@包名`，`getApplicationInfo` 必然查不到、提示里就成了包名。
 */
private fun appLabel(
    context: Context?,
    target: String,
): String {
    val pkg = AppTarget.pkg(target)
    val ctx = context ?: return pkg
    return try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Throwable) {
        XLog.d("执行时提示：取应用名失败（$pkg）${e.javaClass.simpleName}")
        pkg
    }
}
