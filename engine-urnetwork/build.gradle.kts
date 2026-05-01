plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.engineurnetwork"
}

dependencies {
    implementation(project(":engines-core"))
    implementation(project(":common-vpn"))
    implementation(libs.bundles.coroutines)

    implementation(libs.hilt.android)
    implementation(libs.datastore.preferences)

    // Локальные AAR из scripts/build_wireguard_android.sh + tools/build-urnetwork-aar.sh.
    // Если папка libs/ пуста (Stub-режим, AAR ещё не собраны) — fileTree
    // резолвится в пустой набор и конфигурация не падает. Stub bridge остаётся
    // активным провайдером в WarpModule/UrnetworkModule.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.datastore.preferences.core)
}
