package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config

/**
 * 本模块（[MODULE_ID]）配置文件的**门面**：主配置的读、写、缓存，以及「哪三个槽是本模块的」。
 *
 * 文件怎么放、怎么写、怎么删，全在 [ModuleFiles]（多模块共用的那一层）；
 * 这一层只剩**本模块自己的事**：文件格式（[ConfigCodec] + [FileObfuscation]）与读缓存。
 */
object ConfigStore {
    /**
     * 本模块在宿主私有目录里的文件前缀（`opluskey_*.conf`）。
     *
     *  与 [com.lookie.opluskey.core.ModuleInfo.PACKAGE] 不是一个东西：那个是**包名**（带点），
     *  这个是**文件名前缀**（只许小写字母 / 数字 / 连字符，规则见 [ModuleFiles.of]）。
     *  两者同源但不同用途，改包名不必跟着改它 —— 除非真要换一套文件名（那要连迁移一起做）。
     */
    const val MODULE_ID: String = "opluskey"

    /** 主配置的槽名（`opluskey_config.conf`）。 */
    const val SLOT_CONFIG: String = "config"

    /**
     * 自定义命令库（[CommandLibrary]）的槽名，与主配置分开存：
     * 主配置只记 `cmd:<名字>` 引用，命令内容在这一份里。
     */
    const val SLOT_COMMANDS: String = "commands"

    /**
     * `commands` 槽的跨 uid 可读口径 —— [CommandLibrary.save] 与 [ProfileCommands] 写的是**同一份
     * 文件**，两处各写一个字面量的话，debug 包里谁最后写就决定它可不可读（权限来回翻）。
     * 与主配置（[write] 里的 `openReadable = true`）保持一致。
     */
    const val OPEN_READABLE_COMMANDS: Boolean = true

    /** 界面状态（`ui/state/UiState`）的槽名，**不是用户配置**（只有「显示系统应用」这类界面偏好）。 */
    const val SLOT_UI: String = "ui"

    /**
     * 自定义变量的槽名（`opluskey_vars.conf`，见 [VariableStore]）。
     *
     *  与主配置 / 命令库分开一份：变量的值会被命令频繁改写，混进主配置等于每按一次就整份重写 +
     *  发一次配置广播。
     */
    const val SLOT_VARS: String = "vars"

    /** 本模块的文件句柄 —— 多模块那一层留出的唯一入口。 */
    fun files(): ModuleFiles = ModuleFiles.of(MODULE_ID)

    /** 本模块的配置文件目录（宿主私有目录里那一层，所有模块共用）。 */
    fun filesDir() = hostFilesDir()

    /** 主配置文件（宿主私有目录里的那个）。 */
    fun sharedFile() = files().file(SLOT_CONFIG)

    /** 自定义命令库文件。 */
    fun commandFile() = files().file(SLOT_COMMANDS)

    /** 界面状态文件。 */
    fun uiFile() = files().file(SLOT_UI)

    /** 主配置的读取结果 —— 「还没写过」与「读不懂」必须分开，理由见 [load]。 */
    sealed interface Load {
        /** 正常读到一份配置。 */
        data class Ok(
            val config: Config,
        ) : Load

        /** 文件还不存在（首装 / 刚清过数据 —— 连 `.prev` 都没有）—— 用默认值，**允许写回**。 */
        data object Absent : Load

        /**
         * 文件在，但读不了 / 读不懂 —— **不允许写回**。
         *
         * 这时候手上那份 [Config.DEFAULT] 不是用户的真相，只是「读失败」的替身；
         * 谁把它写回文件，就等于把用户配的东西整份抹成默认值。
         */
        data object Unreadable : Load

        /**
         * 主文件读不懂，但**上一次成功写入**留下的 `.prev` 读得懂 —— 用的就是备份那份。
         *
         *  与 [Ok] 一样**可以写回**（手上这份是用户的东西，不是默认值），只是比用户最新的那次
         *  改动旧一个版本：坏掉那一版里的改动没了。所以页面要提一句 —— 否则用户只会发现
         *  「我明明配过的那一项不见了」，而没有任何线索。
         */
        data class Recovered(
            val config: Config,
        ) : Load
    }

    /**
     * 读主配置，把**三态**分开报出来（[Load]）。
     *
     * 为什么必须分三态：只有「读到一份 / 退化成默认值」两条路的话，「退化成默认值」这一条
     * 在写方那边会被当成真相继续用 —— 页面读失败 ⇒ 拿默认值渲染 ⇒ 用户随便改一项 ⇒
     * 整份默认值落盘，**主开关、三个手势绑定、振动开关一起被抹掉**。
     * 真机上先丢的是「振动反馈」（只剩这一个开关被人一眼看出来），
     * 再往后一次就是把绑定一起抹掉。
     *
     *  只有宿主进程调得到。注入侧读这份文件必然 EACCES（也正是它不读文件、只吃配置广播的原因），
     * 那边若真调到会拿到 [Load.Unreadable]，按不接管处理 —— 行为与原生一致。
     *
     * **进程内结果缓存**：这个方法在宿主进程的**按键路径**上被调好几次（手势执行一次、
     * 顺带那次报活再一次），每次都是「读盘 + 剥两层外皮 + JSON 解析 + 建映射」。
     * 缓存按**文件指纹**（修改时间 + 长度 + inode，见 [Cached]）命中，所以「改配置即时生效」这条
     * 语义不变：文件一被写，指纹就变，下一次读立刻重解析。本进程自己的写盘（[write]）另外主动失效。
     *
     * 缓存的是**内容派生出来的结论**（[Load.Ok] 与「内容读不懂」的 [Load.Unreadable]）——
     * 内容没变，结论就不会变。**读不出来那一路不缓存**（EACCES / IO 错）：它不是内容的性质，
     * 缓存住会让一次偶发失败一直粘着，而写方又被 [Load.Unreadable] 闸着不让写回。
     */
    fun load(): Load {
        val f = sharedFile()
        // 注意：**不能**先判 `f.isFile`。只剩 `.bak` 时主文件不在，但配置还在 —— 那要交给
        //  [ModuleFiles.read] 走 `AtomicFile.openRead()` 还原，判成「首装」就会被默认值覆盖。
        //
        //  指纹在**读盘之前**取：`read` 可能顺手把 `.bak` 还原成主文件，取在后面就不是
        //  「这次读的是哪个版本」的指纹了。还原那一路取到的是全 0（当时主文件还不在），
        //  与「文件不在」同值 —— [FileStamp.sameAs] 要求 inode 非 0，所以它永远不会被命中。
        val stamp = fileStamp(f)
        cached?.let { if (it.stamp.sameAs(stamp)) return it.result }

        val raw =
            when (val r = files().read(SLOT_CONFIG)) {
                is ModuleFiles.Load.Ok -> {
                    r.text
                }

                ModuleFiles.Load.Absent -> {
                    //  **只剩 `.prev` 时不能判「首装」**：`[writeProfileConfig]` 每次写盘前留的那份快照
                    //  正是给这种情况准备的，而 `slots()` 也把 `.prev` 算作一个槽（列表里看得见它）。
                    //  两头结论相反的话，列表里那一份会在下一次保存时被默认值整份盖掉 ——
                    //  那是「配置莫名回到出厂」最安静的一条路径。
                    recoverFromBackup()?.let {
                        XLog.w("读配置：主文件与 .bak 都不在，用上次写入留下的 .prev 顶上")
                        return it
                    }
                    // 真的没写过（主文件、`.bak`、`.prev` 都不在）。
                    //  用 d：这是首装时每条按键都会走到的正常状态，w 会刷屏。
                    XLog.d("读配置：主文件、.bak 与 .prev 都不在，按首装处理（${f.path}）")
                    cached = null
                    return Load.Absent
                }

                ModuleFiles.Load.Unreadable -> {
                    //  **判据级但要防刷屏**：宿主每条按键前都读一遍配置，文件坏一次就是每秒十几行。
                    //  所以第一次用 `w`（排查时要看得到「读不出来」），之后降到 `d`；
                    //  具体哪条路径坏的那一行由 `ModuleFiles` 打（那里有路径与原因）。
                    if (!unreadableWarned.add(f.path)) {
                        XLog.d("读配置失败（文件在但读不出来）：${f.path}")
                    } else {
                        XLog.w("读配置失败（文件在但读不出来）：${f.path}（同一文件后续只记调试档）")
                    }
                    return Load.Unreadable
                }
            }

        // 剥外皮（[FileObfuscation]）：首行不是魔数、或正文认不出，都算「读不懂」。
        //  **不做旧格式兼容**：加第二层之前写下的文件、以及开发期的明文文件，判 [Load.Unreadable]
        //  （页面只读、不接管），不退回「当它没写过」—— 那会把用户配置整份换成默认值。
        //  正文自带 md5（[ConfigCodec]），值对不上同样走 Unreadable。
        //
        //  **读不懂也按指纹缓存**：这是由文件内容推出来的结论，指纹不变结论就不会变；不缓存的话
        //  坏文件会被每一条按键重读一遍，`FileObfuscation` 那两行 W 也跟着刷屏（判据行会被冲掉）。
        //  与它相对的，IO 失败不缓存 —— 那是**瞬时**故障（EACCES / IO 抖动），下一按可能就好了。
        val text = FileObfuscation.unwrap(FileObfuscation.MAGIC_RUNTIME, raw)
        val result =
            text?.let { ConfigCodec.parseOrNull(it) }?.let { Load.Ok(it) }
                ?: recoverFromBackup()
                ?: Load.Unreadable
        cached = Cached(stamp, result)
        return result
    }

    /**
     * 主文件解析失败之后**再试一次 `.prev`**（上一次成功写入前自己留的那份快照）。
     *
     *  为什么值得试：坏文件旁边常常躺着一份好的。不做这一步时，用户的选择只有「清空配置」
     *  ——把绑定、开关、变量一起丢掉——而上一版就在同一个目录里。
     *
     *  为什么是 `.prev` 而不是 `AtomicFile` 的 `.bak`：现代 `AtomicFile.finishWrite` 写成功时
     *  直接删掉旧 base，根本不产生 `.bak`（装到机器上 `ls` 过，一份都没有）；而且 `.bak` 这个名字
     *  被 `openRead()` 占用着语义位 —— 它会认为「上次没写完」，删主文件把它顶上去。
     *  快照由 [snapshotModuleFile] 在 [write] 之前自己复制，见那里的注释。
     *
     *  为什么不直接覆盖主文件：读的这一层**只读**（宿主按键路径每秒会进来好几次，写在这里
     *  就是不可预期的 IO 与权限问题）。等用户下一次改配置，`write` 自然把这份写回去，
     *  主文件那时就修好了。
     *
     *  `.prev` 也读不懂时返回 `null` —— 调用方照旧 [Load.Unreadable]，页面只读、绝不写默认值。
     */
    private fun recoverFromBackup(): Load? {
        val raw =
            readModuleFileBackup(files().file(SLOT_CONFIG))
                ?: return null
        val config =
            FileObfuscation
                .unwrap(FileObfuscation.MAGIC_RUNTIME, raw)
                ?.let { ConfigCodec.parseOrNull(it) }
                ?: return null
        //  这条必须是 w：它解释了「为什么用户的某一项改动不见了」
        XLog.w("读配置：主文件读不懂（或已不在），已从 .prev 快照恢复（比最后一次成功写入旧一个版本）")
        return Load.Recovered(config)
    }

    /**
     * 上一次读的结果与它对应的文件指纹。
     *
     * 指纹本身是 [FileStamp]（修改时间 + 长度 + inode 三个字段，分着比是精确的，合成一个数会
     * 有碰撞）。为什么必须带上 inode：写方是设置页 `:ui` 进程、读方是宿主主进程，
     * **写方清缓存清不到读方**，读方只能靠指纹自己发现「文件换了」——若新内容**长度恰好相同**、
     * 又落在同一个 mtime 粒度内，只看（修改时间, 长度）就会把陈值当命中。写盘走的是
     * `AtomicFile`（同目录改名换文件），目标文件因此每次都是**新 inode**，inode 必变，
     * 能天然区分出每一次写。
     */
    private class Cached(
        val stamp: FileStamp,
        val result: Load,
    )

    @Volatile
    private var cached: Cached? = null

    /**
     * 已经用 `w` 报过的**文件路径**（每进程一份）。
     *
     *  以前是**一个布尔**：进程里第一个坏文件用 `w`，之后所有坏文件（哪怕完全不相干的另一份）
     *  都降成 `d` —— 第二个坏掉的文件在 release 日志里等于不存在，而它恰好可能就是用户
     *  抱怨的那一份。按路径记，每个文件第一条都是 `w`（这条判据会被**每条按键**打到，
     *  所以仍要防刷屏）。
     */
    private val unreadableWarned =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<String>()

    /**
     * 读配置；**读不到 / 读不懂都退化成 [Config.DEFAULT]**（不抛）。
     *
     * 注意：这是**只读方**的入口（宿主侧每条按键、每次执行、每次报活都读一遍）。语义是
     * 「读不到就按不接管处理，行为与原生一致」，对只读方是对的。
     * **写方不许用这个** —— 写之前必须拿 [load] 的三态，见 [Load]。
     */
    fun read(): Config =
        when (val l = load()) {
            is Load.Ok -> l.config

            //  从 `.prev` 恢复来的那份是用户的东西 —— 只读方照用，与 Ok 同义
            is Load.Recovered -> l.config

            Load.Absent, Load.Unreadable -> Config.DEFAULT
        }

    /**
     * 写配置（只有宿主进程调得到）。
     *
     * 先包一层外皮（[FileObfuscation]），再交给 [ModuleFiles.write]（`android.util.AtomicFile`），
     * 避免执行侧读到半截内容。
     *
     * 写成功后**按新文件的指纹把读缓存直接填成刚写下的那一份**（与 [CommandLibrary.save] 同一口径）：
     * 刚写下去的这份就是盘上的全部内容，丢掉缓存的话，下一次写盘开头那句 [load]（要判断能不能留
     * `.prev`）会在**调用线程**——设置页是主线程——把整份文件重读、剥两层外皮、再解析一遍。
     * 跨进程的写方（宿主主进程）改的是别的缓存，那种情况由指纹里的 inode 兜住（见 [Cached]）。
     */
    fun write(config: Config): Boolean {
        //  **写之前先把现有那份留成 `.prev`**：它是「上一次成功写入」的完整一份，下一次主文件
        //  读不懂时 [recoverFromBackup] 就靠它。不能指望 `AtomicFile` —— 它写成功时直接删旧
        //  base，`.bak` 只在 legacy 命名下才有（真机上一次都没出现过，见 `snapshotModuleFile`）。
        //
        //  **只有主文件现在读得懂（[Load.Ok]）时才复制**：`Absent` 没有可复制的东西；
        //  `Recovered` 与 `Unreadable` 都意味着主文件本身是坏的 —— 那时 `.prev` 往往正是唯一
        //  还能救的副本，把它换成一份坏内容等于当场把退路毁掉（这一次写盘之后再失败，就再也
        //  回不去了）。判据因此只能是 `Ok`，不能是「不是 Unreadable」。
        if (load() is Load.Ok) snapshotModuleFile(files().file(SLOT_CONFIG))
        val ok =
            files().write(
                SLOT_CONFIG,
                FileObfuscation.wrap(FileObfuscation.MAGIC_RUNTIME, ConfigCodec.serialize(config)),
                openReadable = true,
            )
        if (ok) {
            val stamp = fileStamp(sharedFile())
            //  指纹取不到（inode 0）时不记：那与「读不到」同值，下一次读照旧走盘，方向是安全的那一侧。
            cached = if (stamp.inode != 0L) Cached(stamp, Load.Ok(config)) else null
        }
        return ok
    }
}

/**
 * 一份文件的读缓存指纹 —— 修改时间 / 长度 / inode，**一次 `stat` 取齐**。
 *
 *  这三项原先在三处各写一遍（主配置 [ConfigStore]、命令库 `CommandLibrary`、变量表
 *  `VariableStore`），每处都是 `File.lastModified()` + `File.length()` + 一次 `Os.stat`：
 *  那是**三次**系统调用。而 `ConfigStore.read()` 在宿主进程的按键路径上每条按键要被问好几次
 *  （执行、定键身份、报活各一次），三次变一次是这条路径上唯一还剩下的固定开销。
 *
 *  `Os.stat` 的返回结构里本来就有这三项，所以收成这一处、一次取齐。三处共用一份实现也顺带
 *  保证「什么算命中」只有一条判据 —— 原先三份各自演进过一次，其中一份没跟上 inode。
 *
 *  ## 精度
 *
 *  精度没有变：Android 上 `File.lastModified()` 本身就是把 `st_mtime`（秒）乘 1000 得来的，
 *  原先那三项里本来就没有毫秒。判据也不靠时间粒度 —— 每次写盘都走 `AtomicFile`
 *  （同目录改名换文件），文件因此每次都是**新 inode**，inode 变了就不算命中；
 *  时间与长度只是同一道判据上的第二、三项。stat 失败时（文件不在 / 权限）三项全 0，
 *  [sameAs] 要求 inode 非 0，于是永远不会被当成命中 —— 方向是「多读一遍盘」。
 *
 *  写成**顶层**类 + 函数而不是对象成员：纯函数、不吃对象状态，而 [ConfigStore] 的函数条数
 *  已经贴着 detekt `TooManyFunctions` 那一档。
 */
internal class FileStamp(
    val modified: Long,
    val length: Long,
    val inode: Long,
) {
    /**
     * 与 [other] 是同一个文件的同一个版本吗。
     *
     *  `inode != 0L` 是**必要条件**：文件不在（主文件与 `.bak` 都没了）时三项一起是 0，而
     *  「只剩 `.bak` 被还原」那条路存下来的指纹正好也是全 0 —— 只比三个数会把「已经不存在」
     *  判成命中，把已经删掉的内容当成本次结果。
     */
    fun sameAs(other: FileStamp): Boolean = inode != 0L && inode == other.inode && modified == other.modified && length == other.length
}

/** 取一份文件的指纹（一次 stat）；失败给全 0 —— 见 [FileStamp.sameAs]。 */
internal fun fileStamp(f: java.io.File): FileStamp =
    try {
        val st = android.system.Os.stat(f.path)
        FileStamp(st.st_mtime, st.st_size, st.st_ino)
    } catch (e: Throwable) {
        //  d：取不到是「文件还没写 / 已删」这类正常状态，按键路径上会反复走到
        XLog.d { "取文件指纹失败（本次按未命中处理）：${f.path} | ${e.javaClass.simpleName}" }
        FileStamp(0L, 0L, 0L)
    }
