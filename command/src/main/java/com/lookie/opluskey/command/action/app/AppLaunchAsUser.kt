package com.lookie.opluskey.command.action.app

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.command.action.app.MiniWindowFacts.START_DELIVERED_TO_TOP
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect
import java.lang.reflect.Modifier

/**
 * 「按目标用户起一个界面」—— 分身与主用户走同一条路。
 *
 * ## 为什么主用户（`userId == 0`）也走这条路
 *
 *  不只是分身需要它：**装了分身的应用在主用户下启动，ColorOS 同样会问「用哪一份」**
 *  （同一个包名在 0 和 998/999 里各有一份，系统分不清调用方要哪个）。绕开那次询问的唯一办法
 *  是给 intent 挂 `OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER`，而挂完之后 intent 必须仍由这条
 *  AIDL 下发才带着正确的 userId —— `Context#startActivity` 传的是「本进程所在用户」，
 *  语义上等价，但就没法在调用前统一处理了。所以两支合并成一支。
 *
 * ## 为什么不走 `Context#createContextAsUser`
 *
 *  它要求**调用方自己的包**在目标用户里也装着（真机实测：`Own package not found for user
 *  998: package=com.oplus.gesture`）。宿主只装在主用户 ⇒ 对任何分身槽必然失败。
 *  ATMS 这条 AIDL 只认 userId，没有那个前提。
 *
 * ## 为什么落在 `host/action`
 *
 *  这是**执行**（起界面），不是数据。`core/model` 里只留目标写法的解析（`AppTarget`）：
 *  `:core` 是注入侧也加载的那一层，让它编得到「起界面」等于给 `AGENTS.md` 五之二、5 那条递刀。
 *
 * ## 参数个数逐版不同
 *
 *  Android 13/14 是 12 个（多一个 `callingFeatureId`），11/12 是 11 个。所以按
 *  「名字 + 个数」取两轮，用哪一轮能调通就用哪一轮 —— 与项目里其它 hidden API 同一口径。
 */
internal object AppLaunchAsUser {
    /**
     * `OplusIntent.OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER` —— 让 ColorOS 别弹「用哪一份」。
     *
     * ## 值是怎么来的
     *
     *  从 `oplus-services.jar` 里 `OplusMultiAppManagerService#shouldShowResolverActivity`
     *  的字节码读出来的 —— 两处 `const/16` 紧跟着 `hasOplusFlag`（详见 `docs/device/actions/launch.md`
     *  二十二）。`hasOplusFlag` 读的是 `Intent.getIntentExt().getOplusFlags()`，**只看调用方
     *  自己挂在 intent 上的 oplus flags**，与「谁调用的」无关 —— 所以不必伪装 callingPackage。
     */
    private const val OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER = 0x800

    /**
     * 给 intent 挂上 [OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER]。
     *
     *  `Intent#addOplusFlags(int)` 是 OPPO 往 AOSP 的 `Intent` 上加的方法，编译期不存在，
     *  只能反射。加不上**不阻断启动**（顶多是选择器照弹），所以返回布尔给调用方记日志。
     *
     *  **不只在 [start] 里挂**：原版动画那条路（`OriginalLaunch`）把 intent 直接交给宿主，
     *  不经过这里 —— 而它同样会撞上「装了分身的应用」（主用户下启动照样弹选择器，
     *  那一趟原版动画就被截断了）。所以调用方在转交之前也要挂一次。
     */
    internal fun markSkipChooser(intent: Intent): Boolean {
        val method =
            runCatching {
                Intent::class.java.getMethod("addOplusFlags", Int::class.javaPrimitiveType)
            }.getOrNull() ?: run {
                // 不带这台 ROM 的 oplus 扩展（或方法改名）—— 分身仍能起，只是要多点一次
                XLog.w("Intent#addOplusFlags 不存在，分身启动会弹选择器")
                return false
            }
        return runCatching {
            method.invoke(intent, OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER)
            true
        }.getOrElse {
            XLog.w("加 OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER 失败", it)
            false
        }
    }

    /**
     * 按 `userId` 下发一次启动。
     *
     * @return 真的发出去了（不代表目标界面起来了：那是系统的事，我们只看调用有没有被拒）
     */
    fun start(
        context: Context,
        intent: Intent,
        userId: Int,
    ): Boolean {
        val pkg = context.packageName
        markSkipChooser(intent)
        if (startViaAtms(intent, pkg, userId)) return true
        // ATMS 这条反射挂了（ROM 没有那两个签名）时的退路：**只有主用户有**。
        // 分身没得退 —— 换成 `Context#startActivity` 会起在主用户里，比失败更糟。
        if (userId != 0) return false
        XLog.w("startActivityAsUser 不可用，主用户退回 Context.startActivity")
        return runCatching { context.startActivity(intent) }
            .onFailure {
                //  只打落点：整个 Intent 会带上 data / extras，那是用户写进命令里的东西 ——
                //  与 `startViaAtms` 那条成功日志同一口径（脱敏）。
                val at = intent.component?.flattenToShortString() ?: intent.action.orEmpty()
                XLog.w("退回 Context.startActivity 也失败：$at", it)
            }.isSuccess
    }

    /** `startActivityAsUser` 两个签名都试一遍。成功返回 `true`。 */
    private fun startViaAtms(
        intent: Intent,
        pkg: String,
        userId: Int,
    ): Boolean {
        val atm = activityTaskManager() ?: return false
        // 12 个参数的那一版：caller / callingPackage / callingFeatureId / intent / resolvedType /
        //  resultTo / resultWho / requestCode / startFlags / profilerInfo / options / userId
        val v12 =
            arrayOf<Any?>(
                null,
                pkg,
                null,
                intent,
                null,
                null,
                null,
                -1,
                0,
                null,
                null,
                userId,
            )
        // 11 个的那一版：去掉 callingFeatureId
        val v11 =
            arrayOf<Any?>(
                null,
                pkg,
                intent,
                null,
                null,
                null,
                -1,
                0,
                null,
                null,
                userId,
            )
        for ((arity, args) in listOf(12 to v12, 11 to v11)) {
            val method =
                Reflect.methodByNameAndArity(atm.javaClass, "startActivityAsUser", arity) ?: continue
            val thrown = runCatching { method.invoke(atm, *args) }
            if (thrown.isSuccess) {
                if (reportStart(intent, thrown.getOrNull(), userId, arity)) return true
            } else {
                val cause = thrown.exceptionOrNull()?.let { Reflect.deepestCause(it) }
                XLog.w(
                    "startActivityAsUser(user=$userId, arity=$arity) 失败：" +
                        "${cause?.javaClass?.name}: ${cause?.message}",
                )
            }
        }
        XLog.w("startActivityAsUser 没有可用签名（user=$userId）")
        return false
    }

    /**
     * 一次 `invoke` 的落点判定 —— `START_*` 只有 0..3 算成功，其余如实说不成。
     *
     *  `invoke` 不抛 **不等于** 起得来：这个方法返回 `ActivityStarter` 的 `START_*`，
     *  被后台启动策略拦下时同样是正常返回（`START_CANCELED` / 负的 error code）。
     *  只看「没抛」会把拒绝报成「已下发启动」，用户侧则是一片静默。
     *
     * @param raw `invoke` 的返回值
     * @return 真的发出去了
     */
    private fun reportStart(
        intent: Intent,
        raw: Any?,
        userId: Int,
        arity: Int,
    ): Boolean {
        val code = raw as? Int
        if (code == null) {
            //  **返回类型不符 ≠ 被系统拒**：返回 void / 返回了别的对象时，我们手里没有
            //  任何判据，只能说「无法判断」。把它说成「被拒」会把排查带去后台启动策略。
            XLog.w(
                "startActivityAsUser(user=$userId, arity=$arity) 返回类型不符" +
                    "（${raw?.javaClass?.name ?: "null"}），无法判断有没有起界面",
            )
            return false
        }
        if (code in 0..START_DELIVERED_TO_TOP) {
            //  成功码只有 0..3（`START_SUCCESS` / `START_RESTART` / `START_DELAY_RESUME` /
            //  `START_DELIVERED_TO_TOP`）。`code >= 0` 会把 **101/102**
            //  （`START_RETURN_LOCK_TASK_MODE_VIOLATION` / `START_ABORTED`，后台启动被拦时正是它）
            //  也当成成功 —— 失败提示与回退逻辑整条都不触发。
            //  只打落点：整个 Intent 会带上 data / extras，那是用户写进命令里的东西
            val at = intent.component?.flattenToShortString() ?: intent.action.orEmpty()
            XLog.w("已下发启动：$at（user=$userId，arity=$arity，START_*=$code）")
            return true
        }
        XLog.w("startActivityAsUser(user=$userId, arity=$arity) 被系统拒（START_*=$code），没有起界面")
        return false
    }

    /** `ActivityTaskManager.getService()` → `IActivityTaskManager`（静态 hidden）。 */
    private fun activityTaskManager(): Any? {
        val cls =
            Reflect.findClass(null, "android.app.ActivityTaskManager") ?: run {
                XLog.w("找不到 ActivityTaskManager")
                return null
            }
        val got = Reflect.callStatic(cls, "getService")
        if (got == null) XLog.w("ActivityTaskManager.getService() 返回 null")
        return got
    }
}

/**
 * 「此刻最前面那个应用是哪个包」，以及那张表本身 —— 条件执行与「有小窗」两条判据共用。
 *
 * ## 为什么走 `OplusActivityManager#getAllTopAppInfos()`
 *
 *  模块已经在用这张表（小窗那一族），**权限与反射面都验过**：宿主进程读得到，而
 *  `ActivityManager#getRunningTasks` 那一套在新版本上要么被 `GET_TASKS` 权限挡住、要么
 *  只回本进程的任务，`UsageStatsManager` 则要用户手动授权「使用情况访问」。
 *
 *  表是**按「最前面」排序**的，取第 0 项就是前台应用。注意小窗在场时这张表会同时有
 *  「小窗那份」与「全屏那份」两条，第 0 项是谁取决于此刻谁在最前面 —— 对「前台是谁」
 *  这个问题正是要的那个答案，不要改成「扫整张表」（那是小窗验收的判据，问的不是一回事）。
 *
 * ## 读不到时返回空
 *
 *  没有这个类 / 方法、或表是空的，都会返回空。调用方（分叉）把空当 **「判不出」**：
 *  不命中任何带条件的分支，直接落兜底。宁可走兜底，也不猜一个包名 —— 猜错会执行用户
 *  完全没有预期的那条命令。
 */
internal object ForegroundApp {
    /** 前台应用的包名；判不出时 `null`。 */
    fun packageName(): String? =
        infos().firstOrNull()?.let { top ->
            Reflect
                .fieldValue(top, top.javaClass, MiniWindowFacts.TOP_APP_ACTIVITY_FIELD)
                ?.let { packageOf(it) }
        }

    /**
     * 整张表（原始的 `OplusAppInfo` 对象）；取不到时返回空表。
     *
     *  「有小窗」那条判据要自己扫这张表（找 `windowingMode == 100` 的那一条），所以这里把
     *  原始对象交出去，而不是只给包名。
     */
    fun infos(): List<Any> {
        //  反射那三条（找类 / getInstance / method）与「哪一步断了」的话术只在
        //  [MiniWindowFacts.topAppInfos] 里写一份 —— 这一层只决定**报几次**（每条手势都读，
        //  所以要 warnOnce）。
        val raw = MiniWindowFacts.topAppInfos(ForegroundApp::class.java.classLoader, ::warnOnce)
        return raw.orEmpty().filterNotNull()
    }

    /**
     * 「这条判据整条不通」的日志只打**一次**。
     *
     *  分叉每按一下都会判一次条件；读不到时每按一下刷一行同样的话没有意义，反而把别的判据行挤走。
     */
    @Volatile
    private var warned = false

    private fun warnOnce(message: String) {
        if (warned) return
        warned = true
        XLog.w(message)
    }

    /**
     * `OplusAppInfo.topActivity` 的包名 —— `ComponentName` 与 `ComponentInfo` 两种形状都认。
     *
     *  与 `MiniWindowLauncher.packageOf` 同一套判法，但那份挂在需要 Context 的实例上；
     *  这里只借「按名字取字段」这一步，不再拖一个执行器进来。
     */
    private fun packageOf(top: Any): String? =
        (top as? android.content.ComponentName)?.packageName
            ?: Reflect.call(top, "getPackageName") as? String
            ?: Reflect.fieldValue(top, top.javaClass, MiniWindowFacts.TOP_APP_PACKAGE_FIELD) as? String
}

/**
 * 「打开应用」时**交给宿主自己的启动链路**去起 —— 原版那套卡片展开与扫光就在它里面。
 *
 * ## 为什么是「转交」而不是「复刻」
 *
 *  技术文档 `docs/features/launch-animation.md` §9 的结论是那条链路恒不生效（`callback.a()` 与 `z`
 *  都是常量、adapter 恒 `null`、`d.o` 只打一条失败日志就返回）。**真机上不是这样** ——
 *  把模块总闸关掉、让侧键走原生行为，系统日志里会出现：
 *
 *  ```
 *  WindowManagerShell: RemoteTransitionInfo { remoteTransition = …Stub$Proxy@…,
 *                                            debugName = SpruceLauncher }
 *  WindowManagerShell: Delegate animation for (#47) to RemoteTransition { … SpruceLauncher }
 *  ```
 *
 *  `SpruceLauncher` 这个 debugName 全 APK 只有 `d$a.a()` 那一处写，而它只在 `adapter != null`
 *  时被调到 ⇒ 那条链路在运行时是**活的**（静态分析读的是常量分支，运行时的实际取值不是它算的那个）。
 *
 *  既然它活着，就没有复刻的理由：自己造一套 `RemoteAnimationAdapter` 不但要拖进
 *  `IRemoteAnimationRunner` 这种隐藏 AIDL（本工程没有框架 stub 依赖，编不过），
 *  复刻出来的还只是它的近似 —— 而现成那一套就在同一个进程里，一个反射调用就能用。
 *
 * ## 转交的是什么
 *
 *  `remote.transition.d#m(Context, Intent, int, boolean)` —— 原版三个入口最终都落到它
 *  （文档 §2.1），内部依次选动画实现、埋点、配 COE 扫光、建 animator，最后由
 *  `context.startActivityAsUser(intent, options, CURRENT)` 起界面。**界面也由它起**，
 *  所以调用方转交之后不能再自己起一次（会两个任务）。
 *
 * ## 为什么只对主用户
 *
 *  它内部用的是 `UserHandle.CURRENT`，没有 userId 入口 —— 分身目标（`999@包名`）交给它会在
 *  主用户里起。那一档仍然走我们自己的 `AppLaunchAsUser`，没有原版动画（分身本来也没验过）。
 *
 * ## 失败怎么退
 *
 *  反射拿不到 / 调用抛异常 ⇒ 返回 `false`，调用方照旧自己起界面。**不留延时兜底**：
 *  那需要在前台判定与「原版已起但还没到前台」之间猜，猜错就是开两个任务；
 *  而这条链路在真机上是活的，正常路径不会走到兜底里来。
 */
internal object OriginalLaunch {
    /** 原版那套启动控制器的类名（R8 之后的名字，见文档 §12.1 的符号表）。 */
    private const val LAUNCH_CONTROLLER = "com.oplus.gesture.remote.transition.d"

    /** `d#m` 的类型参数：`2` = 打开应用 / 打开设置页（文档 §2.2）。 */
    private const val TYPE_OPEN_APP = 2

    /**
     * 把这次启动交给原版 — `true` = 已经交出（界面由它起），调用方不要再起一次。
     *
     *  **就在调用线程上同步调**，不 post 到主线程。曾经照原版入口的样子 post 过一次（原版的
     *  `ActionRemoteStartContentProvider` 是 `mHandler.post` 之后才调 `d.m`），结果**原本有动画的
     * 应用全都变成没有**：`d.n` 里 `ActivityTransitionAnimator` 的 executor 本身就往主线程投递，
     *  我们再套一层 post，启动与转场之间就多了一拍，远端转场拿不到那次启动。
     *  原版那个 post 是 Binder 线程进主线程的必需，我们这条本来就不在 Binder 线程上，照抄是错的。
     */
    fun launch(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            //  **必须用宿主的 ClassLoader**：模块自己那条 dex 路径里只有模块包（异常信息里那个
            //  `InMemoryDexFile[...]`），默认的 `Class.forName(name)` 会当场 ClassNotFoundException。
            val cls = Class.forName(LAUNCH_CONTROLLER, false, context.classLoader)
            val controller = controllerOf(cls)
            //  「type 认不出」时宿主那条路会**连界面都不起**：`d#m` 第一件事就是 `d#k(context, type)`
            //  取动画实现，取不到只打一行 `e` 日志就 `return`。我们不能把它当「已交出」——
            //  那样用户看到的是「按了没反应」（界面也没起来），比没有动画更糟。
            if (implMissing(cls, controller, context)) {
                XLog.w("打开应用：原版链路认不出 type=$TYPE_OPEN_APP（取不到动画实现），改由模块自己起")
                return false
            }
            cls
                .getDeclaredMethod(
                    "m",
                    Context::class.java,
                    Intent::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ).apply { isAccessible = true }
                .invoke(controller, context, intent, TYPE_OPEN_APP, false)
            XLog.w("打开应用：已转交原版启动链路（由宿主建 RemoteTransition）")
            true
        } catch (e: Throwable) {
            val cause = Reflect.deepestCause(e)
            XLog.w("打开应用：原版链路调不起来，改由模块自己起：${cause.javaClass.simpleName}: ${cause.message}")
            false
        }

    /**
     * `d#k(Context, int)` 这一趟拿不到实现吗 —— `true` = 确定拿不到（调用方别再转交）。
     *
     *  拿不准（方法改名 / 反射失败）时返回 `false`，照旧转交：这一档只用来挡「宿主自己会静默
     *  返回、界面都不起」那一种，不能因为探不到就把整条原版动画关掉。
     */
    private fun implMissing(
        cls: Class<*>,
        controller: Any,
        context: Context,
    ): Boolean =
        runCatching {
            cls
                .getDeclaredMethod("k", Context::class.java, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(controller, context, TYPE_OPEN_APP) == null
        }.getOrDefault(false)

    /**
     * 原版那个控制器实例 —— **必须是宿主自己那一份**，不能自己新造一个。
     *
     *  `d` 存着 COE 扫光的状态：启动前 `d#m` → `d#n` → `d#l` 把 `mCoeAnimName` 与
     *  `COESysEffect` 写进去；**转场真正开播时读的却是伴生对象那个懒单例** ——
     *  `ActivityTransitionAnimator$a#w` 里写着 `d$a#b()` → `d#i()`（取 `mCoeAnimName`）→ `playAnim`。
     *
     *  自己 `new` 一个的话，`l` 把状态写到了新实例上，回调读的是另一个（它自己懒建的）实例，
     *  `d#i` 当场抛 `UninitializedPropertyAccessException: lateinit property mCoeAnimName has not
     *  been initialized`。那一枪跑在**宿主主线程**的 `Handler` 上，宿进程直接崩 —— 用户看到的是
     *  「没有动画」，接着「所有按键都没反应」（2026-11-15 真机，PLK110 / ColorOS 17.0.0，日志里
     *  `FATAL EXCEPTION: main … d.i … ActivityTransitionAnimator$a.w`）。
     *
     *  `f` 那个静态字段因此是「全世界只有一份」的那个开关：为 null 说明宿主自己还没建过，
     *  这时必须走它自己的懒入口（建好并写回字段），而不是在旁边再造一份。
     */
    private fun controllerOf(cls: Class<*>): Any {
        staticField(cls)?.let { field ->
            field.isAccessible = true
            runCatching { field.get(null) }.getOrNull()?.let { return it }
        }
        viaCompanion(cls)?.let {
            XLog.w("打开应用：控制器取自宿主自己的单例（转场回调读的就是它）")
            return it
        }
        //  兜底（换 ROM / 入口改名）：自己建一份，但**写回**那个静态字段 —— 回调与我们配的
        //  是同一份这件事不能靠运气，写不进去就当这次转交失败（宁可退回自己起界面，也不崩宿主）。
        val created = cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val field = staticField(cls) ?: return created
        field.isAccessible = true
        runCatching { field.set(null, created) }
            .onFailure {
                XLog.w("控制器写回静态字段失败（转场会把配置读成未初始化）：${it.javaClass.simpleName}")
            }
        return created
    }

    /** `d` 上那个「存着单例」的静态字段（类型就是 `d` 自己）。 */
    private fun staticField(cls: Class<*>): java.lang.reflect.Field? =
        cls.declaredFields.firstOrNull { Modifier.isStatic(it.modifiers) && it.type == cls }

    /**
     * 走宿主自己的懒单例入口 —— 伴生对象上那个「无参、返回 `d`」的方法（`d$a#b()`）。
     *
     *  不写死方法名：R8 每版都重排短名，而形状（静态字段里那个伴生对象 + 它上面唯一的无参工厂）
     *  是稳定的；找不到就返回 `null` 交给 [controllerOf] 的兜底，不硬猜。
     */
    private fun viaCompanion(cls: Class<*>): Any? =
        cls.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type != cls }
            .firstNotNullOfOrNull { field ->
                field.isAccessible = true
                val companion = runCatching { field.get(null) }.getOrNull() ?: return@firstNotNullOfOrNull null
                val factory =
                    companion.javaClass.methods.firstOrNull {
                        it.parameterCount == 0 && it.returnType == cls
                    } ?: return@firstNotNullOfOrNull null
                runCatching { factory.invoke(companion) }.getOrNull()
            }
}
