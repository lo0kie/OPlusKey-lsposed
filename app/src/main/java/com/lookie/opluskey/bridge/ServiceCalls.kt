package com.lookie.opluskey.bridge

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 在 system_server 里按名字取服务、按「名字 + 参数个数」调方法的两个小工具。
 *
 *  从 `SettingCalls` 抽出来是为了两件事：那个 object 的函数数顶到 detekt 上限；
 *  以及这两件事本来就不属于「切开关」—— 别的注入侧调用（状态栏 / 小窗）要按名字调服务时也能用。
 */
internal object ServiceCalls {
    /** system_server 自己的 uid —— 造 AttributionSource / 传包名时都用它。 */
    private const val SYSTEM_UID = 1000

    /** 造 `AttributionSource` 时的 attribution tag —— 系统身份调蓝牙那几个方法时**必须**给一个。 */
    private const val ATTRIBUTION_TAG = "opluskey"

    /**
     * 按服务名取**可反射的对象**（取不到返回 `null`）。
     *
     *  注入侧拿到的是两种东西：
     *   · 跑在 system_server 里的服务（wifi / connectivity / window / uimode / bluetooth_manager）——
     *     拿到的就是实现类本身（`Binder` 子类），反射直接能调；
     *   · 跑在**别的进程**里的服务（nfc 在 `com.android.nfc`、phone 在 `com.android.phone`）—— 拿到的
     *     是 `BinderProxy`，**它上面一个业务方法都没有**（只有 `transact`），反射必然「没命中」
     *     （真机实测：`isEnabled/0：BinderProxy 上没有这个方法`）。这种要过一遍 AIDL 的
     *     `Stub.asInterface(binder)` 才变成接口对象。
     *
     * @param name `ServiceManager` 里的名字（`service list` 那份）
     * @param iface AIDL 接口全名，用来找它的 `$Stub`（`android.nfc.INfcAdapter`）
     */
    fun service(
        loader: ClassLoader,
        name: String,
        iface: String,
    ): Any? {
        val binder =
            Reflect
                .findClass(loader, Framework.Calls.SERVICE_MANAGER)
                ?.let { Reflect.callStatic(it, Framework.Calls.GET_SERVICE, name) }
        if (binder == null) {
            XLog.w("取不到服务 $name")
            return null
        }
        if (!binder.javaClass.name.contains("BinderProxy")) return binder
        val stub = Reflect.findClass(loader, "$iface\$Stub")
        if (stub == null) {
            XLog.w("$name 是 BinderProxy，但找不到 $iface\$Stub")
            return binder
        }
        return Reflect.callStatic(stub, "asInterface", binder) ?: binder
    }

    /**
     * 造一个 `AttributionSource`（uid 1000、包名 `android`、tag `opluskey`）。
     *
     *  蓝牙那几个方法在 Android 13+ 上都改成了 `enable(AttributionSource)` /
     *  `disable(AttributionSource, boolean)` —— 无参 / 只有一个 boolean 的几版已经删掉了。
     *  **tag 不能是 null**：系统身份调会被 `checkCallingAttributionSource` 直接拒
     *  （真机实测 `IllegalArgumentException: System generic caller must set the Attribution tag`）。
     */
    fun attributionSource(
        loader: ClassLoader,
        packageName: String = "android",
    ): Any? {
        val cls =
            Reflect.findClass(loader, "android.content.AttributionSource") ?: run {
                XLog.w("找不到 android.content.AttributionSource")
                return null
            }
        runCatching {
            return cls
                .getConstructor(Int::class.java, String::class.java, String::class.java)
                .newInstance(SYSTEM_UID, packageName, ATTRIBUTION_TAG)
        }.onFailure { XLog.w("AttributionSource 三参构造不可用：${it.javaClass.simpleName}") }
        return runCatching {
            val builderCls = Reflect.findClass(loader, "android.content.AttributionSource\$Builder")
            if (builderCls == null) {
                XLog.w("找不到 AttributionSource\$Builder")
                return null
            }
            val builder = builderCls.getConstructor(Int::class.java).newInstance(SYSTEM_UID)
            builderCls.getMethod("setPackageName", String::class.java).invoke(builder, packageName)
            runCatching {
                builderCls
                    .getMethod("setAttributionTag", String::class.java)
                    .invoke(builder, ATTRIBUTION_TAG)
            }
            builderCls.getMethod("build").invoke(builder)
        }.onFailure {
            XLog.w("AttributionSource.Builder 也不可用：${it.javaClass.simpleName}: ${it.message}")
        }.getOrNull()
    }

    /** 「调到了」的凭据 —— 方法本身返回 void 时返回值是 null，不能拿它当失败判据。 */
    class Called(
        val value: Any?,
    )

    /**
     * 调一个方法：按顺序试几组实参，第一组能命中的就调。
     *
     * @return 命中且调用成功 = [Called]（里带着返回值，void 方法里是 null）；没命中 / 抛异常 = null
     */
    @Suppress("SpreadOperator")
    fun call(
        target: Any,
        name: String,
        vararg argSets: List<Any?>,
    ): Called? {
        val sets = if (argSets.isEmpty()) arrayOf(emptyList<Any?>()) else argSets
        for (args in sets) {
            val m = Reflect.methodByNameAndArity(target.javaClass, name, args.size) ?: continue
            return try {
                m.isAccessible = true
                Called(m.invoke(target, *args.toTypedArray()))
            } catch (e: Throwable) {
                // 真正的拒绝原因在 cause 里（`InvocationTargetException` 自己的 message 恒为 null）
                val cause = Reflect.deepestCause(e)
                XLog.w("调 $name/${args.size} 被拒：${cause.javaClass.simpleName}: ${cause.message}")
                null
            }
        }
        // 一个候选都没命中：把接收者是谁打出来 —— 是「服务对象不对」还是「签名不对」一眼能分
        XLog.w("$name/${sets.firstOrNull()?.size ?: 0}：${target.javaClass.simpleName} 上没有这个方法")
        return null
    }
}
