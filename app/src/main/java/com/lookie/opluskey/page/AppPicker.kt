package com.lookie.opluskey.page

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.ProgressIndicatorDefaults
import io.github.suqi8.coui.kmp.basic.RadioButton
import io.github.suqi8.coui.kmp.basic.RotatingProgressIndicator
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import com.lookie.opluskey.KeyModule
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Config
import com.lookie.opluskey.hook.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 「打开应用」页：应用列表加载 / 排序 / 选择。 */
/** 「打开应用」页里的一条 —— 界面要用的几样。 */
internal data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
    /**
     * 是不是系统应用（`FLAG_SYSTEM`）——「显示系统应用」开着时拿去画「系统 / 用户」小标签。
     *
     * 用 `FLAG_SYSTEM` 而非「来自 `getInstalledApplications` 那一批」：launcher 图标查询里
     * 本就混着系统应用，按来源分会把它们当用户应用。
     */
    val system: Boolean,
    /**
     * 是不是 Xposed 模块。标签三选一：「模块」压过「系统」压过「用户」——
     * 一个模块同时是系统应用时按模块报。
     */
    val xposed: Boolean,
    /**
     * [AppEntry.resolve] 解得出来入口 —— `app:` 那一步真的拉得起来。
     *
     * 与执行端（[AppLauncher.launchApp]）同一把尺子，拉不起来就在行尾挂「不可打开」提示。
     * 系统应用缺入口的居多，用户应用里也有（被停用 / 非导出入口 / 装了一半）。
     */
    val launchable: Boolean,
)

/** Xposed 模块自带的模块设置入口 category —— 「选择应用」页认模块的判据。 */
private const val XPOSED_MODULE_SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS"

/**
 * 上一次查到的可启动应用清单 —— 进程级顶层 `var`（活到 `:ui` 进程结束），不是 `remember`，
 * 因此也钉住清单里的 `Drawable` 图标。
 *
 * 让二次进入「打开应用」页时立刻有内容可画；缓存不阻止重查：每次进页仍会在后台重跑一遍并
 * 覆盖它，新装 / 卸载的应用照样能出现。
 */
internal var cachedApps: List<LaunchableApp>? = null

/**
 * [cachedApps] 是在哪个模式下查的（「显示全部应用」开没开）。
 *
 * 必须与清单一起写：否则会把「开着系统应用时查的清单」当成「关着时的首帧」用，退出再进
 * 会先闪一屏系统应用。
 */
internal var cachedAppsWithSystem = false

/**
 * 本机可启动的应用，按名字排序。
 *
 * 默认只有有启动图标的那些（`ACTION_MAIN` + `CATEGORY_LAUNCHER`），`includeSystem` 为 true
 * 时再追加一批系统应用。
 *
 * @param includeSystem 顶栏菜单的「显示系统应用」
 */
internal fun SettingsPage.loadLaunchableApps(includeSystem: Boolean = false): List<LaunchableApp> {
    val t0 = android.os.SystemClock.uptimeMillis()
    val pm = context.packageManager
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    // 汉字 → 拼音的转写器（ICU 自带，`icudt*.dat` 里有 `Han-Latin`）
    val romanizer = try {
        android.icu.text.Transliterator.getInstance("Han-Latin")
    } catch (e: Throwable) {
        XLog.w("取不到 Han-Latin 转写器，应用列表退回原名排序：${e.javaClass.simpleName}")
        null
    }
    // 兜底比较器（同键时比原名）；取不到就只按键比，仍稳定
    val collator = try {
        java.text.Collator.getInstance(java.util.Locale.CHINA)
    } catch (e: Throwable) {
        null
    }
    return try {
        // 一条 `ApplicationInfo` → 界面要的那几样，两条来源共用；`xposed` 由调用点给。
        fun entry(ai: android.content.pm.ApplicationInfo, xposed: Boolean) = LaunchableApp(
            packageName = ai.packageName,
            label = try {
                ai.loadLabel(pm).toString()
            } catch (e: Throwable) {
                ai.packageName
            },
            icon = try {
                ai.loadIcon(pm)
            } catch (e: Throwable) {
                null
            },
            system = ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
            launchable = AppEntry.resolve(pm, ai.packageName) != null,
            xposed = xposed,
        )
        // 有启动图标的应用
        val launched = pm.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != KeyModule.MODULE_PACKAGE }
            .toList()
        // Xposed 模块：默认就列，不看开关 —— 模块往往没有 launcher 图标、不在上一批里，却是最常挑的。
        // 判据是模块自带的入口 category `MODULE_SETTINGS`（`xposedminversion` 那类 metaData 在 dumpsys
        // 里不落地，翻 APK 的 `META-INF/xposed/` 太贵）。没声明该 category 的模块认不出来，行不带「模块」标签。
        val xposedList = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(
                XPOSED_MODULE_SETTINGS_CATEGORY,
            ),
            0,
        )
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != KeyModule.MODULE_PACKAGE }
            .toList()
        val xposedPkgs = xposedList.mapTo(HashSet()) { it.packageName }
        // 「显示系统应用」开着时再追加的一批 —— 既不在上一批、也不是模块的其余应用（主体是没有
        // launcher 图标的系统应用）。不筛「拉不拉得起来」：能拿到全量包列表靠宿主自己声明了
        // `QUERY_ALL_PACKAGES` —— 界面代码跑在宿主进程，包可见性按宿主算。
        val others = if (!includeSystem) {
            emptyList()
        } else {
            val known = (launched + xposedList).mapTo(HashSet()) { it.packageName }
            pm.getInstalledApplications(0)
                .asSequence()
                .filter { it.packageName !in known }
                .filter { it.packageName != KeyModule.MODULE_PACKAGE }
                .toList()
        }
        // 两条来源必须跨来源去重：Xposed 模块的规范写法是「一个 launcher activity + 一个挂
        // MODULE_SETTINGS 的 activity-alias」，同一个包在 launcher 与模块两批里各出现一次。
        // 不去重 ⇒ 列表两行同包名 ⇒ `items(key = ...)` 抛「Key ... was already used」，异常落在
        // 宿主 Activity 的组合里 ⇒ 打开「选择应用」页即崩宿主页面（`others` 已按 known 滤过）。
        val apps = ((launched + xposedList).distinctBy { it.packageName } + others)
            .map { entry(it, it.packageName in xposedPkgs) }
            // 排序键只算一次：罗马化不便宜，不能放进比较器里每比一次算一次
            .map { it to sortKeyOf(it.label, romanizer) }
            .sortedWith { (a, ka), (b, kb) ->
                val byKey = ka.compareTo(kb)
                if (byKey != 0) {
                    byKey
                } else {
                    val byName = collator?.compare(a.label, b.label) ?: 0
                    if (byName != 0) byName else a.packageName.compareTo(b.packageName)
                }
            }
            .map { it.first }
            .toList()
        // 打头几条 + 耗时便于核对（转写器是否生效、这次查了多久）
        XLog.i(
            "可启动应用：${apps.size} 个（显示系统应用=$includeSystem，模块 ${xposedList.size} 个、" +
                "额外追加 ${others.size} 个），" +
                "耗时 ${android.os.SystemClock.uptimeMillis() - t0}ms，" +
                "排序（前 10）：" + apps.take(10).joinToString(" / ") { it.label },
        )
        apps
    } catch (e: Throwable) {
        XLog.w("查可启动应用失败（本页会显示空提示）", e)
        emptyList()
    }
}

/**
 * 一个应用名的排序键 —— 罗马化之后的小写 ASCII 字母数字串。
 *
 * 汉字经 ICU 的 `Han-Latin` 转成拼音，再走 NFKD 拆出组合符号、只留字母数字：不能只滤非
 * a-z，声调是组合符号（`ǎ` = `a` + U+030C），按字符过滤会把元音一起丢掉（`bǎi` → `bi`）。
 * 转写器为 null / 抛异常 / 结果为空时退回原名小写。
 */
internal fun SettingsPage.sortKeyOf(label: String, romanizer: android.icu.text.Transliterator?): String {
    val romanized = try {
        romanizer?.transliterate(label) ?: label
    } catch (e: Throwable) {
        label
    }
    val decomposed = java.text.Normalizer.normalize(
        romanized,
        java.text.Normalizer.Form.NFKD,
    )
    val sb = StringBuilder(decomposed.length)
    for (ch in decomposed) {
        // 全角字母 / 数字经 NFKD 已折成半角，这里只剩 ASCII 判据
        if (ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z') {
            sb.append(ch.lowercaseChar())
        }
    }
    return sb.toString().ifEmpty { label.lowercase(java.util.Locale.ROOT) }
}

/**
 * 一条应用项的行首图标 —— 把别的应用的 `Drawable` 转成 `ImageBitmap` 才能给 Compose 的
 * `Image` 用（工程里没有 `rememberDrawablePainter` 那类桥）。
 *
 * 加载放在每项自己的组合里：只有滚到的项才会 `loadIcon`，两百个应用也不会拖住首帧。
 */
@Composable
internal fun SettingsPage.AppIcon(app: LaunchableApp) {
    // 取样按 [APP_ICON_BITMAP_SIZE]（比实际渲染的 40dp 大一档，缩下来清晰）。
    val px = with(androidx.compose.ui.platform.LocalDensity.current) {
        APP_ICON_BITMAP_SIZE.roundToPx()
    }
    val bitmap = remember(app.packageName) {
        try {
            app.icon?.let { d ->
                val bmp = android.graphics.Bitmap.createBitmap(
                    px,
                    px,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
                d.setBounds(0, 0, px, px)
                d.draw(android.graphics.Canvas(bmp))
                bmp.asImageBitmap()
            }
        } catch (e: Throwable) {
            XLog.d("取应用图标失败：${app.packageName}（${e.javaClass.simpleName}）")
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
private fun BoxScope.PageStatePlaceholder(
    visible: Boolean,
    topInset: Dp,
    text: String,
    glyph: @Composable (Color) -> Unit,
) {
    Box(
        modifier = Modifier
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
 * 应用名右边那枚小标签 —— 「系统」/「用户」，或者警告档的「不可打开」。
 *
 * 「系统 / 用户」只在「显示系统应用」开着时画（判据在调用点）：那一档会把没有启动图标的
 * 系统应用混进来，光看名字分不出来（「设置」「信息」既有系统的也有用户装的）；关着时以
 * 用户应用为主，逐行挂标签纯属噪音。「不可打开」不看那个开关：任何一行拉不起来都该有。
 *
 * 样式是一枚胶囊（`footnote2` + 低透明度底 + 4dp 圆角）；不用库的 `Chip` —— 那是可选中
 * 的筛选控件，这里要的只是纯展示的小标记。
 *
 * @param text 标签文字
 * @param warning 警告档：取 `error` 色（「不可打开」用），而不是次要文字色
 */
@Composable
internal fun SettingsPage.AppKindTag(
    text: String,
    warning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tint = if (warning) {
        COUITheme.colorScheme.error
    } else {
        COUITheme.colorScheme.onSurfaceVariantSummary
    }
    Box(
        modifier = modifier
            // 往上抬半档：COUI 的 `LineHeightStyle` 把行距全塞在行的下面，行盒比字面高一截，
            // 与标题「盒子居中」的胶囊会被推下去小半档，抬回来两个字面的中线才重合。
            .offset(y = -APP_KIND_TAG_RAISE)
            .background(
                color = tint.copy(alpha = APP_KIND_TAG_BG_ALPHA),
                shape = RoundedCornerShape(APP_KIND_TAG_RADIUS),
            )
            .padding(APP_KIND_TAG_INSIDE),
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
    val label = try {
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

/**
 * 「选择应用」页那条搜索带（固定搜索框）—— 挂在顶栏的 `bottomContent` 里。
 *
 * 位置是折叠栏底边（顶栏自己排的），底色 / 模糊 / 下沿细线都由顶栏那层负责。搜索词是页面级
 * 状态 [SettingsPage.appFilter]；高度报给 [SettingsPage.appSearchBandHeight]，列表顶上那个
 * 占位 Spacer 读它。
 */
@Composable
internal fun SettingsPage.AppSearchBand() {
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { appSearchBandHeight.value = it.height },
    ) {
        // 库自己的搜索输入框（`InputField`）。`expanded` 必须给 false：那一套「展开态」是给
        // 「点搜索框撑开一页结果」用的，本页本身就是结果列表；而且 `expanded = true` 会让
        // `label` 一直不显示，占位文案就没了。
        // 上下两档与别处同一套，且两档同值（都是 [PAGE_TOP_PADDING]）。
        InputField(
            query = appFilter.value,
            onQueryChange = { appFilter.value = it },
            // 键盘上点「搜索」不做额外动作：过滤是边输边做的
            onSearch = { },
            expanded = false,
            onExpandedChange = { },
            modifier = Modifier
                .fillMaxWidth()
                // 只让开左右。纵向只带下边一档（[PAGE_TOP_PADDING]）：带子贴着折叠栏下沿，
                // 下边这一档就是带子与内容之间的间距，各页同一档。
                .padding(
                    start = PAGE_MARGIN,
                    end = PAGE_MARGIN,
                    bottom = PAGE_TOP_PADDING,
                ),
            label = "搜索应用",
        )
    }
}

/**
 * 「打开应用」页 —— 列出本机可启动的应用。
 *
 * 点一条只**切换选中的那个**（行尾单选圈跟着走），右下角那颗 ✓ 才把选择交出去
 * （`app:<包名>` 或 `window:<包名>`，看 [SettingsPage.smallWindow]）。
 *
 * 用 `LazyColumn` 而不是 `verticalScroll + Card`：这一页可能有 200+ 行，`verticalScroll`
 * 会把所有行都组合一遍（每行都要 `loadIcon`，首帧直接卡住）。每行各自一张卡。
 */
@Composable
internal fun SettingsPage.AppPickerScreen(
    selected: String?,
    onPick: (String, Boolean) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    // 这一页是 push 转场的目标页，首帧要把 160 个应用的 label 读出来、还要把中文名逐个转写成
    // 拼音算排序键 —— 全压在首帧上转场就卡住。现在丢到后台调度器去查（`Dispatchers.IO`），
    // 页面立刻进场、行等查完自己出现。
    //
    // 「显示系统应用」开关一变，列表来源就变了，所以直接把它当重查的键。
    val withSystem = showSystemApps.value
    // 初值先铺上一次查到的清单（[cachedApps]），二次进入这一页就是瞬时的，同时后台照样重查
    // 一遍（新装的应用才会出现）。那份缓存只在模式一致时才能拿来铺首帧，否则会先闪一屏系统
    // 应用（见 [cachedAppsWithSystem]）。
    var loaded by remember {
        mutableStateOf(if (cachedAppsWithSystem == withSystem) cachedApps else null)
    }
    // 上一轮查的是哪个模式（`null` = 还没查过）。只有模式真的翻转才把列表收起来走加载态：
    // 那一刻列表来源换了，旧清单留着是错的。进页那一次不收 —— 那是同一份清单的重查。
    var lastMode by remember { mutableStateOf<Boolean?>(null) }
    // 本地选中：点行只改它，右下角那颗 ✓ 才把选择交出去。初值照当前绑定的那个（进页时先亮着它）。
    var pending by remember(selected) { mutableStateOf(selected) }
    LaunchedEffect(withSystem) {
        // 日志放在 effect 里而不是组合体里：组合体会随每次重组重跑，那几行会被打好几遍。
        if (lastMode != null && lastMode != withSystem) {
            XLog.i("选择应用：模式翻转（显示全部应用=$withSystem），清单回到加载态")
            loaded = null
        }
        lastMode = withSystem
        // 整段查询都在后台调度器上，一行都不许落在主线程：里面有 `getInstalledApplications`、
        // 逐个 `getLaunchIntentForPackage`（开着系统应用时是几百次 binder 调用）与每个包的
        // label / icon 加载，全是阻塞式的。用 `IO` 而非 `Default`：这些活是 IO 型，而 `Default`
        // 是按 CPU 核数限流的共享池，压满它会拖住别的 Default 任务（转场、各页动画）。
        XLog.i("打开应用页：取列表（显示全部应用=$withSystem，先用缓存 ${cachedApps?.size ?: 0} 条）")
        val fresh = withContext(Dispatchers.IO) { loadLaunchableApps(withSystem) }
        cachedApps = fresh
        cachedAppsWithSystem = withSystem
        loaded = fresh
    }
    val apps = loaded.orEmpty()

    // 搜索词提到页面级（[SettingsPage.appFilter]）：搜索框挂在顶栏里，与这一页的过滤逻辑分处
    // 两棵树，得共用同一份状态。它跟着进这一页清空（见 [SettingsPage.openAppPickerForStep]）。
    val filter = appFilter.value
    // 「隐藏失效应用」：纯显示过滤 —— 判据 [LaunchableApp.launchable] 已经在手上的清单里，
    // 不必重查。它不随进页清空、且落盘（与「显示系统应用」同一套账）。
    val hideBroken = hideUnopenable.value
    val shown = remember(apps, filter, hideBroken) {
        apps.asSequence()
            .filter { !hideBroken || it.launchable }
            .filter {
                filter.isBlank() ||
                    it.label.contains(filter, ignoreCase = true) ||
                    it.packageName.contains(filter, ignoreCase = true)
            }
            .toList()
    }

    val listState = rememberLazyListState()
    // 固定搜索框那条带子的高度（含上下两档内边距）—— 列表顶上那个占位 Spacer 用同一个值，
    // 两者才严丝合缝。带子挂在顶栏里，量高度在那边，值走页面级的
    // [SettingsPage.appSearchBandHeight]；首帧还没量到时是 0，下一帧自纠。
    val searchBandDp = with(LocalDensity.current) { appSearchBandHeight.value.toDp() }
    val tick = rememberTick()
    // 列表顶上那一整段（顶栏 + 固定搜索带 + 首行上边距）—— 进场定位时目标行要落在它下面。
    // 取折叠后的顶栏高度（[collapsedHeaderHeight]）：定位会把顶栏折叠到底，按展开高度算会多
    // 让出一整段。带子高度含它自己那 8dp 下内边距，再加列表头那个占位项补的 [PAGE_TOP_PADDING]。
    // 再补半个行距（[GRID_GAP] / 2）：行距挂在每一行下边，目标行顶到带子下沿时上面那半个间距
    // 正好被带子吃掉，补一半上下两行的间距才均匀。
    // `scrollToItem` 收的是 px，而 `LocalDensity.current` 只能在组合期读，不能挪进 `LaunchedEffect`。
    val listTopInsetPx = with(LocalDensity.current) {
        (collapsedHeaderHeight() + searchBandDp + PAGE_TOP_PADDING + GRID_GAP / 2).roundToPx()
    }
    // 只在没在过滤、且不在第一项时才滚（第一项本就在眼前，滚了反而多一次位移）。
    // 下标要加 [APP_LIST_HEADER_ITEMS]（列表头是「顶栏让位 + 搜索带」那一项）。
    // 还必须带一段负偏移：`scrollToItem` 把目标顶到视窗顶部，而顶部被固定搜索带盖着，不加
    // 偏移目标行正好藏在带子底下，表现就是「滚了却看不见」。带子高度首帧还没量到时先不滚。
    // key 里不放 `listTopInsetPx`：它跟着顶栏折叠走（用户一滚就变），放进去等于每滚一下都被
    // 拽回已选那一行。
    LaunchedEffect(selected, shown, searchBandDp) {
        if (selected == null || filter.isNotBlank()) return@LaunchedEffect
        if (searchBandDp <= 0.dp) return@LaunchedEffect
        val index = shown.indexOfFirst { it.packageName == selected }
        if (index > 0) {
            // `scrollToItem` 是程序性跳转，不走嵌套滚动那条路 ⇒ 顶栏不会自己折叠，跳完还是
            // 展开的、把目标行压在下面，因此直接把折叠量拉满。
            scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
            XLog.d(
                "定位到已选：index=$index 顶栏 offset=${scrollBehavior.state.heightOffset}" +
                    "/limit=${scrollBehavior.state.heightOffsetLimit} inset=$listTopInsetPx",
            )
            listState.scrollToItem(index + APP_LIST_HEADER_ITEMS, scrollOffset = -listTopInsetPx)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 列表区（含加载提示）—— 两种状态占同一块地方：加载时不留白、加载完不跳一下。
        //
        // 右下角那颗「滚到顶部」的 FAB 也挂在这个盒子里：盒子就是「页面顶 → 页面底」那一整块，
        // 它的 `BottomEnd` 正好是页面右下角，与「按键绑定」页那颗 FAB 同一个落点，不必再套 Box。
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                // `nestedScroll` 挂在 `LazyColumn` 的 `modifier` 上 = 挂在它自己那个滚动容器
                // 外侧（`LazyColumn` 的滚动在组件内部）。
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 越界回弹：COUI 自己的弹簧实现（`overScrollVertical`）。挂在这里同样是滚动
                    // 容器外侧，吃的是它没吃掉的那部分位移 —— 列表不满一屏也弹。
                    .overScrollVertical(),
                // 底部留白与别页一模一样（[PAGE_BOTTOM_PADDING]，32dp）——
                // 与「按键绑定」页那种「最后一行被 FAB 压住」的取舍保持一致。
                contentPadding = PaddingValues(bottom = PAGE_BOTTOM_PADDING),
            ) {
                // —— 头一项：顶栏让位 + 固定搜索框那条带子的占位 ——
                //
                // 搜索框在固定层里，所以列表只要在顶上让出「顶栏 + 那条带子」这么多地方；滚起来
                // 之后列表钻到带子背后，由带子那层不透明底挡住。因此下面所有按下标的滚动都要加
                // [APP_LIST_HEADER_ITEMS] 这个偏移。
                item(key = "header-space") {
                    // 用动态值（见 [currentHeaderHeight]）：折叠阶段这一档跟着缩下去。
                    // 顶栏 + 带子（带子的测量高度里含它自己那 8dp 下内边距）再补一档
                    // [PAGE_TOP_PADDING]：这一页第一行是卡片，没有别页首行那个 `SmallTitle`
                    // 自带的上内边距，不补的话卡片会直接贴着搜索框。
                    Spacer(
                        Modifier.height(
                            currentHeaderHeight(scrollBehavior) + searchBandDp + PAGE_TOP_PADDING,
                        ),
                    )
                }

                // 列表区 —— 加载中什么都不铺（列表空着），转圈由下面那层叠上去画。
                if (loaded != null) {
                    // 「查得到但没有匹配项」那句提示不在这个列表里（见下面那层浮层）：它一进来就
                    // 会占掉一个下标，而按下标滚到当前绑定应用的偏移 [APP_LIST_HEADER_ITEMS]
                    // 只算了顶上那个占位项 —— 多一项就错一行。
                    items(shown, key = { it.packageName }) { app ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                // 列表项的出现 / 消失 / 换位都带动画（`animateItem`）：加载完成时
                                // 一行行淡进来，过滤时也是挪过去的，而不是整屏换掉。
                                .animateItem()
                                .padding(horizontal = PAGE_MARGIN)
                                // 间距在高度之前给：这样 [APP_ROW_HEIGHT] 量的是卡片自己，那 12dp
                                // 是卡片外面的行距（顺序反了会把卡片压矮 12dp）。
                                .padding(bottom = GRID_GAP)
                                // 行高定死 ⇒ 行首那枚图标才能整高（见 [APP_ROW_HEIGHT]）
                                .height(APP_ROW_HEIGHT),
                        ) {
                            BasicComponent(
                                startAction = { AppIcon(app) },
                                // 行尾单选圈跟着**本地选中**走：点一行只是把圈挪过去，交出去靠右下角那颗 ✓
                                endActions = {
                                    RadioButton(
                                        selected = app.packageName == pending,
                                        onClick = null,
                                    )
                                },
                                cardListPosition = CardListPosition.Full,
                                onClick = {
                                    tick()
                                    pending = app.packageName
                                },
                            ) {
                                // 标题 / 摘要自己排，不走库的 `title` / `summary` 两个参数：标题那行要在
                                // 名字右边再挂一枚小标签，而那两个参数只吃 `String`。样式照库的默认内容来。
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = app.label,
                                        // `weight` 让长名字先缩，标签始终留在行内可见
                                        //（`fill = false`：名字短时不去撑满，标签紧跟在右边）
                                        modifier = Modifier.weight(1f, fill = false),
                                        style = COUITheme.textStyles.headline1,
                                        fontWeight = FontWeight.Medium,
                                        color = BasicComponentDefaults.titleColor().color,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    // 这一行是「盒子居中」而非 `alignByBaseline`：基线对齐会把胶囊整体
                                    // 压低小半档（大标题字面比标签高一截），居中反而让两个字面的中线
                                    // 基本重合（见 [APP_KIND_TAG_RAISE]）。
                                    // 「系统 / 用户」只在「显示系统应用」开着时画；「模块」不看那个开关
                                    // —— 模块默认就在列表里，开关关着也得分得出来。
                                    if (withSystem || app.xposed) {
                                        Spacer(Modifier.width(APP_KIND_TAG_GAP))
                                        // 三选一：「模块」压过「系统」压过「用户」
                                        AppKindTag(
                                            text = when {
                                                app.xposed -> "模块"
                                                app.system -> "系统"
                                                else -> "用户"
                                            },
                                        )
                                    }
                                    // 「不可打开」不看那个开关：用户应用里也有拉不起来的，每一行都该有。
                                    if (!app.launchable) {
                                        Spacer(Modifier.width(APP_KIND_TAG_GAP))
                                        AppKindTag(text = "不可打开", warning = true)
                                    }
                                }
                                Text(
                                    text = app.packageName,
                                    modifier = Modifier.padding(top = APP_TITLE_SUMMARY_GAP),
                                    style = COUITheme.textStyles.body2,
                                    color = BasicComponentDefaults.summaryColor().color,
                                )
                            }
                        }
                    }
                }
            }

            // —— 除列表外的三种形态（一条都没查到 / 被搜索词筛没了 / 还在加载）都走
            // [PageStatePlaceholder] 那一份，三者只差上面那颗字形与下面那句话。它们都挂在列表
            // 外面（与 `LazyColumn` 同一层浮层），而不是当列表第一项：列表项一多，按下标滚到当前
            // 绑定应用的偏移 [APP_LIST_HEADER_ITEMS] 就错一行。空态只在查完之后才有意义。
            PageStatePlaceholder(
                visible = loaded != null && shown.isEmpty(),
                topInset = currentHeaderHeight(scrollBehavior) + searchBandDp,
                // 两种情况各一颗：清单本身是空的 vs 被搜索词筛没了。后一颗不用划掉的放大镜
                // —— 那道斜杠与「无操作」那颗 `Ban` 长得像，摆这儿读起来像「禁止搜索」。
                glyph = { tint ->
                    Icon(
                        imageVector = if (apps.isEmpty()) {
                            PageIcons.EmptyApps
                        } else {
                            PageIcons.NoMatch
                        },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = tint,
                    )
                },
                text = if (apps.isEmpty()) {
                    "没查到可启动的应用 —— 系统限制了本页可见的应用列表"
                } else if (hideBroken && apps.all { !it.launchable }) {
                    "可启动的应用都被「隐藏不可打开」藏掉了"
                } else {
                    "没有匹配「$filter」的应用"
                },
            )

            // 转圈用 COUI 的 `RotatingProgressIndicator`（ColorOS 默认加载态）。
            PageStatePlaceholder(
                visible = loaded == null,
                topInset = currentHeaderHeight(scrollBehavior) + searchBandDp,
                text = "正在加载应用…",
                glyph = { tint ->
                    // 库那套三个数是按 26dp 画布算出来的，只把 `size` 换成 32dp 等于只多留白、
                    // 圈没变大 —— 三个数要按同一比例一起放大（比例由 `APP_STATE_ICON_SIZE` 现算）。
                    val spinnerScale = APP_STATE_ICON_SIZE.value /
                        ProgressIndicatorDefaults.DefaultRotatingProgressIndicatorSize.value
                    RotatingProgressIndicator(
                        color = tint,
                        size = APP_STATE_ICON_SIZE,
                        ringDiameter = ProgressIndicatorDefaults
                            .DefaultRotatingProgressIndicatorRingDiameter * spinnerScale,
                        strokeWidth = ProgressIndicatorDefaults
                            .DefaultRotatingProgressIndicatorStrokeWidth * spinnerScale,
                    )
                },
            )

            // 右下角那颗 ✓ 才是「确定选择」：点行只切换。样式与「按键绑定」页那颗（新增命令）逐项
            // 对齐：尺寸 / 形状 / 阴影走库默认（56dp 见方、`CircleShape`、12dp 阴影）、容器色取库默认
            // `colorScheme.primary`。图标 tint 显式给 `onPrimary`：库内部只给容器色、不给 contentColor，
            // 不显式上色会继承到 `onSurface`（深色）。位置同「右 [PAGE_MARGIN]×2、下
            // [PAGE_BOTTOM_PADDING]×2」，点一下先振动（[rememberTick]）。
            // 显隐：手上有选中的才出现 —— 没得确认就不占地方。
            PageCornerFab(visible = pending != null) {
                FloatingActionButton(
                    onClick = {
                        tick()
                        pending?.let { onPick(it, smallWindow.value) }
                    },
                ) {
                    Icon(
                        PageIcons.Confirm,
                        contentDescription = "确定选择",
                        tint = COUITheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/**
 * 从「添加命令」浮层（或点已添加那一步弹出的同一张选择菜单）进「打开应用」页 ——
 * 挑中的应用作为一步写进当前多条命令：[replaceIndex] < 0 时追加，`>= 0` 时把那一步换成它。
 *
 * 与网格那条入口的区别只有两处：不记手势（[_appPickerGesture] = null），以及调用点在
 * 「自定义命令」页上 —— 压进栈之后「打开应用」下面那一页就是「自定义命令」，返回自然退到它。
 */
internal fun SettingsPage.openAppPickerForStep(replaceIndex: Int = -1) {
    XLog.i("添加命令：进「打开应用」页挑一步（替换行=$replaceIndex）")
    _appPickerGesture = null
    _appPickerForStep = true
    _appPickerReplaceStep = replaceIndex
    // 搜索词清空：它是页面级状态，不随页面出栈注销 —— 留着的话下次进来就是一份被过滤过的列表。
    appFilter.value = ""
    navTo(Screen.APP_PICKER)
}
