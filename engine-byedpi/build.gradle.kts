plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginebyedpi"

    defaultConfig {
        ndk {
            // NDK ABI filters — .so sources added in E1+
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    // ndkVersion and externalNativeBuild activated when CMakeLists.txt is added (E1+)
    // ndkVersion = "27.2.12479018"
    // externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":common-vpn"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
