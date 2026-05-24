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

val hasNativeAar = file("libs").listFiles()?.any { it.extension == "aar" } == true

dependencies {
    implementation(project(":engines-core"))
    if (hasNativeAar) {
        implementation(
            fileTree(
                mapOf(
                    "dir" to "libs",
                    "include" to listOf("*.aar", "*.jar"),
                ),
            ),
        )
    } else {
        compileOnly(project(":singbox-stubs"))
    }
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
