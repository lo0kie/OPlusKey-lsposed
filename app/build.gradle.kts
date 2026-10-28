import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

/**
 * 生成 `build_id` 这个字符串资源 —— 界面版本行展示的那个「出包时刻」。
 *
 * ## 为什么是「任务」而不是 `resValue(…)`
 *
 * `resValue` 收的是**配置期**的一个 String：它的取值在 Gradle 配置阶段就要定下来。
 * 这有两层后果，第二层是要命的那层：
 *
 *  1. 打开 configuration cache 之后，**配置阶段整个被跳过**（复用上次的产物）⇒ `resValue`
 *     里那个值跟着一起被复用 ⇒ 一天里出十个包，版本行显示的都是**第一次**那个时刻。
 *  2. 即使不开缓存，配置期取时钟也意味着「跑一次 `./gradlew help` 也在算日期」——
 *     配置与执行混在一起，是这个项目一直没开 configuration cache 的直接原因。
 *
 * 把它做成任务之后：时间戳在**执行期**取，每次打包都是新的；configuration cache 只缓存
 * 「怎么跑」，不缓存「跑出什么」，两件事各归各位。
 *
 * ## 可复现口径没变
 *
 * 给了 `SOURCE_DATE_EPOCH`（秒级 Unix 时间戳）就用它，没给才取当下 —— 与原来一致。
 * 区别是它现在走 [sourceDateEpoch] 这个 `@Input`，于是「环境变量变了 ⇒ 任务重跑」由 Gradle
 * 自己判，不再需要手工 `--rerun-tasks`。
 *
 * ## 但它**故意**不参与增量判定
 *
 *  注册处写了 `outputs.upToDateWhen { false }`：每次构建都重新取时间。
 *  理由是这个值的用途是「对上时间」，而「现在是几点」不是任务输入 —— 默认的增量判定会让它
 *  在源码变了、包也变了的情况下仍停在更早那一次（实测踩到：改一行注释重新出包，包里还是 6 分钟前）。
 *  代价只是资源重新合并 + 重新打包，R8 不受影响（字符串资源不进混淆）。
 */
abstract class GenerateBuildId : DefaultTask() {
    /** `SOURCE_DATE_EPOCH`（秒）。没设时为空 ⇒ 执行期取当下。 */
    @get:Input
    @get:Optional
    abstract val sourceDateEpoch: Property<String>

    /** 时区。CI 是 UTC、本机是东八 —— 版本行按**东八**显示，不跟着构建机的时区漂。 */
    @get:Input
    abstract val timeZoneId: Property<String>

    /** 生成的 res 目录（里面是 `values/build_id.xml`）。 */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val millis = sourceDateEpoch.orNull?.toLongOrNull()?.times(1000L) ?: System.currentTimeMillis()
        val text =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .apply { timeZone = TimeZone.getTimeZone(timeZoneId.get()) }
                .format(Date(millis))
        val dir = outputDir.get().asFile.resolve("values")
        dir.mkdirs()
        // 取值是固定格式的日期串，理论上不含 XML 特殊字符；仍然过一道转义 ——
        // 这个值将来若被人改成带 `&` 的形态，不该以「资源编译报错」的方式被发现。
        File(dir, "build_id.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- 由 `app/build.gradle.kts` 的 `GenerateBuildId` 生成，不要手改。 -->
            <resources>
                <string name="build_id">${xmlEscape(text)}</string>
            </resources>
            """.trimIndent() + "\n",
        )
    }

    private fun xmlEscape(raw: String): String =
        raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}

plugins {
    // [AGP 9] Kotlin 已内置，不挂 kotlin-android
    id("com.android.application")
    // Compose 编译器
    id("org.jetbrains.kotlin.plugin.compose")
    // 格式化（引擎 ktlint，规则见根目录 .editorconfig）
    id("com.diffplug.spotless")
    // 静态检查（规则见 config/detekt/detekt.yml，存量见同目录 baseline.xml）
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.lookie.opluskey"
    // 37 为 `libxposed:api:102` 元数据要求的最低编译版本；AGP 上限警告由
    // gradle.properties 的 `android.suppressUnsupportedCompileSdk=37` 压掉。
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        // 包名即 LSPosed 模块仓库名（由 Xposed-Modules-Repo 机器人按它建库）；
        // 前缀必须是 `io.github.<GitHub 用户名>.`，或包名根域名有对应的 TXT 校验记录。
        // 改这一行 = 换仓库 + 老用户重装，别随手动。
        applicationId = "io.github.lo0kie.opluskey"
        minSdk = 33 // ColorOS 13（Android 13）起才有侧键派发
        targetSdk = 35
        // 第一个进 LSPosed 模块仓库的版本是 1.0.0（仓库里此前 0 条 release —— `0.1.0` 是手动发的，
        // 没走模块仓库；`0.2.0` 没发过）。`versionCode` 仍取 3 而不是 1：1 / 2 都被历史包用掉了
        // （手动发的 0.1.0 与本地出的 0.2.0），而管理器判「有没有新版本」是**严格大于**
        // （`RepoLoader.upgradable`），安装器也不接受比已装版本小的包 —— 只增不减。
        versionCode = 8
        versionName = "1.3.0"

        // `build_id`（出包时刻，界面版本行展示）**不在这里**声明：
        //  它是 [GenerateBuildId] 这个任务的产物，由 `androidComponents.onVariants` 挂进各变体的
        //  res 目录。为什么不能写 `resValue`（配置期取值会被 configuration cache 冻住）见那个任务类。
        //
        //  为什么要日期：只给 `HHmmss` 时，「手上这个包是哪天出的」看不出来
        //  —— 调试包与正式包都可能跨天留在手里（见 `ui/screen/HomeScreen` 的 `appVersionLabel`）。

        // 只留 arm64-v8a。包里那份 `libandroidx.graphics.path.so` 是 Compose 画矢量路径带来的，
        //  而**本模块没有自己的进程**（零组件，界面跑在宿主进程里）⇒ 它只会被**注入目标的那个进程**
        //  加载：目标机型上宿主与 system_server 都是 64 位。另外三份副本（armeabi-v7a / x86 / x86_64）
        //  纯属白带，占 27 KiB。
        //  代价：真碰上 32 位 ROM 会 `UnsatisfiedLinkError` —— 那类机型没有侧键，不在适配清单里。
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        // [coui-nav] `entry<T>()` 是 inline，其 class 文件是 Java 21（major 65）——
        // Kotlin 不许把高阶 target 的字节码内联进低阶目标，所以这里与 Kotlin 一起抬到 21
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // 语言资源只留中英两档。
    //
    //  这**不是**本模块自己的文案：界面字典是 `assets/locales/*.json`（走 `core/i18n`，
    //  与 Android 资源无关），所以这一刀动不了模块的一个字。它砍的是**依赖**带进来的字符串 ——
    //  androidx（Compose 文本选择工具条那几颗「复制 / 粘贴 / 全选」）、COUI、Haze 这些 AAR
    //  各自带了一整套本地化，实测合并后 **89 种语言配置**全进了 `resources.arsc`。
    //
    //  为什么留 `zh` 与 `zh-rCN` 两条：`zh` 是简体/繁体/港台的公共回退档，`zh-rCN` 是部分库
    //  单独写的那份；只留 `zh` 会让少数库在国行机（`zh-rCN`）上退到 `zh`——大概率逐字相同，
    //  但这一档没有收益可比（省下的是**空的**，见下），不留着才是白改。
    //  `en` 必须留：它是所有缺档语言的最终回退，砍了会让一批库的字符串直接消失。
    //
    //  注意用的是 `androidResources.localeFilters` 而**不是** `defaultConfig.resourceConfigurations`：
    //  后者自 AGP 8.1 起已废弃，AGP 9 里没有它。
    androidResources {
        localeFilters += listOf("zh", "en", "zh-rCN")
    }

    // 正式签名从根目录 keystore.properties 读取（不入库）；文件不存在则跳过。
    // 必须排在 buildTypes 之前：signingConfig 是配置期求值的。
    signingConfigs {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            val props = Properties()
            propsFile.inputStream().use { props.load(it) }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    // 发布签名只有本机有 keystore.properties 时才建得出来（见上面那段）。没有时 **release 退回
    //  debug 签名** —— 走到那一步时真正会让构建失败的是 `:core` 的封存层（它要签名派生密钥，
    //  见 `SealReleaseAssets`），那条错误才说得清是「缺签名」；不给回落的话，配置期会先抛一句
    //  `SigningConfig with name 'release' not found`，与真因无关（CI 上没有那份文件）。
    val publishSigning =
        signingConfigs.findByName("release") ?: run {
            logger.warn(
                "没有 keystore.properties：release 构建会失败（封存层需要签名派生密钥）。" +
                    "本机生成一份：keytool -genkeypair -keystore release.jks -alias opluskey " +
                    "-keyalg RSA -keysize 2048 -validity 10000",
            )
            signingConfigs.getByName("debug")
        }

    buildTypes {
        debug {
            // 注意：**固定用 debug 签名**，不回落 release。
            //  原来「与 release 同签名」是为了免掉互相覆盖安装失败，代价是：debug 包（带
            //  `DEBUG_PROBE=true`，等于把「任意反射执行」开在 system_server 里，见 `BuildFlags`）
            //  能直接覆盖安装到 release 包上、反之亦然 —— 手上那个包到底是哪一种，只看签名分不出来。
            //  代价明说：debug 与 release 现在**不能互相覆盖安装**（签名冲突，要卸载重装），
            //  这是**期望的**隔离。
            signingConfig = signingConfigs.getByName("debug")
            // 系统「应用信息」里也分得清两档 debug / release（页面上的版本串另有 `BUILD_KIND`）
            versionNameSuffix = "-debug"
        }

        release {
            // 混淆 + 资源收缩。详细日志不是被 R8 裁的：XLog.d / i 走的是 Kotlin 常量条件
            // （`inline` + `if (VERBOSE_LOG)`，见 `BuildFlags`），编译期就折掉了；
            // 告警与错误保留作降级诊断。
            //
            //  **`-Pfast` = 本地迭代档**：R8 那一步要 13 秒（构建剖析里占八成），改一行界面就得
            //  等它一次。带上这个参数就跳过混淆 / 资源收缩，出包从 ~21s 降到 ~4s。
            //  产物**不是交付物**（体积大、未混淆），只是「装上去看效果」用的；
            //  它仍然是 release 签名、`isDebuggable = false`、`DEBUG_PROBE = false`，
            //  所以装到真机上不会像 debug 包那样把「任意反射执行」开着。
            val fast = project.hasProperty("fast")
            isMinifyEnabled = !fast
            isShrinkResources = !fast
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = publishSigning
        }

        // 「带完整日志的 release」—— 给排查用的包：除日志外与 release 完全一致
        //  （R8 + 资源收缩 + 正式签名 + 资产封存 + 不可调试）。
        //
        //  日志那一档由 `:core` 的 `src/releaseLog/BuildFlags.kt` 顶替（`VERBOSE_LOG = true`），
        //  所以这一档**必须**在 `:core` 也声明同名 buildType —— 否则变体匹配失败。
        //  `versionNameSuffix` 让「应用信息」与产物名都看得出这是哪一档（`1.0.0-log`）。
        create("releaseLog") {
            initWith(getByName("release"))
            versionNameSuffix = "-log"
        }
    }

    buildFeatures {
        // 详细日志开关用按构建类型各放一份的 const val，编译期折掉
        buildConfig = false
        compose = true
        // `build_id` 已改成任务产物（见 [GenerateBuildId]），不再需要 `resValue` 这一档。
        //  仍然保持显式 false：AGP 9 起它默认就是 false，写出来是为了让人知道「本模块不用它」，
        //  而不是「忘了开」。
        resValues = false
    }

    // META-INF/xposed/* 必须原样打进 APK。
    //
    //  注意：**不给 `packaging` 配 `pickFirsts`**：那三件套真出现重复时 `pickFirsts` 会
    //  **静默取其一**（大概率取错的那份），构建照样全绿；不声明的话 Gradle 会直接报
    //  「Duplicate file」—— 重复本身就是不该发生的，让它响出来。
    packaging {
        // dex 压进 zip（`classes.dex` 占全包 95%，AGP 默认**不压缩**它以便 mmap 共享）。
        //  实测 3050 KB → 1419 KB，包体 3.34 MB → 1.72 MB。
        //  代价：解压不再共享，改由加载它的每个进程自己解一份；LSPosed 会给模块传
        //  `preLoadedDexes`（框架自己的那份缓存），真机上不一定走到这一步。
        dex {
            useLegacyPackaging = true
        }
        resources {
            excludes +=
                setOf(
                    // 协程的调试探针资源：只在主动装 `DebugProbes.install()` 时读，
                    //   release 里没人走那条路（它是数据不是代码，删了不改动行为）。
                    "DebugProbesKt.bin",
                    // 依赖的版本清单（`META-INF/androidx.*.version` 那一类）：四十来份，各 6~9 字节，
                    //  是给构建期的「依赖版本对齐报告」看的，设备上没有任何代码读它。
                    "META-INF/*.version",
                    // AGP 写进去的构建元数据：只在排查构建本身时有用，运行期无人读取。
                    "META-INF/com/android/build/gradle/app-metadata.properties",
                    "META-INF/version-control-info.textproto",
                    // kotlin 编译器的工具元数据：打给**构建工具**看的，不在运行期用。
                    "kotlin-tooling-metadata.json",
                )
            //  上面**没有**排除许可证文件（原本是 5 份逐字节相同的 Apache-2.0 LICENSE.txt，
            //  共 17.4 KiB）。它们确实互相重复，但那是依赖的许可证正文 —— 本项目是 GPL-3.0，
            //  README 里也专节列了第三方归属，为十几 KB 把许可证正文从产物里拿掉不合适。
            //  真要省这 17.4 KiB，就在这条 `excludes` 里再加 `"META-INF/*/LICENSE.txt"` 即可。
        }
    }

    // lint 的策略（abortOnError / checkDependencies / warningsAsErrors / 三个 disable）
    //  也收在根 `build.gradle.kts` 里了，与 `:core` 同一份。
}

// release 的资产封存接进构建：容器进 `assets/`，指纹常量进源码。
//
//  走 Variant API 而不是给 `sourceSets.release` 加目录：封存是**任务产物**（依赖 keystore、
//  必须在打包前跑完），用 `addGeneratedSourceDirectory` 把任务的输出目录挂上去，Gradle 才认得
//  「先跑任务、再打包」这条依赖。
androidComponents {

    // `build_id` 的生成任务挂进**每一个**变体（三档都要版本行）。
    //
    //  另起一个 `onVariants { }` 而不是塞进上面那个：上面那个在 debug 上会 `return@onVariants`
    //  提前退出（debug 不封存资产），两件事的适用条件不同，混在一起就得手工排顺序。
    onVariants { variant ->
        val taskName = "generateBuildId" + variant.name.replaceFirstChar(Char::uppercase)
        val gen =
            tasks.register<GenerateBuildId>(taskName) {
                description = "生成 build_id 字符串资源（${variant.name}）"
                // 用 `providers.environmentVariable` 而不是 `System.getenv`：
                //  前者会被登记成**任务的输入**，环境变量一变 Gradle 自己就重跑；
                //  后者是配置期的普通读，configuration cache 只能把它当成「配置的一部分」冻住。
                sourceDateEpoch.set(providers.environmentVariable("SOURCE_DATE_EPOCH"))
                timeZoneId.set("Asia/Shanghai")
                outputDir.set(layout.buildDirectory.dir("generated/buildId/${variant.name}/res"))
                // **每次构建都重新生成**，不参与 UP-TO-DATE 判定。
                //
                //  为什么必须这样：这个值是给人「对上时间」用的（用户报障时会说「我装的是
                //  几点几分那个包」）。默认的增量判定只认**任务输入**，而「现在是几点」不是输入
                //  —— 于是改了源码、APK 确实变了，但只要这个任务的输入没变，它就保持 UP-TO-DATE、
                //  时刻停在更早那一次。实测踩到：改了一行注释重新出包，包里还是 6 分钟前那个时刻。
                //
                //  代价只有资源重新合并 + 重新打包（debug 约几秒）；R8 不受影响 ——
                //  字符串资源不进混淆，`minify*WithR8` 不会因此重跑。
                outputs.upToDateWhen { false }
            }
        variant.sources.res?.addGeneratedSourceDirectory(gen, GenerateBuildId::outputDir)
    }
}

// [AGP 9 内置 Kotlin] 编译器选项挂顶层的 `kotlin` 扩展（AGP 创建的那个），不再放 android{} 里
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        // 警告即编译失败：新增的告警（弃用 API、多余判空、未用变量…）当场拦住，
        //  不让它们攒成「反正一直红着」的背景噪音。当前**零告警**，加这一条不必先还债。
        allWarningsAsErrors = true
        // 注入侧大量与 Java / 反射打交道：按 JSR-305 注解判可空性（平台类型不再一律「随你」）
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xno-param-assertions")
    }
}

// 格式化（spotless）与静态检查（detekt）的配置**不在这里**：
//  它们与 `:core` 是同一份，收在根 `build.gradle.kts` 的 `subprojects { }` 里 ——
//  重复的规则等于没有规则（改一边另一边不会红，只会静默地跑在两套标准上）。
//  本文件只声明「用哪个插件」，规则本身一处定义。

dependencies {
    // 两个进程共用的那一层（配置 / 字典 / 日志 / 模型 / 线协议 / 反射工具）—— 独立模块，
    //  于是「它不许引用界面层」从 CI 的事后扫描变成了编译期约束。
    implementation(project(":core"))
    // 只读 asset（捐赠名单）改用 kotlinx 的树 API 解析；不挂编译器插件，就不需要 @Serializable
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Xposed API：运行时由 LSPosed 提供
    compileOnly("io.github.libxposed:api:102.0.0")

    // 界面：COUI（ColorOS 16 设计语言的 Compose 实现，自带色板与组件，不依赖宿主主题）
    //
    //  `strictly` 不能去：Gradle 版本序里纯 `1.1.0` 比 `1.1.0-subtitle11` 更高（带限定符算更低），
    //  而 coui-preference / coui-nav 传递依赖 `coui-ui-android:1.1.0`，不受约束时会解析回 `1.1.0`。
    implementation("io.github.suqi8.coui.kmp:coui-ui-android:1.1.0-subtitle11") {
        version { strictly("1.1.0-subtitle11") }
    }
    // Preference 整行组件（Switch/Arrow/SliderPreference），不在 coui-ui 传递依赖里
    implementation("io.github.suqi8.coui.kmp:coui-preference-android:1.1.0")
    // 磨砂（顶栏 / 浮层菜单 / 底部工具栏 / FAB）—— Haze。
    //
    //  为什么不用 coui 自带的 `coui-blur`：它把取样层坐标在 `onGloballyPositioned` 里**存一次**，
    //  而页面转场（`coui-nav` 的连续深度转场）给「被盖页」施加的 `translationX = -0.25 × 宽 = -318px`
    //  是**绘制期**改的、不触发布局 ⇒ 那个快照再也不更新（实测：真机日志里 `live == cached == -318`，
    //  连「读实时值」都救不了）。于是浮层磨砂整块横向错 318px、右边缘露底。
    //
    //  Haze 的做法不一样：`hazeSource` 记下 `positionInRoot` 与 `positionOnScreen` **两套**坐标，
    //  并在每帧 `OnPreDrawListener` 里重算；源与效果不在同一个 window 时（浮层菜单就在宿主
    //  `COUIPopupHost` 里）由 `HazePositionStrategy.Auto` 自动升级成屏幕坐标。
    implementation("dev.chrisbanes.haze:haze:2.0.1")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.1")
    // 页面栈：NavDisplay + 连续深度转场，与 coui-ui 同版本，零依赖 androidx.navigation。
    // 栈用 navBackStackOf（纯内存、不要求 @Serializable）—— 页面进程死了本来也不该恢复栈。
    implementation("io.github.suqi8.coui.kmp:coui-nav-android:1.1.0")
    // Tabler 图标（lyxnx 分支，版本号跟 Tabler 本体；R8 裁掉未引用字形）。
    // 3.49.0 是下限：`circle-play` 这一批（同样还有 `circle-pause` / `circle-stop`）到 3.49 才进 Tabler，
    // 之前拿 `player-play` 顶过 —— 那颗是**光秃秃一个三角**、不带圆圈，不是同一个字形。
    // （旧注释里「镜像源缺 3.45.0+ 的 AAR」已不成立：3.45.0 ~ 3.49.0 的 aar 现在都在 Maven Central。）
    implementation("io.github.lyxnx.compose.ui:tabler-icons-android:3.49.0")
    // ComposeView 需要 ViewTreeLifecycleOwner / ViewModelStoreOwner，宿主树里没有，自建挂载
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.savedstate:savedstate-ktx:1.3.0")
    // COUI 浮层菜单依赖 LocalNavigationEventDispatcherOwner（页面自建根 dispatcher）
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")

    // androidx 只出现在 `ui/`：`:core` 里一个 androidx import 都没有（`tools/audit-arch.py`
    //  把这条当判据在 CI 里核）。`ui/` 在 system_server 里从不执行（KeyModule 只在设置页那个
    //  进程分支里装界面 hook），注入侧（`inject/`）既不引用 `ui/` 也不引用 `I18n` 的界面订阅口，
    //  所以惰性类解析不会触发 NoClassDefFoundError。

    // 不声明 libxposed:service：模块零 Activity，XposedServiceHelper 从未使用，
    // 引入会往清单里合并一个多余的 XposedProvider。

    // framework 私有类一律不声明依赖：non-SDK 接口会被拦，且由注入进程在运行期提供，
    // 这类类一律按名字反射调用。
}
