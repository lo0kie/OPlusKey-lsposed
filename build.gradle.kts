plugins {
    // [AGP 9] Kotlin 编译已内置（`android.builtInKotlin`），不再单独挂 kotlin-android 插件
    id("com.android.application") version "9.1.0" apply false
    // Compose 编译器：Kotlin 2.x 起它是独立插件，版本必须跟 Kotlin 一致（内置 Kotlin 也要挂它）
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    // 格式化：Spotless 管仓库形态（缩进 / 换行 / 导入顺序），引擎用 ktlint
    id("com.diffplug.spotless") version "8.10.4" apply false
    // 静态检查：detekt 管「代码本身」的问题（复杂度 / 可疑写法 / 死代码），与 ktlint 不重叠
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

/**
 * 格式化用的 ktlint 版本。
 *
 *  刻意**写死**而不是跟插件默认那版：格式化的结果要能复现 —— 换一版 ktlint 就是换一套规则，
 *  升级它时那个 diff 必须是一次显式的提交，而不是某次顺手跑构建带出来的。
 */
val ktlintVersion = "1.8.0"

/** ktlint 规则 —— 写 Kotlin 就按 Kotlin 自己的规范来（`ktlint_official` 那一档）。 */
val ktlintRules =
    mapOf(
        // 语言自己的规范，不套 Android Studio 那套（4 空格缩进、一个符号一行 import 都是它的默认）
        "ktlint_code_style" to "ktlint_official",
        // Compose 的组件函数按惯例是大驼峰（`@Composable fun SettingsPage.HomeScreen()`），而
        //  `function-naming` 默认只认小驼峰 —— 这是 ktlint 官方给这条规则留的口子（按注解放行），
        //  不是「我们放宽了规则」。
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        // 行长：ktlint 自己**没有**默认档，不写这条它整个规则都不跑
        "max_line_length" to "140",
        // 死 import：这条规则在 ktlint 1.8 的默认配置里**不生效**（实测：完全无引用的 import
        //  也原地不动），必须显式打开。它按 PSI 的引用表达式判，不是按文本出现 —— 所以
        //  `val size = 1` 这种同名局部变量不会把它糊弄过去（detekt 的 UnusedImports 会）。
        "ktlint_standard_no-unused-imports" to "enabled",
    )

/**
 * lint 的项目策略（`:app` 与 `:core` **同一份**，不各写一遍）。
 *
 *  写成扩展函数而不是在两边各贴一段：这两处早先逐字相同，唯一的差别是 `:app` 那边多了几句注释。
 *  那种「内容一样、注释不一样」的重复最危险 —— 下次谁改一边，另一边不会红，只会静默地不同。
 */
fun com.android.build.api.dsl.Lint.applyProjectPolicy() {
    // 检查不通过就**构建失败**：这一档是给 CI 与本地「提交前」用的闸，
    //  红灯留着不修等于没有检查。存量以 `@Suppress` 抑制落在代码里（本仓不留 lint 基线），
    //  新代码不许再添抑制。
    abortOnError = true
    // 报全量（含非致命档），CI 的 HTML 报告才看得出全貌
    checkDependencies = true
    warningsAsErrors = false
    // 与本项目无关的检查：
    //  · `GradleDependency` / `NewerVersionAvailable` 是「有新版可升」，不是缺陷
    //  · `AndroidGradlePluginVersion` 同上（AGP 版本由 compileSdk 37 这条硬约束钉着）
    disable +=
        setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
        )
}

/**
 * 三道闸的聚合入口 —— `./gradlew gate`。
 *
 * ## 为什么要有它
 *
 *  提交前要跑的是「格式 / 静态检查 / lint」这三样，**与打包无关**。而过去的口令是
 *  `./gradlew spotlessCheck detekt lintDebug assembleDebug assembleRelease` 一把梭：
 *  那会顺带把**两遍 R8**（release 与 beta 各一遍，见 `app/build.gradle.kts` 的 beta 段）也跑掉，
 *  在某些机器上是三道闸本身的好几倍时间。
 *
 *  分成两条口令之后：改代码时反复跑 `gate`（几十秒量级），真要出包时才 `assemble<X>`。
 *
 * ## 为什么是写死的任务路径
 *
 *  任务名与模块名都是稳定的（`spotlessCheck` / `detekt` / `lintDebug` 由插件提供）。写死路径的好处
 *  是**改名会当场报错**「Task 'xxx' not found」—— 这比 `findByName` 那种「找不到就静默跳过」好：
 *  后者会让一道闸在不知不觉中不再被跑，而它看起来仍然是绿的。
 */
tasks.register("gate") {
    group = "verification"
    description = "三道闸（格式 / 静态检查 / lint），不打包"
    dependsOn(
        ":app:spotlessCheck",
        ":app:detekt",
        ":app:lintDebug",
        ":core:spotlessCheck",
        ":core:detekt",
        ":core:lintDebug",
    )
}

/**
 * 两个模块共用的全部构建期约束 —— 一处定义，两个模块都从这里拿。
 *
 *  ## 为什么收在这里
 *
 *  这些配置原先是 `app/` 与 `core/` 各写一份、逐字重复（ktlint 版本与规则、spotless 的
 *  `target`、detekt 的 config/baseline、lint 的三个开关）—— 而**重复的规则等于没有规则**：
 *  改一边另一边不会红，只会静默地跑在两套标准上。拆模块那一轮就踩过这个形态
 *  （detekt 阈值只在 `:app` 调过，`:core` 仍是默认值）。
 *
 *  ## 为什么用 `plugins.withId` 而不是直接 configure
 *
 *  `subprojects { }` 这一段在**根脚本求值时就跑**，那时子项目的 `plugins { }` 还没执行、
 *  扩展还不存在，直接 `extensions.configure<…>` 会当场失败。`withId` 是「等这个插件真的
 *  被应用之后再做」，与子项目脚本里的书写顺序无关。
 *
 *  ## 为什么三个插件类在这里能直接写
 *
 *  上面 `plugins { }` 里那几个 `apply false` 把它们放进了**整个构建的 classpath**，
 *  所以根脚本可以引用它们的扩展类型（`SpotlessExtension` / `DetektExtension` / DSL 接口），
 *  不必用 `by name` 那套字符串写法。
 */
subprojects {
    // 依赖锁定：把解析结果的版本钉死，写进各模块的 `gradle.lockfile`（入库）。
    //
    //  为什么要有这一层：`app/build.gradle.kts` 里声明的十几个依赖各自带一串传递依赖，
    //  「今天出得出来的包」与「下周出得出来的包」可能差在某个传递依赖的补丁版本上，
    //  而这个模块的界面跑在**别人的进程里**（宿主设置页），线上只看得到行为差异、看不到依赖表。
    //  锁住之后版本升级变成一次显式动作：改完跑 `./gradlew :app:dependencies --write-locks`，
    //  `gradle.lockfile` 的 diff 就是「这次升了什么」的清单。
    //
    //  `lockAllConfigurations()` 而不是只锁 `compileClasspath` / `runtimeClasspath`：三个变体各有
    //  一对，外加 `*LintChecksClasspath`（lint 的检查器本身也是依赖）—— 只锁一对的话，其余那几条
    //  的版本漂移仍然无人管，而它们同样进产物或进检查结论。
    //
    //  注意：**插件与它们自己的工具链不在锁定范围**。AGP / Kotlin / detekt / spotless 走
    //  `plugins {}` 的 classpath，由上面那几行写死的版本号钉着；detekt 与 spotless 各自依赖的
    //  引擎（比如 ktlint 的版本）在根脚本里也是显式常量。这些改版本本来就得动手改文件。
    dependencyLocking {
        lockAllConfigurations()
    }

    // 格式化：Spotless 管「仓库形态」（缩进 / 换行 / 尾随空格 / 导入顺序），引擎 ktlint。
    //
    //  注意：**Spotless 不会去读仓库根那份 `.editorconfig`**（它跑的是 ktlint 的默认档，
    //  那份文件里写的东西一条都不生效 —— 这条踩过：改了风格却毫无变化）。所以规则写在这里的
    //  `editorConfigOverride` 里；`.editorconfig` 留给 IDE 与 detekt（只放「编辑器约定」那几条，
    //  不放 ktlint 规则，免得两处各说一套）。
    plugins.withId("com.diffplug.spotless") {
        extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktlint(ktlintVersion).editorConfigOverride(ktlintRules)
                trimTrailingWhitespace()
                endWithNewline()
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint(ktlintVersion).editorConfigOverride(ktlintRules)
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }

    // 静态检查：detekt。规则文件与基线都放在仓库根的 `config/detekt/`（detekt 的惯例位置）。
    //
    //  基线是**两个模块共用一份**：`config/detekt/baseline.xml` 里每条都带文件与签名，
    //  detekt 按「本模块扫到的文件」去匹配，匹配不上就静默忽略 —— 所以共用一份不会互相污染。
    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            // 默认规则集全开，`detekt.yml` 只做少量覆盖（见那份文件的说明）
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            baseline = rootProject.file("config/detekt/baseline.xml")
            parallel = true
        }
        tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
            // 与 Kotlin 的 jvmTarget 一档：detekt 自己也要能读 Java 21 的字节码
            jvmTarget = "21"
            // 扫源码与构建脚本；`build/` 之类的产物本来就在 target 之外
            reports {
                html.required.set(true)
                sarif.required.set(true)
                md.required.set(false)
                xml.required.set(false)
                txt.required.set(false)
            }
        }
    }

    // lint：`:app` 是 application、`:core` 是 library，DSL 类型不同（`ApplicationExtension`
    //  / `LibraryExtension`），但策略是同一份 —— 所以两处都调 [applyProjectPolicy]。
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            lint { applyProjectPolicy() }
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            lint { applyProjectPolicy() }
        }
    }
}
