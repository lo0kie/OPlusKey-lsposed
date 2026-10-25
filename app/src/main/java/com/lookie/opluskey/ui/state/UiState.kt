package com.lookie.opluskey.ui.state

import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.config.ModuleFiles
import com.lookie.opluskey.core.log.XLog

/**
 * 「界面状态」的本地持久化 —— 不走用户配置（那份文件是执行侧要读的契约，落在
 * `ConfigStore.uiFile()` 那种「只有本进程看」的文件里）。存的是「这一页怎么显示」的偏好，格式为 `key=value`
 * 若干行（`#` 开头为注释）。读写两端都在本进程（设置页与宿主同 uid），不需要跨域权限；
 * 读在 `readPageLoadSnapshot`（建页那一刻、**IO 线程**上，见 `ui/state/PageLoadSnapshot.kt`）、
 * 写在「选择应用」菜单翻档那一下，读不了 / 解析不了一律退默认（关），与配置同一条降级纪律。
 */
internal object UiState {
    /** 「选择应用」页是否列出系统应用。 */
    private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"

    /** 「选择应用」页是否把**拉不起来**的应用也列出来（见 `SettingsPage.picker`）。 */
    private const val KEY_SHOW_UNOPENABLE = "show_unopenable"

    /**
     * 「系统那一侧**曾经**回过话」—— 只在收到注入侧回包那一下写 true，此后不再回 false。
     *
     *  它要回答的问题只有一个：**这次到底是「从来没用过」还是「用过、这会儿没起来」**。
     *  两者的现象一模一样（注入侧没有回包），但对用户说的话不同：前者要先去框架里勾作用域，
     *  后者只要重启。没有这一位，界面只能把两种可能一起糊成一句。
     *
     *  为什么存在界面状态里而不是用户配置里：它描述的是「这台机器上跑过没有」，不是用户的选择，
     *  也不该进执行侧要读的那份契约（`opluskey_config.conf`）。
     */
    private const val KEY_INJECT_EVER_ALIVE = "inject_ever_alive"

    /** 上一次收到注入侧回包时的那轮开机（`ProcClock.bootWall()`，秒级）—— 用来判「那次是不是本轮的」。 */
    private const val KEY_INJECT_ALIVE_BOOT = "inject_alive_boot"

    /**
     * 界面状态的一次读全 —— **建页那一次用的唯一入口**。
     *
     * 逐项各读一遍的话会把同一个文件 open + parse 好几遍；建页那次读现在整个挪到了
     * IO 线程上（见 `ui/state/PageLoadSnapshot.kt`），但「一次开一次盘」这件事本身仍然成立
     * —— 少一趟就少一趟，行为完全一样。
     *
     * 注意：这里**没有**「以小窗口打开」—— 那一项**不落盘**：
     * 它默认关、每次进「选择应用」页按「正在编辑的那个动作」重算，见 `SettingsPage.picker`。
     */
    fun readPrefs(): UiPrefs {
        val map = parse(readTextOrNull().orEmpty())
        return UiPrefs(
            showSystemApps = map[KEY_SHOW_SYSTEM_APPS]?.toBooleanStrictOrNull() ?: false,
            showUnopenable = map[KEY_SHOW_UNOPENABLE]?.toBooleanStrictOrNull() ?: false,
            injectEverAlive = map[KEY_INJECT_EVER_ALIVE]?.toBooleanStrictOrNull() ?: false,
            injectAliveBoot = map[KEY_INJECT_ALIVE_BOOT]?.toLongOrNull() ?: 0L,
        )
    }

    /**
     * 写一条；其余键原样保留（先读出来改一个键再整份写回）。走 [ModuleFiles.write]：
     * 虽只有本进程读写，但「先截断再写」遇到进程被杀会把整份状态变半截。
     * `openReadable = false`：不需要跨 uid 读。
     *
     * 注意：**读不出来时一个字节都不写**：写回是「读 → 改一个键 → 整份写」，
     *  读失败时手上只有空表 —— 照写就把**另一个键一起抹掉**了（用户只是切了一下「显示系统应用」，
     *  「显示失效应用」却悄悄回到默认）。
     *
     * @return 是否真的落盘 —— 调用方（`PageHeader` 那三处切换）会紧接着向用户报「已…」，
     *   丢了这个返回值就变成了「明明没写进去却说已落盘」。吞掉返回值、那三处又用
     *  `XLog.d` 打一句，release 下那行被编译期消除，是最难发现的一类谎报。
     */
    fun writeShowSystemApps(value: Boolean): Boolean {
        val text = readTextOrNull() ?: return refuseWrite(KEY_SHOW_SYSTEM_APPS, value)
        val map = parse(text)
        map[KEY_SHOW_SYSTEM_APPS] = value.toString()
        return writeText(render(map))
    }

    fun writeShowUnopenable(value: Boolean): Boolean {
        val text = readTextOrNull() ?: return refuseWrite(KEY_SHOW_UNOPENABLE, value)
        val map = parse(text)
        map[KEY_SHOW_UNOPENABLE] = value.toString()
        return writeText(render(map))
    }

    /**
     * 记下「系统那一侧回话了，就在这一轮开机里」。
     *
     *  调用方（`PageContent` 的注入侧回包处理）只在值**真的要变**时才调它：注入侧每次自检都回包，
     *  每次都落一次盘属于白写（这是主线程上的调用点，写盘必须可省）。
     *
     *  两条一起写：`ever` 是布尔、`boot` 是那一轮的开机指纹。`ever` 只增不减 ——
     *  它的语义是「这台机器上曾经跑起来过」，不是「现在活着」。
     *
     * @return 是否真的落盘
     */
    fun writeInjectAlive(bootWall: Long): Boolean {
        val text = readTextOrNull() ?: return refuseWrite(KEY_INJECT_EVER_ALIVE, true)
        val map = parse(text)
        map[KEY_INJECT_EVER_ALIVE] = true.toString()
        map[KEY_INJECT_ALIVE_BOOT] = bootWall.toString()
        return writeText(render(map))
    }

    /** 读失败时不写回，只报一声（判据级，用 `w`：`d` 在 release 被编译期消除）。 */
    private fun refuseWrite(
        key: String,
        value: Boolean,
    ): Boolean {
        XLog.w("写界面状态失败：原文件读不出来，不整份覆盖（$key=$value）")
        return false
    }

    /**
     * 读原始文本。
     *
     * @return **读不了返回 null**，与「文件还不存在 = 空串」分开 —— 写方靠这个区分
     *   「可以按空表重建」与「不许覆盖」（见 [writeShowSystemApps]）。
     *
     *  两件事都在 [ModuleFiles.read] 里：只剩 `.bak` 时由 `AtomicFile.openRead()` 先还原
     *  （**不能**先判 `isFile`，那会绕过还原、读到空串，下一次写回就把这份文件里其它键一起抹掉）；
     *  「文件在但读不出」与「文件真不在」分开判（同一个 `FileNotFoundException` 两种来路）。
     */
    private fun readTextOrNull(): String? =
        when (val r = files().read(ConfigStore.SLOT_UI)) {
            is ModuleFiles.Load.Ok -> {
                r.text
            }

            ModuleFiles.Load.Absent -> {
                //  主文件与 .bak 都不在 = 还没写过 ⇒ 空表（与「读不了」分开，见 KDoc）
                XLog.d("读界面状态：主文件与 .bak 都不在，按空表处理")
                ""
            }

            ModuleFiles.Load.Unreadable -> {
                // 判据级：读失败会让写回被拒（用户看到的是「没保存」），用 w 才能在 release 里看见
                XLog.w("读界面状态失败（按默认处理、且不写回）")
                null
            }
        }

    /** @return 是否真的落盘 —— 只意味着「这次改动活过了本进程」，失败由 [ModuleFiles.write] 记日志。 */
    private fun writeText(text: String): Boolean = files().write(ConfigStore.SLOT_UI, text, openReadable = false)

    /** 本模块的文件句柄（[ConfigStore] 是它的门面，这里只要知道「哪个槽」）。 */
    private fun files(): ModuleFiles = ConfigStore.files()

    /** `key=value` 逐行解析；注释 / 空行 / 没有 `=` 的行一律跳过（半截文件也只是少几个键）。 */
    private fun parse(text: String): MutableMap<String, String> {
        val map = LinkedHashMap<String, String>()
        text.lineSequence().forEach { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEach
            val i = t.indexOf('=')
            if (i > 0) map[t.substring(0, i).trim()] = t.substring(i + 1).trim()
        }
        return map
    }

    private fun render(map: Map<String, String>): String =
        buildString {
            appendLine("# OPlusKey 界面状态 —— 模块自己的显示偏好，不是用户配置（见 ui/state/UiState.kt）")
            map.forEach { (key, value) -> appendLine("$key=$value") }
        }
}

/**
 * 界面状态的一次读全（见 [UiState.readPrefs]）。
 *
 * @param showUnopenable 「选择应用」页是否列出拉不起来的应用
 * @param injectEverAlive 系统那一侧**曾经**回过话（只增不减）
 * @param injectAliveBoot 上一次回话时的那轮开机（`ProcClock.bootWall()`，0 = 从没回话）
 */
internal class UiPrefs(
    val showSystemApps: Boolean,
    val showUnopenable: Boolean,
    val injectEverAlive: Boolean,
    val injectAliveBoot: Long,
)
