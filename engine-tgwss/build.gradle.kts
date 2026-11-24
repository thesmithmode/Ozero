plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginetgwss"
}

dependencies {
    implementation(libs.okhttp.core)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
