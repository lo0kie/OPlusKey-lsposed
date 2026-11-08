package com.lookie.opluskey.bridge

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect
import java.lang.reflect.InvocationTargetException

/**
 * 「把这块屏的背光推到某个值」那一条 —— [StatusBarBridge.CMD_BRIGHTNESS] 的注入侧实现。
 *
 * ## 为什么要走 system_server
 *
 *  `IDisplayManager.setTemporaryBrightness` 在服务那层要
 *  `android.permission.CONTROL_DISPLAY_BRIGHTNESS`（signature|privileged）。宿主
 *  `com.oplus.gesture` 只有 `WRITE_SETTINGS` + `DEVICE_POWER`，调不动；system_server 是 uid 1000，
 *  天然放行（依据见 `docs/device/actions/brightness.md` 38.3）。
 *
 * ## 为什么要调它
 *
 *  控制中心的亮度条只在 `DisplayPowerController.saveBrightnessInfo()` 判成「这块屏的背光变了」时
 *  才刷新（它随后 `sendDisplayEventsIfEnabledLocked(display, 128)` → `onDisplayChanged` →
 *  SystemUI 重读 `screen_brightness`）。自动亮度下 `screen_brightness` 只是「关掉自动后回到的值」，
 *  写它不会让背光按那个值走，条也就不动 —— 小布 `j40.j.r()` 在写库前先调这两个方法，正是为了
 *  这一下（38.2 / 38.3）。
 *
 * ## 独立成一个对象
 *
 *  `StatusBarBridge` 已经贴着 detekt 的「对象内函数数」上限（[StatusBarBridge] 那几条指令各一支
 *  private 实现），再往里塞四个函数就顶破了；这一支与状态栏也本来没关系 —— 它只是借了那条广播。
 */
internal object BrightnessBridge {
    /**
     * 把主屏背光推到 [raw]（raw 尺度，本机 `2..4095`）。
     *
     *  与系统自己拖动亮度条同一条路（`SystemUI/BrightnessController.setBrightness` 调的也是它）。
     *  **不写库**：写库仍归宿主（自动亮度下要写的 `screen_auto_brightness_adj` 是 PRIVATE_SETTINGS，
     *  注入侧也没有 `ContentResolver`）。
     */
    fun apply(
        loader: ClassLoader?,
        raw: Int,
    ) {
        if (raw < 0) {
            XLog.w("亮度指令没带值，跳过")
            return
        }
        val global = displayManager(loader) ?: return
        val value = raw.toFloat()
        try {
            if (!pushTemporaryBrightness(global, value)) return
            XLog.w("已把背光推到 $raw（DisplayManager 临时亮度）")
            pushAutoAdjustment(global, value)
        } catch (e: InvocationTargetException) {
            // 服务端抛的异常包在 InvocationTargetException 里 —— 只打外层等于没打
            XLog.w("setTemporaryBrightness 被拒：${e.cause?.javaClass?.name}: ${e.cause?.message}")
        } catch (e: Throwable) {
            XLog.w("推背光失败（忽略）：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 取 `DisplayManagerGlobal` 实例（`setTemporaryBrightness` 的载体）；取不到就记一行、返回 `null`。 */
    private fun displayManager(loader: ClassLoader?): Any? {
        if (loader == null) {
            XLog.w("还没有 system_server 的 ClassLoader，亮度刷新跳过")
            return null
        }
        val cls =
            Reflect.findClass(loader, Framework.Calls.DISPLAY_MANAGER_GLOBAL) ?: run {
                XLog.w("找不到 ${Framework.Calls.DISPLAY_MANAGER_GLOBAL}，亮度刷新跳过")
                return null
            }
        return Reflect.callStatic(cls, Framework.Calls.GET_INSTANCE) ?: run {
            XLog.w("DisplayManagerGlobal.getInstance() 返回 null，亮度刷新跳过")
            return null
        }
    }

    /** 推临时亮度；方法不存在时记一行并返回 `false`（调用方据此不再往下走）。 */
    private fun pushTemporaryBrightness(
        global: Any,
        value: Float,
    ): Boolean {
        val m =
            Reflect.methodByNameAndArity(
                global.javaClass,
                Framework.Calls.SET_TEMPORARY_BRIGHTNESS,
                2,
            ) ?: run {
                XLog.w("${global.javaClass.name} 上没有 setTemporaryBrightness(int, float)，亮度刷新跳过")
                return false
            }
        m.isAccessible = true
        asSystem { m.invoke(global, 0, value) }
        return true
    }

    /** 第二下：把自动亮度的用户偏移也设成同值；方法不存在时只记一行（不影响上面那一下）。 */
    private fun pushAutoAdjustment(
        global: Any,
        value: Float,
    ) {
        val m =
            Reflect.methodByNameAndArity(
                global.javaClass,
                Framework.Calls.SET_TEMPORARY_AUTO_ADJUSTMENT,
                1,
            )
        if (m == null) {
            XLog.w("${global.javaClass.name} 上没有 setTemporaryAutoBrightnessAdjustment(float)，只推了背光")
            return
        }
        m.isAccessible = true
        asSystem { m.invoke(global, value) }
    }
}
