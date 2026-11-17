package com.lookie.opluskey.ui.row

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

/**
 * 「独立设置」页此刻那几道闸 —— **一处算清**，页面与卡都读这一份。
 *
 * ## 它挡的是哪三件事
 *
 *  这一页上的行分三类，各自能改的条件不同，混在一起算就会漏：
 *  · **单键手势那几档**（多击 / 三击 / 四击 / 单击后长按 / 单击卡）：只管整页闸门 + 这颗键
 *    还判不判单键手势；
 *  · **独立设置那几项**（接口 / 振动 / 提示 / 文案）：还要这一档开了「使用独立设置」；
 *  · **组合参数那两张卡**（窗口 / 按住下限 / 场景）：同上一类，但这几项只对组合成立。
 *
 *  [keyGestures] 为假只有一种情形：单键开了「只参与非首键的按住组合」—— 那颗键不判单键手势，
 *  多击与接口 / 振动 / 提示都没有执行的机会，亮着就是在骗人「还能调」。这一条仍留着那一行开关
 *  本身可改，否则用户想关回来都点不动。
 *
 * ## 为什么是一个值而不是几个 Boolean 参数
 *
 *  同一条判据（`page && !comboOnly`）原先在页面里出现四次、在两个卡的内部再各算一次；
 *  加一档新规则就得改四处，漏一处就是「亮着但配了没用」。现在判据在 [EditGate] 里，
 *  调用点只挑自己那一档（[keyRows] / [ownRows] / [comboRows]）。
 *
 * @param page 整页闸门（主开关 + 链路 + 配置可写）—— 关着时所有行不吃点击
 * @param keyGestures 这颗键还判不判单键手势（组合一律为真）
 * @param ownOn 这一档用不用自己的独立设置
 */
internal class EditGate(
    val page: Boolean,
    val keyGestures: Boolean,
    val ownOn: Boolean,
) {
    /** 单键手势那几档能不能改。 */
    val keyRows: Boolean = page && keyGestures

    /** 独立设置那几项能不能改。 */
    val ownRows: Boolean = keyRows && ownOn

    /** 组合参数那两张卡能不能改（组合没有「不判单键手势」这一档，所以只看 `page` 与 `ownOn`）。 */
    val comboRows: Boolean = page && ownOn

    /**
     * 独立设置那几项灰着被点一下时的说法：**只有「总开关没开」这一种**会说话 ——
     * 整页闸门关着 / 这颗键不判单键手势时留空，让页面那句统一的原因去说
     * （同一个原因不该在几个地方各算一遍）。
     */
    val ownHint: String? =
        if (keyRows && !ownOn) I18n.get(Strings.KEY_SETTINGS_NEED_OWN) else null
}
