plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.enginetgwss"
}

dependencies {
    implementation(project(":engines-core"))
    implementation(libs.bundles.coroutines)
    implementation(libs.okhttp.core)

    testImplementation(libs.bundles.testing.unit)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
