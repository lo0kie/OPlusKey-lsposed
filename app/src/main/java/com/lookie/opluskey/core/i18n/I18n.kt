package com.lookie.opluskey.core.i18n

import androidx.compose.runtime.mutableStateOf
import com.lookie.opluskey.core.log.XLog
import org.json.JSONObject
import java.io.InputStream
import java.util.Locale

/** 支持的界面语言档位。 */
enum class AppLanguage(val code: String, val label: String) {
    AUTO("auto", "跟随系统"),
    ZH_CN("zh_CN", "简体中文"),
    EN("en", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AUTO
    }
}

/**
 * 基于 JSON 字典的国际化管理器。
 *
 * 字典文件存放在 assets/locales/（如 zh-CN.json, en.json）。
 * 加载方式：通过 ClassLoader 读取 APK 内 assets/locales/ 纯文本并解析为 JSONObject。
 * 零 Context 依赖、无 Resources 跨包隔离问题，保证在 LSPosed 宿主与系统进程中稳定运行。
 */
object I18n {

    @Volatile
    var currentLanguage: AppLanguage = AppLanguage.AUTO
        set(value) {
            field = value
            reload()
        }

    /** 实际生效语言（AUTO 时由系统语言决出）。 */
    val effectiveLanguage: AppLanguage
        get() = when (currentLanguage) {
            AppLanguage.AUTO -> {
                val loc = Locale.getDefault()
                if (loc.language.equals("zh", ignoreCase = true)) AppLanguage.ZH_CN else AppLanguage.EN
            }

                        else -> currentLanguage
        }

    /** 当前是否中文环境。 */
    val isChinese: Boolean
        get() = effectiveLanguage == AppLanguage.ZH_CN

    /**
     * 当前这一档语言的字典（key -> localized text）—— 用 **Compose 状态**持有。
     *
     * [get] 读的就是它，于是**组合期**里每取一次文案就订阅了一回「换了字典」：切档之后页面上
     * 那些文案当场重画（此前是普通 Map，切档只换了内存里那份，界面没有任何理由重组 ——
     * 要退出页面再进来才看得到新语言）。
     *
     * 放在 core 这一层是安全的：注入侧（`inject/`）不引用本类，所以那两个被注入的进程不会
     * 因为这一行去加载 compose 运行时。
     */
    private val strings = mutableStateOf<Map<String, String>>(emptyMap())

    /** [strings] 里现在装的是哪一份文件 —— 与要读的那份一致就不必重读（见 [reload]）。 */
    @Volatile
    private var loadedFile: String? = null

    init {
        reload()
    }

    /**
     * 重载语言字典。
     *
     *  同一份文件不重读：语言是**跟着配置**走的（每次配置落盘都会重设一遍，见
     *  `ui/state/ConfigActions.write`），不设这道闸，拨任何一个开关都要把 JSON 再解一遍。
     */
    fun reload() {
        val targetLang = effectiveLanguage
        val fileName = when (targetLang) {
            AppLanguage.ZH_CN -> "zh-CN.json"
            AppLanguage.EN -> "en.json"
            AppLanguage.AUTO -> "zh-CN.json"
        }
        if (fileName == loadedFile) return
        loadedFile = fileName
        strings.value = loadJson(fileName)
    }

    /**
     * 从 APK 内部读取指定路径的 JSON 并解析为 Map。
     */
    private fun loadJson(fileName: String): Map<String, String> {
        val candidates = listOf(
            "assets/locales/$fileName",
            "locales/$fileName",
            "/assets/locales/$fileName",
            "/locales/$fileName",
        )
        var stream: InputStream? = null
        for (c in candidates) {
            stream = openResourceStream(c)
            if (stream != null) break
        }
        if (stream == null) {
            XLog.w("未找到国际化资源文件: $fileName")
            return emptyMap()
        }
        return try {
            val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(content)
            val result = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = json.optString(k, "")
            }
            result
        } catch (e: Throwable) {
            XLog.w("解析国际化 JSON 失败: $fileName", e)
            emptyMap()
        }
    }

    /**
     * 通过 ClassLoader 尝试获取资源输入流。
     */
    private fun openResourceStream(path: String): InputStream? {
        val cleanPath = path.removePrefix("/")
        val cl = I18n::class.java.classLoader
        return cl?.getResourceAsStream(cleanPath)
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(cleanPath)
            ?: I18n::class.java.getResourceAsStream("/$cleanPath")
            ?: I18n::class.java.getResourceAsStream(cleanPath)
    }

    /**
     * 获取指定 key 的国际化文本。
     * @param key 字典中的键（例如 "setting.key_binding"）
     * @param default 找不到对应 key 时的兜底文案（默认返回 key 本身）
     */
    fun get(key: String, default: String = key): String =
        strings.value[key] ?: default

    /**
     * 带格式化参数的国际化文本获取（`%s` / `%d`）。
     *
     *  **带参数必须走这一个入口，不能写成 `get(key, 参数)`**：[get] 的第二个参数是「兜底文案」，
     *  而它比 vararg 那支更具体 —— `get(key, 参数)` 会被解析到 [get] 上，于是模板原样留在界面上
     *  （真机踩到：「打开应用:%s」「以小窗口打开:%s」、删除确认里那个 `%s`）。
     *  所以这里**不叫** `get`：名字分开，编译器才拦得住这种写法。
     *
     *  字典里没有这个键时用 key 本身当模板（与 [get] 的兜底同一条口径）。
     */
    fun format(key: String, vararg args: Any): String {
        val pattern = strings.value[key] ?: key
        return try {
            if (args.isEmpty()) pattern else String.format(pattern, *args)
        } catch (e: Throwable) {
            pattern
        }
    }

    /** 操作符重载，支持 I18n["key"] 形式快捷调用。 */
    operator fun get(key: String): String = get(key, key)
}
