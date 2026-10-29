package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

/**
 * **「一个动作怎么念」** —— 全项目唯一实现。
 *
 *  三个地方要说这句话：执行时提示（`host/action/ActionNotice`，宿主进程）、自定义命令的步骤行
 *  （`ui/model/CommandText.stepTitle`，界面进程）、以及导出 / 清空配置时的确认文案。它们必须是
 *  **同一句话** —— 界面上写着「打开应用:微信」而按键后提示「微信」，用户会以为执行的是另一条。
 *
 *  原先这两处各写一份同构的 `when`（6 个分支逐个对齐），靠注释互相提醒「与对方逐档一致」。
 *  那等于用记性当编译器：加一档动作时漏改一处，两边就分叉，而且只在按键那一刻才看得见。
 *
 *  ## 为什么参数里带一个 [appName] 回调
 *
 *  「打开应用」那三档要的是应用名，而**只有调用方知道从哪儿查**：宿主进程现查 `PackageManager`
 *  （见 `host/action/ActionNotice` 里的 `appLabel`），界面进程先查这次清单、再查配置里记的名字
 *  （见 `ui/AppNaming.appNameOf`）。所以这一层只管「句子怎么拼」，名字从哪来交给调用方。
 *
 *  回调的入参是**整个 [Action]** 而不是目标字符串：三档应用动作的取名优先级本来就不同
 *  （快捷方式可以先查「落盘时记下的名字」再退 PM，`app:` 则直接问 PM），把目标拍平成字符串
 *  就没法表达这个差别 —— 那会悄悄改掉提示里的名字来源。
 *
 *  ## 为什么 `app:` / `window:` / `appshortcut:` 不能被 `else` 接住
 *
 *  那三档走的是**带 `%s` 的格式键**；清单里那条说明文案的值里没有格式符，拿去 `format` 等于
 *  把应用名丢掉。所以它们必须在 `when` 里显式分出来。
 *
 * @param appName 只在上面那三档被调用；其余分支不读它
 * @param fallback 认不出这一档时原样显示什么 —— **由调用方给**：界面用配置里那串原始 token
 *  （手改过的文件要看得见原始写法），宿主用规范化之后的 token。两者只在「认不出来」时才不同。
 */
fun actionTitle(
    action: Action,
    appName: (Action) -> String,
    fallback: String,
): String =
    when (action) {
        // 「无操作」不是「执行了某条命令」：提示里给空串，调用点据此不弹
        is Action.None -> {
            ""
        }

        // 自定义命令取命令名 —— 界面（「已保存的命令」卡片）也是这么叫的
        is Action.Custom -> {
            action.name
        }

        is Action.App -> {
            I18n.format(Strings.COMMON_OPEN_APP_FORMAT, appName(action))
        }

        is Action.AppWindow -> {
            I18n.format(Strings.COMMON_OPEN_MINI_WINDOW_FORMAT, appName(action))
        }

        // 快捷方式是「打开它的某个快捷方式」（「扫一扫」），与上面两档说的不是一回事，不能合并
        is Action.AppShortcut -> {
            I18n.format(Strings.COMMON_OPEN_SHORTCUT_FORMAT, appName(action))
        }

        // 自定义变量（`setvar:计数+=1`）：四档各一句话。
        //  值是配置里的原文（里面可能还写着 `${其它变量}`），照原样显示 —— 展开是执行时的事。
        is Action.VarSet -> {
            varSetTitle(action)
        }

        // 系统开关：切换 / 打开 / 关闭三档各一句话 —— 名字在清单里，认不出就退回 id
        is Action.Setting -> {
            I18n.format(
                when (action.on) {
                    true -> Strings.COMMON_TURN_ON_SETTING_FORMAT
                    false -> Strings.COMMON_TURN_OFF_SETTING_FORMAT
                    null -> Strings.COMMON_TOGGLE_SETTING_FORMAT
                },
                SystemSwitches.titleOfId(action.id) ?: action.id,
            )
        }

        // 其余内置动作：档位名 → 清单标题 → 认不出来。
        //  这一步读的是 [ActionCatalog.label]，与界面那一格的名字同源。
        else -> {
            ActionCatalog.label(action) ?: fallback
        }
    }

/**
 * 一步变量操作在界面上的说法 —— 四档各一句，值是配置里的原文（可能还写着 `${其它变量}`）。
 *
 *  单独一支只为把 [actionTitle] 的圈复杂度压回 detekt 的阈值之下（那一支本来就贴着上限，
 *  再并进来一条就报 `CyclomaticComplexMethod`）。
 */
private fun varSetTitle(action: Action.VarSet): String =
    I18n.format(
        when (action.op) {
            VarOp.SET -> Strings.VAR_STEP_SET
            VarOp.ADD -> Strings.VAR_STEP_ADD
            VarOp.SUB -> Strings.VAR_STEP_SUB
            VarOp.NOT -> Strings.VAR_STEP_NOT
        },
        action.name,
        action.value,
    )
