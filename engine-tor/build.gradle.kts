plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginetor"

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
    implementation(project(":core-subscriptions"))
    implementation(project(":core-storage"))
    implementation(project(":common-vpn"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
