package com.lookie.opluskey.ui.screen.command

import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.host.action.shell.RootGrant
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.DialogButtonRow
import com.lookie.opluskey.ui.dialog.FocusOnShow
import com.lookie.opluskey.ui.dialog.FrostedDialog
import com.lookie.opluskey.ui.dialog.dialogSideMargin
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMMAND
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.input.ImeFocusRelease
import com.lookie.opluskey.ui.model.theme.EASING_EMPHASIZED_DECEL
import com.lookie.opluskey.ui.model.theme.VISIBILITY_ENTER_MS
import com.lookie.opluskey.ui.row.TitleCounter
import com.lookie.opluskey.ui.row.codePointLength
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.write.branchKeyNow
import com.lookie.opluskey.ui.write.write
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
        title = I18n.get(Strings.COMMAND_DETAIL_TITLE),
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
        titleTrailing = { TitleCounter(codePointLength(text.text), MAX_SINGLE_LENGTH) },
    ) {
        //  **侧边距只给输入框**：库的按钮条是「两半平分整个宽度」（自己带内部边距），
        //  套在侧边距里就缩进一圈、与弹窗边缘对不上（`NameInputBody` 也是这么摆的）。
        EditorField(value = text, onValueChange = { text = it }, focus = focus)
    }
}

/**
 * 编辑区本体 —— 一行 `TextField` 的配置太多，单独摆出来读着顺。
 *
 *  长度上限在**这一层**兜（库的 `maxCount` 撤了：计数器挪去标题行）：超出的部分直接吃掉，
 *  计数器与真正写回的值才不会对不上；截断时把光标一起夹回末尾，否则选区会落到字数之外。
 *
 *  **键盘收走就失焦**（[ImeFocusRelease]）：这一块是全屏编辑区，收起键盘之后框里还留着光标与
 *  聚焦描边，看着像「还在打字」—— 与页面里那些输入栏同一条口径。
 */
@Composable
private fun EditorField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focus: FocusRequester,
) {
    //  这一块此刻有没有焦点 —— 「键盘收走就失焦」的判据
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    ImeFocusRelease(focused = focused) { focusManager.clearFocus() }
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
                .focusRequester(focus)
                .onFocusChanged { focused = it.hasFocus },
        label = I18n.get(Strings.COMMAND_CMD_HINT),
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

/**
 * 打开「以 root 执行」时**当场申请一次 root 授权**（「命令设置」那一行）。
 *
 *  为什么不等真正执行：`su` 的授权框是 root 管理器在**第一次执行**时弹的，而那要等到用户去按侧键
 *  —— 授权框会在他看不到前因后果的时刻跳出来（按一下侧键，屏幕上多一个陌生的框）。既然他刚在
 *  这一行说过「这条要 root」，授权就该发生在此刻。
 *
 *  判据与真执行同一条（[RootGrant.request] 走的就是 `ShellRunner.exec`）：这里报「已拿到」，按侧键
 *  时也一定拿得到；这里报没授权，按下去也是同一句失败提示。
 *
 *  开关**关掉**时不申请任何东西 —— 那一档只用 `sh`，没有要 root 的地方。
 */
internal fun SettingsPage.requestRootGrant() {
    // 提示条是 Compose 状态（`HintQueue.push` 直接写 `mutableStateOf`），而结果回在 shell 线程上
    // ⇒ 回主线程再报。
    val main = Handler(Looper.getMainLooper())
    try {
        RootGrant.request { failure ->
            main.post {
                hint(
                    // 失败那一侧直接用 `CommandFailure` 表里那句：与按侧键执行失败时一字不差，
                    // 不另写一份「没授权」的说法（同一件事只有一处文案）。
                    failure?.hint ?: I18n.get(Strings.COMMAND_ROOT_GRANTED),
                    slot = HINT_SLOT_COMMAND,
                )
            }
        }
    } catch (e: Throwable) {
        // 页面动作绝不外抛（与 `testCommand` 同一条纪律）：抛出去会落到 Compose 的点击处理里
        XLog.w("申请 root 失败（已忽略）：${e.message}")
    }
}

// 「这张场景表往哪儿写」的两条路 —— 整表（「系统场景」页那颗「确认」）与单块（「场景动作」页那颗）。
//
//  两条路共用同一段落盘（[writeScenes]）：找「在编的那一份」、换掉它的 `scenes`、写盘、打日志。
//  差别在两处**写哪张表、写表里的哪些字段**：整表写的是屏幕上那份草稿（条件 + 三档）；
//  单块写的是配置那份表里的一格，且只换那**动作那几格**（[saveSceneBlock]）——
//  条件与停用位归「系统场景」页，那一页没按「确认」就不该落盘。

/** 单块落盘的三种结果（见 [saveSceneBlock]）。 */
internal enum class SceneBlockSave {
    /** 写成了 —— 草稿快照已经跟上，调用方可以退页。 */
    Saved,

    /** 没写：这一块还没有配置槽位，或配置在别处变过。改动留在草稿里，上一页那颗「确认」会写。 */
    DraftOnly,

    /** 写盘失败（什么都没变）—— 调用方**不要**退页。 */
    Failed,
}

/**
 * 把 [scenes] 写进「在编的那一份」（这颗键 / 这一档组合键）—— 两条落盘路唯一共用的那一段。
 *
 *  别的键 / 别的组合键一个字节都不动，那一段仍按原样带过去。
 *
 * @return 调用方只在 [CommandsSave.Failed] 时按「什么都没变」处理（广播失败时盘上已经是新样子）
 */
internal fun SettingsPage.writeScenes(scenes: List<SceneRule>): CommandsSave {
    val cfg = configState.config.value
    val comboId = branchDraft.comboId
    if (comboId != null) {
        val combo = cfg.combos.firstOrNull { it.id == comboId }
        if (combo == null) {
            //  在别处把这一组删了：不能再往一个不存在的 id 上写（那等于凭空造一档组合键）
            XLog.w("系统场景：组合键 $comboId 已经不在了，本次不保存")
            return CommandsSave.Failed
        }
        val label = comboLabel(cfg, combo)
        val next = cfg.copy(combos = cfg.combos.map { if (it.id != comboId) it else it.copy(scenes = scenes) })
        val result = write(next, "系统场景：$label（${scenes.size} 块）")
        if (result != CommandsSave.Failed) XLog.w("系统场景已保存：$label ${scenes.size} 块")
        return result
    }
    val key = branchKeyNow()
    val next =
        cfg.copy(
            keys =
                cfg.keys.mapValues { (id, keyConfig) ->
                    if (id != key) keyConfig else keyConfig.copy(scenes = scenes)
                },
        )
    val result = write(next, "系统场景：${key.label}（${scenes.size} 块）")
    if (result != CommandsSave.Failed) XLog.w("系统场景已保存：${key.label} ${scenes.size} 块")
    return result
}

/**
 * 配置里「在编的那一份」现在的场景表（`null` = 那一份已经不在了）。
 *
 *  收成**纯函数**而不是 `SettingsPage` 扩展：它只读配置，唯一调用点就是下面那颗「确认」的落盘口，
 *  不需要页面上的任何东西。页面扩展成员数是棘轮判据 3（`tools/ratchet.txt`），
 *  凡是「拿得到 cfg 就能算」的都不该再往那个计数器上加一格。
 */
private fun scenesNowOf(
    cfg: Config,
    comboId: String?,
    key: KeyId,
): List<SceneRule>? =
    if (comboId != null) {
        cfg.combos.firstOrNull { it.id == comboId }?.scenes
    } else {
        cfg.key(key).scenes
    }

/**
 * 只把草稿里第 [at] 块的**动作那几格**写回配置 —— 「场景动作」页那颗「确认」的唯一落盘口。
 *
 * 「动作那几格」= 三档手势 + 松手那一族（`releaseOn` / `release`）：它们回答的都是「这一档做什么」。
 *
 * ## 为什么不整表写回
 *
 *  「场景动作」页与「系统场景」页是**两件独立的事**（这一页改「这一档做什么」，上一页改
 *  「这一块在什么时候生效」）。整表写回会把上一页还没确认的条件改动一起落盘 —— 等于替用户
 *  按了那颗「确认」，那正是这一页不该做的事。
 *
 * ## 也不整块写回
 *
 *  只换一格还不够：草稿里那一块是**同一个 `SceneRule`**，上一页改了还没确认的 `conditions` /
 *  `enabled` 就挂在它身上。整块写下去等于把「这一块在什么时候生效」也一并确认了 —— 连
 *  「条件还没选完」那种半成品都会被写进配置（上一页那颗「确认」本来会拦住它）。
 *
 *  所以写下去的那一块是「**配置里的条件 + 草稿里的动作那几格**」：这一页只认动作，另外两个字段
 *  原样取配置那份。条件改动仍留在草稿里，退回上一页那颗「确认」还亮着，由它自己决定什么时候写。
 *
 * ## 凭什么能只写一块
 *
 *  草稿每一行记着自己在配置里的**槽位**（`BranchDraft.slots`，载入时按位置对齐、加块时为
 *  `null`），删块 / 挪块都跟着行自己走。所以「草稿第 N 块」能落到「配置第 M 格」，而不用
 *  按内容去猜对应关系（`scenesToWrite` 那条注释说的就是为什么不能猜）。
 *
 *  两道闸，过不去就**不写**（改动留在草稿里，上一页那颗「确认」照样能整表写下去）：
 *   · 这一块是新加的（没有槽位）—— 配置里还没有它的位置；
 *   · 槽位上那一块已经不是载入时那一块 —— 配置在别处变过（导入 / 别的入口写过），
 *     这时按旧下标写会把别的一块盖掉。
 */
internal fun SettingsPage.saveSceneBlock(at: Int): SceneBlockSave {
    val draft = branchDraftNow()
    val block = draft.scenes.getOrNull(at) ?: return SceneBlockSave.DraftOnly
    val slot = draft.slotOf(at)
    val base = scenesNowOf(configState.config.value, branchDraft.comboId, branchKeyNow())
    val write = blockWrite(base, slot, draft.loadedAt(slot), block)
    return when {
        write == null -> {
            XLog.w("场景动作：第 ${at + 1} 块没有可写的配置槽位，本次只留在草稿里")
            SceneBlockSave.DraftOnly
        }

        writeScenes(write.scenes) == CommandsSave.Failed -> {
            SceneBlockSave.Failed
        }

        else -> {
            //  快照换成**写下去的那一块**（不是草稿那一块）：条件改动没跟着走，这一块就还是脏的
            draft.noteBlockSaved(at, write.block)
            XLog.w("场景动作：第 ${at + 1} 块的动作已写回配置")
            SceneBlockSave.Saved
        }
    }
}

/** 单块写回要落下去的那一份：写进配置的那一块 + 换掉这一格之后的整表。 */
private class BlockWrite(
    val block: SceneRule,
    val scenes: List<SceneRule>,
)

/**
 * 算出「配置里那一格 + 草稿里那一块的动作那几格」—— [saveSceneBlock] 说的两道闸过不去就答 `null`。
 *
 * @param loaded 载入时那一格的样子，用来判断配置有没有在别处变过
 */
private fun blockWrite(
    base: List<SceneRule>?,
    slot: Int?,
    loaded: SceneRule?,
    block: SceneRule,
): BlockWrite? {
    if (slot == null || base == null) return null
    if (slot !in base.indices) return null
    val old = base[slot]
    if (old != loaded) return null
    //  这一页的东西：各档动作 + **两族松手**（长按的 `release*` 与「单击后长按」的 `chainRelease*`）
    //  + 它们共用的「沿用按下时的场景」；`conditions` / `enabled` 归上一页，原样留着。
    //
    //  `chainRelease*` 原先漏在这里：那一页能编它、草稿里也改了，写回时却没带上 ——
    //  点「确认」之后那一档悄悄回到旧值。松手沿用那一格同理，一并写。
    val written =
        old.copy(
            actions = block.actions,
            releaseOn = block.releaseOn,
            release = block.release,
            chainReleaseOn = block.chainReleaseOn,
            chainRelease = block.chainRelease,
        )
    return BlockWrite(written, base.toMutableList().also { it[slot] = written })
}
