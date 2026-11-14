package com.lookie.opluskey.ui.model.icon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow

/**
 * 「图标」那一行 —— **凡是某条东西（命令 / 常用命令）能带 `<id>|正文` 这种写法的地方，都画这一行**。
 *
 *  它只做两件事：点开网格选择器（[FluidIconPicker]）、把选中的 id 交回调用方。
 *  怎么把 id 写回去由调用方定（命令改 `form.single`，常用命令改清单里那一条），因为两处的落点
 *  不是同一个对象 —— 但**入口的形状、副标题、选择器**必须只有一份，否则同一次点选用两种说法。
 *
 *  **这一行不显示「现在用的是哪颗」**：选择器那一层本来就没有「当前选中」这一档
 *  （点一颗就是换一颗，关掉窗之后命令里那个 id 才是答案），入口行再摆一次当前值只是重复；
 *  副标题说的是「后面还有多少颗可选」，那是「值不值得点进去」的量。
 *
 * ## 为什么只出现在流体云上
 *
 *  这套图标是**胶囊左边那一格**的东西（`fluid:` 的 `logo`，见 `core/model/FluidCard`）：
 *  字形被渲染成 png 存进宿主的共享目录，执行时按 id 组出 `content://`。别的动作没有这个槽，
 *  给了也画不上去 —— 所以「显不显示这一行」由调用方按动作种类决定。
 *
 * @param show 这一行露不露 —— 交给 `CardList` 播进出场，调用方别在外面 `if`
 * @param enabled 整页闸关着时点不动
 * @param onPick 点中一颗：调用方把它写回去
 */
@Composable
internal fun SettingsPage.FluidIconRow(
    show: Boolean,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    visible = show,
                    title = I18n.get(Strings.COMMAND_FLUID_ICON_TITLE, "图标"),
                    //  副标题讲**这一格后面有多少东西**（`当前有 48 个图标`）——它是「值不值得点进去」
                    //  的量，而不是「现在用的是哪一颗」：把 id 念成 `click` 对谁都没帮助，
                    //  想知道当前是哪颗，点开窗看一眼网格就够。
                    summary = I18n.format(Strings.COMMAND_FLUID_ICON_COUNT, FluidIcons.ALL.size),
                    //  行首是「图」本身（`photo`）—— 原先借的是那颗「点一下」的指针，
                    //  它讲的是手势、不是图。
                    icon = PageIcons.FluidIcon,
                    //  这一行也是「点进去还有一层」，与别处进下一层的行同一个记号。
                    //  画与颜色都由 `CardList` 管（按 enabled），调用点不再自己配一份。
                    arrow = true,
                    enabled = enabled,
                    onClick = { picking = true },
                ),
            ),
    )
    //  选择器**不**写成 `if (picking) { … }`：一写整棵子树消失，退场就成了硬切（同 `PageSheet` 那条）。
    //  壳自己按 `show` 播进出场，这里原样递进去。
    FluidIconPicker(
        show = picking,
        onPick = { id ->
            picking = false
            onPick(id)
        },
        onDismiss = { picking = false },
    )
}
