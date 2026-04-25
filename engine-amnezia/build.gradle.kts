plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

// libamneziawg.aar собирается из amneziawg-go (форк wireguard-go от amnezia)
// через gomobile bind в CI (RT.1.7.4). Скачивается preBuild с sha256 verify.
// JNI integration (StartAwg/StopAwg/IsUp/Version) — RT.2/RT.3.
ozeroBinaries {
    artifact("libamneziawg.aar")
}

android {
    namespace = "ru.ozero.engineamnezia"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    // ndkVersion and externalNativeBuild activated when CMakeLists.txt is added (E1+)
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-orchestrator"))
    implementation(project(":core-subscriptions"))
    implementation(project(":core-storage"))
    implementation(project(":common-vpn"))
    implementation(project(":common-crypto"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
