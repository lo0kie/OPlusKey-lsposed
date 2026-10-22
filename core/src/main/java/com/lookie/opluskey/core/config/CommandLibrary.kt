package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.isSerialCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * 自定义命令库的读写：JSON 编解码（kotlinx.serialization）加文件 IO。
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
    private const val KEY_LIBRARY_COMMANDS = "commands"

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

    /**
     * 唯一的编解码器 —— 写与读必须用同一份配置，否则「自己写的文件自己读不出来」。
     *
     * `encodeDefaults = true`：`commands` 那一段即使是空表也要写出来 —— [parseOrNull] 把
     * 「没有 commands 这一段」判成读不懂，省略它就等于让「空命令库」变成「文件坏了」。
     */
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    /**
     * 取 `commands` 那一段的原始元素；不是合法 JSON / 没有这一段 ⇒ `null`。
     *
     * 先拿元素、再逐条解码，而不是整份 `decodeFromString`：命令库的纪律是**坏一条只丢一条**
     * （见 [parse] 的 KDoc），整份解码会让一条手改坏的条目带走整个命令库。
     */
    private fun commandElements(text: String): JsonArray? =
        runCatching { json.parseToJsonElement(text).jsonObject[KEY_LIBRARY_COMMANDS]?.jsonArray }.getOrNull()

    /** 解一条命令；类型不对 / 认不出 → `null`（调用方决定是跳过还是整份判坏）。 */
    private fun decodeCommand(e: JsonElement): Command? = runCatching { toCommand(json.decodeFromJsonElement<CommandDto>(e)) }.getOrNull()

    /**
     * 线格式 → 领域模型；名字为空 / 类型认不出都返回 `null`（跳过它）。
     *
     * 认不出的类型（版本比我们新）**跳过这一条**，不要降级成单条：串行命令的 `steps` /
     * `stepDelayMs` 与 `single` 是两个字段，降级后只读 `single` ⇒ 那些步骤当场被掏空，
     * 而用户下一次保存会把这条空单条**固化**进文件（静默丢数据）。
     */
    private fun toCommand(dto: CommandDto): Command? {
        val name = sanitizeName(dto.name)
        if (name.isEmpty()) return null
        val kind = CommandKind.fromId(dto.kind)
        if (kind == null) {
            XLog.w("命令库：有一条的类型认不出（已跳过）：$name（kind=${dto.kind}）")
            return null
        }
        //  **必须一个类型一个分支**：这里是「不是 SERIAL 就当 SINGLE」的话，以后再加一档就会
        //  被静默读成单条，而用户下一次保存就把它固化进文件。
        return when (kind) {
            CommandKind.SINGLE -> {
                Command(name = name, kind = CommandKind.SINGLE, single = dto.single)
            }

            CommandKind.SERIAL -> {
                Command(
                    name = name,
                    kind = CommandKind.SERIAL,
                    steps = dto.steps.map { it?.trim().orEmpty() }.filter { it.isNotEmpty() },
                    stepDelayMs = dto.stepDelayMs.coerceIn(Command.MIN_STEP_DELAY_MS, Command.MAX_STEP_DELAY_MS),
                )
            }
        }
    }

    /** 解析命令库文本，读不懂的条目跳过，绝不抛异常。 */
    fun parse(text: String): List<Command> =
        try {
            val arr = commandElements(text) ?: return emptyList()
            // 名字是主键：同一名字出现多次时**后写的覆盖先写的**
            //  —— 必须显式这么写：`distinctBy { it.name }` 留的是**第一个**，与上面这句话相反。
            val byName = LinkedHashMap<String, Command>()
            for (e in arr) {
                val cmd = decodeCommand(e) ?: continue
                byName[cmd.name] = cmd
            }
            byName.values.toList()
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
    fun parseOrNull(text: String): List<Command>? {
        val arr = commandElements(text) ?: return null
        //  「类型认不出」是**已知形态**（老文件里的 `branch`、或比我们新的版本写进来的类型），
        //  跳过那一条、其余照常导入；真坏（名字空 / 结构不对）才整份判坏 ——
        //  否则用户拿着旧备份升级后就再也导不进来（报「未知的配置格式」）。
        val broken = arr.any { e -> decodeCommand(e) == null && !isUnknownKind(e) }
        return if (broken) null else parse(text)
    }

    /** 这一条是不是「名字读得出来、只是类型认不出」—— 与「真坏」分开，见 [parseOrNull]。 */
    private fun isUnknownKind(e: JsonElement): Boolean =
        runCatching {
            val dto = json.decodeFromJsonElement<CommandDto>(e)
            sanitizeName(dto.name).isNotEmpty() && CommandKind.fromId(dto.kind) == null
        }.getOrDefault(false)

    /** 序列化成命令库文本，缩进两格、末尾一个换行（与老格式一致）。 */
    fun serialize(commands: List<Command>): String =
        json.encodeToString(CommandLibraryDto(VERSION, commands.map { CommandDto.of(it) })) + "\n"

    /**
     * 读命令库（三态）。文件不存在 ⇒ [Load.Absent]；外皮解不开 / 被截断 / 解析抛异常 ⇒
     * [Load.Unreadable]（**不是**空表）；正常 ⇒ [Load.Ok]。全程不抛。
     */
    fun loadState(): Load =
        try {
            val f = ConfigStore.commandFile()
            // 走 ConfigStore.readText：剥 BOM（记事本另存为会加），否则整份命令库在 JSONObject 那一步就抛；
            //  它同时负责「只剩 `.bak` 时先还原」——所以**不能**先判 `f.isFile`（那会把还原机会跳过去）。
            val raw =
                try {
                    ConfigStore.readText(f)
                } catch (e: java.io.FileNotFoundException) {
                    XLog.d("读命令库：主文件与 .bak 都不在，按没有命令处理 | ${e.message}")
                    return Load.Absent
                }
            // 剥外皮（[FileObfuscation]）：首行不是魔数、或正文解不开，都算「读不懂」——
            //  同一个判据、不抛。不做旧格式兼容（见 `FileObfuscation` 的类注释）。
            val text = FileObfuscation.unwrap(FileObfuscation.MAGIC_RUNTIME, raw)
            if (text == null) {
                XLog.w("读命令库：外皮解不开（文件可能被截断）⇒ 按读不懂处理")
                Load.Unreadable
            } else {
                // 解析这一层也分「读得懂」与「读不懂」：`parse` 兜底成空表，但外皮剥开了、
                //  正文却不是合法 JSON，那同样是「文件坏了」而不是「用户没有命令」。
                parseState(text)
            }
        } catch (e: Throwable) {
            XLog.w("读命令库失败（按读不懂处理）：${e.message}")
            Load.Unreadable
        }

    private fun parseState(text: String): Load {
        val elements =
            commandElements(text) ?: run {
                // 外皮剥开了、正文却不是合法 JSON，或正文里没有那一段 ⇒ 文件坏了，不是「用户没有命令」
                XLog.w("读命令库：正文不是合法 JSON 或没有 $KEY_LIBRARY_COMMANDS 这一段 ⇒ 按读不懂处理")
                return Load.Unreadable
            }
        val commands = parse(text)
        // 「有元素、但一条都认不出」不是空表：那是整份格式变了解析不了。当 Ok(empty) 交出去，
        //  界面下一次保存就会把这张空表写回、把用户的命令全删掉。
        if (elements.isNotEmpty() && commands.isEmpty()) {
            XLog.w("读命令库：${elements.size} 条一条都认不出 ⇒ 按读不懂处理（不拿空表覆盖）")
            return Load.Unreadable
        }
        reportDanglingRefs(commands)
        return Load.Ok(commands)
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
            //  `inode != 0` 是必要条件，与 [ConfigStore.load] 同一口径：inode 取不到时三个值
            //  一起是 0，而「只剩 `.bak` 被还原」那条路存下来的条目也正好全 0 —— 只比三个数的话，
            //  之后文件被删掉会命中那条，把已经不存在的内容当成本次结果。
            val sameFile = it.modified == modified && it.length == length && it.inode == inode
            if (inode != 0L && sameFile) return it.commands
        }
        //  **只缓存 Load.Ok**：读不懂（半截文件 / IO 抖动）塌成的空表一旦按指纹记下来，
        //  指纹不变就一直命中 —— 一次抖动被记成「命令库为空」，要等到下次写盘才恢复。
        val state = loadState()
        return if (state is Load.Ok) {
            cache = Cached(modified, length, inode, state.commands)
            state.commands
        } else {
            cache = null
            emptyList()
        }
    }

    /**
     * 写命令库；失败只记日志。必须走 [ConfigStore.writeAtomically]（`AtomicFile` 原子写，见那边的说明）：
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
     * 报一次**悬空引用** —— 某条命令引用了库里根本不存在的 `cmd:<名字>`。
     *
     *  这种条目执行时必然是 `NOT_FOUND`（按下去只弹一句「命令不存在」），可它在界面上好好列着。
     *  来路是手改文件，或删命令时别处被漏清（正常删除路径会顺手摘掉引用，见
     *  `ui/screen/command/CommandLibrary.removeCommand`）。
     *
     *  **只报不改**：替用户清掉那一步、或者改成「无操作」，都是在动他的配置；而这一行日志能让
     *  「按下去没反应」当场有答案。判据与改名 / 删引用同源（[Command.referencedNames]）。
     */
    private fun reportDanglingRefs(commands: List<Command>) {
        if (commands.isEmpty()) return
        val names = commands.mapTo(HashSet(commands.size)) { it.name }
        val missing = LinkedHashSet<String>()
        for (c in commands) {
            for (ref in c.referencedNames()) if (ref !in names) missing += ref
        }
        if (missing.isNotEmpty()) {
            XLog.w(
                "命令库有悬空引用（cmd:<名字> 指向不存在的命令）：${missing.joinToString()} —— " +
                    "执行到那一步会是 NOT_FOUND，界面上仍会列着",
            )
        }
    }

    /**
     * 执行侧的缓存：上一次读的结果与它对应的文件指纹。
     *
     *  指纹是「修改时间 + 长度 + inode」三个字段，分着比、不合成一个数（合成会有碰撞的可能）。
     *  为什么必须带上 inode：与 [ConfigStore.Cached] **同一个场景** —— 写方是设置页 `:ui` 进程、
     *  读方是宿主主进程，**写方清缓存清不到读方**，读方只能靠指纹自己发现「文件换了」。
     *  若新内容**长度恰好相同**、又落在同一个 mtime 粒度内，只看（修改时间, 长度）就会把陈值
     *  当命中，于是宿主永久读旧表 —— 而日志完全自洽，看不出问题。
     *  写盘走的是 `AtomicFile`（同目录改名换文件）（[ConfigStore.writeAtomically]），目标文件因此每次都是
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
    fun longIsSerial(
        config: Config,
        key: KeyId,
    ): Boolean = isSerialCommand(loadCached(), config.key(key).long.token)
}
