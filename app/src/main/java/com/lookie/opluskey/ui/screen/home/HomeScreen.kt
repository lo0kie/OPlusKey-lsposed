package com.lookie.opluskey.ui.screen.home

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.BUILD_KIND
import com.lookie.opluskey.SHOW_BUILD_TIME
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.ModuleInfo
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.ConfirmDialog
import com.lookie.opluskey.ui.component.MenuEntry
import com.lookie.opluskey.ui.component.MenuPopup
import com.lookie.opluskey.ui.component.SwitchRow
import com.lookie.opluskey.ui.component.currentHeaderHeight
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.CONFIG_READ_FAILED_HINT
import com.lookie.opluskey.ui.model.DISCLAIMER_TEXT
import com.lookie.opluskey.ui.model.DISCLAIMER_TITLE
import com.lookie.opluskey.ui.model.DONATE_LIST_TITLE
import com.lookie.opluskey.ui.model.DonateTarget
import com.lookie.opluskey.ui.model.FREE_NOTICE_TEXT
import com.lookie.opluskey.ui.model.FREE_NOTICE_TITLE
import com.lookie.opluskey.ui.model.HOME_ACTION_ROWS
import com.lookie.opluskey.ui.model.HOME_BINDING_ROWS
import com.lookie.opluskey.ui.model.HOME_MORE_ROWS
import com.lookie.opluskey.ui.model.HOST_ALIVE_TIMEOUT_MS
import com.lookie.opluskey.ui.model.KEY_DISABLED_HINT
import com.lookie.opluskey.ui.model.MODULE_UNAVAILABLE_HINT
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PENDING_DEVICE_HINT
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.QUERY_ROUNDS
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.UNSUPPORTED_DEVICE_HINT
import com.lookie.opluskey.ui.screen.home.DonateDialog
import com.lookie.opluskey.ui.state.acceptDisclaimerAndEnable
import com.lookie.opluskey.ui.state.devicePending
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
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.SnackbarDuration
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.delay

/**
 * 首页（快捷键）。
 *
 * 从 `SettingsPage.kt` 按职责拆出：成员改为 [SettingsPage]
 * 的扩展/同包顶层声明，调用点零改动。
 */
@Composable
internal fun SettingsPage.HomeScreen(scrollBehavior: ScrollBehavior) {
    val cfg = configState.config.value
    val key = cfg.key(KeyId.PLUS)
    // 触觉反馈入口提到页面级 —— 「按键绑定」那行的主开关也要用（它排在「按键行为」卡之前）
    val tick = rememberTick()
    // 「启用按键绑定」要的那次确认：true = 弹窗开着，**此时还没写盘**（点了确认才写）。
    //  只在「打开」这一侧要；关闭直接写。弹窗在本页那张卡后面（见下面的 [ConfirmDialog]）。
    var disclaimerPending by remember { mutableStateOf(false) }

    // 免责声明**确认之后**接着弹的那一个「温馨提醒」（模块免费、认准官方正版）。
    //  与上面那次确认分开两个开关：这一条**不写盘、不拦任何操作**，两个按钮都只是关掉它；
    //  合成一个开关会让「关掉它」和「没确认免责声明」变成同一件事，总闸就跟着被卡住了。
    var freeNoticePending by remember { mutableStateOf(false) }

    // 「捐赠打赏」那一行：锚点、菜单开关、选中的去处（非 null = 收款码窗口开着）。
    //  菜单与收款码窗口都是**浮层**，调用点在本页末尾（与「更多选项」那张卡同一段之后）。
    val donateClick = remember { PreciseClickState() }
    var showDonateMenu by remember { mutableStateOf(false) }
    var donateTarget by remember { mutableStateOf<DonateTarget?>(null) }

    // 链路上每一段在不在 ⇒ 缺哪一段就报哪一段（见 `ui/state/LinkCheck.kt` 的 `linkChecks`）。
    //  判据只能是**模块自己报活**：查宿主那支侧键服务不管用 —— 它是宿主自己的常驻服务，
    // 系统会自己把它拉起，服务在跑 ≠ 模块在跑（真机实测：重启后没注入它照样在跑）。
    //  每轮对**两端各记一次代次**、各发一句「在不在」（报活广播是一次性的，我们可能已经错过了），
    // 等一档再看代次有没有变：没变就是没加载。
    //  注意：**一轮不够，要重问**（用户报的「换 ROM 后整页一直是灰的，退出去再进来才好」）：
    //  注入侧回不了包有两种常见成因 —— 它借不到 Context、或它的宿主 uid 还没解析出来
    //  （那时 fail-closed 会把问句丢掉，见 `KeyHooks.isFromHost`）。两种都会自愈，只是**不是立刻**；
    //  而这条 effect 只发一次的话，页面会一直停在「未就绪」上等一个再也不会来的回包。
    //  这里最多问 [QUERY_ROUNDS] 轮，**两端都答上就提前收工** —— 已经就绪时只问一次；
    //  一直问不到也不会转下去：适配清单外的机器本来就该一直禁用，无限轮询只是白耗电。
    LaunchedEffect(Unit) {
        var hostEverAnswered = false
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
            // 没回音就把上一轮的回包清掉，免得拿旧结论当新结论（注入侧那一档是「清成未知」）
            if (!injectAnswered) link.injectStatus = null
            if (hostAnswered && injectAnswered) return@LaunchedEffect
            if (round > 0) XLog.w("链路自检第 ${round + 1} 轮仍未齐（宿主=$hostAnswered 注入=$injectAnswered）")
        }
        //  注意：**只有问满所有轮都没回音才把宿主降档**：每轮超时就
        //  `hostServiceUp = false`，一轮偶然没回音（广播被压 / 宿主正忙）就会把一个**已经确认过**
        //  的宿主降成「没起来」；而同一段的注入侧那一档只是把结论清成「未知」（`injectStatus = null`），
        //  两端口径还不一致。现在与注入侧对齐：单轮漏答不算数，全程没答才判它没起来。
        if (!hostEverAnswered) link.hostServiceUp = false
        XLog.w("链路自检问了 $QUERY_ROUNDS 轮都没问齐，不再重问（详见「更多选项 → 运行状态」）")
    }
    // 这一页所有可交互项的**总闸**：机型允许 + 主开关开着 + 链路齐了（见 [linkReady]，它现在等同于
    // 「运行状态里除『主开关已关闭』与『使用验证』外每一段都 ok」）+ 配置可写
    // （建页那次没读成功 ⇒ 只读，写回一律被拒，见 `SettingsPage.configReadOnly`）。
    // 有哪一条不满足时改配置不会生效，放用户去改只会误导（底部那条常驻提示会说明该怎么办）。
    //  注意：起**主开关也吃这个闸**（`linkReady` + 另两条）。顶栏 ⋮ **不吃** ——
    // 它本身永远可点，里面只有「重启服务 / 导出日志 / 清空配置」三项跟着灰，
    // 「默认界面」（逃生口）始终可用（见 `ResetMenu`；「更多选项」那一项已从菜单删掉，只留首页这一行）。
    //  适配清单外的组合：只注入了界面层（见 `core/model/DeviceSupport`），
    // 链路永远不通 ⇒ 本来就整页禁用；`deviceSupported` 只是让**原因**说得清。
    val pageEnabled = key.enabled && linkReady && !configState.readOnly && deviceSupported

    // 判据就是**同一个总闸**（[linkReady]），不再另算一遍「哪一段 not-ok」：这样「运行状态里报异常」
    //  与「首页弹提示」永远是同一件事 —— 「还没被验证过」是信息、不是故障，「主开关自己关着」属用户
    //  意图、也不是故障，两档都不该骂人。
    //  落点由 [HostLinkState.snackbar] 那一层的 `SnackbarHost` 给（屏幕底边），这里只管弹 / 收。
    //  `newestSnackbarData() == null` 那半句是**去重**：首页被切走再回来会重新组合，
    // 不去重就会叠出第二条（真机反馈「弹了两个 Snackbar」）。
    //
    //  注意：**报的是整体结论，不是缺的那一段的名字**：具体缺哪一段
    // 是给「更多选项 → 运行状态」看的，首页这条只回答「现在能不能用」。
    //  让页面变灰的每一条原因**各说各的**，**顺序就是优先级**（与 [pageEnabled] 那几项一一对应）：
    //   · 机型不支持 ⇒ 这台机器上就是不能用（[UNSUPPORTED_DEVICE_HINT]，等也没用）；
    //   · 机型在、系统版本没适配 ⇒ 只能等作者适配（[PENDING_DEVICE_HINT]）；
    //   · 配置没读成功 ⇒ 用户重进页面就能解决（[CONFIG_READ_FAILED_HINT]）；
    //   · 主开关关着 ⇒ 就是它关着导致的（[KEY_DISABLED_HINT]，拨回来即可）；
    //   · 链路没齐 ⇒ 请稍候（[MODULE_UNAVAILABLE_HINT]）。
    //  ⚠️ 主开关那一档排在链路**之前**：「链路没齐」有两类成因 —— 一类会自愈（注入侧回包还没到），
    //  一类是用户当下就该动手的那一件（主开关关着）。把主开关排在后面，**清空配置**之后会先报
    //  「模块暂不可用，请稍候」：那个「稍候」等的是回包，用户等不出结果，真正该做的（把主开关
    //  打开）反而被盖住。代价：链路真故障**又**恰逢主开关关着时，
    //  报的是「模块未启用」而那一行当时是灰的（`enabled` 吃 [linkReady]）——
    //  拨不动的人会去看「更多选项 → 运行状态」，那儿逐段说清了缺哪一段。
    val blockHint =
        when {
            devicePending -> PENDING_DEVICE_HINT
            !deviceSupported -> UNSUPPORTED_DEVICE_HINT
            configState.readOnly -> CONFIG_READ_FAILED_HINT
            !key.enabled -> KEY_DISABLED_HINT
            !linkReady -> MODULE_UNAVAILABLE_HINT
            else -> null
        }
    LaunchedEffect(blockHint) {
        // 去重判据是**原因本身**，不是「当前有没有东西」：那条提示是 `Indefinite`（不会自己走），
        //  只判 newest 是否为 null 的话，原因从 A 变成 B 时旧条还在、新条被跳过 ——
        //  底部一直停在已经不成立的那条原因上，用户照着它排查会被带偏。
        val shown = link.snackbar.newestSnackbarData()
        if (blockHint == null) {
            shown?.dismiss()
        } else if (shown?.visuals?.message != blockHint) {
            shown?.dismiss()
            XLog.w("首页提示：$blockHint")
            link.snackbar.showSnackbar(
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
        //  注意：**不再另加 [PAGE_TOP_PADDING]**：这一页没有固定带，顶栏底下
        // 没有看得见的边界，而顶栏自己底部就留了 12dp（库的 `LargeTitleBottomPadding`）
        // —— 再加那 8dp，首行距标题的空白明显比带子页大一截（真机反馈「首页的上边距比其他页多」）。
        //  带子页那一档来自带子自己的 8dp 下内边距，两边视觉上正好齐平。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        SmallTitle(I18n.get(Strings.BINDING_TITLE, "按键绑定"))

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
            //  注意：`!configReadOnly` 这一条是被真机逼出来的：只读态下刚进页时
            // 链路还没报到（`linkReady=false`）⇒ 主开关是灰的；等注入侧回包一到、`linkReady` 变 true，
            // 它就**自己重新亮起来**了，用户当场拨了三下（每次都被 `write` 拒掉）。
            // **「拨了没反应」比「一开始就灰着」更难读**，所以这一档必须一起禁。
            //  `deviceSupported` 同理：适配清单外的组合只注入了界面层，拨它也不会生效。
            //
            // 模块没就绪时它照样禁用 —— 那种情况下拨这个开关也不会生效，页面底部那条常驻提示
            // （[MODULE_UNAVAILABLE_HINT] / [CONFIG_READ_FAILED_HINT] / [UNSUPPORTED_DEVICE_HINT] /
            //  [PENDING_DEVICE_HINT]）
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
                title = I18n.get(Strings.SETTING_ENABLE_BINDING, "启用按键绑定"),
                // 开着时说明「关掉会怎样」；关着时直接报状态 —— 光看一个灰开关不知道后果
                summary =
                    if (key.enabled) {
                        // （14 字）正好一行放得下，再多一个字就折行、把这张卡撑高
                        I18n.get(Strings.HOME_SWITCH_DESC_ENABLED, "关闭后此模块将停用")
                    } else {
                        I18n.get(Strings.HOME_SWITCH_DESC_DISABLED, "侧键由系统处理")
                    },
                // 行首图标与下一行对齐用（不给的话两行的标题左边缘会差一个图标宽）。
                // 电源符号 = 「这一行是总开关」。
                icon = PageIcons.Power,
                checked = key.enabled,
                position = cardListPositionOf(0, HOME_BINDING_ROWS),
                enabled = linkReady && !configState.readOnly && deviceSupported,
            ) {
                tick()
                // 打开之前先要一次确认：没确认过就只弹窗、**不写盘**。
                //  顺序不能反过来 —— 先写后弹等于「先启用了再问你同不同意」。
                //  关闭不需要确认，直接写。
                if (it && !configState.config.value.disclaimerAccepted) {
                    XLog.w("免责声明：还没确认过，弹窗（总闸先不打开）")
                    disclaimerPending = true
                } else {
                    setKeyEnabled(it)
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))

            // 第 2 行：进「按键绑定」子页（纯跳转，行尾只有箭头）。
            // 进不去那个页面，也就不存在「关着还能改配置」。
            //
            //  行尾箭头**自己画**：库的 `ArrowPreference` 那颗是 COUI 图标集的
            // 12x24 窄箭头（描边 1.4），与页面其余 Tabler 图标（描边 2）不是一支笔。
            // 自己画就得自己管两档色 —— 照抄库那支选择器
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
                cardListPosition = cardListPositionOf(1, HOME_BINDING_ROWS),
                enabled = pageEnabled,
                onClick = {
                    navTo(Screen.BINDING)
                },
            )

            //  这张卡**只有两行**：第 1 行主开关、第 2 行进「按键绑定」子页。
            // `setRingerModeInternal` 一起删掉，理由见这张卡上方那段注释。
        }

        // 首次启用（以及清空配置之后再启用）要的那一次确认 —— 归上面「启用按键绑定」那一行管，
        //  所以摆在那张卡后面，不跟别的开关混在一起。
        //  确认之后由 [acceptDisclaimerAndEnable] 一次写盘（记录 + 总闸）。
        ConfirmDialog(
            show = disclaimerPending,
            title = DISCLAIMER_TITLE,
            summary = DISCLAIMER_TEXT,
            action = I18n.get(Strings.DIALOG_DISCLAIMER_ACKNOWLEDGE, "我已了解"),
            // 不丢用户任何东西的确认 ⇒ 走主题色，不给红：红会读成「这个操作有危险」，
            // 而这里只是「你点的那一下要你先看一眼说明」。
            positiveDanger = false,
            onDismiss = {
                XLog.w("免责声明：没确认，不启用（总闸保持关闭）")
                disclaimerPending = false
            },
            onConfirm = {
                XLog.w("免责声明：已确认，启用按键绑定")
                disclaimerPending = false
                acceptDisclaimerAndEnable()
                // 接着弹「温馨提醒」：确认免责声明的人正是最该被告知「这模块不收费」的那个人。
                //  两个弹窗同一帧切换（上一个在退场、这一个在进场）—— 这是控件给的样子，
                //  要等前者退净得用库里那个 `onDismissFinished`，而 [ConfirmDialog] 没往外露。
                freeNoticePending = true
            },
        )

        // 紧接着那个「温馨提醒」：模块完全免费、认准官方正版。
        //  按口径，两颗按钮（确认 / 取消）**都只是关掉它** —— 它不写盘、不拦操作，
        //  所以两个回调是同一件事；分两个入口只是把「看过了」和「划走」分成两种按法。
        ConfirmDialog(
            show = freeNoticePending,
            title = FREE_NOTICE_TITLE,
            summary = FREE_NOTICE_TEXT,
            action = I18n.get(Strings.DIALOG_CONFIRM, "确认"),
            positiveDanger = false,
            onDismiss = { freeNoticePending = false },
            onConfirm = { freeNoticePending = false },
        )

        // 「按键绑定」卡片与「按键行为」分组之间留一档空档
        Spacer(Modifier.height(8.dp))

        SmallTitle(I18n.get(Strings.HOME_BINDING_CARD_TITLE, "按键行为"))

        // （见 [LongRepeatCard]）—— 它们只管长按那一个手势，摆在这一页会让人以为对三个手势都生效。
        // 搬去长按 tab 是同一个理由：它们只管自己那一个手势，
        // 摆在这一页与「振动反馈」并列，会被当成对三个手势都生效的全局项。
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
            //  它是**全局**项，不分命令 —— 它回答的是「按下去到底有没有被触发」，
            // 与「这条命令是什么」无关，每条命令各配一次是白费力气。
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
        }

        // 版本 / 仓库地址那两行搬去**独立的「更多选项」页**
        // （[AboutScreen]，从顶栏 ⋮ 菜单进）—— 它们是「想知道时才看一眼」的信息，
        // 摆在首页会把常用的那几张卡挤下去。

        // 「其他」分组：进「更多选项」页（[AboutScreen]：
        // 运行状态、配置导入导出、版本与地址）。
        //
        // 注意：这一行**不吃任何闸**（同日用户要求「首页的更多行不跟随总闸禁用」）：
        // 它是进「更多选项」页的**唯一入口**（⋮ 里那一项已删），而那一页里有「运行状态」——
        // 模块整页禁用（机型不支持 / 链路没齐 / 配置只读 / 主开关关着）时，用户正需要进去看断在哪一段。
        //  行尾箭头与行首图标因此都恒用常规色，不再按 `pageEnabled` 换灰。
        Spacer(Modifier.height(8.dp))
        SmallTitle(I18n.get(Strings.HOME_OTHER_SECTION, "其他"))
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
            //  这一行同样**不吃任何闸**：打赏与模块启没启用无关，整页禁用时它照样该能点。
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
                modifier = Modifier.preciseClickAnchor(donateClick),
                onClick = {
                    tick()
                    showDonateMenu = true
                },
            )
        }

        MenuPopup(
            show = showDonateMenu,
            preciseClick = donateClick,
            onDismiss = { showDonateMenu = false },
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
                                showDonateMenu = false
                                donateTarget = target
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
                            showDonateMenu = false
                            navTo(Screen.DONATE_LIST)
                        },
                    )
                },
        )

        // 收款码窗口（居中）：内容是那张图，图还没进包时退成品牌字形（见 [DonateDialog]）
        DonateDialog(target = donateTarget, onDismiss = { donateTarget = null })

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
 * 模块自己的版本号（读不到返回 `null`，不抛异常）。
 *
 *  包名必须显式用 [ModuleInfo.PACKAGE]（模块自己），**不能用 `context.packageName`**
 * —— 这一页跑在宿主进程里，那个 `context` 是宿主的 Context，读出来是宿主 App 的版本号。
 *
 *  `BuildConfig` 在本工程是**关掉**的（见 `build.gradle.kts` 的 `buildFeatures`），所以只能
 * 从 `PackageManager` 读。
 */
private fun SettingsPage.moduleVersionName(): String? =
    try {
        context.packageManager
            .getPackageInfo(ModuleInfo.PACKAGE, 0)
            .versionName
    } catch (e: Throwable) {
        XLog.w("读模块版本号失败：${e.javaClass.simpleName}")
        null
    }

/**
 * 出包时注入的构建时刻（`yyyy-MM-dd HH:mm:ss`，gradle 的 `resValue`，见 `build.gradle.kts` 的 `build_id`）。
 *
 *  不能写 `R.string.build_id`：页面跑在**宿主**进程里，`R` 会解析到宿主自己的资源表，取出来
 * 是别人的字符串（或 0）。必须按**模块包名**去拿我们那份资源。拿不到（没走 gradle 出的包 /
 * 宿主限制）就退回 [ModuleInfo.BUILD_ID] **整串** —— 它本身是 `日期-序号`，也是带日期的。
 */
private fun SettingsPage.moduleBuildStamp(): String =
    try {
        val res =
            context.packageManager
                .getResourcesForApplication(ModuleInfo.PACKAGE)
        val id = res.getIdentifier("build_id", "string", ModuleInfo.PACKAGE)
        res.getString(id).takeIf { it.isNotBlank() }
    } catch (e: Throwable) {
        XLog.w("读构建标识失败（退回常量）：${e.javaClass.simpleName}")
        null
    } ?: ModuleInfo.BUILD_ID

/**
 * 完整版本标识：**模块自己**的版本号 + [BUILD_KIND] + 构建时刻
 * （`1.0.0-debug-2026-10-15 15:07:12`）。
 *
 *  两处用它：导出的日志头（见 `ConfigTransfer.exportLog`）与**「更多选项」页的版本行** ——
 * 排查时要据此认出是哪个包、什么时候出的，而「什么时候」必须带日期才认得出。
 * 首页那条副标题走 [appVersionLabel]（它按构建类型决定要不要带末段）。
 */
internal fun SettingsPage.appVersionText(): String {
    val name = moduleVersionName()
    val build = moduleBuildStamp()
    // 三段用 `-` 连（要求「· 改成 -」）。
    return if (name.isNullOrBlank()) {
        "$BUILD_KIND-$build"
    } else {
        "$name-$BUILD_KIND-$build"
    }
}

/**
 * 首页大标题那条副标题用的版本（不是「更多选项」页 —— 那一页走 [appVersionText]，见它）。
 *
 *  前面带一截 `version-`：这串东西摆在大标题底下、没有标签，光一个 `1.0.0-release`
 *  看不出它是什么（页面上别处也没有「版本」两个字给它托底）。
 *
 *  **debug 带构建时刻、release 不带**（[SHOW_BUILD_TIME]）：自用包一天里会出好几个，光看
 * `1.0.0-debug` 分不出手上这个是哪一次出的；release 的首页是面向用户的界面，
 * 构建时刻对用户没意义（要看的去「更多选项」页）。读不到版本号时只显示
 * [BUILD_KIND]，不抛异常。
 */
internal fun SettingsPage.appVersionLabel(): String {
    val version =
        if (SHOW_BUILD_TIME) {
            appVersionText()
        } else {
            val name = moduleVersionName()
            if (name.isNullOrBlank()) BUILD_KIND else "$name-$BUILD_KIND"
        }
    return "$VERSION_LABEL_PREFIX$version"
}

/** 首页副标题那串版本前面的前缀（见 [appVersionLabel]）。 */
private const val VERSION_LABEL_PREFIX = "version-"
