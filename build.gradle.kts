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
