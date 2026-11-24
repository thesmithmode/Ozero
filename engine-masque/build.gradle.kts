plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginemasque"
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("native"))
    sourceSets["main"].assets.srcDir("third-party")

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
            if (providers.gradleProperty("ozero.soak.x86_64").isPresent) abiFilters += "x86_64"
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
