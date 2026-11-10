package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.DialogButtonRow
import com.lookie.opluskey.ui.FocusOnShow
import com.lookie.opluskey.ui.FrostedDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.TitleCounter
import com.lookie.opluskey.ui.dialogSideMargin
import com.lookie.opluskey.ui.model.EASING_EMPHASIZED_DECEL
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER_MS
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldDefaults
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 编辑区**最少 / 最多**显示几行。
 *
 *  最少 3 行：这是「随内容变高」的起点 —— 空命令时弹窗就这么高，不再一开就占掉三分之一屏
 *  （原来固定 200dp，用户口径「不默认就拉高，而是随内容变高」）。
 *  最多 12 行：再长就让框自己竖着滚（`BasicTextField` 的 `maxLines` 行为），弹窗高度到此为止，
 *  软键盘弹起来之后仍整个落在可见区域里。
 *  从 8 行放到 12 行（1.5 倍）：512 字的命令在 8 行里要滚着看，放宽到 [MAX_SINGLE_LENGTH] 之后
 *  更需要一次多看几行 —— 12 行 × 约 24dp 加上标题与按钮条约 400dp，再叠一层键盘仍在竖屏内。
 */
private const val EDITOR_MIN_LINES = 3
private const val EDITOR_MAX_LINES = 12

/**
 * 编辑区内部留白。
 *
 *  比库 `Rectangle` 模式那一档（16 × 12）各收 4dp：这块是整片编辑区，
 *  按一行输入框的内缩算，四周空得太多、字显得被挤在中间。
 */
private val EDITOR_INSIDE = DpSize(12.dp, 8.dp)

/**
 * 「命令详情」那一栏的**全屏编辑弹窗** —— 由小标题点开（`SectionTitleClick.Expand`）。
 *
 * ## 它解决什么
 *
 *  页面里那个输入框是**一行**（库的 `InputField` 只有单行）：`shell:` 里带换行、或者一条
 *  带好几个参数的长命令，在那一行里只能左右蹭着看。这里给一块多行、能竖着滚的编辑区。
 *
 * ## 编辑的是一份拷贝
 *
 *  打开时从草稿里取一份，**取消就丢掉、确定才写回**（`commandDraft.draft.single`）——
 *  与页面那个输入框共用同一份草稿，所以写回之后页面那边立刻跟着变。
 *  `remember(show)` 保证每次打开都从草稿现取：上一次改到一半关掉，再打开不该还挂着那半截。
 *
 * ## 多行显示、单行落盘
 *
 *  这一块**能显示多行**（`singleLine = false`），因为它要装下一条很长的命令 —— 页面那一栏只有
 *  一行，长命令在那里只能左右蹭着看。但**落盘的值是单行**：确定时经 `CommandForm.single` 的写入口
 *  折成一行（`singleLine`），换行折成 `; ` 串联。页面那栏是单行输入框，收到带换行的值会**一个字
 *  都看不见**（值还在）—— 折在这一处，两边看到的就是同一个东西。
 *
 *  长度上限仍走 [MAX_SINGLE_LENGTH]：库的 `maxCount` 撤了（计数器挪去标题行），
 *  截断改在 `onValueChange` 里做，计数器与真正写回的值才不会对不上。
 *
 * ## 输入框就交给库
 *
 *  用库的 `TextField`（`Rectangle` 模式）原样摆：占位、聚焦描边、正文的垂直位置都是它那一套
 *  —— 与库自己的输入框一致（真机口径：按库的来，别自己拼）。
 *
 * ## 高度随内容长
 *
 *  行数交给库的 `minLines` / `maxLines`（[EDITOR_MIN_LINES] / [EDITOR_MAX_LINES]），
 *  外面再套一层 `animateContentSize` 把「多一行」补成过渡 —— 敲回车时弹窗跟着长一截，
 *  而不是从头到尾固定一块 200dp 的大白框。
 *
 * @param show 开着没有（原样递给 [FrostedDialog]，退场动画靠它）
 * @param onDismiss 收起（取消 / 点空白 / 返回，三条路都走它）
 */
@Composable
internal fun SettingsPage.CommandFullscreenDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val current = commandDraft.draft.single
    //  初值把光标放在**末尾**：打开是接着改，不是在开头插字。
    var text by remember(show) {
        mutableStateOf(TextFieldValue(current, selection = TextRange(current.length)))
    }
    val focus = remember { FocusRequester() }
    FocusOnShow(show, focus)

    FrostedDialog(
        show = show,
        title = I18n.get(Strings.COMMAND_DETAIL_TITLE, "命令详情"),
        onDismiss = onDismiss,
        //  带输入框：键盘由输入框那一侧叫，这里不能收（收晚一步就是「聚焦后又失焦」）
        hideImeOnShow = false,
        //  字数统计放**标题行行尾**：库的 `maxCount` 是画在输入框右下角的，而这一格是编辑区
        //  （框高随行数变），计数器贴着框底、离正在敲的字很远，读起来像另一条信息。
        //  按钮是**底部那一区**（三段式里竖向留白由弹窗统一给）
        footer = {
            DialogButtonRow(
                onNegative = onDismiss,
                onPositive = {
                    //  折行 / CRLF 归一都归 `CommandForm.single` 的写入口（见类注释）——
                    //  这里交原文即可
                    commandDraft.draft.single = text.text
                    onDismiss()
                },
            )
        },
        titleTrailing = { TitleCounter(text.text.length, MAX_SINGLE_LENGTH) },
    ) {
        //  **侧边距只给输入框**：库的按钮条是「两半平分整个宽度」（自己带内部边距），
        //  套在侧边距里就缩进一圈、与弹窗边缘对不上（`NameInputBody` 也是这么摆的）。
        EditorField(value = text, onValueChange = { text = it }, focus = focus)
    }
}

/**
 * 编辑区本体 —— 一行 `TextField` 的配置太多，单独摆出来才让弹窗那一支留在 detekt 的长度线内。
 *
 *  长度上限在**这一层**兜（库的 `maxCount` 撤了：计数器挪去标题行）：超出的部分直接吃掉，
 *  计数器与真正写回的值才不会对不上；截断时把光标一起夹回末尾，否则选区会落到字数之外。
 */
@Composable
private fun EditorField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focus: FocusRequester,
) {
    TextField(
        value = value,
        onValueChange = { next ->
            val cut = next.text.take(MAX_SINGLE_LENGTH)
            onValueChange(
                if (cut.length == next.text.length) {
                    next
                } else {
                    TextFieldValue(cut, selection = TextRange(cut.length))
                },
            )
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .dialogSideMargin()
                //  高度**随内容变**：行数交给库的 `minLines` / `maxLines` 管，
                //  这一层只把「又长了一行」那一跳补成过渡 —— 与页面里其他
                //  「动态显隐」同一档时长与缓动（`VISIBILITY_ENTER_MS` /
                //  `EASING_EMPHASIZED_DECEL`），敲回车时框不会猛地弹一截。
                .animateContentSize(tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL))
                .focusRequester(focus),
        label = I18n.get(Strings.COMMAND_CMD_HINT, "请输入命令"),
        //  描边圆角矩形：这一块是「一整片编辑区」，下划线那副样子撑不起这块高度
        backgroundMode = TextFieldMode.Rectangle,
        //  **强制换行**（用户口径）：不写明时走平台的 `Unspecified`，这一版 ROM 按「短语」断
        //  —— 一条 `shell:settings put system …` 的命令会因为行尾放不下最后一个 token 而整块提前
        //  折行、右边留一大片。`LineBreak.Simple` 把断行策略钉成 simple、词边界用平台默认（不按短语），
        //  行尾就按字符填满再换。
        //  其余样式仍走库那一份（`textStyle(backgroundMode)`），只 copy 这一项。
        textStyle = TextFieldDefaults.textStyle(TextFieldMode.Rectangle).copy(lineBreak = LineBreak.Simple),
        insideMargin = EDITOR_INSIDE,
        //  **不用主题色**：聚焦时那道描边默认是 `primary`，在这一整片编辑区上太抢；
        //  改成「未聚焦一档淡、聚焦一档稍重」的中性色，聚焦与否仍看得出来。
        colors =
            TextFieldDefaults.textFieldColors(
                borderColor = COUITheme.colorScheme.onSurfaceContainerHigh,
                unfocusedBorderColor = COUITheme.colorScheme.dividerLine,
            ),
        singleLine = false,
        //  行数上下限：空的时候只有 3 行高，敲满 8 行以后框自己滚
        minLines = EDITOR_MIN_LINES,
        maxLines = EDITOR_MAX_LINES,
    )
}
