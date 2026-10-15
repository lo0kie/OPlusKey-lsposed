import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

/** [defaultConfig] 里 `build_id` 用的时间源：设了 `SOURCE_DATE_EPOCH` 就按它（可复现），否则取现在。 */
fun epochMillis(): Long {
    val epoch = System.getenv("SOURCE_DATE_EPOCH")?.toLongOrNull() ?: return Date().time
    return epoch * 1000L
}

plugins {
    // [AGP 9] Kotlin 已内置，不挂 kotlin-android
    id("com.android.application")
    // Compose 编译器
    id("org.jetbrains.kotlin.plugin.compose")
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
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
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
            //（`inline` + `if (VERBOSE_LOG)`，见 `BuildFlags`），编译期就折掉了；
            // 告警与错误保留作降级诊断。
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (signingConfigs.findByName("release") != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "没有 keystore.properties，release 包使用 debug 签名，仅限本机测试" +
                        "（keytool -genkeypair -keystore release.jks -alias opluskey " +
                        "-keyalg RSA -keysize 2048 -validity 10000）",
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

    // META-INF/xposed/* 必须原样打进 APK。
    //
    //  注意：**不用 `pickFirsts`**：那三件套真出现重复时 `pickFirsts` 会
    //  **静默取其一**（大概率取错的那份），构建照样全绿；不声明的话 Gradle 会直接报
    //  「Duplicate file」—— 重复本身就是不该发生的，让它响出来。
    packaging {
        resources {
            // 不 pickFirsts：见上面的注释。
        }
        // dex 压进 zip（`classes.dex` 占全包 95%，AGP 默认**不压缩**它以便 mmap 共享）。
        //  实测 3050 KB → 1419 KB，包体 3.34 MB → 1.72 MB。
        //  代价：解压不再共享，改由加载它的每个进程自己解一份；LSPosed 会给模块传
        //  `preLoadedDexes`（框架自己的那份缓存），真机上不一定走到这一步。
        dex {
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
    }
}

// [AGP 9 内置 Kotlin] 编译器选项挂顶层的 `kotlin` 扩展（AGP 创建的那个），不再放 android{} 里
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xno-param-assertions")
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
    // 顶栏磨砂（layerBackdrop + textureBlur），同样需要显式声明；要求 minSdk 33
    implementation("io.github.suqi8.coui.kmp:coui-blur-android:1.1.0")
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

    // androidx 只被 ui/ 界面代码引用，ui/ 在 system_server 里从不执行
    //（KeyModule 只在设置页那个进程分支里装界面 hook），惰性类解析不会触发 NoClassDefFoundError。

    // 不声明 libxposed:service：模块零 Activity，XposedServiceHelper 从未使用，
    // 引入会往清单里合并一个多余的 XposedProvider。

    // framework 私有类一律不声明依赖：non-SDK 接口会被拦，且由注入进程在运行期提供，
    // 这类类一律按名字反射调用。
}
