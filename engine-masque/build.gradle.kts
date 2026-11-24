plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginemasque"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
}

dependencies {
    implementation(project(":common-crypto"))
    implementation(libs.bundles.coroutines)
    implementation(libs.datastore.preferences)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.json)
}
