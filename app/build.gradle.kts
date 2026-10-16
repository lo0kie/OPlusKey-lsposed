import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** [defaultConfig] 里 `build_id` 用的时间源：设了 `SOURCE_DATE_EPOCH` 就按它（可复现），否则取现在。 */
fun epochMillis(): Long {
    val epoch = System.getenv("SOURCE_DATE_EPOCH")?.toLongOrNull() ?: return Date().time
    return epoch * 1000L
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
    )

/**
 * release 的资产封存 —— 把 `app/seal/` 下的明文（三张收款码 + 捐赠名单）加密成一份容器资产。
 *
 * ## 为什么是「绑签名指纹」
 *
 *  密钥**不是**写死的常量，而是从签名证书的 SHA-256 派生出来的（证书是公开信息，但**只有
 *  持钥方**能签出那份指纹）。于是「改包」这条路会被卡住：换掉图再重签 ⇒ 运行期算出来的
 *  指纹变了 ⇒ 密钥跟着变 ⇒ 包里那份密文解不开，只能见到兜底字形。
 *  把密钥换成任何写死的常量都不行 —— 那样攻击者解一次就能重新封一份。
 *
 * ## 为什么没密钥就直接失败
 *
 *  这一层**没有明文回退**：回退就是后门（删掉封存目录即可）。所以 release 构建拿不到
 *  `keystore.properties` 时这里**直接抛错**，而不是退回 debug 签名继续出包 —— 那种包装上去
 *  也解不开自己的收款码。CI 里没配签名密钥时不构建 release（见 `.github/workflows/build.yml`）。
 *
 * ## 产物有两份
 *
 *  1. `assets/sealed.bin` —— 容器本体（[assetsOut]）；
 *  2. `SealPin.kt` —— 本次签名证书的 SHA-256，编译进 release 的那份常量的来源（[sourcesOut]）。
 *     它是**生成**的、不是手写的：手写一份就得跟 keystore 对账，迟早走散。
 */
abstract class SealReleaseAssets : DefaultTask() {
    /** 明文源目录（`app/seal`，仓库里保留，同时供 debug 直接当 assets 用）。 */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoot: DirectoryProperty

    /**
     * 正式签名配置（仓库根的 `keystore.properties`）。
     *
     *  用 `ConfigurableFileCollection` 而不是 `@InputFile`：后者在文件不存在时由 Gradle 先报一句
     *  「input file doesn't exist」（见下面 [seal] 里那段说明）—— 它没说清这一档**为什么没有回退**，
     *  而那正是看日志的人需要知道的事。这里自己判，把话说全。
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val keystoreFiles: ConfigurableFileCollection

    /** 生成的那份容器资产（`assets/sealed.bin`）。 */
    @get:OutputDirectory
    abstract val assetsOut: DirectoryProperty

    /** 生成的那份 Kotlin 常量。 */
    @get:OutputDirectory
    abstract val sourcesOut: DirectoryProperty

    @TaskAction
    fun seal() {
        val propsFile = keystoreFiles.files.firstOrNull()
        if (propsFile == null || !propsFile.exists()) {
            throw GradleException(
                "release 构建需要正式签名：读不到 ${propsFile?.path ?: "keystore.properties"}。\n" +
                    "  封存层没有明文回退，拿不到签名就没法派生密钥 —— 这里**故意**直接失败，" +
                    "而不是退回 debug 签名继续出包（那种包解不开自己的收款码）。\n" +
                    "  本机生成一份：keytool -genkeypair -keystore release.jks -alias opluskey " +
                    "-keyalg RSA -keysize 2048 -validity 10000",
            )
        }

        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        val storeFile = props.getProperty("storeFile") ?: throw GradleException("keystore.properties 缺 storeFile")
        val alias = props.getProperty("keyAlias") ?: throw GradleException("keystore.properties 缺 keyAlias")

        val store =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                // storeFile 相对 **keystore.properties 所在目录**（也就是仓库根）——与
                //  `signingConfigs` 那块同一条口径。这里不引 `rootProject`：任务类一旦引用
                //  脚本作用域里的东西，Kotlin 会把整个类编译成「非静态内部类」，Gradle 建不出来。
                File(propsFile.parentFile, storeFile).inputStream().use { load(it, props.getProperty("storePassword")?.toCharArray()) }
            }
        val cert = store.getCertificate(alias) ?: throw GradleException("keystore 里没有别名 $alias 的证书")
        val certDer = cert.encoded
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(certDer)

        // 明文清单：源目录下**全部**文件，条目名 = 相对路径（`/` 分隔）
        val root = sourceRoot.get().asFile
        val entries =
            root
                .walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
                .map { it.relativeTo(root).invariantSeparatorsPath to it.readBytes() }
                .toList()
        if (entries.isEmpty()) {
            throw GradleException("封存源目录是空的：${root.path}（明文资产挪过位置？）")
        }

        val random = SecureRandom()
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            out.write(MAGIC)
            out.writeByte(FORMAT_VERSION)
            out.writeInt(entries.size)
            entries.forEach { (name, plain) ->
                val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(fingerprint, "AES"),
                    GCMParameterSpec(TAG_BITS, iv),
                )
                val sealed = cipher.doFinal(plain)
                out.writeUTF(name)
                out.writeInt(iv.size)
                out.write(iv)
                out.writeInt(sealed.size)
                out.write(sealed)
            }
        }

        val assetsDir = assetsOut.get().asFile
        assetsDir.mkdirs()
        File(assetsDir, CONTAINER_NAME).writeBytes(body.toByteArray())

        val srcDir = sourcesOut.get().asFile.resolve("com/lookie/opluskey/core/seal")
        srcDir.mkdirs()
        File(srcDir, "SealPin.kt").writeText(
            """
            // 由 `app/build.gradle.kts` 的 `SealReleaseAssets` 生成，**不要手改** ——
            //  它是本次签名证书的 SHA-256，改了就和密文对不上。
            package com.lookie.opluskey.core.seal

            /** release：封存层开着，资产只从 `assets/$CONTAINER_NAME` 解。 */
            internal const val SEAL_ENABLED: Boolean = true

            /** 本包签名证书（SHA-256，小写十六进制）—— 与运行期实测值不符即视为被改过的包。 */
            internal const val SEAL_PIN: String = "${fingerprint.joinToString("") { "%02x".format(it) }}"
            """.trimIndent() + "\n",
        )

        logger.lifecycle(
            "封存 ${entries.size} 份资产 → assets/$CONTAINER_NAME（签名证书 ${fingerprint.joinToString("") { "%02x".format(it) }.take(16)}…）",
        )
    }

    private companion object {
        val MAGIC = byteArrayOf('O'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), 'S'.code.toByte())
        const val FORMAT_VERSION = 1
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        const val CONTAINER_NAME = "sealed.bin"
    }
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
        versionCode = 1
        versionName = "1.0.0"

        // 出包时刻**带日期**，界面版本行展示（BuildConfig 已关闭，走 string resource）。
        //
        //  注意：可复现构建口径：这里取的是**配置期的本地时钟**——同一次提交
        //  出两次包值不一样、CI（UTC）与本机（东八）也不一样。要复现时按惯例给
        //  `SOURCE_DATE_EPOCH`（秒级 Unix 时间戳），给了就用它、不给维持原来的本地钟。
        //  （项目没开 configuration-cache，不存在「被冻住」那一档；真开了它会自己报错，不用防。）
        //
        //  为什么要日期：只给 `HHmmss` 时，「手上这个包是哪天出的」根本看不出来
        //  —— 而调试包与正式包都可能跨天留在手里（见 `ui/screen/HomeScreen` 的 `appVersionLabel`）。
        resValue(
            "string",
            "build_id",
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
                .format(epochMillis()),
        )
    }

    compileOptions {
        // [coui-nav] `entry<T>()` 是 inline，其 class 文件是 Java 21（major 65）——
        // Kotlin 不许把高阶 target 的字节码内联进低阶目标，所以这里与 Kotlin 一起抬到 21
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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

    buildTypes {
        debug {
            // 注意：**固定用 debug 签名**，不回落 release。
            //  原来「与 release 同签名」是为了免掉互相覆盖安装失败，代价是：debug 包（带
            //  `DEBUG_PROBE=true`，等于把「任意反射执行」开在 system_server 里，见 `BuildFlags`）
            //  能直接覆盖安装到 release 包上、反之亦然 —— 手上那个包到底是哪一种，只看签名分不出来。
            //  代价明说：debug 与 release 现在**不能互相覆盖安装**（签名冲突，要卸载重装），
            //  这是**期望的**隔离。
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // 混淆 + 资源收缩。详细日志不是被 R8 裁的：XLog.d / i 走的是 Kotlin 常量条件
            // （`inline` + `if (VERBOSE_LOG)`，见 `BuildFlags`），编译期就折掉了；
            // 告警与错误保留作降级诊断。
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (signingConfigs.findByName("release") != null) {
                    signingConfigs.getByName("release")
                } else {
                    // 走到这里说明没有 keystore.properties —— release **注定构建不成**
                    //  （`SealReleaseAssets` 会直接失败：封存层没有明文回退，见那边的说明）。
                    //  这里仍给 debug 签名只是为了配置期能过，好让那条错误信息是「缺签名」而不是
                    //  一句与真因无关的 signingConfig 缺失。
                    logger.warn(
                        "没有 keystore.properties：release 构建会失败（封存层需要签名派生密钥）。" +
                            "本机生成一份：keytool -genkeypair -keystore release.jks -alias opluskey " +
                            "-keyalg RSA -keysize 2048 -validity 10000",
                    )
                    signingConfigs.getByName("debug")
                }
        }
    }

    buildFeatures {
        // 详细日志开关用按构建类型各放一份的 const val，编译期折掉
        buildConfig = false
        compose = true
        // AGP 9 起 `resValues` 默认关闭，而 defaultConfig 里用了 resValue(build_id)
        resValues = true
    }

    // 明文资产（`app/seal/donate/*.png` 与 `app/seal/donors.json`）**只接给 debug**：
    //  开发时要能随手换图，走的就是明文这条路。
    //  release 不接这一条 —— 它的收款码与名单由 [SealReleaseAssets] 封成 `assets/sealed.bin`，
    //  明文一律不进包（这是「换图重打包」那条路被卡住的地方，见那个任务的说明）。
    //  注意路径相对模块目录（`app/`），写 `seal` 就是 `app/seal`。
    sourceSets {
        getByName("debug") { assets.directories.add("seal") }
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
    }

    lint {
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
}

// release 的资产封存接进构建：容器进 `assets/`，指纹常量进源码。
//
//  走 Variant API 而不是给 `sourceSets.release` 加目录：封存是**任务产物**（依赖 keystore、
//  必须在打包前跑完），用 `addGeneratedSourceDirectory` 把任务的输出目录挂上去，Gradle 才认得
//  「先跑任务、再打包」这条依赖。
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val seal =
            tasks.register<SealReleaseAssets>("sealReleaseAssets") {
                description = "把 app/seal 下的明文资产封成 assets/sealed.bin，并生成签名指纹常量"
                sourceRoot.set(layout.projectDirectory.dir("seal"))
                keystoreFiles.from(rootProject.layout.projectDirectory.file("keystore.properties"))
                assetsOut.set(layout.buildDirectory.dir("generated/seal/${variant.name}/assets"))
                sourcesOut.set(layout.buildDirectory.dir("generated/seal/${variant.name}/java"))
            }
        variant.sources.assets?.addGeneratedSourceDirectory(seal, SealReleaseAssets::assetsOut)
        variant.sources.java?.addGeneratedSourceDirectory(seal, SealReleaseAssets::sourcesOut)
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

// 格式化：Spotless 管「仓库形态」（缩进 / 换行 / 尾随空格 / 导入顺序），引擎 ktlint。
//
//  注意：**Spotless 不会去读仓库根那份 `.editorconfig`**（它跑的是 ktlint 的默认档，
//  那份文件里写的东西一条都不生效 —— 这条踩过：改了风格却毫无变化）。所以规则写在下面这个
//  `editorConfigOverride` 里；`.editorconfig` 留给 IDE 与 detekt（只放「编辑器约定」那几条，
//  不放 ktlint 规则，免得两处各说一套）。
spotless {
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

// 静态检查：detekt。规则文件与基线都放在仓库根的 `config/detekt/`（detekt 的惯例位置）。
detekt {
    // 默认规则集全开，`detekt.yml` 只做少量覆盖（见那份文件的说明）
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml")
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
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

dependencies {
    // Xposed API：运行时由 LSPosed 提供
    compileOnly("io.github.libxposed:api:102.0.0")

    // 界面：COUI（ColorOS 16 设计语言的 Compose 实现，自带色板与组件，不依赖宿主主题）
    //
    //  `strictly` 不能去：Gradle 版本序里纯 `1.1.0` 比 `1.1.0-subtitle4` 更高（带限定符算更低），
    //  而 coui-preference / coui-nav 传递依赖 `coui-ui-android:1.1.0`，不受约束时会解析回 `1.1.0`。
    implementation("io.github.suqi8.coui.kmp:coui-ui-android:1.1.0-subtitle4") {
        version { strictly("1.1.0-subtitle4") }
    }
    // Preference 整行组件（Switch/Arrow/SliderPreference），不在 coui-ui 传递依赖里
    // 弹窗正文的 markdown 渲染（`ui/component/MarkdownBody` 用它）。**只给界面层用** ——
    //  注入侧（inject/）与宿主侧（host/）一律不许引用它（AGENTS 一、1 / 二、1.2）。
    //  它只拖一个 markdown 解析器，不引 Material3：色板与字体由 `MarkdownBody` 按 COUI 主题
    //  一档档映射过去，不另开一套主题。
    implementation("com.mikepenz:multiplatform-markdown-renderer-android:0.45.0")

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

    // androidx 出现在 ui/ 与 core/i18n/I18n.kt（后者 import 了 compose 的 `mutableStateOf`）；
    //  ui/ 在 system_server 里从不执行（KeyModule 只在设置页那个进程分支里装界面 hook），
    //  注入侧（inject/）不引用 I18n，所以惰性类解析不会触发 NoClassDefFoundError。

    // 不声明 libxposed:service：模块零 Activity，XposedServiceHelper 从未使用，
    // 引入会往清单里合并一个多余的 XposedProvider。

    // framework 私有类一律不声明依赖：non-SDK 接口会被拦，且由注入进程在运行期提供，
    // 这类类一律按名字反射调用。
}
