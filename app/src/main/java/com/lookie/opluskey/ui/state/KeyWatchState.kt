package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId

/**
 * 「现在在编辑哪一颗键」—— **纯界面状态**，不落盘。
 *
 * ## 它只剩这一件事了
 *
 *  自定义按键的清单本身（有哪些键、各叫什么、绑了什么）现在是**配置的一部分**
 *  （`Config.customKeys` + `Config.keys`），由 `ConfigKeys.kt` 那几个扩展函数增删改、
 *  由 `SettingsPage.write` 落盘并广播。早先这里存着一份 `mutableStateListOf`，是「还没接线」
 *  时的临时落脚点：进程一死清单就没了，注入侧也根本不知道有这些键。
 *
 *  留在这里的只有「用户在菜单里点中了哪一颗」—— 它只在**这一趟停留**里有效：
 *  关页（`SettingsPageHooks.clearPageRefs`）与从设置重进（`SettingsPage.onHostReentered`）
 *  都会把它收回侧键。写进配置反而是错的。
 *
 * ## 为什么存 id 而不是 `KeyId` 对象
 *
 *  改名会换一个 `KeyId` 实例（身份不变）。存对象的话，改完名 `selected` 还指着旧实例，
 *  菜单里那个对勾会落到一颗「已经不存在的键」上。存 id、每次从配置现解析，就没有这个缝。
 */
internal object KeyWatchState {
    /** 当前在编辑哪一颗键的 id；`null` = 侧键。 */
    var selectedId by mutableStateOf<String?>(null)
        private set

    /** 在菜单里点了某一组（`null` = 侧键）。 */
    fun select(id: String?) {
        selectedId = id
    }

    /**
     * 当前在编辑的那颗键 —— 从 [config] 现解析。
     *
     *  配置里找不到（那颗键刚被删掉）就退回侧键：宁可让用户看到侧键的绑定，
     *  也不要让页面指着一颗不存在的键去读写。
     */
    fun editing(config: Config): KeyId = selectedId?.let { config.keyIdOf(it) } ?: KeyId.PLUS

    /** 选中的键被删掉之后，把选中态收回侧键（删除那条路调用）。 */
    fun forget(id: String) {
        if (selectedId == id) selectedId = null
    }
}
