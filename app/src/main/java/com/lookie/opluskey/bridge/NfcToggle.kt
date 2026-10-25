package com.lookie.opluskey.bridge

import android.os.SystemClock
import com.lookie.opluskey.core.log.XLog

/**
 * NFC 那一条开关 —— **从 `SettingCalls` 里分出来的一支**，只因为这一条要记一份「用户想要的状态」。
 *
 * ## 为什么不能直接拿 `getState` 取反（真机量出来的）
 *
 *  2026-10-24 在 PLQ110 上连按侧键、同时每 200ms 轮询 `cmd nfc status`，八次按压的结论：
 *
 *   · **打开**：`enable` 返回后 **0.18 ~ 0.27 s** 就报 enabled；
 *   · **关闭**：`disable` 返回后要 **2.9 ~ 3.4 s** 才报 disabled（协议栈 teardown ＋ 安全元件那一段）；
 *   · 而且关闭那 3 秒里 `getState` **一直是 3（STATE_ON）**，不是 4（TURNING_OFF）。
 *
 *  「点了没反应」不是我们慢 —— 模块自己这条链路（宿主收到手势 → 读状态 → 下发）只花 **9 ms**。
 *  慢的是 NFC 自己，而且只有关的那一头慢。
 *
 * ## 所以只留一件事：[intent]
 *
 *  关闭那 3 秒里 `getState` 还停在 3。照「读状态取反」的写法，用户在这个窗口里再按一下，
 *  读到的还是「开着」⇒ 又发一次 `disable` —— 第二下白按，看起来就是「有时候没生效」。
 *
 *  现在记下**用户最后一次想要的状态**（[intent]，[INTENT_WINDOW_MS] 之内算数），窗口内再按
 *  就按它取反，不看那个还停在旧值的 `getState`。落定之后两种算法结果一致（状态本来就是
 *  对的），所以这个窗口只在「状态滞后」那 3 秒里起作用 —— 它要管的也正好只有那 3 秒。
 *
 * ## 没有「迁移中等落定再补发」那一套
 *
 *  上一版还有一支：状态是 TURNING_ON / TURNING_OFF 时把反向动作记成 pending、每 150ms 轮询
 *  `getState`、等落定后再补发。按上面那份实测它几乎是空转 —— 关闭那 3 秒（真正会被连按的
 *  窗口）里 `getState` 停在 3、根本不进那一支，能进去的只剩开启的 0.18~0.27 s。
 *  为一个最宽 200ms 的窗口挂一个 Handler ＋ 4 秒超时，不值：那 55 行换来的效果还是「3 秒后
 *  才生效」，不是「取消」。现在迁移中按一下就按 [desired] 算出的方向直接下发，服务真丢掉的
 *  话就是丢一次（日志里有 `下发…→false`），比吊一个定时器干净。
 *
 *  要再确认这一步是否还值得加回来，量一次就够（关一次 NFC，看序列里 4 出现几个采样点）：
 *
 *  ```
 *  adb shell "su -c 'for i in $(seq -w 1 25); do cmd nfc status; sleep 0.2; done'"
 *  ```
 */
internal object NfcToggle {
    /** `NfcAdapter.STATE_TURNING_ON` —— 正在开。 */
    private const val STATE_TURNING_ON = 2

    /** `NfcAdapter.STATE_ON`。 */
    private const val STATE_ON = 3

    /** `NfcAdapter.STATE_TURNING_OFF` —— 正在关（只用于日志；[desired] 按「不开」算）。 */
    private const val STATE_TURNING_OFF = 4

    /** 系统身份调 `enable` / `disable` 时传的包名（uid 1000 名下就是 `android`）。 */
    private const val PKG = "android"

    /**
     * [intent] 的有效期。
     *
     *  必须盖住关闭那 3.4 s —— 窗口内再按要按「用户意图」算、而不是按 `getState` 那个旧值。
     *  留一点余量取 4 s。
     *
     *  **这个值只管意图窗口**。上一版还兼着「等落定轮询超时」那一份语义（同一个常量两种尺子，
     *  改一处会误伤另一处）；那支轮询已经删了，现在它是单义的。
     */
    private const val INTENT_WINDOW_MS = 4000L

    /**
     * 用户**最后一次想要的状态**（`null` = 还没表达过）。
     *
     *  与「`getState` 说现在是什么」是两件事：迁移期间前者已经变了、后者还没。
     *  连按翻转要按前者算，否则就是把用户刚表达的反方向又发一遍。
     *
     *  只在**下发成功**时更新（见 [fire]）：被系统拒掉的那一下没生效，不能当成用户「已经要到了」
     *  记进来 —— 否则窗口内再按一下会按一个从未发生过的意图取反。
     */
    private var intent: Boolean? = null

    /** [intent] 记下的时刻 —— 只在 [INTENT_WINDOW_MS] 之内算数。 */
    private var intentAt = 0L

    /**
     * 切一下 NFC。
     *
     * @param target `null` = 切换（按 [intent] / `getState` 取反）；`true` / `false` = 明确的开 / 关
     * @return 这一次**有没有真的下发出去了**（不是「状态已经变了」—— 变没变得等 NFC 自己落定）
     */
    fun run(
        loader: ClassLoader,
        target: Boolean?,
    ): Boolean {
        val s =
            ServiceCalls.service(loader, "nfc", "android.nfc.INfcAdapter") ?: run {
                XLog.w("nfc：取不到 nfc 服务")
                return false
            }
        val state = state(s) ?: return false
        val want = target ?: desired(state)
        XLog.w("nfc：getState=$state（${stateLabel(state)}）目标=${label(want)}${if (target == null) "（切换）" else ""}")
        return fire(s, want)
    }

    /** 目标状态：窗口内听用户上一次的表达，否则按 `getState` 取反。 */
    private fun desired(state: Int): Boolean {
        val recent = intent?.takeIf { SystemClock.uptimeMillis() - intentAt < INTENT_WINDOW_MS }
        if (recent != null) return !recent
        return !(state == STATE_ON || state == STATE_TURNING_ON)
    }

    /** 真发那一下 —— 下发之后按结果记录「用户想要的状态」。 */
    private fun fire(
        s: Any,
        on: Boolean,
    ): Boolean {
        //  同蓝牙那一支：返回 boolean，false = 被拒，不能按「非 null 就是成功」判
        //
        //  `disable` 的第一参 **必须传 true**（persist）。传 false 只是「这次关掉」：
        //  `com.android.nfc` 的 `nfc_on` 不动，于是**重启之后 NFC 自己开回来** —— 用户口径
        //  「侧键绑定切换 NFC，重启会自动打开 NFC」。
        //  这一条与 `SettingCalls.bluetooth` 是同一个坑（那里注释记着同样的现象），
        //  而 NFC 这一处原先传的是 false，等于把「关」这件事只做了一半。
        val ok =
            if (on) {
                ServiceCalls.call(s, "enable", listOf(PKG))?.value == true
            } else {
                ServiceCalls.call(s, "disable", listOf(true, PKG))?.value == true
            }
        //  记在**下发之后**、且只记成功的：被拒的那一下没生效，窗口内再按应当按真实状态重算
        //  （还是同一个方向），而不是按一个没发生过的意图取反。
        if (ok) {
            intent = on
            intentAt = SystemClock.uptimeMillis()
        }
        XLog.w("nfc：下发${label(on)} → $ok")
        return ok
    }

    /** 读 `getState`；读不到返回 `null`。 */
    private fun state(s: Any): Int? {
        val v = ServiceCalls.call(s, "getState")?.value as? Int
        if (v == null) XLog.w("nfc：读不到 getState（服务对象是 ${s.javaClass.simpleName}）")
        return v
    }

    private fun label(on: Boolean): String = if (on) "开" else "关"

    /** 状态值的中文名 —— 只给日志看（`getState=3` 单看数字认不出是哪一档）。 */
    private fun stateLabel(state: Int): String =
        when (state) {
            STATE_TURNING_ON -> "正在开"
            STATE_ON -> "开"
            STATE_TURNING_OFF -> "正在关"
            else -> "关"
        }
}
