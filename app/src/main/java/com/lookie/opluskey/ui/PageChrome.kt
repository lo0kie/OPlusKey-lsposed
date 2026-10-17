package com.lookie.opluskey.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chrisbanes.haze.HazeState

/**
 * 页面「外观 + 磨砂取样」那一域的状态。
 *
 *  为什么单列一个对象：这四样以前摊在 `SettingsPage` 上，分布在六个文件里读写 ——
 *  「磨砂为什么没糊」这类问题要同时看 `pageHazeState`（谁在栈顶）与 `hazeStatesByPage`
 *  （每页登记了没有），摊开时这两个的**对应关系**从类定义上看不出来。收进来之后，
 *  「取样源是谁、谁登记的、降级日志报过没有」是一眼能读完的一件事。
 *
 *  注意：本类**不引用 `SettingsPage`**，只持状态；写它的仍然是那几个扩展函数。
 */
internal class PageChrome {
    /**
     * 系统此刻是不是深色 —— 建页期由 `createView` 定一次，之后由 `SyncToHost` 跟着系统改。
     *
     *  页面的底色、色板、状态栏配色全读它；**不读 Activity 的 `configuration`**（宿主声明了
     *  `configChanges=uiMode`，那份配置会停在旧值），见 `systemNight` 的注释。
     */
    var dark by mutableStateOf(false)

    /**
     * **当前栈顶页**内容那一层的取样状态 —— 顶栏 / 浮层菜单 / 底部工具栏的磨砂都从它取样。
     *
     *  注意：**必须按「谁在栈顶」来选，不能按「谁最后组合」**。旧写法是
     *  `if (pageBackdrop !== backdrop) pageBackdrop = backdrop` —— 最后组合的那一页说了算；
     *  而深度 2 往返还栈时，被 cull 的首页会重新组合、时机晚于栈顶页，于是取样源被它抢走：
     *  浮层菜单取到的是**另一页**的内容（真机：「磨砂处似乎是取样的两个 switch，但菜单底下
     *  并没有 switch」）。选法见 `PageEffects` 里那个 `LaunchedEffect(navStack.lastOrNull())`。
     *
     *  注意：取样方**不能落进它自己取样的那个 `hazeSource`** —— 落进去就是渲染节点链无限增长、
     *  RenderThread 递归到栈溢出（真机崩过一次）。浮层菜单画在 Scaffold 的 `COUIPopupHost` 里
     *（页面那层之外）才是安全的。
     */
    var pageHazeState: HazeState? by mutableStateOf(null)

    /**
     * 每页各一份取样状态（键是页）—— 各页在 `DisposableEffect(s)` 里把自己那份登记进来、并在
     *  `onDispose` 摘掉（见 `PageEntry` 里 `NavDisplay` 的 entry）。页被 cull 后重建会换一份新的
     *  进来，旧的那份随 `onDispose` 出表（它的源节点已 detach）。
     */
    val hazeStatesByPage = mutableMapOf<Screen, HazeState>()

    /**
     * 「取样层没就绪 ⇒ 这次用不透明底」那一条日志的闸 —— 建页期只报一次，免得每个消费方各刷一行。
     * 见 [pageHazeState] 与 `PageMenuPopup` / `CommandToolbar` 里那两处判定。
     */
    var frostDegradeLogged = false
}
