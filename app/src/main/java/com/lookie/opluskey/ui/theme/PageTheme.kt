package com.lookie.opluskey.ui.theme

import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.DARK_SCHEME
import com.lookie.opluskey.ui.model.LIGHT_SCHEME
import com.lookie.opluskey.ui.model.NIGHT_MODE_KEY
import com.lookie.opluskey.ui.model.NIGHT_POLL_MS
import com.lookie.opluskey.ui.model.NIGHT_RETRY_MS
import kotlinx.coroutines.launch

/**
 * 主题：深浅色判定/取色与系统主题同步。
 *
 * 从 `SettingsPage.kt` 按职责拆出（2026-10-12 重构）：成员改为 [SettingsPage]
 * 的扩展/同包顶层声明，调用点零改动。
 */
/** 页面底色 —— COUI 色板的 `surface`（深色 `#000000`、浅色 `#F0F1F2`，与系统设置页一致）。 */
internal val SettingsPage.pageBackgroundColor: Int
    get() = colorsFor(dark.value).surface.toArgb()

internal fun SettingsPage.colorsFor(night: Boolean) =
    if (night) DARK_SCHEME else LIGHT_SCHEME

/**
 * 系统当前是不是深色 —— 读系统全局配置；不能用 Activity / Application 的 `configuration`
 * （宿主声明了 `configChanges=uiMode`）。
 */
internal fun SettingsPage.systemNight(): Boolean = try {
    val uiMode = Resources.getSystem().configuration.uiMode
    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
} catch (e: Throwable) {
    XLog.d("读系统深浅失败：${e.message}")
    dark.value
}

/** 系统切深浅时同步给 hook 侧（顶栏与状态栏配色）。 */
@Composable
internal fun SettingsPage.SyncToHost() {
    LaunchedEffect(dark.value) { onSystemThemeChanged?.invoke() }

    // 真机量下来两边轮询延迟随机落在 0.14s~0.56s（相位运气），补这条推送把最常见的「手动切」
    // 压成即时。回调里不读设置项的值、只当「有事发生」的信号，权威仍是 [systemNight]（读系统
    // 全局配置）；也不能只查一次 —— 写设置项与全局配置真的翻过去不是同一时刻。
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        var retry: kotlinx.coroutines.Job? = null
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                XLog.d("推送：$NIGHT_MODE_KEY 变了（连查 ${NIGHT_RETRY_MS.size} 次）")
                retry?.cancel()
                retry = scope.launch {
                    for (wait in NIGHT_RETRY_MS) {
                        if (wait > 0) kotlinx.coroutines.delay(wait)
                        if (syncNight("ui_night_mode 变了")) break
                    }
                }
            }
        }
        val registered = try {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(NIGHT_MODE_KEY),
                false,
                observer,
            )
            true
        } catch (e: Throwable) {
            XLog.w("监听系统深浅设置失败（只剩轮询）：${e.message}")
            false
        }
        onDispose {
            retry?.cancel()
            if (registered) {
                runCatching { context.contentResolver.unregisterContentObserver(observer) }
            }
        }
    }

    // 「定时 / 日落到日出」那种设置项不变的切换不会触发上面的监听，所以留着这条轮询
    // （间隔 [NIGHT_POLL_MS]，读的是系统配置上的一个字段，代价可忽略）。
    // ⚠️ 它会常驻：露出宿主原版页面只是把 pageView 置 GONE，组合并没有被销毁，而自建的
    // owner 又停在 RESUMED（规则禁推 DESTROYED）—— 别拿「不可见就停」当前提去改它。
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(NIGHT_POLL_MS)
            syncNight("轮询")
        }
    }
}

/**
 * 对一次「系统深浅可能变了」的账 —— ①的推送与②的轮询共用这一个入口。
 *
 * @param from 触发来源（日志用；排查「到底哪条路先醒」时全靠它）
 * @return 这次真的翻过去了（①据此决定还要不要再查）
 */
internal fun SettingsPage.syncNight(from: String): Boolean {
    val now = systemNight()
    if (now == dark.value) return false
    XLog.w("系统深浅变化（$from）：night=$now")
    dark.value = now
    return true
}
