import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("ozero.jacoco")
}

extensions.configure<BaseAppModuleExtension> {
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val keystorePath = providers.environmentVariable("OZERO_KEYSTORE_PATH").orNull
    val keystorePassword = providers.environmentVariable("OZERO_KEYSTORE_PASSWORD").orNull
    val keyAlias = providers.environmentVariable("OZERO_KEY_ALIAS").orNull
    val keyPassword = providers.environmentVariable("OZERO_KEY_PASSWORD").orNull
    val hasReleaseSigning =
        keystorePath != null && keystorePassword != null && keyAlias != null && keyPassword != null

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                                                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
                                    isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                                                gradle.taskGraph.whenReady {
                    val releaseTask = allTasks.find {
                        it.name.contains("assembleRelease", ignoreCase = true) ||
                            it.name.contains("bundleRelease", ignoreCase = true)
                    }
                    if (releaseTask != null) {
                        throw GradleException(
                            "Release build требует переменные окружения: " +
                                "OZERO_KEYSTORE_PATH, OZERO_KEYSTORE_PASSWORD, " +
                                "OZERO_KEY_ALIAS, OZERO_KEY_PASSWORD",
                        )
                    }
                }
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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

extensions.configure<com.android.build.gradle.internal.dsl.BaseAppModuleExtension> {
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
                                disable += setOf("GradleDependency", "NewerVersionAvailable")
    }
}
