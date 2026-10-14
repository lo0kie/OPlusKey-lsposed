package com.lookie.opluskey.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.BUILD_KIND
import com.lookie.opluskey.KeyModule
import com.lookie.opluskey.SHOW_BUILD_TIME
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.SwitchRow
import com.lookie.opluskey.ui.component.currentHeaderHeight
import com.lookie.opluskey.ui.model.CONFIG_READ_FAILED_HINT
import com.lookie.opluskey.ui.model.HOME_ACTION_ROWS
import com.lookie.opluskey.ui.model.HOME_BINDING_ARROW_SIZE
import com.lookie.opluskey.ui.model.HOME_BINDING_ROWS
import com.lookie.opluskey.ui.model.HOME_MORE_ROWS
import com.lookie.opluskey.ui.model.HOST_ALIVE_TIMEOUT_MS
import com.lookie.opluskey.ui.model.MODULE_UNAVAILABLE_HINT
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.QUERY_ROUNDS
import com.lookie.opluskey.ui.model.UNSUPPORTED_DEVICE_HINT
import com.lookie.opluskey.ui.state.deviceSupported
import com.lookie.opluskey.ui.state.linkReady
import com.lookie.opluskey.ui.state.rememberTick
import com.lookie.opluskey.ui.state.setKeyEnabled
import com.lookie.opluskey.ui.state.setToastOnRun
import com.lookie.opluskey.ui.state.setVibrate
import com.lookie.opluskey.ui.state.tickNow
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.SnackbarDuration
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.delay

/**
 * 首页（快捷键）。
 *
 * 从 `SettingsPage.kt` 按职责拆出（2026-10-12 重构）：成员改为 [SettingsPage]
 * 的扩展/同包顶层声明，调用点零改动。
 */
@Composable
internal fun SettingsPage.HomeScreen(scrollBehavior: ScrollBehavior) {
    val cfg = config.value
    val key = cfg.key(KeyId.PLUS)
    // 触觉反馈入口提到页面级 —— 「按键绑定」那行的主开关也要用（它排在「按键行为」卡之前）
    val tick = rememberTick()

    // 链路上每一段在不在 ⇒ 缺哪一段就报哪一段（见 `ui/state/LinkCheck.kt` 的 `linkChecks`）。
    //  判据只能是**模块自己报活**：查宿主那支侧键服务不管用 —— 它是宿主自己的常驻服务，
    // 系统会自己把它拉起，服务在跑 ≠ 模块在跑（真机实测：重启后没注入它照样在跑）。
    //  每轮对**两端各记一次代次**、各发一句「在不在」（报活广播是一次性的，我们可能已经错过了），
    // 等一档再看代次有没有变：没变就是没加载。
    //  ⚠️ **一轮不够，要重问**（2026-10-14 用户报的「换 ROM 后整页一直是灰的，退出去再进来才好」）：
    //  注入侧回不了包有两种常见成因 —— 它借不到 Context、或它的宿主 uid 还没解析出来
    //  （那时 fail-closed 会把问句丢掉，见 `KeyHooks.isFromHost`）。两种都会自愈，只是**不是立刻**；
    //  而这条 effect 原来只发一次，页面就一直停在「未就绪」上等一个再也不会来的回包。
    //  这里改成最多问 [QUERY_ROUNDS] 轮，**两端都答上就提前收工** —— 已经就绪时与原来一样只问一次；
    //  一直问不到也不会转下去：机型允许表外的机器本来就该一直禁用，无限轮询只是白耗电。
    LaunchedEffect(Unit) {
        repeat(QUERY_ROUNDS) { round ->
            val hostToken = hostAliveToken
            val injectToken = injectAliveToken
            context.sendBroadcast(
                Intent(KeyModule.ACTION_HOST_SERVICE_QUERY).setPackage(KeyModule.PAGE_PACKAGE),
            )
            // 注入侧（system_server）那一段：它挂在 AMS 的广播入口上，认得出这条问句
            context.sendBroadcast(
                Intent(KeyModule.ACTION_INJECT_QUERY).setPackage(KeyModule.PAGE_PACKAGE),
            )
            delay(HOST_ALIVE_TIMEOUT_MS)
            val hostAnswered = hostAliveToken != hostToken
            val injectAnswered = injectAliveToken != injectToken
            if (!hostAnswered) hostServiceUp = false
            // 没回音就把上一轮的回包清掉，免得拿旧结论当新结论
            if (!injectAnswered) injectStatus = null
            if (hostAnswered && injectAnswered) return@LaunchedEffect
            if (round > 0) XLog.w("链路自检第 ${round + 1} 轮仍未齐（宿主=$hostAnswered 注入=$injectAnswered）")
        }
        XLog.w("链路自检问了 $QUERY_ROUNDS 轮都没问齐，不再重问（详见「关于模块 → 运行状态」）")
    }
    // 这一页所有可交互项的**总闸**：机型允许 + 主开关开着 + 链路齐了（见 [linkReady]，它现在等同于
    // 「运行状态里除『主开关已关闭』与『使用验证』外每一段都 ok」）+ 配置可写
    // （建页那次没读成功 ⇒ 只读，写回一律被拒，见 `SettingsPage.configReadOnly`）。
    // 有哪一条不满足时改配置不会生效，放用户去改只会误导（底部那条常驻提示会说明该怎么办）。
    //  ⚠️ 2026-10-14 起**主开关也吃这个闸**（`linkReady` + 另两条）。顶栏 ⋮ **不吃** ——
    // 它本身永远可点，里面只有「重启服务 / 导出日志 / 清空配置」三项跟着灰，
    // 「默认界面」（逃生口）始终可用（见 `ResetMenu`；「更多选项」那一项已从菜单删掉，只留首页这一行）。
    //  机型不在允许表里时：允许表外只注入了界面层（见 `core/model/DeviceSupport`），
    // 链路永远不通 ⇒ 本来就整页禁用；`deviceSupported` 只是让**原因**说得清。
    val pageEnabled = key.enabled && linkReady && !configReadOnly && deviceSupported

    // 判据就是**同一个总闸**（[linkReady]），不再另算一遍「哪一段 not-ok」：这样「运行状态里报异常」
    //  与「首页弹提示」永远是同一件事 —— 「还没被验证过」是信息、不是故障，「主开关自己关着」属用户
    //  意图、也不是故障，两档都不该骂人。
    //  落点由 [hostServiceSnackbar] 那一层的 `SnackbarHost` 给（屏幕底边），这里只管弹 / 收。
    //  `newestSnackbarData() == null` 那半句是**去重**：首页被切走再回来会重新组合，
    // 不去重就会叠出第二条（真机反馈「弹了两个 Snackbar」）。
    //
    //  ⚠️ **报的是整体结论，不是缺的那一段的名字**（2026-10-14 用户定的）：具体缺哪一段
    // 是给「更多选项 → 运行状态」看的，首页这条只回答「现在能不能用」。
    //  三种原因各说各的，**顺序就是优先级**：
    //   · 机型不支持 ⇒ 这台机器上就是不能用（[UNSUPPORTED_DEVICE_HINT]，等也没用）；
    //   · 配置没读成功 ⇒ 用户重进页面就能解决（[CONFIG_READ_FAILED_HINT]）；
    //   · 链路没齐 ⇒ 等系统响应（[MODULE_UNAVAILABLE_HINT]）。
    val blockHint = when {
        !deviceSupported -> UNSUPPORTED_DEVICE_HINT
        configReadOnly -> CONFIG_READ_FAILED_HINT
        !linkReady -> MODULE_UNAVAILABLE_HINT
        else -> null
    }
    LaunchedEffect(blockHint) {
        if (blockHint == null) {
            hostServiceSnackbar.newestSnackbarData()?.dismiss()
        } else if (hostServiceSnackbar.newestSnackbarData() == null) {
            XLog.w("首页提示：$blockHint")
            hostServiceSnackbar.showSnackbar(
                message = blockHint,
                duration = SnackbarDuration.Indefinite,
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // 每个页面自带不透明底：push/pop 转场两屏交叠时，透出的是本页自己的底色。
            .background(COUITheme.colorScheme.surface)
            //  `nestedScroll` 必须排在 `verticalScroll` **之前**（链上更靠外）：
            // 它是「把里层滚动容器的余量交给顶栏」的那一环，挂在滚动容器内侧
            // 就收不到它自己的滚动事件了。
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            // 越界回弹：COUI 自己的弹簧实现（`overScrollVertical`）。它挂在滚动容器**外侧**，
            // 吃的是滚动容器**没吃掉**的那部分位移 —— 所以内容不满一屏时照样弹。
            .overScrollVertical()
            .verticalScroll(rememberScrollState()),
    ) {
        //  顶栏是叠在内容上的（见 [PageHeader]），所以顶部这一块要自己让出来 ——
        // 数值是**动态**的（跟着折叠走），理由见 [currentHeaderHeight]。
        //  ⚠️ **不再另加 [PAGE_TOP_PADDING]**（2026-10-13）：这一页没有固定带，顶栏底下
        // 没有看得见的边界，而顶栏自己底部就留了 12dp（库的 `LargeTitleBottomPadding`）
        // —— 再加那 8dp，首行距标题的空白明显比带子页大一截（真机反馈「首页的上边距比其他页多」）。
        //  带子页那一档来自带子自己的 8dp 下内边距，两边视觉上正好齐平。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        SmallTitle("按键绑定")

        // 「响铃状态」走的是公共 `AudioManager.setRingerMode`，ColorOS 的 ringer
        // delegate 会把外来的静音请求改写成振动，于是要靠「勿扰访问」权限绕。
        // 现在执行侧改走 `setRingerModeInternal`（宿主本来就持有
        // `STATUS_BAR_SERVICE`），**与勿扰再无关系** —— 那一行连同它引入的
        // 动态行数（`homeBindingRows`）一并删掉，别再按「静音要授权」加回来。

        Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            // 主开关是这张卡的第一行，**不能被自己锁死**：它一旦关掉，全页（含这一页其它控件、
            // 「按键绑定」子页、顶栏菜单三项）都禁用 —— 它要是跟着一起禁用，就再也开不回来了。
            //  所以它的 `enabled` 吃的是 [linkReady]（宿主侧在跑 **且** 注入侧报到了）**外加**
            // `!configReadOnly` 与 `deviceSupported`，**不吃 `pageEnabled`**（后者含它自己）。
            // 这几个条件里都没有主开关的任何成分，所以「关掉它 ⇒ 它自己被禁用」这条死路不存在。
            //
            //  ⚠️ `!configReadOnly` 这一条是被真机逼出来的（2026-10-14）：只读态下刚进页时
            // 链路还没报到（`linkReady=false`）⇒ 主开关是灰的；等注入侧回包一到、`linkReady` 变 true，
            // 它就**自己重新亮起来**了，用户当场拨了三下（每次都被 `write` 拒掉）。
            // **「拨了没反应」比「一开始就灰着」更难读**，所以这一档必须一起禁。
            //  `deviceSupported` 同理：允许表外机型只注入了界面层，拨它也不会生效。
            //
            // 模块没就绪时它照样禁用 —— 那种情况下拨这个开关也不会生效，页面底部那条
            // 常驻提示（[MODULE_UNAVAILABLE_HINT] / [CONFIG_READ_FAILED_HINT] / [UNSUPPORTED_DEVICE_HINT]）
            // 会说明该做什么。
            //
            // 它写的是 [KeyConfig.enabled] —— 执行侧早就存在的总闸（注入侧
            // `inject/SideKeyInterceptor` 与宿主侧 `host/GestureReceiver` 各有一道
            // `if (!…enabled) return`），所以这一开关不只是界面置灰，是真的把功能关掉。
            //
            // 开关切换交给整行点击 —— 与「振动反馈」行同一条规矩
            // （行与开关各切一次会「闪一下又变回去」）。库 `Switch` 自己那次无条件振动由
            // [SwitchRow] 统一掐掉，只留 `tick()` 这条通用通道。
            SwitchRow(
                title = "启用按键绑定",
                // 开着时说明「关掉会怎样」；关着时直接报状态 —— 光看一个灰开关不知道后果
                summary = if (key.enabled) {
                    // （14 字）正好一行放得下，再多一个字就折行、把这张卡撑高
                    "关闭后此模块将停用"
                } else {
                    "侧键由系统处理"
                },
                // 行首图标与下一行对齐用（不给的话两行的标题左边缘会差一个图标宽）。
                // 电源符号 = 「这一行是总开关」。
                icon = PageIcons.Power,
                checked = key.enabled,
                position = cardListPositionOf(0, HOME_BINDING_ROWS),
                enabled = linkReady && !configReadOnly && deviceSupported,
            ) {
                tick()
                setKeyEnabled(it)
            }

            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))

            // 第 2 行：进「按键绑定」子页（纯跳转，行尾只有箭头）。
            // 进不去那个页面，也就不存在「关着还能改配置」。
            //
            //  行尾箭头**自己画**：原先用库的 `ArrowPreference`，它那颗是 COUI 图标集的
            // 12x24 窄箭头（描边 1.4），与页面其余 Tabler 图标（描边 2）不是一支笔。
            // 自己画就得自己管两档色 —— 照抄库那支选择器
            //（`onSurfaceVariantActions` / `disabledOnSecondaryVariant`）。
            //  尺寸见 [HOME_BINDING_ARROW_SIZE]：24dp 下 Tabler 那一段折线正好与库原箭头一样高。
            BasicComponent(
                title = "按键绑定",
                summary = "配置单击、双击、长按对应的动作",
                startAction = {
                    //  行首图标还是得自己管：库只给**它自己画的东西**按 `enabled` 上色，
                    // `startAction` 槽位原样渲染。不给的话 `enabled = false` 会「文字灰了、
                    // 图标还亮着」。
                    if (pageEnabled) {
                        Icon(PageIcons.TouchApp, null)
                    } else {
                        Icon(
                            PageIcons.TouchApp,
                            null,
                            tint = COUITheme.colorScheme.disabledOnSecondaryVariant,
                        )
                    }
                },
                endActions = {
                    Icon(
                        PageIcons.EnterArrow,
                        contentDescription = null,
                        modifier = Modifier.size(HOME_BINDING_ARROW_SIZE),
                        tint = if (pageEnabled) {
                            COUITheme.colorScheme.onSurfaceVariantActions
                        } else {
                            COUITheme.colorScheme.disabledOnSecondaryVariant
                        },
                    )
                },
                cardListPosition = cardListPositionOf(1, HOME_BINDING_ROWS),
                enabled = pageEnabled,
                onClick = {
                    navTo(Screen.BINDING)
                },
            )

            //  这张卡**只有两行**：第 1 行主开关、第 2 行进「按键绑定」子页。
            // `setRingerModeInternal` 一起删掉，理由见这张卡上方那段注释。
        }

        // 「按键绑定」卡片与「按键行为」分组之间留一档空档
        Spacer(Modifier.height(8.dp))

        SmallTitle("按键行为")

        // （见 [LongRepeatCard]）—— 它们只管长按那一个手势，摆在这一页会让人以为对三个手势都生效。
        // 搬去长按 tab 是同一个理由：它们只管自己那一个手势，
        // 摆在这一页与「振动反馈」并列，会被当成对三个手势都生效的全局项。
        Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            SwitchRow(
                title = "振动反馈",
                summary = "命令触发时振动",
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
            //  它原来是**每条命令自己**的开关（只在「自定义命令」页多条命令的表单里出现），
            // 2026-10-13 搬到这一页做成**全局**项 —— 它回答的是「按下去到底有没有被触发」，
            // 与「这条命令是什么」无关，每条命令各配一次是白费力气。
            //  判定侧读它见 `host/action/CustomCommandRunner`（宿主进程里执行时**现读**配置，
            // 所以拨完开关立刻生效，不必重启）。
            SwitchRow(
                title = "执行时提示",
                summary = "执行命令时系统提示",
                icon = PageIcons.CommandToast,
                checked = cfg.global.toastOnRun,
                position = cardListPositionOf(1, HOME_ACTION_ROWS),
                enabled = pageEnabled,
            ) {
                tick()
                setToastOnRun(it)
            }
        }

        // 版本 / 仓库地址那两行（原来这张「其他」卡）2026-10-13 搬去**独立的「更多选项」页**
        //（[AboutScreen]，从顶栏 ⋮ 菜单进）—— 它们是「想知道时才看一眼」的信息，
        // 摆在首页会把常用的那几张卡挤下去。

        // 「其他」分组（2026-10-14 用户要求）：进「更多选项」页（[AboutScreen]：
        // 运行状态、配置导入导出、版本与地址）。
        //
        // ⚠️ 这一行**不吃任何闸**（同日用户要求「首页的更多行不跟随总闸禁用」）：
        // 它是进「更多选项」页的**唯一入口**（⋮ 里那一项已删），而那一页里有「运行状态」——
        // 模块整页禁用（机型不支持 / 链路没齐 / 配置只读 / 主开关关着）时，用户正需要进去看断在哪一段。
        //  行尾箭头与行首图标因此都恒用常规色，不再按 `pageEnabled` 换灰。
        Spacer(Modifier.height(8.dp))
        SmallTitle("其他")
        Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            BasicComponent(
                title = "更多选项",
                // 副标题只报这一页有什么，不交代机制（2026-10-14 用户要求「一起改短」）
                summary = "运行状态与配置备份",
                startAction = { Icon(PageIcons.MoreOptions, null) },
                endActions = {
                    Icon(
                        PageIcons.EnterArrow,
                        contentDescription = null,
                        modifier = Modifier.size(HOME_BINDING_ARROW_SIZE),
                        tint = COUITheme.colorScheme.onSurfaceVariantActions,
                    )
                },
                cardListPosition = cardListPositionOf(0, HOME_MORE_ROWS),
                onClick = {
                    navTo(Screen.ABOUT)
                },
            )
        }

        Spacer(Modifier.height(PAGE_BOTTOM_PADDING))
    }
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
    val intent = android.content.Intent(
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
 * 模块自己的版本号（读不到返回 `null`，不抛异常）。
 *
 *  包名必须显式用 [KeyModule.MODULE_PACKAGE]（模块自己），**不能用 `context.packageName`**
 * —— 这一页跑在宿主进程里，那个 `context` 是宿主的 Context，读出来是宿主 App 的版本号。
 *
 *  `BuildConfig` 在本工程是**关掉**的（见 `build.gradle.kts` 的 `buildFeatures`），所以只能
 * 从 `PackageManager` 读。
 */
private fun SettingsPage.moduleVersionName(): String? = try {
    context.packageManager
        .getPackageInfo(KeyModule.MODULE_PACKAGE, 0)
        .versionName
} catch (e: Throwable) {
    XLog.w("读模块版本号失败：${e.javaClass.simpleName}")
    null
}

/**
 * 出包时注入的构建时刻（`HHmmss`，gradle 的 `resValue`，见 `build.gradle.kts` 的 `build_id`）。
 *
 *  不能写 `R.string.build_id`：页面跑在**宿主**进程里，`R` 会解析到宿主自己的资源表，取出来
 * 是别人的字符串（或 0）。必须按**模块包名**去拿我们那份资源。拿不到（没走 gradle 出的包 /
 * 宿主限制）就退回 [KeyModule.BUILD_ID] 的末段。
 */
private fun SettingsPage.moduleBuildStamp(): String = try {
    val res = context.packageManager
        .getResourcesForApplication(KeyModule.MODULE_PACKAGE)
    val id = res.getIdentifier("build_id", "string", KeyModule.MODULE_PACKAGE)
    res.getString(id).takeIf { it.isNotBlank() }
} catch (e: Throwable) {
    XLog.w("读构建标识失败（退回常量）：${e.javaClass.simpleName}")
    null
} ?: KeyModule.BUILD_ID.substringAfterLast('-')

/**
 * 上报用的完整版本标识：**模块自己**的版本号 + [BUILD_KIND] + 构建时刻（`1.0.0-debug-183000`）。
 *
 *  只给导出的日志头用（见 `SettingsPageHooks.exportLog`）—— 排查时要据此认出是哪个包、
 * 什么时候出的。界面显示走 [appVersionLabel]（它按构建类型决定要不要带末段）。
 */
internal fun SettingsPage.appVersionText(): String {
    val name = moduleVersionName()
    val build = moduleBuildStamp()
    // 三段用 `-` 连（2026-10-13 要求「· 改成 -」）。
    return if (name.isNullOrBlank()) {
        "$BUILD_KIND-$build"
    } else {
        "$name-$BUILD_KIND-$build"
    }
}

/**
 * 界面上显示的版本（「更多选项」页的「版本」行与首页大标题副标题都用它）。
 *
 *  **debug 带构建时刻、release 不带**（[SHOW_BUILD_TIME]）：自用包一天里会出好几个，光看
 * `1.0.0-debug` 分不出手上这个是哪一次出的；release 面向用户，`HHmmss` 只是技术细节
 *（2026-10-14 用户定的「用户界面不需要技术细节 / 不报时间戳」）。读不到版本号时只显示
 * [BUILD_KIND]，不抛异常。
 */
internal fun SettingsPage.appVersionLabel(): String {
    if (SHOW_BUILD_TIME) return appVersionText()
    val name = moduleVersionName()
    return if (name.isNullOrBlank()) BUILD_KIND else "$name-$BUILD_KIND"
}
