package com.lookie.opluskey.page

import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.ConfigStore

/**
 * 「界面状态」的本地持久化 —— 不走用户配置（那份文件是执行侧要读的契约，理由见
 * `ConfigStore.UI_FILE` 的注释）。存的是「这一页怎么显示」的偏好，格式为 `key=value`
 * 若干行（`#` 开头为注释）。读写两端都在本进程（设置页与宿主同 uid），不需要跨域权限；
 * 读在 [SettingsPage.createView]、写在「选择应用」菜单翻档那一下，读不了 / 解析不了
 * 一律退默认（关），与配置同一条降级纪律。
 */
internal object UiState {

    /** 「选择应用」页是否列出系统应用。 */
    private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"

    /** 「选择应用」页是否把拉不起来的应用藏掉（见 [SettingsPage.hideUnopenable]）。 */
    private const val KEY_HIDE_UNOPENABLE = "hide_unopenable"

    /** 「选择应用」菜单里「小窗打开」那一项（见 [SettingsPage.smallWindow]）。 */
    private const val KEY_SMALL_WINDOW = "small_window"

    fun readShowSystemApps(): Boolean =
        parse(readText())[KEY_SHOW_SYSTEM_APPS]?.toBooleanStrictOrNull() ?: false

    fun readHideUnopenable(): Boolean =
        parse(readText())[KEY_HIDE_UNOPENABLE]?.toBooleanStrictOrNull() ?: false

    fun readSmallWindow(): Boolean =
        parse(readText())[KEY_SMALL_WINDOW]?.toBooleanStrictOrNull() ?: false

    /**
     * 写一条；其余键原样保留（先读出来改一个键再整份写回）。走 [ConfigStore.writeAtomically]：
     * 虽只有本进程读写，但「先截断再写」遇到进程被杀会把整份状态变半截。
     * `openReadable = false`：不需要跨 uid 读。
     */
    fun writeShowSystemApps(value: Boolean) {
        val map = parse(readText())
        map[KEY_SHOW_SYSTEM_APPS] = value.toString()
        writeText(render(map))
    }

    fun writeHideUnopenable(value: Boolean) {
        val map = parse(readText())
        map[KEY_HIDE_UNOPENABLE] = value.toString()
        writeText(render(map))
    }

    fun writeSmallWindow(value: Boolean) {
        val map = parse(readText())
        map[KEY_SMALL_WINDOW] = value.toString()
        writeText(render(map))
    }

    private fun readText(): String = try {
        val f = ConfigStore.uiFile()
        if (f.isFile) f.readText(Charsets.UTF_8) else ""
    } catch (e: Throwable) {
        XLog.d("读界面状态失败（按默认处理）：${e.message}")
        ""
    }

    private fun writeText(text: String) {
        // 写不进去只意味着「这次改动活不过本进程」，不值得弹错；失败由 writeAtomically 记日志。
        ConfigStore.writeAtomically(ConfigStore.uiFile(), text, openReadable = false)
    }

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
        appendLine("# OPlusKey 界面状态 —— 模块自己的显示偏好，不是用户配置（见 page/UiState.kt）")
        map.forEach { (key, value) -> appendLine("$key=$value") }
    }
}
