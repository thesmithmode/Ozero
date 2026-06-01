plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.singboxsubscription"
}

dependencies {
    implementation(project(":singbox-fmt"))
    implementation(project(":singbox-room"))
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.okhttp)
    implementation(libs.snakeyaml)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.json)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.okhttp.mockwebserver)
}
