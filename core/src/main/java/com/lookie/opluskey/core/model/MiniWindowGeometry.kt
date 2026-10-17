package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.log.XLog

/**
 * **按机型标定的小窗几何** —— 展开档小窗的落点与缩放，**一台机器一档**。
 *
 * ## 为什么键是「机型」而不是 ROM
 *
 * 这两个数由**屏幕尺寸与 dpi** 决定（本机 `1272×2772 @560dpi` 上量出来的），换一块屏就得重新量；
 * 而 [RomProfile] 那张表按 **ROM 系列**分档（同一版 ROM 在不同机型上落点相同）。
 * 两张表的键不同，正是这个原因 —— 别把它们并成一张。
 *
 * ## 为什么收成一张表
 *
 * 宿主侧（下发几何）与注入侧（宿主没带参数时的兜底）原来各写一份**同一组数**：
 * 改一处漏一处时，表现是「宿主下发的与注入侧兜底的对不上」—— 而兜底那条路只在宿主没带参数时
 * 才走得到，平时完全看不出来。
 *
 * ## 加一台机型要做什么
 *
 * 1. 这里加一个 [Slot]（`model` 与 [DeviceSupport] 同口径：`Build.MODEL`）；
 * 2. 在 [DeviceSupport.ADAPTED] 里加那一行（那道闸管「能不能注入」，与本表各管一件事）；
 * 3. 真机上量一遍：拿**原生小窗**的 `Task.mBounds` 反查配置矩形，取法与两个坑见
 *    `docs/zoom-window.md` 与 `docs/device-findings.md` 附七。
 *
 * ## 本机没适配时「照最后一档试」
 *
 * 与 `RomProfile` 里「接管点 / 反射面」同一档待遇（而不是内置命令落点那种「不猜」）：
 * 几何猜错的表现是**窗口位置或大小不对**（看得见、能改），不会去动别的东西；而 debug 包关掉了
 * 适配闸，开发要在任意机器上能试。落档时会打一行 `w` 说明用的是哪一档。
 */
object MiniWindowGeometry {
    /**
     * 一台机器的小窗几何。
     *
     * @param model `android.os.Build.MODEL` 的真机实测值（与 [DeviceSupport] 同口径）
     * @param label 给人看的机型名（日志用）
     * @param verified 真机验证记录（日期 + 量法出处）
     * @param left / @param top / @param right / @param bottom **应用配置矩形**的四个边 ——
     *   不是屏幕矩形：系统会把它的尺寸**再乘一次 [scale]** 才是屏幕上的实际大小
     *   （`1272 × 2261 × 0.8017 = 1020 × 1812`，与真机一致）
     * @param scale 显示缩放
     */
    @Suppress("LongParameterList")
    class Slot(
        val model: String,
        val label: String,
        val verified: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val scale: Float,
    ) {
        /**
         * 应用配置矩形 —— **不是**屏幕矩形。
         *
         * 踩过的坑：曾经直接填**屏幕矩形**（1020×1812），结果被系统又缩了一次
         * （`1020 × 0.8017 = 817`），窗口整整齐齐小了 0.8017 倍，表现为「小窗是对的，
         * 但比 ColorOS 原生的小一圈」。正确值是拿真机「原生小窗」的 `dumpsys activity activities`
         * → `Task.mBounds` 反查出来的（见 `docs/device-findings.md` 附七）。
         *
         * 落点（left / top）是**直接照搬到屏幕坐标**的、不含缩放，所以两头是同一个数。
         *
         * 不要拿 `getMiniFlexibleTaskInfo` 的值代替 —— 那是**收起档**的几何
         *（约 `Rect(898,168-1203,710)` / `0.2397`），会把窗口缩成右上角一条。
         */
        val bounds: android.graphics.Rect get() = android.graphics.Rect(left, top, right, bottom)
    }

    /** **各机型的小窗几何 —— 加一台机器就在这里加一个 [Slot]**。 */
    private val SLOTS: List<Slot> =
        listOf(
            Slot(
                model = "PLK110",
                label = "OnePlus 15（PLK110）",
                verified = "1272×2772 @560dpi；2026-10-14 按原生小窗 Task.mBounds 反查",
                left = 126,
                top = 319,
                right = 1398,
                bottom = 2580,
                scale = 0.8017f,
            ),
        )

    /**
     * 本机这一档；`null` = 这台机器还没量过。
     *
     * 进程内算一次：机型不会中途变，而这两个数在**每次播小窗动画**时都要读。
     */
    val matched: Slot? by lazy {
        val model = DeviceSupport.currentModel
        SLOTS.firstOrNull { it.model.equals(model, ignoreCase = true) }
    }

    /**
     * 实际用的那一档 —— 本机没量过时**照最后一档试**并打一行 `w`（理由见类注释）。
     *
     * `SLOTS` 是常量表、不会是空的。
     */
    val effective: Slot by lazy {
        val model = DeviceSupport.currentModel
        matched
            ?: SLOTS.last().also {
                XLog.w("本机机型 $model 不在小窗几何表里 ⇒ 照 ${it.label} 那一档试（窗口大小可能不对）")
            }
    }

    /** 本机档位的**一句话描述** —— 安装期判据行用它。 */
    fun describeCurrent(): String {
        val model = DeviceSupport.currentModel
        val slot = matched ?: return "未标定（本机机型 ${model.ifEmpty { "读不出来" }}）"
        return "${slot.label}（${slot.left},${slot.top}-${slot.right},${slot.bottom} × ${slot.scale}）"
    }
}
