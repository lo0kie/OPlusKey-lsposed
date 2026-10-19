package com.lookie.opluskey.ui.screen.home

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.MenuPopup
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.acceptDisclaimerAndEnable
import com.lookie.opluskey.ui.model.DISCLAIMER_TEXT
import com.lookie.opluskey.ui.model.DISCLAIMER_TITLE
import com.lookie.opluskey.ui.model.DONATE_LIST_TITLE
import com.lookie.opluskey.ui.model.DonateTarget
import com.lookie.opluskey.ui.model.FREE_NOTICE_TEXT
import com.lookie.opluskey.ui.model.FREE_NOTICE_TITLE
import com.lookie.opluskey.ui.model.PageIcons

// 首页那两处「弹出来」的东西 —— 两次确认，和捐赠那个菜单 + 收款码窗口。

/**
 * 首次启用（以及清空配置之后再启用）要的那两次确认。
 *
 *  第一个归「启用按键绑定」那一行管（**模块总闸**），确认之后由 [acceptDisclaimerAndEnable]
 *  一次写盘（记录 + 总闸）；第二个是紧接着的「温馨提醒」。
 *
 *  这道闸只服务总闸：⋮ 菜单里「停用 / 启用这颗键」是**逐键**开关，与它无关。
 */
@Composable
internal fun SettingsPage.HomeDisclaimers(ui: HomeUiState) {
    ConfirmDialog(
        show = ui.disclaimerPending,
        title = DISCLAIMER_TITLE,
        summary = DISCLAIMER_TEXT,
        action = I18n.get(Strings.DIALOG_DISCLAIMER_ACKNOWLEDGE, "我已了解"),
        // 不丢用户任何东西的确认 ⇒ 走主题色，不给红：红会读成「这个操作有危险」，
        // 而这里只是「你点的那一下要你先看一眼说明」。
        positiveDanger = false,
        onDismiss = {
            XLog.w("免责声明：没确认，不启用（总闸保持关闭）")
            ui.disclaimerPending = false
        },
        onConfirm = {
            XLog.w("免责声明：已确认，启用按键绑定")
            ui.disclaimerPending = false
            acceptDisclaimerAndEnable()
            // 接着弹「温馨提醒」：确认免责声明的人正是最该被告知「这模块不收费」的那个人。
            //  两个弹窗同一帧切换（上一个在退场、这一个在进场）—— 这是控件给的样子，
            //  要等前者退净得用库里那个 `onDismissFinished`，而 [ConfirmDialog] 没往外露。
            ui.freeNoticePending = true
        },
    )

    // 「温馨提醒」：模块完全免费、认准官方正版。
    //  两颗按钮（确认 / 取消）**都只是关掉它** —— 它不写盘、不拦操作，所以两个回调是同一件事；
    //  分两个入口只是把「看过了」和「划走」分成两种按法。
    ConfirmDialog(
        show = ui.freeNoticePending,
        title = FREE_NOTICE_TITLE,
        summary = FREE_NOTICE_TEXT,
        action = I18n.get(Strings.DIALOG_CONFIRM, "确认"),
        positiveDanger = false,
        onDismiss = { ui.freeNoticePending = false },
        onConfirm = { ui.freeNoticePending = false },
    )
}

/**
 * 「捐赠打赏」那一行的菜单（三个去处）+ 选中之后的**居中**收款码窗口。
 *
 *  菜单里最后还多一行「捐赠列表」（它是**进下一层页面**，所以吃组间那条槽、行尾给
 *  「打开新页面」那颗字形）；收款码窗口在图还没进包时退成品牌字形（见 [DonateDialog]）。
 */
@Composable
internal fun SettingsPage.HomeDonateOverlays(ui: HomeUiState) {
    MenuPopup(
        show = ui.showDonateMenu,
        preciseClick = ui.donateClick,
        onDismiss = { ui.showDonateMenu = false },
        entries =
            buildList {
                DonateTarget.entries.forEachIndexed { index, target ->
                    // 相邻两行一条细线 —— 与全项目其它菜单同一件（[MenuEntry.RowLine]）
                    if (index > 0) add(MenuEntry.RowLine)
                    add(
                        MenuEntry.Row(
                            title = target.label,
                            // 三行各一个品牌字形：都是「一个去处」，只有品牌图能一眼分清
                            icon = target.icon,
                        ) {
                            ui.showDonateMenu = false
                            ui.donateTarget = target
                        },
                    )
                }
                // 加深分隔：上面三行是「怎么给」，下面这一行是「谢谢谁」—— 两件事，
                //  用组间那条**槽**分开（[MenuEntry.GroupLine]），不是行与行之间那种细线。
                add(MenuEntry.GroupLine)
                add(
                    MenuEntry.Row(
                        title = DONATE_LIST_TITLE,
                        // 这一行是「一份带金额的清单」，与上面三行「怎么给」的品牌字形分开
                        icon = PageIcons.DonateList,
                        // 行尾那一格：这一行点了是**进下一层页面**
                        submenu = true,
                        // 字形用「打开新页面」那一颗（与网格里「打开应用」进「选择应用」页同一颗，
                        //  见 [PageIcons.BrowserPlus]）—— 默认那支右箭头是「弹一层菜单」的记号，
                        //  放在这儿读不出「会离开这一层」。
                        trailingIcon = PageIcons.BrowserPlus,
                    ) {
                        ui.showDonateMenu = false
                        navTo(Screen.DONATE_LIST)
                    },
                )
            },
    )

    // 收款码窗口（居中）：内容是那张图，图还没进包时退成品牌字形（见 [DonateDialog]）
    DonateDialog(target = ui.donateTarget, onDismiss = { ui.donateTarget = null })
}
