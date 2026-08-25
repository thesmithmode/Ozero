package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxSoakContractTest {

    @Test
    fun `soak runs native x86_64 app and exercises real VPN traffic`() {
        val root = locateRepoRoot()
        val workflow = File(root, ".github/workflows/soak.yml").readText()
        val runner = File(root, ".github/soak/run-singbox-tun-soak.sh").readText()
        val soak = File(
            root,
            "app/src/androidTest/java/ru/ozero/app/soak/SoakTest.kt",
        ).readText()
        val manifest = File(root, "app/src/androidTest/AndroidManifest.xml").readText()
        val externalProbe = File(
            root,
            "app/src/androidTest/java/ru/ozero/app/soak/SoakExternalProbeReceiver.java",
        ).readText()
        val debugManifest = File(root, "app/src/debug/AndroidManifest.xml").readText()
        val debugNetworkSecurity = File(
            root,
            "app/src/debug/res/xml/network_security_config_debug.xml",
        ).readText()
        val appBuild = File(root, "app/build.gradle.kts").readText()
        val engineBuild = File(root, "engine-singbox/build.gradle.kts").readText()

        assertTrue(workflow.contains("default: '35'"))
        assertTrue(workflow.contains("target: google_apis"))
        assertTrue(workflow.contains("runs-on: ubuntu-latest"))
        assertTrue(workflow.contains("arch: x86_64"))
        assertTrue(workflow.contains("-Pozero.soak.x86_64=true"))
        assertTrue(workflow.contains("Build libhev for x86_64 soak APK"))
        assertTrue(workflow.contains("APP_ABI=\"x86_64\""))
        assertTrue(workflow.contains("libhev-ozero-socks5-tunnel.so"))
        assertTrue(appBuild.contains("val soakX86_64"))
        assertTrue(engineBuild.contains("val soakX86_64"))
        assertTrue(appBuild.contains("val supportedAbis"))
        assertTrue(engineBuild.contains("val supportedAbis"))
        assertTrue(appBuild.contains("else listOf(\"arm64-v8a\")"))
        assertTrue(engineBuild.contains("else listOf(\"arm64-v8a\")"))
        assertTrue(workflow.contains("download-libbox-aar.sh"))
        assertFalse(workflow.contains("gh release download singbox-1.13.12"))
        assertTrue(debugManifest.contains("@xml/network_security_config_debug"))
        assertTrue(debugManifest.contains("tools:replace=\"android:networkSecurityConfig\""))
        assertTrue(debugNetworkSecurity.contains("cleartextTrafficPermitted=\"true\""))
        assertTrue(debugNetworkSecurity.contains(">10.0.2.2</domain>"))
        assertTrue(workflow.contains(":app:assembleDebugAndroidTest"))
        assertTrue(workflow.contains("OZERO_SOAK_VLESS"))
        assertTrue(workflow.contains("OZERO_SOAK_VMESS"))
        assertTrue(workflow.contains("OZERO_SOAK_TROJAN"))
        assertTrue(runner.contains("OK (1 test)"))
        assertTrue(runner.contains("successful_cycles"))
        assertTrue(runner.contains("inbound connection"))
        assertTrue(soak.contains("OzeroVpnService.ACTION_START"))
        assertTrue(soak.contains("VpnService.prepare(targetContext)"))
        assertTrue(soak.contains("TunnelState.Connected"))
        assertTrue(soak.contains("probeFromExternalUid"))
        assertTrue(soak.contains("ContextCompat.registerReceiver"))
        assertTrue(soak.contains("ContextCompat.RECEIVER_EXPORTED"))
        assertTrue(soak.contains("probeContext.sendBroadcast"))
        assertTrue(soak.contains("vpnTransport"))
        assertTrue(soak.contains("markerMatch"))
        assertFalse(soak.contains("fun doHttpRequest"))
        assertTrue(manifest.contains("SoakExternalProbeReceiver"))
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(manifest.contains("android:exported=\"true\""))
        assertTrue(manifest.contains("android:process=\":soak_probe\""))
        assertTrue(externalProbe.contains("class SoakExternalProbeReceiver"))
        assertFalse(externalProbe.contains("kotlin."))
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root not found")
    }
}
