package com.lookie.opluskey.ui

import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.state.FallbackOwners

/**
 * 页面那棵 View 树上的三件事：**建好的根 View**、以及必要时自建的三个 owner。
 *
 *  为什么单列一个对象：这三样是「和宿主那棵 View 树打交道」的全部 —— 建页时把 owner 钉上、
 *  addView 之后往祖先链补挂、进子页时把 Android 焦点抢到根 View 上。它们以前是三个字段 +
 *  三个方法摊在 `SettingsPage` 上，夹在「导航栈」「浮窗登记」之间，看不出它们其实是一套。
 *
 *  注意：本类**不引用 `SettingsPage`**（除了 `state` 里那两样纯数据），写它的仍然是
 *  `createView` 与 hook 侧那两个调用点。
 */
internal class PageViewTree {
    /**
     * 建好的页面 View（`createView` 里赋值）。
     *
     *  只为一件事：进「按键绑定」子页时把 **Android 层的焦点**抢到这个 View 上
     * —— 方向键事件按焦点派发，没焦点就收不到（见 `ActionTile` 的注释）。
     */
    var rootView: View? = null

    /** 建好的 owner 集合，等 [attachOwnersUpward] 在 addView 之后往祖先链上挂。 */
    var pendingOwners: FallbackOwners? = null

    /** 已自建的兜底 owner（同一页面实例内复用，见 [ownOwners]）。 */
    var fallbackOwners: FallbackOwners? = null

    /**
     * 把 owner 挂到 [root] 的**整条祖先链**上（含 `root` 自己）。
     *
     *  **必须在 `container.addView(view)` 之后调用** —— 在那之前 `parent` 还是 null。
     * 真机第三轮崩溃换来的：`resolveParentCompositionContext` 只沿 **parent 链**找
     * （报错原文点名宿主布局根 `androidx.constraintlayout.widget.ConstraintLayout`）。
     *
     * @return 挂到了几层（日志用）
     */
    fun attachOwnersUpward(root: View): Int {
        val own = pendingOwners ?: return 0
        var node: View? = root
        var depth = 0
        while (node != null) {
            attachOne(node, own)
            node = node.parent as? View
            depth++
        }
        XLog.w("ViewTree owner 已挂满祖先链 $depth 层（ComposeView → decorView）")
        return depth
    }

    /**
     * 自建的「三合一 owner」。
     *
     * 只在宿主确实缺 owner 时才创建；已创建过就复用（同一 Activity 反复进设置页时
     * 不自建第二份 —— 两份 `LifecycleRegistry` 会让 Compose 的 `Recomposer` 状态错乱）。
     *
     * **宿主三件套齐全时返回 `null`，而不是一个「用不上」的占位实例**：占位实例一旦被
     * [attachOne] 钉进 ViewTree（`attachOne` 只挂不覆盖），后面就再也换不掉，Compose
     * 会拿到那份停在 `INITIALIZED` 的 lifecycle —— 页面一片空白（见 `createView` 里的调用点注释）。
     */
    fun ownOwners(
        hostLifecycle: androidx.lifecycle.LifecycleOwner?,
        hostViewModel: androidx.lifecycle.ViewModelStoreOwner?,
        hostSavedState: androidx.savedstate.SavedStateRegistryOwner?,
    ): FallbackOwners? {
        if (hostLifecycle != null && hostViewModel != null && hostSavedState != null) {
            return null
        }
        fallbackOwners?.let { return it }
        val created = FallbackOwners()
        fallbackOwners = created
        XLog.w(
            "宿主不提供 ViewTree owner（lifecycle=${hostLifecycle != null}" +
                " viewModel=${hostViewModel != null} savedState=${hostSavedState != null}）" +
                "⇒ 已自建 LifecycleRegistry + ViewModelStore + SavedStateRegistry 兜底",
        )
        return created
    }

    /** 往单个 View 挂三个 owner（**只挂不覆盖**）。 */
    internal fun attachOne(
        target: View,
        own: FallbackOwners,
    ) {
        try {
            if (target.findViewTreeLifecycleOwner() == null) target.setViewTreeLifecycleOwner(own)
            if (target.findViewTreeViewModelStoreOwner() == null) target.setViewTreeViewModelStoreOwner(own)
            if (target.findViewTreeSavedStateRegistryOwner() == null) {
                target.setViewTreeSavedStateRegistryOwner(own)
            }
        } catch (e: Throwable) {
            XLog.w("挂 ViewTree owner 失败（${target.javaClass.simpleName}）", e)
        }
    }
}
