plugins {
    id("ozero.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("org.owasp.dependencycheck")
}

android {
    namespace = "ru.ozero.app"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "ru.ozero.app"
        versionCode = 14
        versionName = "1.0.9"
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"thesmithmode\"")
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"Ozero\"")
        buildConfigField(
            "String",
            "UPDATE_PUBLIC_KEY_HEX",
            "\"0000000000000000000000000000000000000000000000000000000000000000\"",
        )
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            val updateKey = providers.environmentVariable("UPDATE_PUBLIC_KEY_HEX").orNull
                ?.takeIf { it.isNotBlank() }
            if (updateKey != null) {
                buildConfigField("String", "UPDATE_PUBLIC_KEY_HEX", "\"$updateKey\"")
            }
        }
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                    "META-INF/LICENSE*",
                    "META-INF/NOTICE*",
                    "META-INF/DEPENDENCIES",
                    "META-INF/AL2.0",
                    "META-INF/LGPL2.1",
                )
        }
    }
}

gradle.taskGraph.whenReady {
    val isRelease = allTasks.any { it.name.endsWith("Release") && it.name.contains("assemble") }
    if (!isRelease) return@whenReady
    val key = providers.environmentVariable("UPDATE_PUBLIC_KEY_HEX").orNull
    val placeholder = "0000000000000000000000000000000000000000000000000000000000000000"
    if (key.isNullOrBlank() || key == placeholder) {
        throw GradleException(
            "UPDATE_PUBLIC_KEY_HEX env var обязателен для release-сборки " +
                "(self-update будет навсегда сломан). См. docs/key-rotation.md.",
        )
    }
    require(key.length == 64 && key.all { it in "0123456789abcdefABCDEF" }) {
        "UPDATE_PUBLIC_KEY_HEX должен быть 64 hex-символа (32 байта Ed25519), получено ${key.length}"
    }
}

dependencies {
    implementation(libs.core.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.bundles.coroutines)

    implementation(project(":core-api"))
    implementation(project(":core-orchestrator"))
    implementation(project(":core-subscriptions"))
    implementation(project(":core-storage"))
    implementation(project(":common-vpn"))
    implementation(project(":common-crypto"))
    implementation(project(":engine-byedpi"))
    implementation(project(":engine-xray"))
    implementation(project(":engine-amnezia"))
    implementation(project(":engine-hysteria2"))
    implementation(project(":engine-naive"))
    implementation(project(":engine-urnetwork"))
    implementation(project(":engine-tor"))
    implementation(project(":security"))

    implementation(libs.play.feature.delivery)

    implementation(libs.bundles.okhttp)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.bouncycastle)
    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

dependencyCheck {
    format = "ALL"
    failBuildOnCVSS = 7.0f
    suppressionFile = rootProject.file("owasp-suppressions.xml").takeIf { it.exists() }?.absolutePath
    nvd {
        apiKey = providers.environmentVariable("NVD_API_KEY").orNull ?: ""
    }
}
