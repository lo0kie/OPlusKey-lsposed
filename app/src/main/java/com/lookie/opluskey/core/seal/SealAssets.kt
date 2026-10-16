package com.lookie.opluskey.core.seal

import android.content.Context
import android.content.pm.PackageManager
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ModuleInfo
import java.util.zip.ZipFile

/**
 * 「按包名问自己要一份资产」—— 两条路，按可靠性排。
 *
 *  1. `getResourcesForApplication`：模块自己那几个进程走这条；
 *  2. **把模块 APK 当 zip 读**：宿主**主**进程只能走这条。
 *
 *  第二条是必需的：LSPosed 只把模块 **dex** 注进宿主进程，模块 APK 本身不在那个类加载器的资源
 *  路径上（同一条账见 `core/i18n/DictionaryLoader`）。
 *
 *  两条都不能用 `context.assets` —— 那是**宿主** APK 的资源，读出来的是别人的东西。
 */
internal object SealAssets {
    /** 读一份资产；读不到返回 `null`，并留一句 `w`（与「文件本来就不该在」分得开）。 */
    fun bytes(
        context: Context,
        name: String,
    ): ByteArray? =
        fromResources(context, name)
            ?: fromApkZip(context, name)
            ?: missing(name)

    private fun fromResources(
        context: Context,
        name: String,
    ): ByteArray? =
        try {
            context.packageManager
                .getResourcesForApplication(ModuleInfo.PACKAGE)
                .assets
                .open(name)
                .use { it.readBytes() }
        } catch (e: Throwable) {
            XLog.d("按资源读 $name 未成：${e.javaClass.simpleName}")
            null
        }

    private fun fromApkZip(
        context: Context,
        name: String,
    ): ByteArray? =
        try {
            val apk =
                context.packageManager
                    .getApplicationInfo(ModuleInfo.PACKAGE, PackageManager.ApplicationInfoFlags.of(0))
                    .sourceDir ?: return null
            ZipFile(apk).use { zip ->
                val entry = zip.getEntry(name) ?: return null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (e: Throwable) {
            XLog.d("按 zip 读 $name 未成：${e.javaClass.simpleName}")
            null
        }

    private fun missing(name: String): Nothing? {
        XLog.w("模块资产读不到（退成兜底形态）：$name")
        return null
    }
}
