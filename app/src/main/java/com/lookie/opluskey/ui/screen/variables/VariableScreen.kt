package com.lookie.opluskey.ui.screen.variables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lookie.opluskey.core.config.VariableStore
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.VarType
import com.lookie.opluskey.core.model.Variable
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.Feedback
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.VarListSignal
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Abc
import io.github.lyxnx.compose.ui.tablericons.outline.Number
import io.github.lyxnx.compose.ui.tablericons.outline.ToggleLeft
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 「自定义变量」页 —— 管清单的那一页：首页「其他」卡那一行，以及绑定页变量那格弹出的抽屉
// 右上角那颗 + 都落到这里（`ActionCatalog.VARS_TILE` 那一格本身是「挑一步操作绑上去」）。
//
// 这一页只做三件事：**列出**变量、点一颗**改它**（进「变量操作」页的管理模式）、顶栏右上角**新建**。
//  变量的语义与存储全在 `core/config/VariableStore` + `core/model/Variable`：这里一条规则都不重写
//  —— 包括「值怎么归一」（走 `Vars.assign`，与命令执行侧同一支）与「名字能不能用」（`VariableName`）。

/**
 * 变量列表页。
 *
 *  **读盘在 IO 线程**（与 `loadPageState` 同一条纪律）：`VariableStore.load` 会读文件、剥外皮、
 *  解 JSON，放在组合线程上就是首帧卡一下。
 */
@Composable
internal fun SettingsPage.VariableScreen(scrollBehavior: ScrollBehavior) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var vars by remember { mutableStateOf<List<Variable>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    /**
     * 落盘并刷新。`block` 返回 `null` = 这次改不了（名字 / 值不符合类型，原因已经进日志）——
     * 界面只说一句「改不了」，不猜是哪一条规则。
     */
    fun apply(block: (List<Variable>) -> List<Variable>?) {
        scope.launch {
            val next = withContext(Dispatchers.IO) { block(vars) }
            if (next == null) {
                hint(I18n.get(Strings.VAR_FAILED, "改不了"))
            } else {
                //  不在这里直接改 `vars`：让下面那个 `LaunchedEffect` 按记号重读一遍 ——
                //  「这一页自己新建」与「变量操作页改值 / 换配置」就落到同一份刷新逻辑上
                VarListSignal.bump()
                hint(I18n.get(Strings.VAR_SAVED, "已保存"))
            }
        }
    }

    //  **跟着记号重读**：去「变量操作」页改过值 / 删过一颗、或换了一份配置（变量跟着配置走）之后
    //  回来，这一页的组合还在栈里（没注销），不重读就还是改之前那份清单。
    //  首次进页同样走这一支（初值 0）。
    LaunchedEffect(VarListSignal.generation) {
        //  换页时页面级浮窗表会把抽屉收掉（见 `PageEffects` 的 `closeOverlays`）——
        //  「新建」这个开关得跟着回位，否则下次进这一页会看到一个自己弹出来的抽屉。
        VariablePageState.creating = false
        vars = withContext(Dispatchers.IO) { readVars() }
        loaded = true
    }

    //  加法入口在**顶栏右端**（见 `VariableHeaderAdd`），这一页不留右下角 FAB：
    //  页里只有一列变量，那颗 FAB 会一直压着最后一行的右侧。
    BoxWithConstraints(Modifier.fillMaxSize()) {
        //  空态要「占满一屏再居中」：普通滚动列里没有 `fillParentMaxHeight`（那是 `LazyItemScope`
        //  才有的），只能自己按视口算 —— 见下面那个 `Box` 的高度。
        val viewport = maxHeight
        val topInset = currentHeaderHeight(scrollBehavior)
        //  `loaded` 之前不判空：读盘那几毫秒里闪一下整屏空态，比什么都不显示更像出错
        val empty = loaded && vars.isEmpty()
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .verticalScroll(scroll),
        ) {
            //  **让出顶栏**：它是压在内容上的浮层，滚动内容的第一项仍从 y=0 起 ——
            //  不先垫这一档，下面那块的中线就会整体偏上（真机口径「没有垂直居中」）。
            Spacer(Modifier.height(topInset))
            if (empty) {
                //  高度取「视口 − 顶栏」：与上面那个 `Spacer` 加在一起**正好一屏**，
                //  于是这块的中心就是可见区的中心（不多不少），同时内容高度不小于视口 ——
                //  手一拖照样能越界回弹（这正是空态不做成浮层的原因）。
                Box(
                    Modifier.fillMaxWidth().height(viewport - topInset),
                    contentAlignment = Alignment.Center,
                ) {
                    //  排版走 [Feedback]（「选择应用」「条件执行」「已保存的命令」那几块同一份），
                    //  字形也取同一张脸：说的都是「这里什么都没有」。
                    Feedback(
                        glyph = { tint ->
                            Icon(
                                PageIcons.NoMatch,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                tint = tint,
                            )
                        },
                        text = I18n.get(Strings.VARIABLES_EMPTY, "还没有变量，点右上角那颗 + 新建一颗"),
                    )
                }
            } else {
                SectionTitle(I18n.get(Strings.VARIABLES_TITLE, "自定义变量列表"))
                CardList(rows = vars.map { rowOf(it) })
                //  页底留白 —— 与全项目同一档、同一种写法（挂在滚动内容里）；
                //  空态那一支自己就是一屏，不吃这一档（吃了会把中线抬上去半档）
                Spacer(Modifier.height(PAGE_BOTTOM_PADDING))
            }
        }
    }

    VariableOverlays(onApply = ::apply)
}

/**
 * 这一页那两层浮层：**新建**（抽屉）与**改一颗**（居中弹窗）。
 *
 *  点一颗变量不再进「变量操作」页 —— 那条路（挑一步）留给按键绑定 / 命令步骤 / 条件分支，
 *  这一页面对的永远是「已经有了的这一颗」，弹一张小窗就够了。
 */
@Composable
private fun SettingsPage.VariableOverlays(onApply: ((List<Variable>) -> List<Variable>?) -> Unit) {
    CreateSheet(
        show = VariablePageState.creating,
        onDismiss = { VariablePageState.creating = false },
        onCreate = { name, type, initial ->
            VariablePageState.creating = false
            onApply { old -> createVar(old, name, type, initial) }
        },
    )
    VarManageDialog()
}

/**
 * 一颗变量一行：标题是名字，副标题是「当前值 - 类型」，行首那颗字形**按类型给**。
 *
 *  副标题里值可能是空串（文本变量允许）—— 那时只写类型，免得它变成一条孤零零的分隔符。
 *
 *  点一行弹的是**居中的操作弹窗**（改当前值 / 删除，见 `VarManageDialog`）—— 不再滑进一整页。
 */
private fun SettingsPage.rowOf(v: Variable): CardRow {
    val type = typeLabel(v.type)
    return CardRow.Item(
        title = v.name,
        summary = if (v.value.isBlank()) type else "${v.value} - $type",
        icon = typeIcon(v.type),
        arrow = true,
        onClick = { VarManageState.open(v) },
    )
}

/** 类型名（三档）—— 列表副标题与新建表单共用这一处。 */
internal fun typeLabel(type: VarType): String =
    when (type) {
        VarType.STRING -> I18n.get(Strings.VAR_TYPE_STRING, "文本")
        VarType.NUMBER -> I18n.get(Strings.VAR_TYPE_NUMBER, "数字")
        VarType.BOOLEAN -> I18n.get(Strings.VAR_TYPE_BOOLEAN, "Boolean")
    }

/**
 * 三档类型各自的图标 —— **一眼分得开**：文本是 `abc`、数字是 `123`、布尔是开关。
 *
 *  两处在用：列表每一行的**行首**（那一行是哪一档，光看名字看不出来）、新建表单里那个
 *  「类型」菜单（三行都用同一个图标时，「现在是哪一档」只能靠文字读，菜单里三个一样的小方块
 *  也帮不上忙）。
 *
 *  与 [typeLabel] 摆在一起：同一件事（这一档类型叫什么 / 长什么样）的两面，改一处漏一处的
 *  表现就是「列表说文本、菜单画成开关」。
 */
internal fun typeIcon(type: VarType): ImageVector =
    when (type) {
        VarType.STRING -> TablerIcons.Outline.Abc
        VarType.NUMBER -> TablerIcons.Outline.Number
        VarType.BOOLEAN -> TablerIcons.Outline.ToggleLeft
    }

/**
 * 读全部变量；没写过 / 读不懂都当空表。
 *
 *  三个调用点：「自定义变量」页自己（列表与空态）、**「变量操作」页**（挑哪一颗 / 改它的值）、
 *  以及命令的步骤抽屉 —— 几处必须读同一份，否则能挑到一颗已经不存在的变量。
 */
internal fun readVars(): List<Variable> =
    when (val l = VariableStore.load()) {
        is VariableStore.Load.Ok -> {
            l.snapshot.vars
        }

        VariableStore.Load.Absent, VariableStore.Load.Unreadable -> {
            emptyList()
        }
    }

/**
 * 新建一颗：重名 / 名字或初始值不合法都不写盘（原因进日志，界面只说「改不了」）。
 *
 *  判重名按名字来 —— 同名的两颗永远只有第一颗会被引用（与命令库那条同名纪律同一个道理）。
 */
private fun createVar(
    old: List<Variable>,
    name: String,
    type: VarType,
    initial: String,
): List<Variable>? {
    if (old.any { it.name == name }) {
        XLog.w("变量：新建被拒（已有同名）—— $name")
        return null
    }
    val fresh = Variable.create(name, type, initial)
    if (fresh == null) {
        XLog.w("变量：新建被拒（名字或初始值不合法）—— $name / $type")
        return null
    }
    return saveVars(old + fresh)
}

/** 落盘；失败返回 `null`（写不进去时调用方按「没改」处理）。 */
private fun saveVars(next: List<Variable>): List<Variable>? = if (VariableStore.save(next)) next else null
