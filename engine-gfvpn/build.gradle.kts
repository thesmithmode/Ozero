plugins {
    id("ozero.android.library")
}
android {
    namespace = "ru.ozero.enginegfvpn"
}

dependencies {
    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
