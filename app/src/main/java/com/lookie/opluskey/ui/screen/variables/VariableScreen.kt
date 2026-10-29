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

// 「自定义变量」页 —— 管清单的那一页：首页「其他」卡那一行，以及命令里引用变量那几条入口
// （绑定网格那一格 / 命令步骤抽屉那一行 / 条件分支那条动作）都落到这里。
//
// 这一页做三件事：**列出**变量、点一颗**弹窗管它**（改值 / 删除 / 挑一步，见 `VarManageDialog`）、
//  顶栏右上角**新建**。变量的语义与存储全在 `core/config/VariableStore` + `core/model/Variable`：
//  这里一条规则都不重写 —— 包括「值怎么归一」（走 `Vars.assign`）与「名字能不能用」（`VariableName`）。
//
//  **写盘一律走 `VariableStore` 的那几个口子**（`add` / `remove` / `apply`），不在这里拼整表再
//  `save`：页面手里那份清单是**进页那一刻**的快照，而执行侧的 `setvar:` 是现读盘的 —— 拿快照
//  整表写回，就会把「页面开着时按侧键改的值」整表退回去。

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
    //  文件在、但读不出来（截断 / 手改坏）—— **与「还没有变量」分开**：那种状态下列表不能说成
    //  空表（那份空表会被当成写路径的输入），空态也不能写「点右上角 + 新建」
    var unreadable by remember { mutableStateOf(false) }

    /**
     * 新建一颗。落盘走 [VariableStore.add]（读 → 追加 → 写）：读不懂时它自己不写，
     * 所以这一页开着、盘上那份坏了的情况下点新建，也不会把那份覆盖掉。
     */
    fun create(
        name: String,
        type: VarType,
        initial: String,
    ) {
        val fresh = Variable.create(name, type, initial)
        if (fresh == null) {
            XLog.w("变量：新建被拒（名字或初始值不合法）—— $name / $type")
            hint(I18n.get(Strings.VAR_FAILED, "改不了"))
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { VariableStore.add(fresh) }
            if (ok) {
                VarListSignal.bump()
                hint(I18n.get(Strings.VAR_SAVED, "已保存"))
            } else {
                hint(I18n.get(Strings.VAR_FAILED, "改不了"))
            }
        }
    }

    //  **跟着记号重读**：弹窗里改过值 / 删过一颗、或换了一份配置（变量跟着配置走）之后回来，
    //  这一页的组合还在栈里（没注销），不重读就还是改之前那份清单。
    //  首次进页同样走这一支（初值 0）。
    LaunchedEffect(VarListSignal.generation) {
        //  换页时页面级浮窗表会把抽屉收掉（见 `PageEffects` 的 `closeOverlays`）——
        //  「新建」这个开关得跟着回位，否则下次进这一页会看到一个自己弹出来的抽屉。
        VariablePageState.creating = false
        //  三态一起收在这里：读到 / 没写过 / 读不懂。后两者列表都是空的，但**读不懂不算空态**
        //  （见上面的 `unreadable`）—— 那份空表会被当成写路径的输入。
        val l = withContext(Dispatchers.IO) { VariableStore.load() }
        vars = (l as? VariableStore.Load.Ok)?.snapshot?.vars.orEmpty()
        unreadable = l == VariableStore.Load.Unreadable
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
        val fullscreen = loaded && (vars.isEmpty() || unreadable)
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
            if (fullscreen) {
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
                        text =
                            if (unreadable) {
                                I18n.get(Strings.VARIABLES_UNREADABLE, "变量表读不出来，先别在这里新建（那会盖掉盘上那份）")
                            } else {
                                I18n.get(Strings.VARIABLES_EMPTY, "还没有变量，点右上角那颗 + 新建一颗")
                            },
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

    VariableOverlays(onCreate = ::create)
}

/**
 * 这一页那两层浮层：**新建**（抽屉）与**管一颗**（居中弹窗）。
 *
 *  点一颗变量不再进「变量操作」页 —— 那条路（挑一步）留给按键绑定 / 命令步骤 / 条件分支，
 *  这一页面对的永远是「已经有了的这一颗」，弹一张小窗就够了。
 */
@Composable
private fun SettingsPage.VariableOverlays(onCreate: (String, VarType, String) -> Unit) {
    CreateSheet(
        show = VariablePageState.creating,
        onDismiss = { VariablePageState.creating = false },
        onCreate = { name, type, initial ->
            VariablePageState.creating = false
            onCreate(name, type, initial)
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
        VarType.BOOLEAN -> I18n.get(Strings.VAR_TYPE_BOOLEAN, "布尔")
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
