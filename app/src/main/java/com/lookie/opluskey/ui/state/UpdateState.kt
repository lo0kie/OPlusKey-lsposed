package com.lookie.opluskey.ui.state

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.update.ReleaseInfo
import com.lookie.opluskey.core.update.UpdateSource
import com.lookie.opluskey.core.update.isNewer
import com.lookie.opluskey.core.update.parseLatestRelease
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.fetchText
import com.lookie.opluskey.ui.moduleVersionName

/**
 * 「检查更新」的那一格状态 —— 一趟宿主进程只查一次，查到新版本就把窗弹出来。
 *
 *  ## 为什么和链路自检一起做
 *
 *  两者都是「这一页打开时问一句外面」的事，也都是「有结论才说话」：链路自检回答「模块跑起来没有」，
 *  这个回答「有没有新版本」。放在同一个启动自检里，用户不必知道「还有个地方要去手动点一下」。
 *
 *  ## 上限 10 秒
 *
 *  [TOTAL_TIMEOUT_MS] 是**整趟**的上限，不是每一路的上限：两路依次试（见 [UpdateSource]），
 *  后一路只能用前一路剩下的时间。到点就收工 —— 检查更新拖住页面打开是最差的一种打扰，
 *  而这件事实在没查到也毫无损失（下次进页还会再查）。
 *
 *  ## 只在「有新版」时才弹
 *
 *  「已经是最新」「没网」「接口失败」一律不出声（各自一行 W 日志）：
 *  一次打开弹一个「你已是最新版本」的窗，比不查更烦人。
 */
internal object UpdateState {
    /** 整趟的上限（毫秒）—— 用户口径「最长等待 10 秒」。 */
    private const val TOTAL_TIMEOUT_MS = 10_000L

    /** 一趟检查的结局 —— 手动那一行据此给回执；自动那一趟只看「有没有弹窗」。 */
    enum class CheckOutcome {
        /** 这一路没取到（网络 / 接口），接着试下一路。 */
        UNAVAILABLE,

        /** 已经是最新，或这一版勾过「不再提示」。 */
        LATEST,

        /** 查到了更新的版本，弹窗已挂上。 */
        OFFERED,

        /** 两路都没取到 / 读不到本机版本号 / 超时。 */
        FAILED,
    }

    /** 正在查（界面上「运行状态」那一行可以据此显示「正在检查更新」）。 */
    var checking by mutableStateOf(false)
        private set

    /** 查到的新版本；`null` = 没查到 / 已是最新 / 用户说了「不再提示」。 */
    var offer by mutableStateOf<ReleaseInfo?>(null)
        private set

    /** 窗里那个「不再提示」勾选框。 */
    var dontRemind by mutableStateOf(false)

    /** 这一趟是从哪一路取到的（日志与弹窗正文都用它）—— 没查到是 `null`。 */
    var source by mutableStateOf<UpdateSource?>(null)
        private set

    /** 一遍宿主进程只查一次：重启 / 换包之后才重来（与链路自检同一档）。 */
    private var ranThisProcess = false

    /** 这一趟进程还没查过吗。 */
    fun needsCheck(): Boolean = !ranThisProcess

    /** 手动点「检查更新」那一行：**绕过**「每进程一次」的闸，每次都真去查。 */
    suspend fun checkManually(page: SettingsPage): CheckOutcome {
        ranThisProcess = true
        checking = true
        try {
            return runCheck(page)
        } finally {
            checking = false
        }
    }

    /**
     * 跑一趟检查（[SettingsPage.checkUpdateOnce] 的入口，页面级副作用调它）。
     *
     *  失败一律只记日志：这条路上任何一环坏掉都不该让页面表现异常 ——
     *  最坏的结果是「这次没提示新版本」。
     */
    suspend fun checkUpdateOnce(page: SettingsPage) {
        if (ranThisProcess) return
        ranThisProcess = true
        checking = true
        try {
            runCheck(page)
        } finally {
            checking = false
        }
    }

    /** [checkUpdateOnce] 的本体 —— 每个出口各归一类：查不到、已最新、跳过、有新版。 */
    private suspend fun runCheck(page: SettingsPage): CheckOutcome {
        val current =
            page.moduleVersionName() ?: run {
                XLog.w("检查更新：读不到本机版本号，跳过")
                return CheckOutcome.FAILED
            }
        val skip = UiState.readPrefs().skipUpdateVersion
        val deadline = SystemClock.elapsedRealtime() + TOTAL_TIMEOUT_MS
        for (candidate in UpdateSource.entries) {
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0) {
                XLog.w("检查更新：整趟超时（${TOTAL_TIMEOUT_MS}ms），不再试下一路")
                return CheckOutcome.FAILED
            }
            val outcome = page.offerCandidate(candidate, current, skip, left)
            if (outcome != CheckOutcome.UNAVAILABLE) return outcome
        }
        XLog.w("检查更新：两路都没取到（网络 / 接口 / 仓库地址）")
        return CheckOutcome.FAILED
    }

    /**
     * 试一路：取、解析、判新旧、判「不再提示」，决定要不要把窗弹出来。
     *
     * @return [CheckOutcome.UNAVAILABLE] = 这一路没取到（网络 / 接口），接着试下一路；
     *   其余 = 这一路已经给出最终结论（弹了 / 已最新 / 该跳过），**不再试下一路**。
     */
    private suspend fun SettingsPage.offerCandidate(
        candidate: UpdateSource,
        current: String,
        skip: String?,
        timeoutMs: Long,
    ): CheckOutcome {
        val release = fetchRelease(candidate, timeoutMs) ?: return CheckOutcome.UNAVAILABLE
        val newer = isNewer(release.version, current)
        XLog.w(
            "检查更新：${candidate.id} 取到 ${release.version}（本机 $current，" +
                if (newer) "有新版" else "已是最新" + "）",
        )
        if (!newer) return CheckOutcome.LATEST
        if (release.version == skip) {
            XLog.w("检查更新：${release.version} 这一版用户勾过「不再提示」，不弹")
            return CheckOutcome.LATEST
        }
        source = candidate
        dontRemind = false
        offer = release
        return CheckOutcome.OFFERED
    }

    /** 从一路取并解析；取不到返回 `null`（调用方接着试下一路）。 */
    private suspend fun SettingsPage.fetchRelease(
        source: UpdateSource,
        timeoutMs: Long,
    ): ReleaseInfo? {
        val reply = fetchText(source.apiUrl, timeoutMs) ?: return null
        if (!reply.ok) {
            XLog.w("检查更新：${source.id} 没取到（status=${reply.status} ${reply.error}）")
            return null
        }
        return parseLatestRelease(reply.body)
    }

    /** 关掉窗（「稍后」）。勾了「不再提示」就记住这一版。 */
    fun dismiss() {
        val info = offer ?: return
        if (dontRemind && UiState.writeSkipUpdateVersion(info.version)) {
            XLog.w("检查更新：记下「不再提示」${info.version}")
        }
        offer = null
        dontRemind = false
    }

    /** 点了「去下载」：同样按勾选框记一笔，然后由界面去开浏览器。 */
    fun takeDownload(): String? {
        val info = offer ?: return null
        if (dontRemind) UiState.writeSkipUpdateVersion(info.version)
        offer = null
        dontRemind = false
        return info.pageUrl.takeIf { it.isNotEmpty() }
    }
}
