package com.lookie.opluskey.core.config

import java.io.File

/**
 * 放开宿主私有目录的权限，让 `adb` 直接读得到配置文件。本文件只存在于 debug 源集。
 *
 * 为什么整段挪进 debug 源集而不是用编译期常量挡：靠的是源集物理隔离 —— release 由
 * `src/release/` 的同名文件提供空实现，产物里根本不含 `setReadable(true, false)` 这些调用。
 * 放开的是**宿主 App 的数据目录**，`false` 参数还是「不限于 owner」——静态扫描里这是提权特征。
 */
internal fun exposeToOthers(
    dir: File,
    file: File,
) {
    // 只放开可读与可遍历：写权限一律不动（`setWritable(true, false)` 会把 0600 抬成 0666）
    dir.setReadable(true, false)
    dir.setExecutable(true, false)
    dir.parentFile?.setExecutable(true, false)
    file.setReadable(true, false)
}
