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
include(":app")
