package com.lookie.opluskey.host.action.notice

import android.content.Context
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.host.action.CommandFailure

/**
 * 「弹一条流体云」（`fluid:正文`）—— 状态栏 / AOD 上那颗胶囊，**不是**通知中心里的一条通知。
 *
 * ## 为什么只能借宿主的 SDK
 *
 *  ColorOS 的流体云只有「Seedling 卡片」这一条实现，卡片由**已注册的 seedling 宿主**向卡服务
 *  提交；`SystemUI` 的 `SEEDLING_SERVICE` 那条 Messenger 通道只认四类内置胶囊
 *  （响铃 / 免打扰 / 手电筒 / 媒体），文案写死在 `SystemUI` 里、塞不进自定义文字
 *  （反编译结论见 `docs/device/actions/feedback.md` 四十二）。所以这一档直走宿主自己那套
 *  ——`i9.e0`（`ActionKeyFluidCloudManager`）就是它弹响铃胶囊用的入口。
 *
 * ## 调的是哪一个方法
 *
 * ```
 * i9.e0.k(context)                                  ← 取实例（宿主自己的那一个）
 * i9.e0.q(String logoPath, String text, boolean showA)   ← 纯发送：组 options → 组 JSON →
 *                                                            SeedlingTool.sendSeedling(...)
 * ```
 *
 *  `q` 里没有屏幕 / 锁屏 / AOD 的闸门（那些在 `v` 那一层），所以按键按下去就发，显示与否交给
 *  系统 —— 这正是「用户按了键」这一档该有的口径。载荷是 `initData{title, logo, showA}`，
 *  [logoPath] 指向**宿主 APK 自己的 assets**（不能给我们的路径：卡服务按宿主的资源读）。
 *
 * ## 换 ROM 就换名
 *
 *  `i9.e0` 与 `q` 都是混淆名，跨 ROM 会整体改名 —— 所以写成候选表 + 逐层降级（AGENTS 五、3）：
 *  落空只记一行 W 并报失败，不去猜别的名字。
 */
internal object FluidAction {
    /**
     * 「侧键流体云」管理器的类名候选（8.6.0 实测就是第一个）。
     *
     *  换一版 ROM 时加一行即可；全落空只表现为「找不到类」，不影响按键接管。
     */
    private val MANAGERS = listOf("i9.e0")

    /** 取实例的静态入口：`k(Context)`。 */
    private const val ACCESSOR = "k"

    /** 发送入口：`q(String logoPath, String text, boolean showA)`。 */
    private const val SEND = "q"

    /**
     * 胶囊左侧那颗图标 —— 就是卡片模板里的 `{{logo}}`（模板见 `docs/device/actions/feedback.md`
     * 四十二之三）。路径相对**卡片资源包** `assets/ActionKeyTip.upk` 的 `assets/images/`，
     * 所以只写文件名；包里只有五个素材：
     *
     * ```
     * op_ring.json / op_silence.json / op_vibrate.json   ← 三条 lottie（响铃 / 静音 / 振动）
     * op_ic_silence.svg                                  ← 静音那道斜杠
     * precise_notification_icon.png                      ← 模板自己的默认图标（通用「通知」小图）
     * ```
     *
     *  取最后那张：前四条都带「响铃 / 静音 / 振动」的语义，而这一档是通用文字胶囊，
     *  挂一颗铃声图标只会让人以为按到了响铃。
     */
    private const val LOGO = "precise_notification_icon.png"

    /**
     * **配 [LOGO] 的那一位**：模板里两个图标槽互斥 —— `showA=true` 走 `lottie` 槽（只认 `.json`），
     * `false` 走 `image` 槽（只认 png / svg）。PNG 必须走 image 槽，所以这里是 `false`。
     */
    private const val SHOW_ANIM = false

    /**
     * 弹一条胶囊。
     *
     * @param onFailure 找不到宿主入口 / 调用抛异常时报出去（这一档没有回执可读：
     *   `sendSeedling` 的结果码写在宿主自己的日志里，见类注释下方那条判据行）
     */
    fun run(
        context: Context?,
        action: Action.Fluid,
        onFailure: (CommandFailure) -> Unit,
    ) {
        val ctx =
            context ?: run {
                XLog.w("弹流体云跳过：没有 Context")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
        val text = action.message
        if (text.isEmpty()) {
            //  手改配置可能写成空的（界面那边 `isBarePrefix` 已经把「只有前缀」判成坏配置）
            XLog.w("弹流体云跳过：正文是空的")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        try {
            val entry = resolve(ctx)
            if (entry == null) {
                XLog.w("弹流体云失败：宿主里没找到 ${MANAGERS.joinToString()} 的 $ACCESSOR/$SEND（ROM 改过名？）")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
            entry.send.invoke(entry.instance, LOGO, text, SHOW_ANIM)
            //  判据级：胶囊到底弹没弹，下一步看宿主自己那行
            //  `ActionKeyFluidCloudManager: showSendling onIntentResultCodeCallBack--:… i:…`
            //  （那是卡服务的回执码，`i` 非 0 就是被拒）。
            XLog.w("已请宿主弹流体云：$text")
        } catch (e: Throwable) {
            //  执行在宿主进程里：抛出去就是宿主崩（`runAction` 有兜底，但这一行要自己说清断在哪）
            XLog.w("弹流体云失败：${e.javaClass.simpleName}: ${e.message}", e)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /** 宿主那边的入口四件套：实例，以及那个 `q` 方法。 */
    private class Entry(
        val instance: Any,
        val send: java.lang.reflect.Method,
    )

    /**
     * 走一遍候选表，取第一个完整的入口。
     *
     *  用 `firstNotNullOfOrNull` 而不是手写循环 + `continue`：候选表变长时那一串 `continue`
     *  会撞上 detekt 的跳转上限，而这里本来就只是「第一个能用的」。
     */
    private fun resolve(ctx: Context): Entry? = MANAGERS.firstNotNullOfOrNull { name -> entryOf(ctx, name) }

    /**
     * 一个候选 → 入口；缺哪一环只记一行 W（AGENTS 五、2：说清断在类、还是方法、还是取实例）。
     */
    private fun entryOf(
        ctx: Context,
        name: String,
    ): Entry? {
        val cls = Reflect.findClass(ctx.classLoader, name) ?: return null
        val accessor = Reflect.method(cls, ACCESSOR, Context::class.java)
        val send =
            Reflect.method(
                cls,
                SEND,
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType!!,
            )
        val instance = accessor?.let { runCatching { it.invoke(null, ctx) }.getOrNull() }
        if (instance == null || send == null) {
            XLog.w("$name 上取不到实例或缺 $SEND(String, String, boolean) —— 换 ROM 之后这一档改名了")
            return null
        }
        send.isAccessible = true
        return Entry(instance, send)
    }
}
