package com.lookie.opluskey.ui.model.page

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.GlobalConfig

// 组合两列的**排列次序** —— 那两列小标题行尾「更多」里的三档。

/**
 * 「等待组合 / 按住组合」两列怎么排。
 *
 *  排在内存里、落在配置文件的 `GlobalConfig.waitComboSort` / `holdComboSort`（文本 id）：
 *  **跟着配置文件走** —— 换一份配置就换一套显示偏好，与配色 / 语言同一档。
 *
 *  排的是**显示次序**，不是配置：`cfg.combos` 自己的先后（= 创建先后）永远不动，
 *  所以拨回 [CREATED] 就回到原来的样子。
 */
internal enum class ComboSort(
    /** 存进配置文件的那个 id（见 `GlobalConfig.SORT_BY_CREATED` 那一组）。 */
    val code: String,
) {
    /** 不排序 —— 就是创建顺序（`cfg.combos` 里的先后）。 */
    CREATED(GlobalConfig.SORT_BY_CREATED),

    /** 按顺序触发的在前，其余保持创建顺序。 */
    ORDERED_FIRST(GlobalConfig.SORT_ORDERED_FIRST),

    /** 无顺序（不分前后）的在前，其余保持创建顺序。 */
    UNORDERED_FIRST(GlobalConfig.SORT_UNORDERED_FIRST),
    ;

    companion object {
        /**
         * 按 id 找档位；认不出一律落回 [CREATED]。
         *
         *  与 `AppTheme.ofCode` / `PagePalette.ofId` 同一个理由：id 存在配置文件里，
         *  手改过、从别的版本导回来都可能带进不认识的档 —— 那时最合理的结果是「不排序」，
         *  而不是整列读不出来。
         */
        fun ofCode(code: String?): ComboSort = entries.firstOrNull { it.code == code } ?: CREATED
    }
}

/**
 * 这一档在**菜单行与回执**里的说法 —— 两处读同一份，改文案不会只改到一半。
 */
internal fun comboSortLabel(sort: ComboSort): String =
    I18n.get(
        when (sort) {
            ComboSort.CREATED -> Strings.COMBO_SORT_CREATED
            ComboSort.ORDERED_FIRST -> Strings.COMBO_SORT_ORDERED_FIRST
            ComboSort.UNORDERED_FIRST -> Strings.COMBO_SORT_UNORDERED_FIRST
        },
    )

/**
 * 菜单行那句副标题 —— **只有「不排序」有**：另外两档的名字已经说清了「按什么排」，
 * 这一档的排序键（创建时间）在名字里看不出来，得单独写一句。
 */
internal fun comboSortSummary(sort: ComboSort): String? =
    when (sort) {
        ComboSort.CREATED -> I18n.get(Strings.COMBO_SORT_CREATED_DESC)
        ComboSort.ORDERED_FIRST, ComboSort.UNORDERED_FIRST -> null
    }
