package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.model.PageIcons
import io.github.suqi8.coui.kmp.basic.CardListPosition

// 「长按」那一档的设置卡 —— 单键页与组合键页**共用这一张**。

/**
 * 这张卡摆**哪几族**行 —— 四族（时序 / 松手额外动作 / 连发 / 长按延迟）不是每种页面都该摆。
 *
 *  另外三族的参数**全部归这颗键**，而且是在**按下那一刻**就被注入侧读走、用来排长按枪与连发
 *  的（见 `GestureDetector` 的 `longPressAt` / `scheduleRepeat`）—— 它们不认识场景条件，
 *  场景块也就改不动它们。摆在场景块的动作页上，改的是整颗键：换一个条件进去看到的还是同一个值，
 *  读起来像「这一块单独配过」（用户报的）。
 */
internal enum class LongPressSections {
    /** 四族全摆 —— 「按键绑定」页与「组合键」页要的那一套（行序见 [LongPressSettingsCard]）。 */
    All,

    /**
     * 只摆「松手执行额外动作」那一族 —— 「场景动作」页。
     *
     *  它为什么留得住：这一族虽然也写在**这颗键**身上（`KeyConfig.longRelease`），但它是
     *  「这一次按压的收尾」，与这一块挑的动作是同一件事的两半；另外三族是纯粹的判定阈值，
     *  与「这一块做什么」无关。**「松手时触发」那一行不摆但照读** —— 它开在这颗键上时，
     *  这一族就得灰着（见 [LongPressGates.releaseEnabled]）。
     */
    ReleaseOnly,
}

/**
 * 一次组合要的现行值、三档时序的互斥结论所需的那几位，以及四条写回去的路。
 *
 * 为什么要有这一份：这张卡在「按键绑定」的长按档与「组合键」的某一条上各画一次，**行序、文案、
 * 可见性判据必须一模一样**（用户口径「和另一个一样」）。所以「画哪些行」只有这一份，两页只答
 * 两件事：读谁的字段、写完落到哪一条写盘说明（`onRepeat` / `onVibrate` / `onInterval` /
 * `onPressMs` 只把值递出去，写盘仍在各自的页里）。
 *
 * @param enabled 上面那几行能不能点（各自还要看与别人是否互斥）
 * @param delayEnabled 「长按延迟」那一行能不能点 —— 它比上面几行多一道闸：单键页还看首页那个主开关
 * @param sections 摆哪几族行（[LongPressSections]）—— 「场景动作」页只摆松手那一族
 * @param blockedReason 非 null ⇒ **整张卡**置灰（主开关关 / 链路没通 / 配置只读）—— 只放整页级的原因
 * @param repeatBlockedReason 非 null ⇒ 只有「连续触发」那一族置灰：这一档的长按动作**自己**不支持
 *   连发（多条命令 / 灵感语音）。它**不连坐**时序两行与长按延迟 —— 那三项跟连不连发无关
 * @param onInterval / @param onPressMs 返回 `false` ⇒ 这一档没落盘（滑杆拨回真实值）
 * @param fireOnRelease 「松手才触发」—— 长按动作本身等抬手才执行（与连续触发、松手执行额外动作互斥）
 * @param onFireOnReleaseToggle 拨「松手才触发」
 * @param releaseSupported 这一页的判定侧有没有「松手」这条通道 —— 两行都靠它（组合键那一页没有，
 *   见 `ComboLongCard` 的说明），没有就**一行都不画**：摆一个拨了没反应的开关比没有更糟
 * @param releaseOn 开关；为真时下面滑入「额外动作」那一行
 * @param releaseLabel 这一档现在的动作名（**永远有值**：「无操作」也是名字，见下）
 * @param releaseIcon 那个动作自己的图标（**永远有值**，`actionIcon(token)` 给的）
 * @param releaseBound 松手那一档此刻**真的有事可做** —— 直接递 `KeyConfig.releaseBound`，
 *   界面不重算这条规则（互斥只在数据层有一份，见 `KeyConfig.releaseBound`）
 * @param onReleaseToggle 拨开关
 * @param onReleasePick 点「额外动作」那一行：打开动作清单
 */
@Suppress("LongParameterList")
internal class LongPressSpec(
    val repeat: Boolean,
    val vibrate: Boolean,
    val intervalMs: Int,
    val pressMs: Int,
    val fireOnRelease: Boolean,
    val releaseSupported: Boolean,
    /** 「松手执行额外动作」的开关 —— 为真时下面滑入「额外动作」那一行。 */
    val releaseOn: Boolean,
    val releaseLabel: String,
    val releaseIcon: ImageVector,
    val releaseBound: Boolean,
    val enabled: Boolean,
    val delayEnabled: Boolean,
    /** 摆哪几族行 —— 见 [LongPressSections]。 */
    val sections: LongPressSections,
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
 * 长按这一档的**置灰结论** —— 哪一行亮、哪一行灰、灰着那一行说什么，只在这里算一次。
 *
 * 为什么单开一个类而不是写在卡片里：两个时序开关互相牵制（松手才触发 ⇄ 松手执行额外动作），
 * 写在一处才看得出这件事，卡片自己只管把它们摆成行。连续触发那一族除了整页闸门与「动作本身
 * 不支持连发」，只被「松手才触发」挡着 —— 与「松手执行额外动作」是同一按的两个阶段，可以同时开着。
 * `enabled = false` 的行**一般**不给 `disabledHint` —— 那时该说的是**整页**为什么改不了
 * （链路 / 只读），不是这一行自己的事。唯一的例外是 [LongPressSections.ReleaseOnly] 那一形态：
 * 整页那条回退读的是「上一次看过的那颗键」，在这一页上会答错，所以它改说 [LongPressSpec.blockedReason]
 * （见 [releaseHint]）。
 *
 * 判据与数据侧同一口径（见 `KeyConfig.releaseBound`）：**那条规则不在
 * 这里重算** —— `releaseBound` 由调用点从配置里取来递进 `LongPressSpec`，这一层只负责把它翻译成
 *  「哪一行灰 / 灰着说什么」。
 */
private class LongPressGates(
    private val spec: LongPressSpec,
) {
    /** 松手那一档**真的有事可做**（`KeyConfig.releaseBound` 原样递进来）。 */
    val releaseBound: Boolean = spec.releaseBound

    /**
     * 整张卡此刻能不能点 —— 只看页面闸门（主开关 / 链路 / 只读）。
     */
    private val live: Boolean = spec.enabled

    /** 「松手才触发」能不能拨 —— 松手那一档占着时它得让位（先关掉那个）。 */
    val fireEnabled: Boolean = live && !releaseBound

    /** 「松手执行额外动作」能不能拨 —— 「松手才触发」开着时它没有意义。 */
    val releaseEnabled: Boolean = live && !spec.fireOnRelease

    /** 连续触发那一族能不能点 —— 三种情形：整张卡被闸住 / 「松手才触发」占着时序 / 动作本身不支持连发。 */
    val repeatEnabled: Boolean =
        live && !spec.fireOnRelease && spec.repeatBlockedReason == null

    /** 「长按延迟」那一行。 */
    val delayLive: Boolean = spec.delayEnabled

    /** 「松手才触发」灰着被点一下时的说法（`null` = 用整页那句）。 */
    val fireHint: String? =
        if (spec.enabled && releaseBound) {
            I18n.get(Strings.BINDING_RELEASE_UNSUPPORTED_FIRE, "先关掉 [松手执行额外动作]")
        } else {
            null
        }

    /**
     * 「松手执行额外动作」灰着被点一下时的说法。
     *
     *  三种来源，按「越靠前越该先看到」排：整张卡被闸住 → 说**这一页**为什么改不了；
     *  「松手才触发」占着这一族 → 两种说法，按这张卡摆没摆那一行分（见下）。
     *
     *  第一种**只在 [LongPressSections.ReleaseOnly] 取**：那一形态里没有别的行会读到
     *  [LongPressSpec.blockedReason]（`All` 那一形态由连发那一族说，见 [LongPressGates.repeatBlocked]），
     *  而整页那条回退（`disabledClickReason`）读的是 `KeyWatchState.editing` —— 在场景动作页上
     *  它指的是**上一次在按键绑定页看过的那一颗**，于是点这一行会冒出「『音量+』已停用」这种
     *  与眼前无关的话（真机口径「点它回落到上一条文案」）。
     */
    val releaseHint: String? =
        when {
            !spec.enabled -> {
                if (spec.sections == LongPressSections.ReleaseOnly) spec.blockedReason else null
            }

            !spec.fireOnRelease -> {
                null
            }

            //  这一页不摆「松手时触发」那一行（见 `LongPressSections`），所以得指路；
            //  摆了的（按键 / 组合那一页）只说结论 —— 那一行就在上面，抬头就看得见。
            spec.sections == LongPressSections.ReleaseOnly -> {
                I18n.get(
                    Strings.BINDING_FIRE_ON_RELEASE_UNSUPPORTED_RELEASE_KEY,
                    "这颗键已设为 [松手时触发]，去按键绑定里关掉",
                )
            }

            else -> {
                I18n.get(Strings.BINDING_FIRE_ON_RELEASE_UNSUPPORTED_RELEASE, "长按已改为松手时触发")
            }
        }

    /**
     * 连续触发那一族的副标题 / 置灰原因 —— 三个来源，按「越靠前越该先看到」排：
     * 整张卡的原因、「松手才触发」、动作本身不支持连发（[LongPressSpec.repeatBlockedReason]）。
     * 「松手执行额外动作」**不在**其中：它与连续触发不冲突（见 [LongPressGates] 的类注释）。
     */
    val repeatBlocked: String? =
        spec.blockedReason
            ?: when {
                spec.fireOnRelease -> {
                    I18n.get(Strings.BINDING_FIRE_ON_RELEASE_UNSUPPORTED_REPEAT, "松手时触发，连续触发不生效")
                }

                else -> {
                    spec.repeatBlockedReason
                }
            }
}

/**
 * 「长按」那一档的**唯一**一张卡：松手才触发 + 松手执行额外动作（+ 额外动作）+ 长按连续触发
 * （+ 连续触发时振动 + 触发间隔）+ 长按延迟。
 *
 *  行序：**「松手才触发」在最上面**（它管的是「什么时候执行」，下面两族管的是「执行几次 /
 *  松手还做不做别的」）；紧跟「松手执行额外动作」那一族，再是「长按连续触发」那一族；
 *  「长按延迟」落到最后一行。
 *
 *  「松手才触发」与另外两族互斥：开着它，连发与「松手执行额外动作」都**置灰但保留**
 *  （用户口径：不要藏起来）—— 藏掉等于让人以为自己没配过它，而它只是这一刻不生效；值也原样留着。
 *  「松手执行额外动作」与「长按连续触发」**不互斥**：按住期间照常连发，松手再补一条。
 *
 *  摆哪几族由 [LongPressSpec.sections] 定：「场景动作」页只摆松手那一族（理由见
 *  [LongPressSections]），行本身的文案与判据仍是下面那两支 —— 这一处只回答「摆哪几族」。
 */
@Composable
internal fun SettingsPage.LongPressSettingsCard(spec: LongPressSpec) {
    val gates = LongPressGates(spec)
    CardList(
        rows =
            when (spec.sections) {
                LongPressSections.All -> allRows(spec, gates)
                LongPressSections.ReleaseOnly -> releaseRows(spec, gates)
            },
    )
}

/** 四族全摆 —— 「按键绑定」与「组合键」两页要的那一套，行序见 [LongPressSettingsCard]。 */
private fun SettingsPage.allRows(
    spec: LongPressSpec,
    gates: LongPressGates,
): List<CardRow> =
    buildList {
        addAll(timingRows(spec, gates))
        addAll(releaseRows(spec, gates))
        addAll(repeatRows(spec, gates))
        add(
            CardRow.Custom { position ->
                LongPressDelayRow(position, spec.pressMs, gates.delayLive, null, spec.onPressMs)
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
                title = I18n.get(Strings.BINDING_LONG_PRESS_FIRE_ON_RELEASE_TITLE, "松手时触发"),
                summaryOn = I18n.get(Strings.BINDING_LONG_PRESS_FIRE_ON_RELEASE_DESC_ON, "按满时长先不执行，抬手时执行"),
                summaryOff = I18n.get(Strings.BINDING_LONG_PRESS_FIRE_ON_RELEASE_DESC_OFF, "达到长按时长即立刻执行"),
                icon = PageIcons.LongFireOnRelease,
                checked = spec.fireOnRelease,
                enabled = gates.fireEnabled,
                disabledHint = gates.fireHint,
                onCheckedChange = spec.onFireOnReleaseToggle,
            ),
        )
    }

/**
 * 「松手执行额外动作」那一族：一个开关 + 开关为真时滑入的「额外动作」一行。
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
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE, "松手执行额外动作"),
                summaryOn = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_DESC_ON, "松手时再执行一个动作"),
                summaryOff = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_DESC_OFF, "松手不再执行别的"),
                icon = PageIcons.LongRelease,
                checked = spec.releaseOn,
                enabled = gates.releaseEnabled,
                disabledHint = gates.releaseHint,
                onCheckedChange = spec.onReleaseToggle,
            ),
            CardRow.Item(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_ACTION, "额外动作"),
                //  这一行**永远写动作名**，包括「无操作」—— 后者是这一档的默认值，不是「还没配」。
                //  原来未配时写「未设置（松手不做事）」，与「无操作」是同一个意思却两种说法，
                //  还得为「未设置」单独准备一颗图标（那颗原先借了网格里「系统按键」的，撞了）。
                summary = spec.releaseLabel,
                //  图标**跟着动作走**，所有状态都有：无操作是 `ban`、应用是应用类、自定义命令是终端。
                //  与网格里对应那一格同源（`actionIcon`）—— 同一件事在两处不该长两张脸。
                icon = spec.releaseIcon,
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
            RepeatIntervalRow(position, spec.intervalMs, gates.repeatEnabled, gates.repeatBlocked, spec.onInterval)
        },
    )

/**
 * 「触发间隔」那一行 —— 长按连续触发隔多久重放一次。
 *
 *  单独一支只为让 [LongPressSettingsCard] 留在 detekt 的长度上限之内（它自己也要能被 `visible` 藏起来）。
 *
 *  @param disabledReason 灰着被点一下时的说法 —— 与上面那两行**同一句**
 *   （[LongPressGates.repeatBlocked]：整页闸门 / 松手才触发 / 动作不支持连发，三者之一）
 */
@Composable
private fun SettingsPage.RepeatIntervalRow(
    position: CardListPosition,
    value: Int,
    enabled: Boolean,
    disabledReason: String?,
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
                disabledHint = disabledReason,
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
 *
 *  @param disabledReason 灰着被点一下时的说法。**走 `SliderSpec.disabledHint` 那条通道**，
 *   不再拿副标题顶替 —— 副标题是「这一行是什么」，用它答「为什么点不了」会把原来的说明挤掉，
 *   而且灰着不动时它一直在，读起来像这一行本来就长这样。
 */
@Composable
private fun SettingsPage.LongPressDelayRow(
    position: CardListPosition,
    value: Int,
    enabled: Boolean,
    disabledReason: String?,
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
                disabledHint = disabledReason,
            ),
        onCommit = onCommit,
    )
}
