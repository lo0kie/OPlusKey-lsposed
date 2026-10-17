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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.AnimatedFade
import com.lookie.opluskey.ui.model.APP_ICON_BITMAP_SIZE
import com.lookie.opluskey.ui.model.APP_KIND_MODULE_DARK
import com.lookie.opluskey.ui.model.APP_KIND_MODULE_LIGHT
import com.lookie.opluskey.ui.model.APP_KIND_TAG_BG_ALPHA
import com.lookie.opluskey.ui.model.APP_KIND_TAG_INSIDE
import com.lookie.opluskey.ui.model.APP_KIND_TAG_RADIUS
import com.lookie.opluskey.ui.model.APP_KIND_TAG_RAISE
import com.lookie.opluskey.ui.model.APP_ROW_HEIGHT
import com.lookie.opluskey.ui.model.APP_STATE_ICON_SIZE
import com.lookie.opluskey.ui.model.APP_STATE_TEXT_GAP
import com.lookie.opluskey.ui.model.LaunchableApp
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
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
    // 读一次版本号 = 订阅「清缓存」（理由见 `AppPickerState.iconsGeneration`）
    val generation = picker.iconsGeneration.intValue
    // 这一行自己那份图：初值先看缓存（滚回来那一轮就命中），清过缓存则变回 null ⇒ 重新取。
    //  它留在这里不随缓存淘汰而消失 —— 视口里的行不该因为别人补图 / 缓存淘汰而退回通用字形。
    var icon by remember(packageName, generation) { mutableStateOf(picker.cachedIcon(packageName)) }
    // 键只用（包名 + 版本号）：别的行补图不再打断这一行还在取的那一张。
    LaunchedEffect(packageName, generation) {
        if (icon != null) return@LaunchedEffect
        val loaded =
            withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getApplicationIcon(packageName)
                } catch (e: Throwable) {
                    XLog.d("取应用图标失败：$packageName（${e.javaClass.simpleName}）")
                    null
                }
            } ?: return@LaunchedEffect
        // 写进缓存是为了「滚回去不再跨进程问第二轮」，写进自己这一份是为了当场画出来
        picker.putIcon(packageName, loaded)
        icon = loaded
    }
    // 取样按 [APP_ICON_BITMAP_SIZE]（比实际渲染的 40dp 大一档，缩下来清晰）。
    val px =
        with(androidx.compose.ui.platform.LocalDensity.current) {
            APP_ICON_BITMAP_SIZE.roundToPx()
        }
    val bitmap =
        // 尺寸跟着密度走：`px` 不进 key 的话，显示大小 / 密度一变，位图还是按老尺寸建的那张（发虚）
        remember(packageName, icon, px) {
            try {
                icon?.let { d ->
                    val bmp =
                        android.graphics.Bitmap.createBitmap(
                            px,
                            px,
                            android.graphics.Bitmap.Config.ARGB_8888,
                        )
                    d.setBounds(0, 0, px, px)
                    d.draw(android.graphics.Canvas(bmp))
                    bmp.asImageBitmap()
                }
            } catch (e: Throwable) {
                XLog.d("光栅化应用图标失败：$packageName（${e.javaClass.simpleName}）")
                null
            }
        }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            // `fillMaxHeight` 把高度钉在内容区整高上（行高见 [APP_ROW_HEIGHT]），`aspectRatio(1f)`
            // 再按高度算宽度。顺序不能反：先 `aspectRatio` 会拿宽度当基准。
            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
        )
    } else {
        // 取不到图标退回通用「应用」字形
        Icon(PageIcons.MenuStock, null, Modifier.fillMaxHeight().aspectRatio(1f))
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
                // 往上抬半档：COUI 的 `LineHeightStyle` 把行距全塞在行的下面，行盒比字面高一截，
                // 与标题「盒子居中」的胶囊会被推下去小半档，抬回来两个字面的中线才重合。
                .offset(y = -APP_KIND_TAG_RAISE)
                .background(
                    color = tint.copy(alpha = APP_KIND_TAG_BG_ALPHA),
                    shape = RoundedCornerShape(APP_KIND_TAG_RADIUS),
                ).padding(APP_KIND_TAG_INSIDE),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = COUITheme.textStyles.footnote2,
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * 包名 → 应用名（取不到就退回包名本身）—— 「打开应用」格那行小字，以及多条命令里
 * `app:` 那一步的标题。
 *
 * 走宿主的 `PackageManager`：包可见性按宿主算，绑过的应用本就在 [loadLaunchableApps]
 * 那份可见清单里，正常查得到。
 *
 * 带一层缓存（步骤行标题每次重组都要算，名称框里每敲一个字就重组一遍）。它是进程级顶层
 * `val`（活到 `:ui` 进程结束），且只缓存查成功的 —— 把失败写进去（值 = 包名）会让暂时的
 * 查失败永久固化成包名。
 */
internal val appLabelCache = HashMap<String, String>()

internal fun SettingsPage.appLabelOf(packageName: String): String {
    appLabelCache[packageName]?.let { return it }
    val label =
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Throwable) {
            XLog.d("取应用名失败：$packageName（${e.javaClass.simpleName}）")
            // 失败不写缓存，下次再查一次
            return packageName
        }
    appLabelCache[packageName] = label
    return label
}
