package com.lookie.opluskey.inject.system

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lookie.opluskey.bridge.TimedCache
import com.lookie.opluskey.bridge.inject.SystemContext
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire

/**
 * 「桌面上钉住的快捷方式」—— 由注入侧（`system_server`）直接读**快捷方式服务的活数据**。
 *
 * ## 为什么只有这一条路
 *
 *  `LauncherApps.getShortcuts` 对「钉住」这一档按**调用方**过滤：宿主不是桌面宿主读不到，
 *  换成系统身份（uid 1000）走同一个 API 也是同一结果（真机实测 `动态 3 / 清单 0 / 钉住 0`）。
 *  但本模块的注入侧**就住在 `system_server` 里**，那个服务的实例本来就在本进程 —— 它手上的
 *  `ShortcutUser` 全是活的 `ShortcutInfo`（含 `isPinned`），这层数据不经过任何调用方校验。
 *
 * ## 取数链（真机 `probe/dump` 摸出来的）
 *
 * ```
 * LocalServices.getService(com.android.server.pm.ShortcutService)
 *   → getUserShortcutsLocked(userId)   : ShortcutUser
 *   → getAllPackagesForTest()          : ArrayMap<包名, ShortcutPackage>
 *   → getAllShortcutsForTest()         : List<ShortcutInfo>   ← 含 isPinned
 * ```
 *
 *  三个都不是公开 API（`getAll*ForTest` 是 AOSP 自带的测试口子），所以整段走反射、按名字取，
 *  每一步单独兜住：拿不到返回 `null`（「这一次读不到」），与「读到了、但这个应用没钉过」分开 ——
 *  后者是一条有效结论。
 *
 * ## 安全边界（五之二）
 *
 *  · 只**读**，不调任何写方法；
 *  · 只在有人问的时候跑（`probe/shortcuts` 或宿主那条一问一答），安装期一概不碰；
 *  · 这些类此刻早已初始化（服务就在本进程里跑着），不存在「提前触发 `<clinit>` 把类拖成 erroneous」那条险路；
 *  · 整段在 worker 线程上、外面还包着 `postGuarded`，抛不出去。
 */
internal object ShortcutRead {
    private const val SERVICE_CLS = "com.android.server.pm.ShortcutService"
    private const val LOCAL_SERVICES_CLS = "com.android.server.LocalServices"

    /**
     * 内部接口那几个候选类名 —— `LocalServices` 里注册的一般是它（各版本可能分两处包名）。
     *
     * 真机上 `com.android.server.pm.ShortcutServiceInternal` 存在（`probe/dump` 那一轮查过）。
     */
    private val INTERNAL_IFACES =
        listOf(
            "com.android.server.pm.ShortcutServiceInternal",
            "com.android.server.shortcut.ShortcutServiceInternal",
        )

    /**
     * [pkg] 在 [userId] 下**被钉住**的那几条快捷方式（`id → 名字`）。
     *
     * @param loader `system_server` 的 ClassLoader（`KeyHooks` 手上那份）
     * @return `null` = 这一趟读不到（类 / 方法 / 实例哪一环没接上，原因已记日志）；
     *   空表 = 读到了，这个包没有钉住的快捷方式
     */
    fun pinned(
        loader: ClassLoader,
        pkg: String,
        userId: Int,
    ): List<Pair<String, String>>? {
        val service = serviceInstance(loader) ?: return null
        val user = oneInt(service, "getUserShortcutsLocked", userId) ?: return null
        val all = shortcutsOf(user, pkg, userId) ?: return null
        //  整理解不出（`isPinned` / 标签那几步碰到怪数据）也只算「这一趟读不到」——
        //  抛出去会变成「不回包」，宿主那一侧分不清是超时还是失败。
        return runCatching {
            val pinned = all.filterIsInstance<ShortcutInfo>().filter { it.isPinned }
            //  判据级：这一行回答「系统里到底钉了几条」—— 与宿主那一行（钉住 0）并排看就知道这条通道有没有用。
            XLog.w(
                "快捷方式（系统侧）：$pkg（user=$userId）系统里共 ${all.size} 条，其中钉住 ${pinned.size} 条" +
                    (if (pinned.isEmpty()) "" else "：" + pinned.joinToString("、") { labelOf(it) }),
            )
            pinned.map { it.id to labelOf(it) }
        }.getOrElse { e ->
            XLog.w("快捷方式（系统侧）：结果整理失败（${e.javaClass.simpleName}）⇒ 这一趟按读不到处理", e)
            null
        }
    }

    /**
     * 处理一条 [ModuleWire.ACTION_SHORTCUT_QUERY]：读一遍，把结果回给宿主。
     *
     *  **读不到就不回包**（宿主那边等满自己的超时之后按「没要到」收尾），读到了但没有钉住的才回空表：
     *  「读不到」与「这个应用没钉过」是两条不同的结论 —— 把前一条回成后者，宿主会把一条
     *  `appshortcut:` 绑定当成「快捷方式已经不在了」降级成整个应用。那比晚 300 毫秒严重得多。
     *
     * @param loader `system_server` 的 ClassLoader；`null` = 这一趟还没拿到，直接不回
     */
    fun reply(
        loader: ClassLoader?,
        intent: Intent,
    ) {
        val l = loader ?: return
        val req = intent.getStringExtra(ModuleWire.EXTRA_SHORTCUT_REQ) ?: return
        val pkg = intent.getStringExtra(ModuleWire.EXTRA_SHORTCUT_PKG) ?: return
        val userId = intent.getIntExtra(ModuleWire.EXTRA_SHORTCUT_USER, 0)
        val rows = pinned(l, pkg, userId) ?: return
        val ids = ArrayList<String>(rows.size)
        val labels = ArrayList<String>(rows.size)
        for ((id, label) in rows) {
            ids += id
            labels += label
        }
        //  发广播要一份 Context：借真实 App 的那份（`getSystemService` / `sendBroadcast` 都归它管，
        //  见 `SystemContext` 的注释）。拿不到就不回 —— 宿主那边按「没要到」收尾。
        val ctx = SystemContext.get(l) ?: return
        PageWire.send(
            ctx,
            HostApp.PACKAGE,
            Intent(ModuleWire.ACTION_SHORTCUT_RESULT)
                .putExtra(ModuleWire.EXTRA_SHORTCUT_REQ, req)
                .putStringArrayListExtra(ModuleWire.EXTRA_SHORTCUT_IDS, ids)
                .putStringArrayListExtra(ModuleWire.EXTRA_SHORTCUT_LABELS, labels),
        )
    }

    /**
     * [user] 手上 [pkg] 那一份快捷方式表 —— `null` = 这一趟读不到（原因已记日志）；
     * 空表 = 读到了，这个包一条都没注册（有效结论，与「读不到」是两件事）。
     */
    private fun shortcutsOf(
        user: Any,
        pkg: String,
        userId: Int,
    ): List<*>? {
        val packages =
            noArg(user, "getAllPackagesForTest") as? Map<*, *> ?: run {
                XLog.w("快捷方式（系统侧）：读不到用户 $userId 的包表（getAllPackagesForTest 对不上）")
                return null
            }
        val entry = packages[pkg] ?: return emptyList<Any>()
        return noArg(entry, "getAllShortcutsForTest") as? List<*> ?: run {
            XLog.w("快捷方式（系统侧）：读不到 $pkg 的快捷方式表（getAllShortcutsForTest 对不上）")
            null
        }
    }

    /**
     * 拿本进程那个 `ShortcutService` 实例 —— 三条路依次试，**每一步都留判据**（哪条路成了、翻到了什么类）。
     *
     *  `LocalServices` 里注册的**不是实现类**：真机上按 `com.android.server.pm.ShortcutService`
     * 直接取是 `null`（注册进去的是它内部那个 `ShortcutServiceInternal` 实现）。所以：
     *
     *  1. 先按实现类取（少数版本真注册了）；
     *  2. 再按内部接口取，取到的对象是服务的**内部类**，`this$0` 就指回服务本身；
     *  3. 还不行就走 `ServiceManager.getService("shortcut")` 那个 binder —— 同样是内部类，一样翻 `this$0`。
     *
     *  三条都落空就返回 `null` 并把「各条路分别返回了什么」记下来：那行就是下一轮该改哪里。
     */
    private fun serviceInstance(loader: ClassLoader): Any? {
        val serviceCls =
            runCatching { Class.forName(SERVICE_CLS, false, loader) }.getOrNull() ?: run {
                XLog.w("快捷方式（系统侧）：找不到 $SERVICE_CLS（类名随版本变过）")
                return null
            }
        //  四条路按顺序降级；每条自己留判据日志，这里只负责「先试哪条」
        val found =
            direct(loader, serviceCls)
                ?: holder(loader, serviceCls)
                ?: scanLocalServices(loader, serviceCls)
        if (found == null) {
            XLog.w(
                "快捷方式（系统侧）：拿不到 $SERVICE_CLS 实例" +
                    "（LocalServices 直接取=空，内部接口=${INTERNAL_IFACES.joinToString("/")}，ServiceManager 也认不出）",
            )
        }
        return found
    }

    /**
     * 「按内部接口取」与「按 `ServiceManager` 的 binder 取」这两条路 —— 拿到的都是 holder，
     * 要翻 `this$0` 才回到服务本身。
     *
     *  内部接口那一条先试：注册的是哪个接口随 ROM 变，两个候选名都要试；都对不上才去问
     *  `ServiceManager`（那条在真机上给的就是服务实例本身，见下面那段）。
     */
    private fun holder(
        loader: ClassLoader,
        serviceCls: Class<*>,
    ): Any? {
        val fromIfaces =
            INTERNAL_IFACES
                .mapNotNull { name -> runCatching { Class.forName(name, false, loader) }.getOrNull() }
                .mapNotNull { iface -> direct(loader, iface) }
                .firstNotNullOfOrNull { outerOf(it, serviceCls) }
        if (fromIfaces != null) return fromIfaces
        val binder =
            runCatching {
                Class
                    .forName("android.os.ServiceManager", false, loader)
                    .getDeclaredMethod("getService", String::class.java)
                    .invoke(null, "shortcut")
            }.getOrNull() ?: return null
        //  这版服务类自己就是那个 Binder（`extends IShortcutService.Stub`）：拿到的对象就是服务，
        //  不用再翻 `this$0`。真机日志原样写着 `ServiceManager=com.android.server.pm.ShortcutService`。
        if (serviceCls.isInstance(binder)) {
            XLog.w("快捷方式（系统侧）：ServiceManager 给的就是服务实例（${binder.javaClass.name}）")
            return binder
        }
        return outerOf(binder, serviceCls)
    }

    /**
     * `LocalServices.getService(cls)` —— 取不到返回 `null`。
     *
     * ## 为什么这一处抑制 `BlockedPrivateApi`
     *
     *  那条检查判的是「第三方应用能不能反射碰到 hiddenapi 黑名单里的成员」。本段**只在
     *  `system_server` 里执行**（`inject/` 这一层的唯一落点），那个进程本身就在 hiddenapi
     *  的豁免名单上 —— 检查在这里不成立。换成别的写法（拼方法名、走 `MethodHandles`）只是
     *  让检查看不见，行为一点不变。
     */
    @Suppress("BlockedPrivateApi")
    private fun direct(
        loader: ClassLoader,
        cls: Class<*>,
    ): Any? =
        runCatching {
            Class
                .forName(LOCAL_SERVICES_CLS, false, loader)
                .getDeclaredMethod("getService", Class::class.java)
                .apply { isAccessible = true }
                .invoke(null, cls)
        }.getOrNull()

    /**
     * 扫 `LocalServices` 那张静态表（`SparseArray<ArrayMap<Class, Object>>`）：只要值是服务本身、
     * 或它的内部类（`this$0` 指回服务），就认。
     *
     *  为什么要这一招：`LocalServices` 的键是**接口**，各版本 / 各 ROM 用哪个接口注册都不同；
     *  上面那几条按名字试的路一旦对不上，这一招还能兜住。整表只有几十项，扫一遍是微秒级。
     *
     *  抑制 `BlockedPrivateApi` 的理由与 [direct] 那段相同（只在 `system_server` 里执行）。
     */
    @Suppress("BlockedPrivateApi")
    private fun scanLocalServices(
        loader: ClassLoader,
        serviceCls: Class<*>,
    ): Any? =
        runCatching {
            val table =
                Class
                    .forName(LOCAL_SERVICES_CLS, false, loader)
                    .getDeclaredField("sLocalServiceObjects")
                    .apply { isAccessible = true }
                    .get(null) ?: return null
            val size = table.javaClass.getMethod("size").invoke(table) as Int
            for (i in 0 until size) {
                //  这两步都可能抛（ROM 换过这两个内部口子）：取不到就跳过这一张表，不中断整趟扫描
                val perUser =
                    runCatching {
                        table.javaClass.getMethod("valueAt", Int::class.javaPrimitiveType).invoke(table, i)
                    }.getOrNull()
                val values =
                    (
                        perUser?.let {
                            runCatching { it.javaClass.getMethod("values").invoke(it) }.getOrNull()
                        } as? Collection<*>
                    ).orEmpty()
                for (value in values) {
                    if (value == null) continue
                    if (serviceCls.isInstance(value)) {
                        XLog.w("快捷方式（系统侧）：在 LocalServices 表里扫到服务本身（第 $i 张表）")
                        return value
                    }
                    outerOf(value, serviceCls)?.let { return it }
                }
            }
            XLog.w("快捷方式（系统侧）：LocalServices 表扫了 $size 张，没有服务实例")
            null
        }.getOrNull()

    /**
     * [holder] 若是服务的内**部类**实例，`this$0` 就指回服务本身 —— 认它是不是 [serviceCls] 再返回。
     *
     *  顺带把结论打进日志：`xxx → this$0=yyy`。下一轮「为什么还是空」全靠这一行。
     */
    private fun outerOf(
        holder: Any,
        serviceCls: Class<*>,
    ): Any? {
        val outer =
            runCatching {
                holder.javaClass.declaredFields
                    .firstOrNull { it.name == "this$0" }
                    ?.apply { isAccessible = true }
                    ?.get(holder)
            }.getOrNull()
        val ok = outer != null && serviceCls.isInstance(outer)
        XLog.w("快捷方式（系统侧）：${holder.javaClass.name} → this\$0=${outer?.javaClass?.name ?: "空"}（认作服务=$ok）")
        return if (ok) outer else null
    }

    /**
     * 调一个**非公开**的无参方法。
     *
     *  按名字取、不锁签名：这几个口子在不同 ROM 版本上加过参数，锁死签名等于换一版 ROM 就静默失效 ——
     *  而这里要的恰恰是「拿不到要说出来」。
     */
    private fun noArg(
        target: Any,
        name: String,
    ): Any? =
        try {
            target.javaClass
                .getDeclaredMethod(name)
                .apply { isAccessible = true }
                .invoke(target)
        } catch (e: Throwable) {
            XLog.w("快捷方式（系统侧）：调 $name 失败（${e.javaClass.simpleName}）", e)
            null
        }

    /** 调一个只收一个 `int` 的非公开方法（`getUserShortcutsLocked(int)` 就这一种）。 */
    private fun oneInt(
        target: Any,
        name: String,
        arg: Int,
    ): Any? =
        try {
            target.javaClass
                .getDeclaredMethod(name, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(target, arg)
        } catch (e: Throwable) {
            XLog.w("快捷方式（系统侧）：调 $name 失败（${e.javaClass.simpleName}）", e)
            null
        }
}

/**
 * 快捷方式显示的那行字 —— 短名优先、再长名、最后退回 id（与宿主侧 `ActivityShortcuts` 同一条规则）。
 */
private fun labelOf(info: ShortcutInfo): String =
    info.shortLabel?.toString()?.takeIf { it.isNotBlank() }
        ?: info.longLabel?.toString()?.takeIf { it.isNotBlank() }
        ?: info.id

/**
 * `system_server` **自己那一份** System Context（包名 `android`）—— 注入侧「以系统身份 +
 * 走公开 API」的那几个动作（弹提示、写音量）都借它。
 *
 * ## 为什么不用 `bridge/inject/SystemContext` 那份
 *
 *  那一份是从某个真实 App 进程里**借**来的 Application Context：身份是 system（挂在我们这个进程
 *  上），包名却仍是那个 App —— 弹 Toast 会按宿主的名义走限流、写音量会按宿主的身份判权限。
 *  `ActivityThread.getSystemContext()` 给的这一份两者对得上，正是那两件事需要的。
 *
 * ## 只在注入侧可用
 *
 *  `currentActivityThread()` / `getSystemContext()` 都是 `ActivityThread` 的静态口子：只有
 *  `system_server` 里有那个实例。宿主进程里调会拿到 `null`（那边也不需要它）。
 *
 *  取到就缓存（一份 Context 贯穿进程寿命）；**取不到不缓存**，下次再试 —— 启动早期
 *  `ActivityThread` 可能还没就绪。缓存到失败值会让那两件事整个进程内永久失效。
 */
internal object SystemServerContext {
    @Volatile
    private var cached: Context? = null

    /**
     * @param loader `system_server` 的 ClassLoader
     * @return 拿不到返回 `null`（调用方各自记一句「断在哪一环」的日志，见五、2）
     */
    fun get(loader: ClassLoader): Context? {
        cached?.let { return it }
        val thread = ActivityThreadCompat.currentActivityThread(loader) ?: return null
        val ctx = Reflect.call(thread, "getSystemContext") as? Context ?: return null
        cached = ctx
        return ctx
    }
}

/**
 * 由 `system_server` 替宿主弹一条提示（命令里 `echo 'tip: …'` 那行）。
 *
 *  **为什么不在宿主那边弹**：系统的 toast 是**按包限流**的（3 条 / 20 秒、5 条 / 42 秒、
 *  6 条 / 68 秒，反编译与真机日志见 `docs/device/actions/feedback.md` 四十），宿主的包一旦连按超过
 *  这个密度，后面那些直接被 `NotificationService` 丢弃。而**系统自己**（包名 `android`）
 *  持有 `android.permission.UNLIMITED_TOASTS`，`isExemptFromRateLimiting` 判它免限流 ——
 *  所以这条提示改由注入侧用系统 Context 弹：同一个模块、同一句话，只是换了发话人。
 *
 *  两个约束：
 *  1) 用 `ActivityThread.getSystemContext()`（包名 `android`），**不是** [SystemContext] 借来的
 *     那份 App Context —— 后者包名仍是宿主，限流照样跑不掉；
 *  2) 整段跑在主线程、整段兜 `Throwable`：这是 `system_server`，漏一个异常出去就是整机重启
 *     （五之二）。
 */
internal object SystemToast {
    /** 主线程 Handler —— toast 只能在有 Looper 的线程上建。 */
    private val main = Handler(Looper.getMainLooper())

    /** 同一句话在这个间隔内不重复弹（长按连发时同一条命令会连着报同一句）。 */
    private const val DEDUP_MS = 1000L

    //  值与时刻收成一个对象（见 [TimedCache]）：判重与判过期读的是同一份
    private var lastToast: TimedCache<String>? = null

    /** 请注入侧弹一条提示；任何一环失败都只记日志。 */
    fun show(
        loader: ClassLoader,
        text: String,
    ) {
        if (text.isEmpty()) return
        main.post {
            try {
                val ctx = context(loader) ?: return@post
                val now = android.os.SystemClock.elapsedRealtime()
                if (lastToast?.let { it.value == text && it.fresh(now, DEDUP_MS) } == true) return@post
                lastToast = TimedCache(text, now)
                // 每次**新建**一条、也不 cancel 上一条：同一个实例再 show 会按 token 就地更新
                //  队列里那一条（框架的 `indexOfToastLocked`），连按时就只剩「一条一直在变」——
                //  用户要的是每按一下都完整弹一条，所以让它们排队。配额这边免了（见类注释），
                //  排几条都不会被系统丢掉。
                Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
                XLog.w("注入侧弹提示：$text")
            } catch (e: Throwable) {
                XLog.w("注入侧弹提示失败（已忽略）：${e.javaClass.simpleName}", e)
            }
        }
    }

    /** `ActivityThread.getSystemContext()` —— 包名 `android` 的那一份（免限流就靠它）。 */
    private fun context(loader: ClassLoader): Context? {
        val ctx = SystemServerContext.get(loader)
        if (ctx == null) XLog.w("拿不到 system_server 的 SystemContext，注入侧弹不了提示")
        return ctx
    }
}

/**
 * 由 `system_server` 替宿主**把某一路流设到某一档**（`ModuleWire.ACTION_SET_VOLUME`）。
 *
 *  **为什么不在宿主那边设**：宿主进程里 `setStreamVolume` 会被 ColorOS 静默丢掉 —— 写下去、
 *  读回来还是旧值，也不抛异常（`docs/device/actions/audio.md` 三十四）。宿主只剩「注入音量键」
 *  这一条退路，而一颗键就是一整格（量程的 1/16，`0..160` 上 10 格），`±1%`（1.6 格）这种
 *  细步进走不出来：硬按就是「1% 走成 6.25%」，不按就是「按了没反应」。
 *  这一层跑在 `system_server`，调用身份是系统本身，那条策略不挡它 —— 精确档位又能拿到了，
 *  所以**音量那一族正常就走这一条**，宿主自己那几档写只剩「模块没注入时」的兜底。
 *
 *  **带 `FLAG_SHOW_UI`**：改完屏幕上就有那一路流的音量条。**值本来就等于目标**时改走
 *  `adjustStreamVolume(ADJUST_SAME, FLAG_SHOW_UI)` —— `setStreamVolume` 在同值上不发面板
 * （宿主与这一侧都一样，真机实测），而物理音量键按到底时值也没变、面板照样弹，走的就是 adjust
 *  （`docs/device/actions/audio.md` 三十七）。
 *
 *  两条约束（与 [SystemToast] 同一档）：
 *  1) 用 `ActivityThread.getSystemContext()`（包名 `android`）—— 身份与包名对得上，
 *     不用 [com.lookie.opluskey.bridge.inject.SystemContext] 借来的那份 App Context
 *     （那份的包名仍是宿主，身份与包名不一致）；
 *  2) 整段兜 `Throwable`：这是 `system_server`，漏一个异常出去就是整机重启（五之二）。
 *     调用方（`inject/KeyHooks`）已经把它 post 到 worker 线程上，这里不再切线程。
 */
internal object SystemVolume {
    /**
     * 设为 [index] 档；任何一环失败都只记日志。
     *
     * @param stream `AudioManager.STREAM_MUSIC` / `STREAM_RING` 之类
     */
    fun set(
        loader: ClassLoader,
        stream: Int,
        index: Int,
    ) {
        try {
            val ctx = context(loader) ?: return
            val am =
                ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: run {
                    XLog.w("注入侧设音量：拿不到 AudioManager（路 $stream → $index）")
                    return
                }
            val before = am.getStreamVolume(stream)
            if (before == index) {
                //  值没有要改的 ⇒ 这一档要的只是**把音量条叫出来**。`setStreamVolume` 在同值上
                //  不发面板（这一侧也一样，真机实测），而 `adjustStreamVolume` 会 —— 物理音量键
                //  按到底时值也没变、面板照样弹，走的就是它。`ADJUST_SAME` 不改值；面板是
                //  **当前活跃流**那一条（这台 ROM 实测）：媒体流就是媒体条。
                am.adjustStreamVolume(stream, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                XLog.w("注入侧设音量：路 $stream 已经是 $index ⇒ 值不动，只把音量条叫出来")
                return
            }
            am.setStreamVolume(stream, index, AudioManager.FLAG_SHOW_UI)
            //  判据级：宿主那边看不到结果，只认「写前 / 写后」这两个读数
            XLog.w("注入侧设音量：路 $stream $before → ${am.getStreamVolume(stream)}（目标 $index）")
        } catch (e: Throwable) {
            XLog.w("注入侧设音量失败（已忽略）：路 $stream → $index | ${e.javaClass.simpleName}", e)
        }
    }

    /** `ActivityThread.getSystemContext()` —— 包名 `android` 的那一份。 */
    private fun context(loader: ClassLoader): Context? {
        val ctx = SystemServerContext.get(loader)
        if (ctx == null) XLog.w("注入侧设音量：拿不到 system_server 的 SystemContext")
        return ctx
    }
}
