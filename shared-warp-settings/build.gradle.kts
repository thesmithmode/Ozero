plugins {
    id("ozero.kotlin.library")
}

dependencies {
    implementation(libs.coroutines.core)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
