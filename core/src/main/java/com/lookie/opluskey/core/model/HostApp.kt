package com.lookie.opluskey.core.model

/**
 * 宿主 App 的身份 —— **全项目只在这里写**。
 *
 * 「宿主」= 侧键设置页的寄主，模块的界面层与执行层都跑在它的进程里（见 `KeyModule` 的类注释）。
 * 它的包名与进程名在几处各自出现，含义还不一样：
 *
 *  · **包名** —— 广播的收方（`setPackage`）、`scope.list` 里写的那一项、跨包资源查找的包名；
 *  · **进程名** —— 挑「这一个进程要不要装界面 hook / 手势劫持」时的比对面（`KeyModule`）。
 *
 *  两者在本机**同值**（宿主的进程名就是包名），但**不是同一个概念**：设置页那个进程叫
 *  `com.oplus.gesture.ui`（与包同名加后缀），所以拿包名去比进程名只对主进程成立。
 *  分开写两个常量正是为了让「这里要的是包还是进程」在调用点看得出来；**字面量只有这一份**。
 *
 *  注意：`scope.list`（`app/src/main/resources/META-INF/xposed/scope.list`）是**资源文件**，读不到这里的
 *  常量 —— 它由 CI 与这里的取值对齐（见 `.github/workflows/build.yml` 的「scope.list 与 HostApp 对齐」）。
 *  改这里的包名要连着那份文件一起改。
 */
object HostApp {
    /** 宿主包名（`scope.list` 里写的就是它，也是发广播时的收方包名）。 */
    const val PACKAGE = "com.oplus.gesture"

    /**
     * 侧键设置页所在的**进程**名（`ActionButtonSettingsActivity` 的 `android:process`）。
     * 是进程名不是包名，**不要**写进 `scope.list`。
     */
    const val PAGE_PROCESS = "com.oplus.gesture.ui"
}
