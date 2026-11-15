package com.lookie.opluskey.host.action.notice

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionReading
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.FluidCard
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.resolveAppName
import com.lookie.opluskey.core.model.resolveTargetName
import com.lookie.opluskey.core.present.appTargetLabel
import com.lookie.opluskey.core.present.commandTitleOf
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
        //  没传图标（`fluid:正文`）就**连 logo 都不给**：胶囊左边那一位空着。
        //  以前这里会退回一张默认图标（界面层画的 `okey-fluid.png`，再退一步是模块 assets 里
        //  那张），于是「没选图标」看起来与「选了默认那颗」一模一样 —— 用户分不出自己到底
        //  选没选，也没法让它干脆不带图。模板那一段认空串，渲染端就不画那一格。
        val logo = if (action.icon.isEmpty()) "" else FluidCard.nameOf(action.icon)
        runText(context, action.message, logo, onFailure)
    }

    /**
     * 弹一条**指定文案与图标文件**的胶囊 —— 「执行时提示」走流体云时用的就是它。
     *
     *  与 [run] 的差别只在图标从哪来：`fluid:` 的图标是用户在命令里点选的字形（id 存在命令里），
     *  而执行时提示的图标取**被执行的那个动作**自己的字形 —— 界面层按
     *  [FluidCard.actionNameOf] 预渲染成 png，这里按同一个名字找回来。
     *
     * @param logoFile `files/share_images/` 下的文件名；空串 = 不要图标。
     *   传了名字但文件不在（还没渲染过 / 刚导入配置）时退回包里那张通用图 —— 用户**要**图标，
     *   给一张通用图比空着更接近他的意思。
     */
    fun runText(
        context: Context?,
        text: String,
        logoFile: String,
        onFailure: (CommandFailure) -> Unit,
    ) {
        val ctx =
            context ?: run {
                XLog.w("弹流体云跳过：没有 Context")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
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
            val logo = if (logoFile.isEmpty()) "" else iconUri(ctx, logoFile) ?: LOGO
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
     *  文件不在（还没渲染过这一颗）返回 `null`，退路由调用方定 —— 见 [runText] 里 `logo` 那一处。
     *
     * @param fileName `files/share_images/` 下的文件名（`fluid:` 走 `FluidCard.nameOf`，
     *   执行时提示走 `FluidCard.actionNameOf`）
     */
    private fun iconUri(
        ctx: Context,
        fileName: String,
    ): String? =
        try {
            val file = File(File(ctx.filesDir, FluidCard.SHARE_DIR), fileName)
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

/**
 * 「执行时提示」（`GlobalConfig.toastOnRun`）里那行字：**这次执行的是哪个动作**。
 *
 *  名字与界面上那一格同一来源（[ActionCatalog] 是**纯数据** —— 只有取值 / 标题 / 宿主 drawable 名，
 * 不引 androidx，宿主进程取它不会把界面那一层拉进来），免得出现「界面上叫小布识屏、提示里换个说法」。
 * 认不出来的取值（手改过配置文件）原样显示 —— 与界面 `ui/model/CommandText.stepTitle` 同一条兜底。
 *
 *  用的是 [commandTitleOf] —— 全项目唯一那一份「一个动作怎么念」：入口格带父名
 *  （`系统开关: WIFI - 打开`），与步骤行、命令卡摘要、系统场景那一格说的是同一句。
 *  以前这条路上另有一份 `actionFinalTitle`，同一个动作因此在提示里和步骤行里两个样子。
 *
 *  返回的是**整句 + 拆开的三段**（`ActionReading`）而不是一个字符串：模板里的 `$action` 要整句，
 *  `$parent` / `$name` / `$state` 要拆开的各段，而两者由 `CommandTitle.reading` 一次算出来。
 *
 *  **「无操作」在这一层不弹**（判在 `GestureExec.noticeIfNeeded`，不在这里）：这个函数只管
 *  把句子念出来，`None` 念出来就是「无操作」四个字。该不该弹是提示这个位置的规矩。
 */
internal fun actionNoticeReading(
    context: Context?,
    action: Action,
    /**
     * 「打开应用」那一族的显示名（`Config.appNames`，键是 `AppTarget.identity`）——
     * 快捷方式的「扫一扫」这类名字**只在配置里**，token 里不再带（见 `AppTarget` 的类注释）。
     * 它是名字优先级链的第二档（[resolveAppName]），不是第一档：现查 `PackageManager` 更准，
     * 只有应用已经卸载时这一份才有用。
     */
    appNames: Map<String, String> = emptyMap(),
): ActionReading =
    commandTitleOf(action).reading(
        action = action,
        // 顺序不在这里：`resolveAppName` / `resolveTargetName` 是唯一声明处（现查系统 →
        //  落盘那份 → 老 `|名字` → 包名）。这里只回答「宿主这一侧那两档拿什么」——
        //  与界面 `ui/AppNaming` 同一份顺序，两处否则会像以前那样各自排一遍、还排反了。
        appName = { a ->
            when (a) {
                //  整个应用 / 某个界面：`微信` / `微信(LauncherUI)`。界面那一截由
                //  「具体是哪一个」补上（与界面那边 `appTargetLabelOf` 同一个拼法）。
                is Action.App -> {
                    appTargetLabel(
                        hostAppName(context, a.target, appNames),
                        window = false,
                        AppTarget.activity(a.target)?.substringAfterLast('.'),
                        stockAnim = a.stockAnim,
                    )
                }

                //  以小窗打开：动词只有「打开应用」一个词，小窗是目标的注记（`微信(小窗)`）
                is Action.AppWindow -> {
                    appTargetLabel(
                        hostAppName(context, a.target, appNames),
                        window = true,
                        AppTarget.activity(a.target)?.substringAfterLast('.'),
                    )
                }

                //  快捷方式：应用名 + 它自己的名字（`微信(扫一扫)`）。
                //  应用名**只按包名取** —— `包名/快捷方式id` 那个身份在名字表里存的是
                //  **快捷方式名**，拿它当应用名会写成「扫一扫（扫一扫）」。
                is Action.AppShortcut -> {
                    val target = a.appTarget.orEmpty()
                    val app = hostAppName(context, AppTarget.format(AppTarget.pkg(target), AppTarget.userId(target)), appNames)
                    val own = resolveTargetName(target, draft = null) { id -> appNames[id] }
                    appTargetLabel(app, window = a.window, own)
                }

                //  结束应用（`proc:stop:`）：目标直接就是包名（没有 `/界面`、也没有分身前缀）
                is Action.Process -> {
                    hostAppName(context, a.pkg, appNames)
                }

                else -> {
                    ""
                }
            }
        },
        // 认不出来时用**规范化后的** token（界面那边用配置里那串原始写法）
        fallback = action.token,
    )

/**
 * 宿主侧那两个名字来源 —— 顺序由 [resolveAppName] 定，这里只回答「这一档取到什么」。
 *
 *  与界面那边（`ui/AppNaming.appNameOf`）**同一份顺序**：现查系统 → 落盘那份 → 老 `|名字` →
 *  包名。区别只在实现：那边有名字缓存、还要补分身槽号；这边一次提示一个名字，不缓存。
 */
private fun hostAppName(
    context: Context?,
    target: String,
    appNames: Map<String, String>,
): String =
    resolveAppName(
        target = target,
        fromSystem = { pkg -> pmLabel(context, pkg) },
        fromSaved = { id -> appNames[id] },
    )

/**
 * 包名 → 应用名；**查不到给 `null`**。
 *
 *  「查不到」与「名字恰好是包名」是两件事：前者要让 [resolveAppName] 继续往下走（落盘那份 /
 * 老后缀），后者不该。合成一个「查不到返回包名」的话，应用卸载之后提示里就只剩包名 ——
 * 而配置里明明记着它从前叫什么。
 *
 *  包名走 [AppTarget.pkg] 而不是 `substringBefore('/')`：分身的写法是 `999@包名/Activity`，
 *  直接切会剩下 `999@包名`，`getApplicationInfo` 必然查不到、提示里就成了包名。
 */
private fun pmLabel(
    context: Context?,
    pkg: String,
): String? {
    val ctx = context ?: return null
    return try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Throwable) {
        XLog.d("执行时提示：取应用名失败（$pkg）${e.javaClass.simpleName}")
        null
    }
}

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
