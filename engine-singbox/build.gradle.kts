plugins {
    id("ozero.android.library")
    id("kotlin-parcelize")
}

android {
    namespace = "ru.ozero.enginesingbox"

    buildFeatures {
        aidl = true
    }

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
}

dependencies {
    implementation(project(":engines-core"))
    implementation(project(":common-vpn"))
    implementation(project(":singbox-core"))
    implementation(project(":singbox-fmt"))
    implementation(project(":singbox-config"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    implementation(libs.datastore.preferences)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.json)
}
