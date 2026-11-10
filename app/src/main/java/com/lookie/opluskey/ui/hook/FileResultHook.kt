package com.lookie.opluskey.ui.hook

import android.app.Activity
import android.content.Intent
import com.lookie.opluskey.bridge.hookGuarded
import com.lookie.opluskey.core.log.XLog
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/**
 * 把「宿主 Activity 的结果回调」补挂到**宿主自己重写的那一层**。
 *
 *  平台类 `Activity#onActivityResult` 上那份 hook 只在宿主**没有重写**、或重写后调了 `super` 时
 *  才收得到。宿主在自己的 Activity（或中间那个基类）里覆盖了它、又不调 `super` 的话，
 *  回调到那一层就断了 —— 用户挑完文件回来「什么都没发生」，而且**日志里一条失败行都没有**
 *  （这条链路上没有任何地方会抛）。
 *
 *  所以弹选择器之前沿类链找**第一处声明**，找到就单独挂一份同样的处理：
 *  挂上之后返回 `null`、`super` 不会被调用，平台那份 hook 也就不会重复触发。
 *  同一个类只挂一次（[hookedClasses] 记住）。
 */
internal object FileResultHook {
    private const val METHOD = "onActivityResult"

    /**
     * 已经补挂过的宿主 Activity 类（同类只挂一次）。
     *
     *  以前只记**一个**类：宿主若出现第二种设置页 Activity，再 arm 时会把记录覆盖掉，
     *  回头第一个类被当成没挂过、再挂一份 ⇒ 导入/导出回调跑两遍。类对象本身活到进程结束，
     *  这个集合不会长出无用项。
     */
    private val hookedClasses = mutableSetOf<Class<*>>()

    /** 装平台 hook 时记下的模块实例（本文件在 `ui/hook`，与那处安装同一个进程）。 */
    private var module: XposedModule? = null

    fun install(module: XposedModule) {
        this.module = module
    }

    /**
     * @param activity 宿主的 Activity（要弹选择器的那个）
     * @param hooker 与平台那份 hook **完全相同**的处理（返回 `null` = 吞掉这次回调）
     */
    fun arm(
        activity: Activity,
        hooker: XposedInterface.Hooker,
    ) {
        val module =
            this.module ?: run {
                //  静默 return 的表现是「点了没反应、日志一行都没有」——这一行是那种现象的唯一线索。
                XLog.w("文件选择：模块实例还没装上，这次 arm 放弃（导入/导出会收不到结果）")
                return
            }
        val cls = activity.javaClass
        if (cls in hookedClasses) return
        var cur: Class<*>? = cls
        //  有没有哪一层**声明了**这个方法 —— 与「声明了但挂钩失败」是两件事，收尾要分开说：
        //  前者说明宿主没覆盖（以后也不用再试），后者说明这次没接上（下次还得重试）。
        var anyDeclared = false
        while (cur != null && cur != Activity::class.java) {
            val declared = declaredResult(cur)
            if (declared != null) {
                anyDeclared = true
                //  挂钩**成功**才记账。没记上就把 `hookedClass` 提前写上、还打一句「已挂钩」，
                //  后面每次进来都会被开头那句 `hookedClass === cls` 挡回去 —— 结果是这条路
                //  **一次都没接上、日志还说自己接上了**（导入导出整条静默归零）。
                if (module.hookGuarded(declared, hooker) != null) {
                    hookedClasses += cls
                    XLog.w("文件选择：宿主的 ${cur.simpleName}#$METHOD 已单独挂钩（它不往上调也能收到）")
                    return
                }
                XLog.w("文件选择：宿主 ${cur.simpleName}#$METHOD 挂钩失败，继续往上找")
            }
            cur = cur.superclass
        }
        if (anyDeclared) {
            //  **这一趟不记账**：记了等于「以后不再试」，而这里失败的原因是挂钩抛了异常
            //  （框架侧一时的问题），下一次 arm 值得再试一遍。日志也要说清是哪一种失败。
            XLog.w("文件选择：宿主覆盖了 $METHOD，但每一层都挂钩失败（本次不记账，下次 arm 重试）")
            return
        }
        //  宿主没覆盖 ⇒ 以后也不用再扫这一层链，照样记账（否则每次 arm 都要把父类链走一遍）。
        hookedClasses += cls
        //  **判据级**：导入导出「点了没反应」时，先要看的就是走没走到平台那个 hook ⇒ `w`。
        XLog.w("文件选择：宿主没有覆盖 $METHOD，走平台那个 hook")
    }

    /** 这一层有没有声明结果回调；没有就是没有（继续往父类看）。 */
    private fun declaredResult(cls: Class<*>): java.lang.reflect.Method? =
        try {
            cls.getDeclaredMethod(
                METHOD,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Intent::class.java,
            )
        } catch (e: Throwable) {
            //  「没声明」与「反射失败」在这里同形，所以留一行 `d`：真出问题（签名变了）时才查得到。
            XLog.d("文件选择：${cls.simpleName} 上没有 $METHOD（${e.javaClass.simpleName}）")
            null
        }
}
