package com.lookie.opluskey.ui.screen.about

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.REPO_URL
import com.lookie.opluskey.ui.state.UpdateState

// 「版本」那一行的菜单 —— 从 `AboutMenus.kt` 拆出来。
//
// 那一份里已经挤了语言 / 主题 / 配色 / 配置那几层菜单，再加一层就撞 detekt 的
// `TooManyFunctions`（一个文件 11 个函数）。这一层与「检查更新」同属版本那一行的事，单独成文件。

/** 「版本」那一行的菜单 —— 「检查更新」+「项目仓库」两处。 */
@Composable
internal fun SettingsPage.VersionMenu(ui: AboutUiState) {
    //  已经查到新版时，这一行改口叫「发现新版本」、图标从「重新查」换成「去下载」：
    //  结果已经有了，还写「检查更新」等于让人白点一次（点下去照样会弹窗，见 `AboutScreen`）。
    val found = UpdateState.hasUpdate
    PageMenu(
        entries =
            listOfNotNull(
                // 检查更新：点一下立即查，有新版弹窗、没新给句回执（见 `AboutScreen` 那个副作用）
                MenuEntry.Row(
                    title =
                        if (found) {
                            I18n.get(Strings.UPDATE_FOUND_ROW, "发现新版本")
                        } else {
                            I18n.get(Strings.UPDATE_CHECK_ROW, "检查更新")
                        },
                    icon = if (found) PageIcons.Download else PageIcons.CheckUpdate,
                    //  行尾不挂红点：有新版这件事首页顶上那块「模块状态」已经说了（见 `HomeActivationCard`），
                    //  这条只负责改口与换图标 —— 点进去照样会弹窗
                ) {
                    ui.showVersionMenu = false
                    ui.checkUpdateNow = true
                },
                // 项目仓库：跳浏览器前先问一句（与「作者 / 反馈」同一条账）
                MenuEntry.Row(
                    title = I18n.get(Strings.MORE_ABOUT_REPO, "项目仓库"),
                    icon = PageIcons.BrandGithub,
                    submenu = true,
                    trailingIcon = PageIcons.Link,
                ) {
                    ui.showVersionMenu = false
                    ui.pendingUrl = REPO_URL
                },
            ).takeIf { ui.showVersionMenu }
                .orEmpty(),
        preciseClick = ui.versionClick,
        onDismiss = { ui.showVersionMenu = false },
    )
}
