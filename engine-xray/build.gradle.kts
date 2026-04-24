plugins {
    id("ozero.android.library")
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
    implementation(project(":common-vpn"))
    implementation(project(":common-crypto"))
    implementation(project(":core-subscriptions"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
