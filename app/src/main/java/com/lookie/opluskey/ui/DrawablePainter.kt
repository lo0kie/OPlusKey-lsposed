package com.lookie.opluskey.ui

import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * 直接画 [Drawable] 的 [Painter] —— **不建中间位图**。
 *
 * ## 为什么不用 `Bitmap` 中转
 *
 *  早先在 `AppTile` 里是「`createBitmap` → `Canvas` → `drawable.draw` → `asImageBitmap`」：
 *  每滑过一个应用就在 Java 堆与 Native 堆各分配一张 48dp 见方的位图（还有一次整块拷贝），
 *  列表几百项时内存曲线跟着滚动来回抖，GC 也被喂饱。
 *
 *  这里在 `onDraw` 里直接拿到 `nativeCanvas` 把 Drawable 画上去 —— Compose 本来就是
 *  「一层层往 canvas 上画」，中间那张位图纯属多余的搬运。矢量图 / 自适应图标也因此按当前
 *  画布尺寸实时光栅化，不会因为取样尺寸与渲染尺寸不等而发虚。
 *
 * ## 为什么只实现「画」，不实现 alpha / 色彩滤镜
 *
 *  `applyAlpha` / `applyColorFilter` 的语义是改**这个 painter 的**绘制参数，而这里的
 *  Drawable 是从缓存里取出来的、**可能被多行共用**（`AppIconCache.put`）。改它的
 *  `alpha` 会把状态留在那个对象上，别的行下次画出来就跟着变。返回 `false` 表示「本 painter
 *  不支持这一项」，Compose 会在有 `alpha` modifier 时退回分层绘制。
 *
 * ## 边界（bounds）会被改
 *
 *  `onDraw` 里改的是 Drawable 自己的 bounds —— 这是 `Drawable` 唯一的绘制接口，绕不开。
 *  绘制都在主线程上串行发生，**每次画之前重设一遍**，所以共用同一个 Drawable 也不会串。
 */
internal class DrawablePainter(
    private val drawable: Drawable,
) : Painter() {
    /** 内容固有尺寸；`-1` 表示这一类 Drawable 没有固有尺寸（Compose 侧按「未知」处理）。 */
    override val intrinsicSize: Size
        get() {
            val w = drawable.intrinsicWidth
            val h = drawable.intrinsicHeight
            return if (w < 0 || h < 0) Size.Unspecified else Size(w.toFloat(), h.toFloat())
        }

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }

    // `true` = 由本 painter 自己处理并**不改变绘制结果**。这两项都返回 false：
    //  真去改 Drawable 会把状态留在共用对象上（见类注释）。
    override fun applyAlpha(alpha: Float): Boolean = false

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean = false

    /**
     * RTL：`Drawable` 自己认 `setLayoutDirection`，但同一个缓存对象同样可能被两种方向共用，
     * 所以这里只画它的**当前**方向，不改它 —— 应用图标本身也不随方向翻转。
     */
    override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean = false
}
