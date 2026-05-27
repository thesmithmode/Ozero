plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.singboxconfig"
}

dependencies {
    implementation(project(":singbox-fmt"))
    implementation(project(":engines-core"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.json)
}
