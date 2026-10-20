package com.lookie.opluskey.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.EASING_EMPHASIZED_DECEL
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER_MS
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 一行长文本的**折行展开 / 收起** —— 收起时压成一行（末尾省略号），展开后原样折行。
 *
 * ## 它解决的是「折行」不是「多段」
 *
 *  值本身是**一段**文字（指纹这种），只是太长、在界面上会折成两三行，把整张卡的行高撑得不齐。
 *  所以收起时给 `maxLines = 1` + 省略号，展开时放开 —— 展开后与不折叠时**一模一样**：
 *  排版仍是同一个 `Text`（同样的字号、颜色、左边界、行距），只是 `maxLines` 不同。
 *
 *  早先两版都错在这里：先是另画一块子行（左对齐与行距都对不上），后又按行拆成 `List<String>`
 *  （把「一段折行」当成了「多段内容」）。
 *
 * ## 行尾那颗「展开 / 收起」
 *
 *  它只是**指示**，不是按钮：点击一律交给整行（`BasicComponent` 的 `onClick`）。
 *  早先给它单独挂了 `clickable`，结果是「点箭头才灵、点同一行的文字反应不一样」——
 *  同一件事在一行里出现两种命中区，用户没法预期。
 *
 *  只有**真的折了行**才画这颗箭头：一行放得下时「展开」点了也没有变化，等于骗人。
 *  判据是收起状态下那次排版有没有溢出（`TextLayoutResult.hasVisualOverflow`），
 *  量一次就够，不必自己算宽度。
 */
@Composable
internal fun SettingsPage.ExpandableRow(
    spec: ExpandableRowSpec,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val tick = rememberTick()
    // 收起时那一次排版有没有溢出 —— 只有溢出（也就是真的折行了）才值得给「展开」
    var overflowed by remember(spec.text) { mutableStateOf(false) }
    val toggle = {
        tick()
        onToggle()
    }
    BasicComponent(
        onClick = toggle,
        startAction = { Icon(spec.icon, contentDescription = null, tint = spec.iconTint) },
        endActions =
            if (expanded || overflowed) {
                {
                    // 箭头 + 旋转：收起时朝下、展开时转 180° 朝上，转的过程与高度动画同一档。
                    //  **不单独挂点击**：整行就是命中区（见类注释）。
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL),
                        label = "ExpandArrow",
                    )
                    Icon(
                        PageIcons.StepDown,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .rotate(rotation)
                                .size(ROW_END_ICON_SIZE),
                        // 与全页其他「行尾图标」同一档色（不是强调色）：它只是个指示，不是动作项
                        tint = COUITheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            } else {
                null
            },
        cardListPosition = spec.position,
    ) {
        // 高度自己补动画：`maxLines` 一变，这一块就从一行跳到三行 —— 与页面里其他「动态显隐」
        //  同一档时长与缓动（`VISIBILITY_ENTER_MS` / `EASING_EMPHASIZED_DECEL`），切换才不突兀。
        Column(
            Modifier.animateContentSize(tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL)),
        ) {
            // 标题与值自己画：库那个 `BasicComponent(title, summary)` 版本不给 `maxLines`，
            //  而「收起成一行」正是这里唯一要做的事。样式逐项照抄库那一版。
            Text(
                text = spec.title,
                style = COUITheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                // 与滑杆那一档同一套：标题取 `onBackground`（库的 `titleColor()` 是 internal，取不到）
                color = COUITheme.colorScheme.onBackground,
            )
            Text(
                text = spec.text,
                modifier = Modifier.padding(top = TITLE_SUMMARY_GAP),
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                // 收起压成一行、展开原样折行 —— 这是这一档唯一的行为差别
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    // 只在收起态量：展开态本来就全部显示，溢出恒为 false
                    if (!expanded) overflowed = result.hasVisualOverflow
                },
            )
        }
    }
}

/** 标题与值之间那一档行距 —— 与库 `BasicComponent` 里 `MarginBetweenLine` 同值（2dp）。 */
private val TITLE_SUMMARY_GAP = 2.dp
