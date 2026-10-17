package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config

/**
 * 「配置与命令库」那一域的状态 —— 主配置、命令库，以及它们各自的**只读位**。
 *
 *  为什么这两份文件同属一域：它们同生共死 —— 都在建页那次一起读、都由 ⋮ 菜单的
 *  「清空配置」一起重建、都由「导入 / 导出配置」一起换掉；两个只读位也是同一条纪律
 *  （读不懂就整份拒写，绝不拿默认值覆盖用户的东西）。
 */
internal class ConfigState {
    /**
     * 建页那次读盘**回来了没有** —— 页面里的内容一律等这一位翻 true 才组合。
     *
     *  为什么要有这一位：[config] / [commands] 的初值都是「安全的默认值」（主开关关、
     *  三个手势全是无操作、命令库空），而真正的值要读三份文件才拿得到。那三趟读以前是
     *  `SettingsPage.createView()` 里**同步**做的 —— 跑在宿主 Activity 的主线程上。
     *  现在挪到组合期、在 IO 线程上跑（见 `ui/PageLoad.kt` 的 `loadPageState`），于是
     *  「数据还没到」这一档真实存在了：不拦住的话首帧会先按默认值把「模块未开、全无操作」
     *  的界面画出来，几十毫秒后再翻成真值 —— 用户看到的是开关自己弹一下。
     *
     *  所以 [SettingsPage.Page] 在 false 时**整棵页面树都不组合**（只铺一屏底色），
     *  副作用也就一个都不会跑（接收器注册、链路自检、换页收浮窗）。
     *
     *  另一位用途是写闸：这一位为 false 时 [readOnly] / [commandsReadOnly] 还是初值 false，
     *  而 [config] 只是默认值 —— 这时候任何写回都会把用户的文件覆盖成默认值。
     */
    var loaded by mutableStateOf(false)

    /** 当前配置（页面自己读自己写）。 */
    val config = mutableStateOf(Config.DEFAULT)

    /**
     * 建页那次**没读成功**（文件在、但读不了 / 读不懂）⇒ 页面进**只读**：任何写回都被拒。
     *
     * 为什么要有这个开关：默认值长这样 —— 主开关**关**、三个手势全是「无操作」、**不振动**。
     * 读失败时若照常拿它渲染、又照常写回，用户配的东西就被整份替换成这份默认值
     *（真机上真的发生过，先丢的是「振动反馈」）。
     *
     * 退出方式：**重进页面**（会重读一次）；或走 ⋮ 菜单的「默认界面」离开；或点「清空配置」
     * ——那是用户明说要丢掉重来，`ConfigWrite.resetAll` 会先把这一位清掉再写。
     *
     *  全项目只有两处写它：建页按读到的那一态赋值、以及 `resetAll` 的放行 —— 别在别处顺手置 false，
     *  那正是这道闸要防的事。
     */
    var readOnly by mutableStateOf(false)

    /**
     * 命令库的只读位 —— 与 [readOnly] 同一条纪律，管的是**另一份文件**（`CommandLibrary`）。
     *
     *  建页那次读命令库读不懂（外皮解不开 / 被截断 / 正文不是合法 JSON）时置 true：手上那份空表
     *  不是用户的真相，`saveCommands` 一律拒写。少这一位的话，用户随手改一条命令就会把「读不懂」
     *  的那份文件覆盖成只有这一条 —— 用户的命令没了，而且没有任何一步提示过他。
     */
    var commandsReadOnly by mutableStateOf(false)

    /**
     * **自定义命令库**（`core/config/CommandLibrary` 里那一串）—— 界面自己的状态，与 [config] 分开。
     *
     *  命令的**内容**存在命令库里，绑定到手势时写进主配置的只是 `cmd:<名字>` 这个引用
     *（与别的动作一样只是 `plus_single` 里的一个取值，注入侧不需要知道名字背后是什么）。
     *  这份列表变了要重组的是 `ActionCatalog.forGesture` 的调用点（图标表与绑定列表）。
     */
    val commands = mutableStateOf<List<Command>>(emptyList())
}
