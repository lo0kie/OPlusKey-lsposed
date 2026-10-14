package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import java.io.File

/** 配置文件的位置与读写，全项目只有这一处。 */
object ConfigStore {

    /**
     * 宿主 App（`com.oplus.gesture`）私有目录 —— 用的是 **DE（设备加密）** 那一份。
     *
     * 为什么不用 CE（`/data/data/…`）：CE 在**首次解锁前不可见**，开机到解锁之间配置只能读到
     * 默认值（`enabled=false`），注入侧据此按「不接管」处理、侧键那一段只能走系统原生。
     * DE 与设备加密同源、开机即可读，解锁前也能按真配置接管。
     */
    private const val HOST_DIR = "/data/user_de/0/com.oplus.gesture"

    private const val HOST_FILES = "$HOST_DIR/files"

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
        data class Ok(val config: Config) : Load

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
     * 为什么必须分三态：原先只有「读到一份 / 退化成默认值」两条路，而「退化成默认值」这一条
     * 在写方那边会被当成真相继续用 —— 页面读失败 ⇒ 拿默认值渲染 ⇒ 用户随便改一项 ⇒
     * 整份默认值落盘，**主开关、三个手势绑定、振动开关一起被抹掉**。
     * 真机上 2026-10-14 先丢的是「振动反馈」（只剩这一个开关被人一眼看出来），
     * 再往后一次就是把绑定一起抹掉。
     *
     *  只有宿主进程调得到。注入侧读这份文件必然 EACCES（也正是它不读文件、只吃配置广播的原因），
     * 那边若真调到会拿到 [Load.Unreadable]，按不接管处理 —— 与它原来拿默认值的行为一致。
     */
    fun load(): Load {
        val f = sharedFile()
        if (!f.isFile) return Load.Absent

        val text = try {
            readText(f)
        } catch (e: Throwable) {
            // EACCES / IO 错：只给 d（宿主每条按键前都要读一遍，w 会刷屏）
            XLog.d("读配置失败（文件在但读不出来）：${f.path} | ${e.message}")
            return Load.Unreadable
        }

        return ConfigCodec.parseOrNull(text)?.let { Load.Ok(it) } ?: Load.Unreadable
    }

    /**
     * 读配置；**读不到 / 读不懂都退化成 [Config.DEFAULT]**（不抛）。
     *
     * ⚠️ 这是**只读方**的入口（宿主侧每条按键、每次执行、每次报活都读一遍）。语义是
     * 「读不到就按不接管处理，行为与原生一致」，对只读方是对的。
     * **写方不许用这个** —— 写之前必须拿 [load] 的三态，见 [Load]。
     */
    fun read(): Config = when (val l = load()) {
        is Load.Ok -> l.config
        Load.Absent, Load.Unreadable -> Config.DEFAULT
    }

    /**
     * 写配置（只有宿主进程调得到）。
     * 先写 `.tmp` 再 rename，避免执行侧读到半截内容。
     */
    fun write(config: Config): Boolean =
        writeAtomically(sharedFile(), ConfigCodec.serialize(config), openReadable = true)

    /**
     * **原子写**：先写同目录下的 `.tmp`，再 `rename` 覆盖目标。读方可能是另一个进程
     * （命令库读方是宿主主进程、写方是设置页 `:ui` 进程，主配置同理），直接 `writeText`
     * 是「先截断再写」，读方正好落在中间就读到半截内容。三个文件统一走这一份。
     *
     * DE 下的 `files/` 目录一开始并不存在（宿主自己没用过 DE），所以这里必须会建目录。
     *
     * @param openReadable 请求跨 uid 可读；只有 debug 包真的放开（见 `exposeToOthers`）。
     */
    internal fun writeAtomically(target: File, text: String, openReadable: Boolean): Boolean {
        val dir = target.parentFile
        if (dir == null) return false

        return try {
            if (!dir.isDirectory && !dir.mkdirs()) {
                XLog.w("建目录失败：${dir.path}")
                return false
            }
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
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
     * 只剥**开头一个**：BOM 的语义就是「这个流的开头」，中间出现 `U+FEFF`（零宽不换行空格）是
     * 正常字符，不该动。
     */
    internal fun readText(f: File): String {
        val raw = f.readText(Charsets.UTF_8)
        return stripBom(raw)
    }

    /** 真正的剥离（纯函数，便于命令行 / 单测核对；只认开头那一个）。 */
    internal fun stripBom(text: String): String =
        if (text.startsWith(BOM)) text.substring(1) else text

    /** UTF-8 文本流开头的 BOM（一个 `U+FEFF` 码元）。 */
    private const val BOM = '\uFEFF'
}
