plugins {
    id("ozero.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
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
        // Public key Ed25519 (hex, 64 символа = 32 байта). Заменяется при ротации
        // ключа (см. docs/key-rotation.md). Сейчас placeholder — все нули, что
        // означает: ни одна реальная подпись не пройдёт verify до прописывания
        // настоящего ключа в release-сборку. Это намеренно: чтобы случайный APK
        // не был установлен через Ed25519-обход.
        buildConfigField(
            "String",
            "UPDATE_PUBLIC_KEY_HEX",
            "\"0000000000000000000000000000000000000000000000000000000000000000\"",
        )
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
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
