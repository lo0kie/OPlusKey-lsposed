package com.lookie.opluskey.core.model

/**
 * 模块**自己**的身份 —— 全项目只在这里写。
 *
 *  与 [HostApp] 分开：那个是「寄主」（我们的代码跑在它的进程里），这个是「我们自己」。
 *
 *  用处：按包名问 `PackageManager` 要自己的 APK —— 显示模块版本（`ui/screen/HomeScreen`），
 *  以及 `core/i18n/I18n` **从 APK 里抠字典**（宿主进程里类加载器够不到模块 APK 的资源，
 *  见那边的说明）。两处都不能用 `context.packageName`：那个在宿主进程里拿到的是宿主包名。
 *
 *  硬编码是因为本工程关了 `BuildConfig`（拿不到 `APPLICATION_ID`）；它与
 *  `app/build.gradle.kts` 的 `applicationId` 必须一致 —— CI 会核对（见 `.github/workflows/build.yml`
 *  的「模块包名与 applicationId 对齐」）。
 */
object ModuleInfo {
    /** 模块自己的包名（= `app/build.gradle.kts` 的 `applicationId`）。 */
    const val PACKAGE = "io.github.lo0kie.opluskey"
}
