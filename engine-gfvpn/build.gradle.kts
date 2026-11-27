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
val sdkArchivePath = providers.gradleProperty("ozero.gfvpn.sdkArchive")
val unitTestsOnly = providers.gradleProperty("ozero.gfvpn.unitTestsOnly").orNull == "true"

val prepareGfVpnSdk by tasks.registering(PrepareGfVpnSdkTask::class) {
    sourceArchive.set(
        layout.file(sdkArchivePath.map { rootProject.file(it) })
            .orElse(layout.projectDirectory.file("vendor/gfvpn-sdk-1.0.6.zip")),
    )
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

// SDK-free mode may run JVM tests but must not produce installable APKs or AARs.
if (unitTestsOnly) {
    gradle.taskGraph.whenReady {
        check(allTasks.any { it.name.endsWith("UnitTest") }) {
            "ozero.gfvpn.unitTestsOnly requires a unit-test task"
        }
        val appPackaging = setOf(
            "assemble", "assembleDebug", "assembleRelease", "assembleDebugAndroidTest",
            "bundle", "bundleDebug", "bundleRelease", "packageDebug", "packageRelease", "packageDebugAndroidTest",
        )
        val sdkPackaging = setOf("assemble", "assembleDebug", "assembleRelease", "bundleDebugAar", "bundleReleaseAar")
        check(allTasks.none { task ->
            val name = task.name
            val publishesApp = task.project.path == ":app" &&
                (name in appPackaging || name.startsWith("install") || name.startsWith("publish"))
            val publishesSdk = task.project.path == ":engine-gfvpn" &&
                (name in sdkPackaging || name.startsWith("publish"))
            publishesApp || publishesSdk
        }) {
            "SDK-free unit-test mode must not produce installable or distributable artifacts"
        }
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
