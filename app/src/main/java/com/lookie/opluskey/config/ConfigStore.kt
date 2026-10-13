package com.lookie.opluskey.config

import com.lookie.opluskey.XLog
import java.io.File

/** 配置文件的位置与读写，全项目只有这一处。 */
object ConfigStore {

    /** 宿主 App（`com.oplus.gesture`）私有目录。 */
    private const val HOST_DIR = "/data/data/com.oplus.gesture"
    private const val HOST_FILES = "$HOST_DIR/files"
    private const val SHARED_FILE = "$HOST_FILES/opluskey_config.conf"

    /**
     * 自定义命令库（[CommandLibrary] 读写），与主配置分开存：
     * 主配置只记 `cmd:<名字>` 引用，命令内容在这一份里。
     */
    private const val COMMAND_FILE = "$HOST_FILES/opluskey_commands.conf"

    /**
     * 界面状态文件，不是用户配置（只有「显示系统应用」这类界面偏好）。
     * 与主配置分开存，重置 / 导出导入配置不影响它；只有宿主进程读写得到。
     */
    private const val UI_FILE = "$HOST_FILES/opluskey_ui.conf"

    /** 主配置文件（宿主私有目录里的那个）。 */
    fun sharedFile(): File = File(SHARED_FILE)

    /** 自定义命令库文件。 */
    fun commandFile(): File = File(COMMAND_FILE)

    /** 界面状态文件。 */
    fun uiFile(): File = File(UI_FILE)

    /**
     * 读配置；读不到或解析不了都退化成 [Config.DEFAULT]。
     * 只有宿主进程调得到：注入侧读这份文件必然 EACCES，返回默认值是预期行为。
     */
    fun read(): Config = readFrom(sharedFile()) ?: Config.DEFAULT

    /** 读一个文件；不存在或读不了返回 null（不抛）。 */
    private fun readFrom(f: File): Config? = try {
        if (f.isFile) ConfigCodec.parse(f.readText(Charsets.UTF_8)) else null
    } catch (e: Throwable) {
        // EACCES 是常态（注入侧跨域读被 SELinux 拒），只记简短日志
        XLog.d("读配置失败（忽略）：${f.path} | ${e.message}")
        null
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
     * @param openReadable true = 再把文件放开到 0644（跨 uid 读得到，方便 adb / root 排查）。
     *   只放开读、不放开写：`setWritable(true, false)` 会把 0600 抬成 0666（全局可写），
     *   rename 会保留该模式 —— 放开读的理由（跨 uid 读）不需要写权限。
     */
    internal fun writeAtomically(target: File, text: String, openReadable: Boolean): Boolean {
        val dir = target.parentFile
        if (dir == null) return false

        return try {
            if (!dir.isDirectory && !dir.mkdirs()) {
                XLog.w("建目录失败：${dir.path}")
                return false
            }
            // 逐级放开可遍历权限：只放开 files/ 不够，宿主数据目录本身是 0700
            dir.setReadable(true, false)
            dir.setExecutable(true, false)
            File(HOST_DIR).setExecutable(true, false)

            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (openReadable) tmp.setReadable(true, false)

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
}
