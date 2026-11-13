package com.lookie.opluskey.host.action.notice

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.FluidCard
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.host.action.CommandFailure
import java.io.File

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
     * 四十二之三）。值是**资源包内的相对路径**，包里只有五个素材：
     *
     * ```
     * assets/images/op_ring.json / op_silence.json / op_vibrate.json   ← 三条 lottie（响铃 / 静音 / 振动）
     * assets/images/op_ic_silence.svg                                  ← 静音那道斜杠
     * assets/images/precise_notification_icon.png                      ← 模板自己的默认图标（通用「通知」小图）
     * ```
     *
     *  ⚠️ **必须带 `assets/images/` 这一层**。宿主自己弹响铃胶囊时传的就是
     *  `assets/images/op_silence.json`（`i9.e0.t`），渲染端拿这个值**当路径查资源表**；
     *  只写裸文件名（`precise_notification_icon.png`）查不到 —— 表现是**胶囊左边一直空着**，
     *  而且不报任何错（真机实际栽在这里，2026-11-13 之前这一档从来没有图标）。
     *
     *  取最后那张：前四条都带「响铃 / 静音 / 振动」的语义，而这一档是通用文字胶囊，
     *  挂一颗铃声图标只会让人以为按到了响铃。**实际发出去的不是这张** —— 见 [iconUri]：
     *  我们自己那张 png 走 `content://` 递给渲染端，这里只是写不出来时的兜底。
     */
    private const val LOGO = "assets/images/precise_notification_icon.png"

    /**
     * **配 [LOGO] 的那一位**：模板里两个图标槽互斥 —— `showA=true` 走 `lottie` 槽（只认 `.json`），
     * `false` 走 `image` 槽（只认 png / svg）。PNG 必须走 image 槽，所以这里是 `false`。
     */
    private const val SHOW_ANIM = false

    /**
     * `logo` 也可以是 **`content://`** —— 系统自己弹相机胶囊时就是这么传的
     * （`content://com.oplus.gesture.FileShareProvider/share_images/2131232270`，实测能显示）。
     *
     * 那个 provider 就是 `androidx.core.content.FileProvider` 的子类，`share_images` 映射到
     * 宿主的 `files/share_images/`，uri 最后一段就是**文件名**。而 `fluid:` 的代码本就跑在宿主
     * 进程里 —— 有那个目录的写权限。所以：把自己的 png 写进去，`logo` 给对应的 uri，
     * 图标就是我们的了，不用改包、不用 hook。
     */
    private const val ICON_AUTHORITY = "${HostApp.PACKAGE}.FileShareProvider"

    /** 画胶囊的那个进程（授权图标 uri 用）。 */
    private const val SYSTEM_UI = "com.android.systemui"

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
            //  没传图标（`fluid:正文`）就**连 logo 都不给**：胶囊左边那一位空着。
            //  以前这里会退回一张默认图标（界面层画的 `okey-fluid.png`，再退一步是模块 assets 里
            //  那张），于是「没选图标」看起来与「选了默认那颗」一模一样 —— 用户分不出自己到底
            //  选没选，也没法让它干脆不带图。模板那一段认空串，渲染端就不画那一格。
            //
            //  传了图标但文件还没渲染出来（刚导入配置、还没进过设置页）：仍退回包里那张通用图 ——
            //  用户是**选过**的，给一张通用图比空着更接近他的意思。
            val logo = if (action.icon.isEmpty()) "" else iconUri(ctx, action.icon) ?: LOGO
            entry.send.invoke(entry.instance, logo, text, SHOW_ANIM)
            //  判据级：胶囊到底弹没弹，下一步看宿主自己那行
            //  `ActionKeyFluidCloudManager: showSendling onIntentResultCodeCallBack--:… i:…`
            //  （那是卡服务的回执码，`i` 非 0 就是被拒）。
            XLog.w("已请宿主弹流体云：$text（图标=${logo.ifEmpty { "无" }}）")
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
     * 把图标放进宿主那个 provider 的共享目录，返回它的 `content://` uri。
     *
     *  **图只由界面层画**（`ui/model/icon/FluidCardArt`：图标源是模块已引用的 Tabler 字形，
     *  只有带 Compose 的那一侧画得出来），这里只负责「找到那一张、把它的 uri 组出来」。
     *
     *  文件不在（还没渲染过这一颗）返回 `null`，退路由调用方定 —— 见 [run] 里 `logo` 那一处。
     */
    private fun iconUri(
        ctx: Context,
        icon: String,
    ): String? =
        try {
            val file = File(File(ctx.filesDir, FluidCard.SHARE_DIR), FluidCard.nameOf(icon))
            //  界面层还没渲染过这一颗：交给调用方，这里不自己造图
            if (!file.isFile) return null
            val uri = "content://$ICON_AUTHORITY/${FluidCard.SHARE_DIR}/${file.name}"
            // FileProvider 是**未导出**的：能读它的只有被显式授权的包。读图解码的是 SystemUI
            //  （胶囊就是它画的），不给这一下就是 `Permission Denial … not exported`。
            runCatching {
                ctx.grantUriPermission(SYSTEM_UI, Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { XLog.w("把胶囊图标的 uri 授权给 SystemUI 失败：${it.javaClass.simpleName}", it) }
            uri
        } catch (e: Throwable) {
            XLog.w("写不出胶囊图标，这一次用包里那张", e)
            null
        }

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
