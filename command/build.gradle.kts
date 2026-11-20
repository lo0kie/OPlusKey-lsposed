// `:command` —— **命令能力层**：一条命令从「用户写的那串字」到「真的落地」的全套能力。
//
// 为什么单独一个模块：这一层要被**不止一个界面用**（现在是我们自己的设置页与手势执行侧，
//  后面还会有别的模块直接拿它跑命令）。放进 `:app` 时它只对 `:app` 可见，任何一个新模块要用
//  都得先把整套执行器复制一遍 —— 复制出来的第二份行为迟早与第一份走散（同一条 `shell:` 在两边
//  报不同的失败原因，而两边都不报错）。切成模块之后「谁能用它」变成编译期事实。
//
// 依赖方向只朝下：`:command` → `:core`。它**不认识界面，也不认识注入侧** ——
//  那些「本模块自有通道」（状态栏指令、按键注入、小窗广播、系统开关）在这一层里是
//  [com.lookie.opluskey.command.CommandPlatform] 这组端口，由接入方实现
//  （本项目是 `:app` 的 `bridge/CommandPlatformImpl`）。所以新模块接进来时，
//  **不需要改这一层任何一行**，只要给一份自己的端口实现。

plugins {
    id("com.android.library")
    // 格式化与静态检查：规则不在这里，与另两个模块同一份（根 `build.gradle.kts` 的 `subprojects`）
    id("com.diffplug.spotless")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.lookie.opluskey.command"
    // 与另两个模块同一档（见 `core/build.gradle.kts` 的说明）
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        // 与 `:app` / `:core` 一起抬到 21：三个模块的 target 必须一致（不一致时 Kotlin 直接拒绝内联）
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        // `:app` 的 `releaseLog` 变体要求依赖模块也有同名变体，缺了会在变体匹配阶段报
        // 「No matching variant」。这一档与 release 同形态，只是日志没被折掉。
        create("releaseLog")
    }

    testOptions {
        // 与 `:core` 同一档：本机 JVM 单测里 `android.jar` 的方法返回默认值而不是抛 `Stub!`
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        // 与另两个模块同一条纪律：新增告警当场拦住
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xno-param-assertions")
    }
}

dependencies {
    // 配置 / 字典 / 日志 / 模型 / 线协议 —— 这条 `api` 是**接口的一部分**：本模块对外露出的
    //  参数与返回值里就有 `Action` / `Command` 这些 core 类型，接入方不该被迫再声明一次。
    api(project(":core"))

    // Shizuku：无 root 时借 adb 权限跑 shell —— 是「执行身份」三档里的一档，属于本模块的能力。
    //  `provider` 不能省：权限申请要经它注册的 ContentProvider 走 binder（清单在 `:app`）。
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // 单测只测本层的纯函数（命令文本 → 步骤表那一类）
    testImplementation("junit:junit:4.13.2")
}
