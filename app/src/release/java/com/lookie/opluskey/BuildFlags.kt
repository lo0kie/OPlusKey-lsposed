package com.lookie.opluskey

/**
 * 按构建类型各放一份的编译期常量（release 版）。
 * `VERBOSE_LOG` = false 时 `XLog.d` / `XLog.i` 被编译期消除，降级诊断 `w` / `e` / `guard` 不受影响。
 */
@PublishedApi
internal const val VERBOSE_LOG: Boolean = false

/** 构建类型标识，界面底部会显示，便于区分手上这个包是哪种。 */
internal const val BUILD_KIND: String = "release"
