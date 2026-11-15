package com.lookie.opluskey.bridge

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

/**
 * 把一个包名解析成「能打开的入口」。
 *
 * `pm.getLaunchIntentForPackage` 只认 `ACTION_MAIN` + `CATEGORY_LAUNCHER`，而**把桌面图标藏起来
 * 的应用**往往正是把那个带 `LAUNCHER` 的分量（常是个 `activity-alias`）`disabled` 掉 —— 于是它
 * 判成「没有入口」，可包里另有一个带 `MAIN` 的界面是好的，**显式组件**起得起来
 * （`com.ss.android.ugc.aweme.yyds` 即此：`…awemes.Home` 带 LAUNCHER 且被禁用，
 * `…awemes.MainActivity` 带 MODULE_SETTINGS 且可用）。
 *
 * 所以按「启动图标 → TV 图标 → 任意 `MAIN` 界面」逐层退，最后一律用显式组件起 —— 显式起不受
 * `intent-filter` 匹配限制，只要求组件导出。
 *
 * 选择列表的「不可打开」标记与 `app:` 动作共用本函数：两边判据必须一致，否则会出现
 * 「列表里点得动、按下去报找不到」。
 */
internal object AppEntry {
    /** 名字里带这些词的界面算更像入口，排前面。 */
    private val NAME_HINTS = listOf("main", "launcher", "home", "entry", "welcome", "splash", "start")

    /**
     * 一个**目标写法**（`app:` / `window:` 动作里那一段）解析成可打开的 Intent；解析不出返回 `null`。
     *
     * 两种写法：
     *  · `包名/类名` —— 直接组显式组件；类名以 `.` 开头（或压根没点）时补上包名前缀；
     *  · 只有包名 —— 走 [resolve] 那套「启动图标 → TV 图标 → 任意 `MAIN` 界面」逐层退。
     *
     *  与 [resolve] 放在一处是因为它们是同一件事的两级：`resolve` 管「包名 → 入口」，
     *  这里管「用户写的那串 → 入口」。原来这一层写在 `AppLauncher` 里，于是要用它的两个动作族
     *  （普通打开与小窗打开）都得挂在同一个类上。
     */
    fun intentOf(
        pm: PackageManager,
        target: String,
    ): Intent? {
        // 目标里可能带多开前缀（`999@com.tencent.mm/.ui.LauncherUI`，见 `AppTarget`）——
        //  解析组件之前先剥掉：包名与类名都不含 `@`，带着它去 setClassName 只会拼出个不存在的组件。
        val plain = AppTarget.withoutUser(target)
        return if (plain.contains('/')) {
            val pkg = plain.substringBefore('/')
            val raw = plain.substringAfter('/')
            // 短横线写法（`app:pkg/.MainActivity`）按 `ComponentName.unflattenFromString` 的口径
            //  拼：点开头就是「类名相对包名」，直接接在包名后面 —— 写成 `"$pkg.$raw"` 会拼出
            //  `pkg..MainActivity`（两个点），那个类永远不存在，表现为「应用起不来」。
            val cls =
                if (raw.startsWith(".")) {
                    pkg + raw
                } else if ('.' !in raw) {
                    "$pkg.$raw"
                } else {
                    raw
                }
            Intent().setClassName(pkg, cls)
        } else {
            resolve(pm, plain)
        }
    }

    /** 解析不出入口就返回 `null` —— 调用方据此标「不可打开」。 */
    fun resolve(
        pm: PackageManager,
        pkg: String,
    ): Intent? {
        // 注意：**没有代码的包（`android:hasCode="false"`）直接判不可打开**：这类纯资源包声明得了组件、
        //  却一个都起不来（类根本不在包里）。真机上确实踩到 —— OPPO 的 `oplus`
        // （`/system_ext/framework/oplus-framework-res.apk`，**标签也是「Android 系统」**）里有个
        //  `TileLongClickActivity`（`MAIN` + `QS_TILE_PREFERENCES`、`exported`），第三层会把它当成
        //  「这个应用的入口」⇒ 列表里显示成「可以打开」（连「隐藏失效应用」都滤不掉）、动作点下去必失败
        // （用户报「Android 系统打不开但是没有被过滤」就是这个）。读不到 ApplicationInfo 时
        //  也按不可打开处理：连它是什么都问不出来，起它更没戏。
        val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
        if (app.flags and ApplicationInfo.FLAG_HAS_CODE == 0) return null
        // 三层都要包住：这个函数跑在「选择应用」页的整表循环里，一个包查不动不该把
        //  整份查询带崩（第一层裸着的话，抛出去整表返回 null）。
        runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()?.let { return it }
        // TV 图标单列一条路，普通 ROM 上恒为 null
        runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) }.getOrNull()?.let { return it }
        val activity =
            runCatching {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).setPackage(pkg), 0)
            }.getOrNull()
                ?.mapNotNull { it.activityInfo }
                //  这一层只认**真的给用户当入口用**的活动，判据见 [isLaunchable]。
                ?.filter { isLaunchable(it, pkg) }
                ?.minWithOrNull(compareBy({ hintRank(it.name) }, { it.name }))
                ?: return null
        return Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, activity.name))
    }

    /**
     * 分身材质的一条路：主用户的 `PackageManager` **看不见**分身里的包，所以按 userId 走 binder 查
     * （[AppClonePackages.queryLaunchablesAsUser]，与「列分身清单」是同一条路）。
     *
     *  判据与 [resolve] 最后那层一致：只认导出的、没有 `permission` 要求的、没被排除在最近任务外的活动。
     *
     * @return 解析不出入口返回 `null`（调用方据此提示「没有可启动界面」而不是「已卸载」）
     */
    fun resolveAsUser(
        pkg: String,
        userId: Int,
    ): Intent? {
        val resolved =
            AppClonePackages.queryLaunchablesAsUser(
                Intent(Intent.ACTION_MAIN).setPackage(pkg),
                userId,
            ) ?: return null
        val activity =
            resolved
                .mapNotNull { it.activityInfo }
                .filter { isLaunchable(it, pkg) }
                .minWithOrNull(compareBy({ hintRank(it.name) }, { it.name }))
                ?: return null
        return Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, activity.name))
    }

    /**
     * 「**哪些包点得开**」—— 一次问全机的快路，替代「逐包 [resolve]」。
     *
     *  判据与 [resolve] 同一份：`FLAG_HAS_CODE` 那一条由调用方按住手上那份 `ApplicationInfo`
     *  判（这里只给包名，拿不到它），其余全在 [isLaunchable] 里。
     *
     *  为什么需要这一支：「选择应用」页现在**一次就取全部**装着的应用（几百个），
     *  逐包 `resolve` 是几百次 binder 调用；而这里一共只有三次查询 —— 把「能不能打开」
     *  从「每包一问」降成「一次问全机」，这一页才付得起「开关只是过滤、不再重查」那笔账。
     *
     *  `MAIN + LAUNCHER` 与 `MAIN + INFO` 是 [resolve] 前两层认的那两档
     *  （`getLaunchIntentForPackage` 查的就是它们），那两层只要求活动**导出** ——
     *  所以这里也只按导出判，不套 [isLaunchable] 后两条：那种包 [resolve] 一样会给出口子，
     *  判严了就成了「列表标着打不开、其实点得开」。
     */
    fun launchablePackages(pm: PackageManager): Set<String> {
        val pkgs = HashSet<String>()
        // 判据级：这一次查询要是失败（列表太大撑爆 binder 也会走到这里），那一层就整个不作数 ——
        //  行上会一片「不可打开」，日志里得有这句话，否则只能靠猜。
        runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN), 0) }
            .onFailure { XLog.w("查「点得开」：MAIN 那一层没查成，这一层不作数", it) }
            .getOrNull()
            .orEmpty()
            .asSequence()
            .mapNotNull { it.activityInfo }
            .filter { isLaunchable(it, it.packageName) }
            .mapTo(pkgs) { it.packageName }
        for (category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_INFO)) {
            runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
                .onFailure { XLog.w("查「点得开」：$category 那一层没查成", it) }
                .getOrNull()
                .orEmpty()
                .asSequence()
                .mapNotNull { it.activityInfo }
                .filter { it.exported }
                .mapTo(pkgs) { it.packageName }
        }
        return pkgs
    }

    /**
     * 一个活动算不算「给用户当入口用」—— [resolve] 与 [resolveAsUser] 共用这一份判据。
     *
     *  1) 包名必须对得上（查询可能带回别家的活动）；
     *  2) 导出是前提；
     *  3) `permission` 非空的不算 —— 那种活动要调用方持有某个（多半是 signature 级的）权限，
     *     而本模块**零权限**，起它就是 `SecurityException`；
     *  4) `android:excludeFromRecents` 的不算 —— 那是悬浮面板宿主 / 磁贴设置页这类**辅助活动**的
     *     写法（「不进最近任务」正说明它不是「打开这个应用」的入口）。
     *
     *  后两条都是真机踩出来的（症状同为「列表里显示可以打开、按下去打不开」）。
     */
    private fun isLaunchable(
        info: ActivityInfo,
        pkg: String,
    ): Boolean =
        info.packageName == pkg &&
            info.exported &&
            info.permission == null &&
            info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS == 0

    /** 名字里第一个命中的提示词的下标；一个都没命中排最后。 */
    private fun hintRank(name: String): Int {
        val lower = name.lowercase()
        val hit = NAME_HINTS.indexOfFirst { lower.contains(it) }
        return if (hit < 0) NAME_HINTS.size else hit
    }
}

/**
 * 以 **system_server 自己的身份**（uid 1000）执行 [block] —— 注入侧那几条系统服务调用共用这一份。
 *
 * ## 为什么需要换身份
 *
 *  这些反射调用跑在 AMS 的广播 / 按键回调里。整条链路若是从宿主进程的 Binder 事务进来的，
 *  `Binder.getCallingUid()` 拿到的是**宿主的 uid** —— 目标服务一旦按调用方判权限就抛
 *  `SecurityException`，表现是「通知中心 / 控制中心 / 电源菜单按了没反应」。
 *  换成本进程（system_server）的身份之后，目标看到的就是 uid 1000。
 *
 * ## 换完必须还原
 *
 *  `clearCallingIdentity` 改的是**当前线程**的调用方身份，不还原会把 system 身份泄漏给同一线程
 *  后面的调用 —— 那一段代码会以 1000 的身份去访问别人的东西。所以 [block] 用 `try/finally` 包着，
 *  抛异常也照样还原。
 *
 * ## 为什么收成一处
 *
 *  这段原来是 `StatusBarBridge` 与 `VoiceAssistBridge` 各自一份 private 实现，两份逐字相同 ——
 *  而「必须还原」这件事只写在各自的注释里。以后要改（比如加一层日志、或者改成按线程缓存 token），
 *  改一处漏一处就是身份泄漏。
 */
internal inline fun <T> asSystem(block: () -> T): T {
    val token = Binder.clearCallingIdentity()
    return try {
        block()
    } finally {
        Binder.restoreCallingIdentity(token)
    }
}

/** Hook 安装的统一收口，失败只记日志、逐项降级。 */
fun XposedModule.hookGuarded(
    origin: Executable?,
    hooker: XposedInterface.Hooker,
): XposedInterface.HookHandle? {
    if (origin == null) {
        XLog.w("Hook 目标为 null，本次跳过（调用方应已打过更具体的原因）")
        return null
    }
    return try {
        hook(origin)
            // hooker 运行期抛异常时由框架拦下（PASSTHROUGH 会往外抛、DEFAULT 语义不设防）——
            // 这是「失败只降级、不拖垮宿主进程 / system_server」的最后兜底，所有 hook 点都走这一处。
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(hooker)
    } catch (e: Throwable) {
        // Throwable 而非 Exception：框架侧失败有可能是 Error 系，一并吞掉只降级不扩散
        XLog.w("装 Hook 失败（已跳过，后续 Hook 继续）：${origin.declaringClass.name}#${origin.name}", e)
        null
    }
}
