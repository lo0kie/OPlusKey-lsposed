package com.lookie.opluskey.core.config

import java.io.File

/**
 * release 源集：不放开任何权限 —— 文件留在宿主私有目录的 0600 / 0700 上。
 *
 * 这里的空实现是刻意的：release 产物里不应出现任何 `setReadable` / `setExecutable` 调用。
 */
@Suppress("UNUSED_PARAMETER")
internal fun exposeToOthers(
    dir: File,
    file: File,
) = Unit
