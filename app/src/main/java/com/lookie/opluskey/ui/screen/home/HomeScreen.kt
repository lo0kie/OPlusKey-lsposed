package com.lookie.opluskey.ui.screen.home

import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.HOST_ALIVE_TIMEOUT_MS
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.QUERY_ROUNDS
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.LINK_SILENT_RETRY_MS
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
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
    //  注意：**主开关也吃这个闸**（`linkReady` + 另两条，见 [HomeBindingCard]）。顶栏 ⋮ **不吃** ——
    // 它本身永远可点，里面只有「重启服务 / 导出日志 / 清空配置」三项跟着灰，
    // 「默认界面」（逃生口）始终可用。
    //  适配清单**不再参与**这道闸：清单外的机器照样能改（见 `core/model/DeviceSupport`），
    //  验没验过只在「更多选项」里提示。
    val pageEnabled = pageWritable()

    Column(
        Modifier
            .fillMaxSize()
            // 每个页面自带不透明底：push/pop 转场两屏交叠时，透出的是本页自己的底色。
            .background(COUITheme.colorScheme.surface)
            //  `nestedScroll` 必须排在 `verticalScroll` **之前**（链上更靠外）：
            // 它是「把里层滚动容器的余量交给顶栏」的那一环，挂在滚动容器内侧就收不到它自己的滚动事件了。
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            // 越界回弹：COUI 自己的弹簧实现（`overScrollVertical`）。它挂在滚动容器**外侧**，
            // 吃的是滚动容器**没吃掉**的那部分位移 —— 所以内容不满一屏时照样弹。
            .overScrollVertical()
            .verticalScroll(rememberScrollState()),
    ) {
        //  顶栏是叠在内容上的（见 `PageHeader`），所以顶部这一块要自己让出来 ——
        // 数值是**动态**的（跟着折叠走），理由见 `currentHeaderHeight`。
        //  注意：**不再另加 `PAGE_TOP_PADDING`**：这一页没有固定带，顶栏底下没有看得见的边界，
        // 而顶栏自己底部就留了 12dp（库的 `LargeTitleBottomPadding`）—— 再加那 8dp，
        // 首行距标题的空白明显比带子页大一截（真机反馈「首页的上边距比其他页多」）。
        //  带子页那一档来自带子自己的 8dp 下内边距，两边视觉上正好齐平。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        // 这一节只有模块总闸一行，小标题就按它叫（原先叫「按键绑定」，与那一页的页名混在一起）。
        //  **不要借 `Strings.BINDING_TITLE`**：那是「按键绑定」子页的标题，改这里会把它一起改名。
        SmallTitle(I18n.get(Strings.HOME_MASTER_SECTION, "主开关"))
        // 「响铃状态」走的是公共 `AudioManager.setRingerMode`，ColorOS 的 ringer delegate 会把外来的
        // 静音请求改写成振动，于是要靠「勿扰访问」权限绕。现在执行侧改走 `setRingerModeInternal`
        // （宿主本来就持有 `STATUS_BAR_SERVICE`），**与勿扰再无关系** —— 那一行连同它引入的
        // 动态行数（`homeBindingRows`）一并删掉，别再按「静音要授权」加回来。
        HomeBindingCard(ui)

        // 首次启用（以及清空配置之后再启用）要的那两次确认 —— 摆在那张卡后面，不跟别的开关混在一起。
        HomeDisclaimers(ui)

        // 「按键列表」那一节 —— **只有按键清单**（用户口径）：原先它在
        // 「按键绑定」页顶栏那份 ⋮ 菜单里，而那一页只回答「这颗键现在绑了什么」；
        // 有哪几颗键是总览，属于首页。一颗键一行，点哪颗进哪颗。
        Spacer(Modifier.height(8.dp))
        SmallTitle(I18n.get(Strings.HOME_KEYS_SECTION, "按键列表"))
        HomeKeysCard(pageEnabled)

        // 「按键操作」那一节 —— 组合按键与「添加按键」两行（用户口径：与清单分开一张卡）。
        //  这两行不是清单里的东西，是**对这颗键 / 这组键做的事**。
        Spacer(Modifier.height(8.dp))
        SmallTitle(I18n.get(Strings.HOME_KEY_OPS_SECTION, "按键操作"))
        HomeKeyOpsCard(pageEnabled, ui)

        // 「按键行为」那一整块搬进了「通用设置」页（入口在下面「其他」卡的第一行）——
        //  它是**全局**开关，与「这颗键绑了什么」无关，摆在这里会把常用的入口挤下去。

        // 版本 / 仓库地址那两行搬去**独立的「更多选项」页**（`AboutScreen`，从顶栏 ⋮ 菜单进）——
        // 它们是「想知道时才看一眼」的信息，摆在首页会把常用的那几张卡挤下去。
        Spacer(Modifier.height(8.dp))
        SmallTitle(I18n.get(Strings.HOME_OTHER_SECTION, "其他"))
        HomeOtherCard(pageEnabled, ui)

        HomeDonateOverlays(ui)

        Spacer(Modifier.height(PAGE_BOTTOM_PADDING))
    }
}

/**
 * 链路自检 —— 进页问一次（最多 [QUERY_ROUNDS] 轮）。
 *
 *  「链路上每一段在不在」⇒ 缺哪一段就报哪一段（见 `ui/LinkCheck.kt` 的 `linkChecks`）。
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
                queryLinkOnce()
                link.checkedHostStart = link.hostProcStart
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
            link.injectChecked = true
            link.silentRetryAt = 0L
            return
        }
        if (round > 0) XLog.w("链路自检第 ${round + 1} 轮仍未齐（宿主=$hostAnswered 注入=$injectAnswered）")
    }
    //  注意：**只有问满所有轮都没回音才降档**：每轮超时就清，一轮偶然没回音
    //  （广播被压 / 宿主正忙）就会把一个**已经确认过**的结论清成「未加载」。
    //  现在两端同一套判据：单轮漏答不算数，全程没答才判它没起来 / 没加载。
    //  问满所有轮也结束：此时「没回音」才是结论 —— `blockedReason` 正是靠这一位
    //  把「还在问」与「问了没人答」分开的。
    link.injectChecked = true
    if (!hostEverAnswered) link.hostServiceUp = false
    if (!injectEverAnswered) link.injectStatus = null
    //  **问空了不算「问过」到底**：注入侧自己恢复（借到 Context、uid 晚到）并不换宿主进程，
    //  若就此不再问，首页会一直灰着，只能杀 `:ui` 进程或重启。所以记一个退避时刻 ——
    //  过 [LINK_SILENT_RETRY_MS] 之后回首页会再问一趟（判据见 `HostLinkState.needsLinkCheck`）。
    link.silentRetryAt = SystemClock.elapsedRealtime() + LINK_SILENT_RETRY_MS
    XLog.w(
        "链路自检问了 $QUERY_ROUNDS 轮都没问齐，" +
            "改在 ${LINK_SILENT_RETRY_MS / 1000} 秒后回首页时再问（详见「更多选项 → 运行状态」）",
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
