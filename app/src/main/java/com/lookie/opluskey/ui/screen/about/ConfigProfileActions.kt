package com.lookie.opluskey.ui.screen.about

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.config.ConfigProfiles
import com.lookie.opluskey.core.config.ProfileCommands
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.NameDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.escapeEmphasis
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.VarListSignal
import com.lookie.opluskey.ui.write

// 「配置」那四个**动作**（切换 / 新建 / 重命名 / 删除）与命名窗。
//
// 入口都在「切换配置」那一层菜单里（见 `AboutMenus` 的 `ProfileMenu`）：它们只对「配置」这个东西
// 有意义，与列表放在同一层里，卡片上就只剩「切换 / 导出 / 导入」三件事。
// 菜单行的拼装留在 `AboutMenus`，这里只有「点了之后干什么」。

/**
 * 换了一份配置之后的三件事：落盘广播 + 重读命令库 + 让变量页重读清单。
 *
 *  命令库是**跟着配置走**的（见 [ConfigProfiles.switchTo] / [ProfileCommands.swap]），切换把两边
 *  一起换了之后，页面手上这份命令表还是旧的 —— 不重读的话命令页与绑定页列的是上一份的命令。
 *
 *  变量表同样跟着走（[ProfileVars]）：两页变量清单都是 `remember` 住的，不打个记号它们会一直
 *  显示上一份配置的那几颗 —— 而这一位（`VarListSignal`）同时管「改过值」与「换过配置」，
 *  两页各自 `LaunchedEffect` 跟着它重读。
 *
 *  **写失败（含只读态）时不再往下接**：那两份表该留在旧的那份上 —— 接着读盘只会让界面变成
 *  「提示保存失败、内容却换了」。
 *
 *  只读态不动命令表：那时手上这份本来就不是盘上的真相（变量那两页是只读显示的，照读即可）。
 *
 * @return [CommandsSave.Ok] 才说明「换成了」；调用方据此决定要不要报成功
 */
internal fun SettingsPage.applySwitchedConfig(
    next: Config,
    what: String,
): CommandsSave {
    val saved = write(next, what)
    if (saved == CommandsSave.Failed) return saved
    if (!configState.commandsReadOnly) {
        ProfileCommands.read(ConfigProfiles.CURRENT_SLOT)?.let { configState.commands.value = it }
    }
    VarListSignal.bump()
    return saved
}

/** 切到某一份：写进当前（连同广播），失败就只提示不动界面。 */
internal fun SettingsPage.switchToProfile(item: ConfigProfiles.Item) {
    val next =
        ConfigProfiles.switchTo(item.slot)
            ?: run {
                hint(I18n.get(Strings.PROFILE_SWITCH_FAILED, "切换失败：这一份读不出来"))
                return
            }
    //  落盘那一步失败时 `write` 已经说过一句（「保存失败」/「只读态」），这里不再盖掉它
    if (applySwitchedConfig(next, "切换配置 → ${item.displayName}") == CommandsSave.Failed) return
    hint(I18n.format(Strings.PROFILE_SWITCHED, item.displayName))
}

/**
 * 新建一份**空白**配置 [name]，然后**切到它**。
 *
 *  空白是用户口径（「新建配置应该是一份空配置」）：绑定全回默认、没有组合键 / 场景表、命令库
 *  也空 —— 切过去就是一张白纸，从头配。本机的全局设置跟着带过来（见 `ConfigProfiles.createAs`）。
 *
 *  切过去同样是口径（「新建后弹命名、确认后切换到新配置」）：他刚给这份起了名字，就是要用它；
 *  不切的话卡片上写着「当前：原来配置」，看着像没建成。
 *
 *  第二步失败只报「已新建」——新配置确实已经在盘上了，只是没能换上来。
 */
internal fun SettingsPage.createProfile(name: String) {
    val slot =
        ConfigProfiles.createAs(name)
            ?: run {
                //  满额与写盘失败分开报：已经三份时用户该知道的是「到上限了」，不是「失败了」
                val reason =
                    if (ConfigProfiles.isFull()) {
                        I18n.get(Strings.PROFILE_FULL, "最多三份配置")
                    } else {
                        I18n.get(Strings.PROFILE_NEW_FAILED, "新建失败")
                    }
                hint(reason)
                return
            }
    XLog.w("配置：新建 $name（槽 $slot）")
    val next =
        ConfigProfiles.switchTo(slot)
            ?: run {
                hint(I18n.format(Strings.PROFILE_CREATED, name))
                return
            }
    //  写失败时 `write` 已经提示过 —— 再报一句「已新建」会把它盖掉，用户以为切过去了
    if (applySwitchedConfig(next, "新建配置并切换 → $name") == CommandsSave.Failed) return
    hint(I18n.format(Strings.PROFILE_CREATED, name))
}

/**
 * 改名：只改**当前配置**（名字跟着内容走，见 [ConfigProfiles.renamed]）。
 *
 *  落盘走通用写入通道（`write`）而不是自己写文件：那条路负责页面状态、配置广播与重问注入侧，
 *  少一样的话卡片上的名字变了、注入侧手上这份却还带着旧名字。
 */
internal fun SettingsPage.renameProfile(name: String) {
    val current = configState.config.value
    val next =
        ConfigProfiles.renamed(current, name)
            ?: run {
                //  只有「默认」配置会走到这儿 —— 空名字在弹窗里点不动「确定」
                hint(I18n.get(Strings.PROFILE_RENAME_DEFAULT, "默认配置不许改名"))
                return
            }
    //  名字没变就什么都不做：不落盘、也不报「已重命名」
    if (next == current) return
    if (write(next, "重命名配置 → ${next.profileName}") == CommandsSave.Failed) return
    hint(I18n.format(Strings.PROFILE_RENAMED, next.profileName))
}

/**
 * 删掉一条**读不出来的**配置 —— 它切不过去、也没有名字，列表里点它只剩这一条路。
 *
 *  这一条存在本身就是「自救入口」：文件坏了的那一格占着位置（`isFull` 按槽位算），
 *  不给删的话「新建 / 导入」会永久灰着。
 */
internal fun SettingsPage.deleteBrokenProfile(item: ConfigProfiles.Item) {
    if (!item.broken) return
    if (!ConfigProfiles.delete(item.slot)) {
        hint(I18n.get(Strings.PROFILE_DELETE_FAILED, "删除失败"))
        return
    }
    hint(I18n.get(Strings.PROFILE_DELETED, "已删除"))
}

/**
 * **删除当前配置**。
 *
 *  当前生效的永远是那个固定槽（执行侧只读它），所以「删掉当前」只能是两步：先换一份上来，
 *  再把原来配置删掉。而换就是**两份内容互换** —— 换完之后要删的配置正好落在退路配置的槽里：
 *
 * ```
 * 换之前：config = 要删的（名字 X）   config-2 = 默认
 * 换之后：config = 默认               config-2 = 要删的（名字 X）   ← 删这一槽
 * ```
 *
 *  退路优先选「默认」配置（升级时落下的退路，删不掉也最该回去）；没有就选最靠前的一份。
 *  当前是「默认」配置时不许删 —— 那就没有地方可以退回去了。
 */
internal fun SettingsPage.deleteCurrentProfile() {
    val current = configState.config.value
    if (current.profileName.isBlank()) {
        hint(I18n.get(Strings.PROFILE_DELETE_DEFAULT, "默认配置不许删除"))
        return
    }
    val others = ConfigProfiles.list().filter { !it.isCurrent }
    val fallback = others.firstOrNull { it.isDefault } ?: others.firstOrNull()
    if (fallback == null) {
        XLog.w("配置删除：没有可以退回去的配置（只剩当前这一份带名字的），不动")
        hint(I18n.get(Strings.PROFILE_DELETE_FAILED, "删除失败"))
        return
    }
    val next =
        ConfigProfiles.switchTo(fallback.slot)
            ?: run {
                hint(I18n.get(Strings.PROFILE_SWITCH_FAILED, "切换失败：这一份读不出来"))
                return
            }
    val switched = applySwitchedConfig(next, "删除当前配置 → 先切到 ${fallback.displayName}")
    //  **没落盘就不能接着删**：那一步删的是 `fallback.slot`，而切换没成时那一槽里还是退路配置本身
    //  —— 接着删就是「本来只想删当前，结果把退路删了」。
    val deleted = switched != CommandsSave.Failed && ConfigProfiles.delete(fallback.slot)
    //  切换本身没成时 `write` 已经说过一句（保存失败 / 只读态），这里不再盖掉它；
    //  广播没通知到注入侧时那条提示也占着底部那一行，同样不补「已删除」。
    if (switched != CommandsSave.Failed && !deleted) {
        hint(I18n.get(Strings.PROFILE_DELETE_FAILED, "删除失败"))
    } else if (deleted && switched == CommandsSave.Ok) {
        hint(I18n.format(Strings.PROFILE_DELETED_SWITCHED, current.profileName, fallback.displayName))
    }
}

/**
 * 「删除配置」的确认窗 —— 删的是**当前配置**，正文里点名是哪一份。
 *
 *  与「删除命令」「清空配置」同一套控件（取消中性色 + 动作项红）：一整份配置连同它的命令库
 *  一起消失，且没有回收站，点一下就走太轻。
 */
@Composable
internal fun SettingsPage.ProfileDeleteConfirmDialog(ui: AboutUiState) {
    val name = configState.config.value.profileName
    ConfirmDialog(
        show = ui.showDeleteConfirm,
        title = I18n.get(Strings.DIALOG_DELETE_PROFILE_TITLE, "删除配置"),
        summary =
            I18n.format(
                Strings.DIALOG_DELETE_PROFILE_MESSAGE,
                //  「默认」配置没有名字（这时菜单里那一行本来就是灰的，走不到这里）——
                //  仍然给一个能读的说法，免得万一出现一个空书名号
                escapeEmphasis(name.ifBlank { I18n.get(Strings.PROFILE_DEFAULT, "默认配置") }),
            ),
        action = I18n.get(Strings.COMMON_DELETE, "删除"),
        onDismiss = { ui.showDeleteConfirm = false },
        onConfirm = {
            ui.showDeleteConfirm = false
            deleteCurrentProfile()
        },
    )
}

/**
 * 「读不出来的那一份」的删除确认 —— 与 [ProfileDeleteConfirmDialog] 同一套控件（取消中性色 +
 * 动作项红），但删的是**指定那一槽**，不是当前配置：那一份已经读不出来了，删掉不会影响当前这份。
 */
@Composable
internal fun SettingsPage.ProfileBrokenDeleteDialog(ui: AboutUiState) {
    val item = ui.brokenToDelete
    ConfirmDialog(
        show = item != null,
        title = I18n.get(Strings.DIALOG_DELETE_PROFILE_TITLE, "删除配置"),
        summary = I18n.get(Strings.PROFILE_BROKEN_DELETE, "这一份已经读不出来了，删掉它才能腾出位置新建配置"),
        action = I18n.get(Strings.COMMON_DELETE, "删除"),
        onDismiss = { ui.brokenToDelete = null },
        onConfirm = {
            ui.brokenToDelete = null
            item?.let { deleteBrokenProfile(it) }
            //  删完重读一遍：那一行要从菜单里消失（下次点开时是新的列表）
            ui.profiles = ConfigProfiles.list()
        },
    )
}

/**
 * 命名的输入窗 —— 新建与重命名共用一个（「填一个名字」这一类弹窗与添加按键同一个 [NameDialog]）。
 *
 *  两条路只差**标题**与**确认之后干什么**（见 [ProfileNaming]）；起点由点那一行时算好放进来，
 *  所以这里不读盘。
 */
@Composable
internal fun SettingsPage.ProfileNamingDialog(ui: AboutUiState) {
    val naming = ui.naming
    NameDialog(
        show = naming != null,
        title =
            when (naming?.kind) {
                ProfileNaming.Kind.NEW -> I18n.get(Strings.PROFILE_NEW, "新建配置")
                else -> I18n.get(Strings.PROFILE_RENAME, "重命名")
            },
        label = I18n.get(Strings.PROFILE_NAME, "配置名称"),
        //  与「添加按键 / 重命名按键」同一颗标签：这一格填的都是**一个名字**
        icon = PageIcons.LabelImportant,
        initial = naming?.initial.orEmpty(),
        onDismiss = { ui.naming = null },
        onConfirm = { name ->
            ui.naming = null
            when (naming?.kind) {
                ProfileNaming.Kind.NEW -> createProfile(name)
                ProfileNaming.Kind.RENAME -> renameProfile(name)
                null -> Unit
            }
        },
    )
}
