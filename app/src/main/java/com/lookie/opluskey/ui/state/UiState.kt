package com.lookie.opluskey.ui.state

import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage

/**
 * 「界面状态」的本地持久化 —— 不走用户配置（那份文件是执行侧要读的契约，落在
 * `ConfigStore.uiFile()` 那种「只有本进程看」的文件里）。存的是「这一页怎么显示」的偏好，格式为 `key=value`
 * 若干行（`#` 开头为注释）。读写两端都在本进程（设置页与宿主同 uid），不需要跨域权限；
 * 读在 [SettingsPage.createView]、写在「选择应用」菜单翻档那一下，读不了 / 解析不了
 * 一律退默认（关），与配置同一条降级纪律。
 */
internal object UiState {

    /** 「选择应用」页是否列出系统应用。 */
    private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"

    /** 「选择应用」页是否把拉不起来的应用藏掉（见 [SettingsPage.hideUnopenable]）。 */
    private const val KEY_HIDE_UNOPENABLE = "hide_unopenable"

    fun readShowSystemApps(): Boolean =
        parse(readText())[KEY_SHOW_SYSTEM_APPS]?.toBooleanStrictOrNull() ?: false

    fun readHideUnopenable(): Boolean =
        parse(readText())[KEY_HIDE_UNOPENABLE]?.toBooleanStrictOrNull() ?: false

    /**
     * 一次把两项读出来（**建页那次用的唯一入口**）。
     *
     * 两项各调一次 [readShowSystemApps] 那种写法会把同一个文件 open + parse **两遍**，而建页跑在
     * 主线程上（见 `SettingsPage.createView`）—— 两项一起读只开一次盘，行为完全一样。
     *
     * ⚠️ 这里**没有**「以小窗口打开」—— 那一项**不落盘**（2026-10-14 用户口径）：
     * 它默认关、每次进「选择应用」页按「正在编辑的那个动作」重算，见 `SettingsPage.smallWindow`。
     */
    fun readAll(): Pair<Boolean, Boolean> {
        val map = parse(readText())
        return Pair(
            map[KEY_SHOW_SYSTEM_APPS]?.toBooleanStrictOrNull() ?: false,
            map[KEY_HIDE_UNOPENABLE]?.toBooleanStrictOrNull() ?: false,
        )
    }

    /**
     * 写一条；其余键原样保留（先读出来改一个键再整份写回）。走 [ConfigStore.writeAtomically]：
     * 虽只有本进程读写，但「先截断再写」遇到进程被杀会把整份状态变半截。
     * `openReadable = false`：不需要跨 uid 读。
     *
     * @return 是否真的落盘 —— 调用方（`PageHeader` 那三处切换）会紧接着向用户报「已…」，
     *   丢了这个返回值就变成了「明明没写进去却说已落盘」。原来这里吞掉返回值、那三处又用
     *   `XLog.i` 断言，release 下两处都看不见，是最难发现的一类谎报。
     */
    fun writeShowSystemApps(value: Boolean): Boolean {
        val map = parse(readText())
        map[KEY_SHOW_SYSTEM_APPS] = value.toString()
        return writeText(render(map))
    }

    fun writeHideUnopenable(value: Boolean): Boolean {
        val map = parse(readText())
        map[KEY_HIDE_UNOPENABLE] = value.toString()
        return writeText(render(map))
    }

    private fun readText(): String = try {
        val f = ConfigStore.uiFile()
        // 剥 BOM：这份文件同样可能被记事本改过（见 ConfigStore.readText）
        if (f.isFile) ConfigStore.readText(f) else ""
    } catch (e: Throwable) {
        XLog.d("读界面状态失败（按默认处理）：${e.message}")
        ""
    }

    /** @return 是否真的落盘 —— 只意味着「这次改动活过了本进程」，失败由 writeAtomically 记日志。 */
    private fun writeText(text: String): Boolean =
        ConfigStore.writeAtomically(ConfigStore.uiFile(), text, openReadable = false)

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

    private fun render(map: Map<String, String>): String = buildString {
        appendLine("# OPlusKey 界面状态 —— 模块自己的显示偏好，不是用户配置（见 ui/state/UiState.kt）")
        map.forEach { (key, value) -> appendLine("$key=$value") }
    }
}
