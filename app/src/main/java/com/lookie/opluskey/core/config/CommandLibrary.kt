package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.isSerialCommand
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自定义命令库的读写：JSON 编解码加文件 IO（`org.json` 平台自带，不引依赖）。
 * 磁盘上正文之外套了一层外皮（[FileObfuscation]），[loadState] / [save] 负责剥与包。
 */
object CommandLibrary {
    /** 当前格式版本，解析时不做版本兼容，只写进文件备查。 */
    const val VERSION = 1

    /**
     * 读命令库的结果 —— 与 [ConfigStore.Load] **同一条口径**：「读不懂」必须与「空」分两态。
     *
     *  为什么这条纪律在命令库上更要紧：界面把空表当真相之后，用户**随便改一条命令**就会
     *  把这份空表写回盘（[save]），那份读不懂的文件被彻底覆盖 —— 用户的命令就这么没了。
     *  主配置那边靠这三态 + `configReadOnly` 挡住同一件事，命令库原来没有，只有个空表兜底。
     */
    sealed interface Load {
        /** 读到了（可能是空表 —— 用户确实一条都没建）。 */
        data class Ok(
            val commands: List<Command>,
        ) : Load

        /** 文件不存在（首装 / 刚清过数据）—— 空表就是真相，可以写回。 */
        data object Absent : Load

        /** 文件在、但读不懂（外皮解不开 / 被截断 / 解析失败）—— **不许**把它当空表写回。 */
        data object Unreadable : Load
    }

    // 这一张表是**命令库自己那份文件**的格式（`opluskey_commands.conf`），与主配置的
    //  `core/model/ConfigFields` 各管一份文件。三个 `"version"` 与两个 `"commands"` 是
    //  **同字面量不同义**（命令库版本 / 主配置版本 / 导出信封版本各一个），所以常量名带上前缀
    //  区分 —— 别把它们并成一个：它们的版本号各涨各的。
    private const val LIB_VERSION = "version"
    private const val KEY_LIBRARY_COMMANDS = "commands"
    private const val FIELD_NAME = "name"
    private const val FIELD_KIND = "kind"
    private const val FIELD_STEPS = "steps"
    private const val FIELD_DELAY = "stepDelayMs"

    /** 单条命令的正文。与主配置里的 `"single"`（手势相位）同字面量但无关。 */
    private const val FIELD_SINGLE = "single"

    /** 清洗命令名：去掉换行（换行会撑开列表行），掐掉首尾空白。存库 / 比对用。 */
    fun sanitizeName(raw: String): String = sanitizeNameInput(raw).trim()

    /**
     * 输入侧清洗：只把换行换成空格，**不 trim** —— 名称框边输边调它，
     * 掐掉首尾空白的话空格就打不进去（打一个被吃一个）。
     */
    fun sanitizeNameInput(raw: String): String =
        raw
            .map { if (it == '\n' || it == '\r') ' ' else it }
            .joinToString("")

    /** 解析命令库文本，读不懂的条目跳过，绝不抛异常。 */
    fun parse(text: String): List<Command> =
        try {
            val arr = JSONObject(text).optJSONArray(KEY_LIBRARY_COMMANDS)
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
     * [parse] 的「分得清读不懂」版：**结构认不出**就返回 `null`，认得出就返回列表
     * —— **空表是合法结果**（命令库本来就是空的，那是用户的真实状态）。
     *
     * 与 [parse] 的差别有两处，都是给「导入配置」那一路用的（它必须**整份**过关才落盘）：
     *  · 没有 `commands` 数组 ⇒ `null`（[parse] 会当成空表）；
     *  · **有一条命令认不出 ⇒ `null`**（[parse] 是跳过那一条）。
     * 把「读不懂」当成「空表」再拿去覆盖用户的命令库，等于把用户的命令全删了。
     *
     * 注意：不要拿它替换 [parse]：文件加载那一路要的是「坏一条只丢一条」，
     * 一条手改坏的条目不该让整个命令库消失。
     */
    fun parseOrNull(text: String): List<Command>? =
        try {
            val arr = JSONObject(text).optJSONArray(KEY_LIBRARY_COMMANDS) ?: return null
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: return null
                if (commandOf(o) == null) return null
            }
            parse(text)
        } catch (e: Throwable) {
            XLog.w("命令库解析失败（判为读不懂）：${e.message}")
            null
        }

    /**
     * 取一个字符串字段。不能直接用 `optString(key, fallback)`：键不存在才走 fallback，
     * 显式写成 `"single": null` 的 JSON 会返回字符串 "null"（`JSONObject.NULL.toString()`），
     * `Action.parse("null")` 兜底成 `Shell("null")`，按那个手势就真去跑 `sh -c null` 而非「无操作」。
     * 这些 KDoc 恰恰邀请手改文件（缩进两格），所以显式挡掉 isNull。
     */
    private fun textOf(
        o: JSONObject,
        key: String,
        fallback: String = "",
    ): String = if (o.isNull(key)) fallback else o.optString(key, fallback)

    /** 一条命令；名字为空 / 对象坏了都返回 null（跳过它）。 */
    private fun commandOf(o: JSONObject): Command? {
        val name = sanitizeName(textOf(o, FIELD_NAME))
        if (name.isEmpty()) return null

        // 认不出的类型（版本比我们新）——**跳过这一条**，不要降级成单条：
        //  串行命令的 `steps` / `stepDelayMs` 与 `single` 是两个字段，降级后只读 `single`
        //  ⇒ 那些步骤当场被掏空，而用户下一次保存会把这条空单条**固化**进文件（静默丢数据）。
        //  与「名字为空」同一档：坏一条只丢一条，日志里查得到（`parse` 的 KDoc 就是这个口径）。
        val kindId = textOf(o, FIELD_KIND)
        val kind = CommandKind.fromId(kindId)
        if (kind == null) {
            XLog.w("命令库：有一条的类型认不出（已跳过）：$name（kind=$kindId）")
            return null
        }
        if (kind != CommandKind.SERIAL) {
            return Command(name = name, kind = CommandKind.SINGLE, single = textOf(o, FIELD_SINGLE))
        }

        val steps = o.optJSONArray(FIELD_STEPS)
        val stepList =
            if (steps == null) {
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
            stepDelayMs =
                o
                    .optInt(FIELD_DELAY, Command.DEFAULT_STEP_DELAY_MS)
                    .coerceIn(Command.MIN_STEP_DELAY_MS, Command.MAX_STEP_DELAY_MS),
        )
    }

    /** 序列化成命令库文本，缩进两格。 */
    fun serialize(commands: List<Command>): String {
        val arr = JSONArray()
        commands.forEach { c ->
            val o =
                JSONObject()
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
            .put(LIB_VERSION, VERSION)
            .put(KEY_LIBRARY_COMMANDS, arr)
            .toString(2) + "\n"
    }

    /**
     * 读命令库（三态）。文件不存在 ⇒ [Load.Absent]；外皮解不开 / 被截断 / 解析抛异常 ⇒
     * [Load.Unreadable]（**不是**空表）；正常 ⇒ [Load.Ok]。全程不抛。
     */
    fun loadState(): Load =
        try {
            val f = ConfigStore.commandFile()
            // 走 ConfigStore.readText：剥 BOM（记事本另存为会加），否则整份命令库在 JSONObject 那一步就抛。
            if (!f.isFile) {
                Load.Absent
            } else {
                // 剥外皮（[FileObfuscation]）：首行不是魔数、或正文解不开，都算「读不懂」——
                //  同一个判据、不抛。不做旧格式兼容（见 `FileObfuscation` 的类注释）。
                val text = FileObfuscation.unwrap(FileObfuscation.MAGIC_RUNTIME, ConfigStore.readText(f))
                if (text == null) {
                    XLog.w("读命令库：外皮解不开（文件可能被截断）⇒ 按读不懂处理")
                    Load.Unreadable
                } else {
                    // 解析这一层也分「读得懂」与「读不懂」：`parse` 兜底成空表，但外皮剥开了、
                    //  正文却不是合法 JSON，那同样是「文件坏了」而不是「用户没有命令」。
                    parseState(text)
                }
            }
        } catch (e: Throwable) {
            XLog.w("读命令库失败（按读不懂处理）：${e.message}")
            Load.Unreadable
        }

    /** 读命令库；读不到 / 读不懂都返回空表（不抛）—— 只给「空表就够用」的调用方（执行侧、导出包）。 */
    fun load(): List<Command> = (loadState() as? Load.Ok)?.commands ?: emptyList()

    private fun parseState(text: String): Load =
        try {
            val json = JSONObject(text)
            val arr = json.optJSONArray(KEY_LIBRARY_COMMANDS)
            if (arr == null) {
                // `commands` 那一段都不在 ⇒ 不是我们写的文件（或写坏了）
                XLog.w("读命令库：正文里没有 $KEY_LIBRARY_COMMANDS 这一段 ⇒ 按读不懂处理")
                Load.Unreadable
            } else {
                Load.Ok(parse(text))
            }
        } catch (e: Throwable) {
            XLog.w("读命令库：正文不是合法 JSON ⇒ 按读不懂处理（${e.message}）")
            Load.Unreadable
        }

    /**
     * 带缓存的读，给执行侧用：按键每次都要展开 `cmd:` 动作，不能每次读盘。
     *
     * 缓存按**文件指纹**命中（修改时间 + 长度 + inode，见 [Cached]），与 [ConfigStore.load]
     * 同口径 —— 也**必须**同口径：这是同一个场景（写方 `:ui` 进程、读方宿主主进程）的同一份判据，
     * 两处各写一份就会演进成两版，漏掉 inode 那一版会在「等长 + 同秒改写」时把陈值当命中。
     *
     * 为什么不按「距上次读过了多久」算：写方清缓存清不到读方 —— 若只按「未满一秒」判命中，
     * 「改完命令库立刻按键」会在那一秒里读到上一版。指纹比对让「文件一被写、下一次读立刻重解析」
     * 这条语义成立。本进程自己的写盘（[save]）另外主动失效。
     */
    fun loadCached(): List<Command> {
        val f = ConfigStore.commandFile()
        val modified = f.lastModified()
        val length = f.length()
        val inode = inodeOf(f)
        cache?.let {
            if (it.modified == modified && it.length == length && it.inode == inode) return it.commands
        }
        val fresh = load()
        cache = Cached(modified, length, inode, fresh)
        return fresh
    }

    /**
     * 写命令库；失败只记日志。必须走 [ConfigStore.writeAtomically]（先 `.tmp` 再 rename）：
     * 写方是设置页 `:ui` 进程、读方是宿主主进程（`host/action/CustomCommandRunner` 按键时现读），
     * 同 uid 不同进程 —— 直接 `writeText`（先截断再写）撞上读方就会读到半截 JSON，那一下
     * 按键的所有 `cmd:` 都报「命令库里没有」；写盘中途抛异常更会把命令库永久留在坏文件上。
     */
    fun save(commands: List<Command>): Boolean {
        val ok =
            ConfigStore.writeAtomically(
                ConfigStore.commandFile(),
                // 与主配置同一层外皮（[FileObfuscation]）
                FileObfuscation.wrap(FileObfuscation.MAGIC_RUNTIME, serialize(commands)),
                openReadable = false,
            )
        cache = null
        return ok
    }

    /**
     * 执行侧的缓存：上一次读的结果与它对应的文件指纹。
     *
     *  指纹是「修改时间 + 长度 + inode」三个字段，分着比、不合成一个数（合成会有碰撞的可能）。
     *  为什么必须带上 inode：与 [ConfigStore.Cached] **同一个场景** —— 写方是设置页 `:ui` 进程、
     *  读方是宿主主进程，**写方清缓存清不到读方**，读方只能靠指纹自己发现「文件换了」。
     *  若新内容**长度恰好相同**、又落在同一个 mtime 粒度内，只看（修改时间, 长度）就会把陈值
     *  当命中，于是宿主永久读旧表 —— 而日志完全自洽，看不出问题。
     *  写盘走的是「先 `.tmp` 再 rename」（[ConfigStore.writeAtomically]），目标文件因此每次都是
     *  **新 inode**，inode 必变，能天然区分出每一次写。
     *
     *  取法统一走 [inodeOf]（在 [ConfigStore] 那份里）：两份指纹各自实现过一次，
     *  结果是这一份没跟上，别再分叉。
     */
    private class Cached(
        val modified: Long,
        val length: Long,
        val inode: Long,
        val commands: List<Command>,
    )

    @Volatile
    private var cache: Cached? = null

    /**
     * 广播发送侧要的那一位：[config] 的长按动作是不是「多条命令」。
     *
     *  合在这里是为了**把读盘这一步留在 `core/config`** —— [isSerialCommand] 那条规则本身是纯的
     *  （在 `core/model`），而「读命令库」只有落盘包会做；广播构造器因此不必认识本包，
     *  注入侧那条「不许碰 `core/config`」的编译期保证才成立（见 `docs/decoupling.md` 动作 3）。
     */
    fun longIsSerial(config: Config): Boolean = isSerialCommand(loadCached(), config.key(KeyId.PLUS).long.token)
}
