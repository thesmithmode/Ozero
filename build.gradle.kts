// Root build file — plugin declarations only, no actions here.
// Convention plugins (buildSrc) carry AGP, Kotlin, detekt, ktlint to each module.
// Plugins already on classpath via buildSrc must NOT specify version here.
plugins {
    // AGP — already on classpath via buildSrc, no version
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("com.android.dynamic-feature") apply false
    // Kotlin — already on classpath via buildSrc, no version
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    // detekt/ktlint — already on classpath via buildSrc, no version
    id("io.gitlab.arturbosch.detekt") apply false
    id("org.jlleitschuh.gradle.ktlint") apply false
    // KSP and Hilt — NOT in buildSrc, need version from catalog
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    // JaCoCo — built-in Gradle plugin
    jacoco
}

// JaCoCo version — keep in sync with gradle/libs.versions.toml [versions].jacoco
jacoco {
    toolVersion = "0.8.12"
}
