package com.lookie.opluskey.ui.screen.binding

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.commandTitleOf
import com.lookie.opluskey.core.model.isSerialCommand
import com.lookie.opluskey.ui.BindingTarget
import com.lookie.opluskey.ui.HINT_SLOT_BINDING
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appLabelOf
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.blockedReason
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.MODULE_UNAVAILABLE_HINT
import com.lookie.opluskey.ui.model.stepTitle
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeysAsk
import com.lookie.opluskey.ui.write
import com.lookie.opluskey.ui.writeAndNotice

/**
 * 把一个清单里的动作绑到 [target] 那一格上。
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
    target: BindingTarget,
    appName: String? = null,
): Boolean = writeBinding(spec.token, target, appName) { action -> boundNotice(spec, action, appName) }

/**
 * 把**不在动作清单里**的一个 token 绑到 [target] 那一格上 —— 与 [pick] 同一条写盘路径。
 *
 *  哪些动作走这一支：它们在清单里没有对应的 `Spec`（取值是运行时才有的，静态表里列不出来）——
 *  · 变量那一步（`setvar:计数+=1`）；
 *  · 结束应用（`proc:stop:包名`，由「应用控制」那一格挑出来）；
 *  · 「选择应用」页 FAB 确认的那一条（`app:包名` / `appshortcut:包名/id`）—— 带包名之后
 *    就不在清单里了，而清单里那个 `Spec` 只为回执文案存在，不值得为它造一条假的。
 *
 *  回执那句话走 [stepTitle]（「怎么念一个动作」全项目只有那一支实现），外面套
 *  [Strings.TOAST_BOUND] 那层「」—— 与 [pick] 的回执同一个形状。
 *
 * @param appName 与 [pick] 同一个含义：挑应用那一趟要一并记下的名字（`Config.appNames`）
 */
internal fun SettingsPage.pickToken(
    token: String,
    target: BindingTarget,
    appName: String? = null,
): Boolean =
    writeBinding(token, target, appName) { _ ->
        //  名字走 `appNameOf`：`proc:` / `app:` 那几档的标题里要带应用名（`结束应用:微信`），
        //  而 `stepTitle` 的回调拿到的是**目标写法**（`999@包名/类名`）而不是包名 ——
        //  `appLabelOf` 只认包名，查不到时把整串原样吐回来（回执就成了「已绑定「999@com.foo/.Bar」」）。
        I18n.format(Strings.TOAST_BOUND, stepTitle(token) { target -> appNameOf(target) })
    }

/**
 * 绑定的**唯一写盘路径**（[pick] / [pickToken] 都从这里过）。
 *
 * @param token 要写进配置的动作写法
 * @param notice 成功回执那句话（参数是解析后的动作）—— 两种动作叫法不同，这一层不猜
 */
private fun SettingsPage.writeBinding(
    token: String,
    target: BindingTarget,
    appName: String?,
    notice: (Action) -> String,
): Boolean {
    // 与首页 / ⋮ 菜单**同一套闸位**。正常路径上它们已经拦过一遍（进不了这一页），
    //  这里拦的是「绕过首页直接落到这一页」那条路 —— 调试通道的 `navTo` 就是那么进来的
    //  （release 不可达，但它证明这个入口自己没有闸）。少了这一层，未就绪时网格照样能改绑定：
    //  写盘成功、广播也发得出去，可注入侧那一半压根没装起来，用户看到的是「点了没反应」。
    //  `configReadOnly` 不在这里判 —— 它由 [write] 单点拦（那里还负责报「未保存」）。
    if (!linkReady) {
        XLog.w("选动作被拦住（页面未就绪）：链路就绪=$linkReady ${target.what} → $token")
        // 与首页那条常驻提示**同一句话**（判据见 [blockedReason]）：被拦住时看到的原因
        //  不该因为你从哪条路点进来而不同。
        hint(blockedReason() ?: MODULE_UNAVAILABLE_HINT, slot = HINT_SLOT_BINDING)
        return false
    }
    return try {
        val cfg = configState.config.value
        val action = Action.parse(token)
        // 已经绑的就是它 ⇒ 直接返回（判据见 [bindingUnchanged]）
        if (bindingUnchanged(cfg, target, action, appName)) {
            XLog.w("选动作：${target.what} 已经是 $token，不重复写")
            return true
        }
        // 名字按**这次要绑的那个目标**记（应用/快捷方式/磁贴）：
        //  token 里带着 `app:` / `window:` / `setting:` 这些前缀与写法差异，
        //  而名字表的键是「身份」（`AppTarget.identity` 或磁贴组件名）—— 与界面查名字用的那一把完全一致。
        //  名字为空时**不动表**：`withAppName(…, null)` 是「删这一条」，那是「重新挑失败」的语义，
        //  不该被一次普通的重新绑定（绑内置动作、绑别的应用）顺手触发。
        //
        //  **场景目标不走这条路**（见 [commitBinding]）：它写的是草稿，没有「名字表」可落 ——
        //  那一页的显示名由 `appNameOf` 按包名现取，与网格里那行小字同一把尺子。
        val result = commitBinding(cfg, target, action, appName)
        // 绑成了才报：被拦 / 写盘失败时 [write] 自己已经报过原因；广播没发出去时它报的那条
        //  「已保存，但通知注入侧失败」更不能被一句「已绑定…」顶掉（底部只有一行回执）。
        //  归到**整页那一个位**（[HINT_SLOT_BINDING]）：这一页连改几格时屏幕上改字，
        //  不叠出「已绑定甲」「已绑定乙」两串。
        if (result == CommandsSave.Ok) hint(notice(action), slot = HINT_SLOT_BINDING)
        //  返回值仍按「配置有没有落盘」给（见 [write] 的三态口径）：广播失败时文件与本地状态
        //  都已经是新值，调用方不该把它当成「没绑上」。
        result != CommandsSave.Failed
    } catch (e: Throwable) {
        XLog.w("选动作失败（忽略）：$token", e)
        //  点了格子必须有个说法：只记日志的话用户看到的就是「点了一下没反应」。
        //  同一页只有一条回执 ⇒ 它**顶掉**上一条（而不是叠一条）—— 失败本该盖住那句「已绑定」。
        hint(I18n.get(Strings.TOAST_CONFIG_SAVE_FAILED, "保存失败"), slot = HINT_SLOT_BINDING)
        false
    }
}

/**
 * 把这一档选中的 [action] **落到它该去的地方** —— 单键 / 组合写盘，场景写草稿。
 *
 *  分派只有这一处：`BindingTarget` 回答「写哪儿」，这里回答「怎么落地」。写成两支而不是让
 *  每个目标自己实现一个 `commit`，是为了把「落盘 + 广播 + 名字表」那套留在同一个地方 ——
 *  三条路各写一遍时，加一个目标就要把这一整套复制一份。
 */
private fun SettingsPage.commitBinding(
    cfg: Config,
    target: BindingTarget,
    action: Action,
    appName: String?,
): CommandsSave {
    if (target is BindingTarget.Scene) {
        //  场景块属于**草稿**：这一下只改内存里的那张表，等「系统场景」页那颗确认才写配置
        //  （见 `BranchScreen.saveScenes`）。落在这里的改动退回上一页时也还在（草稿是页面级的）。
        val ok = target.bind(action)
        XLog.w("场景动作：第 ${target.at + 1} 块 ${target.gesture.id} → ${action.token}（草稿，未落盘）")
        return if (ok) CommandsSave.Ok else CommandsSave.Failed
    }
    return write(
        withTargetBound(cfg, target, action).withAppNameOf(action, appName),
        "${target.what} → ${action.token}",
    )
}

/**
 * 名字跟着绑定一起写进 [Config.appNames]（判据见 [pick] 的 `appName`）—— 只对带目标的那几族成立。
 */
private fun Config.withAppNameOf(
    action: Action,
    appName: String?,
): Config {
    val t = action.appTarget ?: return this
    return if (appName != null) withAppName(t, appName) else this
}

/**
 * 把 [action] 绑到 [target] 那一格上 —— **两种目标各写各的表**，其余一律不动。
 *
 *  单键那两支带着两条老规矩（选动作 == 启用、长按换成多条命令时关掉连续触发），
 *  组合键没有这些位：它没有「这一颗键启用不启用」，也没有「连续触发」。
 *
 *  **场景那一支走不到这里**：它写的是草稿，在 [commitBinding] 里就分走了。留这一支只为
 *  `when` 穷尽 —— 返回原样，不会把「草稿」这件事悄悄落成一次配置写入。
 */
private fun SettingsPage.withTargetBound(
    cfg: Config,
    target: BindingTarget,
    action: Action,
): Config =
    when (target) {
        is BindingTarget.Scene -> {
            cfg
        }

        is BindingTarget.Key -> {
            val key = cfg.key(target.id)
            val updated =
                when (target.gesture) {
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
            cfg.copy(keys = cfg.keys + (target.id to updated))
        }

        is BindingTarget.Combo -> {
            cfg.copy(
                combos =
                    cfg.combos.map { c ->
                        if (c.id != target.id) {
                            c
                        } else {
                            when (target.gesture) {
                                Gesture.SINGLE -> {
                                    c.copy(single = action)
                                }

                                Gesture.LONG -> {
                                    c.copy(long = action)
                                }

                                //  组合上没有双击（`ComboKey.actionOf` 恒返回「无操作」）——
                                //  界面上走不到这一支；真走到了就原样不动，不写一个用不上的位。
                                Gesture.DOUBLE -> {
                                    c
                                }
                            }
                        }
                    },
            )
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
 *   · 开关得是开着的 —— 关着时 [pick] 那一下还会顺手 `enabled = true`，那是真变化
 *    （这一条只对单键成立：组合键没有这个位）；
 *   · 规范写法要一样（比 `token`，不比对象：`Action.parse` 出来的取值就是这个串）；
 *   · **显示名也没变** —— 挑应用那条路会把名字带进来，同一个应用也可能换了名字
 *    （分身 / 快捷方式），那时候不写就等于把用户刚挑的名字丢了。
 */
private fun SettingsPage.bindingUnchanged(
    cfg: Config,
    target: BindingTarget,
    action: Action,
    appName: String?,
): Boolean {
    if (target is BindingTarget.Key && !cfg.key(target.id).enabled) return false
    if (target.actionIn(cfg).token != action.token) return false
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
 *
 *  **打开 / 结束应用那一族要带前缀**：只写应用名（「已绑定「微信」」）看不出这一趟是「打开」
 *  还是「结束」—— 两件事读起来完全不同。所以那两档走 [commandTitleOf]（与步骤行、执行时提示
 *  同一支实现），产出「已绑定「打开应用:微信」」「已绑定「结束应用:微信」」。
 *
 *  **其余动作走 [stepTitle]**：「一个动作怎么念」全项目只有那一份规则，步骤行、命令卡摘要、
 *  执行时提示念的都是同一句，这一条回执没有理由例外。自定义命令尤其明显 —— 裸名是
 *  「已绑定「录音」」，而步骤行里那一行写的是「自定义命令: 录音」，两处叫法不该是两种。
 *
 *  「应用名从哪个字段取」是唯一的差别，所以先把包名抽出来（[appPkgOf]），两档各自走**同一支**
 *  标题实现 —— 各写一遍的话，「怎么念一个动作」就又多出几份，而 `tools/audit-arch.py` 数正是这个数。
 */
private fun SettingsPage.boundNotice(
    spec: ActionCatalog.Spec,
    action: Action,
    appName: String?,
): String {
    val pkg = appPkgOf(action)
    val shown =
        if (pkg == null) {
            //  挑应用那条路带进来的名字优先（它是口令表里的写法，`stepTitle` 认不出来）；
            //  都没有时 `stepTitle` 会把 token 原样吐回来（手改过的配置），那也比空串好。
            appName ?: stepTitle(action.token) { target -> appNameOf(target) }.ifBlank { spec.label }
        } else {
            commandTitleOf(action).forSentence(action, { appName ?: appLabelOf(pkg) }, action.token)
        }
    return I18n.get(Strings.TOAST_BOUND, "已绑定「%s」").format(shown)
}

/**
 * 「带动作前缀」那三档各自的目标包名 —— 只有它们要前缀，其余动作没有这一层。
 *
 *  `null` = 这一档不归 [commandTitleOf] 管（清单名 / 挑应用带进来的名字已经够具体）。
 */
private fun appPkgOf(action: Action): String? =
    when (action) {
        is Action.App -> AppTarget.pkg(action.target)
        is Action.AppWindow -> AppTarget.pkg(action.target)
        is Action.Process -> action.pkg
        else -> null
    }

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
    //  含这颗键的组合（用户口径：与按键列表那颗开关**完全一样**的检查与提示）：
    //   · 停用 ⇒ 含它的组合一起停，但先弹窗说清有几组；
    //   · 启用 ⇒ 含它、现在关着的组合不自动开，问一句。
    val using = cfg.combos.filter { id.id in it.keys }
    if (on) {
        val offIds = using.filterNot { it.enabled }.map { it.id }.toSet()
        if (offIds.isNotEmpty()) {
            KeysAsk.ask(
                KeysAsk.Kind.ENABLE,
                keys = setOf(id.id),
                extraCombos = offIds,
                slot = HINT_SLOT_BINDING,
            )
            return
        }
    } else {
        val onIds = using.filter { it.enabled }.map { it.id }.toSet()
        if (onIds.isNotEmpty()) {
            KeysAsk.ask(
                KeysAsk.Kind.DISABLE,
                keys = setOf(id.id),
                extraCombos = onIds,
                slot = HINT_SLOT_BINDING,
            )
            return
        }
    }
    val cascadeOff = if (on) emptySet() else using.map { it.id }.toSet()
    writeAndNotice(
        cfg.copy(
            keys = cfg.keys + (id to key.copy(enabled = on)),
            //  停用这一档：含它的组合一起停（启用那一支在上面问过了，走到这里说明没有要问的）
            combos = cfg.combos.map { if (it.id in cascadeOff) it.copy(enabled = false) else it },
        ),
        "key_enabled=${if (on) 1 else 0} key=${id.id}",
        //  整页一个位（[HINT_SLOT_BINDING]）：不按 `what` 的前缀分（那会得到 `key_enabled`，
        //  与这一页别的回执各占一格、又叠起来）。
        slot = HINT_SLOT_BINDING,
    ) {
        I18n
            .get(
                if (on) Strings.TOAST_KEY_ENABLED else Strings.TOAST_KEY_DISABLED,
                if (on) "已启用 [%s]" else "已停用 [%s]",
            ).format(id.label)
    }
}
