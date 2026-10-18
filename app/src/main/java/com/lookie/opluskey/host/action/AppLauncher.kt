package com.lookie.opluskey.host.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.bridge.AppEntry
import com.lookie.opluskey.bridge.host.BuiltInMessenger
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.RomProfile

/** 拉界面 / 发广播 / 触发外部能力这一族动作的执行器（小窗那一族见 [MiniWindowLauncher]）。 */
internal class AppLauncher(
    private val context: () -> Context?,
    /** 失败通知出口。 */
    private val onFailure: (CommandFailure) -> Unit,
) {
    /** 小窗那一族动作的执行器（`window:` 起法与「把前台收成小窗」共用一套反射降级，见 [MiniWindowLauncher]）。 */
    internal val mini = MiniWindowLauncher(context, onFailure)

    /**
     * `intent:ACTION`：先按界面拉起，拉不起来再当广播发。顺序不能反 ——
     * 清单里打开相机那条就是个界面 Intent，只发广播相机不会开。
     */
    fun openIntent(action: String) {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，intent:$action 跳过")
                return
            }
        try {
            ctx.startActivity(Intent(action).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            XLog.w("intent:$action → 已作为界面拉起")
            return
        } catch (e: Throwable) {
            // 回落这一档是一次隐式广播：Intent 既没 `setPackage` 也没组件，API 26+ 打不到
            //  清单里登记的接收器，**只可能命中动态注册者**；而且没人接收时 `sendBroadcast`
            //  也不抛异常 —— 所以「发出去了」绝不等于「有人收」，本文件里也**没有**一条「已发广播」
            //  的成功日志（正是为了让这一档不显得比实际更可靠）。
            //  留着它的理由：动态注册者那一档仍然有效，去掉等于把这一档一起关掉。
            XLog.w("intent:$action 当界面拉不起来（${e.message}），改发隐式广播（没带包名，多半无人接收）")
        }
        try {
            // 注意：广播上**不挂** `FLAG_ACTIVITY_NEW_TASK`：那是给
            //  `startActivity` 用的，挂在 Intent 上再 `sendBroadcast` 没有任何意义（原样照抄了
            //  上面那条 startActivity 的写法）。无害但误导，去掉。
            ctx.sendBroadcast(Intent(action))
        } catch (e: Throwable) {
            // 界面与广播两条路都没成 ⇒ 这一下确实什么都没发生。原来只记日志，用户侧一片静默。
            XLog.w("发广播失败：$action", e)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /**
     * `app:包名/Activity` 或 `app:包名` —— 拉起一个界面。
     *
     * 目标可以带多开前缀（`999@包名/Activity`，见 `AppTarget`）：那时**解析与启动都必须换成那个
     * 用户的视角** —— 主用户的 `PackageManager` 看不见分身里的包，直接 `ctx.startActivity`
     * 也会在主用户里找组件（表现为「解析不到界面」）。
     */
    fun launchApp(target: String) {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，app:$target 跳过")
                return
            }
        val userId = AppTarget.userId(target)
        val pkg = AppTarget.pkg(target)
        val pm = AppCloneCatalog.packageManagerFor(ctx, userId) ?: ctx.packageManager
        val intent = AppEntry.intentOf(pm, target)
        if (intent == null) {
            // 区分「包还在但没有可启动界面」与「包已不在」，前者提示「已卸载」会误导
            //  分身目标：主用户的 PM 问不出那个包在不在（`getApplicationInfo` 必抛），
            //  这一支**不报「可能已卸载」**—— 上面 `resolveAsUser` 已经按 userId 问过 binder，
            //  拿不到就是「没有可用入口」。误报「已卸载」正是「列表点得动、按下报找不到」的成因之一。
            val installed =
                if (userId != 0) {
                    true
                } else {
                    try {
                        pm.getApplicationInfo(pkg, 0)
                        true
                    } catch (e: Throwable) {
                        false
                    }
                }
            XLog.w("解析不到界面：$target（这个包还在=$installed，user=$userId）")
            onFailure(if (installed) CommandFailure.APP_NO_ENTRY else CommandFailure.APP_MISSING)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            // 主用户与分身**都走** ATMS 的 `startActivityAsUser`：只有这条路能在下发前统一给
            //  intent 挂 `OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER`。不挂的话，**装了分身的应用在主
            //  用户下启动照样会被 ColorOS 问「用哪一份」**（同名包在 0 与 998/999 里各有一份）。
            //  `createContextAsUser` 那条为什么不行见 `AppCloneCatalog`。
            if (!AppLaunchAsUser.start(ctx, intent, userId)) {
                XLog.w("启动被拒：$target（user=$userId）")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
        } catch (e: android.content.ActivityNotFoundException) {
            //  注意：报「已卸载」之前先看一眼包还在不在：显式组件
            //  （`app:包名/Activity`）**被停用 / 组件不存在**时抛的也是 ANFE，
            //  一律报 APP_MISSING（「可能已经卸载了」）会把用户带偏 —— 包明明还在。
            //  判据与上面 `intent == null` 那一支同源（`getApplicationInfo` 查得到就是还在）。
            val installed =
                if (userId != 0) {
                    true
                } else {
                    try {
                        pm.getApplicationInfo(pkg, 0)
                        true
                    } catch (e2: Throwable) {
                        false
                    }
                }
            XLog.w("拉起界面失败：$target（这个包还在=$installed，user=$userId）", e)
            onFailure(if (installed) CommandFailure.APP_NO_ENTRY else CommandFailure.APP_MISSING)
        } catch (e: Throwable) {
            // 最常见的是后台启动限制 / 安全异常，不是「已卸载」—— 报「没执行成功」别误导
            XLog.w("拉起界面失败：$target（user=$userId）", e)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /**
     * `window:包名` —— 用小窗打开（有现成任务就复用，没有才新建一份）。
     *
     *  实现整族搬到了 [MiniWindowLauncher]（`window:` 起法、`zoom:mini` 的两档、ColorOS 小窗反射、
     *  几何验收与动画路线，连同它专用的常量）。本类只留转发口。
     */
    fun launchAppInWindow(target: String) = mini.launchAppInWindow(target)

    /** 把**前台**应用收成小窗（`zoom:mini` 那两档）；[collapsed] = 收起档。实现见 [MiniWindowLauncher]。 */
    fun putForegroundInMiniWindow(collapsed: Boolean = false) = mini.putForegroundInMiniWindow(collapsed)

    // `app:` / `window:` 共用的入口解析已挪到 `AppEntry.intentOf` —— 与「包名 → 入口」的
    //  `AppEntry.resolve` 是同一件事的两级，放一处；本类与小窗那一侧都调它。

    /**
     * 执行 ColorOS 小布快捷指令（`com.coloros.shortcuts`）。
     * 早先「三套通道逐个试、哪个成了算哪个」的判据不成立（`sendBroadcast` 是 fire-and-forget，
     * 没接收者也不抛异常，第一条永远 ok、后两套根本不会被尝试）。现在先问系统哪些 action
     * 真有接收者、**只发第一个命中的那条**（避免同一条指令被执行两次），一条都查不到再全部试
     * （非导出接收器对跨 uid 查询不可见）；日志只说「已发」这一步的事实，指令有没有被执行是小布自己的事。
     */
    fun runShortcut(shortcutId: String) {
        if (shortcutId.isBlank()) return
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，shortcut:$shortcutId 跳过")
                return
            }

        //  注意：**只发一条**：两条 action 都命中时会各发一遍，而同一个
        //  接收器往往同时监听这两条 action ⇒ 指令被执行两次（注释里「避免同一条指令被执行两次」
        //  正好写反了）。既然已经问出「谁有接收者」，就只发给第一个命中的那个。
        //  一条都没查到（非导出接收器对跨 uid 查询不可见）时退回「全部各试一次」——
        //  那种情况下不知道哪条才对，宁可重复也不能不发。
        val resolved = SHORTCUT_ACTIONS.filter { hasReceiver(ctx, it) }
        val channels =
            if (resolved.isNotEmpty()) {
                listOf(resolved.first())
            } else {
                XLog.w("小布快捷指令：没查到任何接收者（可能是非导出接收器），按全部通道各试一次")
                SHORTCUT_ACTIONS
            }

        var sent = 0
        for (action in channels) {
            val intent =
                Intent(action)
                    .setPackage(SHORTCUT_PKG)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 各版本用过不同的 extra key 名，**两条都带上** —— 实现方读哪个都能拿到。
            for (key in SHORTCUT_ID_KEYS) intent.putExtra(key, shortcutId)
            try {
                ctx.sendBroadcast(intent)
                sent++
                XLog.w("已发小布快捷指令：$action（id=$shortcutId）")
            } catch (e: Throwable) {
                XLog.w("发小布快捷指令失败：$action", e)
            }
        }
        if (sent == 0) onFailure(CommandFailure.SHORTCUT_FAILED)
        // 兜底：`sendBroadcast` 在没有接收者时**不抛异常**，所以「发出去了」不等于「有人收」——
        //  查不到任何接收者（`resolved` 为空）时上面会按全部通道各发一次，而结果**无从确认**
        //  （收方不回执）。这条 w 是留给排查的：用户在「快捷指令没反应」时能看到是这一档。
        if (resolved.isEmpty()) {
            XLog.w("小布快捷指令：查不到接收者，已按全部通道各发一次（结果无法确认，id=$shortcutId）")
        }
    }

    /** 这条 action 在 `com.coloros.shortcuts` 里有没有接收者（查不到 / 抛异常都按「没有」）。 */
    private fun hasReceiver(
        ctx: Context,
        action: String,
    ): Boolean =
        try {
            ctx.packageManager
                .queryBroadcastReceivers(
                    Intent(action).setPackage(SHORTCUT_PKG),
                    0,
                ).isNotEmpty()
        } catch (e: Throwable) {
            XLog.d("查小布快捷指令接收者失败（按没有处理）：$action | ${e.message}")
            false
        }

    /**
     * 起一个 ColorOS 系统能力（`oplus:` 动作）；目标组件与 action 见 [RomProfile]。
     */
    fun startOplusFeature(feature: String) {
        val target =
            RomProfile.builtIn(feature) ?: run {
                // 本机 ROM 没验过时落点会照基准实现试（见 `RomProfile.landing`），所以走到这里只剩
                //  两种情形：功能名写错了，或者那一档里根本没登记这个名字 —— 由下面那句日志报出来
                XLog.w("oplus:$feature 没有落点 —— 内置命令档位：${RomProfile.describeCurrent()}")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，oplus:$feature 跳过")
                return
            }
        // Messenger 绑定型（场景表 type=service 的大部分）必须 bind 后发 Message
        if (target.message != null) {
            sendMessengerMessage(ctx, feature, target)
            return
        }
        val intent = buildFeatureIntent(target)
        fillExtras(intent, target.extras)

        val short = "${target.owner}/${target.service.substringAfterLast('.')}"
        // 界面型目标：非 Activity 的 Context 起界面必须带 NEW_TASK，否则 ActivityNotFoundException
        if (target.activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(intent)
                XLog.w("oplus:$feature → startActivity($short)")
            } catch (e: Throwable) {
                XLog.w("起 oplus:$feature 的界面失败：${e.javaClass.simpleName}: ${e.message}")
                onFailure(CommandFailure.UNKNOWN)
            }
            return
        }

        // 目标声明了 special-use 前台类型时先走前台 —— 那是它在清单里给自己定的起法，
        // 普通 startService 会被后台启动限制拒。没声明的反过来先试 startService：
        // 对方不调 startForeground 时，前台方式会让**它的进程**抛
        // ForegroundServiceDidNotStartInTimeException。
        var last: Throwable? = null
        for (foreground in if (target.foreground) FOREGROUND_FIRST else SERVICE_FIRST) {
            try {
                if (foreground) ctx.startForegroundService(intent) else ctx.startService(intent)
                XLog.w("oplus:$feature → ${if (foreground) "startForegroundService" else "startService"}($short)")
                return
            } catch (e: Throwable) {
                last = e
                XLog.d("oplus:$feature 的 ${if (foreground) "前台" else "普通"}起法被拒：${e.message}")
            }
        }

        // 两种起法都被拒 ⇒ 退一层显式组件（口径与边界见 [tryComponentFallback]）。
        // 退路成功（返回 null）就在这里收手；否则拿最后那个错误报失败。
        val finalError = tryComponentFallback(ctx, feature, target, last) ?: return
        XLog.w("起 oplus:$feature 失败：${finalError.javaClass.simpleName}: ${finalError.message}")
        onFailure(CommandFailure.UNKNOWN)
    }

    /**
     * 结束一个「按下开始、松手结束」的 ColorOS 能力（此刻只有灵感语音，见 [RomProfile.BuiltIn.stopExtras]）。
     *
     *  落点表里没登记结束形态的能力直接返回 —— 这类动作按一下就完事、没有「结束」这回事，
     *  所以这个函数的空转是**正常**的，不是配置写错。
     *
     *  失败**不弹提示**（与起采集相反）：收尾是尽力而为，弹一句「结束失败」只会让人以为
     *  刚才那一段也没录上。退路也只有「换一种起法」，不走 `tryComponentFallback` —— 那一步是
     *  为「起不来」准备的，而结束的目标此刻还在跑，显式组件反而容易命中不到它的 action 分支。
     */
    fun stopOplusFeature(feature: String) {
        val target = RomProfile.builtIn(feature)?.takeIf { it.stopExtras.isNotEmpty() } ?: return
        if (target.message != null || target.activity) {
            // 绑定型 / 界面型没有「再发一条就是结束」的形态；表里登记错了只记一行，不去猜
            XLog.w("oplus:$feature 有 stopExtras 但落点是绑定 / 界面型，结束这一步跳过")
            return
        }
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，结束 oplus:$feature 跳过")
                return
            }
        val intent = buildFeatureIntent(target)
        // 与起采集时同一套 extra，只把 stopExtras 盖上（灵感语音 = `longPressEventType` 改成 2）
        fillExtras(intent, target.extras + target.stopExtras)
        val short = "${target.owner}/${target.service.substringAfterLast('.')}"
        for (foreground in if (target.foreground) FOREGROUND_FIRST else SERVICE_FIRST) {
            try {
                if (foreground) ctx.startForegroundService(intent) else ctx.startService(intent)
                XLog.w("结束 oplus:$feature → ${if (foreground) "startForegroundService" else "startService"}($short)")
                return
            } catch (e: Throwable) {
                XLog.d("结束 oplus:$feature 的 ${if (foreground) "前台" else "普通"}起法被拒：${e.message}")
            }
        }
        // 两种都被拒：这一段可能还在录 —— 值得一行 w（不弹提示，理由见上）
        XLog.w("结束 oplus:$feature 失败：两种起法都被拒（那一段可能还在采集）")
    }

    /**
     * Messenger 绑定型服务：**bind 后发一条 `Message`**（`what=1` 单击 / `2` 长按）。
     *
     * 实现在 `bridge/host/BuiltInMessenger` —— 手电筒那条也走它，两边各写一份时先漂的
     * 一定是边界处理（超时 / bind 被拒 / 失败上报），见那边的说明。
     */
    private fun sendMessengerMessage(
        ctx: Context,
        feature: String,
        target: RomProfile.BuiltIn,
    ) {
        BuiltInMessenger.send(ctx, target, "oplus:$feature") { onFailure(CommandFailure.UNKNOWN) }
    }

    /**
     * 小布识屏：配置里仍写 KSU 那串 shell 命令（老配置不能失效），执行换成
     * [startOplusFeature] 的精确调用。
     */
    fun startSidebar() = startOplusFeature(RomProfile.NAME_SCREEN_OCR)

    private companion object {
        /** 小布快捷指令的包名。 */
        const val SHORTCUT_PKG = "com.coloros.shortcuts"

        /**
         * ColorOS 快捷指令的触发 action —— 各版本用过好几套名字（旧的是 `oplus` 命名空间）。
         * 见 [runShortcut]：先按「有接收者」筛，筛不出来才全部各发一次。
         */
        val SHORTCUT_ACTIONS =
            listOf(
                "com.coloros.shortcuts.action.EXECUTE_SHORTCUT",
                "com.oplus.shortcuts.action.EXECUTE_SHORTCUT",
            )

        /** 指令 id 在广播里的 extra key —— 各版本用过这两个名字，两条都带。 */
        val SHORTCUT_ID_KEYS = listOf("shortcut_id", "shortcutId")

        /** [startOplusFeature] 的尝试顺序：true = 前台起法。 */
        val FOREGROUND_FIRST = listOf(true, false)
        val SERVICE_FIRST = listOf(false, true)

        // 「等服务连上的上限」不在本类的 companion 里：它跟着 `BuiltInMessenger.TIMEOUT_MS` 走
        // （手电筒那条也吃同一个值），别在这儿再存一份。
    }
}

/**
 * `action` 起法失败之后试一次**显式组件**，返回这一次的错误（成功返回 `null`）；试不了就把 [last]
 * 原样退回去。
 *
 *  为什么要有这一层：两次起法走的是 `action + setPackage`（由清单 intent-filter 解析），清单对不上 /
 *  组件被停用时它必然失败，而显式组件能直接命中 —— 与「逐层降级」同一条纪律。
 *
 *  为什么不能无脑退：[RomProfile.BuiltIn] 的 `service` **不总是类名**（有的档把 action 串写在同一格
 *  里，见 `RomProfile` 的 `RECORDING`），拿它去拼 [`ComponentName`] 只会得到一个永远解析不到的组件，
 *  退路就成了「只会多打一条失败日志」的空动作。所以先判它像不像类名：含 `.` 且每一段都是合法 Java
 *  标识符；只有 `action` 那一支才需要退路，`action` 为空时第一次起法本就是显式组件。
 *
 *  写成**顶层**函数而不是 `AppLauncher` 的成员：它只吃入参、不吃对象状态，而那个对象的函数条数已经
 *  贴着 detekt `TooManyFunctions` 那一档。
 */
private fun tryComponentFallback(
    ctx: Context,
    feature: String,
    target: RomProfile.BuiltIn,
    last: Throwable?,
): Throwable? {
    val service = target.service
    val looksLikeClassName =
        target.action != null &&
            service.contains('.') &&
            service.split('.').all { part ->
                part.isNotEmpty() &&
                    Character.isJavaIdentifierStart(part[0]) &&
                    part.drop(1).all { Character.isJavaIdentifierPart(it) }
            }
    if (!looksLikeClassName) return last
    XLog.w(
        "oplus:$feature 的 action 起法失败（${last?.javaClass?.simpleName}: ${last?.message}），" +
            "改退显式组件 ${target.owner}/$service 再试",
    )
    return try {
        val explicit =
            Intent()
                .setComponent(ComponentName(target.owner, service))
                .apply {
                    //  退路只换「怎么定位这个服务」，**载荷不能丢**：少了 triggerType /
                    //  longPressEventType 这类 extra，服务照样被起、但已经不是用户要的那一下，
                    //  而日志还写着「退路成功」。
                    target.action?.let { action = it }
                    putExtrasOf(target.extras, feature)
                }
        if (target.foreground) ctx.startForegroundService(explicit) else ctx.startService(explicit)
        XLog.w("oplus:$feature → 显式组件退路成功（${target.owner}/${service.substringAfterLast('.')}）")
        null
    } catch (e: Throwable) {
        XLog.w("oplus:$feature 的显式组件退路也失败：${e.javaClass.simpleName}: ${e.message}")
        e
    }
}

/**
 * 把场景表里那几样 extras 原样搬进 Intent。
 *
 *  正路（action + setPackage）与退路（显式组件）共用同一份载荷 —— 少了 `triggerType` /
 *  `longPressEventType` 这类项，服务照样被起、但已经不是用户要的那一下。
 *
 *  抽成顶层函数只为让 [tryComponentFallback] 的圈复杂度别顶破 detekt 那一档。
 */
private fun Intent.putExtrasOf(
    extras: Map<String, Any>,
    feature: String,
): Intent =
    apply {
        extras.forEach { (k, v) ->
            when (v) {
                is String -> putExtra(k, v)
                is Int -> putExtra(k, v)
                is Long -> putExtra(k, v)
                is Boolean -> putExtra(k, v)
                is Float -> putExtra(k, v)
                else -> XLog.w("oplus:$feature 的 extra $k 类型 ${v.javaClass.simpleName} 搬不过去")
            }
        }
    }

/**
 * 造出「第一次」要发的那个 Intent（起采集与结束两条路共用）。
 *
 *  有 action 的按系统自己的写法发 —— `action + setPackage`，由清单 intent-filter 解析；
 *  场景表里 `scene_owner` + action 本来就是系统发给该服务的原样。只有组件名（无 action）
 *  的才用显式组件。失败之后的退路不在这里，在 [AppLauncher.startOplusFeature]
 *  （试 [tryComponentFallback] 的显式组件）。
 *
 *  提成顶层函数只为让 [AppLauncher] 的函数数别顶破 detekt；本函数不吃对象状态。
 */
private fun buildFeatureIntent(target: RomProfile.BuiltIn): Intent =
    if (target.action != null) {
        Intent(target.action).setPackage(target.owner)
    } else {
        Intent().setComponent(ComponentName(target.owner, target.service))
    }

/**
 * 把落点表里登记的 extra 写进 Intent（`Int` 原样、其余转字符串）。
 *
 *  提成顶层函数是因为**起 / 停两条路都要它**（[AppLauncher.startOplusFeature] 与
 *  [AppLauncher.stopOplusFeature]）—— 各写一遍时先漂的必然是类型判断那一档，
 *  而那个类的函数条数已经贴着 detekt `TooManyFunctions` 那一档。
 */
private fun fillExtras(
    intent: Intent,
    extras: Map<String, Any>,
) {
    for ((key, value) in extras) {
        when (value) {
            is Int -> intent.putExtra(key, value)
            else -> intent.putExtra(key, value.toString())
        }
    }
}
