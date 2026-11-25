plugins {
    id("ozero.android.library")
}
android {
    namespace = "ru.ozero.enginegfvpn"
}

dependencies {
    implementation(project(":common-vpn"))
    implementation(project(":engines-core"))
    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
