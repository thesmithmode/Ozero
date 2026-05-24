plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.singboxfmt"
}

dependencies {
    implementation(libs.kryo)
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
}
