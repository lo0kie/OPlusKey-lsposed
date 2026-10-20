package com.lookie.opluskey.core

/**
 * 模块**自己**的身份 —— 全项目只在这里写。
 *
 *  与 [com.lookie.opluskey.core.model.HostApp] 分开：那个是「寄主」（我们的代码跑在它的进程里），这个是「我们自己」。
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

    /**
     * 每次出包手动更新 —— 它是**注入侧与界面之间那把「是不是同一次构建」的尺子**
     * （`LinkCheck` 的「换包没重启」那一档比的就是它）。
     *
     *  不更新的话那把尺子量不出差别：换了包、system_server 里还是旧的，界面照样判「同一次构建」。
     *  产物命名**不看它**（`build.yml` 按 `versionName` 命名，见那一步的注释）。
     *
     * 它还是**构建时刻读不到时的回退值**（`ui/screen/PageText.kt` 的 `moduleBuildStamp`）
     * —— 那时界面版本行显示的整串就是它，所以它自己带着日期。
     *
     *  原来挂在 `KeyModule` 上：那是「版本身份」，不是「入口派发」——而挂在那里会让
     *  界面为了一个版本号去 import 注入入口那个类（`ui → root` 也是审计要清的边）。
     */
    const val BUILD_ID = "2026-10-20-0002"
}
