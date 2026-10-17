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
        google()
        mavenCentral()
    }
}

rootProject.name = "oplus-key-lsposed"
// `:core` —— 两个进程共用的那一层（见 core/build.gradle.kts 的说明）
include(":core")
include(":app")
