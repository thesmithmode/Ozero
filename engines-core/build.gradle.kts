plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginescore"
}

dependencies {
    implementation(libs.bundles.coroutines)
    implementation(libs.datastore.preferences.core)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
