plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

ozeroBinaries {
    artifact("hev-gfvpn-2.20.0.aar")
}

val gfVpnHevAar = layout.projectDirectory.file("libs/hev-gfvpn-2.20.0.aar")
val gfVpnHevJni = layout.buildDirectory.dir("generated/gfvpn-hev/jniLibs")
val prepareGfVpnHev by tasks.registering(Sync::class) {
    dependsOn(tasks.named("downloadBinaries"))
    from({ zipTree(gfVpnHevAar.asFile) }) {
        include("jni/arm64-v8a/libhev-socks5-tunnel.so")
        eachFile {
            path = "arm64-v8a/libhev-gfvpn-socks5-tunnel.so"
        }
        includeEmptyDirs = false
    }
    into(gfVpnHevJni)
    doLast {
        check(gfVpnHevJni.get().file("arm64-v8a/libhev-gfvpn-socks5-tunnel.so").asFile.length() > 0) {
            "Verified gfVPN HEV AAR did not contain the ARM64 native library"
        }
    }
}

android {
    namespace = "ru.ozero.enginegfvpn"
    sourceSets.getByName("main").jniLibs.srcDir(prepareGfVpnHev)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(prepareGfVpnHev)
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
