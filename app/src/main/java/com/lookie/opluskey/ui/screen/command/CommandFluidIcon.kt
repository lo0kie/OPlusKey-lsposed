package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.icon.FluidIconRow
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
 *  行本体与「常用命令」那一段是**同一份**（`ui/model/icon/FluidIconRow`）：形状、副标题、
 *  选择器都只有一处实现，这里只负责「写回正文」，那里负责「写回清单」。
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
    //  小标题与卡片一起进退（`AnimatedColumn` 里面自带 `Column`，两行不会叠在一处）
    AnimatedColumn(visible = isFluid) {
        SectionTitle(I18n.get(Strings.COMMAND_FLUID_ICON_TITLE, "图标"))
        FluidIconRow(
            //  显隐已由外面那一档管着，这一行只在本档在场时才组合 ⇒ 恒为显示
            show = true,
            enabled = enabled,
            //  只换图标那一段，正文一个字不动
            onPick = { id -> form.single = Action.Fluid(message, id).token },
        )
    }
}
