package com.lookie.opluskey.ui.screen.home

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.seal.Seal
import com.lookie.opluskey.ui.FrostedDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialogSideMargin
import com.lookie.opluskey.ui.model.DONATE_CODE_SIZE
import com.lookie.opluskey.ui.model.DonateTarget
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 「捐赠打赏」：菜单里的三个去处 + 点开之后那个居中的收款码窗口。

/**
 * 「捐赠打赏」的收款码窗口 —— **居中**的对话框，与全项目其他窗同一套皮。
 *
 *  走 [FrostedDialog] 而不是自己搭一层 `OverlayDialog`：那是全项目唯一的面板（磨砂 + 三区排版）。
 *  自己搭的那一版面板不磨砂、区距自定，同一页里它和确认窗一眼看上去像两个应用的控件。
 *  居中由 [FrostedDialog] 的 `centered` 传下去（库在窗口宽度不到 840dp 时默认是**底部滑入**）。
 *
 *  **窗口只有标题，正文全在内容槽里**：那两句话（[Strings.DONATE_HINT] 与
 *  [Strings.DONATE_NAME_NOTE]）要在**图下面**，而 `summary` 槽在库里固定排在内容**之上**
 *  （见 `DialogContentLayout` 的 Column），所以不喂 `summary`，两句话归 [DonateBody] 摆。
 *
 * @param target 选中的去处；`null` = 收起来
 * @param onDismiss 收窗口（点遮罩 / 返回手势 / 页面离开都会走到这里）
 */
@Composable
internal fun SettingsPage.DonateDialog(
    target: DonateTarget?,
    onDismiss: () -> Unit,
) {
    val show = target != null
    // 页面离开 / 换页时跟着一起收（与确认窗、浮层菜单同一条纪律，见 [SettingsPage.closeOverlays]）
    OverlayCloser(show = show, onDismiss = onDismiss)
    //  退场动画期间 `show` 已经是 false，但面板还要再画一次 —— 内容不能跟着清空，否则那一帧是个
    //  空面板（与浮层菜单同一条账，见 [MenuPopup]）。普通对象、**不是 state**：写它不该触发重组，
    //  它只是「上一次那一份」这个事实。
    val last = remember { LastDonate() }
    if (target != null) last.value = target
    FrostedDialog(
        show = show,
        //  标题是必填的（[FrostedDialog] 那一栏不是可空槽）：没选中时窗口本来就不露出，
        //  空串只是占位，退场那一帧仍有 `last` 里的上一个去处。
        title = last.value?.label.orEmpty(),
        onDismiss = onDismiss,
        // 强制居中：库默认按窗口尺寸自动判，手机上一律是底部滑入
        centered = true,
    ) {
        DonateBody(last.value)
    }
}

/** 退场期间要画的那一份（见 [DonateDialog]）。 */
private class LastDonate {
    var value: DonateTarget? = null
}

/** 图与它下面那几行字之间的空 —— 18dp（库里摘要在图上方时，自己带的就是上下 18dp）。 */
private val DONATE_CODE_TEXT_GAP = 18.dp

/** 图下面那两行字之间的空 —— 比行距略大一点，读得出「这是两句」，又不至于散成两块。 */
private val DONATE_NOTE_GAP = 6.dp

/**
 * 内容槽里那一叠：**图在上、话在下**。
 *
 *  正文那两句话（[Strings.DONATE_HINT] 与 [Strings.DONATE_NAME_NOTE]）要在**图下面**，
 *  而库的 `summary` 槽固定排在内容之上，所以不喂 `summary`，两句话归这里摆。
 *
 *  **上下留白不在这里补**：那归 `FrostedDialog` 的三区排版（头 / 正文 / 底之间统一一档），
 *  各补一份正是「gap 一叠加、上下对不齐」的来源。
 */
@Composable
private fun DonateBody(target: DonateTarget?) {
    if (target == null) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DonateCode(target)
        Spacer(Modifier.height(DONATE_CODE_TEXT_GAP))
        DonateNote(I18n.get(Strings.DONATE_HINT, "您的慷慨将帮助此项目持续发展"))
        Spacer(Modifier.height(DONATE_NOTE_GAP))
        DonateNote(
            I18n.get(
                Strings.DONATE_NAME_NOTE,
                "备注昵称即可加入捐赠列表，不备注按匿名标注",
            ),
        )
    }
}

/**
 * 窗口里那张图：`assets/donate/<id>.webp`；**不在包里**时退成品牌字形。
 *
 *  退成字形而不是报错，是为了让这个窗口在收款码还没放进包时也**不像坏了**；
 *  同时日志里留一句 `w`（判据行：这一档的图没进包）。
 */
@Composable
private fun DonateCode(target: DonateTarget) {
    val context = LocalContext.current
    // 一次用户动作触发一次的读图（不是列表里逐行渲染）。
    //  **但必须放 IO**：`LaunchedEffect` 的默认调度器是主线程，而这里要做的是「读 assets +
    //  解一张 900×900 的 WebP」—— 同步读会把开窗那一帧整个压住（首帧是一块空白面板）。
    //  键带 target：换一个去处就重新取那一张。
    var bitmap by remember(target) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(target) { bitmap = withContext(Dispatchers.IO) { loadDonateCode(context, target) } }
    val code = bitmap
    //  水平居中必须自己做：`FrostedDialog` 的 content 槽是个 `ColumnScope`（默认
    //  `horizontalAlignment = Start`），比面板窄的图会**贴左**。
    //  撑满一行再让内容居中，是这一槽里唯一稳的写法 —— 只给图加 `wrapContentWidth` 没用：
    //  面板宽度由内容自己撑时那个节点就只有图的宽度，居中是空操作。
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (code != null) {
            Image(
                bitmap = code,
                contentDescription = null,
                modifier = Modifier.size(DONATE_CODE_SIZE),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(target.icon, contentDescription = null, modifier = Modifier.size(DONATE_CODE_SIZE))
        }
    }
}

/**
 * 图下面那一行说明 —— 待遇与库里那个 `summary` 槽一致：左右按 [dialogSideMargin]、整行居中。
 *
 *  颜色也照 `summary` 走（`DialogDefaults.summaryColor` = `onBackground`）：这两句是窗口的正文，
 *  不是次级备注，压成灰的会读成「可以不看」。
 */
@Composable
private fun DonateNote(text: String) {
    Text(
        text = text,
        modifier =
            Modifier
                .fillMaxWidth()
                .dialogSideMargin(),
        style = COUITheme.textStyles.body2.copy(color = COUITheme.colorScheme.onBackground),
        textAlign = TextAlign.Center,
    )
}

/**
 * 读 `assets/donate/<id>.webp`。
 *
 *  读法走 `core/seal` 的 [Seal]：**release 包里这张图是封着的**（明文不落地，见那边的说明），
 *  debug 才直接读明文。`context.assets` 不能碰 —— 那是**宿主** APK 的资源
 *  （与字典同一件事，见 `core/i18n/DictionaryLoader`）。
 *
 *  **是 WebP、不是 PNG**：三张都是 900×900。
 *
 *  为什么**不**顺手把分辨率也降下去：这张图在屏上按 [DONATE_CODE_SIZE]（220dp）画，而在密度
 *  560dpi 的机器上那就是 **770 物理像素** —— 降到 600×600 会被**放大 1.28 倍**显示，而这一页
 *  存在的唯一理由就是「扫得动」，清晰度不该为二十几 KB 让步。
 *
 *  体积靠**图本身**省，不靠降分辨率：图里的亮度只有黑白两阶、色度只保留中间那块头像，
 *  再按**无损** WebP 存 —— 三张合计约 30 KiB。二维码载荷与校验时用的那份逐字相同：
 *  离线拿解码器核过，还过了「屏上 770px 绘制 → 相机 1.0 / 0.5 / 0.35 倍采样 →
 *  高斯模糊 + 噪声」这一档劣化模拟。
 *
 *  解码仍走 [BitmapFactory.decodeByteArray]：它按**内容**认格式，平台自带 WebP 解码器。
 *
 *  读不到返回 `null`（调用方退成品牌字形），判据由 [Seal] 那边留。
 */
private fun loadDonateCode(
    context: Context,
    target: DonateTarget,
): ImageBitmap? {
    val bytes = Seal.open(context, "donate/${target.id}.webp") ?: return null
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    if (decoded == null) XLog.w("捐赠：assets/donate/${target.id}.webp 解不成位图（退成品牌字形）")
    return decoded?.asImageBitmap()
}
