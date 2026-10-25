package com.lookie.opluskey.ui.screen

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.addStep
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.replaceStep
import com.lookie.opluskey.ui.screen.binding.BindingTarget
import com.lookie.opluskey.ui.screen.binding.pick
import com.lookie.opluskey.ui.screen.command.writeBranchCommand
import com.lookie.opluskey.ui.screen.command.writeBranchCondition
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.state.PickTarget
import com.lookie.opluskey.ui.state.PickedDetail
import com.lookie.opluskey.ui.tickNow

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
                    .ifEmpty { listOf(AppTarget.pkg(Action.parse(token).appTarget.orEmpty())) }
            // `takePickTarget()` 是**一次性消费**：写失败了得把「为谁挑」放回去，否则这一页
            //  立刻退化成「普通挑应用」—— 点一行会去挑界面、按确定还会写到手势绑定上。
            if (!writeBranchCondition(pkgs, target.index)) {
                picker.openForBranch(target.index)
                false
            } else {
                true
            }
        }

        is PickTarget.BranchCommand -> {
            if (!writeBranchCommand(token, target.index)) {
                picker.openForBranch(target.index, forCommand = true)
                false
            } else {
                true
            }
        }

        // 「加一步 / 换一步」：`addStep()` / `replaceStep()` 自己会把页面切回「自定义命令」
        // （那才是这次挑应用的来处），所以这条路上调用方**不再退页**。
        //  真加进去了才算写成功：满员时 `addStep` 只 toast、不发车，这里若照样返回 true，
        //  调用方会当作「已落盘」直接 return，刚挑的应用就凭空消失。
        is PickTarget.Step -> {
            if (target.index >= 0) replaceStep(target.index, token) else addStep(token)
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
            pick(
                // 只为把取值交给 `pick`（它读的是 token），name 这一位在这条路上不参与显示。
                ActionCatalog.Spec(name = token, token = token),
                // 写回**进这一页时那一格**：组合键那一页（`picker.comboId` 非空）写它那一档，
                //  否则写当前在编辑的那颗键。判据只有 `comboId` 一处 —— 不按「当前在哪一页」判：
                //  退场动画期间栈顶可能已经变了，按页面判会把动作写到上一页那颗键上。
                picker.comboId
                    ?.let { BindingTarget.Combo(it, g) }
                    ?: BindingTarget.Key(KeyWatchState.editing(configState.config.value), g),
                appName,
            )
        }
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
    //  （见 `writeBranchCondition` / `writeBranchCommand`）—— 这里不能再补一次
    //  `goBack()`，否则一次退两层、还会把「放弃未保存的命令」弹出来。
    val forBranch = picker.forBranch
    // 刚在「选择界面」页确认过就照那一条写（`app:包名/类名` / `appshortcut:`）；
    // 没确认过就照 `pending` 这一份写 —— 它带着快捷方式的 id，不能退化成 `app:包名/id`。
    //  给「条件执行」挑的那一趟**没有二级页**（不挑界面），所以那边一律按 `pending` 写：
    //  草稿里若留着上一趟的 `包名/类名`，照它写会把条件弄成「某个界面」，而条件只认包名。
    val token =
        if (forBranch) {
            picked.token(window = false)
        } else {
            picker.draft?.token(picker.smallWindow.value) ?: picked.token(window)
        }
    // 名字与绑定**同一次写盘**（`Config.appNames`）—— 分两次写要多落一次盘 + 一次广播，
    //  而中间那一下读到的配置只有新绑定、没有名字，顶栏会当场闪一下包名。
    //  快捷方式带的是它自己的名字（「扫一扫」），其余按目标现取应用名。
    val name = picker.draft?.label ?: picked.label ?: appNameOf(picked.target)
    if (!writeToken(token, name)) {
        XLog.w("选择应用：${picked.target} 没写进绑定，留在本页（不 goBack）")
        return
    }
    //  **写成了就不再恢复抽屉**：抽屉那边记着「挑完回来把抽屉支起来」，那是**还没选完**时的账。
    //  这一笔已经落盘（抽屉里现在是「选中即确认」），回去再把抽屉弹出来等于让人对着同一条命令
    //  再选一遍（用户口径「从应用组件确认后返回，抽屉会显示出来」）。
    //  没写成 / 中途退出时不清 —— 那时抽屉还是用户没走完的那一步，回去要原样支起来。
    picker.cancelResume()
    if (forStep || forBranch) return
    picker.clearTarget()
    goBack()
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
    // 草稿与「选中位」装同一份：行尾靠它的 shortcutId 画 table-shortcut，FAB 靠它取目标
    picker.draft = detail
    appPickerUi.pending = detail
    goBack()
}
