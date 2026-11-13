package com.lookie.opluskey.ui.model.icon

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.FluidCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 把一颗 Tabler 字形画成 png，写进宿主的共享目录 —— `fluid:` 那边只管把这个文件转成 uri。
 *
 * ## 为什么渲染放在界面层
 *
 *  图标源是模块**已经在用**的 Tabler 字形，它是 Compose 的 `ImageVector`：只有界面层
 *  （`com.oplus.gesture.ui`）带 Compose 运行时，而执行 `fluid:` 的宿主进程没有 ——
 *  在那边引等于 `NoClassDefFoundError`。所以这里画好、落成文件，宿主侧只认文件。
 *
 * ## 形态
 *
 *  36×36、**透明底**、纯白描边：胶囊本身有底色（系统那颗 `op_ic_silence.svg` 也是这么做的，
 *  只有白色 path、没有底板），所以不铺底。`tintColor` 给白，是把 Tabler 自带的黑色描边整体刷白。
 *
 * ## 什么时候画
 *
 *  **只在用户点中一颗的时候画**（选择器那道网格，见 `FluidIconPicker`）：命令里记 id，
 *  这里按 id 落一个 `okey-fluid-<id>.png`，宿主执行时按同一个 id 找回来。
 *  「没选图标」不留文件、也不画默认那颗 —— 那种命令（`fluid:正文`）执行时根本不要图标。
 *
 * ## 为什么是 `suspend`，且自己 `withContext`
 *
 *  画一张位图再落盘是几十毫秒起步（首次还有 `mkdirs`），而唯一的调用点是网格里那一下点击
 *  —— 落在主线程上就是当场掉帧。**让函数自己保证这件事**，调用方不必记得包一层：
 *  上一版把 `withContext` 放在调用点（首页那次预热）里，那个调用点撤掉之后，这一段就
 *  裸在主线程上了（`tools/audit-perf.py` 的判据 1 会直接报出来）。
 *
 *  `painter` 只能在 Composable 里 `rememberVectorPainter` 出来，所以由调用方给。
 */
internal suspend fun exportPainter(
    ctx: Context,
    painter: Painter,
    density: Density,
    layout: LayoutDirection,
    name: String,
): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val size = Size(FluidCard.ICON_SIZE.toFloat(), FluidCard.ICON_SIZE.toFloat())
            val image = ImageBitmap(FluidCard.ICON_SIZE, FluidCard.ICON_SIZE)
            CanvasDrawScope().draw(density, layout, Canvas(image), size) {
                with(painter) { draw(size) }
            }
            //  落点：宿主的 `files/share_images/` —— 同包同 uid，界面层进程有写权限。
            val dir = File(ctx.filesDir, FluidCard.SHARE_DIR)
            if (!dir.exists() && !dir.mkdirs()) return@withContext false
            FileOutputStream(File(dir, name)).use {
                whiten(image.asAndroidBitmap()).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            true
        } catch (e: Throwable) {
            //  路径直接拼，不再为了一句日志去 stat 一次目录（那又是一处同步 IO）
            XLog.w("写胶囊图标失败（${FluidCard.SHARE_DIR}/$name）", e)
            false
        }
    }

/**
 * 把画好的图标整体刷成白色（保留 alpha）。
 *
 *  Tabler 的 `ImageVector` 自带的是**黑色描边**，而 `rememberVectorPainter(image)` 那个重载
 *  没有 `tintColor` 参数（带 tint 的那个重载要的是 `Dp` 尺寸）。与其去凑重载，不如画完再刷：
 *  `SRC_IN` 只覆盖不透明像素，描边变白、背景仍然透明。
 */
private fun whiten(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawBitmap(src, 0f, 0f, null)
    canvas.drawColor(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
    return out
}
