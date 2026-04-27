plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.coresubscriptions"
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":common-crypto"))
    implementation(project(":core-storage"))
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.okhttp)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("org.json:json:20240303")
}
