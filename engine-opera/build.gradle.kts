plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

ozeroBinaries {
    artifact("libopera_proxy-arm64-v8a.so")
}

android {
    namespace = "ru.ozero.engineopera"
}

dependencies {
    implementation(project(":engines-core"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
