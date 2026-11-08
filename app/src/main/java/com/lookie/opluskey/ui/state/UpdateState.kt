package com.lookie.opluskey.ui.state

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.update.ReleaseInfo
import com.lookie.opluskey.core.update.UpdateSource
import com.lookie.opluskey.core.update.compareVersion
import com.lookie.opluskey.core.update.isNewer
import com.lookie.opluskey.core.update.parseLatestRelease
import com.lookie.opluskey.core.wire.NetReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
 *  [TOTAL_TIMEOUT_MS] 是**整趟**的上限：两路**并行**发起（见 [UpdateSource]），各自最多等到这个点，
 *  所以最坏等待是 10 秒、不是两倍的 10 秒。到点就收工 —— 检查更新拖住页面打开是最差的一种打扰，
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
        /** 已经是最新，或**这一趟自动检查**撞上了勾过「不再提示」的那一版。 */
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

    /**
     * 手上那条「有新版本」的记录 —— **活过「稍后」**。
     *
     *  与 [offer] 的区别：`offer` 只管那个弹窗（「稍后」一按就清空），而「有新东西」这件事
     *  用户可能只是这次不想看 —— 首页那条提示、两处红点，以及提示上那颗「去下载」都要还找得到这一版。
     *  清掉只有两种：勾着「不再提示」关窗（`skipUpdateVersion` 记下这一版）、点了「去下载」。
     */
    var pending by mutableStateOf<ReleaseInfo?>(null)
        private set

    /**
     * 有新版、而且用户**还没把它处理掉** —— 首页那条提示、首页「更多选项」与那一页「版本」行的红点跟它走。
     *
     *  由 [pending] 派生，**不另存一份布尔**：两者本来就是同一件事（手上有一条没处理的新版记录），
     *  各存一处必然分叉（清了弹窗忘清红点那种）。清掉的两种情形见 [pending]。
     */
    val hasUpdate: Boolean
        get() = pending != null

    /** 窗里那个「不再提示」勾选框。 */
    var dontRemind by mutableStateOf(false)

    /**
     * 这个窗里还给不给那颗「不再提示」勾选框。
     *
     *  已经记过这一版的人不该再被问一次：手动检查撞上自己勾过的版本时，窗照弹
     *  （那是用户在问，不是我们在打扰），但那一行**整块不出现** —— 再摆一个已经生效过的开关，
     *  按下去也什么都不变，只会让人以为「上次没记住」。
     */
    var showDontRemind by mutableStateOf(true)
        private set

    /** 这一趟是从哪一路取到的（日志与弹窗正文都用它）—— 没查到是 `null`。 */
    var source by mutableStateOf<UpdateSource?>(null)
        private set

    /** 一遍宿主进程只查一次：重启 / 换包之后才重来（与链路自检同一档）。 */
    private var ranThisProcess = false

    /** 这一趟进程还没查过吗。 */
    fun needsCheck(): Boolean = !ranThisProcess

    /**
     * 检查更新要的那几样**页面能力** —— 由调用方递进来，状态层不认识页面。
     *
     *  `ui.state` 不许反向 import `ui` 根（分层是单向的：`ui.screen → ui → ui.state`，
     *  见 `docs/architecture/decoupling.md`）。这一条以前是靠「直接收一个 `SettingsPage`」破的 ——
     *  状态对象于是知道了整个页面，而它其实只用三样：版本号、发一条网络请求、一个 Context
     *  （只给调试包的假 release 用）。
     */
    internal class Host(
        val context: Context,
        val versionName: () -> String?,
        val fetch: suspend (url: String, timeoutMs: Long) -> NetReply?,
    )

    /** 手动点「检查更新」那一行：**绕过**「每进程一次」的闸，每次都真去查。 */
    suspend fun checkManually(host: Host): CheckOutcome {
        ranThisProcess = true
        checking = true
        try {
            return runCheck(host, manual = true)
        } catch (e: CancellationException) {
            //  取消是结构化并发的一部分，原样抛（吞掉会让调用方的 join 永远等不到）
            throw e
        } catch (e: Throwable) {
            //  这一条跑在页面级 `LaunchedEffect` 里：异常漏出去会穿进 Recomposer，把整个 `:ui`
            //  进程的界面拖垮（一次网络/解析的意外不该有这种后果）。
            XLog.w("手动检查更新失败（已忽略）：${e.javaClass.simpleName}", e)
            return CheckOutcome.FAILED
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
    suspend fun checkUpdateOnce(host: Host) {
        if (ranThisProcess) return
        ranThisProcess = true
        checking = true
        try {
            runCheck(host, manual = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            XLog.w("检查更新失败（已忽略）：${e.javaClass.simpleName}", e)
        } finally {
            checking = false
        }
    }

    /**
     * 两个入口的本体 —— 每个出口各归一类：查不到、已最新、跳过、有新版。
     *
     * @param manual 这一趟是用户自己点的「检查更新」还是页面打开时那一趟。
     *   两者只差一件事：**「不再提示」压得住自动那一趟，压不住手动**（见 [applyRelease]）。
     */
    private suspend fun runCheck(
        host: Host,
        manual: Boolean,
    ): CheckOutcome {
        val current =
            host.versionName() ?: run {
                XLog.w("检查更新：读不到本机版本号，跳过")
                return CheckOutcome.FAILED
            }
        val skip = UiState.readPrefs().skipUpdateVersion
        //  调试包：`Settings.Global` 里塞了假 release 就直接当作「第一路已经取到」，
        //  后面的判新旧 / 判「不再提示」与真取到的那一路完全同一套（见 [debugMockRelease]）
        val mocked = debugMockRelease(host.context)
        if (mocked != null) return applyRelease(mocked, UpdateSource.GITHUB, current, skip, manual)
        val deadline = SystemClock.elapsedRealtime() + TOTAL_TIMEOUT_MS
        val fetched = fetchAllSources(host, deadline)
        if (fetched.isEmpty()) {
            XLog.w("检查更新：两路都没取到（超时 / 接口失败）")
            return CheckOutcome.FAILED
        }
        //  两路都回来之后才判：镜像可能比 GitHub 晚几秒，谁先回来算谁会把新版盖掉
        //  （见 `UpdateSource` 那段「两路问的不是同一件事」）。
        val (from, release) =
            fetched.reduce { best, next ->
                if (compareVersion(next.second.version, best.second.version) > 0) next else best
            }
        return applyRelease(release, from, current, skip, manual)
    }

    /**
     * 手上已经有一条发布记录时的判定 —— 判新旧、判「不再提示」，该弹就把窗挂上。
     *
     *  抽出来是为了让**假的那条**（[debugMockRelease]）和真取到的那条走同一套判据：
     *  否则调试包与正式包就是两条不同逻辑，测出来的结论不算数。
     *
     *  ## 「不再提示」压得住谁
     *
     *  它压的是**主动招呼**：页面打开时那一趟不再弹窗，首页那条提示也不点亮。
     *  但用户自己去点「检查更新」时照样弹 —— 那是他在问，不是我们在打扰；
     *  只是这一趟**不上首页提示**（他已经说过这版不想被提醒了）。
     */
    private fun applyRelease(
        release: ReleaseInfo,
        from: UpdateSource,
        current: String,
        skip: String?,
        manual: Boolean,
    ): CheckOutcome {
        val newer = isNewer(release.version, current)
        //  if / else 必须自己带括号：`… + if (newer) "有新版" else "已是最新" + "）"` 的运算符
        //  优先级会让右括号只挂到 else 那一支上，`newer` 为真时日志少半截。
        XLog.w(
            "检查更新：${from.id} 取到 ${release.version}（本机 $current，" +
                (if (newer) "有新版" else "已是最新") + "）",
        )
        if (!newer) return CheckOutcome.LATEST
        val skipped = release.version == skip
        if (skipped && !manual) {
            XLog.w("检查更新：${release.version} 这一版用户勾过「不再提示」，这一趟不弹")
            return CheckOutcome.LATEST
        }
        if (skipped) XLog.w("检查更新：${release.version} 勾过「不再提示」，手动这一趟照弹、不上首页提示")
        source = from
        dontRemind = false
        //  勾过这一版的人不再被问第二次（见 [showDontRemind]）
        showDontRemind = !skipped
        offer = release
        //  首页那条提示在这里点亮（勾过「不再提示」就不点）；「稍后」关窗时**不**清 —— 见 [pending]
        pending = if (skipped) null else release
        return CheckOutcome.OFFERED
    }

    /**
     * 两路**同时**发起，等到都回来（或各自到点）为止。
     *
     *  为什么不在第一路回来时就收工：两路给的版本不一定一样（镜像可能慢几拍），
     *  先回来的若是旧的那条，用户就看不到真正的新版。并行省的是「串行相加」那段时间，
     *  最坏仍是 [TOTAL_TIMEOUT_MS] 一整趟 —— 不会变成两倍。
     *
     *  取不到的那一路直接不进结果表（它自己已经打过 W 日志），两路都空才由调用方判失败。
     */
    private suspend fun fetchAllSources(
        host: Host,
        deadline: Long,
    ): List<Pair<UpdateSource, ReleaseInfo>> =
        coroutineScope {
            UpdateSource.entries
                .map { candidate ->
                    async {
                        val left = deadline - SystemClock.elapsedRealtime()
                        if (left <= 0) {
                            XLog.w("检查更新：${candidate.id} 还没发出就到上限了（${TOTAL_TIMEOUT_MS}ms）")
                            return@async null
                        }
                        fetchRelease(host, candidate, left)?.let { candidate to it }
                    }
                }.awaitAll()
                .filterNotNull()
        }

    /** 从一路取并解析；取不到返回 `null`（另一路照常跑，不影响）。 */
    private suspend fun fetchRelease(
        host: Host,
        source: UpdateSource,
        timeoutMs: Long,
    ): ReleaseInfo? {
        val reply = host.fetch(source.apiUrl, timeoutMs) ?: return null
        if (!reply.ok) {
            XLog.w("检查更新：${source.id} 没取到（status=${reply.status} ${reply.error}）")
            return null
        }
        //  Gitee 不给 `html_url`，地址由这一路自己拼（见 `UpdateSource.tagPageUrl`）
        return parseLatestRelease(reply.body) { tag -> source.tagPageUrl(tag) }
    }

    /** 关掉窗（「稍后」）。勾了「不再提示」就记住这一版。 */
    fun dismiss() {
        val info = offer ?: return
        //  勾着「不再提示」才算把这一版处理掉了；光是「稍后」还留着提示与红点（见 [pending]）
        if (dontRemind && UiState.writeSkipUpdateVersion(info.version)) {
            XLog.w("检查更新：记下「不再提示」${info.version}")
            pending = null
        }
        offer = null
        dontRemind = false
    }

    /**
     * 把手上这一版重新摆回弹窗 —— 首页那条提示点的就是这一下。
     *
     *  与 [takeDownload] 的分界：这一下**只是再看一眼**，什么都没处理掉 —— [pending] 不动，
     *  首页那条提示与两处红点照旧；真正的处置在窗里那两颗按钮上。
     */
    fun reopen() {
        val info = pending ?: return
        dontRemind = false
        //  手上这一版是「还没被勾过」的（勾过的不会进 [pending]），照常给勾选框
        showDontRemind = true
        offer = info
    }

    /** 点了「去下载」：同样按勾选框记一笔，然后把这一版从手上放掉。由窗里那颗按钮调。 */
    fun takeDownload(): String? {
        val info = offer ?: return null
        if (dontRemind) UiState.writeSkipUpdateVersion(info.version)
        //  已经去拿了，提示与红点都不必再挂
        pending = null
        offer = null
        dontRemind = false
        return info.pageUrl.takeIf { it.isNotEmpty() }
    }
}

/**
 * 调试包专用的「假 release」—— 值放在 `Settings.Global` 里，**只有 debug 构建认**。
 *
 * ## 为什么留这个口子
 *
 *  线上 latest 与本机同版时（`1.3.3` 对 `1.3.3-debug`），[isNewer] 判「已是最新」，
 *  更新弹窗在真机上**根本没有办法出现** —— 而它的排版（长说明、按钮、勾选框）恰恰只能真机看。
 *  接口地址是硬编码的 https，本地起不了替身（注入侧只认 https，见 `inject/NetChannel`），
 *  所以只能在这里开一个只在调试包里生效的读口。
 *
 * ## 写法
 *
 *  值与接口返回**同一套 JSON**，直接复用 [parseLatestRelease]，不另立格式：
 *
 *  ```
 *  adb shell settings put global opluskey_mock_update \
 *      '{"tag_name":"v1.3.4","body":"更新说明","html_url":"https://…"}'
 *  adb shell settings delete global opluskey_mock_update     # 测完删掉
 *  ```
 *
 *  正式包第一条就返回 `null` —— 这个键对发布版没有任何作用。
 */
private fun debugMockRelease(context: Context): ReleaseInfo? {
    //  「是不是调试包」看模块自己清单里的 `android:debuggable`。不用 `BuildConfig.DEBUG`：
    //  本工程没开 `buildConfig` 特性（AGP 8 默认关）；也不看 `versionName` 的 `-debug` 后缀 ——
    //  那是**版本**档位，改个后缀就能绕过，而 `debuggable` 由构建类型决定，正式包一定是 false。
    val debuggable =
        runCatching {
            val info = context.packageManager.getApplicationInfo(ModuleInfo.PACKAGE, 0)
            (info.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }.getOrDefault(false)
    if (!debuggable) return null
    val raw =
        runCatching { Settings.Global.getString(context.contentResolver, MOCK_UPDATE_KEY) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
    return parseLatestRelease(raw)?.also {
        XLog.w("检查更新：命中 debug mock（$MOCK_UPDATE_KEY → ${it.version}）")
    }
}

/** [debugMockRelease] 读的那个 `Settings.Global` 键（只在 debug 包生效）。 */
private const val MOCK_UPDATE_KEY = "opluskey_mock_update"
