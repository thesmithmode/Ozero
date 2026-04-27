plugins {
    id("ozero.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.ozero.corestorage"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core-api"))
        api(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
