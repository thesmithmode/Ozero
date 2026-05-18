import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("ozero.jacoco")
}

extensions.configure<LibraryExtension> {
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
                it.maxParallelForks =
                    (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                it.forkEvery = 100
                it.timeout.set(java.time.Duration.ofMinutes(15))
                it.systemProperty("junit.jupiter.execution.timeout.default", "120s")
                it.systemProperty("junit.jupiter.execution.timeout.testable.method.default", "120s")
                it.testLogging {
                    events("passed", "failed", "skipped")
                    showStandardStreams = false
                }
            }
        }
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
    android.set(true)
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

extensions.configure<LibraryExtension> {
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "NewApi")
    }
}
