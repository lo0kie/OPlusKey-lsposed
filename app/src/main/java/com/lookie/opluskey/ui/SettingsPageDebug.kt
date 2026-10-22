package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.DeviceSupport
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.ui.state.deviceModel
import com.lookie.opluskey.ui.state.deviceSupport
import com.lookie.opluskey.ui.state.fakeModel

// 调试通道（`debug` 广播）那两条 —— 与页面骨架无关，单独一个文件。

/**
 * 调试通道的处理体 —— **只有 debug 包会走到这里**（实现只在 `src/debug/` 源集）。
 *
 * 支持的动作见 [PageDebugChannel] 的类注释（`screen` / `gesture` / `kind` /
 * `name` / `cmd` / `steps` / `del` / `reset` / `save` / `hint` / `action` / `state` / `model` / `rom`，
 * 一次可以带多个）。`model` / `rom` 是**伪装本机身份**（只动判定与显示，不碰注入），
 * 传空串即恢复真机值。
 *
 * 执行顺序是**固定的**：先跳页、再切 tab、再填字段、最后才 reset / save —— 这样
 * 「`--es screen COMMANDS --es kind SERIAL --es name X --ez save true`」这一条广播
 * 就是一个完整的用例。认不出的取值只记日志、不抛异常。
 */
internal fun SettingsPage.onDebugCommand(intent: android.content.Intent) {
    intent.getStringExtra("screen")?.let { raw ->
        val target = Screen.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (target != null) {
            XLog.w("调试：跳页 → $target")
            //  直接跳「选择应用」时补上「为谁挑」：正常路径由格子点击 `openAppPickerForGesture`
            //  设置，少了它这一页的 FAB 会静默无效（`writeToken` 拿不到手势）。
            if (target == Screen.APP_PICKER) {
                picker.openForGesture(Gesture.entries.getOrElse(branchGesture) { Gesture.SINGLE })
            }
            // 走 [navTo]（不是「压一层」）：目标已经在栈里就直接回到它，否则压上去。
            // 于是 `--es screen COMMANDS` 从首页一步落到「自定义命令」页，
            // 上面那条「push 时拨分页状态」的 effect 照样会跑到（栈深变深了）。
            navTo(target)
        } else {
            XLog.w("调试：认不出的 screen=$raw（可用：${Screen.entries.joinToString()}）")
        }
    }
    intent.getStringExtra("gesture")?.let { raw ->
        val target = Gesture.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (target != null) {
            XLog.w("调试：手势 tab → $target")
            debugGesture.value = target
        } else {
            XLog.w("调试：认不出的 gesture=$raw（可用：${Gesture.entries.joinToString()}）")
        }
    }
    intent.getStringExtra("kind")?.let { raw ->
        val target = CommandKind.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (target != null) {
            XLog.w("调试：命令类型 → ${target.id}")
            commandDraft.draft.kind = target
        } else {
            XLog.w("调试：认不出的 kind=$raw（可用：${CommandKind.entries.joinToString()}）")
        }
    }
    // 字段一律走**与界面同一条清洗**（名称过 `sanitizeName`），否则填进去的值
    // 与手敲的不是一回事，验出来的结论就不作数
    intent.getStringExtra("name")?.let { commandDraft.draft.name = CommandLibrary.sanitizeName(it) }
    intent.getStringExtra("cmd")?.let { commandDraft.draft.single = it }
    intent.getStringExtra("steps")?.let { raw ->
        commandDraft.draft.steps.clear()
        commandDraft.draft.steps.addAll(raw.split(';').map { it.trim() }.filter { it.isNotEmpty() })
    }
    if (intent.getBooleanExtra("reset", false)) {
        XLog.w("调试：重置表单（等于点「重置 / 取消」）")
        commandDraft.draft.reset()
    }
    // 删一条命令 —— 给「填进去 → 保存 → 验完 → 清掉」这种自测收尾用，
    intent.getStringExtra("del")?.let { raw ->
        val name = CommandLibrary.sanitizeName(raw)
        if (configState.commands.value.none { it.name == name }) {
            XLog.w("调试：没有叫 '$name' 的命令，不用删")
        } else {
            XLog.w("调试：删除命令 '$name'")
            saveCommands(configState.commands.value.filterNot { it.name == name }, "调试删除：$name")
        }
    }
    if (intent.getBooleanExtra("save", false)) {
        XLog.w("调试：触发保存（等于点「保存 / 修改」）")
        addCommand()
    }
    // 弹一条操作提示 —— 排查提示条的排版 / 影子 / 进出场时用：
    //   am broadcast -a com.lookie.opluskey.DEBUG --es hint "已切换到「音量 -」"
    //  不这么做就得先点两下菜单才能触发一次，而这一段要看的是**动画**。
    intent.getStringExtra("hint")?.let { text ->
        XLog.w("调试：弹提示 → $text")
        hint(text)
    }
    // 通用动作派发：`--es action NAME` —— 页面级调试动作统一走这张表，加新动作只动这里
    intent.getStringExtra("action")?.let { raw ->
        when (raw.uppercase()) {
            "CONFIRM_RESET" -> {
                XLog.w("调试：action=CONFIRM_RESET → 弹出清空确认弹窗")
                overlays.confirmReset = true
            }

            else -> {
                XLog.w(
                    "调试：认不出的 action=$raw（可用：CONFIRM_RESET）",
                )
            }
        }
    }
    // 伪装机型 —— 只看「更多选项 → 适配清单」那一行（清单不参与任何闸，不换机也能验另外两档）：
    //   am broadcast -a com.lookie.opluskey.DEBUG --es model CPH9999
    //   am broadcast -a com.lookie.opluskey.DEBUG --es model ""        ← 恢复真机
    intent.getStringExtra("model")?.let { raw ->
        fakeModel = raw.trim().ifEmpty { null }
        XLog.w("调试：伪装机型 → ${fakeModel ?: "（恢复真机）"}；清单判定=$deviceSupport")
    }
    // 伪装 ROM 系列 —— **整条链路**都跟着变：适配清单那一行、内置命令落点（照基准实现试）、
    //  安装期那句「适配档位」。验「另一版 ROM 上那几条内置命令还跑不跑」用这个。
    //   am broadcast -a com.lookie.opluskey.DEBUG --es rom 16.0.10
    //   am broadcast -a com.lookie.opluskey.DEBUG --es rom ""            ← 恢复真机
    intent.getStringExtra("rom")?.let { raw ->
        DeviceSupport.romSeriesOverride = raw.trim().ifEmpty { null }
        XLog.w(
            "调试：伪装 ROM → ${DeviceSupport.currentRomSeries.ifEmpty { "（读不出来）" }}" +
                "；清单判定=${DeviceSupport.verdict}；内置命令档位=${RomProfile.describeCurrent()}",
        )
    }
    if (intent.getBooleanExtra("state", false)) logDebugState()
}

/**
 * 把当前状态打进日志（TAG `OplusKey`）—— 省掉「`uiautomator dump` 一遍再看」。
 *
 * 打的是**配置里的真值**（三个手势各绑了什么、主开关）与**表单草稿**，
 * 验「保存后到底写进去没有」「返回时有没有把绑定冲掉」这类问题时直接看这一行。
 */
internal fun SettingsPage.logDebugState() {
    val key = configState.config.value.key(KeyId.PLUS)
    val master = configState.config.value.global.enabled
    XLog.w(
        "调试·状态：screen=$screen 栈=${navStack.joinToString(">")} 总闸=$master 侧键=${key.enabled} " +
            "机型=$deviceModel 清单=$deviceSupport " +
            //  token 与**命令正文**一律过脱敏：`shell:` / `intent:` 的正文可能内嵌 token、密码，
            //  而这一行是 W 级 —— 既进 logcat 也进「导出日志」那份文件（口径见 `XLog.redactCommand`）。
            //  时长 / 指纹仍能把「同一处、前后两次」对上，判据不受影响。
            "单击=${XLog.redactToken(key.single.token)}" +
            " 双击=${XLog.redactToken(key.double.token)}" +
            " 长按=${XLog.redactToken(key.long.token)} | " +
            "表单 kind=${commandDraft.draft.kind.id} editing=${commandDraft.draft.editingName} " +
            "name='${commandDraft.draft.name}'" +
            " cmd=${XLog.redactToken(commandDraft.draft.single)}" +
            " steps=${commandDraft.draft.steps.map { XLog.redactToken(it) }} | " +
            "命令库=${configState.commands.value.map { it.name }}",
    )
}
