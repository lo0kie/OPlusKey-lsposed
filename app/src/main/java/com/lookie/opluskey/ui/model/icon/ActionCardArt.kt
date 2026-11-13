package com.lookie.opluskey.ui.model.icon

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.FluidCard
import com.lookie.opluskey.core.model.actionTokens
import com.lookie.opluskey.core.present.ActionCatalog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.text.actionCellIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把这份配置里**可能被执行到的动作**逐个渲染成 png，写进宿主的共享目录 ——
 * 「执行时提示走流体云」那一档执行时按 token 找回来（`FluidCard.actionNameOf`）。
 *
 * ## 为什么挂在设置页根上
 *
 *  执行提示发生在**宿主主进程**，而动作图标是 Compose 的 `ImageVector`，那边没有 Compose
 *  运行时（与 `fluid:` 图标同一条理由，见 `FluidCardArt`）。所以只能在这个页面进程里先画好、
 *  落成文件；宿主侧只认文件。
 *
 *  挂在根上而不是某个开关旁边：预热要覆盖**整份配置**（用户可能在这颗键上绑 A、那颗绑 B），
 *  而配置每次写盘都会换一个新实例 —— 这里跟着它重算一份 token 表，新绑上的动作立刻就有图。
 *
 * ## 已经画过的不再画
 *
 *  文件名由 token 决定，token 不变 ⇒ 图标不变。重进页面 / 编辑别的项时不重复画那几十张
 *  （每张都要走一次 Canvas + PNG 压缩）。唯一的例外是 `cmd:` 改了命令类型（单条 ↔ 多条）——
 *  那种情况下 token 不变、图标会短暂停在旧形状，直到下次换绑或清数据；它不影响胶囊弹出来。
 */
@Composable
internal fun SettingsPage.PrewarmActionIcons() {
    val config = configState.config.value
    val commands = configState.commands.value
    val ctx = LocalContext.current
    //  `actionTokens()` 是全项目唯一那份遍历（改名 / 删命令也读它）：这里只把「无操作 / 跟随」
    //  滤掉 —— 它们不弹执行提示，没有图可画。去重交给 `Set`。
    //
    //  **用解析后的 token 而不是配置里的原字面量**：执行侧拿的是 `Action.parse(...)` 出来的
    //  那个对象（`action.token`），有些档位会在构造时归一（百分比夹到 0..100 等）。两边不一致
    //  就对不上文件名 —— 表现是「图标永远是通用那张」。
    val tokens =
        remember(config) {
            config
                .actionTokens()
                .mapNotNull { raw -> Action.parse(raw).takeIf { it.isBound }?.token }
                .toSet()
        }
    tokens.forEach { token ->
        key(token) { ActionIconExport(token, commands) }
    }
    //  已经不在配置里的那几张图顺手清掉：文件名由 token 决定，改过命令内容的旧 token 再也不会
    //  被用到，留着只是让共享目录只增不减。一次配置一份要留的名单，IO 在后台。
    LaunchedEffect(tokens) {
        withContext(Dispatchers.IO) {
            pruneActionIcons(ctx, tokens.mapTo(HashSet()) { FluidCard.actionNameOf(it) })
        }
    }
}

/**
 * 一个 token 一张图 —— 每个 token 自己一份 `remember` / `LaunchedEffect`。
 *
 *  在调用点循环里直接 `rememberVectorPainter` 是不行的：列表长度变化会打乱 Compose 的槽位
 *  对应（`key(token)` 才把「第几个」换成「是哪一个」）。
 */
@Composable
private fun SettingsPage.ActionIconExport(
    token: String,
    commands: List<Command>,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val layout = LocalLayoutDirection.current
    //  有父格时用那一行自己的字形（与步骤行 / 绑定列表同一份取法）
    val painter = rememberVectorPainter(actionCellIcon(ActionCatalog.actionOf(token), commands))
    LaunchedEffect(token, commands) {
        exportPng(ctx, painter, density, layout, FluidCard.actionNameOf(token))
    }
}

/**
 * [exportPainter] 加一道「已经有了就不重画」。
 *
 *  查文件与画图都自己 `withContext(Dispatchers.IO)`：`LaunchedEffect` 恢复在主线程上，
 *  在这里裸着 stat 一次就是主线程 IO（`tools/audit-perf.py` 判据 1 会直接报出来）。
 */
private suspend fun exportPng(
    ctx: Context,
    painter: Painter,
    density: Density,
    layout: LayoutDirection,
    name: String,
): Boolean =
    withContext(Dispatchers.IO) {
        val file = File(File(ctx.filesDir, FluidCard.SHARE_DIR), name)
        if (file.isFile) return@withContext true
        exportPainter(ctx, painter, density, layout, name)
    }

/**
 * 删掉**已经不在配置里**的那几张动作图 —— 只认 [FluidCard.ACTION_PREFIX] 那一族。
 *
 *  `fluid:` 那一档（`okey-fluid-<id>.png`）**不碰**：它的名字来自用户在命令里点选的字形，
 *  与「配置里还有没有这个动作」无关。
 *
 *  跟着 `tokens` 的 `LaunchedEffect` 跑在 IO 上；删错一张的后果只是下一次进设置页重画一遍，
 *  所以这里不为「列目录失败」做任何补偿。
 */
private fun pruneActionIcons(
    ctx: Context,
    keep: Set<String>,
) {
    val dir = File(ctx.filesDir, FluidCard.SHARE_DIR)
    val stale =
        dir.listFiles { f -> f.isFile && f.name.startsWith(FluidCard.ACTION_PREFIX) && f.name !in keep }
            ?: return
    if (stale.isEmpty()) return
    var gone = 0
    for (f in stale) {
        if (f.delete()) gone++
    }
    if (gone > 0) XLog.d("动作图标：清掉 $gone 张已经不在配置里的旧图")
}
