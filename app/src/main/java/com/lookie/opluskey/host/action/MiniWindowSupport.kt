package com.lookie.opluskey.host.action

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 「这个应用能不能**以小窗打开**」—— 与执行侧同源的一把尺子（判据是框架自己的
 * `OplusZoomWindowManager#isSupportZoomMode(pkg, userId, cls, Bundle)`，宿主 boot classpath 上
 * 按名字反射即可）。
 *
 * 注意：**它不是纯查询：判「不支持」时框架会顺手弹一条系统 toast「此应用尚未支持浮窗」**
 * （传空 bundle 也一样，与 `extra_window_mode` 无关）。所以**只能一问一答地用在用户刚做了某个
 * 动作的时刻，绝不能拿它整表轮询**（那会排出一串 toast）；框架里也没有可替代的静默判据。
 * 证据见 `docs/device-findings.md` 附十三之四。
 *
 * **三态的理由**：读不到 / 调不通时返回 `null` 说「不知道」，界面据此**不提示也不拦** ——
 * 绝不能默认成 `true` 或 `false`（那是拿假判据误导用户）。
 *
 * 注意：**别拿 `ActivityInfo.resizeMode` 当判据**：真机上游戏也是 `0` 却能起成小窗
 * （见 `docs/device-findings.md` 附十三之三）。
 */
internal object MiniWindowSupport {

    /**
     * 小窗状态门面（`oplus-framework.jar`，宿主 boot classpath 上反射可及）。
     *
     *  **同包的唯一一份**：[AppLauncher] 取 `zoomRect`（判「窗口多大」）也走它
     *（同包只留一份）。
     */
    internal const val MANAGER = "com.oplus.zoomwindow.OplusZoomWindowManager"

    /** 框架判「这个应用支不支持小窗」的方法名。 */
    private const val METHOD = "isSupportZoomMode"

    /** `extra_window_mode` 的取值 —— 与 `startZoomWindow` 那份 bundle 一致（`WINDOWING_MODE_ZOOM`）。 */
    private const val WINDOWING_MODE_ZOOM = 100

    private const val EXTRA_WINDOW_MODE = "extra_window_mode"

    /** `isSupportZoomMode` 的形参个数（包名 / userId / 活动类名 / bundle）。 */
    private const val ARITY = 4

    /**
     * @param cls 该应用的启动活动类名（`AppEntry.resolve(...)?.component?.className`）。
     *   调用方**已经**解过一次入口了，别再解一遍 —— 这个函数在选择应用页要跑几百次。
     *   真机核对过：传空串照样判得准（`com.heytap.market` 也返回 `false`）。
     * @return `true` = 支持；`false` = 不支持；`null` = **不知道**（反射不通 / 调用抛了）
     */
    fun isSupported(pm: PackageManager, pkg: String, cls: String?): Boolean? = try {
        val managerCls = Reflect.findClass(javaClass.classLoader, MANAGER)
        val manager = managerCls?.let { Reflect.callStatic(it, "getInstance") }
        val method = managerCls?.let { Reflect.methodByNameAndArity(it, METHOD, ARITY) }
        if (manager == null || method == null) {
            XLog.d("小窗支持性：$pkg 判不出来（找不到 $MANAGER#$METHOD/$ARITY）")
            null
        } else {
            val bundled = Bundle().apply { putInt(EXTRA_WINDOW_MODE, WINDOWING_MODE_ZOOM) }
            val result = Reflect.call(
                manager, method, pkg, currentUserId(), cls.orEmpty(), bundled,
            ) as? Boolean
            XLog.d("小窗支持性：$pkg（$METHOD）→ $result")
            result
        }
    } catch (e: Throwable) {
        XLog.d("小窗支持性：$pkg 判不出来（${e.javaClass.simpleName}: ${e.message}）")
        null
    }

    /** `Process.myUid()` 里一个用户占的区间（`UserHandle.PER_USER_RANGE` 的等价常量）。 */
    private const val PER_USER_RANGE = 100000

    /**
     * 当前用户 id —— `UserHandle.getUserId` 是隐藏 API，拿不到就按 [PER_USER_RANGE] 折。
     *
     *  **同包的唯一一份**：[AppLauncher] 解析包名（含工作资料里那份）也要它
     *（同包只留一份）。放在本文件是因为它是 object、[AppLauncher]
     *  是类 —— 类的成员函数跨不过来。
     */
    internal fun currentUserId(): Int =
        Reflect.callStatic(UserHandle::class.java, "getUserId", Process.myUid()) as? Int
            ?: (Process.myUid() / PER_USER_RANGE)
}
