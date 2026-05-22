plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginefptn"

    defaultConfig {
        ndk {
            abiFilters += "arm64-v8a"
        }
        consumerProguardFiles("proguard-rules.pro")
    }
}

dependencies {
    implementation(project(":engines-core"))
    implementation(project(":common-vpn"))
    implementation(libs.bundles.coroutines)

    implementation(libs.hilt.android)
    implementation(libs.datastore.preferences)
    implementation(libs.brotli.dec)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.datastore.preferences.core)
    testImplementation(libs.json)
}
