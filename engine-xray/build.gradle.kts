plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

// libxray.aar скачивается из GitHub Releases через ozero.binaries (RT.1.7.2).
// Подключение AAR к Kotlin/JNI — отдельный шаг RT.2 (DI) / RT.3 (VpnService pipeline).
ozeroBinaries {
    artifact("libxray.aar")
}

android {
    namespace = "ru.ozero.enginexray"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    // ndkVersion and externalNativeBuild activated when CMakeLists.txt is added (E1+)
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-orchestrator"))
    implementation(project(":common-vpn"))
    implementation(project(":common-crypto"))
    implementation(project(":common-json"))
    implementation(project(":core-subscriptions"))
    implementation(project(":core-storage"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
