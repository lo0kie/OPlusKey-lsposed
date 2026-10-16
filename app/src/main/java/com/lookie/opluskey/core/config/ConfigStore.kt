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
        if (!f.isFile) {
            cached = null
            return Load.Absent
        }

        val modified = f.lastModified()
        val length = f.length()
        val inode = inodeOf(f)
        cached?.let {
            if (it.modified == modified && it.length == length && it.inode == inode) return it.result
        }

        val raw =
            try {
                readText(f)
            } catch (e: Throwable) {
                // EACCES / IO 错：只给 d（宿主每条按键前都要读一遍，w 会刷屏）
                XLog.d("读配置失败（文件在但读不出来）：${f.path} | ${e.message}")
                return Load.Unreadable
            }

        // 剥外皮（[FileObfuscation]）：首行不是魔数、或正文认不出，都算「读不懂」。
        //  **不做旧格式兼容**：加第二层之前写下的文件、以及开发期的明文文件，判 [Load.Unreadable]
        //  （页面只读、不接管），不退回「当它没写过」—— 那会把用户配置整份换成默认值。
        //  正文自带 md5（[ConfigCodec]），值对不上同样走 Unreadable。
        val text =
            FileObfuscation.unwrap(FileObfuscation.MAGIC_RUNTIME, raw)
                ?: return Load.Unreadable

        val result = ConfigCodec.parseOrNull(text)?.let { Load.Ok(it) } ?: Load.Unreadable
        cached = Cached(modified, length, inode, result)
        return result
    }

    /**
     * 上一次读的结果与它对应的文件指纹。
     *
     * 指纹是「修改时间 + 长度 + inode」三个字段，不合成一个数：合成会有碰撞的可能，分着比是精确的。
     * 为什么还要带上 inode：写方是设置页 `:ui` 进程、读方是宿主主进程，**写方清缓存清不到读方**，
     * 读方只能靠指纹自己发现「文件换了」——若新内容**长度恰好相同**、又落在同一个 mtime 粒度内，
     * 只看（修改时间, 长度）就会把陈值当命中。写盘走的是「先 `.tmp` 再 rename」，目标文件因此
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
     * 先包一层外皮（[FileObfuscation]），再走「先写 `.tmp` 再 rename」，避免执行侧读到半截内容。
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
     * **原子写**：先写同目录下的 `.tmp`、`fsync` 落盘，再 `rename` 覆盖目标。读方可能是另一个进程
     * （命令库读方是宿主主进程、写方是设置页 `:ui` 进程，主配置同理），直接 `writeText`
     * 是「先截断再写」，读方正好落在中间就读到半截内容。三个文件统一走这一份。
     *
     *  `fsync` 那一步不能省（与 `android.util.AtomicFile` 同一步）：rename 只保证「名字换过去」这件事
     * 原子，不保证被它盖住的内容已经在盘上 —— 掉电时可能落成「名字换了、内容还是空的」，正是
     * [Load.Unreadable] 那一档。只 `sync` 文件、不 `sync` 目录：与 `AtomicFile` 一致，省掉一次同步。
     *  代价是每次写盘多几毫秒，写盘都在用户动作之后（拨开关 / 松手 / 存命令），走的是 UI 线程 ——
     *  与 `SharedPreferences.commit()` 同一条路数，不值得为它把写盘改成异步（那会打断「落盘成功才
     *  提交状态、才广播」这条因果链）。
     *
     * DE 下的 `files/` 目录一开始并不存在（宿主自己没用过 DE），所以这里必须会建目录。
     *
     * @param openReadable 请求跨 uid 可读；只有 debug 包真的放开（见 `exposeToOthers`）。
     */
    internal fun writeAtomically(
        target: File,
        text: String,
        openReadable: Boolean,
    ): Boolean {
        val dir = target.parentFile
        if (dir == null) return false

        return try {
            if (!dir.isDirectory && !dir.mkdirs()) {
                XLog.w("建目录失败：${dir.path}")
                return false
            }
            // 临时名带上 pid 与时间戳：当前只有一个写方且串行，定名不会撞；这里只是防御 ——
            //  将来多一个并发写方（或同进程重入）时，定名会让两处共用同一个 tmp，
            //  互相把对方没写完的内容 rename 成目标文件。带上进程与时刻就从命名上错开。
            val tmp = File(dir, "${target.name}.${android.os.Process.myPid()}.${System.currentTimeMillis()}.tmp")
            val bytes = text.toByteArray(Charsets.UTF_8)
            java.io.FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            // 放开权限只为 adb 排查方便，release 侧是空实现
            if (openReadable) exposeToOthers(dir, tmp)

            // rename 在同一文件系统内是原子的，失败（跨挂载点 / 个别 FS 不支持）才退回「复制 + 删」
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        } catch (e: Throwable) {
            XLog.w("写文件失败：${target.path} | ${e.message}")
            false
        }
    }

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
    internal fun readText(f: File): String {
        val raw = f.readText(Charsets.UTF_8)
        return stripBom(raw)
    }

    /** 真正的剥离（纯函数，便于命令行 / 单测核对；只认开头那一个）。 */
    internal fun stripBom(text: String): String = if (text.startsWith(BOM)) text.substring(1) else text

    /** UTF-8 文本流开头的 BOM（一个 `U+FEFF` 码元）。 */
    private const val BOM = '\uFEFF'
}

/**
 * 取文件 inode（`android.system.Os.stat`，本地 syscall，很便宜）。
 *
 *  文件已不在 / stat 失败时返回 0：那与「取不到」同值，最多让缓存多失效一次（多读一遍盘），
 *  不会把陈值当命中 —— 方向是安全的那一侧。
 *
 *  写成**顶层**函数而不是对象成员：它是纯函数、不吃对象状态，而 [ConfigStore] 的函数条数已经贴着
 *  detekt `TooManyFunctions` 那一档，再往里放一个就把闸口顶红。
 */
private fun inodeOf(f: File): Long =
    try {
        android.system.Os
            .stat(f.path)
            .st_ino
    } catch (e: Throwable) {
        XLog.d("取文件 inode 失败（缓存指纹退化为修改时间 + 长度）：${f.path} | ${e.message}")
        0L
    }
