package com.lookie.opluskey.ui

import com.lookie.opluskey.core.log.XLog

// 输入法的两件小事（收起 / 是否露出）—— 页面层到处要用，留在 ui 包。

/**
 * 软键盘现在是否露着 —— 判据用 IME 的 inset 可见性（`WindowInsets.Type.ime()`），
 * 不是 `InputMethodManager.isActive()`：后者只表示「当前有输入连接」，字段拿到焦点、键盘收起后照样是 true，
 * 拿它当判据会让「键盘明明关着、返回却被吃掉」。
 *
 * `rootWindowInsets` 在 `onCreate` 早期还没下发，那时读到 null ⇒ 返回 false（键盘本来也不可能开着）。
 */
internal fun isImeVisible(activity: android.app.Activity): Boolean =
    try {
        activity.window
            ?.decorView
            ?.rootWindowInsets
            ?.isVisible(
                android.view.WindowInsets.Type
                    .ime(),
            ) == true
    } catch (e: Throwable) {
        XLog.d("读 IME inset 失败（当作键盘没开）：${e.message}")
        false
    }

/**
 * 收起软键盘 —— 走 `InputMethodManager.hideSoftInputFromWindow` 而不是
 * `WindowInsetsController.hide(ime())`：宿主 window 的 `insetsController` 由宿主自己配，
 * IME 那段不一定交给我们控制；IMM 按 window token 直接发命令，与谁配的无关。
 *
 * 不清焦点：字段保持焦点、只收起键盘，再点一下键盘就回来；清焦点会让光标消失。
 */
internal fun hideIme(activity: android.app.Activity) {
    try {
        val imm =
            activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager ?: return
        imm.hideSoftInputFromWindow(activity.window?.decorView?.windowToken, 0)
    } catch (e: Throwable) {
        XLog.d("收键盘失败（忽略）：${e.message}")
    }
}
