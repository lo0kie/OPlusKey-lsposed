package com.lookie.opluskey.host.action

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.host.action.MiniWindowFacts.START_DELIVERED_TO_TOP

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
     *  的字节码读出来的 —— 两处 `const/16` 紧跟着 `hasOplusFlag`（详见 `docs/device-findings.md`
     *  二十二）。`hasOplusFlag` 读的是 `Intent.getIntentExt().getOplusFlags()`，**只看调用方
     *  自己挂在 intent 上的 oplus flags**，与「谁调用的」无关 —— 所以不必伪装 callingPackage。
     */
    private const val OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER = 0x800

    /**
     * 给 intent 挂上 [OPLUS_FLAG_MULTI_APP_SKIP_CHOOSER]。
     *
     *  `Intent#addOplusFlags(int)` 是 OPPO 往 AOSP 的 `Intent` 上加的方法，编译期不存在，
     *  只能反射。加不上**不阻断启动**（顶多是选择器照弹），所以返回布尔给调用方记日志。
     */
    private fun markSkipChooser(intent: Intent): Boolean {
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
            .onFailure { XLog.w("退回 Context.startActivity 也失败：$intent", it) }
            .isSuccess
    }

    /** `startActivityAsUser` 两个签名都试一遍。成功返回 `true`。 */
    @Suppress("SpreadOperator")
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
            //  `invoke` 不抛 **不等于** 起得来：这个方法返回 `ActivityStarter` 的 `START_*`，
            //  被后台启动策略拦下时同样是正常返回（`START_CANCELED` / 负的 error code）。
            //  只看「没抛」会把拒绝报成「已下发启动」，用户侧则是一片静默。
            val code = thrown.getOrNull() as? Int
            if (thrown.isSuccess) {
                // 成功码只有 0..3（`START_SUCCESS` / `START_RESTART` / `START_DELAY_RESUME` /
                //  `START_DELIVERED_TO_TOP`）。`code >= 0` 会把 **101/102**
                //  （`START_RETURN_LOCK_TASK_MODE_VIOLATION` / `START_ABORTED`，后台启动被拦时正是它）
                //  也当成成功 —— 失败提示与回退逻辑整条都不触发。
                if (code != null && code in 0..START_DELIVERED_TO_TOP) {
                    //  只打落点：整个 Intent 会带上 data / extras，那是用户写进命令里的东西
                    val at = intent.component?.flattenToShortString() ?: intent.action.orEmpty()
                    XLog.w("已下发启动：$at（user=$userId，arity=$arity，START_*=$code）")
                    return true
                }
                XLog.w("startActivityAsUser(user=$userId, arity=$arity) 被系统拒（START_*=$code），没有起界面")
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
