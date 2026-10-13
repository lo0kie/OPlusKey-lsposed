package com.lookie.opluskey.page

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import com.lookie.opluskey.BUILD_KIND
import com.lookie.opluskey.KeyModule
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.KeyId

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
            // 主开关是这张卡的第一行，且只有它自己永远可操作：关掉之后全页（含这一页的
            // 其它控件、「按键绑定」子页、顶栏菜单两项）都禁用，它再被禁用就再也打不开了。
            //
            // 它写的是 [KeyConfig.enabled] —— 执行侧早就存在的总闸（注入侧
            // `hook/SideKeyInterceptor` 与宿主侧 `gesture/GestureReceiver` 各有一道
            // `if (!…enabled) return`），所以这一开关不只是界面置灰，是真的把功能关掉。
            //
            // 开关 `onCheckedChange = null`，切换交给整行点击 —— 与「振动反馈」行同一条规矩
            // （行与开关各切一次会「闪一下又变回去」）。用库的 `SwitchPreference` 时，
            // [NoSwitchHaptic] 把库 `Switch` 自己那次无条件振动掐掉，只留 `tick()` 这条通用通道。
            NoSwitchHaptic {
                SwitchPreference(
                    title = "启用按键绑定",
                    // 开着时说明「关掉会怎样」；关着时直接报状态 —— 光看一个灰开关不知道后果
                    summary = if (key.enabled) {
                        // （14 字）正好一行放得下，再多一个字就折行、把这张卡撑高
                        "关闭后此模块将停用"
                    } else {
                        "侧键交还系统处理"
                    },
                    checked = key.enabled,
                    onCheckedChange = {
                        tick()
                        setKeyEnabled(it)
                    },
                    // 行首图标与下一行对齐用（不给的话两行的标题左边缘会差一个图标宽）。
                    // 电源符号 = 「这一行是总开关」。
                    startAction = { Icon(PageIcons.Power, null) },
                    cardListPosition = cardListPositionOf(0, HOME_BINDING_ROWS),
                )
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
                    if (key.enabled) {
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
                        tint = if (key.enabled) {
                            COUITheme.colorScheme.onSurfaceVariantActions
                        } else {
                            COUITheme.colorScheme.disabledOnSecondaryVariant
                        },
                    )
                },
                cardListPosition = cardListPositionOf(1, HOME_BINDING_ROWS),
                enabled = key.enabled,
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
            // 同上：`BasicComponent` + 行尾 `Switch` 的手拼换成库的 `SwitchPreference`。
            // 振动见 [NoSwitchHaptic]（库 `Switch` 自己会无条件振一次）。
            NoSwitchHaptic {
                SwitchPreference(
                    title = "振动反馈",
                    summary = "命令触发时振动",
                    checked = cfg.global.vibrate,
                    onCheckedChange = {
                        tickNow()
                        setVibrate(it)
                    },
                    //  startAction 槽位库不上色：主开关为关时得自己跟着灰
                    //（与「按键绑定」行的 TouchApp 同一条规矩）。
                    startAction = {
                        if (key.enabled) {
                            Icon(PageIcons.Vibrate, null)
                        } else {
                            Icon(
                                PageIcons.Vibrate,
                                null,
                                tint = COUITheme.colorScheme.disabledOnSecondaryVariant,
                            )
                        }
                    },
                    cardListPosition = cardListPositionOf(0, HOME_ACTION_ROWS),
                    // `enabled = false` 时 COUI 不挂 `clickableModifier`、开关也跟着灰，
                    // 行点击与开关点击自然都失效。
                    enabled = key.enabled,
                )
            }

            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))

            // 「执行时提示」：执行命令时弹一条 Toast（内容是**命令名**）。
            //  它原来是**每条命令自己**的开关（只在「自定义命令」页多条命令的表单里出现），
            // 2026-10-13 搬到这一页做成**全局**项 —— 它回答的是「按下去到底有没有被触发」，
            // 与「这条命令是什么」无关，每条命令各配一次是白费力气。
            //  判定侧读它见 `hook/CustomCommandRunner`（宿主进程里执行时**现读**配置，
            // 所以拨完开关立刻生效，不必重启）。
            NoSwitchHaptic {
                SwitchPreference(
                    title = "执行时提示",
                    summary = "执行命令时系统提示",
                    checked = cfg.global.toastOnRun,
                    onCheckedChange = {
                        tick()
                        setToastOnRun(it)
                    },
                    // 与「振动反馈」同一条规矩：`startAction` 槽位库不上色，主开关为关时自己灰
                    startAction = {
                        if (key.enabled) {
                            Icon(PageIcons.CommandToast, null)
                        } else {
                            Icon(
                                PageIcons.CommandToast,
                                null,
                                tint = COUITheme.colorScheme.disabledOnSecondaryVariant,
                            )
                        }
                    },
                    cardListPosition = cardListPositionOf(1, HOME_ACTION_ROWS),
                    enabled = key.enabled,
                )
            }
        }

        // 版本 / 仓库地址那两行（原来这张「其他」卡）2026-10-13 搬去**独立的「关于模块」页**
        //（[AboutScreen]，从顶栏 ⋮ 菜单进）—— 它们是「想知道时才看一眼」的信息，
        // 摆在首页会把常用的那几张卡挤下去。
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
        XLog.i("已请求打开地址：$url（${if (host != null) "同任务" else "新任务"}）")
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
 * 「版本」那一行的值：**模块自己**的版本号 + [KeyModule.BUILD_ID]。
 *
 *  包名必须显式用 [KeyModule.MODULE_PACKAGE]（模块自己），**不能用 `context.packageName`**
 * —— 这一页跑在宿主进程里，那个 `context` 是宿主的 Context，读出来是宿主 App 的版本号
 *
 * 版本号从 `PackageManager` 读（`BuildConfig` 在本工程是**关掉**的，见 `build.gradle.kts`
 * 的 `buildFeatures`）；读不到只显示 `BUILD_ID`，不抛异常。
 */
internal fun SettingsPage.appVersionText(): String {
    val name = try {
        context.packageManager
            .getPackageInfo(KeyModule.MODULE_PACKAGE, 0)
            .versionName
    } catch (e: Throwable) {
        XLog.w("读模块版本号失败：${e.javaClass.simpleName}")
        null
    }
    // 手写常量（`2026-10-12-0001`），根本不跟着出包变。现在换成**出包时注入**的
    // 真构建标识（gradle 的 `resValue`，见 `build.gradle.kts` 的 `build_id`）。
    //
    //  不能写 `R.string.build_id`：页面跑在**宿主**进程里，`R` 会解析到宿主自己的
    // 资源表，取出来是别人的字符串（或 0）。必须按**模块包名**去拿我们那份资源。
    // 拿不到（没走 gradle 出的包 / 宿主限制）才退回那个常量的末段。
    val build = try {
        val res = context.packageManager
            .getResourcesForApplication(KeyModule.MODULE_PACKAGE)
        val id = res.getIdentifier("build_id", "string", KeyModule.MODULE_PACKAGE)
        res.getString(id).takeIf { it.isNotBlank() }
    } catch (e: Throwable) {
        XLog.w("读构建标识失败（退回常量）：${e.javaClass.simpleName}")
        null
    } ?: KeyModule.BUILD_ID.substringAfterLast('-')
    // （原先它单独占一行，已删）。三段用 `-` 连（同日要求「· 改成 -」）。
    return if (name.isNullOrBlank()) {
        "$BUILD_KIND-$build"
    } else {
        "$name-$BUILD_KIND-$build"
    }
}
