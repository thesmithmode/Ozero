plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

// libhysteria2.aar собирается из apernet/hysteria v2 через gomobile bind
// (RT.1.7.5). Скачивается preBuild с sha256 verify. JNI integration —
// RT.2/RT.3.
ozeroBinaries {
    artifact("libhysteria2.aar")
}

android {
    namespace = "ru.ozero.enginehysteria2"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
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
