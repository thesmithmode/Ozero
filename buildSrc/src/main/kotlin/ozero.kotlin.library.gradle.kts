/**
 * Convention plugin for pure Kotlin/JVM modules (no Android SDK dependency).
 *
 * Used by :core-api and any future platform-agnostic modules.
 *
 * Configures:
 * - JVM 17 toolchain
 * - Kotlin K2 compiler options
 * - JaCoCo code coverage with 90% gate
 */
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("ozero.jacoco")
}

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    config.setFrom(rootProject.files("detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = false
    parallel = true
}

extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("1.3.1")
    outputToConsole.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**", "**/build/**", "**/Placeholder.kt")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
