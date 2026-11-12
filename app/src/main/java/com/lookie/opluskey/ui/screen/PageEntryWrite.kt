package com.lookie.opluskey.ui.screen

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.CommonCommand
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.LongPressEdit
import com.lookie.opluskey.core.model.appKeyOf
import com.lookie.opluskey.core.model.trigger
import com.lookie.opluskey.core.model.withLongPress
import com.lookie.opluskey.ui.BindingTarget
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.addStep
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.replaceStep
import com.lookie.opluskey.ui.screen.binding.pickToken
import com.lookie.opluskey.ui.screen.command.branchDraftNow
import com.lookie.opluskey.ui.screen.command.writeBranchCondition
import com.lookie.opluskey.ui.screen.variables.VarManageState
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.state.PickTarget
import com.lookie.opluskey.ui.state.PickedDetail
import com.lookie.opluskey.ui.tickNow
import com.lookie.opluskey.ui.write

// 「挑中的东西怎么落到配置里」—— 与 `PageEntry.kt` 分开：那边是**铺页面**（每页一份滚动行为、
// 顶栏与浮层），这里是「写回」三颗（拼好的 token 写哪儿去 / 应用页那颗 FAB / 界面页确认）。
// 两族改的理由不同，且合在一起时那个文件已经贴着 `TooManyFunctions` 的阈值。

/**
 * 把一个**已经拼好的 token** 写成本次挑应用的取值 —— **不负责退页**。
 *
 *  两页（应用 / 界面）最后都落到这里：应用那页拼 `app:` / `window:`，界面那页还可能拼
 *  `appshortcut:`。拼 token 的地方只剩调用点，这一层只管「写到哪儿去」。
 *
 * @return true = 写下去了；false = 没地方写（手势那条入口没记下来）或被拦
 *   （链路未就绪 / 写盘失败）。调用方据此决定留在原地。
 */
internal fun SettingsPage.writeToken(
    token: String,
    appName: String?,
): Boolean {
    // 「写到哪儿去」只有一处分派：分叉的条件 / 分叉某一支要执行的应用 / 多条命令的某一步 /
    //   手势绑定。
    return when (val target = picker.takePickTarget()) {
        is PickTarget.Branch -> {
            // 条件这一趟**可能多选**：整组包名在 `picker.pickedApps` 里（`takePickTarget()` 不动它，
            //  `clearTarget()` 才清）。空集合说明状态被谁提前清了 —— 退回 token 里那一个包名。
            val pkgs =
                picker.pickedApps.value
                    .toList()
                    .ifEmpty { listOf(appKeyOf(Action.parse(token).appTarget.orEmpty())) }
            // `takePickTarget()` 是**一次性消费**：写失败了得把「为谁挑」放回去，否则这一页
            //  立刻退化成「普通挑应用」—— 点一行会去挑界面、按确定还会写到手势绑定上。
            //  **连条件槽位一起放回**：从「条件 2」那一行进来的这一趟，重挑时必须还写那一条；
            //  只传 index 会让 branchConditionIndex 退回 -1，落到 `old.appConditionIndex(kind)`
            //  或追加，把同一块里另一条条件覆盖掉。**档位（正 / 反）也一起放回** —— 少了它，
            //  写失败重挑一次就悄悄变成「前台应用」。
            if (!writeBranchCondition(pkgs, target.index)) {
                picker.openForBranch(target.index, picker.branchConditionIndex, picker.branchKind)
                false
            } else {
                true
            }
        }

        // 「松手执行额外动作」：写这一档的松手格，然后自己退页（与分支那两条同一条纪律 ——
        //  调用方拿到 true 就不要再补一次 `goBack()`）。写哪儿见 [writeReleaseTo]。
        is PickTarget.Release -> {
            if (writeReleaseTo(target, Action.parse(token))) {
                popBackStack()
                true
            } else {
                //  写失败：把「为谁挑」放回去，留在这一页（否则这一页会退化成普通挑应用）
                picker.openForRelease(combo = target.comboId, sceneAt = target.sceneAt)
                false
            }
        }

        // 「加一步 / 换一步」：`addStep()` / `replaceStep()` 自己会把页面切回「自定义命令」
        // （那才是这次挑应用的来处），所以这条路上调用方**不再退页**。
        //  真加进去了才算写成功：满员时 `addStep` 只 toast、不发车，这里若照样返回 true，
        //  调用方会当作「已落盘」直接 return，刚挑的应用就凭空消失。
        is PickTarget.Step -> {
            if (target.index >= 0) replaceStep(target.index, token) else addStep(token)
        }

        // 「常用命令」那张清单：挑中的应用 / 结束应用追加成新的一条，然后自己退页（同 [PickTarget.Release]
        //  的纪律 —— 调用方拿到 true 就不要再补一次 `goBack()`）。
        PickTarget.CommonCommand -> {
            writeCommonCommand(Action.parse(token))
        }

        // 写的是**进这一页时那个手势**（见 picker.gesture），不是 pager 的当前页
        // —— 这一页不属于 pager。
        null -> {
            val g =
                picker.gesture ?: run {
                    //  **别静默返回**：正常路径由 [openAppPickerForGesture] 先记下手势，但经
                    //  调试通道直接 `navTo(APP_PICKER)` 时没有那一步 —— 那时这一页的确认会
                    //  一声不响地什么都不做（用户看到的就是「FAB 点了没反应」）。
                    XLog.w("选择应用：这一趟没有目标手势（gesture=null），确认无效 —— 请从「按键绑定」页那一格进来")
                    return false
                }
            // 写回**进这一页时那一格**：场景动作页（`picker.sceneAt` 非负）写那一块的这一档，
            //  组合键那一页（`picker.comboId` 非空）写它那一档，否则写当前在编辑的那颗键。
            //  判据只有这两处 —— 不按「当前在哪一页」判：退场动画期间栈顶可能已经变了，
            //  按页面判会把动作写到上一页那颗键上。三选一那一支见 [pickTargetOf]。
            pickToken(
                token,
                pickTargetOf(
                    gesture = g,
                    sceneAt = picker.sceneAt,
                    comboId = picker.comboId,
                    scene = { BindingTarget.Scene(branchDraftNow(), picker.sceneAt, g, branchDraft.comboId) },
                    key = BindingTarget.Key(KeyWatchState.editing(configState.config.value), g),
                ),
                appName,
            )
        }
    }
}

/**
 * 「写回哪一格」的三选一 —— 判据只有 [sceneAt] / [comboId] 两处（见 `writeToken` 里那段说明）。
 *
 *  单独一支是为了让 [writeToken] 留在 detekt 的 `CyclomaticComplexMethod` 之内：那一位是**落点表
 *  的分派器**，每多一个落点必然多一个分支，把三选一的判定也摊在里面就顶线了。这里不碰页面状态
 *  （[scene] / [key] 由调用点现算，用 lambda 只为在走不到时不算 [key]）。
 */
private fun pickTargetOf(
    gesture: GestureTrigger,
    sceneAt: Int,
    comboId: String?,
    scene: () -> BindingTarget,
    key: BindingTarget,
): BindingTarget {
    val combo = comboId?.let { BindingTarget.Combo(it, gesture) }
    return when {
        sceneAt >= 0 -> scene()
        combo != null -> combo
        else -> key
    }
}

/**
 * 「选择应用」页点 FAB —— 把**当前选中**的那一条写下去。
 *
 *  **这是整趟挑应用唯一的落盘点**：二级页点确认只写 [AppPickerState.draft]（草稿），
 *  到这一颗才真正写配置。所以 token 优先取草稿 —— 有草稿说明用户刚在二级页挑过，
 *  照它写 `app:包名/类名` / `appshortcut:`；没有草稿才是「就绑这个应用」（`app:包名`）。
 *
 *  写成了才退页：被拦（链路未就绪）或写盘失败时留在原地，
 *  否则用户挑的东西看起来像存好了，其实一个字节都没写。
 */
internal fun SettingsPage.onAppPicked(
    picked: PickedDetail,
    window: Boolean,
) {
    tickNow()
    // `forStep` 必须在写之前读：`writeToken` 里那个 `takePickTarget()` 会把它清掉
    val forStep = picker.forStep
    // 分叉那两条路同理（条件 / 那一支要执行的应用），而且它们**自己会退页**
    //  （见 `writeBranchCondition`）—— 这里不能再补一次
    //  `goBack()`，否则一次退两层、还会把「放弃未保存的命令」弹出来。
    val forBranch = picker.forBranch
    // 「松手执行额外动作」那一趟同样自己退页（`PickTarget.Release` 分支里 `popBackStack()`），
    //  这里再补一次 `goBack()` 会一次退两层、直接回到首页。
    val forRelease = picker.forRelease
    // 「常用命令」那一趟同理（见 `PickTarget.CommonCommand`）。
    val forCommonCommand = picker.forCommonCommand
    // 「应用控制」→ 结束那一趟：写的是 `proc:stop:包名`（不挑界面、也没有小窗）
    val stopApp = picker.stopApp
    // 刚在「选择界面」页确认过就照那一条写（`app:包名/类名` / `appshortcut:`）；
    // 没确认过就照 `pending` 这一份写 —— 它带着快捷方式的 id，不能退化成 `app:包名/id`。
    //  给「条件执行」挑的那一趟**没有二级页**（不挑界面），所以那边一律按 `pending` 写：
    //  草稿里若留着上一趟的 `包名/类名`，照它写会把条件弄成「某个界面」，而条件只认包名。
    //  给**分支动作**挑的那一趟不在此列 —— 它与网格同一套（草稿 + 小窗都算数）。
    val forCondition = picker.branchTarget != AppPickerState.NOT_FOR_BRANCH
    //  「以小窗口打开」与「使用原版动画」是同一格的两种起法（互斥）：`window` 由调用点带货
    //  （「不支持小窗」那张窗会强制传 false，不走页面状态，所以这里读参数而不是再读一遍），
    //  `stockAnim` 只有页面状态这一个来源。
    val stockAnim = picker.stockAnim.value
    val token =
        when {
            stopApp -> Action.Process(AppTarget.pkg(picked.target)).token
            forCondition -> picked.token(window = false)
            else -> picker.draft?.token(window, stockAnim) ?: picked.token(window, stockAnim)
        }
    // 名字与绑定**同一次写盘**（`Config.appNames`）—— 分两次写要多落一次盘 + 一次广播，
    //  而中间那一下读到的配置只有新绑定、没有名字，顶栏会当场闪一下包名。
    //  快捷方式带的是它自己的名字（「扫一扫」），其余按目标现取应用名。
    val name = picker.draft?.label ?: picked.label ?: appNameOf(picked.target)
    //  **这一笔账在写之前清**：`writeToken` 里那两条分支路径会当场 `popBackStack()` 退回来源页，
    //  来源页的 effect 立刻读 `resumeSheetAt` 把抽屉支起来 —— 写成功后再清已经晚了一帧，
    //  真机上就是「点了确认、抽屉照样弹出来」（用户口径）。
    //  没写成的极小概率（配置只读 / 链路未就绪）会少一次「回来支抽屉」，那也比每次确认都弹好。
    picker.cancelResume()
    if (!writeToken(token, name)) {
        XLog.w("选择应用：${picked.target} 没写进绑定，留在本页（不 goBack）")
        return
    }
    //  「这几趟自己会退页」合成一位：`if` 里串四个 || 就撞上 detekt 的 `ComplexCondition`，
    //  而它们本来就是同一件事（这个 `writeToken` 自己 `popBackStack` 了，调用方别再补 `goBack()`）。
    val popsItself = forBranch || forRelease || forCommonCommand
    if (forStep || popsItself) return
    picker.clearTarget()
    //  「以小窗口打开」/「使用原版动画」也**已经随这一条写下去了**，所以把进页那一刻的基线挪到当下：
    //  不挪的话，`goBack()` 的 `hasUnconfirmedPick` 仍在拿这两个开关与进页基线比（草稿刚被
    //  `clearTarget` 清掉，只剩这一条还能为真）—— 用户拨过开关、再点确认，写盘成功了却当场
    //  弹一张「放弃这次选择」。
    picker.markWindowTogglesBaseline()
    goBack()
}

/**
 * 「松手执行额外动作」写到这一档 —— 三种落点只差「写给谁」，判据在 [pick] 里。
 *
 *  · 场景块（`sceneAt >= 0`）：写**草稿里那一块**，与这一页的三档动作同一个规矩（点「确认」才落盘）；
 *  · 其他：写那颗键 / 那一档组合键的 `longRelease`，当场落盘。
 *
 *  之前这里只有后面那一支（`writeLongReleaseTo`），于是从「场景动作」页进来挑的应用被写进了
 *  **整颗键**的松手格，草稿里那一格还是空的 —— 返回后那一行照样显示「无操作」。
 *
 *  @return 写下去没有（场景块那一支返回「那一块还在不在」；另一支返回「链路未就绪 / 写盘失败」）
 */
private fun SettingsPage.writeReleaseTo(
    pick: PickTarget.Release,
    action: Action,
): Boolean {
    //  「单击后长按」那一档的松手格自成一路（`clickThenLongRelease`）：挑法、清单、回填
    //  与长按的松手共用，只有落点不同 —— 所以只在落点这里分一次
    if (pick.chain) {
        //  场景块走草稿（与 `writeReleaseTo` 的长按那一支同一条纪律：这一页只改草稿）
        if (pick.sceneAt >= 0) {
            val block = BindingTarget.Scene(branchDraftNow(), pick.sceneAt, GestureTrigger.ClickThenLong, pick.comboId)
            val ok = block.bindChainRelease(action = action)
            XLog.w("额外动作：${block.what} ← ${action.token}（草稿）${if (ok) "" else "（那一块已不在草稿里）"}")
            return ok
        }
        val cfg = configState.config.value
        val id = KeyWatchState.editing(cfg)
        val key = cfg.key(id)
        val ok =
            write(
                cfg.copy(keys = cfg.keys + (id to key.copy(clickThenLongRelease = action))),
                "click_then_long_release key=${id.id}",
            ) != CommandsSave.Failed
        XLog.w("额外动作：单击后长按松手 ← ${action.token}（${if (ok) "已落盘" else "没写进去"}）")
        return ok
    }
    if (pick.sceneAt >= 0) {
        val block = BindingTarget.Scene(branchDraftNow(), pick.sceneAt, Gesture.LONG.trigger(), pick.comboId)
        val ok = block.bindRelease(action = action)
        XLog.w("额外动作：${block.what} ← ${action.token}（草稿）${if (ok) "" else "（那一块已不在草稿里）"}")
        return ok
    }
    return writeLongReleaseTo(pick.comboId, action)
}

/**
 * 「松手执行额外动作」写到这一颗键 / 这一档组合 —— 两档**只差写到哪张表**，判据在 `BindingTarget` 里。
 *
 *  之前这里是 `if (comboId != null) writeComboLongRelease(…) else writeLongRelease(…)`：
 *  两个函数、两张表各写一遍同一件事。现在两条路合成一条，都走 `withLongPress`。
 *
 *  @param comboId 这一趟绑的是哪一档组合键（`null` = 当前在编辑的那颗键）
 *  @return 写下去没有（`false` = 链路未就绪 / 写盘失败）
 */
private fun SettingsPage.writeLongReleaseTo(
    comboId: String?,
    action: Action,
): Boolean {
    val cfg = configState.config.value
    val target =
        if (comboId != null) {
            BindingTarget.Combo(comboId, Gesture.LONG.trigger())
        } else {
            BindingTarget.Key(KeyWatchState.editing(cfg), Gesture.LONG.trigger())
        }
    val ok =
        write(target.withLongPress(cfg, LongPressEdit(release = action)), "${target.what}:long_release") !=
            CommandsSave.Failed
    XLog.w("额外动作：${target.what} ← ${action.token}（${if (ok) "已落盘" else "没写进去"}）")
    return ok
}

/**
 * 往「常用命令」那张清单里**追加一条**（见 [PickTarget.CommonCommand]）。
 *
 *  与抽屉里那一支同一个口径：已经在清单里就**不重复收录**，但这一趟仍然算成功 —— 用户点的是
 *  「把它加进来」，它本来就在里面，结果与期待一致（再报一句「已经有了」只会让人以为没生效）。
 *
 *  整份写回（`setCommonCommands` 同一套）：这张表本来就小，按 token 打补丁要额外处理中间态。
 *
 * @return 写下去没有（`false` = 这条动作根本不能执行 / 链路未就绪 / 写盘失败）
 */
private fun SettingsPage.addCommonCommand(action: Action): Boolean {
    //  入口格那种「标记」写法（`appctl:open`）不是能执行的动作，读盘那一道也会把它丢掉
    if (action is Action.None) return false
    val cfg = configState.config.value
    val list = cfg.global.commonCommands
    if (list.any { it.token == action.token }) return true
    val ok =
        write(
            cfg.copy(global = cfg.global.copy(commonCommands = list + CommonCommand(token = action.token))),
            "common_commands=add",
        ) != CommandsSave.Failed
    XLog.w("常用命令：← ${action.token}（${if (ok) "已落盘" else "没写进去"}）")
    return ok
}

/**
 * 「常用命令」那一趟：追加进清单，成功就自己退页（见 [PickTarget.CommonCommand]）。
 *
 *  为什么单独一支：`writeToken` 那个 `when` 的圈复杂度已经贴着 detekt 的线，把「写 + 退页 + 写失败
 *  放回目标」摊在那里会把它顶过去；而这三种结局本来就该在一处读。
 *
 * @return 写下去了没有（`false` = 没地方写 / 这条动作不能执行 / 写盘失败，调用方留在原地）
 */
private fun SettingsPage.writeCommonCommand(action: Action): Boolean {
    if (addCommonCommand(action)) {
        popBackStack()
        return true
    }
    //  写失败：把「为谁挑」放回去，否则这一页会退化成普通挑应用（点一行就绑到手势上）
    picker.openForCommonCommand(stop = picker.stopApp)
    return false
}

/**
 * 「选择界面」页点确认 —— **只记草稿，不写配置**。
 *
 *  这一页是「挑」，落盘在上一级（「选择应用」页那颗 FAB）。所以这里做两件事：
 *   · 把挑中的那一条存进 [AppPickerState.draft]（连同「以小窗口打开」那一档，等提交时再拼 token）；
 *   · 退回一级页，并把那一页的选中也挪过来 —— 行尾图标要立刻显示成「一块屏」。
 *
 *  用户在这之后按返回走人，配置一个字节都不会动（草稿随这一趟结束一起作废，
 *  见 [AppPickerState.clearTarget]）。
 */
internal fun SettingsPage.onDraftPicked(
    target: String,
    shortcutId: String?,
    label: String?,
) {
    tickNow()
    val detail = PickedDetail(target, shortcutId, label)
    // 「这一条吃不到原版动画」不在这里判：开关该不该拨回关要到**上一级那颗 FAB** 才说得准
    //  （那一处与小窗共用一份判据，见 `AppConfirmFab.blockedToggleOf`）。
    // 草稿与「选中位」装同一份：行尾靠它的 shortcutId 画 table-shortcut，FAB 靠它取目标
    picker.draft = detail
    appPickerUi.pending = detail
    goBack()
}

/**
 * 网格里点「自定义变量」那一格 → 进「自定义变量」列表页（**为这一格**挑一步）。
 *
 *  与 `openAppPickerForTarget` 一样先记下「为谁挑」，但**不碰挑应用那几笔账**
 *  （搜索词 / 小窗口 / 列表选中复位）—— 那些只属于那一页。
 *
 *  变量只有**那一个页面**：两趟入口的区别只写进 `VarManageState.enter`（这一趟是「挑一步」），
 *  点列表里的一颗才会弹那张操作窗，挑完落成一步。
 */
internal fun SettingsPage.openVarOpsForTarget(target: BindingTarget) {
    picker.openForGesture(target.gesture, (target as? BindingTarget.Combo)?.id, sceneAt = target.sceneAt)
    VarManageState.enter(VarManageState.Mode.Step)
    navTo(Screen.VARIABLES)
}

/**
 * 「添加 / 选择命令」抽屉里点「自定义变量」那一行 → 进同一页，挑完**落成一步**
 * （[replaceIndex] `< 0` = 追加一步，否则换掉那一步）。
 */
internal fun SettingsPage.openVarOpsForStep(replaceIndex: Int) {
    picker.openForStep(replaceIndex)
    VarManageState.enter(VarManageState.Mode.Step)
    navTo(Screen.VARIABLES)
}
