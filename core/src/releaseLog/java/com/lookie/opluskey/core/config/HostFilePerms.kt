package com.lookie.opluskey.core.config

//  `releaseLog` 源集：与 `src/release/` 那份**逐字相同** —— 「带完整日志的 release」也是发布形态，
//  不该因为「要日志」就顺手把宿主私有目录放开给 adb。这一份的存在只是源集物理隔离的代价：
//  每个构建类型都必须有自己的同名文件，否则那个变体编不过（不是被注释掉，而是符号缺失）。

import java.io.File

/**
 * release 源集：不放开任何权限 —— 文件留在宿主私有目录的 0600 / 0700 上。
 *
 * 这里的空实现是刻意的：release 产物里不应出现任何 `setReadable` / `setExecutable` 调用。
 */
@Suppress("UNUSED_PARAMETER")
fun exposeToOthers(
    dir: File,
    file: File,
) = Unit
