plugins {
    // [AGP 9] Kotlin 编译已内置（`android.builtInKotlin`），不再单独挂 kotlin-android 插件
    id("com.android.application") version "9.1.0" apply false
    // Compose 编译器：Kotlin 2.x 起它是独立插件，版本必须跟 Kotlin 一致（内置 Kotlin 也要挂它）
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
