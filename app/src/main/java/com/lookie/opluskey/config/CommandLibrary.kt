package com.lookie.opluskey.config

import com.lookie.opluskey.XLog
import org.json.JSONArray
import org.json.JSONObject

/** 自定义命令库的读写：JSON 编解码加文件 IO（`org.json` 平台自带，不引依赖）。 */
object CommandLibrary {

    /** 当前格式版本，解析时不做版本兼容，只写进文件备查。 */
    const val VERSION = 1

    private const val FIELD_VERSION = "version"
    private const val KEY_COMMANDS = "commands"
    private const val FIELD_NAME = "name"
    private const val FIELD_KIND = "kind"
    private const val FIELD_STEPS = "steps"
    private const val FIELD_DELAY = "stepDelayMs"
    private const val FIELD_SINGLE = "single"

    /** 执行侧缓存的存活时长（毫秒）。 */
    private const val CACHE_MS = 1000L

    /** 清洗命令名：去掉换行（换行会撑开列表行），掐掉首尾空白。存库 / 比对用。 */
    fun sanitizeName(raw: String): String = sanitizeNameInput(raw).trim()

    /**
     * 输入侧清洗：只把换行换成空格，**不 trim** —— 名称框边输边调它，
     * 掐掉首尾空白的话空格就打不进去（打一个被吃一个）。
     */
    fun sanitizeNameInput(raw: String): String = raw
        .map { if (it == '\n' || it == '\r') ' ' else it }
        .joinToString("")

    /** 解析命令库文本，读不懂的条目跳过，绝不抛异常。 */
    fun parse(text: String): List<Command> = try {
        val arr = JSONObject(text).optJSONArray(KEY_COMMANDS)
        if (arr == null) {
            emptyList()
        } else {
            // 名字是主键：同一名字出现多次时**后写的覆盖先写的**
            //  —— 必须显式这么写：`distinctBy { it.name }` 留的是**第一个**，与上面这句话相反。
            val byName = LinkedHashMap<String, Command>()
            for (i in 0 until arr.length()) {
                val cmd = arr.optJSONObject(i)?.let(::commandOf) ?: continue
                byName[cmd.name] = cmd
            }
            byName.values.toList()
        }
    } catch (e: Throwable) {
        XLog.w("命令库解析失败（按空表处理）：${e.message}")
        emptyList()
    }

    /**
     * 取一个字符串字段。不能直接用 `optString(key, fallback)`：键不存在才走 fallback，
     * 显式写成 `"single": null` 的 JSON 会返回字符串 "null"（`JSONObject.NULL.toString()`），
     * `Action.parse("null")` 兜底成 `Shell("null")`，按那个手势就真去跑 `sh -c null` 而非「无操作」。
     * 这些 KDoc 恰恰邀请手改文件（缩进两格），所以显式挡掉 isNull。
     */
    private fun textOf(o: JSONObject, key: String, fallback: String = ""): String =
        if (o.isNull(key)) fallback else o.optString(key, fallback)

    /** 一条命令；名字为空 / 对象坏了都返回 null（跳过它）。 */
    private fun commandOf(o: JSONObject): Command? {
        val name = sanitizeName(textOf(o, FIELD_NAME))
        if (name.isEmpty()) return null

        // 认不出的类型（版本比我们新）按单条处理，至少不会整条丢掉
        val kind = CommandKind.fromId(textOf(o, FIELD_KIND)) ?: CommandKind.SINGLE
        if (kind != CommandKind.SERIAL) {
            return Command(name = name, kind = CommandKind.SINGLE, single = textOf(o, FIELD_SINGLE))
        }

        val steps = o.optJSONArray(FIELD_STEPS)
        val stepList = if (steps == null) {
            emptyList()
        } else {
            (0 until steps.length())
                .map { if (steps.isNull(it)) "" else steps.optString(it).trim() }
                .filter { it.isNotEmpty() }
        }
        return Command(
            name = name,
            kind = CommandKind.SERIAL,
            steps = stepList,
            stepDelayMs = o.optInt(FIELD_DELAY, Command.DEFAULT_STEP_DELAY_MS)
                .coerceIn(Command.MIN_STEP_DELAY_MS, Command.MAX_STEP_DELAY_MS),
        )
    }

    /** 序列化成命令库文本，缩进两格。 */
    fun serialize(commands: List<Command>): String {
        val arr = JSONArray()
        commands.forEach { c ->
            val o = JSONObject()
                .put(FIELD_NAME, c.name)
                .put(FIELD_KIND, c.kind.id)
            if (c.kind == CommandKind.SERIAL) {
                o.put(FIELD_STEPS, JSONArray(c.steps))
                o.put(FIELD_DELAY, c.stepDelayMs)
            } else {
                o.put(FIELD_SINGLE, c.single)
            }
            arr.put(o)
        }
        return JSONObject()
            .put(FIELD_VERSION, VERSION)
            .put(KEY_COMMANDS, arr)
            .toString(2) + "\n"
    }

    /** 读命令库；文件不存在或读不了都返回空表（不抛）。 */
    fun load(): List<Command> = try {
        val f = ConfigStore.commandFile()
        if (f.isFile) parse(f.readText(Charsets.UTF_8)) else emptyList()
    } catch (e: Throwable) {
        XLog.w("读命令库失败（按空表处理）：${e.message}")
        emptyList()
    }

    /** 带缓存的读，给执行侧用：按键每次都要展开 `cmd:` 动作，不能每次读盘，缓存 1s。 */
    fun loadCached(): List<Command> {
        // 用 elapsedRealtime 而非 currentTimeMillis：后者会被改表 / 网络校时回拨，
        // 回拨后 now - cacheAt 为负、缓存永不刷新。
        val now = android.os.SystemClock.elapsedRealtime()
        val hit = cache
        if (hit != null && now - cacheAt < CACHE_MS) return hit
        val fresh = load()
        cache = fresh
        cacheAt = now
        return fresh
    }

    /**
     * 写命令库；失败只记日志。必须走 [ConfigStore.writeAtomically]（先 `.tmp` 再 rename）：
     * 写方是设置页 `:ui` 进程、读方是宿主主进程（`hook/CustomCommandRunner` 按键时现读），
     * 同 uid 不同进程 —— 直接 `writeText`（先截断再写）撞上读方就会读到半截 JSON，那一下
     * 按键的所有 `cmd:` 都报「命令库里没有」；写盘中途抛异常更会把命令库永久留在坏文件上。
     */
    fun save(commands: List<Command>): Boolean {
        val ok = ConfigStore.writeAtomically(
            ConfigStore.commandFile(),
            serialize(commands),
            openReadable = false,
        )
        cache = null
        cacheAt = 0L
        return ok
    }

    /** 执行侧的缓存。 */
    @Volatile
    private var cache: List<Command>? = null

    @Volatile
    private var cacheAt: Long = 0L

    /**
     * 判断 `cmd:<名字>` 指向的是不是多条命令（[CommandKind.SERIAL]），非 `cmd:` 或名字查不到一律 `false`。
     * 两个调用方都在宿主进程侧：界面（长按 tab 要不要显示连续触发）与 [ConfigBroadcast.intent]
     * （把这一位随配置广播带给注入侧）；判定侧（system_server）用广播来的 `KeyConfig.longIsSerial`。
     */
    fun isSerial(commands: List<Command>, token: String): Boolean {
        if (!token.startsWith(Command.PREFIX)) return false
        val name = token.removePrefix(Command.PREFIX)
        return commands.firstOrNull { it.name == name }?.kind == CommandKind.SERIAL
    }
}
