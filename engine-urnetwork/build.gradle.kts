plugins {
    id("ozero.android.library")
}

android {
    namespace = "ru.ozero.engineurnetwork"
}

// AGP отказывается паковать local .aar в любой AAR/lint bundle для library
// модуля. engine-urnetwork никем не consume'ится как .aar — это internal
// library project с classes for app модуль. Disable все AAR bundle/lint
// tasks; compileDebugKotlin, testDebugUnitTest, jacocoTestReport остаются.
afterEvaluate {
    tasks.matching {
        it.name.matches(Regex("bundle.*Aar")) ||
            it.name.matches(Regex("generate.*LintModel")) ||
            it.name.matches(Regex("generate.*LintReportModel")) ||
            it.name.matches(Regex("lintAnalyze.*AndroidTest")) ||
            it.name.matches(Regex("lintReport.*AndroidTest")) ||
            it.name.matches(Regex("lintVitalAnalyze.*AndroidTest")) ||
            it.name.matches(Regex("lintAnalyze.*UnitTest")) ||
            it.name.matches(Regex("lintReport.*UnitTest")) ||
            it.name.matches(Regex("lintVitalAnalyze.*UnitTest")) ||
            it.name == "lintAnalyzeDebug" ||
            it.name == "lintAnalyzeRelease" ||
            it.name == "lintReportDebug" ||
            it.name == "lintReportRelease" ||
            it.name == "lintDebug" ||
            it.name == "lintRelease" ||
            it.name == "lint"
    }.configureEach {
        enabled = false
    }
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
