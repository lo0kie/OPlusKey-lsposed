package com.lookie.opluskey.ui.screen.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lookie.opluskey.core.config.ConfigProfiles
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.DialogButtonRow
import com.lookie.opluskey.ui.DialogPositiveStyle
import com.lookie.opluskey.ui.FrostedDialog
import com.lookie.opluskey.ui.ImportOverwrite
import com.lookie.opluskey.ui.LocalHint
import com.lookie.opluskey.ui.PendingImport
import com.lookie.opluskey.ui.PickerRow
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.escapeEmphasis
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.MENU_GROUP_LINE_DARK_ALPHA
import com.lookie.opluskey.ui.model.MENU_GROUP_LINE_LIGHT_ALPHA
import com.lookie.opluskey.ui.model.isDarkScheme
import com.lookie.opluskey.ui.state.CommandsSave
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 导入配置**选目标**那一窗 —— 导入文件校验通过后弹出，列出已有配置（含「默认」），
 * 点一行选中、点「确定」把导入的内容盖到那一份；最后一行是「新增配置」，选它则像以前一样
 * 插在最末。
 *
 * ## 为什么是「单选 + 确定」而不是「点一行就作数」
 *
 *  覆盖是一整份配置被换掉（连同命令库），代价与「删除配置」同档 —— 点一下就走太轻。
 *  先选中、再点确定，中间还能反悔（点别的行 / 点取消）。
 *
 * ## 「新增配置」到顶时那行灰着但**仍可点**
 *
 *  点它报一句「最多三份配置」（不是真的按不动）—— 灰着却点不出声，最容易让人以为界面坏了。
 */
@Composable
internal fun SettingsPage.ImportTargetDialog() {
    val pending = pendingImport
    val state = rememberImportTargetState(pending)
    FrostedDialog(
        show = pending != null,
        title = I18n.get(Strings.PROFILE_IMPORT_OVERWRITE_TITLE, "覆盖配置"),
        onDismiss = { pendingImport = null },
        onDismissFinished = {
            //  这一窗真的收场了再弹确认窗（同帧开两个会叠在一起）
            state.nextOverwrite?.let { pendingImportOverwrite = it }
            state.nextOverwrite = null
        },
        footer = {
            DialogButtonRow(
                positive = I18n.get(Strings.COMMON_CONFIRM, "确定"),
                onNegative = { pendingImport = null },
                onPositive = {
                    val target = state.selected
                    //  **先清再判**：窗要从这一刻开始退场，确认窗排在收场之后（见 onDismissFinished）。
                    //  `pending` 是这一帧的快照，清掉 pendingImport 不影响它。
                    pendingImport = null
                    if (target != null && pending != null) {
                        when (target) {
                            //  新建一份没有可确认的（不动任何已有内容）：直接写
                            ImportTarget.New -> {
                                createImportedProfile(pending)
                            }

                            //  覆盖：先记下要弹的那张窗，等这一窗收场再开
                            is ImportTarget.Into -> {
                                state.nextOverwrite =
                                    ImportOverwrite(
                                        content = pending,
                                        slot = target.slot,
                                        displayName =
                                            state.profiles
                                                .firstOrNull { it.slot == target.slot }
                                                ?.displayName
                                                .orEmpty(),
                                    )
                            }
                        }
                    }
                },
                positiveStyle =
                    DialogPositiveStyle(
                        enabled = state.selected != null,
                        onDisabledClick = {
                            hint(I18n.get(Strings.PROFILE_IMPORT_PICK_FIRST, "先从列表里选一份配置"))
                        },
                    ),
            )
        },
    ) {
        ImportTargetRows(state.profiles, state.full, state.selected) { state.selected = it }
    }
}

/**
 * 导入选择器那几样临时状态。
 *
 * ## 为什么**不能**用 `remember(pending)`
 *
 *  点「确定」那一下会先把 [SettingsPage.pendingImport] 清掉（窗要从这一刻开始退场）。状态若挂在
 *  `remember(pending)` 上，那一帧重组会把它们**全部重建** —— 刚写进去的「接下来要弹哪张确认窗」
 *  当场变回 `null`，而 `onDismissFinished` 读到的正是这个新值。真机表现就是这个 bug：
 *  选了、点确定、什么也没发生，窗直接关了。
 *
 *  （`remember` 的 key 一变就丢值。这里恰恰要在 `pending` 变 `null` **之后**还读得到它。）
 *
 *  改成不带 key 的 `remember` 就稳了 —— 页面在这一整段时间里只组合一次这一支，对象不会重建；
 *  「每次打开要重读列表、清掉上次的选中」交给 [rememberImportTargetState] 里那个 `LaunchedEffect`。
 */
private class ImportTargetState {
    /**
     * 打开那一刻扫的配置列表 —— 已有配置（含「默认」），**「读不出来」的那份已滤掉**：
     * 它没有可保留的名字与位次（`ConfigProfiles.importInto` 按 `readOf` 拒绝覆盖），
     * 列进来只会让用户选中之后得到一句「导入失败」。清理它走「切换配置」菜单里那条删除。
     */
    var profiles by mutableStateOf<List<ConfigProfiles.Item>>(emptyList())

    /** 「新增配置」还能不能选（除「默认」之外是否已有三份）。 */
    var full by mutableStateOf(false)

    /** 选中的目标：`null` = 还没选；[ImportTarget.Into] 带槽名，[ImportTarget.New] = 另起一份。 */
    var selected by mutableStateOf<ImportTarget?>(null)

    /**
     * 点了「确定」之后要接着弹的那张确认窗 —— **等这一窗收场了才置给页面**
     * （两层窗不叠着开，见 `FrostedDialog` 的 `onDismissFinished`）。
     */
    var nextOverwrite by mutableStateOf<ImportOverwrite?>(null)
}

/**
 * 取（并维护）选择器的那几样状态 —— 抽成函数只为让 [ImportTargetDialog] 留在 `LongMethod` 线内。
 *
 *  列表与「已满」在**每次打开**时重读一遍（盘上扫的，几份小文件）：上一次进来之后用户可能
 *  新建 / 删除过配置。`pending` 变 `null`（窗在退场）时什么都不做 —— 那一帧手上的列表还要
 *  接着画完退场动画，清空会闪一下白。
 */
@Composable
private fun rememberImportTargetState(pending: PendingImport?): ImportTargetState {
    val state = remember { ImportTargetState() }
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        state.profiles = ConfigProfiles.list().filterNot { it.broken }
        state.full = ConfigProfiles.isFull()
        state.selected = null
        state.nextOverwrite = null
    }
    return state
}

/**
 * 选择器正文那一列 —— 已有配置 + 最末一行「新增配置」。拆出来是为了让 [ImportTargetDialog]
 * 留在 detekt 的 `LongMethod` 线内。
 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ImportTargetRows(
    profiles: List<ConfigProfiles.Item>,
    full: Boolean,
    selected: ImportTarget?,
    onSelect: (ImportTarget) -> Unit,
) {
    //  点「新增配置」却已满时那句提示 —— 组合期读一次，点击回调里直接用（`LocalHint.current`
    //  只能在组合期读，不能挪进 `onClick` 那个普通 lambda）
    val fullHint = LocalHint.current
    val fullText = I18n.get(Strings.PROFILE_FULL, "最多三份配置")
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        profiles.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            }
            PickerRow(
                label = item.displayName,
                selected = selected == ImportTarget.Into(item.slot),
                onClick = { onSelect(ImportTarget.Into(item.slot)) },
            )
        }
        //  「新增配置」与上面那份列表之间用**加深带**断开：上面几行是「盖掉哪一份」（替换已有的），
        //  这一行是「另起一份」，两类动作不是一件事 —— 一条同样粗细的细线读起来还在同一组里。
        if (profiles.isNotEmpty()) {
            PopupHeavyDivider()
        }
        PickerRow(
            label = I18n.get(Strings.PROFILE_IMPORT_NEW, "新增配置"),
            selected = selected == ImportTarget.New,
            enabled = !full,
            onClick = {
                if (full) {
                    fullHint(fullText)
                } else {
                    onSelect(ImportTarget.New)
                }
            },
        )
    }
}

/** 导入的目标 —— 「盖到哪一份」或「新增一份」。 */
private sealed interface ImportTarget {
    /** 覆盖到 [slot] 那一份（含「默认」与当前生效的那份）。 */
    data class Into(
        val slot: String,
    ) : ImportTarget

    /** 新增一份、插在最末。 */
    data object New : ImportTarget
}

/**
 * 选择器里「这一组 / 下一组」之间那条**加深带** —— 与浮层菜单组间那条同一档（同两个 alpha、
 * 同一档粗细），两处读起来才是同一种分隔。
 */
@Composable
private fun PopupHeavyDivider() {
    HorizontalDivider(
        thickness = DropdownDefaults.PopupGroupDividerThickness,
        color =
            if (COUITheme.colorScheme.isDarkScheme) {
                Color.Black.copy(alpha = MENU_GROUP_LINE_DARK_ALPHA)
            } else {
                Color.Black.copy(alpha = MENU_GROUP_LINE_LIGHT_ALPHA)
            },
    )
}

/** 新建一份（插在最末）—— 与旧版「导入时插到最末」完全一致。 */
private fun SettingsPage.createImportedProfile(pending: PendingImport) {
    val slot = ConfigProfiles.importAs(pending.config, pending.commands, pending.name)
    if (slot == null) {
        XLog.w("导入配置：没写成（存储不可写？）")
        hint(I18n.get(Strings.TOAST_CONFIG_IMPORT_FAILED, "导入失败"))
        return
    }
    hint(I18n.format(Strings.PROFILE_IMPORTED, ConfigProfiles.readOf(slot)?.profileName ?: pending.name))
}

/**
 * 确认窗点了「覆盖」之后的真正写盘 —— 覆盖那一份；若覆盖的是**当前生效**的（`CURRENT_SLOT`），
 * 还要把页面状态与命令库刷新、并广播给注入侧（与「切换配置」后同一条收尾）。
 */
internal fun SettingsPage.overwriteProfile(overwrite: ImportOverwrite) {
    val ordered = ConfigProfiles.importInto(overwrite.slot, overwrite.content.config, overwrite.content.commands)
    if (ordered == null) {
        XLog.w("导入配置：覆盖 ${overwrite.slot} 没写成")
        hint(I18n.get(Strings.TOAST_CONFIG_IMPORT_FAILED, "导入失败"))
        return
    }
    //  覆盖的是当前生效那份：文件已经换成新的，但页面状态、命令库、注入侧还都是旧的 ——
    //  走通用写入通道（`write`）刷新并广播（与「切换配置」同一条收尾，见 applySwitchedConfig）
    if (overwrite.slot == ConfigProfiles.CURRENT_SLOT) {
        if (applySwitchedConfig(ordered, "导入覆盖当前配置") == CommandsSave.Failed) return
    }
    hint(
        I18n.format(
            Strings.PROFILE_OVERWRITTEN,
            ordered.profileName.ifBlank { I18n.get(Strings.PROFILE_DEFAULT, "默认配置") },
        ),
    )
}

/**
 * 覆盖确认窗 —— 在选择器点「确定」之后弹（选择器先关，两窗不叠着开）。
 *
 *  正文点名要覆盖哪一份，并说清「那一份的内容会被换掉」：这是没有回收站的操作。
 */
@Composable
internal fun SettingsPage.ImportOverwriteConfirmDialog() {
    val overwrite = pendingImportOverwrite
    ConfirmDialog(
        show = overwrite != null,
        title = I18n.get(Strings.PROFILE_IMPORT_OVERWRITE_TITLE, "覆盖配置"),
        summary =
            I18n.format(
                Strings.PROFILE_IMPORT_OVERWRITE_MESSAGE,
                escapeEmphasis(overwrite?.displayName.orEmpty()),
            ),
        action = I18n.get(Strings.PROFILE_IMPORT_OVERWRITE_ACTION, "覆盖"),
        onDismiss = { pendingImportOverwrite = null },
        onConfirm = {
            pendingImportOverwrite = null
            overwrite?.let { overwriteProfile(it) }
        },
    )
}
