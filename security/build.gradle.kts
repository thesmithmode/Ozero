plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.security"
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":common-crypto"))
    implementation(libs.bundles.bouncycastle)
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
