plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.corebackup"
}

dependencies {
    implementation(libs.bundles.coroutines)
    implementation(libs.datastore.preferences)

    implementation(project(":engines-core"))
    implementation(project(":engine-warp"))
    implementation(project(":engine-urnetwork"))
    implementation(project(":core-storage"))

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.json)
}
