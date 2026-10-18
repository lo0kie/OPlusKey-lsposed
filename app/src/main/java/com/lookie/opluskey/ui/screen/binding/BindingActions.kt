package com.lookie.opluskey.ui.screen.binding

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.isSerialCommand
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.blockedReason
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.MODULE_UNAVAILABLE_HINT
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.toast
import com.lookie.opluskey.ui.write

/**
 * 把一个动作绑到手势 [g] 上。
 *
 * @param appName 「打开应用」那一族挑完之后要**一并记下的名字**（`微信 2` / `扫一扫`）。
 *   它与绑定同一次写盘（`Config.appNames`，见 `Config.appNames` 的注释）；其余动作为 `null`。
 *   分两次写要多落一次盘 + 一次广播，而中间那一下读到的配置只有新绑定、没有名字，
 *   界面会当场闪一下包名。
 * @return **是否真的落盘了** —— 调用方（「打开应用」页挑完应用那一处）据此决定退不退页：
 *   被拦住 / 写盘失败还照退的话，用户刚挑的那个应用**看起来选了、其实什么都没写**。
 */
internal fun SettingsPage.pick(
    spec: ActionCatalog.Spec,
    g: Gesture,
    appName: String? = null,
): Boolean {
    // 与首页 / ⋮ 菜单**同一套闸位**。正常路径上它们已经拦过一遍（进不了这一页），
    //  这里拦的是「绕过首页直接落到这一页」那条路 —— 调试通道的 `navTo` 就是那么进来的
    //  （release 不可达，但它证明这个入口自己没有闸）。少了这一层，未就绪时网格照样能改绑定：
    //  写盘成功、广播也发得出去，可注入侧那一半压根没装起来，用户看到的是「点了没反应」。
    //  `configReadOnly` 不在这里判 —— 它由 [write] 单点拦（那里还负责报「未保存」）。
    if (!linkReady) {
        XLog.w("选动作被拦住（页面未就绪）：链路就绪=$linkReady ${g.id} → ${spec.token}")
        // 与首页那条常驻提示**同一句话**（判据见 [blockedReason]）：被拦住时看到的原因
        //  不该因为你从哪条路点进来而不同。
        toast(blockedReason() ?: MODULE_UNAVAILABLE_HINT)
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
        // 名字按**这次要绑的那个目标**记（应用/快捷方式/磁贴）：
        //  token 里带着 `app:` / `window:` / `setting:` 这些前缀与写法差异，
        //  而名字表的键是「身份」（`AppTarget.identity` 或磁贴组件名）—— 与界面查名字用的那一把完全一致。
        //  名字为空时**不动表**：`withAppName(…, null)` 是「删这一条」，那是「重新挑失败」的语义，
        //  不该被一次普通的重新绑定（绑内置动作、绑别的应用）顺手触发。
        val targetKey = action.appTarget
        val next =
            cfg
                .copy(keys = cfg.keys + (KeyId.PLUS to updated))
                .let { c -> if (appName != null && targetKey != null) c.withAppName(targetKey, appName) else c }
        write(next, "${g.id} → ${spec.token}") != CommandsSave.Failed
    } catch (e: Throwable) {
        XLog.w("选动作失败（忽略）：${spec.token}", e)
        false
    }
}
