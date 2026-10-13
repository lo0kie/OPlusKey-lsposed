package com.lookie.opluskey

/**
 * 按构建类型各放一份的编译期常量：编译期直接折成字面量，死分支连同日志字符串一起被消掉。
 * 不能用 `BuildConfig.DEBUG` 等价替代 —— proguard 规则 `-keep` 了自己的类，R8 不会折死分支。
 */
@PublishedApi
internal const val VERBOSE_LOG: Boolean = true

/** 构建类型标识，界面底部会显示，便于区分手上这个包是哪种。 */
internal const val BUILD_KIND: String = "debug"
