package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.icon.FluidIconPicker
import com.lookie.opluskey.ui.model.icon.FluidIcons
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.state.page.CommandForm
import com.lookie.opluskey.ui.style.AnimatedColumn

/**
 * 单条命令的「图标」那一行 —— 只在正文是 `fluid:` 时出现。
 *
 *  点开是网格选择器（`FluidIconPicker`）：模块在用的那批 Tabler 字形全在里面，点一颗就把它
 *  渲染成 png 存进宿主的共享目录，同时把命令改成 `fluid:<id>|正文`。
 *  命令里只记 id，执行时按 id 找文件（`FluidCard.nameOf`），所以换图标只重渲染点中的那一颗。
 *
 *  **副标题写「一共有几颗」，不写「现在用的是哪一颗」**：后者没法用一行字说清（把 id 念成
 *  `click` 对谁都没帮助），想知道当前是哪颗，点开窗看一眼网格就够；前者是「这一格后面还有
 *  多少东西」的量，用户据此决定值不值得点进去。
 *
 *  显隐走**统一那一档**（`AnimatedColumn` = 淡入 + 纵向展开，顶边缘推开）：正文从 `fluid:`
 *  改成别的（或反过来）时这一档不该「啪」地出现 / 消失 —— 它夹在「可用变量」与「命令设置」
 *  之间，硬切会把下面那一大块整体顶一下。
 */
@Composable
internal fun SettingsPage.CommandFluidIconCard(
    form: CommandForm,
    enabled: Boolean,
) {
    //  只认前缀，不认 `parse` 的结果：正文写成 `fluid:` 就该有这一行 —— 哪怕后面还没写字、
    //  或者写了什么让 `parse` 判成别的，也不该让这一档忽隐忽现。
    val text = form.single.trim()
    val isFluid = text.startsWith(Action.FLUID_PREFIX)
    val fluid = remember(text) { Action.parse(text) as? Action.Fluid }
    val message = fluid?.message ?: text.removePrefix(Action.FLUID_PREFIX).trim()
    var picking by remember { mutableStateOf(false) }
    //  小标题与卡片一起进退（`AnimatedColumn` 里面自带 `Column`，两行不会叠在一处）
    AnimatedColumn(visible = isFluid) {
        SectionTitle(I18n.get(Strings.COMMAND_FLUID_ICON_TITLE, "图标"))
        CardList(
            rows =
                listOf(
                    CardRow.Item(
                        title = I18n.get(Strings.COMMAND_FLUID_ICON_TITLE, "图标"),
                        //  副标题讲**这一格后面有多少东西**（`当前有 48 个图标`）——
                        //  它是「值不值得点进去」的量，而这一行原先不写副标题：那时小字讲的是
                        //  「现在用的是哪一颗」，把 id 念成 `click` 对谁都没帮助。
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
    }
    //  选择器留在动画外面：`AnimatedVisibility` 会裁掉超出自身的内容，
    //  弹窗跟着那一档一起进出的话，退场那 200ms 里会被切掉一截。
    //
    //  **不要写成 `if (picking) { … }`**：一写整棵子树消失，退场就成了硬切（同 `PageSheet` 那条）。
    //  壳自己按 `show` 播进出场，这里原样递进去。
    FluidIconPicker(
        show = picking,
        onPick = { id ->
            // 只换图标那一段，正文一个字不动
            form.single = Action.Fluid(message, id).token
            picking = false
        },
        onDismiss = { picking = false },
    )
}
