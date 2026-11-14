package com.lookie.opluskey.ui.screen.combo

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.present.COMBO_PAIR_SEPARATOR
import com.lookie.opluskey.core.present.comboKeyNames
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.row.CHIP_SLOT
import com.lookie.opluskey.ui.row.chipsOfNames
import com.lookie.opluskey.ui.row.namesToChipsTemplate
import com.lookie.opluskey.ui.screen.binding.ComboModeAsk
import com.lookie.opluskey.ui.screen.binding.ComboStarterBlocked
import com.lookie.opluskey.ui.state.binding.KeysAsk
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.write.write

// 「组合键」那两页共用的读写 —— 列表页（加 / 删 / 摘要）与编辑页（标题上那行成员名）都走这里。

/**
 * 一组组合键最多几档 —— 九档（用户口径）。
 *
 *  列表行首用数字图标标「第几组」（1~9）：上限跟着那排数字走，界面与模型不会各说一个数
 *  （`PageIcons.menuKeyNumber` 只到 9 —— 图标库的圈数字到这里为止，不为第 10 档手绘）。
 *
 *  为什么不再往下放：一组组合要占两格动作（短按 + 长按），九组已是一整页网格的量；
 *  再多用户自己也记不住哪一组是什么，列表行与数字图标都失去意义。
 */
internal const val MAX_COMBOS = 9

/**
 * 一组键最多能有几种触发方式 —— **等待一档 + 每个成员各当一次首键**。
 *
 *  回执里「N 种触发方式都已添加」那个 N 就是它。成员越多，按住那一类能配的档越多：
 *  三颗时是「等待 + 按住 A + 按住 B + 按住 C」四种。所以它是个函数，不是一个常量 ——
 *  写死成 3 的话，三颗的组合补到第三档就会说「都已添加」，而其实还差一档。
 */
internal fun comboModeTotal(keyCount: Int): Int = 1 + keyCount.coerceAtLeast(0)

/**
 * 按住那一档那句话的**模板** —— 「按住 [首键] 后再按 [后续…]」。
 *
 *  后续成员**每颗各占一枚名字框**、之间用 [COMBO_PAIR_SEPARATOR] 连起来（与等待组合的
 *  `A × B` 同一个分隔符）。成员数不定，所以这是一支函数而不是一条写死的文案：
 *  模板里的槽数必须与调用点传进来的名字数一致，否则多出来的那几颗会被静默丢掉
 *  （`HintText.plain()` 只按顺序填）。
 *
 *  文案本身只写 `%1$s` / `%2$s` 两个段：首键一段、后续那一段再按颗数展开。
 *
 * @param followers 后续成员的个数（按住档 = `keys.size - 1`）
 */
internal fun holdSentenceTemplate(followers: Int): String {
    val slots = List(followers.coerceAtLeast(1)) { CHIP_SLOT }.joinToString(" $COMBO_PAIR_SEPARATOR ")
    return I18n
        .get(Strings.COMBO_MODE_HOLD, "按住 %1\$s 后再按 %2\$s")
        .replace("%1\$s", CHIP_SLOT)
        .replace("%2\$s", slots)
}

/**
 * 等待组合那句话的模板 —— 成员连起来（`A × B`），后面接同一句「后等另一颗」。
 *
 *  与 [holdSentenceTemplate] 同一个形状：文案里只有一个 `%s`（那一串成员），按颗数展开。
 *  原先那句写的是「按下 %s 或 %s」—— 三颗时第三个名字会被丢掉，而「或」在两颗以上也不对
 *  （它们是**一起**按的，不是二选一）。
 */
internal fun waitSentenceTemplate(count: Int): String {
    val slots = List(count.coerceAtLeast(1)) { CHIP_SLOT }.joinToString(" $COMBO_PAIR_SEPARATOR ")
    return I18n.get(Strings.COMBO_MODE_WAIT, "按下 %s 后等另一颗").replace(CHIP_SLOT, slots)
}

//  `COMBO_PAIR_SEPARATOR` / `comboLabel` / `keyName` 已挪到 `core/model/KeyNames.kt`：
//  除了 UI（`ui/write/BindingTarget.kt` 的停用提示、顶栏副标题、列表行），**执行时提示**
//  （`host/GestureExec.kt`）也要念键名，而 `host` 不许认识 `ui`（那条边由
//  `tools/audit-coupling.py` 核）—— 一份实现要给两侧共用，只能落在两边都许引的 `core`。

/**
 * 这组键在**这一类里**还排得下吗 —— 等待组合一档、按住组合**每个成员各一档**（谁当首键）。
 *
 *  列表页把它拆成两列（等待 / 按住），添加时类别就已经定下，所以「还能不能再加一档」
 *  只在**同一类内部**问：另一类里的档不算占位。
 *
 *  这是「添加」按钮按不按得动的唯一判据（`KeysScreen` 的选键窗与 [addCombo] 共用）。
 *  按「具体哪一档已经有人」判（[waitTaken] / [usedComboStarters]），不数条数 ——
 *  同一档重复两次是废配置，数条数会把它当成「两档都占了」。
 */
internal fun comboRoomLeft(
    cfg: Config,
    ids: List<String>,
    hold: Boolean,
): Boolean =
    if (hold) {
        //  还有没当过首键的成员 ⇒ 这一类就还排得下
        val used = usedComboStarters(cfg, ids)
        ids.any { it !in used }
    } else {
        !waitTaken(cfg, ids)
    }

/**
 * [comboRoomLeft] 的反面 —— 这一类里的触发方式**已经全占满了**。
 *
 *  单独起个名字只为读起来顺：被拦住时要说哪一句由它决定（见 `KeysScreen` 的选键窗与
 *  [writeNewCombo]）—— 按住那一类里每个成员都当过首键时说「所有触发方式都已添加」，
 *  而不是笼统的「这组按键已经添加过了」：后者读起来像整组都加过，而另一类往往还能加。
 */
internal fun comboAllModesTaken(
    cfg: Config,
    ids: List<String>,
    hold: Boolean,
): Boolean = !comboRoomLeft(cfg, ids, hold)

/**
 * 这一组键的**等待档**在不在 —— 同一批成员的等待档只可能有一档。
 *
 *  与 [usedComboStarters] 同源、分两个函数：一类一档、一类按成员各一档，
 *  合成一个「返回集合」的接口反而要在调用点各拆一次。
 */
internal fun waitTaken(
    cfg: Config,
    ids: List<String>,
): Boolean {
    val want = ids.toSet()
    return cfg.combos.any { it.isWait && it.keys.toSet() == want }
}

/**
 * 这一组键里**已经当过首键**的那些成员（按住档）—— 选择窗据此把用不上的那几行置灰。
 *
 *  只算**这一批成员完全一样**的档：同一批键的等待档只有一档、按住档则是「每个成员各一档」。
 *  判等用**集合**：按住档的 `keys` 第一颗是首键、顺序有含义，但「是不是同一批成员」仍只看集合 ——
 *  首键之差由返回值本身区分（返回的是首键 id）。
 */
internal fun usedComboStarters(
    cfg: Config,
    ids: List<String>,
): Set<String> {
    val want = ids.toSet()
    return cfg.combos
        .filter { it.isHold && it.keys.toSet() == want }
        .mapNotNullTo(HashSet()) { it.starterKey }
}

/**
 * 这一组键的那一档选完之后，**这一组键一共占了几档**（含正要加的这一档）。
 *
 *  回执用它决定说「已添加 …」还是「%d 种触发方式都已添加 …」：同一组键的档数上限是
 *  「等待 + 每个成员当首键」（[comboModeTotal]），补齐时说清「这一组已经齐了」，
 *  比再说一次「已添加」有用 —— 用户补最后一档时往往正是在凑齐。
 *
 *  @param starter 正要加的那一档的首键（`null` = 等待档）
 */
internal fun comboCountAfter(
    cfg: Config,
    ids: List<String>,
    starter: KeyId?,
): Int {
    val used = usedComboStarters(cfg, ids) + listOfNotNull(starter?.id)
    return used.size + if (waitTaken(cfg, ids)) 1 else 0
}

/**
 * 这颗键现在是**哪几档按住组合的起点**（空表 = 不是任何一档的起点）。
 *
 *  这是「只参与组合」那对矛盾的**另一半**：加一档按住组合时查「起点键是不是只参与组合」
 *  （[addCombo] 里那道拦截），打开「只参与组合」时查「它是不是已经是某档的起点」
 *  （`KeySettingsScreen` 的 `comboOnlyRow`）。**两条必须问同一句话** —— 各写一份的话，
 * 一边放行、一边拒绝，落下的就是一份自相矛盾的配置：起点键不判单键手势，而那一档组合
 * 又要求它「按住到点」。
 *
 *  返回的是**整张表**而不是「第一档」：同一颗键可以同时是好几个按住组合的起点（`A+B` 先按 A、
 *  `A+C` 也先按 A）。只取一档的话，用户按提示删掉其中一档、回头再点还是一样被拦，
 *  而界面上再也说不出「还剩哪一档挡着」。
 *
 *  **停用的那几档也算**：停用只是暂时不生效，重新启用时同一个矛盾还在 —— 拿停用状态把它
 *  放过去，等于埋一份「一开就矛盾」的配置。
 */
internal fun Config.holdCombosStartedBy(key: KeyId): List<ComboKey> =
    combos.filter { it.mode != ComboKey.MODE_WAIT && it.starterKey == key.id }

/**
 * 加一档组合（选键弹窗点了「添加」）。
 *
 *  **类别在添加那一刻定下来**（用户口径）：等待组合 [starter] 传 `null`，按住组合传**那颗首键**。
 *  一档只认一种触发方式，所以以后想两种都有，就是两列里各建一档 —— 不再有「在一档上改方式」这条路。
 *
 *  ## 按住那一档先问一句，**问的时候还什么都没写**
 *
 *  它要求**首键**是「松手时触发」：不是的话那一档永远不成立（判定侧把「已经执行过自己
 *  动作」的键排除在按住之外）。所以先问（[ComboModeAsk]），**确认之后才连同添加一起落盘**。
 *
 *  早先的做法是「先写进配置、再问」——那一问于是只是做样子：点取消时那一档已经在列表里了，
 *  而起点键没改，它是一条死活不成立的废档（用户口径「那个 dialog 只是做样子，触发方式已经
 *  被真的添加了」）。
 *
 * @param starter 按住档的**首键**（`keys` 里任意一颗）；`null` = 这一档是等待组合
 * @return 新那一档的 id。`null` = 没加：这一类排满了 / 成员数不对 / 超出上限 / 写盘失败 /
 *   **正在等那一问的回答**（确认之后由 `ComboModeAskDialog` 走 [writeNewCombo] 真正写下去）
 */
internal fun SettingsPage.addCombo(
    keys: List<KeyId>,
    starter: KeyId?,
): String? {
    val cfg = configState.config.value
    val ids = keys.map { it.id }.distinct()
    if (starter != null) {
        //  **「只参与组合」的键先拦一道**：那一档位的意思是「这颗键不判单键手势」，
        //  它自己的「松手时触发」开关在按键设置页是灰的（改不了）。所以这里不能再走
        //  下面那条「切换为松手时触发并添加」的问句 —— 用户点了也没有效果，只会落下
        //  一份 `combo_only=true` 又 `long_fire_on_release=true` 的自相矛盾配置。
        //  改为直接说明原因、不添加（用户口径「也 dialog 拦截」）。
        if (cfg.key(starter).comboOnly) {
            XLog.w("组合键：${starter.label} 只参与组合，不能作为按住起点（${ids.joinToString("+")}）")
            ComboStarterBlocked.ask(starter)
            return null
        }
        if (!cfg.key(starter).longFireOnRelease) {
            XLog.w("组合键：${starter.label} 还不是「松手时触发」，先问一句再添加（${ids.joinToString("+")}）")
            ComboModeAsk.askAdd(listOf(starter), keys, ComboKey.MODE_HOLD)
            return null
        }
    }
    return writeNewCombo(keys, starter, releaseKeys = emptyList())
}

/**
 * 「这一类最多几颗」那句话 —— **两类各一条文案**（用户口径）。
 *
 *  两类的上限现在**是同一个数**（[ComboKey.MAX_KEYS]）：按住那一档也能到三颗了
 *  （首键一颗 + 后续两颗）。文案仍分开写，因为主语不同 —— 「等待组合最多」与「按住组合最多」
 *  说的是两种按法的容量，用户看到的那句话要能对上自己正在配的那一类。
 */
internal fun comboMaxKeysHint(hold: Boolean): String =
    I18n.format(
        if (hold) Strings.COMBO_MAX_KEYS_HOLD else Strings.COMBO_MAX_KEYS_WAIT,
        ComboKey.MAX_KEYS,
    )

/**
 * 真正把新那一档写进配置（按住那两档可顺带把起点键改成「松手时触发」，同一次落盘）。
 *
 *  过了那一问之后走这里；起点键本来就合规时 [addCombo] 也直接走这里。所有闸**都在这里再过
 *  一遍**：等确认的那段时间里配置可能变过（用户又加了一档 / 删了档），不能拿提问那一刻的
 *  结论当数。
 *
 * @param releaseKeys 要改成「松手时触发」的那几颗键（空 = 不动键）
 */
internal fun SettingsPage.writeNewCombo(
    keys: List<KeyId>,
    starter: KeyId?,
    releaseKeys: List<KeyId>,
): String? {
    val cfg = configState.config.value
    val picked = keys.map { it.id }.distinct()
    //  **首键排到第一位**：按住档的 `keys` 第一位就是首键，判定（`ComboDetector`）、界面文案
    //  （「按住 A 后再按 B」）、配置落盘三处都只读这一条，不另存一个「谁是首键」的字段。
    val ids = if (starter != null) listOf(starter.id) + picked.filterNot { it == starter.id } else picked
    if (ids.size !in ComboKey.MIN_KEYS..ComboKey.MAX_KEYS) {
        //  界面那边颗数不对时「添加」是灰的（点一下会说原因），走不到这里；
        //  这一句是给**新增入口**兜底：判定侧认得更多成员，界面这一侧仍然只造到
        //  `MAX_KEYS`（三颗）—— 别让某个新入口绕过这条线。
        XLog.w("组合键：成员 ${ids.size} 颗不在 ${ComboKey.MIN_KEYS}..${ComboKey.MAX_KEYS} 之内，不添加（${ids.joinToString("+")}）")
        hint(comboMaxKeysHint(starter != null), slot = HINT_SLOT_COMBO)
        return null
    }
    val resolved = if (starter != null) ComboKey.MODE_HOLD else ComboKey.MODE_WAIT
    val hold = resolved != ComboKey.MODE_WAIT
    //  上限是**每一列各一个**：等待组合 9 档、按住组合 9 档（`MAX_COMBOS` 不跨类合计）
    if (cfg.combos.count { it.isHold == hold } >= MAX_COMBOS) {
        hint(I18n.format(Strings.COMBO_LIMIT, MAX_COMBOS), slot = HINT_SLOT_COMBO)
        return null
    }
    //  同一类里这组键还排不排得下 —— 界面那边同一判据判「添加」按不动，
    //  这一句是给手改的配置与将来的入口兜底。全占满时换个说法（见 `comboAllModesTaken`）
    if (comboAllModesTaken(cfg, ids, hold)) {
        hint(
            if (hold) {
                I18n.get(Strings.COMBO_ALL_MODES_TAKEN, "所有触发方式都已添加")
            } else {
                I18n.get(Strings.COMBO_DUPLICATE, "这组按键已经添加过了")
            },
            slot = HINT_SLOT_COMBO,
        )
        return null
    }
    //  序号只增不减：删掉中间一档之后再加，不会复用那个 id（复用会让「在编哪一档」指到别人身上）
    val seq =
        (cfg.combos.mapNotNull { it.id.removePrefix(ComboKey.ID_PREFIX).toIntOrNull() }.maxOrNull() ?: 0) + 1
    val combo = ComboKey(id = ComboKey.idOf(seq), keys = ids, enabled = comboEnableByDefault(cfg, ids), mode = resolved)
    val label = comboLabel(cfg, combo)
    //  `cfg.keys` 是按 `KeyId` 索引的，所以这一份集合也用 `KeyId`（与 `writeComboMode` 同一条）
    val release = releaseKeys.toHashSet()
    val keysNext =
        if (release.isEmpty()) {
            cfg.keys
        } else {
            cfg.keys.mapValues { (id, key) -> if (id in release) key.copy(longFireOnRelease = true) else key }
        }
    val what =
        buildString {
            append("combo_add=${combo.id} mode=$resolved")
            if (release.isNotEmpty()) append(" fire_on_release=${releaseKeys.joinToString(",") { it.id }}")
        }
    val result = write(cfg.copy(keys = keysNext, combos = cfg.combos + combo), what)
    // 与 [writeAndNotice] 同一套口径：只在真的落盘并广播出去时报一句；失败时 [write] 自己已经报过。
    //  回执里每个键名各套一对括号（`已添加「侧键」+「电量 +」`）—— 整串再包一层会把两颗键
    //  读成一个名字。
    if (result == CommandsSave.Ok) {
        //  这一组键的触发方式都补齐时换一种说法（见 `comboCountAfter`）：
        //  补到最后一档的人要的是「这组已经齐了」，再报一次「已添加」读起来像什么都没发生。
        //  上限随成员数走（[comboModeTotal]）：等待一档 + 每个成员各当一次首键。
        val count = comboCountAfter(cfg, ids, starter)
        //  名字串按**逐个名字一枚框**展开（见 `namesToChipsTemplate`）：回执里那几颗键
        //  与列表行、顶栏副标题是同一种长相，不再是一串套着方括号的纯文字。
        val names = comboKeyNames(cfg, combo)
        val notice =
            if (count >= comboModeTotal(ids.size)) {
                namesToChipsTemplate(
                    I18n
                        .get(Strings.COMBO_ALL_MODES_ADDED, "%1\$d 种触发方式都已添加 %2\$s")
                        .replace("%1\$d", count.toString()),
                    "%2\$s",
                    names,
                )
            } else {
                namesToChipsTemplate(I18n.get(Strings.COMBO_ADDED, "已添加 %s"), "%s", names)
            }
        hint(notice, slot = HINT_SLOT_COMBO, chips = chipsOfNames(names))
    }
    XLog.w("组合键：加了一档 ${combo.id}=${combo.keys.joinToString("+")}（$label / mode $resolved）")
    return combo.id.takeIf { result == CommandsSave.Ok }
}

/**
 * 删掉一档组合按键。
 *
 * @return `true` = 已经落盘（或本来就不在了）；`false` = 写盘被拒 / 失败。调用方据此决定
 *   要不要收掉编辑目标与退页 —— 写失败时那一档还在配置里，页面不能先走。
 */
internal fun SettingsPage.removeCombo(id: String): Boolean {
    val cfg = configState.config.value
    val combo = cfg.combos.firstOrNull { it.id == id } ?: return true
    XLog.w("组合键：删掉 ${combo.id}（${comboLabel(cfg, combo)}）")
    //  回执与删除确认窗同一个口径：每个键名各套一对括号（见 `addCombo`）
    val label = comboLabel(cfg, combo)
    //  判「没成」只看 [CommandsSave.Failed]：`BroadcastFailed` 是「盘上删了、只是没通知到注入侧」，
    //  那时这一档确实已经不在了，调用方不能按「什么都没变」处理（`write` 的契约就是这么写的）。
    if (write(cfg.copy(combos = cfg.combos.filterNot { it.id == id }), "combo_del=$id") !=
        CommandsSave.Failed
    ) {
        val names = comboKeyNames(cfg, combo)
        hint(
            namesToChipsTemplate(I18n.get(Strings.COMBO_REMOVED, "已删除 %s"), "%s", names),
            slot = HINT_SLOT_COMBO,
            chips = chipsOfNames(names),
        )
        return true
    }
    return false
}

/**
 * 新加的这一档**默认跟成员键的实际状态**：成员里还有停用的键，它从一开始就是停用。
 *
 *  否则会造出「配置说启用、列表说停用」的一档 —— 等那颗键被开回来时这一组还会自己活过来。
 *  选键弹窗里那颗键的启停态不明显（它只列名字），所以这里不弹窗问，直接按事实落位：
 *  想开这一组，用户会去那颗键上开，或者在这一组的 ⋮ 菜单里点启用（那时才问「要不要把键也开」）。
 */
private fun comboEnableByDefault(
    cfg: Config,
    ids: List<String>,
): Boolean = ids.all { cfg.keyIdOf(it)?.let { kid -> cfg.key(kid).enabled } == true }

/**
 * 停用 / 启用一档组合 —— 与单键那一支（`setKeyEnabledFromMenu`）同一套行为。
 *
 *  写的是这一档自己的 [ComboKey.enabled]，与模块总闸（`GlobalConfig.enabled`）是两层：
 *  总闸关 ⇒ 一颗键都不接管；这一档关 ⇒ 只有这一组不成立（两颗键各按自己的绑定照常执行 ——
 *  判定侧读 `ComboKey.isUsable`，`Config.comboFor` 与 `ConfigChannel.joinsCombo` 两处都不认它，
 *  于是「参与组合的键要等一个窗口」也随之消失）。
 *
 *  **没变就不写**：菜单里点到当前那一档时，不该落一次盘、更不该报「已停用」。
 */
internal fun SettingsPage.setComboEnabled(
    id: String,
    on: Boolean,
) {
    val cfg = configState.config.value
    val combo = cfg.combos.firstOrNull { it.id == id } ?: return
    if (combo.enabled == on) return
    //  **回执与「已添加 / 已删除」同一个口径**：成员名各带自己的括号，整串不再套引号
    val label = comboLabel(cfg, combo)
    //  **启用之前先看成员键**（与按键列表那颗开关、绑定页 ⋯ 菜单同一套口径）：
    //  直接写 `enabled=true` 会留下一份「配置说启用、列表说停用」的状态 ——
    //  等那颗键被开回来时这一组还会自己活过来，用户看到的是「我没动它，它自己开了」。
    if (on && !comboEnableAllowed(cfg, combo, label)) return
    XLog.w("组合键：${if (on) "启用" else "停用"} $id（$label）")
    if (updateCombo(id, "enabled=${if (on) 1 else 0}") { it.copy(enabled = on) }) {
        val names = comboKeyNames(cfg, combo)
        val base =
            if (on) {
                I18n.get(Strings.COMBO_ENABLED, "已启用 %s")
            } else {
                I18n.get(Strings.COMBO_DISABLED, "已停用 %s")
            }
        hint(
            namesToChipsTemplate(base, "%s", names),
            slot = HINT_SLOT_COMBO,
            chips = chipsOfNames(names),
        )
    }
}

/**
 * 启用一档组合之前的闸 —— `true` = 放行。
 *
 *  两档拦法：
 *  1) **成员键已经不在配置里**（手改 / 导入带来的）：这一组本来就废了，直接拒；
 *     这里**不能**走「一起启用」那一问 —— 那颗键根本不存在，答应了也只会留下一个更假的状态。
 *  2) **成员键里有停用的**：问一句「要不要把键也开」（`KeysAsk`，与按键列表那颗 FAB 共用一个窗）。
 *     答「否」时这一组保持停用（这一档凑不齐，`Config.liveCombos` 也会把它排除在判定与等待之外）。
 */
private fun SettingsPage.comboEnableAllowed(
    cfg: Config,
    combo: ComboKey,
    label: String,
): Boolean {
    if (combo.keys.any { cfg.keyIdOf(it) == null }) {
        XLog.w("组合键：${combo.id} 的成员键已不在配置里，不启用（$label）")
        hint(
            I18n.get(Strings.KEYS_COMBO_NEEDS_KEYS, "成员键没启用，这组也停用"),
            slot = HINT_SLOT_COMBO,
        )
        return false
    }
    val dead =
        combo.keys
            .mapNotNull { cfg.keyIdOf(it) }
            .filterNot { cfg.key(it).enabled }
            .map { it.id }
            .toSet()
    if (dead.isEmpty()) return true
    KeysAsk.ask(
        KeysAsk.Kind.ENABLE,
        keys = emptySet(),
        combos = setOf(combo.id),
        extraKeys = dead,
        slot = HINT_SLOT_COMBO,
    )
    return false
}

/** 组合里有没有已经删掉的键（列表那一行据此标一句）。 */
internal fun SettingsPage.comboHasMissingKey(
    cfg: Config,
    combo: ComboKey,
): Boolean = combo.keys.any { cfg.keyIdOf(it) == null }

/** 写盘结果的回执位 —— 这两页一次只改一件事，共用一个位就够（见 [HINT_SLOT_COMBO]）。 */
internal fun SettingsPage.comboWriteFailed() {
    hint(I18n.get(Strings.TOAST_CONFIG_SAVE_FAILED, "保存失败"), slot = HINT_SLOT_COMBO)
}

/** 写盘发生异常时也报一句 —— 与 `pick` 同一道兜底（点了必须有说法）。 */
internal fun SettingsPage.comboWriteGuard(
    what: String,
    block: () -> Unit,
) = XLog.guard(
    what = "组合键：$what",
    note = "这一次没写下去",
    onError = { comboWriteFailed() },
    block = block,
)

/**
 * 改一档组合的某个属性 —— 编辑页那几行（连续触发 / 振动 / 间隔 / 长按延迟）都走它。
 *
 *  与单键那几张卡同一个口径：值没变就不落盘（拖滑杆松手时常常是同一个值），
 *  写盘说明里带上档位 id 与改的是谁 —— 日志里能对上是哪一组。
 *
 * @param what 写盘说明里那一段（`long_repeat=1` 这种）
 * @return `true` = 已经是目标值（落盘了，或者本来就是这个值）
 */
internal fun SettingsPage.updateCombo(
    id: String,
    what: String,
    change: (ComboKey) -> ComboKey,
): Boolean {
    val cfg = configState.config.value
    val combo = cfg.combos.firstOrNull { it.id == id } ?: return false
    val next = change(combo)
    if (next == combo) return true
    return write(cfg.copy(combos = cfg.combos.map { if (it.id == id) next else it }), "combo:$id:$what") !=
        CommandsSave.Failed
}

/**
 * 写「按键之间的等待」（毫秒）—— 两颗键按下最多隔多久才算一起按下。
 *
 *  这一位**改的是注入侧的手感**，所以走通用写入通道（落盘 + 广播 + 重问注入侧），
 *  与别的全局项同一条路。返回「写盘成没成」：滑杆失败时要拨回原值（见 `SliderRow` 的 `commit`）。
 */
internal fun SettingsPage.setComboWindow(ms: Int): Boolean {
    val cfg = configState.config.value
    val value = ms.coerceIn(GlobalConfig.COMBO_WINDOW_MIN_MS, GlobalConfig.COMBO_WINDOW_MAX_MS)
    if (cfg.global.comboWindowMs == value) return true
    return write(cfg.copy(global = cfg.global.copy(comboWindowMs = value)), "combo_window_ms=$value") != CommandsSave.Failed
}

/**
 * 写**这一组自己**的按键间隔（毫秒）。
 *
 *  与 [setComboWindow] 不是同一个落点：那个写公共那一档（没单独设过的组都用它），
 *  这个写这一组自己的值 —— 判定侧两处都读（`Config.comboWindowFor`）。
 *
 * ## 「回到跟随公共」不是这一条路
 *
 *  那个状态的入口只有一个：**关掉这一组的「使用独立设置」**
 *  （`Config.comboWindowFor` 的第一支：关着就整份看公共）。开着独立设置时不存在「跟随」——
 *  打开那一刻已经把公共值播进这一组了（见 `KeySettingsScreen.withSeededComboFields`），
 *  之后改公共它一项都不动；关掉再打开用的还是这一组上次那套。
 *
 *  所以下面 `ms <= 0` 那一支**界面上到不了**：滑杆的下限是
 *  [GlobalConfig.COMBO_WINDOW_MIN_MS]（20ms）。它留着是因为**老配置**里可能已经是
 *  [ComboKey.WINDOW_FOLLOW_GLOBAL]（这一位出现之前写下的 0），读取侧的 `?: global` 靠它兜底；
 *  手改配置文件写 0 也照样表达「跟随」。
 */
internal fun SettingsPage.setComboOwnWindow(
    id: String,
    ms: Int,
): Boolean =
    updateCombo(id, "window_ms=$ms") { combo ->
        val value =
            if (ms <= 0) {
                ComboKey.WINDOW_FOLLOW_GLOBAL
            } else {
                ms.coerceIn(GlobalConfig.COMBO_WINDOW_MIN_MS, GlobalConfig.COMBO_WINDOW_MAX_MS)
            }
        combo.copy(windowMs = value)
    }

/**
 * 写**这一组自己**的「首键要先按住多久」（毫秒）—— 按住手势的下限。
 *
 *  与 [setComboOwnWindow] 同一个形状：那个写这一组的按键间隔，这个写这一组的按住下限，
 *  判定侧都只读 `Config` 那两处（`comboHoldMinFor` / `comboWindowFor`）。
 *  `0` 同样只服务**老配置**与手改文件（界面滑杆的下限是 40ms）。
 */
internal fun SettingsPage.setComboOwnHoldMin(
    id: String,
    ms: Int,
): Boolean =
    updateCombo(id, "hold_min_ms=$ms") { combo ->
        val value =
            if (ms <= 0) {
                ComboKey.HOLD_MIN_FOLLOW_GLOBAL
            } else {
                ms.coerceIn(GlobalConfig.COMBO_HOLD_MIN_MIN_MS, GlobalConfig.COMBO_HOLD_MIN_MAX_MS)
            }
        combo.copy(holdMinMs = value)
    }
