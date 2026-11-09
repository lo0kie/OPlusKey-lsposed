package com.lookie.opluskey.ui.screen.combo

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.DrawnIcons
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.keyBrackets
import com.lookie.opluskey.ui.model.keyName
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.SelectAll

// 「组合按键列表」那块小标题与它的筛选菜单 —— 从 `ComboListCard.kt` 拆出来的。
//
// 卡片里那一列回答的是「有哪几组、每行写什么」；这一份回答的是「这一列叫什么、按哪颗键筛」。
// 两件事共用同一张卡的位置，但一个是行、一个是菜单，写在一个文件里会一起长成一坨
// （detekt 的 `TooManyFunctions` 数的就是这一坨）。

/**
 * 列表标题 —— 筛着的时候把「在看哪颗键」接在后面（`组合按键列表 - 「侧键」`）。
 *
 *  不写的话，从别处回到这一页只会看到一张短了的表：不知道那是筛出来的，第一反应是「组合丢了」。
 *  连接符用半角连字符（用户口径），与页内别处的「键 - 手势」同一套写法；
 *  **键名照例带括号**（[keyBrackets]，与列表行、删除确认窗同一个口径）—— 不带的话
 *  「组合按键列表 - 侧键」读起来像一句话，看不出后面那一段是**一颗键的名字**。
 */
internal fun SettingsPage.comboListTitle(
    cfg: Config,
    filter: String?,
): String {
    val base = I18n.get(Strings.COMBO_LIST_TITLE, "组合按键列表")
    val key = filter?.let { cfg.keyIdOf(it) } ?: return base
    val (open, close) = keyBrackets()
    return "$base - $open${keyName(key)}$close"
}

/**
 * 筛选菜单 —— 「全部」+ **出现在组合里**的每一颗键（单选，打勾的是当前那一档）。
 *
 *  只列被某组用到的键：一颗谁都还没用上的键，选了只会得到一张空表。
 *  行首图标与首页「按键列表」同一套（侧键那颗 / 第几颗自定义键），两处读起来是同一种东西；
 *  **顺序也按那套**（侧键在前、自定义键按收集顺序）—— 不排序的话是「组合里第一次出现的次序」。
 *
 * @param onPick `null` = 不筛（「全部」那一行）
 */
internal fun SettingsPage.comboFilterEntries(
    cfg: Config,
    filter: String?,
    onPick: (String?) -> Unit,
): List<MenuEntry> =
    buildList {
        //  小标题**在最顶上**：这一张菜单整体就是「按按键筛这一列」，先给它一个名字，
        //  下面才是「全部」与那几颗键。不留标题的话，一列裸选项看不出它们是一件事。
        //
        //  顺序仍按**首页「按键列表」那一套**：侧键在前，自定义键按收集顺序（即「第几颗」的号）。
        //  直接拿 `combos.flatMap { it.keys }.distinct()` 的次序是「在组合表里第一次出现的顺序」，
        //  真机上就成了「侧键 / 1 / 3 / 2」这种看不懂的序 —— 那不是任何一处列表的顺序。
        add(
            MenuEntry.Section(
                title = I18n.get(Strings.COMBO_FILTER_SECTION, "筛选按键"),
                rows =
                    buildList {
                        //  「不筛」那一行也在这一组里（它同样是「按按键筛」的一个档位）；
                        //  与下面真正的键之间用一条**加深带**断开 —— 见下一行那个 `dividerBefore`。
                        add(
                            MenuEntry.Row(
                                title = I18n.get(Strings.COMBO_FILTER_ALL, "全部"),
                                //  这一行的意思是「不筛」（与「已保存的命令」那张菜单同一颗 `select-all`）
                                icon = TablerIcons.Outline.SelectAll,
                                selected = filter == null,
                            ) { onPick(null) },
                        )
                        cfg.combos
                            .flatMap { it.keys }
                            .distinct()
                            .mapNotNull { cfg.keyIdOf(it) }
                            .sortedBy { key -> cfg.customKeys.indexOfFirst { it.id == key.id } }
                            .forEachIndexed { index, key ->
                                add(
                                    MenuEntry.Row(
                                        title = keyName(key),
                                        icon = keyFilterIcon(cfg, key),
                                        selected = filter == key.id,
                                        //  第一颗键与上面那行「全部」之间来一条加深带
                                        dividerBefore = index == 0,
                                    ) { onPick(key.id) },
                                )
                            }
                    },
            ),
        )
    }

/** 筛选菜单里那颗键的行首图标 —— 与首页「按键列表」同一套（侧键那颗 / 第几颗自定义键）。 */
internal fun keyFilterIcon(
    cfg: Config,
    key: KeyId,
): ImageVector =
    if (key.isCustom) {
        PageIcons.menuKeyNumber(cfg.customKeys.indexOfFirst { it.id == key.id }.coerceAtLeast(0))
    } else {
        DrawnIcons.MenuSideKey
    }
