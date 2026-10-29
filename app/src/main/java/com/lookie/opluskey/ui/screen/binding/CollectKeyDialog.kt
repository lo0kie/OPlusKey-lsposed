package com.lookie.opluskey.ui.screen.binding

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.nextCustomKeyId
import com.lookie.opluskey.core.model.withCustomKey
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.FrostedDialog
import com.lookie.opluskey.ui.HINT_SLOT_BINDING
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialogBottomMargin
import com.lookie.opluskey.ui.dialogSideMargin
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

/**
 * 「等按键」那个窗收场的最长等待（毫秒）—— [AddKeyDialog] 的聚焦最多被推迟这么久。
 *
 *  取值比库的退场动画（250ms）留一倍余量。这条只在 `onDismissFinished` 没来时才起作用
 *  （见 [CollectKeyDialog] 里那个兜底 effect）：正常路径回调一到就走，不等这一档。
 */
private const val COLLECT_CLOSE_TIMEOUT_MS = 600L

/**
 * 「自定义按键」收集弹窗 —— 按下哪颗键，就把它收进 [KeyWatchState]。
 *
 * ## 它替掉了原来那一整页
 *
 *  收集这件事本来就不需要一整页：用户进来、按一下、走人。以前那一页还带「添加」按钮和
 *  已添加列表，等于把「按一下」拆成三步。现在**按下即收**、弹窗自己关，清单回到了它该在的
 *  地方 —— 首页那张「按键」卡（见 `HomeKeysCard`；它原来在「按键绑定」页顶栏那份菜单里）。
 *
 *  已经收过的键再按一次只把它**选中**，不会多出一行（同一个键按两次不该有两行）。
 *
 * ## 三个弹窗，不是一个浮层换正文
 *
 *  按用户口径拆开，各自只办一件事：
 *
 *  1. [CollectKeyBody] —— 等按键（没有底部按钮：等着按键，没有第二步可点）；
 *  2. [AddKeyDialog] —— 听到了键，**起个名字、确认才落盘**（取消就是没加）；
 *  3. [DuplicateBody] —— 这颗键本来就在清单里，只提示一句。
 *
 *  三件事塞进一个浮层换正文的话，标题与按钮只能共用一套语义，越改越别扭；分开之后
 *  每一步的「能做什么」都由它自己那个弹窗说清楚。
 *
 * ## 监听是注入侧的事，收听是**弹窗自己**的事
 *
 *  界面拿不到按键：设置页是宿主 Activity 里的覆盖层，焦点在宿主控件上，音量 / 电源更是在
 *  应用之前就被系统处理掉。所以弹窗一露头发一条 `ACTION_KEY_WATCH` 让注入侧开旁路监听，
 *  关掉再关掉（`DisposableEffect` 的 `onDispose` 一定走到，包括被返回键 / 点遮罩关掉）。
 *
 *  上报（`ACTION_KEY_SEEN`）由**本弹窗自己注册的接收器**收 —— 不借宿主那条
 *  （`host/GestureReceiver`）：那边属 `host/`，而 host/ 不许认识界面层
 *  （`docs/decoupling.md` 动作 1、2，`tools/audit-coupling.py` 会当场报错）。
 *
 *  那条旁路**只读**：它不改认领与放行，监听期间按键该干嘛还干嘛。
 */
@Composable
internal fun SettingsPage.CollectKeyDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 听到一颗**能收**的键：收集到此为止，交给「添加按键」那个弹窗起名字。
    var pendingCode by remember(show) { mutableStateOf<Int?>(null) }
    // 这颗键本来就在清单里：只提示一句（没有下一步可做，所以那一档不摆按钮）。
    var duplicateName by remember(show) { mutableStateOf<String?>(null) }
    // 被拒（保留码 / 内置键 / 到上限）用**消息条**说，不写在弹窗正文里（用户口径）：
    //  正文里那句红字会把面板撑高、又跟下面的「正在等待按键…」挤在一起；而且它是
    //  「对刚才那一下的答复」，与底部的操作回执是同一类东西。
    //  提示条必须画在弹窗**之上**才看得见 —— 根 Scaffold 那边已经把提示挂到浮层槽的
    //  弹窗宿主之后（见 `PageContent` 的 `popupHost`）。
    // 「等按键」那个窗**退场动画播完了**没有 —— 命名的聚焦要等它（见 [AddKeyDialog] 的 `focusReady`）。
    //  库在退场动画结束时会收一次键盘：早于那一步聚焦，键盘就是「弹出来又被收走」。
    var collectClosed by remember(show) { mutableStateOf(false) }
    // 那个窗此刻**该不该关** —— 听到键（或撞上重复）之后它就该退了。
    //  它和 `collectClosed` 一起决定「收场回调还作不作数」：弹窗被用户自己关掉（点遮罩 / 返回）
    //  时它也是开着→关着，那一刻的 `onDismissFinished` 与「听到键」没有任何关系。
    val collectClosing = pendingCode != null || duplicateName != null
    // 「还在等按键」= 既没听到能收的键、也没撞上重复的那一颗。
    val waiting = !collectClosing
    val draftName = newKeyName()

    //  **倒计时**：注入侧到点自己停发键码（`ModuleWire.KEY_WATCH_WINDOW_MS`），界面必须把
    //  这段时间显示出来、并在同一刻收场 —— 否则第 60 秒弹窗还挂着、按键已经没人报，
    //  用户看到的是「按了没反应」。数字与注入侧共用同一个常量，不各写一份。
    val secondsLeft = rememberWatchCountdown(show, waiting, onDismiss)

    // 兜底 —— 这一档只在「收场回调根本没来」时才该跑，理由见 [CollectCloseFallback]。
    CollectCloseFallback(show = show, closing = collectClosing) { collectClosed = true }

    // **只在「等按键」这一小段里监听**：一听到键就交给下一步，注入侧不该继续往外报。
    //  被拒时留在等待态、用消息条说一句，用户可以再按一次。
    KeyWatchWhileVisible(show && waiting, context) { code ->
        when (val check = checkKey(code)) {
            // 被拒：消息条说一句，继续等下一颗（弹窗不关）
            is CollectCheck.Rejected -> check.message?.let { hint(it, slot = HINT_SLOT_BINDING) }

            is CollectCheck.Duplicate -> duplicateName = check.name

            CollectCheck.Ok -> pendingCode = code
        }
    }
    // 两个浮层都走**项目自己的外壳**（[FrostedDialog]）：面板底色 / 磨砂 / 标题由它画。
    //  用库的 `OverlayDialog(title = …)` 的话面板是库画的不透明底，同一页里别的弹窗是糊的、
    //  这两个是不透明的（用户口径「收集 dialog 换成通用 dialog」）。
    FrostedDialog(
        show = show && waiting,
        title = I18n.get(Strings.KEY_WATCH_TITLE, "自定义按键"),
        onDismiss = onDismiss,
        // 退场动画播完才算「让位」—— 库在这一刻会收一次键盘，命名的聚焦必须排在这之后。
        //  只在**该关**那一档置位：用户自己关掉弹窗时「上一个窗收场了」这件事对命名窗毫无意义。
        onDismissFinished = { if (collectClosing) collectClosed = true },
    ) {
        CollectKeyBody(secondsLeft)
    }
    AddKeyDialog(
        //  **等收集窗彻底收场了再开这一个**：两个弹窗窗口同时在场的那几百毫秒里，键盘会被
        //  前一个窗口的销毁带走一次，而我们在后一个窗口里刚聚焦 ⇒ 用户看到的就是
        //  「键盘拉起又消失」。以前是「早开窗、晚抢焦点」（`focusReady`），窗口仍是叠着的；
        //  现在连窗都不叠 —— 收场回调（或它的兜底）一到，这一格才置位。
        show = show && pendingCode != null && collectClosed,
        initial = draftName,
        // 取消 = 不添加：这一步还没写过配置，关掉就是没加
        onDismiss = onDismiss,
        onConfirm = { name ->
            // 写盘失败时 `write` 自己已经提示过，这里不关窗：用户可以改个名再试一次
            if (addKey(pendingCode, name)) onDismiss()
        },
    )
    FrostedDialog(
        show = show && duplicateName != null,
        title = I18n.get(Strings.KEY_WATCH_TITLE, "自定义按键"),
        onDismiss = onDismiss,
    ) {
        duplicateName?.let { DuplicateBody(it) }
    }
}

/**
 * 「等按键」那个窗**收场的兜底** —— `onDismissFinished` 没来时才接手。
 *
 *  那个回调正常都会来（库在退场动画播完时给）。但真机上一旦因为「动画被打断」之类没回调，
 *  流程就卡在「弹窗已消失、命名窗永不出现」，所以留一条超时：
 *
 *   · 先等 [COLLECT_CLOSE_TIMEOUT_MS]（比退场动画留一倍余量）；
 *   · 到点再看一眼：这一刻弹窗**还开着、并且已经该关了** ⇒ 说明那声回调丢了，直接放行；
 *   · **已经关着** ⇒ 回调确实来过（它已把那个位置位），什么都不做。
 *     这一步是关键：否则「用户自己点遮罩关掉弹窗 → 回调照常置位」之后，这条 effect 会在
 *     600ms 时再跑一次，把一颗**迟到的键**当成「上一个窗收场了」。
 *
 *  单独成函数是为了让 [CollectKeyDialog] 留在 detekt 的圈复杂度阈值之内。
 *
 * @param closing 这个窗**此刻该不该关**（听到键 / 撞上重复之后）
 * @param onTimedOut 确认回调丢了，放行
 */
@Composable
private fun CollectCloseFallback(
    show: Boolean,
    closing: Boolean,
    onTimedOut: () -> Unit,
) {
    LaunchedEffect(show, closing) {
        if (!closing) return@LaunchedEffect
        delay(COLLECT_CLOSE_TIMEOUT_MS)
        if (show) onTimedOut()
    }
}

/**
 * 下一颗自定义键的**默认名** —— 只算，不写。
 *
 *  名字预填「按键 N」（N 取自 id 的序号）：它是**存进配置的字面量**，之后切界面语言也不跟着
 *  变 —— 这份清单是用户的东西，不是界面文案。
 */
private fun SettingsPage.newKeyName(): String {
    val cfg = configState.config.value
    val id = cfg.nextCustomKeyId()
    // 序号从 id 里取（`ck3` → 3）：删过键之后「第几颗」与「名字里的数」会分叉，
    //  而名字一旦定下就是配置里的字面量，不该因为删了别的键而改。
    val seq = id.removePrefix(KeyId.CUSTOM_ID_PREFIX).toIntOrNull() ?: (cfg.customKeys.size + 1)
    return I18n.get(Strings.BINDING_CUSTOM_KEY_NAME, "按键 %d").format(seq)
}

/** [checkKey] 的结论 —— 能不能收、还是本来就有。 */
private sealed interface CollectCheck {
    /**
     * 不是可自定义的键（保留码 / 内置键），或已到上限。
     *
     * @param message 经消息条说出去的理由；`null` = 什么都不用说（键码无效这种内部情形）
     */
    data class Rejected(
        val message: String?,
    ) : CollectCheck

    /** 本来就在清单里（已顺手选中）。 */
    data class Duplicate(
        val name: String,
    ) : CollectCheck

    /** 可以收 —— 交给「添加按键」那个弹窗起名字。 */
    data object Ok : CollectCheck
}

/**
 * 这一颗键**能不能收** —— 只判、不写。
 *
 *  与 [addKey] 分开是为了「先命名再落盘」：用户点了确定才真的写配置。
 */
private fun SettingsPage.checkKey(code: Int): CollectCheck {
    val cfg = configState.config.value
    // 查重比的是**配置认得出的全部键**（内置 + 自定义），不是只看 customKeys：
    //  按下侧键时若不拦，会被收成一颗「影子键」—— 而分发时 `keyForCode` 先匹配到侧键，
    //  这颗键永远触发不了，还查不出原因。
    val existing = cfg.allKeys.firstOrNull { code in it.acceptedKeyCodes }
    // 「这颗码本来就不该被收」：保留码（动作会注入）与内置键（侧键本来就在清单里）
    val notCollectable = code in KeyId.RESERVED_CODES || existing?.isCustom == false
    // 数量上限：自定义键最多 [KeyId.MAX_CUSTOM_KEYS] 颗（用户口径）
    val atLimit = cfg.customKeys.size >= KeyId.MAX_CUSTOM_KEYS
    return when {
        //  **0 不是一颗键，是「广播里没带这个字段」**（接收器的默认值）—— 这一档说明那条按键
        //  广播本身有问题（发送侧没塞 extra / 传了空 intent）。静默拒绝的表现是「按了没反应」，
        //  与「这颗键不能收」长得一样，用户只会以为功能坏了。留一行账，界面上也说一句。
        code <= 0 -> {
            XLog.w("收集按键：收到 keyCode=$code（广播没带按键码？），拒绝")
            CollectCheck.Rejected(I18n.get(Strings.KEY_WATCH_NO_CODE, "没收到按键码，再按一次试试"))
        }

        notCollectable -> {
            XLog.w("收集按键：keyCode=$code 不是可自定义的键（保留码 / 内置键），拒绝")
            CollectCheck.Rejected(I18n.get(Strings.KEY_WATCH_RESERVED, "这颗键不能添加：模块自己会用到它"))
        }

        existing != null -> {
            KeyWatchState.select(existing.id)
            CollectCheck.Duplicate(existing.label)
        }

        atLimit -> {
            XLog.w("收集按键：已到上限（${KeyId.MAX_CUSTOM_KEYS} 颗），拒绝 keyCode=$code")
            CollectCheck.Rejected(I18n.format(Strings.KEY_WATCH_LIMIT, KeyId.MAX_CUSTOM_KEYS))
        }

        else -> {
            CollectCheck.Ok
        }
    }
}

/**
 * 把 [code] 连同用户起的名字写进配置，并弹一句「已添加「…」」—— 命名弹窗上点了「确定」才走到这里。
 *
 *  id 在这里按**此刻**的配置重新取（不在组合期就定下）：弹窗开着这段时间别处可能已经写过
 *  配置，拿旧快照算出来的 id 可能已经有人占了。
 *
 *  [code] 可空只是为了让调用点少一层判断（见 `CollectKeyDialog` 那条圈复杂度）；
 *  真走到 `null` 说明命名窗没听到键就开了，直接当失败。
 *
 * @return `true` = 写成功；`false` = 写盘失败（`write` 自己已经 toast 过）或没有键码，调用方别关窗
 */
private fun SettingsPage.addKey(
    code: Int?,
    name: String,
): Boolean {
    // `null` = 没听到键就点了确定（这一档不该出现：命名窗只在听到键之后才开）
    if (code == null) return false
    val cfg = configState.config.value
    //  **这里只拿得到一个码**（收集期报回来的是「按下那一下的 keyCode」），而有的键一次按压
    //  只报其中一个码 —— 压感拍照键双击报 769、短按报 767。族内其余码由 `KeyId.custom`
    //  按主码**整族认下**（见 `KeyId.familyOf`），所以这里不必（也拿不到）逐个收集。
    val key = KeyId.custom(cfg.nextCustomKeyId(), name, code)
    if (write(cfg.withCustomKey(key), "custom_key_add=${key.id}") == CommandsSave.Failed) return false
    KeyWatchState.select(key.id)
    // 回执照发即可：提示条现在挂在**浮层槽、弹窗宿主之后**（见 `PageContent`），
    //  自己就压在弹窗之上；`onDismiss()` 紧跟其后把弹窗收走，两句谁先谁后都看得见
    hint(
        I18n.get(Strings.KEY_WATCH_ADDED, "已添加「%s」").format(name),
        slot = HINT_SLOT_BINDING,
    )
    return true
}

/**
 * 弹窗正文：三行，居中。
 *
 *  **没有底部按钮**：这一步只是等按键，没有第二步可确认；关掉靠返回键 / 点遮罩
 *  （[FrostedDialog] 里的 `OverlayDialog` 自带 `onDismissRequest`）。
 *
 *  **也没有「被拒」那行红字**：那类答复走底部的消息条（用户口径），正文只保留
 *  「按下想添加的按键 / 正在等待按键… / N 秒后自动关闭」这三行，高度恒定、不随按错而跳动。
 *  倒计时**单独一行**：与「正在等待按键」挤在一句里时，那个括号会把前半句推得长短不一。
 */
@Composable
private fun CollectKeyBody(secondsLeft: Int) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = I18n.get(Strings.KEY_WATCH_HINT, "按下想添加的按键"),
            style = COUITheme.textStyles.body2,
            color = COUITheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .dialogSideMargin()
                    .padding(bottom = 4.dp),
        )
        Text(
            text = I18n.get(Strings.KEY_WATCH_WAITING, "正在等待按键…"),
            style = COUITheme.textStyles.body2,
            color = COUITheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .dialogSideMargin()
                    .padding(bottom = 2.dp),
        )
        Text(
            text = I18n.format(Strings.KEY_WATCH_COUNTDOWN, secondsLeft),
            style = COUITheme.textStyles.body2,
            color = COUITheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .dialogSideMargin()
                    // 这一档**没有底部按钮**：底部内缩与标题上边距取同一档，上下才对称
                    .dialogBottomMargin(),
        )
    }
}

/**
 * 「这颗键已经在清单里了」—— 一句提示，**没有底部按钮**（用户口径）。
 *
 *  没有下一步可做：键已经在清单里、也已经被选中了，关掉就是全部。
 */
@Composable
private fun DuplicateBody(name: String) {
    Text(
        text = I18n.get(Strings.KEY_WATCH_DUPLICATE, "「%s」已经在清单里了").format(name),
        style = COUITheme.textStyles.body2,
        color = COUITheme.colorScheme.onSurfaceSecondary,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .dialogSideMargin()
                .dialogBottomMargin(),
    )
}

/**
 * 在这段该收键的时间（[active]）里开 / 关注入侧那条旁路监听，并把收到的键交给 [onSeen]。
 *
 *  [active] 不只是「弹窗开着」：听到键之后调用方会把它置 false —— 那时弹窗还留在屏幕上
 *  走下一步，但已经不该再往外报键了。
 *
 *  接收器活在 `DisposableEffect(active)` 里、不随每次重组重建，而 [onSeen] 每次重组都是新
 *  lambda —— 用 `rememberUpdatedState` 抓当前那一份，免得收到键时调到上一次组合的旧闭包。
 */
@Composable
private fun KeyWatchWhileVisible(
    active: Boolean,
    context: Context,
    onSeen: (Int) -> Unit,
) {
    val seenHandler by rememberUpdatedState(onSeen)
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose {}
        val receiver =
            seenKeyReceiver { code ->
                seenHandler.invoke(code)
            }
        // 发送方是 system_server（uid 1000）：系统身份对 NOT_EXPORTED 接收器照样可达，
        // 普通 App 即便发同名广播也进不来 —— 与 `host/GestureReceiver` 同一口径。
        val registered =
            runCatching {
                context.registerReceiver(
                    receiver,
                    IntentFilter(ModuleWire.ACTION_KEY_SEEN),
                    Context.RECEIVER_NOT_EXPORTED,
                )
            }.onFailure { XLog.w("收集按键：接收器没注册上（弹窗会一直显示「正在等待按键」）", it) }
                .isSuccess
        //  接收器没注册上就别开监听：注入侧照旧上报，但没有接收方，弹窗会一直停在
        //  「正在等待按键」直到 60 秒自动关 —— 用户看到的是「按了没反应」。
        if (!registered) return@DisposableEffect onDispose {}
        watch(context, on = true)
        onDispose {
            watch(context, on = false)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}

/**
 * 收「注入侧听见了一颗键」的接收器。
 *
 *  只把键交给调用方：这一路是**收集**，不是派发动作 —— 监听期间侧键该干嘛还干嘛。
 */
private fun seenKeyReceiver(onSeen: (Int) -> Unit): BroadcastReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(
            context: Context?,
            intent: Intent?,
        ) {
            if (intent?.action != ModuleWire.ACTION_KEY_SEEN) return
            runCatching {
                onSeen(intent.getIntExtra(ModuleWire.EXTRA_SEEN_CODE, 0))
            }.onFailure { XLog.w("收集按键：读到键码失败（忽略）：${it.javaClass.simpleName}") }
        }
    }

/** 开 / 关注入侧那条旁路监听（同一个 action，`watch_on` 分开关）。 */
private fun watch(
    context: Context,
    on: Boolean,
) {
    PageWire.send(
        context,
        HostApp.PACKAGE,
        Intent(ModuleWire.ACTION_KEY_WATCH)
            .putExtra(ModuleWire.EXTRA_WATCH_ON, on),
    )
}
