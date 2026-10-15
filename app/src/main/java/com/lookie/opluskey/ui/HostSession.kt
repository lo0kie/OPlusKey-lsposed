package com.lookie.opluskey.ui

import android.app.Activity

/**
 * 当前被换页的宿主 Activity 与模块页面 —— 换页编排 / 窗口与状态栏 / 返回拦截 / 导入导出
 * 四个簇**唯一共享**的两个引用，所以单拎出来。
 *
 * 为什么是可变字段而不是构造参数：它们的生命周期跨「安装（这时还没有 Activity）→ onCreate
 * → replaceContent → onDestroy」；而返回拦截与 SAF 的结果回调都是**异步**来的 ——
 * 回调那一刻才知道「现在挂的是哪一份」，只能每次现取。
 *
 * 两张都不能省：返回拦截必须**按实例**判断（我们 hook 的挂在平台类 `Activity` 上，
 * 不认实例会把宿主进程里别的 Activity 一起拦掉）。
 */
internal object HostSession {

    @Volatile
    var activity: Activity? = null

    /** 模块页面（`SettingsPage`）；页面关掉时与 [activity] 一起松掉，别钉着已销毁的 Activity。 */
    @Volatile
    var page: SettingsPage? = null
}
