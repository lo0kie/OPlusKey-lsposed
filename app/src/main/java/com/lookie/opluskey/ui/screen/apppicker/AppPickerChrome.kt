package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageFabButton
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.rememberTick
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.target.LaunchableApp
import com.lookie.opluskey.ui.state.picker.AppPickerUiState
import com.lookie.opluskey.ui.state.picker.PickedDetail
import com.lookie.opluskey.ui.state.picker.PickerToggle
import com.lookie.opluskey.ui.state.picker.ToggleBlock
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「选择应用」页那三块**浮在列表上**的东西：两句空态 / 转圈、那句话「为什么一条都没有」，
// 以及右下角那颗确认键。
//
// 两块空态是 `BoxScope.` 扩展（对齐靠 `.align(...)`），页面侧那点东西（文案）由调用点算好传进来；
// `emptyHint` 要在 `SettingsPage` 上算，确认键要读页面状态，所以这两支都是 `SettingsPage.` 扩展。

/**
 * 「查得到但没有匹配项 / 一条都没查到」那一句 + 一颗字形。
 *
 *  @param appsEmpty 清单本身是空的（而不是被搜索词筛没了）—— 决定用哪颗字形：
 *   后一颗不用划掉的放大镜（那道斜杠与「无操作」那颗 `Ban` 长得像，摆这儿读起来像「禁止搜索」）。
 */
@Composable
internal fun BoxScope.PickListEmptyState(
    visible: Boolean,
    topInset: Dp,
    appsEmpty: Boolean,
    text: String,
) {
    PageStatePlaceholder(
        visible = visible,
        topInset = topInset,
        glyph = { tint ->
            Icon(
                imageVector = if (appsEmpty) PageIcons.EmptyApps else PageIcons.NoMatch,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = tint,
            )
        },
        text = text,
    )
}

/**
 * 列表还在查 —— ColorOS 默认那颗转圈 + 一句话。
 *
 *  两级页面（「选择应用」/「选择界面」）共用这一支：转圈那颗、摆位、让开顶栏那一档全在这里，
 *  只有那句话各说各的（一级说「应用」、二级说「应用组件」）—— 用户在两级之间来回跳，
 *  同一个加载态换一张脸只会让人以为这一页卡住了。
 *
 *  所以 [text] 不给默认值：默认值会让新调用点悄悄拿到另一个页面的文案。
 */
@Composable
internal fun BoxScope.PickListLoadingState(
    visible: Boolean,
    topInset: Dp,
    text: String,
) {
    PageStatePlaceholder(
        visible = visible,
        topInset = topInset,
        text = text,
        //  转圈本身收在 [PageStateSpinner]（「选择界面」页也用同一颗 —— 两处各写一遍时
        //  那三个数的缩放迟早有一处只换了 `size`，见那边的注释）。
        glyph = { tint -> PageStateSpinner(tint) },
    )
}

/**
 * 列表**没读出来** —— 与「正在加载」并列的第二档占位。
 *
 *  为什么必须有它：列表只在 `loaded != null` 时铺（见 `AppPickerList`），所以「查失败、`loaded`
 *  仍是 null」这一档如果什么都不画，整页就是**一片空白** —— 连一句「没读出来」都看不到，用户只能
 *  猜。文案与 `emptyHint` 里那一档同一句（同一个原因，不该有两种说法）。
 */
@Composable
internal fun BoxScope.PickListFailedState(
    visible: Boolean,
    topInset: Dp,
) {
    PageStatePlaceholder(
        visible = visible,
        topInset = topInset,
        text = I18n.get(Strings.APP_PICKER_LOAD_FAILED, "列表没读出来，退回再进一次"),
        glyph = { tint ->
            Icon(
                PageIcons.EmptyApps,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = tint,
            )
        },
    )
}

/**
 * 空态那句话 —— 按「为什么一条都没有」分几档（真因优先：**没查出来**不是「一个都没有」）。
 *
 *  注意：这一句用的是**页面级**的开关与搜索词，所以放在 `SettingsPage.` 扩展里算，
 *  而不是让空态组件自己取。
 *
 *  「清单里有东西、但一条都不显示」有两档原因（见 `AppPickerUiState.shown` 的两条过滤）：
 *  点不开的被「显示失效应用」挡着、没有启动图标的被「显示系统应用」挡着 —— 说清是哪一档，
 *  别让人去猜（两档都挡着时先说「显示失效应用」那一档，那是用户更常撞上的）。
 */
internal fun SettingsPage.emptyHint(ui: AppPickerUiState): String {
    val apps = ui.apps
    //  两条判据各管一维、互不借光（见 `AppPickerState.includesIconless`）
    val includesIconless = picker.includesIconless
    val includesUnopenable = picker.includesUnopenable
    return when {
        // 真因优先：这次是**没查出来**，不是「一个都没有」（后者是有效结论）
        ui.failed && apps.isEmpty() -> {
            I18n.get(Strings.APP_PICKER_LOAD_FAILED, "列表没读出来，退回再进一次")
        }

        apps.isEmpty() -> {
            // 不再说「系统限制了本页可见的应用列表」：那个前提不成立，
            // 这一页的包可见性跟着宿主走，真列不出来只会是别的原因。
            I18n.get(Strings.APP_PICKER_EMPTY_OPENABLE, "没有找到应用")
        }

        // 搜不到就只说「没匹配上」—— **不复述搜索词**：那一串字就写在上面的搜索框里，
        //  空态再念一遍只是把同一件事说两次，长词还会在窄屏上折成两行。
        picker.filter.value.isNotBlank() -> {
            I18n.get(Strings.APP_PICKER_NO_SEARCH_RESULT, "没有匹配的应用")
        }

        // 被「显示失效应用」挡着：确实有打不开的行，而且它们本来够得着显示
        !includesUnopenable && apps.any { !it.launchable && (includesIconless || it.byDefault) } -> {
            I18n.get(Strings.APP_PICKER_EMPTY_HINT_BROKEN, "没有能打开的；开 [显示失效应用] 看看")
        }

        // 剩下的空只可能是「没有启动图标」那一维挡着（另一维已放行）
        !includesIconless -> {
            I18n.get(Strings.APP_PICKER_EMPTY_HINT_ICONLESS, "一条都没有；开 [显示系统应用] 看看")
        }

        // 两维都放行了却一条不剩：手里那些行**既没图标又打不开**，少开哪一个都出不来
        else -> {
            I18n.get(Strings.APP_PICKER_EMPTY_HINT_ICONLESS, "一条都没有；开 [显示系统应用] 看看")
        }
    }
}

/**
 * 右下角那颗「确定选择」—— 本页**唯一的写入入口**。
 *
 *  点行只进下一级去挑界面（**不写任何东西**），要「就绑这个应用、不挑界面」就走这一颗；
 *  二级页那颗 FAB 是另一个入口（挑到具体界面 / 快捷方式）。**点 FAB 才走确认流程**：
 *
 *  1. 拉不起来的应用（被停用 / 非导出入口 / 装了一半）先拦一句，**什么都不写**；
 *  2. 开着的那个开关**这一次用不了** ⇒ 拦一句（[blockedToggleOf]）：小窗是点这一下问一次框架
 *     （全流程只问这一次），原版动画按目标形态直接判。免得写一个执行时必弹 toast 的 `window:`，
 *     或者写一个根本吃不到那个动画的 `app:a:`；
 *  3. 都过了才 `onPick(pkg, 小窗开关)`。
 *
 *  样式（尺寸 / 形状 / 阴影 / 容器色）全走 [PageFabButton] 那一份 —— 与「按键绑定」页那颗共用。
 *  图标 tint 显式给 `onPrimary`：库内部只给容器色、不给 contentColor。
 */
@Composable
internal fun SettingsPage.AppConfirmFab(
    ui: AppPickerUiState,
    onPick: (PickedDetail, Boolean) -> Unit,
) {
    val tick = rememberTick()
    PageFabButton(
        onClick = {
            tick()
            //  **给条件挑前台应用那一趟**（多选）：FAB 常驻（清空勾选也不收，见调用点的
            //  `PageCornerFab`），但一条都没勾时不能确认 —— 一条条件至少要有一个应用，
            //  写下去就是个永远不成立的条件。说一句、什么都不写。
            if (picker.forBranch && picker.pickedApps.value.none { it.isNotBlank() }) {
                hint(I18n.get(Strings.APP_PICKER_BRANCH_AT_LEAST_ONE, "至少选择一个应用"))
                return@PageFabButton
            }
            // 交出去的是**整个 pending**，不是一个 target 字符串：快捷方式的身份是
            // `shortcutId` 而不是 `包名/id`，只传 target 会让落盘那边退回 `app:包名/id`
            // （那是一条跑不通的 token）。
            //
            //  **只在有选中项时这颗按钮才在**（见调用点的 `PageCornerFab(visible = …)`）——
            //  所以这一支理论上到不了；留着当兜底：将来要是又把 FAB 改回常驻，
            //  按下它也该说一句「先从列表里选一个应用」，而不是默默什么都不做。
            val picked =
                ui.pending ?: run {
                    hint(I18n.get(Strings.APP_PICKER_PICK_APP_FIRST, "先从列表里选一个应用"))
                    return@PageFabButton
                }
            val target = picked.target
            val pkg = AppTarget.pkg(target)
            // 结束应用那一趟**什么都不拦**：`proc:stop:` 要的就是「把它停掉」，
            //  与这个应用有没有启动图标、支不支持小窗都无关。
            if (picker.stopApp) {
                onPick(picked, false)
                return@PageFabButton
            }
            // 一行一个应用：`pending` 可能带 `/类名`，所以按「同一个应用」找，不能按 `same`
            val app = ui.apps.firstOrNull { AppTarget.sameApp(it.target, target) }
            //  **只拦「整个应用都没有入口」那一档**：`launchable` 是**应用级**的判据
            //  （`LaunchableApp` 只有包名，没有具体界面），而用户可能是在二级页挑了一个
            //  具体界面 —— 那一页列出来的就是能起的入口。按应用行拦等于把页面自己提供的
            //  可选项否掉：挑了一个可用的 Activity 却被弹「此应用无法打开」，一个字节都不写。
            //  带 `/类名` 的 target 交给执行侧那把尺子（`AppLauncher` 解析不出来会自己报「打不开」）。
            if (app != null && !app.launchable && AppTarget.activity(target) == null) {
                XLog.w("选择应用：$pkg 拉不起来，弹「此应用无法打开」")
                ui.blockedUnopenable = app
                return@PageFabButton
            }
            //  再一关：开着的那个开关**这一次用不了**（判据见 `blockedToggleOf`）。拦下来说一句、
            //  什么都不写 —— 写下去要么执行时弹 toast，要么根本吃不到那个动画。
            val blocked = blockedToggleOf(picked, app)
            if (blocked != null) {
                ui.blockedToggle = blocked
                return@PageFabButton
            }
            onPick(picked, picker.smallWindow.value)
        },
    ) {
        Icon(
            PageIcons.Confirm,
            contentDescription = I18n.get(Strings.APP_PICKER_CONFIRM_SELECTION, "确定选择"),
            tint = COUITheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 点 FAB 那一刻判「开着的那个开关这一次用不了」—— 两档共用这一份，`null` = 都能用。
 *
 *  两档判据的形状不同，但**结论与收尾完全一样**（说一句、拨回关、按普通方式写），所以只留一个
 *  出口；各写一份的话，「确定之后怎么收尾」那一段就要抄两遍（见 `ToggleBlock`）。
 *
 *  · **小窗**要当场问一次框架：这里要按结果决定「写还是拦」，异步化会造出「连点两下写两次」
 *    的新洞，而这是一次性用户动作、代价有界。
 *  · **原版动画**按目标形态直接判（分身 / 快捷方式 / 具体界面），不必问谁 ——
 *    为什么那三类吃不到见 `AppPickerState.stockAnimUnsupported`。
 *
 *  @param app 列表里那一个应用行；`null` = `pending` 是一个列表里没有的目标（小窗那一档跳过他）
 */
private fun SettingsPage.blockedToggleOf(
    picked: PickedDetail,
    app: LaunchableApp?,
): ToggleBlock? {
    val target = picked.target
    if (picker.smallWindow.value &&
        app != null &&
        querySmallWindowSupport(AppTarget.pkg(target), AppTarget.userId(target)) == false
    ) {
        XLog.w("选择应用：$target 不支持小窗，点确认时拦下（不写 window:）")
        return ToggleBlock(app.label, PickerToggle.MiniWindow)
    }
    if (picker.stockAnim.value && picker.stockAnimUnsupported(target, picked.shortcutId)) {
        XLog.w("选择应用：$target 吃不到原版动画，点确认时拦下（不写 app:a:）")
        return ToggleBlock(app?.label ?: AppTarget.pkg(target), PickerToggle.StockAnim)
    }
    return null
}
