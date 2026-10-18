package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

/**
 * 收集到的一颗自定义按键：名字与键码。
 *
 *  [code] 是唯一标识（同一个键按两次只算一颗），[name] 只负责显示。
 *
 * ## [name] 是**存下来的字面量**，不是每次现算的
 *
 *  收集那一刻按当时的语言拼出「按键 1」/「Key 1」并**存进这个字段**；之后切语言，
 *  已有的名字**不跟着变** —— 这份清单将来要落盘，它是用户的东西，不是界面文案。
 *  跟着 i18n 变的只有菜单里的那些固定字（小标题、「添加按键」这类）。
 */
internal data class WatchedKey(
    val name: String,
    val code: Int,
) {
    /** 菜单里那一行的文字 —— 就是存下来的名字。 */
    val label: String get() = name
}

/**
 * 「自定义按键」那份清单 —— 收进来了哪几颗、当前停在哪一颗。
 *
 * ## 它现在是进程级的单例
 *
 *  写入方有两处：收集弹窗（`CollectKeyDialog`）与**它自己注册的接收器**（注入侧报回来的键）。
 *  接收器活着的这段时间正是弹窗开着的时候，两处读写同一份即可。
 *
 *  代价是「关掉页面后清单还在」—— 这正是现在想要的：菜单里那几行要一直看得到。
 *  它**还不落盘**：一颗键要能各自绑定，得先把 `KeyId` 从枚举改成动态、配置按键存、
 *  注入侧按收集到的键码认领；这三步没做完之前写进配置文件只会存下一份谁也读不懂的清单。
 *
 * ## [selected] 为空 = 停在**侧键**那一组
 *
 *  菜单第一行永远是侧键（`null`），其余是收集到的键。删除只作用在这里选中的那一颗 ——
 *  侧键不能被删，所以它选中时「删除按键」是灰的。
 */
internal object KeyWatchState {
    /** 已经收集到的键（按收集顺序）。 */
    val added = mutableStateListOf<WatchedKey>()

    /** 当前停在哪一颗；`null` = 侧键。 */
    var selected by mutableStateOf<WatchedKey?>(null)
        private set

    /**
     * 默认名字的序号 —— **只增不减**。
     *
     *  用「第几颗」而不是 `added.size + 1`：删掉中间那颗再收一颗，后者会撞上已经存在的名字。
     */
    private var seq = 0

    /**
     * 注入侧报回来一颗键 —— 收进清单并**立刻选中它**。
     *
     *  收集弹窗的语义就是「我刚按下的这一颗」，所以不再要求用户按第二下「添加」；
     *  已经收过的键重复按只把它选中（同一个键按两次不该出现两行）。
     *
     *  名字在这里一次定下（见 [WatchedKey] 的说明）：注入侧报上来的
     *  `KEYCODE_VOLUME_UP` 这种串不给用户看，按「按键 N」编一个更好认的名字。
     *
     * @return 这一颗是不是新收进来的
     */
    fun collect(code: Int): Boolean {
        if (code <= 0) return false
        val existing = added.firstOrNull { it.code == code }
        if (existing != null) {
            selected = existing
            return false
        }
        seq += 1
        val key =
            WatchedKey(
                name = I18n.get(Strings.BINDING_CUSTOM_KEY_NAME, "按键 %d").format(seq),
                code = code,
            )
        added.add(key)
        selected = key
        return true
    }

    /** 菜单里点了某一组（`null` = 侧键）。 */
    fun select(key: WatchedKey?) {
        selected = key
    }

    /**
     * 给某一颗改名 —— 空名字直接忽略（改名弹窗的「确定」在这种情况下本来就是灰的）。
     *
     *  改的是**存下来的那个字段**，所以切界面语言不会跟着变；同时把 [selected] 指向新对象，
     *  否则菜单里那一行的对勾会落在一个已经不存在的旧实例上。
     */
    fun rename(
        key: WatchedKey,
        name: String,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val index = added.indexOfFirst { it.code == key.code }
        if (index < 0) return
        if (added[index].name == trimmed) return
        val updated = added[index].copy(name = trimmed)
        added[index] = updated
        if (selected?.code == key.code) selected = updated
    }

    /** 删掉当前选中的那一颗；选中的是侧键时什么都不做（那颗按钮本来就是灰的）。 */
    fun removeSelected() {
        val key = selected ?: return
        added.removeAll { it.code == key.code }
        selected = null
    }
}
