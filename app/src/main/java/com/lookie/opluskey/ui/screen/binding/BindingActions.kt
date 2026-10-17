package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.key
import androidx.compose.ui.input.key.key
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.isSerialCommand
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.MODULE_UNAVAILABLE_HINT
import com.lookie.opluskey.ui.model.PENDING_DEVICE_HINT
import com.lookie.opluskey.ui.model.UNSUPPORTED_DEVICE_HINT
import com.lookie.opluskey.ui.state.devicePending
import com.lookie.opluskey.ui.state.deviceSupported
import com.lookie.opluskey.ui.state.linkReady
import com.lookie.opluskey.ui.state.toast
import com.lookie.opluskey.ui.state.write

internal fun SettingsPage.actionOf(
    cfg: Config,
    g: Gesture,
): Action {
    val key = cfg.key(KeyId.PLUS)
    return when (g) {
        Gesture.SINGLE -> key.single
        Gesture.DOUBLE -> key.double
        Gesture.LONG -> key.long
    }
}

/**
 * 把一个动作绑到手势 [g] 上。
 *
 * @return **是否真的落盘了** —— 调用方（「打开应用」页挑完应用那一处）据此决定退不退页：
 *   被拦住 / 写盘失败还照退的话，用户刚挑的那个应用**看起来选了、其实什么都没写**。
 */
internal fun SettingsPage.pick(
    spec: ActionCatalog.Spec,
    g: Gesture,
): Boolean {
    // 与首页 / ⋮ 菜单**同一套闸位**。正常路径上它们已经拦过一遍（进不了这一页），
    //  这里拦的是「绕过首页直接落到这一页」那条路 —— 调试通道的 `navTo` 就是那么进来的
    //  （release 不可达，但它证明这个入口自己没有闸）。少了这一层，未就绪时网格照样能改绑定：
    //  写盘成功、广播也发得出去，可注入侧那一半压根没装起来，用户看到的是「点了没反应」。
    //  `configReadOnly` 不在这里判 —— 它由 [write] 单点拦（那里还负责报「未保存」）。
    if (!linkReady || !deviceSupported) {
        XLog.w(
            "选动作被拦住（页面未就绪）：链路就绪=$linkReady 清单命中=$deviceSupported " +
                "系统版本等待适配=$devicePending ${g.id} → ${spec.token}",
        )
        toast(
            when {
                devicePending -> PENDING_DEVICE_HINT
                !deviceSupported -> UNSUPPORTED_DEVICE_HINT
                else -> MODULE_UNAVAILABLE_HINT
            },
        )
        return false
    }
    return try {
        val cfg = configState.config.value
        val key = cfg.key(KeyId.PLUS)
        val action = Action.parse(spec.token)
        val updated =
            when (g) {
                Gesture.SINGLE -> {
                    key.copy(single = action)
                }

                Gesture.DOUBLE -> {
                    key.copy(double = action)
                }

                //  长按换成「多条命令」时必须把「连续触发」一起关掉（纯界面操作就能落进这个洞）：
                // 先给一条单条命令开连续触发、再把长按换成多条命令 —— 换动作只写 `long`、
                // 从不碰 `longRepeat`，界面只是把那一行置灰，而按住不放会一条条重放整个命令链。
                //  只在这一种组合下清：换成别的动作时把用户的开关也一起抹掉没有道理，
                // 而「动作是多条命令 + 连续触发=开」这个不一致由这里和判定侧的
                // `KeyConfig.longIsSerial` 双重保证（判定侧那道闸门见 `GestureDetector`）。
                Gesture.LONG -> {
                    key.copy(
                        long = action,
                        longRepeat = key.longRepeat && !isSerialCommand(configState.commands.value, action.token),
                    )
                }
            }.copy(
                //  选动作 == 启用（真机发现）：设置页读不到旧配置时会回落到
                // `Config.DEFAULT`，而默认是 `plus_enabled=0`（"装上模块不改变行为" 是有意的默认值）。
                enabled = true,
            )
        write(cfg.copy(keys = cfg.keys + (KeyId.PLUS to updated)), "${g.id} → ${spec.token}")
    } catch (e: Throwable) {
        XLog.w("选动作失败（忽略）：${spec.token}", e)
        false
    }
}
