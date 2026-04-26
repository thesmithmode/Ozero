plugins {
    id("ozero.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // OWASP Dependency-Check — только для security audit CI task (E13.4)
    id("org.owasp.dependencycheck")
}

android {
    namespace = "ru.ozero.app"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "ru.ozero.app"
        versionCode = 1
        versionName = "0.1.0"
        // Self-update (RT.6.2): owner/repo для GitHub Releases API.
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"thesmithmode\"")
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"Ozero\"")
        // Placeholder для debug/test (все нули = self-update заблокирован, никакая
        // подпись не пройдёт verify). Release ОБЯЗАН переопределить через
        // UPDATE_PUBLIC_KEY_HEX env var (см. buildTypes.release ниже).
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
            // F4 hardening: release-сборка обязана получить реальный Ed25519 pubkey
            // через env var UPDATE_PUBLIC_KEY_HEX (GitHub Secret в release.yml).
            // Гвард-проверка ниже в taskGraph.whenReady — placeholder/пустое значение
            // в release фейлит build, симметрично release-fail-without-signing.
            val updateKey = providers.environmentVariable("UPDATE_PUBLIC_KEY_HEX").orNull
                ?.takeIf { it.isNotBlank() }
            if (updateKey != null) {
                buildConfigField("String", "UPDATE_PUBLIC_KEY_HEX", "\"$updateKey\"")
            }
        }
    }

    // NDK / CMake placeholder (E1+ will add real C++ sources)
    // ndkVersion and externalNativeBuild are activated when CMakeLists.txt is added
    // ndkVersion = "27.2.12479018"
    // externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }

    dynamicFeatures += setOf(":dynamic_tor")

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

// Release-time enforcement: assembleRelease без UPDATE_PUBLIC_KEY_HEX env →
// self-update будет permanently broken (ключ остаётся placeholder all-zeros).
// Лучше fail-fast чем silent ship.
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
    // AndroidX
    implementation(libs.core.ktx)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // E16.1: WorkManager + Hilt-Work для periodic harvester job
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // DataStore — user preferences (Settings)
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Internal modules
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
    // engine-tor: Kotlin/JVM код в base APK (нужен для DI EngineModule), а
    // тяжёлые нативные .so доставляет :dynamic_tor on-demand через PlayCore.
    implementation(project(":engine-tor"))
    implementation(project(":security"))

    // PlayCore feature-delivery — нужен И в base APK (не только engine-tor):
    // dynamic_tor merged manifest ссылается на @integer/google_play_services_version
    // и @style/Theme.PlayCore.Transparent, которые приходят из этой либы.
    // AAPT линкует base + feature manifests в одном проходе → ресурсы должны быть в base.
    implementation(libs.play.feature.delivery)

    // Networking (self-update + diagnostics)
    implementation(libs.bundles.okhttp)

    // Testing
    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.bouncycastle)
    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

// E13.4: OWASP Dependency-Check конфигурация
dependencyCheck {
    format = "ALL" // HTML + XML + JSON отчёты
    // Падаем на HIGH и выше (CVSS >= 7.0). Раньше было 11.0f (выше максимума 10.0)
    // = аудит был декоративным, никакой CVE не блокировал build.
    failBuildOnCVSS = 7.0f
    suppressionFile = rootProject.file("owasp-suppressions.xml").takeIf { it.exists() }?.absolutePath
    nvd {
        // API ключ через переменную окружения NVD_API_KEY (из GH Secret)
        apiKey = providers.environmentVariable("NVD_API_KEY").orNull ?: ""
    }
}
