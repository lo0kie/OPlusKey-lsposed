package com.lookie.opluskey.ui.screen.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SwitchRow
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.HOME_ACTION_ROWS
import com.lookie.opluskey.ui.model.HOME_BINDING_ROWS
import com.lookie.opluskey.ui.model.HOME_MORE_ROWS
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.SHOW_HOME_MINI_ANIM
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.setColorOsApi
import com.lookie.opluskey.ui.setMiniWindowAnim
import com.lookie.opluskey.ui.setModuleEnabled
import com.lookie.opluskey.ui.setToastOnRun
import com.lookie.opluskey.ui.setVibrate
import com.lookie.opluskey.ui.tickNow
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

// 首页那三张卡 —— 从 `HomeScreen.kt` 拆出来。
//
// `HomeScreen` 原来是一个 434 行的函数：状态、链路自检、提示条、三张卡、两个确认窗、
// 一个菜单全平铺在一起。按**一张卡**切开之后，页面只剩「铺顺序 + 那两条 effect」，
// 卡里的人只管自己那几行。

/**
 * 「按键绑定」那张卡 —— 第 1 行主开关、第 2 行接口选择、第 3 行进「按键绑定」子页。
 *
 * @param pageEnabled 整页总闸（含主开关自己）—— 只给第 2 行用
 */
@Composable
internal fun SettingsPage.HomeBindingCard(
    pageEnabled: Boolean,
    ui: HomeUiState,
) {
    val tick = rememberTick()
    // 主开关写的是**模块总闸**（`GlobalConfig.enabled`），不是「侧键那颗键的开关」——
    //  两者拆开之后，「停用这颗键」只停那一颗（⋮ 菜单里），这个开关停的是整个模块。
    val moduleOn = configState.config.value.global.enabled
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        // 主开关是这张卡的第一行，**不能被自己锁死**：它一旦关掉，全页（含这一页其它控件、
        // 「按键绑定」子页、顶栏菜单三项）都禁用 —— 它要是跟着一起禁用，就再也开不回来了。
        //  所以它的 `enabled` 吃的是 [linkReady]（宿主侧在跑 **且** 注入侧报到了）**外加**
        // `!configReadOnly` 与 `deviceSupported`，**不吃 `pageEnabled`**（后者含它自己）。
        // 这几个条件里都没有主开关的任何成分，所以「关掉它 ⇒ 它自己被禁用」这条死路不存在。
        //
        //  注意：`!configReadOnly` 这一条是被真机逼出来的：只读态下刚进页时
        // 链路还没报到（`linkReady=false`）⇒ 主开关是灰的；等注入侧回包一到、`linkReady` 变 true，
        // 它就**自己重新亮起来**了，用户当场拨了三下（每次都被 `write` 拒掉）。
        // **「拨了没反应」比「一开始就灰着」更难读**，所以这一档必须一起禁。
        //
        // 它写的是 [GlobalConfig.enabled] —— 执行侧的总闸（注入侧 `ConfigChannel.enabledOf`
        // 与宿主侧各有一道 `if (!…enabled) return`），所以这一开关不只是界面置灰，是真的把功能关掉。
        //
        // 开关切换交给整行点击 —— 与「振动反馈」行同一条规矩（行与开关各切一次会「闪一下又变回去」）。
        // 库 `Switch` 自己那次无条件振动由 [SwitchRow] 统一掐掉，只留 `tick()` 这条通用通道。
        SwitchRow(
            title = I18n.get(Strings.SETTING_ENABLE_BINDING, "启用按键绑定"),
            // 开着时说明「关掉会怎样」；关着时直接报状态 —— 光看一个灰开关不知道后果
            summary =
                if (moduleOn) {
                    // （14 字）正好一行放得下，再多一个字就折行、把这张卡撑高
                    I18n.get(Strings.HOME_SWITCH_DESC_ENABLED, "关闭后此模块将停用")
                } else {
                    I18n.get(Strings.HOME_SWITCH_DESC_DISABLED, "关闭后侧键交还系统，立即生效")
                },
            // 行首图标与下一行对齐用（不给的话两行的标题左边缘会差一个图标宽）。
            // 电源符号 = 「这一行是总开关」。
            icon = PageIcons.Power,
            checked = moduleOn,
            position = cardListPositionOf(0, HOME_BINDING_ROWS),
            enabled = linkReady && !configState.readOnly,
        ) {
            tick()
            // 打开之前先要一次确认：没确认过就只弹窗、**不写盘**。
            //  顺序不能反过来 —— 先写后弹等于「先启用了再问你同不同意」。
            //  关闭不需要确认，直接写。
            if (it && !configState.config.value.disclaimerAccepted) {
                XLog.w("免责声明：还没确认过，弹窗（总闸先不打开）")
                ui.disclaimerPending = true
            } else {
                setModuleEnabled(it)
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        // 第 2 行：接口选择 —— 走 ColorOS 专属系统接口，还是 AOSP 标准接口。
        SwitchRow(
            title = I18n.get(Strings.HOME_COLOROS_TITLE, "使用 ColorOS 接口"),
            // 11 字：与上一行那两句同档，一行放得下，不把卡片撑高
            summary = I18n.get(Strings.HOME_COLOROS_DESC, "关闭后部分命令将使用 AOSP 标准接口"),
            icon = PageIcons.ColorOsLink,
            checked = configState.config.value.global.colorOsApi,
            position = cardListPositionOf(1, HOME_BINDING_ROWS),
            // 吃整页总闸（含主开关自己）：主开关一关，这一行跟着灰
            enabled = pageEnabled,
        ) {
            tick()
            setColorOsApi(it)
        }

        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        BindingEntryRow(pageEnabled)
        //  这张卡**三行**：第 1 行主开关、第 2 行接口选择、第 3 行进「按键绑定」子页。
        // 「响铃状态」那两档（`setRingerModeInternal`）已删，理由见「按键绑定」小标题上方那段注释。
    }
}

/**
 * 「按键行为」那张卡 —— 振动反馈 / 执行时提示（两个**全局**项，不分手势）。
 *
 *  「长按连发」那几个设置不在这张卡里（它们只管长按那一个手势，摆在首页会让人以为对三个手势
 *  都生效，见 `LongRepeatCard`）。
 */
@Composable
internal fun SettingsPage.HomeActionsCard(pageEnabled: Boolean) {
    val tick = rememberTick()
    val cfg = configState.config.value
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        SwitchRow(
            title = I18n.get(Strings.HOME_VIBRATE_TITLE, "振动反馈"),
            summary = I18n.get(Strings.HOME_VIBRATE_DESC, "命令触发时振动"),
            icon = PageIcons.Vibrate,
            checked = cfg.global.vibrate,
            position = cardListPositionOf(0, HOME_ACTION_ROWS),
            // `enabled = false` 时 COUI 不挂 `clickableModifier`、开关也跟着灰，
            // 行点击与开关点击自然都失效。
            enabled = pageEnabled,
        ) {
            tickNow()
            setVibrate(it)
        }

        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))

        // 「执行时提示」：执行命令时弹一条 Toast（内容是**命令名**）。
        //  它是**全局**项，不分命令 —— 它回答的是「按下去到底有没有被触发」，与「这条命令是什么」无关。
        //  判定侧读它见 `host/action/CustomCommandRunner`（宿主进程里执行时**现读**配置，
        // 所以拨完开关立刻生效，不必重启）。
        SwitchRow(
            title = I18n.get(Strings.HOME_TOAST_TITLE, "执行时提示"),
            summary = I18n.get(Strings.HOME_TOAST_DESC, "执行命令时系统提示"),
            icon = PageIcons.CommandToast,
            checked = cfg.global.toastOnRun,
            position = cardListPositionOf(1, HOME_ACTION_ROWS),
            enabled = pageEnabled,
        ) {
            tick()
            setToastOnRun(it)
        }

        // 「前台转小窗时使用动画」：`zoom:mini` 那一下要不要播缩小动画。
        //  关（默认）= 原地收、无动画；开 = 原地收成小窗后紧接着让框架自己的动画器播那一段
        //  （插值 / 矩阵 / spring 全是框架在跑，模块只交起止参数，配方见 `docs/zoom-window.md` §十二）。
        //  **现行隐藏**（[SHOW_HOME_MINI_ANIM] = false）：这一档还没收敛（终点几何是竖屏标定值、
        //  横屏播不了），先把入口收起来 —— 配置字段与执行侧都留着，改回 true 即恢复。
        if (SHOW_HOME_MINI_ANIM) {
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            SwitchRow(
                title = I18n.get(Strings.HOME_MINI_ANIM_TITLE, "前台应用转小窗时使用动画"),
                summary = I18n.get(Strings.HOME_MINI_ANIM_DESC, "全屏应用缩成小窗时播放动画（仅竖屏）"),
                icon = PageIcons.MiniWindow,
                checked = cfg.global.miniWindowAnim,
                position = cardListPositionOf(2, HOME_ACTION_ROWS),
                enabled = pageEnabled,
            ) {
                tick()
                setMiniWindowAnim(it)
            }
        }
    }
}

/**
 * 「其他」那张卡 —— 进「更多选项」页 + 捐赠打赏。
 *
 *  两行都**不吃任何闸**（用户要求「首页的更多行不跟随总闸禁用」）：
 *   · 「更多选项」是进那一页的**唯一入口**，而那一页里有「运行状态」—— 链路没齐 / 配置只读 /
 *  主开关关着时，用户正需要进去看断在哪一段（机型清单不参与任何禁用，见 AGENTS.md 一、13）；
 *   · 打赏与模块启没启用无关，整页禁用时它照样该能点。
 *  所以行尾箭头与行首图标恒用常规色，不按 `pageEnabled` 换灰。
 */
@Composable
internal fun SettingsPage.HomeOtherCard(ui: HomeUiState) {
    val tick = rememberTick()
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        BasicComponent(
            title = I18n.get(Strings.MORE_OPTIONS_TITLE, "更多选项"),
            // 副标题只报这一页有什么，不交代机制（「一起改短」）
            summary = I18n.get(Strings.HOME_MORE_OPTIONS_SUMMARY, "运行状态与配置备份"),
            startAction = { Icon(PageIcons.MoreOptions, null) },
            endActions = {
                Icon(
                    PageIcons.EnterArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ROW_END_ICON_SIZE),
                    tint = COUITheme.colorScheme.onSurfaceVariantActions,
                )
            },
            cardListPosition = cardListPositionOf(0, HOME_MORE_ROWS),
            onClick = {
                navTo(Screen.ABOUT)
            },
        )

        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))

        // 「捐赠打赏」：点开一层菜单（三个去处），选中一个再弹**居中**的收款码窗口。
        //  与「更多选项」并排在这一张卡里 —— 两行都是「偶尔才点一次」的去处。
        //  行尾给选择器字形（读作「点了要选点什么」），不是进下一页的箭头，与「语言」那一行同一套。
        BasicComponent(
            title = I18n.get(Strings.HOME_DONATE, "捐赠打赏"),
            summary = I18n.get(Strings.HOME_DONATE_SUMMARY, "您的慷慨将帮助此项目持续发展"),
            startAction = { Icon(PageIcons.Donate, null) },
            endActions = {
                Icon(
                    PageIcons.Selector,
                    contentDescription = null,
                    modifier = Modifier.size(ROW_END_ICON_SIZE),
                    tint = COUITheme.colorScheme.onSurfaceVariantActions,
                )
            },
            cardListPosition = cardListPositionOf(1, HOME_MORE_ROWS),
            modifier = Modifier.preciseClickAnchor(ui.donateClick),
            onClick = {
                tick()
                ui.showDonateMenu = true
            },
        )
    }
}

/**
 * 「按键绑定」那张卡的第 2 行 —— 进「按键绑定」子页（纯跳转，行尾只有箭头）。
 *
 * 进不去那个页面，也就不存在「关着还能改配置」。
 */
@Composable
private fun SettingsPage.BindingEntryRow(pageEnabled: Boolean) {
    // 第 3 行：进「按键绑定」子页（纯跳转，行尾只有箭头）。
    // 进不去那个页面，也就不存在「关着还能改配置」。
    //
    //  行尾箭头**自己画**：库的 `ArrowPreference` 那颗是 COUI 图标集的 12x24 窄箭头（描边 1.4），
    // 与页面其余 Tabler 图标（描边 2）不是一支笔。自己画就得自己管两档色 —— 照抄库那支选择器
    // （`onSurfaceVariantActions` / `disabledOnSecondaryVariant`）。
    // 尺寸见 [ROW_END_ICON_SIZE]：24dp 下 Tabler 那一段折线正好与库原箭头一样高。
    val bindingIconTint by animateColorAsState(
        targetValue =
            if (pageEnabled) {
                COUITheme.colorScheme.onSurfaceContainer
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "HomeBindingIcon",
    )
    val bindingArrowTint by animateColorAsState(
        targetValue =
            if (pageEnabled) {
                COUITheme.colorScheme.onSurfaceVariantActions
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "HomeBindingArrow",
    )
    BasicComponent(
        title = I18n.get(Strings.SETTING_KEY_BINDING, "按键绑定"),
        summary = I18n.get(Strings.HOME_BINDING_CARD_SUMMARY, "配置单击、双击、长按对应的动作"),
        startAction = {
            Icon(
                PageIcons.TouchApp,
                contentDescription = null,
                tint = bindingIconTint,
            )
        },
        endActions = {
            Icon(
                PageIcons.EnterArrow,
                contentDescription = null,
                modifier = Modifier.size(ROW_END_ICON_SIZE),
                tint = bindingArrowTint,
            )
        },
        cardListPosition = cardListPositionOf(2, HOME_BINDING_ROWS),
        enabled = pageEnabled,
        onClick = {
            navTo(Screen.BINDING)
        },
    )
}
