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

    testImplementation(libs.okhttp.mockwebserver)
}
