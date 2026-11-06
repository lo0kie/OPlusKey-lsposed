package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.model.PageIcons
import io.github.suqi8.coui.kmp.basic.CardListPosition

// 「长按」那一档的设置卡 —— 单键页与组合键页**共用这一张**。

/**
 * 一次组合要的现行值、三档时序的互斥结论所需的那几位，以及四条写回去的路。
 *
 * 为什么要有这一份：这张卡在「按键绑定」的长按档与「组合键」的某一条上各画一次，**行序、文案、
 * 可见性判据必须一模一样**（用户口径「和另一个一样」）。原先两处各写一份同样的 `CardRow` 清单，
 * 改一句副标题就得记着去改另一处 —— 现在「画哪些行」只有这一份，两页只答两件事：读谁的字段、
 * 写完落到哪一条写盘说明（`onRepeat` / `onVibrate` / `onInterval` / `onPressMs` 仍留在各自的页里，
 * 写盘说明与提示语义位的差异不跨页传染）。
 *
 * @param enabled 上面那几行能不能点（各自还要看与别人是否互斥）
 * @param delayEnabled 「长按延迟」那一行能不能点 —— 它比上面几行多一道闸：单键页还看首页那个主开关
 * @param blockedReason 非 null ⇒ **整张卡**置灰（主开关关 / 链路没通 / 配置只读）—— 只放整页级的原因
 * @param repeatBlockedReason 非 null ⇒ 只有「连续触发」那一族置灰：这一档的长按动作**自己**不支持
 *   连发（多条命令 / 灵感语音）。它**不连坐**时序两行与长按延迟 —— 那三项跟连不连发无关
 * @param onInterval / @param onPressMs 返回 `false` ⇒ 这一档没落盘（滑杆拨回真实值）
 * @param fireOnRelease 「松手才触发」—— 长按动作本身等抬手才执行（与连续触发、松手再加动作互斥）
 * @param onFireOnReleaseToggle 拨「松手才触发」
 * @param releaseSupported 这一页的判定侧有没有「松手」这条通道 —— 两行都靠它（组合键那一页没有，
 *   见 `ComboLongCard` 的说明），没有就**一行都不画**：摆一个拨了没反应的开关比没有更糟
 * @param releaseOn 开关；为真时下面滑入「松手动作」那一行
 * @param releaseLabel 已配的动作名；`null` = 还没配（副标题写「未设置」）
 * @param releaseBound 松手那一档此刻**真的有事可做** —— 直接递 `KeyConfig.releaseBound`，
 *   界面不重算这条规则（三条时序的互斥只在数据层有一份，见 `KeyConfig.longOnReleaseTiming`）
 * @param onReleaseToggle 拨开关
 * @param onReleasePick 点「松手动作」那一行：打开动作清单
 */
@Suppress("LongParameterList")
internal class LongPressSpec(
    val repeat: Boolean,
    val vibrate: Boolean,
    val intervalMs: Int,
    val pressMs: Int,
    val fireOnRelease: Boolean,
    val releaseSupported: Boolean,
    /** 「松手后再执行」的开关 —— 为真时下面滑入「松手动作」那一行。 */
    val releaseOn: Boolean,
    val releaseLabel: String?,
    val releaseBound: Boolean,
    val enabled: Boolean,
    val delayEnabled: Boolean,
    val blockedReason: String? = null,
    val repeatBlockedReason: String? = null,
    val onRepeat: (Boolean) -> Unit,
    val onVibrate: (Boolean) -> Unit,
    val onInterval: (Int) -> Boolean,
    val onPressMs: (Int) -> Boolean,
    val onFireOnReleaseToggle: (Boolean) -> Unit,
    val onReleaseToggle: (Boolean) -> Unit,
    val onReleasePick: () -> Unit,
)

/**
 * 三档时序的**互斥结论** —— 哪一行亮、哪一行灰、灰着那一行说什么，只在这里算一次。
 *
 * 为什么单开一个类而不是写在卡片里：三个开关互相牵制（松手才触发 ⇄ 松手后再执行 ⇄ 连续触发），
 * 只有**一条时序能占着**；写在一处才看得出这件事，卡片自己只管把它们摆成行。
 * `enabled = false` 的行不给 `disabledHint` —— 那时该说的是**整页**为什么改不了（链路 / 只读），
 * 不是这一行自己的事。
 *
 * 判据与数据侧同一口径（见 `KeyConfig.releaseBound` / `longOnReleaseTiming`）：**那条规则不在
 * 这里重算** —— `releaseBound` 由调用点从配置里取来递进 `LongPressSpec`，这一层只负责把它翻译成
 *  「哪一行灰 / 灰着说什么」。
 */
private class LongPressGates(
    private val spec: LongPressSpec,
) {
    /** 松手那一档**真的有事可做**（`KeyConfig.releaseBound` 原样递进来）。 */
    val releaseBound: Boolean = spec.releaseBound

    /** 「松手才触发」能不能拨 —— 松手那一档占着时它得让位（先关掉那个）。 */
    val fireEnabled: Boolean = spec.enabled && !releaseBound

    /** 「松手后再执行」能不能拨 —— 「松手才触发」开着时它没有意义。 */
    val releaseEnabled: Boolean = spec.enabled && !spec.fireOnRelease

    /** 连续触发那一族能不能点 —— 三种情形：整张卡被闸住 / 时序被占着 / 动作本身不支持连发。 */
    val repeatEnabled: Boolean =
        spec.enabled && !spec.fireOnRelease && !releaseBound && spec.repeatBlockedReason == null

    /** 「松手才触发」灰着被点一下时的说法（`null` = 用整页那句）。 */
    val fireHint: String? =
        if (spec.enabled && releaseBound) {
            I18n.get(Strings.BINDING_RELEASE_UNSUPPORTED_FIRE, "先关掉「松手后再执行」")
        } else {
            null
        }

    /** 「松手后再执行」灰着被点一下时的说法。 */
    val releaseHint: String? =
        if (spec.enabled && spec.fireOnRelease) {
            I18n.get(Strings.BINDING_FIRE_ON_RELEASE_UNSUPPORTED_RELEASE, "长按已经改成松手才触发")
        } else {
            null
        }

    /**
     * 连续触发那一族的副标题 / 置灰原因 —— 有**四个来源**，按「越靠前越该先看到」排：整张卡的原因、
     * 占着时序的那一位、动作本身不支持连发（[LongPressSpec.repeatBlockedReason]）。
     */
    val repeatBlocked: String? =
        spec.blockedReason
            ?: when {
                spec.fireOnRelease -> {
                    I18n.get(Strings.BINDING_FIRE_ON_RELEASE_UNSUPPORTED_REPEAT, "松手才触发，连续触发不生效")
                }

                releaseBound -> {
                    I18n.get(Strings.BINDING_RELEASE_UNSUPPORTED_REPEAT, "配了松手动作，连续触发不生效")
                }

                else -> {
                    spec.repeatBlockedReason
                }
            }
}

/**
 * 「长按」那一档的**唯一**一张卡：松手才触发 + 松手后再执行（+ 松手动作）+ 长按连续触发
 * （+ 连续触发时振动 + 触发间隔）+ 长按延迟。
 *
 *  行序：**「松手才触发」在最上面**（它管的是「什么时候执行」，下面两族管的是「执行几次 /
 *  松手还做不做别的」）；紧跟「松手后再执行」那一族，再是「长按连续触发」那一族；
 *  「长按延迟」落到最后一行。
 *
 *  三种时序只能占一种：开了「松手才触发」，连发与「松手后再执行」都**置灰但保留**
 *  （用户口径：不要藏起来）—— 藏掉等于让人以为自己没配过它，而它只是这一刻不生效；值也原样留着。
 */
@Composable
internal fun SettingsPage.LongPressSettingsCard(spec: LongPressSpec) {
    val gates = LongPressGates(spec)
    CardList(
        rows =
            buildList {
                addAll(timingRows(spec, gates))
                addAll(releaseRows(spec, gates))
                addAll(repeatRows(spec, gates))
                add(
                    CardRow.Custom { position ->
                        LongPressDelayRow(position, spec.pressMs, spec.delayEnabled, spec.onPressMs)
                    },
                )
            },
    )
}

/**
 * 「松手才触发」那一行 —— 长按的**时序**本身：到点就执行，还是等抬手那一刻才执行。
 *
 *  开着它，长按动作只执行一次、且发生在抬手；关着才是原来的「到点即执行」（那一档才有连发）。
 *  组合键那一页不画（[LongPressSpec.releaseSupported] = false）。
 */
private fun timingRows(
    spec: LongPressSpec,
    gates: LongPressGates,
): List<CardRow> =
    if (!spec.releaseSupported) {
        emptyList()
    } else {
        listOf(
            CardRow.Switch(
                title = I18n.get(Strings.BINDING_LONG_PRESS_FIRE_ON_RELEASE_TITLE, "松手才触发"),
                summaryOn = I18n.get(Strings.BINDING_LONG_PRESS_FIRE_ON_RELEASE_DESC_ON, "按到长按时长先不执行，抬手那一刻执行"),
                summaryOff = I18n.get(Strings.BINDING_LONG_PRESS_FIRE_ON_RELEASE_DESC_OFF, "按到长按时长就立刻执行"),
                icon = PageIcons.LongFireOnRelease,
                checked = spec.fireOnRelease,
                enabled = gates.fireEnabled,
                disabledHint = gates.fireHint,
                onCheckedChange = spec.onFireOnReleaseToggle,
            ),
        )
    }

/**
 * 「松手后再执行」那一族：一个开关 + 开关为真时滑入的「松手动作」一行。
 *
 *  开关与动作分开（见 `KeyConfig.longReleaseOn`）：开着但还没挑动作是合法状态 —— 松手什么都不做。
 *  子行走 `visible`，所以它是**滑入**（连分隔线一起），不是"啪"地出现。
 */
private fun releaseRows(
    spec: LongPressSpec,
    gates: LongPressGates,
): List<CardRow> =
    if (!spec.releaseSupported) {
        emptyList()
    } else {
        listOf(
            CardRow.Switch(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE, "松手后再执行"),
                summaryOn = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_DESC_ON, "松手时再执行一个动作"),
                summaryOff = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_DESC_OFF, "松手不再执行别的"),
                icon = PageIcons.LongRelease,
                checked = spec.releaseOn,
                enabled = gates.releaseEnabled,
                disabledHint = gates.releaseHint,
                onCheckedChange = spec.onReleaseToggle,
            ),
            CardRow.Item(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_ACTION, "松手动作"),
                summary =
                    spec.releaseLabel
                        ?: I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_EMPTY, "未设置（松手不做事）"),
                icon = PageIcons.Click,
                arrow = true,
                enabled = gates.releaseEnabled,
                disabledHint = gates.releaseHint,
                onClick = spec.onReleasePick,
                visible = spec.releaseOn,
            ),
        )
    }

/**
 * 连续触发那一族：开关 + 「连续触发时振动」+「触发间隔」。
 *
 *  后两行只在开关打开时存在（关着时没有可调的东西），且都是那一行的参数 —— 所以紧跟着它，
 *  而不是掉到「长按延迟」后面去；显隐由列表连分隔线一起做（`visible`），只藏行会留一条孤零零的线。
 *  两行的先后是**先「振动」后「间隔」**（用户口径）：先答「每次要不要振」，再调「隔多久一次」。
 */
private fun SettingsPage.repeatRows(
    spec: LongPressSpec,
    gates: LongPressGates,
): List<CardRow> =
    listOf(
        //  副标题那三个来源收在 [LongPressGates.repeatBlocked] 一处。
        CardRow.Switch(
            title = I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE, "长按连续触发"),
            summaryOn = gates.repeatBlocked ?: I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_DESC_ON, "按住不放，按间隔重复执行"),
            summaryOff = gates.repeatBlocked ?: I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_DESC_OFF, "按住不放只执行一次"),
            icon = PageIcons.LongRepeat,
            checked = spec.repeat,
            enabled = gates.repeatEnabled,
            //  灰着被点一下也要说清为什么（与「选择应用」那几页的禁用行同一条口径）
            disabledHint = gates.repeatBlocked,
            onCheckedChange = spec.onRepeat,
        ),
        CardRow.Switch(
            title = I18n.get(Strings.BINDING_REPEAT_VIBRATE_TITLE, "连续触发时振动"),
            summaryOn = I18n.get(Strings.BINDING_REPEAT_VIBRATE_DESC_ON, "每次重复都振动"),
            summaryOff = I18n.get(Strings.BINDING_REPEAT_VIBRATE_DESC_OFF, "只在长按触发时振动一次"),
            icon = PageIcons.Vibrate,
            checked = spec.vibrate,
            enabled = gates.repeatEnabled,
            visible = spec.repeat,
            disabledHint = gates.repeatBlocked,
            onCheckedChange = spec.onVibrate,
        ),
        CardRow.Custom(visible = spec.repeat) { position ->
            RepeatIntervalRow(position, spec.intervalMs, gates.repeatEnabled, spec.onInterval)
        },
    )

/**
 * 「触发间隔」那一行 —— 长按连续触发隔多久重放一次。
 *
 *  单独一支只为让 [LongPressSettingsCard] 留在 detekt 的长度上限之内（它自己也要能被 `visible` 藏起来）。
 */
@Composable
private fun SettingsPage.RepeatIntervalRow(
    position: CardListPosition,
    value: Int,
    enabled: Boolean,
    onCommit: (Int) -> Boolean,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.BINDING_REPEAT_INTERVAL_TITLE, "触发间隔"),
                icon = PageIcons.DelayRepeat,
                summary = I18n.get(Strings.BINDING_REPEAT_INTERVAL_DESC, "长按连续触发的重复间隔"),
                min = KeyConfig.MIN_REPEAT_INTERVAL_MS,
                max = KeyConfig.MAX_REPEAT_INTERVAL_MS,
                value = value,
                default = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                enabled = enabled,
            ),
        onCommit = onCommit,
    )
}

/**
 * 「长按延迟」那一行 —— 多久算长按。
 *
 *  说明里点一句「比它短算单击」：按不出来时用户第一个怀疑的就是这个数。到这一句为止 ——
 *  写全「这一档已短于人一次自然按压」那种话太长，会把行撑成两行、与旁边几行的说明也不齐。
 *
 *  它的 `enabled` 由 [LongPressSpec.delayEnabled] 单独给：同一张卡里上面几行灰着、它独自亮着，
 *  会让人以为「这行还能改 ⇒ 上面那几行也许还能点」（用户报的）。
 */
@Composable
private fun SettingsPage.LongPressDelayRow(
    position: CardListPosition,
    value: Int,
    enabled: Boolean,
    onCommit: (Int) -> Boolean,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_TITLE, "长按延迟"),
                summary = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_DESC, "多久算长按，比它短算单击"),
                icon = PageIcons.DelayHold,
                min = KeyConfig.MIN_LONG_PRESS_MS,
                max = KeyConfig.MAX_LONG_PRESS_MS,
                value = value,
                default = KeyConfig.DEFAULT_LONG_PRESS_MS,
                enabled = enabled,
            ),
        onCommit = onCommit,
    )
}
