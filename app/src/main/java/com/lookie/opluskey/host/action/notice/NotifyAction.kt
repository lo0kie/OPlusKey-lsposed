package com.lookie.opluskey.host.action.notice

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.host.action.CommandFailure

/**
 * 「发一条通知」（`notify:正文`）—— 走**宿主应用自己的通知**：通知中心里那一条，不是屏幕上闪一下的
 * 提示（那是 `ModuleWire.ACTION_TOAST` 那条路）。
 *
 * ## 为什么挂在宿主名下
 *
 *  动作在宿主进程里执行（AGENTS 五之二、6），`NotificationManager` 用哪个 Context 发，通知就挂在
 *  哪个包名下 —— 这里拿到的正是宿主（`com.oplus.gesture`）那一份。模块自己那个包在宿主进程里
 *  既没有通知渠道也没有界面，所以**标题**取宿主应用名（`PackageManager` 取，已由宿主本地化），
 *  **正文**就是 token 里那一段。这就是「以宿主的身份发一条通知」的全部含义。
 *
 * ## 渠道与图标
 *
 *  · 渠道固定 `opluskey_notice`、`IMPORTANCE_DEFAULT`（有提示音、不弹浮窗）。渠道名同样用宿主
 *    应用名 —— 它只在系统设置里露一次，自造一句文案就得跟着翻译，收益不值那个成本。
 *  · 小图标用宿主的 `applicationInfo.icon`。**不能**用模块自己的 `R.drawable.*`：那串 id 是按
 *    宿主的资源表解析的，落在那里是另一个资源（或压根不存在），通知栏会拿到一张错的图。
 *  · 同一段正文落在同一个通知 id 上（`hashCode`）：再按一次是**替换**那一条，不会堆一串
 *    一模一样的通知。
 *
 * ## 发不出去时
 *
 *  宿主在部分版本上没拿到 `POST_NOTIFICATIONS`（或用户关了它的通知）—— 这一档只记日志 + 走
 *  [onFailure]：用户至少知道「键生效了、只是系统不让发通知」，而不是以为键坏了。
 */
internal object NotifyAction {
    /** 通知渠道 —— 固定 id。改它等于在系统设置里多出一条旧渠道，旧的一条谁也不会去删。 */
    private const val CHANNEL_ID = "opluskey_notice"

    /** 正文为空时用哪一个通知 id（那条通知只有标题，同一 id 上反复替换）。 */
    private const val EMPTY_TEXT_ID = 0x0F1

    /** 渠道建过没有 —— 每次按键都建一遍是白跑一次 binder。 */
    @Volatile
    private var channelReady = false

    /**
     * 发一条通知。
     *
     * @param action `notify:` 那一档，正文在 [Action.Notify.message] 里
     * @param onFailure 发不出去时报出去（拿不到服务 / 通知权限关着 / 建通知时抛异常）
     */
    fun run(
        context: Context?,
        action: Action.Notify,
        onFailure: (CommandFailure) -> Unit,
    ) {
        val ctx =
            context ?: run {
                XLog.w("发通知跳过：没有 Context")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
        val nm =
            ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: run {
                XLog.w("发通知跳过：拿不到 NotificationManager")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
        //  宿主自己的通知权限不归模块管，只能读一次再照实说一句 ——
        //  这一档**不抛也不静默**：静默的话用户看到的是「设了发通知，按下去什么都没有」。
        if (!postAllowed(ctx, nm)) {
            XLog.w("发通知跳过：宿主（${ctx.packageName}）不持有 POST_NOTIFICATIONS，或它的通知被关掉了")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        try {
            val title = titleOf(ctx)
            ensureChannel(nm, title)
            val text = action.message
            val builder =
                Notification
                    .Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(iconOf(ctx))
                    .setContentTitle(title)
                    .setAutoCancel(true)
            if (text.isNotEmpty()) {
                builder.setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            }
            post(nm, if (text.isEmpty()) EMPTY_TEXT_ID else text.hashCode(), builder.build())
            //  判据级：这一行是「按下去到底发没发」的唯一凭据（通知栏里看不见时先看它）。
            XLog.w("已按宿主身份发通知：${ctx.packageName}｜$title｜${text.ifEmpty { "（无正文）" }}")
        } catch (e: Throwable) {
            //  与其余动作同一条纪律：执行在宿主进程里，抛出去就是宿主崩（`runAction` 有兜底，
            //  但这一行要自己带上「是发通知这一步断的」）。
            XLog.w("发通知失败：${e.javaClass.simpleName}: ${e.message}", e)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /**
     * 这个身份**现在发不发得出去通知** —— 两道闸都要过。
     *
     *  · Android 13 起的 `POST_NOTIFICATIONS`：查的是 [context] **那个包**（宿主），不是模块自己。
     *    通知挂在谁名下就看谁，`checkSelfPermission` 传的也是这一份 Context。
     *  · 「用户在系统设置里把它的通知关了」（`areNotificationsEnabled`）。权限不给时这一位通常也是
     *    false，但两件事分开判，日志才答得出「是谁关的」。
     *
     *  读不到（老系统没有该权限 / 服务异常）一律按「能发」处理：这里只是**拦明显发不出去的**，
     *  真失败还有 [run] 那一层的 catch 兜着 —— 不能因为读不到权限就把功能整条掐掉（AGENTS 五、5）。
     */
    private fun postAllowed(
        context: Context,
        nm: NotificationManager,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                runCatching {
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                }.getOrDefault(true)
            if (!granted) return false
        }
        return runCatching { nm.areNotificationsEnabled() }.getOrDefault(true)
    }

    /**
     * 真正把那条通知交出去 —— 单独一支只为挂这一行注解（注解放不进表达式，只能挂一支函数）。
     *
     *  **为什么必须抑制**：Lint 的 `NotificationPermission` 只看「本模块清单里有没有声明
     *  `POST_NOTIFICATIONS`」，它没有「调用方身份」这个概念 —— 而这里发通知的是**宿主**
     *  （模块从不以自己的身份发通知，也没有自己的通知渠道）。实测过另一条路：在调用点跑
     *  `checkSelfPermission(POST_NOTIFICATIONS)` 并提前返回，Lint 照样红 —— 它不认运行时判据。
     *
     *  真正的判据是 [postAllowed]：它查的正是 `ctx`（宿主）那一份，真机上宿主这条权限是
     *  `SYSTEM_FIXED|GRANTED_BY_DEFAULT`；拿不到时只报一句、不抛（见 [run] 的注释）。
     *  在模块清单里声明这个权限能糊弄过 Lint，但那等于用一个模块自己永不放的权限换一句静默。
     */
    @SuppressLint("NotificationPermission")
    private fun post(
        nm: NotificationManager,
        id: Int,
        notification: Notification,
    ) {
        nm.notify(id, notification)
    }

    /**
     * 渠道**缺了才**建（名字取宿主应用名，见类注释）。
     *
     *  不能只建一次：用户在系统「通知设置」里把这个渠道删掉之后，`notify` 会**静默不显示**
     *  （渠道不存在 ⇒ 那一条被丢掉），而调用方照旧报「已发出」—— 用户看到的是「提示功能坏了」，
     *  日志上却一切正常。所以在建过一次之后仍要问一句「它还在不在」：那是一趟进程内查询，
     *  比每次都 `createNotificationChannel` 便宜，也比「永远不查」可靠。
     */
    private fun ensureChannel(
        nm: NotificationManager,
        name: String,
    ) {
        if (channelReady && nm.getNotificationChannel(CHANNEL_ID) != null) return
        if (channelReady) XLog.w("通知渠道 $CHANNEL_ID 不在了（用户在系统设置里删过）⇒ 重新建一条")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT),
        )
        channelReady = true
    }

    /**
     * 小图标 —— 宿主的启动图标。
     *
     * 兜底用框架里那颗通用的「信息」图标：`applicationInfo.icon` 为 0（没有启动图标）时，
     * 通知照样发得出去，只是图标是系统默认那张。
     */
    private fun iconOf(context: Context): Int = context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_notify_more

    /** 标题 —— 宿主应用名；取不到（包信息读不出来）就退回包名。 */
    private fun titleOf(context: Context): String =
        runCatching { context.packageManager.getApplicationLabel(context.applicationInfo).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: context.packageName
}
