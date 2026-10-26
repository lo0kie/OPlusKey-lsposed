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

        /** 文件还不存在（首装 / 刚清过数据）—— 用默认值，**允许写回**。 */
        data object Absent : Load

        /**
         * 文件在，但读不了 / 读不懂 —— **不允许写回**。
         *
         * 这时候手上那份 [Config.DEFAULT] 不是用户的真相，只是「读失败」的替身；
         * 谁把它写回文件，就等于把用户配的东西整份抹成默认值。
         */
        data object Unreadable : Load
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
        val modified = f.lastModified()
        val length = f.length()
        val inode = inodeOf(f)
        cached?.let {
            //  `inode != 0` 是**必要条件**：inode 取不到（主文件与 `.bak` 都不在、stat 失败）时三个
            //  值一起是 0，而「只剩 `.bak` 被还原」那条路存下来的缓存条目**正好也是全 0** ——
            //  只比三个数的话，之后配置被删掉会命中那条，把已经不存在的内容当成本次结果。
            val sameFile = it.modified == modified && it.length == length && it.inode == inode
            if (inode != 0L && sameFile) return it.result
        }

        val raw =
            when (val r = files().read(SLOT_CONFIG)) {
                is ModuleFiles.Load.Ok -> {
                    r.text
                }

                ModuleFiles.Load.Absent -> {
                    // 真的没写过（主文件与 `.bak` 都不在，[ModuleFiles.read] 已经还原过了）。
                    //  用 d：这是首装时每条按键都会走到的正常状态，w 会刷屏。
                    XLog.d("读配置：主文件与 .bak 都不在，按首装处理（${f.path}）")
                    cached = null
                    return Load.Absent
                }

                ModuleFiles.Load.Unreadable -> {
                    //  d：宿主每条按键前都要读一遍，w 会刷屏 —— 与原来那一处同一个级别
                    XLog.d("读配置失败（文件在但读不出来）：${f.path}")
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
        val result = text?.let { ConfigCodec.parseOrNull(it) }?.let { Load.Ok(it) } ?: Load.Unreadable
        cached = Cached(modified, length, inode, result)
        return result
    }

    /**
     * 上一次读的结果与它对应的文件指纹。
     *
     * 指纹是「修改时间 + 长度 + inode」三个字段，不合成一个数：合成会有碰撞的可能，分着比是精确的。
     * 为什么还要带上 inode：写方是设置页 `:ui` 进程、读方是宿主主进程，**写方清缓存清不到读方**，
     * 读方只能靠指纹自己发现「文件换了」——若新内容**长度恰好相同**、又落在同一个 mtime 粒度内，
     * 只看（修改时间, 长度）就会把陈值当命中。写盘走的是 `AtomicFile`（同目录改名换文件），目标文件因此
     * 每次都是**新 inode**，inode 必变，能天然区分出每一次写。
     */
    private class Cached(
        val modified: Long,
        val length: Long,
        val inode: Long,
        val result: Load,
    )

    @Volatile
    private var cached: Cached? = null

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
            Load.Absent, Load.Unreadable -> Config.DEFAULT
        }

    /**
     * 写配置（只有宿主进程调得到）。
     *
     * 先包一层外皮（[FileObfuscation]），再交给 [ModuleFiles.write]（`android.util.AtomicFile`），
     * 避免执行侧读到半截内容。
     *
     * 写成功后**主动丢掉读缓存**（[load] 那一份）：本进程自己的写盘最清楚「刚换过内容」，
     * 主动失效省掉一次多余的 `stat` 比对，不依赖指纹去发现。
     * 跨进程的写方（设置页 `:ui`）清不到这里的缓存，那种情况由指纹里的 inode 兜住（见 [Cached]）。
     */
    fun write(config: Config): Boolean {
        val ok =
            files().write(
                SLOT_CONFIG,
                FileObfuscation.wrap(FileObfuscation.MAGIC_RUNTIME, ConfigCodec.serialize(config)),
                openReadable = true,
            )
        if (ok) cached = null
        return ok
    }
}

/**
 * 取文件 inode（`android.system.Os.stat`，本地 syscall，很便宜）。
 *
 *  文件已不在 / stat 失败时返回 0：那与「取不到」同值，最多让缓存多失效一次（多读一遍盘），
 *  不会把陈值当命中 —— 方向是安全的那一侧。
 *
 *  写成**顶层**函数而不是对象成员，而且是 `internal`：它是纯函数、不吃对象状态 ——
 *  而 [ConfigStore] 与 [CommandLibrary] **两处**都要按它算缓存指纹（同一场景：写方 `:ui` 进程、
 *  读方宿主主进程），各自写一份就会演进成两份不一样的实现。放 [ConfigStore] 当对象成员的话
 *  [CommandLibrary] 拿不到，而且 [ConfigStore] 的函数条数已经贴着 detekt `TooManyFunctions`
 *  那一档，再往里放一个就把闸口顶红。
 */
fun inodeOf(f: java.io.File): Long =
    try {
        android.system.Os
            .stat(f.path)
            .st_ino
    } catch (e: Throwable) {
        XLog.d("取文件 inode 失败（缓存指纹退化为修改时间 + 长度）：${f.path} | ${e.message}")
        0L
    }
