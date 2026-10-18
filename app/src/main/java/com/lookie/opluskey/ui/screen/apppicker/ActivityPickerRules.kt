package com.lookie.opluskey.ui.screen.apppicker

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.escapeEmphasis
import com.lookie.opluskey.ui.model.LaunchableActivity
import com.lookie.opluskey.ui.state.PickedDetail

// 「选择界面」这一页的**判据**（纯函数）—— 与 `ActivityPickerList.kt` 的界面那棵树分开：
// 那边是 Compose（组合、滚动、布局），这边只吃清单与几个值、只吐结果，不碰 Compose。
//
// 分开的直接原因：`ActivityPickerList.kt` 的函数条数贴着 detekt `TooManyFunctions` 那一档，
// 而这几条判据又多被**两个**地方读（默认选中 / 定位 / 空态文案），塞在一起只会让下一个
// 想加一行判断的人先得读一遍组合代码。
//
// 这里每一条都要拿**完整清单**算：搜索词只决定「显示什么」，不决定「绑的是什么」。

/**
 * 把「当前绑定」规范成**清单里确实存在的某一行** —— 找不到就退回「自动」。
 *
 *  默认选中「自动」是这一页的起点：用户从应用那一级点进来，多半就是要绑整个应用；
 *  绑定里若记着某个具体界面 / 快捷方式，且这次清单里还有它，才点亮那一行。
 *
 *  为什么要判「在不在清单里」：卸载重装、应用改版删掉某个活动、或者换成另一个应用进来 ——
 *  那时绑定里的那条在这份清单里根本不存在。不兜这一下，整页一行都不亮，看起来像从没绑过；
 *  兜成「自动」则始终有一行亮着，用户一眼知道当前是什么档。
 *
 *  ## `all` 必须是**完整清单**，不是搜索过滤之后的那份
 *
 *  过滤只决定「屏幕上显示哪几行」。拿过滤后的清单当判据的话，用户随手打一个字、把当前绑定的
 *  那一行筛掉了，这一页就会判成「那一项已经不在清单里」⇒ 选中态退回「自动」⇒ 他一按 FAB，
 *  `包名/类名` 就被写成了 `包名` —— 他只是搜了一下，绑定被降级了。
 */
internal fun resolveSelection(
    selected: String?,
    target: String,
    all: List<LaunchableActivity>,
): String? =
    when {
        // 没绑过 / 绑的就是这个应用 ⇒ 「自动」
        selected == null || AppTarget.same(selected, target) -> target

        // 清单还没到（或查完了确实一条都没有）⇒ **不知道**，不等于「不在」：
        //  这时保持原值。判成「自动」的话，进页那一瞬（列表还在查）按 FAB 就会把它降级。
        all.isEmpty() -> selected

        // 绑的是具体某一项，且它还在清单里 ⇒ 用它
        all.any { AppTarget.same(it.target, selected) } -> selected

        // 那一项已经不在清单里（界面被删 / 换了应用）⇒ 退回「自动」
        else -> target
    }

/**
 * 进页默认选中的那一条 —— 在**完整清单**里找 `selected` 对应的行。
 *
 *  按完整清单找（而不是按过滤后的两节）：搜索词筛掉了那一行时，选中态仍要停在它上面，
 *  FAB 才会把「原来绑的东西」原样写回去。快捷方式要连 `shortcutId` 一起带上 ——
 *  少了它，写回会走 `app:`（而不是 `appshortcut:`），`包名/快捷方式id` 这种写法就废了。
 *
 *  @param selected 已经过 [resolveSelection] 规范化 —— 一定在清单里，或就是 [target]（「自动」）
 */
internal fun defaultPickOf(
    all: List<LaunchableActivity>,
    selected: String?,
    target: String,
): PickedDetail {
    val hit = all.firstOrNull { AppTarget.same(it.target, selected) }
    return when {
        // 快捷方式那一行要把它**自己的名字**（「扫一扫」）带上：不点行直接按 FAB 时，
        //  名字不能丢 —— 丢了就退回应用名，用户会看到「扫一扫」变成「微信」。
        hit != null && hit.shortcutId != null -> PickedDetail(hit.target, hit.shortcutId, hit.label)

        hit != null -> PickedDetail(hit.target, null)

        else -> PickedDetail(selected ?: target, null)
    }
}

/**
 * 搜索命中 —— 名字、类名、拼音三者任一包含即算。
 *
 *  · 名字：活动自己声明了 label 的那些（「扫一扫」这类）；
 *  · 类名：绝大多数活动没有 label，只能靠类名认（`ui.LauncherUI`）；
 *  · 拼音：名字是汉字时，用户会打 `saoyisao` 而不是 `扫一扫`（键见 [LaunchableActivity.sortKey]）。
 *
 *  单独一支是因为上面那个组合函数已经贴着 detekt 的长度上限；命中判据并在这里而不是再拆
 *  一个 `hit()` —— 文件里的函数数量也贴上限了。
 */
internal fun shownActivities(
    all: List<LaunchableActivity>?,
    term: String,
): List<LaunchableActivity> {
    val list = all ?: return emptyList()
    if (term.isEmpty()) return list
    val needle = term.lowercase(java.util.Locale.ROOT)
    return list.filter {
        it.label.contains(term, ignoreCase = true) ||
            it.summary.contains(term, ignoreCase = true) ||
            it.name.contains(term, ignoreCase = true) ||
            it.sortKey.contains(needle)
    }
}

/** 空态那句话 —— 真因优先：**没查出来**不是「一个都没有」。 */
internal fun emptyText(
    failed: Boolean,
    all: List<LaunchableActivity>?,
    term: String,
): String =
    when {
        failed -> I18n.get(Strings.APP_PICKER_ACTIVITY_LOAD_FAILED, "界面列表没读出来，退回上一页再进来试一次")

        all.isNullOrEmpty() -> I18n.get(Strings.APP_PICKER_ACTIVITY_EMPTY, "这个应用没有可从外部打开的界面")

        // 用户输入会整体过一遍正文记号：先转义
        else -> I18n.format(Strings.APP_PICKER_NO_SEARCH_RESULT_ACTIVITY, escapeEmphasis(term))
    }
