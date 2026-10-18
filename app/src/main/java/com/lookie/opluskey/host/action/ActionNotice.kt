package com.lookie.opluskey.host.action

import android.content.Context
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.AppTarget

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
    when (action) {
        // 「无操作」不是「执行了某条命令」，调用点已排除，这里给空串兜底
        is Action.None -> {
            ""
        }

        // 自定义命令取命令名 —— 界面（「已保存的命令」卡片）也是这么叫的
        is Action.Custom -> {
            action.name
        }

        // 这两格是**入口**，清单只给到「打开应用」这个组名，带上应用名才说得清是哪一条
        is Action.App -> {
            I18n.format(Strings.COMMON_OPEN_APP_FORMAT, appLabel(context, action.target))
        }

        is Action.AppWindow -> {
            I18n.format(Strings.COMMON_OPEN_MINI_WINDOW_FORMAT, appLabel(context, action.target))
        }

        // 快捷方式：显示它自己的名字（「扫一扫」，落盘时记在 `appNames` 里），
        //  没记过就退回**老配置 token 里那一段 `|名字`**、再退回应用名 / 包名 ——
        //  与界面 `ui/appNameOf` 的优先级逐档对齐（少这一档时，老配置里绑的快捷方式在
        //  提示里显示成应用名，而界面上显示的是快捷方式名）。
        is Action.AppShortcut -> {
            val target = action.appTarget.orEmpty()
            I18n.format(
                Strings.COMMON_OPEN_SHORTCUT_FORMAT,
                appNames[AppTarget.identity(target)] ?: AppTarget.label(target) ?: appLabel(context, target),
            )
        }

        // 与界面 `ui/model/CommandText.stepTitle` 共用同一份优先级（`ActionCatalog.label`）：
        //  档位名 → 清单标题 → 认不出来。兜底各写各的（这里用规范化后的 token）。
        else -> {
            ActionCatalog.label(action) ?: action.token
        }
    }

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
