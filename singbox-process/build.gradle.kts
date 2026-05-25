plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.singboxprocess"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
}

dependencies {
    implementation(project(":engine-singbox"))
    implementation(project(":singbox-core"))
    implementation(project(":engines-core"))
    compileOnly(fileTree(mapOf("dir" to rootProject.file("singbox-core/libs-stubs"), "include" to listOf("*.jar"))))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
