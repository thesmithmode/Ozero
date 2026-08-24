package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxSoakContractTest {

    @Test
    fun `soak runs native arm64 app and exercises real VPN traffic`() {
        val root = locateRepoRoot()
        val workflow = File(root, ".github/workflows/soak.yml").readText()
        val runner = File(root, ".github/soak/run-singbox-tun-soak.sh").readText()
        val soak = File(
            root,
            "app/src/androidTest/java/ru/ozero/app/soak/SoakTest.kt",
        ).readText()
        val manifest = File(root, "app/src/androidTest/AndroidManifest.xml").readText()
        val debugManifest = File(root, "app/src/debug/AndroidManifest.xml").readText()
        val debugNetworkSecurity = File(
            root,
            "app/src/debug/res/xml/network_security_config_debug.xml",
        ).readText()

        assertTrue(workflow.contains("default: '30'"))
        assertTrue(workflow.contains("target: google_apis"))
        assertTrue(workflow.contains("runs-on: macos-14"))
        assertTrue(workflow.contains("arch: arm64-v8a"))
        assertFalse(workflow.contains("arch: x86_64"))
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
        assertTrue(soak.contains("TunnelState.Connected"))
        assertTrue(soak.contains("probeFromExternalUid"))
        assertTrue(soak.contains("vpnTransport"))
        assertTrue(soak.contains("markerMatch"))
        assertFalse(soak.contains("fun doHttpRequest"))
        assertTrue(manifest.contains("SoakExternalProbeService"))
        assertTrue(manifest.contains("android:process=\":soak_probe\""))
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
