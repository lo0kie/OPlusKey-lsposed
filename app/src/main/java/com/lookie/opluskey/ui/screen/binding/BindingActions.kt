package com.lookie.opluskey.ui.screen.binding

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.isSerialCommand
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.actionOf
import com.lookie.opluskey.ui.blockedReason
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.MODULE_UNAVAILABLE_HINT
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.write
import com.lookie.opluskey.ui.writeAndNotice

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
        hint(blockedReason() ?: MODULE_UNAVAILABLE_HINT)
        return false
    }
    return try {
        val cfg = configState.config.value
        // 绑到**当前在编辑的那颗键**上：侧键与自定义键各有一份 `KeyConfig`，
        //  以前这里写死 `KeyId.PLUS`，等于在任何一颗键上选动作都改侧键。
        val keyId = KeyWatchState.editing(cfg)
        val key = cfg.key(keyId)
        val action = Action.parse(spec.token)
        // 已经绑的就是它 ⇒ 直接返回（判据见 [bindingUnchanged]）
        if (bindingUnchanged(cfg, g, action, appName)) {
            XLog.w("选动作：${g.id} 已经是 ${spec.token}，不重复写")
            return true
        }
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
                .copy(keys = cfg.keys + (keyId to updated))
                .let { c -> if (appName != null && targetKey != null) c.withAppName(targetKey, appName) else c }
        val result = write(next, "${g.id} → ${spec.token}")
        // 绑成了才报：被拦 / 写盘失败时 [write] 自己已经报过原因；广播没发出去时它报的那条
        //  「已保存，但通知注入侧失败」更不能被一句「已绑定…」顶掉（底部只有一行回执）。
        if (result == CommandsSave.Ok) hint(boundNotice(spec, action, appName))
        //  返回值仍按「配置有没有落盘」给（见 [write] 的三态口径）：广播失败时文件与本地状态
        //  都已经是新值，调用方不该把它当成「没绑上」。
        result != CommandsSave.Failed
    } catch (e: Throwable) {
        XLog.w("选动作失败（忽略）：${spec.token}", e)
        //  点了格子必须有个说法：只记日志的话用户看到的就是「点了一下没反应」。
        hint(I18n.get(Strings.TOAST_CONFIG_SAVE_FAILED, "保存失败"))
        false
    }
}

/**
 * 这次「选动作」是不是**等于现状**（点的就是已经打着勾那一档 / 那一格）。
 *
 *  **去重是为了「点了没变的事，别落盘、也别报回执」**：网格里点一下当前选中那格、或入口格菜单里
 *  点一下已经打勾那档，是最常见的误触；这时候还落一次盘、再弹一句「已绑定「…」」，
 *  读起来像「刚刚改了什么」。
 *
 *  三条判据，缺一不可：
 *   · 主开关得是开着的 —— 关着时 [pick] 那一下还会顺手 `enabled = true`，那是真变化；
 *   · 规范写法要一样（比 `token`，不比对象：`Action.parse` 出来的取值就是这个串）；
 *   · **显示名也没变** —— 挑应用那条路会把名字带进来，同一个应用也可能换了名字
 *    （分身 / 快捷方式），那时候不写就等于把用户刚挑的名字丢了。
 */
private fun SettingsPage.bindingUnchanged(
    cfg: Config,
    g: Gesture,
    action: Action,
    appName: String?,
): Boolean {
    if (!cfg.key(KeyWatchState.editing(cfg)).enabled) return false
    if (actionOf(cfg, g).token != action.token) return false
    val nameDiffers = appName != null && action.appTarget?.let { cfg.appName(it) != appName } == true
    return !nameDiffers
}

/**
 * 「已绑定「录音」」—— 绑成功之后那一句话。
 *
 *  名字按「谁更具体」取：挑应用那条路把名字当参数带进来（[pick] 的 `appName`，那是
 *  `微信 2` 这种口令表里的写法）→ 清单里的名字（内置动作与入口格那些档位，`ActionCatalog.label`）
 *  → 格子自己的名字（自定义命令 —— `label` 只认清单里那一批，认不出来）。
 *
 *  **不能直接用 `spec.label`**：挑应用那条路传进来的 `spec` 只有 token（`name = token`），
 *  打出来会是「已绑定「app:com.tencent.mm」」。
 */
private fun boundNotice(
    spec: ActionCatalog.Spec,
    action: Action,
    appName: String?,
): String =
    I18n
        .get(Strings.TOAST_BOUND, "已绑定「%s」")
        .format(appName ?: ActionCatalog.label(action) ?: spec.label)

/**
 * 停用 / 启用**一颗键** —— 按键菜单里那一行（「停用这颗键」/「启用这颗键」）。
 *
 *  `enabled` 就是「这颗键能不能用」的闸门：自定义键收集时置开、导入时一律置开，
 *  而在这之前界面上**没有任何入口**能改它 —— 导入一份收了音量键的配置之后只能删键（连带丢绑定）。
 *
 *  **侧键也走这一支**（用户口径：「快捷键也允许被禁用」）：从前它只给自定义键 ——
 *  那时侧键的 `enabled` **同时兼任模块总闸**，从这条菜单写它等于关掉整个模块。
 *  拆开之后这一位只表示「这一颗键接管不接管」，与首页那个总闸（`GlobalConfig.enabled`）
 *  是两层，所以不需要过免责声明（那道闸只开总闸时才要，也只在首页）。
 *
 *  回执两档都发（与首页主开关不同，那一档关闭时不发）：这里没有「常驻横幅立刻接管」那回事，
 *  发完这一句底部就是这一句；首页关主开关时横幅会紧接着顶上来，所以那边才不发。
 */
internal fun SettingsPage.setKeyEnabledFromMenu(
    id: KeyId,
    on: Boolean,
) {
    val cfg = configState.config.value
    val key = cfg.key(id)
    // 没变就不写：菜单里点一下本来就打着勾的那一档，不该落一次盘、更不该报「已停用」
    if (key.enabled == on) return
    writeAndNotice(
        cfg.copy(keys = cfg.keys + (id to key.copy(enabled = on))),
        "key_enabled=${if (on) 1 else 0} key=${id.id}",
    ) {
        I18n
            .get(
                if (on) Strings.TOAST_KEY_ENABLED else Strings.TOAST_KEY_DISABLED,
                if (on) "已启用「%s」" else "已停用「%s」",
            ).format(id.label)
    }
}
