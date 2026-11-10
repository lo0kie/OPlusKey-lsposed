package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import java.io.File

/**
 * 宿主私有目录里「**一个模块的**那几份配置文件」—— 多模块共用的底层接口。
 *
 * ## 命名与位置
 *
 * 所有模块共用宿主自己的 `files/` 目录，靠**文件名前缀**分开：
 *
 * ```
 * <模块 id>_<槽名>.conf
 * ```
 *
 * [ConfigStore.MODULE_ID] 是 `opluskey`，它那三份正好就是原来那三个文件名
 * （`opluskey_config.conf` / `opluskey_commands.conf` / `opluskey_ui.conf`）——
 * 加这一层**不迁移任何现成文件**，一个字节都不变。
 *
 * 为什么平铺而不是一个模块一个子目录：现有文件就在这一层，改子目录要写迁移；
 * 而且 `AtomicFile` 的 `.bak` / `.new` 只在**同目录**里做改名（跨目录不是原子操作）。
 * 前缀方案换来的唯一代价是「id 里不能有下划线」（见 [of]）。
 *
 * ## 这一层管什么、不管什么
 *
 * **管**：按名字定位文件、原始文本的原子读写、三态（[Load]）、删干净（连 `.bak` / `.new`）、
 * 列目录。判据只有一条：**与「这份文件属于哪个模块」有关的事都在这层**。
 *
 * **不管**：外皮（[FileObfuscation]）与编解码。各模块的格式版本各涨各的
 * （`ConfigCodec.VERSION` / `CommandLibrary.VERSION` / 导出信封版本是三个独立的数），
 * 硬凑到一层里就得给「版本」再造一套多态，反而把「一份文件一种格式」这件简单事搞复杂。
 * 于是 [read] / [write] 搬的是**磁盘原文**（只顺手剥掉开头那个 BOM，理由见 [stripBom]），
 * 套不套外皮由调用方决定。
 *
 * ## 为什么读必读三态
 *
 * [Load.Absent]（还没写过）与 [Load.Unreadable]（文件在、但读不出来）**在写方那边完全是两件事**：
 * 前者可以拿默认值重建，后者要是也按默认值写回，就把用户配的东西整份抹了。
 * 这条纪律原本在主配置与命令库里各写了一遍，现在只有这一处。
 */
class ModuleFiles internal constructor(
    /** 模块 id：只含小写字母 / 数字 / 连字符，不含下划线（[of] 会校验）。 */
    val id: String,
) {
    /**
     * 「文件在但读不出来」这条 W 是否已经报过（理由见 [ConfigStore] 里同名字段：
     * 这条判据会被每条按键打到）。
     */
    @Volatile
    private var unreadableWarned = false

    /** 读不出来的那一行 —— 同一件事的两个 catch 分支**用同一档日志**，别一处 `w` 一处 `d`。 */
    private fun warnUnreadable(
        file: File,
        detail: String?,
    ) {
        if (unreadableWarned) {
            XLog.d("读模块文件失败（文件在但读不出来）：${file.path} | $detail")
        } else {
            unreadableWarned = true
            XLog.w("读模块文件失败（文件在但读不出来）：${file.path} | $detail（后续同类只记调试档）")
        }
    }

    /**
     * 这个模块的配置文件目录 —— 与宿主自己用的是同一个（见类注释里为什么平铺）。
     *
     * 目录可能**还不存在**（宿主自己从没用过 DE 那一份），[write] 会按需建。
     */
    fun dir(): File = hostFilesDir()

    /** 某个槽的那个文件。槽名由各模块自己定，底层不认识任何具体名字。 */
    fun file(slot: String): File = File(dir(), "$id$SEP$slot$SUFFIX")

    /**
     * 这个槽有没有落过盘。
     *
     * 注意：只看主文件。只剩 `.bak`（写盘写到一半被杀）时这里报 `false`，但 [read] 仍能读出来
     * —— `AtomicFile.openRead()` 会先还原。判「要不要新建」用它，判「有没有数据」用 [read]。
     */
    fun exists(slot: String): Boolean = file(slot).exists()

    /**
     * 读一个槽的磁盘原文（已剥 BOM），三态见 [Load]。
     *
     * **不判 `isFile`**：只剩 `.bak` 时主文件不在、配置却还在 —— 交给
     * `AtomicFile.openRead()` 去还原，判成「没写过」会被调用方用默认值覆盖。
     *
     * 读不出来那一路**没有缓存**（这一层压根不缓存）：EACCES / IO 抖动是瞬时故障，
     * 缓存住会让一次偶发失败一直粘着（主配置那边的指纹缓存是另一回事，见 `ConfigStore.load`）。
     */
    fun read(slot: String): Load {
        val f = file(slot)
        val raw =
            try {
                android.util
                    .AtomicFile(f)
                    .openRead()
                    .use { it.readBytes().decodeToString() }
            } catch (e: java.io.FileNotFoundException) {
                //  **FNF 有两种**：文件真的不在，与「文件在但没权限」—— `FileInputStream` 对
                //  EACCES 抛的也是 `FileNotFoundException`。后者按「没写过」处理，下一次写就会把
                //  用户的配置整份换成默认值，所以先看文件到底在不在。
                if (f.exists()) {
                    warnUnreadable(f, e.message)
                    return Load.Unreadable
                }
                return Load.Absent
            } catch (e: Throwable) {
                warnUnreadable(f, e.message)
                return Load.Unreadable
            }
        return Load.Ok(stripBom(raw))
    }

    /**
     * 写一个槽（磁盘原文，调用方自己套外皮）。
     *
     * 走 `android.util.AtomicFile`：`startWrite` → 写字节 → `finishWrite`（内部 flush + `fd.sync`
     * + 旧文件改名成 `.bak` + `.new` 改名成目标）。自己拼 `.tmp` 那套能挡「读到半截」，
     * 挡不住「写完之前断电」—— 那时目标已经没了、`.tmp` 还在；`AtomicFile` 把旧文件留成 `.bak`，
     * `openRead` 发现目标缺失会用它还原。
     *
     * 写完之后**自己补 chmod**（[exposeToOthers]）：`AtomicFile` 不知道我们要把文件放开给别的 uid 读。
     *
     * @param openReadable 请求跨 uid 可读；只有 debug 包真的放开（见 `HostFilePerms`）。
     * @return 是否真的落了盘。
     */
    fun write(
        slot: String,
        text: String,
        openReadable: Boolean = false,
    ): Boolean {
        val target = file(slot)
        val dir = target.parentFile
        if (dir == null) return false
        if (!dir.isDirectory && !dir.mkdirs()) {
            XLog.w("建目录失败：${dir.path}")
            return false
        }

        // **进程内串行**：`AtomicFile` 固定用同一个 `<文件>.new` 临时文件，两条线程同时写会互相掺字节。
        //
        // **跨进程这一层没有任何互斥，而「后写覆盖先写」这个假设不成立**：同一个槽有两个写方
        //  （`:ui` 进程与宿主主进程，同 uid），两边都按 `<槽>.conf.new` 这个名字写临时文件 ——
        //  同时写时它们往**同一个字节流**里各写一份，`finishWrite` 再把混出来的那份改名成目标。
        //  结果不是「后写的那份赢」，而是一份半 A 半 B / 长度对不上的内容：读侧按
        //  [Load.Unreadable] 处理（不覆盖、不动），最坏时那一份配置要等下一次完整写盘才恢复。
        //  变量表那边靠 `VariableStore` 的 `.lock` 文件串行，配置 / 命令库**没有**那把锁。
        //  这里仍然只护进程内：换临时文件名或跨进程锁都会改到写盘机制本身，不在这一层做。
        synchronized(writeLock) {
            val atomic = android.util.AtomicFile(target)
            // 只用于失败分支：`finishWrite` 之后置 null，免得对已交接的流再 `failWrite` 一次
            var stream: java.io.FileOutputStream? = null
            return try {
                val out = atomic.startWrite()
                stream = out
                out.write(text.toByteArray(Charsets.UTF_8))
                atomic.finishWrite(out)
                stream = null
                // 放开权限只为 adb 排查方便，release 侧是空实现
                if (openReadable) exposeToOthers(dir, target)
                true
            } catch (e: Throwable) {
                // `failWrite` 会把半截的 `.new` 收掉
                stream?.let { runCatching { atomic.failWrite(it) } }
                XLog.w("写模块文件失败：${target.path} | ${e.message}")
                false
            }
        }
    }

    /**
     * 删掉一个槽 —— **连 `.bak` / `.new` 一起**。
     *
     * 只删主文件的话，下一次 [read] 会把 `.bak` 还原回来：用户以为删掉了，其实又冒出来了。
     *
     * @return 删完之后这个槽真的不在（本来就不在也算 true）。
     */
    fun delete(slot: String): Boolean {
        val f = file(slot)
        val all =
            listOf(
                f,
                File(f.path + BAK),
                File(f.path + NEW),
                File(f.path + PREV_SUFFIX),
                File(f.path + PREV_SUFFIX + TEMP_SUFFIX),
            )
        all.forEach { runCatching { it.delete() } }
        //  **三个都要判**：只判主文件的话，`.bak` 删不掉也照样返 true —— 而 `AtomicFile.openRead()`
        //  在主文件缺失时会拿 `.bak` 还原（见 [read] 的注释），于是那份配置「删掉之后又活了」。
        val leftover = all.filter { it.exists() }
        if (leftover.isNotEmpty()) {
            XLog.w("模块文件：${f.name} 没删干净（还剩 ${leftover.joinToString { it.name }}）")
        }
        return leftover.isEmpty()
    }

    /**
     * 删掉这个模块的**全部**槽（卸载 / 重置用）。
     *
     * @return 是不是每一个槽都删干净了。槽本来就只剩 `.bak`（主文件已不在）也算删干净 ——
     *   判的是「删完还有没有残留」，不是「删掉了几个」。
     */
    fun deleteAll(): Boolean = slots().all { delete(it) }

    /**
     * 这个模块有哪些槽（扫目录得来，不用注册表）。
     *
     *  主文件与 `.bak` / `.new` / `.prev` **都算这个槽还在**：只剩 `.bak`（写盘写到一半被杀）时
     *  主文件不在，但 [read] 仍能还原出内容 —— 光按 `.conf` 结尾扫会把那份配置**从列表里抹掉**，
     *  它的槽号随即被 [ConfigProfiles.nextSlot] 复用，用户那份配置就找不回来了。
     *  `.new` 与 `.prev` 是写盘过程中的副产物：`.new` 本身不含完整数据，只跟 `.bak` 一样当
     *  「槽号被占着」的标记；`.prev` 则是完整的一份（见 [snapshotModuleFile]），
     *  它单独存在意味着「主文件那次写入没落地」，正是**最不该被复用槽号**的情形。
     *  目录不存在时返回空表。
     */
    fun slots(): List<String> {
        val files = dir().listFiles() ?: return emptyList()
        val prefix = "$id$SEP"
        return files
            .map { it.name }
            .filter { it.startsWith(prefix) }
            //  **只收四种合法形态**：`<槽>.conf`、`.conf.bak`、`.conf.new`、`.conf.prev`。
            //  只按前缀过滤会把同目录里的其它东西（最典型的是 `…_vars.conf.lock` 这类锁文件）
            //  当成一个槽 —— 于是 `deleteAll()` 去删一个不存在的 `<假槽>.conf`、报「删干净了」，
            //  而真正的残留还在。
            .filter { name ->
                name.endsWith(SUFFIX) ||
                    name.endsWith(SUFFIX + BAK) ||
                    name.endsWith(SUFFIX + NEW) ||
                    name.endsWith(SUFFIX + PREV_SUFFIX)
            }.map { name ->
                //  剥掉可能存在的 `.bak` / `.new` / `.prev` 尾巴，再剥掉 `.conf` —— 种种形态落到同一个槽名
                val base =
                    name
                        .removeSuffix(BAK)
                        .removeSuffix(NEW)
                        .removeSuffix(PREV_SUFFIX)
                        .removeSuffix(SUFFIX)
                name to base.removePrefix(prefix)
            }.filter { it.second.isNotEmpty() }
            .map { it.second }
            .distinct()
            .sorted()
    }

    /** 读结果的三种情形 —— 「还没写过」与「读不懂」必须分开，理由见类注释。 */
    sealed interface Load {
        /** 读到了一份（原文，已剥 BOM）。 */
        data class Ok(
            val text: String,
        ) : Load

        /** 文件不存在（首装 / 刚清过数据）—— 用默认值是安全的，**允许写回**。 */
        data object Absent : Load

        /**
         * 文件在，但读不了 / 读不懂 —— **不允许**按默认值写回。
         *
         * 这时手上那份默认值不是用户的真相，只是「读失败」的替身；谁把它写回，
         * 就等于把用户配的东西整份抹成默认值。
         */
        data object Unreadable : Load

        /** 读到就交正文，否则 `null` —— 只给「读不到就当没有」的只读方用（写方必须走 `when`）。 */
        fun textOrNull(): String? = (this as? Ok)?.text
    }

    companion object {
        /** 文件名里模块 id 与槽名之间的分隔符 —— 也是「id 不许含下划线」这条约束的来处。 */
        private const val SEP = "_"

        /** 模块配置的扩展名。 */
        private const val SUFFIX = ".conf"

        /** `AtomicFile` 的备份 / 临时后缀（删的时候要连它们一起删）。 */
        private const val BAK = ".bak"
        private const val NEW = ".new"

        /** id 的合法形态：小写字母 / 数字 / 连字符，且不能以连字符开头（见类注释）。 */
        private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9-]*$")

        /** 扫目录时认模块名：取第一个下划线之前那一段。 */
        private val MODULE_NAME = Regex("^([a-z0-9][a-z0-9-]*)_[^_].*\\.conf$")

        /**
         * 取一个模块的句柄（进程内同一个 id 交同一个实例）。
         *
         *  id **必须**满足 [ID_PATTERN]：文件名的切分点是第一个下划线，id 里再有下划线就会把
         *  切点定错（`my_mod_config.conf` 会被读成「`my` 模块的 `mod_config` 槽」）。
         *  因为它是**写代码的人**写死的常量，不是运行时数据，违规直接抛 —— 这是一个编程错误，
         *  不该被静默兜成「换个名字接着跑」。
         */
        fun of(id: String): ModuleFiles {
            require(ID_PATTERN.matches(id)) { "模块 id 只允许小写字母 / 数字 / 连字符：$id" }
            return cache.getOrPut(id) { ModuleFiles(id) }
        }

        /**
         * 目录里**已经有配置文件**的那些模块 id（扫出来的，不用注册表）。
         *
         * 用途是「让界面/清理工具知道还有谁在」；读取某个具体模块永远走 [of]，
         * 不依赖这里 —— 没落过盘的模块扫不出来，那正是它的正常状态。
         */
        fun modules(): List<String> {
            val files = hostFilesDir().listFiles() ?: return emptyList()
            return files
                .mapNotNull { MODULE_NAME.matchEntire(it.name)?.groupValues?.get(1) }
                .distinct()
                .sorted()
        }

        private val cache = java.util.concurrent.ConcurrentHashMap<String, ModuleFiles>()
    }
}

/**
 * 宿主私有目录 —— 所有模块共用的那一层（`/data/user_de/<uid>/<宿主包名>/files`）。
 *
 * 为什么是 **DE（设备加密）** 那一份：CE（`/data/data/…`）在**首次解锁前不可见**，
 * 开机到解锁之间配置只能读到默认值（`enabled=false`），注入侧据此按「不接管」处理、
 * 侧键那一段只能走系统原生。DE 与设备加密同源、开机即可读。
 *
 * 为什么用户 id 不能写死成 0：`/data/user_de/<uid>/…` 里的 `<uid>` 是**当前用户**的用户 id
 * （`android.os.Process.myUid() / 100000`）。副用户 / 工作资料下不是 0，写死 0 会一直去读
 * 主用户那份 —— 那些用户改配置怎么都不生效。
 */
fun hostFilesDir(): File = File(HOST_FILES)

private val HOST_FILES: String =
    "/data/user_de/${android.os.Process.myUid() / 100000}/${HostApp.PACKAGE}/files"

/**
 * 剥掉开头的 UTF-8 BOM（`U+FEFF`）。
 *
 * 为什么必须剥：配置是让人能手改的（排障时用 root 直接编辑、或者拉出来改完再推回去），
 * 而 Windows 记事本类的「另存为 UTF-8」会**自动加 BOM**。加了之后整份文本的第一段是
 * `\uFEFF{` —— JSON 解析直接抛，于是读方退化成默认值、写方被「读不懂不许写回」闸住，
 * 用户在界面里怎么改都「保存失败」，而他刚用记事本确认过文件内容好端端的。
 *
 * 只剥**开头一个**：BOM 的语义就是「这个流的开头」，中间出现 `U+FEFF`（零宽不换行空格）
 * 是正常字符，不该动。
 */
fun stripBom(text: String): String = if (text.startsWith(BOM)) text.substring(1) else text

/** UTF-8 文本流开头的 BOM（一个 `U+FEFF` 码元）。 */
private const val BOM = '\uFEFF'

/**
 * 我们自己留的历史快照后缀。
 *
 * **不能复用 `.bak`**：那是 `AtomicFile` 的语义位 —— `openRead()` 见到它就认为上次写盘没写完，
 * 会删主文件、把它顶上去。快照叫 `.prev`，`AtomicFile` 不认识。
 */
private const val PREV_SUFFIX = ".prev"

/** 快照落地的临时名（写完 rename 成 [PREV_SUFFIX]）；中途掉电留下的残片，`slots()` 不会认成槽。 */
private const val TEMP_SUFFIX = ".tmp"

/**
 * 把**当前主文件**复制成一份历史快照（`.prev`）—— 写盘之前调，给「下一次读不懂」留一条退路。
 *
 * ## 为什么必须自己留，而不是指望 `AtomicFile`
 *
 *  现代 `AtomicFile`（`startWrite` → 写 `.new` → `finishWrite`）在成功那一刻直接**删掉**旧 base
 *  再把 `.new` 改名过去，全程不产生 `.bak`。真机验证过：这个目录里写了几十次，
 *  `*.config.conf.bak` 一次都没出现过 —— 于是「从备份恢复」那条路是空转的。
 *
 *  主文件坏掉时用户只剩「清空配置」（绑定、开关、变量一起丢），而**上一份好的**其实就在刚才
 *  那个位置 —— 写之前先留一份就没有这个问题。
 *
 *  写成顶层函数、收 [f] 而不收 `slot`：它不读任何实例状态。放在 [ModuleFiles] 里会把这个类
 *  顶破 detekt 的函数数上限。
 *
 * @return 复制成了没有（主文件不在时算成功：没什么可留的）
 */
internal fun snapshotModuleFile(f: java.io.File): Boolean {
    if (!f.isFile) return true
    val prev = java.io.File(f.path + PREV_SUFFIX)
    //  **先写 `.prev.tmp` 再 rename**，不直接 `copyTo(prev, overwrite = true)`：那是原地截断重写，
    //  复制到一半掉电时旧的退路也没了 —— 而这份快照正是「主文件读不懂」时唯一能救回来的东西。
    //  同目录 rename 是原子的：要么还是旧快照，要么已经是完整的新快照，中间态不存在。
    //  写完 `fd.sync()` 再 rename：ext4 的延迟分配下，close 之后 rename 成功也可能落成空文件。
    val tmp = java.io.File(f.path + PREV_SUFFIX + TEMP_SUFFIX)
    return try {
        java.io.FileOutputStream(tmp).use { out ->
            f.inputStream().use { input ->
                input.copyTo(out)
            }
            out.flush()
            out.fd.sync()
        }
        if (tmp.renameTo(prev)) {
            true
        } else {
            XLog.w("模块文件：历史快照改名失败（${prev.path}）")
            tmp.delete()
            false
        }
    } catch (e: Throwable) {
        //  复制不了不阻断写盘：那只是少了退路，而写盘是用户此刻要做的事
        XLog.w("模块文件：留历史快照失败（${prev.path}）| ${e.message}")
        tmp.delete()
        false
    }
}

/**
 * 读那份历史快照（[snapshotModuleFile] 留的 `.prev`），读不出给 `null`。
 *
 *  故意**不经 `AtomicFile`**：它的 `openRead()` 会先去动主文件（那个可能已经坏了），
 *  而这里要的就是快照本体。
 *
 *  读不出来（没有快照 / 权限 / 它本身也坏）给 `null` —— 调用方该退回「不可读」，
 *  绝不能拿默认值顶上去。
 */
internal fun readModuleFileBackup(f: java.io.File): String? {
    val prev = java.io.File(f.path + PREV_SUFFIX)
    if (!prev.isFile) return null
    return try {
        stripBom(prev.readText())
    } catch (e: Throwable) {
        XLog.w("模块文件：读历史快照失败（${prev.path}）| ${e.message}")
        null
    }
}

/** [ModuleFiles.write] 的进程内互斥（理由见那里）。 */
private val writeLock = Any()

/** 跨进程锁文件的后缀（见 [withCrossProcessLock]）—— 不以 `.conf` 结尾，不会被当成一个槽。 */
private const val LOCK_SUFFIX = ".lock"

/**
 * 「读出来 → 改 → 写回去」那一段的**跨进程**互斥 —— 锁文件是目标文件旁边那个 `.lock`。
 *
 *  同一个槽有两个写方（`:ui` 进程与宿主主进程，同 uid）：进程内的锁管不到对方，两边各读一份
 *  旧表再写回，后写的那一份就把先写的那一次改动盖掉了。锁文件由内核持有，进程死了自动释放。
 *
 *  拿不到锁时**不阻塞功能**：记一行日志、照常做这一次（宁可极小概率丢一次改动，
 *  也不要在按键路径上卡住）。所以是 [java.nio.channels.FileLock.tryLock] 而不是 `lock()`：
 *  另一个进程正拿着锁、而它刚好被冻结时，`lock()` 会把调用线程卡死。
 *
 * @param lockFile 目标文件（锁就是它 + [LOCK_SUFFIX]）
 * @param label 日志主语（`变量` / `界面状态`）—— 故障时日志要能看出是哪一份在串行
 *
 *  公开（不是 `internal`）：界面层（`:app`）的 `UiState` 也要用同一把尺子，而 `internal`
 *  在 Gradle 模块之间不可见 —— 另写一份就又是「同一件事两套实现」。
 */
fun <T> withCrossProcessLock(
    lockFile: java.io.File,
    label: String,
    block: () -> T,
): T {
    val channel =
        try {
            java.io.RandomAccessFile(java.io.File(lockFile.path + LOCK_SUFFIX), "rw").channel
        } catch (e: Throwable) {
            XLog.w("$label：跨进程锁打不开（${e.javaClass.simpleName}），这一次不串行")
            return block()
        }
    return channel.use { ch ->
        //  `tryLock` 抛异常（通常是同进程重入的 `OverlappingFileLockException`）与返回 null
        //  是同一档：都当「拿不到锁、这次不串行」，不把异常往上带。
        val lock =
            try {
                ch.tryLock()
            } catch (e: Throwable) {
                XLog.w("$label：拿跨进程锁失败（${e.javaClass.simpleName}），这一次不串行")
                null
            }
        if (lock == null) {
            XLog.w("$label：拿不到跨进程锁（另一进程正在写），这一次不串行")
            return@use block()
        }
        try {
            block()
        } finally {
            runCatching { lock.release() }
        }
    }
}
