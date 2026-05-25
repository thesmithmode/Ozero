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
    implementation(libs.bouncycastle.prov)

    // AGP 4.1+ запрещает упаковку local AAR в bundleReleaseLocalLintAar/AAR
    // библиотеки (Direct local .aar file dependencies are not supported when
    // building an AAR). Поэтому AAR здесь только compileOnly — engine-urnetwork
    // компилируется против SDK типов, но не пытается их паковать. Runtime
    // classpath получает их через app:implementation(fileTree("engine-urnetwork/libs")).
    // Если папка пуста (Stub-режим до CI download) — fileTree резолвится в
    // пустой набор, конфигурация не падает, StubUrnetworkSdkBridge активен.
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    compileOnly(fileTree(mapOf("dir" to "${rootProject.projectDir}/singbox-core/libs-stubs", "include" to listOf("*.jar"))))
    testImplementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    testImplementation(fileTree(mapOf("dir" to "${rootProject.projectDir}/singbox-core/libs-stubs", "include" to listOf("*.jar"))))

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.datastore.preferences.core)
}
