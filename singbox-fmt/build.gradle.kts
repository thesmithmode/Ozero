plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.singboxfmt"
}

dependencies {
    implementation(libs.kryo)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.json)
}
