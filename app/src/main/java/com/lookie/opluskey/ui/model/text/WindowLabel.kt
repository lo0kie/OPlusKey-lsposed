package com.lookie.opluskey.ui.model.text

import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey

// 窗口那两行**开关标题**的前缀，以及「这一档该用哪个前缀」的判据。
//
//  住在共用件这一层而不是 `ui/screen/combo`：卡片（`ComboParamCards`）与写回层
//  （`ui/write/GlobalWrite` 拨开关的回执）念的是同一个名字，两处必须同一份实现；
//  而让 `ui/write` 反过来 import 具体屏是分层倒挂（`tools/audit-coupling.py` 判越界）。

/**
 * 窗口那两行**开关标题**的**前缀**（「固定窗口起点」前面的那几个字）；滑杆那一行不带前缀。
 *
 *  一张卡同一时刻只有一个前缀：公共页两个卡片各取一个（「按键间隔和多击…」/「按键间隔…」，
 *  一份默认值同时服务几类档），独立设置页只写这一档自己的那一个 —— 两套标签摆在同一页上，
 *  用户得先分辨「我这一档算哪种」才知道该调哪一个。
 */
internal enum class WindowLabel {
    /** 不带前缀（普通等待组合：那两行量的是两颗键之间的间隔，没有更具体的一类可写）。 */
    PLAIN,

    /** 「多击…」—— 普通档（成员键自己的连击窗口）。 */
    MULTI,

    /** 「按键间隔…」—— 除两键档以外的所有档（有序也算：它量的仍是两次按下之间的间隔）。 */
    INTERVAL,

    /** 「按键间隔和多击…」—— 公共那张按住卡：那里的默认值同时服务三颗以上的按住档与两键档。 */
    INTERVAL_MULTI,
}

/** 「固定窗口起点」那两行在这一档下叫什么 —— 卡片标题与回执文案共用这一支。 */
internal fun WindowLabel.fixedWindowStartTitle(): String =
    when (this) {
        WindowLabel.PLAIN -> Strings.COMBO_FIXED_WINDOW_START_TITLE
        WindowLabel.MULTI -> Strings.COMBO_MULTI_FIXED_WINDOW_TITLE
        WindowLabel.INTERVAL -> Strings.COMBO_INTERVAL_FIXED_WINDOW_TITLE
        WindowLabel.INTERVAL_MULTI -> Strings.COMBO_INTERVAL_MULTI_FIXED_WINDOW_TITLE
    }

/**
 * 某一档在独立设置页里该用哪个前缀（按住那张卡 / 那颗开关）—— 判据只有这一处。
 *
 *  只有**两键的按住组合**写「多击…」（那两行在那档上只服务成员键自己的连击链），
 *  其余一律「按键间隔…」—— 有序与不分顺序都算这一类：档位按不按先后落，
 *  与「多久之内落完」是两件事。
 */
internal fun holdWindowLabelOf(combo: ComboKey): WindowLabel =
    when {
        combo.memberGestures != null -> WindowLabel.MULTI
        else -> WindowLabel.INTERVAL
    }
