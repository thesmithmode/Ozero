plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginewarp"

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
    implementation(libs.bundles.coroutines)

    implementation(libs.hilt.android)
    implementation(libs.datastore.preferences)

    implementation(libs.relinker)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.datastore.preferences.core)
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
}
