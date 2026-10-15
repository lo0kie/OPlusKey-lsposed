package com.lookie.opluskey.host.action

import android.content.Context
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.ui.model.ActionCatalog

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
): String =
    when (action) {
        // 「无操作」不是「执行了某条命令」，调用点已排除，这里给空串兜底
        is Action.None -> ""

        // 自定义命令取命令名 —— 界面（「已保存的命令」卡片）也是这么叫的
        is Action.Custom -> action.name

        // 这两格是**入口**，清单只给到「打开应用」这个组名，带上应用名才说得清是哪一条
        is Action.App -> I18n.format(Strings.COMMON_OPEN_APP_FORMAT, appLabel(context, action.target))

        is Action.AppWindow -> I18n.format(Strings.COMMON_OPEN_MINI_WINDOW_FORMAT, appLabel(context, action.target))

        else -> ActionCatalog.specOf(action)?.title ?: action.token
    }

/** 包名 → 应用名；取不到原样回包名（`app:` 的目标可能写成 `包名/Activity`，只取前半段）。 */
private fun appLabel(
    context: Context?,
    target: String,
): String {
    val pkg = target.substringBefore('/')
    val ctx = context ?: return pkg
    return try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Throwable) {
        XLog.d("执行时提示：取应用名失败（$pkg）${e.javaClass.simpleName}")
        pkg
    }
}
