plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.singboxconfig"
    sourceSets.getByName("test").resources.srcDir(project(":singbox-fmt").file("src/test/resources"))
}

dependencies {
    implementation(project(":singbox-fmt"))
    implementation(project(":engines-core"))
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.json)
}
