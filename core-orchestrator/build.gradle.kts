plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.coreorchestrator"
}

dependencies {
    implementation(project(":core-api"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
