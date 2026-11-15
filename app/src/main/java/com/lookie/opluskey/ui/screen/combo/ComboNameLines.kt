package com.lookie.opluskey.ui.screen.combo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.present.COMBO_ORDER_SEPARATOR
import com.lookie.opluskey.core.present.COMBO_PAIR_SEPARATOR
import com.lookie.opluskey.core.present.comboKeyNames
import com.lookie.opluskey.ui.row.ChipSentence
import com.lookie.opluskey.ui.row.NameFlow
import io.github.suqi8.coui.kmp.basic.Text

// 「拦下这一下的是谁」—— 拦截窗正文里那一串要点名念出来的东西。
//
//  删不掉（挡路的是几档组合）、连带停用（跟着停的是几档组合）、开不了某个开关（挡路的是
//  几档组合）、组合开不起来（还关着的是几颗键）—— 这几张窗原先只在句子里报一个数，
//  用户读完仍不知道自己该去处理哪一条。现在由调用方把**具体那几项**交进来，这里只负责排。

/** 一档一行之间的间距 —— 比段间距小一档，读起来是「同一段里的几个例子」。 */
private val LINE_GAP = 6.dp

/**
 * 几档组合，一档一行、水平居中。
 *
 *  两类写法分开（与组合列表同一套）：等待组合是一串名字（`A × B`），按住组合是一句话
 *  （`按住 A 后再按 B`）—— 只摆一串名字读不出「谁先按住」，而那正是那一档的判据。
 *  有序档的分隔符是「→」（先后），不分顺序是「×」（一起按下），与列表行同一个判据。
 *
 * @param cfg 取键名用（成员键被删掉的那种畸形配置由 `comboKeyNames` 兜底）
 */
@Composable
internal fun ComboNameLines(
    cfg: Config,
    combos: List<ComboKey>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LINE_GAP),
    ) {
        combos.forEach { combo ->
            val names = comboKeyNames(cfg, combo)
            val separator = if (combo.ordered) COMBO_ORDER_SEPARATOR else COMBO_PAIR_SEPARATOR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (combo.isHold) {
                    ChipSentence(
                        template = holdSentenceTemplate(names.size - 1, separator),
                        names = names,
                        style = style,
                        color = color,
                        //  这一句自己撑满整行（它是 `BasicText`），外层那个 `Arrangement.Center`
                        //  只摆得动盒子、摆不动盒子里的字 —— 按住组合那几档得在这里点名居中
                        textAlign = TextAlign.Center,
                    )
                } else {
                    NameFlow(names = names, style = style, color = color, separator = separator)
                }
            }
        }
    }
}

/**
 * 几颗键，一颗一行、水平居中。
 *
 *  与 [ComboNameLines] 并列：那一档报的是「哪几档组合」，这一档报的是「哪几颗键」
 *  （组合开不起来时挡路的是成员键有没有启用，不是哪一档组合）。
 */
@Composable
internal fun KeyNameLines(
    names: List<String>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LINE_GAP),
    ) {
        names.forEach { name ->
            Text(
                text = name,
                style = style.copy(textAlign = TextAlign.Center),
                color = color,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
