plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.commonnet"
}

dependencies {
    implementation(libs.bundles.coroutines)
    implementation(libs.okhttp.core)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.okhttp.mockwebserver)
}
