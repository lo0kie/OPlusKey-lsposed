package com.lookie.opluskey.ui.screen.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.CommonCommand
import com.lookie.opluskey.core.model.withoutAction
import com.lookie.opluskey.core.present.CommonCommands
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageCornerFab
import com.lookie.opluskey.ui.chrome.PageFabButton
import com.lookie.opluskey.ui.chrome.PageScrollColumn
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.NameDialog
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.feedback.Feedback
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.tickNow
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.icon.commonCommandIcon
import com.lookie.opluskey.ui.model.page.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.theme.TAB_PAGE_SWITCH_MS
import com.lookie.opluskey.ui.model.theme.VISIBILITY_EXIT_MS
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.titleWithCount
import com.lookie.opluskey.ui.screen.command.StepPickerPage
import com.lookie.opluskey.ui.screen.command.StepPickerSheet
import com.lookie.opluskey.ui.screen.command.StepSheetFilter
import com.lookie.opluskey.ui.screen.command.StepSheetSpec
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.style.VisibleAreaCentered
import com.lookie.opluskey.ui.style.visibleAreaHeight
import com.lookie.opluskey.ui.write.openAppPickerForCommonCommand
import com.lookie.opluskey.ui.write.setCommonCommands
import com.lookie.opluskey.ui.write.write
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

// 「常用命令」页 —— 管那张**自己的清单**：收录哪几条、每条叫什么、哪些出现在按键绑定页。
//
// 与「自定义命令」页是两套东西：那边写命令正文（可编辑、会出现在命令页），这边只是一串指向动作
// 的指针（`cmd:` 那一族 —— 命令库里的某条、或任意动作写法），正文由动作本身决定 ——
// 用户能动的只有名字与显不显示。两页各管各的，谁也不进对方的列表；但挑的时候**列的是同一张表**
// （命令库那一组也在，见 `addSheet`）：一条常用命令指向自己存的那条命令是正经用法。

/**
 * 这一页（[com.lookie.opluskey.ui.Screen.COMMON_COMMANDS]）。
 *
 *  三种状态都在同一页上，各有各的落点：
 *  · 点一行 → 一层菜单（改名 / 在按键绑定页显示 / 移除）；
 *  · 右下角那颗 → 抽屉（复用命令页那一支，与「额外动作」同一套过滤）；
 *  · 空清单时给居中的空态（见 [CommonCommandsSection]），别让页面看起来像坏了。
 *
 *  清单为空是**合法状态**（用户把它们全删了），不在这里偷偷回填默认那几条 —— 回填等于否决用户。
 */
@Composable
internal fun SettingsPage.CommonCommandsScreen(scrollBehavior: ScrollBehavior) {
    val pageEnabled = pageWritable()
    val list = configState.config.value.global.commonCommands
    //  这一页的所有「开着哪一个」与两段动画的时机都在这里（见 `CommonCommandsUiState`）——
    //  摊成六个 `remember` 的话，主函数一半的行数都花在声明上。
    val st = remember { CommonCommandsUiState() }
    // 每一行自己的锚点：菜单要落在**被点的那一行**上（共用一份会弹到别的行）
    val anchors = remember(list) { list.associate { it.token to PreciseClickState() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        //  空态要**在可见区里居中**：滚动容器的子项拿到的是无限高度约束（`fillMaxHeight()` 在那儿
        //  没有意义），只能由这里量一次视口再算给下面 —— 账走全项目那一支（[visibleAreaHeight]）。
        //  空态仍留在滚动内容里：它得跟着内容一起拖、一起弹。
        val emptyHeight =
            visibleAreaHeight(
                maxHeight = maxHeight,
                topInset = currentHeaderHeight(scrollBehavior),
                bottomInset = PAGE_BOTTOM_PADDING,
            )
        PageScrollColumn(scrollBehavior = scrollBehavior) {
            Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))
            CommonCommandsSection(
                list = list,
                emptyHeight = emptyHeight,
                enabled = pageEnabled,
                anchors = anchors,
                st = st,
            )
        }
        //  右下角那颗「添加命令」—— 与「添加按键」同一颗加号（这一下也是「往清单里加一条」）。
        //  位置 / 进出场走全项目那一份（`PageCornerFab`）；只读页上跟着 `pageEnabled` 收起来。
        PageCornerFab(visible = pageEnabled) {
            PageFabButton(
                onClick = {
                    tickNow()
                    st.picking = true
                },
            ) {
                Icon(PageIcons.MenuKeyAdd, contentDescription = null, tint = COUITheme.colorScheme.onPrimary)
            }
        }
    }

    //  四层浮出与两段时机各自一支（见下面那几个函数）—— 主函数只留「页面长什么样」。
    CommonCommandsMenu(list, anchors, st)
    //  改名窗自己按当前配置取那一条（确认那一笔已经写进配置了，用这里的 `list` 会覆盖回去）
    renameDialog(
        configState.config.value.global.commonCommands
            .firstOrNull { it.token == st.renameToken },
    ) {
        st.renameToken = null
    }
    CommonCommandsRemove(list, st)
    CommonCommandsAdd(list, st)
}

/**
 * 页里那一段：**空态 与 清单**，两者之间走交叉过渡。
 *
 *  空态不再摆成一行「尚未添加命令」的卡片 —— 那一行读起来像「有一条不能点的命令」，而且整页看着
 *  像坏了。现在按全项目那套整页空态来（[Feedback] + [VisibleAreaCentered]，「条件执行」页同一份）。
 *
 *  两者之间原来是 `if / else` 硬切 —— 删掉最后一条时清单「啪」地消失、空态「啪」地出现，中间没有
 *  任何交代。转场见 [COMMANDS_BODY_TRANSFORM]。
 *
 * @param emptyHeight 空态那一块的高度（可见区减顶栏、减页底留白，由调用点量好）
 */
@Composable
private fun CommonCommandsSection(
    list: List<CommonCommand>,
    emptyHeight: Dp,
    enabled: Boolean,
    anchors: Map<String, PreciseClickState>,
    st: CommonCommandsUiState,
) {
    //  目标态传的是**列表本身**，不是 `isEmpty()` 那个布尔 —— `AnimatedContent` 把旧值交给退场那一侧，
    //  若只传布尔，退场侧拿到的仍是「非空」分支、而数据已经空了 ⇒ 它渲染不出任何行，那半场会瞬间
    //  塌成 0 高，过渡等于没有。`contentKey` 只看空 / 非空 ⇒ 增删某一条（空非空没变）不重播过渡，
    //  那种变化由 `CardList` 按行自己的 `visible` 播。
    AnimatedContent(
        targetState = list,
        contentKey = { it.isEmpty() },
        transitionSpec = { COMMANDS_BODY_TRANSFORM },
        contentAlignment = Alignment.TopCenter,
        label = "common-commands",
    ) { shown ->
        if (shown.isEmpty()) {
            VisibleAreaCentered(emptyHeight) {
                //  空态按全项目那一份排（[Feedback]）：一颗字形 + 一句状态 + 一句「现在怎么加」。
                //  字形取「这里什么都没有」那张脸（[PageIcons.NoMatch]），与「条件执行」「选择应用」
                //  「已保存的命令」那几块同一个读法；上面那句说状态、下面那句说下一步 —— 只给
                //  「尚未添加命令」的话，用户还得自己去找加号在哪。
                Feedback(
                    glyph = { tint ->
                        Icon(PageIcons.NoMatch, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = tint)
                    },
                    title = I18n.get(Strings.COMMON_COMMANDS_EMPTY, "尚未添加命令"),
                    text = I18n.get(Strings.COMMON_COMMANDS_EMPTY_DESC, "使用右下角的添加按钮"),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            //  **必须自己套一层 `Column`**：`AnimatedContent` 的内容位是叠放式的，直接往里发
            //  小标题 + 卡片会让两件事叠在同一处。
            Column(Modifier.fillMaxWidth()) {
                //  条数拼进标题（`常用命令 (3)`）—— 与别处同一写法；空态那一支不画它，
                //  个数在顶栏副标题里照样看得到。
                SectionTitle(titleWithCount(I18n.get(Strings.BINDING_COMMON_COMMANDS, "常用命令"), shown.size))
                CardList(
                    rows =
                        shown.map { cmd ->
                            commonCommandRow(
                                cmd = cmd,
                                anchor = anchors[cmd.token] ?: st.coldAnchor,
                                enabled = enabled,
                                //  正在退场 / 还没进场的那一条由 `CardList` 收起（时机见 `CommonCommandsRemove` / `CommonCommandsAdd`）
                                visible = cmd.token != st.leavingToken && cmd.token != st.enteringToken,
                                onOpenMenu = { st.menuToken = cmd.token },
                            )
                        },
                )
            }
        }
    }
}

/**
 * 空态 与 清单那一层的转场：**只做交叉淡入淡出**，不插值尺寸。
 *
 *  空态那块按可见区撑满、清单再长也短于它；一旦插值高度，观感就是「先收再涨」多滑一下。
 *  时长取整块内容淡入淡出那一档（[TAB_PAGE_SWITCH_MS]）；「条件执行」页的空态 ↔ 表格同一套做法。
 */
private val COMMANDS_BODY_TRANSFORM: ContentTransform =
    ContentTransform(
        targetContentEnter = fadeIn(tween(TAB_PAGE_SWITCH_MS, easing = DecelerateEasing())),
        initialContentExit = fadeOut(tween(TAB_PAGE_SWITCH_MS, easing = AccelerateEasing())),
        sizeTransform = null,
    )

/**
 * 这一页的界面状态 —— 「哪一个浮层开着」以及进 / 退场各由哪一条在做。
 *
 *  收成一个类：这几格互相咬合（菜单点「移除」把 token 交给移除窗、确认之后又交给退场那一段、
 *  播完才真删），摊成六个 `remember` 会让主函数一半的行数花在声明上，也让「谁清掉了谁」散在四处。
 */
private class CommonCommandsUiState {
    /** 行菜单开着哪一条（按 token 记：改名之后对象会换，按下标记会指错）。 */
    var menuToken by mutableStateOf<String?>(null)

    /** 改名窗开着哪一条。 */
    var renameToken by mutableStateOf<String?>(null)

    /** 移除确认窗开着哪一条。 */
    var removingToken by mutableStateOf<String?>(null)

    /** 挑动作那层抽屉开着没有。 */
    var picking by mutableStateOf(false)

    /** 正在播退场的那一条 —— `CardList` 按 `visible` 收起它，播完才从清单里删。 */
    var leavingToken by mutableStateOf<String?>(null)

    /** 刚收录、还没播进场的那一条 —— 先按「不在场」组合一帧，下一帧才展开。 */
    var enteringToken by mutableStateOf<String?>(null)

    /**
     * 行锚点的兜底：过渡期间退场那一侧画的是**旧列表**，里面有这一页已经删掉的那条 token，
     * `anchors` 里就没有它 —— 菜单得有个从零起算的落点，不能拿 `!!`。
     */
    val coldAnchor = PreciseClickState()
}

/**
 * 确认窗在退场那两百毫秒里要画的那一条（见 [CommonCommandsRemove]）。
 *
 *  关窗是「先清数据、再播退场」：`removingToken` 一空，正文里那个名字就跟着空了 —— 而库的退场
 *  还要走两百毫秒，那一截会闪成「将「」移除」。所以照**最后画过的那一份**画（与 `ModeMenuPopups`
 *  的 `LastPick`、`DonateDialog` 的 `Last` 同一个理由）。只留一个格子就够：标题与正文都由它现算。
 */
private class LastCommand {
    var cmd: CommonCommand? = null
}

/**
 * 点一行开出来的那层菜单（改名 / 是否在绑定页显示 / 移除）。
 *
 *  **关着的时候也照样调用 [rowMenu]**，只是给一条 `null`：菜单的退场动画靠**空行表**触发
 *  （见 `PageMenu`），在这里 `?.let` 一刀切掉的话那一层是「啪」地消失的
 *  （用户口径「菜单消失没有动画」）。
 */
@Composable
private fun SettingsPage.CommonCommandsMenu(
    list: List<CommonCommand>,
    anchors: Map<String, PreciseClickState>,
    st: CommonCommandsUiState,
) {
    val cmd = list.firstOrNull { it.token == st.menuToken }
    rowMenu(
        cmd = cmd,
        list = list,
        anchor = cmd?.let { anchors[it.token] } ?: st.coldAnchor,
        onDismiss = { st.menuToken = null },
        onRename = {
            st.menuToken = null
            st.renameToken = it.token
        },
        onRemove = {
            st.menuToken = null
            st.removingToken = it.token
        },
    )
}

/**
 * 移除：先确认，再**先播退场、后真删**。
 *
 *  直接从清单里删的话那一行是硬消失（与页里其它行的规格不一致）；这里让 `CardList` 先把那一条
 *  收起（`visible = false`），退场播完再从清单里删 —— 删的那一刻它已经是零高，看不出跳变。
 *
 *  **真删不是「只从清单里摘一行」**：绑过这条动作的地方（按键的每一档、组合键、各自的场景块）
 *  一起清成「无操作」（见 `Config.withoutAction`）。只摘清单的话，那颗键会继续按那串已经念不出
 *  名字的 token 执行 —— 而用户按的是「移除」，期待的是「这一条不再存在」。
 *
 *  两笔**落在同一次落盘里**：分两次写就会出现「清单没了、绑定还在」的中间态。
 *  不给提示：后果在确认窗里已经说过（见 `removeDialog`），这一下之后那一行还会自己退场 ——
 *  再弹一句「已保存」只是噪音；写失败由 `write` 那条共同收尾报。
 *
 *  **确认窗的显隐与内容分两位**：`show` 看这一条在不在，正文照**最后画过的那一条**画（见
 *  `LastCommand`）—— 清数据发生在退场之前，正文跟着变空的话那两百毫秒里会闪成「将「」移除」。
 */
@Composable
private fun SettingsPage.CommonCommandsRemove(
    list: List<CommonCommand>,
    st: CommonCommandsUiState,
) {
    val target = list.firstOrNull { it.token == st.removingToken }
    val last = remember { LastCommand() }
    target?.let { last.cmd = it }
    removeDialog(
        show = target != null,
        cmd = target ?: last.cmd,
        onDismiss = { st.removingToken = null },
        onRemove = {
            st.removingToken = null
            st.leavingToken = it
        },
    )
    LaunchedEffect(st.leavingToken) {
        val token = st.leavingToken ?: return@LaunchedEffect
        //  **删的是最后一条时不等**：这一下整块要变成空态，高度由外层（空态 与 清单）那套交叉过渡
        //  接管；再等一轮「行收起 200ms + 结算 40ms、然后空态淡入 260ms」就是两段串在一起
        //  （「条件执行」页删最后一块同一套处理）。直接删，那一行跟着整块一起淡出。
        if (list.size > 1) delay(VISIBILITY_EXIT_MS + ROW_EXIT_SETTLE_MS)
        st.leavingToken = null
        val cfg = configState.config.value
        write(
            cfg
                .copy(global = cfg.global.copy(commonCommands = cfg.global.commonCommands.filterNot { it.token == token }))
                .withoutAction(token),
            "common_commands=remove",
        )
    }
}

/**
 * 添加：抽屉 + **进场时机**。
 *
 *  新行先按「不在场」组合一次，下一帧才翻成在场 —— 初值就是在场的话，`CardList` 那边没有
 *  一条「false → true」可播，行会直接满高出现。
 *
 *  **清单本来是空的时候例外**（见 [addSheet] 的 `onPick`）：那一下整块正从空态交叉淡入到清单，
 *  行再自己展开一遍就是同一件事播两遍。
 */
@Composable
private fun SettingsPage.CommonCommandsAdd(
    list: List<CommonCommand>,
    st: CommonCommandsUiState,
) {
    addSheet(
        list = list,
        show = st.picking,
        onDismiss = { st.picking = false },
        onAdded = { st.enteringToken = it },
    )
    LaunchedEffect(st.enteringToken) {
        if (st.enteringToken == null) return@LaunchedEffect
        withFrameNanos { }
        st.enteringToken = null
    }
}

/**
 * 改名窗 —— `cmd` 为 `null` 时整支不画（那是「没开着」）。
 *
 *  清单在自己的窗口里再读一次配置：确认那一笔已经写进配置了，拿调用点那份旧列表去拼会把它覆盖回去。
 */
@Composable
private fun SettingsPage.renameDialog(
    cmd: CommonCommand?,
    onDismiss: () -> Unit,
) {
    //  **不整支 `return`**：`NameDialog` 的开关是 `show`，退场那两百毫秒还得让窗留在组合里，
    //  当场摘掉的话它是「啪」地消失的（用户口径「dialog 消失没有动画」）。
    val list = configState.config.value.global.commonCommands
    NameDialog(
        show = cmd != null,
        title = I18n.get(Strings.COMMON_COMMANDS_RENAME, "重命名"),
        label = I18n.get(Strings.COMMON_COMMANDS_RENAME_LABEL, "名称"),
        icon = PageIcons.CommandEdit,
        initial = cmd?.name.orEmpty(),
        //  空 = 回到动作自带的名字（`CommonCommands.displayName` 那一套），所以允许空
        allowEmpty = true,
        hint = I18n.get(Strings.COMMON_COMMANDS_RENAME_HINT, "留空则使用动作名称"),
        onDismiss = onDismiss,
        onConfirm = { text ->
            onDismiss()
            //  退场途中那一下不再落盘（`cmd` 已经空了）—— 与「音量窗退场途中又点一次确定」同一个口径
            val token = cmd?.token
            if (token != null) {
                setCommonCommands(list.map { row -> if (row.token == token) row.copy(name = text.trim()) else row })
            }
        },
    )
}

/**
 * 移除确认窗 —— `show` 是开关，`cmd` 是**画什么**（两者分开：退场期间 `cmd` 仍是最后画过的那条）。
 *
 *  正文要写清**绑过它的按键会一起变成「无操作」**：这一下不只是把清单里那一行拿掉，而是把这条
 *  动作从配置里所有引用它的地方清掉（见 [com.lookie.opluskey.core.model.withoutAction]）。
 *  不写就会有人以为「只是从这一段里收拾一下」，结果按那颗键什么也不发生了 —— 反过来也一样。
 *
 *  **真删不在这一层**：确认之后把 token 交给调用点，由它先播退场、再从清单里删
 *  （见 `CommonCommandsScreen` 的 `leavingToken`）—— 在这里直接删的话那一行是硬消失。
 *
 * @param onRemove 确认之后拿这一条的 token
 */
@Composable
private fun SettingsPage.removeDialog(
    show: Boolean,
    cmd: CommonCommand?,
    onDismiss: () -> Unit,
    onRemove: (String) -> Unit,
) {
    ConfirmDialog(
        show = show,
        title = I18n.get(Strings.COMMON_COMMANDS_REMOVE_TITLE, "移除常用命令"),
        summary = I18n.format(Strings.COMMON_COMMANDS_REMOVE_BODY, cmd?.let { CommonCommands.displayName(it) }.orEmpty()),
        action = I18n.get(Strings.COMMON_COMMANDS_REMOVE, "移除"),
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            //  退场途中那一下不再落盘（`cmd` 已经空了）
            cmd?.let { onRemove(it.token) }
        },
    )
}

/**
 * 「添加命令」那层抽屉 —— 复用命令页那一支（行高 / 分组 / 二级菜单都是现成的）。
 *
 *  过滤条件与「松手额外动作」那几处**一模一样**（用户口径）：不滤命令库那一组 —— 一条常用命令
 *  当然可以指向自己存的某条命令；只剔掉两个「换一页挑」的格子（选择应用 / 自定义变量），
 *  这一页没有接那两页的去路（点一下只会关掉抽屉）。「无操作」那一格也不列：钉一条什么都不做
 *  没有意义，它只属于「额外动作」那几处（那边要的是「配了但这次不做事」）。
 *
 * @param onAdded 收录成功、且清单里已经有别的条目时拿这一条的 token —— 调用点据此播一次进场
 *   （清单本来就是空的时候**不报**：那一行跟着整块从空态淡进来，不再单独展开一遍）
 */
@Composable
private fun SettingsPage.addSheet(
    list: List<CommonCommand>,
    show: Boolean,
    onDismiss: () -> Unit,
    onAdded: (String) -> Unit,
) {
    StepPickerSheet(
        show = show,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.COMMON_COMMANDS_ADD, "添加命令"),
                current = null,
                //  「应用控制」那两档（打开应用 / 结束应用）要挑一个具体应用 —— 与「松手额外动作」
                //  那几处走同一条挑应用的路，挑完追加成清单里新的一条（落点见 `PickTarget.CommonCommand`）。
                //  这一格原来的做法是只关抽屉，于是那两行点下去什么都不发生（用户口径「打不开」）。
                onOpenPicker = { page ->
                    onDismiss()
                    when (page) {
                        StepPickerPage.App -> openAppPickerForCommonCommand()

                        StepPickerPage.AppStop -> openAppPickerForCommonCommand(stop = true)

                        //  「自定义变量」那一格这一页没有去路（`excludeTiles` 已经把它剔掉），写出来只为穷尽
                        StepPickerPage.Vars -> Unit
                    }
                },
                onPick = { token ->
                    onDismiss()
                    when {
                        //  入口格那种「标记」写法（`appctl:open` 这种）不是能执行的动作：收录进来也跑不了，
                        //  而且读盘那一道会把它丢掉（见 `CommonCommandDto.toCommonCommand`）——
                        //  当场说一句，别让它在保存之后无声消失。
                        Action.parse(token) is Action.None -> {
                            hint(I18n.get(Strings.COMMON_COMMANDS_NOT_ACTION, "请先选择具体动作"))
                        }

                        //  已经在清单里就不重复收录（同一个动作出现两次只是噪音）
                        list.any { it.token == token } -> {
                        }

                        else -> {
                            //  清单**本来是空的**时不给这一行播进场：那一下整块正从空态交叉淡入到
                            //  清单，行再自己从零高展开就是同一件事播两遍（用户口径「从空态到列表
                            //  不要滑出来」）。它直接以在场状态组合，跟着整块一起显出来。
                            if (list.isNotEmpty()) onAdded(token)
                            setCommonCommands(list + CommonCommand(token = token))
                        }
                    }
                },
                onDismiss = onDismiss,
                filter = StepSheetFilter(excludeTiles = true),
            ),
    )
}

/** 退场动画收尾的余量（毫秒）—— 让「状态落到不在场」与下一次组合错开一帧。 */
private const val ROW_EXIT_SETTLE_MS = 40L

/** 清单里的一行：点它开菜单（改名 / 显不显示 / 移除），行尾给箭头表示「还有一层」。 */
@Composable
private fun commonCommandRow(
    cmd: CommonCommand,
    anchor: PreciseClickState,
    enabled: Boolean,
    visible: Boolean,
    onOpenMenu: () -> Unit,
): CardRow =
    CardRow.Item(
        //  进 / 退场由 `CardList` 按这一位播（规格见 `VISIBILITY_ENTER` / `VISIBILITY_EXIT`）
        visible = visible,
        title = CommonCommands.displayName(cmd),
        //  自带那几条有自己的小字；用户自己收录的动作没有（小字里塞 token 只是噪音）
        summary = CommonCommands.entryOf(cmd.token)?.let { I18n.get(it.summaryKey, it.summaryFallback) },
        icon = commonCommandIcon(cmd.token),
        //  在按键绑定页不显示的那几条压一档色：一眼能看出哪些不会出现在绑定页
        enabled = enabled,
        titleTint = if (cmd.showInBinding) null else COUITheme.colorScheme.onSurfaceVariantSummary,
        arrow = true,
        modifier = Modifier.preciseClickAnchor(anchor),
        onClick = onOpenMenu,
    )

/**
 * 那一行点开的三项 —— 改名、切「在按键绑定页显示」、移除。
 *
 *  `cmd == null` = 关着，此时给**空行表**而不是整支不画：`PageMenu` 的开关就是「行表空不空」，
 *  而退场那两百毫秒里的内容与锚点由 `MenuPopup` 自己保住 —— 在这里 `if (cmd == null) return`
 *  的话菜单是「啪」地消失的（用户口径「菜单消失没有动画」）。
 *
 *  `showInBinding` 那一行的对勾照当前这一条画；关着的时候画不到（行表为空）所以不影响退场。
 */
@Composable
private fun SettingsPage.rowMenu(
    cmd: CommonCommand?,
    list: List<CommonCommand>,
    anchor: PreciseClickState,
    onDismiss: () -> Unit,
    onRename: (CommonCommand) -> Unit,
    onRemove: (CommonCommand) -> Unit,
) {
    PageMenu(
        entries =
            if (cmd == null) {
                emptyList()
            } else {
                listOf(
                    MenuEntry.Row(
                        title = I18n.get(Strings.COMMON_COMMANDS_RENAME, "重命名"),
                        icon = PageIcons.CommandEdit,
                    ) {
                        onRename(cmd)
                    },
                    MenuEntry.Row(
                        title = I18n.get(Strings.COMMON_COMMANDS_SHOW_IN_BINDING, "显示在按键绑定页"),
                        icon = PageIcons.CommonCommands,
                        //  行首换成对勾 = 现在**会**出现在那一段（与别处的勾选行同一个读法）
                        selected = cmd.showInBinding,
                    ) {
                        onDismiss()
                        setCommonCommands(
                            list.map { row -> if (row.token == cmd.token) row.copy(showInBinding = !row.showInBinding) else row },
                        )
                    },
                    MenuEntry.Row(
                        title = I18n.get(Strings.COMMON_COMMANDS_REMOVE, "移除"),
                        icon = PageIcons.CommandDelete,
                        danger = true,
                    ) {
                        //  不在这里直接删：先问一句（见 `removeDialog`）—— 移除会把它从这一段里拿掉，
                        //  而那几条是用户自己一条条加进来的，误触的代价比一次确认高。
                        onRemove(cmd)
                    },
                )
            },
        preciseClick = anchor,
        onDismiss = onDismiss,
    )
}
