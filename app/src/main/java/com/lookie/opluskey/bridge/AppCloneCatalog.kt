package com.lookie.opluskey.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.UserManager
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 「应用多开 / 分身」在**宿主侧**的那一套：列分身用户、按用户查包、把分身里的可启动应用摊成清单。
 *
 * ## 分身是什么
 *
 *  不是「同一个用户下的第二份包」，而是**另一个 Android 用户**。真机（PLK110 / ColorOS 17）实测：
 *
 *  ```
 *  UserInfo{0:李尔康:4c13}            ← 主用户
 *  UserInfo{995..999:MultiApp}        Type: android.os.usertype.profile.CLONE
 *  ```
 *
 *  五个槽位，**同一个应用可以同时占多个**（实测微信在 998 与 999 各有一份）：
 *
 *  ```
 *  u0_a359     10359     com.tencent.mm   ← 主用户
 *  u998_a359   99810359  com.tencent.mm   ← 分身 1
 *  u999_a359   99910359  com.tencent.mm   ← 分身 2
 *  ```
 *
 *  ⇒ **uid 的十万位以上就是 userId**（`uid / 100_000`），而进程名一模一样 ——
 *  「哪一份在跑」只能靠 uid 判，靠名字判不出来。
 *
 * ## 为什么在 `bridge` 而不是 `core/model`
 *
 *  这里每一件事都要 `Context`：列用户走 `UserManager`、取包管理器走 `Context#createContextAsUser`、
 *  查别人的包走 `IPackageManager`、起别人的界面走 `IATMS` —— 全是**执行与平台适配**，不是纯数据。
 *  `:core` 是**两个进程共用**的那一层（注入侧也加载），把这套能力放进去等于让 `system_server`
 *  里也编得到「起界面」；真有人从注入侧调一次，就是 `AGENTS.md` 五之二、5 那条（系统进程不能起界面）。
 *  纯数据的部分（目标写法 `999@包名/类名`）留在 `core/model/AppTarget`。
 *
 *  放在 `bridge/` 顶层（不分 `host` / `inject`）：**两侧都读它** —— 页面侧用它列分身（选择应用页、
 *  格子的绑定小字），宿主侧用它按用户起界面 / 快捷方式 / 小窗。它是**查询能力**，不是一次动作：
 *  原先搁在 `host/action` 时，页面层只能反向依赖执行层（`ui → host` 那条边就是这么长出来的）。
 *
 * ## 为什么这一整个类都在走反射
 *
 *  `UserManager#getUsers` / `UserHandle#of` / `Context#createContextAsUser` 在公开 SDK 里
 *  都是 hidden / `@SystemApi`，**直接写编译不过**（实测：三个全部未解析）。AOSP 里它们一直是那个
 *  样子，签名很稳，所以用 [Reflect] 按名字取；顺便沿用本项目对 hidden API 的一贯口径
 *  （找不到就记账、降级，不抛）。
 *
 * ## 为什么本项目能碰这些东西
 *
 *  模块跑在宿主 `com.oplus.gesture` 的进程里，宿主虽是普通 app uid（实测 10277），却被授予了
 *  `INTERACT_ACROSS_USERS_FULL` / `MANAGE_USERS` / `CREATE_USERS` / `MANAGE_ACTIVITY_TASKS` /
 *  `REAL_GET_TASKS`，跨用户读包信息与起 Activity 都过得了权限检查 —— **不需要 root**。
 *
 *  换一台没这些权限的机器跑，[users] 只会得到空表 —— 那是**合法**结论（那台机器上没有分身），
 *  不必为它单独分支。
 */
internal object AppCloneCatalog {
    /** AOSP 给「应用分身」定的用户类型 —— 比 user id 段、比显示名都稳（见类注释的实测）。 */
    private const val CLONE_USER_TYPE = "android.os.usertype.profile.CLONE"

    /** uid ↔ userId 的进制：Android 的 uid = `userId * 100000 + appId`。 */
    private const val UID_PER_USER = 100_000

    /**
     * [cloneUsersInDisplayOrder] 记下的那一份用户表（只记非空的那次，见那边的注释）。
     *
     *  `@Volatile`：写它的是后台协程（[entries]），读它的是界面线程（[slotOf]）——
     *  没有它，界面可能一直看着「还没列出来」那份，逐行去重列用户表。
     */
    @Volatile
    private var cachedUsers: List<Int>? = null

    /**
     * [slotOf] 已经**确认**不在用户表里的那些 user id。
     *
     *  查不到要重列一次用户（缓存是进程级的，新装的分身还没进表），但**不能每次查不到都重列** ——
     *  拼名字那条路是逐行调的，一个已删除的分身会让每一行都跑一次 `getUsers` 的反射 + binder。
     *  这份备忘录在 [entries] 作废用户表时一起清（那里是唯一会「重新认识这台机器」的地方）。
     */
    private val unresolvedUsers =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<Int>()

    /** 分身那一份里的一个可启动应用 —— 界面层要的那几样，外加它属于哪个槽。 */
    data class Entry(
        val packageName: String,
        val label: String,
        val system: Boolean,
        val userId: Int,
        val slot: Int,
    )

    /**
     * 本机所有**分身用户**（不含主用户）。列不出来时返回空表。
     *
     *  判据是 `UserInfo` 的类型串，**不按 id 段也不按显示名**：id 各家 ROM 不同（OPPO 从 995 起、
     *  别的 ROM 又是别的段），显示名是 ROM 起的（这台叫 `MultiApp`）。类型串是 AOSP 定的。
     */
    fun users(context: Context): List<Int> {
        val um =
            runCatching { context.getSystemService(UserManager::class.java) }.getOrNull() ?: run {
                XLog.w("拿不到 UserManager，多开用户列不出来")
                return emptyList()
            }
        val raw =
            Reflect.call(um, "getUsers") as? List<*> ?: run {
                XLog.w("UserManager#getUsers 取不到（这台机器大概没有多开）")
                return emptyList()
            }
        val clones = raw.filterNotNull().filter { isClone(it) }
        if (clones.isNotEmpty()) {
            XLog.w("多开用户：${clones.joinToString { "${idOf(it)}(${nameOf(it)})" }}")
        }
        return clones.map { idOf(it) }.filter { it > 0 }
    }

    /**
     * 本机分身用户，按 **ColorOS 多开页的显示顺序**排（`[999, 998, 997, …]`）。
     *
     *  那一页是**倒序**的：`999` 显示成「分身 1」、`998` 是「分身 2」……（真机实测）。
     *  所以槽号 = `indexOf(userId) + 1`，不要用 `1000 - userId` 之类按段硬算的式子 ——
     *  别的 ROM 槽位段不一定从 995 起（见类注释）。
     */
    fun cloneUsersInDisplayOrder(context: Context): List<Int> {
        //  拼名字那条路（`ui.appNameOf` → [slotOf]）是逐行调的，而 [users] 每次都要走
        //  `UserManager#getUsers` 的反射 + binder。分身的用户表在一个进程生命周期里不会变，
        //  所以拿到非空结果就记住这一次。
        //  空表**不**缓存：那多半是这台机器没有分身 / 权限没给到（[users] 的合法结论），
        //  缓存住会让「后来才装的分身」永远看不到。
        cachedUsers?.let { return it }
        val sorted = users(context).sortedDescending()
        if (sorted.isNotEmpty()) cachedUsers = sorted
        return sorted
    }

    /**
     * 某个用户在 ColorOS 多开页里的**槽号**（主用户得 `0`）—— 界面显示「微信 1」的那个编号。
     *
     *  规则只有这一处：选择应用页拼行名、「打开应用」格拼绑定的小标题都调它。各算一套的话，
     *  网格里的「微信 1」与列表里的「微信 1」可能指向不同用户。
     *
     *  查不到那个 user（缓存里没有、重列也没有：分身刚删掉，或者那个 user 根本不是分身类型）
     *  返回 [UNKNOWN_SLOT]（负数），**绝不返回 0** —— 0 是主用户的编号，返 0 就是把「不知道」
     *  画成「这是主用户那一份」（撞号）。
     */
    fun slotOf(
        context: Context,
        userId: Int,
    ): Int {
        if (userId == 0) return 0
        val known = cloneUsersInDisplayOrder(context)
        val hit = known.indexOf(userId)
        if (hit >= 0) return hit + 1
        // 缓存里没有 ⇒ 要么这一份才装上（缓存只记非空的那一次），要么那个分身已经删了。
        //  重列一次；已经记过「查无此人」的就不再重列（拼名字那条路是逐行调的）。
        val already = userId in unresolvedUsers
        val fresh = if (already) known else users(context).sortedDescending().ifEmpty { known }
        if (fresh !== known) cachedUsers = fresh
        val idx = fresh.indexOf(userId)
        if (idx >= 0) {
            unresolvedUsers -= userId
            return idx + 1
        }
        //  以前这里是 `indexOf(userId) + 1` ⇒ 查不到得 **0**，而那正是**主用户**的编号 ⇒
        //  界面上「微信 1」被画成「微信」，与真主用户那一行同名（撞号）。
        if (!already) {
            unresolvedUsers += userId
            XLog.w("槽号：user=$userId 不在分身用户表里（${known.joinToString()}），按 user id 显示")
        }
        return UNKNOWN_SLOT
    }

    /** [slotOf] 的「查不到槽号」—— 负数，不可能与真槽号（从 1 起）撞上。 */
    const val UNKNOWN_SLOT: Int = -1

    /**
     * 分身槽里那些**可启动的应用**，按槽分组摊平（主用户的不在这里，见 `AppList`）。
     *
     *  查不到的那一槽整块不出现（[AppClonePackages.queryLaunchablesAsUser] 返回 `null` 时）——
     *  **绝不退回主用户**：那会造出「看起来像分身」的假行（上一轮就是这样多出 790 行假数据）。
     */
    fun entries(context: Context): List<Entry> {
        val users = cloneUsersInDisplayOrder(context)
        if (users.isEmpty()) return emptyList()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return users.flatMapIndexed { index, uid ->
            val resolved =
                AppClonePackages.queryLaunchablesAsUser(intent, uid) ?: run {
                    // 断点由 `AppClonePackages` 那边说（哪一个反射环节落空）；这里补的是**后果**：
                    //  这一槽在界面上会整块消失，看着像「这个分身一份应用都没装」。
                    //  同时作废用户表：这一槽多半是**已经删掉的分身**，那份缓存还留着它就会
                    //  每次刷新都白查一遍（槽号也还按它算）。作废 = 清掉这一份，下一问重新列；
                    //  「已确认查无此人」的备忘录也一起清（新装的分身可能正是那个 id）。
                    XLog.w("分身槽（user=$uid）的可启动列表拿不到，这一槽不列")
                    cachedUsers = null
                    unresolvedUsers.clear()
                    return@flatMapIndexed emptyList()
                }
            resolved
                .asSequence()
                // `applicationInfo?.flags?.and(..) != 0` 在它为 null 时得到的是 `null != 0`
                //  ⇒ **true**，会把一个读不到信息的应用标成「系统应用」（小标签画错，且
                //  「只显示用户应用」那类筛选会把它漏掉）。读不到就当不知道 —— 不是系统应用。
                .filter { it.activityInfo?.packageName != ModuleInfo.PACKAGE }
                .distinctBy { it.activityInfo?.packageName }
                .mapNotNull { ri ->
                    val info = ri.activityInfo ?: return@mapNotNull null
                    // 分身那一份的 label 要那个用户的 Context 才拿得准（拿不到，见 [contextFor]），
                    //  退回主用户的：同一个包在两个用户里的 label 相同，不影响认人。
                    val label =
                        runCatching { info.loadLabel(pm).toString() }
                            .getOrElse { info.packageName }
                    Entry(
                        packageName = info.packageName,
                        label = label,
                        system = info.applicationInfo?.let { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 } == true,
                        userId = uid,
                        slot = index + 1,
                    )
                }.toList()
        }
    }

    /**
     * 这个 user 是不是分身。
     *
     *  `userType` 在不同 API 上可见性不一样（有版是 public 字段、有版只有 getter），两条都试 ——
     *  读不到就当「不是分身」，不影响主流程。
     */
    private fun isClone(info: Any): Boolean {
        val cls = info.javaClass
        val type =
            runCatching { cls.getMethod("getUserType").invoke(info) as? String }.getOrNull()
                ?: runCatching {
                    cls.getDeclaredField("userType").apply { isAccessible = true }.get(info) as? String
                }.getOrNull()
        return type == CLONE_USER_TYPE
    }

    private fun idOf(info: Any): Int =
        (Reflect.call(info, "getId") as? Int)
            ?: runCatching { info.javaClass.getField("id").get(info) as? Int }.getOrNull()
            ?: -1

    private fun nameOf(info: Any): String =
        (Reflect.call(info, "getName") as? String)
            ?: runCatching { info.javaClass.getField("name").get(info) as? String }.getOrNull()
            ?: "?"

    /** 等价于 hidden 的 `UserHandle.of(id)`：拿那个用户的 system uid 反查。 */
    fun handleOf(userId: Int): UserHandle = UserHandle.getUserHandleForUid(userId * UID_PER_USER)

    /**
     * 按用户拿到一个能用的 Context：`userId == 0` 时就是原 Context；**拿不到返回 `null`**。
     *
     *  `createContextAsUser` 返回的 Context 上，`packageManager` / `startActivity` 都**按那个
     *  用户算**。真机实测这个调用在宿主进程里**会抛**（`IllegalStateException: Own package not
     *  found for user 998`：宿主只装在主用户），所以它现在只剩「能拿到就用」这一条路，拿不到由
     *  调用方退到主用户的包管理器（解析用）。
     *
     *  ## 为什么是 `null` 而不是「退回主用户」
     *
     *  早先这里退回主用户的 Context，结果每个分身槽都拿主用户的应用查了一遍 —— 列表凭空多出
     *  790 行「分身」，全是假的。**拿不到就是拿不到**：让调用方跳过，别造一份看起来像成功的数据。
     */
    fun contextFor(
        context: Context,
        userId: Int,
    ): Context? {
        if (userId == 0) return context
        val handle = handleOf(userId)
        val method =
            Reflect.method(Context::class.java, "createContextAsUser", UserHandle::class.java, Int::class.javaPrimitiveType!!)
                ?: run {
                    XLog.w("Context#createContextAsUser 找不到 ⇒ 分身起不来")
                    return null
                }
        // 失败要把**真正的原因**打出来：`Reflect.call` 那层只记了 `InvocationTargetException`
        // （它的 message 恒为 null），等于什么都没说。
        val thrown = runCatching { method.invoke(context, handle, 0) }
        val made = thrown.getOrNull() as? Context
        if (made != null) return made
        val cause = thrown.exceptionOrNull()?.let { Reflect.deepestCause(it) }
        XLog.w("createContextAsUser($userId) 失败：${cause?.javaClass?.name}: ${cause?.message}")
        return null
    }

    /**
     * 那个用户视角下的 `PackageManager`（`null` = 拿不到）。
     *
     *  **解析入口要用这个**，不能用当前用户的：`getLaunchIntentForPackage` 只在**本用户**
     *  的可见范围内找，分身里的包在主用户视角下压根不存在。拿不到时调用方退主用户的 PM
     *  （显式组件那条路仍走 binder 查，见 [AppClonePackages]）。
     */
    fun packageManagerFor(
        context: Context,
        userId: Int,
    ): PackageManager? {
        val perUser = contextFor(context, userId) ?: return null
        return runCatching { perUser.packageManager }
            .onFailure {
                XLog.w("取 user $userId 的包管理器失败：${it.javaClass.simpleName}")
            }.getOrNull()
    }
}

/**
 * 分身这条路的 **binder 层** —— 与 [AppCloneCatalog] 分开是因为两件事的工具箱不一样：
 * 那边是 `UserManager`（列用户），这边是 `IPackageManager`（按 userId 查包与入口）。
 * 合在一个对象里会过 detekt 的 `TooManyFunctions`，而那个阈值本身是对的：
 * 一个对象里塞两套互不相关的能力，读的人得先分清「这函数属于哪一半」。
 */
internal object AppClonePackages {
    /**
     * `IPackageManager`（binder 那一层）—— 分身这条路上**唯一能用的入口**。
     *
     * ## 为什么必须下沉到 binder
     *
     *  真机实测两条上层 API 都不可用：
     *
     *  ```
     *  Context#createContextAsUser(998) → IllegalStateException:
     *      Own package not found for user 998: package=com.oplus.gesture
     *  PackageManager#queryIntentActivitiesAsUser → 这台 ROM 上没有这个方法
     *  ```
     *
     *  前者要求**调用方自己的包**在目标用户里也装着（宿主只装在主用户，必然失败）；后者被这个
     *  ROM 砍了。而 `IPackageManager` 的 AIDL 方法**本身带 userId 参数**，不要求调用方在那个
     *  用户里有包 —— 这正是系统自己列分身应用时走的那条路。
     *
     * ## 拿到它
     *
     *  `ActivityThread.getPackageManager()` 是静态 hidden 方法，返回 `IPackageManager`
     *  （实际是 `IPackageManager$Stub$Proxy`）。它与本进程的 uid 绑定，权限按宿主算 ——
     *  宿主有 `INTERACT_ACROSS_USERS`，所以跨用户调用过得去。
     */
    private fun ipm(): Any? {
        val at =
            Reflect.findClass(null, "android.app.ActivityThread") ?: run {
                XLog.w("找不到 ActivityThread，分身这条路上不去")
                return null
            }
        val got = Reflect.callStatic(at, "getPackageManager")
        if (got == null) XLog.w("ActivityThread.getPackageManager() 返回 null")
        return got
    }

    /**
     * 用 binder 层按 userId 查可启动界面（`IPackageManager#queryIntentActivities`）。
     *
     *  返回 `List<ResolveInfo>`；拿不到返回 `null`（**不要**退成主用户 —— 那会造出假行）。
     *
     *  兼容两种返回形状：老版本直接是 `List`，Android 10 起是 `ParceledListSlice`（有个
     *  `getList()`）。两种都认，省得按版本分叉。
     */
    fun queryLaunchablesAsUser(
        intent: Intent,
        userId: Int,
    ): List<android.content.pm.ResolveInfo>? {
        val binder = ipm() ?: return null
        // flags 的类型跟着 AIDL 走：**Android 13 起是 `long`**（`ResolveInfoFlags`），之前是 `int`。
        //  只按 int 找的话，新 ROM 上这里恒为 null —— 分身那一槽整块消失，日志却只说「没有这个方法」。
        val withLongFlags =
            Reflect.method(
                binder.javaClass,
                "queryIntentActivities",
                Intent::class.java,
                String::class.java,
                java.lang.Long.TYPE,
                Int::class.javaPrimitiveType!!,
            )
        val method =
            withLongFlags
                ?: Reflect.method(
                    binder.javaClass,
                    "queryIntentActivities",
                    Intent::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                ) ?: run {
                XLog.w("IPackageManager 上没有 queryIntentActivities(Intent,String,long|int,int)")
                return null
            }
        // 参数值也得跟着签名给：long 那一档传 `0`（Int）会 `IllegalArgumentException`
        val flags: Any = if (withLongFlags != null) 0L else 0
        val thrown = runCatching { method.invoke(binder, intent, null, flags, userId) }
        val raw = thrown.getOrNull()
        if (raw == null) {
            val cause = thrown.exceptionOrNull()?.let { Reflect.deepestCause(it) }
            XLog.w("queryIntentActivities(user=$userId) 失败：${cause?.javaClass?.name}: ${cause?.message}")
            return null
        }
        return unwrapList(raw, userId)
    }

    /** `List` 与 `ParceledListSlice` 两种返回形状都摊成 `List<ResolveInfo>`。 */
    private fun unwrapList(
        raw: Any,
        userId: Int,
    ): List<android.content.pm.ResolveInfo>? {
        val list = if (raw is List<*>) raw else Reflect.call(raw, "getList") as? List<*>
        if (list == null) {
            XLog.w("queryIntentActivities(user=$userId) 返回了不认识的形状：${raw.javaClass.name}")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return list.filterIsInstance<android.content.pm.ResolveInfo>()
    }
}
