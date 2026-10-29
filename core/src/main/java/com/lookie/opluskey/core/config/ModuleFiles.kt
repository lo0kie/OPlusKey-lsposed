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
                    XLog.d("读模块文件失败（文件在但读不出来）：${f.path} | ${e.message}")
                    return Load.Unreadable
                }
                return Load.Absent
            } catch (e: Throwable) {
                XLog.d("读模块文件失败（文件在但读不出来）：${f.path} | ${e.message}")
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

        // **进程内串行**：`AtomicFile` 固定用同一个 `.new` 临时文件，两条线程同时写会互相掺字节。
        //  跨进程那两个写方本来就只能「后写覆盖先写」，这里只护同进程这一层。
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
        val all = listOf(f, File(f.path + BAK), File(f.path + NEW))
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
     * `.bak` / `.new` 天然被排掉：它们以那两个后缀**结尾**，不匹配 [SUFFIX]。
     * 目录不存在时返回空表。
     */
    fun slots(): List<String> {
        val files = dir().listFiles() ?: return emptyList()
        val prefix = "$id$SEP"
        return files
            .map { it.name }
            .filter { it.startsWith(prefix) && it.endsWith(SUFFIX) }
            .map { it.removePrefix(prefix).removeSuffix(SUFFIX) }
            .filter { it.isNotEmpty() }
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

/** [ModuleFiles.write] 的进程内互斥（理由见那里）。 */
private val writeLock = Any()
