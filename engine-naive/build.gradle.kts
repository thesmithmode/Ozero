plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

ozeroBinaries {
    artifact("libnaive-arm64-v8a.so")
    artifact("libnaive-armeabi-v7a.so")
    artifact("libnaive-x86_64.so")
    artifact("libnaive-x86.so")
}

android {
    namespace = "ru.ozero.enginenaive"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-orchestrator"))
    implementation(project(":core-subscriptions"))
    implementation(project(":core-storage"))
    implementation(project(":common-vpn"))
    implementation(project(":common-json"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
