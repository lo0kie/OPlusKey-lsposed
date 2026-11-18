package com.lookie.opluskey.ui.screen.apppicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.kit.ConfirmDialog
import com.lookie.opluskey.ui.state.picker.AppPickerUiState
import com.lookie.opluskey.ui.state.picker.PickedDetail
import com.lookie.opluskey.ui.state.picker.PickerToggle
import com.lookie.opluskey.ui.style.escapeEmphasis
import com.lookie.opluskey.ui.wire.appTargetLabelOf
import com.lookie.opluskey.ui.wire.registerLocalReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// 「选择应用」页那三张**拦人的脸** —— 都是「留在这一页」，什么都不写。
//
// 前两张只在**点 FAB** 那一刻弹：点行只是往下一级走，到不了写入那一步。
// 第三张是**退页**那一刻弹（触发点在 `SettingsPage.goBack`）—— 结束应用那趟没有二级页，
// 挑中一行就点 FAB，所以「挑了却没确认就退」也只有这一张管得着。

/**
 * 三张确认窗的挂载点。
 *
 * @param onPick 「这一档开关用不了」那一路的兜底：用户点了确定 ⇒ 按普通方式写下去。
 */
@Composable
internal fun SettingsPage.AppPickerDialogs(
    ui: AppPickerUiState,
    onPick: (PickedDetail, Boolean) -> Unit,
) {
    UnopenableDialog(ui)
    BlockedToggleDialog(ui, onPick)
    DiscardPickDialog()
}

/**
 * 把 [toggle] 那一档开关拨回关 —— 「这一档用不了」那张窗点确定时调。
 *
 *  两档各一个字段、判据只有「哪一档」，所以收在这一处；窗那边不必认识具体字段
 *  （与「两档共用一张窗」同一个理由，见 [ToggleBlock]）。
 */
private fun SettingsPage.turnOff(toggle: PickerToggle) {
    when (toggle) {
        PickerToggle.MiniWindow -> picker.smallWindow.value = false
        PickerToggle.StockAnim -> picker.stockAnim.value = false
    }
}

/**
 * (1)「此应用无法打开」：点 FAB 时选中的是个拉不起来的应用。
 *
 *  **没有正文**（`summary = null`；给空串会画出一行空白），两颗按钮都只是把这句提示关掉、
 *  回选择页 —— 什么都不写、什么都不改。
 */
@Composable
private fun SettingsPage.UnopenableDialog(ui: AppPickerUiState) {
    ConfirmDialog(
        show = ui.blockedUnopenable != null,
        title = I18n.get(Strings.DIALOG_APP_UNOPENABLE_TITLE),
        summary = null,
        action = I18n.get(Strings.COMMON_CONFIRM),
        positiveDanger = false,
        onDismiss = { ui.blockedUnopenable = null },
        onConfirm = { ui.blockedUnopenable = null },
    )
}

/**
 * (2)「这一档开关这一次用不了」：**只有点 FAB 这一个触发点**（`AppConfirmFab` 里，
 * 判据见 `blockedToggleOf`）。两档共用这一张，只有文案与「拨哪一个开关」不同：
 *  · 小窗 —— 框架不支持这个应用开小窗；
 *  · 原版动画 —— 分身 / 快捷方式 / 具体界面吃不到（见 `AppPickerState.stockAnimUnsupported`）。
 *
 *  确认 ⇒ 菜单里那一项拨回关，并按**原来那个完整目标**照常写下去（用户要的就是「取消勾选、
 *  照常存」）；取消 ⇒ 什么都不做。正文里的名字用 `「」` 夹住；动作项不上红（不丢用户的东西）。
 *
 *  「完整」这三个字是重点：`999@包名/类名` 里的用户前缀是分身的身份，`/类名` 是用户在二级页
 *  刚挑好的具体界面 —— 早先这里交的是 `app.packageName`，写回去就成了「主用户、自动入口」，
 *  用户看到的是「我就关了个开关，绑的东西怎么变了」。
 */
@Composable
private fun SettingsPage.BlockedToggleDialog(
    ui: AppPickerUiState,
    onPick: (PickedDetail, Boolean) -> Unit,
) {
    val blocked = ui.blockedToggle
    val kind = blocked?.toggle
    ConfirmDialog(
        show = blocked != null,
        title =
            when (kind) {
                PickerToggle.StockAnim -> I18n.get(Strings.DIALOG_STOCK_ANIM_DROPPED_TITLE)
                else -> I18n.get(Strings.DIALOG_APP_UNSUPPORTED_MINI_WINDOW_TITLE)
            },
        summary =
            blocked?.let {
                I18n.format(
                    when (it.toggle) {
                        PickerToggle.StockAnim -> Strings.DIALOG_STOCK_ANIM_DROPPED_MESSAGE
                        PickerToggle.MiniWindow -> Strings.DIALOG_APP_UNSUPPORTED_MINI_WINDOW_MESSAGE
                    },
                    escapeEmphasis(it.label),
                )
            },
        action = I18n.get(Strings.COMMON_CONFIRM),
        positiveDanger = false,
        onDismiss = { ui.blockedToggle = null },
        onConfirm = {
            ui.blockedToggle = null
            // 拨回关：菜单里那一项跟着回去，用户看得见这次选择了什么
            blocked?.let {
                turnOff(it.toggle)
                XLog.w("选择应用：这一档用不了，已把「${it.toggle}」拨回关")
            }
            //  写下去的仍是 `pending`（可能带 `/类名`、快捷方式的 id，也可能带 `999@`）——
            //  这一页的 FAB 就是按它弹的这张窗。
            ui.pending?.let { picked ->
                XLog.w("选择应用：${picked.target} 改为普通方式写入")
                onPick(picked, picker.smallWindow.value)
            }
        },
    )
}

/**
 * (3)「放弃这次选择」：这一趟已经挑中过一条（二级页点了确认）**或拨过打开方式**、
 * 还没按右下角那颗提交，用户按了返回。
 *
 *  触发点在 `SettingsPage.goBack`（`picker.draft` 非空、或 `picker.windowTogglesChanged` 时
 *  置位并拦住不退），这里只负责画。不拦的话这一退会把草稿连同整趟挑的整个丢掉 ——
 *  而页面离栈时 `resetSession` 连草稿一起清，用户只是「挑完想先看看别的」回来就没有了。
 *  拨开关那一档同理：小窗 / 原版动画是绑定的一部分，拨了没挑就退出去等于什么都没改。
 *
 *  ## 正文里念的是**格式化后**的那一句
 *
 *  `draft.label` 只是那一行自己的名字（应用名 / 界面名 / 快捷方式名），单独拿出来念，
 *  用户分不清丢的是「微信」还是「微信(扫一扫)」—— 而这页列表很长，挑错了要滚回去重找。
 *  所以走 [appTargetLabelOf]：应用名 [+ 小窗] [+ 组件名 / 快捷方式名]，与顶栏「已选择」那行、
 *  网格那一格的小字、命令卡摘要**同一套拼法**（三处各拼一半的老毛病不再带进来）。
 *
 *  调用点把草稿的 `shortcutId` / 「小窗口打开」那一档一起交过去：快捷方式不带 `/类名` 的后缀，
 *  勾着小窗的目标要念成「微信(小窗)」而不是光一个应用名。
 *
 *  **没有草稿（只拨过开关）就不带正文**：那句要嵌「哪一条」，而这时没有目标可念 ——
 *  同一张窗、同一个标题与同一颗「放弃」就够说清这一退会丢什么；不为这一档另写一句文案。
 *
 *  确认 ⇒ 丢掉草稿并**真退**（`popBackStack` 绕开 `goBack` 的拦截，否则又弹回自己）；
 *  取消 ⇒ 关掉窗，回到这一页继续挑（草稿原样留着，那颗 FAB 仍在）。
 */
@Composable
private fun SettingsPage.DiscardPickDialog() {
    //  名字要在**弹窗那一刻**取：草稿一清（确认那一下）就没有来源了。
    val picked = picker.draft
    ConfirmDialog(
        show = overlays.confirmLeavePick,
        title = I18n.get(Strings.DIALOG_DISCARD_PICK_TITLE),
        summary =
            picked?.let {
                I18n.format(
                    Strings.DIALOG_DISCARD_PICK_MESSAGE,
                    escapeEmphasis(
                        appTargetLabelOf(
                            target = it.target,
                            shortcut = it.shortcutId != null,
                            window = picker.smallWindow.value,
                            draftLabel = it.label,
                            stockAnim = picker.stockAnim.value,
                        ),
                    ),
                )
            },
        action = I18n.get(Strings.COMMON_DISCARD),
        onDismiss = { overlays.confirmLeavePick = false },
        onConfirm = {
            overlays.confirmLeavePick = false
            XLog.w("选择应用：放弃这一趟挑中的 ${picked?.target}，退页")
            popBackStack()
        },
    )
}

/**
 * 一次查询的**附注** —— 目前只有两件：快捷方式那一节是不是没查出来、以及它是不是还在等回包。
 *
 *  为什么不并进返回值：清单本身是 `List<LaunchableActivity>?`（null = 整次失败），
 *  而「界面查到了、快捷方式没查到」是第三种状态。编码成哨兵行或改返回类型，会让每个
 *  只关心清单的调用点都跟着改；而它唯一的用处是「别把 `appshortcut:` 降级成 `app:`」
 *  （见 [resolveSelection]）。
 *
 *  落在自己这个文件里：[ActivityList] 那一份是**一组查询函数**（按主题命名），它是其中一个
 *  小对象；两者塞在同一个文件里时「文件名该跟谁」这件事没有正确答案。
 */
internal class LoadNotes {
    /** 快捷方式那一节这次**没查出来**（读不到）—— 判据同旧版，见 [resolveSelection]。 */
    var shortcutsFailed = false
}

// 「进场定位」最后那一下**实测校准** —— 「选择应用」与「选择界面」两页共用。
//
// 抽出来是因为两页的几何是同一件事（都是「顶栏 + 固定带」下面起画），而校准只有一份口径。

/**
 * 定位滚完之后，按**实测布局**把目标行收进该在的位置。
 *
 * ## 为什么不只信算术
 *
 *  落点是按库的两个槽位常量算的（展开 / 折叠两档，见两页 `Locate*Selected` 的注释）。
 *  常量与实际之间总会差几 dp（字号档、系统栏高度、库版本），而 `scrollToItem` 的位移还可能被
 *  顶栏吃掉一点。所以滚完读一次布局结果，差多少收多少 —— 判断标准是**那一行此刻在屏幕上的位置**。
 *
 * ## 只往**上**收
 *
 *  目标行比预期低（`arrived > targetPx`）才补：低一点只是多留一段空白，不会看错行；
 *  而往回滚会让顶栏重新展开、遮挡变高，越纠越糟。
 *
 * ## 必须先等一帧
 *
 *  `scrollToItem` 是挂起的，但它返回那一刻 `layoutInfo` 还是滚动**之前**的那一份。
 *  上一版就是因为没等这一帧，读到旧落点、照着它「纠正」，结果首屏多滚了一整屏
 *  （真机反馈「滚动距离偏多」）。
 *
 * @param index 列表里那一项的下标（**含**顶上那个「顶栏让位」占位项，见 `APP_LIST_HEADER_ITEMS`）
 * @param targetPx 这一项的顶边该落在视窗顶下方多少像素
 * @return 收完之后那一项的落点（px）。**第一次就看不到**（这一项还没进视窗）返回 `-1`；
 *   补滚一帧之后仍读不到时返回 [targetPx] —— 那是「按差值补过了」的估计值，
 *   不是「没找到」（写 `-1` 的话调用方会把一次成功的定位当成失败）
 */
internal suspend fun LazyListState.settleRowBelow(
    index: Int,
    targetPx: Int,
): Int {
    withFrameNanos { }
    val arrived = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset ?: return -1
    if (arrived <= targetPx) return arrived
    scrollBy((arrived - targetPx).toFloat())
    //  再等一帧才读得到补完之后的真值；读不到就按「按差值补过」报，日志里不会说谎
    withFrameNanos { }
    return layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset ?: targetPx
}

/**
 * 「这个包在桌面上钉了哪几条」—— 向注入侧（`system_server`）问一趟。
 *
 * ## 为什么必须问注入侧
 *
 *  宿主自己那一趟（`LauncherApps.getShortcuts`）只拿得到 `动态 / 清单`；**钉住的那一档**按
 *  「钉它的那个桌面」存，宿主不是桌面宿主，一条也拿不到 —— 换系统身份走同一个 API 也一样
 *  （真机实测 `动态 3 / 清单 0 / 钉住 0`）。而注入侧住在 `system_server` 里，能直接读快捷方式
 *  服务手上的活数据（见 `inject/ShortcutRead`），这一趟就是去问它。
 *
 * ## 一问一答、有上限
 *
 *  发 [ModuleWire.ACTION_SHORTCUT_QUERY]，等 [WAIT_MS]；等不到就当这一趟没要到（返回 `null`），
 *  页面照旧只列本进程读到的那些 —— **不能因为对面没起来就让清单空掉**。
 *
 *  等待走 `delay` 而不是阻塞线程：调用方整段在 IO 上（见 `ActivityPickerList` 的
 *  `withContext(Dispatchers.IO)`），挂起期间那条线程可以先去干别的。
 */
internal object ShortcutPull {
    /** 等注入侧回包的上限（毫秒）。本地广播实测几十毫秒；这条只是「别无限等」的兜底。 */
    private const val WAIT_MS = 300L

    /** 这一趟的编号 —— 一次进页可能问好几条（翻应用、分身），回包靠它配对。 */
    private val seq = AtomicInteger()

    /**
     * @return `id → 名字`（顺序照系统给的）；`null` = 这一趟**没要到**（没注册上 / 没回包），
     *   与「要到了、但是空的」是两件事，页面据此决定日志怎么说
     */
    suspend fun pull(
        context: Context,
        pkg: String,
        userId: Int,
    ): List<Pair<String, String>>? {
        val req = seq.incrementAndGet().toString()
        //  回包在主线程的广播回调里写、在这次调用的线程上读 —— 用原子引用而不是普通变量：
        //  普通变量在这里既没有可见性保证，也说不清「读到的到底是哪一趟」。
        val reply = AtomicReference<List<Pair<String, String>>?>(null)
        //  **回包一到就醒**，不是固定睡满 [WAIT_MS]：本地广播实测几十毫秒就回来了，
        //  睡满等于给每一次进页白加 300ms（翻应用 / 分身一次要问好几条）。
        //  `WAIT_MS` 只当「别无限等」的上限。
        val done = CompletableDeferred<Unit>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action != ModuleWire.ACTION_SHORTCUT_RESULT) return
                    if (intent.getStringExtra(ModuleWire.EXTRA_SHORTCUT_REQ) != req) return
                    val ids = intent.getStringArrayListExtra(ModuleWire.EXTRA_SHORTCUT_IDS).orEmpty()
                    val labels = intent.getStringArrayListExtra(ModuleWire.EXTRA_SHORTCUT_LABELS).orEmpty()
                    reply.set(ids.mapIndexed { i, id -> id to (labels.getOrNull(i) ?: id) })
                    //  先写结果再放行；重复回包（补发那条）时 `complete` 是幂等的
                    done.complete(Unit)
                }
            }
        val registered =
            context.registerLocalReceiver(
                "快捷方式（系统身份那一趟没要到，只列本进程读到的）",
                receiver,
                ModuleWire.ACTION_SHORTCUT_RESULT,
            )
        if (!registered) return null
        val sent =
            PageWire.send(
                context,
                HostApp.PACKAGE,
                Intent(ModuleWire.ACTION_SHORTCUT_QUERY)
                    .putExtra(ModuleWire.EXTRA_SHORTCUT_REQ, req)
                    .putExtra(ModuleWire.EXTRA_SHORTCUT_PKG, pkg)
                    .putExtra(ModuleWire.EXTRA_SHORTCUT_USER, userId),
            )
        try {
            if (sent) withTimeoutOrNull(WAIT_MS) { done.await() }
        } finally {
            //  页面走了（协程被取消）也要把接收器摘掉，否则下一次进来会再注册一份
            runCatching { context.unregisterReceiver(receiver) }
        }
        return reply.get()
    }
}
