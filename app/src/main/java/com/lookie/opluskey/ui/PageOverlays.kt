package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command

/**
 * 设置页上**所有盖在页面之上的东西**（`⋮` 菜单、各类确认窗、浮层）那一域。
 *
 *  ## 为什么是一组
 *
 *  这五样原来摊在 [SettingsPage] 上，看上去各是各的（两个开关、一个待确认项、一个计数、一张
 *  登记表）。它们其实说的是同一件事：**「此刻页面上有东西盖着」以及「怎么把它收掉」**。
 *  摊开时那条对应关系在类定义上看不出来 —— 比如「浮层开着时系统返回要整个吞掉」需要同时知道
 *  [openCount] 与 [closers]，而它们隔着一百多行。
 *
 *  ## 三样「问一句」的开关
 *
 *  [confirmReset] / [confirmLeaveCommands] / [pendingDraftLoad] 都是「要不要弹一张确认窗」，
 *  但问的是三件不同的事，**不能合并**：
 *   · [confirmReset] —— 清空配置（由 `⋮` 菜单或调试通道置位）；
 *   · [confirmLeaveCommands] —— 退不退页（表单改过还没保存时由 [SettingsPage.goBack] 置位）；
 *   · [pendingDraftLoad] —— 换不换内容（要载入另一条命令时由 [SettingsPage.loadDraft] 置位，
 *     存的是**待载入的那一条**，不是布尔）。
 *
 *  后两者必须分开：一个是「退页」，一个是「覆盖表单」，同一个开关会让「我要换命令」被当成
 *  「我要退出」。
 *
 *  ## 为什么这个类不引用 [SettingsPage]
 *
 *  它只持状态与「怎么收起」的登记表，判据（要不要接管返回）留在页面那边 —— 那几条要看
 *  `navStack` 与草稿是否脏，属于页面自己的账。所以这一层可以独立描述，不是把一个大对象
 *  拆成一个小对象。
 */
internal class PageOverlays {
    /**
     * 此刻开着几个浮层 —— [Closer] 在组合期维护，读它的有两处：
     * 预测返回的 `onBackStarted`（决定接不接管）与自己那个 handler。
     *
     *  用计数而不是布尔：浮层可以叠（菜单上再弹一层确认），布尔会让「收掉一层」直接归零。
     */
    val openCount = mutableIntStateOf(0)

    /**
     * 页面里**所有浮窗**的「统一收起」通道，只存「怎么把我关掉」、不存「谁开着」。
     *
     *  关一个本来就关着的浮窗是一次**无副作用的空写**（`mutableStateOf` 写同值不触发重组），
     *  所以收的时候不必先判在不在。返回值 = 这一层刚才**真的开着**（只用来把日志写准）。
     */
    val closers = mutableListOf<() -> Boolean>()

    /** 「清空配置」确认弹窗（调试通道经 `action=CONFIRM_RESET` 置 true 弹出）。 */
    var confirmReset by mutableStateOf(false)

    /**
     * 「表单里有没保存的改动，但用户要退出这一页」—— 待确认。
     * 触发点是 [SettingsPage.goBack]（草稿 `dirty` 时置位并**拦住不退**），窗由 `CommandScreen` 画。
     */
    var confirmLeaveCommands by mutableStateOf(false)

    /**
     * 「表单里有没保存的改动，但用户要换成另一条命令」—— 待确认的那一条（`null` = 没在弹）。
     *
     *  触发点是 [SettingsPage.loadDraft]（草稿脏时置位、**先问一句**），窗由 `CommandScreen` 画。
     *  与 [confirmLeaveCommands] 分开：那个问的是「退不退页」，这个问的是「换不换内容」。
     */
    var pendingDraftLoad by mutableStateOf<Command?>(null)

    /** 有浮层开着吗 —— 开着时系统返回**整个吞掉**（见 `BackInterception.handleDiscreteBack`）。 */
    fun hasOpen(): Boolean = openCount.intValue > 0

    /**
     * 页面离开时**主动收掉所有浮窗**。
     *
     * 为什么必须「主动」：COUI 的 `OverlayListPopup` / `OverlayDialog` 都渲染在
     * **根 Scaffold 的 `popupHost`** 那一层（见库的 `Scaffold` 文档），它**不属于任何一页**
     * —— 既不跟着页面转场走，也不随「那一页出栈」以外的任何事消失；而首页顶栏那颗菜单
     * 更是**永远在栈里**（首页常驻），它的浮窗状态永远不会被组合释放。于是不主动收的话：
     * 从「设置」重进、切后台回来，菜单还开着；换页时浮窗还会**钉在屏幕上不动**，
     * 等那一页出栈才跟着消失。
     *
     * 触发点（都在「页面离开」这条需求路径上）：
     *  · 换页 / 清栈回首页 —— `PageContent` 里那个看着 `screen` 的 `LaunchedEffect`；
     *  · 从设置重进模块（栈顶未变时 LaunchedEffect 不触发）—— [SettingsPage.onHostReentered]；
     *  · 顶栏返回键在首页（关页面）—— `PageHeader`；
     *  · 露出宿主原版界面 / 系统返回关页面 / 宿主 Activity `onStop` —— `SettingsPageHooks`。
     *
     * @param reason 只进日志，便于真机上核对「是哪条路把它收掉的」
     */
    fun closeAll(reason: String) {
        if (closers.isEmpty()) return
        // 复制一份再遍历：关闭过程中会触发重组，进而增删登记表
        val snapshot = closers.toList()
        val closed = snapshot.count { runCatching { it() }.getOrDefault(false) }
        // 一层都没开着时不写日志：页面离开是高频动作，每次都留一行「收掉 0 个」只会淹掉别的
        if (closed > 0) XLog.w("页面离开（$reason）：已收起 $closed 个浮窗")
    }

    /**
     * 浮窗在**组合期**把自己登记进 [closers] —— 各浮窗调用点只需要加一行：
     * `overlays.Closer(show = open) { open = false }`。
     *
     * 登记时机是「宿主组件进组合」，**不是**「浮窗弹出来」：`show` 只是浮窗自己的一个参数，
     * 组件一直挂着（`show = false` 时只是不渲染内容）—— 所以收起时那一下写的是「本来就该是
     * 的值」，不判空也不会误伤。
     *
     * `rememberUpdatedState` 是为了拿到**最新**的 `show` / `onDismiss`：那些值每次重组都会变，
     * 而注册只在第一次组合时做一次。
     *
     * @param show 这一层此刻开着没有 —— 只决定「这一层算不算被收掉了一个」（日志用）
     */
    @Composable
    fun Closer(
        show: Boolean,
        onDismiss: () -> Unit,
    ) {
        val latestShow by rememberUpdatedState(show)
        val latestDismiss by rememberUpdatedState(onDismiss)
        // 「有没有浮层开着」的计数 —— 预测返回的 onBackStarted 要用它决定接不接管
        // （见 [SettingsPage.wouldConsumeBack]）。键是 `show`：翻转时旧的 effect 收尾、新的进来，
        // 一次翻转恰好各加减一次；组件出组合时若还开着也会补减。
        DisposableEffect(show) {
            if (show) openCount.intValue++
            onDispose { if (show) openCount.intValue-- }
        }
        DisposableEffect(Unit) {
            val closer = {
                if (latestShow) {
                    latestDismiss()
                    true
                } else {
                    false
                }
            }
            closers += closer
            onDispose { closers -= closer }
        }
    }
}
