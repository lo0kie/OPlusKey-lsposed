package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.PageFabButton
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.escapeEmphasis
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.APP_STATE_ICON_SIZE
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.state.AppPickerUiState
import com.lookie.opluskey.ui.state.PickedDetail
import com.lookie.opluskey.ui.state.SmallWindowBlock
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ProgressIndicatorDefaults
import io.github.suqi8.coui.kmp.basic.RotatingProgressIndicator
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

/** 列表还在查 —— ColorOS 默认那颗转圈 + 一句话。 */
@Composable
internal fun BoxScope.PickListLoadingState(
    visible: Boolean,
    topInset: Dp,
) {
    PageStatePlaceholder(
        visible = visible,
        topInset = topInset,
        text = I18n.get(Strings.APP_PICKER_LOADING_APPS, "正在加载应用…"),
        glyph = { tint ->
            // 库那套三个数是按 26dp 画布算出来的，只把 `size` 换成 32dp 等于只多留白、
            // 圈没变大 —— 三个数要按同一比例一起放大（比例由 `APP_STATE_ICON_SIZE` 现算）。
            val spinnerScale =
                APP_STATE_ICON_SIZE.value /
                    ProgressIndicatorDefaults.DefaultRotatingProgressIndicatorSize.value
            RotatingProgressIndicator(
                color = tint,
                size = APP_STATE_ICON_SIZE,
                ringDiameter = ProgressIndicatorDefaults.DefaultRotatingProgressIndicatorRingDiameter * spinnerScale,
                strokeWidth = ProgressIndicatorDefaults.DefaultRotatingProgressIndicatorStrokeWidth * spinnerScale,
            )
        },
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
        text = I18n.get(Strings.APP_PICKER_LOAD_FAILED, "应用列表没读出来，退回上一页再进来试一次"),
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
 * 空态那句话 —— 按「为什么一条都没有」分四档（真因优先：**没查出来**不是「一个都没有」）。
 *
 *  注意：这一句用的是**页面级**的两个落盘开关（`showUnopenable`）与搜索词，所以放在
 *  `SettingsPage.` 扩展里算，而不是让空态组件自己取。
 */
internal fun SettingsPage.emptyHint(ui: AppPickerUiState): String {
    val apps = ui.apps
    val showBroken = picker.showUnopenable.value
    return when {
        // 真因优先：这次是**没查出来**，不是「一个都没有」（后者是有效结论）
        ui.failed && apps.isEmpty() -> {
            I18n.get(Strings.APP_PICKER_LOAD_FAILED, "应用列表没读出来，退回上一页再进来试一次")
        }

        apps.isEmpty() -> {
            // 不再说「系统限制了本页可见的应用列表」：那个前提不成立，
            // 这一页的包可见性跟着宿主走，真列不出来只会是别的原因。
            I18n.get(Strings.APP_PICKER_EMPTY_OPENABLE, "没有找到可以打开的应用")
        }

        // 措辞与菜单里那一项（「显示失效应用」）对齐，别让用户去猜是哪一个开关
        !showBroken && apps.all { !it.launchable } -> {
            I18n.get(Strings.APP_PICKER_EMPTY_HINT_BROKEN, "可以打开的一个都没有；开「显示失效应用」能看到打不开的那些")
        }

        else -> {
            // 用户输入会整体过一遍正文记号：先转义
            I18n.format(Strings.APP_PICKER_NO_SEARCH_RESULT, escapeEmphasis(picker.filter.value.trim()))
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
 *  2. 开着「以小窗口打开」⇒ 点这一下问一次框架（全流程只问这一次）：不支持就拦下，
 *     免得写一个执行时必弹 toast 的 `window:`；
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
            // 一行一个应用：`pending` 可能带 `/类名`，所以按「同一个应用」找，不能按 `same`
            val app = ui.apps.firstOrNull { AppTarget.sameApp(it.target, target) }
            //  **只拦「整个应用都没有入口」那一档**：`launchable` 是**应用级**的判据
            //  （`LaunchableApp` 只有包名，没有具体界面），而用户可能是在二级页挑了一个
            //  具体界面 —— 那一页列出来的就是能起的入口。按应用行拦等于把页面自己提供的
            //  可选项否掉：开了「显示失效应用」之后，挑一个可用的 Activity 却被弹
            //  「此应用无法打开」，一个字节都不写。带 `/类名` 的 target 交给执行侧那把尺子
            //  （`AppLauncher` 解析不出来会自己报「打不开」）。
            if (app != null && !app.launchable && AppTarget.activity(target) == null) {
                XLog.w("选择应用：$pkg 拉不起来，弹「此应用无法打开」")
                ui.blockedUnopenable = app
                return@PageFabButton
            }
            if (picker.smallWindow.value && app != null) {
                // 主线程问一次：这里要按结果当场决定「写还是拦」，异步化会造出
                // 「连点两下写两次」的新洞，而这是一次性用户动作、代价有界。
                if (querySmallWindowSupport(pkg, AppTarget.userId(target)) == false) {
                    XLog.w("选择应用：$pkg 不支持小窗，点确认时拦下（不写 window:）")
                    ui.blockedSmallWindow = SmallWindowBlock(app, fromPick = true)
                    return@PageFabButton
                }
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
