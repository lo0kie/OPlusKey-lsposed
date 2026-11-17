package com.lookie.opluskey.ui.row

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.STEP_MS
import com.lookie.opluskey.ui.model.text.durationText
import io.github.suqi8.coui.kmp.basic.CardListPosition
import kotlin.math.roundToInt

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
    /**
     * 四族全摆 —— 「按键绑定」页的长按档、「单击后长按」那一档、以及「组合键」页要的那一套
     * （行序见 [LongPressSettingsCard]）。
     *
     *  「单击后长按」曾经单独有一档（只摆后三族、不摆「松手时触发」），那一版把这一位拿掉的理由是
     *  「那一档本来就是按住之后松手才结束」—— 真机上它就是一行有用的时序：按住到点先不执行、
     *  抬手再跑，与长按那一档逐字同义（用户口径「和长按一样的逻辑」）。
     */
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
    /**
     * 这一档**本身**有没有动作（长按 / 单击后长按）。
     *
     *  为 `false` 时「松手执行额外动作」那一族整族焊死：那一条是「这一档执行完之后」的收尾，
     *  这一档本身什么都不做时，松手再补一个动作就没有主语（用户口径「设为无操作时禁用」）。
     */
    val releaseAvailable: Boolean = true,
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

    /** 「松手执行额外动作」能不能拨 —— 「松手才触发」开着时它没有意义；这一档本身无操作时也没有。 */
    val releaseEnabled: Boolean = live && !spec.fireOnRelease && spec.releaseAvailable

    /** 「松手执行额外动作」灰着被点一下时说的话（`null` = 用整页那句）。 */
    val releaseNeedsActionHint: String? =
        if (spec.enabled && !spec.releaseAvailable) {
            I18n.get(Strings.BINDING_RELEASE_NEEDS_ACTION)
        } else {
            null
        }

    /** 连续触发那一族能不能点 —— 三种情形：整张卡被闸住 / 「松手才触发」占着时序 / 动作本身不支持连发。 */
    val repeatEnabled: Boolean =
        live && !spec.fireOnRelease && spec.repeatBlockedReason == null

    /** 「长按延迟」那一行。 */
    val delayLive: Boolean = spec.delayEnabled

    /** 「松手才触发」灰着被点一下时的说法（`null` = 用整页那句）。 */
    val fireHint: String? =
        if (spec.enabled && releaseBound) {
            I18n.get(Strings.BINDING_RELEASE_UNSUPPORTED_FIRE)
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

            //  这一档本身无操作：说「先给它配一个动作」，比说页面闸门有用
            !spec.releaseAvailable -> {
                releaseNeedsActionHint
            }

            !spec.fireOnRelease -> {
                null
            }

            //  这一页不摆「松手时触发」那一行（见 `LongPressSections`），所以得指路；
            //  摆了的（按键 / 组合那一页）只说结论 —— 那一行就在上面，抬头就看得见。
            spec.sections == LongPressSections.ReleaseOnly -> {
                I18n.get(Strings.BINDING_FIRE_ON_RELEASE_UNSUPPORTED_RELEASE_KEY)
            }

            else -> {
                I18n.get(Strings.BINDING_FIRE_ON_RELEASE_UNSUPPORTED_RELEASE)
            }
        }

    /**
     * 连续触发那一族的**置灰原因** —— 三个来源，按「越靠前越该先看到」排：
     * 整张卡的原因、「松手才触发」、动作本身不支持连发（[LongPressSpec.repeatBlockedReason]）。
     * 「松手执行额外动作」**不在**其中：它与连续触发不冲突（见 [LongPressGates] 的类注释）。
     */
    val repeatBlocked: String? =
        spec.blockedReason
            ?: when {
                spec.fireOnRelease -> {
                    I18n.get(Strings.BINDING_FIRE_ON_RELEASE_UNSUPPORTED_REPEAT)
                }

                else -> {
                    spec.repeatBlockedReason
                }
            }
}

/**
 * 「长按」那一档的**唯一**一张卡：松手才触发 + 松手执行额外动作（+ 额外动作）+ 长按连续触发
 * （+ 每次触发都振动 + 连续触发间隔）+ 长按延迟。
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
 *
 *  **这张卡的行一律不画副标题**（用户口径）：开关两档说的是「开 / 关各是什么意思」、滑杆那两行
 *  说的是「这一根在量什么」，都是解释性的话，标题已经说得清。唯一的例外是「额外动作」那一行 ——
 *  它的副标题是**取值**（现在配的是哪个动作），不是解释。
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
        addAll(lowerRows(spec, gates))
    }

/**
 * 「松手执行额外动作 + 连发 + 长按延迟」那三族 —— 全摆那一形态与「单击后长按」共用这一份。
 *
 *  收成一支是为了让「摆哪几族」只有一处行序：两个调用点各写一遍的话，
 *  「先振动后间隔」这种细节迟早只在其中一边生效。
 */
private fun SettingsPage.lowerRows(
    spec: LongPressSpec,
    gates: LongPressGates,
): List<CardRow> =
    buildList {
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
 *
 *  **这一张卡的行一律不画副标题**（用户口径）：开关的两种状态、滑杆那一档问的是什么，
 *  都由标题自己说得清，多一行解释只是把卡片撑高。
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
                title = I18n.get(Strings.BINDING_LONG_PRESS_FIRE_ON_RELEASE_TITLE),
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
 *
 *  开关那一行**不画副标题**（与这张卡其它行同一条）；「额外动作」那一行的副标题**留着** ——
 *  那不是解释，是这一行的**取值**（现在配的是哪个动作），去掉之后这一行就只剩一个标题。
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
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE),
                icon = PageIcons.LongRelease,
                checked = spec.releaseOn,
                enabled = gates.releaseEnabled,
                disabledHint = gates.releaseHint,
                onCheckedChange = spec.onReleaseToggle,
            ),
            CardRow.Item(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_ACTION),
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
 * 连续触发那一族：开关 + 「每次触发都振动」+「连续触发间隔」。
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
        //  三个来源收在 [LongPressGates.repeatBlocked] 一处。
        //
        //  **它只落在 `disabledHint` 上，不再顶替副标题**（用户口径：这张卡的行不画副标题）：
        //  灰着被点一下照样能听见原因，而亮着时那一行不需要一句「为什么能开」。
        CardRow.Switch(
            title = I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE),
            icon = PageIcons.LongRepeat,
            checked = spec.repeat,
            enabled = gates.repeatEnabled,
            //  灰着被点一下也要说清为什么（与「选择应用」那几页的禁用行同一条口径）
            disabledHint = gates.repeatBlocked,
            onCheckedChange = spec.onRepeat,
        ),
        CardRow.Switch(
            title = I18n.get(Strings.BINDING_REPEAT_VIBRATE_TITLE),
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
 * 「连续触发间隔」那一行 —— 长按连续触发隔多久重放一次。
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
                title = I18n.get(Strings.BINDING_REPEAT_INTERVAL_TITLE),
                icon = PageIcons.DelayRepeat,
                min = KeyConfig.MIN_REPEAT_INTERVAL_MS,
                max = KeyConfig.MAX_REPEAT_INTERVAL_MS,
                value = value,
                default = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                //  取值 10ms 一档（50~500 之间 46 档）；**拖动落点吸到 50 的整数倍** ——
                //  46 档铺在一根轨道上拖不准，粗拖 + 手输才是这一行的用法（见 `SliderSpec.snapDrag`）
                step = 10,
                dragStep = 50,
                enabled = enabled,
                disabledHint = disabledReason,
            ),
        onCommit = onCommit,
    )
}

/**
 * 「长按延迟」那一行 —— 多久算长按。
 *
 *  **不画副标题**（与这张卡其它行同一条）：标题已经把这一行问的是什么说完了，行尾那个
 * 「320 毫秒」才是要看的数。
 *
 *  它的 `enabled` 由 [LongPressSpec.delayEnabled] 单独给：同一张卡里上面几行灰着、它独自亮着，
 *  会让人以为「这行还能改 ⇒ 上面那几行也许还能点」（用户报的）。
 *
 *  @param disabledReason 灰着被点一下时的说法。**走 `SliderSpec.disabledHint` 那条通道** ——
 *   灰着不动时不占版面，点一下才出那句话。
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
                title = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_TITLE),
                icon = PageIcons.DelayHold,
                min = KeyConfig.MIN_LONG_PRESS_MS,
                max = KeyConfig.MAX_LONG_PRESS_MS,
                value = value,
                default = KeyConfig.DEFAULT_LONG_PRESS_MS,
                //  取值 10ms 一档（200~1000 之间 80 档）；**拖动落点吸到 50 的整数倍** ——
                //  80 档铺在一根轨道上拖不准，粗拖 + 手输才是这一行的用法（见 `SliderSpec.snapDrag`）
                step = 10,
                dragStep = 50,
                enabled = enabled,
                disabledHint = disabledReason,
            ),
        onCommit = onCommit,
    )
}

/**
 * 一根滑杆的**全部规格**：显示什么（标题 / 摘要 / 行首图标）+ 量什么（区间、当前值、出厂值、步长）。
 *
 *  合成一个类型是为了让 [SettingsPage.SliderRow] 的参数表停在一只手数得过来的量级 ——
 *  这些本来就是一件事（「这一行是一根什么样的滑杆」），一起给比散在五个形参里更好读。
 *
 * ## 一条区间，两套刻度
 *
 *  · **取值刻度**（[step] / [tickStep] / [snap]）：值能落在哪几格 —— 手动输入、写盘、输入窗那句
 *    「步进」都用它；
 *  · **拖动刻度**（[dragStep] / [snapDrag]）：手指滑一下跨多宽 —— 拖动落点由它吸。
 *
 *  两者**由调用点各自决定**，默认是同一个数。想「拖粗一点、精确值靠手输」的，就在那一行把
 *  [dragStep] 传大一些 —— 取值刻度不受影响，写下去、手输吸、输入窗报步进全都还按 [step]。
 *  这里故意不做「按区间自动稀疏化」那一套：一根滑杆该分几档是手感，不是能算出来的东西。
 *
 * @param step 取值刻度：相邻两格的目标步长。区间不整除时真实档距是均分出来的（见 [tickStep]），
 *   提示里报的是真实那一档。
 * @param dragStep 拖动刻度：拖动落点取它的**整数倍**（见 [snapDrag]），默认与 [step] 相同。
 *   比 [step] 大就是「粗拖」—— 拖出来的值仍会在提交前被 [snap] 吸回取值刻度。
 * @param enabled 这一根滑杆能不能动
 * @param disabledHint 灰着被点一下时**这一行自己的说法**（`null` = 用整页那句，见 [DisabledHint]）。
 *   与 `CardRow.Item.disabledHint` / `CardRow.Switch.disabledHint` 同一个意思、同一套判据：
 *   整页那条答的是「这一页为什么改不了」（主开关 / 链路 / 只读），有些滑杆是**因为它自己
 *   这一行的条件不够**才灰的（「使用独立设置」没开、长按动作不支持连发、排序中整页锁住），
 *   拿整页那句回答它，等于答了另一个问题。只在 `enabled = false` 时生效。
 * @param valueText 主标题**右侧**那个「当前值」怎么写（毫秒那几档默认走 [durationText]）；
 *   `null` = **不画**（档位那种一眼看得出当前值的滑杆，再写一遍是重复）
 * @param keyPoints 要在轨道上**画出来的**刻度点；`null` = 只画出厂默认值那一个圆点。
 *   库那边 `showKeyPoints` 单独打开不画东西 —— 它读的是 `keyPoints`，两者要一起给
 *   （见 COUI Slider 文档：`showKeyPoints` 仅当 `keyPoints` 不为 null 时有效）。
 */
internal data class SliderSpec(
    val title: String,
    /** 副标题（`null` = 这一行不画副标题，只留标题与滑杆）。 */
    val summary: String? = null,
    val icon: ImageVector,
    val min: Int,
    val max: Int,
    val value: Int,
    val default: Int,
    val step: Int = STEP_MS,
    /** 拖动那一套刻度，见类注释的「两套刻度」；不传 = 与 [step] 同一套。 */
    val dragStep: Int = step,
    val enabled: Boolean = true,
    val disabledHint: String? = null,
    val valueText: ((Int) -> String)? = { durationText(it) },
    val keyPoints: List<Float>? = null,
) {
    /** 把任意值**吸到这一根滑杆的取值刻度上** —— 手动输入那一档与提交前那一步都用它（见 [snapToStep]）。 */
    fun snap(raw: Int): Int = snapToStep(raw, min, max, step)

    /**
     * 把**拖动**的落点吸到 [dragStep] 上 —— 取它的**整数倍**（绝对值，不是从区间起点数）。
     *
     *  为什么是绝对倍数：拖的时候看到的是「50 / 100 / 150」，而不是「20 / 70 / 120」那种从下限
     *  起算的数列，更不是「175 / 278」这种怪数 —— 后者是「区间 ÷ 档数」均分出来的（980 ÷ 50 除不尽，
     *  `Slider.steps` 那条路只能均分）。出厂值（四格窗口的 150）正好落在这一套刻度上，
     *  拖回默认值不会停在旁边的怪数上。
     *
     *  两端夹回 [min] / [max]：区间下限自己不在刻度上时（20），拖到最左仍取得到 20。
     */
    fun snapDrag(raw: Int): Int {
        val lo = minOf(min, max)
        val hi = maxOf(min, max)
        val s = dragStep.coerceAtLeast(1)
        return ((raw.coerceIn(lo, hi).toFloat() / s).roundToInt() * s).coerceIn(lo, hi)
    }

    /**
     * 取值真实的一档多宽 —— 输入窗那句「步进」与 [snap] 用它，不用 [step]。
     *
     *  **提示与真实档距必须同源**：区间不整除时，真实一档是「区间 ÷ 档数」均分出来的，
     *  与参数里的 [step] 不是同一个数。各说各的时输入窗写着「步进 50」、实际一档却是 51。
     */
    val tickStep: Int get() = realStep(min, max, step)
}

/** 区间按 [step] 均分之后，真实的一档多宽（区间不整除时不是 [step]）。 */
private fun realStep(
    min: Int,
    max: Int,
    step: Int,
): Int {
    val lo = minOf(min, max)
    val hi = maxOf(min, max)
    val span = (hi - lo).coerceAtLeast(1)
    return span.toDouble().div(sliderTickCount(lo, hi, step)).roundToInt()
}

/**
 * 两端之间分几档（含两端）—— 全项目唯一一份，`Slider.steps` 与 [snapToStep] 都从这里取。
 *
 *  `step` 为 0 时夹到 1（否则除法直接抛 ArithmeticException，崩在组合期）。
 */
internal fun sliderTickCount(
    min: Int,
    max: Int,
    step: Int,
): Int = ((max - min).coerceAtLeast(1) / step.coerceAtLeast(1)).coerceAtLeast(1)

/**
 * 把任意值吸到**刻度**上：两端之间分 `(max-min)/step` 格（与库 `Slider.steps` 同一套，
 * `steps` 不含两个端点）。区间不整除时按格数均分，不是拿 `step` 硬乘 —— 那两套值会差一格。
 *
 *  两个入口共用：`tapSliderToSeek` 把「点在轨道上的相对位置」折成值，以及**手动输入**把用户
 *  打的数吸回档位上。后者是新加的那一档：不吸的话配置里会存进 260 这种「不在档位上」的数，
 *  滑杆画出来滑块夹在两档之间 —— 看着就像 step 失效了。
 */
internal fun snapToStep(
    value: Int,
    min: Int,
    max: Int,
    step: Int,
): Int {
    //  **上下限反了不能抛**：`coerceIn(min, max)` 在 `min > max` 时直接抛
    //  `IllegalArgumentException`，而这两个数一路来自配置 / 调用方算出来的边界 ——
    //  一处算错就是整页崩。交换一下继续算：它们描述的是同一个区间。
    val lo = minOf(min, max)
    val hi = maxOf(min, max)
    val span = (hi - lo).coerceAtLeast(1)
    //  档数与 `Slider.steps` 同一处（见 [sliderTickCount]）：两边各算一份时会在「区间不整除」时差一档。
    val ticks = sliderTickCount(lo, hi, step)
    val idx = ((value.coerceIn(lo, hi) - lo).toFloat() / span * ticks).roundToInt()
    return (lo + idx * span.toDouble() / ticks).roundToInt().coerceIn(lo, hi)
}
