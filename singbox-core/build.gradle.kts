plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.singboxcore"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
}

dependencies {
    implementation(project(":engines-core"))
    api(
        fileTree(
            mapOf(
                "dir" to "libs",
                "include" to listOf("*.jar"),
            ),
        ),
    )
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
