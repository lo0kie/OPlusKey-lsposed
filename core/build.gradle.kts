// `:core` —— 注入侧与宿主侧**共用**的那一层：配置读写、字典、日志、数据模型、线协议、反射工具，
// 以及**封存层**（`core/seal/`：签名派生密钥 + 容器解密 + 签名判定）。
//
// 为什么单独一个模块：这一层会被**两个进程**加载（system_server 里的注入侧、宿主 App 里的
//  界面层与宿主侧 hook），所以它有一条硬约束 —— **不许引用界面层（`ui/`）与任何只有界面才用的
//  依赖**。以前这条约束只由 `tools/audit-coupling.py` 在 CI 里事后扫；切成模块之后它变成
//  **编译期**约束：`:core` 的 classpath 里根本没有 `:app`，写错了当场编译不过。
//
// 封存层也在这里（曾经留在 `:app`）：它唯一的「生成物」是 `SealPin.kt`（本次签名证书的
//  SHA-256），而**使用方 `Seal.kt` 现在同一模块** —— 于是「生成方与使用方必须同一个变体、
//  同一个模块」这条变成天然成立的，不必再靠两处路径约定对齐。
//  编译期常量生成到本模块的变体源码目录，容器资产走库的 assets（AGP 会把它并进 APK）。

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * release 的资产封存 —— 把 `core/seal/` 下的明文（三张收款码 + 捐赠名单）加密成一份容器资产。
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
 *  也解不开自己的收款码。CI 里没配签名密钥时不构建 release（见 `.github/workflows/ci.yml`）。
 *
 * ## 产物有两份
 *
 *  1. `assets/sealed.bin` —— 容器本体（[assetsOut]），作为**库的资产**进 APK；
 *  2. `SealPin.kt` —— 本次签名证书的 SHA-256，编译进 release 的那份常量的来源（[sourcesOut]）。
 *     它是**生成**的、不是手写的：手写一份就得跟 keystore 对账，迟早走散。
 */
abstract class SealReleaseAssets : DefaultTask() {
    /** 明文源目录（`core/seal`，仓库里保留，同时供 debug 直接当 assets 用）。 */
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
        //  例外：`*.schema.json` 是给编辑器看的**规则**（躺在数据文件旁边那份），不是资产 —— 不进包。
        val root = sourceRoot.get().asFile
        val entries =
            root
                .walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".schema.json") }
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
            // 由 `core/build.gradle.kts` 的 `SealReleaseAssets` 生成，**不要手改** ——
            //  它是本次签名证书的 SHA-256，改了就和密文对不上。
            package com.lookie.opluskey.core.seal

            /** release：封存层开着，资产只从 `assets/$CONTAINER_NAME` 解。 */
            const val SEAL_ENABLED: Boolean = true

            /** 本包签名证书（SHA-256，小写十六进制）—— 与运行期实测值不符即视为被改过的包。 */
            const val SEAL_PIN: String = "${fingerprint.joinToString("") { "%02x".format(it) }}"
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
    id("com.android.library")
    // 格式化与静态检查：规则**不在这里**，与 `:app` 同一份，收在根 `build.gradle.kts`
    //  的 `subprojects { }` 里（一处定义，两个模块都从这里拿）。
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.diffplug.spotless")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.lookie.opluskey.core"
    // 与 `:app` 同一档：`libxposed:api:102` 的元数据要求，上限警告由 gradle.properties 压掉
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        // 与 `:app` 同一档（ColorOS 13 / Android 13 起才有侧键派发）
        minSdk = 33
    }

    compileOptions {
        // 与 `:app` 一起抬到 21：[coui-nav] 的 `entry<T>()` 是 Java 21 字节码，
        //  `:app` 内联它时两个模块的 target 必须一致（不一致时 Kotlin 直接拒绝内联）。
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        // 「带完整日志的 release」—— 与 release 同形态（日志那一档由 `src/releaseLog/` 顶替）。
        //  **必须在这里声明**：`:app` 的 `releaseLog` 变体要求 `:core` 也有同名变体，
        //  缺了它会在变体匹配阶段直接报「No matching variant」。
        create("releaseLog")
    }

    // 明文资产**只接给 debug**：开发时要能随手换图，走的就是明文这条路。
    //  release 不接这一条 —— 收款码与名单由 [SealReleaseAssets] 封成 `assets/sealed.bin`，
    //  明文一律不进包（这是「换图重打包」那条路被卡住的地方，见那个任务的说明）。
    //  注意路径相对本模块目录，写 `seal` 就是 `core/seal`。
    sourceSets {
        getByName("debug") { assets.directories.add("seal") }
    }

    // lint 的策略与 `:app` 同一份，收在根 `build.gradle.kts` 里。
}

// 封存接进构建：容器进本模块的 assets（AGP 合并库资产时进 APK），指纹常量进本模块的变体源码。
//
//  走 Variant API 而不是给 `sourceSets.release` 加目录：封存是**任务产物**（依赖 keystore、
//  必须在打包前跑完），用 `addGeneratedSourceDirectory` 把任务的输出目录挂上去，Gradle 才认得
//  「先跑任务、再打包」这条依赖。
androidComponents {
    onVariants { variant ->
        // 只有「发布形态」的那些变体需要封存：`release` 与「带完整日志的 release」。
        //  后者行为与发布版一致（同一把签名、同一套资产），只是日志没被折掉 —— 它也要封存，
        //  否则装上去「捐赠打赏」那一页的图就没有了（明文资产只接给 debug）。
        if (variant.buildType != "release" && variant.buildType != "releaseLog") return@onVariants
        val taskName = "seal" + variant.name.replaceFirstChar(Char::uppercase) + "Assets"
        val seal =
            tasks.register<SealReleaseAssets>(taskName) {
                description = "把 core/seal 下的明文资产封成 assets/sealed.bin，并生成签名指纹常量（${variant.name}）"
                sourceRoot.set(layout.projectDirectory.dir("seal"))
                keystoreFiles.from(rootProject.layout.projectDirectory.file("keystore.properties"))
                assetsOut.set(layout.buildDirectory.dir("generated/seal/${variant.name}/assets"))
                sourcesOut.set(layout.buildDirectory.dir("generated/seal/${variant.name}/java"))
            }
        variant.sources.assets?.addGeneratedSourceDirectory(seal, SealReleaseAssets::assetsOut)
        variant.sources.java?.addGeneratedSourceDirectory(seal, SealReleaseAssets::sourcesOut)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        // 与 `:app` 同一条纪律：新增告警当场拦住（当前零告警）
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xno-param-assertions")
    }
}

dependencies {
    // Xposed API：只在 `core/reflect` 那几处用到，运行时由 LSPosed 提供
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
}
