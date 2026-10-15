package com.lookie.opluskey.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.currentHeaderHeight
import com.lookie.opluskey.ui.model.ABOUT_ROWS
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.AUTHOR
import com.lookie.opluskey.ui.model.ISSUE_URL
import com.lookie.opluskey.ui.model.MORE_CONFIG_ROWS
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PROFILE_URL
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.REPO_URL
import com.lookie.opluskey.ui.state.LinkCheck
import com.lookie.opluskey.ui.state.linkChecks
import com.lookie.opluskey.ui.state.rememberTick
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/** 「更多选项」页 —— 从首页「其他 → 更多选项」那一行进来（见 [Screen.ABOUT]）。 */
@Composable
internal fun SettingsPage.AboutScreen(scrollBehavior: ScrollBehavior) {
    val tick = rememberTick()
    // 取版本号要过 PackageManager（见 [moduleVersionName]），本页每次重组都算一遍纯属白花。
    // 「运行状态」那张卡是 `@Composable` 读状态、会频繁重组，而版本号在整个进程生命里不变。
    //  这一页走 [appVersionText] —— **总是带构建时刻**（含日期）：这一页本来就回答「我这个包是
    //  哪一个」，「什么时候出的」正是它要说的事。首页那条副标题走 [appVersionLabel]，
    //  仍按构建类型决定带不带。
    val version = remember { appVersionText() }
    Column(
        Modifier
            .fillMaxSize()
            // 大标题随滚动折叠：`nestedScroll` 必须在 `verticalScroll` 之前
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical()
            .verticalScroll(rememberScrollState())
            .padding(bottom = PAGE_BOTTOM_PADDING),
    ) {
        // 顶栏叠在内容上，顶部要自己让出（动态值）。这一页首行是卡片，没有别页首行 `SmallTitle`
        // 自带的上内边距，要另加 [PAGE_TOP_PADDING]，否则贴着顶栏。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior) + PAGE_TOP_PADDING))

        Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            BasicComponent(
                title = "版本",
                // 摘要 = 版号 + 构建类型 + 构建时刻（含日期），见 [appVersionText]
                summary = version,
                startAction = { Icon(PageIcons.Info, null) },
                // 只读信息行，不给 onClick（给了反而像能点）
                cardListPosition = cardListPositionOf(0, ABOUT_ROWS),
            )
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            BasicComponent(
                title = "作者",
                summary = AUTHOR,
                onClick = {
                    tick()
                    openUrl(PROFILE_URL)
                },
                startAction = { Icon(PageIcons.Author, null) },
                cardListPosition = cardListPositionOf(1, ABOUT_ROWS),
            )
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            BasicComponent(
                title = "查看地址",
                summary = REPO_URL,
                onClick = {
                    tick()
                    openUrl(REPO_URL)
                },
                startAction = { Icon(PageIcons.Address, null) },
                cardListPosition = cardListPositionOf(2, ABOUT_ROWS),
            )
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            BasicComponent(
                title = "反馈问题",
                summary = ISSUE_URL,
                onClick = {
                    tick()
                    openUrl(ISSUE_URL)
                },
                startAction = { Icon(PageIcons.Feedback, null) },
                cardListPosition = cardListPositionOf(3, ABOUT_ROWS),
            )
        }

        // 「配置」卡（加导入 / 导出）—— 放在信息卡之后、运行状态之前：
        // 这两项是**动作**，比下面那张诊断表更常被用到；而它们又不像版本 / 作者那样只是「看一眼」。
        //  导出走系统的「保存到」、导入走系统的「打开」，两边都由 hook 侧实现
        //（`SettingsPageHooks`：SAF + `Activity#onActivityResult`），这里只负责派发。
        //  两张卡的行数各算各的（见 [MORE_CONFIG_ROWS]），不能合成一个数。
        Spacer(Modifier.height(GRID_ROW_GAP))
        SmallTitle("配置")
        Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            BasicComponent(
                title = "导出配置",
                // 副标题只报「做什么」的一句话，不解释机制 —— 用户界面不写
                // 「把配置与自定义命令存成一份文件」那种交代细节的写法
                summary = "保存到本地文件",
                onClick = {
                    tick()
                    onExportConfig?.invoke()
                },
                startAction = { Icon(PageIcons.ConfigExport, null) },
                cardListPosition = cardListPositionOf(0, MORE_CONFIG_ROWS),
            )
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            BasicComponent(
                title = "导入配置",
                summary = "从本地文件读取",
                onClick = {
                    tick()
                    onImportConfig?.invoke()
                },
                startAction = { Icon(PageIcons.ConfigImport, null) },
                cardListPosition = cardListPositionOf(1, MORE_CONFIG_ROWS),
            )
        }

        // 「运行状态」（内部叫链路自检）—— 模块要正常工作，得**几段**都通（宿主侧的服务、
        // 注入侧的接管点、配置送达、以及宿主真读到配置），外加一段只能靠实践拿到的证据。
        // 只看「宿主报活了没有」一个布尔的话，答不了「注入侧压根没装」这种
        //（那种情况下宿主照样报活、页面全绿）。
        //  只读信息，没有可点的行。
        //  注意：数据是**读缓存**、不是「进页时现取」：不能写
        //  「进页时现取一次（见 [linkChecks]），不轮询」—— 与实际相反。`linkChecks()` 读的是
        //  `hostServiceUp` / `injectStatus` 这些**已经缓存在页面状态里**的结论，本页**不发任何问句**；
        //  主动报活只有首页那条自检（`HomeScreen`）会做，注入侧也只应答、不主动报
        //  （`inject/KeyHooks` 只在收到 `ACTION_INJECT_QUERY` 时回包）。
        //  所以进本页看到的是「最近一次自检的结论」，想刷新就回首页走一遍。
        //  注意：**这一页是「细节」的落点，措辞仍是给用户看的**：
        // 段名与取值都不出现内部结构（不写「注入侧 / 同步 / 读取 / 就绪」），
        // 想知道「怎么坏的」去看日志（`XLog`）。
        Spacer(Modifier.height(GRID_ROW_GAP))
        SmallTitle("运行状态")
        Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            val checks = linkChecks()
            val broken = checks.firstOrNull { !it.ok && it.blocking }
            val rows = buildList {
                // 第 0 行是**整体结论**，缺哪一段直接点名（后面逐段列细节）
                add(
                    LinkCheck(
                        name = "整体状态",
                        ok = broken == null,
                        detail = if (broken == null) "全部正常" else "${broken.name}异常",
                    ),
                )
                addAll(checks)
            }
            rows.forEachIndexed { i, row ->
                if (i > 0) {
                    HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
                }
                val statusTint by animateColorAsState(
                    targetValue = when {
                        row.ok -> COUITheme.colorScheme.onSurfaceContainer
                        row.blocking -> COUITheme.colorScheme.error
                        else -> COUITheme.colorScheme.onSurfaceVariantSummary
                    },
                    animationSpec = COLOR_TRANSITION_SPEC,
                    label = "LinkStatusTint",
                )
                BasicComponent(
                    title = row.name,
                    summary = row.detail,
                    startAction = {
                        Icon(
                            if (row.ok) PageIcons.LinkOk else PageIcons.LinkBroken,
                            contentDescription = null,
                            // 三档色：「通了」用常规色、故障用 error、「还没拿到证据」用中性色，带平滑色彩过渡。
                            tint = statusTint,
                        )
                    },
                    cardListPosition = cardListPositionOf(i, rows.size),
                )
            }
        }
    }
}
