import binaries.ExtractGfVpnHevTask
import binaries.PrepareGfVpnSdkTask

plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

ozeroBinaries {
    artifact("hev-gfvpn-2.20.0.aar")
}

val prepareGfVpnHev by tasks.registering(ExtractGfVpnHevTask::class) {
    dependsOn(tasks.named("downloadBinaries"))
    sourceAar.set(layout.projectDirectory.file("libs/hev-gfvpn-2.20.0.aar"))
    outputDirectory.set(layout.buildDirectory.dir("generated/gfvpn-hev/jniLibs"))
}

// The archive is a build input, not a file selected by the installed app.
val sdkArchive = providers.gradleProperty("ozero.gfvpn.sdkArchive").orNull
    ?.let { rootProject.file(it) }
    ?: layout.projectDirectory.file("vendor/runtime-bundle-1.0.6.zip").asFile
val unitTestsOnly = providers.gradleProperty("ozero.gfvpn.unitTestsOnly").orNull == "true"

val prepareGfVpnSdk by tasks.registering(PrepareGfVpnSdkTask::class) {
    sourceArchive.fileValue(sdkArchive)
    outputDirectory.set(layout.buildDirectory.dir("generated/gfvpn-sdk/assets"))
}

android {
    namespace = "ru.ozero.enginegfvpn"
    if (!unitTestsOnly) {
        sourceSets.getByName("main").assets.srcDir(prepareGfVpnSdk.flatMap { it.outputDirectory })
    }
    sourceSets.getByName("main").jniLibs.srcDir(prepareGfVpnHev.flatMap { it.outputDirectory })
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(prepareGfVpnHev)
    if (!unitTestsOnly) dependsOn(prepareGfVpnSdk)
}

// Validate requested tasks at configuration time, including configuration-cache builds.
if (unitTestsOnly) {
    val allowed = setOf("testDebugUnitTest", "jacocoTestReport", "jacocoTestCoverageVerification")
    val testCompilation = setOf(":app:compileDebugAndroidTestKotlin", ":app:compileDebugAndroidTestJavaWithJavac")
    val requested = gradle.startParameter.taskNames
    val includesTests = requested.any { it.substringAfterLast(':') == "testDebugUnitTest" }
    val testsOnly = requested.all {
        it in testCompilation || (it.startsWith(":") && it.substringAfterLast(':') in allowed)
    }
    check(includesTests && testsOnly) {
        "SDK-free unit-test mode requires explicit JVM test and coverage tasks only"
    }
}

dependencies {
    implementation(project(":common-vpn"))
    implementation(project(":common-net"))
    implementation(libs.okhttp.core)
    implementation(project(":engines-core"))
    implementation(libs.bundles.coroutines)
    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
