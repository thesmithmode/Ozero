plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

ozeroBinaries {
    artifact("libmtg-arm64-v8a.so")
    artifact("libmtg-armeabi-v7a.so")
    artifact("libmtg-x86_64.so")
}

android {
    namespace = "ru.ozero.enginetelegram"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
}

dependencies {
    implementation(project(":engines-core"))
    implementation(libs.bundles.coroutines)
    implementation(libs.datastore.preferences)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
