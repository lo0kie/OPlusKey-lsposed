package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.HostApp
import java.io.File

/** 配置文件的位置与读写，全项目只有这一处。 */
object ConfigStore {
    /**
     * 宿主 App（[HostApp.PACKAGE]）私有目录 —— 用的是 **DE（设备加密）** 那一份，路径按**当前用户**拼。
     *
     * 为什么不用 CE（`/data/data/…`）：CE 在**首次解锁前不可见**，开机到解锁之间配置只能读到
     * 默认值（`enabled=false`），注入侧据此按「不接管」处理、侧键那一段只能走系统原生。
     * DE 与设备加密同源、开机即可读，解锁前也能按真配置接管。
     *
     * 为什么用户 id 不能写死成 0：`/data/user_de/<uid>/…` 里的 `<uid>` 是**当前用户**的用户 id
     * （`android.os.Process.myUid() / 100000`，与 `UserHandle.getUserId()` 同口径）。副用户 / 工作资料
     * 下用户 id 不是 0，写死 0 会一直去读主用户那份 —— 那些用户改配置怎么都不生效（读到的是别的
     * 用户的配置，或干脆读不到而退回默认值）。
     */
    private val HOST_DIR: String = "/data/user_de/${android.os.Process.myUid() / 100000}/${HostApp.PACKAGE}"

    private val HOST_FILES: String = "$HOST_DIR/files"

    private const val SHARED_NAME = "opluskey_config.conf"

    /**
     * 自定义命令库（[CommandLibrary] 读写）的文件名，与主配置分开存：
     * 主配置只记 `cmd:<名字>` 引用，命令内容在这一份里。
     */
    private const val COMMAND_NAME = "opluskey_commands.conf"

    /** 界面状态文件名，不是用户配置（只有「显示系统应用」这类界面偏好）。 */
    private const val UI_NAME = "opluskey_ui.conf"

    /** 本模块在宿主私有目录里用的文件夹（配置、命令库、界面状态、调试旁路标记都放这儿）。 */
    fun filesDir(): File = File(HOST_FILES)

    /** 主配置文件（宿主私有目录里的那个）。 */
    fun sharedFile(): File = File(filesDir(), SHARED_NAME)

    /** 自定义命令库文件。 */
    fun commandFile(): File = File(filesDir(), COMMAND_NAME)

    /** 界面状态文件。 */
    fun uiFile(): File = File(filesDir(), UI_NAME)

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
        //  [readText] 走 `AtomicFile.openRead()` 还原，判成「首装」就会被默认值覆盖。
        val modified = f.lastModified()
        val length = f.length()
        val inode = inodeOf(f)
        cached?.let {
            if (it.modified == modified && it.length == length && it.inode == inode) return it.result
        }

        val raw =
            try {
                readText(f)
            } catch (e: java.io.FileNotFoundException) {
                // 主文件与 `.bak` 都不在 = 真的没写过（只剩 `.bak` 的情况 [readText] 已经还原了）
                XLog.d("读配置：主文件与 .bak 都不在，按首装处理 | ${e.message}")
                cached = null
                return Load.Absent
            } catch (e: Throwable) {
                // EACCES / IO 错：只给 d（宿主每条按键前都要读一遍，w 会刷屏）
                XLog.d("读配置失败（文件在但读不出来）：${f.path} | ${e.message}")
                return Load.Unreadable
            }

        // 剥外皮（[FileObfuscation]）：首行不是魔数、或正文认不出，都算「读不懂」。
        //  **不做旧格式兼容**：加第二层之前写下的文件、以及开发期的明文文件，判 [Load.Unreadable]
        //  （页面只读、不接管），不退回「当它没写过」—— 那会把用户配置整份换成默认值。
        //  正文自带 md5（[ConfigCodec]），值对不上同样走 Unreadable。
        //
        //  **读不懂也按指纹缓存**：这是由文件内容推出来的结论，指纹不变结论就不会变；不缓存的话
        //  坏文件会被每一条按键重读一遍，`FileObfuscation` 那两行 W 也跟着刷屏（判据行会被冲掉）。
        //  与它相对的，上面 readText 的 IO 失败不缓存 —— 那是**瞬时**故障（EACCES / IO 抖动），
        //  下一按可能就好了。
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
     * 先包一层外皮（[FileObfuscation]），再走 [writeAtomically]（`android.util.AtomicFile`），
     * 避免执行侧读到半截内容。
     *
     * 写成功后**主动丢掉读缓存**（[load] 那一份）：本进程自己的写盘最清楚「刚换过内容」，
     * 主动失效省掉一次多余的 `stat` 比对，不依赖指纹去发现。
     * 跨进程的写方（设置页 `:ui`）清不到这里的缓存，那种情况由指纹里的 inode 兜住（见 [Cached]）。
     */
    fun write(config: Config): Boolean {
        val ok =
            writeAtomically(
                sharedFile(),
                FileObfuscation.wrap(FileObfuscation.MAGIC_RUNTIME, ConfigCodec.serialize(config)),
                openReadable = true,
            )
        if (ok) cached = null
        return ok
    }

    /**
     * 写配置（只有宿主进程调得到）。
     *
     * 先包一层外皮（[FileObfuscation]），再走 `android.util.AtomicFile` 的原子写：
     * `startWrite` → 写字节 → `finishWrite`（内部 flush + `fd.sync` + 旧文件改名成 `.bak` +
     * `.new` 改名成目标）。
     *
     * ## 为什么换成平台的 `AtomicFile`
     *
     *  早先是自己拼一个带 pid + 时间戳的 `.tmp`、`fd.sync()`、`renameTo`、失败时删残file ——
     *  那套能挡住「读到半截」，但**挡不住写完之前断电 / 进程被杀**：目标文件已经没了、`.tmp`
     *  还在，下一次读到的是一份不完整的新内容或干脆什么都没有。`AtomicFile` 在写之前先把旧文件
     *  留成 `.bak`，`openRead` 发现目标缺失会用它还原（Android 官方在底层做的）。
     *
     * 写完之后仍要**自己补 chmod**（[exposeToOthers]）：`AtomicFile` 只认「写 + 还原」，
     * 不知道我们要把文件放开给别的 uid 读。写在 `finishWrite` 之后、对**目标文件**做。
     *
     * ## 一处能力退化，记在这里
     *
     *  原来那个「tmp 名带 pid + 时间戳」是**并发写的防御**：两个写方各写各的 tmp，最后谁 rename
     *  谁算数，字节不会互相掺。`AtomicFile` 固定用 `.new`，两个写方会写同一个临时文件 ⇒
     *  理论上可能掺。当前写方是串行的（页面写配置、命令库写命令），跨进程同时写同一份文件这件事
     *  本来就没有锁保护、也只是「后写覆盖先写」，所以接受这个退化；真要并发写，得上锁文件，
     *  不在这一版做。
     *
     * DE 下的 `files/` 目录一开始并不存在（宿主自己没用过 DE），所以这里必须会建目录。
     *
     * @param openReadable 请求跨 uid 可读；只有 debug 包真的放开（见 `exposeToOthers`）。
     */
    fun writeAtomically(
        target: File,
        text: String,
        openReadable: Boolean,
    ): Boolean {
        val dir = target.parentFile
        if (dir == null) return false
        if (!dir.isDirectory && !dir.mkdirs()) {
            XLog.w("建目录失败：${dir.path}")
            return false
        }

        // **进程内串行**：`AtomicFile` 固定用同一个 `.new` 临时文件，两条线程同时写会互相掺字节。
        //  跨进程那两个写方本来就只能「后写覆盖先写」（见上面那段能力退化），这里只护同进程这一层。
        synchronized(writeLock) {
            val atomic = android.util.AtomicFile(target)
            // 只用于失败分支：`finishWrite` 之后置 null，免得对已交接的流再 `failWrite` 一次
            var stream: java.io.FileOutputStream? = null
            return try {
                val out = atomic.startWrite()
                stream = out
                out.write(text.toByteArray(Charsets.UTF_8))
                // 内部：flush + fd.sync + 旧文件 → `.bak`、`.new` → 目标（同目录内的 rename，原子）
                atomic.finishWrite(out)
                stream = null
                // 放开权限只为 adb 排查方便，release 侧是空实现
                if (openReadable) exposeToOthers(dir, target)
                true
            } catch (e: Throwable) {
                // `failWrite` 会把半截的 `.new` 收掉 —— 不像手写那版要自己记得删
                stream?.let { runCatching { atomic.failWrite(it) } }
                XLog.w("写文件失败：${target.path} | ${e.message}")
                false
            }
        }
    }

    /** [writeAtomically] 的进程内互斥（见那里的说明）。 */
    private val writeLock = Any()

    /**
     * 读文本文件并**剥掉开头的 UTF-8 BOM**（`U+FEFF`）。
     *
     * 为什么必须剥：配置是让人能手改的（排障时用 root 直接编辑、或者拉出来改完再推回去），
     * 而 Windows 记事本 / 记事本类的「另存为 UTF-8」会**自动加 BOM**。加了之后整份文本的第一段
     * 是 `\\uFEFF{` —— `JSONObject` 在这里直接抛，于是 [load] 判 [Load.Unreadable]：
     *   · 读方退化成默认值（侧键像没配过一样）；
     *   · 更糟的是**写方被闸住**（Unreadable 不允许写回），用户在界面里怎么改都「保存失败」，
     *     而他刚用记事本确认过文件内容好端端的 —— 这类「文件明明在、编辑器说没问题，模块说读不懂」
     *     是最难自查的一种，代价只是这里 `removePrefix` 一下。
     *
     * 注意：**配置文件的外皮**（[FileObfuscation]）在这一层之外：本函数拿到的仍是磁盘原文
     * （首行可能是 `OPlusKey-Runtime-1`），剥外皮在 [load] / `CommandLibrary.load` 里做。
     * 手改配置文件要连着外皮一起改（解两层、改完再包两层，配方见 [FileObfuscation]），
     * 正文里那个 `md5` 还得一起重算（见 `ConfigCodec`）。
     *
     * 只剥**开头一个**：BOM 的语义就是「这个流的开头」，中间出现 `U+FEFF`（零宽不换行空格）是
     * 正常字符，不该动。
     */
    fun readText(f: File): String {
        // **走 `AtomicFile.openRead()`**：目标文件缺失时它会先用 `.bak` 还原。写盘是
        //  「base → .bak、.new → base」两步，中间被杀 / 断电就会留下「base 不在、.bak 在」——
        //  这时直接 `f.readText()` 抛、调用方判「首装没配过」，用户整份配置随后被默认值覆盖。
        //  两个都不在时它抛 `FileNotFoundException`，调用方据此判「真的没写过」。
        val raw =
            android.util
                .AtomicFile(f)
                .openRead()
                .use { it.readBytes().decodeToString() }
        return stripBom(raw)
    }

    /** 真正的剥离（纯函数，便于命令行 / 单测核对；只认开头那一个）。 */
    fun stripBom(text: String): String = if (text.startsWith(BOM)) text.substring(1) else text

    /** UTF-8 文本流开头的 BOM（一个 `U+FEFF` 码元）。 */
    private const val BOM = '\uFEFF'
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
fun inodeOf(f: File): Long =
    try {
        android.system.Os
            .stat(f.path)
            .st_ino
    } catch (e: Throwable) {
        XLog.d("取文件 inode 失败（缓存指纹退化为修改时间 + 长度）：${f.path} | ${e.message}")
        0L
    }
