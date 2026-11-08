pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // 补丁过的 coui-ui-android 本地仓库，必须排在 mavenCentral 前面
        maven { url = uri("patches/repo") }
        // **tabler 图标只从 Maven Central 取**：本机的 init 脚本会把阿里云镜像插在最前面，
        // 而镜像上的 3.50.0 只同步了 pom、没有 aar ⇒ Gradle 认定这个 module 属于镜像仓库，
        // artifact 缺失就直接失败，不会回头再找 central（报错里只有 aliyun 那两个地址）。
        // 这里把它单独锁到 central；其余依赖仍照旧走镜像。
        exclusiveContent {
            forRepository { mavenCentral() }
            filter { includeGroup("io.github.lyxnx.compose.ui") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "oplus-key-lsposed"
// `:core` —— 两个进程共用的那一层（见 core/build.gradle.kts 的说明）
include(":core")
include(":app")
