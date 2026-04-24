plugins {
    id("ozero.android.library")
}

// byedpi C-исходники — git submodule (hufrea/byedpi), добавляется отдельно.
// Пока submodule не инициализирован, externalNativeBuild отключён чтобы CI оставался зелёным.
val byedpiSourcesPresent = file("src/main/cpp/byedpi").listFiles()?.any { it.name.endsWith(".c") } == true

android {
    namespace = "ru.ozero.enginebyedpi"

    if (byedpiSourcesPresent) {
        ndkVersion = "27.2.12479018"

        defaultConfig {
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }

        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":common-vpn"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
