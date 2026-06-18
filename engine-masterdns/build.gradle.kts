plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

ozeroBinaries {
    artifact("libmdnsvpn-arm64-v8a.so")
}

android {
    namespace = "ru.ozero.enginemasterdns"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
}

dependencies {
    implementation(project(":engines-core"))
    implementation(project(":common-vpn"))
    implementation(libs.bundles.coroutines)
    implementation(libs.datastore.preferences)
    implementation(libs.sshj)
    implementation(libs.slf4j.nop)
    implementation(libs.bundles.bouncycastle)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
