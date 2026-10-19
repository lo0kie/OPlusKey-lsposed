package com.lookie.opluskey.core.i18n

import com.lookie.opluskey.core.log.XLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.Locale

/** 支持的界面语言档位。 */
enum class AppLanguage(
    val code: String,
) {
    AUTO("auto"),
    ZH_CN("zh_CN"),
    EN("en"),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AUTO
    }
}

/**
 * 基于 JSON 字典的国际化管理器。
 *
 * 字典文件存放在 assets/locales/（如 zh-CN.json, en.json）。
 *
 * **文件从哪来**归 [DictionaryLoader]（两条路：类加载器的资源、从模块 APK 里抠 —— 后者是宿主
 * 主进程唯一能走通的那条，理由与后果都写在那边）；本类只管**状态与查询**：当前语言、字典表、
 * 界面层的订阅口。
 *
 * 读不到字典时**显示键名**（`I18n.get` 的兜底就是 key），不静默退成某一种语言 ——
 * 字典缺项要看得见（覆盖由 CI 逐键核对）。
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
        get() =
            when (currentLanguage) {
                AppLanguage.AUTO -> {
                    val loc = Locale.getDefault()
                    if (loc.language.equals("zh", ignoreCase = true)) AppLanguage.ZH_CN else AppLanguage.EN
                }

                else -> {
                    currentLanguage
                }
            }

    /** 当前是否中文环境。 */
    val isChinese: Boolean
        get() = effectiveLanguage == AppLanguage.ZH_CN

    /**
     * 当前这一档语言的字典（key -> localized text）—— **普通 Map，不是 Compose 状态**。
     *
     *  为什么不能是 Compose 状态：本类被**注入侧**与宿主进程双头使用，而 CI 有一条
     *  「注入侧不得引用界面层依赖」的 grep 把 `core/` 一并圈在范围内（见
     *  `.github/workflows/build.yml`）—— 这一层出现 `androidx.*` 就等于把发版断在 CI 上。
     *  界面要的「切档当场重画」走 [reader] 那道口（见 [installUiBridge]）。
     */
    @Volatile
    private var strings: Map<String, String> = emptyMap()

    /**
     * 界面层装的字典读取器 —— 装了之后 [get] / [format] 一律经它取表（见 [installUiBridge]）。
     *
     *  没装时直接读 [strings]：宿主进程里的执行侧、日志那几处调用点不在组合期，不需要订阅。
     */
    @Volatile
    private var reader: (() -> Map<String, String>)? = null

    /** 换过字典之后的回调（界面层用它刷新自己那份状态），没装时为 null。 */
    @Volatile
    private var changed: (() -> Unit)? = null

    /** [strings] 里现在装的是哪一份文件 —— 与要读的那份一致就不必重读（见 [reload]）。 */
    @Volatile
    private var loadedFile: String? = null

    /**
     * 读字典失败过几次（成功时归零）—— 只用来给 [dict] 那道重试闸封顶。
     *
     *  **必须在 [init] 之前声明**：Kotlin 按声明顺序跑属性初始化与 `init` 块，而 [init] 会调
     *  [reload] → [loadJson]，那里就要读写这个字段。写在 `init` 之后的话，类初始化那一刻它还是
     *  `null` —— 真机踩到过：首次读字典报
     *  `NullPointerException ... AtomicInteger.set(int)`，被 [loadJson] 的 catch 当成「解析失败」，
     *  于是**每个进程的第一次取文案都白跑一趟**（结果不错，靠的是 [dict] 的重试，所以界面层
     *  一直没暴露）。这个字段本身与 `AtomicInteger` 的构造都没有副作用，提前声明是安全的。
     */
    private val loadAttempts =
        java.util.concurrent.atomic
            .AtomicInteger()

    /** 一个进程里最多试这么多次；见 [dict]。 */
    private const val MAX_LOAD_ATTEMPTS = 3

    init {
        reload()
    }

    /**
     * 界面层装配字典订阅 —— 建页时装一次（见 `ui/state/I18nBridge.kt`）。
     *
     *  @param read 组合期取字典的出口：界面层在里面读自己那份 Compose 状态，于是「取一次文案」
     *    本身变成一次状态读取 —— 换字典时组合会重组，调用点一个字不用改。
     *  @param onChanged 换过字典之后的通知：界面层据此把新表灌进那份状态。装配时立刻调一次，
     *    免得装配之前已经 [reload] 过的那一份表留在界面层外面。
     */
    fun installUiBridge(
        read: () -> Map<String, String>,
        onChanged: () -> Unit,
    ) {
        reader = read
        changed = onChanged
        runCatching { onChanged() }
    }

    /** 当前字典（只读现值）—— 给界面层那份状态灌值用，见 [installUiBridge]。 */
    fun snapshot(): Map<String, String> = strings

    /**
     * 取表：装了界面层的读取器就走它（组合期读它 = 订阅），否则读本地这一份。
     *
     *  空表时**有限重试**：`I18n` 的 `<clinit>` 可能在 Application 挂上之前就跑了（宿主进程里
     *  首次取文案的时机由手势决定，一般在挂上之后，但不能假设），那时第二条路拿不到 Context。
     *  上限 [MAX_LOAD_ATTEMPTS] 次 —— 字典真的读不到时，不能让每一次取文案都去开一次 zip。
     */
    private fun dict(): Map<String, String> {
        reader?.let { return it() }
        if (strings.isEmpty() && loadAttempts.get() < MAX_LOAD_ATTEMPTS) reload()
        return strings
    }

    /**
     * 重载语言字典。
     *
     *  同一份文件不重读：语言是**跟着配置**走的（每次配置落盘都会重设一遍，见
     *  `ui/state/ConfigWrite.write`），不设这道闸，拨任何一个开关都要把 JSON 再解一遍。
     */
    fun reload() {
        val targetLang = effectiveLanguage
        val fileName =
            when (targetLang) {
                AppLanguage.ZH_CN -> "zh-CN.json"
                AppLanguage.EN -> "en.json"
                AppLanguage.AUTO -> "zh-CN.json"
            }
        if (fileName == loadedFile) return
        // 读失败（`null`）**不记账**：记了的话，同语言下再调 [reload] 会被上面那行直接短路，
        //  字典就一直是空/旧的那份，直到用户换一次语言才重试。
        val map = loadJson(fileName) ?: return
        loadedFile = fileName
        strings = map
        // 通知界面层换表 —— 它那边只是写一次状态，抛异常也只是这次不重画，不该把 reload 打断
        runCatching { changed?.invoke() }
            .onFailure { XLog.w("通知界面层换字典失败（已忽略）：${it.javaClass.simpleName}") }
    }

    /**
     * 从 APK 内部读取指定路径的 JSON 并解析为 Map；读不到 / 解析不了返回 `null`。
     *
     *  返回 `null` 而不是空表：[reload] 要据此决定「这次算不算读到了」—— 空表与读失败
     *  在它是两件事（见那边的说明）。
     */
    private fun loadJson(fileName: String): Map<String, String>? {
        val content = DictionaryLoader.read(fileName)
        if (content == null) {
            loadAttempts.incrementAndGet()
            XLog.w("未找到国际化资源文件: $fileName")
            return null
        }
        return try {
            // 字典是一张扁平的 `key -> 文本` 表；非标量 / JSON `null` 都当空串（缺文案好过整份崩）
            val result =
                Json
                    .parseToJsonElement(content)
                    .jsonObject
                    .mapValues { (_, v) -> (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty() }
            loadAttempts.set(0)
            result
        } catch (e: Throwable) {
            XLog.w("解析国际化 JSON 失败: $fileName", e)
            null
        }
    }

    /**
     * 获取指定 key 的国际化文本。
     * @param key 字典中的键（例如 "setting.key_binding"）
     * @param default 找不到对应 key 时的兜底文案（默认返回 key 本身）
     */
    fun get(
        key: String,
        default: String = key,
    ): String = dict()[key] ?: default

    /**
     * 带格式化参数的国际化文本获取（`%s` / `%d`）。
     *
     *  **带参数必须走这一个入口，不能写成 `get(key, 参数)`**：[get] 的第二个参数是「兜底文案」，
     *  而它比 vararg 那支更具体 —— `get(key, 参数)` 会被解析到 [get] 上，于是模板原样留在界面上
     *  （真机踩到：「打开应用:%s」「以小窗口打开:%s」、删除确认里那个 `%s`）。
     *  所以这里**不叫** `get`：名字分开，编译器才拦得住这种写法。
     *
     *  字典里没有这个键时用 key 本身当模板（与 [get] 的兜底同一条口径）。
     *
     *  格式化**显式给 `Locale.ROOT`**：不带 Locale 的 `String.format` 吃系统区域设置 ——
     *  阿拉伯语环境里 `%d` 会被写成阿拉伯-印度数字（`٣`），界面上是数字的地方全变样。
     *  我们自己的模板里 `%s` 占绝大多数（应用名、命令名），一律按原样插入才对。
     */
    fun format(
        key: String,
        vararg args: Any,
    ): String {
        val pattern = dict()[key] ?: key
        return try {
            if (args.isEmpty()) pattern else String.format(Locale.ROOT, pattern, *args)
        } catch (e: Throwable) {
            //  占位符与参数个数不匹配是**代码 bug**，静默返回原模板会让界面留一个裸 `%s`
            //  而日志里什么都没有 —— 留一行，别让这类问题只能靠肉眼发现。
            XLog.w("i18n：$key 的模板与 ${args.size} 个参数对不上（按原样返回）：${e.javaClass.simpleName}")
            pattern
        }
    }

    /** 操作符重载，支持 I18n["key"] 形式快捷调用。 */
    operator fun get(key: String): String = get(key, key)
}
