plugins {
    id("ozero.android.library")
    id("kotlin-parcelize")
}

val soakX86_64 = providers.gradleProperty("ozero.soak.x86_64").isPresent
val supportedAbis =
    if (soakX86_64) listOf("arm64-v8a", "x86_64") else listOf("arm64-v8a")

android {
    namespace = "ru.ozero.enginesingbox"

    buildFeatures {
        aidl = true
    }

    defaultConfig {
        ndk {
            abiFilters += supportedAbis
        }
    }
}

dependencies {
    implementation(project(":engines-core"))
    implementation(project(":common-vpn"))
    implementation(project(":singbox-core"))
    implementation(project(":singbox-fmt"))
    implementation(project(":singbox-config"))
    implementation(project(":singbox-room"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    implementation(libs.datastore.preferences)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.json)
}
