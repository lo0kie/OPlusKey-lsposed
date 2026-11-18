package com.lookie.opluskey.ui.screen.home

import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.present.ActionCatalog
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageScrollColumn
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.kit.SectionTitle
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.target.DonateTarget
import com.lookie.opluskey.ui.model.theme.HOST_ALIVE_TIMEOUT_MS
import com.lookie.opluskey.ui.model.theme.QUERY_ROUNDS
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.RowEndIcon
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.screen.variables.openVariablesForManage
import com.lookie.opluskey.ui.state.page.LINK_SILENT_RETRY_MS
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Variable
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import kotlinx.coroutines.delay

// 首页 —— 三张卡 + 两条「进页就跑一次」的副作用。
//
// 拆之前这里是 434 行一个函数：状态、链路自检、底部提示条、三张卡、两个确认窗、一个捐赠菜单
// 全平铺在一起。现在卡在 `HomeCards.kt`、弹窗与菜单在 `HomeDialogs.kt`、瞬时态在 `HomeUiState.kt`，
// 本文件只剩「铺顺序」和那两条 effect。

/** 首页。 */
@Composable
internal fun SettingsPage.HomeScreen(scrollBehavior: ScrollBehavior) {
    val ui = remember { HomeUiState() }

    //  自检**不在这一页**：它挂在 `PageContent` 的页面级副作用里 —— 挂在这里的话，
    //  一离开首页这一棵就出组合、协程被取消，`injectChecked` 永远回不来（症状：留在首页才看得到
    //  「系统侧还是旧版」，一点进子页就再也不会说）。

    // 这一页所有可交互项的**总闸**：模块总闸开着（`GlobalConfig.enabled`，主开关那一行写的就是它）
    // + 链路齐了（见 [linkReady]，它现在等同于
    // 「运行状态里除『主开关已关闭』与『使用验证』外每一段都 ok」）+ 配置可写
    // （建页那次没读成功 ⇒ 只读，写回一律被拒，见 `ConfigState.readOnly`）。
    // 有哪一条不满足时改配置不会生效，放用户去改只会误导（底部那条常驻提示会说明该怎么办）。
    //  注意：**主开关也吃这个闸**（`linkReady` + 另两条，见 [HomeActivationCard] 里那一行）。顶栏 ⋮ **不吃** ——
    // 它本身永远可点，里面只有「重启服务 / 导出日志 / 清空配置」三项跟着灰，
    // 「默认界面」（逃生口）始终可用。
    //  适配清单**不再参与**这道闸：清单外的机器照样能改（见 `core/model/DeviceSupport`），
    //  验没验过只在「更多选项」里提示。
    val pageEnabled = pageWritable()

    PageScrollColumn(scrollBehavior = scrollBehavior) {
        //  顶栏是叠在内容上的（见 `PageHeader`），所以顶部这一块要自己让出来 ——
        // 数值是**动态**的（跟着折叠走），理由见 `currentHeaderHeight`。
        //  注意：**不再另加 `PAGE_TOP_PADDING`**：这一页没有固定带，顶栏底下没有看得见的边界，
        // 而顶栏自己底部就留了 12dp（库的 `LargeTitleBottomPadding`）—— 再加那 8dp，
        // 首行距标题的空白明显比带子页大一截（真机反馈「首页的上边距比其他页多」）。
        //  带子页那一档来自带子自己的 8dp 下内边距，两边视觉上正好齐平。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        //  顶栏提示与「模块状态」那一块**已经合成一块**：新版本 / 有一件事要处理 / 激活状态 /
        //  模块总闸四件事本来都在答「这个模块现在什么情况」，分成三四块摆读起来像三四件事。
        //  它自带上面那一档间距（见 `ACTIVATION_TOP_GAP`），所以这一页仍不用写 `Spacer`。
        HomeActivationCard(ui)

        //  按键列表搬去了「按键功能」页（与组合按键列表收在同一页）：首页只留「按键操作」这一节，
        //  两列清单挤在首页会把常用入口推下去。

        // 「按键操作」那一节 —— 组合按键与「添加按键」两行（用户口径：与清单分开一张卡）。
        //  这两行不是清单里的东西，是**对这颗键 / 这组键做的事**。
        //  **不再带小标题**（用户口径），间距按 **24dp** 补：标题那一档（上内边距 + 标题行）
        //  整块留出来太空，16dp 又让两张卡粘在一起 —— 24 是中间那一档（用户口径）。
        Spacer(Modifier.height(24.dp))
        HomeKeyOpsCard(pageEnabled)

        // 「按键行为」那一整块搬进了「公共设置」页（入口在上一张「按键操作」卡的第二行）——
        //  它是**全局**开关，与「这颗键绑了什么」无关，摆在这里会把常用的入口挤下去。

        // 版本 / 仓库地址那两行搬去**独立的「更多选项」页**（`AboutScreen`，从顶栏 ⋮ 菜单进）——
        //  它们是「想知道时才看一眼」的信息，摆在首页会把常用的那几张卡挤下去。
        SectionTitle(I18n.get(Strings.HOME_OTHER_SECTION))
        HomeOtherCard(pageEnabled, ui)

        //  下面两个是**浮层宿主**（本身不占布局）：摆在块容器外面，
        //  否则块间距会在它们前后各留一档空档。
        HomeDisclaimers(ui)
        HomeDonateOverlays(ui)
    }
}

/**
 * 链路自检 —— 进页问一次（最多 [QUERY_ROUNDS] 轮）。
 *
 *  「链路上每一段在不在」⇒ 缺哪一段就报哪一段（见 `ui/wire/LinkCheck.kt` 的 `linkChecks`）。
 *  判据只能是**模块自己报活**：查宿主那支侧键服务不管用 —— 它是宿主自己的常驻服务，
 *  系统会自己把它拉起，服务在跑 ≠ 模块在跑（真机实测：重启后没注入它照样在跑）。
 *  每轮对**两端各记一次代次**、各发一句「在不在」（报活广播是一次性的，我们可能已经错过了），
 *  等一档再看代次有没有变：没变就是没加载。
 *
 *  注意：**一轮不够，要重问**（用户报的「换 ROM 后整页一直是灰的，退出去再进来才好」）：
 *  注入侧回不了包有两种常见成因 —— 它借不到 Context、或它的宿主 uid 还没解析出来
 *  （那时 fail-closed 会把问句丢掉）。两种都会自愈，只是**不是立刻**；而这条 effect 只发一次的话，
 *  页面会一直停在「未就绪」上等一个再也不会来的回包。
 *  这里最多问 [QUERY_ROUNDS] 轮，**两端都答上就提前收工** —— 已经就绪时只问一次；
 *  一直问不到也不会转下去：链路一直不通的机器本来就该一直禁用，无限轮询只是白耗电。
 */
@Composable
internal fun SettingsPage.HomeLinkSelfCheck() {
    //  键**不是**当前屏、也不是 `Unit` —— 两种极端都踩过：
    //   · `Unit`：只在这一棵进组合时跑一次，而首页被子页盖住、再露回来时**组合并不重建**
    //     （真机实测：退到「自定义命令」再回首页，那条问句还是原来那 13 条，一条没多）⇒ 链路后来
    //     自己好了（注入侧补上 Context / uid），页面仍停在未就绪，只能关掉整个设置页重进；
    //   · `screen`：切页会把**正在跑的那一趟掐掉**，而那一趟是唯一会把 `injectChecked` 置 true
    //     的地方 ⇒ 掐掉之后那句「正在检查模块…」再也没人收（它挂在页面级的 host 上、常驻不会自己
    //     走），别的页点到灰控件也回它。真机症状：自检还没问完就切页，提示条一直挂着。
    //  所以这里盯 `screen` 的一条流：**一趟自检跑完为止**（最多 [QUERY_ROUNDS] 轮）。
    //  跑完之后**不再每回首页重问** —— 判据是「这一趟宿主进程问过没有」（[HostLinkState.needsLinkCheck]）：
    //  宿主没换进程就沿用上一次的结论，换了（重启 / 被杀后重起）才重问一遍。
    LaunchedEffect(Unit) {
        snapshotFlow { screen }.collect { s ->
            // 同一趟宿主进程只问一次（判据见 `HostLinkState.needsLinkCheck`）：
            //  宿主没换过进程，问出来的必然是同一份结论，而一趟最多 QUERY_ROUNDS 轮广播。
            if (s == Screen.SHORTCUTS && link.needsLinkCheck()) {
                //  结论（跑完没有 / 宿主是哪个进程 / 退避到什么时候）由这一趟自己一次写完，
                //  见 `HostLinkState.markCheckPassed` / `markCheckSilent`。
                queryLinkOnce()
            }
        }
    }
}

/** 跑一趟链路自检（见 [HomeLinkSelfCheck]）—— 两个出口都算「问完了」，见 `injectChecked`。 */
private suspend fun SettingsPage.queryLinkOnce() {
    var hostEverAnswered = false
    var injectEverAnswered = false
    repeat(QUERY_ROUNDS) { round ->
        val hostToken = link.hostAliveToken
        val injectToken = link.injectAliveToken
        // 发不出去（广播队列满 / 进程正在死）不该把宿主 UI 进程带走
        //  —— 裸着的话异常会顺协程抛出去（保护在 `PageWire.send` 里一处）
        PageWire.send(
            context,
            HostApp.PACKAGE,
            Intent(ModuleWire.ACTION_HOST_SERVICE_QUERY),
        )
        // 注入侧（system_server）那一段：它挂在 AMS 的广播入口上，认得出这条问句
        PageWire.send(context, HostApp.PACKAGE, Intent(ModuleWire.ACTION_INJECT_QUERY))
        delay(HOST_ALIVE_TIMEOUT_MS)
        val hostAnswered = link.hostAliveToken != hostToken
        val injectAnswered = link.injectAliveToken != injectToken
        if (hostAnswered) hostEverAnswered = true
        if (injectAnswered) injectEverAnswered = true
        // 单轮漏答**不清结论**：AMS 偶尔忙一轮，清掉就 `linkReady=false` ⇒ 首页整片置灰
        //  （用户报的「换 ROM 后整页一直是灰的，退出去再进来才好」正是这一处）。
        //  与宿主那一档对齐：全程没答才降档，见下面两行。
        if (hostAnswered && injectAnswered) {
            // 两端都答了 ⇒ 这一趟自检结束（提示可以按真结论说话了，见 `injectChecked`）。
            //  问通了就清掉退避：这一趟的结论可以一直用到宿主换进程。
            link.markCheckPassed(link.hostProcStart)
            return
        }
        if (round > 0) XLog.w("链路自检第 ${round + 1} 轮仍未齐（宿主=$hostAnswered 注入=$injectAnswered）")
    }
    //  注意：**只有问满所有轮都没回音才降档**：每轮超时就清，一轮偶然没回音
    //  （广播被压 / 宿主正忙）就会把一个**已经确认过**的结论清成「未加载」。
    //  现在两端同一套判据：单轮漏答不算数，全程没答才判它没起来 / 没加载。
    //  问满所有轮也结束：此时「没回音」才是结论 —— `blockedReason` 正是靠这一位
    //  把「还在问」与「问了没人答」分开的。
    //  **问空了不算「问过」到底**：注入侧自己恢复（借到 Context、uid 晚到）并不换宿主进程，
    //  若就此不再问，首页会一直灰着，只能杀 `:ui` 进程或重启。所以记一个退避时刻 ——
    //  过 [LINK_SILENT_RETRY_MS] 之后回首页会再问一趟（判据见 `HostLinkState.needsLinkCheck`）。
    link.markCheckSilent(
        hostStart = link.hostProcStart,
        retryAt = SystemClock.elapsedRealtime() + LINK_SILENT_RETRY_MS,
    )
    if (!hostEverAnswered) link.hostServiceUp = false
    if (!injectEverAnswered) link.injectStatus = null
    XLog.w(
        "链路自检问了 $QUERY_ROUNDS 轮都没问齐，" +
            "改在 ${LINK_SILENT_RETRY_MS / 1000} 秒后回首页时再问（详见 [更多选项 → 运行状态]）",
    )
}

/**
 * 用系统浏览器打开 [url]（「查看地址」那一行用）。
 *
 *  **优先用 Activity 起（同任务）**：这一页的 [context] 不一定是 Activity ——
 * 浅色主题下 `pageContext` 给的是 `ContextThemeWrapper`（见它的链式注释），
 * 所以先沿 `baseContext` 剥到 Activity 再 `startActivity`：浏览器开在**同一个任务**里，
 * 从浏览器返回就回到这一页。
 *
 *  剥不到 Activity 才退回带 `FLAG_ACTIVITY_NEW_TASK` 的那条路（否则直接抛
 * `Calling startActivity() from outside of an Activity context`）—— 那条路浏览器会开在
 *
 *  整段包 try：本机没有浏览器 / 被策略拦掉都只是「点了没反应」，不能让设置页崩
 *（与这一层别处的纪律一致：页面上的动作失败绝不外抛）。
 */
internal fun SettingsPage.openUrl(url: String) {
    val intent =
        android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url),
        )
    val host = unwrapActivity(context)
    try {
        if (host != null) {
            host.startActivity(intent)
        } else {
            context.startActivity(
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        XLog.w("已请求打开地址：$url（${if (host != null) "同任务" else "新任务"}）")
    } catch (e: Throwable) {
        XLog.w("打开地址失败（本机可能没有浏览器）：$url", e)
    }
}

/**
 * 沿 `baseContext` 往上剥到 Activity —— `pageContext` 在浅色主题下给的是
 * `ContextThemeWrapper`，直接拿它 `startActivity` 必须带 `NEW_TASK`（会开成新任务）。
 *
 * 带圈数上限 + 自引用判断：`ContextWrapper.baseContext` 原则上可能指回自己，
 * 无脑递归会栈溢出。
 */
internal fun SettingsPage.unwrapActivity(from: android.content.Context?): android.app.Activity? {
    var cur = from
    var guard = 0
    while (cur != null && guard++ < 10) {
        if (cur is android.app.Activity) return cur
        val base = (cur as? android.content.ContextWrapper)?.baseContext ?: return null
        if (base === cur) return null
        cur = base
    }
    return null
}

/**
 * 首页那几样**一口气开一个**的东西 —— 两次确认 + 捐赠那个菜单与收款码窗口。
 *
 *  收成一个对象而不是几个 `remember { }`：它们分散在「卡片之间」与「页面末尾」两处，
 *  摊开时「这个位是谁置的」得来回看；而且菜单与窗口要共用「选了哪个去处」这一份结论
 *  （`donateTarget`），分开写很容易写成两份。
 *
 */
internal class HomeUiState {
    /**
     * 「启用按键绑定」要的那次确认：true = 弹窗开着，**此时还没写盘**（点了确认才写）。
     *  只在「打开」这一侧要；关闭直接写。
     *
     *  它只服务首页那个主开关（**模块总闸**）—— ⋮ 菜单里「停用这颗键」是逐键开关，
     *  不进这道闸（拆开之后两者是两件事）。
     */
    var disclaimerPending by mutableStateOf(false)

    /**
     * 免责声明**确认之后**接着弹的那一个「温馨提醒」（模块免费、认准官方正版）。
     *
     *  与上面那次确认分开两个开关：这一条**不写盘、不拦任何操作**，两个按钮都只是关掉它；
     *  合成一个开关会让「关掉它」和「没确认免责声明」变成同一件事，总闸就跟着被卡住了。
     */
    var freeNoticePending by mutableStateOf(false)

    /** 「捐赠打赏」那一行的锚点 —— 菜单的落点由它给。 */
    val donateClick = PreciseClickState()

    var showDonateMenu by mutableStateOf(false)

    /** 选中的捐赠去处（非 `null` = 收款码窗口开着）。 */
    var donateTarget by mutableStateOf<DonateTarget?>(null)
}

// 首页那两张卡片 —— 从 `HomeScreen.kt` 拆出来。
//
// `HomeScreen` 原来是一个 434 行的函数：状态、链路自检、提示条、三张卡、两个确认窗、
// 一个菜单全平铺在一起。按**一张卡**切开之后，页面只剩「铺顺序 + 那两条 effect」，
// 卡里的人只管自己那几行。

/**
 * 「其他」那张卡 —— 自定义命令 / 自定义变量 / 更多选项 / 捐赠打赏。
 *
 *  「公共设置」（原「模块设置」）**不在这一卡里**：它跟「按键怎么工作」有关，已挪进上面的
 *  「按键操作」卡（见 `HomeKeyOpsCard`）。
 *
 *  **闸门不是一刀切**（用户口径「更多行不跟随总闸禁用」，而这几行改配置的跟随）：
 *   · 「自定义命令」「自定义变量」是这一页里**会改配置**的那两行（存命令 / 建改变量），它们吃整页
 *     总闸（[pageEnabled]）—— 与「按键」卡里那几颗键同一档：别的都灰着、这两行还能点进去改，就是个漏洞；
 *   · 「更多选项」是进那一页的**唯一入口**，而那一页里有「运行状态」—— 链路没齐 / 配置只读 /
 *     主开关关着时，用户正需要进去看断在哪一段（机型清单不参与任何禁用，见 AGENTS.md 一、13）；
 *   · 打赏与模块启没启用无关，整页禁用时它照样该能点。
 */
@Composable
internal fun SettingsPage.HomeOtherCard(
    pageEnabled: Boolean,
    ui: HomeUiState,
) {
    CardList(
        rows =
            listOf(
                // 「自定义命令」：这一卡的第一行。它进的是命令库那一页 ——
                //  名称格、单条 / 多条两栏、「已保存的命令」都在那边。
                //  标题与那一页顶栏**同名**（与「按键设置」「更多选项」同一条口径：点的那一行
                //  与进去之后看到的标题是同一句话）。
                //  这一行会写配置（存命令），所以吃整页总闸。
                CardRow.Item(
                    title = I18n.get(Strings.ACTION_CUSTOM_COMMAND),
                    icon = PageIcons.CommandCode,
                    enabled = pageEnabled,
                    arrow = true,
                    onClick = { openCommandsPage() },
                ),
                // 「自定义变量」：与「自定义命令」并排 —— 两者是同一类东西（用户自己定义、之后
                //  在命令里用），摆在一起比塞进「更多选项」更好找。
                //  这一页只有变量的清单与新建，不吃整页总闸以外的东西：建 / 改变量要写盘，
                //  所以与上面两行同一档（`pageEnabled`）。
                //  **入口临时收起来**（[ActionCatalog.SHOW_VARIABLES]，用户口径「UI 入口先藏、功能不动」）：
                //  走行的 `visible`，连它下面那条细线一起藏，不留孤零零一条线。
                CardRow.Item(
                    title = I18n.get(Strings.TOOL_VARIABLES),
                    summary = I18n.get(Strings.HOME_VARIABLES_DESC),
                    icon = TablerIcons.Outline.Variable,
                    enabled = pageEnabled,
                    arrow = true,
                    visible = ActionCatalog.SHOW_VARIABLES,
                    //  这一趟是**管数据**：点列表里的一颗会弹「编辑 / 删除 + 当场改值」那张窗
                    onClick = { openVariablesForManage() },
                ),
                CardRow.Item(
                    title = I18n.get(Strings.MORE_OPTIONS_TITLE),
                    // 副标题只报这一页有什么，不交代机制（「一起改短」）
                    summary = I18n.get(Strings.HOME_MORE_OPTIONS_SUMMARY),
                    icon = PageIcons.MoreOptions,
                    //  行尾**不挂红点**：有新版这件事由首页顶上那块「模块状态」说（见 `HomeActivationCard`），
                    //  同一条信息在一页里出现两处，读起来像两件事
                    arrow = true,
                    onClick = { navTo(Screen.ABOUT) },
                ),
                // 「捐赠打赏」：点开一层菜单（三个去处），选中一个再弹**居中**的收款码窗口；
                //  菜单最后那一项（「捐赠列表」）才是名单页本身，表格字形在那一项上
                //  （[PageIcons.DonateList]），这一行仍是原来那颗心（[PageIcons.Donate]）。
                //  与「更多选项」并排在这一张卡里 —— 两行都是「偶尔才点一次」的去处。
                //  行尾给选择器字形（读作「点了要选点什么」），不是进下一页的箭头，与「语言」那一行同一套。
                CardRow.Item(
                    title = I18n.get(Strings.HOME_DONATE),
                    summary = I18n.get(Strings.HOME_DONATE_SUMMARY),
                    icon = PageIcons.Donate,
                    // 菜单的落点由这一行记下的触点给（`MenuPopup` 的 `preciseClick`）。
                    //  少了它，锚点永远是空的 ⇒ 菜单退回库的默认位置，读起来就是「没有跟随手指」。
                    modifier = Modifier.preciseClickAnchor(ui.donateClick),
                    trailing = { RowEndIcon(PageIcons.Selector) },
                    onClick = {
                        ui.showDonateMenu = true
                    },
                ),
            ),
    )
}

/**
 * 进「自定义命令」页 —— 这是**新建**的入口，与「按键绑定」页那颗 FAB 同一条规矩：
 * 先把草稿清干净再进（`goBack` 只在草稿脏时才 reset，「点开一条看一眼、原样返回」那种
 * 不脏的退页不会清 `editingName`，留着的话下次进来表单还挂着「修改」）。编辑已有命令那条路
 * 走的是「已保存的命令」卡片，不经过这里。
 *
 *  清的是**当前这一页**（`draft.kind`）：两页是两个独立会话，另一页的草稿不动 ——
 *  整页归零只在出栈时发生（`CommandDraftState.discard`）。
 */
private fun SettingsPage.openCommandsPage() {
    commandDraft.draft.reset()
    //  首页这个入口落**「命令列表」**那一页（用户口径）：从首页来多半是「看看都有哪些命令 /
    //  改一条已经存在的」，而不是「现在就要新建一条」—— 新建在那一页右下角那颗 FAB 上，
    //  点一下就进表单。与「按键绑定」页那条「已保存的命令」入口同一个落点（见 `SavedCommandsTitle`）。
    commandDraft.openListPage = true
    navTo(Screen.COMMANDS)
}
