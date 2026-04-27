plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.engineurnetwork"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-orchestrator"))
    implementation(libs.bundles.coroutines)

        val aarFile = file("libs/URnetworkSdk.aar")
    if (aarFile.exists()) {
        implementation(files("libs/URnetworkSdk.aar"))
    }

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
