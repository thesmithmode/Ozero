plugins {
    id("ozero.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.ozero.corestorage"
}

dependencies {
    implementation(project(":core-api"))
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
