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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.state.KeyWatchState
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「自定义按键」收集弹窗 —— 按下哪颗键，就把它收进 [KeyWatchState]。
 *
 * ## 它替掉了原来那一整页
 *
 *  收集这件事本来就不需要一整页：用户进来、按一下、走人。以前那一页还带「添加」按钮和
 *  已添加列表，等于把「按一下」拆成三步。现在**按下即收**、弹窗自己关，清单回到了它该在的
 *  地方 —— 「按键绑定」页顶栏那份菜单（见 [BindingKeyMenu]）。
 *
 *  已经收过的键再按一次只把它**选中**，不会多出一行（同一个键按两次不该有两行）。
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
internal fun CollectKeyDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    KeyWatchWhileVisible(show, context) { code ->
        KeyWatchState.collect(code)
        onDismiss()
    }
    OverlayDialog(
        show = show,
        title = I18n.get(Strings.KEY_WATCH_TITLE, "自定义按键"),
        onDismissRequest = onDismiss,
    ) {
        CollectKeyBody(onDismiss)
    }
}

/**
 * 弹窗正文：一句说明 + 一行「正在等待按键…」+ 一颗「取消」。
 *
 *  只有取消：收键是**按下即收**，没有第二步可确认，所以不给「添加」——
 *  多一颗按钮只会让人以为还得再点一下。
 */
@Composable
private fun CollectKeyBody(onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = I18n.get(Strings.KEY_WATCH_HINT, "按下想添加的按键"),
            style = COUITheme.textStyles.body2,
            color = COUITheme.colorScheme.onSurfaceSecondary,
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .dialogSideMargin()
                    .padding(bottom = 12.dp),
        )
        TextButton(
            text = I18n.get(Strings.DIALOG_CANCEL, "取消"),
            onClick = onDismiss,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            pressScaleEnabled = false,
            minHeight = DialogDefaults.ButtonBarMinHeight,
            colors =
                ButtonDefaults.textButtonColorsBorderless(
                    textColor = COUITheme.colorScheme.onSurfaceSecondary,
                ),
            insideMargin = DialogDefaults.ButtonBarInsideMargin,
        )
    }
}

/** 弹窗正文的左右内缩 —— 用库自己那一档，正文才与标题左缘对齐。 */
private fun Modifier.dialogSideMargin(): Modifier =
    padding(
        start = DialogDefaults.insideMargin.width,
        end = DialogDefaults.insideMargin.width,
    )

/**
 * 弹窗可见期间开 / 关注入侧那条旁路监听，并把收到的键交给 [onSeen]。
 *
 *  接收器活在 `DisposableEffect(show)` 里、不随每次重组重建，而 [onSeen] 每次重组都是新
 *  lambda —— 用 `rememberUpdatedState` 抓当前那一份，免得收到键时调到上一次组合的旧闭包。
 */
@Composable
private fun KeyWatchWhileVisible(
    show: Boolean,
    context: Context,
    onSeen: (Int) -> Unit,
) {
    val seenHandler by rememberUpdatedState(onSeen)
    DisposableEffect(show) {
        if (!show) return@DisposableEffect onDispose {}
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
        watch(context, on = true)
        onDispose {
            watch(context, on = false)
            if (registered) runCatching { context.unregisterReceiver(receiver) }
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
