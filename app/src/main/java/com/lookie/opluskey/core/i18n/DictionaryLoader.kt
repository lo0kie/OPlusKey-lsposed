package com.lookie.opluskey.core.i18n

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ModuleInfo
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import java.io.InputStream

/**
 * 字典文件的读取 —— 「从哪拿到那份 JSON 文本」这一件事，全在这里（[I18n] 只管状态与查询）。
 *
 * ## 两条路，按可靠性排
 *
 *  1. **类加载器的资源** —— 模块自己那几个进程走这条（真机实测设置页 `:ui` 能拿到）；
 *  2. **从模块 APK 里直接抠** —— 宿主**主**进程只能走这条。
 *
 *  第二条是必需的：LSPosed 只把模块 **dex** 注进宿主进程，**模块 APK 本身不在那个类加载器的
 *  资源路径上**，所以第一条在宿主主进程里必然落空。真机实测的表现是那句
 *  `未找到国际化资源文件: zh-CN.json`，后果是**宿主侧所有只靠字典的文案退回键名**
 *  （「执行时提示」会显示 `catalog.flashlight` 这种东西）。
 *  界面层那边看不出来，因为它的每一处 `I18n.get` 都带中文兜底 —— 只有 `ActionCatalog` 的
 *  `Spec.label` 是纯字典（刻意的：见那边的说明）。
 *
 *  所以这一层**不是零 Context 依赖**：第二条路要 `PackageManager`（借 [ActivityThreadCompat]）。
 *  拿不到 Context 时返回 `null`，与「读不到字典」同一个结果。
 */
internal object DictionaryLoader {
    /** APK 里字典所在的目录（`assets/locales/`，见 `app/src/main/assets`）。 */
    private const val ASSET_DIR = "assets/locales"

    /**
     * 读出字典文件的**文本**；两条路都落空才返回 `null`（调用方按「读不到字典」处理：
     * 显示键名，不静默退成中文）。
     */
    fun read(fileName: String): String? =
        fromClassLoader(fileName)
            ?: fromModuleApk(fileName)

    /** 第 1 条路：类加载器的资源（候选路径逐个试，第一个读出来的算）。 */
    private fun fromClassLoader(fileName: String): String? =
        listOf(
            "$ASSET_DIR/$fileName",
            "locales/$fileName",
            "/$ASSET_DIR/$fileName",
            "/locales/$fileName",
        ).firstNotNullOfOrNull { path ->
            val stream = openResourceStream(path) ?: return@firstNotNullOfOrNull null
            try {
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: Throwable) {
                XLog.d("读字典资源失败（$path）：${e.message}")
                null
            }
        }

    /** 通过 ClassLoader 取资源输入流（四种取法：本类的、线程上下文的、按绝对路径的两种）。 */
    private fun openResourceStream(path: String): InputStream? {
        val cleanPath = path.removePrefix("/")
        val cl = DictionaryLoader::class.java.classLoader
        return cl?.getResourceAsStream(cleanPath)
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(cleanPath)
            ?: DictionaryLoader::class.java.getResourceAsStream("/$cleanPath")
            ?: DictionaryLoader::class.java.getResourceAsStream(cleanPath)
    }

    /**
     * 第 2 条路：按包名问 `PackageManager` 要自己的 APK，当 zip 读 `assets/locales/<file>`。
     *
     *  包名取 [ModuleInfo]（`core/` 不许依赖根包里的 `KeyModule`，而这条要用它）。
     */
    private fun fromModuleApk(fileName: String): String? =
        try {
            // 传自己的类加载器：`ActivityThread` 是 boot classpath 上的框架类，沿父链找得到
            val loader = DictionaryLoader::class.java.classLoader ?: return null
            val app = ActivityThreadCompat.currentApplication(loader) ?: return null
            val apk = app.packageManager.getApplicationInfo(ModuleInfo.PACKAGE, 0).sourceDir ?: return null
            java.util.zip.ZipFile(apk).use { zip ->
                val entry = zip.getEntry("$ASSET_DIR/$fileName") ?: return null
                zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Throwable) {
            XLog.w("从模块 APK 读字典失败（$fileName）：${e.javaClass.simpleName}: ${e.message}")
            null
        }
}
