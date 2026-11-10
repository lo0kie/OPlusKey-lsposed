package com.lookie.opluskey.inject

import android.content.Intent
import android.content.pm.ShortcutInfo
import com.lookie.opluskey.bridge.inject.SystemContext
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
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
        val pinned = all.filterIsInstance<ShortcutInfo>().filter { it.isPinned }
        //  判据级：这一行回答「系统里到底钉了几条」—— 与宿主那一行（钉住 0）并排看就知道这条通道有没有用。
        XLog.w(
            "快捷方式（系统侧）：$pkg（user=$userId）系统里共 ${all.size} 条，其中钉住 ${pinned.size} 条" +
                (if (pinned.isEmpty()) "" else "：" + pinned.joinToString("、") { labelOf(it) }),
        )
        return pinned.map { it.id to labelOf(it) }
    }

    /**
     * 处理一条 [ModuleWire.ACTION_SHORTCUT_QUERY]：读一遍，把结果回给宿主。
     *
     *  空表照回（那是「这个应用没钉过」或「读失败」的结论）：回一条空包让宿主那边**不再等**。
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
        val rows = pinned(l, pkg, userId).orEmpty()
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
