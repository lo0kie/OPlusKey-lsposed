package com.lookie.opluskey.core.config

import java.io.File

/**
 * beta 源集：**不放开任何权限** —— 与 release 同一条口径。
 *
 * 这里刻意用空实现而不是复用 `src/debug` 那份：beta 是发布包，
 * 产物里不应出现任何 `setReadable` / `setExecutable` 调用（那是提权特征，
 * 静态扫描会认，改包的人也会顺着它去找配置文件）。
 */
@Suppress("UNUSED_PARAMETER")
fun exposeToOthers(
    dir: File,
    file: File,
) = Unit
