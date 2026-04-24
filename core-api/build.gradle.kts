plugins {
    id("ozero.kotlin.library")
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.kotlin.stdlib)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
