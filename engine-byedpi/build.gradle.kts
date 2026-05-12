plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

ozeroBinaries {
    artifact("libbyedpi-arm64-v8a.so")
    artifact("libbyedpi-armeabi-v7a.so")
    artifact("libbyedpi-x86_64.so")
}

android {
    namespace = "ru.ozero.enginebyedpi"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
}

dependencies {
    implementation(project(":engines-core"))
    implementation(project(":common-vpn"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.json)
}
