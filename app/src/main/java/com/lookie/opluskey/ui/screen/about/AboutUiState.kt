package com.lookie.opluskey.ui.screen.about

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.config.ConfigProfiles
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 「更多选项」页上那三个**行内菜单**的锚点与开关（语言 / 反馈问题 / 作者）。
 *
 *  三样东西长得一样，以前是三对 `remember { }` 摊在 `AboutScreen` 的开头 —— 谁和谁是一对
 *  只能靠变量名认；收成一个对象之后，`ui.langClick` / `ui.showLangMenu` 天然成组，
 *  菜单那三个 composable 也只需要一个参数。
 *
 *  注意：锚点必须**跟着那一行**（`preciseClickAnchor` 挂在行上），所以它建在这里、由行与菜单
 *  两边共用一份 —— 各建一份的话菜单会弹在别处。
 */
internal class AboutUiState {
    /** 「语言」那一行。 */
    val langClick = PreciseClickState()
    var showLangMenu by mutableStateOf(false)

    /** 「反馈问题」那一行（三个反馈渠道）。 */
    val feedbackClick = PreciseClickState()
    var showFeedbackMenu by mutableStateOf(false)

    /** 「作者」那一行（GitHub / 酷安两个去处，见 [com.lookie.opluskey.ui.model.AuthorTarget]）。 */
    val authorClick = PreciseClickState()
    var showAuthorMenu by mutableStateOf(false)

    /** 「界面颜色」那一行（跟随系统 / 浅色 / 深色）。 */
    val themeClick = PreciseClickState()
    var showThemeMenu by mutableStateOf(false)

    /** 「系统配色」那一行（五套强调色，见 [com.lookie.opluskey.ui.model.PagePalette]）。 */
    val paletteClick = PreciseClickState()
    var showPaletteMenu by mutableStateOf(false)

    /**
     * 「切换配置」那一行 —— 点开的是一层**菜单**（先列配置，底部是管理那三行）。
     *
     *  与其他几行同一个形状（语言 / 配色）：一行一档、点一下即作数。配置的**全部**管理动作
     *  （切换 / 新建 / 重命名 / 删除）都收在这一层菜单里 —— 首页 `⋮` 恢复原样，不再有配置相关项。
     */
    val profileClick = PreciseClickState()
    var showProfileMenu by mutableStateOf(false)

    /**
     * 「新建 / 重命名」那个输入窗：`null` = 关着。
     *
     *  两条路只在**标题**与**确认之后干什么**上不同，所以共用一个窗、一个状态（见 [ProfileNaming]）。
     *  起点（输入框的初值）也在里面 —— 新建是「配置 N」（要读一次盘上现在有几份），重命名是当前名字；
     *  两者都在**点那一行的那一刻**算好，不在组合期读盘。
     */
    var naming by mutableStateOf<ProfileNaming?>(null)

    /**
     * 「删除配置」的确认窗开着没有。
     *
     *  删掉一整份配置（连同它自己的命令库）不可恢复，与「清空配置」「删除命令」同一档代价：
     *  点一下就走太轻，所以先问一句。
     */
    var showDeleteConfirm by mutableStateOf(false)

    /**
     * 「读不出来的那一份」的删除确认 —— `null` = 关着。
     *
     *  那一份切不过去、也没有名字可改，列表里点它只剩「删掉」这一条路；删掉之后那个槽才腾出来
     *  （`isFull` 按槽位算，不删的话「新建 / 导入」会永久灰着）。
     */
    var brokenToDelete by mutableStateOf<ConfigProfiles.Item?>(null)

    /**
     * 「导出配置」那一行 —— 先选一份（同样是菜单）。
     *
     *  只有一份时这层菜单里也只有一行（「默认」）—— 仍然照常弹：**配置列表永远是同一形状**，
     *  不为「只有一份」特例出一个「点了就直接保存」的分支。
     */
    val exportClick = PreciseClickState()
    var showExportMenu by mutableStateOf(false)

    /**
     * 那几层菜单共用的那一份**配置列表** —— 点哪一行时读一次（盘上扫出来的，要读文件）。
     *
     *  同一时刻只可能开着一层，所以共用一格；关着时是上一次的结果（只用来铺行、数「有几份能删」）。
     */
    var profiles by mutableStateOf<List<ConfigProfiles.Item>>(emptyList())

    /**
     * 「签名」那一行**展开了没有** —— 指纹平时收着，点一下才列出来（见 `ui/ExpandableRow`）。
     *
     *  放进这里而不是 `remember` 在行里：这一位要在整页重组（比如切语言）之后仍保持，
     *  与上面几个菜单开关同一档。
     */
    var signatureExpanded by mutableStateOf(false)
}

/**
 * 「新建 / 重命名」那个输入窗这一次要做什么。
 *
 * @param kind 新建一份，还是给当前配置改名
 * @param initial 输入框的起点（新建给「配置 N」，重命名给当前配置的名字）
 */
internal data class ProfileNaming(
    val kind: Kind,
    val initial: String,
) {
    enum class Kind { NEW, RENAME }
}
