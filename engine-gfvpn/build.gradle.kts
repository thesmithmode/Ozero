plugins {
    id("ozero.android.library")
}
android {
    namespace = "ru.ozero.enginegfvpn"
}

dependencies {
    implementation(project(":common-vpn"))
    implementation(project(":engines-core"))
    implementation(libs.bundles.coroutines)
    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
