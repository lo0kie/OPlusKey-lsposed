package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.DialogBody
import com.lookie.opluskey.ui.dialog.DialogButtonRow
import com.lookie.opluskey.ui.dialog.DialogPositiveStyle
import com.lookie.opluskey.ui.dialog.FrostedDialog
import com.lookie.opluskey.ui.feedback.DisabledStateWithArrow
import com.lookie.opluskey.ui.feedback.DisabledStateWithPick
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.keyStateLabel
import com.lookie.opluskey.ui.model.icon.DrawnIcons
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.screen.keyEnabled
import com.lookie.opluskey.ui.state.binding.ComboEditState
import com.lookie.opluskey.ui.state.binding.KeyWatchState
import com.lookie.opluskey.ui.state.binding.KeysAsk
import com.lookie.opluskey.ui.state.binding.KeysEditState
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.write.write

// 「按键功能」页上半块 —— 按键列表（侧键 + 收集来的键）那张卡，与它下面单起一张的「添加按键」。
//
//  从 `KeysScreen.kt` 拆出来：那边是这一页的骨架（两张清单 + 筛选 + 两个弹窗），
//  这一块只回答「有哪几颗键、怎么再加一颗」。

/**
 * 「按键列表」那一块 —— **内置侧键那一行**。
 *
 *  收集来的键另起一张卡（[CustomKeysCard]）：两者来路不同（内置 / 用户收集）、额度也只限后者，
 *  拼在一张卡里只能靠一条加深带勉强分开 —— 用户口径改成**两张卡各带小标题**。
 *
 * ## 点一行做什么
 *
 *  选中这颗键再进「按键绑定」页 —— 绑定的动作属于那一页，
 *  这一页只回答「有哪几颗键、点进去改哪一颗」。进页会把手势 tab 拨回第一档。
 *
 * ## 停用的键怎么画
 *
 *  行首图标换成禁用 + 行尾写「已停用」：停用只靠颜色区分对色弱不友好，
 *  而这一页的灰还可能是整页闸门关着，两件事得能分开看。停用的键照样能点进去（那一页能开回来）。
 *
 * ## 编辑态（见 [KeysEditState]）
 *
 *  顶栏那颗「编辑」按下去之后，点一行是**勾选**而不是进页。侧键也能勾（用户口径「快捷键也要可选」），
 *  只是删不掉 —— 那一侧在 `removePicked` 里过滤，见 [KeysListCard] 的调用点。
 */
@Composable
internal fun SettingsPage.KeysListCard(pageEnabled: Boolean) {
    val editing = KeysEditState.editing
    val picked = KeysEditState.pickedKeys
    //  这一行行尾那半句话（停用 / 只参与组合 / 什么都不写）—— 判据在 `keyStateLabel` 里，
    //  与自定义键那几行共用；两处各判一次的话，加第三种状态时必漏一处。
    val sideKeyState =
        keyStateLabel(
            keyEnabled(KeyId.PLUS),
            configState.config.value
                .key(KeyId.PLUS)
                .comboOnly,
        )

    CardList(
        rows =
            listOf(
                //  内置侧键：**能勾、不能删**（用户口径「快捷键也要可选，删除时过滤掉」）。
                //  所以编辑态不再置灰 —— 它和别的行一样可选；「删不掉」这件事在 `removePicked`
                //  那一侧过滤（选它一个时给一句提示），而不是把这一行按死。
                CardRow.Item(
                    title = I18n.get(Strings.BINDING_SECTION_KEYS, "快捷键"),
                    //  行尾只有一格（`trailing` 优先于 `arrow`）：编辑态是勾选框，平时那一格
                    //  写**这一颗键此刻是什么状态**（`keyStateLabel`：停用 / 只参与组合 / 正常启用
                    //  三选一，最后一种什么都不写，回到 `arrow` 那条默认路径）。
                    trailing =
                        when {
                            editing -> {
                                {
                                    DisabledStateWithPick(
                                        picked = KeyId.PLUS.id in picked,
                                        enabled = pageEnabled,
                                        state = sideKeyState,
                                    )
                                }
                            }

                            sideKeyState == null -> {
                                null
                            }

                            else -> {
                                { DisabledStateWithArrow(sideKeyState) }
                            }
                        },
                    //  停用只把行首换成禁用图标，**不压色**：图标一灰，整行看起来像点不了，
                    //  而这一行恰恰还能点进去（停用也照样能进那一页开回来）。
                    icon = keyIcon(DrawnIcons.MenuSideKey, keyEnabled(KeyId.PLUS)),
                    enabled = pageEnabled,
                    arrow = !editing,
                    onClick = {
                        if (editing) KeysEditState.toggleKey(KeyId.PLUS.id) else openKey(KeyId.PLUS)
                    },
                ),
            ),
    )
}

/**
 * 「自定义按键」那一块 —— 收集来的键，**最后一行是「添加按键」**（用户口径：与清单同卡）。
 *
 *  上面回答「这一列现在有哪几颗」，最后一行是往清单里添一颗 —— 同属「这一列」这件事，
 *  而额度的计数在小标题上（`KeysScreen` 那个 `TitleCounter`），也不该跟着「内置侧键」走。
 *
 * @param pageEnabled 整页总闸（模块开着 + 链路齐了 + 配置可写）
 * @param onAddKey 点「添加按键」—— 收集窗挂在页面那一层（[KeysScreen]）
 */
@Composable
internal fun SettingsPage.CustomKeysCard(
    pageEnabled: Boolean,
    onAddKey: () -> Unit,
) {
    val cfg = configState.config.value
    val editing = KeysEditState.editing

    CardList(
        rows =
            buildList {
                cfg.customKeys.forEachIndexed { i, key ->
                    val k = cfg.key(key)
                    //  行尾那半句与内置侧键同一份判据（`keyStateLabel`）
                    add(
                        customKeyRow(
                            key = key,
                            state = keyStateLabel(k.enabled, k.comboOnly),
                            rowIcon = keyIcon(PageIcons.menuKeyNumber(i), k.enabled),
                            pageEnabled = pageEnabled,
                        ),
                    )
                }
                add(addKeyRow(cfg, pageEnabled, onAddKey, editing = editing))
            },
    )
}

/**
 * 一颗自定义键那一行。
 *
 *  行尾只有一格（`CardRow` 里 `trailing` 优先于 `arrow`）：编辑态是勾选框，平时是那句状态
 *  **加自己画的箭头** —— 填了这一格 `arrow` 那条路就不画了，所以两者共存必须自己画。
 *
 *  @param state 行尾那半句话（[keyStateLabel] 算好传进来）；`null` = 正常启用，这一格不占位
 */
private fun SettingsPage.customKeyRow(
    key: KeyId,
    state: String?,
    rowIcon: ImageVector,
    pageEnabled: Boolean,
): CardRow.Item {
    //  编辑态与勾选集合在这一层直接读（两者都是页面级状态，传进来只会多两个参数）
    val editing = KeysEditState.editing
    val picked = KeysEditState.pickedKeys
    return CardRow.Item(
        title = key.label,
        // 停用换禁用图标（第几颗就画哪个数字，由调用点算好传进来）
        icon = rowIcon,
        enabled = pageEnabled,
        //  行尾那一格：编辑态是勾选框（旁边可能还跟着那句状态），平时就是状态 + 箭头 ——
        //  正常启用时给 `null`，让 `arrow` 那条默认路径画。
        trailing =
            when {
                editing -> {
                    {
                        DisabledStateWithPick(
                            picked = key.id in picked,
                            enabled = pageEnabled,
                            state = state,
                        )
                    }
                }

                state == null -> {
                    null
                }

                else -> {
                    { DisabledStateWithArrow(state) }
                }
            },
        arrow = !editing,
        onClick = {
            if (editing) KeysEditState.toggleKey(key.id) else openKey(key)
        },
    )
}

/**
 * 「添加按键」那一行 —— 清单的**最后一行**（与清单同卡，用户口径）。
 *
 *  不带副标题：这一行说的是「往清单里添一颗」，点下去做什么由标题自己说清。
 *  加不了的时候（满员 / 编辑态）原因走 `disabledHint`，点一下才说。
 *
 * @param editing 编辑态下**禁用**而不是退场（用户口径）：编辑态在删、不在加，但这一行要一直
 *   在，用户才知道「这一列还能再添东西」。
 */
private fun SettingsPage.addKeyRow(
    cfg: Config,
    pageEnabled: Boolean,
    onAddKey: () -> Unit,
    editing: Boolean,
): CardRow.Item {
    val atLimit = cfg.customKeys.size >= KeyId.MAX_CUSTOM_KEYS
    return CardRow.Item(
        title = I18n.get(Strings.BINDING_ADD_KEY, "添加按键"),
        icon = PageIcons.MenuKeyAdd,
        enabled = pageEnabled && !atLimit && !editing,
        //  这一行自己的原因：满员 > 编辑态（编辑态里满员也是先报满员，更贴近眼前的事实）
        disabledHint =
            when {
                atLimit -> I18n.format(Strings.KEY_WATCH_LIMIT, KeyId.MAX_CUSTOM_KEYS)
                editing -> I18n.get(Strings.KEYS_EDIT_NO_ADD, "编辑中不能添加，请先完成")
                else -> null
            },
        onClick = onAddKey,
    )
}

/** 这一行的行首图标 —— 停用的键换成**禁用**（`pointer-cancel`），其余保持原样。 */
private fun keyIcon(
    normal: ImageVector,
    enabled: Boolean,
): ImageVector = if (enabled) normal else PageIcons.Cancel

/**
 * 这颗键在列表里那一行的行首图标 —— 「重命名按键」那个输入窗取的就是它。
 *
 *  **弹窗的图标跟着触发它的那一行走**：滑杆、提示文案、变量值那几支已经是这个口径，
 *  改名这一支原先却写死一颗标签图 —— 同一个弹窗在两处长得不一样。
 */
internal fun SettingsPage.keyRowIcon(key: KeyId): ImageVector {
    val cfg = configState.config.value
    val normal =
        if (key == KeyId.PLUS) {
            DrawnIcons.MenuSideKey
        } else {
            PageIcons.menuKeyNumber(cfg.customKeys.indexOf(key).coerceAtLeast(0))
        }
    return keyIcon(normal, cfg.key(key).enabled)
}

/**
 * 选中 [key] 再进「按键绑定」页。
 *
 *  点的就是当前那颗也照进 —— 这里不是「切换」动作，用户点了一颗键，期望的是进那颗键的页面。
 */
private fun SettingsPage.openKey(key: KeyId) {
    // 判据用 `editing()`，**不能拿 `selectedId` 直接比 `key.id`**：`selectedId == null` 本来
    //  就表示侧键，直接比会在「首次点侧键」时把 `null != "plus"` 当成切换，弹一句假的「已切换」。
    if (KeyWatchState.editing(configState.config.value) != key) {
        // 侧键的规范写法是 `null`，别把 "plus" 写进去（两处口径不一致，读回来又要归一一次）
        KeyWatchState.select(if (key == KeyId.PLUS) null else key.id)
        // **不发回执**：点一行就是「进那颗键的页面」，不是菜单里那种「切换」
        XLog.w("按键页：切到「${key.label}」")
    }
    navTo(Screen.BINDING)
}

/**
 * 删掉编辑态里勾中的那些项（按键 + 组合按键）—— 右下角那颗「删除」→ 确认窗之后调。
 *
 *  **一次落盘**：勾了几项就写一遍，回执报总数（「已删除 3 项」）。分两次写会播两条通知、
 *  「谁删了谁」还会互相盖掉。
 *
 *  连带影响：
 *   · **按键** —— 内置侧键不在其中（编辑态里它勾不到）。删掉一颗自定义键，**用到它的组合按键
 *     一起删掉**：组合少一颗成员就是废配置（列表里只会画成停用图标、点了也进不去），
 *     留着唯一的作用是让用户困惑「这组怎么还在」。会被带走几个由确认窗提前报出
 *     （见 `KeysScreen` 的 `cascadeCombos`）。
 *     正在编辑的那颗键被删掉时，选中态收回侧键（[KeyWatchState.forget]）—— 不收回的话
 *     绑定页会指着一颗不存在的键去读写。
 *   · **组合** —— 整组连它的动作一起没；正在编辑的是它时，把 `ComboEditState` 的编辑目标清掉
 *     （否则「组合键」编辑页还停在一个已经删掉的 id 上）。
 */
internal fun SettingsPage.removePicked(): Boolean {
    val cfg = configState.config.value
    //  **内置侧键能勾、不能删**（用户口径）：它现在与其他行一样可选，删除时在这里把它滤掉。
    //  连带计算也必须在滤掉之后做 —— 否则「选中侧键」会把用到侧键的那些组合一起带走，
    //  而侧键本身根本没被删掉。
    val keyIds = KeysEditState.pickedKeys - KeyId.PLUS.id
    val pickedCombos = KeysEditState.pickedCombos
    if (keyIds.isEmpty() && pickedCombos.isEmpty()) {
        //  勾的只有内置侧键（或者什么都没勾）：说清为什么删不掉，别让「点了没反应」看起来像坏了
        if (KeysEditState.pickedKeys == setOf(KeyId.PLUS.id) && pickedCombos.isEmpty()) {
            hint(I18n.get(Strings.KEYS_BUILTIN_KEEP, "内置快捷键不能删除"), slot = "keys_del")
        }
        return false
    }
    //  勾中的键连带带走「含有它们」的组合；已经勾上的不重复算（它们本来就在这一趟里）
    val cascaded =
        cfg.combos
            .filter { it.id !in pickedCombos && it.keys.any { keyId -> keyId in keyIds } }
            .map { it.id }
    val comboIds = pickedCombos + cascaded
    val count = keyIds.size + comboIds.size
    val victims = cfg.customKeys.filter { it.id in keyIds }
    XLog.w(
        "按键页：删除 $count 项" +
            "（按键 ${victims.joinToString { it.label }}；" +
            "组合 ${pickedCombos.joinToString()}；连带 ${cascaded.joinToString()}）",
    )
    val next =
        cfg.copy(
            customKeys = cfg.customKeys.filterNot { it.id in keyIds },
            //  这一颗键自己的设置（停用 / 单击延迟…）跟着一起走，别在配置里留一串没人认领的条目
            keys = cfg.keys.filterKeys { !it.isCustom || it.id !in keyIds },
            combos = cfg.combos.filterNot { it.id in comboIds },
        )
    //  **先落盘、成了再收状态**。判「没成」只看 [CommandsSave.Failed]（= 什么都没写）：
    //  [CommandsSave.BroadcastFailed] 是「盘上已经是新样子、只是没通知到注入侧」—— 那时按键
    //  确实已经删了，界面不跟着收就会勾着一批不存在的 id。
    if (write(next, "keys_del=$count") == CommandsSave.Failed) return false
    hint(I18n.format(Strings.KEYS_DELETED, count), slot = "keys_del")
    victims.forEach { KeyWatchState.forget(it.id) }
    if (ComboEditState.editingId in comboIds) ComboEditState.clear()
    return true
}

/**
 * 勾中的项里**还有没有开着的**（按键 + 组合按键）。
 *
 *  这一条同时决定两件事：那颗 FAB 显示「停用」还是「启用」、以及按下去之后全部置成哪一档。
 *  收成一处是因为两边各算一次就会出现「图标写着停用、按下去却全启用了」这种自相矛盾。
 */
internal fun pickedAnyEnabled(cfg: Config): Boolean =
    //  不看 `isCustom`：内置侧键也能被勾、也能停用（它只是删不掉），漏掉它会让那颗按钮
    //  判成「全关着」，于是一按就把侧键也打开。
    cfg.keys.any { (id, key) -> id.id in KeysEditState.pickedKeys && key.enabled } ||
        cfg.combos.any { it.id in KeysEditState.pickedCombos && it.enabled }

/**
 * 把编辑态里勾中的那些项**统一**改成「启用 / 停用」—— 右下角那颗开关按钮调的。
 *
 *  **不是逐项取反**：勾了「一开一关」两项，取反之后还是一开一关 —— 按下去像什么都没发生。
 *  判据是 [pickedAnyEnabled]：只要还有开着的就全部停用，全关才全部启用。
 *
 *  与组合按键的联动（用户口径）：
 *   · **停用 ⇒ 连带停用**：含这些键的组合一并停（少一颗键的组合本来就跑不起来）；
 *   · **启用 ⇒ 只问一句**：含这些键、现在关着的组合不自动开（用户可能正是想关掉它们），
 *     交给 [KeysAsk] + 页面上的那一问；勾里本来就有的组合照常直接开。
 *
 * @return `true` = 已落盘；`false` = 没写（写失败，或抛出了那一问）
 */
internal fun SettingsPage.togglePickedEnabled(): Boolean {
    val cfg = configState.config.value
    val keyIds = KeysEditState.pickedKeys
    val comboIds = KeysEditState.pickedCombos
    if (keyIds.isEmpty() && comboIds.isEmpty()) return false
    val turnOn = !pickedAnyEnabled(cfg)
    //  含这些键的组合（不管开没开）
    val usingKeys = cfg.combos.filter { combo -> combo.keys.any { it in keyIds } }
    if (turnOn) {
        //  ① **先看勾中的组合**：它成员的键里还有关着的 ⇒ 这组根本跑不起来，先问「要不要把键也开」
        val offMemberKeys =
            cfg.combos
                .filter { it.id in comboIds }
                .flatMap { it.keys }
                .filter { member -> cfg.keys.any { (id, key) -> id.id == member && !key.enabled } }
                .toSet() - keyIds
        if (offMemberKeys.isNotEmpty()) {
            KeysAsk.ask(KeysAsk.Kind.ENABLE, keyIds, comboIds, extraKeys = offMemberKeys)
            return false
        }
        //  ② 再看含这些键、现在关着、又没被勾中的组合：不自动开，问一句
        val offCombos = usingKeys.filter { !it.enabled && it.id !in comboIds }.map { it.id }.toSet()
        if (offCombos.isNotEmpty()) {
            KeysAsk.ask(KeysAsk.Kind.ENABLE, keyIds, comboIds, extraCombos = offCombos)
            return false
        }
        return applyToggle(cfg, keyIds, comboIds, emptySet(), emptySet(), enable = true, slot = "keys_toggle")
    }
    //  ③ 停用：含这些键的组合**一起停**，但先把「有哪几个」说清（用户口径：要弹窗）。
    //  只数**自己还开着**的那些 —— 已经停用的组合再「连带停用」一次等于虚报，用户会以为这次
    //  按下去弄掉了 N 组，其实其中几组早就关着。
    val cascade =
        usingKeys
            .filter { it.enabled }
            .map { it.id }
            .filter { it !in comboIds }
            .toSet()
    if (cascade.isNotEmpty()) {
        KeysAsk.ask(KeysAsk.Kind.DISABLE, keyIds, comboIds, extraCombos = cascade)
        return false
    }
    return applyToggle(cfg, keyIds, comboIds, emptySet(), emptySet(), enable = false, slot = "keys_toggle")
}

/**
 * 真正落盘的那一下（[togglePickedEnabled] 与 [resolveAsk] 共用）。
 *
 *  @param extraKeys / extraCombos 只有用户答「一起改」时才非空
 */
private fun SettingsPage.applyToggle(
    cfg: Config,
    pickedKeys: Set<String>,
    pickedCombos: Set<String>,
    extraKeys: Set<String>,
    extraCombos: Set<String>,
    enable: Boolean,
    slot: String,
): Boolean {
    val allKeys = pickedKeys + extraKeys
    val allCombos = pickedCombos + extraCombos
    val count = pickedKeys.size + pickedCombos.size
    XLog.w("按键页：${if (enable) "启用" else "停用"} $count 项（键 $allKeys；组合 $allCombos）")
    val next =
        cfg.copy(
            keys =
                cfg.keys.mapValues { (id, key) ->
                    if (id.id in allKeys) key.copy(enabled = enable) else key
                },
            combos = cfg.combos.map { if (it.id in allCombos) it.copy(enabled = enable) else it },
        )
    if (write(next, "keys_toggle=$enable") == CommandsSave.Failed) return false
    hint(
        if (!enable && extraCombos.isNotEmpty()) {
            I18n.format(Strings.KEYS_TOGGLE_OFF_CASCADE, count, extraCombos.size)
        } else {
            I18n.format(if (enable) Strings.KEYS_TOGGLE_ON else Strings.KEYS_TOGGLE_OFF, count)
        },
        slot = slot,
    )
    //  **切换成功后退出编辑态**（用户口径，与「删除成功」那一档一致）：这一趟的活干完了，
    //  留着一排勾没有下一步。只在「按键页那颗开关」这条路上退 —— 绑定页 ⋯ 菜单那条
    //  （`HINT_SLOT_BINDING`）本来就不在编辑态里。写失败的那条路上面已经 `return false`。
    if (slot == "keys_toggle") KeysEditState.toggleEditing()
    return true
}

/**
 * 回答 [KeysAsk] 那一问。
 *
 *  启用那几问共用一句语义：**「是」= 连带的也一起改，「否」= 只改用户点名的那几项**。
 *  所以那里的否定不是「什么都不做」—— 否则用户点了「不启用」会发现键也没开，还得再按一次。
 *
 *  **停用那一问不在此列**：它的两颗按钮是「取消 / 停用」，否定就是真的取消 ——
 *  什么都不写、什么都不改（与点蒙层同一口径）。取消之后还把点名的键停掉，等于按钮说了谎。
 */
internal fun SettingsPage.resolveAsk(accept: Boolean): Boolean {
    val kind = KeysAsk.kind ?: return false
    val keys = KeysAsk.keys
    val combos = KeysAsk.combos
    val extraKeys = if (accept) KeysAsk.extraKeys else emptySet()
    val extraCombos = if (accept) KeysAsk.extraCombos else emptySet()
    //  「组合用的键还关着」那一问：答「否」⇒ **这一组也别开**（用户口径）。
    //  少一颗键的组合开起来只会画成停用图标、点了也没反应 —— 与其开一半，不如保持原样，
    //  并说清为什么（不然用户会以为按钮没生效）。
    val memberKeysAsk = KeysAsk.extraKeys.isNotEmpty()
    val slot = KeysAsk.slot
    KeysAsk.clear()
    if (kind == KeysAsk.Kind.DISABLE && !accept) {
        //  取消：这一问到此为止（键与组合都不动，编辑态也不退）
        return false
    }
    if (memberKeysAsk && !accept) {
        hint(I18n.get(Strings.KEYS_COMBO_NEEDS_KEYS, "成员键没启用，这组也停用"), slot = slot)
        return false
    }
    return applyToggle(
        configState.config.value,
        keys,
        combos,
        extraKeys,
        extraCombos,
        enable = kind == KeysAsk.Kind.ENABLE,
        slot = slot,
    )
}

/**
 * 键与组合连带关系的那一问（停用告知 / 启用询问 / 组合成员键询问）——
 * 按键列表与按键绑定页各挂一次（同一时刻只有一页可见）。
 *
 *  **停用那一问**是纯确认：`取消` / `停用`。取消就是什么都不做（与点蒙层同一口径）。
 *  **启用那几问**的两颗都是动词（`不启用` / `启用`）：那几问的「否」仍有含义 ——
 *  只动用户点名的那几项，连带的那些留着（见 [resolveAsk]）。
 *  主按钮**不用警告色**：停用虽属破坏性，但这一问的目的不是吓人，而是把连带范围说清。
 */
@Composable
internal fun SettingsPage.KeysAskDialog() {
    val kind = KeysAsk.kind
    val extraKeyCount = KeysAsk.extraKeys.size
    val extraComboCount = KeysAsk.extraCombos.size
    //  标题 / 正文跟着「连带的是键还是组合」走，句子里报的都是**额外会被改动的条数**
    val disable = kind == KeysAsk.Kind.DISABLE
    val title =
        when {
            kind == null -> ""
            extraKeyCount > 0 -> I18n.get(Strings.KEYS_ENABLE_KEYS_TITLE, "同时启用这些按键？")
            disable -> I18n.get(Strings.KEYS_DISABLE_CASCADE_TITLE, "同时停用组合按键？")
            else -> I18n.get(Strings.KEYS_ENABLE_COMBOS_TITLE, "同时启用组合按键？")
        }
    val body =
        when {
            kind == null -> ""
            extraKeyCount > 0 -> I18n.format(Strings.KEYS_ENABLE_KEYS_BODY, extraKeyCount)
            disable -> I18n.format(Strings.KEYS_DISABLE_CASCADE_BODY, extraComboCount)
            else -> I18n.format(Strings.KEYS_ENABLE_COMBOS_BODY, extraComboCount)
        }
    FrostedDialog(
        show = kind != null,
        title = title,
        //  **点蒙层只关窗，不算任何一种回答**（用户口径）：蒙层是「我不想选」，
        //  既不改配置、也不退出编辑态 —— 直接 `clear()` 把这一问丢掉。
        onDismiss = { KeysAsk.clear() },
        footer = {
            DialogButtonRow(
                negative =
                    if (disable) {
                        I18n.get(Strings.DIALOG_CANCEL, "取消")
                    } else {
                        I18n.get(Strings.KEYS_NOT_ENABLE, "不启用")
                    },
                positiveStyle =
                    DialogPositiveStyle(
                        label =
                            if (disable) {
                                I18n.get(Strings.KEYS_DISABLE, "停用")
                            } else {
                                I18n.get(Strings.KEYS_ENABLE, "启用")
                            },
                    ),
                onNegative = { resolveAsk(accept = false) },
                onPositive = { resolveAsk(accept = true) },
            )
        },
    ) {
        //  正文走全项目那一份（[DialogBody]）：短句居中、长句左对齐，超出可滚 ——
        //  手写一个裸 `Text` 时正文一长就把两颗按钮顶出可见区，且内缩得自己补一遍。
        DialogBody(body)
    }
}
