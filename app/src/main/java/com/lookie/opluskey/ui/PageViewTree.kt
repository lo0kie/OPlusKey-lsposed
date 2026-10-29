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

    /**
     * 页面关掉时放掉这一页自建的兜底 owner。
     *
     *  `attachOne` 里那次 `dispose` 只在「下一屏换了 owner」时走；直接关页（`clearPageRefs`）
     *  没有下一页，那一套 `ViewModelStore` 就一直挂着，直到这个 `:ui` 进程结束。
     */
    fun release() {
        val own = fallbackOwners ?: return
        fallbackOwners = null
        own.dispose()
        XLog.w("ViewTree owner：页面关闭，兜底 owner 已释放")
    }

    /**
     * 往单个 View 挂三个 owner。
     *
     *  **宿主已经给了就不覆盖**；但**上一页自建的**要换成这一页的 —— 那个「不覆盖」原本把
     *  上一页的也一起保住了：兜底 owner 一旦挂到宿主那棵树上，页面关掉之后它还留在那儿，
     *  于是下一次进设置页时，祖先链上挂的是**上一页**那套（lifecycle 停在 RESUMED、ViewModelStore
     *  没人清）。真机日志里每开一次页多一套「已自建 …」，而**只有第一次**挂满了 8 层 ——
     *  后面几次的 owner 全被 `findViewTree*Owner() == null` 这一道跳过了。
     *
     *  判据靠 [attachedTo] 这份账（我们挂过哪几个 View），而不是 `findViewTree*Owner()`：
     *  后者是**沿 parent 链找**的，分不清「这个 View 自己挂了一个」与「它上面那层有」。
     */
    internal fun attachOne(
        target: View,
        own: FallbackOwners,
    ) {
        try {
            val prev = attachedTo[target]
            val replace = prev != null && prev !== own
            if (replace) {
                XLog.w("ViewTree owner：${target.javaClass.simpleName} 上挂着上一页留下的兜底 owner，换成本页的")
                // 旧那套的 ViewModel 不该跟着宿主活到进程结束；registry 不强行置 DESTROYED ——
                //  上一页的 View 可能还在退场动画里被引用着，半路把它销毁反而会炸在组合里。
                prev.dispose()
            }
            if (replace || target.findViewTreeLifecycleOwner() == null) target.setViewTreeLifecycleOwner(own)
            if (replace || target.findViewTreeViewModelStoreOwner() == null) target.setViewTreeViewModelStoreOwner(own)
            if (replace || target.findViewTreeSavedStateRegistryOwner() == null) {
                target.setViewTreeSavedStateRegistryOwner(own)
            }
            //  **只要三个里有一个是我们写的，就要记账**：只看 lifecycle 的话，宿主给了 lifecycle、
            //  却缺 ViewModelStore / SavedState 的那种树会漏记 —— 下一页 `replace` 判不出来，
            //  而它要找的那两个 owner 已经被上一页 `dispose()` 过了（新页拿到一个已销毁的 store）。
            //  三个都不是我们的（宿主本来就给全了），那就一个 setter 都没调，不记 ——
            //  否则下一页会把 `own` 当成「上一页留下的兜底 owner」，反过来覆盖宿主的。
            val wrote =
                target.findViewTreeLifecycleOwner() === own ||
                    target.findViewTreeViewModelStoreOwner() === own ||
                    target.findViewTreeSavedStateRegistryOwner() === own
            if (replace || wrote) {
                attachedTo[target] = own
            } else {
                attachedTo.remove(target)
            }
        } catch (e: Throwable) {
            XLog.w("挂 ViewTree owner 失败（${target.javaClass.simpleName}）", e)
        }
    }

    private companion object {
        /**
         * 我们挂过兜底 owner 的那些 View → 挂的是哪一套（**弱引用**，宿主那棵树的寿命比页长）。
         *
         *  只用来分清「这个 View 上的 owner 是我们挂的」还是「宿主本来就有」——
         *  见 [attachOne]：前者可以换，后者一律不碰。
         */
        private val attachedTo = java.util.WeakHashMap<View, FallbackOwners>()
    }
}
