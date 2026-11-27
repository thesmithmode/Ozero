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

val prepareGfVpnSdk by tasks.registering(PrepareGfVpnSdkTask::class) {
    sourceArchive.set(layout.projectDirectory.file("vendor/gfvpn-sdk-1.0.6.zip"))
    outputDirectory.set(layout.buildDirectory.dir("generated/gfvpn-sdk/assets"))
}

android {
    namespace = "ru.ozero.enginegfvpn"
    sourceSets.getByName("main").assets.srcDir(prepareGfVpnSdk.flatMap { it.outputDirectory })
    sourceSets.getByName("main").jniLibs.srcDir(prepareGfVpnHev.flatMap { it.outputDirectory })
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(prepareGfVpnHev, prepareGfVpnSdk)
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
