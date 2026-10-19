package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.AnimatedFade
import com.lookie.opluskey.ui.DrawablePainter
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.APP_KIND_MODULE_DARK
import com.lookie.opluskey.ui.model.APP_KIND_MODULE_LIGHT
import com.lookie.opluskey.ui.model.APP_KIND_TAG_BG_ALPHA
import com.lookie.opluskey.ui.model.APP_KIND_TAG_INSIDE
import com.lookie.opluskey.ui.model.APP_KIND_TAG_RADIUS
import com.lookie.opluskey.ui.model.APP_KIND_TAG_SINK
import com.lookie.opluskey.ui.model.APP_STATE_ICON_SIZE
import com.lookie.opluskey.ui.model.APP_STATE_TEXT_GAP
import com.lookie.opluskey.ui.model.CLONE_TAG_DARK
import com.lookie.opluskey.ui.model.CLONE_TAG_LIGHT
import com.lookie.opluskey.ui.model.LaunchableApp
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.opticalTextCenter
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 一条应用项的行首图标 —— 把别的应用的 `Drawable` 转成 `ImageBitmap` 才能给 Compose 的
 * `Image` 用（工程里没有 `rememberDrawablePainter` 那类桥）。
 *
 * 图标**按需取**：查列表时只记包名（见 [LaunchableApp]），真滚进视口的项才在这里问一次
 * PackageManager，取到的 `Drawable` 记进 `AppPickerState` 的图标缓存 —— 滚回去时命中缓存，不再跨进程问第二轮；
 * 没拿到之前这一格先空着（一屏十来行，取图标是毫秒级）。
 *
 *  光栅化（`createBitmap` + `draw`）只在每项自己的组合里做，两百个应用不会在首帧一次合成两百张位图。
 *
 *  取回来的图进**这一行自己的状态**（下面那个 `icon`）：到位只重组这一行。发一份全局版本号去通知
 *  所有行「我这张到了」是错的 —— 一屏十来行依次到位就是十来次全表重组，而别的行除了重跑一遍查表
 *  什么也没得到。版本号只留「清缓存」那一档（见 `AppPickerState.iconsGeneration`）。
 *
 *  位图的 `remember` 键带 [icon] 而不只带包名：清过缓存之后同一个包名会取到新一份 `Drawable`，
 *  键里不带它就重画不出来。
 *
 *  图标换了（换主题 / 图标包）由 `AppPickerState.clearIcons` 跟着清单一起失效，**已经在视口里的那几行也算**
 *  —— 版本号一动，下面那个 `remember` 就重新取值（缓存这时是空的 ⇒ 重新取）。
 */
@Composable
internal fun SettingsPage.AppIcon(app: LaunchableApp) {
    val packageName = app.packageName
    //  缓存键带 user：主用户与分身**同包名**，只按包名存会把主用户那份图发给分身那一行
    val iconKey = "${app.userId}@$packageName"
    // 读一次版本号 = 订阅「清缓存」（理由见 `AppPickerState.iconsGeneration`）
    val generation = picker.iconsGeneration.intValue
    // 这一行自己那份图：初值先看缓存（滚回来那一轮就命中）。
    //  **key 里只有包名，没有 `generation`**：清缓存（`generation +1`）只该让下面那个 effect
    //  重新取一张来覆盖，不该把已经在屏幕上的这张图丢掉 —— 带上 `generation` 就会在每次重查
    //  清单之后把整屏图标重置回空白再重新填，看起来就是「进页后图标闪一下」。
    var icon by remember(iconKey) { mutableStateOf(picker.cachedIcon(iconKey)) }
    // 「这一行问过一轮了没有」—— 决定那张通用字形到底该不该出现。
    //  缓存命中就算问过（`icon != null`）；否则要等这一轮 IO 回来才算。
    var resolved by remember(iconKey) { mutableStateOf(icon != null) }
    // 键里带 `generation`：清过缓存就重取一张**覆盖**（换图标包 / 换主题后能换过来），
    //  但在新图到手之前，屏幕上那张旧图留着不动。
    LaunchedEffect(iconKey, generation) {
        // 缓存里已有就直接用，不再跨进程问
        picker.cachedIcon(iconKey)?.let {
            icon = it
            resolved = true
            return@LaunchedEffect
        }
        val loaded = loadIconOnce(packageName)
        // 取不到也要记「问过了」：下面据此决定画通用字形还是留白
        if (loaded != null) {
            // 写进缓存是为了「滚回去不再跨进程问第二轮」，写进自己这一份是为了当场画出来
            picker.putIcon(iconKey, loaded)
            icon = loaded
        }
        resolved = true
    }
    // 直接把 Drawable 交给 painter 画（见 [DrawablePainter]）：不分配中间位图。
    //  早先是 createBitmap + Canvas + asImageBitmap —— 每滑过一个应用就多一张 48dp 的
    //  Bitmap 和一次整块拷贝，几百项时内存与 GC 跟着滚动抖。
    val painter = remember(icon) { icon?.let { DrawablePainter(it) } }
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            // `fillMaxHeight` 把高度钉在内容区整高上（行高见 [APP_ROW_HEIGHT]），`aspectRatio(1f)`
            // 再按高度算宽度。顺序不能反：先 `aspectRatio` 会拿宽度当基准。
            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
        )
    } else if (!resolved) {
        // **还在取**：留一块同尺寸的空白，不要画通用字形。
        //  否则每一行都会走「通用字形 → 真图标」那一下跳变，进页/滚动时满屏都在闪
        //  （真机反馈：「选择应用的图标进去后会闪一下」）。
        Spacer(Modifier.fillMaxHeight().aspectRatio(1f))
    } else {
        // 问过一轮、确实没有（包已删 / 取图标抛异常）—— 这时才退回通用「应用」字形
        Icon(PageIcons.MenuStock, null, Modifier.fillMaxHeight().aspectRatio(1f))
    }
}

/**
 * 取一次应用图标 —— **跨进程**，只能在 IO 上跑；取不到返回 `null` 并记一行 `d`。
 *
 *  从 [AppIcon] 里抽出来：那段 try/catch 加上后面几行会把那个组合函数顶过 detekt 的长度上限，
 *  而它本身与「画什么」无关，只是把 `Drawable` 拿回来。
 */
private suspend fun SettingsPage.loadIconOnce(packageName: String): android.graphics.drawable.Drawable? =
    withContext(Dispatchers.IO) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Throwable) {
            XLog.d("取应用图标失败：$packageName（${e.javaClass.simpleName}）")
            null
        }
    }

/**
 * 「选择应用」页除列表外那三种形态（加载中 / 一条都没查到 / 搜索无结果）共用的一块 ——
 * 三者只差上面那颗字形与下面那句话，其余摆位 / 尺寸 / 间距 / 字色全走这一份。
 *
 * 让出 [topInset]（顶栏 + 搜索带）后在剩下那块可见内容区里居中，不是整屏居中：content 这层
 * 一直铺到屏幕顶、顶栏是压在它上面的浮层，直接 `Alignment.Center` 会摆到整屏正中。显隐走
 * [AnimatedFade]（只淡）：套展开 / 收起会从中心撑开，看着像抖。
 *
 * @param visible 显不显示（三种形态互斥，判据在调用点）
 * @param topInset 顶上要让出的那一档（顶栏 + 搜索带）
 * @param text 下面那句话
 * @param glyph 上面那颗字形（转圈 / 图标）；取色由这一份给（次要文字色）
 */
@Composable
internal fun BoxScope.PageStatePlaceholder(
    visible: Boolean,
    topInset: Dp,
    text: String,
    glyph: @Composable (Color) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .fillMaxSize()
                .padding(top = topInset),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedFade(visible = visible) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 字形占一个定死的方框：三种形态互相切换时，下面那行字不会跟着上下跳。
                Box(
                    modifier = Modifier.size(APP_STATE_ICON_SIZE),
                    contentAlignment = Alignment.Center,
                ) { glyph(COUITheme.colorScheme.onSurfaceVariantSummary) }
                Spacer(Modifier.height(APP_STATE_TEXT_GAP))
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = PAGE_MARGIN + 16.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 应用种类标签的配色 —— 用户 / 系统 / 模块三档各一个色（区分开）。
 *
 *  · 用户 = 中性（`onSurfaceVariantSummary`）—— 大多数行都是它，不抢眼；
 *  · 系统 = 主题色（`primary`，库那套色板里的蓝）；
 *  · 模块 = 紫（[APP_KIND_MODULE_LIGHT] / [APP_KIND_MODULE_DARK]）—— 库色板只有蓝与红两个色相，
 *    第三个色相得自己定一份，按背景亮度切深浅（与 `popupSurfaceColor` 同一套判据）。
 */
@Composable
internal fun appKindTagTint(
    xposed: Boolean,
    system: Boolean,
): Color =
    when {
        xposed -> {
            if (COUITheme.colorScheme.background.luminance() < 0.5f) {
                APP_KIND_MODULE_DARK
            } else {
                APP_KIND_MODULE_LIGHT
            }
        }

        system -> {
            COUITheme.colorScheme.primary
        }

        else -> {
            COUITheme.colorScheme.onSurfaceVariantSummary
        }
    }

/** 「分身」标签色（明暗各一值，见 `CLONE_TAG_*`）—— 与 [appKindTagTint] 同一套切色判据。 */
@Composable
internal fun cloneTagTint(): Color =
    if (COUITheme.colorScheme.background.luminance() < 0.5f) {
        CLONE_TAG_DARK
    } else {
        CLONE_TAG_LIGHT
    }

/**
 * 应用名右边那枚小标签 —— 「模块」/「系统」/「用户」，或者警告档的「不可打开」。
 *
 * 「模块 / 系统 / 用户」只在「显示系统应用」开着时画（判据在调用点）：那一档会把没有启动图标的
 * 系统应用混进来，光看名字分不出来（「设置」「信息」既有系统的也有用户装的）；关着时以
 * 用户应用为主，逐行挂标签纯属噪音。「不可打开」不看那个开关：任何一行拉不起来都该有。
 *
 * 样式是一枚胶囊（`footnote2` + 低透明度底 + 4dp 圆角）；不用库的 `Chip` —— 那是可选中
 * 的筛选控件，这里要的只是纯展示的小标记。底色由**文字色的低透明度**派生，所以调用点
 * 只需要给颜色。
 *
 * @param text 标签文字
 * @param tint 标签色（文字与底色都由它派生）—— 三档的配色见 [appKindTagTint]，
 *   警告档（「不可打开」）传 `error`
 */
@Composable
internal fun SettingsPage.AppKindTag(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                // 字在胶囊里已经是正中的（下面那个 [opticalTextCenter]），但**胶囊自己**比旁边那行
                //  16sp 标题的墨迹中线高 3px（实测），整体让下来这一档两者才齐（见 [APP_KIND_TAG_SINK]）
                .offset(y = APP_KIND_TAG_SINK)
                .background(
                    color = tint.copy(alpha = APP_KIND_TAG_BG_ALPHA),
                    shape = RoundedCornerShape(APP_KIND_TAG_RADIUS),
                ).padding(APP_KIND_TAG_INSIDE),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            //  标签是一枚**可见的胶囊**：胶囊高度 = 行盒 + 上下各 1dp，而行盒里 baseline 下面那段
            //  descent 汉字不占 ⇒ 字在胶囊里天然偏高（上下留白 1dp : 2dp，一眼看得出）。
            //  沉 0.1em 之后上下才真等距（[opticalTextCenter]）。
            //  与旁边那个大标题的关系不用再单独补偿：标题那边同样调这一支，两边沉的比例一致、
            //  字号差 5sp 只差 0.5dp —— 原先那条 `APP_KIND_TAG_RAISE`（0.5dp）量的正是这个差，
            //  现在由这条公式自己算出来，那枚常量整个删掉了。
            modifier = Modifier.opticalTextCenter(COUITheme.textStyles.footnote2),
            style = COUITheme.textStyles.footnote2,
            color = tint,
            maxLines = 1,
        )
    }
}
