plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginemasque"
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("native"))

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
}

dependencies {
    implementation(project(":common-crypto"))
    implementation(project(":engines-core"))
    implementation(libs.bundles.coroutines)
    implementation(libs.datastore.preferences)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.json)
    testImplementation(libs.datastore.preferences.core)
}
