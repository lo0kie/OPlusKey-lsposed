package com.lookie.opluskey.shizuku

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import com.lookie.opluskey.core.log.XLog
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Shizuku 那三件事的唯一实现：**查可用、跑一条命令、申请一次授权**。
 *
 * ## 为什么不带进程假设
 *
 *  这三件事全都只跟「这个进程的 uid 有没有被授权」有关，与进程是谁无关 —— 于是宿主进程
 *  （Sui 那一路：zygisk 把 binder 注进了每个 app 进程，本进程自己 `Sui.init` 就能拿到）与
 *  模块进程（真 Shizuku 那一路：binder 由管理器推到本模块的 provider 里）可以共用这一份，
 *  不必为「在哪跑」各写一遍。
 *
 *  调用方负责先把 binder 弄到手（见 `ShizukuHost.ensureBinder`），这里只管用。
 *
 * ## 为什么直接照 AIDL 调，而不是 `Shizuku.newProcess`
 *
 *  `api:13.1.5` 把 `newProcess` 收成了私有方法（`javap` 核过），公开的只有服务 binder：
 *  `IShizukuService.newProcess` 返回一个 `IRemoteProcess`，给的是一对 `ParcelFileDescriptor`
 *  加 `waitForTimeout` —— 够用，而且服务端版本一变是 `RemoteException` 而不是「静默不做事」。
 */
internal object ShizukuExec {
    /** 执行异常 / 超时 —— 与真实的退出码分开（真实退出码非负）。 */
    const val FAIL_CODE = -1002

    const val TIMEOUT_CODE = -1003

    /** 命令要跑多久算超时（秒）—— 与宿主侧 `ShellRunner` 那一档同量级。 */
    private const val TIMEOUT_SEC = 30L

    /** 子进程已退出后，等读线程收完剩余输出的上限 —— 只影响日志完整度，不判定成败。 */
    private const val DRAIN_JOIN_MS = 500L

    /** 授权请求自己那一个编号 —— 回调里按它把别人的请求筛掉（号本身没有含义）。 */
    private const val GRANT_REQUEST_CODE = 1

    /**
     * 失败时回放的 stderr 行数 —— 与 `ShellRunner.SHELL_TAIL_LINES` 同数。
     *
     *  再多的行数只会把判据行冲掉：真正有用的那一句（`not exported from uid …`）总在栈顶。
     */
    private const val TAIL_LINES = 12

    /**
     * 读子进程输出的两条槽（stdout / stderr 各一条）。
     *
     *  读必须与 `waitForTimeout` **并行**：一路写满 64K 管道就卡死在那条命令上（与
     *  `ShellRunner.awaitProcess` 同一笔账）。
     */
    private val readers =
        Executors.newFixedThreadPool(2) { r ->
            Thread(r, "shizuku-io").apply { isDaemon = true }
        }

    private var grantListener: Shizuku.OnRequestPermissionResultListener? = null

    private var grantCallback: ((GrantResult) -> Unit)? = null

    /** 这个进程里拿得到 Shizuku 的 binder 吗（拿到 ≠ 有权限）。 */
    fun available(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** 这个进程的 uid 被授权了吗。 */
    fun granted(): Boolean =
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
            .getOrDefault(false)

    /**
     * 跑一条命令，返回退出码（[FAIL_CODE] / [TIMEOUT_CODE] 见上）。**阻塞**：调用方得是后台线程。
     */
    fun exec(
        command: String,
        onLine: ((String) -> Unit)? = null,
    ): Int {
        //  stderr 的**尾部**：失败时以 w 回放 —— 与 `sh` 那条（`ShellRunner.logOutputTail`）
        //  同一档。原先这里传 `null`，[readAll] 只打 `d`（release 里编译期消除）⇒ 走 Shizuku 的
        //  命令失败时日志里只有一句 `exit=255`，看不出是「activity 不 exported」「找不到类」
        //  还是命令写错（真机踩过：微信收付款那条反复 255，原因一个字都没有）。
        val errTail = java.util.Collections.synchronizedList(ArrayDeque<String>())
        val code =
            runCatching {
                val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                val proc = service.newProcess(arrayOf("sh", "-c", command), null, null)
                val out = readAll(proc.inputStream, "shizuku> ", onLine)
                val err = readAll(proc.errorStream, "shizuku! ", { line -> appendTail(errTail, line) })
                //  AIDL 这一档的单位是个**字符串**（服务端按 `TimeUnit.valueOf` 认，见
                //  `ShizukuRemoteProcess.waitForTimeout` 的传法），所以给 `toString()` 而不是枚举。
                val exited = proc.waitForTimeout(TIMEOUT_SEC, TimeUnit.SECONDS.toString())
                if (!exited) {
                    runCatching { proc.destroy() }
                    TIMEOUT_CODE
                } else {
                    //  进程已退出，但管道可能还被派生的子进程占着：只给读线程一段有限宽限。
                    runCatching { out.get(DRAIN_JOIN_MS, TimeUnit.MILLISECONDS) }
                    runCatching { err.get(DRAIN_JOIN_MS, TimeUnit.MILLISECONDS) }
                    proc.exitValue()
                }
            }.getOrElse { e ->
                XLog.w("Shizuku：执行失败", e)
                FAIL_CODE
            }
        XLog.w("Shizuku：命令结束（exit=$code）")
        if (code != 0) logErrTail(errTail)
        return code
    }

    /**
     * 收一条 stderr 到环形尾部（最多 [TAIL_LINES] 行）。
     *
     *  读写不在同一条线程上（读线程写、`exec` 在退出后读），所以调用方给的是
     *  `Collections.synchronizedList`，这里再套一层同步块取一致的快照。
     */
    private fun appendTail(
        tail: MutableList<String>,
        line: String,
    ) {
        synchronized(tail) {
            if (tail.size >= TAIL_LINES) tail.removeAt(0)
            tail.add(line)
        }
    }

    /**
     * 把 stderr 的尾部打出来 —— **只在失败时**：成功那一路它可能是正常告警，刷出来只会盖住判据行。
     *
     *  内容不脱敏：它已经是命令自己的输出，脱敏会把 `cmp=…` / `not exported from uid …` 这类
     *  判据抹掉（命令本身在 `ShellRunner` 那侧已过 `redactCommand`）。
     */
    private fun logErrTail(tail: List<String>) {
        val lines = synchronized(tail) { tail.toList() }
        if (lines.isEmpty()) return
        XLog.w("Shizuku 输出（尾 ${lines.size} 行）：\n" + lines.joinToString("\n"))
    }

    /**
     * 申请一次授权 —— **已授权就直接回 [GrantResult.GRANTED]、不弹窗**（用户可能只是关掉又打开开关）。
     *
     *  **阻塞**：里面每一步都是 binder 调用（`checkSelfPermission` / `requestPermission`），
     *  而 Sui / Shizuku 会把结果投递回**本进程的主线程**。调用方必须给它一条后台线程，
     *  否则主线程卡在 binder 上等结果、结果又在等主线程 —— 两边互等，界面上就是「一直在等」。
     *
     *  回调只会有一次：授结果、拒绝、发起失败、以及重复请求（上一轮先按「没问成」收掉）。
     */
    fun requestGrantBlocking(onResult: (GrantResult) -> Unit) {
        XLog.w("Shizuku：开始申请（available=${available()} granted=${granted()}）")
        if (!available()) {
            XLog.w("Shizuku：申请时没有 binder")
            onResult(GrantResult.FAILED)
            return
        }
        if (granted()) {
            onResult(GrantResult.GRANTED)
            return
        }
        //  上一次还挂着：先收掉，否则那一侧要等到超时才知道
        finishGrant(GrantResult.FAILED)
        val listener =
            Shizuku.OnRequestPermissionResultListener { code, grantResult ->
                if (code != GRANT_REQUEST_CODE) return@OnRequestPermissionResultListener
                finishGrant(
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        GrantResult.GRANTED
                    } else {
                        GrantResult.DENIED
                    },
                )
            }
        synchronized(this) {
            grantListener = listener
            grantCallback = onResult
        }
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
            .onFailure {
                XLog.w("Shizuku：注册授权回调失败", it)
                finishGrant(GrantResult.FAILED)
                return
            }
        XLog.w("Shizuku：发起授权请求（等回执）")
        //  「再问一次还会不会弹框」—— Shizuku 在用户明确拒绝过之后会置 false，那时再发请求
        //  服务端会直接丢掉（现象就是「框不来、回执也不来」）。打出来，免得继续瞎猜。
        XLog.w("Shizuku：shouldShowRationale=${runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrNull()}")
        runCatching { Shizuku.requestPermission(GRANT_REQUEST_CODE) }
            .onFailure {
                XLog.w("Shizuku：发起授权请求失败", it)
                finishGrant(GrantResult.FAILED)
            }
    }

    /**
     * 一次申请授权的三种收场。
     *
     *  [DENIED] 与 [FAILED] 必须分开：前者是**用户说他不要**（回执里明确拒绝），界面一个字都不用报；
     *  后者是**这一次压根没问成**（没有 binder / 注册回调失败 / 请求本身抛了），结论是不知道 ——
     *  不报的话用户只看到转圈停下、什么也没说。
     */
    enum class GrantResult {
        /** 拿到了。 */
        GRANTED,

        /** 有 Shizuku，用户明确拒绝了。**不是故障**。 */
        DENIED,

        /** 没问成 —— 结论未知。 */
        FAILED,
    }

    private fun finishGrant(result: GrantResult) {
        val listener: Shizuku.OnRequestPermissionResultListener?
        val callback: ((GrantResult) -> Unit)?
        synchronized(this) {
            listener = grantListener
            callback = grantCallback
            grantListener = null
            grantCallback = null
        }
        listener?.let { runCatching { Shizuku.removeRequestPermissionResultListener(it) } }
        if (callback == null) return
        XLog.w("Shizuku：授权结果 $result")
        callback(result)
    }

    /** 把一路输出读干净并进日志（`d` 级：这是高频路径，判据行在那两处 `w`）。 */
    private fun readAll(
        fd: ParcelFileDescriptor,
        prefix: String,
        onLine: ((String) -> Unit)?,
    ): Future<*> =
        readers.submit {
            runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        XLog.d { "$prefix$line" }
                        //  标准输出同时交给调用方 —— 「显示执行返回结果」那一档要的就是它
                        if (onLine != null) runCatching { onLine(line) }
                    }
                }
            }.onFailure { XLog.d { "读 Shizuku 输出失败：${it.message}" } }
        }
}
