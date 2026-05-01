plugins {
    id("ozero.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.ozero.corestorage"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)

    testImplementation(libs.junit4)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
}
