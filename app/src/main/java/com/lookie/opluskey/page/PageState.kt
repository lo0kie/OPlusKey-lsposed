package com.lookie.opluskey.page

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Command
import com.lookie.opluskey.config.CommandKind

/**
 * 页面用到的两个状态容器：`CommandDraft` 是纯表单模型，`FallbackOwners` 是宿主不给 owner
 * 时的兜底实现。两者都不引用外层类的任何东西，从 `page/SettingsPage` 原样拆出（去掉 `inner`）。
 */

/**
 * 「自定义命令」页正在编辑的那条命令（草稿）。
 *
 * 用 Compose 的 `mutableStateOf` / `mutableStateListOf` 存，所以表单是「边输边存」的：
 * push 到「添加命令」那一级再回来，名称 / 类型 / 已经挑好的步骤都还在。
 */
internal class CommandDraft {
    var kind by mutableStateOf(CommandKind.SINGLE)
    var name by mutableStateOf("")
    var single by mutableStateOf("")
    val steps = mutableStateListOf<String>()

    /**
     * 跨进程的步骤（最近任务 / 通知栏 / shell）落地要几百毫秒，不等待就会
     * 「第 3 步打在概览还没起来的桌面上」。
     */
    var stepDelayMs by mutableStateOf(Command.DEFAULT_STEP_DELAY_MS)

    /**
     * 「点击已保存的命令时可编辑」—— 加载后记着原名，UI 据此把卡片标题 / 提交按钮切成
     * 「修改命令 / 修改」；`addCommand` 据此判「名字改没改」（改了 = 真替换，没改 = 原地覆盖）。
     */
    var editingName by mutableStateOf<String?>(null)

    /** 保存成功、或从菜单重新进来时清空。 */
    fun reset() {
        kind = CommandKind.SINGLE
        name = ""
        single = ""
        steps.clear()
        stepDelayMs = Command.DEFAULT_STEP_DELAY_MS
        editingName = null
    }

    /** 把已保存的 [cmd] 灌进表单，进入编辑态。 */
    fun load(cmd: Command) {
        kind = cmd.kind
        name = cmd.name
        single = cmd.single
        steps.clear()
        steps.addAll(cmd.steps)
        stepDelayMs = cmd.stepDelayMs
        editingName = cmd.name
    }
}

/**
 * 宿主不提供时，我们自建的那套 ViewTree owner。
 *
 * 三件事必须做对，否则 Compose 在 attach 时抛异常（真机三轮踩出来的）：
 *  1. `LifecycleRegistry` 要推到 `RESUMED` —— 停在 `INITIALIZED` 时 `WindowRecomposer`
 *     认为窗口不可见，组合不跑（页面一片空白）；
 *  2. `SavedStateRegistryController.performAttach()` + `performRestore(null)` 必须在
 *     推进 lifecycle **之前**完成，否则抛
 *     `IllegalStateException: You must call performAttach() before calling performRestore()`；
 *  3. **绝不推进到 `DESTROYED`** —— Compose 的 `Recomposer` 收到就永久取消。
 *
 * `isUsable = false` 的那一份是「宿主三件套齐全、其实用不上我们」的占位实例 ——
 * 调用方拿它去填 `pendingOwners` 的引用，行为与「没有兜底」一致。
 */
internal class FallbackOwners(val isUsable: Boolean) :
    androidx.lifecycle.LifecycleOwner,
    androidx.lifecycle.ViewModelStoreOwner,
    androidx.savedstate.SavedStateRegistryOwner {

    private val registry = androidx.lifecycle.LifecycleRegistry(this)

    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry

    override val viewModelStore: androidx.lifecycle.ViewModelStore = androidx.lifecycle.ViewModelStore()

    private val savedController = androidx.savedstate.SavedStateRegistryController.create(this)

    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedController.savedStateRegistry

    init {
        if (isUsable) {
            try {
                savedController.performAttach()
                savedController.performRestore(null)
            } catch (e: Throwable) {
                XLog.w("SavedStateRegistry 初始化失败（Compose 可能起不来）", e)
            }
            try {
                registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
            } catch (e: Throwable) {
                XLog.w("推进兜底 Lifecycle 到 RESUMED 失败", e)
            }
        }
    }
}
